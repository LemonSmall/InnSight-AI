package com.sushijia.hotel.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sushijia.common.response.R;
import com.sushijia.framework.tenant.TenantContext;
import com.sushijia.hotel.model.DistrictCandidate;
import com.sushijia.hotel.model.HotelPoiCandidate;
import com.sushijia.hotel.model.WeatherNow;
import com.sushijia.hotel.service.AmapPoiService;
import com.sushijia.hotel.service.SurroundingIntelService;
import com.sushijia.repository.entity.RoomType;
import com.sushijia.repository.entity.Tenant;
import com.sushijia.repository.mapper.RoomTypeMapper;
import com.sushijia.repository.mapper.TenantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/hotel")
@RequiredArgsConstructor
public class HotelConfigController {

    private final TenantMapper tenantMapper;
    private final RoomTypeMapper roomTypeMapper;
    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AmapPoiService amapPoiService;
    private final SurroundingIntelService surroundingIntelService;

    @GetMapping("/config")
    public R<Tenant> getConfig() {
        Long tenantId = TenantContext.get();
        Tenant cached = readCache(hotelConfigKey(tenantId), Tenant.class);
        if (cached != null) {
            return R.ok(cached);
        }
        Tenant tenant = tenantMapper.selectById(tenantId);
        writeCache(hotelConfigKey(tenantId), tenant, 10, TimeUnit.MINUTES);
        return tenant != null ? R.ok(tenant) : R.fail(404, "tenant not found");
    }

    @PutMapping("/config")
    public R<String> saveConfig(@RequestBody Tenant config) {
        Long tenantId = TenantContext.get();
        config.setId(tenantId);
        config.setTotalRooms(calculateTenantTotalRooms(tenantId));
        tenantMapper.updateById(config);
        clearTenantProfileCache(tenantId);
        return R.ok("saved");
    }

    @GetMapping("/poi/search")
    public R<List<HotelPoiCandidate>> searchPoi(@RequestParam("keyword") String keyword,
                                                @RequestParam(value = "city", required = false) String city) {
        return R.ok(amapPoiService.searchHotel(keyword, city));
    }

    @GetMapping("/region/children")
    public R<List<DistrictCandidate>> regionChildren(@RequestParam(value = "keyword", defaultValue = "China") String keyword,
                                                     @RequestParam(value = "subdistrict", defaultValue = "1") int subdistrict) {
        return R.ok(amapPoiService.districtChildren(keyword, subdistrict));
    }

    @GetMapping("/weather/current")
    public R<WeatherNow> currentWeather() {
        Long tenantId = TenantContext.get();
        WeatherNow cached = readCache(weatherKey(tenantId), WeatherNow.class);
        if (cached != null) {
            return R.ok(cached);
        }
        Tenant tenant = tenantMapper.selectById(tenantId);
        String city = tenant == null ? "" : firstNotBlank(tenant.getPoiAdcode(), tenant.getPoiCity(), tenant.getCity());
        WeatherNow weather = amapPoiService.currentWeather(city);
        writeCache(weatherKey(tenantId), weather, 20, TimeUnit.MINUTES);
        return R.ok(weather);
    }

    @PostMapping("/poi/bind")
    @Transactional
    public R<Map<String, Object>> bindPoi(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContext.get();
        String poiId = String.valueOf(body.getOrDefault("poiId", ""));
        HotelPoiCandidate poi = amapPoiService.getHotelById(poiId);
        Tenant config = savePoiBinding(tenantId, poi);
        if (config == null) return R.fail(400, "酒店绑定保存失败，请稍后重试");
        return R.ok(bindingResponse(config));
    }

    @PostMapping("/poi/bind-candidate")
    @Transactional
    public R<Map<String, Object>> bindPoiCandidate(@RequestBody HotelPoiCandidate poi) {
        Long tenantId = TenantContext.get();
        if (!amapPoiService.isHotelPoi(poi)) {
            return R.fail(400, "only hotel-like POI can be bound");
        }
        Tenant config = savePoiBinding(tenantId, poi);
        if (config == null) return R.fail(400, "酒店绑定保存失败，请稍后重试");
        return R.ok(bindingResponse(config));
    }

