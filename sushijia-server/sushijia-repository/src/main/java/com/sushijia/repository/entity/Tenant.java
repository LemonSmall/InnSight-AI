package com.sushijia.repository.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 租户实体 —— 对应 tenants 表
 */
@Data
@TableName("tenants")
public class Tenant {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String type;
    private String city;
    private Integer totalRooms;
    private String tags;
    private String targetAudience;
    private String nearby;
    private String contactPhone;
    private String tier;    // trial/basic/pro/flagship
    private String status;  // active/warning/suspended/closed
    private Integer balance;
    private Integer alertThreshold;
    private Integer meltThreshold;
    private Integer qpsLimit;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
