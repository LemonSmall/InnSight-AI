package com.sushijia.framework.ratelimit;

import java.lang.annotation.*;

/**
 * 限流注解 —— 基于 Redis 滑动窗口
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {
    /** 限流 key 前缀 */
    String key() default "";
    /** 时间窗口内允许的最大调用次数 */
    int maxCalls() default 10;
    /** 时间窗口（秒） */
    int windowSeconds() default 60;
    /** 限流失败提示信息 */
    String message() default "请求过于频繁，请稍后再试";
}
