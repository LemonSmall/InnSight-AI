package com.sushijia.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sushijia.repository.entity.HotelKnowledgeItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface HotelKnowledgeItemMapper extends BaseMapper<HotelKnowledgeItem> {
    @Select("""
        SELECT * FROM hotel_knowledge_items
        WHERE tenant_id = #{tenantId}
          AND status = 'active'
          AND (effective_from IS NULL OR effective_from <= NOW())
          AND (effective_to IS NULL OR effective_to >= NOW())
        ORDER BY updated_at DESC
        LIMIT #{limit}
        """)
    List<HotelKnowledgeItem> findActiveKnowledge(@Param("tenantId") Long tenantId, @Param("limit") int limit);
}
