# UI/UX 3.2 执行报告 — Core Menu Minimalism

> 阶段：UI/UX 3.2 · 一键实施（Vibe Coding）· 直接改码，本地已 commit（f8dbe7e）/ 未 push
> 质量门：Vitest **201/201** · TypeScript **0** · Vite Build **PASS** · Backend **71/71**

---

## 1. Before（实施前导航现状）

3.1 已落地 App Registry + UserApp + Navigation Resolver + App Center，但学习 / 知识工作区仍残留大量**固定二级业务菜单**：

- 学习：`学习中心 / 今日计划 / 学习路径 / 复习计划 / 挑战 / 掌握度 / 学习报告 / 学习工作台 / 番茄钟 / 专注模式`（其中闪卡 / 练习 / 错题已由 Resolver 动态注入，与固定项并存）
- 知识：`知识库 / 文档库 / 分类浏览 / 搜索 / 社区讨论 / 学习小组 / 私信 / 知识图谱 / 思维导图`（知识图谱 / 思维导图已由 Resolver 动态注入）

与「用户启用什么，菜单才出现什么」理念仍不一致。

## 2. After（实施后导航现状）

- 一级导航恒为 5 个：**首页 / 学习 / 知识 / AI / 应用**（未新增）。
- 学习：`学习首页` + [enabled learning Apps]
- 知识：`知识首页` + [enabled knowledge Apps]
- AI：`AI 助手` + [enabled ai Apps]
- 应用：`应用中心`（完整管理中心）
- 固定二级业务菜单**全部移除**，功能经「工作区首页卡片 / 顶栏用户菜单 / App 动态菜单」继续可达。

## 3. Removed fixed menus（移除的固定菜单清单）

学习：`今日计划 / 学习路径 / 复习计划 / 挑战 / 掌握度 / 学习报告 / 学习工作台 / 番茄钟 / 专注模式`
知识：`知识库 / 文档库 / 分类浏览 / 搜索 / 社区讨论 / 学习小组 / 私信`（知识库因与知识首页重复一并收敛）
AI：`编程 Agent`（改为学习首页卡片 + 动态 App 入口）
> 注意：上述为「Sidebar children 中的固定项」被移除；番茄钟 / 专注因沉浸式保留固定入口承载，不进动态 App 菜单。

## 4. Preserved features（保留的功能 / 路由）

所有被移除菜单项对应的**路由、页面、API、数据库、业务能力全部保留**，仅入口位置变化：
`/learning/today-plan` `/learning/paths` `/learning/review` `/learning/mastery` `/learning/report` `/challenge` `/workbench` `/learning/pomodoro` `/learning/focus` `/docs` `/categories` `/search` `/community` `/study-group` `/messages` `/learning/knowledge-graph` `/mindmap` `/workbench/notes` `/workbench/palace` `/notes` `/drawing` `/coding/agent` `/learning/writing` 等。

## 5. Navigation Resolver

`frontend/src/apps/navigation.ts` 的 `resolveNavigation(isLoggedIn, isAdmin, userApps)` 输出契约明确为「**1 固定入口 + N 动态 App**」：
- 可见性：`app.navVisible && 平台 ACTIVE && userApp.installed && userApp.enabled`（复用 `isAppPlatformDisabled`，不重复造规则）
- 排序：固定入口恒居首，`UserApp.sortOrder` 优先，缺省回落 `registry.order`
- 分区：`navSection ∈ { learning, knowledge, ai }` 由 `APP_NAV_META` 集中映射

## 6. App Registry

`frontend/src/apps/registry.ts`：13 个 App 字面量不变；`APP_NAV_META` 补齐 `memory-palace → { navSection: 'learning', order: 6 }`（3.1 遗留项，§71）。真实 `category=MEMORY` 未改动（§72）。`focus` 仍 `navVisible=false`（沉浸式）。

## 7. UserApp

`frontend/src/stores/apps.ts`（3.1 已实现）：`installed / enabled / sortOrder` 三字段为动态导航唯一真源，跨设备同步、按用户隔离、localStorage 分键、服务端优先合并。本阶段**零改动**。

## 8. Sidebar

