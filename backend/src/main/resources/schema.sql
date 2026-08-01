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
    owner_user_id BIGINT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_lp_owner ON learning_path (owner_user_id);

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

-- 闪卡表（用户维度隔离，支持关联知识库/文档来源、间隔重复复习算法）
CREATE TABLE IF NOT EXISTS learning_flashcard (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '所属用户ID（逻辑外键sys_user.id）',
    path_id BIGINT COMMENT '归属学习路径ID（逻辑外键learning_path.id，可为空）',
    chapter_id BIGINT COMMENT '归属章节ID（逻辑外键learning_chapter.id，可为空）',
    category_id BIGINT COMMENT '关联知识库/分类ID（逻辑外键doc_category.id）',
    doc_id BIGINT COMMENT '来源文档ID（逻辑外键doc_document.id）',
    front TEXT NOT NULL COMMENT '正面：问题/概念',
    back TEXT NOT NULL COMMENT '背面：答案/解释',
    category VARCHAR(50) COMMENT '用户自定义分类标签',
    difficulty INT DEFAULT 1 COMMENT '难度：1简单 2中等 3困难',
    tags VARCHAR(500) COMMENT '逗号分隔的自定义标签',
    source_type VARCHAR(20) DEFAULT 'MANUAL' COMMENT '来源：MANUAL/AI_DOC/AI_KB/IMPORT',
    review_count INT DEFAULT 0 COMMENT '已复习次数',
    review_interval INT DEFAULT 0 COMMENT '当前复习间隔（天，间隔重复算法）',
    next_review_time TIMESTAMP COMMENT '下次应复习时间',
    last_review_time TIMESTAMP COMMENT '上次复习时间',
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
-- 闪卡索引（遵循阿里规范：所有逻辑外键列必须建索引）
CREATE INDEX IF NOT EXISTS idx_fc_user        ON learning_flashcard (user_id);
CREATE INDEX IF NOT EXISTS idx_fc_path        ON learning_flashcard (path_id);
CREATE INDEX IF NOT EXISTS idx_fc_chap        ON learning_flashcard (chapter_id);
CREATE INDEX IF NOT EXISTS idx_fc_category    ON learning_flashcard (category_id);
CREATE INDEX IF NOT EXISTS idx_fc_doc         ON learning_flashcard (doc_id);
CREATE INDEX IF NOT EXISTS idx_fc_next_review ON learning_flashcard (next_review_time);
CREATE INDEX IF NOT EXISTS idx_fc_deleted     ON learning_flashcard (deleted);
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

-- ========== 在线答题记录：用户作答历史与判分结果 ==========
CREATE TABLE IF NOT EXISTS quiz_answer_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '用户ID（逻辑外键 sys_user.id）',
    question_id BIGINT NOT NULL COMMENT '题目ID（逻辑外键 quiz_question.id）',
    user_answer TEXT COMMENT '用户提交的答案',
    is_correct INT DEFAULT 0 COMMENT '是否答对：0 错误 / 1 正确',
    score INT DEFAULT 0 COMMENT '本题得分（0-100）',
    time_cost INT DEFAULT 0 COMMENT '答题耗时（秒）',
    ai_feedback TEXT COMMENT 'AI 评语（简答题等主观题预留）',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删'
);
CREATE INDEX IF NOT EXISTS idx_qar_user ON quiz_answer_record (user_id);
CREATE INDEX IF NOT EXISTS idx_qar_question ON quiz_answer_record (question_id);
CREATE INDEX IF NOT EXISTS idx_qar_correct ON quiz_answer_record (is_correct);

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
);
-- 同一用户同一自然日仅允许一条打卡记录（幂等约束）
CREATE UNIQUE INDEX IF NOT EXISTS uk_uci_user_date ON user_check_in (user_id, check_date);
CREATE INDEX IF NOT EXISTS idx_uci_user ON user_check_in (user_id);

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
);
CREATE INDEX IF NOT EXISTS idx_sg_owner ON study_group (owner_id);
CREATE INDEX IF NOT EXISTS idx_sg_type ON study_group (type);
CREATE INDEX IF NOT EXISTS idx_sg_deleted ON study_group (deleted);

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
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sgm_group_user ON study_group_member (group_id, user_id, deleted);
CREATE INDEX IF NOT EXISTS idx_sgm_user ON study_group_member (user_id);
CREATE INDEX IF NOT EXISTS idx_sgm_role ON study_group_member (role);

