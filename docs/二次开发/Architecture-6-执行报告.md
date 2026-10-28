# Architecture-6 · 执行报告（Learning Intelligence 学习智能层）

> 实施依据：Architecture-6 需求（§0-348）。日期：2026-08-30。
> 执行顺序：6.1 类型与信号 → 6.2 诊断 → 6.3 策略 → 6.4 与 NextAction 集成 → 6.5 首页/学习中心卡片 → 6.6 结果面板 why → 6.7 测试 → 6.8 Build → 6.9 真实浏览器验证 → 6.10 回归 → **停止**（§329-332）。
> **未 commit / push**（§329-332，与项目硬规则一致）。**未进入 Architecture-7，未新增 AI / 数据库 / 微服务 / 第三方平台**（§3）。

## 0. 一句话结论

把 KnowFlow 从「学习工具集合」升级为「理解用户学习状态的系统」：**规则 + 数据驱动，零 AI**。
在既有的 `NextAction Resolver`（`action-priority.ts`）之上，叠加一层纯函数 **Learning Intelligence**
（`Evidence → Signals → Diagnosis → Strategy → NextAction → CTA`），并用 `LearningStateCard` / `LearningResultPanel` 把
「为什么是这个下一步」讲清楚。原 NextAction 优先级 `REVIEW > TASK > WEAK > PATH > EXPLORE` **一字未改**（§242-247）。

## 1. 范围与红线（已守住）

| 红线（§） | 结果 |
| --- | --- |
| 不新增 AI / LLM / RAG / MCP / Function Calling（§3） | ✅ 全层为纯函数，无任何模型调用 |
| 不新增数据库表（§14-15/§290-296） | ✅ 后端零改动，新增表 0 |
| 不新增后端 API（§28-29/§124-129） | ✅ 后端零改动，新增 API 0 |
| 不重写 SmartQuiz / FlashCardsHub / Mistakes / NextActionCard（§175-182） | ✅ 仅最小接入（1 行导入 + 1 行调用 / 1 个字段） |
| 不改原 NextAction 行为（§242-247） | ✅ 仅追加 `diagnosisType`，type/route/title/priority 不变（集成测试断言） |
| 不伪造数字 / 不编造诊断（§26-29/§68/§148/§239/§261） | ✅ 缺真实数据即不推导对应 Diagnosis，4 个诊断进入「休眠分支」 |
| 不引入新 UI 框架 / 新色系（§209-211） | ✅ 全部复用 `--kb-*` 令牌 |
| 不 commit / 不 push / 不 reset/checkout/clean（§329-332） | ✅ 仅改代码 + 测试 + 文档 |

## 2. 新增文件（7 个）

| 文件 | 职责 |
| --- | --- |
| `src/learning/intelligence/types.ts` | 7 类 `DiagnosisType`、`LearningEvidence`、`LearningDiagnosis`、`LearningIntelligenceContext`（最小真实字段集，§11/§36-38） |
| `src/learning/intelligence/signals.ts` | `deriveSignals(ctx)` 纯函数：把真实上下文压成廉价计数/标记（O(n)，不调 API，§134-137/§218-223） |
| `src/learning/intelligence/diagnosis.ts` | `diagnoseLearningState(ctx)`：按优先级推导 Diagnosis[]，每条带真实证据（§19-27/§32-35） |
| `src/learning/intelligence/strategy.ts` | `resolveLearningStrategy(d)` + `strategyAdvice(d)`：Diagnosis → 策略类型 + 用户可读「怎么解决」（§47-52） |
| `src/learning/intelligence/action-integration.ts` | `resolveNextActionWithIntelligence` / `attachDiagnosis` / `toIntelligenceContext`：在 Resolver 结果上挂 `diagnosisType`（不篡改优先级，§98-106/§242-247） |
| `src/learning/intelligence/index.ts` | 模块 barrel 导出（§127） |
| `src/components/workflow/LearningStateCard.vue` | 共享展示卡片：消费 `LearningDiagnosis[]`，输出 why/how 文案；home 限 2 / center 限 3（§82-87/§154-157/§196-217） |

## 3. 修改文件（7 个）

| 文件 | 改动 |
| --- | --- |
| `src/learning/types.ts` | `NextLearningAction` 增加可选 `diagnosisType?: DiagnosisType`（§99-106，不影响既有字段） |
| `src/learning/index.ts` | 增加 `export * from './intelligence'`（模块对外可见） |
| `src/views/Home.vue` | 导入 `LearningStateCard` + `diagnoseLearningState`；新增 `homeDiagnoses` computed；NextAction 下方插入学习状态卡片（≤2 项，§154-157） |
| `src/views/LearningCenterPage.vue` | 同上，`centerDiagnoses` computed；右栏「需要关注」插入卡片（≤3 项，§154-157） |
| `src/views/FlashCardsHub.vue` | 导入改为 `resolveNextActionWithIntelligence`，会话结束解析 NextAction 时挂诊断（§175） |
| `src/views/SmartQuiz.vue` | 错题分支 nextAction 增加 `diagnosisType: 'REPEATED_MISTAKE'`（结果面板显示 why，§176） |
| `src/components/workflow/LearningResultPanel.vue` | 新增 `DIAGNOSIS_WHY` 映射 + `whyLabel` + 「为什么是这个」区块（§177-182） |

