package com.example.demo.repository;

import com.example.demo.entity.BookCopy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookCopyRepository extends JpaRepository<BookCopy, Long> {
    List<BookCopy> findByBookIdOrderByCopyCodeAsc(Long bookId);

    List<BookCopy> findByBookIdAndStatusOrderByCopyCodeAsc(Long bookId, String status);

    Optional<BookCopy> findFirstByBookIdAndStatusOrderByCopyCodeAsc(Long bookId, String status);

    Optional<BookCopy> findByCurrentBorrowRecordId(Long currentBorrowRecordId);

    long countByBookId(Long bookId);

    long countByBookIdAndStatus(Long bookId, String status);

    void deleteByBookId(Long bookId);
}
