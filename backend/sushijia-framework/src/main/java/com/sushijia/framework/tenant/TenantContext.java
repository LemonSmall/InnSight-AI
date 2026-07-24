package com.sushijia.framework.tenant;

import com.alibaba.ttl.TransmittableThreadLocal;

/**
 * 租户上下文 —— 全链路透传 tenant_id
 * 使用 TransmittableThreadLocal 保证线程池场景下不丢失
 */
public class TenantContext {

    private static final TransmittableThreadLocal<Long> HOLDER = new TransmittableThreadLocal<>();

    public static void set(Long tenantId) {
        HOLDER.set(tenantId);
    }

    public static Long get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
