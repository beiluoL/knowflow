# KnowFlow UI/UX 设计系统（V3.0 统一组件规范）

> **KnowFlow Modern AI Workspace** · 当前规范更新于 2026-10-25 · 历史审计不代表全站实现完成。
>
> 本文是 [UI_UX_SYSTEM.md](UI_UX_SYSTEM.md) 的输入、控件和浮层专项规范。原文继续承担产品哲学、学习闭环、导航及通用交互原则；本专项范围的旧外观条款以本文目标为准，开发时先执行第0节和正文现行条款；第1节审计表、旧实施记录及其报告仅保存历史事实，不覆盖现行规范。Token 的实际值以源码为准；历史「拟新增」状态须核对源码，不能据此重复定义变量。

## 0. 当前有效开发规范（2026-10-24）

2026-10-25 将用户确认的 V3.0 组件视觉与布局标准合并到本文件，保留路径和第0节锚点兼容已有引用，不另建第二份权威设计系统。第3、4、5、8、9、13节共同组成现行合同。**规范写入不等于全站改造或运行验收完成。**

**普通输入控件保留统一浅灰表面，但聚焦不出现蓝色或中性灰外框。** 此条取代历史的1px输入边框、2px灰色键盘环、蓝色软环及透明输入表面方案；不再通过往文末追加相反条款维持两套要求。

| 范围 | 现行规则 |
| --- | --- |
| 普通文本、数字、搜索、Textarea、普通Select触发器 | 浅灰表面，默认无明显边框；鼠标/键盘聚焦均不新增border、outline、ring或box-shadow外框。可以用现有表面Token轻微反馈，不恢复蓝/灰描边 |
| 组合输入 | 外壳统一负责背景和圆角，内部input透明；这里的内部透明不等于整块控件透明。禁止双层容器/双焦点框 |
| 图标、文本、快捷键 | Flex/Grid与gap分隔，通常8–12px；顶部搜索已落地12px。绝对定位图标必须预留对应padding，默认样式不得覆盖 |
| 表单对齐 | 复用Input/Textarea/Select/FormField；kb-form-row共享label/control/meta轨道，长标签、帮助、错误不推歪相邻输入；窄屏单列 |
| 尺寸 | 普通单行Input/Select默认44px、水平padding12px；普通Textarea默认最小96px，保留业务所需高度与独立滚动 |
| 原生语义 | 保留label、aria、readonly、disabled、min/max/step、required和v-model；数字空值不能强制转0 |
| 排除项 | 代码/Markdown编辑器、文件、日期/时间、颜色、范围等特殊控件按原实现评估，不机械套用普通输入重置 |
| 键盘与浮层 | 输入保留Tab、方向键、Enter、Escape、光标/选择及菜单焦点管理；菜单项、按钮、Checkbox/Radio的可见键盘反馈不全局取消；forced-colors保留系统指示 |
| 错误 | 真实错误文字、aria-invalid和aria-describedby；不只靠颜色，不为错误恢复普通输入的聚焦外环 |

既有开发基线（具体实现范围见对应报告）：

- 颜色/字号/圆角/间距使用现有`--kb-*`；沿用Vue 3、TypeScript、现有Tailwind与Scoped CSS，不新增UI库或第二套主题。先修公共组件，禁止全局input/button重置与大面积!important。
- 字体角色：UI/聊天正文用Sans，少量展示标题显式用Serif，代码/技术标识用Mono；使用现有字号/行高Token，根字号保持14px。
- 菜单为白色/主题悬浮卡片，浅灰hover；平面Select选中默认白底/透明、右侧Check，不用永久灰底或字重混充焦点；搜索区约42px，选项独立滚动，保留视口避让及键盘操作。
- 二次确认复用ConfirmDialog/AppDialog与dialog.confirm/confirmDialog；文案具体、安全次操作优先、异步loading防重、失败留弹窗重试。不得window.confirm或每页另造确认框。
- 图标复用Icon支持的线性SVG，图标按钮有可访问名称；不传不支持的fill属性，页面类用fav-*等局部前缀，避免icon-*全局字体伪元素冲突。
- 聊天复用AIComposer及唯一发送/停止入口；无消息头像列，正文与Composer宽度协调；教练为约340px独立工作栏，空间不足用抽屉。学习核心数据流不因视觉调整重算。
- 保留已有未提交代码，不自动commit/push/reset/clean；功能修改按项目规则全量测试、类型、build与真实浏览器验证。纯文档修改检查链接/一致性/Diff即可，不冒称重新测试产品。

**实施边界**：公共控件和组卷页已接入结构化对齐，其他历史页面主要完成外观迁移，不能宣称所有旧表单结构已迁移。AIComposer是独立组合组件：内部textarea不画框，容器保留源码现有克制边界/阴影；普通输入无聚焦外框的规则不等于本轮已经改造全部Composer容器。状态与限制见[最终输入报告](borderless-forms/no-focus-frame/REPORT.md)、[确认弹窗报告](confirm-dialog/REPORT.md)、[收藏图标报告](favorites-icons/REPORT.md)。

## 1. 设计理念与适用范围

定位：蓝白灰、极简、专业、内容优先的 AI 学习工作台。信息层级服务于“当前任务、学习上下文、下一步”，不把每段内容套成相同阴影卡片。不复制 ChatGPT、DeepSeek、Notion 或 Linear 的品牌资产；借鉴容器化输入、轻量菜单、可预测键盘交互。

视觉策略：白色内容表面 + 浅灰页面层级；品牌蓝承担主要操作、链接和选择标记；按钮/菜单可保留键盘提示，普通输入不得据此恢复蓝/灰聚焦外框。普通字段以浅灰表面表达可编辑性，内部不再叠框；悬浮层保留轻边界和克制阴影。

本文持续维护现行规范；后续日期条目记录实现事实。下列初始审计来自2026-10-23，不作为当前未完成清单；代码和各次验证报告决定实现状态。2026-10-24本次规范同步仅修改文档，不修改运行时代码。

### 1.1 真实审计范围与方法

- 工作守则：AGENTS.md、VIBE_CONTEXT.md、DEVELOPMENT_PROTOCOL.md、PROJECT_ARCHITECTURE.md、UI_UX_SYSTEM.md。
- 样式：`frontend/src/style.css`、`styles/ui-tokens.css`、`styles/pc-studio.css`、`styles/ui-v2-phase-a.css`；入口 main.ts、CLayout.vue、useTheme.ts、tailwind.config.js。
- 组件：ui/Input、Textarea、Button、Select、Checkbox、AppDialog、ContextMenu、ToastHost、EmptyState、ErrorState、Icon、CategoryTreeSelect；ai/AIComposer；Chat、Messages、Login、SearchResult、Profile、NotesManage 等实际调用点。
- 静态扫描全部 `frontend/src/views/**/*.vue` 和 `components/**/*.vue`；不扫描独立 desktopApp/mobile/miniprogram 工程。计数仅表示源码标签出现次数，含隐藏条件、不同模式和业务专用输入，不等于缺陷数或运行时控件数。

| 扫描区域 | 原生 input / textarea / select | 含原生标签的文件 | Input / Textarea / Select 组件标签 | AIComposer / ContextMenu |
|---|---|---|---|---|
| views | 306 / 84 / 148 | 86 | 23 / 0 / 0 | 2 / 2 |
| components | 54 / 16 / 18 | 35 | 4 / 0 / 0 | 0 / 0 |

结论：全站主要靠原生元素 + CSS 类实现，不能只升级三个包装组件就宣称统一，也不能机械替换文件、日期、数字和画布输入。

### 1.2 初始审计快照（2026-10-23，非当前规范/待办）

