# Phase UI-3 · 首页分析报告（Home Analysis）

> 日期：2026-08-30　对象：`frontend/src/views/Home.vue`（1598 行）
> 原则：**不造数、不 mock、不修改学习引擎**，只消费已有 API

---

## 一、当前首页结构（真实扫描）

| 区块 | 行号 | 数据来源 | 真实性 |
|---|---|---|---|
| 欢迎横幅（问候 + 学习时长/文档数/连击） | 3–53 | `userApi.stats()` | ✅ 真实 |
| `今日学习进度` | 54–~128 | `currentCourse`（由 `continueDocs[0]` 推导）+ `masteryPercent` | ⚠️ 部分真实（见 §四） |
| `今日待办与计划` | 131–~186 | `learningApi.tasks()` | ✅ 真实 |
| `为你推荐` | 187–~226 | `learningApi.paths()` | ✅ 真实路径列表 |
| `继续学习` | 227–~266 | `docsApi.recent()` | ✅ 真实 |
| `AI 学习建议` | 267–~306 | **`aiRecs` 静态常量数组（412–417 行）** | ❌ **非 AI，硬编码 4 条** |

---

## 二、当前 API 与数据来源

| API | 方法 | 返回 | 用途 |
|---|---|---|---|
| `userApi.stats()` | `GET` | `UserStatsVO` | 学习时长、文档数、连击天数 |
| `learningApi.tasks()` | `GET` | `LearningTaskVO[]` | 今日待办 |
| `learningApi.paths()` | `GET` | `LearningPathVO[]` | 推荐路径 |
| `docsApi.recent()` | `GET` | `DocVO[]` | 继续学习 |
| `learningApi.chapters(pathId)` | `GET` | 章节 | 计算路径进度（447 行） |

---

## 三、当前 CTA

`dynamicCta`（359 行）三分支：

1. 未登录 或 `studyHours === 0` → 「探索学习路径」/「浏览知识库」
2. `continueDocs.length > 0` → 「继续上次学习」/「查看待办」
3. 默认 → 「进入学习中心」/「创建笔记」

另有一个固定的「专注模式」按钮 → `/learning/focus`。

**问题**：**完全不考虑"待复习"与"薄弱知识点"**，而这两项恰恰是学习闭环的核心。

---

## 四、当前存在的问题

| # | 问题 | 严重度 | 依据 |
|---|---|---|---|
| 1 | **没有"今日待复习"模块** | 🔴 | 全文件无 `review-required` 调用 |
| 2 | **没有"薄弱知识点"模块** | 🔴 | 全文件无 `mastery/weak` 调用 |
| 3 | **`masteryTarget = 200` 是伪造目标** | 🔴 | 399 行硬编码；`masteryPoints` 取的是 `totalFlashcards`，与"掌握度"混为一谈（§16 明令禁止） |
| 4 | **「AI 学习建议」名不副实** | 🟡 | `aiRecs` 是 412–417 行的静态常量，与用户无关（§32） |
| 5 | **CTA 未纳入复习/薄弱** | 🟡 | 见 §三 |
| 6 | 区块顺序未体现"今日行动优先" | 🟡 | 「为你推荐」排在「继续学习」之前 |
| 7 | 各区块无独立 Loading / Error | 🟡 | 整页一次性 `onMounted` 串行加载 |
| 8 | 新用户态未专门设计 | 🟡 | 各区块各自显示 0 / 暂无 |

---

## 五、6 个问题的回答情况

| 问题 | 修改前 | 依据 |
|---|---|---|
| ① 我正在学什么？ | ✅ | `currentCourse`（继续学习文档） |
| ② 今天应该学什么？ | ✅ | 今日待办（`learningApi.tasks()`） |
| ③ 今天还剩什么任务？ | ⚠️ 部分 | 有待办列表，但无"还剩多少"汇总 |
| ④ 我已经学了多少？ | ✅ | 学习时长 / 文档数 / 连击 |
| ⑤ 我的薄弱知识点是什么？ | ❌ **无** | 未调用任何 mastery 接口 |
| ⑥ 今天应该复习什么？ | ❌ **无** | 未调用 `review-required` |

**得分：4 / 6**（审计阶段结论一致）

---

## 六、可复用的真实 API（已确认存在）

| 功能 | API | 前端方法 | 真实存在 |
|---|---|---|---|
| 薄弱知识点 | `GET /learning/mastery/weak` | `learningApi.knowledgeMasteryWeak()` | ✅ |
| 今日待复习 | `GET /learning/mastery/review-required` | `learningApi.knowledgeMasteryReviewRequired()` | ✅ |
| 全部掌握度 | `GET /learning/mastery` | `learningApi.knowledgeMastery()` | ✅ |
| 掌握度分布 | `GET /learning/stats/mastery` | `learningApi.mastery()` | ✅ |
| 今日计划 | `GET /learning/plan/today` | `learningApi.getTodayPlan()` | ✅ |
| 学习报告 | `GET /learning/report?period=` | `learningApi.getReport()` | ✅ |

### `KnowledgeMasteryVO` 字段（可用于展示）

```
knowledgeId, name, type, categoryId, categoryName,
masteryScore, confidenceScore, learningStatus,
forgettingRisk, weaknessTypes[], reason,
lastLearnedAt, lastReviewedAt, lastAssessedAt, nextReviewAt
```

- `categoryName` → 待复习可按分类聚合（§21）
- `masteryScore` → 掌握度百分比（§26）
- `reason` / `weaknessTypes` → 薄弱原因，但**需翻译**，不得直接暴露内部字段（§59/§60）

---

## 七、需要新增的组件

| 组件 | 用途 | 数据 |
|---|---|---|
| `ReviewDueCard.vue` | 今日待复习 | `knowledgeMasteryReviewRequired()` |
| `WeakPointCard.vue` | 薄弱知识点 Top3 | `knowledgeMasteryWeak()` |

其余（进度、待办、继续学习、推荐）沿用 `Home.vue` 现有区块，仅调整顺序与权重，**不重复创建**。

---

## 八、是否需要新增 API

**不需要。** 6 个问题所需数据全部可由上表已有接口覆盖：

- 待复习数量与分类 → `review-required`（返回数组，前端聚合 `categoryName`）
- 薄弱 Top3 → `mastery/weak`（按 `masteryScore` 升序取前 3）

**不新增 Dashboard API，不修改后端**（§五/§六/§五十八）。

---

## 九、改造方向

1. 新增 `ReviewDueCard` / `WeakPointCard`，数据各自治理、各自 Loading/Empty/Error（§67 错误隔离）
2. `Promise.allSettled` 并联加载，单接口失败不影响其他区块（§42/§64）
3. 主 CTA 增加"待复习"分支，优先级：待复习 → 继续学习 → 今日计划 → 学习路径（§14）
4. **移除伪造目标 `masteryTarget = 200`**，改为展示真实数据或明确"未设置目标"（§16）
5. `aiRecs` 更名为中性表述并降低权重，移至页面底部（§32）
6. 重排区块：Hero → 进度+待复习 → 今日任务 → 继续学习+薄弱 → 建议 → 推荐（§33/§37）
7. 新用户态：无数据时给引导 CTA 而非一堆 0（§六十九）
