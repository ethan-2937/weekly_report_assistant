from __future__ import annotations

from typing import Any


def _text(value: Any) -> str:
    return str(value or "").strip()


def _explicit_ids(user: dict[str, Any]) -> set[str] | None:
    keys = ("subordinate_userids", "subordinate_user_ids", "managed_userids", "subordinates")
    present = next((key for key in keys if key in user), None)
    if present is None:
        return None
    raw = user.get(present)
    values = raw if isinstance(raw, (list, tuple, set)) else str(raw or "").replace(";", ",").split(",")
    result: set[str] = set()
    for value in values:
        if isinstance(value, dict):
            value = value.get("userid") or value.get("user_id") or value.get("id")
        if _text(value):
            result.add(_text(value))
    return result


def map_subordinates(
    leader: dict[str, Any], users: list[dict[str, Any]], reports_by_user: dict[str, dict[str, Any]]
) -> tuple[tuple[str, ...], tuple[str, ...], str, tuple[str, ...], str]:
    """Return names, ids, source, missing names and the compliance status."""
    user_by_id = {_text(user.get("userid")): user for user in users if _text(user.get("userid"))}
    explicit = _explicit_ids(leader)
    if explicit is not None:
        candidate_ids = explicit & set(user_by_id)
        source = "USERID"
        status = "已按 userid 映射"
    else:
        leader_depts = {_text(value) for value in leader.get("dept_id_list") or [] if _text(value)}
        candidate_ids = {
            _text(user.get("userid"))
            for user in users
            if _text(user.get("userid")) != _text(leader.get("userid"))
            and not user.get("leader")
            and leader_depts.intersection({_text(value) for value in user.get("dept_id_list") or []})
        }
        source = "DEPT"
        status = "按所属部门映射，待上级确认" if leader_depts else "无法判断（负责人无部门）"
    ordered = sorted(candidate_ids, key=lambda userid: (_text(user_by_id[userid].get("name")), userid))
    names = tuple(_text(user_by_id[userid].get("name")) for userid in ordered)
    missing = tuple(
        _text(user_by_id[userid].get("name"))
        for userid in ordered
        if userid not in reports_by_user
    )
    if missing:
        status = "不合格（下属未提交）"
    return names, tuple(ordered), source, missing, status
