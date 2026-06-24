package com.sushijia.framework.tenant;

import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.stereotype.Component;

/**
 * MyBatis-Plus 多租户拦截器
 * 所有 SQL 自动拼接 tenant_id = ? 条件
 */
@Component
public class TenantLineHandler implements net.sf.jsqlparser.expression.ExpressionVisitor {

    /**
     * 创建拦截器实例
     */
    public static TenantLineInnerInterceptor createInterceptor() {
        return new TenantLineInnerInterceptor(new net.sf.jsqlparser.expression.ExpressionVisitor() {
            // 当前租户 ID
        });
    }

    /**
     * 简化版：直接使用 Lambda 创建
     */
    public static TenantLineInnerInterceptor interceptor() {
        return new TenantLineInnerInterceptor(() -> {
            Long tenantId = TenantContext.get();
            if (tenantId == null) return new LongValue(0);
            return new LongValue(tenantId);
        }, "tenant_id");
    }
}
