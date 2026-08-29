-- 适用：已部署的 MySQL 8 生产库（新库直接执行 db/mysql/schema.sql 即可，无需本脚本）
-- 目的：新建知识点掌握度引擎两套表（Knowledge Mastery Engine，Phase 2-B）
-- 说明：CREATE TABLE IF NOT EXISTS 幂等，重复执行不报错。
--       Flyway 未启用时，可在「数据库设置」页或手动执行本脚本为存量库补表。

CREATE TABLE IF NOT EXISTS resource_knowledge_mapping (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  resource_type VARCHAR(40) NOT NULL COMMENT '资源类型：QUIZ/CODE_QUESTION/MISTAKE/REVIEW_CARD/RECALL_SESSION/FLASHCARD/DOC',
  resource_id   BIGINT NOT NULL COMMENT '关联资源ID（逻辑外键，按 resource_type 指向对应业务表）',
  knowledge_id  BIGINT NOT NULL COMMENT '知识点ID（逻辑外键 kg_entity.id，仅可学习类型）',
  source        VARCHAR(20) NOT NULL DEFAULT 'AUTO' COMMENT '映射来源：MANUAL/AI/IMPORT/AUTO/CATEGORY_FALLBACK',
  confidence    DECIMAL(4,3) DEFAULT 0 COMMENT '置信度 0~1',
  status        VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '映射状态：ACCEPTED/PENDING/REJECTED',
  create_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted       INT      DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
  UNIQUE KEY uk_rkm_res_know (resource_type, resource_id, knowledge_id),
  KEY idx_rkm_knowledge (knowledge_id),
  KEY idx_rkm_resource (resource_type, resource_id),
  KEY idx_rkm_status (source, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS knowledge_mastery (
  id                    BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  user_id               BIGINT NOT NULL COMMENT '所属用户ID（逻辑外键 sys_user.id）',
  knowledge_id          BIGINT NOT NULL COMMENT '知识点ID（逻辑外键 kg_entity.id）',
  mastery_score         INT    DEFAULT 0 COMMENT '掌握度 0~100',
  confidence_score      INT    DEFAULT 0 COMMENT '置信度 0~100',
  learning_status       VARCHAR(20) DEFAULT 'NOT_STARTED' COMMENT '学习状态：NOT_STARTED/LEARNING/WEAK/MASTERED/REVIEW_REQUIRED',
  correct_count         INT    DEFAULT 0 COMMENT 'Quiz 答对累计',
  wrong_count           INT    DEFAULT 0 COMMENT 'Quiz 答错累计',
  attempt_count         INT    DEFAULT 0 COMMENT 'Quiz 作答次数累计',
  review_count          INT    DEFAULT 0 COMMENT '复习次数累计',
  recall_count          INT    DEFAULT 0 COMMENT '主动回忆会话次数累计',
  recall_avg_score      INT    DEFAULT 0 COMMENT '主动回忆平均得分 0~100',
  coding_attempt_count   INT    DEFAULT 0 COMMENT '代码提交次数累计',
  coding_pass_count      INT    DEFAULT 0 COMMENT '代码完全通过次数累计',
  mistake_count         INT    DEFAULT 0 COMMENT '关联错题数累计',
  mistake_mastered      INT    DEFAULT 0 COMMENT '已掌握错题数累计',
  consecutive_correct   INT    DEFAULT 0 COMMENT '当前连续答对',
  consecutive_wrong     INT    DEFAULT 0 COMMENT '当前连续答错',
  last_learned_at       TIMESTAMP COMMENT '最近学习/互动时间',
  last_reviewed_at      TIMESTAMP COMMENT '最近复习时间',
  last_assessed_at      TIMESTAMP COMMENT '最近测评时间',
  next_review_at        TIMESTAMP COMMENT '下次应复习时间',
  forgetting_risk       INT    DEFAULT 0 COMMENT '遗忘风险 0~100',
  create_time          TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time           TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted               INT      DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
  UNIQUE KEY uk_km_user_knowledge (user_id, knowledge_id),
  KEY idx_km_status (user_id, learning_status),
  KEY idx_km_risk (user_id, forgetting_risk),
  KEY idx_km_next (user_id, next_review_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
