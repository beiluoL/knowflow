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
    complete_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
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
    review_count INT DEFAULT 0,
    last_review_time TIMESTAMP NULL DEFAULT NULL,
    mastered INT DEFAULT 0,
    source VARCHAR(50),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
    question_type VARCHAR(20) NOT NULL DEFAULT 'SINGLE_CHOICE' COMMENT '题型：SINGLE_CHOICE 单选 / MULTIPLE_CHOICE 多选 / FILL_BLANK 填空 / TRUE_FALSE 判断 / SHORT_ANSWER 简答',
    options LONGTEXT COMMENT '选项 JSON 数组（选择题用），如 ["选项A","选项B","选项C","选项D"]',
    answer LONGTEXT NOT NULL COMMENT '正确答案：选择题填选项索引(如 "0" 或 "0,2")，填空题填答案文本，判断题填 true/false',
    explanation LONGTEXT COMMENT '答案解析',
    difficulty INT DEFAULT 1 COMMENT '难度：1 简单 / 2 中等 / 3 困难',
    category_id BIGINT COMMENT '关联知识库ID（逻辑外键 doc_category.id）',
    doc_id BIGINT COMMENT '关联文档ID（逻辑外键 doc_document.id）',
    tags VARCHAR(500) COMMENT '标签，逗号分隔',
    source VARCHAR(20) DEFAULT 'MANUAL' COMMENT '来源：AI 生成 / MANUAL 手动新增',
    status INT DEFAULT 1 COMMENT '状态：0 草稿 / 1 已发布',
    sort_order INT DEFAULT 0 COMMENT '排序值',
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
  round1_text LONGTEXT COMMENT '第一轮：即时默写内容',
  round1_score INT COMMENT '第一轮得分（0-100）',
  round2_text LONGTEXT COMMENT '第二轮：补漏默写内容',
  round2_score INT COMMENT '第二轮得分（0-100）',
  round3_text LONGTEXT COMMENT '第三轮：1小时后复测内容',
  round3_score INT COMMENT '第三轮得分（0-100）',
  current_round INT DEFAULT 1 COMMENT '当前轮次：1 / 2 / 3',
  status VARCHAR(20) DEFAULT 'IN_PROGRESS' COMMENT '会话状态：IN_PROGRESS 进行中 / COMPLETED 已完成',
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