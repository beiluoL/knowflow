-- 增量辅助表：不改写原会话快照，不清空用户历史；同一脚本可重复运行。
CREATE TABLE IF NOT EXISTS mock_interview_v2_state (
 session_id BIGINT PRIMARY KEY COMMENT '原面试会话主键',
 user_id BIGINT NOT NULL COMMENT '服务端认证的会话所属用户',
 start_request VARCHAR(80) NOT NULL COMMENT '创建幂等键',
 start_hash VARCHAR(64) NOT NULL COMMENT '创建请求指纹',
 version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
 state VARCHAR(32) NOT NULL COMMENT '服务端面试状态',
 mode VARCHAR(20) NOT NULL COMMENT 'BASIC基础 STRICT严格 TEACHING教学',
 role VARCHAR(100) NOT NULL COMMENT '目标岗位，仅作为不可信上下文',
 difficulty VARCHAR(20) NOT NULL COMMENT '用户选择的面试难度',
 max_depth INT NOT NULL COMMENT '最大连续追问深度',
 question_index INT NOT NULL DEFAULT 0 COMMENT '当前主问题序号，从零开始',
 followup_depth INT NOT NULL DEFAULT 0 COMMENT '当前追问深度',
 duration_minutes INT NOT NULL COMMENT '有效面试时间上限分钟',
 deadline TIMESTAMP NOT NULL COMMENT '服务端有效截止时间',
 paused_at TIMESTAMP COMMENT '服务端暂停时间',
 current_prompt VARCHAR(2000) NOT NULL COMMENT '当前服务端决定的问题',
 current_question_id VARCHAR(100) NOT NULL COMMENT '原会话快照题目身份',
 project_json TEXT COMMENT '已授权项目资料及用户描述快照',
 message VARCHAR(1000) COMMENT '降级或超时提示，不包含密钥',
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
 updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '最后状态变更时间',
 CONSTRAINT fk_iv2_session FOREIGN KEY(session_id) REFERENCES mock_interview_session(id),
 CONSTRAINT uk_iv2_start UNIQUE(user_id,start_request),
 CONSTRAINT ck_iv2_state CHECK(state IN ('CREATED','ASKING','ANSWERING','TRANSCRIBING','ANSWER_CONFIRMED','EVALUATING','DECIDING','FOLLOW_UP','PAUSED','COMPLETED')),
 CONSTRAINT ck_iv2_depth CHECK(max_depth BETWEEN 0 AND 3 AND followup_depth BETWEEN 0 AND 3)
) COMMENT='面试V2的受控状态，不建立第二套学习状态';
CREATE INDEX IF NOT EXISTS idx_iv2_owner ON mock_interview_v2_state(user_id,state);
CREATE TABLE IF NOT EXISTS mock_interview_v2_turn (
 id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '确认轮次身份',
 session_id BIGINT NOT NULL COMMENT '原面试会话身份',
 user_id BIGINT NOT NULL COMMENT '服务端所属用户',
 request_id VARCHAR(80) NOT NULL COMMENT '确认提交幂等键',
 request_hash VARCHAR(64) NOT NULL COMMENT '确认内容指纹，拒绝同键不同正文',
 question_id VARCHAR(100) NOT NULL COMMENT '主问题快照身份',
 prompt VARCHAR(2000) NOT NULL COMMENT '本轮真正问过的问题',
 depth INT NOT NULL COMMENT '本轮连续追问深度',
 answer TEXT NOT NULL COMMENT '用户明确确认后的文字，仅此文字可评分',
 speech_id BIGINT COMMENT '原始语音转写身份，不能重复作为新轮次消费',
 self_score INT COMMENT '用户自评分，独立于AI评分，可为空',
 evaluation_json TEXT COMMENT '经结构及原文引用校验的AI建议，含模型和版本',
 decision VARCHAR(24) COMMENT '服务端最终决定的追问或下一题',
 source VARCHAR(24) NOT NULL COMMENT 'AI或预置回退来源',
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '确认时间',
 CONSTRAINT fk_iv2_turn_session FOREIGN KEY(session_id) REFERENCES mock_interview_session(id),
 CONSTRAINT uk_iv2_answer UNIQUE(user_id,request_id),
 CONSTRAINT uk_iv2_position UNIQUE(session_id,question_id,depth),
 CONSTRAINT uk_iv2_speech UNIQUE(speech_id),
 CONSTRAINT ck_iv2_self_score CHECK(self_score IS NULL OR self_score BETWEEN 1 AND 5)
) COMMENT='真实确认回答与追问链，不混用自评与AI评价';
CREATE INDEX IF NOT EXISTS idx_iv2_turn_session ON mock_interview_v2_turn(session_id,id);
CREATE TABLE IF NOT EXISTS mock_interview_v2_command (
 user_id BIGINT NOT NULL COMMENT '服务端用户',
 request_id VARCHAR(80) NOT NULL COMMENT '命令幂等键',
 session_id BIGINT NOT NULL COMMENT '原会话身份',
 operation VARCHAR(20) NOT NULL COMMENT 'pause resume finish命令',
 request_hash VARCHAR(64) NOT NULL COMMENT '版本和操作指纹',
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '执行时间',
 PRIMARY KEY(user_id,request_id),
 CONSTRAINT fk_iv2_command_session FOREIGN KEY(session_id) REFERENCES mock_interview_session(id)
) COMMENT='面试状态操作幂等记录';
