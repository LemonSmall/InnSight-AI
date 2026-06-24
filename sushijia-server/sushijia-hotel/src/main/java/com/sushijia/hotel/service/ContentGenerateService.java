package com.sushijia.hotel.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sushijia.ai.client.AiClient;
import com.sushijia.ai.engine.PromptEngine;
import com.sushijia.common.exception.BizException;
import com.sushijia.common.response.ResultCode;
import com.sushijia.repository.entity.*;
import com.sushijia.repository.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * AI 内容生成服务
 * 流程：加载 Prompt 模板 → 渲染变量 → 调用 AI API → 保存结果 → 完成
 * 本地回退：AI API Key 未配置或调用失败时，自动使用本地模板兜底
 */
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
    private final SystemSettingMapper settingMapper;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 提交异步生成任务 */
    @Transactional
    public Long submitTask(Long tenantId, String moduleKey, Map<String, Object> params) {
        Tenant tenant = tenantMapper.selectById(tenantId);
        ContentTask task = new ContentTask();
        task.setTenantId(tenantId);
        task.setModuleKey(moduleKey);
        try {
            task.setInputParams(objectMapper.writeValueAsString(params));
        } catch (JsonProcessingException e) {
            throw new BizException(ResultCode.BAD_REQUEST, "参数序列化失败");
        }
        task.setStatus("pending");
        task.setCreatedAt(LocalDateTime.now());
        taskMapper.insert(task);
        executeTask(task.getId(), tenantId, moduleKey, params, tenant);
        return task.getId();
    }

    @Async
    public void executeTask(Long taskId, Long tenantId, String moduleKey,
                            Map<String, Object> params, Tenant tenant) {
        try {
            ContentTask task = taskMapper.selectById(taskId);
            task.setStatus("processing");
            taskMapper.updateById(task);

            // 模拟异步延迟后，调用 AI
            Thread.sleep(500);

            String content = generateWithAi(moduleKey, params, tenant);

            // 保存结果
            ContentResult result = new ContentResult();
            result.setTaskId(taskId);
            result.setContent(content);
            result.setCreatedAt(LocalDateTime.now());
            resultMapper.insert(result);

            task.setStatus("done");
            task.setResultId(result.getId());
            task.setCompletedAt(LocalDateTime.now());
            taskMapper.updateById(task);

            log.info("AI生成完成: tenant={}, module={}, taskId={}", tenantId, moduleKey, taskId);
        } catch (Exception e) {
            handleTaskError(taskId, e);
        }
    }

    /** 查询任务结果 */
    public Map<String, Object> getTaskResult(Long tenantId, Long taskId) {
        ContentTask task = taskMapper.selectById(taskId);
        if (task == null || !task.getTenantId().equals(tenantId)) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", task.getId());
        result.put("status", task.getStatus());
        if ("done".equals(task.getStatus()) && task.getResultId() != null) {
            ContentResult cr = resultMapper.selectById(task.getResultId());
            if (cr != null) {
                result.put("content", cr.getContent());
            }
        }
        if ("failed".equals(task.getStatus()) && task.getErrorMsg() != null) {
            result.put("errorMsg", task.getErrorMsg());
        }
        return result;
    }

    /** 智慧大脑对话 */
    public Map<String, Object> brainChat(Long tenantId, String message) {
        Tenant tenant = tenantMapper.selectById(tenantId);
        List<RoomType> rooms = roomTypeMapper.selectList(
            new LambdaQueryWrapper<RoomType>().eq(RoomType::getTenantId, tenantId));

        String systemPrompt = buildBrainSystemPrompt(tenant, rooms);
        String reply = aiClient.chat(systemPrompt, message);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", reply);
        result.put("suggestions", List.of(
            "帮我写今天的朋友圈文案",
            "端午海报文案怎么写",
            "如何设计端午套餐"
        ));
        return result;
    }

    // ====== AI 生成核心 ======

    /** 加载 Prompt 模板 + 风格 → 渲染 → 调 AI → 返回内容 */
    private String generateWithAi(String moduleKey, Map<String, Object> params, Tenant tenant) {
        // 1. 加载 Prompt 模板
        PromptTemplate template = promptTemplateMapper.selectOne(
            new LambdaQueryWrapper<PromptTemplate>()
                .eq(PromptTemplate::getModuleKey, moduleKey)
                .eq(PromptTemplate::getStatus, "production")
                .orderByDesc(PromptTemplate::getVersion)
                .last("LIMIT 1"));

        // 2. 加载绑定的风格
        String styleSegment = loadStyleForModule(moduleKey, tenant.getId());

        // 3. 构建变量上下文
        Map<String, String> variables = buildVariables(tenant, params);

        // 4. 渲染模板
        String templateContent = (template != null) ? template.getContent() : getDefaultTemplate(moduleKey);
        String renderedTemplate = PromptEngine.render(templateContent, variables);

        // 5. 拼接完整 System Prompt
        String systemPrompt = "你是一个专业的酒店营销内容创作助手。" +
            (styleSegment != null ? "\n写作风格要求：\n" + styleSegment : "") +
            "\n请严格根据以下模板结构和酒店信息生成内容，用中文回复：\n" + renderedTemplate;

        // 6. 构建 User Prompt（把用户额外输入传进去）
        String userPrompt = buildUserPrompt(params);

        // 7. 调用 AI
        String model = (template != null) ? template.getModelName() : null;
        Integer maxTokens = (template != null) ? template.getMaxTokens() : null;
        return aiClient.chat(systemPrompt, userPrompt, model, maxTokens);
    }

    /** 加载模块绑定的风格 prompt 片段 */
    private String loadStyleForModule(String moduleKey, Long tenantId) {
        try {
            List<StyleLibrary> styles = styleMapper.selectList(
                new LambdaQueryWrapper<StyleLibrary>()
                    .eq(StyleLibrary::getEnabled, true)
                    .and(w -> w.eq(StyleLibrary::getScope, "public")
                        .or().eq(StyleLibrary::getTenantId, tenantId))
                    .orderByDesc(StyleLibrary::getUsageCount)
                    .last("LIMIT 3"));
            if (styles.isEmpty()) return null;
            StringBuilder sb = new StringBuilder();
            for (StyleLibrary s : styles) {
                sb.append(s.getPromptSegment()).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("加载风格失败: module={}", moduleKey, e);
            return null;
        }
    }

    /** 构建变量映射 */
    private Map<String, String> buildVariables(Tenant tenant, Map<String, Object> params) {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("hotel_name", tenant.getName());
        vars.put("hotel_type", tenant.getType() != null ? tenant.getType() : "精品民宿");
        vars.put("city", tenant.getCity() != null ? tenant.getCity() : "");
        vars.put("tags", tenant.getTags() != null ? tenant.getTags() : "竹林、温泉");
        vars.put("target_audience", tenant.getTargetAudience() != null ? tenant.getTargetAudience() : "城市中产");
        vars.put("nearby", tenant.getNearby() != null ? tenant.getNearby() : "");
        // 注入用户参数
        if (params != null) {
            for (Map.Entry<String, Object> e : params.entrySet()) {
                if (e.getValue() != null) {
                    vars.put(e.getKey(), e.getValue().toString());
                }
            }
        }
        return vars;
    }

    /** 构建用户提示词 */
    private String buildUserPrompt(Map<String, Object> params) {
        if (params == null || params.isEmpty()) return "请生成内容";
        StringBuilder sb = new StringBuilder("请根据以下要求生成内容：\n");
        for (Map.Entry<String, Object> e : params.entrySet()) {
            if (e.getValue() != null && !"module".equals(e.getKey())) {
                sb.append("- ").append(e.getKey()).append("：").append(e.getValue()).append("\n");
            }
        }
        return sb.toString();
    }

    /** 智能大脑系统提示词 */
    private String buildBrainSystemPrompt(Tenant tenant, List<RoomType> rooms) {
        int totalRooms = rooms.stream().mapToInt(RoomType::getCount).sum();
        return String.format(
            "你是「%s」酒店的 AI 运营顾问。\n" +
            "- 类型：%s\n- 城市：%s\n- 特色标签：%s\n- 总房量：%d间\n" +
            "你的职责是：分析酒店运营数据，给出可执行的定价建议、内容营销方案、客服话术优化。\n" +
            "回答要简洁、专业、可落地，用中文回复。",
            tenant.getName(), tenant.getType(), tenant.getCity(), tenant.getTags(), totalRooms);
    }

    /** 默认模板（如果数据库没有该模块的模板） */
    private String getDefaultTemplate(String moduleKey) {
        return switch (moduleKey) {
            case "wechat" -> "为「{{hotel_name}}」酒店写早中晚三条朋友圈文案，风格亲切自然。酒店特色：{{tags}}。所在城市：{{city}}。";
            case "xhs" -> "为「{{hotel_name}}」创作一篇小红书种草图文，包含吸睛标题、正文、话题标签。酒店特色：{{tags}}。目标客群：{{target_audience}}。";
            case "video" -> "为「{{hotel_name}}」写一段30秒抖音短视频口播脚本，包含黄金前3秒钩子、中段卖点、结尾行动号召。";
            case "poster" -> "为「{{hotel_name}}」设计一张营销海报方案，包含主标题、副标题、行动号召、视觉建议。";
            case "article" -> "为「{{hotel_name}}」写一篇公众号推文，以「{{city}}的{{tags}}」为关键词。";
            default -> "为「{{hotel_name}}」酒店生成相关内容。酒店特色：{{tags}}。";
        };
    }

    private void handleTaskError(Long taskId, Exception e) {
        ContentTask task = taskMapper.selectById(taskId);
        task.setStatus("failed");
        task.setErrorMsg(e.getMessage());
        task.setCompletedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        log.error("AI生成失败: taskId={}", taskId, e);
    }
}
