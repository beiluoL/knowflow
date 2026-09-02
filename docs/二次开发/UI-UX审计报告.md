# KnowFlow UI/UX 全面审计报告

> **审计日期**：2026-08-30
> **审计范围**：`frontend/`（Vue 3 + TS + Vite + Tailwind + Pinia），只读源码审计
> **审计阶段**：**第一阶段 —— 只审计，未修改任何代码**
> **样本规模**：95 个页面（views）/ 69 个组件 / 7 个 store / 42 个 API 模块 / 1 份路由表
> **方法**：以 `src/router/index.ts` 为唯一事实源建立页面地图，逐项 grep 量化 + 关键文件精读，所有结论均附证据

---

## 0. 审计摘要（先结论）

KnowFlow 的**设计系统已经建成且质量不低**，但**几乎没有被采用**——这是本次审计最核心的发现，也是绝大多数"不一致"问题的真正根因。

| 指标 | 实测值 | 判断 |
|---|---|---|
| 设计令牌体系 `--kb-*` | 颜色/圆角/间距/字号/图标/导航/侧栏 + 明暗双主题，共 100+ 令牌 | ✅ 成熟 |
| `Button.vue` 采用率 | **13 / 95 页面（13.7%）**，而 **93 / 95 页面手写 `<button>`** | ❌ 形同虚设 |
| `EmptyState.vue` 采用率 | **4 / 95（4.2%）**，**66 / 95 页面手写空状态** | ❌ 形同虚设 |
| `ErrorState` / `StateView` / `Pagination` / `PageHeader` | **0 / 95** | ❌ 完全未使用 |
| 硬编码 hex 色值的页面 | **74 / 95（77.9%）** | ❌ 令牌被绕过 |
| 响应式完全未处理的页面 | **11 / 95（11.6%）** | ⚠️ 存在死区 |
| 错误处理（`getApiError`）覆盖 | **61 / 95（64.2%）**，470 个 catch 块 | ✅ 尚可 |
| Skeleton 骨架屏覆盖 | **4 / 95（4.2%）** | ❌ 严重不足 |

**一句话结论**：KnowFlow 不是"没有设计系统"，而是**有一套好系统 + 95 个各自为政的页面**。因此重构主路径不是"重新设计视觉"，而是 **Phase UI-1 先把令牌与组件强制落地**。

---

## 1. 产品定位

### 1.1 官方定位
> AI 知识库 + AI 学习平台。

### 1.2 应有主轴
```
知识 → 学习 → 练习 → 复习 → 掌握
```

### 1.3 实测定位偏移

通过导航权重统计（C 端顶部导航），主流程与辅助功能的入口配比如下：

| 类别 | 导航入口数 | 占比 |
|---|---|---|
| **学习主流程**（学习中心/路径/闪卡/复习/代码练习/今日计划） | 7 | 26.9% |
| **知识库浏览**（知识库/分类/搜索/文档库/知识图谱） | 5 | 19.2% |
| **成长/游戏化**（任务中心/任务清单/日历/每日打卡/习惯打卡/成就/称号） | 7 | 26.9% |
| **社区/IM**（社区/学习小组/私信） | 3 | 11.5% |
| **AI 工具**（智能问答/编程Agent/智能测验/智能写作） | 4 | 15.4% |

**判断**：成长/游戏化（26.9%）与学习主流程（26.9%）**权重相等**，社区+IM 占 11.5%。
对于"AI 知识库 + AI 学习平台"这一定位，辅助功能合计已占 **53.8%**，**学习主轴被稀释**。

**这不是要删除功能**，而是导航权重需要重新分配（见 §28）。

---

## 2. 页面地图（以真实 Router 为唯一事实源）

### 2.1 C 端：发现与浏览

| 路由 | 页面文件 | 页面作用 | 所属模块 |
|---|---|---|---|
| `/` | Home.vue | 首页 Dashboard | 首页 |
| `/knowledge` | KnowledgeHome.vue | 知识库首页（fullscreen） | 知识库 |
| `/knowledge/upload` | KnowledgeUpload.vue | 上传文档 | 知识库 |
| `/knowledge/import` | KnowledgeImport.vue | 批量导入 | 知识库 |
| `/obsidian/import` | ObsidianImport.vue | Obsidian 模板导入 | 知识库 |
| `/import-templates` | ImportTemplates.vue | 规则模板 | 知识库 |
| `/knowledge/reader` | LocalReader.vue | 本地阅读器（无布局壳） | 知识库 |
| `/knowledge/new` | KnowledgeCreate.vue | 新建知识 | 知识库 |
| `/doc/:id` | DocDetail.vue | 文档详情 | 知识库 |
| `/docs` | Docs.vue | 文档库 | 知识库 |
| `/categories` | Categories.vue | 分类浏览 | 知识库 |
| `/search` | SearchResult.vue | 搜索结果 | 知识库 |
| `/certificate/:id` | Certificate.vue | 证书详情 | 成长 |
| `/study-group` | StudyGroup.vue | 学习小组 | 社区 |
| `/messages` | Messages.vue | 私信 IM | 社区 |

### 2.2 C 端：学习中心（`meta.layout='c'`）

| 路由 | 页面文件 | 页面作用 | ⚠️ 备注 |
|---|---|---|---|
| `/learning` | → redirect `/learning/center` | — | — |
| `/learning/center` | **LearningReport.vue** | 学习中心总览 | ⚠️ 文件名与路由**倒挂** |
| `/learning/report` | **LearningReportView.vue** | 学习报告（独立页） | ⚠️ 与上一行命名冲突 |
| `/learning/pomodoro` | **LearningCenter.vue** | 番茄钟专注 | ⚠️ 其 `<h1>` 竟为"学习中心" |
| `/learning/paths` | LearningPaths.vue | 学习路径列表 | — |
| `/learning/path/:id` | PathDetail.vue | 路径详情 | — |
| `/learning/chapter/:id` | ChapterLearn.vue | 章节学习 | — |
| `/learning/code-practice` | CodePractice.vue | 代码练习列表 | — |
| `/learning/code-practice/:id` | CodePlayground.vue | 代码编辑运行（无布局壳） | — |
| `/challenge` `/challenge/:id` | Challenge / ChallengePlay | 挑战 | — |
| `/learning/flashcards` | FlashCardsHub.vue | 闪卡大厅 | 已整合旧入口 |
| `/learning/knowledge-graph` | KnowledgeGraph.vue | 知识图谱 | — |
| `/learning/mastery` | MasteryDashboard.vue | 掌握分布看板（C①） | — |
| `/learning/knowledge-mastery/:id` | KnowledgeMasteryDetail.vue | 知识点掌握详情 | — |
| `/learning/events` | LearningEvents.vue | 学习行为事件（埋点只读） | 偏内部 |
| `/learning/review` | ReviewPlan.vue | 复习计划 | — |
| `/learning/mode` | LearningMode.vue | 沉浸学习（无顶栏） | — |
| `/learning/focus` | FocusMode.vue | 专注模式 5 种（无顶栏） | — |
| `/learning/data-structures/red-black-tree` | RedBlackTree.vue | 红黑树可视化 | 演示性质 |
| `/learning/today-plan` | TodayPlan.vue | 今日计划（AI 编排） | — |

### 2.3 C 端：AI 助手 / 个人空间 / 工作台

