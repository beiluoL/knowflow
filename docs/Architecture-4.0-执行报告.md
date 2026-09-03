# KnowFlow Architecture 4.0 — 执行报告

> 日期：2026-09-03 ｜ 范围：Web 端（frontend/ + backend/）｜ 方式：真实扫描 → 最小增量 → 真实测试 → 诚实披露
> 架构文档：`docs/Architecture-4.0-Learning-Loop.md`

---

## 1. 本轮做了什么（结论先行）

**把学习闭环的「起点」钉成了真实实体：Learning Goal（学习目标）**，并让「目标 → 路线 → 节点 → 进度」成为一条真实可跑、可测、可在首页看到的数据链：

- 后端新增 `learning_goal` 表（H2 + MySQL schema 同步 + MySQL 迁移文件）+ 实体/Mapper/Service/Controller，5 个端点全部按 userId 隔离。
- 目标关联学习路径时**自动报名**，目标进度实时读 `learning_user_path`（不冗余存进度）。
- 前端新增 `/learning/goals` 学习目标页（创建/编辑/完成/重开/删除 + 关联路线 + **AI 生成路线**（复用既有 personalized-path→adopt 链路））。
- 首页新增「当前目标」横幅（无目标时引导设定），与既有 NextAction 形成「目标 → 下一步」的第一屏语义。

**质量门禁（全部通过，无删改旧断言）：**

| 门禁 | 结果 |
| --- | --- |
| 后端 Maven 测试 | **77/77 PASS**（基线 71 + 新增 Goal 闭环测试 6） |
| 前端 vue-tsc -b（TypeScript） | **0 errors** |
| vite build | **PASS** |
| Vitest | **241/241 PASS**（21 个测试文件） |
| HTTP 冒烟（18080 独立实例） | 登录→建目标→关联路径→完成→让位 全链路通过 |

## 2. 真实扫描结论（已有 vs 缺失）

**已有（复用，绝不重复建）：**
- 路径层：`learning_path / learning_chapter(prerequisite DAG) / learning_user_path / learning_user_chapter`，报名/完成/进度/证书齐全。
- AI 个性化路径：生成（按 user+goal+level+daily 缓存）→ 采用落地 → 自动报名，幂等。
- 事件层：`learning_event` 18 类事件，统一 `record()` 入口（REQUIRES_NEW，失败不阻断），已埋点覆盖 Quiz/Doc/Mistake/Workbench/Learning/Chat/CheckIn。
- 掌握度：`knowledge_mastery` 引擎 6 信号整行重算 + confidence/risk + 状态机，按 `(user_id, knowledge_id)` 隔离。
- 学习智能（前端纯函数）：`learning/intelligence`（7 种 Diagnosis：REVIEW_OVERDUE / WEAK_KNOWLEDGE / REPEATED_MISTAKE / LOW_ACCURACY / PATH_STALLED / INACTIVE_LEARNING / HEALTHY_PROGRESS；Strategy 7 类；证据驱动、缺数据不推导）。
- NextAction：`learning/action-priority.ts`（Review > Task > Weak > Path > Explore）+ `learning/route.ts` 动作白名单 + NextActionCard / LearningStateCard。
- 12 Skill 中 10 个能在上述真实能力里找到落地（映射见架构文档 §4）。

**缺失（本阶段只补这一个真实缺口）：**
- ❌ 独立 Goal 实体：此前「目标」只是 AI 生成入参字符串（`ai_personalized_path.goal`），落库即丢，`learning_path` 无目标语义列，无法支撑「你当前的目标是什么 / 走到哪 / 下一步」。

## 3. 变更清单

