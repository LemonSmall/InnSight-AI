package com.sushijia.hotel.controller;

import com.sushijia.common.response.R;
import com.sushijia.framework.tenant.TenantContext;
import com.sushijia.repository.entity.RoomType;
import com.sushijia.repository.mapper.RoomTypeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 智能定价 - 四因子模型
 */
@RestController
@RequestMapping("/api/hotel")
@RequiredArgsConstructor
public class PricingController {

    private final RoomTypeMapper roomTypeMapper;

    @PostMapping("/pricing/recommend")
    public R<Map<String, Object>> recommend(@RequestBody Map<String, String> params) {
        Long tenantId = TenantContext.get();
        String holiday = params.getOrDefault("holiday", "normal");
        String occupancy = params.getOrDefault("occupancy", "50-70");
        String weather = params.getOrDefault("weather", "sunny");
        String competition = params.getOrDefault("competition", "none");

        double holidayMul = getHolidayMultiplier(holiday);
        double occAdj = getOccupancyAdjust(occupancy);
        double weatherAdj = getWeatherAdjust(weather);
        double compAdj = getCompetitionAdjust(competition);

        List<RoomType> types = roomTypeMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RoomType>()
                .eq(RoomType::getTenantId, tenantId));

        List<Map<String, Object>> results = new ArrayList<>();
        List<String> reasons = new ArrayList<>();

        for (RoomType rt : types) {
            double totalAdj = 1 + occAdj + weatherAdj + compAdj;
            int recommended = (int) Math.round(rt.getBasePrice().doubleValue() * holidayMul * totalAdj);
            int changePct = (int) Math.round((recommended / rt.getBasePrice().doubleValue() - 1) * 100);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("roomId", rt.getId());
            item.put("roomName", rt.getName());
            item.put("basePrice", rt.getBasePrice());
            item.put("recommendedPrice", recommended);
            item.put("changePercent", changePct);
            results.add(item);
        }

        reasons.add("节假日类型倍率: ×" + holidayMul);
        if (occAdj > 0) reasons.add("入住率较高，供不应求，上浮" + Math.round(occAdj * 100) + "%");
        else if (occAdj < 0) reasons.add("入住率偏低，降价引流，下调" + Math.round(Math.abs(occAdj) * 100) + "%");
        else reasons.add("入住率正常区间，价格保持平稳");
        if (weatherAdj < 0) reasons.add("天气影响出行意愿，下调" + Math.round(Math.abs(weatherAdj) * 100) + "%");
        if (compAdj > 0) reasons.add("周边竞争较小，适当上浮" + Math.round(compAdj * 100) + "%");
        else if (compAdj < 0) reasons.add("周边竞争激烈，下调" + Math.round(Math.abs(compAdj) * 100) + "%");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("results", results);
        response.put("reasons", reasons);
        return R.ok(response);
    }

    private double getHolidayMultiplier(String h) {
        return switch (h) { case "big" -> 1.28; case "small" -> 1.15; case "weekend" -> 1.1; case "emotion" -> 1.2; default -> 1.0; };
    }
    private double getOccupancyAdjust(String o) {
        return switch (o) { case "90+" -> 0.10; case "70-90" -> 0.05; case "50-70" -> 0; case "30-50" -> -0.08; case "30-" -> -0.18; default -> 0; };
    }
    private double getWeatherAdjust(String w) {
        return switch (w) { case "sunny" -> 0; case "rain" -> -0.08; case "heavy" -> -0.15; case "extreme" -> -0.22; default -> 0; };
    }
    private double getCompetitionAdjust(String c) {
        return switch (c) { case "none" -> 0.05; case "light" -> 0; case "medium" -> -0.10; case "high" -> -0.15; default -> 0; };
    }
}
