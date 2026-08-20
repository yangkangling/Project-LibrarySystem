package com.example.demo.service;

import com.example.demo.entity.Book;
import com.example.demo.entity.BookCopy;
import com.example.demo.entity.BorrowRecord;
import com.example.demo.repository.BookCopyRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

// 单册编号和借还状态维护。
@Service
public class BookCopyService {
    // 单册仓库，负责具体馆藏单册的读写。
    private final BookCopyRepository bookCopyRepository;

    // 构造方法注入单册仓库。
    public BookCopyService(BookCopyRepository bookCopyRepository) {
        this.bookCopyRepository = bookCopyRepository;
    }

    // 根据图书馆藏总数同步单册记录。
    public void syncCopies(Book book) {
        // 图书或馆藏数量不完整时不处理。
        if (book == null || book.getId() == null || book.getTotalCount() == null) {
            return;
        }

        // 馆藏总数变化时自动补齐或停用具体单册，已借出的单册不会被直接删除。
        List<BookCopy> copies = bookCopyRepository.findByBookIdOrderByCopyCodeAsc(book.getId());
        // 目标单册数量不能小于 0。
        int targetTotal = Math.max(0, book.getTotalCount());
        // 当前有效单册数量，disabled 不计入有效馆藏。
        long currentTotal = copies.stream().filter(copy -> !"disabled".equals(copy.getStatus())).count();

        // 当前有效单册少于目标数量时，需要补新单册。
        if (currentTotal < targetTotal) {
            // 从已有最大流水号后继续编号。
            int next = nextSerialNumber(copies);
            // 沿用已有编号前缀，避免老编号变化。
            String codePrefix = copyCodePrefix(book, copies);
            // 逐本补齐单册。
            for (long count = currentTotal; count < targetTotal; count++) {
                bookCopyRepository.save(newCopy(book, codePrefix, next, book.getShelfLocation()));
                next++;
            }
        // 当前有效单册多于目标数量时，只停用空闲单册。
        } else if (currentTotal > targetTotal) {
            // 需要停用的数量。
            long removeCount = currentTotal - targetTotal;
            // 从编号靠后的可借单册开始停用。
            copies.stream()
                    .filter(copy -> "available".equals(copy.getStatus()))
                    .sorted(Comparator.comparing(BookCopy::getCopyCode).reversed())
                    .limit(removeCount)
                    .forEach(copy -> {
                        copy.setStatus("disabled");
                        copy.setUpdatedAt(LocalDateTime.now());
                        bookCopyRepository.save(copy);
                    });
        }

        // 图书主书架修改后，同步未借出的单册书架位置。
        bookCopyRepository.findByBookIdOrderByCopyCodeAsc(book.getId()).forEach(copy -> {
            // 已借出的单册保留借出时书架，不强行改动。
            if (!"borrowed".equals(copy.getStatus()) && !Objects.equals(book.getShelfLocation(), copy.getShelfLocation())) {
                copy.setShelfLocation(book.getShelfLocation());
                copy.setUpdatedAt(LocalDateTime.now());
                bookCopyRepository.save(copy);
            }
        });
    }

    // 借书时选择一个可借单册并标记为借出。
    public BookCopy borrowAvailableCopy(Book book, Long userId) {
        return borrowAvailableCopy(book, userId, book.getShelfLocation());
    }

    // 借书时优先选择指定书架上的可借单册。
    public BookCopy borrowAvailableCopy(Book book, Long userId, String shelfLocation) {
        // 优先在本次扣减的书架上占用，保证书架库存和单册位置一致。
        return reserveAvailableCopy(book, userId, shelfLocation)
                // 兼容旧数据：如果旧单册没有书架或书架不一致，兜底选择任意可借单册。
                .orElseGet(() -> reserveAvailableCopy(book, userId, null)
                        .orElseThrow(() -> new RuntimeException("《" + book.getTitle() + "》没有可借单册，请刷新后重试")));
    }

