# Task: Apply exemptions in Docker and download leader attachments for Codex

## 问题

服务器重跑后应交候选仍为 101，说明免交配置未被采集脚本读取。当前根 `.env` 不会自动传递任意变量到容器，且 `load_env()` 让 `config/.env` 覆盖进程环境，空配置也会覆盖有效值。负责人履职输入目前只有附件元数据，Codex 无法读取附件正文。

## 当前证据与风险

- Compose 未声明 `WEEKLY_REPORT_EXEMPT_SUBMITTERS`，根 `.env` 中的值不会进入容器。
- `config/.env.example` 包含空免交项；当前合并顺序会用空值覆盖容器环境变量。
- 阿里云官方钉钉 SDK 2.2.52 定义 Drive 下载信息接口：`GET /v1.0/drive/spaces/{spaceId}/files/{fileId}/downloadInfos`，需要 `unionId` 和访问令牌，返回短期资源地址及下载 headers。
- 下载地址、fileId、spaceId、unionId、附件正文均为敏感数据，不得进入日志、Git 或错误响应。

## 范围

- 包含：Compose 透传免交配置；进程环境优先于配置文件；仅下载负责人周报附件；限制协议、文件类型、单文件及总大小；写入周次私有附件目录；分析输入只记录本地相对路径和安全状态；Skill 指示 Codex 读取本地附件。
- 不包含：下载普通员工附件；上传或转发附件；把附件正文复制到日志/数据库；修改钉钉模板；绕过钉钉文件权限。

## 验收标准

- [x] 根 `.env` 中的免交变量能进入容器，且不会被 `config/.env` 空值覆盖。
- [x] 非 Docker 运行仍可从 `config/.env` 配置免交名单。
- [x] 只为负责人、且仅在存在 `unionId/fileId/spaceId` 时请求下载信息。
- [x] 下载信息接口与资源下载均使用注入式 HTTP 边界，测试不访问真实网络。
- [x] 只允许 HTTPS、受控扩展名和大小上限；失败不影响周报主流程。
- [x] 分析输入包含成功下载的本地相对路径，绝不包含 token、fileId、spaceId 或短期 URL。
- [x] Skill 对本地 PDF/Word/Excel/文本附件进行解析，无法读取时明确报告而不留空。

## 约束

- 产品不变量：负责人个人周报和团队职责分别评价；证据不可见不等于未履职。
- 架构边界：Python 下载并落盘，Codex Skill 解析，Spring 服务层过滤，Vue 只展示。
- 隐私：真实附件只进入忽略的 `output/<week>/attachments/`，不进入测试、Git 或普通日志。

## 验证

- Python：环境合并、接口请求、下载 headers、扩展名/大小拒绝、失败降级、负责人范围和分析路径。
- Spring/前端：现有权限和展示回归。
- 统一命令：`powershell -ExecutionPolicy Bypass -File scripts/verify.ps1`

## 交付

- 决策：使用官方 Drive 下载信息接口，不在分析包中保存短期下载 URL。
- 实现：Compose 显式透传免交变量，进程环境优先于配置文件；负责人附件落盘到 userid 哈希目录，分析包只保留安全文件名和本地路径。
- 验证：29 个 Python 测试、28 个 Spring 测试、8 个 Vitest 测试、前端生产构建、Harness 与 `scripts/verify.ps1` 全部通过。
- 安全：覆盖 HTTPS、私网 URL、类型、大小、缺失 unionId、下载信息失败、非负责人、敏感元数据清洗和无真实网络路径。
- 复杂度：`attachment_download.py` 255 行，`run_weekly.py` 保持 134 行预算，其他新增/修改文件均未超过预算。
- 剩余风险：钉钉应用缺少 Drive 权限或文件所有者 `unionId` 时只能报告下载信息获取失败；未在本地调用真实钉钉验证生产权限。
- 延后工作：根据实际文件类型扩展安全解析能力和附件保留周期清理。
