package com.sushijia.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("hotel_knowledge_extract_jobs")
public class HotelKnowledgeExtractJob {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long fileId;
    private String inputText;
    private String sourceType;
    private String status;
    private String extractedJson;
    private String summary;
    private String errorMsg;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
