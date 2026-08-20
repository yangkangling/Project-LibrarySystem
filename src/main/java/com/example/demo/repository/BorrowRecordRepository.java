package com.example.demo.repository;

import com.example.demo.entity.BorrowRecord;
import javax.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

// 借阅记录数据访问。
public interface BorrowRecordRepository extends JpaRepository<BorrowRecord, Long> {
    // 统计某个读者某种状态的借阅记录数量。
    long countByUserIdAndStatus(Long userId, String status);

    // 判断读者是否已借同一本图书且尚未归还。
    boolean existsByUserIdAndBookIdAndStatus(Long userId, Long bookId, String status);

    // 判断读者是否存在早于指定日期的未还记录，用于逾期禁借。
    boolean existsByUserIdAndStatusAndDueDateBefore(Long userId, String status, LocalDate date);

    // 判断某本图书是否存在借阅历史。
    boolean existsByBookId(Long bookId);

    // 统计某本图书当前指定状态的借阅记录数。
    long countByBookIdAndStatus(Long bookId, String status);

    // 查询某种状态的借阅记录。
    List<BorrowRecord> findByStatus(String status);

    // 归还时锁定借阅记录，避免并发重复还书导致库存重复恢复。
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from BorrowRecord r where r.id = :id")
    Optional<BorrowRecord> findByIdForUpdate(@Param("id") Long id);

    // 查询当前未还且已过应还日期的记录。
    List<BorrowRecord> findByStatusAndDueDateBeforeOrderByDueDateAsc(String status, LocalDate date);

    // 按续借状态查询申请记录。
    List<BorrowRecord> findByExtensionStatusOrderByExtensionRequestedAtAsc(String extensionStatus);

    // 查询所有提交过续借申请的记录。
    List<BorrowRecord> findByExtensionRequestedAtIsNotNullOrderByExtensionRequestedAtDesc();

    // 统计某种原始状态的借阅记录数量。
    long countByStatus(String status);

    // 统计某种状态且应还日期早于指定日期的记录数量。
    long countByStatusAndDueDateBefore(String status, LocalDate date);

    // 查询某本图书最新 10 条借阅记录。
    List<BorrowRecord> findTop10ByBookIdOrderByIdDesc(Long bookId);

    // 查询某个读者的全部借阅记录。
    List<BorrowRecord> findByUserIdOrderByIdDesc(Long userId);

