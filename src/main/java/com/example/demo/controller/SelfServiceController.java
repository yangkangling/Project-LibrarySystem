package com.example.demo.controller;

import com.example.demo.entity.BorrowRecord;
import com.example.demo.entity.User;
import com.example.demo.repository.BookRepository;
import com.example.demo.repository.BorrowRecordRepository;
import com.example.demo.repository.CategoryRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.BorrowRecordViewService;
import com.example.demo.service.BorrowService;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/self")
public class SelfServiceController {
    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final CategoryRepository categoryRepository;
    private final BorrowRecordRepository borrowRecordRepository;
    private final BorrowRecordViewService borrowRecordViewService;
    private final BorrowService borrowService;

    public SelfServiceController(
            UserRepository userRepository,
            BookRepository bookRepository,
            CategoryRepository categoryRepository,
            BorrowRecordRepository borrowRecordRepository,
            BorrowRecordViewService borrowRecordViewService,
            BorrowService borrowService
    ) {
        this.userRepository = userRepository;
        this.bookRepository = bookRepository;
        this.categoryRepository = categoryRepository;
        this.borrowRecordRepository = borrowRecordRepository;
        this.borrowRecordViewService = borrowRecordViewService;
        this.borrowService = borrowService;
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpSession session) {
        User reader = currentReader(session);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", reader.getId());
        item.put("username", reader.getUsername());
        item.put("realName", reader.getRealName());
        item.put("phone", reader.getPhone());
        item.put("currentBorrowCount", borrowRecordRepository.countByUserIdAndStatus(reader.getId(), "borrowed"));
        item.put("maxBorrowCount", 3);
        return item;
    }

    @GetMapping("/categories")
    public List<Map<String, Object>> categories() {
        return categoryRepository.findAll().stream()
                .map(category -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", category.getId());
                    item.put("name", category.getName());
                    return item;
                })
                .toList();
    }

    @GetMapping("/books")
    public Page<Map<String, Object>> books(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size
    ) {
        return bookRepository.search(keyword == null ? null : keyword.trim(), categoryId, null, "enabled", PageRequest.of(page, size))
                .map(book -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", book.getId());
                    item.put("isbn", book.getIsbn());
                    item.put("title", book.getTitle());
                    item.put("author", book.getAuthor());
                    item.put("category", book.getCategory());
                    item.put("shelfLocation", book.getShelfLocation());
                    item.put("availableCount", book.getAvailableCount());
                    item.put("totalCount", book.getTotalCount());
                    item.put("borrowable", book.getAvailableCount() != null && book.getAvailableCount() > 0);
                    return item;
                });
    }

    @GetMapping("/records")
    public List<Map<String, Object>> records(
            @RequestParam(required = false) String status,
            HttpSession session
    ) {
        User reader = currentReader(session);
        return borrowRecordRepository.findByUserIdOrderByIdDesc(reader.getId())
                .stream()
                .map(borrowRecordViewService::toView)
                .filter(record -> status == null || status.isBlank() || Objects.equals(record.get("status"), status) || Objects.equals(record.get("rawStatus"), status))
                .sorted(Comparator.comparing(item -> (Long) item.get("id"), Comparator.reverseOrder()))
                .toList();
    }

    @PostMapping("/borrow")
    public List<Map<String, Object>> borrow(
            @RequestBody BorrowRequest request,
            HttpSession session
    ) {
        User reader = currentReader(session);
        List<BorrowRecord> records = borrowService.borrowBooks(reader.getId(), request.bookIds(), null);
        return records.stream().map(borrowRecordViewService::toView).toList();
    }

    @PostMapping("/return")
    public List<Map<String, Object>> returnBooks(
            @RequestBody ReturnRequest request,
            HttpSession session
    ) {
        User reader = currentReader(session);
        List<BorrowRecord> records = borrowRecordRepository.findAllById(request.recordIds());
        boolean hasOtherReaderRecord = records.stream().anyMatch(record -> !Objects.equals(record.getUserId(), reader.getId()));
        if (records.size() != request.recordIds().size() || hasOtherReaderRecord) {
            throw new RuntimeException("只能归还本人借阅记录");
        }
        return borrowService.returnBooks(request.recordIds()).stream().map(borrowRecordViewService::toView).toList();
    }

    private User currentReader(HttpSession session) {
        Object readerId = session.getAttribute("readerId");
        if (!(readerId instanceof Long id)) {
            throw new RuntimeException("请先登录读者自助端");
        }
        return userRepository.findById(id).orElseThrow(() -> new RuntimeException("读者不存在"));
    }

    public record BorrowRequest(List<Long> bookIds) {
    }

    public record ReturnRequest(List<Long> recordIds) {
    }
}
