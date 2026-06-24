package com.sushijia.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sushijia.repository.entity.HotelStaff;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface HotelStaffMapper extends BaseMapper<HotelStaff> {

    @Select("SELECT * FROM hotel_staff WHERE phone = #{phone}")
    HotelStaff findByPhone(String phone);
}
