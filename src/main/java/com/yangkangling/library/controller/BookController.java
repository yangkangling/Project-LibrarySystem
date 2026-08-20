package com.yangkangling.library.controller;

import com.yangkangling.library.entity.Book;
import com.yangkangling.library.entity.Category;
import com.yangkangling.library.config.LibraryProperties;
import com.yangkangling.library.dto.BookRequest;
import com.yangkangling.library.repository.BookRepository;
import com.yangkangling.library.repository.BookCopyRepository;
import com.yangkangling.library.repository.BorrowRecordRepository;
import com.yangkangling.library.repository.CategoryRepository;
import com.yangkangling.library.repository.UserRepository;
import com.yangkangling.library.service.BorrowRecordViewService;
import com.yangkangling.library.service.BookCopyService;
import com.yangkangling.library.service.StorageLocationService;
import javax.validation.Valid;
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

// 图书资料与馆藏维护接口。
@RestController
@RequestMapping("/books")
public class BookController {
    private static final int BOOK_TOTAL_COUNT_MAX = 50;

    // 图书主表仓库，负责图书基础信息查询和保存。
    private final BookRepository bookRepository;
    // 单册仓库，负责查询和删除具体馆藏单册。
    private final BookCopyRepository bookCopyRepository;
    // 借阅记录仓库，用于判断图书是否已有借阅历史。
    private final BorrowRecordRepository borrowRecordRepository;
    // 分类仓库，用于校验和解析图书分类。
    private final CategoryRepository categoryRepository;
    // 用户仓库，用于在单册详情中补充当前借阅读者信息。
    private final UserRepository userRepository;
    // 借阅记录视图服务，用于格式化单册编号和借阅记录。
    private final BorrowRecordViewService borrowRecordViewService;
    // 单册服务，用于同步馆藏单册数量和编号。
    private final BookCopyService bookCopyService;
    // 书架服务，用于同步图书主书架库存。
    private final StorageLocationService storageLocationService;
    // 系统容量配置，用于限制分页大小。
    private final LibraryProperties libraryProperties;

    // 构造方法注入图书管理需要的仓库和服务。
    public BookController(
            BookRepository bookRepository,
            BookCopyRepository bookCopyRepository,
            BorrowRecordRepository borrowRecordRepository,
            CategoryRepository categoryRepository,
            UserRepository userRepository,
            BorrowRecordViewService borrowRecordViewService,
            BookCopyService bookCopyService,
            StorageLocationService storageLocationService,
            LibraryProperties libraryProperties
    ) {
        this.bookRepository = bookRepository;
        this.bookCopyRepository = bookCopyRepository;
        this.borrowRecordRepository = borrowRecordRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.borrowRecordViewService = borrowRecordViewService;
        this.bookCopyService = bookCopyService;
        this.storageLocationService = storageLocationService;
        this.libraryProperties = libraryProperties;
    }

    // 查询图书列表，支持关键字、分类、状态和分页。
    @GetMapping
    public Object list(
            // ISBN、书名、作者或书架关键字。
            @RequestParam(required = false) String keyword,
            // 分类 id。
            @RequestParam(required = false) Long categoryId,
            // 分类名称，兼容旧数据。
            @RequestParam(required = false) String category,
            // 图书状态 enabled/disabled。
            @RequestParam(required = false) String status,
            // 页码；为空时兼容旧接口返回数组。
            @RequestParam(required = false) Integer page,
            // 每页数量。
            @RequestParam(defaultValue = "10") Integer size
    ) {
        // 不传 page 时走旧接口逻辑。
        if (page == null) {
            // 没有关键字时返回全部图书。
            if (keyword == null || keyword.trim().isEmpty()) {
                return bookRepository.findAll().stream()
                        .map(this::withActiveBorrowCount)
                        .collect(java.util.stream.Collectors.toList());
            }
            // 有关键字时查询全部匹配图书。
            String value = keyword.trim();
            return bookRepository.searchAll(value).stream()
                    .map(this::withActiveBorrowCount)
                    .collect(java.util.stream.Collectors.toList());
        }

        // 新分页接口：按多条件查询图书。
        return bookRepository.search(
                normalize(keyword),
                categoryId,
                normalize(category),
                normalize(status),
                PageRequest.of(Math.max(0, page), libraryProperties.normalizePageSize(size))
        ).map(this::withActiveBorrowCount);
    }

