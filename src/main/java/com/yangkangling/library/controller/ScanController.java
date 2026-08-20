package com.yangkangling.library.controller;

import com.yangkangling.library.entity.Book;
import com.yangkangling.library.entity.BookCopy;
import com.yangkangling.library.entity.StorageLocation;
import com.yangkangling.library.repository.BookCopyRepository;
import com.yangkangling.library.repository.BookRepository;
import com.yangkangling.library.repository.StorageLocationRepository;
import com.yangkangling.library.service.BorrowRecordViewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/scan")
public class ScanController {
    private final BookRepository bookRepository;
    private final BookCopyRepository bookCopyRepository;
    private final StorageLocationRepository storageLocationRepository;
    private final BorrowRecordViewService borrowRecordViewService;

    public ScanController(
            BookRepository bookRepository,
            BookCopyRepository bookCopyRepository,
            StorageLocationRepository storageLocationRepository,
            BorrowRecordViewService borrowRecordViewService
    ) {
        this.bookRepository = bookRepository;
        this.bookCopyRepository = bookCopyRepository;
        this.storageLocationRepository = storageLocationRepository;
        this.borrowRecordViewService = borrowRecordViewService;
    }

    @GetMapping("/resolve")
    public Map<String, Object> resolve(@RequestParam String code) {
        String value = requireCode(code);
        return bookCopyRepository.findByCopyCode(value)
                .map(this::copyResult)
                .or(() -> bookRepository.findByIsbn(value).map(this::bookResult))
                .orElseGet(() -> shelfResult(value));
    }

    private Map<String, Object> copyResult(BookCopy copy) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "copy");
        result.put("copyId", copy.getId());
        result.put("copyCode", borrowRecordViewService.displayCopyCode(copy.getCopyCode()));
        result.put("rawCopyCode", copy.getCopyCode());
        result.put("copyStatus", copy.getStatus());
        result.put("shelfLocation", copy.getShelfLocation());
        result.put("currentUserId", copy.getCurrentUserId());
        result.put("currentBorrowRecordId", copy.getCurrentBorrowRecordId());
        bookRepository.findById(copy.getBookId()).ifPresent(book -> result.put("book", bookSummary(book)));
        return result;
    }

    private Map<String, Object> bookResult(Book book) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "book");
        result.put("book", bookSummary(book));
        result.put("copies", bookCopyRepository.findByBookIdOrderByCopyCodeAsc(book.getId()).stream()
                .map(copy -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", copy.getId());
                    item.put("copyCode", borrowRecordViewService.displayCopyCode(copy.getCopyCode()));
                    item.put("rawCopyCode", copy.getCopyCode());
                    item.put("status", copy.getStatus());
                    item.put("shelfLocation", copy.getShelfLocation());
                    return item;
                })
                .collect(java.util.stream.Collectors.toList()));
        return result;
    }

    private Map<String, Object> shelfResult(String shelfLocation) {
        String normalizedShelf = normalizeShelfCode(shelfLocation);
        List<StorageLocation> locations = storageLocationRepository.findByShelfLocation(normalizedShelf);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", locations.isEmpty() ? "unknown" : "shelf");
        result.put("shelfLocation", normalizedShelf);
        result.put("locations", locations.stream()
                .map(location -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", location.getId());
                    item.put("bookId", location.getBookId());
                    item.put("totalCount", location.getTotalCount());
                    item.put("availableCount", location.getAvailableCount());
                    item.put("borrowedCount", safeInt(location.getTotalCount()) - safeInt(location.getAvailableCount()));
                    bookRepository.findById(location.getBookId()).ifPresent(book -> item.put("book", bookSummary(book)));
                    return item;
                })
                .collect(java.util.stream.Collectors.toList()));
        return result;
    }

    private Map<String, Object> bookSummary(Book book) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", book.getId());
        item.put("isbn", book.getIsbn());
        item.put("title", book.getTitle());
        item.put("author", book.getAuthor());
        item.put("category", book.getCategory());
        item.put("shelfLocation", book.getShelfLocation());
        item.put("status", book.getStatus());
        item.put("totalCount", book.getTotalCount());
        item.put("availableCount", book.getAvailableCount());
        return item;
    }

    private String requireCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new RuntimeException("扫码内容不能为空");
        }
        return code.trim();
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String normalizeShelfCode(String code) {
        String value = code.trim().toUpperCase();
        String[] parts = value.split("-");
        if (parts.length == 3 && parts[0].matches("[A-Z]") && parts[1].matches("\\d{1,2}") && parts[2].matches("\\d{1,2}")) {
            return parts[0] + "-" + String.format("%02d", Integer.parseInt(parts[1])) + "-" + String.format("%02d", Integer.parseInt(parts[2]));
        }
        return value;
    }
}