| 路由 | 页面文件 | 页面作用 |
|---|---|---|
| `/chat` | Chat.vue | 智能问答（多轮+RAG+多模型） |
| `/coding/agent` | CodeAgent.vue | 编程 Agent |
| `/learning/quiz` | SmartQuiz.vue | 智能测验 |
| `/learning/writing` | SmartWriting.vue | 智能写作 |
| `/tasks` | TaskCenter.vue | 任务中心（router 注释称"新的工作台首页"） |
| `/task-list` | TaskManager.vue | 任务清单（Things3 式） |
| `/calendar` | CalendarView.vue | 日历 |
| `/achievements` `/kb-titles` | Achievement / KBTitle | 成就 / 称号 |
| `/check-in` `/habits` | CheckIn / Habits | 每日打卡 / 习惯打卡 |
| `/profile` `/favorites` `/notes` | Profile / Favorites / NotesManage | 个人/收藏/笔记 |
| `/mistakes` | Mistakes.vue | 错误本 |
| `/notifications` | Notifications.vue | 通知 |
| `/mindmap` `/drawing` | MindMap / DrawingEditor | 思维导图 / 绘图 |
| `/workbench` + 9 子路由 | Workbench*.vue | 学习工作台四模块闭环 |

**工作台子树（10 个路由）**：
```
/workbench              总览
  ├─ /capture           收集
  ├─ /notes  /notes/:id 康奈尔笔记
  ├─ /review            复习
  ├─ /palace /palace/:id 记忆宫殿
  ├─ /recall            主动回忆
  └─ /story  /story/:id 费曼故事
```

### 2.4 B 端：管理后台（`meta.layout='b'`）
`/admin/overview`、`knowledge`、`categories`、`docs`、`docs/new`、`docs/:id/edit`、`upload`、`tags`、`flashcards`、`learning-paths`、`learning/chapters`、AI 与生成（`quiz`/`writing`/`chat-config`）、`icons`、`files`、`code-questions`、`users`、`database`、`community` —— 共 20 个路由。

### 2.5 ⚠️ 孤儿页面（存在于 views/ 但**未注册路由**）

| 孤儿文件 | 说明 |
|---|---|
| `views/TaskKanban.vue` | 任务看板视图，已废弃 |
| `views/TaskQuadrant.vue` | 四象限视图，已废弃 |
| `views/WeeklyReport.vue` | 周报，与 `LearningReportView.vue` 功能重叠 |
| `views/LearningPrefs.vue` | 学习偏好设置，未接入 |

**影响**：4 个已开发页面无法访问，是"任务系统多套并存"认知混乱的直接来源之一。

### 2.6 兼容重定向（历史包袱）
`/portal→/tasks`、`/register→/login?tab=register`、`/learning/my-flashcards→/learning/flashcards`、`/learning/flashcards-old→/learning/flashcards`、`/docs/new→/admin/docs/new`、`/upload→/admin/upload`。

---

## 3. 用户核心流程（新用户视角走查）

模拟**第一次注册**的新用户，逐步记录：

| 步骤 | 页面 | 我现在在哪？ | 我该做什么？ | 页面告诉我了吗？ | 判定 |
|---|---|---|---|---|---|
| 1 | `/login` | 登录页 | 登录 | ✅ 明确 | ✅ |
| 2 | `/` 首页 | 首页 | 开始学习 | ⚠️ 有 CTA（`dynamicCta`）但**未回答"今天该复习什么"** | ⚠️ |
| 3 | `/knowledge` | 知识库 | 选知识 | ⚠️ 与 `/docs`、`/categories` 三入口并存，不知选哪个 | ❌ |
| 4 | `/doc/:id` | 文档详情 | 阅读 | ✅ 基本明确 | ✅ |
| 5 | 开始学习 | ？ | ？ | ❌ **阅读→学习无直接通路**：文档详情页无"就此文档开始学习/生成题目"入口 | ❌ |
| 6 | `/learning/center` | "学习中心" | 看数据 | ⚠️ 页面以概览数据为主，不像"学习入口" | ⚠️ |
| 7 | `/learning/paths` | 路径列表 | 选路径 | ✅ | ✅ |
| 8 | `/learning/path/:id` | 路径详情 | 选章节 | ✅ DAG 清晰 | ✅ |
| 9 | `/learning/chapter/:id` | 章节学习 | 学完 | ✅ | ✅ |
| 10 | 做题 | `/learning/quiz` | 练习 | ⚠️ 与章节学习未打通（无"学完即测"） | ⚠️ |
| 11 | 错题 | `/mistakes` | 看错题 | ❌ **无"知识点→薄弱→重学"链路** | ❌ |
| 12 | 闪卡 | `/learning/flashcards` | 复习 | ⚠️ 有 SM-2 与评分，但**不说明为何此刻复习** | ⚠️ |
| 13 | 复习 | `/learning/review` | 按计划复习 | ⚠️ 与闪卡、工作台复习三者关系不明 | ⚠️ |
| 14 | 看结果 | `/learning/report` | 看报告 | ⚠️ 与 `/learning/center` 名称混淆 | ⚠️ |

**断点结论**：流程中 **3 个硬断点**（步骤 5、11、12）与 **4 个认知断点**（3、6、13、14）。

---

## 4. 当前 UI 架构

### 4.1 双布局壳
| 布局 | 组件 | 结构 | 适用 |
|---|---|---|---|
| `meta.layout='c'` | `CLayout.vue` + `CTopNav.vue` | 固定顶栏(h-14) + 内容区 `pt-14` | C 端全部页面 |
| `meta.layout='b'` | `AppShell.vue` + `Sidebar` + `BTopbar` | 左栏(240/64 折叠) + 顶栏 + 可滚动主区 | 管理后台 |
| `meta.layout='none'` | 无 | 全屏 | 登录/阅读器/沉浸/404 |

### 4.2 内容宽度策略（**不一致**）
- `CLayout`：`max-w-7xl`（1280px）居中；`fullscreen` 时取消限制
- `AppShell`：`max-w-[1400px]`；`fullscreen` 时取消限制
- **两套最大宽度（1280 vs 1400）**，跨端视觉节奏不统一。

### 4.3 组件分层
`components/` 下 7 组：`ui/`(20 个基础件)、`layout/`(8)、`immersive/`(12)、`agent/`(4)、`calendar/`(3)、`community/`(3)、`doc/`(1)、`Knowledge/`(3)、`Learning/DagGraph.vue`。

---

## 5. 当前 UX 架构

### 5.1 导航结构（C 端顶部）
8 个顶级入口，其中 5 个为下拉：

```
首页 │ 学习工作台 │ 知识库▾ │ 学习▾ │ 社区▾ │ AI助手▾ │ 成长▾ │ 我的▾
```

各下拉菜单项数：知识库 5 / 学习 7 / 社区 3 / AI助手 4 / 成长 7 / 我的 4 = **30 个二级项**。

### 5.2 状态反馈组件完备度
| 状态 | 组件 | 采用 |
|---|---|---|
| 空状态 | `EmptyState.vue` | 4 / 95 |
| 错误态 | `ErrorState.vue` | **0 / 95** |
| 三态统一 | `StateView.vue` | **0 / 95** |
| 骨架屏 | `SkeletonList.vue` | 4 / 95 |
| 分页 | `Pagination.vue` | **0 / 95** |
| 页头 | `PageHeader.vue` | **0 / 95** |

