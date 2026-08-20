package com.example.demo.controller;

import com.example.demo.entity.BorrowRecord;
import com.example.demo.entity.User;
import com.example.demo.config.LibraryProperties;
import com.example.demo.repository.BookRepository;
import com.example.demo.repository.BorrowRecordRepository;
import com.example.demo.repository.CategoryRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.BorrowRecordViewService;
import com.example.demo.service.BorrowService;
import javax.servlet.http.HttpSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.time.temporal.ChronoUnit;

// 读者自助端接口。
@RestController
@RequestMapping("/self")
public class SelfServiceController {
    // 默认查看 90 天内的未还风险，避免预警中心长期空表。
    private static final int DEFAULT_WARNING_DAYS = 90;

    // 用户仓库，用于读取当前登录读者。
    private final UserRepository userRepository;
    // 图书仓库，用于读者端查询可借图书。
    private final BookRepository bookRepository;
    // 分类仓库，用于读者端图书分类下拉。
    private final CategoryRepository categoryRepository;
    // 借阅记录仓库，用于查询读者本人记录。
    private final BorrowRecordRepository borrowRecordRepository;
    // 借阅记录视图服务，用于统一前端展示字段。
    private final BorrowRecordViewService borrowRecordViewService;
    // 借还书服务，用于自助借书、还书和校验。
    private final BorrowService borrowService;
    // 系统容量和业务规则配置。
    private final LibraryProperties libraryProperties;

    // 构造方法注入读者自助端需要的仓库和服务。
    public SelfServiceController(
            UserRepository userRepository,
            BookRepository bookRepository,
            CategoryRepository categoryRepository,
            BorrowRecordRepository borrowRecordRepository,
            BorrowRecordViewService borrowRecordViewService,
            BorrowService borrowService,
            LibraryProperties libraryProperties
    ) {
        this.userRepository = userRepository;
        this.bookRepository = bookRepository;
        this.categoryRepository = categoryRepository;
        this.borrowRecordRepository = borrowRecordRepository;
        this.borrowRecordViewService = borrowRecordViewService;
        this.borrowService = borrowService;
        this.libraryProperties = libraryProperties;
    }

    // 查询当前登录读者的个人信息和借阅上限。
    @GetMapping("/me")
    public Map<String, Object> me(HttpSession session) {
        // 从 Session 中读取当前读者。
        User reader = currentReader(session);
        // 使用有序 Map 返回个人信息。
        Map<String, Object> item = new LinkedHashMap<>();
        // 读者 id。
        item.put("id", reader.getId());
        // 借阅证号。
        item.put("username", reader.getUsername());
        // 姓名。
        item.put("realName", reader.getRealName());
        // 手机号。
        item.put("phone", reader.getPhone());
        // 账号状态，用于读者端展示冻结提示。
        item.put("status", reader.getStatus());
        // 当前未还数量。
        item.put("currentBorrowCount", borrowRecordRepository.countByUserIdAndStatus(reader.getId(), "borrowed"));
        // 最大同时借阅数量。
        item.put("maxBorrowCount", libraryProperties.getMaxActiveBorrowCount());
        return item;
    }

    // 查询读者端可选图书分类。
    @GetMapping("/categories")
    public List<Map<String, Object>> categories() {
        // 返回全部分类的 id 和名称。
        return categoryRepository.findAll().stream()
                .map(category -> {
                    // 组装分类下拉项。
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", category.getId());
                    item.put("name", category.getName());
                    return item;
                })
                .collect(java.util.stream.Collectors.toList());
    }

    // 读者端查询可借图书。
    @GetMapping("/books")
    public Page<Map<String, Object>> books(
            // ISBN、书名、作者、书架关键字。
            @RequestParam(required = false) String keyword,
            // 分类 id。
            @RequestParam(required = false) Long categoryId,
            // 当前页。
            @RequestParam(defaultValue = "0") Integer page,
            // 每页数量。
            @RequestParam(defaultValue = "10") Integer size
    ) {
        // 只展示启用状态图书。
        return bookRepository.search(keyword == null ? null : keyword.trim(), categoryId, null, "enabled", PageRequest.of(Math.max(0, page), libraryProperties.normalizePageSize(size)))
                .map(book -> {
                    // 组装读者端图书行。
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", book.getId());
                    item.put("isbn", book.getIsbn());
                    item.put("title", book.getTitle());
                    item.put("author", book.getAuthor());
                    item.put("category", book.getCategory());
                    item.put("shelfLocation", book.getShelfLocation());
                    item.put("availableCount", book.getAvailableCount());
                    item.put("totalCount", book.getTotalCount());
                    // 前端按钮是否可加入借阅清单。
                    item.put("borrowable", book.getAvailableCount() != null && book.getAvailableCount() > 0);
                    return item;
                });
    }

