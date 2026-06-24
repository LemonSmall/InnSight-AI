package com.sushijia.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sushijia.repository.entity.SystemSetting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SystemSettingMapper extends BaseMapper<SystemSetting> {

    @Select("SELECT setting_value FROM system_settings WHERE setting_key = #{key}")
    String findValueByKey(String key);
}
