# Codeup 到 Ubuntu 服务器部署说明

公司后续使用云效 Codeup 作为主仓库，Git 地址：

```text
https://codeup.aliyun.com/684beabd28a6beb51d765af2/weekly-report.git
```

目标服务器：`yzzhang@182.92.251.239`
目标目录：`/data2/person_path/yzzhang/weekly-report`
当前对外端口：`22081`

## 1. 登录服务器

```bash
ssh yzzhang@182.92.251.239
```

## 2. 首次部署

```bash
mkdir -p /data2/person_path/yzzhang
cd /data2/person_path/yzzhang
git clone https://codeup.aliyun.com/684beabd28a6beb51d765af2/weekly-report.git weekly-report
cd weekly-report
```

如果 Codeup 要求登录，使用云效账号和个人访问令牌，不要把令牌写进 README 或聊天记录。

## 3. 配置钉钉密钥

`config/.env` 是运行配置文件，只放在服务器上，不提交到代码仓库。

```bash
cd /data2/person_path/yzzhang/weekly-report
cp config/.env.example config/.env
nano config/.env
chmod 600 config/.env
```

需要填写的核心项：

```text
DINGTALK_APP_KEY=钉钉应用的 Client ID 或 AppKey
DINGTALK_APP_SECRET=钉钉应用的 Client Secret
DINGTALK_AGENT_ID=钉钉应用的 AgentId
DINGTALK_ROOT_DEPT_ID=1
DINGTALK_REPORT_TEMPLATE=优智科技周报（Weekly Outcomes）
OUTPUT_ROOT=output
```

Docker 部署将 `WEEKLY_REPORT_EXEMPT_SUBMITTERS` 写入根目录 `.env`；直接运行 Python 时也可写入 `config/.env`。该项使用英文或中文分号/逗号分隔，优先配置稳定 `USERID:`，无法取得 userid 时才使用完整 `NAME:`。进程环境优先于文件配置，真实名单不得提交到仓库；匹配为规范化后的完整值，不支持手机号或姓名的模糊匹配。

用户人工确认的负责人关系写入同一未跟踪 `.env` 的 `WEEKLY_REPORT_LEADER_OVERRIDES`，格式和失败关闭规则见 `docs/docker_deploy.md`。生产环境优先使用 `USERID:`；如临时使用 `NAME:`，必须确保通讯录中精确唯一匹配。采集成功后再从私有 `leader_subordinates.csv` 核对关系，不要把该文件提交或粘贴到普通日志。

## 4. 配置服务端口

服务器运维端口按实际环境分配。根目录 `.env` 是 Docker Compose 配置，不同于 `config/.env`，必须包含生产认证凭据。

```bash
cd /data2/person_path/yzzhang/weekly-report
cp .env.example .env
nano .env
chmod 600 .env
```

至少填写 `WEEKLY_HOST_PORT`、`MYSQL_ROOT_PASSWORD`、`WEEKLY_JWT_SECRET`、`WEEKLY_BOOTSTRAP_ADMIN_PASSWORD` 和实际免交规则 `WEEKLY_REPORT_EXEMPT_SUBMITTERS`，并保持 `WEEKLY_AUTH_DEVELOPMENT_MODE=false`。不得把真实值写回 `.env.example`。

周日未交提醒默认关闭。首次部署先保持 `WEEKLY_SUBMISSION_REMINDER_ENABLED=false`，完成下述预检和钉钉应用可见范围核对后再启用；完整说明见 `docs/docker_deploy.md`。

## 5. 安装 Codex skill

```bash
cd /data2/person_path/yzzhang/weekly-report
mkdir -p ~/.codex/skills
rm -rf ~/.codex/skills/weekly-report-assistant
cp -a codex-skills/weekly-report-assistant ~/.codex/skills/weekly-report-assistant
```

这样服务器 Codex 就可以使用 `$weekly-report-assistant`。周一可生成暂定结果，周四补交截止后应再次读取上一业务周数据并生成最终评价。

自动评价 Harness 会机械检查已安装 Skill 与仓库版本一致；每次拉取包含 Skill 变更的代码后都必须重新复制。完整的 `codex exec` 预检、隔离、变更指纹和 cron 配置见 `docs/server_codex_automation.md`。

## 6. Docker 启动

```bash
cd /data2/person_path/yzzhang/weekly-report
docker compose up -d --build
```

验证：

```bash
docker compose ps
docker compose logs -f --tail=100
curl http://127.0.0.1:22081/api/health
curl http://127.0.0.1:22081/api/weeks
```

提交概览中的“下载原周报”默认依赖新版采集生成的私有 `output/<周次>/raw/all_reports.json`。W28 是历史例外，可以直接使用既有 `raw/reports.json` 下载，不要求重新采集或检查旧模板是否完整；其他升级前已有周次仍需按原业务周期重新采集，缺少完整快照时接口会明确拒绝。

周日提醒预检只输出汇总人数，不发送消息或显示 userid：

```bash
docker compose exec weekly-report python3 /app/scripts/submission_reminder.py
```