### 后端（新增 8 文件，改动 3 文件）
| 文件 | 类型 | 说明 |
| --- | --- | --- |
| `backend/.../entity/LearningGoal.java` | 新增 | learning_goal 实体（BaseEntity） |
| `backend/.../mapper/LearningGoalMapper.java` | 新增 | MyBatis-Plus Mapper |
| `backend/.../dto/GoalDTO.java` | 新增 | 覆盖式入参 |
| `backend/.../vo/LearningGoalVO.java` | 新增 | 含 pathTitle/pathProgress/pathChapterCount |
| `backend/.../service/LearningGoalService.java` | 新增 | 接口 |
| `backend/.../service/impl/LearningGoalServiceImpl.java` | 新增 | 逻辑外键校验/自动报名/进度回显/隔离 |
| `backend/.../controller/LearningGoalController.java` | 新增 | GET list / GET active / POST / PUT / DELETE |
| `backend/src/test/.../LearningGoalClosedLoopTest.java` | 新增 | 6 用例闭环集成测试（真实 H2） |
| `backend/src/main/resources/db/h2/schema.sql` | 改动 | +learning_goal 建表与索引（追加尾部） |
| `backend/src/main/resources/db/mysql/schema.sql` | 改动 | +learning_goal（utf8mb4 方言） |
| `backend/src/main/resources/db/mysql/migration/V20260903__learning_goal.sql` | 新增 | 存量 MySQL 8 幂等补表 |

### 前端（新增 2 文件，改动 4 文件）
| 文件 | 类型 | 说明 |
| --- | --- | --- |
| `frontend/src/views/LearningGoals.vue` | 新增 | 学习目标页（自包含令牌化样式） |
| `frontend/src/api/types.ts` | 改动 | +LearningGoalVO / LearningGoalInput / LearningGoalStatus |
| `frontend/src/api/learning.ts` | 改动 | +learningApi.goals/activeGoal/createGoal/updateGoal/removeGoal |
| `frontend/src/router/index.ts` | 改动 | +/learning/goals（layout c，requiresAuth，不进侧边栏） |
| `frontend/src/views/Home.vue` | 改动 | +「当前目标 / 设定目标」横幅区块与加载 |

> 说明：未触碰工作区中已有的 UI/UX 3.3.2 未提交改动（CLayout.vue 及其测试、UI-UX-3.3* 文档）；未 commit、未 push（按 Git 安全约定）。

## 4. 为什么新增这 5 个 API（每一条的现有替代论证）

| 端点 | 为什么现有 API 无法支持 |
| --- | --- |
| GET /learning/goals | 没有任何实体承载「用户目标列表」，learning_path 是路线不是目标 |
| GET /learning/goals/active | 无「当前进行中目标」概念；路径「已报名且在学」≠「用户声明的目标」 |
| POST /learning/goals | 目标持久化缺失；personalized-path 生成的是 AI 推荐缓存而非用户目标 |
| PUT /learning/goals/{id} | 同上；adopt 只会落地路径，不会维护目标状态机（ACTIVE/COMPLETED） |
| DELETE /learning/goals/{id} | 逻辑删除目标；adopt 无「删除目标」语义（只能删推荐缓存） |

**刻意没有新增的 API**（复用已有，防 CRUD 膨胀）：章节完成、进度、闪卡复习、掌握度、错题、个性化路径生成/采用 —— 全部复用既有端点。

## 5. 测试覆盖（新增 6 用例，全真实 H2 无 mock）

`LearningGoalClosedLoopTest`（@SpringBootTest @ActiveProfiles("local")）：
1. 创建目标（无路径）→ 列表可见 → activeGoal 返回（空时返回 null）
2. 创建目标并关联公开路径 → 自动报名 → **真实完成 1/6 章节后目标进度实时联动为 17**
3. 状态流转 COMPLETED → activeGoal 让位给下一个 ACTIVE
4. 跨用户隔离（A 看不到 B；越权更新他人目标 404）
5. 关联他人私有路径被 403 拦截
6. 空标题创建被拒 + 逻辑删除后不再出现

## 6. 19 项记录点核对（按任务 §57）

| # | 项 | 状态与位置 |
| --- | --- | --- |
| 1 | 当前系统已有能力 | ✅ 架构文档 §3 功能矩阵 |
| 2 | 12 Skill 映射 | ✅ 架构文档 §4 |
| 3 | Learning Goal | ✅ 新增 learning_goal + 页面 + 首页横幅 |
| 4 | Learning Path | ✅ 复用（关联即报名，进度回显） |
| 5 | Material | ✅ 复用章节/文档/笔记，映射关系在文档 §6 |
| 6 | Knowledge | ✅ 复用 kg_entity + resource_knowledge_mapping + recalculate |
| 7 | Skill | ✅ 文档 §4 映射；无独立 Sidebar |
| 8 | LearningEvent | ✅ 复用 18 类统一入口，Goal 不伪造事件 |
| 9 | Evidence | ✅ 掌握度引擎信号即证据（前端 evidence 推导仅取真实数据） |
| 10 | Mastery | ✅ 复用，未重建 |
| 11 | Intelligence | ✅ 复用前端纯函数决策层 |
| 12 | Diagnosis | ✅ 7 类真实触发（缺数据不推导） |
| 13 | Strategy | ✅ Diagnosis→Strategy 映射已有 |
| 14 | NextAction | ✅ action-priority 五级优先级 + 循环保护 |
| 15 | App Resolver | ✅ route.ts 白名单 + UserApp enabled 过滤；本阶段未新增动作类型 |
| 16 | 完整数据流 | ✅ 架构文档 §5 |
| 17 | MVP 闭环 | ✅ JVM GC 样例链路见架构文档 §6（1-15 节点真实落点表） |
| 18 | 未实现能力 | 见下节（诚实披露） |
| 19 | 下一阶段 | 见架构文档 §9 |

