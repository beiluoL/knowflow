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
@TestPropertySource(properties="knowflow.datasource.h2.url=jdbc:h2:mem:suggestion_batch1;DB_CLOSE_DELAY=-1;MODE=MySQL")
class AiSuggestionTransactionTest {
    @Autowired AiLearningSuggestionController controller;
    @Autowired JdbcTemplate jdbc;
    @SpyBean com.knowflow.mapper.AiLearningSuggestionMapper suggestions;

    @Test
    void failedReplacementKeepsPreviousCachedSuggestion() {
        long owner = 991883L;
        jdbc.update("INSERT INTO sys_user(id, username, password, deleted) VALUES (?, ?, ?, 0)", owner, "suggestion_atomic_owner", "test");
        var cached = new com.knowflow.entity.AiLearningSuggestion();
        cached.setUserId(owner);
        cached.setPeriod("week");
        cached.setSuggestions("[]");
        suggestions.insert(cached);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(owner, "", List.of()));
        doThrow(new IllegalStateException("insert failure")).when(suggestions).insert(any(com.knowflow.entity.AiLearningSuggestion.class));
        try {
            assertThrows(RuntimeException.class, () -> controller.regenerate("week", null));
            assertNotNull(suggestions.selectById(cached.getId()));
        } finally { reset(suggestions); SecurityContextHolder.clearContext(); }
    }
}
