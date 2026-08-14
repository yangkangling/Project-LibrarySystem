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
import java.time.Year;
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
                return userRepository.findByRole("reader").stream()
                        .map(this::toReaderRow)
                        .toList();
            }

            String value = keyword.trim();
            return userRepository.findByRoleAndUsernameContainingOrRoleAndRealNameContainingOrRoleAndPhoneContaining(
                    "reader", value,
                    "reader", value,
                    "reader", value
            ).stream()
                    .map(this::toReaderRow)
                    .toList();
        }

        return userRepository.searchReaders(normalize(keyword), normalize(status), PageRequest.of(page, size))
                .map(this::toReaderRow);
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        User user = findReader(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reader", user);
        result.put("currentBorrowCount", borrowRecordRepository.countByUserIdAndStatus(id, "borrowed"));
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
        validatePassword(user.getPassword(), true);
        if (userRepository.existsByPhoneAndRole(user.getPhone().trim(), "reader")) {
            throw new RuntimeException("该手机号已办理读者账号，请直接登录或联系管理员");
        }

        user.setUsername(nextReaderCard());
        user.setRealName(user.getRealName() == null || user.getRealName().trim().isEmpty()
                ? maskPhone(user.getPhone().trim())
                : user.getRealName().trim());
        user.setPhone(user.getPhone().trim());
        user.setRemark(user.getRemark());
        user.setPassword(user.getPassword().trim());
        user.setRole("reader");
        user.setStatus("enabled");
        user.setCreatedAt(LocalDateTime.now());

        return userRepository.save(user);
    }

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody RegisterRequest request) {
        if (request.password() == null || !request.password().equals(request.confirmPassword())) {
            throw new RuntimeException("两次输入的密码不一致");
        }

        User user = new User();
        user.setPhone(request.phone());
        user.setPassword(request.password());
        user.setRealName(maskPhone(request.phone()));
        User saved = add(user);
        return Map.of(
                "message", "注册成功",
                "readerCard", saved.getUsername(),
                "realName", saved.getRealName()
        );
    }

    @PutMapping("/{id}")
    public User update(@PathVariable Long id, @RequestBody User input) {
        User user = findReader(id);
        validateReader(input);
        userRepository.findByPhoneAndRole(input.getPhone().trim(), "reader").ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new RuntimeException("该手机号已办理其他读者账号，请更换手机号");
            }
        });

        user.setRealName(input.getRealName() == null || input.getRealName().trim().isEmpty()
                ? user.getRealName()
                : input.getRealName().trim());
        user.setPhone(input.getPhone().trim());
        user.setRemark(input.getRemark());
        user.setStatus(input.getStatus() == null ? "enabled" : input.getStatus());
        if (input.getPassword() != null && !input.getPassword().trim().isEmpty()) {
            validatePassword(input.getPassword(), false);
            user.setPassword(input.getPassword().trim());
        }

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
        if (user.getPhone() == null || user.getPhone().trim().isEmpty()) {
            throw new RuntimeException("手机号不能为空");
        }
        if (!user.getPhone().trim().matches("^1\\d{10}$")) {
            throw new RuntimeException("手机号格式不正确，请输入 11 位手机号");
        }
    }

    private void validatePassword(String password, boolean required) {
        if (password == null || password.trim().isEmpty()) {
            if (required) {
                throw new RuntimeException("读者密码不能为空");
            }
            return;
        }
        if (password.trim().length() < 6) {
            throw new RuntimeException("读者密码至少 6 位");
        }
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private synchronized String nextReaderCard() {
        String prefix = "R" + Year.now().getValue();
        List<User> readers = userRepository.findByRoleAndUsernameStartingWithOrderByUsernameDesc("reader", prefix);
        int nextNumber = 1;
        if (!readers.isEmpty()) {
            String latest = readers.get(0).getUsername();
            if (latest != null && latest.matches("^" + prefix + "\\d{4}$")) {
                nextNumber = Integer.parseInt(latest.substring(prefix.length())) + 1;
            }
        }

        String card;
        do {
            card = prefix + String.format("%04d", nextNumber++);
        } while (userRepository.existsByUsername(card));
        return card;
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.trim().length() < 7) {
            return "读者";
        }
        String value = phone.trim();
        return "读者" + value.substring(value.length() - 4);
    }

    private Map<String, Object> toReaderRow(User reader) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", reader.getId());
        item.put("username", reader.getUsername());
        item.put("realName", reader.getRealName());
        item.put("phone", reader.getPhone());
        item.put("remark", reader.getRemark());
        item.put("status", reader.getStatus());
        item.put("createdAt", reader.getCreatedAt());
        item.put("currentBorrowCount", borrowRecordRepository.countByUserIdAndStatus(reader.getId(), "borrowed"));
        return item;
    }

    public record RegisterRequest(String phone, String password, String confirmPassword) {
    }
}
