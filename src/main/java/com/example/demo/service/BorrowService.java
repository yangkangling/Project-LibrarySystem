package com.example.demo.service;

import com.example.demo.entity.Book;
import com.example.demo.entity.BookCopy;
import com.example.demo.entity.BorrowRecord;
import com.example.demo.entity.StorageLocation;
import com.example.demo.entity.User;
import com.example.demo.config.LibraryProperties;
import com.example.demo.repository.BookRepository;
import com.example.demo.repository.BorrowRecordRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// 借还书核心业务处理。
@Service
public class BorrowService {
    // 默认借阅 30 天；自选借阅和续借的单次上限均为 90 天。
    public static final int DEFAULT_BORROW_DAYS = 30;
    public static final int MAX_BORROW_DAYS = 90;
    public static final int MAX_RENEW_DAYS = 90;

    // 图书仓库，用于读取图书和扣减/恢复库存。
    private final BookRepository bookRepository;
    // 用户仓库，用于校验读者身份和状态。
    private final UserRepository userRepository;
    // 借阅记录仓库，用于创建、查询和更新借阅记录。
    private final BorrowRecordRepository borrowRecordRepository;
    // 单册服务，用于占用和释放具体单册。
    private final BookCopyService bookCopyService;
    // 书架服务，用于占用和释放书架库存。
    private final StorageLocationService storageLocationService;
    // 系统容量和业务规则配置。
    private final LibraryProperties libraryProperties;

    // 构造方法注入借还书需要的仓库和服务。
    public BorrowService(
            BookRepository bookRepository,
            UserRepository userRepository,
            BorrowRecordRepository borrowRecordRepository,
            BookCopyService bookCopyService,
            StorageLocationService storageLocationService,
            LibraryProperties libraryProperties
    ) {
        this.bookRepository = bookRepository;
        this.userRepository = userRepository;
        this.borrowRecordRepository = borrowRecordRepository;
        this.bookCopyService = bookCopyService;
        this.storageLocationService = storageLocationService;
        this.libraryProperties = libraryProperties;
    }

    // 单本借书使用默认 30 天期限。
    @Transactional
    public BorrowRecord borrowBook(Long userId, Long bookId) {
        return borrowBook(userId, bookId, dueDateFromBorrowDays(DEFAULT_BORROW_DAYS));
    }

    @Transactional
    public BorrowRecord borrowBook(Long userId, Long bookId, LocalDate dueDate) {
        // 复用批量借书逻辑，取返回列表第一条。
        return borrowBooks(userId, List.of(bookId), dueDate).get(0);
    }

    // 读者端可选择默认 30 天或自定义天数，最终统一转换成应还日期。
    public LocalDate dueDateFromBorrowDays(Integer borrowDays) {
        // 未传天数时使用默认 30 天。
        int days = borrowDays == null ? DEFAULT_BORROW_DAYS : borrowDays;
        // 借阅天数必须大于 0。
        if (days < 1) {
            throw new RuntimeException("借阅天数不能少于 1 天");
        }
        // 借阅天数不能超过 90 天。
        if (days > MAX_BORROW_DAYS) {
            throw new RuntimeException("借阅期限最长 3 个月");
        }
        // 用今天加借阅天数得到应还日期。
        return LocalDate.now().plusDays(days);
    }

    // 按借阅天数批量借书。
    @Transactional
    public List<BorrowRecord> borrowBooksForDays(Long userId, List<Long> bookIds, Integer borrowDays) {
        // 先把天数转换成应还日期，再走统一借书逻辑。
        return borrowBooks(userId, bookIds, dueDateFromBorrowDays(borrowDays));
    }

