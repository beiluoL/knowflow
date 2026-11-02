-- 重评只追加运行快照，不覆盖原始轮次与成绩。
CREATE TABLE IF NOT EXISTS interview_evaluation_run (
 id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '评价运行身份',
 session_id BIGINT NOT NULL COMMENT '原面试会话逻辑关联',
 turn_id BIGINT NOT NULL COMMENT '不可变回答轮次逻辑关联',
 user_id BIGINT NOT NULL COMMENT '服务端认证用户',
 request_id VARCHAR(100) NOT NULL COMMENT '用户确认重评的幂等标识',
 status VARCHAR(24) NOT NULL COMMENT 'PENDING EVALUATED PARTIAL NOT_EVALUATED',
 evaluation_json TEXT COMMENT '已校验评分与模型规则版本快照',
 create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
 CONSTRAINT uk_ieval_request UNIQUE(user_id,session_id,turn_id,request_id)
) COMMENT='面试重评历史，不更新原始评分或客观掌握度';
