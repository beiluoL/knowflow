# KnowFlow Architecture 4.1

# Evidence & Learning Strategy Engine

> 从「记录用户做了什么」升级为「知道用户哪里强、哪里弱、为什么弱，以及下一步该用什么方法学」。

- 版本：Architecture 4.1
- 状态：已实现（MVP 已跑通）
- 前置：UI/UX 3.0–3.3、Architecture 4.0（Learning Loop / Goal / Path / Scenario / Feynman）
- 代码位置：`frontend/src/learning/evidence/`、`backend/.../LearningFirstPrinciples*`

---

## 1. Event vs Evidence

这是本阶段最核心的区分，也是整个引擎的地基。

| | LearningEvent | Evidence（BehaviorEvidence） |
|---|---|---|
| 回答什么 | **发生了什么** | **这件事说明用户掌握情况如何** |
| 性质 | 事实，后端落库，不可变 | 解释，由 Resolver 确定性推导，**可重算** |
| 例子 | `FEYNMAN_EXPRESSED` | `Explanation Quality = 52` |
| 例子 | `SCENARIO_COMPLETED` | `Application Coverage = 75` |
| 删除后果 | 真相丢失 | 可用事件重算，不产生任何影响 |

设计铁律：**Evidence 永远是 Event 的派生结果**。它不写库、不新增表，只是一个纯函数层的解释。

```text
LearningEvent: FEYNMAN_EXPRESSED  { overall: 56, accuracy: 75, clarity: 25, ... }
        ↓ resolveFeynmanEvidence()
Evidence:      EXPLANATION 56 分（置信度 0.6，样本 1）
```

### 为什么新开 `BehaviorEvidence`，而不是复用既有的 `LearningEvidence`

`intelligence/types.ts` 里已有一个 `LearningEvidence`（`WEAK_MASTERY` / `REVIEW_DUE` / `QUIZ_ACCURACY`…）。它回答的是**「凭什么这么诊断」**——Diagnosis 的支撑材料，没有维度、没有分数。

本阶段的 Evidence 回答的是**「这次行为说明某项能力有多强」**——必须有 `dimension` + `score` + `confidence`，是 Mastery / Strategy 的输入。

两者语义不同、生命周期不同。强行合并会让「证据」一词同时承担两种含义，违反「不制造同义重复枚举」的原则。因此本阶段新开 `BehaviorEvidence`，并在文档中明确二者的分工。

---

## 2. Evidence Dimensions

统一能力维度（`frontend/src/learning/evidence/types.ts`）：

| 维度 | 含义 | 典型来源 |
|---|---|---|
| `RECALL` | 记忆 / 主动回忆 | 答题、闪卡（SM-2）、主动回忆会话 |
| `UNDERSTANDING` | 理解 / 底层机制 | 第一性原理推导 |
| `EXPLANATION` | 讲解 / 费曼表达 | 费曼四维评分 |
| `APPLICATION` | 应用 / 场景与编程 | 场景练习、代码提交 |
| `EXPRESSION` | 书面表达 | **未实现**（见 §16 诚实边界） |
| `CONSISTENCY` | 一致性 / 稳定复现 | 由聚合层从证据时间分布推导，非单事件产出 |

---

## 3. Evidence Resolver

`evidence/resolver.ts`。纯函数、无副作用、不调 API。同一事件算一万次结果完全一致。

统一入口 `resolveEvidence(event)` 按事件类型分派到各来源 Resolver：

