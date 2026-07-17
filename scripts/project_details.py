from __future__ import annotations

import csv
import re
from pathlib import Path
from typing import Any


CSV_FIELDS = [
    "姓名",
    "userid",
    "部门",
    "序号",
    "产品线",
    "客户名称",
    "项目名称",
    "本周投入工时（天）",
    "本周差旅费用",
    "本周招待费用",
]

FIELD_ALIASES = {
    "产品线": ("归属于哪条产品线",),
    "客户名称": ("本周服务的客户名称",),
    "项目名称": ("本周服务的项目名称",),
    "本周投入工时（天）": ("本周投入工时合计天",),
    "本周差旅费用": ("本周您产生的差旅费用", "本周产生的差旅费用"),
    "本周招待费用": ("本周您产生的招待费用", "本周产生的招待费用"),
}


def extract_project_detail(report: dict[str, Any]) -> dict[str, str] | None:
    fields = _report_fields(report)
    detail = {
        output_field: _find_value(fields, aliases)
        for output_field, aliases in FIELD_ALIASES.items()
    }
    return detail if any(detail.values()) else None


def write_project_details(
    path: Path,
    users: list[dict[str, Any]],
    reports: list[dict[str, Any]],
    dept_by_id: dict[Any, str],
) -> None:
    users_by_id = {
        str(user.get("userid") or ""): user
        for user in users
        if user.get("userid")
    }
    latest_reports: dict[str, dict[str, Any]] = {}
    for report in reports:
        userid = str(report.get("creator_id") or "")
        if not userid:
            continue
        current = latest_reports.get(userid)
        if current is None or _report_time(report) >= _report_time(current):
            latest_reports[userid] = report

    rows: list[dict[str, str]] = []
    for userid, report in latest_reports.items():
        detail = extract_project_detail(report)
        if detail is None:
            continue
        user = users_by_id.get(userid, {})
        departments = [
            dept_by_id.get(dept_id, str(dept_id))
            for dept_id in user.get("dept_id_list") or []
        ]
        rows.append(
            {
                "姓名": _clean(user.get("name") or report.get("creator_name")),
                "userid": userid,
                "部门": "/".join(departments) or _clean(report.get("dept_name")),
                **detail,
            }
        )

    rows.sort(key=lambda row: (row["部门"], row["姓名"], row["userid"]))
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8-sig") as file:
        writer = csv.DictWriter(file, fieldnames=CSV_FIELDS)
        writer.writeheader()
        for index, row in enumerate(rows, 1):
            writer.writerow({**row, "序号": str(index)})


def _report_fields(report: dict[str, Any]) -> list[tuple[str, str]]:
    contents = report.get("contents")
    if not isinstance(contents, list):
        return []
    fields: list[tuple[str, str]] = []
    for item in contents:
        if not isinstance(item, dict):
            continue
        label = _clean(item.get("key") or item.get("title") or item.get("name"))
        value = _clean(_first_present(item, ("value", "content", "text")))
        if label:
            fields.append((label, value))
    return fields


def _find_value(fields: list[tuple[str, str]], aliases: tuple[str, ...]) -> str:
    normalized_aliases = tuple(_normalize(alias) for alias in aliases)
    for label, value in fields:
        normalized_label = _normalize(label)
        if any(alias in normalized_label for alias in normalized_aliases):
            return value
    return ""


def _normalize(value: str) -> str:
    return re.sub(r"[\s　（）()【】\[\]，,。；;：:、/\\\-？?]", "", value).lower()


def _report_time(report: dict[str, Any]) -> int:
    values = [report.get("create_time"), report.get("modified_time")]
    return max((int(value) for value in values if isinstance(value, (int, float))), default=0)


def _clean(value: Any) -> str:
    text = ("" if value is None else str(value)).replace("\x00", "")
    return re.sub(r"[\r\n]+", " ", text).strip()


def _first_present(item: dict[str, Any], keys: tuple[str, ...]) -> Any:
    for key in keys:
        if key in item and item[key] is not None:
            return item[key]
    return ""