    @PostMapping("/surrounding/recommendation")
    public R<Map<String, Object>> surroundingRecommendation() {
        Long tenantId = TenantContext.get();
        Tenant tenant = tenantMapper.selectById(tenantId);
        HotelPoiCandidate poi = fromTenant(tenant);
        return R.ok(surroundingIntelService.recommendFromBinding(tenantId, currentUserId(), tenant, poi));
    }

    private Map<String, Object> bindingResponse(Tenant config) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("config", config);
        return result;
    }

    @Transactional
    protected Tenant savePoiBinding(Long tenantId, HotelPoiCandidate poi) {
        Tenant update = new Tenant();
        update.setId(tenantId);
        update.setPoiProvider("amap");
        update.setPoiId(poi.getPoiId());
        update.setPoiName(poi.getName());
        update.setPoiAddress(poi.getAddress());
        update.setPoiProvince(poi.getProvince());
        update.setPoiCity(poi.getCity());
        update.setPoiDistrict(poi.getDistrict());
        update.setPoiAdcode(poi.getAdcode());
        update.setPoiLongitude(poi.getLongitude());
        update.setPoiLatitude(poi.getLatitude());
        update.setPoiTypeCode(poi.getTypeCode());
        update.setPoiTypeName(poi.getTypeName());
        update.setPoiVerified(true);
        update.setPoiSyncedAt(LocalDateTime.now());
        update.setName(firstNotBlank(poi.getName(), ""));
        update.setType(inferHotelType(poi));
        update.setCity(joinRegion(poi));
        update.setTags(inferTags(poi));
        update.setTargetAudience(inferAudience(poi));
        update.setNearby(inferNearby(poi));
        int updatedRows = tenantMapper.updateById(update);
        if (updatedRows <= 0) {
            return null;
        }

        clearTenantProfileCache(tenantId);
        return tenantMapper.selectById(tenantId);
    }

    @GetMapping("/rooms")
    public R<List<RoomType>> getRoomTypes() {
        Long tenantId = TenantContext.get();
        List<RoomType> cached = readCache(roomTypesKey(tenantId), new TypeReference<List<RoomType>>() {});
        if (cached != null) {
            return R.ok(cached);
        }
        List<RoomType> rooms = roomTypeMapper.selectList(
                activeRoomTypeQuery(tenantId));
        writeCache(roomTypesKey(tenantId), rooms, 10, TimeUnit.MINUTES);
        return R.ok(rooms);
    }

    @Transactional
    @PutMapping("/rooms")
    public R<List<RoomType>> saveRoomTypes(@RequestBody List<Map<String, Object>> roomList) {
        Long tenantId = TenantContext.get();
        List<RoomType> existingRooms = roomTypeMapper.selectList(
                activeRoomTypeQuery(tenantId));
        Map<Long, RoomType> existingById = new HashMap<>();
        for (RoomType room : existingRooms) {
            existingById.put(room.getId(), room);
        }
        Set<Long> keptIds = new HashSet<>();

        for (int i = 0; i < roomList.size(); i++) {
            Map<String, Object> rm = roomList.get(i);
            RoomType rt = new RoomType();
            rt.setTenantId(tenantId);
            rt.setName((String) rm.get("name"));
            rt.setBasePrice(new BigDecimal(String.valueOf(rm.getOrDefault("basePrice", 0))));
            rt.setCount(((Number) rm.getOrDefault("count", 0)).intValue());
            rt.setSortOrder(i);
            rt.setEnabled(1);
            Long id = parseLong(rm.get("id"));
            if (id != null && existingById.containsKey(id)) {
                rt.setId(id);
                roomTypeMapper.updateById(rt);
                keptIds.add(id);
            } else {
                roomTypeMapper.insert(rt);
                keptIds.add(rt.getId());
            }
        }

        for (RoomType room : existingRooms) {
            if (keptIds.contains(room.getId())) continue;
            archiveRoomType(tenantId, room.getId());
        }
        clearTenantProfileCache(tenantId);
        List<RoomType> savedRooms = selectTenantRoomTypes(tenantId);
        syncTenantTotalRooms(tenantId, savedRooms);
        writeCache(roomTypesKey(tenantId), savedRooms, 10, TimeUnit.MINUTES);
        return R.ok(savedRooms);
    }

    @Transactional
    @DeleteMapping("/rooms/{id}")
    public R<List<RoomType>> deleteRoomType(@PathVariable("id") Long id) {
        Long tenantId = TenantContext.get();
        if (id == null || id <= 0) {
            return R.ok(selectTenantRoomTypes(tenantId));
        }
        RoomType room = roomTypeMapper.selectOne(
                new LambdaQueryWrapper<RoomType>()
                        .eq(RoomType::getTenantId, tenantId)
                        .eq(RoomType::getId, id)
                        .eq(RoomType::getEnabled, 1)
                        .last("LIMIT 1"));
        if (room != null) {
            archiveRoomType(tenantId, id);
        }
        clearTenantProfileCache(tenantId);
        List<RoomType> savedRooms = selectTenantRoomTypes(tenantId);
        syncTenantTotalRooms(tenantId, savedRooms);
        writeCache(roomTypesKey(tenantId), savedRooms, 10, TimeUnit.MINUTES);
        return R.ok(savedRooms);
    }

    @GetMapping("/occupancy-imports/current")
    public R<Map<String, Object>> getOccupancyImportRecords() {
        Long tenantId = TenantContext.get();
        ensureOccupancyImportTable();
        return R.ok(occupancyPayload(tenantId, Map.of(
                "added", 0,
                "duplicates", 0,
                "conflicts", 0,
                "skippedConflicts", 0,
                "total", 0
        )));
    }

    @Transactional
    @PostMapping("/occupancy-imports/import")
    public R<Map<String, Object>> importOccupancyRecords(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContext.get();
        ensureOccupancyImportTable();

        String sourceFileName = text(body.get("sourceFileName"));
        boolean overwrite = Boolean.TRUE.equals(body.get("overwrite"));
        Object rawRecords = body.get("records");
        List<?> records = rawRecords instanceof List<?> ? (List<?>) rawRecords : List.of();
        int added = 0;
        int duplicates = 0;
        int conflicts = 0;
        int skippedConflicts = 0;

        for (Object item : records) {
            if (!(item instanceof Map<?, ?> raw)) continue;
            String date = text(raw.get("date"));
            String roomTypeName = cleanRoomTypeName(text(firstValue(raw, "roomTypeName", "roomType", "房型", "房型名称")));
            String normalizedRoomTypeName = normalizeRoomTypeName(roomTypeName);
            if (date.isBlank() || normalizedRoomTypeName.isBlank()) continue;
            int totalRooms = intValue(firstValue(raw, "totalRooms", "total", "roomCount", "总房量", "总房数"));
            int occupiedRooms = intValue(firstValue(raw, "occupiedRooms", "occupied", "soldRooms", "usedRooms", "占用房", "已住房", "出租房"));
            int remainingRooms = intValue(firstValue(raw, "remainingRooms", "remaining", "availableRooms", "vacantRooms", "剩余可售", "可售房", "余房"));
            if (totalRooms <= 0) totalRooms = Math.max(0, occupiedRooms + remainingRooms);
            BigDecimal occupancyRate = decimalRate(firstValue(raw, "occupancyRate", "rate", "出租率", "入住率"), occupiedRooms, totalRooms);

            List<Map<String, Object>> existing = jdbcTemplate.queryForList(
                    "SELECT id, total_rooms, occupied_rooms, remaining_rooms, occupancy_rate FROM occupancy_import_records " +
                            "WHERE tenant_id = ? AND business_date = ? AND normalized_room_type_name = ? LIMIT 1",
                    tenantId, date, normalizedRoomTypeName);
            if (existing.isEmpty()) {
                jdbcTemplate.update(
                        "INSERT INTO occupancy_import_records " +
                                "(tenant_id, source_file_name, business_date, room_type_name, normalized_room_type_name, total_rooms, occupied_rooms, remaining_rooms, occupancy_rate) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        tenantId, sourceFileName, date, roomTypeName, normalizedRoomTypeName, totalRooms, occupiedRooms, remainingRooms, occupancyRate);
                added++;
                continue;
            }

            Map<String, Object> old = existing.get(0);
            if (sameOccupancyRecord(old, totalRooms, occupiedRooms, remainingRooms, occupancyRate)) {
                duplicates++;
                continue;
            }
            conflicts++;
            if (overwrite) {
                jdbcTemplate.update(
                        "UPDATE occupancy_import_records SET source_file_name = ?, room_type_name = ?, total_rooms = ?, occupied_rooms = ?, remaining_rooms = ?, occupancy_rate = ?, updated_at = CURRENT_TIMESTAMP " +
                                "WHERE id = ?",
                        sourceFileName, roomTypeName, totalRooms, occupiedRooms, remainingRooms, occupancyRate, old.get("id"));
            } else {
                skippedConflicts++;
            }
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("added", added);
        report.put("duplicates", duplicates);
        report.put("conflicts", conflicts);
        report.put("skippedConflicts", skippedConflicts);
        report.put("total", records.size());
        return R.ok(occupancyPayload(tenantId, report));
    }

    private List<RoomType> selectTenantRoomTypes(Long tenantId) {
        return roomTypeMapper.selectList(
                activeRoomTypeQuery(tenantId));
    }

    private LambdaQueryWrapper<RoomType> activeRoomTypeQuery(Long tenantId) {
        return new LambdaQueryWrapper<RoomType>()
                .eq(RoomType::getTenantId, tenantId)
                .eq(RoomType::getEnabled, 1)
                .orderByAsc(RoomType::getSortOrder)
                .orderByAsc(RoomType::getId);
    }

    private void archiveRoomType(Long tenantId, Long roomTypeId) {
        if (tenantId == null || roomTypeId == null) return;
        RoomType update = new RoomType();
        update.setId(roomTypeId);
        update.setEnabled(0);
        roomTypeMapper.update(update, new LambdaQueryWrapper<RoomType>()
                .eq(RoomType::getTenantId, tenantId)
                .eq(RoomType::getId, roomTypeId));
    }

    private int calculateTenantTotalRooms(Long tenantId) {
        return selectTenantRoomTypes(tenantId).stream()
                .map(RoomType::getCount)
                .filter(count -> count != null)
                .mapToInt(Integer::intValue)
                .sum();
    }

    private void syncTenantTotalRooms(Long tenantId, List<RoomType> rooms) {
        int totalRooms = rooms.stream()
                .map(RoomType::getCount)
                .filter(count -> count != null)
                .mapToInt(Integer::intValue)
                .sum();
        Tenant update = new Tenant();
        update.setId(tenantId);
        update.setTotalRooms(totalRooms);
        tenantMapper.updateById(update);
        clearTenantProfileCache(tenantId);
    }

    private Map<String, Object> occupancyPayload(Long tenantId, Map<String, Object> report) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT source_file_name, business_date, room_type_name, total_rooms, occupied_rooms, remaining_rooms, occupancy_rate " +
                        "FROM occupancy_import_records WHERE tenant_id = ? ORDER BY business_date ASC, room_type_name ASC",
                tenantId);
        List<Map<String, Object>> records = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("date", row.get("business_date"));
            record.put("roomTypeName", row.get("room_type_name"));
            record.put("totalRooms", row.get("total_rooms"));
            record.put("occupiedRooms", row.get("occupied_rooms"));
            record.put("remainingRooms", row.get("remaining_rooms"));
            record.put("occupancyRate", row.get("occupancy_rate"));
            records.add(record);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sourceFileName", rows.isEmpty() ? "" : text(rows.get(rows.size() - 1).get("source_file_name")));
        payload.put("records", records);
        payload.put("report", report);
        return payload;
    }

    private void ensureOccupancyImportTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS occupancy_import_records (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  tenant_id BIGINT NOT NULL,
                  source_file_name VARCHAR(255) DEFAULT '',
                  business_date VARCHAR(20) NOT NULL,
                  room_type_name VARCHAR(100) NOT NULL,
                  normalized_room_type_name VARCHAR(100) NOT NULL,
                  total_rooms INT NOT NULL DEFAULT 0,
                  occupied_rooms INT NOT NULL DEFAULT 0,
                  remaining_rooms INT NOT NULL DEFAULT 0,
                  occupancy_rate DECIMAL(8,4) NOT NULL DEFAULT 0,
                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  UNIQUE KEY uk_occupancy_tenant_date_room (tenant_id, business_date, normalized_room_type_name),
                  INDEX idx_occupancy_tenant_date (tenant_id, business_date)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='历史房态导入记录'
                """);
    }

    private boolean sameOccupancyRecord(Map<String, Object> old, int totalRooms, int occupiedRooms, int remainingRooms, BigDecimal occupancyRate) {
        return intValue(old.get("total_rooms")) == totalRooms
                && intValue(old.get("occupied_rooms")) == occupiedRooms
                && intValue(old.get("remaining_rooms")) == remainingRooms
                && decimal(old.get("occupancy_rate")).compareTo(occupancyRate) == 0;
    }

    private Object firstValue(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key)) return map.get(key);
        }
        return null;
    }

    private String cleanRoomTypeName(String value) {
        return value.replaceAll("(?:[（(]\\s*\\d+\\s*[)）])+\\s*$", "")
                .replaceAll("\\s*\\d+\\s*间\\s*$", "")
                .trim();
    }

    private String normalizeRoomTypeName(String value) {
        return cleanRoomTypeName(value).replaceAll("\\s+", "");
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private int intValue(Object value) {
        try {
            if (value instanceof Number number) return number.intValue();
            String text = text(value).replace(",", "");
            if (text.isBlank()) return 0;
            return (int) Math.round(Double.parseDouble(text.replaceAll("[^0-9.\\-]", "")));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private BigDecimal decimalRate(Object value, int occupiedRooms, int totalRooms) {
        BigDecimal explicit = decimal(value);
        if (explicit.compareTo(BigDecimal.ZERO) > 0) {
            return explicit.compareTo(BigDecimal.ONE) > 0
                    ? explicit.divide(BigDecimal.valueOf(100), 4, BigDecimal.ROUND_HALF_UP)
                    : explicit.setScale(4, BigDecimal.ROUND_HALF_UP);
        }
        if (totalRooms <= 0) return BigDecimal.ZERO.setScale(4, BigDecimal.ROUND_HALF_UP);
        return BigDecimal.valueOf(occupiedRooms)
                .divide(BigDecimal.valueOf(totalRooms), 4, BigDecimal.ROUND_HALF_UP);
    }

    private BigDecimal decimal(Object value) {
        try {
            if (value instanceof BigDecimal decimal) return decimal.setScale(4, BigDecimal.ROUND_HALF_UP);
            if (value instanceof Number number) return BigDecimal.valueOf(number.doubleValue()).setScale(4, BigDecimal.ROUND_HALF_UP);
            String text = text(value).replace(",", "");
            if (text.isBlank()) return BigDecimal.ZERO.setScale(4, BigDecimal.ROUND_HALF_UP);
            return new BigDecimal(text.replaceAll("[^0-9.\\-]", "")).setScale(4, BigDecimal.ROUND_HALF_UP);
        } catch (Exception ignored) {
            return BigDecimal.ZERO.setScale(4, BigDecimal.ROUND_HALF_UP);
        }
    }

    private Long parseLong(Object value) {
        if (value == null) return null;
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void detachGuestsFromRoomType(Long roomTypeId) {
        if (roomTypeId == null) return;
        try {
            jdbcTemplate.update(
                    "UPDATE guests SET room_type_id = NULL WHERE room_type_id = ?",
                    roomTypeId
            );
        } catch (Exception ignored) {
            // Old deployments may not have the guests table anymore.
        }
    }

    private void clearRoomTypeReferences(Long roomTypeId) {
        if (roomTypeId == null) return;
        try {
            jdbcTemplate.update(
                    "UPDATE room_status SET room_type_id = NULL WHERE room_type_id = ?",
                    roomTypeId
            );
        } catch (Exception ignored) {
            // Old deployments may not have the room_status table anymore.
        }
        try {
            jdbcTemplate.update(
                    "UPDATE future_room_status SET room_type_id = NULL WHERE room_type_id = ?",
                    roomTypeId
            );
        } catch (Exception ignored) {
            // Old deployments may not have the future_room_status table anymore.
        }
    }

    private void clearTenantProfileCache(Long tenantId) {
        if (tenantId == null) return;
        try {
            redisTemplate.delete(List.of(
                    hotelConfigKey(tenantId),
                    roomTypesKey(tenantId),
                    dashboardKey(tenantId),
                    knowledgeSnapshotKey(tenantId),
                    weatherKey(tenantId)
            ));
        } catch (Exception ignored) {
            // Redis is an acceleration layer only; cache cleanup must not roll back business writes.
        }
    }

    private String hotelConfigKey(Long tenantId) {
        return "sushijia:tenant:" + tenantId + ":config";
    }

    private String roomTypesKey(Long tenantId) {
        return "sushijia:tenant:" + tenantId + ":rooms";
    }

    private String dashboardKey(Long tenantId) {
        return "sushijia:tenant:" + tenantId + ":dashboard";
    }

    private String knowledgeSnapshotKey(Long tenantId) {
        return "sushijia:knowledge:snapshot:" + tenantId;
    }

    private String weatherKey(Long tenantId) {
        return "sushijia:tenant:" + tenantId + ":weather";
    }

    private <T> T readCache(String key, Class<T> type) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            return value == null || value.isBlank() ? null : objectMapper.readValue(value, type);
        } catch (Exception ignored) {
            return null;
        }
    }

    private <T> T readCache(String key, TypeReference<T> type) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            return value == null || value.isBlank() ? null : objectMapper.readValue(value, type);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void writeCache(String key, Object value, long timeout, TimeUnit unit) {
        if (value == null) return;
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), timeout, unit);
        } catch (Exception ignored) {
            // Redis is an acceleration layer only.
        }
    }

    private boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String firstNotBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (notBlank(value)) return value.trim();
        }
        return "";
    }

    private String joinRegion(HotelPoiCandidate poi) {
        List<String> parts = new ArrayList<>();
        if (notBlank(poi.getProvince())) parts.add(poi.getProvince());
        if (notBlank(poi.getCity()) && !poi.getCity().equals(poi.getProvince())) parts.add(poi.getCity());
        if (notBlank(poi.getDistrict()) && !poi.getDistrict().equals(poi.getCity())) parts.add(poi.getDistrict());
        return parts.isEmpty() ? poi.getCity() : String.join(" / ", parts);
    }

    private String inferHotelType(HotelPoiCandidate poi) {
        String text = String.join(" ",
                firstNotBlank(poi.getTypeName(), ""),
                firstNotBlank(poi.getName(), ""),
                firstNotBlank(poi.getAddress(), ""),
                firstNotBlank(poi.getKeytag(), ""),
                firstNotBlank(poi.getBusinessArea(), ""));
        if (containsAny(text, "亲子", "儿童", "乐园", "family", "kids")) return "亲子民宿";
        if (containsAny(text, "民宿", "客栈", "homestay", "inn", "guesthouse")) return "精品民宿";
        if (containsAny(text, "度假", "温泉", "山庄", "resort", "spa", "villa")) return "度假酒店";
        if (containsAny(text, "公寓", "apartment")) return "酒店式公寓";
        if (containsAny(text, "商务", "国际", "广场", "中心", "business", "international", "plaza")) return "商务酒店";
        return "酒店";
    }

    private String inferTags(HotelPoiCandidate poi) {
        String text = firstNotBlank(poi.getName(), poi.getTypeName(), poi.getAddress());
        List<String> tags = new ArrayList<>();
        if (containsAny(text, "学校", "大学", "学院")) tags.add("附近学校较多");
        if (containsAny(text, "地铁", "车站", "机场", "高铁", "火车")) tags.add("交通便利");
        if (containsAny(text, "商圈", "CBD", "广场", "中心", "购物", "business", "plaza", "center")) tags.add("商圈便利");
        if (containsAny(text, "景区", "公园", "山", "湖", "江", "河", "竹", "温泉", "mountain", "lake", "river", "view")) tags.add("周边游友好");
        if (containsAny(text, "民宿", "客栈", "homestay", "inn")) tags.add("本地体验");
        if (notBlank(poi.getKeytag())) tags.add(poi.getKeytag());
        if (notBlank(poi.getBusinessArea())) tags.add(poi.getBusinessArea());
        if (tags.isEmpty()) {
            tags.add("出行便利");
            if (notBlank(poi.getDistrict())) tags.add(poi.getDistrict() + "周边");
        }
        return String.join("、", tags);
    }

    private String inferAudience(HotelPoiCandidate poi) {
        String type = inferHotelType(poi);
        String text = firstNotBlank(poi.getName(), poi.getAddress(), poi.getTypeName());
        if (containsAny(text, "学校", "大学", "学院")) return "商务差旅客、亲子家庭、探校访友、城市短途游客";
        if ("精品民宿".equals(type)) return "周末度假客、情侣客群、亲子家庭、短途旅行客";
        if ("度假酒店".equals(type)) return "亲子家庭、情侣度假、团建客群、休闲旅行客";
        if ("公寓酒店".equals(type)) return "长住客、商务差旅客、亲子家庭";
        if ("商务酒店".equals(type)) return "商务差旅客、会议会展客、城市短住游客";
        return "商务差旅客、亲子家庭、情侣客群、短途旅行客";
    }

    private String inferNearby(HotelPoiCandidate poi) {
        List<String> parts = new ArrayList<>();
        if (notBlank(poi.getAddress())) parts.add("详细地址：" + poi.getAddress());
        if (notBlank(poi.getDistrict())) parts.add("所在区县：" + poi.getDistrict());
        if (notBlank(poi.getBusinessArea())) parts.add("商圈：" + poi.getBusinessArea());
        if (notBlank(poi.getTel())) parts.add("联系电话：" + poi.getTel());
        if (notBlank(poi.getKeytag())) parts.add("地点标签：" + poi.getKeytag());
        if (notBlank(poi.getRating())) parts.add("地图评分：" + poi.getRating());
        return parts.isEmpty() ? "已绑定真实门店位置，可补充周边商圈、交通枢纽和景点信息。" : String.join("；", parts);
    }

    private List<RoomType> suggestRooms(HotelPoiCandidate poi) {
        if (poi.getLowestPrice() == null || poi.getLowestPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }
        return rooms(room("map_reference_starting_room", poi.getLowestPrice().intValue(), 0));
    }

    private List<RoomType> rooms(RoomType... rooms) {
        return new ArrayList<>(List.of(rooms));
    }

    private RoomType room(String name, int basePrice, int count) {
        RoomType room = new RoomType();
        room.setName(name);
        room.setBasePrice(BigDecimal.valueOf(basePrice));
        room.setCount(count);
        return room;
    }

    private HotelPoiCandidate fromTenant(Tenant tenant) {
        HotelPoiCandidate poi = new HotelPoiCandidate();
        if (tenant == null) return poi;
        poi.setProvider(firstNotBlank(tenant.getPoiProvider(), "amap"));
        poi.setPoiId(tenant.getPoiId());
        poi.setName(firstNotBlank(tenant.getPoiName(), tenant.getName()));
        poi.setAddress(tenant.getPoiAddress());
        poi.setProvince(tenant.getPoiProvince());
        poi.setCity(tenant.getPoiCity());
        poi.setDistrict(tenant.getPoiDistrict());
        poi.setAdcode(tenant.getPoiAdcode());
        poi.setLongitude(tenant.getPoiLongitude());
        poi.setLatitude(tenant.getPoiLatitude());
        poi.setTypeCode(tenant.getPoiTypeCode());
        poi.setTypeName(tenant.getPoiTypeName());
        return poi;
    }

    private Long currentUserId() {
        try {
            Object staffId = org.springframework.web.context.request.RequestContextHolder
                    .currentRequestAttributes()
                    .getAttribute("staffId", org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST);
            return staffId instanceof Long ? (Long) staffId : staffId == null ? null : Long.valueOf(String.valueOf(staffId));
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean containsAny(String value, String... keywords) {
        String safeValue = value == null ? "" : value;
        for (String keyword : keywords) {
            if (safeValue.contains(keyword)) return true;
        }
        return false;
    }
}
