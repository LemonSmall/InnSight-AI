package com.sushijia.admin.controller;

import com.sushijia.repository.entity.SystemSetting;
import com.sushijia.repository.mapper.SystemSettingMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SettingsControllerTest {

    @Test
    void sensitiveValuesAreMasked() {
        SystemSettingMapper mapper = mock(SystemSettingMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of(
            setting(1L, "modelscope_api_key", "private-token"),
            setting(2L, "ai_base_url", "https://api.dify.ai/v1")
        ));

        Map<String, String> settings = new SettingsController(mapper, mock(StringRedisTemplate.class)).getSettings().getData();

        assertEquals("********", settings.get("modelscope_api_key"));
        assertEquals("true", settings.get("modelscope_api_key_configured"));
        assertEquals("https://api.dify.ai/v1", settings.get("ai_base_url"));
        assertFalse(settings.containsValue("private-token"));
    }

    @Test
    void maskedOrBlankSecretDoesNotOverwriteStoredValue() {
        SystemSettingMapper mapper = mock(SystemSettingMapper.class);
        SystemSetting stored = setting(1L, "ai_api_key", "stored-secret");
        when(mapper.selectOne(any())).thenReturn(stored);

        SettingsController controller = new SettingsController(mapper, mock(StringRedisTemplate.class));
        controller.saveSettings(Map.of("ai_api_key", "********"));

        verify(mapper, never()).updateById(any(SystemSetting.class));
        assertEquals("stored-secret", stored.getSettingValue());
    }

    @Test
    void newSecretIsPersistedWithoutReturningIt() {
        SystemSettingMapper mapper = mock(SystemSettingMapper.class);
        SystemSetting stored = setting(1L, "ai_api_key", "old-secret");
        when(mapper.selectOne(any())).thenReturn(stored);

        new SettingsController(mapper, mock(StringRedisTemplate.class)).saveSettings(Map.of("ai_api_key", "new-secret"));

        ArgumentCaptor<SystemSetting> captor = ArgumentCaptor.forClass(SystemSetting.class);
        verify(mapper).updateById(captor.capture());
        assertEquals("new-secret", captor.getValue().getSettingValue());
    }

    private SystemSetting setting(Long id, String key, String value) {
        SystemSetting setting = new SystemSetting();
        setting.setId(id);
        setting.setSettingKey(key);
        setting.setSettingValue(value);
        return setting;
    }
}
