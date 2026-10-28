# Phase UI-2 执行报告 — App Shell / Navigation

> **执行日期**：2026-08-30
> **范围**：仅 `frontend/`（未修改 backend / 数据库 / 业务逻辑 / AI / SRS / LearningEvent / KnowledgeMastery）
> **配套文档**：`Phase-UI-2-Route-Map.md`（完整路由地图）、`Phase-UI-2-Navigation-Naming.md`（命名对照表）
> **未 commit、未 push**；**未执行** `git reset` / `git clean` / `git checkout .`

---

## 1. Phase UI-2 目标

解决信息架构与全局外壳：顶级导航过多、入口重复、命名不一致、当前位置不明确、移动端导航缺失。

让用户自然理解：**我在哪 → 我能去哪 → 我从哪开始学 → 知识在哪 → 任务在哪 → AI 在哪 → 进度在哪**。

---

## 2. 修改前导航结构（真实扫描）

C 端顶部导航（`CTopNav.vue`）为**单一顶部导航，无侧边栏**：

```
首页 │ 学习工作台 │ 知识库▾ │ 学习▾ │ 社区▾ │ AI助手▾ │ 成长▾ │ 我的▾
```

- **8 个顶级入口**，其中 6 个为下拉，二级项合计 **30 个**
- 导航数据硬编码为 **6 个数组**：`knowledgeMenu` / `learningMenu` / `communityMenu` / `aiMenu` / `growthMenu` / `personalMenu`，外加 `mobileLinks`
- **路由与导航两套命名**：导航菜单手写 path，Router 是另一套，无单一数据源
- 已知问题：
  - 「学习工作台」出现 **2 次**（顶级直链 + 「学习」下拉内）
  - 顶级「知识库▾」与二级项「知识库」**同名**
  - `/learning/center`（`LearningReport.vue`）与 `/learning/pomodoro`（`LearningCenter.vue`，页内 `<h1>` 为「学习中心」）**两个页面都叫"学习中心"**
  - 移动端仅有右侧抽屉（内嵌在 CTopNav 里），**无底部导航**
  - 「成长」是产品内部概念，非用户语言

---

## 3. 修改后导航结构

```
┌──────────────────────────────────────────────┐
│ ☰  KnowFlow    🔍搜索…        AI 🍅 🔔 👤  │  Header（固定 h-14）
├──────────────┬───────────────────────────────┤
│ 🏠 首页      │                               │
│ 🎓 学习    ▸ │          主内容区             │  Sidebar（固定，可折叠）
│ 📚 知识    ▸ │          （仅 Main 滚动）     │
│ 🤖 AI      ▸ │                               │
│ 👥 社区    ▸ │                               │
│ 👤 我的    ▸ │                               │
│ ─────────── │                               │
│ ‹ 收起菜单   │                               │
└──────────────┴───────────────────────────────┘
        移动端（<1024px）：抽屉 + 底部 5 个入口
```

**Header 只保留**：Logo + 搜索 + AI 快捷 + 番茄钟 + 通知 + 用户菜单（§27）。
**导航职责全部下沉到侧边栏。**

---

## 4. 顶级导航数量

| | 数量 |
|---|---:|
| 修改前 | **8** |
| 修改后 | **6** |

**6 个**：首页、学习、知识、AI、社区、我的。

「学习工作台」由顶级降为二级；「成长」整体并入「我的」；「知识库▾」更名为「知识」。
**功能零删除**——原 30 个二级项全部保留并重新归类，另新增 6 个原先无入口的页面（挑战、掌握度、学习报告、专注模式、思维导图、绘图），现共 **38 个二级项**。

---

## 5. Navigation Tree（真实）

