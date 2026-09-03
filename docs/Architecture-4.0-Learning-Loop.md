# KnowFlow Architecture 4.0 — Learning Loop（学习闭环架构设计）

> 适用范围：KnowFlow Web 端（frontend/ Vue3 + backend/ Spring Boot 3）。
> 配套执行报告见 `docs/Architecture-4.0-执行报告.md`。
> 本文是「目标 → 学习 → 理解 → 记忆 → 诊断 → 实践 → 表达 → 再学习」闭环的**架构蓝图**与**真实现状映射**，一切名称以真实代码为准。

---

## 1. 定位与边界

```text
App 是执行工具，Skill 是学习方法，Core 是学习闭环。
```

- **App（Registry + UserApp）**：可执行的入口（闪卡/练习/错题/费曼/路径…），继续由 `apps/registry.ts × stores/apps.ts` 唯一驱动，Skill 不注入侧边栏。
- **Skill（学习方法）**：FEYNMAN / FIRST_PRINCIPLES / ACTIVE_RECALL / SPACED_REPETITION 等能力定义 → 见 §4 映射表，落到已有 App / 工作流，不新建「12 个侧边栏菜单」。
- **Core（闭环）**：Goal → Path/Node → Material → Event → Evidence/Mastery → Intelligence → NextAction → App。本阶段补齐 **Goal 实体化**，把闭环的「起点」钉死。

## 2. 闭环主链路（最终形态）

```text
Learning Goal → Learning Plan/Path → Learning Node → Material
  → Knowledge Processing → Learning Method(理解/回忆) → Flashcard
  → Review → LearningEvent → Evidence → Mastery
  → Learning Intelligence(Diagnosis → Strategy) → NextAction → 真实 App → Evidence → 下一轮
```

## 3. 现状功能矩阵（扫描真实代码所得，非设计稿）

| 闭环概念 | 现有真实能力（文件/表/端点） | 已有程度 | 复用 / 升级方案 |
| --- | --- | --- | --- |
| 学习目标 Goal | **缺失**（`ai_personalized_path.goal` 仅是 AI 入参字符串） | 缺失 | **本次新增 `learning_goal`**，见 §4.1 |
| Learning Plan（日计划） | `learning_plan` + `/api/learning/plan/*`（F3 今日计划） | 已有 | 保持独立；Goal 的 dailyMinutes 可作为日计划输入，不合并 |
| Learning Path | `learning_path/chapter/user_path/user_chapter` + `/api/learning/paths|chapters|dag` + 报名/完成/进度 | 已有（含 DAG 前置依赖） | 复用；Goal 关联 path 后进度自动回显 |
| AI 个性化路径 | `personalized-path`（AI 生成+缓存）→ `adopt` 落地私有 path+chapter+自动报名 | 已有 | 复用为「目标 → 路线」生成链路 |
| Material（学习资料） | `learning_chapter.doc_ids` 关联文档、`learning_note` 章节笔记、`doc_document` | 部分已有 | 接入：资料即章节内容/关联文档 |
| Knowledge Processing | 文档实体抽取 → `kg_entity/kg_relation`、`resource_knowledge_mapping`(ACCEPTED) | 部分已有 | 复用映射构建 + `recalculate`；不做第二套抽取 |
| Feynman / 费曼 | App `feynman`（/workbench/story 故事化表达）+ AI 写作评价链路 | 已有形态 | 升级为方法：作为 RELEARN 策略的载体；产出入 `learning_event` |
| First Principles | 无独立 App | 未实现 | 本期只作概念与映射占位（见 §11 诚实披露），不造假 UI |
| Active Recall | `workbench` 回忆会话（三轮回忆 RECALL_COMPLETED）、闪卡问答 | 已有 | 复用；Recall 数据已进 Mastery 信号 |
| Flashcard 生成 | `my/flashcards` CRUD + AI generate/import/export、`reviewFlashcard`(SM-2) | 已有 | 复用；草稿→用户可改→保存已满足 |
| Review / 间隔重复 | SM-2 后端调度（`next_review_time` 回写）、`/flashcards/due`、ReviewPlan | 已有 | 复用为闭环「记忆」节点 |
| LearningEvent | `learning_event` 18 类事件、统一 `record()` 入口（REQUIRES_NEW + 幂等/吞错+掌握度联动） | 已有 | 复用它；Goal 不制造伪事件 |
| Evidence / Mastery | `knowledge_mastery` 引擎：6 信号整行重算（quiz/coding/recall/SM-2/错题/阅读），confidence/risk，按 `(user_id, knowledge_id)` 隔离 | 已有 | 复用，不重建；多轮 Evidence 由重算自然累积 |
| Learning Intelligence | 前端纯函数 `learning/intelligence/*`：7 Diagnosis + Strategy + 证据驱动（无真实数据不推导） | 已有 | 复用为决策层 |
| NextAction | `learning/action-priority.ts`(Review>Task>Weak>Path>Explore) + `learning/route.ts` 白名单 + `NextActionCard` | 已有 | 复用为「下一步」输出 |
| Practice / Scenario | Scenario Lab **已建（4.0-4）**：内容表 `learning_scenario` + `/api/learning/scenarios`（列表/详情/提交评分）+ `SCENARIO_COMPLETED` 事件；App `quiz/coding` 照旧 | 部分→已建 | 4.0-4 落地确定性要点覆盖评分（不改写掌握度）；**4.0-6 补内容管理端 CRUD**（`/api/admin/learning-scenarios` + `/admin/scenarios` 管理页），见 §4.2 |
| Expression / Writing | App `ai-writing`（SmartWriting 智能写作）；费曼表达四维评分器（4.0-7，/learning/feynman） | 已有 | 复用；写作/表达作为 Evidence 载体 |

