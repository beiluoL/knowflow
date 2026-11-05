CREATE TABLE IF NOT EXISTS ai_quota_usage (
  usage_day DATE NOT NULL,
  dimension_key VARCHAR(80) NOT NULL,
  reserved_tokens BIGINT NOT NULL,
  PRIMARY KEY (usage_day, dimension_key)
);

CREATE TABLE IF NOT EXISTS ai_billing_entry (
  id VARCHAR(36) PRIMARY KEY,
  usage_day DATE NOT NULL,
  user_id BIGINT,
  scene VARCHAR(40) NOT NULL,
  model_scope VARCHAR(255) NOT NULL,
  reserved_tokens BIGINT NOT NULL,
  status VARCHAR(24) NOT NULL,
  input_tokens BIGINT,
  output_tokens BIGINT,
  input_usd_per_million DECIMAL(20,8),
  output_usd_per_million DECIMAL(20,8),
  cost_usd DECIMAL(30,14),
  INDEX idx_ai_billing_owner_day (user_id, usage_day),
  INDEX idx_ai_billing_scene_day (scene, usage_day)
);
