# GitHub Copilot Instructions — KnowFlow

本仓库的工程规范以 `docs/engineering/` 为唯一权威，本文件只做指向。

## 必读

- `AGENTS.md` — 强制工作守则与架构铁律
- `docs/engineering/ENGINEERING_STANDARDS.md` — 规范总入口
- `docs/engineering/BACKEND_JAVA_STANDARD.md` — Java/Spring Boot/数据库/并发/异常
- `docs/engineering/FRONTEND_VUE_TS_STANDARD.md` — Vue 3/TypeScript/Pinia/API/UI 写法
- `docs/engineering/SECURITY_AND_AI_STANDARD.md` — 权限/AI/判题/隐私
- `docs/engineering/TESTING_AND_AUTOMATION_GATES.md` — 测试/CI/证据

## 生成代码时必须遵守

- Vue 3 `<script setup lang="ts">`；组件名多词；`v-for` 必须带 key 且不与 `v-if` 同元素；Props/Emits 显式类型。
- 禁止显式 `any`；未知类型用 `unknown` 收窄；禁止 `catch (e: any)`。
- 请求只走 `@/api/request.ts` 的 `apiGet/apiPost/...`；响应为 `Result<T>`，分页取 `records`。
- UI 只用 `--kf-*` 令牌与 `kf` 命名空间；禁止 `window.alert/confirm/prompt`；普通输入聚焦不加外框。
- 后端身份来自认证上下文，归属在 Service 校验；禁止 `SELECT *`、新增物理外键、`System.out.println`、`printStackTrace()`。
- 不重复实现既有唯一事实来源（NextAction / Evidence / 路由白名单 / App Registry / UI Design System / API 请求层）。

## 审核与验证

- 不删除测试、不跳过测试、不改断言制造 PASS。
- 无法验证的结论标注 `⚠️ Not verified`；不得把规范写入说成全站实现。
- 提交前运行 `node scripts/engineering/check.mjs` 与对应前后端门禁。

Java 注释统一执行 `docs/engineering/BACKEND_JAVA_STANDARD.md` BJ-67（路径相对仓库根）：Entity/DTO/VO 全字段、record 组件、公共业务方法及复杂逻辑须说明真实业务约束；存量分批治理，禁止猜测或覆盖他人 WIP。