## 4. 12 个 Skill → App 映射（Skill Registry 语义，落在一处文档）

| Skill | 落地 App（appKey / 路由） | 说明 |
| --- | --- | --- |
| FEYNMAN | `feynman` → /workbench/story | 故事化/费曼式表达 + AI 反馈；**4.0-7 起补确定性四维自检评分器**（/learning/feynman，Expression 节点） |
| FIRST_PRINCIPLES | `knowledge-graph`（概念拆解）+ 未来 capability | 本期仅占位映射，无独立 UI |
| ACTIVE_RECALL | `quiz`、`flashcard`、workbench 回忆会话 | 已有真实入口 |
| SPACED_REPETITION | `flashcard`（SM-2 review）、`review` 计划 | 后端调度 |
| KNOWLEDGE_SUMMARY | `knowledge`（知识库抽取映射）+ `ai-writing` | 复用既有知识加工链路 |
| LEARNING_PATH | 学习路径（/learning/paths、个性化路径 adopt） | 升级为 Goal 落地载体 |
| WEAK_POINT_DIAGNOSIS | 学习智能（Home/LearningCenter 诊断卡 + 教练） | 前端纯函数 |
| FLASHCARD_GENERATION | `flashcard` + AI generate | 已有草稿→保存流 |
| CODE_REVIEW | `coding`（CodeAgent / 代码练习） | 复用 |
| NOTE_RESTRUCTURE | `notes`、`cornell`、`mindmap` | 复用 |
| SCENARIO_SIMULATION | `scenario-lab` → `/learning/scenario`（**4.0-4 已建**；非 UserApp，入口=学习中心「更多工具」） | Practice 节点内容 + 确定性评分 |
| WRITING_REVIEW | `ai-writing`（SmartWriting） | 复用 |

## 4.1 本阶段新增：Learning Goal（唯一新增领域实体）

