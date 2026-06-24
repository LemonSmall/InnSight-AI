package com.sushijia.repository.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.sushijia.framework.tenant.TenantContext;
import com.sushijia.common.utils.JwtUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置 —— 多租户 + 分页
 */
@Configuration
public class MyBatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 多租户插件：自动在所有 SQL 的 WHERE 中追加 tenant_id
        // 注意：isolateSql=false 确保 JOIN 等复杂 SQL 也能正确处理
        TenantTenantLineHandler handler = new TenantTenantLineHandler();
        // 这里简化处理：对于不需要多租户的表，由 Mapper 自行指定 @SqlParser(filter = true)

        // 分页插件
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));

        return interceptor;
    }

    /**
     * 内部类：多租户拦截处理器
     */
    public static class TenantTenantLineHandler extends com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor {
        public TenantTenantLineHandler() {
            super(new com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler() {
                @Override
                public net.sf.jsqlparser.expression.Expression getTenantId() {
                    Long tid = TenantContext.get();
                    return tid != null
                        ? new net.sf.jsqlparser.expression.LongValue(tid)
                        : new net.sf.jsqlparser.expression.LongValue(0);
                }

                @Override
                public String getTenantIdColumn() {
                    return "tenant_id";
                }

                @Override
                public boolean ignoreTable(String tableName) {
                    // 不需要租户隔离的表：全局配置表 + 后台专有表
                    return "billing_rules".equalsIgnoreCase(tableName)
                        || "recharge_packages".equalsIgnoreCase(tableName)
                        || "admins".equalsIgnoreCase(tableName)
                        || "prompt_templates".equalsIgnoreCase(tableName)
                        || "style_library".equalsIgnoreCase(tableName)
                        || "moderation_rules".equalsIgnoreCase(tableName)
                        || "audit_logs".equalsIgnoreCase(tableName);
                }
            });
        }
    }
}
