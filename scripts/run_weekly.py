from __future__ import annotations

import argparse
import csv
import json
from collections import deque
from pathlib import Path
from typing import Any

from dingtalk_common import (
    CN_TZ,
    DingTalkError,
    add_week_args,
    get_access_token,
    load_env,
    output_root,
    resolve_week_args,
    topapi_post,
    write_json,
)
from download_reports import download_reports


def list_sub_departments(access_token: str, dept_id: int) -> list[dict[str, Any]]:
    result = topapi_post("/topapi/v2/department/listsub", {"dept_id": dept_id}, access_token)
    return result.get("result", []) or []


def list_department_users(access_token: str, dept_id: int) -> list[dict[str, Any]]:
    users: list[dict[str, Any]] = []
    cursor = 0
    while True:
        result = topapi_post(
            "/topapi/v2/user/list",
            {"dept_id": dept_id, "cursor": cursor, "size": 100, "language": "zh_CN"},
            access_token,
        ).get("result", {})
        users.extend(result.get("list", []) or [])
        if not result.get("has_more"):
            break
        cursor = int(result.get("next_cursor", 0))
    return users


def download_contacts(access_token: str, root_dept_id: int) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    departments: list[dict[str, Any]] = []
    users_by_id: dict[str, dict[str, Any]] = {}
    queue: deque[int] = deque([root_dept_id])

    while queue:
        dept_id = queue.popleft()
        for user in list_department_users(access_token, dept_id):
            userid = user.get("userid")
            if userid:
                users_by_id[userid] = user

        children = list_sub_departments(access_token, dept_id)
        departments.extend(children)
        for dept in children:
            child_id = dept.get("dept_id")
            if child_id is not None:
                queue.append(int(child_id))

    return list(users_by_id.values()), departments


def dept_names(user: dict[str, Any], dept_by_id: dict[Any, str]) -> str:
    names: list[str] = []
    for dept_id in user.get("dept_id_list") or []:
        names.append(dept_by_id.get(dept_id, str(dept_id)))
    return "/".join(names)


def format_dt_ms(value: Any) -> str:
    if not isinstance(value, (int, float)):
        return ""
    return __import__("datetime").datetime.fromtimestamp(value / 1000, CN_TZ).strftime("%Y-%m-%d %H:%M:%S")


def first_value(obj: dict[str, Any], keys: list[str]) -> Any:
    for key in keys:
        if key in obj and obj[key] not in (None, ""):
            return obj[key]
    return ""


def flatten_content(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, str):
        return value
    if isinstance(value, (int, float, bool)):
        return str(value)
    if isinstance(value, list):
        parts: list[str] = []
        for item in value:
            if isinstance(item, dict):
                title = first_value(item, ["key", "title", "name", "label"])
                body = first_value(item, ["value", "content", "text", "content_value"])
                parts.append(f"{title}: {flatten_content(body)}".strip(": "))
            else:
                parts.append(flatten_content(item))
        return "\n".join(part for part in parts if part)
    if isinstance(value, dict):
        return "\n".join(f"{key}: {flatten_content(child)}" for key, child in value.items() if flatten_content(child))
    return str(value)


def report_text(report: dict[str, Any]) -> str:
    for key in ("contents", "content", "text", "report_content", "body"):
        if key in report:
            text = flatten_content(report[key])
            if text:
                return text
    return flatten_content(report)


