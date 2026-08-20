package com.yangkangling.library.controller;

import com.yangkangling.library.config.LibraryProperties;
import com.yangkangling.library.dto.BookRequest;
import com.yangkangling.library.entity.Book;
import com.yangkangling.library.entity.Category;
import com.yangkangling.library.repository.BookCopyRepository;
import com.yangkangling.library.repository.BookRepository;
import com.yangkangling.library.repository.BorrowRecordRepository;
import com.yangkangling.library.repository.CategoryRepository;
import com.yangkangling.library.repository.UserRepository;
import com.yangkangling.library.service.BookCopyService;
import com.yangkangling.library.service.BorrowRecordViewService;
import com.yangkangling.library.service.StorageLocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookControllerStatusTest {
    @Mock
    private BookRepository bookRepository;
    @Mock
    private BookCopyRepository bookCopyRepository;
    @Mock
    private BorrowRecordRepository borrowRecordRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private BorrowRecordViewService borrowRecordViewService;
    @Mock
    private BookCopyService bookCopyService;
    @Mock
    private StorageLocationService storageLocationService;
    @Mock
    private LibraryProperties libraryProperties;

    private BookController controller;

    @BeforeEach
    void setUp() {
        controller = new BookController(
                bookRepository,
                bookCopyRepository,
                borrowRecordRepository,
                categoryRepository,
                userRepository,
                borrowRecordViewService,
                bookCopyService,
                storageLocationService,
                libraryProperties
        );
    }

    @Test
    void listAddsActiveBorrowCountForDisableWarning() {
        Book book = book();
        when(libraryProperties.normalizePageSize(10)).thenReturn(10);
        when(bookRepository.search(isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(book)));
        when(borrowRecordRepository.countByBookIdAndStatus(7L, "borrowed")).thenReturn(2L);

        @SuppressWarnings("unchecked")
        Page<Book> result = (Page<Book>) controller.list(null, null, null, null, 0, 10);

        assertEquals(2L, result.getContent().get(0).getActiveBorrowCount());
        verify(borrowRecordRepository).countByBookIdAndStatus(7L, "borrowed");
    }

    @Test
    void disableReturnsOutstandingLoanCount() {
        Book book = book();
        when(bookRepository.findById(7L)).thenReturn(Optional.of(book));
        when(bookRepository.save(book)).thenReturn(book);
        when(borrowRecordRepository.countByBookIdAndStatus(7L, "borrowed")).thenReturn(2L);

        Book result = controller.disable(7L);

        assertEquals("disabled", result.getStatus());
        assertEquals(2L, result.getActiveBorrowCount());
    }

    @Test
    void addRejectsInitialStockAboveLimit() {
        BookRequest input = bookInput();
        input.setTotalCount(51);

        assertThrows(RuntimeException.class, () -> controller.add(input));
    }

    @Test
    void updateIgnoresSubmittedStockCount() {
        Book existing = book();
        existing.setTotalCount(3);
        existing.setAvailableCount(2);
        BookRequest input = bookInput();
        input.setTotalCount(50);
        Category category = category();

        when(bookRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(bookRepository.findByIsbn("9780000000001")).thenReturn(Optional.of(existing));
        when(bookRepository.save(existing)).thenReturn(existing);

        Book result = controller.update(7L, input);

        assertEquals(3, result.getTotalCount());
        assertEquals(2, result.getAvailableCount());
        verify(bookRepository).save(existing);
    }

    private Book book() {
        Book book = new Book();
        book.setId(7L);
        book.setIsbn("9780000000001");
        book.setTitle("Test Book");
        book.setAuthor("Test Author");
        book.setStatus("enabled");
        book.setTotalCount(3);
        book.setAvailableCount(1);
        return book;
    }

    private BookRequest bookInput() {
        BookRequest request = new BookRequest();
        request.setIsbn("9780000000001");
        request.setTitle("Test Book");
        request.setAuthor("Test Author");
        request.setStatus("enabled");
        request.setTotalCount(3);
        request.setCategoryId(1L);
        request.setShelfLocation("A-01-01");
        return request;
    }

    private Category category() {
        Category category = new Category();
        category.setId(1L);
        category.setName("文学");
        return category;
    }
}
