package com.sushijia.hotel.controller;

import com.sushijia.common.response.R;
import com.sushijia.framework.tenant.TenantContext;
import com.sushijia.repository.entity.MarketingPlan;
import com.sushijia.repository.mapper.MarketingPlanMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 营销方案 CRUD — 对应前端 PlansView / PlanView
 */
@RestController
@RequestMapping("/api/hotel/plans")
@RequiredArgsConstructor
public class PlanController {

    private final MarketingPlanMapper planMapper;

    @GetMapping
    public R<List<MarketingPlan>> list() {
        Long tenantId = TenantContext.get();
        return R.ok(planMapper.findByTenant(tenantId));
    }

    @GetMapping("/{id}")
    public R<MarketingPlan> detail(@PathVariable Long id) {
        return R.ok(planMapper.selectById(id));
    }

    @PostMapping
    public R<MarketingPlan> create(@RequestBody MarketingPlan plan) {
        plan.setTenantId(TenantContext.get());
        plan.setCreatedAt(LocalDateTime.now());
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.insert(plan);
        return R.ok(plan);
    }

    @PutMapping("/{id}")
    public R<String> update(@PathVariable Long id, @RequestBody MarketingPlan plan) {
        plan.setId(id);
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.updateById(plan);
        return R.ok("ok");
    }

    @DeleteMapping("/{id}")
    public R<String> delete(@PathVariable Long id) {
        planMapper.deleteById(id);
        return R.ok("ok");
    }
}
