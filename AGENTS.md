# AGENTS.md — KnowFlow Agent 工作守则

> **本文件是所有 Agent（含人类工程师）在 KnowFlow 仓库工作的强制入口。**
> 基于 2026-09-10 真实代码扫描，非臆造。

---

## 文档接管与同步入口（2026-10-24）

开始任务依次读取：[VIBE_CONTEXT](VIBE_CONTEXT.md) → [文档中心](docs/README.md) → [当前状态roadmap/01](docs/roadmap/01_CURRENT_SYSTEM_AUDIT.md) → [下一轮roadmap/08](docs/roadmap/08_NEXT_EXECUTION_PLAN.md) → 本任务架构/PRD/模块源码。

- 功能状态唯一入口是[roadmap/02能力矩阵](docs/roadmap/02_PRODUCT_CAPABILITY_MATRIX.md)，架构为PROJECT_ARCHITECTURE，需求沿用产品需求/PRD-功能需求文档，路线沿用04/08；不要再建第二套“最新PRD/能力矩阵/路线”。
- 证据优先级：当前源码/真实行为→本轮实际测试→有效最新报告→历史→提案。测试文件/页面存在不等于VERIFIED；实现与运行验证分开。H2/5.7/8/生产、技术审阅/人工批准、AI评价/客观成绩不能混写。
- 当前普通笔记是服务端主库；SmartWriting仍本地草稿且有模拟回退。T1/G1/G2有历史执行，不重复当未实施；HashMap新包人工批准/受控发布仍阻断。全局发布NOT_READY，未经实际门禁不改READY。
- 每次迭代同步模块/矩阵/PRD/状态/路线和交接；架构或UI另同步权威规范。历史报告保留原日期与结果，SUPERSEDED规划不能覆盖现行规则；见[文档治理](docs/DOCUMENTATION_GOVERNANCE.md)。
- 文档治理只改文档/必要校验脚本，不借机修改业务、生产配置、Schema或正式数据；归档不删证据，删除仅列候选待用户批准。纯文档做链接/路径/锚点/状态/Diff检查，不伪报新业务E2E。

---

## 统一工程规范入口（2026-11-01，适用所有 AI 工具）

所有 AI 编程工具（WorkBuddy / Trae / Codex / Cursor / Claude Code / GitHub Copilot / Gemini CLI）统一从 [docs/engineering/ENGINEERING_STANDARDS.md](docs/engineering/ENGINEERING_STANDARDS.md) 进入后端、前端、安全与 AI、测试与门禁分册。本文件仍是架构铁律与工作守则，不被分册替代。

- 后端：[BACKEND_JAVA_STANDARD](docs/engineering/BACKEND_JAVA_STANDARD.md)；前端：[FRONTEND_VUE_TS_STANDARD](docs/engineering/FRONTEND_VUE_TS_STANDARD.md)；安全：[SECURITY_AND_AI_STANDARD](docs/engineering/SECURITY_AND_AI_STANDARD.md)；测试/CI：[TESTING_AND_AUTOMATION_GATES](docs/engineering/TESTING_AND_AUTOMATION_GATES.md)。
- 提交前运行 `node scripts/engineering/check.mjs`；零容忍项新增即失败，棘轮项只减不增（基线 `scripts/engineering/baseline.json`）。
- Skill 权威副本在 `.agents/skills/`，`.trae/skills/` 与 `.workbuddy/skills/` 必须逐字节一致，修改后运行上述检查。
- 规范现状与冲突项见 [工程规范审计](docs/engineering/AUDIT_2026-11-01.md)；该审计是静态结论，不构成功能或发布 VERIFIED。

---

## 当前 UI 开发约束（2026-10-24，持续有效）

UI专项规则以 [docs/UI_DESIGN_SYSTEM_V2.md](docs/UI_DESIGN_SYSTEM_V2.md) 第0节及正文为准；产品哲学/导航见 [docs/UI_UX_SYSTEM.md](docs/UI_UX_SYSTEM.md)。历史报告、旧交接中的外观方案不能覆盖现行规范。以下是后续Agent必须遵守的项目记忆：

