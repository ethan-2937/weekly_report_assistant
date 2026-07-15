from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from leader_overrides import LeaderOverrideError, apply_leader_overrides  # noqa: E402


class LeaderOverrideTests(unittest.TestCase):
    def test_exact_name_rules_become_explicit_userid_relationships(self) -> None:
        users = [
            self._user("test-leader-001", "示例负责人甲", False),
            self._user("test-user-002", "示例员工乙", False),
            self._user("test-old-leader-003", "示例旧负责人丙", True),
        ]
        configured = json.dumps(
            {
                "NAME:示例负责人甲": {"leader": True, "subordinates": ["NAME:示例员工乙"]},
                "USERID:test-old-leader-003": {"leader": False},
            },
            ensure_ascii=False,
        )

        actual = apply_leader_overrides(users, configured)

        self.assertFalse(users[0]["leader"], "the source contacts are not mutated")
        self.assertTrue(actual[0]["leader"])
        self.assertEqual(["test-user-002"], actual[0]["subordinate_userids"])
        self.assertFalse(actual[2]["leader"])
        self.assertNotIn("subordinate_userids", actual[2])

    def test_unknown_or_ambiguous_names_fail_without_echoing_private_selector(self) -> None:
        users = [self._user("test-user-001", "示例同名员工", False), self._user("test-user-002", "示例同名员工", False)]
        configured = json.dumps({"NAME:示例同名员工": {"leader": False}}, ensure_ascii=False)
        unknown = json.dumps({"NAME:示例不存在员工": {"leader": False}}, ensure_ascii=False)

        with self.assertRaises(LeaderOverrideError) as raised:
            apply_leader_overrides(users, configured)

        self.assertEqual("LEADER_OVERRIDE_SELECTOR_AMBIGUOUS", str(raised.exception))
        self.assertNotIn("示例同名员工", str(raised.exception))
        with self.assertRaisesRegex(LeaderOverrideError, "LEADER_OVERRIDE_SELECTOR_UNRESOLVED"):
            apply_leader_overrides(users, unknown)

    def test_self_mapping_and_multiple_leaders_are_rejected(self) -> None:
        users = [
            self._user("test-leader-001", "示例负责人甲", True),
            self._user("test-leader-002", "示例负责人乙", True),
            self._user("test-user-003", "示例员工丙", False),
        ]
        self_mapping = json.dumps(
            {"USERID:test-leader-001": {"leader": True, "subordinates": ["USERID:test-leader-001"]}}
        )
        duplicate_mapping = json.dumps(
            {
                "USERID:test-leader-001": {"leader": True, "subordinates": ["USERID:test-user-003"]},
                "USERID:test-leader-002": {"leader": True, "subordinates": ["USERID:test-user-003"]},
            }
        )

        with self.assertRaisesRegex(LeaderOverrideError, "LEADER_OVERRIDES_SELF_MAPPING"):
            apply_leader_overrides(users, self_mapping)
        with self.assertRaisesRegex(LeaderOverrideError, "LEADER_OVERRIDES_MULTIPLE_LEADERS"):
            apply_leader_overrides(users, duplicate_mapping)

    def test_schema_requires_explicit_boolean_leader_state(self) -> None:
        users = [self._user("test-user-001", "示例员工甲", False)]

        with self.assertRaisesRegex(LeaderOverrideError, "LEADER_OVERRIDES_SCHEMA_INVALID"):
            apply_leader_overrides(users, '{"USERID:test-user-001": {"subordinates": []}}')

    def _user(self, userid: str, name: str, leader: bool) -> dict:
        return {"userid": userid, "name": name, "leader": leader, "dept_id_list": [101]}


if __name__ == "__main__":
    unittest.main()
