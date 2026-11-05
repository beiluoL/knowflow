package com.knowflow.identity.service;

import com.knowflow.identity.entity.SysUser;
import com.knowflow.identity.mapper.SysUserMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 验证奖励成员投影、所有者锁及历史经验值和签到更新。
 */
class RewardMemberAccessServiceTest {
    @Test
    void projectsOnlyRewardProfileFieldsAndDoesNotMutatePersistenceRow() {
        var mapper = mock(SysUserMapper.class);
        var row = new SysUser();
        row.setId(42L);
        row.setExp(10);
        row.setPassword("test-only-secret");
        row.setNickname("member");
        when(mapper.selectById(42L)).thenReturn(row);
        var result = new RewardMemberAccessService(mapper).find(42L);
        assertEquals(42L, result.getId());
        assertEquals("member", result.getNickname());
        result.setExp(20);
        assertEquals(10, row.getExp());
        assertFalse(List.of(result.getClass().getDeclaredFields()).stream()
                .anyMatch(field -> field.getName().equals("password") || field.getName().equals("role")));
    }

    @Test
    void preservesMissingMemberAndOwnerLockSemantics() {
        var mapper = mock(SysUserMapper.class);
        var service = new RewardMemberAccessService(mapper);
        assertNull(service.find(42L));
        when(mapper.lockActiveUserId(42L)).thenReturn(42L);
        assertEquals(42L, service.lockActiveUserId(42L));
        verify(mapper).lockActiveUserId(42L);
        service.syncStreak(42L, 2);
        verify(mapper, never()).updateById(any(SysUser.class));
    }

    @Test
    void preservesLegacyAbsoluteExperienceAndStreakUpdates() {
        var mapper = mock(SysUserMapper.class);
        var service = new RewardMemberAccessService(mapper);
        service.setLegacyExperience(42L, 18);
        var update = ArgumentCaptor.forClass(SysUser.class);
        verify(mapper).updateById(update.capture());
        assertEquals(42L, update.getValue().getId());
        assertEquals(18, update.getValue().getExp());
        assertNull(update.getValue().getPassword());
        var row = new SysUser(); row.setId(42L); row.setExp(18);
        when(mapper.selectById(42L)).thenReturn(row);
        service.syncStreak(42L, 3);
        assertEquals(3, row.getStreakDays());
        assertEquals(18, row.getExp());
        verify(mapper).updateById(row);
    }
}
