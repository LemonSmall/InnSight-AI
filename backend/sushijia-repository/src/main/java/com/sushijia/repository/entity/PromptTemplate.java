package com.sushijia.repository.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("prompt_templates")
public class PromptTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String moduleKey;
    private String version;
    private String title;
    private String content;
    private String modelName;
    private Integer maxTokens;
    private String status;  // draft/gray/production/rolled_back
    private Integer grayPercent;
    private String createdBy;
    private LocalDateTime createdAt;
}
