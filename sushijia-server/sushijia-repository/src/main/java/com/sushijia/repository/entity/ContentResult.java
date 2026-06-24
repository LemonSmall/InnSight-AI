package com.sushijia.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("content_results")
public class ContentResult {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private String content;
    private Integer tokensUsed;
    private Integer moderated;
    private String moderationDetail;
    private LocalDateTime createdAt;
}
