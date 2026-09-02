# Architecture-6 · Learning Intelligence（学习智能层）设计说明

> 配套：`Architecture-6-执行报告.md`（交付结果）/ `Architecture-6-Diagnosis-Matrix.md`（诊断矩阵）/ `Architecture-6-Implementation.md`（落地与扩展）。
> 模块位置：`frontend/src/learning/intelligence/`。

## 1. 为什么需要这一层

Architecture-5 解决了「**下一步做什么**」（`NextAction Resolver`，规则优先级 `REVIEW > TASK > WEAK > PATH > EXPLORE`）。
但它只回答 *what*，不回答 *why*（为什么是这个，而不是别的）和 *how*（针对这个问题该怎么学）。

Architecture-6 在 **不引入 AI** 的前提下，补上「理解学习状态」的能力：

- **what**：下一步动作（沿用 Resolver，不变）
- **why**：这个动作对应的学习状态诊断（本层新增）
- **how**：针对该状态的策略建议（本层新增）

最终体验：首页看到「学习状态」卡片，学习中心看到「需要关注」，完成面板看到「为什么是这个」。

## 2. 设计原则（硬约束）

1. **纯函数，零副作用**（§32-35）：整个 Intelligence 层不调用任何 API / fetch / DB，输入是页面已加载的真实数据，输出是推导结果。可单测、可缓存、可复用。
2. **推导结果，不是真相源**（§18）：真相源仍是 `KnowledgeMastery` / `LearningEvent` / `Review` / `Quiz` / `Mistake`。Intelligence 只是「读真实数据 → 给结论」。
3. **不伪造**（§26-29/§68/§148/§239/§261）：没有真实数字就不写数字；没有真实 mapping 就不推导对应诊断。宁可不显示，也不编造「连续错 3 次」「正确率 23%」。
4. **最小上下文**（§36-38）：`LearningIntelligenceContext` 只含真实必要字段，不构造巨型 `GlobalUserLearningContext`；经济（wallet/coin/product）不属于「学习能力」，不纳入。
5. **不改原行为**（§242-247）：只给 `NextLearningAction` 追加一个 `diagnosisType` 标签，绝不偷偷改 `type/route/title/reason/priority`。
6. **复用设计系统**（§209-211）：展示组件全部走 `--kb-*` 令牌，不新建色系、不引入 UI 框架。

## 3. 分层架构

```
┌─────────────────────────────────────────────────────────────┐
│                      展示层（组件）                            │
│   LearningStateCard.vue  ·  LearningResultPanel.vue          │
│   （消费 LearningDiagnosis[]，输出 why / how 文案）           │
└───────────────┬───────────────────────────┬─────────────────┘
                │                           │
                ▼                           ▼
┌──────────────────────────┐   ┌──────────────────────────────┐
│  action-integration.ts   │   │  strategy.ts                 │
│  resolveNextActionWith…  │   │  resolveLearningStrategy /    │
│  attachDiagnosis         │   │  strategyAdvice              │
│  （只挂 diagnosisType）   │   └──────────────┬───────────────┘
└───────────────┬──────────┘                 │
                │ toIntelligenceContext       │
                ▼                             ▼
┌──────────────────────────┐   ┌──────────────────────────────┐
│  diagnosis.ts            │   │  signals.ts                  │
│  diagnoseLearningState   │◄──│  deriveSignals               │
│  （带 Evidence 的诊断）   │   │  （O(n) 信号聚合）           │
└───────────────┬──────────┘   └──────────────┘
                │
                ▼
        types.ts（DiagnosisType / LearningEvidence / LearningDiagnosis / Context）
```

数据来源（真实、已加载）：
- `reviewRequired` ← `GET /learning/mastery/review-required`
- `weakPoints` ← `GET /learning/mastery/weak`（含 `masteryScore` + `weaknessTypes`）
- `tasks` ← `GET /learning/tasks`
- `currentPath` ← 已报名且 `0 < progress < 100` 的路径
- （可选）`recentEvents` / `repeatedMistakeKnowledgeIds` / `lowAccuracyKnowledgeIds` / `stalledPathIds`

## 4. 核心类型

