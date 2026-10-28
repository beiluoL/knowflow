# KnowFlow UI/UX 3.0 执行报告

> 阶段：App-Driven Navigation（动态应用导航系统）
> 基础：UI-UX 2.0 + 2.1 + Architecture-6；**未进入 Architecture-7**、未新增 AI Coach / 新 App / 新一级导航。
> 完成时间：2026-08-31

---

## 1. 修改文件

| 文件 | 类型 | 改动说明 |
|---|---|---|
| `frontend/src/apps/navigation.ts` | **新增** | 唯一导航 Resolver：`resolveNavigation()` / `resolveAppNavChildren()` / `canShowInNavigation()` |
| `frontend/src/apps/types.ts` | 修改 | 新增 `AppNavSection` 类型；`AppDefinition` 增加 `navVisible` / `navSection` / `order`（运行时富化，13 个字面量不动） |
| `frontend/src/apps/registry.ts` | 修改 | 新增 `APP_NAV_META`（分区/排序/可见性单一来源）+ 模块加载时一次性富化 |
| `frontend/src/config/navigation.ts` | 修改 | 移除 学习/知识/AI 中**写死的 App 子项**（闪卡/错题本/智能测验/代码练习/知识图谱/智能写作），保留固定工作区入口与专注固定入口 |
| `frontend/src/components/layout/CSidebar.vue` | 修改 | 改用 `resolveNavigation()`；`isOpen`/路由 watch 基于解析后的分组（动态 App 页也能自动展开父级） |
| `frontend/src/components/layout/CLayout.vue` | 修改 | 移动端底部导航改用同一 Resolver；`onMounted` 在常驻 Shell 同步 UserApp（保证深链直达时菜单就绪） |
| `frontend/src/stores/apps.ts` | 修改 | 对外暴露原始 `userApps`（Resolver 输入），其余逻辑零改动 |
| `frontend/src/views/LearningCenterPage.vue` | 修改 | 快捷入口中的 App 类链接按启用态过滤（§34） |
| `frontend/src/apps/__tests__/navigation-resolver.spec.ts` | **新增** | 15 例导航 Resolver 单测（§88-91） |

**零改动**：后端（Java）、Purchase / Entitlement / Wallet / Order、LearningEvent / Reward / Mastery / SRS / NextAction / Learning Intelligence。

---

## 2. 导航树（After）

```text
首页  /
学习  ├─ 固定：学习中心 / 今日计划 / 学习路径 / 复习计划 / 挑战 / 掌握度 / 学习报告 / 学习工作台
     ├─ 固定·专注：番茄钟(/learning/pomodoro) · 专注模式(/learning/focus)   ← §13 沉浸式
     └─ 动态：闪卡 · 练习 · 错题 · 代码练习 · 费曼
知识  ├─ 固定：知识库 / 文档库 / 分类浏览 / 搜索 / 社区讨论 / 学习小组 / 私信
     └─ 动态：知识图谱 · 康奈尔 · 思维导图 · 笔记 · 绘图
AI    ├─ 固定：AI 助手(/chat) · 编程 Agent(/coding/agent)
     └─ 动态：智能写作
应用  /apps  （App 管理中心：我的应用 + 全部/发现）
```

一级导航恒为 **5 个**：首页 / 学习 / 知识 / AI / 应用（§2、§73）。

---

## 3. 动态规则

```text
可见（§41） = app.navVisible  &&  userApp.installed  &&  userApp.enabled
排序（§18） = userApp.sortOrder  ??  app.order（registry）
分区（§19） = app.navSection（仅在 registry.ts 单点定义）
未登录     = 不注入任何 App 子菜单（§46）
空工作区   = 保留固定首页入口，不出现空 section（§24）
例外       = focus：navVisible=false，不进动态子菜单，沉浸式固定入口保留（§13）
```

