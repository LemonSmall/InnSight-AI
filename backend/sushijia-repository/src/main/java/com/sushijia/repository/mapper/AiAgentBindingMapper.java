package com.sushijia.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sushijia.repository.entity.AiAgentBinding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AiAgentBindingMapper extends BaseMapper<AiAgentBinding> {

    @Select("SELECT * FROM ai_agent_bindings WHERE module_key = #{moduleKey} AND enabled = 1 ORDER BY updated_at DESC, id DESC LIMIT 1")
    AiAgentBinding findEnabledByModuleKey(String moduleKey);
}