    // 通过条件更新占用一本可借单册；并发失败时重新取候选少量重试。
    private Optional<BookCopy> reserveAvailableCopy(Book book, Long userId, String shelfLocation) {
        for (int attempt = 0; attempt < 3; attempt++) {
            List<BookCopy> candidates = hasText(shelfLocation)
                    ? bookCopyRepository.findTop10ByBookIdAndStatusAndShelfLocationOrderByCopyCodeAsc(book.getId(), "available", shelfLocation)
                    : bookCopyRepository.findTop10ByBookIdAndStatusOrderByCopyCodeAsc(book.getId(), "available");
            if (candidates.isEmpty()) {
                return Optional.empty();
            }
            for (BookCopy copy : candidates) {
                LocalDateTime updatedAt = LocalDateTime.now();
                int updatedRows = bookCopyRepository.markBorrowedIfAvailable(copy.getId(), userId, updatedAt);
                if (updatedRows == 1) {
                    if (hasText(shelfLocation) && !hasText(copy.getShelfLocation())) {
                        copy.setShelfLocation(shelfLocation);
                    }
                    copy.setStatus("borrowed");
                    copy.setCurrentUserId(userId);
                    copy.setUpdatedAt(updatedAt);
                    return Optional.of(copy);
                }
            }
        }
        return Optional.empty();
    }

    // 新增书架库存时同步创建对应书架上的可借单册。
    public void addCopies(Book book, String shelfLocation, int count) {
        // 数量不合法时不处理。
        if (book == null || book.getId() == null || count <= 0) {
            return;
        }
        // 读取现有单册，沿用原有编号前缀并从最大流水号后继续。
        List<BookCopy> copies = bookCopyRepository.findByBookIdOrderByCopyCodeAsc(book.getId());
        String codePrefix = copyCodePrefix(book, copies);
        int next = nextSerialNumber(copies);
        // 按新增数量创建新单册。
        for (int index = 0; index < count; index++) {
            bookCopyRepository.save(newCopy(book, codePrefix, next + index, shelfLocation));
        }
    }

    // 减少书架库存时停用对应书架上的空闲单册。
    public void disableAvailableCopies(Long bookId, String shelfLocation, int count) {
        // 数量不合法时不处理。
        if (bookId == null || count <= 0) {
            return;
        }
        // 从该书架编号靠后的可借单册开始停用。
        List<BookCopy> copies = bookCopyRepository.findByBookIdAndStatusOrderByCopyCodeAsc(bookId, "available").stream()
                .filter(copy -> !hasText(shelfLocation) || Objects.equals(shelfLocation, copy.getShelfLocation()))
                .sorted(Comparator.comparing(BookCopy::getCopyCode).reversed())
                .limit(count)
                .collect(java.util.stream.Collectors.toList());
        // 可停用单册不足说明单册和书架库存不一致，不能继续减少。
        if (copies.size() < count) {
            throw new RuntimeException("该书架可停用单册不足，请先检查单册状态");
        }
        // 标记为空闲停用，历史借阅记录不受影响。
        copies.forEach(copy -> {
            copy.setStatus("disabled");
            copy.setUpdatedAt(LocalDateTime.now());
            bookCopyRepository.save(copy);
        });
    }

    // 借阅记录保存后，把记录 id 回填到单册上。
    public void attachBorrowRecord(BookCopy copy, Long borrowRecordId) {
        copy.setCurrentBorrowRecordId(borrowRecordId);
        copy.setUpdatedAt(LocalDateTime.now());
        bookCopyRepository.save(copy);
    }

    // 还书时释放单册占用。
    public void returnCopy(Long borrowRecordId) {
        // 根据当前借阅记录 id 找到对应单册。
        bookCopyRepository.findByCurrentBorrowRecordId(borrowRecordId).ifPresent(copy -> {
            // 单册恢复可借。
            copy.setStatus("available");
            // 清空当前借阅读者。
            copy.setCurrentUserId(null);
            // 清空当前借阅记录。
            copy.setCurrentBorrowRecordId(null);
            // 更新时间。
            copy.setUpdatedAt(LocalDateTime.now());
            bookCopyRepository.save(copy);
        });
    }

    // 初始化或修复旧数据时，把借阅记录绑定到单册。
    public boolean attachExistingBorrowRecord(Book book, BorrowRecord record) {
        // 图书或记录为空时不处理。
        if (book == null || record == null) {
            return false;
        }

        // 尽量找到该记录对应的单册。
        BookCopy copy = findCopyForRecord(book, record);
        if (copy == null) {
            return false;
        }

        // 未还记录需要同步单册借出状态。
        if ("borrowed".equals(record.getStatus())) {
            copy.setStatus("borrowed");
            copy.setCurrentUserId(record.getUserId());
            copy.setCurrentBorrowRecordId(record.getId());
            copy.setUpdatedAt(LocalDateTime.now());
            bookCopyRepository.save(copy);
        }

        // 标记借阅记录是否被补齐。
        boolean changed = false;
        // 补齐单册 id。
        if (record.getBookCopyId() == null || !record.getBookCopyId().equals(copy.getId())) {
            record.setBookCopyId(copy.getId());
            changed = true;
        }
        // 补齐单册编号。
        if (!hasText(record.getCopyCode())) {
            record.setCopyCode(copy.getCopyCode());
            changed = true;
        }
        // 补齐单册书架位置。
        if (!hasText(record.getCopyShelfLocation())) {
            record.setCopyShelfLocation(copy.getShelfLocation());
            changed = true;
        }
        return changed;
    }