- 13 个 App 中 **12 个**可进入动态子菜单，1 个（focus）有意保留为固定入口。
- 记忆宫殿（`memory-palace`）当前 `APP_NAV_META` 未配置 → 不进动态子菜单（仍可在应用中心安装使用；见遗留问题）。

---

## 4. UserApp

- 状态：`installed` / `enabled` / `sortOrder`；**后端 `user_app` 为真源**，Pinia 为缓存，localStorage 仅降级读缓存（§46）。
- 非法组合 `installed=false && enabled=true` 由 store 归一化（`!installed → enabled=false`，§17）。
- 登录/同步：本阶段在常驻 `CLayout.onMounted` 增加一次 `hydrate()`，**修复此前「仅 Home/AppCenter 挂载才同步」导致深链直达时动态菜单可能为空**的问题。
- 切换/登出：store 内 `watch(uid)` 重置，避免 A→B 串号（§45）。
- 停用只隐藏，不删除任何 Flashcard / Quiz / Mistake / LearningEvent / Mastery / Review / Entitlement 数据；重新启用无需再购买（§16、§44）。

---

## 5. 测试

| 项 | 结果 |
|---|---|
| 本阶段新增 | `navigation-resolver.spec.ts` **15 例** |
| 覆盖（§88-91） | 默认状态 / enabled / disabled / installed / navVisible / category 分区 / sortOrder 覆盖 / 未登录隔离 |
| 累计 | **Vitest 120/120 通过**（88 → 105(2.2) → **120** = 105 + 15） |
| 约束 | 未删除、未 skip、未 ignore、未降低断言；未使用 `@ts-ignore` / `any` |

---

## 6. TypeScript

| 项 | 结果 |
|---|---|
| `vue-tsc`（`npm run build`） | **0 errors** |

过程中真实修复的 2 类错误（未用 `@ts-ignore` 规避）：
1. `AppDefinition` 新增必填字段导致 13 个 App 字面量缺属性 → 改为**可选字段 + registry 运行时富化**（不动 13 个字面量）。
2. `appStore.userApps` 未从 store 返回 → 在 `stores/apps.ts` 的 return 中补充暴露 `userApps`。

---

## 7. Build

| 项 | 结果 |
|---|---|
| `npm run build`（vue-tsc + vite） | **PASS**，`✓ built in 3.54s`，Exit 0 |
| 说明 | 仅有 chunk >500kB 的体积提示（非阻塞，非本阶段引入） |

---

## 8. Backend

| 项 | 结果 |
|---|---|
| `mvn -f backend/pom.xml test` | **Tests run: 71, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS** |

本阶段**后端零改动**，71/71 沿用。

---

## 9. 浏览器验证（必须诚实标注）

> 沙箱环境的 `agent-browser` 白屏，**无法进行真人像素级验收**。以下为「已通过代码/单测/构建验证」与「仍需你本地验证」的分区。

### 9.1 ✅ 已由代码 + 单测验证

| 验证点 | 依据 |
|---|---|
| 一级导航恒为 5 个 | `navigation.spec.ts` + `navigation-resolver.spec.ts` |
| 启用 Feynman → 学习菜单出现 | 单测「启用 Feynman → 出现 / 停用 → 消失」 |
| 停用 Feynman → 学习菜单消失 | 同上 |
| 启用 Knowledge Graph → 知识菜单出现 | 单测断言 |
| installed=false → 不可见 | 单测 |
| navVisible=false（focus）→ 不进动态子菜单 | 单测 |
| sortOrder 覆盖 registry order | 单测 |
| 未登录不注入 App 子菜单（用户隔离） | 单测 |
| Desktop / Mobile 共用同一 Resolver | `CSidebar` 与 `CLayout` 均调用 `resolveNavigation()` |
| 停用后数据保留 | `disable()` 仅改 `enabled=false`，未触碰任何业务数据 |
| 重新启用无需购买 | `enable()` 仅改 `enabled=true` |

### 9.2 ⚠️ Not verified（需你本地 `npm run dev` 真机走查）

