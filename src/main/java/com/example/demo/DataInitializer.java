package com.example.demo;

import com.example.demo.entity.Category;
import com.example.demo.entity.Book;
import com.example.demo.entity.BorrowRecord;
import com.example.demo.entity.User;
import com.example.demo.repository.BookRepository;
import com.example.demo.repository.BorrowRecordRepository;
import com.example.demo.repository.CategoryRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.BookCopyService;
import com.example.demo.service.StorageLocationService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.Comparator;

@Component
public class DataInitializer implements CommandLineRunner {
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final BookRepository bookRepository;
    private final BorrowRecordRepository borrowRecordRepository;
    private final BookCopyService bookCopyService;
    private final StorageLocationService storageLocationService;

    public DataInitializer(
            UserRepository userRepository,
            CategoryRepository categoryRepository,
            BookRepository bookRepository,
            BorrowRecordRepository borrowRecordRepository,
            BookCopyService bookCopyService,
            StorageLocationService storageLocationService
    ) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.bookRepository = bookRepository;
        this.borrowRecordRepository = borrowRecordRepository;
        this.bookCopyService = bookCopyService;
        this.storageLocationService = storageLocationService;
    }

    @Override
    public void run(String... args) {
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
        ensureReader("R20260002", "李四", "13900000002", "disabled", "停用读者演示");
        ensureReader("R20260003", "王五", "13900000003", "enabled", "逾期演示读者");
        ensureReader("R20260004", "赵六", "13900000004", "enabled", "借阅上限演示读者");

        normalizeReaderCards();

        Category computer = ensureCategory("计算机", "计算机与软件开发类图书");
        Category literature = ensureCategory("文学", "小说、散文与文学作品");
        Category history = ensureCategory("历史", "历史与文化类图书");
        Category education = ensureCategory("教育", "教材、教辅与教育类图书");

        ensureBook("Java程序设计", "张老师", "978000000001", "清华大学出版社", LocalDate.of(2024, 3, 1), computer, "A-03-02", 5);
        ensureBook("数据库系统概论", "王老师", "978000000002", "高等教育出版社", LocalDate.of(2023, 9, 1), computer, "A-04-01", 3);
        ensureBook("停用图书", "测试作者", "978000000004", "演示出版社", LocalDate.of(2022, 2, 1), literature, "B-01-02", 2, "disabled");
        ensureBook("逾期演示图书", "测试作者", "978000000005", "演示出版社", LocalDate.of(2021, 5, 1), history, "C-02-01", 2);
        ensureBook("上限演示图书1", "测试作者", "978000000006", "演示出版社", LocalDate.of(2020, 1, 1), computer, "D-01-01", 1);
        ensureBook("上限演示图书2", "测试作者", "978000000007", "演示出版社", LocalDate.of(2020, 1, 2), computer, "D-01-02", 1);
        ensureBook("上限演示图书3", "测试作者", "978000000008", "演示出版社", LocalDate.of(2020, 1, 3), computer, "D-01-03", 1);
        ensureBook("库存边界演示图书", "测试作者", "978000000009", "演示出版社", LocalDate.of(2020, 1, 4), computer, "D-01-04", 1);
        ensureBook("并发边界演示图书", "测试作者", "978000000010", "演示出版社", LocalDate.of(2020, 1, 5), computer, "D-01-05", 1);
        ensureBook("软件工程导论", "李老师", "978000000011", "机械工业出版社", LocalDate.of(2022, 6, 1), computer, "A-05-01", 4);
        ensureBook("现代文学选读", "陈老师", "978000000012", "人民文学出版社", LocalDate.of(2021, 5, 1), literature, "B-02-03", 3);
        ensureBook("中国历史简明读本", "赵老师", "978000000013", "中华书局", LocalDate.of(2020, 10, 1), history, "C-01-02", 2);
        ensureBook("小学教育心理学", "李老师", "978000000003", "教育出版社", LocalDate.of(2022, 1, 1), education, "D-01-01", 6);

        fillBookCategoryIds();
        fillBookShelves();
        bookRepository.findAll().forEach(storageLocationService::syncPrimaryStorage);
        bookRepository.findAll().forEach(bookCopyService::syncCopies);
        bookRepository.findAll().forEach(this::normalizeBorrowRecordCopies);
        borrowRecordRepository.findAll().forEach(this::fillBorrowRecordSnapshot);
    }

    private Category ensureCategory(String name, String description) {
        return categoryRepository.findByName(name).orElseGet(() -> {
            Category category = new Category();
            category.setName(name);
            category.setDescription(description);
            category.setCreatedAt(LocalDateTime.now());
            return categoryRepository.save(category);
        });
    }

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
        if (bookRepository.findByIsbn(isbn).isPresent()) {
            return;
        }

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
        bookRepository.save(book);
    }

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

    private void fillBookShelves() {
        bookRepository.findAll().forEach(book -> {
            if (hasText(book.getShelfLocation())) {
                return;
            }

            book.setShelfLocation(defaultShelfFor(book));
            bookRepository.save(book);
        });
    }

    private String defaultShelfFor(Book book) {
        if ("978000000001".equals(book.getIsbn())) return "A-03-02";
        if ("978000000002".equals(book.getIsbn())) return "A-04-01";
        if ("978000000011".equals(book.getIsbn())) return "A-05-01";
        if ("978000000012".equals(book.getIsbn())) return "B-02-03";
        if ("978000000013".equals(book.getIsbn())) return "C-01-02";
        if ("978000000003".equals(book.getIsbn())) return "D-01-01";

        String category = book.getCategory() == null ? "" : book.getCategory().trim();
        return switch (category) {
            case "计算机" -> "A-01-01";
            case "文学" -> "B-01-01";
            case "历史" -> "C-01-01";
            case "教育" -> "D-01-01";
            default -> "Z-01-01";
        };
    }

    private void fillBorrowRecordSnapshot(BorrowRecord record) {
        boolean changed = false;

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

        Book book = bookRepository.findById(record.getBookId()).orElse(null);
        if (!hasText(record.getBookIsbn()) || !hasText(record.getBookTitle()) || !hasText(record.getBookAuthor())) {
            if (book != null) {
                record.setBookIsbn(book.getIsbn());
                record.setBookTitle(book.getTitle());
                record.setBookAuthor(book.getAuthor());
                changed = true;
            }
        }

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

    private void normalizeBorrowRecordCopies(Book book) {
        var records = borrowRecordRepository.findByBookIdOrderByBorrowDateAscIdAsc(book.getId());
        int serialIndex = 0;
        int copyCount = book.getTotalCount() == null || book.getTotalCount() <= 0 ? 1 : book.getTotalCount();
        String codePrefix = hasText(book.getIsbn()) ? book.getIsbn().trim() : "BOOK" + book.getId();

        for (BorrowRecord record : records) {
            if (!"returned".equals(record.getStatus()) || record.getCreatedAt() != null) {
                continue;
            }

            int serialNumber = serialIndex % copyCount + 1;
            serialIndex++;

            String copyCode = codePrefix + "-" + String.format("%03d", serialNumber);
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

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private void normalizeReaderCards() {
        userRepository.findByRole("reader").stream()
                .filter(user -> !isValidReaderCard(user.getUsername()))
                .sorted(Comparator.comparing(User::getId))
                .forEach(user -> {
                    String oldCard = user.getUsername();
                    String newCard = nextReaderCard();
                    user.setUsername(newCard);
                    userRepository.save(user);
                    borrowRecordRepository.findByUserIdOrderByIdDesc(user.getId()).forEach(record -> {
                        if (!hasText(record.getReaderCard()) || oldCard.equals(record.getReaderCard())) {
                            record.setReaderCard(newCard);
                            borrowRecordRepository.save(record);
                        }
                    });
                });
    }

    private boolean isValidReaderCard(String value) {
        return value != null && value.matches("^R\\d{8}$");
    }

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
            card = prefix + String.format("%04d", nextNumber++);
        } while (userRepository.existsByUsername(card));
        return card;
    }
}
