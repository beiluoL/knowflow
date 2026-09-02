# KnowFlow UI/UX 3.1 执行报告

> 阶段：App Management Workspace（我的应用 / 启用停用 / 菜单管理 / 排序 / 加载与失败态）
> 基础：UI/UX 3.0（App-Driven Navigation）。未进入 Architecture-7；未新增 App / 一级导航 / 数据库 / API / App State 系统。
> 完成时间：2026-09-01（Vibe 版补齐：加载/失败态 + 平台停用可见性 + 三矩阵测试 + Before 文档）

---

## 0. Before → After

| 维度 | Before（3.0 结束时） | After（3.1） |
|---|---|---|
| 我的应用 | `installedApps` 平铺网格，**不区分启用/停用** | **2 个状态组**：已启用 / 已停用（§17） |
| 卡片信息 | 显示 `v1.0.0` 版本号 | 移除版本；只留图标/名称/描述/状态/操作（§174、§20） |
| 已停用卡片 | **Bug**：同时显示「重新启用」和「停用」 | 只显示「启用」，无「停用」（§22） |
| CTA 来源 | AppCard 内硬编码 4 个分支 | 统一由 `actionState.ts` 计算（§111-112） |
| 失败反馈 | **无**：store 吞掉异常，UI 仍显示成功 | 回滚 + Toast「操作失败，请重试」+ 返回 false（§49-50） |
| 按钮 Loading | 无，可连点 | `isUpdating(key)` 按钮级 loading + 禁用（§51-53） |
| 页面加载态 | 无（首屏闪一下空态） | `mineState` → `SkeletonList` 内容区骨架（§137-138） |
| 页面失败态 | 无（静默空列表） | `ErrorState` + 「重试」，**内容区级不整页 Error**（§139-140） |
| 离线降级 | 无提示 | 「当前显示本地缓存，联网后会自动同步」（§232） |
| 平台停用 App | 用户已启用则仍留在菜单（潜伏缺陷） | `status !== ACTIVE` → 不进菜单，与 CTA 同源（§34/§175） |
| 排序 | 无 UI（仅 Resolver 读 sortOrder） | 桌面拖拽 + 移动/键盘上移下移，写回 `sortOrder`（§61-78） |
| 发现应用 | 名为「全部」，含已安装 App | 名为「发现应用」，**只列未安装 App**（§101-104） |

---

## 1. App Management

App Center 成为**唯一完整管理中心**（§113-120）：Sidebar / Home 只是快捷入口，Profile / 设置不管理 App，不存在三处都能 enable/disable 的情况。

## 2. UserApp

- 字段沿用真实项目：`installed` / `enabled` / `sortOrder`（§9）。
- **未新增第二套 App State**（§10）：无 AppPreference / MenuPreference / NavPreference。
- 后端 `user_app` 为真源；Pinia 缓存；localStorage 为 `knowflow:apps:<userId>`（用户隔离，§214-216）。
- 数据流：`appsApi` → `useAppStore` → `resolveNavigation()` → 各视图；视图层不直接 axios（§159-166）。

## 3. Navigation

- 可见性 = `navVisible && !平台停用 && installed && enabled`（§41），未登录不注入（§46）。
- 排序 = `sortOrder ?? registry.order`，**单一排序结果**（§80）。
- 启用/停用后 `computed` 自动重算，**无需刷新网页 / 重新登录 / 重启 App**（§37-40）。

## 4. Enable

`PUT /api/apps/user-apps/{appKey}` `{ installed:true, enabled:true }`。
成功后 Toast：`已添加到学习菜单「费曼」`（工作区名由 `navSectionLabel()` 取，§225）。幂等（§57-60）。

## 5. Disable

`PUT /api/apps/user-apps/{appKey}` `{ installed:true, enabled:false }`。
二次确认弹窗明确「已有学习数据会保留，可随时重新启用」；成功 Toast：`已从菜单隐藏「费曼」`（§224）。
只改 `enabled`：不动 Entitlement，不删 LearningEvent / Mastery / Review / App 数据 / 购买记录（§26-32）。

## 6. Sort

