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
);

-- 文档表
CREATE TABLE IF NOT EXISTS doc_document (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT,
    summary VARCHAR(500),
    cover VARCHAR(255),
    icon VARCHAR(255),
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
    deleted INT DEFAULT 0
);

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
    deleted INT DEFAULT 0
);

-- 收藏表
CREATE TABLE IF NOT EXISTS doc_favorite (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    doc_id BIGINT NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
);

-- 阅读进度表
CREATE TABLE IF NOT EXISTS doc_read_progress (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    doc_id BIGINT NOT NULL,
    progress DECIMAL(5,2) DEFAULT 0,
    read_seconds INT DEFAULT 0,
    last_read_time TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
);

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
);

-- 消息表
CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT,
    doc_references VARCHAR(1000),
    token_count INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
);

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
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
);

-- 章节表
CREATE TABLE IF NOT EXISTS learning_chapter (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    path_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT,
    sort_order INT DEFAULT 0,
    duration INT DEFAULT 0,
    doc_ids VARCHAR(500),
    flashcard_ids VARCHAR(500),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
);

-- 用户学习路径进度表
CREATE TABLE IF NOT EXISTS learning_user_path (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    path_id BIGINT NOT NULL,
    progress DECIMAL(5,2) DEFAULT 0,
    completed_chapters INT DEFAULT 0,
    enroll_time TIMESTAMP,
    last_study_time TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
);

-- 闪卡表
CREATE TABLE IF NOT EXISTS learning_flashcard (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    path_id BIGINT,
    chapter_id BIGINT,
    front TEXT,
    back TEXT,
    category VARCHAR(50),
    difficulty INT DEFAULT 1,
    review_count INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
);

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
    deadline TIMESTAMP,
    status INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
);

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
ALTER TABLE sys_user ADD CONSTRAINT IF NOT EXISTS uk_user_email UNIQUE (email);
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
ALTER TABLE doc_favorite ADD CONSTRAINT IF NOT EXISTS uk_fav_user_doc UNIQUE (user_id, doc_id);
ALTER TABLE doc_read_progress ADD CONSTRAINT IF NOT EXISTS uk_rp_user_doc UNIQUE (user_id, doc_id);
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
    deleted INT DEFAULT 0
);
CREATE INDEX idx_uc_user_chapter ON learning_user_chapter (user_id);
CREATE INDEX idx_uc_chapter_id   ON learning_user_chapter (chapter_id);
CREATE UNIQUE INDEX uk_uc_user_chapter ON learning_user_chapter (user_id, chapter_id);

-- 自定义图标表
CREATE TABLE IF NOT EXISTS sys_icon (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(20) DEFAULT 'custom',
    content TEXT NOT NULL,
    color VARCHAR(20),
    user_id BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
);
CREATE INDEX idx_icon_user ON sys_icon (user_id);
CREATE INDEX idx_icon_type ON sys_icon (type);
CREATE INDEX idx_fc_path      ON learning_flashcard (path_id);
CREATE INDEX idx_fc_chap      ON learning_flashcard (chapter_id);
-- SM-2 间隔重复算法所需字段
ALTER TABLE learning_flashcard ADD COLUMN IF NOT EXISTS review_interval INT DEFAULT 0;
ALTER TABLE learning_flashcard ADD COLUMN IF NOT EXISTS next_review_time TIMESTAMP;
ALTER TABLE learning_flashcard ADD COLUMN IF NOT EXISTS last_review_time TIMESTAMP;
CREATE INDEX idx_task_user    ON learning_task (user_id);
CREATE INDEX idx_task_status  ON learning_task (status);

-- 错题表
CREATE TABLE IF NOT EXISTS learning_mistake (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    question TEXT NOT NULL,
    wrong_answer TEXT,
    correct_answer TEXT,
    category VARCHAR(50),
    difficulty INT DEFAULT 1,
    review_count INT DEFAULT 0,
    last_review_time TIMESTAMP,
    mastered INT DEFAULT 0,
    source VARCHAR(50),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
);

-- 社区帖子表
CREATE TABLE IF NOT EXISTS community_post (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT,
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
);

-- 帖子点赞关系表（F-10：点赞幂等 + 可取消，用户-帖子联合唯一）
CREATE TABLE IF NOT EXISTS community_post_like (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    CONSTRAINT uk_post_like UNIQUE (post_id, user_id)
);

-- 社区评论表
CREATE TABLE IF NOT EXISTS community_comment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
);

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
);

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
CREATE INDEX idx_comment_deleted ON community_comment (deleted);

-- 消息通知索引
CREATE INDEX idx_notif_user   ON sys_notification (user_id);
CREATE INDEX idx_notif_type   ON sys_notification (type);
CREATE INDEX idx_notif_read   ON sys_notification (is_read);
CREATE INDEX idx_notif_deleted ON sys_notification (deleted);

-- 用户 AI 配置表
CREATE TABLE IF NOT EXISTS sys_user_ai_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    provider VARCHAR(50) NOT NULL COMMENT '模型提供商: deepseek / siliconflow / openai / custom',
    api_key VARCHAR(500) NOT NULL COMMENT '用户自己的 API Key',
    base_url VARCHAR(255) COMMENT '自定义 API 地址（留空用默认）',
    model VARCHAR(100) COMMENT '默认模型名',
    is_active INT DEFAULT 1 COMMENT '是否启用: 1 启用 / 0 禁用',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
);
CREATE UNIQUE INDEX uk_user_ai_config_user ON sys_user_ai_config (user_id, deleted);

