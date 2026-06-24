package com.sushijia.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sushijia.common.response.R;
import com.sushijia.repository.entity.SystemSetting;
import com.sushijia.repository.mapper.SystemSettingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理后台 - 系统配置（AI / 短信等）
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class SettingsController {

    private final SystemSettingMapper settingMapper;

    @GetMapping("/settings")
    public R<Map<String, String>> getSettings() {
        List<SystemSetting> list = settingMapper.selectList(new LambdaQueryWrapper<>());
        Map<String, String> map = new LinkedHashMap<>();
        for (SystemSetting s : list) {
            map.put(s.getSettingKey(), s.getSettingValue());
        }
        return R.ok(map);
    }

    @PutMapping("/settings")
    public R<String> saveSettings(@RequestBody Map<String, String> body) {
        for (Map.Entry<String, String> entry : body.entrySet()) {
            SystemSetting exist = settingMapper.selectOne(
                new LambdaQueryWrapper<SystemSetting>()
                    .eq(SystemSetting::getSettingKey, entry.getKey()));
            if (exist != null) {
                exist.setSettingValue(entry.getValue());
                settingMapper.updateById(exist);
            } else {
                SystemSetting ns = new SystemSetting();
                ns.setSettingKey(entry.getKey());
                ns.setSettingValue(entry.getValue());
                settingMapper.insert(ns);
            }
        }
        return R.ok("保存成功");
    }
}
