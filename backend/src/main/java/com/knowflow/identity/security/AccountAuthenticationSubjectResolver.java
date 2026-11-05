package com.knowflow.identity.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.identity.entity.SysUser;
import com.knowflow.framework.security.AuthenticationSubjectResolver;
import com.knowflow.identity.mapper.SysUserMapper;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 通过账号表读取可认证主体，逻辑删除由 ORM 过滤。 */
@Component
@RequiredArgsConstructor
public class AccountAuthenticationSubjectResolver implements AuthenticationSubjectResolver {
  private final SysUserMapper users;

  @Override
  public Optional<Subject> resolve(long userId) {
    var account =
        users.selectOne(
            new LambdaQueryWrapper<SysUser>()
                .select(SysUser::getId, SysUser::getRole)
                .eq(SysUser::getId, userId));
    return Optional.ofNullable(account)
        .filter(user -> StringUtils.hasText(user.getRole()))
        .map(user -> new Subject(user.getId(), List.of("ROLE_" + user.getRole())));
  }
}
