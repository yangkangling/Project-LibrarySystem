package com.example.demo.controller;

import com.example.demo.entity.User;
import com.example.demo.config.LibraryProperties;
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

// 读者注册和管理接口。
@RestController
@RequestMapping("/readers")
public class ReaderController {
    // 用户仓库，读者账号也存放在用户表中。
    private final UserRepository userRepository;
    // 借阅记录仓库，用于统计和查询读者借阅记录。
    private final BorrowRecordRepository borrowRecordRepository;
    // 借阅记录视图服务，用于把借阅实体转为前端行数据。
    private final BorrowRecordViewService borrowRecordViewService;
    // 系统容量配置，用于限制分页大小。
    private final LibraryProperties libraryProperties;

    // 构造方法注入读者管理需要的仓库和服务。
    public ReaderController(
            UserRepository userRepository,
            BorrowRecordRepository borrowRecordRepository,
            BorrowRecordViewService borrowRecordViewService,
            LibraryProperties libraryProperties
    ) {
        this.userRepository = userRepository;
        this.borrowRecordRepository = borrowRecordRepository;
        this.borrowRecordViewService = borrowRecordViewService;
        this.libraryProperties = libraryProperties;
    }

    // 查询读者列表，支持关键字、状态和分页。
    @GetMapping
    public Object list(
            // 借阅证号、姓名、手机号关键字。
            @RequestParam(required = false) String keyword,
            // 账号状态 enabled/disabled。
            @RequestParam(required = false) String status,
            // 页码；为空时兼容旧接口返回全部。
            @RequestParam(required = false) Integer page,
            // 每页数量。
            @RequestParam(defaultValue = "10") Integer size
    ) {
        // 不传 page 时走旧逻辑，返回列表数组。
        if (page == null) {
            // 没有关键字时返回全部读者。
            if (keyword == null || keyword.trim().isEmpty()) {
                return userRepository.findByRole("reader").stream()
                        .map(this::toReaderRow)
                        .collect(java.util.stream.Collectors.toList());
            }

            // 有关键字时按借阅证号、姓名、手机号模糊搜索。
            String value = keyword.trim();
            return userRepository.findByRoleAndUsernameContainingOrRoleAndRealNameContainingOrRoleAndPhoneContaining(
                    "reader", value,
                    "reader", value,
                    "reader", value
            ).stream()
                    .map(this::toReaderRow)
                    .collect(java.util.stream.Collectors.toList());
        }

        // 新分页接口：按关键字和状态查询读者。
        return userRepository.searchReaders(normalize(keyword), normalize(status), PageRequest.of(Math.max(0, page), libraryProperties.normalizePageSize(size)))
                .map(this::toReaderRow);
    }

