# KnowFlow UI/UX V1.0 测试与浏览器验证报告

- 日期：2026-10-28
- 环境：本地开发（前端 `http://localhost:5173` Vite，后端 `:8080`），服务经 `./knowflow-dev.sh restart` 重启加载新 App 白名单
- 账号：`seed-personal-v1`（userId 4，ADMIN，昵称「课程固化验收用户」）
- 数据源：`backend/config/datasource.json` 指向本地 file H2（`rebuild-v1-host-test-20261012`），未改正式数据库

---

## 1. 自动化测试

| 项目 | 命令 | 结果 |
|---|---|---|
| 前端单元 / 组件测试 | `cd frontend && npx vitest run` | **241 个文件 / 1768 个用例全部通过**，退出码 0（改动后共跑 2 次，末次 29.65s） |
| 类型检查 | `cd frontend && npx vue-tsc --noEmit` | 退出码 0，无类型错误 |
| 生产构建 | `cd frontend && npm run build` | 退出码 0，`✓ built in 5.53s`；仅既有 chunk >500kB 提示 |
| 后端 App 相关集成测试 | `mvn -Dtest='UserAppIntegrationTest,ExamAppInstallIntegrationTest,PurchaseAppDisabledIntegrationTest' test` | **10 个用例通过**（5 + 4 + 1，0 failure / 0 error），退出码 0 |

未运行后端全量 `mvn test`：本轮后端唯一改动是 `OfficialAppRegistry` 的 5 个 key 登记，已用上述 3 个 App 管理 / 安装 / 停用相关集成测试覆盖，未触碰业务逻辑。

---

## 2. 浏览器多视口验证（修改后）

工具：Playwright（Chromium for Testing，headless），以既有登录态注入 localStorage 复用会话；截图落盘 `docs/ui-ux/v1/screenshots/`。

### 2.1 视口矩阵（26 张）

| 页面 | 1440×900 | 1280×800 | 1024×800 | 768×1024 | 390×844 |
|---|---|---|---|---|---|
| `/` | ✅ | ✅ | ✅ | ✅ | ✅ |
| `/apps` | ✅ | ✅ | ✅ | ✅ | ✅ |
| `/chat` | ✅ | ✅ | ✅ | ✅ | ✅ |
| `/learning/center` | ✅ | — | — | — | ✅ |
| `/notes` | ✅ | — | — | — | — |

全部页面 `document.scrollWidth - clientWidth = 0`（无横向溢出）。

### 2.2 关键度量

- 顶栏动作按钮：1440 下 7 个可见（组件中心 / AI / 任务 / 签到 / 习惯 / 更多 / 通知）全部 **36×36、top=10、图标 18px、图标垂直偏移 iconDy=0**。
- 390 下只剩 4 个（搜索 / AI / 更多 / 通知），仍全部 36×36 且同 top=10，无溢出、不挤压头像。
- 侧栏：一级项 38px、二级项 32px；折叠后宽度 **68px**，24 个 rail 项 **全部**保留 `title` + `aria-label`。
- 内容区：1440 下 `main` 宽 1216（1440 − 224 侧栏），正文 14px 全站一致。

---

## 3. 排版审计与修复复测

### 3.1 修复前审计（28 条路由）

- H1 基本为 28px，例外：`/learning/pomodoro` 24/700、`/notes`（侧栏标题 18px，页面无独立 h1）、`/tasks`+`/task-list` 每页两个 H1、「收件箱」20px。
- 正文标题：`/chat` 为 21.75 / 18.75 / 16.5px（em 值）、字重 650。
- 同级区块：`/learning/center`「当前目标」18px vs「今日行动」20px。
- `/check-in` H3 混排 15 / 18 / 20px（页面内覆盖全局 `.kb-h*`）。
- 按钮 14px 为主；`/notes` 77 个按钮 13px、`/chat` 32 个 13px、`/learning/paths` 30 个 12px（密集工具条，本轮保留并标 PARTIAL）。

