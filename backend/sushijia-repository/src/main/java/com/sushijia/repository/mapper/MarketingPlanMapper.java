package com.sushijia.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sushijia.repository.entity.MarketingPlan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MarketingPlanMapper extends BaseMapper<MarketingPlan> {

    @Select("SELECT * FROM marketing_plans WHERE tenant_id = #{tenantId} ORDER BY updated_at DESC")
    List<MarketingPlan> findByTenant(Long tenantId);
}
