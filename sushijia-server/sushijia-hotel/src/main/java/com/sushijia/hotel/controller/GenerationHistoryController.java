package com.sushijia.hotel.controller;

import com.sushijia.common.response.R;
import com.sushijia.framework.tenant.TenantContext;
import com.sushijia.hotel.service.GenerationHistoryService;
import com.sushijia.repository.entity.AiGenerationHistory;
import com.sushijia.repository.entity.UserRecentPreset;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai/history")
@RequiredArgsConstructor
public class GenerationHistoryController {

    private final GenerationHistoryService historyService;

    @GetMapping
    public R<List<AiGenerationHistory>> list(@RequestParam(value = "moduleKey", required = false) String moduleKey,
                                             @RequestParam(value = "limit", defaultValue = "100") int limit,
                                             HttpServletRequest request) {
        Long tenantId = TenantContext.get();
        Long userId = (Long) request.getAttribute("staffId");
        return R.ok(historyService.listRecent(tenantId, userId, moduleKey, limit));
    }

    @GetMapping("/latest-preset")
    public R<UserRecentPreset> latestPreset(@RequestParam("moduleKey") String moduleKey, HttpServletRequest request) {
        Long tenantId = TenantContext.get();
        Long userId = (Long) request.getAttribute("staffId");
        return R.ok(historyService.latestPreset(tenantId, userId, moduleKey));
    }

    @GetMapping("/{id}")
    public R<AiGenerationHistory> detail(@PathVariable("id") Long id,
                                         @RequestParam(value = "moduleKey", required = false) String moduleKey,
                                         HttpServletRequest request) {
        Long tenantId = TenantContext.get();
        Long userId = (Long) request.getAttribute("staffId");
        AiGenerationHistory history = historyService.detail(tenantId, userId, moduleKey, id);
        if (history == null) {
            return R.fail(404, "生成记录不存在或已删除");
        }
        return R.ok(history);
    }

    @DeleteMapping
    public R<String> delete(@RequestBody Map<String, List<Long>> body, HttpServletRequest request) {
        Long tenantId = TenantContext.get();
        Long userId = (Long) request.getAttribute("staffId");
        historyService.delete(tenantId, userId, body == null ? List.of() : body.get("ids"));
        return R.ok("删除成功");
    }
}
