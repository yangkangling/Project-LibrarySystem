package com.example.demo.repository;

import com.example.demo.entity.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// 图书分类数据访问。
public interface CategoryRepository extends JpaRepository<Category, Long> {
    // 判断分类名称是否存在。
    boolean existsByName(String name);

    // 按分类名称查询分类。
    Optional<Category> findByName(String name);

    // 按分类名称模糊查询并分页。
    Page<Category> findByNameContainingOrderByIdDesc(String name, Pageable pageable);

    // 按分类名称模糊查询并按 id 倒序列表。
    List<Category> findByNameContainingOrderByIdDesc(String name);

    // 查询全部分类并按 id 倒序分页。
    Page<Category> findAllByOrderByIdDesc(Pageable pageable);

    // 查询全部分类并按 id 倒序列表。
    List<Category> findAllByOrderByIdDesc();
}
