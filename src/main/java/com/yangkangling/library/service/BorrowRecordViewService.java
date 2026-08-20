package com.yangkangling.library.service;

import com.yangkangling.library.entity.Book;
import com.yangkangling.library.entity.BorrowRecord;
import com.yangkangling.library.entity.User;
import com.yangkangling.library.repository.BookRepository;
import com.yangkangling.library.repository.BookCopyRepository;
import com.yangkangling.library.repository.StorageLocationRepository;
import com.yangkangling.library.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

// 组装借阅记录展示数据。
@Service
public class BorrowRecordViewService {
    // 每逾期一天的罚款金额。
    public static final BigDecimal DAILY_FINE_AMOUNT = new BigDecimal("0.50");

    // 用户仓库，用于借阅快照缺失时回查读者信息。
    private final UserRepository userRepository;
    // 图书仓库，用于借阅快照缺失时回查图书信息。
    private final BookRepository bookRepository;
    // 单册仓库，用于补齐或格式化单册编号。
    private final BookCopyRepository bookCopyRepository;
    // 书架仓库，用于补齐书架位置。
    private final StorageLocationRepository storageLocationRepository;

    // 构造方法注入视图转换需要的仓库。
    public BorrowRecordViewService(
            UserRepository userRepository,
            BookRepository bookRepository,
            BookCopyRepository bookCopyRepository,
            StorageLocationRepository storageLocationRepository
    ) {
        this.userRepository = userRepository;
        this.bookRepository = bookRepository;
        this.bookCopyRepository = bookCopyRepository;
        this.storageLocationRepository = storageLocationRepository;
    }

    // 列表展示优先使用借阅快照，缺失时回查当前资料。
    public Map<String, Object> toView(BorrowRecord record) {
        // 当前读者资料，可能为空。
        User currentUser = userRepository.findById(record.getUserId()).orElse(null);
        // 当前图书资料，可能为空。
        Book currentBook = bookRepository.findById(record.getBookId()).orElse(null);
        BigDecimal currentFineAmount = fineAmount(record);
        String currentFineStatus = fineStatus(record);

        // 使用有序 Map 保持前端字段顺序。
        Map<String, Object> item = new LinkedHashMap<>();
        // 借阅记录 id。
        item.put("id", record.getId());
        // 读者 id。
        item.put("userId", record.getUserId());
        // 图书 id。
        item.put("bookId", record.getBookId());
        // 借阅证号，优先用快照。
        item.put("readerCard", firstText(record.getReaderCard(), currentUser == null ? "" : currentUser.getUsername()));
        // 读者姓名，优先用快照。
        item.put("readerName", firstText(record.getReaderName(), currentUser == null ? "" : currentUser.getRealName()));
        // 读者手机号，优先用快照。
        item.put("readerPhone", firstText(record.getReaderPhone(), currentUser == null ? "" : currentUser.getPhone()));
        // 当前读者状态。
        item.put("readerStatus", currentUser == null ? "" : currentUser.getStatus());
        // ISBN，优先用借阅时快照。
        item.put("isbn", firstText(record.getBookIsbn(), currentBook == null ? "" : currentBook.getIsbn()));
        // 书名，优先用借阅时快照。
        item.put("bookTitle", firstText(record.getBookTitle(), currentBook == null ? "" : currentBook.getTitle()));
        // 作者，优先用借阅时快照。
        item.put("bookAuthor", firstText(record.getBookAuthor(), currentBook == null ? "" : currentBook.getAuthor()));
        // 单册 id。
        item.put("bookCopyId", record.getBookCopyId());
        // 书架记录 id。
        item.put("storageLocationId", record.getStorageLocationId());
        // 原始单册编号。
        String rawCopyCode = firstText(record.getCopyCode(), fallbackCopyCode(currentBook));
        // 页面展示单册编号。
        item.put("copyCode", displayCopyCode(rawCopyCode));
        // 保留原始编号，必要时调试或详情使用。
        item.put("rawCopyCode", rawCopyCode);
        // 借阅时书架快照。
        item.put("shelfLocationSnapshot", firstText(record.getShelfLocationSnapshot(), fallbackStorageLocation(currentBook)));
        // 页面展示书架位置。
        item.put("copyShelfLocation", firstText(record.getShelfLocationSnapshot(), firstText(record.getCopyShelfLocation(), currentBook == null ? "" : currentBook.getShelfLocation())));
        // 借阅批次号。
        item.put("batchNo", record.getBatchNo());
        // 借阅日期。
        item.put("borrowDate", record.getBorrowDate());
        // 应还日期。
        item.put("dueDate", record.getDueDate());
        // 实际归还日期。
        item.put("returnDate", record.getReturnDate());
        // 数据库原始状态。
        item.put("rawStatus", record.getStatus());
        // 页面展示状态，未还且过期会转为 overdue。
        item.put("status", recordStatus(record));
        // 逾期天数。
        item.put("overdueDays", overdueDays(record));
        // 应缴或已记录罚款金额。
        item.put("fineAmount", currentFineAmount);
        // 罚款处理状态。
        item.put("fineStatus", currentFineStatus);
        // 罚款处理时间。
        item.put("fineHandledAt", record.getFineHandledAt());
        // 罚款处理备注。
        item.put("fineNote", record.getFineNote());
        // 每日罚款标准。
        item.put("fineDailyRate", DAILY_FINE_AMOUNT);
        // 续借申请状态。
        item.put("extensionStatus", extensionStatus(record));
        // 申请续借天数。
        item.put("extensionRequestedDays", record.getExtensionRequestedDays());
        // 申请后的应还日期。
        item.put("extensionRequestedDueDate", record.getExtensionRequestedDueDate());
        // 申请时间。
        item.put("extensionRequestedAt", record.getExtensionRequestedAt());
        // 管理员处理时间。
        item.put("extensionHandledAt", record.getExtensionHandledAt());
        // 当前记录最多可续借到的日期。
        item.put("maxDueDate", maxDueDate(record));
        return item;
    }

