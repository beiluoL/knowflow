-- 适用：已部署的 MySQL 8 生产库（新库直接执行 db/mysql/schema.sql 即可，无需本脚本）
-- 目的：Phase 0 数据模型收敛 —— ①补 6 张孤儿表（有实体无 DDL）②闪卡表补 SM-2 ease_factor 列
--       ③错题表补原题回溯列（question_id / code_question_id），打通 错题→原题→知识点→掌握度 闭环。
-- 说明：MySQL 8 不支持 ADD COLUMN IF NOT EXISTS / CREATE INDEX IF NOT EXISTS，
--       故用存储过程 + information_schema 守卫，确保脚本可重复执行而不报错。

-- ========== ① 6 张孤儿表（幂等建表） ==========

CREATE TABLE IF NOT EXISTS learning_note (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '用户ID（逻辑外键 sys_user.id）',
    chapter_id BIGINT COMMENT '关联章节ID（可空，表示通用笔记，逻辑外键 learning_chapter.id）',
    path_id BIGINT COMMENT '关联学习路径ID（逻辑外键 learning_path.id）',
    title VARCHAR(200) COMMENT '笔记标题',
    content LONGTEXT COMMENT '笔记正文（Markdown）',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    KEY idx_learning_note_user (user_id),
    KEY idx_learning_note_user_chapter (user_id, chapter_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS chapter_favorite (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '用户ID（逻辑外键 sys_user.id）',
    chapter_id BIGINT NOT NULL COMMENT '章节ID（逻辑外键 learning_chapter.id）',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    KEY idx_chapter_favorite_user_chapter (user_id, chapter_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_pet (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '用户ID（逻辑外键 sys_user.id）',
    name VARCHAR(50) DEFAULT '小鹰' COMMENT '宠物名称',
    level INT DEFAULT 1 COMMENT '等级',
    mood VARCHAR(20) DEFAULT '开心' COMMENT '心情',
    energy INT DEFAULT 80 COMMENT '体力值 0-100',
    exp INT DEFAULT 0 COMMENT '当前经验值',
    max_exp INT DEFAULT 100 COMMENT '当前等级经验上限',
    avatar VARCHAR(50) DEFAULT 'owl' COMMENT '宠物头像标识',
    total_focus_minutes INT DEFAULT 0 COMMENT '累计专注分钟',
    total_pomodoros INT DEFAULT 0 COMMENT '累计番茄数',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    KEY idx_user_pet_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS weekly_report (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '用户ID（逻辑外键 sys_user.id）',
    week_start DATE NOT NULL COMMENT '本周一日期',
    week_end DATE NOT NULL COMMENT '本周日日期',
    summary TEXT COMMENT 'AI 生成的周报摘要',
    achievements TEXT COMMENT 'JSON：本周成就列表',
    suggestions TEXT COMMENT 'JSON：下周学习建议',
    study_minutes INT DEFAULT 0 COMMENT '本周学习分钟',
    checkin_days INT DEFAULT 0 COMMENT '本周签到天数',
    flashcard_reviewed INT DEFAULT 0 COMMENT '本周闪卡复习数',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    UNIQUE KEY uk_weekly_report_user_week (user_id, week_start, deleted),
    KEY idx_weekly_report_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_learning_suggestion (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '用户ID（逻辑外键 sys_user.id）',
    suggestions LONGTEXT COMMENT 'JSON 数组字符串：[{title,desc,icon,path}]',
    period VARCHAR(10) DEFAULT 'week' COMMENT '周期：week/month',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    KEY idx_ai_learning_suggestion_user_period (user_id, period)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sys_user_pref (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '用户ID（逻辑外键 sys_user.id）',
    focus_minutes INT DEFAULT 25 COMMENT '默认专注时长（分钟）',
    short_break INT DEFAULT 5 COMMENT '短休时长（分钟）',
    long_break INT DEFAULT 15 COMMENT '长休时长（分钟）',
    rounds INT DEFAULT 4 COMMENT '每组工作次数',
    card_strategy VARCHAR(20) DEFAULT 'RANDOM' COMMENT '闪卡抽取策略：RANDOM/DIFFICULTY/DUE/WEAK',
    card_count INT DEFAULT 20 COMMENT '每次卡片数量（0 表示全部）',
    difficulty_filter VARCHAR(10) COMMENT '难度过滤：1/2/3 或空',
    theme VARCHAR(20) DEFAULT 'day' COMMENT '阅读主题：day/eye/night/parchment',
    font_size VARCHAR(10) DEFAULT 'md' COMMENT '字体大小：sm/md/lg/xl',
    sound_enabled INT DEFAULT 1 COMMENT '声音提醒：0 关 / 1 开',
    notification_enabled INT DEFAULT 1 COMMENT '桌面通知：0 关 / 1 开',
    reminder_time VARCHAR(5) COMMENT '每日学习提醒时间 HH:mm',
    white_noise VARCHAR(20) COMMENT '白噪音：rain/cafe/wave 或空',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    KEY idx_sys_user_pref_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========== ② 闪卡表补 SM-2 ease_factor 列 + 错题表补回溯列（守卫式加列/索引） ==========

DROP PROCEDURE IF EXISTS migrate_phase0_srs_mistake;
DELIMITER $$
CREATE PROCEDURE migrate_phase0_srs_mistake()
BEGIN
    -- learning_flashcard.ease_factor
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'learning_flashcard' AND column_name = 'ease_factor'
    ) THEN
        ALTER TABLE learning_flashcard ADD COLUMN ease_factor INT DEFAULT 250 COMMENT 'SM-2 难度系数（放大100倍存储，默认250即2.5）';
    END IF;

    -- learning_mistake.question_id
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'learning_mistake' AND column_name = 'question_id'
    ) THEN
        ALTER TABLE learning_mistake ADD COLUMN question_id BIGINT COMMENT '原测验题ID（逻辑外键 quiz_question.id，可空兼容旧数据）';
    END IF;

    -- learning_mistake.code_question_id
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'learning_mistake' AND column_name = 'code_question_id'
    ) THEN
        ALTER TABLE learning_mistake ADD COLUMN code_question_id BIGINT COMMENT '原代码题ID（逻辑外键 code_question.id，可空兼容旧数据）';
    END IF;

    -- learning_mistake 索引
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'learning_mistake' AND index_name = 'idx_learning_mistake_user'
    ) THEN
        CREATE INDEX idx_learning_mistake_user ON learning_mistake (user_id);
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'learning_mistake' AND index_name = 'idx_learning_mistake_question'
    ) THEN
        CREATE INDEX idx_learning_mistake_question ON learning_mistake (question_id);
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'learning_mistake' AND index_name = 'idx_learning_mistake_code'
    ) THEN
        CREATE INDEX idx_learning_mistake_code ON learning_mistake (code_question_id);
    END IF;
END$$
DELIMITER ;
CALL migrate_phase0_srs_mistake();
DROP PROCEDURE IF EXISTS migrate_phase0_srs_mistake;
