# KnowFlow UI/UX 3.1 — App Management Workspace

> 把 **App → UserApp → Navigation** 做成用户可操作的「应用工作区管理系统」。
> 基础：UI/UX 3.0（App-Driven Navigation）。不新增 App / 一级导航 / 数据库 / API / App State 系统。

---

## 1. 五层职责（严格区分，§6-7）

| 层 | 含义 | 实体 | 判断依据 |
|---|---|---|---|
| AppDefinition | 系统有哪些 App | `registry.ts` | Registry |
| Product | 卖什么 | `products`（服务端） | 价格/商品 |
| Entitlement | 用户拥有什么 | `purchase.owned` | 后端 |
| **UserApp** | **用户安装/启用什么** | `installed` / `enabled` / `sortOrder` | **后端 `user_app` 真源** |
| **Navigation** | **用户看到什么** | `resolveNavigation()` | registry × UserApp |

**明确禁止混淆**（§7）：

- ❌ 用 Coin 判断 `enabled`
- ❌ 用 Entitlement 判断菜单可见性
- ❌ 用 Product 判断菜单可见性
- ✅ 菜单可见性**只由 `UserApp.installed && enabled` 决定**

---

## 2. UserApp（§8-9）

```ts
interface UserAppState {
  installed: boolean
  enabled: boolean
  sortOrder: number
}
```

- **唯一真源**：后端 `user_app`（`UserAppService`），Pinia 为状态缓存，localStorage 为降级读缓存（§203-208）。
- **未新增第二套状态系统**（§10）：无 AppPreference / MenuPreference / NavPreference / AppVisibilityPreference。
- 缓存按用户隔离：`knowflow:apps:<userId>`（§214-216），不使用全局 `knowflow:apps`。

### 最终数据流（§170）

```text
appsApi (GET/PUT /api/apps/user-apps)
      ↓
Pinia UserApp (stores/apps.ts)
      ↓
Navigation Resolver (apps/navigation.ts)
      ↓
Sidebar / Mobile / Home / AppCenter   ← 视图层只调 Store，不直接 axios（§159-166）
```

---

## 3. App State Matrix（§300）

| installed | enabled | Navigation（菜单） | 我的应用分组 | CTA |
|---|---|---|---|---|
| `false` | `false` | 隐藏 | 不显示（在「发现应用」） | 获取 |
| `true` | `false` | **隐藏** | 已停用 | 启用 |
| `true` | `true` | **可见** | 已启用 | 打开 / 停用 |
| `false` | `true` | 隐藏（非法组合，前后端均归一化） | — | — |

> 已由 `src/apps/__tests__/app-management.spec.ts` 覆盖（含非法组合防御）。

### 3.1 叠加「平台停用」维度（§34/§175，2026-09-01 补齐）

菜单可见性 = `navVisible` **且** `status === 'ACTIVE'` **且** `installed` **且** `enabled`。

| installed | enabled | 平台 status | 导航 | 我的应用分组 | CTA |
|---|---|---|---|---|---|
| `true` | `true` | `ACTIVE` | 可见 | 已启用 | 打开 / 停用 |
| `true` | `true` | `BETA` / `LEGACY` | **隐藏** | 已启用 | 暂不可用 |
| `true` | `false` | 任意 | 隐藏 | 已停用 | 启用 |

判定函数与 CTA **共用** `actionState.isAppPlatformDisabled()`，不重复实现（避免「CTA 说不可用、菜单却还能点」）。
新增不变量测试：任何 `navVisible && navSection` 的 App 必须 `status === 'ACTIVE'`（当前 13 个 App 全部满足，属潜伏缺陷防御）。

---

## 4. Navigation Matrix（§301 · 由 Registry 分区决定，§94-98）

