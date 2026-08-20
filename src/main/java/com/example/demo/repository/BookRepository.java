package com.example.demo.repository;

import com.example.demo.entity.Book;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

// 图书主表数据访问。
public interface BookRepository extends JpaRepository<Book, Long> {
    // 判断 ISBN 是否已经存在。
    boolean existsByIsbn(String isbn);

    // 按旧分类名称判断是否存在图书。
    boolean existsByCategory(String category);

    // 按分类 id 判断是否存在图书。
    boolean existsByCategoryId(Long categoryId);

    // 按分类 id 或分类名称判断是否存在图书，兼容旧数据。
    boolean existsByCategoryIdOrCategory(Long categoryId, String category);

    // 按分类名称统计图书数量。
    long countByCategory(String category);

    // 按分类 id 统计图书数量。
    long countByCategoryId(Long categoryId);

    // 按分类 id 或分类名称统计图书数量。
    long countByCategoryIdOrCategory(Long categoryId, String category);

    // 按 ISBN 查询图书。
    Optional<Book> findByIsbn(String isbn);

    // 汇总馆藏总册数。
    @Query("select coalesce(sum(b.totalCount), 0) from Book b")
    Long sumTotalCount();

    // 汇总当前可借册数。
    @Query("select coalesce(sum(b.availableCount), 0) from Book b")
    Long sumAvailableCount();

    // 按分类汇总馆藏、可借和已借数量。
    @Query("select b.category, coalesce(sum(b.totalCount), 0), coalesce(sum(b.availableCount), 0) " +
            "from Book b " +
            "group by b.category")
    List<Object[]> categoryInventoryStats();

    // 查询某分类下的图书，用于分类改名时同步图书分类名称。
    List<Book> findByCategoryIdOrCategory(Long categoryId, String category);

    // 分页查询某分类下的图书明细。
    @Query("select b from Book b " +
            "where (b.categoryId = :categoryId or b.category = :category) " +
            "and (:keyword is null or :keyword = '' " +
            "or b.isbn like concat('%', :keyword, '%') " +
            "or b.title like concat('%', :keyword, '%') " +
            "or b.author like concat('%', :keyword, '%') " +
            "or b.shelfLocation like concat('%', :keyword, '%')) " +
            "order by b.id desc")
    Page<Book> searchByCategory(
            @Param("categoryId") Long categoryId,
            @Param("category") String category,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    // 不分页的图书关键字查询，兼容旧接口。
    @Query("select distinct b from Book b " +
            "where (:keyword is null or :keyword = '' " +
            "or b.title like concat('%', :keyword, '%') " +
            "or b.author like concat('%', :keyword, '%') " +
            "or b.isbn like concat('%', :keyword, '%') " +
            "or b.shelfLocation like concat('%', :keyword, '%') " +
            "or exists ( " +
            "select 1 from StorageLocation s " +
            "where s.bookId = b.id " +
            "and s.shelfLocation like concat('%', :keyword, '%') " +
            ")) " +
            "order by b.id desc")
    List<Book> searchAll(@Param("keyword") String keyword);

    // 借书时原子扣减可借数量，只有启用且有库存才会成功。
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Book b " +
            "set b.availableCount = b.availableCount - 1 " +
            "where b.id = :id " +
            "and b.status = 'enabled' " +
            "and b.availableCount > 0")
    int decreaseAvailableCountWhenAvailable(@Param("id") Long id);

    // 还书时原子恢复可借数量，但不能超过馆藏总数。
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Book b " +
            "set b.availableCount = b.availableCount + 1 " +
            "where b.id = :id " +
            "and b.availableCount < b.totalCount")
    int increaseAvailableCountWithinTotal(@Param("id") Long id);

    // 管理端和读者端共用的分页图书查询。
    @Query("select distinct b from Book b " +
            "where (:keyword is null or :keyword = '' " +
            "or b.title like concat('%', :keyword, '%') " +
            "or b.author like concat('%', :keyword, '%') " +
            "or b.isbn like concat('%', :keyword, '%') " +
            "or b.shelfLocation like concat('%', :keyword, '%') " +
            "or exists ( " +
            "select 1 from StorageLocation s " +
            "where s.bookId = b.id " +
            "and s.shelfLocation like concat('%', :keyword, '%') " +
            ")) " +
            "and (:categoryId is null or b.categoryId = :categoryId) " +
            "and (:category is null or :category = '' or b.category = :category) " +
            "and (:status is null or :status = '' or b.status = :status) " +
            "order by b.id desc")
    Page<Book> search(
            // 关键字，可匹配书名、作者、ISBN、书架。
            @Param("keyword") String keyword,
            // 分类 id。
            @Param("categoryId") Long categoryId,
            // 分类名称，兼容旧数据。
            @Param("category") String category,
            // 图书状态。
            @Param("status") String status,
            // 分页参数。
            Pageable pageable
    );
}
