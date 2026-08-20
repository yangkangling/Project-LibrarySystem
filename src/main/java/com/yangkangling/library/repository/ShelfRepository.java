package com.yangkangling.library.repository;

import com.yangkangling.library.entity.Shelf;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

// 独立书架数据访问。
public interface ShelfRepository extends JpaRepository<Shelf, Long> {
    // 按书架位置查询。
    Optional<Shelf> findByShelfLocation(String shelfLocation);

    // 判断书架位置是否已经存在。
    boolean existsByShelfLocation(String shelfLocation);

    // 按书架位置排序查询。
    List<Shelf> findAllByOrderByShelfLocationAsc();

    // 分页查询全部书架。
    Page<Shelf> findAllByOrderByShelfLocationAsc(Pageable pageable);

    // 书架列表搜索，支持书架、备注和关联图书关键字。
    @Query("select sh from Shelf sh " +
            "where (:keyword is null or :keyword = '' " +
            "or sh.shelfLocation like concat('%', :keyword, '%') " +
            "or sh.remark like concat('%', :keyword, '%') " +
            "or exists ( " +
            "select 1 from StorageLocation sl, Book b " +
            "where sl.shelfLocation = sh.shelfLocation " +
            "and b.id = sl.bookId " +
            "and (b.title like concat('%', :keyword, '%') " +
            "or b.isbn like concat('%', :keyword, '%') " +
            "or b.author like concat('%', :keyword, '%') " +
            "or b.category like concat('%', :keyword, '%')) " +
            ")) " +
            "order by sh.shelfLocation asc")
    Page<Shelf> search(
            @Param("keyword") String keyword,
            Pageable pageable
    );
}
