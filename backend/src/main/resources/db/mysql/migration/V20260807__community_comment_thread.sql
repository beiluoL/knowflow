-- =============================================================
-- KnowFlow 增量迁移脚本
-- 版本：V20260807  主题：社区评论功能增强（F-06）
-- 适用：已部署的 MySQL 8 生产库（新库直接执行 db/mysql/schema.sql 即可，无需本脚本）
-- 内容：
--   1. community_comment 增加 parent_id / reply_to_user_id / like_count / reply_count
--   2. 新增 community_comment_like 评论点赞关系表
--   3. 补建索引
--   4. 回填历史数据的 comment_count（可选，见文末）
-- 说明：MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，重复执行会报 1060，属预期。
--      如需幂等，请先用 SHOW COLUMNS FROM community_comment LIKE 'parent_id'; 检查。
-- =============================================================

-- 1. 评论表增加嵌套回复与计数字段
ALTER TABLE community_comment
    ADD COLUMN parent_id BIGINT DEFAULT 0 COMMENT '0=顶级评论，非0=回复的顶级评论ID' AFTER user_id,
    ADD COLUMN reply_to_user_id BIGINT DEFAULT 0 COMMENT '被回复用户ID，0=直接回复顶级评论' AFTER parent_id,
    ADD COLUMN like_count INT DEFAULT 0 COMMENT '点赞数' AFTER content,
    ADD COLUMN reply_count INT DEFAULT 0 COMMENT '回复数，仅 parent_id=0 的顶级评论维护' AFTER like_count;

-- 2. 评论点赞关系表
CREATE TABLE IF NOT EXISTS community_comment_like (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    comment_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    CONSTRAINT uk_comment_like UNIQUE (comment_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. 索引（逻辑外键列必须建索引，阿里规约）
CREATE INDEX idx_comment_parent    ON community_comment (parent_id);
CREATE INDEX idx_comment_like_user ON community_comment_like (user_id);

-- 4. 历史数据校准：把帖子的 comment_count 与评论表实际条数对齐
UPDATE community_post p
SET p.comment_count = (
    SELECT COUNT(*) FROM community_comment c
    WHERE c.post_id = p.id AND c.deleted = 0
)
WHERE p.deleted = 0;
