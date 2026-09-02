# Phase UI-2 · 路由地图（Route Map）

> 数据源：`frontend/src/router/index.ts`（真实路由表）
> 生成日期：2026-08-30
> 说明：**本阶段未修改任何 URL**，仅重新组织导航归属。所有导航项指向的 path 均已逐一核对存在于路由表。

---

## 一、图例

| 字段 | 含义 |
|---|---|
| 顶级入口 | 是否作为一级导航直接展示（本阶段收敛为 6 个） |
| 导航名称 | 侧边栏/移动端展示的中文名 |
| 布局 | `c`=AppShell(顶栏+侧栏+主区) / `b`=后台(侧栏+顶栏) / `none`=无壳 |
| 面包屑 | 由 `@/config/navigation.ts` 自动推导，或页面自定义 |

---

## 二、顶级入口（6 个）

| # | 顶级入口 | 图标 | 直达 path | 是否含二级 |
|---|---|---|---|---|
| 1 | 首页 | `home` | `/` | 否 |
| 2 | 学习 | `graduation-cap` | — | 是（14 项） |
| 3 | 知识 | `library` | — | 是（9 项） |
| 4 | AI | `bot` | — | 是（3 项） |
| 5 | 社区 | `users` | — | 是（3 项） |
| 6 | 我的 | `user` | — | 是（9 项） |

> 修改前为 **8 个顶级入口**（首页 / 学习工作台 / 知识库▾ / 学习▾ / 社区▾ / AI助手▾ / 成长▾ / 我的▾），共 30 个二级项。

---

## 三、完整路由 → 导航映射表

### 3.1 首页

| Route | 页面文件 | 顶级 | 导航名称 | 布局 | 面包屑 |
|---|---|---|---|---|---|
| `/` | `Home.vue` | 首页 | 首页 | c | — |

### 3.2 学习

| Route | 页面文件 | 顶级 | 导航名称 | 布局 | 面包屑 |
|---|---|---|---|---|---|
| `/learning` | redirect → `/learning/center` | 学习 | — | c | — |
| `/learning/center` | `LearningReport.vue` | 学习 | 学习中心 | c(fullscreen) | 学习 / 学习中心 |
| `/learning/today-plan` | `TodayPlan.vue` | 学习 | 今日计划 | c | 学习 / 今日计划 |
| `/learning/paths` | `LearningPaths.vue` | 学习 | 学习路径 | c | 学习 / 学习路径 |
| `/learning/path/:id` | `PathDetail.vue` | 学习 | （详情页） | c | 学习 / 学习路径 / 详情 |
| `/learning/chapter/:id` | `ChapterLearn.vue` | 学习 | （详情页） | c(fullscreen) | 学习 / 章节 |
| `/learning/review` | `ReviewPlan.vue` | 学习 | 复习计划 | c(fullscreen) | 学习 / 复习计划 |
| `/learning/flashcards` | `FlashCardsHub.vue` | 学习 | 闪卡 | c(fullscreen) | 学习 / 闪卡 |
| `/mistakes` | `Mistakes.vue` | 学习 | 错题本 | c | 学习 / 错题本 |
| `/learning/quiz` | `SmartQuiz.vue` | 学习 | 智能测验 | c(fullscreen) | 学习 / 智能测验 |
| `/learning/code-practice` | `CodePractice.vue` | 学习 | 代码练习 | c(fullscreen) | 学习 / 代码练习 |
| `/learning/code-practice/:id` | `CodePlayground.vue` | 学习 | （编辑器） | **none** | — |
| `/challenge` | `Challenge.vue` | 学习 | 挑战 | c(fullscreen) | 学习 / 挑战 |
| `/challenge/:id` | `ChallengePlay.vue` | 学习 | （详情页） | c(fullscreen) | 学习 / 挑战 / 详情 |
| `/learning/mastery` | `MasteryDashboard.vue` | 学习 | 掌握度 | c(fullscreen) | 学习 / 掌握度 |
| `/learning/knowledge-mastery/:id` | `KnowledgeMasteryDetail.vue` | 学习 | （详情页） | c(fullscreen) | 学习 / 掌握度 / 详情 |
| `/learning/report` | `LearningReportView.vue` | 学习 | 学习报告 | c | 学习 / 学习报告 |
| `/workbench` | `Workbench.vue` | 学习 | 学习工作台 | c(fullscreen) | 学习 / 学习工作台 |
| `/workbench/capture` | `WorkbenchCapture.vue` | 学习 | （子页） | c(fullscreen) | — |
| `/workbench/notes` `/:id` | `WorkbenchNotes`/`NoteEdit` | 学习 | （子页） | c(fullscreen) | — |
| `/workbench/review` | `WorkbenchReview.vue` | 学习 | （子页） | c(fullscreen) | — |
| `/workbench/palace` `/:id` | `WorkbenchPalace`/`Edit` | 学习 | （子页） | c(fullscreen) | — |
| `/workbench/recall` | `WorkbenchRecall.vue` | 学习 | （子页） | c(fullscreen) | — |
| `/workbench/story` `/:id` | `WorkbenchStory`/`Edit` | 学习 | （子页） | c(fullscreen) | — |
| `/learning/pomodoro` | `LearningCenter.vue` | 学习 | 番茄钟 | c(fullscreen) | 学习 / 番茄钟 |
| `/learning/focus` | `FocusMode.vue` | 学习 | 专注模式 | **none** | — |
| `/learning/mode` | `LearningMode.vue` | 学习 | （沉浸） | **none** | — |

