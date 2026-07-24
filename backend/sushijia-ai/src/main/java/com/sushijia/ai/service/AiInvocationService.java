package com.sushijia.ai.service;

import com.sushijia.ai.client.DifyClient;
import com.sushijia.common.exception.BizException;
import com.sushijia.common.response.ResultCode;
import com.sushijia.repository.entity.AiAgentConfig;
import com.sushijia.repository.mapper.AiAgentConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiInvocationService {

    private final AiAgentConfigMapper agentConfigMapper;
    private final DifyClient difyClient;

    public String invoke(Long tenantId,
                         String moduleKey,
                         String systemPrompt,
                         String userPrompt,
                         Map<String, Object> context) {
        return dispatch(tenantId, moduleKey, systemPrompt, userPrompt, context, null);
    }

    public String stream(Long tenantId,
                         String moduleKey,
                         String systemPrompt,
                         String userPrompt,
                         Map<String, Object> context,
                         Consumer<String> onChunk) {
        return dispatch(tenantId, moduleKey, systemPrompt, userPrompt, context, onChunk);
    }

    private String dispatch(Long tenantId,
                            String moduleKey,
                            String systemPrompt,
                            String userPrompt,
                            Map<String, Object> context,
                            Consumer<String> onChunk) {
        AiAgentConfig config = agentConfigMapper.findEffectiveConfig(tenantId, moduleKey);
        if (config == null) {
            log.warn("AI agent config missing. tenantId={}, module={}", tenantId, moduleKey);
            throw new BizException(ResultCode.AI_GENERATE_FAILED, "AI \u670d\u52a1\u672a\u914d\u7f6e");
        }

        String provider = config.getProviderKey() == null ? "dify" : config.getProviderKey();
        Map<String, Object> invokeContext = context == null ? new LinkedHashMap<>() : new LinkedHashMap<>(context);
        if ("dify".equalsIgnoreCase(provider)) {
            if (onChunk == null) {
                return difyClient.runAgent(moduleKey, systemPrompt, userPrompt, invokeContext);
            }
            return difyClient.streamAgent(moduleKey, systemPrompt, userPrompt, invokeContext, onChunk);
        }

        log.warn("AI provider is not implemented. provider={}, module={}", provider, moduleKey);
        throw new BizException(ResultCode.AI_GENERATE_FAILED, "AI \u670d\u52a1\u672a\u914d\u7f6e");
    }
}
