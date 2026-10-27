-- Phase 3H：模拟面试会话（基础模式最小闭环，不依赖 AI）
-- 幂等迁移：表/索引经 information_schema 判断，可重复执行；不触碰任何历史作答数据。
-- 与 db/mysql/schema.sql 的新建库 DDL 保持一致；H2 由 db/h2/schema.sql 直接建表，不执行本文件。
DELIMITER $$
DROP PROCEDURE IF EXISTS migrate_phase3h_mock_interview$$
CREATE PROCEDURE migrate_phase3h_mock_interview()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = 'mock_interview_session') THEN
        CREATE TABLE mock_interview_session (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            user_id BIGINT NOT NULL,
            track VARCHAR(32) NOT NULL COMMENT '方向编码',
            mode VARCHAR(16) NOT NULL DEFAULT 'BASIC' COMMENT 'BASIC 基础模式 / AI 模式预留',
            status VARCHAR(16) NOT NULL DEFAULT 'IN_PROGRESS' COMMENT 'IN_PROGRESS/FINISHED',
            question_count INT NOT NULL,
            questions_json TEXT NOT NULL COMMENT '题目快照（题干/参考要点/追问树）',
            answers_json TEXT COMMENT '作答快照 JSON',
            avg_self_score DECIMAL(4,1) COMMENT '自评均分 1-5',
            duration_sec INT COMMENT '服务端计算的实际用时秒',
            started_at DATETIME,
            finished_at DATETIME,
            create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
            update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            deleted INT DEFAULT 0
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'mock_interview_session' AND index_name = 'idx_mis_user') THEN
        CREATE INDEX idx_mis_user ON mock_interview_session (user_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'mock_interview_session' AND index_name = 'idx_mis_status') THEN
        CREATE INDEX idx_mis_status ON mock_interview_session (status);
    END IF;
END$$
DELIMITER ;
CALL migrate_phase3h_mock_interview();
DROP PROCEDURE IF EXISTS migrate_phase3h_mock_interview;