## 7. 未实现能力（诚实披露，不为凑数造假）

1. **Scenario Lab**：未建 App，只在文档留契约。因此「Diagnosis → SCENARIO_SIMULATION → 打开场景 App」在 NextAction 里**没有真实动作可开**——这是既有缺失，本轮未伪造一个假的场景页来“完成验收”。
2. **Feynman 四维评分**（Accuracy/Clarity/Completeness/Structure）：现有费曼/故事式表达 App 有交互与 AI 反馈，但无“逐维评分证据入库”的确定性闭环；本轮未补造评分器。
3. **NextAction 的 Goal 上下文排序**：activeGoal 未参与 resolveNextAction 的优先级计算（当前为全局 Review>Task>Weak>Path>Explore）。Home 的目标横幅给出了「目标→进度」上文，但“目标关联节点的薄弱点置顶”列入下一阶段。
4. **AI 生成路线需外部 LLM 配置**：与 LearningPaths 同链路，无 AI 配置的环境无法端到端演示 AI 生成；目标页已做失败提示（不伪造成功）。
5. First Principles 无独立 UI，仅占位映射。

## 8. 验证记录（自动修复轮次）

- 后端编译一次通过；新增 Goal 测试首跑 6 用例 1 失败（无路径时 `pathProgress` 为 null 与 VO 约定 0 不符）→ 修复 `toVo` 默认值 → 6/6 绿。
- 前端首轮 `vue-tsc -b && vite build` 直接通过；Vitest 全量通过（无断言改动）。
- HTTP 冒烟：目标创建/关联/完成/让位全链路符合预期（详见 §1）。
- 修复轮数：≤ 2 轮（远低于上限 5）。

## 9. 收尾

- 未 commit / 未 push（按 Git 安全规则，如需里程碑提交请明确指示）。
- 工作区原有未提交改动（UI/UX 3.3.2 相关）保持原样未被触碰。

---

# 4.0-2 增补：NextAction 接入 Goal 上下文（2026-09-03，同日第二轮）

## 背景
4.0-1 诚实标注的下一缺口：「NextAction 仍是全局优先级，activeGoal 未参与决策」。本轮闭环最后一个决策语义：**有进行中目标时，首页/学习中心的「下一步」优先朝目标走**（架构 §26：用户正学 JVM GC → 下一步应是 GC 路线而非随机路径）。

## 改动（纯前端逻辑，未动后端）
| 文件 | 改动 |
| --- | --- |
| `frontend/src/learning/types.ts` | `LearningActionContext` 新增可选 `goal?: LearningGoalVO` 与 `goalPath?: LearningPathVO`（调用方从已加载 paths 匹配，不新增 API） |
| `frontend/src/learning/action-priority.ts` | 新增 `buildGoalPathAction(ctx)`：目标已关联路线且已报名且进度<100（**0% 也接受** → 刚落地路线有起步入口）时返回目标路线 LEARNING_PATH，文案带目标标题；插入 PATH 阶段最前。Review/Task/Weak 健康信号仍在前（优先级不变） |
| `frontend/src/views/Home.vue` | `nextAction` 传 `goal: activeGoal` + `goalPath: goalLinkedPath`（按 pathId 匹配已加载 learningPaths） |
| `frontend/src/views/LearningCenterPage.vue` | 新增 activeGoal/allPaths/goalLinkedPath 接线；loadData 并联拉取 `activeGoal`（失败容错 null） |

