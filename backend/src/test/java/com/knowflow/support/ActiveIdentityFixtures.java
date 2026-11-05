package com.knowflow.support;

import org.springframework.jdbc.core.JdbcTemplate;

/** 为经过有效账号认证过滤器的 HTTP 测试提供真实数据库用户。 */
public final class ActiveIdentityFixtures {
    private ActiveIdentityFixtures() {}

    /** 仅在测试用户不存在时插入，不更新或恢复已有身份；冲突、已删除或权限不同的用户使初始化失败，避免掩盖问题。 */
    public static synchronized void ensureActive(
            JdbcTemplate sql, long userId, String username, String role) {
        sql.update("INSERT INTO sys_user (id, username, nickname, role, deleted) "
                        + "SELECT ?, ?, ?, ?, 0 WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE id=?)",
                userId, username, username, role, userId);
        Integer matches = sql.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE id=? AND username=? AND role=? AND deleted=0",
                Integer.class, userId, username, role);
        if (!Integer.valueOf(1).equals(matches)) {
            throw new IllegalStateException("Test identity conflicts with its active database actor: " + userId);
        }
    }
}
