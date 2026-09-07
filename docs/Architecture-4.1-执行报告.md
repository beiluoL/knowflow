# Architecture 4.1 执行报告

# Evidence & Learning Strategy Engine

> 执行日期：2026-09-07
> 结论：**已完成**，四项门禁全部通过，JVM GC MVP 端到端跑通。

---

## 一、测试门禁（§37）

| 门禁 | 开始前基线 | 完成后 | 结论 |
|---|---|---|---|
| Vitest | 23 文件 / **263 passed** | 29 文件 / **343 passed** | ✅ 全通过，0 失败 |
| TypeScript（vue-tsc -b） | 0 errors | **0 errors** | ✅ |
| Vite Build | PASS | **PASS** | ✅ |
| Backend Maven Test | 15 文件 / **93 passed** | 16 文件 / **100 passed** | ✅ 全通过 |

**未删除任何旧测试，未修改任何既有断言。** 新增测试共 87 条（前端 80 + 后端 7）。

> 环境提示：本机 `vitest` 默认 forks pool 会出现随机 worker 启动超时（丢文件，非测试失败）。
> 最终门禁以 `npx vitest run --maxWorkers=1` 完整跑通为准：**29 文件 / 343 passed / 0 failed**。

### 本地冒烟（真实 HTTP）

| 验证项 | 结果 |
|---|---|
| 匿名访问 `GET /api/learning/first-principles/rubric` | `401`（端点存在且需鉴权） |
| 登录后获取 rubric | `200`，六环节 key/顺序/minLength 与前端常量一致 |
| 提交 4/6 环节 | `200`，`completeness=67, filled=4, missing=[causality, derivation], recorded=true` |
| 事件落库 `GET /api/learning/events?eventType=FIRST_PRINCIPLES_DERIVED` | `200`，`resourceType=FIRST_PRINCIPLES, resourceId=0`，metadata 含 `{"completeness":67,"filled":4,"total":6,...}` —— 与 `resolveFirstPrinciplesEvidence` 读取口径完全一致 |
| 前端 dev（:5173）/ 后端（:8080） | 均 200 / 401（正常） |
| 构建产物 | `dist/assets/FirstPrinciplesLab-*.js` 已产出，路由已注册 |

### 新增测试明细

| 文件 | 测试数 | 覆盖点 |
|---|---|---|
| `evidence/__tests__/resolver.spec.ts` | 17 | 各来源 Evidence 换算、脏数据容错、evidenceId 稳定性、knowledgeId 不猜测 |
| `evidence/__tests__/aggregate.spec.ts` | 14 | 样本上限（禁单次满分）、时间衰减半衰期、置信度、CONSISTENCY 推导 |
| `evidence/__tests__/diagnosis.spec.ts` | 18 | 无 Evidence → 无诊断、阈值、严重度、优先级、根因确定性 |
| `evidence/__tests__/strategy.spec.ts` | 16 | Diagnosis→Strategy、一策略多 App、App 可用性过滤、fallback、Goal 上下文 |
| `evidence/__tests__/first-principles.spec.ts` | 9 | 结构完整度、只判结构不判对错、校验、metadata 口径 |
| `evidence/__tests__/mvp.spec.ts` | 6 | **JVM GC 四步端到端** + §36 回归守护 |
| `LearningFirstPrinciplesClosedLoopTest.java` | 7 | rubric 下发、完整度、400 校验、事件按用户隔离 |

---

## 二、代码改动清单

### 前端 · 新增（`frontend/src/learning/evidence/`）

