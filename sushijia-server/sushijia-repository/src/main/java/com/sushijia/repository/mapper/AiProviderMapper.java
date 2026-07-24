package com.sushijia.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sushijia.repository.entity.AiProvider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AiProviderMapper extends BaseMapper<AiProvider> {
    @Select("SELECT * FROM ai_providers WHERE provider_key = #{providerKey} AND enabled = 1 LIMIT 1")
    AiProvider findEnabledByKey(String providerKey);
}
