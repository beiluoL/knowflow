# 前端代码开发规范 — 强制（强制）级规则全集

> 本文件为 `frontend-code-standard` 技能的详细参考。前端界的「阿里巴巴规范」基线 = **Airbnb JavaScript Style Guide**；Vue 项目叠加 **Vue 官方风格指南**（vuejs.org/style-guide）。以下摘录强制（essential / must）与强烈推荐（strongly recommended）项，用于代码评审、写组件、写 TS 时逐项核对。

## 一、Airbnb JavaScript 强制项（基线）

### 类型与声明
1. **禁止 `var`**，一律 `const`（不重新赋值）/ `let`（需重新赋值）。
2. 优先 `const`，确需可变才 `let`。
3. 用 `const`/`let` 声明时不得重复声明同名变量。

### 引用与解构
4. 对数组、对象、函数参数使用**解构赋值**（提高可读性、避免临时引用）。
5. 函数参数超过 2~3 个时，改用**对象传参 + 解构**（避免参数顺序错乱、易扩展）。
6. 返回值解构时重命名避免与已有变量冲突。

### 字符串
7. 字符串用**单引号** `'foo'`（非双引号）；含变量/换行用**模板字符串** `` `hello ${name}` ``。
8. **禁止字符串拼接** `+`。

### 函数
9. **匿名函数用箭头函数** `() => {}`（保留词法 `this`）。
10. 需要动态 `this` 或作为方法时可用普通 `function`。
11. 自调用函数用括号包裹（立即执行）或改用块级作用域。
12. 函数参数默认值放在末尾，避免改变参数本身（不修改入参对象）。
13. **优先 `async/await`** 处理异步；裸 Promise 链的 `.then` 也要 `.catch`。

### 比较与相等
14. **用 `===` / `!==`**，禁止 `==` / `!=`（避免隐式类型转换 bug）。
15. 特例：判 `null`/`undefined` 可用 `foo == null`（同时命中两者），其余一律 `===`。
16. 布尔、字符串、数字比较禁止依赖隐式转换。

### 块与分号
17. **所有块必须用花括号** `if (x) { doY() }`，即使单行（防 ASI 与后续维护出错）。
18. **语句结尾必须写分号 `;`**（Airbnb 要求，防自动分号插入陷阱）。
19. `if` / `else if` / `else` 的 `else` 与 `if` 右花括号同行。

### 命名
20. 变量、函数：`camelCase`。
21. 类、构造函数、组件：`PascalCase`。
22. 常量：`UPPER_SNAKE_CASE`；React 组件名 `PascalCase`（即便常量赋值）。
23. **禁止魔法数字/字符串**：提取为具名常量（如 `const MAX_RETRY = 3`）。
24. 避免使用单字母变量（循环 `i/j/k` 除外）；避免使用拼音无含义缩写。

### 模块
25. **用 `import` / `export` 静态模块**，禁止 `require` / `module.exports`（ESM 优先）。
26. `import` 放文件顶部，按类型分组（第三方 → 本项目 → 相对）。
27. 禁止未使用的导入/变量（lint 报错）。

### 其它
28. **禁止 `eval` 与 `with`**。
29. 遍历数组用 `for...of` / `forEach` / `map`，**禁止 `for...in` 遍历数组**（`for...in` 遍历可枚举属性含原型链）。
30. 对象方法用方法简写 `obj = { foo() {} }`；计算属性键用 `[computed]`。
31. 2 空格缩进；多行对象/数组/函数参数 **trailing comma 必须**。

## 二、Vue 3 官方风格指南（Priority A 强制 + B 强烈推荐）

### Priority A（Essential，必须）
1. **组件名为多个单词**：`components/ TodoItem.vue`（`TodoItem`），避免与 HTML 原生元素（`button` 等）冲突。
2. **Prop 定义尽量详细**：指定 `type`，并 `required` 或 `default`；必要时 `validator`。
   ```ts
   props: { id: { type: Number, required: true }, status: { type: String, default: 'draft', validator: v => ['draft','published'].includes(v) } }
   ```
3. **`v-for` 必须设置 `:key`**（稳定唯一值，非数组下标）。
4. **`v-for` 与 `v-if` 禁止用于同一元素**：Vue 3 中 `v-if` 优先级更高，混用会导致 `v-if` 无法访问 `v-for` 变量或渲染异常。列表过滤应在 computed 中完成。
5. **复杂逻辑用 `computed` 属性**，禁止在模板 `{{ }}` 内写复杂表达式或多层方法调用作为派生值。
6. **模板中组件名用 `kebab-case`**（`<todo-item>`），JS/导入用 `PascalCase`（`TodoItem`）。
7. **Prop 名：JS 中 `camelCase`，模板中 `kebab-case`**（`propName` ↔ `:prop-name`）。
8. **单文件组件使用 `<script setup>`**（组合式 API 现代写法，更简洁、编译优化更好）。
9. **组件 `data` 必须是返回对象的函数**（避免多实例共享状态）。
10. 组件 `name` 选项在多词组件上明确（`<script setup>` 可用 `defineOptions({ name })`）。

