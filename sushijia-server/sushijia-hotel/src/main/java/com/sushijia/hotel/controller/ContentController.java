package com.sushijia.hotel.controller;

import com.sushijia.common.response.R;
import com.sushijia.framework.tenant.TenantContext;
import com.sushijia.hotel.service.CreditService;
import com.sushijia.hotel.service.ContentGenerateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI 内容生成 —— 所有模块的统一入口
 * 对应前端 Wechat/Xhs/Video/Poster/Article/Review/Reply/Brain 各页面
 */
@RestController
@RequestMapping("/api/content")
@RequiredArgsConstructor
public class ContentController {

    private final ContentGenerateService contentService;
    private final CreditService creditService;

    /**
     * 生成内容（异步任务模式）
     * POST /api/content/generate
     * body: { module: "xhs", params: { theme: "rain", tone: "emotional", ... } }
     * 返回 taskId，前端轮询 GET /api/content/task/{taskId}
     */
    @PostMapping("/generate")
    public R<Map<String, Object>> generate(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContext.get();
        String module = (String) body.get("module");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) body.getOrDefault("params", Map.of());

        // 扣减算力（免费模块不扣）
        int balance = creditService.deduct(tenantId, module, "AI生成: " + module);

        // 提交异步生成任务
        Long taskId = contentService.submitTask(tenantId, module, params);

        return R.ok(Map.of("taskId", taskId, "balance", balance));
    }

    /**
     * 查询生成任务状态
     */
    @GetMapping("/task/{taskId}")
    public R<Map<String, Object>> getTask(@PathVariable Long taskId) {
        Long tenantId = TenantContext.get();
        return R.ok(contentService.getTaskResult(tenantId, taskId));
    }

    /**
     * 智慧大脑流式对话 (SSE)
     */
    @PostMapping("/brain/chat")
    public R<Map<String, Object>> brainChat(@RequestBody Map<String, String> body) {
        Long tenantId = TenantContext.get();
        String message = body.get("message");
        return R.ok(contentService.brainChat(tenantId, message));
    }
}