**刻意未做**：不改 REVIEW/TASK/WEAK 排序语义；不新增动作类型；Skill/App Registry/UserApp/导航零改动（§48 红线）。

## 验证（全绿）
- Vitest **250/250**（241 → +9 目标上下文用例：0% 起步、40% 优先于其它进行中路径、Review/Weak 仍压过目标、未报名/100%/无路线/无目标回归、显式 skip 兼容）。
- `vue-tsc -b` 0 errors；`vite build` PASS。
- 后端零改动（沿用 77/77）。
- 修复轮数：0（首轮即绿）。

---

# 4.0-3 增补：Goal Workspace「当前节点」（2026-09-03，同日第三轮）

## 背景
架构 §37 的 Learning Workspace 语义还缺一环：目标关联路线后，用户只能看到百分比进度，**看不到「下一步该学哪一节」**（§36 Home 只展示概览，细节应在目标工作区）。补齐方式必须是真实数据——即路径 DAG 的 `available` 节点（前置依赖已满足、当前可学）。

## 改动（纯前端，未动后端/未新增 API）
| 文件 | 改动 |
| --- | --- |
| `frontend/src/learning/path-node.ts`（新增） | 纯函数 `resolveCurrentChapterNode`（按 sortOrder 取第一个 `available` 节点）+ `isPathFullyCompleted` |
| `frontend/src/learning/__tests__/path-node.spec.ts`（新增） | 6 用例：空/排序/无可用/全完成/sortOrder 缺省回退/完成判定 |
| `frontend/src/views/LearningGoals.vue` | 活跃目标 Hero 增加「当前节点」行（加载/节点/全完成/异常 4 态，真实调 `learningApi.dag`）；主 CTA 有节点时直达 `/learning/chapter/:id`（开始本节/继续本节），否则回退路线详情 |

**刻意未做**：不在 Home 增加 dag 请求（保持首页轻量）；不新增任何 Diagnosis/App/Registry 语义。

## 验证（全绿）
- Vitest **256/256**（250 → +6）；`vue-tsc -b` 0 errors；`vite build` PASS；后端零改动（沿用 77/77）；修复轮数 0。

---

# 4.0-4 增补：Scenario Lab 场景练习（2026-09-03，同日第四轮）

## 背景
4.0-3 收尾时架构 §30/§40 的 Practice 节点仍是缺口：quiz/coding 偏客观题，缺**开放式场景练习**——把「学到」推向「会做」。本轮落地 Scenario Lab：给定真实业务场景（JVM Full GC / 慢 SQL / 缓存三大坑 / 秒杀链路），用户闭卷写出排查/设计思路，服务端按要点关键词做**确定性覆盖评分**（Deterministic-first，不调 LLM、不改写掌握度，§40-42 红线守住）。

## 改动清单

### 后端（新增 10 文件，改动 2 文件 + 2 SQL 种子文件）
| 文件 | 改动 |
| --- | --- |
| `common/LearningEventType.java` | 追加 `SCENARIO_COMPLETED`（第 19 类事件，元数据携带 coverage/score/matched/total 等） |
| `entity/LearningScenario.java` / `mapper/LearningScenarioMapper.java`（新增） | `@TableName("learning_scenario")` 内容实体（title/context/question/key_points JSON/difficulty/knowledge_id/status）；mapper 需显式 `@Mapper`（本仓逐接口约定，首轮漏加导致上下文启动失败） |
| `dto/ScenarioKeyPoint.java` / `dto/ScenarioSubmitDTO.java`（新增） | key_points JSON 元素（label+keywords）；提交入参（answer/durationSeconds） |
| `vo/ScenarioSummaryVO` / `ScenarioDetailVO` / `ScenarioSubmitResultVO`（新增） | 列表卡片（含 pointCount）；详情（只下发 label，**keywords 不下发**防提前泄露）；提交结果（coverage/score/命中漏点/确定性点评） |
| `service/LearningScenarioService` + `impl/LearningScenarioServiceImpl`（新增） | 列表/详情/提交；**确定性覆盖评分**：规范化=小写+剔空白标点符号→子串包含，任一关键词命中即覆盖该要点；事件 `record(userId, SCENARIO_COMPLETED, "SCENARIO", scenarioId, metadata)`（REQUIRES_NEW 吞错不反噬主流程） |
| `controller/LearningScenarioController.java`（新增） | GET `/api/learning/scenarios`（knowledgeId/difficulty 过滤）、GET `/{id}`、POST `/{id}/submit`（需登录，内容共享、事件按 userId 隔离） |
| SQL | `learning_scenario` DDL 已前置追加进 h2/mysql schema.sql；本轮补 **h2/data.sql + mysql/data.sql 种子**（4 场景：Full GC/慢 SQL/缓存/秒杀，key_points JSON）+ **`db/mysql/migration/V20260904__learning_scenario.sql`**（存量库补表补数据；种子用 `INSERT…SELECT…WHERE NOT EXISTS` 按标题去重，幂等） |

