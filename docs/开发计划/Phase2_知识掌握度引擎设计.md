# Phase 2：Knowledge Mastery Engine（知识掌握度智能引擎）设计草案

> 状态：**设计阶段（待产品负责人确认后实现）** · 原则：只增不删、复用现有能力、不破坏已有 API
> 配套总控文档：`项目架构扫描与二次开发分析.md`（Phase 0 审计）、Phase 1 Learning Event System（已落地）

---

## 0. 设计目标（来自总控 Prompt）

建立 `Knowledge Mastery Engine`，让系统可持续回答：

```
用户是谁 → 正在学什么 → 掌握了哪些知识点 → 每个知识点掌握程度多少
→ 哪些薄弱 → 为什么薄弱 → 最近是否学/复习 → 是否有遗忘风险
→ 是“看懂了”还是“真正掌握” → 哪些该再学/复习/做题/写代码/可跳过
```

最终数据流：

```
Learning Event → Resource→Knowledge Mapping → kg_entity → MasterySignal
             → KnowledgeMastery → {mastery / confidence / risk}
             → Weakness Detection → 后续 AI Coach / Adaptive Path
```

---

## 1. 扫描结论：kg_entity 真实结构与可学习性

### 1.1 type 实际枚举
来自 `KnowledgeServiceImpl.normalizeType()`，默认 `CONCEPT`：

`CONCEPT / TECHNIQUE / TERM / PRINCIPLE / TOOL / OTHER`（共 6 种）

> 注意：kg_entity **不存在** CATEGORY / DOCUMENT / USER 类型（总控 Prompt 举的反例在实体里压根没有）。

### 1.2 可学习知识点筛选规则（可配置 `MasteryConfig.LEARNable_TYPES`）
| type | 是否进入 Mastery | 理由 |
|------|----------------|------|
| CONCEPT | ✅ | 概念，典型可掌握知识点 |
| TECHNIQUE | ✅ | 技术/技法，可掌握 |
| TERM | ✅ | 术语/定义，可掌握 |
| PRINCIPLE | ✅ | 原理，可掌握 |
| TOOL | ⚙️ 默认不进（留开关） | “工具”偏引用型，非必须掌握 |
| OTHER | ❌ | 杂项，排除 |

→ `knowledge_mastery.knowledge_id = kg_entity.id` 且 `kg_entity.type ∈ 可学习集合`。

### 1.3 知识图谱如何关联实体
`KnowledgeServiceImpl.getEntityGraph()` 用 `kg_relation`（`sourceEntityId`/`targetEntityId`）连边，关系类型：
`RELATED_TO / PREREQUISITE / IS_A / PART_OF / USES / CONTRASTS`。
→ 满足前置链分析（如 CAS→AQS：AQS 薄弱且前置 CAS 薄弱 → 标记 `prerequisite_weakness`，Phase 2 仅输出诊断，不改 Learning Path）。

---

## 2. 资源类型与关联现状（逐项核查）

| 资源 | 实体 | 关联 category | 关联 kg_entity | 知识点外键 |
|------|------|--------------|---------------|-----------|
| QUESTION | quiz_question + quiz_answer_record | `categoryId` + `tags`(文本) + `docId` | ❌ | 否 |
| CODE | code_question + code_submit_record | 仅 `tags`(文本) | ❌ | 否 |
| MISTAKE | learning_mistake | `category`(自由文本) | ❌ | 否 |
| REVIEW(SRS) | wb_review_card + wb_review_log | `categoryId` | ❌（有 `knowledge_point` 文本列但未映射实体） | 否 |
| RECALL | wb_recall_session | `noteId/cardId`(间接) | ❌ | 否 |
| FLASHARD | learning_flashcard | `categoryId` + `docId` | ❌ | 否 |
| DOCUMENT | doc_document + doc_read_progress | `categoryId` + `docId` | ❌ | 否 |

### 2.1 关键结论
1. **已存在 resource→knowledge 映射表？** 全仓 grep `resource_knowledge / question_knowledge / code_knowledge / knowledge_mapping` 等 → **完全不存在**（greenfield，需新建）。
2. **所有资源均缺知识点映射** → 全部需经映射层 + 自动链接。
3. **历史兼容风险**：`data.sql` 无 kg_entity 种子行，kg_entity 由文档 AI 抽取运行时生成。dev/H2 冷库可能为空 → 自动链接暂匹配不到 → 事件照常落库、mastery 跳过并记诊断（符合总控 §30）。引擎在文档被分析后自然生效。原有 `/stats/mastery`、`/category-mastery` **完全不受影响**。
4. `wb_review_card.knowledge_point`（VARCHAR 500）属 schema/实体漂移（列在 DB、实体未映射）→ 不把它当载体，但可解析其文本作为 AUTO 候选来源。

