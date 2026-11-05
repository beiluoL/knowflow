package com.knowflow.identity.contract;

import java.util.List;
import lombok.Data;

/** 奖励模块使用的身份模块资料及历史统计操作。 */
public interface RewardMemberAccess {
    /** 读取未删除成员，不暴露凭据或角色。 */
    Member find(Long userId);
    /** 沿用历史排行的经验值排序，限制返回 1–100 位成员。 */
    List<Member> ranking(int limit);
    /** 在调用方事务内锁定已有的有效用户行。 */
    Long lockActiveUserId(Long userId);
    /** 在调用方事务内保留历史的经验值绝对赋值行为。 */
    void setLegacyExperience(Long userId, int experience);
    /** 成员仍存在时同步连续签到天数。 */
    void syncStreak(Long userId, int days);

    @Data
    class Member {
        private Long id;
        private String username;
        private String nickname;
        private String avatar;
        private Integer level;
        private Integer exp;
        private Integer streakDays;
        private Integer readDocsCount;
    }
}
