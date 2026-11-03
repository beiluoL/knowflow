---
name: frontend-code-standard
description: 在编写前端代码（JavaScript / TypeScript / Vue 3 / React）或评审前端代码时加载本技能，强制遵循类「阿里巴巴代码规范」的前端标准——以 Airbnb JavaScript Style Guide 为基线（前端界的「阿里规范」），叠加 Vue 3 官方风格指南（Priority A/B 强制与强烈推荐项）与本项目的 Vue3+TS+Vite+Pinia 约定。When the user references 前端规范 / Airbnb / 风格指南 / style guide / 代码规范 / 前端评审 / Vue 规范，trigger this skill.
agent_created: true
---

# 前端代码开发规范（强制项速查）

本技能对应后端侧的 `alibaba-code-standard`，用于所有前端（JS / TS / Vue 3 / React）开发与代码评审，确保默认遵循业界强制级规约。前端界的「阿里巴巴规范」即 **Airbnb JavaScript Style Guide**（采用最广），Vue 项目再叠加 **Vue 官方风格指南**。完整条目见 `references/frontend_mandatory_rules.md`。

## 何时使用

- 编写 Vue 3 / React 组件、TS 类型、工具函数、请求层
- 评审前端代码（PR / 自查）
- 新建前端页面 / 路由 / store
- 用户提及「前端规范」「Airbnb」「风格指南」「Vue 规范」「代码规范」

## 最高优先级：Airbnb JS 核心强制项（前端「阿里规范」基线）

1. **变量声明用 `const` / `let`，禁止 `var`**（`var` 函数作用域易引发 bug）。
2. **比较用 `===` / `!==`，禁止 `==` / `!=`**（仅 `foo == null` 可同时判 null/undefined，属 Airbnb 允许的特例）。
3. **字符串用单引号 `'`**，模板字符串用反引号；禁止字符串拼接。
4. **语句结尾必须写分号 `;`**（Airbnb 风格，防 ASI 自动插入陷阱）。
5. **对象/数组/函数参数使用解构**；函数参数 > 2~3 个时改用对象传参。
6. **禁止未使用的变量**（lint 报错）；导入但未使用的模块必须删除。
7. **块级语句（if/for/while）必须用花括号 `{}`**，即使单行。
8. **命名**：变量/函数 `camelCase`；类/组件/构造函数 `PascalCase`；常量 `UPPER_SNAKE_CASE`；禁止使用魔法数字/字符串（提取为命名常量）。
9. **箭头函数用于匿名函数**；需要 `this` 绑定时用普通函数或组合式 API 的 `setup`。
10. **优先 `async/await` 而非裸 Promise 链**；`async` 函数必须 `try/catch` 或 `.catch` 处理异常，禁止吞掉。
11. **模块用 `import` / `export`，禁止 `require`**；导入路径统一、不写冗余相对层级。
12. **禁止 `eval` 与 `with`**；禁止在 `for` 循环里用 `for...in` 遍历数组。
13. **2 空格缩进，末尾逗号 trailing comma 必须**（多行对象/数组/参数）。

## Vue 3 官方风格指南（Priority A 强制 + B 强烈推荐）

**Priority A（必须）：**
- **组件名必须多词**（避免与 HTML 原生标签冲突），如 `DocCard` 非 `Card`。
- **Prop 必须详细定义**（类型 + `required` 或 `default` + 必要时 `validator`）。
- **`v-for` 必须带 `:key`**，且**不与 `v-if` 用于同一元素**（Vue 3 中 `v-if` 优先级更高，混用会出错）。
- **派生状态用 `computed`，禁止在模板 `{{ }}` 里写复杂表达式或方法调用当派生值**。
- **模板里组件用 `kebab-case`，JS 里用 `PascalCase`**；prop 名 JS 用 `camelCase`，模板用 `kebab-case`。
- **单文件组件 `<script>` 优先用 `<script setup>`**（组合式 API 现代写法）。
- 组件 data 必须是有返回对象的函数。

**Priority B（强烈推荐）：**
- `v-if` / `v-else` 相邻元素必须加 `:key`。
- 多用指令简写 `:` 与 `@`。
- 组件 attribute 顺序约定：`<script setup>` 后 `name`、props、emits、其余。
- 多 attribute 元素属性分行。

## TypeScript 专项

1. **禁止 `any`**（`@typescript-eslint/no-explicit-any`）；未知类型用 `unknown` + 类型守卫。
2. **开启 `strict` 模式**（strictNullChecks 等）。
3. 对象形状用 `interface`，联合/交叉类型用 `type`；函数参数与返回值显式标注类型。
4. TSX 中用 `as` 断言，不用尖括号 `<Type>x`（避免与 JSX 语法冲突）。
5. 枚举优先用 `const enum` 或字面量联合类型替代魔法字符串。

## 本项目（KnowFlow）前端约定

- 技术栈：**Vue 3 + TypeScript + Vite + Pinia + Vue Router + Axios**。
- 路径别名 `@` → `src`；页面放 `src/views`，组件放 `src/components`，接口层放 `src/api`（类型在 `src/api/types.ts`）。
- **请求层已类型化**：统一用 `apiGet<T>` / `apiPost<T>` / `apiPatch<T>` / `apiPut<T>` / `apiDelete<T>` / `apiUpload<T>`（来自 `@/api/request`），禁止页面里直接 `axios.get` 裸调。
- 后端统一返回 `ApiResult<T>{ code, message, data }`、分页 `PageResult{ records, total, pageNum, pageSize, pages }`——注意分页数据是 `records` 不是 `list`。
- **JWT**：请求拦截器自动带 `Bearer` token；401 跳登录（登录/注册接口自身 401 除外）；store 用 `useAuthStore`（`src/stores/auth.ts`）。
- 状态管理用 **Pinia**（如 `useAuthStore`），不用 Vuex；跨组件共享状态放 store，不放组件 data 透传。
- 路由守卫在 `router/index.ts` 统一处理登录态；需登录页面加 `meta.requiresAuth`。
- **设计令牌**：颜色/字号/间距/圆角一律用 `style.css` 与 `styles/ui-tokens.css` 的 `--kf-*` 变量，禁止写死十六进制色值或像素；不新增 UI 库、不建第二套主题。
- **提示与确认**：禁止 `window.alert` / `window.confirm` / `window.prompt`，统一用 `@/utils/toast` 的 `notify` / `confirmDialog` / `promptDialog`，错误用 `getApiError`。

## 应用方式（面向 WorkBuddy）

1. 写组件前对照 Vue Priority A：多词组件名、prop 详定义、`v-for` 带 key 且不混 `v-if`、`computed` 派生。
2. 写 TS 时对照：无 `any`、strict、接口/类型分明、函数签名标注。
3. 写请求时**必须走 `apiGet/apiPost/...` 类型化封装**，响应取 `res.data`，分页取 `res.data.records`。
4. 评审代码按 Airbnb 13 条 + Vue 指南 + TS 专项逐项检查并给出整改建议。

## 参考

- 详细规则全集：`references/frontend_mandatory_rules.md`（Airbnb 强制项 / Vue 风格指南 / TS / 项目约定）。
