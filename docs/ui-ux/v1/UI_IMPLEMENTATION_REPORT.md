# KnowFlow UI/UX V1.0 实施报告

- 日期：2026-10-28
- 分支：`main`（未新建分支 / 未 commit / 未 push / 未 merge）
- 范围：全站视觉标准（Batch A）→ 高频页面（Batch B/C）→ Mini App 应用化（Batch D）→ 顶栏快捷入口与回归（Batch E）
- 结论：共享基线、5 个 Mini App、顶栏快捷入口已 **DONE 并经浏览器验证**；逐页覆盖为 **PARTIAL**（明细见第 6 节与 `UI_TEST_REPORT.md`）

---

## 1. 统一了哪些视觉 Token 和组件

### 1.1 新增语义 Token（`frontend/src/style.css`，旧 `--kb-*` 全部保留）

| Token | 值 | 用途 |
|---|---|---|
| `--kb-icon-slot` | 18px（= `--kb-icon-lg`） | 导航 / 菜单图标固定容器 |
| `--kb-icon-slot-sm` | 16px（= `--kb-icon-md`） | 按钮 / 表单图标固定容器 |
| `--kb-nav-item-height` | 38px | 一级导航项高度 |
| `--kb-nav-item-height-sm` | 32px | 二级导航项高度 |
| `--kb-nav-item-gap` | 2px | 导航项垂直节奏 |
| `--kb-page-padding-x` / `-y` | 24px（≥1280：32/28；≤639：16） | 内容区统一留白 |
| `--kb-quick-action-size` | 36px | 顶栏快捷入口热区 |
| `--kb-quick-action-gap` | 4px | 顶栏快捷入口间距 |
| `--kb-content-reading` | 72ch | 长文 / 描述阅读宽度上限 |

字号阶梯沿用既有语义 Token，不再新增：`h1 28/600`、`h2 24/600`、`h3 20/600`、`h4 18/500`、`body-lg 16`、`body-md 14`、`body-sm 13`、`caption 12`。

### 1.2 基础组件（从根因修复，不在页面里加补丁）

| 组件 | 修改 |
|---|---|
| `components/ui/Icon.vue` | 所有分支（SVG / iconfont / SVG code / fallback）统一追加 `.kb-icon`：`inline-block + vertical-align:middle + flex:0 0 auto + line-height:1`；iconfont 用 `inline-flex` 居中 |
| `components/ui/PageHeader.vue` | 移除 `space-y-3` 与 tailwind 字号类，改语义类 `.kb-page-header__title(--lg)`：默认 28px/600，`size="md"` 24px；描述 14px + `72ch`；计数徽章统一 22px 高 |
| `components/layout/CSidebar.vue` | 一级项 38px / 14px / 500 / 圆角 `md` / gap 10px；二级项 32px / 13px / 图标 16px；导航 `gap 2px`；折叠 rail 34px 且全部保留 `title` + `aria-label` |
| `components/layout/CTopNav.vue` | 新增 `.topnav-action` 统一容器（36×36、18px 图标、统一 hover / `is-active`）；新增 `.topnav-quick` 快捷组与「更多快捷操作」面板；焦点环由 4px 收窄为 `--kb-studio-focus`（2px）+ 2px offset |
| `components/layout/CLayout.vue` | 内容区 padding 改由 `--kb-page-padding-x/y` 统一（原 16/24 混用） |
| `views/AppCenterPage.vue` | 间距由 10/14/16px 收口到 `--kb-space-3/4`；分组标题 `10px→12px`；付费精选标题 `16px→h4(18/500)` |
| `views/TaskManager.vue` | 容器高度 `calc(100vh - 104px)` → `nav + 2×page-padding`；圆角 → `--kb-radius-lg`；模式切换 13px→14px；列表标题 `<h1>`→`<h2>` |

未新增第二套组件体系，未引入第二个图标库。

---

## 2. 解决了哪些排版 / 对齐 / 留白问题（根因 → 修复）

