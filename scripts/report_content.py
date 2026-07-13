from __future__ import annotations

import json
from typing import Any

from dingtalk_common import CN_TZ


def format_dt_ms(value: Any) -> str:
    if not isinstance(value, (int, float)):
        return ""
    return __import__("datetime").datetime.fromtimestamp(value / 1000, CN_TZ).strftime("%Y-%m-%d %H:%M:%S")


def _first_value(obj: dict[str, Any], keys: list[str]) -> Any:
    for key in keys:
        if key in obj and obj[key] not in (None, ""):
            return obj[key]
    return ""


def _attachment_names(value: Any) -> str:
    try:
        parsed = json.loads(value) if isinstance(value, str) else value
    except (TypeError, json.JSONDecodeError):
        return "附件元数据不可读"
    if not isinstance(parsed, list):
        return "附件元数据不可读"
    names = [
        str(item.get("fileName") or "未命名附件").strip()
        for item in parsed
        if isinstance(item, dict)
    ]
    return "、".join(names) if names else "无"


def _flatten_content(value: Any) -> str:
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
                title = _first_value(item, ["key", "title", "name", "label"])
                body = _first_value(item, ["value", "content", "text", "content_value"])
                if str(title).strip() == "附件":
                    body = _attachment_names(body)
                parts.append(f"{title}: {_flatten_content(body)}".strip(": "))
            else:
                parts.append(_flatten_content(item))
        return "\n".join(part for part in parts if part)
    if isinstance(value, dict):
        return "\n".join(
            f"{key}: {_flatten_content(child)}"
            for key, child in value.items()
            if _flatten_content(child)
        )
    return str(value)


def report_text(report: dict[str, Any]) -> str:
    for key in ("contents", "content", "text", "report_content", "body"):
        if key in report:
            text = _flatten_content(report[key])
            if text:
                return text
    return _flatten_content(report)