---

## 6. P0 问题（影响核心使用，必须优先修）

### P0-1　AI 对话完全缺失学习闭环
**证据**：`Chat.vue`（2079 行）中对下列关键词的命中数**全部为 0**：
`生成闪卡` 0 / `闪卡` 0 / `加入错题` 0 / `错题` 0 / `继续学习` 0 / `学习路径` 0 / `主动回忆` 0 / `相关知识点` 0 / `生成题目` 0

**现象**：AI 回答后仅是一个聊天气泡，无任何学习动作。
**影响**：AI 是 KnowFlow 的核心卖点，但与学习主流程完全脱节——用户问完"什么是 volatile"就结束了，知识无法沉淀成闪卡/题目/错题。
**原则**：AI 应服务于"学"，而非"聊"。

---

### P0-2　错题只是"错误记录"，闭环完全断裂
**证据**：`Mistakes.vue`（392 行）关键词命中：
`知识点` **0** / `薄弱` **0** / `重新学习|重学` **0** / `再次练习|重做` **0**
仅有：`掌握` 6 / `分类|标签` 3 / `统计` 1 / `筛选` 1

**现象**：错题页只能标记"已掌握"，无法回溯到知识点、无法聚合出薄弱点、无法触发重新学习与再次练习。
**影响**：错题是学习平台最重要的反馈信号，当前形态使其退化为"历史日志"。

---

### P0-3　首页未回答 6 个关键问题中的 2 个（含核心复习闭环）
**证据**：`Home.vue` 全部区块标题（grep `section-title|<h2|<h3`）：
- 欢迎横幅（学习小时 / 篇文档 / 天连击 + CTA）
- `今日学习进度`（L56）
- `今日待办与计划`（L131）
- `为你推荐`（L187）
- `继续学习`（L227）
- `AI 学习建议`（L267）

**对照 6 问**：
| 问题 | 是否回答 |
|---|---|
| 我正在学什么？ | ✅ 今日学习进度 |
| 今天应该学什么？ | ✅ 今日待办与计划 |
| 我已经学了什么？ | ✅ 学习小时 / 文档数 |
| 最近学习进度怎么样？ | ✅ 今日学习进度 |
| **哪里薄弱？** | ❌ **无** |
| **今天应该复习什么？** | ❌ **无** |

**影响**：复习是学习平台的核心留存引擎，首页却不呈现"今日待复习"，用户必须通过 `我的▸…` 或 `/learning/review` 才能找到。

---

### P0-4　设计系统已建成但未被采用（根因级问题）
**证据**：
- `Button.vue` 采用 **13/95**，而 **93/95 页面手写 `<button>`**
- `EmptyState` **4/95**，**66/95 手写空状态**（`length === 0`）
- `ErrorState` **0/95**、`StateView` **0/95**、`Pagination` **0/95**、`PageHeader` **0/95**
- `Select`/`Textarea`/`Checkbox` **0/95**
- **74/95 页面含硬编码 hex 色值**

**影响**：这是所有"按钮长得不一样 / 卡片间距不一样 / 空状态文案不一致"的**共同根因**。不先解决它，后续任何视觉重构都会被 95 个页面的手写样式稀释掉。

---

## 7. P1 问题（严重影响体验）

### P1-1　导航入口重复与命名倒挂（**严重 IA 缺陷**）
**证据 A —— 入口重复**：`CTopNav.vue` 中"学习工作台"出现**两次**
- L30-39：顶级直链 `<router-link to="/workbench">学习工作台</router-link>`
- L586：又出现在 `learningMenu` 数组中（`{ path: '/workbench', label: '学习工作台' }`）

**证据 B —— 文件名与路由完全倒挂**（三文件互相打架）：
| 路由 | 文件 | 页面自称 |
|---|---|---|
| `/learning/center` | `LearningReport.vue` | 注释 L347："**学习中心**：名人名言+学习数据概览+…" |
| `/learning/pomodoro` | `LearningCenter.vue` | **L6 `<h1 class="page-title">学习中心</h1>`** |
| `/learning/report` | `LearningReportView.vue` | L5 `title="学习报告"` + 面包屑「学习中心 › 学习报告」 |

**结论**：`/learning/center` 与 `/learning/pomodoro` **两个不同页面都自称"学习中心"**，且 `LearningCenter` / `LearningReport` 两个文件名与所服务的路由**正好互换**。用户与后续维护者均无法分辨。

**证据 C —— 概念重名**：
- `/workbench` = "学习工作台"（C 端顶级入口）
- `/tasks` = "任务中心"，但 router L17 注释称其为"**新的工作台首页**"
→ 两处都叫"工作台"。

**证据 D —— 多套并存**：
- 任务：`/tasks`（任务中心）+ `/task-list`（任务清单）+ 孤儿 `TaskKanban` + 孤儿 `TaskQuadrant`
- 笔记：`/notes`（笔记管理）+ `/workbench/notes`（工作台笔记）
- 复习：`/learning/review` + `/workbench/review` + `/learning/flashcards`

---

### P1-2　闪卡缺失"为何此刻复习"与"完成反馈"
**证据**：`FlashCardsHub.vue`（2135 行）命中：
`为什么` **0** / `到期|due` **0** / `完成|congrat` **0**
存在：`SM-2|ease|interval` 6 / `Rating|Again|Hard|Good|Easy` 8 / `下一张|next` 8

**现象**：SM-2 算法与评分机制**已实现**，但用户看不到"这张卡为什么现在出现（到期？新卡？掌握度下降？）"，复习结束也**没有任何完成反馈**。
**影响**：算法在跑，用户无感知 → 信任感与完成感缺失。

---

### P1-3　知识图谱偏"装饰"，缺乏学习引导
**证据**：`KnowledgeGraph.vue` 命中：
`前置` 3 / `相关` 2 / `薄弱` 1 / `聚焦` 1
`推荐` **0** / `当前知识点` **0** / `待学|建议学` **0** / `路径` **0**

**现象**：有节点、连线、缩放、前置/相关关系，但**没有"我现在该看哪个节点"**的引导。
**影响**：图谱是"可看的图"，不是"可用的学习导航"。

---

### P1-4　响应式存在 11 个死区页面
**证据**：**完全无任何** `sm:|md:|lg:|xl:|@media` 的页面（11/95）：
`DrawingEditor.vue`、`CalendarView.vue`、`MindMap.vue`、`Docs.vue`、`LearningEvents.vue`、`ImportTemplates.vue`、`PostEdit.vue`、`admin/CategoryMgmt.vue`、`admin/FileMgmt.vue`、`OAuthCallback.vue`、`Redirect.vue`

**其中风险最高**：
- `CalendarView.vue`（月视图网格，390px 必然溢出）
- `MindMap.vue` / `DrawingEditor.vue`（画布类，无响应式等于移动端不可用）
- `Docs.vue`（文档库列表）

---

### P1-5　代码练习三栏布局在窄屏必然崩塌
**证据**：`CodePlayground.vue`（**2659 行**）为三面板布局：
- L52 `problem-panel`（题目）/ L200 `editor-panel`（编辑器）/ 控制台（L235，含测试结果 L339、AI 助手 L354 两个 Tab）
- 但全文件响应式规则 **仅 2 处**

