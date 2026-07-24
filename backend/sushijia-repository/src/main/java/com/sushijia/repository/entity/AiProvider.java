package com.sushijia.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_providers")
public class AiProvider {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String providerKey;
    private String providerName;
    private String providerType;
    private String endpoint;
    private String authType;
    private String apiKeyEncrypted;
    private Integer enabled;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
