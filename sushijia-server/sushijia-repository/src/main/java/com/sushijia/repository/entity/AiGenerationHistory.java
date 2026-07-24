package com.sushijia.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_generation_history")
public class AiGenerationHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long userId;
    private String moduleKey;
    private String title;
    private String prompt;
    private String inputParams;
    private String outputContent;
    private String outputAssets;
    private String providerKey;
    private Long agentConfigId;
    private Long agentBindingId;
    private String agentName;
    private String requestId;
    private String knowledgeRefs;
    private Integer durationMs;
    private Integer costCredits;
    private String status;
    private String errorMsg;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
