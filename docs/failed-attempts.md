# Failed Attempts

本文记录开发中真实遇到的失败和修复。保留这些记录的目的，是让项目呈现出可追溯的工程过程，而不是看起来像一次性生成的成品。

## 1. 打包失败：jar 被正在运行的 8080 服务占用

现象：

```text
Unable to rename 'target\demo-0.0.1-SNAPSHOT.jar'
to 'target\demo-0.0.1-SNAPSHOT.jar.original'
```

原因：

Windows 下运行中的 Spring Boot 进程会占用 jar 文件。旧服务仍在 `8080` 上运行，Maven repackage 阶段需要重命名 jar，所以失败。

修复：

先停止旧的 `java -jar` 进程，再执行：

```powershell
.\mvnw.cmd -DskipTests package
```

结果：

打包成功。后续在重新打包前会先确认 8080 旧进程是否还在。

## 2. 包名迁移后测试仍加载旧 `com.example.demo`

现象：

`.\mvnw.cmd test` 中出现旧类名：

```text
Failed to introspect Class [com.example.demo.controller.StorageLocationController]
ClassNotFoundException: com.example.demo.repository.ShelfRepository
```

原因：

源码已经从 `com.example.demo` 迁移到 `com.yangkangling.library`，但 `target/classes` 中还残留上一次编译产生的旧类。普通 `mvn test` 没有完全清掉这些旧产物。

修复：

执行：

```powershell
.\mvnw.cmd clean test
```

结果：

旧类残留被清理，Spring 上下文重新从 `com.yangkangling.library.DemoApplication` 启动。

## 3. Spring Boot 测试构造器注入失败

现象：

新增 `BorrowServiceConcurrencyTest` 后出现：

```text
No ParameterResolver registered for parameter BorrowService
```

原因：

JUnit 5 默认不会把测试类构造器参数当作 Spring Bean 注入，除非通过 Spring 测试扩展明确启用。

修复：

在测试类构造器上增加：

```java
@Autowired
BorrowServiceConcurrencyTest(...)
```

结果：

Spring 能正确注入 `BorrowService`、Repository 和 `TransactionTemplate`。

## 4. 并发测试清理阶段没有事务

现象：

并发借书测试业务断言已经通过，但 `@AfterEach` 清理数据时报错：

```text
No EntityManager with actual transaction available for current thread
```

原因：

`bookCopyRepository.deleteByBookId`、`storageLocationRepository.deleteByBookId` 这类派生删除方法需要事务。生命周期方法上直接加事务没有按预期包住删除操作。

修复：

使用 `TransactionTemplate` 显式包裹清理逻辑：

```java
transactionTemplate.executeWithoutResult(status -> {
    ...
});
```

结果：

清理逻辑稳定，`.\mvnw.cmd test` 最终通过 13 个测试。

## 5. SQL 初始化脚本残留旧字段

现象：

检查 `sql/init.sql` 时发现 `users` 表结构已删除 `account_balance`，但演示用户插入语句仍在插入 `account_balance`。

原因：

前面移除了“余额扣款”设计，但初始化脚本没有同步改完。

修复：

删除插入语句中的 `account_balance` 字段和值，并把默认用户密码改成 BCrypt hash。

结果：

SQL 脚本与当前实体模型一致，文档也改成“确认缴纳/免罚”，不再写“已扣款”。

## 6. Docker Compose 配置未做本地完整启动实验

现象：

仓库已经添加 `Dockerfile` 和 `docker-compose.yml`，但当前本地验证过程没有实际执行：

```bash
docker compose up --build
```

处理：

README 只写“已提供 Docker Compose 启动配置”，不写“已完成本地 Docker 实测”。后续如果执行并通过，再把实验记录补到 `docs/experiment-log.md`。

结论：

没有跑过的东西不在 README 里装作已经跑过。
