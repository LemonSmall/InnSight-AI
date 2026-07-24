package com.sushijia.repository.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("style_library")
public class StyleLibrary {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String scope;  // public / private
    private Long tenantId;
    private String promptSegment;
    private java.math.BigDecimal feedbackScore;
    private Integer usageCount;
    @TableField("enabled")
    private Boolean enabled;
    private LocalDateTime createdAt;
}
