package com.sushijia.hotel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 酒店端服务 - 端口 8080
 */
@EnableAsync
@SpringBootApplication(scanBasePackages = "com.sushijia")
public class HotelApplication {

    public static void main(String[] args) {
        SpringApplication.run(HotelApplication.class, args);
    }
}
