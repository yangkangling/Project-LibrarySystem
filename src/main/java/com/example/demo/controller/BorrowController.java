package com.example.demo.controller;

import com.example.demo.entity.BorrowRecord;
import com.example.demo.entity.Book;
import com.example.demo.entity.User;
import com.example.demo.config.LibraryProperties;
import com.example.demo.repository.BookRepository;
import com.example.demo.repository.BorrowRecordRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.BorrowRecordViewService;
import com.example.demo.service.BorrowService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// 借书、还书、逾期和罚款处理接口。
@RestController
@RequestMapping("/borrow")
public class BorrowController {
    // 默认查看 90 天内的未还风险，避免预警中心长期空表。
    private static final int DEFAULT_WARNING_DAYS = 90;

    // 借还书核心服务，负责库存、单册、书架和借阅记录联动。
    private final BorrowService borrowService;
    // 借阅记录仓库，用于查询和更新借阅记录。
    private final BorrowRecordRepository borrowRecordRepository;
    // 用户仓库，用于查询读者并冻结账号。
    private final UserRepository userRepository;
    // 图书仓库，用于借书候选查询。
    private final BookRepository bookRepository;
    // 借阅记录视图服务，用于统一格式化列表返回值。
    private final BorrowRecordViewService borrowRecordViewService;
    // 系统容量配置，用于限制分页大小。
    private final LibraryProperties libraryProperties;

    // 构造方法注入借还书相关服务和仓库。
    public BorrowController(
            BorrowService borrowService,
            BorrowRecordRepository borrowRecordRepository,
            UserRepository userRepository,
            BookRepository bookRepository,
            BorrowRecordViewService borrowRecordViewService,
            LibraryProperties libraryProperties
    ) {
        this.borrowService = borrowService;
        this.borrowRecordRepository = borrowRecordRepository;
        this.userRepository = userRepository;
        this.bookRepository = bookRepository;
        this.borrowRecordViewService = borrowRecordViewService;
        this.libraryProperties = libraryProperties;
    }

    // 管理端“全部借阅记录”查询，支持状态、读者、图书、日期和关键词组合筛选。
    @GetMapping("/records")
    public Object records(
            // 读者、图书、单册编号等关键字。
            @RequestParam(required = false) String keyword,
            // 借阅状态，如 borrowed、returned、overdue。
            @RequestParam(required = false) String status,
            // 指定读者 id。
            @RequestParam(required = false) Long userId,
            // 指定图书 id。
            @RequestParam(required = false) Long bookId,
            // 借阅日期开始。
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate borrowStart,
            // 借阅日期结束。
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate borrowEnd,
            // 应还日期开始。
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueStart,
            // 应还日期结束。
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueEnd,
            // 页码；为空时返回数组。
            @RequestParam(required = false) Integer page,
            // 每页数量。
            @RequestParam(defaultValue = "10") Integer size
    ) {
        if (page != null) {
            String effectiveStatus = status;
            LocalDate effectiveDueEnd = dueEnd;
            if ("overdue".equals(status)) {
                effectiveStatus = "borrowed";
                LocalDate latestOverdueDueDate = LocalDate.now().minusDays(1);
                effectiveDueEnd = effectiveDueEnd == null || effectiveDueEnd.isAfter(latestOverdueDueDate)
                        ? latestOverdueDueDate
                        : effectiveDueEnd;
            }
            return borrowRecordRepository.searchRecords(
                            normalize(keyword),
                            normalize(effectiveStatus),
                            userId,
                            bookId,
                            borrowStart,
                            borrowEnd,
                            dueStart,
                            effectiveDueEnd,
                            PageRequest.of(Math.max(0, page), libraryProperties.normalizePageSize(size))
                    )
                    .map(borrowRecordViewService::toView);
        }

        // 查询全部记录后按条件过滤，并转换成前端展示格式。
        List<Map<String, Object>> records = borrowRecordRepository.findAll().stream()
                // 过滤状态，兼容实时计算出的 overdue 状态和原始状态。
                .filter(record -> status == null || status.isBlank() || Objects.equals(borrowRecordViewService.recordStatus(record), status) || Objects.equals(record.getStatus(), status))
                // 按读者过滤。
                .filter(record -> userId == null || Objects.equals(record.getUserId(), userId))
                // 按图书过滤。
                .filter(record -> bookId == null || Objects.equals(record.getBookId(), bookId))
                // 借阅日期不能早于开始日期。
                .filter(record -> borrowStart == null || !record.getBorrowDate().isBefore(borrowStart))
                // 借阅日期不能晚于结束日期。
                .filter(record -> borrowEnd == null || !record.getBorrowDate().isAfter(borrowEnd))
                // 应还日期不能早于开始日期。
                .filter(record -> dueStart == null || !record.getDueDate().isBefore(dueStart))
                // 应还日期不能晚于结束日期。
                .filter(record -> dueEnd == null || !record.getDueDate().isAfter(dueEnd))
                // 转为前端行数据。
                .map(borrowRecordViewService::toView)
                // 关键字匹配转换后的行字段。
                .filter(record -> borrowRecordViewService.matchesKeyword(record, keyword))
                // 最新记录放前面。
                .sorted(Comparator.comparing(item -> (Long) item.get("id"), Comparator.reverseOrder()))
                .collect(java.util.stream.Collectors.toList());

        // 不传 page 时兼容旧接口。
        if (page == null) {
            return records;
        }
        // 返回分页结果。
        return toPage(records, page, size);
    }

