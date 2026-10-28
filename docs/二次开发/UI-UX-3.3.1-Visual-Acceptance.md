# UI/UX 3.3.1 视觉验收标准 — Visual Acceptance

> 阶段：UI/UX 3.3.1 · Visual Acceptance + Sidebar + 全局界面修复
> 目标：**不新增任何业务功能**，只做真实页面验收与修复（UI / UX / 导航交互 / 布局 / 侧边栏 / 页头 / 图标 / 字体 / 按钮 / 卡片 / 响应式 / 深色模式）。
> 硬规则（§43）：环境无法真正看到浏览器时，纯视觉/浏览器层验收标记 **⚠️ Not verified**，不得伪称 `✅ Browser tested`。

---

## 状态图例

- ✅ 代码级已落实 + 组件测试覆盖（happy-dom 真实 DOM 行为）
- ⚠️ Not verified：环境无可用浏览器引擎（agent-browser 未装、playwright-core 无 Chromium 缓存；本沙箱 Chromium 白屏不可靠），纯视觉表现无法真实验证，以组件测试 + 源码核查为准
- 🟡 部分落实 / 仅结构性统一，待人工浏览器复核

---

## 1. 当前真实问题（验收基线）

| # | 问题 | 来源 | 状态 |
|---|------|------|------|
| 1.1 | 约 28 处非 App 顶层视图散写 `router.back()`，直接 URL / 刷新 / 书签进入时返回会带离应用 | §16 | ✅ 已统一 |
| 1.2 | 侧边栏折叠态二级 App 图标不可见、点击死按钮 | §3/§9/§10 | ✅ 已修复 |
| 1.3 | 折叠态宽度未真正变化 / 主内容区未同步 | §6 | ✅ 已修复 |
| 1.4 | 折叠不持久化 / 污染 UserApp 状态 | §8 | ✅ 已修复 |
| 1.5 | 激活态仅一层浅背景，不易辨识 | §12 | ✅ 已修复 |
| 1.6 | Tooltip 折叠后可能显示 `undefined` | §11 | ✅ 已修复 |
| 1.7 | 部分页面无统一 PageHeader / 标题贴边 / 顶部空白 | §14/§15 | 🟡 见 §4 |
| 1.8 | 全站字体/间距/按钮/卡片令牌化已建，但各页面仍有散落硬编码 | §18/§20/§22 | 🟡 见 §6/§7/§8 |
| 1.9 | 移动端 390px / 深色模式 / 键盘可达 需逐页复核 | §33/§34 | ⚠️ Not verified |

---

## 2. Sidebar 折叠（P0，§3–§12）

验收清单：

- [x] 展开 240px / 折叠 64px，宽度真正切换（CLayout `padding-left` 同步，CSidebar `width` 切换）
- [x] 折叠非「隐藏」，而是「文字→图标」rail，二级 App 图标全部可见
- [x] 折叠按钮真实渲染、在可视区、`title`/`aria-label` 正确（「收起侧边栏」↔「展开侧边栏」）
- [x] 轻量动画 `transition: width 200ms ease`（Sidebar）+ `padding-left 200ms ease`（Main），内容不飞出（`overflow: hidden` 裁切）
- [x] 折叠态点击分组标题直达工作区首页（修 §9 死按钮）；点击 App 图标 rail → hover / tooltip / click / active / keyboard 均可用
- [x] 持久化：`localStorage['csidebar-collapsed']`，F5 保持；仅写该键，不污染 UserApp（CLayout.spec 断言 `keys === ['csidebar-collapsed']`）
- [x] 图标全部来自 Icon.vue 定义，无空白 / iconfont 丢失（Sidebar 导航图标经 `navigation.ts` 派生，均在 Icon.vue 210 个定义内）
- [x] Tooltip 折叠后 `:title` 显示全称（如「闪卡」），非 `undefined`
- [x] 激活态：`hover` / `active` / `focus-visible` 三态 + 主色左侧指示条（`is-active::before`）

**测试覆盖**：`CSidebar.spec.ts`（11 例）、`CLayout.spec.ts`（6 例）。

---

## 3. 返回体验（P0，§16）

验收清单：

- [x] 统一入口 `usePageNavigation().goBack(fallback?)`：真实 history 存在 → `router.back()`；否则按上下文 fallback → 工作区父页 → Home
- [x] 13 个独立顶层视图已统一（Certificate / Chat / DocCreate / DocDetail / DocEdit / KnowledgeCreate / KnowledgeUpload / LearningPrefs / ObsidianImport / WeeklyReport / admin/ChapterEdit / PostEdit / PostDetail / KnowledgeMasteryDetail）
- [x] 文档列表 → 详情 → 编辑 返回链路正确（DocDetail 兜底 `/docs`，DocEdit 兜底 `/doc/:id`），不粗暴回学习首页
- [x] 12 个 `MiniAppShell` 包裹的 App 视图（Mistakes / CodePractice / KnowledgeGraph / Workbench* / MindMap / SmartQuiz / DrawingEditor / FlashCardsHub / NotesManage / WorkbenchStory / SmartWriting）其本地 `goBack` 为死代码，返回已由 MiniAppShell 内部 `nav.goBack()` 接管，**不重复改造**以免双重返回
- [x] `WorkbenchNoteEdit` 保留「有未保存内容 → confirmDialog 确认」特殊业务返回逻辑，按 §16「不破坏特殊业务返回逻辑」保持不变

---

## 4. PageHeader（§14–§15）

验收清单：

