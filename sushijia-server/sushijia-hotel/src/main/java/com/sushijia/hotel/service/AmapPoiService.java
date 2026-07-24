package com.sushijia.hotel.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sushijia.common.exception.BizException;
import com.sushijia.common.response.ResultCode;
import com.sushijia.hotel.model.DistrictCandidate;
import com.sushijia.hotel.model.HotelPoiCandidate;
import com.sushijia.hotel.model.WeatherForecast;
import com.sushijia.hotel.model.WeatherNow;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AmapPoiService {

    private static final String AMAP_SEARCH_URL = "https://restapi.amap.com/v3/place/text";
    private static final String AMAP_AROUND_URL = "https://restapi.amap.com/v3/place/around";
    private static final String AMAP_DETAIL_URL = "https://restapi.amap.com/v3/place/detail";
    private static final String AMAP_WEATHER_URL = "https://restapi.amap.com/v3/weather/weatherInfo";
    private static final String HOTEL_TYPE = "100000";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    @Value("${map.amap.key:${AMAP_WEB_KEY:}}")
    private String amapKey;

    public List<HotelPoiCandidate> searchHotel(String keyword, String city) {
        String safeKeyword = safe(keyword);
        if (safeKeyword.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "请输入酒店名称");
        }
        UriComponentsBuilder builder = UriComponentsBuilder
            .fromHttpUrl(AMAP_SEARCH_URL)
            .queryParam("key", requireKey())
            .queryParam("keywords", safeKeyword)
            .queryParam("types", HOTEL_TYPE)
            .queryParam("extensions", "all")
            .queryParam("offset", "12")
            .queryParam("page", "1")
            .queryParam("output", "json");
        if (!safe(city).isBlank()) {
            builder.queryParam("city", safe(city));
            builder.queryParam("citylimit", "true");
        }
        JsonNode root = request(builder
            .build()
            .encode()
            .toUri());

        List<HotelPoiCandidate> result = new ArrayList<>();
        JsonNode pois = root.path("pois");
        if (pois.isArray()) {
            for (JsonNode poi : pois) {
                HotelPoiCandidate candidate = toCandidate(poi);
                if (isHotelPoi(candidate)) {
                    result.add(candidate);
                }
            }
        }
        return result;
    }

    public List<DistrictCandidate> districtChildren(String keyword, int subdistrict) {
        String safeKeyword = safe(keyword);
        if (safeKeyword.isBlank()) safeKeyword = "中国";
        JsonNode root = request(UriComponentsBuilder
            .fromHttpUrl("https://restapi.amap.com/v3/config/district")
            .queryParam("key", requireKey())
            .queryParam("keywords", safeKeyword)
            .queryParam("subdistrict", Math.max(0, Math.min(subdistrict, 2)))
            .queryParam("extensions", "base")
            .queryParam("output", "json")
            .build()
            .encode()
            .toUri());
        JsonNode districts = root.path("districts");
        if (!districts.isArray() || districts.isEmpty()) {
            return List.of();
        }
        JsonNode children = districts.get(0).path("districts");
        if (!children.isArray()) {
            return List.of();
        }
        List<DistrictCandidate> result = new ArrayList<>();
        for (JsonNode item : children) {
            DistrictCandidate row = new DistrictCandidate();
            row.setName(text(item, "name"));
            row.setAdcode(text(item, "adcode"));
            row.setCitycode(text(item, "citycode"));
            row.setLevel(text(item, "level"));
            row.setCenter(text(item, "center"));
            result.add(row);
        }
        return result;
    }

    public HotelPoiCandidate getHotelById(String poiId) {
        String safeId = safe(poiId);
        if (safeId.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "请选择要绑定的酒店");
        }
        JsonNode root = request(UriComponentsBuilder
            .fromHttpUrl(AMAP_DETAIL_URL)
            .queryParam("key", requireKey())
            .queryParam("id", safeId)
            .queryParam("extensions", "all")
            .queryParam("output", "json")
            .build()
            .encode()
            .toUri());
        JsonNode pois = root.path("pois");
        if (!pois.isArray() || pois.isEmpty()) {
            throw new BizException(ResultCode.NOT_FOUND, "没有找到对应酒店");
        }
        HotelPoiCandidate candidate = toCandidate(pois.get(0));
        if (!isHotelPoi(candidate)) {
            throw new BizException(ResultCode.BAD_REQUEST, "只能绑定酒店、民宿、客栈等住宿类地点");
        }
        return candidate;
    }

    public List<HotelPoiCandidate> searchNearbyHotels(BigDecimal longitude,
                                                       BigDecimal latitude,
                                                       String city,
                                                       String excludePoiId,
                                                       int limit) {
        if (longitude == null || latitude == null) {
            return List.of();
        }
        UriComponentsBuilder builder = UriComponentsBuilder
            .fromHttpUrl(AMAP_AROUND_URL)
            .queryParam("key", requireKey())
            .queryParam("location", longitude.toPlainString() + "," + latitude.toPlainString())
            .queryParam("keywords", "酒店|民宿|客栈")
            .queryParam("types", HOTEL_TYPE)
            .queryParam("radius", "5000")
            .queryParam("sortrule", "distance")
            .queryParam("extensions", "all")
            .queryParam("offset", String.valueOf(Math.max(1, Math.min(limit, 20))))
            .queryParam("page", "1")
            .queryParam("output", "json");
        if (!safe(city).isBlank()) {
            builder.queryParam("city", safe(city));
        }

        JsonNode root = request(builder.build().encode().toUri());
        JsonNode pois = root.path("pois");
        if (!pois.isArray()) {
            return List.of();
        }
        List<HotelPoiCandidate> result = new ArrayList<>();
        for (JsonNode poi : pois) {
            HotelPoiCandidate candidate = toCandidate(poi);
            if (!isHotelPoi(candidate)) {
                continue;
            }
            if (!safe(excludePoiId).isBlank() && safe(excludePoiId).equals(candidate.getPoiId())) {
                continue;
            }
            result.add(candidate);
        }
        return result;
    }

    public WeatherNow currentWeather(String cityOrAdcode) {
        String city = safe(cityOrAdcode);
        if (city.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "请先绑定酒店或设置城市");
        }
        JsonNode root = request(UriComponentsBuilder
            .fromHttpUrl(AMAP_WEATHER_URL)
            .queryParam("key", requireKey())
            .queryParam("city", city)
            .queryParam("extensions", "base")
            .queryParam("output", "json")
            .build()
            .encode()
            .toUri());
        JsonNode lives = root.path("lives");
        if (!lives.isArray() || lives.isEmpty()) {
            throw new BizException(ResultCode.NOT_FOUND, "暂无当地天气数据");
        }
        JsonNode live = lives.get(0);
        WeatherNow weather = new WeatherNow();
        weather.setProvince(text(live, "province"));
        weather.setCity(text(live, "city"));
        weather.setAdcode(text(live, "adcode"));
        weather.setWeather(text(live, "weather"));
        weather.setTemperature(text(live, "temperature"));
        weather.setWindDirection(text(live, "winddirection"));
        weather.setWindPower(text(live, "windpower"));
        weather.setHumidity(text(live, "humidity"));
        weather.setReportTime(text(live, "reporttime"));
        return weather;
    }

    public List<WeatherForecast> forecastWeather(String cityOrAdcode) {
        String city = safe(cityOrAdcode);
        if (city.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "请先绑定酒店或设置城市");
        }
        JsonNode root = request(UriComponentsBuilder
            .fromHttpUrl(AMAP_WEATHER_URL)
            .queryParam("key", requireKey())
            .queryParam("city", city)
            .queryParam("extensions", "all")
            .queryParam("output", "json")
            .build()
            .encode()
            .toUri());
        JsonNode forecasts = root.path("forecasts");
        if (!forecasts.isArray() || forecasts.isEmpty()) {
            throw new BizException(ResultCode.NOT_FOUND, "暂无当地天气数据");
        }
        JsonNode casts = forecasts.get(0).path("casts");
        if (!casts.isArray()) {
            return List.of();
        }
        List<WeatherForecast> result = new ArrayList<>();
        for (JsonNode cast : casts) {
            WeatherForecast forecast = new WeatherForecast();
            forecast.setDate(text(cast, "date"));
            forecast.setWeek(text(cast, "week"));
            forecast.setDayWeather(text(cast, "dayweather"));
            forecast.setNightWeather(text(cast, "nightweather"));
            forecast.setDayTemp(text(cast, "daytemp"));
            forecast.setNightTemp(text(cast, "nighttemp"));
            forecast.setDayWind(text(cast, "daywind"));
            forecast.setNightWind(text(cast, "nightwind"));
            forecast.setDayPower(text(cast, "daypower"));
            forecast.setNightPower(text(cast, "nightpower"));
            result.add(forecast);
        }
        return result;
    }

    private JsonNode request(URI uri) {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BizException(ResultCode.BAD_REQUEST, "地图服务暂时不可用");
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (!"1".equals(root.path("status").asText())) {
                String message = root.path("info").asText("地图服务调用失败");
                throw new BizException(ResultCode.BAD_REQUEST, message);
            }
            return root;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ResultCode.BAD_REQUEST, "地图服务调用失败，请稍后重试");
        }
    }

    private HotelPoiCandidate toCandidate(JsonNode poi) {
        HotelPoiCandidate candidate = new HotelPoiCandidate();
        candidate.setProvider("amap");
        candidate.setPoiId(text(poi, "id"));
        candidate.setName(text(poi, "name"));
        candidate.setAddress(text(poi, "address"));
        candidate.setProvince(text(poi, "pname"));
        candidate.setCity(text(poi, "cityname"));
        candidate.setDistrict(text(poi, "adname"));
        candidate.setAdcode(text(poi, "adcode"));
        candidate.setTypeCode(text(poi, "typecode"));
        candidate.setTypeName(text(poi, "type"));
        candidate.setKeytag(text(poi, "keytag"));
        candidate.setTel(text(poi, "tel"));
        candidate.setBusinessArea(text(poi, "business_area"));
        JsonNode bizExt = poi.path("biz_ext");
        candidate.setRating(text(bizExt, "rating"));
        candidate.setLowestPrice(decimal(firstText(bizExt, "lowest_price", "cost")));
        String[] location = text(poi, "location").split(",");
        if (location.length == 2) {
            candidate.setLongitude(decimal(location[0]));
            candidate.setLatitude(decimal(location[1]));
        }
        return candidate;
    }

    public boolean isHotelPoi(HotelPoiCandidate candidate) {
        String typeCode = safe(candidate.getTypeCode());
        String text = safe(candidate.getTypeName()) + " " + safe(candidate.getName());
        return typeCode.startsWith("100") && containsAny(text,
            "住宿", "酒店", "宾馆", "饭店", "旅馆", "旅社", "民宿", "客栈", "公寓", "度假村");
    }

    private boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword)) return true;
        }
        return false;
    }

    private String requireKey() {
        String key = safe(amapKey);
        if (key.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "请先配置 AMAP_WEB_KEY 或 map.amap.key");
        }
        return key;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() || value.isArray() || value.isObject()
            ? ""
            : value.asText("").trim();
    }

    private String firstText(JsonNode node, String... fields) {
        if (node == null || node.isMissingNode() || fields == null) return "";
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isArray() && !value.isEmpty()) {
                String text = value.get(0).asText("").trim();
                if (!text.isBlank()) return text;
            } else if (!value.isMissingNode() && !value.isNull() && !value.isObject()) {
                String text = value.asText("").trim();
                if (!text.isBlank()) return text;
            }
        }
        return "";
    }

    private BigDecimal decimal(String value) {
        try {
            return new BigDecimal(safe(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
