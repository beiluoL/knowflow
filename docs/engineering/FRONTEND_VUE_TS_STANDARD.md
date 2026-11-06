# KnowFlow 前端 Vue 3 + TypeScript 工程规范

> **CURRENT · 2026-11-01** · 适用：`frontend/src/**`
> 上游依据：Airbnb JavaScript Style Guide、Vue 3 官方风格指南（Priority A/B）、typescript-eslint 推荐规则、项目真实栈（Vue 3.4 + TS 6 + Vite 8 + Pinia 4 + Vue Router 4 + Vitest 4 + Tailwind 3.4）。

---

## 0. 适用与优先级

- UI 视觉与控件合同的唯一权威是 [UI_DESIGN_SYSTEM_V2](../UI_DESIGN_SYSTEM_V2.md)；本分册只约束**写法与复用**。
- 学习架构唯一来源由 [AGENTS.md](../../AGENTS.md) 第二节规定，本分册不重复。
- 存量违规采用棘轮基线，不得新增。

---

## 1. JavaScript 基础

| 编号 | 规则 | 级别 | 来源 |
|---|---|---|---|
| FE-01 | 变量只用 `const` / `let`，禁止 `var` | FORBIDDEN | OFFICIAL |
| FE-02 | 比较用 `===` / `!==`；唯一例外：`x == null` 同时判断 null/undefined | MUST | OFFICIAL |
| FE-03 | 字符串用单引号；模板拼接用反引号；禁止用 `+` 拼接展示文本 | SHOULD | OFFICIAL |
| FE-04 | 语句结尾保留分号（与现有代码一致，由 Prettier 统一） | SHOULD | PROJECT |
| FE-05 | 函数参数超过 3 个改用对象参数；优先解构 | SHOULD | OFFICIAL |
| FE-06 | 禁止未使用变量与未使用导入（`noUnusedLocals` / `noUnusedParameters` 已开启） | FORBIDDEN | PROJECT |
| FE-07 | `if/for/while` 必须使用花括号 | MUST | OFFICIAL |
| FE-08 | 禁止 `eval`、`with`、`new Function` | FORBIDDEN | OFFICIAL |
| FE-09 | 模块只用 `import` / `export`，禁止 `require` | FORBIDDEN | OFFICIAL |
| FE-10 | 禁止魔法数字/魔法字符串；提取为命名常量或配置 | MUST | OFFICIAL |
| FE-11 | `async` 必须处理失败路径（`try/catch` 或调用方 `.catch`），禁止静默吞掉 | MUST | PROJECT |

---

## 2. Vue 3 规范

### 2.1 Priority A（必须）

| 编号 | 规则 | 级别 | 来源 |
|---|---|---|---|
| FE-12 | 单文件组件使用 `<script setup lang="ts">` + Composition API | MUST | OFFICIAL |
| FE-13 | 组件名必须多词（`DocCard`、`StudyPackList`）；`App` 等根组件除外 | MUST | OFFICIAL |
| FE-14 | Props 必须定义类型，并提供 `required` 或 `default`；复杂对象必要时 `validator` | MUST | OFFICIAL |
| FE-15 | Emits 必须显式声明类型；禁止未声明事件 | MUST | OFFICIAL |
| FE-16 | `v-for` 必须带稳定 `:key`；禁止同一元素同时使用 `v-if` 与 `v-for` | FORBIDDEN | OFFICIAL |
| FE-17 | 派生状态用 `computed`；禁止在模板 `{{ }}` 中写复杂表达式或调用方法做派生 | MUST | OFFICIAL |
| FE-18 | 组件名在模板中用 `kebab-case`，在 JS/TS 中用 `PascalCase`；prop JS `camelCase`、模板 `kebab-case` | MUST | OFFICIAL |
| FE-19 | `v-if` / `v-else-if` / `v-else` 相邻分支建议加 `:key` | SHOULD | OFFICIAL |

### 2.2 组件职责

| 编号 | 规则 | 级别 | 来源 |
|---|---|---|---|
| FE-20 | 组件只做一件事；页面编排逻辑留在 `views/`，可复用交互抽到 `components/` | MUST | RECOMMENDATION |
| FE-21 | 禁止在组件内复制已有业务状态机（NextAction、Evidence、FSRS、App 列表） | FORBIDDEN | PROJECT |
| FE-22 | 修改共享组件前先搜索全部使用方，评估影响面并在报告中列出 | MUST | PROJECT |
| FE-23 | 新增公共组件必须同步 `/dev/ui-components` 注册表（`src/dev/registry.ts`），并给出真实 Props/示例/源码路径/无障碍说明/测试 | MUST | PROJECT |
| FE-24 | 禁止为单个页面新建第二套同义公共组件（如第二个 Button/Input/Dialog） | FORBIDDEN | PROJECT |

