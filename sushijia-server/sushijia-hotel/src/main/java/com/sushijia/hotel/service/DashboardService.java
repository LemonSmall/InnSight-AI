package com.sushijia.hotel.service;

import com.sushijia.framework.tenant.TenantContext;
import com.sushijia.repository.entity.*;
import com.sushijia.repository.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 数字营销大盘服务
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final RoomStatusMapper roomStatusMapper;
    private final RoomTypeMapper roomTypeMapper;
    private final FutureRoomStatusMapper futureRoomStatusMapper;
    private final GuestMapper guestMapper;
    private final TenantMapper tenantMapper;

    public Map<String, Object> getDashboard(Long tenantId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("kpi", calcKpis(tenantId));
        result.put("roomTypeStats", calcRoomTypeStats(tenantId));
        result.put("futureStatus", calcFutureStatus(tenantId));
        result.put("guests", queryGuests(tenantId));
        result.put("config", tenantMapper.selectById(tenantId));
        return result;
    }

    private Map<String, Object> calcKpis(Long tenantId) {
        List<RoomType> roomTypes = roomTypeMapper.selectList(
                query(RoomType.class).eq(RoomType::getTenantId, tenantId));
        int totalRooms = roomTypes.stream().mapToInt(RoomType::getCount).sum();

        List<RoomStatus> statuses = roomStatusMapper.selectList(
                query(RoomStatus.class).eq(RoomStatus::getTenantId, tenantId));
        int totalSold = (int) statuses.stream().filter(r -> "sold".equals(r.getStatus())).count();

        Map<Long, java.math.BigDecimal> priceMap = roomTypes.stream()
                .collect(Collectors.toMap(RoomType::getId, RoomType::getBasePrice));
        int totalRevenue = statuses.stream()
                .filter(r -> "sold".equals(r.getStatus()))
                .mapToInt(r -> priceMap.getOrDefault(r.getRoomTypeId(), java.math.BigDecimal.ZERO).intValue())
                .sum();

        int occupancyRate = totalRooms > 0 ? Math.round((float) totalSold / totalRooms * 100) : 0;
        int revpar = totalRooms > 0 ? Math.round((float) totalRevenue / totalRooms) : 0;

        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.put("occupancyRate", occupancyRate);
        kpi.put("totalSold", totalSold);
        kpi.put("totalRooms", totalRooms);
        kpi.put("freeCount", totalRooms - totalSold);
        kpi.put("totalRevenue", totalRevenue);
        kpi.put("revpar", revpar);
        return kpi;
    }

    private List<Map<String, Object>> calcRoomTypeStats(Long tenantId) {
        List<RoomType> roomTypes = roomTypeMapper.selectList(
                query(RoomType.class).eq(RoomType::getTenantId, tenantId));

        List<Map<String, Object>> stats = new ArrayList<>();
        for (RoomType rt : roomTypes) {
            List<RoomStatus> rooms = roomStatusMapper.selectList(
                    query(RoomStatus.class).eq(RoomStatus::getTenantId, tenantId)
                            .eq(RoomStatus::getRoomTypeId, rt.getId()));

            Map<String, Object> stat = new LinkedHashMap<>();
            stat.put("id", rt.getId());
            stat.put("name", rt.getName());
            stat.put("basePrice", rt.getBasePrice());
            stat.put("total", rooms.size());
            stat.put("sold", rooms.stream().filter(r -> "sold".equals(r.getStatus())).count());
            stat.put("free", rooms.stream().filter(r -> "free".equals(r.getStatus())).count());
            stat.put("dirty", rooms.stream().filter(r -> "dirty".equals(r.getStatus())).count());
            stat.put("repair", rooms.stream().filter(r -> "repair".equals(r.getStatus())).count());
            stats.add(stat);
        }
        return stats;
    }

    private List<Map<String, Object>> calcFutureStatus(Long tenantId) {
        List<FutureRoomStatus> list = futureRoomStatusMapper.findByTenant(tenantId);
        Map<String, Map<String, Object>> byDate = new LinkedHashMap<>();
        for (FutureRoomStatus fs : list) {
            String key = fs.getDate().toString();
            Map<String, Object> day = byDate.computeIfAbsent(key, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("date", fs.getDate().toString().substring(5));
                m.put("totalOccupied", 0);
                m.put("totalAvailable", 0);
                return m;
            });
            day.put("totalOccupied", (int) day.get("totalOccupied") + fs.getOccupied());
            day.put("totalAvailable", (int) day.get("totalAvailable") + fs.getAvailable());
        }
        return new ArrayList<>(byDate.values());
    }

    private List<Map<String, Object>> queryGuests(Long tenantId) {
        return guestMapper.selectList(
                query(Guest.class).eq(Guest::getTenantId, tenantId)
                        .in(Guest::getStatus, "staying", "checking_in"))
                .stream().map(g -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("roomNumber", g.getRoomNumber());
                    m.put("guestType", g.getGuestType());
                    m.put("source", g.getSource());
                    m.put("nights", g.getNights());
                    m.put("checkoutDate", g.getCheckoutDate());
                    return m;
                }).collect(Collectors.toList());
    }

    private <T> com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<T> query(Class<T> clz) {
        return new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
    }
}
