-- 适用：已部署的 MySQL 8 生产库（新库直接执行 db/mysql/schema.sql 即可，无需本脚本）
-- 目的：为任务表增加「四象限（important/urgent）」与「看板阶段（stage）」所需列及索引。
-- 说明：列均带 DEFAULT 0，对存量数据零影响；使用 IF NOT EXISTS 守卫，可重复执行。

ALTER TABLE task
    ADD COLUMN IF NOT EXISTS important INT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS urgent    INT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS stage     INT DEFAULT 0;

-- MySQL 8 不支持 CREATE INDEX IF NOT EXISTS，改用存储过程守卫避免重复执行报错
DROP PROCEDURE IF EXISTS add_task_stage_index;
DELIMITER $$
CREATE PROCEDURE add_task_stage_index()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name   = 'task'
          AND index_name   = 'idx_task_stage'
    ) THEN
        CREATE INDEX idx_task_stage ON task (stage);
    END IF;
END$$
DELIMITER ;
CALL add_task_stage_index();
DROP PROCEDURE IF EXISTS add_task_stage_index;