    // 查询详情时在列表字段基础上补充更多信息。
    public Map<String, Object> toDetail(BorrowRecord record) {
        // 先复用列表转换。
        Map<String, Object> detail = toView(record);
        // 创建时间。
        detail.put("createdAt", record.getCreatedAt());
        // 是否保存过读者、图书或书架快照。
        detail.put("snapshotSaved", hasText(record.getReaderCard()) || hasText(record.getBookTitle()) || hasText(record.getShelfLocationSnapshot()));
        return detail;
    }

    // 计算页面展示的借阅状态。
    public String recordStatus(BorrowRecord record) {
        // 已归还记录直接显示 returned。
        if ("returned".equals(record.getStatus())) {
            return "returned";
        }
        // 未归还且应还日期早于今天，显示 overdue。
        if (record.getDueDate() != null && record.getDueDate().isBefore(LocalDate.now())) {
            return "overdue";
        }
        // 其他情况返回数据库原始状态。
        return record.getStatus();
    }

    // 计算逾期天数。
    public long overdueDays(BorrowRecord record) {
        // 没有应还日期无法计算逾期。
        if (record.getDueDate() == null) {
            return 0;
        }
        // 已归还用归还日期计算，未归还用今天计算。
        LocalDate endDate = record.getReturnDate() == null ? LocalDate.now() : record.getReturnDate();
        // 应还日期不早于结束日期，说明没有逾期。
        if (!record.getDueDate().isBefore(endDate)) {
            return 0;
        }
        // 计算相差天数。
        return ChronoUnit.DAYS.between(record.getDueDate(), endDate);
    }

    // 未落库罚款时按逾期天数实时计算。
    public BigDecimal fineAmount(BorrowRecord record) {
        // 已经保存过罚款金额时直接使用保存值。
        if (record.getFineAmount() != null) {
            return normalizeMoney(record.getFineAmount());
        }
        // 没保存时按逾期天数实时计算。
        return DAILY_FINE_AMOUNT.multiply(BigDecimal.valueOf(overdueDays(record))).setScale(2, RoundingMode.HALF_UP);
    }

