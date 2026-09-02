# Phase 0 执行报告 —— 数据模型收敛与系统稳定化

> 日期：2026-08-30 · 基线 commit：`36bd7ec` · 类型：本阶段为「数据模型收敛」，未做 AI/RAG/图谱/UI 重构
> 原则遵循：不推翻现有系统、最小修改、旧 API 兼容、旧数据兼容、不引入新框架。

---

## 1. 修改前问题

| # | 问题 | 严重度 | 证据（真实源码） |
|---|---|---|---|
| 1 | **6 个孤儿实体无 DDL**：`learning_note` / `chapter_favorite` / `user_pet` / `weekly_report` / `ai_learning_suggestion` / `sys_user_pref`，Entity+Mapper+Controller 全部存在且在用，但 H2/MySQL schema 与全部 migration 中均无建表语句 → 一旦调用即 SQL 报错 | 🔴 P0 | `entity/*.java` 均有 `@TableName`；`controller/*Controller.java` 均注册 `/api/learning/...`；`grep CREATE TABLE` 三个 schema + 5 个 migration 均 0 命中 |
| 2 | **双套 SM-2 实现**：`learning_flashcard`（LearningServiceImpl.reviewFlashcard，简化版）与 `wb_review_card`（WorkbenchServiceImpl.gradeReview，完整版）各写一份间隔重复算法，后续升级需改两处 | 🟠 P1 | 两处均有独立 EF/interval 计算 |
| 3 | **错题无法回溯原题**：`learning_mistake` 只存文本快照，无 `question_id`/`code_question_id` → 无法 错题→原题→解析→知识点→再练习 | 🟠 P1 | `entity/LearningMistake.java`、`db/h2/schema.sql` 360-375 行 |
| 4 | **H2/MySQL schema 漂移**：H2 缺 4 张表（`habit`/`habit_checkin`/`task_tag`/`task_tag_rel`），MySQL 有 | 🟡 P2 | 表清单 diff |
| 5 | **前端孤儿页面缺 API 层**：`WeeklyReport.vue` / `LearningPrefs.vue` 调用 `learningApi.getWeeklyReport` 等不存在的方法、引用不存在的类型 → 类型错误（后端接口实际存在） | 🟡 P2 | `api/learning.ts` 178 行内无相关方法 |
| 6 | **前端构建断裂（既有）**：184 处 TS 类型错误 + `ImportTemplates.vue` 模板语法错误（15 处裸属性缺引号）阻塞 `vite build` | 🟡 P2（历史遗留） | vue-tsc 基线 184 错误 |
| 7 | 学习报告页 `getReport`/`LearningReportData` 缺 API 层（后端 `LearningReportController` 已存在） | 🟡 P2 | `api/learning.ts` 无 getReport |

---

## 2. 修改了哪些文件

### 后端（8 改 + 3 新增）
| 文件 | 改动 |
|---|---|
| `entity/LearningFlashcard.java` | +`easeFactor` 字段（SM-2 ×100） |
| `entity/LearningMistake.java` | +`questionId` +`codeQuestionId` 字段 |
| `service/SrsService.java` | **新增**：统一 SRS 服务接口（SM-2 唯一入口，含 SrsState/SrsResult） |
| `service/impl/SrsServiceImpl.java` | **新增**：SM-2 算法唯一实现 |
| `service/impl/LearningServiceImpl.java` | `reviewFlashcard` 改为调用 SrsService |
| `service/impl/WorkbenchServiceImpl.java` | `gradeReview` 改为调用 SrsService（行为逐位等价）；移除冗余 MIN_EF 常量 |
| `service/impl/QuizServiceImpl.java` | 答错同步错题时 `setQuestionId(q.getId())` |
| `service/impl/MistakeServiceImpl.java` | `collectCodeMistake` 记 `codeQuestionId`；`addMistake`/`collectCodeMistake` 合并旧数据时回填回溯字段 |
| `vo/MistakeVO.java` | +`questionId` +`codeQuestionId`（BeanUtil 拷贝输出） |
| `resources/db/mysql/migration/V20260830__phase0_data_model_convergence.sql` | **新增**：存量 MySQL 库幂等迁移（建 6 表 + 加列 + 加索引，存储过程守卫） |
| `src/test/java/com/knowflow/service/SrsServiceImplTest.java` | **新增**：SM-2 单元测试 5 例 |

