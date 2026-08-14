# 图书馆借阅系统

基于 Spring Boot、MySQL/MariaDB、DBeaver 的图书馆借阅系统。系统包含管理员/馆员管理端和普通读者自助端，现有后台功能属于完整系统中的管理端模块。

## 环境要求

- JDK 17
- IntelliJ IDEA
- MySQL 或 MariaDB
- DBeaver

## 数据库初始化

在 DBeaver 中执行：

```text
sql/init.sql
```

脚本会重新创建 `library_system` 数据库，并写入演示数据。演示数据覆盖：

- 正常借阅
- 正常归还
- 无库存图书
- 停用读者
- 停用图书
- 逾期未还记录
- 单个读者 3 本未还的借阅上限场景

## 数据库配置

配置文件：

```text
src/main/resources/application.properties
```

当前配置：

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/library_system?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
spring.datasource.username=root
spring.datasource.password=
```

如果你的数据库有密码，修改：

```properties
spring.datasource.password=你的密码
```

## 启动方式

在 IntelliJ IDEA 中打开：

```text
src/main/java/com/example/demo/DemoApplication.java
```

点击绿色运行按钮启动。

启动成功后访问：

```text
http://localhost:8080/
```

默认管理员账号：

```text
账号：admin
密码：123456
```

默认读者账号：

```text
借阅证号：R20260001
密码：123456
```

## 已实现功能

- 管理员登录和退出
- 后台接口登录拦截，未登录不能直接访问管理接口
- 普通读者登录和退出
- 普通读者可自行注册读者账号，系统保存每个读者的借阅证号和密码
- 管理员新增读者时可设置登录密码，编辑读者时可重置密码
- 管理端与读者自助端权限隔离，读者不能进入管理员功能
- 工作台统计和近期借阅展示
- 图书分类新增、编辑、删除、查询、分页
- 书架存储查询，展示每本图书的存放书架、馆藏数、可借数和已借数
- 图书新增、完整编辑、删除、启用、停用、查询、分页、详情
- 读者新增、完整编辑、启用、停用、查询、分页、详情
- 管理端借书时搜索读者和图书，并显示状态、库存和当前借阅数量
- 管理端支持一次选择多本图书办理借阅，系统为同一次借阅生成统一批次号
- 借书校验：停用限制、库存限制、最多 3 本、不能重复借同一本未还图书
- 还书时搜索未还记录，并显示读者、图书、借阅日期、应还日期和逾期情况
- 管理端支持勾选多条未还记录批量归还，可实现一次借阅中的部分还书
- 读者自助端支持图书查询、自助借书、查看本人未还、自助勾选部分还书和查看本人记录
- 借阅记录按读者、图书、状态、借阅日期、应还日期查询，并保存读者和图书快照、借阅批次号
- 借书时通过数据库条件更新扣减库存，防止并发借阅造成超库存借阅
- 逾期查询显示逾期天数，并可直接还书
- 页面包含分页、重置、加载中提示、无数据提示、重要操作确认
- 新增、编辑、删除、停用、借书、还书等操作会禁用按钮，防止重复点击
- 后端统一返回中文错误提示，并对参数异常、数据库约束异常和未知异常做统一处理

## 接口清单

```text
POST   /auth/login
POST   /auth/logout
GET    /auth/me

GET    /dashboard

GET    /categories?page=0&size=10&keyword=计算机
GET    /categories/{id}
POST   /categories
PUT    /categories/{id}
DELETE /categories/{id}

GET    /storage-locations?page=0&size=10&keyword=A-03-02
GET    /storage-locations/{id}

GET    /books?page=0&size=10&keyword=Java&category=计算机&status=enabled
GET    /books/{id}
POST   /books
PUT    /books/{id}
PUT    /books/{id}/disable
PUT    /books/{id}/enable
DELETE /books/{id}

GET    /readers?page=0&size=10&keyword=张三&status=enabled
GET    /readers/{id}
POST   /readers
PUT    /readers/{id}
PUT    /readers/{id}/disable
PUT    /readers/{id}/enable

GET    /borrow/reader-options?keyword=张三
GET    /borrow/book-options?keyword=Java
GET    /borrow/return-options?keyword=Java
GET    /borrow/records?page=0&size=10&keyword=张三&status=borrowed
GET    /borrow/records/{id}
GET    /borrow/overdue?page=0&size=10&keyword=王五
POST   /borrow?userId=2&bookId=1&dueDate=2026-09-10
POST   /borrow/return/{recordId}
```

## 验收测试建议

1. 使用 `admin / 123456` 登录后台。
2. 在“图书分类”中新增、编辑、查询和删除一个没有图书的分类。
3. 在“图书管理”中新增图书，查询后编辑、停用、启用。
4. 在“读者管理”中新增读者，查询后编辑、停用、启用。
5. 在“借书办理”中搜索读者和图书，选择后借书。
6. 对无库存图书、停用读者、停用图书、已借满 3 本读者分别测试借书失败。
7. 在“还书办理”中搜索未还记录，确认还书。
8. 再次归还同一记录，应提示失败且库存不重复增加。
9. 在“逾期查询”中查看逾期记录和逾期天数。
10. 重启服务后确认图书、读者、借阅记录仍然存在。

## 设计文档

设计说明、ER 图、用例图、借书流程图和还书流程图见：

```text
docs/design.md
```
