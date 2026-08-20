# 图书馆借阅管理系统

这是一个基于 Spring Boot + Vue 3 的图书馆借阅管理系统，覆盖管理员端和读者自助端两套工作流。系统围绕图书、分类、书架、馆藏、副本、读者、借阅、归还、续借、预警、逾期罚款和账号冻结进行设计，适合课程设计、毕业设计、实验室/班级图书角、小型图书室等场景继续扩展。

当前版本已经从简单 CRUD 增强为更完整、可复用的借阅系统：支持分页、搜索、批量借还、库存一致性、书架位置管理、单册编号、逾期保留、还书预警、罚款缴纳确认、违规读者冻结，以及读者端自助借阅/归还/续借申请。

## 技术栈

| 模块 | 技术 |
| --- | --- |
| 后端 | Java 11, Spring Boot 2.7.18, Spring Web, Spring Data JPA |
| 前端 | Vue 3, Vite, Element Plus, Axios |
| 数据库 | MySQL / MariaDB |
| 构建 | Maven Wrapper, npm |
| 部署形态 | Spring Boot Jar 内置前端静态资源，默认端口 `8080` |

## 核心功能

### 管理员端

- 工作台：统计图书、馆藏、可借、借阅中、读者数量、逾期未还、分类分布和借阅趋势。
- 分类管理：分类查询、新增、编辑、删除、合并、查看分类下图书。
- 图书管理：ISBN/书名/作者搜索，分类和状态筛选，新增、编辑、停用、启用、删除、详情查看。
- 馆藏与副本：维护馆藏数量、可借数量、书架位置、单册编号和副本状态。
- 书架管理：按 `区域-排号-格号` 管理位置，后两段上限 50，避免无限扩张。
- 读者管理：读者新增、编辑、停用/启用、重置密码、查看当前借阅和历史记录。
- 借书办理：选择读者和多本图书批量借出，自动校验库存、读者状态、借阅上限、重复借阅和逾期限制。
- 还书办理：当前未还记录查询，支持单条归还和批量归还。
- 续借审核：读者提交续借申请后，管理员可审核并更新应还日期。
- 还书预警：按未来 7/30/90 天或全年范围查询即将到期和已逾期记录，支持关键词搜索。
- 逾期处理：保留所有曾经逾期的记录，已归还后仍可在逾期历史中处理罚款和冻结。
- 罚款与冻结：逾期罚款待缴纳时默认冻结读者账号，管理员确认缴纳或免罚后系统自动判断是否解冻。

### 读者自助端

- 读者注册：手机号注册，系统自动生成借阅证号。
- 读者登录：冻结账号不能登录自助端。
- 我的借阅：首页展示借阅证号、姓名、账号状态、当前未还、可借上限和还书预警。
- 图书查询：按 ISBN、书名、作者和分类查询可借图书。
- 自助借书：可选择默认 30 天或自定义借阅天数，最长 90 天。
- 还书预警：支持范围筛选、关键词搜索、查询、刷新和重置。
- 续借办理：未逾期、未归还且无待审批申请的记录可申请续借。
- 自助还书：读者只能归还自己的未还记录。
- 我的记录：查看本人完整借阅历史。

## 关键业务规则

| 规则 | 说明 |
| --- | --- |
| 读者证号 | 自动生成，格式为 `R + 年份 + 四位序号`，例如 `R20260001` |
| 借阅上限 | 默认每位读者最多同时借阅 3 本，可通过 `library.max-active-borrow-count` 配置 |
| 借阅期限 | 默认 30 天，自定义最长 90 天 |
| 续借 | 单次续借不超过 3 个月，逾期记录不可续借 |
| 馆藏上限 | 新增图书初始馆藏和书架库存每次新增上限为 50 |
| 书架位置 | 采用 `A-01-01` 这类格式，排号和格号均限制在 1-50 |
| 图书停用 | 停用后禁止新借；已经借出的记录保留，仍可正常归还 |
| 逾期记录 | 归还后仍保留在逾期历史中，便于处理罚款和追溯 |
| 罚款金额 | 默认每天 `0.50` 元，按逾期天数计算 |
| 罚款缴纳 | 管理员端只做“确认缴纳/免罚”，不做虚假的余额扣款 |
| 账号冻结 | 逾期罚款待缴纳时冻结读者；缴纳或免罚后若无其他逾期/待缴罚款则自动解冻 |

## 项目结构

```text
demo
├── frontend/                         # Vue 3 + Vite 前端工程
├── src/main/java/com/example/demo/    # Spring Boot 后端源码
│   ├── config/                        # 登录拦截、业务配置
│   ├── controller/                    # REST 接口
│   ├── entity/                        # JPA 实体
│   ├── repository/                    # 数据访问层
│   └── service/                       # 业务服务
├── src/main/resources/static/         # 前端构建后的静态资源
├── src/main/resources/application.properties
├── src/test/java/                     # 后端测试
├── sql/init.sql                       # 数据库初始化脚本
├── docs/                              # 需求和设计文档
├── pom.xml
└── README.md
```

## 数据库配置

默认连接配置在 `src/main/resources/application.properties`：

```properties
server.port=8080

spring.datasource.url=jdbc:mysql://localhost:3306/library_system?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
spring.datasource.username=root
spring.datasource.password=

spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false

library.max-active-borrow-count=3
library.max-page-size=100
library.repair-legacy-data-on-startup=false

spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
```

如果本机 MySQL 有密码，请修改：

```properties
spring.datasource.password=你的数据库密码
```

### 初始化数据库