- 桌面：原生 HTML5 Drag & Drop（项目无拖拽库，按 §63-65 不引入第三方）。
- 移动 / 键盘：上移 / 下移按钮（§66-67、§248-250），44px 热区。
- 写回 `UserApp.sortOrder`；只对已启用 App 排序，已停用排在其后（§179-181）。
- 失败整体回滚，不产生两套顺序（§79）。

## 7. My Apps

`installed=true` 的 App，分「已启用 / 已停用」两组；空态文案「还没有启用应用…」而非 `0 apps`（§230）。

## 8. Discovery

Registry + Products + Entitlement；**只列未安装 App**，已启用的不重复以商品形态出现（§104）；付费精选由服务端提供价格/拥有态；App 数量仅十几个，**未做复杂搜索/筛选器/排序器**（§232-236）。

## 9. Purchase

`Purchase → Entitlement → UserApp(installed+enabled) → Navigation`，兑换成功后自动入菜单、**无需用户刷新**（§137-140）；CTA「立即打开」走 **Registry Route**（§141-143），不接受远程路由 / `javascript:`（§144-146）。**未修改** PurchaseService / Wallet / Order（§30、§136）。

## 10. Entitlement

拥有态来自服务端 `product.owned`；**不用于判断菜单可见性**（§7）。已拥有但未安装的 App 由付费精选的「打开」覆盖。

## 11. Loading & Error States（本轮新增）

| 区块 | loading | error | 说明 |
|---|---|---|---|
| 我的应用 | `SkeletonList rows=3 type=list` | `ErrorState` + 「重试」→ `store.fetchUserApps()` | 内容区级；PageHeader 与 Tab 始终可点，用户可切到「发现应用」继续操作 |
| 付费精选 | `SkeletonList rows=1 type=card cols=3` | `ErrorState` + 「重试」→ `fetchProducts()` | **只影响本区块**，下方免费应用照常渲染（§139 不整页 Error） |
| 离线缓存 | — | 不算错误 | 同步失败但本地有缓存 → 按 `ready` 展示缓存 + 「当前显示本地缓存，联网后会自动同步」提示（§49-52） |

- 状态机：`mineState` / `productsState` ∈ `loading | ready | error`。
- `stores/apps.ts` 的 `fetchUserApps()` 由 `Promise<void>` 改为 **`Promise<boolean>`**，使页面能区分三态（此前异常被吞掉，页面只能静默空列表）。
- 未登录时返回 `true`（无可同步，不算失败），避免鉴权路由下出现假错误态。

## 12. Platform Disabled Visibility（本轮新增）

- `canShowInNavigation()` 增加 `isAppPlatformDisabled(app.status)` 判定：`status !== 'ACTIVE'`（BETA / LEGACY）一律不进导航，即使用户已 installed+enabled。
- **复用** `components/workflow/actionState.ts` 的既有实现，不在导航里重写一份规则（§34/§175）——CTA 显示「暂不可用」与菜单隐藏因此永远一致。
- 现状：Registry 中 13 个 App 全为 `ACTIVE`，该修复属**潜伏缺陷防御**；新增不变量测试守护（任何允许进导航的 App 必须 `status === 'ACTIVE'`）。

## 13. Mobile

桌面侧边栏 / 移动端底部导航（恒 5 个一级入口）+ 抽屉；App Center 390px 单列、桌面多列；操作按钮 ≥44px（§251-256）。

## 14. Dark

全部继续 `--kb-*`，未新增主题（§257-258）。本轮新增的骨架屏/错误态/缓存提示同样走令牌。

## 15. Accessibility

真实 `<button>`；`aria-label` 覆盖每个操作（如「停用「费曼」」）；`focus-visible` 用 `--kb-ring`；**拖拽提供键盘可达的上移/下移替代**（§244-250）。
本轮新增：骨架屏容器带 `aria-busy="true"` + `aria-live="polite"`；离线提示带 `role="status"`。

## 16. Performance

局部状态更新，不整页刷新、不重新拉取全部数据；排序仅在提交时写回（§259-264）。骨架屏避免首屏布局跳动（CLS）。

## 17. Security

后端**零改动**，逐条核对已满足（详见 App-Management.md §11）：`SecurityUtils.getCurrentUserId()`、appKey 白名单、`installed=false→enabled=false`、`Math.max(0, sortOrder)`、幂等 upsert、统一 `BusinessException`。
本轮仍未触碰后端；前端新增代码无 `eval` / 无 `v-html` / 无远程路由跳转。

