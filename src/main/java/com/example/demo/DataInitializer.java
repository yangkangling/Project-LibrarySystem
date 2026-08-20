package com.example.demo;

import com.example.demo.config.LibraryProperties;
import com.example.demo.entity.Category;
import com.example.demo.entity.Book;
import com.example.demo.entity.BookCopy;
import com.example.demo.entity.BorrowRecord;
import com.example.demo.entity.User;
import com.example.demo.repository.BookCopyRepository;
import com.example.demo.repository.BookRepository;
import com.example.demo.repository.BorrowRecordRepository;
import com.example.demo.repository.CategoryRepository;
import com.example.demo.repository.StorageLocationRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.BookCopyService;
import com.example.demo.service.StorageLocationService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.Comparator;
import java.util.List;

// 启动时补齐演示基础数据。
@Component
public class DataInitializer implements CommandLineRunner {
    // 套书演示数据每册默认准备的馆藏数量。
    private static final int SERIES_TOTAL_COUNT = 3;
    // 用户表访问，用于创建管理员和读者。
    private final UserRepository userRepository;
    // 分类表访问，用于补齐默认分类。
    private final CategoryRepository categoryRepository;
    // 图书表访问，用于创建和修正图书主数据。
    private final BookRepository bookRepository;
    // 借阅记录表访问，用于补齐历史快照。
    private final BorrowRecordRepository borrowRecordRepository;
    // 单册表访问，用于同步每一本可借副本。
    private final BookCopyRepository bookCopyRepository;
    // 书架表访问，用于同步馆藏位置。
    private final StorageLocationRepository storageLocationRepository;
    // 单册服务，负责按馆藏数维护副本。
    private final BookCopyService bookCopyService;
    // 书架服务，负责维护图书主书架。
    private final StorageLocationService storageLocationService;
    // 系统容量和启动修复配置。
    private final LibraryProperties libraryProperties;

