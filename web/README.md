# 周报 Web 界面

Web 使用 Spring Boot + Vue 3 + Element Plus。旧的 `web/backend` 是早期简易 Java HTTP Server 版本，当前推荐使用 `web/backend-spring`。

## 后端分层

```text
web/backend-spring/src/main/java/com/yzzhang/weeklyreport/
  controller/       # REST API 和前端入口
  service/          # 业务接口
  service/impl/     # 业务实现
  mapper/           # 文件/CSV 数据访问，后续可替换为 MyBatis/数据库
  po/               # 持久化对象/文件读取对象
  vo/               # 前端返回对象
  config/           # 路径、跨域、静态资源配置
  common/           # 异常和统一处理
  util/             # CSV、周次等工具
```

## 本地启动

1. 构建前端：

   ```powershell
   cd web\frontend
   npm install
   npm run build
   ```

2. 编译 Spring Boot 后端：

   ```powershell
   cd ..\backend-spring
   mvn -DskipTests package
   ```

   如果本机没有 Maven，可以用 Docker 构建，或临时下载 Maven。

3. 启动 Spring Boot 后端：

   ```powershell
   java -jar target\weekly-report-backend-1.0.0.jar
   ```

4. 打开：

   ```text
   http://127.0.0.1:8088
   ```

## Ubuntu 启动

```bash
cd /data2/person_path/yzzhang/weekly-report/web/frontend
npm install
npm run build

cd ../backend-spring
mvn -DskipTests package
java -jar target/weekly-report-backend-1.0.0.jar
```

也可以直接使用 Docker：

```bash
cd /data2/person_path/yzzhang/weekly-report
docker compose up -d --build
```

## 评价文件约定

Codex skill 每周一生成正式评价后，请保存到：

```text
output/<YYYY-Www>/summary/manager_report.md
```

前端“AI 周报评价”页面会优先展示该文件；如果文件不存在，则展示 `analysis_input.md` 并提示等待 Codex 生成正式评价。