```
首页                        /            （直达）

学习                                     （14 项）
├── 学习中心                /learning/center
├── 今日计划                /learning/today-plan
├── 学习路径                /learning/paths
├── 复习计划                /learning/review
├── 闪卡                    /learning/flashcards
├── 错题本                  /mistakes
├── 智能测验                /learning/quiz
├── 代码练习                /learning/code-practice
├── 挑战                    /challenge
├── 掌握度                  /learning/mastery
├── 学习报告                /learning/report
├── 学习工作台              /workbench
├── 番茄钟                  /learning/pomodoro
└── 专注模式                /learning/focus

知识                                     （9 项）
├── 知识库                  /knowledge
├── 文档库                  /docs
├── 分类浏览                /categories
├── 搜索                    /search
├── 知识图谱                /learning/knowledge-graph
├── 收藏夹                  /favorites
├── 笔记                    /notes
├── 思维导图                /mindmap
└── 绘图                    /drawing

AI                                       （3 项）
├── 智能问答                /chat
├── 编程 Agent              /coding/agent
└── 智能写作                /learning/writing

社区                                     （3 项）
├── 社区讨论                /community
├── 学习小组                /study-group
└── 私信                    /messages

我的                                     （9 项）
├── 任务中心                /tasks
├── 任务清单                /task-list
├── 日历                    /calendar
├── 每日打卡                /check-in
├── 习惯打卡                /habits
├── 成就                    /achievements
├── 称号                    /kb-titles
├── 个人中心                /profile
└── 背景设置                /settings/background
```

---

## 6. Router 修改

| 改动 | 说明 |
|---|---|
| 新增 `afterEach` | 页面标题由 `@/config/navigation.ts` **自动推导**：`{页面} · {模块} · KnowFlow`（§12/§20） |
| 更新布局注释 | 明确 `layout:'c'` = 顶栏 + 侧栏 + 主区；`fullscreen` 只取消最大宽度，仍保留导航壳（§40） |

**为什么不给 90 条路由逐条手写 `meta.title`**：
一旦逐条维护，"导航叫什么"与"页面标题叫什么"迟早各说各话——本项目此前就出现了两个页面都自称"学习中心"的情况。改为单一数据源推导后，二者永远一致。

**URL 未做任何修改**，详见 §14。

---

## 7. AppShell 修改（`CLayout.vue` 重写）

| 项 | 修改前 | 修改后 |
|---|---|---|
| 结构 | 顶栏 + 主区（**无侧边栏**） | **Header + Sidebar + Main**（§36） |
| 滚动 | 整页滚动 | Header/Sidebar 固定，**只有 Main 滚动**（§48，避免三层滚动） |
| 侧边栏 | 无 | `≥1024px` 常驻，可折叠 240px ↔ 64px |
| 移动端 | CTopNav 内嵌右侧抽屉 | 左侧抽屉 + **底部 5 个入口导航**（§31-§33） |
| 内容宽度 | `max-w-7xl`(1280) | 常规页统一 `max-w:1280px`；`fullscreen` 页不限宽（§37） |
| 折叠持久化 | 无 | `localStorage: csidebar-collapsed`（§47，不新建状态系统） |
| 动画 | — | 折叠/展开 **200ms**（§57，轻量） |

---

## 8. Sidebar 修改

C 端**原本没有侧边栏**，本次新建 `src/components/layout/CSidebar.vue`：

| 能力 | 实现 |
|---|---|
| 一级 + 二级导航 | ✅ 14/9/3/3/9 项分组展开 |
| **当前项高亮** | ✅ `aria-current="page"` + 主色 + `--kb-primary-soft` 底 |
| **父级高亮**（§19） | ✅ 进入 `/learning/review` 时「学习」保持 active（`isGroupActive`） |
| 所在分组自动展开 | ✅ 路由命中分组内子项时自动展开 |
| 折叠（§17/§54） | ✅ 240px ↔ 64px，折叠后图标居中，**`title` + `aria-label` 双保险** |
| 键盘可达（§53） | ✅ 分组为 `<button>`（Enter/Space 展开），链接为 `<router-link>`（Enter 跳转），`focus-visible` 有 ring |
| 无障碍（§52） | ✅ `aria-label="主导航"`、`aria-expanded`、`aria-controls`、`aria-current` |
| 颜色（§56） | ✅ **0 处硬编码**，全部 `var(--kb-*)` |
| 深色模式 | ✅ 跟随 `--kb-sidebar` / `--kb-primary-soft` 等令牌 |
| 权限（§46） | ✅ `visibleGroups(isLoggedIn, isAdmin)` 过滤，未登录不显示需登录入口，管理员项仅管理员可见 |

### 后台 `Sidebar.vue`
本阶段**未改动**（其已具备分组、折叠、`title` 提示、按角色过滤，质量合格），仅保持原状。

---

## 9. Header 修改（`CTopNav.vue` 重写）

