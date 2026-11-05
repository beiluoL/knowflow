package com.knowflow.support;

import org.springframework.jdbc.core.JdbcTemplate;

/** Real database actors for HTTP tests that exercise the active-account authentication filter. */
public final class ActiveIdentityFixtures {
    private ActiveIdentityFixtures() {}

    /**
     * Inserts a missing test actor without updating or reviving an existing identity.
     * A conflicting, deleted, or differently privileged actor fails setup instead of masking it.
     */
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