    // 读者端记录查询始终基于当前会话读者，避免越权查看他人记录。
    @GetMapping("/records")
    public Object records(
            // 借阅状态筛选。
            @RequestParam(required = false) String status,
            // 页码；为空时返回全部数组。
            @RequestParam(required = false) Integer page,
            // 每页数量。
            @RequestParam(required = false) Integer size,
            // 当前会话。
            HttpSession session
    ) {
        // 只查询当前登录读者自己的记录。
        User reader = currentReader(session);
        // 分页请求走数据库分页，避免读者历史记录很多时一次性加载。
        if (page != null || size != null) {
            int pageIndex = normalizePage(page);
            int pageSize = normalizePageSize(size);
            Page<BorrowRecord> result = hasText(status)
                    ? borrowRecordRepository.findByUserIdAndStatusOrderByIdDesc(reader.getId(), status.trim(), PageRequest.of(pageIndex, pageSize))
                    : borrowRecordRepository.findByUserIdOrderByIdDesc(reader.getId(), PageRequest.of(pageIndex, pageSize));
            return result.map(borrowRecordViewService::toView);
        }
        // 查询本人全部记录并转成展示行。
        List<Map<String, Object>> records = borrowRecordRepository.findByUserIdOrderByIdDesc(reader.getId())
                .stream()
                .map(borrowRecordViewService::toView)
                // 按状态过滤，兼容展示状态和原始状态。
                .filter(record -> status == null || status.isBlank() || Objects.equals(record.get("status"), status) || Objects.equals(record.get("rawStatus"), status))
                // 最新记录放前面。
                .sorted(Comparator.comparing(item -> (Long) item.get("id"), Comparator.reverseOrder()))
                .collect(java.util.stream.Collectors.toList());
        // 兼容旧接口：不分页时返回数组。
        return records;
    }

    // 查询当前读者的还书预警，包括逾期、今天到期和即将到期。
    @GetMapping("/warnings")
    public Page<Map<String, Object>> warnings(
            // 图书、ISBN、单册编号或批次号关键字。
            @RequestParam(required = false) String keyword,
            // 提前预警天数，默认 90 天。
            @RequestParam(defaultValue = "90") Integer days,
            // 当前页。
            @RequestParam(defaultValue = "0") Integer page,
            // 每页数量。
            @RequestParam(defaultValue = "10") Integer size,
            // 当前会话。
            HttpSession session
    ) {
        // 只查询当前读者自己的记录。
        User reader = currentReader(session);
        // 今天作为预警计算基准。
        LocalDate today = LocalDate.now();
        // 预警截止日期。
        LocalDate warningEnd = today.plusDays(normalizeWarningDays(days));
        // 规范化分页参数。
        int pageIndex = normalizePage(page);
        int pageSize = normalizePageSize(size);
        return borrowRecordRepository.searchReaderWarnings(
                        reader.getId(),
                        warningEnd,
                        normalize(keyword),
                        PageRequest.of(pageIndex, pageSize)
                )
                .map(record -> toWarningView(record, today));
    }

    // 读者自助借书复用 BorrowService 的库存、上限、逾期和重复借阅校验。
    @PostMapping("/borrow")
    public List<Map<String, Object>> borrow(
            @RequestBody BorrowRequest request,
            HttpSession session
    ) {
        // 当前登录读者。
        User reader = currentReader(session);
        // 冻结或停用的读者不能继续自助借书。
        ensureReaderEnabled(reader);
        // 请求体为空时给默认空对象，交给服务层抛出明确提示。
        BorrowRequest safeRequest = request == null ? new BorrowRequest(null, null, null) : request;
        // 根据默认模式或自定义模式确定借阅天数。
        Integer borrowDays = customBorrowDays(safeRequest);
        // 复用借书服务处理库存、上限、逾期和重复借阅校验。
        List<BorrowRecord> records = borrowService.borrowBooksForDays(reader.getId(), safeRequest.bookIds(), borrowDays);
        // 返回前端展示行。
        return records.stream().map(borrowRecordViewService::toView).collect(java.util.stream.Collectors.toList());
    }

