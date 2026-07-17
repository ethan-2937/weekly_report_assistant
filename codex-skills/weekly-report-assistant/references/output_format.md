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

`weekly_report_template.txt` must mirror the real DingTalk online template. Do not edit it just because evaluation criteria changed. Separate DingTalk template compliance from manager screening criteria.

Each employee report should be checked against the actual DingTalk template fields:

- 本周完成成果: must contain concrete deliverable/output nouns, such as merged code, PRD, test report, signed contract, released document, resolved issue, dashboard, page, interface, script, list, or other verifiable output. Action-only wording such as `推进/跟进/参与/沟通/处理/学习` is not enough.
- 工时投入分析: should provide percentages or clear proportions by activity. Compare with the employee's title/role from the roster when possible; if no baseline exists, state that role baseline is missing rather than inventing one.
- AI应用及效果: should include tool/scenario + effect. If not used, state `未使用`. Items marked `【可复用】` are AI highlights and should be promoted to the AI红榜/亮点 section.
- 个人分享: optional. Reusable AI tools, models, scenarios, or methods marked `【可复用】` may supplement the AI红榜 evidence.
- 下周计划（含交付时间）: must contain both a date/deadline and a planned output. Plans containing vague `继续` wording are unqualified unless rewritten with concrete date + output; prefer treating any `继续` as a warning.
- 风险与求助: management extraction dimension. If the actual report/template has this field, evaluate it directly; otherwise extract blockers/support needs from any section and do not count absence as template non-compliance.

The new product-detail fields use a role-aware template-compliance denominator when the roster title is explicit:

- Other roles: four universal required fields.
- Sales: four universal fields plus product line, customer, project, travel expense, and hospitality expense (nine total).
- Technical/product: the sales set plus total workdays and workday distribution (eleven total).
- Numeric `0` is a valid filled expense value. Activate 9/11-field checks only when the report contains the new conditional field labels; historical templates and ambiguous titles keep the universal four-field denominator rather than being guessed or retroactively penalized.

Collection separately produces one public project-detail row per applicable submitted report with these columns, preserving multiple-value text without invented splitting:

- 序号
- 姓名（仅在服务层权限过滤后公开）
- 产品线
- 客户名称
- 项目名称
- 本周投入工时（天）
- 本周差旅费用
- 本周招待费用

Do not infer per-project workdays or expense allocation from comma-separated customer/project fields.

## Leadership Evaluation Dimensions

These dimensions replace the old per-person evaluation fields. Do not output them as a separate `筛选标准结论` section. The per-person evaluation table/narrative should directly use these dimensions. `哪里需要老板拍板` can be pulled out into a separate top-level module for leaders.

| 领导想看的维度 | 智能体需要哪个字段 | 合格标准 |
|---|---|---|
| 谁在真干活（虚实盘） | 本周完成成果/本周成果 | 必须有产出物名词，不能光写动作。 |
| 谁时间分配畸形（健康度） | 工时投入分析/工时占比 + 通讯录岗位角色 | 用百分比写清楚，智能体才能比对岗位基准线；没有基准线时只标明显异常。 |
| AI用得怎样（红黑榜） | AI应用及效果/AI使用 | 写清楚工具 + 效果，含 `【可复用】` 的自动入选亮点。 |
| 下周计划合不合格 | 下周计划（含交付时间）/下周计划 | 必须带日期和产出，不能只有 `继续` 类表述。 |
| 哪里需要老板拍板 | 风险/阻塞/求助信息（如有） | 简述卡点 + 需要什么支持，智能体自动置顶；若钉钉模板没有该字段，不因缺失判定模板不合格。 |

## Per-Person Evaluation Columns

Recommended columns for table or spreadsheet outputs:

- 姓名
- 部门/团队
- 是否团队负责人
- 提交状态
- 虚实盘（本周成果）
- 时间分配健康度
- AI使用红黑榜
- 下周计划合格性
- 综合结论
- 需跟进