| 分类 / 优先级 | 源码证据 | 已实现事实 / 风险 | 后续处理，尚未实施 |
|---|---|---|---|
| 正确基础 | style.css `:root`、`[data-theme='dark']`；useTheme.ts | 品牌与语义色、主题切换、color-scheme 已存在 | 延续同一主题机制，不新增第二套 |
| 历史字段基础 | style.css `.kb-input/.kb-textarea`、ui-tokens.css `--kb-field-*` | 当时有浅边界/低透明环；该外观已失效 | 当前改为浅灰、无聚焦外框；error/disabled/forced-colors语义保留，见第0/5节 |
| 正确基础 | AIComposer.vue 模板和 resize/onKey；Chat.vue 377 附近；Messages.vue 166 附近 | 输入受控、增长上限、工具事件、原业务入口已接入 | 不复制发送；继续完善视觉 Token 与错误优先级 |
| 正确基础 | ContextMenu.vue activate/cleanup/onKey/position | Teleport、测量避让、键盘、外部/Escape 关闭、disabled/selected 已有 | 保留；视觉缺项另修，不能仅凭交互测试认定样式完整 |
| Token 缺失 P0 | AIComposer.vue `var(--kb-radius-xl)`、`var(--kb-radius-full)`；ContextMenu.vue `var(--kb-shadow-md)`；全 frontend 搜索无对应定义 | 声明可能在计算值阶段失效，圆角/阴影不能保证；当前截图也未证明符合目标 | 优先补齐统一 Token 或改用已定义值；本轮不修代码 |
| 焦点冲突 P1 | Chat.vue `.phase-d-chat :focus-visible`；AIComposer 内部更具体选择器；Login.vue `.form-input:focus` + `:focus-visible` | Composer 内部双框已在前序修复，但页面宽泛规则仍在；登录键盘焦点仍叠加环和 outline | 页面只补无自有焦点的元素；每控件选单一绘制层 |
| 规范漂移 P1 | UI_UX_SYSTEM 组件表的 Dropdown.vue、开头 styles/style.css 路径 | 实际无 ui/Dropdown.vue；主样式在 src/style.css；文档历史版本混杂 | 原文勘误并链接本专项，不创建同名空组件迎合文档 |
| 样式重复 P1 | style.css `.kb-dropdown-*`、`.kb-select-*`；ContextMenu.vue；Chat.vue `.conversation-menu` | 有多套面板圆角、阴影、选中态；Chat 留有旧菜单 CSS | 先核对实际引用再删除死样式，提取共享浮层视觉 Token |
| Select 可访问性 P0 | Select.vue 无 onKey/aria-expanded/listbox，label 无关联；style.css `.kb-select-panel` absolute | 外部关闭和卸载清理已有；完整选项键盘、Escape、禁用选项、视口避让缺失，可能被裁切 | 优先补齐选择语义和行为；不能直接套 action menu role |
| 字段语义 P1 | Input.vue/Textarea.vue Props、根包装 div | error 有文字与 aria-invalid，但未关联 aria-describedby；required 只画星号；readonly/name 等未明确透传到内部元素 | 规划内部 attrs 透传与 label/error ID，兼容原 v-model；不要误把包装 div 属性当作输入属性 |
| 多行上限 P1 | Textarea.vue resize 直接使用 scrollHeight | autoResize 没有 maxHeight，长内容可无限撑开；不同于 Composer 已有上限 | 增加可配置上限和区域滚动，保持原生手动 resize 场景 |
| 尺寸漂移 P1 | Button.vue iconOnly md=`w-9 h-9`，注释声称38；Input 用 py-2，无绑定 input-height；Select CSS 写38 | Token 不保证实际高度；icon md 实为36px；ghost 实为蓝色描边 | 统一尺寸语义与兼容变体，避免无审计改名造成全站回归 |
| 焦点缺失静态风险 P0 | Checkbox.vue 隐藏 opacity:0 input；style.css `.kb-checkbox-*` 无视觉代理焦点 | 原生键盘能力存在，但焦点画在透明 input 上可能不可见 | 给可见 box `:has(input:focus-visible)`；需真实浏览器/高对比度验证 |
| 弹窗语义 P0 | AppDialog.vue aria-label、焦点圈定/恢复只在 scrollable 分支 | 表单模式较完整；普通确认模式不保证可访问名称、焦点圈定/恢复 | 两种模式统一基本对话框合同，保留 persistent 语义 |
| 语义色风险 P0 | ContextMenu `.is-danger`、style.css --kb-destructive | #EF4444 对白底约3.76:1，普通小字号不足4.5:1；减透明不能补救 | 拟新增 danger-text，经浅/深主题验收后用在文字；保留业务危险语义 |
| 状态缺口 P1 | AIComposer error 模板/样式；Button loading 模板 | Composer 有 error 文案与 aria-invalid，外层错误边界优先级未专门定义；Button loading 有spinner/disabled，未明确 aria-busy | 错误可读且不被蓝焦点盖住；loading 补播报和状态合同 |
| 缺少统一组件 P2 | ui 文件清单；业务页面原生 radio/switch/title | 无独立通用 Radio/Switch/Tooltip/Combobox 组件；Select searchable 不是完整 Combobox | 先定义语义再按真实复用需求扩展，不为表格中每项都新建组件 |
| 已有状态能力 | StateView/EmptyState/ErrorState/ToastHost/utils/toast | 空、错、重试和通知入口存在，不能把异常伪装成空结果 | 复用现有入口，不新建通知总线 |

P0 为下一开发批次优先项，不表示本轮已复现生产事故；静态风险须按具体页面复现。尺寸/缺失变量属于源码确认事实；本轮未修改实现。

## 2. 颜色 Token 与主题

### 2.1 已定义且继续使用

| 语义 | 当前 Token | 浅主题值 / 规则 |
|---|---|---|
| 页面 / 内容 / 悬浮层 | --kb-background / --kb-card / --kb-popover | #F7F8FA / #FFFFFF / 现有主题值 |
| 表面层级 | --kb-surface-0/1/2/3 | 对应 background/card/muted/popover；避免再造同义调色板 |
| 正文 / 辅助 / 占位 | --kb-foreground / --kb-muted-foreground | #1A1D23 / #6B7280；placeholder 不承担唯一 label |
| 默认边界 | --kb-border | #E2E6EC；不是输入可访问名称的替代 |
| 品牌 / 主色文字 | --kb-primary / --kb-primary-foreground | #3B6FE0 / #FFFFFF |
| 强调软底 | --kb-primary-soft | 使用现有值；仅小面积状态 |
| 按钮/菜单键盘焦点 | --kb-ring；--kb-focus-ring 为别名 | 不用于恢复普通输入外框；高对比度保留系统指示 |
| 成功 / 警告 / 错误 / 信息 | --kb-state-success/warning/error/info | 沿用；图标、文字和状态一起表达 |
| 危险基色 | --kb-destructive | 保留现有语义，不直接推定适合任何字号 |
| 历史字段环变量 | --kb-field-focus-shadow / --kb-field-error-shadow | 变量存在不代表普通输入应使用；普通字段聚焦禁止外环，特殊组件按真实源码评估 |
| Composer容器 | 现有AIComposer样式与--kb-border-hover/--kb-shadow-md | 内部textarea无框；容器反馈独立评估，不复用普通字段外环 |

### 2.2 初始Token收口计划（历史，新增前核对源码）

值只在 Token 定义文件集中声明；组件禁止复制十六进制。以下名字为开发建议，落地前检查已有别名，不同时维护多套事实来源。

| 拟议 Token / 映射 | 默认目标 | 深色要求 |
|---|---|---|
| --kb-border-hover | 沿用现有 color-mix(border 65%, muted-foreground) | 从主题语义色混合，不写固定浅灰 |
| --kb-field-surface / --kb-field-border | card / border | 继承主题 |
| --kb-field-active-border | primary；error 优先 destructive | 检查非文本3:1与高对比度 |
| --kb-menu-hover / --kb-menu-selected | muted / muted，selected 加勾选标记 | 不只靠灰度区分选择 |
| --kb-disabled-surface/text | muted / muted-foreground | 原生 disabled 与只读不可混同；不要整块统一降低透明度影响文字 |
| --kb-danger-text | 浅主题候选 #B91C1C，待实测后定值 | 用独立浅红文字候选并实测，不沿用深红 |
| --kb-radius-xl / --kb-radius-full | 20px / 999px，供 Composer/圆按钮 | 必须先补定义；均为拟议值 |
| --kb-shadow-md | 优先别名到现有 --shadow-dropdown-2 | 随主题检查，不能仅用阴影表达边界 |
| --kb-composer-radius / --kb-menu-radius | xl / md（10px） | ContextMenu 当前 lg=16px，目标待迁移 |
| --kb-menu-shadow | shadow-md | surface+border 必须同时存在 |
| --kb-composer-max-height / --kb-menu-item-min-height | 12rem / 36px（桌面） | 触屏菜单热区提升至44px |
| --kb-layer-menu/dialog/toast | 需先盘点当前层级：Select50、Dialog1000、ContextMenu1200 | Dialog 内菜单应高于所属Dialog，背景菜单不得跨越模态层；禁止盲目加z-index |

