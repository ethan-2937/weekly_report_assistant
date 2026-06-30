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
请自动拉取上一周钉钉周报，生成提交状态和分析输入包，然后基于 output/<周次>/analysis/analysis_input.md 总结每个人做了哪些工作、效果如何、团队负责人是否履职，并列出未提交候选名单。
请按最新筛选标准重点输出：1）真实产出是否存在；2）工时占比是否健康；3）AI使用红黑榜，含【可复用】亮点；4）下周计划是否同时包含日期和产出，避免“继续”类空话；5）风险与求助中需要老板拍板/协调的事项，并置顶展示。
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
