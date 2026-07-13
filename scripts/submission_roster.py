from __future__ import annotations

import re
import unicodedata
from dataclasses import dataclass
from typing import Any


EXEMPT_SUBMITTERS_ENV = "WEEKLY_REPORT_EXEMPT_SUBMITTERS"


def _normalize_identity(value: Any) -> str:
    text = unicodedata.normalize("NFKC", str(value or "")).strip()
    return re.sub(r"\s+", " ", text)


@dataclass(frozen=True)
class ExemptionRules:
    userids: frozenset[str]
    names: frozenset[str]
    compatible_text: frozenset[str]

    def matches(self, userid: Any, name: Any) -> bool:
        normalized_userid = _normalize_identity(userid)
        normalized_name = _normalize_identity(name)
        return (
            normalized_userid in self.userids
            or normalized_name in self.names
            or normalized_userid in self.compatible_text
            or normalized_name in self.compatible_text
        )


def parse_exemption_rules(configured: str | None) -> ExemptionRules:
    userids: set[str] = set()
    names: set[str] = set()
    compatible_text: set[str] = set()

    for raw_rule in re.split(r"[;,；，\n]+", configured or ""):
        rule = _normalize_identity(raw_rule)
        if not rule:
            continue
        if ":" not in rule:
            compatible_text.add(rule)
            continue

        prefix, value = rule.split(":", 1)
        normalized_value = _normalize_identity(value)
        if not normalized_value or prefix.upper() not in {"NAME", "USERID"}:
            raise ValueError(
                f"Invalid {EXEMPT_SUBMITTERS_ENV} rule; use NAME:<exact-name> or USERID:<exact-userid>."
            )
        if prefix.upper() == "USERID":
            userids.add(normalized_value)
        else:
            names.add(normalized_value)

    return ExemptionRules(frozenset(userids), frozenset(names), frozenset(compatible_text))


def partition_expected_submitters(
    users: list[dict[str, Any]], rules: ExemptionRules
) -> tuple[list[dict[str, Any]], set[str]]:
    expected: list[dict[str, Any]] = []
    exempt_userids: set[str] = set()
    for user in users:
        userid = _normalize_identity(user.get("userid"))
        if rules.matches(userid, user.get("name")):
            if userid:
                exempt_userids.add(userid)
        else:
            expected.append(user)
    return expected, exempt_userids


def filter_exempt_reports(
    reports: list[dict[str, Any]], rules: ExemptionRules, exempt_userids: set[str]
) -> list[dict[str, Any]]:
    return [
        report
        for report in reports
        if _normalize_identity(report.get("creator_id")) not in exempt_userids
        and not rules.matches(report.get("creator_id"), report.get("creator_name"))
    ]
