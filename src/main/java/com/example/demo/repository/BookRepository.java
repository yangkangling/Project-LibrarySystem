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

public interface BookRepository extends JpaRepository<Book, Long> {
    boolean existsByIsbn(String isbn);

    boolean existsByCategory(String category);

    boolean existsByCategoryId(Long categoryId);

    boolean existsByCategoryIdOrCategory(Long categoryId, String category);

    long countByCategory(String category);

    long countByCategoryId(Long categoryId);

    long countByCategoryIdOrCategory(Long categoryId, String category);

    Optional<Book> findByIsbn(String isbn);

    List<Book> findByCategoryIdOrCategory(Long categoryId, String category);

    @Query("""
            select distinct b from Book b
            where (:keyword is null or :keyword = ''
                or b.title like concat('%', :keyword, '%')
                or b.author like concat('%', :keyword, '%')
                or b.isbn like concat('%', :keyword, '%')
                or b.shelfLocation like concat('%', :keyword, '%')
                or exists (
                    select 1 from StorageLocation s
                    where s.bookId = b.id
                    and s.shelfLocation like concat('%', :keyword, '%')
                ))
            order by b.id desc
            """)
    List<Book> searchAll(@Param("keyword") String keyword);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Book b
            set b.availableCount = b.availableCount - 1
            where b.id = :id
            and b.status = 'enabled'
            and b.availableCount > 0
            """)
    int decreaseAvailableCountWhenAvailable(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Book b
            set b.availableCount = b.availableCount + 1
            where b.id = :id
            and b.availableCount < b.totalCount
            """)
    int increaseAvailableCountWithinTotal(@Param("id") Long id);

    @Query("""
            select distinct b from Book b
            where (:keyword is null or :keyword = ''
                or b.title like concat('%', :keyword, '%')
                or b.author like concat('%', :keyword, '%')
                or b.isbn like concat('%', :keyword, '%')
                or b.shelfLocation like concat('%', :keyword, '%')
                or exists (
                    select 1 from StorageLocation s
                    where s.bookId = b.id
                    and s.shelfLocation like concat('%', :keyword, '%')
                ))
            and (:categoryId is null or b.categoryId = :categoryId)
            and (:category is null or :category = '' or b.category = :category)
            and (:status is null or :status = '' or b.status = :status)
            order by b.id desc
            """)
    Page<Book> search(
            @Param("keyword") String keyword,
            @Param("categoryId") Long categoryId,
            @Param("category") String category,
            @Param("status") String status,
            Pageable pageable
    );
}
