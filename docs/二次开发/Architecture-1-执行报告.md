# Architecture-1 · 执行报告（Core + Mini Apps）

## 1. 本阶段目标

将 KnowFlow 从「功能集合」收敛为「学习操作系统 + 学习应用」：Core 承载用户/知识/学习数据/AI，13 个真实 Mini App 通过 **App Registry** 统一管理安装/启用/停用/入口，**不重写业务、不删数据、不加数据库**。

## 2. 方案决策（为什么这么做）

| 决策点 | 结论 | 依据 |
| --- | --- | --- |
| 动态代码下载 | ❌ 不做，Built-in Mini Apps | §4 |
| Registry 载体 | 静态 `src/apps/registry.ts`（TS 元数据） | §22-23 |
| 用户状态 | `localStorage` 按 userId 隔离（`knowflow:apps:<uid>`） | §23/§183 |
| 数据库 | **不新增表**；未来跨设备再按 §108 加 `user_app` | §66-69 |
| 后端 | **零改动**、新增 API 0 | §153-154 |
| App 路由 | 沿用既有业务路由，不建 `/apps/xxx` | §84/§148 |
| 停用 | 仅 `enabled=false` 隐藏，数据保留 | §29/§125 |

## 3. 新增文件（5 个）

| 文件 | 职责 |
| --- | --- |
| `src/apps/types.ts` | AppDefinition / AppCategory / AppStatus / UserAppState / MiniAppContext（§61 类型边界） |
| `src/apps/registry.ts` | 13 个 App 定义（真实 route 逐一核对）+ 纯函数查询（getApps/getAppByKey/getDefaultApps/getRecommendedApps/getAppsByCategory，§105 可单测） |
| `src/stores/apps.ts` | Pinia store：installed/enabled/sortOrder + 幂等 install/enable/disable + userId 隔离 + 登出重置（§194） |
| `src/components/apps/AppCard.vue` | 纯展示卡片（§169：只 emit，不调业务 API；状态文字+颜色双表达 §86） |
| `src/views/AppCenterPage.vue` | `/apps` 应用中心：我的应用 / 全部 + 分类筛选（§31/§100） |

## 4. 修改文件（3 个）

| 文件 | 改动 |
| --- | --- |
| `src/router/index.ts` | 新增 `/apps` 路由（layout 'c'、requiresAuth，§147） |
| `src/config/navigation.ts` | 新增顶级「应用」入口（icon grid、path /apps，§146；不展开二级 §48-50） |
| `src/views/Home.vue` | 新增「我的应用」区（Registry 驱动，已安装+启用的前 6 个 + 应用中心链接，§139/§162） |

## 5. App Inventory（真实核对结果）

| App Key | 名称 | 分类 | Route | 默认安装 | 状态 | 真实存在 |
| --- | --- | --- | --- | --- | --- | --- |
| flashcard | 闪卡 | MEMORY | /learning/flashcards | ✅ | ACTIVE | ✅ |
| quiz | 练习 | PRACTICE | /learning/quiz | ✅ | ACTIVE | ✅ |
| mistake | 错题 | PRACTICE | /mistakes | ✅ | ACTIVE | ✅ |
| focus | 专注 | FOCUS | /learning/pomodoro | ✅ | ACTIVE | ✅ |
| feynman | 费曼 | UNDERSTANDING | /workbench/story | ❌ | ACTIVE | ✅ |
| cornell | 康奈尔 | ORGANIZATION | /workbench/notes | ❌ | ACTIVE | ✅ |
| memory-palace | 记忆宫殿 | MEMORY | /workbench/palace | ❌ | ACTIVE | ✅ |
| knowledge-graph | 知识图谱 | UNDERSTANDING | /learning/knowledge-graph | ❌ | ACTIVE | ✅ |
| coding | 代码练习 | CODING | /learning/code-practice | ❌ | ACTIVE | ✅ |
| mindmap | 思维导图 | ORGANIZATION | /mindmap | ❌ | ACTIVE | ✅ |
| drawing | 绘图 | ORGANIZATION | /drawing | ❌ | ACTIVE | ✅ |
| ai-writing | 智能写作 | AI | /learning/writing | ❌ | ACTIVE | ✅ |
| notes | 笔记 | ORGANIZATION | /notes | ❌ | ACTIVE | ✅ |

**总数 13 / Active 13 / Beta 0 / Legacy 0 / 默认安装 4。**

## 6. Core Map

```
KnowFlow Core
├── User / Auth
├── Knowledge（知识库/文档/分类/搜索/收藏/笔记）
├── LearningEvent
├── KnowledgeMastery
├── Learning Path
├── SRS / Review（唯一 SrsService）
├── AI（智能问答 / 编程 Agent）
├── App Registry（本阶段新增）
└── Settings
```

## 7. 质量验证

| 项 | 结果 |
| --- | --- |
| `vite build` | ✅ 通过（3.86s） |
| `vue-tsc -b` | 74（与 UI-9 结束时**持平，零新增**；改动文件全零错误） |
| 新增文件 rgba()/hex | **0 处**（全 --kb-* 令牌） |
| 后端 | 零改动；新增 API 0；数据库 0 |
| Mock / 假 App | 0（13 个 App 的 route 全部与 router 核对） |
| commit / push | 未执行 |

## 8. 遗留问题与建议

1. **未实机浏览器验证**：建议 `npm run dev` 后看 `/apps`、首页「我的应用」、侧栏「应用」高亮、Dark、390px。
2. **跨设备同步**：目前状态在 localStorage；如需多设备一致，后续按 §108 加 `user_app` 表（H2+MySQL 同步）与 4 个 API（GET/PUT 安装启用停用）。
3. **MiniAppContext 仅类型声明**：后续做「AI 生成闪卡」等跨 App 动作时再实例化。
4. **清 74 个历史 TS 错误**：与全站 build 通过强相关，仍是最大欠账。
5. **App 图标**：quiz / knowledge-graph 使用 iconfont 名（icon-kaoshi / icon-zhishitupubaocun），与既有导航一致，需实机确认渲染。

## 9. 下一阶段建议

- 实机验收 App Center 全流程（安装→打开→停用→重新启用→Home 同步）
- 后端 user_app 持久化（跨设备）——小改动，收益明确
- 之后可进入 Phase UI-10（全站响应式）或专项「74 个 TS 错误清零」