**为什么现有 API 无法支持（§50 硬性说明）：**
现有系统把「目标」仅作为 `ai_personalized_path.goal` 的生成入参字符串，落库后即丢失；`learning_path` 表也没有「目标」语义列。缺少一个持久的、可跨路径/跨任务的「用户目标」实体，闭环的起点（§七 目标 → 路径）无法回答「为什么学」以及「当前进行中目标是谁」。

**模型（表 `learning_goal`）：**

| 列 | 类型 | 语义 |
| --- | --- | --- |
| id / user_id | BIGINT | 主键；**userId 隔离**（逻辑外键 sys_user.id，建索引 `idx_lg_user(user_id,status)`） |
| title | VARCHAR(200) | 一句话目标（如「7 天掌握 JVM GC」），必填 |
| description / target | VARCHAR | 为什么学 / 做到什么算掌握 |
| status | VARCHAR(20) | ACTIVE（进行中）/ COMPLETED（达成）/ ARCHIVED（归档） |
| path_id | BIGINT | 关联真实 learning_path（逻辑外键，索引 `idx_lg_path`；可空） |
| start_date / deadline / daily_minutes | DATE/INT | 时间盒 |
| create/update/deleted | 通用 | BaseEntity + @TableLogic |

**行为约定：**
- 进度**不冗余存储**：VO 实时读 `learning_user_path.progress`（0~100 口径与 LearningService 一致）。
- 关联路径时校验可见性（公开 owner=0 / 本人私有），未报名自动走 `LearningService.enrollPath` 报名。
- 端点（全部按当前登录用户隔离）：GET `/learning/goals`、GET `/learning/goals/active`、POST、PUT `/{id}`、DELETE `/{id}`。

**前端接入：**
- 新路由 `/learning/goals`（学习目标页 = Goal Workspace），**不进侧边栏**（沿用「技能不生成菜单」红线），入口 = 首页「当前目标」横幅 + 目标页自身。
- Home 新增「当前目标」区块（闭环第一屏语义：目标 → 路线 → 进度 → CTA 去学习 / AI 生成路线）。
- 目标页（Goal Workspace，4.0-3）：活跃目标 Hero 带「当前节点」行 —— 由路径 DAG 的 `available` 节点（`learning/path-node.ts` 纯函数）推导「下一步该学哪一节」，主 CTA 直达 `/learning/chapter/:id`。
- 目标页「AI 生成路线」= 复用 `personalized-path`（生成）→ `adopt`（落地返回 pathId）→ 目标关联；**不新增第二条 AI 生成链路**。

## 4.2 本阶段新增（4.0-4）：Scenario Lab（Practice 节点）

把闭环的「Practice/实践」从「已有 quiz/coding 客观题」扩展出**开放式场景练习**：给定真实业务场景（如「线上 Full GC 频繁」），用户闭卷写出排查/设计思路，系统按要点关键词做**确定性覆盖评分**（§40 Deterministic-first：不调用 LLM 评分、不伪造掌握度联动）。

**模型（表 `learning_scenario`，内容共享，无 userId 隔离）：**

| 列 | 类型 | 语义 |
| --- | --- | --- |
| id / title / status | BIGINT / VARCHAR(200) / INT | 主键；标题；1 上架 / 0 下架 |
| context_text / question_text | TEXT / VARCHAR(1000) | 情景背景（可选）/ 开放式问题（必填） |
| key_points | TEXT | 要点清单 JSON：`[{label, keywords[]}]`。**keywords 只存服务端**用于评分，详情接口只下发 label（避免提前泄露答题口径） |
| difficulty | INT(1-3) | 难度：入门 / 进阶 / 挑战 |
| knowledge_id | BIGINT NULL | 逻辑外键 kg_entity.id（可空；种子数据不悬挂引用故为 NULL） |

