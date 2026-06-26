#!/usr/bin/env python3
"""Normalize weekly-report inputs into a Markdown analysis pack.

This helper is intentionally dependency-free. It does not summarize; it prepares
contacts, submissions, and missing-candidate lists for Codex to analyze.
"""

from __future__ import annotations

import argparse
import csv
import json
import re
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any

CN_TZ = timezone(timedelta(hours=8))
TEXT_SUFFIXES = {".txt", ".md", ".markdown", ".json"}


def load_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8-sig"))


def dump_jsonish(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, indent=2)


def normalize_name(value: Any) -> str:
    return re.sub(r"\s+", "", str(value or "").strip())


def first_value(obj: dict[str, Any], keys: list[str]) -> Any:
    for key in keys:
        if key in obj and obj[key] not in (None, ""):
            return obj[key]
    return ""


def current_week_label() -> str:
    now = datetime.now(CN_TZ)
    # Company convention: Monday summaries analyze the previous completed week.
    start = now - timedelta(days=now.weekday()) - timedelta(weeks=1)
    iso = start.isocalendar()
    return f"{iso.year}-W{iso.week:02d}"


def find_first_existing(candidates: list[Path]) -> Path | None:
    for path in candidates:
        if path.exists():
            return path
    return None


def find_latest_reports_json(workspace: Path) -> Path | None:
    candidates = sorted(workspace.glob("output/*/raw/reports.json"), key=lambda p: p.stat().st_mtime, reverse=True)
    return candidates[0] if candidates else None


def extract_records_from_json(value: Any) -> list[dict[str, Any]]:
    if isinstance(value, list):
        return [item for item in value if isinstance(item, dict)]
    if not isinstance(value, dict):
        return []
    for key in ("data_list", "list", "users", "reports", "result"):
        child = value.get(key)
        if isinstance(child, list):
            return [item for item in child if isinstance(item, dict)]
        if isinstance(child, dict):
            records = extract_records_from_json(child)
            if records:
                return records
    return [value]


def load_contacts(path: Path | None) -> list[dict[str, Any]]:
    if not path:
        return []
    return extract_records_from_json(load_json(path))


def load_reports_json(path: Path | None) -> list[dict[str, Any]]:
    if not path:
        return []
    return extract_records_from_json(load_json(path))


def flatten_content(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, str):
        return value
    if isinstance(value, (int, float, bool)):
        return str(value)
    if isinstance(value, list):
        parts = []
        for item in value:
            if isinstance(item, dict):
                title = first_value(item, ["key", "title", "name", "label"])
                body = first_value(item, ["value", "content", "text", "content_value"])
                if title or body:
                    parts.append(f"{title}: {flatten_content(body)}".strip(": "))
                else:
                    parts.append(flatten_content(item))
            else:
                parts.append(flatten_content(item))
        return "\n".join(part for part in parts if part)
    if isinstance(value, dict):
        parts = []
        for key, child in value.items():
            if key.lower() in {"userid", "creator_id", "creator_name", "name"}:
                continue
            text = flatten_content(child)
            if text:
                parts.append(f"{key}: {text}")
        return "\n".join(parts)
    return str(value)


def report_owner(report: dict[str, Any]) -> tuple[str, str]:
    userid = str(first_value(report, ["creator_id", "userid", "user_id", "creatorId", "userId"]) or "")
    name = str(first_value(report, ["creator_name", "name", "user_name", "creatorName", "userName"]) or "")
    return userid, name


def report_text(report: dict[str, Any]) -> str:
    for key in ("contents", "content", "text", "report_content", "body"):
        if key in report:
            text = flatten_content(report[key])
            if text:
                return text
    return flatten_content(report)


def load_report_files(reports_dir: Path | None) -> list[dict[str, Any]]:
    if not reports_dir or not reports_dir.exists():
        return []
    reports = []
    for path in sorted(p for p in reports_dir.rglob("*") if p.is_file() and p.suffix.lower() in TEXT_SUFFIXES):
        text = path.read_text(encoding="utf-8-sig", errors="replace")
        if path.suffix.lower() == ".json":
            try:
                loaded = load_json(path)
                for record in extract_records_from_json(loaded):
                    reports.append(record)
                continue
            except Exception:
                pass
        reports.append({"creator_name": path.stem, "source_file": str(path), "content": text})
    return reports


def contact_identity(contact: dict[str, Any]) -> tuple[str, str, str]:
    userid = str(first_value(contact, ["userid", "user_id", "userId", "id"]) or "")
    name = str(first_value(contact, ["name", "username", "user_name", "realName"]) or "")
    dept = first_value(contact, ["dept_name", "department", "deptName", "department_name"])
    if isinstance(dept, list):
        dept = "/".join(str(x) for x in dept)
    return userid, name, str(dept or "")


