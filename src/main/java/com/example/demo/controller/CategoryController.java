package com.example.demo.controller;

import com.example.demo.entity.Book;
import com.example.demo.entity.Category;
import com.example.demo.config.LibraryProperties;
import com.example.demo.repository.BookRepository;
import com.example.demo.repository.CategoryRepository;
import org.springframework.data.domain.Page;
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
import java.util.Objects;

// 图书分类维护接口。
@RestController
@RequestMapping("/categories")
public class CategoryController {
    // 分类仓库，负责分类表增删改查。
    private final CategoryRepository categoryRepository;
    // 图书仓库，用于统计、查看和迁移分类下的图书。
    private final BookRepository bookRepository;
    // 系统容量配置，用于限制分页大小。
    private final LibraryProperties libraryProperties;

    // 构造方法注入仓库。
    public CategoryController(
            CategoryRepository categoryRepository,
            BookRepository bookRepository,
            LibraryProperties libraryProperties
    ) {
        this.categoryRepository = categoryRepository;
        this.bookRepository = bookRepository;
        this.libraryProperties = libraryProperties;
    }

    // 查询分类列表，支持关键字、排序和分页。
    @GetMapping
    public Object list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(defaultValue = "10") Integer size
    ) {
        // 不传 page 时返回完整列表，用于下拉框。
        if (page == null) {
            return categoryList(keyword).stream().map(this::categoryView).collect(java.util.stream.Collectors.toList());
        }
        return categoryPage(keyword, page, size).map(this::categoryView);
    }

    // 查询单个分类详情。
    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        Category category = findCategory(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("category", category);
        result.put("bookCount", countBooks(category));
        return result;
    }

    // 查询某个分类下的图书，用于点击“图书数量”查看明细。
    @GetMapping("/{id}/books")
    public Page<Map<String, Object>> books(
            @PathVariable Long id,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size
    ) {
        Category category = findCategory(id);
        return bookRepository.searchByCategory(
                        category.getId(),
                        category.getName(),
                        normalize(keyword),
                        PageRequest.of(Math.max(0, page), libraryProperties.normalizePageSize(size))
                )
                .map(this::bookView);
    }

    // 新增分类。
    @PostMapping
    public Category add(@RequestBody Category category) {
        validateCategory(category);
        String name = category.getName().trim();
        if (categoryRepository.existsByName(name)) {
            throw new RuntimeException("分类名称已存在，请更换后再保存");
        }
        category.setName(name);
        category.setCreatedAt(LocalDateTime.now());
        return categoryRepository.save(category);
    }

    // 修改分类，并同步图书表里的分类快照名称。
    @PutMapping("/{id}")
    @Transactional
    public Category update(@PathVariable Long id, @RequestBody Category input) {
        Category category = findCategory(id);
        validateCategory(input);
        String nextName = input.getName().trim();

        categoryRepository.findByName(nextName).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new RuntimeException("分类名称已存在，请更换后再保存");
            }
        });

        String oldName = category.getName();
        category.setName(nextName);
        category.setDescription(input.getDescription());
        Category saved = categoryRepository.save(category);
        syncBookCategoryName(saved, oldName);
        return saved;
    }

    // 删除分类；分类下有书时，必须传入目标分类并先迁移图书。
    @DeleteMapping("/{id}")
    @Transactional
    public String delete(
            @PathVariable Long id,
            @RequestParam(required = false) Long targetCategoryId
    ) {
        Category category = findCategory(id);
        long bookCount = countBooks(category);
        if (bookCount > 0) {
            if (targetCategoryId == null) {
                throw new RuntimeException("该分类下已有 " + bookCount + " 本图书，请先选择目标分类迁移后再删除");
            }
            Category target = findCategory(targetCategoryId);
            migrateBooks(category, target);
        }
        categoryRepository.deleteById(id);
        return "删除成功";
    }

    // 合并分类：把当前分类下的图书迁移到目标分类，然后删除当前分类。
    @PostMapping("/{id}/merge")
    @Transactional
    public Map<String, Object> merge(
            @PathVariable Long id,
            @RequestBody MergeCategoryRequest request
    ) {
        Category source = findCategory(id);
        if (request == null || request.targetCategoryId() == null) {
            throw new RuntimeException("请选择要合并到的目标分类");
        }
        Category target = findCategory(request.targetCategoryId());
        int movedCount = migrateBooks(source, target);
        categoryRepository.deleteById(source.getId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sourceCategory", source.getName());
        result.put("targetCategory", target.getName());
        result.put("movedCount", movedCount);
        return result;
    }

    // 按 id 查询分类，不存在时抛出业务提示。
    private Category findCategory(Long id) {
        return categoryRepository.findById(id).orElseThrow(() -> new RuntimeException("分类不存在"));
    }

    // 把分类实体转换成前端列表行。
    private Map<String, Object> categoryView(Category category) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", category.getId());
        item.put("name", category.getName());
        item.put("description", category.getDescription());
        item.put("createdAt", category.getCreatedAt());
        item.put("bookCount", countBooks(category));
        return item;
    }

    // 把图书实体转换成分类图书明细行。
    private Map<String, Object> bookView(Book book) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", book.getId());
        item.put("isbn", book.getIsbn());
        item.put("title", book.getTitle());
        item.put("author", book.getAuthor());
        item.put("category", book.getCategory());
        item.put("shelfLocation", book.getShelfLocation());
        item.put("totalCount", book.getTotalCount());
        item.put("availableCount", book.getAvailableCount());
        item.put("status", book.getStatus());
        return item;
    }

    // 统计某个分类下的图书数量。
    private long countBooks(Category category) {
        return bookRepository.countByCategoryIdOrCategory(category.getId(), category.getName());
    }

    // 分类改名后，同步图书表里的冗余分类名称。
    private void syncBookCategoryName(Category category, String oldName) {
        List<Book> books = bookRepository.findByCategoryIdOrCategory(category.getId(), oldName);
        books.forEach(book -> {
            book.setCategoryId(category.getId());
            book.setCategory(category.getName());
        });
        bookRepository.saveAll(books);
    }

    // 把源分类下的图书迁移到目标分类。
    private int migrateBooks(Category source, Category target) {
        if (Objects.equals(source.getId(), target.getId())) {
            throw new RuntimeException("目标分类不能和当前分类相同");
        }
        List<Book> books = bookRepository.findByCategoryIdOrCategory(source.getId(), source.getName());
        books.forEach(book -> {
            book.setCategoryId(target.getId());
            book.setCategory(target.getName());
        });
        bookRepository.saveAll(books);
        return books.size();
    }

    // 分类列表固定按编号降序，最新创建的分类排在前面。
    private Page<Category> categoryPage(String keyword, Integer page, Integer size) {
        PageRequest pageRequest = PageRequest.of(Math.max(0, page == null ? 0 : page), libraryProperties.normalizePageSize(size));
        String value = normalize(keyword);
        if (value == null || value.isBlank()) {
            return categoryRepository.findAllByOrderByIdDesc(pageRequest);
        }
        return categoryRepository.findByNameContainingOrderByIdDesc(value, pageRequest);
    }

    // 不分页的分类列表仅用于下拉框，仍保持编号降序。
    private List<Category> categoryList(String keyword) {
        String value = normalize(keyword);
        if (value == null || value.isBlank()) {
            return categoryRepository.findAllByOrderByIdDesc();
        }
        return categoryRepository.findByNameContainingOrderByIdDesc(value);
    }

    // 查询参数统一去掉前后空格。
    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    // 校验分类必填字段。
    private void validateCategory(Category category) {
        if (category.getName() == null || category.getName().trim().isEmpty()) {
            throw new RuntimeException("分类名称不能为空");
        }
    }

    // 合并分类请求体。
    public static class MergeCategoryRequest {
        private Long targetCategoryId;

        public Long getTargetCategoryId() {
            return targetCategoryId;
        }

        public void setTargetCategoryId(Long targetCategoryId) {
            this.targetCategoryId = targetCategoryId;
        }

        public Long targetCategoryId() {
            return targetCategoryId;
        }
    }
}
