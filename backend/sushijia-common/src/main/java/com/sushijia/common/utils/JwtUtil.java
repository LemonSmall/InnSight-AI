package com.sushijia.common.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Slf4j
public class JwtUtil {

    private static final String SECRET = System.getenv("JWT_SECRET") != null
        ? System.getenv("JWT_SECRET")
        : "sushijia-ai-hotel-platform-secret-key-2026-must-be-longer";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private static final long ACCESS_EXPIRE = 30 * 60 * 1000L;
    private static final long ADMIN_ACCESS_EXPIRE = 7 * 24 * 60 * 60 * 1000L;
    private static final long REFRESH_EXPIRE = 7 * 24 * 60 * 60 * 1000L;

    public static String createAccessToken(Long tenantId, Long staffId, String role, Long adminId) {
        return buildToken(tenantId, staffId, role, adminId, ACCESS_EXPIRE);
    }

    public static String createAdminAccessToken(Long adminId, String role) {
        return buildToken(null, null, role, adminId, ADMIN_ACCESS_EXPIRE);
    }

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

    public static Map<String, Object> parseToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(KEY)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims;
        } catch (JwtException e) {
            log.warn("JWT parse failed: {}", e.getMessage());
            return null;
        }
    }

    public static boolean validate(String token) {
        return parseToken(token) != null;
    }

    public static Long getTenantId(String token) {
        Map<String, Object> claims = parseToken(token);
        if (claims == null) return null;
        Object v = claims.get("tenant_id");
        return v instanceof Number ? ((Number) v).longValue() : null;
    }
}
