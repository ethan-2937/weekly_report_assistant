# Weekly Report System - Codex Map

本仓库包含钉钉周报采集、规则化输入、Codex 周报分析、Spring Boot API 和 Vue 管理界面。仓库是持久上下文的唯一来源；聊天记忆不能替代版本化规则。

## 开始工作

1. 阅读 `docs/PRODUCT.md`，确认用户、输出和业务不变量。
2. 阅读 `docs/ARCHITECTURE.md`，确认 Python、Skill、Spring 和 Vue 的边界。
3. 阅读最近的子目录 `AGENTS.md`。
4. 非简单任务从 `docs/tasks/TEMPLATE.md` 创建任务记录。
5. 修改评价口径前阅读 `codex-skills/weekly-report-assistant/SKILL.md`。
6. 交付前运行 `powershell -ExecutionPolicy Bypass -File scripts/verify.ps1`。

## 仓库地图

- `scripts/`：钉钉采集、人员匹配和分析输入包生成，见 `scripts/AGENTS.md`。
- `codex-skills/weekly-report-assistant/`：周报评价工作流与输出标准，见其 `AGENTS.md`。
- `web/backend-spring/`：当前 Spring Boot 后端，见其 `AGENTS.md`。
- `web/frontend/`：当前 Vue 前端，见其 `AGENTS.md`。
- `web/backend/`：早期 Java HTTP Server，仅作历史参考，不新增功能。
- `docs/`：产品、架构、质量、安全和部署文档。
- `output/`、`logs/`：包含运行数据或敏感信息，永不提交。
- `weekly_report_template.txt`：钉钉线上真实模板的镜像，受哈希保护。

## 不可破坏的不变量

- 默认统计上一完整 ISO 周：周一 00:00 至周日 23:59:59，Asia/Shanghai。
- 应交与实交优先按 DingTalk `userid` 匹配；无法确定时进入待确认，不能猜测。
- 钉钉模板合规性和管理筛选标准是两套规则，不能相互冒充。
- `manager_report.md` 固定写入 `output/<YYYY-Www>/summary/`，前端依赖此契约。
- 部门与人员范围必须在后端服务层过滤；隐藏前端控件不构成权限控制。
- 不记录、提交或输出密钥、访问令牌、原始周报、通讯录及不必要的员工隐私。
- 默认测试不得访问钉钉、真实 MySQL、真实账号或外部通知接口。
- `weekly_report_template.txt` 只有在用户明确确认钉钉线上模板已变更时才能更新。

## 工作约定

- 保留工作区中与任务无关的修改，不回退他人工作。
- 新功能只进入 Spring 后端；不要扩展 `web/backend/`。
- 修改权限、人员匹配、缺交判断或评价标签时必须添加回归测试。
- 不通过放宽质量预算、删除检查或伪造样例让验证通过。
- 行为、接口、部署或评价规则变化时，同一任务更新相应文档和 Skill。
- 验证通过且无隐私/远程异常时，按本仓库既有约定提交并推送；遇到敏感数据、无关改动、构建失败或远程异常时停止推送并报告。

## 完成定义

- 验收标准满足，失败路径和权限路径得到验证。
- `scripts/verify.ps1` 通过。
- Diff 不包含凭据、原始员工数据、构建产物或无关修改。
- 任务记录说明改动、验证、风险和后续工作。
- 交付说明提交与推送状态；未推送时给出明确原因。
