CREATE TABLE IF NOT EXISTS learning_evidence_receipt (
 user_id BIGINT NOT NULL COMMENT '认证用户，不授予管理员读取私人答卷权限',
 source_type VARCHAR(32) NOT NULL COMMENT '原业务记录命名空间',
 source_id BIGINT NOT NULL COMMENT '原作答/复习事件/面试轮次ID，不复制题库身份',
 resource_key VARCHAR(160) NOT NULL COMMENT '来源内资源身份，不跨量纲合并成绩',
 first_source_id BIGINT NOT NULL COMMENT '捕获时原表已知首条记录ID，不是最近一页首条',
 snapshot_json LONGTEXT NOT NULL COMMENT '不可覆盖的原回答、评分依据、题目与条件快照',
 snapshot_sha VARCHAR(64) NOT NULL COMMENT '冻结回执SHA256，不以当前题目补造旧版本',
 occurred_at TIMESTAMP NOT NULL COMMENT '原业务发生时间',
 captured_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '真实捕获时间，区别历史发生时间',
 PRIMARY KEY(user_id,source_type,source_id)
) COMMENT='V7跨模块证据回执，只追加，不产生新成绩或掌握度';
CREATE TABLE IF NOT EXISTS learning_evidence_knowledge (
 user_id BIGINT NOT NULL COMMENT '回执所属用户',
 source_type VARCHAR(32) NOT NULL COMMENT '原来源命名空间',
 source_id BIGINT NOT NULL COMMENT '回执来源ID',
 knowledge_id BIGINT NOT NULL COMMENT '唯一身份kg_entity.id，仅原始快照已有关系',
 PRIMARY KEY(user_id,knowledge_id,source_type,source_id)
) COMMENT='冻结证据知识索引，不自动审核或新建知识映射';

CREATE TABLE IF NOT EXISTS learning_evidence_retest (
 user_id BIGINT NOT NULL COMMENT '本人关系，只读取认证身份',
 retest_type VARCHAR(32) NOT NULL COMMENT '复测来源命名空间',
 retest_id BIGINT NOT NULL COMMENT '原始复测记录ID，禁止覆盖原答',
 baseline_type VARCHAR(32) NOT NULL COMMENT '原始作答来源命名空间',
 baseline_id BIGINT NOT NULL COMMENT '原始作答记录ID',
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '用户明确建立关系的时间，不回填成历史发生时间',
 PRIMARY KEY(user_id,retest_type,retest_id)
) COMMENT='显式复测关系，只追加，一条复测不能被重绑基线';