### 前端（新增 1 文件，改动 5 文件）
| 文件 | 改动 |
| --- | --- |
| `views/ScenarioLab.vue`（新增） | 列表（难度筛选）→ 详情（情景/问题，闭卷作答）→ 确定性评分反馈（覆盖进度 + 命中/漏点双栏 + 分档点评）→ 对照漏点再答；作答计时；全站 `--kb-fs-*`/`--kb-radius-*` 令牌化（过 3.3.2 守护） |
| `api/types.ts` / `api/learning.ts` | 场景类型 4 个 + `learningApi.scenarios/scenarioDetail/submitScenario` |
| `router/index.ts` | 新增 `/learning/scenario`（layout c、requiresAuth、**不进侧边栏**） |
| `views/LearningCenterPage.vue` | 学习中心「更多工具」新增「场景练习」卡片入口（icon `pen-tool`） |
| `views/LearningEvents.vue` | 事件页补 `SCENARIO_COMPLETED` 标签（场景练习）与徽章色，历史可筛可看 |

## 验证（全绿）
- 后端 **82/82**（77 → +5：`LearningScenarioLabClosedLoopTest` 覆盖列表/详情不泄露 keywords、全覆盖 100 分、部分覆盖 25 分、空答/超长 400、不存在/下架 404、事件按用户隔离 1:1）。
- Vitest **256/256**（与 4.0-3 持平——本轮新增页面零逻辑单测，由 typography/icon 全局守护自动纳入扫描）。
- `vue-tsc -b` 0 errors；`vite build` PASS。
- 修复轮数：**2**——① `LearningScenarioMapper` 漏 `@Mapper` → Spring 上下文启动失败；② typography 守护命中新文件非阶梯字号（15/17/22/44px）→ 全量换 `--kb-fs-*` 令牌。另修正 1 处测试断言语义（误把 label 中的工具名当泄漏）。

## 诚实披露（未实现 / 刻意不做）
- **未接入掌握度引擎**：SCENARIO_COMPLETED 只沉淀事件，不重算 knowledge_mastery（证据权重未经评审，不伪造联动）。
- 评分是**关键词覆盖启发式**：同义改写 / 长答案噪音词可能漏判或误命中；对「要点标签 + 高辨识关键词」足够稳定，语义级评分留待 AI 评分器单独立项。
- **无场景内容管理端 CRUD**：内容由 SQL 种子维护（后续补管理接口）。
- 未新增 Diagnosis→SCENARIO 的 NextAction 判定（需先定义 UserApp disabled 时的 fallback，见架构文档 §9）。
- 未 commit / 未 push（沿用本阶段约定，等待整体确认后由你决定）。

---

# 4.0-5 增补：场景练习待补强推荐（2026-09-03，同日第五轮）

## 背景
4.0-4 收尾后 §9 剩余的第一项是「把 SCENARIO 结果接回决策层」。本轮以**真实事件数据**打通补强回路：用户每次场景提交都已落 `SCENARIO_COMPLETED`（metadata 含 coverage/score/matched/total），据此可在学习中心推导「**最近一次未覆盖全部要点的场景**」并给出待补强提示，深链回该场景再答，从而形成 练习 → 评分 → 补强提示 → 再练习 的 Practice 闭环。**刻意不进 action-priority 健康链**（不稀释 Review/Task/Weak 优先级）、不接掌握度引擎（证据权重未评审）。