| navSection（由 Registry 派生） | 代表 App | 启用后进入 |
|---|---|---|
| `learning` | 闪卡 / 练习 / 错题 / 代码练习 / 费曼 | 学习菜单 |
| `knowledge` | 知识图谱 / 康奈尔 / 思维导图 / 笔记 / 绘图 | 知识菜单 |
| `ai` | 智能写作 | AI 菜单 |
| `—`（focus） | 专注 | **不进动态子菜单**，沉浸式固定入口（§13） |

**Design Deviation 记录（§99-100）**：
Registry 的真实 `category` 是 `MEMORY/PRACTICE/UNDERSTANDING/ORGANIZATION/FOCUS/CODING/AI`（学习方法类型），
与规范示例中的 `category=learning/knowledge` 并非同一维度；且 Feynman 与 KnowledgeGraph 同属 `UNDERSTANDING` 却需分处学习/知识两个工作区。
因此**没有为迎合 UI 去改 Registry 的 category**，而是新增 `navSection` 字段（导航工作区维度），
由 `registry.ts` 的 `APP_NAV_META` **单点定义**，各消费方一律从 Resolver 读取（§87-93）。

---

## 5. App Lifecycle（§302）

```text
Discover（发现应用）
   ↓ 获取 / 兑换
Installed (installed=true, enabled=true)
   ↓
Navigation（出现在对应工作区菜单）
   ↓ 停用
Hidden (enabled=false)  ← installed 仍为 true，数据全部保留
   ↓ 重新启用（无需再获取 / 兑换 / 购买）
Navigation（回到菜单）
```

已由单测 `App Lifecycle` 用例覆盖全链路。

---

## 6. App Center（§11-13 · 唯一完整管理中心，§113-120）

页面结构：

```text
应用

[我的应用] [发现应用]

我的应用
─────────────────
已启用（N 个 · 显示在菜单）      ← 可排序：拖动 / 上移下移
  ┌────────────────────────┐
  │ 闪卡                   │
  │ 通过主动回忆巩固知识    │
  │ ✓ 已启用    [打开][停用]│
  └────────────────────────┘
已停用（N 个 · 已从菜单隐藏，数据保留）
  ┌────────────────────────┐
  │ 费曼                   │
  │ 用自己的话解释知识      │
  │ ⃠ 已停用     [启用]     │
  └────────────────────────┘
[发现更多应用 →]

发现应用
─────────────────
付费精选（服务端价格 / 拥有态）
未安装的应用（按 Registry 分类筛选）
```

- **只有 2 个状态组**（§15-19）：已启用 / 已停用。
- 卡片只放：图标 / 名称 / 一句描述 / 状态 / 操作（§20）；
  **不显示** appKey、productId、entitlementId、**version**、capability（§21、§174）。
- 状态用「文字 + icon + 底色」，不只用颜色区分（§242-243）。
- 发现应用只列**未安装**的 App，已启用的不会重复以商品形态出现（§104）。

### 语言统一（§22-25）

| 场景 | 文案 |
|---|---|
| 已启用 → 隐藏 | **停用** |
| 已停用 → 显示 | **启用**（CTA 复用 `actionState.ts` 的「重新启用」） |
| 未安装 → 获取 | 获取 |

**不出现**用「安装 / 下载 / 删除」表达 enabled 的文案。

---

## 7. Enable / Disable（§26-60）

```
enable  → PUT /api/apps/user-apps/{appKey}  { installed:true,  enabled:true  }
disable → PUT /api/apps/user-apps/{appKey}  { installed:true,  enabled:false }
```

- **单一 API**（§42-44）：不新增 `/install` `/uninstall` `/enable` `/disable` 多套接口。
- 只改 `enabled`；**不修改 Entitlement**（§27）、**不删除** LearningEvent / Mastery / Review / App 数据 / 购买记录（§28-32）。
- 重新启用 = `enabled=true`，**不要求重新兑换 / 安装 / 购买**（§33-36）。
- **幂等**（§57-60）：enable 10 次仍为 `enabled=true`，按 `appKey` upsert，不产生重复记录。
- **按钮级 loading + 禁用**（§51-54）：`store.isUpdating(key)` 驱动，防连点 10 次；不阻塞整个页面。
- **失败处理**（§49-50、§278）：
  - API 失败 → **回滚乐观更新**，`syncPending=true`，返回 `false`
  - UI → Toast「操作失败，请重试」+ 按钮恢复
  - **不伪造成功**
