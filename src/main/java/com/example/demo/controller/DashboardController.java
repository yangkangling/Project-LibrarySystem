package com.example.demo.controller;

import com.example.demo.repository.BookRepository;
import com.example.demo.repository.BorrowRecordRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.BorrowRecordViewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final BorrowRecordRepository borrowRecordRepository;
    private final BorrowRecordViewService borrowRecordViewService;

    public DashboardController(
            BookRepository bookRepository,
            UserRepository userRepository,
            BorrowRecordRepository borrowRecordRepository,
            BorrowRecordViewService borrowRecordViewService
    ) {
        this.bookRepository = bookRepository;
        this.userRepository = userRepository;
        this.borrowRecordRepository = borrowRecordRepository;
        this.borrowRecordViewService = borrowRecordViewService;
    }

    @GetMapping
    public Map<String, Object> dashboard() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bookTypes", bookRepository.count());
        data.put("totalBooks", bookRepository.findAll().stream().mapToInt(book -> safeInt(book.getTotalCount())).sum());
        data.put("availableBooks", bookRepository.findAll().stream().mapToInt(book -> safeInt(book.getAvailableCount())).sum());
        data.put("borrowedBooks", borrowRecordRepository.countByStatus("borrowed"));
        data.put("readers", userRepository.countByRole("reader"));
        data.put("overdue", borrowRecordRepository.countByStatusAndDueDateBefore("borrowed", LocalDate.now()));
        data.put("recentRecords", borrowRecordRepository.findTop10ByOrderByIdDesc().stream()
                .map(borrowRecordViewService::toView)
                .toList());
        return data;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }
}
