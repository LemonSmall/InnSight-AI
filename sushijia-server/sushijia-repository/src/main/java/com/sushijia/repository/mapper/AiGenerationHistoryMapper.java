package com.sushijia.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sushijia.repository.entity.AiGenerationHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AiGenerationHistoryMapper extends BaseMapper<AiGenerationHistory> {
    @Select("""
        SELECT
          id, tenant_id, user_id, module_key, title, prompt,
          CASE
            WHEN input_params IS NULL THEN NULL
            WHEN CHAR_LENGTH(input_params) > 12000 THEN CONCAT(LEFT(input_params, 12000), '...')
            ELSE input_params
          END AS input_params,
          CASE
            WHEN output_content IS NULL THEN NULL
            WHEN CHAR_LENGTH(output_content) > 12000 THEN CONCAT(LEFT(output_content, 12000), '...')
            ELSE output_content
          END AS output_content,
          output_assets, provider_key, agent_config_id, agent_binding_id, agent_name,
          request_id, knowledge_refs, duration_ms, cost_credits, status, error_msg,
          created_at, completed_at
        FROM ai_generation_history
        WHERE tenant_id = #{tenantId}
          AND user_id = #{userId}
          AND (#{moduleKey} IS NULL OR module_key = #{moduleKey})
        ORDER BY created_at DESC
        LIMIT #{limit}
        """)
    List<AiGenerationHistory> findRecent(@Param("tenantId") Long tenantId,
                                         @Param("userId") Long userId,
                                         @Param("moduleKey") String moduleKey,
                                         @Param("limit") int limit);
}
