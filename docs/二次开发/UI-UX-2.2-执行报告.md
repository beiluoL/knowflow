# UI/UX 2.2 · 执行报告（Learning Flow Polish）

> 阶段定位：学习主链路精修 + 状态组件统一 + 上下文连续性。
> 不增加能力；不重新设计 Product / Architecture-6 / App Store / MiniAppShell；只做体验收敛（规范 §1-8）。
> 基础：`UI/UX 2.0` + `UI/UX 2.1` + `Architecture-6`。
> 配套文档：`UI-UX-2.2-State-Inventory.md` / `UI-UX-2.2-Learning-Flow.md` / `UI-UX-2.2-Browser-Verification.md`。

## 完成情况总览

| 项 | 状态 |
| --- | --- |
| 已完成（代码 + 验证） | State 共享组件修复、Context 透传链路收口、死 CSS 清理、新增 17 单测、4 文档 |
| 已核实无需改动 | Result 统一、CTA 统一、Home/Learning 首屏、MiniAppShell 12/13 接壳、Focus 保留沉浸 |
| 无法实机验证 | 像素/暗色/键盘/手势（沙箱 agent-browser 白屏，已标 ⚠️） |

## 1. State Inventory

扫描 `components/{ui,layout,workflow}` + `views/*` + `learning/*` + `apps/*` + `config/navigation.ts` + `stores/*` + `api/*`。
产出 `UI-UX-2.2-State-Inventory.md`：13 Mini App 状态实现表 + 关键页面对照 + 重复判定。
真实结论：共享组件**已存在**，问题在于采用率不均衡，非组件缺失。

## 2. Shared State Components

已存在 `EmptyState` / `ErrorState` / `SkeletonList` / `StateView`（四态容器）/`Pagination`。
**未新建** `StateComponentV2` 等（规范 §16）。
本阶段修复共享组件唯一缺陷：`SkeletonList` 硬编码 `bg-gray-100/200` → `--kb-muted`/`--kb-border`（暗色跟随，规范 §188）。

## 3. Loading

禁止裸「Loading...」；统一用 `SkeletonList`（list/card/line）。`StateView` 的 `loading` 态委托 `SkeletonList`。
本阶段修复其暗色令牌问题。

## 4. Empty

统一三要素：标题 + 说明 + 可选 CTA(≤1)（`EmptyState` 已强制，规范 §25-28）。
采用率：Mistake/AppCenter/Community/LearningReport/PostDetail/TodayPlan/NotesManage/MemoryPalace 已用；其余内联（留后续批次，不机械替换，规范 §14/§17）。

## 5. Error

统一：友好文案 + 重试，不暴露 500/异常（`ErrorState` 默认「加载失败」+`@retry`，规范 §29-30）。
`ErrorState` 已在 Mistake/LearningReport/Community 等采用。

## 6. Context Continuity

- 真实支持 `knowledgeId` 的 App：**Flashcard / Quiz**（经 `route.query` 显示「正在学习：X」、可 clear）、**Knowledge Graph**（聚焦节点）。
- 透传链已通：`buildWeakAppAction`（`action-priority.ts:150`）→ 带 `knowledgeId` 的 `MINI_APP` 动作 → `NextActionCard.doAction` 经 `buildMiniAppContextQuery`（`route.ts`）写 `router.push({ path, query:{ knowledgeId, knowledgeName } })`。
- 本阶段将内联 query 构造**提取为纯函数** `buildMiniAppContextQuery`（`learning/route.ts`），单测覆盖（§217）。
- Mistake/Feynman/Cornell/Memory Palace/Coding/Mindmap/Notes/Drawing **无 knowledgeId 作用域**（视图+API 均无）→ 不显示「正在学习：X」（不伪造，§50）。
- 不新增 `AppKnowledgeContext`/`WorkflowContextService`/`GlobalLearningContext`（§45）；不大型后端重构（§44）。

## 7. NextAction

`resolveNextAction`（`action-priority.ts`）纯函数优先级 REVIEW>TASK>WEAK>PATH>EXPLORE，循环保护 30min；`buildingWeakAppAction` 已带 knowledgeId。
Home/LearningCenter 共用 `actionToCta`（`route.ts`）单一来源。未改动（保持 Architecture-4/6）。

## 8. Result

`LearningResultPanel` 已统一：完成/结果/奖励/学习状态/下一步；无真实数据不显示（§56-64）。
本阶段**未重写**（§58）。Quiz/Flashcard 已接入；Mistake 标记掌握接真实奖励 toast；Coding/Memory Palace 经各自完成态。
奖励仅在真实钱包 diff 时展示（§70-71，不三处重复）。

## 9. CTA

`resolveAppActionState`（`actionState.ts`）纯函数已实现：获取/兑换/打开/重新启用/暂不可用 + 去学习赚取（Coin 不足，§74-80）。
`NextActionCard` 复用；AppCard 按状态文案。未改动业务状态，只统一文案视觉（§79-80）。
单测已覆盖（action-state.spec 13 例，§221）。

## 10. Home

只突出 NextAction + LearningState(≤2) + Continue Path + My Apps；未增卡片（§106-111）。未改动。

## 11. Learning

Learning Center ≤3 重点诊断（§113）；Continue Path/今日任务/待复习明细保留。未改动（保持 Architecture-6）。

## 12. Knowledge

知识库内容源；进入 Quiz/Flashcard 携带 knowledgeId（§102-103）。未改动。

## 13. AI

Chat/Coach 复用既有；未扩展 AI Coach（规范禁止）。未改动。

## 14. Apps

