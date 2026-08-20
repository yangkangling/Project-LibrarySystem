package com.example.demo.service;

import com.example.demo.entity.Book;
import com.example.demo.entity.BorrowRecord;
import com.example.demo.entity.Shelf;
import com.example.demo.entity.StorageLocation;
import com.example.demo.repository.BookRepository;
import com.example.demo.repository.ShelfRepository;
import com.example.demo.repository.StorageLocationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 维护书架库存占用和归还。
@Service
public class StorageLocationService {
    private static final int SHELF_NUMBER_MAX = 50;
    private static final int BOOK_TOTAL_COUNT_MAX = 50;
    private static final Pattern SHELF_PATTERN = Pattern.compile("^([A-Z])-(\\d{1,2})-(\\d{1,2})$", Pattern.CASE_INSENSITIVE);

    // 书架库存仓库，负责实际数据库读写。
    private final StorageLocationRepository storageLocationRepository;
    // 图书仓库，用来同步图书主表馆藏和可借数量。
    private final BookRepository bookRepository;
    // 单册服务，用来新增或停用具体单册。
    private final BookCopyService bookCopyService;
    // 独立书架仓库，只登记书架位置本身。
    private final ShelfRepository shelfRepository;

    // 构造方法注入书架库存仓库、图书仓库和单册服务。
    public StorageLocationService(
            StorageLocationRepository storageLocationRepository,
            BookRepository bookRepository,
            BookCopyService bookCopyService,
            ShelfRepository shelfRepository
    ) {
        this.storageLocationRepository = storageLocationRepository;
        this.bookRepository = bookRepository;
        this.bookCopyService = bookCopyService;
        this.shelfRepository = shelfRepository;
    }

    // 同步某本书的主书架库存记录。
    @Transactional
    public StorageLocation syncPrimaryStorage(Book book) {
        // 图书为空时无法维护书架。
        if (book == null || book.getId() == null) {
            throw new RuntimeException("图书不存在，无法维护书架存储");
        }
        // 读取该书所有书架库存。
        String shelf = normalizeShelf(book.getShelfLocation());
        if (!Objects.equals(book.getShelfLocation(), shelf)) {
            book.setShelfLocation(shelf);
            bookRepository.save(book);
        }
        List<StorageLocation> locations = storageLocationRepository.findByBookIdOrderByIdAsc(book.getId());
        // 没有书架时创建默认主书架。
        if (locations.isEmpty()) {
            StorageLocation saved = storageLocationRepository.save(newStorageLocation(book, shelf, safeInt(book.getTotalCount()), safeInt(book.getAvailableCount()), "默认书架存储"));
            ensureShelf(saved.getShelfLocation());
            return saved;
        }
        // 已经拆到多个书架时，不再把第一条覆盖成整本书库存，避免破坏多书架分布。
        if (locations.size() > 1) {
            return locations.get(0);
        }

        // 只有单一书架时，才让它跟随图书主表。
        StorageLocation storageLocation = locations.get(0);

        // 书架位置跟随图书主表。
        storageLocation.setShelfLocation(shelf);
        // 馆藏数量跟随图书主表。
        storageLocation.setTotalCount(safeInt(book.getTotalCount()));
        // 可借数量跟随图书主表。
        storageLocation.setAvailableCount(safeInt(book.getAvailableCount()));
        // 更新同步时间。
        storageLocation.setUpdatedAt(LocalDateTime.now());
        ensureShelf(storageLocation.getShelfLocation());
        // 保存同步结果。
        return storageLocationRepository.save(storageLocation);
    }

    // 给某本书增加一个书架库存；同位置已存在时累加数量。
    @Transactional
    public StorageLocation addStorage(Long bookId, String shelfLocation, Integer count, String remark) {
        // 必须指定图书。
        if (bookId == null) {
            throw new RuntimeException("请选择图书");
        }
        // 查询关联图书。
        Book book = bookRepository.findById(bookId).orElseThrow(() -> new RuntimeException("图书不存在"));
        // 规范化书架和数量。
        String shelf = normalizeShelf(shelfLocation);
        int amount = normalizePositiveCount(count);
        if (safeInt(book.getTotalCount()) + amount > BOOK_TOTAL_COUNT_MAX) {
            throw new RuntimeException("单本图书馆藏总量不能超过 " + BOOK_TOTAL_COUNT_MAX + " 册");
        }
        ensureShelf(shelf);
        // 同一本书同一书架存在时累加，不重复建行。
        StorageLocation storageLocation = storageLocationRepository.findFirstByBookIdAndShelfLocationOrderByIdAsc(book.getId(), shelf)
                .orElseGet(() -> newStorageLocation(book, shelf, 0, 0, firstText(remark, "新增书架库存")));
        // 累加馆藏和可借数量。
        storageLocation.setTotalCount(safeInt(storageLocation.getTotalCount()) + amount);
        storageLocation.setAvailableCount(safeInt(storageLocation.getAvailableCount()) + amount);
        storageLocation.setRemark(firstText(remark, storageLocation.getRemark()));
        storageLocation.setUpdatedAt(LocalDateTime.now());
        StorageLocation saved = storageLocationRepository.save(storageLocation);

        // 新增对应书架上的单册。
        bookCopyService.addCopies(book, shelf, amount);
        // 按所有书架重新汇总图书主表数量。
        recalculateBookCounts(book);
        return saved;
    }

