package com.example.demo.repository;

import com.example.demo.entity.Book;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface BookRepository extends JpaRepository<Book, Long> {
    boolean existsByIsbn(String isbn);

    boolean existsByCategory(String category);

    long countByCategory(String category);

    Optional<Book> findByIsbn(String isbn);

    List<Book> findByTitleContainingOrAuthorContainingOrIsbnContaining(String title, String author, String isbn);

    @Query("""
            select b from Book b
            where (:keyword is null or :keyword = ''
                or b.title like concat('%', :keyword, '%')
                or b.author like concat('%', :keyword, '%')
                or b.isbn like concat('%', :keyword, '%'))
            and (:category is null or :category = '' or b.category = :category)
            and (:status is null or :status = '' or b.status = :status)
            order by b.id desc
            """)
    Page<Book> search(
            @Param("keyword") String keyword,
            @Param("category") String category,
            @Param("status") String status,
            Pageable pageable
    );
}
