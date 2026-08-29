-- 适用：已部署的 MySQL 8 生产库（新库直接执行 db/mysql/schema.sql 即可，无需本脚本）
-- 目的：新建学习行为事件表 learning_event（Learning Event System，Phase 1）
-- 说明：CREATE TABLE IF NOT EXISTS 幂等，重复执行不报错。
--       Flyway 未启用时，可在「数据库设置」页或手动执行本脚本为存量库补表。

CREATE TABLE IF NOT EXISTS learning_event (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  user_id BIGINT NOT NULL COMMENT '所属用户ID（逻辑外键 sys_user.id）',
  event_type VARCHAR(40) NOT NULL COMMENT '事件类型',
  resource_type VARCHAR(40) COMMENT '资源类型',
  resource_id BIGINT COMMENT '关联资源ID（可为空）',
  metadata LONGTEXT COMMENT '事件扩展信息（JSON）',
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
  KEY idx_le_user_time (user_id, create_time),
  KEY idx_le_user_type (user_id, event_type, create_time),
  KEY idx_le_resource (resource_type, resource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