| 事件类型 | Resolver | 换算口径（全部复用后端已写入的真实数字） | 维度 | 置信度 |
|---|---|---|---|---|
| `FEYNMAN_EXPRESSED` | `resolveFeynmanEvidence` | 复用 `overall`（缺失时用四维均值兜底） | EXPLANATION | 0.6 |
| `SCENARIO_COMPLETED` | `resolveScenarioEvidence` | 复用 `score`（缺失时 `matched/total`） | APPLICATION | 0.9 |
| `CODE_SUBMITTED` | `resolveCodingEvidence` | `passCount / total` | APPLICATION | 0.9 |
| `QUESTION_ANSWERED` | `resolveQuizEvidence` | `correct ? 100 : 0`（缺失时用 `score`） | RECALL | 0.9 |
| `FLASHCARD_REVIEWED` | `resolveFlashcardEvidence` | `quality / 5 × 100`，`lapsed` 时封顶 40 | RECALL | 0.4 |
| `RECALL_COMPLETED` | `resolveRecallEvidence` | 复用 `score` | RECALL | 0.9 |
| `FIRST_PRINCIPLES_DERIVED` | `resolveFirstPrinciplesEvidence` | 复用 `completeness` | UNDERSTANDING | 0.5 |

置信度口径（确定性，非模型）：

- `0.9` 机器可验证（测试通过率、对错、要点覆盖计数）
- `0.6` 作者自检（费曼四维：无机器参照系，如实降权）
- `0.5` 结构完整度（第一性原理：只能证明填了，不能证明对）
- `0.4` 单次主观自评（SM-2 quality：样本极薄）

**容错原则**：不支持的事件类型、非法 JSON、脏数据（`total <= 0`）一律返回 `null`，绝不抛错、绝不猜数。

---

## 4. Evidence Aggregation

`evidence/aggregate.ts`。输入一段时间窗（默认 30 天）内的真实 Evidence，输出六维得分。

三条硬规则：

### 4.1 禁止单次满分

样本上限 `sampleCap(n) = 50 + 50 × min(1, (n-1)/3)`：

| 样本数 n | 1 | 2 | 3 | ≥4 |
|---|---|---|---|---|
| 聚合分上限 | 50 | 67 | 83 | 100 |

这条规则明确切断「一次 100 分 → 掌握度直接 100」的污染链。

### 4.2 时间衰减

半衰期 7 天：`weight = 0.5 ^ (ageDays / 7)`。越近的证据权重越高。

### 4.3 置信度分离

分数与置信度分开输出。样本少时分数不低但置信度低，Diagnosis 层据此「少样本不下重诊断」，而不是假装数据很充分。

`confidence = sampleConfidence(n) × 该维度证据的平均置信度`，其中 `sampleConfidence(n) = min(1, n/3)`。

### 4.4 CONSISTENCY 的推导

没有任何单个事件能直接证明「一致性」，它由分布推导：

```text
dayPart   = min(1, activeDays / 3)          // 节奏连续性
stability = 1 - min(1, stdev(所有证据分数) / 40)
CONSISTENCY = round(100 × dayPart × (0.5 + 0.5 × stability))
```

**这不是能力分**，它只表示「近期是否连续、稳定地在练」。UI 与文档均如实标注。

---

## 5. Feynman Evidence

复用事件 metadata 中**已由后端算好**的 `overall`（四维勾选 / 16 × 100），**不重新设计评分器、不重新评分**。

```text
FEYNMAN_EXPRESSED { overall: 56, accuracy: 75, clarity: 25, completeness: 75, structure: 50 }
  ↓
EXPLANATION Evidence { score: 56, confidence: 0.6 }
```

诚实边界：Accuracy 无机器可验证参照系，以作者对照资料自检为据，因此置信度只有 0.6。

---

## 6. Scenario Evidence

复用 `SCENARIO_COMPLETED` 事件 metadata 中已算好的 `coverage / score / matched / total`。

```text
SCENARIO_COMPLETED { score: 75, matched: 3, total: 4 }
  ↓
APPLICATION Evidence { score: 75, confidence: 0.9 }
```

要点覆盖是确定性关键词匹配计数，机器可验证，置信度 0.9。

---

## 7. First Principles

### 7.1 定位

路由 `/learning/first-principles`，视图 `FirstPrinciplesLab.vue`。
入口：学习中心 → 更多工具 → 第一性原理。**不进侧边栏**（与 Scenario Lab / Feynman Lab 一致）。

