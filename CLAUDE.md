# CLAUDE.md — KnowFlow Claude Code 入口

> 本文件只做指向，不复制规范正文。规范正文以 `docs/engineering/` 为唯一权威。

## 开始任务前

1. 读 [AGENTS.md](AGENTS.md) — 强制工作守则与架构铁律。
2. 读 [docs/engineering/ENGINEERING_STANDARDS.md](docs/engineering/ENGINEERING_STANDARDS.md) — 规范总入口、优先级、规则等级。
3. 按任务类型读对应分册：
   - [BACKEND_JAVA_STANDARD.md](docs/engineering/BACKEND_JAVA_STANDARD.md)
   - [FRONTEND_VUE_TS_STANDARD.md](docs/engineering/FRONTEND_VUE_TS_STANDARD.md)
   - [SECURITY_AND_AI_STANDARD.md](docs/engineering/SECURITY_AND_AI_STANDARD.md)
   - [TESTING_AND_AUTOMATION_GATES.md](docs/engineering/TESTING_AND_AUTOMATION_GATES.md)
4. 读 [docs/README.md](docs/README.md) 找到任务的现行权威入口（功能状态看 `docs/roadmap/02`）。

## 硬约束

- 禁止破坏性 Git（`reset --hard`、`clean -fd`、`checkout .`、`restore .`、擅自 stash）；不自动 commit/push。
- 不开发第二套学习状态、Evidence、NextAction、路由白名单、App Registry、UI 设计系统或请求封装。
- 禁止 `window.alert/confirm/prompt`、`printStackTrace()`、新增 `System.out.println`、新增 `SELECT *`、新增物理外键。
- 不删除测试、不跳过测试、不改断言制造 PASS；不能验证的标注 `⚠️ Not verified`。

## 提交前

```bash
node scripts/engineering/check.mjs
cd frontend && npm test && npx vue-tsc --noEmit && npx vue-tsc -p tsconfig.app.json --noEmit && npm run build
cd backend && mvn -B test
```

纯文档改动只跑工程检查与文档链接检查，不伪报业务 E2E。
