package com.yangkangling.library.service;

import com.yangkangling.library.entity.Book;
import com.yangkangling.library.entity.Category;
import com.yangkangling.library.entity.User;
import com.yangkangling.library.repository.BookCopyRepository;
import com.yangkangling.library.repository.BookRepository;
import com.yangkangling.library.repository.BorrowRecordRepository;
import com.yangkangling.library.repository.CategoryRepository;
import com.yangkangling.library.repository.StorageLocationRepository;
import com.yangkangling.library.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class BorrowServiceConcurrencyTest {
    private final BorrowService borrowService;
    private final StorageLocationService storageLocationService;
    private final BookCopyService bookCopyService;
    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final CategoryRepository categoryRepository;
    private final BorrowRecordRepository borrowRecordRepository;
    private final BookCopyRepository bookCopyRepository;
    private final StorageLocationRepository storageLocationRepository;
    private final TransactionTemplate transactionTemplate;

    private Long bookId;
    private Long categoryId;
    private final java.util.List<Long> readerIds = new java.util.ArrayList<>();

    @Autowired
    BorrowServiceConcurrencyTest(
            BorrowService borrowService,
            StorageLocationService storageLocationService,
            BookCopyService bookCopyService,
            UserRepository userRepository,
            BookRepository bookRepository,
            CategoryRepository categoryRepository,
            BorrowRecordRepository borrowRecordRepository,
            BookCopyRepository bookCopyRepository,
            StorageLocationRepository storageLocationRepository,
            TransactionTemplate transactionTemplate
    ) {
        this.borrowService = borrowService;
        this.storageLocationService = storageLocationService;
        this.bookCopyService = bookCopyService;
        this.userRepository = userRepository;
        this.bookRepository = bookRepository;
        this.categoryRepository = categoryRepository;
        this.borrowRecordRepository = borrowRecordRepository;
        this.bookCopyRepository = bookCopyRepository;
        this.storageLocationRepository = storageLocationRepository;
        this.transactionTemplate = transactionTemplate;
    }

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            if (bookId != null) {
                borrowRecordRepository.findByBookIdOrderByIdDesc(bookId).forEach(borrowRecordRepository::delete);
                bookCopyRepository.deleteByBookId(bookId);
                storageLocationRepository.deleteByBookId(bookId);
                bookRepository.deleteById(bookId);
            }
            readerIds.forEach(userRepository::deleteById);
            if (categoryId != null) {
                categoryRepository.deleteById(categoryId);
            }
        });
    }

    @Test
    void onlyOneReaderCanBorrowTheLastAvailableCopy() throws Exception {
        TestFixture fixture = createFixture();
        CountDownLatch startLine = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Callable<Boolean> firstBorrow = borrowAttempt(fixture.firstReaderId(), fixture.bookId(), startLine);
        Callable<Boolean> secondBorrow = borrowAttempt(fixture.secondReaderId(), fixture.bookId(), startLine);
        Future<Boolean> first = executor.submit(firstBorrow);
        Future<Boolean> second = executor.submit(secondBorrow);

        startLine.countDown();

        int successCount = (first.get() ? 1 : 0) + (second.get() ? 1 : 0);
        executor.shutdownNow();

        Book reloaded = bookRepository.findById(fixture.bookId()).orElseThrow();
        assertEquals(1, successCount);
        assertEquals(0, reloaded.getAvailableCount());
        assertEquals(1, borrowRecordRepository.countByBookIdAndStatus(fixture.bookId(), "borrowed"));
        assertEquals(1, bookCopyRepository.countByBookIdAndStatus(fixture.bookId(), "borrowed"));
        assertEquals(0, storageLocationRepository.findFirstByBookIdOrderByIdAsc(fixture.bookId()).orElseThrow().getAvailableCount());
    }

    private Callable<Boolean> borrowAttempt(Long readerId, Long targetBookId, CountDownLatch startLine) {
        return () -> {
            startLine.await();
            try {
                borrowService.borrowBook(readerId, targetBookId, LocalDate.now().plusDays(7));
                return true;
            } catch (RuntimeException exception) {
                return false;
            }
        };
    }

    private TestFixture createFixture() {
        String suffix = Long.toString(System.nanoTime());
        Category category = new Category();
        category.setName("并发测试-" + suffix);
        category.setDescription("并发借最后一本书测试分类");
        category.setCreatedAt(LocalDateTime.now());
        category = categoryRepository.save(category);
        categoryId = category.getId();

        User firstReader = saveReader("RCON" + suffix.substring(Math.max(0, suffix.length() - 8)), "并发读者A");
        User secondReader = saveReader("RCOB" + suffix.substring(Math.max(0, suffix.length() - 8)), "并发读者B");

        Book book = new Book();
        book.setTitle("并发最后一本-" + suffix);
        book.setAuthor("测试作者");
        book.setIsbn("T" + suffix);
        book.setCategoryId(category.getId());
        book.setCategory(category.getName());
        book.setShelfLocation("E-01-01");
        book.setStatus("enabled");
        book.setTotalCount(1);
        book.setAvailableCount(1);
        book.setCreatedAt(LocalDateTime.now());
        book = bookRepository.save(book);
        bookId = book.getId();

        storageLocationService.syncPrimaryStorage(book);
        bookCopyService.syncCopies(book);
        return new TestFixture(firstReader.getId(), secondReader.getId(), book.getId());
    }

    private User saveReader(String username, String realName) {
        User reader = new User();
        reader.setUsername(username);
        reader.setPassword("test-only");
        reader.setRealName(realName);
        reader.setRole("reader");
        reader.setPhone("13" + username.substring(Math.max(0, username.length() - 9)));
        reader.setStatus("enabled");
        reader.setCreatedAt(LocalDateTime.now());
        User saved = userRepository.save(reader);
        readerIds.add(saved.getId());
        return saved;
    }

    private static class TestFixture {
        private final Long firstReaderId;
        private final Long secondReaderId;
        private final Long bookId;

        TestFixture(Long firstReaderId, Long secondReaderId, Long bookId) {
            this.firstReaderId = firstReaderId;
            this.secondReaderId = secondReaderId;
            this.bookId = bookId;
        }

        Long firstReaderId() {
            return firstReaderId;
        }

        Long secondReaderId() {
            return secondReaderId;
        }

        Long bookId() {
            return bookId;
        }
    }
}
