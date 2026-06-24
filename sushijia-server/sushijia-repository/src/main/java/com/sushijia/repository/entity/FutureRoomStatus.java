package com.sushijia.repository.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;

@Data
@TableName("future_room_status")
public class FutureRoomStatus {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private LocalDate date;
    private String roomTypeName;
    private Integer occupied;
    private Integer available;
    private Integer overbooked;
}
