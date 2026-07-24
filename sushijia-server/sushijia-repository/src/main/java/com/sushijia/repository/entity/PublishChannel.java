package com.sushijia.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("publish_channels")
public class PublishChannel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String channelType;
    private String channelName;
    private String authConfig;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
