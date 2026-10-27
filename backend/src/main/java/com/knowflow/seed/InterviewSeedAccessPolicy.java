package com.knowflow.seed;

import com.knowflow.entity.SysUser;
import org.springframework.jdbc.core.JdbcTemplate;

/** Promoting an existing learner must not remove their own activated material.
 * Fresh administrator accounts remain excluded from automatic personal seeding.
 */
final class InterviewSeedAccessPolicy {
    private InterviewSeedAccessPolicy() {}
    static boolean allowed(SysUser user, JdbcTemplate jdbc) {
        if ("USER".equalsIgnoreCase(user.getRole())) return true;
        return "ADMIN".equalsIgnoreCase(user.getRole()) &&
                jdbc.queryForObject("SELECT COUNT(*) FROM interview_seed_activation WHERE seed_key=? AND user_id=?",
                        Integer.class, InterviewSeedCatalog.KEY, user.getId()) == 1;
    }
}
