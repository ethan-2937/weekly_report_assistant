# Ubuntu 部署说明

目标路径：

```bash
/data2/person_path/yzzhang/weekly-report
```

## 目录

```text
weekly-report/
  config/
    .env
    .env.example
  scripts/
    run_weekly.py
    test_token.py
    download_contacts.py
    download_reports.py
    dingtalk_common.py
  output/
  logs/
```

## 首次验证

```bash
cd /data2/person_path/yzzhang/weekly-report
python3 --version
python3 scripts/test_token.py
python3 scripts/run_weekly.py --week current
```

## Web 界面

```bash
cd /data2/person_path/yzzhang/weekly-report/web/frontend
npm install
npm run build

cd ../backend-spring
mvn -DskipTests package
export WEEKLY_AUTH_DEVELOPMENT_MODE=false
export WEEKLY_JWT_SECRET='<由部署平台注入>'
export WEEKLY_BOOTSTRAP_ADMIN_PASSWORD='<由部署平台注入>'
java -jar target/weekly-report-backend-1.0.0.jar
```

默认访问：

```text
http://服务器IP:8088
```

如果要换端口：

```bash
export WEEKLY_REPORT_PORT=8090
java -jar target/weekly-report-backend-1.0.0.jar
```

仅本地开发可显式设置 `WEEKLY_AUTH_DEVELOPMENT_MODE=true` 使用开发默认值；服务器不得启用。生产值缺失、为空或仍为开发默认值时 Spring 会拒绝启动，错误只指出需要配置的环境变量。

更推荐 Docker 部署，见 `docs/docker_deploy.md`：

```bash
cd /data2/person_path/yzzhang/weekly-report
docker compose up -d --build
```

## 周一正式运行

不加参数，默认分析上一周：

```bash
cd /data2/person_path/yzzhang/weekly-report
python3 scripts/run_weekly.py
```

## Codex 提示词

```text
Use $weekly-report-assistant in /data2/person_path/yzzhang/weekly-report.
请自动拉取上一周钉钉周报，生成提交状态和分析输入包，然后基于 output/<周次>/analysis/analysis_input.md 生成正式管理评价。
每个人评价直接使用最新维度替换旧字段：虚实盘（本周成果）、时间分配健康度、AI使用红黑榜、下周计划合格性、综合结论/需跟进。不要单独新增“筛选标准结论”板块。
请把风险/阻塞/求助信息中需要老板拍板/协调的事项单独置顶展示。注意：如果钉钉模板没有“风险与求助”字段，不要因为缺失该字段判定模板不合格。
请把正式评价保存到 output/<周次>/summary/manager_report.md，供 Web 前端展示。不要输出 config/.env 或任何密钥。
```

## Cron 示例

每周一 09:00 自动拉取上一周数据：

```bash
0 9 * * 1 cd /data2/person_path/yzzhang/weekly-report && python3 scripts/run_weekly.py >> logs/weekly.log 2>&1
```

## Skill 安装位置

服务器 Codex 自动发现路径：

```bash
~/.codex/skills/weekly-report-assistant
```

如果 Codex 没识别新 skill，重启 Codex 或开启新线程。
