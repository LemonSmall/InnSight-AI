package com.sushijia.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sushijia.ai.service.AiCallLogService;
import com.sushijia.common.exception.BizException;
import com.sushijia.common.response.ResultCode;
import com.sushijia.repository.entity.AiAgentBinding;
import com.sushijia.repository.mapper.AiAgentBindingMapper;
import com.sushijia.repository.mapper.SystemSettingMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.MediaType;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
@Service
public class DifyClient {

    private static final String DEFAULT_ENDPOINT = "https://api.dify.ai/v1";
    private static final String USER_AI_FAILED_MESSAGE = "AI \u8c03\u7528\u5931\u8d25";
    private static final String USER_AI_NOT_CONFIGURED_MESSAGE = "AI \u670d\u52a1\u672a\u914d\u7f6e";

    private final SystemSettingMapper settingMapper;
    private final AiAgentBindingMapper agentBindingMapper;
    private final AiCallLogService callLogService;
    private final StringRedisTemplate redisTemplate;
    private final ConcurrentMap<String, String> localConversations = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DifyClient(SystemSettingMapper settingMapper,
                      AiAgentBindingMapper agentBindingMapper,
                      AiCallLogService callLogService,
                      StringRedisTemplate redisTemplate) {
        this.settingMapper = settingMapper;
        this.agentBindingMapper = agentBindingMapper;
        this.callLogService = callLogService;
        this.redisTemplate = redisTemplate;
    }

    public boolean isEnabled() {
        return "true".equalsIgnoreCase(getSetting("dify_enabled", "false"));
    }

    public String runAgent(String moduleKey,
                           String systemPrompt,
                           String userPrompt,
                           Map<String, Object> context) {
        return executeAgent(moduleKey, systemPrompt, userPrompt, context, null);
    }

    public String streamAgent(String moduleKey,
                              String systemPrompt,
                              String userPrompt,
                              Map<String, Object> context,
                              Consumer<String> onChunk) {
        return executeAgent(moduleKey, systemPrompt, userPrompt, context, onChunk);
    }

