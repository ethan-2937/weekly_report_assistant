# Output Format And Evaluation Rules

## Source Priority

1. Workspace policy files override this reference:
   - `weekly_report_template.txt`
   - `team_leader_extra_duties.txt`
   - `金证优智工作周报模板.txt`
   - `团队负责人额外职责.txt`
2. DingTalk contacts/report JSON override filename-based inference.
3. Manual roster or leader configuration overrides inferred organization structure.

## Weekly Report Template Checks

Each employee report should include:

- 本周成果: must contain concrete deliverable/output nouns, such as merged code, PRD, test report, signed contract, released document, resolved issue, dashboard, page, interface, script, list, or other verifiable output. Action-only wording such as `推进/跟进/参与/沟通/处理/学习` is not enough.
- 工时占比 + 岗位角色: should provide percentages or clear proportions by activity. Compare with the employee's title/role when possible; if no baseline exists, state that role baseline is missing rather than inventing one.
- AI使用: should include tool/scenario + effect. If not used, state `未使用`. Items marked `【可复用】` are AI highlights and should be promoted to the AI红榜/亮点 section.
- 下周计划: must contain both a date/deadline and a planned output. Plans containing vague `继续` wording are unqualified unless rewritten with concrete date + output; prefer treating any `继续` as a warning.
- 风险与求助: should briefly state the blocking point and the support needed from management or another team. These items should be placed near the top of the manager-facing report.

## Latest Screening Dimensions

| 领导想看的维度 | 智能体需要哪个字段 | 合格标准 |
|---|---|---|
| 谁在真干活（虚实盘） | 本周成果 | 必须有产出物名词，不能光写动作。 |
| 谁时间分配畸形（健康度） | 工时占比 + 岗位角色 | 用百分比写清楚，智能体才能比对岗位基准线；没有基准线时只标明显异常。 |
| AI用得怎样（红黑榜） | AI使用 | 写清楚工具 + 效果，含 `【可复用】` 的自动入选亮点。 |
| 下周计划合不合格 | 下周计划 | 必须带日期和产出，不能只有 `继续` 类表述。 |
| 哪里需要老板拍板 | 风险与求助 | 简述卡点 + 需要什么支持，智能体自动置顶。 |

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
- 真实产出判断
- 工时健康度
- AI应用情况
- AI红黑榜标记
- 下周计划质量
- 风险/阻塞
- 需老板拍板/支持
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
需老板拍板/协调事项包括……；AI可复用亮点包括……。
团队负责人履职方面，X 人完整提交团队汇总，Y 人缺少成员评价/风险/资源需求等关键内容。
```

## Per-Person Narrative Template

```text
姓名：
- 本周工作：
- 交付与效果：
- 周报完整性：
- 真实产出判断：
- 工时健康度：
- AI应用：
- AI红黑榜标记：
- 下周计划：
- 风险与求助：
- 评价结论：
```

## Web Display Contract

For the weekly-report web interface, save the final formal evaluation as `output/<YYYY-Www>/summary/manager_report.md`. Do not overwrite `analysis_input.md`; it is the raw analysis pack.