    // 根据借阅记录寻找对应单册。
    private BookCopy findCopyForRecord(Book book, BorrowRecord record) {
        // 第一优先：记录里已经保存单册 id。
        if (record.getBookCopyId() != null) {
            BookCopy copy = bookCopyRepository.findById(record.getBookCopyId()).orElse(null);
            if (copy != null) {
                return copy;
            }
        }

        // 未归还记录优先找当前绑定的单册。
        if ("borrowed".equals(record.getStatus())) {
            BookCopy borrowedCopy = bookCopyRepository.findByCurrentBorrowRecordId(record.getId()).orElse(null);
            if (borrowedCopy != null) {
                return borrowedCopy;
            }
            // 找不到绑定时临时取一本可借单册用于修复数据。
            return bookCopyRepository.findFirstByBookIdAndStatusOrderByCopyCodeAsc(book.getId(), "available").orElse(null);
        }

        // 已归还历史记录无法确定具体单册时，按记录 id 稳定映射到一本单册。
        List<BookCopy> copies = bookCopyRepository.findByBookIdOrderByCopyCodeAsc(book.getId());
        if (copies.isEmpty()) {
            return null;
        }
        long recordId = record.getId() == null ? 1 : record.getId();
        int index = Math.floorMod(recordId - 1, copies.size());
        return copies.get(index);
    }

    // 统计某本书当前可借单册数量。
    public long availableCopyCount(Long bookId) {
        return bookCopyRepository.countByBookIdAndStatus(bookId, "available");
    }

    // 创建一本新的可借单册。
    private BookCopy newCopy(Book book, String codePrefix, int serialNumber, String shelfLocation) {
        // 创建和更新时间使用同一时间。
        LocalDateTime now = LocalDateTime.now();
        // 构造单册实体。
        BookCopy copy = new BookCopy();
        copy.setBookId(book.getId());
        copy.setCopyCode(copyCode(codePrefix, serialNumber));
        copy.setShelfLocation(hasText(shelfLocation) ? shelfLocation : book.getShelfLocation());
        copy.setStatus("available");
        copy.setCreatedAt(now);
        copy.setUpdatedAt(now);
        return copy;
    }

    // 内部副本号保留最后三位流水号，用于区分同一本书的多本实体馆藏。
    private String copyCode(String codePrefix, int serialNumber) {
        return codePrefix.toUpperCase(Locale.ROOT) + "-" + String.format("%03d", serialNumber);
    }

    // 优先沿用已有编号前缀，避免修改馆藏数量时改变既有单册编号。
    private String copyCodePrefix(Book book, List<BookCopy> copies) {
        return copies.stream()
                .map(BookCopy::getCopyCode)
                .filter(this::hasText)
                .map(String::trim)
                .filter(code -> code.matches("(?i)^.+-\\d{3}$"))
                .map(this::copyCodePrefixFromExisting)
                .findFirst()
                .orElseGet(() -> defaultCopyCodePrefix(book));
    }

    private String copyCodePrefixFromExisting(String code) {
        // 套书展示前缀本身就是完整前缀时，直接沿用。
        if (code.matches("^\\d{13}-\\d{2}-\\d{3}$") || code.matches("^\\d{2}-\\d{3}$")) {
            return code;
        }
        // 普通内部编号去掉最后的流水号，保留前缀。
        return code.substring(0, code.length() - 4);
    }

    // 普通书内部格式为 ISBN-00-000-流水号；页面展示时会转换为 ISBN-流水号。
    private String defaultCopyCodePrefix(Book book) {
        String isbn = hasText(book.getIsbn()) ? book.getIsbn().trim() : "BOOK" + book.getId();
        return (isbn + "-00-000").toUpperCase(Locale.ROOT);
    }

    private int nextSerialNumber(List<BookCopy> copies) {
        // 提取已有编号最后三位，取最大值后加 1。
        return copies.stream()
                .map(BookCopy::getCopyCode)
                .filter(this::hasText)
                .map(code -> code.substring(Math.max(0, code.length() - 3)))
                .filter(value -> value.matches("\\d{3}"))
                .mapToInt(Integer::parseInt)
                .max()
                .orElse(0) + 1;
    }

    // 判断文本是否有实际内容。
    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
