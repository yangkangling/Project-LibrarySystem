package com.example.demo.controller;

import com.example.demo.entity.Category;
import com.example.demo.repository.BookRepository;
import com.example.demo.repository.CategoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
import java.util.Map;

@RestController
@RequestMapping("/categories")
public class CategoryController {
    private final CategoryRepository categoryRepository;
    private final BookRepository bookRepository;

    public CategoryController(CategoryRepository categoryRepository, BookRepository bookRepository) {
        this.categoryRepository = categoryRepository;
        this.bookRepository = bookRepository;
    }

    @GetMapping
    public Object list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(defaultValue = "10") Integer size
    ) {
        if (page == null) {
            return categoryRepository.findAll().stream().map(this::categoryView).toList();
        }
        if (keyword == null || keyword.trim().isEmpty()) {
            return categoryRepository.findAllByOrderByIdDesc(PageRequest.of(page, size)).map(this::categoryView);
        }
        return categoryRepository.findByNameContainingOrderByIdDesc(keyword.trim(), PageRequest.of(page, size)).map(this::categoryView);
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        Category category = findCategory(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("category", category);
        result.put("bookCount", bookRepository.countByCategory(category.getName()));
        return result;
    }

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

    @PutMapping("/{id}")
    public Category update(@PathVariable Long id, @RequestBody Category input) {
        Category category = findCategory(id);
        validateCategory(input);
        String nextName = input.getName().trim();

        categoryRepository.findByName(nextName).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new RuntimeException("分类名称已存在，请更换后再保存");
            }
        });

        category.setName(nextName);
        category.setDescription(input.getDescription());
        return categoryRepository.save(category);
    }

    @DeleteMapping("/{id}")
    public String delete(@PathVariable Long id) {
        Category category = findCategory(id);
        if (bookRepository.existsByCategory(category.getName())) {
            throw new RuntimeException("该分类下已有图书，不能删除，请先调整相关图书分类");
        }
        categoryRepository.deleteById(id);
        return "删除成功";
    }

    private Category findCategory(Long id) {
        return categoryRepository.findById(id).orElseThrow(() -> new RuntimeException("分类不存在"));
    }

    private Map<String, Object> categoryView(Category category) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", category.getId());
        item.put("name", category.getName());
        item.put("description", category.getDescription());
        item.put("createdAt", category.getCreatedAt());
        item.put("bookCount", bookRepository.countByCategory(category.getName()));
        return item;
    }

    private void validateCategory(Category category) {
        if (category.getName() == null || category.getName().trim().isEmpty()) {
            throw new RuntimeException("分类名称不能为空");
        }
    }
}
