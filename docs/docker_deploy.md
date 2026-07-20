# Docker 部署说明

## 构建并启动

```bash
cd /data2/person_path/yzzhang/weekly-report
cp .env.example .env
# 必须填写 WEEKLY_JWT_SECRET 和 WEEKLY_BOOTSTRAP_ADMIN_PASSWORD；不要启用开发模式
docker compose up -d --build
```

默认访问：

```text
http://服务器IP:22080
```

如果根目录 `.env` 设置了 `WEEKLY_HOST_PORT=22081`，访问地址就是 `http://服务器IP:22081`。容器内部始终监听 `8088`。

## 数据挂载

`docker-compose.yml` 会把以下目录映射到容器：

```text
./config/.env -> /app/config/.env:ro
./output      -> /app/output
./logs        -> /app/logs
```

这样容器生成的 `output/<周次>` 仍然保存在服务器项目目录下，服务器 Codex 可以继续读取并生成：

```text
output/<周次>/summary/manager_report.md
```

新版本采集还会生成私有 `output/<周次>/raw/all_reports.json`，用于 Web 中“下载原周报”。它只包含指定周报模板和旧“周报”模板，不参与提交统计或 AI 评价。W28 经用户确认可直接回退既有 `raw/reports.json`，不要求重新采集；其他升级前已经采集的周次缺少该快照时，必须在服务器重新运行对应周次采集。

前端会自动展示这个文件。

Docker 部署的免交名单优先配置在根目录 `.env`，Compose 会显式传入容器：

```text
WEEKLY_REPORT_EXEMPT_SUBMITTERS=USERID:test-user-001;NAME:示例员工甲
```

优先使用稳定 userid；姓名仅作精确兼容匹配。直接在宿主机运行脚本时也可写入 `config/.env`；进程环境优先于文件配置。免交人员及其提交会在 CSV、Markdown 和分析输入统计前统一排除，真实名单不得写入仓库。

负责人及直属关系可在服务器未跟踪的根 `.env` 中用 `WEEKLY_REPORT_LEADER_OVERRIDES` 覆盖钉钉部门推断。值是单行 JSON，键和值使用 `USERID:` selector；只有暂时无法取得 userid 时才使用完整且唯一的 `NAME:`。每条规则显式提供 `leader` 和 `subordinates`，例如：

```text
WEEKLY_REPORT_LEADER_OVERRIDES={"USERID:test-leader-001":{"leader":true,"subordinates":["USERID:test-user-002"]},"USERID:test-user-003":{"leader":false}}
```

采集时 selector 会先解析为 userid；姓名重名、人员不存在、自映射或同一直属员工配置给多个负责人都会停止生成，避免猜测关系。真实值不得写入源码、示例、日志或任务记录。

负责人附件通过钉钉 Drive 下载信息接口写入 `output/<周次>/attachments/team_leads/`。该目录已随 `output/` 忽略并挂载持久化；下载失败不会中断整周采集，分析输入会记录安全失败状态。

## MySQL 与登录

`docker-compose.yml` 会同时启动：

```text
weekly-report       # Spring Boot + Vue 静态资源
weekly-report-mysql # MySQL 8.4
```

首次启动时，后端会自动创建认证表，并使用 `.env` 中的配置预置管理员。密码会以 BCrypt 哈希写入 MySQL，不会明文保存。正式使用要求：

1. 根目录 `.env` 必须提供非空、非开发默认值的 `WEEKLY_JWT_SECRET` 和 `WEEKLY_BOOTSTRAP_ADMIN_PASSWORD`。
2. 保持 `WEEKLY_AUTH_DEVELOPMENT_MODE=false`；缺失安全凭据时 Compose 或 Spring 启动会直接失败。
3. 配置部署专用的 `MYSQL_ROOT_PASSWORD`，不要提交真实值。
4. 如果启用钉钉登录，再补充 `WEEKLY_DINGTALK_LOGIN_ENABLED=true`、`WEEKLY_DINGTALK_CLIENT_ID`、`WEEKLY_DINGTALK_CLIENT_SECRET`、`WEEKLY_DINGTALK_REDIRECT_URI`。

钉钉回调地址示例：

```text
http://服务器IP:22081/api/auth/dingtalk/callback
```

## 周日未交提醒

提醒功能默认关闭。启用前必须确认：

1. `config/.env` 中的 `DINGTALK_REPORT_TEMPLATE` 是指定周报模板，采集凭据能够读取通讯录和周报。
2. `WEEKLY_REPORT_EXEMPT_SUBMITTERS` 在容器中生效，免交人数与预期一致。
3. 现有反馈通知接收人和 `WEEKLY_FEEDBACK_DINGTALK_*` 工作通知凭据有效。
4. 钉钉企业应用可见范围覆盖所有应交人员，而不只是管理员。

先运行只输出汇总数字、不会发消息的预检：

```bash
docker compose exec weekly-report python3 /app/scripts/submission_reminder.py
```

预检只应显示周次、截止时间和应交/已交/未交/待确认人数，不应显示姓名、userid 或 token。核对无误后在根目录 `.env` 设置：

```text
WEEKLY_SUBMISSION_REMINDER_ENABLED=true
WEEKLY_SUBMISSION_REMINDER_CRON=0 0 18 * * SUN
WEEKLY_SUBMISSION_REMINDER_ZONE=Asia/Shanghai
WEEKLY_SUBMISSION_REMINDER_PROCESS_TIMEOUT_SECONDS=180
```