方式一：使用脚本重建数据库并导入演示数据。

```bash
mysql -u root -p < sql/init.sql
```

方式二：手动创建数据库，让 Spring Boot 自动建表和补齐演示数据。

```sql
CREATE DATABASE library_system DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

`DataInitializer` 会在启动时创建默认管理员、演示读者、分类、图书、书架、馆藏和借阅数据。已有库需要大规模修复旧数据时，可临时开启：

```properties
library.repair-legacy-data-on-startup=true
```

修复完成后建议改回 `false`，避免大型数据量下每次启动扫描全库。

## 启动方式

### 一体化启动

前端已经构建到 `src/main/resources/static` 后，可直接运行 Spring Boot：

```bash
.\mvnw.cmd test
.\mvnw.cmd -DskipTests package
java -jar target\demo-0.0.1-SNAPSHOT.jar
```

访问：

```text
http://localhost:8080/
```

### 前后端分离开发

先启动后端：

```bash
.\mvnw.cmd spring-boot:run
```

再启动前端开发服务器：

```bash
cd frontend
npm install
npm run dev
```

访问：

```text
http://localhost:5173/
```

Vite 已配置代理，`/auth`、`/books`、`/borrow`、`/self` 等请求会转发到 `http://localhost:8080`。

### 构建前端到 Spring Boot

```bash
cd frontend
npm install
npm run build:spring
```

构建产物会写入：

```text
src/main/resources/static
```

## 默认账号

管理员：

```text
账号：admin
密码：123456
```

演示读者：

```text
借阅证号：R20260001
密码：123456
```

系统还会创建若干演示读者，用于展示停用、逾期和借阅上限场景。

## 常用接口

| 方法 | 地址 | 说明 |
| --- | --- | --- |
| `POST` | `/auth/login` | 管理员登录 |
| `POST` | `/auth/reader-login` | 读者登录 |
| `POST` | `/auth/logout` | 退出登录 |
| `GET` | `/auth/me` | 当前登录状态 |
| `GET` | `/dashboard` | 管理员工作台 |
| `GET/POST/PUT/DELETE` | `/categories` | 分类管理 |
| `GET/POST/PUT/DELETE` | `/books` | 图书管理 |
| `GET/POST/PUT` | `/readers` | 读者管理 |
| `GET/POST/DELETE` | `/storage-locations` | 书架/馆藏位置管理 |
| `GET` | `/borrow/reader-options` | 借书读者候选 |
| `GET` | `/borrow/book-options` | 借书图书候选 |
| `POST` | `/borrow/batch` | 管理员批量借书 |
| `GET` | `/borrow/return-options` | 可还记录 |
| `POST` | `/borrow/return` | 批量还书 |
| `GET` | `/borrow/warnings` | 管理员还书预警 |
| `GET` | `/borrow/overdue` | 逾期历史和罚款处理 |
| `POST` | `/borrow/records/{id}/fine/paid` | 确认罚款已缴纳 |
| `POST` | `/borrow/records/{id}/fine/waived` | 免除罚款 |
| `POST` | `/borrow/records/{id}/freeze-reader` | 冻结违规读者 |
| `GET` | `/self/me` | 读者个人信息 |
| `GET` | `/self/books` | 读者端图书查询 |
| `GET` | `/self/warnings` | 读者端还书预警 |
| `GET` | `/self/records` | 读者本人记录 |
| `POST` | `/self/borrow` | 读者自助借书 |
| `POST` | `/self/return` | 读者自助还书 |
| `POST` | `/self/records/{id}/extension-request` | 读者提交续借申请 |

## 测试

后端测试覆盖图书停用、库存/书架规则、逾期罚款缴纳和冻结解冻逻辑。

```bash
.\mvnw.cmd test
```

前端生产构建：

```bash
cd frontend
npm run build:spring
```

## 部署建议

- 生产环境请修改默认管理员密码和数据库密码。
- 不建议在生产库长期使用 `spring.jpa.hibernate.ddl-auto=update`，可改用明确的 SQL 迁移脚本。
- 如果并发用户增加，请根据数据库承载能力调整 Hikari 连接池和 MySQL 最大连接数。
- `library.max-page-size` 用于限制单次分页返回量，避免大表查询把页面拖慢。
- 大型数据量场景下，保持 `library.repair-legacy-data-on-startup=false`。

## 常见问题

### 8080 端口被占用

先确认占用进程：

```bash
netstat -ano | findstr :8080
```

结束旧进程后再启动。本项目默认固定使用 `8080`，前端代理和文档都按该地址配置。

### 前端页面还是旧样子

重新构建前端并打包后端：

```bash
cd frontend
npm run build:spring
cd ..
.\mvnw.cmd -DskipTests package
java -jar target\demo-0.0.1-SNAPSHOT.jar
```

浏览器仍缓存旧资源时，按 `Ctrl + F5` 强制刷新。

### 读者被冻结后为什么不能登录

这是当前业务规则：逾期罚款待缴纳或管理员冻结后，读者不能登录自助端，也不能继续借书/续借。管理员在逾期处理中确认罚款已缴纳或免罚后，系统会检查该读者是否还有其他逾期或待缴罚款；全部处理完才会自动恢复启用。

## 后续可扩展方向

- 条码/二维码扫描借还。
- 邮件、短信或站内信到期提醒。
- 更细的馆藏副本流转记录。
- 多馆区、多角色权限和操作审计。
- 预约、催还、丢失赔偿和损坏登记。
- Docker Compose 一键启动 MySQL + 后端服务。