def make_markdown(
    contacts: list[dict[str, Any]],
    reports: list[dict[str, Any]],
    max_chars: int,
    sources: dict[str, str],
) -> str:
    contacts_by_id: dict[str, tuple[str, str]] = {}
    contacts_by_name: dict[str, tuple[str, str]] = {}
    for contact in contacts:
        userid, name, dept = contact_identity(contact)
        if userid:
            contacts_by_id[userid] = (name, dept)
        if name:
            contacts_by_name[normalize_name(name)] = (userid, dept)

    submitted_ids: set[str] = set()
    submitted_names: set[str] = set()
    normalized_reports = []
    unmatched_reports = []
    for report in reports:
        userid, name = report_owner(report)
        norm_name = normalize_name(name)
        if userid:
            submitted_ids.add(userid)
        elif norm_name:
            submitted_names.add(norm_name)
        else:
            unmatched_reports.append(report)
        normalized_reports.append((userid, name, report))

    missing = []
    for userid, (name, dept) in contacts_by_id.items():
        if userid not in submitted_ids and normalize_name(name) not in submitted_names:
            missing.append((userid, name, dept))

    lines = []
    lines.append("# Weekly Report Analysis Pack")
    lines.append("")
    lines.append("## Sources")
    for key, value in sources.items():
        lines.append(f"- {key}: {value or '未提供'}")
    lines.append("")
    lines.append("## Submission Statistics")
    lines.append(f"- Expected submitters from roster: {len(contacts)}")
    lines.append(f"- Submitted report records: {len(reports)}")
    lines.append(f"- Missing candidates: {len(missing) if contacts else 'N/A (no roster)'}")
    lines.append(f"- Unmatched reports: {len(unmatched_reports)}")
    lines.append("")

    lines.append("## Expected Submitters")
    if contacts:
        lines.append("| userid | name | dept |")
        lines.append("|---|---|---|")
        for contact in contacts:
            userid, name, dept = contact_identity(contact)
            lines.append(f"| {userid} | {name} | {dept} |")
    else:
        lines.append("No roster/contact data found. Missing-submission statistics cannot be confirmed.")
    lines.append("")

    lines.append("## Missing Candidates")
    if contacts:
        lines.append("| userid | name | dept |")
        lines.append("|---|---|---|")
        for userid, name, dept in missing:
            lines.append(f"| {userid} | {name} | {dept} |")
    else:
        lines.append("N/A")
    lines.append("")

    lines.append("## Submitted Reports")
    if normalized_reports:
        for idx, (userid, name, report) in enumerate(normalized_reports, 1):
            text = report_text(report)
            if len(text) > max_chars:
                text = text[:max_chars] + "\n...[truncated]"
            template = first_value(report, ["template_name", "templateName"])
            created = first_value(report, ["create_time", "created_at", "gmt_create", "modified_time"])
            source = first_value(report, ["source_file", "report_id", "id"])
            lines.append(f"### Report {idx}: {name or '未知'} ({userid or 'no userid'})")
            lines.append(f"- template: {template}")
            lines.append(f"- time: {created}")
            lines.append(f"- source: {source}")
            lines.append("")
            lines.append("```text")
            lines.append(text)
            lines.append("```")
            lines.append("")
    else:
        lines.append("No submitted reports found.")
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(description="Prepare weekly-report inputs for Codex analysis.")
    parser.add_argument("--workspace", default=".", help="Workspace root. Default: current directory.")
    parser.add_argument("--contacts", help="Contacts/users JSON path. Default: auto-detect output/contacts/users.json.")
    parser.add_argument("--reports-json", help="DingTalk reports JSON path. Default: latest output/*/raw/reports.json.")
    parser.add_argument("--reports-dir", help="Directory containing local .txt/.md/.json weekly reports.")
    parser.add_argument("--week-label", default=current_week_label(), help="Output week label, e.g. 2026-W26.")
    parser.add_argument("--out", help="Output Markdown path. Default: output/<week>/analysis/analysis_input.md.")
    parser.add_argument("--max-chars", type=int, default=12000, help="Max chars per report in Markdown pack.")
    args = parser.parse_args()

    workspace = Path(args.workspace).resolve()
    contacts_path = Path(args.contacts).resolve() if args.contacts else find_first_existing([
        workspace / "output" / "contacts" / "users.json",
        workspace / "contacts" / "users.json",
        workspace / "contacts.json",
    ])
    reports_json_path = Path(args.reports_json).resolve() if args.reports_json else find_latest_reports_json(workspace)
    reports_dir = Path(args.reports_dir).resolve() if args.reports_dir else None
    out_path = Path(args.out).resolve() if args.out else workspace / "output" / args.week_label / "analysis" / "analysis_input.md"

    contacts = load_contacts(contacts_path)
    reports = load_reports_json(reports_json_path) + load_report_files(reports_dir)
    md = make_markdown(
        contacts=contacts,
        reports=reports,
        max_chars=args.max_chars,
        sources={
            "workspace": str(workspace),
            "contacts": str(contacts_path) if contacts_path else "",
            "reports_json": str(reports_json_path) if reports_json_path else "",
            "reports_dir": str(reports_dir) if reports_dir else "",
        },
    )
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(md, encoding="utf-8")
    print(f"Wrote {out_path}")
    print(f"Contacts: {len(contacts)}; reports: {len(reports)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
