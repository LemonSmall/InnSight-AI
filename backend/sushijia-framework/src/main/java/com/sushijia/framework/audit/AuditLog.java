package com.sushijia.framework.audit;

import java.lang.annotation.*;

/**
 * 操作审计注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {
    String action();
    String detail() default "";
}
