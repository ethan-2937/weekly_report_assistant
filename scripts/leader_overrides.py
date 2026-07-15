from __future__ import annotations

import json
from typing import Any


LEADER_OVERRIDES_ENV = "WEEKLY_REPORT_LEADER_OVERRIDES"
SUBORDINATE_KEYS = ("subordinate_userids", "subordinate_user_ids", "managed_userids", "subordinates")


class LeaderOverrideError(ValueError):
    pass


def apply_leader_overrides(users: list[dict[str, Any]], raw_config: str | None) -> list[dict[str, Any]]:
    """Apply private, explicit leader relationships after resolving selectors to userid."""
    if not str(raw_config or "").strip():
        return users
    try:
        configured = json.loads(str(raw_config))
    except json.JSONDecodeError as exc:
        raise LeaderOverrideError("LEADER_OVERRIDES_JSON_INVALID") from exc
    if not isinstance(configured, dict):
        raise LeaderOverrideError("LEADER_OVERRIDES_SCHEMA_INVALID")

    result = [dict(user) for user in users]
    resolve = _selector_resolver(result)
    assignments: list[tuple[str, bool, list[str]]] = []
    subordinate_owners: dict[str, str] = {}
    for leader_selector, rule in configured.items():
        if not isinstance(leader_selector, str) or not isinstance(rule, dict):
            raise LeaderOverrideError("LEADER_OVERRIDES_SCHEMA_INVALID")
        leader_id = resolve(leader_selector)
        is_leader = rule.get("leader")
        subordinate_selectors = rule.get("subordinates", [])
        if not isinstance(is_leader, bool) or not isinstance(subordinate_selectors, list):
            raise LeaderOverrideError("LEADER_OVERRIDES_SCHEMA_INVALID")
        if not is_leader and subordinate_selectors:
            raise LeaderOverrideError("LEADER_OVERRIDES_NON_LEADER_HAS_SUBORDINATES")

        subordinate_ids: list[str] = []
        for selector in subordinate_selectors:
            if not isinstance(selector, str):
                raise LeaderOverrideError("LEADER_OVERRIDES_SCHEMA_INVALID")
            subordinate_id = resolve(selector)
            if subordinate_id == leader_id:
                raise LeaderOverrideError("LEADER_OVERRIDES_SELF_MAPPING")
            owner = subordinate_owners.setdefault(subordinate_id, leader_id)
            if owner != leader_id:
                raise LeaderOverrideError("LEADER_OVERRIDES_MULTIPLE_LEADERS")
            if subordinate_id not in subordinate_ids:
                subordinate_ids.append(subordinate_id)
        assignments.append((leader_id, is_leader, subordinate_ids))

    users_by_id = {str(user.get("userid") or "").strip(): user for user in result}
    for leader_id, is_leader, subordinate_ids in assignments:
        leader = users_by_id[leader_id]
        leader["leader"] = is_leader
        for key in SUBORDINATE_KEYS:
            leader.pop(key, None)
        if is_leader:
            leader["subordinate_userids"] = subordinate_ids
    return result


def _selector_resolver(users: list[dict[str, Any]]):
    by_userid: dict[str, list[str]] = {}
    by_name: dict[str, list[str]] = {}
    for user in users:
        userid = str(user.get("userid") or "").strip()
        name = str(user.get("name") or "").strip()
        if not userid:
            continue
        by_userid.setdefault(userid, []).append(userid)
        if name:
            by_name.setdefault(name, []).append(userid)

    def resolve(selector: str) -> str:
        prefix, separator, value = selector.strip().partition(":")
        if not separator or not value.strip():
            raise LeaderOverrideError("LEADER_OVERRIDE_SELECTOR_INVALID")
        normalized_prefix = prefix.strip().upper()
        matches = by_userid.get(value.strip(), []) if normalized_prefix == "USERID" else by_name.get(value.strip(), [])
        if normalized_prefix not in {"USERID", "NAME"}:
            raise LeaderOverrideError("LEADER_OVERRIDE_SELECTOR_INVALID")
        if not matches:
            raise LeaderOverrideError("LEADER_OVERRIDE_SELECTOR_UNRESOLVED")
        if len(matches) != 1:
            raise LeaderOverrideError("LEADER_OVERRIDE_SELECTOR_AMBIGUOUS")
        return matches[0]

    return resolve
