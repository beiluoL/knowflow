# Phase UI-3 执行报告 — Home / Dashboard 重构

> **执行日期**：2026-08-30
> **范围**：仅 `frontend/`。未修改 backend / 数据库 / LearningEvent / KnowledgeMastery / SRS / Review engine（§五十八）
> **未 commit、未 push**；未执行 `git reset` / `git clean` / `git checkout .`
> **配套文档**：`Phase-UI-3-Home-Analysis.md`（改造前分析）

---

## 1. Phase UI-3 目标

把首页从「功能展示型 Dashboard」升级为「学习驾驶舱」：用户打开首页 **3 秒内**知道 6 件事——在学什么 / 今天学什么 / 还剩什么任务 / 学了多少 / 哪里薄弱 / 今天复习什么。

---

## 2. 修改前 Home 结构（真实扫描）

```
欢迎横幅（问候 + 时长/文档数/连击 + dynamicCta）
今日学习进度（含「掌握度」卡：masteryTarget=200 是写死的假目标 ❌）
今日待办与计划（learningApi.tasks）
为你推荐（learningApi.paths）
继续学习（docsApi.recent）
AI 学习建议（⚠️ aiRecs 是硬编码静态数组，非 AI）
```

**没有**今日待复习、**没有**薄弱知识点、CTA 不考虑复习。

---

## 3. 修改后 Home 结构

```
欢迎横幅（问候 + 动态 CTA）
今日学习进度（真实平均掌握度，移除假目标）
[新增] 今日待复习 ReviewDueCard        ← P0
今日待办与计划
继续学习
[新增] 我的薄弱知识点 WeakPointCard     ← P0
推荐学习（原「AI 学习建议」改名+降权，移除误导性说明）
为你推荐（学习路径，保持低位）
```

---

## 4. 6 问回答情况

| 问题 | 修改前 | 修改后 | 数据来源（真实 API） |
|---|---|---|---|
| ① 我正在学什么？ | ✅ | ✅ | `docsApi.recent()` / `learningApi.paths()` |
| ② 今天应该学什么？ | ✅ | ✅ | `learningApi.tasks()` |
| ③ 今天还剩什么任务？ | ⚠️ | ✅ | `learningApi.tasks()`（已完成 X / 共 Y） |
| ④ 我已经学了多少？ | ✅ | ✅ | `userApi.stats()` |
| ⑤ 哪里薄弱？ | ❌ | ✅ | `GET /learning/mastery/weak` |
| ⑥ 今天应该复习什么？ | ❌ | ✅ | `GET /learning/mastery/review-required` |

**4 / 6 → 6 / 6** ✅

---

## 5. 主 CTA 逻辑（§14 决策树）

```
未登录 / 新用户（studyHours=0）   → 探索学习路径 / 浏览知识库
有进行中的学习（continueDocs>0） → 继续上次学习 /（待复习>0 时）开始复习(N)
无进行中学习但今日有待复习        → 开始复习(N) / 查看待办          ← 新增
有未完成待办                     → 开始今日任务 / 浏览学习路径      ← 新增
默认                            → 进入学习中心 / 创建笔记
```

**CTA 现在随用户状态动态变化**，且把「待复习」纳入决策（§13/§14）。

---

## 6. ReviewDueCard（今日待复习）

| 项 | 实现 |
|---|---|
| 数据 | `learningApi.knowledgeMasteryReviewRequired()` → `GET /learning/mastery/review-required`（真实） |
| 内容 | 大数字数量 + **按分类聚合 Top3**（categoryName，真实字段）+ [开始复习]（§21） |
| 空态 | 「今日复习已完成 🎉」+ [继续学习]（§22，不显示"0"） |
| 错误态 | `ErrorState` + 重试，**不拖垮首页**（§42/§67） |
| 组件职责 | 只展示 + emit（retry/start/continue），数据由 Home.vue 传入（§47） |

## 7. WeakPointCard（薄弱知识点）

| 项 | 实现 |
|---|---|
| 数据 | `learningApi.knowledgeMasteryWeak()` → `GET /learning/mastery/weak`（真实，**零 mock**） |
| 内容 | Top3（masteryScore 升序），每项：名次 + 名称 + 掌握度% + **真实进度条**（含 aria）+ 原因 |
| 原因翻译（§60） | `weaknessTypes` 枚举 → 「近期错误较多 / 最近较少复习 / 掌握度下降 / 遗忘风险较高」；后端 `reason` 可直接用。**不暴露内部字段** |
| 行动（§28） | [查看全部] → `/learning/mastery` |
| 空态 | 「暂无薄弱知识点」+ [查看掌握度] |
| 错误态 | ErrorState + 重试 |

