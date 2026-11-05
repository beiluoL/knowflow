package com.knowflow.identity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.identity.contract.RewardMemberAccess;
import com.knowflow.identity.entity.SysUser;
import com.knowflow.identity.mapper.SysUserMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

/** 用户持久化与行锁保留在身份模块内，并加入调用方现有事务。 */
@Service
@RequiredArgsConstructor
public class RewardMemberAccessService implements RewardMemberAccess {
    private static final int MIN_RANK_LIMIT = 1;
    private static final int MAX_RANK_LIMIT = 100;

    private final SysUserMapper users;

    /** {@inheritDoc} */
    @Override
    public Member find(Long userId) {
        SysUser user = users.selectById(userId);
        return user == null ? null : project(user);
    }

    /** {@inheritDoc} */
    @Override
    public List<Member> ranking(int limit) {
        int size = Math.min(Math.max(limit, MIN_RANK_LIMIT), MAX_RANK_LIMIT);
        return users.selectList(new LambdaQueryWrapper<SysUser>()
                .orderByDesc(SysUser::getExp).last("LIMIT " + size)).stream().map(this::project).toList();
    }

    /** {@inheritDoc} */
    @Override
    public Long lockActiveUserId(Long userId) {
        return users.lockActiveUserId(userId);
    }

    /** {@inheritDoc} */
    @Override
    public void setLegacyExperience(Long userId, int experience) {
        SysUser update = new SysUser();
        update.setId(userId);
        update.setExp(experience);
        users.updateById(update);
    }

    /** {@inheritDoc} */
    @Override
    public void syncStreak(Long userId, int days) {
        SysUser user = users.selectById(userId);
        if (user != null) {
            user.setStreakDays(days);
            users.updateById(user);
        }
    }

    private Member project(SysUser user) {
        Member member = new Member();
        BeanUtils.copyProperties(user, member);
        return member;
    }
}
