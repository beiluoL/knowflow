package com.knowflow.controller;

import com.knowflow.entity.UserPet;
import com.knowflow.mapper.UserPetMapper;
import com.knowflow.support.FakeAiConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Import(FakeAiConfiguration.class)
@TestPropertySource(properties="knowflow.datasource.h2.url=jdbc:h2:mem:config_batch1;DB_CLOSE_DELAY=-1;MODE=MySQL")
class AiConfigTransactionTest {
    @Autowired AiConfigController controller;
    @Autowired JdbcTemplate jdbc;
    @SpyBean com.knowflow.mapper.UserAiConfigMapper configs;

    @Test
    void failedActivationRetainsPreviousActiveConfig() {
        long owner = 991882L;
        jdbc.update("INSERT INTO sys_user(id, username, password, deleted) VALUES (?, ?, ?, 0)", owner, "config_atomic_owner", "test");
        com.knowflow.entity.UserAiConfig first = config(owner, 1);
        com.knowflow.entity.UserAiConfig second = config(owner, 0);
        configs.insert(first);
        configs.insert(second);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(owner, "", List.of()));
        java.util.concurrent.atomic.AtomicInteger writes = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(invocation -> {
            if (writes.incrementAndGet() == 2) throw new IllegalStateException("activation failure");
            return jdbc.update("UPDATE sys_user_ai_config SET is_active = 0 WHERE user_id = ? AND id <> ?", owner, second.getId());
        }).when(configs).update(isNull(), any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        try {
            assertThrows(RuntimeException.class, () -> controller.activate(second.getId(), null));
            assertEquals(1, configs.selectById(first.getId()).getIsActive());
        } finally { reset(configs); SecurityContextHolder.clearContext(); }
    }

    private com.knowflow.entity.UserAiConfig config(long owner, int active) {
        var config = new com.knowflow.entity.UserAiConfig();
        config.setUserId(owner);
        config.setProvider("openai");
        config.setApiKey("test");
        config.setIsActive(active);
        return config;
    }
}
