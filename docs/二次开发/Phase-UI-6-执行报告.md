# Phase UI-6 执行报告 — AI Chat / AI 学习助手

> **执行日期**：2026-08-30
> **范围**：仅 `frontend/src/views/Chat.vue`。未修改后端 / RAG / Agent Runtime / KnowledgeMastery / LearningEvent / SRS
> **未 commit、未 push**；未执行 `git reset` / `git clean` / `git checkout .`
> **配套文档**：`Phase-UI-6-AIChat-Analysis.md`

---

## 1. Phase UI-6 目标

把 AI Chat 从"单纯聊天"升级为"AI 学习助手"：AI 回答完成后提供**真实可用的学习动作**，让用户自然进入 提问→学习→练习→记忆→复习 闭环。**只接真实能力，禁止伪造。**

## 2. 修改前 AI Chat 结构

```
会话列表 │ 聊天区（用户气泡 + AI Markdown + 赞/踩/复制 + 参考来源 + 输入框）
```

能力齐全（流式/停止/会话/引用/附件）但**无学习动作承接**——回答完就结束了。

## 3. 修改后 AI Chat 结构

```
AI 消息
 ├─ Markdown 内容（保留）
 ├─ 参考来源（保留，真实 Badge）
 ├─ 消息操作 赞/踩/复制（保留）
 └─ ★ 学习动作条（新增）：[继续学习] [智能测验] [复习计划] [掌握度]
```

## 4-9. 真实 AI / RAG / Session / Streaming / References / Context

| 项 | 实现 | 状态 |
|---|---|---|
| AI API | `chatApi.sendStream`（SSE） | ✅ 未改动 |
| RAG | 后端检索，返回 `sources` 引用 | ✅ 未改动 |
| Session | `conversations/messages/create/delete` | ✅ 未改动 |
| Streaming | `onToken/onDone/onError` + 打字机 | ✅ 未改动 |
| References | `message.sources` → Badge（真实） | ✅ 未改动 |
| Context | Chat 无文档上下文传递（未伪造） | ⚠️ 如实 |

## 10-12. Session Sidebar / Message / Input

均未改动（已成熟）。新增动作条仅挂接在 `isMessageComplete(index)` 的 AI 消息尾部。

## 13-19. Learning Action Bar

| 动作 | 是否真实 | 前端入口 | 说明 |
|---|---|---|---|
| 继续学习 | ✅ | `/learning/center` | 真实路由 |
| 智能测验 | ✅ | `/learning/quiz` | 真实路由 |
| 复习计划 | ✅ | `/learning/review` | 真实路由 |
| 掌握度 | ✅ | `/learning/mastery` | 真实路由 |

视觉：次级动作条（ghost 风格 chip），1 行不喧宾夺主（§40/§九十）；`aria-label` 齐备（§91）。

## 20. 哪些能力没有后端支持（如实隐藏，不造假按钮）

| 能力 | 原因 |
|---|---|
| 生成闪卡 | Chat 消息无 docId 上下文（`generateFlashcards` 需 docId）——接入会导致"假跳转"（§33/§50 禁止） |
| 生成题目 | 仅管理侧接口（`/admin/quiz-questions/ai-generate`），非用户侧能力 |
| 加入学习路径 | 无"文档→路径"真实 API |
| 主动回忆 | 无独立 Recall 入口 |

**遵循 §三十二~§三十九 的"没有就隐藏"原则。**

## 21-24. 新增/修改组件与 API / Backend

| 项 | 状态 |
|---|---|
| 新增组件 | **0**（动作条内联实现，Chat.vue 未拆——§123 拆组件风险大于收益） |
| 修改组件 | `Chat.vue`（模板 + ~20 行样式） |
| 修改 API | **0** |
| 修改 Backend | **0** |

## 25-28. Loading / Empty / Error / Streaming Error

全部沿用既有实现（`isTyping` 指示器、空态建议、发送失败提示、停止生成 + 截断保留），未改动、未破坏（§19 强制保护项）。

