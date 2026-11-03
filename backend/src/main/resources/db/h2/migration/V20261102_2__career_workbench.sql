-- 求职工作台：岗位投递状态与备注。仅新增本人岗位流程状态，不改写简历/JD 版本或匹配快照。
CREATE TABLE IF NOT EXISTS interview_career_job_pipeline (
 id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '岗位流程状态身份',
 record_id BIGINT NOT NULL COMMENT '岗位资料逻辑关联',
 user_id BIGINT NOT NULL COMMENT '服务端认证所属用户',
 status VARCHAR(24) NOT NULL DEFAULT 'SAVED' COMMENT 'SAVED待关注 TO_APPLY待投递 APPLIED已投递 WRITTEN_TEST笔试 INTERVIEWING面试中 OFFER已录用 REJECTED已拒绝 CLOSED已结束',
 note VARCHAR(2000) NULL COMMENT '本人投递备注',
 applied_at TIMESTAMP NULL COMMENT '首次进入已投递时间',
 create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
 CONSTRAINT uk_career_job_pipeline UNIQUE(record_id)
) COMMENT='本人岗位投递流程状态，不参与权限判定';

CREATE INDEX IF NOT EXISTS idx_career_pipeline_user ON interview_career_job_pipeline (user_id, status);
CREATE INDEX IF NOT EXISTS idx_career_record_user_kind ON interview_career_record (user_id, kind, deleted);
CREATE INDEX IF NOT EXISTS idx_career_snapshot_user_kind ON interview_career_snapshot (user_id, kind, id);
CREATE INDEX IF NOT EXISTS idx_career_session_user_plan ON interview_career_session (user_id, plan_id);
