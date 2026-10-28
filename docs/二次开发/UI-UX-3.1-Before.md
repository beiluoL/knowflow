# UI/UX 3.1 — 开工前现状快照（Before）

> 目标：为「App Management Workspace + Dynamic Navigation」提供**可核对的现状基线**。
> 全部条目均来自真实源码扫描（`frontend/src` + `backend/src`），不引用任何规范假设。
>
> - 生成时间：2026-09-01
> - 扫描范围：`frontend/src/{apps,config,stores,views,components/layout,components/apps,components/workflow}`、`backend/src/main/java/com/knowflow/controller/AppsController.java`
> - 与 3.0 的关系：3.0 已落地 Navigation Resolver，本快照记录的是**3.1 开工时（即 3.0 之后）**的真实状态；标注 `3.1 改动` 的条目表示本阶段被修改。

---

## 1. 一级导航：5 个固定入口

- 定义位置：`frontend/src/config/navigation.ts`，`GROUPS` 常量导出 `visibleGroups(isLoggedIn, isAdmin)`。
- 五个一级入口：`首页(/)`、`学习(/learning/center)`、`知识(/knowledge)`、`AI(/chat)`、`应用(/apps)`。
- 结构：`NavGroup { label, path, icon?, children?: NavChild[] }`；`NavChild { label, path, icon?, auth? }`。
- 3.0 后：一级入口仍由该文件静态收口，Resolver 只向「学习/知识/AI」三个分组**追加**动态子项，不新增一级入口。
- 3.1 改动：无。

## 2. 二级导航：静态 children + 3.0 起的动态注入

- 3.0 之前：`学习` 分组内**硬编码**了闪卡 `/learning/flashcards`、练习 `/learning/quiz`、错题 `/mistakes`、代码练习、知识图谱、智能写作等 App 入口 —— 即「菜单顺序由代码写死，与用户启用状态无关」。
- 3.0 之后：`config/navigation.ts` 中这些 App 入口已被删除；改由 `resolveNavigation()` 依据 `AppRegistry × UserApp` 动态注入，固定工作区入口（学习中心/今日计划/知识库/AI 助手…）保持在**前**，动态 App 追加在**后**。
- 判定函数：`frontend/src/apps/navigation.ts` → `canShowInNavigation(app, state)`。
- 3.1 改动：`canShowInNavigation` 增加「平台停用」判定（见第 9 项）。

## 3. App Registry（应用定义的唯一来源）

- 文件：`frontend/src/apps/registry.ts`，`APP_DEFINITIONS` 共 **13 个** App 字面量。
- 字段：`key / name / description / icon / category / categoryLabel / route / version / status / defaultInstalled / capabilities`。
- 分类（`AppCategory`）真实取值：`MEMORY / PRACTICE / UNDERSTANDING / ORGANIZATION / FOCUS / CODING / AI`（**不是** learning/knowledge 这类导航词）。
- 3.0 起新增运行时富化：`APP_NAV_META` 集中映射 `navSection / order / navVisible`，模块加载时写回 `AppDefinition`，13 个字面量本身不改。
- 默认安装：`DEFAULT_APP_KEYS = defaultInstalled === true` 的 App（闪卡/练习/错题/专注）。
- 3.1 改动：无。

## 4. UserApp（用户-应用状态）

- 后端真源：`user_app` 表，`AppsController` 暴露 `GET /api/apps/user-apps`、`PUT /api/apps/user-apps/{appKey}`；仅操作当前登录用户（SecurityContext），`appKey` 白名单校验、`installed=false → enabled=false` 归一化、`Math.max(0, sortOrder)`、幂等 upsert。
- 前端状态：`frontend/src/stores/apps.ts`（Pinia `apps`），`userApps: Record<string, UserAppState>`；后端服务端优先，localStorage 仅作降级缓存。
- 状态形状：`UserAppState { installed, enabled, sortOrder }`。
- 3.1 改动：`fetchUserApps()` 返回值由 `Promise<void>` 改为 `Promise<boolean>`，供页面判定加载三态（见第 11 项）。

## 5. 侧边栏：后台 Sidebar 与 C 端 CSidebar 是两套

- `components/layout/Sidebar.vue`：后台 Admin 侧栏，与 C 端无关（易误改，已在 3.0 排查中确认）。
- `components/layout/CSidebar.vue`：C 端侧栏，3.0 起调用 `resolveNavigation(auth.isLoggedIn, auth.isAdmin, appStore.userApps)`。
- `components/layout/CLayout.vue`：外层布局，`onMounted` 中若登录且 `userApps` 为空则补一次同步（修深链直达导致菜单为空的问题），并计算移动端底部导航。
- 3.1 改动：无。

## 6. 移动端底部导航

- 位置：`CLayout.vue`，同一份 `resolveNavigation()` 结果取**一级入口**渲染底部 5 项；子菜单通过抽屉展开。
- 3.1 改动：无。

## 7. App Center 页面（应用管理中心）

