package com.sushijia.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("hotel_knowledge_sync_jobs")
public class HotelKnowledgeSyncJob {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String providerKey;
    private String targetDatasetId;
    private Long knowledgeItemId;
    private String status;
    private Integer retryCount;
    private String errorMsg;
    private LocalDateTime syncedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
