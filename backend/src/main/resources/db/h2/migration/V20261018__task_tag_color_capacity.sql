-- 仅扩容颜色列，不清空或覆盖既有标签；同一脚本可重复执行。
ALTER TABLE task_tag ALTER COLUMN color VARCHAR(64) DEFAULT 'var(--kb-muted-foreground)';
COMMENT ON COLUMN task_tag.color IS '标签颜色，支持设计令牌与CSS颜色值';
