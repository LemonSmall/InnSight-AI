package com.sushijia.hotel.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sushijia.repository.entity.RoomType;
import com.sushijia.repository.mapper.RoomTypeMapper;
import com.sushijia.repository.mapper.TenantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Hotel profile dashboard data.
 *
 * The product is not connected to a PMS, OTA or order system, so this service
 * only exposes tenant-maintained hotel and room-type reference information.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final RoomTypeMapper roomTypeMapper;
    private final TenantMapper tenantMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public Map<String, Object> getDashboard(Long tenantId) {
        String cacheKey = "sushijia:tenant:" + tenantId + ":dashboard";
        Map<String, Object> cached = readDashboard(cacheKey);
        if (cached != null) {
            return cached;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("config", tenantMapper.selectById(tenantId));
        result.put("roomTypeStats", loadRoomTypes(tenantId));
        result.put("dataScope", "tenant_profile_and_confirmed_knowledge_only");
        writeDashboard(cacheKey, result);
        return result;
    }

    private List<Map<String, Object>> loadRoomTypes(Long tenantId) {
        List<RoomType> roomTypes = roomTypeMapper.selectList(
            new LambdaQueryWrapper<RoomType>()
                .eq(RoomType::getTenantId, tenantId)
                .eq(RoomType::getEnabled, 1)
                .orderByAsc(RoomType::getSortOrder)
                .orderByAsc(RoomType::getId));

        List<Map<String, Object>> result = new ArrayList<>();
        for (RoomType roomType : roomTypes) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", roomType.getId());
            item.put("name", roomType.getName());
            item.put("basePrice", roomType.getBasePrice());
            item.put("total", roomType.getCount());
            result.add(item);
        }
        return result;
    }

    private Map<String, Object> readDashboard(String key) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            return value == null || value.isBlank()
                ? null
                : objectMapper.readValue(value, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ignored) {
            return null;
        }
    }

    private void writeDashboard(String key, Map<String, Object> value) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), 2, TimeUnit.MINUTES);
        } catch (Exception ignored) {
            // Redis is an acceleration layer only.
        }
    }
}
