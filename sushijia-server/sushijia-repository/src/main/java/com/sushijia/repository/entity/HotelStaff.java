package com.sushijia.repository.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 酒店员工实体
 */
@Data
@TableName("hotel_staff")
public class HotelStaff {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private String name;
    private String phone;
    private String role;    // admin/manager/front_desk/marketing
    private String avatar;
    private String passwordHash;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
