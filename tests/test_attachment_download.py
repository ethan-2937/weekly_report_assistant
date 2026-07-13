from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from attachment_download import (  # noqa: E402
    AttachmentDownloadError,
    MAX_FILE_BYTES,
    download_team_lead_attachments,
    extract_attachment_metadata,
)


class AttachmentDownloadTests(unittest.TestCase):
    def test_downloads_only_supported_team_lead_files_with_official_drive_request(self) -> None:
        users = [
            self._user("test-user-001", leader=True, unionid="fictional-union-001"),
            self._user("test-user-002", leader=False, unionid="fictional-union-002"),
        ]
        reports = [
            self._report("test-user-001", "测试团队汇总.docx"),
            self._report("test-user-002", "普通员工附件.docx"),
        ]
        info_requests = []
        file_requests = []

        def load_json(req):
            info_requests.append(req)
            return {
                "downloadInfo": {
                    "resourceUrl": "https://files.example.com/fictional-signed-resource",
                    "headers": {"x-fictional-download-header": "fictional-value"},
                }
            }

        def load_file(req, target, max_bytes):
            file_requests.append((req, target, max_bytes))
            target.write_bytes(b"fictional document bytes")
            return target.stat().st_size

        with tempfile.TemporaryDirectory() as temp_dir:
            week_out = Path(temp_dir) / "2026-W28"
            results = download_team_lead_attachments(
                "fictional-access-token",
                users,
                reports,
                week_out,
                json_loader=load_json,
                file_loader=load_file,
            )
            downloaded_path = week_out / results["test-user-001"][0].local_path
            self.assertTrue(downloaded_path.exists())

        self.assertEqual(1, len(info_requests))
        self.assertEqual(1, len(file_requests))
        self.assertIn("/v1.0/drive/spaces/fictional-space/files/fictional-file/downloadInfos", info_requests[0].full_url)
        self.assertIn("unionId=fictional-union-001", info_requests[0].full_url)
        headers = {key.lower(): value for key, value in info_requests[0].header_items()}
        self.assertEqual("fictional-access-token", headers["x-acs-dingtalk-access-token"])
        self.assertEqual("fictional-value", dict(file_requests[0][0].header_items())["X-fictional-download-header"])
        self.assertLessEqual(file_requests[0][2], MAX_FILE_BYTES)
        self.assertNotIn("test-user-001", results["test-user-001"][0].local_path)
        self.assertNotIn("fictional-file", results["test-user-001"][0].local_path)

    def test_empty_attachment_array_has_no_downloadable_files(self) -> None:
        result = extract_attachment_metadata(
            {"contents": [{"key": "附件", "value": "[]"}]}
        )

        self.assertEqual((), result.files)
        self.assertFalse(result.metadata_error)

    def test_missing_unionid_does_not_call_network(self) -> None:
        users = [self._user("test-user-001", leader=True, unionid="")]
        reports = [self._report("test-user-001", "测试团队汇总.docx")]

        def unexpected(_):
            self.fail("network boundary must not be called")

        with tempfile.TemporaryDirectory() as temp_dir:
            results = download_team_lead_attachments(
                "fictional-access-token",
                users,
                reports,
                Path(temp_dir),
                json_loader=unexpected,
            )

        self.assertIn("unionId", results["test-user-001"][0].status)
        self.assertFalse(results["test-user-001"][0].local_path)

    def test_disallowed_type_is_rejected_before_network(self) -> None:
        users = [self._user("test-user-001", leader=True, unionid="fictional-union-001")]
        reports = [self._report("test-user-001", "测试脚本.exe")]

        def unexpected(_):
            self.fail("disallowed attachment must not call network")

        with tempfile.TemporaryDirectory() as temp_dir:
            results = download_team_lead_attachments(
                "fictional-access-token",
                users,
                reports,
                Path(temp_dir),
                json_loader=unexpected,
            )

        self.assertEqual("不支持的附件类型", results["test-user-001"][0].status)

    def test_download_information_failure_is_non_fatal_and_sanitized(self) -> None:
        users = [self._user("test-user-001", leader=True, unionid="fictional-union-001")]
        reports = [self._report("test-user-001", "测试团队汇总.docx")]

        def fail_info(_):
            raise AttachmentDownloadError("fictional response containing sensitive identifiers")

        with tempfile.TemporaryDirectory() as temp_dir:
            results = download_team_lead_attachments(
                "fictional-access-token",
                users,
                reports,
                Path(temp_dir),
                json_loader=fail_info,
            )

        status = results["test-user-001"][0].status
        self.assertEqual("附件下载信息获取失败", status)
        self.assertNotIn("sensitive", status)

    def test_unsafe_resource_url_fails_without_exposing_it(self) -> None:
        users = [self._user("test-user-001", leader=True, unionid="fictional-union-001")]
        reports = [self._report("test-user-001", "测试团队汇总.pdf")]

        def load_json(_):
            return {"downloadInfo": {"resourceUrl": "http://127.0.0.1/private"}}

        def unexpected_file_loader(*_):
            self.fail("unsafe resource URL must not be downloaded")

        with tempfile.TemporaryDirectory() as temp_dir:
            results = download_team_lead_attachments(
                "fictional-access-token",
                users,
                reports,
                Path(temp_dir),
                json_loader=load_json,
                file_loader=unexpected_file_loader,
            )

        status = results["test-user-001"][0].status
        self.assertEqual("附件下载失败", status)
        self.assertNotIn("127.0.0.1", status)

    def test_declared_oversized_file_is_rejected_before_network(self) -> None:
        users = [self._user("test-user-001", leader=True, unionid="fictional-union-001")]
        reports = [self._report("test-user-001", "测试团队汇总.pdf", MAX_FILE_BYTES + 1)]

        def unexpected(_):
            self.fail("oversized attachment must not call network")

        with tempfile.TemporaryDirectory() as temp_dir:
            results = download_team_lead_attachments(
                "fictional-access-token",
                users,
                reports,
                Path(temp_dir),
                json_loader=unexpected,
            )

        self.assertEqual("附件超过大小限制", results["test-user-001"][0].status)

    def _user(self, userid: str, leader: bool, unionid: str) -> dict:
        return {
            "userid": userid,
            "name": "示例负责人甲" if leader else "示例员工乙",
            "leader": leader,
            "unionid": unionid,
        }

    def _report(self, userid: str, filename: str, size: int = 128) -> dict:
        metadata = [
            {
                "fileId": "fictional-file",
                "spaceId": "fictional-space",
                "fileName": filename,
                "fileSize": size,
                "fileType": Path(filename).suffix.lstrip("."),
            }
        ]
        return {
            "creator_id": userid,
            "contents": [{"key": "附件", "value": json.dumps(metadata, ensure_ascii=False)}],
        }


if __name__ == "__main__":
    unittest.main()
