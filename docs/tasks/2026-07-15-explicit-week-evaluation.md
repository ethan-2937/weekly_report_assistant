# Task: 支持指定历史周次重新采集和评价

## 问题

自动 Harness 默认处理上一业务周。日期进入下一周后，`previous` 会从 `2026-W28` 滚动到 `2026-W29`，无法直接重新生成已经结束的 W28。

## 范围

- 包含：为 `run_codex_evaluation.sh` 增加 `--week-label YYYY-Www`；让 Docker/Host 采集和 Codex 评价使用同一指定周次；补充安全单元测试和部署文档。
- 不包含：修改 cron 默认滚动行为、删除历史 output、修改前端或负责人规则。

## 验收标准

- [ ] `--week-label 2026-W28 --dry-run` 只刷新 W28，不读取当前上一周 W29。
- [ ] 正式执行指定周次时，采集、输入目录、提示词和正式报告全部使用同一个周次。
- [ ] 不传参数时，cron 仍按上一业务周运行。
- [ ] 非法周次安全拒绝，不访问外部服务。

## 约束

- 相关产品不变量：周次为完整 ISO 周，提交窗口仍按周四至下一周周三归属。
- 相关架构边界：只修改 Python Harness、测试和部署说明，不触碰其他智能体的前后端未提交文件。
- 需要保留的无关工作区修改：保留当前前后端智能体改动，不读取或提交真实 `.env`、`output/`、`logs/`。

## 验证

- 测试：`python -m unittest tests.test_codex_evaluation_harness`。
- 手工：服务器先运行 `./scripts/run_codex_evaluation.sh --week-label 2026-W28 --dry-run`，再运行同一周次的正式命令。
- 统一命令：`powershell -ExecutionPolicy Bypass -File scripts/verify.ps1`。

## 交付

- 决策：显式历史周次只影响手动命令；cron 不传参数时保持自动滚动。
- 剩余风险：指定周次会重新采集并可能消耗 Codex token，执行前应确认目标周次和配置。
- 延后工作：如需 Web 按钮选择历史周次，另建任务，不在本次增加接口。
