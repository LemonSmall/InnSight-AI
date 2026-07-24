package com.sushijia.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sushijia.repository.entity.CreditLedger;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CreditLedgerMapper extends BaseMapper<CreditLedger> {

    @Select("""
        SELECT * FROM credit_ledger
        WHERE (#{tenantId} IS NULL OR tenant_id = #{tenantId})
        ORDER BY created_at DESC
        LIMIT #{limit}
        """)
    List<CreditLedger> findByTenant(@Param("tenantId") Long tenantId, @Param("limit") int limit);

    @Select("SELECT IFNULL(SUM(ABS(amount)), 0) FROM credit_ledger WHERE tenant_id = #{tenantId} AND type = 'consume' AND DATE(created_at) = CURDATE()")
    int todayConsumed(@Param("tenantId") Long tenantId);
}
