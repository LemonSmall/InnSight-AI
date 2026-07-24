package com.sushijia.repository.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("admins")
public class Admin {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String email;
    private String name;
    private String passwordHash;
    private String role;
    private String status;
    private LocalDateTime createdAt;
}