    // 计算罚款状态。
    public String fineStatus(BorrowRecord record) {
        // 没有罚款金额时显示 none。
        if (fineAmount(record).compareTo(BigDecimal.ZERO) <= 0) {
            return "none";
        }
        // 有罚款时优先使用数据库状态，没有则默认为 unpaid。
        return hasText(record.getFineStatus()) ? record.getFineStatus() : "unpaid";
    }

    // 判断借阅记录展示行是否匹配关键字。
    public boolean matchesKeyword(Map<String, Object> record, String keyword) {
        // 关键字为空时直接匹配。
        if (keyword == null || keyword.trim().isEmpty()) {
            return true;
        }
        // 统一小写后在所有字段里搜索。
        String value = keyword.trim().toLowerCase();
        return record.values().stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .map(String::toLowerCase)
                .anyMatch(text -> text.contains(value));
    }

    // 页面展示不暴露最后的内部副本流水：套书显示 ISBN-套号-册号，普通书显示 ISBN-册序号。
    public String displayCopyCode(String copyCode) {
        // 空编号直接返回。
        if (!hasText(copyCode)) {
            return copyCode;
        }
        // 去掉编号前后空格。
        String value = copyCode.trim();
        // 单册编号按 - 切分。
        String[] parts = value.split("-");
        // 识别 ISBN-套号-册号 或 ISBN-套号-册号-内部流水。
        if ((parts.length == 3 || parts.length == 4)
                && parts[0].matches("\\d{12,13}")
                && parts[1].matches("\\d{2}")
                && parts[2].matches("\\d{3}")) {
            // 普通单册不展示 00-000，只展示 ISBN-册序号。
            if ("00".equals(parts[1]) && "000".equals(parts[2])) {
                String copyNumber = parts.length == 4 ? parts[3] : "001";
                return parts[0] + "-" + copyNumber;
            }
            // 套书展示 ISBN-套号-册号。
            return parts[0] + "-" + parts[1] + "-" + parts[2];
        }
        // 兼容旧格式 ISBN-流水号。
        if (parts.length == 2 && parts[0].matches("\\d{12,13}") && parts[1].matches("\\d{3}")) {
            return parts[0] + "-" + parts[1];
        }
        // 不认识的格式原样返回。
        return value;
    }

    // 续借状态为空时显示 none。
    private String extensionStatus(BorrowRecord record) {
        return hasText(record.getExtensionStatus()) ? record.getExtensionStatus() : "none";
    }

    // 计算最多可续借到的日期。
    private LocalDate maxDueDate(BorrowRecord record) {
        return record.getDueDate() == null ? null : record.getDueDate().plusDays(BorrowService.MAX_RENEW_DAYS);
    }

    // 优先返回快照值，没有快照值时返回当前值。
    private String firstText(String snapshotValue, String currentValue) {
        return hasText(snapshotValue) ? snapshotValue : currentValue;
    }

    private BigDecimal firstMoney(BigDecimal first, BigDecimal second) {
        return first == null ? second : first;
    }

    // 借阅记录缺少单册编号时，从图书现有单册中找一个兜底展示。
    private String fallbackCopyCode(Book currentBook) {
        // 图书不存在时无法兜底。
        if (currentBook == null || currentBook.getId() == null) {
            return "";
        }
        // 使用该图书第一条单册编号。
        return bookCopyRepository.findByBookIdOrderByCopyCodeAsc(currentBook.getId()).stream()
                .findFirst()
                .map(copy -> copy.getCopyCode())
                .orElse("");
    }

    // 借阅记录缺少书架快照时，从当前书架记录中兜底。
    private String fallbackStorageLocation(Book currentBook) {
        // 图书不存在时无法兜底。
        if (currentBook == null || currentBook.getId() == null) {
            return "";
        }
        // 优先使用书架库存表记录，没有则使用图书主表书架位置。
        return storageLocationRepository.findFirstByBookIdOrderByIdAsc(currentBook.getId())
                .map(storageLocation -> firstText(storageLocation.getShelfLocation(), currentBook.getShelfLocation()))
                .orElse(currentBook.getShelfLocation());
    }

    // 判断文本是否有实际内容。
    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    // 金额统一保留两位小数。
    private BigDecimal normalizeMoney(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
