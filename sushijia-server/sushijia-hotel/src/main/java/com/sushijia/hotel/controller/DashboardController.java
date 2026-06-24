package com.sushijia.hotel.controller;

import com.sushijia.framework.tenant.TenantContext;
import com.sushijia.hotel.service.DashboardService;
import com.sushijia.common.response.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 数字营销大盘 - 对应前端 DashboardView
 */
@RestController
@RequestMapping("/api/hotel")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * 获取大盘数据（KPI + 房型统计 + 在住客人）
     */
    @GetMapping("/dashboard")
    public R<Map<String, Object>> getDashboard() {
        Long tenantId = TenantContext.get();
        return R.ok(dashboardService.getDashboard(tenantId));
    }
}
