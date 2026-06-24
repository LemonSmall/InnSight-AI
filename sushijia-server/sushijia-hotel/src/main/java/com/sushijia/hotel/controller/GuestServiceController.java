package com.sushijia.hotel.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sushijia.ai.client.AiClient;
import com.sushijia.common.response.R;
import com.sushijia.framework.tenant.TenantContext;
import com.sushijia.repository.entity.Guest;
import com.sushijia.repository.entity.Tenant;
import com.sushijia.repository.mapper.GuestMapper;
import com.sushijia.repository.mapper.TenantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 好评引导 / 回评话术 / 在住客管理 — 前台客服三合一
 */
@RestController
@RequestMapping("/api/hotel")
@RequiredArgsConstructor
public class GuestServiceController {

    private final GuestMapper guestMapper;
    private final TenantMapper tenantMapper;
    private final AiClient aiClient;

    /** 在住客人列表 */
    @GetMapping("/guests")
    public R<List<Map<String, Object>>> getGuests() {
        Long tenantId = TenantContext.get();
        List<Guest> guests = guestMapper.selectList(
            new LambdaQueryWrapper<Guest>()
                .eq(Guest::getTenantId, tenantId)
                .in(Guest::getStatus, "staying", "checking_in"));

        List<Map<String, Object>> list = guests.stream().map(g -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", g.getId());
            m.put("roomNumber", g.getRoomNumber());
            m.put("guestType", g.getGuestType());
            m.put("source", g.getSource());
            m.put("nights", g.getNights());
            m.put("checkinDate", g.getCheckinDate());
            m.put("checkoutDate", g.getCheckoutDate());
            m.put("status", g.getStatus());
            return m;
        }).collect(Collectors.toList());
        return R.ok(list);
    }

    /** 生成好评模板 */
    @PostMapping("/review/generate")
    public R<Map<String, String>> generateReview(@RequestBody Map<String, String> body) {
        String guestType = body.getOrDefault("guestType", "couple");
        Long tenantId = TenantContext.get();
        Tenant tenant = tenantMapper.selectById(tenantId);
        String hotelName = tenant != null ? tenant.getName() : "民宿";
        String tags = tenant != null ? tenant.getTags() : "";

        String systemPrompt = String.format(
            "你是「%s」酒店的运营助手。请根据客人类型生成一条自然真实的好评模板。" +
            "酒店特色：%s。语气温暖真诚，像真实客人写的，不要过于夸张。用中文回复。",
            hotelName, tags);

        String userPrompt = String.format("客人类型：%s。请生成一条适合在携程/美团等平台发布的好评。", guestType);

        String review = aiClient.chat(systemPrompt, userPrompt);
        return R.ok(Map.of("review", review, "guestType", guestType));
    }

    /** 生成回评话术 */
    @PostMapping("/reply/generate")
    public R<Map<String, String>> generateReply(@RequestBody Map<String, String> body) {
        String reviewType = body.getOrDefault("reviewType", "好评");
        String style = body.getOrDefault("style", "温暖亲切");
        Long tenantId = TenantContext.get();
        Tenant tenant = tenantMapper.selectById(tenantId);
        String hotelName = tenant != null ? tenant.getName() : "民宿";

        String systemPrompt = String.format(
            "你是「%s」酒店的客服经理。请根据客人评价类型和回复风格生成一条得体的回评话术。" +
            "回复要真诚、有温度，差评要表示歉意和改进决心，好评要表达感谢和期待重逢。用中文回复。",
            hotelName);

        String userPrompt = String.format("评价类型：%s\n回复风格：%s\n请生成回评话术。", reviewType, style);

        String reply = aiClient.chat(systemPrompt, userPrompt);
        return R.ok(Map.of("reply", reply, "reviewType", reviewType));
    }
}
