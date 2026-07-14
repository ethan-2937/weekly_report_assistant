from __future__ import annotations

import io
import sys
import unittest
from contextlib import redirect_stdout
from datetime import datetime
from pathlib import Path
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from dingtalk_common import CN_TZ  # noqa: E402
from submission_reminder import ReminderSnapshot, build_snapshot, collect_snapshot, main  # noqa: E402


class SubmissionReminderTests(unittest.TestCase):
    def test_current_week_snapshot_uses_stable_userids_and_exemptions(self) -> None:
        now = datetime(2026, 7, 19, 18, 0, tzinfo=CN_TZ)
        users = [
            {"userid": "test-user-001", "name": "示例员工甲"},
            {"userid": "test-user-002", "name": "示例员工乙"},
            {"userid": "test-user-003", "name": "示例员工丙"},
            {"userid": "test-user-004", "name": "示例免交人员"},
        ]
        reports = [
            {"creator_id": "test-user-001", "creator_name": "示例员工甲"},
            {"creator_id": "test-user-001", "creator_name": "示例员工甲"},
            {"creator_id": "test-user-004", "creator_name": "示例免交人员"},
        ]

        snapshot = build_snapshot(now, users, reports, "USERID:test-user-004")

        self.assertEqual("2026-W29", snapshot.weekLabel)
        self.assertEqual(3, snapshot.expectedCount)
        self.assertEqual(1, snapshot.submittedCount)
        self.assertEqual(["test-user-002", "test-user-003"], snapshot.missingUserIds)
        self.assertEqual(0, snapshot.unresolvedCount)

    def test_collection_caps_report_query_at_the_detection_time(self) -> None:
        now = datetime(2026, 7, 19, 18, 0, tzinfo=CN_TZ)
        captured: dict[str, object] = {}

        def reports_loader(**kwargs):
            captured.update(kwargs)
            return []

        snapshot = collect_snapshot(
            now=now,
            env={
                "DINGTALK_REPORT_TEMPLATE": "虚构周报模板",
                "DINGTALK_ROOT_DEPT_ID": "1",
            },
            token_loader=lambda env: "fictional-access-token",
            contacts_loader=lambda token, dept_id: (
                [{"userid": "test-user-001", "name": "示例员工甲"}],
                [],
            ),
            reports_loader=reports_loader,
        )

        self.assertEqual(int(now.timestamp() * 1000), captured["end_ms"])
        self.assertEqual("虚构周报模板", captured["template_name"])
        self.assertEqual(["test-user-001"], snapshot.missingUserIds)

    def test_collection_rejects_missing_template_before_network_access(self) -> None:
        network_called = False

        def token_loader(_env):
            nonlocal network_called
            network_called = True
            return "fictional-access-token"

        with self.assertRaisesRegex(Exception, "DINGTALK_REPORT_TEMPLATE"):
            collect_snapshot(
                now=datetime(2026, 7, 19, 18, 0, tzinfo=CN_TZ),
                env={},
                token_loader=token_loader,
            )

        self.assertFalse(network_called)

    def test_reminder_window_rejects_current_week_before_thursday(self) -> None:
        with self.assertRaisesRegex(ValueError, "has not opened"):
            build_snapshot(
                datetime(2026, 7, 15, 18, 0, tzinfo=CN_TZ),
                [{"userid": "test-user-001", "name": "示例员工甲"}],
                [],
                None,
            )

    def test_default_cli_preflight_prints_counts_without_userids(self) -> None:
        snapshot = ReminderSnapshot(
            weekLabel="2026-W29",
            cutoff="2026-07-19T18:00:00+08:00",
            expectedCount=2,
            submittedCount=1,
            missingUserIds=["test-user-sensitive"],
            unresolvedCount=0,
        )
        output = io.StringIO()

        with (
            patch("submission_reminder.collect_snapshot", return_value=snapshot),
            patch.object(sys, "argv", ["submission_reminder.py"]),
            redirect_stdout(output),
        ):
            exit_code = main()

        self.assertEqual(0, exit_code)
        self.assertIn("missing=1", output.getvalue())
        self.assertNotIn("test-user-sensitive", output.getvalue())


if __name__ == "__main__":
    unittest.main()