**现象**：信息层级本身**设计合理**（题目/代码/结果+AI 分区，且 AI 以内联 Tab 呈现、支持"AI 解释这个错误"、`SC1-AI-03 错题归集`），但三栏在 768/390 下无降级策略。
**影响**：移动端/平板完全不可用。

---

### P1-6　Skeleton 骨架屏覆盖仅 4.2%
**证据**：`<SkeletonList|<Skeleton` 命中 **4/95**；而 69/95 页面有 `loading` 标志位。
**影响**：大量列表加载时白屏或仅一个 spinner，缺少结构性占位。

---

## 8. P2 问题（视觉与一致性）

### P2-1　设计令牌双轨制 → 深色模式必然漂移
**证据**：`tailwind.config.js`
- L17-33：语义色**正确**指向 `--kb-*`（card/background/foreground/border/muted…）
- L38-49：`primary` 却是**硬编码色板** `500:'#3B6FE0'` …
- `style.css` L232：深色模式 `--kb-primary: #4F86F9`

**结论**：`text-foreground`（走 `--kb-*`，跟随深色）与 `text-primary-500`（固定 `#3B6FE0`，**不跟随深色**）混用 → 深色模式下主色不一致。
`tailwind.config.js` L2-4 的注释已自承："修改主题请先改 style.css 再同步此处，**防止两套令牌漂移**"——说明漂移是被预知但未根治的风险。

**实测使用硬编码 Tailwind 原色板的页面**：15/95。

---

### P2-2　Button 组件缺少 `danger` 变体
**证据**：`Button.vue` L45 `variant?: 'primary' | 'secondary' | 'text' | 'ghost'` —— **无 danger/destructive**。
而 `--kb-destructive` 令牌**已定义**（style.css L36）。
**影响**：所有删除/危险操作只能各页面手写红色按钮 → 危险操作样式不统一。

---

### P2-3　兄弟组件内部实现不一致
**证据**：
- `EmptyState.vue` L39-63：使用**硬编码 Tailwind 原色板** `bg-blue-50`/`bg-yellow-50`/`bg-green-50`/`text-warning-500`
- `ErrorState.vue` L2-19：使用**令牌化的全局类** `.kb-state` / `.kb-state-error` / `.kb-state-retry`

**结论**：同为状态类组件，一个用令牌、一个用硬编码色板，深色模式下表现必然分叉。

---

### P2-4　内容最大宽度两套标准
`CLayout` 用 `max-w-7xl`(1280px)，`AppShell` 用 `max-w-[1400px]`。

---

### P2-5　DAG 存在单点硬编码
**证据**：`components/Learning/DagGraph.vue` L492 `--dag-locked: #94a3b8;`（其余 `--dag-completed/--dag-available` 均正确引用 `--kb-accent`/`--kb-primary`）。
**影响**：灰色锁定态不跟随深色模式。

---

### P2-6　游戏化要素分布偏广
**证据**：`经验|exp` **30/95 页面**、`等级|level` **26/95**、`连击|streak` 11/95、`成就` 6/95、`能量` 2/95。
**判断**：尚未到"抢走注意力"的程度，但等级/经验出现在近 1/3 页面，建议在**学习主流程页面收敛**，仅在首页/成长页集中呈现。

---

## 9. P3 问题（增强项）

### P3-1　动效
`CTopNav` 下拉已具备 150-200ms 过渡（`nav-dropdown` opacity/transform、`duration-200`）、抽屉 `duration-300`、AppShell `transition-all duration-300` —— **基础动效已有**。
缺：页面切换过渡、列表项进出、`Celebration.vue`（358 行）在 95 页面中的使用场景偏少、**经验/等级变化缺少数值滚动反馈**。

### P3-2　微交互
建议补充：卡片 hover 抬升（`shadow-card-hover` 已定义但需验证使用）、按钮 active 态、进度条填充动画。

### P3-3　视觉装饰
`Home.vue` 已有 welcome-banner 渐变模糊（`banner-blur`/`banner-blur-warm`）与 `reveal-item` 入场（`--reveal-index`），属**正向资产**，建议提炼为全站可复用的 Hero 组件。

---

## 10. 导航问题

| # | 问题 | 证据 | 级别 |
|---|---|---|---|
| N1 | 顶级入口 8 个、二级项 30 个，信息量过载 | CTopNav L18-214 | P1 |
| N2 | "学习工作台"在顶级与"学习"下拉**重复出现** | CTopNav L30-39 vs L586 | P1 |
| N3 | 学习中心/学习报告/番茄钟**命名倒挂** | 见 P1-1 证据 B | P1 |
| N4 | `/workbench` 与 `/tasks` 都叫"工作台" | router L17 注释 | P1 |
| N5 | 任务系统 4 套（含 2 个孤儿文件） | router + 孤儿清单 | P1 |
| N6 | 笔记双入口 `/notes` 与 `/workbench/notes` | router L437 / L354 | P2 |
| N7 | 复习三入口（review / workbench/review / flashcards） | router | P2 |
| N8 | 成长/游戏化占 7 个二级项，与学习主流程等权 | CTopNav L602-610 | P1 |
| N9 | 知识库下拉 5 项与"文档库/分类浏览"功能重叠 | CTopNav L570-576 | P2 |
| N10 | 4 个孤儿页面未注册路由 | TaskKanban/TaskQuadrant/WeeklyReport/LearningPrefs | P2 |

---

## 11. 首页问题

| # | 问题 | 级别 |
|---|---|---|
| H1 | 未回答"**哪里薄弱**" | P0 |
| H2 | 未回答"**今天该复习什么**"（核心留存闭环缺失） | P0 |
| H3 | 6 个区块（进度/待办/推荐/继续学习/AI建议）信息密度高但**主次不分明**，缺少视觉锚点 | P2 |
| H4 | 响应式仅覆盖 640/1024，**缺 768（平板）断点**（L841/847/1391/1397 仅 min-width:640 与 1024） | P2 |
| H5 | 空状态手写（L150/218/258 `v-if="...length === 0"`），未用 `EmptyState` 组件 | P2 |
| H6 | 含 9 处硬编码 hex | P2 |

**首页正面积累**（应保留并提炼）：`dynamicCta` 动态主行动按钮、`dynamicTagline` 动态文案、`reveal-item` 入场序列、welcome-banner 视觉。

---

## 12. 学习中心问题

| # | 问题 | 级别 |
|---|---|---|
| L1 | **命名倒挂**：`/learning/center`(LearningReport.vue) 与 `/learning/pomodoro`(LearningCenter.vue，h1="学习中心") 互相打架 | **P1** |
| L2 | `/learning/center` 与 `/learning/report` 两个"报告/中心"页并存，面包屑把二者绑成父子（学习中心›学习报告），但其实是两个独立路由 | P1 |
| L3 | 学习中心缺少**统一的任务收口**（今日待学 / 待复习 / 待巩固 三 bucket） | P1 |
| L4 | 章节学习 → 做题 **未打通**（无"学完即测"） | P1 |
| L5 | 学习中心 ↔ 工作台 ↔ 任务中心 三者的边界用户不可辨 | P1 |
| L6 | `LearningEvents.vue`（埋点只读视图）暴露在 C 端学习菜单之外，属内部工具页混入 | P3 |

---

## 13. 知识库问题

