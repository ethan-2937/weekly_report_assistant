# Task: 修复 FeedbackRecipientResolver 导致的容器启动失败

## 问题

服务器提交 `add5030` 后，`weekly-report` 容器持续 `Restarting (1)`，没有可用端口。诊断日志显示 Spring 在创建 `submissionReminderService` 时无法实例化 `FeedbackRecipientResolver`，原因是该组件存在多个构造器但没有明确的自动注入构造器，Spring 尝试查找不存在的无参构造器。

前端提交只改变前端文件和测试配置，没有改变 Docker 端口映射；本次修复针对此前提醒通知功能引入的后端 Bean 装配问题。

## 范围

- 包含：明确标注 `FeedbackRecipientResolver` 的生产构造器；补充针对构造器选择的回归证据；更新本任务记录。
- 不包含：前端视觉、Docker 端口、数据库结构、钉钉凭据、提醒业务规则重写。

## 验收标准

- [ ] Spring 可以装配 `FeedbackRecipientResolver` 和 `SubmissionReminderService`。
- [ ] 现有反馈接收人解析和周日提醒逻辑保持不变。
- [ ] 现有单元测试通过，构建出的容器启动后监听 8088。
- [ ] 不在日志、测试或提交中输出真实凭据或周报数据。

## 约束

- 相关产品不变量：提醒通知仍由显式配置控制，接收人解析失败时保持安全失败。
- 相关架构边界：只修改当前 Spring 后端，不扩展历史 `web/backend/`。
- 需要保留的无关工作区修改：保留现有前端提交及用户私有服务器配置。

## 验证

- 测试：`web/backend-spring/mvnw.cmd test`、`web/backend-spring/mvnw.cmd package`。
- 手工/集成证据：服务器执行 `docker compose up -d --build weekly-report` 后检查 `docker compose ps` 和容器日志；不要把完整日志或凭据提交到仓库。
- 统一命令：`powershell -ExecutionPolicy Bypass -File scripts/verify.ps1`

## 交付

- 决策：使用 Spring 明确的 `@Autowired` 生产构造器，保留包可见测试构造器注入虚构环境读取函数。
- 剩余风险：服务器需要拉取修复提交并重建镜像；旧容器不会自动获得新 JAR。
- 延后工作：为关键 Spring 配置增加轻量应用上下文启动回归测试。
