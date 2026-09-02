# Phase 1 执行报告 —— Learning Event 学习事件闭环

> 日期：2026-08-30 · 基线：Phase 0 完成（工作树含 Phase 0 未提交改动，本轮仅改 Phase 1 相关文件）
> 原则：基于现有 LearningEvent 体系增强、不重新设计、一次行为一条核心事件、后端事实驱动、不重构掌握度算法。

---

## 1. Phase 1 目标

让所有「真正影响学习状态」的行为都进入统一 `learning_event`：
- 事件类型只描述**行为**，**结果**进 metadata（不再为每个结果建类型）；
- 一次真实学习行为 = **一条核心事件**（消灭 ANSWERED+CORRECT/WRONG 双事件）；
- 所有写入统一走 `LearningEventService.record()`，不直接碰 Mapper；
- 补齐缺口：错题复习、学习任务完成；
- 新增统一统计服务（未来 AI 诊断/看板数据源）。

## 2. 修改前 LearningEvent 架构

- 表 `learning_event`（user_id / event_type / resource_type / resource_id / metadata JSON）✅ 已存在。
- 统一入口 `LearningEventService.record()`（REQUIRES_NEW、失败仅告警、落库后触发掌握度重算）✅ 已存在。
- 已有事件 15 类：DOCUMENT_READ / CHAPTER_START / CHAPTER_COMPLETE / QUESTION_ANSWERED / QUESTION_CORRECT / QUESTION_WRONG / MISTAKE / CODE_SUBMITTED / CODE_PASSED / CODE_FAILED / FLASHCARD_REVIEWED / RECALL_COMPLETED / AI_CHAT / KNOWLEDGE_VIEWED / PATH_COMPLETED / CHECK_IN。
- **问题**：
  1. 答题产生 QUESTION_ANSWERED + QUESTION_CORRECT/WRONG **两条**事件（一次行为 2 条，违反「一次一条」）；
  2. 代码提交产生 CODE_SUBMITTED + CODE_PASSED/FAILED **两条**事件；
  3. 代码提交事件写在 **Controller**（CodeQuestionController 直接 record）；
  4. 错题复习（markMastered）、学习任务完成 **无事件**；
  5. 无统一统计查询能力。

## 3. 修改后 LearningEvent 架构

```
用户行为
   ↓
业务 Service（判题/复习/任务等业务事实完成点）
   ↓
LearningEventService.record()（统一入口，REQUIRES_NEW）
   ↓
learning_event（行为事件，结果进 metadata JSON）
   ↓
KnowledgeMasteryService.processEvent()（沿用现有逻辑：经 resource_knowledge_mapping 解析知识点 → recalc 读业务表，幂等）
   ↓
未来 AI Diagnosis / AI Coach（数据底座就绪）
```

- 事件类型收敛为「行为型」，结果全部进 metadata（correct / passed / quality / interval 等）。
- 历史结果型事件（QUESTION_CORRECT/WRONG、CODE_PASSED/FAILED）**枚举保留**（旧数据可读、兼容），但**不再新落库**。
- 新增事件类型：`MISTAKE_REVIEW`（错题复习完成）、`LEARNING_TASK_COMPLETE`（学习任务完成）。
- 新增 `LearningEventQueryService`（统一统计口径）+ `GET /api/learning/events/summary`。

## 4. 新增/复用事件类型

| 事件 | 状态 | 说明 |
|---|---|---|
| MISTAKE_REVIEW | 🆕 新增 | 错题标记已掌握（一次真实复习完成信号） |
| LEARNING_TASK_COMPLETE | 🆕 新增 | 学习任务 status→1（仅真实完成记） |
| QUESTION_ANSWERED | ♻️ 复用+收敛 | 答题唯一核心事件，结果进 metadata |
| CODE_SUBMITTED | ♻️ 复用+收敛 | 代码提交唯一核心事件，结果进 metadata |
| DOCUMENT_READ / CHAPTER_START / CHAPTER_COMPLETE / FLASHCARD_REVIEWED / RECALL_COMPLETED / MISTAKE / AI_CHAT / KNOWLEDGE_VIEWED / PATH_COMPLETED / CHECK_IN | ♻️ 复用 | 均已在正确位置记录 |
| QUESTION_CORRECT / QUESTION_WRONG / CODE_PASSED / CODE_FAILED | 🗄️ 保留枚举 | 历史结果事件，不再新落库 |

