# KnowFlow 统一工程规范总入口

> **CURRENT · 2026-11-01 · 适用于所有 AI 编程智能体与人类工程师。**
> 本文件是「怎么做工程」的总入口；产品、架构、需求、能力与路线仍由既有权威文档负责，本规范**不建立第二套**架构/PRD/能力矩阵。

---

## 1. 目的与边界

**目的**：把项目规范从「依赖 AI 自觉遵守」升级为「规范指引 + 工具检查 + 自动测试 + CI 阻断」。

**边界**：

- 本规范只约束**开发方式**：编码、数据库、接口、UI 复用、安全、测试、Agent 协作。
- 不约束产品优先级。功能状态唯一入口仍是 [roadmap/02 能力矩阵](../roadmap/02_PRODUCT_CAPABILITY_MATRIX.md)。
- 架构关系仍是 [PROJECT_ARCHITECTURE](../PROJECT_ARCHITECTURE.md)。
- UI 控件合同仍是 [UI_DESIGN_SYSTEM_V2](../UI_DESIGN_SYSTEM_V2.md)；本规范只说明「如何遵守」，不复制视觉条款。

---

## 2. 规范优先级（冲突时按此顺序判定）

规范权威（Java 后端）：根 AGENTS.md > backend/AGENTS.md > BACKEND_JAVA_STANDARD.md > 架构文档 > scripts/engineering > 测试代码。

事实证据：当前源码/真实行为 > 本轮实际执行证据 > 最新有效执行报告 > 历史报告/提案。事实证据说明“已经怎样”，不授权以存量违规覆盖正式规则。

补充判定：

1. 源码与本规范冲突时，**先记录 Documentation Drift**，不擅自改代码迎合旧文档，也不擅自改规范迎合坏实现。
2. 同一主题出现两份互相矛盾的规范时，以 [DOCUMENTATION_GOVERNANCE](../DOCUMENTATION_GOVERNANCE.md) 指定的唯一入口为准。
3. 本规范与 `AGENTS.md` 的架构铁律冲突时，**AGENTS.md 的架构铁律优先**（NextAction、Evidence、路由白名单、上下文盖章、App Registry 不可绕过）。

---

## 3. 规则等级与来源标注

| 标记 | 含义 | 违反后果 |
|---|---|---|
| **MUST** | 必须遵守的强制项 | 阻塞合并/CI；已存在的存量违规进入棘轮基线，只减不增 |
| **SHOULD** | 强烈推荐 | 需在评审中说明理由；新代码默认遵守 |
| **MAY** | 可选 | 不构成违规 |
| **FORBIDDEN** | 明令禁止 | 一律阻塞，不允许以「历史遗留」新增 |

| 来源 | 含义 |
|---|---|
| **OFFICIAL** | 上游官方规范（阿里巴巴 Java 开发手册、Vue 官方风格指南、Airbnb、OWASP、Spring Boot） |
| **PROJECT** | KnowFlow 项目既有真实约定（已由源码或现行文档确认） |
| **RECOMMENDATION** | 本次治理新增的工程建议，尚未被项目历史验证 |
| **LEGACY** | 历史规范，需确认后才能继续沿用 |

每条规则的写法：`[等级][来源] 规则内容`。无法提供证据的条目不写成本规范。

---

## 4. 规范分册入口

| 分册 | 覆盖范围 | 主要上游依据 |
|---|---|---|
| [BACKEND_JAVA_STANDARD](BACKEND_JAVA_STANDARD.md) | Java 17、Spring Boot 3.2、MyBatis-Plus、MySQL/H2、并发、异常、日志 | 阿里巴巴 Java 开发手册、Spring Boot 官方、Java 并发实践 |
| [FRONTEND_VUE_TS_STANDARD](FRONTEND_VUE_TS_STANDARD.md) | Vue 3 `<script setup>`、TypeScript、Pinia、请求层、UI 复用 | Airbnb JS Style Guide、Vue 3 官方风格指南、TypeScript ESLint、UI V2 |
| [SECURITY_AND_AI_STANDARD](SECURITY_AND_AI_STANDARD.md) | 认证/归属/权限、输入校验、AI Provider、判题与宿主能力、隐私 | OWASP ASVS 5.0、OWASP Top 10、项目安全默认 |
| [TESTING_AND_AUTOMATION_GATES](TESTING_AND_AUTOMATION_GATES.md) | 测试分层、命令门禁、脚本检查、CI 阻断、证据记录 | 项目 roadmap/07、CI 现状 |

**既有入口（不复制、只引用）**：

