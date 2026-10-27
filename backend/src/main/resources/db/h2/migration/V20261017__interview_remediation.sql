-- 已确认的面试复习任务关联既有 task 表，不建立第二套任务或掌握度系统。
-- 不级联删除：逻辑删除后的来源与确认历史保留，归属由服务端校验及会话行锁保证。
CREATE TABLE IF NOT EXISTS interview_remediation (
 session_id BIGINT NOT NULL,
 knowledge_id BIGINT NOT NULL,
 user_id BIGINT NOT NULL,
 task_id BIGINT NOT NULL,
 request_id VARCHAR(100) NOT NULL,
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 PRIMARY KEY (session_id, knowledge_id),
 CONSTRAINT fk_interview_remediation_session FOREIGN KEY (session_id) REFERENCES mock_interview_session(id),
 CONSTRAINT fk_interview_remediation_knowledge FOREIGN KEY (knowledge_id) REFERENCES kg_entity(id),
 CONSTRAINT fk_interview_remediation_task FOREIGN KEY (task_id) REFERENCES task(id),
 CONSTRAINT fk_interview_remediation_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
 CONSTRAINT ck_interview_remediation_ids CHECK (session_id > 0 AND knowledge_id > 0 AND user_id > 0 AND task_id > 0)
);
CREATE INDEX IF NOT EXISTS idx_interview_remediation_user ON interview_remediation(user_id, session_id);
COMMENT ON TABLE interview_remediation IS '用户明确确认的面试复习任务关联；保留历史且不写掌握度';
COMMENT ON COLUMN interview_remediation.session_id IS '来源面试会话ID，关联mock_interview_session.id';
COMMENT ON COLUMN interview_remediation.knowledge_id IS '实际可阅读知识实体ID，关联kg_entity.id；同会话去重';
COMMENT ON COLUMN interview_remediation.user_id IS '服务端认证用户ID，关联sys_user.id；会话及任务归属由服务校验';
COMMENT ON COLUMN interview_remediation.task_id IS '复用既有任务ID，关联task.id；不自动完成任务';
COMMENT ON COLUMN interview_remediation.request_id IS '首次确认请求标识；去重按会话与知识实体而非请求标识';
COMMENT ON COLUMN interview_remediation.created_at IS '首次明确确认并创建关联的时间';
