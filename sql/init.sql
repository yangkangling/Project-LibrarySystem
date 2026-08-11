DROP DATABASE IF EXISTS library_system;
CREATE DATABASE library_system DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE library_system;

CREATE TABLE categories (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(50) NOT NULL UNIQUE,
  description VARCHAR(255),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL UNIQUE,
  password VARCHAR(100) NOT NULL,
  real_name VARCHAR(50),
  role VARCHAR(20) NOT NULL,
  phone VARCHAR(20),
  status VARCHAR(20) DEFAULT 'enabled',
  remark VARCHAR(255),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE books (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(100) NOT NULL,
  author VARCHAR(50) NOT NULL,
  isbn VARCHAR(30) NOT NULL UNIQUE,
  publisher VARCHAR(100),
  publish_date DATE,
  category VARCHAR(50) NOT NULL,
  shelf_location VARCHAR(50),
  status VARCHAR(20) DEFAULT 'enabled',
  total_count INT NOT NULL DEFAULT 1,
  available_count INT NOT NULL DEFAULT 1,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT ck_books_total_count CHECK (total_count > 0),
  CONSTRAINT ck_books_available_count CHECK (available_count >= 0 AND available_count <= total_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE borrow_records (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  book_id BIGINT NOT NULL,
  reader_card VARCHAR(50),
  reader_name VARCHAR(50),
  reader_phone VARCHAR(20),
  book_isbn VARCHAR(30),
  book_title VARCHAR(100),
  book_author VARCHAR(50),
  borrow_date DATE NOT NULL,
  due_date DATE NOT NULL,
  return_date DATE,
  status VARCHAR(20) NOT NULL DEFAULT 'borrowed',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_borrow_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_borrow_book FOREIGN KEY (book_id) REFERENCES books(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_books_keyword ON books(isbn, title, author);
CREATE INDEX idx_books_category_status ON books(category, status);
CREATE INDEX idx_users_reader_search ON users(role, username, real_name, phone, status);
CREATE INDEX idx_borrow_user_status ON borrow_records(user_id, status);
CREATE INDEX idx_borrow_book_status ON borrow_records(book_id, status);
CREATE INDEX idx_borrow_due_status ON borrow_records(status, due_date);

INSERT INTO categories (name, description) VALUES
('计算机', '计算机与软件开发类图书'),
('文学', '小说、散文与文学作品'),
('历史', '历史与文化类图书');

INSERT INTO users (id, username, password, real_name, role, phone, status, remark) VALUES
(1, 'admin', '123456', '管理员', 'admin', '13800000000', 'enabled', '默认管理员账号'),
(2, 'R20260001', '123456', '张三', 'reader', '13900000001', 'enabled', '普通读者'),
(3, 'R20260002', '123456', '李四', 'reader', '13900000002', 'disabled', '停用读者演示'),
(4, 'R20260003', '123456', '王五', 'reader', '13900000003', 'enabled', '逾期演示读者'),
(5, 'R20260004', '123456', '赵六', 'reader', '13900000004', 'enabled', '借阅上限演示读者');

INSERT INTO books (id, title, author, isbn, publisher, publish_date, category, shelf_location, status, total_count, available_count) VALUES
(1, 'Java程序设计', '张老师', '978000000001', '清华大学出版社', '2024-03-01', '计算机', 'A-03-02', 'enabled', 5, 4),
(2, '数据库系统概论', '王老师', '978000000002', '高等教育出版社', '2023-09-01', '计算机', 'A-04-01', 'enabled', 3, 3),
(3, '无库存图书', '测试作者', '978000000003', '演示出版社', '2022-01-01', '计算机', 'B-01-01', 'enabled', 1, 0),
(4, '停用图书', '测试作者', '978000000004', '演示出版社', '2022-02-01', '文学', 'B-01-02', 'disabled', 2, 2),
(5, '逾期演示图书', '测试作者', '978000000005', '演示出版社', '2021-05-01', '历史', 'C-02-01', 'enabled', 2, 1),
(6, '上限演示图书1', '测试作者', '978000000006', '演示出版社', '2020-01-01', '计算机', 'D-01-01', 'enabled', 1, 0),
(7, '上限演示图书2', '测试作者', '978000000007', '演示出版社', '2020-01-02', '计算机', 'D-01-02', 'enabled', 1, 0),
(8, '上限演示图书3', '测试作者', '978000000008', '演示出版社', '2020-01-03', '计算机', 'D-01-03', 'enabled', 1, 0),
(9, '上限演示图书4', '测试作者', '978000000009', '演示出版社', '2020-01-04', '计算机', 'D-01-04', 'enabled', 1, 0),
(10, '上限演示图书5', '测试作者', '978000000010', '演示出版社', '2020-01-05', '计算机', 'D-01-05', 'enabled', 1, 0);

INSERT INTO borrow_records (id, user_id, book_id, reader_card, reader_name, reader_phone, book_isbn, book_title, book_author, borrow_date, due_date, return_date, status) VALUES
(1, 2, 1, 'R20260001', '张三', '13900000001', '978000000001', 'Java程序设计', '张老师', DATE_SUB(CURDATE(), INTERVAL 20 DAY), DATE_ADD(CURDATE(), INTERVAL 10 DAY), NULL, 'borrowed'),
(2, 2, 2, 'R20260001', '张三', '13900000001', '978000000002', '数据库系统概论', '王老师', DATE_SUB(CURDATE(), INTERVAL 40 DAY), DATE_SUB(CURDATE(), INTERVAL 10 DAY), CURDATE(), 'returned'),
(3, 4, 5, 'R20260003', '王五', '13900000003', '978000000005', '逾期演示图书', '测试作者', DATE_SUB(CURDATE(), INTERVAL 45 DAY), DATE_SUB(CURDATE(), INTERVAL 15 DAY), NULL, 'borrowed'),
(4, 5, 6, 'R20260004', '赵六', '13900000004', '978000000006', '上限演示图书1', '测试作者', DATE_SUB(CURDATE(), INTERVAL 3 DAY), DATE_ADD(CURDATE(), INTERVAL 27 DAY), NULL, 'borrowed'),
(5, 5, 7, 'R20260004', '赵六', '13900000004', '978000000007', '上限演示图书2', '测试作者', DATE_SUB(CURDATE(), INTERVAL 3 DAY), DATE_ADD(CURDATE(), INTERVAL 27 DAY), NULL, 'borrowed'),
(6, 5, 8, 'R20260004', '赵六', '13900000004', '978000000008', '上限演示图书3', '测试作者', DATE_SUB(CURDATE(), INTERVAL 3 DAY), DATE_ADD(CURDATE(), INTERVAL 27 DAY), NULL, 'borrowed'),
(7, 5, 9, 'R20260004', '赵六', '13900000004', '978000000009', '上限演示图书4', '测试作者', DATE_SUB(CURDATE(), INTERVAL 3 DAY), DATE_ADD(CURDATE(), INTERVAL 27 DAY), NULL, 'borrowed'),
(8, 5, 10, 'R20260004', '赵六', '13900000004', '978000000010', '上限演示图书5', '测试作者', DATE_SUB(CURDATE(), INTERVAL 3 DAY), DATE_ADD(CURDATE(), INTERVAL 27 DAY), NULL, 'borrowed');