- **普通输入、数字、搜索、Textarea、普通Select保留统一浅灰表面；默认无明显边框；鼠标或键盘聚焦均不出现蓝色或中性灰border/outline/ring/外圈阴影。** 不得恢复旧灰色键盘环、蓝色软环或整块透明表面方案。
- 组合控件由外壳承载背景/圆角，内部输入透明；图标、提示文字、快捷键必须留间距，通常8–12px，顶部搜索12px。使用Flex/Grid+gap，图标定位需预留padding，避免公共样式覆盖。
- 复用Input/Textarea/Select/FormField；同排kf-form-row共享label/control/meta轨道，长标签/帮助/错误不推歪邻列，窄屏单列。单行默认44px，尺寸/颜色/字体使用已有Token，不复制第二套主题。
- 不影响真实label/ARIA、错误、readonly/disabled、number空值/0/min/max/step、v-model、IME、提交/快捷键；代码/Markdown/文件/日期等特殊控件不机械套用普通重置。
- 普通字段无外框不等于全局outline:none：按钮、菜单项、Checkbox/Radio保留各自可见键盘反馈；强制颜色模式保留系统指示。AIComposer内部textarea无框，外壳按其现有独立组件规则，不伪称所有容器已无边界。
- 菜单复用现有白色轻浮层；平面Select选中用右侧Check、默认无永久灰底；二次确认复用ConfirmDialog/AppDialog及confirmDialog，异步防重/失败留错误，不另造系统。
- Checkbox复用公共组件：20px正方形、3px微圆角、蓝底白Check/半选Minus、文字gap8px；无默认/hover/鼠标灰影，键盘和forced-colors保留outline；不替换Switch/Radio/菜单Check，不改FSRS/筛选数据。
- 图标复用Icon支持的SVG和属性，按钮有可访问名称；局部类用fav-*等前缀，避免icon-*与全局图标字体冲突。
- V3组件标准见UI_DESIGN_SYSTEM_V2第3/4/5/8节：Button 32/38/44px、默认14/20/500、6px圆角、图文8px（紧凑6px），常规无装饰阴影；确认/AI发送用专用Token。规范写入不等于全站实现，源码差异和验收须单列，不改变普通输入无聚焦外框合同。
- 保留既有字体角色和Type Scale，业务数据流/唯一Store/API不因UI改造重写；公共控件与组卷页对齐已落地，不宣称全站旧布局均迁移。

---

## 一、第一原则：理解再动手

**代码是真实事实。文档是辅助信息。**

在修改任何代码之前，必须：

1. 真实扫描相关代码（不是猜文件名）
2. 真实理解数据流（不是看旧文档）
3. 真实理解路由、Store、API（不是凭名字猜）
4. 先说明影响范围，再动手

**禁止**：

- 根据文件名猜架构
- 根据旧文档猜实现
- 随意重写已有功能
- 创建重复系统
- 覆盖已有设计
- 为了"更漂亮"破坏业务逻辑
- 为了测试通过删除测试

---

## 二、核心架构铁律

### 2.1 NextAction 是统一事实来源

```
stores/learning.ts = NextAction 唯一计算入口
```

**禁止**：

- Home 自己计算 NextAction
- Learning Center 自己计算 NextAction
- AI 自己计算 NextAction
- Flashcard 自己决定下一步
- 任何页面自行拼装上下文

**必须**复用：

- `resolveNextAction()` — [learning/action-priority.ts](frontend/src/learning/action-priority.ts)
- `useLearningStore()` — [stores/learning.ts](frontend/src/stores/learning.ts)

### 2.2 Evidence 管线不可绕过

```
Event → resolveEvidenceBatch() → aggregateEvidence() → diagnoseFromEvidence() → buildTopCandidate() → resolveNextAction()
```

全部位于 [learning/evidence/](frontend/src/learning/evidence) 目录。

**三条硬规则**（来自 [aggregate.ts](frontend/src/learning/evidence/aggregate.ts) §18）：

1. **禁止单次满分**：单条证据再高，聚合分也被样本上限压住（n=1→50, n=2→67, n=3→83, n≥4→100）
2. **时间衰减**：半衰期 7 天，越近权重越高
3. **置信度分离**：分数与置信度分开输出，少样本时置信度低但不假装数据充分

**绝对禁止**：

- 编造 Evidence（没有事件不产出证据）
- 编造 Diagnosis（无证据返回 `[]`，不返回"状态良好"）
- 绕过置信度直接给满分

### 2.3 路由白名单不可绕过

所有 NextAction → Route 必须经过 [learning/route.ts](frontend/src/learning/route.ts) 的白名单校验：

