package com.sushijia.hotel.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sushijia.ai.client.ModelScopeImageClient;
import com.sushijia.ai.engine.PromptEngine;
import com.sushijia.ai.service.AiInvocationService;
import com.sushijia.common.exception.BizException;
import com.sushijia.common.response.ResultCode;
import com.sushijia.framework.tenant.TenantContext;
import com.sushijia.repository.entity.ContentResult;
import com.sushijia.repository.entity.ContentTask;
import com.sushijia.repository.entity.HotelKnowledgeItem;
import com.sushijia.repository.entity.PromptTemplate;
import com.sushijia.repository.entity.RoomType;
import com.sushijia.repository.entity.StyleLibrary;
import com.sushijia.repository.entity.Tenant;
import com.sushijia.repository.mapper.ContentResultMapper;
import com.sushijia.repository.mapper.ContentTaskMapper;
import com.sushijia.repository.mapper.HotelKnowledgeItemMapper;
import com.sushijia.repository.mapper.PromptTemplateMapper;
import com.sushijia.repository.mapper.RoomTypeMapper;
import com.sushijia.repository.mapper.StyleMapper;
import com.sushijia.repository.mapper.TenantMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentGenerateService {

    private final ContentTaskMapper taskMapper;
    private final ContentResultMapper resultMapper;
    private final TenantMapper tenantMapper;
    private final RoomTypeMapper roomTypeMapper;
    private final PromptTemplateMapper promptTemplateMapper;
    private final StyleMapper styleMapper;
    private final HotelKnowledgeItemMapper knowledgeItemMapper;
    private final AiInvocationService aiInvocationService;
    private final ModelScopeImageClient modelScopeImageClient;
    private final CreditService creditService;
    private final SubscriptionService subscriptionService;
    private final GenerationHistoryService generationHistoryService;
    private final ApplicationContext applicationContext;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Long submitTask(Long tenantId, Long userId, String moduleKey, Map<String, Object> params) {
        Tenant tenant = tenantMapper.selectById(tenantId);
        ContentTask task = new ContentTask();
        task.setTenantId(tenantId);
        task.setUserId(userId);
        task.setModuleKey(moduleKey);
        try {
            task.setInputParams(objectMapper.writeValueAsString(params));
        } catch (JsonProcessingException e) {
            throw new BizException(ResultCode.BAD_REQUEST, "请求参数无效");
        }
        task.setStatus("pending");
        task.setCreatedAt(LocalDateTime.now());
        Long historyId = generationHistoryService.createProcessing(
            tenantId, userId, moduleKey, params, UUID.randomUUID().toString());
        task.setGenerationHistoryId(historyId);
        taskMapper.insert(task);
        applicationContext.getBean(ContentGenerateService.class)
            .executeTask(task.getId(), tenantId, userId, moduleKey, params, tenant);
        return task.getId();
    }

    public Map<String, Object> generateNow(Long tenantId, Long userId, String moduleKey, Map<String, Object> params) {
        TenantContext.set(tenantId);
        long startedAt = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();
        Long historyId = generationHistoryService.createProcessing(tenantId, userId, moduleKey, params, requestId);
        try {
            Tenant tenant = tenantMapper.selectById(tenantId);
            subscriptionService.ensureModuleEnabled(tenantId, moduleKey);
            creditService.ensureCanAfford(tenantId, moduleKey);

            int estimatedCredits = creditService.getModuleCost(moduleKey);
            String content = sanitizeAiOutput(generateWithAi(moduleKey, params, tenant, null, userId, null, null, requestId));
            creditService.deduct(tenantId, moduleKey, "AI generation: " + moduleKey);
            generationHistoryService.complete(historyId, tenantId, userId, moduleKey, params, content, estimatedCredits, startedAt);

            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("content", content);
            result.put("generationId", historyId);
            result.put("costCredits", estimatedCredits);
            result.put("balance", creditService.queryBalance(tenantId));
            return result;
        } catch (Exception e) {
            generationHistoryService.fail(historyId, tenantId, toUserSafeError(e), startedAt);
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new BizException(ResultCode.AI_GENERATE_FAILED, "AI 调用失败");
        } finally {
            TenantContext.clear();
        }
    }

    public Map<String, Object> generateStream(Long tenantId,
                                              Long userId,
                                              String moduleKey,
                                              Map<String, Object> params,
                                              Consumer<String> onStatus,
                                              Consumer<String> onChunk) {
        TenantContext.set(tenantId);
        long startedAt = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();
        Long historyId = generationHistoryService.createProcessing(tenantId, userId, moduleKey, params, requestId);
        try {
            Tenant tenant = tenantMapper.selectById(tenantId);
            subscriptionService.ensureModuleEnabled(tenantId, moduleKey);
            creditService.ensureCanAfford(tenantId, moduleKey);

            int estimatedCredits = creditService.getModuleCost(moduleKey);
            String content = sanitizeAiOutput(generateWithAi(moduleKey, params, tenant, null, userId, onStatus, onChunk, requestId));
            creditService.deduct(tenantId, moduleKey, "AI generation: " + moduleKey);
            generationHistoryService.complete(historyId, tenantId, userId, moduleKey, params, content, estimatedCredits, startedAt);

            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("content", content);
            result.put("generationId", historyId);
            result.put("costCredits", estimatedCredits);
            result.put("balance", creditService.queryBalance(tenantId));
            return result;
        } catch (Exception e) {
            generationHistoryService.fail(historyId, tenantId, toUserSafeError(e), startedAt);
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new BizException(ResultCode.AI_GENERATE_FAILED, "AI 调用失败");
        } finally {
            TenantContext.clear();
        }
    }

    @Async
    public void executeTask(Long taskId, Long tenantId, Long userId, String moduleKey,
                            Map<String, Object> params, Tenant tenant) {
        TenantContext.set(tenantId);
        try {
            ContentTask task = taskMapper.selectById(taskId);
            task.setStatus("processing");
            taskMapper.updateById(task);

            int estimatedCredits = creditService.getModuleCost(moduleKey);
            String content = sanitizeAiOutput(generateWithAi(moduleKey, params, tenant, taskId, userId, null, null, null));
            creditService.deduct(tenantId, moduleKey, "AI generation: " + moduleKey);

            ContentResult result = new ContentResult();
            result.setTaskId(taskId);
            result.setContent(content);
            result.setCreatedAt(LocalDateTime.now());
            resultMapper.insert(result);

            task.setStatus("done");
            task.setResultId(result.getId());
            task.setCostCredits(estimatedCredits);
            task.setCompletedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            generationHistoryService.complete(
                task.getGenerationHistoryId(), tenantId, userId, moduleKey, params,
                content, estimatedCredits, task.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());

            log.info("AI content generation completed. tenant={}, module={}, taskId={}", tenantId, moduleKey, taskId);
        } catch (Exception e) {
            handleTaskError(taskId, e);
            ContentTask failedTask = taskMapper.selectById(taskId);
            if (failedTask != null && failedTask.getGenerationHistoryId() != null) {
                generationHistoryService.fail(
                    failedTask.getGenerationHistoryId(), tenantId, toUserSafeError(e),
                    failedTask.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
            }
        } finally {
            TenantContext.clear();
        }
    }

    public Map<String, Object> getTaskResult(Long tenantId, Long userId, Long taskId) {
        ContentTask task = taskMapper.selectById(taskId);
        if (task == null || !task.getTenantId().equals(tenantId)
            || task.getUserId() == null || !task.getUserId().equals(userId)) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("taskId", task.getId());
        result.put("generationId", task.getGenerationHistoryId());
        result.put("status", task.getStatus());
        if ("done".equals(task.getStatus()) && task.getResultId() != null) {
            ContentResult cr = resultMapper.selectById(task.getResultId());
            if (cr != null) {
                result.put("content", sanitizeAiOutput(cr.getContent()));
            }
        }
        if ("failed".equals(task.getStatus()) && task.getErrorMsg() != null) {
            result.put("errorMsg", task.getErrorMsg());
        }
        return result;
    }

    private String generateWithAi(String moduleKey, Map<String, Object> params, Tenant tenant, Long taskId) {
        return generateWithAi(moduleKey, params, tenant, taskId, null, null, null, null);
    }

    private String generateWithAi(String moduleKey,
                                  Map<String, Object> params,
                                  Tenant tenant,
                                  Long taskId,
                                  Long userId,
                                  Consumer<String> onStatus,
                                  Consumer<String> onChunk,
                                  String requestedRequestId) {
        PromptTemplate template = promptTemplateMapper.selectOne(
            new LambdaQueryWrapper<PromptTemplate>()
                .eq(PromptTemplate::getModuleKey, moduleKey)
                .eq(PromptTemplate::getStatus, "production")
                .orderByDesc(PromptTemplate::getVersion)
                .last("LIMIT 1"));

        String styleSegment = loadStyleForModule(moduleKey, tenant == null ? null : tenant.getId());
        Map<String, Object> sourceParams = params == null
            ? new LinkedHashMap<String, Object>()
            : new LinkedHashMap<String, Object>(params);
        Map<String, Object> visibleRuntimeParams = visibleBusinessParams(sourceParams);
        Map<String, String> variables = buildVariables(tenant, visibleRuntimeParams);
        String templateContent = template != null ? template.getContent() : getDefaultTemplate(moduleKey);
        String renderedTemplate = PromptEngine.render(templateContent, variables);

        StringBuilder system = new StringBuilder();
        system.append("你是专业的酒店/民宿 AI 运营助手。");
        if (!isBlank(styleSegment)) {
            system.append("\n写作风格要求：\n").append(styleSegment);
        }
        system.append("\n只能使用真实酒店资料。不得虚构设施、价格、距离、活动、奖项或政策。");
        system.append("\n平台未接入 PMS、OTA 或订单系统，不得声称掌握实时入住率、可售房、订单、营收、RevPAR 或未来房态。");
        system.append("\n缺少经营数据时，应列出需要酒店人工确认的信息，并给出条件化建议，不得自行补造数字。");
        system.append("\n只输出给酒店经营者看的最终内容，禁止输出推理过程、调试信息和英文建议问题。");
        system.append("\n模板要求：\n").append(renderedTemplate);
        String runtimeInstruction = moduleRuntimeInstruction(moduleKey);
        if (!isBlank(runtimeInstruction)) {
            system.append("\n当前模块强制业务规则：\n").append(runtimeInstruction);
        }

        String userPrompt = buildUserPrompt(moduleKey, visibleRuntimeParams);
        Long tenantId = tenant == null ? null : tenant.getId();
        boolean skipKnowledge = "occupancy_image".equalsIgnoreCase(moduleKey);
        List<Map<String, Object>> knowledge = skipKnowledge ? List.of() : loadKnowledgeSnapshot(tenantId);
        List<RoomType> rooms = loadTenantRooms(tenantId);
        String requestId = isBlank(requestedRequestId) ? UUID.randomUUID().toString() : requestedRequestId;
        Map<String, Object> commonContext = buildCommonContext(tenant, rooms, moduleKey, userId, requestId);
        Map<String, Object> knowledgeContext = buildKnowledgeContext(knowledge, params, userPrompt);
        String confirmedKnowledgeText = buildConfirmedKnowledgeText(knowledge);
        Map<String, Object> runtimeParams = sourceParams;
        if (!skipKnowledge && !isBlank(confirmedKnowledgeText)) {
            system.append("\n\n已确认酒店知识库规则：\n")
                .append("以下内容来自当前租户已确认、启用且未过期的本店知识库。回答时必须优先使用；如果用户问题与其中任一条直接相关，不得回答“不知道”。\n")
                .append(confirmedKnowledgeText);
            userPrompt = userPrompt
                + "\n\n【本店已确认知识库】\n"
                + confirmedKnowledgeText
                + "\n请优先根据以上已确认知识回答当前问题。";
        }

        Map<String, Object> context = new LinkedHashMap<String, Object>();
        context.put("tenantId", tenantId);
        context.put("userId", userId);
        context.put("requestId", requestId);
        context.put("taskId", taskId);
        context.put("moduleKey", moduleKey);
        context.put("hotelName", tenant == null ? "" : tenant.getName());
        context.put("tenant", tenant);
        context.put("rooms", rooms);
        context.put("params", runtimeParams);
        context.put("variables", variables);
        context.put("styleSegment", styleSegment);
        context.put("renderedTemplate", renderedTemplate);
        context.put("knowledge", knowledge);
        context.put("confirmedKnowledgeText", confirmedKnowledgeText);
        context.put("hotelContext", commonContext.get("hotel"));
        context.put("commonContext", commonContext);
        context.put("commonContextJson", safeJson(commonContext));
        context.put("businessParamsJson", safeJson(visibleRuntimeParams));
        context.put("knowledgeContextJson", safeJson(knowledgeContext));
        context.put("selectedParamsJson", safeJson(visibleRuntimeParams));
        context.put("currentTime", LocalDateTime.now().toString());
        String promptMessage = extractPromptMessage(runtimeParams, userPrompt);
        context.put("message", promptMessage);
        context.put("userQuestion", promptMessage);
        context.put("theme", String.valueOf(runtimeParams.getOrDefault("theme", "")));
        context.put("tone", String.valueOf(runtimeParams.getOrDefault("tone", "")));
        context.put("roomSnapshot", runtimeParams.getOrDefault("roomSnapshot", rooms));
        context.put("estimatedCredits", creditService.getModuleCost(moduleKey));

        emitStatus(onStatus, statusBeforeFinalAgent(moduleKey, context));
        log.info("Forwarding request directly to AI agent. module={}, requestId={}, webSearchEnabled={}, message={}",
            moduleKey,
            requestId,
            webSearchEnabled(runtimeParams),
            promptMessage);

        if (isDirectPosterImage(moduleKey, runtimeParams)) {
            String promptResponse = aiInvocationService.invoke(
                tenantId, moduleKey, system.toString(), userPrompt, context);
            String imagePrompt = extractPosterPrompt(promptResponse);
            if (isBlank(imagePrompt)) {
                throw new BizException(ResultCode.AI_GENERATE_FAILED, "海报提示词生成失败");
            }
            Map<String, Object> imageParams = runtimeParams == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(runtimeParams);
            imageParams.put("imagePromptOverride", imagePrompt);
            return modelScopeImageClient.generateImage(imageParams, context);
        }

        String response;
        if (onChunk == null) {
            response = aiInvocationService.invoke(tenantId, moduleKey, system.toString(), userPrompt, context);
        } else {
            response = aiInvocationService.stream(tenantId, moduleKey, system.toString(), userPrompt, context, onChunk);
        }

        if (isXhsImageRequested(moduleKey, runtimeParams)) {
            if (!modelScopeImageClient.isConfigured()) {
                response = mergeXhsImageError(response, "image_service_not_configured", "ModelScope image service is not configured");
            } else {
                emitStatus(onStatus, "正在生成配图");
                response = appendXhsImageIfPossible(response, runtimeParams, context);
            }
        }
        return response;
    }

    private boolean isDirectPosterImage(String moduleKey, Map<String, Object> params) {
        if (!"poster".equals(moduleKey) || params == null) {
            return false;
        }
        String provider = String.valueOf(params.getOrDefault("imageProvider", ""));
        Object useBackendImage = params.get("useBackendImage");
        return "backend_modelscope".equalsIgnoreCase(provider)
            || "modelscope_backend".equalsIgnoreCase(provider)
            || Boolean.TRUE.equals(useBackendImage);
    }

    private Map<String, Object> visibleBusinessParams(Map<String, Object> params) {
        Map<String, Object> visible = new LinkedHashMap<>();
        if (params == null) {
            return visible;
        }
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.startsWith("_dify")) {
                continue;
            }
            visible.put(key, entry.getValue());
        }
        return visible;
    }

    private boolean shouldGenerateXhsImage(String moduleKey, Map<String, Object> params) {
        return isXhsImageRequested(moduleKey, params) && modelScopeImageClient.isConfigured();
    }

    private boolean isXhsImageRequested(String moduleKey, Map<String, Object> params) {
        if (!"xhs".equals(moduleKey) || params == null) {
            return false;
        }
        Object withImage = params.get("withImage");
        if (withImage instanceof Boolean bool) {
            return bool;
        }
        return "true".equalsIgnoreCase(String.valueOf(withImage));
    }

    private String appendXhsImageIfPossible(String response,
                                            Map<String, Object> params,
                                            Map<String, Object> context) {
        String imagePrompt = extractXhsImagePrompt(response);
        if (isBlank(imagePrompt)) {
            return mergeXhsImageError(response, "image_prompt_missing", "AI did not return image_prompt or imageSuggestions");
        }
        Map<String, Object> imageParams = new LinkedHashMap<>(params);
        imageParams.put("imagePromptOverride", imagePrompt);
        imageParams.putIfAbsent("prompt", imagePrompt);
        String content = extractXhsContent(response);
        if (!isBlank(content)) {
            imageParams.putIfAbsent("content", content);
        }
        try {
            String imageUrl = modelScopeImageClient.generateImage(imageParams, context);
            return mergeXhsImageResult(response, imageUrl, imagePrompt);
        } catch (Exception e) {
            return mergeXhsImageError(response, "image_generation_failed", toUserSafeError(e));
        }
    }

    private String extractXhsImagePrompt(String response) {
        JsonNode node = readJsonNode(response);
        if (node == null) {
            return "";
        }
        for (String key : List.of("image_prompt", "imagePrompt", "imageSuggestion", "image_suggestion")) {
            String value = textValue(node.path(key));
            if (!isBlank(value)) {
                return value;
            }
        }
        JsonNode suggestions = firstPresentNode(node, "imageSuggestions", "image_suggestions");
        if (suggestions != null && suggestions.isArray()) {
            for (JsonNode item : suggestions) {
                String value = textValue(item);
                if (!isBlank(value)) {
                    return value;
                }
            }
        }
        return "";
    }

    private String extractXhsContent(String response) {
        JsonNode node = readJsonNode(response);
        if (node == null) {
            return "";
        }
        for (String key : List.of("content", "body", "text", "summary")) {
            String value = textValue(node.path(key));
            if (!isBlank(value)) {
                return value;
            }
        }
        return "";
    }

    private String mergeXhsImageResult(String response, String imageUrl, String imagePrompt) {
        JsonNode node = readJsonNode(response);
        if (node instanceof com.fasterxml.jackson.databind.node.ObjectNode objectNode) {
            objectNode.put("imageStatus", "success");
            objectNode.put("image_status", "success");
            objectNode.put("imageUrl", imageUrl);
            objectNode.put("image_url", imageUrl);
            objectNode.putArray("imageUrls").add(imageUrl);
            objectNode.putArray("image_urls").add(imageUrl);
            if (isBlank(textValue(objectNode.path("image_prompt")))) {
                objectNode.put("image_prompt", imagePrompt);
            }
            if (objectNode.path("imageSuggestions").isMissingNode() || !objectNode.path("imageSuggestions").isArray()) {
                objectNode.putArray("imageSuggestions").add(imagePrompt);
            }
            try {
                return objectMapper.writeValueAsString(objectNode);
            } catch (Exception ignored) {
                // Fall back to plain text below.
            }
        }
        return (response == null ? "" : response) + "\n" + imageUrl;
    }

    private String mergeXhsImageError(String response, String code, String message) {
        JsonNode node = readJsonNode(response);
        if (node instanceof com.fasterxml.jackson.databind.node.ObjectNode objectNode) {
            objectNode.put("imageStatus", "failed");
            objectNode.put("image_status", "failed");
            objectNode.put("imageErrorCode", code == null ? "" : code);
            objectNode.put("image_error_code", code == null ? "" : code);
            objectNode.put("imageError", message == null ? "" : message);
            objectNode.put("image_error", message == null ? "" : message);
            try {
                return objectMapper.writeValueAsString(objectNode);
            } catch (Exception ignored) {
                // Fall back to plain text below.
            }
        }
        return (response == null ? "" : response) + "\nimage_status=failed\nimage_error=" + (message == null ? "" : message);
    }

    private JsonNode readJsonNode(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private JsonNode firstPresentNode(JsonNode node, String... keys) {
        if (node == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            JsonNode child = node.path(key);
            if (!child.isMissingNode() && !child.isNull()) {
                return child;
            }
        }
        return null;
    }

    private String textValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        String value = node.isTextual() ? node.asText() : node.toString();
        return value == null ? "" : value.trim();
    }

    private String extractPosterPrompt(String response) {
        String clean = sanitizeAiOutput(response);
        if (isBlank(clean)) return "";
        try {
            var root = objectMapper.readTree(clean);
            for (String key : List.of("prompt", "imagePrompt", "image_prompt")) {
                if (root.hasNonNull(key) && !root.path(key).asText().isBlank()) {
                    return root.path(key).asText().trim();
                }
            }
        } catch (Exception ignored) {
            // A plain prompt is accepted below.
        }
        return clean;
    }

    private List<Map<String, Object>> loadKnowledgeSnapshot(Long tenantId) {
        List<Map<String, Object>> snapshot = new ArrayList<Map<String, Object>>();
        if (tenantId == null) {
            return snapshot;
        }
        String cacheKey = "sushijia:knowledge:snapshot:" + tenantId;
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (!isBlank(cached)) {
                return objectMapper.readValue(cached, List.class);
            }
        } catch (Exception e) {
            log.debug("Knowledge cache is unavailable. tenantId={}", tenantId);
        }
        try {
            List<HotelKnowledgeItem> items = knowledgeItemMapper.findActiveKnowledge(tenantId, 30);
            int totalCharacters = 0;
            for (HotelKnowledgeItem item : items) {
                String content = safe(item.getContent());
                if (content.length() > 1200) content = content.substring(0, 1200);
                if (totalCharacters + content.length() > 16_000) break;
                Map<String, Object> row = new LinkedHashMap<String, Object>();
                row.put("category", item.getCategory());
                row.put("id", item.getId());
                row.put("title", item.getTitle());
                row.put("content", content);
                row.put("sourceType", item.getSourceType());
                row.put("sourceName", item.getSourceName());
                row.put("effectiveFrom", item.getEffectiveFrom());
                row.put("effectiveTo", item.getEffectiveTo());
                row.put("updatedAt", item.getUpdatedAt());
                row.put("confidence", item.getConfidence());
                snapshot.add(row);
                totalCharacters += content.length();
            }
            redisTemplate.opsForValue().set(cacheKey, safeJson(snapshot), 30, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Failed to load hotel knowledge. tenantId={}", tenantId, e);
        }
        return snapshot;
    }

    private List<RoomType> loadTenantRooms(Long tenantId) {
        if (tenantId == null) {
            return new ArrayList<RoomType>();
        }
        String cacheKey = "sushijia:tenant:" + tenantId + ":rooms";
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (!isBlank(cached)) {
                return objectMapper.readValue(cached, new TypeReference<List<RoomType>>() {});
            }
        } catch (Exception e) {
            log.debug("Room cache is unavailable. tenantId={}", tenantId);
        }
        try {
            List<RoomType> rooms = roomTypeMapper.selectList(new LambdaQueryWrapper<RoomType>()
                    .eq(RoomType::getTenantId, tenantId)
                    .eq(RoomType::getEnabled, 1)
                    .orderByAsc(RoomType::getSortOrder)
                    .orderByAsc(RoomType::getId));
            redisTemplate.opsForValue().set(cacheKey, safeJson(rooms), 10, TimeUnit.MINUTES);
            return rooms;
        } catch (Exception e) {
            log.warn("Failed to load room snapshot. tenantId={}", tenantId, e);
            return new ArrayList<RoomType>();
        }
    }

    private Map<String, Object> buildCommonContext(Tenant tenant,
                                                   List<RoomType> rooms,
                                                   String moduleKey,
                                                   Long userId,
                                                   String requestId) {
        Map<String, Object> context = new LinkedHashMap<String, Object>();
        Map<String, Object> hotel = new LinkedHashMap<String, Object>();
        hotel.put("id", tenant == null ? null : tenant.getId());
        hotel.put("name", tenant == null ? "" : safe(tenant.getName()));
        hotel.put("type", tenant == null ? "" : safe(tenant.getType()));
        hotel.put("city", tenant == null ? "" : safe(tenant.getCity()));
        hotel.put("totalRooms", tenant == null ? null : tenant.getTotalRooms());
        hotel.put("tags", tenant == null ? "" : safe(tenant.getTags()));
        hotel.put("targetAudience", tenant == null ? "" : safe(tenant.getTargetAudience()));
        hotel.put("nearby", tenant == null ? "" : safe(tenant.getNearby()));
        hotel.put("realWorldBinding", buildRealWorldBinding(tenant));

        List<Map<String, Object>> roomSnapshot = new ArrayList<Map<String, Object>>();
        if (rooms != null) {
            for (RoomType room : rooms) {
                Map<String, Object> row = new LinkedHashMap<String, Object>();
                row.put("name", room.getName());
                row.put("basePrice", room.getBasePrice());
                row.put("count", room.getCount());
                roomSnapshot.add(row);
            }
        }

        hotel.put("roomTypes", roomSnapshot);

        context.put("schemaVersion", "1.0");
        context.put("requestId", requestId);
        context.put("tenantId", tenant == null ? null : tenant.getId());
        context.put("userId", userId);
        context.put("moduleKey", moduleKey);
        context.put("locale", "zh-CN");
        context.put("currentTime", LocalDateTime.now().toString());
        context.put("hotel", hotel);
        context.put("dataScope", Map.of(
            "source", "tenant_profile_room_types_confirmed_knowledge_only",
            "hasPms", false,
            "hasOtaRealtimeData", false,
            "hasOrderData", false,
            "allowEstimatedMetrics", false
        ));
        return context;
    }

    private Map<String, Object> buildRealWorldBinding(Tenant tenant) {
        Map<String, Object> binding = new LinkedHashMap<String, Object>();
        binding.put("provider", tenant == null ? "" : safe(tenant.getPoiProvider()));
        binding.put("poiId", tenant == null ? "" : safe(tenant.getPoiId()));
        binding.put("verified", tenant != null && Boolean.TRUE.equals(tenant.getPoiVerified()));
        binding.put("name", tenant == null ? "" : safe(tenant.getPoiName()));
        binding.put("address", tenant == null ? "" : safe(tenant.getPoiAddress()));
        binding.put("province", tenant == null ? "" : safe(tenant.getPoiProvince()));
        binding.put("city", tenant == null ? "" : safe(tenant.getPoiCity()));
        binding.put("district", tenant == null ? "" : safe(tenant.getPoiDistrict()));
        binding.put("adcode", tenant == null ? "" : safe(tenant.getPoiAdcode()));
        binding.put("longitude", tenant == null || tenant.getPoiLongitude() == null ? "" : tenant.getPoiLongitude().toPlainString());
        binding.put("latitude", tenant == null || tenant.getPoiLatitude() == null ? "" : tenant.getPoiLatitude().toPlainString());
        binding.put("mapLocated", tenant != null && tenant.getPoiLongitude() != null && tenant.getPoiLatitude() != null);
        binding.put("typeCode", tenant == null ? "" : safe(tenant.getPoiTypeCode()));
        binding.put("typeName", tenant == null ? "" : safe(tenant.getPoiTypeName()));
        binding.put("syncedAt", tenant == null ? null : tenant.getPoiSyncedAt());
        return binding;
    }

    private Map<String, Object> buildKnowledgeContext(List<Map<String, Object>> knowledge,
                                                       Map<String, Object> params,
                                                       String query) {
        boolean required = params == null || !Boolean.FALSE.equals(params.get("requireKnowledge"));
        List<String> missingFields = new ArrayList<String>();
        if (knowledge == null || knowledge.isEmpty()) {
            missingFields.add("当前问题未检索到可用的已确认知识");
        }
        Map<String, Object> context = new LinkedHashMap<String, Object>();
        context.put("required", required);
        context.put("query", query == null ? "" : query);
        context.put("items", knowledge == null ? List.of() : knowledge);
        context.put("missingFields", missingFields);
        context.put("retrievedAt", LocalDateTime.now().toString());
        context.put("maxItems", 30);
        context.put("policy", "confirmed_active_only");
        return context;
    }

    private String buildConfirmedKnowledgeText(List<Map<String, Object>> knowledge) {
        if (knowledge == null || knowledge.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int index = 1;
        int totalLength = 0;
        for (Map<String, Object> item : knowledge) {
            if (item == null) {
                continue;
            }
            String title = valueText(item.get("title"));
            String content = valueText(item.get("content"));
            String category = valueText(item.get("category"));
            if (isBlank(title) && isBlank(content)) {
                continue;
            }
            if (content.length() > 800) {
                content = content.substring(0, 800);
            }
            String line = index + ". "
                + (isBlank(category) ? "" : "【" + category + "】")
                + (isBlank(title) ? "" : title + "：")
                + content;
            if (totalLength + line.length() > 12_000) {
                break;
            }
            sb.append(line).append('\n');
            totalLength += line.length();
            index++;
        }
        return sb.toString().trim();
    }

    private String valueText(Object value) {
        return value == null ? "" : safe(String.valueOf(value)).trim();
    }

    private String safeJson(Object value) {
        try {
            return value == null ? "{}" : objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String extractPromptMessage(Map<String, Object> params, String fallback) {
        if (params != null) {
            for (String key : List.of("message", "userQuestion", "prompt", "theme", "topic", "content")) {
                Object value = params.get(key);
                if (value != null && !String.valueOf(value).isBlank()) {
                    return String.valueOf(value);
                }
            }
        }
        return fallback == null ? "" : fallback;
    }

    private void emitStatus(Consumer<String> onStatus, String message) {
        if (onStatus != null && !isBlank(message)) {
            onStatus.accept(message);
        }
    }

    private String statusBeforeFinalAgent(String moduleKey, Map<String, Object> context) {
        if ("brain".equals(moduleKey)) {
            return "AI 店长正在生成回复";
        }
        if ("strategy".equals(moduleKey)) {
            return "营销策略师正在生成方案";
        }
        if ("pricing".equals(moduleKey)) {
            return "收益定价顾问正在生成方案";
        }
        return "";
    }

    private boolean webSearchEnabled(Map<String, Object> params) {
        if (params == null) {
            return false;
        }
        Object value = firstPresent(
            params,
            "enableWebSearch",
            "useWebSearch",
            "webSearch",
            "withWebSearch",
            "联网搜索",
            "是否联网搜索"
        );
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = String.valueOf(value).trim().toLowerCase();
        return "true".equals(text)
            || "1".equals(text)
            || "yes".equals(text)
            || "on".equals(text)
            || "y".equals(text)
            || "联网".equals(text)
            || "开启".equals(text)
            || "是".equals(text)
            || "需要".equals(text);
    }

    private Object firstPresent(Map<String, Object> params, String... keys) {
        if (params == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (params.containsKey(key)) {
                return params.get(key);
            }
        }
        return null;
    }

    private String loadStyleForModule(String moduleKey, Long tenantId) {
        try {
            LambdaQueryWrapper<StyleLibrary> query = new LambdaQueryWrapper<StyleLibrary>()
                .eq(StyleLibrary::getEnabled, true);
            if (tenantId == null) {
                query.eq(StyleLibrary::getScope, "public");
            } else {
                query.and(w -> w.eq(StyleLibrary::getScope, "public")
                    .or().eq(StyleLibrary::getTenantId, tenantId));
            }
            query.orderByDesc(StyleLibrary::getUsageCount).last("LIMIT 3");
            List<StyleLibrary> styles = styleMapper.selectList(query);
            if (styles == null || styles.isEmpty()) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            for (StyleLibrary style : styles) {
                sb.append(style.getPromptSegment()).append('\n');
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("Failed to load style prompt. module={}", moduleKey, e);
            return null;
        }
    }

    private Map<String, String> buildVariables(Tenant tenant, Map<String, Object> params) {
        Map<String, String> vars = new LinkedHashMap<String, String>();
        vars.put("hotel_name", tenant == null ? "" : safe(tenant.getName()));
        vars.put("hotel_type", tenant == null ? "" : safe(tenant.getType()));
        vars.put("city", tenant == null ? "" : safe(tenant.getCity()));
        vars.put("hotel_city", tenant == null ? "" : safe(tenant.getCity()));
        vars.put("tags", tenant == null ? "" : safe(tenant.getTags()));
        vars.put("hotel_tags", tenant == null ? "" : safe(tenant.getTags()));
        vars.put("target_audience", tenant == null ? "" : safe(tenant.getTargetAudience()));
        vars.put("nearby", tenant == null ? "" : safe(tenant.getNearby()));
        if (params != null) {
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                if (entry.getValue() != null) {
                    vars.put(entry.getKey(), String.valueOf(entry.getValue()));
                }
            }
        }
        return vars;
    }

    private String buildUserPrompt(String moduleKey, Map<String, Object> params) {
        if ("occupancy_image".equals(moduleKey)) {
            return "识别上传的酒店历史房态表图片，只返回 records 和 warnings 的严格 JSON。";
        }
        if ("brain".equals(moduleKey)) {
            String directPrompt = extractPromptMessage(params, "");
            if (!isBlank(directPrompt)) {
                return directPrompt;
            }
        }
        if (params == null || params.isEmpty()) {
            return "请根据当前模块要求生成内容。";
        }
        StringBuilder sb = new StringBuilder("请根据以下要求生成内容：\n");
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (entry.getValue() != null && !"module".equals(entry.getKey()) && !"moduleKey".equals(entry.getKey())) {
                sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
            }
        }
        return sb.toString();
    }

    private String getDefaultTemplate(String moduleKey) {
        if ("wechat".equals(moduleKey)) {
            return "为 {{hotel_name}} 生成朋友圈或社群文案，包含早/中/晚三个可发布版本。";
        }
        if ("xhs".equals(moduleKey)) {
            return "为 {{hotel_name}} 生成小红书图文，包含标题、正文、标签、封面文字和配图建议。";
        }
        if ("video".equals(moduleKey)) {
            return "为 {{hotel_name}} 生成短视频口播脚本，包含开场钩子、镜头、旁白和发布文案。";
        }
        if ("poster".equals(moduleKey)) {
            return "为 {{hotel_name}} 生成营销海报方案，包含主标题、副标题、行动按钮和画面建议。";
        }
        if ("polish".equals(moduleKey)) {
            return "润色酒店/民宿运营输入内容，只优化表达、结构和营销可读性，不新增事实、不虚构设施、价格、距离、活动或政策。只输出润色后的中文文本。";
        }
        if ("article".equals(moduleKey)) {
            return "为 {{hotel_name}} 生成微信公众号推文，包含标题、摘要、正文结构和转化结尾。";
        }
        if ("pricing".equals(moduleKey)) {
            return moduleRuntimeInstruction(moduleKey);
        }
        if ("strategy".equals(moduleKey)) {
            return "基于酒店真实资料、房型价格、绑定位置、天气、周边情报、用户选填条件和 confirmedKnowledge 生成营销策略计划书。"
                + "如果用户目标为 auto 或目标客群为空，必须根据实际上下文智能设定目标与客群，并说明依据；不得使用固定节日、固定城市、固定 KPI 或模板案例。"
                + "若 AI 工作流已提供联网情报或周边信息，必须优先结合未来天气、热门事件/活动、周边景点/商圈/交通/展会/演出，说明它们如何影响客群需求和执行策略。"
                + "你只负责经营分析，不得联网搜索，不得重新调用周边 Agent，不得直接改价或自动执行。"
                + "不得虚构实时热度、竞品经营数据、本地活动、距离、价格或平台政策；没有真实信号时写“未获取，不作为策略依据”，不要编造。"
                + "输出必须使用 Markdown，并严格按顺序包含这些二级标题：## 核心目标与 KPI、## 天气、热门事件与周边机会判断、## 执行时间表、## 各渠道内容计划、## 策略标签、## 活动与定价承接、## 核心文案示例、## 底部执行动作。"
                + "必须先突出天气、事件、周边带来的机会、实现目标和具体执行步骤，不要在前半部分写长篇背景分析。所有 Markdown 表格的单元格内不得直接换行，需要分点时使用 <br>。"
                + "核心目标与 KPI 必须用 Markdown 表格，列为：指标、目标值、依据、说明原因。"
                + "天气、热门事件与周边机会判断必须用 Markdown 表格，列为：信号、实际情况、对客群/需求的影响、策略调整、原因。"
                + "执行时间表必须用 Markdown 表格，列为：阶段、时间、重点、具体动作、渠道/负责人；具体动作必须写清怎么执行以及与天气、热门事件或周边机会的关系。"
                + "各渠道内容计划必须用 Markdown 表格，列为：渠道、定位、依据、内容主题、承接动作、目标。"
                + "活动与定价承接必须用 Markdown 表格，列为：项目、当前依据、建议动作、执行条件、说明原因。"
                + "核心文案示例必须使用列表，至少输出 3 条文案；必须覆盖用户选择的重点渠道，结合当前天气、热门事件、周边机会或客群；每条写清适用渠道，不要只给一句空泛标题。"
                + "禁止输出风险核验与复盘指标，删除无效、待核实、没有业务价值的填充内容。"
                + "底部执行动作必须用 Markdown 表格，列为：actionKey、按钮文案、执行内容、调用模块；按钮用于跳转到对应内容生成页并注入配置，只输出单个内容生成动作，不输出批量动作。";
        }
        return "";
    }

    private String moduleRuntimeInstruction(String moduleKey) {
        if ("pricing".equals(moduleKey)) {
            return "基于酒店真实房型与挂牌价、用户确认的需求信号、竞品价格观察、销售窗口、价格底线、渠道约束和 AI 工作流提供的联网情报生成收益定价计划书。"
                + "若 AI 工作流已提供联网情报或周边信息，其中带来源、查询时间和价格字段的信息可作为外部参考，但仍需标注来源和待核实状态。"
                + "不得假设系统拥有 PMS、OTA 内部数据、实时入住率、实时库存、订单或营收数据。"
                + "你只负责收益分析，不得联网搜索，不得重新调用周边 Agent，不得直接改价或自动执行。"
                + "输出必须使用 Markdown，并严格按顺序包含这些二级标题：## 经营结论摘要、## 逐房型定价执行表、## 可执行动作清单、## 需求与价格信号图表、## 数据来源与可信度、## 风险核验与复盘指标。"
                + "必须先突出每个房型怎么调整价格，不要在逐房型定价执行表之前写长篇分析。所有 Markdown 表格的单元格内不得直接换行，需要分点时使用 <br>。"
                + "数据来源与可信度必须用 Markdown 表格，列为：来源、数据、用途、可信度、待核实。"
                + "需求与价格信号图表必须用 Markdown 表格，列为：信号、当前判断、依据、对价格影响、待核实。"
                + "逐房型定价执行表必须用 Markdown 表格，列为：房型、当前挂牌价、建议价区间、最低保护价、渠道动作、执行时点、风险；每个房型必须至少一行，即使竞品缺失也要基于本店挂牌价给出待核实建议。"
                + "可执行动作清单必须用 Markdown 表格，列为：actionKey、roomName、按钮文案、targetPrice、channel、status；status 默认 pending。"
                + "风险核验与复盘指标必须用 Markdown 表格，列为：事项、核验方法、负责人、时间点、复盘指标。";
        }
        if ("strategy".equals(moduleKey)) {
            return "基于酒店真实资料、房型价格、绑定位置、天气、周边情报、用户选填条件和 confirmedKnowledge 生成营销策略计划书。"
                + "如果用户目标为 auto 或目标客群为空，必须根据实际上下文智能设定目标与客群，并说明依据；不得使用固定节日、固定城市、固定 KPI 或模板案例。"
                + "若 AI 工作流已提供联网情报或周边信息，必须优先结合未来天气、热门事件/活动、周边景点/商圈/交通/展会/演出，说明它们如何影响客群需求和执行策略。"
                + "你只负责经营分析，不得联网搜索，不得重新调用周边 Agent，不得直接改价或自动执行。"
                + "不得虚构实时热度、竞品经营数据、本地活动、距离、价格或平台政策；没有真实信号时写“未获取，不作为策略依据”，不要编造。"
                + "输出必须使用 Markdown，并严格按顺序包含这些二级标题：## 核心目标与 KPI、## 天气、热门事件与周边机会判断、## 执行时间表、## 各渠道内容计划、## 策略标签、## 活动与定价承接、## 核心文案示例、## 底部执行动作。"
                + "必须先突出天气、事件、周边带来的机会、实现目标和具体执行步骤，不要在前半部分写长篇背景分析。所有 Markdown 表格的单元格内不得直接换行，需要分点时使用 <br>。"
                + "核心目标与 KPI 必须用 Markdown 表格，列为：指标、目标值、依据、说明原因。"
                + "天气、热门事件与周边机会判断必须用 Markdown 表格，列为：信号、实际情况、对客群/需求的影响、策略调整、原因。"
                + "执行时间表必须用 Markdown 表格，列为：阶段、时间、重点、具体动作、渠道/负责人；具体动作必须写清怎么执行以及与天气、热门事件或周边机会的关系。"
                + "各渠道内容计划必须用 Markdown 表格，列为：渠道、定位、依据、内容主题、承接动作、目标。"
                + "活动与定价承接必须用 Markdown 表格，列为：项目、当前依据、建议动作、执行条件、说明原因。"
                + "核心文案示例必须使用列表，至少输出 3 条文案；必须覆盖用户选择的重点渠道，结合当前天气、热门事件、周边机会或客群；每条写清适用渠道，不要只给一句空泛标题。"
                + "禁止输出风险核验与复盘指标，删除无效、待核实、没有业务价值的填充内容。"
                + "底部执行动作必须用 Markdown 表格，列为：actionKey、按钮文案、执行内容、调用模块；按钮用于跳转到对应内容生成页并注入配置，只输出单个内容生成动作，不输出批量动作。";
        }
        return "";
    }

    private void handleTaskError(Long taskId, Exception e) {
        ContentTask task = taskMapper.selectById(taskId);
        if (task != null) {
            task.setStatus("failed");
            task.setErrorMsg(toUserSafeError(e));
            task.setCompletedAt(LocalDateTime.now());
            taskMapper.updateById(task);
        }
        log.error("AI content generation failed. taskId={}", taskId, e);
    }

    private String toUserSafeError(Exception e) {
        if (e instanceof BizException && e.getMessage() != null && !e.getMessage().trim().isEmpty()) {
            String message = e.getMessage();
            if (message.length() <= 40 && !message.contains("http") && !message.contains("Exception")) {
                return message;
            }
        }
        return "AI 调用失败";
    }

    private String sanitizeAiOutput(String content) {
        if (content == null || content.trim().isEmpty()) {
            return content;
        }
        String normalized = content
            .replaceAll("(?is)<think>.*?</think>", "")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim();
        if (normalized.isEmpty()) {
            return "";
        }

        String[] lines = normalized.split("\n");
        StringBuilder cleaned = new StringBuilder();
        boolean started = false;
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                if (started && cleaned.length() > 0 && cleaned.charAt(cleaned.length() - 1) != '\n') {
                    cleaned.append('\n');
                }
                continue;
            }
            if (!started && isInternalReasoningLine(line)) {
                continue;
            }
            if (isEnglishSuggestionLine(line)) {
                continue;
            }
            started = true;
            cleaned.append(rawLine.stripTrailing()).append('\n');
        }

        String result = cleaned.toString().trim();
        return result.isEmpty() ? normalized : result;
    }

    private boolean isInternalReasoningLine(String line) {
        String compact = line.replace(" ", "");
        return compact.startsWith("我们根据")
            || compact.startsWith("用户提供")
            || compact.startsWith("酒店上下文")
            || compact.startsWith("内容构思")
            || compact.startsWith("注意")
            || compact.startsWith("按照")
            || compact.startsWith("作为AI")
            || compact.startsWith("我需要")
            || compact.startsWith("我应该")
            || compact.startsWith("可能是")
            || compact.startsWith("这里")
            || compact.startsWith("首先")
            || compact.startsWith("分析")
            || compact.startsWith("思考")
            || line.startsWith("I need")
            || line.startsWith("We need")
            || line.startsWith("The user");
    }

    private boolean isEnglishSuggestionLine(String line) {
        String trimmed = line.trim();
        return trimmed.matches("(?i)^(Help me|Give me|Write|Create|Generate|Tell me)\\b.*");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
