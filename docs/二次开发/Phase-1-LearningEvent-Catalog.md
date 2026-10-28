# Phase 1 Learning Event Catalog —— 学习事件目录

> 日期：2026-08-30 · 基于真实源码扫描（Phase 1 编码前设计定稿）
> 原则：复用现有事件、不创建重复事件、一次真实学习行为 = 一条核心事件、事件由后端业务事实产生。

---

## 一、事件模型（现状，未改表结构）

表：`learning_event`（Entity `LearningEvent`，Phase 1 已建）

| 字段 | 说明 |
|---|---|
| `user_id` | 谁（逻辑外键 sys_user.id） |
| `event_type` | 做了什么（LearningEventType 枚举） |
| `resource_type` | 作用于什么资源（DOC/CHAPTER/QUIZ/CODE/FLASHCARD/REVIEW_CARD/RECALL_SESSION/MISTAKE/TASK/CHAT/KNOWLEDGE/PATH/CHECKIN） |
| `resource_id` | 资源 ID（可空，如 CHECK_IN） |
| `metadata` | 扩展上下文（JSON 字符串：结果 correct/passed、score、耗时、interval 等） |
| `create_time` | 什么时候发生（BaseEntity 统一） |

> 说明：事件模型**无独立 knowledge_id 列**。事件→知识点通过 `resource_knowledge_mapping`（resource_type + resource_id → knowledge_id）在掌握度引擎解析（`KnowledgeMasteryServiceImpl.processEvent` → `ResourceKnowledgeService.resolveKnowledgeIds`）。本 Phase 遵循「Event → Mastery 不重构」原则，保持该链路不动。
> 事件写入统一入口：`LearningEventService.record(...)`（REQUIRES_NEW 独立事务，失败仅告警不阻断；落库后触发掌握度重算，重算失败仅告警）。

---

## 二、事件类型清单（LearningEventType）

| # | event_type | 语义 | 状态 |
|---|---|---|---|
| 1 | `DOCUMENT_READ` | 文档阅读（DocReadProgress 写入） | 既有，复用 |
| 2 | `CHAPTER_START` | 章节开始学习（首次视频进度上报） | 既有，复用 |
| 3 | `CHAPTER_COMPLETE` | 章节完成（completeChapter） | 既有，复用 |
| 4 | `QUESTION_ANSWERED` | **答题唯一核心事件**（行为+结果合一，metadata.correct/score） | 既有，Phase 1 收敛 |
| 5 | `QUESTION_CORRECT` | 历史结果事件 | **Phase 1 起不再单独落库**（保留枚举兼容旧数据） |
| 6 | `QUESTION_WRONG` | 历史结果事件 | **Phase 1 起不再单独落库**（保留枚举兼容旧数据） |
| 7 | `MISTAKE` | 错题归集（题目/代码错题写入） | 既有，复用 |
| 8 | `MISTAKE_REVIEW` | 错题复习完成（markMastered） | **Phase 1 新增** |
| 9 | `CODE_SUBMITTED` | **代码提交唯一核心事件**（行为+结果合一，metadata.passed/total/passCount） | 既有，Phase 1 收敛 |
| 10 | `CODE_PASSED` | 历史结果事件 | **Phase 1 起不再单独落库**（保留枚举兼容旧数据） |
| 11 | `CODE_FAILED` | 历史结果事件 | **Phase 1 起不再单独落库**（保留枚举兼容旧数据） |
| 12 | `FLASHCARD_REVIEWED` | 闪卡/复习卡复习（SM-2，metadata.quality/interval/lapsed） | 既有，复用（统一 SRS 输出） |
| 13 | `RECALL_COMPLETED` | 主动回忆会话完成 | 既有，复用 |
| 14 | `LEARNING_TASK_COMPLETE` | 学习任务完成（status=1） | **Phase 1 新增** |
| 15 | `AI_CHAT` | AI 对话 | 既有，复用 |
| 16 | `KNOWLEDGE_VIEWED` | 知识图谱查看 | 既有，复用 |
| 17 | `PATH_COMPLETED` | 学习路径全部章节完成 | 既有，复用 |
| 18 | `CHECK_IN` | 每日签到 | 既有，复用 |

