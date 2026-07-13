from __future__ import annotations

import csv
import io
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

import download_reports as download_reports_module  # noqa: E402
import run_weekly  # noqa: E402
from dingtalk_common import parse_date  # noqa: E402


class ReportCollectionWindowTests(unittest.TestCase):
    def test_weekly_workflow_queries_thursday_through_following_wednesday(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            env = self._env(temp_dir)
            with (
                patch.object(sys, "argv", ["run_weekly.py", "--start", "2026-07-06", "--end", "2026-07-12"]),
                patch.object(run_weekly, "load_env", return_value=env),
                patch.object(run_weekly, "get_access_token", return_value="fictional-access-token"),
                patch.object(run_weekly, "download_contacts", return_value=([], [])),
                patch.object(run_weekly, "download_reports", return_value=[]) as download,
                redirect_stdout(io.StringIO()),
            ):
                exit_code = run_weekly.main()

            self.assertEqual(exit_code, 0)
            self.assertEqual(download.call_args.kwargs["start_ms"], self._milliseconds("2026-07-09"))
            self.assertEqual(
                download.call_args.kwargs["end_ms"],
                self._milliseconds("2026-07-16") - 1,
            )
            summary = (
                Path(temp_dir) / "2026-W28" / "summary" / "submission_check.md"
            ).read_text(encoding="utf-8")
            self.assertIn("周报周期：2026-W28（2026-07-06 至 2026-07-12）", summary)
            self.assertIn("提交归属窗口：2026-07-09 至 2026-07-15", summary)

    def test_download_only_workflow_uses_the_same_submission_window(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            env = self._env(temp_dir)
            with (
                patch.object(
                    sys,
                    "argv",
                    ["download_reports.py", "--start", "2026-07-06", "--end", "2026-07-12"],
                ),
                patch.object(download_reports_module, "load_env", return_value=env),
                patch.object(
                    download_reports_module,
                    "get_access_token",
                    return_value="fictional-access-token",
                ),
                patch.object(download_reports_module, "download_reports", return_value=[]) as download,
                redirect_stdout(io.StringIO()),
            ):
                exit_code = download_reports_module.main()

            self.assertEqual(exit_code, 0)
            self.assertEqual(download.call_args.kwargs["start_ms"], self._milliseconds("2026-07-09"))
            self.assertEqual(
                download.call_args.kwargs["end_ms"],
                self._milliseconds("2026-07-16") - 1,
            )

    def test_exempt_submitters_are_removed_from_all_statistical_outputs(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            env = self._env(temp_dir)
            env["WEEKLY_REPORT_EXEMPT_SUBMITTERS"] = "NAME:示例员工甲"
            users = [
                {"userid": "test-user-001", "name": "示例员工甲"},
                {"userid": "test-user-002", "name": "示例员工乙"},
            ]
            reports = [
                {
                    "creator_id": "test-user-001",
                    "creator_name": "示例员工甲",
                    "contents": "虚构免交周报正文",
                }
            ]
            with (
                patch.object(sys, "argv", ["run_weekly.py", "--start", "2026-07-06"]),
                patch.object(run_weekly, "load_env", return_value=env),
                patch.object(run_weekly, "get_access_token", return_value="fictional-access-token"),
                patch.object(run_weekly, "download_contacts", return_value=(users, [])),
                patch.object(run_weekly, "download_reports", return_value=reports),
                redirect_stdout(io.StringIO()),
            ):
                exit_code = run_weekly.main()

            self.assertEqual(0, exit_code)
            week_root = Path(temp_dir) / "2026-W28"
            with (week_root / "exports" / "submission_status.csv").open(
                encoding="utf-8-sig", newline=""
            ) as file:
                rows = list(csv.DictReader(file))
            self.assertEqual(1, len(rows))
            self.assertEqual("test-user-002", rows[0]["userid"])

            summary = (week_root / "summary" / "submission_check.md").read_text(encoding="utf-8")
            analysis = (week_root / "analysis" / "analysis_input.md").read_text(encoding="utf-8")
            self.assertNotIn("示例员工甲", summary)
            self.assertNotIn("示例员工甲", analysis)
            self.assertNotIn("虚构免交周报正文", analysis)
            self.assertIn("test-user-002", analysis)

    def _env(self, output_root: str) -> dict[str, str]:
        return {
            "OUTPUT_ROOT": output_root,
            "DINGTALK_REPORT_TEMPLATE": "虚构周报模板",
            "DINGTALK_ROOT_DEPT_ID": "1",
        }

    def _milliseconds(self, date: str) -> int:
        return int(parse_date(date).timestamp() * 1000)


if __name__ == "__main__":
    unittest.main()