    // 批量借书时统一校验读者、库存和重复借阅。
    @Transactional
    public List<BorrowRecord> borrowBooks(Long userId, List<Long> bookIds, LocalDate dueDate) {
        // 校验读者存在、角色正确并且未停用。
        User user = validateReader(userId);
        // 图书 id 去重并保持选择顺序。
        List<Long> uniqueBookIds = normalizeBookIds(bookIds);

        // 规范化应还日期。
        dueDate = normalizeDueDate(dueDate);

        // 查询当前未还数量。
        long currentBorrowCount = borrowRecordRepository.countByUserIdAndStatus(userId, "borrowed");
        int maxActiveBorrowCount = libraryProperties.getMaxActiveBorrowCount();
        // 本次借书后不能超过配置的未还上限。
        if (currentBorrowCount + uniqueBookIds.size() > maxActiveBorrowCount) {
            throw new RuntimeException("同一读者最多同时借阅 " + maxActiveBorrowCount + " 册图书，本次最多还能借 " + Math.max(0, maxActiveBorrowCount - currentBorrowCount) + " 册");
        }
        // 有逾期未还时禁止新借阅。
        if (borrowRecordRepository.existsByUserIdAndStatusAndDueDateBefore(userId, "borrowed", LocalDate.now())) {
            throw new RuntimeException("该读者存在逾期未还图书，请先归还逾期图书后再办理新借阅");
        }

        // 收集并校验本次要借的图书。
        List<Book> books = new ArrayList<>();
        for (Long bookId : uniqueBookIds) {
            // 图书必须存在。
            Book book = bookRepository.findById(bookId).orElseThrow(() -> new RuntimeException("图书不存在，ID：" + bookId));
            // 校验图书可借状态。
            validateBorrowableBook(userId, book);
            books.add(book);
        }

        // 同一批借书使用同一个批次号。
        String batchNo = "BR" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        // 保存创建出的借阅记录。
        List<BorrowRecord> records = new ArrayList<>();
        for (Book book : books) {
            // 条件更新保证库存原子扣减，避免多人同时借最后一本时出现负库存。
            int updatedRows = bookRepository.decreaseAvailableCountWhenAvailable(book.getId());
            // 扣减失败说明库存已经不足。
            if (updatedRows == 0) {
                throw new RuntimeException("《" + book.getTitle() + "》库存不足，请刷新后重试");
            }
            // 占用一个书架库存。
            StorageLocation storageLocation = storageLocationService.borrowAvailableStorage(book);
            // 占用一本具体单册，优先取刚扣减的书架位置。
            BookCopy copy = bookCopyService.borrowAvailableCopy(book, user.getId(), storageLocation.getShelfLocation());
            // 创建借阅记录。
            BorrowRecord record = borrowRecordRepository.save(createBorrowRecord(user, book, copy, storageLocation, dueDate, batchNo));
            // 把借阅记录 id 回写到单册上。
            bookCopyService.attachBorrowRecord(copy, record.getId());
            // 加入结果列表。
            records.add(record);
        }

        // 返回本次借书记录。
        return records;
    }

    // 借书前先确认账号角色和状态，冻结/停用读者不能产生新借阅。
    private User validateReader(Long userId) {
        // 读者必须存在。
        User user = userRepository.findByIdForUpdate(userId).orElseThrow(() -> new RuntimeException("读者不存在"));
        // 只能 reader 角色办理借书。
        if (!"reader".equals(user.getRole())) {
            throw new RuntimeException("只有读者账号可以办理借书");
        }
        // disabled 账号不能借书。
        if (!isEnabled(user.getStatus())) {
            throw new RuntimeException("停用读者不能办理借书");
        }
        return user;
    }

    // 图书状态、库存和重复借阅在创建记录前统一校验。
    private void validateBorrowableBook(Long userId, Book book) {
        // 停用图书不能借出。
        if (!isEnabled(book.getStatus())) {
            throw new RuntimeException("停用图书不能办理新借阅");
        }
        // 可借数量必须大于 0。
        if (book.getAvailableCount() == null || book.getAvailableCount() <= 0) {
            throw new RuntimeException("《" + book.getTitle() + "》已借完，暂无可借库存");
        }

        // 同一个读者不能重复借同一本未归还图书。
        boolean alreadyBorrowed = borrowRecordRepository.existsByUserIdAndBookIdAndStatus(userId, book.getId(), "borrowed");
        if (alreadyBorrowed) {
            throw new RuntimeException("该读者已借阅《" + book.getTitle() + "》且尚未归还");
        }
    }