-- 学习小组消息表
CREATE TABLE IF NOT EXISTS study_group_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    group_id BIGINT NOT NULL COMMENT '小组ID',
    sender_id BIGINT NOT NULL COMMENT '发送者ID',
    message_type VARCHAR(20) DEFAULT 'TEXT' COMMENT '消息类型：TEXT 文本 / IMAGE 图片 / FILE 文件 / CODE 代码块',
    content TEXT COMMENT '消息内容',
    file_url VARCHAR(500) COMMENT '文件URL',
    file_name VARCHAR(200) COMMENT '文件名',
    file_size BIGINT COMMENT '文件大小（字节）',
    code_language VARCHAR(20) COMMENT '代码语言',
    mention_user_ids VARCHAR(500) COMMENT '@提及的用户ID列表，逗号分隔',
    recalled INT DEFAULT 0 COMMENT '是否已撤回：0 否 / 1 是',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删'
);
CREATE INDEX IF NOT EXISTS idx_sgm_group ON study_group_message (group_id);
CREATE INDEX IF NOT EXISTS idx_sgm_sender ON study_group_message (sender_id);
CREATE INDEX IF NOT EXISTS idx_sgm_type ON study_group_message (message_type);
CREATE INDEX IF NOT EXISTS idx_sgm_ctime ON study_group_message (create_time);

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
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_pc_users ON private_conversation (user_a_id, user_b_id, deleted);
CREATE INDEX IF NOT EXISTS idx_pc_user_a ON private_conversation (user_a_id);
CREATE INDEX IF NOT EXISTS idx_pc_user_b ON private_conversation (user_b_id);

-- 私聊已读游标表
CREATE TABLE IF NOT EXISTS private_conversation_read (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    conversation_id BIGINT NOT NULL COMMENT '会话ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    last_read_message_id BIGINT COMMENT '最后已读消息ID',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删'
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_pcr_conv_user ON private_conversation_read (conversation_id, user_id, deleted);
CREATE INDEX IF NOT EXISTS idx_pcr_user ON private_conversation_read (user_id);

-- 私聊消息表
CREATE TABLE IF NOT EXISTS private_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    conversation_id BIGINT NOT NULL COMMENT '会话ID',
    sender_id BIGINT NOT NULL COMMENT '发送者ID',
    message_type VARCHAR(20) DEFAULT 'TEXT' COMMENT '消息类型：TEXT 文本 / IMAGE 图片 / FILE 文件 / CODE 代码块',
    content TEXT COMMENT '消息内容',
    file_url VARCHAR(500) COMMENT '文件URL',
    file_name VARCHAR(200) COMMENT '文件名',
    file_size BIGINT COMMENT '文件大小（字节）',
    code_language VARCHAR(20) COMMENT '代码语言',
    recalled INT DEFAULT 0 COMMENT '是否已撤回：0 否 / 1 是',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删'
);
CREATE INDEX IF NOT EXISTS idx_pm_conv ON private_message (conversation_id);
CREATE INDEX IF NOT EXISTS idx_pm_sender ON private_message (sender_id);
CREATE INDEX IF NOT EXISTS idx_pm_ctime ON private_message (create_time);

-- ============================================================
-- AI 生成结果缓存表
-- ============================================================

