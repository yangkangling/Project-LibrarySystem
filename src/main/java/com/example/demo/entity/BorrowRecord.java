package com.example.demo.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// 借阅记录实体。
@Entity
@Table(name = "borrow_records", indexes = {
        @Index(name = "idx_borrow_records_user_status_id", columnList = "user_id,status,id"),
        @Index(name = "idx_borrow_records_book_status_id", columnList = "book_id,status,id"),
        @Index(name = "idx_borrow_records_status_due", columnList = "status,due_date,id"),
        @Index(name = "idx_borrow_records_borrow_date", columnList = "borrow_date"),
        @Index(name = "idx_borrow_records_return_date", columnList = "return_date"),
        @Index(name = "idx_borrow_records_extension", columnList = "extension_status,extension_requested_at")
})
public class BorrowRecord {
    // 借阅记录主键 id。
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 读者 id。
    @Column(name = "user_id")
    private Long userId;

    // 图书 id。
    @Column(name = "book_id")
    private Long bookId;

    // 单册 id。
    @Column(name = "book_copy_id")
    private Long bookCopyId;

    // 书架库存记录 id。
    @Column(name = "storage_location_id")
    private Long storageLocationId;

    // 借阅时读者借阅证号快照。
    @Column(name = "reader_card")
    private String readerCard;

    // 借阅时读者姓名快照。
    @Column(name = "reader_name")
    private String readerName;

    // 借阅时读者手机号快照。
    @Column(name = "reader_phone")
    private String readerPhone;

    // 借阅时图书 ISBN 快照。
    @Column(name = "book_isbn")
    private String bookIsbn;

    // 借阅时书名快照。
    @Column(name = "book_title")
    private String bookTitle;

    // 借阅时作者快照。
    @Column(name = "book_author")
    private String bookAuthor;

    // 借阅时单册编号快照。
    @Column(name = "copy_code")
    private String copyCode;

    // 借阅时单册书架位置快照。
    @Column(name = "copy_shelf_location")
    private String copyShelfLocation;

    // 借阅时书架库存位置快照。
    @Column(name = "shelf_location_snapshot")
    private String shelfLocationSnapshot;

    // 借阅批次号，一次批量借书共享同一个批次号。
    @Column(name = "batch_no")
    private String batchNo;

    // 借阅日期。
    @Column(name = "borrow_date")
    private LocalDate borrowDate;

    // 应还日期。
    @Column(name = "due_date")
    private LocalDate dueDate;

    // 实际归还日期。
    @Column(name = "return_date")
    private LocalDate returnDate;

    // 原始借阅状态：borrowed 未还，returned 已还。
    private String status;

    // 罚款金额。
    @Column(name = "fine_amount", precision = 10, scale = 2)
    private BigDecimal fineAmount;

    // 罚款状态：unpaid 待缴纳，paid 已缴纳，waived 已免罚。
    @Column(name = "fine_status")
    private String fineStatus;

    // 罚款处理时间。
    @Column(name = "fine_handled_at")
    private LocalDateTime fineHandledAt;

    // 罚款处理备注。
    @Column(name = "fine_note")
    private String fineNote;

    // 续借申请状态：none/pending/approved。
    @Column(name = "extension_status")
    private String extensionStatus;

    // 本次申请续借天数。
    @Column(name = "extension_requested_days")
    private Integer extensionRequestedDays;

    // 本次申请后的应还日期。
    @Column(name = "extension_requested_due_date")
    private LocalDate extensionRequestedDueDate;

    // 续借申请提交时间。
    @Column(name = "extension_requested_at")
    private LocalDateTime extensionRequestedAt;

    // 管理员处理续借申请时间。
    @Column(name = "extension_handled_at")
    private LocalDateTime extensionHandledAt;

    // 记录创建时间。
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getBookId() {
        return bookId;
    }

    public void setBookId(Long bookId) {
        this.bookId = bookId;
    }

    public Long getBookCopyId() {
        return bookCopyId;
    }