| # | 问题 | 级别 |
|---|---|---|
| K1 | **三入口并存**：`/knowledge`（知识库）、`/docs`（文档库）、`/categories`（分类浏览）让新用户不知选哪个 | P1 |
| K2 | 文档详情 → 学习 **无通路**（阅读完无法"就此文档生成题目/加入学习路径"） | P1 |
| K3 | `LocalReader.vue`（无布局壳）与 `DocDetail.vue` 定位重叠 | P2 |
| K4 | 导入入口 4 个（upload / import / obsidian / templates + create），首次"加知识"易迷路 | P2 |
| K5 | `Docs.vue` 无任何响应式处理 | P1 |
| K6 | 搜索/筛选/排序/分页能力需逐页核对（`Pagination` 组件 0 采用，疑为各页手写） | P2 |

---

## 14. AI Chat 问题

| # | 问题 | 级别 |
|---|---|---|
| A1 | **学习闭环 0 实现**（9 个关键词全 0 命中） | **P0** |
| A2 | AI 回答后无"沉淀"动作（闪卡/题目/错题/路径） | P0 |
| A3 | 4 个 AI 入口（chat / coding-agent / quiz / writing）分散在 AI助手与学习两个下拉，用户难辨差异 | P2 |
| A4 | RAG 引用可溯源性待逐行确认（本轮未深入） | 待核 |

**建议闭环动作清单**（AI 回答下方动作条）：
```
继续学习此知识点 │ 生成闪卡 │ 生成练习题 │ 加入错题本 │ 加入学习路径 │ 主动回忆检测 │ 查看相关知识点
```

---

## 15. 学习路径问题（含 DAG 评估）

### 15.1 ✅ DAG 现状评估：**实现良好，不建议重造**
**证据**（`components/Learning/DagGraph.vue`）：
- 三态齐全：`completed` / `available` / `locked`（L178-182 定义，中文为 已完成 / 可学习 / 未解锁）
- **图例 L12-14**：`dot-completed` / `dot-available` / `dot-locked` 三色圆点 + 文案
- **节点图标 L99-100**：已完成显示 `check` 图标，未解锁显示 `lock` 图标
- **连线状态 L68-69**：`edge-${edge.status}` + 按状态切换箭头 marker
- **Tooltip L118**：`tip-status` + `STATUS_TEXT[tooltip.status]`
- **令牌化 L490-491**：`--dag-completed: var(--kb-accent)`、`--dag-available: var(--kb-primary)`

**结论**：DAG 已同时具备「颜色 + 图标 + 图例 + 悬浮说明 + 连线状态」**五重编码**，信息层级清晰，用户可一眼区分已完成/可学/锁定。
**唯一缺陷**：L492 `--dag-locked: #94a3b8` 硬编码（P2，见 P2-5）。

> 按"不要重新造无关的图谱"的要求：**本轮明确不建议重构 DagGraph，仅需修令牌硬编码**。

### 15.2 其余问题
| # | 问题 | 级别 |
|---|---|---|
| P1 | 路径详情 → 章节学习 → 练习 的链路中，"练习"环节缺失 | P1 |
| P2 | 章节完成后的下一步引导（下一章？做题？复习？）需确认 | 待核 |

---

## 16. 闪卡问题

| # | 问题 | 级别 |
|---|---|---|
| F1 | 不说明"**为什么现在复习这张卡**"（`为什么` 0 / `到期|due` 0） | **P1** |
| F2 | 复习**完成无反馈**（`完成|congrat` 0） | **P1** |
| F3 | SM-2 存在（ease/interval 6 处）但不向用户解释难度系数与下次间隔 | P2 |

**建议**：
- 卡面增加来源标签：`新卡` / `到期复习` / `掌握度下降` / `错题转化`
- 评分后即时反馈：`下次复习：3 天后`
- 会话结束：完成卡片数 + 掌握度变化 + 连续天数

---

## 17. 错题问题

| # | 问题 | 级别 |
|---|---|---|
| M1 | **无知识点关联**（`知识点` 0） | **P0** |
| M2 | **无薄弱点聚合**（`薄弱` 0） | **P0** |
| M3 | **无重新学习 / 再次练习**（均 0） | **P0** |
| M4 | 仅有"已掌握"标记（6 处）、筛选（1）、统计（1）、分类（3） | — |

**应有闭环**：
```
错题 → 关联知识点 → 聚合薄弱点 → 推荐重学资源 → 再次练习 → 掌握度回升 → 移出
```

---

## 18. 代码练习问题

| # | 问题 | 级别 |
|---|---|---|
| C1 | 三栏布局（题目/编辑器/控制台）**仅 2 处响应式** → 768/390 崩塌 | **P1** |
| C2 | 2659 行单文件，题目+编辑器+输出+AI+测试全部耦合 | P2 |

**✅ 值得肯定的信息层级设计**（不应推倒）：
- 三面板分区清晰（`problem-panel` / `editor-panel` / 控制台）
- AI 以**内联 Tab**（L354）呈现，而非浮窗堆叠 —— 避免了"IDE+AI+题库+文档堆一起"
- 错误可直接"用 AI 解释这个错误"（L305/360）
- 具备 `SC1-AI-03 错题归集`（L315）—— 与错题模块已有联动意图

---

## 19. 知识图谱问题

| # | 问题 | 级别 |
|---|---|---|
| G1 | 无"**当前知识点**"定位（0） | P1 |
| G2 | 无"**推荐学习**"路径（0） | P1 |
| G3 | 无"待学/建议学"（0） | P1 |
| G4 | 已有 前置(3)/相关(2)/薄弱(1)/聚焦(1) 的语义基础 | 资产 |

**建议学习模式**（基于已有语义扩展，不重造）：
```
当前知识点 → 前置知识（未掌握则高亮）→ 相关知识 → 薄弱知识 → 推荐下一节点
```
配合"聚焦模式"（已有 focus 1 处）做子图收敛。

---

## 20. 响应式问题

### 20.1 覆盖率
- 有任何响应式处理：**84 / 95（88.4%）**
- 完全无处理：**11 / 95（11.6%）**

### 20.2 断点使用分布
`sm:` 16 页 / `md:` 20 页 / `lg:` 26 页 / `xl:` 4 页
→ **大量页面断点使用稀疏（仅用 1 个断点）**，768px 平板区间普遍未被专门处理。

### 20.3 高风险清单
| 页面 | 风险 |
|---|---|
| `CalendarView.vue` | 月视图网格，无任何响应式 → 390px 必然横向溢出 |
| `MindMap.vue` / `DrawingEditor.vue` | 画布类，移动端基本不可用 |
| `CodePlayground.vue` | 三栏仅 2 处响应式 |
| `Docs.vue` | 列表页无响应式 |
| `Home.vue` | 有 640/1024，**缺 768** |

### 20.4 固定宽度
10 个页面使用 `w-[Npx]` / `min-w-[Npx]` 固定像素宽度，为溢出隐患。

---

## 21. Design System 问题

