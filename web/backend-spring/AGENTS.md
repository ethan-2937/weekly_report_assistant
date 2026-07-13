# Spring Backend Guide

## 当前入口

这是唯一活动后端。`../backend/` 是历史实现，不在其中增加功能。

## 边界

- `controller/`：HTTP、校验和响应转换。
- `service/`：业务接口。
- `service/impl/`：周报读取、权限、任务和反馈编排。
- `mapper/`：文件或数据库访问。
- `security/`：身份解析和 JWT。
- `config/`：配置绑定、路径与安全装配。
- `po/` 和 `vo/`：持久化与 API 对象，不把 PO 直接暴露给前端。

## 规则

- Controller 不直接依赖 Mapper 或 ServiceImpl。
- 周报内容过滤必须在服务层执行，下载接口同样受权限控制。
- 管理员角色不自动等于完整周报权限；以代码中的明确角色规则为准。
- 进程调用 Python 时使用参数列表、受控路径、超时和可审计错误，不拼接 shell 命令。
- 默认单元测试不得要求 MySQL、钉钉或真实文件树。
- 修改登录、部门范围、下载或反馈通知时覆盖拒绝路径，避免只测试成功路径。

## 命令

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
```
