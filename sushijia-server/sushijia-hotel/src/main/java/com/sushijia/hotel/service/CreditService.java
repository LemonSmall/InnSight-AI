package com.sushijia.hotel.service;

import com.sushijia.common.exception.BizException;
import com.sushijia.common.response.ResultCode;
import com.sushijia.repository.entity.BillingRule;
import com.sushijia.repository.entity.CreditLedger;
import com.sushijia.repository.entity.Tenant;
import com.sushijia.repository.mapper.BillingRuleMapper;
import com.sushijia.repository.mapper.CreditLedgerMapper;
import com.sushijia.repository.mapper.TenantMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 算力服务 —— 扣减 + 充值 + 查询
 * 行级锁（SELECT ... FOR UPDATE）防并发超扣
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreditService {

    private final TenantMapper tenantMapper;
    private final BillingRuleMapper billingRuleMapper;
    private final CreditLedgerMapper ledgerMapper;

    @Transactional(rollbackFor = Exception.class)
    public int deduct(Long tenantId, String moduleKey, String detail) {
        BillingRule rule = billingRuleMapper.findByModuleKey(moduleKey);
        if (rule == null || rule.getCost() == null || rule.getCost() == 0) {
            return queryBalance(tenantId);
        }
        int cost = rule.getCost();

        Tenant tenant = tenantMapper.selectByIdForUpdate(tenantId);
        if (tenant == null) throw new BizException(ResultCode.TENANT_NOT_FOUND);

        if (tenant.getBalance() <= tenant.getMeltThreshold()) {
            ledgerMapper.insert(buildLedger(tenantId, 0, tenant.getBalance(),
                    moduleKey, rule.getModuleName(), "熔断拦截: " + detail, "melted"));
            throw new BizException(ResultCode.ACCOUNT_MELTED);
        }

        int newBalance = tenant.getBalance() - cost;
        tenant.setBalance(newBalance);
        tenantMapper.updateById(tenant);

        ledgerMapper.insert(buildLedger(tenantId, -cost, newBalance,
                moduleKey, rule.getModuleName(), detail, "success"));

        if (newBalance <= tenant.getAlertThreshold() && newBalance > tenant.getMeltThreshold()) {
            log.warn("⚠ 租户 {} 算力余额仅剩 {}，触发预警", tenantId, newBalance);
        }

        return newBalance;
    }

    @Transactional(rollbackFor = Exception.class)
    public int recharge(Long tenantId, int amount, String detail) {
        Tenant tenant = tenantMapper.selectByIdForUpdate(tenantId);
        if (tenant == null) throw new BizException(ResultCode.TENANT_NOT_FOUND);

        int newBalance = tenant.getBalance() + amount;
        tenant.setBalance(newBalance);
        tenantMapper.updateById(tenant);

        ledgerMapper.insert(buildLedger(tenantId, amount, newBalance,
                null, "充值", detail, "success"));

        log.info("租户 {} 充值 {} 算力，余额 {}", tenantId, amount, newBalance);
        return newBalance;
    }

    public int queryBalance(Long tenantId) {
        Tenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) throw new BizException(ResultCode.TENANT_NOT_FOUND);
        return tenant.getBalance();
    }

    public boolean canAfford(Long tenantId, String moduleKey) {
        BillingRule rule = billingRuleMapper.findByModuleKey(moduleKey);
        if (rule == null || rule.getCost() == 0) return true;
        return queryBalance(tenantId) >= rule.getCost();
    }

    private CreditLedger buildLedger(Long tenantId, int amount, int balanceAfter,
                                      String moduleKey, String moduleName, String detail, String status) {
        CreditLedger entry = new CreditLedger();
        entry.setTenantId(tenantId);
        entry.setType(amount >= 0 ? "recharge" : "consume");
        entry.setAmount(amount);
        entry.setBalanceAfter(balanceAfter);
        entry.setModuleKey(moduleKey);
        entry.setModuleName(moduleName);
        entry.setDetail(detail);
        entry.setStatus(status);
        entry.setCreatedAt(LocalDateTime.now());
        return entry;
    }
}