### 21.1 ✅ 已有资产（质量良好，应保留）
`src/style.css` 中 `--kb-*` 令牌体系完整：
- **颜色**（L19-39）：background/foreground/card/popover/primary/muted/border/input/ring/sidebar/accent/destructive/warning + 各自的 -foreground
- **状态色**（L58-61）：state-success/warning/error/info
- **高光色**（L47-50）：highlight 系列
- **圆角**（L71-73 / L211）：sm 6 / md 10 / lg 16 / xs 4
- **字号**（L103-111）：h1 40 / h2 32 / h3 24 / h4 20 / body-lg 16 / body-md 14 / body-sm 13 / caption 12 / xs 11，各带 line-height 与 font-weight
- **图标**（L124-131）：xxs 10 → 3xl 32，8 级
- **间距**（L138-144）：space-1 4 / 2 8 / 3 12 / 4 16 / 5 20 / 6 24 / 8 32
- **导航/侧栏**（L153-175）：nav-height、nav/dropdown/sidebar 各级字号间距
- **深色主题**（L226-259）：`[data-theme="dark"]` 完整覆盖

**评价**：这是一套**达到生产级完整度**的令牌体系（明暗双主题、语义化命名、尺度齐全）。

### 21.2 ❌ 核心问题：**采用率崩溃**

| 组件 | 采用页面 / 95 | 采用率 |
|---|---|---|
| Button | 13 | 13.7% |
| Card | 7 | 7.4% |
| Badge | 7 | 7.4% |
| Input | 6 | 6.3% |
| EmptyState | 4 | 4.2% |
| SkeletonList | 4 | 4.2% |
| Progress | 2 | 2.1% |
| Avatar | 2 | 2.1% |
| StatCard | 1 | 1.1% |
| **ErrorState** | **0** | **0%** |
| **StateView** | **0** | **0%** |
| **Pagination** | **0** | **0%** |
| **PageHeader** | **0** | **0%** |
| **Select / Textarea / Checkbox** | **0** | **0%** |

**对照**：93/95 页面手写 `<button>`；66/95 页面手写空状态；74/95 页面硬编码 hex。

### 21.3 其他问题
- 令牌双轨导致深色漂移（P2-1）
- Button 缺 danger 变体（P2-2）
- EmptyState 与 ErrorState 内部实现不一致（P2-3）
- 两套内容最大宽度（P2-4）

---

## 22. Loading 问题

| 指标 | 数值 |
|---|---|
| 有 loading 标志位的页面 | 69 / 95 |
| 使用 Skeleton 骨架屏 | **4 / 95（4.2%）** |
| `SkeletonList.vue` 组件 | 已存在（72 行）但未推广 |

**问题**：
1. 骨架屏覆盖极低，列表加载多为"空白等待"
2. AI 流式（streaming）加载态需确认是否有打字机/流式指示（`Chat.vue` 具备 `aiAnswering` 概念，见 CodePlayground L31 `AI 思考中...`，说明模式已有）
3. 上传进度：项目已做"进度条+分片上传+断点续传"，属**正面积累**

---

## 23. Empty State 问题

| 指标 | 数值 |
|---|---|
| 手写空状态（`length === 0`） | **66 / 95** |
| 使用 `EmptyState.vue` | **4 / 95** |

**问题**：66 个页面各自实现空状态 → 文案、图标、间距、CTA 必然不一致。
**示例**（Home.vue）：
- L150 `v-if="tasks.length === 0"` → "暂无待办，去学习中心添加计划吧"
- L218 `v-if="learningPaths.length === 0"`
- L258 `v-if="continueDocs.length === 0"`
三处均手写，样式由 `.empty-state`（L1574）局部定义。

**要求**：空状态必须具备「说明 + 下一步按钮」三要素，当前 66 处手写无法保证。

---

## 24. Error State 问题

| 指标 | 数值 |
|---|---|
| 使用 `getApiError` 的页面 | **61 / 95（64.2%）** |
| catch 块总数 | 470 |
| 使用 `ErrorState.vue` | **0 / 95** |
| 使用 `StateView.vue` | **0 / 95** |
| 空 catch 静默吞错的页面 | 2 |

**评价**：错误**文案**处理尚可（`getApiError` 覆盖率 64%，能给出友好信息而非裸 500），示例见 `LearningReportView.vue` L302-303：
```ts
error.value = '学习报告加载失败：' + getApiError(e)
notify('学习报告加载失败', 'error')
```
**问题**：
1. 错误**呈现形态**完全不统一——61 个页面各自渲染错误 UI，`ErrorState` 组件 0 采用
2. 剩余 **34/95 页面**未接入 `getApiError`，存在裸错误/静默失败风险
3. 2 个页面存在空 `catch {}` 静默吞错

---

## 25. Animation 问题

**已有**（正向）：
- 下拉菜单：`opacity/transform` + `0.15s`（CTopNav `.nav-dropdown`）
- 抽屉：`transition-transform duration-300`
- 侧栏折叠：`transition-all duration-300`
- 首页 `reveal-item` 序列入场 + `banner-blur` 渐变
- `Celebration.vue`（358 行）庆祝动效

**缺失**：
| 场景 | 建议 |
|---|---|
| 页面切换 | 100-200ms 淡入/位移 |
| Modal / Drawer | 已有框架，需统一到 150-250ms |
| Card hover | `shadow-card`（已定义）→ `shadow-card-hover` 过渡 |
| 进度条 | 填充动画（进度变化需有过渡而非跳变） |
| 学习完成 / 等级提升 | 已有 Celebration，需在学习闭环各节点接入 |
| 经验增加 | 数值滚动/微弹 |

**原则**：100–300ms，用于**表达状态变化**，不做炫技装饰。

---

## 26. Accessibility 问题

**已有正面积累**（`CTopNav.vue`）：
- `aria-haspopup="menu"` / `aria-expanded`（L48-49 等）
- `aria-label` 完备：搜索按钮 L246、通知按钮 L261（`消息通知，${unreadCount} 条未读`）、头像 L278、菜单 L297、关闭 L418
- `role="menu"` / `role="menuitem"`（L55, L363-367）
- `focus-visible:ring-2 ring-offset-2`（L228）

**待改进**：
| # | 问题 | 级别 |
|---|---|---|
| AC1 | 下拉菜单仅为 `click` 触发，**无键盘方向键导航与 Esc 关闭** | P2 |
| AC2 | 焦点管理：抽屉/Modal 打开后焦点未捕获与归还 | P2 |
| AC3 | 硬编码 74/95 页面颜色中，对比度未做 WCAG 校验 | P2 |
| AC4 | 空/错误状态缺少 `role="status"` / `aria-live` 播报 | P3 |
| AC5 | 下拉菜单点击外部关闭依赖全屏透明遮罩（`fixed inset-0 z-40`），语义上应为 focus-trap | P3 |

---

## 27. 推荐 Design System

### 27.1 核心策略：**不新建，先落地**
现有 `--kb-*` 令牌体系质量良好，**无需推倒重来**。Phase UI-1 的任务是**强制落地 + 补齐缺口**。

### 27.2 需补齐的令牌
```css
:root {
  /* —— 补齐：语义状态前景/边框 —— */
  --kb-state-success-soft: rgba(16,185,129,.10);
  --kb-state-warning-soft: rgba(245,158,11,.10);
  --kb-state-error-soft:   rgba(239,68,68,.10);
  --kb-state-info-soft:    rgba(59,111,224,.10);

  /* —— 补齐：层级阴影（已有 shadow-card/card-hover，需接入令牌）—— */
  --kb-shadow-card:       0 2px 8px 0 rgba(0,0,0,.04);
  --kb-shadow-card-hover: 0 8px 24px 0 rgba(0,0,0,.08);

  /* —— 补齐：动效时长 —— */
  --kb-dur-fast:   150ms;
  --kb-dur-base:   200ms;
  --kb-dur-slow:   300ms;
  --kb-ease-std:   cubic-bezier(.4,0,.2,1);

  /* —— 补齐：层级 z-index —— */
  --kb-z-dropdown: 100;
  --kb-z-overlay:   40;
  --kb-z-modal:     50;
  --kb-z-toast:     60;

  /* —— 补齐：布局宽度（消除 1280/1400 双标准）—— */
  --kb-content-max: 1280px;
}
```

