package com.yangkangling.library.controller;

import com.yangkangling.library.config.LibraryProperties;
import com.yangkangling.library.entity.BorrowRecord;
import com.yangkangling.library.entity.User;
import com.yangkangling.library.repository.BookRepository;
import com.yangkangling.library.repository.BorrowRecordRepository;
import com.yangkangling.library.repository.CategoryRepository;
import com.yangkangling.library.repository.UserRepository;
import com.yangkangling.library.service.BorrowRecordViewService;
import com.yangkangling.library.service.BorrowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SelfServiceControllerSecurityTest {
    @Mock
    private UserRepository userRepository;
    @Mock
    private BookRepository bookRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private BorrowRecordRepository borrowRecordRepository;
    @Mock
    private BorrowRecordViewService borrowRecordViewService;
    @Mock
    private BorrowService borrowService;
    @Mock
    private LibraryProperties libraryProperties;

    private SelfServiceController controller;

    @BeforeEach
    void setUp() {
        controller = new SelfServiceController(
                userRepository,
                bookRepository,
                categoryRepository,
                borrowRecordRepository,
                borrowRecordViewService,
                borrowService,
                libraryProperties
        );
    }

    @Test
    void readerCannotReturnAnotherReadersBorrowRecord() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("readerId", 1L);
        User currentReader = new User();
        currentReader.setId(1L);
        currentReader.setRole("reader");
        currentReader.setStatus("enabled");

        BorrowRecord otherReadersRecord = new BorrowRecord();
        otherReadersRecord.setId(9L);
        otherReadersRecord.setUserId(2L);

        SelfServiceController.ReturnRequest request = new SelfServiceController.ReturnRequest();
        request.setRecordIds(List.of(9L));

        when(userRepository.findById(1L)).thenReturn(Optional.of(currentReader));
        when(borrowRecordRepository.findAllById(List.of(9L))).thenReturn(List.of(otherReadersRecord));

        assertThrows(RuntimeException.class, () -> controller.returnBooks(request, session));

        verifyNoInteractions(borrowService);
    }
}
