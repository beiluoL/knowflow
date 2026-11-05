package com.knowflow.identity.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.knowflow.identity.dto.LoginDTO;
import com.knowflow.identity.dto.RegisterDTO;
import com.knowflow.identity.entity.SysUser;
import com.knowflow.identity.vo.LoginVO;
import com.knowflow.identity.vo.UserStatsVO;
import com.knowflow.identity.dto.UpdateProfileDTO;
import com.knowflow.identity.vo.UserVO;

/** 用户业务服务接口。 */
public interface UserService extends IService<SysUser> {

    LoginVO login(LoginDTO dto);

    LoginVO register(RegisterDTO dto);

    UserVO getCurrentUser(Long userId);

    UserStatsVO getUserStats(Long userId);

    UserVO updateProfile(Long userId, UpdateProfileDTO dto);

    /**
     * 第三方 OAuth 登录：根据 provider + providerUid 查找用户，
     * 不存在则自动创建（密码留空，仅用于社交登录），返回 JWT。
     *
     * @param provider    提供方：github / wechat
     * @param providerUid 提供方返回的用户唯一 ID
     * @param nickname    昵称
     * @param avatar      头像 URL
     * @param email       邮箱（可能为空）
     * @return 登录响应（含 token 与用户信息）
     */
    LoginVO oauthLogin(String provider, String providerUid, String nickname, String avatar, String email);
    /** 记录已实际创建或复活的文档收藏；调用方与本人统计必须同事务提交。 */
    void recordDocumentFavoriteAdded(Long actorUserId);

    /** 记录已实际取消的文档收藏，收藏总数不降至负数。 */
    void recordDocumentFavoriteRemoved(Long actorUserId);

    /** 记录文档业务已认定完成的一次阅读，沿用既有计数语义。 */
    void recordDocumentReadCompleted(Long actorUserId);
}