### 27.3 组件补齐清单
| 组件 | 动作 |
|---|---|
| `Button.vue` | **新增 `danger` variant**；variant 全部改为引用 `--kb-*` 令牌 |
| `Card.vue` | 统一 padding / radius / border / shadow-card |
| `Pagination.vue` | 从 0 采用 → 推广到所有列表页 |
| `PageHeader.vue` | 从 0 采用 → 统一页头（标题+面包屑+操作区） |
| `EmptyState.vue` | 内部改为 `--kb-state-*` 令牌（与 ErrorState 对齐） |
| `ErrorState.vue` | 从 0 采用 → 推广 |
| `StateView.vue` | 从 0 采用 → 统一 loading/empty/error 三态 |
| `SkeletonList.vue` | 从 4 → 推广 |
| `Select/Textarea/Checkbox` | 从 0 采用 → 统一表单 |

### 27.4 治理手段（关键）
1. **Stylelint 规则**：禁止 `bg-blue-50` 等原色板类，禁止硬编码 hex
2. **ESLint 规则**：禁止裸 `<button>`（强制用 `Button.vue`）
3. **codemod 批量替换**：93 个手写 `<button>` → `Button.vue`

---

## 28. 推荐导航架构

### 28.1 现状（8 顶级 / 30 二级）
```
首页 │ 学习工作台 │ 知识库▾ │ 学习▾ │ 社区▾ │ AI助手▾ │ 成长▾ │ 我的▾
```

### 28.2 推荐（**5 顶级**，围绕"学"组织）
```
学习 │ 知识 │ 复习 │ AI │ 我的
```

| 顶级 | 二级项 | 收编内容 |
|---|---|---|
| **学习** | 今日计划、学习路径、章节、代码练习、挑战 | 今日计划、学习路径、代码练习、挑战 |
| **知识** | 知识库、文档、分类、搜索、知识图谱、导入 | 合并 `/knowledge`+`/docs`+`/categories`+`/search` |
| **复习** | 闪卡、复习计划、错题、掌握度 | `/learning/flashcards`+`/learning/review`+`/mistakes`+`/learning/mastery` |
| **AI** | 智能问答、编程 Agent、智能测验、智能写作 | 4 个 AI 入口统一收口 |
| **我的** | 个人中心、任务、日历、收藏、笔记、工作台、成就、社区 | 成长类下沉到此，与学习解耦 |

### 28.3 关键决策说明（**不删除任何功能，只重组**）
1. **"学习工作台"去重**：从顶级+学习下拉两处 → 仅保留"我的▸工作台"
2. **"成长"拆解下沉**：任务/日历/打卡/习惯 → "我的"；成就/称号 → "我的▸成就"
3. **三个知识入口合一**：`/knowledge` 为主入口，`/docs`、`/categories` 降为其内部 Tab
4. **孤儿页面处置**：`TaskKanban`/`TaskQuadrant` 合并进 `/task-list` 的视图切换；`WeeklyReport` 并入 `/learning/report`；`LearningPrefs` 接入设置
5. **命名纠偏**（必须做）：
   - `LearningCenter.vue` → 重命名为 `PomodoroPage.vue`（它是番茄钟）
   - `LearningReport.vue` → 重命名为 `LearningCenterPage.vue`（它是学习中心）
   - `LearningReportView.vue` → 重命名为 `LearningReportPage.vue`
6. **"工作台"一词收敛**：`/tasks` 更名为"任务中心"（去掉"工作台首页"的注释语义），`/workbench` 保留"学习工作台"

---

## 29. 推荐首页结构

```
┌─────────────────────────────────────────────────────────┐
│  欢迎横幅（保留现有资产）                                  │
│  问候语 + 动态文案 | 学习时长/文档数/连击 | 【主 CTA】      │
│  🎯 主 CTA = 今日下一个动作（继续学习 / 开始复习 / 做计划） │
└─────────────────────────────────────────────────────────┘
┌──────────────────────┬──────────────────────────────────┐
│ ① 今日待办（3 bucket） │ ② 今日待复习 ★新增                │
│   待学 / 待复习 / 待巩固 │   待复习卡片数 + 一键开始复习      │
├──────────────────────┼──────────────────────────────────┤
│ ③ 继续学习            │ ④ 薄弱知识点 ★新增                 │
│   最近文档/章节         │   Top3 薄弱点 + 去练习             │
├──────────────────────┴──────────────────────────────────┤
│ ⑤ 本周进度趋势（热力图/柱状图）                            │
├─────────────────────────────────────────────────────────┤
│ ⑥ AI 学习建议（保留）                                     │
└─────────────────────────────────────────────────────────┘
```
**补齐 P0-3**：② 今日待复习、④ 薄弱知识点 为新增必答项。

---

## 30. 推荐学习中心结构

```
学习中心 (/learning/center) —— 唯一的学习任务收口
├─ 今日三 bucket
│   ├─ 待学（来自今日计划/学习路径）
│   ├─ 待复习（来自闪卡到期 + 复习计划）★
│   └─ 待巩固（来自错题未掌握）★
├─ 进行中的路径（进度环 + 继续）
├─ 推荐下一步（AI 编排）
└─ 数据概览（学习时长/热力图 → 折叠或移至 /learning/report）
```
**原则**：学习中心 = **行动入口**；`/learning/report` = **回看数据**。二者职责分离，命名随之纠偏。

---

## 31. 推荐核心用户流程

```
                    ┌──────────────┐
                    │   首页 /     │
                    │  学习中心    │◄─── 主 CTA 驱动
                    └──────┬───────┘
                           │
        ┌──────────────────┼──────────────────┐
        ▼                  ▼                  ▼
   【学】              【练】              【复习】
  学习路径            智能测验            闪卡(SM-2)
     │                  │                  │
  章节学习               │                  │
     │                  │                  │
     └──────► 判题 ◄────┘                  │
                 │                         │
                 ▼                         │
              【错题】──► 关联知识点 ──► 薄弱点聚合
                 │                         │
                 └──► 推荐重学 ──► 再次练习 ─┘
                                    │
                                    ▼
                              掌握度回升 → 移出错题
```

**打通当前 3 个断点**：
1. 文档 → 学习：`DocDetail` 增加"生成题目 / 加入学习路径"
2. 错题 → 薄弱 → 重学：建立知识点关联与推荐
3. 章节 → 练习：学完即测

---

## 32. UI 重构路线（13 Phase）

> **当前状态：Phase UI-1 之前。未修改任何代码。等待指令 `执行 Phase UI-1`。**

---

