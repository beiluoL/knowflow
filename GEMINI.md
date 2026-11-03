# GEMINI.md — KnowFlow Gemini CLI 入口

> 本文件只做指向，不复制规范正文。规范正文以 `docs/engineering/` 为唯一权威。

## 开始任务前

1. 读 [AGENTS.md](AGENTS.md)。
2. 读 [docs/engineering/ENGINEERING_STANDARDS.md](docs/engineering/ENGINEERING_STANDARDS.md)。
3. 按任务类型读 [后端](docs/engineering/BACKEND_JAVA_STANDARD.md) / [前端](docs/engineering/FRONTEND_VUE_TS_STANDARD.md) / [安全与 AI](docs/engineering/SECURITY_AND_AI_STANDARD.md) / [测试与门禁](docs/engineering/TESTING_AND_AUTOMATION_GATES.md) 分册。
4. 功能状态与路线从 [docs/README.md](docs/README.md) 进入，不按历史报告猜测。

## 硬约束

- 禁止破坏性 Git；不自动 commit/push；不覆盖未知未提交代码。
- 不重复实现既有唯一事实来源（NextAction / Evidence / 路由白名单 / App Registry / UI Design System / API 请求层）。
- 禁止 `window.alert/confirm/prompt`、`printStackTrace()`、新增 `System.out.println`、新增 `SELECT *`、新增物理外键。
- 不删除测试、不跳过测试、不改断言制造 PASS；无法验证标注 `⚠️ Not verified`。

## 提交前

```bash
node scripts/engineering/check.mjs
cd frontend && npm test && npx vue-tsc --noEmit && npx vue-tsc -p tsconfig.app.json --noEmit && npm run build
cd backend && mvn -B test
```
