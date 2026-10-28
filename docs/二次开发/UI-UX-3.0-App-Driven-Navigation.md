# KnowFlow UI/UX 3.0 — App-Driven Navigation

> 目标：**菜单不再决定有什么功能。用户启用了什么 App，菜单才显示什么 App。**
> 基础：UI/UX 2.0 + 2.1 + Architecture-6。本阶段不新增 App、不新增一级导航、不新增业务系统/数据库/API。

---

## 0. 事实来源（本阶段唯一依据）

| 维度 | 来源文件 | 说明 |
|---|---|---|
| App 定义 | `frontend/src/apps/registry.ts` | 13 个真实 App（已与 `router/index.ts` 核对） |
| 用户 App 状态 | `frontend/src/stores/apps.ts` + `src/api/apps` |后端 `user_app` 为真源，Pinia 为状态缓存，localStorage 仅降级缓存 |
| 导航解析 | `frontend/src/apps/navigation.ts`（本阶段新建） | `resolveNavigation()` 唯一入口 |
| 一级导航 | `frontend/src/config/navigation.ts` | 固定 5 个入口 |

---

## 1. Before（改造前）

导航由 `config/navigation.ts` 的 `NAVIGATION` 常量**静态硬编码**，App 以字面量数组写死在二级菜单里：

```text
首页  /
学习  ├─ 学习中心 /learning/center
     ├─ 今日计划 /learning/today-plan
     ├─ 学习路径 /learning/paths
     ├─ 复习计划 /learning/review
     ├─ 闪卡        /learning/flashcards   ← 写死
     ├─ 错题本      /mistakes              ← 写死
     ├─ 智能测验    /learning/quiz         ← 写死
     ├─ 代码练习    /learning/code-practice ← 写死
     ├─ 挑战 /challenge
     ├─ 掌握度 /learning/mastery
     ├─ 学习报告 /learning/report
     ├─ 学习工作台 /workbench
     ├─ 番茄钟 /learning/pomodoro
     └─ 专注模式 /learning/focus
知识  ├─ 知识库 /knowledge
     ├─ 文档库 /docs
     ├─ 分类浏览 /categories
     ├─ 搜索 /search
     ├─ 知识图谱 /learning/knowledge-graph ← 写死
     ├─ 社区讨论 /community
     ├─ 学习小组 /study-group
     └─ 私信 /messages
AI    ├─ AI 助手 /chat
     ├─ 编程 Agent /coding/agent
     └─ 智能写作 /learning/writing        ← 写死
应用  /apps
```

**问题**：
1. 用户停用某 App，菜单**不会变化**（菜单由常量决定，与 UserApp 无关）。
2. 未安装的 App（费曼/康奈尔/记忆宫殿/思维导图/绘图/笔记…）在菜单中**完全找不到**。
3. 同一份 App 列表在 Sidebar、Home、LearningCenter、AppCenter 处各自判断，状态来源分散。

---

## 2. After（改造后）

```text
首页  /                                     （固定，次级无动态 App）
学习  ├─ [固定工作区]  学习中心/今日计划/学习路径/复习计划/挑战/掌握度/学习报告/学习工作台
     ├─ [固定·专注]   番茄钟 /learning/pomodoro、专注模式 /learning/focus   ← 沉浸式（§13）
     └─ [动态 App]   闪卡·练习·错题·代码练习·费曼       ← installed && enabled && navVisible
知识  ├─ [固定工作区]  知识库/文档库/分类浏览/搜索/社区讨论/学习小组/私信
     └─ [动态 App]   知识图谱·康奈尔·思维导图·笔记·绘图
AI    ├─ [固定入口]   AI 助手 /chat、编程 Agent /coding/agent
     └─ [动态 App]   智能写作
应用  /apps                                  （App 管理中心：我的应用 + 发现应用）
```

数据流：

```text
AppRegistry (registry.ts)
   +  UserApp (stores/apps.ts)
        ↓  resolveNavigation(isLoggedIn, isAdmin, userApps)
   NavigationTree (NavGroup[]，含动态 App 子菜单)
        ↓
   CSidebar（桌面）  +  CLayout 底部导航（移动端）   ← 共用同一 Resolver（§21）
```

---

## 3. Navigation Rules（动态规则）

