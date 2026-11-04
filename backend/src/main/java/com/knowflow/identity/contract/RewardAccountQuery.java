package com.knowflow.identity.contract;

/** 供钱包读取历史 XP 和当前服务端消费权限。 */
public interface RewardAccountQuery {
  AccountSnapshot find(Long userId);

  record AccountSnapshot(int legacyXp, boolean unlimited) {}
}