    // 借阅记录保留读者、图书和书架快照，避免后续资料修改影响历史记录。
    private BorrowRecord createBorrowRecord(User user, Book book, BookCopy copy, StorageLocation storageLocation, LocalDate dueDate, String batchNo) {
        // 构造借阅记录实体。
        BorrowRecord record = new BorrowRecord();
        // 读者和图书关联 id。
        record.setUserId(user.getId());
        record.setBookId(book.getId());
        // 单册和书架关联 id。
        record.setBookCopyId(copy.getId());
        record.setStorageLocationId(storageLocation.getId());
        // 保存读者快照。
        record.setReaderCard(user.getUsername());
        record.setReaderName(user.getRealName());
        record.setReaderPhone(user.getPhone());
        // 保存图书快照。
        record.setBookIsbn(book.getIsbn());
        record.setBookTitle(book.getTitle());
        record.setBookAuthor(book.getAuthor());
        // 保存单册和书架快照。
        record.setCopyCode(copy.getCopyCode());
        record.setCopyShelfLocation(copy.getShelfLocation());
        record.setShelfLocationSnapshot(storageLocation.getShelfLocation());
        // 保存批次、借阅日期和应还日期。
        record.setBatchNo(batchNo);
        record.setBorrowDate(LocalDate.now());
        record.setDueDate(dueDate);
        // 新借阅记录默认未归还。
        record.setStatus("borrowed");
        // 保存创建时间。
        record.setCreatedAt(LocalDateTime.now());
        return record;
    }

    // 批量借书时去重并保持用户选择顺序，同一本书不能在同一批次重复选择。
    private List<Long> normalizeBookIds(List<Long> bookIds) {
        // 必须至少选择一本图书。
        if (bookIds == null || bookIds.isEmpty()) {
            throw new RuntimeException("请至少选择一本图书");
        }
        // LinkedHashSet 去重并保留用户选择顺序。
        Set<Long> uniqueBookIds = new LinkedHashSet<>();
        for (Long bookId : bookIds) {
            // 忽略空 id。
            if (bookId != null) {
                // add 返回 false 表示重复。
                if (!uniqueBookIds.add(bookId)) {
                    throw new RuntimeException("同一本图书不能重复借阅，请选择不同图书");
                }
            }
        }
        // 全是空 id 时也视为未选择图书。
        if (uniqueBookIds.isEmpty()) {
            throw new RuntimeException("请至少选择一本图书");
        }
        // 转回列表供后续处理。
        return new ArrayList<>(uniqueBookIds);
    }

    // 还书时同步罚款、单册状态、图书库存和书架位置。
    @Transactional
    public BorrowRecord returnBook(Long recordId) {
        // 查出要归还的借阅记录。
        BorrowRecord record = borrowRecordRepository.findByIdForUpdate(recordId).orElseThrow(() -> new RuntimeException("借阅记录不存在"));

        // 已归还记录不能重复处理。
        if ("returned".equals(record.getStatus())) {
            throw new RuntimeException("该借阅记录已经归还，不能重复还书");
        }

        // 归还前计算并保存逾期罚款状态。
        assessOverdueFine(record);
        // 设置实际归还日期。
        record.setReturnDate(LocalDate.now());
        // 标记借阅记录已归还。
        record.setStatus("returned");
        // 保存记录状态。
        borrowRecordRepository.save(record);
        // 释放单册。
        bookCopyService.returnCopy(record.getId());

        // 查询关联图书。
        Book book = bookRepository.findById(record.getBookId()).orElseThrow(() -> new RuntimeException("图书不存在"));
        // 图书主表可借数量加 1，但不能超过馆藏总数。
        int updatedRows = bookRepository.increaseAvailableCountWithinTotal(book.getId());
        if (updatedRows == 0) {
            throw new RuntimeException("可借数量不能大于馆藏总数，请检查库存数据");
        }
        // 释放书架库存。
        storageLocationService.returnStorage(record, book);

        return record;
    }

