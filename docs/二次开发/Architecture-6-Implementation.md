# Architecture-6 · Implementation（落地与扩展手册）

> 实现位置：`frontend/src/learning/intelligence/`。
> 配套：执行报告 / Learning-Intelligence / Diagnosis-Matrix。

## 1. 文件地图

```
frontend/src/learning/intelligence/
├── types.ts            # 类型：DiagnosisType / EvidenceType / Severity / LearningEvidence / LearningDiagnosis / LearningIntelligenceContext
├── signals.ts          # deriveSignals(ctx) → LearningSignalsResolved（O(n)，纯内存聚合）
├── diagnosis.ts        # diagnoseLearningState(ctx) → LearningDiagnosis[]（带 Evidence，按 priority 降序）
├── strategy.ts         # resolveLearningStrategy(d) / strategyAdvice(d)
├── action-integration.ts # resolveNextActionWithIntelligence / attachDiagnosis / toIntelligenceContext
├── index.ts            # barrel 导出
└── __tests__/
    ├── diagnosis.spec.ts   (18)
    ├── strategy.spec.ts    (9)
    └── integration.spec.ts (8)

frontend/src/
├── learning/types.ts            # NextLearningAction 增加 diagnosisType?
├── learning/index.ts            # export * from './intelligence'
├── components/workflow/
│   ├── LearningStateCard.vue    # 共享展示卡片（home≤2 / center≤3）
│   └── LearningResultPanel.vue  # 新增「为什么是这个」区块
└── views/
    ├── Home.vue                  # 插入 LearningStateCard（mode="home"）
    ├── LearningCenterPage.vue    # 插入 LearningStateCard（mode="center"）
    ├── FlashCardsHub.vue         # resolveNextActionWithIntelligence 接入
    └── SmartQuiz.vue             # 错题分支补 diagnosisType: 'REPEATED_MISTAKE'
```

## 2. 数据流（Mermaid）

```mermaid
flowchart TD
    A[真实数据<br/>reviewRequired / weakPoints / tasks / currentPath] --> B[LearningIntelligenceContext]
    B --> C[deriveSignals<br/>signals.ts]
    C --> D[diagnoseLearningState<br/>diagnosis.ts]
    D --> E[LearningDiagnosis[]<br/>带 Evidence + priority]
    E --> F[resolveLearningStrategy<br/>strategy.ts]
    F --> G[LearningStrategyType + strategyAdvice]
    E --> H[LearningStateCard.vue<br/>why / how 文案]
    E --> I[attachDiagnosis<br/>action-integration.ts]
    J[resolveNextAction<br/>action-priority.ts 不变] --> I
    I --> K[NextLearningAction.diagnosisType]
    K --> L[LearningResultPanel.vue<br/>为什么是这个]
```

## 3. 关键函数签名

```ts
// signals.ts
deriveSignals(ctx: LearningIntelligenceContext): LearningSignalsResolved

// diagnosis.ts —— 纯函数，不调 API
diagnoseLearningState(ctx: LearningIntelligenceContext): LearningDiagnosis[]

// strategy.ts
resolveLearningStrategy(d: LearningDiagnosis): LearningStrategyType
strategyAdvice(d: LearningDiagnosis): string

// action-integration.ts —— 不重写 Resolver
toIntelligenceContext(ctx: LearningActionContext): LearningIntelligenceContext
attachDiagnosis(action: NextLearningAction, diagnoses: LearningDiagnosis[]): NextLearningAction
resolveNextActionWithIntelligence(ctx: LearningActionContext, skip?): NextLearningAction
```

## 4. 在页面接入（示例：Home.vue）

```ts
import LearningStateCard from '@/components/workflow/LearningStateCard.vue'
import { diagnoseLearningState } from '@/learning/intelligence/diagnosis'
import type { LearningDiagnosis } from '@/learning/intelligence/types'

const homeDiagnoses = computed<LearningDiagnosis[]>(() =>
  diagnoseLearningState({
    reviewRequired,          // 页面已加载（GET /learning/mastery/review-required）
    weakPoints,              // 页面已加载（GET /learning/mastery/weak）
    tasks,                   // 页面已加载（GET /learning/tasks）
    currentPath,             // 页面已加载（进行中路径）
  }),
)
```

```vue
<!-- NextAction 卡片下方 -->
<LearningStateCard :diagnoses="homeDiagnoses" mode="home" />
```

## 5. 如何点亮「休眠」诊断（无需改 Intelligence 代码）

纯函数分支已就绪，只需在页面把真实字段喂进 `LearningIntelligenceContext`：

| 诊断 | 需要补的上下文字段 | 真实数据来源建议 |
| --- | --- | --- |
| `REPEATED_MISTAKE` | `repeatedMistakeKnowledgeIds: number[]` | 后端 `mistake` 表补 `knowledge_id` 列后，按 userId 聚合「近 N 天同 knowledgeId 多次答错」的 id |
| `LOW_ACCURACY` | `lowAccuracyKnowledgeIds: number[]` | 后端聚合「近 N 天 quiz 正确率 < 阈值」的 knowledgeId |
| `PATH_STALLED` | `stalledPathIds: number[]` | `learning_path` 增加 `last_progress_at` 列；进度变化超过 M 天视为停滞 |
| `INACTIVE_LEARNING` | `recentEvents: LearningEventVO[]` | 直接复用现有 `LearningEvent` 最近事件流 |

> 注意：上述字段**全部走既有数据 + 可选上下文**，不强制新建表。若确需 `knowledge_id` / `last_progress_at` 等列，按项目规约建逻辑外键 + 索引（`idx_<表>_<字段>`），并在 H2 + MySQL 同步 schema（§二环境踩坑）。

## 6. 测试写法（参考）

```ts
// diagnosis.spec.ts
const ds = diagnoseLearningState(baseCtx({
  reviewRequired: [mastery({ name: 'AQS', learningStatus: 'REVIEW_REQUIRED' })],
}))
expect(ds[0].type).toBe('REVIEW_OVERDUE')
expect(ds[0].evidence[0].value).toBeGreaterThan(0) // 真实数字才写 value

// integration.spec.ts —— 断言原行为不变
const a = resolveNextAction(ctx)
const b = resolveNextActionWithIntelligence(ctx)
expect(b.type).toBe(a.type)
expect(b.route).toBe(a.route)
expect(b.title).toBe(a.title)
// 仅多 diagnosisType
```

## 7. 回归门（交付必过）

| 项 | 命令 | 预期 |
| --- | --- | --- |
| 类型 | `npm run build`（含 `vue-tsc -b`） | 0 errors |
| 构建 | `vite build` | ✓ built |
| 前端测试 | `npx vitest run` | 62 passed |
| 后端测试 | `mvn -o test`（Corretto 17） | 71 tests / 0 failures |

## 8. 反模式（禁止）

- ❌ 在 `diagnosis.ts` / `signals.ts` 里 `fetch` / 调 API。
- ❌ 编造 `value` 数字（如「连续错 3 次」无真实来源时）。
- ❌ 用 `diagnosisType` 反向改 `resolveNextAction` 的优先级。
- ❌ 在 `LearningStateCard` 里自行拉数据（它只消费传入的 `diagnoses`）。
- ❌ 引入新色系 / 新 UI 框架（必须用 `--kb-*`）。
- ❌ 把 Intelligence 当成真相源写库（它是只读推导）。
