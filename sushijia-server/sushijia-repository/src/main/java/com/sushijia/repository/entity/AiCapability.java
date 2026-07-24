package com.sushijia.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_capabilities")
public class AiCapability {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String capabilityKey;
    private String capabilityName;
    private String description;
    private Integer enabled;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
