package com.knowflow.identity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.identity.contract.MemberDisplayQuery;
import com.knowflow.identity.entity.SysUser;
import com.knowflow.identity.mapper.SysUserMapper;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 身份模块维护的展示投影，MyBatis 保留账号逻辑删除过滤。 */
@Service
@RequiredArgsConstructor
public class MemberDisplayQueryService implements MemberDisplayQuery {
    private final SysUserMapper userMapper;

    @Override
    public MemberDisplay find(Long userId) {
        return project(userMapper.selectById(userId));
    }

    @Override
    public List<MemberDisplay> findAll(Collection<Long> userIds) {
        if (userIds.isEmpty()) return List.of();
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .in(SysUser::getId, userIds)).stream().map(this::project).toList();
    }

    private MemberDisplay project(SysUser user) {
        return user == null ? null : new MemberDisplay(user.getId(), user.getUsername(),
                user.getNickname(), user.getAvatar());
    }
}
