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
        return Map.of("message", "登录成功", "username", admin.getUsername());
    }

    @PostMapping("/logout")
    public Map<String, String> logout(HttpSession session) {
        session.invalidate();
        return Map.of("message", "退出成功");
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpSession session) {
        Object adminId = session.getAttribute("adminId");
        return Map.of("loggedIn", adminId != null);
    }
}
