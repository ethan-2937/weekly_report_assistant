from __future__ import annotations

import argparse
import os
import sys
import tempfile
import unittest
from datetime import timedelta
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from dingtalk_common import (  # noqa: E402
    load_env,
    parse_date,
    require_env,
    resolve_week_args,
    submission_window,
    week_range,
)


class DingTalkCommonTests(unittest.TestCase):
    def test_previous_week_is_one_complete_monday_to_sunday_range(self) -> None:
        current_start, _ = week_range("current")
        previous_start, previous_end = week_range("previous")

        self.assertEqual(previous_start.weekday(), 0)
        self.assertEqual(previous_start, current_start - timedelta(days=7))
        self.assertEqual(previous_end - previous_start, timedelta(days=7) - timedelta(milliseconds=1))

    def test_explicit_dates_override_week_selector(self) -> None:
        args = argparse.Namespace(week="current", start="2026-07-06", end="2026-07-12")

        start, end, label = resolve_week_args(args)

        self.assertEqual(start, parse_date("2026-07-06"))
        self.assertEqual(end.date().isoformat(), "2026-07-12")
        self.assertEqual(label, "2026-W28")

    def test_report_week_uses_thursday_through_wednesday_submission_window(self) -> None:
        period_start = parse_date("2026-07-06")
        period_end = parse_date("2026-07-13") - timedelta(milliseconds=1)

        submit_start, submit_end = submission_window(period_start, period_end)

        self.assertEqual(submit_start, parse_date("2026-07-09"))
        self.assertEqual(submit_end, parse_date("2026-07-16") - timedelta(milliseconds=1))

    def test_adjacent_submission_windows_have_no_gap_or_overlap(self) -> None:
        first_start = parse_date("2026-07-06")
        first_end = parse_date("2026-07-13") - timedelta(milliseconds=1)
        second_start = first_start + timedelta(days=7)
        second_end = first_end + timedelta(days=7)

        _, first_submit_end = submission_window(first_start, first_end)
        second_submit_start, _ = submission_window(second_start, second_end)

        self.assertEqual(first_submit_end + timedelta(milliseconds=1), second_submit_start)

    def test_process_environment_overrides_env_file_without_exposing_values(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            env_path = Path(temp_dir) / ".env"
            env_path.write_text("DINGTALK_APP_KEY=file-value\n", encoding="utf-8")
            with patch.dict(os.environ, {"DINGTALK_APP_KEY": "process-value"}, clear=False):
                loaded = load_env(env_path)

        self.assertEqual(loaded["DINGTALK_APP_KEY"], "process-value")

    def test_empty_env_file_value_does_not_clear_process_exemptions(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            env_path = Path(temp_dir) / ".env"
            env_path.write_text("WEEKLY_REPORT_EXEMPT_SUBMITTERS=\n", encoding="utf-8")
            with patch.dict(
                os.environ,
                {"WEEKLY_REPORT_EXEMPT_SUBMITTERS": "USERID:test-user-001"},
                clear=False,
            ):
                loaded = load_env(env_path)

        self.assertEqual(loaded["WEEKLY_REPORT_EXEMPT_SUBMITTERS"], "USERID:test-user-001")

    def test_required_config_rejects_empty_values(self) -> None:
        with self.assertRaisesRegex(Exception, "DINGTALK_APP_SECRET"):
            require_env({"DINGTALK_APP_SECRET": ""}, "DINGTALK_APP_SECRET")


if __name__ == "__main__":
    unittest.main()
