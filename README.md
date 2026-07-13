# 周报汇总助手

## Codex / Harness 开发入口

在 Codex 中直接打开 `D:\weekly_report`。先阅读 `AGENTS.md` 和最近的子目录指南，统一验证命令为：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\verify.ps1
```

产品、架构、质量与隐私边界分别记录在 `docs/PRODUCT.md`、`docs/ARCHITECTURE.md`、`docs/QUALITY.md` 和 `docs/SECURITY_PRIVACY.md`。

本项目完成了一套从钉钉开放平台到 AI 周报分析的端到端链路：

- 打通钉钉企业内部应用，使用 `Client ID / Client Secret` 获取 `accessToken`。
- 接入钉钉通讯录 API，自动拉取授权范围内的部门、人员、负责人候选信息。
- 接入钉钉日志/周报 API，按周报模板自动拉取指定周期内的员工周报。
- 基于 `userid` 做人员和周报匹配，避免重名导致统计错误。
- 自动生成提交状态表、未提交候选名单、团队负责人候选名单和 AI 分析输入包。
- 编写 `weekly-report-assistant` Codex skill，让服务器上的智能体可以按固定提示词自动执行周报拉取、总结和评价。
- 设计了本地 Windows 验证、Ubuntu 服务器部署、后续定时任务和权限隔离的迁移方案。

换句话说，这套系统已经具备“每周一自动分析上一周周报”的基础能力：先由脚本稳定采集数据，再由 Codex skill 负责总结、评价、发现风险和输出管理视角结论。

## 系统逻辑

整体链路如下：

```text
钉钉企业内部应用
  ↓ 获取 accessToken
钉钉通讯录 API
  ↓ 拉取部门、成员、负责人候选
钉钉日志/周报 API
  ↓ 拉取上一周正式周报模板
本地/服务器脚本
  ↓ userid 匹配、提交状态统计、数据标准化
Codex weekly-report-assistant skill
  ↓ 总结每个人工作、评价效果、检查负责人履职
管理输出
  ↓ 未提交名单、个人评价、团队负责人履职检查、共性风险
```

## 项目亮点

- **自动化**：从“人工下载周报”升级为“API 自动拉取 + 一键生成分析材料”。
- **可验证**：所有统计都基于钉钉 `userid`，比按姓名匹配更可靠。
- **可迁移**：脚本不依赖桌面钉钉客户端，可部署到 Ubuntu 服务器。
- **智能体化**：通过 Codex skill 固化工作流，后续只需要一句提示词即可触发分析。
- **管理视角**：不仅统计谁交了，还按最新筛选标准检查真实产出、工时健康度、AI 红黑榜、下周计划质量、风险求助和团队负责人额外职责。
- **安全边界**：`.env`、原始输出、密钥和敏感数据均默认排除提交，后续可扩展分权限报告。

## 周期规则

公司每周一总结周报，因此脚本默认分析“上一完整周”：

- 默认：上一周周一 00:00 到上一周周日 23:59:59，Asia/Shanghai。
- 测试当前周：加 `--week current`。
- 指定日期：加 `--start YYYY-MM-DD --end YYYY-MM-DD`。

## 本地运行

1. 复制配置模板：

   ```powershell
   Copy-Item config\.env.example config\.env
   ```

2. 编辑 `config\.env`，填写 `DINGTALK_APP_SECRET`，并确认 `DINGTALK_REPORT_TEMPLATE` 是钉钉里的真实周报模板名。

3. 测试 access token：

   ```powershell
   python scripts\test_token.py
   ```

4. 一键拉取并生成分析材料：

   ```powershell
   python scripts\run_weekly.py
   ```

5. 本地测试当前周：

   ```powershell
   python scripts\run_weekly.py --week current
   ```

## 输出

```text
output/
  contacts/
    users.json
    departments.json
  <YYYY-Www>/
    raw/reports.json
    exports/submission_status.csv
    summary/submission_check.md
    summary/manager_report.md
    analysis/analysis_input.md
