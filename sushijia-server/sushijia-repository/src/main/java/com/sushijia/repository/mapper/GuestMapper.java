package com.sushijia.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sushijia.repository.entity.Guest;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GuestMapper extends BaseMapper<Guest> {
}
