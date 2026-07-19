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

Spring Sunday scheduler
    -> Python current-week reminder snapshot
    -> stable userid + exemption comparison
    -> DingTalk work-notice adapter
    -> missing employees + configured administrator result

Codex evaluation Harness
    -> manager_report.md + private employee_feedback.json
    -> Python coverage/privacy validation
    -> Spring Monday 12:00 scheduler
    -> one private DingTalk notice per submitted stable userid
    -> configured administrator result without employee detail
```

## 运行边界

- Python 负责外部采集、周次计算、提交归属窗口、人员匹配、免交名单过滤、负责人附件下载和生成确定性输入包；采集将指定模板写入 `raw/reports.json` 供统计与评价，同时将指定模板和旧“周报”模板写入 `raw/all_reports.json` 供原文件导出，两者不能混用；`submission_roster.py` 应用服务器私有免交配置，`submission_reminder.py` 只生成截至当前时刻的轻量候选快照，不写正式周报输出，`attachment_download.py` 通过钉钉 Drive 短期下载信息受控落盘，`leader_compliance.py` 生成履职证据，`project_details.py` 提取不拆分的项目明细和权限身份字段，Spring 过滤后对外提供八列，`weekly_outputs.py` 统一生成 CSV/Markdown。
- `run_codex_evaluation.py` 在宿主机编排上一业务周采集、输入指纹和状态；只有输入变化或正式报告无效时才调用非交互 Codex。Codex 运行在只含当前周授权快照的临时目录，模型结果经覆盖/隐私校验后由 Python 原子替换正式报告。
- 同一次自动评价还生成私有 `employee_feedback.json`；Harness 要求反馈 userid 集合与已提交集合完全一致，并拒绝姓名、其他人员信息、secret 形态和超长正文。周一通知不解析 Markdown，也不再次调用 Codex。
- Codex Skill 负责从授权输入生成管理评价，不负责身份认证或数据权限。
- Spring Boot 负责身份、数据范围、文件读取、任务触发、周日定时编排和钉钉工作通知；反馈与周报提醒复用同一通知适配器。
- Spring 周一评价通知读取上一完整周的成功评价状态、正式报告摘要和私有反馈清单，四者不一致即失败关闭；每名员工单独发送，结果不确定时通过无明细状态阻止自动重发。
- 管理员通知试发复用同一钉钉适配器，但只向精确确认且唯一的反馈接收人发送明确标注的样例；它不调用候选提供器，也不依赖或写入正式任务状态。
- Vue 负责展示和交互，不承担权限裁决；AI 评价使用已过滤的提交状态构建姓名索引，点击后只按需读取一个人的原文，不批量预取全员正文。
- MySQL 保存认证、权限和任务状态；周报正文当前以文件形式保存。

## Spring 依赖方向

```text
controller -> service -> service/impl -> mapper/file/database
                       -> security/config adapters
```

Controller 不得绕过服务层读取文件或数据库。权限过滤应尽量靠近业务返回边界，并覆盖列表、详情、Markdown 和 XLSX 下载；下载工作簿由服务层从授权行生成。

原周报下载使用 `GET /api/files/{week}/original-reports/download`。服务先取得当前账号范围，再读取私有 `raw/all_reports.json`，将每条原始提交映射为权限行并保留重复记录，最后用流式 OOXML 生成两个工作表。缺少完整快照、身份字段、受支持模板或单元格超出 XLSX 限制时失败关闭；前端不提交正文或员工列表。

单人周报原文使用 `GET /api/weeks/{week}/reports/{userid}`。Controller 只委托 `WeeklyReportSourceService`；服务先对提交状态应用当前账号范围，再按授权行的 `report_id`、稳定 `userid` 读取目标周原始文件。响应只包含单人的展示字段，不包含原始 JSON、附件或内部标识，并对单份预览大小设置上限。

负责人履职链路遵循“确定性证据先于 AI 结论”：Python 先将服务器私有负责人覆盖配置按精确唯一 selector 解析为 userid，再输出全部负责人、附件证据状态和负责人-下属确认映射；没有显式覆盖时才按共享钉钉部门生成待确认候选，并排除免交人员。只下载负责人允许类型/大小的附件并将本地相对路径交给 Skill；Skill 读取本地附件并生成管理结论；Spring 对正式履职表及原始负责人输入逐行过滤，Vue 只渲染授权后的 Markdown。

## 认证与配置边界

- 前端 `api/client.js` 统一处理 fetch、Authorization、响应解析、敏感错误清洗和 401；`composables/useAuth.js` 统一处理 token 持久化、OAuth 回调和登录态失效。
- `features/auth/LoginView.vue` 只负责登录呈现和提交事件，用户名密码与钉钉登录协议仍由既有后端接口提供。
- Spring 启动时由 `ProductionCredentialValidator` 校验 JWT secret 和初始管理员密码。生产模式是默认值；只有显式开发模式允许本地默认凭据。
- 周日提醒默认关闭；启用后固定使用 `Asia/Shanghai`，候选进程有超时且返回值不进入普通日志。同一周只落盘不含姓名和 userid 的阶段/汇总状态，用于阻止不确定发送后的自动重试。

## 活动与历史实现

- 活动后端：`web/backend-spring/`。
- 历史后端：`web/backend/`，仅保留迁移参考。
- 活动前端：`web/frontend/`。

## 变化检查

- 输出文件字段变化：同步 Python、Spring Mapper、Skill 和前端。
- 权限范围语法变化：同步用户管理、解析服务、拒绝路径测试和文档。
- 评价标签变化：同步 Skill、输出格式和 Markdown 着色规则。
- 钉钉模板变化：必须由用户确认线上模板已变更，再更新受保护文件哈希。
- 周次或补交截止变化：同步 `dingtalk_common.py`、两个采集入口、Skill、自动任务时间和无网络边界测试。