---

## 18. Tests

| 项 | 结果 |
|---|---|
| 首轮新增 | `app-management.spec.ts` +12、`apps-store.spec.ts` +5 = **+17** |
| 二轮新增 | `views/__tests__/AppCenterPage.spec.ts` **+5**（页面级三态） |
| 累计 | **Vitest 165/165 通过**（88 → 105 → 120 → 143 → 160 → **165**） |
| apps-store 覆盖 | enable / disable / 幂等 10 次 / **失败回滚且不伪造成功** / sortOrder 持久化 / reorder 失败整体回滚 / localStorage user-scoped / 不存在全局键 / **A→B 切换重置不串号** / **B 写入不污染 A 的缓存键** / **加载三态返回值（成功 / 失败无缓存 / 失败有缓存）** |
| app-management 覆盖 | App State Matrix（4 种组合含非法组合）/ Navigation Matrix（learning→学习、knowledge→知识、ai→AI、不串菜单）/ App Lifecycle 全链路 / sortOrder 顺序 / registry.order 回落 / **平台停用（LEGACY / BETA 不进导航、与 CTA 同源、ACTIVE 回归、不变量）** / **用户隔离（A 启用 B 未启用、纯函数无污染、未登录不注入）** / **Category→Workspace（允许范围、单一来源、同 category 可跨区、分区恒为 3 个）** |
| 约束 | 未删除、未 skip、未 ignore、未降低断言；未用 `any` / `@ts-ignore` / 未关闭 strict |

### 踩坑记录（可复用）

- **Pinia 测试里 mock 的 auth store 必须是 Vue 响应式对象**：apps store 用 `computed(() => auth.user?.id)` + `watch(uid)` 做切换重置，普通对象不会让 computed 失效 → watcher 不触发 → 「用户隔离」测试假失败。修法：`vi.mock('@/stores/auth', async () => { const { reactive } = await import('vue'); ... })`，并通过代理赋值切 uid。

## 19. TypeScript

**`vue-tsc -b` 0 errors**（本轮一度出现 `TS2339: Property 'enabled' does not exist`，为测试内 `JSON.parse` 断言类型过窄所致，已修正类型而非断言）。

## 20. Build

**PASS**，`✓ built in 3.74s`，Exit 0。

## 21. Backend Test

`mvn -f backend/pom.xml test` → **Tests run: 71, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS**（本阶段后端零改动）。

---

## 22. Browser Verification（诚实标注）

> 沙箱 `agent-browser` 白屏，**无法做真人像素级验收**。以下分「已由代码/单测验证」与「需本地走查」。

### ✅ 已验证（代码 + 单测）

| 项 | 依据 |
|---|---|
| 启用 → 菜单出现 / 停用 → 菜单消失 | `app-management.spec.ts` Lifecycle |
| 停用只改 enabled，installed 保留 | 单测 + store 实现 |
| 重新启用无需购买 | 单测（不涉及 Entitlement） |
| 幂等（10 次 enable） | `apps-store.spec.ts` |
| API 失败回滚 + 返回 false（不伪造成功） | `apps-store.spec.ts` |
| sortOrder 写回与顺序 | 单测 |
| 排序失败整体回滚 | 单测 |
| localStorage 用户隔离 + A/B 切换重置 | 单测 |
| 平台停用不进导航 + 与 CTA 同源 | 单测 |
| 状态矩阵 / 导航矩阵 / Category→Workspace | 单测 |
| 加载三态（成功 / 失败 / 离线缓存） | 单测（store 返回值）+ **页面级组件测试** |
| 骨架屏 / 错误态 / 重试 / 付费失败不级联 | `AppCenterPage.spec.ts`（5 例，见 §18） |
| 后端校验规则（真机 API） | 见下方「真机 API 验证」 |

### ✅ 真机 API 验证（2026-09-01，后端 8080 实跑）

