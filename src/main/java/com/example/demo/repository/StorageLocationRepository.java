package com.example.demo.repository;

import com.example.demo.entity.StorageLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface StorageLocationRepository extends JpaRepository<StorageLocation, Long> {
    List<StorageLocation> findByBookIdOrderByIdAsc(Long bookId);

    Optional<StorageLocation> findFirstByBookIdOrderByIdAsc(Long bookId);

    Optional<StorageLocation> findFirstByBookIdAndShelfLocationOrderByIdAsc(Long bookId, String shelfLocation);

    Optional<StorageLocation> findFirstByBookIdAndAvailableCountGreaterThanOrderByIdAsc(Long bookId, Integer availableCount);

    long countByBookId(Long bookId);

    void deleteByBookId(Long bookId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update StorageLocation s
            set s.availableCount = s.availableCount - 1,
                s.updatedAt = :updatedAt
            where s.id = :id
            and s.availableCount > 0
            """)
    int decreaseAvailableCountWhenAvailable(@Param("id") Long id, @Param("updatedAt") LocalDateTime updatedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update StorageLocation s
            set s.availableCount = s.availableCount + 1,
                s.updatedAt = :updatedAt
            where s.id = :id
            and s.availableCount < s.totalCount
            """)
    int increaseAvailableCountWithinTotal(@Param("id") Long id, @Param("updatedAt") LocalDateTime updatedAt);
}
