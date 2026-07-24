package com.sushijia.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("hotel_knowledge_items")
public class HotelKnowledgeItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String category;
    private String title;
    private String content;
    private String structuredJson;
    private String sourceType;
    private String sourceName;
    private Long sourceFileId;
    private Long extractJobId;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private String status;
    private BigDecimal confidence;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