---

## 3. 推荐映射表结构（新增，遵循项目规范：逻辑外键 + `created_time/update_time/deleted`）

表名：`resource_knowledge_mapping`

```
id            BIGINT 自增
resource_type VARCHAR        -- QUESTION/CODE/MISTAKE/REVIEW/RECALL/FLASHARD/DOCUMENT（与事件 resourceType 对齐）
resource_id   BIGINT
knowledge_id  BIGINT         -- → kg_entity.id（仅可学习 type）
source        VARCHAR        -- MANUAL / AUTO / AI / IMPORT
confidence    DECIMAL(4,3)   -- 0~1，全项目统一
status        VARCHAR        -- ACCEPTED / PENDING / REJECTED
created_time  DATETIME
update_time   DATETIME
deleted       INT DEFAULT 0
```

- 唯一约束：`uk_res_know(resource_type, resource_id, knowledge_id)`（防重复映射）
- 索引：`idx_rkm_knowledge(knowledge_id)`、`idx_rkm_status(source, status)`
- **单表 + status 统一“候选/正式”**（不强行拆两张表）

---

## 4. 自动匹配策略（v1 不调 LLM，可解释，关键词/名称/标签/分类匹配）

优先级（严格按总控要求）：

1. **显式映射**（MANUAL/AI/IMPORT，confidence≥阈值且 status=ACCEPTED）→ 直接使用，**绝不**扩散到同分类其他知识点。
2. **最佳努力自动链接**（AUTO）：从资源 `title/content/tags` 抽取词项，对**可学习类型**的 kg_entity 做 name/description 包含匹配打分；
   - `confidence ≥ AUTO_THRESHOLD`（默认 0.6）→ `status=ACCEPTED`
   - `0.3 ≤ confidence < 0.6` → `status=PENDING`（待 AI/人工确认，不进 mastery）
   - `< 0.3` → 丢弃
3. **分类仅作极低置信兜底**（confidence=0.20，可配置）：**仅当**该 `categoryId` 下存在与资源标题共享词元的 kg_entity 时才连；**绝不** `categoryId → 该分类全部 kg_entity`（杜绝污染）。兜底仍无匹配 → 跳过，记诊断。

confidence 取值（集中放 `MasteryConfig`，非魔法数）：
`MANUAL=1.0 / AI=0.92 / AUTO高=0.85 / AUTO中=0.60 / CATEGORY_FALLBACK=0.20`

---

## 5. knowledge_mastery 数据模型（新增）

表名：`knowledge_mastery`

```
id                    BIGINT 自增
user_id               BIGINT         -- → sys_user.id
knowledge_id          BIGINT         -- → kg_entity.id（仅可学习 type）
mastery_score         INT            -- 0~100
confidence_score      INT            -- 0~100
learning_status       VARCHAR        -- NOT_STARTED/LEARNING/WEAK/MASTERED/REVIEW_REQUIRED
correct_count         INT DEFAULT 0
wrong_count           INT DEFAULT 0
attempt_count         INT DEFAULT 0
review_count          INT DEFAULT 0
recall_count          INT DEFAULT 0
recall_avg_score      INT DEFAULT 0
coding_attempt_count  INT DEFAULT 0
coding_pass_count     INT DEFAULT 0
mistake_count         INT DEFAULT 0
mistake_mastered      INT DEFAULT 0
consecutive_correct   INT DEFAULT 0
consecutive_wrong     INT DEFAULT 0
last_learned_at       DATETIME
last_reviewed_at      DATETIME
last_assessed_at      DATETIME
next_review_at        DATETIME
forgetting_risk       INT            -- 0~100
created_time          DATETIME
update_time           DATETIME
deleted               INT DEFAULT 0
```

索引（按实际查询，不滥建）：
- `uk_user_knowledge(user_id, knowledge_id)`（唯一）
- `idx_km_status(user_id, learning_status)`
- `idx_km_risk(user_id, forgetting_risk)`
- `idx_km_next(user_id, next_review_at)`

---

## 6. Mastery 算法（可解释加权，第一版无 ML）

