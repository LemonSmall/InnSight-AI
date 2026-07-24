package com.sushijia.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sushijia.repository.entity.UserRecentPreset;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserRecentPresetMapper extends BaseMapper<UserRecentPreset> {
    @Select("""
        SELECT * FROM user_recent_presets
        WHERE tenant_id = #{tenantId}
          AND user_id = #{userId}
          AND module_key = #{moduleKey}
        ORDER BY last_used_at DESC
        LIMIT 1
        """)
    UserRecentPreset findLatest(@Param("tenantId") Long tenantId,
                                @Param("userId") Long userId,
                                @Param("moduleKey") String moduleKey);
}
