# Phase 2-B 知识掌握度引擎 — 完成报告

> 生成日期：2026-08-29
> 范围：Phase 2-B（Knowledge Mastery Engine），按 `docs/开发计划/Phase2_知识掌握度引擎设计.md` 执行
> 状态：**实施完成，未提交/未推送（按约定仅输出 git diff / git status）**

---

## 1. 概述

Phase 2-B 在 KnowFlow（Spring Boot 3.2 + Vue3 + MyBatis-Plus + JWT）中落地「知识点级掌握度引擎」：将散落在 quiz / code / mistake / review / recall / flashcard / doc 的学习信号，按知识点（`kg_entity.id`）聚合为可解释的掌握度分数、置信度、遗忘风险与学习状态，并驱动前端图谱着色与掌握度看板。全程真实数据、零 Mock，算法完全可解释（无 LLM / 无 ML 黑盒）。

## 2. 目标与验收标准

- [x] 知识载体 = `kg_entity.id`；仅 `CONCEPT/TECHNIQUE/TERM/PRINCIPLE` 进入 Mastery（TOOL 默认关闭）。
- [x] 不新建 `knowledge_point` 表；复用 `kg_entity`。
- [x] 新增 `resource_knowledge_mapping`（资源→知识点映射层）与 `knowledge_mastery`（掌握度表）。
- [x] 显式表 + 最佳努力自动链接（AUTO），支持多对多；不调 LLM；优先级 显式 > AUTO > 分类兜底。
- [x] 事件驱动：`LearningEventServiceImpl.record()` 仅持久化 + 触发，`MasteryEngine` 负责算法。
- [x] 19 个集成测试场景全部通过；前端 `vue-tsc -b` 本模块零类型错误。

## 3. 设计决策回顾

| # | 决策 | 结论 |
|---|------|------|
| q-0 | 知识载体 | `kg_entity.id`；仅 4 类可学习类型；不建新表 |
| q-1 | 资源→知识点映射 | 显式表 + 自动链接；多对多；source + confidence + status；不分类扩散 |
| q-2 | 事件驱动 | `record()` 只持久化 + 触发；算法在 `MasteryEngine`；解析在 `ResourceKnowledgeService`；领域逻辑在 `KnowledgeMasteryService` |

## 4. 总体架构

```
LearningEvent.record()
        │ processEvent(event)
        ▼
ResourceKnowledgeService.resolveKnowledgeIds(...)
        │ 已接受映射 → 逐知识点
        ▼
KnowledgeMasteryService.recalc(userId, kid)   ── @Transactional(REQUIRES_NEW)
        │
        ├─ MasteryEngine.compute()  ── 聚合 7 类信号 → MasteryComputation
        └─ synthesize()             ── mastery / conf / risk / status（状态机）

查询：KnowledgeMasteryController → KnowledgeMasteryService → VOs
回填：ResourceKnowledgeBackfillService.backfillUser() → buildAllMappings() + recalculateUser()
```

## 5. 数据模型

**`resource_knowledge_mapping`**（资源→知识点映射层）
- 列：`id, resource_type, resource_id, knowledge_id, source, confidence, status, create_time, update_time, deleted`
- 唯一键：`uk_rkm_res_know(resource_type, resource_id, knowledge_id)`
- 索引：`idx_rkm_knowledge, idx_rkm_resource, idx_rkm_status`

**`knowledge_mastery`**（掌握度表）
- 列：`id, user_id, knowledge_id, mastery_score, confidence_score, learning_status, correct_count, wrong_count, attempt_count, review_count, recall_count, recall_avg_score, coding_attempt_count, coding_pass_count, mistake_count, mistake_mastered, consecutive_correct, consecutive_wrong, last_learned_at, last_reviewed_at, last_assessed_at, next_review_at, forgetting_risk, create_time, update_time, deleted`
- 唯一键：`uk_km_user_knowledge(user_id, knowledge_id)`
- 索引：`idx_km_status, idx_km_risk, idx_km_next`
- 逻辑外键（无物理外键，符合项目规约）；索引名带表前缀。

## 6. 资源→知识点映射（ResourceKnowledgeService）

- `resolveKnowledgeIds(resourceType, resourceId)`：查已 ACCEPTED 映射，返回关联知识点集合。
- `buildAllMappings()`：扫描存量 quiz / code / mistake / review / recall / flashcard / doc 行，按 AUTO 匹配规则（得分 ≥ `AUTO_THRESHOLD`=0.60 入 ACCEPTED，[0.30,0.60) 入 PENDING，否则跳过）生成映射；已存在则跳过（幂等）。
- 置信度分级：`CONF_MANUAL=1.0 / CONF_AI=0.92 / CONF_AUTO_HIGH=0.85 / CONF_AUTO_MID=0.60 / CATEGORY_FALLBACK=0.20`。
- 分类极低置信兜底仅作最后手段，绝不分类扩散。

