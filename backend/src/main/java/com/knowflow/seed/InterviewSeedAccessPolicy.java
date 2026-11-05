package com.knowflow.seed;

import com.knowflow.identity.entity.SysUser;
import org.springframework.jdbc.core.JdbcTemplate;

/** 已有学习者晋升管理员时保留其已激活资料；新管理员账号不自动初始化个人学习内容。 */
final class InterviewSeedAccessPolicy {
    private InterviewSeedAccessPolicy() {}
    static boolean allowed(SysUser user, JdbcTemplate jdbc) {
        if ("USER".equalsIgnoreCase(user.getRole())) return true;
        return "ADMIN".equalsIgnoreCase(user.getRole()) &&
                jdbc.queryForObject("SELECT COUNT(*) FROM interview_seed_activation WHERE seed_key=? AND user_id=?",
                        Integer.class, InterviewSeedCatalog.KEY, user.getId()) == 1;
    }
}
