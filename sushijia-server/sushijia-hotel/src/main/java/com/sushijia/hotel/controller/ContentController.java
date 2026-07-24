package com.sushijia.hotel.controller;

import com.sushijia.common.response.R;
import com.sushijia.common.exception.BizException;
import com.sushijia.common.response.ResultCode;
import com.sushijia.framework.tenant.TenantContext;
import com.sushijia.hotel.service.ContentGenerateService;
import com.sushijia.hotel.service.CreditService;
import com.sushijia.hotel.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/content")
@RequiredArgsConstructor
@Slf4j
public class ContentController {

    private final ContentGenerateService contentService;
    private final CreditService creditService;
    private final SubscriptionService subscriptionService;

    @PostMapping("/generate")
    public R<Map<String, Object>> generate(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            Long tenantId = TenantContext.get();
            Object moduleValue = body.getOrDefault("moduleKey", body.get("module"));
            if (moduleValue == null || String.valueOf(moduleValue).isBlank()) {
                throw new BizException(ResultCode.BAD_REQUEST, "moduleKey不能为空");
            }
            String module = String.valueOf(moduleValue);
            Map<String, Object> params = extractParams(body);

            subscriptionService.ensureModuleEnabled(tenantId, module);
            creditService.ensureCanAfford(tenantId, module);
            Long userId = (Long) request.getAttribute("staffId");
            Long taskId = contentService.submitTask(tenantId, userId, module, params);

            return R.ok(Map.of(
                "taskId", taskId,
                "estimatedCost", creditService.getModuleCost(module),
                "balance", creditService.queryBalance(tenantId)
            ));
        } catch (BizException e) {
            return R.fail(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("Content generation submission failed.", e);
            return R.fail(ResultCode.AI_GENERATE_FAILED.getCode(), "AI调用失败，请稍后重试");
        }
    }

    @GetMapping("/task/{taskId}")
    public R<Map<String, Object>> getTask(@PathVariable("taskId") Long taskId, HttpServletRequest request) {
        Long tenantId = TenantContext.get();
        return R.ok(contentService.getTaskResult(tenantId, (Long) request.getAttribute("staffId"), taskId));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractParams(Map<String, Object> body) {
        Object params = body.get("params");
        if (params instanceof Map<?, ?>) {
            return (Map<String, Object>) params;
        }
        return body.entrySet().stream()
            .filter(entry -> !"module".equals(entry.getKey()) && !"moduleKey".equals(entry.getKey()))
            .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