## 改动（纯前端，后端/SQL 零改动）
| 文件 | 改动 |
| --- | --- |
| `learning/scenario-debt.ts`（新增，纯函数） | `parseScenarioAttempt`（解析事件 metadata，坏 JSON/非 SCENARIO/缺 total 全容错返回 null）；`latestAttemptByScenario`（每个场景取 VO.id 最大=最近一次提交）；`scenarioPracticeDebt`（过滤 matched<total，覆盖度升序 → 最近优先） |
| `learning/__tests__/scenario-debt.spec.ts`（新增） | 7 用例：合法解析、coverage 兜底、五类容错、去重取最新、满分排除、升序排序（同覆盖最近在前）、空/坏数据安静为空 |
| `views/LearningCenterPage.vue` | 「待复习」条带下方新增「**待补强场景**」条带（`ta-chip` 同款）：最多 5 条 +N，点击 `goTo('/learning/scenario?id=N')`；`loadScenarioDebt` 并联进 `Promise.allSettled`（失败静默为空，不阻塞学习中心）；仅在有数据时渲染 |
| `views/ScenarioLab.vue` | 支持深链 `?id=N`（onMounted 读取 route.query.id → `openById`：命中已加载列表直接作答，否则空壳进详情）；详情加载后回填 current 头部字段（标题/难度/要点数） |

## 验证（全绿）
- Vitest **263/263**（23 文件，256 → +7）；`vue-tsc -b` 0 errors；`vite build` PASS。
- 后端零改动（沿用 **82/82**）。
- 修复轮数：0（首轮即绿）。

## 诚实披露（边界）
- 推荐信号 = **最近一次**提交的覆盖度（用户随后高分再答会自然移出列表；同场景重答覆盖更高时不再提示）。
- 「待补强场景」条带是 Practice 补强入口，**不是** action-priority 健康优先级（避免 4.0-2 红线被稀释）；若未来把 SCENARIO 作为掌握度新信号，需先评审证据权重再并入 Weak 诊断。
- 未 commit / 未 push（沿用本阶段约定）。

---

# 4.0-6 增补：场景内容管理端 CRUD（2026-09-03，同日第六轮）

## 背景
4.0-4 诚实披露遗留的运维欠账：「场景内容只能靠 SQL 种子维护」。本轮补齐确定性、可测试、不碰掌握度红线的**后台 ADMIN CRUD**——后端 `/api/admin/learning-scenarios` 五个端点 + 前端 `/admin/scenarios` 管理页（新建 / 覆盖式编辑 / 上下架 / 逻辑删除），keywords 从此在管理后台成为**唯一读入口**（学习端详情依旧只下发 label）。

## 改动清单

### 后端（新增 2 文件，改动 1 文件）
| 文件 | 类型 | 说明 |
| --- | --- | --- |
| `dto/ScenarioAdminDTO.java` / `vo/ScenarioAdminVO.java` | 新增（4.0-6 骨架轮） | 管理入参（title/context/question/keyPoints/difficulty/knowledgeId/status）；管理 VO（含 createTime/updateTime） |
| `service/LearningScenarioService` + `impl/LearningScenarioServiceImpl` | 改动 | 补 5 个 admin 方法：`adminList(keyword,status)`（含下架、排除逻辑删除、id 倒序）、`adminDetail`（含 keywords）、`adminCreate`、`adminUpdate`（覆盖式）、`adminRemove`（@TableLogic 逻辑删除）。校验：title/questionText 必填且长度对齐 schema（200/1000）、keyPoints 非空（≤30 条、label 必填 ≤100、每要点 ≥1 个非空关键词 ≤60）、难度 1-3 默认 1、状态 0/1 默认 1；校验失败 400，目标不存在/已删除 404 |
| `controller/admin/LearningScenarioAdminController.java` | 新增 | 前缀 `/api/admin/learning-scenarios`：GET 列表 / GET `{id}` / POST / PUT `{id}` / DELETE `{id}`；`@PreAuthorize("hasRole('ADMIN')")`（与 SecurityConfig `/api/admin/**` 双保险） |
| `service/__tests__/LearningScenarioAdminClosedLoopTest.java` | 新增 | 5 用例真实 H2：列表含上下架/keyword/status 过滤与删除排除；详情含 keywords、404（不存在/已删）；新建默认值与 JSON 往返；校验 400 族 + 覆盖式更新 + 更新 404；逻辑删除物理行 deleted=1（JdbcTemplate 直查库核对） |

> 无 schema/迁移改动（复用 4.0-4 的 `learning_scenario` 表，零新增表/列）。

