package com.example.demo.repository;

import com.example.demo.entity.BorrowRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface BorrowRecordRepository extends JpaRepository<BorrowRecord, Long> {
    long countByUserIdAndStatus(Long userId, String status);

    boolean existsByUserIdAndBookIdAndStatus(Long userId, Long bookId, String status);

    boolean existsByUserIdAndStatusAndDueDateBefore(Long userId, String status, LocalDate date);

    boolean existsByBookId(Long bookId);

    List<BorrowRecord> findByStatus(String status);

    List<BorrowRecord> findByStatusAndDueDateBeforeOrderByDueDateAsc(String status, LocalDate date);

    long countByStatus(String status);

    long countByStatusAndDueDateBefore(String status, LocalDate date);

    List<BorrowRecord> findTop10ByOrderByIdDesc();

    List<BorrowRecord> findTop10ByBookIdOrderByIdDesc(Long bookId);

    List<BorrowRecord> findByUserIdOrderByIdDesc(Long userId);

    List<BorrowRecord> findByBookIdOrderByIdDesc(Long bookId);

    List<BorrowRecord> findByBookIdOrderByBorrowDateAscIdAsc(Long bookId);

    @Query("""
            select r from BorrowRecord r
            where (:status is null or :status = '' or r.status = :status)
            and (:userId is null or r.userId = :userId)
            and (:bookId is null or r.bookId = :bookId)
            order by r.id desc
            """)
    Page<BorrowRecord> search(
            @Param("status") String status,
            @Param("userId") Long userId,
            @Param("bookId") Long bookId,
            Pageable pageable
    );
}