### Priority B（Strongly Recommended，强烈推荐）
11. `v-if` / `v-else` / `v-else-if` 相邻链中每个元素加 `:key`。
12. **指令简写**：`:` 代替 `v-bind:`，`@` 代替 `v-on:`。
13. **单文件组件顶层元素顺序**：`<script>`（含 `<script setup>`）→ `<template>` → `<style>`；`<style>` 加 `scoped`。
14. 组件属性分行（超过 1 个 attribute 时每行一个）。
15. 简单计算属性原则：computed 内不要有副作用、不要调用会改变状态的方法。
16. 空行：`<script>`、`<template>`、`<style>` 之间留空行；同一区块逻辑分组留空行。

### Priority C（Recommended，推荐）
17. 元素 attribute 顺序约定：`is` → `v-for` → `v-if`/`v-else` → `id` → `ref` → `key` → `v-` 指令 → `:prop` → `@event` → 普通 HTML 属性。
18. 同一项目内模板引号风格一致（单引号）。
19. 组件文件命名：单文件组件用 `PascalCase` 或 `kebab-case`，项目内统一。

## 三、TypeScript 专项（@typescript-eslint 强制）

1. **禁止显式 `any`**（`no-explicit-any`）：未知用 `unknown` + 类型收窄；第三方无类型用 `// @ts-expect-error` 局部抑制并注明原因。
2. **`tsconfig` 开启 `strict: true`**（含 `strictNullChecks`、`noImplicitAny`、`strictFunctionTypes` 等）。
3. 对象结构用 `interface`；联合/交叉/工具类型用 `type`。
4. **函数参数与返回值显式标注类型**，不要依赖推断（公共 API）。
5. TSX 中用 `as` 断言（`const x = y as Foo`），不用尖括号 `<Foo>y`（与 JSX 冲突）。
6. 用 `readonly` / `ReadonlyArray<T>` 表示不可变；用 `as const` 冻结字面量。
7. 枚举：优先 `const enum` 或字面量联合类型 `type Status = 'draft' | 'published'`，减少运行时对象。
8. `null` 与 `undefined` 区分明确；可空类型用 `T | null`，禁止用 `T | undefined` 与 `T | null` 混用不处理。

## 四、本项目（KnowFlow）前端约定（实战约束）

1. 技术栈：**Vue 3 + TypeScript + Vite + Pinia + Vue Router + Axios**。
2. 路径别名 `@` → `src`；目录：`src/views`（页面）、`src/components`（组件）、`src/api`（接口层，类型在 `src/api/types.ts`）、`src/stores`（Pinia）、`src/router`（路由）。
3. **请求层已类型化、必须复用**：统一从 `@/api/request` 调用 `apiGet<T>` / `apiPost<T>` / `apiPut<T>` / `apiDelete<T>`，**禁止页面内直接 `axios.get(...)` 裸调**。
4. 后端统一响应结构：
   - `ApiResult<T> = { code: number; message: string; data: T }`——取值用 `res.data`。
   - 分页 `PageResult<T> = { records: T[]; total: number; pageNum: number; pageSize: number; pages: number }`——**列表在 `records` 字段，不是 `list`**。
5. **鉴权**：Axios 请求拦截器自动附加 `Authorization: Bearer <token>`（token 存 localStorage，由 `useAuthStore` 管理）；响应 401 自动跳 `/login`。
6. 状态管理用 **Pinia**（如 `useAuthStore`、`useXxxStore`），**不用 Vuex**；跨组件共享状态放 store，避免 props 层层透传。
7. 路由：`router/index.ts` 配置路由表；需登录的页面加 `meta: { requiresAuth: true }`，由全局前置守卫 `beforeEach` 校验 token。
8. 组件 `props` / `emits` 用 `defineProps<T>()` / `defineEmits<T>()` 类型化；事件名 `kebab-case`。
9. 列表渲染 `:key` 用后端返回的稳定 `id`，不用数组下标。
10. 表单/异步操作统一 `try/catch` 处理错误并给用户 `message` 提示，禁止静默失败。

## 五、代码评审检查清单（前端）

- [ ] 无 `var`、无 `==`、无字符串拼接、无裸 `require`
- [ ] 分号、单引号、2 空格、trailing comma 一致
- [ ] 无未使用变量/导入、无魔法数字
- [ ] 异步均 `try/catch` 或 `.catch`，无吞异常
- [ ] Vue：组件多词名、`prop` 详定义、`v-for` 带 `:key` 且不混 `v-if`、`computed` 派生
- [ ] TS：无 `any`、strict 开启、函数签名标注、接口/类型分明
- [ ] 请求走 `apiGet/apiPost/...`，分页取 `records`
- [ ] `<script setup>` + `scoped` style

---
> 基线来源：Airbnb JavaScript Style Guide（eslint-config-airbnb-base / airbnb-typescript）；Vue 官方风格指南 vuejs.org/style-guide（Priority A/B/C）。本文件摘录强制与强推荐项，实施结合 ESLint + @typescript-eslint + eslint-plugin-vue 规则。