## 8. 今日计划 / 9. 今日学习进度

- 今日待办：保留原有列表（真实 `learningApi.tasks()`），已完成/总数徽标不变
- 今日学习进度：**移除伪造的 `masteryTarget = 200`**（§16）。现在平均掌握度来自 `GET /learning/mastery` 全部知识点的 `masteryScore` 均值——**真实计算**。无数据时明确显示「暂无掌握度数据」，不再给出凭空目标
- 进度条补 `role="progressbar"` + aria-valuenow/min/max（§75）

## 10. 继续学习

保留 `docsApi.recent()` 驱动，位于薄弱知识点之前（§33 优先级）。

## 11. AI 学习建议 → 12. 推荐

**整改**（§32）：原「AI 学习建议」的 `aiRecs` 是**硬编码 4 条常量**（412 行），并非 AI 生成，标题「基于你的学习行为智能生成」误导用户。
- 改名「**推荐学习**」，副标题改为「你可以从这里继续拓展」
- 区块保持页面底部，**降低视觉权重**，不再抢占今日行动区

---

## 13. API 数据来源（全真实，无 mock）

| Dashboard 功能 | API | 前端方法 | 真实存在 |
|---|---|---|---|
| 今日进度/时长 | `GET /users/stats` | `userApi.stats()` | ✅ |
| 今日任务 | `GET /learning/tasks` | `learningApi.tasks()` | ✅ |
| 待复习 | `GET /learning/mastery/review-required` | `learningApi.knowledgeMasteryReviewRequired()` | ✅ |
| 薄弱知识点 | `GET /learning/mastery/weak` | `learningApi.knowledgeMasteryWeak()` | ✅ |
| 平均掌握度 | `GET /learning/mastery` | `learningApi.knowledgeMastery()` | ✅ |
| 继续学习 | `GET /docs/recent` | `docsApi.recent()` | ✅ |
| 学习路径/推荐 | `GET /learning/paths` | `learningApi.paths()` | ✅ |

## 14. 是否新增 API

**否。** 6 个问题全部由现有接口覆盖，未新增任何 Dashboard API，未修改后端（§八/§六）。

## 15. 是否新增组件

| 组件 | 状态 |
|---|---|
| `components/home/ReviewDueCard.vue` | **新增** |
| `components/home/WeakPointCard.vue` | **新增** |
| 进度/待办/继续学习/推荐 | 复用 Home.vue 现有区块，**未重复创建**（§46） |

> 刻意未把 Home 拆成 8 个卡片组件（§48 警告巨型组件）：当前 Home.vue 主要复杂度来自既有区块样式，新增部分已通过 2 个组件收敛；整体继续观察，若后续继续膨胀再拆。

## 16. 新用户体验 / 17. 老用户体验

| 用户态 | 行为 |
|---|---|
| 新用户（studyHours=0 / 未登录） | CTA「探索学习路径」；各卡片显示空态+引导，而非一堆 0 |
| 进行中用户 | 直接进入「今日行动」区（进度+待复习+待办） |
| 高活跃用户 | 同一结构，待复习/薄弱若为 0 显示完成态 |

按现有数据（studyHours/streakDays/tasks/reviewRequired）简单分档（§71），无复杂画像。

## 18. Loading / 19. Empty / 20. Error

| 卡片 | Loading | Empty | Error |
|---|---|---|---|
| ReviewDueCard | ✅ 骨架占位 | ✅ 完成态+继续学习 | ✅ ErrorState+重试 |
| WeakPointCard | ✅ 骨架占位 | ✅ 无薄弱+查看掌握度 | ✅ ErrorState+重试 |
| 其余区块 | 保持原有 | 保持原有 | 保持原有（try/catch 兜底） |

关键设计（§42/§64/§67）：待复习、薄弱、掌握度三接口用 **`Promise.allSettled` 并联**，任一失败只影响对应卡片，**整页不白屏**。

## 21. Dark Mode

