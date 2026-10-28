# Phase UI-5 · 知识库路由地图（Knowledge Route Map）

> 数据源：`frontend/src/router/index.ts`（真实路由）
> 日期：2026-08-30

## 知识模块路由 → 职责映射

| Route | Component | 职责 | 导航归属（UI-2） | 是否重复 |
|---|---|---|---|---|
| `/knowledge` | `KnowledgeHome.vue` | **知识总入口**：分类侧栏 + 搜索 + 最近文档 + 文档列表 | 知识 › 知识库 | 唯一 |
| `/docs` | `Docs.vue` | 文档浏览管理（分类 Tab 筛选） | 知识 › 文档库 | 与 KnowledgeHome 有重叠（均为文档浏览），但视角不同：KnowledgeHome=总览+侧栏分类，Docs=纯列表 |
| `/doc/:id` | `DocDetail.vue` | 文档阅读 + 学习动作（章节学习/AI闪卡/AI摘要/收藏/笔记） | 详情页（面包屑进入） | 唯一 |
| `/categories` | `Categories.vue` | 按分类发现知识（分类 → 文档） | 知识 › 分类浏览 | 唯一 |
| `/search` | `SearchResult.vue` | 全局知识搜索（关键词 → 文档结果） | 知识 › 搜索 | 唯一（全站唯一搜索主入口） |
| `/favorites` | `Favorites.vue` | 我收藏的文档 | 知识 › 收藏夹 | 唯一 |
| `/notes` `/notes/new` `/notes/:id/edit` | `NotesManage` / `NoteEdit` | 个人笔记管理 | 知识 › 笔记 | 唯一（与 /workbench/notes 为不同概念，UI-2 已定不合并） |
| `/learning/knowledge-graph` | `KnowledgeGraph.vue` | 知识图谱（掌握度/知识点可视化） | 知识 › 知识图谱 | 唯一 |
| `/mindmap` | `MindMap.vue` | 思维导图（知识整理工具） | 知识 › 思维导图 | 唯一 |
| `/drawing` | `DrawingEditor.vue` | 绘图（知识整理工具） | 知识 › 绘图 | 唯一 |
| `/knowledge/upload` | `KnowledgeUpload.vue` | 上传文档（子流程） | 页内按钮进入 | — |
| `/knowledge/import` `/obsidian/import` `/import-templates` | 各导入页 | 批量/模板导入（子流程） | 页内进入 | — |
| `/knowledge/new` | `KnowledgeCreate.vue` | 新建知识（子流程） | 页内进入 | — |
| `/knowledge/reader` | `LocalReader.vue` | 本地阅读器（无布局壳） | 特殊布局 | 与 DocDetail 定位相近，为本地文件阅读场景 |

## 信息架构结论（§四十二）

```
知识
├── 找知识：知识库(/knowledge) · 文档(/docs) · 搜索(/search) · 分类(/categories)
├── 管理知识：收藏(/favorites) · 笔记(/notes)
└── 整理知识：知识图谱 · 思维导图(/mindmap) · 绘图(/drawing)
```

**顶层不新增入口**（UI-2 已收敛为 9 项）；工具类（图谱/导图/绘图）保持二级，不抢占"找知识"主线（§四十三）。

## Document → Learning 闭环（已存在，真实）

```
DocDetail (/doc/:id)
  ├── [进入章节学习] → 反查关联章节 learningApi → 章节学习（真实）
  ├── [AI 闪卡]      → docsApi.generateFlashcards(id) → 闪卡（真实）
  ├── [AI 摘要]      → docsApi.generateSummary(id)（真实）
  ├── [记笔记]       → /notes（真实）
  └── [收藏]         → docsApi.toggleFavorite(id)（真实）
```

无需新增任何接口，闭环已由真实 API 支撑（§十九~§二十二）。
