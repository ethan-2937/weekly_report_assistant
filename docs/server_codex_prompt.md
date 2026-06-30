# 服务器 Codex 交接提示词

```text
Use $weekly-report-assistant.
项目路径：/data2/person_path/yzzhang/weekly-report。
请先阅读 README.md、docs/codeup_server_deploy.md、docs/ubuntu_deploy.md，以及 skill 的 SKILL.md。
不要打印或泄露 config/.env、DINGTALK_APP_SECRET、access_token。
请运行 python3 scripts/run_weekly.py 拉取上一周钉钉周报和通讯录。
运行成功后，读取 output/<周次>/analysis/analysis_input.md，生成：
1. 本周提交概览；
2. 需老板拍板/协调事项，并置顶展示；
3. 未提交/异常提交名单；
4. 按最新筛选标准输出结论：真实产出、工时健康度、AI红黑榜、下周计划合格性、风险求助；
5. 每人工作总结与效果评价；
6. 团队负责人履职检查；
7. 共性风险与下周关注点；
8. 数据质量与需要人工确认事项。
请把最终正式评价保存为 output/<周次>/summary/manager_report.md，供 Java + Vue 前端展示。
```
