package com.yangkangling.library.service;

import com.yangkangling.library.entity.User;
import com.yangkangling.library.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordService {
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;

    public PasswordService(PasswordEncoder passwordEncoder, UserRepository userRepository) {
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
    }

    public String encode(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    public String encodeIfNecessary(String password) {
        if (password == null || password.isBlank()) {
            throw new RuntimeException("密码不能为空");
        }
        return isBcrypt(password) ? password : encode(password);
    }

    public boolean verifyAndUpgrade(User user, String rawPassword) {
        if (user == null || rawPassword == null) {
            return false;
        }
        String storedPassword = user.getPassword();
        if (isBcrypt(storedPassword)) {
            return passwordEncoder.matches(rawPassword, storedPassword);
        }
        boolean matched = rawPassword.equals(storedPassword);
        if (matched) {
            user.setPassword(encode(rawPassword));
            userRepository.save(user);
        }
        return matched;
    }

    private boolean isBcrypt(String value) {
        return value != null && value.matches("^\\$2[aby]\\$\\d{2}\\$.+");
    }
}