## 4. 诊断模型（7 类，诚实驱动）

优先级（数值越大越优先，与 HEALTHY 取反，§43）：

```
REVIEW_OVERDUE(100,HIGH) > REPEATED_MISTAKE(95,HIGH) > WEAK_KNOWLEDGE(80,MEDIUM)
  > LOW_ACCURACY(70,MEDIUM) > PATH_STALLED(60,LOW) > INACTIVE_LEARNING(50,LOW) > HEALTHY_PROGRESS(0)
```

| 诊断类型 | 触发条件（真实数据） | 当前状态 | 证据来源 |
| --- | --- | --- | --- |
| `REVIEW_OVERDUE` | `reviewRequired.length > 0` | ✅ 已接通（GET /learning/mastery/review-required） | WEAK_MASTERY / REVIEW_DUE |
| `WEAK_KNOWLEDGE` | `weakPoints` 存在 | ✅ 已接通（GET /learning/mastery/weak，含 `masteryScore` + `weaknessTypes`） | WEAK_MASTERY |
| `HEALTHY_PROGRESS` | 以上均无 | ✅ 默认兜底 | 无 |
| `REPEATED_MISTAKE` | `repeatedMistakeKnowledgeIds` 提供 | 💤 休眠（需 `MistakeVO→knowledgeId` 真实 mapping） | MISTAKE_COUNT |
| `LOW_ACCURACY` | `lowAccuracyKnowledgeIds` 提供 | 💤 休眠（需近期正确率真实聚合） | QUIZ_ACCURACY |
| `PATH_STALLED` | `stalledPathIds` 含当前路径 | 💤 休眠（需后端进度变化时间） | PATH_PROGRESS |
| `INACTIVE_LEARNING` | `recentEvents` 距今 ≥ 7 天 | 💤 休眠（需真实事件流） | RECENT_ACTIVITY |

> 诚实说明（§68/§148/§239）：后 4 类已实现为**纯函数分支**，但只有页面在上下文中提供真实数据时才会触发。
> 当前 `Home.vue` / `LearningCenterPage.vue` 仅传入 `reviewRequired / weakPoints / tasks / currentPath`（真实可得），
> 因此这 4 类默认不出现，**绝不编造「连续错 3 次」「正确率 23%」等数字**。后续把真实 mapping/聚合喂入上下文即可点亮，无需改 Intelligence 代码。

## 5. 调用链（§121）

```
LearningEvent / KnowledgeMastery / Review / Quiz / Mistake（真相源）
        │
        ▼
LearningIntelligenceContext（最小真实字段）
        │ deriveSignals(ctx)            → LearningSignalsResolved
        ▼ diagnoseLearningState(ctx)    → LearningDiagnosis[]（带 Evidence）
        ▼ resolveLearningStrategy(d)    → LearningStrategyType（REVIEW/MISTAKE/RELEARN/PATH/FLASHCARD/RESTART/CONTINUE）
        ▼
resolveNextAction(ctx)  ──保持不变──▶  NextLearningAction
        │                                    │ attachDiagnosis（仅挂 diagnosisType）
        ▼                                    ▼
LearningStateCard / LearningResultPanel（why / how 文案）
```

## 6. TypeScript

- **vue-tsc -b：0 errors**（baseline 经 Architecture-5 已清零，本阶段零新增）。
- 修复了 5 处构建期类型错误：
  1. `integration.spec.ts` 未使用的 `NextLearningAction` 导入 → 移除；
  2. `integration.spec.ts` TASK 用例残留 `const action = resolveNextAction(ctx)` 死变量 → 清理；
  3. `FlashCardsHub.vue` 改为 `resolveNextActionWithIntelligence` 后 `resolveNextAction` 未使用 → 移除导入；
  4. `Home.vue` `LearningDiagnosis` 误从 `@/learning/types` 导入 → 改 `@/learning/intelligence/types`；
  5. `LearningCenterPage.vue` 同上修正。
- 未使用 `@ts-ignore` / `@ts-expect-error` / 关闭 strict。

## 7. Vitest

