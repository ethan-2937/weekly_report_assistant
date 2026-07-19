from __future__ import annotations

import argparse
import json

from attachment_download import download_team_lead_attachments
from dingtalk_common import (
    DingTalkError,
    add_week_args,
    get_access_token,
    load_env,
    output_root,
    resolve_week_args,
    submission_window,
    write_json,
)
from download_contacts import download_contacts
from download_reports import download_report_sets
from leader_overrides import LEADER_OVERRIDES_ENV, apply_leader_overrides
from submission_roster import (
    EXEMPT_SUBMITTERS_ENV,
    filter_exempt_reports,
    parse_exemption_rules,
    partition_expected_submitters,
)
from weekly_outputs import write_submission_outputs

def main() -> int:
    parser = argparse.ArgumentParser(description="Download last week's DingTalk reports and prepare analysis files.")
    add_week_args(parser)
    parser.add_argument("--skip-contacts", action="store_true", help="Use existing output/contacts/*.json files.")
    args = parser.parse_args()
    try:
        env = load_env()
        period_start, period_end, week_label = resolve_week_args(args)
        submit_start, submit_end = submission_window(period_start, period_end)
        root_dept_id = int(env.get("DINGTALK_ROOT_DEPT_ID", "1"))
        template_name = env.get("DINGTALK_REPORT_TEMPLATE", "").strip()
        out_root = output_root(env)
        access_token = get_access_token(env)
        if args.skip_contacts:
            users = json.loads((out_root / "contacts" / "users.json").read_text(encoding="utf-8"))
            departments = json.loads((out_root / "contacts" / "departments.json").read_text(encoding="utf-8"))
        else:
            users, departments = download_contacts(access_token, root_dept_id)
            write_json(out_root / "contacts" / "users.json", users)
            write_json(out_root / "contacts" / "departments.json", departments)
        users = apply_leader_overrides(users, env.get(LEADER_OVERRIDES_ENV))
        reports, all_reports = download_report_sets(
            access_token=access_token,
            primary_template=template_name,
            start_ms=int(submit_start.timestamp() * 1000),
            end_ms=int(submit_end.timestamp() * 1000),
        )
        exemption_rules = parse_exemption_rules(env.get(EXEMPT_SUBMITTERS_ENV))
        expected_users, exempt_userids = partition_expected_submitters(users, exemption_rules)
        statistical_reports = filter_exempt_reports(reports, exemption_rules, exempt_userids)
        week_out = out_root / week_label
        write_json(week_out / "raw" / "reports.json", reports)
        write_json(week_out / "raw" / "all_reports.json", all_reports)
        attachment_downloads = download_team_lead_attachments(access_token, expected_users, statistical_reports, week_out)
        period_text = f"{period_start.strftime('%Y-%m-%d')} 至 {period_end.strftime('%Y-%m-%d')}"
        submission_window_text = f"{submit_start.strftime('%Y-%m-%d')} 至 {submit_end.strftime('%Y-%m-%d')}"
        write_submission_outputs(
            week_out,
            expected_users,
            departments,
            statistical_reports,
            week_label,
            template_name,
            period_text,
            submission_window_text,
            attachment_downloads,
        )
    except (DingTalkError, OSError, json.JSONDecodeError, ValueError) as exc:
        print(f"FAILED: {exc}")
        return 1

    print(f"OK: weekly data prepared for {week_label}.")
    print(f"Report period: {period_start.isoformat()} -> {period_end.isoformat()}")
    print(f"Submission window: {submit_start.isoformat()} -> {submit_end.isoformat()}")
    print(f"Expected users: {len(expected_users)}; exempt users: {len(users) - len(expected_users)}; departments: {len(departments)}; statistical reports: {len(statistical_reports)}")
    print(f"Output: {week_out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
