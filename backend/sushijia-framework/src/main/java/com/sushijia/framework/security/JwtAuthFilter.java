package com.sushijia.framework.security;

import com.sushijia.common.utils.JwtUtil;
import com.sushijia.framework.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String path = request.getRequestURI();
        if (isPermitPath(path)) {
            chain.doFilter(request, response);
            return;
        }

        String token = extractToken(request);
        if (token == null) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"未登录\"}");
            return;
        }

        Map<String, Object> claims = JwtUtil.parseToken(token);
        if (claims == null) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"Token无效或已过期\"}");
            return;
        }

        try {
            Object tenantId = claims.get("tenant_id");
            if (tenantId instanceof Number) {
                TenantContext.set(((Number) tenantId).longValue());
            }

            Object staffId = claims.get("staff_id");
            Object adminId = claims.get("admin_id");
            Object role = claims.get("role");
            request.setAttribute("staffId", staffId instanceof Number ? ((Number) staffId).longValue() : null);
            request.setAttribute("adminId", adminId instanceof Number ? ((Number) adminId).longValue() : null);
            request.setAttribute("role", role);

            String principal = adminId != null ? "admin:" + adminId : "staff:" + staffId;
            String roleName = role == null ? "USER" : role.toString().toUpperCase();
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + roleName))
            );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);

            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private boolean isPermitPath(String path) {
        return path.startsWith("/api/auth/")
            || path.startsWith("/api/admin/auth/")
            || path.startsWith("/v3/api-docs")
            || path.startsWith("/doc.html")
            || path.startsWith("/swagger-ui")
            || path.startsWith("/webjars");
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
