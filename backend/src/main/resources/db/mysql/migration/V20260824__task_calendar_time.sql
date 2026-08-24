-- 适用：已部署的 MySQL 8 生产库（新库直接执行 db/mysql/schema.sql 即可，无需本脚本）
-- 目的：为任务表增加「日历起止时间（start_time / end_time）」列及索引，支撑日历定时事件。
-- 说明：列均允许 NULL 且带 DEFAULT NULL，对存量数据零影响。
--       MySQL 8 不支持 ADD COLUMN IF NOT EXISTS / CREATE INDEX IF NOT EXISTS，
--       故用存储过程 + information_schema 守卫，确保脚本可重复执行而不报错。

DROP PROCEDURE IF EXISTS migrate_task_calendar_time;
DELIMITER $$
CREATE PROCEDURE migrate_task_calendar_time()
BEGIN
    -- 1. start_time
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'task' AND column_name = 'start_time'
    ) THEN
        ALTER TABLE task ADD COLUMN start_time TIMESTAMP NULL DEFAULT NULL COMMENT '定时事件开始时间';
    END IF;
    -- 2. end_time
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'task' AND column_name = 'end_time'
    ) THEN
        ALTER TABLE task ADD COLUMN end_time TIMESTAMP NULL DEFAULT NULL COMMENT '定时事件结束时间';
    END IF;
    -- 3. 索引
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'task' AND index_name = 'idx_task_start_time'
    ) THEN
        CREATE INDEX idx_task_start_time ON task (start_time);
    END IF;
END$$
DELIMITER ;
CALL migrate_task_calendar_time();
DROP PROCEDURE IF EXISTS migrate_task_calendar_time;