- 成功 Toast 用**用户语言**（§224-229）：
  - 启用：`已添加到学习菜单「费曼」`（工作区名由 `navSectionLabel()` 动态取）
  - 停用：`已从菜单隐藏「费曼」`
  - 不出现 `UserApp updated successfully` / `API success`

### 实时更新（§37-40）

`resolveNavigation()` 读取响应式 `userApps`，`CSidebar` / `CLayout` 用 `computed` 包装 →
**启用/停用后立即刷新导航，不需要刷新网页、重新登录或重启 App**。

---

## 8. Sort（§61-86）

- **持久化到 `UserApp.sortOrder`**（§69），导航排序 `sortOrder ?? registry.order`（§70-73），**不按 App 名字母序**（§72）。
- **桌面**：原生 HTML5 Drag & Drop（项目无拖拽库，按 §63-65 不引入重量级第三方）。
- **移动端 / 键盘**：上移 / 下移按钮（§66-67、§248-250），按钮 44px 热区（§255-256）。
- 排序**只对已启用 App 生效**（§179-181）；已停用 App 不参与排序，统一排在后面。
- **单一排序源**（§79-86）：Sidebar / Mobile / Home / AppCenter 全部读取 `Navigation Resolver` 的结果，
  页面不自造顺序（Home 直接读 `store.enabledApps`，其排序同样由 `sortOrder` 决定）。

---

## 9. Navigation

```text
UserApp 变更
   ↓
Navigation Resolver（唯一排序与可见性裁决）
   ↓
Sidebar（桌面） + Bottom Nav / Drawer（移动端）
```

- 停用 → 从 Sidebar 消失，但仍在「我的应用 → 已停用」中（§129-132）。
- 排序后 → 菜单 / Home 我的应用 / App Center / 移动端**四方同步**（§75-78）。
- 移动端动态 App 过多时不会挤满底部导航（底部恒为 5 个一级入口，App 在抽屉内展开）。

---

## 10. Purchase 闭环（§133-146）

```text
Purchase → Entitlement → UserApp(installed+enabled) → Navigation
```

- 兑换成功：`store.install(appKey)` 自动写入 UserApp，**不需要用户刷新**（§137-140）。
- CTA「立即打开」跳转**真实 Registry Route**（§141-143），不接受远程路由 / `javascript:`（§144-146）。
- **未修改** PurchaseService / Entitlement / Wallet / Order（§30、§136）。

---

## 11. Security（§147-161 · 后端已满足，本阶段零后端改动）

已在源码中逐条核对（`AppsController` / `UserAppService` / `UserAppUpdateRequest`）：

| 要求 | 实现 |
|---|---|
| GET/PUT 只操作当前用户（§147-149） | `SecurityUtils.getCurrentUserId()`，DTO **不含 userId** |
| appKey 校验、未知 App 拒绝（§150-151） | `registry.isValid(appKey)` → `BusinessException(INVALID_APP)` |
| `installed=false && enabled=true` 归一化（§152） | 后端强制 `enabled=false`；前端 Store 同步归一化；Resolver 侧亦防御 |
| sortOrder 合法整数（§153-155） | `Math.max(0, sortOrder)`，负值与异常值被夹紧为 0 |
| 幂等 upsert（§57-60） | 存在则 update，不存在则 insert；`DuplicateKeyException` 兜底 |
| 统一错误（§156-157） | 复用 `BusinessException` / `GlobalExceptionHandler` / `Result`，无第二套响应 |
| 前端不散落 axios（§158-161） | 统一 `appsApi.updateUserApp()`，视图层只调 Store，未新增 AppApi2 |