**行为约定（Deterministic Workflow，§40）：**
- 端点（需登录，内容共享、事件按用户隔离）：`GET /api/learning/scenarios`（支持 knowledgeId/difficulty 过滤）、`GET /{id}`、`POST /{id}/submit`。
- 评分 = 要点覆盖度：每条要点**任一个关键词命中即覆盖**；规范化规则 = 小写 + 剔除空白/标点/符号后做子串包含。`coverage = matched/total`，`score = round(coverage*100)`，分档点评由阈值驱动（非 AI 生成）。局限（同义改写 / 噪音词误命中）在执行报告如实披露。
- 提交后统一落 `learning_event`（`SCENARIO_COMPLETED`，resourceType=`SCENARIO`），元数据携带 scenarioId/title/difficulty/coverage/score/matched/total/durationSeconds/answerLength。**不改写 knowledge_mastery**（掌握度联动留待引擎接入，避免伪造 Evidence）。
- 内容维护走 SQL 种子 + **后台管理 CRUD（4.0-6 已交付）**：管理端端点前缀 `/api/admin/learning-scenarios`（SecurityConfig `/api/admin/**` + Controller `@PreAuthorize("hasRole('ADMIN')")` 双保险），管理页 `/admin/scenarios`（侧边栏「内容管理」组）提供新建 / 覆盖式编辑 / 上下架切换 / 逻辑删除；`keywords` 只在该管理后台可见（唯一读入口）。

**前端接入：**
- 新路由 `/learning/scenario`（Scenario Lab 页 = 场景列表 → 闭卷作答 → 评分反馈（命中/漏点清单 + 覆盖进度）→ 对照漏点再答），**不进侧边栏**；入口 = 学习中心「更多工具」卡片「场景练习」；支持深链 `?id=N` 直达指定场景作答。
- 事件页 `/learning/events` 补齐 `SCENARIO_COMPLETED` 的标签（场景练习）与徽章色，历史记录可筛可看。
- **补强回路（4.0-5）**：学习中心「待复习」下新增「待补强场景」条带——由真实 `SCENARIO_COMPLETED` 事件推导（纯函数 `learning/scenario-debt.ts`：每场景取最近一次提交，`matched<total` 即待补强，覆盖度升序），点击深链回该场景重答；刻意**不进 action-priority 健康链**（不稀释 Review/Task/Weak），不接掌握度引擎。
- **内容管理（4.0-6）**：后台路由 `/admin/scenarios`（layout b + requiresAdmin），页面 `views/admin/ScenarioMgmt.vue` 消费 `adminApi.scenarios/scenarioDetail/createScenario/updateScenario/removeScenario`；要点编辑器按「label + 关键词行」维护，前端按分隔符拆词、服务端再校验（label 必填、每要点 ≥1 关键词、难度 1-3、状态 0/1、标题/问题长度上限对齐 schema）。

## 4.3 本阶段新增（4.0-7）：费曼表达四维评分器（Expression 节点）

把「理解了」推向「讲得清」：作者选一个刚学的主题，写下给外行看的解释，再按 **Accuracy/Clarity/Completeness/Structure** 四维各 4 条**可观察自检准则**逐条核验勾选，服务端确定性计分并落事件。**不新增任何表**（表达记录只沉淀事件），与 Scenario 同属「确定性证据候选」，不直写掌握度。

**评分口径（Deterministic-first，§40）：**
- rubric 由服务端常量唯一定义并下发（`GET /api/learning/feynman/rubric`）：维度顺序固定（准确性 → 清晰度 → 完整度 → 结构），每维 4 条稳定 id 自检准则 + 计分口径说明。
- 提交（`POST /api/learning/feynman/submit`，需登录）：主题 ≤200 / 正文 1~10000 必填 → 勾选集合按 rubric 白名单过滤（未知 id 忽略、同一准则 Set 去重只计一次）→ 维度分 `round(勾选/4×100)`、总分 `round(勾选/16×100)` → 确定性分档点评（阈值驱动，非 AI）。
- 事件：`FEYNMAN_EXPRESSED`（resourceType=FEYNMAN、**resourceId=0**——无内容实体，按 user+type+时间查询），meta 携带 topic/explanationLength/overall/checkedTotal/totalCriteria + 四维 score 与 checked。