然后重建并重新创建应用容器：

```bash
docker compose up -d --build --force-recreate weekly-report
```

正常情况下，Spring 在每周日 18:00 实时检查当前业务周：未交人员分别收到私人工作通知，配置的反馈接收人无论执行成功或失败都会收到汇总结果。周日结果只是提醒，不是周三补交截止后的最终缺交结论。

防重复状态写入 `output/<YYYY-Www>/reminders/sunday-1800.json`，只包含任务阶段、汇总人数和时间。若员工通知的远程结果不确定，系统不会自动重发，管理员结果通知会要求人工检查。

## 周一个人评价通知

个人评价通知默认关闭。它不在周一再次调用 Codex，而是读取最近一次自动评价同时生成并通过 Harness 校验的 `output/<YYYY-Www>/automation/employee_feedback.json`。启用前必须确认：

1. 宿主机 Codex Skill 已同步到当前仓库版本，并且周一 12:00 前的自动评价 cron 能成功完成上一业务周评价。
2. `evaluation_state.json` 状态为 `SUCCESS`，正式报告和私有反馈文件均已生成；首次升级后需重新运行一次目标周评价，旧报告没有反馈清单。
3. 钉钉应用可见范围覆盖全部已提交人员，现有 `WEEKLY_FEEDBACK_DINGTALK_*` 工作通知凭据有效。
4. HR 联系人只写入服务器未跟踪的根 `.env`，不要写回源码或 `.env.example`。

服务器根 `.env` 配置：

```text
WEEKLY_EVALUATION_FEEDBACK_ENABLED=true
WEEKLY_EVALUATION_FEEDBACK_CRON=0 0 12 * * MON
WEEKLY_EVALUATION_FEEDBACK_ZONE=Asia/Shanghai
WEEKLY_EVALUATION_FEEDBACK_HR_CONTACT=<实际HR联系人>
```

然后重新复制 Skill、手动生成一次上一周评价并重建应用容器：

```bash
rm -rf "$HOME/.codex/skills/weekly-report-assistant"
cp -a codex-skills/weekly-report-assistant "$HOME/.codex/skills/weekly-report-assistant"
./scripts/run_codex_evaluation.sh --force
docker compose up -d --build --force-recreate weekly-report
```

每位已提交员工只收到本人的私人消息，先展示做得好的地方，再重点展示改进建议，然后用基于本人当周贡献的两句式感谢收束，最后附配置的 HR 联系方式。v1 旧反馈没有 `thanks` 时使用固定暖心感谢，不阻断既有通知。管理员收到的成功或失败结果只含周次和人数。防重复状态写入 `output/<YYYY-Www>/notifications/monday-evaluation-feedback.json`；远程结果不确定时不会自动重发。

### 上线前向单一接收人试发

如需在正式调度前验证两种钉钉发送链路，先确认根 `.env` 中 `WEEKLY_FEEDBACK_RECIPIENT_NAME=张艺政`，且 `WEEKLY_FEEDBACK_DINGTALK_USER_IDS` 只配置张艺政一个稳定 userid。不要打印或提交该值。重建容器后用 ADMIN 登录 Web，进入“运行状态 → 自动通知试发”，依次点击“测试周日提醒”和“测试周一评价”并确认。

两条消息都会醒目标注“测试”：前者复用周日批量 Markdown 发送接口，后者复用周一逐人 Markdown 发送接口。试发不读取提交状态、周报正文或评价文件，不会创建或修改 `sunday-1800.json`、`monday-evaluation-feedback.json`，因此不影响随后正式任务。REPORT_ALL 或普通范围账号不能试发。

## 自动采集与评价

正式 AI 自动评价使用宿主机 Codex Harness：从周日 18:10 起处理当前 ISO 周，周一和周二每两小时继续刷新同一目标周，周二 22:10 后停止。完整 cron 和 `--scheduled-window` 配置见 `docs/server_codex_automation.md`。

Spring Scheduler 的周日提醒和周一个人反馈只负责通知，不会触发采集或 Codex。Harness 每次先通过容器或宿主机采集，再按输入指纹决定是否调用模型。

如只需要采集而不生成 AI 评价，仍可单独调用：

```bash
0 9 * * 4 docker exec weekly-report python3 /app/scripts/run_weekly.py --week previous
```

注意：正式 AI 评价仍建议由 Codex skill 生成并写入 `manager_report.md`，因为它需要模型完成管理评价。

## 资源占用

当前 Docker 部署的资源预估：

- 镜像大小约 `487 MB`。
- Java 容器运行内存通常约 `170-220 MiB`，MySQL 容器通常约 `300-600 MiB`。
- Compose 已设置 Java 服务 `mem_limit: 768m`，Java 参数为 `-Xms128m -Xmx384m`。
- 首次构建会下载 Node/Maven 依赖，建议预留 `3-5 GB` 磁盘；构建完成后 Docker build cache 可能额外占用 `1-2 GB`。
- `output/` 会随着每周数据增长，通常每周从几十 KB 到数 MB 不等。

如果需要清理无用构建缓存：

```bash
docker builder prune
```

执行前确认当前没有正在构建的镜像。