### 数据库脚本（2 改）
| 文件 | 改动 |
|---|---|
| `resources/db/h2/schema.sql` | +6 张孤儿表 +`habit`×2 +`task_tag`×2；`learning_flashcard` 加 `ease_factor`；`learning_mistake` 加 `question_id`/`code_question_id` + 索引 |
| `resources/db/mysql/schema.sql` | 同上（habit/task_tag 已存在，仅补其余） |

### 前端（4 改）
| 文件 | 改动 |
|---|---|
| `api/types.ts` | +`WeeklyReportVO` +`UserLearningPrefInput/VO` +`LearningReportData`；`MistakeVO` +回溯字段 |
| `api/learning.ts` | +`getWeeklyReport/listWeeklyReports/generateWeeklyReport` +`getPrefs/savePrefs` +`getReport` |
| `views/WeeklyReport.vue` | Icon class 写法、3 处 `?? 0` 兜底（类型化后暴露） |
| `views/LearningReportView.vue` | `categoryMastery` 可空兜底 |
| `views/ImportTemplates.vue` | 修复 15 处模板裸属性缺引号（**阻塞 vite build 的既有语法错误**） |

---

## 3. 数据库修改

- **H2（`db/h2/schema.sql`）**：76→82 张表；全部含 `id/create_time/update_time/deleted` 四件套；索引名带表前缀（`idx_learning_note_user` 等），H2 用 `IF NOT EXISTS` 幂等。
- **MySQL（`db/mysql/schema.sql`）**：与 H2 表清单完全一致（82=82），`ENGINE=InnoDB CHARSET=utf8mb4`。
- **存量库迁移（`V20260830__phase0_data_model_convergence.sql`）**：6 张孤儿表 `CREATE TABLE IF NOT EXISTS`；`learning_flashcard` 加 `ease_factor INT DEFAULT 250`；`learning_mistake` 加 `question_id BIGINT`/`code_question_id BIGINT`（均可空）+ 3 个索引；存储过程 + `information_schema` 守卫，重复执行不报错。
- **唯一索引取舍**：`weekly_report` 用 `uk_weekly_report_user_week(user_id, week_start, deleted)`（与 Mapper 注释一致）；`sys_user_pref` 只用普通索引 `idx_sys_user_pref_user`，**不用**含 deleted 的唯一索引（规避「逻辑删除两行 deleted=1 冲突 500」的已知坑，Service 层 upsert 保证单条）；`chapter_favorite` 用普通索引（toggle 逻辑删除后重插不冲突）。

---

## 4. Entity 修改

| 实体 | 新增字段 |
|---|---|
| `LearningFlashcard` | `easeFactor`（Integer，×100，默认 250） |
| `LearningMistake` | `questionId`、`codeQuestionId`（Long，可空） |

---

## 5. Service 修改

- **新增 `SrsService` + `SrsServiceImpl`**：SM-2 唯一实现入口。
  - `grade(quality, passQuality, state, now)`：EF 公式 `EF' = EF + (0.1-(5-q)(0.08+(5-q)*0.02))`、下限 1.3；遗忘（q<passQuality）repetitions=0/interval=1/lapseCount+1；通过 1/6/interval×EF；quality 自动夹取 [0,5]。
  - 两个领域各自传及格阈值：学习闪卡 `passQuality=3`、工作台复习卡 `passQuality=2`（与旧行为逐位一致）。
- `LearningServiceImpl.reviewFlashcard` / `WorkbenchServiceImpl.gradeReview`：删除各自内联的 SM-2 计算，改为调用 `srsService.grade(...)` 后落库；**API 签名、返回结构、事件、日志不变**。
- `QuizServiceImpl`：答错同步错题时写入 `questionId`。
- `MistakeServiceImpl`：代码错题归集写入 `codeQuestionId`；合并旧数据（同用户同题干）时若新数据带回溯字段则回填，**旧数据 questionId 保持 null 继续工作**。

---

## 6. Controller 修改

**无**。API 全部保持兼容（`MistakeController.add` 直接接收实体，新字段自动透传；`POST /api/learning/flashcards/{id}/review`、`POST /api/workbench/reviews/{id}/grade` 签名未动）。

---

## 7. 前端修改

- **API 层补齐**（均为后端已存在接口的客户端封装，无 UI 改动）：
  - 周报：`getWeeklyReport` / `listWeeklyReports` / `generateWeeklyReport` → `/learning/weekly-report`
  - 偏好：`getPrefs` / `savePrefs` → `/learning/prefs`
  - 学习报告：`getReport(period)` → `/learning/report`（`LearningReportView` 页所需）
