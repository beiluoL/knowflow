# Phase UI-8 · Quiz / Mistake 分析

> 日期：2026-08-30　对象：`SmartQuiz.vue`（1310 行）、`Mistakes.vue`（392 行）、`api/quiz.ts`、`api/mistakes.ts`

## 真实能力（全部存在）

| 能力 | 实现 | 状态 |
|---|---|---|
| Quiz 判题 | `api/quiz.ts`（提交判分 `submitOnlineQuiz`，后端判题） | ✅ 真实 |
| 答案反馈 | 正确/错误 + 正确答案 + 解析（`resultOf(q.id)`） | ✅ |
| 题型 | judgeOptions 判断题 + 选择题等（以真实题型为准） | ✅ |
| 错题列表 | `mistakesApi.list(params)` → `GET /mistakes` | ✅ |
| 错题详情 | `mistakesApi.detail(id)` → `GET /mistakes/{id}` | ✅ |
| 标记掌握 | `mistakesApi.markMastered(id)` → `PUT /mistakes/{id}/mastered` | ✅ |
| 统计 | `mistakesApi.stats()` → `GET /mistakes/stats` | ✅ |
| 原题回溯 | Phase 0 已给 `MistakeVO` 补 `questionId/codeQuestionId` + 回溯逻辑 | ✅ |
| 错题采集 | Phase 0：`QuizServiceImpl` 带 questionId、`MistakeServiceImpl.collectCodeMistake` 带 codeQuestionId | ✅ 后端自动，前端**不重复创建**（§三十一/§三十二） |
| 掌握率 | Mistakes.vue 环形进度（真实 mastery 数据） | ✅ |

## 数据流（真实，来自 Phase 0/1 链路）

```
Quiz 提交 → 后端判题 → QuizAnswerRecord → LearningEvent(QUESTION_ANSWERED)
  → 答错自动 learning_mistake（带 questionId 回溯） → KnowledgeMastery
Mistake Hub → detail → 原题回溯 → markMastered → 掌握
```

## 结论

Quiz / Mistake 模块在 Phase 0/1 已完成领域闭环（判题、错题自动采集、原题回溯、掌握标记、统计、事件）。前端页面具备 EmptyState/判分中/答案解析等完整交互。
**本阶段经扫描确认无需伪造任何能力，未做无谓改动**（与 UI-5 同结论）。

## 已知边界

- 普通用户**不可 AI 出题**（`/admin/quiz-questions/ai-generate` 仅管理员）——页面未暴露给普通用户（§十七/§六十守）
- "再次练习"原题级入口依赖 questionId 存在（旧数据无 ID 时用快照，不做假跳转）
