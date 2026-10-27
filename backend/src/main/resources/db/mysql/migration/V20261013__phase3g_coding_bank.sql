-- Phase 3G：统一题库分类 + 算法题（CODE）扩展
-- 幂等迁移：全部 DDL 经 information_schema 判断，可重复执行；不删除/不修改任何历史作答数据。
-- 与 db/mysql/schema.sql 的新建库 DDL 保持一致；H2 由 db/h2/schema.sql 直接建表，不执行本文件。
DELIMITER $$
DROP PROCEDURE IF EXISTS migrate_phase3g_coding_bank$$
CREATE PROCEDURE migrate_phase3g_coding_bank()
BEGIN
    -- quiz_question 新增统一题库列
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'quiz_question' AND column_name = 'canonical_key') THEN
        ALTER TABLE quiz_question ADD COLUMN canonical_key VARCHAR(128) COMMENT '稳定语义键';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'quiz_question' AND column_name = 'topic_code') THEN
        ALTER TABLE quiz_question ADD COLUMN topic_code VARCHAR(64) COMMENT '显式知识点编码';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'quiz_question' AND column_name = 'review_status') THEN
        ALTER TABLE quiz_question ADD COLUMN review_status VARCHAR(24) COMMENT 'PENDING_REVIEW/APPROVED/REJECTED';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'quiz_question' AND column_name = 'version') THEN
        ALTER TABLE quiz_question ADD COLUMN version INT DEFAULT 1 COMMENT '题目版本号';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'quiz_question' AND column_name = 'creator_id') THEN
        ALTER TABLE quiz_question ADD COLUMN creator_id BIGINT COMMENT '创建人';
    END IF;

    CREATE TABLE IF NOT EXISTS question_topic (
        id BIGINT AUTO_INCREMENT PRIMARY KEY,
        code VARCHAR(64) NOT NULL COMMENT '稳定编码',
        name VARCHAR(100) NOT NULL,
        parent_code VARCHAR(64),
        level INT NOT NULL DEFAULT 1,
        sort_order INT DEFAULT 0,
        status INT DEFAULT 1,
        create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        deleted INT DEFAULT 0
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

    CREATE TABLE IF NOT EXISTS code_problem (
        id BIGINT AUTO_INCREMENT PRIMARY KEY,
        question_id BIGINT NOT NULL,
        supported_languages VARCHAR(200) DEFAULT 'java',
        function_signature LONGTEXT,
        starter_code LONGTEXT,
        constraints LONGTEXT,
        examples LONGTEXT,
        time_limit_ms INT DEFAULT 1000,
        memory_limit_mb INT DEFAULT 256,
        judge_mode VARCHAR(24) DEFAULT 'FUNCTION',
        checker VARCHAR(24) DEFAULT 'EXACT',
        tolerance_value DOUBLE DEFAULT 0,
        test_case_version VARCHAR(24) DEFAULT 'v1',
        reference_solution LONGTEXT,
        solution_explanation LONGTEXT,
        complexity_analysis LONGTEXT,
        follow_ups LONGTEXT,
        common_mistakes LONGTEXT,
        harness_class VARCHAR(200),
        create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        deleted INT DEFAULT 0
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

    CREATE TABLE IF NOT EXISTS code_test_case (
        id BIGINT AUTO_INCREMENT PRIMARY KEY,
        problem_id BIGINT NOT NULL,
        input LONGTEXT NOT NULL,
        expected_output LONGTEXT NOT NULL,
        visibility VARCHAR(16) NOT NULL DEFAULT 'PUBLIC',
        weight INT DEFAULT 1,
        order_no INT DEFAULT 0,
        version VARCHAR(24) DEFAULT 'v1',
        create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        deleted INT DEFAULT 0
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

    CREATE TABLE IF NOT EXISTS code_draft (
        id BIGINT AUTO_INCREMENT PRIMARY KEY,
        user_id BIGINT NOT NULL,
        question_id BIGINT NOT NULL,
        language VARCHAR(20) NOT NULL DEFAULT 'java',
        source_code LONGTEXT,
        create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        deleted INT DEFAULT 0
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

    CREATE TABLE IF NOT EXISTS code_submission (
        id BIGINT AUTO_INCREMENT PRIMARY KEY,
        user_id BIGINT NOT NULL,
        question_id BIGINT NOT NULL,
        problem_version VARCHAR(24) DEFAULT 'v1',
        language VARCHAR(20) NOT NULL DEFAULT 'java',
        source_code LONGTEXT NOT NULL,
        submission_type VARCHAR(16) NOT NULL DEFAULT 'SUBMIT',
        judge_status VARCHAR(24) NOT NULL DEFAULT 'QUEUED',
        score INT,
        passed_count INT DEFAULT 0,
        total_count INT DEFAULT 0,
        execution_time_ms INT,
        memory_used_kb INT,
        compile_output LONGTEXT,
        case_results LONGTEXT,
        judge_detail LONGTEXT,
        context_type VARCHAR(16) DEFAULT 'PRACTICE',
        context_ref_id BIGINT,
        create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        deleted INT DEFAULT 0
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'question_topic' AND index_name = 'uk_qt_code') THEN
        CREATE UNIQUE INDEX uk_qt_code ON question_topic (code, deleted);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'code_problem' AND index_name = 'uk_cp_question') THEN
        CREATE UNIQUE INDEX uk_cp_question ON code_problem (question_id, deleted);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'code_test_case' AND index_name = 'idx_ctc_problem') THEN
        CREATE INDEX idx_ctc_problem ON code_test_case (problem_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'code_draft' AND index_name = 'uk_cd_user_question') THEN
        CREATE UNIQUE INDEX uk_cd_user_question ON code_draft (user_id, question_id, deleted);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'code_submission' AND index_name = 'idx_cs_user') THEN
        CREATE INDEX idx_cs_user ON code_submission (user_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'code_submission' AND index_name = 'idx_cs_question') THEN
        CREATE INDEX idx_cs_question ON code_submission (question_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'code_submission' AND index_name = 'idx_cs_status') THEN
        CREATE INDEX idx_cs_status ON code_submission (judge_status);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'code_submission' AND index_name = 'idx_cs_context') THEN
        CREATE INDEX idx_cs_context ON code_submission (context_type, context_ref_id);
    END IF;
END$$
DELIMITER ;
CALL migrate_phase3g_coding_bank();
DROP PROCEDURE IF EXISTS migrate_phase3g_coding_bank;
