package com.sushijia.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sushijia.common.response.R;
import com.sushijia.repository.entity.AiAgentBinding;
import com.sushijia.repository.mapper.AiAgentBindingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/ai-agent-bindings")
@RequiredArgsConstructor
public class AiAgentBindingController {

    private static final List<String> MODULE_KEYS = List.of(
        "brain", "strategy", "pricing", "xhs", "wechat", "article",
        "video", "poster", "polish", "review", "reply", "occupancy_image"
    );

    private static final String STRATEGY_INPUT_SCHEMA = "{\"theme\":\"string\",\"target\":\"string\",\"objective\":\"string\",\"objectiveLabel\":\"string\",\"period\":\"string\",\"occasion\":\"string\",\"targetAudience\":\"string\",\"channels\":\"array\",\"budgetLevel\":\"string\",\"executionCapacity\":\"string\",\"outputDepth\":\"string\",\"marketSignals\":\"string\",\"competitorObservations\":\"string\",\"marketContext\":\"string\",\"availableOffers\":\"string\",\"constraints\":\"string\",\"evidenceRequirement\":\"boolean\",\"surroundingContextJson\":\"string\",\"selectedParamsJson\":\"string\"}";
    private static final String PRICING_INPUT_SCHEMA = "{\"dateRange\":\"string\",\"pricingPeriod\":\"string\",\"pricingGoal\":\"string\",\"demandSignal\":\"string\",\"bookingWindow\":\"string\",\"eventFactor\":\"string\",\"competitorPriceRange\":\"string\",\"currentPriceNotes\":\"string\",\"priceFloor\":\"string\",\"maxDiscountPercent\":\"number\",\"targetChannels\":\"array\",\"promotionAllowed\":\"boolean\",\"packagePreference\":\"string\",\"riskLevel\":\"string\",\"constraints\":\"string\",\"roomSnapshot\":\"array\",\"evidenceRequirement\":\"boolean\",\"surroundingContextJson\":\"string\",\"selectedParamsJson\":\"string\"}";
    private static final String SURROUNDING_INPUT_SCHEMA = "{\"taskMode\":\"string\",\"hotelName\":\"string\",\"city\":\"string\",\"district\":\"string\",\"address\":\"string\",\"longitude\":\"string\",\"latitude\":\"string\",\"checkIn\":\"string\",\"checkOut\":\"string\",\"query\":\"string\",\"surroundingContextJson\":\"string\",\"selectedParamsJson\":\"string\"}";

    private final AiAgentBindingMapper bindingMapper;
    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;

    @GetMapping
    public R<List<BindingView>> list() {
        ensureTables();
        synchronizeBindingSchemas();
        List<AiAgentBinding> bindings = bindingMapper.selectList(
            new LambdaQueryWrapper<AiAgentBinding>()
                .orderByAsc(AiAgentBinding::getModuleKey)
                .orderByDesc(AiAgentBinding::getEnabled)
                .orderByDesc(AiAgentBinding::getUpdatedAt)
                .orderByDesc(AiAgentBinding::getId));
        return R.ok(bindings.stream().map(this::toView).toList());
    }

    private void synchronizeBindingSchemas() {
        List<AiAgentBinding> bindings = bindingMapper.selectList(null);
        for (AiAgentBinding binding : bindings) {
            if (!MODULE_KEYS.contains(binding.getModuleKey())) {
                continue;
            }
            String expected = inputSchema(binding.getModuleKey());
            if (!expected.equals(trim(binding.getInputSchema()))) {
                binding.setInputSchema(expected);
                binding.setUpdatedAt(LocalDateTime.now());
                bindingMapper.updateById(binding);
            }
        }
        for (String moduleKey : MODULE_KEYS) {
            syncModuleConfig(moduleKey);
        }
    }