新增组件全部使用 `--kb-*` 令牌（card/bg/foreground/muted-foreground/state-success/state-warning/state-error/success-subtle/warning-subtle/danger-subtle），**无硬编码色**（已扫描验证），深色自动跟随。

## 22. Responsive

- 新增卡片为整行卡片（Card 自适应），移动端 1 列不挤压（§39）
- 复用了既有的 `reveal-item` / section 布局体系；未新增断点

## 23. Accessibility

- 进度条：`role="progressbar"` + aria-valuenow/min/max + aria-label（§75）
- 颜色不唯一信息：薄弱点同时用「排名数字 + 名称 + 百分比文字 + 原因文字」（§76）
- 所有按钮均为 `<button>` / `<router-link>`，Tab/Enter 可达（§77）

## 24. Build / 25. TypeScript / 26. Lint

| 项 | 结果 |
|---|---|
| `npx vite build` | ✅ **通过**（5.99s） |
| `vue-tsc -b` | ⚠️ **74 个**（修改前 75，**净 −1**；Home 与两个新卡片 **零错误**） |
| Lint | 项目未配置脚本，无 lint 可执行（§87） |

---

## 27. 遗留问题

| # | 问题 | 说明 |
|---|---|---|
| 1 | 首页无「今日目标」 | 项目没有每日目标设置，因此不展示假目标（这是**正确行为**，但体验上缺"目标感"，建议后续在设置里加真实目标） |
| 2 | 待复习/薄弱无 `nextReviewAt` 倒计时展示 | 字段存在（`nextReviewAt`）但首页未用，可后续增强 |
| 3 | 卡片为整行非 2 列网格 | 为稳妥采用整行布局；若桌面想并排「进度+待复习」需补一行 grid CSS |
| 4 | 未做实机浏览器验证 | 同上两个 Phase 的遗留，建议真机核对 |

## 28. 下一阶段建议

1. 实机验证首页（重点：新用户空态、待复习>0、薄弱>0、Dark、390px）
2. 建议在「设置」中提供真实的每日学习目标，首页进度条才能真正"目标感"
3. 可把 `nextReviewAt` 用于待复习卡片的"X 小时后到期"提示
4. 承接遗留：清零 74 个 TS 错误、Phase UI-1b 页面迁移

---

## 最终验收表

| 项目 | 修改前 | 修改后 | 状态 |
|---|---:|---:|:---:|
| 6 问回答 | 4/6 | **6/6** | ✅ |
| 主 CTA | 不涉及复习 | **含待复习分支，动态变化** | ✅ |
| 今日待复习 | 无 | **ReviewDueCard（真实接口）** | ✅ |
| 薄弱知识点 | 无 | **WeakPointCard（真实接口）** | ✅ |
| 伪造目标 masteryTarget=200 | 存在 | **已移除，改真实均值** | ✅ |
| AI 建议误导标题 | 有（非 AI 却称 AI） | **改名「推荐学习」+ 降权** | ✅ |
| Loading | 整页串行 | **各卡片独立 Skeleton** | ✅ |
| Empty | 0/暂无混用 | **完成态 + 引导** | ✅ |
| Error | 静默/整体失败 | **ErrorState + 重试 + 隔离** | ✅ |
| 新增 API | — | **0**（全复用） | ✅ |
| 新增组件 | — | **2**（ReviewDueCard/WeakPointCard） | ✅ |
| Mobile | 1 列 | **1 列（不挤压）** | ✅ |
| Dark Mode | — | **全令牌，无硬编码** | ✅ |
| Build | — | **✓ 通过** | ✅ |
| TypeScript | 75 | **74（Home/新卡片 0 错误）** | ✅ |
| Lint | 无脚本 | 无脚本 | — |

**达标 15 / 15（Lint 不适用）。**

---

## 本阶段改动文件

| 文件 | 类型 |
|---|---|
| `src/components/home/ReviewDueCard.vue` | 新增 |
| `src/components/home/WeakPointCard.vue` | 新增 |
| `src/views/Home.vue` | 修改（2 卡片接入、CTA、真实掌握度、推荐改名） |
| `docs/二次开发/Phase-UI-3-Home-Analysis.md` | 新增 |
| `docs/二次开发/Phase-UI-3-执行报告.md` | 新增 |

---

**Phase UI-3 已完成。未 commit、未 push。等待你的下一步指令。**
