package com.sushijia.repository.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("content_tasks")
public class ContentTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long userId;
    private String moduleKey;
    private String inputParams;    // JSON string
    private String status;         // pending/processing/done/failed/moderated
    private Long resultId;
    private Long generationHistoryId;
    private String errorMsg;
    private Integer costCredits;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
