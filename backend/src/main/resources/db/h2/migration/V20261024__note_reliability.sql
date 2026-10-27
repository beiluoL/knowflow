-- 2026-10-24 笔记可靠性增量升级；不删除原始数据。
-- 自动执行入口为 NoteSchema.migrate，由 DataSourceManager 在发布目标连接前调用。
ALTER TABLE learning_note ADD COLUMN IF NOT EXISTS revision BIGINT NOT NULL DEFAULT 1 COMMENT '递增修订号';
ALTER TABLE learning_note ADD COLUMN IF NOT EXISTS source_type VARCHAR(20) NOT NULL DEFAULT 'GENERAL' COMMENT '笔记来源';
ALTER TABLE learning_note ADD COLUMN IF NOT EXISTS content_format VARCHAR(20) NOT NULL DEFAULT 'MARKDOWN' COMMENT '正文格式';
ALTER TABLE learning_note ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'SAVED' COMMENT '草稿或已保存';
ALTER TABLE learning_note ADD COLUMN IF NOT EXISTS tags VARCHAR(2000) COMMENT '标签';
ALTER TABLE learning_note ADD COLUMN IF NOT EXISTS source_title VARCHAR(500) COMMENT '来源标题提示，非权限关联';
ALTER TABLE learning_note ADD COLUMN IF NOT EXISTS import_key VARCHAR(64) COMMENT '服务端迁移指纹';
UPDATE learning_note SET source_type='CHAPTER' WHERE chapter_id IS NOT NULL OR path_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_note_import ON learning_note(user_id,import_key);
CREATE INDEX IF NOT EXISTS idx_note_owner_updated ON learning_note(user_id,deleted,update_time,id);
CREATE TABLE IF NOT EXISTS learning_note_version (id BIGINT AUTO_INCREMENT PRIMARY KEY,note_id BIGINT NOT NULL COMMENT '所属笔记',user_id BIGINT NOT NULL COMMENT '修改者',revision BIGINT NOT NULL COMMENT '修订号',title VARCHAR(200),content LONGTEXT,tags VARCHAR(2000),source_title VARCHAR(500),chapter_id BIGINT,path_id BIGINT,status VARCHAR(20),note_deleted INT NOT NULL DEFAULT 0,reason VARCHAR(40) COMMENT '版本来源',create_time TIMESTAMP NOT NULL,UNIQUE(note_id,revision));