-- 代码练习题目表（B 端题库管理 + C 端代码练习共用）
CREATE TABLE IF NOT EXISTS code_question (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL COMMENT '题目标题',
    description TEXT NOT NULL COMMENT '题目描述（多行文本）',
    difficulty INT DEFAULT 0 COMMENT '难度：0 简单 / 1 中等 / 2 困难',
    language VARCHAR(20) DEFAULT 'javascript' COMMENT '主语言：javascript/typescript/python/java/sql',
    tags VARCHAR(500) COMMENT '题目标签，逗号分隔',
    hint TEXT COMMENT '题目提示',
    example_input TEXT COMMENT '输入示例',
    example_output TEXT COMMENT '输出示例',
    code_template TEXT COMMENT '函数签名模板（编辑器初始内容）',
    test_cases TEXT COMMENT '测试用例 JSON 数组：[{input, expected}]',
    solution_hint TEXT COMMENT '预期解法关键词，用于 AI 回答提示',
    duration INT DEFAULT 30 COMMENT '建议做题时长（分钟）',
    sort_order INT DEFAULT 0 COMMENT '排序值，越小越靠前',
    status INT DEFAULT 1 COMMENT '状态：0 草稿 / 1 已发布',
    pass_count INT DEFAULT 0 COMMENT '通过次数',
    submit_count INT DEFAULT 0 COMMENT '提交次数',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
);
CREATE INDEX idx_cq_status    ON code_question (status);
CREATE INDEX idx_cq_difficulty ON code_question (difficulty);
CREATE INDEX idx_cq_language  ON code_question (language);
CREATE INDEX idx_cq_sort      ON code_question (sort_order);

-- ============ 知识库成员与权限 ============

-- 分类（知识库）增加 owner_id 字段（逻辑外键 → sys_user.id），用于标识创建者/拥有者
ALTER TABLE doc_category ADD COLUMN IF NOT EXISTS owner_id BIGINT COMMENT '知识库所有者用户ID（逻辑外键sys_user.id）';
CREATE INDEX IF NOT EXISTS idx_cat_owner ON doc_category (owner_id);

-- 知识库成员表：多对多关联，每个成员持有角色与权限
CREATE TABLE IF NOT EXISTS kb_member (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    category_id BIGINT NOT NULL COMMENT '知识库ID（逻辑外键doc_category.id）',
    user_id BIGINT COMMENT '用户ID（逻辑外键sys_user.id）；邮箱邀请未注册时为 NULL',
    role VARCHAR(20) NOT NULL DEFAULT 'READER' COMMENT '成员角色：OWNER / EDITOR / READER',
    invite_code VARCHAR(50) COMMENT '邀请码（可选，用于未注册邮箱邀请）',
    invite_email VARCHAR(100) COMMENT '邀请目标邮箱（未注册用户邀请时记录）',
    status INT DEFAULT 1 COMMENT '状态：0 已移除 / 1 生效',
    join_time TIMESTAMP COMMENT '加入时间',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删'
);
-- 联合唯一：一个已注册用户在一个知识库中只能有一条有效成员记录（user_id 为 NULL 不参与唯一冲突）
CREATE UNIQUE INDEX IF NOT EXISTS uk_kb_member_cat_user ON kb_member (category_id, user_id, deleted);
CREATE INDEX IF NOT EXISTS idx_kb_member_category ON kb_member (category_id);
CREATE INDEX IF NOT EXISTS idx_kb_member_user ON kb_member (user_id);
CREATE INDEX IF NOT EXISTS idx_kb_member_role ON kb_member (role);
CREATE INDEX IF NOT EXISTS idx_kb_member_status ON kb_member (status);
CREATE INDEX IF NOT EXISTS idx_kb_member_invite_email ON kb_member (invite_email);

-- ========== 题库管理：支持多题型的智能出题 ==========
CREATE TABLE IF NOT EXISTS quiz_question (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    title VARCHAR(500) NOT NULL COMMENT '题目标题',
    content TEXT NOT NULL COMMENT '题干内容（支持 Markdown）',
    question_type VARCHAR(20) NOT NULL DEFAULT 'SINGLE_CHOICE' COMMENT '题型：SINGLE_CHOICE 单选 / MULTIPLE_CHOICE 多选 / FILL_BLANK 填空 / TRUE_FALSE 判断 / SHORT_ANSWER 简答',
    options TEXT COMMENT '选项 JSON 数组（选择题用），如 ["选项A","选项B","选项C","选项D"]',
    answer TEXT NOT NULL COMMENT '正确答案：选择题填选项索引(如 "0" 或 "0,2")，填空题填答案文本，判断题填 true/false',
    explanation TEXT COMMENT '答案解析',
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
);
CREATE INDEX IF NOT EXISTS idx_qq_type ON quiz_question (question_type);
CREATE INDEX IF NOT EXISTS idx_qq_difficulty ON quiz_question (difficulty);
CREATE INDEX IF NOT EXISTS idx_qq_status ON quiz_question (status);
CREATE INDEX IF NOT EXISTS idx_qq_source ON quiz_question (source);
CREATE INDEX IF NOT EXISTS idx_qq_category ON quiz_question (category_id);
CREATE INDEX IF NOT EXISTS idx_qq_doc ON quiz_question (doc_id);
