package com.sushijia.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sushijia.repository.entity.Admin;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdminMapper extends BaseMapper<Admin> {
    @Select("SELECT * FROM admins WHERE email = #{email}")
    Admin findByEmail(String email);
}
