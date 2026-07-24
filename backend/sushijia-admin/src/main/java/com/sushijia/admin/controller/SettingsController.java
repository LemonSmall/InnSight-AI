package com.sushijia.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sushijia.common.response.R;
import com.sushijia.repository.entity.SystemSetting;
import com.sushijia.repository.mapper.SystemSettingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class SettingsController {

    private static final String MASK = "********";
    private static final List<String> SENSITIVE_KEYS = List.of(
        "ai_api_key", "modelscope_api_key", "sms_access_key", "sms_secret_key"
    );

    private final SystemSettingMapper settingMapper;
    private final StringRedisTemplate redisTemplate;

    @GetMapping("/settings")
    public R<Map<String, String>> getSettings() {
        List<SystemSetting> list = settingMapper.selectList(new LambdaQueryWrapper<>());
        Map<String, String> map = new LinkedHashMap<>();
        for (SystemSetting s : list) {
            String key = s.getSettingKey();
            String value = s.getSettingValue();
            if (SENSITIVE_KEYS.contains(key)) {
                boolean configured = value != null && !value.isBlank();
                map.put(key, configured ? MASK : "");
                map.put(key + "_configured", String.valueOf(configured));
            } else {
                map.put(key, value);
            }
        }
        return R.ok(map);
    }

    @PutMapping("/settings")
    public R<String> saveSettings(@RequestBody Map<String, String> body) {
        for (Map.Entry<String, String> entry : body.entrySet()) {
            if (entry.getKey().endsWith("_configured")) {
                continue;
            }
            SystemSetting exist = settingMapper.selectOne(
                new LambdaQueryWrapper<SystemSetting>()
                    .eq(SystemSetting::getSettingKey, entry.getKey()));
            if (exist != null) {
                if (SENSITIVE_KEYS.contains(entry.getKey())
                    && (entry.getValue() == null || entry.getValue().isBlank() || MASK.equals(entry.getValue()))) {
                    continue;
                }
                exist.setSettingValue(entry.getValue());
                settingMapper.updateById(exist);
            } else {
                SystemSetting ns = new SystemSetting();
                ns.setSettingKey(entry.getKey());
                ns.setSettingValue(entry.getValue());
                settingMapper.insert(ns);
            }
        }
        clearSettingsCache();
        return R.ok("保存成功");
    }

    private void clearSettingsCache() {
        try {
            Set<String> keys = redisTemplate.keys("sushijia:settings:*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception ignored) {
            // Redis is an acceleration layer only.
        }
    }
}