```ts
// types.ts（节选）
export type DiagnosisType =
  | 'REVIEW_OVERDUE' | 'WEAK_KNOWLEDGE' | 'REPEATED_MISTAKE'
  | 'LOW_ACCURACY' | 'PATH_STALLED' | 'INACTIVE_LEARNING' | 'HEALTHY_PROGRESS'

export interface LearningEvidence {
  type: EvidenceType            // WEAK_MASTERY / REVIEW_DUE / MISTAKE_COUNT / QUIZ_ACCURACY / PATH_PROGRESS / RECENT_ACTIVITY
  label: string                 // 用户可读，如「掌握度 42%」
  value?: number                // 可选真实量化值；无则不展示
}

export interface LearningDiagnosis {
  type: DiagnosisType
  title: string                 // 「AQS 需要复习」
  reason: string                // 真实数据驱动，禁止「AI 认为…」
  evidence: LearningEvidence[]  // 支撑证据（真实）
  knowledgeId?: number
  knowledgeName?: string
  severity: Severity            // HIGH / MEDIUM / LOW（产品标签，非伪精确评分）
  priority: number              // 排序权重，越大越优先
}
```

## 5. 与 Architecture-5 的关系

| 维度 | Architecture-5 | Architecture-6 |
| --- | --- | --- |
| 职责 | 下一步动作（what） | 学习状态理解（why + how） |
| 核心 | `action-priority.ts` Resolver | `intelligence/` 纯函数层 |
| 改动 Resolver？ | — | **不改**，仅包装追加 `diagnosisType` |
| 真相源 | LearningEvent / Mastery / Reward | 同上（Intelligence 只读不写） |
| 新增 DB/API | 否 | 否 |

两层通过 `action-integration.ts` 衔接：`resolveNextActionWithIntelligence(ctx)` 内部先调 `resolveNextAction(ctx)`，再 `attachDiagnosis` 挂诊断，行为等价。

## 6. 诚实性边界（重点）

本层刻意区分「已接通」与「休眠」：

- **已接通（真实数据可得，默认显示）**：`REVIEW_OVERDUE`、`WEAK_KNOWLEDGE`、`HEALTHY_PROGRESS`。
- **休眠（纯函数已实现，需真实数据点亮）**：
  - `REPEATED_MISTAKE`：需要 `MistakeVO → knowledgeId` 的真实关联（当前 `MistakeVO` 缺该字段，无法无伪造地关联）。
  - `LOW_ACCURACY`：需要「近期练习正确率」的真实聚合（当前上下文未加载该聚合）。
  - `PATH_STALLED`：需要路径「进度变化时间」（当前 `LearningPathVO` 无该时间戳）。
  - `INACTIVE_LEARNING`：需要真实事件流（`recentEvents`）。

这 4 类在上下文未提供对应字段时**不触发**，因此不会出现编造数字。点亮它们不需要改 Intelligence 代码，只需在页面把真实字段喂进 `LearningIntelligenceContext`（见 Implementation.md §5）。

## 7. 展示组件契约

`LearningStateCard.vue`（共享）：
- props：`diagnoses: LearningDiagnosis[]`、`mode?: 'home' | 'center'`
- 限制：`home` 取前 2 条，`center` 取前 3 条（§154-157）
- 不展示内部枚举文案，只展示：标题 / 原因 / 证据 chips / 策略建议
- 严重度标签：HIGH→「需关注」、MEDIUM→「可巩固」、LOW→「提示」（用户可读，不暴露枚举）
- 颜色全部走 `--kb-warning` / `--kb-primary` / `--kb-muted-foreground`

`LearningResultPanel.vue`：
- 完成面板内新增「为什么是这个：<whyLabel>」区块
- `DIAGNOSIS_WHY: Record<DiagnosisType, string>` 把诊断类型映射为「为什么」（如 `REVIEW_OVERDUE → 它已经进入复习队列`）

## 8. 可测试性

全层为纯函数，35 个新增用例覆盖：
- 诊断优先级与排序；
- 真实触发（REVIEW / WEAK / HEALTHY）与证据真实性；
- 休眠分支的「有数据触发 / 无数据不触发」双路径；
- `resolveNextActionWithIntelligence` 与原 Resolver 行为一致；
- `attachDiagnosis` 只补标签，TASK/EXPLORE 不挂诊断。
