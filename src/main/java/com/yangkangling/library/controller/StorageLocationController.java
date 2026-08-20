package com.yangkangling.library.controller;

import com.yangkangling.library.entity.Book;
import com.yangkangling.library.entity.Shelf;
import com.yangkangling.library.entity.StorageLocation;
import com.yangkangling.library.config.LibraryProperties;
import com.yangkangling.library.repository.BookRepository;
import com.yangkangling.library.repository.ShelfRepository;
import com.yangkangling.library.repository.StorageLocationRepository;
import com.yangkangling.library.service.StorageLocationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// 书架位置查询接口。
@RestController
@RequestMapping("/storage-locations")
public class StorageLocationController {
    // 书架位置仓库，负责查询各书架库存记录。
    private final StorageLocationRepository storageLocationRepository;
    // 图书仓库，用于补充书名、作者、分类等信息。
    private final BookRepository bookRepository;
    // 书架服务，用于同步主书架库存。
    private final StorageLocationService storageLocationService;
    // 独立书架仓库，用来维护空书架。
    private final ShelfRepository shelfRepository;
    // 系统容量配置，用于限制分页大小。
    private final LibraryProperties libraryProperties;

    // 构造方法注入仓库和服务。
    public StorageLocationController(
            StorageLocationRepository storageLocationRepository,
            BookRepository bookRepository,
            StorageLocationService storageLocationService,
            ShelfRepository shelfRepository,
            LibraryProperties libraryProperties
    ) {
        this.storageLocationRepository = storageLocationRepository;
        this.bookRepository = bookRepository;
        this.storageLocationService = storageLocationService;
        this.shelfRepository = shelfRepository;
        this.libraryProperties = libraryProperties;
    }

    // 查询书架位置列表，支持关键字、图书筛选和分页。
    @GetMapping
    public Page<Map<String, Object>> list(
            // 书名、ISBN、作者、分类、书架等关键字。
            @RequestParam(required = false) String keyword,
            // 图书 id；传入后只看某一本书的书架位置。
            @RequestParam(required = false) Long bookId,
            // 当前页码。
            @RequestParam(defaultValue = "0") Integer page,
            // 每页数量。
            @RequestParam(defaultValue = "10") Integer size
    ) {
        String value = keyword == null ? "" : keyword.trim();
        int pageNumber = Math.max(0, page == null ? 0 : page);
        int pageSize = libraryProperties.normalizePageSize(size);
        List<Map<String, Object>> items;
        if (bookId != null) {
            items = storageLocationRepository.findByBookIdOrderByIdAsc(bookId).stream()
                    .map(this::toView)
                    .filter(item -> matchesKeyword(item, value.toLowerCase()))
                    .sorted(Comparator.comparing(item -> (Long) item.get("id"), Comparator.reverseOrder()))
                    .collect(java.util.stream.Collectors.toList());
            int from = Math.min(pageNumber * pageSize, items.size());
            int to = Math.min(from + pageSize, items.size());
            return new PageImpl<>(items.subList(from, to), PageRequest.of(pageNumber, pageSize), items.size());
        }

        Page<Shelf> shelves = hasText(value)
                ? shelfRepository.search(value, PageRequest.of(pageNumber, pageSize))
                : shelfRepository.findAllByOrderByShelfLocationAsc(PageRequest.of(pageNumber, pageSize));
        return shelves.map(this::toShelfView);
    }

    // 查询所有可选书架位置，用于新增/编辑图书下拉选择。
    @GetMapping("/options")
    public List<String> options() {
        List<String> locations = new ArrayList<>();
        locations.addAll(shelfRepository.findAllByOrderByShelfLocationAsc().stream()
                .map(Shelf::getShelfLocation)
                .collect(java.util.stream.Collectors.toList()));
        locations.addAll(storageLocationRepository.findDistinctShelfLocations());
        return locations.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .sorted()
                .collect(java.util.stream.Collectors.toList());
    }

