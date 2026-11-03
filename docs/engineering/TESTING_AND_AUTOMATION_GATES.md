# KnowFlow 测试、自动化检查与 CI 门禁

> **CURRENT · 2026-11-01** · 本分册定义「什么必须跑、跑不过算不算通过、CI 阻断在哪」。
> 证据分层与发布门禁仍以 [roadmap/07](../roadmap/07_TEST_AND_RELEASE_GATES.md) 为准；本分册不改变全局 **NOT_READY**。

---

## 1. 测试分层

| 层 | 范围 | 工具 | 必须覆盖 |
|---|---|---|---|
| 单元测试 | Store、工具函数、Evidence/路由/策略、Service | Vitest / JUnit 5 + Mockito | 主路径 + 失败路径 + 边界 |
| 组件测试 | 公共组件、页面交互 | Vitest + @vue/test-utils | Props/Emits/空态/错误/键盘 |
| 集成测试 | Controller + Service + 持久化 | Spring Boot Test / HTTP 测试基类 | 权限矩阵、事务、幂等、409 |
| 数据库验证 | 迁移、schema、恢复 | H2 / 私有 MySQL | 迁移双份一致、UNIQUE+逻辑删除 |
| 浏览器 E2E | 真实页面交互 | 真实浏览器 | 1440×900 / 1280×800 / 1024px + 窄屏兼容 |

| 编号 | 规则 | 级别 | 来源 |
|---|---|---|---|
| TG-01 | 新增/修复功能必须新增或更新测试；修复类任务先证明回归用例失败 | MUST | PROJECT |
| TG-02 | 禁止删除测试、跳过测试、放宽断言、只跑局部后声称全量通过 | FORBIDDEN | PROJECT |
| TG-03 | H2 结果不能称为 MySQL 验证；Mock 不能称为真实 Provider E2E | FORBIDDEN | PROJECT |
| TG-04 | 无法验证的项目必须标注 `⚠️ Not verified` / `NOT_VERIFIED` | MUST | PROJECT |
| TG-05 | 测试环境必须保持安全默认（AI/Host/Judge/运行时切库/开发种子关闭） | MUST | PROJECT |

---

## 2. 功能修改必跑门禁

```bash
# 前端
cd frontend
npm test                      # check:ui-prefix + vitest run
npx vue-tsc --noEmit
npx vue-tsc -p tsconfig.app.json --noEmit
npm run build

# 后端
cd backend
mvn -B test                   # 相关测试至少；必要时全量
```

| 编号 | 规则 | 级别 | 来源 |
|---|---|---|---|
| TG-06 | 前端四条命令必须全部执行，不得只跑 `vitest` | MUST | PROJECT |
| TG-07 | 后端至少跑与改动相关的测试；跨模块/公共改动必须全量 `mvn test` | MUST | PROJECT |
| TG-08 | 资源紧张可降低并发（如 `--maxWorkers`），但必须全量并记录参数 | MAY | PROJECT |
| TG-09 | 记录命令、退出码、通过/失败/错误/跳过数与既有失败清单 | MUST | PROJECT |

---

## 3. 统一自动化脚本

新增：`scripts/engineering/check.mjs`（根目录，零依赖，Node 18+）。

```bash
node scripts/engineering/check.mjs          # 全部检查
node scripts/engineering/check.mjs --json   # 机器可读
```

检查项：

| 检查 | 类型 | 规则 |
|---|---|---|
| Agent Skill 副本一致 | 零容忍 | `.agents/skills` ↔ `.trae/skills` ↔ `.workbuddy/skills` 逐字节一致 |
| 工具入口存在 | 零容忍 | `AGENTS.md`、`CLAUDE.md`、`GEMINI.md`、`.cursor/rules/knowflow-engineering.mdc`、`.github/copilot-instructions.md` |
| 前端禁用弹窗 | 零容忍 | `src/` 中 `window.alert/confirm/prompt` 为 0 |
| 后端禁用输出 | 零容忍 | `printStackTrace()` 为 0 |
| `System.out.println` | 棘轮 | 基线 2，只减不增 |
| `Executors.new` / `new Thread(` | 棘轮 | 基线 13 文件，只减不增 |
| TS `any` | 棘轮 | 基线 60，只减不增 |
| `SELECT *` | 棘轮 | 基线 55，只减不增 |
| 物理 `FOREIGN KEY` | 棘轮 | 基线 35，只减不增（存量需逐表登记） |
| 规范文档相对链接 | 零容忍 | `docs/engineering/*.md` 链接可解析 |

