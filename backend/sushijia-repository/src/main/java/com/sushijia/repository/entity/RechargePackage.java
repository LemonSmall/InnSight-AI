package com.sushijia.repository.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("recharge_packages")
public class RechargePackage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Integer credits;
    private java.math.BigDecimal priceRmb;
    private String applicableTiers;
    @TableField("enabled")
    private Boolean enabled;
}
