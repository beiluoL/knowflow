# Architecture-6 · Diagnosis Matrix（诊断矩阵）

> 7 类学习诊断的完整定义。优先级、严重度、证据、策略一一对应。
> 实现位置：`frontend/src/learning/intelligence/diagnosis.ts` + `strategy.ts`。
> 状态图例：✅ 已接通（真实数据驱动）｜💤 休眠（纯函数就绪，需真实数据点亮，无伪造）。

## 1. 优先级总表

数值越大越优先（与 `HEALTHY` 取反，§43）。`diagnoseLearningState` 返回结果按 `priority` 降序排列。

| 序 | 诊断类型 | 优先级 | 严重度 | 状态 | 触发条件 |
| --- | --- | --- | --- | --- | --- |
| 1 | `REVIEW_OVERDUE` | 100 | HIGH | ✅ | `reviewRequired.length > 0` |
| 2 | `REPEATED_MISTAKE` | 95 | HIGH | 💤 | `repeatedMistakeKnowledgeIds` 提供且匹配 |
| 3 | `WEAK_KNOWLEDGE` | 80 | MEDIUM | ✅ | `weakPoints` 中存在（逐条） |
| 4 | `LOW_ACCURACY` | 70 | MEDIUM | 💤 | `lowAccuracyKnowledgeIds` 提供且匹配 |
| 5 | `PATH_STALLED` | 60 | LOW | 💤 | `stalledPathIds` 含当前 `currentPath.id` |
| 6 | `INACTIVE_LEARNING` | 50 | LOW | 💤 | `recentEvents` 距今 ≥ 7 天 |
| 7 | `HEALTHY_PROGRESS` | 0 | LOW | ✅ | 以上均无（兜底） |

## 2. 逐类定义

### 2.1 REVIEW_OVERDUE（✅ 已接通）

| 项 | 内容 |
| --- | --- |
| 触发 | `reviewRequired.length > 0`（真实 `status = REVIEW_REQUIRED`） |
| 标题 | 多个→「N 个知识点需要复习」；单个→「「X」需要复习」 |
| 原因 | 「这些知识点已进入复习队列，需要巩固记忆，避免遗忘。」 |
| 证据 | `REVIEW_DUE`：「N 个知识点处于待复习状态」(value=N) |
| 策略 | `REVIEW` → 「通过复习巩固记忆，避免遗忘。」 |
| 关联 | `top.knowledgeId` / `knowledgeName` |

### 2.2 WEAK_KNOWLEDGE（✅ 已接通，按 weakPoints 逐条）

| 项 | 内容 |
| --- | --- |
| 触发 | `weakPoints` 中每个元素 |
| 标题 | 「「X」掌握度偏弱」 |
| 原因 | 「当前掌握度 S%，<薄弱原因>。」/ 无原因→「当前掌握度 S%，建议重新学习巩固。」 |
| 证据 | `WEAK_MASTERY`：「当前掌握度 S%」(value=S)；若有 `weaknessTypes` 再补一条 `QUIZ_ACCURACY`（如「近期答题错误率偏高」） |
| 策略 | `RELEARN` → 「用闪卡或费曼法把这个知识点重新讲一遍。」 |
| 薄弱原因翻译 | `ERROR_RATE/HIGH_ERROR_RATE`→近期答题错误率偏高；`NO_REVIEW/REVIEW_GAP`→最近复习较少；`MASTERY_DECAY/MASTERY_DROP`→掌握度有所下降；`LOW_CONFIDENCE`→掌握信心不足 |

> `weaknessTypes` 为真实枚举，由 `GET /learning/mastery/weak` 返回。仅在真实存在时才出现对应文案，不臆造。

### 2.3 REPEATED_MISTAKE（💤 休眠）

| 项 | 内容 |
| --- | --- |
| 触发 | `repeatedMistakeKnowledgeIds` 提供（需 `MistakeVO → knowledgeId` 真实 mapping；当前缺该关联） |
| 标题 | 匹配到知识点→「「X」近期重复出错」；否则→「有知识点近期重复出错」 |
| 原因 | 「这个知识点近期多次答错，可以优先弄清错因并巩固。」 |
| 证据 | `MISTAKE_COUNT`：「近期相关题目多次答错」（无 value，避免编造次数） |
| 策略 | `MISTAKE` → 「回到错题，弄清错因后再做一遍。」 |

### 2.4 LOW_ACCURACY（💤 休眠）

