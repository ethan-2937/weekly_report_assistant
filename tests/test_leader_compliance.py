from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from leader_compliance import (  # noqa: E402
    ATTACHMENT_PENDING,
    NO_EVIDENCE,
    build_team_lead_evidence,
    extract_attachment_evidence,
    render_team_lead_input,
)
from attachment_download import DownloadedAttachment  # noqa: E402


class TeamLeadComplianceTests(unittest.TestCase):
    def test_real_attachment_entries_are_distinct_from_an_empty_attachment_field(self) -> None:
        with_file = self._report(
            contents=[
                {
                    "key": "附件",
                    "value": json.dumps(
                        [
                            {
                                "fileName": "测试团队汇总.docx",
                                "fileId": "fictional-file-id",
                                "spaceId": "fictional-space-id",
                            }
                        ],
                        ensure_ascii=False,
                    ),
                }
            ]
        )
        empty = self._report(contents=[{"key": "附件", "value": "[]"}])

        actual = extract_attachment_evidence(with_file)
        absent = extract_attachment_evidence(empty)

        self.assertEqual(("测试团队汇总.docx",), actual.file_names)
        self.assertEqual((), absent.file_names)
        self.assertFalse(absent.metadata_error)

    def test_attachment_without_body_content_stays_pending_instead_of_missing(self) -> None:
        users = [self._user("test-user-001", "示例负责人甲", leader=True)]
        reports = [
            self._report(
                userid="test-user-001",
                contents=[
                    {
                        "key": "附件",
                        "value": json.dumps([{"fileName": "测试团队汇总.docx"}], ensure_ascii=False),
                    }
                ],
            )
        ]

        evidence = build_team_lead_evidence(users, reports, {101: "测试研发部"})[0]

        self.assertEqual("疑似已提交（附件名匹配）", evidence.team_summary)
        self.assertEqual(ATTACHMENT_PENDING, evidence.overall_progress)
        self.assertEqual(ATTACHMENT_PENDING, evidence.member_evaluation)
        self.assertIn("测试团队汇总.docx", evidence.attachment)
        self.assertNotIn("fictional-file-id", evidence.attachment)

    def test_body_evidence_populates_every_leadership_dimension(self) -> None:
        users = [self._user("test-user-001", "示例负责人甲", leader=True)]
        body = "团队汇总：整体进度已说明；成员评价已覆盖；风险点已列出；资源需求已说明；已提醒提交周报。"
        reports = [self._report(userid="test-user-001", contents=[{"key": "本周完成成果", "value": body}])]

        evidence = build_team_lead_evidence(users, reports, {101: "测试研发部"})[0]

        self.assertEqual("正文有团队汇总证据", evidence.team_summary)
        self.assertEqual("正文有相关证据", evidence.overall_progress)
        self.assertEqual("正文有相关证据", evidence.member_evaluation)
        self.assertEqual("正文有相关证据", evidence.risk)
        self.assertEqual("正文有相关证据", evidence.resource_need)
        self.assertEqual("正文有相关证据", evidence.reminder)

    def test_missing_leader_is_listed_and_non_leader_is_omitted(self) -> None:
        users = [
            self._user("test-user-001", "示例负责人甲", leader=True),
            self._user("test-user-002", "示例员工乙", leader=False),
        ]

        evidence = build_team_lead_evidence(users, [], {101: "测试研发部"})

        self.assertEqual(1, len(evidence))
        self.assertEqual("未提交", evidence[0].personal_report)
        self.assertIn("个人周报未提交", evidence[0].team_summary)

    def test_invalid_attachment_metadata_is_safe_and_does_not_echo_raw_value(self) -> None:
        report = self._report(contents=[{"key": "附件", "value": "fictional-sensitive-metadata"}])

        attachments = extract_attachment_evidence(report)
        users = [self._user("test-user-001", "示例负责人甲", leader=True)]
        evidence = build_team_lead_evidence(users, [report], {101: "测试研发部"})
        rendered = "\n".join(render_team_lead_input(evidence))

        self.assertTrue(attachments.metadata_error)
        self.assertEqual((), attachments.file_names)
        self.assertIn("附件元数据异常", rendered)
        self.assertNotIn("fictional-sensitive-metadata", rendered)

    def test_empty_attachment_field_is_reported_as_no_evidence(self) -> None:
        users = [self._user("test-user-001", "示例负责人甲", leader=True)]
        reports = [self._report(contents=[{"key": "附件", "value": "[]"}])]

        evidence = build_team_lead_evidence(users, reports, {101: "测试研发部"})[0]

        self.assertEqual(NO_EVIDENCE, evidence.team_summary)
        self.assertEqual("无附件", evidence.attachment)
        self.assertEqual(NO_EVIDENCE, evidence.overall_progress)

    def test_department_fallback_lists_same_department_and_marks_missing_subordinate(self) -> None:
        users = [
            self._user("test-leader-001", "示例负责人甲", leader=True),
            self._user("test-user-002", "示例员工乙", leader=False),
            self._user("test-user-003", "示例员工丙", leader=False),
            self._user("test-user-004", "示例员工丁", leader=False),
        ]
        users[2]["dept_id_list"] = [101]
        users[3]["dept_id_list"] = [202]
        reports = [
            self._report("test-leader-001"),
            self._report("test-user-002"),
        ]

        evidence = build_team_lead_evidence(users[:3] + [users[3]], reports, {101: "测试研发部", 202: "测试市场部"})[0]

        self.assertEqual("DEPT", evidence.subordinate_source)
        self.assertEqual(("示例员工丙", "示例员工乙"), evidence.subordinate_names)
        self.assertEqual(("示例员工丙",), evidence.subordinate_missing_names)
        self.assertEqual("不合格（下属未提交）", evidence.subordinate_status)

    def test_explicit_subordinate_userids_override_department_fallback(self) -> None:
        leader = self._user("test-leader-001", "示例负责人甲", leader=True)
        leader["subordinate_userids"] = ["test-user-003"]
        users = [leader, self._user("test-user-002", "示例员工乙", leader=False), self._user("test-user-003", "示例员工丙", leader=False)]
        users[2]["dept_id_list"] = [202]

        evidence = build_team_lead_evidence(users, [self._report("test-leader-001"), self._report("test-user-003")], {101: "测试研发部", 202: "测试市场部"})[0]

        self.assertEqual("USERID", evidence.subordinate_source)
        self.assertEqual(("示例员工丙",), evidence.subordinate_names)
        self.assertEqual((), evidence.subordinate_missing_names)
        self.assertEqual("已按 userid 映射", evidence.subordinate_status)

    def test_downloaded_local_attachment_is_exposed_for_codex_without_remote_identifiers(self) -> None:
        users = [self._user("test-user-001", "示例负责人甲", leader=True)]
        reports = [
            self._report(
                contents=[
                    {
                        "key": "附件",
                        "value": json.dumps(
                            [{"fileName": "测试团队汇总.pdf", "fileId": "fictional-file-id"}],
                            ensure_ascii=False,
                        ),
                    }
                ]
            )
        ]
        downloads = {
            "test-user-001": (
                DownloadedAttachment(
                    "测试团队汇总.pdf",
                    "attachments/team_leads/fictional-hash/01_测试团队汇总.pdf",
                    "已下载",
                ),
            )
        }

        evidence = build_team_lead_evidence(users, reports, {101: "测试研发部"}, downloads)[0]
        rendered = "\n".join(render_team_lead_input([evidence]))

        self.assertEqual("待 Codex 解析", evidence.overall_progress)
        self.assertIn("attachments/team_leads/fictional-hash/01_测试团队汇总.pdf", rendered)
        self.assertNotIn("fictional-file-id", rendered)

    def _user(self, userid: str, name: str, leader: bool) -> dict:
        return {
            "userid": userid,
            "name": name,
            "leader": leader,
            "title": "测试负责人" if leader else "测试岗位",
            "dept_id_list": [101],
        }

    def _report(self, userid: str = "test-user-001", contents: list | None = None) -> dict:
        return {
            "creator_id": userid,
            "creator_name": "示例负责人甲",
            "contents": contents or [],
            "template_name": "虚构周报模板",
        }


if __name__ == "__main__":
    unittest.main()