    private String executeAgent(String moduleKey,
                                String systemPrompt,
                                String userPrompt,
                                Map<String, Object> context,
                                Consumer<String> onChunk) {
        long start = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();
        if (!isEnabled()) {
            callLogService.record(moduleKey, null, null, requestId, context, null, null, "failed", null,
                elapsed(start), "DIFY_DISABLED", "Dify service is disabled");
            throw new BizException(ResultCode.AI_GENERATE_FAILED, USER_AI_NOT_CONFIGURED_MESSAGE);
        }

        AiAgentBinding binding = getBinding(moduleKey);
        if (binding == null || !"dify".equalsIgnoreCase(defaultString(binding.getProvider(), "dify"))) {
            callLogService.record(moduleKey, binding, null, requestId, context, null, null, "failed", null,
                elapsed(start), "NOT_CONFIGURED", "Dify binding is not configured");
            throw new BizException(ResultCode.AI_GENERATE_FAILED, USER_AI_NOT_CONFIGURED_MESSAGE);
        }

        String appType = resolveAppType(moduleKey, binding);
        String apiKey = firstNotBlank(binding.getApiKey(), binding.getBotApiKey());
        String endpoint = normalizeEndpoint(firstNotBlank(binding.getEndpoint(), getSetting("dify_endpoint", DEFAULT_ENDPOINT)));
        if (isBlank(apiKey)) {
            callLogService.record(moduleKey, binding, endpoint, requestId, context, null, null, "failed", null,
                elapsed(start), "API_KEY_MISSING", "Dify app API key is not configured");
            throw new BizException(ResultCode.AI_GENERATE_FAILED, USER_AI_NOT_CONFIGURED_MESSAGE);
        }

        boolean chatflow = "chatflow".equalsIgnoreCase(appType);
        String url = endpoint + (chatflow ? "/chat-messages" : "/workflows/run");
        String responseMode = onChunk == null ? getResponseMode() : "streaming";
        String requestUser = chatflow ? difyUser(context) : workflowDifyUser(context);
        String uploadFileId = uploadDifyFileIfPresent(endpoint, apiKey, context, requestUser);
        if (!isBlank(uploadFileId) && context != null) {
            context.put("_difyUploadFileId", uploadFileId);
        }
        String conversationKey = chatflow && "brain".equalsIgnoreCase(moduleKey)
            ? conversationKey(moduleKey, binding, context)
            : null;
        if (conversationKey != null && shouldResetConversation(context)) {
            clearConversation(conversationKey);
        }
        ObjectNode body = chatflow
            ? buildChatflowBody(moduleKey, userPrompt, context, responseMode)
            : buildWorkflowBody(moduleKey, systemPrompt, userPrompt, context, responseMode);
        body.put("user", requestUser);
        if (conversationKey != null) {
            String conversationId = loadConversation(conversationKey);
            if (!isBlank(conversationId)) {
                body.put("conversation_id", conversationId);
                log.info("Dify conversation loaded. module={}, key={}, conversationId={}", moduleKey, conversationKey, conversationId);
            } else {
                log.info("Dify conversation not found. module={}, key={}", moduleKey, conversationKey);
            }
        }
        applyInputSchema(body, binding.getInputSchema(), context);
        pruneOccupancyImageInputs(moduleKey, body);
        String requestSummary = summarizeRequest(body);
        String responseSummary = null;
        Integer httpStatus = null;

        int timeoutSeconds = getTimeoutSeconds(moduleKey);
        try {
            StreamAccumulator stream = new StreamAccumulator(objectMapper, onChunk);
            log.info("Sending Dify stream request. module={}, appType={}, responseMode={}, endpoint={}", moduleKey, appType, responseMode, url);
            DifyHttpResult httpResult = buildClient(timeoutSeconds).post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + apiKey)
                .bodyValue(body)
                .exchangeToMono(response -> response.bodyToFlux(DataBuffer.class)
                    .doOnNext(dataBuffer -> {
                        try {
                            stream.accept(dataBuffer.toString(StandardCharsets.UTF_8));
                        } finally {
                            DataBufferUtils.release(dataBuffer);
                        }
                    })
                    .then(Mono.fromCallable(() -> new DifyHttpResult(response.statusCode().value(), stream.raw()))))
                .retryWhen(Retry.backoff(getRetryTimes(), Duration.ofMillis(800)).filter(this::isTransientNetworkError))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .block();

            if (httpResult == null) {
                callLogService.record(moduleKey, binding, url, requestId, context, requestSummary, null, "failed", null,
                    elapsed(start), "EMPTY_HTTP_RESPONSE", "Dify response is empty");
                throw new BizException(ResultCode.AI_GENERATE_FAILED, USER_AI_FAILED_MESSAGE);
            }

            httpStatus = httpResult.statusCode;
            responseSummary = summarizeResponse(httpResult.body, null);
            if (httpStatus < 200 || httpStatus >= 300) {
                String errorMessage = extractErrorMessage(httpResult.body);
                log.error("Dify returned non-2xx. module={}, appType={}, status={}, body={}",
                    moduleKey, appType, httpStatus, httpResult.body);
                callLogService.record(moduleKey, binding, url, requestId, context, requestSummary, responseSummary, "failed", httpStatus,
                    elapsed(start), "HTTP_" + httpStatus, errorMessage);
                throw new BizException(ResultCode.AI_GENERATE_FAILED, errorMessage);
            }

            String content = sanitizeFinalAnswer(stream.result());
            if (isBlank(content)) {
                content = sanitizeFinalAnswer(extractContent(httpResult.body, appType));
            }
            responseSummary = summarizeResponse(httpResult.body, content);

            if (isBlank(content)) {
                String errorCode = isIncompleteWorkflowStream(httpResult.body) ? "WORKFLOW_INCOMPLETE" : "EMPTY_OUTPUT";
                String errorDetail = isIncompleteWorkflowStream(httpResult.body)
                    ? "Dify workflow stream ended before workflow_finished"
                    : "Dify response content is empty";
                log.error("Dify response content is empty. module={}, response={}", moduleKey, httpResult.body);
                callLogService.record(moduleKey, binding, url, requestId, context, requestSummary, responseSummary, "failed", httpStatus,
                    elapsed(start), errorCode, errorDetail);
                throw new BizException(ResultCode.AI_GENERATE_FAILED, outputFailureMessage(moduleKey, httpResult.body));
            }
            if (onChunk != null && stream.hasMissingFinalContent(content)) {
                log.info("Dify stream missed final tail; replaying missing answer progressively. module={}, emittedChars={}, finalChars={}",
                    moduleKey, stream.emittedLength(), content.length());
                stream.emitMissingFinal(content);
            }
            if (conversationKey != null && !isBlank(stream.conversationId())) {
                saveConversation(conversationKey, stream.conversationId());
            } else if (conversationKey != null) {
                log.warn("Dify conversation id missing from response. module={}, key={}", moduleKey, conversationKey);
            }

            callLogService.record(moduleKey, binding, url, requestId, context, requestSummary, responseSummary, "success", httpStatus,
                elapsed(start), null, null);
            return content;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            Throwable cause = unwrapRetryCause(e);
            if (isTimeoutException(cause)) {
                String message = timeoutMessage(moduleKey, timeoutSeconds);
                log.error("Dify request timed out. module={}, appType={}, endpoint={}, timeoutSeconds={}",
                    moduleKey, appType, endpoint, timeoutSeconds, e);
                callLogService.record(moduleKey, binding, url, requestId, context, requestSummary, responseSummary,
                    "failed", httpStatus, elapsed(start), "DIFY_TIMEOUT", rootMessage(cause));
                throw new BizException(ResultCode.AI_GENERATE_FAILED, message);
            }
            log.error("Dify request failed. module={}, appType={}, endpoint={}, cause={}",
                moduleKey, appType, endpoint, rootMessage(cause), e);
            callLogService.record(moduleKey, binding, url, requestId, context, requestSummary, responseSummary,
                "failed", httpStatus, elapsed(start), cause.getClass().getSimpleName(), rootMessage(cause));
            throw new BizException(ResultCode.AI_GENERATE_FAILED, USER_AI_FAILED_MESSAGE);
        }
    }

    private String emptyOutputMessage(String moduleKey, String responseBody) {
        if ("occupancy_image".equalsIgnoreCase(moduleKey)) {
            return "Dify 图片识别工作流没有返回可用结果，请确认 End 节点输出 output 或 records/warnings。响应摘要：" + truncate(defaultString(responseBody, ""), 300);
        }
        return USER_AI_FAILED_MESSAGE;
    }

    private String outputFailureMessage(String moduleKey, String responseBody) {
        if ("occupancy_image".equalsIgnoreCase(moduleKey)) {
            if (isIncompleteWorkflowStream(responseBody)) {
                return "\u56fe\u7247\u8bc6\u522b\u5de5\u4f5c\u6d41\u5df2\u5f00\u59cb\uff0c\u4f46\u672a\u8fd4\u56de\u5b8c\u6210\u4e8b\u4ef6\u3002\u8bf7\u7a0d\u540e\u91cd\u8bd5\uff1b\u5982\u679c\u6301\u7eed\u51fa\u73b0\uff0c\u8bf7\u68c0\u67e5 Dify \u5de5\u4f5c\u6d41\u662f\u5426\u5728\u89c6\u89c9\u8bc6\u522b\u8282\u70b9\u8d85\u65f6\u6216\u672a\u8fde\u5230 End \u8282\u70b9\u3002\u54cd\u5e94\u6458\u8981\uff1a"
                    + truncate(defaultString(responseBody, ""), 300);
            }
            return "\u56fe\u7247\u8bc6\u522b\u5de5\u4f5c\u6d41\u6ca1\u6709\u8fd4\u56de\u53ef\u7528\u7ed3\u679c\uff0c\u8bf7\u786e\u8ba4 End \u8282\u70b9\u8f93\u51fa output \u6216 records/warnings\u3002\u54cd\u5e94\u6458\u8981\uff1a"
                + truncate(defaultString(responseBody, ""), 300);
        }
        return USER_AI_FAILED_MESSAGE;
    }

    private String timeoutMessage(String moduleKey, int timeoutSeconds) {
        if ("occupancy_image".equalsIgnoreCase(moduleKey)) {
            return "\u56fe\u7247\u8bc6\u522b\u8d85\u65f6\uff0cDify \u5728 " + timeoutSeconds
                + " \u79d2\u5185\u672a\u8fd4\u56de\u5b8c\u6210\u7ed3\u679c\u3002\u8bf7\u7a0d\u540e\u91cd\u8bd5\uff0c\u6216\u5728 Dify \u540e\u53f0\u68c0\u67e5\u89c6\u89c9\u8bc6\u522b\u8282\u70b9\u548c End \u8282\u70b9\u8f93\u51fa\u3002";
        }
        return USER_AI_FAILED_MESSAGE;
    }

    private boolean isIncompleteWorkflowStream(String responseBody) {
        if (isBlank(responseBody)) {
            return false;
        }
        String lower = responseBody.toLowerCase();
        return lower.contains("\"event\":\"workflow_started\"")
            && !lower.contains("\"event\":\"workflow_finished\"")
            && !lower.contains("\"event\":\"workflow_failed\"")
            && !lower.contains("\"event\":\"error\"");
    }

    private ObjectNode buildWorkflowBody(String moduleKey,
                                         String systemPrompt,
                                         String userPrompt,
                                         Map<String, Object> context,
                                         String responseMode) {
        ObjectNode body = objectMapper.createObjectNode();
        ObjectNode inputs = objectMapper.createObjectNode();
        inputs.put("tenantId", extractString(context, "tenantId", "system"));
        inputs.put("taskId", extractString(context, "taskId", "task-" + UUID.randomUUID()));
        inputs.put("moduleKey", moduleKey);
        inputs.put("message", firstNotBlank(extractString(context, "message", ""), userPrompt));
        inputs.put("userQuestion", firstNotBlank(extractString(context, "userQuestion", ""), userPrompt));
        inputs.put("theme", firstNotBlank(extractString(context, "theme", ""), userPrompt));
        inputs.put("tone", extractString(context, "tone", ""));
        inputs.put("platform", moduleKey);
        inputs.put("instruction", finalAnswerInstruction());
        appendCanonicalContexts(inputs, context);
        appendParams(inputs, context);
        inputs.put("systemPrompt", defaultString(systemPrompt, "") + "\n\n" + finalAnswerInstruction());
        inputs.put("userPrompt", defaultString(userPrompt, ""));
        inputs.put("knowledge", serializeValue(context == null ? null : context.get("knowledge")));
        inputs.put("confirmedKnowledgeText", extractString(context, "confirmedKnowledgeText", ""));
        inputs.put("hotelContext", serializeValue(context == null ? null : context.get("hotelContext")));
        inputs.put("commonContext", serializeValue(context == null ? null : context.get("commonContext")));
        inputs.put("context", serializeContext(context));
        inputs.put("selectedParamsJson", firstNotBlank(extractString(context, "selectedParamsJson", ""), serializeValue(context == null ? null : context.get("params"))));
        inputs.put("currentTime", extractString(context, "currentTime", ""));
        appendDifyFileInputs(inputs, context);
        body.set("inputs", inputs);
        body.put("response_mode", responseMode);
        body.put("user", "sushijia-" + extractString(context, "tenantId", "system"));
        return body;
    }

    private void applyInputSchema(ObjectNode body, String inputSchema, Map<String, Object> context) {
        if (isBlank(inputSchema) || body == null || !body.path("inputs").isObject()) {
            return;
        }
        try {
            JsonNode schema = objectMapper.readTree(inputSchema);
            if (!schema.isObject()) {
                return;
            }
            ObjectNode inputs = (ObjectNode) body.path("inputs");
            schema.fieldNames().forEachRemaining(name -> {
                if (!inputs.has(name)) {
                    if (hasDifyUploadedFile(context) && isLikelyFileInputName(name)) {
                        putDifyFileInput(inputs, name, String.valueOf(context.get("_difyUploadFileId")), uploadedDifyFileType(context));
                    } else {
                        inputs.put(name, "");
                    }
                }
            });
        } catch (Exception e) {
            log.warn("Invalid Dify input schema ignored. schema={}", inputSchema, e);
        }
    }

    @SuppressWarnings("unchecked")
    private void appendParams(ObjectNode inputs, Map<String, Object> context) {
        if (context == null || !(context.get("params") instanceof Map)) {
            return;
        }
        Map<String, Object> params = (Map<String, Object>) context.get("params");
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String key = entry.getKey();
            if (isBlank(key)
                || entry.getValue() == null
                || "moduleKey".equals(key)
                || "resetConversation".equals(key)
                || key.startsWith("_dify")) {
                continue;
            }
            putDifyInput(inputs, key, entry.getValue());
        }
    }

    private void putDifyInput(ObjectNode inputs, String key, Object value) {
        if (value == null) {
            inputs.put(key, "");
            return;
        }
        if (value instanceof Number) {
            inputs.set(key, objectMapper.valueToTree(value));
            return;
        }
        if (value instanceof Boolean) {
            inputs.put(key, (Boolean) value);
            return;
        }
        if (value instanceof CharSequence) {
            inputs.put(key, value.toString());
            return;
        }
        inputs.put(key, serializeValue(value));
    }

    private ObjectNode buildChatflowBody(String moduleKey,
                                         String userPrompt,
                                         Map<String, Object> context,
                                         String responseMode) {
        ObjectNode body = objectMapper.createObjectNode();
        ObjectNode inputs = objectMapper.createObjectNode();
        inputs.put("tenantId", extractString(context, "tenantId", "system"));
        inputs.put("hotelName", extractString(context, "hotelName", ""));
        inputs.put("moduleKey", moduleKey);
        inputs.put("message", firstNotBlank(extractString(context, "message", ""), userPrompt));
        inputs.put("userQuestion", firstNotBlank(extractString(context, "userQuestion", ""), userPrompt));
        inputs.put("instruction", finalAnswerInstruction());
        appendCanonicalContexts(inputs, context);
        inputs.put("knowledge", serializeValue(context == null ? null : context.get("knowledge")));
        inputs.put("confirmedKnowledgeText", extractString(context, "confirmedKnowledgeText", ""));
        inputs.put("hotelContext", serializeValue(context == null ? null : context.get("hotelContext")));
        inputs.put("commonContext", serializeValue(context == null ? null : context.get("commonContext")));
        inputs.put("context", serializeContext(context));
        inputs.put("selectedParamsJson", firstNotBlank(extractString(context, "selectedParamsJson", ""), serializeValue(context == null ? null : context.get("params"))));
        inputs.put("taskId", extractString(context, "taskId", ""));
        inputs.put("roomSnapshot", serializeValue(context == null ? null : context.get("roomSnapshot")));
        inputs.put("reviewText", extractString(context, "reviewText", ""));
        inputs.put("platform", extractString(context, "platform", moduleKey));
        inputs.put("sourceType", extractString(context, "sourceType", moduleKey));
        inputs.put("sourceContent", firstNotBlank(extractString(context, "sourceContent", ""), userPrompt));
        inputs.put("existingKnowledge", serializeValue(context == null ? null : context.get("knowledge")));
        inputs.put("currentTime", extractString(context, "currentTime", ""));
        inputs.put("imagePromptOverride", extractString(context, "imagePromptOverride", ""));
        appendParams(inputs, context);
        appendDifyFileInputs(inputs, context);
        body.set("inputs", inputs);
        body.put("query", defaultString(userPrompt, "\u8bf7\u7ed9\u51fa\u4e00\u6761\u9152\u5e97\u8fd0\u8425\u5efa\u8bae\u3002"));
        body.put("response_mode", responseMode);
        body.put("user", difyUser(context));
        return body;
    }

    @SuppressWarnings("unchecked")
    private String uploadDifyFileIfPresent(String endpoint,
                                           String apiKey,
                                           Map<String, Object> context,
                                           String requestUser) {
        if (context == null || !(context.get("params") instanceof Map<?, ?> params)) {
            return null;
        }
        Object encoded = ((Map<String, Object>) params).get("_difyFileBase64");
        if (encoded == null || isBlank(String.valueOf(encoded))) {
            return null;
        }
        String fileName = firstNotBlank(String.valueOf(((Map<String, Object>) params).getOrDefault("_difyFileName", "")), "upload.bin");
        String contentType = firstNotBlank(String.valueOf(((Map<String, Object>) params).getOrDefault("_difyFileContentType", "")), MediaType.APPLICATION_OCTET_STREAM_VALUE);
        byte[] bytes = Base64.getDecoder().decode(String.valueOf(encoded));
        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
        MultiValueMap<String, Object> multipart = new LinkedMultiValueMap<>();
        multipart.add("file", resource);
        multipart.add("user", requestUser);
        DifyHttpResult uploadResult = buildClient().post()
            .uri(endpoint + "/files/upload")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .header("Authorization", "Bearer " + apiKey)
            .body(BodyInserters.fromMultipartData(multipart))
            .exchangeToMono(response -> response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> new DifyHttpResult(response.statusCode().value(), body)))
            .timeout(Duration.ofSeconds(getTimeoutSeconds()))
            .block();
        if (uploadResult == null || uploadResult.statusCode < 200 || uploadResult.statusCode >= 300) {
            String body = uploadResult == null ? "" : uploadResult.body;
            log.error("Dify file upload failed. status={}, body={}",
                uploadResult == null ? null : uploadResult.statusCode,
                body);
            throw new BizException(ResultCode.AI_GENERATE_FAILED, firstNotBlank(extractErrorMessage(body), "Dify 文件上传失败"));
        }
        String response = uploadResult.body;
        try {
            JsonNode root = objectMapper.readTree(response);
            String id = asText(root.path("id"));
            if (!isBlank(id)) {
                ((Map<String, Object>) params).put("_difyUploadedFileType", difyFileType(contentType));
                return id;
            }
        } catch (Exception e) {
            throw new BizException(ResultCode.AI_GENERATE_FAILED, "Dify 文件上传失败");
        }
        throw new BizException(ResultCode.AI_GENERATE_FAILED, "Dify 文件上传失败");
    }

    @SuppressWarnings("unchecked")
    private void appendDifyFileInputs(ObjectNode inputs, Map<String, Object> context) {
        if (context == null || !(context.get("params") instanceof Map<?, ?> params)) {
            return;
        }
        String uploadFileId = String.valueOf(context.getOrDefault("_difyUploadFileId", ""));
        if (isBlank(uploadFileId)) {
            return;
        }
        Map<String, Object> typed = (Map<String, Object>) params;
        String type = firstNotBlank(String.valueOf(typed.getOrDefault("_difyUploadedFileType", "")), "video");
        inputs.put("uploadedFileName", String.valueOf(typed.getOrDefault("_difyFileName", "")));
        String preferred = firstNotBlank(String.valueOf(typed.getOrDefault("_difyFileVariable", "")), "video");
        putDifyFileInput(inputs, preferred, uploadFileId, type);
        putDifyFileInput(inputs, "fileName", uploadFileId, type);
        putDifyFileInput(inputs, "file", uploadFileId, type);
        putDifyFileInput(inputs, "videoFile", uploadFileId, type);
        putDifyFileInput(inputs, "uploadFile", uploadFileId, type);
        putDifyFileInput(inputs, "video_file", uploadFileId, type);
        putDifyFileInput(inputs, "upload_file", uploadFileId, type);
        putDifyFileInput(inputs, "sourceFile", uploadFileId, type);
        putDifyFileInput(inputs, "source_file", uploadFileId, type);
    }

    private void putDifyFileInput(ObjectNode inputs, String key, String uploadFileId, String type) {
        if (isBlank(key)) {
            return;
        }
        JsonNode existing = inputs.get(key);
        if (existing != null
            && existing.isObject()
            && !isBlank(asText(existing.path("upload_file_id")))) {
            return;
        }
        ObjectNode file = objectMapper.createObjectNode();
        file.put("type", type);
        file.put("transfer_method", "local_file");
        file.put("upload_file_id", uploadFileId);
        inputs.set(key, file);
    }

    private void pruneOccupancyImageInputs(String moduleKey, ObjectNode body) {
        if (!"occupancy_image".equalsIgnoreCase(moduleKey)
            || body == null
            || !body.path("inputs").isObject()) {
            return;
        }
        HashSet<String> allowed = new HashSet<>(Arrays.asList(
            "commonContextJson",
            "businessParamsJson",
            "knowledgeContextJson",
            "message",
            "sourceFileName",
            "sourceFileType",
            "sourceFileSize",
            "image"
        ));
        ObjectNode inputs = (ObjectNode) body.path("inputs");
        List<String> removeNames = new ArrayList<>();
        Iterator<String> names = inputs.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!allowed.contains(name)) {
                removeNames.add(name);
            }
        }
        removeNames.forEach(inputs::remove);
    }

    private String difyFileType(String contentType) {
        String type = String.valueOf(contentType == null ? "" : contentType).toLowerCase();
        if (type.startsWith("image/")) return "image";
        if (type.startsWith("audio/")) return "audio";
        if (type.startsWith("video/")) return "video";
        return "document";
    }

    private boolean hasDifyUploadedFile(Map<String, Object> context) {
        return context != null && !isBlank(String.valueOf(context.getOrDefault("_difyUploadFileId", "")));
    }

    private String uploadedDifyFileType(Map<String, Object> context) {
        if (context == null || !(context.get("params") instanceof Map<?, ?> params)) {
            return "video";
        }
        Object type = params.get("_difyUploadedFileType");
        return isBlank(String.valueOf(type)) ? "video" : String.valueOf(type);
    }

    private boolean isLikelyFileInputName(String name) {
        String value = String.valueOf(name == null ? "" : name).toLowerCase();
        return value.contains("file") || value.contains("video") || value.contains("audio") || value.contains("image") || value.contains("upload");
    }

    private String difyUser(Map<String, Object> context) {
        return "sushijia-" + extractString(context, "tenantId", "system")
            + "-" + extractString(context, "userId", "anonymous");
    }

    private String workflowDifyUser(Map<String, Object> context) {
        return "sushijia-" + extractString(context, "tenantId", "system");
    }

    private String conversationKey(String moduleKey, AiAgentBinding binding, Map<String, Object> context) {
        return "ai:dify:conversation:"
            + extractString(context, "tenantId", "system") + ":"
            + extractString(context, "userId", "anonymous") + ":"
            + moduleKey + ":"
            + String.valueOf(binding.getId());
    }

    @SuppressWarnings("unchecked")
    private boolean shouldResetConversation(Map<String, Object> context) {
        if (context == null || !(context.get("params") instanceof Map<?, ?>)) {
            return false;
        }
        Object value = ((Map<String, Object>) context.get("params")).get("resetConversation");
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    private String loadConversation(String key) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (!isBlank(value)) {
                localConversations.put(key, value);
                return value;
            }
        } catch (Exception e) {
            log.warn("Unable to read Dify conversation from Redis; using local cache. key={}", key);
        }
        return localConversations.get(key);
    }

    private void saveConversation(String key, String conversationId) {
        localConversations.put(key, conversationId);
        try {
            redisTemplate.opsForValue().set(key, conversationId, Duration.ofDays(30));
            log.info("Dify conversation saved. key={}, conversationId={}", key, conversationId);
        } catch (Exception e) {
            log.warn("Unable to persist Dify conversation to Redis; using local cache. key={}", key);
        }
    }

    private void clearConversation(String key) {
        localConversations.remove(key);
        try {
            redisTemplate.delete(key);
            log.info("Dify conversation cleared. key={}", key);
        } catch (Exception e) {
            log.warn("Unable to clear Dify conversation in Redis. key={}", key);
        }
    }

    private void appendCanonicalContexts(ObjectNode inputs, Map<String, Object> context) {
        inputs.put("commonContextJson", extractString(context, "commonContextJson", "{}"));
        inputs.put("businessParamsJson", extractString(context, "businessParamsJson", "{}"));
        inputs.put("knowledgeContextJson", extractString(context, "knowledgeContextJson", "{}"));
        inputs.put("surroundingContextJson", extractString(context, "surroundingContextJson", ""));
    }

    private String extractContent(String response, String appType) throws Exception {
        if (isBlank(response)) {
            return null;
        }
        if (response.contains("data:") || response.contains("\"event\"")) {
            return extractStreamContent(response);
        }

        JsonNode root = objectMapper.readTree(response);
        if (!isBlank(asText(root.path("message"))) && root.has("code")) {
            return null;
        }
        if ("chatflow".equalsIgnoreCase(appType)) {
            String answer = asText(root.path("answer"));
            if (!isBlank(answer)) {
                return answer;
            }
        }
        String output = extractOutputs(root.path("data").path("outputs"));
        if (!isBlank(output)) {
            return output;
        }
        output = extractOutputs(root.path("outputs"));
        if (!isBlank(output)) {
            return output;
        }
        String dataText = asText(root.path("data").path("text"));
        if (!isBlank(dataText)) {
            return dataText;
        }
        String answer = asText(root.path("answer"));
        return isBlank(answer) ? null : answer;
    }

    private String extractStreamContent(String stream) throws Exception {
        StreamAccumulator accumulator = new StreamAccumulator(objectMapper, null);
        accumulator.accept(stream);
        return accumulator.result();
    }

    private String extractOutputs(JsonNode outputs) {
        if (outputs == null || outputs.isMissingNode() || outputs.isNull()) {
            return null;
        }
        if (outputs.has("records") || outputs.has("warnings")) {
            return outputs.toString();
        }
        String output = firstText(
            outputs.path("output"),
            outputs.path("records"),
            outputs.path("text"),
            outputs.path("answer"),
            outputs.path("content"),
            outputs.path("warnings"),
            outputs.path("article"),
            outputs.path("articleContent"),
            outputs.path("article_content"),
            outputs.path("markdown"),
            outputs.path("body"),
            outputs.path("result"),
            outputs.path("data")
        );
        if (!isBlank(output)) {
            return output;
        }
        return outputs.isObject() && outputs.size() > 0 ? outputs.toString() : null;
    }

    private String sanitizeFinalAnswer(String content) {
        if (isBlank(content)) {
            return content;
        }
        String normalized = content
            .replaceAll("(?is)<think>.*?</think>", "")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim();
        if (isBlank(normalized)) {
            return null;
        }

        String[] lines = normalized.split("\n");
        StringBuilder cleaned = new StringBuilder();
        boolean finalAnswerStarted = false;
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                if (finalAnswerStarted && cleaned.length() > 0 && cleaned.charAt(cleaned.length() - 1) != '\n') {
                    cleaned.append('\n');
                }
                continue;
            }
            if (!finalAnswerStarted && isInternalReasoningLine(line)) {
                continue;
            }
            if (isEnglishSuggestionLine(line)) {
                continue;
            }
            finalAnswerStarted = true;
            cleaned.append(rawLine.stripTrailing()).append('\n');
        }

        String result = cleaned.toString().trim();
        return isBlank(result) ? normalized : result;
    }

    private boolean isInternalReasoningLine(String line) {
        String compact = line.replace(" ", "");
        return compact.startsWith("\u6211\u4eec\u6839\u636e")
            || compact.startsWith("\u7528\u6237\u63d0\u4f9b")
            || compact.startsWith("\u9152\u5e97\u4e0a\u4e0b\u6587")
            || compact.startsWith("\u5185\u5bb9\u6784\u601d")
            || compact.startsWith("\u6ce8\u610f")
            || compact.startsWith("\u6309\u7167")
            || compact.startsWith("\u4f5c\u4e3aAI")
            || compact.startsWith("\u6211\u9700\u8981")
            || compact.startsWith("\u6211\u5e94\u8be5")
            || compact.startsWith("\u53ef\u80fd\u662f")
            || compact.startsWith("\u8fd9\u91cc")
            || compact.startsWith("\u9996\u5148")
            || compact.startsWith("\u5206\u6790")
            || compact.startsWith("\u601d\u8003")
            || line.startsWith("I need")
            || line.startsWith("We need")
            || line.startsWith("The user");
    }

    private boolean isEnglishSuggestionLine(String line) {
        return line.trim().matches("(?i)^(Help me|Give me|Write|Create|Generate|Tell me)\\b.*");
    }

    private String finalAnswerInstruction() {
        return "\u53ea\u8f93\u51fa\u7ed9\u9152\u5e97\u7ecf\u8425\u8005\u770b\u7684\u6700\u7ec8\u7b54\u6848\u3002"
            + "\u7981\u6b62\u8f93\u51fa\u63a8\u7406\u8fc7\u7a0b\u3001\u610f\u56fe\u5206\u6790\u3001\u89d2\u8272\u81ea\u8ff0\u3001\u82f1\u6587\u5efa\u8bae\u95ee\u9898\u6216\u8c03\u8bd5\u4fe1\u606f\u3002"
            + "\u56de\u7b54\u5fc5\u987b\u4f7f\u7528\u4e2d\u6587\uff0c\u7b80\u6d01\u3001\u5177\u4f53\u3001\u53ef\u6267\u884c\u3002";
    }

    private String serializeContext(Map<String, Object> context) {
        try {
            return context == null ? "{}" : objectMapper.writeValueAsString(context);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String serializeValue(Object value) {
        try {
            return value == null ? "[]" : objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String summarizeRequest(ObjectNode body) {
        ObjectNode summary = objectMapper.createObjectNode();
        summary.set("inputs", body.path("inputs"));
        if (body.has("query")) {
            summary.put("query", asText(body.path("query")));
        }
        summary.put("response_mode", asText(body.path("response_mode")));
        summary.put("user", asText(body.path("user")));
        return summary.toString();
    }

    private String summarizeResponse(String response, String content) {
        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("stream", response != null && (response.contains("data:") || response.contains("\"event\"")));
        summary.put("content", truncate(content, 500));
        return summary.toString();
    }

    private String extractErrorMessage(String response) {
        if (isBlank(response)) {
            return "Dify request failed";
        }
        String trimmed = response.trim();
        if (looksLikeHtml(trimmed)) {
            String lower = trimmed.toLowerCase();
            if (lower.contains("413") || lower.contains("payload too large")) {
                return "Dify 文件或请求体过大，请压缩视频后再上传";
            }
            return firstNotBlank(extractHtmlTitle(trimmed), truncate(stripHtml(trimmed), 500));
        }
        try {
            JsonNode root = objectMapper.readTree(trimmed);
            String message = asText(root.path("message"));
            if (!isBlank(message)) {
                return message;
            }
            String error = asText(root.path("error"));
            return isBlank(error) ? "Dify request failed" : error;
        } catch (Exception e) {
            return truncate(response, 500);
        }
    }

    private boolean looksLikeHtml(String value) {
        String lower = value == null ? "" : value.trim().toLowerCase();
        return lower.startsWith("<!doctype html") || lower.startsWith("<html") || lower.contains("<body");
    }

    private String extractHtmlTitle(String html) {
        if (html == null) {
            return "";
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
            .compile("(?is)<title>(.*?)</title>")
            .matcher(html);
        return matcher.find() ? stripHtml(matcher.group(1)).trim() : "";
    }

    private String stripHtml(String html) {
        return html == null ? "" : html.replaceAll("(?is)<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    private String extractString(Map<String, Object> context, String key, String defaultValue) {
        if (context == null || context.get(key) == null) {
            return defaultValue;
        }
        return String.valueOf(context.get(key));
    }

    private String getSetting(String key, String defaultValue) {
        String val = null;
        String cacheKey = "sushijia:settings:" + key;
        try {
            val = redisTemplate.opsForValue().get(cacheKey);
        } catch (Exception ignored) {
            // Redis is an acceleration layer only.
        }
        if (isBlank(val)) {
            val = settingMapper.findValueByKey(key);
            if (!isBlank(val)) {
                try {
                    redisTemplate.opsForValue().set(cacheKey, val, 5, TimeUnit.MINUTES);
                } catch (Exception ignored) {
                    // Redis is an acceleration layer only.
                }
            }
        }
        return !isBlank(val) ? val.trim() : defaultValue;
    }

    private AiAgentBinding getBinding(String moduleKey) {
        String cacheKey = "sushijia:ai:binding:" + moduleKey;
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (!isBlank(cached)) {
                return objectMapper.readValue(cached, AiAgentBinding.class);
            }
        } catch (Exception ignored) {
            // Redis is an acceleration layer only.
        }
        AiAgentBinding binding = agentBindingMapper.findEnabledByModuleKey(moduleKey);
        if (binding != null) {
            try {
                redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(binding), 5, TimeUnit.MINUTES);
            } catch (Exception ignored) {
                // Redis is an acceleration layer only.
            }
        }
        return binding;
    }

    private String getResponseMode() {
        String mode = getSetting("dify_response_mode", "streaming");
        return "blocking".equalsIgnoreCase(mode) ? "blocking" : "streaming";
    }

    private String resolveAppType(String moduleKey, AiAgentBinding binding) {
        return defaultString(firstNotBlank(binding.getAppType(), binding.getBotId()), "chatflow");
    }

    private int getTimeoutSeconds() {
        try {
            return Math.max(180, Integer.parseInt(getSetting("dify_timeout_seconds", "180")));
        } catch (NumberFormatException e) {
            return 180;
        }
    }

    private int getTimeoutSeconds(String moduleKey) {
        int base = getTimeoutSeconds();
        if ("occupancy_image".equalsIgnoreCase(moduleKey)) {
            try {
                return Math.max(base, Integer.parseInt(getSetting("dify_occupancy_image_timeout_seconds", "420")));
            } catch (NumberFormatException e) {
                return Math.max(base, 420);
            }
        }
        return base;
    }

    private int getRetryTimes() {
        try {
            return Math.max(0, Math.min(3, Integer.parseInt(getSetting("dify_retry_times", "2"))));
        } catch (NumberFormatException e) {
            return 2;
        }
    }

    private WebClient buildClient() {
        return buildClient(getTimeoutSeconds());
    }

    private WebClient buildClient(int timeoutSeconds) {
        HttpClient httpClient = HttpClient.create()
            .keepAlive(false)
            .responseTimeout(Duration.ofSeconds(timeoutSeconds + 10L));
        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
            .build();
    }

    private boolean isTimeoutException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof java.util.concurrent.TimeoutException) {
                return true;
            }
            String name = current.getClass().getName();
            if (name != null && name.toLowerCase().contains("timeout")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isTransientNetworkError(Throwable throwable) {
        Throwable unwrapped = unwrapRetryCause(throwable);
        if (unwrapped != throwable) {
            return isTransientNetworkError(unwrapped);
        }
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof WebClientRequestException) {
                String message = current.getMessage();
                return message != null && (message.contains("Connection reset") || message.contains("Connection prematurely closed"));
            }
            String message = current.getMessage();
            if (message != null && (message.contains("Connection reset") || message.contains("Connection prematurely closed"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private Throwable unwrapRetryCause(Throwable throwable) {
        if (throwable == null) {
            return new RuntimeException("Unknown Dify error");
        }
        if (Exceptions.isRetryExhausted(throwable) && throwable.getCause() != null) {
            return unwrapRetryCause(throwable.getCause());
        }
        return throwable;
    }

    private String normalizeEndpoint(String endpoint) {
        String value = defaultString(endpoint, DEFAULT_ENDPOINT).trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String firstNotBlank(String first, String second) {
        return !isBlank(first) ? first.trim() : defaultString(second, "").trim();
    }

    private String firstText(JsonNode... nodes) {
        if (nodes == null) {
            return "";
        }
        for (JsonNode node : nodes) {
            String value = asText(node);
            if (!isBlank(value)) {
                return value;
            }
        }
        return "";
    }

    private String defaultString(String value, String defaultValue) {
        return value == null ? defaultValue : value;
    }

    private String asText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.isTextual() ? node.asText() : node.toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }

    private String truncate(String value, int limit) {
        if (value == null) {
            return null;
        }
        return value.length() <= limit ? value : value.substring(0, limit) + "...";
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage();
    }

    private record DifyHttpResult(int statusCode, String body) {
    }

    private static final class StreamAccumulator {
        private final ObjectMapper mapper;
        private final Consumer<String> onChunk;
        private final ThinkFilter thinkFilter = new ThinkFilter();
        private final StringBuilder raw = new StringBuilder();
        private final StringBuilder buffer = new StringBuilder();
        private final StringBuilder emitted = new StringBuilder();
        private String finalOutput;
        private String conversationId;

        private StreamAccumulator(ObjectMapper mapper, Consumer<String> onChunk) {
            this.mapper = mapper;
            this.onChunk = onChunk;
        }

        private void accept(String piece) {
            if (piece == null || piece.isEmpty()) {
                return;
            }
            raw.append(piece);
            buffer.append(piece.replace("\r\n", "\n").replace('\r', '\n').replace("}{", "}\n{"));
            drainBufferedEvents();
        }

        private String raw() {
            drainBufferedEvents();
            return raw.toString();
        }

        private String result() {
            drainBufferedEvents();
            if (emitted.length() > 0) {
                return emitted.toString();
            }
            return finalOutput;
        }

        private boolean hasEmitted() {
            return emitted.length() > 0;
        }

        private int emittedLength() {
            return emitted.length();
        }

        private String conversationId() {
            return conversationId;
        }

        private boolean hasMissingFinalContent(String content) {
            if (onChunk == null || content == null || content.isBlank()) {
                return false;
            }
            if (!hasEmitted()) {
                return true;
            }
            String emittedText = emitted.toString();
            return content.length() > emittedText.length() && content.startsWith(emittedText);
        }

        private void emitMissingFinal(String content) {
            if (content == null || content.isBlank()) {
                return;
            }
            if (!hasEmitted()) {
                emitFallbackFinal(content);
                return;
            }
            String emittedText = emitted.toString();
            if (!content.startsWith(emittedText)) {
                return;
            }
            emitFallbackFinal(content.substring(emittedText.length()));
        }

        private void emitFallbackFinal(String content) {
            if (onChunk == null || content == null || content.isBlank()) {
                return;
            }
            int index = 0;
            while (index < content.length()) {
                int next = nextFallbackBoundary(content, index);
                emitClean(content.substring(index, next));
                index = next;
                if (index < content.length()) {
                    sleepQuietly(32L);
                }
            }
        }

        private void drainBufferedEvents() {
            while (true) {
                int separator = buffer.indexOf("\n\n");
                if (separator >= 0) {
                    String event = buffer.substring(0, separator);
                    buffer.delete(0, separator + 2);
                    parseEvent(event);
                    continue;
                }
                int newline = buffer.indexOf("\n");
                if (newline >= 0) {
                    String line = buffer.substring(0, newline).trim();
                    if (line.startsWith("{") || line.startsWith("data:")) {
                        buffer.delete(0, newline + 1);
                        parseEvent(line);
                        continue;
                    }
                }
                String trimmed = buffer.toString().trim();
                if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                    parseEvent(trimmed);
                    buffer.setLength(0);
                }
                return;
            }
        }

        private void parseEvent(String event) {
            String data = extractData(event);
            if (data.isBlank() || "[DONE]".equals(data)) {
                return;
            }
            try {
                JsonNode node = mapper.readTree(data);
                String eventName = firstNotBlank(text(node.path("event")), extractSseEventName(event));
                String eventConversationId = firstNotBlank(
                    text(node.path("conversation_id")),
                    text(node.path("data").path("conversation_id")),
                    findConversationId(node)
                );
                if (!eventConversationId.isBlank()) {
                    conversationId = eventConversationId;
                }
                String delta = extractDelta(node, eventName);
                if (!delta.isBlank()) {
                    emit(delta);
                }
                String candidate = extractFinal(node, eventName);
                if (!candidate.isBlank()) {
                    finalOutput = candidate;
                }
            } catch (Exception ignored) {
                // Non-json stream frames are ignored.
            }
        }

        private String extractData(String event) {
            String trimmed = event == null ? "" : event.trim();
            if (!trimmed.contains("data:")) {
                return trimmed;
            }
            StringBuilder data = new StringBuilder();
            for (String line : trimmed.split("\n")) {
                if (line.startsWith("data:")) {
                    if (data.length() > 0) {
                        data.append('\n');
                    }
                    data.append(line.substring(5).trim());
                }
            }
            return data.toString();
        }

        private String extractSseEventName(String event) {
            if (event == null || event.isBlank()) {
                return "";
            }
            for (String line : event.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("event:")) {
                    return trimmed.substring(6).trim();
                }
            }
            return "";
        }

        private String extractDelta(JsonNode node, String eventName) {
            if ("message".equals(eventName) || "agent_message".equals(eventName)) {
                return firstText(
                    node.path("answer"),
                    node.path("text"),
                    node.path("content"),
                    node.path("message"),
                    node.path("delta"),
                    node.path("data").path("answer"),
                    node.path("data").path("text"),
                    node.path("data").path("content"),
                    node.path("data").path("message"),
                    node.path("data").path("delta")
                );
            }
            if ("text_chunk".equals(eventName)) {
                return firstText(
                    node.path("text"),
                    node.path("chunk"),
                    node.path("answer"),
                    node.path("content"),
                    node.path("data").path("text"),
                    node.path("data").path("chunk"),
                    node.path("data").path("answer"),
                    node.path("data").path("content")
                );
            }
            if (eventName.isBlank() && node.has("answer")) {
                return text(node.path("answer"));
            }
            return "";
        }

        private String extractFinal(JsonNode node, String eventName) {
            if ("message".equals(eventName) || "agent_message".equals(eventName) || "text_chunk".equals(eventName)) {
                return "";
            }
            return firstNotBlank(
                output(node.path("data").path("outputs")),
                output(node.path("outputs")),
                text(node.path("answer")),
                text(node.path("data").path("text"))
            );
        }

        private String output(JsonNode outputs) {
            if (outputs == null || outputs.isMissingNode() || outputs.isNull()) {
                return "";
            }
            if (outputs.has("records") || outputs.has("warnings")) {
                return outputs.toString();
            }
            String value = firstText(
                outputs.path("output"),
                outputs.path("records"),
                outputs.path("text"),
                outputs.path("answer"),
                outputs.path("content"),
                outputs.path("warnings"),
                outputs.path("article"),
                outputs.path("articleContent"),
                outputs.path("article_content"),
                outputs.path("markdown"),
                outputs.path("body"),
                outputs.path("result"),
                outputs.path("data")
            );
            return value.isBlank() && outputs.isObject() && outputs.size() > 0 ? outputs.toString() : value;
        }

        private void emit(String delta) {
            String clean = thinkFilter.accept(delta);
            if (clean.isBlank()) {
                return;
            }
            emitClean(clean);
        }

        private void emitClean(String clean) {
            if (clean == null || clean.isBlank()) {
                return;
            }
            emitted.append(clean);
            if (onChunk != null) {
                log.debug("Dify stream delta emitted. chars={}", clean.length());
                onChunk.accept(clean);
            }
        }

        private int nextFallbackBoundary(String content, int start) {
            int min = Math.min(content.length(), start + 3);
            int max = Math.min(content.length(), start + 8);
            for (int i = min; i < max; i++) {
                char ch = content.charAt(i);
                if (ch == '\n' || ch == '。' || ch == '！' || ch == '？' || ch == ';' || ch == '；' || ch == ',' || ch == '，') {
                    return i + 1;
                }
            }
            return max;
        }

        private void sleepQuietly(long millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        private String firstText(JsonNode... nodes) {
            for (JsonNode node : nodes) {
                String value = text(node);
                if (!value.isBlank()) {
                    return value;
                }
            }
            return "";
        }

        private String firstNotBlank(String... values) {
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
            return "";
        }

        private String findConversationId(JsonNode node) {
            if (node == null || node.isMissingNode() || node.isNull()) {
                return "";
            }
            if (node.isObject()) {
                JsonNode direct = node.get("conversation_id");
                String directValue = text(direct);
                if (!directValue.isBlank()) {
                    return directValue;
                }
                var fields = node.fields();
                while (fields.hasNext()) {
                    String value = findConversationId(fields.next().getValue());
                    if (!value.isBlank()) {
                        return value;
                    }
                }
                return "";
            }
            if (node.isArray()) {
                for (JsonNode item : node) {
                    String value = findConversationId(item);
                    if (!value.isBlank()) {
                        return value;
                    }
                }
            }
            return "";
        }

        private String text(JsonNode node) {
            if (node == null || node.isMissingNode() || node.isNull()) {
                return "";
            }
            return node.isTextual() ? node.asText() : node.toString();
        }
    }

    private static final class ThinkFilter {
        private boolean inThink;

        private String accept(String input) {
            if (input == null || input.isEmpty()) {
                return "";
            }
            String rest = input;
            StringBuilder out = new StringBuilder();
            while (!rest.isEmpty()) {
                if (inThink) {
                    int end = indexOfIgnoreCase(rest, "</think>");
                    if (end < 0) {
                        return out.toString();
                    }
                    rest = rest.substring(end + "</think>".length());
                    inThink = false;
                    continue;
                }

                int start = indexOfIgnoreCase(rest, "<think>");
                if (start < 0) {
                    out.append(rest);
                    break;
                }
                out.append(rest, 0, start);
                rest = rest.substring(start + "<think>".length());
                inThink = true;
            }
            return out.toString();
        }

        private int indexOfIgnoreCase(String source, String target) {
            return source.toLowerCase().indexOf(target.toLowerCase());
        }
    }
}
