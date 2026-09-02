# UI/UX 2.2 · State Component Inventory（状态组件清单）

> 生成依据：扫描 `frontend/src/components/{ui,layout,workflow}`、`frontend/src/views/*`、`frontend/src/learning/*`、`frontend/src/apps/*`、`frontend/src/config/navigation.ts`、`frontend/src/stores/*`、`frontend/src/api/*` 的真实源码。
> 所有条目均来自真实 grep / 读取，未虚构。
> 配套执行报告见 `UI-UX-2.2-执行报告.md`。

## 0. 已存在的共享状态组件（无需新建）

| 组件 | 路径 | 职责 | 视觉 |
| --- | --- | --- | --- |
| `EmptyState` | `components/ui/EmptyState.vue` | 空态三要素：title + description + `#action` 插槽 | `--kb-*` 令牌 |
| `ErrorState` | `components/ui/ErrorState.vue` | 错误态：title + description + 重试按钮（`@retry`） | `.kb-state-*` 类 |
| `SkeletonList` | `components/ui/SkeletonList.vue` | 骨架屏：list/card/line 三形态 + rows/cols | `animate-pulse`（**硬编码 gray-100/200，待修**） |
| `StateView` | `components/ui/StateView.vue` | **统一四态容器**：`status: loading\|error\|empty\|ready`，内部委托上面三者 | 跟随上面 |
| `Pagination` | `components/ui/Pagination.vue` | 分页 | `--kb-*` |

结论：规范 §15「如果项目已有类似组件，直接复用，不新建」**已满足**。不存在 `StateComponentV2 / StateRenderer2 / UniversalPageState2` 的必要。
真正的问题是**采用率不均衡**（非组件缺失），且 `SkeletonList` 硬编码色违反 `--kb-*` 令牌体系（见 §2.2 修复）。

## 1. Mini App 状态实现清单（13 个真实 App）

> 计数来自对 `src/views/*` 的真实 grep：`<EmptyState` / `<ErrorState` / `<StateView` / `animate-pulse` / `skeleton` / `暂无|还没有`。
> "重复？" = 该 App 是否仍用手写内联三态而非 `StateView`（即与共享组件重复）。

| 页面 / App | Route | Loading | Empty | Error | 当前实现 | 是否重复 |
| --- | --- | --- | --- | --- | --- | --- |
| 闪卡大厅 Flashcard | /learning/flashcards | 内联 | 内联（暂无×4） | 内联（notify） | 内联三态 | 是 |
| 智能测验 Quiz | /learning/quiz | 内联 | `EmptyState`×6 | 内联 / `ErrorState` | 部分用 `EmptyState` | 部分 |
| 错题本 Mistake | /mistakes | `animate-pulse`×2 | `EmptyState`×3 + 暂无×1 | `ErrorState`×1 + getApiError | `EmptyState`+`ErrorState`+`animate-pulse` | 否（最统一） |
| 费曼故事 Feynman | /workbench/story | `animate-pulse`×1 | 内联（暂无×1） | 内联 | 内联 | 是 |
| 康奈尔笔记 Cornell | /workbench/notes | `animate-pulse`×1 | 内联（暂无×1） | 内联 | 内联 | 是 |
| 记忆宫殿 Memory Palace | /workbench/palace | `SkeletonList`×1 | 内联（暂无×1） | 内联 | 部分 `SkeletonList` | 部分 |
| 知识图谱 Knowledge Graph | /learning/knowledge-graph | 内联（canvas） | 内联（暂无×2） | 内联 | 内联 | 是 |
| 代码练习 Coding | /learning/code-practice | `animate-pulse`×1 | 内联（暂无×2） | 内联 | 内联 | 是 |
| 思维导图 Mindmap | /mindmap | 内联（vue-flow） | 内联（暂无×1） | 内联 | 内联 | 是 |
| 绘图 Drawing | /drawing | 内联（canvas） | 内联（暂无×1） | 内联 | 内联 | 是 |
| 智能写作 AI Writing | /learning/writing | 内联（编辑器） | 内联（暂无×1） | 内联 | 内联 | 是 |
| 笔记 Notes | /notes | `SkeletonList`×11 | 内联（暂无×2） | 内联 | `SkeletonList` 重度 | 部分 |
| 专注 Focus | /learning/pomodoro | 内联（沉浸式） | 内联（沉浸式） | 内联（沉浸式） | 沉浸式自管 | 不适用（保留） |

## 2. 其它关键页面状态实现（对照）

| 页面 | Loading | Empty | Error | 当前实现 |
| --- | --- | --- | --- | --- |
| 首页 Home | `animate-pulse` | `EmptyState`/`WeakPointCard` 内联 | 内联（各卡片独立） | 各卡片独立 loading/empty/error（§67 隔离） |
| 学习中心 LearningCenter | `animate-pulse`×2 | 内联 + ReviewDueCard/WeakPointCard | 内联 | 卡片化 |
| 应用中心 App Center | — | `EmptyState`×1 | `ErrorState`×1 | 已用共享组件 |
| 社区 Community | — | `EmptyState`×3 | `ErrorState`×1 | 已用共享组件 |
| 学习报告 LearningReport | — | `EmptyState`×5 | `ErrorState`×2 | 已用共享组件 |

## 3. 重复判定与收敛建议（对应规范 §13-20）

1. **组件层已统一**：`StateView` 已实现 `loading/error/empty/ready` 四态委托，无需新建抽象。
2. **采用率问题**：仅 Mistake / AppCenter / Community / LearningReport / PostDetail / TodayPlan / NotesManage / MemoryPalace 采用了共享组件；Flashcard / Feynman / Cornell / Knowledge Graph / Coding / Mindmap / Drawing / SmartWriting 仍为内联三态。
3. **不机械批量替换（规范 §14/§17）**：内联空态形态各异（自定义 class / 嵌套卡片 / 内联 SVG），且工作树有用户未提交的 Phase 0–7 改动，大范围交叠风险高。本阶段采取**保守路径**：
   - 修 `SkeletonList` 硬编码色（唯一共享组件缺陷，影响所有 loading 态暗色表现）→ 见执行报告 §2.2。
   - 删除 FlashCardsHub / SmartQuiz 中已死亡但未清理的 `.context-bar` / `.context-clear` CSS（规范 §162 视觉清理）。
   - 逐 App 内联态收敛留作后续独立批次，本阶段不强行替换。
4. **不新建 `StateComponentV2` 等**（规范 §16）。

## 4. 上下文连续性现状（对应规范 §34-55）

| App | 是否真实支持 knowledgeId | 当前上下文行为 |
| --- | --- | --- |
| Flashcard | 是（API 按 knowledgeId 过滤 + 显示"正在学习：X"） | 经 `route.query.knowledgeId` 透传，MiniAppShell `:knowledge-name` 显示，可 clear |
| Quiz | 是（同上） | 同上 |
| Knowledge Graph | 是（聚焦节点 knowledgeId） | 读取并聚焦节点 |
| Mistake / Feynman / Cornell / Memory Palace / Coding / Mindmap / Notes / Drawing | 否（视图与 API 均无 knowledgeId 作用域） | 不显示"正在学习：X"（规范 §50 不伪造） |

**NextAction → Mini App 透传链已通**：`buildWeakAppAction`（`learning/action-priority.ts:150`）产出带 `knowledgeId` 的 `MINI_APP` 动作；`NextActionCard.doAction`（`components/workflow/NextActionCard.vue:151-160`）将其写入 `router.push({ path: app.route, query: { knowledgeId, knowledgeName } })`。Flashcard / Quiz 消费该 query（§2.3 验证）。