### 6.1 多信号（按知识点、按用户）
- **Quiz**：`acc = (correct + α) / (correct + wrong + 2α)`（α=1 拉普拉斯平滑，解决稀疏）
- **Coding**：`passRate = (codingPass + α) / (codingAttempt + 2α)`（权重高于 quiz）
- **Recall**：`recallAvgScore / 100`
- **Review**：由 SM-2 `repetitions / lapseCount / nextReviewTime` 推导稳定度
- **Mistake**：`mastered / (mastered + pending)`
- **Learning**：该 kg_entity 所属 doc/category 有阅读/章节完成

### 6.2 合成（默认权重，可配置 `MasteryConfig.WEIGHTS`）
`Quiz 25% / Coding 25% / Recall 20% / Review 10% / Mistake 10% / Learning 10%`

### 6.3 时间衰减
`effective = mastery × decay(daysSinceReview)`（30 天未复习衰减至 ~0.7，可配置）

### 6.4 冷启动（§18）
无任何信号 → `learning_status = NOT_STARTED`，**不显示 0%**，前端显示“未学习”（区分“未知”与“不会”）。

### 6.5 Confidence（§20）
`conf = N / (N + K) × 100`（K=10 样本饱和）。一次答对 → mastery 可能 80 但 confidence≈9%，明确“样本不足”。

### 6.6 Forgetting Risk（§22，0~100）
`risk = clamp(daysSinceReview/30 × 60 + lapseWeight + (100 − mastery) × 0.2, 0, 100)`（可调）

### 6.7 learning_status
`NOT_STARTED / LEARNING / WEAK(<60) / MASTERED(≥80 & conf≥50 & risk<60) / REVIEW_REQUIRED(risk≥70 或 nextReview 已过)`

### 6.8 Weakness 类型（Service 层诊断，不强制建表）
`LOW_MASTERY / HIGH_ERROR_RATE / FORGETTING_RISK / LOW_CONFIDENCE / CODING_WEAK / RECALL_WEAK / PREREQUISITE_WEAK`

---

## 7. 事件驱动架构（沿用已确认决策）

`LearningEventServiceImpl.doRecord()` 在 `this.save(event)` 之后调用 `masteryProcessor.process(event)`（独立 try/catch，失败仅告警、不阻断业务）。职责分层：

```
LearningEventServiceImpl.record()
        ↓ 保存事件
ResourceKnowledgeService  → 解析 resourceType+resourceId → 有效 knowledge_id[]
        ↓
MasteryEngine             → 按 LearningEventType 转 MasterySignal(knowledgeId, signalType, strength, confidence, source, eventId)
        ↓
KnowledgeMasteryService  → 重算该知识点整行（计数器 + mastery/confidence/risk/status）
        ↓
knowledge_mastery
```

- **算法不写在 record() 里**（`MasteryEngine` 负责各事件计算逻辑）。
- **幂等**：`learning_event.id` 唯一且追加只写；`MasteryEngine` 对每个知识点**整行重算**（从业务表经映射聚合），天然幂等，不重复累加（§49）。
- **无 mapping**：事件保存、跳过 mastery、可观察日志（§30/§51）。
- **失败策略**：同步 `REQUIRES_NEW`（复用 Phase 1 机制），不引入 MQ。

---

## 8. API 设计（新增，统一 `Result<T>`，前端只拿 mastery/confidence/status/risk）

`KnowledgeMasteryController` `@RequestMapping("/api/learning/mastery")`：
- `GET /` —— 整体 + 按分类分组的掌握度
- `GET /weak` —— 薄弱知识点
- `GET /review-required` —— 需复习知识点
- `GET /{knowledgeId}` —— 知识点详情 + `getMasteryExplanation()` 结构化解释
- `GET /diagnostics` —— 缺映射事件数（可观察性，§30）
- `POST /recalculate` —— 重算当前用户（算法升级用）

> **兼容保证**：不改动 `/stats/mastery`（`MasteryDistributionVO`）、`/category-mastery`（`CategoryMasteryVO`）返回结构（§56）。

---

## 9. 前端集成（§34–§38）

- 复用 `MasteryDashboard.vue`：在现有汇总下方新增“我的知识点掌握度”分区（按分类分组进度条）。
- `KnowledgeGraph.vue`：节点叠加 `mastery_score`/状态色（复用 `--kb-*` token），点击节点弹知识点详情（掌握度/置信度/遗忘风险/信号明细）。
- 新建 `KnowledgeMasteryDetail.vue`（详情抽屉/页）。
- `frontend/src/api/learning.ts` 新增 `knowledgeMastery()` 等方法，复用 `apiGet`。

---

## 10. 历史数据迁移方案

