# Task: 权限回归、生产凭据保护与前端认证切片

## 问题

### 当前实现证据

- `ReportPermissionServiceImpl` 当前把 `ADMIN`、`REPORT_ALL` 和 `ALL` 范围都视为完整周报权限；这与 `docs/PRODUCT.md` 中“ADMIN 不自动拥有完整周报内容”冲突。
- 周报列表、摘要、分析 Markdown 和 CSV 都经过 `WeeklyReportServiceImpl` 的服务层权限过滤，但缺少系统化回归测试；现有 Spring 测试只覆盖周次标签工具。
- `/api/jobs/**` 由 Spring Security 限制为 `ADMIN` 或 `REPORT_ALL`，尚无 HTTP 状态回归测试。
- `application.yml`、`WeeklyReportProperties` 和 Docker Compose 都提供开发 JWT secret 与初始管理员密码默认值，生产部署可静默沿用。
- `App.vue` 当前集中包含 token 生命周期、通用 fetch、OAuth 回调、登录界面和业务页面；实际 1495 行，`styles.css` 实际 2288 行，前端没有测试命令。
- 初始化器还包含既有的固定全权限账号引导逻辑；本任务不在无明确产品决策时改变其角色、密码哈希或钉钉绑定行为。

### 风险分析

- `ADMIN` 自动获得完整周报会越过账号管理与员工敏感内容之间的职责分离。
- 部门、人员、userid、Markdown 或 CSV 任一路径过滤不一致，都可能通过直接 API 调用泄露正文或评价。
- 生产环境沿用开发凭据会导致 JWT 可伪造或初始管理员账号可被猜测。
- 认证逻辑散落在巨型组件内，容易产生下载接口漏加 401 处理、错误消息拼入敏感响应等回归。

## 范围

- 包含：权限服务和周报服务单元测试、Security HTTP 集成测试、证据明确的最小权限修复。
- 包含：显式开发模式与生产凭据启动校验、Docker/示例配置/部署文档同步。
- 包含：抽取 API client、认证 API、`useAuth` 和登录视图，建立 Vitest 测试基础。
- 包含：按真实行数下降收紧 `App.vue` 预算，同步架构、质量、安全和子目录指南。
- 不包含：评价口径、真实周报/通讯录、钉钉采集、历史后端、全量前端重写、全局状态框架。
- 不包含：在未获授权时重构固定全权限账号初始化模型或修改密码哈希/钉钉登录协议。
- 不包含：强行拆分本次未安全触及的 `styles.css`。

## 验收标准

- [x] `REPORT_ALL` 可读取完整数据；仅有 `ADMIN` 时必须有明确范围才能读取周报。
- [x] `DEPT:`、`USER:`、`USERID:`、兼容文本、多范围、空范围和无匹配范围均有虚构数据测试。
- [x] 列表、摘要/Markdown、CSV 使用同一服务层过滤；普通范围账号不能调用任务接口。
- [x] 未认证返回 401、权限不足返回 403，错误内容不包含正文、token、密码或内部配置。
- [x] 生产模式拒绝默认 JWT secret 和默认初始管理员密码，并指出需配置的环境变量；显式安全值可通过。
- [x] 本地显式开发模式保留快速启动；Docker 生产入口不再静默回退开发凭据。
- [x] 通用请求、token 生命周期、401 和 OAuth URL 清理由抽取边界统一处理，登录与钉钉登录行为保持。
- [x] 前端 Vitest 覆盖 Authorization、有/无 token、401、敏感错误、OAuth 清理和登录失败密码保护。
- [x] `App.vue` 预算只按实际缩减下调，任何 Harness 预算均不提高。

## 约束

- 相关产品不变量：后端服务层决定数据可见范围；ADMIN 管理账号但不自动读取完整周报；REPORT_ALL 可查看完整数据；任务接口仍允许 ADMIN 或 REPORT_ALL。
- 相关架构边界：Controller 只依赖 Service；认证/API 边界从 `App.vue` 抽取但不引入新状态框架。
- 需要保留的无关工作区修改：开始时工作区干净；后续若出现未知改动立即停止并确认。
- 隐私边界：不读取 `.env`、`config/.env`、`output/`、`logs/` 或真实业务数据；测试只使用明显虚构数据且不访问网络/MySQL/钉钉。

## 验证

- 测试：后端定向单元/集成测试、`npm test`、Python 既有测试。
- 手工/集成证据：前端生产构建、实际行数、生产/开发配置校验测试、权限覆盖矩阵。
- 统一命令：`powershell -ExecutionPolicy Bypass -File scripts/verify.ps1`
- Git：`git diff --check`、`git status`、暂存后隐私扫描；只有全部通过且无敏感/无关改动时才提交和推送。

## 交付

- 决策：完整周报只由 `REPORT_ALL` 或显式 `ALL` 范围授予；`ADMIN` 继续拥有账号管理和任务接口权限，但读取周报时必须受范围约束。
- 决策：生产模式默认开启凭据校验；`WEEKLY_AUTH_DEVELOPMENT_MODE=true` 仅由本地运行脚本显式启用。Compose 对两个生产认证变量使用必填插值，Spring 再做默认值/空值校验。
- 权限覆盖：11 个范围单测覆盖 REPORT_ALL、ADMIN、DEPT、USER、USERID、兼容文本、多范围、无匹配、空范围和 ALL；3 个周报服务测试覆盖列表、完整/过滤 Markdown 与 CSV；7 个 HTTP 测试覆盖 401、403、任务角色、服务层拒绝和异常脱敏。
- 前端覆盖：8 个 Vitest 用例覆盖 Authorization 有/无 token、401 统一失效、错误脱敏、登录失败密码保护、OAuth token 保存/URL 清理及 401 后持久化清理。
- 复杂度：`App.vue` 从 1499 行降到 1324 行，预算从 1550 降到 1324；`styles.css` 保持 2288 行，本次未强行拆分。
- 验证：`scripts/check-harness.ps1`、全部 27 个 Spring 测试、全部 8 个前端测试、4 个 Python 测试、Vite 生产构建和统一 `scripts/verify.ps1` 均通过；构建仍提示既有的单包超过 500 kB。
- 剩余风险：固定全权限账号引导逻辑仍包含历史固定账号/初始凭据策略，需要单独的产品与安全决策；本次不静默改变角色、哈希或钉钉绑定流程。
- 延后工作：`ChangePasswordDialog`、`styles.css` 的功能化拆分，以及 App 中下一批周次/评价/任务/用户管理切片；前端主包按后续切片引入懒加载。