| 项 | 修改前 | 修改后 |
|---|---|---|
| 导航项 | **8 个顶级 + 30 个二级**（全部塞在 Header） | **0 个**（下沉到侧边栏） |
| 内容 | Logo + 8 导航 + 搜索 + 番茄钟 + 通知 + 头像 | **Logo + 搜索 + AI 快捷 + 番茄钟 + 通知 + 头像**（§27） |
| Logo 文案 | 「知识库」 | **「KnowFlow」**（与后台一致，消除与"知识库"菜单重名） |
| 汉堡按钮 | 仅开右侧抽屉 | 向 `CLayout` 派发 `toggle-sidebar`，打开左侧抽屉 |
| AI 入口 | 「AI 助手▾」下拉（3 项） | **1 个直达 `/chat` 的图标按钮**（§29，不再 3 个 AI 按钮） |
| 通知/用户菜单 | 保留 | ✅ 保留，用户菜单新增「我的任务」 |
| 硬编码颜色 | 有（`bg-blue-50` / `text-primary-500` 等） | **0 处**，全部令牌化（§56） |

---

## 10. Mobile Navigation 修改

| 项 | 修改前 | 修改后 |
|---|---|---|
| 底部导航 | **无** | ✅ 新增，`<1024px` 显示，**5 个入口**（首页/学习/知识/AI/我的）（§31-§33） |
| 抽屉 | 右侧抽屉（内嵌在 CTopNav） | 左侧抽屉（复用 CSidebar 组件，数据同源） |
| 二级功能 | — | 通过抽屉进入，移动端不塞完整侧边栏（§33） |
| 安全区 | — | `env(safe-area-inset-bottom)` 适配全面屏 |
| 内容遮挡 | — | 主区自动补 `padding-bottom: 68px` |

---

## 11. Breadcrumb 修改

| 项 | 状态 |
|---|---|
| 自动生成能力 | ✅ `navigation.ts` 提供 `buildCrumbs(path)`，可自动推导「模块 / 页面」两级 |
| 列表页 | ✅ 由路由自动推导（无需页面改动） |
| `DocDetail.vue` | ✅ **本阶段新增**：`知识 / 知识库 / {文档标题}`，含 `aria-current="page"`（§25） |
| 其余详情页 | ⚠️ 已各自具备返回按钮/`PageHeader`（`PathDetail` `ChapterLearn` `PostDetail` `KnowledgeMasteryDetail` 等），本阶段未逐一改造 |

---

## 12. 页面标题统一

| 项 | 修改前 | 修改后 |
|---|---|---|
| 浏览器标题 | 未统一 | 自动：`{页面} · {模块} · KnowFlow`（例：`复习计划 · 学习 · KnowFlow`） |
| 学习中心重名 | 两个页面都叫"学习中心" | 导航层区分：`/learning/center`=**学习中心**，`/learning/pomodoro`=**番茄钟** |
| Logo | 「知识库」 | 「KnowFlow」 |

完整改名清单见 `Phase-UI-2-Navigation-Naming.md`。

---

## 13. 重复入口清理

| 功能 | 修改前 | 修改后 |
|---|---:|---:|
| 学习工作台 | **2 处**（顶级 + 学习下拉） | **1 处**（学习 › 学习工作台） |
| 顶级/二级同名（知识库） | 存在 | 消除（顶级改「知识」） |
| AI 按钮 | 下拉 + 分散 | 主导航「AI」分组 + Header 1 个直达 |
| 隐藏功能无入口 | 6 个（挑战/掌握度/学习报告/专注模式/思维导图/绘图） | 全部纳入导航 |

**未合并（有意保留，功能不丢）**：任务中心 vs 任务清单（不同视图）、`/notes` vs `/workbench/notes`（普通笔记 vs 康奈尔笔记，Phase 0 已确认不合并）、`/tasks` vs `/workbench`（不同领域）。

---

## 14. 旧 URL 兼容

**本阶段未修改任何 URL，因此无需新增重定向。**（§41：不要为了"漂亮"大规模改 URL）

既有的 8 条重定向全部保留：`/portal→/tasks`、`/register→/login?tab=register`、`/learning/my-flashcards` 与 `/learning/flashcards-old` → `/learning/flashcards`、`/docs/new`、`/docs/:id/edit`、`/upload`、`/learning→/learning/center`。