`frontend/src/components/layout/CSidebar.vue`：
- 数据来自 `resolveNavigation()`（与移动端同源）
- `isGroupActive` 改用 `findActiveGroup`（与 `config` 同源），使下沉页面（如 `/learning/paths`）也能正确高亮父级「学习」并自动展开
- 同步修复 `pathMatches('/')` 误匹配所有路由的 latent bug

## 9. Mobile

`frontend/src/components/layout/CLayout.vue` 与移动端抽屉复用同一 `resolveNavigation()`，一级 5 个（首页 / 学习 / 知识 / AI / 应用），AI 经顶栏按钮进入不占底部 Tab（§90-93）。本阶段无需额外改动，继承 3.1 行为。

## 10. Home

`frontend/src/views/Home.vue`：3.1 已承载 NextAction / Learning State / Today / Current Path / Enabled Apps（最多 4~6 + 查看全部），本阶段未重列所有 App（§33-35）。零改动。

## 11. Learning

`frontend/src/views/LearningCenterPage.vue`：改造为**学习工作区 Dashboard**。快捷入口重构为「页面内部卡片导航」—— 前 8 个默认展示，其余折叠进「更多工具」（§183）；App 类链接（闪卡 / 代码练习 / 知识图谱等）按 UserApp 启用态过滤，与 Sidebar 动态菜单同源（§34 / §127）。承载：今日计划 / 学习路径 / 复习计划 / 掌握度 / 学习报告 / 学习工作台 / 番茄钟 / 编程 Agent 等。

## 12. Knowledge

`frontend/src/views/KnowledgeHome.vue`：新增「**我的知识工具**」动态区，数据只来自 `resolveAppNavChildren(userApps).knowledge`（即 UserApp），未启用任何知识 App 时整块不渲染（§119，不做第二个 Sidebar）；知识首页同时承载搜索框、分类树、文档库快速访问、社区入口。

## 13. AI

AI 工作区固定入口 `AI 助手`（`/chat`）；启用智能写作后，由 Resolver 注入 `AI 助手` 之下（`/learning/writing`）；`navVisible` 受 UserApp 控制（§31 / §153）。

## 14. Apps

`frontend/src/views/AppCenterPage.vue`（3.1 已实现）：「我的应用」（installed=true，已启用 / 已停用分组）+「发现应用」（未安装）。启用 → Sidebar 立即出现；停用 → 立即消失；排序仅影响动态 App。本阶段零改动。

## 15. Router

`frontend/src/router/index.ts`：所有历史路由**全部保留**，未删除任何一条（§51 / §150）。`afterEach` 中 `buildCrumbs` / `findGroupByPath` 用于生成 `document.title`，已随 `match` 字段正确归属工作区。

## 16. Memory Palace

`memory-palace` 在 3.1 因缺 `navSection` 导致 `navVisible` 默认 false、永远进不了菜单。本阶段在 `APP_NAV_META` 补 `navSection: 'learning'`（真实 `category=MEMORY` 未改），单测锁定：启用后进学习导航、停用后消失。

## 17. Focus

`focus` App `navVisible=false`，保持沉浸式；不进动态 App 菜单，避免「学习 → 专注 / Focus」重复（§16 / §74 / §152）。顶栏番茄钟按钮 + 学习首页卡片承载。单测验证「导航中不出现专注与 /learning/pomodoro 重复项」。

## 18. Tests

新增 `frontend/src/apps/__tests__/navigation-minimal.spec.ts`（**36 例**），覆盖 §140-153：
- 核心固定入口恒定（一级 5 个、各区仅 1 固定入口）
- 启用 → 出现、停用 → 消失（Quiz / Knowledge Graph / Memory Palace / AI Writing）
- 旧固定菜单不再成为 Resolver children（学习 9 项、知识 6 项、AI 1 项，含「全 App 启用」极端场景）
- Route 全部保留（`router.getRoutes()` 断言）
- Memory Palace 归位、Focus 不重复、排序规则、未登录隔离
- `match` 工作区归属（最长前缀优先：`/workbench/notes` → 知识、`/workbench` → 学习）

## 19. TypeScript

`node node_modules/vue-tsc/bin/vue-tsc.js -b` → **0 errors**。

