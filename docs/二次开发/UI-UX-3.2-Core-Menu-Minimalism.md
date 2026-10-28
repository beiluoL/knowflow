# KnowFlow UI/UX 3.2 — Core Menu Minimalism（核心菜单极简化 + App 全动态化）

> 阶段代号：UI/UX 3.2 · 一键实施（Vibe Coding）· 本地直接改码，未 commit / 未 push
> 前置阶段：UI/UX 3.1（App Management Workspace）已完成，App Registry + UserApp + Navigation Resolver + App Center 已落地
> 质量基线（3.1 末）：Vitest 165/165 · TypeScript 0 · Vite Build PASS · Backend 71/71
> 本阶段结果：Vitest **201/201** · TypeScript **0** · Vite Build **PASS** · Backend **71/71**

---

## 0. 一句话目标

把「学习 / 知识」工作区里残留的**固定二级业务菜单**彻底收敛为：

> **1 个核心固定入口（工作区首页） + N 个用户 enabled 的 App（由 UserApp 动态注入）**

导航不再试图展示「产品的全部能力地图」，而是只展示「用户当前正在使用的能力」。

---

## 1. 旧的固定菜单 → 新的动态 App 菜单

### 1.1 旧结构（3.1 末，仍然存在大量固定 children）

```
学习
├── 学习中心         ← 固定
├── 今日计划         ← 固定
├── 学习路径         ← 固定
├── 复习计划         ← 固定
├── 闪卡             ← 已由 resolver 动态注入
├── 练习             ← 已由 resolver 动态注入
├── 错题             ← 已由 resolver 动态注入
├── 挑战             ← 固定
├── 掌握度           ← 固定
├── 学习报告         ← 固定
├── 学习工作台       ← 固定
├── 番茄钟           ← 固定
└── 专注模式         ← 固定

知识
├── 知识库           ← 固定（与「知识首页」重复）
├── 文档库           ← 固定
├── 分类浏览         ← 固定
├── 搜索             ← 固定
├── 知识图谱         ← 已由 resolver 动态注入
├── 思维导图         ← 已由 resolver 动态注入
├── 社区讨论         ← 固定
├── 学习小组         ← 固定
└── 私信             ← 固定
```

### 1.2 新结构（3.2 末）

```
首页                                               ← 固定（NextAction / Learning State / Today / Enabled Apps）
学习
└── 学习首页                                        ← 固定（Dashboard，承载被下沉功能）
    └── [enabled learning Apps...]                  ← 动态：闪卡 / 练习 / 错题 / 费曼 / 代码练习 / 记忆宫殿 …
知识
└── 知识首页                                        ← 固定（知识工作区主入口）
    └── [enabled knowledge Apps...]                 ← 动态：知识图谱 / 康奈尔 / 思维导图 / 笔记 / 绘图 …
AI
└── AI 助手                                         ← 固定
    └── [enabled AI Apps...]                        ← 动态：智能写作（启用后出现）
应用
└── 应用中心                                        ← 固定（完整 App 管理中心：我的应用 / 发现应用）
```

**核心入口数量：5（首页 / 学习 / 知识 / AI / 应用）。动态部分数量由 App 决定。**

### 1.3 关键取舍（硬约束守则）

- **删入口 ≠ 删功能**：所有旧固定菜单对应的**路由 / 页面 / API / 数据库 / 业务能力一律保留**，仅把入口从 Sidebar 移到「工作区首页卡片」或「顶栏用户菜单」或「App 动态菜单」（§9 / §51-59）。
- **不新增一级导航**：仍是 5 个（§5 / §172）。
- **不新增 App / 不新增 App State 系统**（§173-174）。
- **不新增 API**（除非某功能删菜单后完全没有入口且真实必须 —— 本阶段未触发，0 新增 API）（§171）。
- **Focus（番茄钟 / 专注）保持沉浸式**：`navVisible=false`，不进动态 App 菜单，避免「学习 → 专注 / Focus」重复；由顶栏番茄钟按钮 + 学习首页卡片承载（§13 / §16 / §74 / §152）。
- **社区三维（讨论 / 小组 / 私信）**：并入顶栏用户菜单 + 知识首页卡片，不再占任何工作区 Sidebar（§23 / §25）。

---

## 2. Navigation Matrix（最终导航矩阵）

| 工作区 | 固定入口 | 动态入口 |
| --- | --- | --- |
| 首页 | 首页 | —（内部展示 Enabled Apps，最多 4~6 + 查看全部，§33-35） |
| 学习 | 学习首页 | enabled **learning** Apps（来自 UserApp × `navSection='learning'`） |
| 知识 | 知识首页 | enabled **knowledge** Apps（来自 UserApp × `navSection='knowledge'`） |
| AI | AI 助手 | enabled **ai** Apps（来自 UserApp × `navSection='ai'`） |
| 应用 | 应用中心 | —（完整管理中心：我的应用 / 发现应用） |

> 动态部分由 `resolveNavigation()`（`frontend/src/apps/navigation.ts`）按 `registry × UserApp` 推导，
> 固定部分由 `NAVIGATION`（`frontend/src/config/navigation.ts`）收口。**两套同源、不重复硬编码。**

---

## 3. Feature Routing Matrix（功能路由矩阵 — 删菜单后功能去向）

