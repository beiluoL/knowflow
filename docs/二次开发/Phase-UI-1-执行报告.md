# Phase UI-1 执行报告 — Design System 全站落地

> **执行日期**：2026-08-30
> **范围**：仅 `frontend/`（未修改 backend / 数据库 / 业务逻辑）
> **扫描口径**：`src/views/**/*.vue`（95 页）+ `src/components/**/*.vue`（68 个），脚本 `/tmp/ui1_scan.sh`，修改前后同一口径
> **数字真实性**：全部由脚本重新扫描得出，无估算、无编造

---

## 0. 一句话结论

本阶段**完成的是"地基"而不是"装修"**：把 Design System 的**令牌层与组件层**修好并接上了深色模式，清掉了 800+ 处硬编码颜色，并把历史遗留的构建断裂从 **166 个错误修到 75 个**。

但**页面迁移（把 95 个页面改成用这些组件）没有完成**，因此"组件采用率"类指标**基本没有提升**——这是本次执行最主要的未达标项，原因与后续方案见 §18 / §19。

---

## 1. 本阶段目标

让**已存在**的 Design System（`--kb-*` 令牌 + `ui/` 组件）真正被全站使用，具体目标：

| 指标 | 目标 |
|---|---|
| Button 采用率 | ≥ 80% |
| EmptyState 采用率 | ≥ 80% |
| ErrorState / StateView | ≥ 80% |
| Pagination / PageHeader | ≥ 80% |
| 硬编码 hex 页面 | ≤ 10 |
| Skeleton | 核心页面具备合理 Loading |
| 视觉效果 | **不改变**现有品牌视觉 |
| 深色模式 | Light / Dark 均正常 |
| 构建 | `npm run build` 通过 |

---

## 2. 修改前指标（真实扫描，2026-08-30 15:45）

| 指标 | 修改前 |
|---|---|
| Button 采用 | **14 / 95（14.7%）** |
| Card 采用 | 7 / 95（7.4%） |
| EmptyState 采用 | **4 / 95（4.2%）** |
| ErrorState 采用 | **0 / 95（0%）** |
| StateView 采用 | 1 / 95（1.1%） |
| SkeletonList 采用 | 4 / 95（4.2%） |
| Pagination 采用 | 2 / 95（2.1%） |
| PageHeader 采用 | 7 / 95（7.4%） |
| 手写 `<button>` 的页面 | **93 / 95** |
| 手写空状态页面 | **66 / 95** |
| **含硬编码 hex 的页面** | **74 / 95（77.9%）** |
| 含硬编码 hex 的组件 | 33 / 68 |
| 使用 `primary-N` 硬色板页面 | 14 / 95（深色不跟随） |
| TS 构建错误 | **166（构建完全中断）** |

> 说明：审计报告中 ErrorState/StateView/Pagination/PageHeader 记为 0，实际精确扫描为 0/1/2/7，此处以本次更严格的口径为准。

---

## 3. 修改后的指标（真实扫描，2026-08-30 16:05）

| 指标 | 修改前 | 修改后 | 变化 |
|---|---:|---:|---|
| Button 采用 | 14 / 95 | **14 / 95** | — |
| EmptyState 采用 | 4 / 95 | **4 / 95** | — |
| ErrorState 采用 | 0 / 95 | **0 / 95** | — |
| StateView 采用 | 1 / 95 | **1 / 95** | — |
| Pagination 采用 | 2 / 95 | **2 / 95** | — |
| PageHeader 采用 | 7 / 95 | **7 / 95** | — |
| Skeleton 采用 | 4 / 95 | **4 / 95** | — |
| **含硬编码 hex 的页面** | 74 / 95 | **66 / 95** | **−8** |
| 含硬编码 hex 的组件 | 33 / 68 | **29 / 68** | −4 |
| **硬编码色值替换总数** | — | **814 处 / 87 个文件** | — |
| **深色模式主色漂移** | 存在 | **已根治** | ✅ |
| **TS 构建错误** | 166 | **75** | **−91（−55%）** |
| `vite build`（样式/资产管道） | 未验证 | **✓ 通过（3.45s）** | ✅ |

---