- **类型补齐**：`WeeklyReportVO`、`UserLearningPrefInput/VO`、`LearningReportData`；`MistakeVO` + `questionId/codeQuestionId`。
- **小修复**：`WeeklyReport.vue` Icon class 写法 + 3 处 `?? 0`；`LearningReportView.vue` `categoryMastery` 可空兜底；`ImportTemplates.vue` 15 处模板裸属性缺引号（既有语法错误，阻塞 vite build）。
- **未做**：未加新路由、未重构任何 UI（WeeklyReport/LearningPrefs 页面仍未被路由引用，属既有状态）。

---

## 8. Flashcard/SRS 收敛结果

```
学习闪卡 reviewFlashcard ─┐
                          ├─→ SrsService.grade() ← SM-2 唯一实现
工作台复习卡 gradeReview ─┘          │
                                    ├─→ 各自表落库（表不删）
                                    ├─→ LearningEvent(FLASHCARD_REVIEWED)
                                    └─→ 掌握度引擎（MasteryEngine 聚合，原链路不变）
```

- 收敛前：两处独立实现（简化版 + 完整版）。
- 收敛后：**算法仅存在于 `SrsServiceImpl` 一处**；两张表（`learning_flashcard`/`wb_review_card`）结构保留，仅 `learning_flashcard` 补了 `ease_factor` 列以承载 SM-2 状态。
- 工作台复习卡行为**逐位等价**（同公式、同阈值 2）；学习闪卡从「固定 EF 查表」升级为标准 SM-2 公式并持久化 EF，间隔略有变化（更标准）。
- 运行时验证：`POST /flashcards/1/review?quality=5` 与 `quality=1`（遗忘）均 200，ease_factor 列可写。

---

## 9. Task 收敛结果

**结论：不合并（分析结论，未改动）。**
- 两个任务模型是**不同领域**：`learning_task`（学习任务，学习中心语义，带 exp_reward/energy_cost）+ `task`/`task_list`（Things3 式通用任务，任务中心），均由 `LearningPlanServiceImpl` 作为**聚合层**编排（学习计划 = 学习任务 + 习惯 + 待办 + 时间块）。
- `learning_plan.learning_task_ids/habit_ids/todo_ids` 为 JSON 快照（计划编排产物），Service 层已在生成时用候选项池校验（`LearningPlanServiceImpl` 618-769 行），存在「快照过期」的理论一致性风险，但**破坏式合并会损伤两个活跃功能**，超出 Phase 0「最小修改」边界。
- 处置：保留现状，记录为后续 Phase 观察项。

---

## 10. Note 收敛结果

**结论：不合并（两个不同概念）。**
- `learning_note`（章节学习笔记：user_id + chapter_id + path_id + title + content，绑定学习路径/章节）。
- `wb_note`（工作台康奈尔笔记：capture_id + cue/note/summary_column，属于 收集→整理→复习→输出 工作台管线）。
- 二者数据模型与产品语义不同，均活跃。真正的重复是**前端 `/notes` 个人笔记（纯 localStorage，无后端）**，属前端功能，不在 Phase 0 接入后端（避免新增功能）。本轮为 `learning_note` 补了 DDL（A 类：正在使用）。

---

## 11. Mistake 回溯结果

- 表结构：`learning_mistake` + `question_id`（→quiz_question.id）+ `code_question_id`（→code_question.id），均为**可空逻辑外键**（项目无物理 FK）。
- 写入口：测验答错（`QuizServiceImpl` 带 questionId）；代码错题归集（`MistakeServiceImpl.collectCodeMistake` 带 codeQuestionId）；前端手动添加错题（`MistakeController.add` 透传实体字段）。
- 合并回填：旧错题再次答错/归集时自动回填回溯字段；**存量数据不受影响**（可空）。
- 输出：`MistakeVO` 返回 `questionId/codeQuestionId`，前端 `MistakeVO` 类型已同步。
- 闭环打通：`错题 →(question_id)→ 原题(解析/选项) →(resource_knowledge_mapping)→ 知识点 → 掌握度 → 再次练习`。
- 运行时验证：collect-code 带 questionId=8 → 错题记录 `codeQuestionId: 8` ✅。

---

## 12. H2/MySQL 对齐结果

