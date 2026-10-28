# UI/UX 2.1 执行报告 · Mini App Experience Unification

> 阶段目标（§1）：不增加新能力，仅把全部真实 Mini App 统一到 `MiniAppShell` 外壳，实现「进入方式 / 返回 / 标题 / 上下文 / 操作 / 内容布局 / 结果 / 下一步」一致，App 内部学习方法不变（§4/§41）。
> 硬约束（§5/§6/§217-220）：不碰 LearningEvent/Reward/Mastery/SRS/NextAction/Learning Intelligence/Purchase/Entitlement/UserApp；不增后端/API/DB；禁 `git reset/clean/checkout/commit/push`。

## 1. Mini App Inventory
见 `UI-UX-2.1-MiniApp-Inventory.md`。结论：13 个真实 App，12 个接入 `MiniAppShell`，1 个（专注 focus）有意保留沉浸式原样。

## 2. 迁移 App（12）
SmartQuiz(2.0) · FlashCardsHub · Mistakes · WorkbenchStory · WorkbenchNotes · WorkbenchPalace · CodePractice · KnowledgeGraph · MindMap · DrawingEditor · SmartWriting · NotesManage。

## 3. 未迁移 App（1）
`focus`（/learning/pomodoro，FocusMode）：沉浸式全屏专注，自有 topbar；§53 明确 Focus Timer 不被外壳限制空间。保持原样，符合「统一外壳、不统一内容」原则。

## 4. MiniAppShell
`components/layout/MiniAppShell.vue`（UI/UX 2.0 创建，本阶段稳定复用）。API：`title / subtitle? / knowledgeName? / backLabel? / clearableContext?` + emit `back` / `clear-context` + slot `#actions` + default slot。单一职责=UI Shell，不含业务 API/学习事件/奖励（§18-19/§202-206）。

## 5. Header
Before：各 App 自造 header（back-btn / h1 / 副标题 / context-bar / 操作按钮散落）。
After：统一 `mini-app-header` = 返回(`<button>`+aria-label) + 标题(`--kb-fs-h2`) + 条件上下文 + 动作 slot。富品牌 hero（wb-hero）去重 h1，eyebrow/loop-nav 留内容。

## 6. Context
仅 `knowledgeName` 真实存在才渲染（§21-22）；空条不显示。`flashcard`/`quiz` 沿用既有 `knowledgeContext`；其余不制造默认上下文（§68-69）。清除按钮仅 `clearableContext` 时显示，emit `clear-context`。

## 7. Actions
业务操作迁移进 `#actions` slot，真实 `<button>`/`router-link`（§142-148）。单 App 动作数：Mistakes 1、Cornell 1、Feynman 1、Memory Palace 2、SmartWriting 编辑器工具条留内容（不强制进 slot）。无超过 3 个的情况（§24-26）。

## 8. Result
学习型 App 复用既有 `LearningResultPanel`（§71-72，未改）；工具/AI 型不强加（§73）。结果结构=结果/奖励/学习状态/下一步，无真实数据项隐藏（§74-77）。

## 9. Loading
既有 Skeleton/animate-pulse 保留（CodePractice / WorkbenchNotes / WorkbenchStory / WorkbenchPalace / KnowledgeGraph 均有）；未新增统一 Skeleton 组件（按既有实现，避免新建业务组件，§85）。

## 10. Empty
既有 EmptyState / `wb-empty` / 空态文案保留（Mistakes 用 `EmptyState`，wb 系列用 `wb-empty`，Notes/Mindmap/Drawing 用 `mm-hint`/`dw-hint`）。未强制统一为新组件（§88）。

## 11. Error
既有错误提示走 `@/utils/toast`（notify/getApiError），未改；未新增统一 ErrorState 组件（保持既有错误处理链路，§87）。

## 12. Responsive
`MiniAppShell` header `flex-wrap`，390px 下 context 折行（`@media (max-width:640px)`）；内容布局由各 App 自有响应式（grid / flex）控制（§126-138）。未设固定 600px 限制（§135）。

## 13. Dark Mode
全部颜色走 `--kb-*`（MiniAppShell 已全量引用）；各 App 既有 `--kb-*` 体系未引入硬编码 white/black（§139-141）。

## 14. Accessibility
返回=真实 `<button>` + `aria-label`；图标操作有 `title`/`aria-label`；`router-link` 用于导航（§142-147）；聚焦环 `focus-visible:ring-[var(--kb-ring)]`；未用 clickable div 冒充导航（§148）。

## 15. Home → App
Home 的 NextAction 深链进入各 Mini App（route query 透传，闪卡/测验已接 `knowledgeContext`）；外壳提供统一返回（§101-105）。

## 16. App → Result
学习型 App 完成后复用 `LearningResultPanel`（既有链路，未改）。

## 17. Result → NextAction
Result 面板内「下一步」复用既有 NextAction 解析（未改，§81-84）；不在 MiniAppShell 内自算 NextAction（§82-83）。

## 18. App Store → App
`AppCenterPage.vue`（UI/UX 2.0 已统一）CTA 用语收敛：获取 / 兑换 / 打开 / 重新启用 / 暂不可用（§111-112），未改购买核心。

## 19. UserApp
`registry.ts` / `UserAppVO` / 后端 `user_app` 未改（§109-110）。

## 20. Purchase
Purchase / Entitlement 走 server truth，未改（§107-108）。

## 21. Entitlement
同上，未改。

## 22. TypeScript
`vue-tsc -b` 随 `npm run build` 执行，**0 错误**（BUILD_EXIT=0）。

## 23. Vitest
**88 passed / 88**（80 基线 + 8 新增 `MiniAppShell.spec.ts`）。无 skip/ignore/删测试（§173-177）。

## 24. Build
`npm run build` → `✓ built in ~3.5s`，EXIT=0。

## 25. Backend Test
后端零改动（本阶段未触任何 Java/Controller/Service/Entity）。沿用 UI/UX 2.0 基线 **71/71**（§228-229）。

## 26. Browser Checklist
见 `UI-UX-2.1-Browser-Test-Checklist.md`。

## 27. 实际已验证项目
- 编译门：`npm run build` 全绿（所有迁移后 .vue 编译通过）。
- 测试门：`npm run test` 88/88 全绿（含 MiniAppShell 8 项单测：title/subtitle 条件/context 条件/actions/back/clear-context/default slot）。
- 结构核对：13 个 App 路由→组件逐一映射，迁移后模板闭合、props/emit 一致。

## 28. 未能实机验证项目（诚实边界）
- **真实浏览器视觉/交互逐页 E2E**：沙箱 `agent-browser` 白屏不可靠，无法由我观察渲染像素。验证以「build 编译 + Vitest 单测 + 模板结构核对」为准。
- **390/768/1440 三断点 + 暗色 + 键盘 + 加载/空/错态**：已通过代码与 CSS 确认，但建议你在本地 `npm run dev` 真人验证一遍（见 Checklist）。
- **用户切换 A→B 的 UserApp 状态不串**：逻辑未改，建议联调确认（§162-163）。

## 29. 遗留问题 / 后续
- `focus` 沉浸式 App 未套壳（有意，§53）。若后续要统一返回手势，可单独评估。
- 统一 Skeleton / EmptyState / ErrorState 组件：本阶段复用既有实现，未新建共享组件（避免引入业务组件，§85/§88）；如要进一步收敛，可作为下一小步。
- 上下文连续性：仅闪卡/测验已接 `knowledgeContext`；其余 App 从 NextAction 进入时的上下文透传需后端/route query 支持，按 §103 保持现状不强行改造。