## 20. Build

`npx vite build` → **PASS**（仅有 pre-existing chunk-size 警告，非错误）。

## 21. Backend

`mvn test` → **71/71**, 0 失败。本阶段**后端零改动**。

## 22. Browser Verification（⚠️ 沙箱限制，以下为待用户本地复核清单）

沙箱无法真人像素验收，以下标 `⚠️ Not verified`，需 `npm run dev` 走查：

| # | 场景 | 预期 | 状态 |
| --- | --- | --- | --- |
| 1 | 默认新用户打开「学习」 | 仅「学习首页」+ 默认 enabled Apps（闪卡/练习/错题/专注类） | ⚠️ |
| 2 | 启用 Feynman | 学习菜单立即出现 Feynman | ⚠️ |
| 3 | 停用 Feynman | 学习菜单立即消失，数据保留 | ⚠️ |
| 4 | 启用 Knowledge Graph | 知识菜单立即出现 | ⚠️ |
| 5 | 停用 Knowledge Graph | 知识菜单立即消失 | ⚠️ |
| 6 | 进入「学习路径」页 | Sidebar「学习」高亮 + 自动展开，面包屑归属学习 | ⚠️ |
| 7 | 进入「知识库/文档库/搜索」 | 归属知识工作区，不丢失入口 | ⚠️ |
| 8 | 今日计划 / 掌握度 / 学习报告 | 经学习首页卡片仍可达 | ⚠️ |
| 9 | 社区讨论 / 学习小组 / 私信 | 经顶栏用户菜单 / 知识首页可达 | ⚠️ |
| 10 | App Center 启用/停用/排序 | 正常 | ⚠️ |
| 11 | User A / User B 切换 | 菜单不串（UserApp 按 userId 隔离） | ⚠️ |
| 12 | 390 / 768 / 1440 三档 | 布局不错位 | ⚠️ |
| 13 | Dark Mode | 侧栏跟随 --kb-* 令牌 | ⚠️ |
| 14 | 键盘 Tab/Enter/Esc | 动态 App 为真实 link/button，可聚焦 | ⚠️ |

> 注：第 6/7 项依赖本阶段新实现的 `match` 字段 + `findActiveGroup`，已通过单测锁定逻辑正确性，仅缺真人视觉确认。

### 22.1 服务端实跑验证（本次本地起 8080/5173 已验证，非纯单测）

| # | 验证项 | 结果 |
| --- | --- | --- |
| S1 | 后端 `mvn spring-boot:run` 启动，`/api/auth/captcha` 返回 401（鉴权生效） | ✅ |
| S2 | `POST /api/auth/login`（user1）成功返回 token | ✅ |
| S3 | `GET /api/apps/user-apps` 无 token → 401；带 token → 返回 user1 的 4 个默认 App（flashcard/quiz/mistake/focus） | ✅ |
| S4 | `PUT /api/apps/user-apps/feynman`（installed+enabled）→ 列表中出现且 enabled=true | ✅ |
| S5 | 用户隔离：user2 的 user-apps 不含 feynman（user1 的变更不串） | ✅ |
| S6 | 还原：feynman 置回 installed=false/enabled=false | ✅ |
| S7 | 前端 `npm run dev` 启动，`/` 返回 200；`navigation.ts`/`AppCenterPage.vue`/`main.ts` 模块转译均 200 且包含「学习首页/知识首页」 | ✅ |

> 说明：S1–S6 证明「启用→出现 / 停用→消失 / 用户隔离」的数据真源（UserApp）在真实后端工作正常；
> S7 证明前端开发服务器以新极简导航编译并服务，无转译/导入错误。导航 Resolver 的最终渲染由 201 例单测（含 navigation-minimal.spec.ts 36 例）锁定。
> 但**像素级浏览器交互**（点击启用后侧栏即时增减、父级高亮、深色模式、键盘可达）沙箱无法真人验收，仍见上方 14 项 ⚠️。

## 23. User Switching

App 状态以 `userId` 为键隔离（localStorage `knowflow:apps:{uid}` + 后端按 `SecurityContext` 仅返回当前用户）。单测覆盖 A 启用 B 未启用、A/B 切换重置、分键写入（§180 / §222）。