---

## 3. TypeScript

| 编号 | 规则 | 级别 | 来源 |
|---|---|---|---|
| FE-25 | 禁止显式 `any`；未知类型用 `unknown` + 类型守卫收窄 | FORBIDDEN（新增） | OFFICIAL |
| FE-26 | 保持 `strict` 开启；不得为绕过错误关闭 `strictNullChecks`、`noImplicitAny` | FORBIDDEN | OFFICIAL |
| FE-27 | 对象形状用 `interface`；联合/交叉/工具类型用 `type` | SHOULD | OFFICIAL |
| FE-28 | API 请求/响应必须有显式类型；禁止用 `any` 或裸 `object` 承载合同 | MUST | PROJECT |
| FE-29 | 类型导入使用 `import type`（Vue 的 `InjectionKey`、`Ref` 等） | MUST | PROJECT |
| FE-30 | 避免非空断言 `!` 链；必要时先校验再断言，并说明不变量 | SHOULD | RECOMMENDATION |
| FE-31 | 禁止 `catch (e: any)`；统一 `catch (e: unknown)` + `getApiError` | FORBIDDEN | PROJECT |
| FE-32 | 存量 `any` 60 处进入棘轮基线；修改相关文件时优先消除本次触及处 | SHOULD | PROJECT |

> 说明：当前工程**尚未安装 ESLint**，`no-explicit-any` 等由本规范与检查脚本承担。安装 ESLint 属于后续独立任务，不在本次治理中新增依赖。

---

## 4. 状态管理（Pinia）

| 编号 | 规则 | 级别 | 来源 |
|---|---|---|---|
| FE-33 | 全局共享状态用 Pinia；禁止新建与 `stores/learning.ts` 平行的学习状态 Store | FORBIDDEN | PROJECT |
| FE-34 | Store 只暴露业务动作与派生状态，不暴露可直接改写的内部 ref | SHOULD | RECOMMENDATION |
| FE-35 | 禁止在页面中直接读写 `localStorage` 承载业务状态；仅允许既有已确认的偏好/草稿键 | FORBIDDEN | PROJECT |
| FE-36 | Store 动作必须处理失败并给出可恢复状态，禁止 `.catch(() => [])` 把失败伪装成空数据 | FORBIDDEN | PROJECT |

---

## 5. API 与异步状态

| 编号 | 规则 | 级别 | 来源 |
|---|---|---|---|
| FE-37 | 所有请求必须走 `@/api/request.ts` 的 `apiGet/apiPost/apiPatch/apiPut/apiDelete/apiDeleteWithBody/apiUpload`；禁止页面直接 `axios.*` | FORBIDDEN | PROJECT |
| FE-38 | 响应统一 `Result<T>{code,message,data}`；分页 `PageResult{records,total,pageNum,pageSize,pages}`，取 `records` 不是 `list` | MUST | PROJECT |
| FE-39 | 每个异步视图必须区分 Loading / Success / Empty / Error / Retry；不得只有 Loading 与成功两条路径 | MUST | PROJECT |
| FE-40 | 错误提示统一 `notify` / `getApiError`；确认/输入统一 `confirmDialog` / `promptDialog` | MUST | PROJECT |
| FE-41 | 禁止 `window.alert` / `window.confirm` / `window.prompt`（当前 `src/` 为 0 处，必须保持） | FORBIDDEN | PROJECT |
| FE-42 | 禁止用静态假数据、localStorage 或前端常量冒充真实后端持久化结果 | FORBIDDEN | PROJECT |
| FE-43 | 竞态保护：切换页面/会话后的迟到响应不得写入当前视图；需要时用请求序号或 `AbortController` | SHOULD | RECOMMENDATION |

---

## 6. UI 与设计令牌

