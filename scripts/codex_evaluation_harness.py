from __future__ import annotations

import csv
import hashlib
import json
import os
import re
import tempfile
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Mapping


REQUIRED_SECTIONS = (
    "本周提交概览",
    "需老板拍板/协调事项",
    "未提交/异常提交名单",
    "员工五维评价",
    "团队负责人履职检查",
    "共性风险与下周关注点",
    "数据质量与需要人工确认事项",
)
REQUIRED_DIMENSIONS = (
    "虚实盘（本周成果）",
    "时间分配健康度",
    "AI使用红黑榜",
    "下周计划合格性",
    "综合结论",
)
SENSITIVE_OUTPUT = (
    re.compile(r"(?i)access[_ -]?token\s*[:=]\s*\S+"),
    re.compile(r"(?i)appsecret\s*[:=]\s*\S+"),
    re.compile(r"(?i)bearer\s+[A-Za-z0-9._~+/=-]{12,}"),
    re.compile(r"eyJ[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{10,}"),
)


class EvaluationHarnessError(RuntimeError):
    def __init__(self, code: str):
        super().__init__(code)
        self.code = code


@dataclass(frozen=True)
class RosterEvidence:
    expected_names: tuple[str, ...]
    submitted_count: int
    missing_count: int
    leader_names: tuple[str, ...]
    userids: tuple[str, ...]
    submitted_userids: tuple[str, ...]

    @property
    def expected_count(self) -> int:
        return len(self.expected_names)


def load_roster_evidence(csv_path: Path) -> RosterEvidence:
    try:
        with csv_path.open("r", encoding="utf-8-sig", newline="") as handle:
            rows = list(csv.DictReader(handle))
    except (OSError, UnicodeError, csv.Error) as exc:
        raise EvaluationHarnessError("ROSTER_READ_FAILED") from exc
    if not rows:
        raise EvaluationHarnessError("ROSTER_EMPTY")

    names: list[str] = []
    leaders: list[str] = []
    userids: list[str] = []
    submitted_userids: list[str] = []
    submitted = 0
    missing = 0
    for row in rows:
        name = str(row.get("姓名") or "").strip()
        userid = str(row.get("userid") or "").strip()
        status = str(row.get("提交状态") or "").strip()
        if not name or not userid or status not in {"已提交", "未提交"}:
            raise EvaluationHarnessError("ROSTER_ROW_INVALID")
        names.append(name)
        userids.append(userid)
        submitted += status == "已提交"
        missing += status == "未提交"
        if status == "已提交":
            submitted_userids.append(userid)
        if str(row.get("是否负责人候选") or "").strip() == "是":
            leaders.append(name)

    return RosterEvidence(
        expected_names=tuple(names),
        submitted_count=submitted,
        missing_count=missing,
        leader_names=tuple(leaders),
        userids=tuple(userids),
        submitted_userids=tuple(submitted_userids),
    )


def build_input_digest(project_root: Path, week_root: Path, prompt_path: Path, schema_path: Path) -> tuple[str, int]:
    fixed_paths = (
        week_root / "analysis" / "analysis_input.md",
        week_root / "exports" / "submission_status.csv",
        project_root / "codex-skills" / "weekly-report-assistant" / "SKILL.md",
        project_root / "codex-skills" / "weekly-report-assistant" / "references" / "output_format.md",
        project_root / "weekly_report_template.txt",
        project_root / "team_leader_extra_duties.txt",
        prompt_path,
        schema_path,
    )
    attachments_root = week_root / "attachments" / "team_leads"
    attachments = tuple(sorted(path for path in attachments_root.rglob("*") if path.is_file())) \
        if attachments_root.exists() else tuple()
    paths = fixed_paths + attachments
    digest = hashlib.sha256()
    for path in paths:
        if not path.is_file():
            raise EvaluationHarnessError("REQUIRED_INPUT_MISSING")
        try:
            relative = path.resolve().relative_to(project_root.resolve()).as_posix()
        except ValueError as exc:
            raise EvaluationHarnessError("INPUT_PATH_OUTSIDE_PROJECT") from exc
        digest.update(relative.encode("utf-8"))
        digest.update(b"\0")
        try:
            with path.open("rb") as handle:
                for chunk in iter(lambda: handle.read(1024 * 1024), b""):
                    digest.update(chunk)
        except OSError as exc:
            raise EvaluationHarnessError("INPUT_READ_FAILED") from exc
        digest.update(b"\0")
    return digest.hexdigest(), len(attachments)