    // 分页查询某个读者的借阅记录。
    Page<BorrowRecord> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);

    // 分页查询某个读者指定原始状态的借阅记录。
    Page<BorrowRecord> findByUserIdAndStatusOrderByIdDesc(Long userId, String status, Pageable pageable);

    // 查询某个读者指定原始状态的借阅记录。
    List<BorrowRecord> findByUserIdAndStatusOrderByIdDesc(Long userId, String status);

    // 查询某本图书的全部借阅记录。
    List<BorrowRecord> findByBookIdOrderByIdDesc(Long bookId);

    // 按借阅日期正序查询某本图书的记录，用于初始化和编号修复。
    List<BorrowRecord> findByBookIdOrderByBorrowDateAscIdAsc(Long bookId);

    // 借阅记录分页查询，按状态、读者、图书筛选。
    @Query("select r from BorrowRecord r " +
            "where (:status is null or :status = '' or r.status = :status) " +
            "and (:userId is null or r.userId = :userId) " +
            "and (:bookId is null or r.bookId = :bookId) " +
            "order by r.id desc")
    Page<BorrowRecord> search(
            // 原始借阅状态。
            @Param("status") String status,
            // 读者 id。
            @Param("userId") Long userId,
            // 图书 id。
            @Param("bookId") Long bookId,
            // 分页参数。
            Pageable pageable
    );

    // 管理端借阅记录分页查询，按关键字、状态、读者、图书和日期过滤。
    @Query("select r from BorrowRecord r " +
            "where (:keyword is null or :keyword = '' " +
            "or r.readerCard like concat('%', :keyword, '%') " +
            "or r.readerName like concat('%', :keyword, '%') " +
            "or r.readerPhone like concat('%', :keyword, '%') " +
            "or r.bookIsbn like concat('%', :keyword, '%') " +
            "or r.bookTitle like concat('%', :keyword, '%') " +
            "or r.bookAuthor like concat('%', :keyword, '%') " +
            "or r.copyCode like concat('%', :keyword, '%') " +
            "or r.batchNo like concat('%', :keyword, '%')) " +
            "and (:status is null or :status = '' or r.status = :status) " +
            "and (:userId is null or r.userId = :userId) " +
            "and (:bookId is null or r.bookId = :bookId) " +
            "and (:borrowStart is null or r.borrowDate >= :borrowStart) " +
            "and (:borrowEnd is null or r.borrowDate <= :borrowEnd) " +
            "and (:dueStart is null or r.dueDate >= :dueStart) " +
            "and (:dueEnd is null or r.dueDate <= :dueEnd) " +
            "order by r.id desc")
    Page<BorrowRecord> searchRecords(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("userId") Long userId,
            @Param("bookId") Long bookId,
            @Param("borrowStart") LocalDate borrowStart,
            @Param("borrowEnd") LocalDate borrowEnd,
            @Param("dueStart") LocalDate dueStart,
            @Param("dueEnd") LocalDate dueEnd,
            Pageable pageable
    );

    // 管理端还书候选分页查询，只查当前未归还记录。
    @Query("select r from BorrowRecord r " +
            "where r.status = 'borrowed' " +
            "and (:keyword is null or :keyword = '' " +
            "or r.readerCard like concat('%', :keyword, '%') " +
            "or r.readerName like concat('%', :keyword, '%') " +
            "or r.readerPhone like concat('%', :keyword, '%') " +
            "or r.bookIsbn like concat('%', :keyword, '%') " +
            "or r.bookTitle like concat('%', :keyword, '%') " +
            "or r.bookAuthor like concat('%', :keyword, '%') " +
            "or r.copyCode like concat('%', :keyword, '%') " +
            "or r.batchNo like concat('%', :keyword, '%')) " +
            "order by r.id desc")
    Page<BorrowRecord> searchReturnOptions(
            @Param("keyword") String keyword,
            Pageable pageable
    );

    // 管理端续借申请分页查询，待处理申请优先显示。
    @Query("select r from BorrowRecord r " +
            "where r.extensionRequestedAt is not null " +
            "and (:extensionStatus is null or :extensionStatus = '' or r.extensionStatus = :extensionStatus) " +
            "and (:keyword is null or :keyword = '' " +
            "or r.readerCard like concat('%', :keyword, '%') " +
            "or r.readerName like concat('%', :keyword, '%') " +
            "or r.readerPhone like concat('%', :keyword, '%') " +
            "or r.bookIsbn like concat('%', :keyword, '%') " +
            "or r.bookTitle like concat('%', :keyword, '%') " +
            "or r.bookAuthor like concat('%', :keyword, '%') " +
            "or r.copyCode like concat('%', :keyword, '%') " +
            "or r.batchNo like concat('%', :keyword, '%')) " +
            "order by case when r.extensionStatus = 'pending' then 0 else 1 end asc, " +
            "r.extensionRequestedAt desc")
    Page<BorrowRecord> searchExtensionRequests(
            @Param("keyword") String keyword,
            @Param("extensionStatus") String extensionStatus,
            Pageable pageable
    );

    // 逾期历史分页查询：当前未还逾期和已归还但归还日期晚于应还日期的记录都保留。
    @Query("select r from BorrowRecord r " +
            "where r.dueDate < :today " +
            "and (r.status = 'borrowed' or (r.status = 'returned' and r.returnDate is not null and r.returnDate > r.dueDate)) " +
            "and (:keyword is null or :keyword = '' " +
            "or r.readerCard like concat('%', :keyword, '%') " +
            "or r.readerName like concat('%', :keyword, '%') " +
            "or r.readerPhone like concat('%', :keyword, '%') " +
            "or r.bookIsbn like concat('%', :keyword, '%') " +
            "or r.bookTitle like concat('%', :keyword, '%') " +
            "or r.bookAuthor like concat('%', :keyword, '%') " +
            "or r.copyCode like concat('%', :keyword, '%') " +
            "or r.batchNo like concat('%', :keyword, '%')) " +
            "order by r.dueDate asc, r.id desc")
    Page<BorrowRecord> searchOverdueHistory(
            @Param("today") LocalDate today,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    // 管理端预警分页查询。
    @Query("select r from BorrowRecord r " +
            "where r.status = 'borrowed' " +
            "and r.dueDate is not null " +
            "and r.dueDate <= :warningEnd " +
            "and (:keyword is null or :keyword = '' " +
            "or r.readerCard like concat('%', :keyword, '%') " +
            "or r.readerName like concat('%', :keyword, '%') " +
            "or r.readerPhone like concat('%', :keyword, '%') " +
            "or r.bookIsbn like concat('%', :keyword, '%') " +
            "or r.bookTitle like concat('%', :keyword, '%') " +
            "or r.bookAuthor like concat('%', :keyword, '%') " +
            "or r.copyCode like concat('%', :keyword, '%') " +
            "or r.batchNo like concat('%', :keyword, '%')) " +
            "order by r.dueDate asc, r.id desc")
    Page<BorrowRecord> searchWarnings(
            @Param("warningEnd") LocalDate warningEnd,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    // 读者端预警分页查询，限定本人未还记录并支持图书、ISBN、单册编号和批次号搜索。
    @Query("select r from BorrowRecord r " +
            "where r.userId = :userId " +
            "and r.status = 'borrowed' " +
            "and r.dueDate is not null " +
            "and r.dueDate <= :warningEnd " +
            "and (:keyword is null or :keyword = '' " +
            "or r.bookIsbn like concat('%', :keyword, '%') " +
            "or r.bookTitle like concat('%', :keyword, '%') " +
            "or r.bookAuthor like concat('%', :keyword, '%') " +
            "or r.copyCode like concat('%', :keyword, '%') " +
            "or r.batchNo like concat('%', :keyword, '%')) " +
            "order by r.dueDate asc, r.id desc")
    Page<BorrowRecord> searchReaderWarnings(
            @Param("userId") Long userId,
            @Param("warningEnd") LocalDate warningEnd,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    // 读者端预警分页查询。
    Page<BorrowRecord> findByUserIdAndStatusAndDueDateLessThanEqualOrderByDueDateAscIdDesc(
            Long userId,
            String status,
            LocalDate dueDate,
            Pageable pageable
    );

    long countByBorrowDate(LocalDate borrowDate);

    long countByReturnDate(LocalDate returnDate);
}
