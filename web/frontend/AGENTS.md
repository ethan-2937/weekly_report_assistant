# Vue Frontend Guide

## 当前状态

`src/App.vue` 和 `src/styles.css` 是已知迁移热点，预算记录在 `harness/quality-baseline.json`。预算是上限，不是设计目标。

认证与 API 第一切片已经位于 `src/api/`、`src/composables/useAuth.js` 和 `src/features/auth/LoginView.vue`；不要把 fetch、token 或登录表单逻辑搬回 `App.vue`。

## 目标结构

- `src/features/<feature>/`：登录、提交概览、缺交、AI评价、任务、用户管理。
- `src/components/`：无业务 API 的通用展示组件。
- `src/api/`：认证、错误处理和接口模块。
- `src/styles/`：tokens、shell 与功能样式。
- `App.vue`：应用壳、路由状态和顶层组合。

## 规则

- 新功能不继续堆入 `App.vue`；触碰相关区域时渐进抽取。
- 不在组件中复制认证请求逻辑；统一处理 401/403 和 token 清理。
- 前端隐藏按钮不是权限边界，权限仍由后端强制执行。
- AI评价页可宽屏，普通页面保持较窄布局；保持现有正式、克制的视觉语言。
- 不在 UI、日志或错误信息中展示 token、员工原始周报或内部配置。
- 新增 API 或认证行为时使用 mock fetch 补充 Vitest；测试不得访问真实网络，也不得在快照中保存 token 或密码。
- 保持移动端可用，构建后检查关键页面状态。

## 命令

```powershell
npm ci
npm test
npm run build
```
