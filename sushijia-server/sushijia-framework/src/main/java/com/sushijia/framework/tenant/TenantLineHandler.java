package com.sushijia.framework.tenant;

import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import net.sf.jsqlparser.expression.LongValue;

/**
 * MyBatis-Plus 多租户拦截器工厂
 * 所有 SQL 自动拼接 tenant_id = ? 条件
 */
public class TenantLineHandler {

    /**
     * 创建多租户拦截器
     */
    public static TenantLineInnerInterceptor createInterceptor() {
        return new TenantLineInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler() {
            @Override
            public net.sf.jsqlparser.expression.Expression getTenantId() {
                Long tenantId = TenantContext.get();
                return new LongValue(tenantId != null ? tenantId : 0);
            }

            @Override
            public String getTenantIdColumn() {
                return "tenant_id";
            }

            @Override
            public boolean ignoreTable(String tableName) {
                // 默认不忽略任何表，子类可重写
                return false;
            }
        });
    }
}
