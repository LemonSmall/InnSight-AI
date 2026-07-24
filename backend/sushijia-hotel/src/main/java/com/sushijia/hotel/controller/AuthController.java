package com.sushijia.hotel.controller;

import com.sushijia.common.response.R;
import com.sushijia.hotel.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 酒店端 - 登录认证
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 发送短信验证码
     */
    @PostMapping("/sms/send")
    public R<String> sendSms(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        authService.sendSmsCode(phone);
        return R.ok("验证码已发送");
    }

    /**
     * 手机号 + 验证码登录
     */
    @PostMapping("/login/phone")
    public R<Map<String, String>> loginByPhone(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        String code = body.get("code");
        Map<String, String> tokens = authService.loginByPhone(phone, code);
        return R.ok(tokens);
    }

    /**
     * 手机号 + 密码登录
     */
    @PostMapping("/login/password")
    public R<Map<String, String>> loginByPassword(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        String password = body.get("password");
        Map<String, String> tokens = authService.loginByPassword(phone, password);
        return R.ok(tokens);
    }

    /**
     * Refresh Token 换取新 Access Token
     */
    @PostMapping("/token/refresh")
    public R<Map<String, String>> refreshToken(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        Map<String, String> tokens = authService.refreshAccessToken(refreshToken);
        return R.ok(tokens);
    }
}
