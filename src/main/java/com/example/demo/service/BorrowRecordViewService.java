package com.example.demo.service;

import com.example.demo.entity.Book;
import com.example.demo.entity.BorrowRecord;
import com.example.demo.entity.User;
import com.example.demo.repository.BookRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Service
public class BorrowRecordViewService {
    private final UserRepository userRepository;
    private final BookRepository bookRepository;

    public BorrowRecordViewService(UserRepository userRepository, BookRepository bookRepository) {
        this.userRepository = userRepository;
        this.bookRepository = bookRepository;
    }

    public Map<String, Object> toView(BorrowRecord record) {
        User currentUser = userRepository.findById(record.getUserId()).orElse(null);
        Book currentBook = bookRepository.findById(record.getBookId()).orElse(null);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", record.getId());
        item.put("userId", record.getUserId());
        item.put("bookId", record.getBookId());
        item.put("readerCard", firstText(record.getReaderCard(), currentUser == null ? "" : currentUser.getUsername()));
        item.put("readerName", firstText(record.getReaderName(), currentUser == null ? "" : currentUser.getRealName()));
        item.put("readerPhone", firstText(record.getReaderPhone(), currentUser == null ? "" : currentUser.getPhone()));
        item.put("isbn", firstText(record.getBookIsbn(), currentBook == null ? "" : currentBook.getIsbn()));
        item.put("bookTitle", firstText(record.getBookTitle(), currentBook == null ? "" : currentBook.getTitle()));
        item.put("bookAuthor", firstText(record.getBookAuthor(), currentBook == null ? "" : currentBook.getAuthor()));
        item.put("borrowDate", record.getBorrowDate());
        item.put("dueDate", record.getDueDate());
        item.put("returnDate", record.getReturnDate());
        item.put("rawStatus", record.getStatus());
        item.put("status", recordStatus(record));
        item.put("overdueDays", overdueDays(record));
        return item;
    }

    public Map<String, Object> toDetail(BorrowRecord record) {
        Map<String, Object> detail = toView(record);
        detail.put("createdAt", record.getCreatedAt());
        detail.put("snapshotSaved", hasText(record.getReaderCard()) || hasText(record.getBookTitle()));
        return detail;
    }

    public String recordStatus(BorrowRecord record) {
        if ("returned".equals(record.getStatus())) {
            return "returned";
        }
        if (record.getDueDate() != null && record.getDueDate().isBefore(LocalDate.now())) {
            return "overdue";
        }
        return record.getStatus();
    }

    public long overdueDays(BorrowRecord record) {
        if (record.getDueDate() == null || record.getReturnDate() != null || !record.getDueDate().isBefore(LocalDate.now())) {
            return 0;
        }
        return ChronoUnit.DAYS.between(record.getDueDate(), LocalDate.now());
    }

    public boolean matchesKeyword(Map<String, Object> record, String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return true;
        }
        String value = keyword.trim().toLowerCase();
        return record.values().stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .map(String::toLowerCase)
                .anyMatch(text -> text.contains(value));
    }

    private String firstText(String snapshotValue, String currentValue) {
        return hasText(snapshotValue) ? snapshotValue : currentValue;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