## 5. Quiz 接入

- 位置：`QuizServiceImpl.submit`（判题完成处）。
- 修改：删除 QUESTION_CORRECT / QUESTION_WRONG 两条独立记录，收敛为**单条** `QUESTION_ANSWERED`，metadata `{correct, score, timeCost}`。
- 结果判定：`correct` 布尔直接进 metadata；答错自动归集错题（MISTAKE 事件）不受影响（不同业务语义，各自单条）。
- 多知识点：题目→知识点经 `resource_knowledge_mapping` 由掌握度引擎统一解析（每资源一条核心事件，不改事件表结构）。

## 6. Flashcard 接入

- 位置：`LearningServiceImpl.reviewFlashcard`（FLASHCARD）、`WorkbenchServiceImpl.gradeReview`（REVIEW_CARD）——两者均已走 Phase 0 统一 `SrsService`。
- 修改：无结构改动（Phase 0 已收敛）；本 Phase 验证「一次复习 = 1 条 FLASHCARD_REVIEWED + SRS 正常更新」（测试锁定）。
- 顺手修复：SrsService 增加 interval 下限保护 `Math.max(1, interval × EF)`，修复种子/历史脏数据（review_interval=0 且 repetitions≥3）导致间隔算成 0 天的回归（Phase 0 收敛时丢失的下限）。

## 7. Mistake 接入

- 归集：`MISTAKE`（addMistake / collectCodeMistake）既有，复用。
- **新增** `MISTAKE_REVIEW`：`MistakeServiceImpl.markMastered` 标记已掌握时记 1 条，metadata `{mastered, questionId, codeQuestionId}`。
- 不重复：错题「再次作答」仍走 Quiz 事件；「标记掌握」是独立业务语义，仅此一条。

## 8. Coding 接入

- 位置：代码提交业务事实完成点（CodeSubmitRecord 写入处）。
- 修改：删除 CODE_PASSED / CODE_FAILED 两条独立记录，收敛为**单条** `CODE_SUBMITTED`，metadata `{passed, total, passCount, language}`。
- 说明：代码提交业务本身位于 Controller（现有薄架构，未做 Service 抽取以免大重构）；事件写入经 `LearningEventService.record()`（Service 入口），**未直接触碰 LearningEventMapper**，符合「Controller → 业务 → 事件 Service」约束。已在本报告「修改范围外说明」标注。

## 9. Chapter 接入

- `CHAPTER_START`（首次视频进度上报）、`CHAPTER_COMPLETE`（completeChapter）既有，复用；末章完成联动 `PATH_COMPLETED`（G-CERT-01）。
- 本 Phase 无改动（测试锁定 CHAPTER_COMPLETE 单条）。

## 10. Task 接入

- **新增** `LEARNING_TASK_COMPLETE`：`LearningServiceImpl.updateTaskStatus` 当 status==1（已完成）时记 1 条，metadata `{title, type}`；切换回待办不记（避免刷事件）。

## 11. Learning Path 接入

- 复用 `PATH_COMPLETED` + `CHAPTER_COMPLETE`（metadata.pathId）。不新增 LEARNING_PATH_PROGRESS（避免每次页面访问刷事件）。

## 12. Document 接入

- `DOCUMENT_READ`（DocServiceImpl）既有，复用。
- **不直接提升掌握度**：本 Phase 不重构 Event→Mastery（按协议 §20 保留现有逻辑）。文档阅读作为 DOC 信号参与掌握度是既有设计，留待后续掌握度权重阶段评估。

## 13. 幂等处理

- 掌握度重算幂等（`recalc` 读业务表，事件仅为触发器）。
- 事件层：一次业务操作 = 一条核心事件，已由 `LearningEventClosedLoopTest` 锁定（多题提交每题各 1 条、无结果型事件产生）。
- 未引入事件级 idempotencyKey 列（避免 Phase 1 大规模重构；如未来支付/重试场景需要再加）。

