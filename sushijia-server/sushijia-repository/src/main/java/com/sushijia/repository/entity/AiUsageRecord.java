package com.sushijia.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ai_usage_records")
public class AiUsageRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long userId;
    private String moduleKey;
    private String providerKey;
    private String requestId;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer imageCount;
    private Integer videoSeconds;
    private BigDecimal rawCost;
    private Integer creditCost;
    private LocalDateTime createdAt;
}
