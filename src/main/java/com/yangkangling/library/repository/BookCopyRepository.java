package com.yangkangling.library.repository;

import com.yangkangling.library.entity.BookCopy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// 单册馆藏数据访问。
public interface BookCopyRepository extends JpaRepository<BookCopy, Long> {
    // 查询某本图书的全部单册，按单册编号升序。
    List<BookCopy> findByBookIdOrderByCopyCodeAsc(Long bookId);

    // 查询某本图书指定状态的单册。
    List<BookCopy> findByBookIdAndStatusOrderByCopyCodeAsc(Long bookId, String status);

    // 查询某本图书前几本可借单册，用于并发占用时少量重试。
    List<BookCopy> findTop10ByBookIdAndStatusOrderByCopyCodeAsc(Long bookId, String status);

    // 查询某本图书指定书架前几本可借单册。
    List<BookCopy> findTop10ByBookIdAndStatusAndShelfLocationOrderByCopyCodeAsc(Long bookId, String status, String shelfLocation);

    // 查询某本图书第一本指定状态单册，借书时取第一本 available。
    Optional<BookCopy> findFirstByBookIdAndStatusOrderByCopyCodeAsc(Long bookId, String status);

    // 借书时按状态条件占用单册，避免并发请求同时拿到同一本。
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update BookCopy c " +
            "set c.status = 'borrowed', " +
            "c.currentUserId = :userId, " +
            "c.updatedAt = :updatedAt " +
            "where c.id = :id " +
            "and c.status = 'available'")
    int markBorrowedIfAvailable(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    // 通过当前借阅记录 id 查找被占用的单册。
    Optional<BookCopy> findByCurrentBorrowRecordId(Long currentBorrowRecordId);

    Optional<BookCopy> findByCopyCode(String copyCode);

    // 统计某本图书的单册总数。
    long countByBookId(Long bookId);

    // 统计某本图书指定状态单册数量。
    long countByBookIdAndStatus(Long bookId, String status);

    // 删除某本图书的全部单册。
    void deleteByBookId(Long bookId);
}
