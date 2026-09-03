# UI/UX 3.3.1 执行报告 — Visual Acceptance + Sidebar + 全局界面修复

> 阶段：UI/UX 3.3.1 · 一键实施（Vibe Coding）· 直接改码，本地 **未 commit / 未 push**
> 质量门：Vitest **230/230** · TypeScript **0 errors** · Vite Build **PASS** · Backend **71/71 BUILD SUCCESS**
> 配套验收文档：`UI-UX-3.3.1-Visual-Acceptance.md`
> 本阶段 **STOP**：不进入 AI Coach / Agent / MCP / Tool Calling / Architecture-7（§45）

---

## 1. 当前真实问题

3.3 报告明确：代码实现完成 ≠ 用户实际体验完成；3.3 全部浏览器验收为 ⚠️ Not verified。本阶段不新增业务功能，只做真实页面验收与修复。

扫描真实代码后确认的真实缺口（非假设）：

- 约 28 处非 App 顶层视图散写 `router.back()`；直接 URL / 刷新 / 书签进入时返回会把用户带离应用。
- 侧边栏折叠态：二级 App 图标不可见、分组标题点击是「死按钮」、宽度未真正切换、主内容未同步、不持久化、激活态仅浅背景、Tooltip 可能 `undefined`。
- 图标单一来源已建（`APP_ICONS` + Icon.vue），但旧业务页仍有未定义图标名与少量 iconfont 依赖。
- 令牌体系已建（`ui-tokens.css`），但各页仍有硬编码字号/间距/按钮/卡片。

---

## 2. Sidebar 改动（P0，§3–§12）

文件：`frontend/src/components/layout/CSidebar.vue`、`CLayout.vue`

- **折叠宽度真正切换**：`CSidebar.width` 240px ↔ 64px（`is-collapsed`）；`CLayout .clayout-main` `padding-left` 240px ↔ 64px，主内容区同步扩大。
- **折叠≠隐藏**：折叠态渲染图标 rail（`.csidebar-subrail-item`），二级 App 图标全部可见，`:title` 提供全称 Tooltip。
- **修死按钮（§9）**：折叠态点击分组标题由「切换不可见子项」改为直达工作区首页（`router.push(group.children[0].path)` + `emit('navigate')`）；展开态保持原切换行为。
- **防抖动（§7）**：`.csidebar-item` 加 `position: relative` + `overflow: hidden`，240→64 过渡期间裁切溢出文字，不飞出。
- **激活指示条（§12）**：`.csidebar-item.is-active::before` 主色左侧 3px 指示条（height 60%），`hover`/`active`/`focus-visible` 三态齐备。
- **持久化（§8）**：`CLayout` 读/写 `localStorage['csidebar-collapsed']`，`onMounted` 读取、`watch(collapsed)` 落盘；F5 保持，且仅写该键（测试断言 `keys === ['csidebar-collapsed']`，不污染 UserApp）。
- **折叠按钮真实可见**：`CSidebar` footer 内 `csidebar-collapse` 按钮，`title`/`aria-label` 随状态切换「收起/展开侧边栏」，footer `flex-shrink:0` + 顶部边框，不被内容挤出。

---

## 3. 返回体验改动（P0，§16）

文件：`usePageNavigation.ts` + 13 个独立顶层视图

- `goBack(fallback?: string)` 扩展：真实 history（`window.history.state.back`）→ `router.back()`；否则 `fallback`（且不等于当前路径）→ `router.push(fallback)`；再否则 `goToParent()`。
- 统一 13 个独立顶层视图的返回入口（裸 `router.back()` / `history.back()` 全部替换为 `nav.goBack(fallback)`）：
  - Certificate、Chat、DocCreate、DocDetail(`/docs`)、DocEdit(`/doc/:id`)、KnowledgeCreate(`/knowledge`)、KnowledgeUpload(`/knowledge`)、LearningPrefs、ObsidianImport、WeeklyReport、admin/ChapterEdit、PostEdit(`/community`)、PostDetail(`/community`)、KnowledgeMasteryDetail(`/learning/mastery`)。