主题只复用 `<html data-theme>` 与 useTheme，不新增媒体查询驱动的第二套主题状态。Token 别名应在定义处与深色覆盖一起检查，避免 root 别名解析出浅主题固定值。

对比度静态计算（sRGB、纯白背景、无透明合成）：正文约16.88:1、辅助约4.83:1、品牌蓝约4.63:1、危险红约3.76:1。品牌蓝与辅助色余量有限，不能再通过 opacity 削弱。此计算不是全站对比度 PASS；hover、浅灰背景、深色主题须分别测。

## 3. 字体、排版与图标

沿用 --font-sans（Noto Sans SC + 系统回退）、--font-mono（既有代码字体）；不新增网络字体。--font-serif 保留长文/展示语境，工作台控件、页面操作标题用 sans。字体加载失败不应改变布局与操作可见性。

| 角色 | 字号 / 行高 / 字重 | 复用Token |
|---|---|---|
| H1 | 28 / 36 / 600 | --kb-fs-h1 / --kb-lh-h1 / --kb-fw-h1 |
| H2 | 24 / 32 / 600 | --kb-fs-h2 / --kb-lh-h2 / --kb-fw-h2 |
| H3 | 20 / 28 / 600 | --kb-fs-h3 / --kb-lh-h3 / --kb-fw-h3 |
| Section | 18 / 26 / 500 | --kb-fs-h4 / --kb-lh-h4 / --kb-fw-h4 |
| Body Large | 16 / 28 / 400 | --kb-fs-body-lg / --kb-lh-body-lg / --kb-fw-body-lg |
| Body | 14 / 22 / 400 | --kb-fs-body-md / --kb-lh-body-md / --kb-fw-body-md |
| Button | 14 / 20 / 500 | --kb-fs-body-md / --kb-button-line-height / --kb-fw-label；行高语义映射已落地 |
| Form Label | 13 / 20 / 500 | --kb-fs-body-sm / --kb-lh-body-sm / --kb-fw-label |
| Caption | 12 / 18 / 400 | --kb-fs-caption / --kb-lh-caption / --kb-fw-caption |

字号与字重不在业务页重建；行高Token当前为无单位比率，上表列出对应像素目标。UI用Sans，代码用Mono；显式展示标题可少量Serif，不用于按钮/导航/表单。图标默认16px、大控件18px、紧凑14px、页面20–24px；图文默认gap8px。普通对齐使用Flex/Grid双轴居中，图标flex-shrink:0，不靠负margin、绝对定位或transform校正。既有搜索内嵌图标的定位需预留padding，后续优先迁移Flex结构。

图标统一走 Icon.vue；装饰图标 aria-hidden，图标按钮由动作提供中文可访问名称，不能只用 `iconName='trash'` 作为最终名称。图标本体与点击热区分开设计，不放大图标来凑触控尺寸。

## 4. 间距、圆角、尺寸与阴影

4px基线：space-1/2/3/4/5/6/8 = 4/8/12/16/20/24/32px。表单字段纵向16px，组间24px；label与控件8px，错误与控件4–8px。页面安全边距沿用24px PC、16px窄屏，不叠加二次水平padding。

圆角：xs4（标记）、sm6（普通按钮默认/菜单项）、md10（普通控件/菜单）、lg16（一般面板；确认弹窗按专用22px Token）、xl20（Composer）、full（圆图标按钮/胶囊）。同一控件只画一层圆角，输入内部不再套md。卡片不必全部带阴影，默认表单不使用悬浮层阴影。

| 层级 | 按钮高度 | 普通字段高度 | 图标按钮热区 | 场景 |
|---|---|---|---|---|
| sm | 32px，既有btn-height-sm | 普通字段仍默认44px；紧凑变体需显式评估 | ≥32px | 桌面密集工具栏 |
| md | 38px，既有btn-height-md | 44px，既有input-height | 按Button现有尺寸Token | 默认业务表单；不要把所有按钮强制改为输入高度 |
| lg / 触屏 | 44px，既有btn-height-lg | 44px；手机输入文字建议≥16px | ≥44px | 主要CTA及兼容触屏 |

拟字段尺寸、热区须落到 Token，不将本表数字复制到每个组件。桌面密集操作至少24px可点区域，目标32–38；触屏44。旧 `--kb-control-hit-size=40px` 与上述触屏目标不等价，需按平台合同收口。

### 4.1 Button / IconButton 统一合同（V3）

- 默认38px、Small32px、Large44px，使用现有`--kb-btn-height-sm/md/lg`；默认圆角6px使用`--kb-btn-radius`。视觉居中不要求按钮与44px输入等高。
- 默认14/20、500；默认图标16px，大按钮18px，紧凑14px。普通图文gap8px，显式紧凑gap6px；图标不伸缩，内容区min-width:0，inline-flex双轴居中。
- 常规水平padding12–16px；按钮间距8–12px。上层工具栏允许换行，单个标签保持单行；不靠固定窄宽截断动作名称。长文本调整布局/合理文案，必要省略须提供完整可访问名称。
- 常规按钮无额外装饰阴影。Hover/Active/Focus-visible/Disabled/Loading状态齐全且无无意义尺寸变化；loading保留标签与稳定图标占位，不因增加spinner而跳宽，aria-busy与防重复提交必须保留。
- IconOnly提供语义可访问名称，不以原始图标名作为最终文案；图标大小与热区分别管理。确认弹窗、AI发送按钮继续使用专用Token，不机械改为38px。
- 2026-10-25公共Button已校准：真实32/38/44px、14/20/500、gap8、6px圆角、无装饰阴影，loading保留标签宽度，支持iconPosition。新增outline显式变体；**ghost保留原有蓝色描边合同**，不为命名统一破坏既有调用。专用确认/AI发送Token不变。展示与本次验证范围见[组件展示中心](component-playground/REPORT.md)，不据此认定所有业务页自绘按钮已迁移。

### 4.2 Layout 与Token实施边界（V3）

4px间距基线；图文8px、关联按钮8–12px、表单列16px、内容组24px、页面区块24–32px；PC安全边距24px、窄屏16px。优先Flex/Grid与align-items/justify-content/gap，不用随意margin或transform修补错位，不设导致截断/横溢出的固定宽度。专用场景沿用明确的组件Token。

基础Token目前分布于`frontend/src/style.css`与`frontend/src/styles/ui-tokens.css`，Tailwind映射和别名仅引用它们。新增语义映射前检查已定义变量；缺少Token标记待实现，不在页面或文档示例维护第二套主题。公共Button/Icon/Input/Select/Checkbox/Dialog/FormField优先于页面自绘，按真实调用逐步迁移，不批量盲替换或大量!important覆盖。

## 5. 输入框与文本区域

### 5.1 普通控件外观

文本、数字、搜索、Textarea和普通Select触发器统一使用现有浅灰表面Token；默认无明显边框，鼠标/键盘聚焦不新增蓝色、灰色outline/ring/边界或外圈阴影。组合输入由外壳拥有背景和圆角，内部透明。禁止把这一规则扩散为全局取消按钮、菜单、Checkbox/Radio的键盘反馈。系统forced-colors保留Highlight指示。

### 5.2 字段与对齐合同

