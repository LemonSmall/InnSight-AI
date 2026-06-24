package com.sushijia.repository.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 房型实体
 */
@Data
@TableName("room_types")
public class RoomType {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private String name;
    private BigDecimal basePrice;
    private Integer count;
    private Integer sortOrder;
}
