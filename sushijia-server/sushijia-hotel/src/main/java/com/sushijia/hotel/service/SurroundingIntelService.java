package com.sushijia.hotel.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sushijia.ai.service.AiInvocationService;
import com.sushijia.common.exception.BizException;
import com.sushijia.hotel.model.HotelPoiCandidate;
import com.sushijia.hotel.model.WeatherForecast;
import com.sushijia.hotel.model.WeatherNow;
import com.sushijia.repository.entity.Tenant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SurroundingIntelService {

    public static final String MODULE_KEY = "surrounding";
    public static final String TASK_MODE_FULL = "full";
    public static final String TASK_MODE_WEATHER_ONLY = "weather_only";

    private final AiInvocationService aiInvocationService;
    private final AmapPoiService amapPoiService;
    private final ObjectMapper objectMapper;

    public Map<String, Object> recommendFromBinding(Long tenantId,
                                                    Long userId,
                                                    Tenant tenant,
                                                    HotelPoiCandidate poi) {
        Map<String, Object> fallback = fallbackRecommendation(tenant, poi, TASK_MODE_FULL);
        try {
            Map<String, Object> context = buildContext(
                tenantId, userId, tenant, poi, null, null, "", TASK_MODE_FULL);
            String content = aiInvocationService.invoke(
                tenantId,
                MODULE_KEY,
                systemPrompt(TASK_MODE_FULL),
                userPrompt(tenant, poi, TASK_MODE_FULL),
                context
            );
            Map<String, Object> parsed = parseObject(content);
            if (parsed.isEmpty()) {
                return fallback;
            }
            mergeAmapCompetitorPrices(parsed, tenant, poi);
            parsed.putIfAbsent("provider", "dify_surrounding");
            parsed.putIfAbsent("queriedAt", LocalDateTime.now().toString());
            parsed.putIfAbsent("fallback", false);
            return parsed;
        } catch (BizException e) {
            log.info("Surrounding intel agent is unavailable; using fallback recommendation. tenantId={}, message={}",
                tenantId, e.getMessage());
            return fallback;
        } catch (Exception e) {
            log.warn("Surrounding intel agent failed; using fallback recommendation. tenantId={}", tenantId, e);
            return fallback;
        }
    }

    public Map<String, Object> collectForGeneration(Long tenantId,
                                                    Long userId,
                                                    Tenant tenant,
                                                    Map<String, Object> businessParams,
                                                    Object knowledgeContext,
                                                    String message,
                                                    String taskMode) {
        String normalizedMode = normalizeTaskMode(taskMode);
        HotelPoiCandidate poi = candidateFromTenant(tenant);
        Map<String, Object> fallback = fallbackRecommendation(tenant, poi, normalizedMode);
        if (TASK_MODE_WEATHER_ONLY.equals(normalizedMode)) {
            Map<String, Object> weatherOnly = collectWeatherOnlyFromAmap(tenant, poi, fallback);
            if (weatherOnly != null) {
                log.info("Surrounding context collected from local Amap weather API. tenantId={}, taskMode={}",
                    tenantId, normalizedMode);
                return weatherOnly;
            }
        }
        try {
            log.info("Calling Dify surrounding agent. tenantId={}, taskMode={}, message={}",
                tenantId, normalizedMode, message);
            Map<String, Object> context = buildContext(
                tenantId, userId, tenant, poi, businessParams, knowledgeContext, message, normalizedMode);
            String content = aiInvocationService.invoke(
                tenantId,
                MODULE_KEY,
                systemPrompt(normalizedMode),
                userPrompt(tenant, poi, normalizedMode),
                context
            );
            Map<String, Object> parsed = parseObject(content);
            if (parsed.isEmpty()) {
                return fallback;
            }
            mergeAmapCompetitorPrices(parsed, tenant, poi);
            parsed.putIfAbsent("provider", "dify_surrounding");
            parsed.putIfAbsent("taskMode", normalizedMode);
            parsed.putIfAbsent("queriedAt", LocalDateTime.now().toString());
            parsed.putIfAbsent("fallback", false);
            return parsed;
        } catch (BizException e) {
            log.info("Surrounding intel agent is unavailable; using fallback. tenantId={}, taskMode={}, message={}",
                tenantId, normalizedMode, e.getMessage());
            return fallback;
        } catch (Exception e) {
            log.warn("Surrounding intel agent failed; using fallback. tenantId={}, taskMode={}", tenantId, normalizedMode, e);
            return fallback;
        }
    }

    private Map<String, Object> collectWeatherOnlyFromAmap(Tenant tenant,
                                                           HotelPoiCandidate poi,
                                                           Map<String, Object> fallback) {
        String cityOrAdcode = firstNotBlank(
            poi.getAdcode(),
            poi.getCity(),
            value(tenant == null ? null : tenant.getPoiAdcode()),
            value(tenant == null ? null : tenant.getPoiCity()),
            value(tenant == null ? null : tenant.getCity())
        );
        if (cityOrAdcode.isBlank()) {
            return null;
        }
        try {
            WeatherNow now = amapPoiService.currentWeather(cityOrAdcode);
            List<WeatherForecast> forecasts = amapPoiService.forecastWeather(cityOrAdcode);
            Map<String, Object> result = new LinkedHashMap<>(fallback);
            Map<String, Object> weather = new LinkedHashMap<>();
            weather.put("summary", weatherSummary(now, forecasts));
            weather.put("temperature", now.getTemperature());
            weather.put("source", "amap_weather");
            weather.put("sourceUrl", "https://lbs.amap.com/api/webservice/guide/api/weatherinfo");
            weather.put("queriedAt", LocalDateTime.now().toString());
            weather.put("confidence", "high");
            weather.put("current", now);
            weather.put("forecast", forecasts);
            result.put("provider", "amap_weather");
            result.put("fallback", false);
            result.put("taskMode", TASK_MODE_WEATHER_ONLY);
            result.put("queriedAt", LocalDateTime.now().toString());
            result.put("weather", weather);
            result.put("searchEvidence", List.of(Map.of(
                "title", "Amap Weather API",
                "url", "https://lbs.amap.com/api/webservice/guide/api/weatherinfo",
                "source", "amap_weather",
                "queriedAt", LocalDateTime.now().toString(),
                "usedFor", "weather"
            )));
            result.put("unavailableFields", List.of());
            return result;
        } catch (Exception e) {
            log.info("Amap weather-only collection failed; falling back to surrounding agent. cityOrAdcode={}", cityOrAdcode);
            return null;
        }
    }

    private String weatherSummary(WeatherNow now, List<WeatherForecast> forecasts) {
        List<String> parts = new ArrayList<>();
        if (now != null) {
            parts.add("当前" + firstNotBlank(now.getWeather(), "天气未知")
                + (notBlank(now.getTemperature()) ? "，" + now.getTemperature() + "℃" : ""));
        }
        if (forecasts != null && !forecasts.isEmpty()) {
            List<String> days = new ArrayList<>();
            for (WeatherForecast item : forecasts) {
                days.add(item.getDate() + " " + firstNotBlank(item.getDayWeather(), item.getNightWeather())
                    + " " + firstNotBlank(item.getNightTemp(), "?") + "-" + firstNotBlank(item.getDayTemp(), "?") + "℃");
            }
            parts.add("预报：" + String.join("；", days));
        }
        return parts.isEmpty() ? "" : String.join("。", parts);
    }

    private Map<String, Object> buildContext(Long tenantId,
                                             Long userId,
                                             Tenant tenant,
                                             HotelPoiCandidate poi,
                                             Map<String, Object> businessParams,
                                             Object knowledgeContext,
                                             String message,
                                             String taskMode) {
        String normalizedMode = normalizeTaskMode(taskMode);
        Map<String, Object> hotel = new LinkedHashMap<>();
        hotel.put("name", firstNotBlank(value(tenant == null ? null : tenant.getName()), poi.getName()));
        hotel.put("type", value(tenant == null ? null : tenant.getType()));
        hotel.put("city", firstNotBlank(poi.getCity(), value(tenant == null ? null : tenant.getPoiCity()), value(tenant == null ? null : tenant.getCity())));
        hotel.put("district", firstNotBlank(poi.getDistrict(), value(tenant == null ? null : tenant.getPoiDistrict())));
        hotel.put("address", firstNotBlank(poi.getAddress(), value(tenant == null ? null : tenant.getPoiAddress())));
        hotel.put("longitude", poi.getLongitude() == null ? "" : poi.getLongitude().toPlainString());
        hotel.put("latitude", poi.getLatitude() == null ? "" : poi.getLatitude().toPlainString());
        hotel.put("adcode", firstNotBlank(poi.getAdcode(), value(tenant == null ? null : tenant.getPoiAdcode())));
        hotel.put("businessArea", poi.getBusinessArea());
        hotel.put("rating", poi.getRating());
        hotel.put("keytag", poi.getKeytag());
        hotel.put("tel", poi.getTel());
        hotel.put("checkIn", LocalDate.now().toString());
        hotel.put("checkOut", LocalDate.now().plusDays(1).toString());

        Map<String, Object> common = new LinkedHashMap<>();
        common.put("schemaVersion", "1.1");
        common.put("tenantId", tenantId);
        common.put("userId", userId);
        common.put("moduleKey", MODULE_KEY);
        common.put("currentTime", LocalDateTime.now().toString());
        common.put("hotel", Map.of("realWorldBinding", hotel));
        common.put("dataScope", Map.of(
            "requiresRealtimeSearch", true,
            "allowUnsourcedPrice", false,
            "allowInventedEvents", false,
            "requiredEvidence", true
        ));

        Map<String, Object> searchParams = new LinkedHashMap<>();
        searchParams.put("scene", "hotel_surrounding_intelligence");
        searchParams.put("taskMode", normalizedMode);
        searchParams.put("query", queryForMode(normalizedMode, message));
        searchParams.put("checkIn", hotel.get("checkIn"));
        searchParams.put("checkOut", hotel.get("checkOut"));
        searchParams.put("searchRadiusKm", 5);
        searchParams.put("allowedCompetitorTypes", List.of("酒店", "民宿", "客栈", "酒店式公寓"));
        searchParams.put("requiredTasks", requiredTasks(normalizedMode));
        searchParams.put("hotelIdentity", hotel);
        searchParams.put("originalBusinessParams", businessParams == null ? Map.of() : businessParams);
        searchParams.put("outputContract", "strict_json_only");

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("tenantId", tenantId);
        context.put("userId", userId);
        context.put("moduleKey", MODULE_KEY);
        context.put("hotelName", hotel.get("name"));
        context.put("city", hotel.get("city"));
        context.put("address", hotel.get("address"));
        context.put("longitude", hotel.get("longitude"));
        context.put("latitude", hotel.get("latitude"));
        context.put("checkIn", hotel.get("checkIn"));
        context.put("checkOut", hotel.get("checkOut"));
        context.put("taskMode", normalizedMode);
        context.put("query", queryForMode(normalizedMode, message));
        context.put("message", firstNotBlank(message, queryForMode(normalizedMode, message)));
        context.put("params", searchParams);
        context.put("commonContext", common);
        context.put("commonContextJson", toJson(common));
        context.put("businessParamsJson", toJson(searchParams));
        context.put("knowledgeContextJson", knowledgeContext == null ? "{}" : toJson(knowledgeContext));
        context.put("currentTime", LocalDateTime.now().toString());
        return context;
    }

    private Map<String, Object> fallbackRecommendation(Tenant tenant,
                                                       HotelPoiCandidate poi,
                                                       String taskMode) {
        String normalizedMode = normalizeTaskMode(taskMode);
        Map<String, Object> recommendation = new LinkedHashMap<>();
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("name", firstNotBlank(poi.getName(), value(tenant == null ? null : tenant.getName())));
        profile.put("type", inferHotelType(poi));
        profile.put("city", joinRegion(poi));
        profile.put("tags", inferTags(poi));
        profile.put("targetAudience", inferAudience(poi));
        profile.put("nearby", inferNearby(poi));

        recommendation.put("provider", "amap_fallback");
        recommendation.put("fallback", true);
        recommendation.put("taskMode", normalizedMode);
        recommendation.put("queriedAt", LocalDateTime.now().toString());
        recommendation.put("hotelProfileSuggestion", profile);
        recommendation.put("currentHotelPrices", currentHotelPrices(poi));
        recommendation.put("nearbyHotelPrices", amapNearbyHotelPrices(tenant, poi));
        recommendation.put("nearbyHotPlaces", List.of());
        recommendation.put("localEvents", List.of());
        recommendation.put("weather", Map.of(
            "summary", "",
            "temperature", "",
            "source", "",
            "sourceUrl", "",
            "queriedAt", "",
            "confidence", "low"
        ));
        recommendation.put("searchEvidence", evidence(poi));
        if (TASK_MODE_WEATHER_ONLY.equals(normalizedMode)) {
            recommendation.put("unavailableFields", List.of("未配置周边信息智能体或高德天气工具，暂未获取未来天气"));
        } else {
            recommendation.put("unavailableFields", List.of(
                "未配置周边信息智能体，暂未执行联网搜索",
                "未获取到当天实时房型价格",
                "未获取到周边实时热点事件"
            ));
        }
        return recommendation;
    }

    @SuppressWarnings("unchecked")
    private void mergeAmapCompetitorPrices(Map<String, Object> parsed, Tenant tenant, HotelPoiCandidate poi) {
        Object existing = parsed.get("nearbyHotelPrices");
        if (existing instanceof List<?> list && !list.isEmpty()) {
            return;
        }
        List<Map<String, Object>> prices = amapNearbyHotelPrices(tenant, poi);
        if (!prices.isEmpty()) {
            parsed.put("nearbyHotelPrices", prices);
            List<Object> unavailable = new ArrayList<>();
            Object rawUnavailable = parsed.get("unavailableFields");
            if (rawUnavailable instanceof List<?> list) {
                unavailable.addAll(list.stream()
                    .filter(item -> !String.valueOf(item).contains("周边") || !String.valueOf(item).contains("价格"))
                    .toList());
            }
            parsed.put("unavailableFields", unavailable);
        }
    }

    private List<Map<String, Object>> currentHotelPrices(HotelPoiCandidate poi) {
        if (poi.getLowestPrice() == null) {
            return List.of();
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("hotelName", firstNotBlank(poi.getName(), "当前酒店"));
        row.put("roomType", "公开最低价");
        row.put("price", "￥" + poi.getLowestPrice().stripTrailingZeros().toPlainString());
        row.put("currency", "CNY");
        row.put("checkIn", LocalDate.now().toString());
        row.put("checkOut", LocalDate.now().plusDays(1).toString());
        row.put("platform", "高德地图");
        row.put("source", "amap_poi");
        row.put("queriedAt", LocalDateTime.now().toString());
        row.put("confidence", "medium");
        row.put("note", "地图 POI biz_ext.lowest_price/cost，仅作公开参考");
        return List.of(row);
    }

    private List<Map<String, Object>> amapNearbyHotelPrices(Tenant tenant, HotelPoiCandidate poi) {
        try {
            List<HotelPoiCandidate> hotels = amapPoiService.searchNearbyHotels(
                poi.getLongitude(),
                poi.getLatitude(),
                firstNotBlank(poi.getCity(), value(tenant == null ? null : tenant.getPoiCity()), value(tenant == null ? null : tenant.getCity())),
                poi.getPoiId(),
                8
            );
            List<Map<String, Object>> rows = new ArrayList<>();
            for (HotelPoiCandidate hotel : hotels) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("hotelName", hotel.getName());
                row.put("distanceOrArea", firstNotBlank(hotel.getBusinessArea(), hotel.getDistrict()));
                row.put("roomType", hotel.getLowestPrice() == null ? "" : "公开最低价");
                row.put("price", hotel.getLowestPrice() == null ? "价格未公开" : "￥" + hotel.getLowestPrice().stripTrailingZeros().toPlainString());
                row.put("currency", "CNY");
                row.put("checkIn", LocalDate.now().toString());
                row.put("checkOut", LocalDate.now().plusDays(1).toString());
                row.put("platform", "高德地图");
                row.put("source", "amap_poi_around");
                row.put("sourceUrl", "");
                row.put("queriedAt", LocalDateTime.now().toString());
                row.put("confidence", hotel.getLowestPrice() == null ? "low" : "medium");
                row.put("note", hotel.getLowestPrice() == null ? "高德 POI 未返回公开价格，不用于直接跟价" : "高德 POI biz_ext.lowest_price/cost，仅作公开参考");
                rows.add(row);
            }
            return rows;
        } catch (Exception e) {
            log.info("Amap nearby hotel price collection failed; keeping fallback empty. tenantId={}", tenant == null ? null : tenant.getId());
            return List.of();
        }
    }

    private List<Map<String, Object>> evidence(HotelPoiCandidate poi) {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("source", "amap_poi");
        row.put("title", poi.getName());
        row.put("address", poi.getAddress());
        row.put("queriedAt", LocalDateTime.now().toString());
        row.put("confidence", "high");
        rows.add(row);
        return rows;
    }

    private String systemPrompt(String taskMode) {
        return """
            你是“宿识家周边信息智能体”，角色是外部情报搜索员，不是 AI 店长，不做最终定价决策，不输出营销方案。
            你的唯一任务是根据 commonContextJson、businessParamsJson、knowledgeContextJson 和 query 使用联网搜索工具汇总酒店经营所需的可核验外部情报。
            hotelName、city、address、longitude、latitude、checkIn、checkOut 只是后端为 Dify 调试和工具 query 提供的冗余字段；如与 JSON 冲突，以 commonContextJson 和 businessParamsJson 为准。
            必须读取 taskMode：
            - taskMode=weather_only 时，只调用高德天气工具或天气工具查询酒店所在地未来天气，不调用联网搜索工具，不搜索房价、竞品、热点和活动。
            - taskMode=full 时，才执行完整联网搜索。
            必须主动搜索：当前酒店公开房价和房型、周边竞品酒店/民宿/客栈/酒店式公寓公开房价和房型、周边热点、近期活动、天气。
            必须真实、可追溯、带来源。不得编造价格、房型、事件、客流、热度、距离或平台政策。
            如果联网搜索工具、地图/POI 工具、天气工具或网页检索不可用，必须在 unavailableFields 中说明，不得补写假数据。
            输出必须是严格 JSON 对象，不要 Markdown，不要代码块，不要解释文字。
            JSON 字段：
            {
              "provider": "dify_surrounding_agent",
              "fallback": false,
              "taskMode": "full|weather_only",
              "queriedAt": "ISO时间",
              "hotelProfileSuggestion": {"name":"","type":"","city":"","tags":"","targetAudience":"","nearby":"","businessArea":""},
              "currentHotelPrices": [{"hotelName":"","roomType":"","price":"","currency":"CNY","checkIn":"","checkOut":"","platform":"","source":"","sourceUrl":"","queriedAt":"","confidence":"high|medium|low","note":""}],
              "nearbyHotelPrices": [{"hotelName":"","distanceOrArea":"","roomType":"","price":"","currency":"CNY","checkIn":"","checkOut":"","platform":"","source":"","sourceUrl":"","queriedAt":"","confidence":"high|medium|low","note":""}],
              "nearbyHotPlaces": [{"name":"","category":"","reason":"","distanceOrArea":"","source":"","sourceUrl":"","confidence":"high|medium|low"}],
              "localEvents": [{"title":"","date":"","location":"","impact":"","source":"","sourceUrl":"","confidence":"high|medium|low"}],
              "weather": {"summary":"","temperature":"","source":"","sourceUrl":"","queriedAt":"","confidence":"high|medium|low"},
              "searchEvidence": [{"title":"","url":"","source":"","queriedAt":"","usedFor":""}],
              "unavailableFields": []
            }
            """;
    }

    private String userPrompt(Tenant tenant, HotelPoiCandidate poi, String taskMode) {
        String normalizedMode = normalizeTaskMode(taskMode);
        String instruction = TASK_MODE_WEATHER_ONLY.equals(normalizedMode)
            ? "请只查询酒店所在地未来天气，并严格输出 JSON。不要搜索房价、竞品、热点或活动。"
            : "请基于以下输入联网搜索酒店周边经营情报，并严格输出 JSON。必须搜索：当前酒店公开价格和房型、周边竞品酒店公开价格和房型、周边热门地点、近期活动和天气。";
        return instruction
            + "taskMode=" + normalizedMode
            + "酒店=" + firstNotBlank(poi.getName(), value(tenant == null ? null : tenant.getName()))
            + "，城市=" + firstNotBlank(poi.getCity(), value(tenant == null ? null : tenant.getCity()))
            + "，区县=" + poi.getDistrict()
            + "，地址=" + poi.getAddress()
            + "，经度=" + (poi.getLongitude() == null ? "" : poi.getLongitude().toPlainString())
            + "，纬度=" + (poi.getLatitude() == null ? "" : poi.getLatitude().toPlainString())
            + "，入住=" + LocalDate.now()
            + "，离店=" + LocalDate.now().plusDays(1)
            + "，商圈=" + poi.getBusinessArea();
    }

    private HotelPoiCandidate candidateFromTenant(Tenant tenant) {
        HotelPoiCandidate poi = new HotelPoiCandidate();
        poi.setProvider(firstNotBlank(value(tenant == null ? null : tenant.getPoiProvider()), "tenant_profile"));
        poi.setPoiId(value(tenant == null ? null : tenant.getPoiId()));
        poi.setName(firstNotBlank(value(tenant == null ? null : tenant.getPoiName()), value(tenant == null ? null : tenant.getName())));
        poi.setAddress(value(tenant == null ? null : tenant.getPoiAddress()));
        poi.setProvince(value(tenant == null ? null : tenant.getPoiProvince()));
        poi.setCity(firstNotBlank(value(tenant == null ? null : tenant.getPoiCity()), value(tenant == null ? null : tenant.getCity())));
        poi.setDistrict(value(tenant == null ? null : tenant.getPoiDistrict()));
        poi.setAdcode(value(tenant == null ? null : tenant.getPoiAdcode()));
        poi.setLongitude(tenant == null ? null : tenant.getPoiLongitude());
        poi.setLatitude(tenant == null ? null : tenant.getPoiLatitude());
        poi.setTypeCode(value(tenant == null ? null : tenant.getPoiTypeCode()));
        poi.setTypeName(value(tenant == null ? null : tenant.getPoiTypeName()));
        poi.setKeytag("");
        poi.setTel("");
        poi.setBusinessArea(value(tenant == null ? null : tenant.getNearby()));
        return poi;
    }

    private String normalizeTaskMode(String taskMode) {
        return TASK_MODE_WEATHER_ONLY.equalsIgnoreCase(value(taskMode)) ? TASK_MODE_WEATHER_ONLY : TASK_MODE_FULL;
    }

    private String queryForMode(String taskMode, String message) {
        String userMessage = value(message);
        if (TASK_MODE_WEATHER_ONLY.equals(normalizeTaskMode(taskMode))) {
            return firstNotBlank(userMessage, "请查询酒店所在地未来一周天气、温度趋势、是否炎热或降雨");
        }
        return firstNotBlank(userMessage, "请搜索该酒店房价、房型、周边竞品价格、周边热点、近期活动和天气");
    }

    private List<String> requiredTasks(String taskMode) {
        if (TASK_MODE_WEATHER_ONLY.equals(normalizeTaskMode(taskMode))) {
            return List.of("weather");
        }
        return List.of(
            "current_hotel_public_room_prices",
            "nearby_competitor_public_room_prices",
            "nearby_hot_places",
            "local_events",
            "weather"
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseObject(String content) {
        try {
            String json = extractJson(content);
            JsonNode root = objectMapper.readTree(json);
            if (!root.isObject()) {
                return Map.of();
            }
            return objectMapper.convertValue(root, Map.class);
        } catch (Exception e) {
            log.warn("Unable to parse surrounding intel output. content={}", content);
            return Map.of();
        }
    }

    private String extractJson(String content) {
        String value = content == null ? "" : content.trim();
        if (value.startsWith("```")) {
            value = value.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim();
        }
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        return start >= 0 && end > start ? value.substring(start, end + 1) : value;
    }

    private String inferHotelType(HotelPoiCandidate poi) {
        String text = firstNotBlank(poi.getTypeName(), poi.getName());
        if (containsAny(text, "民宿", "客栈", "旅社")) return "精品民宿";
        if (containsAny(text, "度假", "山庄", "温泉")) return "度假酒店";
        if (containsAny(text, "公寓")) return "酒店式公寓";
        if (containsAny(text, "商务", "国际", "广场")) return "商务酒店";
        return "酒店";
    }

    private String inferTags(HotelPoiCandidate poi) {
        List<String> tags = new ArrayList<>();
        String text = firstNotBlank(poi.getName(), poi.getTypeName(), poi.getAddress());
        if (containsAny(text, "民宿", "客栈")) tags.add("在地体验");
        if (containsAny(text, "山", "湖", "江", "湾", "景")) tags.add("景观房");
        if (containsAny(text, "度假", "温泉", "山庄")) tags.add("度假休闲");
        if (containsAny(text, "亲子", "乐园")) tags.add("亲子友好");
        if (containsAny(text, "商务", "国际", "广场", "中心")) tags.add("商务出行");
        if (notBlank(poi.getKeytag())) tags.add(poi.getKeytag());
        if (notBlank(poi.getBusinessArea())) tags.add(poi.getBusinessArea() + "商圈");
        if (tags.isEmpty()) {
            tags.add("地址绑定");
            tags.add("交通便利");
        }
        return String.join("、", tags);
    }

    private String inferAudience(HotelPoiCandidate poi) {
        String type = inferHotelType(poi);
        if ("精品民宿".equals(type)) return "周末度假客、情侣出游、亲子家庭、城市周边短途游客";
        if ("度假酒店".equals(type)) return "亲子家庭、情侣度假、团队团建、中高端休闲游客";
        if ("酒店式公寓".equals(type)) return "长住客、商务差旅、家庭出行、对空间和便利性有要求的客人";
        if ("商务酒店".equals(type)) return "商务差旅客、会议客、城市短暂停留客、周边办事人群";
        return "商务差旅客、亲子家庭、情侣周末游、城市短途游客";
    }

    private String inferNearby(HotelPoiCandidate poi) {
        List<String> parts = new ArrayList<>();
        if (notBlank(poi.getAddress())) parts.add("详细地址：" + poi.getAddress());
        if (notBlank(poi.getDistrict())) parts.add("所在区县：" + poi.getDistrict());
        if (notBlank(poi.getBusinessArea())) parts.add("附近商圈：" + poi.getBusinessArea());
        if (notBlank(poi.getTel())) parts.add("联系电话：" + poi.getTel());
        if (notBlank(poi.getKeytag())) parts.add("地图档次：" + poi.getKeytag());
        if (notBlank(poi.getRating())) parts.add("地图评分：" + poi.getRating());
        return parts.isEmpty() ? "地址绑定，可继续补充附近商圈、交通站点、景区和核心客源位置" : String.join("；", parts);
    }

    private String joinRegion(HotelPoiCandidate poi) {
        List<String> parts = new ArrayList<>();
        if (notBlank(poi.getProvince())) parts.add(poi.getProvince());
        if (notBlank(poi.getCity()) && !parts.contains(poi.getCity())) parts.add(poi.getCity());
        if (notBlank(poi.getDistrict()) && !parts.contains(poi.getDistrict())) parts.add(poi.getDistrict());
        return String.join(" / ", parts);
    }

    private boolean containsAny(String value, String... keywords) {
        String text = value == null ? "" : value;
        for (String keyword : keywords) {
            if (text.contains(keyword)) return true;
        }
        return false;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ignored) {
            return "{}";
        }
    }

    private String firstNotBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (notBlank(value)) return value.trim();
        }
        return "";
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