- 文档链路返回正确：列表 → 详情 → 编辑 各自兜底到上一级，不粗暴回学习首页。
- 12 个 `MiniAppShell` 包裹的 App 视图本地 `goBack` 为死代码，返回已由 MiniAppShell 内部 `nav.goBack()` 接管，**不重复改造**（避免双重返回）。
- `WorkbenchNoteEdit` 保留「未保存内容 → confirmDialog 确认」特殊返回逻辑，按 §16 不破坏。

> 注：本会话接续上一轮，修复了上一轮批量脚本漏改的 3 个文件（PostEdit / PostDetail / KnowledgeMasteryDetail），并清理了批量脚本插入 import 时造成的语法损坏与未使用变量（`DocEdit` / `WeeklyReport` 的 `import {` 错位、`router` 未使用告警）。

---

## 4. PageHeader

文件：`frontend/src/components/ui/PageHeader.vue`

已统一结构：面包屑（可点击返回）、标题、数量徽标、描述区、右侧 Actions 插槽；全部令牌化。
现状：全站 81 视图仅 9 个复用（🟡 见 Remaining Issues）。本阶段未强制全量迁移。

---

## 5. Icon

文件：`frontend/src/apps/icon-registry.ts` + `apps/__tests__/icon-registry.spec.ts`

- 严格执行 `App → APP_ICONS → Icon.vue` 单一来源。
- `icon-registry.spec.ts`（3 例）校验所有 `APP_ICONS` 值均存在于 Icon.vue → 保证 Sidebar / App 图标无空白。
- 残留：少量旧业务页使用了 Icon.vue 未定义图标名（如 `activity`/`network`/`square` 等）及 3 处 iconfont 依赖，会渲染空白（⚠️ 待下阶段清零）。

---

## 6. Typography

文件：`frontend/src/styles/ui-tokens.css`

建立语义层级 `kb-text-page-title / section-title / card-title / body / secondary / caption` + 间距工具类（4px 基线），全部引用 `--kb-*` 令牌。
残留：旧页面非阶梯硬编码字号待逐页替换（🟡）。

---

## 7. Button

文件：`frontend/src/components/ui/Button.vue`

统一变体 `primary / secondary / text / ghost / danger / iconOnly` 已具备。
尺寸 Small/Medium/Large 全站统一待各页面自查（🟡）。

---

## 8. Card

令牌体系（`--kb-radius-sm` / `--kb-border` / `--kb-card` / `--kb-muted`）已建立。各页面卡片圆角/边框/padding 收敛待逐页复核（🟡，遵循 §23「视觉安静」）。

---

## 9. Responsive

`CLayout.vue`：桌面 `>=1024px` Sidebar 常驻 + Main `max-width:1280px` 居中 + 仅 Main 滚动；移动 `<1024px` Sidebar 转抽屉 + 底部导航（≤5 个 `mobilePrimary`）。390px 真机视觉未复核（⚠️）。

---

## 10. Dark Mode

全部颜色走 `--kb-*` 令牌，深色模式自动跟随。真实对比度/图标可见性未做真机复核（⚠️）。

---

## 11. 修改文件

**本阶段新增/修改（frontend）：**

| 文件 | 改动 |
|------|------|
| `components/layout/CSidebar.vue` | 折叠死按钮修复、防抖动、激活指示条 |
| `components/layout/CLayout.vue` | 折叠持久化（已含，本次核查确认） |
| `composables/usePageNavigation.ts` | `goBack(fallback?)` 上下文兜底 |
| `views/Certificate.vue` | 返回统一 + 清理未用 router |
| `views/Chat.vue` | 返回统一（上轮） |
| `views/DocCreate.vue` | 返回统一（上轮） |
| `views/DocDetail.vue` | 返回统一 `/docs` |
| `views/DocEdit.vue` | 返回统一 `/doc/:id` + 修复 import 错位 |
| `views/KnowledgeCreate.vue` | 返回统一 `/knowledge` |
| `views/KnowledgeUpload.vue` | 返回统一 `/knowledge` |
| `views/LearningPrefs.vue` | 返回统一 + 清理未用 router |
| `views/ObsidianImport.vue` | 返回统一（上轮） |
| `views/WeeklyReport.vue` | 返回统一 + 清理未用 router + 修复 import 错位 |
| `views/admin/ChapterEdit.vue` | 返回统一 + 清理未用 router |
| `views/PostEdit.vue` | 返回统一 `/community`（本会话） |
| `views/PostDetail.vue` | 返回统一 `/community`（本会话） |
| `views/KnowledgeMasteryDetail.vue` | 返回统一 `/learning/mastery` + 清理未用 router（本会话） |