| 页面 | 现象 | 根因 | 修复 | 复测 |
|---|---|---|---|---|
| `/chat` | AI 正文 H1/H2/H3 = 21.75 / 18.75 / 16.5px，字重 650 | `ChatMarkdownRenderer.vue` 用 `1.45em/1.25em/1.1em` 非阶梯值 | 改 `--kb-fs-h3 / h4 / body-lg / 1em`，字重 `--kb-fw-h3` | 实测 43 个标题仅 20/18/16px、≤600 ✅ |
| `/learning/pomodoro` | 页面标题 24px/700 | `PomodoroPage.vue` 页面内写死 `.page-title` | 改 `--kb-fs-h1/fw-h1/lh-h1` | 28px/600 ✅ |
| `/learning/center` | 「当前目标」18px 与「今日行动」20px 同级不同字号 | `AdaptiveLearningPanel.vue` 用 `--kb-fs-h3` | 改 `--kb-fs-h4` + `--kb-lh-h4` | 两者均 18px/600 ✅ |
| `/check-in` | `.kb-h1/.kb-h3/.kb-h4` 被页面局部覆盖（28/700、20、15） | `CheckIn.vue` scoped 覆盖同名全局语义类 | 删除覆盖；「连续打卡里程碑」由 `kb-h3` 改 `kb-h4` | h1 28/600，四个 H3 全 18px ✅ |
| `/tasks`、`/task-list` | 一页两个 `<h1>`（页面 28px + 「收件箱」20px） | `TaskManager.vue` 列表标题误用 `h1` | 改为 `h2`（视觉仍 20px） | 单页单 H1 ✅ |
| `/habits` | h1 28px/700 | 页面 scoped 覆盖 `.kb-h1` | 删除覆盖，走全局令牌 | 28px/600 ✅ |
| `/knowledge` | h1 28px/700 | `.greeting-title` 写死 `font-weight:700` | 改 `--kb-fw-h1` | 28px/600 ✅ |
| 全站图标 | 有的偏上 / 偏下、菜单高度不一 | 各页 `width/height` 与基线各自为政 | `Icon` 统一 `.kb-icon` + 侧栏固定 18/16px 容器 + 38/32px 行高 | 顶栏图标 iconDy=0，侧栏 38/32px ✅ |
| 全站留白 | 顶部留白过高、内容区左右不一致 | `CLayout` 与 `pc-studio.css` 各自写 padding | 收口到 `--kb-page-padding-x/y`（24 → 宽屏 32 / 小屏 16） | `main` 宽度 1216@1440，无横向溢出 ✅ |

`/profile` 的昵称 H1 保留 24px（hero 标题 + 等级徽章同排），属于任务书允许的 24–28px 区间，未强行拉到 28px。

---

## 3. 新增 Mini App（Phase 3）

全部走既有 `apps/registry.ts` + `stores/apps.ts`，未新建 Registry / Sidebar / 状态系统。

| key | 名称 | 路由 | 分类 | 图标 | 默认安装 | 导航 |
|---|---|---|---|---|---|---|
| `tasks` | 任务 | `/tasks` | PRODUCTIVITY（效率） | `list-todo` | 否 | 工具 order 2 |
| `check-in` | 签到 | `/check-in` | 效率 | `calendar-check` | 否 | 工具 order 3 |
| `habits` | 习惯打卡 | `/habits` | 效率 | `check-check` | 否 | 工具 order 4 |
| `calendar` | 日历 | `/calendar` | 效率 | `calendar` | 否 | 工具 order 5 |
| `today-plan` | 今日计划 | `/learning/today-plan` | 效率 | `list-checks` | 否 | 工具 order 6 |

配套修改：

- `apps/types.ts`：`AppCategory` 增加 `PRODUCTIVITY`。
- `apps/icon-registry.ts`：5 个 `APP_ICONS` 映射（全部为 Icon 已有名称）。
- `apps/registry.ts`：`CATEGORY_ORDER` 插入 `PRODUCTIVITY`（位于 FOCUS 与 CODING 之间）、`CATEGORY_LABELS.PRODUCTIVITY='效率'`、`APP_NAV_META` 工具区 order 2–6。
- `backend/.../OfficialAppRegistry.java`：5 个 key 登记为 `ACTIVE`（不登记会被后端 400 INVALID_APP 拒绝）。
- `apps/__tests__/app-management.spec.ts`：tools 工作区允许分类追加 `PRODUCTIVITY`（原断言只允许 FOCUS）。
- 未改 `DEFAULT_APP_KEYS`、未改任何安装语义；停用只置 `enabled=false`，不删除学习数据。

---

## 4. 左侧导航具体变化

- 动态导航仍由 `resolveNavigation()`（Registry × UserApp）推导，「工具」组仅在有已启用子项时出现。
- 一级项：38px 高、14px/500、图标 18px、圆角 `md`、间距 2px；二级项：32px 高、13px、图标 16px，左侧 1px 竖线 + 8px 缩进。
- 折叠态：68px 图标 rail，每个 App 仍显示图标并保留 `title` + `aria-label`；点父级标题直达该工作区首页（不是「死按钮」）。
- 新增的 5 个 App 启用后按 `sortOrder` 出现在「工具」组，支持应用中心上移 / 下移 / 拖拽排序；排序写回 `UserApp.sortOrder`，刷新与重新登录后由服务端恢复。