- 复用FormField的label/control/meta结构；同排用kb-form-row共享轨道，长标签和帮助/错误不会推歪相邻输入；窄屏单列。
- 单行Input/Select/DatePicker默认44px、文字14px、水平内边距12px；表单标签13/20、500，标签间距8px、列间距16px、字段纵向16px、组间24px、帮助/错误距离4–8px，均引用已有Token。图标/文字通常间隔8–12px，顶部搜索12px；图标不覆盖placeholder或快捷键。
- label、name、autocomplete、inputmode、type、required、readonly、maxlength保持真实语义。readonly能选择复制，不用disabled冒充；placeholder不替代label。
- 错误使用文字、aria-invalid与aria-describedby，保留错误语义表面；不依赖聚焦外框表达错误。
- 数字保留原生min/max/step、整数/小数与原v-model.number契约，空字符串不强制转0。
- 普通Textarea保留合理高度/resize，自动增长到上限后内部滚动；不把代码、Markdown、contenteditable及特殊原生输入机械套用普通样式。
- 不改变Enter/ShiftEnter/IME、提交、复制、选择、清除或快捷键，不通过视觉假装请求成功。菜单开关/焦点恢复继续沿用原实现。

## 6. AI Composer 规范

现有 AIComposer 是复用入口，不创建第二个“ChatGPTInput”。结构保持附件区→textarea→工具栏；左侧附件/知识库，右侧模型配置/语音/发送或停止。只用模型配置能力，不把当前入口描述为已实现独立模型选择器。

容器：card表面、1px轻边界、现有--kb-composer-radius圆角、克制小阴影；padding space-3/4。textarea内部无边界、背景透明；默认可见约一行，行高body-md；增长到12rem（可配置），之后内部滚动。消息区域独立滚动，Composer不被教练面板/底部导航/软键盘遮挡。长附件名截断但保留移除按钮和可访问名称。

焦点：内部textarea不绘制outline/ring/阴影；AIComposer外壳使用源码现有的克制边界和阴影反馈，不套用已废弃的“普通输入外环”规则。工具按钮保留各自键盘反馈，forced-colors保留系统指示。不得用页面宽泛focus-visible给内部textarea再加一框。

行为保留：Enter发送、ShiftEnter换行、中文组合输入不发送；空白且无附件禁止发送；busy防重复，stoppable显示停止；停止只取消当前请求；失败保留原问题/附件，不覆盖用户新草稿；文件校验和权限继续由业务层处理。图片能力不伪称支持任意文档解析。语音沿用已有实现，实际权限/浏览器支持未验不显示虚假成功。

职责合同：v-model文本/知识库，父层持有附件和请求状态；submit/stop/attach/removeAttachment/voice/modelSettings events复用原入口；组件负责布局、输入事件、resize、focus和卸载清理。无第二套Pinia/API/SSE。error外层视觉和状态播报为待完善项。

## 7. 菜单与选择浮层

### 7.1 共同视觉

浅主题白色（popover/card）、轻边界、目标md10圆角、shadow-md，内部space-1；行padding space-2/3，图标16px，文案13–14px，行高目标至少36px。分隔线用border，组间space-1；hover浅灰，selected浅灰+勾选/标记，不能只靠与hover相同底色。danger用经过对比度验证的语义文字色。长菜单max-height受视口约束并内部滚动。

### 7.2 不同语义不能混用

| 类型 | 语义和键盘合同 | 实施路径 |
|---|---|---|
| 动作菜单 | menu/menuitem；选择型动作可用menuitemcheckbox；上下/Home/End，Enter/Space执行，Escape关闭并恢复触发焦点 | 复用ContextMenu，业务items由调用方传入 |
| Select | 值选择，优先原生select；自定义用listbox/option与aria-selected | 先补现有Select，不能换成动作menu |
| Combobox | 可编辑过滤输入+建议listbox，aria-expanded/controls/activedescendant等完整合同 | searchable Select当前不满足；先定义焦点模型再实现 |
| 导航链接浮层 | 链接列表，保留原生链接和Tab语义 | 不强行加menu role |

仅接管指定目标的右键，不全局屏蔽浏览器菜单。更多按钮提供名称、aria-haspopup、aria-expanded；移动端依靠按钮。右键/更多/ShiftF10使用同一业务items；不能触发底下列表项选择。

定位复用已有 Teleport/测量能力：按触发点/锚点，测量后翻转或clamp；保留视口安全间距，避免父级overflow裁切；滚动/resize可关闭，菜单自身滚动不关闭；尺寸变化重测。监听只在open期间存在，关闭和卸载必须清理。Select当前尚未具备同等定位能力，不能写成已完成。

点击外部关闭，内部点击不算外部；选中默认关闭，disabled不可执行；Escape恢复仍存在的触发元素焦点；Tab关闭并顺序导航；切换会话/宿主卸载关闭。菜单回调失败由业务层展示真实错误，不因视觉selected提前假装持久化成功。

## 8. 通用组件规范

| 组件 | 目标视觉与交互 | 复用与待迁移 |
|---|---|---|
| Button / IconButton | primary仅主要CTA；secondary轻边界；text/ghost弱背景；danger有确认。loading不改宽度、原文字保留、aria-busy、防重。键盘/鼠标active一致 | 复用Button；当前ghost为蓝描边，先保留兼容别名，再规划outline/ghost命名；32/38/44热区及loading占位/gap/无装饰阴影已校准；ghost历史合同保留，业务页逐步迁移 |
| Search | 单一容器，左图标右清除；清除按钮有名称；有非placeholder的label；搜索中/无结果/失败不同 | 参考SearchResult容器，不复制所有布局到小搜索；NotesManage等逐页迁移 |
| Select/Combobox | 同字段尺寸和错误合同；选中勾选、无结果文案，加载与请求失败可重试 | 现有Select/CategoryTreeSelect逐项审计；无通用Combobox完成声明 |
| Dialog | lg圆角，title/description关联；按场景宽度，max-height与内部滚动；footer不遮挡正文 | 复用AppDialog/DialogHost和confirmDialog/promptDialog；普通模式补焦点、命名、恢复 |
| Drawer | 与Dialog同模态/焦点/返回合同；右侧辅助层可折叠；窄屏全宽，保留关闭按钮 | 复用页面已有drawer，先盘点再抽取；不宣称存在统一Drawer组件 |
| Checkbox | 公共Checkbox：20px正方形、3px微圆角、1px浅边框；品牌蓝选中/半选与白色14px Check/Minus，文字14px/400、gap8px；默认/hover/鼠标无灰影，键盘品牌outline | 复用原生v-model/表单/Space与半选属性；详见8.1，不替换Radio/Switch/菜单Check |
| Radio | 保留原生语义/同组name/方向键及可见键盘反馈 | 不套用Checkbox方框或普通文本输入无外框规则 |
| Switch | 表示立即生效开关，role=switch+aria-checked，名称稳定，Space切换；状态文字独立 | Composer现有switch可参考；不用于“提交后生效”的布尔表单替代checkbox |
| Tooltip | hover和focus均触发；Escape关闭；role=tooltip、aria-describedby；离开关闭；内容只补充，不放必需操作 | 尚无通用Tooltip；原生title仅过渡，不承担唯一名称/触屏说明 |
| Toast | 成功polite、错误按重要性assertive；可关闭，重试使用可操作提示；不遮挡焦点 | 复用notify/ToastHost，不新增总线；关键错误同时保留在对应字段或页面 |
| Empty/Error/Loading | Loading/Success/Empty/Error/Retry清楚区分；骨架保持大致布局；区域aria-busy，重要状态播报节制 | 复用StateView/EmptyState/ErrorState/SkeletonList；禁止catch(()=>[]) |

### 8.1 Checkbox / 复选框现行规范（2026-10-24 V3）

