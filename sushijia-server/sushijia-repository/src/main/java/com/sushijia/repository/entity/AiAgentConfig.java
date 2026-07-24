package com.sushijia.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_agent_configs")
public class AiAgentConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String moduleKey;
    private String moduleName;
    private String capabilityKey;
    private String providerKey;
    private String appId;
    private String workflowId;
    private String apiKeyEncrypted;
    private String endpoint;
    private String callMode;
    private String inputSchema;
    private String outputSchema;
    private String outputParser;
    private String knowledgePolicy;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
