-- 初始化图书馆系统数据库。
DROP DATABASE IF EXISTS library_system;
CREATE DATABASE library_system DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE library_system;

-- 图书分类。
CREATE TABLE categories (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(50) NOT NULL UNIQUE,
  description VARCHAR(255),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 管理员和读者账号。
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

-- 图书主表。
CREATE TABLE books (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(100) NOT NULL,
  author VARCHAR(50) NOT NULL,
  isbn VARCHAR(30) NOT NULL UNIQUE,
  publisher VARCHAR(100),
  publish_date DATE,
  category_id BIGINT,
  category VARCHAR(50) NOT NULL,
  shelf_location VARCHAR(50),
  status VARCHAR(20) DEFAULT 'enabled',
  total_count INT NOT NULL DEFAULT 1,
  available_count INT NOT NULL DEFAULT 1,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT ck_books_total_count CHECK (total_count > 0),
  CONSTRAINT ck_books_available_count CHECK (available_count >= 0 AND available_count <= total_count),
  CONSTRAINT fk_books_category FOREIGN KEY (category_id) REFERENCES categories(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 独立书架，只表示一个可用书架位置。
CREATE TABLE shelves (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  shelf_location VARCHAR(50) NOT NULL UNIQUE,
  remark VARCHAR(255),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 书架库存位置。
CREATE TABLE storage_locations (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  book_id BIGINT NOT NULL,
  shelf_location VARCHAR(50) NOT NULL,
  total_count INT NOT NULL DEFAULT 1,
  available_count INT NOT NULL DEFAULT 1,
  remark VARCHAR(255),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT ck_storage_total_count CHECK (total_count >= 0),
  CONSTRAINT ck_storage_available_count CHECK (available_count >= 0 AND available_count <= total_count),
  CONSTRAINT fk_storage_book FOREIGN KEY (book_id) REFERENCES books(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 单册馆藏。
CREATE TABLE book_copies (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  book_id BIGINT NOT NULL,
  copy_code VARCHAR(80) NOT NULL UNIQUE,
  shelf_location VARCHAR(50),
  status VARCHAR(20) NOT NULL DEFAULT 'available',
  current_user_id BIGINT,
  current_borrow_record_id BIGINT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_copy_book FOREIGN KEY (book_id) REFERENCES books(id),
  CONSTRAINT fk_copy_current_user FOREIGN KEY (current_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 借阅记录。
CREATE TABLE borrow_records (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  book_id BIGINT NOT NULL,
  book_copy_id BIGINT,
  storage_location_id BIGINT,
  reader_card VARCHAR(50),
  reader_name VARCHAR(50),
  reader_phone VARCHAR(20),
  book_isbn VARCHAR(30),
  book_title VARCHAR(100),
  book_author VARCHAR(50),
  copy_code VARCHAR(80),
  copy_shelf_location VARCHAR(50),
  shelf_location_snapshot VARCHAR(50),
  batch_no VARCHAR(40),
  borrow_date DATE NOT NULL,
  due_date DATE NOT NULL,
  return_date DATE,
  status VARCHAR(20) NOT NULL DEFAULT 'borrowed',
  fine_amount DECIMAL(10, 2),
  fine_status VARCHAR(20),
  fine_handled_at DATETIME,
  fine_note VARCHAR(255),
  extension_status VARCHAR(20) DEFAULT 'none',
  extension_requested_days INT,
  extension_requested_due_date DATE,
  extension_requested_at DATETIME,
  extension_handled_at DATETIME,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_borrow_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_borrow_book FOREIGN KEY (book_id) REFERENCES books(id),
  CONSTRAINT fk_borrow_copy FOREIGN KEY (book_copy_id) REFERENCES book_copies(id),
  CONSTRAINT fk_borrow_storage FOREIGN KEY (storage_location_id) REFERENCES storage_locations(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 常用查询索引。
CREATE INDEX idx_books_category_id ON books(category_id);
CREATE INDEX idx_books_status_id ON books(status, id);
CREATE INDEX idx_books_shelf_location ON books(shelf_location);
CREATE INDEX idx_storage_book_shelf ON storage_locations(book_id, shelf_location);
CREATE INDEX idx_storage_shelf ON storage_locations(shelf_location);
CREATE INDEX idx_storage_book_available ON storage_locations(book_id, available_count);
CREATE INDEX idx_book_copies_book_status ON book_copies(book_id, status, copy_code);
CREATE INDEX idx_book_copies_book_status_shelf ON book_copies(book_id, status, shelf_location, copy_code);
CREATE INDEX idx_book_copies_borrow_record ON book_copies(current_borrow_record_id);
CREATE INDEX idx_book_copies_shelf ON book_copies(shelf_location);
CREATE INDEX idx_users_role_username ON users(role, username);
CREATE INDEX idx_users_phone_role ON users(phone, role);
CREATE INDEX idx_users_role_status ON users(role, status);
CREATE INDEX idx_borrow_records_user_status_id ON borrow_records(user_id, status, id);
CREATE INDEX idx_borrow_records_book_status_id ON borrow_records(book_id, status, id);
CREATE INDEX idx_borrow_records_status_due ON borrow_records(status, due_date, id);
CREATE INDEX idx_borrow_records_borrow_date ON borrow_records(borrow_date);
CREATE INDEX idx_borrow_records_return_date ON borrow_records(return_date);
CREATE INDEX idx_borrow_records_extension ON borrow_records(extension_status, extension_requested_at);
CREATE INDEX idx_borrow_records_batch_no ON borrow_records(batch_no);

-- 演示分类数据。
INSERT INTO categories (name, description) VALUES
('计算机', '计算机与软件开发类图书'),
('文学', '小说、散文与文学作品'),
('历史', '历史与文化类图书'),
('教育', '教材、教辅与教育类图书');

-- 演示用户数据。
INSERT INTO users (id, username, password, real_name, role, phone, status, remark) VALUES
(1, 'admin', '$2b$10$9VHIiCuOlNrw0yXjKFfJPOddVLOR/wsiPUUb2oBgzzFY9KEpqBvwa', '管理员', 'admin', '13800000000', 'enabled', '默认管理员账号'),
(2, 'R20260001', '$2b$10$9VHIiCuOlNrw0yXjKFfJPOddVLOR/wsiPUUb2oBgzzFY9KEpqBvwa', '张三', 'reader', '13900000001', 'enabled', '普通读者'),
(3, 'R20260002', '$2b$10$9VHIiCuOlNrw0yXjKFfJPOddVLOR/wsiPUUb2oBgzzFY9KEpqBvwa', '李四', 'reader', '13900000002', 'disabled', '停用读者演示'),
(4, 'R20260003', '$2b$10$9VHIiCuOlNrw0yXjKFfJPOddVLOR/wsiPUUb2oBgzzFY9KEpqBvwa', '王五', 'reader', '13900000003', 'enabled', '逾期演示读者'),
(5, 'R20260004', '$2b$10$9VHIiCuOlNrw0yXjKFfJPOddVLOR/wsiPUUb2oBgzzFY9KEpqBvwa', '赵六', 'reader', '13900000004', 'enabled', '借阅上限演示读者');

INSERT INTO books (id, title, author, isbn, publisher, publish_date, category_id, category, shelf_location, status, total_count, available_count) VALUES
(1, 'Java程序设计', '张老师', '978000000001', '清华大学出版社', '2024-03-01', 1, '计算机', 'A-03-02', 'enabled', 5, 4),
(2, '数据库系统概论', '王老师', '978000000002', '高等教育出版社', '2023-09-01', 1, '计算机', 'A-04-01', 'enabled', 3, 3),
(3, '小学教育心理学', '李老师', '978000000003', '教育出版社', '2022-01-01', 4, '教育', 'D-01-01', 'enabled', 6, 6),
(4, '停用图书', '测试作者', '978000000004', '演示出版社', '2022-02-01', 2, '文学', 'B-01-02', 'disabled', 2, 2),
(5, '逾期演示图书', '测试作者', '978000000005', '演示出版社', '2021-05-01', 3, '历史', 'C-02-01', 'enabled', 2, 1),
(6, '倚天屠龙记 第一册', '金庸', '978000000006', '三联书店', '2020-01-01', 2, '文学', 'B-03-01', 'enabled', 3, 2),
(7, '倚天屠龙记 第二册', '金庸', '978000000007', '三联书店', '2020-01-02', 2, '文学', 'B-03-02', 'enabled', 3, 2),
(8, '倚天屠龙记 第三册', '金庸', '978000000008', '三联书店', '2020-01-03', 2, '文学', 'B-03-03', 'enabled', 3, 2),
(9, '倚天屠龙记 第四册', '金庸', '978000000009', '三联书店', '2020-01-04', 2, '文学', 'B-03-04', 'enabled', 3, 3),
(10, '并发边界演示图书', '测试作者', '978000000010', '演示出版社', '2020-01-05', 1, '计算机', 'D-01-05', 'enabled', 1, 1),
(11, '软件工程导论', '李老师', '978000000011', '机械工业出版社', '2022-06-01', 1, '计算机', 'A-05-01', 'enabled', 4, 4),
(12, '现代文学选读', '陈老师', '978000000012', '人民文学出版社', '2021-05-01', 2, '文学', 'B-02-03', 'enabled', 3, 3),
(13, '中国历史简明读本', '赵老师', '978000000013', '中华书局', '2020-10-01', 3, '历史', 'C-01-02', 'enabled', 2, 2),
(14, '射雕英雄传 第一册', '金庸', '978000000014', '三联书店', '2020-02-01', 2, '文学', 'B-04-01', 'enabled', 3, 3),
(15, '射雕英雄传 第二册', '金庸', '978000000015', '三联书店', '2020-02-02', 2, '文学', 'B-04-02', 'enabled', 3, 3),
(16, '射雕英雄传 第三册', '金庸', '978000000016', '三联书店', '2020-02-03', 2, '文学', 'B-04-03', 'enabled', 3, 3),
(17, '射雕英雄传 第四册', '金庸', '978000000017', '三联书店', '2020-02-04', 2, '文学', 'B-04-04', 'enabled', 3, 3),
(18, '神雕侠侣 第一册', '金庸', '978000000018', '三联书店', '2020-03-01', 2, '文学', 'B-05-01', 'enabled', 3, 3),
(19, '神雕侠侣 第二册', '金庸', '978000000019', '三联书店', '2020-03-02', 2, '文学', 'B-05-02', 'enabled', 3, 3),
(20, '神雕侠侣 第三册', '金庸', '978000000020', '三联书店', '2020-03-03', 2, '文学', 'B-05-03', 'enabled', 3, 3),
(21, '神雕侠侣 第四册', '金庸', '978000000021', '三联书店', '2020-03-04', 2, '文学', 'B-05-04', 'enabled', 3, 3);

INSERT INTO shelves (id, shelf_location, remark) VALUES
(1, 'A-03-02', '演示书架'),
(2, 'A-04-01', '演示书架'),
(3, 'D-01-01', '演示书架'),
(4, 'B-01-02', '演示书架'),
(5, 'C-02-01', '演示书架'),
(6, 'B-03-01', '演示书架'),
(7, 'B-03-02', '演示书架'),
(8, 'B-03-03', '演示书架'),
(9, 'B-03-04', '演示书架'),
(10, 'D-01-05', '演示书架'),
(11, 'A-05-01', '演示书架'),
(12, 'B-02-03', '演示书架'),
(13, 'C-01-02', '演示书架'),
(14, 'B-04-01', '演示书架'),
(15, 'B-04-02', '演示书架'),
(16, 'B-04-03', '演示书架'),
(17, 'B-04-04', '演示书架'),
(18, 'B-05-01', '演示书架'),
(19, 'B-05-02', '演示书架'),
(20, 'B-05-03', '演示书架'),
(21, 'B-05-04', '演示书架');

INSERT INTO storage_locations (id, book_id, shelf_location, total_count, available_count, remark) VALUES
(1, 1, 'A-03-02', 5, 4, '默认书架存储'),
(2, 2, 'A-04-01', 3, 3, '默认书架存储'),
(3, 3, 'D-01-01', 6, 6, '默认书架存储'),
(4, 4, 'B-01-02', 2, 2, '默认书架存储'),
(5, 5, 'C-02-01', 2, 1, '默认书架存储'),
(6, 6, 'B-03-01', 3, 2, '倚天屠龙记套书第1册'),
(7, 7, 'B-03-02', 3, 2, '倚天屠龙记套书第2册'),
(8, 8, 'B-03-03', 3, 2, '倚天屠龙记套书第3册'),
(9, 9, 'B-03-04', 3, 3, '倚天屠龙记套书第4册'),
(10, 10, 'D-01-05', 1, 1, '默认书架存储'),
(11, 11, 'A-05-01', 4, 4, '默认书架存储'),
(12, 12, 'B-02-03', 3, 3, '默认书架存储'),
(13, 13, 'C-01-02', 2, 2, '默认书架存储'),
(14, 14, 'B-04-01', 3, 3, '射雕英雄传套书第1册'),
(15, 15, 'B-04-02', 3, 3, '射雕英雄传套书第2册'),
(16, 16, 'B-04-03', 3, 3, '射雕英雄传套书第3册'),
(17, 17, 'B-04-04', 3, 3, '射雕英雄传套书第4册'),
(18, 18, 'B-05-01', 3, 3, '神雕侠侣套书第1册'),
(19, 19, 'B-05-02', 3, 3, '神雕侠侣套书第2册'),
(20, 20, 'B-05-03', 3, 3, '神雕侠侣套书第3册'),
(21, 21, 'B-05-04', 3, 3, '神雕侠侣套书第4册');

INSERT INTO book_copies (id, book_id, copy_code, shelf_location, status, current_user_id, current_borrow_record_id) VALUES
(1, 1, '978000000001-00-000-001', 'A-03-02', 'borrowed', 2, 1),
(2, 1, '978000000001-00-000-002', 'A-03-02', 'available', NULL, NULL),
(3, 1, '978000000001-00-000-003', 'A-03-02', 'available', NULL, NULL),
(4, 1, '978000000001-00-000-004', 'A-03-02', 'available', NULL, NULL),
(5, 1, '978000000001-00-000-005', 'A-03-02', 'available', NULL, NULL),
(6, 2, '978000000002-00-000-001', 'A-04-01', 'available', NULL, NULL),
(7, 2, '978000000002-00-000-002', 'A-04-01', 'available', NULL, NULL),
(8, 2, '978000000002-00-000-003', 'A-04-01', 'available', NULL, NULL),
(9, 3, '978000000003-00-000-001', 'D-01-01', 'available', NULL, NULL),
(10, 3, '978000000003-00-000-002', 'D-01-01', 'available', NULL, NULL),
(11, 3, '978000000003-00-000-003', 'D-01-01', 'available', NULL, NULL),
(12, 3, '978000000003-00-000-004', 'D-01-01', 'available', NULL, NULL),
(13, 3, '978000000003-00-000-005', 'D-01-01', 'available', NULL, NULL),
(14, 3, '978000000003-00-000-006', 'D-01-01', 'available', NULL, NULL),
(15, 4, '978000000004-00-000-001', 'B-01-02', 'available', NULL, NULL),
(16, 4, '978000000004-00-000-002', 'B-01-02', 'available', NULL, NULL),
(17, 5, '978000000005-00-000-001', 'C-02-01', 'borrowed', 4, 3),
(18, 5, '978000000005-00-000-002', 'C-02-01', 'available', NULL, NULL),
(19, 6, '978000000006-03-001-001', 'B-03-01', 'borrowed', 5, 4),
(20, 7, '978000000007-03-002-001', 'B-03-02', 'borrowed', 5, 5),
(21, 8, '978000000008-03-003-001', 'B-03-03', 'borrowed', 5, 6),
(22, 9, '978000000009-03-004-001', 'B-03-04', 'available', NULL, NULL),
(23, 10, '978000000010-00-000-001', 'D-01-05', 'available', NULL, NULL),
(24, 11, '978000000011-00-000-001', 'A-05-01', 'available', NULL, NULL),
(25, 11, '978000000011-00-000-002', 'A-05-01', 'available', NULL, NULL),
(26, 11, '978000000011-00-000-003', 'A-05-01', 'available', NULL, NULL),
(27, 11, '978000000011-00-000-004', 'A-05-01', 'available', NULL, NULL),
(28, 12, '978000000012-00-000-001', 'B-02-03', 'available', NULL, NULL),
(29, 12, '978000000012-00-000-002', 'B-02-03', 'available', NULL, NULL),
(30, 12, '978000000012-00-000-003', 'B-02-03', 'available', NULL, NULL),
(31, 13, '978000000013-00-000-001', 'C-01-02', 'available', NULL, NULL),
(32, 13, '978000000013-00-000-002', 'C-01-02', 'available', NULL, NULL),
(33, 14, '978000000014-01-001-001', 'B-04-01', 'available', NULL, NULL),
(34, 14, '978000000014-01-001-002', 'B-04-01', 'available', NULL, NULL),
(35, 14, '978000000014-01-001-003', 'B-04-01', 'available', NULL, NULL),
(36, 15, '978000000015-01-002-001', 'B-04-02', 'available', NULL, NULL),
(37, 15, '978000000015-01-002-002', 'B-04-02', 'available', NULL, NULL),
(38, 15, '978000000015-01-002-003', 'B-04-02', 'available', NULL, NULL),
(39, 16, '978000000016-01-003-001', 'B-04-03', 'available', NULL, NULL),
(40, 16, '978000000016-01-003-002', 'B-04-03', 'available', NULL, NULL),
(41, 16, '978000000016-01-003-003', 'B-04-03', 'available', NULL, NULL),
(42, 17, '978000000017-01-004-001', 'B-04-04', 'available', NULL, NULL),
(43, 17, '978000000017-01-004-002', 'B-04-04', 'available', NULL, NULL),
(44, 17, '978000000017-01-004-003', 'B-04-04', 'available', NULL, NULL),
(45, 18, '978000000018-02-001-001', 'B-05-01', 'available', NULL, NULL),
(46, 18, '978000000018-02-001-002', 'B-05-01', 'available', NULL, NULL),
(47, 18, '978000000018-02-001-003', 'B-05-01', 'available', NULL, NULL),
(48, 19, '978000000019-02-002-001', 'B-05-02', 'available', NULL, NULL),
(49, 19, '978000000019-02-002-002', 'B-05-02', 'available', NULL, NULL),
(50, 19, '978000000019-02-002-003', 'B-05-02', 'available', NULL, NULL),
(51, 20, '978000000020-02-003-001', 'B-05-03', 'available', NULL, NULL),
(52, 20, '978000000020-02-003-002', 'B-05-03', 'available', NULL, NULL),
(53, 20, '978000000020-02-003-003', 'B-05-03', 'available', NULL, NULL),
(54, 21, '978000000021-02-004-001', 'B-05-04', 'available', NULL, NULL),
(55, 21, '978000000021-02-004-002', 'B-05-04', 'available', NULL, NULL),
(56, 21, '978000000021-02-004-003', 'B-05-04', 'available', NULL, NULL),
(57, 6, '978000000006-03-001-002', 'B-03-01', 'available', NULL, NULL),
(58, 6, '978000000006-03-001-003', 'B-03-01', 'available', NULL, NULL),
(59, 7, '978000000007-03-002-002', 'B-03-02', 'available', NULL, NULL),
(60, 7, '978000000007-03-002-003', 'B-03-02', 'available', NULL, NULL),
(61, 8, '978000000008-03-003-002', 'B-03-03', 'available', NULL, NULL),
(62, 8, '978000000008-03-003-003', 'B-03-03', 'available', NULL, NULL),
(63, 9, '978000000009-03-004-002', 'B-03-04', 'available', NULL, NULL),
(64, 9, '978000000009-03-004-003', 'B-03-04', 'available', NULL, NULL);

INSERT INTO borrow_records (id, user_id, book_id, book_copy_id, storage_location_id, reader_card, reader_name, reader_phone, book_isbn, book_title, book_author, copy_code, copy_shelf_location, shelf_location_snapshot, batch_no, borrow_date, due_date, return_date, status) VALUES
(1, 2, 1, 1, 1, 'R20260001', '张三', '13900000001', '978000000001', 'Java程序设计', '张老师', '978000000001-00-000-001', 'A-03-02', 'A-03-02', 'BR202608100001', DATE_SUB(CURDATE(), INTERVAL 20 DAY), DATE_ADD(CURDATE(), INTERVAL 10 DAY), NULL, 'borrowed'),
(2, 2, 2, 6, 2, 'R20260001', '张三', '13900000001', '978000000002', '数据库系统概论', '王老师', '978000000002-00-000-001', 'A-04-01', 'A-04-01', 'BR202608100001', DATE_SUB(CURDATE(), INTERVAL 40 DAY), DATE_SUB(CURDATE(), INTERVAL 10 DAY), CURDATE(), 'returned'),
(3, 4, 5, 17, 5, 'R20260003', '王五', '13900000003', '978000000005', '逾期演示图书', '测试作者', '978000000005-00-000-001', 'C-02-01', 'C-02-01', 'BR202608100002', DATE_SUB(CURDATE(), INTERVAL 45 DAY), DATE_SUB(CURDATE(), INTERVAL 15 DAY), NULL, 'borrowed'),
(4, 5, 6, 19, 6, 'R20260004', '赵六', '13900000004', '978000000006', '倚天屠龙记 第一册', '金庸', '978000000006-03-001-001', 'B-03-01', 'B-03-01', 'BR202608100003', DATE_SUB(CURDATE(), INTERVAL 3 DAY), DATE_ADD(CURDATE(), INTERVAL 27 DAY), NULL, 'borrowed'),
(5, 5, 7, 20, 7, 'R20260004', '赵六', '13900000004', '978000000007', '倚天屠龙记 第二册', '金庸', '978000000007-03-002-001', 'B-03-02', 'B-03-02', 'BR202608100003', DATE_SUB(CURDATE(), INTERVAL 3 DAY), DATE_ADD(CURDATE(), INTERVAL 27 DAY), NULL, 'borrowed'),
(6, 5, 8, 21, 8, 'R20260004', '赵六', '13900000004', '978000000008', '倚天屠龙记 第三册', '金庸', '978000000008-03-003-001', 'B-03-03', 'B-03-03', 'BR202608100003', DATE_SUB(CURDATE(), INTERVAL 3 DAY), DATE_ADD(CURDATE(), INTERVAL 27 DAY), NULL, 'borrowed');
