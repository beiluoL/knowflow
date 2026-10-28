# Architecture-3 · App Inventory（应用清单）

> 全部基于真实源码：`frontend/src/apps/registry.ts`（13 App）+ `product` 表种子（5 商品）+ `router/index.ts`。
> Product 列：FREE = 无收费商品（默认 4 App）；其余 = 对应 product_key + 价格。

| App Key | Name | Route | 当前代码 | Category | Product | Entitlement | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| flashcard | 闪卡 | /learning/flashcards | ✅ FlashCardsHub.vue | MEMORY | FREE | 不需要（免费） | ACTIVE |
| quiz | 练习 | /learning/quiz | ✅ SmartQuiz.vue | PRACTICE | FREE | 不需要 | ACTIVE |
| mistake | 错题 | /mistakes | ✅ Mistakes.vue | PRACTICE | FREE | 不需要 | ACTIVE |
| focus | 专注 | /learning/pomodoro | ✅ PomodoroPage.vue | FOCUS | FREE | 不需要 | ACTIVE |
| feynman | 费曼 | /workbench/story | ✅ WorkbenchStory.vue | UNDERSTANDING | feynman_pro（200 Coin） | ✅ 购买后授予 | ACTIVE |
| cornell | 康奈尔 | /workbench/notes | ✅ WorkbenchNotes.vue | ORGANIZATION | cornell_pro（150 Coin） | ✅ | ACTIVE |
| memory-palace | 记忆宫殿 | /workbench/palace | ✅ WorkbenchPalace.vue | MEMORY | memory_palace_pro（150 Coin） | ✅ | ACTIVE |
| knowledge-graph | 知识图谱 | /learning/knowledge-graph | ✅ KnowledgeGraph.vue | UNDERSTANDING | graph_pro（120 Coin） | ✅ | ACTIVE |
| coding | 代码练习 | /learning/code-practice | ✅ CodePractice.vue | CODING | coding_pro（180 Coin） | ✅ | ACTIVE |
| mindmap | 思维导图 | /mindmap | ✅ MindMap.vue | ORGANIZATION | 无 | 不需要 | ACTIVE |
| drawing | 绘图 | /drawing | ✅ DrawingEditor.vue | ORGANIZATION | 无 | 不需要 | ACTIVE |
| ai-writing | 智能写作 | /learning/writing | ✅ SmartWriting.vue | AI | 无 | 不需要 | ACTIVE |
| notes | 笔记 | /notes | ✅ NotesManage.vue | ORGANIZATION | 无 | 不需要 | ACTIVE |

## 说明

- **默认安装 4 个**：flashcard / quiz / mistake / focus（免费，不建收费商品，§130）。
- **付费 5 个**：feynman / cornell / memory-palace / knowledge-graph / coding 已有真实商品（Architecture-2 种子），Entitlement 由购买授予。
- **无商品 4 个**：mindmap / drawing / ai-writing / notes 免费可用（Architecture-1 注册，App Center 可见，无 Product 关联）。
- 全部 publisherType 概念 = OFFICIAL（§74）。
- 无重复 App（已核验 route 唯一，§138）。

## 分类聚合（§106/§66）

| 商店分类 | App |
| --- | --- |
| 学习 | flashcard / quiz / mistake / focus / feynman / cornell / memory-palace / knowledge-graph / coding / mindmap / drawing / notes |
| AI | ai-writing |
| 工具 | mindmap / drawing / notes（整理工具） |
| 游戏 | （无，预留 GAME 类型，§67-69） |