`ResourceKnowledgeBackfillService`（一次性/可重复）：
扫描存量 quiz/code/mistake/review/recall/flashcard/doc 行 → 走同一 AUTO 匹配逻辑生成映射（已存在则跳过，幂等）→ 触发 `recalculateUserMastery`。
**不修改任何原表**，仅在 `resource_knowledge_mapping` + `knowledge_mastery` 落盘。

---

## 11. 已确认的关键决策（回顾）

| # | 决策点 | 结论 |
|---|--------|------|
| q-0 | 知识载体 | `kg_entity.id` 为知识点载体；仅 `CONCEPT/TECHNIQUE/TERM/PRINCIPLE`（TOOL 可配置）进入 Mastery；不新建 knowledge_point 表；不用 doc_category 作主载体 |
| q-1 | 资源→知识点映射 | 显式表 `resource_knowledge_mapping` + 最佳努力自动链接；优先级 显式 > AUTO > 分类极低置信兜底（绝不分类扩散）；不调 LLM；支持多对多；source+confidence+status |
| q-2 | 事件驱动 | 集中在 `LearningEventServiceImpl.record()` 触发；`record()` 只负责持久化+触发，`MasteryEngine` 负责算法，`ResourceKnowledgeService` 负责解析，`KnowledgeMasteryService` 负责领域逻辑 |

---

## 12. 待确认后执行：实现任务拆解（不在此阶段动手）

1. 新增 `knowledge_mastery` + `resource_knowledge_mapping` 两表（H2 schema / MySQL schema / MySQL 幂等迁移脚本 `V2026xxxx__knowledge_mastery.sql`）
2. 实体 + Mapper：`KnowledgeMastery` / `ResourceKnowledgeMapping`
3. `MasteryConfig`（权重/阈值常量）
4. `ResourceKnowledgeService`（解析 + AUTO 匹配 + 回填）
5. `MasteryEngine`（事件→MasterySignal→重算整行）
6. `KnowledgeMasteryService`（查询/薄弱/需复习/详情/解释/重算）
7. `KnowledgeMasteryController` + 前端 API
8. `LearningEventServiceImpl.record()` 接入 `masteryProcessor.process(event)`
9. 前端：`MasteryDashboard` 接入、`KnowledgeGraph` 加掌握度、`KnowledgeMasteryDetail.vue`
10. 后端编译 + 前端 build + H2 运行时端到端验证（冷启动/连续正确/连续错误/编码/回忆/复习/时间衰减/重复事件/多知识点/缺映射）
11. 更新 `项目架构扫描与二次开发分析.md`（Phase 2 = DONE）+ 本文档归档
12. 本地 commit（不推送远程）

---

## 13. 已知风险 / 技术债（实现时关注）

- dev/H2 冷库 kg_entity 可能为空 → 首轮自动链接可能全跳过；需文档分析或回填种子才有数据。
- `wb_review_card.knowledge_point` schema/实体漂移，不纳入主线，仅作 AUTO 候选可选来源。
- 权重/阈值为 v1 默认，需在真实数据上校准（提供 `MasteryConfig` 集中调整）。
- 自动匹配为关键词级，长尾召回有限；AI 补链作为后续增强（架构已预留扩展点）。

---

## 14. 实现完成记录（更新日志）

**状态：Phase 2-B 已落地（2026-08-29）。** 以下为相对于 §1–§13 设计的最终实现事实，便于后续维护与审计。

### 14.1 新增 / 修改文件清单

**后端（Java / Spring Boot 3.2）**
- 新增实体：`KnowledgeMastery.java`、`ResourceKnowledgeMapping.java`
- 新增 Mapper：`KnowledgeMasteryMapper.java`、`ResourceKnowledgeMappingMapper.java`
- 新增算法 / 配置：`mastery/MasteryConfig.java`、`mastery/SignalType.java`、`mastery/MasterySignal.java`、`mastery/MasteryComputation.java`
- 新增服务：`ResourceKnowledgeService.java`(接口) + `ResourceKnowledgeServiceImpl.java`、`KnowledgeMasteryService.java`(接口) + `KnowledgeMasteryServiceImpl.java`、`MasteryEngine.java`(impl)、`ResourceKnowledgeBackfillService.java`
- 新增 VO：`KnowledgeMasteryVO.java`、`KnowledgeMasteryDetailVO.java`、`SignalContributionVO.java`、`MasteryDiagnosticsVO.java`
- 新增控制器：`KnowledgeMasteryController.java`（`/api/learning/mastery` 组）
- 修改：`LearningEventServiceImpl.record()` 接入 `knowledgeMasteryService.processEvent(event)`（仅持久化 + 触发，算法不在 record 内）；合并修复了此前遗留的 `lombok.RequiredArgsConstructor` 与 `MasteryEngine` 包路径两处编译错误
- 测试：`MasteryEngineIntegrationTest.java`（@SpringBootTest + H2 真实库，19 场景全绿）