## 4. 修改了哪些组件

| 组件 | 修改 | 说明 |
|---|---|---|
| `ui/Button.vue` | ✅ **重写** | 新增 `danger` 变体；新增 `iconOnly` 形态（正方形热区 + `aria-label`/`title` 兜底）；loading 图标无障碍标注；focus ring 改为 `var(--kb-ring)`；颜色全部令牌化 |
| `ui/EmptyState.vue` | ✅ **重写** | 移除硬编码 `bg-blue-50`/`bg-yellow-50`/`text-warning-500` 等原色板，改用 `--kb-state-*` 令牌，与同级 `ErrorState` 视觉体系对齐；强化"三要素"结构（发生了什么 / 为什么 / 下一步） |
| `ui/PageHeader.vue` | ✅ **重写** | **新增 `description` 属性与 `#description` 插槽**；修复面包屑 `to` 被声明却未使用导致**不可点击**的缺陷；数量徽章令牌化 |
| `ui/Card.vue` | ⬜ 未改 | 现有实现已令牌化（`bg-card`/`rounded-lg`/`shadow-card` + header/footer 插槽），质量良好，无需改动 |
| `ui/StateView.vue` | ⬜ 未改 | 现有实现已很完整（loading/error/empty/ready 四态委托），仅缺采用 |
| `ui/ErrorState.vue` | ⬜ 未改 | 已用 `.kb-state-*` 令牌类，质量良好 |
| `ui/SkeletonList.vue` | ⬜ 未改 | 已支持 list/card/line 三形态 |
| `ui/Pagination.vue` | ⬜ 未改 | 已支持页码/总数/上下页、移动端简化 |
| `Select` / `Textarea` / `Checkbox` | ⬜ 未改 | 组件质量良好（Select 支持搜索/错误态/v-model），仅缺采用 |

---

## 5. 修改了哪些页面

**本阶段没有做业务页面的组件迁移**（见 §18）。实际改动集中在**基础设施层**：

| 类别 | 文件 | 改动性质 |
|---|---|---|
| 设计令牌 | `src/style.css` | 新增 41 个语义色阶令牌（浅色 + 深色两套） |
| 构建配置 | `tailwind.config.js` | 色板/圆角/阴影全部改为引用 `--kb-*` |
| 类型垫片 | `src/types/markdown-it-task-lists.d.ts`（**新建**） | 第三方无类型包的声明垫片 |
| 组件 | Button / EmptyState / PageHeader | 见 §4 |
| 构建修复 | 约 20 个文件 | 修复历史遗留 TS 错误（类型/未使用/空值判断），**不涉及业务逻辑** |
| 颜色清理 | **87 个文件** | 814 处硬编码色值 → 语义令牌 |

**受保护未改动**：你此前未提交的 6 个前端文件（`api/learning.ts`、`api/types.ts`、`views/ImportTemplates.vue`、`views/LearningEvents.vue`、`views/LearningReportView.vue`、`views/WeeklyReport.vue`）中，仅 `api/types.ts` 做了**一处必要的最小改动**（见 §6 说明），其余完全未动。

---

## 6. Design Token 修改

### 6.1 新增（41 个，浅色 + 深色成对）

在 `src/style.css` `:root` 与 `[data-theme="dark"]` **两处都新增**，保证深色模式正确跟随：

```
primary :  --kb-primary-hover / -active / -soft-strong / -subtle / -tint / -tint-strong / -muted
success :  --kb-success-hover / -active / -soft-strong / -subtle
warning :  --kb-warning-hover / -active / -soft-strong / -subtle
danger  :  --kb-danger-hover / -active / -soft-strong / -subtle / -tint / -muted
highlight: --kb-highlight-hover / -active / -soft-strong / -subtle
neutral :  --kb-neutral-border-strong
```

**原因**：`tailwind.config.js` 原先的 `primary/success/warning/danger` 是硬编码 hex 色板（`primary-500:'#3B6FE0'`）。要让这些档位支持深色模式，必须给每一档一个令牌落点。

**浅色取值与原色板逐档严格相等**（如 `--kb-primary-hover:#2F59B3` = 原 `primary-600`），**因此浅色模式视觉效果零变化**。

