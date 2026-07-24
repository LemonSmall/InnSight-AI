package com.sushijia.admin.controller;

import com.sushijia.common.response.R;
import com.sushijia.common.utils.JwtUtil;
import com.sushijia.repository.entity.Admin;
import com.sushijia.repository.mapper.AdminMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminMapper adminMapper;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public R<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String password = body.get("password");

        Admin admin = adminMapper.findByEmail(email);
        if (admin == null || !passwordEncoder.matches(password, admin.getPasswordHash())) {
            return R.fail(3001, "邮箱或密码错误");
        }
        if ("disabled".equals(admin.getStatus())) {
            return R.fail(1002, "账户已禁用");
        }

        String accessToken = JwtUtil.createAdminAccessToken(admin.getId(), admin.getRole());
        String refreshToken = JwtUtil.createRefreshToken(null, null, admin.getRole(), admin.getId());

        return R.ok(Map.of(
            "accessToken", accessToken,
            "refreshToken", refreshToken,
            "name", admin.getName(),
            "role", admin.getRole()
        ));
    }
}