---

## 5. 顶栏快捷入口（Phase 4）

- 位置：搜索 / 组件中心 / AI 助手之后，通知与头像之前；全部为 36×36、18px 图标、同一水平中线。
- `quickActions`（6 项，全部真实路由）：任务 `/tasks`、签到 `/check-in`、习惯打卡 `/habits`、今日计划 `/learning/today-plan`、日历 `/calendar`、专注 `/learning/pomodoro`。
- ≥1280px 显示前 3 个（任务 / 签到 / 习惯打卡）+「更多快捷操作」；<1280px 只保留「更多」。
- 面板 `role="menu"` + `role="menuitem"`，6 项中文名称与图标；`aria-haspopup/aria-expanded` 正确；与通知 / 用户菜单互斥。
- 当前路由 `is-active`（`aria-current="page"`）；无任何编造的待办数 / 连续天数；点击只做路由跳转，不自动签到、不写打卡记录。

---

## 6. 页面覆盖清单（执行与防遗漏）

状态：DONE = 本轮真实修改共享基线或页面并浏览器验收；PARTIAL = 共享基线已生效但未逐页实拍或存在保留项。

| 路由 | 布局 | 主要组件 | 登录 | 主要问题 | 状态 |
|---|---|---|---|---|---|
| `/` | C | Home + pc-studio | 否 | 页头 24px、留白 | DONE（1440/1280/1024/768/390） |
| `/learning/center` | C | AdaptiveLearningPanel | 是 | 区块标题 18/20 混用 | DONE |
| `/knowledge` | C | KnowledgeHome | 否 | h1 700 字重 | DONE |
| `/apps` | C | AppCenterPage + AppCard | 是 | 间距 10/14 混用、分类缺效率 | DONE（含深色） |
| `/search` | C | 搜索页 | 是 | — | DONE（实拍 1440） |
| `/notes` | C | Notes 列表 | 是 | 密集工具条 13px | PARTIAL |
| `/chat` | C（沉浸） | Chat + ChatMarkdownRenderer | 是 | 负边距依赖 24px、正文标题阶梯 | DONE（含深色） |
| `/tasks`、`/task-list` | C | TaskManager | 是 | 双 H1、13px 模式切换、高度写死 104px | DONE |
| `/check-in` | C | CheckIn | 是 | 语义类局部覆盖 | DONE |
| `/habits` | C | Habits | 是 | h1 700 | DONE |
| `/calendar` | C | 日历 | 是 | — | DONE（实拍 1440） |
| `/learning/pomodoro` | C | PomodoroPage | 是 | h1 24/700 | DONE |
| `/learning/paths` | C | 学习路径 | 是 | 工具条 12px | PARTIAL |
| `/interview` | C | 模拟面试 | 是 | — | DONE（实拍 1440） |
| `/learning/report` | C | 学习报告 | 是 | — | DONE（实拍 1440） |
| `/profile` | C | Profile | 是 | h1 24px（hero，保留） | DONE |
| `/admin/overview` | B | 管理后台 | ADMIN | 独立 B 端布局 | DONE（实拍 1440） |
| `/knowledge/reader`、`/knowledge/new`、笔记编辑器 | C（fullscreen） | 沉浸页 | 是 | 未逐页实拍 | PARTIAL |
| 社区 / 代码练习 / 知识图谱 / 知识工作台 / 管理后台其余子页 / 今日计划 | 多种 | — | 是 | 未逐页实拍（共享基线已生效） | PARTIAL |

---

## 7. 明确未做（避免误报）

- 未生成「修改前」截图：工作树已包含本轮改动且按规则禁止 `stash/checkout`，只能提供修改后多视口证据（`UI_TEST_REPORT.md` §6 说明）。
- 未修改任何业务算法 / 数据流：FSRS、Evidence、MasteryEngine、NextAction、测验评分、Tutor 历史、MySQL 迁移、F11 Runner 均未触碰。
- 未做用户级快捷栏自定义（固定 3 + 更多，复用现有偏好体系之前不引入第二套菜单后端）。
- 未把全部页面都改造成 Mini App；学习中心 / 知识首页 / 登录 / 设置保持原角色。

---

## 8. 2026-10-28 增补：任务中心 + 今日计划深度重构