### 3.2 修复后复测（全部 PASS）

| 断言 | 实测 |
|---|---|
| `/learning/pomodoro` h1 = 28px/600 | `{"text":"番茄钟","size":"28px","weight":"600"}` |
| `/learning/center` 两个同级 h2 = 18px | 当前目标 18/600、今日行动 18/600 |
| `/check-in` h1 = 28/600、H3 全 18px | 升级进度 / 里程碑 / 打卡日历 / 打卡榜 均 18px |
| `/tasks` 单页单 h1 | `h1 任务中心 28px` + `h2 收件箱 20px` |
| `/apps` 单页单 h1 | count = 1 |
| AI 正文标题阶梯 | 43 个标题，仅 20 / 18 / 16px，字重 ≤600 |
| 390 顶栏 | ox=0，4 个按钮 36×36 同 top=10 |
| `/knowledge`、`/habits` h1 | 均 28px/600 |

---

## 4. Mini App 端到端验收（Phase 3）

脚本断言 21 项，**最终 21/21 通过**（首轮 2 项为脚本自身断言写法问题，非产品缺陷，见 §6）。

| 环节 | 结果 |
|---|---|
| 未安装时「工具」组不含任务 / 签到 / 习惯 / 日历 / 今日计划 | PASS（工具组 = `["专注"]`） |
| 应用中心「发现应用」出现「效率」分类 | PASS（分类 chips：全部/记忆/练习/理解/整理/专注/**效率**/实战/AI） |
| 发现 5 个新 App（任务 / 签到 / 习惯打卡 / 日历 / 今日计划） | PASS ×5 |
| 安装任务 / 签到 / 习惯打卡（真实 `PUT /api/apps/user-apps`） | PASS ×3（安装后从「发现应用」移除） |
| 左侧「工具」组出现三项 | PASS（`["专注","任务","签到","习惯打卡"]`） |
| 未启用的日历 / 今日计划不出现在左侧 | PASS |
| 点击左侧「任务」进入真实页面 | PASS（`/tasks`） |
| 上移排序改变「我的应用」顺序 | PASS |
| 刷新后排序保持 | PASS |
| 收起菜单 → 68px 图标栏，全部 rail 项保留 title / aria-label | PASS（items=24，missing=0） |
| 停用「任务」需二次确认，确认后离开「已启用」 | PASS |
| 刷新后左侧不再显示「任务」，签到 / 习惯保留 | PASS |
| 清空会话后重新写入登录态 → 状态从服务端恢复 | PASS（`["专注","习惯打卡","签到"]`） |
| 重新启用「任务」回到工具组 | PASS |
| 顶栏快捷入口「任务」可跳转 | PASS（`/tasks`） |
| 「更多快捷操作」面板 6 项 | PASS（任务 / 签到 / 习惯打卡 / 今日计划 / 日历 / 专注） |

> 测试数据副作用：`tasks / check-in / habits` 已安装并启用；`calendar / today-plan` 保持未安装。业务数据（任务、签到、习惯记录）未删除、未伪造。

---

## 5. 无障碍与主题

| 检查 | 结果 |
|---|---|
| 侧栏导航项可访问名 | PASS（7/7，无缺失） |
| 顶栏交互项可访问名 | PASS（13/13，无缺失） |
| 键盘可操作 | PASS：Tab 可达快捷按钮，Enter 触发跳转 `/tasks` |
| 焦点可见 | PASS：`outline: 2px solid rgb(59,111,224)`、offset 2px（本轮由 4px 收窄） |
| 折叠后仍可识别 | PASS（68px rail + title + aria-label） |
| 不只靠颜色表达状态 | PASS：AppCard 状态为文字（已启用 / 已停用 / 未安装） |
| `prefers-reduced-motion: reduce` | PASS：过渡时长 1e-05s（`transition:none` 全局覆盖）、无动画 |
| 390 移动端抽屉 | PASS：`打开导航菜单` → 侧栏 x=0、宽 224 |
| 深色模式 | PASS：`data-theme=dark`，`--kb-background #0F1115` / 前景 `#E6E8EC`；`/`、`/apps`、`/chat`、`/notes` 1440 实拍无溢出、文字与卡片边界清晰 |
| 系统字体缩放后关键操作可用 | ⚠️ Not verified（未做浏览器字号放大实测） |

