# Phase UI-7 · Flashcard / Review 分析

> 日期：2026-08-30　对象：`FlashCardsHub.vue`（2135 行）、`ReviewPlan.vue`、`api/learning.ts`

## 当前真实架构

| 层 | 实现 | 状态 |
|---|---|---|
| SRS 唯一入口 | `SrsService/SrsServiceImpl`（Phase 0 已收敛，SM-2 唯一实现） | ✅ 复用，**不新建** |
| 复习 API | `learningApi.reviewFlashcard(id, quality)` → `POST /learning/flashcards/{id}/review?quality=N` | ✅ 真实 |
| 到期队列 | `learningApi.dueFlashcards()` → `GET /learning/flashcards/due` | ✅ 真实 |
| 闪卡列表 | `flashcards()` / `myFlashcards()` / `dueFlashcards()` | ✅ |
| AI 生成 | `generateMyFlashcards()` → `/learning/my/flashcards/generate` | ✅ 真实 |
| DocDetail 生成 | `docsApi.generateFlashcards(docId)` | ✅ 真实链路（UI-5 确认） |
| 复习模式 | FlashCardsHub 内：翻卡 + 4 档评分（unknown/familiar/mastered…）→ reviewFlashcard | ✅ 完整 |
| 进度 | `learnIndex+1 / learnFiltered.length` + 百分比 | ✅ 真实 |

## 复习数据流（真实）

```
开始复习 → dueFlashcards()/myFlashcards() → 翻卡 → 显示答案
  → 评分 → reviewFlashcard(id, quality) → 后端 SrsService 更新 interval/nextReviewAt
  → LearningEvent（MISTAKE_REVIEW/闪卡复习，后端落库） → 下一张
```

## 发现的问题

| # | 问题 | 处理 |
|---|---|---|
| 1 | 评分反馈写死「**已记录 · 明天再复习这张**」——API 返回 void，无 nextReviewAt，是**前端猜时间**（§31 禁止） | ✅ **已改为如实文案**「已记录 · 会在复习计划中安排再练」 |
| 2 | 复习会话无显式「完成」屏（到最后一张直接停） | ⚠️ 遗留：需模板级结束态改造，本阶段未做（避免无实机验证大改） |
| 3 | 不展示"为何此刻复习"（到期/新卡） | ⚠️ 遗留：后端 due 接口未回传原因字段 |

## 结论

SRS 收敛（Phase 0）已到位：评分 → 后端 SM-2 → Review Log → LearningEvent → Mastery 联动全部真实。
本阶段前端只需**不再伪造时间**，并如实记录无法在前端补足的能力。