- `ACTION_ROUTES`：核心固定映射
- `EVIDENCE_PRACTICE_ROUTES`：Evidence 策略允许落地的页面
- `KNOWLEDGE_DEEP_LINK_ROUTES`：支持知识点深链的页面
- `CHAPTER_FILTER_ROUTES`：支持章节过滤的页面

**禁止**：返回任意远程 URL / `javascript:` / 未注册的路由。

### 2.4 上下文盖章不可跳过

任何入口跳转到学习页面时，必须通过 [learning/action-contract.ts](frontend/src/learning/action-contract.ts) 的 `stampActionContext()` + `resolveContextualRoute()` 携带上下文。

`LEARNING_ENTRY_CONTEXT` 记录了每个页面**真正消费**的参数（由真实代码核对），**禁止**给不消费参数的页面制造假深链。

### 2.5 Mini App Registry 是唯一注册源

所有 Mini App 必须在 [apps/registry.ts](frontend/src/apps/registry.ts) 注册。

- APP_DEFINITIONS 是数量与入口的事实来源；2026-10-24静态快照为18项，不固定为永久数量，路由/开放范围须查当前矩阵
- `APP_NAV_META` 是导航分区的唯一来源
- **禁止**在 Sidebar / Home / LearningCenter 各自硬编码 App 分类

---

## 三、绝对不能重复实现的逻辑

| 逻辑 | 唯一实现位置 | 禁止行为 |
|------|-------------|----------|
| NextAction 决策 | `learning/action-priority.ts` → `resolveNextAction()` | 任何页面自行计算下一步 |
| Evidence 解析 | `learning/evidence/resolver.ts` → `resolveEvidenceBatch()` | 任何页面自行解析事件 |
| Evidence 聚合 | `learning/evidence/aggregate.ts` → `aggregateEvidence()` | 任何页面自行聚合分数 |
| 能力诊断 | `learning/evidence/diagnosis.ts` → `diagnoseFromEvidence()` | 任何页面自行诊断 |
| 策略推导 | `learning/evidence/strategy.ts` → `resolveEvidenceStrategy()` | 任何页面自行推荐策略 |
| 候选动作构建 | `learning/evidence/action-candidate.ts` → `buildTopCandidate()` | 任何页面复制候选推导 |
| 路由白名单 | `learning/route.ts` → `resolveActionRoute()` | 直接 `router.push` 裸路由 |
| 上下文盖章 | `learning/action-contract.ts` → `stampActionContext()` | 手动拼 query 参数 |
| Knowledge Context | `learning/context.ts` → `resolveLearningContext()` | 任何页面自行解析知识点上下文 |
| DAG 节点解析 | `learning/path-node.ts` → `resolveCurrentChapterNode()` | 任何页面自行取"当前章节" |
| App 注册 | `apps/registry.ts` → `APP_DEFINITIONS` | 任何页面自行硬编码 App 列表 |
| App 状态管理 | `stores/apps.ts` → `useAppStore()` | 直接操作 localStorage |
| 学习状态 | `stores/learning.ts` → `useLearningStore()` | 创建第二套学习状态 Store |
| UI 设计令牌 | `style.css` + `styles/ui-tokens.css` 的 `--kf-*` 变量 | 写死十六进制色值或像素值 |
| Toast / Dialog | `utils/toast` → `notify` / `confirmDialog` / `promptDialog` | 使用 `window.alert` / `window.confirm` / `window.prompt` |

---

## 四、Agent 可修改区域

### ✅ 自由修改

- `frontend/src/views/` 下的页面组件（不含核心逻辑变更）
- `frontend/src/components/` 下的 UI 组件
- `frontend/src/api/` 下的请求封装（新增接口、调整参数）
- `backend/src/main/java/com/knowflow/<module>/controller/` 新增 Controller
- `backend/src/main/java/com/knowflow/<module>/service/` 新增 Service
- `backend/src/main/java/com/knowflow/<module>/dto/` / `vo/` / `entity/` 新增类型
- `docs/` 下的文档

### ⚠️ 谨慎修改（需理解影响范围）

