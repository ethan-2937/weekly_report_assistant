# Codex 项目工作约定

本文件记录本项目和 Codex 协作时的默认操作，避免后续遗忘。

## 默认仓库

- 本地项目路径：`D:\weekly_report`
- 公司主仓库：云效 Codeup，remote 名称为 `origin`，推送到 `master`
- 个人备份仓库：GitHub，remote 名称为 `github`，推送到 `main`
- 服务器部署只从云效 Codeup 拉取最新代码。

## 默认提交与推送规则

以后 Codex 在本项目中完成代码、脚本、文档、skill 或配置说明变更后，默认执行：

```powershell
git status
git add -A
git commit -m "简洁准确的提交说明"
git pushall
```

`git pushall` 已配置为同时推送：

```powershell
git push origin HEAD:master
git push github HEAD:main
```

除非遇到明显风险，否则不需要再额外询问“是否提交并推送”。

## 安全例外

以下情况不自动提交，Codex 需要先说明原因：

- 发现疑似用户临时改动或与当前任务无关的文件变更。
- 发现 `.env`、token、secret、员工隐私原始数据、HR/财务敏感文件可能被误加入提交。
- 构建或语法校验失败，且失败会影响部署。
- Git 分支、远程仓库或合并状态异常。

## 常用验证

- 前端变更：`cd web/frontend && npm run build`
- Python 脚本变更：`python -m py_compile scripts/run_weekly.py`
- Docker/服务器部署前：确认 `docker compose up -d --build` 可执行。

## 服务器更新流程

服务器目录：`/data2/person_path/yzzhang/weekly-report`

```bash
cd /data2/person_path/yzzhang/weekly-report
git pull
docker compose up -d --build
curl http://127.0.0.1:22081/api/health
```

如果更新了 Codex skill，还需要同步服务器 skill：

```bash
rm -rf ~/.codex/skills/weekly-report-assistant
cp -a codex-skills/weekly-report-assistant ~/.codex/skills/weekly-report-assistant
```
