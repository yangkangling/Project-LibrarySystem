# Experiment Log

本文记录已经执行过的验证步骤，只写真实运行过或明确未运行的内容。时间以本地开发环境为准。

## 2026-08-20 后端回归测试

目的：

验证包名正式化、BCrypt、DTO 参数校验、并发库存测试和读者权限隔离测试没有破坏原有业务。

命令：

```powershell
.\mvnw.cmd test
```

结果：

```text
Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

覆盖点：

- `BookControllerStatusTest`：图书停用、馆藏上限、编辑时不允许前端覆盖库存。
- `BorrowControllerFineTest`：逾期罚款确认缴纳、冻结和解冻逻辑。
- `SelfServiceControllerSecurityTest`：读者端不能归还他人的借阅记录。
- `BorrowServiceConcurrencyTest`：两个读者并发借最后一本书，只允许一个成功。
- `StorageLocationServiceTest`：书架位置格式和 1-50 上限。

结论：

后端核心业务和新增安全/并发规则可以通过自动化测试复现，不只依赖手工点击页面。

## 2026-08-20 前端生产构建

目的：

确认 Vue 3 + Element Plus 前端源码可以构建，并且构建产物可以写入 Spring Boot 静态资源目录。

命令：

```powershell
cd frontend
npm run build:spring
```

结果：

```text
vite v6.4.3 building for production...
✓ built
```

说明：

构建过程中 Rollup 对第三方依赖中的 `/* #__PURE__ */` 注释给出提示，但构建成功。这是依赖包注释位置提示，不影响当前产物生成。

结论：

前端可以作为生产静态资源随 Spring Boot jar 一起发布。

## 2026-08-20 后端打包

目的：

确认 Maven 坐标从 `demo` 改为 `library-system` 后，最终 jar 文件名和启动入口正确。

命令：

```powershell
.\mvnw.cmd -DskipTests package
```

结果：

```text
Building jar: target\library-system-0.0.1-SNAPSHOT.jar
BUILD SUCCESS
```

结论：

项目正式化后的 Maven 坐标可正常打包，jar 名称已经变为 `library-system-0.0.1-SNAPSHOT.jar`。

## 2026-08-20 本地 jar 启动和接口冒烟

目的：

确认最新 jar 使用固定端口 `8080` 成功启动，并且首页、登录状态接口、扫码解析接口可访问。

命令：

```powershell
java -jar target\library-system-0.0.1-SNAPSHOT.jar
(Invoke-WebRequest -UseBasicParsing http://localhost:8080/).StatusCode
(Invoke-WebRequest -UseBasicParsing http://localhost:8080/auth/me).StatusCode
```

扫码解析接口验证：

```powershell
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
Invoke-WebRequest -UseBasicParsing -Method Post -Uri http://localhost:8080/auth/login -Body @{ username='admin'; password='123456' } -WebSession $session
(Invoke-WebRequest -UseBasicParsing -Uri 'http://localhost:8080/scan/resolve?code=978000000001' -WebSession $session).StatusCode
```

结果：

```text
首页：200
/auth/me：200
/scan/resolve?code=978000000001：200
```

结论：

当前 jar 可在本地 `8080` 启动，Session 登录和扫码/RFID 模拟解析接口可以完成基本访问。

## 尚未执行的实验

Docker Compose：

```bash
docker compose up --build
```

当前状态：

仓库已经提供 `Dockerfile`、`docker-compose.yml` 和 `.env.example`，但当前本地环境没有执行完整容器启动实验。README 中已经按“提供配置”描述，而不是宣称已完成容器实测。

GitHub Actions：

当前状态：

仓库已经提交 `.github/workflows/ci.yml`。远端 runner 的最终结果以 GitHub Actions 页面为准，本地不伪造 CI 通过结论。
