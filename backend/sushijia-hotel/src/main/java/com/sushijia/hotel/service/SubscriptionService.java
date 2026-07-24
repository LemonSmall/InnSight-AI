package com.sushijia.hotel.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sushijia.common.exception.BizException;
import com.sushijia.common.response.ResultCode;
import com.sushijia.repository.entity.TenantPlan;
import com.sushijia.repository.entity.TenantSubscription;
import com.sushijia.repository.mapper.TenantPlanMapper;
import com.sushijia.repository.mapper.TenantSubscriptionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    private final TenantSubscriptionMapper subscriptionMapper;
    private final TenantPlanMapper planMapper;

    public TenantSubscription getCurrentSubscription(Long tenantId) {
        try {
            return subscriptionMapper.findCurrentByTenantId(tenantId);
        } catch (DataAccessException e) {
            log.error("Tenant subscription table is not initialized. tenantId={}", tenantId, e);
            throw new BizException(ResultCode.FORBIDDEN, "套餐数据未初始化，请联系管理员");
        }
    }

    public TenantPlan getPlan(String planCode) {
        if (planCode == null || planCode.isBlank()) {
            return null;
        }
        try {
            return planMapper.selectOne(new LambdaQueryWrapper<TenantPlan>()
                .eq(TenantPlan::getCode, planCode)
                .eq(TenantPlan::getEnabled, true)
                .last("LIMIT 1"));
        } catch (DataAccessException e) {
            log.error("Tenant plan table is not initialized. planCode={}", planCode, e);
            throw new BizException(ResultCode.FORBIDDEN, "套餐数据未初始化，请联系管理员");
        }
    }

    public Map<String, Object> getSubscriptionOverview(Long tenantId) {
        TenantSubscription subscription = getCurrentSubscription(tenantId);
        TenantPlan plan = subscription == null ? null : getPlan(subscription.getPlanCode());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("subscription", subscription);
        result.put("plan", plan);
        result.put("active", isSubscriptionActive(subscription));
        result.put("enabledModules", plan == null ? "" : safe(plan.getEnabledModules()));
        return result;
    }

    public void ensureModuleEnabled(Long tenantId, String moduleKey) {
        TenantSubscription subscription = getCurrentSubscription(tenantId);
        if (!isSubscriptionActive(subscription)) {
            throw new BizException(ResultCode.FORBIDDEN, "当前套餐不可用，请联系管理员");
        }

        TenantPlan plan = getPlan(subscription.getPlanCode());
        if (plan == null) {
            throw new BizException(ResultCode.FORBIDDEN, "当前套餐未配置，请联系管理员");
        }

        String modules = safe(plan.getEnabledModules());
        boolean allowed = Arrays.stream(modules.split(","))
            .map(String::trim)
            .anyMatch(item -> item.equals(moduleKey) || isIncludedModule(moduleKey, item));
        if (!allowed) {
            throw new BizException(ResultCode.FORBIDDEN, "当前套餐暂未开通该功能");
        }
    }

    private boolean isIncludedModule(String moduleKey, String enabledModule) {
        return "occupancy_image".equals(moduleKey) && "brain".equals(enabledModule);
    }

    private boolean isSubscriptionActive(TenantSubscription subscription) {
        if (subscription == null) {
            return false;
        }
        String status = subscription.getStatus();
        if (!"active".equals(status) && !"trialing".equals(status)) {
            return false;
        }
        LocalDateTime endAt = subscription.getEndAt();
        return endAt == null || endAt.isAfter(LocalDateTime.now());
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