    @PostMapping
    @Transactional
    public R<BindingView> create(@RequestBody AiAgentBinding body) {
        ensureTables();
        if (isBlank(body.getModuleKey())) {
            return R.fail(400, "请选择功能模块");
        }
        if (isBlank(body.getAppName())) {
            return R.fail(400, "请填写独立智能体名称");
        }
        if (existsSameName(body.getModuleKey(), body.getAppName(), null)) {
            return R.fail(400, "同一功能下智能体名称不能重复");
        }
        if (body.getEnabled() != null && body.getEnabled() == 1 && isBlank(firstNotBlank(body.getApiKey(), body.getBotApiKey()))) {
            return R.fail(400, "启用智能体前请填写独立的 Dify App API Key");
        }
        if (body.getEnabled() != null && body.getEnabled() == 1
            && isAppUsedByOtherModule(firstNotBlank(body.getApiKey(), body.getBotApiKey()), body.getModuleKey(), null)) {
            return R.fail(400, "该 Dify 应用已绑定其他功能，请为本功能创建独立应用");
        }

        AiAgentBinding binding = new AiAgentBinding();
        binding.setModuleKey(trim(body.getModuleKey()));
        applyBody(binding, body);
        binding.setCreatedAt(LocalDateTime.now());
        binding.setUpdatedAt(LocalDateTime.now());

        if (isEnabled(binding)) {
            disableOtherBindings(binding.getModuleKey(), null);
        }
        bindingMapper.insert(binding);
        syncModuleConfig(binding.getModuleKey());
        clearBindingCache(binding.getModuleKey());
        return R.ok(toView(binding));
    }

    @PutMapping("/{id}")
    @Transactional
    public R<String> update(@PathVariable("id") Long id, @RequestBody AiAgentBinding body) {
        ensureTables();
        AiAgentBinding binding = bindingMapper.selectById(id);
        if (binding == null) {
            return R.fail(404, "智能体绑定不存在");
        }
        if (isBlank(body.getAppName())) {
            return R.fail(400, "请填写独立智能体名称");
        }
        if (existsSameName(body.getModuleKey(), body.getAppName(), id)) {
            return R.fail(400, "同一功能下智能体名称不能重复");
        }
        String effectiveApiKey = submittedApiKey(body, binding.getApiKey());
        if (body.getEnabled() != null && body.getEnabled() == 1 && isBlank(effectiveApiKey)) {
            return R.fail(400, "启用智能体前请填写独立的 Dify App API Key");
        }
        if (body.getEnabled() != null && body.getEnabled() == 1
            && isAppUsedByOtherModule(effectiveApiKey, body.getModuleKey(), id)) {
            return R.fail(400, "该 Dify 应用已绑定其他功能，请为本功能创建独立应用");
        }

        String oldModuleKey = binding.getModuleKey();
        applyBody(binding, body);
        binding.setUpdatedAt(LocalDateTime.now());

        if (isEnabled(binding)) {
            disableOtherBindings(binding.getModuleKey(), binding.getId());
        }
        bindingMapper.updateById(binding);
        syncModuleConfig(binding.getModuleKey());
        if (!oldModuleKey.equals(binding.getModuleKey())) {
            syncModuleConfig(oldModuleKey);
            clearBindingCache(oldModuleKey);
        }
        clearBindingCache(binding.getModuleKey());
        return R.ok("Saved");
    }

    @DeleteMapping("/{id}")
    @Transactional
    public R<String> delete(@PathVariable("id") Long id) {
        ensureTables();
        AiAgentBinding binding = bindingMapper.selectById(id);
        if (binding == null) {
            return R.fail(404, "智能体绑定不存在");
        }
        if (isEnabled(binding)) {
            return R.fail(400, "正在启用的智能体不能删除，请先停用或切换绑定");
        }
        String moduleKey = binding.getModuleKey();
        bindingMapper.deleteById(id);
        syncModuleConfig(moduleKey);
        clearBindingCache(moduleKey);
        return R.ok("Deleted");
    }

    private void clearBindingCache(String moduleKey) {
        if (isBlank(moduleKey)) {
            return;
        }
        try {
            redisTemplate.delete("sushijia:ai:binding:" + moduleKey);
        } catch (Exception ignored) {
            // Redis is an acceleration layer only.
        }
    }