**Schema**
- `db/h2/schema.sql`、`db/mysql/schema.sql`：新增 `resource_knowledge_mapping`、`knowledge_mastery` 两套表（含逻辑外键索引；列名 `create_time` 与 `BaseEntity` 对齐）
- `db/mysql/migration/V20260830__knowledge_mastery_engine.sql`：存量 MySQL 库幂等补表脚本（`CREATE TABLE IF NOT EXISTS`）

**前端（Vue3 + TS + Vite）**
- `src/api/types.ts`：新增 `MasteryStatus`、`SignalContributionVO`、`KnowledgeMasteryVO`、`KnowledgeMasteryDetailVO`、`MasteryDiagnosticsVO`
- `src/api/learning.ts`：新增 `knowledgeMastery / weak / reviewRequired / detail / diagnostics / recalculate` 6 个 API
- `src/router/index.ts`：新增路由 `/learning/knowledge-mastery/:id`
- `src/views/KnowledgeMasteryDetail.vue`：知识点掌握度详情页（复用 `--kb-*` 令牌）
- `src/views/MasteryDashboard.vue`：新增「我的知识点掌握度」区块（列表 + 重新计算入口）
- `src/views/KnowledgeGraph.vue`：知识图谱节点叠加掌握度状态色 + 分数 + 查看详情入口

### 14.2 端点（6 个，全部当前用户隔离）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/learning/mastery` | 我的知识点掌握度总览 |
| GET | `/api/learning/mastery/weak` | 薄弱（WEAK） |
| GET | `/api/learning/mastery/review-required` | 需复习（REVIEW_REQUIRED） |
| GET | `/api/learning/mastery/{knowledgeId}` | 详情 + 可解释说明 |
| GET | `/api/learning/mastery/diagnostics` | 引擎可观察性诊断 |
| POST | `/api/learning/mastery/recalculate` | 构建映射并重算我的掌握度 |

兼容端点 `GET /api/learning/stats/mastery`、`GET /api/learning/category-mastery`（LearningController）保持不变。

### 14.3 与设计的偏差 / 实现要点

- **Schema 列名 Bug（已修）**：设计初稿两张新表误用 `created_time`，与 `BaseEntity.createTime` 映射的 `create_time` 不符，会导致插入/自动填充失败。已在 `db/h2/schema.sql`、`db/mysql/schema.sql` 及迁移脚本中统一为 `create_time`。
- **拉普拉斯平滑**：QUIZ/CODING/MISTAKE 正确率均用 `(x+α)/(n+2α)`（α=1）。单样本全对 = 67、单样本全错 = 33、10 次全对 = 92，而非 100；测试断言据此校正（原断言 100 为错误预期）。
- **多信号归一化**：mastery = Σ(wᵢ·strengthᵢ)/Σwᵢ×100，缺失信号不计入分母（避免被稀释）。
- **状态机**：NOT_STARTED / LEARNING / WEAK(<60) / MASTERED(≥80 且 conf≥50 且 risk<60) / REVIEW_REQUIRED(risk≥70 或 nextReviewAt 已过)。
- **幂等**：`recalc` 整行重算；无信号则删除旧行；`recalculateUser` 经 `buildAllMappings` + 逐知识点重算。
- **事务**：`recalc` 用 `REQUIRES_NEW` 独立事务，事件触发失败仅告警不阻断主业务。

### 14.4 验证结论

- 后端集成测试 **19/19 通过**（场景覆盖：单信号 QUIZ/CODING/DOC/REVIEW/MISTAKE/RECALL、多信号加权、状态机 WEAK/MASTERED/REVIEW_REQUIRED、置信度、幂等、冷启动跳过、诊断、详情、按状态列表）。
- 前端 `vue-tsc -b` 类型检查：Phase 2-B 相关文件 **零类型错误**；仓库既有 `types.ts` 中 `CategoryVO` 重复声明冲突（lines 159/163/1925/1928）为本次改动之前已存在的问题，与本次模块无关。
- 未执行 `git commit` / `git push`（按约定仅输出 `git diff` / `git status`）。

### 14.5 后续建议

- 真实数据校准权重/阈值（集中在 `MasteryConfig`）。
- AI 补链 / 长尾召回增强（架构已预留扩展点）。
- 文档分析或回填种子后，首轮自动链接才有数据（冷库 kg_entity 为空时全跳过，符合预期）。
