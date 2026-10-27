
-- Phase 3I-G: independent interview AI advice, never an answer or score source.
CREATE TABLE IF NOT EXISTS mock_interview_ai_turn (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, session_id BIGINT NOT NULL,
 question_id VARCHAR(100) NOT NULL, answer_fingerprint VARCHAR(64) NOT NULL,
 round_no INT NOT NULL, request_id VARCHAR(80) NOT NULL, status VARCHAR(16) NOT NULL,
 source_snapshot MEDIUMTEXT NOT NULL, followup_answer TEXT, result_json TEXT, error_message VARCHAR(240),
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE (user_id,request_id), UNIQUE (session_id,question_id,round_no)
);
