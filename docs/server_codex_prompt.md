# 服务器 Codex 交接提示词

```text
Use $weekly-report-assistant.
项目路径：/data2/person_path/yzzhang/weekly-report。
请先阅读 README.md、docs/codeup_server_deploy.md、docs/ubuntu_deploy.md，以及 skill 的 SKILL.md。
不要打印或泄露 config/.env、DINGTALK_APP_SECRET、access_token。
请运行 python3 scripts/run_weekly.py 拉取上一业务周钉钉周报和通讯录。业务周按周一至周日命名，脚本会自动使用周四至下一周周三的提交归属窗口；周四截止后运行得到最终提交状态。
运行成功后，读取 output/<周次>/analysis/analysis_input.md；先使用其中“团队负责人履职输入（确定性证据）”建立完整负责人清单。附件待解析/有附件待确认不得判为未提交或不合格，内容维度写“无法判断（附件正文未解析）”。然后生成：
1. 本周提交概览；
2. 需老板拍板/协调事项，并置顶展示；
3. 未提交/异常提交名单；
4. 员工五维评价：直接用“虚实盘（本周成果）/时间分配健康度/AI使用红黑榜/下周计划合格性/综合结论”替换旧评价字段，不要单独新增“筛选标准结论”板块；
5. 团队负责人履职检查；
6. 共性风险与下周关注点；
7. 数据质量与需要人工确认事项。
请把最终正式评价保存为 output/<周次>/summary/manager_report.md，供 Java + Vue 前端展示。
```
