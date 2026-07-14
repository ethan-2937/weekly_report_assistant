from __future__ import annotations

import csv
from pathlib import Path
from typing import Any

from attachment_download import DownloadedAttachment
from leader_compliance import build_team_lead_evidence, render_team_lead_input
from report_content import format_dt_ms, report_text


CSV_FIELDS = [
    "提交状态",
    "姓名",
    "userid",
    "部门",
    "是否负责人候选",
    "职务",
    "周报部门",
    "提交时间",
    "report_id",
    "模板",
]


def dept_names(user: dict[str, Any], dept_by_id: dict[Any, str]) -> str:
    names: list[str] = []
    for dept_id in user.get("dept_id_list") or []:
        names.append(dept_by_id.get(dept_id, str(dept_id)))
    return "/".join(names)


def write_submission_outputs(
    out_dir: Path,
    users: list[dict[str, Any]],
    departments: list[dict[str, Any]],
    reports: list[dict[str, Any]],
    week_label: str,
    template_name: str,
    period_text: str,
    submission_window_text: str,
    attachment_downloads: dict[str, tuple[DownloadedAttachment, ...]] | None = None,
) -> None:
    dept_by_id = {dept.get("dept_id"): dept.get("name") for dept in departments}
    reports_by_user = {report.get("creator_id"): report for report in reports if report.get("creator_id")}
    users_by_id = {str(user.get("userid") or ""): user for user in users if user.get("userid")}

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

    with (exports_dir / "submission_status.csv").open("w", newline="", encoding="utf-8-sig") as file:
        writer = csv.DictWriter(file, fieldnames=CSV_FIELDS)
        writer.writeheader()
        writer.writerows(rows)

    submitted = [row for row in rows if row["提交状态"] == "已提交"]
    missing = [row for row in rows if row["提交状态"] == "未提交"]
    leaders = [row for row in rows if row["是否负责人候选"] == "是"]
    leader_evidence = build_team_lead_evidence(
        users,
        reports,
        dept_by_id,
        attachment_downloads,
    )
    with (exports_dir / "leader_subordinates.csv").open("w", newline="", encoding="utf-8-sig") as file:
        writer = csv.DictWriter(
            file,
            fieldnames=["负责人", "负责人userid", "部门", "映射来源", "下属员工", "下属userid", "未提交下属", "履职风险"],
        )
        writer.writeheader()
        for item in leader_evidence:
            writer.writerow(
                {
                    "负责人": item.name,
                    "负责人userid": item.userid,
                    "部门": item.dept,
                    "映射来源": item.subordinate_source,
                    "下属员工": "、".join(item.subordinate_names),
                    "下属userid": "、".join(item.subordinate_userids),
                    "未提交下属": "、".join(item.subordinate_missing_names),
                    "履职风险": item.subordinate_status,
                }
            )

    summary_lines = [
        "# 周报提交验证结果",
        "",
        f"- 周报周期：{week_label}（{period_text}）",
        f"- 提交归属窗口：{submission_window_text}（周四截止，周一至周三补交归上一周）",
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
            "- 明确配置的免交人员已在生成统计前排除。",
        ]
    )
    (summary_dir / "submission_check.md").write_text("\n".join(summary_lines) + "\n", encoding="utf-8")

    analysis_lines = [
        "# Weekly Report Analysis Pack",
        "",
        "## Sources",
        f"- week_label: {week_label}",
        f"- report_period: {period_text}",
        f"- submission_window: {submission_window_text}",
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
        "## Per-Person Evaluation Dimensions",
        "Use these dimensions to replace the old per-person evaluation fields. Do not create a separate screening section in the final manager report. Keep boss decision/support items in a separate top-level module.",
        "| Leader dimension | Required field | Qualified standard |",
        "|---|---|---|",
        "| 谁在真干活（虚实盘） | 本周完成成果/本周成果 | 必须有产出物名词，不能光写动作。 |",
        "| 谁时间分配畸形（健康度） | 工时投入分析/工时占比 + 通讯录岗位角色 | 用百分比写清楚，结合岗位角色比对基准线；没有基准线时只标明显异常。 |",
        "| AI用得怎样（红黑榜） | AI应用及效果/AI使用 | 写清楚工具 + 效果，含 `【可复用】` 的自动入选亮点。 |",
        "| 下周计划合不合格 | 下周计划（含交付时间）/下周计划 | 必须带日期和产出，不能只有 `继续` 类表述。 |",
        "| 哪里需要老板拍板 | 风险/阻塞/求助信息（如有） | 简述卡点 + 需要什么支持，正式报告中自动置顶；若钉钉模板没有该字段，不因缺失判定模板不合格。 |",
        "",
    ]
    analysis_lines.extend(render_team_lead_input(leader_evidence))
    analysis_lines.extend(
        [
            "## Missing Candidates",
            "| userid | name | dept | leader | title |",
            "|---|---|---|---|---|",
        ]
    )
    for row in missing:
        analysis_lines.append(
            f"| {row['userid']} | {row['姓名']} | {row['部门']} | {row['是否负责人候选']} | {row['职务']} |"
        )

    analysis_lines.extend(["", "## Submitted Reports"])
    for index, report in enumerate(reports, 1):
        text = report_text(report)
        if len(text) > 12000:
            text = text[:12000] + "\n...[truncated]"
        roster_user = users_by_id.get(str(report.get("creator_id") or ""), {})
        analysis_lines.extend(
            [
                f"### Report {index}: {report.get('creator_name') or '未知'} ({report.get('creator_id') or 'no userid'})",
                f"- dept: {report.get('dept_name') or dept_names(roster_user, dept_by_id)}",
                f"- title: {roster_user.get('title') or ''}",
                f"- leader_candidate: {'是' if roster_user.get('leader') is True else '否'}",
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
