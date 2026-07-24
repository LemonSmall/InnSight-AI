package com.sushijia.admin.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sushijia.common.response.R;
import com.sushijia.repository.entity.AiAgentBinding;
import com.sushijia.repository.mapper.AiAgentBindingMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin/dify")
@RequiredArgsConstructor
public class DifyValidationController {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AiAgentBindingMapper bindingMapper;

    @PostMapping("/validate")
    public R<Map<String, Object>> validate(@RequestBody ValidateRequest body) {
        AiAgentBinding binding = body.getBindingId() == null ? null : bindingMapper.selectById(body.getBindingId());
        String endpoint = normalizeEndpoint(firstNotBlank(body.getEndpoint(), binding == null ? null : binding.getEndpoint()));
        String requestApiKey = isInvalidApiKeyText(body.getApiKey()) ? null : body.getApiKey();
        String apiKey = binding != null && isBlank(requestApiKey)
            ? binding.getApiKey()
            : firstNotBlank(requestApiKey, binding == null ? null : binding.getApiKey());
        String appType = firstNotBlank(body.getAppType(), binding == null ? null : binding.getAppType());
        if (parseBaseEndpoint(endpoint) == null) {
            return R.fail(400, "Dify Endpoint is invalid");
        }
        if (isBlank(apiKey)) {
            return R.fail(400, "Dify App API Key is required");
        }
        if (!"workflow".equalsIgnoreCase(appType) && !"chatflow".equalsIgnoreCase(appType)) {
            return R.fail(400, "Dify App type is required");
        }

        try {
            HttpResult response = getJson(endpoint + "/parameters", apiKey);

            if (response.statusCode < 200 || response.statusCode >= 300) {
                log.warn("Dify validate failed. status={}, body={}", response.statusCode, response.body);
                return R.fail(500, "Dify connection failed: " + extractError(response.body));
            }

            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("status", "ok");
            result.put("appType", appType);
            return R.ok(result);
        } catch (Exception e) {
            log.warn("Dify validate exception. endpoint={}, appType={}", endpoint, appType, e);
            return R.fail(500, "Dify connection failed: " + rootMessage(e));
        }
    }

    private URI parseBaseEndpoint(String endpoint) {
        try {
            URI uri = URI.create(endpoint);
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                return null;
            }
            return isBlank(uri.getHost()) ? null : uri;
        } catch (Exception e) {
            return null;
        }
    }

    private HttpResult getJson(String url, String apiKey) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);

        int statusCode = connection.getResponseCode();
        InputStream stream = statusCode >= 200 && statusCode < 300
            ? connection.getInputStream()
            : connection.getErrorStream();
        String responseBody = readStream(stream);
        connection.disconnect();
        return new HttpResult(statusCode, responseBody);
    }

    private String readStream(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString();
    }

    private String extractError(String responseBody) {
        if (isBlank(responseBody)) {
            return "empty response";
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String message = asText(root.path("message"));
            if (!isBlank(message)) {
                return message;
            }
            String error = asText(root.path("error"));
            if (!isBlank(error)) {
                return error;
            }
            String code = asText(root.path("code"));
            if (!isBlank(code)) {
                return code;
            }
        } catch (Exception ignored) {
            // Return a short plain body below.
        }
        return responseBody.length() > 300 ? responseBody.substring(0, 300) : responseBody;
    }

    private String normalizeEndpoint(String endpoint) {
        String value = trim(endpoint);
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private String asText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.isTextual() ? node.asText() : node.toString();
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String firstNotBlank(String first, String second) {
        return !isBlank(first) ? first.trim() : trim(second);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean isInvalidApiKeyText(String value) {
        if (isBlank(value)) {
            return false;
        }
        String lower = value.toLowerCase();
        return lower.contains("dify connection failed") || lower.contains("access token is invalid");
    }

    @Data
    public static class ValidateRequest {
        private Long bindingId;
        private String endpoint;
        private String apiKey;
        private String appType;
        private String moduleKey;
    }

    private static class HttpResult {
        private final int statusCode;
        private final String body;

        private HttpResult(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }
    }
}
