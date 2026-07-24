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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreditService {

    private final TenantMapper tenantMapper;
    private final BillingRuleMapper billingRuleMapper;
    private final CreditLedgerMapper ledgerMapper;
    private final StringRedisTemplate redisTemplate;

    public int getModuleCost(String moduleKey) {
        String cached = read(cacheBillingRuleKey(moduleKey));
        if (cached != null) {
            return parseInt(cached, 0);
        }

        BillingRule rule = billingRuleMapper.findByModuleKey(moduleKey);
        if (rule == null || Boolean.FALSE.equals(rule.getEnabled()) || rule.getCost() == null) {
            write(cacheBillingRuleKey(moduleKey), "0", 10, TimeUnit.MINUTES);
            return 0;
        }
        int cost = Math.max(rule.getCost(), 0);
        write(cacheBillingRuleKey(moduleKey), String.valueOf(cost), 10, TimeUnit.MINUTES);
        return cost;
    }

    public void ensureCanAfford(Long tenantId, String moduleKey) {
        int cost = getModuleCost(moduleKey);
        if (cost == 0) {
            return;
        }

        Tenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new BizException(ResultCode.TENANT_NOT_FOUND);
        }

        int balance = valueOrZero(tenant.getBalance());
        int meltThreshold = valueOrZero(tenant.getMeltThreshold());
        if (balance <= meltThreshold) {
            throw new BizException(ResultCode.ACCOUNT_MELTED);
        }
        if (balance < cost) {
            throw new BizException(ResultCode.INSUFFICIENT_CREDIT);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public int deduct(Long tenantId, String moduleKey, String detail) {
        BillingRule rule = billingRuleMapper.findByModuleKey(moduleKey);
        if (rule == null || Boolean.FALSE.equals(rule.getEnabled()) || rule.getCost() == null || rule.getCost() == 0) {
            return queryBalance(tenantId);
        }

        int cost = Math.max(rule.getCost(), 0);
        Tenant tenant = tenantMapper.selectByIdForUpdate(tenantId);
        if (tenant == null) {
            throw new BizException(ResultCode.TENANT_NOT_FOUND);
        }

        int balance = valueOrZero(tenant.getBalance());
        int meltThreshold = valueOrZero(tenant.getMeltThreshold());
        if (balance <= meltThreshold) {
            ledgerMapper.insert(buildLedger(tenantId, "consume", 0, balance, moduleKey, rule.getModuleName(), "账户熔断，已拦截：" + detail, "melted"));
            throw new BizException(ResultCode.ACCOUNT_MELTED);
        }
        if (balance < cost) {
            ledgerMapper.insert(buildLedger(tenantId, "consume", 0, balance, moduleKey, rule.getModuleName(), "余额不足，已拦截：" + detail, "failed"));
            throw new BizException(ResultCode.INSUFFICIENT_CREDIT);
        }

        int newBalance = balance - cost;
        tenant.setBalance(newBalance);
        tenantMapper.updateById(tenant);

        ledgerMapper.insert(buildLedger(tenantId, "consume", -cost, newBalance, moduleKey, rule.getModuleName(), detail, "success"));
        write(cacheBalanceKey(tenantId), String.valueOf(newBalance), 30, TimeUnit.SECONDS);

        int alertThreshold = valueOrZero(tenant.getAlertThreshold());
        if (newBalance <= alertThreshold && newBalance > meltThreshold) {
            log.warn("Tenant {} credit balance is low: {}", tenantId, newBalance);
        }

        return newBalance;
    }

    @Transactional(rollbackFor = Exception.class)
    public int recharge(Long tenantId, int amount, String detail) {
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

        ledgerMapper.insert(buildLedger(tenantId, "recharge", amount, newBalance, null, "后台充值", detail, "success"));
        write(cacheBalanceKey(tenantId), String.valueOf(newBalance), 30, TimeUnit.SECONDS);

        log.info("Tenant {} recharged {} credits. balance={}", tenantId, amount, newBalance);
        return newBalance;
    }

    public int queryBalance(Long tenantId) {
        String cached = read(cacheBalanceKey(tenantId));
        if (cached != null) {
            return parseInt(cached, 0);
        }
        Tenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new BizException(ResultCode.TENANT_NOT_FOUND);
        }
        int balance = valueOrZero(tenant.getBalance());
        write(cacheBalanceKey(tenantId), String.valueOf(balance), 30, TimeUnit.SECONDS);
        return balance;
    }

    public boolean canAfford(Long tenantId, String moduleKey) {
        try {
            ensureCanAfford(tenantId, moduleKey);
            return true;
        } catch (BizException e) {
            return false;
        }
    }

    private CreditLedger buildLedger(Long tenantId, String type, int amount, int balanceAfter,
                                     String moduleKey, String moduleName, String detail, String status) {
        CreditLedger entry = new CreditLedger();
        entry.setTenantId(tenantId);
        entry.setType(type);
        entry.setAmount(amount);
        entry.setBalanceAfter(balanceAfter);
        entry.setModuleKey(moduleKey);
        entry.setModuleName(moduleName);
        entry.setDetail(detail);
        entry.setStatus(status);
        entry.setCreatedAt(LocalDateTime.now());
        return entry;
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private String cacheBalanceKey(Long tenantId) {
        return "sushijia:tenant:" + tenantId + ":credit:balance";
    }

    private String cacheBillingRuleKey(String moduleKey) {
        return "sushijia:billing:rule:" + moduleKey;
    }

    private String read(String key) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            return value == null || value.isBlank() ? null : value;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void write(String key, String value, long timeout, TimeUnit unit) {
        try {
            redisTemplate.opsForValue().set(key, value, timeout, unit);
        } catch (Exception ignored) {
            // Redis is an acceleration layer only.
        }
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