def validate_manager_report(markdown: str, week_label: str, roster: RosterEvidence) -> tuple[str, ...]:
    errors: list[str] = []
    if len(markdown.encode("utf-8")) < 800:
        errors.append("REPORT_TOO_SHORT")
    if len(markdown.encode("utf-8")) > 2 * 1024 * 1024:
        errors.append("REPORT_TOO_LARGE")
    if week_label not in markdown:
        errors.append("WEEK_LABEL_MISSING")

    section_positions: list[int] = []
    for section in REQUIRED_SECTIONS:
        match = re.search(rf"(?m)^##\s+(?:\d+[.、]\s*)?{re.escape(section)}\s*$", markdown)
        if not match:
            errors.append("REQUIRED_SECTION_MISSING")
            break
        section_positions.append(match.start())
    if len(section_positions) == len(REQUIRED_SECTIONS) and section_positions != sorted(section_positions):
        errors.append("REQUIRED_SECTION_ORDER_INVALID")
    for dimension in REQUIRED_DIMENSIONS:
        if dimension not in markdown:
            errors.append("EVALUATION_DIMENSION_MISSING")
            break
    if any(name not in markdown for name in roster.expected_names):
        errors.append("ROSTER_COVERAGE_INCOMPLETE")

    leader_section = _section(markdown, "团队负责人履职检查")
    if roster.leader_names and any(name not in leader_section for name in roster.leader_names):
        errors.append("LEADER_COVERAGE_INCOMPLETE")
    if any(userid in markdown for userid in roster.userids if len(userid) >= 6):
        errors.append("USERID_EXPOSED")
    if re.search(r"(?i)\b(userid|unionid|fileid|spaceid)\b", markdown):
        errors.append("INTERNAL_IDENTIFIER_LABEL_EXPOSED")
    if any(pattern.search(markdown) for pattern in SENSITIVE_OUTPUT):
        errors.append("SECRET_SHAPE_EXPOSED")
    return tuple(dict.fromkeys(errors))


def _section(markdown: str, title: str) -> str:
    match = re.search(rf"(?m)^##\s+(?:\d+[.、]\s*)?{re.escape(title)}\s*$", markdown)
    if not match:
        return ""
    following = re.search(r"(?m)^##\s+", markdown[match.end():])
    end = match.end() + following.start() if following else len(markdown)
    return markdown[match.end():end]


def sanitized_codex_environment(source: Mapping[str, str] | None = None) -> dict[str, str]:
    environment = dict(source or os.environ)
    for key in tuple(environment):
        upper = key.upper()
        if upper == "CODEX_API_KEY":
            continue
        if upper.startswith((
            "DINGTALK_",
            "WEEKLY_DINGTALK_",
            "WEEKLY_FEEDBACK_",
            "WEEKLY_EVALUATION_FEEDBACK_",
            "SPRING_DATASOURCE_",
        )):
            environment.pop(key, None)
            continue
        if upper in {
            "MYSQL_ROOT_PASSWORD",
            "WEEKLY_REPORT_EXEMPT_SUBMITTERS",
            "WEEKLY_REPORT_LEADER_OVERRIDES",
            "OPENAI_API_KEY",
        }:
            environment.pop(key, None)
            continue
        if any(marker in upper for marker in ("PASSWORD", "SECRET", "TOKEN", "API_KEY", "APP_KEY", "CREDENTIAL")):
            environment.pop(key, None)
    environment["PYTHONDONTWRITEBYTECODE"] = "1"
    return environment


def atomic_write_text(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary: Path | None = None
    try:
        with tempfile.NamedTemporaryFile(
            "w",
            encoding="utf-8",
            newline="\n",
            dir=path.parent,
            delete=False,
        ) as handle:
            temporary = Path(handle.name)
            handle.write(content)
            if not content.endswith("\n"):
                handle.write("\n")
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, path)
    except OSError as exc:
        if temporary is not None:
            temporary.unlink(missing_ok=True)
        raise EvaluationHarnessError("ATOMIC_WRITE_FAILED") from exc


def read_state(path: Path) -> dict[str, Any]:
    if not path.exists():
        return {}
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        raise EvaluationHarnessError("STATE_READ_FAILED") from exc
    return value if isinstance(value, dict) else {}


def write_state(path: Path, state: Mapping[str, Any]) -> None:
    atomic_write_text(path, json.dumps(dict(state), ensure_ascii=False, indent=2, sort_keys=True))


def next_attempt(state: Mapping[str, Any], input_digest: str) -> int:
    if state.get("inputDigest") != input_digest:
        return 1
    return int(state.get("attemptCount") or 0) + 1


def should_run_codex(
    state: Mapping[str, Any],
    input_digest: str,
    current_report_errors: tuple[str, ...],
    max_attempts: int,
) -> tuple[bool, str]:
    if state.get("status") == "SUCCESS" and state.get("inputDigest") == input_digest and not current_report_errors:
        return False, "UNCHANGED"
    attempts = int(state.get("attemptCount") or 0) if state.get("inputDigest") == input_digest else 0
    if state.get("status") == "FAILED" and attempts >= max(1, max_attempts):
        return False, "RETRY_LIMIT"
    return True, "INPUT_CHANGED_OR_REPORT_INVALID"


class EvaluationLock:
    def __init__(self, path: Path, stale_seconds: int = 4 * 60 * 60):
        self.path = path
        self.stale_seconds = stale_seconds
        self.acquired = False

    def __enter__(self) -> "EvaluationLock":
        self.path.parent.mkdir(parents=True, exist_ok=True)
        if self.path.exists() and time.time() - self.path.stat().st_mtime > self.stale_seconds:
            self.path.unlink(missing_ok=True)
        try:
            descriptor = os.open(self.path, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
        except FileExistsError as exc:
            raise EvaluationHarnessError("RUN_ALREADY_ACTIVE") from exc
        with os.fdopen(descriptor, "w", encoding="ascii") as handle:
            handle.write(str(os.getpid()))
        self.acquired = True
        return self

    def __exit__(self, exc_type, exc, traceback) -> None:
        if self.acquired:
            self.path.unlink(missing_ok=True)
