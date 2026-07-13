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

前端会自动展示这个文件。

周报采集免交名单也配置在服务器 `config/.env`：

```text
WEEKLY_REPORT_EXEMPT_SUBMITTERS=USERID:test-user-001;NAME:示例员工甲
```

优先使用稳定 userid；姓名仅作精确兼容匹配。免交人员及其提交会在 CSV、Markdown 和分析输入统计前统一排除，真实名单不得写入仓库。

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

## 周四截止自动化

周一可以用服务器 Codex 或 Web 按钮生成暂定结果；周四补交截止后应再次触发并生成最终结果。自动化可采用两种方式：

1. Spring Scheduler：Java 服务内部每周四自动执行 `scripts/run_weekly.py`。
2. Linux cron：每周四执行 `docker exec weekly-report python3 scripts/run_weekly.py`，或者给 Web 后端扩展专用任务 Token 后再调用接口。

cron 直接调脚本示例：

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