- 复用`components/ui/Checkbox.vue`，不再按页面重复绘制；正方形20×20px，3px微圆角，不做圆形或胶囊。尺寸集中在`--kb-checkbox-*`，不在页面硬编码。
- 未选中为主题表面+1px浅边框；已选中与indeterminate为品牌蓝底/边框，白色14px Check或Minus居中。只有一个可见方框，不叠原生外观或自绘span。
- 组件、方框默认/hover/鼠标点击都没有box-shadow、drop-shadow或灰色外圈/胶囊背景。hover仅调整边框/填充，不改变尺寸或位置。
- 键盘Tab/Space保留原生能力；可见方框通过focus-visible显示品牌outline，不用灰影。forced-colors使用系统Canvas/Highlight/GrayText指示，不能全局outline:none。
- 文字14px/400、方框文字gap8px、align-items:center；桌面标签热区至少32px，粗指针至少44px。原业务卡片/选项的分组、正确/错误提示不是Checkbox外壳，不为此删除业务状态。
- 禁用仍保留20px轮廓、真实disabled与可读辅助色，不加阴影；半选必须同时设置原生indeterminate属性与蓝底横线。
- 继续支持布尔、数组/Set、数字true/false、id/name/value/required/aria、原change布尔事件；迁移原生事件用native-change携带原Event。父级已有label时用as="span"避免嵌套label，整个原标签仍可点击。
- 闪卡排程Select44px、Checkbox20px、设置按钮沿用Button Token，Flex对齐/12–16px组间距；窄屏换行。不得修改dueOnly过滤、FSRS排程/评分或学习数据。

旧历史18px/4px圆角、12px图标及原生input:focus阴影不能作为新Checkbox标准。普通文本输入无外框规范不取消Checkbox的键盘outline。真实范围、回归与未验证项见[Checkbox V3报告](checkbox-v3/REPORT.md)。

## 9. 交互状态表

| 状态 | 输入/Composer | 按钮 | 菜单/选择器 | 可访问性要求 |
|---|---|---|---|---|
| default | 普通输入浅灰表面、无明显边框；Composer独立容器 | 按层级呈现 | 安静白底/主题表面 | 有名称和真实角色 |
| hover | 普通输入仅克制表面变化，无新框 | 克制底色变化 | 浅灰底 | 不能仅hover才显示唯一操作 |
| focus-within | 普通输入外壳保持浅灰表面，无聚焦外框 | 不适用容器替代按钮焦点 | 锚点与当前项独立 | 不让焦点意外跳走 |
| focus-visible | 普通输入不画蓝/灰环；保留真实焦点/光标及forced-colors | 可见环 | 当前项可见环 | forced-colors可见，无双环 |
| active/pressed | 保持文字选择和输入 | 轻压暗，无位移跳动 | 执行前反馈，等待后端结果 | 不等同selected |
| selected/checked | KB/布尔状态文字明确 | toggle用aria-pressed | 平面Select用Check、不永久灰底；其他按组件语义，aria-selected/checked | 按语义使用，不互换 |
| disabled | 不可编辑，说明原因 | 不执行，无hover | 跳过/禁用不可执行 | 原生disabled优先 |
| readonly | 能选中复制，与disabled区别 | — | — | readonly传到真实input |
| loading | 可继续编辑或禁用由业务决定；stop保留 | 防重、busy/文字 | 请求区域busy，错误可重试 | 不重复播报每个流token |
| error | 错误文字/标记/语义表面，不新增普通输入外环 | 失败可重试不丢输入 | 不伪造成功选中 | aria-invalid/describedby；错误不只靠颜色 |
| empty | 输入占位不代替label | 空内容不得提交 | 无匹配与加载失败区分 | 明确可执行下一步 |

## 10. 桌面、窄屏与主题验收

PC主界面优先：1440×900、1280×800、1024px宽（包括侧栏展开/收起、教练开/关）；390×844和375×812只做兼容，不扩展为独立移动产品。现有断点有639/767/768/1023/1279，后续批次按实际布局收口，禁止全站一次改为另一套断点。

- 外层工作区可铺满，但长文本阅读列/搜索字段/Dialog可保留语义宽度；遵循原文2026-10-14铺满补充。
- 工具栏允许换行，次要长模型名截断；发送/停止不可被挤出，文本和附件区min-width:0。
- Composer保持在底部导航之上，验证100dvh、safe-area、浏览器缩放及软键盘；不能用固定绝对坐标盖住消息。
- 窄屏侧栏/Drawer通过按钮打开关闭、合理焦点圈定，关闭后输入可达；更多操作不依赖hover/右键。
- 深浅主题都按语义Token；验证200%缩放、键盘、reduced-motion、forced-colors。暗色不简单反色品牌或危险色。

## 11. CSS 实施规则

1. **Token先行**：style.css继续维护基础色/主题/基础尺寸；ui-tokens.css维护语义映射与专用尺寸。现有入口顺序为main.ts先style.css后ui-tokens；CLayout另导入pc-studio/phase-a。不要再加第三个全局覆盖文件“修全部输入”。
2. **仅显式组件作用域**：用field/composer/menu类，不写全局input/button border:0；不把Phase A文件扩展为全站皮肤，该文件明确只覆盖外壳和显式页面。
3. **单所有者**：普通输入外壳统一拥有表面/圆角，不拥有常规聚焦外框；嵌套输入不得再挂ring。页面focus-visible不得覆盖普通字段的无外框合同；按钮和菜单规则单独限定作用域。
4. **优先级可解释**：先盘点style.css @layer与SFC scoped顺序，再调整；不批量!important、不盲目加选择器特异性。已有兼容!important不扩散。
5. **状态变量驱动**：组件class/aria状态控制surface、error与disabled，不为普通输入重新添加border/ring。保持真实键盘行为，forced-colors与菜单/按钮的焦点代理分别验证。
6. **浮层共享视觉而非强行共享语义**：菜单和listbox可以共享Token；不复制业务回调，也不把Select强行改为ContextMenu。复用Teleport与测量逻辑时明确焦点模型。
7. **渐进兼容**：先扩展Input/Textarea内部属性、Slots和状态，再迁移调用者；保留v-model、原表单默认提交及native类型；废弃接口需列清调用点。
8. **静态约束**：新增未定义变量/硬编码语义色纳入检查；仅对新增增量报错，避免为通过检查重写全站。CSS示例属于建议，未新增任何可运行实现。

推荐字段DOM合同：FormField → label(for稳定id) / control(统一浅灰外壳、无聚焦外框) / meta(help与error的稳定id)。原生输入保留真实attrs和键盘能力，装饰icon不抢焦点，清除/附件按钮仍是原生button；不以全局outline:none实现，forced-colors使用系统回退。

## 12. 初始迁移计划（历史快照，非当前待办）

| 批次 | 优先统一对象 | 范围 / 完成条件 | 当前状态 |
|---|---|---|---|
| A / P0 | 缺失Token、危险文字对比、焦点所有者 | 补radius-xl/full/shadow-md；颜色双主题验证；定位不叠框 | 待开发 |
| B / P0 | Select、Checkbox、AppDialog基本无障碍 | 完整键盘/label/role/焦点恢复；真实overflow边缘验证；普通Dialog也达标 | 待开发；保留现有可用分支 |
| C / P1 | Input/Textarea/Search、Button | 真实attrs/错误关联/readonly；尺寸、loading、ghost兼容；先Profile/Login/SearchResult/NotesManage验证 | 待迁移；已有基础组件 |
| D / P1 | AIComposer与ContextMenu视觉收口 | 使用补齐Token、单一错误/焦点、热区/选中、三种桌面尺寸及窄屏 | 已接入；视觉与可访问性仍有上述缺项 |
| E / P1 | 高复用业务字段 | 知识库/笔记/学习工具的筛选搜索与表单，按路由清单逐页迁移原生标签 | 需逐页建调用清单，不能机械替换扫描总量 |
| F / P2 | Radio/Switch/Tooltip/Combobox/Drawer | 确有复用需求再扩展组件；完整语义合同与状态演示 | 规范已定义，通用实现未完成 |
| G / P2 | 死CSS及历史别名 | 核对未引用再清理Chat旧菜单、重复.kb-dropdown等；同步原文索引 | 待开发，不在本轮删除 |

每批先做控件状态页/测试用例→选一个真实页面→浏览器验证→扩大调用范围。设计规范进入文档不等于迁移完成。优先级可按发现的真实功能阻断提升，禁止先全站换圆角再补键盘行为。

## 13. 不应修改的业务逻辑

