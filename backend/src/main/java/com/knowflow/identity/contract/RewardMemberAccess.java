package com.knowflow.identity.contract;

import java.util.List;
import lombok.Data;

/** Identity-owned profile and legacy statistics operations used by Reward. */
public interface RewardMemberAccess {
    /** Read a non-deleted member; never exposes credentials or roles. */
    Member find(Long userId);
    /** Same experience ordering as the legacy ranking, limited to 1–100 members. */
    List<Member> ranking(int limit);
    /** Acquire the existing active-user row lock within the caller transaction. */
    Long lockActiveUserId(Long userId);
    /** Preserve the legacy absolute XP assignment within the caller transaction. */
    void setLegacyExperience(Long userId, int experience);
    /** Synchronize the check-in streak if the member still exists. */
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