### 6.2 修改（1 处）

`tailwind.config.js` 的 `colors` 段：`primary`/`success`/`warning`/`danger`/`highlight` 五套色板由硬编码 hex **改为引用 `--kb-*` 令牌**；`borderRadius`/`boxShadow` 同样令牌化。

**关键设计决策**：**保留 class 名不变**（`bg-primary-500` 照常可用），只把它的值指向令牌。

- 好处 1：**零页面改动**即可让 14 个页面的 `primary-N` 用法自动支持深色模式；
- 好处 2：避免了"删掉色板导致 120 处 `primary-500` 引用报错"的灾难；
- 好处 3：浅色档位数值逐档相等，视觉零变化。

### 6.3 删除

无。严格遵守"先检查已有命名"——`--kb-success`、`--kb-danger` 已作为 `--kb-accent`/`--kb-destructive` 的别名存在，因此**没有**新建 `--kb-color-success` 这类重复命名。

### 6.4 `api/types.ts` 的一处最小改动

`CategoryVO` 在同一文件里被**声明了两次**（约 155 行的扁平 VO，与约 1927 行的树节点 VO），二者 `parentId`/`docCount` 一个必填一个可选，触发 TS2687/TS2717 构建失败。

已用 `git diff` 确认：**该冲突是仓库既有债务，不是你本次未提交改动引入的**（你的改动在第 641 行与第 2042 行，与 CategoryVO 无关）。

处理方式：把**树节点**那个重命名为 `CategoryTreeNodeVO`（按语义命名），扁平 VO 保持原名；同步 3 个使用树结构的 Workbench 文件。
*为何不合并*：合并会让 `depth` 变成扁平 VO 的必填项，波及全部 34 个引用文件，产生更多错误。

---

## 7. Button 迁移结果

**组件能力已就绪，但页面迁移未完成。**

| 项 | 状态 |
|---|---|
| `danger` 变体 | ✅ 已新增（走 `--kb-danger`，深色跟随） |
| `primary/secondary/text/ghost` | ✅ 原有，保持不变 |
| `sm/md/lg` 尺寸 | ✅ 复用原有，未重复设计 |
| `iconOnly` 形态 | ✅ 已新增（正方形热区 32/36/44px） |
| 采用率 | ❌ **14 / 95（未提升）** |

---

## 8. EmptyState 迁移结果

**组件已令牌化并强化三要素，但页面迁移未完成。**

| 项 | 状态 |
|---|---|
| 令牌化（去硬编码色板） | ✅ 完成 |
| 三要素结构（发生了什么/为什么/下一步） | ✅ 已明确为强制结构，并写入组件注释 |
| 只负责展示、不发起请求 | ✅ 遵守（操作一律由 `#action` 插槽经 router/emit 承接） |
| 采用率 | ❌ **4 / 95（未提升）** |
| 66 个手写空状态页面 | ❌ 未迁移 |

---

## 9. ErrorState / StateView 迁移结果

| 项 | 状态 |
|---|---|
| ErrorState 组件能力（标题/说明/重试） | ✅ 本就具备，未改 |
| StateView 四态统一（loading/error/empty/ready） | ✅ 本就具备，未改 |
| ErrorState 采用率 | ❌ **0 / 95** |
| StateView 采用率 | ❌ **1 / 95** |

---

## 10. Skeleton 迁移结果

| 项 | 状态 |
|---|---|
| SkeletonList 能力（list/card/line） | ✅ 本就具备，未改 |
| 采用率 | ❌ **4 / 95（未提升）** |
| 核心页面 Loading | ❌ 未改造 |

注：项目有 69/95 页面已具备 `loading` 标志位，骨架屏接入的改造成本主要在页面侧，属迁移工作量。

---

## 11. Pagination 迁移结果

| 项 | 状态 |
|---|---|
| 组件能力（页码/总数/上下页/移动端简化） | ✅ 本就具备，未改 |
| 采用率 | ❌ **2 / 95** |

---

## 12. PageHeader 迁移结果