## 14. 事务处理

- 保持现有架构：`record()` 为 REQUIRES_NEW 独立事务（业务回滚事件仍落库；事件失败仅告警）——与「核心学习事实和事件尽量同事务」的项目现状一致（事件是触发器，业务表才是事实源，双写失败不影响业务）。

## 15. 数据库修改

- **零表结构变更**：未加列、未加表。`learning_event` 现有字段（metadata JSON、长度 TEXT）已足够承载新事件与结果。
- H2/MySQL schema 均未改动（Phase 1 无 DDL）。

## 16. 前端修改

- `views/LearningEvents.vue`：事件类型过滤项同步新设计——移除 QUESTION_CORRECT / QUESTION_WRONG / CODE_PASSED / CODE_FAILED（不再新产生），新增 MISTAKE_REVIEW / LEARNING_TASK_COMPLETE；历史数据仍可在全量列表查看（labelMap 兜底）。
- `api/types.ts`：+`LearningEventSummaryVO`。
- `api/learning.ts`：+`learningEventSummary()`（GET /learning/events/summary）。
- 无 UI 重构；不改动其他页面。

## 17. 测试结果

| 项 | 结果 |
|---|---|
| 后端全量 `mvn test` | ✅ **56/56 通过**（0 失败 0 错误） |
| 新增 `LearningEventClosedLoopTest`（7 例，真实 H2） | ✅ 全过：Test1 答题正确=1 条 QUESTION_ANSWERED(correct=true)；Test2 答题错误=1 条(correct=false)；Test3 闪卡复习=1 条 FLASHCARD_REVIEWED 且 SRS 更新；Test4 代码提交=1 条 CODE_SUBMITTED(passed=true)；Test5 章节完成=1 条 CHAPTER_COMPLETE；Test6 错题复习=1 条 MISTAKE_REVIEW 且无结果型事件；Test6b 两题提交每题各 1 条 |
| 新增 `SrsServiceImplTest.zeroIntervalGuard` | ✅ interval=0 脏数据下限保护 |
| 既有测试回归 | ✅ MasteryEngineIntegrationTest 19、LearningPlan 系列、CodeGen 等全部通过 |
| 前端 `vue-tsc -b` | ✅ Phase 1 触碰文件（LearningEvents.vue/learning.ts/types.ts）零错误；总数 166 与基线持平（既有历史错误未新增） |
| 前端 `vite build` | ✅ 通过（2932 模块） |
| Spring Boot 启动（H2） | ✅ 启动成功 |
| 运行时端到端 | ✅ 任务完成→LEARNING_TASK_COMPLETE 落库；闪卡复习→FLASHCARD_REVIEWED(interval=1) 落库；`GET /api/learning/events/summary` 正确返回（todayCount/7d 统计/recentTypes） |

## 18. 发现的问题

1. **SRS 边界回归（已修）**：Phase 0 收敛 SrsService 时丢失 `Math.max(1,...)` 下限，种子/历史闪卡（review_interval=0 且 repetitions≥3）会算出 0 天间隔；已加下限保护 + 单测锁定。
2. **事件结果型双写（已修）**：答题/代码提交此前各产生 2 条事件（行为+结果），已收敛为单条核心事件。

## 19. 尚未解决的问题

1. 前端 166 处历史 TS 类型错误（非本阶段引入，需独立专项）。
2. `DocDetail.vue` 调用不存在的 `learningApi.chaptersByDoc`（后端无此接口）。
3. 代码提交事件落点在 Controller（现有薄架构），未抽取 Service——如需彻底分层，留待后续「Coding Service 抽取」。
4. 文档阅读（DOCUMENT_READ）参与掌握度（DOC 信号）的权重策略未评估——按协议本 Phase 不重构 Event→Mastery，留待掌握度阶段。
5. 代码挑战（code_challenge）赛道完成未接入事件（本 Phase 聚焦 code_question 提交，挑战赛道留观察）。

## 20. 下一阶段建议

