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

from dingtalk_common import load_env, parse_date, require_env, resolve_week_args, week_range  # noqa: E402


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

    def test_env_file_overrides_process_environment_without_exposing_values(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            env_path = Path(temp_dir) / ".env"
            env_path.write_text("DINGTALK_APP_KEY=file-value\n", encoding="utf-8")
            with patch.dict(os.environ, {"DINGTALK_APP_KEY": "process-value"}, clear=False):
                loaded = load_env(env_path)

        self.assertEqual(loaded["DINGTALK_APP_KEY"], "file-value")

    def test_required_config_rejects_empty_values(self) -> None:
        with self.assertRaisesRegex(Exception, "DINGTALK_APP_SECRET"):
            require_env({"DINGTALK_APP_SECRET": ""}, "DINGTALK_APP_SECRET")


if __name__ == "__main__":
    unittest.main()
