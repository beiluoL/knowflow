# UI/UX 2.1 · Mini App Inventory（真实清单）

> 数据来源：`src/apps/registry.ts`（13 个 App 定义）+ `src/router/index.ts`（路由→组件映射）+ 逐个组件源码实读。
> 阶段目标：所有真实 Mini App 统一使用 `components/layout/MiniAppShell.vue` 外壳（§94-99 / §17-30）。

## 1. 真实 Mini App 清单（13 个，来自 registry）

| # | App（key） | 分类 | Route | 组件文件 | 迁移前 Shell | 迁移后 Shell | Context（knowledgeName） | LearningResultPanel | LearningEvent | 本次是否改动 | 状态 |
|---|-----------|------|-------|----------|--------------|--------------|--------------------------|----------------------|---------------|--------------|------|
| 1 | 练习 quiz | A 学习型 | `/learning/quiz` | `SmartQuiz.vue` | 自定义 header | **MiniAppShell** | ✅ route query | ✅ | ✅ | 否（UI/UX 2.0 已完成） | ✅ 已完成 |
| 2 | 闪卡 flashcard | A 学习型 | `/learning/flashcards` | `FlashCardsHub.vue` | `hub-hero`（back+title+4 StatCard+context-bar） | **MiniAppShell**（Stats 降为内容，context 由 shell 渲染） | ✅（已有 `knowledgeContext`） | ✅ | ✅ | 是 | ✅ 已完成 |
| 3 | 错题 mistake | A 学习型 | `/mistakes` | `Mistakes.vue` | `PageHeader`（crumbs+title+action） | **MiniAppShell**（crumbs 去除，action→actions slot） | —（route query 未接，保持现状，不伪造） | ✅ | ✅ | 是 | ✅ 已完成 |
| 4 | 费曼 feynman | A 学习型 | `/workbench/story` | `WorkbenchStory.vue` | `wb-hero`（eyebrow+title+subtitle+CTA+loop-nav） | **MiniAppShell**（h1 去重，eyebrow/subtitle/loop-nav 留内容，CTA→actions） | — | ✅ | ✅ | 是 | ✅ 已完成 |
| 5 | 康奈尔 cornell | A 学习型 | `/workbench/notes` | `WorkbenchNotes.vue` | 自定义 header（title+subtitle+新建） | **MiniAppShell**（新建→actions） | — | ✅ | ✅ | 是 | ✅ 已完成 |
| 6 | 记忆宫殿 memory-palace | A 学习型 | `/workbench/palace` | `WorkbenchPalace.vue` | `wb-hero`（eyebrow+title+subtitle+2 CTA+loop-nav） | **MiniAppShell**（2 CTA→actions） | — | ✅ | ✅ | 是 | ✅ 已完成 |
| 7 | 代码练习 coding | A 学习型 | `/learning/code-practice` | `CodePractice.vue` | 自定义 header（h1+p） | **MiniAppShell** | — | ✅ | ✅ | 是 | ✅ 已完成 |
| 8 | 专注 focus | F 专注 | `/learning/pomodoro` | `FocusMode.vue` | 沉浸式全屏 `immersive-topbar`（退出+模式+排行榜/分享/图谱/白噪音） | **保持原样（§53 沉浸式，Timer 不被外壳限制空间）** | — | — | ✅ | 否（有意延迟） | ⏸️ 有意保留 |
| 9 | 知识图谱 knowledge-graph | C 知识型 | `/learning/knowledge-graph` | `KnowledgeGraph.vue` | 自定义 header（h1+p）+ 工具栏 | **MiniAppShell**（canvas/工具栏留内容） | — | — | — | 是 | ✅ 已完成 |
| 10 | 思维导图 mindmap | C 知识型 | `/mindmap` | `MindMap.vue` | 左栏列表 + 右画布（无顶栏返回） | **MiniAppShell**（左栏标题保留） | — | — | — | 是 | ✅ 已完成 |
| 11 | 绘图 drawing | D 工具型 | `/drawing` | `DrawingEditor.vue` | 左栏列表 + 右画布（无顶栏返回） | **MiniAppShell**（画布全屏保留） | — | — | — | 是 | ✅ 已完成 |
| 12 | 笔记 notes | D 工具型 | `/notes` | `NotesManage.vue` | 左栏（标题+新建）+ 右编辑区 | **MiniAppShell**（编辑区保留） | — | — | — | 是 | ✅ 已完成 |
| 13 | 智能写作 ai-writing | E AI 工具型 | `/learning/writing` | `SmartWriting.vue` | `page-header`（h1+预览/导出MD/导出PDF/新建） | **MiniAppShell**（h1 去重；编辑器工具栏留内容，不强制进 actions slot） | — | — | —（AI 生成，非学习事件） | 是 | ✅ 已完成 |

## 2. 分类汇总（§11）

- **A 学习型（7）**：quiz ✅ / flashcard ✅ / mistake ✅ / feynman ✅ / cornell ✅ / coding ✅ / memory-palace ✅
- **B 复习型**：由 SRS 复用，未单列独立 App
- **C 知识型（2）**：knowledge-graph ✅ / mindmap ✅
- **D 工具型（2）**：notes ✅ / drawing ✅
- **E AI 工具型（1）**：ai-writing ✅
- **F 专注（1）**：focus ⏸️ 有意保留（沉浸式）

## 3. 迁移统计

- **已统一（12/13）**：除 `focus` 外全部接入 `MiniAppShell`。
- **有意保留（1/13）**：`focus` —— 沉浸式全屏专注应用，自有 `immersive-topbar`（退出/模式/排行榜/分享/图谱/白噪音），按 §53「Focus Timer 不能被 Shell 限制空间」保持原样，不强行套壳。它也不在 §248 必须「正常」清单内。
- **未触碰业务**：LearningEvent / Reward / Mastery / SRS / NextAction / Learning Intelligence / Purchase / Entitlement / UserApp 零改动（§5/§6）。

## 4. 迁移规则执行（§36-42）

- 仅含「返回/标题/上下文」的自定义 header → 直接替换为 `MiniAppShell`（CodePractice / KnowledgeGraph / NotesManage / MindMap / DrawingEditor）。
- 含真实业务操作的 header → 操作迁移进 `#actions` slot（Mistakes 的「开始错题练习」、WorkbenchNotes 的「新建笔记」、WorkbenchStory 的「写个故事」、WorkbenchPalace 的「间隔重复+新建宫殿」）。
- 富品牌 hero（wb-hero）→ 去掉重复 `<h1>`，eyebrow/subtitle/loop-nav 作为内容保留，CTA 进 actions（Feynman / Memory Palace）。
- 编辑器工具栏不强制进 actions（SmartWriting 预览/导出/新建保留为内容编辑器工具条，§194）。
- 不制造默认 Context（§68-69）：仅 `flashcard`/`quiz` 已有真实 `knowledgeContext`，其余一律不显示空条。
