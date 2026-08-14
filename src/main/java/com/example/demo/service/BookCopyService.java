package com.example.demo.service;

import com.example.demo.entity.Book;
import com.example.demo.entity.BookCopy;
import com.example.demo.entity.BorrowRecord;
import com.example.demo.repository.BookCopyRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class BookCopyService {
    private final BookCopyRepository bookCopyRepository;

    public BookCopyService(BookCopyRepository bookCopyRepository) {
        this.bookCopyRepository = bookCopyRepository;
    }

    public void syncCopies(Book book) {
        if (book == null || book.getId() == null || book.getTotalCount() == null) {
            return;
        }

        List<BookCopy> copies = bookCopyRepository.findByBookIdOrderByCopyCodeAsc(book.getId());
        int targetTotal = Math.max(0, book.getTotalCount());
        long currentTotal = copies.stream().filter(copy -> !"disabled".equals(copy.getStatus())).count();

        if (currentTotal < targetTotal) {
            int next = nextSerialNumber(copies);
            for (long count = currentTotal; count < targetTotal; count++) {
                bookCopyRepository.save(newCopy(book, next));
                next++;
            }
        } else if (currentTotal > targetTotal) {
            long removeCount = currentTotal - targetTotal;
            copies.stream()
                    .filter(copy -> "available".equals(copy.getStatus()))
                    .sorted(Comparator.comparing(BookCopy::getCopyCode).reversed())
                    .limit(removeCount)
                    .forEach(copy -> {
                        copy.setStatus("disabled");
                        copy.setUpdatedAt(LocalDateTime.now());
                        bookCopyRepository.save(copy);
                    });
        }

        bookCopyRepository.findByBookIdOrderByCopyCodeAsc(book.getId()).forEach(copy -> {
            if (!"borrowed".equals(copy.getStatus()) && !Objects.equals(book.getShelfLocation(), copy.getShelfLocation())) {
                copy.setShelfLocation(book.getShelfLocation());
                copy.setUpdatedAt(LocalDateTime.now());
                bookCopyRepository.save(copy);
            }
        });
    }

    public BookCopy borrowAvailableCopy(Book book, Long userId) {
        BookCopy copy = bookCopyRepository.findFirstByBookIdAndStatusOrderByCopyCodeAsc(book.getId(), "available")
                .orElseThrow(() -> new RuntimeException("《" + book.getTitle() + "》没有可借单册，请刷新后重试"));
        copy.setStatus("borrowed");
        copy.setCurrentUserId(userId);
        copy.setUpdatedAt(LocalDateTime.now());
        return bookCopyRepository.save(copy);
    }

    public void attachBorrowRecord(BookCopy copy, Long borrowRecordId) {
        copy.setCurrentBorrowRecordId(borrowRecordId);
        copy.setUpdatedAt(LocalDateTime.now());
        bookCopyRepository.save(copy);
    }

    public void returnCopy(Long borrowRecordId) {
        bookCopyRepository.findByCurrentBorrowRecordId(borrowRecordId).ifPresent(copy -> {
            copy.setStatus("available");
            copy.setCurrentUserId(null);
            copy.setCurrentBorrowRecordId(null);
            copy.setUpdatedAt(LocalDateTime.now());
            bookCopyRepository.save(copy);
        });
    }

    public boolean attachExistingBorrowRecord(Book book, BorrowRecord record) {
        if (book == null || record == null) {
            return false;
        }

        BookCopy copy = findCopyForRecord(book, record);
        if (copy == null) {
            return false;
        }

        if ("borrowed".equals(record.getStatus())) {
            copy.setStatus("borrowed");
            copy.setCurrentUserId(record.getUserId());
            copy.setCurrentBorrowRecordId(record.getId());
            copy.setUpdatedAt(LocalDateTime.now());
            bookCopyRepository.save(copy);
        }

        boolean changed = false;
        if (record.getBookCopyId() == null || !record.getBookCopyId().equals(copy.getId())) {
            record.setBookCopyId(copy.getId());
            changed = true;
        }
        if (!hasText(record.getCopyCode())) {
            record.setCopyCode(copy.getCopyCode());
            changed = true;
        }
        if (!hasText(record.getCopyShelfLocation())) {
            record.setCopyShelfLocation(copy.getShelfLocation());
            changed = true;
        }
        return changed;
    }

    private BookCopy findCopyForRecord(Book book, BorrowRecord record) {
        if (record.getBookCopyId() != null) {
            BookCopy copy = bookCopyRepository.findById(record.getBookCopyId()).orElse(null);
            if (copy != null) {
                return copy;
            }
        }

        if ("borrowed".equals(record.getStatus())) {
            BookCopy borrowedCopy = bookCopyRepository.findByCurrentBorrowRecordId(record.getId()).orElse(null);
            if (borrowedCopy != null) {
                return borrowedCopy;
            }
            return bookCopyRepository.findFirstByBookIdAndStatusOrderByCopyCodeAsc(book.getId(), "available").orElse(null);
        }

        List<BookCopy> copies = bookCopyRepository.findByBookIdOrderByCopyCodeAsc(book.getId());
        if (copies.isEmpty()) {
            return null;
        }
        long recordId = record.getId() == null ? 1 : record.getId();
        int index = Math.floorMod(recordId - 1, copies.size());
        return copies.get(index);
    }

    public long availableCopyCount(Long bookId) {
        return bookCopyRepository.countByBookIdAndStatus(bookId, "available");
    }

    private BookCopy newCopy(Book book, int serialNumber) {
        LocalDateTime now = LocalDateTime.now();
        BookCopy copy = new BookCopy();
        copy.setBookId(book.getId());
        copy.setCopyCode(copyCode(book, serialNumber));
        copy.setShelfLocation(book.getShelfLocation());
        copy.setStatus("available");
        copy.setCreatedAt(now);
        copy.setUpdatedAt(now);
        return copy;
    }

    private String copyCode(Book book, int serialNumber) {
        String isbn = hasText(book.getIsbn()) ? book.getIsbn().trim() : "BOOK" + book.getId();
        return isbn.toUpperCase(Locale.ROOT) + "-" + String.format("%03d", serialNumber);
    }

    private int nextSerialNumber(List<BookCopy> copies) {
        return copies.stream()
                .map(BookCopy::getCopyCode)
                .filter(this::hasText)
                .map(code -> code.substring(Math.max(0, code.length() - 3)))
                .filter(value -> value.matches("\\d{3}"))
                .mapToInt(Integer::parseInt)
                .max()
                .orElse(0) + 1;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