    // 查询图书详情，包括书架分布、单册列表和最近借阅记录。
    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        // 查出图书，不存在则提示。
        Book book = bookRepository.findById(id).orElseThrow(() -> new RuntimeException("图书不存在"));
        // 使用有序 Map 返回详情。
        Map<String, Object> result = new LinkedHashMap<>();
        // 图书基础信息。
        result.put("book", book);
        // 当前仍在读者手里的册数，用于停用提醒和详情展示。
        long activeBorrowCount = activeBorrowCount(id);
        result.put("borrowedCount", activeBorrowCount);
        result.put("activeBorrowCount", activeBorrowCount);
        // 书架位置分布列表。
        result.put("storageLocations", storageLocationService.findByBookId(id).stream()
                .map(storageLocation -> {
                    // 组装单个书架库存行。
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
                .collect(java.util.stream.Collectors.toList()));
        // 具体馆藏单册列表。
        result.put("copies", bookCopyRepository.findByBookIdOrderByCopyCodeAsc(id).stream()
                .map(copy -> {
                    // 组装单册展示行。
                    Map<String, Object> item = new LinkedHashMap<>();
                    // 保留内部原始单册编号，页面展示时会再格式化。
                    String rawCopyCode = copy.getCopyCode();
                    item.put("id", copy.getId());
                    item.put("copyCode", borrowRecordViewService.displayCopyCode(rawCopyCode));
                    item.put("rawCopyCode", rawCopyCode);
                    item.put("shelfLocation", copy.getShelfLocation());
                    item.put("status", copy.getStatus());
                    item.put("currentBorrowRecordId", copy.getCurrentBorrowRecordId());
                    item.put("currentUserId", copy.getCurrentUserId());
                    // 如果单册正在被借出，就补充当前读者证号和姓名。
                    userRepository.findById(copy.getCurrentUserId() == null ? -1L : copy.getCurrentUserId()).ifPresent(user -> {
                        item.put("currentReaderCard", user.getUsername());
                        item.put("currentReaderName", user.getRealName());
                    });
                    return item;
                })
                .collect(java.util.stream.Collectors.toList()));
        // 最近 10 条与该图书有关的借阅记录。
        result.put("recentBorrowRecords", borrowRecordRepository.findTop10ByBookIdOrderByIdDesc(id).stream()
                .map(borrowRecordViewService::toView)
                .collect(java.util.stream.Collectors.toList()));
        return result;
    }

    // 新增图书。
    @PostMapping
    @Transactional
    public Book add(@Valid @RequestBody BookRequest request) {
        Book book = toBook(request);
        // 校验 ISBN、书名、作者、分类、书架和馆藏数量。
        validateBook(book);
        validateBookTotalCount(book.getTotalCount());
        // 解析分类，确保选择的是已有分类。
        Category category = resolveCategory(book);
        // ISBN 不能重复。
        if (bookRepository.existsByIsbn(book.getIsbn().trim())) {
            throw new RuntimeException("ISBN 已存在，请更换后再保存");
        }

        // 统一清理文本字段前后空格。
        trimBook(book);
        // 写入分类 id 和分类名称。
        applyCategory(book, category);
        // 当前系统暂不维护出版社，统一置空。
        book.setPublisher(null);
        // 当前系统暂不维护出版日期，统一置空。
        book.setPublishDate(null);
        // 未填写可借数量时，默认等于馆藏总数。
        if (book.getAvailableCount() == null) {
            book.setAvailableCount(book.getTotalCount());
        }
        // 未填写状态时默认启用。
        if (book.getStatus() == null || book.getStatus().trim().isEmpty()) {
            book.setStatus("enabled");
        }
        // 设置创建时间。
        book.setCreatedAt(LocalDateTime.now());

        // 校验可借数量不能超过馆藏数量。
        validateStock(book);
        // 保存图书主表。
        Book savedBook = bookRepository.save(book);
        // 同步图书主书架库存。
        storageLocationService.syncPrimaryStorage(savedBook);
        // 根据馆藏数量同步单册记录。
        bookCopyService.syncCopies(savedBook);
        return savedBook;
    }

    // 修改图书。
    @PutMapping("/{id}")
    @Transactional
    public Book update(@PathVariable Long id, @Valid @RequestBody BookRequest request) {
        // 查询原图书。
        Book book = bookRepository.findById(id).orElseThrow(() -> new RuntimeException("图书不存在"));
        Book input = toBook(request);
        // 校验输入字段。
        validateBook(input);
        // 解析分类。
        Category category = resolveCategory(input);

        // 检查 ISBN 是否被其他图书占用。
        bookRepository.findByIsbn(input.getIsbn().trim()).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new RuntimeException("ISBN 已存在，请更换后再保存");
            }
        });

        // 更新 ISBN。
        book.setIsbn(input.getIsbn());
        // 更新书名。
        book.setTitle(input.getTitle());
        // 更新作者。
        book.setAuthor(input.getAuthor());
        // 不维护出版社，保持为空。
        book.setPublisher(null);
        // 不维护出版日期，保持为空。
        book.setPublishDate(null);
        // 更新主书架位置。
        book.setShelfLocation(input.getShelfLocation());
        // 更新状态，空值默认启用。
        book.setStatus(input.getStatus() == null ? "enabled" : input.getStatus());
        // 统一清理文本字段。
        trimBook(book);
        // 同步分类信息。
        applyCategory(book, category);
        // 校验库存数量合法。
        validateStock(book);

        // 保存修改结果。
        Book savedBook = bookRepository.save(book);
        // 同步书架库存。
        storageLocationService.syncPrimaryStorage(savedBook);
        // 同步单册数量和编号。
        bookCopyService.syncCopies(savedBook);
        return savedBook;
    }

    // 停用图书，停用后不能新增借阅。
    @PutMapping("/{id}/disable")
    public Book disable(@PathVariable Long id) {
        Book book = bookRepository.findById(id).orElseThrow(() -> new RuntimeException("图书不存在"));
        book.setStatus("disabled");
        return withActiveBorrowCount(bookRepository.save(book));
    }

    // 启用图书，启用后可重新出现在借阅候选中。
    @PutMapping("/{id}/enable")
    public Book enable(@PathVariable Long id) {
        Book book = bookRepository.findById(id).orElseThrow(() -> new RuntimeException("图书不存在"));
        book.setStatus("enabled");
        return withActiveBorrowCount(bookRepository.save(book));
    }

    // 删除图书。
    @DeleteMapping("/{id}")
    @Transactional
    public String delete(@PathVariable Long id) {
        // 图书不存在时直接提示。
        if (!bookRepository.existsById(id)) {
            throw new RuntimeException("图书不存在");
        }
        // 有借阅历史的图书不能删除，只能停用，避免历史记录失去关联。
        if (borrowRecordRepository.existsByBookId(id)) {
            throw new RuntimeException("该图书已有借阅历史，不能删除，可改为停用");
        }

        // 删除关联书架记录。
        storageLocationService.deleteByBookId(id);
        // 删除关联单册记录。
        bookCopyRepository.deleteByBookId(id);
        // 删除图书主表记录。
        bookRepository.deleteById(id);
        return "删除成功";
    }

    // 校验图书基础字段。
    private void validateBook(Book book) {
        // ISBN 必填。
        if (book.getIsbn() == null || book.getIsbn().trim().isEmpty()) {
            throw new RuntimeException("ISBN 不能为空");
        }
        // 书名必填。
        if (book.getTitle() == null || book.getTitle().trim().isEmpty()) {
            throw new RuntimeException("书名不能为空");
        }
        // 作者必填。
        if (book.getAuthor() == null || book.getAuthor().trim().isEmpty()) {
            throw new RuntimeException("作者不能为空");
        }
        // 分类可以传 id，也可以传名称。
        boolean hasCategoryId = book.getCategoryId() != null;
        boolean hasCategoryName = book.getCategory() != null && !book.getCategory().trim().isEmpty();
        // 分类必须二选一。
        if (!hasCategoryId && !hasCategoryName) {
            throw new RuntimeException("请选择图书分类");
        }
        // 书架位置必填。
        if (book.getShelfLocation() == null || book.getShelfLocation().trim().isEmpty()) {
            throw new RuntimeException("书架位置不能为空");
        }
    }

    private void validateBookTotalCount(Integer totalCount) {
        if (totalCount == null || totalCount <= 0) {
            throw new RuntimeException("馆藏数量必须为正整数");
        }
        if (totalCount > BOOK_TOTAL_COUNT_MAX) {
            throw new RuntimeException("单本图书馆藏数量不能超过 " + BOOK_TOTAL_COUNT_MAX + " 册");
        }
    }

    // 校验库存数量范围。
    private void validateStock(Book book) {
        // 可借数量不能为空。
        if (book.getAvailableCount() == null) {
            throw new RuntimeException("可借数量不能为空");
        }
        // 可借数量不能为负，也不能超过馆藏总数。
        if (book.getAvailableCount() < 0 || book.getAvailableCount() > book.getTotalCount()) {
            throw new RuntimeException("可借数量不能小于 0，也不能大于馆藏总数");
        }
    }

    // 清理图书文本字段前后空格。
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
            book.setShelfLocation(storageLocationService.normalizeShelfLocation(book.getShelfLocation()));
        }
    }

    // 根据图书输入解析分类实体。
    private Category resolveCategory(Book book) {
        // 优先使用分类 id。
        if (book.getCategoryId() != null) {
            return categoryRepository.findById(book.getCategoryId())
                    .orElseThrow(() -> new RuntimeException("图书分类不存在，请先在图书分类页面维护分类"));
        }
        // 没有分类 id 时使用分类名称查找。
        if (book.getCategory() != null && !book.getCategory().trim().isEmpty()) {
            return categoryRepository.findByName(book.getCategory().trim())
                    .orElseThrow(() -> new RuntimeException("图书分类不存在，请从下拉选项中选择已有分类"));
        }
        // 两者都没有时提示选择分类。
        throw new RuntimeException("请选择图书分类");
    }

    // 把分类信息写回图书对象。
    private void applyCategory(Book book, Category category) {
        book.setCategoryId(category.getId());
        book.setCategory(category.getName());
    }

    // 去掉查询参数前后空格。
    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    // 把可能为空的数量转换成 0。
    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private Book withActiveBorrowCount(Book book) {
        if (book != null && book.getId() != null) {
            book.setActiveBorrowCount(activeBorrowCount(book.getId()));
        }
        return book;
    }

    private long activeBorrowCount(Long bookId) {
        return borrowRecordRepository.countByBookIdAndStatus(bookId, "borrowed");
    }

    private Book toBook(BookRequest request) {
        Book book = new Book();
        book.setIsbn(request.getIsbn());
        book.setTitle(request.getTitle());
        book.setAuthor(request.getAuthor());
        book.setCategoryId(request.getCategoryId());
        book.setCategory(request.getCategory());
        book.setShelfLocation(request.getShelfLocation());
        book.setTotalCount(request.getTotalCount());
        book.setStatus(request.getStatus());
        return book;
    }
}
