package com.example.demo.controller;

import com.example.demo.entity.Book;
import com.example.demo.entity.Category;
import com.example.demo.repository.BookRepository;
import com.example.demo.repository.BookCopyRepository;
import com.example.demo.repository.BorrowRecordRepository;
import com.example.demo.repository.CategoryRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.BorrowRecordViewService;
import com.example.demo.service.BookCopyService;
import com.example.demo.service.StorageLocationService;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/books")
public class BookController {
    private final BookRepository bookRepository;
    private final BookCopyRepository bookCopyRepository;
    private final BorrowRecordRepository borrowRecordRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final BorrowRecordViewService borrowRecordViewService;
    private final BookCopyService bookCopyService;
    private final StorageLocationService storageLocationService;

    public BookController(
            BookRepository bookRepository,
            BookCopyRepository bookCopyRepository,
            BorrowRecordRepository borrowRecordRepository,
            CategoryRepository categoryRepository,
            UserRepository userRepository,
            BorrowRecordViewService borrowRecordViewService,
            BookCopyService bookCopyService,
            StorageLocationService storageLocationService
    ) {
        this.bookRepository = bookRepository;
        this.bookCopyRepository = bookCopyRepository;
        this.borrowRecordRepository = borrowRecordRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.borrowRecordViewService = borrowRecordViewService;
        this.bookCopyService = bookCopyService;
        this.storageLocationService = storageLocationService;
    }