### 7.2 六个环节（用户必须自己填写）

1. 已知事实 facts
2. 假设 assumptions
3. 约束 constraints
4. 底层机制 mechanism
5. 因果关系 causality
6. 重新推导 derivation

系统**不提供任何答案**，只提供提示与占位示例。

### 7.3 后端

新增事件类型 `FIRST_PRINCIPLES_DERIVED` + 端点：

- `GET  /api/learning/first-principles/rubric` —— 六环节定义（服务端唯一真相源）
- `POST /api/learning/first-principles/submit` —— 结构完整度评估 + 事件落库

**不新增任何表**，复用既有 `learning_event`。这是「只有真实缺失才新增 API」原则下的最小改动：没有这个事件类型，第一性原理就无法进入事件流，也就无法产生 Evidence，§30 的数据流会断在第一步。

---

## 8. Diagnosis（能力诊断）

`evidence/diagnosis.ts`。由**真实 Evidence 聚合结果**推导。

| 诊断类型 | 维度 | 优先级 |
|---|---|---|
| `LOW_RECALL` | RECALL | 90 |
| `LOW_APPLICATION` | APPLICATION | 85 |
| `LOW_UNDERSTANDING` | UNDERSTANDING | 80 |
| `LOW_EXPLANATION` | EXPLANATION | 75 |
| `LOW_EXPRESSION` | EXPRESSION | 60 |
| `LOW_CONSISTENCY` | CONSISTENCY | 40 |

阈值：`< 60` 判弱，`< 40` 判严重（HIGH）。

**铁律：没有 Evidence 就没有诊断。**

- 某维度 `score === null`（无样本）→ 绝不输出该维度诊断，不用 0 分假装「很差」
- 完全无 Evidence → 返回 `[]`，不返回「状态良好」占位

严重度：`< 40` HIGH，`40–59` MEDIUM，`LOW_CONSISTENCY` 恒为 LOW（只作温和提示）。

### 与既有 `intelligence/diagnosis.ts` 的分工

| | 既有 Diagnosis | 本阶段 BehaviorDiagnosis |
|---|---|---|
| 回答 | **学习状态**哪里不对 | **哪项能力**弱 |
| 输入 | 复习队列 / 薄弱知识点 / 任务 / 路径进度 | Evidence 聚合结果 |
| 类型 | REVIEW_OVERDUE / WEAK_KNOWLEDGE / LOW_ACCURACY… | LOW_RECALL / LOW_EXPLANATION / LOW_APPLICATION… |

既有 `DiagnosisType` 的 `switch` 是穷举的，往里塞能力维度会破坏既有 LearningStrategy 映射，因此新开枚举。

---

## 9. Root Cause

`resolveRootCause(type, agg)` —— 全部为确定性规则（真实分数比较），**不涉及任何 AI 判断**。

| 诊断 | 条件 | 根因文案 |
|---|---|---|
| LOW_RECALL | CONSISTENCY < 60 | 练习间隔过长、证据太零散，记忆还没形成稳定复现 |
| LOW_RECALL | UNDERSTANDING 或 EXPLANATION ≥ 60 | 理解和讲解都还行，但缺少主动回忆练习，记住的东西容易忘 |
| LOW_RECALL | 其他 | 最近缺乏主动回忆练习，记忆缺少复现机会 |
| LOW_UNDERSTANDING | RECALL ≥ 60 | 记得住结论，但缺少对底层机制的拆解练习 |
| LOW_UNDERSTANDING | 其他 | 对底层机制的拆解不足，理解停留在结论层面 |
| LOW_EXPLANATION | UNDERSTANDING ≥ 60 | 表达结构与核心知识之间存在断层：想得清楚，但讲不清楚 |
| LOW_EXPLANATION | 其他 | 理解还不够扎实，讲不出来往往是因为还没真正吃透 |
| LOW_APPLICATION | UNDERSTANDING 或 EXPLANATION ≥ 60 | 理论表现稳定，但场景练习不足，知识还没转化成能用的东西 |
| LOW_APPLICATION | 其他 | 基础理解还不牢，先补齐理解，再谈应用 |
| LOW_EXPRESSION | — | 书面表达缺少训练 |
| LOW_CONSISTENCY | — | 练习节奏不稳定 |

