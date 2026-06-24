package com.sushijia.repository.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("guests")
public class Guest {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long roomTypeId;
    private String roomNumber;
    private String guestType;
    private String source;
    private Integer nights;
    private LocalDate checkinDate;
    private LocalDate checkoutDate;
    private String status;  // checking_in/staying/checking_out/departed

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