| 项 | 内容 |
| --- | --- |
| 触发 | `lowAccuracyKnowledgeIds` 提供（需近期正确率真实聚合） |
| 标题 | 匹配→「「X」近期正确率偏低」；否则→「有知识点近期正确率偏低」 |
| 原因 | 「最近几次练习正确率不高，建议通过练习把正确率提上来。」 |
| 证据 | `QUIZ_ACCURACY`：「最近练习正确率偏低」（无 value） |
| 策略 | `FLASHCARD` → 「用闪卡或练习巩固，把正确率提上来。」 |

### 2.5 PATH_STALLED（💤 休眠）

| 项 | 内容 |
| --- | --- |
| 触发 | `stalledPathIds.length > 0` 且含 `currentPath.id`（需后端进度变化时间） |
| 标题 | 「「<path.title>」进度停滞」 |
| 原因 | 「这条学习路径最近一段时间没有推进，可以接着上次停下的地方继续。」 |
| 证据 | `PATH_PROGRESS`：「当前进度 P%」(value=P) |
| 策略 | `PATH` → 「接着上次停下的地方继续学习。」 |

### 2.6 INACTIVE_LEARNING（💤 休眠）

| 项 | 内容 |
| --- | --- |
| 触发 | `recentEvents` 存在且距今 `>= 7` 天（温和提示，不制造焦虑，§73-76） |
| 标题 | 「最近学习较少」 |
| 原因 | 「最近一段时间没有学习记录，可以从今天的一项任务开始，保持节奏。」 |
| 证据 | `RECENT_ACTIVITY`：「最近一次学习在 D 天前」(value=D) |
| 策略 | `RESTART` → 「从今天的一个小任务重新开始，保持节奏。」 |

### 2.7 HEALTHY_PROGRESS（✅ 已接通，兜底）

| 项 | 内容 |
| --- | --- |
| 触发 | 以上 6 类均无（§25） |
| 标题 | 「学习状态良好」 |
| 原因 | 「当前复习、巩固与任务都在顺利推进，按自己的节奏继续即可。」 |
| 证据 | 无 |
| 策略 | `CONTINUE` → 「按当前节奏继续即可。」 |

## 3. Diagnosis → Strategy 映射

| Diagnosis | StrategyType | 用户可读建议（strategyAdvice） |
| --- | --- | --- |
| `REVIEW_OVERDUE` | `REVIEW` | 通过复习巩固记忆，避免遗忘。 |
| `REPEATED_MISTAKE` | `MISTAKE` | 回到错题，弄清错因后再做一遍。 |
| `WEAK_KNOWLEDGE` | `RELEARN` | 用闪卡或费曼法把这个知识点重新讲一遍。 |
| `PATH_STALLED` | `PATH` | 接着上次停下的地方继续学习。 |
| `LOW_ACCURACY` | `FLASHCARD` | 用闪卡或练习巩固，把正确率提上来。 |
| `INACTIVE_LEARNING` | `RESTART` | 从今天的一个小任务重新开始，保持节奏。 |
| `HEALTHY_PROGRESS` | `CONTINUE` | 按当前节奏继续即可。 |

## 4. DiagnosisType → 结果面板「为什么」（DIAGNOSIS_WHY）

`LearningResultPanel.vue` 用此映射解释 NextAction 的来由（§177-182）：

| DiagnosisType | 为什么是这个 |
| --- | --- |
| `REVIEW_OVERDUE` | 它已经进入复习队列 |
| `WEAK_KNOWLEDGE` | 该知识点掌握度偏弱 |
| `REPEATED_MISTAKE` | 刚才答错了相关题目 |
| `LOW_ACCURACY` | 最近练习正确率偏低 |
| `PATH_STALLED` | 这条学习路径进度停滞 |
| `INACTIVE_LEARNING` | 最近学习较少 |
| `HEALTHY_PROGRESS` | 当前学习状态良好 |

## 5. 诚实性检查清单（提交前自审）

- [ ] 每条 `LearningDiagnosis` 都带 `evidence`（HEALTHY 除外）？
- [ ] 证据 `label` 来自真实字段，无编造数字？
- [ ] `value` 仅在真实量化值存在时填写？
- [ ] 休眠诊断在无对应上下文字段时不触发？
- [ ] `reason` 不出现「AI 认为 / 系统判定」等口吻（§261/§267-269）？
- [ ] 严重度是产品标签，不是伪精确评分（§159-162）？
