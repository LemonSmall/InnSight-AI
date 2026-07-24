package com.sushijia.admin.controller;

import com.sushijia.common.exception.BizException;
import com.sushijia.common.response.R;
import com.sushijia.common.response.ResultCode;
import com.sushijia.repository.entity.BillingRule;
import com.sushijia.repository.entity.CreditLedger;
import com.sushijia.repository.entity.RechargePackage;
import com.sushijia.repository.entity.Tenant;
import com.sushijia.repository.mapper.BillingRuleMapper;
import com.sushijia.repository.mapper.CreditLedgerMapper;
import com.sushijia.repository.mapper.RechargePackageMapper;
import com.sushijia.repository.mapper.TenantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class BillingAdminController {

    private final BillingRuleMapper billingRuleMapper;
    private final RechargePackageMapper packageMapper;
    private final CreditLedgerMapper ledgerMapper;
    private final TenantMapper tenantMapper;
    private final StringRedisTemplate redisTemplate;

    @GetMapping("/billing-rules")
    public R<List<BillingRule>> listRules() {
        return R.ok(billingRuleMapper.selectList(null));
    }

    @PostMapping("/billing-rules")
    public R<BillingRule> createRule(@RequestBody BillingRule rule) {
        billingRuleMapper.insert(rule);
        clearBillingRuleCache(rule.getModuleKey());
        return R.ok(rule);
    }

    @PutMapping("/billing-rules/{id}")
    public R<String> updateRule(@PathVariable("id") Long id, @RequestBody BillingRule rule) {
        BillingRule old = billingRuleMapper.selectById(id);
        rule.setId(id);
        billingRuleMapper.updateById(rule);
        clearBillingRuleCache(old == null ? null : old.getModuleKey());
        clearBillingRuleCache(rule.getModuleKey());
        return R.ok("ok");
    }

    @DeleteMapping("/billing-rules/{id}")
    public R<String> deleteRule(@PathVariable("id") Long id) {
        BillingRule old = billingRuleMapper.selectById(id);
        billingRuleMapper.deleteById(id);
        clearBillingRuleCache(old == null ? null : old.getModuleKey());
        return R.ok("ok");
    }

    @GetMapping("/packages")
    public R<List<RechargePackage>> listPackages() {
        return R.ok(packageMapper.selectList(null));
    }

    @PostMapping("/packages")
    public R<RechargePackage> createPackage(@RequestBody RechargePackage pkg) {
        packageMapper.insert(pkg);
        return R.ok(pkg);
    }

    @PutMapping("/packages/{id}")
    public R<String> updatePackage(@PathVariable("id") Long id, @RequestBody RechargePackage pkg) {
        pkg.setId(id);
        packageMapper.updateById(pkg);
        return R.ok("ok");
    }

    @DeleteMapping("/packages/{id}")
    public R<String> deletePackage(@PathVariable("id") Long id) {
        packageMapper.deleteById(id);
        return R.ok("ok");
    }

    @GetMapping("/ledger")
    public R<List<CreditLedger>> listLedger(@RequestParam(value = "limit", defaultValue = "100") int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        return R.ok(ledgerMapper.findByTenant(null, safeLimit));
    }

    @PostMapping("/tenants/{id}/recharge")
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> recharge(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        Object amountValue = body.get("amount");
        int amount = amountValue instanceof Number
            ? ((Number) amountValue).intValue()
            : Integer.parseInt(String.valueOf(amountValue));
        String detail = String.valueOf(body.getOrDefault("detail", "后台充值"));
        int balance = rechargeTenant(id, amount, detail);
        return R.ok(Map.of("tenantId", id, "balance", balance));
    }

    private int rechargeTenant(Long tenantId, int amount, String detail) {
        if (amount <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "充值金额必须大于 0");
        }

        Tenant tenant = tenantMapper.selectByIdForUpdate(tenantId);
        if (tenant == null) {
            throw new BizException(ResultCode.TENANT_NOT_FOUND);
        }

        int newBalance = valueOrZero(tenant.getBalance()) + amount;
        tenant.setBalance(newBalance);
        tenantMapper.updateById(tenant);

        CreditLedger entry = new CreditLedger();
        entry.setTenantId(tenantId);
        entry.setType("recharge");
        entry.setAmount(amount);
        entry.setBalanceAfter(newBalance);
        entry.setModuleName("后台充值");
        entry.setDetail(detail);
        entry.setStatus("success");
        entry.setCreatedAt(LocalDateTime.now());
        ledgerMapper.insert(entry);

        try {
            redisTemplate.opsForValue().set("sushijia:tenant:" + tenantId + ":credit:balance", String.valueOf(newBalance));
        } catch (Exception ignored) {
            // Redis is an acceleration layer only.
        }
        return newBalance;
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private void clearBillingRuleCache(String moduleKey) {
        if (moduleKey == null || moduleKey.isBlank()) {
            return;
        }
        try {
            redisTemplate.delete("sushijia:billing:rule:" + moduleKey);
        } catch (Exception ignored) {
            // Redis is an acceleration layer only.
        }
    }
}
