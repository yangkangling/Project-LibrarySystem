package com.example.demo;

import com.example.demo.entity.Category;
import com.example.demo.entity.Book;
import com.example.demo.entity.BorrowRecord;
import com.example.demo.entity.User;
import com.example.demo.repository.BookRepository;
import com.example.demo.repository.BorrowRecordRepository;
import com.example.demo.repository.CategoryRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class DataInitializer implements CommandLineRunner {
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final BookRepository bookRepository;
    private final BorrowRecordRepository borrowRecordRepository;

    public DataInitializer(
            UserRepository userRepository,
            CategoryRepository categoryRepository,
            BookRepository bookRepository,
            BorrowRecordRepository borrowRecordRepository
    ) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.bookRepository = bookRepository;
        this.borrowRecordRepository = borrowRecordRepository;
    }

    @Override
    public void run(String... args) {
        if (userRepository.findByUsername("admin").isEmpty()) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword("123456");
            admin.setRealName("Administrator");
            admin.setRole("admin");
            admin.setStatus("enabled");
            admin.setCreatedAt(LocalDateTime.now());
            userRepository.save(admin);
        }

        if (categoryRepository.count() == 0) {
            Category category = new Category();
            category.setName("计算机");
            category.setDescription("计算机与软件开发类图书");
            category.setCreatedAt(LocalDateTime.now());
            categoryRepository.save(category);
        }

        borrowRecordRepository.findAll().forEach(this::fillBorrowRecordSnapshot);
    }

    private void fillBorrowRecordSnapshot(BorrowRecord record) {
        boolean changed = false;

        if (!hasText(record.getReaderCard()) || !hasText(record.getReaderName()) || !hasText(record.getReaderPhone())) {
            User user = userRepository.findById(record.getUserId()).orElse(null);
            if (user != null) {
                record.setReaderCard(user.getUsername());
                record.setReaderName(user.getRealName());
                record.setReaderPhone(user.getPhone());
                changed = true;
            }
        }

        if (!hasText(record.getBookIsbn()) || !hasText(record.getBookTitle()) || !hasText(record.getBookAuthor())) {
            Book book = bookRepository.findById(record.getBookId()).orElse(null);
            if (book != null) {
                record.setBookIsbn(book.getIsbn());
                record.setBookTitle(book.getTitle());
                record.setBookAuthor(book.getAuthor());
                changed = true;
            }
        }

        if (changed) {
            borrowRecordRepository.save(record);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
