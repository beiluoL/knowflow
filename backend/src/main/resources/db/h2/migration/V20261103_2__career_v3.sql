-- Career V3.0：岗位来源溯源、搜索历史、定制简历任务。
-- 不修改既有 record/version/snapshot 表，不重建任何历史数据。
CREATE TABLE IF NOT EXISTS interview_career_job_source (
 id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '岗位溯源身份',
 user_id BIGINT NOT NULL COMMENT '服务端认证所属用户',
 record_id BIGINT NOT NULL COMMENT '岗位资料逻辑关联',
 source_id VARCHAR(32) NOT NULL COMMENT '来源标识，如 remotive/remoteok/manual',
 source_name VARCHAR(64) NOT NULL COMMENT '来源展示名',
 external_job_id VARCHAR(191) NULL COMMENT '上游岗位ID，仅在上游提供时保存',
 source_url VARCHAR(2048) NULL COMMENT '原岗位链接，用于回链与去重',
 attribution VARCHAR(200) NULL COMMENT '上游要求的署名文案',
 posted_at TIMESTAMP NULL COMMENT '上游发布时间，缺失为NULL不推测',
 fetched_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '抓取时间',
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
 CONSTRAINT uk_career_job_source_record UNIQUE(record_id)
) COMMENT='岗位来源溯源，仅本人可读，不参与权限判定';

CREATE TABLE IF NOT EXISTS interview_career_search_history (
 id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '搜索记录身份',
 user_id BIGINT NOT NULL COMMENT '服务端认证所属用户',
 keyword VARCHAR(100) NOT NULL COMMENT '搜索关键词',
 filters_json VARCHAR(2000) NOT NULL COMMENT '筛选条件快照',
 saved INT NOT NULL DEFAULT 0 COMMENT '是否保存为常用搜索',
 last_used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近使用时间',
 create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
 deleted INT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识'
) COMMENT='本人岗位搜索历史与保存的搜索条件';

CREATE TABLE IF NOT EXISTS interview_career_tailoring_task (
 id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '定制任务身份',
 user_id BIGINT NOT NULL COMMENT '服务端认证所属用户',
 resume_id BIGINT NOT NULL COMMENT '主简历逻辑关联',
 resume_version INT NOT NULL COMMENT '主简历版本，不可变',
 job_id BIGINT NOT NULL COMMENT '目标岗位逻辑关联',
 job_version INT NOT NULL COMMENT '岗位版本，不可变',
 match_id BIGINT NULL COMMENT '使用的匹配快照，可空',
 mode VARCHAR(16) NOT NULL COMMENT 'LIGHT轻度 KEYWORD关键词 DEEP深度',
 status VARCHAR(24) NOT NULL COMMENT 'GENERATED待审核 APPLIED已生成版本 DISCARDED已放弃',
 model_source VARCHAR(24) NOT NULL COMMENT 'RULE规则生成 AI模型生成',
 changes_json LONGTEXT NOT NULL COMMENT '逐条修改建议与原文证据',
 prompt_version VARCHAR(24) NOT NULL COMMENT '提示词或规则版本',
 result_version INT NULL COMMENT '生成的新简历版本号',
 deleted INT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
 create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间'
) COMMENT='简历定制任务，只建议不覆盖原简历';
