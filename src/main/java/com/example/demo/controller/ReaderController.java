package com.example.demo.controller;

import com.example.demo.entity.User;
import com.example.demo.repository.BorrowRecordRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.BorrowRecordViewService;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/readers")
public class ReaderController {
    private final UserRepository userRepository;
    private final BorrowRecordRepository borrowRecordRepository;
    private final BorrowRecordViewService borrowRecordViewService;

    public ReaderController(
            UserRepository userRepository,
            BorrowRecordRepository borrowRecordRepository,
            BorrowRecordViewService borrowRecordViewService
    ) {
        this.userRepository = userRepository;
        this.borrowRecordRepository = borrowRecordRepository;
        this.borrowRecordViewService = borrowRecordViewService;
    }

    @GetMapping
    public Object list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(defaultValue = "10") Integer size
    ) {
        if (page == null) {
            if (keyword == null || keyword.trim().isEmpty()) {
                return userRepository.findByRole("reader");
            }

            String value = keyword.trim();
            return userRepository.findByRoleAndUsernameContainingOrRoleAndRealNameContainingOrRoleAndPhoneContaining(
                    "reader", value,
                    "reader", value,
                    "reader", value
            );
        }

        return userRepository.searchReaders(normalize(keyword), normalize(status), PageRequest.of(page, size));
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        User user = findReader(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reader", user);
        result.put("currentBorrowRecords", borrowRecordRepository.findByStatus("borrowed").stream()
                .filter(record -> id.equals(record.getUserId()))
                .map(borrowRecordViewService::toView)
                .toList());
        result.put("historyRecords", borrowRecordRepository.findByUserIdOrderByIdDesc(id).stream()
                .map(borrowRecordViewService::toView)
                .toList());
        return result;
    }

    @PostMapping
    public User add(@RequestBody User user) {
        validateReader(user);
        if (userRepository.existsByUsername(user.getUsername().trim())) {
            throw new RuntimeException("借阅证号已存在，请更换后再保存");
        }

        user.setUsername(user.getUsername().trim());
        user.setRealName(user.getRealName().trim());
        user.setPhone(user.getPhone().trim());
        user.setRemark(user.getRemark());
        user.setPassword("123456");
        user.setRole("reader");
        user.setStatus("enabled");
        user.setCreatedAt(LocalDateTime.now());

        return userRepository.save(user);
    }

    @PutMapping("/{id}")
    public User update(@PathVariable Long id, @RequestBody User input) {
        User user = findReader(id);
        validateReader(input);

        userRepository.findByUsername(input.getUsername().trim()).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new RuntimeException("借阅证号已存在，请更换后再保存");
            }
        });

        user.setUsername(input.getUsername().trim());
        user.setRealName(input.getRealName().trim());
        user.setPhone(input.getPhone().trim());
        user.setRemark(input.getRemark());
        user.setStatus(input.getStatus() == null ? "enabled" : input.getStatus());

        return userRepository.save(user);
    }

    @PutMapping("/{id}/disable")
    public User disable(@PathVariable Long id) {
        User user = findReader(id);
        user.setStatus("disabled");
        return userRepository.save(user);
    }

    @PutMapping("/{id}/enable")
    public User enable(@PathVariable Long id) {
        User user = findReader(id);
        user.setStatus("enabled");
        return userRepository.save(user);
    }

    private User findReader(Long id) {
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("读者不存在"));
        if (!"reader".equals(user.getRole())) {
            throw new RuntimeException("读者不存在");
        }
        return user;
    }

    private void validateReader(User user) {
        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            throw new RuntimeException("借阅证号不能为空");
        }
        if (user.getRealName() == null || user.getRealName().trim().isEmpty()) {
            throw new RuntimeException("读者姓名不能为空");
        }
        if (user.getPhone() == null || user.getPhone().trim().isEmpty()) {
            throw new RuntimeException("手机号不能为空");
        }
        if (!user.getPhone().trim().matches("^1\\d{10}$")) {
            throw new RuntimeException("手机号格式不正确，请输入 11 位手机号");
        }
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
