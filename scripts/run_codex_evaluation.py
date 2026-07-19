from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
from datetime import date, datetime, timedelta
from pathlib import Path
from typing import Any

from codex_evaluation_harness import (
    EvaluationHarnessError,
    EvaluationLock,
    atomic_write_text,
    build_input_digest,
    load_roster_evidence,
    next_attempt,
    read_state,
    sanitized_codex_environment,
    should_run_codex,
    validate_manager_report,
    write_state,
)
from codex_employee_feedback import validate_employee_feedback, validate_employee_feedback_artifact
from codex_evaluation_workspace import isolated_evaluation_workspace
from codex_evaluation_runtime import (
    codex_command,
    parse_codex_result,
    preflight,
    resolve_codex_bin,
    run_checked,
)
from dingtalk_common import CN_TZ, load_env, output_root, week_range

ROOT = Path(__file__).resolve().parents[1]
PROMPT_PATH = ROOT / "harness" / "weekly-report-evaluation" / "prompt.md"
SCHEMA_PATH = ROOT / "harness" / "weekly-report-evaluation" / "output-schema.json"

def week_label(week: str = "previous") -> str:
    start, _ = week_range(week)
    iso = start.isocalendar()
    return f"{iso.year}-W{iso.week:02d}"


def scheduled_week_label(now: datetime | None = None) -> str | None:
    """Resolve the report week for the Sunday-through-Tuesday automation window."""
    current = now or datetime.now(CN_TZ)
    if current.tzinfo is None:
        current = current.replace(tzinfo=CN_TZ)
    current = current.astimezone(CN_TZ)
    weekday = current.isoweekday()
    if weekday == 7:
        if (current.hour, current.minute) < (18, 10):
            return None
        start, _ = week_range("current", current)
    elif weekday in (1, 2):
        start, _ = week_range("previous", current)
    else:
        return None
    iso = start.isocalendar()
    return f"{iso.year}-W{iso.week:02d}"


def resolve_week_label(value: str | None) -> str:
    if not value:
        return week_label("previous")
    match = re.fullmatch(r"(\d{4})-W(\d{2})", value.strip())
    if not match:
        raise EvaluationHarnessError("WEEK_LABEL_INVALID")
    year, week = (int(part) for part in match.groups())
    try:
        start = date.fromisocalendar(year, week, 1)
    except ValueError as exc:
        raise EvaluationHarnessError("WEEK_LABEL_INVALID") from exc
    iso = start.isocalendar()
    return f"{iso.year}-W{iso.week:02d}"


def collection_command(mode: str, label: str) -> list[str]:
    start = date.fromisocalendar(int(label[:4]), int(label[6:]), 1)
    end = start + timedelta(days=6)
    week_args = ["--start", start.isoformat(), "--end", end.isoformat()]
    if mode == "docker":
        return [
            "docker", "compose", "exec", "-T", "weekly-report",
            "python3", "/app/scripts/run_weekly.py", *week_args,
        ]
    if mode == "host":
        return [sys.executable, str(ROOT / "scripts" / "run_weekly.py"), *week_args]
    raise EvaluationHarnessError("COLLECTION_MODE_INVALID")
def render_prompt(label: str) -> str:
    try:
        template = PROMPT_PATH.read_text(encoding="utf-8")
    except (OSError, UnicodeError) as exc:
        raise EvaluationHarnessError("PROMPT_READ_FAILED") from exc
    replacements = {
        "{{WEEK_LABEL}}": label,
        "{{WEEK_ROOT}}": ".",
        "{{ANALYSIS_INPUT}}": "analysis/analysis_input.md",
        "{{SUBMISSION_CSV}}": "exports/submission_status.csv",
    }
    for marker, value in replacements.items():
        template = template.replace(marker, value)
    if "{{" in template or "}}" in template:
        raise EvaluationHarnessError("PROMPT_PLACEHOLDER_UNRESOLVED")
    return template


def persisted_text_digest(content: str) -> str:
    persisted = content if content.endswith("\n") else content + "\n"
    return hashlib.sha256(persisted.encode("utf-8")).hexdigest()

