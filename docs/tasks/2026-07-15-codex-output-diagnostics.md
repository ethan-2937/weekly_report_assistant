# Task: 区分 Codex 输出阻塞与周次不匹配

## 问题

服务器数据采集已经完成，但 Harness 多次返回 `CODEX_OUTPUT_BLOCKED_OR_WRONG_WEEK`。当前校验把 Codex 返回 `status=blocked` 和返回错误 `week_label` 合并为同一个错误码，无法判断是输入被模型判定为不完整，还是模型忽略了目标周次。

## 范围

- 包含：为结构化 Codex 输出增加安全、确定性的错误分类；锁定提示词中的目标周次；补充 Python 回归测试；同步 Harness 文档。
- 不包含：前端未提交改动、周报正文、模型提示词评价口径、真实配置、重试上限和自动化调度。

## 验收标准

- [ ] `status=blocked` 返回 `CODEX_OUTPUT_BLOCKED`。
- [ ] `status=completed` 但周次不匹配返回 `CODEX_OUTPUT_WRONG_WEEK`。
- [ ] 其他状态返回安全的状态错误，不输出模型正文或警告原文。
- [ ] 提示词明确要求顶层 JSON 和 Markdown 使用同一个不可推导的目标周次。
- [ ] 原有成功 JSON、Schema 校验和敏感信息过滤行为保持不变。

## 约束

- 相关产品不变量：正式报告仍只接受目标业务周且必须由完整结构化结果生成。
- 相关架构边界：只修改 Python Harness、测试和 Harness 文档，不修改前端智能体正在进行的文件。
- 需要保留的无关工作区修改：保留其他智能体的前端文件、`config/.env.save` 和 `output.root-backup-*`，不读取、不提交、不删除。

## 验证

- 测试：`python -m unittest tests.test_codex_evaluation_harness`。
- 统一命令：`powershell -ExecutionPolicy Bypass -File scripts/verify.ps1`。
- 服务器验证：拉取后先运行 `--preflight`，再运行一次正式任务，确认错误码能区分阻塞和周次不匹配。

## 交付

- 决策：只记录安全错误码，不持久化模型警告原文、报告正文或员工标识。
- 剩余风险：如果返回 `CODEX_OUTPUT_BLOCKED`，仍需根据输入完整性或模型服务状态进一步处理；本任务不自动放宽阻塞条件。
- 延后工作：必要时增加安全的、白名单化的阻塞原因摘要。
