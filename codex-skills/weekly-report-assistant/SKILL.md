---
name: weekly-report-assistant
description: Summarize employee weekly reports, evaluate work effectiveness against a company weekly-report template, check team-lead extra responsibilities, compare expected submitters with actual submissions, and produce missing-submission lists. Use when Codex is asked to analyze weekly reports, DingTalk work-log/report exports, team rosters, department contacts, leader compliance, or weekly-report submission statistics.
---

# Weekly Report Assistant

## Core Workflow

1. Identify the reporting period and data sources before evaluating content.
   - Prefer explicit user dates. The report period remains the previous completed ISO week (Monday 00:00 to Sunday 23:59:59, Asia/Shanghai), while submissions are attributed from Thursday 00:00 of that report week through Wednesday 23:59:59.999 of the following week. Monday-through-Wednesday late submissions therefore belong to the previous report week; Thursday starts the next report week's submission window.
   - Locate weekly-report files, DingTalk report JSON, contact/roster exports, and leader lists.
   - If `weekly_report_template.txt`, `team_leader_extra_duties.txt`, `金证优智工作周报模板.txt`, or `团队负责人额外职责.txt` exists in the workspace, read them and treat them as the source of truth.
   - Do not rewrite or reinterpret `weekly_report_template.txt` to fit evaluation needs. It must mirror the real DingTalk template; only change it if the user explicitly says the DingTalk template itself changed.

2. Build the expected submitter roster.
   - Prefer stable IDs (`userid`, employee ID) over names.
   - Use DingTalk contacts, exported rosters, or `人员名单/团队负责人/权限配置` files when present.
   - Do not claim who is missing if no expected roster exists; report that missing-submission statistics are unavailable and state what roster is needed.

3. Build the actual submission list.
   - For DingTalk JSON, group reports by `creator_id`/`userid` and keep template name, creation time, department, and raw content.
   - For local files, infer submitter from filename first, then document title/header/content.
   - Detect duplicates, ambiguous owners, empty reports, wrong-template reports, and reports outside the target period.

4. Compare roster vs submissions.
   - Match by stable ID when possible; fall back to exact normalized name only when IDs are unavailable.
   - Output counts: 应交人数、已交人数、未交人数、重复/异常提交数.
   - Keep an “无法确认” bucket for unmatched submissions or roster ambiguity.

5. Evaluate each submitted weekly report.
   - Summarize: 做了哪些工作, 关键交付物, 效果如何, 工时健康度, AI使用质量, 风险/求助, 下周计划.
   - Apply the latest weekly-report screening standard:
     - 谁在真干活（虚实盘）: read the actual template field `本周完成成果`/`本周成果`; it must contain concrete output/deliverable nouns such as 文档、代码、报告、方案、清单、页面、接口、脚本、合同、测试结果、上线内容. Do not treat action-only wording such as 推进、跟进、参与、沟通、处理、学习 as a valid output by itself.
     - 谁时间分配畸形（健康度）: read `工时投入分析`/`工时占比`, and combine it with the roster/title as `岗位角色`; the report should use percentages or clear proportions. Compare against the employee's role/title when available; if no role baseline exists, flag only obvious imbalance and state that the baseline is unavailable.
     - AI用得怎样（红黑榜）: read the actual template field `AI应用及效果`/`AI使用`; it must include tool/scenario + effect. Any item explicitly marked `【可复用】` should be promoted into the AI亮点/红榜 section.
     - 下周计划合不合格: read `下周计划（含交付时间）`/`下周计划`; it must include both date/deadline and planned output. If it contains `继续` without a concrete date and output, mark it as unqualified; the preferred rule is to avoid `继续` entirely.
     - 哪里需要老板拍板: read `风险与求助` only if the report/template provides that field, otherwise extract from risk/blocker/support wording in any section. Put found items near the top of the manager-facing report, but do not mark absence as template non-compliance unless the real DingTalk template contains that field.
   - Check completeness against the real DingTalk template fields first: 本周完成成果、工时投入分析、AI应用及效果、下周计划（含交付时间）. Treat 风险与求助 as a management extraction dimension unless it exists in the actual template.
   - Prefer factual, evidence-based wording; distinguish completed deliverables from “推进/跟进/参与”等过程描述.
   - Quantify when the report contains data; do not invent metrics.

6. Check team-lead extra responsibilities.
   - A team lead must submit a personal weekly report and a team summary attachment/section.
   - Check whether the team summary covers: 整体进度、个人工作评价、风险点、资源需求.
   - Check whether the lead appears to have reminded/promoted timely submission only when evidence exists; otherwise mark “未见证据”.
   - Evaluate lead compliance separately from the lead's personal work output.

