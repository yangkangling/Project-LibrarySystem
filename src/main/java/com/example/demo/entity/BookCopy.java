package com.example.demo.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import java.time.LocalDateTime;

// 单册馆藏实体。
@Entity
@Table(name = "book_copies", uniqueConstraints = {
        @UniqueConstraint(name = "uk_book_copy_code", columnNames = "copy_code")
}, indexes = {
        @Index(name = "idx_book_copies_book_status", columnList = "book_id,status,copy_code"),
        @Index(name = "idx_book_copies_book_status_shelf", columnList = "book_id,status,shelf_location,copy_code"),
        @Index(name = "idx_book_copies_borrow_record", columnList = "current_borrow_record_id"),
        @Index(name = "idx_book_copies_shelf", columnList = "shelf_location")
})
public class BookCopy {
    // 单册主键 id。
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 所属图书 id。
    @Column(name = "book_id")
    private Long bookId;

    // 单册编号，全馆唯一。
    @Column(name = "copy_code", nullable = false)
    private String copyCode;

    // 单册所在书架。
    @Column(name = "shelf_location")
    private String shelfLocation;

    // 单册状态：available 可借，borrowed 借出，disabled 停用。
    private String status;

    // 当前借阅读者 id，未借出时为空。
    @Column(name = "current_user_id")
    private Long currentUserId;

    // 当前借阅记录 id，未借出时为空。
    @Column(name = "current_borrow_record_id")
    private Long currentBorrowRecordId;

    // 创建时间。
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // 更新时间。
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getBookId() {
        return bookId;
    }

    public void setBookId(Long bookId) {
        this.bookId = bookId;
    }

    public String getCopyCode() {
        return copyCode;
    }

    public void setCopyCode(String copyCode) {
        this.copyCode = copyCode;
    }

    public String getShelfLocation() {
        return shelfLocation;
    }

    public void setShelfLocation(String shelfLocation) {
        this.shelfLocation = shelfLocation;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getCurrentUserId() {
        return currentUserId;
    }

    public void setCurrentUserId(Long currentUserId) {
        this.currentUserId = currentUserId;
    }

    public Long getCurrentBorrowRecordId() {
        return currentBorrowRecordId;
    }

    public void setCurrentBorrowRecordId(Long currentBorrowRecordId) {
        this.currentBorrowRecordId = currentBorrowRecordId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
