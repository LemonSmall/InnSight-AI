package com.sushijia.hotel.controller;

import com.sushijia.common.response.R;
import com.sushijia.framework.tenant.TenantContext;
import com.sushijia.hotel.service.CreditService;
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
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String type) {
        Long tenantId = TenantContext.get();
        List<CreditLedger> list = ledgerMapper.findByTenant(tenantId, limit);
        if (type != null) {
            list = list.stream().filter(l -> type.equals(l.getType())).toList();
        }
        return R.ok(list);
    }

    /** 检查指定模块是否够算力 */
    @GetMapping("/credits/check")
    public R<Map<String, Boolean>> checkBalance(@RequestParam String moduleKey) {
        Long tenantId = TenantContext.get();
        Map<String, Boolean> result = new HashMap<>();
        result.put("canAfford", creditService.canAfford(tenantId, moduleKey));
        return R.ok(result);
    }
}