    // 减少某个书架的空闲馆藏数量。
    @Transactional
    public StorageLocation decreaseStorage(Long storageLocationId, Integer count) {
        // 查询书架库存记录。
        StorageLocation storageLocation = storageLocationRepository.findById(storageLocationId)
                .orElseThrow(() -> new RuntimeException("书架存储记录不存在"));
        // 规范化减少数量。
        int amount = normalizePositiveCount(count);
        int available = safeInt(storageLocation.getAvailableCount());
        int total = safeInt(storageLocation.getTotalCount());
        // 只能减少还没有借出去的空闲册数。
        if (amount > available) {
            throw new RuntimeException("只能减少当前可借的空闲册数，已借出的册数不能直接减少");
        }
        // 停用对应书架上的空闲单册。
        bookCopyService.disableAvailableCopies(storageLocation.getBookId(), storageLocation.getShelfLocation(), amount);
        // 扣减书架库存。
        storageLocation.setTotalCount(total - amount);
        storageLocation.setAvailableCount(available - amount);
        storageLocation.setUpdatedAt(LocalDateTime.now());

        if (safeInt(storageLocation.getTotalCount()) <= 0) {
            storageLocationRepository.delete(storageLocation);
        } else {
            storageLocationRepository.save(storageLocation);
        }
        // 重新汇总图书主表数量。
        Book book = bookRepository.findById(storageLocation.getBookId()).orElseThrow(() -> new RuntimeException("图书不存在"));
        recalculateBookCounts(book);
        return storageLocation;
    }

    // 删除没有借出册数的书架库存。
    @Transactional
    public void deleteStorage(Long storageLocationId) {
        // 查询书架库存记录。
        StorageLocation storageLocation = storageLocationRepository.findById(storageLocationId)
                .orElseThrow(() -> new RuntimeException("书架存储记录不存在"));
        // 有借出册数时必须等归还后再删。
        if (borrowedCount(storageLocation) > 0) {
            throw new RuntimeException("该书架还有已借出册数，不能直接删除");
        }
        // 空书架直接删除。
        if (safeInt(storageLocation.getAvailableCount()) <= 0) {
            Book book = bookRepository.findById(storageLocation.getBookId()).orElseThrow(() -> new RuntimeException("图书不存在"));
            storageLocationRepository.delete(storageLocation);
            recalculateBookCounts(book);
            return;
        }
        // 删除等价于减少全部可借册数。
        decreaseStorage(storageLocationId, safeInt(storageLocation.getAvailableCount()));
    }

    // 只新增一个空书架，不绑定任何图书。
    @Transactional
    public Shelf createShelf(String shelfLocation, String remark) {
        String shelf = normalizeShelf(shelfLocation);
        if (shelfRepository.existsByShelfLocation(shelf)) {
            throw new RuntimeException("书架已存在");
        }
        LocalDateTime now = LocalDateTime.now();
        Shelf entity = new Shelf();
        entity.setShelfLocation(shelf);
        entity.setRemark(remark);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return shelfRepository.save(entity);
    }

    // 确保书架位置存在；已有则复用，没有则自动补建。
    @Transactional
    public Shelf ensureShelf(String shelfLocation) {
        String shelf = normalizeShelf(shelfLocation);
        return shelfRepository.findByShelfLocation(shelf).orElseGet(() -> {
            LocalDateTime now = LocalDateTime.now();
            Shelf entity = new Shelf();
            entity.setShelfLocation(shelf);
            entity.setRemark("由现有馆藏位置自动补齐");
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            return shelfRepository.save(entity);
        });
    }

    // 把历史图书库存位置同步进独立书架表。
    public String normalizeShelfLocation(String shelfLocation) {
        return normalizeShelf(shelfLocation);
    }

