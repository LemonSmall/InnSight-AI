package com.sushijia.hotel.controller;

import com.sushijia.common.exception.BizException;
import com.sushijia.common.response.ResultCode;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Legacy pricing endpoint. Real pricing advice must use /api/content/generate with moduleKey=pricing.
 */
@RestController
@RequestMapping("/api/hotel")
public class PricingController {

    @PostMapping("/pricing/recommend")
    public void recommend() {
        throw new BizException(ResultCode.BAD_REQUEST, "请使用AI内容生成接口");
    }
}