| # | 场景 | 结果 |
|---|---|---|
| 1 | `GET /api/apps/user-apps` 未带 token | `401`（鉴权生效） |
| 2 | 登录 `user1` → 读取 | 返回 4 个默认 App：flashcard / quiz / mistake / focus |
| 3 | `PUT feynman {installed:true, enabled:true}` | 200，写入成功（启用 → 进菜单） |
| 4 | `PUT feynman {installed:true, enabled:false}` | 200，**`installed` 保持 true**（停用不删数据） |
| 5 | `PUT quiz {installed:false, enabled:true}`（非法组合） | 200，后端归一化为 `enabled:false` |
| 6 | `PUT quiz {sortOrder:-9}` | 200，归一化为 `sortOrder:0` |
| 7 | `PUT evil-app`（白名单外） | `code 2006 无效的应用标识`，被拒 |
| 8 | 登录 `user2` 读取 | 只有 4 个默认 App，**不含 user1 的 feynman**（用户隔离） |

> 验证后已把 `user1` 的测试数据还原（feynman → installed=false；quiz → sortOrder=1）。

### ⚠️ Not verified（需你本地 `npm run dev` 走查）

| # | 场景 |
|---|---|
| 1 | 应用 → 我的应用 → 启用 Feynman → **Sidebar 立即出现**（不刷新） |
| 2 | 关闭 Feynman → Sidebar 立即消失 → 重新启用 → 再出现（§265） |
| 3 | 拖动排序 → 顺序变化 → **刷新页面顺序保持**（§266） |
| 4 | 移动端上移/下移可用性 + 44px 热区手感 |
| 5 | A 登出 → B 登录 → 菜单不同（§267） |
| 6 | 换设备/换浏览器登录 → 状态同步（§268） |
| 7 | 应用中心兑换 → 自动加入菜单 → 「立即打开」进入真实路由（§269） |
| 8 | Coin 不足 → 「去学习赚取」→ 回到学习（§270） |
| 9 | 390 / 768 / 1440 三断点布局 |
| 10 | 暗色模式无白底泄漏 |
| 11 | 键盘 Tab / Enter 走查拖拽替代操作 |
| 12 | 断网时点击启用 → 是否出现「操作失败，请重试」且状态回滚 |

> 原第 13、14 项（骨架屏/错误态、付费精选失败不级联）已由 `AppCenterPage.spec.ts` 转为 ✅ 自动验证，从本清单移除。

### 🟡 仍在跑的服务（供你直接走查）

- 后端：http://localhost:8080 （Spring Boot，H2 内存库）
- 前端：http://localhost:5173 （Vite dev，`SERVER__PORT=8080` 已指向后端）
- 账号：`admin` / `user1` / `user2`，密码 `admin123`
- 入口：http://localhost:5173/apps

---

## 23. User Switching & Isolation

- Store 内 `watch(uid)` 在用户切换/登出时重置 `userApps`（Architecture-3 既有机制）。
- 缓存键 `knowflow:apps:<userId>` 天然隔离；单测断言**不存在**全局 `knowflow:apps` 键。
- 本轮新增两条 A/B 隔离测试：切换用户内存态重置不串号；B 的写入只落在 `knowflow:apps:2`，A 的键保持原样。
- ⚠️ A→B 真机切换的视觉验证未做（见 §22-5）。

## 24. Cross Device

- 后端为真源，新设备登录后 `fetchUserApps()` 拉取服务端状态覆盖本地缓存（§219-221）。
- 本轮新增：同步失败但本地有缓存时不判为页面级失败，仍可离线使用（§49-52）。
- ⚠️ 跨设备真实同步未做实机验证（需要两个浏览器/设备）。

## 25. 三大矩阵 + 数据流

### 25.1 App State Matrix

| installed | enabled | 平台 status | 导航可见 | 我的应用分组 | CTA |
|---|---|---|---|---|---|
| false | false | ACTIVE | 否 | 不出现（在「发现应用」） | 获取 |
| false | true（非法） | ACTIVE | 否（归一化） | 不出现 | 获取 |
| true | false | ACTIVE | 否 | **已停用** | 启用 |
| true | true | ACTIVE | 是 | **已启用** | 打开 |
| true | true | BETA / LEGACY | **否**（本轮新增） | 已启用 | 暂不可用 |

### 25.2 Navigation Matrix

