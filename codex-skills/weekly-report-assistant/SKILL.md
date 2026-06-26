---
name: weekly-report-assistant
description: Summarize employee weekly reports, evaluate work effectiveness against a company weekly-report template, check team-lead extra responsibilities, compare expected submitters with actual submissions, and produce missing-submission lists. Use when Codex is asked to analyze weekly reports, DingTalk work-log/report exports, team rosters, department contacts, leader compliance, or weekly-report submission statistics.
---

# Weekly Report Assistant

## Core Workflow

1. Identify the reporting period and data sources before evaluating content.
   - Prefer explicit user dates. If the user asks for the weekly Monday summary or does not specify dates, use the previous completed ISO week (Monday 00:00 to Sunday 23:59:59, Asia/Shanghai).
   - Locate weekly-report files, DingTalk report JSON, contact/roster exports, and leader lists.
   - If `金证优智工作周报模板.txt` or `团队负责人额外职责.txt` exists in the workspace, read them and treat them as the source of truth.

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
   - Summarize: 做了哪些工作, 关键交付物, 效果如何, 风险/阻塞, 下周计划.
   - Check completeness: 本周完成成果、工时投入分析、AI应用及效果、下周计划含交付时间.
   - Prefer factual, evidence-based wording; distinguish completed deliverables from “推进/跟进/参与”等过程描述.
   - Quantify when the report contains data; do not invent metrics.

6. Check team-lead extra responsibilities.
   - A team lead must submit a personal weekly report and a team summary attachment/section.
   - Check whether the team summary covers: 整体进度、个人工作评价、风险点、资源需求.
   - Check whether the lead appears to have reminded/promoted timely submission only when evidence exists; otherwise mark “未见证据”.
   - Evaluate lead compliance separately from the lead's personal work output.

7. Produce manager-facing outputs.
   - Start with a concise executive summary.
   - Include submission statistics and missing list before detailed per-person evaluation.
   - Include team-lead compliance table.
   - Include data-quality notes: missing roster, ambiguous names, API permission gaps, unreadable attachments.
   - Do not expose secrets, access tokens, or raw sensitive data unnecessarily.

## DingTalk Auto-Pull Workflow

When the workspace contains project scripts such as `scripts/run_weekly.py`, prefer the deterministic script before manual analysis:

```bash
python3 scripts/run_weekly.py
```

This project script defaults to `--week previous`, because the company summarizes weekly reports every Monday and analyzes the previous week. Use `--week current` only for testing or when the user explicitly asks for the current incomplete week. Use `--start YYYY-MM-DD --end YYYY-MM-DD` for an explicit custom period.

After the script runs, analyze:

- `output/<YYYY-Www>/analysis/analysis_input.md` for report content and missing candidates.
- `output/<YYYY-Www>/exports/submission_status.csv` for the machine-readable submission table.
- `output/<YYYY-Www>/summary/submission_check.md` for the submission overview.

When producing the formal HR-facing evaluation, write it to `output/<YYYY-Www>/summary/manager_report.md`. The Java + Vue web interface reads that exact file and displays it as the AI evaluation page. Include the standard sections: ??????, ???/??????, ???????????, ?????????, ??????????, ?????????????.

Do not print `.env`, `DINGTALK_APP_SECRET`, or access tokens. If `scripts/run_weekly.py` fails because permissions are missing, report the exact missing DingTalk scope from the error message and stop before inventing missing-submission results.

## Output Standard

When producing a report, use this structure unless the user requests a different format:

1. 本周提交概览
2. 未提交/异常提交名单
3. 每人工作总结与效果评价
4. 团队负责人履职检查
5. 共性风险与下周关注点
6. 数据质量与需要人工确认事项

For scoring or status labels, prefer:

- `完成较好`: clear deliverables, measurable effect, next plan specific.
- `基本完成`: has real work output but lacks some quantification or deadlines.
- `需改进`: vague progress wording, missing deliverables, missing AI/time/next-plan sections, or weak evidence.
- `未提交`: expected submitter has no matching report.
- `无法判断`: source data is missing, ambiguous, or outside permission scope.

## Useful Resource

- Read `references/output_format.md` when preparing a formal manager report, Excel columns, or a handoff summary.
- Run `scripts/prepare_weekly_report_inputs.py` when DingTalk JSON/contact files or many local text/markdown reports need to be normalized before analysis.

## Privacy And Safety

- Treat weekly reports, contacts, attachments, HR, finance, salary, performance, and contract content as confidential.
- Do not read HR/finance/salary/contract folders unless the user explicitly asks and access is appropriate.
- Do not print `AppSecret`, `access_token`, or full raw API responses in final messages.
- Generate separate outputs for different audiences if distribution is requested; do not include full-team sensitive details in team-lead-only reports by default.