    // 读者只能为本人未逾期、未归还且无待审批申请的记录提交续借申请。
    @PostMapping("/records/{id}/extension-request")
    @Transactional
    public Map<String, Object> requestExtension(
            @PathVariable Long id,
            @RequestBody ExtensionRequest request,
            HttpSession session
    ) {
        // 当前登录读者。
        User reader = currentReader(session);
        // 冻结或停用的读者不能继续提交续借申请。
        ensureReaderEnabled(reader);
        // 查出要续借的借阅记录。
        BorrowRecord record = borrowRecordRepository.findById(id).orElseThrow(() -> new RuntimeException("借阅记录不存在"));
        // 只能续借自己的记录。
        if (!Objects.equals(record.getUserId(), reader.getId())) {
            throw new RuntimeException("只能申请本人的借阅记录续书");
        }
        // 只有未归还记录可以续借。
        if (!"borrowed".equals(record.getStatus())) {
            throw new RuntimeException("只有未归还记录可以申请续书");
        }
        // 已有待审批申请时不能重复提交。
        if ("pending".equals(record.getExtensionStatus())) {
            throw new RuntimeException("该记录已有待审核续书申请");
        }
        // 已逾期记录不能申请续借。
        if (record.getDueDate() == null || record.getDueDate().isBefore(LocalDate.now())) {
            throw new RuntimeException("逾期记录不能申请续书");
        }

        // 校验续借天数。
        int days = normalizeExtensionDays(request == null ? null : request.days());
        // 申请后的新应还日期。
        LocalDate requestedDueDate = record.getDueDate().plusDays(days);
        // 本次续借最大允许日期。
        LocalDate maxDueDate = record.getDueDate().plusDays(BorrowService.MAX_RENEW_DAYS);
        // 超过 3 个月不允许提交。
        if (requestedDueDate.isAfter(maxDueDate)) {
            throw new RuntimeException("每次续书不能超过 3 个月");
        }

        // 标记为待管理员审批。
        record.setExtensionStatus("pending");
        // 保存申请天数。
        record.setExtensionRequestedDays(days);
        // 保存申请的新应还日期。
        record.setExtensionRequestedDueDate(requestedDueDate);
        // 保存申请时间。
        record.setExtensionRequestedAt(LocalDateTime.now());
        // 清空处理时间，表示还未处理。
        record.setExtensionHandledAt(null);
        // 保存申请。
        borrowRecordRepository.save(record);
        // 返回最新记录展示数据。
        return borrowRecordViewService.toView(record);
    }

    // 自助还书必须校验记录归属，防止读者归还或影响他人借阅记录。
    @PostMapping("/return")
    public List<Map<String, Object>> returnBooks(
            @RequestBody ReturnRequest request,
            HttpSession session
    ) {
        // 当前登录读者。
        User reader = currentReader(session);
        // 查询提交的记录集合。
        List<BorrowRecord> records = borrowRecordRepository.findAllById(request.recordIds());
        // 判断是否包含其他读者的记录。
        boolean hasOtherReaderRecord = records.stream().anyMatch(record -> !Objects.equals(record.getUserId(), reader.getId()));
        // 数量不一致说明有不存在记录；包含他人记录也不能继续。
        if (records.size() != request.recordIds().size() || hasOtherReaderRecord) {
            throw new RuntimeException("只能归还本人借阅记录");
        }
        // 复用还书服务处理状态、库存、书架和罚款。
        return borrowService.returnBooks(request.recordIds()).stream().map(borrowRecordViewService::toView).collect(java.util.stream.Collectors.toList());
    }

    // 读者端接口统一从 session 读取 readerId，不接受前端传入用户 ID。
    private User currentReader(HttpSession session) {
        // Session 中必须有 readerId。
        Object readerId = session.getAttribute("readerId");
        // 没有读者登录时提示重新登录。
        if (!(readerId instanceof Long)) {
            throw new RuntimeException("请先登录读者自助端");
        }
        Long id = (Long) readerId;
        // 根据 readerId 查询读者实体。
        return userRepository.findById(id).orElseThrow(() -> new RuntimeException("读者不存在"));
    }