截图证据（`docs/ui-ux/v1/screenshots/`）：

- 多视口：`home-*`、`apps-*`、`chat-*`（1440/1280/1024/768/390）、`learning-center-*`、`notes-*`
- 单页 1440：`knowledge`、`search`、`profile`、`paths`、`habits`、`calendar`、`interview`、`learning-report`、`tasks`、`task-list`、`check-in`、`pomodoro`、`admin-overview`、`apps-discover`、`apps-efficiency`
- 导航态：`sidebar-collapsed-1440`、`sidebar-expanded-1440`
- 深色：`home-dark`、`apps-dark`、`chat-dark`、`notes-dark`

---

## 6. 首轮失败的 2 项断言（脚本问题，非产品缺陷）

1. 「应用中心出现效率分类」首轮 chip=0：脚本在「我的应用」Tab 下检查分类 chips；分类 chips 只在「发现应用」Tab 渲染。改为先切 Tab 后复测 PASS。
2. 「折叠 rail 包含任务」首轮 FAIL：脚本只取前 8 个 rail 标题比对，而 rail 共 24 项、任务位于学习组之后。改为全量比对后 PASS。

两项均已保留在脚本与本节，未删除失败记录。

---

## 7. 已知限制（如实标注）

- **无修改前截图**：进入本轮时工作树已含改动，按规则禁止 `stash / checkout / reset`，无法还原「改前」状态；仅提供修改后多视口证据，前后对照标 ⚠️ PARTIAL。
- **未直接断言 API HTTP 状态码**：App 安装 / 停用通过 UI 状态与刷新、重新登录后的服务端恢复间接验证，未做 Network 面板的 200/400 断言。
- **未运行后端全量测试**（理由见 §1）。
- **未逐页覆盖**：沉浸式阅读 / 编辑器、社区、代码练习、知识图谱、知识工作台、管理后台其余子页、今日计划仅共享基线生效，未逐页实拍（PARTIAL）。
- **H2 通过 ≠ MySQL 验证**：本地 file H2 结果不代表 MySQL 约束已验证，本轮未改 Schema。

---

## 8. 2026-10-28 增补：任务中心 / 今日计划 验收

### 8.1 截图与执行方式

- 目录：`docs/ui-ux/v1/screenshots/task-plan-before/`（HEAD 基线）与 `task-plan-after/`（当前源码），各 18 张（含新增 `task-center-quadrant-1440x900.png`、`task-center-board-1440x900.png`）。
- 基线方法：把当前 `frontend` 复制到 `/tmp/kf-before`，用 `git show HEAD:` 覆写 `TodayPlan.vue` / `TaskManager.vue` / `TaskRow.vue`（`TaskKanban.vue` 当时未修改，等价 HEAD），独立 `vite --port 5174` 渲染。未使用 `stash/checkout/reset`。
- 采集方式：headless Chrome + CDP `Page.captureScreenshot`（本轮 Playwright/Puppeteer 不可用，未虚构工具）。同账号 `user1`、同数据、同主题。
- 视口：1440×900、1280×800、1024×800、768×1024、390×844；额外暗色 1440 与 125%（1152×720 DSF1.25）/150%（960×600 DSF1.5）。

### 8.2 任务中心验收