### Phase UI-1　Design System 落地
**目标**：让已有令牌与组件真正被 95 个页面使用（**这是所有后续 Phase 的前置**）
- **修改页面**：全站
- **修改组件**：`Button`（补 danger + 令牌化）、`Card`、`EmptyState`（令牌化）、`Pagination`、`PageHeader`、`StateView`、`ErrorState`、`SkeletonList`、`Select/Textarea/Checkbox`
- **修改样式**：`style.css` 补齐 §27.2 令牌；`tailwind.config.js` 消除 `primary/success/warning/danger` 硬编码色板 → 改引用 `--kb-*`（根治深色漂移）
- **风险**：
  - 🔴 高：93 页手写 button 批量替换可能破坏局部样式 → 必须 codemod + 逐页回归
  - 🟡 中：令牌语义替换可能改变既有视觉 → 需截图基线对比
- **验收标准**：
  - `Button` 采用率 ≥ 80%（13 → ≥76）
  - `EmptyState/ErrorState/StateView` 采用率 ≥ 80%
  - 硬编码 hex 页面 ≤ 10（74 → ≤10）
  - 深色模式下主色一致，无 `primary-500` 残留

---

### Phase UI-2　App Shell / Navigation
**目标**：8 顶级 → 5 顶级；消除重复入口；纠正命名倒挂
- **修改页面**：`CTopNav.vue`、`CLayout.vue`、`AppShell.vue`、`Sidebar.vue`、router
- **修改组件**：导航菜单数据结构
- **风险**：🔴 高 —— 路由重命名/文件重命名影响所有外链与书签 → 必须保留旧路由 301 重定向
- **验收**：顶级入口 5 个；"学习工作台"仅 1 处；无重复入口；孤儿页面 0

---

### Phase UI-3　Dashboard（首页）
**目标**：补齐"今日待复习"与"薄弱知识点"
- **修改页面**：`Home.vue`
- **修改组件**：新增 `ReviewDueCard`、`WeakPointCard`
- **风险**：🟡 中 —— 依赖后端待复习/薄弱点接口，需先确认 API
- **验收**：首页 6 问全部有答；3 秒内可识别主 CTA

---

### Phase UI-4　Learning Center
**目标**：学习中心 = 行动收口（待学/待复习/待巩固），报告页专司回看
- **修改页面**：`LearningReport.vue`→`LearningCenterPage.vue`、`LearningReportView.vue`→`LearningReportPage.vue`、`LearningCenter.vue`→`PomodoroPage.vue`
- **风险**：🟡 中 —— 文件重命名需全量更新 import
- **验收**：三 bucket 齐备；学习中心与报告职责分离；无命名歧义

---

### Phase UI-5　Knowledge Base
**目标**：三入口合一；文档 → 学习打通
- **修改页面**：`KnowledgeHome`、`Docs`、`Categories`、`DocDetail`、`SearchResult`
- **风险**：🟡 中 —— 合并入口需保留旧 URL
- **验收**：知识浏览单一主入口；`DocDetail` 有"生成题目/加入路径"

---

### Phase UI-6　AI Chat
**目标**：建立 AI 学习闭环（P0-1）
- **修改页面**：`Chat.vue`
- **修改组件**：新增 `AiAnswerActions` 动作条
- **风险**：🟡 中 —— 生成闪卡/题目/错题需后端接口支持
- **验收**：AI 回答下方含 ≥5 个学习动作；可一键生成闪卡并跳转

---

### Phase UI-7　Learning Path
**目标**：学完即测；DAG 仅修令牌硬编码（**不重造**）
- **修改页面**：`PathDetail`、`ChapterLearn`、`DagGraph.vue`（仅 L492）
- **风险**：🟢 低
- **验收**：章节完成 → 自动推荐练习；`--dag-locked` 令牌化

---

### Phase UI-8　Flashcard / Review
**目标**：补齐"为何此刻复习"与"完成反馈"
- **修改页面**：`FlashCardsHub.vue`
- **风险**：🟢 低（SM-2 已实现，仅补 UI 表达）
- **验收**：卡面显示来源标签；评分后显示下次复习时间；会话结束有完成总结

---

### Phase UI-9　Quiz / Mistake
**目标**：建立 错题→知识点→薄弱→重学→再练→掌握 闭环（P0-2）
- **修改页面**：`Mistakes.vue`、`SmartQuiz.vue`
- **风险**：🔴 高 —— 需后端知识点关联与薄弱点聚合能力
- **验收**：错题可关联知识点；薄弱点 Top N 可视化；可从薄弱点直接发起重学与练习

---

### Phase UI-10　Knowledge Graph
**目标**：从"可看的图"→"可用的学习导航"（基于已有前置/相关/薄弱语义扩展，不重造）
- **修改页面**：`KnowledgeGraph.vue`
- **风险**：🟡 中 —— 依赖图谱学习推荐数据
- **验收**：有"当前知识点"定位；可展示前置/相关/薄弱/推荐下一节点

---

### Phase UI-11　Coding
**目标**：三栏布局响应式降级
- **修改页面**：`CodePlayground.vue`
- **风险**：🟡 中 —— 2659 行单文件，建议先拆分再改
- **验收**：768/390 下三栏降级为可切换单栏；编辑器与控制台均可用

---

### Phase UI-12　Responsive
**目标**：消灭 11 个响应式死区 + 补齐 768 断点
- **修改页面**：`CalendarView`、`MindMap`、`DrawingEditor`、`Docs`、`LearningEvents`、`ImportTemplates`、`PostEdit`、`admin/CategoryMgmt`、`admin/FileMgmt`、`Home`（补 768）
- **风险**：🟡 中 —— 画布类页面（MindMap/Drawing）移动端改造成本高，可降级为"只读/提示用桌面端"
- **验收**：1440/1280/1024/768/390 五档无横向溢出；画布类有明确降级策略

---

### Phase UI-13　Animation / Micro Interaction
**目标**：统一 100–300ms 状态转场
- **修改页面**：全站
- **修改组件**：`Celebration` 接入学习闭环；进度条填充动画；Card hover
- **风险**：🟢 低
- **验收**：`--kb-dur-*` 令牌统一；所有状态变化有过渡；无炫技动画

---

## 附：审计方法与证据说明

- **事实源**：`src/router/index.ts`（671 行，全量读取）
- **导航事实源**：`src/components/layout/CTopNav.vue`（841 行，全量读取）
- **布局事实源**：`CLayout.vue` / `AppShell.vue` / `Sidebar.vue`（全量读取）
- **设计系统事实源**：`tailwind.config.js`（155 行全量）、`src/style.css` 令牌块（L19-259 grep）
- **量化方法**：对 `src/views/**/*.vue`（95 个）执行 grep 计数，所有百分比均基于 95 页样本
- **未深入项（已标注"待核"）**：`Chat.vue` RAG 引用可溯源性、`MasteryDashboard`、`Workbench` 子模块（10 个路由）、`ChapterLearn`、`SmartQuiz` 内部细节、`admin/*` 20 个页面
- **边界声明**：本轮为**只读源码审计**，未启动浏览器实机截图验证；响应式结论基于"是否编写断点/媒体查询"的静态推断，实机 5 档验证建议在 Phase UI-12 执行

---

**审计结论**：已完成源码扫描、页面扫描、UI 审计、UX 审计、用户流程审计、响应式审计、Design System 审计、问题分级、重构路线。
**本阶段未修改任何代码、未 commit、未 push。**
**等待指令：`执行 Phase UI-1`**
