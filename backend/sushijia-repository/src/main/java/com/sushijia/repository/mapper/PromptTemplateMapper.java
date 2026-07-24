package com.sushijia.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sushijia.repository.entity.PromptTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PromptTemplateMapper extends BaseMapper<PromptTemplate> {

    @Select("SELECT * FROM prompt_templates WHERE module_key = #{moduleKey} AND status = 'production' ORDER BY version DESC LIMIT 1")
    PromptTemplate findProductionByModule(String moduleKey);

    @Select("SELECT * FROM prompt_templates WHERE module_key = #{moduleKey} ORDER BY version DESC")
    List<PromptTemplate> findVersionsByModule(String moduleKey);
}