| 工作区 | 可进动态子菜单的 App | 固定入口（前置） |
|---|---|---|
| 学习 | flashcard / quiz / mistake / coding / feynman | 学习中心、今日计划、学习路径、复习计划、挑战、掌握度、学习报告、学习工作台、番茄钟、专注模式 |
| 知识 | knowledge-graph / cornell / mindmap / notes / drawing | 知识库、文档库、分类浏览、搜索、社区讨论、学习小组、私信 |
| AI | ai-writing | AI 助手、编程 Agent |
| 首页 / 应用 | —（不展开动态子菜单） | 首页；应用页本身即管理中心 |

- `focus`（番茄钟/专注）`navVisible=false`：保持沉浸式，由固定入口承载，不重复注入。
- `memory-palace` 未配 `navSection`：暂不进动态子菜单（见遗留问题 2）。

### 25.3 App Lifecycle

```
发现应用（未安装）
   │  获取 / 兑换（installed=true, enabled=true）
   ▼
我的应用 · 已启用 ──► 导航可见（Sidebar / 移动端 / Home 快捷入口）
   │  停用（仅 enabled=false，数据全保留）
   ▼
我的应用 · 已停用 ──► 导航隐藏
   │  重新启用（无需再次购买）
   └──────────────► 回到「已启用」
```

### 25.4 数据流 / 架构

```
┌─────────────────┐   ┌──────────────────┐
│ registry.ts     │   │ Backend user_app │
│ 13 App 定义     │   │ (真源, /api/apps)│
│ APP_NAV_META    │   └────────┬─────────┘
└────────┬────────┘            │ appsApi
         │                     ▼
         │            ┌──────────────────┐
         │            │ stores/apps.ts   │
         │            │ userApps + 三态  │
         │            └────────┬─────────┘
         └───────────┬────────┘
                     ▼
          ┌───────────────────────┐
          │ apps/navigation.ts    │
          │ resolveNavigation()   │
          │ canShowInNavigation() │──► 复用 actionState.isAppPlatformDisabled
          └───────────┬───────────┘
                      ▼
   ┌──────────────────┼──────────────────┐
   ▼                  ▼                  ▼
CSidebar.vue     CLayout.vue      LearningCenterPage
（侧栏子菜单）   （移动端底部导航）  （快捷入口按启用过滤）

写操作唯一入口：AppCenterPage.vue ──► useAppStore（enable / disable / install / reorder）
CTA 文案唯一来源：components/workflow/actionState.ts
```

---

## 附录 A. Design Deviations（与规范不一致处及理由）

| # | 规范假设 | 真实项目 | 处理 |
|---|---|---|---|
| 1 | App 分类为 `learning` / `knowledge` | 真实 `category` 是 `MEMORY / PRACTICE / UNDERSTANDING / ORGANIZATION / FOCUS / CODING / AI`（学习方法维度） | **不改业务分类**，新增 `navSection`（导航维度）在 `APP_NAV_META` 单点映射，满足「分类只来自 Registry」 |
| 2 | 「Sidebar」唯一 | 存在后台 `Sidebar.vue`（Admin）与 C 端 `CSidebar.vue` 两套 | 只改 C 端；后台侧栏未触碰 |
| 3 | BETA 视为可用 | `actionState.isAppPlatformDisabled` 判定 `!== ACTIVE` 即不可用 | 导航与 CTA **共用同一判定**，保持一致性优先；若未来要开放 BETA，应同时改 `actionState` |
| 4 | 失败整页 ErrorState | 会遮住仍可用的免费应用 | 改为**内容区级 / 区块级**错误态，Header 与 Tab 保持可用 |
| 5 | 拖拽库 | 项目无拖拽依赖 | 原生 HTML5 DnD + 上移/下移替代，未引入第三方（§63-65） |

## 附录 B. 修改文件清单

### 本轮（2026-09-01 Vibe 补齐）