    private void applyBody(AiAgentBinding binding, AiAgentBinding body) {
        binding.setModuleKey(trim(body.getModuleKey()));
        binding.setProvider(firstNotBlank(body.getProvider(), "dify"));
        binding.setAppType(normalizeAppType(firstNotBlank(body.getAppType(), body.getBotId())));
        binding.setAppId(trim(body.getAppId()));
        binding.setApiKey(submittedApiKey(body, binding.getApiKey()));
        binding.setAppName(trim(body.getAppName()));
        binding.setEndpoint(trim(body.getEndpoint()));
        binding.setInputSchema(normalizeInputSchema(body.getInputSchema(), binding.getModuleKey()));
        binding.setBotId(binding.getAppType());
        binding.setBotApiKey(binding.getApiKey());
        binding.setBotName(binding.getAppName());
        binding.setEnabled(body.getEnabled() == null ? 0 : body.getEnabled());
    }

    private void disableOtherBindings(String moduleKey, Long exceptId) {
        if (isBlank(moduleKey)) {
            return;
        }
        List<AiAgentBinding> rows = bindingMapper.selectList(
            new LambdaQueryWrapper<AiAgentBinding>()
                .eq(AiAgentBinding::getModuleKey, moduleKey)
                .eq(AiAgentBinding::getEnabled, 1));
        for (AiAgentBinding row : rows) {
            if (exceptId != null && exceptId.equals(row.getId())) {
                continue;
            }
            row.setEnabled(0);
            row.setUpdatedAt(LocalDateTime.now());
            bindingMapper.updateById(row);
        }
    }

    private void syncModuleConfig(String moduleKey) {
        if (isBlank(moduleKey)) {
            return;
        }
        AiAgentBinding active = bindingMapper.findEnabledByModuleKey(moduleKey);
        if (active == null) {
            jdbcTemplate.update(
                "UPDATE ai_agent_configs SET enabled = 0, updated_at = NOW() WHERE tenant_id = 0 AND module_key = ?",
                moduleKey
            );
            return;
        }
        syncAgentConfig(active);
    }

    private void syncAgentConfig(AiAgentBinding binding) {
        String moduleKey = trim(binding.getModuleKey());
        jdbcTemplate.update(
            """
            INSERT INTO ai_agent_configs
              (tenant_id, module_key, module_name, capability_key, provider_key, app_id, workflow_id,
               api_key_encrypted, endpoint, call_mode, input_schema, output_schema, output_parser,
               knowledge_policy, enabled, created_at, updated_at)
            VALUES
              (0, ?, ?, ?, ?, ?, '', ?, ?, ?, ?, ?, ?, 'tenant_confirmed', ?, NOW(), NOW())
            ON DUPLICATE KEY UPDATE
              module_name = VALUES(module_name),
              capability_key = VALUES(capability_key),
              provider_key = VALUES(provider_key),
              app_id = VALUES(app_id),
              api_key_encrypted = VALUES(api_key_encrypted),
              endpoint = VALUES(endpoint),
              call_mode = VALUES(call_mode),
              input_schema = VALUES(input_schema),
              output_schema = VALUES(output_schema),
              output_parser = VALUES(output_parser),
              knowledge_policy = VALUES(knowledge_policy),
              enabled = VALUES(enabled),
              updated_at = NOW()
            """,
            moduleKey,
            firstNotBlank(binding.getAppName(), defaultModuleName(moduleKey)),
            capabilityKey(moduleKey),
            firstNotBlank(binding.getProvider(), "dify"),
            trim(binding.getAppId()),
            firstNotBlank(binding.getApiKey(), binding.getBotApiKey()),
            trim(binding.getEndpoint()),
            normalizeAppType(binding.getAppType()),
            normalizeInputSchema(binding.getInputSchema(), moduleKey),
            outputSchema(moduleKey),
            outputParser(moduleKey),
            isEnabled(binding) ? 1 : 0
        );
    }

