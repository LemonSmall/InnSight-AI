package com.sushijia.admin.controller;

import com.sushijia.common.response.R;
import com.sushijia.hotel.service.CreditService;
import com.sushijia.repository.entity.BillingRule;
import com.sushijia.repository.entity.CreditLedger;
import com.sushijia.repository.entity.RechargePackage;
import com.sushijia.repository.mapper.BillingRuleMapper;
import com.sushijia.repository.mapper.CreditLedgerMapper;
import com.sushijia.repository.mapper.RechargePackageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 后台 - 计费规则 + 充值套餐 + 流水
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class BillingAdminController {

    private final BillingRuleMapper billingRuleMapper;
    private final RechargePackageMapper packageMapper;
    private final CreditLedgerMapper ledgerMapper;
    private final CreditService creditService;

    // ====== 计费规则 ======
    @GetMapping("/billing-rules")
    public R<List<BillingRule>> listRules() {
        return R.ok(billingRuleMapper.selectList(null));
    }

    @PutMapping("/billing-rules/{id}")
    public R<String> updateRule(@PathVariable Long id, @RequestBody BillingRule rule) {
        rule.setId(id);
        billingRuleMapper.updateById(rule);
        return R.ok("ok");
    }

    // ====== 充值套餐 ======
    @GetMapping("/packages")
    public R<List<RechargePackage>> listPackages() {
        return R.ok(packageMapper.selectList(null));
    }

    @PostMapping("/packages")
    public R<RechargePackage> createPackage(@RequestBody RechargePackage pkg) {
        packageMapper.insert(pkg);
        return R.ok(pkg);
    }

    // ====== 流水 ======
    @GetMapping("/ledger")
    public R<List<CreditLedger>> listLedger(@RequestParam(defaultValue = "100") int limit) {
        return R.ok(ledgerMapper.findByTenant(null, limit));
    }

    // ====== 充值 ======
    @PostMapping("/tenants/{id}/recharge")
    public R<Map<String, Object>> recharge(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        int amount = body.getOrDefault("amount", 0);
        String detail = "后台充值: " + body.getOrDefault("detail", "无备注");
        int balance = creditService.recharge(id, amount, detail);
        return R.ok(Map.of("tenantId", id, "balance", balance));
    }
}
