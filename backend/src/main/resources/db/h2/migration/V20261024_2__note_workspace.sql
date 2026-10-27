-- 笔记V2：组织与关系增量迁移，不删除正文。自动入口仍为NoteSchema.migrate。
ALTER TABLE learning_note ADD COLUMN IF NOT EXISTS folder_id BIGINT COMMENT '所属笔记目录';
ALTER TABLE learning_note ADD COLUMN IF NOT EXISTS favorite BOOLEAN NOT NULL DEFAULT FALSE COMMENT '个人收藏状态';
ALTER TABLE learning_note ADD COLUMN IF NOT EXISTS source_doc_id BIGINT COMMENT '来源文档ID，读取时复验权限';
ALTER TABLE learning_note_version ADD COLUMN IF NOT EXISTS folder_id BIGINT COMMENT '所属笔记目录';
ALTER TABLE learning_note_version ADD COLUMN IF NOT EXISTS favorite BOOLEAN NOT NULL DEFAULT FALSE COMMENT '个人收藏状态';
ALTER TABLE learning_note_version ADD COLUMN IF NOT EXISTS source_doc_id BIGINT COMMENT '来源文档ID，读取时复验权限';
CREATE TABLE IF NOT EXISTS learning_note_folder(id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT NOT NULL COMMENT '所属用户',parent_id BIGINT COMMENT '父文件夹',name VARCHAR(100) NOT NULL COMMENT '目录名',revision BIGINT NOT NULL DEFAULT 1 COMMENT '目录修订号',INDEX idx_note_folder_owner(user_id,parent_id));
CREATE TABLE IF NOT EXISTS learning_note_tag(id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT NOT NULL,name VARCHAR(100) NOT NULL COMMENT '标签名',name_key VARCHAR(100) NOT NULL COMMENT '规范化唯一名',UNIQUE(user_id,name_key));
CREATE TABLE IF NOT EXISTS learning_note_tag_ref(user_id BIGINT NOT NULL,note_id BIGINT NOT NULL,tag_id BIGINT NOT NULL,PRIMARY KEY(note_id,tag_id),INDEX idx_note_tag_owner(user_id,tag_id,note_id));
CREATE TABLE IF NOT EXISTS learning_note_relation(id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT NOT NULL,note_id BIGINT NOT NULL,target_type VARCHAR(10) NOT NULL COMMENT 'DOC或NOTE',target_id BIGINT NOT NULL,UNIQUE(user_id,note_id,target_type,target_id));
