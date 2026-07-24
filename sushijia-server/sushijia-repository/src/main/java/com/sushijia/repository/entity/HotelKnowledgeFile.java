package com.sushijia.repository.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("hotel_knowledge_files")
public class HotelKnowledgeFile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String originalName;
    private String fileType;
    private Long fileSize;
    @JsonIgnore
    private String storagePath;
    private String parseStatus;
    private String parseError;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
