package com.sushijia.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sushijia.repository.entity.TenantSubscription;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TenantSubscriptionMapper extends BaseMapper<TenantSubscription> {

    @Select("SELECT * FROM tenant_subscriptions WHERE tenant_id = #{tenantId} ORDER BY created_at DESC LIMIT 1")
    TenantSubscription findCurrentByTenantId(Long tenantId);
}
