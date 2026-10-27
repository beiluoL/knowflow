CREATE TABLE IF NOT EXISTS interview_seed_pack (
 seed_key VARCHAR(80) PRIMARY KEY,
 initialized INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS interview_seed_resource (
 seed_key VARCHAR(80) NOT NULL,
 resource_key VARCHAR(180) NOT NULL,
 resource_type VARCHAR(30) NOT NULL,
 resource_id BIGINT NOT NULL,
 PRIMARY KEY(seed_key, resource_key)
);
CREATE TABLE IF NOT EXISTS interview_seed_activation (
 seed_key VARCHAR(80) NOT NULL,
 user_id BIGINT NOT NULL,
 goal_id BIGINT NOT NULL,
 activated_at TIMESTAMP NOT NULL,
 PRIMARY KEY(seed_key, user_id)
);
