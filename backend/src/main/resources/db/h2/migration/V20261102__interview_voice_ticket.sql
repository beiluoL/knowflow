-- 一次性语音连接票据：只存SHA256，不存JWT或原始音频。
CREATE TABLE IF NOT EXISTS interview_voice_ticket (
 id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '连接票据身份',
 user_id BIGINT NOT NULL COMMENT '认证用户逻辑关联',
 session_id BIGINT NOT NULL COMMENT '原V2面试会话逻辑关联',
 ticket_hash CHAR(64) NOT NULL COMMENT '一次性票据SHA256',
 origin VARCHAR(255) NOT NULL COMMENT '精确绑定允许来源',
 status VARCHAR(12) NOT NULL COMMENT 'ISSUED CONNECTED CLOSED EXPIRED',
 expires_at TIMESTAMP NOT NULL COMMENT '票据或活动连接截止',
 deleted INT NOT NULL DEFAULT 0 COMMENT '关闭或过期标识',
 create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
 CONSTRAINT uk_ivoice_ticket UNIQUE(ticket_hash),
 INDEX idx_ivoice_owner(user_id,expires_at),
 INDEX idx_ivoice_session(session_id)
) COMMENT='实时语音短期连接凭证和并发配额';