> 路由路径为 3.2 实施时的真实 `router/index.ts` 路径；功能均保留，仅入口位置变化。

### 3.1 学习类（承载于「学习首页」卡片 / 页面内部）

| 功能 | 新位置 | 路由（保留） |
| --- | --- | --- |
| 今日计划 | 学习首页「快捷入口」卡片 | `/learning/today-plan` |
| 学习路径 | 学习首页卡片 / 页面内部 | `/learning/paths` |
| 复习计划 | 学习首页卡片 | `/learning/review` |
| 掌握度 | 学习首页卡片 / 学习概览 | `/learning/mastery` |
| 学习报告 | 学习首页卡片 | `/learning/report` |
| 挑战 | 学习首页卡片 | `/challenge` |
| 学习工作台 | 学习首页卡片 | `/workbench` |
| 番茄钟 / 专注 | 顶栏番茄钟 + 学习首页卡片（沉浸式，非动态 App） | `/learning/pomodoro` · `/learning/focus` |

### 3.2 知识类（承载于「知识首页」/ 动态 App）

| 功能 | 新位置 | 路由（保留） |
| --- | --- | --- |
| 文档库 | 知识首页「快速访问 / 查看全部」 | `/docs` |
| 分类浏览 | 知识首页分类树 | `/categories` |
| 搜索 | 知识首页搜索框 | `/search` |
| 知识图谱 | 知识 Sidebar 动态 App（启用后）+ 知识首页「我的知识工具」 | `/learning/knowledge-graph` |
| 思维导图 | 知识 Sidebar 动态 App + 知识首页 | `/mindmap` |
| 康奈尔 | 知识 Sidebar 动态 App | `/workbench/notes` |
| 记忆宫殿 | 学习 Sidebar 动态 App（3.1 遗留 navSection 已补齐） | `/workbench/palace` |
| 笔记 | 知识 Sidebar 动态 App | `/notes` |
| 绘图 | 知识 Sidebar 动态 App | `/drawing` |

### 3.3 社区 / 用户类（承载于顶栏用户菜单 / 页面内部）

| 功能 | 新位置 | 路由（保留） |
| --- | --- | --- |
| 社区讨论 | 顶栏用户菜单 + 知识首页卡片 | `/community` |
| 学习小组 | 顶栏用户菜单 / 知识首页 | `/study-group` |
| 私信 | 顶栏用户菜单 / 社区内部 | `/messages` |
| 编程 Agent | 学习首页卡片（工具区） | `/coding/agent` |
| 智能写作（AI） | AI Sidebar 动态 App（启用后） | `/learning/writing` |

---

## 4. 实现要点（代码层面）

| 文件 | 改动 |
| --- | --- |
| `config/navigation.ts` | `NAVIGATION` 收敛为「1 固定入口 + 0 固定业务 children」；新增 `NavGroup.match` 字段（只用于父级高亮 / 面包屑归属，不渲染为菜单）；`findGroupByPath` 改为「最长前缀优先」；修复 `pathMatches('/')` 误匹配所有路由的 latent bug |
| `apps/registry.ts` | 补齐 `memory-palace` 的 `APP_NAV_META.navSection='learning'`（3.1 遗留项，§71），真实 `category=MEMORY` 未改动 |
| `apps/navigation.ts` | 注释更新：明确 3.2 输出契约「1 固定 + N 动态 App」；可见性 / 排序逻辑不变 |
| `components/layout/CSidebar.vue` | `isGroupActive` 改用 `findActiveGroup`（与 `config` 同源），父级高亮 / 自动展开对下沉页面生效；同步修复 `pathMatches('/')` |
| `views/LearningCenterPage.vue` | 快捷入口重构为「页面内部卡片导航」（前 8 + 更多工具），App 类链接按 UserApp 启用态过滤 |
| `views/KnowledgeHome.vue` | 新增「我的知识工具」动态区（数据只来自 UserApp，未启用不渲染），非固定菜单 |
| `views/Community.vue` | 面包屑 `知识库` → `知识首页`（修正已删除菜单残留） |
| `apps/__tests__/navigation-minimal.spec.ts`（新增） | 覆盖 §140-153：核心入口恒定、启用/停用即时反映、旧固定菜单不再出现、Route 保留、Memory Palace 归位、Focus 不重复、排序、未登录隔离、`match` 工作区归属 |

---

## 5. 验收门（四门全绿）

| 门 | 结果 |
| --- | --- |
| Vitest | **201/201**（导航 minimal +36） |
| `vue-tsc -b` | **0 errors** |
| `vite build` | **PASS** |
| `mvn test`（后端零改动） | **71/71** |

> 浏览器像素级走查（§205-226）受沙箱限制无法真人验收，已在执行报告 §22 标注 `⚠️ Not verified`，需本地 `npm run dev` 复核。

---

## 6. 最终产品理念（§234-236）

- **Core 提供工作区**（首页 / 学习首页 / 知识首页 / AI 助手 / 应用中心）
- **App 提供能力**（闪卡 / 知识图谱 / 智能写作 …）
- **UserApp 决定启用什么**
- **Navigation 反映用户选择**
- **Sidebar 不再承担整个产品的信息架构** —— 它只是「个人工作区」的快捷入口

> 功能越多，用户看到的菜单**不一定越长**：只有真正启用的 App，才会出现在导航里。
