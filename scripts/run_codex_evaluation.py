from __future__ import annotations

import argparse
import hashlib
import os
import sys
from datetime import datetime
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


def collection_command(mode: str) -> list[str]:
    if mode == "docker":
        return [
            "docker", "compose", "exec", "-T", "weekly-report",
            "python3", "/app/scripts/run_weekly.py", "--week", "previous",
        ]
    if mode == "host":
        return [sys.executable, str(ROOT / "scripts" / "run_weekly.py"), "--week", "previous"]
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
    parser = argparse.ArgumentParser(description="Refresh the previous-week Codex evaluation only when inputs change.")
    parser.add_argument("--preflight", action="store_true", help="Check Codex CLI and installed Skill without reading report data.")
    parser.add_argument("--dry-run", action="store_true", help="Collect and fingerprint inputs without invoking Codex.")
    parser.add_argument("--force", action="store_true", help="Ignore the successful input digest and retry limit.")
    args = parser.parse_args()

    configured = load_env()
    codex_environment = sanitized_codex_environment(os.environ)
    label = week_label("previous")
    out_root = output_root(configured)
    week_root = out_root / label
    state_path = week_root / "automation" / "evaluation_state.json"
    lock_path = out_root / ".automation" / "codex-evaluation.lock"
    digest = ""
    roster = None
    attempt = 0
    try:
        codex_bin = resolve_codex_bin(configured)
        approval_mode = preflight(codex_bin, codex_environment)
        if args.preflight:
            print("OK: Codex evaluation preflight passed.")
            return 0

        with EvaluationLock(lock_path):
            mode = configured.get("WEEKLY_CODEX_COLLECTION_MODE", "host").strip().lower() or "host"
            collection_timeout = int(configured.get("WEEKLY_CODEX_COLLECTION_TIMEOUT_SECONDS", "600"))
            collection = run_checked(collection_command(mode), collection_timeout)
            if collection.returncode != 0:
                raise EvaluationHarnessError("COLLECTION_FAILED")

            roster = load_roster_evidence(week_root / "exports" / "submission_status.csv")
            digest, attachment_count = build_input_digest(ROOT, week_root, PROMPT_PATH, SCHEMA_PATH)
            state = read_state(state_path)
            report_path = week_root / "summary" / "manager_report.md"
            current_report = report_path.read_text(encoding="utf-8") if report_path.is_file() else ""
            current_errors = validate_manager_report(current_report, label, roster) if current_report else ("REPORT_MISSING",)
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
            markdown, _warnings = parse_codex_result(codex.stdout, label)
            validation_errors = validate_manager_report(markdown, label, roster)
            if validation_errors:
                raise EvaluationHarnessError(validation_errors[0])

            atomic_write_text(report_path, markdown)
            report_digest = hashlib.sha256(markdown.encode("utf-8")).hexdigest()
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
