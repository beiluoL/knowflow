CREATE TABLE IF NOT EXISTS interview_resume_draft (
 id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '草稿身份',
 user_id BIGINT NOT NULL COMMENT '认证所属用户',
 record_id BIGINT NOT NULL COMMENT '简历身份，0表示新简历草稿',
 revision BIGINT NOT NULL COMMENT '并发修订号',
 base_version INT NOT NULL COMMENT '基于的正式版本',
 payload_json LONGTEXT NOT NULL COMMENT '有界编辑内容，非正式版本',
 create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近保存时间',
 CONSTRAINT uk_resume_draft_owner UNIQUE(user_id,record_id)
) COMMENT='私人简历编辑草稿，不改变正式历史';
