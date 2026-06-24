package com.sushijia.framework.security;

import com.sushijia.common.utils.JwtUtil;
import com.sushijia.framework.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * JWT 认证过滤器
 * 解析 Token → 注入 TenantContext → 放行
 */
@Slf4j
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // 白名单：登录/注册/Knife4j/Swagger 直接放行
        if (path.startsWith("/api/auth/")
                || path.startsWith("/api/admin/auth/")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/doc.html")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/webjars")) {
            chain.doFilter(request, response);
            return;
        }

        String token = extractToken(request);
        if (token == null) {
            response.setStatus(401);
            response.getWriter().write("{\"code\":401,\"message\":\"未登录\"}");
            return;
        }

        Map<String, Object> claims = JwtUtil.parseToken(token);
        if (claims == null) {
            response.setStatus(401);
            response.getWriter().write("{\"code\":401,\"message\":\"Token无效或已过期\"}");
            return;
        }

        // 注入租户上下文（酒店端 Token 才有 tenant_id）
        Object tenantId = claims.get("tenant_id");
        if (tenantId instanceof Number) {
            TenantContext.set(((Number) tenantId).longValue());
        }

        // 将用户信息放入 request attribute
        Object staffId = claims.get("staff_id");
        Object adminId = claims.get("admin_id");
        request.setAttribute("staffId", staffId instanceof Number ? ((Number) staffId).longValue() : null);
        request.setAttribute("adminId", adminId instanceof Number ? ((Number) adminId).longValue() : null);
        request.setAttribute("role", claims.get("role"));

        chain.doFilter(request, response);

        // 请求结束后清理
        TenantContext.clear();
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
