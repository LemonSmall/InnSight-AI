package com.sushijia.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sushijia.repository.entity.BillingRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface BillingRuleMapper extends BaseMapper<BillingRule> {

    @Select("SELECT * FROM billing_rules WHERE module_key = #{moduleKey} AND enabled = 1")
    BillingRule findByModuleKey(String moduleKey);
}
