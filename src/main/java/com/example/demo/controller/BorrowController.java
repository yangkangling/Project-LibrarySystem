package com.example.demo.controller;

import com.example.demo.entity.BorrowRecord;
import com.example.demo.entity.Book;
import com.example.demo.entity.User;
import com.example.demo.repository.BookRepository;
import com.example.demo.repository.BorrowRecordRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.BorrowRecordViewService;
import com.example.demo.service.BorrowService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/borrow")
public class BorrowController {
    private final BorrowService borrowService;
    private final BorrowRecordRepository borrowRecordRepository;
    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final BorrowRecordViewService borrowRecordViewService;

    public BorrowController(
            BorrowService borrowService,
            BorrowRecordRepository borrowRecordRepository,
            UserRepository userRepository,
            BookRepository bookRepository,
            BorrowRecordViewService borrowRecordViewService
    ) {
        this.borrowService = borrowService;
        this.borrowRecordRepository = borrowRecordRepository;
        this.userRepository = userRepository;
        this.bookRepository = bookRepository;
        this.borrowRecordViewService = borrowRecordViewService;
    }

    @GetMapping("/records")
    public Object records(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long bookId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate borrowStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate borrowEnd,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueEnd,
            @RequestParam(required = false) Integer page,
            @RequestParam(defaultValue = "10") Integer size
    ) {
        List<Map<String, Object>> records = borrowRecordRepository.findAll().stream()
                .filter(record -> status == null || status.isBlank() || Objects.equals(borrowRecordViewService.recordStatus(record), status) || Objects.equals(record.getStatus(), status))
                .filter(record -> userId == null || Objects.equals(record.getUserId(), userId))
                .filter(record -> bookId == null || Objects.equals(record.getBookId(), bookId))
                .filter(record -> borrowStart == null || !record.getBorrowDate().isBefore(borrowStart))
                .filter(record -> borrowEnd == null || !record.getBorrowDate().isAfter(borrowEnd))
                .filter(record -> dueStart == null || !record.getDueDate().isBefore(dueStart))
                .filter(record -> dueEnd == null || !record.getDueDate().isAfter(dueEnd))
                .map(borrowRecordViewService::toView)
                .filter(record -> borrowRecordViewService.matchesKeyword(record, keyword))
                .sorted(Comparator.comparing(item -> (Long) item.get("id"), Comparator.reverseOrder()))
                .toList();

        if (page == null) {
            return records;
        }
        return toPage(records, page, size);
    }

    @GetMapping("/records/{id}")
    public Map<String, Object> recordDetail(@PathVariable Long id) {
        BorrowRecord record = borrowRecordRepository.findById(id).orElseThrow(() -> new RuntimeException("借阅记录不存在"));
        return borrowRecordViewService.toDetail(record);
    }

    @GetMapping("/overdue")
    public Object overdue(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(defaultValue = "10") Integer size
    ) {
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> records = borrowRecordRepository.findByStatusAndDueDateBeforeOrderByDueDateAsc("borrowed", today)
                .stream()
                .map(record -> {
                    Map<String, Object> item = borrowRecordViewService.toView(record);
                    item.put("status", "overdue");
                    item.put("overdueDays", borrowRecordViewService.overdueDays(record));
                    return item;
                })
                .filter(record -> borrowRecordViewService.matchesKeyword(record, keyword))
                .sorted(Comparator.comparing(item -> (Long) item.get("overdueDays"), Comparator.reverseOrder()))
                .toList();

        if (page == null) {
            return records;
        }
        return toPage(records, page, size);
    }

    @GetMapping("/reader-options")
    public Page<Map<String, Object>> readerOptions(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "5") Integer size
    ) {
        return userRepository.searchReaders(keyword == null ? null : keyword.trim(), "enabled", PageRequest.of(page, size))
                .map(reader -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", reader.getId());
                    item.put("cardNumber", reader.getUsername());
                    item.put("realName", reader.getRealName());
                    item.put("phone", reader.getPhone());
                    item.put("status", reader.getStatus());
                    item.put("currentBorrowCount", borrowRecordRepository.countByUserIdAndStatus(reader.getId(), "borrowed"));
                    return item;
                });
    }

    @GetMapping("/book-options")
    public Page<Map<String, Object>> bookOptions(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "5") Integer size
    ) {
        return bookRepository.search(keyword == null ? null : keyword.trim(), null, "enabled", PageRequest.of(page, size))
                .map(book -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", book.getId());
                    item.put("isbn", book.getIsbn());
                    item.put("title", book.getTitle());
                    item.put("author", book.getAuthor());
                    item.put("category", book.getCategory());
                    item.put("status", book.getStatus());
                    item.put("availableCount", book.getAvailableCount());
                    item.put("totalCount", book.getTotalCount());
                    return item;
                });
    }

    @GetMapping("/return-options")
    public Page<Map<String, Object>> returnOptions(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size
    ) {
        List<Map<String, Object>> records = borrowRecordRepository.findByStatus("borrowed").stream()
                .map(borrowRecordViewService::toView)
                .filter(record -> borrowRecordViewService.matchesKeyword(record, keyword))
                .sorted(Comparator.comparing(item -> (Long) item.get("id"), Comparator.reverseOrder()))
                .toList();
        return toPage(records, page, size);
    }

    @PostMapping
    public BorrowRecord borrow(
            @RequestParam Long userId,
            @RequestParam Long bookId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDate
    ) {
        return borrowService.borrowBook(userId, bookId, dueDate);
    }

    @PostMapping("/return/{recordId}")
    public BorrowRecord returnBook(@PathVariable Long recordId) {
        return borrowService.returnBook(recordId);
    }

    private Page<Map<String, Object>> toPage(List<Map<String, Object>> records, int page, int size) {
        int from = Math.min(page * size, records.size());
        int to = Math.min(from + size, records.size());
        return new PageImpl<>(records.subList(from, to), PageRequest.of(page, size), records.size());
    }
}