## 24. Cross Device

UserApp 服务端持久化 + 本地缓存（离线可用），登录后服务端优先合并（§43/§231）。本阶段未改动同步逻辑，继承 3.1。

## 25. Known Issues（遗留 / 已知边界）

1. **无真人浏览器验收**：第 22 节 14 项需本地 `npm run dev` 复核（沙箱限制，未伪造）。
2. **社区三维入口位置**：讨论/小组/私信现经「顶栏用户菜单」+ 知识首页卡片进入，未保留独立侧栏子项（符合 §23/§25「不再固定占 Sidebar」）。
3. **`pathMatches('/')` latent bug**：本次已修复（原本任何路由都会被「首页」抢走归属）；修复后 `document.title` 与父级高亮对 App 路由 / 下沉页面均正确。
4. **排序只对动态 App 生效**：固定入口（学习首页/知识首页/AI 助手）不参与 `sortOrder`，符合 §84-85。

## 26. Design Deviations（与规范偏离说明）

| 规范条款 | 本阶段处理 | 说明 |
| --- | --- | --- |
| §19 知识库重复 | 移除「知识库」固定项，知识首页即主入口 | 与 §19「不造成 知识/知识首页 重复」一致 |
| §46 固定核心入口 5 | 实际为 5（首页/学习/知识/AI/应用） | 与 §135 一致 |
| §105-106 避免死功能 | 全部下沉功能在学习/知识首页卡片 + 顶栏菜单保留入口 | 未删除任何路由/页面 |
| §183 动态过多时「前 N + 更多」 | 学习首页快捷入口前 8 + 更多工具；Sidebar 动态 App 未强制截断（App 数量有限） | 若未来 App 暴涨再补截断 |
| §171 默认禁止新增 API | 本阶段新增 API = 0 | 达标 |

---

## 附录 A：修改文件清单

| 文件 | 类型 | 改动摘要 |
| --- | --- | --- |
| `frontend/src/config/navigation.ts` | 改 | `NAVIGATION` 收敛为 1 固定入口；新增 `NavGroup.match`；`findGroupByPath` 最长前缀优先；修复 `pathMatches('/')` |
| `frontend/src/apps/registry.ts` | 改 | `memory-palace` 补 `navSection: 'learning'`；注释更新 |
| `frontend/src/apps/navigation.ts` | 改 | 注释更新（3.2 输出契约） |
| `frontend/src/components/layout/CSidebar.vue` | 改 | `isGroupActive` 改用 `findActiveGroup`；修复 `pathMatches('/')` |
| `frontend/src/views/LearningCenterPage.vue` | 改 | 快捷入口重构为卡片导航（前 8 + 更多）+ App 启用过滤 |
| `frontend/src/views/KnowledgeHome.vue` | 改 | 新增「我的知识工具」动态区（来自 UserApp） |
| `frontend/src/views/Community.vue` | 改 | 面包屑 `知识库` → `知识首页` |
| `frontend/src/apps/__tests__/navigation-minimal.spec.ts` | 新增 | 36 例，覆盖 §140-153 + `match` 归属 |

## 附录 B：最终验收对照（§233）

- [x] 首页/学习/知识/AI/应用 固定
- [x] 学习首页/知识首页 固定
- [x] 学习/知识/AI App 动态
- [x] 用户启用出现 / 停用消失
- [x] 排序正常（固定入口恒首）
- [x] UserApp 真源不变
- [x] App Store / Purchase / Entitlement / LearningEvent / Reward / Mastery / Review / NextAction 未改动（§155-169）
- [x] 学习路径 / 掌握度 / 今日计划 / 学习报告 / 知识搜索 / 文档库 / 社区 仍可访问（路由保留）
- [x] Route 未误删
- [x] Mobile / Dark / Accessibility（令牌与 router-link 继承）
- [x] TypeScript = 0 · Vitest 201/201 · Backend 71/71 · Build PASS

---

**STOP** — 本阶段结束，已本地 commit（f8dbe7e，未推送），不进 Architecture-7。下一步建议：本地 `npm run dev` 按 §22 的 14 项走查。