| 文件 | 改动 |
|---|---|
| `frontend/src/apps/navigation.ts` | `canShowInNavigation` 增加平台停用判定，复用 `actionState.isAppPlatformDisabled` |
| `frontend/src/stores/apps.ts` | `fetchUserApps()` 返回 `Promise<boolean>`，供页面判定加载三态 |
| `frontend/src/views/AppCenterPage.vue` | 新增 `mineState` / `productsState` 三态；骨架屏、ErrorState+重试、离线缓存提示、`.sync-hint` 样式；`onMounted` 重构为 `hydrateCache → reloadMine → fetchProducts → redeem 深链` |
| `frontend/src/apps/__tests__/app-management.spec.ts` | +12：平台停用（5）/ 用户隔离（3）/ Category→Workspace（4） |
| `frontend/src/stores/__tests__/apps-store.spec.ts` | +5：A/B 切换重置、B 不污染 A、加载三态返回值（3）；auth mock 改为响应式 |
| `frontend/src/views/__tests__/AppCenterPage.spec.ts` | **新增** +5：骨架屏出现/消失、错误态+重试恢复、离线缓存提示、付费精选失败不级联 |
| `docs/二次开发/UI-UX-3.1-Before.md` | **新增**：12 项现状快照 |
| `docs/二次开发/UI-UX-3.1-执行报告.md` | 更新至 25 节 + 3 附录 |

### 3.1 前序轮次（2026-08-31）

| 文件 | 改动 |
|---|---|
| `frontend/src/components/apps/AppCard.vue` | 重写：去版本号、修按钮互斥、补 aria-label、CTA 走 actionState |
| `frontend/src/views/AppCenterPage.vue` | 我的应用 2 组 + 发现应用 Tab + 拖拽/上下移排序 + 按钮级 loading |
| `frontend/src/views/LearningCenterPage.vue` | `quickNavItems` 改为按启用 App 过滤（去硬编码） |
| `frontend/src/apps/registry.ts` / `types.ts` / `navigation.ts` | 3.0 遗留：navSection / navVisible / order 元数据与 Resolver |
| `frontend/src/config/navigation.ts` | 删除硬编码 App 子菜单，改由 Resolver 注入 |
| `frontend/src/components/layout/CSidebar.vue` / `CLayout.vue` | 接入 Resolver；补 UserApp 同步 |

### 未改动（明确）

- 后端全部文件（0 改动，71/71 测试照常通过）
- `LearningEvent` / `Reward` / `Mastery` / `SRS` / `NextAction`
- 未新增 App、一级导航、数据库表、API

## 附录 C. 遗留问题

1. **真人浏览器验收未做**（沙箱限制），12 项清单见 §22（原 14 项中 2 项已由组件测试转为自动验证）。
   - 后端 API 侧已做真机实跑验证（8 项，见 §22）；前端视觉与交互仍需你本地走查。
2. **memory-palace 仍未配 `navSection`**：不进动态子菜单，仅可从应用中心/首页进入（补 `APP_NAV_META` 一行即可）。
3. **排序仅对已启用 App 生效**，已停用 App 统一排在其后且不参与排序（§180 允许）。
4. **已拥有但未安装的付费 App**：在「发现应用」网格中 CTA 显示为「获取」而非「已拥有·打开」；其「已拥有·打开」入口由上方「付费精选」区块提供。属已知的展示口径差异，未改动 `actionState.ts`（避免破坏既有 13 条断言）。
5. **拖拽为原生 HTML5 DnD**：桌面体验可用，移动端依赖上移/下移；若未来需要更顺滑的移动端拖拽再评估引入轻量库。
6. **未做菜单预览页**（§182-184）：按规范选择「直接让 Sidebar 实时变化」。
7. **BETA 目前与 LEGACY 同等对待（均不进菜单）**：与 CTA 一致，但是否应让 BETA 可用属产品决策，需产品确认后再动 `actionState`。

## 附录 D. 约束遵守

| 约束 | 状态 |
|---|---|
| 开始 `git status` | ✅ |
| 禁止 `git reset --hard` / `git clean -fd` / `git checkout .` | ✅ 未执行 |
| 禁止 `git commit` / `git push` | ✅ 未执行 |
| 不覆盖未提交修改 | ✅ 仅新增/最小编辑 |
| 不修改 LearningEvent / Reward / Mastery / SRS / NextAction | ✅ |
| 不进入 Architecture-7 / 不做 AI Coach / 不增 App / 不增一级导航 / 不增数据库 | ✅ |

---

**STOP** — 本阶段结束，未提交、未推送。后端与前端开发服务器**仍在运行**，可直接按 §22 的 12 项走查：http://localhost:5173/apps