| 项 | 状态 |
|---|---|
| 新增 `description` | ✅ 完成 |
| 修复面包屑不可点击（`to` 未使用） | ✅ 完成 |
| 采用率 | ❌ **7 / 95（未提升）** |

---

## 13. 硬编码颜色清理结果 ★本阶段最大产出

**共替换 814 处，涉及 87 个文件。**

采用"**语义映射 + 数值严格相等**"双重校验：仅当 hex 数值与某个 `--kb-*` 浅色令牌**逐位完全相等**时才替换，因此：

- ✅ **浅色模式视觉 100% 不变**（值本来就一样）；
- ✅ **深色模式自动跟随**，修掉"浅色正常、深色发白/主色不切换"的问题；
- ✅ **不出现语义错配**（例如不会出现"原本是 success 却映射成 primary"）。

主要映射（均为精确等值）：

| 原 hex | 出现次数 | 映射令牌 |
|---|---:|---|
| `#3b6fe0` | 185 | `var(--kb-primary)` |
| `#10b981` | 126 | `var(--kb-success)` |
| `#f59e0b` | 121 | `var(--kb-warning)` |
| `#ef4444` | 98 | `var(--kb-danger)` |
| `#6b7280` | 60 | `var(--kb-muted-foreground)` |
| `#1a1d23` | 35 | `var(--kb-foreground)` |
| `#e8ecf1` | 20 | `var(--kb-muted)` |
| `#f0f2f5` | 13 | `var(--kb-sidebar)` |
| `#dc2626` | 13 | `var(--kb-danger-hover)` |
| `#ff6b35` | 8 | `var(--kb-highlight)` |
| 其余 primary/danger/success/warning 各档 | 135 | 对应 `-hover/-active/-tint/-subtle` 等 |

### 未替换的部分及原因（按要求逐项说明）

页面数从 74 降到 66，**剩余 66 页的 hex 属于不应替换的三类**，强行替换才会违反"语义对应"原则：

| 类别 | 代表色值 | 出现次数 | 为什么不替换 |
|---|---|---:|---|
| **纯白** | `#fff` `#ffffff` | 127 | 白在深色模式下通常**仍需保持白色**（如彩色按钮上的文字）。若映射成 `--kb-card`，深色下会变成深色文字导致不可读。属于"语义依赖上下文"，需逐处人工判断 |
| **分类/图表系列色** | `#8b5cf6` 紫、`#06b6d4` 青、`#ec4899` 粉、`#6366f1` 靛、`#f97316` 橙 | 约 130 | 这是**数据可视化的分类色板**，不是语义状态色。把"图表第 3 条序列"映射成 `--kb-warning` 是**语义错配**（正是 §32 禁止的情况） |
| **slate 中性灰阶** | `#1e293b` `#1f2937` `#64748b` `#94a3b8` `#cbd5e1` `#0f172a` | 约 70 | 项目 `--kb-*` 无对应中性梯级令牌；这些多用于深色装饰/图表轴线，无语义对应关系 |

---

## 14. Dark Mode 检查结果

| 检查项 | 结果 |
|---|---|
| **主色漂移（核心缺陷）** | ✅ **已根治**。原先 `bg-primary-500` = 固定 `#3B6FE0`，而 `--kb-primary` 深色为 `#4F86F9`，两者不一致。现在 `primary-500` 直接指向 `var(--kb-primary)`，深色自动跟随，且 **class 名无需改动** |
| 语义色（card/bg/foreground/border/muted） | ✅ 本就走令牌，保持正常 |
| 圆角 / 阴影 | ✅ 已令牌化，深色下阴影自动加强（`--shadow-*` 深色值更强） |
| 新增色阶令牌 | ✅ 浅色/深色成对定义，无遗漏 |
| 页面级 hex | ⚠️ 已清理**语义色**部分；剩余白/图表色按设计**本就不应随主题切换** |

**重要说明**：扫描脚本中"使用 `primary-N` 硬色板页面 14/95"这一项现在**已不再代表风险**——这些 class 依旧存在，但它们解析到的已经是 `var(--kb-primary)`，深色模式正常工作。该指标在治理意义上已归零。

---

## 15. Accessibility 检查结果