- [stores/learning.ts](frontend/src/stores/learning.ts) — 学习状态唯一真源，影响全站
- [learning/](frontend/src/learning) 整个目录 — 学习架构核心
- [apps/registry.ts](frontend/src/apps/registry.ts) — App 注册表
- [config/navigation.ts](frontend/src/config/navigation.ts) — 导航配置
- [router/index.ts](frontend/src/router/index.ts) — 路由表
- `backend/.../service/impl/LearningServiceImpl.java` — 学习核心服务
- `backend/.../mastery/MasteryEngine` — 掌握度引擎
- `backend/src/main/resources/schema.sql` — 数据库结构

### 🚫 禁止修改（除非有明确架构升级指令）

- `learning/evidence/` 下文件的算法逻辑（聚合规则、诊断阈值、置信度公式）
- `learning/action-priority.ts` 的优先级链顺序
- `learning/route.ts` 的白名单（只能追加，不能删除已有条目）
- `learning/action-contract.ts` 的 `LEARNING_ENTRY_CONTEXT` 映射（只能追加新页面，不能删已有映射）

---

## 五、Git 工作规则

1. **禁止 `git reset --hard` / `git clean -fd` / `git checkout .` / `git restore .`**
2. **禁止自动 commit** — 只有功能变动较大时才提交本地 git commit
3. **禁止自动 push** — 不主动 push 到 GitHub / Gitee，除非用户明确要求
4. **不提交 `design/` 和 `mobile/` 目录**
5. **不提交 `application-local.yml` / `.env` 等含 API Key 的敏感文件**
6. 提交信息格式：`feat: <版本主题>(<核心模块简述>)` + 正文分模块列出
7. 未提交修改可能来自其他 Agent — 先理解，不确定归属时不覆盖

---

## 六、测试规则

任何功能修改必须：

1. 运行测试 — `cd frontend && npx vitest run`
2. 类型检查 — `cd frontend && npx vue-tsc --noEmit`
3. Build — `cd frontend && npm run build`

**禁止**：

- 删除测试
- 修改断言制造 PASS
- 只运行局部测试然后声称全部通过

---

## 七、浏览器验证规则

- 浏览器能测的：**必须真实测**
- 不能测的：明确标注 `⚠️ Not verified`
- **绝对不能虚构 PASS**

启动前端：`cd frontend && npm run dev`

---

## 八、完成后必须写执行报告

每次工作完成，输出：

1. 修改目标
2. 修改原因
3. 修改文件
4. 业务影响
5. UI 影响
6. 数据流影响
7. 测试
8. Build
9. 浏览器验证
10. 遗留问题
11. 下一步建议

---

## 九、相关文档

- [VIBE_CONTEXT.md](VIBE_CONTEXT.md) — Vibe Coding 上下文
- [docs/PROJECT_ARCHITECTURE.md](docs/PROJECT_ARCHITECTURE.md) — 项目架构
- [docs/UI_UX_SYSTEM.md](docs/UI_UX_SYSTEM.md) — UI/UX 系统
- [docs/DEVELOPMENT_PROTOCOL.md](docs/DEVELOPMENT_PROTOCOL.md) — 开发协议
- [docs/AGENT_HANDOFF.md](docs/AGENT_HANDOFF.md) — Agent 交接

## 十、Codex 执行约定（2026-10-01 补充）

以下补充保留前文架构铁律；当前事实以真实代码为准，扫描快照不作为永久数量约束。

### Project Context
AI 个人知识管理 + 自适应学习；当前只开发普通用户 PC 主界面。仓库内 `desktopApp/`、`mobile/`、`miniprogram/` 是预期迁移删除，不恢复、不扫描、不修改。桌面工程在 `/Users/beiluo/Documents/alProject/qoderProject/desktopApp`，移动工程在 `/Users/beiluo/Documents/alProject/qoderProject/lectoforge_mobile`。

### Before Editing
先检查 status、diff、cached diff、branch、HEAD、最近历史与 untracked；读取本文件及任务相关文档，真实追踪路由、Store、API 和服务，先说明影响范围。

### Git Safety
未知修改属于用户或其他 Agent；禁止 reset/clean/全仓 restore/checkout/stash。不得自动 commit/push；只有用户明确授权才提交。本次接管仅改文档，不提交。根目录一键启停脚本调用 `knowflow-dev.sh`；运行前检查 8080/5173 归属，脚本会终止占用端口的进程，不能误杀仓库外服务。

### Architecture First
保留第二、三节唯一事实来源。`buildTopCandidate()` 当前位于 `learning/evidence/action-candidate.ts`；`strategy.ts` 推导策略，不能按旧路径复制实现。Registry 数量以 `APP_DEFINITIONS` 为准，不以历史“13 个”限制新注册。

