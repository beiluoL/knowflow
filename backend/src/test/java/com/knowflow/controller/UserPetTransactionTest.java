package com.knowflow.controller;

import com.knowflow.reward.controller.UserPetController;


import com.knowflow.reward.entity.UserPet;
import com.knowflow.reward.mapper.UserPetMapper;
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
@TestPropertySource(properties="knowflow.datasource.h2.url=jdbc:h2:mem:pet_batch1;DB_CLOSE_DELAY=-1;MODE=MySQL")
class UserPetTransactionTest {
    @Autowired UserPetController controller;
    @Autowired JdbcTemplate jdbc;
    @SpyBean UserPetMapper pets;

    @Test
    void failedFirstFeedRollsBackDefaultPetCreation() {
        long owner = 991881L;
        jdbc.update("INSERT INTO sys_user(id, username, password, deleted) VALUES (?, ?, ?, 0)", owner, "pet_atomic_owner", "test");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(owner, "", List.of()));
        doThrow(new IllegalStateException("injected update failure")).when(pets).updateById(any(UserPet.class));
        try {
            assertThrows(RuntimeException.class, () -> controller.feed(null));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM user_pet WHERE user_id = ?", Integer.class, owner));
        } finally {
            reset(pets);
            SecurityContextHolder.clearContext();
        }
    }
    @Autowired com.knowflow.reward.service.UserPetService service;

    @Test
    void concurrentFirstFocusAwardsCreateOnePetWithoutLosingMinutes() throws Exception {
        long owner = 991884L;
        jdbc.update("INSERT INTO sys_user(id, username, password, deleted) VALUES (?, ?, ?, 0)", owner, "pet_concurrent_owner", "test");
        var executor = new java.util.concurrent.ForkJoinPool(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            java.util.concurrent.Callable<Void> award = () -> {
                start.await();
                service.grantFocus(owner, 10, 1);
                return null;
            };
            var first = executor.submit(award);
            var second = executor.submit(award);
            start.countDown();
            first.get(10, java.util.concurrent.TimeUnit.SECONDS);
            second.get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM user_pet WHERE user_id = ?", Integer.class, owner));
            assertEquals(20, jdbc.queryForObject("SELECT total_focus_minutes FROM user_pet WHERE user_id = ?", Integer.class, owner));
            assertEquals(2, jdbc.queryForObject("SELECT total_pomodoros FROM user_pet WHERE user_id = ?", Integer.class, owner));
        } finally { executor.shutdownNow(); }
    }

}
