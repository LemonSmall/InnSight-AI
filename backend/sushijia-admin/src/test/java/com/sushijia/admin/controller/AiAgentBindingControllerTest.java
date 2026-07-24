package com.sushijia.admin.controller;

import com.sushijia.repository.entity.AiAgentBinding;
import com.sushijia.repository.mapper.AiAgentBindingMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiAgentBindingControllerTest {

    @Test
    void listOnlyReturnsKeyConfiguredFlag() {
        AiAgentBindingMapper mapper = mock(AiAgentBindingMapper.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AiAgentBinding binding = binding(1L, "stored-secret");
        when(mapper.selectList(any())).thenReturn(List.of(binding));
        when(mapper.findEnabledByModuleKey(anyString())).thenReturn(null);
        when(jdbc.queryForObject(anyString(), any(Class.class), any(), any())).thenReturn(1);

        AiAgentBindingController controller = new AiAgentBindingController(mapper, jdbc, mock(StringRedisTemplate.class));
        var response = controller.list();

        assertEquals(1, response.getData().size());
        assertTrue(response.getData().get(0).apiKeyConfigured());
    }

    @Test
    void blankKeyUpdateKeepsStoredSecret() {
        AiAgentBindingMapper mapper = mock(AiAgentBindingMapper.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AiAgentBinding stored = binding(7L, "stored-secret");
        when(mapper.selectById(7L)).thenReturn(stored);
        when(mapper.selectCount(any())).thenReturn(0L);
        when(mapper.selectList(any())).thenReturn(List.of(stored));
        when(mapper.findEnabledByModuleKey("xhs")).thenReturn(stored);
        when(jdbc.queryForObject(anyString(), any(Class.class), any(), any())).thenReturn(1);

        AiAgentBinding body = binding(7L, "");
        body.setBotApiKey("");
        AiAgentBindingController controller = new AiAgentBindingController(mapper, jdbc, mock(StringRedisTemplate.class));
        controller.update(7L, body);

        ArgumentCaptor<AiAgentBinding> captor = ArgumentCaptor.forClass(AiAgentBinding.class);
        verify(mapper).updateById(captor.capture());
        assertEquals("stored-secret", captor.getValue().getApiKey());
    }

    private AiAgentBinding binding(Long id, String apiKey) {
        AiAgentBinding binding = new AiAgentBinding();
        binding.setId(id);
        binding.setModuleKey("xhs");
        binding.setProvider("dify");
        binding.setAppType("chatflow");
        binding.setAppName("宿识家小红书创作官");
        binding.setApiKey(apiKey);
        binding.setBotApiKey(apiKey);
        binding.setEndpoint("");
        binding.setInputSchema("{}");
        binding.setEnabled(1);
        return binding;
    }
}
