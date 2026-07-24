package com.sushijia.hotel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sushijia.repository.entity.AiGenerationHistory;
import com.sushijia.repository.entity.AiGenerationAsset;
import com.sushijia.repository.entity.AiAgentBinding;
import com.sushijia.repository.entity.HotelKnowledgeItem;
import com.sushijia.repository.entity.ContentResult;
import com.sushijia.repository.entity.ContentTask;
import com.sushijia.repository.entity.UserRecentPreset;
import com.sushijia.repository.mapper.AiGenerationHistoryMapper;
import com.sushijia.repository.mapper.AiGenerationAssetMapper;
import com.sushijia.repository.mapper.AiAgentBindingMapper;
import com.sushijia.repository.mapper.HotelKnowledgeItemMapper;
import com.sushijia.repository.mapper.ContentResultMapper;
import com.sushijia.repository.mapper.ContentTaskMapper;
import com.sushijia.repository.mapper.UserRecentPresetMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class GenerationHistoryService {

    private final AiGenerationHistoryMapper historyMapper;
    private final AiGenerationAssetMapper assetMapper;
    private final AiAgentBindingMapper agentBindingMapper;
    private final HotelKnowledgeItemMapper knowledgeItemMapper;
    private final UserRecentPresetMapper recentPresetMapper;
    private final ContentTaskMapper contentTaskMapper;
    private final ContentResultMapper contentResultMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Pattern MARKDOWN_IMAGE = Pattern.compile("!\\[[^]]*]\\(((?:https?://|/api/public/ai-images/)[^)\\s]+)\\)");
    private static final Pattern IMAGE_URL = Pattern.compile("(?:https?://[^\\s\\\"'<>]+?|/api/public/ai-images/[^\\s\\\"'<>]+?)\\.(?:png|jpe?g|webp|gif)(?:\\?[^\\s\\\"'<>]*)?", Pattern.CASE_INSENSITIVE);

    public Long createProcessing(Long tenantId,
                                 Long userId,
                                 String moduleKey,
                                 Map<String, Object> params,
                                 String requestId) {
        AiAgentBinding binding = agentBindingMapper.findEnabledByModuleKey(moduleKey);
        List<HotelKnowledgeItem> knowledge = knowledgeItemMapper.findActiveKnowledge(tenantId, 30);
        List<Map<String, Object>> references = new ArrayList<>();
        for (HotelKnowledgeItem item : knowledge) {
            references.add(Map.of(
                "id", item.getId(),
                "title", item.getTitle(),
                "updatedAt", item.getUpdatedAt() == null ? "" : item.getUpdatedAt().toString()
            ));
        }

        AiGenerationHistory history = new AiGenerationHistory();
        history.setTenantId(tenantId);
        history.setUserId(userId);
        history.setModuleKey(moduleKey);
        history.setTitle(buildTitle(moduleKey, params));
        history.setPrompt(promptFromParams(params));
        history.setInputParams(historyParamsJson(params));
        history.setOutputContent("");
        history.setOutputAssets("[]");
        history.setProviderKey("dify");
        history.setAgentBindingId(binding == null ? null : binding.getId());
        history.setAgentName(binding == null ? "" : binding.getAppName());
        history.setRequestId(requestId);
        history.setKnowledgeRefs(toJson(references));
        history.setCostCredits(0);
        history.setStatus("processing");
        history.setCreatedAt(LocalDateTime.now());
        historyMapper.insert(history);
        return history.getId();
    }

    public void complete(Long historyId,
                         Long tenantId,
                         Long userId,
                         String moduleKey,
                         Map<String, Object> params,
                         String outputContent,
                         Integer costCredits,
                         long startedAt) {
        AiGenerationHistory history = historyMapper.selectById(historyId);
        if (history == null || !tenantId.equals(history.getTenantId())) return;
        List<String> urls = extractAssetUrls(outputContent);
        history.setOutputContent(outputContent == null ? "" : outputContent);
        history.setOutputAssets(toJson(urls));
        history.setCostCredits(costCredits == null ? 0 : costCredits);
        history.setStatus("success");
        history.setDurationMs((int) Math.min(Integer.MAX_VALUE, Math.max(0, System.currentTimeMillis() - startedAt)));
        history.setCompletedAt(LocalDateTime.now());
        historyMapper.updateById(history);

        for (String url : urls) {
            AiGenerationAsset asset = new AiGenerationAsset();
            asset.setTenantId(tenantId);
            asset.setGenerationId(historyId);
            asset.setAssetType("image");
            asset.setUrl(url);
            asset.setStoragePath("");
            asset.setMetadataJson(toJson(Map.of("source", "ai_output")));
            asset.setCreatedAt(LocalDateTime.now());
            assetMapper.insert(asset);
        }
        saveRecentPreset(tenantId, userId, moduleKey, params);
    }

    public void fail(Long historyId, Long tenantId, String errorMessage, long startedAt) {
        AiGenerationHistory history = historyMapper.selectById(historyId);
        if (history == null || !tenantId.equals(history.getTenantId())) return;
        history.setStatus("failed");
        history.setErrorMsg(errorMessage);
        history.setDurationMs((int) Math.min(Integer.MAX_VALUE, Math.max(0, System.currentTimeMillis() - startedAt)));
        history.setCompletedAt(LocalDateTime.now());
        historyMapper.updateById(history);
    }

    public void saveFromContentTask(Long tenantId, Long userId, String moduleKey, Map<String, Object> params, Long taskId) {
        ContentTask task = contentTaskMapper.selectById(taskId);
        if (task == null || !tenantId.equals(task.getTenantId())) {
            return;
        }
        ContentResult result = task.getResultId() == null ? null : contentResultMapper.selectById(task.getResultId());

        AiGenerationHistory history = new AiGenerationHistory();
        history.setTenantId(tenantId);
        history.setUserId(userId);
        history.setModuleKey(moduleKey);
        history.setTitle(buildTitle(moduleKey, params));
        history.setPrompt(promptFromParams(params));
        history.setInputParams(historyParamsJson(params));
        history.setOutputContent(result == null ? "" : result.getContent());
        history.setOutputAssets("[]");
        history.setProviderKey("dify");
        history.setCostCredits(task.getCostCredits() == null ? 0 : task.getCostCredits());
        history.setStatus("done".equals(task.getStatus()) ? "success" : task.getStatus());
        history.setErrorMsg(task.getErrorMsg());
        history.setCreatedAt(task.getCreatedAt());
        history.setCompletedAt(task.getCompletedAt());
        historyMapper.insert(history);

        saveRecentPreset(tenantId, userId, moduleKey, params);
    }

    public Long saveDirect(Long tenantId,
                           Long userId,
                           String moduleKey,
                           Map<String, Object> params,
                           String outputContent,
                           String providerKey,
                           Integer costCredits,
                           String status,
                           String errorMsg) {
        AiGenerationHistory history = new AiGenerationHistory();
        LocalDateTime now = LocalDateTime.now();
        history.setTenantId(tenantId);
        history.setUserId(userId);
        history.setModuleKey(moduleKey);
        history.setTitle(buildTitle(moduleKey, params));
        history.setPrompt(promptFromParams(params));
        history.setInputParams(historyParamsJson(params));
        history.setOutputContent(outputContent == null ? "" : outputContent);
        history.setOutputAssets("[]");
        history.setProviderKey(providerKey == null ? "dify" : providerKey);
        history.setCostCredits(costCredits == null ? 0 : costCredits);
        history.setStatus(status == null ? "success" : status);
        history.setErrorMsg(errorMsg);
        history.setCreatedAt(now);
        history.setCompletedAt(now);
        historyMapper.insert(history);

        if ("success".equals(history.getStatus())) {
            saveRecentPreset(tenantId, userId, moduleKey, params);
        }
        return history.getId();
    }

    public List<AiGenerationHistory> listRecent(Long tenantId, Long userId, String moduleKey, int limit) {
        return historyMapper.findRecent(tenantId, userId, moduleKey, Math.max(1, Math.min(limit, 200)));
    }

    public AiGenerationHistory detail(Long tenantId, Long userId, String moduleKey, Long id) {
        if (tenantId == null || userId == null || id == null || id <= 0) {
            return null;
        }
        AiGenerationHistory history = historyMapper.selectById(id);
        if (history == null) {
            return null;
        }
        if (!tenantId.equals(history.getTenantId()) || !userId.equals(history.getUserId())) {
            return null;
        }
        if (moduleKey != null && !moduleKey.isBlank() && !moduleKey.equals(history.getModuleKey())) {
            return null;
        }
        return history;
    }

    public UserRecentPreset latestPreset(Long tenantId, Long userId, String moduleKey) {
        if (userId == null) {
            return null;
        }
        return recentPresetMapper.findLatest(tenantId, userId, moduleKey);
    }

    public void delete(Long tenantId, Long userId, List<Long> ids) {
        if (tenantId == null || userId == null || ids == null || ids.isEmpty()) {
            return;
        }
        List<Long> safeIds = ids.stream()
            .filter(id -> id != null && id > 0)
            .distinct()
            .limit(200)
            .toList();
        if (safeIds.isEmpty()) {
            return;
        }
        assetMapper.delete(new LambdaQueryWrapper<AiGenerationAsset>()
            .eq(AiGenerationAsset::getTenantId, tenantId)
            .in(AiGenerationAsset::getGenerationId, safeIds));
        historyMapper.delete(new LambdaQueryWrapper<AiGenerationHistory>()
            .eq(AiGenerationHistory::getTenantId, tenantId)
            .eq(AiGenerationHistory::getUserId, userId)
            .in(AiGenerationHistory::getId, safeIds));
    }

    private void saveRecentPreset(Long tenantId, Long userId, String moduleKey, Map<String, Object> params) {
        if (userId == null || params == null) {
            return;
        }
        UserRecentPreset preset = recentPresetMapper.findLatest(tenantId, userId, moduleKey);
        if (preset == null) {
            preset = new UserRecentPreset();
            preset.setTenantId(tenantId);
            preset.setUserId(userId);
            preset.setModuleKey(moduleKey);
            preset.setPresetName("最近使用");
            preset.setCreatedAt(LocalDateTime.now());
        }
        preset.setParamsJson(historyParamsJson(params));
        preset.setLastUsedAt(LocalDateTime.now());
        preset.setUpdatedAt(LocalDateTime.now());
        if (preset.getId() == null) {
            recentPresetMapper.insert(preset);
        } else {
            recentPresetMapper.updateById(preset);
        }
    }

    private String buildTitle(String moduleKey, Map<String, Object> params) {
        String theme = params == null ? "" : String.valueOf(params.getOrDefault("theme", ""));
        if (theme == null || theme.isBlank()) {
            return moduleKey + " 生成";
        }
        return theme.length() > 40 ? theme.substring(0, 40) : theme;
    }

    private String promptFromParams(Map<String, Object> params) {
        if (params == null) return "";
        for (String key : List.of("message", "userQuestion", "theme", "customTopic", "title", "content", "sellingPoints", "reviewText", "additionalNotes", "note")) {
            Object value = params.get(key);
            if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value);
        }
        return "";
    }

    private List<String> extractAssetUrls(String content) {
        if (content == null || content.isBlank()) return List.of();
        LinkedHashMap<String, Boolean> urls = new LinkedHashMap<>();
        Matcher markdown = MARKDOWN_IMAGE.matcher(content);
        while (markdown.find()) urls.put(markdown.group(1), true);
        Matcher direct = IMAGE_URL.matcher(content);
        while (direct.find()) urls.put(direct.group(), true);
        return new ArrayList<>(urls.keySet());
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String historyParamsJson(Map<String, Object> params) {
        return toJson(sanitizeHistoryValue(params));
    }

    @SuppressWarnings("unchecked")
    private Object sanitizeHistoryValue(Object value) {
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> cleaned = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (shouldDropHistoryParam(key, entry.getValue())) {
                    continue;
                }
                cleaned.put(key, sanitizeHistoryValue(entry.getValue()));
            }
            return cleaned;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::sanitizeHistoryValue).toList();
        }
        return value;
    }

    private boolean shouldDropHistoryParam(String key, Object value) {
        if (key == null) {
            return true;
        }
        String normalized = key.trim();
        if (normalized.startsWith("_dify")) {
            return true;
        }
        String lower = normalized.toLowerCase();
        if (lower.contains("base64")) {
            return true;
        }
        if ("imageData".equals(normalized) && value != null && String.valueOf(value).length() > 2000) {
            return true;
        }
        return false;
    }
}
