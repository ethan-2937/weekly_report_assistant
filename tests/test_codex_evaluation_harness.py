from __future__ import annotations

import csv
import json
import sys
import tempfile
import unittest
from datetime import datetime
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from codex_evaluation_harness import (  # noqa: E402
    EvaluationHarnessError,
    EvaluationLock,
    atomic_write_text,
    build_input_digest,
    load_roster_evidence,
    next_attempt,
    sanitized_codex_environment,
    should_run_codex,
    validate_manager_report,
)
from codex_employee_feedback import (  # noqa: E402
    validate_employee_feedback,
    validate_employee_feedback_artifact,
)
from codex_evaluation_workspace import isolated_evaluation_workspace  # noqa: E402
from dingtalk_common import CN_TZ  # noqa: E402
from run_codex_evaluation import (  # noqa: E402
    codex_command,
    collection_command,
    parse_codex_result,
    persisted_text_digest,
    render_prompt,
    resolve_week_label,
    scheduled_week_label,
)


class CodexEvaluationHarnessTests(unittest.TestCase):
    def test_scheduled_window_targets_one_week_from_sunday_evening_through_tuesday(self) -> None:
        self.assertIsNone(scheduled_week_label(datetime(2026, 7, 19, 18, 9, tzinfo=CN_TZ)))
        self.assertEqual("2026-W29", scheduled_week_label(datetime(2026, 7, 19, 18, 10, tzinfo=CN_TZ)))
        self.assertEqual("2026-W29", scheduled_week_label(datetime(2026, 7, 20, 0, 10, tzinfo=CN_TZ)))
        self.assertEqual("2026-W29", scheduled_week_label(datetime(2026, 7, 21, 22, 10, tzinfo=CN_TZ)))
        self.assertIsNone(scheduled_week_label(datetime(2026, 7, 22, 0, 0, tzinfo=CN_TZ)))

    def test_scheduled_window_handles_iso_year_boundary(self) -> None:
        self.assertEqual("2026-W53", scheduled_week_label(datetime(2027, 1, 3, 18, 10, tzinfo=CN_TZ)))
        self.assertEqual("2026-W53", scheduled_week_label(datetime(2027, 1, 4, 0, 10, tzinfo=CN_TZ)))

    def test_explicit_week_label_resolves_and_builds_fixed_collection_range(self) -> None:
        self.assertEqual("2026-W28", resolve_week_label("2026-W28"))
        command = collection_command("host", "2026-W28")
        self.assertEqual(["--start", "2026-07-06", "--end", "2026-07-12"], command[-4:])

        with self.assertRaisesRegex(EvaluationHarnessError, "WEEK_LABEL_INVALID"):
            resolve_week_label("2026-W99")

    def test_rendered_prompt_locks_target_week_in_json_and_markdown(self) -> None:
        prompt = render_prompt("2026-W28")

        self.assertNotIn("{{WEEK_LABEL}}", prompt)
        self.assertGreaterEqual(prompt.count("2026-W28"), 3)
        self.assertIn('top-level JSON `week_label` to exactly `2026-W28`', prompt)

    def test_input_digest_changes_for_new_reports_attachments_and_policy(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            project, week, prompt, schema = self._input_tree(Path(temp_dir))
            first, attachment_count = build_input_digest(project, week, prompt, schema)

            (week / "analysis" / "analysis_input.md").write_text("changed report", encoding="utf-8")
            second, _ = build_input_digest(project, week, prompt, schema)
            (week / "attachments" / "team_leads" / "fictional" / "summary.txt").write_text(
                "changed attachment",
                encoding="utf-8",
            )
            third, _ = build_input_digest(project, week, prompt, schema)

        self.assertEqual(1, attachment_count)
        self.assertNotEqual(first, second)
        self.assertNotEqual(second, third)

    def test_valid_report_covers_every_roster_member_and_leader(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            roster_path = Path(temp_dir) / "submission_status.csv"
            self._write_roster(roster_path)
            roster = load_roster_evidence(roster_path)

            errors = validate_manager_report(self._valid_report(), "2026-W29", roster)

        self.assertEqual((), errors)
        self.assertEqual(2, roster.expected_count)
        self.assertEqual(1, roster.submitted_count)
        self.assertEqual(1, roster.missing_count)
        self.assertEqual(("test-user-001",), roster.submitted_userids)

    def test_employee_feedback_covers_only_submitted_userids_and_rejects_private_data(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            roster_path = Path(temp_dir) / "submission_status.csv"
            self._write_roster(roster_path)
            roster = load_roster_evidence(roster_path)
            valid = [
                {
                    "userid": "test-user-001",
                    "praise": "本周形成了明确的虚构交付物，成果证据较完整。",
                    "improvement": "建议补充量化效果，并为下周计划写明日期和产出。",
                }
            ]

            self.assertEqual((), validate_employee_feedback(valid, roster))
            self.assertIn(
                "EMPLOYEE_FEEDBACK_COVERAGE_INVALID",
                validate_employee_feedback([], roster),
            )
            exposed = [dict(valid[0], praise="示例员工甲完成较好")]
            self.assertIn(
                "EMPLOYEE_FEEDBACK_NAME_EXPOSED",
                validate_employee_feedback(exposed, roster),
            )
            secret = [dict(valid[0], improvement="建议清理 access_token=fictional-sensitive-token")]
            self.assertIn(
                "EMPLOYEE_FEEDBACK_SECRET_EXPOSED",
                validate_employee_feedback(secret, roster),
            )
            link = [dict(valid[0], improvement="建议访问 https://example.invalid/internal 查看详情")]
            self.assertIn(
                "EMPLOYEE_FEEDBACK_FORBIDDEN_CONTENT",
                validate_employee_feedback(link, roster),
            )

    def test_employee_feedback_artifact_must_match_week_and_report_digest(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            roster_path = root / "submission_status.csv"
            artifact = root / "employee_feedback.json"
            self._write_roster(roster_path)
            roster = load_roster_evidence(roster_path)
            payload = {
                "version": 1,
                "weekLabel": "2026-W29",
                "inputDigest": "input-digest",
                "reportDigest": "report-digest",
                "feedback": [{
                    "userid": "test-user-001",
                    "praise": "形成了明确的虚构交付物。",
                    "improvement": "建议补充量化效果和明确日期。",
                }],
            }
            artifact.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")

            self.assertEqual(
                (),
                validate_employee_feedback_artifact(
                    artifact,
                    "2026-W29",
                    roster,
                    "input-digest",
                    "report-digest",
                ),
            )
            self.assertIn(
                "EMPLOYEE_FEEDBACK_ARTIFACT_MISMATCH",
                validate_employee_feedback_artifact(
                    artifact,
                    "2026-W28",
                    roster,
                    "input-digest",
                    "report-digest",
                ),
            )

    def test_validation_rejects_missing_people_and_stable_userids(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            roster_path = Path(temp_dir) / "submission_status.csv"
            self._write_roster(roster_path)
            roster = load_roster_evidence(roster_path)
            report = self._valid_report().replace("示例员工乙", "未列出的人员") + "\ntest-user-001\n"

            errors = validate_manager_report(report, "2026-W29", roster)

        self.assertIn("ROSTER_COVERAGE_INCOMPLETE", errors)
        self.assertIn("USERID_EXPOSED", errors)

    def test_validation_rejects_sensitive_shapes_with_safe_error_codes(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            roster_path = Path(temp_dir) / "submission_status.csv"
            self._write_roster(roster_path)
            roster = load_roster_evidence(roster_path)
            report = self._valid_report() + "\nBearer fictional-token-001234\n"

            errors = validate_manager_report(report, "2026-W29", roster)

        self.assertIn("SECRET_SHAPE_EXPOSED", errors)
        self.assertNotIn("fictional-token-001234", " ".join(errors))

    def test_validation_rejects_reordered_required_sections(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            roster_path = Path(temp_dir) / "submission_status.csv"
            self._write_roster(roster_path)
            roster = load_roster_evidence(roster_path)
            report = self._valid_report().replace(
                "## 需老板拍板/协调事项\n\n暂无需要拍板事项。\n\n## 未提交/异常提交名单",
                "## 未提交/异常提交名单\n\n- 示例员工乙：未提交。\n\n## 需老板拍板/协调事项\n\n暂无需要拍板事项。\n\n## 未提交/异常提交名单",
            )

            errors = validate_manager_report(report, "2026-W29", roster)

        self.assertIn("REQUIRED_SECTION_ORDER_INVALID", errors)

    def test_unchanged_success_skips_but_changed_or_failed_inputs_retry(self) -> None:
        success = {"status": "SUCCESS", "inputDigest": "digest-a", "attemptCount": 1}
        failed = {"status": "FAILED", "inputDigest": "digest-a", "attemptCount": 3}

        self.assertEqual((False, "UNCHANGED"), should_run_codex(success, "digest-a", (), 3))
        self.assertEqual(
            (True, "INPUT_CHANGED_OR_REPORT_INVALID"),
            should_run_codex(success, "digest-b", (), 3),
        )
        self.assertEqual((False, "RETRY_LIMIT"), should_run_codex(failed, "digest-a", ("REPORT_MISSING",), 3))
        self.assertEqual(4, next_attempt(failed, "digest-a"))
        self.assertEqual(1, next_attempt(failed, "digest-b"))

    def test_codex_environment_removes_business_secrets_but_keeps_codex_auth(self) -> None:
        environment = sanitized_codex_environment(
            {
                "PATH": "/usr/bin",
                "CODEX_API_KEY": "fictional-codex-api-key",
                "DINGTALK_APP_SECRET": "fictional-dingtalk-secret",
                "WEEKLY_JWT_SECRET": "fictional-jwt-secret",
                "MYSQL_ROOT_PASSWORD": "fictional-db-password",
                "WEEKLY_REPORT_EXEMPT_SUBMITTERS": "USERID:test-user-001",
                "WEEKLY_REPORT_LEADER_OVERRIDES": '{"USERID:test-user-002":{"leader":false}}',
                "WEEKLY_EVALUATION_FEEDBACK_HR_CONTACT": "示例HR联系人",
                "OPENAI_API_KEY": "fictional-openai-key",
                "GH_TOKEN": "fictional-github-token",
            }
        )

        self.assertEqual("fictional-codex-api-key", environment["CODEX_API_KEY"])
        self.assertEqual("/usr/bin", environment["PATH"])
        self.assertNotIn("DINGTALK_APP_SECRET", environment)
        self.assertNotIn("WEEKLY_JWT_SECRET", environment)
        self.assertNotIn("MYSQL_ROOT_PASSWORD", environment)
        self.assertNotIn("WEEKLY_REPORT_EXEMPT_SUBMITTERS", environment)
        self.assertNotIn("WEEKLY_REPORT_LEADER_OVERRIDES", environment)
        self.assertNotIn("WEEKLY_EVALUATION_FEEDBACK_HR_CONTACT", environment)
        self.assertNotIn("OPENAI_API_KEY", environment)
        self.assertNotIn("GH_TOKEN", environment)

    def test_isolated_workspace_contains_only_authorized_inputs(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            project, week, _, schema = self._input_tree(Path(temp_dir))
            (project / "config").mkdir()
            (project / "config" / ".env").write_text("SECRET=do-not-copy", encoding="utf-8")
            (project / "output" / "2026-W28").mkdir(parents=True)

            with isolated_evaluation_workspace(project, week, "safe prompt", schema) as workspace:
                self.assertTrue((workspace / "analysis" / "analysis_input.md").is_file())
                self.assertTrue((workspace / "attachments" / "team_leads" / "fictional" / "summary.txt").is_file())
                self.assertTrue((workspace / "policy" / "SKILL.md").is_file())
                self.assertFalse((workspace / "config" / ".env").exists())
                self.assertFalse((workspace / "output" / "2026-W28").exists())
                workspace_path = workspace

            self.assertFalse(workspace_path.exists())

    def test_codex_command_uses_ephemeral_isolated_workspace_and_no_approvals(self) -> None:
        workspace = Path("/tmp/fictional-weekly-workspace")
        command = codex_command(
            "/usr/local/bin/codex",
            "safe prompt",
            {"WEEKLY_CODEX_REASONING_EFFORT": "high"},
            workspace,
        )
        joined = " ".join(command)

        self.assertIn("--ephemeral", command)
        self.assertIn("--skip-git-repo-check", command)
        self.assertIn("--ignore-user-config", command)
        self.assertIn("--ignore-rules", command)
        self.assertIn("sandbox_workspace_write.network_access=false", command)
        self.assertIn("workspace-write", command)
        self.assertIn("never", command)
        self.assertIn(str(workspace), command)
        self.assertNotIn("danger-full-access", joined)

        proxied = codex_command(
            "/usr/local/bin/codex",
            "safe prompt",
            {"WEEKLY_CODEX_REASONING_EFFORT": "high", "WEEKLY_CODEX_BASE_URL": "https://proxy.example.invalid/openai"},
            workspace,
        )
        self.assertIn('model_provider="crs"', proxied)
        self.assertIn('model_providers.crs.base_url="https://proxy.example.invalid/openai"', proxied)
        self.assertIn('model_providers.crs.wire_api="responses"', proxied)

        legacy = codex_command(
            "/usr/local/bin/codex",
            "safe prompt",
            {"WEEKLY_CODEX_REASONING_EFFORT": "high"},
            workspace,
            "legacy",
        )
        self.assertIn("--full-auto", legacy)
        self.assertNotIn("--ask-for-approval", legacy)

        configured = codex_command(
            "/usr/local/bin/codex",
            "safe prompt",
            {"WEEKLY_CODEX_REASONING_EFFORT": "high"},
            workspace,
            "config",
        )
        self.assertIn('approval_policy="never"', configured)

    def test_structured_result_requires_completed_matching_week(self) -> None:
        report = self._valid_report()
        stdout = (
            '{"status":"completed","week_label":"2026-W29",'
            f'"manager_report_markdown":{json.dumps(report, ensure_ascii=False)},'
            '"employee_feedback":[{"userid":"test-user-001",'
            '"praise":"形成了明确的虚构交付物。",'
            '"improvement":"建议补充量化效果和明确日期。"}],"warnings":[]}'
        )

        parsed, feedback, warnings = parse_codex_result(stdout, "2026-W29")

        self.assertEqual(report, parsed)
        self.assertEqual("test-user-001", feedback[0]["userid"])
        self.assertEqual((), warnings)
        with self.assertRaisesRegex(EvaluationHarnessError, "CODEX_OUTPUT_WRONG_WEEK"):
            parse_codex_result(stdout.replace("2026-W29", "2026-W28", 1), "2026-W29")

        blocked = '{"status":"blocked","week_label":"2026-W29","manager_report_markdown":"","employee_feedback":[],"warnings":[]}'
        with self.assertRaisesRegex(EvaluationHarnessError, "CODEX_OUTPUT_BLOCKED"):
            parse_codex_result(blocked, "2026-W29")

        invalid_status = '{"status":"pending","week_label":"2026-W29","manager_report_markdown":"","employee_feedback":[],"warnings":[]}'
        with self.assertRaisesRegex(EvaluationHarnessError, "CODEX_OUTPUT_STATUS_INVALID"):
            parse_codex_result(invalid_status, "2026-W29")

    def test_atomic_write_preserves_complete_new_content_and_lock_rejects_overlap(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            report = root / "manager_report.md"
            atomic_write_text(report, "完整虚构评价")
            self.assertEqual("完整虚构评价\n", report.read_text(encoding="utf-8"))
            self.assertEqual(
                persisted_text_digest("完整虚构评价"),
                __import__("hashlib").sha256(report.read_bytes()).hexdigest(),
            )

            lock_path = root / "evaluation.lock"
            with EvaluationLock(lock_path):
                with self.assertRaisesRegex(EvaluationHarnessError, "RUN_ALREADY_ACTIVE"):
                    with EvaluationLock(lock_path):
                        pass
            self.assertFalse(lock_path.exists())

    def _input_tree(self, project: Path) -> tuple[Path, Path, Path, Path]:
        week = project / "output" / "2026-W29"
        (week / "analysis").mkdir(parents=True)
        (week / "exports").mkdir(parents=True)
        attachment = week / "attachments" / "team_leads" / "fictional" / "summary.txt"
        attachment.parent.mkdir(parents=True)
        attachment.write_text("fictional attachment evidence", encoding="utf-8")
        (week / "analysis" / "analysis_input.md").write_text("fictional analysis input", encoding="utf-8")
        self._write_roster(week / "exports" / "submission_status.csv")

        skill = project / "codex-skills" / "weekly-report-assistant"
        (skill / "references").mkdir(parents=True)
        (skill / "SKILL.md").write_text("fictional skill", encoding="utf-8")
        (skill / "references" / "output_format.md").write_text("fictional format", encoding="utf-8")
        (project / "weekly_report_template.txt").write_text("fictional template", encoding="utf-8")
        (project / "team_leader_extra_duties.txt").write_text("fictional duties", encoding="utf-8")
        prompt = project / "prompt.md"
        schema = project / "schema.json"
        prompt.write_text("fictional prompt", encoding="utf-8")
        schema.write_text("{}", encoding="utf-8")
        return project, week, prompt, schema

    def _write_roster(self, path: Path) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        fields = ["提交状态", "姓名", "userid", "部门", "是否负责人候选", "职务"]
        with path.open("w", encoding="utf-8-sig", newline="") as handle:
            writer = csv.DictWriter(handle, fieldnames=fields)
            writer.writeheader()
            writer.writerows(
                [
                    {
                        "提交状态": "已提交",
                        "姓名": "示例员工甲",
                        "userid": "test-user-001",
                        "部门": "测试研发部",
                        "是否负责人候选": "是",
                        "职务": "示例负责人",
                    },
                    {
                        "提交状态": "未提交",
                        "姓名": "示例员工乙",
                        "userid": "test-user-002",
                        "部门": "测试研发部",
                        "是否负责人候选": "否",
                        "职务": "示例工程师",
                    },
                ]
            )

    def _valid_report(self) -> str:
        detail = "基于虚构证据形成结论，不补造指标。" * 20
        return f"""# 2026-W29 周报管理评价

## 本周提交概览

应交 2 人，已交 1 人，未交 1 人。{detail}

## 需老板拍板/协调事项

暂无需要拍板事项。

## 未提交/异常提交名单

- 示例员工乙：未提交。

## 员工五维评价

### 示例员工甲

- 虚实盘（本周成果）：完成较好。
- 时间分配健康度：合格。
- AI使用红黑榜：红榜。
- 下周计划合格性：合格。
- 综合结论/需跟进：完成。

## 团队负责人履职检查

| 负责人 | 履职结论 |
|---|---|
| 示例员工甲 | 完成 |

## 共性风险与下周关注点

关注交付证据完整性。

## 数据质量与需要人工确认事项

当前使用虚构测试数据，无其他事项。
"""


if __name__ == "__main__":
    unittest.main()
