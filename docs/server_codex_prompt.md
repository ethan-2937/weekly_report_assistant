# 服务器 Codex 交接提示词

```text
Use $weekly-report-assistant.
项目路径：/data2/person_path/yzzhang/weekly-report。
请先阅读 README.md、docs/codeup_server_deploy.md、docs/ubuntu_deploy.md，以及 skill 的 SKILL.md。
不要打印或泄露 config/.env、DINGTALK_APP_SECRET、access_token。
请运行 python3 scripts/run_weekly.py 拉取上一周钉钉周报和通讯录。
运行成功后，读取 output/<周次>/analysis/analysis_input.md，生成：
1. 本周提交概览；
2. 未提交/异常提交名单；
3. 每人工作总结与效果评价；
4. 团队负责人履职检查；
5. 共性风险与下周关注点；
6. 数据质量与需要人工确认事项。
请把最终正式评价保存为 output/<周次>/summary/manager_report.md，供 Java + Vue 前端展示。
```
