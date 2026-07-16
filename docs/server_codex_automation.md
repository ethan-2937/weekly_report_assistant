# 服务器 Codex 自动评价 Harness

## 目标

服务器不再依赖人工粘贴长提示词。Linux cron 定期调用 `scripts/run_codex_evaluation.sh`：每次先刷新上一业务周数据，再比较分析包、提交 CSV、负责人附件、Skill 和提示词的 SHA-256 指纹。只有输入变化、正式报告缺失或报告校验失败时才调用 Codex。

周一至周三的补交会改变指纹并触发完整重评；周四 00:00 补交窗口关闭后再运行一次形成最终评价。完整重建比给旧 Markdown 打局部补丁更可靠，可以同步修正提交统计、员工评价、负责人履职和管理摘要。

官方 Codex CLI 使用 `codex exec` 执行非交互任务，支持临时会话、显式沙箱和结构化输出。CLI 本身不提供 Scheduled 管理界面，因此服务器使用 cron 触发仓库内 Harness：

- [Codex non-interactive mode](https://learn.chatgpt.com/docs/non-interactive-mode.md)
- [Codex scheduled tasks](https://learn.chatgpt.com/docs/automations.md)

## 安全边界

Harness 不让 Codex 直接在服务器项目目录中工作。每次运行会创建权限受限的临时目录，只复制：

- 当前目标周 `analysis_input.md` 和 `submission_status.csv`；
- 当前周负责人附件；
- 周报模板、负责人职责、Skill 和输出格式快照；
- 自动评价提示词与 JSON Schema。

不会复制 `.env`、`config/.env`、原始 API JSON、其他周、旧评价、Git 历史或日志。传给 Codex 的进程环境会移除 DingTalk、MySQL、JWT、反馈、免交名单、通用 token/password/secret/API key；仅保留 Codex 自己的 `CODEX_API_KEY`（如使用）或已有登录会话。

Codex 使用：

```text
--ephemeral
--sandbox workspace-write
--ask-for-approval never
--skip-git-repo-check
--ignore-user-config
--ignore-rules
--output-schema harness/weekly-report-evaluation/output-schema.json
-c 'sandbox_workspace_write.network_access=false'
-c 'web_search="disabled"'
```

Harness 会根据 `codex exec --help` 自动选择审批参数：新 CLI 使用 `--ask-for-approval never`，不支持该参数的旧 CLI 使用兼容的 `--full-auto`。两种模式都限制在临时工作区，且不会授权生产目录写入。

`workspace-write` 只用于临时隔离目录内的附件解析中间文件，不授权生产目录写入；正式报告由 Python 校验后原子替换。模型失败、超时、输出不完整或泄露内部标识时，上一次有效 `manager_report.md` 保持不变。

## 前置条件

1. 宿主机安装并登录 Codex CLI，运行用户与 cron 用户必须相同。
2. `codex exec --help` 必须支持 `--ephemeral`、`--output-schema`、`--sandbox`、`--ignore-rules`，以及 `--ask-for-approval`、`--full-auto` 或 `-c/--config` 之一。
3. 同步仓库 Skill 到 `~/.codex/skills/weekly-report-assistant`；Harness 会比较 `SKILL.md`，过期时拒绝运行。
4. Docker 服务已启动；推荐让 Harness 通过容器完成钉钉采集，Codex 本身仍运行在宿主机。
5. `output/`、`logs/` 和项目目录对运行用户可写，临时目录应有足够空间容纳当前周附件。

不要把 Codex 登录文件、`auth.json`、API key 或真实业务凭据复制进仓库。`codex exec` 默认复用当前用户已有登录；若必须使用 API key，只在 cron 的私有运行环境中提供 `CODEX_API_KEY`。

## 配置

在服务器私有 `config/.env` 中配置非敏感 Harness 参数：

```text
WEEKLY_CODEX_COLLECTION_MODE=docker
WEEKLY_CODEX_BIN=codex
# Optional approved HTTPS proxy; set this when Codex cannot reach its service directly.
WEEKLY_CODEX_BASE_URL=https://proxy.example.invalid/openai
WEEKLY_CODEX_MODEL=
WEEKLY_CODEX_REASONING_EFFORT=high
WEEKLY_CODEX_COLLECTION_TIMEOUT_SECONDS=600
WEEKLY_CODEX_TIMEOUT_SECONDS=2700
WEEKLY_CODEX_MAX_ATTEMPTS_PER_INPUT=3
```

`WEEKLY_CODEX_MODEL` 留空时使用该 Codex CLI 的可用默认模型，避免仓库硬编码未来可能不可用的型号。`high` 推理强度用于提高评价一致性；如果额度或耗时压力较大，可在验证效果后改为 `medium`。

`WEEKLY_CODEX_BASE_URL` 只应写入服务器私有 `config/.env`，必须是 HTTPS 地址；配置该地址后，Harness 会显式传入公司 `crs` provider、`responses` 协议和 `CRS_OAI_KEY` 环境变量名，并继续忽略其他用户配置，避免代理配置之外的本地规则改变自动任务行为。

## 安装与预检

更新代码并同步 Skill：

```bash
cd /data2/person_path/yzzhang/weekly-report
git pull
mkdir -p ~/.codex/skills
mkdir -p logs
rm -rf ~/.codex/skills/weekly-report-assistant
cp -a codex-skills/weekly-report-assistant ~/.codex/skills/weekly-report-assistant
chmod 700 scripts/run_codex_evaluation.sh
```

只检查 CLI 和 Skill，不读取周报或调用钉钉：

```bash
./scripts/run_codex_evaluation.sh --preflight
```

刷新上一业务周并判断是否需要运行 Codex，但不调用模型：

```bash
./scripts/run_codex_evaluation.sh --dry-run
```

首次人工执行：

```bash
./scripts/run_codex_evaluation.sh
```

如需重新采集和评价已经结束的历史周次，显式传入 ISO 周次；例如重新生成 W28：

```bash
./scripts/run_codex_evaluation.sh --week-label 2026-W28 --dry-run
./scripts/run_codex_evaluation.sh --week-label 2026-W28
```

不传 `--week-label` 时仍按上一业务周运行，适合 cron。指定周次会将采集参数、输入目录、提示词和正式报告统一锁定到该周，不会自动读取当前的 W29 或其他周次。

正常输出只包含周次、应交/已交/未交人数、附件数量和状态，不包含姓名、userid、正文或模型原始输出。

## Cron

使用项目绝对路径，确保 cron 能找到 `docker`、`codex` 和 `python3`。先运行 `command -v codex`，如果不在 cron 的默认 `PATH`，把绝对路径写入 `WEEKLY_CODEX_BIN`。

```cron
SHELL=/bin/bash
PATH=/usr/local/bin:/usr/bin:/bin

# 周一至周三 08:17-22:17 每两小时刷新；没有变化时只采集并跳过 Codex。
17 8-22/2 * * 1-3 cd /data2/person_path/yzzhang/weekly-report && ./scripts/run_codex_evaluation.sh >> logs/codex-evaluation.log 2>&1

# 周四补交窗口关闭后生成最终评价。
17 9 * * 4 cd /data2/person_path/yzzhang/weekly-report && ./scripts/run_codex_evaluation.sh >> logs/codex-evaluation.log 2>&1
```

先观察至少一个完整周，再根据提交习惯降低频率。脚本自带原子锁；并发 cron 会返回 `RUN_ALREADY_ACTIVE`，不会同时运行两个 Codex。

## 状态与校验

状态写入忽略目录：

```text
output/<YYYY-Www>/automation/evaluation_state.json
```

只包含输入/报告散列、任务状态、尝试次数、时间、汇总人数和安全错误码。相同输入最多自动尝试 3 次；新补交、附件或规则变化会产生新指纹并重置次数。`--force` 可以人工忽略成功指纹和重试上限，但应先查清失败原因。

Codex 输出必须通过以下检查才会替换正式报告：

- 七个固定模块和正确顺序；
- 五个员工评价维度；
- CSV 中每位应交人员均出现；
- 确定性负责人清单全部出现在履职模块；
- 周次匹配，长度合理；
- 不包含 userid、unionId、fileId、spaceId、token、JWT 或 Bearer 凭据形态。
- 私有员工反馈的 userid 集合与已提交集合完全一致，反馈 prose 不包含姓名、标识、同事信息、URL、secret、路径或附件元数据。

正式输出路径保持不变：

```text
output/<YYYY-Www>/summary/manager_report.md
output/<YYYY-Www>/automation/employee_feedback.json
```

第二个文件是周一私人评价通知的受限输入，不进入 Web API；它与正式报告在同一次 Codex 调用中生成，并带输入和报告摘要供 Spring 再校验。更新本能力后必须重新复制 Skill，并对目标周重新运行一次评价；只有旧 `manager_report.md` 不足以发送员工通知。

常见安全错误码包括 `CODEX_SKILL_OUTDATED`、`COLLECTION_FAILED`、`CODEX_EXEC_FAILED`、`CODEX_OUTPUT_BLOCKED`、`CODEX_OUTPUT_WRONG_WEEK`、`CODEX_OUTPUT_STATUS_INVALID`、`ROSTER_COVERAGE_INCOMPLETE`、`EMPLOYEE_FEEDBACK_COVERAGE_INVALID`、`EMPLOYEE_FEEDBACK_SECRET_EXPOSED`、`LEADER_COVERAGE_INCOMPLETE`、`USERID_EXPOSED` 和 `RUN_ALREADY_ACTIVE`。其中输出错误码只记录结构化状态，不打印模型输出、警告原文、员工姓名或钉钉响应。
