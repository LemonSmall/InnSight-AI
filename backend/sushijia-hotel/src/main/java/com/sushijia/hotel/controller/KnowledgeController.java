package com.sushijia.hotel.controller;

import com.sushijia.common.response.R;
import com.sushijia.framework.tenant.TenantContext;
import com.sushijia.hotel.service.KnowledgeService;
import com.sushijia.repository.entity.HotelKnowledgeExtractJob;
import com.sushijia.repository.entity.HotelKnowledgeFile;
import com.sushijia.repository.entity.HotelKnowledgeItem;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    @PostMapping("/text")
    public R<Map<String, Object>> submitText(@RequestBody Map<String, String> body,
                                             HttpServletRequest request) {
        Long tenantId = TenantContext.get();
        Long userId = (Long) request.getAttribute("staffId");
        return R.ok(knowledgeService.submitTextUpdate(tenantId, userId, body.get("content")));
    }

    @PostMapping("/files")
    public R<Map<String, Object>> uploadFile(@RequestParam("file") MultipartFile file,
                                             HttpServletRequest request) {
        return R.ok(knowledgeService.uploadFile(
            TenantContext.get(), (Long) request.getAttribute("staffId"), file));
    }

    @PostMapping("/jobs/{jobId}/confirm")
    public R<Map<String, Object>> confirm(@PathVariable("jobId") Long jobId, HttpServletRequest request) {
        Long tenantId = TenantContext.get();
        Long userId = (Long) request.getAttribute("staffId");
        return R.ok(knowledgeService.confirmExtractJob(tenantId, userId, jobId));
    }

    @PostMapping("/jobs/{jobId}/cancel")
    public R<String> cancel(@PathVariable("jobId") Long jobId, HttpServletRequest request) {
        knowledgeService.cancelExtractJob(TenantContext.get(), (Long) request.getAttribute("staffId"), jobId);
        return R.ok("Cancelled");
    }

    @PutMapping("/items/{itemId}")
    public R<HotelKnowledgeItem> update(@PathVariable("itemId") Long itemId,
                                        @RequestBody Map<String, Object> body,
                                        HttpServletRequest request) {
        return R.ok(knowledgeService.updateKnowledge(
            TenantContext.get(), (Long) request.getAttribute("staffId"), itemId, body));
    }

    @DeleteMapping("/items/{itemId}")
    public R<String> delete(@PathVariable("itemId") Long itemId, HttpServletRequest request) {
        knowledgeService.archiveKnowledge(TenantContext.get(), (Long) request.getAttribute("staffId"), itemId);
        return R.ok("Archived");
    }

    @GetMapping("/items")
    public R<List<HotelKnowledgeItem>> listItems(@RequestParam(value = "category", required = false) String category,
                                                 @RequestParam(value = "limit", defaultValue = "100") int limit) {
        Long tenantId = TenantContext.get();
        return R.ok(knowledgeService.listKnowledge(tenantId, category, limit));
    }

    @GetMapping("/jobs/pending")
    public R<List<HotelKnowledgeExtractJob>> pendingJobs() {
        Long tenantId = TenantContext.get();
        return R.ok(knowledgeService.listPendingJobs(tenantId));
    }

    @GetMapping("/files")
    public R<List<HotelKnowledgeFile>> files(@RequestParam(value = "limit", defaultValue = "50") int limit) {
        return R.ok(knowledgeService.listFiles(TenantContext.get(), limit));
    }
}
