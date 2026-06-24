package com.sushijia.admin.controller;

import com.sushijia.common.response.R;
import com.sushijia.framework.tenant.TenantContext;
import com.sushijia.repository.entity.Tenant;
import com.sushijia.repository.mapper.TenantMapper;
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

    @GetMapping
    public R<List<Tenant>> list() {
        return R.ok(tenantMapper.selectList(null));
    }

    @GetMapping("/{id}")
    public R<Tenant> detail(@PathVariable Long id) {
        return R.ok(tenantMapper.selectById(id));
    }

    @PostMapping
    public R<Tenant> create(@RequestBody Tenant tenant) {
        tenantMapper.insert(tenant);
        return R.ok(tenant);
    }

    @PutMapping("/{id}")
    public R<String> update(@PathVariable Long id, @RequestBody Tenant tenant) {
        tenant.setId(id);
        tenantMapper.updateById(tenant);
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
}
