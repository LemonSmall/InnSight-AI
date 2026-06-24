package com.sushijia.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 后台管理端服务 - 端口 8081
 */
@SpringBootApplication(scanBasePackages = "com.sushijia")
public class AdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