def write_submission_outputs(
    out_dir: Path,
    users: list[dict[str, Any]],
    departments: list[dict[str, Any]],
    reports: list[dict[str, Any]],
    week_label: str,
    template_name: str,
    range_text: str,
) -> None:
    dept_by_id = {dept.get("dept_id"): dept.get("name") for dept in departments}
    reports_by_user = {report.get("creator_id"): report for report in reports if report.get("creator_id")}

    rows: list[dict[str, str]] = []
    for user in sorted(users, key=lambda item: (dept_names(item, dept_by_id), item.get("name") or "")):
        report = reports_by_user.get(user.get("userid"))
        rows.append(
            {
                "提交状态": "已提交" if report else "未提交",
                "姓名": str(user.get("name") or ""),
                "userid": str(user.get("userid") or ""),
                "部门": dept_names(user, dept_by_id),
                "是否负责人候选": "是" if user.get("leader") is True else "否",
                "职务": str(user.get("title") or ""),
                "周报部门": str((report or {}).get("dept_name") or ""),
                "提交时间": format_dt_ms((report or {}).get("create_time") or (report or {}).get("modified_time")),
                "report_id": str((report or {}).get("report_id") or ""),
                "模板": str((report or {}).get("template_name") or ""),
            }
        )

    exports_dir = out_dir / "exports"
    summary_dir = out_dir / "summary"
    analysis_dir = out_dir / "analysis"
    exports_dir.mkdir(parents=True, exist_ok=True)
    summary_dir.mkdir(parents=True, exist_ok=True)
    analysis_dir.mkdir(parents=True, exist_ok=True)

    csv_path = exports_dir / "submission_status.csv"
    if rows:
        with csv_path.open("w", newline="", encoding="utf-8-sig") as file:
            writer = csv.DictWriter(file, fieldnames=list(rows[0].keys()))
            writer.writeheader()
            writer.writerows(rows)

    submitted = [row for row in rows if row["提交状态"] == "已提交"]
    missing = [row for row in rows if row["提交状态"] == "未提交"]
    leaders = [row for row in rows if row["是否负责人候选"] == "是"]

    summary_lines = [
        "# 周报提交验证结果",
        "",
        f"- 统计周期：{week_label}（{range_text}）",
        f"- 周报模板：{template_name or '未筛选模板'}",
        f"- 通讯录范围人数：{len(rows)}",
        f"- 已提交人数：{len(submitted)}",
        f"- 未提交候选人数：{len(missing)}",
        f"- 团队负责人候选人数：{len(leaders)}",
        "",
        "## 已提交名单",
        "| 姓名 | 部门 | 提交时间 |",
        "|---|---|---|",
    ]
    for row in submitted:
        summary_lines.append(f"| {row['姓名']} | {row['周报部门'] or row['部门']} | {row['提交时间']} |")
    summary_lines.extend(["", "## 团队负责人候选未提交", "| 姓名 | 部门 | 职务 |", "|---|---|---|"])
    for row in missing:
        if row["是否负责人候选"] == "是":
            summary_lines.append(f"| {row['姓名']} | {row['部门']} | {row['职务']} |")
    summary_lines.extend(
        [
            "",
            "## 说明",
            "- 当前按钉钉应用授权范围内的通讯录成员作为应交候选。",
            "- 若部分高管、职能人员、外部账号不需要提交，请后续在规则文件中排除。",
        ]
    )
    (summary_dir / "submission_check.md").write_text("\n".join(summary_lines) + "\n", encoding="utf-8")

    analysis_lines = [
        "# Weekly Report Analysis Pack",
        "",
        "## Sources",
        f"- week_label: {week_label}",
        f"- range: {range_text}",
        f"- template: {template_name or '未筛选模板'}",
        f"- contacts: {len(users)} users",
        f"- reports: {len(reports)} records",
        "",
        "## Submission Statistics",
        f"- Expected submitters from roster: {len(users)}",
        f"- Submitted report records: {len(reports)}",
        f"- Submitted unique users: {len(set(report.get('creator_id') for report in reports if report.get('creator_id')))}",
        f"- Missing candidates: {len(missing)}",
        "",
        "## Latest Screening Criteria",
        "| Leader dimension | Required field | Qualified standard |",
        "|---|---|---|",
        "| 谁在真干活（虚实盘） | 本周成果 | 必须有产出物名词，不能光写动作。 |",
        "| 谁时间分配畸形（健康度） | 工时占比 + 岗位角色 | 用百分比写清楚，结合岗位角色比对基准线；没有基准线时只标明显异常。 |",
        "| AI用得怎样（红黑榜） | AI使用 | 写清楚工具 + 效果，含 `【可复用】` 的自动入选亮点。 |",
        "| 下周计划合不合格 | 下周计划 | 必须带日期和产出，不能只有 `继续` 类表述。 |",
        "| 哪里需要老板拍板 | 风险与求助 | 简述卡点 + 需要什么支持，正式报告中自动置顶。 |",
        "",
        "## Missing Candidates",
        "| userid | name | dept | leader | title |",
        "|---|---|---|---|---|",
    ]
    for row in missing:
        analysis_lines.append(
            f"| {row['userid']} | {row['姓名']} | {row['部门']} | {row['是否负责人候选']} | {row['职务']} |"
        )

    analysis_lines.extend(["", "## Submitted Reports"])
    for index, report in enumerate(reports, 1):
        text = report_text(report)
        if len(text) > 12000:
            text = text[:12000] + "\n...[truncated]"
        analysis_lines.extend(
            [
                f"### Report {index}: {report.get('creator_name') or '未知'} ({report.get('creator_id') or 'no userid'})",
                f"- dept: {report.get('dept_name') or ''}",
                f"- template: {report.get('template_name') or ''}",
                f"- time: {format_dt_ms(report.get('create_time') or report.get('modified_time'))}",
                f"- report_id: {report.get('report_id') or ''}",
                "",
                "```text",
                text,
                "```",
                "",
            ]
        )
    (analysis_dir / "analysis_input.md").write_text("\n".join(analysis_lines), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description="Download last week's DingTalk reports and prepare analysis files.")
    add_week_args(parser)
    parser.add_argument("--skip-contacts", action="store_true", help="Use existing output/contacts/*.json files.")
    args = parser.parse_args()

    try:
        env = load_env()
        start, end, week_label = resolve_week_args(args)
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

        reports = download_reports(
            access_token=access_token,
            template_name=template_name,
            start_ms=int(start.timestamp() * 1000),
            end_ms=int(end.timestamp() * 1000),
        )
        week_out = out_root / week_label
        write_json(week_out / "raw" / "reports.json", reports)
        range_text = f"{start.strftime('%Y-%m-%d')} 至 {end.strftime('%Y-%m-%d')}"
        write_submission_outputs(week_out, users, departments, reports, week_label, template_name, range_text)
    except (DingTalkError, OSError, json.JSONDecodeError, ValueError) as exc:
        print(f"FAILED: {exc}")
        return 1

    print(f"OK: weekly data prepared for {week_label}.")
    print(f"Range: {start.isoformat()} -> {end.isoformat()}")
    print(f"Users: {len(users)}; departments: {len(departments)}; reports: {len(reports)}")
    print(f"Output: {week_out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