> 学习路径进度：复用 `CHAPTER_COMPLETE`（metadata.pathId）+ `PATH_COMPLETED`，**不新增** LEARNING_PATH_PROGRESS（避免每次页面访问刷事件，符合「不要大量记录每次页面访问」）。

---

## 三、行为 → 事件 → 结果 映射（Phase 1 定稿）

| 用户行为 | 核心事件 | 结果表达 | 来源（业务事实） |
|---|---|---|---|
| 答题（判题完成） | `QUESTION_ANSWERED` | metadata: `correct`(bool) / `score`(0/100) / `timeCost` | `QuizServiceImpl.submit`（答错同时走错题归集，不另计事件） |
| 复习闪卡（SM-2 评分） | `FLASHCARD_REVIEWED` | metadata: `quality` / `interval` / `lapsed` | `LearningServiceImpl.reviewFlashcard`、`WorkbenchServiceImpl.gradeReview`（统一 SrsService） |
| 错题归集 | `MISTAKE` | metadata: `source` / `updated` | `MistakeServiceImpl.addMistake` / `collectCodeMistake` |
| 错题复习完成（标记掌握） | `MISTAKE_REVIEW` | metadata: `mastered` / `questionId` / `codeQuestionId` | `MistakeServiceImpl.markMastered` |
| 代码提交（测试完成） | `CODE_SUBMITTED` | metadata: `passed` / `total` / `passCount` / `language` | 代码提交业务（CodeSubmitRecord 写入处） |
| 章节完成 | `CHAPTER_COMPLETE` | metadata: `pathId` | `LearningServiceImpl.completeChapter` |
| 章节开始（首次有效学习） | `CHAPTER_START` | — | `LearningServiceImpl`（视频进度首次插入） |
| 学习任务完成（status→1） | `LEARNING_TASK_COMPLETE` | metadata: `title` / `type` | `LearningServiceImpl.updateTaskStatus` |
| 路径完成（全部章节） | `PATH_COMPLETED` | metadata: `completedChapters` / `totalChapters` | `LearningServiceImpl.completeChapter`（末章触发） |
| 文档阅读 | `DOCUMENT_READ` | — | `DocServiceImpl` |
| 主动回忆完成 | `RECALL_COMPLETED` | metadata: 轮次/得分 | `WorkbenchServiceImpl` |
| AI 对话 | `AI_CHAT` | — | `ChatServiceImpl` |
| 签到 | `CHECK_IN` | metadata: 连续天数 | `CheckInServiceImpl` |
| 图谱查看 | `KNOWLEDGE_VIEWED` | — | `KnowledgeController` |

---

## 四、设计约束（Phase 1 落实）

1. **行为/结果合一**：事件类型只描述行为（QUESTION_ANSWERED / CODE_SUBMITTED），结果进 metadata（correct / passed）——不再为每个结果建独立类型。
2. **一次行为一条核心事件**：答题 1 条、代码提交 1 条、闪卡复习 1 条、章节完成 1 条（测试锁定）。
3. **统一入口**：所有写入走 `LearningEventService.record()`；不直接操作 LearningEventMapper；Controller 不直接写 Mapper（代码提交事件落点虽在 Controller 层业务事实处，但经 Service 写入）。
4. **后端业务事实驱动**：核心事件一律由后端业务完成点产生，前端不埋核心事件。
5. **Event→Mastery**：保持 `record()` → `processEvent()` → `recalc()`（读业务表，幂等）链路，不重构。
6. **幂等**：掌握度重算幂等（读业务表）；事件本身为追加日志，一次业务操作只落一条（已由测试锁定）。
