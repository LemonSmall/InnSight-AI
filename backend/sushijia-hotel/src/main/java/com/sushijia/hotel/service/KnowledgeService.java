package com.sushijia.hotel.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sushijia.common.exception.BizException;
import com.sushijia.common.response.ResultCode;
import com.sushijia.ai.service.AiInvocationService;
import com.sushijia.repository.entity.HotelKnowledgeExtractJob;
import com.sushijia.repository.entity.HotelKnowledgeFile;
import com.sushijia.repository.entity.HotelKnowledgeItem;
import com.sushijia.repository.entity.HotelKnowledgeSyncJob;
import com.sushijia.repository.entity.TenantOperationLog;
import com.sushijia.repository.mapper.HotelKnowledgeExtractJobMapper;
import com.sushijia.repository.mapper.HotelKnowledgeFileMapper;
import com.sushijia.repository.mapper.HotelKnowledgeItemMapper;
import com.sushijia.repository.mapper.HotelKnowledgeSyncJobMapper;
import com.sushijia.repository.mapper.TenantOperationLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private final HotelKnowledgeExtractJobMapper extractJobMapper;
    private final HotelKnowledgeFileMapper knowledgeFileMapper;
    private final HotelKnowledgeItemMapper knowledgeItemMapper;
    private final HotelKnowledgeSyncJobMapper syncJobMapper;
    private final TenantOperationLogMapper operationLogMapper;
    private final StringRedisTemplate redisTemplate;
    private final AiInvocationService aiInvocationService;
    private final KnowledgeDocumentParser documentParser;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${sushijia.knowledge.storage-path:./data/knowledge}")
    private String storagePath;

    public Map<String, Object> submitTextUpdate(Long tenantId, Long userId, String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "内容不能为空");
        }

        HotelKnowledgeExtractJob job = new HotelKnowledgeExtractJob();
        job.setTenantId(tenantId);
        job.setInputText(content.trim());
        job.setSourceType("sentence");
        job.setStatus("processing");
        job.setCreatedBy(userId);
        job.setCreatedAt(LocalDateTime.now());
        extractJobMapper.insert(job);

        return extractTextUpdateLocally(job, content.trim());
    }

    public Map<String, Object> uploadFile(Long tenantId, Long userId, MultipartFile multipartFile) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "请选择要上传的文件");
        }
        if (multipartFile.getSize() > 20L * 1024 * 1024) {
            throw new BizException(ResultCode.BAD_REQUEST, "文件不能超过 20MB");
        }

        String originalName = safeOriginalName(multipartFile.getOriginalFilename());
        String extension = documentParser.extension(originalName);
        Path target = storageRoot(tenantId).resolve(UUID.randomUUID() + "." + extension).normalize();
        if (!target.startsWith(storageRoot(tenantId))) {
            throw new BizException(ResultCode.BAD_REQUEST, "文件名不合法");
        }

        HotelKnowledgeFile file = new HotelKnowledgeFile();
        file.setTenantId(tenantId);
        file.setOriginalName(originalName);
        file.setFileType(extension);
        file.setFileSize(multipartFile.getSize());
        file.setStoragePath(target.toString());
        file.setParseStatus("processing");
        file.setCreatedBy(userId);
        file.setCreatedAt(LocalDateTime.now());
        file.setUpdatedAt(LocalDateTime.now());
        knowledgeFileMapper.insert(file);

        try {
            Files.createDirectories(target.getParent());
            try (InputStream input = multipartFile.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
            String extractedText = documentParser.parse(target, extension);
            file.setParseStatus("done");
            file.setUpdatedAt(LocalDateTime.now());
            knowledgeFileMapper.updateById(file);

            HotelKnowledgeExtractJob job = new HotelKnowledgeExtractJob();
            job.setTenantId(tenantId);
            job.setFileId(file.getId());
            job.setInputText(extractedText);
            job.setSourceType("file");
            job.setStatus("processing");
            job.setCreatedBy(userId);
            job.setCreatedAt(LocalDateTime.now());
            extractJobMapper.insert(job);
            Map<String, Object> result = extractWithAgent(job, tenantId, userId, extractedText, "file", originalName);
            result.put("fileId", file.getId());
            result.put("fileName", originalName);
            return result;
        } catch (BizException e) {
            if (!"done".equals(file.getParseStatus())) {
                markFileFailed(file);
            }
            throw e;
        } catch (Exception e) {
            markFileFailed(file);
            throw new BizException(ResultCode.BAD_REQUEST, "文件上传或解析失败");
        }
    }

    @Transactional
    public Map<String, Object> confirmExtractJob(Long tenantId, Long userId, Long jobId) {
        HotelKnowledgeExtractJob job = extractJobMapper.selectById(jobId);
        if (job == null || !tenantId.equals(job.getTenantId())) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        if (!"awaiting_confirm".equals(job.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "当前知识无需确认");
        }

        Map<String, Object> extracted = normalizeExtractedRoomTypeNames(parseJson(job.getExtractedJson()));
        List<Map<String, Object>> candidates = extractedItems(extracted, job);
        List<Long> knowledgeIds = new ArrayList<>();
        List<Long> syncJobIds = new ArrayList<>();
        for (Map<String, Object> candidate : candidates) {
            HotelKnowledgeItem item = createKnowledgeItem(tenantId, userId, job, candidate);
            knowledgeItemMapper.insert(item);
            knowledgeIds.add(item.getId());

            HotelKnowledgeSyncJob syncJob = createSyncJob(tenantId, item.getId());
            syncJobMapper.insert(syncJob);
            syncJobIds.add(syncJob.getId());
        }

        job.setStatus("confirmed");
        job.setCompletedAt(LocalDateTime.now());
        extractJobMapper.updateById(job);
        evictKnowledgeCache(tenantId);
        audit(tenantId, userId, "knowledge.confirm", "knowledge_extract_job", job.getId(), job.getSummary());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("knowledgeIds", knowledgeIds);
        result.put("syncJobIds", syncJobIds);
        result.put("message", "知识已确认");
        return result;
    }

    @Transactional
    public void cancelExtractJob(Long tenantId, Long userId, Long jobId) {
        HotelKnowledgeExtractJob job = requirePendingJob(tenantId, jobId);
        job.setStatus("cancelled");
        job.setCompletedAt(LocalDateTime.now());
        extractJobMapper.updateById(job);
        evictKnowledgeCache(tenantId);
        audit(tenantId, userId, "knowledge.cancel", "knowledge_extract_job", jobId, job.getSummary());
    }

    @Transactional
    public HotelKnowledgeItem updateKnowledge(Long tenantId,
                                              Long userId,
                                              Long itemId,
                                              Map<String, Object> body) {
        HotelKnowledgeItem item = requireKnowledgeItem(tenantId, itemId);
        String category = stringValue(body.get("category"));
        String title = stringValue(body.get("title"));
        String content = stringValue(body.get("content"));
        if (category.isBlank() || title.isBlank() || content.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "分类、标题和内容不能为空");
        }
        item.setCategory(category);
        item.setTitle(title);
        item.setContent(content);
        item.setEffectiveFrom(parseDateTime(body.get("effectiveFrom"), false));
        item.setEffectiveTo(parseDateTime(body.get("effectiveTo"), true));
        item.setUpdatedBy(userId);
        item.setUpdatedAt(LocalDateTime.now());
        item.setStatus(isExpired(item.getEffectiveTo()) ? "expired" : "active");
        knowledgeItemMapper.updateById(item);
        evictKnowledgeCache(tenantId);
        audit(tenantId, userId, "knowledge.update", "knowledge_item", itemId, title);
        return item;
    }

    @Transactional
    public void archiveKnowledge(Long tenantId, Long userId, Long itemId) {
        HotelKnowledgeItem item = requireKnowledgeItem(tenantId, itemId);
        item.setStatus("archived");
        item.setUpdatedBy(userId);
        item.setUpdatedAt(LocalDateTime.now());
        knowledgeItemMapper.updateById(item);
        evictKnowledgeCache(tenantId);
        audit(tenantId, userId, "knowledge.archive", "knowledge_item", itemId, item.getTitle());
    }

    public List<HotelKnowledgeItem> listKnowledge(Long tenantId, String category, int limit) {
        LambdaQueryWrapper<HotelKnowledgeItem> query = new LambdaQueryWrapper<HotelKnowledgeItem>()
            .eq(HotelKnowledgeItem::getTenantId, tenantId)
            .eq(HotelKnowledgeItem::getStatus, "active")
            .orderByDesc(HotelKnowledgeItem::getUpdatedAt)
            .last("LIMIT " + Math.max(1, Math.min(limit, 200)));
        if (category != null && !category.isBlank()) {
            query.eq(HotelKnowledgeItem::getCategory, category);
        }
        return knowledgeItemMapper.selectList(query);
    }

    public List<HotelKnowledgeExtractJob> listPendingJobs(Long tenantId) {
        return extractJobMapper.selectList(new LambdaQueryWrapper<HotelKnowledgeExtractJob>()
            .eq(HotelKnowledgeExtractJob::getTenantId, tenantId)
            .eq(HotelKnowledgeExtractJob::getStatus, "awaiting_confirm")
            .orderByDesc(HotelKnowledgeExtractJob::getCreatedAt)
            .last("LIMIT 50"));
    }

    public List<HotelKnowledgeFile> listFiles(Long tenantId, int limit) {
        return knowledgeFileMapper.selectList(new LambdaQueryWrapper<HotelKnowledgeFile>()
            .eq(HotelKnowledgeFile::getTenantId, tenantId)
            .orderByDesc(HotelKnowledgeFile::getCreatedAt)
            .last("LIMIT " + Math.max(1, Math.min(limit, 100))));
    }

    @Scheduled(cron = "0 15 2 * * *")
    public void expireKnowledge() {
        List<HotelKnowledgeItem> expired = knowledgeItemMapper.selectList(
            new LambdaQueryWrapper<HotelKnowledgeItem>()
                .eq(HotelKnowledgeItem::getStatus, "active")
                .isNotNull(HotelKnowledgeItem::getEffectiveTo)
                .lt(HotelKnowledgeItem::getEffectiveTo, LocalDateTime.now()));
        Set<Long> tenantIds = new HashSet<>();
        for (HotelKnowledgeItem item : expired) {
            item.setStatus("expired");
            item.setUpdatedAt(LocalDateTime.now());
            knowledgeItemMapper.updateById(item);
            tenantIds.add(item.getTenantId());
        }
        tenantIds.forEach(this::evictKnowledgeCache);
    }

    private HotelKnowledgeExtractJob requirePendingJob(Long tenantId, Long jobId) {
        HotelKnowledgeExtractJob job = extractJobMapper.selectById(jobId);
        if (job == null || !tenantId.equals(job.getTenantId())) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        if (!"awaiting_confirm".equals(job.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "当前资料已处理");
        }
        return job;
    }

    private HotelKnowledgeItem requireKnowledgeItem(Long tenantId, Long itemId) {
        HotelKnowledgeItem item = knowledgeItemMapper.selectById(itemId);
        if (item == null || !tenantId.equals(item.getTenantId()) || "archived".equals(item.getStatus())) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        return item;
    }

    private LocalDateTime parseDateTime(Object value, boolean endOfDay) {
        String text = stringValue(value);
        if (text.isBlank()) return null;
        try {
            if (text.length() == 10) {
                LocalDate date = LocalDate.parse(text);
                return endOfDay ? date.atTime(23, 59, 59) : date.atStartOfDay();
            }
            return LocalDateTime.parse(text);
        } catch (Exception e) {
            throw new BizException(ResultCode.BAD_REQUEST, "有效期格式不正确");
        }
    }

    private boolean isExpired(LocalDateTime effectiveTo) {
        return effectiveTo != null && effectiveTo.isBefore(LocalDateTime.now());
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private void evictKnowledgeCache(Long tenantId) {
        try {
            redisTemplate.delete("sushijia:knowledge:snapshot:" + tenantId);
        } catch (Exception ignored) {
            // Redis is an acceleration layer only; cache cleanup must not break knowledge writes.
        }
    }

    private void audit(Long tenantId, Long userId, String action, String targetType, Long targetId, String detail) {
        TenantOperationLog log = new TenantOperationLog();
        log.setTenantId(tenantId);
        log.setUserId(userId);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(String.valueOf(targetId));
        log.setDetail(detail == null ? "" : detail);
        log.setCreatedAt(LocalDateTime.now());
        operationLogMapper.insert(log);
    }

    private Map<String, Object> extractTextUpdateLocally(HotelKnowledgeExtractJob job, String content) {
        String category = inferTextUpdateCategory(content);
        String label = knowledgeCategoryLabel(category);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("category", category);
        item.put("title", buildTextUpdateTitle(label, content));
        item.put("content", content);
        item.put("effectiveFrom", LocalDate.now().toString());
        item.put("effectiveTo", "");
        item.put("confidence", 100);
        item.put("source", "staff_sentence");

        Map<String, Object> extracted = new LinkedHashMap<>();
        extracted.put("summary", "识别到一条" + label + "，确认后将写入酒店资料库并进入同步队列。");
        extracted.put("items", List.of(item));
        extracted = normalizeExtractedRoomTypeNames(extracted);

        job.setStatus("awaiting_confirm");
        job.setSummary(stringValue(extracted.get("summary")));
        job.setExtractedJson(toJson(extracted));
        job.setCompletedAt(LocalDateTime.now());
        extractJobMapper.updateById(job);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("jobId", job.getId());
        result.put("id", job.getId());
        result.put("status", job.getStatus());
        result.put("summary", job.getSummary());
        result.put("inputText", content);
        result.put("createdAt", job.getCreatedAt());
        result.put("extracted", extracted);
        return result;
    }

    private String inferTextUpdateCategory(String content) {
        String text = content == null ? "" : content;
        if (containsAny(text, "装修", "维修", "不能出租", "不可出租", "锁房", "停用", "楼层", "房间", "房型")) {
            return "room";
        }
        if (containsAny(text, "早餐", "入住", "退房", "押金", "发票", "取消", "政策", "时间", "营业")) {
            return "policy";
        }
        if (containsAny(text, "停车", "接送", "洗衣", "泳池", "健身", "设施", "服务", "免费")) {
            return "facility";
        }
        if (containsAny(text, "活动", "套餐", "优惠", "促销", "折扣", "亲子", "周末")) {
            return "promotion";
        }
        return "temporary_notice";
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String knowledgeCategoryLabel(String category) {
        return switch (category) {
            case "room" -> "房型/房态更新";
            case "policy" -> "政策时间更新";
            case "facility" -> "设施服务更新";
            case "promotion" -> "活动促销更新";
            default -> "临时通知";
        };
    }

    private String buildTextUpdateTitle(String label, String content) {
        String compact = content == null ? "" : content.replaceAll("\\s+", " ").trim();
        if (compact.length() > 36) {
            compact = compact.substring(0, 36) + "...";
        }
        return label + "：" + compact;
    }

    private Map<String, Object> extractWithAgent(HotelKnowledgeExtractJob job,
                                                 Long tenantId,
                                                 Long userId,
                                                 String sourceText,
                                                 String sourceType,
                                                 String sourceName) {
        Map<String, Object> business = new LinkedHashMap<>();
        business.put("sourceType", sourceType);
        business.put("sourceText", sourceText);
        business.put("fileName", sourceName);
        business.put("extractionMode", "facts_only");
        business.put("effectiveHint", "从原文提取，无法确定时留空");

        Map<String, Object> common = new LinkedHashMap<>();
        common.put("schemaVersion", "1.0");
        common.put("requestId", UUID.randomUUID().toString());
        common.put("tenantId", tenantId);
        common.put("userId", userId);
        common.put("moduleKey", "knowledge");
        common.put("currentTime", LocalDateTime.now().toString());
        common.put("dataScope", Map.of("tenantProfileOnly", true, "hasPms", false, "hasOta", false));

        Map<String, Object> knowledge = new LinkedHashMap<>();
        knowledge.put("required", false);
        knowledge.put("items", List.of());
        knowledge.put("policy", "Do not infer facts not present in sourceText");

        Map<String, Object> context = new LinkedHashMap<>(business);
        context.put("commonContextJson", toJson(common));
        context.put("businessParamsJson", toJson(business));
        context.put("knowledgeContextJson", toJson(knowledge));
        context.put("message", "整理并提取酒店资料中的可确认事实");

        try {
            String response = aiInvocationService.invoke(
                tenantId,
                "knowledge",
                "仅提取输入中明确存在的酒店事实，并严格输出约定 JSON。",
                sourceText,
                context);
            Map<String, Object> extracted = normalizeExtractedRoomTypeNames(normalizeAgentResult(response));
            job.setStatus("awaiting_confirm");
            job.setSummary(stringValue(extracted.get("summary")));
            job.setExtractedJson(toJson(extracted));
            job.setCompletedAt(LocalDateTime.now());
            extractJobMapper.updateById(job);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("jobId", job.getId());
            result.put("status", job.getStatus());
            result.put("summary", job.getSummary());
            result.put("extracted", extracted);
            return result;
        } catch (Exception e) {
            job.setStatus("failed");
            job.setErrorMsg("知识整理失败");
            job.setCompletedAt(LocalDateTime.now());
            extractJobMapper.updateById(job);
            throw e instanceof BizException bizException
                ? bizException
                : new BizException(ResultCode.AI_GENERATE_FAILED, "知识整理失败");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeAgentResult(String response) {
        String json = response == null ? "" : response.trim()
            .replaceAll("(?s)<think>.*?</think>", "")
            .replaceFirst("(?s)^```(?:json)?\\s*", "")
            .replaceFirst("(?s)\\s*```$", "")
            .trim();
        Map<String, Object> result = parseJson(json);
        Object itemsValue = result.get("items");
        if (!(itemsValue instanceof List<?> items) || items.isEmpty()) {
            throw new BizException(ResultCode.AI_GENERATE_FAILED, "知识整理结果无效");
        }
        for (Object value : items) {
            if (!(value instanceof Map<?, ?> item)
                || stringValue(item.get("title")).isBlank()
                || stringValue(item.get("content")).isBlank()) {
                throw new BizException(ResultCode.AI_GENERATE_FAILED, "知识整理结果无效");
            }
        }
        if (stringValue(result.get("summary")).isBlank()) {
            result.put("summary", "已提取 " + items.size() + " 条待确认知识");
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractedItems(Map<String, Object> extracted, HotelKnowledgeExtractJob job) {
        Object value = extracted.get("items");
        if (value instanceof List<?> list) {
            List<Map<String, Object>> items = list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .toList();
            if (!items.isEmpty()) return items;
        }
        throw new BizException(ResultCode.BAD_REQUEST, "待确认知识内容无效");
    }

    private HotelKnowledgeItem createKnowledgeItem(Long tenantId,
                                                   Long userId,
                                                   HotelKnowledgeExtractJob job,
                                                   Map<String, Object> candidate) {
        candidate = normalizeRoomTypeCandidate(candidate);
        HotelKnowledgeItem item = new HotelKnowledgeItem();
        item.setTenantId(tenantId);
        item.setCategory(normalizeCategory(stringValue(candidate.get("category"))));
        item.setTitle(stringValue(candidate.get("title")));
        item.setContent(stringValue(candidate.get("content")));
        item.setStructuredJson(toJson(candidate));
        item.setSourceType(job.getSourceType());
        item.setSourceName(sourceName(job));
        item.setSourceFileId(job.getFileId());
        item.setExtractJobId(job.getId());
        item.setEffectiveFrom(parseDateTime(candidate.get("effectiveFrom"), false));
        item.setEffectiveTo(parseDateTime(candidate.get("effectiveTo"), true));
        item.setStatus(isExpired(item.getEffectiveTo()) ? "expired" : "active");
        item.setConfidence(parseConfidence(candidate.get("confidence")));
        item.setCreatedBy(userId);
        item.setUpdatedBy(userId);
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        return item;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeExtractedRoomTypeNames(Map<String, Object> extracted) {
        Map<String, Object> result = new LinkedHashMap<>(extracted == null ? Map.of() : extracted);
        Object itemsValue = result.get("items");
        if (itemsValue instanceof List<?> items) {
            List<Object> normalizedItems = new ArrayList<>();
            for (Object item : items) {
                normalizedItems.add(normalizeRoomTypeValue(item, false, ""));
            }
            result.put("items", normalizedItems);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeRoomTypeCandidate(Map<String, Object> candidate) {
        Object normalized = normalizeRoomTypeValue(candidate, true, "");
        return normalized instanceof Map<?, ?> map ? (Map<String, Object>) map : candidate;
    }

    private Object normalizeRoomTypeValue(Object value, boolean parentRoomLike, String key) {
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                map.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            boolean roomLike = parentRoomLike || isRoomLikeKnowledge(map);
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                normalized.put(entry.getKey(), normalizeRoomTypeValue(entry.getValue(), roomLike, entry.getKey()));
            }
            return normalized;
        }
        if (value instanceof List<?> list) {
            List<Object> normalized = new ArrayList<>();
            for (Object item : list) {
                normalized.add(normalizeRoomTypeValue(item, parentRoomLike, key));
            }
            return normalized;
        }
        if (value instanceof String text && parentRoomLike && isRoomNameKey(key)) {
            return stripTrailingRoomParentheses(text);
        }
        return value;
    }

    private boolean isRoomLikeKnowledge(Map<String, Object> item) {
        String category = normalizeCategory(stringValue(item.get("category")));
        if ("room".equals(category)) {
            return true;
        }
        for (String key : item.keySet()) {
            if (isRoomNameKey(key)) {
                return true;
            }
        }
        return false;
    }

    private boolean isRoomNameKey(String key) {
        String normalized = key == null ? "" : key.toLowerCase().replaceAll("[_\\-\\s]", "");
        return normalized.equals("name")
            || normalized.equals("title")
            || normalized.equals("room")
            || normalized.equals("roomtype")
            || normalized.equals("roomtypename")
            || normalized.equals("roomname")
            || keyContains(key, "房型")
            || keyContains(key, "房间名")
            || keyContains(key, "客房名");
    }

    private boolean keyContains(String key, String text) {
        return key != null && key.contains(text);
    }

    private String stripTrailingRoomParentheses(String value) {
        String text = value == null ? "" : value.trim();
        String next = text.replaceAll("\\s*[（(][^（）()]*[）)]\\s*$", "").trim();
        while (!next.isBlank() && !next.equals(text)) {
            text = next;
            next = text.replaceAll("\\s*[（(][^（）()]*[）)]\\s*$", "").trim();
        }
        return text;
    }

    private HotelKnowledgeSyncJob createSyncJob(Long tenantId, Long knowledgeItemId) {
        HotelKnowledgeSyncJob syncJob = new HotelKnowledgeSyncJob();
        syncJob.setTenantId(tenantId);
        syncJob.setProviderKey("local_context");
        syncJob.setTargetDatasetId("");
        syncJob.setKnowledgeItemId(knowledgeItemId);
        syncJob.setStatus("success");
        syncJob.setRetryCount(0);
        syncJob.setSyncedAt(LocalDateTime.now());
        syncJob.setCreatedAt(LocalDateTime.now());
        syncJob.setUpdatedAt(LocalDateTime.now());
        return syncJob;
    }

    private String sourceName(HotelKnowledgeExtractJob job) {
        if (job.getFileId() == null) return "店员录入";
        HotelKnowledgeFile file = knowledgeFileMapper.selectById(job.getFileId());
        return file == null ? "酒店文件" : file.getOriginalName();
    }

    private String normalizeCategory(String category) {
        return Set.of("basic", "room", "facility", "policy", "temporary_notice", "promotion", "faq")
            .contains(category) ? category : "temporary_notice";
    }

    private BigDecimal parseConfidence(Object value) {
        try {
            BigDecimal confidence = new BigDecimal(stringValue(value));
            if (confidence.compareTo(BigDecimal.ONE) <= 0) confidence = confidence.multiply(new BigDecimal("100"));
            return confidence.max(BigDecimal.ZERO).min(new BigDecimal("100"));
        } catch (Exception ignored) {
            return BigDecimal.ZERO;
        }
    }

    private Path storageRoot(Long tenantId) {
        return Paths.get(storagePath).toAbsolutePath().normalize().resolve(String.valueOf(tenantId)).normalize();
    }

    private String safeOriginalName(String fileName) {
        String value = fileName == null ? "" : Paths.get(fileName).getFileName().toString().trim();
        if (value.isBlank() || value.length() > 255) {
            throw new BizException(ResultCode.BAD_REQUEST, "文件名不合法");
        }
        return value;
    }

    private void markFileFailed(HotelKnowledgeFile file) {
        file.setParseStatus("failed");
        file.setParseError("文件解析失败");
        file.setUpdatedAt(LocalDateTime.now());
        knowledgeFileMapper.updateById(file);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        try {
            return objectMapper.readValue(json == null ? "{}" : json, Map.class);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }
}
