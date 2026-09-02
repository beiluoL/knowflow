# Phase UI-7 执行报告 — Flashcard / Review

> **执行日期**：2026-08-30
> **范围**：仅 `frontend/src/views/FlashCardsHub.vue`。未修改后端 / SRS / KnowledgeMastery / LearningEvent
> **未 commit、未 push**；未执行 `git reset` / `git clean` / `git checkout .`
> **配套文档**：`Phase-UI-7-Flashcard-Analysis.md`

---

## 1. Phase UI-7 目标

统一 Review 体验并确认 SRS 真实闭环。**绝对不重造第二套 SRS**（§三）。

## 2-5. 修改前/后 Flashcard 与 Review 架构

**修改前**：FlashCardsHub 已具备完整复习流（翻卡→评分→`reviewFlashcard`→后端 SRS），但评分反馈**伪造下次复习时间**。
**修改后**：时间不再伪造；其余架构保持（SRS 唯一入口 = Phase 0 的 `SrsService`，未新建）。

## 6. 当前 SRS 唯一入口

`backend SrsService/SrsServiceImpl`（Phase 0 收敛产物）——复习评分全部走它，**前端零 SM-2 计算**（§29/§三十守）。

## 7-8. Flashcard / Review Queue 数据来源

| 项 | API | 真实 |
|---|---|---|
| 闪卡列表 | `GET /learning/flashcards`、`/learning/my/flashcards` | ✅ |
| 到期队列 | `GET /learning/flashcards/due` | ✅ |
| 评分 | `POST /learning/flashcards/{id}/review?quality=N` | ✅ |
| AI 生成 | `POST /learning/my/flashcards/generate` | ✅ |
| 文档生成 | `docsApi.generateFlashcards(docId)` | ✅ |

## 9. Review Item 类型

沿用现有 `FlashcardVO`（未新建类型，避免重复建模 §13）。

## 10-12. Review Page / Flashcard Page / Rating

均沿用 FlashCardsHub 既有实现（翻卡、4 档评分、进度 X/Y），未重建。

## 13-16. SRS 更新 / Review Log / LearningEvent / Mastery 联动

全部由**后端真实完成**（SrsService 更新 interval/nextReviewAt → Review Log → LearningEvent → Mastery），前端只提交 `quality`，**未在浏览器端计算任何 SRS 逻辑**。

## 17. DocDetail → Flashcard

真实链路 `doc → docsApi.generateFlashcards(docId) → 闪卡 → 复习` 保持正常（未改动）。

## 18. Chat → Flashcard 当前限制

沿用 UI-6 结论：Chat 无 `docId/knowledgeId` 元数据，**不伪造"AI Chat 生成闪卡"按钮**（§七十六）。

## 19. AI Flashcard

`/learning/my/flashcards/generate` 真实存在，FlashCardsHub 的"AI 生成并保存"沿用。

## 20-23. 组件 / API / Backend 改动

| 项 | 状态 |
|---|---|
| 新增组件 | **0** |
| 修改组件 | `FlashCardsHub.vue`（1 处文案） |
| 修改 API | **0** |
| 修改 Backend | **0** |

## 24-30. Loading / Empty / Error / Mobile / Dark / A11y / Performance

未改动相关逻辑（沿用既有实现），无回归；修改内容为纯文案，令牌化。

## 31. TypeScript / 32. Build / 33. Test

| 项 | 结果 |
|---|---|
| `npx vite build` | ✅ 通过（4.05s） |
| `vue-tsc -b` | ⚠️ **74**（修改前 74，**持平**；改动文件零错误） |
| Lint / Test | 项目无脚本，未引入 |

## 34. 遗留问题

| # | 问题 |
|---|---|
| 1 | 复习会话无显式「完成」屏（到最后一张直接停）——需模板级结束态改造，建议下阶段处理 |
| 2 | 不展示"为何此刻复习"（到期/新卡原因）——后端 `due` 接口未回传原因字段，前端无法如实展示 |
| 3 | 未实机验证（翻卡→评分→SRS 建议真机核对） |

## 35. Backend Gaps

| Gap | 说明 |
|---|---|
| `reviewFlashcard` 返回 void | 前端无法展示真实 `nextReviewAt`；若返回它，可补"下次复习 X 天后"真实反馈（§30） |
| `due` 接口无原因字段 | 无法展示"逾期/今日到期/新卡"分类（§19/§20） |

## 36. 下一阶段建议

1. 后端 `reviewFlashcard` 返回 `nextReviewAt`（最小改动）→ 前端展示真实下次复习时间
2. 复习完成屏：最后一张评分后显示「本轮完成 X 张 + [返回学习中心]」
3. 承接遗留：清零 74 个 TS 错误
4. 之后：Phase UI-8（Quiz / Mistake）

---

## API Matrix（真实）

| 能力 | 前端页面 | API | 后端 Service | 数据表 |
|---|---|---|---|---|
| 闪卡列表 | FlashCardsHub | `GET /learning/flashcards` | FlashcardService | learning_flashcard |
| AI 生成 | FlashCardsHub | `POST /learning/my/flashcards/generate` | FlashcardService | learning_flashcard |
| 到期队列 | FlashCardsHub | `GET /learning/flashcards/due` | FlashcardService | learning_flashcard |
| 复习评分 | FlashCardsHub | `POST /learning/flashcards/{id}/review` | **SrsService**（Phase 0 唯一入口） | learning_flashcard + review log |
| 文档生成 | DocDetail | `POST /docs/{id}/generate-flashcards` | FlashcardService | learning_flashcard |

## 验收清单（§一百二十三）

- [x] SRS 唯一入口（SrsService）未新建第二套
- [x] 评分 → 后端 SRS → Log → Event → Mastery（真实链路）
- [x] **不伪造时间**（修复"明天再复习"）
- [x] 无 Mock / 无假 API
- [x] Build ✓ / TypeScript 零新增
- ⚠️ 未做：完成屏（遗留 #1）、"为何复习"原因（后端 Gap）

---

**Phase UI-7 已完成。未 commit、未 push。等待你的下一步指令。**