预检应在本周周四 00:00 之后执行。确认模板、应交人数、免交人数、反馈通知接收人和应用可见范围后，在根 `.env` 设置 `WEEKLY_SUBMISSION_REMINDER_ENABLED=true`，再执行 `docker compose up -d --build --force-recreate weekly-report`。正常任务在周日 18:00 执行，成功或失败都会向配置的反馈接收人发送汇总结果。

周一个人评价通知同样默认关闭。它依赖当前版本 Skill 在同一次自动评价中生成的私有反馈清单，不会为每名员工单独调用 Codex。首次启用时，在根 `.env` 设置 `WEEKLY_EVALUATION_FEEDBACK_ENABLED=true`、周一 12:00 cron、上海时区和私有 HR 联系人；随后重新复制 Skill、执行一次 `./scripts/run_codex_evaluation.sh --force`，确认成功后再重建应用容器。完整配置和幂等说明见 `docs/docker_deploy.md`。

需要立即验证钉钉链路时，确保反馈接收人名称为张艺政且只配置一个对应 userid，使用 ADMIN 登录后进入“运行状态 → 自动通知试发”，分别发送周日提醒样例和周一评价样例。试发不读取真实周报、不写正式幂等状态；普通用户和 REPORT_ALL 账号无权操作。

访问地址：

```text
http://182.92.251.239:22081
```

## 7. 每次代码更新后的服务器操作

以后本地开发完成并推送到 Codeup 后，服务器执行：

```bash
cd /data2/person_path/yzzhang/weekly-report
git pull
docker compose up -d --build
curl http://127.0.0.1:22081/api/health
```

如果 Codex skill 也更新了，再同步一次：

```bash
cd /data2/person_path/yzzhang/weekly-report
rm -rf ~/.codex/skills/weekly-report-assistant
cp -a codex-skills/weekly-report-assistant ~/.codex/skills/weekly-report-assistant
```

## 8. 从旧 GitHub 目录迁移到 Codeup

如果服务器已有从 GitHub 克隆的旧目录，推荐保留备份后重新克隆 Codeup，避免 GitHub 网络问题影响部署。

```bash
cd /data2/person_path/yzzhang
cp weekly-report/config/.env /tmp/weekly-report-config.env
cp weekly-report/.env /tmp/weekly-report-compose.env 2>/dev/null || true
mv weekly-report weekly-report-github-backup-$(date +%Y%m%d-%H%M%S)
git clone https://codeup.aliyun.com/684beabd28a6beb51d765af2/weekly-report.git weekly-report
cp /tmp/weekly-report-config.env weekly-report/config/.env
cp /tmp/weekly-report-compose.env weekly-report/.env 2>/dev/null || true
cp -a weekly-report-github-backup-*/output weekly-report/output 2>/dev/null || true
cp -a weekly-report-github-backup-*/logs weekly-report/logs 2>/dev/null || true
cd weekly-report
docker compose up -d --build
curl http://127.0.0.1:22081/api/health
```

如果只是修改远程地址，也可以在原目录执行：

```bash
cd /data2/person_path/yzzhang/weekly-report
git remote set-url origin https://codeup.aliyun.com/684beabd28a6beb51d765af2/weekly-report.git
git pull
docker compose up -d --build
```

## 9. Codex 自动评价

优先执行：

```bash
cd /data2/person_path/yzzhang/weekly-report
chmod 700 scripts/run_codex_evaluation.sh
./scripts/run_codex_evaluation.sh --preflight
./scripts/run_codex_evaluation.sh --dry-run
./scripts/run_codex_evaluation.sh
```

确认首次输出正确后，按 `docs/server_codex_automation.md` 安装周一至周三的变更检查和周四最终评价 cron。Harness 会在没有新周报、附件或规则变化时跳过 Codex；有补交时完整重建并机械验证正式评价。

## 10. 人工回退提示词

```text
Use $weekly-report-assistant.
项目路径：/data2/person_path/yzzhang/weekly-report。
请自动拉取上一业务周钉钉周报；业务周按周一至周日命名，提交按周四至下一周周三归属。生成提交状态和分析输入包后，基于 output/<周次>/analysis/analysis_input.md 生成正式管理评价。先读取“团队负责人履职输入（确定性证据）”建立完整负责人清单；附件状态包含 attachments/team_leads/ 本地路径时，在当前周目录内读取并解析附件。下载或解析失败不得判为未提交或不合格，内容维度写“无法判断”并说明安全原因。
每个人评价直接使用最新维度替换旧字段：虚实盘（本周成果）、时间分配健康度、AI使用红黑榜、下周计划合格性、综合结论/需跟进。不要单独新增“筛选标准结论”板块。
请把风险/阻塞/求助信息中需要老板拍板/协调的事项单独置顶展示。注意：如果钉钉模板没有“风险与求助”字段，不要因为缺失该字段判定模板不合格。请把正式评价保存到 output/<周次>/summary/manager_report.md，供 Web 前端展示。不要输出 config/.env 或任何密钥。
```
