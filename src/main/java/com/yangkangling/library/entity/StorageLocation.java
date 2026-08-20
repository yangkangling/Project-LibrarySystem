package com.yangkangling.library.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;

import java.time.LocalDateTime;

// 书架库存位置实体。
@Entity
@Table(name = "storage_locations", indexes = {
        @Index(name = "idx_storage_book_shelf", columnList = "book_id,shelf_location"),
        @Index(name = "idx_storage_shelf", columnList = "shelf_location"),
        @Index(name = "idx_storage_book_available", columnList = "book_id,available_count")
})
public class StorageLocation {
    // 书架库存记录主键 id。
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 所属图书 id。
    @Column(name = "book_id")
    private Long bookId;

    // 书架位置。
    @Column(name = "shelf_location")
    private String shelfLocation;

    // 该书架馆藏总数。
    @Column(name = "total_count")
    private Integer totalCount;

    // 该书架当前可借数量。
    @Column(name = "available_count")
    private Integer availableCount;

    // 备注。
    private String remark;

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

    public String getShelfLocation() {
        return shelfLocation;
    }

    public void setShelfLocation(String shelfLocation) {
        this.shelfLocation = shelfLocation;
    }

    public Integer getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Integer totalCount) {
        this.totalCount = totalCount;
    }

    public Integer getAvailableCount() {
        return availableCount;
    }

    public void setAvailableCount(Integer availableCount) {
        this.availableCount = availableCount;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
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
