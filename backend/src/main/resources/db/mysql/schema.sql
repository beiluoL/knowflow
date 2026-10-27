-- ============================================================
-- MySQL 8.x 方言建表脚本（由 db/h2/schema.sql 转换生成）
-- 与 H2 版差异：去除 H2 专有 IF NOT EXISTS 索引/约束语法、
-- 建表补 InnoDB + utf8mb4、TEXT 提升为 LONGTEXT。
-- 注意：索引采用先建后判策略，重复执行前请确保库为空或使用迁移工具。
-- ============================================================

-- 用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100),
    password VARCHAR(255) DEFAULT '',
    nickname VARCHAR(50),
    avatar VARCHAR(255),
    role VARCHAR(20) DEFAULT 'USER',
    total_study_hours DECIMAL(10,2) DEFAULT 0,
    read_docs_count INT DEFAULT 0,
    streak_days INT DEFAULT 0,
    favorite_count INT DEFAULT 0,
    level INT DEFAULT 1,
    exp INT DEFAULT 0,
    energy INT DEFAULT 100,
    provider VARCHAR(20),
    provider_uid VARCHAR(100),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 文档表
CREATE TABLE IF NOT EXISTS doc_document (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content LONGTEXT,
    summary VARCHAR(500),
    cover VARCHAR(255),
    icon VARCHAR(255),
    file_name VARCHAR(500) COMMENT '原始文件名（上传时保留，含扩展名）',
    file_url VARCHAR(500) COMMENT '原始文件访问路径（/uploads/...），用于原文下载/预览',
    file_size BIGINT COMMENT '原始文件字节大小',
    category_id BIGINT,
    category_path VARCHAR(500),
    tags VARCHAR(500),
    view_count INT DEFAULT 0,
    read_count INT DEFAULT 0,
    favorite_count INT DEFAULT 0,
    word_count INT DEFAULT 0,
    difficulty INT DEFAULT 1,
    sort_order INT DEFAULT 0,
    status INT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    source_path VARCHAR(500) COMMENT '导入来源相对路径（Obsidian/本地目录相对路径），用于增量去重',
    content_hash VARCHAR(64) COMMENT '内容哈希（SHA-256），用于增量去重'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 分类表
CREATE TABLE IF NOT EXISTS doc_category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    code VARCHAR(50),
    parent_id BIGINT DEFAULT 0,
    icon VARCHAR(255),
    description VARCHAR(500),
    sort_order INT DEFAULT 0,
    doc_count INT DEFAULT 0,
    status INT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    owner_id BIGINT COMMENT '知识库所有者用户ID（逻辑外键sys_user.id）'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 收藏表
CREATE TABLE IF NOT EXISTS doc_favorite (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    doc_id BIGINT NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 阅读进度表
CREATE TABLE IF NOT EXISTS doc_read_progress (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    doc_id BIGINT NOT NULL,
    progress DECIMAL(5,2) DEFAULT 0,
    read_seconds INT DEFAULT 0,
    last_read_time TIMESTAMP NULL DEFAULT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 对话表
CREATE TABLE IF NOT EXISTS chat_conversation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(200),
    message_count INT DEFAULT 0,
    last_message VARCHAR(4000),
    summary LONGTEXT,
    summary_updated_at DATETIME NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 消息表
CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content LONGTEXT,
    doc_references VARCHAR(1000),
    token_count INT DEFAULT 0,
    truncated INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 学习路径表
CREATE TABLE IF NOT EXISTS learning_path (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    cover VARCHAR(255),
    level VARCHAR(20),
    chapter_count INT DEFAULT 0,
    total_duration INT DEFAULT 0,
    enrolled_count INT DEFAULT 0,
    sort_order INT DEFAULT 0,
    status INT DEFAULT 1,
    owner_user_id BIGINT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_lp_owner ON learning_path (owner_user_id);

-- 章节表
CREATE TABLE IF NOT EXISTS learning_chapter (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    path_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content LONGTEXT,
    sort_order INT DEFAULT 0,
    duration INT DEFAULT 0,
    doc_ids LONGTEXT,
    flashcard_ids LONGTEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    prerequisite_chapter_ids VARCHAR(500) DEFAULT NULL COMMENT '前置章节ID列表，逗号分隔（如 "1,3,5"），全部完成后该章节才可学习'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 用户学习路径进度表
CREATE TABLE IF NOT EXISTS learning_user_path (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    path_id BIGINT NOT NULL,
    progress DECIMAL(5,2) DEFAULT 0,
    completed_chapters INT DEFAULT 0,
    enroll_time TIMESTAMP NULL DEFAULT NULL,
    last_study_time TIMESTAMP NULL DEFAULT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 闪卡表（用户维度隔离，支持关联知识库/文档来源、间隔重复复习算法）
CREATE TABLE IF NOT EXISTS learning_flashcard (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '所属用户ID（逻辑外键sys_user.id）',
    path_id BIGINT COMMENT '归属学习路径ID（逻辑外键learning_path.id，可为空）',
    chapter_id BIGINT COMMENT '归属章节ID（逻辑外键learning_chapter.id，可为空）',
    category_id BIGINT COMMENT '关联知识库/分类ID（逻辑外键doc_category.id）',
    doc_id BIGINT COMMENT '来源文档ID（逻辑外键doc_document.id）',
    front LONGTEXT NOT NULL COMMENT '正面：问题/概念',
    back LONGTEXT NOT NULL COMMENT '背面：答案/解释',
    category VARCHAR(50) COMMENT '用户自定义分类标签',
    difficulty INT DEFAULT 1 COMMENT '难度：1简单 2中等 3困难',
    tags VARCHAR(500) COMMENT '逗号分隔的自定义标签',
    source_type VARCHAR(20) DEFAULT 'MANUAL' COMMENT '来源：MANUAL/AI_DOC/AI_KB/IMPORT',
    review_count INT DEFAULT 0 COMMENT '已复习次数',
    review_interval INT DEFAULT 0 COMMENT '当前复习间隔（天，间隔重复算法）',
    ease_factor INT DEFAULT 250 COMMENT 'SM-2 难度系数（放大100倍存储，默认250即2.5）',
    next_review_time TIMESTAMP NULL DEFAULT NULL COMMENT '下次应复习时间',
    last_review_time TIMESTAMP NULL DEFAULT NULL COMMENT '上次复习时间',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 学习任务表
CREATE TABLE IF NOT EXISTS learning_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(500),
    type VARCHAR(20),
    target_id BIGINT,
    exp_reward INT DEFAULT 0,
    energy_cost INT DEFAULT 0,
    deadline TIMESTAMP NULL DEFAULT NULL,
    status INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 任务清单（Things3 式：领域 / 项目 / 清单层级）
CREATE TABLE IF NOT EXISTS task_list (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    kind VARCHAR(20),
    parent_id BIGINT DEFAULT 0,
    color VARCHAR(20),
    icon VARCHAR(40),
    sort_order INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 任务（Things3 式：子任务 parent_id + 智能列表 scheduled_date/someday/status）
CREATE TABLE IF NOT EXISTS task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    list_id BIGINT,
    parent_id BIGINT DEFAULT 0,
    title VARCHAR(255) NOT NULL,
    notes TEXT,
    learning_plan_ref VARCHAR(36),
    learning_metadata TEXT,
    status INT DEFAULT 0,
    scheduled_date DATE,
    due_date DATE,
    someday INT DEFAULT 0,
    important INT DEFAULT 0,
    urgent INT DEFAULT 0,
    stage INT DEFAULT 0,
    sort_order INT DEFAULT 0,
    start_time TIMESTAMP NULL DEFAULT NULL,
    end_time   TIMESTAMP NULL DEFAULT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_task_user     ON task (user_id);
CREATE INDEX idx_task_list     ON task (list_id);
CREATE INDEX idx_task_parent   ON task (parent_id);
CREATE INDEX idx_task_status   ON task (status);
CREATE INDEX idx_task_start_time ON task (start_time);
CREATE INDEX idx_task_stage    ON task (stage);
CREATE INDEX idx_tasklist_user ON task_list (user_id);
CREATE INDEX idx_tasklist_par  ON task_list (parent_id);

-- ---------- 日历：自定义纪念日（Memorial）----------
-- 两类：fixed（固定日期，仅当年生效）/ yearly（每年重复，按 MM-dd 展开）
CREATE TABLE IF NOT EXISTS calendar_memorial (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(10) NOT NULL,
    month_day VARCHAR(5) NOT NULL,
    fixed_date DATE NULL,
    color VARCHAR(20),
    note VARCHAR(500),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_cm_user ON calendar_memorial (user_id);
CREATE INDEX idx_cm_deleted ON calendar_memorial (deleted);

-- ============================================================
-- 表间关系说明（遵循《阿里巴巴 Java 开发手册》：不使用物理外键）
-- ------------------------------------------------------------
-- 【强制】不得使用外键与级联，一切外键概念必须在应用层解决。
--   说明：以「学生-成绩」为例，student_id 是主键，那么成绩表中的
--   student_id 是逻辑外键。物理外键会带来更新主表主键时的级联问题、
--   增加数据库工作量、影响插入速度、易造成死锁，并使分库分表困难。
-- 本库所有表间关联均为「逻辑外键」：仅存储关联列（如 category_id、
--   user_id、path_id 等），关系的完整性由 Service 层业务代码保证，
--   数据库不建立任何 FOREIGN KEY 约束。
-- doc_category.parent_id 使用 0 作为顶级哨兵值（非 NULL），无自引用外键。
-- ============================================================

-- ============================================================
-- 索引设计
-- 目标：为逻辑外键关联列建立普通索引（阿里规范要求逻辑外键列必须有索引），
--       并加速逻辑删除过滤、状态过滤与常用列表排序。
-- ============================================================
-- 用户
CREATE INDEX idx_user_role    ON sys_user (role);
CREATE INDEX idx_user_deleted ON sys_user (deleted);
-- 邮箱唯一（防止重复注册/改资料，与 Service 层查重双保险；种子 email 各异，无冲突）
CREATE UNIQUE INDEX uk_user_email ON sys_user (email);
-- 文档
CREATE INDEX idx_doc_category ON doc_document (category_id);
CREATE INDEX idx_doc_status   ON doc_document (status);
CREATE INDEX idx_doc_deleted  ON doc_document (deleted);
CREATE INDEX idx_doc_ctime    ON doc_document (create_time);
-- 分类
CREATE INDEX idx_cat_parent   ON doc_category (parent_id);
CREATE INDEX idx_cat_status   ON doc_category (status);
-- 收藏 / 阅读进度
CREATE INDEX idx_fav_user     ON doc_favorite (user_id);
CREATE INDEX idx_fav_doc      ON doc_favorite (doc_id);
CREATE INDEX idx_rp_user      ON doc_read_progress (user_id);
CREATE INDEX idx_rp_doc       ON doc_read_progress (doc_id);
-- 联合唯一约束：防止同一用户对同一文档重复收藏/重复进度记录（并发下先查后写会重复插入）
CREATE UNIQUE INDEX uk_fav_user_doc ON doc_favorite (user_id, doc_id);
CREATE UNIQUE INDEX uk_rp_user_doc ON doc_read_progress (user_id, doc_id);
-- 对话 / 消息
CREATE INDEX idx_conv_user    ON chat_conversation (user_id);
CREATE INDEX idx_conv_deleted ON chat_conversation (deleted);
CREATE INDEX idx_msg_conv     ON chat_message (conversation_id);
-- 学习路径 / 章节 / 进度 / 闪卡 / 任务
CREATE INDEX idx_path_status  ON learning_path (status);
CREATE INDEX idx_chap_path    ON learning_chapter (path_id);
CREATE INDEX idx_up_user      ON learning_user_path (user_id);
CREATE INDEX idx_up_path      ON learning_user_path (path_id);
-- 用户章节完成记录表（保证 completeChapter 幂等：同一用户对同一章节只计一次）
CREATE TABLE IF NOT EXISTS learning_user_chapter (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    path_id BIGINT NOT NULL,
    chapter_id BIGINT NOT NULL,
    complete_time TIMESTAMP NULL DEFAULT NULL,
    reading_anchor VARCHAR(255) DEFAULT NULL COMMENT '章节阅读标题位置，不表示掌握',
    reading_revision BIGINT NOT NULL DEFAULT 0 COMMENT '阅读位置乐观锁版本',
    reading_saved_at TIMESTAMP NULL DEFAULT NULL COMMENT '阅读位置保存时间',
    reading_content_hash VARCHAR(64) DEFAULT NULL COMMENT '保存时原章节内容摘要',
    reading_presentation_hash VARCHAR(64) DEFAULT NULL COMMENT '用户阅读展示版本指纹，仅用于本人书签坐标',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    video_progress DECIMAL(5,2) DEFAULT 0 COMMENT '视频观看进度百分比(0-100)，达标后允许完成章节'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_uc_user_chapter ON learning_user_chapter (user_id);
CREATE INDEX idx_uc_chapter_id   ON learning_user_chapter (chapter_id);
CREATE UNIQUE INDEX uk_uc_user_chapter ON learning_user_chapter (user_id, chapter_id);

-- 自定义图标表
CREATE TABLE IF NOT EXISTS sys_icon (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(20) DEFAULT 'custom',
    content LONGTEXT NOT NULL,
    color VARCHAR(20),
    user_id BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_icon_user ON sys_icon (user_id);
CREATE INDEX idx_icon_type ON sys_icon (type);
-- 闪卡索引（遵循阿里规范：所有逻辑外键列必须建索引）
CREATE INDEX idx_fc_user        ON learning_flashcard (user_id);
CREATE INDEX idx_fc_path        ON learning_flashcard (path_id);
CREATE INDEX idx_fc_chap        ON learning_flashcard (chapter_id);
CREATE INDEX idx_fc_category    ON learning_flashcard (category_id);
CREATE INDEX idx_fc_doc         ON learning_flashcard (doc_id);
CREATE INDEX idx_fc_next_review ON learning_flashcard (next_review_time);
CREATE INDEX idx_fc_deleted     ON learning_flashcard (deleted);
CREATE INDEX idx_learning_task_user    ON learning_task (user_id);
CREATE INDEX idx_learning_task_status  ON learning_task (status);

-- 错题表
CREATE TABLE IF NOT EXISTS learning_mistake (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    question LONGTEXT NOT NULL,
    wrong_answer LONGTEXT,
    correct_answer LONGTEXT,
    category VARCHAR(50),
    difficulty INT DEFAULT 1,
    question_id BIGINT COMMENT '原测验题ID（逻辑外键 quiz_question.id，可空兼容旧数据）',
    code_question_id BIGINT COMMENT '原代码题ID（逻辑外键 code_question.id，可空兼容旧数据）',
    review_count INT DEFAULT 0,
    last_review_time TIMESTAMP NULL DEFAULT NULL,
    mastered INT DEFAULT 0,
    source VARCHAR(50),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_learning_mistake_user ON learning_mistake (user_id);
CREATE INDEX idx_learning_mistake_question ON learning_mistake (question_id);
CREATE INDEX idx_learning_mistake_code ON learning_mistake (code_question_id);

-- 社区帖子表
CREATE TABLE IF NOT EXISTS community_post (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content LONGTEXT,
    category VARCHAR(50),
    tags VARCHAR(500),
    like_count INT DEFAULT 0,
    comment_count INT DEFAULT 0,
    view_count INT DEFAULT 0,
    is_essence INT DEFAULT 0,
    status INT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 帖子点赞关系表（F-10：点赞幂等 + 可取消，用户-帖子联合唯一）
CREATE TABLE IF NOT EXISTS community_post_like (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    CONSTRAINT uk_post_like UNIQUE (post_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 社区评论表（F-06：支持一级回复、评论点赞与回复计数）
CREATE TABLE IF NOT EXISTS community_comment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    parent_id BIGINT DEFAULT 0 COMMENT '0=顶级评论，非0=回复的顶级评论ID',
    reply_to_user_id BIGINT DEFAULT 0 COMMENT '被回复用户ID，0=直接回复顶级评论',
    content LONGTEXT NOT NULL,
    like_count INT DEFAULT 0,
    reply_count INT DEFAULT 0 COMMENT '回复数，仅 parent_id=0 的顶级评论维护',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 评论点赞关系表（F-06：评论点赞幂等 + 可取消，用户-评论联合唯一）
CREATE TABLE IF NOT EXISTS community_comment_like (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    comment_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    CONSTRAINT uk_comment_like UNIQUE (comment_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 消息通知表
CREATE TABLE IF NOT EXISTS sys_notification (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type VARCHAR(30) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content VARCHAR(1000),
    is_read INT DEFAULT 0,
    related_id BIGINT,
    related_type VARCHAR(30),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 错题索引
CREATE INDEX idx_mistake_user    ON learning_mistake (user_id);
CREATE INDEX idx_mistake_category ON learning_mistake (category);
CREATE INDEX idx_mistake_mastered ON learning_mistake (mastered);
CREATE INDEX idx_mistake_deleted  ON learning_mistake (deleted);

-- 社区帖子索引
CREATE INDEX idx_post_user     ON community_post (user_id);
CREATE INDEX idx_post_category ON community_post (category);
CREATE INDEX idx_post_status   ON community_post (status);
CREATE INDEX idx_post_essence  ON community_post (is_essence);
CREATE INDEX idx_post_ctime     ON community_post (create_time);
CREATE INDEX idx_post_deleted   ON community_post (deleted);

-- 帖子点赞索引（逻辑外键列建索引，阿里规约）
CREATE INDEX idx_post_like_user ON community_post_like (user_id);

-- 社区评论索引
CREATE INDEX idx_comment_post    ON community_comment (post_id);
CREATE INDEX idx_comment_user    ON community_comment (user_id);
CREATE INDEX idx_comment_parent  ON community_comment (parent_id);
CREATE INDEX idx_comment_deleted ON community_comment (deleted);

-- 评论点赞索引（逻辑外键列建索引，阿里规约）
CREATE INDEX idx_comment_like_user ON community_comment_like (user_id);

-- 消息通知索引
CREATE INDEX idx_notif_user   ON sys_notification (user_id);
CREATE INDEX idx_notif_type   ON sys_notification (type);
CREATE INDEX idx_notif_read   ON sys_notification (is_read);
CREATE INDEX idx_notif_deleted ON sys_notification (deleted);

-- 用户 AI 配置表
CREATE TABLE IF NOT EXISTS sys_user_ai_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    provider VARCHAR(50) NOT NULL COMMENT '模型提供商: deepseek / siliconflow / openai / ollama / vllm / localai / custom 等',
    api_key VARCHAR(500) NOT NULL COMMENT '用户自己的 API Key；本地模型约定填 local',
    api_secret VARCHAR(500) COMMENT 'API Secret，仅文心 qianfan 等双密钥协议需要',
    base_url VARCHAR(255) COMMENT '自定义 API 地址（留空用默认）',
    model VARCHAR(100) COMMENT '默认模型名',
    is_active INT DEFAULT 1 COMMENT '是否启用（通用 Chat 使用）: 1 启用 / 0 禁用',
    provider_type VARCHAR(16) DEFAULT 'CLOUD' COMMENT '提供商类型: CLOUD / LOCAL',
    capability VARCHAR(16) DEFAULT 'STANDARD' COMMENT '能力等级: LIGHT / STANDARD / POWERFUL',
    display_name VARCHAR(100) COMMENT '用户自定义显示名（如 我的本地 Llama3）',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
-- 编程 Agent 场景下同一用户可有多条配置，故不再对 user_id 设唯一约束；
-- 通用 Chat 通过 is_active=1 标记当前使用的唯一配置，由应用层 clearOtherActive 保证。
CREATE INDEX idx_user_ai_config_user ON sys_user_ai_config (user_id, deleted);

-- ===== 编程 Agent 多会话与调用日志 =====
CREATE TABLE IF NOT EXISTS agent_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '所属用户',
    title VARCHAR(200) NOT NULL COMMENT '会话标题（取首条问题前 30 字）',
    config_id BIGINT COMMENT '使用的模型配置ID（可空，表示用默认）',
    project_dir VARCHAR(500) COMMENT '项目目录名（File System Access 句柄名，仅展示用）',
    message_count INT DEFAULT 0 COMMENT '消息条数（冗余字段，便于列表展示）',
    last_message VARCHAR(500) COMMENT '最后一条消息摘要',
    context_window INT DEFAULT 6000 COMMENT '上下文窗口上限（token），用于摘要压缩阈值',
    agent_mode VARCHAR(20) DEFAULT 'chat' COMMENT '会话模式 chat / agent',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_agent_session_user ON agent_session (user_id, deleted);

CREATE TABLE IF NOT EXISTS agent_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL COMMENT '所属会话',
    user_id BIGINT NOT NULL COMMENT '冗余用户ID，便于查询',
    role VARCHAR(20) NOT NULL COMMENT 'system / user / assistant / tool',
    content LONGTEXT COMMENT '消息内容',
    file_path VARCHAR(500) COMMENT '附带的文件路径（user 消息可选）',
    message_type VARCHAR(20) DEFAULT 'normal' COMMENT 'normal / tool_call / tool_result / summary',
    tool_call_id VARCHAR(40) COMMENT '工具调用ID（tool 角色回灌时关联）',
    tool_name VARCHAR(40) COMMENT '触发的工具名（可视化用）',
    parent_id BIGINT COMMENT '父消息ID（多轮/工具循环溯源）',
    token_count INT DEFAULT 0 COMMENT '预估 token 数',
    latency_ms INT COMMENT 'assistant 消息的响应耗时（毫秒）',
    is_error INT DEFAULT 0 COMMENT '是否为错误消息: 0 否 / 1 是',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_agent_message_session ON agent_message (session_id, create_time);
CREATE INDEX idx_agent_message_user ON agent_message (user_id, deleted);

-- 模型调用日志：用于监测仪表盘（响应时间、调用次数、错误率）
CREATE TABLE IF NOT EXISTS agent_call_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    config_id BIGINT COMMENT '模型配置ID',
    provider VARCHAR(50) COMMENT '提供商（冗余，便于按提供商聚合）',
    session_id BIGINT COMMENT '所属会话',
    success INT NOT NULL DEFAULT 1 COMMENT '调用是否成功: 1 成功 / 0 失败',
    latency_ms INT COMMENT '响应耗时（毫秒）',
    token_in INT DEFAULT 0 COMMENT '输入 token 数',
    token_out INT DEFAULT 0 COMMENT '输出 token 数',
    error_msg VARCHAR(1000) COMMENT '失败时的错误信息',
    score DOUBLE COMMENT '输出准确率评估得分 0~1（P3 评估闭环写入，可空）',
    intent VARCHAR(20) COMMENT '评估关联的意图类型 generate/modify/explain/debug/chat（P3 评估闭环写入，可空）',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_agent_call_log_user ON agent_call_log (user_id, create_time);
CREATE INDEX idx_agent_call_log_config ON agent_call_log (config_id, create_time);
CREATE INDEX idx_agent_call_log_intent ON agent_call_log (intent, create_time);

-- 工具启用配置：每个用户对各内置工具的启用状态与写授权
CREATE TABLE IF NOT EXISTS agent_tool_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '所属用户',
    tool_name VARCHAR(40) NOT NULL COMMENT '工具名（逻辑外键 agent_tool.name）',
    enabled INT DEFAULT 1 COMMENT '是否启用: 0 禁用 / 1 启用',
    allow_write INT DEFAULT 0 COMMENT '是否允许写操作（WRITE 工具需 true 才执行）',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE UNIQUE INDEX uk_agent_tool_config_user_tool ON agent_tool_config (user_id, tool_name, deleted);

-- 工具调用链明细：每次工具调用的入参/结果/状态/耗时，用于调用链路可视化
CREATE TABLE IF NOT EXISTS agent_tool_call (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL COMMENT '所属会话（逻辑外键 agent_session.id）',
    message_id BIGINT COMMENT '关联消息（逻辑外键 agent_message.id）',
    tool_name VARCHAR(40) NOT NULL COMMENT '工具名',
    permission VARCHAR(20) COMMENT '权限等级 SAFE/WRITE/DANGEROUS',
    args_json LONGTEXT COMMENT '入参 JSON',
    result_json LONGTEXT COMMENT '出参/错误 JSON',
    status VARCHAR(20) COMMENT 'success / failed / cancelled',
    latency_ms BIGINT COMMENT '耗时（毫秒）',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_agent_tool_call_session ON agent_tool_call (session_id, create_time);

-- 自定义工作流：用户预设 prompt 模板 + 触发条件，由 Agent 编排入口注入
CREATE TABLE IF NOT EXISTS agent_workflow (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '所属用户（逻辑外键 sys_user.id）',
    name VARCHAR(100) NOT NULL COMMENT '工作流名称',
    trigger_type VARCHAR(20) NOT NULL COMMENT 'intent(按意图触发)/keyword(关键词)/manual(手动)',
    trigger_value VARCHAR(200) COMMENT '意图类型或触发关键词，逗号分隔；manual 可空',
    prompt_template LONGTEXT NOT NULL COMMENT '注入的系统/用户 prompt 模板，支持 {input}/{file}/{tree} 占位',
    enabled INT DEFAULT 1 COMMENT '是否启用: 0 禁用 / 1 启用',
    sort_order INT DEFAULT 0 COMMENT '排序，越小越优先',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_agent_workflow_user ON agent_workflow (user_id, deleted);

-- Ollama 本地模型配置表（持久化服务地址、默认模型、参数预设）
CREATE TABLE IF NOT EXISTS ollama_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '所属用户',
    base_url VARCHAR(255) DEFAULT 'http://localhost:11434' COMMENT 'Ollama 服务地址',
    default_model VARCHAR(100) COMMENT '默认模型名',
    temperature DOUBLE DEFAULT 0.7 COMMENT '温度预设',
    top_p DOUBLE DEFAULT 0.9 COMMENT 'Top-P 预设',
    max_tokens INT DEFAULT 4000 COMMENT '最大 Token 预设',
    timeout_seconds INT DEFAULT 60 COMMENT '连接超时（秒）',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_ollama_config_user ON ollama_config (user_id, deleted);

-- 代码练习题目表（B 端题库管理 + C 端代码练习共用）
CREATE TABLE IF NOT EXISTS code_question (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL COMMENT '题目标题',
    description LONGTEXT NOT NULL COMMENT '题目描述（多行文本）',
    difficulty INT DEFAULT 0 COMMENT '难度：0 简单 / 1 中等 / 2 困难',
    language VARCHAR(20) DEFAULT 'javascript' COMMENT '主语言：javascript/typescript/python/java/sql',
    tags VARCHAR(500) COMMENT '题目标签，逗号分隔',
    hint LONGTEXT COMMENT '题目提示',
    example_input LONGTEXT COMMENT '输入示例',
    example_output LONGTEXT COMMENT '输出示例',
    code_template LONGTEXT COMMENT '函数签名模板（编辑器初始内容）',
    test_cases LONGTEXT COMMENT '测试用例 JSON 数组：[{input, expected}]',
    solution_hint LONGTEXT COMMENT '预期解法关键词，用于 AI 回答提示',
    duration INT DEFAULT 30 COMMENT '建议做题时长（分钟）',
    sort_order INT DEFAULT 0 COMMENT '排序值，越小越靠前',
    status INT DEFAULT 1 COMMENT '状态：0 草稿 / 1 已发布',
    pass_count INT DEFAULT 0 COMMENT '通过次数',
    submit_count INT DEFAULT 0 COMMENT '提交次数',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_cq_status    ON code_question (status);
CREATE INDEX idx_cq_difficulty ON code_question (difficulty);
CREATE INDEX idx_cq_language  ON code_question (language);
CREATE INDEX idx_cq_sort      ON code_question (sort_order);

-- ============ 知识库成员与权限 ============

-- 分类（知识库）增加 owner_id 字段（逻辑外键 → sys_user.id），用于标识创建者/拥有者

CREATE INDEX idx_cat_owner ON doc_category (owner_id);

-- 知识库成员表：多对多关联，每个成员持有角色与权限
CREATE TABLE IF NOT EXISTS kb_member (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    category_id BIGINT NOT NULL COMMENT '知识库ID（逻辑外键doc_category.id）',
    user_id BIGINT COMMENT '用户ID（逻辑外键sys_user.id）；邮箱邀请未注册时为 NULL',
    role VARCHAR(20) NOT NULL DEFAULT 'READER' COMMENT '成员角色：OWNER / EDITOR / READER',
    invite_code VARCHAR(50) COMMENT '邀请码（可选，用于未注册邮箱邀请）',
    invite_email VARCHAR(100) COMMENT '邀请目标邮箱（未注册用户邀请时记录）',
    status INT DEFAULT 1 COMMENT '状态：0 已移除 / 1 生效',
    join_time TIMESTAMP NULL DEFAULT NULL COMMENT '加入时间',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
-- 联合唯一：一个已注册用户在一个知识库中只能有一条有效成员记录（user_id 为 NULL 不参与唯一冲突）
CREATE UNIQUE INDEX uk_kb_member_cat_user ON kb_member (category_id, user_id, deleted);
CREATE INDEX idx_kb_member_category ON kb_member (category_id);
CREATE INDEX idx_kb_member_user ON kb_member (user_id);
CREATE INDEX idx_kb_member_role ON kb_member (role);
CREATE INDEX idx_kb_member_status ON kb_member (status);
CREATE INDEX idx_kb_member_invite_email ON kb_member (invite_email);

-- ========== 题库管理：支持多题型的智能出题 ==========
CREATE TABLE IF NOT EXISTS quiz_question (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    title VARCHAR(500) NOT NULL COMMENT '题目标题',
    content LONGTEXT NOT NULL COMMENT '题干内容（支持 Markdown）',
    question_type VARCHAR(24) NOT NULL DEFAULT 'SINGLE_CHOICE' COMMENT '题型：SINGLE_CHOICE/MULTIPLE_CHOICE/FILL_BLANK/TRUE_FALSE/SHORT_ANSWER/CODE',
    options LONGTEXT COMMENT '选项 JSON 数组（选择题用），如 ["选项A","选项B","选项C","选项D"]',
    answer LONGTEXT COMMENT '正确答案：选择题填选项索引(如 "0" 或 "0,2")，填空题填答案文本，判断题填 true/false；CODE 题可空',
    explanation LONGTEXT COMMENT '答案解析',
    difficulty INT DEFAULT 1 COMMENT '难度：1 简单 / 2 中等 / 3 困难',
    category_id BIGINT COMMENT '关联知识库ID（逻辑外键 doc_category.id）',
    doc_id BIGINT COMMENT '关联文档ID（逻辑外键 doc_document.id）',
    tags VARCHAR(500) COMMENT '标签，逗号分隔',
    source VARCHAR(20) DEFAULT 'MANUAL' COMMENT '来源：AI 生成 / MANUAL 手动新增',
    status INT DEFAULT 1 COMMENT '状态：0 草稿 / 1 已发布',
    sort_order INT DEFAULT 0 COMMENT '排序值',
    canonical_key VARCHAR(128) COMMENT '稳定语义键：跨版本/去重/组卷身份',
    topic_code VARCHAR(64) COMMENT '显式知识点编码（question_topic.code）；非空时不再从 tags 首段推导归属',
    review_status VARCHAR(24) COMMENT '审核状态：PENDING_REVIEW/APPROVED/REJECTED；历史题为 NULL',
    version INT DEFAULT 1 COMMENT '题目版本号',
    creator_id BIGINT COMMENT '创建人（sys_user.id）；种子题/历史题为 NULL',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_qq_type ON quiz_question (question_type);
CREATE INDEX idx_qq_difficulty ON quiz_question (difficulty);
CREATE INDEX idx_qq_status ON quiz_question (status);
CREATE INDEX idx_qq_source ON quiz_question (source);
CREATE INDEX idx_qq_category ON quiz_question (category_id);
CREATE INDEX idx_qq_doc ON quiz_question (doc_id);

-- ========== 在线答题记录：用户作答历史与判分结果 ==========
CREATE TABLE IF NOT EXISTS quiz_answer_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '用户ID（逻辑外键 sys_user.id）',
    question_id BIGINT NOT NULL COMMENT '题目ID（逻辑外键 quiz_question.id）',
    user_answer LONGTEXT COMMENT '用户提交的答案',
    is_correct INT DEFAULT 0 COMMENT '是否答对：0 错误 / 1 正确',
    score INT DEFAULT 0 COMMENT '本题得分（0-100）',
    time_cost INT DEFAULT 0 COMMENT '答题耗时（秒）',
    question_snapshot TEXT NULL COMMENT '作答时题目与判分条件快照，旧记录不补造',
    ai_feedback LONGTEXT COMMENT 'AI 评语（简答题等主观题预留）',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_qar_user ON quiz_answer_record (user_id);
CREATE INDEX idx_qar_question ON quiz_answer_record (question_id);
CREATE INDEX idx_qar_correct ON quiz_answer_record (is_correct);

-- ========== 每日打卡：连续天数与奖励记录 ==========
CREATE TABLE IF NOT EXISTS user_check_in (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '用户ID（逻辑外键 sys_user.id）',
    check_date DATE NOT NULL COMMENT '打卡日期（自然日）',
    continuous_days INT DEFAULT 1 COMMENT '当日累计的连续打卡天数',
    reward_exp INT DEFAULT 0 COMMENT '本次打卡奖励经验值',
    reward_energy INT DEFAULT 0 COMMENT '本次打卡奖励精力值',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
-- 同一用户同一自然日仅允许一条打卡记录（幂等约束）
CREATE UNIQUE INDEX uk_uci_user_date ON user_check_in (user_id, check_date);
CREATE INDEX idx_uci_user ON user_check_in (user_id);

-- ========== 学习小组 ==========

-- 学习小组表
CREATE TABLE IF NOT EXISTS study_group (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    name VARCHAR(100) NOT NULL COMMENT '小组名称',
    description VARCHAR(500) COMMENT '小组描述',
    icon VARCHAR(50) COMMENT '小组图标',
    color VARCHAR(20) COMMENT '小组颜色',
    type VARCHAR(20) DEFAULT 'PUBLIC' COMMENT '小组类型：PUBLIC 公开 / PRIVATE 私有',
    owner_id BIGINT NOT NULL COMMENT '创建者ID',
    member_count INT DEFAULT 0 COMMENT '成员数量',
    announcement VARCHAR(1000) COMMENT '小组公告',
    learning_plan_id BIGINT COMMENT '关联的学习计划ID',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_sg_owner ON study_group (owner_id);
CREATE INDEX idx_sg_type ON study_group (type);
CREATE INDEX idx_sg_deleted ON study_group (deleted);

-- 学习小组成员表
CREATE TABLE IF NOT EXISTS study_group_member (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    group_id BIGINT NOT NULL COMMENT '小组ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role VARCHAR(20) NOT NULL DEFAULT 'MEMBER' COMMENT '成员角色：OWNER 创建者 / ADMIN 管理员 / MEMBER 普通成员',
    invited_by BIGINT COMMENT '邀请人ID',
    last_read_message_id BIGINT COMMENT '最后已读消息ID',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE UNIQUE INDEX uk_sgm_group_user ON study_group_member (group_id, user_id, deleted);
CREATE INDEX idx_sgm_user ON study_group_member (user_id);
CREATE INDEX idx_sgm_role ON study_group_member (role);

-- 学习小组消息表
CREATE TABLE IF NOT EXISTS study_group_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    group_id BIGINT NOT NULL COMMENT '小组ID',
    sender_id BIGINT NOT NULL COMMENT '发送者ID',
    message_type VARCHAR(20) DEFAULT 'TEXT' COMMENT '消息类型：TEXT 文本 / IMAGE 图片 / FILE 文件 / CODE 代码块',
    content LONGTEXT COMMENT '消息内容',
    file_url VARCHAR(500) COMMENT '文件URL',
    file_name VARCHAR(200) COMMENT '文件名',
    file_size BIGINT COMMENT '文件大小（字节）',
    code_language VARCHAR(20) COMMENT '代码语言',
    mention_user_ids VARCHAR(500) COMMENT '@提及的用户ID列表，逗号分隔',
    recalled INT DEFAULT 0 COMMENT '是否已撤回：0 否 / 1 是',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_sgm_group ON study_group_message (group_id);
CREATE INDEX idx_sgm_sender ON study_group_message (sender_id);
CREATE INDEX idx_sgm_type ON study_group_message (message_type);
CREATE INDEX idx_sgm_ctime ON study_group_message (create_time);

-- ========== 单聊私信 ==========

-- 私聊会话表
CREATE TABLE IF NOT EXISTS private_conversation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_a_id BIGINT NOT NULL COMMENT '用户A ID（较小的一方）',
    user_b_id BIGINT NOT NULL COMMENT '用户B ID（较大的一方）',
    last_message_id BIGINT COMMENT '最后一条消息ID',
    last_message_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '最后消息时间',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE UNIQUE INDEX uk_pc_users ON private_conversation (user_a_id, user_b_id, deleted);
CREATE INDEX idx_pc_user_a ON private_conversation (user_a_id);
CREATE INDEX idx_pc_user_b ON private_conversation (user_b_id);

-- 私聊已读游标表
CREATE TABLE IF NOT EXISTS private_conversation_read (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    conversation_id BIGINT NOT NULL COMMENT '会话ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    last_read_message_id BIGINT COMMENT '最后已读消息ID',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE UNIQUE INDEX uk_pcr_conv_user ON private_conversation_read (conversation_id, user_id, deleted);
CREATE INDEX idx_pcr_user ON private_conversation_read (user_id);

-- 私聊消息表
CREATE TABLE IF NOT EXISTS private_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    conversation_id BIGINT NOT NULL COMMENT '会话ID',
    sender_id BIGINT NOT NULL COMMENT '发送者ID',
    message_type VARCHAR(20) DEFAULT 'TEXT' COMMENT '消息类型：TEXT 文本 / IMAGE 图片 / FILE 文件 / CODE 代码块',
    content LONGTEXT COMMENT '消息内容',
    file_url VARCHAR(500) COMMENT '文件URL',
    file_name VARCHAR(200) COMMENT '文件名',
    file_size BIGINT COMMENT '文件大小（字节）',
    code_language VARCHAR(20) COMMENT '代码语言',
    recalled INT DEFAULT 0 COMMENT '是否已撤回：0 否 / 1 是',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_pm_conv ON private_message (conversation_id);
CREATE INDEX idx_pm_sender ON private_message (sender_id);
CREATE INDEX idx_pm_ctime ON private_message (create_time);

-- ============================================================
-- AI 生成结果缓存表
-- ============================================================

-- 概念可视化图解缓存表（按用户+概念名维度缓存，避免重复调用 AI）
CREATE TABLE IF NOT EXISTS ai_concept_diagram (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '所属用户ID（逻辑外键sys_user.id）',
    concept VARCHAR(100) NOT NULL COMMENT '概念名称',
    diagram_type VARCHAR(20) DEFAULT 'FLOWCHART' COMMENT '图解类型：FLOWCHART/SEQUENCE/CLASS/ER/PIE',
    mermaid_code LONGTEXT COMMENT 'Mermaid 语法源码',
    description VARCHAR(1000) COMMENT '概念简要说明',
    explanation LONGTEXT COMMENT 'AI 详细解释',
    difficulty INT DEFAULT 1 COMMENT '难度：1 入门 / 2 中等 / 3 进阶',
    key_points LONGTEXT COMMENT '关键知识点列表（JSON 数组字符串）',
    related_concepts LONGTEXT COMMENT '关联概念列表（JSON 数组字符串）',
    code_example LONGTEXT COMMENT '代码示例（可为空）',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE UNIQUE INDEX uk_acd_user_concept ON ai_concept_diagram (user_id, concept, deleted);
CREATE INDEX idx_acd_user ON ai_concept_diagram (user_id);

-- 个性化学习路径缓存表（按用户+目标+水平+每日时长维度缓存）
CREATE TABLE IF NOT EXISTS ai_personalized_path (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '所属用户ID（逻辑外键sys_user.id）',
    goal VARCHAR(200) NOT NULL COMMENT '学习目标',
    level VARCHAR(20) DEFAULT '入门' COMMENT '当前水平：入门/进阶/高级',
    daily_minutes INT DEFAULT 30 COMMENT '每日学习时长（分钟）',
    title VARCHAR(200) COMMENT '推荐路径标题',
    reason VARCHAR(1000) COMMENT '推荐理由',
    total_duration INT DEFAULT 0 COMMENT '预计总时长（分钟）',
    goals_text LONGTEXT COMMENT '学习目标列表（JSON 数组字符串）',
    chapters_text LONGTEXT COMMENT '章节规划（JSON 数组字符串）',
    advice LONGTEXT COMMENT 'AI 学习建议',
    related_path_id BIGINT COMMENT '关联已有路径ID（可为空）',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE UNIQUE INDEX uk_app_user_goal ON ai_personalized_path (user_id, goal, level, daily_minutes, deleted);
CREATE INDEX idx_app_user ON ai_personalized_path (user_id);

-- ============================================================
-- 编程挑战（编程闯关：赛道 / 关卡 / 用户进度 / 关卡记录，游戏化积分）
-- ============================================================

-- 挑战赛道表：一个赛道由若干关卡组成（如「JavaScript 十题闯关」）
CREATE TABLE IF NOT EXISTS code_challenge (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL COMMENT '挑战标题',
    description LONGTEXT COMMENT '挑战简介',
    language VARCHAR(20) DEFAULT 'javascript' COMMENT '主语言：javascript/typescript/python/java/sql',
    difficulty INT DEFAULT 0 COMMENT '难度：0 简单 / 1 中等 / 2 困难',
    icon VARCHAR(50) DEFAULT 'trophy' COMMENT '图标名（lucide 图标）',
    theme_color VARCHAR(20) DEFAULT '#3B6FE0' COMMENT '主题色（十六进制）',
    tags VARCHAR(500) COMMENT '标签，逗号分隔',
    level_count INT DEFAULT 0 COMMENT '关卡总数',
    total_points INT DEFAULT 0 COMMENT '满分积分（各关卡积分之和）',
    sort_order INT DEFAULT 0 COMMENT '排序值，越小越靠前',
    status INT DEFAULT 1 COMMENT '状态：0 草稿 / 1 已发布',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_cc_status ON code_challenge (status);
CREATE INDEX idx_cc_sort   ON code_challenge (sort_order);

-- 挑战关卡表：内嵌题目信息，使赛道自包含（不依赖 code_question）
CREATE TABLE IF NOT EXISTS code_challenge_level (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    challenge_id BIGINT NOT NULL COMMENT '所属挑战ID（逻辑外键code_challenge.id）',
    level_no INT NOT NULL COMMENT '关卡序号，从 1 开始递增',
    title VARCHAR(200) NOT NULL COMMENT '关卡标题',
    description LONGTEXT COMMENT '题目描述（多行文本）',
    difficulty INT DEFAULT 0 COMMENT '难度：0 简单 / 1 中等 / 2 困难',
    language VARCHAR(20) DEFAULT 'javascript' COMMENT '语言标识',
    hint LONGTEXT COMMENT '关卡提示',
    example_input LONGTEXT COMMENT '输入示例',
    example_output LONGTEXT COMMENT '输出示例',
    code_template LONGTEXT COMMENT '代码模板（编辑器初始内容）',
    test_cases LONGTEXT COMMENT '测试用例 JSON 数组：[{input, expected}]',
    points INT DEFAULT 10 COMMENT '通关积分',
    status INT DEFAULT 1 COMMENT '状态：0 草稿 / 1 已发布',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_ccl_challenge ON code_challenge_level (challenge_id);
CREATE INDEX idx_ccl_no        ON code_challenge_level (challenge_id, level_no);

-- 用户挑战进度表：记录某用户在某赛道的整体进度（不建含 deleted 的唯一索引，避免逻辑删除重复覆盖冲突）
CREATE TABLE IF NOT EXISTS code_challenge_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID（逻辑外键sys_user.id）',
    challenge_id BIGINT NOT NULL COMMENT '挑战ID（逻辑外键code_challenge.id）',
    cleared_levels INT DEFAULT 0 COMMENT '已通关关卡数',
    current_level INT DEFAULT 1 COMMENT '当前解锁到的关卡序号',
    total_points INT DEFAULT 0 COMMENT '本赛道累计获得积分',
    total_stars INT DEFAULT 0 COMMENT '本赛道累计星星数',
    status INT DEFAULT 0 COMMENT '状态：0 进行中 / 1 已通关',
    start_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
    finish_time TIMESTAMP NULL DEFAULT NULL COMMENT '通关时间',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_ccr_user      ON code_challenge_record (user_id);
CREATE INDEX idx_ccr_user_ch   ON code_challenge_record (user_id, challenge_id);

-- ========== 成就/勋章系统（G-ACHIEVE-01/02） ==========

-- 成就定义表：预定义的成就模板（编码唯一，管理端可增删）
CREATE TABLE IF NOT EXISTS achievement (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL COMMENT '成就编码（英文标识，如 READ_1ST_DOC）',
    name VARCHAR(100) NOT NULL COMMENT '成就名称',
    description VARCHAR(500) COMMENT '成就描述',
    icon VARCHAR(50) DEFAULT 'trophy' COMMENT '成就图标名（Icon.vue 图标名）',
    category VARCHAR(20) NOT NULL COMMENT '分类：LEARNING/EXPLORATION/COMMUNITY/PERSISTENCE/SPECIAL',
    condition_type VARCHAR(30) NOT NULL COMMENT '条件类型：READ_DOCS/COMPLETE_PATH/REVIEW_FLASHCARD/CODE_EXERCISE/FAVORITE_DOC/NOTE_CREATED/MISTAKE_CLEARED/STREAK_DAYS/CATEGORY_ALL/CHECKIN_DAYS',
    condition_value INT NOT NULL COMMENT '条件阈值（达标所需数量）',
    sort_order INT DEFAULT 0 COMMENT '排序值，越小越靠前',
    reward_exp INT DEFAULT 0 COMMENT '达成奖励经验值',
    status INT DEFAULT 1 COMMENT '状态：0 禁用 / 1 启用',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_ach_category ON achievement (category);
CREATE UNIQUE INDEX idx_ach_code ON achievement (code);

-- 用户成就解锁记录表：用户与成就多对多关系，同成就仅能解锁一次
CREATE TABLE IF NOT EXISTS user_achievement (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID（逻辑外键sys_user.id）',
    achievement_id BIGINT NOT NULL COMMENT '成就ID（逻辑外键achievement.id）',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE UNIQUE INDEX idx_ua_user_ach ON user_achievement (user_id, achievement_id);
CREATE INDEX idx_ua_user ON user_achievement (user_id);

-- 用户关卡通关记录表：记录某用户单个关卡的通关结果、星级与获得积分
CREATE TABLE IF NOT EXISTS code_challenge_level_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID（逻辑外键sys_user.id）',
    challenge_id BIGINT NOT NULL COMMENT '挑战ID（逻辑外键code_challenge.id）',
    level_id BIGINT NOT NULL COMMENT '关卡ID（逻辑外键code_challenge_level.id）',
    level_no INT NOT NULL COMMENT '关卡序号',
    passed INT DEFAULT 0 COMMENT '是否通关：0 未通关 / 1 已通关',
    stars INT DEFAULT 0 COMMENT '获得星级：1-3',
    attempts INT DEFAULT 0 COMMENT '提交次数',
    points_earned INT DEFAULT 0 COMMENT '本关获得积分',
    last_code LONGTEXT COMMENT '最近一次提交的代码',
    finish_time TIMESTAMP NULL DEFAULT NULL COMMENT '通关时间',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_cclr_user     ON code_challenge_level_record (user_id);
CREATE INDEX idx_cclr_user_lvl ON code_challenge_level_record (user_id, level_id);

-- ========== 文档分块与向量索引（A-RAG 文档向量检索）==========

-- 文档分块表：存储文档切分后的片段与 embedding 向量
CREATE TABLE IF NOT EXISTS doc_chunk (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    doc_id BIGINT NOT NULL COMMENT '归属文档ID（逻辑外键 doc_document.id）',
    chunk_index INT NOT NULL COMMENT '分块序号（从 0 开始）',
    content LONGTEXT NOT NULL COMMENT '分块文本内容',
    char_count INT DEFAULT 0 COMMENT '字符数',
    embedding LONGTEXT COMMENT 'embedding 向量：逗号分隔的浮点数组（如 "0.123,-0.456,..."）',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_dc_doc ON doc_chunk (doc_id);
CREATE INDEX idx_dc_doc_order ON doc_chunk (doc_id, chunk_index);

-- L-PATH-01 章节前置依赖：在 learning_chapter 表添加前置章节ID列表

-- L-FORM-01 章节嵌入视频进度追踪：在用户章节进度表添加视频观看进度（0-100）

-- ========== 代码提交记录（P-CODE-03 代码判题记录持久化）==========
CREATE TABLE IF NOT EXISTS code_submit_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID（逻辑外键 sys_user.id）',
    question_id BIGINT NOT NULL COMMENT '题目ID（逻辑外键 code_question.id）',
    code LONGTEXT NOT NULL COMMENT '提交的代码',
    language VARCHAR(20) DEFAULT 'javascript' COMMENT '编程语言',
    total INT DEFAULT 0 COMMENT '总测试用例数',
    pass_count INT DEFAULT 0 COMMENT '通过用例数',
    passed INT DEFAULT 0 COMMENT '是否完全通过：0 未通过 / 1 已通过',
    error_msg LONGTEXT COMMENT '运行时错误信息',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_csr_user ON code_submit_record (user_id);
CREATE INDEX idx_csr_question ON code_submit_record (question_id);
CREATE INDEX idx_csr_user_q ON code_submit_record (user_id, question_id);

-- ========== 知识图谱实体关系（A-RAG-04：AI 从文档抽取实体+关系，构建真正知识图谱）==========

-- 知识实体表：AI 从文档正文抽取的知识实体（概念/技术/术语/原理/工具），按名称全局去重合并
CREATE TABLE IF NOT EXISTS kg_entity (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    doc_id       BIGINT NOT NULL COMMENT '首次抽取来源文档ID（逻辑外键 doc_document.id）',
    category_id  BIGINT COMMENT '所属分类ID（逻辑外键 doc_category.id），取首次抽取文档的分类',
    name         VARCHAR(200) NOT NULL COMMENT '实体名称（全局唯一，合并同名实体）',
    type         VARCHAR(30)  DEFAULT 'CONCEPT' COMMENT '实体类型：CONCEPT/TECHNIQUE/TERM/PRINCIPLE/TOOL/OTHER',
    description  VARCHAR(1000) COMMENT '实体说明',
    weight       INT          DEFAULT 1 COMMENT '重要度/被抽取次数，用于前端节点大小',
    create_time  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      INT          DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_kg_entity_doc ON kg_entity (doc_id);
CREATE INDEX idx_kg_entity_category ON kg_entity (category_id);
CREATE INDEX idx_kg_entity_name ON kg_entity (name);

-- 实体关系表：知识实体之间的语义关系，记录抽取来源文档以支持溯源
CREATE TABLE IF NOT EXISTS kg_relation (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_entity_id BIGINT NOT NULL COMMENT '源实体ID（逻辑外键 kg_entity.id）',
    target_entity_id BIGINT NOT NULL COMMENT '目标实体ID（逻辑外键 kg_entity.id）',
    relation         VARCHAR(30) NOT NULL COMMENT '关系类型：RELATED_TO/PREREQUISITE/IS_A/PART_OF/USES/CONTRASTS',
    description      VARCHAR(500) COMMENT '关系说明',
    doc_id           BIGINT COMMENT '抽取来源文档ID（逻辑外键 doc_document.id）',
    create_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          INT       DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_kg_rel_source ON kg_relation (source_entity_id);
CREATE INDEX idx_kg_rel_target ON kg_relation (target_entity_id);
CREATE INDEX idx_kg_rel_doc ON kg_relation (doc_id);

-- ========== 数字证书（G-CERT-01：路径完成后自动颁发）==========
CREATE TABLE IF NOT EXISTS learning_certificate (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '持证用户ID（逻辑外键 sys_user.id）',
    path_id BIGINT NOT NULL COMMENT '完成的学习路径ID（逻辑外键 learning_path.id）',
    cert_no VARCHAR(64) NOT NULL COMMENT '唯一证书验证码（可公开验证）',
    path_title VARCHAR(200) NOT NULL COMMENT '路径标题快照（颁发时固化，避免路径改名影响证书）',
    user_name VARCHAR(50) NOT NULL COMMENT '持证用户名快照',
    issue_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '颁发时间',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE UNIQUE INDEX uk_cert_no ON learning_certificate (cert_no);
CREATE INDEX idx_cert_user ON learning_certificate (user_id);
CREATE INDEX idx_cert_path ON learning_certificate (path_id);
CREATE UNIQUE INDEX uk_cert_user_path ON learning_certificate (user_id, path_id, deleted);

-- ========== 知识库批量导入：来源追踪与增量去重 ==========
-- 在 doc_document 上追加来源路径与内容哈希列，支持 Obsidian/本地目录导入时的增量去重

CREATE INDEX idx_doc_source_path ON doc_document (source_path);
CREATE INDEX idx_doc_content_hash ON doc_document (content_hash);

-- ========== 全文检索索引（MySQL 专有）==========
-- LIKE '%kw%' 前后通配无法命中 B-Tree 索引，数据量增长后必然全表扫描。
-- 这里为标题/摘要/正文建立 FULLTEXT，配合 ngram 解析器支持中文分词（MySQL 5.7.6+ 内置），
-- 使检索可走 MATCH ... AGAINST 并获得 BM25 相关度评分。
-- 注意：H2 不支持 FULLTEXT，故该索引只存在于 MySQL 方言脚本中；
-- 应用层检索保持 LIKE 与 MATCH 双路兼容，缺失该索引时功能不受影响，仅性能下降。
CREATE FULLTEXT INDEX ft_doc_content ON doc_document (title, summary, content) WITH PARSER ngram;

-- ========== 专注会话（P0 专注模块核心表）==========
CREATE TABLE IF NOT EXISTS focus_session (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  mode VARCHAR(20) DEFAULT 'POMODORO',
  start_time TIMESTAMP NULL DEFAULT NULL,
  end_time TIMESTAMP NULL DEFAULT NULL,
  duration_min INT DEFAULT 0,
  distraction_count INT DEFAULT 0,
  completed_pomodoros INT DEFAULT 0,
  associated_task_id BIGINT,
  quality_rating INT,
  note VARCHAR(500),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  deleted INT DEFAULT 0,
  KEY idx_user_time (user_id, start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 用户自定义背景预设
CREATE TABLE IF NOT EXISTS user_background_preset (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  name VARCHAR(50) NOT NULL,
  bg_type VARCHAR(20) NOT NULL,
  bg_value VARCHAR(1000),
  thumbnail VARCHAR(500),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  deleted INT DEFAULT 0,
  UNIQUE KEY uk_ubp_user_name (user_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========== 导入规则模板（驱动 Obsidian 目录一键导入的闪卡/题库抽取逻辑）==========
CREATE TABLE IF NOT EXISTS import_template (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id BIGINT NOT NULL COMMENT '创建者ID（逻辑外键 sys_user.id），预设模板为 1',
  name VARCHAR(100) NOT NULL COMMENT '模板名称',
  type VARCHAR(20) NOT NULL COMMENT '模板类型：FLASHCARD 闪卡 / QUIZ 题库 / PATH 学习路径',
  description VARCHAR(500) DEFAULT NULL COMMENT '模板描述',
  content LONGTEXT COMMENT '模板内容（JSON 字符串）',
  enabled INT DEFAULT 1 COMMENT '是否启用：1 启用 / 0 停用',
  is_default INT DEFAULT 0 COMMENT '是否默认模板：1 默认 / 0 否（同类型仅一个默认）',
  is_preset INT DEFAULT 0 COMMENT '是否预设模板：1 系统预设 / 0 用户自定义',
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
  PRIMARY KEY (id),
  KEY idx_it_user (user_id),
  KEY idx_it_type (type),
  KEY idx_it_default (type, is_default),
  KEY idx_it_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- 知识库工作台（Workbench）：输入 → 整理 → 复习 → 输出 四模块闭环
-- MySQL 方言：建表保留 IF NOT EXISTS，索引用 KEY，TEXT 用 LONGTEXT，结尾 InnoDB。
-- ============================================================================

-- ---------- 模块一：知识输入（收集箱 Inbox）----------
CREATE TABLE IF NOT EXISTS wb_capture (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  user_id BIGINT NOT NULL COMMENT '所属用户ID（逻辑外键 sys_user.id）',
  title VARCHAR(200) NOT NULL COMMENT '标题/一句话摘要',
  content LONGTEXT COMMENT '正文内容（Markdown）',
  source_type VARCHAR(20) DEFAULT 'MANUAL' COMMENT '来源：MANUAL 手记 / DOC 文档 / WEB 网页 / AI 生成 / IMPORT 导入',
  source_url VARCHAR(1000) COMMENT '来源链接（网页剪藏时使用）',
  doc_id BIGINT COMMENT '来源文档ID（逻辑外键 doc_document.id）',
  category_id BIGINT COMMENT '归属知识库/分类ID（逻辑外键 doc_category.id）',
  tags VARCHAR(500) COMMENT '逗号分隔标签',
  status VARCHAR(20) DEFAULT 'INBOX' COMMENT '流转状态：INBOX 待整理 / PROCESSED 已整理 / ARCHIVED 已归档',
  starred INT DEFAULT 0 COMMENT '是否标星：1 是 / 0 否',
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
  KEY idx_wbc_user_status (user_id, status),
  KEY idx_wbc_category (category_id),
  KEY idx_wbc_doc (doc_id),
  KEY idx_wbc_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------- 模块二：知识整理（康奈尔笔记 Cornell Note）----------
CREATE TABLE IF NOT EXISTS wb_note (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  user_id BIGINT NOT NULL COMMENT '所属用户ID（逻辑外键 sys_user.id）',
  capture_id BIGINT COMMENT '来源收集箱条目ID（逻辑外键 wb_capture.id）',
  category_id BIGINT COMMENT '归属知识库/分类ID（逻辑外键 doc_category.id）',
  title VARCHAR(200) NOT NULL COMMENT '笔记标题',
  cue_column LONGTEXT COMMENT '康奈尔-线索栏：关键问题/关键词，用于主动回忆自测',
  note_column LONGTEXT COMMENT '康奈尔-笔记栏：课堂/阅读主体内容',
  summary_column LONGTEXT COMMENT '康奈尔-总结栏：用自己的话概括',
  tags VARCHAR(500) COMMENT '逗号分隔标签',
  mastery INT DEFAULT 0 COMMENT '掌握度自评：0~100',
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
  KEY idx_wbn_user (user_id),
  KEY idx_wbn_capture (capture_id),
  KEY idx_wbn_category (category_id),
  KEY idx_wbn_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------- 模块三：知识复习（间隔重复卡片，SM-2 算法）----------
CREATE TABLE IF NOT EXISTS wb_review_card (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  user_id BIGINT NOT NULL COMMENT '所属用户ID（逻辑外键 sys_user.id）',
  capture_id BIGINT COMMENT '来源收集箱条目ID（逻辑外键 wb_capture.id）',
  note_id BIGINT COMMENT '来源康奈尔笔记ID（逻辑外键 wb_note.id）',
  category_id BIGINT COMMENT '归属知识库/分类ID（逻辑外键 doc_category.id）',
  front LONGTEXT NOT NULL COMMENT '卡片正面：问题/线索',
  back LONGTEXT NOT NULL COMMENT '卡片背面：答案/解释',
  card_type VARCHAR(20) DEFAULT 'BASIC' COMMENT '卡片类型：BASIC 问答 / CLOZE 挖空 / RECALL 主动回忆',
  hint VARCHAR(1000) COMMENT '记忆提示（可选）',
  tags VARCHAR(500) COMMENT '标签，逗号分隔（可选）',
  extra_json TEXT COMMENT '类型专属扩展 JSON：RECALL 检查要点等；旧数据为空',
  ease_factor INT DEFAULT 250 COMMENT 'SM-2 难度系数（放大100倍存储，默认250即2.5）',
  repetitions INT DEFAULT 0 COMMENT 'SM-2 连续答对次数，答错归零',
  interval_day INT DEFAULT 0 COMMENT '当前复习间隔（天）',
  review_count INT DEFAULT 0 COMMENT '累计复习次数',
  lapse_count INT DEFAULT 0 COMMENT '遗忘次数（评分低于及格线）',
  next_review_time TIMESTAMP COMMENT '下次应复习时间（遗忘曲线提醒依据）',
  last_review_time TIMESTAMP COMMENT '上次复习时间',
  suspended INT DEFAULT 0 COMMENT '是否暂停复习：1 暂停 / 0 正常',
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
  KEY idx_wbrc_user_next (user_id, next_review_time),
  KEY idx_wbrc_capture (capture_id),
  KEY idx_wbrc_note (note_id),
  KEY idx_wbrc_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 复习日志：每次抽查的评分流水，用于遗忘曲线可视化与学习报告统计
CREATE TABLE IF NOT EXISTS wb_review_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  user_id BIGINT NOT NULL COMMENT '所属用户ID（逻辑外键 sys_user.id）',
  card_id BIGINT NOT NULL COMMENT '复习卡片ID（逻辑外键 wb_review_card.id）',
  quality INT NOT NULL COMMENT '用户反馈评分：0 完全忘记 / 1 困难 / 2 一般 / 3 容易（映射 SM-2）',
  interval_day INT COMMENT '本次评分后计算出的新间隔（天）',
  ease_factor INT COMMENT '本次评分后的难度系数（放大100倍）',
  cost_ms BIGINT COMMENT '本次作答耗时（毫秒）',
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
  KEY idx_wbrl_user_time (user_id, create_time),
  KEY idx_wbrl_card (card_id),
  KEY idx_wbrl_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------- 模块三扩展：主动回忆（三轮闭卷默写）----------
-- 三轮流程：1 即时默写 → 2 补漏默写 → 3 1小时后复测。每轮提交后与原文比对计分。
CREATE TABLE IF NOT EXISTS wb_recall_session (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  user_id BIGINT NOT NULL COMMENT '所属用户ID（逻辑外键 sys_user.id）',
  note_id BIGINT COMMENT '关联笔记ID（逻辑外键 wb_note.id）',
  card_id BIGINT COMMENT '关联复习卡ID（逻辑外键 wb_review_card.id）',
  title VARCHAR(200) COMMENT '标题（冗余自笔记/卡片，便于列表展示）',
  source_text LONGTEXT NOT NULL COMMENT '原文（默写对照基准）',
  source_type VARCHAR(20) DEFAULT 'PASTE' COMMENT '原文来源类型：PASTE 粘贴 / NOTE 从笔记选择',
  archived INT DEFAULT 0 COMMENT '归档标记：0 未归档 / 1 已归档',
  round1_draft LONGTEXT COMMENT '第一轮中途草稿',
  round2_draft LONGTEXT COMMENT '第二轮中途草稿',
  round3_draft LONGTEXT COMMENT '第三轮中途草稿',
  gap_json LONGTEXT COMMENT '用户确认的关键遗漏（JSON 按轮次）',
  round1_text LONGTEXT COMMENT '第一轮：即时默写内容',
  round1_score INT COMMENT '第一轮得分（0-100）',
  round2_text LONGTEXT COMMENT '第二轮：补漏默写内容',
  round2_score INT COMMENT '第二轮得分（0-100）',
  round3_text LONGTEXT COMMENT '第三轮：1小时后复测内容',
  round3_score INT COMMENT '第三轮得分（0-100）',
  current_round INT DEFAULT 1 COMMENT '当前轮次：1 / 2 / 3',
  status VARCHAR(20) DEFAULT 'IN_PROGRESS' COMMENT '会话状态：DRAFT 草稿 / IN_PROGRESS 进行中 / COMPLETED 已完成',
  round3_due_time DATETIME COMMENT '第三轮预定复测时间（1小时后），用于提醒',
  completed_time DATETIME COMMENT '完成时间',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
  KEY idx_wbrs_user_time (user_id, create_time),
  KEY idx_wbrs_note (note_id),
  KEY idx_wbrs_status (status),
  KEY idx_wbrs_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------- 模块三扩展：记忆宫殿（Method of Loci）----------
CREATE TABLE IF NOT EXISTS wb_palace (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  user_id BIGINT NOT NULL COMMENT '所属用户ID（逻辑外键 sys_user.id）',
  name VARCHAR(100) NOT NULL COMMENT '宫殿名称，如「我的书房」',
  description VARCHAR(500) COMMENT '场景描述',
  theme VARCHAR(20) DEFAULT 'ROOM' COMMENT '场景主题：ROOM 房间 / STREET 街道 / CAMPUS 校园 / CUSTOM 自定义',
  cover_color VARCHAR(20) COMMENT '封面主题色（十六进制）',
  category_id BIGINT COMMENT '归属知识库/分类ID（逻辑外键 doc_category.id）',
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
  KEY idx_wbp_user (user_id),
  KEY idx_wbp_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 宫殿位点：pos_x/pos_y 为画布百分比坐标（0~100），前端拖拽编辑空间布局
CREATE TABLE IF NOT EXISTS wb_palace_loci (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  user_id BIGINT NOT NULL COMMENT '所属用户ID（逻辑外键 sys_user.id）',
  palace_id BIGINT NOT NULL COMMENT '所属宫殿ID（逻辑外键 wb_palace.id）',
  capture_id BIGINT COMMENT '关联收集箱条目ID（逻辑外键 wb_capture.id）',
  note_id BIGINT COMMENT '关联康奈尔笔记ID（逻辑外键 wb_note.id）',
  category_id BIGINT COMMENT '归属知识库/分类ID（逻辑外键 doc_category.id）',
  name VARCHAR(100) NOT NULL COMMENT '位点名称，如「书桌左上角」',
  knowledge_point VARCHAR(500) COMMENT '绑定的知识点内容',
  image_hint VARCHAR(500) COMMENT '联想图像描述（越夸张越好记）',
  icon VARCHAR(50) COMMENT '位点图标名（Icon 组件图标）',
  pos_x INT DEFAULT 50 COMMENT '画布横向百分比坐标（0~100）',
  pos_y INT DEFAULT 50 COMMENT '画布纵向百分比坐标（0~100）',
  sort_order INT DEFAULT 0 COMMENT '漫游顺序（记忆宫殿按固定路线回忆）',
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
  KEY idx_wbpl_palace (palace_id, sort_order),
  KEY idx_wbpl_user (user_id),
  KEY idx_wbpl_category (category_id),
  KEY idx_wbpl_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------- 模块四：知识输出（费曼故事）----------
CREATE TABLE IF NOT EXISTS wb_story (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  user_id BIGINT NOT NULL COMMENT '所属用户ID（逻辑外键 sys_user.id）',
  capture_id BIGINT COMMENT '来源收集箱条目ID（逻辑外键 wb_capture.id）',
  note_id BIGINT COMMENT '来源康奈尔笔记ID（逻辑外键 wb_note.id）',
  category_id BIGINT COMMENT '归属知识库/分类ID（逻辑外键 doc_category.id）',
  title VARCHAR(200) NOT NULL COMMENT '故事标题',
  audience VARCHAR(50) DEFAULT 'CHILD' COMMENT '假想听众：CHILD 小孩 / NEWBIE 初学者 / PEER 同行 / INTERVIEWER 面试官',
  metaphor VARCHAR(500) COMMENT '核心类比/隐喻，如「把索引比作书的目录」',
  content LONGTEXT COMMENT '故事正文（Markdown 叙事体）',
  gap_note LONGTEXT COMMENT '讲述卡点记录：没讲清楚的地方 = 知识漏洞',
  status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态：DRAFT 草稿 / DONE 已完成 / PUBLISHED 已分享',
  clarity_score INT DEFAULT 0 COMMENT '自评讲清程度：0~100',
  word_count INT DEFAULT 0 COMMENT '正文字数',
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
  KEY idx_wbs_user_status (user_id, status),
  KEY idx_wbs_capture (capture_id),
  KEY idx_wbs_note (note_id),
  KEY idx_wbs_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------- 学习工作台：思维导图（MindMap）----------
-- 整图以 JSON 形式持久化在 data 列：{ nodes:[{id,text,x,y,parentId,collapsed,color}], edges:[{id,source,target}], view:{scale,tx,ty} }
-- 层级关系由节点 parentId 表达；edges 存储额外自由连线（跨节点连接）。
CREATE TABLE IF NOT EXISTS wb_mindmap (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  user_id BIGINT NOT NULL COMMENT '所属用户ID（逻辑外键 sys_user.id）',
  title VARCHAR(200) NOT NULL COMMENT '思维导图标题',
  data LONGTEXT COMMENT '整图数据（JSON）：节点/连线/视图变换',
  category_id BIGINT COMMENT '归属知识库/分类ID（逻辑外键 doc_category.id）',
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
  KEY idx_wbm_user (user_id),
  KEY idx_wbm_category (category_id),
  KEY idx_wbm_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS wb_drawing (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  user_id BIGINT NOT NULL COMMENT '所属用户ID（逻辑外键 sys_user.id）',
  title VARCHAR(200) NOT NULL COMMENT '绘图标题',
  type VARCHAR(50) DEFAULT 'flowchart' COMMENT '图类型（flowchart 等）',
  data LONGTEXT COMMENT '整图数据（JSON）：节点/连线（vue-flow 契约）',
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
  KEY idx_wbd_user (user_id),
  KEY idx_wbd_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========== 习惯打卡：多习惯管理 + 每日打卡记录 + 连续天数与进度 ==========
CREATE TABLE IF NOT EXISTS habit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '用户ID（逻辑外键 sys_user.id）',
    name VARCHAR(100) NOT NULL COMMENT '习惯名称',
    description VARCHAR(500) COMMENT '习惯描述',
    icon VARCHAR(40) DEFAULT 'repeat' COMMENT '习惯图标',
    color VARCHAR(20) DEFAULT 'var(--kb-primary)' COMMENT '习惯主题色',
    frequency VARCHAR(20) DEFAULT 'daily' COMMENT '打卡频率：daily 每日 / weekly 每周',
    target_count INT DEFAULT 1 COMMENT '每周期目标打卡次数（如每日 8 杯水）',
    reminder_time VARCHAR(5) COMMENT '提醒时间 HH:mm（前端 Notification API 触发）',
    start_date DATE COMMENT '习惯开始日期',
    active TINYINT(1) DEFAULT 1 COMMENT '是否启用：1 启用 / 0 停用',
    sort_order INT DEFAULT 0 COMMENT '排序',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_habit_user ON habit (user_id);
CREATE INDEX idx_habit_active ON habit (active);

-- 习惯打卡记录：每习惯每自然日一行，count 累计当日打卡次数（支持多目标习惯）
CREATE TABLE IF NOT EXISTS habit_checkin (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    habit_id BIGINT NOT NULL COMMENT '习惯ID（逻辑外键 habit.id）',
    check_date DATE NOT NULL COMMENT '打卡日期（自然日）',
    count INT DEFAULT 1 COMMENT '当日累计打卡次数',
    note VARCHAR(500) COMMENT '打卡备注',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
-- 同一用户同一习惯同一自然日仅允许一条记录（幂等约束，count 累加）
CREATE UNIQUE INDEX uk_hc_user_habit_date ON habit_checkin (user_id, habit_id, check_date);
CREATE INDEX idx_hc_user ON habit_checkin (user_id);
CREATE INDEX idx_hc_habit ON habit_checkin (habit_id);

-- ========== 任务标签（Things3 式标签） ==========
CREATE TABLE IF NOT EXISTS task_tag (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    name VARCHAR(50) NOT NULL COMMENT '标签名称',
    color VARCHAR(64) DEFAULT 'var(--kb-muted-foreground)' COMMENT '标签颜色，支持设计令牌与CSS颜色值',
    sort_order INT DEFAULT 0 COMMENT '排序',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_tag_user ON task_tag (user_id);

-- 任务-标签关联（多对多，逻辑外键，无物理外键）
CREATE TABLE IF NOT EXISTS task_tag_rel (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    task_id BIGINT NOT NULL COMMENT '任务ID（逻辑外键 task.id）',
    tag_id BIGINT NOT NULL COMMENT '标签ID（逻辑外键 task_tag.id）',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
-- 同一任务同一标签仅关联一次
CREATE UNIQUE INDEX uk_ttr_task_tag ON task_tag_rel (task_id, tag_id);
CREATE INDEX idx_ttr_task ON task_tag_rel (task_id);
CREATE INDEX idx_ttr_tag ON task_tag_rel (tag_id);

-- ============================================================
-- 学习计划表：AI 编排生成的日级计划，关联任务/习惯/学习任务
-- F3 学习计划智能编排（P2·体系打通）
-- ============================================================
CREATE TABLE IF NOT EXISTS learning_plan (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    plan_date DATE NOT NULL COMMENT '计划日期',
    learning_task_ids LONGTEXT COMMENT '学习任务IDs(JSON数组)',
    habit_ids LONGTEXT COMMENT '习惯IDs(JSON数组)',
    todo_ids LONGTEXT COMMENT '待办任务IDs(JSON数组)',
    time_blocks LONGTEXT COMMENT '时间块安排(JSON数组，PlanBlockVO[]序列化)',
    completed_ratio DECIMAL(5,2) DEFAULT 0 COMMENT '完成率(%)',
    status TINYINT DEFAULT 0 COMMENT '状态：0 草稿 / 1 已生成 / 2 已完成',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    UNIQUE KEY uk_lp_user_date (user_id, plan_date, deleted),
    KEY idx_lp_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- Phase 0 补全：章节学习笔记 / 章节收藏 / 学习宠物 / 学习周报 /
--               AI 学习建议缓存 / 用户学习偏好（此前仅有实体无建表脚本）
-- ============================================================

-- 章节学习笔记：关联用户与章节，正文支持 Markdown
CREATE TABLE IF NOT EXISTS learning_note (
    folder_id BIGINT COMMENT '所属笔记目录',
    favorite BOOLEAN NOT NULL DEFAULT FALSE COMMENT '个人收藏状态',
    source_doc_id BIGINT COMMENT '来源文档ID，读取时复验权限',
    revision BIGINT NOT NULL DEFAULT 1 COMMENT '递增修订号',
    source_type VARCHAR(20) NOT NULL DEFAULT 'GENERAL' COMMENT '笔记来源',
    content_format VARCHAR(20) NOT NULL DEFAULT 'MARKDOWN' COMMENT '正文格式',
    status VARCHAR(20) NOT NULL DEFAULT 'SAVED' COMMENT '草稿或已保存',
    tags VARCHAR(2000) COMMENT '标签',
    source_title VARCHAR(500) COMMENT '来源标题提示，非权限关联',
    import_key VARCHAR(64) COMMENT '服务端迁移指纹',
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

-- 章节收藏：用户与章节多对多，同一章节仅收藏一次
CREATE TABLE IF NOT EXISTS chapter_favorite (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '用户ID（逻辑外键 sys_user.id）',
    chapter_id BIGINT NOT NULL COMMENT '章节ID（逻辑外键 learning_chapter.id）',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    KEY idx_chapter_favorite_user_chapter (user_id, chapter_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 用户学习宠物：每用户一只，喂食/玩耍/专注积累经验升级
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

-- 学习周报：按自然周（周一至周日）汇总学习统计，AI 生成摘要/成就/建议
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

-- AI 学习建议缓存：按用户 + 周期维度缓存，避免重复调用大模型
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

-- 用户学习偏好：沉浸学习时长/难度/节奏等个性化设置，一个用户仅一条有效配置（Service 层 upsert 保证）
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

-- ============================================================
-- 学习行为事件层（Learning Event System，Phase 1）
-- 统一埋点所有学习行为，作为掌握度引擎 / AI 教练 / 学习计划的数据底座。
-- 仅追加记录，不替换任何原有业务表。
-- ============================================================
CREATE TABLE IF NOT EXISTS learning_event (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  user_id BIGINT NOT NULL COMMENT '所属用户ID（逻辑外键 sys_user.id）',
  event_type VARCHAR(40) NOT NULL COMMENT '事件类型：DOCUMENT_READ/CHAPTER_START/CHAPTER_COMPLETE/QUESTION_ANSWERED/QUESTION_CORRECT/QUESTION_WRONG/CODE_SUBMITTED/CODE_PASSED/CODE_FAILED/FLASHCARD_REVIEWED/RECALL_COMPLETED/AI_CHAT/KNOWLEDGE_VIEWED/PATH_COMPLETED/CHECK_IN',
  resource_type VARCHAR(40) COMMENT '资源类型：DOC/CHAPTER/QUIZ/CODE/REVIEW_CARD/RECALL/CHAT/KNOWLEDGE/PATH/CHECKIN',
  resource_id BIGINT COMMENT '关联资源ID（可为空）',
  metadata LONGTEXT COMMENT '事件扩展信息（JSON）：分数/耗时/章节/题目/连续天数等',
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
  KEY idx_le_user_time (user_id, create_time),
  KEY idx_le_user_type (user_id, event_type, create_time),
  KEY idx_le_resource (resource_type, resource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 知识点掌握度引擎（Phase 2-B：Knowledge Mastery Engine）
-- 设计要点：
--   1) knowledge_mastery：以 (user_id, knowledge_id=kg_entity.id) 为主键维度；
--   2) resource_knowledge_mapping：资源 → 知识点（kg_entity）映射层；
--   3) 仅逻辑外键 + 应用层维护，无物理外键；整行重算保证幂等。
-- ============================================================

-- ---------- 资源 → 知识点映射表 ----------
CREATE TABLE IF NOT EXISTS resource_knowledge_mapping (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  resource_type VARCHAR(40) NOT NULL COMMENT '资源类型：QUIZ/CODE_QUESTION/MISTAKE/REVIEW_CARD/RECALL_SESSION/FLASHCARD/DOC',
  resource_id   BIGINT NOT NULL COMMENT '关联资源ID（逻辑外键，按 resource_type 指向对应业务表）',
  knowledge_id  BIGINT NOT NULL COMMENT '知识点ID（逻辑外键 kg_entity.id，仅可学习类型）',
  source        VARCHAR(20) NOT NULL DEFAULT 'AUTO' COMMENT '映射来源：MANUAL/AI/IMPORT/AUTO/CATEGORY_FALLBACK',
  confidence    DECIMAL(4,3) DEFAULT 0 COMMENT '置信度 0~1',
  status        VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '映射状态：ACCEPTED/PENDING/REJECTED',
  create_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted       INT      DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
  UNIQUE KEY uk_rkm_res_know (resource_type, resource_id, knowledge_id),
  KEY idx_rkm_knowledge (knowledge_id),
  KEY idx_rkm_resource (resource_type, resource_id),
  KEY idx_rkm_status (source, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------- 知识点掌握度表 ----------
CREATE TABLE IF NOT EXISTS knowledge_mastery (
  id                    BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  user_id               BIGINT NOT NULL COMMENT '所属用户ID（逻辑外键 sys_user.id）',
  knowledge_id          BIGINT NOT NULL COMMENT '知识点ID（逻辑外键 kg_entity.id）',
  mastery_score         INT    DEFAULT 0 COMMENT '掌握度 0~100',
  confidence_score      INT    DEFAULT 0 COMMENT '置信度 0~100',
  learning_status       VARCHAR(20) DEFAULT 'NOT_STARTED' COMMENT '学习状态：NOT_STARTED/LEARNING/WEAK/MASTERED/REVIEW_REQUIRED',
  correct_count         INT    DEFAULT 0 COMMENT 'Quiz 答对累计',
  wrong_count           INT    DEFAULT 0 COMMENT 'Quiz 答错累计',
  attempt_count         INT    DEFAULT 0 COMMENT 'Quiz 作答次数累计',
  review_count          INT    DEFAULT 0 COMMENT '复习次数累计',
  recall_count          INT    DEFAULT 0 COMMENT '主动回忆会话次数累计',
  recall_avg_score      INT    DEFAULT 0 COMMENT '主动回忆平均得分 0~100',
  coding_attempt_count   INT    DEFAULT 0 COMMENT '代码提交次数累计',
  coding_pass_count      INT    DEFAULT 0 COMMENT '代码完全通过次数累计',
  mistake_count         INT    DEFAULT 0 COMMENT '关联错题数累计',
  mistake_mastered      INT    DEFAULT 0 COMMENT '已掌握错题数累计',
  consecutive_correct   INT    DEFAULT 0 COMMENT '当前连续答对',
  consecutive_wrong     INT    DEFAULT 0 COMMENT '当前连续答错',
  last_learned_at       TIMESTAMP COMMENT '最近学习/互动时间',
  last_reviewed_at      TIMESTAMP COMMENT '最近复习时间',
  last_assessed_at      TIMESTAMP COMMENT '最近测评时间',
  next_review_at        TIMESTAMP COMMENT '下次应复习时间',
  forgetting_risk       INT    DEFAULT 0 COMMENT '遗忘风险 0~100',
  create_time          TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time           TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted               INT      DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
  UNIQUE KEY uk_km_user_knowledge (user_id, knowledge_id),
  KEY idx_km_status (user_id, learning_status),
  KEY idx_km_risk (user_id, forgetting_risk),
  KEY idx_km_next (user_id, next_review_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
-- ============ Architecture-2：Learning Economy（经济层，6 张表） ============
CREATE TABLE wallet (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    xp INT DEFAULT 0 COMMENT '经验值',
    coin INT DEFAULT 0 COMMENT '金币',
    level INT DEFAULT 1 COMMENT '等级',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    UNIQUE KEY uk_wallet_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE reward_ledger (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    currency VARCHAR(8) NOT NULL COMMENT '币种：XP / COIN',
    amount INT NOT NULL,
    direction VARCHAR(8) NOT NULL COMMENT 'CREDIT / DEBIT',
    reason VARCHAR(50) NOT NULL,
    source_type VARCHAR(20) NOT NULL,
    source_id BIGINT,
    balance_after INT NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    UNIQUE KEY uk_ledger_idempotent (user_id, source_type, source_id, currency),
    KEY idx_ledger_user_time (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_key VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    type VARCHAR(20) DEFAULT 'APP' COMMENT 'APP / AI_CAPABILITY / CONTENT',
    app_key VARCHAR(50),
    price_coin INT DEFAULT 0 COMMENT '价格（Coin）',
    status VARCHAR(20) DEFAULT 'ACTIVE',
    version VARCHAR(20) DEFAULT '1.0.0',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    UNIQUE KEY uk_product_key (product_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE order_ (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(40) NOT NULL,
    user_id BIGINT NOT NULL,
    total_coin INT NOT NULL,
    status VARCHAR(20) DEFAULT 'PAID',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    UNIQUE KEY uk_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE order_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_key VARCHAR(50) NOT NULL,
    product_name VARCHAR(100) NOT NULL,
    price_coin INT NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    KEY idx_order_item_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE entitlement (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_key VARCHAR(50) NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    granted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    UNIQUE KEY uk_entitlement_user_product (user_id, product_id),
    KEY idx_entitlement_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============ Architecture-3：UserApp 后端化（1 张表） ============
CREATE TABLE user_app (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    app_key VARCHAR(50) NOT NULL COMMENT 'AppDefinition.key',
    installed INT DEFAULT 1 COMMENT '是否安装',
    enabled INT DEFAULT 1 COMMENT '是否启用',
    sort_order INT DEFAULT 0 COMMENT '排序',
    last_open_at TIMESTAMP NULL COMMENT '最近打开（未来推荐用）',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    UNIQUE KEY uk_user_app (user_id, app_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============ Architecture-4.0：学习目标（Learning Goal，学习闭环起点） ============
CREATE TABLE IF NOT EXISTS learning_goal (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  user_id       BIGINT NOT NULL COMMENT '所属用户ID（逻辑外键 sys_user.id）',
  title         VARCHAR(200) NOT NULL COMMENT '目标标题：一句话目标，如「7 天掌握 JVM GC」',
  description   VARCHAR(1000) NULL COMMENT '目标描述（为什么学）',
  target        VARCHAR(500) NULL COMMENT '达标标准（做到什么算掌握）',
  status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/COMPLETED/ARCHIVED',
  path_id       BIGINT NULL COMMENT '关联学习路径ID（逻辑外键 learning_path.id，可空）',
  start_date    DATE NULL COMMENT '开始日期',
  deadline      DATE NULL COMMENT '截止日期（时间盒终点）',
  daily_minutes INT DEFAULT 0 COMMENT '每日计划投入分钟（0=未约定）',
  create_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted       INT DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
  KEY idx_lg_user (user_id, status),
  KEY idx_lg_path (path_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============ Architecture-4.0-4：场景练习（Scenario Lab，Practice 节点） ============
CREATE TABLE IF NOT EXISTS learning_scenario (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  title         VARCHAR(200) NOT NULL COMMENT '场景标题，如「线上 Full GC 频繁排查」',
  context_text  TEXT NULL COMMENT '情景背景描述',
  question_text VARCHAR(1000) NOT NULL COMMENT '要用户回答的问题',
  key_points    TEXT NOT NULL COMMENT '要点清单 JSON：[{label, keywords[]}]',
  difficulty    INT DEFAULT 1 COMMENT '难度 1-3',
  knowledge_id  BIGINT NULL COMMENT '关联知识点ID（逻辑外键 kg_entity.id，可空）',
  status        INT DEFAULT 1 COMMENT '1 上架 / 0 下架',
  create_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted       INT DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
  KEY idx_lsc_status (status),
  KEY idx_lsc_knowledge (knowledge_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
-- Attachment ownership survives removal of content references; doc_id=0 is legacy public.
CREATE TABLE IF NOT EXISTS doc_asset_binding (
    asset_key CHAR(64) PRIMARY KEY,
    asset_url VARCHAR(2048) NOT NULL,
    doc_id BIGINT NOT NULL
);

-- ==================================================================
-- Phase 3E · Java + AI 智能考试系统（Exam OS）
-- ==================================================================
CREATE TABLE IF NOT EXISTS exam_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_key VARCHAR(64) NOT NULL COMMENT '稳定模板标识',
    name VARCHAR(200) NOT NULL COMMENT '模板名称',
    description VARCHAR(1000) COMMENT '模板说明',
    domain VARCHAR(32) NOT NULL DEFAULT 'JAVA' COMMENT 'JAVA / AI / JAVA_AI',
    topics VARCHAR(500) COMMENT '知识点标签（逗号分隔）',
    spec TEXT NOT NULL COMMENT '组卷规格 JSON',
    duration_minutes INT NOT NULL DEFAULT 60 COMMENT '考试时长（分钟）',
    total_score INT NOT NULL DEFAULT 100 COMMENT '总分',
    pass_score INT NOT NULL DEFAULT 60 COMMENT '及格分',
    mode VARCHAR(16) NOT NULL DEFAULT 'EXAM' COMMENT 'PRACTICE / EXAM',
    status INT DEFAULT 1 COMMENT '1 启用 / 0 停用',
    version VARCHAR(24) DEFAULT '1.0',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    UNIQUE KEY uk_et_key (template_key, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='试卷模板';

CREATE TABLE IF NOT EXISTS exam_paper (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL COMMENT '试卷名称',
    domain VARCHAR(32) COMMENT '领域',
    topics VARCHAR(500) COMMENT '覆盖知识点',
    source VARCHAR(16) NOT NULL COMMENT 'TEMPLATE / SMART / AI',
    template_key VARCHAR(64) COMMENT '来源模板标识',
    strategy VARCHAR(32) DEFAULT 'RANDOM',
    seed_value BIGINT COMMENT '随机种子',
    duration_minutes INT NOT NULL,
    total_score INT NOT NULL,
    pass_score INT NOT NULL DEFAULT 60,
    mode VARCHAR(16) NOT NULL DEFAULT 'EXAM',
    shuffle_question INT DEFAULT 0,
    shuffle_option INT DEFAULT 0,
    allow_pause INT DEFAULT 1,
    allow_change INT DEFAULT 1,
    config_json TEXT COMMENT '组卷输入完整快照',
    bank_version VARCHAR(64) COMMENT '题库版本/指纹',
    ai_meta TEXT COMMENT 'AI 出卷元数据',
    review_status VARCHAR(24) DEFAULT 'APPROVED' COMMENT 'APPROVED / PENDING_REVIEW',
    owner_user_id BIGINT COMMENT 'NULL 系统公共卷',
    status INT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    KEY idx_ep_owner (owner_user_id),
    KEY idx_ep_template (template_key),
    KEY idx_ep_source (source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='试卷快照头';

CREATE TABLE IF NOT EXISTS exam_paper_question (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    paper_id BIGINT NOT NULL,
    question_id BIGINT COMMENT '题库ID，AI 快照题为 NULL',
    ordinal INT NOT NULL,
    score INT NOT NULL DEFAULT 5,
    question_type VARCHAR(24) NOT NULL,
    topic VARCHAR(64),
    scoring_rule VARCHAR(32) DEFAULT 'ALL_OR_NOTHING' COMMENT 'ALL_OR_NOTHING / PARTIAL_CREDIT',
    snapshot TEXT NOT NULL COMMENT '题目快照 JSON',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    KEY idx_epq_paper (paper_id),
    KEY idx_epq_question (question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='试卷题目快照';

CREATE TABLE IF NOT EXISTS exam_attempt (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    paper_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    mode VARCHAR(16),
    status VARCHAR(24) NOT NULL DEFAULT 'CREATED',
    start_time TIMESTAMP NULL,
    server_deadline TIMESTAMP NULL COMMENT '服务端截止时间',
    submit_time TIMESTAMP NULL,
    duration_seconds INT,
    draft TEXT COMMENT '自动保存草稿 JSON',
    marked TEXT COMMENT '标记题号 JSON',
    total_score INT,
    max_score INT COMMENT '已评分题满分合计',
    paper_max_score INT,
    correct_count INT DEFAULT 0,
    graded_count INT DEFAULT 0,
    question_count INT DEFAULT 0,
    grading_status VARCHAR(24) DEFAULT 'NONE' COMMENT 'NONE/PENDING_AI/AI_GRADED/AI_FAILED/REVIEW_REQUIRED',
    ai_meta TEXT,
    client_version VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    KEY idx_ea_user (user_id),
    KEY idx_ea_paper (paper_id),
    KEY idx_ea_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考试尝试';

CREATE TABLE IF NOT EXISTS exam_answer (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    attempt_id BIGINT NOT NULL,
    paper_question_id BIGINT NOT NULL,
    question_id BIGINT,
    ordinal INT,
    topic VARCHAR(64),
    user_answer TEXT,
    is_correct INT,
    score INT COMMENT '未评分为 NULL',
    max_score INT,
    grade_type VARCHAR(16) COMMENT 'RULE / AI / PENDING',
    grade_detail TEXT,
    time_cost INT DEFAULT 0,
    graded INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    UNIQUE KEY uk_eans_attempt_pq (attempt_id, paper_question_id, deleted),
    KEY idx_eans_attempt (attempt_id),
    KEY idx_eans_question (question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='逐题作答与评分证据';

-- ==================== Phase 3G：统一题库分类 + 算法题（CODE）扩展 ====================

CREATE TABLE IF NOT EXISTS question_topic (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) NOT NULL COMMENT '稳定编码，如 ALGO_ARRAY / ALGO_DP',
    name VARCHAR(100) NOT NULL COMMENT '展示名称',
    parent_code VARCHAR(64) COMMENT '父级编码；NULL 为一级分类',
    level INT NOT NULL DEFAULT 1 COMMENT '层级：1 一级 / 2 二级',
    sort_order INT DEFAULT 0,
    status INT DEFAULT 1 COMMENT '1 启用 / 0 停用',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE UNIQUE INDEX uk_qt_code ON question_topic (code, deleted);

CREATE TABLE IF NOT EXISTS code_problem (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    question_id BIGINT NOT NULL COMMENT '统一题目ID quiz_question.id',
    supported_languages VARCHAR(200) DEFAULT 'java',
    function_signature LONGTEXT COMMENT '固定函数签名',
    starter_code LONGTEXT COMMENT '编辑器初始代码',
    constraints LONGTEXT COMMENT '约束条件（Markdown）',
    examples LONGTEXT COMMENT '展示示例 JSON（公开）',
    time_limit_ms INT DEFAULT 1000,
    memory_limit_mb INT DEFAULT 256,
    judge_mode VARCHAR(24) DEFAULT 'FUNCTION' COMMENT 'FUNCTION / STDOUT',
    checker VARCHAR(24) DEFAULT 'EXACT' COMMENT 'EXACT/NUMERIC/UNORDERED/BOOLEAN',
    tolerance_value DOUBLE DEFAULT 0,
    test_case_version VARCHAR(24) DEFAULT 'v1',
    reference_solution LONGTEXT COMMENT '参考解答（保密）',
    solution_explanation LONGTEXT COMMENT '题解（按作答状态开放）',
    complexity_analysis LONGTEXT COMMENT '复杂度分析',
    follow_ups LONGTEXT COMMENT '面试追问 JSON',
    common_mistakes LONGTEXT COMMENT '常见错误 JSON',
    harness_class VARCHAR(200),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE UNIQUE INDEX uk_cp_question ON code_problem (question_id, deleted);

CREATE TABLE IF NOT EXISTS code_test_case (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    problem_id BIGINT NOT NULL COMMENT 'code_problem.id',
    input LONGTEXT NOT NULL COMMENT '入参 JSON',
    expected_output LONGTEXT NOT NULL COMMENT '期望输出 JSON',
    visibility VARCHAR(16) NOT NULL DEFAULT 'PUBLIC' COMMENT 'PUBLIC / HIDDEN',
    weight INT DEFAULT 1,
    order_no INT DEFAULT 0,
    version VARCHAR(24) DEFAULT 'v1',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_ctc_problem ON code_test_case (problem_id);

CREATE TABLE IF NOT EXISTS code_draft (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    language VARCHAR(20) NOT NULL DEFAULT 'java',
    source_code LONGTEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE UNIQUE INDEX uk_cd_user_question ON code_draft (user_id, question_id, deleted);

CREATE TABLE IF NOT EXISTS code_submission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL COMMENT 'quiz_question.id（CODE 题）',
    problem_version VARCHAR(24) DEFAULT 'v1',
    language VARCHAR(20) NOT NULL DEFAULT 'java',
    source_code LONGTEXT NOT NULL,
    submission_type VARCHAR(16) NOT NULL DEFAULT 'SUBMIT' COMMENT 'RUN / SUBMIT',
    judge_status VARCHAR(24) NOT NULL DEFAULT 'QUEUED' COMMENT 'QUEUED/ACCEPTED/WRONG_ANSWER/COMPILE_ERROR/RUNTIME_ERROR/TIME_LIMIT_EXCEEDED/MEMORY_LIMIT_EXCEEDED/SYSTEM_ERROR/JUDGE_UNAVAILABLE',
    score INT COMMENT '百分制得分（NULL=未完成判题）',
    passed_count INT DEFAULT 0,
    total_count INT DEFAULT 0,
    execution_time_ms INT,
    memory_used_kb INT,
    compile_output LONGTEXT,
    case_results LONGTEXT COMMENT '逐用例结果 JSON；隐藏用例仅汇总',
    judge_detail LONGTEXT COMMENT '判题审计 JSON',
    context_type VARCHAR(16) DEFAULT 'PRACTICE' COMMENT 'PRACTICE / EXAM',
    context_ref_id BIGINT COMMENT 'EXAM 时为 exam_answer.id',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_cs_user ON code_submission (user_id);
CREATE INDEX idx_cs_question ON code_submission (question_id);
CREATE INDEX idx_cs_status ON code_submission (judge_status);
CREATE INDEX idx_cs_context ON code_submission (context_type, context_ref_id);

-- Phase 3H：模拟面试会话（基础模式最小闭环；预置题+追问树+手工作答+量表自评；不依赖 AI）
CREATE TABLE mock_interview_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    track VARCHAR(32) NOT NULL COMMENT '方向编码',
    mode VARCHAR(16) NOT NULL DEFAULT 'BASIC' COMMENT 'BASIC 基础模式 / AI 模式预留',
    status VARCHAR(16) NOT NULL DEFAULT 'IN_PROGRESS' COMMENT 'IN_PROGRESS/FINISHED',
    question_count INT NOT NULL,
    questions_json TEXT NOT NULL COMMENT '题目快照（题干/参考要点/追问树）',
    answers_json TEXT COMMENT '作答快照 JSON',
    avg_self_score DECIMAL(4,1) COMMENT '自评均分 1-5',
    duration_sec INT COMMENT '服务端计算的实际用时秒',
    started_at DATETIME,
    finished_at DATETIME,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_mis_user ON mock_interview_session (user_id);
CREATE INDEX idx_mis_status ON mock_interview_session (status);

-- Question Catalog mutation audit: additive only, no source identity migration.
CREATE TABLE IF NOT EXISTS question_catalog_audit (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, actor_id BIGINT NOT NULL,
 source_type VARCHAR(32) NOT NULL, source_question_id BIGINT NOT NULL,
 action VARCHAR(32) NOT NULL, before_state VARCHAR(512), after_state VARCHAR(512),
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Phase 3I-G: independent interview AI advice, never an answer or score source.
CREATE TABLE IF NOT EXISTS mock_interview_ai_turn (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, session_id BIGINT NOT NULL,
 question_id VARCHAR(100) NOT NULL, answer_fingerprint VARCHAR(64) NOT NULL,
 round_no INT NOT NULL, request_id VARCHAR(80) NOT NULL, status VARCHAR(16) NOT NULL,
 source_snapshot MEDIUMTEXT NOT NULL, followup_answer TEXT, result_json TEXT, error_message VARCHAR(240),
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE (user_id,request_id), UNIQUE (session_id,question_id,round_no)
);

-- Phase4E: confirmation drafts only, actual tasks stay in task.
CREATE TABLE IF NOT EXISTS learning_action_draft (
 id VARCHAR(36) PRIMARY KEY, user_id BIGINT NOT NULL, version INT NOT NULL DEFAULT 1,
 state VARCHAR(20) NOT NULL, payload TEXT NOT NULL, result_json TEXT,
 idempotency_key VARCHAR(80), request_hash VARCHAR(64),
 expires_at TIMESTAMP NOT NULL, create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Phase 4I interview V2 additive tables
CREATE TABLE IF NOT EXISTS mock_interview_speech (
 id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '转写记录ID',
 user_id BIGINT NOT NULL COMMENT '认证用户ID',
 session_id BIGINT NOT NULL COMMENT '所属面试会话',
 question_id VARCHAR(100) NOT NULL COMMENT '会话快照题目ID',
 request_id VARCHAR(80) NOT NULL COMMENT '用户范围幂等键',
 request_hash VARCHAR(64) NOT NULL COMMENT '录音和请求上下文摘要',
 status VARCHAR(20) NOT NULL COMMENT 'TRANSCRIBING/TRANSCRIBED/FAILED',
 raw_text TEXT COMMENT '未经用户确认的原始转写',
 error_message VARCHAR(300) COMMENT '脱敏错误',
 duration_ms BIGINT NOT NULL COMMENT 'PCM内容计算的真实时长毫秒',
 claimed_duration_ms BIGINT NOT NULL COMMENT '客户端声明时长仅供审计',
 v2_version BIGINT COMMENT '录音预留时V2版本基础会话为空',
 v2_depth INT COMMENT '录音预留时追问深度',
 v2_prompt_hash VARCHAR(64) COMMENT '完整题干UTF8的SHA256摘要',
 provider VARCHAR(100) NOT NULL COMMENT '服务提供方审计标识不含密钥',
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
 UNIQUE KEY uk_interview_speech_request(user_id,request_id),
 CONSTRAINT fk_interview_speech_session FOREIGN KEY(session_id) REFERENCES mock_interview_session(id)
) COMMENT='模拟面试语音转写（不保存音频，不自动提交答案）';

-- 增量辅助表：不改写原会话快照，不清空用户历史；同一脚本可重复运行。
CREATE TABLE IF NOT EXISTS mock_interview_v2_state (
 session_id BIGINT PRIMARY KEY COMMENT '原面试会话主键',
 user_id BIGINT NOT NULL COMMENT '服务端认证的会话所属用户',
 start_request VARCHAR(80) NOT NULL COMMENT '创建幂等键',
 start_hash VARCHAR(64) NOT NULL COMMENT '创建请求指纹',
 version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
 state VARCHAR(32) NOT NULL COMMENT '服务端面试状态',
 mode VARCHAR(20) NOT NULL COMMENT 'BASIC基础 STRICT严格 TEACHING教学',
 role VARCHAR(100) NOT NULL COMMENT '目标岗位，仅作为不可信上下文',
 difficulty VARCHAR(20) NOT NULL COMMENT '用户选择的面试难度',
 max_depth INT NOT NULL COMMENT '最大连续追问深度',
 question_index INT NOT NULL DEFAULT 0 COMMENT '当前主问题序号，从零开始',
 followup_depth INT NOT NULL DEFAULT 0 COMMENT '当前追问深度',
 duration_minutes INT NOT NULL COMMENT '有效面试时间上限分钟',
 deadline DATETIME NOT NULL COMMENT '服务端有效截止时间',
 paused_at DATETIME COMMENT '服务端暂停时间',
 current_prompt VARCHAR(2000) NOT NULL COMMENT '当前服务端决定的问题',
 current_question_id VARCHAR(100) NOT NULL COMMENT '原会话快照题目身份',
 project_json LONGTEXT COMMENT '已授权项目资料及用户描述快照',
 message VARCHAR(1000) COMMENT '降级或超时提示，不包含密钥',
 created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
 updated_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '最后状态变更时间',
 CONSTRAINT fk_iv2_session FOREIGN KEY(session_id) REFERENCES mock_interview_session(id),
 INDEX idx_iv2_owner(user_id,state),
 CONSTRAINT uk_iv2_start UNIQUE(user_id,start_request),
 CONSTRAINT ck_iv2_state CHECK(state IN ('CREATED','ASKING','ANSWERING','TRANSCRIBING','ANSWER_CONFIRMED','EVALUATING','DECIDING','FOLLOW_UP','PAUSED','COMPLETED')),
 CONSTRAINT ck_iv2_depth CHECK(max_depth BETWEEN 0 AND 3 AND followup_depth BETWEEN 0 AND 3)
) COMMENT='面试V2的受控状态，不建立第二套学习状态';

CREATE TABLE IF NOT EXISTS mock_interview_v2_turn (
 id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '确认轮次身份',
 session_id BIGINT NOT NULL COMMENT '原面试会话身份',
 user_id BIGINT NOT NULL COMMENT '服务端所属用户',
 request_id VARCHAR(80) NOT NULL COMMENT '确认提交幂等键',
 request_hash VARCHAR(64) NOT NULL COMMENT '确认内容指纹，拒绝同键不同正文',
 question_id VARCHAR(100) NOT NULL COMMENT '主问题快照身份',
 prompt VARCHAR(2000) NOT NULL COMMENT '本轮真正问过的问题',
 depth INT NOT NULL COMMENT '本轮连续追问深度',
 answer LONGTEXT NOT NULL COMMENT '用户明确确认后的文字，仅此文字可评分',
 speech_id BIGINT COMMENT '原始语音转写身份，不能重复作为新轮次消费',
 self_score INT COMMENT '用户自评分，独立于AI评分，可为空',
 evaluation_json LONGTEXT COMMENT '经结构及原文引用校验的AI建议，含模型和版本',
 decision VARCHAR(24) COMMENT '服务端最终决定的追问或下一题',
 source VARCHAR(24) NOT NULL COMMENT 'AI或预置回退来源',
 created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '确认时间',
 CONSTRAINT fk_iv2_turn_session FOREIGN KEY(session_id) REFERENCES mock_interview_session(id),
 INDEX idx_iv2_turn_session(session_id,id),
 CONSTRAINT uk_iv2_answer UNIQUE(user_id,request_id),
 CONSTRAINT uk_iv2_position UNIQUE(session_id,question_id,depth),
 CONSTRAINT uk_iv2_speech UNIQUE(speech_id),
 CONSTRAINT ck_iv2_self_score CHECK(self_score IS NULL OR self_score BETWEEN 1 AND 5)
) COMMENT='真实确认回答与追问链，不混用自评与AI评价';

CREATE TABLE IF NOT EXISTS mock_interview_v2_command (
 user_id BIGINT NOT NULL COMMENT '服务端用户',
 request_id VARCHAR(80) NOT NULL COMMENT '命令幂等键',
 session_id BIGINT NOT NULL COMMENT '原会话身份',
 operation VARCHAR(20) NOT NULL COMMENT 'pause resume finish命令',
 request_hash VARCHAR(64) NOT NULL COMMENT '版本和操作指纹',
 created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '执行时间',
 PRIMARY KEY(user_id,request_id),
 CONSTRAINT fk_iv2_command_session FOREIGN KEY(session_id) REFERENCES mock_interview_session(id)
) COMMENT='面试状态操作幂等记录';

-- task_id 关联既有 task；会话行锁及联合主键保证跨请求幂等。
-- 不级联删除，不清理逻辑删除记录的历史关联；归属由服务端校验。
CREATE TABLE IF NOT EXISTS interview_remediation (
 session_id BIGINT NOT NULL COMMENT '来源面试会话ID，关联mock_interview_session.id',
 knowledge_id BIGINT NOT NULL COMMENT '实际可阅读知识实体ID，关联kg_entity.id；同会话去重',
 user_id BIGINT NOT NULL COMMENT '服务端认证用户ID，关联sys_user.id；会话及任务归属由服务校验',
 task_id BIGINT NOT NULL COMMENT '复用既有任务ID，关联task.id；不自动完成任务',
 request_id VARCHAR(100) NOT NULL COMMENT '首次确认请求标识；去重按会话与知识实体而非请求标识',
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次明确确认并创建关联的时间',
 PRIMARY KEY (session_id, knowledge_id),
 KEY idx_interview_remediation_user(user_id,session_id),
 CONSTRAINT fk_interview_remediation_session FOREIGN KEY (session_id) REFERENCES mock_interview_session(id),
 CONSTRAINT fk_interview_remediation_knowledge FOREIGN KEY (knowledge_id) REFERENCES kg_entity(id),
 CONSTRAINT fk_interview_remediation_task FOREIGN KEY (task_id) REFERENCES task(id),
 CONSTRAINT fk_interview_remediation_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
 CONSTRAINT ck_interview_remediation_ids CHECK (session_id > 0 AND knowledge_id > 0 AND user_id > 0 AND task_id > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户明确确认的面试复习任务关联；保留历史且不写掌握度';

-- 笔记历史快照，恢复会创建新版本。
CREATE TABLE IF NOT EXISTS learning_note_version (id BIGINT AUTO_INCREMENT PRIMARY KEY,note_id BIGINT NOT NULL COMMENT '所属笔记',user_id BIGINT NOT NULL COMMENT '修改者',revision BIGINT NOT NULL COMMENT '修订号',title VARCHAR(200),content LONGTEXT,tags VARCHAR(2000),source_title VARCHAR(500),folder_id BIGINT,favorite BOOLEAN NOT NULL DEFAULT FALSE,source_doc_id BIGINT,chapter_id BIGINT,path_id BIGINT,status VARCHAR(20),note_deleted INT NOT NULL DEFAULT 0,reason VARCHAR(40) COMMENT '版本来源',create_time TIMESTAMP NOT NULL,UNIQUE(note_id,revision));

-- ========== 技术英语 P0：公共词库 / 发音变体 / 用户状态 / 概念关联 ==========
-- 设计原则：词条是系统公共知识；收藏与学习状态是用户私有数据，二者分表隔离。
-- 唯一键均包含 deleted，兼容逻辑删除（deleted=1 不释放唯一约束）。

-- 技术英语词条：同一 headword 可因领域/义项不同并存（normalized_key + domain + sense_key 去重）
CREATE TABLE IF NOT EXISTS tech_vocab_term (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    headword VARCHAR(200) NOT NULL COMMENT '英文主词、短语或缩写',
    normalized_key VARCHAR(200) NOT NULL COMMENT '搜索归一化键（小写+空白折叠），不做唯一含义判定',
    term_type VARCHAR(20) NOT NULL DEFAULT 'WORD' COMMENT 'WORD / ACRONYM / PHRASE',
    sense_key VARCHAR(100) NOT NULL DEFAULT 'default' COMMENT '同形异义义项键',
    domain VARCHAR(40) NOT NULL COMMENT '技术领域编码，如 JAVA / JVM / SPRING / DB / LLM / RAG / AGENT',
    full_form VARCHAR(300) COMMENT '缩写全称（普通词为空）',
    class_name VARCHAR(200) COMMENT '对应 Java 类名（如 AbstractQueuedSynchronizer）',
    part_of_speech VARCHAR(40) COMMENT '词性（单词适用）',
    ipa_us VARCHAR(200) COMMENT '经核验的美式音标；无可靠音标时留空',
    ipa_uk VARCHAR(200) COMMENT '经核验的英式音标；无可靠音标时留空',
    meaning_cn VARCHAR(300) NOT NULL COMMENT '中文短释义',
    definition_cn TEXT COMMENT '中文技术概念解释（专业含义优先）',
    definition_en TEXT COMMENT '英文专业解释（可选）',
    word_breakdown TEXT COMMENT '全称逐词拆解 JSON：[{"word":"Abstract","meaningCn":"抽象的"}]',
    breakdown_note VARCHAR(500) COMMENT '拆解说明：完整概念不是各单词含义的机械相加',
    interview_en TEXT COMMENT '面试英文表达：一句可用于技术面试的回答',
    example_en TEXT COMMENT '技术英文例句',
    example_cn TEXT COMMENT '例句中文解释',
    aliases VARCHAR(500) COMMENT '别名/中文别名，逗号分隔，仅用于搜索',
    content_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT / REVIEWED / PUBLISHED / ARCHIVED',
    reference_source VARCHAR(1000) COMMENT '定义、全称与读法来源',
    license_note VARCHAR(500) COMMENT '内容/音频许可说明',
    reviewed_at TIMESTAMP NULL COMMENT '最近审核时间',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted INT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_tech_term_sense (normalized_key, domain, sense_key, deleted),
    KEY idx_tech_term_domain (domain, content_status, deleted),
    KEY idx_tech_term_type (term_type, content_status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='技术英语公共词条；同一词形可按领域分义项';

-- 发音变体：单词 US/UK、缩写读法 ABBR、全称读法 FULL_FORM 分开存储。
CREATE TABLE IF NOT EXISTS tech_vocab_pronunciation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    term_id BIGINT NOT NULL COMMENT '所属词条ID（逻辑外键 tech_vocab_term.id）',
    variant VARCHAR(30) NOT NULL COMMENT 'WORD_US / WORD_UK / ABBR / FULL_FORM',
    reading_text VARCHAR(500) COMMENT '经审核的朗读文本；不等于任意拼接的提示词',
    locale VARCHAR(20) COMMENT 'en-US / en-GB 等',
    reading_mode VARCHAR(30) COMMENT 'LETTER_BY_LETTER / SPOKEN_WORD / MIXED',
    audio_url VARCHAR(1000) COMMENT '已授权音频地址，可为空',
    audio_provider VARCHAR(100) COMMENT '音频提供方',
    license_note VARCHAR(500) COMMENT '音频许可说明',
    verification_status VARCHAR(20) NOT NULL DEFAULT 'UNVERIFIED' COMMENT 'VERIFIED / PENDING / UNAVAILABLE',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted INT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_tech_pron_variant (term_id, variant, deleted),
    KEY idx_tech_pron_term (term_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='词条发音变体与授权音频元数据';

-- 用户词条状态：收藏与学习状态是私有数据，必须按认证用户隔离。
CREATE TABLE IF NOT EXISTS user_vocab_state (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID（逻辑外键 sys_user.id，服务端认证上下文写入）',
    term_id BIGINT NOT NULL COMMENT '词条ID（逻辑外键 tech_vocab_term.id）',
    favorite INT NOT NULL DEFAULT 0 COMMENT '是否收藏：0 否 / 1 是',
    learning_status VARCHAR(20) NOT NULL DEFAULT 'NEW' COMMENT 'NEW / LEARNING / MASTERED（展示层状态）',
    linked_flashcard_id BIGINT COMMENT 'P1 预留：关联既有闪卡ID，不新建复习算法',
    last_opened_at TIMESTAMP NULL COMMENT '最近打开时间',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted INT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_user_vocab_term (user_id, term_id, deleted),
    KEY idx_user_vocab_favorite (user_id, favorite, deleted),
    KEY idx_user_vocab_status (user_id, learning_status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户词条收藏与学习状态（私有数据）';

-- 概念关联：term -> 既有知识实体的稳定 ID 关联，查询时必须二次校验目标权限。
CREATE TABLE IF NOT EXISTS tech_vocab_concept_link (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    term_id BIGINT NOT NULL COMMENT '来源词条ID',
    target_type VARCHAR(30) NOT NULL COMMENT 'KNOWLEDGE / CHAPTER / DOCUMENT / NOTE / TERM',
    target_id BIGINT NOT NULL COMMENT '目标实体稳定ID（禁止用标题文本代替）',
    relation_type VARCHAR(30) NOT NULL DEFAULT 'RELATED' COMMENT 'EXPLAINS / RELATED / CONTRASTS / CONFUSABLE',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted INT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_tech_link (term_id, target_type, target_id, relation_type, deleted),
    KEY idx_tech_link_target (target_type, target_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='词条与技术知识实体的稳定关联';

-- P2 个人候选词条：AI / 规则提取结果，与公共词库物理隔离，审核通过后才生成正式词条。
CREATE TABLE IF NOT EXISTS tech_vocab_candidate (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '提交用户',
    headword VARCHAR(200) NOT NULL COMMENT '候选英文主词',
    normalized_key VARCHAR(200) NOT NULL COMMENT '归一化键（个人候选去重）',
    term_type VARCHAR(20) NOT NULL DEFAULT 'WORD' COMMENT 'WORD / ACRONYM / PHRASE',
    domain VARCHAR(40) NOT NULL COMMENT '建议技术领域',
    full_form VARCHAR(300) COMMENT '建议英文全称（需人工核验）',
    meaning_cn VARCHAR(300) COMMENT '建议中文释义',
    definition_cn TEXT COMMENT '建议中文解释',
    source_type VARCHAR(20) NOT NULL DEFAULT 'TEXT' COMMENT 'DOCUMENT / NOTE / CHAPTER / TEXT',
    source_id BIGINT COMMENT '来源实体ID，TEXT 来源为空',
    source_title VARCHAR(300) COMMENT '来源标题快照（不对外公开）',
    source_context VARCHAR(500) COMMENT '来源上下文片段',
    confidence INT COMMENT '置信提示 0-100，不代表已核验',
    extractor VARCHAR(10) NOT NULL DEFAULT 'RULE' COMMENT 'RULE / AI',
    review_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / APPROVED / REJECTED',
    duplicate_term_id BIGINT COMMENT '与公共词库重复时的词条ID',
    term_id BIGINT COMMENT '审核通过后生成的正式词条ID',
    review_note VARCHAR(500) COMMENT '审核备注',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted INT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_tech_candidate (user_id, normalized_key, source_type, source_id, deleted),
    KEY idx_tech_candidate_owner (user_id, review_status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='技术英语个人候选词条（待审核）';

-- 笔记V2组织与关系
CREATE TABLE IF NOT EXISTS learning_note_folder(id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT NOT NULL COMMENT '所属用户',parent_id BIGINT COMMENT '父文件夹',name VARCHAR(100) NOT NULL COMMENT '目录名',revision BIGINT NOT NULL DEFAULT 1 COMMENT '目录修订号',INDEX idx_note_folder_owner(user_id,parent_id));
CREATE TABLE IF NOT EXISTS learning_note_tag(id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT NOT NULL,name VARCHAR(100) NOT NULL COMMENT '标签名',name_key VARCHAR(100) NOT NULL COMMENT '规范化唯一名',UNIQUE(user_id,name_key));
CREATE TABLE IF NOT EXISTS learning_note_tag_ref(user_id BIGINT NOT NULL,note_id BIGINT NOT NULL,tag_id BIGINT NOT NULL,PRIMARY KEY(note_id,tag_id),INDEX idx_note_tag_owner(user_id,tag_id,note_id));
CREATE TABLE IF NOT EXISTS learning_note_relation(id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT NOT NULL,note_id BIGINT NOT NULL,target_type VARCHAR(10) NOT NULL COMMENT 'DOC或NOTE',target_id BIGINT NOT NULL,UNIQUE(user_id,note_id,target_type,target_id));