### Frontend Rules
Vue 3、TypeScript、Composition API、`script setup`、Pinia、Vue Router。改 UI 前读 UI_UX_SYSTEM 并复用 `style.css`、`styles/ui-tokens.css`、现有 UI 组件；API 必须区分 Loading/Success/Empty/Error/Retry，不用 `.catch(() => [])` 把失败装成空数据。

### Backend Rules
完整 trace Controller → Service → Mapper → 表/索引；先找根因，不通过宽泛 catch 掩盖失败。DTO 身份由服务端认证上下文确定，不信任请求内 userId/role/owner。

### Security & Ownership
逐项验证 Anonymous、Owner、Other User、Admin；知识库额外验证 EDITOR/READER。UI 隐藏不是授权，资源 ID 必须在后端校验归属。本地宿主机读写/运行代码与开发特权不能被默认当作生产安全能力。

### HTTP Semantics
按业务明确 400/401/403/404/409/500，同时断言 HTTP 与 body.code。单参数 BusinessException 仍保留兼容假成功风险，禁止机械替换；认证入口复用 SecurityUtils。

### Database & Logical Delete
`deleted=1` 不会释放 UNIQUE。验证重复注册、删除/恢复、重复关系操作、并发；检查 row count、owner、deleted、索引和副作用。新注册不能复用旧用户 ID 继承私有数据；H2 通过不代表 MySQL 约束已验证。

### Debugging Protocol
Reproduce → Evidence → Root Cause → Regression Test → Minimal Fix → Relevant Tests → Full Verification。修复前应证明回归测试失败；无法复现要标记静态风险，不能宣称已修复。

### Testing Protocol
功能修改执行后端相关测试及必要全量 `mvn test`，前端全量 `npx vitest run`、`npx vue-tsc --noEmit`、`npm run build`。记录命令、通过/失败/跳过数、退出状态，区分既有失败与本次引入；不删除测试、不改断言制造 PASS。

### UI Verification
真实浏览器检查交互、Console、HTTP/Network 与响应式；考虑 1440×900、1280×800、1024px，390×844/375×812 仅作为兼容检查，不扩展当前 PC 开发范围。没有浏览器网络抓包能力时明确说明，curl 不能冒充浏览器 Network 验证。

### Definition of Done
满足需求、通过适用验证、检查最终 diff/status、无意外业务修改/删除/Secret，并输出第八节执行报告。测试通过不等于权限、付费 AI、生产部署已验证；缺项写 `⚠️ Not verified`。

### Documentation
AGENTS=工作规则；VIBE_CONTEXT=产品上下文；PROJECT_ARCHITECTURE=架构；UI_UX_SYSTEM=设计；DEVELOPMENT_PROTOCOL=流程；CODEX_PROJECT_TAKEOVER=当前审计快照。冲突先记 Documentation Drift，不能改代码迎合旧文档。本阶段不安装第三方 Skill，不新建 Agent Framework。

### Agent Handoff
完成后向 AGENT_HANDOFF 追加日期、范围、文件、Git、命令与结果、真实调用链、风险/未验证项及一个下一任务，不删除历史记录；不将历史测试数字当作新基线。

### UI组件维护补充（2026-10-25）

开发环境组件中心`/dev/ui-components`的注册表为`frontend/src/dev/registry.ts`，仅展示注册，不替代业务App Registry或设计Token。新公共组件同步真实Props/示例/源码路径/无障碍说明/测试；预览只用本地数据，禁止AI或学习记录写入。Button已校准32/38/44与稳定loading，ghost保留旧描边接口；页面自绘按钮仍逐步迁移。状态与验证见docs/component-playground/REPORT.md。

---

## 十一、企业级工程治理检查单（2026-11-03）

> 本节是「开工前/收工前」的强制检查单，**不复制**规范正文。
> Java 正文见 [docs/engineering/BACKEND_JAVA_STANDARD.md](docs/engineering/BACKEND_JAVA_STANDARD.md)，
> Vue 正文见 [docs/engineering/FRONTEND_VUE_TS_STANDARD.md](docs/engineering/FRONTEND_VUE_TS_STANDARD.md)，
> 安全见 [SECURITY_AND_AI_STANDARD.md](docs/engineering/SECURITY_AND_AI_STANDARD.md)，
> 测试见 [TESTING_AND_AUTOMATION_GATES.md](docs/engineering/TESTING_AND_AUTOMATION_GATES.md)。
> 审计基线与历史结论见 [docs/architecture/CODE_AUDIT_REPORT.md](docs/architecture/CODE_AUDIT_REPORT.md)。

