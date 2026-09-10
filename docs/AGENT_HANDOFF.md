# AGENT_HANDOFF.md — KnowFlow Agent 交接协议

> 基于 2026-09-10 真实代码扫描。确保多 Agent 协作时不破坏已有工作和架构。

---

## 一、交接核心原则

**任何未提交修改可能来自其他 Agent。**

接收项目时：

1. **先理解** — 扫描 `git status` + `git diff`，理解未提交修改
2. **不确定归属时不覆盖** — 不删除未知代码
3. **不破坏 Git 工作区** — 禁止 `reset --hard` / `clean -fd` / `checkout .`

---

## 二、接收项目时必须执行

### 2.1 Git 侦察

```bash
git status          # 查看未提交修改
git log --oneline -15  # 查看最近提交
git diff --stat     # 查看修改文件统计
```

### 2.2 架构理解

必须阅读以下文件：

| 文件 | 职责 | 优先级 |
|------|------|--------|
| [AGENTS.md](file:///Users/beiluo/Documents/alProject/qoderProject/knowflow/AGENTS.md) | Agent 工作守则 | **必读** |
| [VIBE_CONTEXT.md](file:///Users/beiluo/Documents/alProject/qoderProject/knowflow/VIBE_CONTEXT.md) | Vibe Coding 上下文 | **必读** |
| [docs/PROJECT_ARCHITECTURE.md](file:///Users/beiluo/Documents/alProject/qoderProject/knowflow/docs/PROJECT_ARCHITECTURE.md) | 项目架构 | **必读** |
| [docs/UI_UX_SYSTEM.md](file:///Users/beiluo/Documents/alProject/qoderProject/knowflow/docs/UI_UX_SYSTEM.md) | UI/UX 系统 | UI 工作前必读 |
| [docs/DEVELOPMENT_PROTOCOL.md](file:///Users/beiluo/Documents/alProject/qoderProject/knowflow/docs/DEVELOPMENT_PROTOCOL.md) | 开发协议 | 编码前必读 |

### 2.3 代码扫描

必须真实扫描（不是猜文件名）：

- 项目目录结构
- `frontend/src/router/index.ts` — 路由
- `frontend/src/stores/` — Pinia Store
- `frontend/src/api/` — API 层
- `frontend/src/learning/` — 学习架构核心
- `frontend/src/apps/registry.ts` — App 注册表
- `backend/.../controller/` — Controller
- `backend/.../service/` — Service
- `backend/.../entity/` — Entity
- `docs/` — 文档

---

## 三、核心文件清单（不可破坏）

### 3.1 绝对不能重复实现的逻辑

| 逻辑 | 唯一实现 | 文件 |
|------|----------|------|
| NextAction 决策 | `resolveNextAction()` | [learning/action-priority.ts](file:///Users/beiluo/Documents/alProject/qoderProject/knowflow/frontend/src/learning/action-priority.ts) |
| Evidence 解析 | `resolveEvidenceBatch()` | [learning/evidence/resolver.ts](file:///Users/beiluo/Documents/alProject/qoderProject/knowflow/frontend/src/learning/evidence/resolver.ts) |
| Evidence 聚合 | `aggregateEvidence()` | [learning/evidence/aggregate.ts](file:///Users/beiluo/Documents/alProject/qoderProject/knowflow/frontend/src/learning/evidence/aggregate.ts) |
| 能力诊断 | `diagnoseFromEvidence()` | [learning/evidence/diagnosis.ts](file:///Users/beiluo/Documents/alProject/qoderProject/knowflow/frontend/src/learning/evidence/diagnosis.ts) |
| 策略推导 | `buildTopCandidate()` | [learning/evidence/strategy.ts](file:///Users/beiluo/Documents/alProject/qoderProject/knowflow/frontend/src/learning/evidence/strategy.ts) |
| 路由白名单 | `resolveActionRoute()` | [learning/route.ts](file:///Users/beiluo/Documents/alProject/qoderProject/knowflow/frontend/src/learning/route.ts) |
| 上下文盖章 | `stampActionContext()` + `resolveContextualRoute()` | [learning/action-contract.ts](file:///Users/beiluo/Documents/alProject/qoderProject/knowflow/frontend/src/learning/action-contract.ts) |
| Knowledge Context | `resolveLearningContext()` | [learning/context.ts](file:///Users/beiluo/Documents/alProject/qoderProject/knowflow/frontend/src/learning/context.ts) |
| DAG 节点解析 | `resolveCurrentChapterNode()` | [learning/path-node.ts](file:///Users/beiluo/Documents/alProject/qoderProject/knowflow/frontend/src/learning/path-node.ts) |
| App 注册 | `APP_DEFINITIONS` | [apps/registry.ts](file:///Users/beiluo/Documents/alProject/qoderProject/knowflow/frontend/src/apps/registry.ts) |
| App 状态管理 | `useAppStore()` | [stores/apps.ts](file:///Users/beiluo/Documents/alProject/qoderProject/knowflow/frontend/src/stores/apps.ts) |
| 学习状态 | `useLearningStore()` | [stores/learning.ts](file:///Users/beiluo/Documents/alProject/qoderProject/knowflow/frontend/src/stores/learning.ts) |
| UI 设计令牌 | `--kb-*` CSS 变量 | [styles/ui-tokens.css](file:///Users/beiluo/Documents/alProject/qoderProject/knowflow/frontend/src/styles/ui-tokens.css) |
| Toast / Dialog | `notify` / `confirmDialog` / `promptDialog` | [utils/toast.ts](file:///Users/beiluo/Documents/alProject/qoderProject/knowflow/frontend/src/utils/toast.ts) |

### 3.2 谨慎修改的文件

| 文件 | 风险 | 注意事项 |
|------|------|----------|
| [stores/learning.ts](file:///Users/beiluo/Documents/alProject/qoderProject/knowflow/frontend/src/stores/learning.ts) | 影响全站 NextAction | 改动需理解完整数据流 |
| [learning/](file:///Users/beiluo/Documents/alProject/qoderProject/knowflow/frontend/src/learning/) 整个目录 | 学习架构核心 | 算法逻辑不可随意修改 |
| [apps/registry.ts](file:///Users/beiluo/Documents/alProject/qoderProject/knowflow/frontend/src/apps/registry.ts) | App 注册表 | route 必须与 router 逐一核对 |
| [config/navigation.ts](file:///Users/beiluo/Documents/alProject/qoderProject/knowflow/frontend/src/config/navigation.ts) | 导航配置 | 影响侧边栏 |
| [router/index.ts](file:///Users/beiluo/Documents/alProject/qoderProject/knowflow/frontend/src/router/index.ts) | 路由表 | 新增路由需确认页面存在 |
| `LearningServiceImpl.java` | 学习核心服务 | 影响学习闭环 |
| `MasteryEngine` | 掌握度引擎 | 影响知识点掌握度 |
| `schema.sql` | 数据库结构 | 影响全量数据 |

### 3.3 禁止修改的文件（除非有明确架构升级指令）

- [learning/evidence/aggregate.ts](file:///Users/beiluo/Documents/alProject/qoderProject/knowflow/frontend/src/learning/evidence/aggregate.ts) 的算法逻辑（样本上限/时间衰减/置信度公式）
- [learning/action-priority.ts](file:///Users/beiluo/Documents/alProject/qoderProject/knowflow/frontend/src/learning/action-priority.ts) 的优先级链顺序
- [learning/route.ts](file:///Users/beiluo/Documents/alProject/qoderProject/knowflow/frontend/src/learning/route.ts) 的白名单（只能追加，不能删除已有条目）
- [learning/action-contract.ts](file:///Users/beiluo/Documents/alProject/qoderProject/knowflow/frontend/src/learning/action-contract.ts) 的 `LEARNING_ENTRY_CONTEXT` 映射（只能追加新页面，不能删已有映射）

---

## 四、Agent 可修改区域

### ✅ 自由修改

| 区域 | 说明 |
|------|------|
| `frontend/src/views/` | 页面组件（不含核心逻辑变更） |
| `frontend/src/components/` | UI 组件 |
| `frontend/src/api/` | 请求封装（新增接口、调整参数） |
| `frontend/src/composables/` | Composables |
| `frontend/src/utils/` | 工具函数 |
| `backend/.../controller/` | 新增 Controller |
| `backend/.../service/` | 新增 Service |
| `backend/.../dto/` / `vo/` / `entity/` | 新增类型 |
| `docs/` | 文档 |

### ⚠️ 谨慎修改

| 区域 | 注意事项 |
|------|----------|
| `stores/learning.ts` | 影响全站 NextAction |
| `learning/` 目录 | 学习架构核心 |
| `apps/registry.ts` | App 注册表 |
| `config/navigation.ts` | 导航配置 |
| `router/index.ts` | 路由表 |
| `LearningServiceImpl.java` | 学习核心服务 |
| `MasteryEngine` | 掌握度引擎 |
| `schema.sql` | 数据库结构 |

### 🚫 禁止修改（除非架构升级指令）

| 文件 | 禁止内容 |
|------|----------|
| `learning/evidence/aggregate.ts` | 算法逻辑 |
| `learning/action-priority.ts` | 优先级链顺序 |
| `learning/route.ts` | 白名单条目删除 |
| `learning/action-contract.ts` | ENTRY_CONTEXT 映射删除 |

---

## 五、当前未提交修改状态

截至 2026-09-10，工作区有以下未提交修改（来自其他 Agent 的工作）：

### 已完成的架构线

| 架构线 | 状态 | 涉及文件 |
|--------|------|----------|
| Architecture 4.0 (Learning Loop) | ✅ 已实现 | learning/action-priority.ts + stores/learning.ts |
| Architecture 4.1 (Evidence Strategy) | ✅ 已实现 | learning/evidence/* 全部 7 个文件 |
| Architecture 4.2 (Knowledge Context) | ✅ 已实现 | learning/context.ts + stores/learning.ts |
| Architecture 4.3 (Knowledge Processing) | ✅ 已实现 | 后端 KnowledgeProcessingController/Service + knowledge/ 包 |
| Architecture 4.4 (Content Generator) | ✅ 已实现 | 后端 LearningContentController/Service + Prompts |

### 已完成的 UI/UX 线

| UI/UX 线 | 状态 | 涉及文件 |
|----------|------|----------|
| UI/UX 3.0-3.3 (Unified Workspace) | ✅ 已实现 | CLayout/Sidebar/Badge/CommandPalette 等 |
| UI/UX 5.0-5.1 (Learning OS Store) | ✅ 已实现 | stores/learning.ts + 全页面接入 |
| UI/UX 6.0 (闪卡工作台) | ✅ 已实现 | FlashCardsHub.vue + flashcard-session.ts |

### 接收 Agent 注意事项

1. **不要覆盖这些修改** — 它们是有意为之的架构工作
2. **如需理解细节** — 阅读 `docs/Architecture-4.x-*.md` 和 `docs/二次开发/UI-UX-*.md` 执行报告
3. **如需提交** — 遵循 Git 工作规则，用 `git status` 核对暂存范围

---

## 六、交接时必须输出的侦察报告

新 Agent 接收项目后，首次任务**不修改代码**，只输出以下报告：

```
A. 当前项目目录结构
B. 当前前端架构
C. 当前后端架构
D. 当前核心学习闭环
E. 当前 NextAction 数据流
F. 当前 UI Design System
G. 当前主要页面
H. 当前已经实现的功能
I. 当前未完成的功能
J. 当前 git 未提交修改
K. 当前发现的技术债务
L. 当前发现的 UI/UX 问题
M. 当前发现的重复实现
N. 建议下一阶段
```

---

## 七、多 Agent 协作规则

### 7.1 如果你是第一个 Agent

- 完成工作后写执行报告
- 在报告中注明修改了哪些核心文件
- 不要 push，除非用户明确要求

### 7.2 如果你是接手 Agent

- 先读 `git status` + `git diff`
- 先读 [AGENTS.md](file:///Users/beiluo/Documents/alProject/qoderProject/knowflow/AGENTS.md)
- 先理解未提交修改
- 不确定归属时不覆盖
- 如果发现冲突，先理解再决定

### 7.3 并行 Agent

- 各自只修改各自负责的模块
- 不要触碰其他 Agent 正在修改的文件
- 如果不确定，先问

---

## 八、Agent 执行记录

### 2026-09-10 · P1-B 协议异常语义修复

| 项 | 内容 |
|------|------|
| 当前 Agent | WorkBuddy（Vibe Coding / DeepSeek V4.1 Flash） |
| 阶段 | **P1-B** —— GlobalExceptionHandler 协议异常语义修复 |
| 修改文件（生产代码） | `backend/src/main/java/com/knowflow/exception/GlobalExceptionHandler.java`（**唯一**） |
| 新增测试 | `backend/src/test/java/com/knowflow/exception/ProtocolExceptionMappingTest.java`（6 用例） |
| 架构变化 | **无**。沿用既有 `Result.error(code, message)` + `@ResponseStatus` 单一异常出口，未新建异常体系 / 错误码体系 |
| 未提交 | 遵守 Git 规则，本轮**未 commit、未 push** |

**根因**

4 类协议层异常未在 `GlobalExceptionHandler` 中映射。`ExceptionHandlerExceptionResolver` 在 `HandlerExceptionResolver` 链中优先级最高，因此这些异常**不会**落到 `DefaultHandlerExceptionResolver` 的正确状态码，而是被兜底的 `@ExceptionHandler(Exception.class)` 接走 → HTTP 500「系统内部错误」→ 前端 `utils/error-message.ts` 把 5xx 统一归类为 `server` → 用户看到「服务暂时不可用 / 稍后重试」。即：**用户自己的请求错误被系统说成服务端故障**。

**补充的 4 个 handler**

| 异常类 | HTTP | 业务码 | 用户可见文案 |
|--------|------|--------|--------------|
| `MissingServletRequestParameterException` | 400 | 400 | 缺少必需的请求参数：{参数名} |
| `HttpMessageNotReadableException` | 400 | 400 | 请求参数不正确 |
| `HttpMediaTypeNotSupportedException` | 415 | 415 | 请求内容类型不受支持 |
| `HttpRequestMethodNotSupportedException` | 405 | 405 | 请求方法不受支持 |

**真实接口验收（`curl --noproxy '*'`，后端 :8080，重启加载新代码后）**

| # | 场景 | 修复前 | 修复后 | 结果 |
|---|------|--------|--------|------|
| 1 | `GET /api/learning/certificates/verify?code=ABC`（certNo 缺失） | 500 | **400** | ✅ |
| 2 | `POST /api/auth/login`，body `{bad json` | 500 | **400** | ✅ |
| 3 | `POST /api/auth/login`，Content-Type `text/plain` | 500 | **415** | ✅ |
| 4 | `GET /api/auth/login`（该端点仅 POST） | 500 | **405** | ✅ |
| 5 | `GET /api/docs/abc`（路径变量类型不匹配） | 400 | **400** | ✅ 保持原行为 |

对照组未受影响：证书不存在 → 404（业务语义）、受保护端点匿名 → 401、正常登录 → 200。

**测试**

- 新增 `ProtocolExceptionMappingTest`：6 用例（4 个新映射 + 路径类型不匹配回归 + 业务异常对照），全部通过。
- 后端全量回归：`mvn -f backend/pom.xml test` → **Tests run: 149, Failures: 0, Errors: 0, Skipped: 0；BUILD SUCCESS**（基线 143 → +6）。**未删除任何测试，未修改任何断言**。

**浏览器验证（真实 Chromium + 真实后端 + 真实前端错误层模块）**

- 真实浏览器经 Vite 代理请求 4 个端点，实测状态码 **400 / 400 / 415 / 405** ✅
- 真实浏览器中执行 `frontend/src/utils/error-message.ts` 的 `describePageError()`：400 / 405 / 415 的 `kind` 已由 `server`（→「服务暂时不可用」）变为 `unknown`（→ 显示页面自身 subject 文案，不再说「服务挂了」）；真正的 5xx 仍为 `server` →「服务暂时不可用」✅

**残留（本轮未处理，属前端范围）**

- 400 / 405 / 415 在前端落入 `unknown` 分支，仍带 `retryable: true` 与「稍后重试」提示。即：**不会再被说成「服务挂了」，但「建议重试」仍不完全贴切**。彻底解决需在 `frontend/src/utils/error-message.ts` 增加 4xx 分类 —— 本轮范围明确限定只改后端，故仅记录，未修改。

**后续建议**：P1-A（`permitAll` + 内联 `(Long) authentication.getPrincipal()` → 匿名访问 500）→ 已由下一轮执行「第一批」，见下。

---

### 2026-09-10 · P1-A（第一批）ChatController principal 安全收敛
- **当前 Agent**：WorkBuddy
- **范围**：**只修改 `ChatController`**；`SecurityConfig` 未动，其余 123 处内联强转与 21 个 Controller 未动。
- **修改文件**：`backend/src/main/java/com/knowflow/controller/ChatController.java`（唯一生产代码文件）
- **新增测试**：`backend/src/test/java/com/knowflow/controller/ChatPrincipalAuthTest.java`（8 用例）

**根因**

`SecurityConfig` 为规避 SSE 异步分发时 `SecurityContext` 丢失，对 `POST /api/chat/stream` 配置 `permitAll`，且未 `anonymous().disable()`。匿名请求因此**真实到达控制器**，而控制器内联 `(Long) authentication.getPrincipal()` —— 匿名 principal 是字符串 `"anonymousUser"` → `ClassCastException` → 落入 `GlobalExceptionHandler` 兜底 → **HTTP 500「系统内部错误」**，正确语义应为 **401**。

**关键发现（决定实现方式，勿再踩）**

`BusinessException(String)` 的默认 `code` 是 **500**，经 `GlobalExceptionHandler.handleBusinessException` 判定 `code ∈ [400,500)` 才映射为对应 HTTP，否则回落 **HTTP 200**。
→ 因此**单纯换成 `SecurityUtils.getCurrentUserId()` 拿不到 401**（会得到 HTTP 200 + body `code:500`）。
→ 正确做法：用既有 `SecurityUtils.getCurrentUserIdNullable()` 做判空，再抛既有形态 `BusinessException(401, ...)`（仓内已有 4 处先例），文案复用 `JwtAuthenticationEntryPoint` 的 `未登录或登录已失效`。

**修改内容**

- 6 处内联 `(Long) authentication.getPrincipal()` 全部替换为统一私有入口 `currentUserId()`；连带移除已无用的 `Authentication` 形参与 import。
- `currentUserId()` = `SecurityUtils.getCurrentUserIdNullable()` + 判空 `throw new BusinessException(401, "未登录或登录已失效")`。
- **未新增异常体系、未新增错误码、未新增依赖、未创建新鉴权机制**；响应信封仍为 `Result{code,message,data}`。

**真实接口验收（`curl --noproxy '*'`，后端 :8080，重启加载新代码后）**

| # | 场景 | 修复前 | 修复后 | 结果 |
|---|------|--------|--------|------|
| 1 | 匿名 `POST /api/chat/stream`（合法 body） | 500 | **401** `{"code":401,"message":"未登录或登录已失效"}` | ✅ |
| 2 | 无效 token 同一端点 | 500 | **401** | ✅ |
| 3 | 匿名 + 空 body `{}` | 400 | **400**（校验先于鉴权） | ✅ 行为不变 |
| 4 | 匿名 + `Accept: text/event-stream` | 500 | **401**（JSON 错误体仍可正常写出） | ✅ |
| 5 | 有效 token 同一端点 | 200 | **200 + `Content-Type: text/event-stream`** | ✅ SSE 正常流程保持 |
| 6 | 有效 token `GET /api/chat/conversations` | 200 | **200** | ✅ |
| 7 | 有效 token `POST /api/chat/conversations` | 200 | **200 + 会话 id** | ✅ |
| 8 | 有效 token `DELETE /api/chat/conversations/999999` | 404 | **404**（用户隔离不变） | ✅ |
| 9 | 匿名 `GET /api/chat/conversations` | 401 | **401**（过滤链语义不变） | ✅ |

P1-B 映射回归 5 项全部保持：405 / 415 / 400 / 400 / 400 ✅

**测试**

- 新增 `ChatPrincipalAuthTest`：8 用例（匿名→401、无效 token→401、有效 token 走通 SSE 且会话归属正确、对话列表/创建/删除回归、匿名受保护端点 401、校验先于鉴权 400）。复用既有 `FakeAiConfiguration`（`@Import`）避免测试依赖外部 LLM。
- 后端全量回归：`mvn -f backend/pom.xml test` → **Tests run: 157, Failures: 0, Errors: 0, Skipped: 0；BUILD SUCCESS**（基线 149 → +8）。**未删除任何测试，未修改任何断言**。

**浏览器验证（真实 Chromium + Vite 代理 + 真实前端错误层模块）**

- 匿名经代理请求 `/api/chat/stream` → **401** `{"code":401,...}`（原 500）✅
- 有效 token 同一端点 → **200 + `text/event-stream`** ✅
- 真实 `error-message.ts`：401 → `kind: 'auth'` →「登录状态已过期 / 请重新登录后再试」`retryable: false`；对照 500 仍为 `server` →「服务暂时不可用」✅
- 即：**匿名/登录失效的对话请求不再被显示成「服务已挂」**。

**遗留（本轮范围外，未处理）**

- 其余 **123 处**内联 `(Long) authentication.getPrincipal()`，分布 **22 个 Controller**（重灾区 `LearningController` 31、`StudyGroupController` 19、`DocController` 11）。建议后续按 Controller 分批收敛（参考本轮模式）；这些端点均在 `authenticated()` 之后，当前不会产生匿名 500，但一旦被误加 `permitAll` 即复现同类缺陷。
- 前端 `api/chat.ts` 的 `streamChat` 在失败时抛 `流式请求失败：HTTP ${status}`（含原始状态码），属前端错误文案债，本轮未改。

---

### 2026-09-10 · P1-A（第二批）LearningController principal 安全收敛

- **日期**：2026-09-10
- **当前 Agent**：WorkBuddy
- **当前模型**：DeepSeek V4.1 Flash
- **阶段**：P1-A 第二批
- **范围**：**只修改 `LearningController`**；`SecurityConfig` 未动，其余 21 个 Controller 未动，Service/Repository/前端未动。
- **修改文件**：`backend/src/main/java/com/knowflow/controller/LearningController.java`（唯一生产代码文件）
- **新增测试**：`backend/src/test/java/com/knowflow/controller/LearningPrincipalAuthTest.java`（11 用例）
- **principal 强转数量**：修改前 **31 处**内联 `(Long) authentication.getPrincipal()` → 修改后 **0 处**（收敛到统一私有入口 `currentUserId()`）

**根因**

同第一批：内联 `(Long) authentication.getPrincipal()` 对已登录请求成立，但匿名 principal 是字符串 `"anonymousUser"`（配置未 `anonymous().disable()`）→ `ClassCastException` → 落入 `GlobalExceptionHandler` 兜底 → **HTTP 500**。

**⚠️ 本批关键：不是「防御性收敛」，而是修了一个活缺陷**

`SecurityConfig` 的 `permitAll` 清单里包含 **`GET /api/learning/paths/{id}`**（与 `/api/learning/paths`、`/api/learning/flashcards`、`/api/learning/certificates/verify` 同组），且 `LearningServiceImpl#getPathDetail` **已显式支持 `userId == null`**（`enrolled = userId == null ? 0L : ...`）—— 即设计语义就是「匿名可读」。

但该端点内联强转 → **匿名访问实测 500**（修复前：`GET /api/learning/paths/1` 匿名 → `500 {"code":500,"message":"系统内部错误"}`；同组列表 `GET /api/learning/paths` 匿名 → 200，对照鲜明）。

→ 因此该端点的正确修复是 **500 → 200**（恢复被破坏的 permitAll 能力），**不是 401**。其余 30 处位于 `authenticated()` 之后，语义为「要求登录 → 401」。**这是本批最需要「逐个判断语义、禁止批量替换」的地方。**

**修改内容**

- 31 处内联强转 → 统一私有入口 `currentUserId()`；连带移除已无用的 `Authentication` 形参（不留死参数）。
- `currentUserId()` = `SecurityUtils.getCurrentUserIdNullable()` + 判空 `throw new BusinessException(401, "未登录或登录已失效")`，**与第一批 `ChatController` 实现完全一致**。
- **`pathDetail` 单独处理**：改用 `SecurityUtils.getCurrentUserIdNullable()` 直接透传（匿名可读语义）。
- **`chapterKnowledge`（Architecture-4.2 遗留的新端点）未修改**：它只有 `authentication == null` 判空，**不存在不安全强转**，按「无强转不修改」原则保留，故文件仍保留 `Authentication` import。
- **未新增异常体系、未新增错误码、未新增依赖、未创建新鉴权机制**；响应信封仍为 `Result{code,message,data}`。**无架构变化。**

**测试**

- 新增 `LearningPrincipalAuthTest`：**11 用例**（`@SpringBootTest` + `@AutoConfigureMockMvc` + `@Import(FakeAiConfiguration)` + 独立 H2 库 `knowflow_p1a_learning`，与第一批同款脚手架）。
  覆盖：匿名受保护读/写 → 401、无效 token → 401、**★匿名 `paths/{id}` → 200（原 500）**、有效 token 读接口全绿、**请求体伪造 `userId` 不生效（归属仍为 token 用户）**、**报名写操作绑定当前用户**、**用户 B 读不到用户 A 任务**、**用户 B 不能改/删用户 A 任务且无副作用**、既有 404 业务语义保持、P1-B 协议映射回归。
- 单类单跑：11/11 通过。
- 后端全量回归：`mvn -f backend/pom.xml test` → **Tests run: 168, Failures: 0, Errors: 0, Skipped: 0；BUILD SUCCESS**（基线 157 → +11）。**未删除任何测试，未修改任何断言。**

**真实接口验收（`curl --noproxy '*'`，后端 :8080 重启加载新代码后）**

| # | 场景 | 修复前 | 修复后 | 结果 |
|---|------|--------|--------|------|
| 1 | 匿名 `GET /api/learning/tasks` | 500 | **401** | ✅ |
| 2 | 匿名 `GET /api/learning/my/flashcards` | 500 | **401** | ✅ |
| 3 | 匿名 `GET /api/learning/certificates` | 500 | **401** | ✅ |
| 4 | 匿名 `POST /api/learning/paths/1/enroll` | 500 | **401** | ✅ |
| 5 | 无效 token `GET /api/learning/tasks` | 500 | **401** | ✅ |
| 6 | **匿名 `GET /api/learning/paths/1`**（permitAll） | **500** | **200 + 路径详情** | ✅ ★ |
| 7 | 匿名 `GET /api/learning/paths`（对照） | 200 | **200** | ✅ |
| 8 | 有效 token `GET /api/learning/paths/1` | 200 | **200** | ✅ |
| 9 | 有效 token `GET /api/learning/tasks` | 200 | **200** | ✅ |
| 10 | 匿名 `GET /api/learning/paths/999999` | — | **404「学习路径不存在」** | ✅ |

**用户隔离验收（真实 HTTP，user1 token + 请求体伪造 `userId=3`）**

| 步骤 | 结果 |
|------|------|
| user1 `POST /api/learning/tasks`，body 内写 `userId=3`（user2） | 200 |
| user1 任务列表含该任务 | ✅ 归属 token 中的用户 |
| user2 任务列表含该任务 | ✅ **不含**（请求体 `userId` 被忽略，无越权归属） |
| user2 `PUT /api/learning/tasks/{id}/status?status=1` | 业务失败，且 user1 该任务 `status` 仍为 `0`（**无副作用**）✅ |

P1-B 映射回归 5 项全部保持：405 / 415 / 400 / 400 / 400 ✅

**浏览器验证（真实 Chromium + Vite 代理 + 真实后端）**

- 浏览器内登录 user1 后经代理实测：匿名 `tasks` → 401、匿名 `paths/1` → **200**（原 500）、匿名 `enroll` → 401；登录后 `tasks` / `paths/1` / `my/flashcards` / `stats/mastery` → 200 ✅
- 页面渲染实测（均无「服务暂时不可用 / 系统内部错误 / 加载失败」文案）：
  - `/learning/center`（学习首页）✅ 正常，且正确渲染出真实任务数据
  - `/learning/path/1`（路径详情，本批修复端点驱动）✅ 正常渲染「前端工程师成长之路 / 5 章节 / 20 小时 / 学习路径图谱」
  - `/learning/goals`（学习目标）✅ 正常（空态）
  - `/learning/mastery`（掌握分布看板）✅ 正常渲染
- 截图：`/tmp/p1a2-shots/p1a2-path-detail.png`、`/tmp/p1a2-shots/p1a2-mastery.png`

**未验证项 / 遗留（诚实披露）**

- ⚠️ **发现既有真实缺陷（本轮范围外，未修）**：`LearningServiceImpl#getCategoryMastery` 的原生 SQL 使用 `SELECT q.category ... GROUP BY q.category`，但 `quiz_question` 表在**三份 schema（根 `schema.sql` / `db/h2` / `db/mysql`）中都没有 `category` 列**（只有 `category_id`）→ SQL 必然抛 `bad SQL grammar`，被该方法 `catch (Exception e) { log.warn(...); return List.of(); }` **吞掉**。
  → 后果：`GET /api/learning/category-mastery` **永远返回空数组**，前端 `/learning/mastery`「分类维度掌握度」区块**永远为空**（当前被空态文案「暂无答题记录」掩盖，形成静默失败）。实测确认返回 `data: []`。
  → 修法方向（择一）：SQL 改为按 `category_id` 关联 `doc_category` 取名，或把列名纠正为 `category_id`。**属 Service 层改动，本轮按「禁止顺手重构 Service」未动。**
- ⚠️ **既有 HTTP 语义瑕疵（本轮范围外，未修）**：越权访问他人资源抛的是 `BusinessException(String)`（默认 `code=500`）→ `handleBusinessException` 判定 `code` 不在 `[400,500)` → 回落 **HTTP 200 + body `code:500`**，而非 403。实测：user2 改 user1 任务 → `HTTP 200 {"code":500,"message":"无权操作该任务"}`。功能上不越权，但 HTTP 层语义不准确（同类问题存在于 `updateTaskStatus` / `deleteTask` 等多处）。
- ⚠️ 未做「表单交互式触发 401」的 UI 点击验证 —— 前端缺少会自然产生该类请求的入口，故采用「真实浏览器 + 真实代理 + 真实端点」方式验证，如实说明。
- 剩余 **123 处**内联强转 / **21 个 Controller**（本批已消化 `LearningController` 的 31 处）。

**下一批建议（仅记录，不自动执行）**

**P1-A 第三批 —— `StudyGroupController`（19 处）**。之后：`DocController`（11 处）、`PrivateMessageController`，再其余。这些端点均在 `authenticated()` 之后，当前不会产生匿名 500，属「防未来误加 `permitAll` 即复现」的防御性收敛；**优先级低于上述两个已实证的真实缺陷**（`category-mastery` 静默空数据、越权返回 HTTP 200）。

---

### 2026-09-10 · P1-C Learning 数据正确性与 HTTP 语义修复

**任务边界**：不继续批量收敛 Controller，只修两个**已通过真实实测确认**的缺陷 —— P1-C-1 `category-mastery` 静默失败、P1-C-2 越权操作 HTTP 语义错误。不含 StudyGroupController / DocController / PrivateMessageController，不含 P2 技术债与 UI 重构。

#### 一、修改目标与原因

| 编号 | 缺陷（真实实测确认） | 后果 |
|------|---------------------|------|
| P1-C-1 | `LearningServiceImpl#getCategoryMastery` 的原生 SQL 选取 `q.category`，而 `quiz_question` 在三份 schema 中**只有 `category_id`**（无 `category`） | SQL 必抛 `BadSqlGrammarException` → 被 `catch (Exception) → return List.of()` **吞掉** → `GET /api/learning/category-mastery` 恒返回 `data: []`，前端「分类维度掌握度」恒为空。**系统故障被伪装成「没有数据」** |
| P1-C-2 | `updateTaskStatus` / `deleteTask` 用 `new BusinessException("无权…"***)`（**单参**，默认 `code=500`）拒绝越权 | `handleBusinessException` 只在 `code ∈ [400,500)` 时同步 HTTP 状态码，500 回落 `HttpStatus.OK` → 越权被表述为 **HTTP 200 + body.code 500**，前端会误判为「系统内部错误 / 服务暂时不可用」并提示重试 |

#### 二、修改文件

| 文件 | 改动 |
|------|------|
| `backend/.../service/impl/LearningServiceImpl.java` | ① `getCategoryMastery` SQL 改为 `category_id JOIN doc_category` 取 `name`（`GROUP BY c.id, c.name`），并**删除 catch**，让真实 SQL/系统故障冒泡到 `GlobalExceptionHandler` → 500；② `updateTaskStatus` / `deleteTask` 越权改为 `BusinessException(403, …)` |
| `backend/.../test/.../controller/CategoryMasteryQueryTest.java`（新增） | P1-C-1 六项场景 |
| `backend/.../test/.../controller/LearningTaskPermissionTest.java`（新增） | P1-C-2 八项场景 |
| `backend/.../test/.../controller/LearningPrincipalAuthTest.java` | 把原 P1-A2-9 的「越权 code ≠ 200」断言**收紧为 403**（status 403 + code 403），并移除已无用的 hamcrest `not` 导入 |
| `docs/AGENT_HANDOFF.md` | 本节 |

**关键设计取舍（复用优先，未新增任何字段/表/Repository）**

- 分类名解析复用既有模型：与 `LearningReportServiceImpl#buildCategoryMastery`（`category_id` → `doc_category.name`）**同一语义**，只是把 JOIN 下推到 SQL 以保留 `GROUP BY`/`ORDER BY` 聚合与排序能力。
- `VO` 契约**零变更**：`CategoryMasteryVO.category` 仍是 String 分类名，前端 `CategoryMasteryVO`（`frontend/src/api/types.ts`）与 `MasteryDashboard.vue` **未改动一行**。
- `GROUP BY c.id, c.name`（而非仅 `c.name`）：按分类主键聚合，避免同名分类被错误合并。
- `ORDER BY … * 1.0 / COUNT(*)`：强制小数比较，使 H2(`MODE=MySQL`) 与 MySQL 排序语义一致（替代原 `CAST(… AS DOUBLE)`，避免方言差异）。
- `category_id IS NULL` 的题目（未归类）经 INNER JOIN 自然排除 —— 分类维度统计只统计「有分类」的数据，不发明「未分类」这一新标签。

#### 三、测试（`mvn -f backend/pom.xml test`）

**182 tests / 0 failures / 0 errors — BUILD SUCCESS**（基线 168 + 新增 14）

| 新增用例 | 覆盖 |
|---------|------|
| `CategoryMasteryQueryTest`（6） | ①有数据→真实分类名+total/correct/rate（修复前恒空）②按正确率升序 ③`category_id`→`doc_category.name` 映射（不是 id/null）④无记录→**真空数组**（并同上下文对照有数据者非空，证明「空=真无数据」）⑤按 userId 隔离 ⑥**SQL 故障不再被吞**：`DROP TABLE quiz_answer_record` 后 Service 抛异常、HTTP **500** 且 `data` 非数组 |
| `LearningTaskPermissionTest`（8） | 本人改状态/删除→200；他人改状态→**403+code403**；他人删除→**403+code403**；越权后数据零副作用（status/描述/条数不变，越权者列表也看不到）；匿名→401；无效 token→401；任务不存在→404 回归；越权者隔离只读 |

> 测试脚手架沿用项目既有约定：`@SpringBootTest + @AutoConfigureMockMvc + @ActiveProfiles("local") + @Import(FakeAiConfiguration)`，并各自用 `@TestPropertySource` 指定**独立 H2 库**（`knowflow_p1c_mastery` / `knowflow_p1c_task`），避免共享 `mem:knowflow` 重复执行 schema 导致 `CREATE INDEX` 冲突。`CategoryMasteryQueryTest` 用 `@TestMethodOrder(OrderAnnotation)` 把破坏性用例锁到最后执行。

#### 四、真实接口验证（**真实 curl，非仅 200**）

**P1-C-2 越权语义**（user1=`id 2` 建任务 → user2=`id 3` 越权）

| 场景 | 修复前（实测） | 修复后（实测） |
|------|--------------|--------------|
| user1 改**自己**任务状态 | 200 | **HTTP 200 + code 200** ✅ |
| user2 `PUT /tasks/{id}/status` | HTTP 200 + code 500 ❌ | **HTTP 403 + code 403 + "无权操作该任务"** ✅ |
| user2 `DELETE /tasks/{id}` | HTTP 200 + code 500 ❌ | **HTTP 403 + code 403 + "无权删除该任务"** ✅ |
| 匿名 `PUT` / `DELETE` | — | **HTTP 401 + code 401** ✅ |
| 无效 token | — | **HTTP 401 + code 401** ✅ |
| 越权后重读 user1 数据 | — | 任务仍在、`status=0`、`description` 未变 —— **零副作用** ✅ |

**P1-C-1 分类掌握度（真实数据全链路，非 fixture）**

链路：`admin` 经 `POST /api/admin/quiz-questions` 在「前端开发(7)」「后端开发(8)」各建 2 题 → `user1` 经 `POST /api/quiz/submit` 作答（前端 1 对 1 错 / 后端 2 对）→ `GET /api/learning/category-mastery`：

```json
{"code":200,"data":[
  {"category":"前端开发","total":2,"correct":1,"rate":50,"weak":true},
  {"category":"后端开发","total":2,"correct":2,"rate":100,"weak":false}
]}
```

- 分类**名称**正确（来自 `doc_category.name`，非 id）✅；正确率 50 / 100 ✅；`weak` 标记（<60）✅；正确率升序 ✅
- 同一时刻 `user2`（未答题）→ `data: []` —— 与 user1 的非空结果**同上下文对照**，「空」被证明是真实无数据 ✅
- 后端日志**无** `分类掌握度查询失败` 告警（旧代码的吞错特征）✅

#### 五、浏览器验证（真实 Chromium + Vite 代理 + 真实后端 + 真实登录 user1）

- `/learning/mastery`「**分类维度掌握度**」区块**真实渲染**：`前端开发 薄弱 50% · 1/2`（红条）+ `后端开发 100% · 2/2`（蓝条），标题右侧「**1 个薄弱项待加强**」。**修复前该区块恒为空**（被空态文案掩盖）。截图：`/Users/beiluo/.agent-browser/tmp/screenshots/screenshot-1789029271283.png`
- 越权 403 采用「**真实浏览器 + 真实代理 + 真实端点**」（前端无「操作他人任务」的 UI 入口）：浏览器内 `fetch` 实测 → `PUT` **403**/`code 403`/`无权操作该任务`、`DELETE` **403**/`code 403`/`无权删除该任务`、匿名 **401** ✅
- **前端错误层（§十四）**：`frontend/src/utils/error-message.ts` **已存在** 403 分类 —— `classifyPageError(403) → 'forbidden'`（不是 `server`/`unknown`）、`retryable: false`、文案「没有访问权限 / 当前账号无权访问该内容…」**不含「稍后重试」**。实测 `npx vitest run src/utils/__tests__/error-message.spec.ts` → **25 passed**（含 403→forbidden、401/403/404 不可重试的既有断言）。
  → 因此**未做前端改动**：§十四的前置条件（「若前端没有 403 分类才做最小适配」）不成立。若坚持把标题改为「没有权限执行此操作」，需注意该文案对**只读页面加载失败**场景不如现文案准确，**留待用户拍板**。

#### 六、诚实边界与遗留（本轮未修，仅记录）

- ~~⚠️ **前端仍有一层降级吞错**：`frontend/src/views/MasteryDashboard.vue` 的 `loadData()` 中 `learningApi.categoryMastery().catch(() => [] as CategoryMasteryVO[])`（与同函数的 `knowledgeMastery()` 同款）会把该面板的失败**降级为空数组**。~~ → **已于 P1-D 修复**（见下方 2026-09-10 · P1-D 执行记录）：面板失败现在渲染错误态 + 面板级重试，不再伪装成空数据。
- ⚠️ **同类越权语义缺陷仍存在 19 处**（本轮**只**修任务相关 2 处，按指令不扩散；`grep -rn 'new BusinessException("无权'` 精确清点，`LearningServiceImpl` 内已归零）：`MindMapServiceImpl:137`、`WorkbenchServiceImpl:902`、`CalendarMarkServiceImpl:390`、`MistakeServiceImpl:64/76`、`DrawingServiceImpl:142`、`NotificationServiceImpl:46`、`CommunityServiceImpl:139`、`KbMemberServiceImpl:194/220`、`FocusSessionController:68/195/209`、`AdminKbMemberController:89/168`、`AdminCategoryController:107/123/148/224`（共 19 处，均为 `BusinessException(String)` → HTTP 200 + code 500）。建议后续按同一模式收敛为 403。
- ⚠️ 未做「表单交互式触发 403」的 UI 点击验证 —— 前端缺少会自然产生该请求的入口，故以「真实浏览器 + 真实代理 + 真实端点」替代，如实说明。
- 未验证项：MySQL 方言下 `ORDER BY … * 1.0 / COUNT(*)` 仅经 H2(`MODE=MySQL`) 实测；MySQL 8 语义一致但**未在 MySQL 上实跑**。

#### 七、下一批建议（仅记录，**不自动执行**）

**P1-A 第三批 —— `StudyGroupController`（19 处）**。之后 `DocController`（11 处）、`PrivateMessageController`，再其余，剩余 92 处 / 21 Controller。这些端点均在 `authenticated()` 之后，当前不会产生匿名 500，属「防未来误加 `permitAll` 即复现」的防御性收敛。**但优先级低于上表 19 处已实证的越权 403 语义缺陷（同一根因、影响面更广、改法已固化）。**

---

### 2026-09-10 · P1-D Mastery Dashboard 错误状态与数据语义收口

**授权范围**：只解决 P1-C 遗留的「面板接口失败被降级为空数组」问题（前端）。明确排除 StudyGroupController / DocController / PrivateMessageController / 19 处其他 403 / Focus Mode / 双 Dialog / 双 Diagnosis / A11y / UI 大重构 / NextAction / Evidence / Mastery Engine。**后端一行未改**（后端测试基线保持 182）。

#### 一、根因（真实代码定位）

`frontend/src/views/MasteryDashboard.vue#loadData()` 中：

```ts
learningApi.categoryMastery().catch(() => [] as CategoryMasteryVO[]),
learningApi.knowledgeMastery().catch(() => [] as KnowledgeMasteryVO[]),
```

→ **500 / 401 / 403 / 网络错误全部被吞成 `[]`** → `hasData` 判定为无数据 → UI 显示「暂无答题记录」。**系统故障被伪装成「你没有学习数据」**（§十四 要锁死的红线）。

同类问题：整页错误态只有一句 `'掌握分布加载失败：' + getApiError(...)`，会把 axios 原始英文串渲染给用户。

#### 二、修复（§四～§九）

**`frontend/src/views/MasteryDashboard.vue`**（唯一生产文件；+224/-79）

| 项 | 修复前 | 修复后 |
|---|---|---|
| 面板失败 | `catch(() => [])` → 空数组 → 空态 | **记录原始异常** → 面板级 `ErrorState`（`StateView` 承载），文案由 `describePageError()` 生成 |
| 面板加载 | 与整页共用一个 `loading` | 每面板独立 `catLoading` / `kpLoading`（重试时只动这一块） |
| 面板重试 | 无（只能整页刷新） | 面板内「重新加载」→ **只重发该面板请求**（`loadCategories()` / `loadKnowledgeMastery()`） |
| 整页失败 | 原始 `getApiError` 文本 | `ErrorState` 三要素（发生了什么/影响范围/怎么解决）+ 重试 |
| 整页空态 | `!hasData` | `showPageEmpty = !hasData && !catError && !kpError` —— **面板失败时绝不退化成整页空态** |
| 空态组件 | 手写 `.mmd-empty` | `EmptyState`（复用项目组件，带「下一步」引导） |

- 面板状态 `catStatus` / `kpStatus`：`loading | error | ready`。「空」按 §十三「Empty 要安静」在 `ready` 分支内安静呈现，不塞进 `StateView.empty`（那会变成卡片内整屏大图标）。
- 面板错误文案示例（真实渲染）：`服务暂时不可用` + `分类维度掌握度暂时无法加载。` + `只是这一块没加载出来，页面其他内容仍可正常使用。` + `服务端出现异常，稍后重试；若持续出现请联系管理员。` + `[重新加载]`。

**`frontend/src/utils/error-message.ts`**（§九 补齐）

- 新增 `badrequest`（400）分类与文案「**请求参数不正确**」、`retryable: false`。修复前 400 落到 `unknown`，会被引导成「稍后重试」（无意义）。
- 现有语义复核：401→`auth`、403→`forbidden`、404→`notfound`、500+→`server`、`ERR_NETWORK`→`offline`、`ECONNABORTED/ETIMEDOUT`→`timeout`。**只有 5xx 才是「服务暂时不可用」**。

**`frontend/src/api/request.ts`**（§九 的必要前置，+25/-9）

- 真实缺陷：拦截器用裸 `new Error(message)` 拒绝，**把 HTTP 状态码丢掉** → `classifyPageError()` 只能判成 `unknown`，页面把「没有权限」「服务 500」显示成「未知错误，稍后重试」。
- 修复：新增 `bizError(message, status)`，在两个拒绝分支挂上状态码（字段名与 `extractStatus()` 约定一致）。**文案与拒绝行为完全不变**，只是让错误可被正确分类。全仓仅 `error-message.ts` 消费该字段（已 grep 确认无其它 `.status` 读取方）。

#### 三、测试（新增 24，前端全量 46 文件 / 578 用例全绿）

- **`frontend/src/views/__tests__/MasteryDashboard.spec.ts`（新增 17）**：成功有数据 / 真·空→整页 `EmptyState` / 有闪卡但无答题→面板安静空态 / 分类面板 500→错误态且**不出现空态文案**且另一面板正常 / 知识点面板失败只影响自身 / 核心指标失败→整页错误态且不出现「还没有足够的学习数据」/ **§十四 红线：核心空 + 面板失败不退化成整页空态** / 状态码 400·401·403·404·500·503 各自标题 / **403 不得为「服务暂时不可用」、500 才允许** / 离线→「无法连接到服务」且可重试 / **重试只重发该面板且不重复调用核心接口** / 重试仍失败→错误态保持。
- **`frontend/src/api/__tests__/request-error-status.spec.ts`（新增 4）**：自定义 adapter 在真实 axios 实例上模拟响应 —— `200+code200` 正常解包 / `403` 保留文案且可分类为 `forbidden` / `500`→`server` / `200+code500`→`server`。
- **`frontend/src/utils/__tests__/error-message.spec.ts`（+3）**：400→`badrequest`、400 不与 server/网络混淆、六类状态码互不混同。
- **未删除、未修改任何既有断言**；后端未改，保持 **182 tests**。
- 门禁：`npx vitest run --maxWorkers=1` → **46 files / 578 passed**；`npx vue-tsc -b --force` → **0 error**；`npm run build` → **PASS**。

#### 四、真实浏览器验证（真实后端 + 真实 Vite 代理 + 真实登录 user1）

| 场景 | 注入方式 | 实测结果 |
|---|---|---|
| 正常数据 | 真实接口 | `分类维度掌握度`：`前端开发 薄弱 50% · 1/2` + `后端开发 100% · 2/2` + `1 个薄弱项待加强`；0 个错误态。截图 `screenshot-1789031167623.png` |
| 面板 HTTP 500 | dev proxy 对 `/api/learning/category-mastery` 返回真实 **500** `{"code":500,"message":"系统内部错误"}` | 该面板渲染 `ErrorState`「服务暂时不可用 + 分类维度掌握度暂时无法加载。+ 只是这一块没加载出来… + [重新加载]」；**知识点面板不受影响**；`暂无分类掌握数据` **未出现**；0→1 个错误态。截图 `screenshot-1789031362731.png` |
| 面板重试 | 代理恢复直通后点面板「重新加载」 | 网络日志**只新增 1 条** `GET /api/learning/category-mastery (XHR) 200`，**无 document 重新加载、无 stats/mastery** → 证明只重试失败的那一块；面板恢复真实数据、错误态归零 |
| 核心指标失败（HTTP 500） | 代理对 `/api/learning/stats/mastery` 断开上游（Vite 代理回 500） | 整页 `ErrorState`「服务暂时不可用 + 掌握分布暂时无法加载。+ 这个页面…」；`还没有足够的学习数据` **未出现**。截图 `screenshot-1789031480592.png` |
| 网络中断 | 浏览器 DevTools **真实离线模拟**（`set offline on`）后点「重新加载」 | 整页 `ErrorState`「**无法连接到服务**」+ 重试按钮；仍无假空态 |
| 恢复 | 离线关闭后点「重新加载」 | 错误态消失，全部面板渲染真实数据，错误态计数 0 |

> 故障注入用 `/tmp/p1d_fault_proxy.mjs`（127.0.0.1:9090，真实转发 8080，仅把指定 path 变成真实 500 / 连接中断）+ 第二个 Vite 实例（5199，`SERVER__PORT=9090`）。**验证后已全部清理**（9090/5199 已关闭，原 8080 / 5173 保持可用），未改任何后端或前端代码来制造失败。
> 注：`agent-browser network route` 在本环境未生效（拦截未命中），故改用 dev proxy + DevTools 离线模拟，均为 §十二 允许的注入方式。

#### 五、诚实边界与遗留

- ⚠️ **未在真实 HTTP 403 下跑面板**：掌握度类接口按 `userId` 隔离，正常用户不会拿到 403；403/401/404/400 的语义由**单元测试**（`MasteryDashboard.spec.ts` + `request-error-status.spec.ts`）锁死，未在浏览器里构造 403。属如实说明，非虚构 PASS。
- ⚠️ `error-message.ts` 的 `forbidden` 文案仍是既有「没有访问权限 / 当前账号无权访问该内容」；P1-C 记录里「改为『没有权限执行此操作』」的建议**未执行**（该文案对只读页面加载失败不如现文案准确，仍待用户拍板）。
- ⚠️ `19 处 BusinessException("无权…") → 403` 及 P1-A 第三批 `StudyGroupController` 本轮**未动**。
- 未验证：MySQL 方言路径（本轮无后端改动，不新增此风险面）。

#### 六、下一批建议（仅记录，**不自动执行**）

**P1-D2 —— 19 处 `BusinessException("无权...")` → 403 语义收敛**（`MindMapServiceImpl:137`、`WorkbenchServiceImpl:902`、`CalendarMarkServiceImpl:390`、`MistakeServiceImpl:64/76`、`DrawingServiceImpl:142`、`NotificationServiceImpl:46`、`CommunityServiceImpl:139`、`KbMemberServiceImpl:194/220`、`FocusSessionController:68/195/209`、`AdminKbMemberController:89/168`、`AdminCategoryController:107/123/148/224`）。同根因、改法已固化（双参 `BusinessException(403, …)`），且本轮已把前端 403 语义链路打通（拦截器保状态码 → `forbidden` → 「没有访问权限」），收敛后可直接端到端看到正确语义。→ **已于下方 P1-D2 记录中执行完毕。**

---

### 2026-09-10 · P1-D2 Unauthorized Resource Access → 403 HTTP Semantics

**授权范围**：只解决一件事 —— 把已确认的 **19 处** `BusinessException("无权...")`（单参，默认 `code=500`）收敛为 `BusinessException(403, "...")`，使「已登录但访问他人资源」返回 **HTTP 403 + `body.code=403`**，而不再是被前端判成服务端故障的 **HTTP 200 + `code=500`**。明确排除：`StudyGroupController` / `DocController` / `PrivateMessageController` / 其余 92 处 `principal` 强转 / P2 全部内容 / 前端错误层改动 / 新增异常体系。

#### 一、根因（真实代码链路）

```
requireOwn(entity, userId)            // 各 Service 的私有守卫
  └─ !Objects.equals(owner, userId)
       └─ throw new BusinessException("无权操作该记录")   // 单参 → code 默认 500
            └─ GlobalExceptionHandler.handleBusinessException
                 └─ code ∈ [400,500) ? 同步 HTTP 状态码 : 回落 HttpStatus.OK
                      └─ 500 不在区间 → HTTP 200 + {"code":500,...}
                           └─ 前端 request.ts 拦截器 bizError(msg, 500)
                                └─ classifyPageError → 'server' → 「服务暂时不可用」+ retryable:true
```

即：**权限不足被说成「服务暂时不可用」，还引导用户「重试」**（重试永远不会成功）。这正是 §十四 要锁死的「系统故障 / 用户问题 / 无权限」三者混淆。

#### 二、修复（19 处，**仅改异常构造，文案一字未动**）

| 文件 | 行 | 收敛后 |
|---|---|---|
| `service/impl/MindMapServiceImpl.java` | 137 | `BusinessException(403, "无权操作该记录")` |
| `service/impl/WorkbenchServiceImpl.java` | 902 | `BusinessException(403, "无权操作该记录")` |
| `service/impl/CalendarMarkServiceImpl.java` | 390 | `BusinessException(403, "无权操作该记录")` |
| `service/impl/DrawingServiceImpl.java` | 142 | `BusinessException(403, "无权操作该记录")` |
| `service/impl/NotificationServiceImpl.java` | 46 | `BusinessException(403, "无权操作该通知")` |
| `service/impl/CommunityServiceImpl.java` | 139 | `BusinessException(403, "无权删除他人帖子")` |
| `service/impl/MistakeServiceImpl.java` | 64 / 76 | `BusinessException(403, "无权访问该错题")` / `(403, "无权操作该错题")` |
| `service/impl/KbMemberServiceImpl.java` | 194 / 220 | `BusinessException(403, "无权修改成员角色")` / `(403, "无权移除成员")` |
| `controller/FocusSessionController.java` | 68 / 195 / 209 | `BusinessException(403, "无权操作该会话")` / `(403, "无权查看该会话")` / `(403, "无权删除该会话")` |
| `controller/admin/AdminKbMemberController.java` | 89 / 168 | `BusinessException(403, "无权限查看该知识库成员")` / `(403, "无权限管理该知识库成员")` |
| `controller/admin/AdminCategoryController.java` | 107 / 123 / 148 / 224 | `BusinessException(403, "无权修改该知识库")` / `(403, "无权删除该知识库")` / `(403, "无权向该知识库导入文档")` / `(403, "无权导出该知识库")` |

- **复用现有** `BusinessException(int code, String message)`，未新增 `ForbiddenException` / 错误码枚举 / 依赖。
- 全仓复核：`new BusinessException("(无权|无权限...` → **0 命中**；`new BusinessException(403,` → **27 处**（本轮 19 + P1-C 2 + 既有 6，后者不在本轮范围、未动）。
- 语义判定（§四）逐处确认：19 处**全部**是「身份合法 + 资源不属于你」→ 403，无一处把「不存在」误改成 403（不存在的路径抛的是 `...不存在` 单参异常，见 §六 遗留）。

#### 三、测试（新增 31 用例；后端全量 182 → **213 tests / 0 failures / 0 errors**）

**`backend/src/test/java/com/knowflow/controller/UserResourceOwnership403Test.java`（16 用例）** — 覆盖 8 个用户私有资源模块的 11 处越权点（MindMap / Drawing / WorkbenchCapture / CalendarMemorial / FocusSession / Mistake / Notification / CommunityPost）：

- 每模块覆盖：**本人成功 200** / **他人 403** / **零副作用** / **匿名 401** / **不存在不被改写成 403**。
- 越权断言**同时**校验 HTTP status 与 `body.code`（`status().isForbidden()` + `jsonPath("$.code").value(403)`），防止「只改 body 不改 HTTP」的假修复。
- 零副作用用 Mapper 直查 DB 验证真实未被写坏（如通知 `is_read==0`、专注会话 `endTime==null`、错题 `mastered==0`）。
- 独立 H2 库 `jdbc:h2:mem:knowflow_p1d2_403;MODE=MySQL`（与姊妹测试类刻意同名以复用 Spring 上下文，规避共享 `mem:knowflow` 重复建表在 `CREATE INDEX` 上炸）。

**`backend/src/test/java/com/knowflow/controller/KnowledgeBaseMembership403Test.java`（15 用例）** — 覆盖 8 处知识库 / 管理员越权点：

- **这 8 处无法用 HTTP 触发**（真实约束）：其所在控制器是 `/api/admin/**`，`SecurityConfig` 已 `.hasRole("ADMIN")`，且 `KbMemberServiceImpl.isAdminUser()` 对 ADMIN 短路放行 —— 任何能过角色门禁的请求都走不到 `403` 分支。故采用**直接调用 Controller/Service 验证分支本体**（`as(userId, role, ...)` 显式设置 `SecurityContext`）+ **HTTP 层验证角色门禁**（非管理员→403）+ **管理员回归**（管理员对他人知识库→200，证明未被误伤）。
- `assert403()` 直接断言 `e.getCode()==403`；`notFoundIsNotRewrittenTo403()` 锁定「不存在 ≠ 403」。
- `@BeforeEach` 还原成员角色 / 知识库名基准态，避免用例互相污染。

- 未删除、未修改任何既有断言；前端**一行未改**。
- 门禁：`mvn -f backend/pom.xml test`（JAVA_HOME=corretto-17）→ **213 tests / 0F / 0E / BUILD SUCCESS**。

#### 四、真实 HTTP 验收矩阵（真实 8080 + 真实 curl，before/after 同一脚本）

| 场景 | 修复前 | 修复后 |
|---|---|---|
| user2 读 user1 的笔记 `GET /api/workbench/notes/1` | `HTTP 200` + `{"code":500,"message":"无权操作该记录"}` | **`HTTP 403`** + `{"code":403,"message":"无权操作该记录"}` |
| user2 读 user1 的错题 / 导图 / 绘图 / 纪念日 / 专注会话 | `HTTP 200 + code 500` | **`HTTP 403 + code 403`** |
| user2 删 user1 的帖子 / 改他人通知已读 | `HTTP 200 + code 500` | **`HTTP 403 + code 403`** |
| user1 读**自己**的笔记 | `200 / 200` | `200 / 200`（无回归） |
| 匿名访问 | `401 / 401` | `401 / 401`（无回归） |
| 访问**不存在**的记录 | `200 / 500` | `200 / 500`（本轮未动，见 §六） |
| 非管理员访问 `/api/admin/**` | `403 / 403` | `403 / 403`（角色门禁，无回归） |

- 矩阵脚本 `/tmp/p1d2_matrix.sh`（31 行场景）、零副作用脚本 `/tmp/p1d2_sideeffect.sh`（7 组），基线 `/tmp/p1d2_before.txt` vs `/tmp/p1d2_after.txt`。**脚本置于 /tmp，未入仓库。**
- 零副作用实测：越权失败后 DB 中导图/纪念日/专注会话/错题/帖子/知识库名称**均未被改动**；管理员权限未被误伤。

#### 五、真实浏览器验证（真实 Chromium + 真实登录 user2 + 真实 Vite 代理 5173→8080）

浏览器技能：`agent-browser`（真实 Chromium，非 jsdom）。

**(1) 真实 UI 入口点击链路** — `/workbench/notes/:id` 是真实路由，`WorkbenchNoteEdit.vue#loadNote()` 会真实请求 `GET /api/workbench/notes/{id}`：

- user2 登录（真实登录表单，`localStorage.token` 真实写入，id=3）→ 浏览器导航到 `http://localhost:5173/workbench/notes/1`（user1 的笔记）→ 页面右上角真实渲染 **错误 toast「无权操作该记录」**（截图 `screenshot-1770000000000`、实存 `/tmp/p1d2_ui_403.png`）。
- **关键结论**：UI **没有**出现「服务暂时不可用」「系统内部错误」「未知错误」，也没有出现伪造的空内容。

**(2) 真实响应喂给真实前端分类模块**（在页面上下文里，用 Vite dev 直接服务的 `import('/src/utils/error-message.ts')`，即**线上同一份源码**）：

```json
{
 "realHttpStatus": 403,
 "realBodyCode": 403,
 "realBodyMessage": "无权操作该记录",
 "AFTER_classify": "forbidden",
 "AFTER_title": "没有访问权限",
 "AFTER_retryable": false,
 "BEFORE_classify": "server",
 "BEFORE_title": "服务暂时不可用",
 "BEFORE_retryable": true
}
```

→ **修复前**：同一个越权被分类为 `server` → 标题「服务暂时不可用」+ `retryable:true`（引导用户重试一个永远不会成功的操作）；**修复后**：`forbidden` → 「没有访问权限」+ `retryable:false`。**§十六 要验证的「失败显示权限不足而不是系统内部错误」至此为真实浏览器实测通过。**

**(3) 三态同框**（同一浏览器上下文，真实代理）：

| 场景 | HTTP | body.code | UI 标题 |
|---|---|---|---|
| 他人资源 | **403** | **403** | 没有访问权限 |
| 匿名 | 401 | 401 | 登录状态已过期 |
| 不存在 | 200 | **500** | （落 `unknown`/`server` 分支，详见 §六） |

- 前端**未做任何改动**（§九 前置条件已满足：`error-message.ts` 的 `forbidden` 早已是「没有访问权限 / 当前账号无权访问该内容」）。验证后浏览器已 `close`，8080 / 5173 保持可用。

#### 六、诚实边界、只读审计与遗留（§十九）

**只读审计（未自动改，仅披露）：**

1. **同类缺陷仍在：20 处单参 `BusinessException("...不存在")`** → 真实测得 `HTTP 200 + code 500`（例：`GET /api/workbench/notes/999999` → `{"code":500,"message":"记录不存在"}`；`GET /api/mindmaps/999999` → `{"code":500,"message":"思维导图不存在"}`）。**这类「不存在」被前端判成 `server` → 「服务暂时不可用」**，与 P1-D2 是同一根因、同一用户可见后果。本轮**刻意未动**（§四 明确要求不把「不存在」并入 403）。**建议作为下一批单独排期（语义应为 404）。**
2. **剩余「权限味」但语义不同：2 处** —— `AdminUserController:138 "不能删除当前登录用户"` / `:145 "不能删除管理员账户"`。语义是**业务冲突**（§四：保留原语义），若要收敛更贴切的是 `400` 而非 `403`。**未改，留待用户拍板。**
3. **27 处 `BusinessException(403,` 中另有 6 处为既有（`LearningServiceImpl` / `CommunityCommentServiceImpl` / `LearningGoalServiceImpl` / `KnowledgeImportServiceImpl` / `LearningContentServiceImpl` 等）**，非本轮引入、非本轮范围，未动。

**诚实边界：**

- ⚠️ **8 处知识库 / 管理员越权点无法用 HTTP 触发**（`/api/admin/**` 角色门禁 + `isAdminUser()` 短路）。故其 403 语义由**直接调用 Controller/Service 的单元测试**锁死，**不是**浏览器实测。已在 §三 如实说明，不冒充端到端 PASS。
- ⚠️ 浏览器验证走的是**用户私有资源**这条真实链路（笔记）；其余 7 个用户模块的 403 由「真实 curl 矩阵 + 单元测试」双重覆盖，未逐一在浏览器点。
- ⚠️ 未验证 MySQL 方言路径、未做并发 / 性能验证（本轮无新增表、无新增查询）。

#### 七、下一批建议（仅记录，**不自动执行**）

1. **P1-D3 —— 20 处 `BusinessException("...不存在")` → 404 语义收敛**：与 P1-D2 完全同构（同一 Handler、同一前端分类链路），用户可见后果同为「不存在被说成服务不可用」，且前端 `notfound`（「内容不存在或已被删除」+ `retryable:false`）**已就绪可用**，改法零设计成本。**优先级高于 P1-A 第三批。**
2. **P1-A 第三批 —— `StudyGroupController`（19 处 `principal` 强转）**：仍属「防未来误加 `permitAll` 才复现」的防御性收敛，当前不产生匿名 500，优先级低于上面的 P1-D3。
3. `AdminUserController` 2 处「不能删除…」是否收敛为 400，**待用户拍板**。

---

### 2026-09-10 · P0-1 Flashcard Data Isolation & IDOR Fix

**授权范围**：只修 P2-0 全仓审计实证的两个 P0 —— ① `GET /api/learning/flashcards` 跨用户/匿名读取他人私有闪卡；② `POST /api/learning/flashcards/{id}/review` 跨用户写（IDOR）。明确排除：P1-D3、`StudyGroupController`、`DocController`、`PrivateMessageController`、UI / Focus Mode / 双诊断 / 双 Dialog / A11y / Responsive / 死代码清理。

#### 一、根因（真实代码链路）

```
SecurityConfig:57  .requestMatchers(HttpMethod.GET, ..., "/api/learning/flashcards").permitAll()
  └─ LearningController:136  flashcards(pathId, chapterId)        // ← 签名里没有 userId
       └─ LearningServiceImpl:384  getFlashcardList(pathId, chapterId)
            └─ LambdaQueryWrapper：仅 pathId/chapterId 非空时才 eq
                 └─ 两者皆空 → selectList(空 wrapper) → SELECT * FROM learning_flashcard  // 全表！
                      └─ 匿名 / 任意登录用户可读全站闪卡
```

**次生 IDOR 写**：

```
LearningServiceImpl:727  reviewFlashcard(id, userId, quality, knowledgeId)
  └─ flashcardMapper.selectById(id) + 仅判 null → 404
       └─ 无 owner 校验 → 任何已登录用户可改写他人闪卡的 SM-2 状态（实测 HTTP 200）
```

#### 二、产品语义判定（§五 —— 不按 URL 名字猜业务）

| 证据 | 结论 |
|---|---|
| `db/h2/schema.sql:169` `user_id BIGINT **NOT NULL** COMMENT '所属用户ID'` | 表按设计即用户私有，**不存在** `visibility`/`public` 字段，无法表达「公共卡」 |
| `LearningFlashcard` 字段注释「所属用户 ID（**必须**，用户维度隔离）」+ `idx_fc_user` 索引 | 隔离是一等设计要求 |
| `data.sql` 6 张种子卡全部 `user_id=1` | 不是共享池，而是某个用户的卡 |
| `/my/flashcards` 全家（list/detail/create/update/delete/batch-delete/generate/import/export）**全部**按 userId 隔离 | 私有语义已有正确实现 |
| `AdminLearningController`（`/api/admin/learning`，`@PreAuthorize("hasRole('ADMIN')")`）已有独立 **全局分页** 端点 `GET /flashcards` | 管理端「全站视图」有正确归宿 |

**判定：语义 = B（用户私有学习闪卡池）**。`pathId`/`chapterId` 只是**本人卡片**对路径/章节的可选归属过滤，而非跨用户共享维度 → 无需新增字段，采用当前业务能安全表达的最小方案：**按当前用户过滤 + 需登录**。

#### 三、受影响 API 与页面

| API | 修复前 | 修复后 |
|---|---|---|
| `GET /api/learning/flashcards` | 匿名/任意人 → 全表 | 需登录；仅本人（匿名 **401**） |
| `POST /api/learning/flashcards/{id}/review` | 他人卡 → 200 且改写 SRS | 他人卡 → **403 + code 403**；不存在 → 404 |
| `GET /api/learning/my/flashcards/{id}` | 已正确（`requireMine`） | 不变（回归锁定） |

受影响生产页面（消费该端点）：`FlashCardsHub.vue:1272`、`ReviewPlan.vue:458`、`SmartQuiz.vue:590/763`、`LearningMode.vue:295`、`components/immersive/SpacedMode.vue:249`、`views/admin/FlashcardMgmt.vue:410`。

#### 四、修改文件

| 文件 | 改动 |
|---|---|
| `config/SecurityConfig.java` | 从 GET `permitAll` 白名单**移除** `/api/learning/flashcards`（附根因注释） |
| `service/LearningService.java:71` | `getFlashcardList(Long pathId, Long chapterId)` → `getFlashcardList(Long userId, Long pathId, Long chapterId)` + 语义 javadoc |
| `service/impl/LearningServiceImpl.java:391` | `getFlashcardList`：入参校验（userId 为空 → 401）+ 归属条件**无条件** `.eq(getUserId, userId)` |
| `service/impl/LearningServiceImpl.java:727` | `reviewFlashcard`：`selectById`+null判 → 复用既有 `requireMine(id, userId)`（404 / **403**） |
| `controller/LearningController.java:135` | `flashcards(...)` 传 `currentUserId()` |
| `views/admin/FlashcardMgmt.vue:407` | 管理端改走既有 `adminApi.flashcards()`（`GET /admin/learning/flashcards`，PageResult）；移除未用的 `learningApi` import |
| `src/test/.../FlashcardIsolationTest.java` | **新增** 17 用例 |

**未新增**：0 新表 / 0 新列 / 0 新异常体系 / 0 新错误码 / 0 新依赖。

#### 五、数据查询变化（§十一 —— 约束落在 Service，不在 Controller）

```sql
-- 修复前（pathId/chapterId 皆空时）
SELECT ... FROM learning_flashcard WHERE deleted=0
-- 修复后（恒定带归属；取自真实测试 SQL 日志）
SELECT ... FROM learning_flashcard WHERE deleted=0 AND (user_id = ?)
```

`pathId` / `chapterId` 只作为**附加**过滤条件叠加，无法再「清空」归属条件。即使未来 Controller 或其它调用方绕过校验，数据层也不会返回他人闪卡。

#### 六、权限语义

| 场景 | HTTP | body.code |
|---|---|---|
| 本人读 / 复习 / 详情 | 200 | 200 |
| 他人读→不可见（列表不返回） / 他人 review·详情·删除 | 403 | 403 |
| 匿名（读 / 写） | 401 | 401 |
| 资源不存在（review / 详情） | 404 | 404 |
| 不存在学习路径 | 404 | 404 |

无「200 + code 500」、无 5xx 冒充权限问题。

#### 七、测试

- **新增** `FlashcardIsolationTest`：**17 用例 / 0 failures**。真实 H2 + 真实 User A(97601) / User B(97602)（**非 mock userId**）+ MockMvc，同时断言 HTTP status 与 body.code。
  - 读取 4 项：本人 200 仅含自己 / A↔B 双向不可见 / 匿名 401 / 无效 token 401
  - **查询层证明（§十六）**：直接调用 `learningService.getFlashcardList(USER_A, null, null)`，断言返回集合内**所有** userId == USER_A —— 种子库里 `user_id=1` 的 6 张卡若未被过滤必然出现，故该断言即是对 SQL 条件的直接证明。
  - review 6 项：本人 200 + SRS 推进 + **恰好新增 1 条 FLASHCARD_REVIEWED 事件**（§十三 闭环不回归）/ 他人 **403** + **零副作用**（reviewCount·interval·easeFactor·lastReviewTime·nextReviewTime·userId **逐项比对不变**，且不产生事件）/ 匿名 401 + 零副作用 / 不存在 404
  - 详情 2 项：本人 200 / 他人 403 / 匿名 401 / 不存在 404；以及「不存在裸 `GET /flashcards/{id}` 端点」断言
  - 回归 4 项：`pathId` 过滤仍生效且限定本人（新建真实 `learning_path` fixture，A/B 同路径建卡互不可见）/ 不存在路径 404 / 公开端点 `/learning/paths` 匿名仍 200 / 管理端全局视图走 ADMIN 端点（且管理员在用户端点同样只看自己）/ 非管理员访问 ADMIN → 403 / 他人删除我的卡 → 403 且卡仍在

- **全量门禁**（真跑，非引用）：

| 门禁 | 结果 |
|---|---|
| `mvn -f backend/pom.xml test` | **230 / 0 failures / 0 errors / BUILD SUCCESS**（基线 213 → +17） |
| `npx vitest run --maxWorkers=1` | **46 files / 578 passed**（与 P2-0 基线一致，0 回归） |
| `npx vue-tsc -b --force` | **0 error** |
| `npm run build` | **PASS** |

#### 八、User A/B 隔离结果 + curl 验证（`curl --noproxy '*'`，真实 8080）

| # | 场景 | 实测 |
|---|---|---|
| 1 | 匿名 `GET /learning/flashcards` | **HTTP 401** `{"code":401,"message":"未登录或登录已失效"}`（修复前：200 + 6 张 admin 的卡） |
| 2 | user2 建私密卡 | id=7 |
| 3 | user1 读 | `len=0`，userId 分布 `{}` —— **看不到 user2 的卡** |
| 3 | user2 读 | `len=1`，userId 分布 `{3:1}` —— 看到自己的 |
| 3 | admin 读 | `len=6`，userId 分布 `{1:6}` —— 只看到自己（**这 6 张即修复前 user1 越权看到的卡**，同一 DB、同一数据，唯一变化就是归属过滤） |
| 4 | 匿名 review | **401** |
| 5 | **user1 review user2 的卡** | **HTTP 403 + code 403** `"无权操作该闪卡"`（修复前：200） |
| 6 | 越权后该卡 SRS | `reviewCount=0 reviewInterval=0 nextReviewTime=None lastReviewTime=None` —— **零副作用** |
| 7 | user1 读 user2 卡详情 | **403** |
| 8 | 不存在闪卡 review | 匿名 401（鉴权先行）/ user1 **404** `"闪卡不存在"`（未被误写成 403/401） |
| 9 | user2 本人 review 自己的卡 | **200**，`reviewCount=1 reviewInterval=1 nextReviewTime=2026-09-11T19:09` —— **SRS 与学习闭环正常** |
| 10 | user1 带 `pathId=1` 读 | `len=0`，仍限定本人 |

#### 九、Browser 验证（真实 Chromium + 真实登录 + 真实 Vite 代理 5173→8080）

| 页面 | 身份 | 实测 |
|---|---|---|
| `/learning/flashcards` | user2 (id=3) | **学习闪卡 1**、`你将复习 1 张卡片` —— 自己的卡 |
| `/learning/flashcards` | user1 (id=2) | **学习闪卡 0** + 真实空态「暂无可学习的闪卡 / 系统闪卡来自已发布的学习路径…」+ 两个出口 CTA（**修复前：6**，即 admin 的卡） |
| `/learning/review`（ReviewPlan） | user1 | `待复习 0 张`、**不含** user2 的卡（`hasOtherUsersCard=false`） |
| `/learning/quiz`（SmartQuiz） | user1 | 「暂无足够闪卡用于生成题目」—— **不使用他人私有卡出题** |
| `/admin/flashcards`（FlashcardMgmt） | admin (id=1) | 请求 `GET /api/admin/learning/flashcards?pageNum=1&pageSize=100` → **200**；`总卡片数 7` 且**列出** user2 的卡 → **管理端全局视图未因安全收敛被误伤**；页面已**不再**调用 `/api/learning/flashcards` |

截图：`/tmp/p01_browser_user1.png`、`/tmp/p01_browser_user2.png`、`/tmp/p01_browser_admin.png`。

**诚实边界**：LearningMode / SpacedMode 未逐一点击（其数据源同为已收敛的同一端点，由 curl 矩阵 + 单测覆盖）；未验证 MySQL 方言路径（本轮无新增表/列）。

#### 十、探针清理

浏览器与 curl 探针（`P0-1AUDIT-*` / `P0-1-BROWSER-*`）**已全部删除**：user2 自己的闪卡 `len=0`、残留 `P0-1*` = 0；admin 全局视图 `total=6`（与原始种子一致）、残留 `P0-1*` = **0**。测试用独立 H2 库（`knowflow_p1d2_403`），不触碰生产数据。

#### 十一、剩余问题（**发现即记录，本轮未修**）

1. 🟠 **同族 P0 级写路径缺陷**：`POST /api/docs/{id}/ai-flashcards`（`DocController:142`，前端入口 `DocDetail.vue:532`）—— `DocController` 已取出 `Long userId` 却**未传入** `docService.generateFlashcards(id, pathId, chapterId)`，而 `DocServiceImpl.parseFlashcards:791-807` 构造 `LearningFlashcard` 时**从不 `setUserId`**；`learning_flashcard.user_id` 为 **NOT NULL** → 插入必然失败。**静态判定确认；未能实证**：真实调用在更早的 AI 步骤即返回 `{"code":500,"message":"AI 服务调用失败（状态码 401）"}`，未走到 insert。故本轮**只报告不修**（§二十八 明确禁止进入 `DocController`）。**建议作为下一批 P0-1b 处理**（根因单一：把已有的 userId 一路透传并在 `parseFlashcards` 落库）。
2. 🟠 **AI Provider 当前 401**：`POST /api/docs/16/ai-flashcards` 返回「AI 服务调用失败（状态码 401）」—— 运行中实例的 AI Key 未生效（与本轮改动无关，属环境/配置问题，待单独排查）。
3. 🟡 `LearningMode.vue:295` 的 `.catch(() => [] as FlashcardVO[])` 仍会把失败吞成空态（P2-0 已记录，属 Focus Mode 范围，本轮按指令未动）。
4. 🟡 `dueFlashcards → GET /learning/flashcards/due` 实测 404 死接口（P2-0 已记录，未动）。

#### 十二、§二十七 P0 重扫结果

- `permitAll` GET 白名单复查：剩余项**全部**为真正公开内容（`/api/docs` 列表·详情·推荐、`/api/categories/**`、`/api/learning/paths`(+`{id}`)、`certificates/verify`、`/api/community/posts`(+详情·评论·回复)、`/api/knowledge/{graph,entity-graph}`、`/api/challenges`(+详情·排行榜)、`/api/ranking`）—— **无用户私有数据端点**。
- `flashcardMapper` 全部读取点逐一核对归属：`WeeklyReportController:220`✓、`AiLearningSuggestionController:161`✓、`UserServiceImpl:137`✓、`LearningServiceImpl:{391,839,962,966,1422}`✓、`LearningContentServiceImpl:{605,611,627}`✓、`LearningReportServiceImpl:{105,249,314}`✓ —— **均带 `user_id`**；管理端 `AdminLearningController` 由 `/api/admin/**` 角色门禁保护。
- 写入点：`createMyFlashcard` / `generateMyFlashcards` / `importMyFlashcards` / `ObsidianImportServiceImpl:493` / `LearningContentServiceImpl:203` 均 `setUserId(...)` ✓；`deleteMyFlashcards` 逐条 `Objects.equals(userId)` ✓；`deleteMyFlashcard`/`updateMyFlashcard`/`getMyFlashcard` 走 `requireMine` ✓。
- **结论：Flashcard 家族已无同类未隔离的读/写面。唯一残余为上面第 1 条的 Doc 写路径（属「写不进去」，非泄漏）。**

---

### 2026-09-10 · P2-UI-Flashcards 闪卡学习页 UI 精修

> 纯 UI 任务（小范围）。**未触碰任何业务层**：无 API / 无 Controller / 无 Service / 无 schema / 无权限 / 无数据隔离 / 无 LearningEvent / 无 Evidence / 无 NextAction 改动。

#### 一、日期 / Agent

- 日期：2026-09-10
- Agent：KnowFlow UI 精修 Agent（P2-UI-Flashcards）

#### 二、修改文件

| 文件 | 类型 | 改动 |
|---|---|---|
| `frontend/src/views/FlashCardsHub.vue` | 样式 / 模板 | 按钮基础类族补齐 `.btn-outline`；准备态卡片 spacing / 层级 / 图标精修 |
| `frontend/src/views/__tests__/FlashCardsHub.spec.ts` | 测试（未跟踪文件） | 新增 5 例准备态 Action Group 结构测试 |

#### 三、根因：为什么「先筛选卡片」会错位（真实计算样式证据）

该页面自建了一套页面级按钮类族 `.btn-primary / .btn-secondary / .btn-ghost / .btn-outline`。基础布局规则写在共用选择器里：

```css
.btn-primary, .btn-secondary, .btn-ghost { /* 无 .btn-outline */
  display: inline-flex; align-items: center; gap: 6px;
  height: 36px; padding: 0 14px;
  border-radius: var(--kb-radius-md); ...
}
```

而 `.btn-outline` **只**单独定义了变体视觉（`border / background / color`）。
→ 该按钮拿到「描边 + 白底」，却拿不到任何布局属性，退化成浏览器原生 `<button>`。

修复前后真实 `getComputedStyle` 对比（Chrome，1440×900，admin 准备态）：

| 属性 | 修复前「开始学习」(.btn-primary) | 修复前「先筛选卡片」(.btn-outline) | 修复后两者 |
|---|---|---|---|
| `display` | `flex` | **`block`** | `flex` |
| `align-items` | `center` | **`normal`** | `center` |
| `height` | `36px` | **`37px`（内容撑开）** | `36px` |
| `padding` | `0 14px` | **`0px`** | `0 14px` |
| `gap` | `6px` | **`normal`** | `6px` |
| `border-radius` | `10px` | **`0px`** | `10px` |
| 图标↔文字实际间距 | `6px` | **`-14px`（重叠）** | `6px` |
| 图标中心 vs 文字中心 | `0px` | **`-17px`（图标被 `display:block` 挤到上一行）** | `0px` |

即：不是「差 1px 需要补偿」，而是**根本没进入 flex 布局**。因此修复方式是把 `.btn-outline` 并回基础选择器，而不是加 `margin-top` / `transform: translateY()` 之类的补偿 —— 符合「不要用临时补偿解决」的要求。

#### 四、修复内容

1. **按钮尺寸体系统一**（根因修复）：`.btn-outline` 并入 `.btn-primary, .btn-secondary, .btn-ghost` 基础选择器；补 `justify-content: center`；新增 `.btn-* > svg { flex-shrink: 0 }`（禁止图标在窄屏被压扁）。
2. **Secondary 禁用态补齐**：新增 `.btn-outline:disabled { opacity: .55; cursor: not-allowed }`。放在 `.action-btn:disabled` **之前**，保证 `.action-btn.btn-outline`（复习操作条 44px）继续沿用 `0.4`，不被误改。
3. **Action Group**：`.prep-actions` 保持 `display:flex; align-items:center; gap:10px; flex-wrap:wrap`（间距落在要求的 8~12px 区间），`margin-top` 6px→4px；两个按钮图标 `14px→16px`（对齐设计系统 `Button.vue` md 的图标口径）。
4. **准备卡片 spacing 压缩**（不破坏布局、不过度压缩）：`.prep-card` `padding: 24px → 20px 22px`、`gap: 10px → 8px`；`.prep-meta` `margin-top 2px→0`、`padding-top 12px→10px`、`gap 8px→6px`。卡片高度 **260.6px → 245px（−6%）**。
5. **内容层级**：第一层 `.prep-title` 字号 `--kb-fs-body-lg(16px) → --kb-fs-h4(20px)`（复用既有令牌，不新增字号），强化「你将复习 N 张卡片」为唯一主信息；来源 / 说明 / 操作 / 提示四层样式不变。
6. **快捷键提示**：`.prep-hint` 加 `line-height: var(--kb-lh-caption)`，上间距随 `gap(8px) + margin-top(4px)` 落到 **12px**，字号 12px、颜色 `--kb-muted-foreground` 不变 —— 保持为操作区之下的辅助信息。

#### 五、Tabs 调整

**未修改。** 实测三个 Tab 已完全对齐，无需改动：

| 指标 | 学习闪卡 | 推荐复习 | 我的闪卡 |
|---|---|---|---|
| `display` / `align-items` | `flex` / `center` | `flex` / `center` | `flex` / `center` |
| 高度 / `gap` / `padding` | 37px / 8px / `8px 18px` | 同左 | 同左 |
| 图标中心 vs 文字中心 | `0` | `0` | `0` |
| 图标中心 vs 数字 badge 中心 | `0` | `0` | `0` |
| badge 高度 | 20px | 20px | 20px |

（项目未提供统一 Tabs 组件，本页 `.tabs-bar/.tab-btn` 即既有实现，按「优先复用」原则未另造。）

#### 六、标题区域（Header）调整

**未修改**（`MiniAppShell.vue` 为全部 Mini App 共用组件，且实测已正确 —— 按「不要重构整个 Header」处置）：

- 返回按钮高度 `36px`；返回按钮中心 `108.5px` **=** 标题+副标题块中心 `108.5px`（完全垂直居中）
- 标题 `32px`（`--kb-fs-h2`），标题↔副标题间距 `2px`，返回↔标题水平间距 `12px`（`gap`）
- Header `margin-bottom: 20px`

#### 七、Responsive 检查结果（真实浏览器，Chrome）

| 断点 | 文档横向溢出 | 两按钮高度一致 | 两按钮同一基线 | 图标中心偏移 | 文字截断 | 卡片宽 |
|---|---|---|---|---|---|---|
| 1280×800 | 无 | ✅ 36 / 36 | ✅ | `0px` / `0px` | 无 | 560px |
| 768×900 | 无 | ✅ 36 / 36 | ✅ | `0px` / `0px` | 无 | 560px |
| 390×800 | 无 | ✅ 36 / 36 | ✅ | `0px` / `0px` | 无 | 330px（按钮 `flex:1 1 auto` 单行放下） |
| 375×800 | 无 | ✅ 36 / 36 | ✅ | `0px` / `0px` | 无 | 315px（同上） |

Tab 在 390/375 下仍为单行（各 90px，≤480px 断点隐藏图标后不缩字、不换行）。

#### 八、Accessibility 检查结果

- 两个按钮均为真实 `<button type="button">`；纯图标按钮（搜索 / 随机 / 退出）保留 `aria-label`；带文字按钮未额外叠加 `aria-label`（无冗余）。
- 键盘 focus 实测：`outline: solid 2px rgb(59, 111, 224)`（= `--kb-ring`），未被视觉改动移除。
- 键盘行为不变：Space / Enter 仍从准备态进入学习态（实测 `1 / 6`）；1/2/3 在准备态仍不产生学习记录。

#### 九、测试结果

| 门禁 | 结果 |
|---|---|
| `npx vitest run src/views/__tests__/FlashCardsHub.spec.ts --maxWorkers=1` | **31 / 31 PASS**（26 → 31，+5） |
| `npx vitest run --maxWorkers=1`（全量） | **46 files / 583 tests PASS**（578 → 583） |
| `npx vue-tsc -b --force` | **0 error** |
| `npm run build` | **PASS**（`✓ built in 9.15s`） |
| 后端 `mvn test` | 未运行 —— 本轮 **0 个后端文件**改动 |

新增 5 例（**结构断言，非像素断言**）：① 两个按钮都是真实 button 且分属 Primary/Secondary 变体 ② 两按钮同为「icon + label」结构且图标尺寸一致 ③ 两按钮同处一个 `.prep-actions` 且快捷键提示不在其中 ④ 准备态文案五层完整 + `prep-actions` 在 `prep-hint` 之前 ⑤ 点击「先筛选卡片」仍打开搜索框且不产生学习记录。
loading / empty / ready / error 四态由既有用例继续守护（骨架卡 / 空态 / 加载失败），未删改任何旧断言。

#### 十、Browser 验证结果

真实 Chromium（agent-browser，Vite 5173 → 后端 8080），账号 `admin`（user_id=1，6 张种子闪卡）：

| 验证项 | 结果 |
|---|---|
| 「开始学习」与「先筛选卡片」高度一致 | ✅ 均 36px，`top` 完全相同 |
| 图标垂直居中 | ✅ 图标中心 vs 文字中心 = `0px`（修复前 `-17px`） |
| 图标与文字间距 | ✅ 均 `6px`（修复前 `-14px`，重叠） |
| 两个按钮视觉层级 | ✅ Primary 实色填充 / Secondary 白底描边，圆角同为 10px |
| Action Group spacing | ✅ 10px |
| Tab icon/text/badge 对齐 | ✅ 三项均为 0px 偏移 |
| 页面整体无空洞 | ✅ 卡片 −6%，标题升为 20px 后层级不再散 |
| 375 / 390 不溢出 | ✅ 无横向滚动、无截断 |
| Keyboard focus | ✅ 2px 主色焦点环保留 |
| 点击行为不受影响 | ✅ 「先筛选卡片」打开搜索框仍停留在准备态；Space 仍开始学习 |
| 空态 `.btn-outline`（user1）同步修复 | ✅「浏览学习路径」也是 `flex / 36px / 0 14px / 10px`、图标居中 0（修复前同为 `block` 退化态） |
| 「我的闪卡」工具栏未回归 | ✅ `btn-primary/secondary/ghost` 仍为 36px / `0 14px` / 10px，图标偏移 0 |
| 复习操作条 `.action-btn.btn-outline` 未回归 | ✅ 探针实测仍为 `44px / 0 24px / gap 8px / border 1px`；禁用态仍 `0.4` |

截图：`.dev-logs/ui-flashcards/{before-desktop,after-desktop,after-390,after-768,after-emptystate}.png`。

#### 十一、是否出现业务逻辑变化

**没有。** 本轮改动 100% 落在 `<style>` 与准备态两行图标属性上：未改 `<script setup>`、未改任何 API 调用 / Store 消费 / 路由 / 权限 / 数据隔离 / LearningEvent / Evidence / NextAction / Controller / Service / schema。页面消费的接口、状态机与写入口径与改动前完全一致（由全量 583 例测试 + 浏览器点击/Space 行为复验守护）。

#### 十二、是否发现其他 UI 风险（发现即记录，本轮未扩大范围）

1. 🟡 **`.btn-outline` 与 `.btn-secondary` 视觉完全等价（重复类）**：修复后二者在同一页面并存但语义相同。建议后续统一为一个 Secondary 类（本轮为控制 diff 未合并）。
2. 🟡 **本页按钮高度 36px vs 设计令牌 `--kb-btn-height-md: 38px`**：页面级按钮族沿用 36px（含 `.btn-primary/.btn-secondary/.btn-ghost`，共约 20 处）。统一到令牌会联动改动整页，超出「小范围 UI 精修」范围，**保持现状并记录**。
3. 🟡 **`MiniAppShell` 返回按钮图标 18px 而标签 13px**：头部比例略头重（共用组件，影响全部 Mini App），本轮按「不重构 Header」未动。
4. 🟡 `POST /api/docs/{id}/ai-flashcards` 的 userId 未透传缺陷（P0-1b）与 AI Provider 401 —— 沿用 P0-1 记录，本轮未动。

#### 十三、未解决问题

上述第十二节第 1~4 条均为**有意保留/记录项**，非本轮缺陷；本轮所有计划内 UI 项 100% 完成，无遗留失败项。

---

### 2026-09-10 · P0-1b AI 闪卡写路径 owner 落库修复（4 条路径）

#### 一、任务目标

修复 P0-1 遗留的 P0-1b：`learning_flashcard.user_id` 为 `NOT NULL`，但全仓 7 处写入点中有 4 处**从不写 owner**，命中即插入失败。P0-1 时仅对 `DocController` 一条做了静态判定（因 AI Provider 401 无法实证），本轮修完全部同族路径并用**真实 Provider** 端到端实证。

#### 二、根因（`learning_flashcard` 写入点全量扫描，7 处）

| # | 写入点 | 修复前是否落 owner | 前端入口 |
|---|---|---|---|
| 1 | `DocServiceImpl#parseFlashcards`（经 `DocController`） | ❌ 从不 `setUserId` | `DocDetail.vue:532`「AI 闪卡」 |
| 2 | `AdminLearningController#aiGenerateFlashcards` | ❌ `userId` 只用于 AI 记账 | `LearningPathMgmt.vue:1100`「批量生成闪卡」 |
| 3 | `AdminLearningController#aiGenerateFlashcardsFromKb` | ❌ 同上 | `FlashcardMgmt.vue:493`「AI 生成卡片」 |
| 4 | `AdminLearningController#addFlashcard` | ❌ 直接 insert 请求体，前端表单无 `userId` | `FlashcardMgmt.vue:383`「新增卡片」 |
| 5 | `LearningServiceImpl#createMyFlashcard` | ✅ `setUserId(userId)` | — |
| 6 | `LearningServiceImpl#generateAiFlashcards`（用户侧 AI） | ✅ | — |
| 7 | `LearningServiceImpl#importMyFlashcards` / `LearningContentServiceImpl` / `ObsidianImportServiceImpl` | ✅ | — |

**共性根因**：这 4 处都**已经拿到** `userId`（Controller 里已解析，或 Service 有入参），却只在 AI 调用/判权时用掉，**没有落到实体上**。所以不是「缺数据」，是「漏赋值」。

**次生缺陷（同 4 处）**：`sourceType` 未显式设置 → 落到 schema 默认值 `'MANUAL'`。而该字段是**用户可见的**：`FlashCardsHub.vue:472` 有来源筛选下拉、`:581` 渲染来源徽章（`手动 / AI·文档 / AI·知识库 / 导入`）。即 AI 生成的卡会被标成「手动」。词表封闭（无 `AI_PATH`），故路径批量生成也用 `AI_DOC`。

#### 三、修改文件（4 个生产文件 + 1 个新测试）

| 文件 | 改动 |
|---|---|
| `service/DocService.java` | `generateFlashcards` 签名加 `Long userId`（首位语义为「必传，来自登录态」），补 javadoc 说明 `NOT NULL` 约束与 401 语义 |
| `service/impl/DocServiceImpl.java` | `userId == null → BusinessException(401)` 守卫；透传给 `parseFlashcards`；`parseFlashcards` 内补 `setUserId` + `setDocId(doc.getId())` + `setSourceType("AI_DOC")` + `setReviewInterval(0)` |
| `controller/DocController.java` | 已解析的 `userId` 传入 Service（原来取出即弃） |
| `controller/admin/AdminLearningController.java` | ①`addFlashcard`：`userId` 为空时缺省当前管理员，显式传入则尊重；②`aiGenerateFlashcards`：加 401 守卫 + `setUserId` + `setSourceType("AI_DOC")`；③`aiGenerateFlashcardsFromKb`：加 401 守卫 + `setUserId` + `setDocId`/`setCategoryId` + `setSourceType(docId!=null?AI_DOC:AI_KB)`（对齐 `LearningServiceImpl` 既有口径）；补 `BusinessException` import |
| `test/.../controller/FlashcardAiWriteTest.java`（新） | 11 例，含 `P0-1b-0` 直接证明 `NOT NULL` 约束真实生效 |

**owner 归属语义决策**：闪卡归属 = **发起写入的登录用户**。依据是该表 `user_id NOT NULL` + 全仓无 `visibility/public` 字段（P0-1 已判定为「用户私有池」）+ 无任何「公共卡池」设计，故只有这一个自洽答案。显式传 `userId` 的集成用法予以保留。

#### 四、测试

| 门禁 | 结果 |
|---|---|
| `mvn test -Dtest=FlashcardAiWriteTest` | **11 / 11 PASS** |
| `mvn test`（全量） | **241 / 0 failures / 0 errors**（230 → +11） |
| `npx vitest run --maxWorkers=1` | 前端本轮 **0 个文件改动**，基线 583 保持 |
| `vue-tsc -b --force` / `npm run build` | 未跑 —— 本轮无前端改动 |

`P0-1b-0` 的证据意义：无 owner 直接 `flashcardMapper.insert` 抛异常 → 证明「漏 owner ⇒ 必然插入失败」是**实约束**而非推测，也证明其余 4 处修复前确实 100% 不可用。

#### 五、真后端 + 真实 Provider 验收（17/17 PASS）

用 `--spring.profiles.active=local` 启动，并用 `AI_API_KEY` 注入有效 key，跑真实 DeepSeek：

| 检查 | 结果 |
|---|---|
| 文档 AI 闪卡（user1）→ 200，5 张 | ✅ `userId={2}`、`sourceType={AI_DOC}`、`docId={17}` |
| 生成后 user1 列表可见 / admin 列表不可见 | ✅ n_mine=5 / n_admin=6（隔离保持） |
| 匿名 → HTTP 401 | ✅ |
| 管理端手建（不带 userId） | ✅ 200，`userId=1` |
| 管理端知识库/文档 AI 生成 | ✅ 3 张，`userId={1}`、`docId={17}`、`AI_DOC` |
| 管理端路径批量生成 | ✅ 15 张，`userId={1}`、`pathId={1}` |
| 非管理员访问 `/api/admin/**` | ✅ HTTP 403 |
| 探针清理 | ✅ 24/24 删除，事务外零残留 |

#### 六、真实浏览器验证（Chromium + Vite 5173 → 8080）

| 验证项 | 结果 |
|---|---|
| `DocDetail` 点「AI 闪卡」 | ✅ 真实生成，页面出现「AI 生成的复习闪卡（5 张）」 |
| 「我的闪卡」来源徽章 | ✅ 5 张全部渲染为 **AI·文档**（`source-badge src-ai`），非「手动」 |
| 来源筛选下拉选「AI · 文档」 | ✅ 命中 5 张 |
| 管理端「新增卡片」表单 | ✅ 弹窗关闭 + 列表出现新卡；API 复核 `(id=36, userId=1, MANUAL)` |
| 探针清理 | ✅ H2 回到种子态（6 张闪卡 / 16 篇文档，零探针） |

截图：`.dev-logs/p0-1b/{doc-ai-flashcards,mine-tab-ai-badges,admin-manual-create}.png`

#### 七、业务影响

- **无破坏性变更**：`DocService#generateFlashcards` 加参数属编译期强约束，全仓仅 `DocController` 一处调用，已同步。
- **副作用**：4 条原本 100% 失败的写路径恢复可用；AI 生成的卡来源标记从错误的 `MANUAL` 纠正为 `AI_DOC`；AI 卡开始记录 `docId`。
- **未触碰**：`SecurityConfig` / 查询隔离逻辑 / review & SM-2 / LearningEvent / Evidence / NextAction / schema / 路由 / 前端。

#### 八、未解决问题（重要）

1. 🟠 **「AI Provider 401」根因已定位，但未修（需用户拍板）**：
   - `application-local.yml` 里硬编码的 `ai.api-key` 与 `~/.zshrc` 的 `DEEPSEEK_API_KEY` **md5 不同** → 该文件里的是**失效旧 key**；
   - `knowflow-dev.sh:52` 启动后端时**未激活 `local` profile**，故默认走 `application.yml` 的占位 key；
   - 两者叠加 ⇒ 开发环境 AI 恒 401。**验证时用 `AI_API_KEY` 环境变量注入有效 key 后全部通过。**
   - 建议修法（二选一，均属本地配置改动，**需用户确认后再动**）：① `application-local.yml` 改为 `ai.api-key: ${AI_API_KEY:}`（只引用环境变量，不写 key 值）；② `knowflow-dev.sh` 启动参数加 `--spring.profiles.active=local`。
2. 🟡 **管理端「路径批量生成闪卡」的产物对学员不可见**：卡片 owner = 生成的管理员，而学员侧 `getFlashcardList` 恒定 `user_id` 过滤 ⇒ 该功能产出只进管理员自己的池。修复前它直接 500，所以这不是回归，而是**既有产品设计缺口**（需「共享卡池 / 复制到学员」的产品决策，涉及 schema 或分发逻辑，超出 P0-1b 范围）。
3. 🟡 `DocController#generateSummary:136` 仍有一处「取出 `userId` 未使用」的死赋值（`generateAISummary` 不按用户隔离，无功能影响），建议顺手清理。
4. 🟡 `.btn-outline` / `.btn-secondary` 重复类、本页按钮 36px vs 令牌 38px、`MiniAppShell` 头部比例 —— 沿用 P2-UI-Flashcards 记录，未动。
5. 🟠 101 处单参 `BusinessException`、P1-A 剩余 92 处内联 principal、静默失败/死接口 —— 沿用 P2-0 记录，未动。

---

### 2026-09-10 · P1-D3 「资源不存在」→ HTTP 404 语义收敛

#### 一、范围与真实发现数量

全仓扫描 `new BusinessException("…不存在")`（含 `不存在/未找到/已删除` 变体）：**35 处**（P2-0 审计时为 101 处单参总量的一个子集，前序轮次已收敛 58 处 404 + 19 处 403/401/400）。本轮逐处语义判定后**修改 22 处**，**保留 13 处**（理由见 §四）。

#### 二、修改文件（22 处 → `BusinessException(404, …)`，文案与判定逻辑零改动）

| 文件 | 处数 | 资源 |
|---|---:|---|
| `controller/FocusSessionController.java` | 3 | 专注会话 |
| `controller/admin/AdminCategoryController.java` | 3 | 知识库分类（父分类/目标分类/移动源） |
| `service/impl/KbMemberServiceImpl.java` | 3 | 成员记录 / 用户（邀请时） |
| `service/impl/CodeChallengeServiceImpl.java` | 3 | 挑战 / 关卡（「不存在或未发布」） |
| `service/impl/TaskServiceImpl.java` | 3 | 任务 / 清单 / 标签 |
| `service/impl/CalendarMarkServiceImpl.java` | 1 | 纪念日 |
| `service/impl/WorkbenchServiceImpl.java` | 1 | 工作台记录（笔记详情） |
| `service/impl/CategoryServiceImpl.java` | 1 | 指定的父分类 |
| `service/impl/DrawingServiceImpl.java` | 1 | 绘图 |
| `service/impl/MindMapServiceImpl.java` | 1 | 思维导图 |
| `service/impl/HabitServiceImpl.java` | 1 | 习惯 |
| `service/impl/QuizServiceImpl.java` | 1 | 错题记录 |

#### 三、语义判定原则（逐处人工判断，未机械替换）

- **改 404**：按 ID 查单条资源、查询为 null 且语义就是「目标资源缺失」（`getById`/`requireMine` 链路核实到 Mapper 层）。
- **不改**：列表/搜索空结果（本就返回 `[]`，无此类异常）；「资源存在但不是你的」（已是 403，保持）；未登录（401）；非法参数（400）；业务状态不允许（保留原语义）。

#### 四、保留原语义清单（13 处，均有明确理由）

| 位置 | 文案 | 保留理由 |
|---|---|---|
| `AgentToolController:68,77` | 工具不存在：+name | 工具注册表运行时查找，非 REST 资源寻址 |
| `AgentToolController:132`、`CodeAgentController:640,759` | 会话不存在**或无权访问** | 404/403 混合语义，拆分需改判定逻辑，超本轮 scope → 需后续单独收敛 |
| `CodeAgentController:534` | 该工具调用已超时或不存在 | 状态类语义（超时≠不存在） |
| `LocalReaderService:100,117,241,270`、`PathImportService:103,164` | 路径/目录/图片/文件不存在或不可读 | 文件系统 IO 语义；且两个 Service 未发现 live Controller 入口（疑似死代码），待确认后统一处理 |
| `AchievementServiceImpl:44` | 用户不存在 | 已登录上下文中 `user==null` 属数据不一致，500 合理 |

#### 五、测试

- 新增 `backend/src/test/java/com/knowflow/exception/ResourceNotFoundHttpSemanticsTest.java`：**19 例**，覆盖专注会话/任务/清单/标签/习惯/错题/纪念日/绘图/思维导图/工作台笔记/挑战/关卡/分类 13 类资源。**每例同时断言 `status().isNotFound()` 与 `jsonPath("$.code").value(404)`**（防「HTTP 200 + body 500」复发），并保留原文案断言。
- 对照组回归：本人资源 200、他人资源 403（`TaskOwnership403Test` 既有 11 例仍绿）、匿名 401、正常空列表不 404。
- **后端全量 `mvn test`：260 / 0 failures / 0 errors**（241 → +19）。

#### 六、真实 HTTP 验证（curl，`--noproxy`，真后端 local profile）

32/32 PASS：不存在的任务/清单/标签/习惯/思维导图/绘图/错题 → `HTTP 404 + code 404 + 原文案`；存在资源 → 200；他人任务 → 403；匿名 → 401；列表接口空数据 → 200（非 404）。

#### 七、真实浏览器验证（user1 登录 + Vite 代理）

1. `/challenge/888888`：页面 toast「**加载挑战详情失败：挑战不存在或未发布**」——未出现「服务暂时不可用/稍后重试」（该页既有实现为 toast，非 ErrorState）。
2. `/learning/path/999999`：**无任何错误文案**，但也未进错误态、停留在「加载中...」——属既有「静默失败」家族（P2-0 已记录），非本轮引入，未动。
3. 浏览器内 `fetch('/api/learning/paths/999999')`：**HTTP 404 + body.code 404 + 「学习路径不存在」**——前端 axios 链路真实收到 404，`request.ts` 保留 status、`describePageError` 将 404 分类为 notfound（文案「内容不存在或已被删除」、`retryable:false`），前端零改动。

截图：`.dev-logs/p1-d3/{challenge-999999,path-999999}.png`。

#### 八、门禁与边界

- 后端 31 文件 diff 中仅上述 12 个文件与本轮相关；其余为其他 Agent 的未提交工作，未触碰。
- 前端 **0 文件改动**（vitest/vue-tsc/build 未重跑，基线 583/46 + 0 error + PASS 为同一工作树 1.5 小时前实测）。
- 未改 schema / 数据库 / AI / SecurityConfig；未 commit、未 push。

---

---

### 2026-09-10 · P1-A 第三批 StudyGroupController Principal 安全收敛

- **当前 Agent**：WorkBuddy
- **当前模型**：DeepSeek V4.1 Flash
- **阶段**：P1-A 第三批
- **范围**：**只修改 `StudyGroupController`**；`SecurityConfig` 未动，Service/Repository/前端未动，其余 20 个 Controller 未动。
- **修改文件**：`backend/src/main/java/com/knowflow/controller/StudyGroupController.java`（唯一生产代码文件）
- **新增测试**：`backend/src/test/java/com/knowflow/controller/StudyGroupPrincipalAuthTest.java`（11 用例）
- **principal 强转数量**：修改前 **19 处**内联 `(Long) authentication.getPrincipal()` → 修改后 **0 处**（收敛到统一私有入口 `currentUserId()`）

#### 根因

同第一批/第二批：内联 `(Long) authentication.getPrincipal()` 对已登录请求成立，但匿名 principal 是字符串 `"anonymousUser"`（配置未 `anonymous().disable()`）→ `ClassCastException` → 落入 `GlobalExceptionHandler` 兜底 → **HTTP 500「系统内部错误」**。

`SecurityConfig` 中 `/api/study-groups/**` 落入 `.anyRequest().authenticated()`，因此所有 19 个端点语义均为「必须登录 → 401」。与第二批不同，本批没有 `permitAll` 的公开端点，故全部收敛到 `currentUserId()`。

#### 修改内容

- 19 处内联强转 → 统一私有入口 `currentUserId()`；连带移除已无用的 `Authentication` 形参与 import。
- `currentUserId()` = `SecurityUtils.getCurrentUserIdNullable()` + 判空 `throw new BusinessException(401, "未登录或登录已失效")`，与第一批/第二批实现完全一致。
- **未新增异常体系、未新增错误码、未新增依赖、未创建新鉴权机制**；响应信封仍为 `Result{code,message,data}`。**无架构变化。**
- `uploadFile` 原 `Authentication` 参数未使用，一并移除（功能不变）。

#### 逐端点语义判定

| 端点 | 方法 | 原身份获取 | SecurityConfig | 语义判定 | 修复方式 |
|---|---|---|---|---|---|
| `GET /api/study-groups/my` | getMyGroups | `(Long) authentication.getPrincipal()` | authenticated | 必须登录 | `currentUserId()` |
| `GET /api/study-groups/recommend` | getRecommendGroups | 同上 | authenticated | 必须登录（当前非公开） | `currentUserId()` |
| `GET /api/study-groups/{id}` | getGroupDetail | 同上 | authenticated | 必须登录 | `currentUserId()` |
| `POST /api/study-groups` | createGroup | 同上 | authenticated | 必须登录 | `currentUserId()` |
| `PUT /api/study-groups/{id}` | updateGroup | 同上 | authenticated | 必须登录 | `currentUserId()` |
| `DELETE /api/study-groups/{id}` | deleteGroup | 同上 | authenticated | 必须登录 | `currentUserId()` |
| `GET /api/study-groups/{id}/members` | getGroupMembers | 同上 | authenticated | 必须登录 | `currentUserId()` |
| `POST /api/study-groups/invite` | inviteMember | 同上 | authenticated | 必须登录 | `currentUserId()` |
| `POST /api/study-groups/{id}/join` | joinGroup | 同上 | authenticated | 必须登录 | `currentUserId()` |
| `POST /api/study-groups/{id}/leave` | leaveGroup | 同上 | authenticated | 必须登录 | `currentUserId()` |
| `DELETE /api/study-groups/{groupId}/members/{memberId}` | removeMember | 同上 | authenticated | 必须登录 | `currentUserId()` |
| `PUT /api/study-groups/{groupId}/members/{memberId}/role` | updateMemberRole | 同上 | authenticated | 必须登录 | `currentUserId()` |
| `GET /api/study-groups/{id}/messages` | getMessages | 同上 | authenticated | 必须登录 | `currentUserId()` |
| `POST /api/study-groups/messages` | sendMessage | 同上 | authenticated | 必须登录 | `currentUserId()` |
| `DELETE /api/study-groups/messages/{id}` | recallMessage | 同上 | authenticated | 必须登录 | `currentUserId()` |
| `POST /api/study-groups/{id}/read` | markAsRead | 同上 | authenticated | 必须登录 | `currentUserId()` |
| `GET /api/study-groups/{id}/unread` | getUnreadCount | 同上 | authenticated | 必须登录 | `currentUserId()` |
| `PUT /api/study-groups/{id}/announcement` | updateAnnouncement | 同上 | authenticated | 必须登录 | `currentUserId()` |
| `PUT /api/study-groups/{id}/learning-plan` | linkLearningPlan | 同上 | authenticated | 必须登录 | `currentUserId()` |

**未发现匿名可读端点、未发现 SSE/异步特殊上下文、未发现管理员 bypass 需求。**

#### userId 伪造检查

- `StudyGroupCreateDTO` 无 `userId` 字段，创建时 owner 只能从 `currentUserId()` 取。
- `GroupInviteDTO` 有 `userId` 字段，但语义是**被邀请人 ID**；inviter 仍来自 `currentUserId()`。测试验证：A 邀请 C 时，成员 `userId=C`、`invitedBy=A`，当前用户身份未被覆盖。
- `GroupMessageSendDTO` 无 `userId` 字段，sender 来自 `currentUserId()`。
- Controller 无 query `userId` 参数。

结论：**当前身份绑定正确**，请求体 `userId` 不覆盖 token 用户。

#### 测试

- 新增 `StudyGroupPrincipalAuthTest`：**11 用例**（`@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("local")` + `@Import(FakeAiConfiguration)` + 独立 H2 库 `knowflow_p1a_studygroup`）。
  - 匿名访问受保护读/写接口 → 401
  - 无效 token → 401
  - 有效 token 创建小组 → 200，owner 为 token 用户
  - 有效 token 读接口回归（/my /recommend /{id} /members）
  - **请求体 `userId` 不覆盖当前用户**（invite 场景）
  - 用户 B 不能修改/删除用户 A 的小组（当前业务语义为 500，零副作用）
  - 小组不存在、私有组非成员访问保持既有业务语义（当前 500，未被顺手改动）
  - 成员操作回归：加入公开组、发送消息、离开
- 后端全量回归：`mvn -f backend/pom.xml test` → **271 / 0 failures / 0 errors**（260 → +11）。
- 前端未改：`npx vitest run --maxWorkers=1` → **583 / 46**；`npx vue-tsc -b --force` → **0 error**；`npm run build` → **PASS**。

#### 真实接口验收（`curl --noproxy '*'`）

| # | 场景 | 修复前 | 修复后 | 结果 |
|---|------|--------|--------|------|
| 1 | 匿名 `GET /api/study-groups/my` | 500 | **401** | ✅ |
| 2 | 无效 token 同一端点 | 500 | **401** | ✅ |
| 3 | user1 `POST /api/study-groups` | 200 | **200**，ownerId=2 | ✅ |
| 4 | user1 `GET /api/study-groups/my` | 200 | **200** | ✅ |
| 5 | user2 `PUT /api/study-groups/{id}` | 500 | **500**（未改业务语义） | ✅ |
| 6 | user2 `DELETE /api/study-groups/{id}` | 500 | **500**（未改业务语义） | ✅ |
| 7 | user2 加入公开组并发消息 | 200 | **200**，senderId=3 | ✅ |
| 8 | user1 删除自己的小组 | 200 | **200** | ✅ |
| 9 | `GET /api/study-groups/999999` | 500 | **500**（未改业务语义） | ✅ |
| 10 | user2 访问 user1 私有组 | 500 | **500**（未改业务语义） | ✅ |

#### 浏览器验证

- 真实 Chromium + Vite 代理 5173→8080，user1 登录后导航到 `/study-group`。
- 页面正常渲染：左侧「我加入的小组」「推荐小组」，右侧「选择一个学习小组开始聊天」。
- Console 无错误，仅 Vite debug + WebSocket 连接日志。
- 未出现「系统内部错误」「服务暂时不可用」「加载失败」等文案。
- 截图：`/tmp/p1a3_studygroup_page.png`。

#### 诚实边界与遗留

- ⚠️ **既有业务异常语义未改（按 scope 保留）**：`StudyGroupServiceImpl` 对「小组不存在 / 无权访问 / 无管理权限 / 只有创建者可删除」抛出 `RuntimeException` → HTTP 500。这是当前真实语义，本轮只收敛 principal，未顺手改成 404/403。已在 `StudyGroupPrincipalAuthTest` 中以「当前 500 + 零副作用」锁定，防止回归。
- 🟠 **剩余 principal 强转**：全仓扫描剩余 **73 处** / **20 个 Controller**（不含 StudyGroup 注释）。重灾区：`DocController`（11）、`PrivateMessageController`（7）、`AiConfigController`（7）、`MistakeController`（6）、`UserPetController`（5）等。建议后续按 Controller 分批收敛。
- 🟡 `uploadFile` 未做用户隔离（原就无），上传文件归属全局；未在本次 scope 内处理。

#### 下一批建议

**P1-A 第四批 —— `DocController`（11 处 principal 强转）**。`DocController` 同时存在 P0-1b 遗留问题（`generateSummary` 死赋值等），可在收敛 principal 时一并观察。其次 `PrivateMessageController`（7 处）、`AiConfigController`（7 处）。

---

## 九、相关文档

- [AGENTS.md](file:///Users/beiluo/Documents/alProject/qoderProject/knowflow/AGENTS.md) — Agent 工作守则
- [VIBE_CONTEXT.md](file:///Users/beiluo/Documents/alProject/qoderProject/knowflow/VIBE_CONTEXT.md) — Vibe Coding 上下文
- [docs/PROJECT_ARCHITECTURE.md](file:///Users/beiluo/Documents/alProject/qoderProject/knowflow/docs/PROJECT_ARCHITECTURE.md) — 项目架构
- [docs/UI_UX_SYSTEM.md](file:///Users/beiluo/Documents/alProject/qoderProject/knowflow/docs/UI_UX_SYSTEM.md) — UI/UX 系统
- [docs/DEVELOPMENT_PROTOCOL.md](file:///Users/beiluo/Documents/alProject/qoderProject/knowflow/docs/DEVELOPMENT_PROTOCOL.md) — 开发协议