    public void setBookCopyId(Long bookCopyId) {
        this.bookCopyId = bookCopyId;
    }

    public Long getStorageLocationId() {
        return storageLocationId;
    }

    public void setStorageLocationId(Long storageLocationId) {
        this.storageLocationId = storageLocationId;
    }

    public String getReaderCard() {
        return readerCard;
    }

    public void setReaderCard(String readerCard) {
        this.readerCard = readerCard;
    }

    public String getReaderName() {
        return readerName;
    }

    public void setReaderName(String readerName) {
        this.readerName = readerName;
    }

    public String getReaderPhone() {
        return readerPhone;
    }

    public void setReaderPhone(String readerPhone) {
        this.readerPhone = readerPhone;
    }

    public String getBookIsbn() {
        return bookIsbn;
    }

    public void setBookIsbn(String bookIsbn) {
        this.bookIsbn = bookIsbn;
    }

    public String getBookTitle() {
        return bookTitle;
    }

    public void setBookTitle(String bookTitle) {
        this.bookTitle = bookTitle;
    }

    public String getBookAuthor() {
        return bookAuthor;
    }

    public void setBookAuthor(String bookAuthor) {
        this.bookAuthor = bookAuthor;
    }

    public String getCopyCode() {
        return copyCode;
    }

    public void setCopyCode(String copyCode) {
        this.copyCode = copyCode;
    }

    public String getCopyShelfLocation() {
        return copyShelfLocation;
    }

    public void setCopyShelfLocation(String copyShelfLocation) {
        this.copyShelfLocation = copyShelfLocation;
    }

    public String getShelfLocationSnapshot() {
        return shelfLocationSnapshot;
    }

    public void setShelfLocationSnapshot(String shelfLocationSnapshot) {
        this.shelfLocationSnapshot = shelfLocationSnapshot;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public LocalDate getBorrowDate() {
        return borrowDate;
    }

    public void setBorrowDate(LocalDate borrowDate) {
        this.borrowDate = borrowDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public LocalDate getReturnDate() {
        return returnDate;
    }

    public void setReturnDate(LocalDate returnDate) {
        this.returnDate = returnDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getFineAmount() {
        return fineAmount;
    }

    public void setFineAmount(BigDecimal fineAmount) {
        this.fineAmount = fineAmount;
    }

    public String getFineStatus() {
        return fineStatus;
    }

    public void setFineStatus(String fineStatus) {
        this.fineStatus = fineStatus;
    }

    public LocalDateTime getFineHandledAt() {
        return fineHandledAt;
    }

    public void setFineHandledAt(LocalDateTime fineHandledAt) {
        this.fineHandledAt = fineHandledAt;
    }

    public String getFineNote() {
        return fineNote;
    }

    public void setFineNote(String fineNote) {
        this.fineNote = fineNote;
    }

    public String getExtensionStatus() {
        return extensionStatus;
    }

    public void setExtensionStatus(String extensionStatus) {
        this.extensionStatus = extensionStatus;
    }

    public Integer getExtensionRequestedDays() {
        return extensionRequestedDays;
    }

    public void setExtensionRequestedDays(Integer extensionRequestedDays) {
        this.extensionRequestedDays = extensionRequestedDays;
    }

    public LocalDate getExtensionRequestedDueDate() {
        return extensionRequestedDueDate;
    }

    public void setExtensionRequestedDueDate(LocalDate extensionRequestedDueDate) {
        this.extensionRequestedDueDate = extensionRequestedDueDate;
    }

    public LocalDateTime getExtensionRequestedAt() {
        return extensionRequestedAt;
    }

    public void setExtensionRequestedAt(LocalDateTime extensionRequestedAt) {
        this.extensionRequestedAt = extensionRequestedAt;
    }

    public LocalDateTime getExtensionHandledAt() {
        return extensionHandledAt;
    }

    public void setExtensionHandledAt(LocalDateTime extensionHandledAt) {
        this.extensionHandledAt = extensionHandledAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
