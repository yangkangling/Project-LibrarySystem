# Code Walkthrough

这份文档用于答辩、面试或项目讲解。目标是让你能按自己的话讲清楚：系统解决什么问题、关键代码在哪里、为什么这样设计、怎么证明它能工作。

## 1. 30 秒项目介绍

可以这样讲：

这是一个基于 Spring Boot + Vue 3 的图书馆借阅管理系统。它不是只做图书增删改查，而是围绕“单册级库存流转”设计：一本书有主库存、具体单册、所在书架、借阅记录和逾期罚款。系统支持管理员批量借还、读者自助借还、续借申请、逾期罚款、账号冻结，并且对“并发借最后一本书”做了原子库存扣减和测试验证。

## 2. 代码目录怎么讲

后端主包：

```text
src/main/java/com/yangkangling/library
```

讲解顺序：

1. `controller/`：接口层，接收前端请求。
2. `dto/`：输入对象，防止前端直接提交实体字段。
3. `service/`：业务层，尤其是借书、还书、书架和密码逻辑。
4. `repository/`：数据访问层，包含原子库存扣减 SQL。
5. `entity/`：JPA 实体，映射数据库表。
6. `config/`：登录拦截、业务配置、BCrypt 配置。
7. `src/test/java/...`：关键业务测试。

前端主目录：

```text
frontend/src/components
```

主要组件：

- `LoginView.vue`：管理员/读者登录、读者注册、修改密码。
- `AdminApp.vue`：管理员端，集中管理分类、图书、读者、借还、逾期和书架。
- `ReaderApp.vue`：读者自助端，处理图书查询、自助借书、续借、还书和预警。

## 3. 借书流程怎么讲

核心入口：

```text
BorrowController.borrowBatch
SelfServiceController.borrow
BorrowService.borrowBooks
```

讲法：

管理员端和读者端都不会自己改库存，而是统一调用 `BorrowService`。这样不管从哪个入口借书，规则都是一致的。

关键步骤：

1. 先锁定读者，校验 reader 角色、启用状态、未超过借阅上限。
2. 检查该读者是否有逾期未还。
3. 检查图书是否启用、是否有库存、是否重复借阅。
4. 扣减图书主库存。
5. 扣减书架库存。
6. 占用一本具体单册。
7. 创建借阅记录，并把记录 id 回填到单册。

最值得讲的点：

`BookRepository.decreaseAvailableCountWhenAvailable` 使用条件更新：

```text
available_count = available_count - 1
where available_count > 0
```

它避免两个用户同时借最后一本时都成功。谁先更新成功，谁拿到库存；另一个请求更新行数为 0，就返回库存不足。

## 4. 还书和罚款怎么讲

核心入口：

```text
BorrowController.returnBatch
SelfServiceController.returnBooks
BorrowService.returnBook
```

讲法：

还书也不是只把记录改成 returned。系统要同时恢复图书主库存、书架库存、单册状态，并处理逾期罚款。

关键步骤：

1. 锁定借阅记录，防止重复还书。
2. 如果逾期，生成罚款金额和 `unpaid` 状态。
3. 把借阅记录标记为 `returned`。
4. 释放单册。
5. 恢复图书主库存，且不能超过总馆藏。
6. 恢复书架库存，且不能超过该书架总数。

罚款讲法：

系统没有做虚假的余额扣款。管理员端只确认两种状态：

- 已缴纳：`paid`
- 免罚：`waived`

如果罚款未处理，读者账号会被冻结；全部逾期和待缴罚款处理完后，系统才自动解冻。

## 5. 权限隔离怎么讲

核心文件：

```text
LoginInterceptor.java
SelfServiceController.java
```

讲法：

系统用 Session 区分管理员和读者。管理端接口必须有 `adminId`，读者端 `/self/**` 必须有 `readerId`。

重点：

读者端不相信前端传来的用户 id。比如自助还书时，前端只能传借阅记录 id，后端会检查这些记录是否都属于当前 Session 读者。

可以引用测试：

```text
SelfServiceControllerSecurityTest.readerCannotReturnAnotherReadersBorrowRecord
```

这个测试证明读者不能归还他人的记录。

## 6. 密码安全怎么讲

核心文件：

```text
PasswordConfig.java
PasswordService.java
AuthController.java
ReaderController.java
DataInitializer.java
```

讲法：

新版本不再按明文密码查库，而是按账号和角色查用户，再用 BCrypt 校验密码。

细节：

- 新增用户、注册读者、修改密码都会写入 BCrypt hash。
- 默认初始化账号也写 hash。
- 老数据如果还是明文密码，第一次登录成功后会自动升级成 hash。

这说明系统考虑了从课程演示到正式项目的迁移路径。

## 7. DTO 和参数校验怎么讲

核心文件：

```text
dto/BookRequest.java
dto/ReaderCreateRequest.java
dto/ReaderUpdateRequest.java
dto/ReaderRegisterRequest.java
ApiExceptionHandler.java
```

讲法：

前端不能直接把完整实体交给后端保存。比如读者实体里有 `role` 和 `createdAt`，如果直接接收实体，前端理论上可以伪造角色或修改不该改的字段。

DTO 的价值：

- 输入字段更少、更明确。
- 可以加 `@NotBlank`、`@Pattern`、`@Size` 等校验。
- 错误统一由 `ApiExceptionHandler` 转成前端可读消息。

## 8. 测试怎么讲

自动化测试位置：

```text
src/test/java/com/yangkangling/library
```

最值得讲的测试：

`BorrowServiceConcurrencyTest.onlyOneReaderCanBorrowTheLastAvailableCopy`

讲法：

这个测试创建一本只有 1 册库存的书和两个读者，然后用两个线程同时借这一本书。最终断言：

- 成功借书的请求只有 1 个。
- 图书主库存变成 0。
- 借阅记录只有 1 条。
- 单册状态只有 1 本变成 borrowed。
- 书架可借库存也变成 0。

这比只测 controller 返回值更有含金量，因为它验证了跨表库存一致性。

## 9. 如果被问“你哪里写得不好”

可以诚实回答：

1. `AdminApp.vue` 现在承担了很多管理员端页面逻辑，后续可以拆成多个页面组件。
2. Session 方案适合当前单体系统，多实例部署需要 Redis Session 或 token 方案。
3. `spring.jpa.hibernate.ddl-auto=update` 适合演示和开发，生产环境应该用数据库迁移工具。
4. Docker Compose 配置已经提交，但当前本地还没有完整跑过容器启动实验。
5. 扫码/RFID 目前只是只读模拟接口，还没有前端扫码 UI 和真实硬件接入。

这种回答比硬说“都完成了”更可信。

## 10. 如果被问“你下一步怎么做”

优先级建议：

1. 把 `AdminApp.vue` 拆成图书、读者、借还、逾期、书架多个组件。
2. 给扫码/RFID 模拟加前端页面。
3. 加操作审计表，记录管理员冻结、罚款确认、免罚等动作。
4. 把 Docker Compose 在干净环境实际跑通，并把结果补到实验记录。
5. 引入 Flyway 或 Liquibase，替换 `ddl-auto=update`。

## 11. 一句话总结技术亮点

可以这样收尾：

这个项目最核心的技术点是：用事务和数据库条件更新保证借阅库存一致性，用 Session 隔离管理员和读者权限，用 BCrypt 和 DTO 把安全边界补上，再用并发测试证明“最后一本书不会被借超”。
