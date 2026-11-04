package com.knowflow.identity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 短期 OAuth 状态，只保存状态与浏览器绑定值的组合哈希。 */
@Data
@TableName("identity_oauth_state")
public class OAuthState {
  @TableId(type = IdType.INPUT)
  private String stateHash;

  private String provider;

  /** 状态截止时间，UTC 毫秒。 */
  private Long expiresAt;
}