| 文件 | 职责 |
|---|---|
| `types.ts` | `EvidenceDimension` 六维、`BehaviorEvidence`、metadata 解析工具、分数封顶 |
| `resolver.ts` | `resolveEvidence` + 7 个来源 Resolver（Feynman/Scenario/Coding/Quiz/Flashcard/Recall/FirstPrinciples） |
| `aggregate.ts` | `aggregateEvidence`（时间衰减 + 样本上限 + 置信度）、`deriveConsistency` |
| `first-principles.ts` | 六环节常量、结构完整度评估、提交校验、metadata 序列化 |
| `diagnosis.ts` | `diagnoseFromEvidence`、`resolveRootCause`、`strongestAndWeakest` |
| `strategy.ts` | `resolveEvidenceStrategy`、`STRATEGY_TARGETS`、`availableTargets`（含 fallback） |
| `action-candidate.ts` | `buildStrategyCandidate` / `buildTopCandidate`（Goal 上下文绑定） |
| `index.ts` | 模块出口 |

### 前端 · 新增视图

| 文件 | 说明 |
|---|---|
| `views/FirstPrinciplesLab.vue` | 第一性原理推导页（主题 + 六环节，用户自填，系统不给答案） |

### 前端 · 修改

| 文件 | 改动 |
|---|---|
| `learning/types.ts` | 新增动作类型 `EVIDENCE_PRACTICE`；`LearningActionContext.evidence`（可选）；`NextLearningAction.behaviorDiagnosis` |
| `learning/route.ts` | `ACTION_ROUTES` 补 `EVIDENCE_PRACTICE`；新增 `EVIDENCE_PRACTICE_ROUTES` 本地白名单；`actionToCta` 补分支 |
| `learning/action-priority.ts` | 插入步骤 3.5 EVIDENCE 分支（`ctx.evidence` 为空时行为完全不变） |
| `learning/index.ts` | 导出 evidence 模块 |
| `api/types.ts` | 新增 `FirstPrinciplesStepVO` / `RubricVO` / `SubmitInput` / `SubmitResultVO` |
| `api/learning.ts` | 新增 `firstPrinciplesRubric` / `submitFirstPrinciples` |
| `router/index.ts` | 新增 `/learning/first-principles` 路由 |
| `views/LearningCenterPage.vue` | 接入 Evidence 流水线；「更多工具 → 第一性原理」入口；§28 轻量状态卡 |
| `views/LearningEvents.vue` | 事件类型标签补 `FIRST_PRINCIPLES_DERIVED` |

### 后端 · 新增

| 文件 | 说明 |
|---|---|
| `dto/FirstPrinciplesSubmitDTO.java` | 六环节提交入参 |
| `vo/FirstPrinciplesStepVO.java` | 环节定义 |
| `vo/FirstPrinciplesRubricVO.java` | rubric + 计分口径 |
| `vo/FirstPrinciplesSubmitResultVO.java` | 提交结果 |
| `service/LearningFirstPrinciplesService.java` | 服务接口 |
| `service/impl/LearningFirstPrinciplesServiceImpl.java` | 结构完整度评估 + 事件落库 |
| `controller/LearningFirstPrinciplesController.java` | `GET /rubric`、`POST /submit` |
| `LearningFirstPrinciplesClosedLoopTest.java` | 7 条集成测试 |

### 后端 · 修改

| 文件 | 改动 |
|---|---|
| `common/LearningEventType.java` | 新增 `FIRST_PRINCIPLES_DERIVED` |

**零新增表、零新增列、零迁移脚本**（§35）。新端点复用既有 `learning_event` 表。

---

## 三、MVP 验证结果（§31）

`evidence/__tests__/mvp.spec.ts` 四步断言全部通过：

| 步骤 | 用户行为 | Evidence | Diagnosis | NextAction 实际输出 |
|---|---|---|---|---|
| A | 无 | — | — | `LEARNING_PATH` → `/learning/path/101`（既有行为） |
| B | 费曼 56 分 | EXPLANATION **50**（单样本封顶） | `LOW_EXPLANATION` | `EVIDENCE_PRACTICE` → `/learning/feynman` |
| C | + 场景 75 分 | APPLICATION 50 | `LOW_APPLICATION`（优先级 85 > 75） | `EVIDENCE_PRACTICE` → `/learning/scenario` |
| D | + 场景 3 次满分 | APPLICATION **94** | `LOW_APPLICATION` **消失** | `EVIDENCE_PRACTICE` → `/learning/feynman` |

