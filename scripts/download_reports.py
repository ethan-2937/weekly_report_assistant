from __future__ import annotations

import argparse
from typing import Any

from dingtalk_common import (
    DingTalkError,
    add_week_args,
    get_access_token,
    load_env,
    output_root,
    resolve_week_args,
    submission_window,
    topapi_post,
    write_json,
)


def download_reports(access_token: str, template_name: str, start_ms: int, end_ms: int) -> list[dict[str, Any]]:
    reports: list[dict[str, Any]] = []
    cursor = 0
    while True:
        payload = {
            "start_time": start_ms,
            "end_time": end_ms,
            "cursor": cursor,
            "size": 20,
        }
        if template_name:
            payload["template_name"] = template_name
        result = topapi_post("/topapi/report/list", payload, access_token).get("result", {})
        reports.extend(result.get("data_list", []) or [])
        if not result.get("has_more"):
            break
        cursor = int(result.get("next_cursor", 0))
    return reports


def main() -> int:
    parser = argparse.ArgumentParser(description="Download DingTalk weekly reports.")
    add_week_args(parser)
    args = parser.parse_args()

    try:
        env = load_env()
        period_start, period_end, week_label = resolve_week_args(args)
        submit_start, submit_end = submission_window(period_start, period_end)
        access_token = get_access_token(env)
        template_name = env.get("DINGTALK_REPORT_TEMPLATE", "").strip()
        reports = download_reports(
            access_token=access_token,
            template_name=template_name,
            start_ms=int(submit_start.timestamp() * 1000),
            end_ms=int(submit_end.timestamp() * 1000),
        )
        out_path = output_root(env) / week_label / "raw" / "reports.json"
        write_json(out_path, reports)
    except DingTalkError as exc:
        print(f"FAILED: {exc}")
        return 1

    print(f"OK: downloaded {len(reports)} reports for {week_label}.")
    print(f"Report period: {period_start.isoformat()} -> {period_end.isoformat()}")
    print(f"Submission window: {submit_start.isoformat()} -> {submit_end.isoformat()}")
    print(f"Output: {out_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
