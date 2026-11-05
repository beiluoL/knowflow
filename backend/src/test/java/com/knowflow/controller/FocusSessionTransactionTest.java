package com.knowflow.controller;

import com.knowflow.productivity.controller.FocusSessionController;


import com.knowflow.productivity.dto.FocusSessionEndDTO;
import com.knowflow.productivity.entity.FocusSession;
import com.knowflow.productivity.mapper.FocusSessionMapper;
import com.knowflow.reward.mapper.UserPetMapper;
import com.knowflow.support.FakeAiConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 验证专注会话变更和累计统计的事务边界。
 */
@SpringBootTest
@Import(FakeAiConfiguration.class)
@TestPropertySource(properties="knowflow.datasource.h2.url=jdbc:h2:mem:focus_batch1;DB_CLOSE_DELAY=-1;MODE=MySQL")
class FocusSessionTransactionTest {
    @Autowired FocusSessionController controller;
    @Autowired FocusSessionMapper sessions;
    @SpyBean UserPetMapper pets;

    @Autowired com.knowflow.identity.mapper.SysUserMapper users;

    @org.junit.jupiter.api.BeforeEach
    void seedOwners() {
        for (long owner : new long[]{991871L, 991872L, 991873L}) {
            if (users.selectById(owner) != null) continue;
            var user = new com.knowflow.identity.entity.SysUser();
            user.setId(owner);
            user.setUsername("focus-owner-" + owner);
            user.setEmail("focus-owner-" + owner + "@test.invalid");
            user.setPassword("");
            user.setRole("USER");
            users.insert(user);
        }
    }

    @Test
    void petWriteFailureRollsBackSessionEnd() {
        long owner = 991871L;
        FocusSession session = new FocusSession();
        session.setUserId(owner);
        session.setStartTime(LocalDateTime.now());
        session.setDurationMin(0);
        sessions.insert(session);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(owner, "", List.of()));
        doThrow(new IllegalStateException("injected pet persistence failure")).when(pets).insert(any(com.knowflow.reward.entity.UserPet.class));
        FocusSessionEndDTO request = new FocusSessionEndDTO();
        request.setDurationMin(25);
        try {
            assertThrows(RuntimeException.class, () -> controller.end(session.getId(), request));
            assertNull(sessions.selectById(session.getId()).getEndTime());
            assertEquals(0, sessions.selectById(session.getId()).getDurationMin());
        } finally {
            SecurityContextHolder.clearContext();
            reset(pets);
        }
    }

    @Test
    void repeatedEndDoesNotGrantTwice() {
        long owner = 991872L;
        FocusSession session = new FocusSession();
        session.setUserId(owner);
        session.setStartTime(LocalDateTime.now());
        session.setDurationMin(0);
        sessions.insert(session);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(owner, "", List.of()));
        FocusSessionEndDTO request = new FocusSessionEndDTO();
        request.setDurationMin(25);
        try {
            controller.end(session.getId(), request);
            controller.end(session.getId(), request);
            var pet = pets.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.knowflow.reward.entity.UserPet>().eq(com.knowflow.reward.entity.UserPet::getUserId, owner));
            assertEquals(25, pet.getTotalFocusMinutes());
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test
    void simultaneousEndRequestsGrantOnce() throws Exception {
        long owner = 991873L;
        FocusSession session = new FocusSession();
        session.setUserId(owner);
        session.setStartTime(LocalDateTime.now());
        session.setDurationMin(0);
        sessions.insert(session);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(4);
        var barrier = new java.util.concurrent.CyclicBarrier(4);
        var jobs = new java.util.ArrayList<java.util.concurrent.Future<?>>();
        try {
            for (int index = 0; index < 4; index++) jobs.add(pool.submit(() -> {
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(owner, "", List.of()));
                try {
                    barrier.await(10, java.util.concurrent.TimeUnit.SECONDS);
                    FocusSessionEndDTO request = new FocusSessionEndDTO(); request.setDurationMin(25);
                    controller.end(session.getId(), request);
                    return null;
                } finally { SecurityContextHolder.clearContext(); }
            }));
            for (var job : jobs) job.get(30, java.util.concurrent.TimeUnit.SECONDS);
            var pet = pets.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.knowflow.reward.entity.UserPet>().eq(com.knowflow.reward.entity.UserPet::getUserId, owner));
            assertEquals(25, pet.getTotalFocusMinutes());
        } finally { pool.shutdownNow(); }
    }

    @Test
    void otherOwnerCannotEndSessionOrGrantReward() {
        FocusSession session = new FocusSession();
        session.setUserId(991872L); session.setStartTime(LocalDateTime.now()); session.setDurationMin(0);
        sessions.insert(session);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(991871L, "", List.of()));
        try {
            FocusSessionEndDTO request = new FocusSessionEndDTO(); request.setDurationMin(25);
            var failure = assertThrows(com.knowflow.exception.BusinessException.class, () -> controller.end(session.getId(), request));
            assertEquals(403, failure.getCode());
            assertNull(sessions.selectById(session.getId()).getEndTime());
        } finally { SecurityContextHolder.clearContext(); }
    }
}