导航配置中 **38 个二级项 + 1 个顶级直达的 path 已逐一核对**，全部存在于 `router/index.ts`，无死链。

---

## 15. Accessibility

| 项 | 状态 |
|---|---|
| `aria-current="page"` | ✅ 侧边栏当前项、底部导航当前项、面包屑末级 |
| `aria-label` / `title` | ✅ 侧边栏折叠态图标双保险；AI/搜索/通知/头像/汉堡均有 `aria-label` |
| `aria-expanded` / `aria-controls` | ✅ 分组展开按钮 |
| `role="menu"` / `menuitem` | ✅ 用户菜单 |
| 键盘可达 | ✅ 分组用 `<button>`（Enter/Space），链接用 `<router-link>`（Enter） |
| focus 可见 | ✅ `focus-visible` ring 使用 `--kb-ring`，未删除浏览器 focus |

---

## 16. Dark Mode

| 项 | 状态 |
|---|---|
| 新增强侧边栏 | ✅ 全部 `var(--kb-*)`：背景 `--kb-sidebar`、边框 `--kb-border`、激活 `--kb-primary-soft`、文字 `--kb-sidebar-foreground` |
| Header | ✅ 沿用令牌 + 背景激活时毛玻璃 |
| 底部导航 | ✅ `--kb-card` + `--kb-border` + `--kb-primary` |
| 硬编码颜色检查 | ✅ 4 个导航文件**均为 0 处** hex（已扫描验证） |

> ⚠️ 未做实机浏览器渲染验证，详见 §21。

---

## 17. Responsive

| 断点 | 行为 |
|---|---|
| `<640px` | 侧边栏=抽屉；底部导航显示；内容 padding 16px |
| `640–1023px` | 同上，内容 padding 24px |
| `≥1024px` | 侧边栏常驻（240px，折叠 64px）；底部导航隐藏；主区 `padding-left` 同步 |
| 取消三层滚动 | Header/Sidebar `fixed`，只有 Main 滚动（§48） |

已验证断点：`640px`、`1024px`（项目中既有的两个断点，未新造 breakpoint，§34）。

---

## 18. Build

| 命令 | 结果 |
|---|---|
| `npx vite build` | ✅ **通过**（3.46s），仅既有 chunk 体积警告 |
| `npx vue-tsc -b` | ⚠️ **75 个错误** —— 与 Phase UI-1 结束时**完全持平** |
| 本阶段新增代码错误 | ✅ **0**（`navigation.ts` / `CSidebar` / `CLayout` / `CTopNav` / `DocDetail` 均无报错） |

**说明**：`vue-tsc` 的 75 个错误是 Phase UI-1 就已存在的历史债务（原始 166 → 已修 91 → 余 75），**非本阶段引入**。修复过程中曾因一处未使用 import 升至 76，已修正回 75。

---

## 19. Lint

项目**未配置** ESLint / Prettier（`package.json` 仅 `dev`/`build`/`preview`），无 lint 可执行。

---

## 20. E2E

项目**未配置** Playwright / Cypress / Vitest，无 E2E 或单元测试可执行。

---

## 21. 遗留问题

| # | 问题 | 严重度 | 说明 |
|---|---|---|---|
| 1 | **未做实机浏览器验证** | 🔴 高 | 本轮仅有构建级验证。侧边栏使 C 端主内容区窄了 240px，**建议用真机/浏览器核对**主要页面（尤其 `fullscreen` 的 Chat / 知识图谱 / 工作台）在 1024/1280/1440 下的排版 |
| 2 | `vue-tsc` 75 个历史错误 | 🟡 中 | 阻断 `npm run build`，需专项清零 |
| 3 | 详情页面包屑未全覆盖 | 🟡 中 | 仅 `DocDetail` 新增；其余详情页虽有返回按钮，但未统一为面包屑组件 |
| 4 | 文件名倒挂未修 | 🟡 中 | `LearningCenter.vue` 实为番茄钟、`LearningReport.vue` 实为学习中心。导航层已统一叫法，**文件重命名**建议专项处理（涉及 import 更新） |
| 5 | 后台 `Sidebar.vue` 未改造 | 🟢 低 | 质量合格，本阶段保持原状 |
| 6 | 页面级组件迁移（Phase UI-1 遗留） | 🟡 中 | `PageHeader` 采用率仍为 7/95 等，需在页面迁移批次继续 |

