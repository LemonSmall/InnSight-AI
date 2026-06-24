package com.sushijia.repository.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("room_status")
public class RoomStatus {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long roomTypeId;
    private String roomNumber;
    private String status;  // sold/free/dirty/repair

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