-- 概念可视化图解缓存表（按用户+概念名维度缓存，避免重复调用 AI）
CREATE TABLE IF NOT EXISTS ai_concept_diagram (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '所属用户ID（逻辑外键sys_user.id）',
    concept VARCHAR(100) NOT NULL COMMENT '概念名称',
    diagram_type VARCHAR(20) DEFAULT 'FLOWCHART' COMMENT '图解类型：FLOWCHART/SEQUENCE/CLASS/ER/PIE',
    mermaid_code TEXT COMMENT 'Mermaid 语法源码',
    description VARCHAR(1000) COMMENT '概念简要说明',
    explanation TEXT COMMENT 'AI 详细解释',
    difficulty INT DEFAULT 1 COMMENT '难度：1 入门 / 2 中等 / 3 进阶',
    key_points TEXT COMMENT '关键知识点列表（JSON 数组字符串）',
    related_concepts TEXT COMMENT '关联概念列表（JSON 数组字符串）',
    code_example TEXT COMMENT '代码示例（可为空）',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删'
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_acd_user_concept ON ai_concept_diagram (user_id, concept, deleted);
CREATE INDEX IF NOT EXISTS idx_acd_user ON ai_concept_diagram (user_id);

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
    goals_text TEXT COMMENT '学习目标列表（JSON 数组字符串）',
    chapters_text TEXT COMMENT '章节规划（JSON 数组字符串）',
    advice TEXT COMMENT 'AI 学习建议',
    related_path_id BIGINT COMMENT '关联已有路径ID（可为空）',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删'
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_app_user_goal ON ai_personalized_path (user_id, goal, level, daily_minutes, deleted);
CREATE INDEX IF NOT EXISTS idx_app_user ON ai_personalized_path (user_id);

-- ============================================================
-- 编程挑战（编程闯关：赛道 / 关卡 / 用户进度 / 关卡记录，游戏化积分）
-- ============================================================

-- 挑战赛道表：一个赛道由若干关卡组成（如「JavaScript 十题闯关」）
CREATE TABLE IF NOT EXISTS code_challenge (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL COMMENT '挑战标题',
    description TEXT COMMENT '挑战简介',
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
);
CREATE INDEX IF NOT EXISTS idx_cc_status ON code_challenge (status);
CREATE INDEX IF NOT EXISTS idx_cc_sort   ON code_challenge (sort_order);

-- 挑战关卡表：内嵌题目信息，使赛道自包含（不依赖 code_question）
CREATE TABLE IF NOT EXISTS code_challenge_level (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    challenge_id BIGINT NOT NULL COMMENT '所属挑战ID（逻辑外键code_challenge.id）',
    level_no INT NOT NULL COMMENT '关卡序号，从 1 开始递增',
    title VARCHAR(200) NOT NULL COMMENT '关卡标题',
    description TEXT COMMENT '题目描述（多行文本）',
    difficulty INT DEFAULT 0 COMMENT '难度：0 简单 / 1 中等 / 2 困难',
    language VARCHAR(20) DEFAULT 'javascript' COMMENT '语言标识',
    hint TEXT COMMENT '关卡提示',
    example_input TEXT COMMENT '输入示例',
    example_output TEXT COMMENT '输出示例',
    code_template TEXT COMMENT '代码模板（编辑器初始内容）',
    test_cases TEXT COMMENT '测试用例 JSON 数组：[{input, expected}]',
    points INT DEFAULT 10 COMMENT '通关积分',
    status INT DEFAULT 1 COMMENT '状态：0 草稿 / 1 已发布',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_ccl_challenge ON code_challenge_level (challenge_id);
CREATE INDEX IF NOT EXISTS idx_ccl_no        ON code_challenge_level (challenge_id, level_no);

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
    finish_time TIMESTAMP COMMENT '通关时间',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_ccr_user      ON code_challenge_record (user_id);
CREATE INDEX IF NOT EXISTS idx_ccr_user_ch   ON code_challenge_record (user_id, challenge_id);

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
);
CREATE INDEX IF NOT EXISTS idx_ach_category ON achievement (category);
CREATE UNIQUE INDEX IF NOT EXISTS idx_ach_code ON achievement (code);

-- 用户成就解锁记录表：用户与成就多对多关系，同成就仅能解锁一次
CREATE TABLE IF NOT EXISTS user_achievement (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID（逻辑外键sys_user.id）',
    achievement_id BIGINT NOT NULL COMMENT '成就ID（逻辑外键achievement.id）',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_ua_user_ach ON user_achievement (user_id, achievement_id);
CREATE INDEX IF NOT EXISTS idx_ua_user ON user_achievement (user_id);

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
    last_code TEXT COMMENT '最近一次提交的代码',
    finish_time TIMESTAMP COMMENT '通关时间',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_cclr_user     ON code_challenge_level_record (user_id);
CREATE INDEX IF NOT EXISTS idx_cclr_user_lvl ON code_challenge_level_record (user_id, level_id);

-- ========== 文档分块与向量索引（A-RAG 文档向量检索）==========

-- 文档分块表：存储文档切分后的片段与 embedding 向量
CREATE TABLE IF NOT EXISTS doc_chunk (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    doc_id BIGINT NOT NULL COMMENT '归属文档ID（逻辑外键 doc_document.id）',
    chunk_index INT NOT NULL COMMENT '分块序号（从 0 开始）',
    content TEXT NOT NULL COMMENT '分块文本内容',
    char_count INT DEFAULT 0 COMMENT '字符数',
    embedding TEXT COMMENT 'embedding 向量：逗号分隔的浮点数组（如 "0.123,-0.456,..."）',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_dc_doc ON doc_chunk (doc_id);
CREATE INDEX IF NOT EXISTS idx_dc_doc_order ON doc_chunk (doc_id, chunk_index);

-- L-PATH-01 章节前置依赖：在 learning_chapter 表添加前置章节ID列表
ALTER TABLE learning_chapter ADD COLUMN IF NOT EXISTS prerequisite_chapter_ids VARCHAR(500) DEFAULT NULL COMMENT '前置章节ID列表，逗号分隔（如 "1,3,5"），全部完成后该章节才可学习';

-- L-FORM-01 章节嵌入视频进度追踪：在用户章节进度表添加视频观看进度（0-100）
ALTER TABLE learning_user_chapter ADD COLUMN IF NOT EXISTS video_progress DECIMAL(5,2) DEFAULT 0 COMMENT '视频观看进度百分比(0-100)，达标后允许完成章节';

-- ========== 代码提交记录（P-CODE-03 代码判题记录持久化）==========
CREATE TABLE IF NOT EXISTS code_submit_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID（逻辑外键 sys_user.id）',
    question_id BIGINT NOT NULL COMMENT '题目ID（逻辑外键 code_question.id）',
    code TEXT NOT NULL COMMENT '提交的代码',
    language VARCHAR(20) DEFAULT 'javascript' COMMENT '编程语言',
    total INT DEFAULT 0 COMMENT '总测试用例数',
    pass_count INT DEFAULT 0 COMMENT '通过用例数',
    passed INT DEFAULT 0 COMMENT '是否完全通过：0 未通过 / 1 已通过',
    error_msg TEXT COMMENT '运行时错误信息',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_csr_user ON code_submit_record (user_id);
CREATE INDEX IF NOT EXISTS idx_csr_question ON code_submit_record (question_id);
CREATE INDEX IF NOT EXISTS idx_csr_user_q ON code_submit_record (user_id, question_id);

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
);
CREATE INDEX IF NOT EXISTS idx_kg_entity_doc ON kg_entity (doc_id);
CREATE INDEX IF NOT EXISTS idx_kg_entity_category ON kg_entity (category_id);
CREATE INDEX IF NOT EXISTS idx_kg_entity_name ON kg_entity (name);

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
);
CREATE INDEX IF NOT EXISTS idx_kg_rel_source ON kg_relation (source_entity_id);
CREATE INDEX IF NOT EXISTS idx_kg_rel_target ON kg_relation (target_entity_id);
CREATE INDEX IF NOT EXISTS idx_kg_rel_doc ON kg_relation (doc_id);

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
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_cert_no ON learning_certificate (cert_no);
CREATE INDEX IF NOT EXISTS idx_cert_user ON learning_certificate (user_id);
CREATE INDEX IF NOT EXISTS idx_cert_path ON learning_certificate (path_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_cert_user_path ON learning_certificate (user_id, path_id, deleted);
