package com.example.demo.repository;

import com.example.demo.entity.StorageLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// 书架位置数据访问。
public interface StorageLocationRepository extends JpaRepository<StorageLocation, Long> {
    // 查询某本图书的全部书架库存。
    List<StorageLocation> findByBookIdOrderByIdAsc(Long bookId);

    // 查询某本图书第一条书架库存，作为主书架。
    Optional<StorageLocation> findFirstByBookIdOrderByIdAsc(Long bookId);

    // 按图书和书架位置查询书架库存。
    Optional<StorageLocation> findFirstByBookIdAndShelfLocationOrderByIdAsc(Long bookId, String shelfLocation);

    // 按书架位置查询关联库存。
    List<StorageLocation> findByShelfLocation(String shelfLocation);

    // 判断书架是否仍有馆藏。
    boolean existsByShelfLocationAndTotalCountGreaterThan(String shelfLocation, Integer totalCount);

    // 查询某本图书第一条还有可借数量的书架库存。
    Optional<StorageLocation> findFirstByBookIdAndAvailableCountGreaterThanOrderByIdAsc(Long bookId, Integer availableCount);

    // 统计某本图书书架库存记录数量。
    long countByBookId(Long bookId);

    // 删除某本图书的书架库存记录。
    void deleteByBookId(Long bookId);

    // 查询所有非空书架位置，用于下拉选择。
    @Query("select distinct s.shelfLocation from StorageLocation s " +
            "where s.shelfLocation is not null " +
            "and s.shelfLocation <> '' " +
            "order by s.shelfLocation")
    List<String> findDistinctShelfLocations();

    // 借书时原子扣减书架可借数量。
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update StorageLocation s " +
            "set s.availableCount = s.availableCount - 1, " +
            "s.updatedAt = :updatedAt " +
            "where s.id = :id " +
            "and s.availableCount > 0")
    int decreaseAvailableCountWhenAvailable(@Param("id") Long id, @Param("updatedAt") LocalDateTime updatedAt);

    // 还书时原子恢复书架可借数量，但不能超过该书架馆藏数量。
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update StorageLocation s " +
            "set s.availableCount = s.availableCount + 1, " +
            "s.updatedAt = :updatedAt " +
            "where s.id = :id " +
            "and s.availableCount < s.totalCount")
    int increaseAvailableCountWithinTotal(@Param("id") Long id, @Param("updatedAt") LocalDateTime updatedAt);
}
