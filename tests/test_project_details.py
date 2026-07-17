from __future__ import annotations

import csv
import tempfile
import unittest
from pathlib import Path

from project_details import extract_project_detail, write_project_details
from report_content import report_text


class ProjectDetailsTest(unittest.TestCase):
    def test_extracts_new_template_fields_without_splitting_multiple_projects(self) -> None:
        report = self._report(
            [
                ("技术/产品/销售同学必填。您归属于哪条产品线", "大模型产品线"),
                ("技术/产品/销售同学必填。您本周服务的客户名称是？", "虚构客户甲、虚构客户乙"),
                ("技术/产品/销售同学必填。您本周服务的项目名称是？", "虚构项目甲、虚构项目乙"),
                ("技术/产品同学必填。本周投入工时合计(天)", "4.5"),
                ("技术/产品/销售同学必填。本周您产生的差旅费用是？", "120"),
                ("技术/产品/销售同学必填。本周您产生的招待费用是？", "0"),
            ]
        )

        detail = extract_project_detail(report)

        self.assertEqual("虚构项目甲、虚构项目乙", detail["项目名称"])
        self.assertEqual("4.5", detail["本周投入工时（天）"])
        self.assertEqual("0", detail["本周招待费用"])

    def test_writes_latest_report_with_hidden_permission_metadata(self) -> None:
        users = [
            {
                "userid": "test-user-001",
                "name": "测试员工甲",
                "dept_id_list": [11],
            }
        ]
        older = self._report([("您本周服务的项目名称是？", "旧虚构项目")], 100)
        newer = self._report(
            [
                ("您归属于哪条产品线", "虚构产品线"),
                ("您本周服务的客户名称是？", "虚构客户"),
                ("您本周服务的项目名称是？", "新虚构项目"),
                ("本周投入工时合计(天)", "3"),
            ],
            200,
        )

        with tempfile.TemporaryDirectory() as temp_dir:
            path = Path(temp_dir) / "project_details.csv"
            write_project_details(path, users, [older, newer], {11: "虚构研发部"})
            with path.open(encoding="utf-8-sig", newline="") as file:
                rows = list(csv.DictReader(file))

        self.assertEqual(1, len(rows))
        self.assertEqual("test-user-001", rows[0]["userid"])
        self.assertEqual("虚构研发部", rows[0]["部门"])
        self.assertEqual("新虚构项目", rows[0]["项目名称"])
        self.assertEqual("1", rows[0]["序号"])

    def test_omits_reports_without_project_detail_fields(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            path = Path(temp_dir) / "project_details.csv"
            write_project_details(
                path,
                [{"userid": "test-user-001", "name": "测试员工甲"}],
                [self._report([("本周完成成果", "完成虚构交付")])],
                {},
            )
            with path.open(encoding="utf-8-sig", newline="") as file:
                rows = list(csv.DictReader(file))

        self.assertEqual([], rows)

    def test_keeps_zero_numeric_values_and_hides_image_metadata(self) -> None:
        report = self._report([
            ("本周您产生的差旅费用是？", 0),
            (
                "图片",
                '[{"fileName":"虚构截图.png","downloadUrl":"https://example.invalid/private"}]',
            ),
        ])

        detail = extract_project_detail(report)
        flattened = report_text(report)

        self.assertEqual("0", detail["本周差旅费用"])
        self.assertIn("虚构截图.png", flattened)
        self.assertNotIn("downloadUrl", flattened)
        self.assertNotIn("example.invalid", flattened)

    def test_flattens_line_breaks_for_single_record_csv_compatibility(self) -> None:
        report = self._report([
            ("您本周服务的项目名称是？", "虚构项目甲\n虚构项目乙"),
        ])

        detail = extract_project_detail(report)

        self.assertEqual("虚构项目甲 虚构项目乙", detail["项目名称"])

    def _report(self, fields: list[tuple[str, object]], timestamp: int = 100) -> dict:
        return {
            "creator_id": "test-user-001",
            "creator_name": "测试员工甲",
            "create_time": timestamp,
            "contents": [{"key": key, "value": value} for key, value in fields],
        }


if __name__ == "__main__":
    unittest.main()
