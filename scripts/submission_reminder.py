from __future__ import annotations

import argparse
import json
import sys
from dataclasses import asdict, dataclass
from datetime import datetime
from typing import Any, Callable

from dingtalk_common import (
    CN_TZ,
    DingTalkError,
    get_access_token,
    load_env,
    require_env,
    submission_window,
    week_range,
)
from download_contacts import download_contacts
from download_reports import download_reports
from submission_roster import (
    EXEMPT_SUBMITTERS_ENV,
    filter_exempt_reports,
    missing_expected_userids,
    parse_exemption_rules,
    partition_expected_submitters,
)


@dataclass(frozen=True)
class ReminderSnapshot:
    weekLabel: str
    cutoff: str
    expectedCount: int
    submittedCount: int
    missingUserIds: list[str]
    unresolvedCount: int


def build_snapshot(
    now: datetime,
    users: list[dict[str, Any]],
    reports: list[dict[str, Any]],
    configured_exemptions: str | None,
) -> ReminderSnapshot:
    if now.tzinfo is None:
        raise ValueError("Reminder cutoff must include a timezone.")
    local_now = now.astimezone(CN_TZ)
    period_start, period_end = week_range("current", local_now)
    submit_start, submit_end = submission_window(period_start, period_end)
    if local_now < submit_start:
        raise ValueError("Current business-week reminder window has not opened yet.")

    rules = parse_exemption_rules(configured_exemptions)
    expected_users, exempt_userids = partition_expected_submitters(users, rules)
    statistical_reports = filter_exempt_reports(reports, rules, exempt_userids)
    missing, submitted_count, unresolved_count = missing_expected_userids(expected_users, statistical_reports)
    week_label = f"{period_start.isocalendar().year}-W{period_start.isocalendar().week:02d}"
    return ReminderSnapshot(
        weekLabel=week_label,
        cutoff=min(local_now, submit_end).isoformat(),
        expectedCount=len(expected_users),
        submittedCount=submitted_count,
        missingUserIds=missing,
        unresolvedCount=unresolved_count,
    )


def collect_snapshot(
    now: datetime | None = None,
    env: dict[str, str] | None = None,
    token_loader: Callable[[dict[str, str]], str] | None = None,
    contacts_loader: Callable[[str, int], tuple[list[dict[str, Any]], list[dict[str, Any]]]] | None = None,
    reports_loader: Callable[..., list[dict[str, Any]]] | None = None,
) -> ReminderSnapshot:
    configured = env if env is not None else load_env()
    local_now = (now or datetime.now(CN_TZ)).astimezone(CN_TZ)
    template_name = require_env(configured, "DINGTALK_REPORT_TEMPLATE")
    root_dept_id = int(configured.get("DINGTALK_ROOT_DEPT_ID", "1"))
    period_start, period_end = week_range("current", local_now)
    submit_start, submit_end = submission_window(period_start, period_end)
    if local_now < submit_start:
        raise ValueError("Current business-week reminder window has not opened yet.")

    access_token = (token_loader or get_access_token)(configured)
    users, _ = (contacts_loader or download_contacts)(access_token, root_dept_id)
    reports = (reports_loader or download_reports)(
        access_token=access_token,
        template_name=template_name,
        start_ms=int(submit_start.timestamp() * 1000),
        end_ms=int(min(local_now, submit_end).timestamp() * 1000),
    )
    return build_snapshot(local_now, users, reports, configured.get(EXEMPT_SUBMITTERS_ENV))


def main() -> int:
    parser = argparse.ArgumentParser(description="Build the current-week Sunday reminder snapshot.")
    parser.add_argument("--json", action="store_true", help="Emit machine-readable recipient userids for Spring.")
    args = parser.parse_args()
    try:
        snapshot = collect_snapshot()
    except (DingTalkError, OSError, ValueError) as exc:
        print(f"FAILED: {exc}", file=sys.stderr)
        return 1
    if args.json:
        print(json.dumps(asdict(snapshot), ensure_ascii=False, separators=(",", ":")))
    else:
        print(
            f"OK: reminder snapshot for {snapshot.weekLabel}; expected={snapshot.expectedCount}; "
            f"submitted={snapshot.submittedCount}; missing={len(snapshot.missingUserIds)}; "
            f"unresolved={snapshot.unresolvedCount}; cutoff={snapshot.cutoff}"
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