Keep `需老板拍板/支持` out of the per-person table when possible; aggregate those items into the separate top-level `需老板拍板/协调事项` module.

## Web Color Label Contract

The web UI can infer colors from text, but the formal manager report should still use stable labels to reduce ambiguity:

- Green: `完成较好`, `完成`, `合格`, `红榜`, `可复用`.
- Yellow: `需改进`, `基本完成`, `待确认`, `需关注`.
- Red: `不合格`, `未完成`, `无产出`, `未使用`, `黑榜`.
- Neutral: `无法判断` when evidence is missing or ambiguous.

Preferred cell wording examples:

- `完成较好：有明确产出物，效果可验证。`
- `需改进：只有动作描述，产出物不够清晰。`
- `不合格：下周计划无日期和产出。`

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

Every row from `团队负责人履职输入（确定性证据）` must appear in the final compliance table. Preserve these distinctions:

- `未见证据`: no actual attachment entry and no matching body evidence; it may be reported as a missing duty item.
- `附件待解析` / `有附件待确认` / `疑似已提交（附件名匹配）`: attachment metadata exists but content is unavailable; use `无法判断（附件正文未解析）` for content dimensions and do not call it unsubmitted.
- `正文有相关证据`: evidence text exists, but the final evaluation must still confirm it describes the managed team rather than only personal work.
- `待 Codex 解析` with a local `attachments/team_leads/` path: open the file under the current week directory, inspect its content, and replace the placeholder with a supported conclusion; never follow a path outside that week.
- `不适用（个人周报未提交）`: personal report is missing, so team-summary dimensions cannot be inferred from that report.

### Subordinate mapping evidence

- Prefer a deterministic subordinate userid list when the input provides one; a private manual override resolved by the collection layer is authoritative over department inference.
- When no subordinate userid list exists, use shared DingTalk department membership as a candidate mapping and label it `按所属部门映射，待上级确认`; do not guess cross-department reporting relationships.
- Exempt people are excluded before counting missing subordinates.
- A mapped non-exempt subordinate who has not submitted makes the leader compliance conclusion `不合格（下属未提交）`.
- Keep the detailed mapping for internal confirmation input; do not expose userid values in the formal manager report.

## Missing Submission Rules

- Use stable IDs first: DingTalk `userid`, employee ID, or roster ID.
- Treat employees outside the configured roster/department scope as `非本次统计范围`.
- If a report exists but cannot be matched to a roster member, list it under `无法匹配提交` rather than counting it as a valid submission.
- If multiple reports by the same person exist in the period, count the person as submitted and list duplicates separately.
- If the DingTalk app permission scope excludes some departments, state that the missing list only covers the authorized scope.

## Executive Summary Template

```text
本周应提交 X 人，已提交 Y 人，未提交 Z 人；另有 A 条重复/异常提交、B 条无法匹配提交。
整体看，真实产出较好的方向包括……；时间分配异常集中在……；AI可复用亮点包括……；下周计划主要问题包括……。
需老板拍板/协调事项包括……。
团队负责人履职方面，X 人完整提交团队汇总，Y 人缺少成员评价/风险/资源需求等关键内容。
```

## Per-Person Narrative Template

```text
姓名：
- 虚实盘（本周成果）：
- 时间分配健康度：
- AI使用红黑榜：
- 下周计划合格性：
- 综合结论：
- 需跟进：
```

## Boss Decision Module Template

```text
## 需老板拍板/协调事项

| 优先级 | 人员/团队 | 卡点 | 需要支持 | 建议时限 |
|---|---|---|---|---|
```

## Web Display Contract

For the weekly-report web interface, save the final formal evaluation as `output/<YYYY-Www>/summary/manager_report.md`. Do not overwrite `analysis_input.md`; it is the raw analysis pack.

When the automated Harness requests private employee feedback, return one concise `praise` and one actionable `improvement` for each submitted stable `userid`. Keep these fields out of the manager Markdown and omit names, identifiers, colleagues, raw paragraphs, secrets, paths, and attachment metadata from their prose.