    // 查询读者详情，包括当前未还和历史借阅记录。
    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        // 先确认读者存在。
        User user = findReader(id);
        // 使用有序 Map 返回详情。
        Map<String, Object> result = new LinkedHashMap<>();
        // 读者基本信息。
        result.put("reader", user);
        // 当前未还数量。
        result.put("currentBorrowCount", borrowRecordRepository.countByUserIdAndStatus(id, "borrowed"));
        // 当前未还记录列表。
        result.put("currentBorrowRecords", borrowRecordRepository.findByUserIdAndStatusOrderByIdDesc(id, "borrowed").stream()
                .map(borrowRecordViewService::toView)
                .collect(java.util.stream.Collectors.toList()));
        // 历史借阅记录列表。
        result.put("historyRecords", borrowRecordRepository.findByUserIdOrderByIdDesc(id).stream()
                .map(borrowRecordViewService::toView)
                .collect(java.util.stream.Collectors.toList()));
        return result;
    }

    // 管理员新增读者。
    @PostMapping
    public User add(@RequestBody User user) {
        // 校验手机号。
        validateReader(user);
        // 新增读者必须填写密码。
        validatePassword(user.getPassword(), true);
        // 一个手机号只能办理一个读者账号。
        if (userRepository.existsByPhoneAndRole(user.getPhone().trim(), "reader")) {
            throw new RuntimeException("该手机号已办理读者账号，请直接登录或联系管理员");
        }

        // 自动生成借阅证号。
        user.setUsername(nextReaderCard());
        // 姓名为空时用手机号后四位生成默认名称。
        user.setRealName(user.getRealName() == null || user.getRealName().trim().isEmpty()
                ? maskPhone(user.getPhone().trim())
                : user.getRealName().trim());
        // 保存规范化手机号。
        user.setPhone(user.getPhone().trim());
        // 保存备注。
        user.setRemark(user.getRemark());
        // 保存密码。
        user.setPassword(user.getPassword().trim());
        // 固定为读者角色。
        user.setRole("reader");
        // 新增读者默认启用。
        user.setStatus("enabled");
        // 记录创建时间。
        user.setCreatedAt(LocalDateTime.now());

        // 写入数据库。
        return userRepository.save(user);
    }

    // 读者自助注册入口。
    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody RegisterRequest request) {
        // 两次密码必须一致。
        if (request.password() == null || !request.password().equals(request.confirmPassword())) {
            throw new RuntimeException("两次输入的密码不一致");
        }

        // 构造读者账号实体。
        User user = new User();
        // 自助注册只填写手机号。
        user.setPhone(request.phone());
        // 使用注册密码。
        user.setPassword(request.password());
        // 默认姓名使用手机号后四位。
        user.setRealName(maskPhone(request.phone()));
        // 复用管理员新增读者逻辑。
        User saved = add(user);
        // 返回新借阅证号，读者需要用它登录。
        return Map.of(
                "message", "注册成功",
                "readerCard", saved.getUsername(),
                "realName", saved.getRealName()
        );
    }

    // 管理员编辑读者。
    @PutMapping("/{id}")
    public User update(@PathVariable Long id, @RequestBody User input) {
        // 查出原读者。
        User user = findReader(id);
        // 校验手机号。
        validateReader(input);
        // 手机号不能和其他读者重复。
        userRepository.findByPhoneAndRole(input.getPhone().trim(), "reader").ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new RuntimeException("该手机号已办理其他读者账号，请更换手机号");
            }
        });

        // 姓名为空时保留原姓名。
        user.setRealName(input.getRealName() == null || input.getRealName().trim().isEmpty()
                ? user.getRealName()
                : input.getRealName().trim());
        // 更新手机号。
        user.setPhone(input.getPhone().trim());
        // 更新备注。
        user.setRemark(input.getRemark());
        // 更新状态，空值默认启用。
        user.setStatus(input.getStatus() == null ? "enabled" : input.getStatus());
        // 如果填写了新密码，就重置密码。
        if (input.getPassword() != null && !input.getPassword().trim().isEmpty()) {
            validatePassword(input.getPassword(), false);
            user.setPassword(input.getPassword().trim());
        }

        // 保存修改结果。
        return userRepository.save(user);
    }

    // 停用或冻结读者账号。
    @PutMapping("/{id}/disable")
    public User disable(@PathVariable Long id) {
        // 先确认读者存在。
        User user = findReader(id);
        // disabled 表示不能登录自助端，也不能继续借书。
        user.setStatus("disabled");
        return userRepository.save(user);
    }

    // 启用读者账号。
    @PutMapping("/{id}/enable")
    public User enable(@PathVariable Long id) {
        // 先确认读者存在。
        User user = findReader(id);
        // enabled 表示账号可正常使用。
        user.setStatus("enabled");
        return userRepository.save(user);
    }

    // 按 id 查询读者，并确认角色确实是 reader。
    private User findReader(Long id) {
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("读者不存在"));
        if (!"reader".equals(user.getRole())) {
            throw new RuntimeException("读者不存在");
        }
        return user;
    }

    // 校验读者手机号。
    private void validateReader(User user) {
        // 手机号不能为空。
        if (user.getPhone() == null || user.getPhone().trim().isEmpty()) {
            throw new RuntimeException("手机号不能为空");
        }
        // 手机号必须是 1 开头的 11 位数字。
        if (!user.getPhone().trim().matches("^1\\d{10}$")) {
            throw new RuntimeException("手机号格式不正确，请输入 11 位手机号");
        }
    }

    // 校验读者密码。
    private void validatePassword(String password, boolean required) {
        // 新增时密码必填；编辑时空密码表示不修改。
        if (password == null || password.trim().isEmpty()) {
            if (required) {
                throw new RuntimeException("读者密码不能为空");
            }
            return;
        }
        // 密码至少 6 位。
        if (password.trim().length() < 6) {
            throw new RuntimeException("读者密码至少 6 位");
        }
    }

    // 去掉查询参数前后空格。
    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    // 按年份生成下一个借阅证号，如 R20260001。
    private synchronized String nextReaderCard() {
        // 借阅证号前缀包含当前年份。
        String prefix = "R" + Year.now().getValue();
        // 查询当前年份最大借阅证号。
        List<User> readers = userRepository.findByRoleAndUsernameStartingWithOrderByUsernameDesc("reader", prefix);
        // 默认从 0001 开始。
        int nextNumber = 1;
        // 有历史编号时在最大编号基础上加 1。
        if (!readers.isEmpty()) {
            String latest = readers.get(0).getUsername();
            if (latest != null && latest.matches("^" + prefix + "\\d{4}$")) {
                nextNumber = Integer.parseInt(latest.substring(prefix.length())) + 1;
            }
        }

        // 循环生成直到找到一个未占用的借阅证号。
        String card;
        do {
            card = prefix + String.format("%04d", nextNumber++);
        } while (userRepository.existsByUsername(card));
        return card;
    }

    // 根据手机号生成默认读者姓名。
    private String maskPhone(String phone) {
        // 手机号异常时使用通用名称。
        if (phone == null || phone.trim().length() < 7) {
            return "读者";
        }
        // 正常情况下使用“读者 + 手机号后四位”。
        String value = phone.trim();
        return "读者" + value.substring(value.length() - 4);
    }

    // 把读者实体转换成前端列表行。
    private Map<String, Object> toReaderRow(User reader) {
        // 使用有序 Map 保持字段顺序。
        Map<String, Object> item = new LinkedHashMap<>();
        // 读者主键 id。
        item.put("id", reader.getId());
        // 借阅证号。
        item.put("username", reader.getUsername());
        // 读者姓名。
        item.put("realName", reader.getRealName());
        // 手机号。
        item.put("phone", reader.getPhone());
        // 备注。
        item.put("remark", reader.getRemark());
        // 账号状态。
        item.put("status", reader.getStatus());
        // 创建时间。
        item.put("createdAt", reader.getCreatedAt());
        // 当前未还数量。
        item.put("currentBorrowCount", borrowRecordRepository.countByUserIdAndStatus(reader.getId(), "borrowed"));
        return item;
    }

    // 自助注册请求体。
    public static class RegisterRequest {
        private String phone;
        private String password;
        private String confirmPassword;

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getConfirmPassword() {
            return confirmPassword;
        }

        public void setConfirmPassword(String confirmPassword) {
            this.confirmPassword = confirmPassword;
        }

        public String phone() {
            return phone;
        }

        public String password() {
            return password;
        }

        public String confirmPassword() {
            return confirmPassword;
        }
    }
}