    @Transactional
    public void syncShelvesFromExistingLocations() {
        storageLocationRepository.findDistinctShelfLocations().stream()
                .filter(this::hasText)
                .forEach(this::ensureShelf);
        bookRepository.findAll().stream()
                .map(Book::getShelfLocation)
                .filter(this::hasText)
                .forEach(this::ensureShelf);
    }

    // 删除空书架；已有图书库存的位置不能删。
    @Transactional
    public void deleteShelf(Long shelfId) {
        Shelf shelf = shelfRepository.findById(shelfId)
                .orElseThrow(() -> new RuntimeException("书架不存在"));
        if (storageLocationRepository.existsByShelfLocationAndTotalCountGreaterThan(shelf.getShelfLocation(), 0)) {
            throw new RuntimeException("该书架已有图书库存，不能删除");
        }
        shelfRepository.delete(shelf);
    }

    // 查询某本书的所有书架库存记录。
    public List<StorageLocation> findByBookId(Long bookId) {
        return storageLocationRepository.findByBookIdOrderByIdAsc(bookId);
    }

    // 删除某本书关联的书架库存记录。
    public void deleteByBookId(Long bookId) {
        storageLocationRepository.deleteByBookId(bookId);
    }

    // 借书时占用一个有可借数量的书架库存。
    public StorageLocation borrowAvailableStorage(Book book) {
        // 找到第一条可借书架；没有就先同步主书架。
        StorageLocation storageLocation = storageLocationRepository
                .findFirstByBookIdAndAvailableCountGreaterThanOrderByIdAsc(book.getId(), 0)
                .orElseGet(() -> syncPrimaryStorage(book));

        // 条件更新扣减书架可借数量，避免并发下扣成负数。
        int updatedRows = storageLocationRepository.decreaseAvailableCountWhenAvailable(storageLocation.getId(), LocalDateTime.now());
        // 没扣成功说明可借数量已不足。
        if (updatedRows == 0) {
            throw new RuntimeException("《" + book.getTitle() + "》书架存储可借数量不足，请刷新后重试");
        }
        // 同步内存对象，方便后续写借阅快照。
        storageLocation.setAvailableCount(safeInt(storageLocation.getAvailableCount()) - 1);
        return storageLocation;
    }

    // 还书时归还书架可借数量。
    public StorageLocation returnStorage(BorrowRecord record, Book book) {
        // 优先找到当时借出的书架记录，找不到就同步主书架。
        StorageLocation storageLocation = findStorageForRecord(book, record)
                .orElseGet(() -> syncPrimaryStorage(book));

        // 条件更新增加可借数量，但不能超过馆藏数量。
        int updatedRows = storageLocationRepository.increaseAvailableCountWithinTotal(storageLocation.getId(), LocalDateTime.now());
        // 没更新成功说明库存数据异常。
        if (updatedRows == 0) {
            throw new RuntimeException("书架存储可借数量不能大于馆藏数量，请检查库存数据");
        }
        // 同步内存对象。
        storageLocation.setAvailableCount(safeInt(storageLocation.getAvailableCount()) + 1);
        return storageLocation;
    }

    // 初始化或修复旧数据时，把借阅记录绑定到对应书架记录。
    public boolean attachExistingBorrowRecord(Book book, BorrowRecord record) {
        // 图书或记录为空时不处理。
        if (book == null || record == null) {
            return false;
        }
        // 找到这条借阅记录对应的书架。
        StorageLocation storageLocation = findStorageForRecord(book, record)
                .orElseGet(() -> syncPrimaryStorage(book));

        // 标记借阅记录是否发生了修改。
        boolean changed = false;
        // 补齐书架记录 id。
        if (record.getStorageLocationId() == null || !record.getStorageLocationId().equals(storageLocation.getId())) {
            record.setStorageLocationId(storageLocation.getId());
            changed = true;
        }
        // 补齐借阅时的书架位置快照。
        if (!hasText(record.getShelfLocationSnapshot())) {
            record.setShelfLocationSnapshot(firstText(storageLocation.getShelfLocation(), book.getShelfLocation()));
            changed = true;
        }
        return changed;
    }

