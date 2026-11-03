---
name: knowflow-engineering
description: KnowFlow 统一工程规范入口。任何 AI 编程智能体在修改 KnowFlow 代码、数据库、接口、UI、测试、CI 或评审前加载本技能，按统一入口读取后端/前端/安全/测试门禁分册并执行自动检查。When working on the KnowFlow repository, or when the user mentions 工程规范 / 开发规范 / 代码评审 / 测试门禁 / CI / 提交前检查 / engineering standards，trigger this skill.
agent_created: true
version: 1.0.0
---

# KnowFlow 统一工程规范（Agent 入口）

本技能只做**路由与硬约束**，正文在 `docs/engineering/`，不复制分册内容，避免多副本漂移。

## 何时使用

- 修改或评审 KnowFlow 任意代码、SQL、配置、文档、CI
- 新建页面、接口、迁移、公共组件、Skill
- 准备提交/交接前自检
- 用户要求「按规范做」「检查是否符合规范」

## 读取顺序（必须）

1. `AGENTS.md` — 强制工作守则与架构铁律
2. `docs/engineering/ENGINEERING_STANDARDS.md` — 规范总入口与优先级
3. 本任务对应分册：
   - 后端 / 数据库 / 并发 / 异常 → `docs/engineering/BACKEND_JAVA_STANDARD.md`
   - 前端 / Vue / TS / Pinia / API / UI 写法 → `docs/engineering/FRONTEND_VUE_TS_STANDARD.md`
   - 权限 / 认证 / AI / 判题 / 隐私 → `docs/engineering/SECURITY_AND_AI_STANDARD.md`
   - 测试 / 脚本 / CI / 证据 → `docs/engineering/TESTING_AND_AUTOMATION_GATES.md`
4. 任务相关源码与 `docs/README.md` 指定的现行入口

## 硬约束（摘要）

- **FORBIDDEN**：破坏性 Git（`reset --hard`、`clean -fd`、`checkout .`、`restore .`、擅自 stash）、自动 commit/push、覆盖未知未提交代码。
- **FORBIDDEN**：开发第二套学习状态 / Evidence / NextAction / 路由白名单 / App Registry / UI 设计系统 / 请求封装。
- **FORBIDDEN**：`window.alert|confirm|prompt`、`printStackTrace()`、新增 `System.out.println`、新增 `SELECT *`、新增物理 `FOREIGN KEY`、新增 `Executors.new` / `new Thread(`。
- **FORBIDDEN**：删除测试、跳过测试、改断言制造 PASS、把 Mock 说成真实 E2E、把规范写入说成全站实现。
- **MUST**：身份来自服务端认证上下文；归属在 Service 校验；错误同时断言 HTTP 与 `body.code`。
- **MUST**：前端异步视图覆盖 Loading / Success / Empty / Error / Retry；UI 只用 `--kf-*` 与 `kf` 命名空间。
- **MUST**：普通输入浅灰无边框、聚焦无蓝/灰外框；该合同不得扩大为全局 `outline: none`。

## 提交前门禁

```bash
node scripts/engineering/check.mjs

cd frontend
npm test
npx vue-tsc --noEmit
npx vue-tsc -p tsconfig.app.json --noEmit
npm run build

cd backend
mvn -B test
```

纯文档改动只跑 `node scripts/engineering/check.mjs` 与文档链接检查，不伪报业务 E2E。

## 证据与报告

- 状态只用 PASS / NEEDS_FIX / BLOCKED / NOT_VERIFIED 或 VERIFIED / IMPLEMENTED / PARTIAL / DISABLED / NOT_IMPLEMENTED。
- 完成输出 11 项执行报告（目标、原因、文件、业务影响、UI 影响、数据流影响、测试、Build、浏览器验证、遗留、下一步）。
- 无法验证标注 `⚠️ Not verified`。

## 相关技能

- `alibaba-code-standard` — Java/数据库强制项速查
- `frontend-code-standard` — Airbnb/Vue/TS 强制项速查

本技能在 `.agents/skills/`、`.trae/skills/`、`.workbuddy/skills/` 三处必须逐字节一致；修改后运行检查脚本。
