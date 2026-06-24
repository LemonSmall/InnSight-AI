package com.sushijia.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sushijia.repository.mapper.SystemSettingMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * 通用 AI API 调用客户端
 * 支持 OpenAI / DeepSeek / 智谱 / Moonshot / 通义千问
 * 统一使用 OpenAI 兼容的 chat/completions 格式
 */
@Slf4j
@Service
public class AiClient {

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

    /** 读取AI配置 */
    private String getSetting(String key, String defaultValue) {
        String val = settingMapper.findValueByKey(key);
        return (val != null && !val.isEmpty()) ? val : defaultValue;
    }

    /** 构建 WebClient */
    private WebClient buildClient() {
        return WebClient.builder()
            .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
            .build();
    }

    /** 调用 AI 生成 */
    public String chat(String systemPrompt, String userPrompt) {
        return chat(systemPrompt, userPrompt, null, null);
    }

    /** 调用 AI 生成（可指定模型和 maxTokens） */
    public String chat(String systemPrompt, String userPrompt, String modelOverride, Integer maxTokensOverride) {
        String provider = getSetting("ai_provider", "openai");
        String apiKey = getSetting("ai_api_key", "");
        String model = (modelOverride != null) ? modelOverride : getSetting("ai_model", "gpt-4o");
        int maxTokens = (maxTokensOverride != null) ? maxTokensOverride : Integer.parseInt(getSetting("ai_max_tokens", "4000"));
        String baseUrlOverride = getSetting("ai_base_url", "");

        String url = baseUrlOverride.isEmpty()
            ? PROVIDER_URLS.getOrDefault(provider, PROVIDER_URLS.get("openai"))
            : baseUrlOverride;

        // 无 API Key 时回退到本地模式
        if (apiKey.isEmpty()) {
            log.warn("AI API Key 未配置，使用本地回退模式");
            return getLocalFallback(userPrompt);
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", maxTokens);

        ArrayNode messages = body.putArray("messages");
        ObjectNode sysMsg = messages.addObject();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemPrompt);
        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", userPrompt);

        try {
            String requestBody = objectMapper.writeValueAsString(body);
            log.info("AI请求: provider={}, model={}, url={}", provider, model, url);

            String response = buildClient().post()
                .uri(url)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(60))
                .block();

            if (response != null) {
                JsonNode root = objectMapper.readTree(response);
                JsonNode choices = root.get("choices");
                if (choices != null && choices.isArray() && choices.size() > 0) {
                    JsonNode message = choices.get(0).get("message");
                    if (message != null && message.has("content")) {
                        return message.get("content").asText();
                    }
                }
            }
            log.error("AI 返回格式异常: {}", response);
            return getLocalFallback(userPrompt);
        } catch (Exception e) {
            log.error("AI 调用失败", e);
            return getLocalFallback(userPrompt);
        }
    }

    /** 本地回退：简易模板生成，确保 AI 不可用时产品仍可运行 */
    private String getLocalFallback(String prompt) {
        if (prompt.contains("小红书")) {
            return "标题：莫干山秘境｜住进去就不想走了🌿\n\n正文：推开窗是漫山竹海，泡在私汤里看竹影摇曳。端午假期来山里充电，整个人都被治愈了。评论区扣1获取预订方式～\n\n标签：#莫干山民宿 #端午出行 #竹林民宿 #私汤温泉 #松间山野";
        }
        if (prompt.contains("朋友圈")) {
            return "早安🌿 清晨听竹雨，午后泡私汤。端午来莫干山，住进竹林里的家。\n#山居生活 #端午出行";
        }
        if (prompt.contains("视频") || prompt.contains("口播")) {
            return "你有没有想过，在山里藏着一片只有当地人知道的竹林？🌿 我在莫干山住了两天，最惊艳的是推开后门就是竹林。端午还剩最后几间。";
        }
        if (prompt.contains("海报")) {
            return "主标题：端午特惠\n副标题：连住2晚8.5折\n行动号召：立即预订 →\n视觉建议：深绿底色+竹林剪影+白色书法字体";
        }
        if (prompt.contains("公众号") || prompt.contains("推文")) {
            return "## 莫干山 · 端午运营手记\n\n雨中的竹林雾气缭绕，私汤温泉蒸汽升腾。提前发布预热推文、推出限定套餐、嵌入预订入口，三件事做好，端午满房无忧。";
        }
        return "感谢您的提问。当前AI服务暂未配置API Key，请前往管理后台的AI配置页面设置。";
    }
}