    // 构造注入需要用到的仓库和服务。
    public DataInitializer(
            UserRepository userRepository,
            CategoryRepository categoryRepository,
            BookRepository bookRepository,
            BorrowRecordRepository borrowRecordRepository,
            BookCopyRepository bookCopyRepository,
            StorageLocationRepository storageLocationRepository,
            BookCopyService bookCopyService,
            StorageLocationService storageLocationService,
            LibraryProperties libraryProperties
    ) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.bookRepository = bookRepository;
        this.borrowRecordRepository = borrowRecordRepository;
        this.bookCopyRepository = bookCopyRepository;
        this.storageLocationRepository = storageLocationRepository;
        this.bookCopyService = bookCopyService;
        this.storageLocationService = storageLocationService;
        this.libraryProperties = libraryProperties;
    }

    @Override
    public void run(String... args) {
        // 首次启动时创建默认管理员。
        if (userRepository.findByUsername("admin").isEmpty()) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword("123456");
            admin.setRealName("Administrator");
            admin.setRole("admin");
            admin.setStatus("enabled");
            admin.setCreatedAt(LocalDateTime.now());
            userRepository.save(admin);
        }

        // 首次启动时创建默认读者张三。
        if (userRepository.findByUsername("R20260001").isEmpty()) {
            User reader = new User();
            reader.setUsername("R20260001");
            reader.setPassword("123456");
            reader.setRealName("张三");
            reader.setRole("reader");
            reader.setPhone("13900000001");
            reader.setStatus("enabled");
            reader.setRemark("默认读者账号");
            reader.setCreatedAt(LocalDateTime.now());
            userRepository.save(reader);
        }
        // 补齐几个演示读者，覆盖停用、逾期和借阅上限场景。
        ensureReader("R20260002", "李四", "13900000002", "disabled", "停用读者演示");
        ensureReader("R20260003", "王五", "13900000003", "enabled", "逾期演示读者");
        ensureReader("R20260004", "赵六", "13900000004", "enabled", "借阅上限演示读者");
        // 历史读者证号修复仅在配置开启时执行，避免大型部署每次启动扫描读者和借阅记录。
        if (libraryProperties.isRepairLegacyDataOnStartup()) {
            normalizeReaderCards();
        }

        // 准备系统默认图书分类。
        Category computer = ensureCategory("计算机", "计算机与软件开发类图书");
        Category literature = ensureCategory("文学", "小说、散文与文学作品");
        Category history = ensureCategory("历史", "历史与文化类图书");
        Category education = ensureCategory("教育", "教材、教辅与教育类图书");

        // 创建单本演示图书。
        ensureBook("Java程序设计", "张老师", "978000000001", "清华大学出版社", LocalDate.of(2024, 3, 1), computer, "A-03-02", 5);
        ensureBook("数据库系统概论", "王老师", "978000000002", "高等教育出版社", LocalDate.of(2023, 9, 1), computer, "A-04-01", 3);
        ensureBook("停用图书", "测试作者", "978000000004", "演示出版社", LocalDate.of(2022, 2, 1), literature, "B-01-02", 2, "disabled");
        ensureBook("逾期演示图书", "测试作者", "978000000005", "演示出版社", LocalDate.of(2021, 5, 1), history, "C-02-01", 2);
        // 创建套书演示数据，每一册都是独立图书但共享套号。
        ensureBook("射雕英雄传 第一册", "金庸", "978000000014", "三联书店", LocalDate.of(2020, 2, 1), literature, "B-04-01", SERIES_TOTAL_COUNT);
        ensureBook("射雕英雄传 第二册", "金庸", "978000000015", "三联书店", LocalDate.of(2020, 2, 2), literature, "B-04-02", SERIES_TOTAL_COUNT);
        ensureBook("射雕英雄传 第三册", "金庸", "978000000016", "三联书店", LocalDate.of(2020, 2, 3), literature, "B-04-03", SERIES_TOTAL_COUNT);
        ensureBook("射雕英雄传 第四册", "金庸", "978000000017", "三联书店", LocalDate.of(2020, 2, 4), literature, "B-04-04", SERIES_TOTAL_COUNT);
        ensureBook("神雕侠侣 第一册", "金庸", "978000000018", "三联书店", LocalDate.of(2020, 3, 1), literature, "B-05-01", SERIES_TOTAL_COUNT);
        ensureBook("神雕侠侣 第二册", "金庸", "978000000019", "三联书店", LocalDate.of(2020, 3, 2), literature, "B-05-02", SERIES_TOTAL_COUNT);
        ensureBook("神雕侠侣 第三册", "金庸", "978000000020", "三联书店", LocalDate.of(2020, 3, 3), literature, "B-05-03", SERIES_TOTAL_COUNT);
        ensureBook("神雕侠侣 第四册", "金庸", "978000000021", "三联书店", LocalDate.of(2020, 3, 4), literature, "B-05-04", SERIES_TOTAL_COUNT);
        ensureBook("倚天屠龙记 第一册", "金庸", "978000000006", "三联书店", LocalDate.of(2020, 1, 1), literature, "B-03-01", SERIES_TOTAL_COUNT);
        ensureBook("倚天屠龙记 第二册", "金庸", "978000000007", "三联书店", LocalDate.of(2020, 1, 2), literature, "B-03-02", SERIES_TOTAL_COUNT);
        ensureBook("倚天屠龙记 第三册", "金庸", "978000000008", "三联书店", LocalDate.of(2020, 1, 3), literature, "B-03-03", SERIES_TOTAL_COUNT);
        ensureBook("倚天屠龙记 第四册", "金庸", "978000000009", "三联书店", LocalDate.of(2020, 1, 4), literature, "B-03-04", SERIES_TOTAL_COUNT);
        // 创建更多普通图书，方便分页和借阅流程演示。
        ensureBook("并发边界演示图书", "测试作者", "978000000010", "演示出版社", LocalDate.of(2020, 1, 5), computer, "D-01-05", 1);
        ensureBook("软件工程导论", "李老师", "978000000011", "机械工业出版社", LocalDate.of(2022, 6, 1), computer, "A-05-01", 4);
        ensureBook("现代文学选读", "陈老师", "978000000012", "人民文学出版社", LocalDate.of(2021, 5, 1), literature, "B-02-03", 3);
        ensureBook("中国历史简明读本", "赵老师", "978000000013", "中华书局", LocalDate.of(2020, 10, 1), history, "C-01-02", 2);
        ensureBook("小学教育心理学", "李老师", "978000000003", "教育出版社", LocalDate.of(2022, 1, 1), education, "D-01-01", 6);

        // 只修正固定演示套书，不扫描全库。
        normalizeSeriesBooks(literature);
        if (libraryProperties.isRepairLegacyDataOnStartup()) {
            // 启动全量修复用于老数据迁移，大型部署建议只开启一次。
            fillBookCategoryIds();
            fillBookShelves();
            bookRepository.findAll().forEach(storageLocationService::syncPrimaryStorage);
            bookRepository.findAll().forEach(bookCopyService::syncCopies);
            bookRepository.findAll().forEach(this::normalizeBorrowRecordCopies);
            bookRepository.findAll().forEach(bookCopyService::syncCopies);
            normalizeStandaloneBooks();
            bookRepository.findAll().forEach(storageLocationService::syncPrimaryStorage);
            storageLocationService.syncShelvesFromExistingLocations();
            borrowRecordRepository.findAll().forEach(this::fillBorrowRecordSnapshot);
        }
    }

    // 分类不存在时创建，存在时直接复用。
    private Category ensureCategory(String name, String description) {
        return categoryRepository.findByName(name).orElseGet(() -> {
            Category category = new Category();
            category.setName(name);
            category.setDescription(description);
            category.setCreatedAt(LocalDateTime.now());
            return categoryRepository.save(category);
        });
    }

    // 读者不存在时创建，避免每次启动重复插入。
    private void ensureReader(String username, String realName, String phone, String status, String remark) {
        if (userRepository.findByUsername(username).isPresent()) {
            return;
        }
        User reader = new User();
        reader.setUsername(username);
        reader.setPassword("123456");
        reader.setRealName(realName);
        reader.setRole("reader");
        reader.setPhone(phone);
        reader.setStatus(status);
        reader.setRemark(remark);
        reader.setCreatedAt(LocalDateTime.now());
        userRepository.save(reader);
    }

    // 默认创建启用状态图书。
    private void ensureBook(
            String title,
            String author,
            String isbn,
            String publisher,
            LocalDate publishDate,
            Category category,
            String shelfLocation,
            int totalCount
    ) {
        ensureBook(title, author, isbn, publisher, publishDate, category, shelfLocation, totalCount, "enabled");
    }

    // 图书不存在时创建，并写入基础馆藏数量。
    private void ensureBook(
            String title,
            String author,
            String isbn,
            String publisher,
            LocalDate publishDate,
            Category category,
            String shelfLocation,
            int totalCount,
            String status
    ) {
        // ISBN 已存在说明图书已经初始化过。
        if (bookRepository.findByIsbn(isbn).isPresent()) {
            return;
        }

        // 写入图书主表基础信息。
        Book book = new Book();
        book.setTitle(title);
        book.setAuthor(author);
        book.setIsbn(isbn);
        book.setPublisher(publisher);
        book.setPublishDate(publishDate);
        book.setCategoryId(category.getId());
        book.setCategory(category.getName());
        book.setShelfLocation(shelfLocation);
        book.setStatus(status);
        book.setTotalCount(totalCount);
        book.setAvailableCount(totalCount);
        book.setCreatedAt(LocalDateTime.now());
        Book savedBook = bookRepository.save(book);
        storageLocationService.syncPrimaryStorage(savedBook);
        bookCopyService.syncCopies(savedBook);
    }

    // 套书统一维护套号：射雕 01、神雕 02、倚天 03；每册仍是独立图书，可单独借还。
    private void normalizeSeriesBooks(Category literature) {
        normalizeSeriesBook("978000000014", "01", 1, "射雕英雄传 第一册", "B-04-01", literature);
        normalizeSeriesBook("978000000015", "01", 2, "射雕英雄传 第二册", "B-04-02", literature);
        normalizeSeriesBook("978000000016", "01", 3, "射雕英雄传 第三册", "B-04-03", literature);
        normalizeSeriesBook("978000000017", "01", 4, "射雕英雄传 第四册", "B-04-04", literature);
        normalizeSeriesBook("978000000018", "02", 1, "神雕侠侣 第一册", "B-05-01", literature);
        normalizeSeriesBook("978000000019", "02", 2, "神雕侠侣 第二册", "B-05-02", literature);
        normalizeSeriesBook("978000000020", "02", 3, "神雕侠侣 第三册", "B-05-03", literature);
        normalizeSeriesBook("978000000021", "02", 4, "神雕侠侣 第四册", "B-05-04", literature);
        normalizeSeriesBook("978000000006", "03", 1, "倚天屠龙记 第一册", "B-03-01", literature);
        normalizeSeriesBook("978000000007", "03", 2, "倚天屠龙记 第二册", "B-03-02", literature);
        normalizeSeriesBook("978000000008", "03", 3, "倚天屠龙记 第三册", "B-03-03", literature);
        normalizeSeriesBook("978000000009", "03", 4, "倚天屠龙记 第四册", "B-03-04", literature);
    }

    private void normalizeSeriesBook(String isbn, String seriesCode, int volume, String title, String shelfLocation, Category category) {
        bookRepository.findByIsbn(isbn).ifPresent(book -> {
            boolean bookChanged = false;
            // 修正套书的书名、作者、分类和书架。
            if (textChanged(book.getTitle(), title)) {
                book.setTitle(title);
                bookChanged = true;
            }
            if (textChanged(book.getAuthor(), "金庸")) {
                book.setAuthor("金庸");
                bookChanged = true;
            }
            if (textChanged(book.getPublisher(), "三联书店")) {
                book.setPublisher("三联书店");
                bookChanged = true;
            }
            if (category != null && !category.getId().equals(book.getCategoryId())) {
                book.setCategoryId(category.getId());
                bookChanged = true;
            }
            if (category != null && textChanged(book.getCategory(), category.getName())) {
                book.setCategory(category.getName());
                bookChanged = true;
            }
            if (textChanged(book.getShelfLocation(), shelfLocation)) {
                book.setShelfLocation(shelfLocation);
                bookChanged = true;
            }
            // 可借数量按副本借出状态重新计算。
            int borrowedCount = (int) bookCopyRepository.countByBookIdAndStatus(book.getId(), "borrowed");
            int availableCount = Math.max(0, SERIES_TOTAL_COUNT - borrowedCount);
            if (safeInt(book.getTotalCount()) != SERIES_TOTAL_COUNT) {
                book.setTotalCount(SERIES_TOTAL_COUNT);
                bookChanged = true;
            }
            if (safeInt(book.getAvailableCount()) != availableCount) {
                book.setAvailableCount(availableCount);
                bookChanged = true;
            }
            if (bookChanged) {
                bookRepository.save(book);
            }

            // 修正馆藏位置表中的书架和备注。
            storageLocationRepository.findFirstByBookIdOrderByIdAsc(book.getId()).ifPresent(storage -> {
                boolean storageChanged = false;
                if (textChanged(storage.getShelfLocation(), shelfLocation)) {
                    storage.setShelfLocation(shelfLocation);
                    storageChanged = true;
                }
                String remark = seriesName(title) + "套书第" + volume + "册";
                if (textChanged(storage.getRemark(), remark)) {
                    storage.setRemark(remark);
                    storageChanged = true;
                }
                if (storageChanged) {
                    storage.setUpdatedAt(LocalDateTime.now());
                    storageLocationRepository.save(storage);
                }
            });

            // 修正单册编号，格式保留 ISBN、套号、册号和流水号。
            List<BookCopy> copies = bookCopyRepository.findByBookIdOrderByCopyCodeAsc(book.getId());
            for (int index = 0; index < copies.size(); index++) {
                BookCopy copy = copies.get(index);
                String copyCode = seriesCopyCode(isbn, seriesCode, volume, index + 1);
                boolean copyChanged = false;
                if (textChanged(copy.getCopyCode(), copyCode)) {
                    copy.setCopyCode(copyCode);
                    copyChanged = true;
                }
                if (textChanged(copy.getShelfLocation(), shelfLocation)) {
                    copy.setShelfLocation(shelfLocation);
                    copyChanged = true;
                }
                if (copyChanged) {
                    copy.setUpdatedAt(LocalDateTime.now());
                    bookCopyRepository.save(copy);
                }
            }

            // 修正历史借阅记录中的图书快照和单册快照。
            borrowRecordRepository.findByBookIdOrderByIdDesc(book.getId()).forEach(record -> {
                boolean recordChanged = false;
                if (textChanged(record.getBookTitle(), title)) {
                    record.setBookTitle(title);
                    recordChanged = true;
                }
                if (textChanged(record.getBookAuthor(), "金庸")) {
                    record.setBookAuthor("金庸");
                    recordChanged = true;
                }
                if (textChanged(record.getBookIsbn(), isbn)) {
                    record.setBookIsbn(isbn);
                    recordChanged = true;
                }
                String copyCode = copyCodeForRecord(copies, record.getBookCopyId(), isbn, seriesCode, volume);
                if (textChanged(record.getCopyCode(), copyCode)) {
                    record.setCopyCode(copyCode);
                    recordChanged = true;
                }
                if (textChanged(record.getCopyShelfLocation(), shelfLocation)) {
                    record.setCopyShelfLocation(shelfLocation);
                    recordChanged = true;
                }
                if (textChanged(record.getShelfLocationSnapshot(), shelfLocation)) {
                    record.setShelfLocationSnapshot(shelfLocation);
                    recordChanged = true;
                }
                if (recordChanged) {
                    borrowRecordRepository.save(record);
                }
            });
        });
    }

    // 根据借阅记录绑定的副本取编号，缺失时回退到第一本。
    private String copyCodeForRecord(List<BookCopy> copies, Long bookCopyId, String isbn, String seriesCode, int volume) {
        if (bookCopyId != null) {
            for (BookCopy copy : copies) {
                if (bookCopyId.equals(copy.getId()) && hasText(copy.getCopyCode())) {
                    return copy.getCopyCode();
                }
            }
        }
        return seriesCopyCode(isbn, seriesCode, volume, 1);
    }

    // 套书内部副本号格式：ISBN-套号-册号-流水号，页面展示会隐藏最后流水号。
    private String seriesCopyCode(String isbn, String seriesCode, int volume, int serialNumber) {
        return isbn + "-" + seriesCode + "-" + String.format("%03d", volume) + "-" + String.format("%03d", serialNumber);
    }

    // 从“书名 第一册”中截出套书名。
    private String seriesName(String title) {
        int index = title.indexOf(" ");
        return index > 0 ? title.substring(0, index) : title;
    }

    // 普通书没有套号和册号，占位为 00-000；页面展示为 ISBN-流水号。
    private void normalizeStandaloneBooks() {
        bookRepository.findAll().stream()
                .filter(book -> !isSeriesIsbn(book.getIsbn()))
                .forEach(this::normalizeStandaloneBookCopies);
    }

    private void normalizeStandaloneBookCopies(Book book) {
        List<BookCopy> copies = bookCopyRepository.findByBookIdOrderByCopyCodeAsc(book.getId());
        // 普通书也要按 ISBN 和流水号补齐单册编号。
        for (int index = 0; index < copies.size(); index++) {
            BookCopy copy = copies.get(index);
            String copyCode = standaloneCopyCode(book, index + 1);
            boolean copyChanged = false;
            if (textChanged(copy.getCopyCode(), copyCode)) {
                copy.setCopyCode(copyCode);
                copyChanged = true;
            }
            if (textChanged(copy.getShelfLocation(), book.getShelfLocation())) {
                copy.setShelfLocation(book.getShelfLocation());
                copyChanged = true;
            }
            if (copyChanged) {
                copy.setUpdatedAt(LocalDateTime.now());
                bookCopyRepository.save(copy);
            }
        }

        // 普通书的历史借阅记录也同步单册编号和书架快照。
        borrowRecordRepository.findByBookIdOrderByIdDesc(book.getId()).forEach(record -> {
            boolean recordChanged = false;
            String copyCode = standaloneCopyCodeForRecord(copies, record.getBookCopyId(), book);
            if (textChanged(record.getCopyCode(), copyCode)) {
                record.setCopyCode(copyCode);
                recordChanged = true;
            }
            if (textChanged(record.getCopyShelfLocation(), book.getShelfLocation())) {
                record.setCopyShelfLocation(book.getShelfLocation());
                recordChanged = true;
            }
            if (textChanged(record.getShelfLocationSnapshot(), book.getShelfLocation())) {
                record.setShelfLocationSnapshot(book.getShelfLocation());
                recordChanged = true;
            }
            if (recordChanged) {
                borrowRecordRepository.save(record);
            }
        });
    }

    // 普通书借阅记录优先使用真实副本编号。
    private String standaloneCopyCodeForRecord(List<BookCopy> copies, Long bookCopyId, Book book) {
        if (bookCopyId != null) {
            for (BookCopy copy : copies) {
                if (bookCopyId.equals(copy.getId()) && hasText(copy.getCopyCode())) {
                    return copy.getCopyCode();
                }
            }
        }
        return standaloneCopyCode(book, 1);
    }

    // 普通书内部副本号格式：ISBN-00-000-流水号。
    private String standaloneCopyCode(Book book, int serialNumber) {
        String isbn = hasText(book.getIsbn()) ? book.getIsbn().trim() : "BOOK" + book.getId();
        return isbn + "-00-000-" + String.format("%03d", serialNumber);
    }

    // 判断当前 ISBN 是否属于套书演示数据。
    private boolean isSeriesIsbn(String isbn) {
        if (isbn == null) {
            return false;
        }
        return "978000000006".equals(isbn)
                || "978000000007".equals(isbn)
                || "978000000008".equals(isbn)
                || "978000000009".equals(isbn)
                || "978000000014".equals(isbn)
                || "978000000015".equals(isbn)
                || "978000000016".equals(isbn)
                || "978000000017".equals(isbn)
                || "978000000018".equals(isbn)
                || "978000000019".equals(isbn)
                || "978000000020".equals(isbn)
                || "978000000021".equals(isbn);
    }

    // 比较文本是否需要更新。
    private boolean textChanged(String current, String target) {
        return target != null && !target.equals(current);
    }

    // 空数字按 0 处理，避免空指针。
    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    // 根据分类名称反填分类 id，兼容旧数据。
    private void fillBookCategoryIds() {
        bookRepository.findAll().forEach(book -> {
            if (book.getCategoryId() == null && hasText(book.getCategory())) {
                categoryRepository.findByName(book.getCategory().trim()).ifPresent(category -> {
                    book.setCategoryId(category.getId());
                    book.setCategory(category.getName());
                    bookRepository.save(book);
                });
            }
        });
    }

    // 没有书架位置的旧图书补默认位置。
    private void fillBookShelves() {
        bookRepository.findAll().forEach(book -> {
            if (hasText(book.getShelfLocation())) {
                return;
            }

            book.setShelfLocation(defaultShelfFor(book));
            bookRepository.save(book);
        });
    }

    // 按 ISBN 或分类给旧图书推导默认书架。
    private String defaultShelfFor(Book book) {
        if ("978000000001".equals(book.getIsbn())) return "A-03-02";
        if ("978000000002".equals(book.getIsbn())) return "A-04-01";
        if ("978000000011".equals(book.getIsbn())) return "A-05-01";
        if ("978000000012".equals(book.getIsbn())) return "B-02-03";
        if ("978000000013".equals(book.getIsbn())) return "C-01-02";
        if ("978000000003".equals(book.getIsbn())) return "D-01-01";

        String category = book.getCategory() == null ? "" : book.getCategory().trim();
        if ("计算机".equals(category)) return "A-01-01";
        if ("文学".equals(category)) return "B-01-01";
        if ("历史".equals(category)) return "C-01-01";
        if ("教育".equals(category)) return "D-01-01";
        return "Z-01-01";
    }

    // 补齐借阅记录中的读者、图书、单册和书架快照。
    private void fillBorrowRecordSnapshot(BorrowRecord record) {
        boolean changed = false;

        // 读者快照用于历史记录展示，避免用户信息变更后丢失当时信息。
        User user = userRepository.findById(record.getUserId()).orElse(null);
        if (user != null) {
            if (!hasText(record.getReaderCard()) || !record.getReaderCard().equals(user.getUsername())) {
                record.setReaderCard(user.getUsername());
                changed = true;
            }
            if (!hasText(record.getReaderName())) {
                record.setReaderName(user.getRealName());
                changed = true;
            }
            if (!hasText(record.getReaderPhone())) {
                record.setReaderPhone(user.getPhone());
                changed = true;
            }
        }

        // 图书快照用于借阅记录页面直接展示。
        Book book = bookRepository.findById(record.getBookId()).orElse(null);
        if (!hasText(record.getBookIsbn()) || !hasText(record.getBookTitle()) || !hasText(record.getBookAuthor())) {
            if (book != null) {
                record.setBookIsbn(book.getIsbn());
                record.setBookTitle(book.getTitle());
                record.setBookAuthor(book.getAuthor());
                changed = true;
            }
        }

        // 单册和书架快照缺失时，从当前图书信息自动补上。
        if (book != null && (!hasText(record.getCopyCode()) || !hasText(record.getCopyShelfLocation()))) {
            changed = bookCopyService.attachExistingBorrowRecord(book, record) || changed;
        }
        if (book != null && (!hasText(record.getShelfLocationSnapshot()) || record.getStorageLocationId() == null)) {
            changed = storageLocationService.attachExistingBorrowRecord(book, record) || changed;
        }

        if (changed) {
            borrowRecordRepository.save(record);
        }
    }

    // 给早期没有副本绑定的已还记录分配展示用单册编号。
    private void normalizeBorrowRecordCopies(Book book) {
        var records = borrowRecordRepository.findByBookIdOrderByBorrowDateAscIdAsc(book.getId());
        int serialIndex = 0;
        int copyCount = book.getTotalCount() == null || book.getTotalCount() <= 0 ? 1 : book.getTotalCount();

        for (BorrowRecord record : records) {
            // 只处理早期生成的已还记录，当前未还记录不在这里改。
            if (!"returned".equals(record.getStatus()) || record.getCreatedAt() != null) {
                continue;
            }

            // 按馆藏数量循环分配第几本。
            int serialNumber = serialIndex % copyCount + 1;
            serialIndex++;

            String copyCode = standaloneCopyCode(book, serialNumber);
            boolean changed = false;

            if (!copyCode.equals(record.getCopyCode())) {
                record.setCopyCode(copyCode);
                changed = true;
            }
            if (!hasText(record.getCopyShelfLocation()) || !record.getCopyShelfLocation().equals(book.getShelfLocation())) {
                record.setCopyShelfLocation(book.getShelfLocation());
                changed = true;
            }
            if (!hasText(record.getShelfLocationSnapshot()) || !record.getShelfLocationSnapshot().equals(book.getShelfLocation())) {
                record.setShelfLocationSnapshot(book.getShelfLocation());
                changed = true;
            }

            if (changed) {
                borrowRecordRepository.save(record);
            }
        }
    }

    // 判断字符串是否有有效内容。
    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    // 把不规范读者账号改成 R年份四位序号。
    private void normalizeReaderCards() {
        userRepository.findByRole("reader").stream()
                .filter(user -> !isValidReaderCard(user.getUsername()))
                .sorted(Comparator.comparing(User::getId))
                .forEach(user -> {
                    String oldCard = user.getUsername();
                    String newCard = nextReaderCard();
                    // 用户表更新新证号。
                    user.setUsername(newCard);
                    userRepository.save(user);
                    // 历史借阅记录同步读者证快照。
                    borrowRecordRepository.findByUserIdOrderByIdDesc(user.getId()).forEach(record -> {
                        if (!hasText(record.getReaderCard()) || oldCard.equals(record.getReaderCard())) {
                            record.setReaderCard(newCard);
                            borrowRecordRepository.save(record);
                        }
                    });
                });
    }

    // 合法读者证格式：R + 8 位数字。
    private boolean isValidReaderCard(String value) {
        return value != null && value.matches("^R\\d{8}$");
    }

    // 生成当前年份下一个可用读者证号。
    private String nextReaderCard() {
        String prefix = "R" + Year.now().getValue();
        int nextNumber = userRepository.findByRoleAndUsernameStartingWithOrderByUsernameDesc("reader", prefix).stream()
                .map(User::getUsername)
                .filter(this::isValidReaderCard)
                .mapToInt(card -> Integer.parseInt(card.substring(prefix.length())))
                .max()
                .orElse(0) + 1;

        String card;
        do {
            // 如果编号已经存在就继续往后找。
            card = prefix + String.format("%04d", nextNumber++);
        } while (userRepository.existsByUsername(card));
        return card;
    }
}
