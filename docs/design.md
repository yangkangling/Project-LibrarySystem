# 图书馆借阅系统设计说明

## 默认管理员账号

```text
账号：admin
密码：123456
```

## ER 图

```mermaid
erDiagram
  USERS {
    bigint id PK
    varchar username UK
    varchar password
    varchar real_name
    varchar role
    varchar phone
    varchar status
    varchar remark
    datetime created_at
  }

  BOOKS {
    bigint id PK
    varchar isbn UK
    varchar title
    varchar author
    varchar publisher
    date publish_date
    varchar category
    varchar shelf_location
    varchar status
    int total_count
    int available_count
    datetime created_at
  }

  CATEGORIES {
    bigint id PK
    varchar name UK
    varchar description
    datetime created_at
  }

  BORROW_RECORDS {
    bigint id PK
    bigint user_id FK
    bigint book_id FK
    varchar reader_card
    varchar reader_name
    varchar reader_phone
    varchar book_isbn
    varchar book_title
    varchar book_author
    date borrow_date
    date due_date
    date return_date
    varchar status
    datetime created_at
  }

  USERS ||--o{ BORROW_RECORDS : borrows
  BOOKS ||--o{ BORROW_RECORDS : borrowed_as
```

## 用例图

```mermaid
flowchart LR
  A["管理员"]
  A --> B["登录/退出"]
  A --> C["工作台统计"]
  A --> D["图书分类管理"]
  A --> E["图书管理"]
  A --> F["读者管理"]
  A --> G["借书办理"]
  A --> H["还书办理"]
  A --> I["借阅记录查询"]
  A --> J["逾期查询"]
```

## 借书流程

```mermaid
flowchart TD
  A["提交读者ID和图书ID"] --> B["查询读者"]
  B --> C{"读者存在且启用？"}
  C -- 否 --> X["返回错误，不生成记录"]
  C -- 是 --> D["查询图书"]
  D --> E{"图书存在、启用且有库存？"}
  E -- 否 --> X
  E -- 是 --> F{"读者未还数量小于5？"}
  F -- 否 --> X
  F -- 是 --> G{"未重复借同一本未还图书？"}
  G -- 否 --> X
  G -- 是 --> H["减少可借库存"]
  H --> I["生成借阅记录"]
  I --> J["提交事务并返回记录编号"]
```

## 还书流程

```mermaid
flowchart TD
  A["提交借阅记录ID"] --> B["查询借阅记录"]
  B --> C{"记录存在且未归还？"}
  C -- 否 --> X["返回错误，库存不变化"]
  C -- 是 --> D["设置归还日期和已归还状态"]
  D --> E["图书可借库存增加1"]
  E --> F{"可借数量不超过馆藏总数？"}
  F -- 否 --> X
  F -- 是 --> G["提交事务"]
```

## 接口清单

```text
POST   /auth/login
POST   /auth/logout
GET    /auth/me

GET    /dashboard

GET    /categories
GET    /categories?page=0&size=10&keyword=计算机
GET    /categories/{id}
POST   /categories
PUT    /categories/{id}
DELETE /categories/{id}

GET    /books
GET    /books?page=0&size=10&keyword=Java&category=计算机&status=enabled
GET    /books/{id}
POST   /books
PUT    /books/{id}
PUT    /books/{id}/disable
PUT    /books/{id}/enable
DELETE /books/{id}

GET    /readers
GET    /readers?page=0&size=10&keyword=张三&status=enabled
GET    /readers/{id}
POST   /readers
PUT    /readers/{id}
PUT    /readers/{id}/disable
PUT    /readers/{id}/enable

GET    /borrow/records
GET    /borrow/records?page=0&size=10&status=borrowed&userId=2&bookId=1
GET    /borrow/records/{id}
GET    /borrow/overdue
POST   /borrow?userId=2&bookId=1
POST   /borrow/return/{recordId}
```

## 业务规则实现说明

- ISBN 使用唯一校验。
- 借阅证号使用 `users.username`，并进行唯一校验。
- `books.total_count` 表示馆藏总数，`books.available_count` 表示可借数量。
- 借书和还书方法使用 `@Transactional`，保证记录和库存一起成功或一起失败。
- 借阅记录保存读者证号、读者姓名、手机号、ISBN、书名和作者快照，避免后续资料修改影响历史记录展示。
- 除首页和 `/auth/**` 外，后台接口需要登录后访问。
- `users.status=disabled` 的读者不能新借书。
- `books.status=disabled` 的图书不能新借书，但未还记录仍可归还。
- 同一读者最多 5 本未还。
- 同一读者不能重复借同一本未归还图书。
- 当前日期超过 `due_date` 且状态仍为 `borrowed` 时，会出现在逾期查询中。
