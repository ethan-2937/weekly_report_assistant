# Task: Bootstrap a privacy-aware repository harness

## 问题

仓库已有丰富业务记忆，但入口文档过长，Python、Spring 和 Vue 没有统一验证，64个后端文件和5个Python脚本没有自动化测试。周报、通讯录、权限和钉钉凭据又要求比普通项目更严格的隐私边界。

## 范围

- 包含：分层仓库地图、产品/架构/质量/隐私文档、Maven Wrapper、结构与隐私检查、CI、统一验证和首批测试。
- 不包含：真实钉钉调用、生产部署、权限业务改写、大文件重构和前端测试框架。

## 验收标准

- [x] 根 `AGENTS.md` 是短地图，子系统拥有局部指南。
- [x] 真实周报模板由哈希保护。
- [x] Git 跟踪的敏感运行路径会被机械拒绝。
- [x] Python 与 Spring 至少各有一组确定性测试。
- [x] 一个命令完成结构、隐私、Python、Spring 和 Vue 验证。
- [x] CI 使用同一验证入口。

## 约束

- 相关产品不变量：上一完整周、userid匹配、模板与管理规则分离、服务层权限。
- 相关架构边界：Python采集、Skill评价、Spring授权、Vue展示。
- 需要保留的无关工作区修改：任务开始时工作区干净。

## 验证

- `scripts/check-harness.ps1`
- Python `compileall` 与 `unittest`
- Maven tests
- Vite production build
- 统一命令：`powershell -ExecutionPolicy Bypass -File scripts/verify.ps1`

## 交付

- 决策：采用当前规模可通过的复杂度棘轮；默认测试不访问外部系统。
- 剩余风险：权限核心尚缺充分测试；开发默认凭据仍需生产启动保护；前端仍是大组件。
- 延后工作：按风险优先补权限/匹配测试，再渐进拆分前端和反馈服务。