    // 冻结或停用读者仍可查看记录和归还图书，但不能新借或续借。
    private void ensureReaderEnabled(User reader) {
        if ("disabled".equals(reader.getStatus())) {
            throw new RuntimeException("读者账号已冻结，请联系管理员处理后再办理借阅");
        }
    }

    // 校验续借天数范围。
    private int normalizeExtensionDays(Integer days) {
        // 续借至少 1 天。
        if (days == null || days < 1) {
            throw new RuntimeException("续书天数不能少于 1 天");
        }
        // 单次续借最多 90 天。
        if (days > BorrowService.MAX_RENEW_DAYS) {
            throw new RuntimeException("续书天数不能超过 3 个月");
        }
        return days;
    }

    // 规范化页码。
    private int normalizePage(Integer page) {
        return page == null || page < 0 ? 0 : page;
    }

    // 规范化每页数量，最大不超过 100。
    private int normalizePageSize(Integer size) {
        if (size == null || size < 1) {
            return 10;
        }
        return libraryProperties.normalizePageSize(size);
    }

    // 默认模式不传天数，custom 模式必须传入 1 到 90 天之间的借阅天数。
    private Integer customBorrowDays(BorrowRequest request) {
        // 自定义模式必须传借阅天数。
        if ("custom".equals(request.borrowMode())) {
            if (request.borrowDays() == null) {
                throw new RuntimeException("请选择借阅天数");
            }
            return request.borrowDays();
        }
        // 兼容旧调用：没有 borrowMode 但传了 borrowDays 时也按自定义天数处理。
        if (request.borrowMode() == null && request.borrowDays() != null) {
            return request.borrowDays();
        }
        // 默认模式返回 null，服务层会按 30 天处理。
        return null;
    }

    // 转成读者端预警展示行。
    private Map<String, Object> toWarningView(BorrowRecord record, LocalDate today) {
        // 复用借阅记录展示字段。
        Map<String, Object> item = borrowRecordViewService.toView(record);
        // 距离应还日期天数，负数表示已逾期。
        long daysUntilDue = ChronoUnit.DAYS.between(today, record.getDueDate());
        // 写入预警字段。
        item.put("daysUntilDue", daysUntilDue);
        item.put("warningLevel", warningLevel(daysUntilDue));
        item.put("warningText", warningText(daysUntilDue));
        item.put("warningSort", warningSort(daysUntilDue));
        return item;
    }

    // 预警等级。
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

    // 判断文本是否有实际内容。
    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    // 预警排序：逾期最前。
    private int warningSort(long daysUntilDue) {
        if (daysUntilDue < 0) {
            return 0;
        }
        if (daysUntilDue == 0) {
            return 1;
        }
        return 2;
    }

    // 预警天数限制在 0 到 365 天。
    private int normalizeWarningDays(Integer days) {
        if (days == null || days < 0) {
            return DEFAULT_WARNING_DAYS;
        }
        return Math.min(days, 365);
    }

    // 自助借书请求体。
    public static class BorrowRequest {
        private List<Long> bookIds;
        private Integer borrowDays;
        private String borrowMode;

        public BorrowRequest() {
        }

        public BorrowRequest(List<Long> bookIds, Integer borrowDays, String borrowMode) {
            this.bookIds = bookIds;
            this.borrowDays = borrowDays;
            this.borrowMode = borrowMode;
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

        public String getBorrowMode() {
            return borrowMode;
        }

        public void setBorrowMode(String borrowMode) {
            this.borrowMode = borrowMode;
        }

        public List<Long> bookIds() {
            return bookIds;
        }

        public Integer borrowDays() {
            return borrowDays;
        }

        public String borrowMode() {
            return borrowMode;
        }
    }

    // 续借申请请求体。
    public static class ExtensionRequest {
        private Integer days;

        public Integer getDays() {
            return days;
        }

        public void setDays(Integer days) {
            this.days = days;
        }

        public Integer days() {
            return days;
        }
    }

    // 自助还书请求体。
    public static class ReturnRequest {
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