App Center：Get/Redeem 优先说明「能解决什么问题」，价格第二层（§125-127）。未改动 Purchase/Entitlement/UserApp（§128）。

## 15. Mini Apps

12/13 接 MiniAppShell（§86-87）；Focus 保留沉浸式（§88-89，不破坏 Focus Timer）。
首屏突出当前主要任务（§90-100）：Quiz=开始答题 / Flashcard=开始复习 / Mistake=处理错题 / Feynman=解释知识 / Coding=完成练习。
Visual Cleanup（§162）：删除 FlashCardsHub/SmartQuiz 中已死亡 `.context-bar`/`.context-clear` CSS（无模板引用，已核实）。

## 16. Focus

沉浸式原样（§234-237）；本阶段仅确认退出正常（代码确认 immersive-topbar 退出逻辑未动）。不套 MiniAppShell。

## 17. Responsive

`@media(max-width:640px)` MiniAppShell context 折行；Canvas App（Graph/Mindmap/Drawing/Chat）允许特殊布局（§196-197）。
⚠️ 390/768/1440 真机走查待用户确认。

## 18. Dark Mode

`SkeletonList` 改 `--kb-*` 令牌（本阶段修复）；共享组件/Shell/Result/CTA 全 `--kb-*`。
⚠️ 全站暗色观感待真人确认。

## 19. Accessibility

MiniAppShell back/context-clear 为真实 `<button>`+aria-label；CTA 用 `Button`；图标按钮带 aria-label/title（§177-183）。
未发现 `div @click` 伪装按钮新增。⚠️ 全站 Tab/Enter/Esc 焦点环待真人确认。

## 20. Performance

`resolveNextAction` 纯函数，页面初始化按需加载；不每次 render fetch（§198-203）。未新增 WS/MQ/Redis（§204）。

## 21. Vitest

新增 17 例（基线 88 → **105**）：
- `StateComponents.spec.ts`（11）：EmptyState/ErrorState/StateView 四态
- `context-query.spec.ts`（3）：knowledgeId → query（§217）/ clear → 空（§218）
- `navigation.spec.ts`（3）：5 一级入口（§222）
不删测试、不 skip、不降断言、不 `@ts-ignore`、不 `any`（§209-214）。

## 22. TypeScript

`vue-tsc` = **0 errors**（本阶段改动文件零错误：SkeletonList 令牌、NextActionCard 抽函数、FlashCardsHub/SmartQuiz 删 CSS、新增 3 测试文件）。

## 23. Build

`vite build` = **PASS**（3.46s）。仅 chunk-size 提示（非错误）。

## 24. Backend

**71/71**（本阶段零后端改动；未新增表/API/Service，符合 §1/§268）。

## 25. Browser Verification

产出 `UI-UX-2.2-Browser-Verification.md`（✅/❌/⚠️）。
✅ = 源码/TS/Build/单测验证；⚠️ = 需真人浏览器走查。诚实标记未观察项（§248/§272）。

## 26. User Switching

`stores/apps.ts` 登出重置 + 后端 `user_app` 真源；wallet/entitlement 后端 user-scoped；localStorage key=`knowflow:apps:<uid>`（user-scoped，规范 §139-140）。
⚠️ A→B 串数据真人确认。

## 27. UX Friction Audit

主链路：Home → NextAction(1 点击突出) → Mini App(1 点击开始) → Complete → Result → NextAction。
代码层满足「首页到开始学习 ≤1 主点击、完成到下一步 ≤1 主点击」（§155-156）。
Store：App →（已拥有）Open /（未拥有）Get-Redeem → Open（§159-160）。
⚠️ 真人计时/点击计数待确认。

## 28. 遗留问题

1. **部分 App 仍用内联空态**（Flashcard/Feynman/Cornell/Graph/Coding/Mindmap/Drawing/SmartWriting）未迁 `StateView`——留后续独立收敛批次，本阶段不机械替换（§14）。
2. **实机浏览器验证未完成**：沙箱 agent-browser 白屏，像素/暗色/键盘/手势/手势均标 ⚠️，须用户 `npm run dev` 走 `UI-UX-2.2-Browser-Verification.md`。
3. **不支持 knowledgeId 的 9 个 App 无上下文**：符合规范 §50（不伪造）；若未来后端为某 App 增加 knowledgeId 过滤，再按 §40 接通。
4. **Home/Learning 卡片计数未机验**：交互流畅度依赖真人感知。
5. **后端 71/71 零改动**：本阶段纯前端体验收敛。

## 最终产品判断（§245-246，诚实版）

- 用户打开 Home：NextAction 突出 —— ✅ 代码；⚠️ 观感。
- 点击一次开始学习：Quiz/Flashcard 首屏主 CTA 直达 —— ✅。
- 完成一次知道发生了什么：LearningResultPanel —— ✅ 代码；⚠️ 观感。
- 看到真实奖励：钱包 diff 才显示 —— ✅。
- 知道下一步：NextActionCard —— ✅。
- 未安装能力自然进 App Center：NextAction→Store—— ✅ 代码。
- Coin 不足自然回 Learning：去学习赚取 —— ✅ 代码。
- 购买后自然进 App：`?redeem=` 深链 —— ✅ 代码；⚠️ 真实购买。
- A→B 状态隔离：后端 user-scoped —— ✅ 代码；⚠️ 真人。
- 手机/暗色/键盘：响应式+令牌已就位 —— ⚠️ 真人确认。

> 不准美化：凡标 ⚠️ 均为「代码/构建/单测已验证，未做真人像素验收」。