    // 查询单条书架库存详情。
    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        // 先确认书架库存记录存在。
        StorageLocation storageLocation = storageLocationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("书架存储记录不存在"));
        // 转成前端展示格式。
        return toView(storageLocation);
    }

    // 新增某本书的书架库存；同书同位置会累加数量。
    @PostMapping
    public Map<String, Object> create(@RequestBody StorageCreateRequest request) {
        if (request.bookId() == null) {
            Shelf shelf = storageLocationService.createShelf(request.shelfLocation(), request.remark());
            return toShelfView(shelf);
        }
        // 交给服务层同步书架、图书主表和单册。
        StorageLocation storageLocation = storageLocationService.addStorage(
                request.bookId(),
                request.shelfLocation(),
                request.count(),
                request.remark()
        );
        // 返回最新书架行。
        return toView(storageLocation);
    }

    // 减少某个书架的空闲馆藏数量。
    @PostMapping("/{id}/decrease")
    public Map<String, Object> decrease(
            @PathVariable Long id,
            @RequestBody StorageDecreaseRequest request
    ) {
        // 交给服务层校验可借数量并停用对应单册。
        StorageLocation storageLocation = storageLocationService.decreaseStorage(id, request.count());
        // 如果减少后记录被删除，这里返回删除前的信息，前端随后会刷新列表。
        return toView(storageLocation);
    }

    // 删除没有借出册数的书架库存。
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        Shelf shelf = shelfRepository.findById(id).orElseThrow(() -> new RuntimeException("书架不存在"));
        Map<String, Object> item = toShelfView(shelf);
        storageLocationService.deleteShelf(id);
        return item;
    }

    // 把独立书架转换成前端书架管理行。
    private Map<String, Object> toShelfView(Shelf shelf) {
        List<StorageLocation> locations = storageLocationRepository.findByShelfLocation(shelf.getShelfLocation());
        int totalCount = locations.stream().mapToInt(location -> safeInt(location.getTotalCount())).sum();
        int availableCount = locations.stream().mapToInt(location -> safeInt(location.getAvailableCount())).sum();
        int borrowedCount = totalCount - availableCount;
        List<String> bookTitles = locations.stream()
                .map(location -> bookRepository.findById(location.getBookId()).map(Book::getTitle).orElse(""))
                .filter(value -> !value.isBlank())
                .distinct()
                .limit(5)
                .collect(java.util.stream.Collectors.toList());

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", shelf.getId());
        item.put("shelfLocation", shelf.getShelfLocation());
        item.put("bookTypes", locations.stream().map(StorageLocation::getBookId).filter(Objects::nonNull).distinct().count());
        item.put("bookTitles", String.join("、", bookTitles));
        item.put("totalCount", totalCount);
        item.put("availableCount", availableCount);
        item.put("borrowedCount", borrowedCount);
        item.put("canDelete", totalCount <= 0);
        item.put("remark", shelf.getRemark());
        item.put("updatedAt", shelf.getUpdatedAt());
        return item;
    }

    // 关键字为空时全部展示，否则匹配当前行任意展示字段。
    private boolean matchesKeyword(Map<String, Object> item, String value) {
        return value == null || value.isEmpty() || item.values().stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .map(String::toLowerCase)
                .anyMatch(text -> text.contains(value));
    }

    // 把书架库存实体转换成前端列表行。
    private Map<String, Object> toView(StorageLocation storageLocation) {
        // 查询关联图书，补充书名、作者、分类等字段。
        Book book = bookRepository.findById(storageLocation.getBookId()).orElse(null);
        // 使用有序 Map 保持字段顺序。
        Map<String, Object> item = new LinkedHashMap<>();
        // 书架记录编号。
        item.put("id", storageLocation.getId());
        // 关联图书 id。
        item.put("bookId", storageLocation.getBookId());
        // 图书 ISBN。
        item.put("isbn", book == null ? "" : book.getIsbn());
        // 图书名称。
        item.put("bookTitle", book == null ? "" : book.getTitle());
        // 作者。
        item.put("author", book == null ? "" : book.getAuthor());
        // 分类。
        item.put("category", book == null ? "" : book.getCategory());
        // 书架位置。
        item.put("shelfLocation", storageLocation.getShelfLocation());
        // 该书架馆藏数量。
        item.put("totalCount", safeInt(storageLocation.getTotalCount()));
        // 该书架可借数量。
        item.put("availableCount", safeInt(storageLocation.getAvailableCount()));
        // 该书架已借数量。
        item.put("borrowedCount", safeInt(storageLocation.getTotalCount()) - safeInt(storageLocation.getAvailableCount()));
        // 备注。
        item.put("remark", storageLocation.getRemark());
        // 更新时间。
        item.put("updatedAt", storageLocation.getUpdatedAt());
        return item;
    }

    // 把可能为空的数量转换成 0。
    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    // 判断文本是否有实际内容。
    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    // 新增书架库存请求体。
    public static class StorageCreateRequest {
        private Long bookId;
        private String shelfLocation;
        private Integer count;
        private String remark;

        public Long getBookId() {
            return bookId;
        }

        public void setBookId(Long bookId) {
            this.bookId = bookId;
        }

        public String getShelfLocation() {
            return shelfLocation;
        }

        public void setShelfLocation(String shelfLocation) {
            this.shelfLocation = shelfLocation;
        }

        public Integer getCount() {
            return count;
        }

        public void setCount(Integer count) {
            this.count = count;
        }

        public String getRemark() {
            return remark;
        }

        public void setRemark(String remark) {
            this.remark = remark;
        }

        public Long bookId() {
            return bookId;
        }

        public String shelfLocation() {
            return shelfLocation;
        }

        public Integer count() {
            return count;
        }

        public String remark() {
            return remark;
        }
    }

    // 减少书架库存请求体。
    public static class StorageDecreaseRequest {
        private Integer count;

        public Integer getCount() {
            return count;
        }

        public void setCount(Integer count) {
            this.count = count;
        }

        public Integer count() {
            return count;
        }
    }
}
