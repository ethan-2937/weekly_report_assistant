from __future__ import annotations

import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from submission_roster import (  # noqa: E402
    filter_exempt_reports,
    parse_exemption_rules,
    partition_expected_submitters,
)


class SubmissionRosterTests(unittest.TestCase):
    def test_userid_name_and_compatible_rules_merge_without_partial_matches(self) -> None:
        rules = parse_exemption_rules(
            "USERID:test-user-001; NAME:示例员工乙；示例员工丙"
        )
        users = [
            {"userid": "test-user-001", "name": "同名示例"},
            {"userid": "test-user-002", "name": "示例员工乙"},
            {"userid": "test-user-003", "name": "示例员工丙"},
            {"userid": "test-user-004", "name": "示例员工丙扩展"},
            {"userid": "test-user-005", "name": "示例员工丁"},
        ]

        expected, exempt_userids = partition_expected_submitters(users, rules)

        self.assertEqual(
            ["test-user-004", "test-user-005"],
            [user["userid"] for user in expected],
        )
        self.assertEqual(
            {"test-user-001", "test-user-002", "test-user-003"}, exempt_userids
        )

    def test_exempt_reports_do_not_return_to_statistics(self) -> None:
        rules = parse_exemption_rules("NAME:示例员工甲;USERID:test-user-002")
        users = [
            {"userid": "test-user-001", "name": "示例员工甲"},
            {"userid": "test-user-002", "name": "示例员工乙"},
            {"userid": "test-user-003", "name": "示例员工丙"},
        ]
        _, exempt_userids = partition_expected_submitters(users, rules)
        reports = [
            {"creator_id": "test-user-001", "creator_name": ""},
            {"creator_id": "test-user-002", "creator_name": "示例员工乙"},
            {"creator_id": "test-user-003", "creator_name": "示例员工丙"},
            {"creator_id": "unknown-user", "creator_name": "示例员工甲"},
        ]

        filtered = filter_exempt_reports(reports, rules, exempt_userids)

        self.assertEqual(["test-user-003"], [report["creator_id"] for report in filtered])

    def test_empty_configuration_preserves_the_roster(self) -> None:
        rules = parse_exemption_rules("")
        users = [{"userid": "test-user-001", "name": "示例员工甲"}]

        expected, exempt_userids = partition_expected_submitters(users, rules)

        self.assertEqual(users, expected)
        self.assertEqual(set(), exempt_userids)

    def test_invalid_rule_rejects_without_echoing_the_identity(self) -> None:
        sensitive_identity = "示例员工甲"

        with self.assertRaises(ValueError) as raised:
            parse_exemption_rules(f"PHONE:{sensitive_identity}")

        self.assertIn("WEEKLY_REPORT_EXEMPT_SUBMITTERS", str(raised.exception))
        self.assertNotIn(sensitive_identity, str(raised.exception))


if __name__ == "__main__":
    unittest.main()