### 11.1 开工前必须完成（缺一不可）

1. **读架构**：`AGENTS.md` → `docs/engineering/ENGINEERING_STANDARDS.md` → 任务相关分册。
2. **读真实代码**：定位 Controller → Service → Mapper → 表/索引完整链路，禁止按文件名猜。
3. **查重**：搜索是否已有同类 Service / Composable / `Kf` 组件 / 工具方法，**优先复用**。
4. **确认归属**：`git status` 中不属于你的改动视为他人 WIP，**不覆盖、不格式化、不删除**。
5. **先设计再写**：说明要新增/修改的类与职责；跨层改动先列影响范围。
6. **先建测试**：修改既有业务行为前，先补一个能复现当前行为的测试。
7. **确认边界**：纯业务/架构任务不改 UI 视觉；纯文档任务不改业务代码。

### 11.2 分层与 SQL 硬约束（新代码零容忍）

| 约束 | 规则 |
|---|---|
| Controller | **禁止** `import com.knowflow.*.mapper.*`；**禁止** `JdbcTemplate`；**禁止**事务编排与多步业务 |
| Service | 负责业务编排与事务；**禁止**拼接用户输入构造 SQL |
| SQL 位置 | 只允许出现在 Mapper 接口（注解）或明确的只读查询服务；**禁止**出现在 Controller |
| 动态 SQL | 必须参数绑定（`#{}` / `?` / `.apply("... {0}", arg)`）；**禁止**字符串拼接用户输入 |
| 归属校验 | userId 只来自 `SecurityUtils`；查询条件必须显式 `eq(owner)`，不能只靠 Controller |
| 事务 | 只在 public 方法；`@Transactional` 不与自调用混用 |
| 异常 | 需要 4xx 必须双参 `new BusinessException(404, "...")`；单参默认 code=500 → HTTP 200 假成功 |
| 物理外键 | 禁止新增；用逻辑外键 + `idx_<表>_<列>` |
| 唯一索引 × 逻辑删除 | 新增 `UNIQUE` 前必须确认不含 `deleted` 时软删行不会占死唯一键 |

### 11.3 命名与注释最低要求

- 类：`业务名 + 角色后缀`（Controller/Service/ServiceImpl/Mapper/DTO/VO/DO）。
- 禁止新增 `Manager` / `Helper` / `Common` / `Utils` 这类大而空的类名；确实需要时放进既有 `util` 包并写明职责。
- 方法用动词表达业务意图；禁止 `data` / `temp` / `obj` / `a` / `b` 作为业务变量名。
- 以下必须有 JavaDoc：公共 Service 方法、事务方法、有副作用/状态变更的方法、有特殊参数约束或异常契约的方法。
- 注释只解释**业务含义、约束、易误解点**；禁止复述方法名的空注释与过期 TODO。
- 字段注释必须写清单位与取值（如「完成百分比，0–100」「时间戳，UTC 毫秒」）。

### 11.4 收工前必须执行（未通过不得声称完成）

```bash
# 1. 架构与工程门禁（零依赖）
node scripts/engineering/check.mjs

# 2. 后端测试（涉及后端改动时）
cd backend && mvn -B test

# 3. 前端类型 + 测试 + 构建（涉及前端改动时）
cd frontend && npx vue-tsc -b --force
cd frontend && npx vitest run --maxWorkers=1
cd frontend && npm run build
```

必须逐项汇报：**实际执行了哪些命令、真实结果是什么、哪些没跑、为什么没跑**。
禁止用「应该没问题」「之前是绿的」替代真实执行。

### 11.5 架构门禁与存量基线

- 门禁脚本：`scripts/engineering/check.mjs`（计数棘轮 `baseline.json` + 文件级架构白名单 `architecture-baseline.json`）。
- **新增违规 = 门禁失败**：新代码不得命中 `architecture-baseline.json` 之外的任何架构规则。
- **只允许删除白名单条目**：修好一处即从白名单移除，视为净下降；**严禁**为提高数字而新增条目。
- 白名单条目失效（已修复但未移除）会产生 WARN，应顺手下调。
- 门禁红灯时，先判断归属：他人 WIP 引入的违规**不改别人的代码**，如实记录并交给作者；自己引入的必须修掉。