    // 批量还书。
    @Transactional
    public List<BorrowRecord> returnBooks(List<Long> recordIds) {
        // 必须选择至少一条记录。
        if (recordIds == null || recordIds.isEmpty()) {
            throw new RuntimeException("请至少选择一条要归还的借阅记录");
        }

        // 保存本次归还结果。
        List<BorrowRecord> records = new ArrayList<>();
        // 去重处理，避免重复归还同一条记录。
        for (Long recordId : new LinkedHashSet<>(recordIds)) {
            // 忽略空 id。
            if (recordId != null) {
                records.add(returnBook(recordId));
            }
        }
        // 全是空 id 时提示未选择。
        if (records.isEmpty()) {
            throw new RuntimeException("请至少选择一条要归还的借阅记录");
        }
        return records;
    }

    // 判断状态是否可用，空状态也按启用处理。
    private boolean isEnabled(String status) {
        return status == null || status.isBlank() || "enabled".equals(status);
    }

    // 默认按 30 天借阅；自定义应还日期不得早于今天，也不得超过 90 天上限。
    private LocalDate normalizeDueDate(LocalDate dueDate) {
        // 未传应还日期时默认借阅 30 天。
        LocalDate normalizedDueDate = dueDate == null ? dueDateFromBorrowDays(DEFAULT_BORROW_DAYS) : dueDate;
        // 应还日期不能早于今天。
        if (normalizedDueDate.isBefore(LocalDate.now())) {
            throw new RuntimeException("应还日期不能早于今天");
        }
        // 借阅期限最多 90 天。
        if (normalizedDueDate.isAfter(LocalDate.now().plusDays(MAX_BORROW_DAYS))) {
            throw new RuntimeException("借阅期限最长 3 个月");
        }
        return normalizedDueDate;
    }

    // 逾期记录按天生成待扣罚款。
    private void assessOverdueFine(BorrowRecord record) {
        // 计算逾期天数。
        long overdueDays = overdueDays(record);
        // 未逾期不产生罚款。
        if (overdueDays <= 0) {
            return;
        }
        // 没有保存过罚款金额时，按逾期天数生成。
        if (record.getFineAmount() == null) {
            record.setFineAmount(BorrowRecordViewService.DAILY_FINE_AMOUNT
                    .multiply(BigDecimal.valueOf(overdueDays))
                    .setScale(2, RoundingMode.HALF_UP));
        }
        // 没有罚款状态时默认为 unpaid。
        if (record.getFineStatus() == null || record.getFineStatus().isBlank()) {
            record.setFineStatus("unpaid");
        }
        if (record.getFineNote() == null || record.getFineNote().isBlank()) {
            record.setFineNote("逾期罚款待缴纳，读者账号已自动冻结");
        }
        userRepository.findById(record.getUserId()).ifPresent(reader -> {
            if ("reader".equals(reader.getRole()) && !"disabled".equals(reader.getStatus())) {
                reader.setStatus("disabled");
                userRepository.save(reader);
            }
        });
    }

    // 归还时计算当前逾期天数。
    private long overdueDays(BorrowRecord record) {
        // 没有应还日期或还没到期时为 0。
        if (record.getDueDate() == null || !record.getDueDate().isBefore(LocalDate.now())) {
            return 0;
        }
        // 应还日期到今天的天数差。
        return ChronoUnit.DAYS.between(record.getDueDate(), LocalDate.now());
    }
}