---

## 22. 下一阶段建议

1. **实机验证本阶段改动**（最优先）：启动 `npm run dev`，核对首页/学习/知识/AI/ Chat 在 Light/Dark 与 5 档宽度下的表现；重点是新增侧边栏后全宽页是否被挤压。
2. **专项清零 75 个 TS 错误**，让 `npm run build` 真正通过。
3. **文件重命名专项**：`LearningCenter.vue → PomodoroPage.vue`、`LearningReport.vue → LearningCenterPage.vue`、`LearningReportView.vue → LearningReportPage.vue`，同步更新 import 与路由。
4. **Phase UI-1b 页面迁移**（承接 UI-1 遗留）：分 5 批把 `PageHeader`/`EmptyState`/`StateView` 接入 95 个页面。
5. 再之后才是 Phase UI-3（Dashboard 内容重构）。

---

## 23. 最终验收表

| 项目 | 修改前 | 修改后 | 状态 |
|---|---:|---:|:---:|
| **顶级导航数量** | 8 | **6** | ✅ |
| **Sidebar** | 无（C 端） | **新建 CSidebar**（折叠/高亮/tooltip/权限） | ✅ |
| **Header** | 8 导航 + 30 二级项 | **Logo+搜索+AI+通知+用户** | ✅ |
| **Mobile Nav** | 仅右侧抽屉 | **抽屉 + 底部 5 入口** | ✅ |
| **Breadcrumb** | 无统一机制 | **自动推导 + DocDetail 已接入** | ⚠️ 部分 |
| **当前页面高亮** | 仅按 path 前缀（易错高亮） | **子项 + 父级双高亮 + aria-current** | ✅ |
| **重复入口** | 学习工作台×2、知识库同名 | **全部收敛为 1 处** | ✅ |
| **隐藏功能无入口** | 6 个 | **0 个** | ✅ |
| **硬编码导航颜色** | 有 | **0 处**（4 文件已扫描） | ✅ |
| **Dark Mode** | 导航无深色保障 | **全令牌化** | ✅ |
| **Responsive** | 无侧边栏布局 | **640/1024 断点 + 移动端底部导航** | ✅ |
| **URL 变更数** | — | **0**（无需重定向） | ✅ |
| **Build（vite）** | — | **✓ 通过** | ✅ |
| **vue-tsc** | 75 | **75（无新增）** | ⚠️ 持平 |
| **Lint** | 无脚本 | 无脚本 | — |
| **E2E** | 无脚本 | 无脚本 | — |
| **实机浏览器验证** | — | **未做** | ❌ |

**达标 11 / 17，部分达标 2，不适用 2，未做 1（实机验证）。**

---

## 24. 本阶段改动文件清单

| 文件 | 类型 | 说明 |
|---|---|---|
| `src/config/navigation.ts` | **新增** | 导航单一数据源（6 顶级 / 38 二级 / 查询工具） |
| `src/components/layout/CSidebar.vue` | **新增** | C 端侧边栏（折叠/高亮/tooltip/键盘/权限） |
| `src/components/layout/CLayout.vue` | 重写 | Header + Sidebar + Main；移动端抽屉 + 底部导航 |
| `src/components/layout/CTopNav.vue` | 重写 | 精简为 Logo+搜索+AI+通知+用户；清除 6 个硬编码菜单数组 |
| `src/router/index.ts` | 修改 | 新增 `afterEach` 统一页面标题；更新布局注释 |
| `src/views/DocDetail.vue` | 修改 | 新增面包屑（知识 / 知识库 / 标题） |
| `docs/二次开发/Phase-UI-2-Route-Map.md` | **新增** | 完整路由地图 |
| `docs/二次开发/Phase-UI-2-Navigation-Naming.md` | **新增** | 命名对照表 |
| `docs/二次开发/Phase-UI-2-执行报告.md` | **新增** | 本报告 |

**未改动**：backend、数据库、AI 核心逻辑、SRS、LearningEvent、KnowledgeMastery、Dashboard 内容、Knowledge/Flashcard/Quiz/Chat 页面内容。

---

**Phase UI-2 已完成。未 commit、未 push。等待你的下一步指令。**
