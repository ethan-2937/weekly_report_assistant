# Python Collection Guide

## 职责

- `dingtalk_common.py`：环境加载、周次计算、HTTP 与通用文件操作。
- `run_weekly.py`：完整采集编排和分析输入包生成。
- `download_contacts.py`、`download_reports.py`：单独采集入口。
- `test_token.py`：人工凭据连通性检查，不属于默认测试。

## 规则

- 默认业务周期是上一完整 ISO 周；采集提交窗口统一由 `submission_window` 转换为该周周四至下一周周三，显式日期仍表示业务周期。
- 人员和报告使用稳定 `userid` 匹配，姓名只能作为明确标注的降级路径。
- API 响应、token、通讯录和原始周报不得写入日志或测试快照。
- 网络调用集中在适配边界，解析、匹配和输出构建保持可注入、可单测。
- 新的默认测试使用临时目录和固定夹具，绝不读取 `config/.env`。
- 保持输出路径和 CSV/Markdown 字段向后兼容；变更时同步 Spring 读取端和 Skill。

## 验证

```powershell
python -m compileall -q scripts codex-skills/weekly-report-assistant/scripts
python -m unittest discover -s tests -p "test_*.py"
```
