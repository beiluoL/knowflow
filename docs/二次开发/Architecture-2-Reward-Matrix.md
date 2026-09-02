# Architecture-2 · Reward Matrix（事件-奖励矩阵）

> 事件真实性依据 `LearningEventType` 枚举（20 个）与代码内调用点统计（grep 全量 2026-08-30）。
> 幂等依据：所有事件均为「业务成功后的追加记录」（learning_event 是事实层，绝不替代业务表）。
> XP/Coin 建议值仅作 MVP 默认（RewardRule 静态配置，可调）。

| Event（LearningEventType） | 真实存在 | 有效学习 | XP 建议 | Coin 建议 | 每日上限 | 幂等依据 |
| --- | --- | --- | ---: | ---: | ---: | --- |
| CHAPTER_COMPLETE | ✅（调用点 1） | ✅ | 30 | 5 | 无 | resourceId=chapterId（一次章节只完成一次） |
| PATH_COMPLETED | ✅（1） | ✅ | 120 | 20 | 无 | resourceId=pathId（G-CERT-01 已幂等） |
| QUESTION_ANSWERED | ✅（4） | ✅ | 5 | 1 | 50 题/日 | resourceId=questionId + metadata.sessionId；同题同日只奖励一次 |
| MISTAKE_REVIEW（错题复习完成） | ✅（2） | ✅ | 8 | 2 | 30 次/日 | resourceId=mistakeId |
| FLASHCARD_REVIEWED | ✅（3） | ✅ | 4 | 1 | 60 张/日 | resourceId=reviewCardId + 评分时间窗 |
| RECALL_COMPLETED | ✅（1） | ✅ | 15 | 3 | 10 次/日 | resourceId=recallSessionId |
| CODE_SUBMITTED（通过） | ✅（2） | ✅ | 10 | 2 | 20 次/日 | resourceId=codeQuestionId，仅 metadata.passed=true 奖励 |
| LEARNING_TASK_COMPLETE | ✅（2） | ✅ | 10 | 2 | 无 | resourceId=taskId |
| CHECK_IN | ✅（1） | ✅ | 10 | 2 | 1 次/日（自然日唯一） | user_check_in 唯一（日期唯一约束） |
| DOCUMENT_READ | ✅（1） | ⚠️ 边际 | 0 | 0 | — | 阅读不等于有效学习，不奖励（§15） |
| CHAPTER_START | ✅（1） | ❌ | 0 | 0 | — | 开始≠完成 |
| QUESTION_CORRECT / QUESTION_WRONG | 历史合并进 QUESTION_ANSWERED（不单独落库） | — | 0 | 0 | — | 不单独奖励 |
| CODE_PASSED / CODE_FAILED | 历史合并进 CODE_SUBMITTED | — | 0 | 0 | — | 不单独奖励 |
| AI_CHAT | ✅（2） | ❌ | 0 | 0 | — | 对话≠学习成果（防刷） |
| KNOWLEDGE_VIEWED | ✅（2） | ❌ | 0 | 0 | — | 页面查看不奖励（§15 核心原则） |
| MISTAKE（错题归集） | ✅（4） | ❌（惩罚信号） | 0 | 0 | — | 错题本身不奖励，复习完成才奖励 |

## 防刷规则（第一版 Anti-Abuse，§62-64）

1. **页面/查看/搜索/刷新 → 一律 0 奖励**（KNOWLEDGE_VIEWED、AI_CHAT、DOCUMENT_READ 不奖励）。
2. **同源幂等**：`sourceId`（resourceId）唯一键 + 日期维度，重复事件只入账一次。
3. **每日上限**：答题/复习/回忆/代码等高频事件配 `dailyLimit`（上表），按 `user_id + event_type + 日期` 计数。
4. **重复作答**：同一 `questionId` 同日只奖一次（metadata 含 sessionId 也不重复发）。
5. **奖励只在业务成功路径发**：事件由业务 Service 在成功后调用 `record()`，前端无任何 POST 奖励接口（§65-67）。

## 事件写入调用点统计（证据）

```
QUESTION_ANSWERED ×4   MISTAKE ×4   FLASHCARD_REVIEWED ×3   MISTAKE_REVIEW ×2
LEARNING_TASK_COMPLETE ×2  KNOWLEDGE_VIEWED ×2  CODE_SUBMITTED ×2  AI_CHAT ×2
RECALL_COMPLETED ×1   PATH_COMPLETED ×1   DOCUMENT_READ ×1   CHECK_IN ×1
CHAPTER_START ×1      CHAPTER_COMPLETE ×1
```