- **Phase 1.5 / 前端专项**：清理 166 处历史 TS 错误（含 CategoryVO 重复声明），恢复 `npm run build` 全绿。
- **Phase 2 方向（掌握度增强）**：写作（费曼/康奈尔）入环（WRITING 事件 + 知识点映射）、映射 LLM 补链、薄弱点→复习闭环——事件底座已就绪，可直接消费。
- **Phase 6 前**：统一 KnowledgeRetrievalService 接口契约，事件数据可反哺 AI 上下文。

---

## 附：学习事件覆盖表（基于真实源码）

| 学习行为 | 是否接入 | Event Type | 来源 Service/位置 | Knowledge | Mastery |
|---|---|---|---|---|---|
| 文档阅读 | ✅ | DOCUMENT_READ | DocServiceImpl | ✅（经 DOC 资源映射） | 参与（DOC 信号，权重策略留待后续） |
| 章节开始 | ✅ | CHAPTER_START | LearningServiceImpl | ✅（章节→文档→映射） | 参与 |
| 章节完成 | ✅ | CHAPTER_COMPLETE | LearningServiceImpl.completeChapter | ✅ | 参与 |
| 答题 | ✅ | QUESTION_ANSWERED（结果进 metadata） | QuizServiceImpl.submit | ✅ | 参与 |
| 闪卡复习 | ✅ | FLASHCARD_REVIEWED | LearningServiceImpl.reviewFlashcard / WorkbenchServiceImpl.gradeReview | ✅ | 参与 |
| 错题复习 | ✅ | MISTAKE_REVIEW | MistakeServiceImpl.markMastered | ✅（经 MISTAKE 资源映射） | 参与 |
| 代码提交 | ✅ | CODE_SUBMITTED（结果进 metadata） | 代码提交业务完成点（经 LearningEventService） | ✅ | 参与 |
| 任务完成 | ✅ | LEARNING_TASK_COMPLETE | LearningServiceImpl.updateTaskStatus | ❌（学习任务暂无知识点映射） | 不直接参与 |
| 学习路径 | ✅ | PATH_COMPLETED + CHAPTER_COMPLETE(meta.pathId) | LearningServiceImpl.completeChapter（末章） | ✅ | 参与 |

---

## 最终检查清单（12 问）

1. 是否所有核心学习行为都有 LearningEvent？ → **是**（覆盖表 9 类核心行为全部接入；代码挑战/写作留待后续 Phase）
2. 是否存在重复事件？ → **否**（一次行为一条核心事件，测试锁定；历史结果型事件已停发）
3. 是否存在前端直接写 LearningEvent？ → **否**（核心事件全部由后端业务事实产生）
4. 是否存在 Controller 直接写 Mapper？ → **否**（全部经 LearningEventService；代码提交事件在 Controller 业务事实处但经 Service 写入）
5. 是否一次行为触发多次 record？ → **否**（答题/代码/闪卡/章节/错题复习/任务完成均已收敛为单条；多题提交每题各 1 条）
6. 是否所有事件都能关联 Knowledge？ → 核心学习事件均可（经 resource_knowledge_mapping）；LEARNING_TASK_COMPLETE 暂无知识点映射（任务未挂知识点，合理）
7. 哪些行为暂时无法关联 Knowledge？ → 学习任务完成（任务无知识点维度）；签到/图谱查看/AI 对话（非知识点型行为）
8. LearningEvent 是否正常进入 KnowledgeMastery？ → **是**（record() → processEvent() → recalc() 链路保留，既有测试回归通过）
9. H2 是否正常？ → **是**（56/56 测试跑在 H2，启动正常）
10. MySQL 是否正常？ → **是**（本阶段零 DDL，MySQL schema 未变；沿用 Phase 0 对齐结果）
11. mvn test 是否通过？ → **是**（56/56）
12. npm build 是否通过？ → vite build ✅ 通过；`npm run build`（= vue-tsc -b && vite build）仍被 166 处既有历史 TS 错误阻塞（非本阶段引入，见 §19.1）

---

*Phase 1 完成。未执行 Phase 2、未做 UI 重构、未引入任何新框架、未删除任何表/API/页面。等待下一条指令。*