> ⚠️ 命名倒挂（Phase UI-1 审计已发现，本阶段**未改动文件名**，仅修正展示名）：
> `/learning/center` 渲染 `LearningReport.vue`；`/learning/pomodoro` 渲染 `LearningCenter.vue`（其页内 `<h1>` 原为「学习中心」）。
> 导航层已统一称呼：`/learning/center`=**学习中心**，`/learning/pomodoro`=**番茄钟**。文件重命名建议在后续专项处理。

### 3.3 知识

| Route | 页面文件 | 顶级 | 导航名称 | 布局 | 面包屑 |
|---|---|---|---|---|---|
| `/knowledge` | `KnowledgeHome.vue` | 知识 | 知识库 | c(fullscreen) | 知识 / 知识库 |
| `/docs` | `Docs.vue` | 知识 | 文档库 | c | 知识 / 文档库 |
| `/categories` | `Categories.vue` | 知识 | 分类浏览 | c(fullscreen) | 知识 / 分类浏览 |
| `/search` | `SearchResult.vue` | 知识 | 搜索 | c | 知识 / 搜索 |
| `/doc/:id` | `DocDetail.vue` | 知识 | （详情页） | c | 知识 / 知识库 / {标题} ✅本阶段新增 |
| `/doc/new`…（旧） | redirect → `/admin/docs/new` | — | — | — | — |
| `/learning/knowledge-graph` | `KnowledgeGraph.vue` | 知识 | 知识图谱 | c(fullscreen) | 知识 / 知识图谱 |
| `/favorites` | `Favorites.vue` | 知识 | 收藏夹 | c | 知识 / 收藏夹 |
| `/notes` `/notes/new` `/notes/:id/edit` | `NotesManage`/`NoteEdit` | 知识 | 笔记 | c(fullscreen) | 知识 / 笔记 |
| `/mindmap` | `MindMap.vue` | 知识 | 思维导图 | c(fullscreen) | 知识 / 思维导图 |
| `/drawing` | `DrawingEditor.vue` | 知识 | 绘图 | c(fullscreen) | 知识 / 绘图 |
| `/knowledge/upload` | `KnowledgeUpload.vue` | 知识 | （子流程） | c | — |
| `/knowledge/import` | `KnowledgeImport.vue` | 知识 | （子流程） | c | — |
| `/obsidian/import` | `ObsidianImport.vue` | 知识 | （子流程） | c | — |
| `/import-templates` | `ImportTemplates.vue` | 知识 | （子流程） | c | — |
| `/knowledge/new` | `KnowledgeCreate.vue` | 知识 | （子流程） | c | — |
| `/knowledge/reader` | `LocalReader.vue` | 知识 | （阅读器） | **none** | — |

### 3.4 AI

| Route | 页面文件 | 顶级 | 导航名称 | 布局 | 面包屑 |
|---|---|---|---|---|---|
| `/chat` | `Chat.vue` | AI | 智能问答 | c(fullscreen) | AI / 智能问答 |
| `/coding/agent` | `CodeAgent.vue` | AI | 编程 Agent | c(fullscreen) | AI / 编程 Agent |
| `/learning/writing` | `SmartWriting.vue` | AI | 智能写作 | c(fullscreen) | AI / 智能写作 |

> Header 另设 1 个 AI 直达按钮（→ `/chat`），与主导航「AI」分组构成"主入口 + 快捷方式"，不再是 3 个重复 AI 按钮。

### 3.5 社区

