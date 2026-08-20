package com.example.demo.controller;

import com.example.demo.config.LibraryProperties;
import com.example.demo.entity.BorrowRecord;
import com.example.demo.entity.User;
import com.example.demo.repository.BookRepository;
import com.example.demo.repository.BorrowRecordRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.BorrowRecordViewService;
import com.example.demo.service.BorrowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BorrowControllerFineTest {
    @Mock
    private BorrowService borrowService;
    @Mock
    private BorrowRecordRepository borrowRecordRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private BookRepository bookRepository;
    @Mock
    private BorrowRecordViewService borrowRecordViewService;
    @Mock
    private LibraryProperties libraryProperties;

    private BorrowController controller;

    @BeforeEach
    void setUp() {
        controller = new BorrowController(
                borrowService,
                borrowRecordRepository,
                userRepository,
                bookRepository,
                borrowRecordViewService,
                libraryProperties
        );
    }

    @Test
    void payingFineMarksRecordPaidAndReenablesReaderWhenNoOtherBlockers() {
        BorrowRecord record = returnedOverdueRecord();
        User reader = disabledReader();

        when(borrowRecordRepository.findByIdForUpdate(16L)).thenReturn(Optional.of(record));
        when(borrowRecordViewService.fineAmount(record)).thenReturn(new BigDecimal("2.00"));
        when(borrowRecordViewService.fineStatus(record)).thenAnswer(invocation -> record.getFineStatus());
        when(userRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(reader));
        when(borrowRecordRepository.findByUserIdOrderByIdDesc(3L)).thenReturn(List.of(record));
        when(borrowRecordViewService.toView(record)).thenAnswer(invocation -> {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("fineStatus", record.getFineStatus());
            view.put("readerStatus", reader.getStatus());
            return view;
        });

        Map<String, Object> result = controller.markFinePaid(16L);

        assertEquals(new BigDecimal("2.00"), record.getFineAmount());
        assertEquals("paid", record.getFineStatus());
        assertEquals("paid", record.getFineStatus());
        assertEquals("enabled", reader.getStatus());
        assertEquals(new BigDecimal("2.00"), result.get("paidAmount"));
        verify(borrowRecordRepository).save(record);
        verify(userRepository).save(reader);
    }

    @Test
    void payingFineRequiresReturnedRecord() {
        BorrowRecord record = borrowedOverdueRecord(16L);

        when(borrowRecordRepository.findByIdForUpdate(16L)).thenReturn(Optional.of(record));
        when(borrowRecordViewService.fineAmount(record)).thenReturn(new BigDecimal("2.00"));

        assertThrows(RuntimeException.class, () -> controller.markFinePaid(16L));

        verifyNoInteractions(userRepository);
    }

    @Test
    void payingFineKeepsReaderFrozenWhenOtherOverdueBorrowExists() {
        BorrowRecord record = returnedOverdueRecord();
        BorrowRecord blocker = borrowedOverdueRecord(18L);
        User reader = disabledReader();

        when(borrowRecordRepository.findByIdForUpdate(16L)).thenReturn(Optional.of(record));
        when(borrowRecordViewService.fineAmount(record)).thenReturn(new BigDecimal("2.00"));
        when(borrowRecordViewService.fineStatus(record)).thenAnswer(invocation -> record.getFineStatus());
        when(userRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(reader));
        when(borrowRecordRepository.findByUserIdOrderByIdDesc(3L)).thenReturn(List.of(record, blocker));
        when(borrowRecordViewService.toView(record)).thenAnswer(invocation -> {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("fineStatus", record.getFineStatus());
            view.put("readerStatus", reader.getStatus());
            return view;
        });

        controller.markFinePaid(16L);

        assertEquals("paid", record.getFineStatus());
        assertEquals("disabled", reader.getStatus());
        verify(userRepository, never()).save(reader);
        verify(borrowRecordRepository).save(record);
    }

    private BorrowRecord returnedOverdueRecord() {
        BorrowRecord record = new BorrowRecord();
        record.setId(16L);
        record.setUserId(3L);
        record.setStatus("returned");
        record.setFineStatus("unpaid");
        return record;
    }

    private BorrowRecord borrowedOverdueRecord(Long id) {
        BorrowRecord record = new BorrowRecord();
        record.setId(id);
        record.setUserId(3L);
        record.setStatus("borrowed");
        record.setDueDate(LocalDate.now().minusDays(1));
        record.setFineStatus("unpaid");
        return record;
    }

    private User disabledReader() {
        User reader = new User();
        reader.setId(3L);
        reader.setRole("reader");
        reader.setStatus("disabled");
        return reader;
    }
}
