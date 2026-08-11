package com.example.demo.service;

import com.example.demo.entity.Book;
import com.example.demo.entity.BorrowRecord;
import com.example.demo.entity.User;
import com.example.demo.repository.BookRepository;
import com.example.demo.repository.BorrowRecordRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class BorrowService {
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final BorrowRecordRepository borrowRecordRepository;

    public BorrowService(
            BookRepository bookRepository,
            UserRepository userRepository,
            BorrowRecordRepository borrowRecordRepository
    ) {
        this.bookRepository = bookRepository;
        this.userRepository = userRepository;
        this.borrowRecordRepository = borrowRecordRepository;
    }

    @Transactional
    public BorrowRecord borrowBook(Long userId, Long bookId) {
        return borrowBook(userId, bookId, LocalDate.now().plusDays(30));
    }

    @Transactional
    public BorrowRecord borrowBook(Long userId, Long bookId, LocalDate dueDate) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("读者不存在"));
        if (!"reader".equals(user.getRole())) {
            throw new RuntimeException("只有读者账号可以办理借书");
        }
        if (!isEnabled(user.getStatus())) {
            throw new RuntimeException("停用读者不能办理借书");
        }

        Book book = bookRepository.findById(bookId).orElseThrow(() -> new RuntimeException("图书不存在"));
        if (!isEnabled(book.getStatus())) {
            throw new RuntimeException("停用图书不能办理新借阅");
        }
        if (book.getAvailableCount() == null || book.getAvailableCount() <= 0) {
            throw new RuntimeException("该图书已借完，暂无可借库存");
        }
        if (dueDate == null) {
            dueDate = LocalDate.now().plusDays(30);
        }
        if (dueDate.isBefore(LocalDate.now())) {
            throw new RuntimeException("应还日期不能早于今天");
        }

        long currentBorrowCount = borrowRecordRepository.countByUserIdAndStatus(userId, "borrowed");
        if (currentBorrowCount >= 5) {
            throw new RuntimeException("同一读者最多同时借阅 5 册图书");
        }

        boolean alreadyBorrowed = borrowRecordRepository.existsByUserIdAndBookIdAndStatus(userId, bookId, "borrowed");
        if (alreadyBorrowed) {
            throw new RuntimeException("该读者已借阅这本图书且尚未归还");
        }

        book.setAvailableCount(book.getAvailableCount() - 1);
        bookRepository.save(book);

        BorrowRecord record = new BorrowRecord();
        record.setUserId(userId);
        record.setBookId(bookId);
        record.setReaderCard(user.getUsername());
        record.setReaderName(user.getRealName());
        record.setReaderPhone(user.getPhone());
        record.setBookIsbn(book.getIsbn());
        record.setBookTitle(book.getTitle());
        record.setBookAuthor(book.getAuthor());
        record.setBorrowDate(LocalDate.now());
        record.setDueDate(dueDate);
        record.setStatus("borrowed");

        return borrowRecordRepository.save(record);
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

        Book book = bookRepository.findById(record.getBookId()).orElseThrow(() -> new RuntimeException("图书不存在"));
        int nextAvailableCount = book.getAvailableCount() + 1;
        if (nextAvailableCount > book.getTotalCount()) {
            throw new RuntimeException("可借数量不能大于馆藏总数，请检查库存数据");
        }

        book.setAvailableCount(nextAvailableCount);
        bookRepository.save(book);

        return record;
    }

    private boolean isEnabled(String status) {
        return status == null || status.isBlank() || "enabled".equals(status);
    }
}
