# Design Notes

这份 notes 用来帮助快速理解代码，不追求像正式设计书那样完整，而是记录当前版本最重要的设计边界。

## 1. 领域主线

系统核心不是“图书 CRUD”，而是“单册级库存流转”。

主线对象：

- `Book`：图书主数据，保存 ISBN、书名、分类、总馆藏、可借数量、主书架位置。
- `BookCopy`：具体单册，保存单册编号、当前状态、当前借阅读者和当前借阅记录。
- `StorageLocation`：某本书在某个书架上的库存数量。
- `BorrowRecord`：借阅流水，保存读者、图书、单册、书架和罚款快照。
- `User`：管理员和读者共用账号表，通过 `role` 区分。

关键判断：

如果只维护 `Book.availableCount`，系统很容易变成“库存数字游戏”。引入 `BookCopy` 和 `StorageLocation` 后，才能解释一本具体书在什么书架、是否被借出、归还时回到哪里。

## 2. 借书流程

入口：

- 管理员端：`BorrowController.borrowBatch`
- 读者端：`SelfServiceController.borrow`

核心服务：

- `BorrowService.borrowBooksForDays`
- `BorrowService.borrowBooks`

处理顺序：

1. 锁定读者行，确认账号存在、角色是 reader、状态 enabled。
2. 去重图书 id，校验借阅天数和应还日期。
3. 查询当前未还数量，检查是否超过借阅上限。
4. 检查读者是否存在逾期未还。
5. 检查每本书是否启用、是否有库存、是否重复借阅。
6. 使用条件更新扣减 `Book.availableCount`。
7. 扣减 `StorageLocation.availableCount`。
8. 占用一本 `BookCopy`。
9. 创建 `BorrowRecord` 并回填单册当前借阅记录。

最重要的技术点：

库存扣减不是“查出来减一再保存”，而是数据库条件更新。这样两个请求同时借最后一本时，只有一个请求能更新成功。

## 3. 还书和逾期罚款

入口：

- 管理员端：`BorrowController.returnBatch`
- 读者端：`SelfServiceController.returnBooks`

核心服务：

- `BorrowService.returnBook`
- `BorrowService.returnBooks`

处理顺序：

1. 锁定借阅记录，防止重复归还。
2. 如果逾期，生成或保留罚款信息，罚款状态默认 `unpaid`。
3. 标记记录为 `returned`，写入归还日期。
4. 释放 `BookCopy`。
5. 恢复 `Book.availableCount`，但不能超过总馆藏。
6. 恢复 `StorageLocation.availableCount`，但不能超过该书架总数。

罚款设计：

当前系统不做“余额扣款”。管理员端只做两件事：

- 确认已缴纳：`fineStatus = paid`
- 免罚：`fineStatus = waived`

这样比假装有余额系统更诚实，也避免把不完整的支付逻辑写成已完成能力。

## 4. 冻结与解冻边界

冻结触发：

- 发现逾期罚款待缴纳。
- 管理员手动冻结违规读者。
- 归还逾期书时生成待缴罚款。

解冻规则：

管理员确认缴纳或免罚后，系统会检查该读者是否还有：

- 逾期未还记录。
- 已归还但罚款仍未处理的逾期记录。

只有所有阻塞项都清掉，才自动把读者恢复为 `enabled`。

设计理由：

不能因为某一条罚款处理完，就直接解冻读者；否则读者还有另一条逾期未还时也能继续登录和借书。

## 5. 权限隔离

管理端：

- Session 中必须有 `adminId`。
- 通过 `LoginInterceptor` 拦截非 `/self/**` 接口。

读者端：

- Session 中必须有 `readerId`。
- `/self/**` 不相信前端传来的用户 id。
- 查询记录、借书、续借、还书都以 Session 中的读者为准。

典型例子：

`SelfServiceController.returnBooks` 会先查提交的记录，再确认每条记录的 `userId` 都等于当前 Session 读者 id。只要包含不存在记录或他人记录，就拒绝。

## 6. DTO 边界

已经 DTO 化的输入：

- `BookRequest`
- `ReaderCreateRequest`
- `ReaderUpdateRequest`
- `ReaderRegisterRequest`
- 批量借书/还书请求对象
- 读者自助借书/续借/还书请求对象

DTO 解决的问题：

- 前端不能提交 `role` 伪造管理员或修改读者身份。
- 前端不能直接提交 `createdAt`、`activeBorrowCount` 这类派生字段。
- 图书编辑时不能随便覆盖真实库存数量。
- 参数校验错误由 `ApiExceptionHandler` 统一转成前端可读提示。

仍可继续改进：

- 所有响应也可以逐步改成 Response DTO，减少实体字段暴露。
- 分类、书架管理接口也可以进一步细化请求 DTO。

## 7. 扫码/RFID 模拟模块

当前实现：

- `ScanController.resolve`
- 支持输入单册码、ISBN、书架位置。
- 返回类型可能是 `copy`、`book`、`shelf` 或 `unknown`。

它目前只是轻量模拟，不直接改变库存。

设计理由：

主借还书流程已经稳定，不应该为了贴近 IoT/工业库存管理而大改主线。先加只读解析接口，可以把系统包装成“资产流转与库存监测系统”的雏形，同时不破坏现有借还事务。

后续扩展方向：

- 给前端加扫码输入框。
- 接 WebSocket/SSE 做库存状态推送。
- 模拟 RFID 网关定时上报书架盘点结果。
- 把异常盘点结果转成待处理任务。

## 8. 不要轻易改的地方

不要把借书库存逻辑拆回控制器：

控制器只负责接口输入输出，库存、单册、书架和借阅记录必须放在 `BorrowService` 的事务里。

不要让读者端传 `userId`：

读者身份必须来自 Session。前端传 userId 会打开越权入口。

不要把罚款写成“真实扣款”：

当前没有支付账户和资金流水系统，只能写“确认缴纳/免罚”。如果以后接真实支付，需要新增支付流水表和对账状态，而不是复活余额字段。

不要删除有历史借阅的图书：

有历史记录的图书只能停用，不能删除，否则历史记录失去业务意义。