### 前端（新增 1 文件，改动 3 文件）
| 文件 | 类型 | 说明 |
| --- | --- | --- |
| `views/admin/ScenarioMgmt.vue` | 新增 | 管理页：搜索（标题/问题）+ 状态筛选 + 列表（难度/要点数/状态/更新时间）+ 上下架一键切换 + 新建/编辑弹窗（要点行编辑器：label + 关键词行，前端按 `[,，、;；\s]+` 拆词去重，服务端再校验）+ 删除（统一 `dialog.confirm` danger 变体二次确认）。全页走 Tailwind 语义类 + `--kb-*` 令牌，过 3.3.2 令牌守护（零新增违规文件） |
| `api/admin.ts` | 改动 | 本地类型 `ScenarioPointRow / ScenarioAdminVO / ScenarioAdminInput` + `adminApi.scenarios/scenarioDetail/createScenario/updateScenario/removeScenario` |
| `router/index.ts` | 改动 | +`/admin/scenarios`（layout b、requiresAuth、requiresAdmin） |
| `components/layout/Sidebar.vue` | 改动 | 「内容管理」组 +「场景练习管理」（icon `target`，adminOnly） |

## 验证（全绿）
- 后端 **87/87**（82 → +5 admin 用例，全量 Maven test BUILD SUCCESS）。
- Vitest **263/263**（23 文件，与 4.0-5 持平——新增页面为管理 UI，逻辑由后端集成测试 + 全站守护覆盖）。
- `vue-tsc -b` 0 errors；`vite build` PASS（typography/icon 守护无回退）。
- HTTP 冒烟（18080 独立实例，测毕即释放）：admin 登录 → GET 列表（4 条种子，keywords 可见）→ POST 新建 id=5 → PUT 覆盖式更新（difficulty 3 / status 0 生效）→ GET 详情回读一致 → DELETE → 详情 **404**；user1（非 ADMIN）GET 列表 **403**；匿名 **401**。
- 修复轮数：0（首轮即绿；测试仅 1 处收尾清理：把「逻辑删除」物理行核对从 MP 包装 selectById 改为 JdbcTemplate 直查 `deleted=1`，断言更诚实）。

## 诚实披露（未实现 / 刻意不做）
- 管理页**未做知识点选择器**（knowledgeId 恒为空可存；种子同为空，暂不影响）；关联 `kg_entity` 的绑定 UI 留待后续。
- `adminList` 非分页（场景量级小，返回全量含下架；量大时分页留待需要时补）。
- 角色门禁的 HTTP 层 403/401 已冒烟验证；Service 层集成测试直测方法（与 4.0-4 同口径），未走 MockMvc。
- 未 commit / 未 push（沿用本阶段约定）。

---

# 4.0-7 增补：费曼表达四维真实评分器（2026-09-03，同日第七轮）

## 背景
架构 §38/§152 的 Expression 节点长期停留在「AI 写作反馈交互」，缺「逐维评分证据入库」的确定性闭环。本轮落地 **Feynman 四维评分器**：作者把刚学的主题写成「讲给外行听」的解释 → 按 Accuracy/Clarity/Completeness/Structure 四维各 4 条**可观察自检准则**逐条核验勾选 → 服务端**确定性计分**（勾选数/4，无 AI）→ `FEYNMAN_EXPRESSED` 事件入库。诚实边界：Accuracy 无机器可验证的参照系，以「作者对照资料自检」为据（rubric 文案与页面均明示，不伪装语义评分）。

## 改动清单