| 编号 | 规则 | 级别 | 来源 |
|---|---|---|---|
| FE-44 | 颜色/字号/间距/圆角/阴影使用 `--kf-*` 令牌（源码：`src/style.css`、`src/styles/ui-tokens.css`、`pc-studio.css`、`ui-v2-phase-a.css`） | MUST | PROJECT |
| FE-45 | UI 前缀统一 `kf`：变量 `--kf-*`、类 `.kf-*`、组件 `Kf` + PascalCase、BEM `.kf-x__y` / `.kf-x--variant` | MUST | PROJECT |
| FE-46 | 禁止写死十六进制色值或像素间距（业务数据可视化的必要计算除外，需注释说明） | FORBIDDEN（新增） | PROJECT |
| FE-47 | 禁止新增 UI 框架、第二套图标库、第二套主题或第二套 Design System | FORBIDDEN | PROJECT |
| FE-48 | 普通输入/数字/搜索/Textarea/Select：浅灰表面、默认无明显边框、鼠标/键盘聚焦均不新增蓝/灰 border、outline、ring 或外圈阴影 | MUST | PROJECT |
| FE-49 | 组合控件由外壳承载背景与圆角，内部 input 透明；禁止双层容器与双焦点框 | MUST | PROJECT |
| FE-50 | 图标/文字/快捷键间距 8–12px（顶部搜索 12px）；绝对定位图标必须预留 padding | MUST | PROJECT |
| FE-51 | 表单复用 `Input/Textarea/Select/FormField` 与 `kf-form-row` 轨道；长标签/帮助/错误不得推歪同排邻列 | MUST | PROJECT |
| FE-52 | 普通输入无聚焦外框**不得**扩展为全局 `outline: none`；按钮、菜单项、Checkbox/Radio 必须保留可见键盘反馈与 forced-colors 指示 | FORBIDDEN | PROJECT |
| FE-53 | 特殊控件（代码/Markdown/文件/日期时间/颜色/范围）按其既有实现评估，不机械套用普通输入重置 | MUST | PROJECT |
| FE-54 | 聊天复用 `AIComposer` 与唯一发送/停止入口；不复制发送 Store 或第二套发送 API | FORBIDDEN | PROJECT |
| FE-55 | 局部样式类使用业务前缀（如 `fav-*`），避免与全局 `icon-*` 字体伪元素冲突 | MUST | PROJECT |
| FE-56 | 历史文档中的 `--kb-*` / `.kb-*` 为 LEGACY 命名，只用于理解历史报告，不得在新代码中使用 | LEGACY | PROJECT |

---

## 7. 路由与页面

| 编号 | 规则 | 级别 | 来源 |
|---|---|---|---|
| FE-57 | 路由集中定义在 `src/router/index.ts`；需登录页面标注 `meta.requiresAuth` | MUST | PROJECT |
| FE-58 | NextAction 跳转必须经 `resolveActionRoute()` 白名单与 `stampActionContext()` 盖章，禁止裸 `router.push` 学习路由 | FORBIDDEN | PROJECT |
| FE-59 | 新页面如需注册为 Mini App，必须在 `src/apps/registry.ts` 注册，禁止在 Sidebar/Home 硬编码分类 | MUST | PROJECT |
| FE-60 | 深链参数必须来自 `LEARNING_ENTRY_CONTEXT` 已确认消费的页面，禁止制造假深链 | FORBIDDEN | PROJECT |

---

## 8. 测试与验证

| 编号 | 规则 | 级别 | 来源 |
|---|---|---|---|
| FE-61 | 新增组件/页面/Store 必须有 `__tests__/*.spec.ts`，覆盖主路径与失败路径 | MUST | PROJECT |
| FE-62 | 修改公共组件必须同步更新其专项测试与组件中心示例 | MUST | PROJECT |
| FE-63 | 禁止删除测试、降低断言、跳过用例制造全绿 | FORBIDDEN | PROJECT |
| FE-64 | 提交前执行 `npm test`（含 `check:ui-prefix`）+ `npx vue-tsc --noEmit` + `npx vue-tsc -p tsconfig.app.json --noEmit` + `npm run build` | MUST | PROJECT |
| FE-65 | 能浏览器验证的必须真实验证；不可验证标注 `⚠️ Not verified`，禁止虚构 PASS | MUST | PROJECT |

---

## 9. 提交前自检清单

1. 是否有 `var` / `==` / `any` / `catch(e: any)` / `window.alert`？（必须为否）
2. 组件是否多词命名？`v-for` 是否带 key 且未与 `v-if` 混用？Props/Emits 是否显式？
3. 是否新增了第二套 Store / 第二套请求封装 / 第二套同义组件？（必须为否）
4. 是否使用 `--kf-*` 且没有写死色值？普通输入是否保持无聚焦外框？
5. 异步状态是否覆盖 Loading/Success/Empty/Error/Retry？
6. 是否补充测试并通过四条前端门禁？
