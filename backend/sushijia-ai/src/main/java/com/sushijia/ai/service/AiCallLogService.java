package com.sushijia.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sushijia.repository.entity.AiAgentBinding;
import com.sushijia.repository.entity.AiCallLog;
import com.sushijia.repository.mapper.AiCallLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiCallLogService {

    private static final int SUMMARY_LIMIT = 1000;

    private final AiCallLogMapper logMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void record(String moduleKey,
                       AiAgentBinding binding,
                       String endpoint,
                       String requestId,
                       Map<String, Object> context,
                       String requestSummary,
                       String responseSummary,
                       String status,
                       Integer httpStatus,
                       long durationMs,
                       String errorCode,
                       String errorMessage) {
        try {
            AiCallLog log = new AiCallLog();
            log.setTenantId(parseLong(context == null ? null : context.get("tenantId")));
            log.setUserId(parseLong(context == null ? null : context.get("userId")));
            log.setTaskId(parseLong(context == null ? null : context.get("taskId")));
            log.setModuleKey(moduleKey);
            log.setProvider(binding == null ? "dify" : defaultString(binding.getProvider(), "dify"));
            log.setAppName(binding == null ? "" : firstNotBlank(binding.getAppName(), binding.getBotName()));
            log.setAppType(binding == null ? "" : firstNotBlank(binding.getAppType(), binding.getBotId()));
            log.setEndpoint(endpoint);
            log.setRequestId(requestId);
            log.setStatus(status);
            log.setHttpStatus(httpStatus);
            log.setDurationMs((int) Math.min(durationMs, Integer.MAX_VALUE));
            log.setInputTokens(extractInt(responseSummary, "prompt_tokens"));
            log.setOutputTokens(extractInt(responseSummary, "completion_tokens"));
            log.setCreditsCost(valueOrZero(parseInteger(context == null ? null : context.get("estimatedCredits"))));
            log.setErrorCode(truncate(errorCode, 100));
            log.setErrorMessage(truncate(errorMessage, 500));
            log.setRequestSummary(truncate(requestSummary, SUMMARY_LIMIT));
            log.setResponseSummary(truncate(responseSummary, SUMMARY_LIMIT));
            log.setCreatedAt(LocalDateTime.now());
            logMapper.insert(log);
        } catch (Exception e) {
            log.warn("Failed to write AI call log. module={}, requestId={}", moduleKey, requestId, e);
        }
    }

    private Integer extractInt(String json, String key) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode usage = root.path("metadata").path("usage");
            JsonNode value = usage.path(key);
            return value.isNumber() ? value.asInt() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseInteger(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String firstNotBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : defaultString(second, "");
    }

    private String defaultString(String value, String defaultValue) {
        return value == null ? defaultValue : value;
    }

    private String truncate(String value, int limit) {
        if (value == null) {
            return null;
        }
        return value.length() <= limit ? value : value.substring(0, limit);
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }
}