| Route | 页面文件 | 顶级 | 导航名称 | 布局 | 面包屑 |
|---|---|---|---|---|---|
| `/community` | `Community.vue` | 社区 | 社区讨论 | c | 社区 / 社区讨论 |
| `/study-group` | `StudyGroup.vue` | 社区 | 学习小组 | c(fullscreen) | 社区 / 学习小组 |
| `/messages` | `Messages.vue` | 社区 | 私信 | c(fullscreen) | 社区 / 私信 |
| `/community/post/:id` | `PostDetail.vue` | 社区 | （详情页） | c | 社区 / 社区讨论 / 详情 |
| `/community/post/new` | `PostEdit.vue` | 社区 | （子页） | c | — |

### 3.6 我的

| Route | 页面文件 | 顶级 | 导航名称 | 布局 | 面包屑 |
|---|---|---|---|---|---|
| `/tasks` | `TaskCenter.vue` | 我的 | 任务中心 | c | 我的 / 任务中心 |
| `/task-list` | `TaskManager.vue` | 我的 | 任务清单 | c(fullscreen) | 我的 / 任务清单 |
| `/calendar` | `CalendarView.vue` | 我的 | 日历 | c | 我的 / 日历 |
| `/check-in` | `CheckIn.vue` | 我的 | 每日打卡 | c | 我的 / 每日打卡 |
| `/habits` | `Habits.vue` | 我的 | 习惯打卡 | c | 我的 / 习惯打卡 |
| `/achievements` | `Achievement.vue` | 我的 | 成就 | c | 我的 / 成就 |
| `/kb-titles` | `KBTitle.vue` | 我的 | 称号 | c | 我的 / 称号 |
| `/profile` | `Profile.vue` | 我的 | 个人中心 | c | 我的 / 个人中心 |
| `/settings/background` | `BackgroundSettings.vue` | 我的 | 背景设置 | c | 我的 / 背景设置 |
| `/notifications` | `Notifications.vue` | 我的 | （通知，用户菜单可达） | c | — |
| `/certificate/:id` | `Certificate.vue` | 我的 | （详情页） | c | — |

### 3.7 无布局壳（`layout:'none'`）

| Route | 页面文件 | 说明 |
|---|---|---|
| `/login` | `Login.vue` | 登录（不套 AppShell，§39） |
| `/oauth/callback` | `OAuthCallback.vue` | OAuth 回调 |
| `/redirect` | `Redirect.vue` | 中转 |
| `/:pathMatch(.*)*` | `NotFound.vue` | 404 |

### 3.8 重定向（旧 URL 兼容）

| 旧 URL | 新 URL | 状态 |
|---|---|---|
| `/portal` | `/tasks` | 既有 |
| `/register` | `/login?tab=register` | 既有 |
| `/learning/my-flashcards` | `/learning/flashcards` | 既有 |
| `/learning/flashcards-old` | `/learning/flashcards` | 既有 |
| `/docs/new` | `/admin/docs/new` | 既有 |
| `/docs/:id/edit` | `/admin/docs/:id/edit` | 既有 |
| `/upload` | `/admin/upload` | 既有 |
| `/learning` | `/learning/center` | 既有 |

> **本阶段未新增重定向，因为未修改任何 URL。** 详见执行报告 §14。

### 3.9 B 端（管理后台，`layout:'b'`）

保持原结构，共 20 条路由，顶级不参与 C 端 6 入口统计：
`/admin/overview`、`knowledge`、`categories`、`docs`、`docs/new`、`docs/:id/edit`、`upload`、`tags`、`flashcards`、`learning-paths`、`learning/chapters/new`、`learning/chapters/:id/edit`、`writing`、`chat-config`、`icons`、`files`、`code-questions`、`quiz`、`users`、`database`、`community`。

---

## 四、未纳入主导航的路由（有意为之）

| 类型 | 示例 | 原因 |
|---|---|---|
| 详情页 | `/doc/:id`、`/learning/path/:id` | 由列表进入，靠面包屑返回（§25） |
| 编辑器/沉浸 | `CodePlayground`、`FocusMode`、`LearningMode`、`LocalReader` | 全屏专用布局（§40） |
| 子流程 | `/knowledge/upload`、`/obsidian/import` | 由知识库页内按钮进入 |
| 系统页 | `/login`、`/oauth/callback`、404 | 不套 AppShell（§39） |
| 内部工具 | `/learning/events` | 偏运维/只读，不面向普通用户 |

---

## 五、统计

| 项 | 数量 |
|---|---:|
| C 端总路由 | 约 78 |
| B 端路由 | 20 |
| 顶级入口（修改前 → 后） | **8 → 6** |
| 二级导航项（修改后） | 38 |
| 详情页（面包屑） | 8 |
| 本阶段新增/修改的 URL | **0** |
