-- Additive stage metadata only; existing V2 stores immutable confirmed turns.
CREATE TABLE IF NOT EXISTS interview_radar_session_binding (
 session_id BIGINT PRIMARY KEY,
 user_id BIGINT NOT NULL,
 stage VARCHAR(20) NOT NULL,
 interview_mode VARCHAR(24) NOT NULL,
 source_review_status VARCHAR(32) NOT NULL,
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);