7. Produce manager-facing outputs.
   - Start with a concise executive summary.
   - Put `需老板拍板/协调事项` near the top, before long per-person detail, when any report contains risk/blocker/support content that needs management coordination.
   - Include submission statistics and missing list before detailed per-person evaluation.
   - Replace the old per-person evaluation fields with the latest leadership dimensions. Do not create a separate `筛选标准结论` block. Each employee's evaluation should directly use: `虚实盘（本周成果）`, `时间分配健康度`, `AI使用红黑榜`, `下周计划合格性`, and `综合结论/需跟进`.
   - Use stable status labels inside these five dimensions so the web UI can color cells consistently:
     - Green labels: `完成较好`, `完成`, `合格`, `红榜`, `可复用`.
     - Yellow labels: `需改进`, `基本完成`, `待确认`, `需关注`.
     - Red labels: `不合格`, `未完成`, `无产出`, `未使用`, `黑榜`.
     - Use `无法判断` only when source data is missing or ambiguous.
   - Keep `需老板拍板/协调事项` as a separate top-level module, because leaders need to scan it quickly.
   - Include team-lead compliance table.
   - Include data-quality notes: missing roster, ambiguous names, API permission gaps, unreadable attachments.
   - Do not expose secrets, access tokens, or raw sensitive data unnecessarily.

## DingTalk Auto-Pull Workflow

When the workspace contains project scripts such as `scripts/run_weekly.py`, prefer the deterministic script before manual analysis:

```bash
python3 scripts/run_weekly.py
```

This project script defaults to `--week previous`. Monday runs are provisional because the late-submission window remains open through Wednesday; rerun on Thursday for the final submission status. Use `--week current` only for testing or when the user explicitly asks for the current incomplete week. `--start YYYY-MM-DD --end YYYY-MM-DD` describes the report period, and the script derives its Thursday-through-Wednesday submission window automatically.

After the script runs, analyze:

- `output/<YYYY-Www>/analysis/analysis_input.md` for report content and missing candidates.
- `output/<YYYY-Www>/exports/submission_status.csv` for the machine-readable submission table.
- `output/<YYYY-Www>/summary/submission_check.md` for the submission overview.

When producing the formal HR-facing evaluation, write it to `output/<YYYY-Www>/summary/manager_report.md`. The Java + Vue web interface reads that exact file and displays it as the AI evaluation page. Include the standard sections: 本周提交概览, 需老板拍板/协调事项, 未提交/异常提交名单, 员工五维评价, 团队负责人履职检查, 共性风险与下周关注点, 数据质量与需要人工确认事项.

Do not print `.env`, `DINGTALK_APP_SECRET`, or access tokens. If `scripts/run_weekly.py` fails because permissions are missing, report the exact missing DingTalk scope from the error message and stop before inventing missing-submission results.

## Output Standard

When producing a report, use this structure unless the user requests a different format:

1. 本周提交概览
2. 需老板拍板/协调事项
3. 未提交/异常提交名单
4. 员工五维评价（虚实盘、时间分配健康度、AI使用红黑榜、下周计划合格性、综合结论/需跟进）
5. 团队负责人履职检查
6. 共性风险与下周关注点
7. 数据质量与需要人工确认事项

For scoring or status labels, prefer:

- `完成较好`: clear deliverables, measurable effect, reasonable time allocation, AI use is concrete, and next plan has date + output.
- `基本完成`: has real work output but lacks some quantification, time allocation detail, AI effect, or next-plan precision.
- `需改进`: action-only progress wording, missing deliverables, missing/abnormal time allocation, missing AI tool/effect, next plan without date/output, plan containing vague `继续`, missing risk/support explanation, or weak evidence.
- `未提交`: expected submitter has no matching report.
- `无法判断`: source data is missing, ambiguous, or outside permission scope.
- `AI亮点`: AI section contains tool/scenario/effect and explicitly marks `【可复用】`; include in AI红榜.

## Useful Resource

- Read `references/output_format.md` when preparing a formal manager report, Excel columns, or a handoff summary.
- Run `scripts/prepare_weekly_report_inputs.py` when DingTalk JSON/contact files or many local text/markdown reports need to be normalized before analysis.

## Privacy And Safety

- Treat weekly reports, contacts, attachments, HR, finance, salary, performance, and contract content as confidential.
- Do not read HR/finance/salary/contract folders unless the user explicitly asks and access is appropriate.
- Do not print `AppSecret`, `access_token`, or full raw API responses in final messages.
- Generate separate outputs for different audiences if distribution is requested; do not include full-team sensitive details in team-lead-only reports by default.