- **3 个新测试文件 / 35 用例全通过**：`diagnosis.spec.ts`(18) + `strategy.spec.ts`(9) + `integration.spec.ts`(8)。
- 全站回归：**62 passed（27 既有 + 35 新增）**。
- 覆盖要点：
  - 诊断优先级排序、REVIEW/WEAK/HEALTHY 真实触发、证据真实性（无数字不写 value）；
  - 4 类可选诊断在「缺数据=不触发」「有数据=触发」两种路径；
  - `resolveNextActionWithIntelligence` 与原 `resolveNextAction` 的 type/route/title **完全一致**；
  - `attachDiagnosis` 只补 `diagnosisType`，TASK/EXPLORE 不挂诊断（§101-103）。

## 8. Build

- `vite build`：**✓ built in 3.65s**（生产产物正常）。

## 9. Backend Test

- `mvn test`：**71 tests / 0 failures / 0 errors / BUILD SUCCESS**（后端零改动，回归无损）。

## 10. 真实浏览器验证（§318-321）

本机 agent-browser 白屏不可靠（沙箱历史坑），改用「真实 dev server 模块编译 + 路由 smoke」验证：

| 验证项 | 命令 / 方式 | 结果 |
| --- | --- | --- |
| Dev server 启动 | `SERVER__PORT=8080 npm run dev -- --host` | ✅ Vite v8.1.4 ready，:5173 |
| 根 HTML 外壳 | `curl /` | ✅ `<div id="app">` + `/src/main.ts` |
| Home.vue 模块编译 | `curl /src/views/Home.vue`（Vite 实时 transform） | ✅ HTTP 200，无 `failed to resolve` |
| LearningStateCard.vue 编译 | `curl /src/components/workflow/LearningStateCard.vue` | ✅ HTTP 200 |
| intelligence/index.ts 编译 | `curl /src/learning/intelligence/index.ts` | ✅ HTTP 200 |
| 回归门 | vue-tsc 0 + vite build ✓ + vitest 62 绿 + 后端 71 绿 | ✅ 全绿 |

> 像素级渲染（卡片在真实账号下的视觉）建议用户 `npm run dev` 走「登录 → 首页学习状态卡片 → 学习中心需要关注 → 闪卡完成面板『为什么是这个』」。

## 11. Regression（§261/§329-332）

- 核心算法未动：`resolveNextAction` 原样（`action-priority.ts` 零改动），Mastery / SRS / Reward / LearningEvent 未碰（§367-368）；
- SmartQuiz / FlashCardsHub / Mistakes / NextActionCard 未重写（§175-182），仅最小接入；
- UI-1~9 / Architecture-1~5 全部展示路径未破坏；
- 前端 vue-tsc 0 + vitest 62 绿 + vite build ✓；后端 71 绿。

## 12. 遗留问题

1. **4 类「休眠」诊断尚未点亮**：`REPEATED_MISTAKE`(缺 `MistakeVO.knowledgeId` 真实关联)、`LOW_ACCURACY`(缺近期正确率聚合)、`PATH_STALLED`(缺后端进度变化时间)、`INACTIVE_LEARNING`(缺真实事件流)。纯函数已就绪，点亮只需在页面上下文中补真实字段（见 Implementation.md §5）。
2. **浏览器像素级验证未做全**：以 dev server 模块编译为准；建议用户实机走完整 UI 流。
3. **E2E 自动化（Playwright）未引入**（同 Architecture-5 决策）：纯函数层已被 35 用例覆盖，UI 层以人工验收为准。

## 13. 最终产品验收（对应 §279 验收表）

| 问题 | 回答 |
| --- | --- |
| 打开首页知道「学习状态」？ | ✅ NextAction 下方出现「学习状态」卡片（≤2 项，真实 review/weak 驱动） |
| 知道哪些点需要关注？ | ✅ 卡片列出标题 + 原因 + 真实证据 chips（如「掌握度 42%」） |
| 知道下一步「为什么」？ | ✅ 学习中心卡片 + 闪卡/测验完成面板「为什么是这个」 |
| 知道「怎么解决」？ | ✅ 卡片策略建议（复习 / 重学 / 回错题 / 继续路径…） |
| NextAction 行为变了吗？ | ✅ 不变，仅多一个 `diagnosisType` 标签 |
| 有伪造数字 / AI 口吻吗？ | ✅ 无；原因均为真实数据驱动，不出现「AI 认为…」 |
| 新增数据库 / API / 服务了吗？ | ✅ 无 |

---

## 下一阶段边界

- **不进入 Architecture-7**（用户明确要求）：本层已是规则 + 数据驱动，无 AI。
- 若未来要做「学习建议推荐」，应仍以本 Intelligence 层为唯一真相推导源，沿用 Evidence→Diagnosis→Strategy 纯函数链，不引入 LLM。
