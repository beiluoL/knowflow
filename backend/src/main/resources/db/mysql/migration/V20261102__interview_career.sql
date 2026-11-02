-- 本人简历/JD与不可变版本独立存储，不进入知识库或向量索引。
CREATE TABLE IF NOT EXISTS interview_career_record (
 id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '个人资料身份',
 user_id BIGINT NOT NULL COMMENT '服务端认证所属用户',
 kind VARCHAR(12) NOT NULL COMMENT 'RESUME简历 JOB岗位',
 title VARCHAR(160) NOT NULL COMMENT '本人资料标题',
 current_version INT NOT NULL DEFAULT 1 COMMENT '当前版本号',
 is_default INT NOT NULL DEFAULT 0 COMMENT '本人默认简历标识',
 deleted INT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
 create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间'
) COMMENT='本人简历与岗位元数据';
CREATE TABLE IF NOT EXISTS interview_career_version (
 id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '版本快照身份',
 record_id BIGINT NOT NULL COMMENT '资料逻辑关联',
 version_no INT NOT NULL COMMENT '不可变内容版本',
 title VARCHAR(160) NOT NULL COMMENT '版本标题快照',
 source_text LONGTEXT NOT NULL COMMENT '仅本人可读的原文',
 sections_json LONGTEXT NOT NULL COMMENT '结构化声明与连续原文引用',
 confirmed INT NOT NULL DEFAULT 0 COMMENT '用户已确认标识',
 create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '确认时间',
 CONSTRAINT uk_career_version UNIQUE(record_id,version_no)
) COMMENT='简历岗位版本，不覆盖已用于匹配与面试的内容';

CREATE TABLE IF NOT EXISTS interview_career_snapshot (
 id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '匹配或方案快照身份',
 user_id BIGINT NOT NULL COMMENT '所属用户',
 kind VARCHAR(12) NOT NULL COMMENT 'MATCH匹配 PLAN方案',
 request_id VARCHAR(100) NOT NULL COMMENT '创建幂等键',
 input_json LONGTEXT NOT NULL COMMENT '版本身份与配置快照',
 payload_json LONGTEXT NOT NULL COMMENT '计算结果与证据快照',
 create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
 CONSTRAINT uk_career_snapshot_request UNIQUE(user_id,kind,request_id)
) COMMENT='私人求职匹配与定制面试快照';
CREATE TABLE IF NOT EXISTS interview_career_session (
 id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '定制面试关联身份',
 user_id BIGINT NOT NULL COMMENT '所属用户',
 plan_id BIGINT NOT NULL COMMENT '方案逻辑关联',
 session_id BIGINT NOT NULL COMMENT '原V2会话逻辑关联',
 create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
 CONSTRAINT uk_career_session UNIQUE(user_id,session_id)
) COMMENT='岗位方案与原面试会话关联';

CREATE TABLE IF NOT EXISTS interview_career_file (
 id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '私人文件身份',
 record_id BIGINT NOT NULL COMMENT '资料逻辑关联',
 user_id BIGINT NOT NULL COMMENT '所属用户',
 content_type VARCHAR(100) NOT NULL COMMENT '内容检测格式',
 content_bytes LONGBLOB COMMENT '仅本人鉴权下载的原文件，删除时清空',
 create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
 CONSTRAINT uk_career_file_record UNIQUE(record_id)
) COMMENT='私人原始文件，不生成公开URL或全局向量';