### 后端（新增 10 文件，改动 1 文件；零 schema/SQL 改动）
| 文件 | 类型 | 说明 |
| --- | --- | --- |
| `common/LearningEventType.java` | 改动 | +`FEYNMAN_EXPRESSED`（第 21 类，注释声明只记录事件不直写掌握度） |
| `dto/FeynmanSubmitDTO.java` | 新增 | topic(≤200)/explanation(1~10000)/checkedCriterionIds(白名单过滤)/durationSeconds |
| `vo/FeynmanCriterionVO` `FeynmanDimensionVO` `FeynmanRubricVO` | 新增 | rubric 下发模型（维度 key/中文名/准则文本/计分口径说明） |
| `vo/FeynmanDimensionResultVO` `FeynmanSubmitResultVO` | 新增 | 单维（checked/total/score）+ 提交结果（overall/comment/recorded） |
| `service/LearningFeynmanService` + `impl/LearningFeynmanServiceImpl` | 新增 | rubric 常量（服务端唯一真相源：四维 × 4 条）；submit：非空/长度 400 → **Set 去重勾选** + 白名单过滤（未知 id 忽略、重复只计一次）→ 维度分 round(勾选/4×100)、总分 round(勾选/16×100) → 分档点评 → `record(userId, FEYNMAN_EXPRESSED, "FEYNMAN", 0L, meta)`（meta 含 topic/overall/四维 score 与 checked） |
| `controller/LearningFeynmanController.java` | 新增 | GET `/api/learning/feynman/rubric`、POST `/api/learning/feynman/submit`（需登录；事件按 userId 隔离） |
| `service/__tests__/LearningFeynmanScorerClosedLoopTest.java` | 新增 | 6 用例真实 H2：rubric 形状（4×4 稳定 id）、全勾 100、部分勾折算（25/38）、空/超长 400、未知 id 忽略 + 重复去重、事件按用户隔离（meta 四维分断言） |

> 无新表/无迁移：表达记录只沉淀事件（无内容实体 → resourceType=FEYNMAN、resourceId=0，按 user+type+时间查询）。

### 前端（新增 1 文件，改动 4 文件）
| 文件 | 类型 | 说明 |
| --- | --- | --- |
| `views/FeynmanLab.vue` | 新增 | 编写页：主题 + 解释正文（计数）+ rubric 四维勾选卡（实时 已勾选/4，`role=checkbox` 可访问）+ 提交确定性评分 → 结果面板（总分分档色 + 四维进度条 + 确定性点评 + **未勾选准则补强清单** + 再写一遍）。全站 `--kb-fs-*`/`--kb-radius-*` + 语义色令牌（过 3.3.2 守护，零新增违规） |
| `api/types.ts` / `api/learning.ts` | 改动 | Feynman 类型 7 个 + `learningApi.feynmanRubric/submitFeynman` |
| `router/index.ts` | 改动 | +`/learning/feynman`（layout c、requiresAuth、**不进侧边栏**） |
| `views/LearningCenterPage.vue` | 改动 | 「更多工具」+「费曼表达」（icon `book-open`，非 App 无需启用态过滤） |
| `views/LearningEvents.vue` | 改动 | 事件类型补 `FEYNMAN_EXPRESSED`（费曼表达）+ 徽章色 `#EC4899`，历史可筛可看 |

## 验证（全绿）
- 后端 **93/93**（87 → +6 Feynman 用例，全量 Maven test BUILD SUCCESS）。
- Vitest **263/263**（23 文件，持平——新页为编写/勾选 UI，评分确定性由后端 6 集成用例 + HTTP 冒烟覆盖）。
- `vue-tsc -b` 0 errors；`vite build` PASS（typography/icon 守护无回退）。
- HTTP 冒烟（18080 独立实例，测毕已释放）：user1 登录 → GET rubric（4 维 × 4 条 + 口径说明）→ 全勾 16 条 submit `overall 100` 且四维 100 / recorded true → 仅勾清晰度 4 条 `overall 25` → 空正文 **400** → 事件列表按 `FEYNMAN_EXPRESSED` 筛出 2 条（meta 含 `"clarityChecked":4` 等四维分，正确）。
- 修复轮数：**2**——① 测试构造 `List.of(..., null)` 抛 NPE（List.of 禁 null，移除 null 用例元素）；② 重复勾选语义修正：逐条累加会把「同一条勾两次」虚增，改为 **Set 去重同一准则只计一次**（对用户更诚实）后测试转绿。

## 诚实披露（未实现 / 刻意不做）
- **Accuracy 维度为作者自检**：系统无参照无法验证内容正确性，未伪装成机器语义判分；「清晰度/结构」同样以作者通读/朗读核验为准。此为评分器最大诚实边界，页面 rubric 已明示。
- rubric 准则内容为**服务端常量人工维护**（非 LLM 生成）；16 条准则文案可作为后续评审/增补项。
- 不接掌握度引擎、不接 Weak 诊断（与 Scenario 同一红线：证据权重未评审）；resourceId=0 的约定需在掌握度引擎接入时重新评估。
- 未 commit / 未 push（沿用本阶段约定）。