- NextAction唯一入口、Evidence解析/聚合/诊断/策略、路由白名单和上下文盖章、Mini App Registry与学习Store不参与视觉重构。
- Chat唯一发送函数、SSE事件合同、AbortController、请求/会话epoch、持久化、失败新草稿保护、附件校验、语音权限、模型配置和知识库业务含义不改变。
- 会话操作继续真实Owner API；删除需确认；分享必须真实公开Token合同，不把本人链接冒充公开链接；失败恢复真实状态。
- 私聊WebSocket协议、重连/卸载、发消息与文件上传、身份和归属不改；表单原生提交、校验、快捷键不破坏。
- UI隐藏不是后端授权；不得为状态演示编造持久化数据、Evidence或成功响应；不改后端/数据库来迎合样式。

## 14. 验收标准与本轮验证边界

### V3公共组件验收口径

每个组件记录“通过 / 未通过 / 未验证”及证据：真实计算的height/width/padding/gap、图文对齐、单行与长标签、浅/深主题、Hover/Focus/Active/Disabled/Loading/Error、1440/1280/1024桌面及390窄屏、真实200%浏览器缩放、键盘与高对比度。状态切换不应造成无意义尺寸变化；操作不能只在hover时可达。

普通文字目标对比度≥4.5:1；大文字与必要非文本状态分别按适用标准验证。按钮/Checkbox/Radio/菜单保留可见键盘焦点，普通输入继续无聚焦外框；不能全局outline:none。错误须文字/语义配合，深浅主题同语义同尺寸。

代码实施须执行TypeScript、全量单元测试、正式Build与真实浏览器回归；失败与未验证不得写PASS。本次仅文档同步，不重新运行产品门禁，不用历史结果认证当前组件。


### 后续实现的完成标准

- [ ] Token引用全部有定义；无重复语义色硬编码；深浅主题一致。
- [ ] 普通输入/数字/搜索/Textarea/Select浅灰、无明显边框，鼠标/键盘聚焦无蓝/灰outline/ring/外圈阴影；图标、提示文字、快捷键不重叠，同排字段对齐。Composer内部无框，容器独立验收；readonly/disabled/error可区分。
- [ ] 文字对比度普通文字≥4.5:1、大文字≥3:1；必要非文本状态/边界≥3:1（纯装饰除外）。对真实合成背景测量，不只看色卡。
- [ ] iconOnly有动作名称；label/required/error确实关联内部元素；隐藏checkbox焦点可见。
- [ ] 菜单边缘不越界、父overflow不裁切、外部/Escape/切会话/卸载关闭；键盘跳过disabled；原生button语义不破坏。
- [ ] Select/Combobox与动作menu角色正确；自定义选择器支持完整键盘和值/焦点同步。
- [ ] Dialog各模式有名称、合理初始焦点、圈定和恢复；persistent不会意外关闭。
- [ ] Composer空发送/IME/ShiftEnter/高度上限/附件/KB/发送/停止/失败新草稿保护回归；模型和语音仅按实际能力验收。
- [ ] 1440、1280、1024及两种窄屏无横向溢出，菜单、教练和底部输入互不遮挡；主题/缩放/高对比度/动画偏好通过。
- [ ] 真实浏览器检查Console和HTTP/Network；fixture与真实提供商分开报告，不能把curl叫浏览器验证。
- [ ] 功能实现批次执行全量vitest、vue-tsc、build；有后端变更再执行相关/必要全量mvn；不删测试或改断言制造PASS。

### 本轮执行报告

1. **目标**：形成直接指导开发的输入、菜单与通用控件规范。
2. **原因**：已有Token/组件基础，但缺失变量、样式重复、交互/无障碍不完整，历史文档有漂移。
3. **文件**：新增本文；UI_UX_SYSTEM.md增加权威关联并修正文档路径/组件索引；AGENT_HANDOFF.md追加记录。
4. **业务影响**：无；未改业务代码、接口、数据、状态和远程仓库。
5. **UI影响**：本轮无运行时变化；文中目标均按已实现/待开发分别标注。
6. **数据流影响**：无；保留第13节边界。
7. **测试**：文档链接/Token静态核查、目标diff检查；未重跑功能测试，前序结果不冒充本轮新结果。
8. **Build**：本轮纯文档未运行；不宣称构建新PASS。
9. **浏览器验证**：本轮未重新操作浏览器；此前Composer的交互证据见[组件报告](composer-context-menu/REPORT.md)，不能替代本规范后续验收。未定义Token等视觉缺项已明确，不以旧测试PASS掩盖。
10. **遗留**：第1.2节均按现状保留；全站逐路由可访问性/对比度、真实截图比对 **⚠️ Not verified**。
11. **下一步**：先执行迁移A，补Token并验证浅/深主题焦点、危险文案与真实圆角阴影，再推进B的无障碍。

### 2026-10-23 Select V2 实施关联

本次选择器能力及分批迁移已落地，执行状态以 [Select V2 报告](select-v2/REPORT.md) 为准：扩展既有 Select，复用 SelectChoices、SelectDialog、AppDialog；新增 4 个 select 语义 Token，未新建主题或依赖。Field/Pill/Subtle、分组/搜索/多选、异步状态与受控类型合同见该报告。本节仅关联本轮结果，不将历史设计目标全部标记完成；未迁移页面、树/子菜单及验证边界仍单独列明。

### 2026-10-23 V3 级联与自动搜索实施

复用现有Token、Field/Pill/Subtle与Select多选/分组，新增自动阈值及可取消远程加载；CategoryTreeSelect适配可悬停级联，小屏分层返回，父选择与展开独立。未增依赖或新Token，未复制菜单业务。[执行报告及尚未迁移/未验证项](select-v3/REPORT.md)为本轮事实记录；全站设计规范继续由本文与UI_UX_SYSTEM关联维护。

### 2026-10-23 错题筛选与平面 Select 状态校准

平面 Select 的选中项使用 Check 和 aria-selected，不使用字重反馈，默认白色/透明背景；浅灰背景仅用于 hover，键盘活动项使用浅灰背景与独立焦点轮廓。该约定不改变 ContextMenu 或 Cascader 的选中设计。筛选行的紧凑宽度由页面控制；Select 默认 Field 全宽保持，可通过可选 panelWidth 独立设置浮层宽度并受视口约束。实施与验证见[精准修复报告](mistakes-filter-fix/REPORT.md)。

### 2026-10-23 可搜索 Select 精准校准

公共 Select 的搜索区使用42px尺寸Token，input取消继承的padding与内框，搜索区的浅灰分隔线在聚焦前后保持一致，不变蓝、不加粗；输入光标标识编辑位置，强制颜色模式保留系统轮廓。单行选项默认42px，Selected仅Check，不用背景或字重混充焦点；Hover/键盘活动使用浅灰，键盘另有轮廓。默认搜索浮层宽度限定300–380px（再受视口限制），显式panelWidth优先；知识库调用340px。搜索面板最高352px且按视口缩小，搜索固定、列表独立滚动。普通非搜索宽度策略保持；长名称省略并提供完整title。实施事实、测试及截图见[专项报告](searchable-select-fix/REPORT.md)，不将旧全站迁移目标视为全部完成。

### 2026-10-23 AI聊天视觉校准

消息阅读区与Composer共用--kb-chat-content-max-width（64rem），窄屏按现有可用宽度和共同gutter收缩；AI消息不保留头像列，用户/助手通过对齐、轻容器和ARIA分组区分。Composer发送尺寸由--kb-composer-send-size（36px）控制，向上箭头为20px、居中；入口统一使用Icon中的message-square-sparkles SVG，路由与激活逻辑不变。实现、前后几何及未验证项见[执行报告](chat-ui-polish/REPORT.md)。

## 2026-10-23：字体与学习教练工作栏实施

此节记录已落地代码。字号唯一来源仍为 `frontend/src/style.css` 的 `--kb-fs-*` / `--kb-lh-*` / `--kb-fw-*`，`--text-*` 和 Tailwind fontSize 仅引用它们，不维护独立数值。根字号保持 14px，不以改变 rem 基准扩大页面。

