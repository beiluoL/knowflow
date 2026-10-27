-- 单列兼容升级：旧 VARCHAR(20) 无法容纳 26 字符设计令牌默认值。
-- 仅扩容已有短列，保留标签、默认值和逻辑删除；重复执行不缩短已扩展的列。
SET @kf_tag_color_ddl = IF((SELECT COUNT(*) FROM information_schema.columns
 WHERE table_schema=DATABASE() AND table_name='task_tag' AND column_name='color'
 AND character_maximum_length < 64)=1,
 'ALTER TABLE task_tag MODIFY COLUMN color VARCHAR(64) DEFAULT ''var(--kb-muted-foreground)'' COMMENT ''标签颜色，支持设计令牌与CSS颜色值''',
 'SELECT 1');
PREPARE kf_tag_color_stmt FROM @kf_tag_color_ddl;
EXECUTE kf_tag_color_stmt;
DEALLOCATE PREPARE kf_tag_color_stmt;