---

## 12. Mobile / Dark / Accessibility / Performance

| 维度 | 做法 |
|---|---|
| **Mobile**（§251-256） | 桌面侧边栏 / 移动端底部导航+抽屉；App Center 390px 单列、桌面多列；操作按钮 ≥44px 热区 |
| **Dark**（§257-258） | 全部走 `--kb-*` 令牌，未新增主题 |
| **Accessibility**（§244-250） | 真实 `<button>`；每个操作带 `aria-label`（如「停用「费曼」」）；`focus-visible` 使用 `--kb-ring`；拖拽**同时提供键盘可达的上移/下移**替代操作 |
| **Performance**（§259-264） | 启用/停用为局部状态更新，**不整页刷新、不重新拉取全部数据**；排序仅在提交时写回；成功后不做全量 reload |

---

## 13. 未做的事（有意不做）

- 新增 `/install` `/uninstall` `/enable` `/disable` 多套 API（§43）→ 复用单一 `PUT /user-apps/{appKey}`。
- 新增 AppPreference / MenuPreference 等第二套状态（§10）。
- 引入拖拽第三方库（§65）。
- 重写 Registry 分类以迎合 UI（§98-100）→ 改为新增 `navSection` 维度并记录 Design Deviation。
- 「菜单预览页」（§184）→ 不做，直接让 Sidebar 实时变化。
- 修改 Purchase / Entitlement / Wallet / LearningEvent / Reward / Mastery / SRS / NextAction。

---

## 14. 加载 / 失败 / 离线三态（§137-140 · 2026-09-01 补齐）

| 层 | 职责 |
|---|---|
| `stores/apps.ts` | `fetchUserApps()` 返回 `Promise<boolean>`：成功 `true`；失败 `false` 且 `syncPending=true`；未登录返回 `true`（无可同步，不算失败） |
| `AppCenterPage` | `mineState` / `productsState` ∈ `loading \| ready \| error`，驱动内容区渲染 |
| `SkeletonList` | loading 占位（`type=list` 用于我的应用，`type=card cols=3` 用于付费精选） |
| `ErrorState` | error 展示 + 「重试」按钮（`mineState` → `store.fetchUserApps()`；`productsState` → `fetchProducts()`） |

三条原则：

1. **不整页 Error**：Header 与 Tab 始终可用，用户可切到「发现应用」继续操作。
2. **失败不级联**：付费精选接口失败只影响该区块，下方免费应用照常渲染。
3. **离线可用不算失败**：同步失败但本地有缓存 → 按 `ready` 展示缓存，并显示「当前显示本地缓存，联网后会自动同步」（`role="status"`）。

单测覆盖：`apps-store.spec.ts` 新增「成功 / 失败无缓存 / 失败有缓存」三例。

---

## 15. 状态一致性约定（避免多处自造规则）

| 判定 | 唯一实现位置 | 消费方 |
|---|---|---|
| CTA 文案与可用态 | `components/workflow/actionState.ts → resolveAppActionState()` | AppCard、NextActionCard、产品卡 |
| 平台是否停用 | `actionState.ts → isAppPlatformDisabled()` | **CTA + `canShowInNavigation()` 共用** |
| 是否进导航 | `apps/navigation.ts → canShowInNavigation()` | CSidebar、CLayout、Home、LearningCenter |
| 菜单顺序 | `UserApp.sortOrder ?? registry.order` | Resolver 与所有视图（唯一排序源） |
| 分区归属 | `registry.ts → APP_NAV_META` | 仅 Resolver 读取，UI 层不各写一份 |
| 写操作 | `useAppStore`（`install/enable/disable/reorder`） | 仅 AppCenterPage 调用，组件不直接 axios |

新增 App 只需两处：① `APP_DEFINITIONS` 加一条；② `APP_NAV_META` 加一行（不进菜单则不加）。其余全部自动生效。