    @GetMapping
    public Object list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(defaultValue = "10") Integer size
    ) {
        if (page == null) {
            if (keyword == null || keyword.trim().isEmpty()) {
                return bookRepository.findAll();
            }
            String value = keyword.trim();
            return bookRepository.searchAll(value);
        }

        return bookRepository.search(
                normalize(keyword),
                categoryId,
                normalize(category),
                normalize(status),
                PageRequest.of(page, size)
        );
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        Book book = bookRepository.findById(id).orElseThrow(() -> new RuntimeException("图书不存在"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("book", book);
        result.put("borrowedCount", safeInt(book.getTotalCount()) - safeInt(book.getAvailableCount()));
        result.put("storageLocations", storageLocationService.findByBookId(id).stream()
                .map(storageLocation -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", storageLocation.getId());
                    item.put("shelfLocation", storageLocation.getShelfLocation());
                    item.put("totalCount", storageLocation.getTotalCount());
                    item.put("availableCount", storageLocation.getAvailableCount());
                    item.put("borrowedCount", safeInt(storageLocation.getTotalCount()) - safeInt(storageLocation.getAvailableCount()));
                    item.put("remark", storageLocation.getRemark());
                    item.put("updatedAt", storageLocation.getUpdatedAt());
                    return item;
                })
                .toList());
        result.put("copies", bookCopyRepository.findByBookIdOrderByCopyCodeAsc(id).stream()
                .map(copy -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", copy.getId());
                    item.put("copyCode", copy.getCopyCode());
                    item.put("shelfLocation", copy.getShelfLocation());
                    item.put("status", copy.getStatus());
                    item.put("currentBorrowRecordId", copy.getCurrentBorrowRecordId());
                    item.put("currentUserId", copy.getCurrentUserId());
                    userRepository.findById(copy.getCurrentUserId() == null ? -1L : copy.getCurrentUserId()).ifPresent(user -> {
                        item.put("currentReaderCard", user.getUsername());
                        item.put("currentReaderName", user.getRealName());
                    });
                    return item;
                })
                .toList());
        result.put("recentBorrowRecords", borrowRecordRepository.findTop10ByBookIdOrderByIdDesc(id).stream()
                .map(borrowRecordViewService::toView)
                .toList());
        return result;
    }

    @PostMapping
    @Transactional
    public Book add(@RequestBody Book book) {
        validateBook(book);
        Category category = resolveCategory(book);
        if (bookRepository.existsByIsbn(book.getIsbn().trim())) {
            throw new RuntimeException("ISBN 已存在，请更换后再保存");
        }

        trimBook(book);
        applyCategory(book, category);
        book.setPublisher(null);
        book.setPublishDate(null);
        if (book.getAvailableCount() == null) {
            book.setAvailableCount(book.getTotalCount());
        }
        if (book.getStatus() == null || book.getStatus().trim().isEmpty()) {
            book.setStatus("enabled");
        }
        book.setCreatedAt(LocalDateTime.now());

        validateStock(book);
        Book savedBook = bookRepository.save(book);
        storageLocationService.syncPrimaryStorage(savedBook);
        bookCopyService.syncCopies(savedBook);
        return savedBook;
    }

    @PutMapping("/{id}")
    @Transactional
    public Book update(@PathVariable Long id, @RequestBody Book input) {
        Book book = bookRepository.findById(id).orElseThrow(() -> new RuntimeException("图书不存在"));
        validateBook(input);
        Category category = resolveCategory(input);

        bookRepository.findByIsbn(input.getIsbn().trim()).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new RuntimeException("ISBN 已存在，请更换后再保存");
            }
        });

        int borrowedCount = book.getTotalCount() - book.getAvailableCount();
        if (input.getTotalCount() < borrowedCount) {
            throw new RuntimeException("馆藏总数不能小于当前已借出数量");
        }

        book.setIsbn(input.getIsbn());
        book.setTitle(input.getTitle());
        book.setAuthor(input.getAuthor());
        book.setPublisher(null);
        book.setPublishDate(null);
        book.setShelfLocation(input.getShelfLocation());
        book.setStatus(input.getStatus() == null ? "enabled" : input.getStatus());
        book.setTotalCount(input.getTotalCount());
        book.setAvailableCount(input.getTotalCount() - borrowedCount);
        trimBook(book);
        applyCategory(book, category);
        validateStock(book);

        Book savedBook = bookRepository.save(book);
        storageLocationService.syncPrimaryStorage(savedBook);
        bookCopyService.syncCopies(savedBook);
        return savedBook;
    }

    @PutMapping("/{id}/disable")
    public Book disable(@PathVariable Long id) {
        Book book = bookRepository.findById(id).orElseThrow(() -> new RuntimeException("图书不存在"));
        book.setStatus("disabled");
        return bookRepository.save(book);
    }

    @PutMapping("/{id}/enable")
    public Book enable(@PathVariable Long id) {
        Book book = bookRepository.findById(id).orElseThrow(() -> new RuntimeException("图书不存在"));
        book.setStatus("enabled");
        return bookRepository.save(book);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public String delete(@PathVariable Long id) {
        if (!bookRepository.existsById(id)) {
            throw new RuntimeException("图书不存在");
        }
        if (borrowRecordRepository.existsByBookId(id)) {
            throw new RuntimeException("该图书已有借阅历史，不能删除，可改为停用");
        }

        storageLocationService.deleteByBookId(id);
        bookCopyRepository.deleteByBookId(id);
        bookRepository.deleteById(id);
        return "删除成功";
    }

    private void validateBook(Book book) {
        if (book.getIsbn() == null || book.getIsbn().trim().isEmpty()) {
            throw new RuntimeException("ISBN 不能为空");
        }
        if (book.getTitle() == null || book.getTitle().trim().isEmpty()) {
            throw new RuntimeException("书名不能为空");
        }
        if (book.getAuthor() == null || book.getAuthor().trim().isEmpty()) {
            throw new RuntimeException("作者不能为空");
        }
        boolean hasCategoryId = book.getCategoryId() != null;
        boolean hasCategoryName = book.getCategory() != null && !book.getCategory().trim().isEmpty();
        if (!hasCategoryId && !hasCategoryName) {
            throw new RuntimeException("请选择图书分类");
        }
        if (book.getShelfLocation() == null || book.getShelfLocation().trim().isEmpty()) {
            throw new RuntimeException("书架位置不能为空");
        }
        if (book.getTotalCount() == null || book.getTotalCount() <= 0) {
            throw new RuntimeException("馆藏数量必须为正整数");
        }
    }

    private void validateStock(Book book) {
        if (book.getAvailableCount() == null) {
            throw new RuntimeException("可借数量不能为空");
        }
        if (book.getAvailableCount() < 0 || book.getAvailableCount() > book.getTotalCount()) {
            throw new RuntimeException("可借数量不能小于 0，也不能大于馆藏总数");
        }
    }

    private void trimBook(Book book) {
        book.setIsbn(book.getIsbn().trim());
        book.setTitle(book.getTitle().trim());
        book.setAuthor(book.getAuthor().trim());
        if (book.getPublisher() != null) {
            book.setPublisher(book.getPublisher().trim());
        }
        if (book.getCategory() != null) {
            book.setCategory(book.getCategory().trim());
        }
        if (book.getShelfLocation() != null) {
            book.setShelfLocation(book.getShelfLocation().trim());
        }
    }

    private Category resolveCategory(Book book) {
        if (book.getCategoryId() != null) {
            return categoryRepository.findById(book.getCategoryId())
                    .orElseThrow(() -> new RuntimeException("图书分类不存在，请先在图书分类页面维护分类"));
        }
        if (book.getCategory() != null && !book.getCategory().trim().isEmpty()) {
            return categoryRepository.findByName(book.getCategory().trim())
                    .orElseThrow(() -> new RuntimeException("图书分类不存在，请从下拉选项中选择已有分类"));
        }
        throw new RuntimeException("请选择图书分类");
    }

    private void applyCategory(Book book, Category category) {
        book.setCategoryId(category.getId());
        book.setCategory(category.getName());
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }
}