| # | 验证项 | 建议操作 |
|---|---|---|
| 1 | 新用户默认状态（闪卡/练习/错题/专注） | 新建账号 → 看学习菜单 |
| 2 | 应用中心启用 Feynman → 学习菜单实时出现（不刷新网页） | 停在任意页 → 启用 → 观察侧边栏（§49） |
| 3 | 停用 Feynman → 学习菜单实时消失 | 同上 |
| 4 | 启用/停用 Knowledge Graph → 知识菜单同步 | 同上 |
| 5 | 兑换付费 App → 菜单自动出现 | 应用中心兑换 → 观察（§42/§81） |
| 6 | 重新登录状态恢复 | 登出再登录（§82） |
| 7 | A → 登出 → B：菜单不串 | 双账号切换（§83/§45） |
| 8 | 移动端 390px：5 个一级入口 + 动态 App 正确 | 移动端断点（§84-85） |
| 9 | 768 / 1440px 侧栏与内容布局 | 桌面断点 |
| 10 | 暗色模式动态菜单无白底泄漏 | 主题切换（§86） |
| 11 | 键盘 Tab / Enter / Esc 可达分组与子项 | 键盘走查（§87） |
| 12 | 深链直达（如直接打开 `/learning/center`）时动态菜单已就绪 | 刷新后观察（本阶段新增 Shell 同步） |

---

## 10. 最终产品判断（§245）

| 问题 | 回答 |
|---|---|
| 是否被 App 状态驱动 | ✅ 是，菜单 100% 由 registry × UserApp 推导，无硬编码 App 数组 |
| 一级导航是否稳定 | ✅ 恒为 5 个，未新增 |
| 停用是否删数据 | ✅ 否，仅 `enabled=false` |
| 重新启用是否需重购 | ✅ 否 |
| 桌面/移动是否同源 | ✅ 同一 Resolver |
| 购买/Entitlement 是否改动 | ✅ 零改动 |
| 学习内核是否改动 | ✅ 零改动 |
| 真实浏览器像素验收 | ⚠️ **未完成**（沙箱限制，见 §9.2） |

---

## 11. 遗留问题

1. **真实浏览器验收未做**：沙箱 `agent-browser` 白屏，像素/交互/键盘/暗色/响应式均需本地走查（清单见 §9.2）。
2. **memory-palace 未配置 navSection**：当前不进入动态子菜单，仅可从应用中心/首页「我的应用」进入。如需纳入学习菜单，在 `registry.ts` 的 `APP_NAV_META` 补一行即可（无需改组件）。
3. **focus 双入口**：`番茄钟 /learning/pomodoro` 与 `专注模式 /learning/focus` 同时保留为固定入口，属有意为之（§13 沉浸式），但产品上可考虑后续收敛为一个。
4. **导航分区 navSection 与 category 是两套维度**：`category` = 学习方法类型（MEMORY/PRACTICE/…），`navSection` = 导航工作区（learning/knowledge/ai）。二者关系集中在 `APP_NAV_META` 单点维护，未来如 category 语义调整需同步此处。
5. **未做拖拽排序**：本阶段仅正确读取 `sortOrder`（§50），UI 排序交互留待后续。
6. **工作区 App 数量超阈值时未做「更多」折叠**：当前学习/知识区最多 5 个动态 App，未触及 §61-63 的 6 个阈值；若未来启用更多需补「前 N + 更多」。

---

## 12. 约束遵守情况

| 约束 | 状态 |
|---|---|
| 开始 `git status` | ✅ |
| 禁止 `git reset --hard` / `git clean -fd` / `git checkout .` | ✅ 未执行 |
| 禁止 `git commit` / `git push` | ✅ 未执行 |
| 不覆盖已有未提交修改 | ✅ 仅新增/最小编辑，工作树中既有 Phase 改动原样保留 |
| 不进入 Architecture-7 / AI Coach / 新 App / 新导航 | ✅ |
