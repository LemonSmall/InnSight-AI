package com.sushijia.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_call_logs")
public class AiCallLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long userId;
    private String moduleKey;
    private String provider;
    private String appName;
    private String appType;
    private String endpoint;
    private String requestId;
    private Long taskId;
    private String status;
    private Integer httpStatus;
    private Integer durationMs;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer creditsCost;
    private String errorCode;
    private String errorMessage;
    private String requestSummary;
    private String responseSummary;
    private LocalDateTime createdAt;
}