| 角色 | 字号 / 行高 | 用途 |
| --- | --- | --- |
| Display | 32 / 40 | 显式 `kb-font-display`，少量 Serif 展示标题 |
| H1 | 28 / 36 | 页面标题，Sans |
| H2 | 24 / 32 | 主要区块标题 |
| H3 | 20 / 28 | 次级标题 |
| Section | 18 / 26 | 区域标题 |
| Body large | 16 / 28 | 聊天阅读正文 |
| Body | 14 / 22 | 面板内容、普通 UI |
| Emphasis | 15 / 约24 | 教练目标、下一步标题 |
| Label | 13 / 20 | 控件、辅助正文 |
| Caption | 12 / 18 | 标签、时间和说明 |
| Meta | 11 / 16 | 克制使用的角标 |

字体角色：Sans 使用现有 Noto Sans SC 与系统回退；Serif 使用 Noto Serif SC、Songti SC 等回退；Mono 使用现有 JetBrains Mono、SF Mono、Menlo 等回退。继续使用原有远程字体加载和 display=swap；未新增字体下载依赖。不保证离线环境安装了首选字体。

学习教练：纯展示 `LearningCoachPanel` 消费 Chat 从唯一 learning Store 取得的字段，分为学习概览、今日安排、快捷问题。默认 340px 面板；工作区不足 `340 + 520px` 时复用 AppDialog 右侧抽屉。面板文本完整换行，不使用固定 label 列或省略号；正文独立滚动。错误提供重试，无证据不绘制诊断。NextAction、Evidence、路由盖章、聊天发送和 SSE 均沿用原入口。

公共标题、Tailwind 字体类及已有 Token 消费者全站受益；本次只迁移存在局部固定字号的 FlashCardsHub、SmartQuiz、Profile、TodayPlan 等页面，不声称所有历史局部字号已全部清零。验收与剩余范围见 `docs/coach-typography/REPORT.md`。

## 2026-10-23：二次确认弹窗统一

二次确认统一使用 `ConfirmDialog`（复用 AppDialog 的 Teleport、键盘与模态管理），`dialog.confirm` 和兼容 `confirmDialog` 共用一个队列；ToastHost 不再维护确认弹层。编辑/选择/设置/抽屉仍使用其原组件，不把所有弹窗伪装成确认。

- 确认宽度默认560px，圆角22px、padding28px、标题20/28、正文14/22～28，按钮44px高、最小宽120px；均由 `--kb-confirm-*` 与现有字号/颜色Token提供。
- 标题行使用已有线性Icon、标题、关闭按钮；默认白色主题表面、轻遮罩/轻阴影，无重毛玻璃。次按钮在左、品牌蓝主按钮在右；删除/清空使用语义危险色。
- 默认聚焦安全次操作；Tab圈定在最上层，Enter/Space执行当前聚焦按钮，不用全局Enter直接批准危险操作；Escape默认取消并恢复触发焦点。业务可分别设置closeOnOverlay/closeOnEsc；loading时禁止确认、取消和关闭。
- 异步操作通过 `onConfirm` 等待原API，成功才关闭，失败保留错误和重试；直接受控组件也可传loading/error/disabled。已有复杂流程的前置确认仍保留页面自身状态与错误管理，不移动其权限/保存/重定向决策。
- 全局普通队列一次只显示一项；正在等待的异步操作发起子确认时，以父子链显示，防止隐藏子确认导致死锁。嵌套AppDialog共享顶层判断和计数锁，避免子弹窗关闭后解除父弹窗滚动锁。
- 操作文案由调用方明确设置，不在运行时用文案正则猜业务。危险工具保留既有后端授权和超时拒绝语义。浏览器刷新/关闭的beforeunload保护由浏览器控制，不能替换为可靠的异步Vue确认。

审计、文件与验证证据：`docs/confirm-dialog/AUDIT.md`、`REPORT.md`。

## 2026-10-24：已落地输入与对齐基线

普通字段的现行规则见第0、5、9、11节：统一浅灰表面，聚焦不出现蓝色或中性灰外框。FormField/kb-form-row共享label/control/meta轨道；普通Input/Select默认44px。数字、Textarea与特殊编辑器边界保持第5节合同。

历史变化仅留链接：最初的[灰色键盘环方案](borderless-forms/REPORT.md)与中途[透明表面方案](borderless-forms/transparent-update/REPORT.md)均已失效；不得拿它们作为新开发规范。最终有效实现与验证见[浅灰无聚焦外框报告](borderless-forms/no-focus-frame/REPORT.md)。397处普通控件和138处排除项为当次扫描快照，不是永久数量约束；未迁移的历史表单布局不可描述为已完成。

## 文档治理关联（2026-10-24）

本文件仍是UI专项唯一当前入口；[旧前端规范](前端规范/UI设计系统.md)已标SUPERSEDED，不能恢复旧聚焦环/旧字号。项目整体状态/源码数量/验证以[当前状态](roadmap/01_CURRENT_SYSTEM_AUDIT.md)、[能力矩阵](roadmap/02_PRODUCT_CAPABILITY_MATRIX.md)为准；历史UI报告只证明当次场景，不自动认证当前全部路由。后续UI规范变更按[文档维护规则](DOCUMENTATION_GOVERNANCE.md)同步正文、组件说明和视觉回归边界。

## 2026-10-24：日期选择器 DatePicker V2

原生 `<input type="date">` 的弹层属于浏览器 UI，不能用页面 CSS 控制。单日期字段统一复用 `frontend/src/components/ui/DatePicker.vue`，禁止给原生弹层外壳加样式冒充“已统一”。

- 合同：`v-model` 固定 `YYYY-MM-DD`（空字符串为未选择），全链路禁止 `toISOString()`；日期时间、日期区间使用独立变体，不混用同一 `v-model`。
- 触发器：44px、12px 水平 padding、浅灰表面、无边框；输入区聚焦不出现蓝/灰框或外圈阴影，保留手工输入与 `aria-invalid`。
- 面板：336px、16px 内边距、`--kb-radius-lg`、1px `--kb-border`、`--kb-popover`、`--shadow-dropdown-3`，`placeSelect` 视口避让；标题左、左右箭头右、固定七列。
- 状态与无障碍：选中用品牌蓝底白字，今天用 `--kb-primary-soft` + 圆点；日期格/导航/底部操作使用 inset 键盘 outline，`forced-colors` 使用系统 `Highlight`；不得全局 `outline:none`。
- 迁移：先替换单个日期字段并核对原保存合同，不批量替换 `date/time/datetime-local`。清单与验证证据见 [DATE_PICKER_UI_V2_EXECUTION](ui-v2/DATE_PICKER_UI_V2_EXECUTION.md)。

## 2026-10-24：日期时间组件 V3（TimePicker / DateTimePicker / 统一锚定）

浏览器原生 `date/time/datetime-local` 面板不在页面 DOM 内，页面 CSS 无法控制其定位与遮挡；凡需要可控体验的字段必须迁移到共享组件，不得使用 `top/-translateY` 等固定偏移补丁。

- 组件职责：`DatePicker.vue` 仅 `YYYY-MM-DD`；`TimePicker.vue` 仅 `HH:mm` / `HH:mm:ss`；`DateTimePicker.vue` 组合 `YYYY-MM-DDTHH:mm(:ss)`。日期、时间、日期时间不得共用同一个语义不清的 v-model。
- 定位：统一复用 `select-position.ts` 的 `placeAnchoredPanel` 与 `use-anchored-panel.ts`。真实输入框为 Anchor，默认下方、空间不足翻转上方并保持 4px 贴边，右侧超界左移，至少 8px 视口安全距；Teleport 到 body、`position: fixed`、`z-index: 1200`；ResizeObserver、window/visualViewport 滚动与 resize 重新测量；锚点滚出视口自动关闭；卸载解绑监听。
- 面板：DatePanel 是日历唯一实现，TimeColumns 是时间列唯一实现。时间列宽 240px（分）/288px（秒），每列固定高度、独立滚动、选项 30px 居中；禁止在页面内展开超长列表。
- 交互：日期/时间选择均为草稿，点「确认」才写回；「取消」「Escape」「外部点击」不污染原值；清除仅在允许的空值字段出现。底部操作区 sticky，面板被视口压缩滚动时按钮仍可见。
- 视觉：沿用 `--kb-field-*`、`--kb-popover`、`--kb-border`、`--kb-radius-lg`、`--shadow-dropdown-3`；输入触发器 44px、浅灰、聚焦无蓝/灰外框；日期格、时间项、按钮保留 inset 键盘反馈与 `forced-colors` 系统焦点。
- 合同：纯日期禁止 `toISOString()`；纯时间不附加虚拟日期/时区；LocalDateTime 保持本地墙钟，不加 `Z`/偏移；旧值含秒不得静默截断；非法时/分/秒与不存在日期必须拒绝。
- 迁移边界：`Habits`、`LearningPrefs` 使用 TimePicker（`HH:mm`）；`CalendarEventDialog` 非全天用 DateTimePicker、全天继续 DatePicker；`MemorialDialog` 固定日期用 DatePicker、每年重复逻辑不变；`LearningCenterPage` 需先统一截止时间合同；`ExamMistakes` 属日期范围，待独立 DateRangePicker，禁止误用 DateTimePicker。

