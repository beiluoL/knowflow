-- OAuth state 与浏览器绑定值仅持久化组合哈希。
CREATE TABLE IF NOT EXISTS identity_oauth_state (
    state_hash CHAR(64) PRIMARY KEY COMMENT '状态和浏览器随机值的 SHA-256',
    provider VARCHAR(16) NOT NULL COMMENT 'OAuth 供应商',
    expires_at BIGINT NOT NULL COMMENT '过期时间，UTC 毫秒',
    INDEX idx_identity_oauth_state_expiry (expires_at)
) COMMENT='OAuth 一次性回调状态';
