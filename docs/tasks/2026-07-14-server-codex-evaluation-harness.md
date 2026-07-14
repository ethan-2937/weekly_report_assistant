# Task: 服务器 Codex 自动补齐并验证上一业务周评价

## 问题

服务器当前依赖人工输入 `docs/server_codex_prompt.md` 才会运行 `$weekly-report-assistant` 并生成 `manager_report.md`。周一生成暂定评价后，周一至周三补交会改变上一业务周的输入，但没有确定性的变更检测、自动重跑、输出结构校验或失败状态，因此可能漏掉补交人员评价、保留半成品报告或重复消耗 Codex。

主要风险是：把周报正文中的指令当成系统指令、把钉钉/数据库凭据继承给 Codex 子进程、没有新数据仍重复运行、Codex 失败后覆盖上次有效报告、遗漏员工或负责人、把 userid/token 写入正式报告，以及 cron 并发启动多个评价进程。

## 范围

- 包含：上一业务周确定性采集；分析输入、附件、Skill、提示词的内容指纹；仅变化时运行非交互 `codex exec`；项目只读边界、临时 `workspace-write` 沙箱、无审批、临时会话和净化环境；结构化输出；员工/负责人覆盖与敏感字段校验；原子替换正式报告；安全状态文件、重试上限和跨平台进程锁；Ubuntu cron 部署说明。
- 不包含：真实服务器登录或凭据迁移；真实钉钉采集；修改评价口径；前端改造；自动提交 Git；把原始周报、通讯录或评价状态上传到仓库；Codex CLI 内建 Scheduled 界面。

## 验收标准

- [x] 周一至周三定期采集上一业务周，输入无变化且现有报告有效时不调用 Codex。
- [x] 新补交、报告修改、负责人附件变化或 Skill/提示词变化时重新生成完整评价，并覆盖全部当前应交记录和负责人。
- [x] Codex 只读项目、使用 `approval=never`、临时会话和结构化输出；DingTalk/MySQL/JWT/免交名单等环境变量不会传入 Codex。
- [x] 新报告通过章节、五维标签、人员覆盖和敏感字段检查后才原子替换 `manager_report.md`；失败保留上一份有效报告。
- [x] 状态、日志和错误只记录周次、摘要数字、阶段、散列和安全错误码，不记录姓名、userid、正文、token 或完整模型输出。
- [x] 默认测试不调用 Codex、钉钉、真实网络、真实 MySQL 或真实输出目录。

## 约束

- 相关产品不变量：上一业务周周一至周三补交仍计入该周；周四 00:00 后形成最终输入；正式输出路径保持 `output/<YYYY-Www>/summary/manager_report.md`。
- 相关架构边界：Python 负责采集、指纹、状态与机械验证；Codex Skill 负责评价判断；cron 只触发 Harness，不内嵌业务逻辑。
- 需要保留的无关工作区修改：开始时工作区干净；不得修改 `weekly_report_template.txt`。

## 验证

- 测试：指纹变化、跳过条件、重试限制、环境净化、命令安全参数、章节/人员/负责人覆盖、敏感输出拒绝、原子写入和锁冲突。
- 手工/集成证据：使用虚构周次、姓名、userid、附件和伪 Codex 进程；检查 cron 示例、文件预算、diff 隐私和空白错误。
- 统一命令：`powershell -ExecutionPolicy Bypass -File scripts/verify.ps1`

## 交付

- 决策：服务器使用 Linux cron 调用仓库内 Harness；Codex CLI 本身只提供非交互 `codex exec`，不在服务器 CLI 中提供 Scheduled 管理界面。模型不直接写正式报告，由 Harness 校验结构化输出后原子落盘。
- 剩余风险：服务器 Codex 登录状态、CLI 版本、模型额度和 Office/PDF 解析工具需要部署时验证；输入变化后的评价是完整重建，不做脆弱的局部文本补丁。
- 延后工作：Web 展示自动评价运行历史、钉钉管理员结果通知、systemd timer 和多机分布式锁。
