-- 适用：已部署的 MySQL 8 生产库（新库直接执行 db/mysql/schema.sql 即可，无需本脚本）
-- 目的：新增学习目标表 learning_goal（Architecture-4.0 学习闭环起点）
-- 说明：CREATE TABLE IF NOT EXISTS 幂等，重复执行不报错。
--       Flyway 未启用时，可在「数据库设置」页或手动执行本脚本为存量库补表。
--       存量库需先存在 learning_path / learning_user_path（Phase1 已交付），此处仅做逻辑外键不建物理约束。

CREATE TABLE IF NOT EXISTS learning_goal (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  user_id       BIGINT NOT NULL COMMENT '所属用户ID（逻辑外键 sys_user.id）',
  title         VARCHAR(200) NOT NULL COMMENT '目标标题：一句话目标，如「7 天掌握 JVM GC」',
  description   VARCHAR(1000) NULL COMMENT '目标描述（为什么学）',
  target        VARCHAR(500) NULL COMMENT '达标标准（做到什么算掌握）',
  status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/COMPLETED/ARCHIVED',
  path_id       BIGINT NULL COMMENT '关联学习路径ID（逻辑外键 learning_path.id，可空）',
  start_date    DATE NULL COMMENT '开始日期',
  deadline      DATE NULL COMMENT '截止日期（时间盒终点）',
  daily_minutes INT DEFAULT 0 COMMENT '每日计划投入分钟（0=未约定）',
  create_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted       INT DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
  KEY idx_lg_user (user_id, status),
  KEY idx_lg_path (path_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
