-- Architecture-3：UserApp 后端化（user_app 表）
-- 与 db/h2/schema.sql、db/mysql/schema.sql 同构（§22/§203：H2 与 MySQL 一致）。
CREATE TABLE IF NOT EXISTS user_app (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    app_key VARCHAR(50) NOT NULL,
    installed INT DEFAULT 1,
    enabled INT DEFAULT 1,
    sort_order INT DEFAULT 0,
    last_open_at TIMESTAMP NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    UNIQUE KEY uk_user_app (user_id, app_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