**诚实边界（页面与执行报告均明示）：** Accuracy 无机器可验证参照系，以「作者对照资料自检」为据；Clarity/Structure 以作者通读/朗读核验为准。系统不伪装语义判分——本评分器是「作者自检的结构化表达证据」，不是 AI 语义评分器。

**前端接入：**
- 新路由 `/learning/feynman`（layout c、requiresAuth、**不进侧边栏**）；页面 `FeynmanLab.vue`：主题 + 解释正文 + 四维勾选卡（实时 已勾选/4）→ 结果面板（总分分档色 + 四维进度条 + 未勾选准则补强清单 + 再写一遍）。
- 学习中心「更多工具」+「费曼表达」入口（icon `book-open`）；事件页补 `FEYNMAN_EXPRESSED`（费曼表达）标签与徽章色。

## 5. 数据流（统一）

```text
User Action → App → LearningEvent(backend 统一 record)
  → MasteryEngine(整行重算) → 前端 Learning Intelligence(signals→diagnosis→strategy)
  → NextAction(action-priority + route.ts 白名单) → 打开真实 App 路由 → 新 Evidence → 下一轮
```

AI 边界（§41-42）：AI 只负责解释/总结/生成/评价/建议，输出 `Evidence Candidate`；Core（状态/事件/掌握度/复习调度/UserApp）状态一律由确定性代码落库，AI 不直写。

## 6. MVP 闭环（本阶段真实可跑通的最小链路）

以「7 天掌握 JVM GC」为样例目标，逐节点核对真实落点：

| # | 环节 | 真实落点 |
| --- | --- | --- |
| 1 | 创建学习目标 | POST /learning/goals（✅ 新增，已冒烟） |
| 2 | 得到学习路线 | AI 个性化路径生成+采用（复用已有）或关联现有路线（POST goals 带 pathId 自动报名 ✅） |
| 3 | 学习路径 / 节点 | learning_chapter（含前置依赖 DAG），PathDetail 学习/完成章节 |
| 4 | 关联学习资料 | 章节关联文档（doc_ids）+ 章节内容 |
| 5 | 知识结构 | 知识库抽取 → kg_entity + resource_knowledge_mapping（复用） |
| 6 | 理解方法 | Feynman（App）/ 康奈尔 / 故事表达 |
| 7 | 主动回忆 | 闪卡答题 / 回忆会话（产生 RECALL_COMPLETED / FLASHCARD_REVIEWED） |
| 8 | 闪卡生成 | my/flashcards AI 生成（草稿可改） |
| 9 | 复习 | reviewFlashcard SM-2 回写 next_review_time（后端） |
| 10 | LearningEvent | 上述动作全部由既有 Service 埋点落 learning_event（复用） |
| 11 | Evidence→Mastery | MasteryEngine 按 (user, knowledge) 重算（复用） |
| 12 | 诊断→策略 | 前端 intelligence 纯函数（REVIEW_OVERDUE / WEAK_KNOWLEDGE / …真实数据触发） |
| 13 | NextAction | action-priority（Review>Task>Weak>**GoalPath**>Path>Explore，4.0-2 起接入 Goal 上下文：目标路线 0% 即可起步、优先于其它进行中路径）→ NextActionCard |
| 14 | 打开真实 App | route.ts 白名单 → /learning/flashcards、/learning/review、/mistakes、/learning/path/:id 等 |
| 15 | 再次产生 Evidence | 回到 App 内动作（既有埋点） |

> 诚实说明：其中 6（Feynman 表达）已有故事化/费曼式 App + AI 写作反馈；**4.0-7 起补四维确定性自检评分器**（/learning/feynman，作者自检勾选计分 + FEYNMAN_EXPRESSED 事件，非 AI 语义判分）。Scenario/CodeReview/写作评价的“AI 标准评分卡”仍是既有交互形态而非新评分器，未伪造假完成态。

