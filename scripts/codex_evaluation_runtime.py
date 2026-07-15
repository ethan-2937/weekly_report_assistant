from __future__ import annotations

import json
import shutil
import subprocess
from pathlib import Path

from codex_evaluation_harness import EvaluationHarnessError

ROOT = Path(__file__).resolve().parents[1]
REPO_SKILL_PATH = ROOT / "codex-skills" / "weekly-report-assistant" / "SKILL.md"


def codex_command(
    codex_bin: str,
    prompt: str,
    configured: dict[str, str],
    workspace: Path,
    approval_mode: str = "explicit",
) -> list[str]:
    effort = configured.get("WEEKLY_CODEX_REASONING_EFFORT", "high").strip() or "high"
    if effort not in {"low", "medium", "high", "xhigh"}:
        raise EvaluationHarnessError("CODEX_REASONING_EFFORT_INVALID")
    approval_flags = {
        "explicit": ["--ask-for-approval", "never"],
        "legacy": ["--full-auto"],
        "config": ["-c", 'approval_policy="never"'],
    }[approval_mode]
    command = [
        codex_bin,
        "exec",
        "--ephemeral",
        "--sandbox", "workspace-write",
        *approval_flags,
        "--cd", str(workspace),
        "--skip-git-repo-check",
        "--ignore-user-config",
        "--ignore-rules",
        "--output-schema", str(workspace / "output-schema.json"),
        "-c", "sandbox_workspace_write.network_access=false",
        "-c", 'web_search="disabled"',
        "-c", f'model_reasoning_effort="{effort}"',
    ]
    model = configured.get("WEEKLY_CODEX_MODEL", "").strip()
    if model:
        command.extend(["--model", model])
    command.append(prompt)
    if base_url := configured.get("WEEKLY_CODEX_BASE_URL", "").strip():
        invalid_chars = ('"', "'", " ", "\t", "\r", "\n", "@")
        if not base_url.startswith("https://") or any(char in base_url for char in invalid_chars):
            raise EvaluationHarnessError("CODEX_BASE_URL_INVALID")
        command[-1:-1] = [
            "-c", 'model_provider="crs"',
            "-c", 'model_providers.crs.name="crs"',
            "-c", f'model_providers.crs.base_url="{base_url}"',
            "-c", 'model_providers.crs.wire_api="responses"',
            "-c", 'model_providers.crs.requires_openai_auth=true',
            "-c", 'model_providers.crs.env_key="CRS_OAI_KEY"',
        ]
    return command


def resolve_codex_bin(configured: dict[str, str]) -> str:
    configured_bin = configured.get("WEEKLY_CODEX_BIN", "").strip()
    candidate = configured_bin or shutil.which("codex") or ""
    if not candidate:
        raise EvaluationHarnessError("CODEX_BIN_NOT_FOUND")
    return candidate


def installed_skill_path(environment: dict[str, str]) -> Path:
    home = Path(environment.get("CODEX_HOME") or (Path.home() / ".codex"))
    return home / "skills" / "weekly-report-assistant" / "SKILL.md"


def preflight(codex_bin: str, environment: dict[str, str]) -> str:
    skill_path = installed_skill_path(environment)
    if not skill_path.is_file():
        raise EvaluationHarnessError("CODEX_SKILL_NOT_INSTALLED")
    try:
        if skill_path.read_bytes() != REPO_SKILL_PATH.read_bytes():
            raise EvaluationHarnessError("CODEX_SKILL_OUTDATED")
    except OSError as exc:
        raise EvaluationHarnessError("CODEX_SKILL_READ_FAILED") from exc
    try:
        result = subprocess.run(
            [codex_bin, "exec", "--help"],
            cwd=ROOT,
            env=environment,
            capture_output=True,
            text=True,
            timeout=30,
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise EvaluationHarnessError("CODEX_CLI_CHECK_FAILED") from exc
    help_text = result.stdout + result.stderr
    required_flags = ("--ephemeral", "--output-schema", "--sandbox", "--ignore-rules")
    if result.returncode != 0 or any(flag not in help_text for flag in required_flags):
        raise EvaluationHarnessError("CODEX_CLI_TOO_OLD")
    if "--ask-for-approval" in help_text:
        return "explicit"
    if "--full-auto" in help_text:
        return "legacy"
    if "--config" in help_text or "-c, --config" in help_text:
        return "config"
    raise EvaluationHarnessError("CODEX_APPROVAL_MODE_UNSUPPORTED")


def run_checked(
    command: list[str],
    timeout_seconds: int,
    environment: dict[str, str] | None = None,
    cwd: Path = ROOT,
) -> subprocess.CompletedProcess[str]:
    try:
        return subprocess.run(
            command,
            cwd=cwd,
            env=environment,
            capture_output=True,
            text=True,
            timeout=max(1, timeout_seconds),
            check=False,
        )
    except subprocess.TimeoutExpired as exc:
        raise EvaluationHarnessError("PROCESS_TIMEOUT") from exc
    except OSError as exc:
        raise EvaluationHarnessError("PROCESS_START_FAILED") from exc


def parse_codex_result(stdout: str, label: str) -> tuple[str, tuple[str, ...]]:
    try:
        result = json.loads(stdout)
    except json.JSONDecodeError as exc:
        raise EvaluationHarnessError("CODEX_OUTPUT_NOT_JSON") from exc
    if not isinstance(result, dict):
        raise EvaluationHarnessError("CODEX_OUTPUT_SCHEMA_INVALID")
    status = result.get("status")
    if status == "blocked":
        raise EvaluationHarnessError("CODEX_OUTPUT_BLOCKED")
    if status != "completed":
        raise EvaluationHarnessError("CODEX_OUTPUT_STATUS_INVALID")
    if result.get("week_label") != label:
        raise EvaluationHarnessError("CODEX_OUTPUT_WRONG_WEEK")
    markdown = result.get("manager_report_markdown")
    warnings = result.get("warnings")
    if not isinstance(markdown, str) or not isinstance(warnings, list):
        raise EvaluationHarnessError("CODEX_OUTPUT_SCHEMA_INVALID")
    if any(not isinstance(item, str) for item in warnings):
        raise EvaluationHarnessError("CODEX_OUTPUT_SCHEMA_INVALID")
    return markdown, tuple(warnings)