## 29-32. Mobile / Dark / A11y / Performance

- 未改动布局与颜色（无回归）；动作条为 flex-wrap，窄屏自动换行
- 新增样式全部 `--kb-*` 令牌（已扫描无硬编码）
- `aria-label` 覆盖 4 个动作链接；focus-visible ring 齐备
- 未新增任何性能敏感逻辑（无消息级 v-for 变更）

## 33. TypeScript / 34. Build / 35. Test

| 项 | 结果 |
|---|---|
| `npx vite build` | ✅ 通过（3.94s） |
| `vue-tsc -b` | ⚠️ **74**（修改前 74，**持平**；本阶段改动**零新增错误**） |
| Lint / Test | 项目无脚本，未引入 |

## 36. 遗留问题

| # | 问题 |
|---|---|
| 1 | AI 回答**无法直接生成闪卡/题目**（无 docId 上下文）。真正闭环需要后端在 Chat 响应中携带 `knowledgeId/docId`，已列入 Backend Gaps |
| 2 | 动作条对所有 AI 消息统一显示（含闲聊场景）；按 §43 理想做法应按上下文收敛，但需后端意图识别，本阶段不伪造 |
| 3 | 未做实机浏览器验证（SSE 流式/停止生成建议真机核对） |

## 37. Backend Gaps

| Gap | 说明 |
|---|---|
| Chat 响应缺 `knowledgeId`/`docId` 元数据 | 有它才能让"生成闪卡/查看知识"成为真动作；建议后端在消息 VO 增加可选字段 |
| 用户侧 AI 出题接口缺失 | 目前 `ai-generate` 仅管理员可用 |

## 38. 下一阶段建议

1. 后端在 Chat 消息 VO 增加 `docId/knowledgeId`（最小改动）→ 前端即可接入"生成闪卡/查看知识"真动作
2. 实机验证：新建会话 → 提问 → 流式 → 完成 → 动作条 → 跳转 → 返回不丢会话
3. 承接遗留：清零 74 个 TS 错误
4. 之后：Phase UI-7（Flashcard/Review）

---

## AI Action Matrix（§一百五十四，真实结果）

| Action | 是否真实实现 | 前端入口 | Backend API | 成功结果 |
|---|---:|---|---|---|
| 继续学习 | ✅ | `/learning/center` | —（导航） | 进入学习中心 |
| 智能测验 | ✅ | `/learning/quiz` | —（导航） | 进入测验页 |
| 复习计划 | ✅ | `/learning/review` | `review-required` 数据支撑 | 进入复习 |
| 掌握度 | ✅ | `/learning/mastery` | `mastery/weak` 数据支撑 | 进入掌握度 |
| 生成闪卡 | ❌（无 docId 上下文） | — | `docsApi.generateFlashcards` 存在但缺参数 | — |
| 生成题目 | ❌（仅管理侧） | — | `/admin/quiz-questions/ai-generate` | — |
| 加入路径 | ❌（无 API） | — | — | — |
| 主动回忆 | ❌（无入口） | — | — | — |

## 体验验收表（§一百五十五）

| 项目 | 修改前 | 修改后 | 状态 |
|---|---|---:|---:|
| Chat | 成熟 | 成熟 | ✅ |
| Streaming | ✅ | ✅ 未破坏 | ✅ |
| References | ✅ | ✅ | ✅ |
| Context | 无 | 无（如实，未伪造） | ⚠️ |
| Session | ✅ | ✅ | ✅ |
| **Action Bar** | ❌ | ✅（4 个真实动作） | ✅ |
| Flashcard/Quiz 生成 | ❌ | ❌（后端缺上下文，如实隐藏） | ⚠️ |
| Mobile / Dark / A11y | — | 无回归，令牌化 | ✅ |
| Build | — | ✓ | ✅ |
| TypeScript | 74 | **74（零新增）** | ✅ |

---

**Phase UI-6 已完成。未 commit、未 push。等待你的下一步指令。**
