package com.sushijia.hotel.controller;

import com.sushijia.common.exception.BizException;
import com.sushijia.common.response.ResultCode;
import com.sushijia.framework.tenant.TenantContext;
import com.sushijia.hotel.service.ContentGenerateService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api/ai/generations")
@RequiredArgsConstructor
@Slf4j
public class AiGenerationController {

    private final ContentGenerateService contentGenerateService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> stream(@RequestBody Map<String, Object> body,
                                                        HttpServletRequest request) {
        Long tenantId = TenantContext.get();
        Long userId = (Long) request.getAttribute("staffId");
        String moduleKey = extractModuleKey(body);
        Map<String, Object> params = extractParams(body);

        StreamingResponseBody stream = output -> {
            Object writeLock = new Object();
            AtomicBoolean open = new AtomicBoolean(true);
            Thread heartbeat = startHeartbeat(output, writeLock, open);
            try {
                sendSafe(output, writeLock, "status", Map.of("message", initialStatus(moduleKey, params)));
                Map<String, Object> result = contentGenerateService.generateStream(
                    tenantId,
                    userId,
                    moduleKey,
                    params,
                    message -> sendStatus(output, writeLock, message),
                    chunk -> sendChunk(output, writeLock, moduleKey, chunk)
                );

                Map<String, Object> done = new LinkedHashMap<String, Object>();
                done.put("content", result.get("content"));
                done.put("generationId", result.get("generationId"));
                done.put("costCredits", result.get("costCredits"));
                done.put("balance", result.get("balance"));
                sendSafe(output, writeLock, "done", done);
            } catch (Exception e) {
                log.error("AI stream generation failed. tenantId={}, module={}", tenantId, moduleKey, e);
                String message = toUserMessage(e);
                try {
                    sendSafe(output, writeLock, "error", Map.of("message", message));
                    sendSafe(output, writeLock, "done", Map.of("content", "", "error", message));
                } catch (Exception ignored) {
                    // Client may have disconnected.
                }
            } finally {
                open.set(false);
                heartbeat.interrupt();
                TenantContext.clear();
            }
        };

        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform")
            .header("X-Accel-Buffering", "no")
            .header(HttpHeaders.CONNECTION, "keep-alive")
            .contentType(MediaType.TEXT_EVENT_STREAM)
            .body(stream);
    }

    @PostMapping(value = "/stream-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> streamFile(@RequestParam("moduleKey") String moduleKey,
                                                            @RequestParam(value = "params", required = false) String paramsJson,
                                                            @RequestParam("file") MultipartFile file,
                                                            HttpServletRequest request) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("moduleKey", moduleKey);
            body.put("params", parseParamsJson(paramsJson));
            Map<String, Object> params = extractParams(body);
            attachDifyFile(moduleKey, params, file);
            body.put("params", params);
            return stream(body, request);
        } catch (Exception e) {
            return sseErrorResponse(toUserMessage(e));
        }
    }

    private ResponseEntity<StreamingResponseBody> sseErrorResponse(String message) {
        StreamingResponseBody stream = output -> {
            send(output, "error", Map.of("message", message));
            send(output, "done", Map.of("content", "", "error", message));
        };
        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform")
            .header("X-Accel-Buffering", "no")
            .contentType(MediaType.TEXT_EVENT_STREAM)
            .body(stream);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseParamsJson(String paramsJson) {
        if (paramsJson == null || paramsJson.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Object value = objectMapper.readValue(paramsJson, Object.class);
            if (value instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
        } catch (Exception e) {
            throw new BizException(ResultCode.BAD_REQUEST, "params JSON 格式不正确");
        }
        return new LinkedHashMap<>();
    }

    private void attachDifyFile(String moduleKey, Map<String, Object> params, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "上传文件不能为空");
        }
        try {
            params.put("_difyFileBase64", Base64.getEncoder().encodeToString(file.getBytes()));
            params.put("_difyFileName", file.getOriginalFilename());
            params.put("_difyFileContentType", file.getContentType());
            params.put("_difyFileVariable", difyFileVariable(moduleKey));
        } catch (IOException e) {
            throw new BizException(ResultCode.BAD_REQUEST, "读取上传文件失败");
        }
    }

    private String difyFileVariable(String moduleKey) {
        if ("article".equalsIgnoreCase(moduleKey)) {
            return "fileName";
        }
        if ("occupancy_image".equalsIgnoreCase(moduleKey)) {
            return "image";
        }
        return "video";
    }

    private String initialStatus(String moduleKey, Map<String, Object> params) {
        return "正在准备 AI 生成";
    }

    private String extractModuleKey(Map<String, Object> body) {
        Object value = body.getOrDefault("moduleKey", body.get("module"));
        if (value == null || String.valueOf(value).isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "moduleKey 不能为空");
        }
        return String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractParams(Map<String, Object> body) {
        Object params = body.get("params");
        if (params instanceof Map<?, ?>) {
            return (Map<String, Object>) params;
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> entry : body.entrySet()) {
            if (!"module".equals(entry.getKey()) && !"moduleKey".equals(entry.getKey())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    private Thread startHeartbeat(OutputStream output, Object writeLock, AtomicBoolean open) {
        Thread thread = new Thread(() -> {
            while (open.get()) {
                try {
                    Thread.sleep(1200L);
                    if (open.get()) {
                        sendSafe(output, writeLock, "heartbeat", Map.of("message", ""));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    open.set(false);
                    return;
                }
            }
        }, "ai-stream-heartbeat");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private void sendChunk(OutputStream output, Object writeLock, String moduleKey, String chunk) {
        try {
            log.debug("AI stream chunk. module={}, chars={}", moduleKey, chunk == null ? 0 : chunk.length());
            sendSafe(output, writeLock, "chunk", Map.of("text", chunk));
        } catch (Exception e) {
            log.debug("AI stream client disconnected; generation continues in background");
        }
    }

    private void sendStatus(OutputStream output, Object writeLock, String message) {
        try {
            sendSafe(output, writeLock, "status", Map.of("message", message == null ? "" : message));
        } catch (Exception e) {
            log.debug("AI stream status client disconnected");
        }
    }

    private void sendSafe(OutputStream output,
                          Object writeLock,
                          String eventName,
                          Map<String, Object> data) throws IOException {
        synchronized (writeLock) {
            send(output, eventName, data);
        }
    }

    private void send(OutputStream output,
                      String eventName,
                      Map<String, Object> data) throws IOException {
        String frame = "event: " + eventName + "\n"
            + "data: " + objectMapper.writeValueAsString(data) + "\n\n";
        output.write(frame.getBytes(StandardCharsets.UTF_8));
        output.flush();
    }

    private String toUserMessage(Exception e) {
        if (e instanceof BizException && e.getMessage() != null && !e.getMessage().isBlank()) {
            return e.getMessage();
        }
        return "AI 调用失败";
    }
}
