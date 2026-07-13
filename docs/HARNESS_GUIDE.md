# 在 Codex 中开发本项目

## 标准任务

1. 直接打开 `D:\weekly_report`。
2. 给出一个可观察结果和明确范围。
3. 让 Codex 阅读根目录与最近的 `AGENTS.md`。
4. 非简单任务建立 `docs/tasks/` 任务文件。
5. 要求测试覆盖权限、隐私和失败路径。
6. 运行 `scripts/verify.ps1` 后再交付。

可复用提示：

```text
请实现 <结果>。先阅读 AGENTS.md、docs/PRODUCT.md、docs/ARCHITECTURE.md、
docs/SECURITY_PRIVACY.md 和最近的子目录 AGENTS.md。保留无关改动，
不读取真实 output/logs/config/.env。添加不访问外部服务的回归测试，
运行 scripts/verify.ps1，并报告行为、权限/隐私影响、验证和剩余风险。
```

## 让 Harness 变强

当智能体出错时，优先问“缺少哪条仓库上下文或机械反馈”。重复出现的问题应沉淀为测试、结构检查、受保护哈希或短文档，而不是继续加长每次任务提示。
