package com.sushijia.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sushijia.common.response.R;
import com.sushijia.repository.entity.AiCallLog;
import com.sushijia.repository.mapper.AiCallLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/ai-call-logs")
@RequiredArgsConstructor
@Slf4j
public class AiCallLogController {

    private final AiCallLogMapper logMapper;

    @GetMapping
    public R<List<AiCallLog>> list(@RequestParam(value = "tenantId", required = false) Long tenantId,
                                   @RequestParam(value = "moduleKey", required = false) String moduleKey,
                                   @RequestParam(value = "status", required = false) String status,
                                   @RequestParam(value = "limit", defaultValue = "100") int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        LambdaQueryWrapper<AiCallLog> query = new LambdaQueryWrapper<AiCallLog>()
            .eq(tenantId != null, AiCallLog::getTenantId, tenantId)
            .eq(moduleKey != null && !moduleKey.isBlank(), AiCallLog::getModuleKey, moduleKey)
            .eq(status != null && !status.isBlank(), AiCallLog::getStatus, status)
            .orderByDesc(AiCallLog::getCreatedAt)
            .last("LIMIT " + safeLimit);
        try {
            return R.ok(logMapper.selectList(query));
        } catch (DataAccessException e) {
            log.error("AI call log query failed. Please check whether ai_call_logs table is initialized.", e);
            return R.fail(5001, "AI调用日志表未初始化或结构不完整，请同步 init.sql");
        }
    }
}