### 3.1 可见性（§41）

```text
canShowInNavigation(app, userApp) =
      app.navVisible          （Registry：是否允许进导航）
  &&  userApp.installed       （用户已安装）
  &&  userApp.enabled         （用户已启用）
```

- 非法组合 `installed=false && enabled=true` 由 `stores/apps.ts` 归一化（`!next.installed → next.enabled = false`），导航侧视为不可见（§17）。
- **未登录**：不注入任何 App 子菜单（`auth: true`），仅保留固定入口（§46）。

### 3.2 排序（§18 / §91）

```text
order = userApp.sortOrder ?? app.order（Registry 默认）
```

`UserApp.sortOrder` 优先；未设置时用 Registry 的 `APP_NAV_META.order`。

### 3.3 分区（§19）

`AppDefinition.navSection` 决定 App 出现在哪个一级工作区的动态子菜单，仅在 `registry.ts` 集中定义（`APP_NAV_META`），Sidebar / Home / LearningCenter 不得各自硬编码分类。

| App key | name | category | navSection | order | defaultInstalled |
|---|---|---|---|---|---|
| flashcard | 闪卡 | MEMORY | learning | 1 | ✅ |
| quiz | 练习 | PRACTICE | learning | 2 | ✅ |
| mistake | 错题 | PRACTICE | learning | 3 | ✅ |
| coding | 代码练习 | CODING | learning | 4 | — |
| feynman | 费曼 | UNDERSTANDING | learning | 5 | — |
| focus | 专注 | FOCUS | **—（不注入）** | — | ✅ |
| knowledge-graph | 知识图谱 | UNDERSTANDING | knowledge | 1 | — |
| cornell | 康奈尔 | ORGANIZATION | knowledge | 2 | — |
| mindmap | 思维导图 | ORGANIZATION | knowledge | 3 | — |
| notes | 笔记 | ORGANIZATION | knowledge | 4 | — |
| drawing | 绘图 | ORGANIZATION | knowledge | 5 | — |
| ai-writing | 智能写作 | AI | ai | 1 | — |
| memory-palace | 记忆宫殿 | MEMORY | learning | — | — |

> **说明**：`category`（MEMORY/PRACTICE/…）是「学习方法类型」，`navSection`（learning/knowledge/ai）是「导航工作区」，二者不同维度。规范 §19 要求「严格使用 Registry.category、不在各页面自定义分类」——`navSection` 由 `category` 派生并**仅在 registry.ts 单点定义**，各消费方一律从 Resolver 取，不重复分类。

### 3.4 空工作区（§24）

某工作区无任何动态 App 时，**保留固定工作区入口**（学习首页 / 知识首页等），不出现空 section。

### 3.5 专注（§13 · 例外）

`focus` 的 `navVisible = false`，**不进入动态子菜单**。`番茄钟 /learning/pomodoro` 与 `专注模式 /learning/focus` 作为固定入口保留，沉浸式体验不被统一化破坏。

---

## 4. App State（§40 五层核心状态）

| 层 | 含义 | 实体 |
|---|---|---|
| AppDefinition | 系统有什么 | `registry.ts` / `AppDefinition` |
| Product | 可以买什么 | `products`（服务端唯一真源价格） |
| Entitlement | 用户拥有什么 | `purchase` / `owned` |
| **UserApp** | **用户启用什么** | `user_app`（installed / enabled / sortOrder） |
| **Navigation** | **用户看到什么** | `resolveNavigation()` 输出 |

购买权限（Product/Entitlement）与导航权限（UserApp.enabled）严格分离，不混淆（§39）。

---

## 5. UserApp

```ts
interface UserAppState {
  installed: boolean
  enabled: boolean
  sortOrder: number
}
```

- **后端 `user_app` = 真源**；Pinia = 前端状态缓存；localStorage = 降级读缓存（§46，离线不决定任何权限）。
- 启动/登录：`hydrate()`（本地缓存首屏 → 服务端同步覆盖）。本阶段在常驻 Shell `CLayout.onMounted` 增加一次同步，**保证任意深链直达时动态菜单已就绪**（此前仅 Home/AppCenter 挂载时同步）。
- 用户切换/登出：store 内 `watch(uid)` 重置，避免串号（§45）。
- 写入：乐观更新本地 + 尝试同步服务端；失败保留本地并标记 `syncPending`（§132）。

