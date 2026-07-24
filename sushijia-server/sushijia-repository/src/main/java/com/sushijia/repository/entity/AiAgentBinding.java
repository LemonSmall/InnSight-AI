package com.sushijia.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_agent_bindings")
public class AiAgentBinding {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String moduleKey;
    private String provider;
    private String appType;
    private String appId;
    private String apiKey;
    private String appName;
    private String endpoint;
    private String inputSchema;
    private String botId;
    private String botApiKey;
    private String botName;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