```

## Web 界面

Web 推荐使用 Java + Vue 实现：

- Spring Boot 后端读取 `output/<周次>` 下的 CSV/Markdown/JSON 文件，并可手动触发 `scripts/run_weekly.py`。
- 后端采用传统分层结构：`controller`、`service`、`service.impl`、`mapper`、`po`、`vo`、`config`、`common`、`util`。
- Vue 前端沿用 `D:\BookStore\VueFronted` 的卡片、胶囊导航、柔和渐变和 Element Plus 表格风格。
- Codex skill 每周一生成正式评价后，写入 `output/<周次>/summary/manager_report.md`，前端会自动展示。

## 登录与权限

当前版本已接入 MySQL + JWT：

- 首次启动会自动创建认证相关表：`sys_user`、`sys_role`、`sys_user_role`、`sys_dept_scope`、`sys_login_log`。
- 首次启动会预置管理员账号：`admin / admin123`，密码入库时会转成 BCrypt 哈希；`ADMIN` 可以管理账号，也可以查看完整周报。
- 首次启动会预置 4 个“全部周报权限”账号：`wangkai`、`zhanyi`、`pengweijuan`、`sunxiaoming`。初始密码按“名拼音首字母 + 姓拼音 + @kingdomai.com”的规则生成，例如 `pengweijuan / wjpeng@kingdomai.com`。
- 用户登录后可在右上角“修改密码”中自行修改初始密码；管理员也可以在“用户管理”中重置密码。
- 前端先支持用户名密码登录，并保留“钉钉登录”按钮。
- 方案 B 数据权限已启用：`ADMIN` / `REPORT_ALL` 查看完整周报；其他账号可通过 `sys_dept_scope` 的部门、人员或 `userid` 范围查看授权范围内周报。
- `/api/weeks/**` 和 `/api/files/**` 在服务层按当前用户权限过滤提交概览、未交名单、AI 评价和 CSV 下载；`/api/jobs/**` 仍仅允许 `ADMIN` / `REPORT_ALL` 触发或查看采集任务。
- `ADMIN` 登录后可以进入“用户管理”，新建账号、分配角色、启停账号、绑定钉钉 `userId/unionId`、重置密码，并配置部门/人员权限范围。
- 钉钉登录由钉钉证明身份，本系统仍通过 `sys_user.ding_user_id` 或 `sys_user.ding_union_id` 判断是否允许进入系统。
- 登录后右上角下拉框提供“提出 bug 或建议”，面向领导使用时只需填写一段反馈内容；系统会自动附带账号、周次、页面信息，记录到 `logs/feedback-YYYY-MM.jsonl`，并优先通过钉钉工作通知直达张艺政。

部门权限范围写法：

```text
ALL
DEPT:信用业务线
DEPT:财务
USER:李晓玲
USERID:02485155366824463653
```

也可以直接填写普通文本，系统会自动尝试按部门、姓名或 `userid` 匹配；为避免误匹配，正式配置推荐使用 `DEPT:`、`USER:`、`USERID:` 前缀。

钉钉登录启用方式：

1. 在钉钉开发者后台把登录回调地址配置为 `http(s)://你的域名或IP/api/auth/dingtalk/callback`。
2. 在服务器根目录 `.env` 中设置 `WEEKLY_DINGTALK_LOGIN_ENABLED=true`、`WEEKLY_DINGTALK_CLIENT_ID`、`WEEKLY_DINGTALK_CLIENT_SECRET`、`WEEKLY_DINGTALK_REDIRECT_URI`、`WEEKLY_FRONTEND_URL`。
3. 用户首次钉钉登录时，系统会先按 `ding_user_id/union_id` 查找本地账号；如果未绑定，则只在钉钉姓名与本地唯一启用账号姓名完全一致时自动绑定，管理员账号仍需手动绑定。
4. 如果姓名重复或本地账号不存在，登录会失败，需要管理员在“用户管理”里手动填写钉钉 `userId/unionId`。

反馈通知启用方式：

1. 在 `.env` 或 `config/.env` 中配置 `WEEKLY_FEEDBACK_DINGTALK_USER_IDS`（或 `FEEDBACK_DINGTALK_USER_IDS`），值为张艺政的钉钉 `userid`，多个接收人用逗号分隔。
2. 如果 `.env` 未单独填写 `WEEKLY_FEEDBACK_DINGTALK_APP_KEY`、`WEEKLY_FEEDBACK_DINGTALK_APP_SECRET`、`WEEKLY_FEEDBACK_DINGTALK_AGENT_ID`，后端会回退读取 `config/.env` 里的 `DINGTALK_APP_KEY`、`DINGTALK_APP_SECRET`、`DINGTALK_AGENT_ID`。
3. 如果接收人 `userid` 未配置，系统会尝试从本地用户绑定或 `output/contacts/users.json` 中按姓名查找；仍找不到时，前端会提示复制反馈内容并手动发给张艺政。

本地直连 MySQL 时，默认连接：

```text
jdbc:mysql://localhost:3306/weekly_report
username: root
password: 空
```

如需修改，在启动 Java 服务前设置：

```powershell
$env:SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/weekly_report?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
$env:SPRING_DATASOURCE_USERNAME="root"
$env:SPRING_DATASOURCE_PASSWORD="你的密码"
$env:WEEKLY_JWT_SECRET="至少32字节的正式JWT密钥"
```

正式使用前建议修改 `admin` 密码，并把 `WEEKLY_JWT_SECRET`、MySQL 密码写到服务器根目录 `.env`，不要提交真实密钥。

本地启动：

```powershell
cd web\frontend
npm install
npm run build

cd ..\backend-spring
java -jar target\weekly-report-backend-1.0.0.jar
```

打开：

```text
http://127.0.0.1:8088
```

Docker 部署：

```bash
cp .env.example .env
# 按需修改 .env 里的 MYSQL_ROOT_PASSWORD、WEEKLY_JWT_SECRET、WEEKLY_HOST_PORT
docker compose up -d --build
```

详见 `docs/docker_deploy.md`。

## 安全约定

- `config/.env` 已被 `.gitignore` 忽略，不要提交到代码仓库。
- 不要在日志或聊天中粘贴 `DINGTALK_APP_SECRET`。
- 原始数据和汇总输出默认写到 `output/`，该目录也不会提交。
- 如果要给不同管理者分发报告，先基于 `submission_status.csv` 和权限配置生成分权限版本。

## Docker 资源预估

本地 Docker Desktop 和服务器 Docker 都可以运行这套系统。当前版本的资源占用大致如下：

- Docker 镜像约 `487 MB`。
- Java 容器稳定运行内存约 `170-220 MiB`，MySQL 容器通常约 `300-600 MiB`，视数据量和 MySQL 参数浮动。
- `docker-compose.yml` 已限制 Java 参数为 `-Xms128m -Xmx384m`，并设置 `mem_limit: 768m`，适合普通 Ubuntu 服务器长期运行。
- 加上 MySQL 后，建议服务器预留 `2 CPU / 3 GB RAM / 8 GB 磁盘`；如果还要在服务器上用 Codex 生成 AI 评价，建议至少 `4 GB RAM`。

## 云效 Codeup 代码仓库与服务器部署

公司后续以云效 Codeup 作为主仓库，仓库地址为：

```text
https://codeup.aliyun.com/684beabd28a6beb51d765af2/weekly-report.git
```

GitHub 可以继续作为个人备份，但服务器部署、团队协作和后续更新默认都从 Codeup 拉取。Ubuntu 服务器部署和更新流程见 `docs/codeup_server_deploy.md`。

Codex skill 位于 `codex-skills/weekly-report-assistant`，部署到服务器后复制到 `~/.codex/skills/weekly-report-assistant` 即可让服务器 Codex 识别。

## 无 Docker 备用启动

如果服务器账号没有 `sudo` 或 Docker 权限，可以使用脚本直接启动 Java 后端：

```bash
bash scripts/server_start_no_docker.sh
```

停止服务：

```bash
bash scripts/server_stop_no_docker.sh
```

该方式依赖服务器已经安装 `java`、`mvn`、`node`、`npm`、`python3`。

## 访问地址和端口

运维当前建议使用 `22080-22099` 这一批端口。当前服务器已验证 `22081` 可用：

```text
http://182.92.251.239:22081
```

Docker 容器内部仍监听 Spring Boot 的 `8088`，外部端口通过根目录 `.env` 或命令行变量控制：

```bash
WEEKLY_HOST_PORT=22081 docker compose up -d --build
```

如果后续换端口，只需要修改根目录 `.env` 中的 `WEEKLY_HOST_PORT`，然后重新执行 `docker compose up -d --build`。