**结论：用户的一次真实学习行为，确实改变了系统给出的下一步学习策略。**

同时守护 §36：`ctx.evidence` 为 `undefined` 或 `null` 时，`resolveNextAction` 返回值与 4.1 之前**完全相等**（回归断言：`expect(withNull).toEqual(withoutEvidence)`）。

---

## 四、关键设计决策与理由

### 4.1 为什么新开 `BehaviorEvidence` 而不复用既有 `LearningEvidence`

既有 `LearningEvidence`（`WEAK_MASTERY` / `REVIEW_DUE` / `QUIZ_ACCURACY`…）回答「凭什么这么诊断」，是 Diagnosis 的支撑材料，**没有维度、没有分数**。本阶段需要的是「这次行为说明某项能力有多强」，必须有 `dimension + score + confidence`。语义不同、生命周期不同，合并会让「证据」一词同时承担两种含义。

### 4.2 为什么新开 `BehaviorDiagnosisType` 而不扩展既有 `DiagnosisType`

既有 `intelligence/strategy.ts` 的 `switch (d.type)` 是**穷举**的。往 `DiagnosisType` 里塞能力维度会破坏既有 LearningStrategy 映射，违反 §36。

### 4.3 为什么 Evidence 分支插在 TASK 之后、WEAK 之前

Evidence 比「掌握度偏低」这种泛化信号更具体——它知道弱在记忆 / 理解 / 讲解 / 应用哪一项。放在 WEAK 之前，才能体现「知道为什么弱」的价值；放在 REVIEW / TASK 之后，保证复习与今日计划这些硬日程不被挤掉。

### 4.4 为什么 First Principles 必须新增后端端点

§35 要求「只有真实缺失才新增 API」。这里确实缺失：没有 `FIRST_PRINCIPLES_DERIVED` 事件类型，第一性原理就无法进入事件流，也就无法产生 Evidence，§30 的数据流会断在第一步。因此新增 1 个事件类型 + 2 个端点，是「不伪造」前提下的最小改动。

---

## 五、诚实边界（§33/§34）

1. **EXPRESSION 维度未实现**。扫描确认 SmartWriting 无后端确定性评分服务，按「没有可靠评分就不产出 Evidence」保持未实现，不伪造。

2. **Evidence 不改写 Mastery**（§19）。本阶段 Evidence Engine 独立运行，避免「错误 Evidence → 污染 Mastery → 错误 Diagnosis → 错误 NextAction」。

3. **第一性原理只评结构，不评对错**。`completeness` 只证明「把推导过程写全了」。rubric 文案、UI 提示、结果面板三处均如实披露。测试 `structureOnlyNotSemantics` / `不判断语义正确性` 明确固化这一点。

4. **Goal → knowledgeId 链路未打通**。后端 `LearningGoalVO` 无 `knowledgeId` 字段，知识点级绑定目前只在 Evidence metadata 或调用方显式传参时生效。

5. **CONSISTENCY 不是能力分**，衡量的是练习节奏连续性与分数稳定性。

6. **AI 边界**：本阶段全确定性。AI 不得直接修改 Mastery / Diagnosis / UserApp / Review。

---

## 六、已知遗留（下一阶段再决定）

- SCENARIO / FIRST_PRINCIPLES 并入掌握度证据（需先评审权重）
- Goal → knowledgeId 打通，实现「GC 目标 → 自动定位 GC 场景」
- EXPRESSION 维度（需先有可靠的写作评分口径）
- Feynman 四维评分接入 LLM 作为评价辅助（不得直写 Mastery）

---

## 七、停止条件（§40）

**STOP。** 未开始：AI Coach / Agent / MCP / Tool Calling / RAG / 复杂 LLM Orchestration。
