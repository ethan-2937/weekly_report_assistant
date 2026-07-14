# Python Collection Guide

## 职责

- `dingtalk_common.py`：环境加载、周次计算、HTTP 与通用文件操作。
- `run_weekly.py`：完整采集编排和分析输入包生成。
- `submission_roster.py`：解析免交规则并在统计前统一过滤应交人员和对应提交。
- `submission_reminder.py`：实时生成当前业务周截至检测时刻的未交 userid 快照；默认命令只输出汇总，`--json` 仅供受控 Spring 子进程使用。
- `run_codex_evaluation.py`：服务器自动评价入口；采集上一业务周、比较输入指纹、隔离运行非交互 Codex、机械验证并原子更新正式报告。
- `codex_evaluation_harness.py` / `codex_evaluation_workspace.py`：评价指纹、覆盖/隐私校验、状态锁和仅含授权输入的临时工作区。
- `report_content.py`：将钉钉报告内容规范化为分析输入文本。
- `leader_compliance.py`：从负责人身份、正文和附件元数据生成确定性履职证据，不负责猜测附件正文。
- `leader_subordinates.py`：优先读取负责人下属 userid；缺失时按共享钉钉部门生成待上级确认候选，并标记未交下属风险。
- `attachment_download.py`：使用钉钉 Drive 下载信息接口受控下载负责人附件，不记录远程凭据或短期 URL。
- `weekly_outputs.py`：统一生成提交 CSV、摘要和带负责人证据的分析输入包。
- `download_contacts.py`、`download_reports.py`：单独采集入口。
- `test_token.py`：人工凭据连通性检查，不属于默认测试。

## 规则

- 默认业务周期是上一完整 ISO 周；采集提交窗口统一由 `submission_window` 转换为该周周四至下一周周三，显式日期仍表示业务周期。
- 人员和报告使用稳定 `userid` 匹配，姓名只能作为明确标注的降级路径。
- 免交规则来自服务器私有 `WEEKLY_REPORT_EXEMPT_SUBMITTERS`；优先使用 `USERID:`，`NAME:` 和普通文本只做完整值精确匹配，不得模糊排除。
- API 响应、token、通讯录和原始周报不得写入日志或测试快照。
- 附件字段必须解析真实文件条目；空数组不是附件。只有元数据而没有正文时使用“附件待解析”，不得判定内容合格或不合格。
- 负责人附件只允许 HTTPS 和受控类型/大小，写入目标周忽略目录；下载失败降级为安全状态，不得打印 token、unionId、fileId、spaceId、远程 URL 或响应正文。
- 网络调用集中在适配边界，解析、匹配和输出构建保持可注入、可单测。
- 周日提醒使用当前业务周、本周周四 00:00 至检测时刻的窗口；它是暂定提醒，不得覆盖正式周报输出，也不得下载附件或生成 AI 输入。
- 自动评价只在输入指纹变化或正式报告无效时调用 Codex；Codex 子进程使用临时会话、净化环境、隔离工作区、无审批和非全权限沙箱，模型输出通过机械校验前不得覆盖上次有效报告。
- 新的默认测试使用临时目录和固定夹具，绝不读取 `config/.env`。
- 保持输出路径和 CSV/Markdown 字段向后兼容；变更时同步 Spring 读取端和 Skill。

## 验证

```powershell
python -m compileall -q scripts codex-skills/weekly-report-assistant/scripts
python -m unittest discover -s tests -p "test_*.py"
```
