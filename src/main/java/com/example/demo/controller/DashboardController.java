package com.example.demo.controller;

import com.example.demo.repository.BookRepository;
import com.example.demo.repository.BorrowRecordRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 管理端工作台统计接口。
@RestController
@RequestMapping("/dashboard")
public class DashboardController {
    // 图书数据仓库，用来统计图书种类、馆藏总量和可借数量。
    private final BookRepository bookRepository;
    // 用户数据仓库，用来统计读者数量。
    private final UserRepository userRepository;
    // 借阅记录仓库，用来统计借出、逾期和近期借阅记录。
    private final BorrowRecordRepository borrowRecordRepository;

    // 通过构造方法注入工作台需要的仓库和服务。
    public DashboardController(
            BookRepository bookRepository,
            UserRepository userRepository,
            BorrowRecordRepository borrowRecordRepository
    ) {
        // 保存图书仓库对象。
        this.bookRepository = bookRepository;
        // 保存用户仓库对象。
        this.userRepository = userRepository;
        // 保存借阅记录仓库对象。
        this.borrowRecordRepository = borrowRecordRepository;
    }

    // 查询工作台首页统计数据。
    @GetMapping
    public Map<String, Object> dashboard() {
        // 使用有序 Map 返回，保证前端拿到的字段顺序稳定。
        Map<String, Object> data = new LinkedHashMap<>();
        // 图书种类数量。
        data.put("bookTypes", bookRepository.count());
        // 馆藏总册数，把每本书的 totalCount 累加。
        data.put("totalBooks", safeLong(bookRepository.sumTotalCount()));
        // 当前可借总册数，把每本书的 availableCount 累加。
        data.put("availableBooks", safeLong(bookRepository.sumAvailableCount()));
        // 当前借出未还的册数。
        data.put("borrowedBooks", borrowRecordRepository.countByStatus("borrowed"));
        // 读者账号数量。
        data.put("readers", userRepository.countByRole("reader"));
        // 当前逾期未还数量：状态为 borrowed 且应还日期早于今天。
        data.put("overdue", borrowRecordRepository.countByStatusAndDueDateBefore("borrowed", LocalDate.now()));
        // 借阅记录总数，用来核对状态分布有没有漏算。
        data.put("borrowRecordTotal", borrowRecordRepository.count());
        // 分类馆藏统计，用于饼图和柱状图。
        data.put("categoryStats", categoryStats());
        // 近 7 天借还趋势，用于折线图。
        data.put("borrowTrend", borrowTrend());
        // 借阅状态分布，用于状态统计图。
        data.put("statusStats", statusStats());
        // 返回工作台统计结果。
        return data;
    }

    // 按分类汇总馆藏、可借和已借数量。
    private List<Map<String, Object>> categoryStats() {
        return bookRepository.categoryInventoryStats().stream()
                .map(row -> {
                    String category = hasText((String) row[0]) ? ((String) row[0]).trim() : "未分类";
                    long total = safeNumber(row[1]);
                    long available = safeNumber(row[2]);
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", category);
                    item.put("total", total);
                    item.put("available", available);
                    item.put("borrowed", Math.max(0, total - available));
                    return item;
                })
                .collect(java.util.stream.Collectors.toList());
    }

    // 统计最近 7 天借书和还书数量。
    private List<Map<String, Object>> borrowTrend() {
        LocalDate start = LocalDate.now().minusDays(6);
        return start.datesUntil(LocalDate.now().plusDays(1))
                .map(date -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("date", date.toString());
                    item.put("borrowCount", borrowRecordRepository.countByBorrowDate(date));
                    item.put("returnCount", borrowRecordRepository.countByReturnDate(date));
                    return item;
                })
                .collect(java.util.stream.Collectors.toList());
    }

    // 按页面展示状态汇总借阅记录。
    private List<Map<String, Object>> statusStats() {
        long overdue = borrowRecordRepository.countByStatusAndDueDateBefore("borrowed", LocalDate.now());
        long borrowed = Math.max(0, borrowRecordRepository.countByStatus("borrowed") - overdue);
        long returned = borrowRecordRepository.countByStatus("returned");
        Map<String, Long> grouped = new LinkedHashMap<>();
        grouped.put("borrowed", borrowed);
        grouped.put("returned", returned);
        grouped.put("overdue", overdue);
        return grouped.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", entry.getKey());
                    item.put("value", entry.getValue());
                    return item;
                })
                .collect(java.util.stream.Collectors.toList());
    }

    // 把可能为空的数量转换成 0，避免统计时报空指针。
    private long safeLong(Long value) {
        return value == null ? 0 : value;
    }

    // 把聚合查询返回的数字转换成长整型。
    private long safeNumber(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    // 判断文本是否有内容。
    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