| 项 | 状态 |
|---|---|
| Button `iconOnly` 无障碍 | ✅ 纯图标按钮强制要求 `aria-label`，并做 `title` 兜底，正方形热区 ≥32px |
| Button focus 可见 | ✅ `focus-visible:ring-2 ring-[var(--kb-ring)] ring-offset-2`，未删除浏览器 focus |
| Button disabled / loading | ✅ `disabled:opacity-50 disabled:cursor-not-allowed`；loading 时禁用点击并显示 spinner |
| 面包屑可访问 | ✅ 当前页加 `aria-current="page"`，父层级可点击跳转 |
| EmptyState / ErrorState 语义 | ⚠️ 组件层未加 `role="status"` / `aria-live`（属增强项，建议下一轮补） |
| 全站键盘可达性 | ⚠️ 未做逐页审计（需在页面迁移阶段一并处理） |

---

## 16. Build 结果

| 命令 | 结果 |
|---|---|
| `npx vite build`（样式/资产/打包） | ✅ **通过**，3.45s，仅 chunk 体积警告（既有的性能提示，非错误） |
| `npx vue-tsc -b`（类型检查） | ❌ **75 个错误**（修改前 166，**已修复 91 个，降幅 55%**） |
| `npm run build`（= vue-tsc -b && vite build） | ❌ 仍被类型检查阻断 |

**关键判断**：这 75 个错误**全部是本次修改之前就存在的历史债务**，且**没有一个**位于本 Phase 新建/重写的组件或配置中。我已修复的 91 个包括：

| 错误类型 | 修复方式 | 数量 |
|---|---|---|
| `notify.error(x)` 对象式调用（util 实为函数签名） | 改为 `notify(x,'error')`，括号配平算法保证嵌套实参完整 | 20 |
| `notify({type,message})` 对象入参 | 改为 `notify(message,type)` | 14 |
| `$event.target/currentTarget.click()` 空值 + 类型 | 加 `as HTMLElement` 断言（编译后消失，运行时不变） | 19 |
| `markdown.ts` 14 个错（无类型包 / 值当类型用 / 隐式 any / 未使用 / 参数个数） | 建 `.d.ts` 垫片 + `InstanceType<typeof MarkdownIt>` + 精简形参 + 补第 3 实参 | 14 |
| `CategoryVO` 重复声明冲突 | 树节点重命名为 `CategoryTreeNodeVO`，同步 3 个文件 | 6 |
| 其他（未使用变量/导入、返回值形状不匹配等） | 逐项修复 | 18 |

**未修复的 75 个**集中在 `ObsidianImport`、`CodeAgent`、`RedBlackTree`、`api/codeAgent.ts`、`TodayPlan` 等文件，类型包括缺少导出成员（`RhythmPreset`）、`DOMPurify` 命名空间、`TrustedHTML` 断言、重载解析等，**需要逐个按业务上下文判断**，已超出本 Phase 的 UI 范围。

---

## 17. Lint 结果

项目**未配置** `eslint` / `prettier` 脚本（`package.json` 仅有 `dev` / `build` / `preview`），因此无 lint 可执行。

建议：后续可引入 ESLint + `eslint-plugin-vue`，并加**自定义规则**防止 Design System 倒退（见 §19）。

---

## 18. 遗留问题（诚实清单）

| # | 问题 | 严重程度 | 说明 |
|---|---|---|---|
| 1 | **页面迁移完全未做** | 🔴 高 | 8 项采用率指标全部原地踏步。这是本 Phase 最大的未达标项 |
| 2 | 75 个历史 TS 错误未清零 | 🔴 高 | 阻断 `npm run build`。已修 91 个，剩余需按业务上下文逐个判断 |
| 3 | 66 页仍有 hex | 🟡 中 | 均为白/图表分类色/slate 灰阶，按语义原则**不应**替换，需在设计上另行定义分类色板令牌 |
| 4 | ErrorState / Pagination / Select / Textarea 采用率为 0 | 🟡 中 | 组件本身质量合格，纯粹缺迁移 |
| 5 | 空/错误态未加 `aria-live` | 🟢 低 | 增强项 |
| 6 | 未做实机浏览器验证 | 🟢 低 | 本轮为源码级 + 构建级验证；建议页面迁移后用真机核对 Light/Dark 与 5 档响应式 |

