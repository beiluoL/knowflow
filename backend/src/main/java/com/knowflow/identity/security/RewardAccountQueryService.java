package com.knowflow.identity.security;

import com.knowflow.identity.contract.RewardAccountQuery;
import com.knowflow.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 从有效账号读取钱包初始化与权限所需字段。 */
@Service
@RequiredArgsConstructor
public class RewardAccountQueryService implements RewardAccountQuery {
  private final SysUserMapper users;

  @Override
  public AccountSnapshot find(Long userId) {
    var user = userId == null ? null : users.selectById(userId);
    return user == null
        ? new AccountSnapshot(0, false)
        : new AccountSnapshot(
            user.getExp() == null ? 0 : user.getExp(), "ADMIN".equals(user.getRole()));
  }
}
