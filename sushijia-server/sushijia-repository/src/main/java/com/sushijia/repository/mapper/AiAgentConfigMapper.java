package com.sushijia.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sushijia.repository.entity.AiAgentConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AiAgentConfigMapper extends BaseMapper<AiAgentConfig> {
    @Select("""
        SELECT * FROM ai_agent_configs
        WHERE module_key = #{moduleKey}
          AND enabled = 1
          AND (tenant_id = #{tenantId} OR tenant_id = 0)
        ORDER BY CASE WHEN tenant_id = #{tenantId} THEN 0 ELSE 1 END, id DESC
        LIMIT 1
        """)
    AiAgentConfig findEffectiveConfig(@Param("tenantId") Long tenantId, @Param("moduleKey") String moduleKey);
}
