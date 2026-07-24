package com.sushijia.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_generation_assets")
public class AiGenerationAsset {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long generationId;
    private String assetType;
    private String url;
    private String storagePath;
    private String metadataJson;
    private LocalDateTime createdAt;
}
