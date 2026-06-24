package com.sushijia.hotel.controller;

import com.sushijia.common.response.R;
import com.sushijia.framework.tenant.TenantContext;
import com.sushijia.repository.entity.RoomType;
import com.sushijia.repository.entity.Tenant;
import com.sushijia.repository.mapper.RoomTypeMapper;
import com.sushijia.repository.mapper.TenantMapper;
import com.sushijia.repository.mapper.RoomStatusMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/hotel")
@RequiredArgsConstructor
public class HotelConfigController {

    private final TenantMapper tenantMapper;
    private final RoomTypeMapper roomTypeMapper;
    private final RoomStatusMapper roomStatusMapper;

    @GetMapping("/config")
    public R<Tenant> getConfig() {
        Long tenantId = TenantContext.get();
        Tenant tenant = tenantMapper.selectById(tenantId);
        return tenant != null ? R.ok(tenant) : R.fail(404, "租户不存在");
    }

    @PutMapping("/config")
    public R<String> saveConfig(@RequestBody Tenant config) {
        Long tenantId = TenantContext.get();
        config.setId(tenantId);
        tenantMapper.updateById(config);
        return R.ok("保存成功");
    }

    @GetMapping("/rooms")
    public R<List<RoomType>> getRoomTypes() {
        Long tenantId = TenantContext.get();
        List<RoomType> rooms = roomTypeMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RoomType>()
                .eq(RoomType::getTenantId, tenantId));
        return R.ok(rooms);
    }

    @Transactional
    @PutMapping("/rooms")
    public R<String> saveRoomTypes(@RequestBody List<Map<String, Object>> roomList) {
        Long tenantId = TenantContext.get();
        // 删除旧房型 + 房态，重建
        roomTypeMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RoomType>()
                .eq(RoomType::getTenantId, tenantId));
        roomStatusMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.sushijia.repository.entity.RoomStatus>()
                .eq(com.sushijia.repository.entity.RoomStatus::getTenantId, tenantId));

        for (int i = 0; i < roomList.size(); i++) {
            Map<String, Object> rm = roomList.get(i);
            com.sushijia.repository.entity.RoomType rt = new com.sushijia.repository.entity.RoomType();
            rt.setTenantId(tenantId);
            rt.setName((String) rm.get("name"));
            rt.setBasePrice(new java.math.BigDecimal(String.valueOf(rm.getOrDefault("basePrice", 0))));
            rt.setCount(((Number) rm.getOrDefault("count", 0)).intValue());
            rt.setSortOrder(i);
            roomTypeMapper.insert(rt);

            // 为每个房型自动创建默认房态
            int count = rt.getCount();
            for (int j = 0; j < count; j++) {
                com.sushijia.repository.entity.RoomStatus rs = new com.sushijia.repository.entity.RoomStatus();
                rs.setTenantId(tenantId);
                rs.setRoomTypeId(rt.getId());
                rs.setRoomNumber(String.valueOf((i + 1) * 100 + j + 1));
                rs.setStatus("free");
                roomStatusMapper.insert(rs);
            }
        }
        return R.ok("保存成功");
    }
}
