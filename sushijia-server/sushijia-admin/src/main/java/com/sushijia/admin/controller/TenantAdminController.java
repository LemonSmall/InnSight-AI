package com.sushijia.admin.controller;

import com.sushijia.common.response.R;
import com.sushijia.framework.tenant.TenantContext;
import com.sushijia.repository.entity.Tenant;
import com.sushijia.repository.entity.TenantPlan;
import com.sushijia.repository.entity.TenantSubscription;
import com.sushijia.repository.mapper.TenantMapper;
import com.sushijia.repository.mapper.TenantPlanMapper;
import com.sushijia.repository.mapper.TenantSubscriptionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 后台 - 租户管理
 */
@RestController
@RequestMapping("/api/admin/tenants")
@RequiredArgsConstructor
public class TenantAdminController {

    private final TenantMapper tenantMapper;
    private final TenantPlanMapper tenantPlanMapper;
    private final TenantSubscriptionMapper subscriptionMapper;

    @GetMapping
    public R<List<Tenant>> list() {
        return R.ok(tenantMapper.selectList(null));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable("id") Long id) {
        Tenant tenant = tenantMapper.selectById(id);
        TenantSubscription subscription = subscriptionMapper.findCurrentByTenantId(id);
        TenantPlan plan = null;
        if (subscription != null && subscription.getPlanCode() != null) {
            plan = tenantPlanMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TenantPlan>()
                .eq(TenantPlan::getCode, subscription.getPlanCode())
                .last("LIMIT 1"));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tenant", tenant);
        data.put("subscription", subscription);
        data.put("plan", plan);
        return R.ok(data);
    }

    @GetMapping("/plans")
    public R<List<TenantPlan>> plans() {
        return R.ok(tenantPlanMapper.selectList(null));
    }

    @PostMapping
    public R<Tenant> create(@RequestBody Tenant tenant) {
        applyTenantDefaults(tenant);
        tenantMapper.insert(tenant);
        return R.ok(tenant);
    }

    @PutMapping("/{id}")
    public R<String> update(@PathVariable("id") Long id, @RequestBody Tenant tenant) {
        tenant.setId(id);
        applyTenantDefaults(tenant);
        tenantMapper.updateById(tenant);
        return R.ok("ok");
    }

    @DeleteMapping("/{id}")
    public R<String> delete(@PathVariable("id") Long id) {
        tenantMapper.deleteById(id);
        return R.ok("ok");
    }

    @GetMapping("/stats")
    public R<Map<String, Object>> stats() {
        List<Tenant> all = tenantMapper.selectList(null);
        long active = all.stream().filter(t -> "active".equals(t.getStatus())).count();
        long warning = all.stream().filter(t -> "warning".equals(t.getStatus())).count();
        int totalBalance = all.stream().mapToInt(t -> t.getBalance() != null ? t.getBalance() : 0).sum();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", all.size());
        stats.put("active", active);
        stats.put("warning", warning);
        stats.put("totalBalance", totalBalance);
        return R.ok(stats);
    }

    private void applyTenantDefaults(Tenant tenant) {
        if (tenant.getName() == null || tenant.getName().isBlank()) {
            tenant.setName("未命名酒店");
        }
        if (tenant.getType() == null || tenant.getType().isBlank()) {
            tenant.setType("精品民宿");
        }
        if (tenant.getCity() == null || tenant.getCity().isBlank()) {
            tenant.setCity("未设置城市");
        }
        if (tenant.getTotalRooms() == null) {
            tenant.setTotalRooms(0);
        }
        if (tenant.getTier() == null || tenant.getTier().isBlank()) {
            tenant.setTier("trial");
        }
        if (tenant.getStatus() == null || tenant.getStatus().isBlank()) {
            tenant.setStatus("active");
        }
        if (tenant.getBalance() == null) {
            tenant.setBalance(0);
        }
        if (tenant.getAlertThreshold() == null) {
            tenant.setAlertThreshold(500);
        }
        if (tenant.getMeltThreshold() == null) {
            tenant.setMeltThreshold(0);
        }
        if (tenant.getQpsLimit() == null) {
            tenant.setQpsLimit(5);
        }
    }
}
