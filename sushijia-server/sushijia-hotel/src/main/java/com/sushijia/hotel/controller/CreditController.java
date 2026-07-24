package com.sushijia.hotel.controller;

import com.sushijia.common.response.R;
import com.sushijia.framework.tenant.TenantContext;
import com.sushijia.hotel.service.CreditService;
import com.sushijia.hotel.service.SubscriptionService;
import com.sushijia.repository.entity.CreditLedger;
import com.sushijia.repository.mapper.CreditLedgerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 算力流水 - 对应前端 CreditsView
 */
@RestController
@RequestMapping("/api/hotel")
@RequiredArgsConstructor
public class CreditController {

    private final CreditService creditService;
    private final CreditLedgerMapper ledgerMapper;
    private final SubscriptionService subscriptionService;

    /** 获取余额 + 今日消耗 */
    @GetMapping("/credits/balance")
    public R<Map<String, Object>> getBalance() {
        Long tenantId = TenantContext.get();
        int balance = creditService.queryBalance(tenantId);
        int todayConsume = ledgerMapper.todayConsumed(tenantId);

        Map<String, Object> result = new HashMap<>();
        result.put("balance", balance);
        result.put("todayConsume", todayConsume);
        return R.ok(result);
    }

    /** 获取流水列表（分页） */
    @GetMapping("/credits/ledger")
    public R<List<CreditLedger>> getLedger(
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @RequestParam(value = "type", required = false) String type) {
        Long tenantId = TenantContext.get();
        List<CreditLedger> list = ledgerMapper.findByTenant(tenantId, limit);
        if (type != null) {
            list = list.stream().filter(l -> type.equals(l.getType())).toList();
        }
        return R.ok(list);
    }

    /** 检查指定模块是否够算力 */
    @GetMapping("/credits/check")
    public R<Map<String, Boolean>> checkBalance(@RequestParam("moduleKey") String moduleKey) {
        Long tenantId = TenantContext.get();
        Map<String, Boolean> result = new HashMap<>();
        boolean moduleEnabled = true;
        try {
            subscriptionService.ensureModuleEnabled(tenantId, moduleKey);
        } catch (Exception e) {
            moduleEnabled = false;
        }
        result.put("moduleEnabled", moduleEnabled);
        result.put("canAfford", moduleEnabled && creditService.canAfford(tenantId, moduleKey));
        return R.ok(result);
    }

    /** Current subscription and plan permissions. */
    @GetMapping("/subscription")
    public R<Map<String, Object>> getSubscription() {
        Long tenantId = TenantContext.get();
        return R.ok(subscriptionService.getSubscriptionOverview(tenantId));
    }
}
