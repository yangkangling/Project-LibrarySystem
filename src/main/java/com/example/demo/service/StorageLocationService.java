package com.example.demo.service;

import com.example.demo.entity.Book;
import com.example.demo.entity.BorrowRecord;
import com.example.demo.entity.StorageLocation;
import com.example.demo.repository.StorageLocationRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class StorageLocationService {
    private final StorageLocationRepository storageLocationRepository;

    public StorageLocationService(StorageLocationRepository storageLocationRepository) {
        this.storageLocationRepository = storageLocationRepository;
    }

    public StorageLocation syncPrimaryStorage(Book book) {
        if (book == null || book.getId() == null) {
            throw new RuntimeException("图书不存在，无法维护书架存储");
        }
        StorageLocation storageLocation = storageLocationRepository.findFirstByBookIdOrderByIdAsc(book.getId())
                .orElseGet(() -> newStorageLocation(book));

        storageLocation.setShelfLocation(book.getShelfLocation());
        storageLocation.setTotalCount(safeInt(book.getTotalCount()));
        storageLocation.setAvailableCount(safeInt(book.getAvailableCount()));
        storageLocation.setUpdatedAt(LocalDateTime.now());
        return storageLocationRepository.save(storageLocation);
    }

    public List<StorageLocation> findByBookId(Long bookId) {
        return storageLocationRepository.findByBookIdOrderByIdAsc(bookId);
    }

    public void deleteByBookId(Long bookId) {
        storageLocationRepository.deleteByBookId(bookId);
    }

    public StorageLocation borrowAvailableStorage(Book book) {
        StorageLocation storageLocation = storageLocationRepository
                .findFirstByBookIdAndAvailableCountGreaterThanOrderByIdAsc(book.getId(), 0)
                .orElseGet(() -> syncPrimaryStorage(book));

        int updatedRows = storageLocationRepository.decreaseAvailableCountWhenAvailable(storageLocation.getId(), LocalDateTime.now());
        if (updatedRows == 0) {
            throw new RuntimeException("《" + book.getTitle() + "》书架存储可借数量不足，请刷新后重试");
        }
        storageLocation.setAvailableCount(safeInt(storageLocation.getAvailableCount()) - 1);
        return storageLocation;
    }

    public StorageLocation returnStorage(BorrowRecord record, Book book) {
        StorageLocation storageLocation = findStorageForRecord(book, record)
                .orElseGet(() -> syncPrimaryStorage(book));

        int updatedRows = storageLocationRepository.increaseAvailableCountWithinTotal(storageLocation.getId(), LocalDateTime.now());
        if (updatedRows == 0) {
            throw new RuntimeException("书架存储可借数量不能大于馆藏数量，请检查库存数据");
        }
        storageLocation.setAvailableCount(safeInt(storageLocation.getAvailableCount()) + 1);
        return storageLocation;
    }

    public boolean attachExistingBorrowRecord(Book book, BorrowRecord record) {
        if (book == null || record == null) {
            return false;
        }
        StorageLocation storageLocation = findStorageForRecord(book, record)
                .orElseGet(() -> syncPrimaryStorage(book));

        boolean changed = false;
        if (record.getStorageLocationId() == null || !record.getStorageLocationId().equals(storageLocation.getId())) {
            record.setStorageLocationId(storageLocation.getId());
            changed = true;
        }
        if (!hasText(record.getShelfLocationSnapshot())) {
            record.setShelfLocationSnapshot(firstText(storageLocation.getShelfLocation(), book.getShelfLocation()));
            changed = true;
        }
        return changed;
    }

    private Optional<StorageLocation> findStorageForRecord(Book book, BorrowRecord record) {
        if (record.getStorageLocationId() != null) {
            Optional<StorageLocation> storageLocation = storageLocationRepository.findById(record.getStorageLocationId());
            if (storageLocation.isPresent()) {
                return storageLocation;
            }
        }
        if (hasText(record.getShelfLocationSnapshot())) {
            Optional<StorageLocation> storageLocation = storageLocationRepository
                    .findFirstByBookIdAndShelfLocationOrderByIdAsc(book.getId(), record.getShelfLocationSnapshot());
            if (storageLocation.isPresent()) {
                return storageLocation;
            }
        }
        if (hasText(record.getCopyShelfLocation())) {
            Optional<StorageLocation> storageLocation = storageLocationRepository
                    .findFirstByBookIdAndShelfLocationOrderByIdAsc(book.getId(), record.getCopyShelfLocation());
            if (storageLocation.isPresent()) {
                return storageLocation;
            }
        }
        return storageLocationRepository.findFirstByBookIdOrderByIdAsc(book.getId());
    }

    private StorageLocation newStorageLocation(Book book) {
        LocalDateTime now = LocalDateTime.now();
        StorageLocation storageLocation = new StorageLocation();
        storageLocation.setBookId(book.getId());
        storageLocation.setShelfLocation(book.getShelfLocation());
        storageLocation.setTotalCount(safeInt(book.getTotalCount()));
        storageLocation.setAvailableCount(safeInt(book.getAvailableCount()));
        storageLocation.setRemark("默认书架存储");
        storageLocation.setCreatedAt(now);
        storageLocation.setUpdatedAt(now);
        return storageLocation;
    }

    private String firstText(String first, String second) {
        return hasText(first) ? first : second;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
