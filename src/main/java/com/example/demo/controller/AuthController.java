package com.example.demo.controller;

import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final UserRepository userRepository;

    public AuthController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestParam String username, @RequestParam String password, HttpSession session) {
        User admin = userRepository.findByUsernameAndPasswordAndRole(username, password, "admin")
                .orElseThrow(() -> new RuntimeException("账号或密码错误"));
        session.setAttribute("adminId", admin.getId());
        session.setAttribute("role", "admin");
        return Map.of("message", "管理员登录成功", "username", admin.getUsername(), "role", admin.getRole());
    }

    @PostMapping("/reader-login")
    public Map<String, Object> readerLogin(@RequestParam String username, @RequestParam String password, HttpSession session) {
        User reader = userRepository.findByUsernameAndPasswordAndRole(username, password, "reader")
                .orElseThrow(() -> new RuntimeException("借阅证号或密码错误"));
        if ("disabled".equals(reader.getStatus())) {
            throw new RuntimeException("该读者账号已停用，不能登录自助端");
        }
        session.setAttribute("readerId", reader.getId());
        session.setAttribute("role", "reader");
        return Map.of("message", "读者登录成功", "username", reader.getUsername(), "realName", reader.getRealName(), "role", reader.getRole());
    }

    @PostMapping("/logout")
    public Map<String, String> logout(HttpSession session) {
        session.invalidate();
        return Map.of("message", "退出成功");
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpSession session) {
        Object adminId = session.getAttribute("adminId");
        Object readerId = session.getAttribute("readerId");
        Object role = session.getAttribute("role");
        return Map.of(
                "loggedIn", adminId != null || readerId != null,
                "adminLoggedIn", adminId != null,
                "readerLoggedIn", readerId != null,
                "role", role == null ? "" : role
        );
    }
}