每条文案都能追溯到「哪个分数低 + 另一个分数如何」，不含模糊断言、不出现「AI 认为…」。

---

## 10. Strategy Resolver

`evidence/strategy.ts`。**能力维度 → 学习策略**，不是 Diagnosis → App 一对一硬绑。

| 诊断 | 策略 |
|---|---|
| `LOW_RECALL` | `ACTIVE_RECALL`（主动回忆） |
| `LOW_UNDERSTANDING` | `FIRST_PRINCIPLES`（回到基本原理重新推导） |
| `LOW_EXPLANATION` | `FEYNMAN`（讲出来） |
| `LOW_APPLICATION` | `APPLICATION`（场景 / 编程） |
| `LOW_EXPRESSION` | `WRITING_REVIEW`（书面表达） |
| `LOW_CONSISTENCY` | `SPACED_REVIEW`（把节奏稳定下来） |

---

## 11. App Availability

§23 一个 Strategy 可以落到多个 App：

| 策略 | 候选目标（按优先级） |
|---|---|
| ACTIVE_RECALL | `/learning/flashcards`（核心页）→ App `flashcard` → App `quiz` |
| FIRST_PRINCIPLES | `/learning/first-principles`（核心页） |
| FEYNMAN | `/learning/feynman`（核心页）→ App `feynman` |
| APPLICATION | `/learning/scenario`（核心页）→ App `coding` |
| WRITING_REVIEW | `/learning/writing`（核心页）→ App `ai-writing` |
| SPACED_REVIEW | `/learning/review`（核心页）→ App `flashcard` |

§24 解析后必须再过一遍 UserApp / App Registry 可用性：

- `ROUTE`：核心页面，不依赖 App 启用状态，永远可用
- `APP`：必须在已安装 App 列表中，且能通过 Registry 白名单校验
- **全部不可用 → fallback 到 `/learning/center`**，绝不允许 NextAction 指向被停用的 App

测试守卫：对全部 7 个策略，在「零 App 安装」下断言候选里不出现任何 App 型目标。

---

## 12. Goal Context

§26 候选动作尽可能绑定 `goalId` / `learningNodeId` / `knowledgeId`，让这条链成立：

```text
JVM GC 目标 → GC 节点 → 应用能力弱 → 场景练习 → GC 场景
```

而不是随机练另一个知识点。

**绑定口径（诚实）**：`knowledgeId` 只在真实来源中出现，绝不猜测：

1. Evidence 自身携带（事件 metadata 真实写入 `knowledgeId`）
2. 调用方传入的 Goal / Node 上下文（真实字段）
3. 掌握度列表中按 id 反查的真实名称

三者都取不到时 `knowledgeId` 为 `null`，动作仍然可用，只是不带知识点上下文。**当前限制**：后端 `LearningGoalVO` 尚无 `knowledgeId` 字段，因此 Goal 上下文目前只绑定目标标题与路线，知识点级绑定留待后续阶段。

---

## 13. NextAction

§25 **不重写 NextAction**。

Evidence 候选以 `ctx.evidence` 形式注入既有 `resolveNextAction`，插入位置为 **TASK 之后、WEAK 之前**（步骤 3.5）：

```text
1  REVIEW（待复习）
2  TASK（今日任务）
3.5 EVIDENCE（能力补强）      ← 4.1 新增
4  WEAK（薄弱知识）
4.5 GoalPath（目标路线）
5.5 PATH（进行中路径）
6  EXPLORE（兜底）
```

