package com.sushijia.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sushijia.repository.entity.Tenant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TenantMapper extends BaseMapper<Tenant> {

    /** 行级锁查询 - 用于算力扣减防并发 */
    @Select("SELECT * FROM tenants WHERE id = #{tenantId} FOR UPDATE")
    Tenant selectByIdForUpdate(Long tenantId);
}
