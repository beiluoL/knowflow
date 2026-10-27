-- Additive only. Apply explicitly to existing MySQL deployments before enabling catalog mutations.
-- No question/reference/source IDs are modified.
CREATE TABLE IF NOT EXISTS question_catalog_audit (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 actor_id BIGINT NOT NULL,
 source_type VARCHAR(32) NOT NULL,
 source_question_id BIGINT NOT NULL,
 action VARCHAR(32) NOT NULL,
 before_state VARCHAR(512),
 after_state VARCHAR(512),
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
