-- Career Apps V2.0：简历工具箱分析记录 + 模板设计配置。
-- 只新增两表，不改写 interview_career_record/version/snapshot，也不重建既有数据。
CREATE TABLE IF NOT EXISTS interview_career_tool_run (
 id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '分析记录身份',
 user_id BIGINT NOT NULL COMMENT '服务端认证所属用户',
 resume_id BIGINT NOT NULL COMMENT '简历逻辑关联',
 resume_version INT NOT NULL COMMENT '分析时的简历版本，历史结果绑定具体版本',
 job_id BIGINT NULL COMMENT '岗位逻辑关联，可空',
 job_version INT NULL COMMENT '分析时的岗位版本，可空',
 tool_type VARCHAR(16) NOT NULL COMMENT 'ATS可读性 CONTENT内容体检 JD定向优化',
 analysis_version VARCHAR(24) NOT NULL COMMENT '分析规则版本，规则升级不回算历史',
 result_json LONGTEXT NOT NULL COMMENT '分析结果快照，不保存简历原文副本',
 deleted INT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
 create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
 INDEX idx_career_tool_run_user (user_id, tool_type, create_time)
) COMMENT='简历工具分析记录，仅本人可读';

CREATE TABLE IF NOT EXISTS interview_career_design (
 id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '设计配置身份',
 user_id BIGINT NOT NULL COMMENT '服务端认证所属用户',
 resume_id BIGINT NOT NULL COMMENT '简历逻辑关联，不绑定版本',
 name VARCHAR(80) NOT NULL COMMENT '本人可读的设计名称',
 template_id VARCHAR(48) NOT NULL COMMENT '模板标识',
 template_version VARCHAR(16) NOT NULL COMMENT '模板版本',
 theme_json LONGTEXT NOT NULL COMMENT '字体/字号/行距/页边距/主题色/章节顺序等',
 is_default INT NOT NULL DEFAULT 0 COMMENT '本人默认设计标识',
 deleted INT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
 create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
 INDEX idx_career_design_user (user_id, resume_id, deleted)
) COMMENT='简历排版设计，与简历内容版本独立';
