package com.example.demo.controller;

import com.example.demo.entity.Book;
import com.example.demo.entity.StorageLocation;
import com.example.demo.repository.BookRepository;
import com.example.demo.repository.StorageLocationRepository;
import com.example.demo.service.StorageLocationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/storage-locations")
public class StorageLocationController {
    private final StorageLocationRepository storageLocationRepository;
    private final BookRepository bookRepository;
    private final StorageLocationService storageLocationService;

    public StorageLocationController(
            StorageLocationRepository storageLocationRepository,
            BookRepository bookRepository,
            StorageLocationService storageLocationService
    ) {
        this.storageLocationRepository = storageLocationRepository;
        this.bookRepository = bookRepository;
        this.storageLocationService = storageLocationService;
    }

    @GetMapping
    public Page<Map<String, Object>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long bookId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size
    ) {
        bookRepository.findAll().forEach(storageLocationService::syncPrimaryStorage);
        String value = keyword == null ? "" : keyword.trim().toLowerCase();
        List<Map<String, Object>> items = storageLocationRepository.findAll().stream()
                .filter(storage -> bookId == null || bookId.equals(storage.getBookId()))
                .map(this::toView)
                .filter(item -> value.isEmpty() || item.values().stream()
                        .filter(java.util.Objects::nonNull)
                        .map(Object::toString)
                        .map(String::toLowerCase)
                        .anyMatch(text -> text.contains(value)))
                .sorted(Comparator.comparing(item -> (Long) item.get("id"), Comparator.reverseOrder()))
                .toList();

        int from = Math.min(page * size, items.size());
        int to = Math.min(from + size, items.size());
        return new PageImpl<>(items.subList(from, to), PageRequest.of(page, size), items.size());
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        StorageLocation storageLocation = storageLocationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("书架存储记录不存在"));
        return toView(storageLocation);
    }

    private Map<String, Object> toView(StorageLocation storageLocation) {
        Book book = bookRepository.findById(storageLocation.getBookId()).orElse(null);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", storageLocation.getId());
        item.put("bookId", storageLocation.getBookId());
        item.put("isbn", book == null ? "" : book.getIsbn());
        item.put("bookTitle", book == null ? "" : book.getTitle());
        item.put("author", book == null ? "" : book.getAuthor());
        item.put("category", book == null ? "" : book.getCategory());
        item.put("shelfLocation", storageLocation.getShelfLocation());
        item.put("totalCount", safeInt(storageLocation.getTotalCount()));
        item.put("availableCount", safeInt(storageLocation.getAvailableCount()));
        item.put("borrowedCount", safeInt(storageLocation.getTotalCount()) - safeInt(storageLocation.getAvailableCount()));
        item.put("remark", storageLocation.getRemark());
        item.put("updatedAt", storageLocation.getUpdatedAt());
        return item;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }
}