## 7. 文件与职责归属（前端）

- `learning/` = 学习闭环纯逻辑层（intelligence 诊断、action-priority、route 白名单、coach 教练），不持有状态。
- `views/` 页面（Home / LearningCenterPage / PathDetail / FlashCardsHub / ReviewPlan / LearningGoals…）消费纯函数与 API。
- `components/workflow/` = NextActionCard / LearningStateCard / 结果面板等闭环 UI 组件。
- `components/home/` = Home 专用卡片。

## 8. 与既有架构红线的一致性

- UserApp / resolveNavigation / Registry：未改，Skill 不进侧边栏（§48）。
- `/calendar`、`/tasks` 路由未挂 `/workbench` 前缀；Pinia store ID 未改；事件按日期区间查询（未全量拉取）。
- 新增表 `learning_goal`：索引带表前缀 `idx_lg_*`（规避 H2 全局唯一命名冲突）；H2/MySQL schema 同步 + MySQL 迁移文件；逻辑外键、无物理外键。
- 新增表 `learning_scenario`（4.0-4）：索引带表前缀 `idx_lsc_*`（status / knowledge_id）；H2/MySQL schema 与 data.sql 种子同步 + MySQL 迁移文件 `V20260904__learning_scenario.sql`（`INSERT ... SELECT ... WHERE NOT EXISTS` 按标题去重，幂等）。
- 4.0-6 **未新增任何表/列**（内容管理复用 `learning_scenario`）；管理端点 `/api/admin/learning-scenarios` 由 SecurityConfig `/api/admin/**`（hasRole ADMIN）+ Controller `@PreAuthorize` 双保险保护（HTTP 冒烟：user1=403、匿名=401）。
- 4.0-7 **未新增表/列**：费曼表达仅沉淀 `FEYNMAN_EXPRESSED` 事件（无内容实体，resourceId=0，按 user+type+时间查询）；事件类型追加安全（本仓无对 LearningEventType 的穷举 switch，已核对）。

## 9. 下一阶段建议

1. **Scenario Lab 主体（4.0-4）+ 补强回路（4.0-5）+ 内容管理 CRUD（4.0-6，2026-09-03）**：`learning_scenario` 内容 + 学习端列表/详情/提交评分 API + `SCENARIO_COMPLETED` 事件 + `/learning/scenario` 页面/学习中心入口 + 「待补强场景」事件驱动条带（深链再答）+ 后台 `/api/admin/learning-scenarios` CRUD 与 `/admin/scenarios` 管理页 均已落地；**剩余**：把 SCENARIO 作为正式证据并入掌握度引擎或 Weak 诊断（需先评审证据权重与 UserApp fallback 语义）。
2. ~~Feynman 评分四维（Accuracy/Clarity/Completeness/Structure）真实验证入库为 Evidence~~ → **已完成（Architecture-4.0-7，2026-09-03）**：`/learning/feynman` 费曼表达四维自检评分器（rubric 服务端常量 + 确定性计分 + FEYNMAN_EXPRESSED 事件）；Accuracy 以作者对照资料自检为据（无机器参照，已在文档/UI 明示，不伪装语义判分）。
3. ~~NextAction 增加 Goal 上下文~~ → **已完成（Architecture-4.0-2，2026-09-03）**：`resolveNextAction` 新增可选 `goal`/`goalPath` 上下文；有进行中目标且关联路线时，PATH 决策优先目标路线（0% 起步也可推荐，健康信号 Review/Task/Weak 优先级不变）。
4. ~~Goal Workspace「当前节点」~~ → **已完成（Architecture-4.0-3，2026-09-03）**：目标 Hero 由路径 DAG `available` 节点推导「下一步该学哪一节」，主 CTA 直达 `/learning/chapter/:id`。
5. First Principles 作为独立学习方法的交互 MVP。
