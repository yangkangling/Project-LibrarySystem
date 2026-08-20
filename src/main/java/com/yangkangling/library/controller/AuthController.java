package com.yangkangling.library.controller;

import com.yangkangling.library.entity.User;
import com.yangkangling.library.repository.UserRepository;
import com.yangkangling.library.service.PasswordService;
import javax.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// 登录、登出和密码修改接口。
@RestController
@RequestMapping("/auth")
public class AuthController {
    // 用户仓库，管理员和读者都从同一张用户表中查询。
    private final UserRepository userRepository;
    private final PasswordService passwordService;

    // 构造方法注入用户仓库。
    public AuthController(UserRepository userRepository, PasswordService passwordService) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
    }

    // 管理员登录入口，登录成功后在 Session 中记录管理员身份。
    @PostMapping("/login")
    public Map<String, Object> login(@RequestParam String username, @RequestParam String password, HttpSession session) {
        User admin = authenticate(username, password, "admin", "账号或密码错误");
        // 保存管理员 id，后续接口靠它判断是否已经登录管理端。
        session.setAttribute("adminId", admin.getId());
        // 保存角色，方便前端判断当前登录身份。
        session.setAttribute("role", "admin");
        // 返回登录成功信息给前端。
        return Map.of("message", "管理员登录成功", "username", admin.getUsername(), "role", admin.getRole());
    }

    // 读者登录入口，登录成功后在 Session 中记录读者身份。
    @PostMapping("/reader-login")
    public Map<String, Object> readerLogin(@RequestParam String username, @RequestParam String password, HttpSession session) {
        User reader = authenticate(username, password, "reader", "借阅证号或密码错误");
        // 停用或冻结的读者不能登录自助端。
        if ("disabled".equals(reader.getStatus())) {
            throw new RuntimeException("该读者账号已停用，不能登录自助端");
        }
        // 保存读者 id，后续 self 接口只允许访问自己的数据。
        session.setAttribute("readerId", reader.getId());
        // 保存角色，方便前端进入读者端。
        session.setAttribute("role", "reader");
        // 返回读者登录成功信息。
        return Map.of("message", "读者登录成功", "username", reader.getUsername(), "realName", reader.getRealName(), "role", reader.getRole());
    }

    // 管理员和读者共用的修改密码入口。
    @PostMapping("/change-password")
    public Map<String, String> changePassword(@RequestParam String role,
                                              @RequestParam String username,
                                              @RequestParam String oldPassword,
                                              @RequestParam String newPassword,
                                              @RequestParam String confirmPassword) {
        // 校验并规范化账号类型。
        String normalizedRole = normalizeRole(role);
        // 校验并去掉账号前后的空格。
        String normalizedUsername = requireText(username, "账号不能为空");
        // 校验原密码不能为空。
        String currentPassword = requireText(oldPassword, "原密码不能为空");
        // 校验新密码不能为空。
        String nextPassword = requireText(newPassword, "新密码不能为空");
        // 校验确认密码不能为空。
        String repeatedPassword = requireText(confirmPassword, "确认密码不能为空");

        // 两次新密码必须完全一致。
        if (!nextPassword.equals(repeatedPassword)) {
            throw new RuntimeException("两次输入的新密码不一致");
        }
        // 新密码至少 6 位，避免过短。
        if (nextPassword.length() < 6) {
            throw new RuntimeException("新密码至少 6 位");
        }

        User user = authenticate(normalizedUsername, currentPassword, normalizedRole, "账号或原密码错误");
        // 读者账号如果已经停用，则不允许自助改密码。
        if ("reader".equals(normalizedRole) && "disabled".equals(user.getStatus())) {
            throw new RuntimeException("该读者账号已停用，请联系管理员");
        }

        // 保存新密码。
        user.setPassword(passwordService.encode(nextPassword));
        userRepository.save(user);
        // 返回修改成功提示。
        return Map.of("message", "密码修改成功");
    }

    // 退出登录，清空当前 Session。
    @PostMapping("/logout")
    public Map<String, String> logout(HttpSession session) {
        // Session 失效后，前端再次访问接口会被拦截为未登录。
        session.invalidate();
        return Map.of("message", "退出成功");
    }

    // 查询当前登录状态，用于前端刷新页面后恢复登录身份。
    @GetMapping("/me")
    public Map<String, Object> me(HttpSession session) {
        // 管理员登录时保存的标记。
        Object adminId = session.getAttribute("adminId");
        // 读者登录时保存的标记。
        Object readerId = session.getAttribute("readerId");
        // 当前账号角色。
        Object role = session.getAttribute("role");
        // 同时返回通用登录状态和分角色登录状态。
        return Map.of(
                "loggedIn", adminId != null || readerId != null,
                "adminLoggedIn", adminId != null,
                "readerLoggedIn", readerId != null,
                "role", role == null ? "" : role
        );
    }

    // 校验账号类型，只允许 admin 或 reader。
    private String normalizeRole(String role) {
        String value = requireText(role, "请选择账号类型");
        if (!"admin".equals(value) && !"reader".equals(value)) {
            throw new RuntimeException("账号类型不正确");
        }
        return value;
    }

    // 校验必填文本，并统一去掉前后空格。
    private String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new RuntimeException(message);
        }
        return value.trim();
    }

    private User authenticate(String username, String password, String role, String failureMessage) {
        String normalizedUsername = requireText(username, "账号不能为空");
        String rawPassword = requireText(password, "密码不能为空");
        User user = userRepository.findByUsernameAndRole(normalizedUsername, role)
                .orElseThrow(() -> new RuntimeException(failureMessage));
        if (!passwordService.verifyAndUpgrade(user, rawPassword)) {
            throw new RuntimeException(failureMessage);
        }
        return user;
    }
}
