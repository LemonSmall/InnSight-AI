package com.sushijia.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sushijia.repository.entity.FutureRoomStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FutureRoomStatusMapper extends BaseMapper<FutureRoomStatus> {

    @Select("SELECT * FROM future_room_status WHERE tenant_id = #{tenantId} ORDER BY date")
    List<FutureRoomStatus> findByTenant(Long tenantId);
}