    // 根据借阅记录尽量还原当时使用的书架库存。
    private Optional<StorageLocation> findStorageForRecord(Book book, BorrowRecord record) {
        // 第一优先：借阅记录已经保存了书架记录 id。
        if (record.getStorageLocationId() != null) {
            Optional<StorageLocation> storageLocation = storageLocationRepository.findById(record.getStorageLocationId());
            if (storageLocation.isPresent()) {
                return storageLocation;
            }
        }
        // 第二优先：按借阅快照里的书架位置查找。
        if (hasText(record.getShelfLocationSnapshot())) {
            Optional<StorageLocation> storageLocation = storageLocationRepository
                    .findFirstByBookIdAndShelfLocationOrderByIdAsc(book.getId(), record.getShelfLocationSnapshot());
            if (storageLocation.isPresent()) {
                return storageLocation;
            }
        }
        // 第三优先：按单册书架位置查找。
        if (hasText(record.getCopyShelfLocation())) {
            Optional<StorageLocation> storageLocation = storageLocationRepository
                    .findFirstByBookIdAndShelfLocationOrderByIdAsc(book.getId(), record.getCopyShelfLocation());
            if (storageLocation.isPresent()) {
                return storageLocation;
            }
        }
        // 兜底：返回该图书第一条书架记录。
        return storageLocationRepository.findFirstByBookIdOrderByIdAsc(book.getId());
    }

    // 为图书创建默认主书架库存记录。
    private StorageLocation newStorageLocation(Book book) {
        return newStorageLocation(book, book.getShelfLocation(), safeInt(book.getTotalCount()), safeInt(book.getAvailableCount()), "默认书架存储");
    }

    // 为图书创建指定书架库存记录。
    private StorageLocation newStorageLocation(Book book, String shelfLocation, int totalCount, int availableCount, String remark) {
        // 创建和更新时间使用同一个时间点。
        LocalDateTime now = LocalDateTime.now();
        // 构造书架库存实体。
        StorageLocation storageLocation = new StorageLocation();
        storageLocation.setBookId(book.getId());
        storageLocation.setShelfLocation(shelfLocation);
        storageLocation.setTotalCount(totalCount);
        storageLocation.setAvailableCount(availableCount);
        storageLocation.setRemark(remark);
        storageLocation.setCreatedAt(now);
        storageLocation.setUpdatedAt(now);
        return storageLocation;
    }

    // 根据书架库存汇总图书主表的馆藏和可借数量。
    private void recalculateBookCounts(Book book) {
        // 查询所有书架记录。
        List<StorageLocation> locations = storageLocationRepository.findByBookIdOrderByIdAsc(book.getId());
        // 汇总总册数。
        int total = locations.stream().mapToInt(location -> safeInt(location.getTotalCount())).sum();
        // 汇总可借数。
        int available = locations.stream().mapToInt(location -> safeInt(location.getAvailableCount())).sum();
        // 写回图书主表。
        book.setTotalCount(total);
        book.setAvailableCount(available);
        // 当前主书架不存在时，切到第一条剩余书架。
        if (!locations.isEmpty() && locations.stream().noneMatch(location -> java.util.Objects.equals(location.getShelfLocation(), book.getShelfLocation()))) {
            book.setShelfLocation(locations.get(0).getShelfLocation());
        }
        bookRepository.save(book);
    }

    // 计算书架已借出册数。
    private int borrowedCount(StorageLocation storageLocation) {
        return safeInt(storageLocation.getTotalCount()) - safeInt(storageLocation.getAvailableCount());
    }

    // 校验并规范化书架位置。
    private String normalizeShelf(String shelfLocation) {
        if (!hasText(shelfLocation)) {
            throw new RuntimeException("书架位置不能为空");
        }
        Matcher matcher = SHELF_PATTERN.matcher(shelfLocation.trim());
        if (!matcher.matches()) {
            throw new RuntimeException("书架位置格式应为 A-01-01");
        }
        String area = matcher.group(1).toUpperCase(Locale.ROOT);
        int row = Integer.parseInt(matcher.group(2));
        int slot = Integer.parseInt(matcher.group(3));
        if (row < 1 || row > SHELF_NUMBER_MAX || slot < 1 || slot > SHELF_NUMBER_MAX) {
            throw new RuntimeException("书架排号和位号必须在 1 到 50 之间");
        }
        return area + "-" + String.format("%02d", row) + "-" + String.format("%02d", slot);
    }

    // 校验并规范化正整数数量。
    private int normalizePositiveCount(Integer count) {
        if (count == null || count < 1) {
            throw new RuntimeException("数量不能少于 1");
        }
        if (count > BOOK_TOTAL_COUNT_MAX) {
            throw new RuntimeException("单次数量不能超过 " + BOOK_TOTAL_COUNT_MAX);
        }
        return count;
    }

    // 返回第一个非空文本，否则返回第二个。
    private String firstText(String first, String second) {
        return hasText(first) ? first : second;
    }

    // 把可能为空的数量转换成 0。
    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    // 判断文本是否有实际内容。
    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