### 11.6 汇报口径

`DONE` / `VERIFIED` / `BLOCKED` / `SKIPPED` 必须严格区分：

- `DONE`：代码已改且适用门禁真实通过。
- `VERIFIED`：有可复现命令或真浏览器证据。
- `BLOCKED`：被外部依赖/他人 WIP/环境阻断。
- `SKIPPED`：本次未做。

**禁止**把 `NOT_VERIFIED` / `SKIPPED` / `BLOCKED` 写成 `DONE`。

---

## Java 后端 V2.0 正式规范（Batch 1）

STATUS: FROZEN
VERSION: 2.0
DATE: 2026-11-05

正式名称：KnowFlow Modular Layered Architecture V2.0。采用 Package by Feature + Traditional Layered Architecture（业务模块优先、模块内部传统分层）。执行日期 2026-11-06；冻结规范不等于业务迁移完成。

规范权威顺序：根 AGENTS.md > backend/AGENTS.md > docs/engineering/BACKEND_JAVA_STANDARD.md > 架构文档 > scripts/engineering > 测试代码。事实证据另按源码/真实行为 > 本轮实际测试 > 有效报告 > 历史/提案判定；现有违规不能反向覆盖规范。

业务模块固定为：`identity learning interview exam coding flashcards mastery knowledge ai agent voice reward store catalog techenglish community productivity workbench administration apps`。不得任意拆分或新建全局业务目录。技术根包 common/config/exception/framework/security/util/utils/websocket/seed 保留职责；framework/web 不属于业务模板违规。

模块内按需创建 controller、dto、entity、mapper、service、service/impl、vo、contract、enums、constant、exception。Controller、Mapper、Entity、DTO、VO、Service、ServiceImpl 分别进入对应角色目录；专用子包按真实职责保留，但角色类仍归对应层（如 learning/service/actions）。禁止模块根目录堆积角色类，禁止 api/web/application/domain/infrastructure 作为业务模板。公开契约数据在 contract；不机械移回内部 dto/vo。

Controller 只处理请求、参数校验、服务调用、统一响应及必要转换，不得依赖 Mapper/JDBC、写 SQL 或声明业务事务。Service 负责业务规则、归属校验、事务及编排，不依赖 Controller。Mapper 只做本模块持久化，不编排业务。Entity 必须明确唯一表与模块归属，不直接作前端响应；DTO 为传输，VO 为响应投影，不承载持久化。

跨模块依赖通过 Contract：A Service → B contract → B Service → B Mapper。普通 Service 不自动成为公开契约；内部 Service 接口可以保留，多实现/替换/稳定契约/真实依赖倒置才需要接口，实现进入 service/impl，禁止空壳接口及重复实现。跨模块不得直接依赖 Entity、Mapper、ServiceImpl、内部 DTO/VO；Contract 不暴露 Entity、Mapper、Repository 实现、Wrapper 或内部 Domain 类型。Common 不能变成业务中转站。

Catalog 不获得来源题表第二所有权；Store 不拥有 Reward 钱包；Learning 不拥有 Knowledge/Flashcards/Identity 实体；Administration 管理权限不转移数据所有权。单表权威 Entity/Mapper 不重复创建。Contract 说明身份、权限、异常、空值、事务、幂等及副作用。

执行 ARCH-001～012：Controller 禁 Mapper/JDBC/事务；Service 禁 Controller；Mapper 模块所有权；Entity 不跨模块；禁跨 ServiceImpl；跨模块仅 Contract；Common 不依赖业务；业务模块无循环；角色按传统分层；根目录禁角色堆积。严格门禁报告存量，不新增永久豁免；历史 610 类 manifest 保留原路径→历史路径，V2 目标另列，不改写历史。

本批次只改规范、检查脚本和测试，不移动生产 Java、不改前端/Schema、不提交推送、不进入 Batch 2。后续批次需逐类台账、WIP 归属、权限/事务/API 回归及真实门禁证据。执行 `node scripts/engineering/check.mjs` 和相关 Maven 架构测试；FAIL/PARTIAL/NOT_VERIFIED/SKIPPED 不得写成 PASS。