- 行为守则：[AGENTS.md](../../AGENTS.md)
- 开发流程：[DEVELOPMENT_PROTOCOL.md](../DEVELOPMENT_PROTOCOL.md)
- UI 合同：[UI_DESIGN_SYSTEM_V2.md](../UI_DESIGN_SYSTEM_V2.md)
- 文档治理：[DOCUMENTATION_GOVERNANCE.md](../DOCUMENTATION_GOVERNANCE.md)
- Agent 交接：[AGENT_HANDOFF.md](../AGENT_HANDOFF.md)

---

## 5. Agent Skills 入口

- 统一 Skill：`.agents/skills/knowflow-engineering/SKILL.md`（权威正文）。
- 兼容副本：`.trae/skills/knowflow-engineering/`、`.workbuddy/skills/knowflow-engineering/`。
- 既有代码规范 Skill：`alibaba-code-standard`、`frontend-code-standard`（三处同源，由脚本校验一致）。
- **MUST[PROJECT]** 任何 Skill 修改后必须在三处保持逐字节一致；不一致即 CI 失败。
- **FORBIDDEN[PROJECT]** 安装未经审查的第三方 Skill；新增 Skill 必须先在 `docs/engineering/` 有对应正文。

各工具入口文件：

| 工具 | 入口 |
|---|---|
| Codex / Trae / 通用 | `AGENTS.md` |
| Claude Code | `CLAUDE.md` |
| Gemini CLI | `GEMINI.md` |
| Cursor | `.cursor/rules/knowflow-engineering.mdc` |
| GitHub Copilot | `.github/copilot-instructions.md` |

入口文件**只做指向与最小硬约束**，不复制分册正文，避免多点漂移。

---

## 6. 自动化门禁入口

```bash
# 统一工程检查（根目录）
node scripts/engineering/check.mjs

# 前端 UI 前缀
cd frontend && npm run check:ui-prefix

# 前端测试 + 类型 + 构建
cd frontend && npm test && npx vue-tsc --noEmit && npm run build

# 后端
cd backend && mvn -B test
```

门禁分两级：

1. **零容忍**（当前基线为 0，新增即失败）：`window.alert/confirm/prompt`、`printStackTrace()`、Skill 副本不一致、工具入口缺失。
2. **棘轮**（当前有存量，只减不增）：`any`、`System.out.println`、`Executors`/`new Thread`、`SELECT *`、物理 `FOREIGN KEY`。

基线文件：`scripts/engineering/baseline.json`。**MUST[PROJECT]** 降低基线需同时提交代码修复与基线更新；**FORBIDDEN[PROJECT]** 为了提高基线数字而放宽检查。

---

## 7. 规范变更流程

1. **提议**：说明要解决的问题、影响范围、是否改变既有合同。
2. **取证**：扫描真实源码/配置，记录现状；禁止按历史报告猜测。
3. **落文**：只修改对应分册的唯一段落；跨分册影响需同步修改引用。
4. **同步**：更新 `docs/README.md` 入口表、`.agents/skills/**` 与兼容副本、必要的 CI/脚本。
5. **验证**：运行 `node scripts/engineering/check.mjs`；纯文档改动另跑文档校验；不伪报业务测试。
6. **记录**：在 `AGENT_HANDOFF.md` 追加日期、范围、文件、命令、结果、未验证项。

**FORBIDDEN**：为引入新规则而批量重写历史文档；删除历史证据；把「规范已写入」说成「全站已实现」。

---

## 8. 责任与执行

- 人类工程师与所有 AI 智能体对本规范同等适用。
- Agent 在动手前必须完成：读 `AGENTS.md` → 读本入口 → 读对应分册 → 读任务相关源码。
- Agent 在完成后必须：运行适用门禁 → 输出执行报告（11 项）→ 需要时更新交接。
- 本规范不改变发布状态：全局仍为 **NOT_READY**，任何新规范落地不等于发布门禁通过。

### V1.1-Final 本地增量门禁（2026-11-02）

新增 `node --test scripts/engineering/check.test.mjs`，以及前端 `npm run lint`、`npm run lint:report`、`node --test scripts/lint.test.mjs`。实现与限制见 [V1.1 执行报告](GOVERNANCE_V1_1_REPORT.md)。关键文件 Git 可移植性和 CI 接入仍阻断，不能称跨设备部署成功。

Java 注释统一执行 `docs/engineering/BACKEND_JAVA_STANDARD.md` BJ-67（路径相对仓库根）：Entity/DTO/VO 全字段、record 组件、公共业务方法及复杂逻辑须说明真实业务约束；存量分批治理，禁止猜测或覆盖他人 WIP。
