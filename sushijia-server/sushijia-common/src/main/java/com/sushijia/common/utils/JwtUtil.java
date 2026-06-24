package com.sushijia.common.utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * JWT 工具类
 */
@Slf4j
public class JwtUtil {

    // 优先读取环境变量 JWT_SECRET，未设置则使用默认值
    private static final String SECRET = System.getenv("JWT_SECRET") != null
        ? System.getenv("JWT_SECRET")
        : "sushijia-ai-hotel-platform-secret-key-2026-must-be-longer";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private static final long ACCESS_EXPIRE = 30 * 60 * 1000L;   // 30分钟
    private static final long REFRESH_EXPIRE = 7 * 24 * 60 * 60 * 1000L; // 7天

    /**
     * 生成 Access Token
     */
    public static String createAccessToken(Long tenantId, Long staffId, String role, Long adminId) {
        return buildToken(tenantId, staffId, role, adminId, ACCESS_EXPIRE);
    }

    /**
     * 生成 Refresh Token
     */
    public static String createRefreshToken(Long tenantId, Long staffId, String role, Long adminId) {
        return buildToken(tenantId, staffId, role, adminId, REFRESH_EXPIRE);
    }

    private static String buildToken(Long tenantId, Long staffId, String role, Long adminId, long expire) {
        JwtBuilder builder = Jwts.builder()
                .subject(adminId != null ? "admin:" + adminId : "staff:" + staffId)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expire))
                .signWith(KEY);

        if (tenantId != null) {
            builder.claim("tenant_id", tenantId);
        }
        if (staffId != null) {
            builder.claim("staff_id", staffId);
        }
        if (adminId != null) {
            builder.claim("admin_id", adminId);
        }

        return builder.compact();
    }

    /**
     * 解析 Token 中的 Claims
     */
    public static Map<String, Object> parseToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(KEY)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims;
        } catch (JwtException e) {
            log.warn("JWT解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 验证 Token 是否有效
     */
    public static boolean validate(String token) {
        return parseToken(token) != null;
    }

    /**
     * 从 Token 获取 tenant_id
     */
    public static Long getTenantId(String token) {
        Map<String, Object> claims = parseToken(token);
        if (claims == null) return null;
        Object v = claims.get("tenant_id");
        return v instanceof Number ? ((Number) v).longValue() : null;
    }
}
