# KnowFlow AI 学习平台 — 功能需求文档（PRD）

> **文档版本**：v1.3
> **更新日期**：2026-07-30
> **文档性质**：基于现有代码库全量调研后的功能差距分析与需求定义
> **调研方法**：前后端源码逐文件核实（Controller / Service / schema.sql / Vue 页面 / 路由 / API 层）

---

## 目录

- [1. 项目概述](#1-项目概述)
- [2. 功能现状总览](#2-功能现状总览)
- [3. 五大模块功能差距分析](#3-五大模块功能差距分析)
  - [3.1 智能学习门户](#31-智能学习门户)
  - [3.2 沉浸式学习助手](#32-沉浸式学习助手)
  - [3.3 实战演练场](#33-实战演练场)
  - [3.4 学习成果度量衡](#34-学习成果度量衡)
  - [3.5 社区与激励体系](#35-社区与激励体系)
- [4. 功能需求清单（按优先级）](#4-功能需求清单按优先级)
- [5. 技术方案建议](#5-技术方案建议)
- [6. 场景化需求专题：场景一·编程学习](#6-场景化需求专题场景一编程学习)
- [7. 附录：已实现功能索引](#7-附录已实现功能索引)

---

## 1. 项目概述

KnowFlow 是一个以 AI 驱动的知识学习平台，核心目标是构建「智能学习门户 → 沉浸式学习助手 → 实战演练场 → 学习成果度量 → 社区激励」的全链路学习闭环。

**技术栈**：
- 前端：Vue 3 + TypeScript + Composition API + Pinia + Vue Router + Element Plus 风格组件
- 后端：Spring Boot 3.2 + MyBatis-Plus + H2/MySQL + JWT + WebSocket
- AI：OpenAI 兼容接口（DeepSeek / 通义千问 / OpenAI / SiliconFlow），用户级 API Key 配置
- 文档解析：Apache Tika 2.9.2（PDF/DOC/DOCX/PPT/TXT/MD 统一抽取）

---

## 2. 功能现状总览

| 模块 | 已实现 | 部分实现 | 未实现 |
|------|--------|----------|--------|
| 智能学习门户 | 学习路径 CRUD、AI 生成路径/章节/闪卡、**DAG 依赖图可视化** | 自适应课程内容、嵌入视频/互动讲义 | 多元化课程形式 |
| 沉浸式学习助手 | 文档上传+Tika 解析、文字 AI 答疑、知识图谱（实体关系抽取） | AI 伴学（语音/图片不完整）、RAG（仅 LIKE） | 真正 RAG 向量检索、实时学习反馈 |
| 实战演练场 | 题库 CRUD、AI 出题、在线答题判分、浏览器端代码执行、多语言在线沙箱、AI 评估/调试/错题归集、编程挑战闯关 | 智能出题（简答 AI 评分未实现） | AI 模型实训、自动评分 |
| 学习成果度量衡 | 学习仪表盘、错题本、知识画像（知识点掌握度）、数字证书 | 学习画像（部分分类掌握度） | 全局学习成果汇总 |
| 社区与激励体系 | 社区帖子/评论/点赞、学习小组、私信、每日打卡、成就/勋章、排行榜 | 游戏化（通用分类排行未全落地） | 竞赛中心、作品展示 |

**统计**：已实现 29 项，部分实现 2 项，未实现 1 项，共计 32 项功能点（较 2026-07-30 新增学习门户增强 L-FORM-01/02、A-FB-01/02 与数字证书 G-CERT-01）。

---

## 3. 五大模块功能差距分析

### 3.1 智能学习门户

#### 3.1.1 个性化学习路径

**现状**：🟡 部分实现

- ✅ 已有：`learning_path` / `learning_chapter` / `learning_user_path` / `learning_user_chapter` 四张表
- ✅ 已有：`AdminLearningController.aiGeneratePath()` 支持 AI 基于主题+知识库文档自动生成学习路径及章节
- ✅ 已有：用户报名路径、章节逐个完成、进度百分比追踪
- ✅ 已实现：**知识依赖图（DAG）** — `learning_chapter.prerequisite_chapter_ids` 支持多前置节点（L-PATH-01），前端路径详情页用 dagre 布局 + 自绘 SVG 渲染交互式依赖图谱（拖拽/缩放/悬停高亮/全屏，按完成/可学/锁定三态着色），后端 `GET /api/learning/paths/{pathId}/dag` 返回节点+边
- ✅ 已实现：**AI 自动推断章节依赖（L-PATH-04）** — 个性化路径生成时 AI 输出 `prerequisiteSortOrders`，采用路径时解析为真实章节 ID 写入 `prerequisite_chapter_ids`（过滤自引用与后向依赖保证无环）
- ✅ 已实现：**前置知识解锁机制** — `getChapterList/Detail` 计算 `locked`，`completeChapter` 强制前置校验（L-PATH-02）

**需求定义**：

| 需求 ID | 需求描述 | 优先级 |
|---------|---------|--------|
| L-PATH-01 | 学习路径章节增加前置依赖关系（DAG），支持多前置节点 | P2 |
| L-PATH-02 | 章节解锁逻辑：前置章节全部完成后才允许学习当前章节 | P2 |
| L-PATH-03 | 路径可视化展示 DAG 图（前端 dagre 布局 + 自绘 SVG 交互式渲染章节依赖关系）✅ 已实现(2026-07-31) | P3 |
| L-PATH-04 | AI 生成路径时自动推断章节依赖关系 ✅ 已实现(2026-07-31) | P3 |

**数据模型变更建议**：
```sql
-- learning_chapter 新增字段
ALTER TABLE learning_chapter ADD COLUMN prerequisite_ids VARCHAR(500) COMMENT '前置章节ID列表，逗号分隔';
ALTER TABLE learning_chapter ADD COLUMN unlock_mode VARCHAR(20) DEFAULT 'SEQUENTIAL' COMMENT '解锁方式：SEQUENTIAL(顺序)/DAG(依赖图)';
```

#### 3.1.2 自适应课程内容

**现状**：❌ 未实现

- 章节内容为静态 Markdown，`LearningServiceImpl.completeChapter()` 仅更新进度百分比
- 无根据用户学习进度、答题正确率动态调整内容难度或推送补充材料的逻辑

**需求定义**：

| 需求 ID | 需求描述 | 优先级 |
|---------|---------|--------|
| L-ADAPT-01 | 章节内容支持「难度分级」标签（基础/进阶/挑战），根据用户掌握度自动展示对应难度 | P3 |
| L-ADAPT-02 | 用户答题正确率低于阈值时，自动推荐复习闪卡或前置章节 | P3 |
| L-ADAPT-03 | 章节末尾根据用户掌握情况动态生成「补充练习」 | P3 |

#### 3.1.3 多元化课程形式

**现状**：❌ 未实现

- 章节内容仅文本（`learning_chapter.content` 为 Markdown 字符串）
- 无互动视频、互动黑板、互动讲义

**需求定义**：

| 需求 ID | 需求描述 | 优先级 |
|---------|---------|--------|
| L-FORM-01 | 章节支持嵌入视频（外链 iframe + 进度追踪） | P3 |
| L-FORM-02 | 章节支持「互动讲义」模式：Markdown + 内嵌代码可运行 + 内嵌测验 | P3 |
| L-FORM-03 | 互动黑板：支持图文混排 + AI 实时批注 | P4 |

---

### 3.2 沉浸式学习助手

#### 3.2.1 7×24 AI 伴学

**现状**：🟡 部分实现

- ✅ 已有：`chat_conversation` / `chat_message` 表，`AiServiceImpl` 调用 OpenAI 兼容接口
- ✅ 已有：文字输入（完整对话流 + 历史记录）
- ✅ 已有：语音输入（Web Speech API 语音识别，前端 `Chat.vue:1190-1219`）
- ⚠️ 不足：图片上传仅为占位 — 前端 base64 上传后转文字描述发送，后端不支持多模态（`AiServiceImpl` 仅处理文本）
- ❌ 缺失：多智能体角色参与讨论

**需求定义**：

| 需求 ID | 需求描述 | 优先级 |
|---------|---------|--------|
| A-CHAT-01 | 后端 AI 服务支持多模态（图片输入），调用支持 vision 的模型 | P2 |
| A-CHAT-02 | 多智能体讨论：多个 AI 角色（如"导师"/"同学"/"考官"）参与同一对话 | P4 |

#### 3.2.2 智能资料处理

**现状**：🟡 部分实现

- ✅ 已有：文档上传 + Tika 解析（PDF/DOC/DOCX/PPT/TXT/MD，60s 超时 + 50 万字符上限 + 50MB 文件上限）
- ✅ 已有：知识图谱可视化（`KnowledgeGraph.vue` + `KnowledgeController`）
- ✅ 已落地（2026-07-30）：知识图谱已支持「AI 从文档抽取实体+关系，构建真正知识图谱」（`A-RAG-04`）。新增 `kg_entity`/`kg_relation` 表，`KnowledgeGraph.vue` 新增第 4 个 Tab「知识实体图」，文档上传时自动触发实体抽取；图谱按实体类型（概念/技术/术语/原理/工具）着色、按关系（前置/属于/组成/使用/对比）连线，点击节点查看说明与关联实体。
- ⚠️ 不足：RAG 实为 SQL `LIKE` 关键词匹配（`ChatServiceImpl.searchRelatedDocs()`），无向量化 / embedding / 向量数据库

**需求定义**：

| 需求 ID | 需求描述 | 优先级 |
|---------|---------|--------|
| A-RAG-01 | 引入 embedding 模型，文档入库时自动生成向量表示 | P1 |
| A-RAG-02 | 集成向量数据库（pgvector / Milvus / 或 H2 内存向量），支持相似度检索 | P1 |
| A-RAG-03 | AI 答疑时先检索相关文档片段（Top-K），作为 context 注入 prompt | P1 |
| A-RAG-04 | 知识图谱升级：AI 从文档正文抽取实体+关系，构建真正的知识图谱 | P3 |

**技术方案建议**：
```
方案 A（轻量）：使用 Spring AI + pgvector，文档入库时调用 embedding API 生成向量存入 pgvector，
                查询时向量相似度检索 Top-K 片段注入 prompt。
方案 B（零依赖）：在 H2 中用 FLOAT 数组存储向量，Java 层计算余弦相似度（适合小规模 <10K 文档）。
方案 C（独立服务）：部署 Milvus / Qdrant 作为独立向量数据库，通过 gRPC 访问。
推荐：方案 A，兼顾开发效率与生产可用性。
```

#### 3.2.3 实时学习反馈

**现状**：🟡 部分实现

- ✅ 已有：闪卡复习（SM-2 间隔重复算法）
- ✅ 已有：智能出题（`SmartQuiz.vue` 基于闪卡生成选择题）
- ❌ 缺失：学习过程中的即时概念关联推荐
- ❌ 缺失：知识点即时测验（嵌入章节学习中）

**需求定义**：

| 需求 ID | 需求描述 | 优先级 |
|---------|---------|--------|
| A-FB-01 | 章节学习页面右侧推荐「相关概念」「相关文档」 | P3 |
| A-FB-02 | 章节内嵌即时测验（每学完一节弹出 1-2 道题） | P3 |

---

### 3.3 实战演练场

#### 3.3.1 智能出题与批改

**现状**：🟡 部分实现

- ✅ 已有：`quiz_question` 表（支持单选/多选/填空/判断/简答 5 种题型）
- ✅ 已有：`AdminQuizQuestionController.aiGenerate()` AI 根据知识库/文档生成题目
- ✅ 已有：`code_question` 代码题库表
- ✅ 已有：**在线答题判分系统** — `QuizController` + `QuizServiceImpl` 支持客观题自动判分与答错幂等同步错题本（2026-07-30 已实现）
- ❌ 缺失：主观题 AI 辅助评分

**需求定义**：

| 需求 ID | 需求描述 | 优先级 |
|---------|---------|--------|
| P-QUIZ-01 | 在线答题界面：选择题/判断题/填空题支持用户作答 + 自动判分 | P0 |
| P-QUIZ-02 | 答题记录持久化：记录用户每次答题情况，关联错题本 | P0 |
| P-QUIZ-03 | 简答题 AI 辅助评分：调用大模型对比标准答案，给出分数 + 批改建议 | P2 |
| P-QUIZ-04 | 题库练习模式：随机出题 / 按知识点出题 / 错题重练 | P1 |

**数据模型变更建议**：
```sql
-- 答题记录表
CREATE TABLE IF NOT EXISTS quiz_answer_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '答题用户',
    question_id BIGINT NOT NULL COMMENT '题目ID',
    user_answer TEXT COMMENT '用户答案',
    is_correct INT COMMENT '是否正确：0错 1对 2部分正确',
    score INT COMMENT '得分（百分制）',
    ai_feedback TEXT COMMENT 'AI批改建议（简答题）',
    time_cost INT COMMENT '答题耗时（秒）',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
);
CREATE INDEX idx_ar_user ON quiz_answer_record (user_id);
CREATE INDEX idx_ar_question ON quiz_answer_record (question_id);
```

#### 3.3.2 代码实践环境

**现状**：🟡 部分实现

- ✅ 已有：`code_question` 表 + `CodePlayground.vue` 代码练习页
- ✅ 已有：浏览器端 JS/TS 执行（`new Function()`）、SQL 模拟执行（`sqlSimulator.ts`）
- ❌ 缺失：**云端 IDE** — 后端无代码执行能力，`CodeQuestionController` 注释明确"实际代码执行由前端完成"
- ❌ 缺失：**服务端判题** — 仅累计提交/通过次数，不判定代码正确性

**需求定义**：

| 需求 ID | 需求描述 | 优先级 |
|---------|---------|--------|
| P-CODE-01 | 后端集成代码执行沙箱（Docker / Judge0 / 隔离 JVM），支持 Java/Python/JS 运行 | P2 |
| P-CODE-02 | 代码题在线判题：运行测试用例，比对输出，返回 AC/WA/TLE/RE | P2 |
| P-CODE-03 | 代码提交记录持久化 + 提交历史查看 | P2 |

**技术方案建议**：
```
方案 A（自建）：Spring Boot 调用 Docker API，每次执行创建临时容器运行代码，超时 kill。
方案 B（SaaS）：集成 Judge0（开源判题系统），通过 REST API 提交代码 + 测试用例。
方案 C（轻量）：Java 内嵌 GraalVM Polyglot 直接执行 JS/Python，无 Docker 依赖。
推荐：方案 B，Judge0 已封装好沙箱隔离 + 多语言支持 + 测试用例比对。
```

#### 3.3.3 AI 模型实训

**现状**：❌ 未实现

**需求定义**：

| 需求 ID | 需求描述 | 优先级 |
|---------|---------|--------|
| P-ML-01 | 模型中心：展示可用 AI 模型列表，支持在线体验 | P4 |
| P-ML-02 | 数据标注中心：上传数据集 + 在线标注（分类/标注/NER） | P4 |
| P-ML-03 | 模型训练任务：提交训练任务，查看训练日志与指标 | P4 |

---

### 3.4 学习成果度量衡

#### 3.4.1 学习仪表盘

**现状**：✅ 已实现

- C 端 `LearningReport.vue`（路由 `/learning/center`）：学习热力图（120 天活动）、掌握分布看板、统计概览、快速入口
- B 端 `admin/Overview.vue`：用户增长曲线、健康度指标、最近活动流

#### 3.4.2 精准学习画像

**现状**：🟡 部分实现

- ✅ 已有：`sys_user` 表 level/exp/energy/streak_days 字段，`UserServiceImpl` 实时派生计算
- ✅ 已有：`Profile.vue` 展示等级经验条
- ❌ 缺失：**知识点维度掌握度分析** — 仅有闪卡难度分布、错题掌握计数，无按知识标签/分类维度的掌握度画像

**需求定义**：

| 需求 ID | 需求描述 | 优先级 |
|---------|---------|--------|
| R-PORTRAIT-01 | 按知识库/分类维度聚合：每个知识库的文档阅读率、闪卡掌握率、错题正确率 | P1 |
| R-PORTRAIT-02 | 知识标签维度画像：按 tags 聚合，展示各标签掌握度雷达图 | P2 |
| R-PORTRAIT-03 | 薄弱点识别：自动标记掌握度 < 60% 的知识点，生成复习建议 | P2 |

#### 3.4.3 智能错题本

**现状**：✅ 已实现

- `learning_mistake` 表 + `MistakeController` + `MistakeServiceImpl`
- 幂等添加、标记掌握、本周新增数、待复习数、已掌握/未掌握统计
- 前端 `Mistakes.vue` 页面

---

### 3.5 社区与激励体系

#### 3.5.1 游戏化学习

**现状**：🟡 部分实现

- ✅ 已有：`sys_user` 表 level/exp/energy/streak_days 字段
- ✅ 已有：**成就/勋章系统**（2026-07-30 已实现）— `achievement` + `user_achievement` 表，`Achievement.vue` 已接入真实 API（列表/进度/筛选/解锁时间线），路径完成/阅读/打卡等事件自动解锁
- ✅ 已有：**每日打卡** — `CheckInController` + `user_check_in` 表已实现真实签到/连续天数/奖励（2026-07-30 已实现）
- ❌ 缺失：**通用排行榜** — 仅编程挑战模块 `ChallengeRankVO` 有真实排行榜后端，全局经验/阅读/打卡排行仍缺失（G-RANK-01 已实现按 exp 降序 Top N，分类排行仍缺失）

**需求定义**：

| 需求 ID | 需求描述 | 优先级 |
|---------|---------|--------|
| G-CHECKIN-01 | 每日打卡后端接口：签到 + 连续天数 + 签到奖励（exp/energy） | P0 |
| G-CHECKIN-02 | 签到日历：展示当月签到记录 | P1 |
| G-ACHIEVE-01 | 成就系统：定义成就规则（阅读 10 篇/完成 1 条路径/连续 7 天打卡等） | P1 |
| G-ACHIEVE-02 | 勋章展示：已解锁/未解锁勋章列表 + 解锁条件 | P1 |
| G-RANK-01 | 排行榜后端：按周/月统计 exp 增量，返回 Top N 用户 | P1 |
| G-RANK-02 | 排行榜分类：经验排行 / 阅读量排行 / 连续打卡排行 | P2 |

**数据模型变更建议**：
```sql
-- 签到记录表
CREATE TABLE IF NOT EXISTS user_check_in (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    check_date DATE NOT NULL,
    continuous_days INT DEFAULT 1 COMMENT '连续签到天数',
    reward_exp INT DEFAULT 0 COMMENT '签到奖励经验',
    reward_energy INT DEFAULT 0 COMMENT '签到奖励能量',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
);
CREATE UNIQUE INDEX uk_checkin_user_date ON user_check_in (user_id, check_date);

-- 成就表
CREATE TABLE IF NOT EXISTS achievement (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL COMMENT '成就编码',
    name VARCHAR(100) NOT NULL COMMENT '成就名称',
    description VARCHAR(500) COMMENT '成就描述',
    icon VARCHAR(255) COMMENT '成就图标',
    category VARCHAR(50) COMMENT '分类：LEARNING/SOCIAL/SPECIAL',
    condition_type VARCHAR(50) COMMENT '条件类型：READ_DOCS/COMPLETE_PATH/STREAK_DAYS/...',
    condition_value INT COMMENT '条件阈值',
    reward_exp INT DEFAULT 0 COMMENT '奖励经验',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 用户成就解锁记录
CREATE TABLE IF NOT EXISTS user_achievement (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    achievement_id BIGINT NOT NULL,
    unlocked_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
);
CREATE UNIQUE INDEX uk_ua_user_ach ON user_achievement (user_id, achievement_id);
```

#### 3.5.2 竞赛中心

**现状**：❌ 未实现

**需求定义**：

| 需求 ID | 需求描述 | 优先级 |
|---------|---------|--------|
| G-COMP-01 | 竞赛创建与管理（管理端）：设定时间/题目/规则 | P3 |
| G-COMP-02 | 竞赛报名 + 在线答题 + 实时排名 | P3 |
| G-COMP-03 | 竞赛结束后成绩公示 + 奖励发放 | P3 |

#### 3.5.3 作品与证书

**现状**：🟡 部分实现（数字证书 G-CERT-01 已实现，作品展示 G-WORK-01 未实现）

**需求定义**：

| 需求 ID | 需求描述 | 优先级 |
|---------|---------|--------|
| G-WORK-01 | 用户作品展示页：上传项目截图 + 描述 + 关联学习路径 | P3 |
| G-CERT-01 | 学习路径完成后自动颁发数字证书（含验证码） | P3 |
| G-CERT-02 | 证书验证页：输入验证码查询证书真实性 | P4 |

---

## 4. 功能需求清单（按优先级）

### P0 — 必须实现（全部已落地 ✅）

| 编号 | 功能 | 模块 | 说明 |
|------|------|------|------|
| P-QUIZ-01 | 在线答题判分系统 | 实战演练场 | ✅ 已实现(2026-07-30)：`QuizController` + `QuizService` 自动判分 |
| P-QUIZ-02 | 答题记录持久化 | 实战演练场 | ✅ 已实现(2026-07-30)：`quiz_answer_record` 表持久化，同步错题本 |
| G-CHECKIN-01 | 每日打卡后端 | 社区激励 | ✅ 已实现(2026-07-30)：`CheckInController` + `user_check_in` 表真实签到 |

### P1 — 高优先级（全部已落地 ✅）

| 编号 | 功能 | 模块 | 说明 |
|------|------|------|------|
| A-RAG-01 | Embedding 生成 | 学习助手 | ✅ 已实现(2026-07-30)：`EmbeddingServiceImpl` 调用 AI 嵌入模型，文档上传自动分块+embedding |
| A-RAG-02 | 向量数据库集成 | 学习助手 | ✅ 已实现(2026-07-30)：H2 TEXT 字段存储向量，`DocChunkService.searchSimilar` 余弦相似度检索 |
| A-RAG-03 | RAG 答疑增强 | 学习助手 | ✅ 已实现(2026-07-30)：`ChatServiceImpl.searchRelatedDocs` 优先向量检索，降级 LIKE |
| P-QUIZ-04 | 题库练习模式 | 实战演练场 | ✅ 已实现(2026-07-30)：随机/按知识点/错题重练三种模式，`GET /api/quiz/mistake-practice` |
| R-PORTRAIT-01 | 知识库维度掌握度 | 学习画像 | ✅ 已实现(2026-07-30)：`MasteryDistributionVO` + `GET /api/learning/mastery-distribution` |
| G-CHECKIN-02 | 签到日历 | 社区激励 | ✅ 已实现(2026-07-30)：`CheckInService.getStatus()` 返回当月签到日历 |
| G-ACHIEVE-01 | 成就系统后端 | 社区激励 | ✅ 已实现(2026-07-30)：`achievement`+`user_achievement` 表 + 自动解锁 + EXP 奖励 |
| G-ACHIEVE-02 | 勋章展示 | 社区激励 | ✅ 已实现(2026-07-30)：`AchievementItemVO` 列表+进度+筛选+时间线，`Achievement.vue` 接入真实 API |
| G-RANK-01 | 排行榜后端 | 社区激励 | ✅ 已实现(2026-07-30)：`GET /api/ranking` 匿名可浏览，按 exp 降序 Top N |
| SC1-GRAPH-02 | 个性化 AI 路径（水平+目标） | 智能学习门户 | ✅ 已实现(2026-07-30)：`PersonalizedPathVO` + 生成/重生成/采用/列表/删除 5 个方法 |

### P2 — 中优先级（全部已落地 ✅）

| 编号 | 功能 | 模块 | 说明 |
|------|------|------|------|
| A-CHAT-01 | AI 多模态（图片输入） | 学习助手 | ✅ 已实现(2026-07-30)：`ChatSendDTO.images`+`AiServiceImpl.chatWithImages` vision 模型支持 |
| P-QUIZ-03 | 简答题 AI 评分 | 实战演练场 | ✅ 已实现(2026-07-30)：`AiService.gradeEssay` 大模型批改 + `judge()` 降级宽松包含匹配 |
| P-CODE-01 | 后端代码执行沙箱 | 实战演练场 | ✅ 已实现(2026-07-29)：进程直执行 Py/Java/JS/C++（Judge0 为生产升级路径） |
| P-CODE-02 | 代码在线判题 | 实战演练场 | ✅ 已实现(2026-07-30)：`code_submit_record` 表持久化每次提交的判题结果 |
| P-CODE-03 | 代码提交记录 | 实战演练场 | ✅ 已实现(2026-07-30)：`GET /api/code-questions/{id}/submissions` 提交历史查询 |
| L-PATH-01 | 章节依赖关系（DAG） | 学习门户 | ✅ 已实现(2026-07-30)：`prerequisite_chapter_ids` 字段+逗号分隔多前置节点 |
| L-PATH-02 | 前置知识解锁 | 学习门户 | ✅ 已实现(2026-07-30)：`getChapterList/Detail` 计算 locked + `completeChapter` 前置检查 |
| R-PORTRAIT-02 | 知识标签画像 | 学习画像 | ✅ 已实现(2026-07-30)：`CategoryMasteryVO` 按分类统计答题正确率雷达图 |
| R-PORTRAIT-03 | 薄弱点识别 | 学习画像 | ✅ 已实现(2026-07-30)：正确率 < 60% 自动标记 weak=true，`GET /api/learning/category-mastery` |
| G-RANK-02 | 排行榜分类 | 社区激励 | ✅ 已实现(2026-07-30)：`GET /api/ranking` 支持 period(type/all/week/month) + type(exp/streak/read) 多维度 |
| SC1-IDE-02 | 可重置编码沙箱（用户工作区+一键重置） | 实战演练场 | 隔离环境支持小型项目开发（✅ 已实现 2026-07-29） |
| SC1-AI-01 | AI 编程助手内联联动 | 实战演练场 | 运行报错自动触发 AI 解释与方案建议（✅ 已实现 2026-07-29） |
| SC1-AI-02 | 自动化代码评估 | 实战演练场 | 静态分析+动态测试+能力报告（✅ 已实现 2026-07-29） |
| SC1-AI-03 | 代码异常错题归集 | 实战演练场 | 编译/运行异常自动关联知识库（✅ 已实现 2026-07-29） |
| SC1-GAME-01 | 编程挑战闯关 | 社区激励 | ✅ 已实现(2026-07-30)：赛道 10 关 + 星级/积分/解锁/排行榜 |

### P3 — 低优先级（锦上添花）

| 编号 | 功能 | 模块 | 说明 |
|------|------|------|------|
| L-PATH-03 | DAG 可视化 | 学习门户 | ✅ 已实现(2026-07-31)：`GET /api/learning/paths/{pathId}/dag` 返回节点+边，前端 `DagGraph.vue`（dagre 分层布局 + 自绘 SVG）交互式渲染，支持拖拽 / 缩放 / 悬停高亮 / 全屏，按完成/可学/锁定三态着色 |
| L-PATH-04 | AI 推断依赖关系 | 学习门户 | ✅ 已实现(2026-07-31)：AI 输出 `prerequisiteSortOrders`，采用路径时两阶段落地为 `prerequisite_chapter_ids`，过滤自引用与后向依赖保证无环 |
| L-ADAPT-01 | 内容难度分级 | 学习门户 | 按掌握度展示对应难度 |
| L-ADAPT-02 | 低正确率推荐复习 | 学习门户 | 自动推荐闪卡/前置章节 |
| L-ADAPT-03 | 章节末尾补充练习 | 学习门户 | 动态生成练习题 |
| L-FORM-01 | 嵌入视频 | 学习门户 | ✅ 已实现(2026-07-31)：`learning_user_chapter.video_progress` 字段 + `POST /api/learning/chapters/{id}/video-progress` 进度上报，正文 `[video](url)` 约定语法渲染外链 iframe，达标自动提示完成章节 |
| L-FORM-02 | 互动讲义 | 学习门户 | ✅ 已实现(2026-07-31，2026-08-01 升级)：正文 ` ```js-run ` 代码块**对接后端多语言沙箱**（Python/Java/C++/JS 真实运行，JS 降级浏览器端）、` ```quiz-run ` 代码块即时判分测验，**题型扩展支持单选/多选/填空/拖拽排序四种** |
| A-RAG-04 | 知识图谱升级 | 学习助手 | ✅ 已实现(2026-07-30)：`kg_entity`/`kg_relation` 表 + AI 实体关系抽取（正文 3.2.2 已注明落地） |
| A-FB-01 | 相关概念推荐 | 学习助手 | ✅ 已实现(2026-07-31)：章节学习页右侧「相关概念」标签 +「相关文档」列表（按章节标题关键词检索） |
| A-FB-02 | 章节内嵌测验 | 学习助手 | ✅ 已实现(2026-07-31)：` ```quiz-run ` 约定语法在每节末尾内嵌即时测验，提交后即时判分+解析反馈 |
| G-COMP-01 | 竞赛管理 | 社区激励 | 竞赛创建+规则设定 |
| G-COMP-02 | 竞赛答题+排名 | 社区激励 | 在线答题+实时排名 |
| G-COMP-03 | 竞赛成绩公示 | 社区激励 | 结束后公示+奖励 |
| G-WORK-01 | 作品展示 | 社区激励 | 项目截图+描述 |
| G-CERT-01 | 数字证书 | 社区激励 | ✅ 已实现(2026-07-31)：`learning_certificate` 表 + 路径完成自动颁发（唯一验证码 KC-xxx）+ `GET /api/learning/certificates` 列表/详情 + `verify` 匿名核验 + 前端 `Certificate.vue` canvas 生成证书图可下载 |
| SC1-GRAPH-01 | 技术栈依赖图谱 | 智能学习门户 | 语言/框架/库依赖关系网（复用图谱外壳） |
| SC1-GRAPH-03 | 概念可视化图解 | 智能学习门户 | 变量/条件判断/数据模型等图解讲解 |

### P4 — 远期规划

| 编号 | 功能 | 模块 | 说明 |
|------|------|------|------|
| L-FORM-03 | 互动黑板 | 学习门户 | 图文混排+AI 批注 |
| A-CHAT-02 | 多智能体讨论 | 学习助手 | 多 AI 角色参与 |
| P-ML-01 | 模型中心 | 实战演练场 | AI 模型在线体验 |
| P-ML-02 | 数据标注中心 | 实战演练场 | 在线标注工具 |
| P-ML-03 | 模型训练 | 实战演练场 | 训练任务+日志 |
| G-CERT-02 | 证书验证 | 社区激励 | 验证码查询 |

---

### P3 扩展 — 社交扩展（2026-08-01 实现）

| 编号 | 功能 | 模块 | 说明 |
|------|------|------|------|
| P3-PET | 宠物后端持久化 | 社交激励 | ✅ 已实现：`user_pet` 表 + `UserPetController`（GET 自动创建默认宠物 / POST feed 喂食 / POST play 玩耍 / POST focus-gain 专注获经验 / PUT 更新名称头像），升级逻辑 exp≥maxExp→level+1+maxExp×1.5，前端 `LearningCenter.vue` 对接 API 替代 localStorage |
| P3-GROUP | 学习小组 | 社交激励 | ✅ 已实现(此前完成)：`study_group`/`study_group_member`/`study_group_message` 三表 + WebSocket 实时通信 + 邮箱邀请 + 角色 MEMBER/ADMIN |
| P3-SHARE | 进度分享卡片生成 | 社交激励 | ✅ 已实现：`ShareCard.vue` 组件（Canvas 375×600 绘制渐变背景+统计数字+二维码占位+激励文案），`toBlob` 下载为图片，挂载于学习中心「分享进度」按钮 |
| P3-PUSH | 学习提醒 Service Worker Push | 学习门户 | ✅ 已实现：`vite-plugin-pwa` PWA 配置（manifest+workbox+devOptions）+ `useStudyReminder` composable（Notification API 定时提醒 + 持久化 + 推送订阅预留）+ `LearningPrefs.vue` 提醒设置面板 |
| P3-FOCUS | 主题深度学习模式 | 学习门户 | ✅ 已实现：`FocusMode.vue` 全屏沉浸页面（深色渐变背景 + SVG 进度环 + 番茄钟整合 + 白噪音播放器 + 今日任务 + 激励文案），进入自动切深色主题、退出恢复，路由 `/learning/focus` |

### P4 扩展 — 持续迭代（2026-08-01 实现）

| 编号 | 功能 | 模块 | 说明 |
|------|------|------|------|
| P4-SANDBOX | 代码运行对接后端多语言沙箱 | 学习门户 | ✅ 已实现：`ChapterLearn.vue` 的 `bindInteractiveCode` 改为调用 `codeRunApi.run`（POST /code/run），支持 Python/Java/C++/JS 四语言真实运行，JS 降级浏览器端执行 |
| P4-QUIZ | 互动练习题型扩展 | 学习门户 | ✅ 已实现：`parseQuiz` 扩展支持四种题型——单选（`答案: A`）/ 多选（`类型: 多选` + `答案: ABC`）/ 填空（`Q:` 含 `___` + `答案: 文本`）/ 拖拽排序（`类型: 拖拽` + 选项原始顺序即答案），`bindInteractiveQuiz` 按类型渲染 checkbox/input/原生 draggable UI |
| P4-AI-SUGGEST | AI 智能学习建议 | 学习助手 | ✅ 已实现：`ai_learning_suggestion` 表 + `AiLearningSuggestionController`（GET 缓存查询 / POST regenerate），AI 生成 4 条 JSON 建议 [{title,desc,icon,path}] 并缓存，降级返回静态建议，前端 `Home.vue` 的 AI 卡片改为动态加载 |
| P4-NOISE | 白噪音背景音 | 学习门户 | ✅ 已实现：`WhiteNoisePlayer.vue` 组件（Web Audio API 程序化生成——rain 白噪音+lowpass / cafe 粉红噪音+bandpass / wave 白噪音+lowpass+LFO 调制），音量调节 + 最小化悬浮，挂载于番茄钟页和深度学习模式 |
| P4-WEEKLY | 学习报告周报自动生成 | 学习成果度量 | ✅ 已实现：`weekly_report` 表 + `WeeklyReportController`（GET 本周自动生成 / GET list 最近 8 周 / POST generate 手动生成），AI 生成 summary+achievements+suggestions 注入统计数据，降级模板拼接，前端 `WeeklyReport.vue` 页面展示 |

---

## 5. 技术方案建议

### 5.1 RAG 向量检索（P1）

```
推荐方案：Spring AI + pgvector
- 文档入库时：调用 embedding API（如 text-embedding-3-small）生成 1536 维向量，存入 pgvector
- 用户提问时：问题向量化 → pgvector 余弦相似度 Top-K（K=5）→ 拼接 context → 注入 prompt
- 优势：与现有 Spring Boot 无缝集成，pgvector 是 PostgreSQL 扩展，无需独立服务
- 备选：H2 内存向量（零依赖，适合 <10K 文档的开发环境）
```

### 5.2 在线答题判分（P0）

```
- 客观题（选择/判断/填空）：前端提交答案 → 后端比对标准答案 → 返回对错 + 解析
- 主观题（简答）：前端提交答案 → 后端调用 AI 模型对比标准答案 → 返回分数 + 批改建议
- 答题记录：quiz_answer_record 表持久化，自动同步错题到 learning_mistake
```

### 5.3 代码执行沙箱（P2）

```
推荐方案：Judge0（开源判题系统）
- Docker 部署 Judge0 服务
- Spring Boot 通过 REST API 提交代码 + 测试用例
- Judge0 返回执行结果（stdout/stderr/exit_code/time_usage）
- 前端展示运行结果 + AC/WA/TLE/RE 状态
- 备选：GraalVM Polyglot（Java 内嵌，无 Docker，支持 JS/Python）
```

### 5.4 游戏化后端（P0）

```
- 签到：user_check_in 表，每日唯一约束（user_id + check_date）
- 连续天数：查询昨日是否签到，是则+1，否则重置为1
- 成就判定：事件驱动（阅读完成/路径完成/签到时）触发成就检查，满足条件自动解锁
- 排行榜：Redis ZSET 缓存周/月 exp 排名，定时刷新
```

---

## 6. 场景化需求专题：场景一·编程学习（理论→实践闭环）

> 本专题以「即学即练 + 实战经验」为主线，对照用户提出的四大子场景逐点核查**当前代码库真实状态**，标注已实现（✅）/ 部分实现（🟡）/ 未实现（❌），并给出差距与需求定义。整体结论：该场景目前仍以「数据层与简单页面」为主，最关键的「浏览器真能跑多语言代码 + AI 实时辅助 + 自动评估 + 闯关游戏化」实战闭环**几乎全缺**。

### 6.1 智能知识图谱与路径规划

| 子功能 | 状态 | 对照说明（基于代码实测） |
|--------|------|--------------------------|
| 1.1 技术栈知识图谱（Spring Boot→Java→Maven 依赖网） | ❌ 未实现 | 现有 `KnowledgeGraph.vue` + `/api/knowledge/graph` 是**文档分类图谱**（doc_category / doc_document 的父子与归属），**非技术依赖关系网**。可视化 SVG 外壳可复用。 |
| 1.2 个性化学习路径（按水平+目标定制 AI 动态生成） | 🟡 部分 | 后端 `learning_path` / `learning_chapter` 表 + admin 建路径章节已有；但「用户选新手/初级 + 目标 → AI 实时生成个性化路线」**未真接通**——`LearningPathMgmt.vue:671` 引用未导出的 `AiGeneratePathPayload`（编译错误），AI 生成功能卡死；现有路径为管理员预置，非用户级动态。 |
| 1.3 概念可视化学习（变量/条件判断/数据模型图解） | ❌ 未实现 | 全量 grep 确认**无专门图解页面/组件**，仅有文档 Markdown 渲染与「核心概念」文本提示。 |

**需求定义**

| 需求 ID | 需求描述 | 优先级 | 关联既有 ID |
|---------|---------|--------|------------|
| SC1-GRAPH-01 | 技术栈依赖知识图谱：建模语言/框架/数据库/算法间依赖（如 Spring Boot→Java→Maven），复用现有图谱 SVG 外壳扩展节点类型 | P3 | （新增，区别于 A-RAG-04 文档图谱） |
| SC1-GRAPH-02 | 个性化 AI 路径按「用户水平 + 目标」动态生成（修 `AiGeneratePathPayload` 编译错误并接通） | P1 | 细化 L-PATH-04 |
| SC1-GRAPH-03 | 概念可视化图解：用图解方式讲解变量/条件判断/数据模型等基础概念 | P3 | （新增） |

### 6.2 沉浸式代码实践环境

| 子功能 | 状态 | 对照说明（基于代码实测） |
|--------|------|--------------------------|
| 2.1 在线 IDE（浏览器编写/运行/调试 Py/Java/C++/JS） | ✅ 已实现 | 代码编辑器（编写）+ **真实多语言运行沙箱已落地**（后端进程直执行 Python/Java/JavaScript/C++，见 SC1-IDE-01，2026-07-29 实现）；**在线调试器已落地**（2026-07-29）：Python 基于 AST 插桩实现逐行执行追踪（行号+局部变量快照），Java/JS/C++ 复用真实运行引擎并解析错误行号兜底；前端 `CodePlayground` 新增「调试」Tab 展示逐行轨迹与出错行。 |
| 2.2 实时编码沙箱（隔离、可重置实验环境） | ✅ 已实现 | **可重置实验沙箱已落地**（SC1-IDE-02，2026-07-29）：后端按用户隔离的持久化工作区，支持多文件小项目（如 `main.py` import `helper.py`）、文件新建/编辑/删除、`一键重置沙箱`；运行复用进程直执行引擎。当前为进程直执行地基（非 Docker 硬隔离），生产可升级。 |
| 2.3 即时运行与反馈 | 🟡 部分 | 仅 JS/TS 浏览器即时运行有反馈、SQL 模拟反馈；多语言无。 |

**需求定义**

| 需求 ID | 需求描述 | 优先级 | 关联既有 ID |
|---------|---------|--------|------------|
| SC1-IDE-01 | 真实多语言在线运行沙箱：后端进程直执行（ProcessBuilder）Python/Java/JavaScript/C++，支持 stdin / 超时强杀 / 编译错误识别（Judge0 为生产升级路径） | ✅ 已实现(2026-07-29) | 同 P-CODE-01 |
| SC1-IDE-02 | 可重置编码沙箱：隔离环境 + 一键重置，支持小型项目开发 | ✅ 已实现(2026-07-29) | 同 P-CODE-03 |
| SC1-IDE-03 | 多语言即时运行反馈（扩展 2.3） | P2 | 同 P-CODE-02 |

### 6.3 智能辅助与评估

| 子功能 | 状态 | 对照说明（基于代码实测） |
|--------|------|--------------------------|
| 3.1 AI 编程助手（编码实时提示/错误解释/方案建议） | ✅ 已实现 | **AI 编程助手内联联动已落地**（SC1-AI-01，2026-07-29）：`CodePlayground` 内嵌 AI 助手面板，运行时错误可一键「AI 解释」（携带代码+错误上下文），支持自由提问；后端 `/api/code/ai-assist` 调大模型，未配置 Key 时优雅降级提示。 |
| 3.2 自动化代码评估（静态分析+动态测试+能力报告） | ✅ 已实现 | **自动化代码评估已落地**（SC1-AI-02，2026-07-29）：`/api/code/assess` 支持动态测试用例逐例真实运行评判 + 启发式静态检查（空指针/未定义变量/裸 except 等）+ 大模型能力报告（优势/不足/改进建议），综合评分与「熟练/进阶/入门」评级；未配置 AI Key 时优雅降级。前端 `CodePlayground` 新增「能力评估」Tab 展示得分、用例通过率、静态问题清单与 AI 报告。 |
| 3.3 智能错题归集（编译/运行异常→关联知识库） | ✅ 已实现 | **代码异常智能错题归集已落地**（SC1-AI-03，2026-07-29）：运行报错可一键「归集错题」→ `/api/mistakes/collect-code` 自动抽取错误类型（如 `ZeroDivisionError`/`NullPointerException`）+ 摘要，幂等入库 `learning_mistake`，并按标题模糊匹配关联知识库文档；前端在控制台结果区展示错误类型徽章、摘要与关联知识库文档列表。 |

**需求定义**

| 需求 ID | 需求描述 | 优先级 | 关联既有 ID |
|---------|---------|--------|------------|
| SC1-AI-01 | AI 编程助手内联联动：编码时实时提示 + 运行报错自动触发 AI 解释与方案建议 | ✅ 已实现(2026-07-29) | （新增，扩展 Chat） |
| SC1-AI-02 | 自动化代码评估：结合静态分析 + 动态测试用例评判，生成能力报告 | ✅ 已实现(2026-07-29) | 扩展 P-CODE-02 |
| SC1-AI-03 | 代码异常智能错题归集：自动收集编译/运行异常并关联知识库解决方案 | ✅ 已实现(2026-07-29) | （新增，复用 `learning_mistake`） |

### 6.4 碎片化与游戏化学习

| 子功能 | 状态 | 对照说明（基于代码实测） |
|--------|------|--------------------------|
| 4.1 编程挑战与闯关（十题挑战等） | ✅ 已实现 | `ChallengePlay.vue` + `CodeChallengeServiceImpl`：赛道 10 关逐关解锁，浏览器判题，星级/积分/排行榜完整。 |

**需求定义**

| 需求 ID | 需求描述 | 优先级 | 关联既有 ID |
|---------|---------|--------|------------|
| SC1-GAME-01 | 编程挑战闯关：十题挑战 / 关卡 / 积分，碎片时间快速检验掌握情况 | P2 | （新增，区别于 G-COMP 竞赛） |

### 6.5 场景一·差距汇总（未实现/待修清单）

| 优先级 | 缺口 | 说明 |
|--------|------|------|
| ✅ 场景 P0(已落地) | 2.1/2.2 真实多语言在线运行沙箱 | 后端进程直执行沙箱 2026-07-29 已实现，支持 Py/Java/JS/C++ 真实运行；2.2 可重置实验沙箱（SC1-IDE-02）同批落地，支持多文件工作区 + 一键重置；生产可升级 Judge0/Docker 获网络隔离与硬资源限制。 |
| ✅ 场景 已实现 | 3.1 AI 编程助手内联联动 | SC1-AI-01 2026-07-29 已实现：`CodePlayground` 内嵌 AI 助手，运行报错一键解释 + 自由提问，携带代码上下文调大模型，未配 Key 优雅降级。 |
| 🟠 P1 | 1.2 个性化 AI 路径（水平+目标） | 修 `AiGeneratePathPayload` 编译错误 + 接通用户级动态生成。 |
| ✅ 场景 已实现 | 3.2 自动化代码评估 + 3.3 代码异常错题归集 | SC1-AI-02/SC1-AI-03 2026-07-29 已实现：练习具备「动态评测→静态检查→能力报告→错题归集→关联知识库」完整闭环。 |
| ✅ 已实现(2026-07-30) | 4.1 编程挑战闯关游戏化 | 赛道 10 关 + 星级/积分/解锁/排行榜前后端完整实现。 |
| 🟡 P3 | 1.1 技术栈依赖图谱 + 1.3 概念可视化图解 | 增强项，复用现有图谱 SVG 外壳。 |

### 6.6 可复用基础（无需从零造）

- ✅ `KnowledgeGraph.vue` 图谱 SVG 可视化外壳 → 1.1 技术依赖图谱可扩展节点类型
- ✅ `learning_path` / `learning_chapter` 表 + AI 出题 / `quiz_question` / `code_question` → 1.2 / 4.1 数据底座已有
- ✅ `CodePlayground` 前端代码编辑 + JS/TS 执行框架 → 2.1/2.2 接后端沙箱时直接扩展
- ✅ `learning_mistake` 错题本模型 → 3.3 代码异常归集可复用
- ✅ `Chat.vue` 通用 AI 对话 → 3.1 编程助手底座

---

## 7. 附录：已实现功能索引

### 后端关键文件

| 功能 | 文件路径 |
|------|---------|
| AI 服务 | `backend/src/main/java/com/knowflow/service/impl/AiServiceImpl.java` |
| 文档服务 | `backend/src/main/java/com/knowflow/service/impl/DocServiceImpl.java` |
| 文档解析 | `backend/src/main/java/com/knowflow/service/DocumentTextExtractor.java` |
| 学习服务 | `backend/src/main/java/com/knowflow/service/impl/LearningServiceImpl.java` |
| 聊天服务 | `backend/src/main/java/com/knowflow/service/impl/ChatServiceImpl.java` |
| 错题服务 | `backend/src/main/java/com/knowflow/service/impl/MistakeServiceImpl.java` |
| 题库管理 | `backend/src/main/java/com/knowflow/controller/admin/AdminQuizQuestionController.java` |
| AI 生成路径 | `backend/src/main/java/com/knowflow/controller/admin/AdminLearningController.java` |
| 代码题 | `backend/src/main/java/com/knowflow/controller/CodeQuestionController.java` |
| 知识图谱 | `backend/src/main/java/com/knowflow/service/KnowledgeService.java` |

### 前端关键页面

| 功能 | 文件路径 |
|------|---------|
| 学习中心 | `frontend/src/views/LearningReport.vue` |
| AI 答疑 | `frontend/src/views/Chat.vue` |
| 智能出题 | `frontend/src/views/SmartQuiz.vue` |
| 代码练习 | `frontend/src/views/CodePlayground.vue` |
| 知识图谱 | `frontend/src/views/KnowledgeGraph.vue` |
| 错题本 | `frontend/src/views/Mistakes.vue` |
| 成就 | `frontend/src/views/Achievement.vue`（已接入真实 API） |
| 打卡 | `frontend/src/views/CheckIn.vue`（已接入真实 API） |
| 文档详情 | `frontend/src/views/DocDetail.vue` |
| 我的闪卡 | `frontend/src/views/MyFlashcards.vue` |

### 数据库表索引

| 表名 | 用途 |
|------|------|
| `sys_user` | 用户（含 level/exp/energy/streak_days） |
| `sys_user_ai_config` | 用户级 AI API Key 配置 |
| `doc_document` | 文档（含 file_name/file_url/file_size/content） |
| `doc_category` | 分类/知识库（含 owner_id） |
| `kb_member` | 知识库成员（OWNER/EDITOR/READER） |
| `doc_favorite` / `doc_read_progress` | 收藏 / 阅读进度 |
| `learning_path` / `learning_chapter` | 学习路径 / 章节 |
| `learning_user_path` / `learning_user_chapter` | 用户路径进度 / 章节完成 |
| `learning_flashcard` | 闪卡（含 user_id/category_id/doc_id/source_type） |
| `learning_mistake` | 错题本 |
| `learning_task` | 学习任务 |
| `quiz_question` | 题库（5 种题型） |
| `code_question` | 代码题库 |
| `chat_conversation` / `chat_message` | AI 对话 |
| `study_group` / `study_group_member` / `study_group_message` | 学习小组 |
| `community_post` / `community_comment` | 社区帖子/评论 |

---

> **文档维护说明**：本文档随功能迭代持续更新，每完成一个需求项后将状态标记为 ✅ 并附实现日期。
