# Output Format And Evaluation Rules

## Source Priority

1. Workspace policy files override this reference:
   - `金证优智工作周报模板.txt`
   - `团队负责人额外职责.txt`
2. DingTalk contacts/report JSON override filename-based inference.
3. Manual roster or leader configuration overrides inferred organization structure.

## Weekly Report Template Checks

Each employee report should include:

- 本周完成成果: concrete deliverables, merged code, PRD, test report, signed contract, released document, resolved issue, or other verifiable output.
- 工时投入分析: rough proportion by activity; exact precision is not required.
- AI应用及效果: tool, scenario, and effect; if not used, state `未使用`.
- 下周计划（含交付时间）: planned output and deadline; avoid vague `继续推进` wording.

## Per-Person Evaluation Columns

Recommended columns for table or spreadsheet outputs:

- 姓名
- 部门/团队
- 是否团队负责人
- 提交状态
- 本周主要工作
- 关键交付物
- 效果评价
- 周报完整性
- AI应用情况
- 下周计划质量
- 风险/阻塞
- 评价结论
- 需要跟进

## Team Lead Compliance Columns

- 负责人
- 管理团队/部门
- 个人周报是否提交
- 团队汇总是否提交
- 整体进度是否说明
- 成员评价是否覆盖
- 风险点是否说明
- 资源需求是否说明
- 催交通报/提醒证据
- 履职结论
- 缺失项

## Missing Submission Rules

- Use stable IDs first: DingTalk `userid`, employee ID, or roster ID.
- Treat employees outside the configured roster/department scope as `非本次统计范围`.
- If a report exists but cannot be matched to a roster member, list it under `无法匹配提交` rather than counting it as a valid submission.
- If multiple reports by the same person exist in the period, count the person as submitted and list duplicates separately.
- If the DingTalk app permission scope excludes some departments, state that the missing list only covers the authorized scope.

## Executive Summary Template

```text
本周应提交 X 人，已提交 Y 人，未提交 Z 人；另有 A 条重复/异常提交、B 条无法匹配提交。
整体看，主要工作集中在……；交付效果较好的方向包括……；主要风险包括……。
团队负责人履职方面，X 人完整提交团队汇总，Y 人缺少成员评价/风险/资源需求等关键内容。
```

## Per-Person Narrative Template

```text
姓名：
- 本周工作：
- 交付与效果：
- 周报完整性：
- AI应用：
- 下周计划：
- 评价结论：
```

## Web Display Contract

For the weekly-report web interface, save the final formal evaluation as `output/<YYYY-Www>/summary/manager_report.md`. Do not overwrite `analysis_input.md`; it is the raw analysis pack.
