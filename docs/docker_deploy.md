# Docker 部署说明

## 构建并启动

```bash
cd /data2/person_path/yzzhang/weekly-report
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

## 周一自动化

第一阶段建议仍然用服务器 Codex 或 Web 按钮触发周报拉取与总结。后续可以增加两种方式：

1. Spring Scheduler：Java 服务内部每周一自动执行 `scripts/run_weekly.py`。
2. Linux cron：每周一调用 Web 后端接口或 `docker exec weekly-report python3 scripts/run_weekly.py`。

cron 调 Web 接口示例：

```bash
0 9 * * 1 curl -X POST "http://127.0.0.1:8088/api/jobs/run?week=previous"
```

注意：正式 AI 评价仍建议由 Codex skill 生成并写入 `manager_report.md`，因为它需要模型完成管理评价。

## 资源占用

当前 Docker 部署的资源预估：

- 镜像大小约 `487 MB`。
- 容器运行内存通常约 `170-200 MiB`。
- Compose 已设置 `mem_limit: 768m`，Java 参数为 `-Xms128m -Xmx384m`。
- 首次构建会下载 Node/Maven 依赖，建议预留 `3-5 GB` 磁盘；构建完成后 Docker build cache 可能额外占用 `1-2 GB`。
- `output/` 会随着每周数据增长，通常每周从几十 KB 到数 MB 不等。

如果需要清理无用构建缓存：

```bash
docker builder prune
```

执行前确认当前没有正在构建的镜像。