| # | 检查项 | 结果 |
|---|---|---|
| 1 | 标题与按钮对齐 | PASS：标题/描述左对齐，`今日计划`+`新建任务` 右对齐同一基线 |
| 2 | 分类栏宽度合理 | PASS：232px，行高 32px，选中浅蓝 |
| 3 | 任务列表无过度留白 | PASS：行高 46px、分割线列表 |
| 4 | 搜索与新建任务可用 | PASS：`280×40` 无边框聚焦不出现外框；页头主按钮聚焦既有快捷输入 |
| 5 | 列表/四象限/看板切换 | PASS：三视图共用页头与工具栏；四象限 4 卡网格、看板 3 列无破损（截图各 1 张） |
| 6 | Checkbox 状态正确 | PASS：沿用服务端状态，完成前确认且不预改 |
| 7 | 分类计数正确 | PASS：收件箱/今天/日志计数由原 Store 计算，无前端伪造 |
| 8 | 收起侧栏 | PASS：1440 下 rail 折叠后内容区自适应 |
| 9 | 无多余多层滚动 | PASS：分类栏与主列表各自单滚动 |
| 10 | 小屏无横向溢出 | PASS：768/390 侧栏改为顶部横向条，主列表单列 |

### 8.3 今日计划验收

| # | 检查项 | 结果 |
|---|---|---|
| 1 | 完成度不换行断裂 | PASS：`33%` / `已完成 2 / 3 项` 单行 |
| 2 | 三时段不强制等高 | PASS：各时段卡片内容自适应 |
| 3 | 状态颜色统一 | PASS：普通行中性、当前项浅蓝 + 3px 强调条、完成态浅绿标签 + 删除线 |
| 4 | 图标与文字对齐 | PASS：时段图标 16px 与 18px 标题垂直居中 |
| 5 | 标题完整可读 | PASS：可换行、`overflow-wrap:anywhere` |
| 6 | 时间信息不混乱 | PASS：时段头显示一次范围，行内仅显示耗时 |
| 7 | 日期切换 | PASS：日期 chip 沿用原逻辑 |
| 8 | 计划调整入口 | PASS：`SmartPlanPanel` 保留原预览→确认→保存→历史 |
| 9 | 完成状态回读 | PASS：勾选后重新 `GET /learning/plan/today` `0/3 → 1/3 → 2/3` |
| 10 | 390px 可完成主要操作 | PASS：四按钮完整、单列、无横向滚动（修复 PageHeader actions `shrink-0` 裁切） |

### 8.4 跨页状态一致性（真实 HTTP）

```
目标：提交周报
before: completed 1 / total 3
after : completed 2 / total 3, ratio 66.67
UI    : 已完成 2 / 3 项
任务中心(日志视图): 状态=已完成, 标题 class=tr-title done
刷新后: 一致；重新登录 user1 后: 一致
```

记录见 `task-plan-after/report-verify.json`。统计口径不同（收件箱 13 / 今天 8 / 今日计划 5）经核对为不同查询范围，属预期，未改后端或合并数量。

### 8.5 测试与构建

| 命令 | 结果 |
|---|---|
| `cd frontend && npx vitest run` | PASS：241 files / 1768 tests，退出码 0 |
| `cd frontend && npx vue-tsc --noEmit` | PASS：0 错误，退出码 0 |
| `cd frontend && npm run build` | PASS：`✓ built in 7.83s`（仅既有 chunk >500kB 警告） |

未运行 Maven：本轮只改 Vue 页面与 scoped 样式，未触碰任务 API、完成状态合同或后端服务。

### 8.6 未验证项（如实标注）

- ⚠️ 暗色模式通过 `data-theme=dark` 切换验证，未验证 OS 级 `prefers-color-scheme` 自动跟随。
- ⚠️ 125%/150% 文稿放大用「更窄 CSS 视口 + DSF」等效模拟，未在真实系统字号缩放环境下实拍。
- ⚠️ 未使用浏览器 Network 面板抓包断言状态码；HTTP 结果由页面上下文 `fetch('/api/learning/plan/today')` 读取。
- ⚠️ 未做键盘 Tab 全链路走查（仅静态保留 focus-visible 规则）。
- ⚠️ 本轮 UI 通过不代表 V1 发布 READY，MySQL 生产认证与发布门禁仍属独立任务。