- 文件：`frontend/src/views/AppCenterPage.vue`，路由 `/apps`（需登录）。
- 3.1 前：单页混合展示，App 与付费商品混排，无「我的应用 / 发现应用」分区。
- 3.1 后：两个 Tab ——「我的应用」（`已启用` / `已停用` 两组）与「发现应用」（分类筛选 + 付费精选）。
- 3.1 再补：加载骨架屏、内容区错误态 + 重试、离线缓存提示（本轮新增）。

## 8. AppCard 组件

- 文件：`frontend/src/components/apps/AppCard.vue`。
- 3.1 前：展示版本号；已停用的 App 同时渲染「重新启用」与「停用」两个按钮（状态互斥被破坏）。
- 3.1 后：去掉版本号、修复按钮互斥、补 `aria-label`、CTA 文案与状态全部由 `actionState.ts` 计算。
- 3.1 改动：本轮未再修改。

## 9. App 状态语义（两套「状态」并存）

| 维度 | 字段 | 取值 | 含义 |
| --- | --- | --- | --- |
| 平台状态 | `AppDefinition.status` | `ACTIVE / BETA / LEGACY` | 平台侧是否上架；`!== ACTIVE` 即不可用 |
| 用户状态 | `UserAppState.installed` | boolean | 是否已获取 |
| 用户状态 | `UserAppState.enabled` | boolean | 是否启用（进菜单） |
| 用户状态 | `UserAppState.sortOrder` | number | 菜单内排序 |

- 3.0/3.1 前：`canShowInNavigation` **只看** `navVisible + installed + enabled`，未考虑 `status` —— 理论上平台下架的 App 若用户已启用仍会留在菜单（当前 13 个 App 全为 `ACTIVE`，属潜伏缺陷，非已发问题）。
- 3.1 改动：`canShowInNavigation` 增加 `isAppPlatformDisabled(app.status)` 判定，复用 `components/workflow/actionState.ts` 的既有实现，不重复造规则。

## 10. CTA 与 actionState

- 文件：`frontend/src/components/workflow/actionState.ts`（Architecture-5 产物）。
- 统一文案：免费→「获取」；付费→「兑换 · N Coin」；已拥有→「打开」；已停用→「重新启用」；平台停用→「暂不可用」；余额不足→「去学习赚取」。
- 3.1 改动：无（3.1 只复用，不改写）。

## 11. API 与加载/失败处理

- 前端 API 层：`frontend/src/api/apps.ts`，仅两个方法（`getUserApps` / `updateUserApp`），组件不直接 axios。
- 3.1 前：`stores/apps.ts` 的 `fetchUserApps()` 吞掉异常（返回 `void`），页面无法区分「加载中 / 空 / 失败」，只能在失败时静默回落到可能为空的列表。
- 3.1 改动：`fetchUserApps()` 返回 `boolean`；`AppCenterPage` 增加 `mineState` / `productsState` 两个 `loading|ready|error` 状态机：
  - `loading` → `SkeletonList`（内容区骨架，Header/Tab 保持可用，不做整页 loading）
  - `error` → `ErrorState` + 「重试」（内容区级，付费精选的失败不阻塞免费应用）
  - 同步失败但本地有缓存 → 按 `ready` 展示缓存并显示「当前显示本地缓存，联网后会自动同步」提示
- 付费商品接口：`storeApi.products('APP')`，失败只影响「付费精选」区块。

## 12. 动态 vs 静态菜单逻辑 & 重复逻辑风险点

| # | 风险点 | 3.1 前状态 | 3.1 处理 |
| --- | --- | --- | --- |
| a | 分类概念两套（`category` 学习方法 vs 导航工作区） | 无映射，靠硬编码 | `APP_NAV_META` 单一来源，UI 层不各写一份 |
| b | 排序多处自造（名字母序 / 数组顺序 / sortOrder） | 存在 | 统一 `UserApp.sortOrder ?? registry.order`，由 `store.reorder()` 唯一写回 |
| c | 停用逻辑重复（CTA 与导航各自判定） | 存在 | 导航复用 `canShowInNavigation`，CTA 复用 `actionState`，平台停用判定共用 `isAppPlatformDisabled` |
| d | 菜单为空（深链直达时未同步 UserApp） | 存在 | `CLayout.onMounted` 补同步（3.0 已修） |
| e | 用户隔离 | 后端已按 SecurityContext 隔离；前端 localStorage 按 uid 分键 | 本轮补 A/B 切换重置与分键写入的测试 |
| f | 失败伪造成功 | store 吞异常 | `updateUserApp/reorder` 返回 `boolean` + 回滚；`fetchUserApps` 返回 `boolean` |

---

## 附：本快照不涉及的范围（明确排除）

- 后台 Admin 侧栏 `Sidebar.vue`（与 C 端导航无关）
- 后端 `user_app` 表结构与 `AppsController` 校验逻辑（3.1 复核后确认已满足要求，**零改动**）
- Architecture-7 / AI Coach / 新增 App / 新增一级导航 / 新增数据库表（本阶段红线）
