package com.example.demo.service;

import com.example.demo.entity.Book;
import com.example.demo.entity.BookCopy;
import com.example.demo.entity.BorrowRecord;
import com.example.demo.entity.StorageLocation;
import com.example.demo.entity.User;
import com.example.demo.repository.BookRepository;
import com.example.demo.repository.BorrowRecordRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class BorrowService {
    private static final int MAX_ACTIVE_BORROW_COUNT = 3;

    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final BorrowRecordRepository borrowRecordRepository;
    private final BookCopyService bookCopyService;
    private final StorageLocationService storageLocationService;

    public BorrowService(
            BookRepository bookRepository,
            UserRepository userRepository,
            BorrowRecordRepository borrowRecordRepository,
            BookCopyService bookCopyService,
            StorageLocationService storageLocationService
    ) {
        this.bookRepository = bookRepository;
        this.userRepository = userRepository;
        this.borrowRecordRepository = borrowRecordRepository;
        this.bookCopyService = bookCopyService;
        this.storageLocationService = storageLocationService;
    }

    @Transactional
    public BorrowRecord borrowBook(Long userId, Long bookId) {
        return borrowBook(userId, bookId, LocalDate.now().plusDays(30));
    }

    @Transactional
    public BorrowRecord borrowBook(Long userId, Long bookId, LocalDate dueDate) {
        return borrowBooks(userId, List.of(bookId), dueDate).get(0);
    }

    @Transactional
    public List<BorrowRecord> borrowBooks(Long userId, List<Long> bookIds, LocalDate dueDate) {
        User user = validateReader(userId);
        List<Long> uniqueBookIds = normalizeBookIds(bookIds);

        if (dueDate == null) {
            dueDate = LocalDate.now().plusDays(30);
        }
        if (dueDate.isBefore(LocalDate.now())) {
            throw new RuntimeException("应还日期不能早于今天");
        }

        long currentBorrowCount = borrowRecordRepository.countByUserIdAndStatus(userId, "borrowed");
        if (currentBorrowCount + uniqueBookIds.size() > MAX_ACTIVE_BORROW_COUNT) {
            throw new RuntimeException("同一读者最多同时借阅 3 册图书，本次最多还能借 " + Math.max(0, MAX_ACTIVE_BORROW_COUNT - currentBorrowCount) + " 册");
        }
        if (borrowRecordRepository.existsByUserIdAndStatusAndDueDateBefore(userId, "borrowed", LocalDate.now())) {
            throw new RuntimeException("该读者存在逾期未还图书，请先归还逾期图书后再办理新借阅");
        }

        List<Book> books = new ArrayList<>();
        for (Long bookId : uniqueBookIds) {
            Book book = bookRepository.findById(bookId).orElseThrow(() -> new RuntimeException("图书不存在，ID：" + bookId));
            validateBorrowableBook(userId, book);
            books.add(book);
        }

        String batchNo = "BR" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        List<BorrowRecord> records = new ArrayList<>();
        for (Book book : books) {
            int updatedRows = bookRepository.decreaseAvailableCountWhenAvailable(book.getId());
            if (updatedRows == 0) {
                throw new RuntimeException("《" + book.getTitle() + "》库存不足，请刷新后重试");
            }
            StorageLocation storageLocation = storageLocationService.borrowAvailableStorage(book);
            BookCopy copy = bookCopyService.borrowAvailableCopy(book, user.getId());
            BorrowRecord record = borrowRecordRepository.save(createBorrowRecord(user, book, copy, storageLocation, dueDate, batchNo));
            bookCopyService.attachBorrowRecord(copy, record.getId());
            records.add(record);
        }

        return records;
    }

    private User validateReader(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("读者不存在"));
        if (!"reader".equals(user.getRole())) {
            throw new RuntimeException("只有读者账号可以办理借书");
        }
        if (!isEnabled(user.getStatus())) {
            throw new RuntimeException("停用读者不能办理借书");
        }
        return user;
    }

    private void validateBorrowableBook(Long userId, Book book) {
        if (!isEnabled(book.getStatus())) {
            throw new RuntimeException("停用图书不能办理新借阅");
        }
        if (book.getAvailableCount() == null || book.getAvailableCount() <= 0) {
            throw new RuntimeException("《" + book.getTitle() + "》已借完，暂无可借库存");
        }

        boolean alreadyBorrowed = borrowRecordRepository.existsByUserIdAndBookIdAndStatus(userId, book.getId(), "borrowed");
        if (alreadyBorrowed) {
            throw new RuntimeException("该读者已借阅《" + book.getTitle() + "》且尚未归还");
        }
    }

    private BorrowRecord createBorrowRecord(User user, Book book, BookCopy copy, StorageLocation storageLocation, LocalDate dueDate, String batchNo) {
        BorrowRecord record = new BorrowRecord();
        record.setUserId(user.getId());
        record.setBookId(book.getId());
        record.setBookCopyId(copy.getId());
        record.setStorageLocationId(storageLocation.getId());
        record.setReaderCard(user.getUsername());
        record.setReaderName(user.getRealName());
        record.setReaderPhone(user.getPhone());
        record.setBookIsbn(book.getIsbn());
        record.setBookTitle(book.getTitle());
        record.setBookAuthor(book.getAuthor());
        record.setCopyCode(copy.getCopyCode());
        record.setCopyShelfLocation(copy.getShelfLocation());
        record.setShelfLocationSnapshot(storageLocation.getShelfLocation());
        record.setBatchNo(batchNo);
        record.setBorrowDate(LocalDate.now());
        record.setDueDate(dueDate);
        record.setStatus("borrowed");
        record.setCreatedAt(LocalDateTime.now());
        return record;
    }

    private List<Long> normalizeBookIds(List<Long> bookIds) {
        if (bookIds == null || bookIds.isEmpty()) {
            throw new RuntimeException("请至少选择一本图书");
        }
        Set<Long> uniqueBookIds = new LinkedHashSet<>();
        for (Long bookId : bookIds) {
            if (bookId != null) {
                if (!uniqueBookIds.add(bookId)) {
                    throw new RuntimeException("同一本图书不能重复借阅，请选择不同图书");
                }
            }
        }
        if (uniqueBookIds.isEmpty()) {
            throw new RuntimeException("请至少选择一本图书");
        }
        return new ArrayList<>(uniqueBookIds);
    }

    @Transactional
    public BorrowRecord returnBook(Long recordId) {
        BorrowRecord record = borrowRecordRepository.findById(recordId).orElseThrow(() -> new RuntimeException("借阅记录不存在"));

        if ("returned".equals(record.getStatus())) {
            throw new RuntimeException("该借阅记录已经归还，不能重复还书");
        }

        record.setReturnDate(LocalDate.now());
        record.setStatus("returned");
        borrowRecordRepository.save(record);
        bookCopyService.returnCopy(record.getId());

        Book book = bookRepository.findById(record.getBookId()).orElseThrow(() -> new RuntimeException("图书不存在"));
        int updatedRows = bookRepository.increaseAvailableCountWithinTotal(book.getId());
        if (updatedRows == 0) {
            throw new RuntimeException("可借数量不能大于馆藏总数，请检查库存数据");
        }
        storageLocationService.returnStorage(record, book);

        return record;
    }

    @Transactional
    public List<BorrowRecord> returnBooks(List<Long> recordIds) {
        if (recordIds == null || recordIds.isEmpty()) {
            throw new RuntimeException("请至少选择一条要归还的借阅记录");
        }

        List<BorrowRecord> records = new ArrayList<>();
        for (Long recordId : new LinkedHashSet<>(recordIds)) {
            if (recordId != null) {
                records.add(returnBook(recordId));
            }
        }
        if (records.isEmpty()) {
            throw new RuntimeException("请至少选择一条要归还的借阅记录");
        }
        return records;
    }

    private boolean isEnabled(String status) {
        return status == null || status.isBlank() || "enabled".equals(status);
    }
}
