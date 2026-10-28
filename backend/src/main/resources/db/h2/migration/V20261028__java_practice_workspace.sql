-- F11仅增加草稿修订与提交快照；源码和判题仍复用原code_draft/code_submission。
CREATE TABLE IF NOT EXISTS code_draft_revision (
 owner_id BIGINT NOT NULL, question_id BIGINT NOT NULL, revision BIGINT NOT NULL,
 request_id VARCHAR(80) NOT NULL, input_hash VARCHAR(64) NOT NULL,
 language VARCHAR(20) NOT NULL, source_code LONGTEXT NOT NULL,
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(owner_id,question_id,revision), UNIQUE(owner_id,request_id)
);
CREATE TABLE IF NOT EXISTS code_practice_job (
 submission_id BIGINT PRIMARY KEY, owner_id BIGINT NOT NULL,
 request_id VARCHAR(80) NOT NULL, input_hash VARCHAR(64) NOT NULL,
 snapshot_json LONGTEXT NOT NULL, tutor_note_id BIGINT,
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, UNIQUE(owner_id,request_id)
);
CREATE TABLE IF NOT EXISTS code_practice_attempt (
 submission_id BIGINT NOT NULL, attempt_no INT NOT NULL, owner_id BIGINT NOT NULL,
 request_id VARCHAR(80) NOT NULL, state VARCHAR(40) NOT NULL,
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, finished_at TIMESTAMP,
 PRIMARY KEY(submission_id,attempt_no), UNIQUE(owner_id,request_id)
);
