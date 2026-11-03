CREATE TABLE IF NOT EXISTS interview_career_job_event (
 id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '事件身份',
 record_id BIGINT NOT NULL COMMENT '岗位身份',
 user_id BIGINT NOT NULL COMMENT '认证所属用户',
 event_type VARCHAR(40) NOT NULL COMMENT '事件类型',
 from_status VARCHAR(24) NOT NULL COMMENT '变更前状态',
 to_status VARCHAR(24) NOT NULL COMMENT '变更后状态',
 note VARCHAR(2000) NOT NULL DEFAULT '' COMMENT '用户备注',
 occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '真实发生时间',
 INDEX idx_career_event_owner (user_id,record_id,id)
) COMMENT='岗位真实状态事件，不回填虚构历史';