## 7. 掌握度算法（MasteryEngine 信号聚合）

`compute(userId, knowledgeId)` 仅消费 ACCEPTED 映射 + 当前用户真实记录，聚合 7 类信号（空映射直接返回无信号）：

1. QUIZ：正确率，拉普拉斯 `(correct+α)/(correct+wrong+2α)`（α=1）
2. CODING：通过率，拉普拉斯 `(pass+α)/(attempt+2α)`
3. RECALL：主动回忆平均分 / 100（仅 COMPLETED 会话）
4. REVIEW：SM-2 稳定度 `repetitions/(repetitions+lapses)` × 0.7 + 间隔归一 × 0.3（+ 复习日志质量加权）
5. MISTAKE：已掌握错题比，拉普拉斯 `(mastered+α)/(count+α)`
6. LEARNING（DOC）：阅读进度均值（>0 样本）
7. 各信号记录 sampleCount、计数器、最近时间，供合成与解释。

## 8. 信号维度与权重

| 维度 | 权重 | 强度来源 |
|------|------|----------|
| QUIZ | 0.25 | 答题正确率（拉普拉斯） |
| CODING | 0.25 | 编程通过率（拉普拉斯） |
| RECALL | 0.20 | 主动回忆平均分 / 100 |
| REVIEW | 0.10 | SM-2 稳定度 + 间隔 |
| MISTAKE | 0.10 | 已掌握错题比（拉普拉斯） |
| LEARNING | 0.10 | 文档阅读进度均值 |

全部权重 / 阈值集中在 `MasteryConfig`，禁止 magic number。

## 9. 合成：mastery / confidence / risk / status

- **mastery** = `round(Σ(wᵢ·strengthᵢ) / Σwᵢ × 100)` —— 仅对「有效信号」归一化，缺失信号不计入分母（避免被稀释）。
- **confidence** = `round(N / (N + K) × 100)`，`K=CONFIDENCE_K=10`，`N` = 所有信号样本数之和。
- **forgetting_risk** = `daysSinceActivity/DECAY_PERIOD × 60 + lapseWeight + (100-mastery)×0.2`，clamp [0,100]；`DECAY_PERIOD=30`。
- **weaknessTypes / reason**：LOW_MASTERY / CODING_WEAK / RECALL_WEAK / FORGETTING_RISK / LOW_CONFIDENCE / HIGH_ERROR_RATE 按需判定，生成人读解释。

## 10. 状态机

```
NOT_STARTED ── 无信号（冷启动，跳过，不落行）
LEARNING    ── 默认（非 WEAK / 非 MASTERED / 非 REVIEW_REQUIRED）
WEAK        ── mastery < 60
MASTERED    ── mastery ≥ 80 且 conf ≥ 50 且 risk < 60
REVIEW_REQUIRED ── risk ≥ 70 或 nextReviewAt 已过期
```

## 11. 服务层职责划分

| 服务 | 职责 |
|------|------|
| `ResourceKnowledgeService` | 解析 + AUTO 匹配 + 回填映射 |
| `MasteryEngine` | 事件 → 信号聚合 → MasteryComputation（算法核心） |
| `KnowledgeMasteryService` | 合成计算 + 持久化 + 查询 + 诊断 |
| `ResourceKnowledgeBackfillService` | 一次性/可重复 buildAllMappings + 全量重算 |
| `LearningEventServiceImpl.record()` | 仅持久化 + `processEvent` 触发（算法不在本方法内） |

## 12. 事件驱动接入

`LearningEventServiceImpl.record()` 在持久化事件后调用 `knowledgeMasteryService.processEvent(event)`；`processEvent` 解析映射 → 逐知识点 `recalc`。异常仅告警不阻断主业务。

## 13. 回填与重算

- `POST /api/learning/mastery/recalculate` → `backfillUser(userId)` = `buildAllMappings()` + `recalculateUser(userId)`（文档 AI 抽取或算法升级后调用）。
- `recalc` 整行重算（幂等，不累计增量）；无信号则删除旧行。
- 独立事务 `REQUIRES_NEW`，保证与事件主事务解耦。

## 14. API 端点（6 个，全部当前用户隔离）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/learning/mastery` | 我的知识点掌握度总览 |
| GET | `/api/learning/mastery/weak` | 薄弱（WEAK） |
| GET | `/api/learning/mastery/review-required` | 需复习（REVIEW_REQUIRED） |
| GET | `/api/learning/mastery/{knowledgeId}` | 详情 + 可解释说明 |
| GET | `/api/learning/mastery/diagnostics` | 引擎可观察性诊断 |
| POST | `/api/learning/mastery/recalculate` | 构建映射并重算我的掌握度 |

兼容端点 `GET /api/learning/stats/mastery`、`GET /api/learning/category-mastery` 保持不变。

## 15. 前端：types / API / router

