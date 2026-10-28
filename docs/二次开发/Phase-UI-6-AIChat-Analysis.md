# Phase UI-6 · AI Chat 分析（AIChat Analysis）

> 日期：2026-08-30　对象：`frontend/src/views/Chat.vue`（2079+ 行）与 `api/chat.ts`

## 修改前结构（真实扫描）

```
[会话列表]            [聊天区]
  新建对话              用户消息（右侧气泡）
  历史会话              AI 消息（全宽 Markdown，ChatGPT 风格）
                         ├─ Markdown（prose-chat v-html）
                         ├─ 消息操作：赞 / 踩 / 复制
                         ├─ 参考来源（Badge，来自后端 "[id] 标题" 解析）
                         └─ 输入框（多行 + 附件图片 + 发送）
```

## 真实 API 能力（全部确认存在）

| 能力 | API | 状态 |
|---|---|---|
| 会话列表/新建/删除 | `chatApi.conversations() / createConversation() / deleteConversation()` | ✅ |
| 历史消息 | `chatApi.messages(id)` | ✅ |
| 发送 + **SSE 流式** | `chatApi.sendStream(payload, { onToken, onDone, onError })` | ✅ |
| **停止生成** | `AbortController.abort()`（已有 F1 实现） | ✅ |
| 模型列表 | `chatApi.models()` | ✅ |
| 引用来源 | 后端返回 `sources`（Badge 展示） | ✅ |
| AI 摘要/闪卡（文档侧） | `docsApi.generateSummary/generateFlashcards(id)` | ✅（需 docId，Chat 未直接提供） |
| AI 出题（管理侧） | `adminApi` `/admin/quiz-questions/ai-generate` | ⚠️ 仅管理员 |

## 结论

Chat 已具备成熟骨架：流式、停止、会话管理、引用、消息反馈（赞/踩/复制）、附件图片。
**缺的是"AI 回答 → 学习动作"的承接**，本阶段补齐，且只接真实入口。

## 学习动作设计（全部真实，无伪造）

| 动作 | 入口 | 依据 |
|---|---|---|
| 继续学习 | `/learning/center` | 真实路由（UI-4 学习行动中心） |
| 智能测验 | `/learning/quiz` | 真实路由 |
| 复习计划 | `/learning/review` | 真实路由 + `review-required` 真实数据 |
| 掌握度 | `/learning/mastery` | 真实路由 + `mastery/weak` 真实数据 |

**不做**（后端未支撑/上下文不足，§三十三~§三十九）：生成闪卡（Chat 无 docId 上下文）、生成题目（仅管理侧）、加入路径（无对应 API）、主动回忆（无独立入口）。如实隐藏，不造假按钮。