- 表清单：H2 与 MySQL 均为 **82 张，diff 为空** ✅（此前 H2 缺 4 张：habit/habit_checkin/task_tag/task_tag_rel，已补齐）。
- 字段：孤儿表与新增列的字段名/默认值/注释在两端一致（H2 `TINYINT`、MySQL `TINYINT(1)` 等方言差异已按语义对齐）。
- 根目录 `resources/schema.sql`：为最旧快照，**未被任何配置引用**（application.yml 指向 db/h2 与 db/mysql），标记候选废弃（不删除）。

---

## 13. 删除/废弃内容

- **零删除**。未删表、未删 API、未删前端页面。
- 移除 1 个冗余常量：`WorkbenchServiceImpl.MIN_EF`（逻辑已收敛进 SrsService）。
- 候选废弃（未动）：根目录 `resources/schema.sql`（旧快照，无引用）。

---

## 14. 测试结果

| 项 | 结果 |
|---|---|
| 后端编译 `mvn compile` | ✅ 通过 |
| 后端全量测试 `mvn test` | ✅ **48/48 通过**（0 失败 0 错误），含既有 MasteryEngineIntegrationTest 19 项 + 新增 SrsServiceImplTest 5 项 |
| Spring Boot 启动（H2 内存库，端口 8080） | ✅ 启动成功，H2 schema 全量执行无 SQL 错误 |
| 孤儿表运行时验证 | ✅ `learning_note`(200) / `chapter_favorite`(200) / `user_pet`(200 自动建行) / `sys_user_pref`(200) / `weekly_report`(200 AI 降级) / `ai_learning_suggestion`(200 AI 降级) |
| SRS 运行时验证 | ✅ 闪卡复习 quality=5 / quality=1 均 200，ease_factor 列可写 |
| 错题回溯运行时验证 | ✅ collect-code 带 questionId → `codeQuestionId` 正确返回 |
| 前端类型检查 `vue-tsc -b` | ✅ 基线 184 错误 → 166（**-18**）；Phase 0 触碰文件（WeeklyReport/LearningPrefs/learning.ts/types.ts/LearningReportView）**全部清零**，无新增错误 |
| 前端打包 `vite build` | ✅ 通过（2932 模块，3.76s）——修复了 ImportTemplates.vue 既有语法错误后恢复可打包 |

> 说明：`npm run build`（= vue-tsc -b && vite build）仍会因 **166 处既有历史类型错误**（RedBlackTree/StudyGroup/ObsidianImport/FlashCardsHub/markdown.ts 等 25+ 文件）在 vue-tsc 阶段失败——这些是 2026-07-28 审查已记录的技术债，与 Phase 0 无关，列入剩余问题。

---

## 15. 剩余问题

1. **前端 166 处既有类型错误**（非本阶段引入）：RedBlackTree(5) / StudyGroup(10) / ObsidianImport(12) / FlashCardsHub(30) / markdown.ts(14) / Messages(10) / WorkbenchPalaceEdit(8) / CodeAgent(6) 等 → 需独立「前端构建修复」阶段（建议后续 Phase 专项）。
2. `api/types.ts` **CategoryVO 重复声明冲突**（159 行与 1925 行附近，既有）→ 合并两个重复接口即可消 6 错误。
3. `DocDetail.vue(461)` 调用 `learningApi.chaptersByDoc`，**后端无此接口** → 需后端补接口或前端移除调用（超出 Phase 0）。
4. `WeeklyReport.vue` / `LearningPrefs.vue` 已修好 API 层但**仍未挂路由**（用户不可见）→ 建议在体验优化 Phase 挂路由。
5. `learning_plan` JSON 快照一致性、双任务模型、`/notes` 个人笔记无后端——均为后续 Phase 观察项。
6. 根目录 `resources/schema.sql` 旧快照候选废弃。
7. 生产 MySQL 需手工执行新迁移脚本（项目既定迁移策略，未引入 Flyway）。

---

## 16. 下一阶段建议

- **Phase 1（学习事件补缺口）**：费曼故事/康奈尔笔记创建补 `WRITING` 类 LearningEvent 与知识点映射（写作入环）；学习域前端共享 store（mastery/tasks/paths 去重）。
- **前置小项**：前端专项修复（166 错误 + CategoryVO 重复 + chaptersByDoc），可在 Phase 1 前单独一轮清掉，恢复 `npm run build` 全绿。
- **后续大项**：KG+RAG 升级（Phase 6）前，先落地「统一 KnowledgeRetrievalService」接口契约。

---

*Phase 0 完成。未执行 Phase 1+、未做 UI 重构、未引入任何新框架。等待「执行 Phase 1」指令。*
