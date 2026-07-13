# 架构地图

## 主数据流

```text
DingTalk APIs
    -> Python collection scripts
    -> output/<week> files
    -> Codex weekly-report skill
    -> manager_report.md
    -> Spring Boot permission/filter layer
    -> Vue management UI

MySQL
    -> users, roles, scopes, login logs, jobs
    -> Spring Security/JWT
```

## 运行边界

- Python 负责外部采集、周次计算、人员匹配和生成确定性输入包。
- Codex Skill 负责从授权输入生成管理评价，不负责身份认证或数据权限。
- Spring Boot 负责身份、数据范围、文件读取、任务触发和 API。
- Vue 负责展示和交互，不承担权限裁决。
- MySQL 保存认证、权限和任务状态；周报正文当前以文件形式保存。

## Spring 依赖方向

```text
controller -> service -> service/impl -> mapper/file/database
                       -> security/config adapters
```

Controller 不得绕过服务层读取文件或数据库。权限过滤应尽量靠近业务返回边界，并覆盖列表、详情、Markdown 和 CSV 下载。

## 认证与配置边界

- 前端 `api/client.js` 统一处理 fetch、Authorization、响应解析、敏感错误清洗和 401；`composables/useAuth.js` 统一处理 token 持久化、OAuth 回调和登录态失效。
- `features/auth/LoginView.vue` 只负责登录呈现和提交事件，用户名密码与钉钉登录协议仍由既有后端接口提供。
- Spring 启动时由 `ProductionCredentialValidator` 校验 JWT secret 和初始管理员密码。生产模式是默认值；只有显式开发模式允许本地默认凭据。

## 活动与历史实现

- 活动后端：`web/backend-spring/`。
- 历史后端：`web/backend/`，仅保留迁移参考。
- 活动前端：`web/frontend/`。

## 变化检查

- 输出文件字段变化：同步 Python、Spring Mapper、Skill 和前端。
- 权限范围语法变化：同步用户管理、解析服务、拒绝路径测试和文档。
- 评价标签变化：同步 Skill、输出格式和 Markdown 着色规则。
- 钉钉模板变化：必须由用户确认线上模板已变更，再更新受保护文件哈希。