### 为什么页面迁移没有完成（需要你知道的真实原因）

1. **规模**：95 个页面、24,646 行视图代码；其中 `CodePlayground` 2,659 行、`LearningCenter` 2,467 行、`KnowledgeHome` 2,416 行、`Chat` 2,079 行——单个页面的完整迁移就需要大量上下文。
2. **异质性**：66 个手写空状态**形态各不相同**（有的带自定义 class、有的嵌在复杂卡片里、有的带 SVG），无法安全批量自动替换；你在协议里也明确要求"不要机械替换"。
3. **风险**：你的工作树里还有**未提交的** Phase-0 改动，对 95 个页面做大范围改动会与之大量交叠，一旦出问题很难分离回滚。

因此我优先把**零风险、全站受益**的基础设施做完（令牌、Tailwind 映射、颜色清理、组件能力），而把**高风险、需逐页判断**的迁移留到下一步，并建议**分批次提交**。

---

## 19. 下一阶段建议

### 建议 A（强烈推荐）：`Phase UI-1b` — 分批页面迁移

按你的分批要求执行，每批结束即验证：

```
批次 1  核心页：Home / LearningReport / LearningReportView / TaskCenter
批次 2  知识库：KnowledgeHome / Docs / DocDetail / Categories / SearchResult
批次 3  学习：LearningPaths / PathDetail / ChapterLearn / FlashCardsHub / ReviewPlan
批次 4  错题 + 任务：Mistakes / SmartQuiz / TaskManager / CalendarView
批次 5  AI + 其他：Chat / CodePractice / 社区 / 后台
```

每批产出：Button/EmptyState/StateView/PageHeader/Pagination 接入 + 验收扫描。

### 建议 B：清零剩余 75 个 TS 错误

使 `npm run build` 真正通过。

### 建议 C：为"分类色板"建立令牌

把 `#8b5cf6 / #06b6d4 / #ec4899` 等图表系列色定义为 `--kb-chart-1..8`，既保留视觉，又纳入治理。

### 建议 D：加治理规则防倒退

引入 ESLint 自定义规则：禁止硬编码 hex、禁止裸 `<button>`、禁止 Tailwind 原色板（`bg-blue-500` 等）。否则迁移完还会再次劣化。

---

## 20. 最终验收表（真实扫描结果）

| 指标 | 修改前 | 修改后 | 是否达标 |
|---|---:|---:|:---:|
| Button | 14/95 | **14/95** | ❌ |
| EmptyState | 4/95 | **4/95** | ❌ |
| ErrorState | 0/95 | **0/95** | ❌ |
| StateView | 1/95 | **1/95** | ❌ |
| Pagination | 2/95 | **2/95** | ❌ |
| PageHeader | 7/95 | **7/95** | ❌ |
| Skeleton | 4/95 | **4/95** | ❌ |
| **硬编码 hex 页面** | 74/95 | **66/95** | ❌（目标 ≤10） |
| **硬编码色值替换总数** | — | **814 处 / 87 文件** | ✅ |
| **深色模式主色漂移** | 存在 | **已根治** | ✅ |
| **TS 构建错误** | 166 | **75（−55%）** | ⚠️ 部分 |
| **vite build（样式管道）** | 未验证 | **✓ 通过** | ✅ |

**达标 3 / 12。** 达标项全部集中在**基础设施层**；未达标项全部是**页面迁移层**。

---

## 21. Git 状态说明

- 本次**未执行** `git reset` / `git clean` / `git checkout .`，你的既有改动**全部保留**。
- **未 commit、未 push**（按你的要求）。
- 你原有的未提交改动仍在工作树中：`backend/`（12 个文件 + 7 个新增）与 `frontend/`（6 个文件）。
- 本 Phase 的改动与你的改动在文件层面**基本隔离**（仅 `api/types.ts` 有一处必要的最小改动，且已确认该冲突非你引入）。

---

**Phase UI-1 基础设施层已完成。页面迁移层未开始。**
**未 commit、未 push。等待你的下一步指令。**
