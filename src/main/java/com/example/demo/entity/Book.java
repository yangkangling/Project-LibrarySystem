package com.example.demo.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Transient;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 图书主表实体。
@Entity
@Table(name = "books", indexes = {
        @Index(name = "idx_books_category_id", columnList = "category_id"),
        @Index(name = "idx_books_status_id", columnList = "status,id"),
        @Index(name = "idx_books_shelf_location", columnList = "shelf_location")
})
public class Book {
    // 图书主键 id。
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 书名。
    private String title;
    // 作者。
    private String author;
    // ISBN 编号。
    @Column(nullable = false, unique = true)
    private String isbn;
    // 出版社，目前业务里暂时置空保留。
    private String publisher;
    // 分类名称，冗余保存用于页面展示和兼容旧数据。
    private String category;
    // 图书状态：enabled 表示启用，disabled 表示停用。
    private String status;

    // 分类 id，关联 categories 表。
    @Column(name = "category_id")
    private Long categoryId;

    // 分类实体信息，只读关联，用于需要分类详情时读取。
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id", insertable = false, updatable = false)
    private Category categoryInfo;

    // 出版日期，目前业务里暂时置空保留。
    @Column(name = "publish_date")
    private LocalDate publishDate;

    // 主书架位置。
    @Column(name = "shelf_location")
    private String shelfLocation;

    // 馆藏总册数。
    @Column(name = "total_count")
    private Integer totalCount;

    // 当前可借册数。
    @Column(name = "available_count")
    private Integer availableCount;

    // 创建时间。
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // 当前仍在读者手里的册数，仅用于接口展示，不落库。
    @Transient
    private Long activeBorrowCount;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public String getPublisher() {
        return publisher;
    }

    public void setPublisher(String publisher) {
        this.publisher = publisher;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public Category getCategoryInfo() {
        return categoryInfo;
    }

    public void setCategoryInfo(Category categoryInfo) {
        this.categoryInfo = categoryInfo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDate getPublishDate() {
        return publishDate;
    }

    public void setPublishDate(LocalDate publishDate) {
        this.publishDate = publishDate;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Long getActiveBorrowCount() {
        return activeBorrowCount;
    }

    public void setActiveBorrowCount(Long activeBorrowCount) {
        this.activeBorrowCount = activeBorrowCount;
    }
}
