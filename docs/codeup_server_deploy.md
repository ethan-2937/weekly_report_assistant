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
DINGTALK_REPORT_TEMPLATE=钉钉周报模板名称
OUTPUT_ROOT=output
```

## 4. 配置服务端口

服务器运维当前开放 `22080-22099`，本项目已验证 `22081` 可用。根目录 `.env` 是 Docker Compose 配置，不同于 `config/.env`。

```bash
cd /data2/person_path/yzzhang/weekly-report
cat > .env <<'EOF'
WEEKLY_HOST_PORT=22081
EOF
```

## 5. 安装 Codex skill

```bash
cd /data2/person_path/yzzhang/weekly-report
mkdir -p ~/.codex/skills
rm -rf ~/.codex/skills/weekly-report-assistant
cp -a codex-skills/weekly-report-assistant ~/.codex/skills/weekly-report-assistant
```

这样服务器 Codex 就可以使用 `$weekly-report-assistant`，每周一读取上一周数据并生成正式评价。

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

## 9. 周一正式运行提示词

```text
Use $weekly-report-assistant.
项目路径：/data2/person_path/yzzhang/weekly-report。
请自动拉取上一周钉钉周报，生成提交状态和分析输入包，然后基于 output/<周次>/analysis/analysis_input.md 总结每个人做了哪些工作、效果如何、团队负责人是否履职，并列出未提交候选名单。请把正式评价保存到 output/<周次>/summary/manager_report.md，供 Web 前端展示。不要输出 config/.env 或任何密钥。
```
