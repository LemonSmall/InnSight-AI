package com.sushijia.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sushijia.repository.entity.TenantOperationLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TenantOperationLogMapper extends BaseMapper<TenantOperationLog> {
}
