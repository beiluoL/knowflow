# Architecture-1 · Feature Inventory（功能清单）

> 依据 `frontend/src/router/index.ts`（2026-08-30 全量核对）与既有页面/API 扫描整理。
> 判定：CORE / MINI_APP / SUB_FEATURE / TOOL / LEGACY（§17）。

## 一、Core（KnowFlow Core，不进入 Mini App 体系）

| 功能 | Route/载体 | 说明 |
| --- | --- | --- |
| User / Auth | `/login` `/register` `/profile` | 统一用户体系，Mini App 不拥有独立 User（§10） |
| Knowledge | `/knowledge` `/docs` `/doc/:id` `/categories` `/search` `/favorites` `/notes` | 统一知识体系，Mini App 不复制 Knowledge（§11） |
| LearningEvent | 后端 | 统一学习事件，全 App 共享（§13） |
| KnowledgeMastery | `/learning/mastery` | 统一掌握度，Mini App 禁止自算（§12/§60） |
| SRS / Review | `/learning/review` | 唯一 SrsService，Mini App 复习一律走 Review API（§14） |
| Learning Path | `/learning/paths` `/learning/path/:id` `/learning/chapter/:id` | 核心学习组织 |
| AI 基础 | `/chat` `/coding/agent` | Core AI 能力 |
| App Registry | `src/apps/`（本阶段） | 应用元数据与用户状态 |

## 二、Mini Apps（已注册，13 个，全部真实存在）

| App Key | 名称 | Route | 页面 | 默认安装 |
| --- | --- | --- | --- | --- |
| flashcard | 闪卡 | `/learning/flashcards` | FlashCardsHub.vue | ✅ |
| quiz | 练习 | `/learning/quiz` | SmartQuiz.vue | ✅ |
| mistake | 错题 | `/mistakes` | Mistakes.vue | ✅ |
| focus | 专注 | `/learning/pomodoro` | PomodoroPage.vue | ✅ |
| feynman | 费曼 | `/workbench/story` | WorkbenchStory.vue | ❌ |
| cornell | 康奈尔 | `/workbench/notes` | WorkbenchNotes.vue | ❌ |
| memory-palace | 记忆宫殿 | `/workbench/palace` | WorkbenchPalace.vue | ❌ |
| knowledge-graph | 知识图谱 | `/learning/knowledge-graph` | KnowledgeGraph.vue | ❌ |
| coding | 代码练习 | `/learning/code-practice` | CodePractice.vue | ❌ |
| mindmap | 思维导图 | `/mindmap` | MindMap.vue | ❌ |
| drawing | 绘图 | `/drawing` | DrawingEditor.vue | ❌ |
| ai-writing | 智能写作 | `/learning/writing` | SmartWriting.vue | ❌ |
| notes | 笔记 | `/notes` | NotesManage.vue | ❌ |

## 三、未注册为 Mini App 的既有功能（SUB_FEATURE / TOOL / CORE 子项）

| 功能 | Route | 归类 | 原因 |
| --- | --- | --- | --- |
| 学习中心 | `/learning/center` | Core 子项 | 今日行动总入口，非独立学习任务 |
| 今日计划 | `/learning/today-plan` | Core 子项 | 依附学习中心/任务 |
| 学习报告 | `/learning/report` | Core 子项 | 统计复盘 |
| 掌握度 | `/learning/mastery` | Core 子项 | Core 数据展示 |
| 专注模式 | `/learning/focus` | SUB_FEATURE | 依附 focus App（入口级联，未单独注册） |
| 挑战 | `/challenge` | TOOL | 游戏化轻功能 |
| 任务中心/任务清单 | `/tasks` `/task-list` | Core 子项 | 个人任务体系 |
| 日历 | `/calendar` | TOOL | 日程工具 |
| 打卡/习惯/成就/称号 | `/check-in` `/habits` `/achievements` `/kb-titles` | TOOL | 成长激励 |
| 社区/小组/私信 | `/community` `/study-group` `/messages` | Core 子项 | 社交非学习工具 |
| 工作台（capture/review/recall） | `/workbench/*` 其余 | SUB_FEATURE | 依附费曼/康奈尔/记忆宫殿 |
| 代码 Agent | `/coding/agent` | Core 子项 | Core AI 能力 |
| 思维导图/绘图 | `/mindmap` `/drawing` | MINI_APP（已注册） | 知识整理工具 |

## 四、已知重复（本阶段不删除，仅统一入口）

| 重复项 | 处理 | 依据 |
| --- | --- | --- |
| 笔记 `/notes` vs 工作台笔记 `/workbench/notes`（康奈尔） | 各自注册；notes=普通笔记(PRIMARY)，cornell=康奈尔(独立 App) | §175-176 |
| 番茄钟 `/learning/pomodoro` vs 专注模式 `/learning/focus` | focus App 主入口指向 pomodoro，focus 作为级联入口 | §37-38 |
| 闪卡列表 `/learning/flashcards` vs `/learning/my-flashcards` vs `/learning/flashcards-old` | 只注册主入口 flashcards；另两个为历史/旧版，未注册 | §19 |

## 五、数据库现状（本阶段不新增表，§66-69）

- 未新增任何数据库表；user_app 用户状态由前端 `localStorage`（按 userId 隔离）承载（§23/§183）。
- 未来跨设备同步时，再按 §108 增加 `user_app`（user_id + app_key 唯一），本阶段接口不变。