### 停用 ≠ 删除（§16）

`enabled=false` **只隐藏/停用**，不删除：
Flashcard 数据 / Quiz 数据 / Mistake 数据 / LearningEvent / Mastery / Review / Entitlement 全部保留，重新启用无需再次购买（§44）。

---

## 6. Dynamic Menu（动态行为）

| 用户动作 | 菜单反应 | 数据 |
|---|---|---|
| 启用 Feynman | 学习 → 立即出现「费曼」 | — |
| 停用 Feynman | 学习 → 立即消失 | 全部保留 |
| 启用 Knowledge Graph | 知识 → 立即出现「知识图谱」 | — |
| 停用 Knowledge Graph | 知识 → 立即消失 | 全部保留 |
| 兑换付费 App | Entitlement → UserApp(installed+enabled) → 菜单自动出现（§42） | 新增 |
| 重新启用 | 立即出现，无需再购买（§44） | — |
| A 登出 → B 登录 | 导航立即按 B 的 UserApp 重算（§45） | 不串号 |

**实现机制**：`resolveNavigation()` 读取响应式 `useAppStore().userApps`，`CSidebar` / `CLayout` 用 `computed` 包装 → UserApp 更新后导航**即时刷新，无需刷新网页**（§49）。

---

## 7. Learning 工作区（§9）

- 永远保留：学习中心、今日计划、学习路径、复习计划、挑战、掌握度、学习报告、学习工作台。
- 动态追加：仅 `navSection=learning` 且 **installed && enabled && navVisible** 的 App。
- 学习中心页（LearningCenterPage）快捷入口中的 **App 类链接**（闪卡复习 / 代码练习 / 知识图谱）改为按 `UserApp` 启用态过滤（§34），不再硬编码展示已停用 App；工作区入口（学习路径/复习计划/掌握分布/行为事件/AI 问答）与专注工具保持固定（§13）。

## 8. Knowledge 工作区（§10）

- 永远保留：知识库、文档库、分类浏览、搜索、社区讨论、学习小组、私信。
- 动态追加：仅 `navSection=knowledge` 且启用态满足的 App（知识图谱/康奈尔/思维导图/笔记/绘图）。

## 9. Mobile（§22 / §84）

- 底部一级导航**固定 5 个**：首页、学习、知识、AI、应用。
- 二级 App：由同一 `resolveNavigation()` 推导，抽屉内按当前工作区动态显示（§21：Desktop / Mobile 共用 Resolver，不是两套）。
- 桌面与移动的差别**只在展示形态**，不在数据来源。

## 10. App Center（§25-28 / §65）

`/apps` 是**唯一完整 App 浏览与管理入口**：

- 「我的应用」：`installed=true` 的 App，可**启用 / 停用**（即「显示在菜单 / 隐藏在菜单」，仍是 `UserApp.enabled`）。
- 「全部」：按 category 浏览 + 付费精选（Product/Entitlement）。
- CTA 由 `resolveAppActionState`（Architecture-5）统一：获取 / 兑换 / 打开 / 启用 / 暂不可用（§37：不在 Home / AppCenter / Sidebar 各写一套状态判断）。
- 购买与权益**未改动**（§30）：PurchaseService / Entitlement / Wallet / Order 零修改。

## 11. Home（§32-33）

Home「我的应用」= `appStore.enabledApps.slice(0, 6)`（>4~6 个不再展示），由 `UserApp.enabled + Registry` 驱动（Architecture-1 已实现，本阶段核对保持）。

---

## 12. 未做的事（有意不做）

| 项 | 原因 |
|---|---|
| 新增一级导航 | 规范 §73 禁止（钱包/设置/我的/统计/图谱 均不得升为一级） |
| 「工具」一级导航 | 规范 §12 禁止为动态菜单强行创造 |
| focus 套动态壳 | 规范 §13，保持沉浸式 |
| 重写 Purchase / Entitlement / Wallet | 规范 §30 禁止 |
| 重写 LearningEvent / Reward / Mastery / SRS / NextAction | 规范 §31 禁止 |
| 新建 AppKnowledgeContext / WorkflowContextService / GlobalLearningContext | 规范 §45 禁止 |
