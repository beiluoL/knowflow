# UI/UX 2.1 · MiniApp Design（外壳设计规范）

> 统一 KnowFlow 所有 Mini App 的使用骨架。设计令牌继续沿用 `--kb-*`（§28/§124），不新增第二套 token 系统（§29-30/§125）。

## Shell
- 组件：`components/layout/MiniAppShell.vue`。
- 结构：`mini-app-shell`（flex column）> `mini-app-header` + `mini-app-body`。
- `mini-app-body` 无内边距，内容布局由 App 自己控制（§54-56）；不强制固定高度（§56）。
- 全部 `--kb-*`，深色自动跟随。

## Header（统一）
- 返回：`mini-app-back` 真实 `<button>`，`aria-label="返回{backLabel}"`，`focus-visible:ring`。
- 标题：`mini-app-title`（`--kb-fs-h2`/700/`--kb-foreground`）。
- 副标题：`mini-app-subtitle`（`--kb-fs-body-sm`/`--kb-muted-foreground`），仅存在时显示（§59）。
- 上下文：仅 `knowledgeName` 存在时显示（§100-101）。
- 动作：`mini-app-actions` slot，真实按钮，≤3 个 / 主操作 ≤1（§24-26）。

## Context（统一）
- 仅 `knowledgeName` 真实存在才渲染；无则不显示空条（§21-22）。
- 文案：`正在学习：<strong>{name}</strong>`。
- 清除：`clearableContext` 时显示 `mini-app-context-clear`（emit `clear-context`）。
- 不允许制造默认 Context（§68-69）。

## Actions（统一）
- 仅放真实业务操作（清除上下文 / 设置 / 更多），迁移自原 header 的真实按钮（§23/§37）。
- 富品牌 App：CTA 进 actions，编辑器工具条留内容（§191-194）。

## Content（自由）
- App 内部学习方法/布局完全自由（§41/§239）：闪卡还是闪卡、测验还是测验、费曼还是费曼（§240-246）。
- 画布/编辑器类（Knowledge Graph / Drawing / Mindmap / Notes / AI Writing）保持全屏/大区域（§49-53）。

## Result（复用，不强制）
- 学习型 App 复用既有 `LearningResultPanel`（§71-72）；工具/AI 型不强行（§73）。
- 结构：结果 / 奖励 / 学习状态 / 下一步；无真实数据项隐藏，不显示 0、不造假（§74-77）。
- 完成之后消费既有 NextAction，不新建 MiniAppNextAction（§82-84）。

## Mobile（390 / 768 / 1440）
- `mini-app-header` `flex-wrap`；`@media (max-width:640px)` 下 context `order:3` 整行折行（§130）。
- 内容区按 App 类型自适应宽度（文档/答题/Canvas/Chat 允许差异，§136-138），不用固定 600px 限制所有 App（§135）。
- 不横向溢出、CTA 不被遮挡（§128-129）。

## Dark（继续 --kb-*）
- 不硬编码 white/black/gray-*；外壳与全部 App 沿用既有 `--kb-*` 暗色变量（§139-141）。

## Accessibility（基础可用）
- Header 返回/操作=真实 `<button>`（§142）；导航=`router-link`（§143）；图标操作有 `title`/`aria-label`（§144-145）。
- 键盘：Tab / Enter / Esc 可用，聚焦环明显（§146-147）；禁用 clickable div 充当导航（§148）。

## 反模式（禁止）
- 不新建 `MiniAppShell2` / `AppShell2` / MiniApp Design Tokens V2（§30-31/§125）。
- 不在 Shell 注入 reward / mastery / nextAction / api / loading / error 业务 props（§204-206）。
- 不为统一而修改 App 核心学习逻辑（§4/§36-41）。
