package com.sushijia.common.response;

import lombok.Getter;

@Getter
public enum ResultCode {

    SUCCESS(200, "success"),

    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "数据冲突"),
    SERVER_ERROR(500, "服务器内部错误"),

    TENANT_NOT_FOUND(1001, "租户不存在"),
    TENANT_SUSPENDED(1002, "租户已停用"),

    INSUFFICIENT_CREDIT(2001, "算力余额不足"),
    ACCOUNT_MELTED(2002, "账户已熔断，请充值后继续使用"),

    LOGIN_FAILED(3001, "手机号或验证码错误"),
    SMS_SEND_FAILED(3002, "短信发送失败"),
    SMS_CODE_EXPIRED(3003, "验证码已过期"),

    AI_GENERATE_FAILED(4001, "AI生成失败"),
    AI_TIMEOUT(4002, "AI请求超时"),
    CONTENT_MODERATED(4003, "内容触发合规审查，已拦截");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