**本阶段新增测试 / 资源：**

| 文件 | 说明 |
|------|------|
| `components/layout/__tests__/CSidebar.spec.ts` | 11 例（折叠/rail/active/持久化/死按钮修复） |
| `components/layout/__tests__/CLayout.spec.ts` | 6 例（折叠持久化 + 不污染 UserApp） |
| `composables/__tests__/usePageNavigation.spec.ts` | 9 例（返回 fallback 链路） |
| `apps/icon-registry.ts` + `apps/__tests__/icon-registry.spec.ts` | 图标单一来源校验（3 例） |
| `styles/ui-tokens.css` | 字体/间距语义令牌（上轮建立，本次核查） |

**未改动（保持）：** `apps/registry.ts`、`config/navigation.ts`、`apps/navigation.ts`、`MiniAppShell.vue`、`learning/route.ts`、`main.ts`、`WorkbenchNoteEdit.vue`、所有 `MiniAppShell` 包裹视图的本地 `goBack`。

---

## 12. 自动化测试（§41，四门验证）

| 门 | 命令 | 结果 |
|----|------|------|
| Vitest | `./node_modules/.bin/vitest run` | **230/230** ✅（19 文件） |
| TypeScript | `vue-tsc -b` | **0 errors** ✅ |
| Vite Build | `vite build` | **PASS** ✅ |
| Backend | `mvn -o test`（JDK17 / Maven 3.9.16） | **71/71 BUILD SUCCESS** ✅ |

扩展测试覆盖（§42）：Sidebar 折叠/展开/持久化/折叠态 App 导航/Tooltip/Active、返回 Workspace/App/Deep/Direct-URL fallback、图标 Registry 生效。

自动修复轮次：本会话第 1 轮发现批量脚本 import 错位 + 未用变量（TS6133），已修复并复跑通过（共 1 轮实质修复）。

---

## 13. 浏览器验收（§35–§38，§43）

> ⚠️ **Not verified** — 执行环境无可用的真实浏览器引擎（agent-browser 未安装；`node_modules` 无 playwright/playwright-core Chromium 缓存；沙箱 Chromium 历史白屏不可靠）。依据 §43 硬规则，纯视觉/交互层不伪称通过。

真实达成的验证（强于纯读源码，非像素级）：

- happy-dom 组件测试覆盖折叠宽度切换、图标 rail、active 指示条、Tooltip `:title`、F5 持久化、分组点击直达、返回 fallback 链路。
- 源码核查 CLayout 折叠联动、CSidebar 图标来源、icon-registry 校验。
- 类型检查 + 生产构建通过（无语法/类型损坏）。

待用户本地浏览器复核：§36 折叠完整操作流、§37 返回完整链路、§38 逐页视觉一致性。

---

## 14. Remaining Issues

1. PageHeader 复用 9/81，自定义头部返回一致性待下阶段统一。
2. 旧业务页存在 Icon.vue 未定义图标名 + 3 处 iconfont 依赖（空白图标）。
3. 旧页面硬编码字号/间距/按钮尺寸/卡片样式残留，待令牌化。
4. 390px 移动端、深色模式对比度、键盘可达性未做真机视觉复核。

---

## 15. 下一阶段建议

1. **3.3.2 PageHeader 统一**：81 视图头部收敛到 `PageHeader`（返回+标题+描述+Actions），逐页比对不破坏业务布局。
2. **图标清零（P1）**：扫描全站 `<Icon name>` 与 iconfont，补齐 Icon.vue 或替换，消除空白图标。
3. **令牌化扫尾**：用 `ui-tokens.css` 语义类替换旧页面硬编码。
4. **真机视觉验收**：本地浏览器对 §36/§37/§38 逐条截图复核，补齐 ⚠️ 项。
5. 上述完成后才评估进入 AI Coach / Agent / MCP / Tool Calling / Architecture-7。

---

## STOP

本阶段已按 §45 停止。未 commit / 未 push（本地个人项目，遵循用户硬规则）。所有改动留在工作区，待用户本地浏览器复核后自行决策。
