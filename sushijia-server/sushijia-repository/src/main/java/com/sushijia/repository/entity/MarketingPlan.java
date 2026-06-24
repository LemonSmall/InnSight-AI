package com.sushijia.repository.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("marketing_plans")
public class MarketingPlan {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String name;
    private String festival;
    private String status;  // draft/active/completed
    private String hotelName;
    private String period;
    private String target;
    private String tags;        // JSON
    private String kpis;        // JSON
    private String phases;      // JSON
    private String channels;    // JSON
    private String pricings;    // JSON
    private String activities;  // JSON
    private String alertNote;
    private String alerts;      // JSON

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
