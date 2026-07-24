package com.sushijia.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sushijia.common.exception.BizException;
import com.sushijia.common.response.ResultCode;
import com.sushijia.repository.mapper.SystemSettingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelScopeImageClient {

    private static final String DEFAULT_MODEL = "Tongyi-MAI/Z-Image-Turbo";
    private static final String CREATE_URL = "https://api-inference.modelscope.cn/v1/images/generations";
    private static final String TASK_URL = "https://api-inference.modelscope.cn/v1/tasks/";

    private final SystemSettingMapper settingMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${sushijia.ai-image.storage-path:./data/ai-images}")
    private String imageStoragePath;

    public boolean isConfigured() {
        return !isBlank(apiKey());
    }

    public String generateImage(Map<String, Object> params, Map<String, Object> context) {
        String token = apiKey();
        if (isBlank(token)) {
            throw new BizException(ResultCode.AI_GENERATE_FAILED, "图片生成服务未配置");
        }

        String size = firstNonBlank(string(params, "imageSize"), string(params, "size"));
        String prompt = buildPrompt(params, context, size);
        String taskId = createTask(token, prompt, size);
        return pollImage(token, taskId);
    }

    private String createTask(String token, String prompt, String size) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", setting("modelscope_image_model", DEFAULT_MODEL));
            body.put("prompt", prompt);
            ImageSize imageSize = resolveImageSize(size);
            body.put("width", imageSize.width);
            body.put("height", imageSize.height);
            body.put("size", imageSize.width + "x" + imageSize.height);
            body.put("aspect_ratio", normalizedAspectRatio(size));
            body.put("negative_prompt", negativePrompt(size));

            ObjectNode parameters = objectMapper.createObjectNode();
            parameters.put("width", imageSize.width);
            parameters.put("height", imageSize.height);
            parameters.put("size", imageSize.width + "x" + imageSize.height);
            parameters.put("aspect_ratio", normalizedAspectRatio(size));
            body.set("parameters", parameters);

            String response = client().post()
                .uri(CREATE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)
                .header("X-ModelScope-Async-Mode", "true")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .block();

            JsonNode root = objectMapper.readTree(response);
            String taskId = firstText(root.path("task_id"), root.path("data").path("task_id"));
            if (isBlank(taskId)) {
                log.error("ModelScope create image task returned no task_id. response={}", response);
                throw new BizException(ResultCode.AI_GENERATE_FAILED, "图片生成任务创建失败");
            }
            return taskId;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("ModelScope create image task failed.", e);
            throw new BizException(ResultCode.AI_GENERATE_FAILED, "图片生成任务创建失败");
        }
    }

    private String pollImage(String token, String taskId) {
        int attempts = intSetting("modelscope_image_poll_attempts", 30);
        int intervalSeconds = intSetting("modelscope_image_poll_interval_seconds", 4);

        for (int i = 0; i < attempts; i++) {
            sleep(intervalSeconds);
            try {
                String response = client().get()
                    .uri(TASK_URL + taskId)
                    .header("Authorization", "Bearer " + token)
                    .header("X-ModelScope-Task-Type", "image_generation")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(20))
                    .block();

                JsonNode root = objectMapper.readTree(response);
                String status = firstText(root.path("task_status"), root.path("data").path("task_status"));
                String image = firstText(
                    root.path("output_images").path(0),
                    root.path("data").path("output_images").path(0),
                    root.path("output").path("images").path(0),
                    root.path("data").path("output").path("images").path(0)
                );
                if (!isBlank(image)) {
                    return cacheImageIfPossible(token, image, taskId);
                }
                if ("FAILED".equalsIgnoreCase(status) || "FAIL".equalsIgnoreCase(status)) {
                    log.error("ModelScope image task failed. taskId={}, response={}", taskId, response);
                    throw new BizException(ResultCode.AI_GENERATE_FAILED, "图片生成失败");
                }
            } catch (BizException e) {
                throw e;
            } catch (Exception e) {
                log.warn("ModelScope image poll failed once. taskId={}, attempt={}/{}", taskId, i + 1, attempts, e);
            }
        }
        throw new BizException(ResultCode.AI_GENERATE_FAILED, "图片生成超时");
    }

    private String cacheImageIfPossible(String token, String imageUrl, String taskId) {
        try {
            byte[] bytes = client().get()
                .uri(imageUrl)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(byte[].class)
                .timeout(Duration.ofSeconds(30))
                .block();
            if (bytes == null || bytes.length == 0) {
                return imageUrl;
            }

            Path root = Paths.get(imageStoragePath).toAbsolutePath().normalize();
            Files.createDirectories(root);
            String fileName = "modelscope-" + safeFilePart(taskId) + "-" + UUID.randomUUID() + imageExtension(imageUrl);
            Path target = root.resolve(fileName).normalize();
            if (!target.startsWith(root)) {
                return imageUrl;
            }
            Files.write(target, bytes);
            return "/api/public/ai-images/" + fileName;
        } catch (Exception e) {
            log.warn("ModelScope image cache failed, using remote url. taskId={}, url={}", taskId, imageUrl, e);
            return imageUrl;
        }
    }

    private String imageExtension(String imageUrl) {
        try {
            String path = URI.create(imageUrl).getPath();
            int dot = path == null ? -1 : path.lastIndexOf('.');
            if (dot >= 0) {
                String ext = path.substring(dot).toLowerCase();
                if (ext.matches("\\.(png|jpe?g|webp|gif)")) {
                    return ext;
                }
            }
        } catch (Exception ignored) {
            // Use a safe default below.
        }
        return ".png";
    }

    private String safeFilePart(String value) {
        return String.valueOf(value == null ? "" : value).replaceAll("[^a-zA-Z0-9_-]", "");
    }

    private String buildPrompt(Map<String, Object> params, Map<String, Object> context, String size) {
        String explicit = firstNonBlank(
            string(params, "imagePromptOverride"),
            string(params, "imagePrompt"),
            string(params, "prompt")
        );
        if (!isBlank(explicit)) {
            return explicit + sizeInstruction(size);
        }

        String hotelName = firstNonBlank(string(params, "hotelName"), string(context, "hotelName"));
        String theme = firstNonBlank(string(params, "theme"), string(params, "poster_theme"));
        String content = string(params, "content");
        String style = firstNonBlank(string(params, "visual_style"), string(params, "style"));
        String scene = firstNonBlank(string(params, "posterScene"), string(params, "scene"));
        String platform = string(params, "platform");
        String audience = firstNonBlank(string(params, "targetAudience"), string(params, "audience"));
        String textDensity = string(params, "textDensity");
        String cta = string(params, "cta");
        String sellingPoint = string(params, "sellingPoint");
        StringBuilder prompt = new StringBuilder();
        prompt.append("酒店/民宿营销海报，画面高级、干净、适合中国酒店民宿线上推广。");
        if (!isBlank(hotelName)) {
            prompt.append("酒店名称：").append(hotelName).append("。");
        }
        if (!isBlank(theme)) {
            prompt.append("主题：").append(theme).append("。");
        }
        if (!isBlank(content)) {
            prompt.append("画面文案/氛围：").append(content).append("。");
        }
        if (!isBlank(style)) {
            prompt.append("视觉风格：").append(style).append("。");
        }
        if (!isBlank(scene)) {
            prompt.append("用途场景：").append(scene).append("。");
        }
        if (!isBlank(platform)) {
            prompt.append("投放平台：").append(platform).append("。");
        }
        if (!isBlank(audience)) {
            prompt.append("目标客群：").append(audience).append("。");
        }
        if (!isBlank(sellingPoint)) {
            prompt.append("核心卖点：").append(sellingPoint).append("。");
        }
        if (!isBlank(cta)) {
            prompt.append("行动引导文案：").append(cta).append("。");
        }
        if (!isBlank(textDensity)) {
            prompt.append("文字密度：").append(textDensityLabel(textDensity)).append("。");
        }
        if (!isBlank(size)) {
            prompt.append(sizeInstruction(size));
        }
        prompt.append("不要生成二维码，不要生成虚假价格，不要出现乱码文字，不要生成错误店名。");
        return prompt.toString();
    }

    private WebClient client() {
        return WebClient.builder()
            .codecs(c -> c.defaultCodecs().maxInMemorySize(8 * 1024 * 1024))
            .build();
    }

    private String apiKey() {
        return firstNonBlank(setting("modelscope_api_key", ""), System.getenv("MODELSCOPE_API_KEY"));
    }

    private String setting(String key, String defaultValue) {
        String value = settingMapper.findValueByKey(key);
        return isBlank(value) ? defaultValue : value.trim();
    }

    private int intSetting(String key, int defaultValue) {
        try {
            return Integer.parseInt(setting(key, String.valueOf(defaultValue)));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private void sleep(int seconds) {
        try {
            Thread.sleep(Math.max(1, seconds) * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ResultCode.AI_GENERATE_FAILED, "图片生成被中断");
        }
    }

    private String firstText(JsonNode... nodes) {
        for (JsonNode node : nodes) {
            if (node != null && !node.isMissingNode() && !node.isNull()) {
                String value = node.isTextual() ? node.asText() : node.toString();
                if (!isBlank(value)) return value;
            }
        }
        return "";
    }

    private String string(Map<String, Object> map, String key) {
        if (map == null || map.get(key) == null) return "";
        return String.valueOf(map.get(key));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) return value.trim();
        }
        return "";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private ImageSize resolveImageSize(String size) {
        String normalized = isBlank(size) ? "3:4" : size.trim().toLowerCase();
        if ("1:1".equals(normalized) || "square".equals(normalized)) {
            return new ImageSize(1024, 1024);
        }
        if ("16:9".equals(normalized) || "landscape".equals(normalized)) {
            return new ImageSize(1344, 768);
        }
        if ("9:16".equals(normalized)) {
            return new ImageSize(768, 1344);
        }
        if ("4:3".equals(normalized)) {
            return new ImageSize(1152, 864);
        }
        return new ImageSize(768, 1024);
    }

    private String normalizedAspectRatio(String size) {
        String normalized = isBlank(size) ? "3:4" : size.trim().toLowerCase();
        if ("1:1".equals(normalized) || "square".equals(normalized)) return "1:1";
        if ("16:9".equals(normalized) || "landscape".equals(normalized)) return "16:9";
        if ("9:16".equals(normalized)) return "9:16";
        if ("4:3".equals(normalized)) return "4:3";
        return "3:4";
    }

    private String sizeInstruction(String size) {
        String ratio = normalizedAspectRatio(size);
        if ("16:9".equals(ratio)) {
            return "必须生成 16:9 横版宽幅图片，构图必须横向展开，禁止竖版手机海报、禁止 3:4 或 4:3 竖向版式，主体完整居中，左右留有延展空间。";
        }
        if ("1:1".equals(ratio)) {
            return "必须生成 1:1 方形图片，适合社交媒体封面，主体居中，四边留白均衡。";
        }
        if ("4:3".equals(ratio)) {
            return "必须生成 4:3 横向图片，适合展示页或门店物料，主体完整，不要竖版构图。";
        }
        if ("9:16".equals(ratio)) {
            return "必须生成 9:16 竖版图片，适合手机屏幕和短视频封面，主体完整，不要横版构图。";
        }
        return "必须生成 3:4 竖版图片，适合海报和小红书封面，主体完整，不要横版构图。";
    }

    private String negativePrompt(String size) {
        String ratio = normalizedAspectRatio(size);
        if ("16:9".equals(ratio) || "4:3".equals(ratio)) {
            return "竖版海报, 手机长图, 3:4, 9:16, portrait, vertical poster, cropped text, distorted text, QR code";
        }
        if ("1:1".equals(ratio)) {
            return "竖版长图, 横向宽幅, portrait poster, landscape banner, cropped text, distorted text, QR code";
        }
        return "横版宽幅, 16:9, landscape banner, cropped text, distorted text, QR code";
    }

    private String textDensityLabel(String value) {
        if ("minimal".equalsIgnoreCase(value)) return "少文字，只保留主标题和短卖点";
        if ("full".equalsIgnoreCase(value)) return "信息完整，包含主标题、副标题、核心卖点和行动引导";
        return "均衡，包含主标题和 1-2 个关键卖点";
    }

    private static class ImageSize {
        private final int width;
        private final int height;

        private ImageSize(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }
}
