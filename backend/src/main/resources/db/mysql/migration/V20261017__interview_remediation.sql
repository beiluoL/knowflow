-- task_id 关联既有 task；会话行锁及联合主键保证跨请求幂等。
-- 不级联删除，不清理逻辑删除记录的历史关联；归属由服务端校验。
CREATE TABLE IF NOT EXISTS interview_remediation (
 session_id BIGINT NOT NULL COMMENT '来源面试会话ID，关联mock_interview_session.id',
 knowledge_id BIGINT NOT NULL COMMENT '实际可阅读知识实体ID，关联kg_entity.id；同会话去重',
 user_id BIGINT NOT NULL COMMENT '服务端认证用户ID，关联sys_user.id；会话及任务归属由服务校验',
 task_id BIGINT NOT NULL COMMENT '复用既有任务ID，关联task.id；不自动完成任务',
 request_id VARCHAR(100) NOT NULL COMMENT '首次确认请求标识；去重按会话与知识实体而非请求标识',
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次明确确认并创建关联的时间',
 PRIMARY KEY (session_id, knowledge_id),
 KEY idx_interview_remediation_user(user_id,session_id),
 CONSTRAINT fk_interview_remediation_session FOREIGN KEY (session_id) REFERENCES mock_interview_session(id),
 CONSTRAINT fk_interview_remediation_knowledge FOREIGN KEY (knowledge_id) REFERENCES kg_entity(id),
 CONSTRAINT fk_interview_remediation_task FOREIGN KEY (task_id) REFERENCES task(id),
 CONSTRAINT fk_interview_remediation_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
 CONSTRAINT ck_interview_remediation_ids CHECK (session_id > 0 AND knowledge_id > 0 AND user_id > 0 AND task_id > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户明确确认的面试复习任务关联；保留历史且不写掌握度';
