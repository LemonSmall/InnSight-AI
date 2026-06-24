package com.sushijia.repository.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("billing_rules")
public class BillingRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String moduleKey;
    private String moduleName;
    private String board;
    private Integer cost;
    private java.math.BigDecimal estCostRmb;
    @TableField("enabled")
    private Boolean enabled;
    private Integer sortOrder;
}
