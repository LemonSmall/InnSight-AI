package com.sushijia.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sushijia.common.exception.BizException;
import com.sushijia.common.response.ResultCode;
import com.sushijia.repository.mapper.SystemSettingMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Service
public class AiClient {

    private static final String USER_AI_FAILED_MESSAGE = "AI生成失败，请稍后重试";
    private static final Map<String, String> PROVIDER_URLS = Map.of(
        "openai", "https://api.openai.com/v1/chat/completions",
        "deepseek", "https://api.deepseek.com/v1/chat/completions",
        "zhipu", "https://open.bigmodel.cn/api/paas/v4/chat/completions",
        "moonshot", "https://api.moonshot.cn/v1/chat/completions",
        "tongyi", "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
    );

    private final SystemSettingMapper settingMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiClient(SystemSettingMapper settingMapper) {
        this.settingMapper = settingMapper;
    }

    public String chat(String systemPrompt, String userPrompt) {
        return chat(systemPrompt, userPrompt, null, null);
    }

    public String chat(String systemPrompt, String userPrompt, String modelOverride, Integer maxTokensOverride) {
        String provider = getSetting("ai_provider", "openai");
        String apiKey = getSetting("ai_api_key", "");
        String model = modelOverride != null ? modelOverride : getSetting("ai_model", "gpt-4o");
        int maxTokens = maxTokensOverride != null
            ? maxTokensOverride
            : Integer.parseInt(getSetting("ai_max_tokens", "4000"));
        String url = resolveUrl(provider);

        if (apiKey.isBlank()) {
            log.warn("AI API key is not configured. provider={}, model={}", provider, model);
            throw new BizException(ResultCode.AI_GENERATE_FAILED, "AI服务未配置，请联系管理员");
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", maxTokens);

        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", userPrompt);

        try {
            log.info("Sending AI request. provider={}, model={}", provider, model);
            String response = buildClient().post()
                .uri(url)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(objectMapper.writeValueAsString(body))
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(60))
                .block();

            String content = extractContent(response);
            if (content == null || content.isBlank()) {
                log.error("AI response content is empty. provider={}, model={}, response={}", provider, model, response);
                throw new BizException(ResultCode.AI_GENERATE_FAILED, USER_AI_FAILED_MESSAGE);
            }
            return content;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI request failed. provider={}, model={}, url={}", provider, model, url, e);
            throw new BizException(ResultCode.AI_GENERATE_FAILED, USER_AI_FAILED_MESSAGE);
        }
    }

    private String getSetting(String key, String defaultValue) {
        String val = settingMapper.findValueByKey(key);
        return val != null && !val.isBlank() ? val : defaultValue;
    }

    private String resolveUrl(String provider) {
        String baseUrlOverride = getSetting("ai_base_url", "");
        return baseUrlOverride.isBlank()
            ? PROVIDER_URLS.getOrDefault(provider, PROVIDER_URLS.get("openai"))
            : baseUrlOverride;
    }

    private WebClient buildClient() {
        return WebClient.builder()
            .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
            .build();
    }

    private String extractContent(String response) throws Exception {
        if (response == null || response.isBlank()) {
            return null;
        }
        JsonNode choices = objectMapper.readTree(response).get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            return null;
        }
        JsonNode message = choices.get(0).get("message");
        return message != null && message.has("content") ? message.get("content").asText() : null;
    }
}