- `src/api/types.ts`：新增 `MasteryStatus`、`SignalContributionVO`、`KnowledgeMasteryVO`、`KnowledgeMasteryDetailVO`、`MasteryDiagnosticsVO`。
- `src/api/learning.ts`：新增 6 个 API（knowledgeMastery / weak / reviewRequired / detail / diagnostics / recalculate）。
- `src/router/index.ts`：新增 `/learning/knowledge-mastery/:id` 路由（fullscreen）。

## 16. 前端：MasteryDashboard 扩展

新增「我的知识点掌握度」区块：列表展示各知识点名称 / 类型 / 状态徽标 / 掌握度分 / 置信度与遗忘风险双进度条 / 薄弱标签；提供「重新计算」按钮（`recalculate` 后刷新）；点击行进入详情。

## 17. 前端：KnowledgeGraph 掌握度叠加

`KnowledgeGraph.vue` 拉取 `/mastery` 构建 `kgId → mastery` 映射（失败容错），节点圆描边按状态着色（绿/黄/红/灰）+ 状态点 + 分数；选中节点详情面板新增掌握度概览块 + 「查看详情」按钮。

## 18. 前端：KnowledgeMasteryDetail.vue

独立详情页：类型色头像、核心指标（掌握度 / 状态 / 置信度 / 风险）、分数条、薄弱标签、可解释说明、信号贡献列表（强度 / 权重 / 贡献条）、原始计数器网格、时间信息；复用 `--kb-*` 令牌，保持五项 UI 原则。

## 19. 可观察性：diagnostics

`GET /diagnostics` 返回 映射总数 / ACCEPTED / PENDING / REJECTED / 未映射资源数 + 说明文案，便于排查「有行为但无掌握度」问题（事件无 ACCEPTED 映射时正常跳过且不阻断）。

## 20. 测试策略与结果

`MasteryEngineIntegrationTest`（@SpringBootTest + H2 真实库，19 场景）：

| 组 | 场景 | 结果 |
|----|------|------|
| 单信号 | QUIZ=67 / CODING=67 / DOC=100 / REVIEW=100 / MISTAKE=100 / RECALL=90 | ✅ |
| 加权 | 多信号归一化=72 / 单信号不被稀释=67 | ✅ |
| 状态机 | WEAK=33 / MASTERED=92 / REVIEW_REQUIRED 逾期 | ✅ |
| 置信度 | 3/(3+10)=23 | ✅ |
| 幂等 | 1 行重写 / 无信号删行 | ✅ |
| 事件 | processEvent 触发重算 | ✅ |
| 冷启动 | 无信号跳过 | ✅ |
| 可观察 | diagnostics / detail / listByStatus | ✅ |

**结论：19/19 通过，BUILD SUCCESS。**

## 21. 与设计的偏差 / Bug 修复

- **Schema 列名 Bug（已修）**：两张新表初稿误用 `created_time`，与 `BaseEntity.createTime` 映射的 `create_time` 不符，生产插入会失败。已在 `db/h2/schema.sql`、`db/mysql/schema.sql`、迁移脚本 `V20260830__knowledge_mastery_engine.sql` 统一为 `create_time`。
- **拉普拉斯预期校正**：单样本全对 = 67（非 100）、单样本全错 = 33（非 50）、10 次全对 = 92（非 100）；测试断言据此修正。
- **遗留编译错误修复**：`LearningEventServiceImpl` 补 `import lombok.RequiredArgsConstructor`；`KnowledgeMasteryServiceImpl` 修正 `MasteryEngine` 包路径（`com.knowflow.service.impl`）。
- **迁移脚本一致性**：手动存量库补表脚本现与 schema 列名一致。

## 22. 文档更新清单

- `docs/开发计划/Phase2_知识掌握度引擎设计.md`：新增 §14「实现完成记录（更新日志）」。
- `docs/开发计划/项目架构扫描与二次开发分析.md`：路线图 Phase 2 标注 ✅ DONE 2026-08-29。

## 23. 风险与技术债

- 冷库 `kg_entity` 为空时首轮自动链接全跳过（符合预期，需文档分析 / 回填种子才有数据）。
- 自动匹配为关键词级，长尾召回有限；AI 补链为后续增强（架构已预留扩展点）。
- 权重 / 阈值为 v1 默认，需在真实数据上校准（集中在 `MasteryConfig`）。
- `wb_review_card.knowledge_point` schema/实体漂移，不纳入主线。
- 仓库既有 `types.ts` 中 `CategoryVO` 重复声明冲突（lines 159/163/1925/1928）为本模块改动之前已存在问题，与本次无关。

## 24. 后续建议 / 收尾

- 真实数据校准权重阈值；评估是否启用 TOOL 类型进入 Mastery。
- 推进 AI 补链 / 长尾召回；图谱掌握度着色已就绪，可接入自适应学习路径。
- **Git**：本次改动未 `commit` / 未 `push`，仅输出 `git diff` / `git status`（符合「只本地、不推送」约定）。Phase 2-B 交付即止，未进入 Phase 3。