    // 查询借阅记录详情。
    @GetMapping("/records/{id}")
    public Map<String, Object> recordDetail(@PathVariable Long id) {
        // 先确认记录存在。
        BorrowRecord record = borrowRecordRepository.findById(id).orElseThrow(() -> new RuntimeException("借阅记录不存在"));
        // 转换成详情展示数据。
        return borrowRecordViewService.toDetail(record);
    }

    // 返回所有产生过逾期天数的记录，已归还的逾期记录也保留用于罚款和冻结处理。
    @GetMapping("/overdue")
    @Transactional
    public Object overdue(
            // 读者、图书或单册编号关键字。
            @RequestParam(required = false) String keyword,
            // 页码；为空时返回数组。
            @RequestParam(required = false) Integer page,
            // 每页数量。
            @RequestParam(defaultValue = "10") Integer size
    ) {
        LocalDate today = LocalDate.now();
        if (page != null) {
            return borrowRecordRepository.searchOverdueHistory(
                            today,
                            normalize(keyword),
                            PageRequest.of(Math.max(0, page), libraryProperties.normalizePageSize(size))
                    )
                    .map(record -> {
                        ensureFineDebtRecordedAndReaderFrozen(record);
                        Map<String, Object> item = borrowRecordViewService.toView(record);
                        if (!"returned".equals(record.getStatus())) {
                            item.put("status", "overdue");
                        }
                        item.put("overdueDays", borrowRecordViewService.overdueDays(record));
                        return item;
                    });
        }

        // 兼容旧接口：不分页时仍返回完整列表。
        List<Map<String, Object>> records = borrowRecordRepository.findAll().stream()
                .filter(record -> borrowRecordViewService.overdueDays(record) > 0)
                .map(record -> {
                    ensureFineDebtRecordedAndReaderFrozen(record);
                    // 转成展示行。
                    Map<String, Object> item = borrowRecordViewService.toView(record);
                    // 未还记录显示已逾期，已还记录保留已归还状态，避免再次办理还书。
                    if (!"returned".equals(record.getStatus())) {
                        item.put("status", "overdue");
                    }
                    // 写入逾期天数。
                    item.put("overdueDays", borrowRecordViewService.overdueDays(record));
                    return item;
                })
                // 按关键字过滤。
                .filter(record -> borrowRecordViewService.matchesKeyword(record, keyword))
                // 应还日期早的排前面，便于优先处理拖欠时间久的记录。
                .sorted(Comparator.comparing(item -> (LocalDate) item.get("dueDate"), Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(java.util.stream.Collectors.toList());

        // 不传 page 时返回全部。
        if (page == null) {
            return records;
        }
        // 返回分页结果。
        return toPage(records, page, size);
    }

    // 管理端还书预警：展示已逾期、今天到期和即将到期的未还记录。
    @GetMapping("/warnings")
    public Page<Map<String, Object>> warnings(
            // 读者、图书或单册编号关键字。
            @RequestParam(required = false) String keyword,
            // 提前预警天数，默认 90 天。
            @RequestParam(defaultValue = "90") Integer days,
            // 当前页。
            @RequestParam(defaultValue = "0") Integer page,
            // 每页数量。
            @RequestParam(defaultValue = "10") Integer size
    ) {
        LocalDate today = LocalDate.now();
        // 最大预警日期。
        LocalDate warningEnd = today.plusDays(normalizeWarningDays(days));
        return borrowRecordRepository.searchWarnings(
                        warningEnd,
                        normalize(keyword),
                        PageRequest.of(Math.max(0, page), libraryProperties.normalizePageSize(size))
                )
                .map(record -> toWarningView(record, today));
    }

    // 借书弹窗的读者候选，只返回启用状态的读者账号。
    @GetMapping("/reader-options")
    public Page<Map<String, Object>> readerOptions(
            // 借阅证号、姓名、手机号关键字。
            @RequestParam(required = false) String keyword,
            // 当前页。
            @RequestParam(defaultValue = "0") Integer page,
            // 每页数量，借书候选默认 5 条。
            @RequestParam(defaultValue = "5") Integer size
    ) {
        // 只查询启用状态读者。
        return userRepository.searchReaders(keyword == null ? null : keyword.trim(), "enabled", PageRequest.of(Math.max(0, page), libraryProperties.normalizePageSize(size)))
                .map(reader -> {
                    // 组装读者候选行。
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", reader.getId());
                    item.put("cardNumber", reader.getUsername());
                    item.put("realName", reader.getRealName());
                    item.put("phone", reader.getPhone());
                    item.put("status", reader.getStatus());
                    // 当前未还数量，用于判断是否还能借书。
                    item.put("currentBorrowCount", borrowRecordRepository.countByUserIdAndStatus(reader.getId(), "borrowed"));
                    return item;
                });
    }

    // 借书弹窗的图书候选，只返回启用状态图书，库存校验在提交借书时再次执行。
    @GetMapping("/book-options")
    public Page<Map<String, Object>> bookOptions(
            // ISBN、书名、作者、书架关键字。
            @RequestParam(required = false) String keyword,
            // 当前页。
            @RequestParam(defaultValue = "0") Integer page,
            // 每页数量，图书候选默认 5 条。
            @RequestParam(defaultValue = "5") Integer size
    ) {
        // 只查询启用状态图书。
        return bookRepository.search(keyword == null ? null : keyword.trim(), null, null, "enabled", PageRequest.of(Math.max(0, page), libraryProperties.normalizePageSize(size)))
                .map(book -> {
                    // 组装图书候选行。
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", book.getId());
                    item.put("isbn", book.getIsbn());
                    item.put("title", book.getTitle());
                    item.put("author", book.getAuthor());
                    item.put("category", book.getCategory());
                    item.put("status", book.getStatus());
                    item.put("availableCount", book.getAvailableCount());
                    item.put("totalCount", book.getTotalCount());
                    return item;
                });
    }

    // 还书办理页只查询当前未归还记录，历史已归还记录统一在借阅记录页查看。
    @GetMapping("/return-options")
    public Page<Map<String, Object>> returnOptions(
            // 读者、图书、单册编号关键字。
            @RequestParam(required = false) String keyword,
            // 当前页。
            @RequestParam(defaultValue = "0") Integer page,
            // 每页数量。
            @RequestParam(defaultValue = "10") Integer size
    ) {
        // 只查当前未归还记录，并在数据库层分页。
        return borrowRecordRepository.searchReturnOptions(
                        normalize(keyword),
                        PageRequest.of(Math.max(0, page), libraryProperties.normalizePageSize(size))
                )
                .map(borrowRecordViewService::toView);
    }

    // 管理端延期申请页展示全部已提交申请，待处理和已处理记录都保留可查。
    @GetMapping("/extension-requests")
    public Object extensionRequests(
            // 读者、图书、单册编号关键字。
            @RequestParam(required = false) String keyword,
            // 续借申请状态。
            @RequestParam(required = false) String extensionStatus,
            // 页码；为空时返回数组。
            @RequestParam(required = false) Integer page,
            // 每页数量。
            @RequestParam(defaultValue = "10") Integer size
    ) {
        if (page != null) {
            return borrowRecordRepository.searchExtensionRequests(
                            normalize(keyword),
                            normalize(extensionStatus),
                            PageRequest.of(Math.max(0, page), libraryProperties.normalizePageSize(size))
                    )
                    .map(borrowRecordViewService::toView);
        }

        // 查询所有提交过续借申请的借阅记录。
        List<Map<String, Object>> records = borrowRecordRepository.findByExtensionRequestedAtIsNotNullOrderByExtensionRequestedAtDesc()
                .stream()
                .map(borrowRecordViewService::toView)
                // 按申请状态过滤。
                .filter(record -> extensionStatus == null || extensionStatus.isBlank() || Objects.equals(record.get("extensionStatus"), extensionStatus))
                // 按关键字过滤。
                .filter(record -> borrowRecordViewService.matchesKeyword(record, keyword))
                // 待处理排在前面，再按申请时间倒序。
                .sorted(Comparator
                        .comparing((Map<String, Object> item) -> !"pending".equals(item.get("extensionStatus")))
                        .thenComparing(item -> (LocalDateTime) item.get("extensionRequestedAt"), Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(java.util.stream.Collectors.toList());

        // 不传 page 时返回全部。
        if (page == null) {
            return records;
        }
        // 返回分页结果。
        return toPage(records, page, size);
    }

    // 单本借书接口，兼容旧调用。
    @PostMapping
    public BorrowRecord borrow(
            // 读者 id。
            @RequestParam Long userId,
            // 图书 id。
            @RequestParam Long bookId,
            // 可选应还日期。
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDate
    ) {
        // 交给借阅服务处理库存和记录。
        return borrowService.borrowBook(userId, bookId, dueDate);
    }

    // 管理端批量借书入口，按读者选择的借阅天数生成同一批次记录。
    @PostMapping("/batch")
    public List<Map<String, Object>> borrowBatch(@RequestBody BatchBorrowRequest request) {
        // 批量借书后统一转成前端展示行。
        return borrowService.borrowBooksForDays(request.userId(), request.bookIds(), request.borrowDays())
                .stream()
                .map(borrowRecordViewService::toView)
                .collect(java.util.stream.Collectors.toList());
    }

    // 单条还书接口。
    @PostMapping("/return/{recordId}")
    public BorrowRecord returnBook(@PathVariable Long recordId) {
        // 交给借阅服务处理归还、库存和罚款。
        return borrowService.returnBook(recordId);
    }

    // 管理端批量还书入口，支持同一借阅批次中的部分图书先归还。
    @PostMapping("/return")
    public List<Map<String, Object>> returnBatch(@RequestBody BatchReturnRequest request) {
        // 批量归还后统一转成前端展示行。
        return borrowService.returnBooks(request.recordIds())
                .stream()
                .map(borrowRecordViewService::toView)
                .collect(java.util.stream.Collectors.toList());
    }

    // 管理员确认罚款已缴纳。
    @PostMapping("/records/{id}/fine/paid")
    @Transactional
    public Map<String, Object> markFinePaid(@PathVariable Long id) {
        return updateFineStatus(id, "paid", "管理员已确认逾期罚款缴纳");
    }

    // 免除逾期罚款。
    @PostMapping("/records/{id}/fine/waived")
    @Transactional
    public Map<String, Object> waiveFine(@PathVariable Long id) {
        // waived 表示罚款已免除。
        return updateFineStatus(id, "waived", "已免除逾期罚款");
    }

    // 冻结产生逾期记录的读者账号。
    @PostMapping("/records/{id}/freeze-reader")
    @Transactional
    public Map<String, Object> freezeReader(@PathVariable Long id) {
        // 查出产生逾期的借阅记录。
        BorrowRecord record = findRecord(id);
        // 找到该记录关联的读者。
        User reader = userRepository.findById(record.getUserId()).orElseThrow(() -> new RuntimeException("读者不存在"));
        // 只能冻结读者，不能冻结管理员。
        if (!"reader".equals(reader.getRole())) {
            throw new RuntimeException("只能冻结读者账号");
        }
        // disabled 表示冻结或停用。
        reader.setStatus("disabled");
        // 保存读者状态。
        userRepository.save(reader);
        // 返回借阅记录展示数据，前端可刷新状态。
        return borrowRecordViewService.toView(record);
    }

    // 同意续借时只处理 pending 申请，避免重复审批或审批已归还记录。
    @PostMapping("/records/{id}/extension/approve")
    @Transactional
    public Map<String, Object> approveExtension(@PathVariable Long id) {
        // 查出待审批记录。
        BorrowRecord record = findRecord(id);
        // 只有 pending 状态才能同意。
        if (!"pending".equals(record.getExtensionStatus())) {
            throw new RuntimeException("该记录没有待审核续书申请");
        }
        // 已归还记录不能再改应还日期。
        if (!"borrowed".equals(record.getStatus())) {
            throw new RuntimeException("已归还记录不能同意续书");
        }
        // 读取读者申请的新应还日期。
        LocalDate requestedDueDate = record.getExtensionRequestedDueDate();
        // 新应还日期必须晚于当前应还日期。
        if (requestedDueDate == null || !requestedDueDate.isAfter(record.getDueDate())) {
            throw new RuntimeException("续书申请日期无效");
        }
        // 每次最多续借 90 天。
        LocalDate maxDueDate = record.getDueDate().plusDays(BorrowService.MAX_RENEW_DAYS);
        if (requestedDueDate.isAfter(maxDueDate)) {
            throw new RuntimeException("每次续书不能超过 3 个月");
        }

        // 更新应还日期为申请日期。
        record.setDueDate(requestedDueDate);
        // 标记申请已同意。
        record.setExtensionStatus("approved");
        // 记录处理时间。
        record.setExtensionHandledAt(LocalDateTime.now());
        // 保存审批结果。
        borrowRecordRepository.save(record);
        // 返回最新展示数据。
        return borrowRecordViewService.toView(record);
    }

    // 列表接口统一使用分页结果，保证前端每个数据表格都能显示分页器。
    private Page<Map<String, Object>> toPage(List<Map<String, Object>> records, int page, int size) {
        int pageSize = libraryProperties.normalizePageSize(size);
        int safePage = Math.max(0, page);
        // 计算当前页开始和结束下标。
        int from = Math.min(safePage * pageSize, records.size());
        int to = Math.min(from + pageSize, records.size());
        // 用 PageImpl 包装当前页数据和总数。
        return new PageImpl<>(records.subList(from, to), PageRequest.of(safePage, pageSize), records.size());
    }

    // 把借阅记录转换为预警行。
    private Map<String, Object> toWarningView(BorrowRecord record, LocalDate today) {
        // 先复用借阅记录展示字段。
        Map<String, Object> item = borrowRecordViewService.toView(record);
        // 计算距离应还日期的天数，负数表示已逾期。
        long daysUntilDue = ChronoUnit.DAYS.between(today, record.getDueDate());
        // 写入预警辅助字段。
        item.put("daysUntilDue", daysUntilDue);
        item.put("warningLevel", warningLevel(daysUntilDue));
        item.put("warningText", warningText(daysUntilDue));
        item.put("warningSort", warningSort(daysUntilDue));
        return item;
    }

    // 预警等级用于前端标签颜色。
    private String warningLevel(long daysUntilDue) {
        if (daysUntilDue < 0) {
            return "danger";
        }
        if (daysUntilDue == 0) {
            return "warning";
        }
        return "info";
    }

    // 预警文案。
    private String warningText(long daysUntilDue) {
        if (daysUntilDue < 0) {
            return "已逾期 " + Math.abs(daysUntilDue) + " 天";
        }
        if (daysUntilDue == 0) {
            return "今天到期";
        }
        return daysUntilDue + " 天后到期";
    }

    // 预警排序：逾期优先，其次今天到期，再其次即将到期。
    private int warningSort(long daysUntilDue) {
        if (daysUntilDue < 0) {
            return 0;
        }
        if (daysUntilDue == 0) {
            return 1;
        }
        return 2;
    }

    // 预警天数限制在 0 到 365 天，支持查看全年未还风险。
    private int normalizeWarningDays(Integer days) {
        if (days == null || days < 0) {
            return DEFAULT_WARNING_DAYS;
        }
        return Math.min(days, 365);
    }

    // 查询参数统一去掉前后空格。
    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    // 缴纳和免罚共用同一套状态更新逻辑，金额由逾期天数实时计算或读取已存值。
    private Map<String, Object> updateFineStatus(Long id, String fineStatus, String fineNote) {
        // 查出借阅记录。
        BorrowRecord record = findRecordForUpdate(id);
        // 计算应处理罚款金额。
        BigDecimal amount = payableFineAmount(record);
        if (!"returned".equals(record.getStatus())) {
            throw new RuntimeException("请先办理还书，归还后才能结清逾期罚款");
        }
        if ("paid".equals(fineStatus)) {
            assertFineCanBePaid(record);
        } else {
            assertFineCanBeWaived(record);
        }
        applyFineStatus(record, amount, fineStatus, fineNote);
        // 保存处理结果。
        borrowRecordRepository.save(record);
        resolveReaderStatusAfterFine(record);
        // 返回最新展示数据。
        Map<String, Object> result = borrowRecordViewService.toView(record);
        result.put("paidAmount", amount);
        return result;
    }

    private BigDecimal payableFineAmount(BorrowRecord record) {
        BigDecimal amount = borrowRecordViewService.fineAmount(record);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("该借阅记录未产生逾期罚款");
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private void assertFineCanBePaid(BorrowRecord record) {
        String status = borrowRecordViewService.fineStatus(record);
        if ("paid".equals(status)) {
            throw new RuntimeException("该罚款已经缴纳，不能重复处理");
        }
        if ("waived".equals(status)) {
            throw new RuntimeException("该罚款已经免除，不能重复处理");
        }
    }

    private void assertFineCanBeWaived(BorrowRecord record) {
        String status = borrowRecordViewService.fineStatus(record);
        if ("paid".equals(status)) {
            throw new RuntimeException("该罚款已经缴纳，不能重复处理");
        }
        if ("waived".equals(status)) {
            throw new RuntimeException("该罚款已经免除，不能重复处理");
        }
    }

    private void applyFineStatus(BorrowRecord record, BigDecimal amount, String fineStatus, String fineNote) {
        record.setFineAmount(amount);
        record.setFineStatus(fineStatus);
        record.setFineHandledAt(LocalDateTime.now());
        record.setFineNote(fineNote);
    }

    private void ensureFineDebtRecordedAndReaderFrozen(BorrowRecord record) {
        BigDecimal amount = borrowRecordViewService.fineAmount(record);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        String fineStatus = borrowRecordViewService.fineStatus(record);
        if (!"unpaid".equals(fineStatus)) {
            return;
        }
        boolean changed = false;
        if ("returned".equals(record.getStatus()) && record.getFineAmount() == null) {
            record.setFineAmount(amount.setScale(2, RoundingMode.HALF_UP));
            changed = true;
        }
        if (record.getFineStatus() == null || record.getFineStatus().isBlank()) {
            record.setFineStatus("unpaid");
            changed = true;
        }
        if (record.getFineNote() == null || record.getFineNote().isBlank()) {
            record.setFineNote("逾期罚款待缴纳，读者账号已自动冻结");
            changed = true;
        }
        if (changed) {
            borrowRecordRepository.save(record);
        }
        userRepository.findById(record.getUserId()).ifPresent(reader -> {
            if ("reader".equals(reader.getRole()) && !"disabled".equals(reader.getStatus())) {
                reader.setStatus("disabled");
                userRepository.save(reader);
            }
        });
    }

    private void resolveReaderStatusAfterFine(BorrowRecord record) {
        User reader = userRepository.findByIdForUpdate(record.getUserId())
                .orElseThrow(() -> new RuntimeException("读者不存在"));
        if (!"reader".equals(reader.getRole())) {
            return;
        }
        if (!hasUnresolvedFineOrOverdue(reader.getId())) {
            reader.setStatus("enabled");
            userRepository.save(reader);
        }
    }

    private boolean hasUnresolvedFineOrOverdue(Long userId) {
        LocalDate today = LocalDate.now();
        return borrowRecordRepository.findByUserIdOrderByIdDesc(userId).stream()
                .anyMatch(record -> {
                    if ("borrowed".equals(record.getStatus())
                            && record.getDueDate() != null
                            && record.getDueDate().isBefore(today)) {
                        return true;
                    }
                    if (borrowRecordViewService.fineAmount(record).compareTo(BigDecimal.ZERO) <= 0) {
                        return false;
                    }
                    String status = borrowRecordViewService.fineStatus(record);
                    return !"paid".equals(status) && !"waived".equals(status);
                });
    }

    // 按 id 查询借阅记录。
    private BorrowRecord findRecord(Long id) {
        return borrowRecordRepository.findById(id).orElseThrow(() -> new RuntimeException("借阅记录不存在"));
    }

    private BorrowRecord findRecordForUpdate(Long id) {
        return borrowRecordRepository.findByIdForUpdate(id).orElseThrow(() -> new RuntimeException("借阅记录不存在"));
    }

    // 批量借书请求体。
    public static class BatchBorrowRequest {
        private Long userId;
        private List<Long> bookIds;
        private Integer borrowDays;

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public List<Long> getBookIds() {
            return bookIds;
        }

        public void setBookIds(List<Long> bookIds) {
            this.bookIds = bookIds;
        }

        public Integer getBorrowDays() {
            return borrowDays;
        }

        public void setBorrowDays(Integer borrowDays) {
            this.borrowDays = borrowDays;
        }

        public Long userId() {
            return userId;
        }

        public List<Long> bookIds() {
            return bookIds;
        }

        public Integer borrowDays() {
            return borrowDays;
        }
    }

    // 批量还书请求体。
    public static class BatchReturnRequest {
        private List<Long> recordIds;

        public List<Long> getRecordIds() {
            return recordIds;
        }

        public void setRecordIds(List<Long> recordIds) {
            this.recordIds = recordIds;
        }

        public List<Long> recordIds() {
            return recordIds;
        }
    }
}