def state_payload(
    label: str,
    input_digest: str,
    status: str,
    attempt: int,
    roster,
    error_code: str = "",
    report_digest: str = "",
) -> dict[str, Any]:
    return {
        "version": 1,
        "weekLabel": label,
        "inputDigest": input_digest,
        "status": status,
        "attemptCount": attempt,
        "lastAttemptAt": datetime.now(CN_TZ).isoformat(),
        "expectedCount": roster.expected_count if roster else 0,
        "submittedCount": roster.submitted_count if roster else 0,
        "missingCount": roster.missing_count if roster else 0,
        "reportDigest": report_digest,
        "errorCode": error_code,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Refresh a weekly Codex evaluation only when inputs change.")
    parser.add_argument("--preflight", action="store_true", help="Check Codex CLI and installed Skill without reading report data.")
    parser.add_argument("--dry-run", action="store_true", help="Collect and fingerprint inputs without invoking Codex.")
    parser.add_argument("--force", action="store_true", help="Ignore the successful input digest and retry limit.")
    week_selection = parser.add_mutually_exclusive_group()
    week_selection.add_argument("--week-label", metavar="YYYY-Www", help="Explicit ISO week to collect and evaluate instead of the previous week.")
    week_selection.add_argument(
        "--scheduled-window",
        action="store_true",
        help="Select the current week on Sunday evening and that same week on Monday or Tuesday.",
    )
    args = parser.parse_args()

    configured = load_env()
    codex_environment = sanitized_codex_environment(os.environ)
    out_root = output_root(configured)
    label = ""
    week_root = out_root
    state_path = week_root / "automation" / "evaluation_state.json"
    lock_path = out_root / ".automation" / "codex-evaluation.lock"
    digest = ""
    roster = None
    attempt = 0
    try:
        if args.preflight:
            codex_bin = resolve_codex_bin(configured)
            preflight(codex_bin, codex_environment)
            print("OK: Codex evaluation preflight passed.")
            return 0

        if args.scheduled_window:
            label = scheduled_week_label()
            if label is None:
                print("SKIPPED: reason=OUTSIDE_SCHEDULED_WINDOW")
                return 0
        else:
            label = resolve_week_label(args.week_label)
        week_root = out_root / label
        state_path = week_root / "automation" / "evaluation_state.json"
        codex_bin = resolve_codex_bin(configured)
        approval_mode = preflight(codex_bin, codex_environment)

        with EvaluationLock(lock_path):
            mode = configured.get("WEEKLY_CODEX_COLLECTION_MODE", "host").strip().lower() or "host"
            collection_timeout = int(configured.get("WEEKLY_CODEX_COLLECTION_TIMEOUT_SECONDS", "600"))
            collection = run_checked(collection_command(mode, label), collection_timeout)
            if collection.returncode != 0:
                raise EvaluationHarnessError("COLLECTION_FAILED")

            roster = load_roster_evidence(week_root / "exports" / "submission_status.csv")
            digest, attachment_count = build_input_digest(ROOT, week_root, PROMPT_PATH, SCHEMA_PATH)
            state = read_state(state_path)
            report_path = week_root / "summary" / "manager_report.md"
            current_report = report_path.read_text(encoding="utf-8") if report_path.is_file() else ""
            current_errors = validate_manager_report(current_report, label, roster) if current_report else ("REPORT_MISSING",)
            current_report_digest = hashlib.sha256(current_report.encode("utf-8")).hexdigest() if current_report else ""
            feedback_path = week_root / "automation" / "employee_feedback.json"
            current_errors += validate_employee_feedback_artifact(
                feedback_path,
                label,
                roster,
                digest,
                current_report_digest,
            )
            max_attempts = int(configured.get("WEEKLY_CODEX_MAX_ATTEMPTS_PER_INPUT", "3"))
            should_run, reason = should_run_codex(state, digest, current_errors, max_attempts)
            if not args.force and not should_run:
                print(f"SKIPPED: week={label} reason={reason} attachments={attachment_count}")
                return 0
            if args.dry_run:
                print(f"DRY-RUN: week={label} action=generate attachments={attachment_count}")
                return 0

            attempt = next_attempt(state, digest)
            timeout = int(configured.get("WEEKLY_CODEX_TIMEOUT_SECONDS", "2700"))
            prompt = render_prompt(label)
            with isolated_evaluation_workspace(ROOT, week_root, prompt, SCHEMA_PATH) as workspace:
                codex = run_checked(
                    codex_command(codex_bin, prompt, configured, workspace, approval_mode),
                    timeout,
                    codex_environment,
                    cwd=workspace,
                )
            if codex.returncode != 0:
                raise EvaluationHarnessError("CODEX_EXEC_FAILED")
            markdown, employee_feedback, _warnings = parse_codex_result(codex.stdout, label)
            validation_errors = validate_manager_report(markdown, label, roster)
            validation_errors += validate_employee_feedback(employee_feedback, roster)
            if validation_errors:
                raise EvaluationHarnessError(validation_errors[0])

            report_digest = persisted_text_digest(markdown)
            feedback_payload = {
                "version": 1,
                "weekLabel": label,
                "inputDigest": digest,
                "reportDigest": report_digest,
                "feedback": list(employee_feedback),
            }
            atomic_write_text(
                feedback_path,
                json.dumps(feedback_payload, ensure_ascii=False, indent=2, sort_keys=True),
            )
            atomic_write_text(report_path, markdown)
            write_state(state_path, state_payload(label, digest, "SUCCESS", attempt, roster, report_digest=report_digest))
            print(
                f"OK: week={label} status=generated expected={roster.expected_count} "
                f"submitted={roster.submitted_count} missing={roster.missing_count} attachments={attachment_count}"
            )
            return 0
    except (EvaluationHarnessError, OSError, UnicodeError, ValueError) as exc:
        code = exc.code if isinstance(exc, EvaluationHarnessError) else "HARNESS_CONFIGURATION_INVALID"
        if not args.preflight and week_root.exists():
            try:
                previous = read_state(state_path)
                safe_attempt = attempt or next_attempt(previous, digest) if digest else int(previous.get("attemptCount") or 0)
                write_state(state_path, state_payload(label, digest, "FAILED", safe_attempt, roster, error_code=code))
            except EvaluationHarnessError:
                pass
        print(f"FAILED: week={label} code={code}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