    private void ensureTables() {
        jdbcTemplate.execute(
            "CREATE TABLE IF NOT EXISTS ai_agent_bindings (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "module_key VARCHAR(64) NOT NULL," +
                "provider VARCHAR(32) NOT NULL DEFAULT 'dify'," +
                "app_type VARCHAR(32) NOT NULL DEFAULT 'chatflow'," +
                "app_id VARCHAR(128) NOT NULL DEFAULT ''," +
                "api_key TEXT," +
                "app_name VARCHAR(128) NOT NULL DEFAULT ''," +
                "endpoint VARCHAR(255) NOT NULL DEFAULT ''," +
                "input_schema JSON," +
                "bot_id VARCHAR(128) NOT NULL DEFAULT ''," +
                "bot_api_key TEXT," +
                "bot_name VARCHAR(128) NOT NULL DEFAULT ''," +
                "enabled TINYINT(1) NOT NULL DEFAULT 0," +
                "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "UNIQUE KEY uk_ai_agent_module_agent (module_key, app_name)," +
                "INDEX idx_ai_agent_module_enabled (module_key, enabled)" +
            ")"
        );
        dropIndexIfExists("ai_agent_bindings", "uk_module_key");
        dropIndexIfExists("ai_agent_bindings", "uk_ai_agent_module_key");
        addColumnIfMissing("ai_agent_bindings", "provider", "VARCHAR(32) NOT NULL DEFAULT 'dify'");
        addColumnIfMissing("ai_agent_bindings", "app_type", "VARCHAR(32) NOT NULL DEFAULT 'chatflow'");
        addColumnIfMissing("ai_agent_bindings", "app_id", "VARCHAR(128) NOT NULL DEFAULT ''");
        addColumnIfMissing("ai_agent_bindings", "api_key", "TEXT");
        addColumnIfMissing("ai_agent_bindings", "app_name", "VARCHAR(128) NOT NULL DEFAULT ''");
        addColumnIfMissing("ai_agent_bindings", "endpoint", "VARCHAR(255) NOT NULL DEFAULT ''");
        addColumnIfMissing("ai_agent_bindings", "input_schema", "JSON");
        addIndexIfMissing("ai_agent_bindings", "uk_ai_agent_module_agent", "UNIQUE KEY uk_ai_agent_module_agent (module_key, app_name)");
        addIndexIfMissing("ai_agent_bindings", "idx_ai_agent_module_enabled", "INDEX idx_ai_agent_module_enabled (module_key, enabled)");

        jdbcTemplate.execute(
            "CREATE TABLE IF NOT EXISTS ai_agent_configs (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "tenant_id BIGINT NOT NULL DEFAULT 0," +
                "module_key VARCHAR(64) NOT NULL," +
                "module_name VARCHAR(100) NOT NULL DEFAULT ''," +
                "capability_key VARCHAR(64) NOT NULL," +
                "provider_key VARCHAR(50) NOT NULL DEFAULT 'dify'," +
                "app_id VARCHAR(128) NOT NULL DEFAULT ''," +
                "workflow_id VARCHAR(128) NOT NULL DEFAULT ''," +
                "api_key_encrypted TEXT," +
                "endpoint VARCHAR(255) NOT NULL DEFAULT ''," +
                "call_mode VARCHAR(50) NOT NULL DEFAULT 'chatflow'," +
                "input_schema JSON," +
                "output_schema JSON," +
                "output_parser VARCHAR(100) NOT NULL DEFAULT 'auto'," +
                "knowledge_policy VARCHAR(50) NOT NULL DEFAULT 'tenant_confirmed'," +
                "enabled TINYINT(1) NOT NULL DEFAULT 1," +
                "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "UNIQUE KEY uk_ai_agent_config_tenant_module (tenant_id, module_key)" +
            ")"
        );
    }