| 编号 | 规则 | 级别 | 来源 |
|---|---|---|---|
| TG-10 | 零容忍项新增即失败，不设豁免 | MUST | RECOMMENDATION |
| TG-11 | 棘轮项只能通过修复代码并同步下调基线；提高基线即违规 | FORBIDDEN | RECOMMENDATION |
| TG-12 | 基线文件 `scripts/engineering/baseline.json` 必须与检查脚本同次提交 | MUST | RECOMMENDATION |
| TG-13 | 禁止为了让检查通过而注释掉检查项；删除检查需单独评审 | FORBIDDEN | RECOMMENDATION |

---

## 4. CI 门禁

当前工作流：`.github/workflows/v1-release.yml`

| 作业 | 内容 | 状态 |
|---|---|---|
| `tests` | `mvn -B -f backend/pom.xml test`；`npm ci`；`npx vitest run && npx vue-tsc --noEmit && npm run build && npm run build -- --mode staging` | 已存在 |
| `mysql` | MySQL 8.0 服务、schema 应用、备份恢复比对、`ProductMySqlReleaseTest` | 已存在 |
| `engineering` | `node scripts/engineering/check.mjs` + `npm run check:ui-prefix` | 本次新增 |

| 编号 | 规则 | 级别 | 来源 |
|---|---|---|---|
| TG-14 | CI 必须运行 `npm test`（含 UI 前缀检查），不得直接用 `npx vitest run` 绕过 | MUST | PROJECT |
| TG-15 | CI 必须同时跑 `vue-tsc --noEmit` 与 `vue-tsc -p tsconfig.app.json --noEmit` | MUST | PROJECT |
| TG-16 | CI 必须运行统一工程检查脚本 | MUST | RECOMMENDATION |
| TG-17 | 破坏门禁的提交不得合并；不得以 `--no-verify`、跳过作业、注释检查绕过 | FORBIDDEN | PROJECT |
| TG-18 | CI 通过只证明当次环境与命令；不得外推为权限、付费 AI、MySQL 生产或发布认证 | MUST | PROJECT |

---

## 5. 证据记录格式

每条验证记录必须包含：

1. 日期与候选 `HEAD` + 工作树状态（dirty 文件清单或 SHA）。
2. 环境：Node/Java/浏览器/数据库版本，H2 或 MySQL。
3. 角色与数据夹具。
4. 命令、退出码、通过/失败/错误/跳过数。
5. 证据路径（报告、截图、日志）。
6. 排除项与未验证项。
7. 是否人工批准（与自动化结果分开）。

| 编号 | 规则 | 级别 | 来源 |
|---|---|---|---|
| TG-19 | 历史测试数字不得复用为本轮结果 | FORBIDDEN | PROJECT |
| TG-20 | 自动检查通过不得写成「业务功能 VERIFIED」 | FORBIDDEN | PROJECT |
| TG-21 | 报告使用 PASS / NEEDS_FIX / BLOCKED / NOT_VERIFIED，不用模糊表述 | MUST | PROJECT |

---

## 6. 门禁责任与演进

| 阶段 | 目标 | 不在本阶段做 |
|---|---|---|
| 当前（V1.0） | 零容忍 + 棘轮 + CI 接入 | 不引入 ESLint/Checkstyle 大批依赖 |
| 下一阶段 | 前端接入 ESLint（`no-explicit-any`、Vue Priority A 规则集），后端接入 Checkstyle（先 `warn` 后 `fail`） | 不一次性批量格式化全仓 |
| 再下一阶段 | 覆盖率基线与依赖/秘密扫描 | 不把覆盖率当作业务正确性证明 |

升级 ESLint/Checkstyle 必须单独任务、单独基线、单独验证；不得在本治理任务中顺手引入。

### V1.1-Final 校正与增量实现（2026-11-02）

上文静态历史数字不作为当前阈值；真实值读取 `scripts/engineering/baseline.json`。自测：`node --test scripts/engineering/check.test.mjs`。前端新增 `npm run lint`（指纹增量）、`npm run lint:report`（只读全量）、`node --test scripts/lint.test.mjs`。不因历史问题减少抵消其他文件新增问题；基线与审批快照修改均须人工审阅。详见 [执行报告](GOVERNANCE_V1_1_REPORT.md)。本任务明确授权 ESLint，替代此前“不得在本治理任务引入”的阶段限制；不授权 Checkstyle。CI 尚未接入新增未跟踪文件，不声称远端通过。

用户批准定向暂存后：新增脚本已进入 Git 索引，CI 已接入工程自测与前端 ESLint 自测/增量门禁；远端执行仍 NOT_VERIFIED。此前“尚未接入”仅描述批准前状态。