执行记录、几何证据与迁移清单见 [DATE_TIME_PICKER_V3_EXECUTION](ui-v2/DATE_TIME_PICKER_V3_EXECUTION.md) 与 `docs/ui-v2/date-time-picker-v3/`。



## 2026-10-25：日期时间组件 V4（显示/合同分离、年份年月、三种范围）

日期时间控件的用户可见文本**禁止**直接复用存储合同字符串。现行合同：显示层只输出 `2026-10-25 00:00`、`2026年10月`、`2026年` 这类可读格式；数据层继续使用 `YYYY-MM-DDTHH:mm(:ss)`、`YYYY-MM`、`YYYY`。实现唯一入口是 `date-time-format.ts`（解析/显示/序列化/校验），组件内不得再拼格式字符串或全局替换 `T`。

- 组件矩阵：`DatePicker`（`YYYY-MM-DD`）、`YearPicker`（`YYYY`）、`MonthPicker`（`YYYY-MM`）、`TimePicker`（`HH:mm`/`HH:mm:ss`）、`DateTimePicker`（`YYYY-MM-DDTHH:mm(:ss)`）、`DateRangePicker`、`TimeRangePicker`、`DateTimeRangePicker`（`{start,end}` 两个完整合同值）。
- 具体日期与 `DatePicker` 同实现，不另建 `DayPicker.vue`；「每月 N 日」「持续 N 天」在出现真实业务前不实现，且不得与具体日期合同混用。
- 精度：`resolveDateTimePrecision` 遇到已有非零秒自动按秒展示，分钟精度不得静默截断；年份/年月选择不得自动补出某个虚拟日期。
- 范围：默认 `start <= end`；跨午夜必须显式 `allowOvernight`，不因 `02:00 < 22:00` 自动推断次日；半选状态不对外提交「看似完整」的区间；取消/Escape/外部点击恢复原值，确认一次性提交。
- 面板：宽屏左月历 336px + 右时间列（200px，秒 240px），<720px 转日期/时间 Tab；面板 padding 与粘性底栏的 `bottom`、负 `margin` 必须共用同一组变量（`--dtp-pad-x/--dtp-pad-y`），否则媒体查询压缩 padding 会造成恒定溢出与底栏裁切。
- 跨月：日期/日期时间范围必须支持两个端点落在不同月份——选中开始后翻月不重置端点与视图，区间按真实日期值跨月连续高亮；`DateTimeRangePicker` 切换「开始 / 结束」时把日历带到该端点所在月。筛选接口若只接受纯 `yyyy-MM-dd`，组件传纯日期，禁止拼接 `23:59:59` 一类时间后缀。
- 验收：底栏常驻可见、无无意义整体滚动条、锚点定位正确、日期与时间列各自独立滚动。证据见 [DATE_TIME_PICKER_V4_EXECUTION](ui-v2/DATE_TIME_PICKER_V4_EXECUTION.md)。

## 组件展示与维护入口（2026-10-25）

开发环境访问 `/dev/ui-components`；生产构建不注册该路由，不加入学习导航。展示注册表位于 `frontend/src/dev/registry.ts`，真实预览在 `ComponentPreview.vue`，调试页面在 `views/dev/UIComponents.vue`。注册仅引用已编译组件，禁止eval/运行用户代码。

新增公共组件时同步注册名称/分类/组件引用/公开参数/变体/示例/ARIA说明/源码路径，并更新合同测试。组件实例只消费本地脱敏数据，禁止发起AI/学习记录/API写入；业务专用组件不为凑数量强行通用化。

Token列表只保存真实变量名，实际值从当前主题getComputedStyle读取；空值显示FAIL。自动PASS必须说明检查范围，视觉对比度、完整键盘/读屏和业务回归未测时保留NOT VERIFIED。参考[执行报告和维护规则](component-playground/REPORT.md)。

## 2026-10-25 · V6公共控件增量边界

Radio/Switch/Tabs/SearchInput在现有components/ui提供受控API；SearchInput组合真实Input/Button，保持普通字段浅灰无聚焦外框；Radio/Switch/Tabs保留键盘轮廓与forced-colors。沿用--kb-*，未建立独立knowflow-ui/Kf主题。实现不等于完整读屏/视觉/业务集成认证，见[本轮矩阵](component-playground/v6/V6_COMPONENT_MATRIX.md)。

### 2026-10-26 · V7–V8规划的Kf命名兼容（尚未实施）

用户要求新公共API采用JS/TS `KfButton`、模板`kf-button`、CSS `kf-button`与BEM子元素、Token `--kf-*`。规划采用现有Button/Input/Select/AppDialog/Icon的单向别名或薄适配，不批量改名旧组件，不复制逻辑或第二套主题；`--kf-*`只引用既有`--kb-*`，不写另一套颜色/尺寸值。普通输入浅灰且无蓝/灰聚焦外框、按钮/菜单/Checkbox键盘反馈、原字号与尺寸合同继续有效。此为后续规划兼容方案，不代表代码已完成Kf迁移。详见[UI计划](roadmap/learning-v7-v8/06_UI_UX_DESIGN_PLAN.md)。

### 2026-10-26 · PRODUCT_MVP_01 / F03隔离产品切片

真实源码新增私人3题测验草稿确认、冻结题版、复用ExamGrader判分、私人不可覆盖答卷及原NotesAiPanel交互。独立存储只承载公共Quiz缺失的owner私域，不写公开题库；来源经NoteService owner及DocumentAccessPolicy复验。AI调用复用AiService，闭卷练习仅发题干/选项，生成预览暴露条件明确。PRIVATE_QUIZ_SUBMITTED只记录事实，未接管Evidence/NextAction/FSRS或自动写知识映射。默认开关关闭。首批最新状态见下方2026-10-27收口记录，NOT_READY不变。详见产品执行目录docs/roadmap/learning-v7-v8/executions/PRODUCT_MVP_01/。

2026-10-27 PRODUCT_MVP_01：A/F03、B/F16、C/F17三个最小纵向切片DONE；完整高级规格仍PARTIAL。私人测验保存/冻结/客观判分/新旧历史，原Chat三模式Tutor及来源/逐轮模式持久化，受控TXT/Markdown/文本PDF异步学习包、分项重试、编辑采纳与原FSRS均已实施。真实DeepSeek合成资料链路及浏览器刷新/重登/四宽度通过；前端223文件1710项、类型/build通过。后端最终结果见执行目录TEST_REPORT.md，既有888001 Fixture红项不隐藏。六张新增表仅在隔离H2验证迁移与备份恢复；生产/MySQL未认证，开关默认关闭，T01/T02保持DONE、T03/T04仍未完成，全局NOT_READY。下一批F02/F05/F07/F10。


### 2026-10-27 Tutor学习闭环增量

复用原KfButton/Input/Textarea/Select/AppDialog和Markdown渲染器，未创建第二套Token。阅读侧栏与课程/笔记辅助导师一次只显示一处，关闭侧栏后重读同会话；局部网格使用minmax(0,1fr)收缩。原普通输入浅灰、无聚焦外框合同不变。PC三宽度与390折叠原导航后的兼容检查已验；不宣称旧PC导航完成移动迁移。状态见PRODUCT_MVP_02报告。