范围仅 `/tasks`（`TaskManager.vue` + `TaskRow.vue` + `TaskKanban.vue`）与 `/learning/today-plan`（`TodayPlan.vue`）。未新建分支 / 未 commit / 未 push / 未 merge，未改后端、API、Schema 与业务算法。

### 8.1 任务中心

| 项 | 改前 | 改后 |
|---|---|---|
| 页头 | 整页被一层大卡片包裹，标题/描述/「查看今日计划」同行但无主操作 | 页头移出卡片：`任务中心`(28/600) + 描述(14) + 「今日计划」轻按钮 + `新建任务` 主按钮；内容区只保留一层表面 |
| 视图切换 | 独立通栏 + 底部分割线 | 第二行工具栏，列表/四象限/看板共用；`30px` 高分段控件；列表模式同行右侧 `280×40` 搜索 |
| 左分类栏 | 248px | 232px，行高 32px、选中浅蓝 `--kb-primary-subtle`、计数胶囊 18px、分组间距 `space-4` |
| 任务行 | 卡片感、日期随标签漂移、操作区常驻 | `46px` 轻分割线行：固定 Checkbox → 标题(单行省略且 `title` 可看全文) → 状态浅色标签 → 日期/耗时右侧固定 → 操作区 hover/focus 出现；拖拽手柄默认 `opacity:0` |
| 状态标签 | 无统一表达 | 待开始 / 进行中 / 已完成 / 已延期（仅由 `status` + `dueDate` 推导，未新造后端状态） |
| 新建任务 | 仅底部快捷输入 | 页头主按钮切回可新建视图并聚焦既有快捷输入（`focusQuickAdd`），未建第二套表单 |
| 滚动 | 多层滚动嵌套 | 分类栏与主列表各自单滚动；列表表面 `overflow:hidden` 只裁容器不裁内容 |

### 8.2 今日计划

| 项 | 改前 | 改后 |
|---|---|---|
| 页头 | 左侧标题 + 右侧日期/生成下周/导出并列 | 左侧标题 + 日期摘要；右侧 日期 chip + `查看全部任务` + 次级「生成下周计划 / 导出日历」；窄屏操作整行换行不裁切 |
| 完成度 | 大号百分比与文案被挤压换行 | 进度卡：`今日完成度` + 百分比(`--kb-fs-h1`) + `已完成 x / y 项`(`nowrap`) + 进度条；数值全部来自 `getTodayPlan()` |
| 布局 | 上午/下午/晚间三列等高，短列大片留白 | `≥1280` 左右 `2fr / minmax(280px,1fr)`：左侧时间线（各时段内容自适应、不等高）+ 右侧今日进度 / 下一项待办；`≤1100` 单列 + 概览双列；`≤768` 纯单列 |
| 时段图标 | `🌅 上午 🌅` 重复 emoji | `Icon` 语义图标（morning→coffee、afternoon→sun、evening→moon），每组一个，18px 标题 + 13px 时间 |
| 任务配色 | 首项整块高饱和绿底 | 普通行白/透明 + 分割线；当前项 `--kb-primary-subtle` + 左侧 3px 强调条；完成态用浅绿标签/删除线，不靠颜色单独表意 |
| 任务卡 | 时间信息重复、标签挤标题 | 单行结构：Checkbox → 标题(可换行 `overflow-wrap:anywhere`) + 类型标签(`学习任务/待办/习惯`) → 时间/耗时；更多操作沿用原组件，未误触完成 |

### 8.3 顺带修掉的真实缺陷

`TaskKanban.vue` 的局部类 `.kb-progress*` 与全局工具类 [`.kb-progress`](file:///Users/beiluo/Documents/alProject/qoderProject/knowflow/frontend/src/styles/ui-tokens.css#L252-L258)（`height:6px; overflow:hidden`）同名，导致看板进度行被压成 6px、右侧「3 / 8 已完成」竖排裁切。改为局部前缀 `kbn-progress*`，并收敛为右侧 320px 内的紧凑进度条。该缺陷在 HEAD 基线截图（`task-plan-before/task-center-board-1440x900.png`）中可复现，非本轮 UI 改版引入。

### 8.4 未新增共享组件

两页任务展示结构不同（任务中心需拖拽/标签/子任务，今日计划需时段/耗时/计划来源），强行抽象会引入条件分支。本轮只复用 `Checkbox / Icon / Button / PageHeader / Progress / StateView / TaskCompletionAction`。统计口径维持各自原有语义（收件箱=全部未完成、今天=当天未完成、今日计划=已排入计划项），未在 UI 层强行合并数量。