    private void addColumnIfMissing(String tableName, String column, String definition) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
            Integer.class,
            tableName,
            column
        );
        if (count == null || count == 0) {
            jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN " + column + " " + definition);
        }
    }

    private void dropIndexIfExists(String tableName, String indexName) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?",
            Integer.class,
            tableName,
            indexName
        );
        if (count != null && count > 0) {
            jdbcTemplate.execute("ALTER TABLE " + tableName + " DROP INDEX " + indexName);
        }
    }

    private void addIndexIfMissing(String tableName, String indexName, String definition) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?",
            Integer.class,
            tableName,
            indexName
        );
        if (count == null || count == 0) {
            jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD " + definition);
        }
    }

    private boolean existsSameName(String moduleKey, String appName, Long exceptId) {
        LambdaQueryWrapper<AiAgentBinding> query = new LambdaQueryWrapper<AiAgentBinding>()
            .eq(AiAgentBinding::getModuleKey, trim(moduleKey))
            .eq(AiAgentBinding::getAppName, trim(appName));
        if (exceptId != null) {
            query.ne(AiAgentBinding::getId, exceptId);
        }
        return bindingMapper.selectCount(query) > 0;
    }

    private boolean isAppUsedByOtherModule(String apiKey, String moduleKey, Long exceptId) {
        if (isBlank(apiKey)) {
            return false;
        }
        LambdaQueryWrapper<AiAgentBinding> query = new LambdaQueryWrapper<AiAgentBinding>()
            .eq(AiAgentBinding::getEnabled, 1)
            .eq(AiAgentBinding::getApiKey, apiKey.trim())
            .ne(AiAgentBinding::getModuleKey, trim(moduleKey));
        if (exceptId != null) {
            query.ne(AiAgentBinding::getId, exceptId);
        }
        return bindingMapper.selectCount(query) > 0;
    }

    private boolean isEnabled(AiAgentBinding binding) {
        return binding != null && binding.getEnabled() != null && binding.getEnabled() == 1;
    }

    private String normalizeInputSchema(String inputSchema, String moduleKey) {
        if (!isBlank(inputSchema)) {
            return inputSchema.trim();
        }
        return inputSchema(moduleKey);
    }

    private String normalizeAppType(String value) {
        return "workflow".equalsIgnoreCase(trim(value)) ? "workflow" : "chatflow";
    }

    private String defaultModuleName(String moduleKey) {
        return switch (moduleKey) {
            case "brain" -> "AI 店长";
            case "strategy" -> "营销策略";
            case "xhs" -> "小红书图文";
            case "wechat" -> "朋友圈文案";
            case "article" -> "公众号推文";
            case "poster" -> "营销海报";
            case "video" -> "短视频脚本";
            case "pricing" -> "收益定价建议";
            case "surrounding" -> "周边信息智能体";
            case "review" -> "好评生成";
            case "reply" -> "回评话术";
            case "knowledge" -> "知识库提取";
            case "polish" -> "AI 文案润色";
            case "occupancy_image" -> "房态图片识别";
            default -> moduleKey;
        };
    }

    private String capabilityKey(String moduleKey) {
        return switch (moduleKey) {
            case "brain", "strategy", "pricing", "surrounding" -> "operation_advice";
            case "poster" -> "image_generation";
            case "video" -> "video_script";
            case "knowledge" -> "knowledge_extract";
            case "polish" -> "text_polish";
            case "occupancy_image" -> "image_recognition";
            default -> "text_generation";
        };
    }

    private String outputParser(String moduleKey) {
        return switch (moduleKey) {
            case "brain", "reply", "poster", "polish" -> "auto";
            default -> "json";
        };
    }

    private String inputSchema(String moduleKey) {
        String businessFields = switch (moduleKey) {
            case "brain" -> "\"enableWebSearch\":\"boolean\",\"outputStyle\":\"string\"";
            case "strategy" -> stripObject(STRATEGY_INPUT_SCHEMA);
            case "pricing" -> stripObject(PRICING_INPUT_SCHEMA);
            case "surrounding" -> stripObject(SURROUNDING_INPUT_SCHEMA);
            case "xhs" -> "\"topics\":\"string\",\"customTopic\":\"string\",\"tone\":\"string\",\"style\":\"string\",\"note\":\"string\",\"withImage\":\"boolean\",\"imageSize\":\"string\",\"imageCount\":\"number\"";
            case "wechat" -> "\"slots\":\"array\",\"style\":\"string\",\"length\":\"string\",\"note\":\"string\",\"withImage\":\"boolean\",\"imageSize\":\"string\"";
            case "article" -> "\"title\":\"string\",\"style\":\"string\",\"length\":\"string\",\"withImage\":\"boolean\",\"imageCount\":\"number\",\"fileName\":\"string\"";
            case "video" -> "\"sellingPoints\":\"string\",\"view\":\"string\",\"style\":\"string\",\"goal\":\"string\",\"duration\":\"string\",\"count\":\"number\"";
            case "reply" -> "\"reviewText\":\"string\",\"reviewType\":\"string\",\"style\":\"string\",\"additionalNotes\":\"string\"";
            case "review" -> "\"guestType\":\"string\",\"scene\":\"string\",\"additionalNotes\":\"string\"";
            case "knowledge" -> "\"sourceType\":\"string\",\"sourceText\":\"string\",\"fileName\":\"string\",\"extractionMode\":\"string\",\"effectiveHint\":\"string\"";
            case "occupancy_image" -> "\"image\":\"file\",\"sourceFileName\":\"string\",\"sourceFileType\":\"string\",\"sourceFileSize\":\"number\",\"uploadedFileName\":\"string\"";
            case "poster" -> "\"mode\":\"string\",\"theme\":\"string\",\"content\":\"string\",\"style\":\"string\",\"scene\":\"string\",\"platform\":\"string\",\"targetAudience\":\"string\",\"textDensity\":\"string\",\"cta\":\"string\",\"sellingPoint\":\"string\",\"imageSize\":\"string\",\"imageData\":\"string\"";
            case "polish" -> "\"sourceText\":\"string\",\"scene\":\"string\",\"field\":\"string\",\"style\":\"string\",\"purpose\":\"string\",\"immutableFacts\":\"array\"";
            default -> "\"theme\":\"string\"";
        };
        return "{\"commonContextJson\":\"string\",\"businessParamsJson\":\"string\",\"knowledgeContextJson\":\"string\",\"message\":\"string\""
            + (isBlank(businessFields) ? "" : "," + businessFields) + "}";
    }

    private String stripObject(String value) {
        String trimmed = trim(value);
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String outputSchema(String moduleKey) {
        return switch (moduleKey) {
            case "brain" -> "{\"content\":\"string\",\"suggestions\":\"array\"}";
            case "pricing" -> "{\"advice\":\"array\",\"risks\":\"array\"}";
            case "surrounding" -> "{\"hotelProfileSuggestion\":\"object\",\"currentHotelPrices\":\"array\",\"nearbyHotelPrices\":\"array\",\"nearbyHotPlaces\":\"array\",\"localEvents\":\"array\",\"searchEvidence\":\"array\",\"unavailableFields\":\"array\"}";
            case "reply" -> "{\"reply\":\"string\"}";
            case "knowledge" -> "{\"summary\":\"string\",\"items\":\"array\"}";
            case "occupancy_image" -> "{\"records\":\"array\",\"warnings\":\"array\"}";
            case "poster" -> "{\"imageUrl\":\"string\",\"prompt\":\"string\"}";
            case "wechat" -> "{\"morning\":\"string\",\"noon\":\"string\",\"evening\":\"string\"}";
            case "article" -> "{\"title\":\"string\",\"summary\":\"string\",\"sections\":\"array\"}";
            case "video" -> "{\"hook\":\"string\",\"shots\":\"array\",\"voiceover\":\"string\"}";
            case "polish" -> "{\"content\":\"string\"}";
            default -> "{\"titles\":\"array\",\"body\":\"string\",\"tags\":\"array\"}";
        };
    }

    private String firstNotBlank(String first, String second) {
        return !isBlank(first) ? first.trim() : trim(second);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String submittedApiKey(AiAgentBinding body, String currentValue) {
        String submitted = firstNotBlank(body.getApiKey(), body.getBotApiKey());
        return isBlank(submitted) ? trim(currentValue) : submitted;
    }

    private BindingView toView(AiAgentBinding binding) {
        return new BindingView(
            binding.getId(),
            binding.getModuleKey(),
            binding.getProvider(),
            binding.getAppType(),
            binding.getAppId(),
            binding.getAppName(),
            binding.getEndpoint(),
            binding.getInputSchema(),
            binding.getBotId(),
            binding.getBotName(),
            !isBlank(firstNotBlank(binding.getApiKey(), binding.getBotApiKey())),
            binding.getEnabled(),
            binding.getCreatedAt(),
            binding.getUpdatedAt()
        );
    }

    public record BindingView(
        Long id,
        String moduleKey,
        String provider,
        String appType,
        String appId,
        String appName,
        String endpoint,
        String inputSchema,
        String botId,
        String botName,
        boolean apiKeyConfigured,
        Integer enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {}
}
