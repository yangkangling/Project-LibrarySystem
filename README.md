# 图书馆借阅系统

基于 Spring Boot、MySQL/MariaDB、DBeaver 的图书馆借阅后台管理系统。

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
- 单个读者 5 本未还的借阅上限场景

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

## 已实现功能

- 管理员登录和退出
- 后台接口登录拦截，未登录不能直接访问管理接口
- 工作台统计和近期借阅展示
- 图书分类新增、编辑、删除、查询、分页
- 图书新增、完整编辑、删除、启用、停用、查询、分页、详情
- 读者新增、完整编辑、启用、停用、查询、分页、详情
- 借书时搜索读者和图书，并显示状态、库存和当前借阅数量
- 借书校验：停用限制、库存限制、最多 5 本、不能重复借同一本未还图书
- 还书时搜索未还记录，并显示读者、图书、借阅日期、应还日期和逾期情况
- 借阅记录按读者、图书、状态、借阅日期、应还日期查询，并保存读者和图书快照
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
6. 对无库存图书、停用读者、停用图书、已借满 5 本读者分别测试借书失败。
7. 在“还书办理”中搜索未还记录，确认还书。
8. 再次归还同一记录，应提示失败且库存不重复增加。
9. 在“逾期查询”中查看逾期记录和逾期天数。
10. 重启服务后确认图书、读者、借阅记录仍然存在。

## 设计文档

设计说明、ER 图、用例图、借书流程图和还书流程图见：

```text
docs/design.md
```
