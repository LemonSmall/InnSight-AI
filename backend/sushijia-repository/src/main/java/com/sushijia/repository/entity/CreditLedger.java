package com.sushijia.repository.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("credit_ledger")
public class CreditLedger {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String type;    // consume / recharge
    private Integer amount;
    private Integer balanceAfter;
    private String moduleKey;
    private String moduleName;
    private String detail;
    private String status;  // success / failed / melted

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
