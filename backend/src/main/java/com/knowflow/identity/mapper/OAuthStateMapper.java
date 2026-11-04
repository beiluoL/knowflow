package com.knowflow.identity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.identity.entity.OAuthState;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 持久化 OAuth 回调状态，以条件删除完成原子消费。 */
@Mapper
public interface OAuthStateMapper extends BaseMapper<OAuthState> {
  @Insert(
      """
      INSERT INTO identity_oauth_state (state_hash, provider, expires_at)
      VALUES (#{hash}, #{provider}, #{expiry})
      """)
  int insertState(
      @Param("hash") String hash, @Param("provider") String provider, @Param("expiry") long expiry);

  @Delete(
      """
      DELETE FROM identity_oauth_state
      WHERE state_hash = #{hash} AND provider = #{provider} AND expires_at > #{now}
      """)
  int consume(
      @Param("hash") String hash, @Param("provider") String provider, @Param("now") long now);

  @Delete("DELETE FROM identity_oauth_state WHERE expires_at <= #{now}")
  int deleteExpired(@Param("now") long now);
}