理由：Evidence 比「掌握度偏低」这种泛化信号更具体——它知道弱在记忆 / 理解 / 讲解 / 应用哪一项。

**§36 回归保证**：`ctx.evidence` 为空时，既有优先级链**完全不变**（有测试守护）。

新增动作类型 `EVIDENCE_PRACTICE`，其路由必须落在本地静态白名单 `EVIDENCE_PRACTICE_ROUTES` 内，否则回退学习中心（沿用 §82-84 的路由白名单原则，禁止任意 URL / `javascript:`）。

---

## 14. Data Flow

```text
User Action
  ↓
App（费曼 / 场景 / 第一性原理 / 闪卡 / 答题 / 编程）
  ↓
LearningEvent（后端落库，元数据携带真实评分）
  ↓
Evidence Resolver      ← frontend/src/learning/evidence/resolver.ts
  ↓
Evidence（dimension + score + confidence）
  ↓
Evidence Aggregation   ← aggregate.ts（多次证据 + 时间衰减 + 样本上限）
  ↓
Diagnosis + Root Cause ← diagnosis.ts（无证据 → 无诊断）
  ↓
Strategy Resolver      ← strategy.ts
  ↓
Action Candidate       ← action-candidate.ts（+ App Availability + Goal Context）
  ↓
既有 NextAction Resolver ← action-priority.ts（统一排序，不重写）
  ↓
Real App
  ↓
New LearningEvent  ⟲
```

---

## 15. MVP：JVM GC

端到端测试见 `frontend/src/learning/evidence/__tests__/mvp.spec.ts`。

| 步骤 | 用户行为 | Evidence | Diagnosis | 下一步 |
|---|---|---|---|---|
| A | 无 | — | — | `LEARNING_PATH`（目标路线，既有行为） |
| B | 费曼提交 56 分<br>（accuracy 75 / clarity 25 / completeness 75 / structure 50） | EXPLANATION 50<br>（单样本封顶） | LOW_EXPLANATION | `/learning/feynman` |
| C | 再提交场景 75 分 | APPLICATION 50 | LOW_APPLICATION<br>（优先级 85 > 75） | `/learning/scenario` |
| D | 场景连做 3 次满分 | APPLICATION 94 | LOW_APPLICATION **消失** | `/learning/feynman` |

结论：**用户的一次真实学习行为，会改变系统给出的下一步学习策略。**

---

## 16. Limitations（诚实边界）

1. **EXPRESSION 维度未实现**。SmartWriting 当前没有可靠的确定性评分（后端无对应评分服务），按 §13/§33「没有可靠评分就不产出 Evidence」保持未实现。未来若接入，也必须只能作为辅助，不得直接改写 Mastery。

2. **Evidence 不改写 Mastery**。本阶段 Evidence Engine 独立运行（§19）。只有确认 Evidence 质量与数据口径稳定后，才设计 Mastery Engine 接入，避免「错误 Evidence → 污染 Mastery → 错误 Diagnosis → 错误 NextAction」。

3. **第一性原理只评结构，不评对错**。系统没有可靠的语义参照系，`completeness` 只证明「你把推导过程写全了」。UI 与 rubric 文案中均如实披露。

4. **Goal → knowledgeId 链路未打通**。后端 `LearningGoalVO` 没有 `knowledgeId` 字段，因此 §26 的知识点级绑定目前只在 Evidence metadata 或调用方显式传参时生效，尚不能做到「GC 目标 → 自动定位 GC 场景」。

5. **CONSISTENCY 不是能力分**。它衡量练习节奏的连续性与分数稳定性，不应解读为「能力一致性」。

6. **AI 边界**（§34）。本阶段默认 Deterministic-first。AI 可以解释、总结、辅助生成、辅助建议，但**不得直接修改** Mastery / Diagnosis / UserApp / Review。Core 负责最终状态。

7. **未做**（§40 停止条件）：AI Coach、Agent、MCP、Tool Calling、RAG、复杂 LLM Orchestration。
