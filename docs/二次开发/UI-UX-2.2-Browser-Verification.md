# UI/UX 2.2 · Browser Verification（真实浏览器验收清单）

> 以 `UI-UX-2.1-Browser-Test-Checklist.md` 为基础扩展。
> 图例：✅ Pass（已验证）  ❌ Fail（失败）  ⚠️ Not verified（代码/构建/单测已验证，需真人浏览器走查）
> **诚实声明**：本沙箱 `agent-browser` 不可靠（白屏），以下「✅」均来自 **源码核对 + TypeScript 0 错误 + Vite Build PASS + Vitest 105/105**；凡涉及像素/交互/暗色/键盘的真实观感均标 **⚠️**，须由你在本地 `npm run dev` 真人验收。规范 §248「不能真实观察时明确标记 ⚠️ Not verified」。

## 0. 环境

- [✅] `npm run dev` 可启动（前端 5173）；后端 8080（admin/admin123）
- [✅] `vitest run` = 105 passed
- [✅] `vue-tsc` = 0 errors；`vite build` = PASS
- [⚠️] 像素级视觉由你本地确认

## 1. 全局导航（5 一级）

- [✅] 一级导航 = 首页 / 学习 / 知识 / AI / 应用（`navigation.spec.ts` 断言 = 5）
- [⚠️] 桌面侧栏折叠/展开、移动端底部 4 Tab 真人确认
- [⚠️] 面包屑与高亮一致

## 2. 主链路（Home → NextAction → App → Complete → Result → NextAction）

- [✅] Home 突出 NextAction（代码确认，未删卡片）
- [✅] Quiz 完成 → LearningResultPanel（完成/结果/奖励/状态/下一步）
- [✅] Flashcard 完成 → LearningResultPanel
- [⚠️] 真实点击「开始练习 → 判分 → 结果 → 下一步」是否 ≤1 次主点击（规范 §155-156）
- [⚠️] Result 页「我完成了什么 / 系统记录了什么 / 下一步」是否清晰（规范 §65-68）

## 3. 13 个 Mini App 逐页

| App | 接壳 | 上下文 | 首屏主任务 | 状态组件 | 真人验收 |
| --- | --- | --- | --- | --- | --- |
| Quiz | ✅ | ✅ knowledgeId | 开始答题 | EmptyState | ⚠️ |
| Flashcard | ✅ | ✅ knowledgeId | 开始复习 | 内联 | ⚠️ |
| Mistake | ✅ | —（无 API 支持） | 处理错题 | EmptyState+ErrorState | ⚠️ |
| Feynman | ✅ | — | 解释知识 | 内联 | ⚠️ |
| Cornell | ✅ | — | 记笔记 | 内联 | ⚠️ |
| Memory Palace | ✅ | — | 复习 | SkeletonList | ⚠️ |
| Knowledge Graph | ✅ | ✅ 节点 knowledgeId | 探索依赖 | 内联 | ⚠️ |
| Coding | ✅ | — | 完成练习 | 内联 | ⚠️ |
| Mindmap | ✅ | — | 画导图 | 内联 | ⚠️ |
| Drawing | ✅ | — | 绘图 | 内联 | ⚠️ |
| SmartWriting | ✅ | — | 写作 | 内联 | ⚠️ |
| Notes | ✅ | — | 管理笔记 | SkeletonList | ⚠️ |
| Focus | 保留沉浸式 | 不适用 | 专注计时 | 沉浸式自管 | ⚠️ |

## 4. 上下文连续性（Context Continuity）

- [✅] NextAction 薄弱点 → Flashcard/Quiz 携带 knowledgeId（`buildMiniAppContextQuery` 单测覆盖）
- [✅] Flashcard/Quiz 经 `route.query.knowledgeId` 显示「正在学习：X」（源码确认）
- [✅] clear-context → UI 同步隐藏（MiniAppShell.spec 覆盖）
- [✅] 刷新保持（route.query 持久）
- [⚠️] 真人确认：从 Knowledge 详情进 Quiz/Flashcard 是否显示正确知识名
- [✅] 不支持 knowledgeId 的 App 不显示「正在学习：X」（不伪造）

## 5. App Store / 经济

- [✅] 未安装付费 → 兑换（Coin 足够转 `/apps?redeem=`）
- [✅] Coin 不足 → 「去学习赚取」回 `/learning/center`（非钱包）
- [✅] 购买后自动打开（AppCenterPage `?redeem=` 深链）
- [⚠️] 真人购买闭环（需后端 + 真实 Coin）

## 6. User Switching（A→B 隔离）

- [✅] stores/apps.ts 登出重置 + 后端 user_app 真源（`knowflow:apps:<uid>` 已 user-scoped，规范 §139-140）
- [✅] wallet/entitlement 后端 user-scoped
- [⚠️] 真人：User A 启用 App → 退出 → User B 登录，确认互不串数据

## 7. 响应式 390 / 768 / 1440

- [⚠️] 390：无横向滚动、CTA 可点、Header 折行（MiniAppShell `@media(max-width:640px)` context 折行）
- [⚠️] 768：侧栏/内容
- [⚠️️] 1440：内容不窄
- [✅] Canvas App（Knowledge Graph / Mindmap / Drawing / Chat）允许特殊布局（规范 §196-197）

## 8. Dark Mode

- [✅] SkeletonList 硬编码 gray-100/200 → `--kb-muted`/`--kb-border`（本阶段修复，暗色跟随）
- [✅] 共享状态组件 / MiniAppShell / Result / CTA 全 `--kb-*`
- [⚠️] 真人确认：Home/Learning/Knowledge/AI/Apps/Mini Apps 无白底泄漏/黑字不可见/边框消失

## 9. Accessibility

- [✅] MiniAppShell back/context-clear 为真实 `<button>` + aria-label
- [✅] CTA 用 `Button` 组件（`<button>`）
- [✅] 图标按钮（na-dismiss 等）带 aria-label/title
- [⚠️️] 全站 Tab/Enter/Esc 焦点环真人确认；无 `div @click` 伪装按钮残留

## 10. Loading / Empty / Error 一致性

- [✅] 共享组件 `EmptyState`/`ErrorState`/`StateView`/`SkeletonList` 已存在并被部分采用
- [✅] ErrorState 不暴露 500/异常（默认「加载失败」+ 重试）
- [⚠️] 各 App 内联空态形态统一度（采用率不均衡，留后续批次）

## 11. 回归

- [✅] Vitest 88 → 105（新增 17：StateComponents 11 / context-query 3 / navigation 3）
- [✅] TypeScript = 0
- [✅] Build = PASS
- [✅] Backend = 71/71（本阶段零后端改动）
