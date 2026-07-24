package com.sushijia.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.sushijia.repository.entity.HotelStaff;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface HotelStaffMapper extends BaseMapper<HotelStaff> {

    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM hotel_staff WHERE phone = #{phone}")
    HotelStaff findByPhone(String phone);

    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM hotel_staff WHERE tenant_id = #{tenantId} ORDER BY id DESC")
    List<HotelStaff> findByTenantId(@Param("tenantId") Long tenantId);

    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM hotel_staff WHERE id = #{id} LIMIT 1")
    HotelStaff findByIdIgnoreTenant(@Param("id") Long id);
}