- [x] `PageHeader.vue` 统一结构：面包屑（可点击返回）/ 标题 / 数量徽标 / 描述区 / 右侧 Actions 插槽
- [x] 面包屑 `crumbs.to` 路由可跳转，末项 `aria-current="page"`
- [x] 令牌化：`--kb-fs-*` / `--kb-lh-*` / `--kb-muted` 等，无写死像素
- [🟡] 全站 81 个视图仅 9 个复用 `PageHeader`，其余为各页面自定义头部（多数含返回按钮）。本次未强制全部迁移（风险高、易破坏业务布局），列为下一阶段建议

---

## 5. Icon（§10/§29）

验收清单：

- [x] 严格执行 `App → APP_ICONS → Icon.vue` 单一来源；`apps/icon-registry.ts` + `icon-registry.spec.ts`（3 例）校验所有 `APP_ICONS` 值均存在于 Icon.vue
- [x] Sidebar / 一级导航 / 二级 App 图标全部有效（经 `navigation.ts` 派生，落在 Icon.vue 210 定义内）
- [⚠️] 其他业务页面存在少量 Icon.vue 未定义的字面量图标名（如 `activity` / `network` / `square` 等）及 3 处 iconfont 依赖，会渲染空白；属 P1 残留，待下一阶段按页修复

---

## 6. Typography（§18–§19）

验收清单：

- [x] `ui-tokens.css` 提供语义层级：`kb-text-page-title / section-title / card-title / body / secondary / caption`，全部引用 `--kb-fs-*` / `--kb-lh-*` 令牌
- [🟡] 旧页面仍有 `15px / 17px / 19px` 等非阶梯硬编码字号；已提供语义类，待各页面逐步替换（不强制本阶段全量扫改）

---

## 7. Button（§20–§21）

验收清单：

- [x] `Button.vue` 统一变体：`primary / secondary / text / ghost / danger / iconOnly`
- [🟡] 尺寸 Small/Medium/Large 统一需各页面自查；本次未强制全量替换散落原生 `<button>`

---

## 8. Card（§22–§23）

验收清单：

- [x] 令牌体系已建立（`--kb-radius-sm` / `--kb-border` / `--kb-card` / `--kb-muted`）
- [🟡] 各页面卡片圆角/边框/padding 收敛待逐页复核（§23「视觉安静」原则）

---

## 9. Responsive（§31–§33）

验收清单：

- [x] 桌面 `>=1024px`：Sidebar 常驻 240/64px，Main 居中 `max-width: 1280px`，仅 Main 滚动
- [x] 移动 `<1024px`：Sidebar 转抽屉 + 底部导航（最多 5 个 `mobilePrimary` 入口），避免手机塞完整侧栏
- [⚠️] 390px 真机视觉复核未做（无浏览器）

---

## 10. Dark Mode（§34）

验收清单：

- [x] 全部颜色走 `--kb-*` 令牌，深色模式自动跟随（`--kb-sidebar` / `--kb-foreground` / `--kb-border` 等）
- [⚠️] 深色模式真实对比度/图标可见性未做真机复核

---

## 11. 修改文件

见 `UI-UX-3.3.1-执行报告.md` §11。

---

## 12. 自动化测试

四门验证（§41）：

- Vitest：**230/230** ✅
- TypeScript（vue-tsc -b）：**0 errors** ✅
- Vite Build：**PASS** ✅
- Backend（mvn -o test）：**71/71 BUILD SUCCESS** ✅

新增/扩展测试：CSidebar.spec（11）、CLayout.spec（6）、icon-registry.spec（3）、usePageNavigation.spec（9）。

---

## 13. 浏览器验收（§35–§38，§43）

> ⚠️ **Not verified** — 本执行环境无可用的真实浏览器引擎（agent-browser 未安装；`node_modules` 中无 playwright/playwright-core Chromium 缓存；沙箱 Chromium 历史白屏不可靠）。按 §43 硬规则，纯视觉/交互层不予伪称通过。

实际达成的验证手段（强于纯读源码，但非像素级浏览器验收）：

- 组件级真实 DOM 行为测试（happy-dom）：折叠宽度切换、rail 渲染、active 指示条、tooltip `:title`、持久化 F5、分组点击直达、返回 fallback 链路。
- 源码核查：CLayout 折叠联动机理、CSidebar 图标来源、icon-registry 校验。
- 类型检查 + 生产构建通过，确认无运行时语法/类型损坏。

待用户在本地浏览器复核项：§36 折叠完整操作流、§37 返回完整链路、§38 逐页视觉一致性。

---

## 14. Remaining Issues

1. 全站 PageHeader 复用率仅 9/81，自定义头部返回一致性需下一阶段统一。
2. 非导航业务页存在 Icon.vue 未定义图标名 + 3 处 iconfont 依赖（空白图标）。
3. 字体/按钮/卡片非阶梯硬编码值在旧页面残留，待逐页令牌化。
4. 390px 移动端、深色模式对比度、键盘可达性未做真机视觉复核。

---

## 15. 下一阶段建议

1. **3.3.2（PageHeader 统一）**：将 81 视图头部按「返回 + 标题 + 描述 + Actions」收敛到 `PageHeader`，逐个页面比对不破坏业务布局。
2. **图标清零**：扫描全站 `<Icon name>` 与 iconfont 依赖，补齐 Icon.vue 定义或替换为已定义图标，消除空白图标（P1）。
3. **令牌化扫尾**：用 `ui-tokens.css` 语义类替换旧页面硬编码字号/间距。
4. **真机视觉验收**：在本地浏览器对 §36/§37/§38 逐条截图复核，补齐 ⚠️ 项。
5. 完成上述后再评估是否进入 AI Coach / Agent / MCP / Tool Calling / Architecture-7（§45 本阶段 STOP）。
