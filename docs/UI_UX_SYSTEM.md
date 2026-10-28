# UI_UX_SYSTEM.md — KnowFlow UI/UX 设计系统

> 基于 2026-09-10 真实代码扫描。设计令牌来自 [styles/ui-tokens.css](../frontend/src/styles/ui-tokens.css) 和 [style.css](../frontend/src/style.css)。

> **现行规范 · 2026-10-25**：输入、字体、菜单、弹窗、图标等专项开发规范以 [UI_DESIGN_SYSTEM_V2.md 第0节](UI_DESIGN_SYSTEM_V2.md#0-当前有效开发规范2026-10-24) 及正文为准。本文维护产品哲学、导航与通用原则；旧报告/审计不覆盖现行条款。Token以源码为准，不把未迁移页面描述为已完成。
>
> **普通输入的强制基线**：文本、数字、搜索、Textarea、普通Select统一浅灰表面；默认无明显边框，鼠标/键盘聚焦均不出现蓝色或中性灰border/outline/ring/外圈阴影。组合输入外壳承载表面，内部透明；图标/文字间隔通常8–12px（顶部搜索12px），同排字段复用FormField/kb-form-row对齐。按钮/菜单/Checkbox/Radio保留各自键盘反馈，forced-colors保留系统指示，禁止全局取消焦点。

---

## 一、设计哲学

**KnowFlow 不是**：普通 Dashboard / ChatGPT Clone / 文件管理器 / 游戏化学习网站

**KnowFlow 是**：极简界面 + 强学习闭环

**用户进入任何页面 3 秒内必须知道**：

1. 我在哪里？
2. 我在做什么？
3. 为什么做？
4. 下一步是什么？

**基调**：专业、克制、安静、轻量、现代

**避免**：过度卡片化、过度渐变、过度圆角、过多彩色图标、大量重复按钮、Dashboard 模板感、AI 炫技感

---

## 二、设计令牌（`--kb-*` CSS 变量）

所有颜色、字号、间距、圆角**必须使用 `--kb-*` CSS 变量**，禁止写死十六进制色值或像素值。

### 2.1 颜色系统

| 变量 | 值 | 用途 |
|-----|-----|------|
| `--kb-primary` | `#3B6FE0` | 品牌主蓝：按钮、链接、激活态、强调边框 |
| `--kb-primary-foreground` | `#FFFFFF` | 主色背景上的文字 |
| `--kb-highlight` | `#FF6B35` | 高光暖橘：成就解锁、连续打卡火焰、AI 推荐徽章（仅小面积强调） |
| `--kb-highlight-soft` | `rgba(255,107,53,0.10)` | 高光软背景 |
| `--kb-highlight-border` | `rgba(255,107,53,0.30)` | 高光边框 |
| `--kb-accent` | `#10B981` | 成功/通过绿 |
| `--kb-destructive` | `#EF4444` | 危险/错误红 |
| `--kb-warning` | `#F59E0B` | 警告黄 |
| `--kb-background` | `#F7F8FA` | 页面背景 |
| `--kb-card` | `#FFFFFF` | 卡片/容器背景 |
| `--kb-sidebar` | `#F0F2F5` | 侧边栏背景 |
| `--kb-muted` | `#E8ECF1` | 禁用态/分隔条 |
| `--kb-border` | `#E2E6EC` | 边框/分割线 |
| `--kb-foreground` | `#1A1D23` | 主要文字 |
| `--kb-muted-foreground` | `#6B7280` | 次要/辅助文字 |

### 2.2 语义状态色

| 变量 | 用途 |
|------|------|
| `--kb-state-success` | 成功状态 |
| `--kb-state-warning` | 警告状态 |
| `--kb-state-error` | 错误状态 |
| `--kb-state-info` | 信息状态 |

### 2.3 下拉阴影与主色软背景

| 变量 | 用途 |
|------|------|
| `--shadow-dropdown-2` | 二级下拉阴影 |
| `--shadow-dropdown-3` | 三级下拉阴影 |
| `--kb-primary-soft` | 主色软背景 |
| `--kb-dropdown-radius` | 下拉圆角 |
| `--kb-dropdown-padding-y` | 下拉内边距 Y |
| `--kb-dropdown-item-padding` | 下拉项内边距 |
| `--kb-dropdown-group-padding` | 下拉分组内边距 |

### 2.4 圆角

| 变量 | 用途 |
|------|------|
| `--kb-radius-xs` | 超小圆角 |
| `--kb-radius-sm` | 小圆角 |
| `--kb-radius-md` | 中圆角 |
| `--kb-radius-lg` | 大圆角 |

### 2.5 历史命名别名

以下变量是历史命名的别名，指向上述标准变量（确保旧代码兼容）：

`--kb-text` → `--kb-foreground`、`--kb-muted-text` → `--kb-muted-foreground`、`--kb-primary-light` → `--kb-primary-soft` 等共 18 个。

---

## 三、字体系统

| 字体 | 用途 | CDN |
|------|------|-----|
| Source Han Serif SC / Noto Serif SC | 显式展示标题少量使用，不默认用于H1/H2 | fonts.loli.net |
| Noto Sans SC | UI、导航、按钮、表单、聊天正文及普通页面标题 | fonts.loli.net |
| JetBrains Mono | 代码字体 | fonts.loli.net |

> 使用国内 CDN 镜像 `fonts.loli.net` 替代 `fonts.googleapis.com`

---

## 四、字号与图标尺寸阶梯

### 4.1 当前排版阶梯

字号、行高和字重引用style.css现有Token及其别名，根字号保持14px；不能用改根字号统一放大页面。默认Sans，Serif仅显式展示场景，Mono用于代码/命令/技术标识。

| 角色 | 字号 / 行高 | 字重 / 字体 |
| --- | --- | --- |
| Display | 32 / 40 | bold，显式kb-font-display可用Serif |
| H1 / 页面标题 | 28 / 36 | 600，Sans |
| H2 | 24 / 32 | 600，Sans |
| H3 | 20 / 28 | 600，Sans |
| Section / H4 | 18 / 26 | 500，Sans |
| 阅读正文 | 16 / 28 | regular，Sans |
| 普通正文 / 输入 | 14 / 约22 | regular，Sans |
| Label / UI Small | 13 / 约20 | 按角色medium或regular |
| Caption | 12 / 18 | regular，不强制全大写 |
| Meta | 11 / 16 | medium，克制使用 |

完整字体角色、现有别名与迁移边界见[专项规范](UI_DESIGN_SYSTEM_V2.md#3-字体排版与图标)。章节阅读等专用排版按已有组件Token，不机械覆盖。

### 4.2 图标阶梯（8 级）

| 尺寸 | 用途 |
|------|------|
| xxs | 辅助标注 |
| xs | 行内 |
| sm | 紧凑行 |
| md | 默认 |
| lg | 卡片 |
| xl | 标题 |
| 2xl | Hero |
| 3xl | 特殊强调 |

---

## 五、UI 组件库

### 5.1 基础组件（`components/ui/`）

| 组件 | 文件 | 说明 |
|------|------|------|
| Icon | Icon.vue | 统一图标组件 |
| Button | Button.vue | Primary / Secondary / Ghost / Icon |
| Badge | Badge.vue | 状态标签 |
| Card | Card.vue | 卡片容器 |
| Pagination | Pagination.vue | 分页 |
| EmptyState | EmptyState.vue | 空态（状态+原因+下一步） |
| SkeletonList | SkeletonList.vue | 骨架屏 |
| PageHeader | PageHeader.vue | 页头 |
| ContextMenu | ContextMenu.vue | 已有通用动作菜单；不是 Select/listbox |
| AIComposer | ../ai/AIComposer.vue | 已接入聊天与私聊的受控输入；视觉待完善项见 V2 审计 |
| Input | Input.vue | 文本字段，当前属性/错误关联缺项见 V2 审计 |
| StatCard | StatCard.vue | 统计卡片 |
| StateView | StateView.vue | 状态视图容器 |
| ErrorState | ErrorState.vue | 错误态（发生了什么+解决方案+重试） |
| Textarea | Textarea.vue | 文本域 |
| Checkbox | Checkbox.vue | 20px正方形/3px微圆角/8px文字间距；蓝底白Check/Minus，无灰影，键盘保留outline；完整规则见UI V2的8.1节 |
| Select | Select.vue | 下拉选择 |
| CommandPalette | CommandPalette.vue | 命令面板 |

### 5.2 全局类

| 类 | 用途 |
|----|------|
| `.kb-stat-card` / `.kb-stat-icon` / `.kb-stat-value` / `.kb-stat-unit` / `.kb-stat-label` | 统计卡片 |
| `.kb-state-*`（7个 + fade-in 动画） | 状态标识 |
| `.kb-textarea` / `.kb-checkbox` / `.kb-select-*` | 表单元素 |
| `.kb-select` / `.kb-select-sm` | 原生 select |
| `.kb-filter-btn` / `.kb-filter-group` | 筛选按钮组 |
| `.kb-dropdown` | 自定义弹出下拉 |

---

## 六、页面状态规范

所有页面最终统一为 4 种状态：

| 状态 | 要求 | 组件 |
|------|------|------|
| Loading | SkeletonList 或骨架屏，不要白屏 | `SkeletonList` |
| Ready | 正常内容 | — |
| Empty | 状态 + 原因 + 下一步 | `EmptyState` |
| Error | 发生了什么 + 解决方案 + 重试 | `ErrorState` |

**禁止**：白屏、`Loading...` 裸文字、`暂无数据` 无引导、按钮点击无反馈。

### 使用方式

```vue
<StateView :state="state" :error="error">
  <template #loading><SkeletonList /></template>
  <template #empty><EmptyState title="还没有学习路径" reason="创建你的第一条路径开始学习" action="新建路径" @action="..." /></template>
  <template #error><ErrorState message="加载失败" solution="检查网络后重试" @retry="..." /></template>
  <template #default><!-- Ready 内容 --></template>
</StateView>
```

---

## 七、交互规范

### 7.1 禁止浏览器原生对话框

**禁止**：`window.alert()` / `window.confirm()` / `window.prompt()`

**必须使用** [utils/toast.ts](../frontend/src/utils/toast.ts)：

| 函数 | 用途 |
|------|------|
| `notify(message, type)` | Toast 提示 |
| `confirmDialog(options)` | 确认弹窗 |
| `promptDialog(options)` | 带输入框的确认弹窗 |
| `getApiError(e)` | 统一 API 错误提取 |

### 7.2 按钮规范

按钮的尺寸/字体/间距/状态以[专项4.1节](UI_DESIGN_SYSTEM_V2.md#41-button--iconbutton-统一合同v3)为唯一标准：32/38/44px、默认14/20/500、6px圆角、图文8px、无装饰阴影；确认/AI发送保留专用Token。现有实现的差异见专项，不将规范更新写成全站改造完成。


- 每个区域尽量只有 1 个 Primary CTA
- 按钮类型：Primary / Secondary / Ghost / Icon
- 提交按钮必须有 loading 态，防止连点

### 7.3 下拉选择与筛选

统一使用全局类（确保颜色主题、高度、圆角、内边距一致）：

| 场景 | 全局类 |
|------|--------|
| 原生 select | `.kb-select` / `.kb-select-sm` |
| 筛选按钮组 | `.kb-filter-btn` / `.kb-filter-group` |
| 自定义弹出下拉 | `.kb-dropdown` |

下拉选项文本支持换行以避免截断。

---

## 八、导航结构

### 8.1 侧边栏（5 个顶级入口）

```
首页 / 学习 / 知识 / AI / 应用
```

**禁止增加一级菜单**：Evidence / Mastery / Diagnosis / Strategy / Feynman / First Principles / Scenario 等属于 Core / Skill / App，不是 Sidebar 一级导航。

### 8.2 导航数据源

单一来源：[config/navigation.ts](../frontend/src/config/navigation.ts)

App 导航分区元数据：[apps/registry.ts](../frontend/src/apps/registry.ts) `APP_NAV_META`

---

## 九、代码块渲染规范

| 元素 | 规格 |
|------|------|
| 背景色 | `#1a1d23` |
| 圆角边框 | 10px |
| 左侧行号 | 独立深色背景、不可选中 |
| 代码字体 | JetBrains Mono 等宽 |
| 语法高亮 | github-dark 配色 |
| 复制按钮 | 右上角，点击后显示 2 秒「已复制」绿色提示 |

---

## 十、Markdown 渲染规范

- `markdown.ts` 中设置 `breaks: true` 以支持单行换行转 `<br>`
- 需通过 `normalizeNewlines()` 函数将数据库存储的字面量 `\n` 还原为真实换行符
- 文档详情页样式必须使用全局 `--kb-*` CSS 变量
- TOC 锚点匹配：采用 `heading-${seq}` 序号作为 h2/h3 元素 id

---

## 十一、TypeScript 规范

- Vue 的 `:class` 对象语法中，含连字符的 key（如 `send-error`）必须用引号包裹成字符串字面量
- TypeScript 类型导入必须使用 `type` 前缀：Vue 类型（如 `InjectionKey`, `Ref`）需通过 `import type` 导入
- 禁止 `catch(e: any)`，统一用 `catch(e: unknown)` + `getApiError`
- Mermaid 的 `themeVariables` 不支持 CSS 变量（如 `var(--kb-muted)`），需使用实际十六进制颜色值

---

## 章节阅读 V2（2026-10-11）

普通用户 PC 章节复用 `ChapterLearn`。通过 `--kb-chapter-*` 语义令牌控制 50rem 阅读宽度、70rem 内容宽度、16rem 目录、2rem 栏间距；正文 1rem/1.8，H1/H2/H3 为 2/1.5/1.25rem，代码 .875rem。继承全局字体、前景/次级文字与蓝白颜色，不另外建立设计系统。

标题、摘要、时长/难度/重要度/完成状态、学习目标各只表达一种信息；目标不打完成勾。正文 H2/H3 自动生成目录，点击定位与滚动高亮，长题名截断但保留 title；目录和工具有界滚动，1024 折叠到顶部。代码保留语言/高亮/复制反馈与局部横向滚动，禁止将任意源码在已登录同源页执行。

进度是后端用户路径完成百分比，不是掌握度。读取现有 `displayPathProgress`，未知不显示虚假 0%。工具只连接实际消费上下文的页面：闪卡经 action-contract 带章 ID，题目/实验使用真实资源与本章锚点。底部下一章主操作、手动完成次操作、收藏/笔记辅助；正文预留底栏空间。

## 十二、相关文档

- [AGENTS.md](../AGENTS.md) — Agent 工作守则
- [VIBE_CONTEXT.md](../VIBE_CONTEXT.md) — Vibe Coding 上下文
- [docs/PROJECT_ARCHITECTURE.md](PROJECT_ARCHITECTURE.md) — 项目架构
- [docs/DEVELOPMENT_PROTOCOL.md](DEVELOPMENT_PROTOCOL.md) — 开发协议
- [docs/AGENT_HANDOFF.md](AGENT_HANDOFF.md) — Agent 交接
- [docs/前端规范/UI设计系统.md](前端规范/UI设计系统.md) — 原始设计系统文档

## 2026-10-11 · Chapter Reading V3

章节阅读页的参考图呈现由 `styles/chapter-reading-v3.css` 维护；CLayout 仅在 ChapterLearn 路由增加 `chapter-shell`。50px 顶栏、232px 导航、324px 辅助栏和文章字体尺寸集中在章节语义变量，不改变其他页面壳层或 Registry 分类。1024px 桌面改为可展开目录，不建立第二套全局 Header。

目录从真实 H2/H3 生成、可折叠子目录；学习目标不展示已达成勾选；工具数量消费真实API/训练资源，未知和零资源不启用训练定位。Java阅读保留安全渲染、语法高亮、行号、复制和横向滚动，无任意代码执行。最近学习来源为认证用户最近完成事件，不代表所有浏览历史。验收证据见 `reports/CHAPTER_LEARNING_UI_V3.md`。

## 2026-10-11 · Chapter Reading V4

在 V3 三栏上统一正文16/1.8、H2 24/H3 20/H4 17、代码14；从正文字号派生语义尺寸，避免全局14px根字号导致rem偏小。allowlist 提示块、原生折叠、自测只读清单、真实题目入口与选择题布局沿用现有tokens。声明式题目必须验证本章关系，不允许任意组件/HTML/代码执行。实际选择题反馈来自原Quiz接口，不混淆静态阅读与学习成绩。证据见 `reports/CHAPTER_LEARNING_V4.md`。

## 文本输入视觉与聚焦（现行，2026-10-24）

共享普通输入使用现有`--kb-field-*`浅灰表面与圆角，默认无明显边框；普通focus/focus-visible不画蓝色或灰色outline、ring、边框或外圈阴影。表单字段默认44px高，Textarea保留适当高度和滚动；label/control/help/error对齐，原生数字和验证语义不变。输入光标、选择、Tab与快捷键保持，菜单和按钮保留自身键盘反馈，forced-colors保留系统Highlight。错误有文字和ARIA，不用重新加聚焦环表达错误。

2026-10-11的“1px输入边框+2px外环”已失效；[原修复报告](reports/INPUT_FOCUS_FIX.md)仅为历史证据。当前事实见[最终输入报告](borderless-forms/no-focus-frame/REPORT.md)。

## 2026-10-11 · Chapter Reading V5

学习目标为编号只读清单，无独立完成记录则不显示可交互勾选。技术文章默认左对齐、不缩进：正文16px/1.8、段落16px；H2间距40/16px，H3间距28/12px，代码20px；列表恢复语义编号。章节显式启用 H2/H3 编号：手工编号保留、缺失编号由标题解析生成，TOC与正文同源，语义锚点去重；普通 Markdown 默认旧行为不变。旧 ordinal 锚点描述不再适用于新版章节。详见 `reports/CHAPTER_READING_V5.md`。

## 2026-10-14 补充：取消统一页头返回按钮

按用户明确的新设计要求，`MiniAppShell` 与 `PageHeader` 不再展示统一的「← 返回」按钮，标题直接对齐内容区起始位置。该规则覆盖所有使用公共页头的普通用户 PC 页面，包括考试中心、算法题库等；旧页面传入的 `back` / `backLabel` 参数仅保留兼容，不再影响渲染。

保留侧栏、面包屑、上下文链接与具体业务操作（编辑取消、退出考试、继续练习等）。不因移除页头按钮而改动路由、学习上下文盖章或业务状态。此前关于公共页头必须提供统一返回按钮的说明以本条用户确认设计为准。

## 2026-10-14 补充：全站右侧区域铺满

用户要求以考试中心为参照，所有页面占满菜单右侧可用区域，不保留居中限宽造成的大块左右空白。CLayout统一使用一个width:100%/max-width:none/min-width:0内容区，PC统一使用--kb-space-6（24px）安全边距，小屏--kb-space-4；sidebar展开/收起继续由既有状态决定。

页面外层、学习首页、个人工具和工作台取消二次max-width、margin:auto和重复水平padding。Notes阅读详情也跟随可用工作区宽度；弹窗、画布内部目录栏、正文阅读列、搜索输入与提示文字保留语义宽度，避免全局无差别拉伸控件。B端常规外壳取消1400px居中限制，现有fullscreen编辑布局继续保留。此补充优先于历史“全站最大内容宽度”条款，不改路由权限、Store、API、数据或学习算法。

## 2026-10-23 Select 与浮层 V2

普通表单/筛选优先复用 `components/ui/Select.vue` 的 Field/Pill/Subtle 变体；搜索、多选、分组通过同一组件配置，复杂长列表用 `SelectDialog.vue`，共享 `SelectChoices.vue`。受控值、值类型与保存时机不因样式迁移改变。操作型 ContextMenu 保留独立 menu 语义；树选择继续保留真实层级。新增 Token 仍集中于 ui-tokens.css，不建立第二套权威配色。

[全站修改前审计](select-v2/AUDIT.md)、[接口、迁移清单、测试与未完成项](select-v2/REPORT.md)、[设计系统规范](UI_DESIGN_SYSTEM_V2.md)。这是分批实施，不代表所有原生 select 已迁移。

## 2026-10-23 Select / Cascader V3

Select 默认9项自动搜索，可由 searchable/searchThreshold 覆盖；可选 loadOptions + AbortSignal 提供防抖、取消与竞态保护。CategoryTreeSelect 保留原CategoryVO/number|undefined合同，转交 CascaderSelect：桌面悬停级联，父选择和展开独立；小屏单列进入/返回。平面选择与级联共用几何与既有Token，操作菜单仍保留menu语义。

[本轮审计](select-v3/AUDIT.md)、[接口、45处平面迁移、10处级联入口及验证边界](select-v3/REPORT.md)。本节为实施关联，不代表剩余原生选择器均已迁移，也不绕过已有ADMIN接口权限。

### 2026-10-23 错题筛选与平面 Select 状态校准

平面 Select 的选中项使用 Check 和 aria-selected，不使用字重反馈，默认白色/透明背景；浅灰背景仅用于 hover，键盘活动项使用浅灰背景与独立焦点轮廓。该约定不改变 ContextMenu 或 Cascader 的选中设计。筛选行的紧凑宽度由页面控制；Select 默认 Field 全宽保持，可通过可选 panelWidth 独立设置浮层宽度并受视口约束。实施与验证见[精准修复报告](mistakes-filter-fix/REPORT.md)。

### 2026-10-23 可搜索 Select 精准校准

公共 Select 的搜索区使用42px尺寸Token，input取消继承的padding与内框，搜索区的浅灰分隔线在聚焦前后保持一致，不变蓝、不加粗；输入光标标识编辑位置，强制颜色模式保留系统轮廓。单行选项默认42px，Selected仅Check，不用背景或字重混充焦点；Hover/键盘活动使用浅灰，键盘另有轮廓。默认搜索浮层宽度限定300–380px（再受视口限制），显式panelWidth优先；知识库调用340px。搜索面板最高352px且按视口缩小，搜索固定、列表独立滚动。普通非搜索宽度策略保持；长名称省略并提供完整title。实施事实、测试及截图见[专项报告](searchable-select-fix/REPORT.md)，不将旧全站迁移目标视为全部完成。

## 2026-10-24 · Input Focus V2（本轮明确要求，补充旧记录）

> 本节为最新文本焦点规则，覆盖前述“普通输入键盘聚焦也不显示外框”的视觉条款；旧记录保留为历史。正常表面、字体、尺寸与FormField布局仍复用现有tokens，不改变控件业务语义。

**KnowFlow 不使用高饱和、粗线条、直角矩形的文本输入焦点框。带外部容器的输入区域只由外层绘制焦点反馈，禁止内部 textarea 再绘制独立蓝框。鼠标聚焦保持克制，键盘聚焦提供可见但柔和的单层反馈。**

- 文本字段默认1px中性边界、原圆角和浅灰表面，无外发光；聊天容器保留其既有圆角。鼠标聚焦不绘制明显outline/ring，也不改变尺寸。
- 使用`--kb-field-border`、`--kb-field-focus-width/color/shadow`，不使用业务页高饱和`--kb-ring`画文本焦点。现有按钮、链接、菜单项、Checkbox/Radio继续保留各自键盘规则。
- `.kb-field`和`.kb-input-surface`是组合输入的焦点绘制所有者，内部输入不重复画outline/阴影。独立普通字段才自行绘制。`html[data-kb-input-modality=keyboard]`只负责视觉来源标记；监听不preventDefault、不更改按键/输入/IME/发送事件。文本输入鼠标点击也可能匹配focus-visible，所以不能单凭该伪类区分鼠标与Tab。
- 错误先于普通焦点：错误边界/文字与ARIA保留，键盘用同一所有者的错误色反馈，不叠蓝环。disabled无活动环。
- forced-colors使用系统Highlight轮廓，由同一个所有者绘制，内部不重复；不依赖浅阴影。不得全局清除所有交互控件焦点。
- 现有代码编辑器等特殊控件不机械套用普通输入重置。`:has()`沿用项目已有现代浏览器能力，不新增UI库；本轮Chrome实测，其他浏览器仍需专项兼容验收。

真实根因、全站消费者排查、前后图、Computed Style与验证见[Focus V2执行报告](ui-v2/INPUT_FOCUS_V2_EXECUTION.md)。

## 文档治理入口（2026-10-24）

产品哲学由本文维护，控件/字体/聚焦外观只执行[UI V2现行规则](UI_DESIGN_SYSTEM_V2.md#0-当前有效开发规范2026-10-24)。[前端规范旧UI设计系统](前端规范/UI设计系统.md)已SUPERSEDED，不再同时声明权威。架构/需求/状态/路线职责见[文档中心](README.md)，历史报告与源码实现/实际验证分开。

2026-10-25：公共Button/Input及开发组件中心实施事实见[展示中心报告](component-playground/REPORT.md)；外观唯一规则仍为UI_DESIGN_SYSTEM_V2正文，不能由展示挂载成功推定全站合规。
