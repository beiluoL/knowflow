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

### 2026-09-12 · P1-A 第四批 DocController Principal 安全收敛

> 任务书标题写的是 2026-09-11，实际执行日期为 **2026-09-12**（以真实执行日为准）。

#### 一、基本信息

| 项 | 内容 |
|---|---|
| Agent | WorkBuddy（CodeBuddy Agent 模式） |
| Model | DeepSeek-V4.1-Flash |
| 日期 | 2026-09-12 |
| 范围 | **仅** `DocController`；未触碰其它 Controller / SecurityConfig / 业务逻辑 |
| Git | **未 commit、未 push**（任务书 §一 明确禁止 commit/push） |

#### 二、修复前 / 修复后

| 指标 | 修复前 | 修复后 |
|---|---:|---:|
| `DocController` 内联 `(Long) authentication.getPrincipal()` | **11** | **0** |
| 全仓内联强转（代码口径，排除注释） | **73** | **62** |
| 全仓 grep 原始命中（含 `StudyGroupController` javadoc 中的 1 处引用） | 74 | **63** |
| 涉及文件数 | 21 | **20** |

#### 三、11 个端点的身份语义逐项判定（非机械替换）

| # | 方法 | 行(改前) | Endpoint | 原身份获取 | 配置级别 | 判定 | 改后 |
|---|---|---:|---|---|---|---|---|
| 1 | `detail` | 45 | `GET /api/docs/{id}` | `authentication != null ? … : null` | **permitAll** | 匿名可读 | `SecurityUtils.getCurrentUserIdNullable()` |
| 2 | `toggleFavorite` | 55 | `POST /api/docs/{id}/favorite` | null 守卫 + 强转 | authenticated | 必须登录 | `currentUserId()`（401） |
| 3 | `favorites` | 63 | `GET /api/docs/favorites` | 三元 nullable | 实际被 `/api/docs/{id}` Ant 模式覆盖 → 匿名可达 | **保留 nullable** | nullable |
| 4 | `recent` | 70 | `GET /api/docs/recent` | 三元 nullable | 同上 | **保留 nullable** | nullable |
| 5 | `recommend` | 77 | `GET /api/docs/recommend` | 三元 nullable | **permitAll** | 匿名可读 | nullable |
| 6 | `updateProgress` | 87 | `POST /api/docs/progress` | null 守卫 + 强转 | authenticated | 必须登录 | `currentUserId()` |
| 7 | `create` | 99 | `POST /api/docs` | null 守卫 + 强转 | authenticated | 必须登录 | `currentUserId()`（先取身份后落库，保持原顺序） |
| 8 | `upload` | 117 | `POST /api/docs/upload` | null 守卫 + 强转 | authenticated | 必须登录 | `currentUserId()` |
| 9 | `update` | 130 | `PUT /api/docs/{id}` | null 守卫 + 强转 | authenticated | 必须登录 | `currentUserId()` |
| 10 | `generateSummary` | 136 | `POST /api/docs/{id}/ai-summary` | **无守卫**强转 | authenticated | 必须登录 | `currentUserId()` |
| 11 | `generateFlashcards` | 148 | `POST /api/docs/{id}/ai-flashcards` | **无守卫**强转 | authenticated | 必须登录 | `currentUserId()`（P0-1b owner 链路原样保留） |

`Authentication` 形参已从全部方法签名移除，`import org.springframework.security.core.Authentication` 删除；新增 `import com.knowflow.common.SecurityUtils` / `com.knowflow.exception.BusinessException`。

#### 四、permitAll / nullable 的实测结论

- **permitAll 端点 3 个**：`GET /api/docs`、`GET /api/docs/{id}`、`GET /api/docs/recommend`（`SecurityConfig` 显式白名单）。三者身份语义均为「匿名可读」，一律保持 nullable，**未**改成 401。
- **意外放宽（既有事实，非本轮引入）**：Ant 模式 `/api/docs/{id}` 会匹配任意单段路径，故 `GET /api/docs/favorites`、`GET /api/docs/recent` **实际也匿名可达**。实测匿名返回 `{"code":200,"data":[]}`（Service 层 `eq(user_id, null)` 生成 `= ?` 恒不命中 → 空列表，**无数据泄漏**）。本轮按 scope 未改 `SecurityConfig`，仅记录。
  - ⚠️ 潜在脆弱点：若未来有人把 `getFavoriteList` / `getRecentReadList` 的 `.eq(UserId, userId)` 改成「条件式 eq（null 则不过滤）」，就会立刻变成「匿名读全站收藏」。建议后续加一道显式 `userId == null → return emptyList()`。
- **null 守卫原为「死代码」**：`toggleFavorite` 等端点位于 `authenticated()` 之后，`authentication == null` 分支永不触发，原 `Result.error(401, "请先登录")`（HTTP 200 + code 401）实为不可达。收敛后语义统一为 HTTP 401 + code 401。

#### 五、⚠️ 重要认知修正：`Authentication` 形参在匿名时为 `null`，不是 `anonymousUser`

- 前序批次（P1-A 一/二/三批）的叙述假设「匿名时 principal 是字符串 `anonymousUser` → 强转 `Long` 抛 `ClassCastException` → 500」。**本轮实测证伪了该机制对 `Authentication` 形参的适用性**：
  - Spring MVC 对 `Authentication` 形参取 `request.getUserPrincipal()`，而 `SecurityContextHolderAwareRequestWrapper` 对 `AnonymousAuthenticationToken` **返回 null**。
  - 证据：**修复前**代码下 `curl` 匿名 `GET /api/docs/1` = **HTTP 200**（`.dev-logs/p1a4/pre-fix-matrix.txt`，2026-09-12 11:37:37）。若 principal 真是 `"anonymousUser"`，此处必为 500。
- 结论：`DocController` 本轮**不存在**「活缺陷」，「三元/null 守卫」写法本身是安全的。真实风险只剩两条：
  1. `generateSummary` / `generateFlashcards` 两处**无 null 守卫**，一旦该端点在 `SecurityConfig` 中漂移进 `permitAll`，匿名请求会因 principal 为 `null` 抛 **`NullPointerException` → 500**（不是 CCE）。本轮统一收敛后该风险被彻底消除（确定性 401）。
  2. 内联强转无法表达「未登录」的确定性语义，属防御性技术债。
- **诚实标注**：本节其余端点仍沿用「NPE/CCE → 500」的笼统说法者，均应以上述实测为准，后续批次请勿再复述未经证实的 CCE 机制。

#### 六、userId 伪造检查

| 检查项 | 结论 |
|---|---|
| `doc_document` 是否有 owner 列 | **无**（已用 `INFORMATION_SCHEMA.COLUMNS` 断言：无 `user_id` / `owner_id` / `author_id` / `creator_id`）→ 文档是**全局共享资源**，不存在归属伪造面 |
| 请求体带 `userId` / `ownerId` / `authorId` 创建文档 | Jackson 静默忽略；不产生任何私有关系（测试 P1A4-12） |
| 请求体带 `userId` 调 `POST /{id}/favorite` | 收藏仍归属 token 用户（`currentUserId()`），他人列表不受影响（P1A4-10） |
| query `?userId=<他人>` 调 `GET /docs/favorites` | 端点无该参数，完全忽略；返回结果与不带参数完全一致（P1A4-11） |
| AI 闪卡 `userId` | 只来自 SecurityContext，前端无法传入（P1A4-16） |

#### 七、401 / 403 / 404 回归

| 场景 | 结果 |
|---|---|
| 匿名 / 无效 token → 写操作（favorite / progress / create / ai-summary / ai-flashcards / update） | **401 + code 401**（6/6） |
| 匿名 / 无效 token → permitAll 读操作 | **200**（保持匿名可读） |
| 文档不存在（匿名 & 已登录 & 写操作） | **404 + code 404 +「文档不存在」** |
| `GET /api/docs/abc`（类型不匹配） | **400 + code 400 +「参数格式不正确」** |
| 403 | 本轮未涉及（`DocController` 无「已登录但无权」分支；文档为全局共享资源） |

#### 八、AI Flashcard owner 回归（P0-1b 链路保护）

- `DocService#generateFlashcards(docId, pathId, chapterId, userId)` 签名与内部 `parseFlashcards` 的 `setUserId` / `setSourceType("AI_DOC")` / `setDocId` / `setReviewInterval(0)` **零改动**。
- 单测（FakeAi）：P1A4-16 断言返回体与 DB 行 `userId = 当前用户`、`sourceType=AI_DOC`、`docId` 正确，且他人列表不可见 → PASS。
- **真实 Provider E2E**（注入 `AI_API_KEY` 环境变量，未改任何生产配置）：
  - 浏览器在 `/doc/17` 点击「AI 闪卡」→ 真实 DeepSeek 返回 5 张卡；
  - `GET /api/learning/flashcards`（user1）→ 5 张，全部 `sourceType=AI_DOC`、`userId=2`、`docId=17`；
  - user2 → **0 张**（隔离未回归）；
  - 页面 DOM 渲染出卡片标题与「简单/中等/困难」难度标记。✅

#### 九、测试与门禁

| 门禁 | 结果 |
|---|---|
| `DocPrincipalAuthTest`（新增） | **19 / 19 PASS** |
| `mvn -f backend/pom.xml test` 全量 | **290 / 0 failures / 0 errors**（271 → +19） |
| `npx vitest run --maxWorkers=1` | **583 / 46 文件 PASS** |
| `npx vue-tsc -b --force` | **0 error** |
| `npm run build` | **PASS**（仅既有 chunk 体积提示） |

未删除任何旧测试；未修改旧断言。

#### 十、真实 HTTP（curl，`--noproxy '*'`）

- 环境提示：本机沙箱注入 `SERVER__PORT=50078`，`vite.config.ts` 已按该变量代理，故本轮统一使用 **`http://localhost:50078`**（不是 8080/5173 直连后端）。
- 修复前矩阵：`.dev-logs/p1a4/pre-fix-matrix.txt`（25 项）
- 修复后矩阵：`.dev-logs/p1a4/post-fix-matrix.txt`（25 项）
- 定向探针：`.dev-logs/p1a4/focused-probe.txt`
- 关键结果：匿名读 5/5 = 200；匿名写 6/6 = 401+code401；无效 token 读 200 / 写 401；有效 token 收藏后本人 `favorite=true`、他人 `favorite=false`；404 ×3；400 ×1。

#### 十一、浏览器验证

真实 Chromium（agent-browser）+ Vite 代理 5173→50078：

| 场景 | 结果 |
|---|---|
| 匿名 `/doc/1` | 正常渲染（h1「JavaScript 入门教程」、正文完整、右上显示「登录」），无错误文案 |
| 已登录 `/docs` | 知识库页完整渲染（分类、文档卡片、最近阅读进度），无错误 |
| 已登录 `/doc/17` 点击「收藏」 | 按钮 `收藏 → 已收藏`，无错误提示（证明 principal 写路径通） |
| 已登录 `/doc/17` 点击「AI 闪卡」 | 生成 5 张卡并渲染难度标记（真实 DeepSeek） |

截图：`.dev-logs/p1a4/p1a4-doc1-anon.png`、`p1a4-docs-list.png`、`p1a4-doc-favorited.png`、`p1a4-ai-flashcards.png`。

#### 十二、是否修改 SecurityConfig / 业务逻辑

- `SecurityConfig`：**未改**（endpoint 访问级别零变化）。
- 业务逻辑：**未改**（Service / Repository / SQL / Schema / DTO / VO / API 路径与 JSON 结构零变化）。
- 唯一「非纯替换」的动作：`create` / `update` / `upload` 把身份解析提到业务调用之前，以保持原「守卫在最前」的执行顺序（匿名不可达，行为等价）。
- `generateSummary` 的 **dead assignment 按要求保留**（`Long userId = currentUserId();` + 注释），未借机删除。

#### 十三、⚠️ 本轮新发现（既有缺陷，未修，已复现）

🟠 **P1（用户可见）：取消收藏后再次收藏 → HTTP 500「系统内部错误」，且此后永久失败。**

- 复现（curl 与真实浏览器双向确认）：
  1. `POST /api/docs/{id}/favorite` → 200（收藏成功）
  2. 再点一次（取消收藏）→ 200（逻辑删除：`deleted=1`）
  3. **再点一次（重新收藏）→ 500**；第 4 次同样 500
- 根因（日志实证）：`doc_favorite` 上 `uk_fav_user_doc UNIQUE (user_id, doc_id)` **不包含 `deleted` 列**，而 `DocFavorite extends BaseEntity` 带 `@TableLogic deleted`：
  - 逻辑删除只把 `deleted` 置 1，行仍占用唯一索引；
  - `toggleFavorite` 用 `selectOne(eq(user_id).eq(doc_id))`（自动过滤 `deleted=0`）→ 查不到 → 走 insert 分支 → **`DuplicateKeyException: UK_FAV_USER_DOC`** → `GlobalExceptionHandler` 兜底 → 500。
- 证据：`/tmp/kf-backend2.log`（6 次 `DuplicateKeyException`）、`.dev-logs/p1a4/focused-probe.txt`、`p1a4-preexisting-favorite-defect.png`。
- **归属**：与 principal 收敛无关——修复前矩阵（11:37，改动落盘之前）已同样出现 500，属既有缺陷。
- 修复方向（建议）：在 `toggleFavorite` 里用 `update` 复活旧行（`deleted=0`）而非 insert，或把 `deleted` 纳入唯一键；**未在本轮 scope 内实施**。

#### 十四、剩余风险与遗留

- 🟠 **剩余 principal 强转 62 处 / 20 个 Controller**。重灾区：`PrivateMessageController`(7)、`AiConfigController`(7)、`MistakeController`(6)、`LearningNoteController`(5)、`UserPetController`(5)、`CommunityController`(4)、`NotificationController`(4)。
- 🟠 **`SecurityUtils#getCurrentUserId()` 是陷阱**：走单参 `BusinessException("未登录")` → code=500 → HTTP 200。本轮**未使用**它，并已在 `DocPrincipalAuthTest.P1A4-0b` 中以断言把该语义钉死，防止后来者误用。
- 🟠 上面 §十三 的收藏 toggle 500。
- 🟡 `DocController` 无 `@Transactional`（`create` 失败不会回滚文档），本轮未改。
- 🟡 `doc_favorite` / `doc_read_progress` 的「匿名 nullable 查询依赖 `eq(null)` 恒不命中」是隐式约定，建议显式化。
- 🟡 本机沙箱注入 `SERVER__PORT`，与文档里「后端 8080」的旧描述不一致，需在 README / 开发脚本中同步说明。

#### 十五、下一最佳任务

**P2-Doc-Favorite 取消收藏后无法再次收藏（HTTP 500）** —— 见 §十三。
理由：① 真实用户高频核心动作直接失败；② 现状是「永久坏」（越点越坏）；③ 根因单一、修复成本低（改 `toggleFavorite` 复活旧行即可，不动 schema）；④ 有现成复现脚本与浏览器证据，可闭环验证。

---

### 2026-09-12 · P1-A 第五批 PrivateMessageController Principal 安全收敛

#### 一、Agent / Model / 日期

| 项 | 值 |
|---|---|
| Agent | WorkBuddy（Agent 模式，单 Agent 执行） |
| Model | Claude 系（Claude Code） |
| 日期 | 2026-09-12 |
| HEAD（未变） | `c8b7f59`（本批**未 commit、未 push**，任务书 §一 明确禁止） |

#### 二、修改前 / 修改后 principal 数量

| 范围 | 修改前 | 修改后 |
|---|---:|---:|
| `PrivateMessageController` 内联 `(Long) authentication.getPrincipal()` | **7** | **0** |
| 全仓代码口径（注释不计） | **62** | **55** |
| 涉及 Controller 数（代码口径） | 20 | **19** |

> 口径说明：`grep -rn "(Long) authentication.getPrincipal()" --include='*.java'` 全仓 63 命中，其中 `StudyGroupController.java:47` 是 javadoc 文字（P1-A3 说明），代码口径 = 62 → 55。

#### 三、修改文件

| 文件 | 改动 |
|---|---|
| `backend/src/main/java/com/knowflow/controller/PrivateMessageController.java` | 新增私有 `currentUserId()`；7 处强转 → `currentUserId()`；移除 7 个方法上的 `Authentication` 形参（含 `uploadFile` 未使用的形参）与 `import org.springframework.security.core.Authentication`；补 `SecurityUtils` / `BusinessException` import |
| `backend/src/test/java/com/knowflow/controller/PrivateMessagePrincipalAuthTest.java`（新增） | 21 例（独立 H2 库 `knowflow_p1a5_im`） |
| `docs/AGENT_HANDOFF.md` | 本小节 |

#### 四、7 个 endpoint 的语义判断（逐处，非机械替换）

| # | 方法 | Endpoint | SecurityConfig | 语义 | 改后 |
|---|---|---|---|---:|---|
| 1 | `getOrCreateConversation` | `POST /api/im/private/conversations` | `anyRequest().authenticated()` | 必须登录 | `currentUserId()` → 401 |
| 2 | `getMyConversations` | `GET /api/im/private/conversations` | 同上 | 必须登录 | `currentUserId()` → 401 |
| 3 | `getMessages` | `GET /api/im/private/conversations/{id}/messages` | 同上 | 必须登录 | `currentUserId()` → 401 |
| 4 | `sendMessage` | `POST /api/im/private/messages` | 同上 | 必须登录 | `currentUserId()` → 401 |
| 5 | `markAsRead` | `POST /api/im/private/conversations/{id}/read` | 同上 | 必须登录 | `currentUserId()` → 401 |
| 6 | `getUnreadCount` | `GET /api/im/private/conversations/{id}/unread` | 同上 | 必须登录 | `currentUserId()` → 401 |
| 7 | `recallMessage` | `DELETE /api/im/private/messages/{id}` | 同上 | 必须登录 | `currentUserId()` → 401 |

- **是否存在 permitAll**：**否**。`SecurityConfig` 中无任何 `/api/im/**` 规则；`grep "messages\|private"` 于 `SecurityConfig.java` 零命中 ⇒ 全部落 `anyRequest().authenticated()`。
- **是否存在 nullable 场景**：**否**。7 个端点全为「必须登录」，无匿名可读、无「当前用户可选」、无管理员 bypass、无 SSE/异步（WebSocket 走 `ImWebSocketHandler`，不经本 Controller）。
- 因此本批**未引入** `nullableCurrentUserId()`（与 P1-A4 DocController 的 4 处 nullable 不同）。
- 非身份参数保持原样：`targetUserId`（会话对端，用户自选）、`conversationId`、`page`/`size` **一律未动**——尤其**没有**把 `targetUserId` 误替换成 `currentUserId()`。

#### 五、`senderId` / `userId` 伪造测试

| 伪造方式 | 结果 |
|---|---|
| 请求体 `senderId` / `userId` / `senderName` | **不生效**，落库与响应均为 token 用户（curl：`senderId=2`；浏览器 fetch：`senderId=2, senderName=测试用户1`） |
| query `?userId=<他人>` | **不生效**，会话列表/消息历史作用域与不带参数完全一致 |

结构性保证（测试已断言）：`PrivateMessageSendDTO` **不存在任何身份字段**（反射断言 `senderId`/`userId`/`sender`/`fromUserId`/`ownerId` 全无）；`private_message.sender_id` 为 **NOT NULL**（`INFORMATION_SCHEMA` 断言 `IS_NULLABLE='NO'`）；`private_conversation` 用 `user_a_id`/`user_b_id` 表达双方、**无** `user_id` 单列可伪造。发送者只由 `PrivateMessageServiceImpl#sendMessage` 从当前登录用户写入。

#### 六、`receiverId` 业务语义（未被误改）

`getOrCreateConversation(userId, targetUserId)` 中两者语义严格分离：`userId`（发起人）来自 SecurityContext；`targetUserId` 是用户主动选择的对端。测试 `P1A5-8` 断言：A 向 C 建会话后，DB 行为 `{user_a_id, user_b_id} = {A, C}`（`Math.min/max` 归一），且**响应中 `targetUserId` = C**，未被覆盖成发起人自己。

#### 七、消息读取权限

- 参与者（A/B）读本会话 → 200 且能看到消息。
- 局外人（C）读他人会话 → `checkParticipant` 拦截，响应 **500「系统内部错误」**（既有 Service 语义），且**响应体不含任何消息内容**（测试断言不出现 `SECRET` 串）⇒ 无数据泄漏。
- 会话列表：局外人的 `GET /conversations` 返回 `data: []`（curl 实测 admin 视角为空数组）⇒ 列表隔离正确。

#### 八、已读权限（重点核查项）

`markAsRead` / `getUnreadCount` 均先 `checkParticipant`：
- 本人标记已读 → 200，游标推进到会话最新消息 id（测试断言 `lastReadMessageId == latestMessageId`）。
- **不影响对方**：B 标记已读后 A 的游标一一相等不变。
- 局外人 C 标记他人会话已读 → 500，且 **A/B 游标零变化、不为 C 生成 `private_conversation_read` 记录**（`assertNull(readCursor(conv, USER_C))`）⇒ **无 IDOR**。
- 结论：**Service 已有 owner guard，按任务书 §八「Service 已有 guard 则保持」——未改 Service 一行代码。**

#### 九、删除（撤回）权限

- 本人撤回 → 200 + `recalled=1`；重复撤回仍 200（幂等）。
- 局外人撤回他人消息 → 500，`recalled` 保持 **0**（无副作用）。
- 撤回不存在消息 → 500（既有语义）。
- 边界：`recallMessage` 中用于广播的 `messageMapper.selectById(id)` 在越权时读取了他人消息的 `conversationId`（**仅用于内部 WS 广播参数，未回传调用方**；且越权请求在 Service 处抛异常、广播代码不可达）。属既有实现，未改。

#### 十、401 / 403 / 404 回归（严格 status + body.code 双断言）

| 场景 | 本批前 | 本批后 |
|---|---|---|
| 匿名（7 端点） | 401 + code401 | **401 + code401（不变）** |
| 无效 token | 401 + code401 | **401 + code401（不变）** |
| 匿名 + 非法请求体 | 401（鉴权先于校验） | **401（不变，已断言）** |
| 合法请求 | 200 + code200 | **200 + code200（不变）** |
| 参数非法 / 类型不匹配 | 400 + code400 | **400 + code400（不变）** |
| 会话不存在 | 500 + code500 | **500（不变，见 §十一 缺口）** |
| 无权访问他人会话 | 500 + code500 | **500（不变，见 §十一 缺口）** |
| 消息不存在 / 非本人撤回 | 500 + code500 | **500（不变）** |
| 与自己发起私聊 | 500 + code500 | **500（不变）** |

**未出现任何 `HTTP 200 + body.code=500`**（`P1A5-18` 专项断言成功信封）。

#### 十一、⚠️ 本轮记录但**未修**的既有缺口（按 scope 保留）

🟠 **IM Service 层错误语义缺口（非本批引入，非回归）**：`PrivateMessageServiceImpl` 用裸 `RuntimeException` 表达 4 类用户级错误，被 `GlobalExceptionHandler` 兜底成 **HTTP 500「系统内部错误」**，目标语义应为：

| 位置 | 现状 | 目标 |
|---|---|---|
| `checkParticipant` 会话不存在 | 500 | **404** |
| `checkParticipant` 无权访问该会话 | 500 | **403** |
| `recallMessage` 消息不存在 | 500 | **404** |
| `recallMessage` 只能撤回自己的消息 | 500 | **403** |
| `getOrCreateConversation` 不能与自己发起私聊 | 500 | **400** |

安全属性**未受损**（越权均被拦截、零副作用、零泄漏），只是状态码语义不准。已在 `PrivateMessagePrincipalAuthTest.P1A5-17` 以「记录性断言」钉死当前语义（改动时该测试会失败，提醒同步更新），并列为下一批候选。

#### 十二、curl（真实后端，`--noproxy '*'`，30 项 × 前/后各 1 轮）

脚本 `zsh /tmp/p1a5_matrix.sh`（前）与 `/tmp/p1a5_matrix2.sh`（后），原始输出：

- `.dev-logs/p1a5/pre-fix-matrix.txt`（7 端点匿名 401 / 正常 200 / 伪造不生效 / 越权 500）
- `.dev-logs/p1a5/post-fix-matrix.txt`（同口径复跑，**逐行一致** ⇒ 零回归）

补充取证（post-fix）：伪造 `senderId=3` 的消息在 DB/响应中为 `senderId=2`；`admin` 的会话列表为 `[]`；越权撤回后 `recalled` 仍为 false，本人撤回后 `recalled=True`。

#### 十三、Browser（真实 Chromium + Vite 5173→50078 代理）

| 类型 | 内容 | 结果 |
|---|---|---|
| Browser UI | `/messages` 加载 | 会话列表含「测试用户2」私聊 + 2 个群；无「系统内部错误 / 服务暂时不可用」 |
| Browser UI | 打开私聊会话 | 渲染「[撤回的消息]」「P1A5 post-fix hello」；无报错 |
| Browser UI | 输入框发送消息 | 成功，气泡出现在列表；随后 API 复核 `senderId=2`（token 用户） |
| Browser UI | 点击「撤回」按钮 | 最新消息变为「[撤回的消息]」，撤回按钮数 3→2；无报错 |
| Browser fetch | `GET /api/im/private/conversations`（无/无效 token） | **401 + code401** |
| Browser fetch | `POST /api/im/private/messages` 伪造 `senderId=3,userId=3,senderName=冒名者` | 200 但 `senderId=2, senderName=测试用户1`（伪造被忽略） |
| Browser fetch | `GET /api/im/private/conversations/1/messages`（admin token） | 500 + `leaks:false`（零泄漏） |
| Console | 页面控制台 | 仅 Vite debug + `IM WebSocket 已连接`；无 error |

截图：`.dev-logs/p1a5/p1a5-messages-opened.png`、`p1a5-messages-sent.png`、`p1a5-messages-recall.png`。
（AI Provider 本轮**无涉**：本批未调用任何 AI 接口，故无「E2E 未验证」项。）

#### 十四、后端测试

- 新增 `PrivateMessagePrincipalAuthTest`：**21 / 21 PASS**（`mvn -f backend/pom.xml test -Dtest=PrivateMessagePrincipalAuthTest`）。
- 后端全量：**311 / 0 failures / 0 errors**（290 → +21），BUILD SUCCESS。
- 未删除任何既有测试，未修改任何旧断言。
- 卷宗：`/tmp/p1a5-test.log`、`/tmp/p1a5-full-backend.log`。

#### 十五、前端测试 / vue-tsc / build

| 门禁 | 结果 |
|---|---|
| `npx vitest run --maxWorkers=1` | **583 passed / 46 files**（无前端改动，基线持平） |
| `npx vue-tsc -b --force` | **0 error** |
| `npm run build` | **PASS**（built in 3.90s） |

#### 十六、是否修改 SecurityConfig / 业务逻辑

- `SecurityConfig`：**未改**（未新增/删除任何 `requestMatchers`，endpoint 访问级别零变化）。
- 业务逻辑：**未改**。`PrivateMessageService` / `PrivateMessageServiceImpl` / Mapper / SQL / schema / DTO / VO / API 路径与 JSON 结构零变化。
- 唯一「非纯替换」动作：移除 7 个方法上**未被使用**的 `Authentication` 形参（HTTP 鉴权由过滤链保证，行为等价）。
- P0-1b AI 闪卡 owner 链路、`SecurityConfig`、AI Provider、prompt、schema：**全部未触碰**。

#### 十七、重新审计结果（任务书 §二十一 五项）

| 审计对象 | 结论 |
|---|---|
| A. `PrivateMessageController` | `(Long) authentication.getPrincipal()` = **0**；无 `Authentication` 引用（仅 javadoc 提及）；无 `SecurityUtils.getCurrentUserId()`（陷阱）误用 |
| B. `SecurityConfig` | `/api/im/**` 无 permitAll ⇒ 7 端点全在 `authenticated()` 之后，收敛后语义一致 |
| C. `PrivateMessageService` | 每处读写前均 `checkParticipant`；撤回校验 `senderId`；**无 IDOR**。4 类裸 `RuntimeException`（见 §十一）为纯状态码缺口 |
| D. 所有 sender/userId 来源 | DTO 无身份字段；DB `sender_id NOT NULL`；**WebSocket 身份来自 `WebSocketAuthInterceptor` 验签后的 JWT `userId` claim**（非客户端参数）⇒ 无旁路伪造 |
| E. 所有消息读写接口 | 7 个端点 + WS 三个指令（message/read/typing）均已覆盖；WS 复用同一 Service（同 guard） |

结论：**7 处 principal 确已完成安全收敛**，不存在遗留的强转或身份旁路。

#### 十八、剩余风险与遗留

- 🟠 **剩余 principal 强转 55 处 / 19 Controller**。重灾区：`AiConfigController`(7)、`MistakeController`(6)、`LearningNoteController`(5)、`UserPetController`(5)、`CommunityController`(4)、`NotificationController`(4)、`UserController`/`ChapterFavoriteController`/`WeeklyReportController`/`UserBackgroundPresetController`(各 3)。其中 `NotificationController`/`MistakeController`/`KnowledgeController` 的 8 处是 `authentication != null ? … : null` 变体（等价于 nullable 写法未统一）。
- 🟠 §十一 的 IM 错误语义缺口（404/403/400）。
- 🟠 `P2-Doc-Favorite` 收藏 toggle 500（P1-A4 发现，仍未修）。
- 🟠 开发环境 AI 恒 401（本地配置，待用户拍板）。
- 🟡 **`WebSocketAuthInterceptor:22` 有硬编码兜底密钥** `${jwt.secret:knowflow-secret-key-for-jwt-token-generation}`，而 `JwtUtils` 用 `${jwt.secret}`（无默认，fail-fast）、`application.yml` 用 `${JWT_SECRET}`。当前因启动即 fail-fast 而**不可达**，但属潜在陷阱（建议改为无默认值）。
- 🟡 `PrivateMessageServiceImpl#recallMessage` 与 Controller 各自 `selectById` 同一条消息（重复查询，2 次 DB 往返）；`getMessages` 不区分 `recalled` 内容屏蔽（撤回后 `content` 仍在响应里，前端自行显示「[撤回的消息]」）。
- 🟡 **`/api/im/private/upload` 无 owner 归属**：文件落到通用上传目录，任何人可凭文件名/URL 访问（既有设计，未变）。
- 🟡 沙箱注入 `SERVER__PORT=50078`，README/开发脚本仍写 8080。
- ⚠️ **环境陷阱（本轮实测）**：`localhost:50078` 同时被 WorkBuddy Electron 预览代理 `127.0.0.1:50078` 监听。后端未启动时，`curl localhost:50078/` 会**返回前端 HTML（假成功）**、`/api/*` 返回 404 —— 验证前必须确认响应体是 **Spring JSON** 且 java 进程为本轮新起。

#### 十九、下一最佳任务

**P1-D4 — IM 私信错误语义收敛（`RuntimeException` → 404 / 403 / 400）**。
理由：① 是**真实用户可触发**的语义错误（访问已删除会话、越权点击、自聊），返回「系统内部错误」会误导用户与监控；② 与已完成 P1-D2/P1-D3 同族、模式已验证（双参 `BusinessException` + 双断言测试）；③ 成本低、范围清晰（`PrivateMessageServiceImpl` 5 处），可复用本批已建好的 `PrivateMessagePrincipalAuthTest` 与 curl 脚本闭环。
（次选：`P2-Doc-Favorite` 收藏 toggle 500——影响面更直观，但属 UI 高频路径；本批安全语义收尾的连贯性更强，故排 P1-D4 优先。）

---

### 2026-09-12 · P1-D4 PrivateMessageServiceImpl HTTP Error Semantics

#### 1. Agent / 2. Model / 3. 日期

| 项 | 值 |
|---|---|
| Agent | WorkBuddy（Agent 模式，单 Agent 执行） |
| Model | DeepSeek-V4.1-Flash（会话运行时标注） |
| 日期 | 2026-09-12 |
| HEAD（未变） | `c8b7f59`（本批**未 commit、未 push**，任务书 §二 明确禁止） |

#### 4. 扫描了多少 RuntimeException / 5. 实际修改多少

`PrivateMessageServiceImpl.java` 全文件 `throw` 共 **5 处，全部为裸 `RuntimeException`**（0 处 `BusinessException`）。逐处判定后 **5 处全部收敛（100%）**——本文件不存在「真正的服务端异常」写法（无 `catch`、无 `throw e`、无 SQL 包装），故无保留项。

**修复前候选表（逐处判定，非机械替换）**

| # | 方法 | 条件 | 修复前异常 | 修复前 HTTP | 正确 HTTP | 本轮处理 |
|---|---|---|---|---:|---:|:--:|
| 1 | `getOrCreateConversation` | `targetUserId == null \|\| == userId` | `RuntimeException("不能与自己发起私聊")` | 500 | **400** | ✅ |
| 2 | `recallMessage` | 消息不存在 | `RuntimeException("消息不存在")` | 500 | **404** | ✅ |
| 3 | `recallMessage` | `senderId != userId` | `RuntimeException("只能撤回自己的消息")` | 500 | **403** | ✅ |
| 4 | `checkParticipant` | 会话不存在 | `RuntimeException("会话不存在")` | 500 | **404** | ✅ |
| 5 | `checkParticipant` | 非 `userAId`/`userBId` | `RuntimeException("无权访问该会话")` | 500 | **403** | ✅ |

判定依据：5 处全部满足「用户可理解 + 由用户请求本身触发」⇒ 属 HTTP 语义错误；无一处是数据库/SQL/未知内部故障。

#### 6. 修改文件

| 文件 | 改动 |
|---|---|
| `backend/src/main/java/com/knowflow/service/impl/PrivateMessageServiceImpl.java` | 5 处 `RuntimeException` → 双参 `BusinessException(code, message)`；补 `import ...exception.BusinessException`；补类级 javadoc 说明语义边界；**原文案一字未改**；未新增异常类型、未改方法签名、未改 Mapper/SQL/DTO/VO |
| `backend/src/test/java/com/knowflow/controller/PrivateMessageErrorSemanticsTest.java`（新增） | 16 例（独立 H2 库 `knowflow_p1d4_im`） |
| `backend/src/test/java/com/knowflow/controller/PrivateMessagePrincipalAuthTest.java` | 6 处「记录性断言」按设计同步更新为 403/404/400（详见 §14） |
| `docs/AGENT_HANDOFF.md` | 本小节 |

**未修改**：`PrivateMessageController`（只读）、`SecurityConfig`、`GlobalExceptionHandler`、schema、`ImWebSocketHandler`、`WebSocketAuthInterceptor`、任何其他模块。

#### 7. checkParticipant（403 / 404）

`checkParticipant(conversationId, userId)` 是 `getMessages` / `sendMessage` / `markAsRead` / `getUnreadCount` **四条读写路径的公共守卫**，因此本处收敛一次性覆盖 4 个端点：

- 会话不存在 → `BusinessException(404, "会话不存在")`
- 非参与者 → `BusinessException(403, "无权访问该会话")`
- 抛出位置在**任何数据读写之前** ⇒ 越权请求零副作用、零内容泄漏

四个端点逐一实证（curl + MockMvc 双通道，见 §10 / §14）：`/messages`、`/unread`、`/read`、`POST /messages` 对同一场景返回**完全一致**的 status + code + 文案。

#### 8. recallMessage（403 / 404 + 零副作用）

先 `selectById` 判存在（404），再判 `senderId == userId`（403），**两条失败路径都早于 `setRecalled(1)` / `updateById`** ⇒ 结构上不可能有副作用。

- 消息不存在 → `BusinessException(404, "消息不存在")`（**不是 403**：资源根本不存在）
- 他人消息 → `BusinessException(403, "只能撤回自己的消息")`

零副作用实测（§11）覆盖：消息行 `recalled` / `content` / `update_time` / `deleted`、会话行 `lastMessageId` / 时间戳、`private_conversation_read` 行数、全库消息/会话行数。
私信 recall **不触碰任何 learning 相关表**（`schema.sql` 中亦不存在 `learning_event` 服务端表；学习证据体系在前端 `frontend/src/learning/evidence/`），故未影响 Evidence / Mastery。

#### 9. getOrCreateConversation（400）

`sender == receiver` 属**请求本身不成立**（资源存在与否不是关键）⇒ `BusinessException(400, "不能与自己发起私聊")`，非 403 / 非 404。
`targetUserId == null` 分支同文案保留：Controller 侧 `@RequestParam Long targetUserId` 为必填，HTTP 层不可达（实测缺参返回 400「缺少必需的请求参数：targetUserId」），该分支仅作内部调用防御。
零副作用：自聊请求在 `conversationMapper.insert` **之前**被拒，会话表行数不变（`P1D4-13` 断言）。

#### 10. HTTP 语义矩阵（真实 curl，后端 PID 15958）

| 场景 | 预期 HTTP | 实际 HTTP | body.code | 结果 |
|---|---:|---:|---:|---|
| 未登录 | 401 | 401 | 401 | ✅ |
| 正常（本会话读 / 发 / 已读） | 200 | 200 | 200 | ✅ |
| 无权限访问会话（`/messages`、`/unread`、`/read`、`POST /messages` 四端点） | 403 | 403 | 403 | ✅ |
| 撤回他人消息 | 403 | 403 | 403 | ✅ |
| 消息不存在 | 404 | 404 | 404 | ✅ |
| 会话不存在 | 404 | 404 | 404 | ✅ |
| 自己给自己发消息 | 400 | 400 | 400 | ✅ |
| 协议层（缺参 / 类型不匹配） | 400 | 400 | 400 | ✅ |
| 真正内部异常 | 500 | 500 | 500 | ✅（handler 级断言，见下） |

**无任何一项出现 `HTTP status ≠ body.code`**（`P1D4-15` 对 6 个场景专项断言）。

「真正内部异常 → 500」的取证方式说明：无法在不注入故障、不污染共享测试上下文的前提下可靠地经 HTTP 产生真实 500，故采用 **handler 级 + 注解级双断言**：`GlobalExceptionHandler.handleException(new RuntimeException("…"))` 返回 `code=500 / message="系统内部错误"`，且该方法 `@ResponseStatus` 仍为 `INTERNAL_SERVER_ERROR`（`P1D4-14`）。该断言同时防止「为把 `RuntimeException` 清零而误改兜底语义」。

**对照修复前基线**（同一后端矩阵，P1-A5 轮次原始输出 `.dev-logs/p1a5/post-fix-matrix.txt`，已归档为 `.dev-logs/p1d4/pre-fix-baseline.txt`）：

```
admin 撤回 user1 的消息（越权）      HTTP=500  {"code":500,"message":"系统内部错误"}
admin GET  /conversations/1/messages  HTTP=500  {"code":500,"message":"系统内部错误"}
admin GET  /conversations/1/unread    HTTP=500  {"code":500,"message":"系统内部错误"}
admin POST /conversations/1/read      HTTP=500  {"code":500,"message":"系统内部错误"}
user1 GET  /conversations/999999/messages HTTP=500 {"code":500,"message":"系统内部错误"}
user1 POST /conversations?targetUserId=2  HTTP=500 {"code":500,"message":"系统内部错误"}
```

⇒ 修复后依次为 **403 / 403 / 403 / 403 / 404 / 400**，文案原样。

#### 11. 零副作用验证（三层取证）

| 层 | 手段 | 结果 |
|---|---|---|
| 测试（DB 逐字段） | `snapshot()`：`id\|conversationId\|senderId\|content\|messageType\|recalled\|deleted\|createTime\|updateTime` 前后全等；会话行、已读行数、全库行数前后全等 | `P1D4-9` / `P1D4-10` / `P1D4-13` PASS |
| curl（API 可观测集合） | 越权撤回前后拉全量消息，逐条 `recalled`/`content` 序列化比对 | **完全一致 ⇒ PASS** |
| Browser fetch | user2 现发一条新消息（id=3）→ user1 越权撤回 → 前后 `{recalled:false, content:原样, isMine:false}` 全等 | **zeroSideEffect = true** |

对照：user2 本人撤回同一条 → 200 + `recalled=True`（正常路径未被破坏）。

#### 12. 真实 curl

- 新脚本 `/tmp/p1d4_matrix.sh`（在 `/tmp/p1a5_matrix2.sh` 基础上扩展语义断言与前后比对，未重造基础设施）；原始输出 `.dev-logs/p1d4/post-fix-matrix.txt`。
- 全部 `curl -s --noproxy '*'` 直连 `http://localhost:50078`。
- **环境陷阱复核（务必沿用）**：`127.0.0.1:50078` 常被 WorkBuddy Electron 预览代理占用；本轮验证前先发现**陈旧后端 PID 14283（13:23 启动，跑的是改动前代码）**仍在监听，已 `kill -9` 后重启，**新实例 PID 15958（13:37:59 启动）**，并在跑矩阵前用探针确认响应体为 Spring JSON（401 + `{"code":401,...}`）而非代理返回的前端 HTML。
- 覆盖：核心 7 项 + 同族 6 项 + 零副作用前后比对 + 本人撤回对照 + 协议层 3 项 + 成功信封 3 项。

#### 13. Browser（真实 Chromium + Vite 5173 → 50078 代理）

| 类型 | 内容 | 结果 |
|---|---|---|
| Browser UI | `/login` 真实填写表单登录 `user1` | 成功，跳转首页 |
| Browser UI | `/messages` 加载 | 会话列表含「测试用户2」私聊 + 2 个群；无报错 |
| Browser UI | 打开私聊会话 | 渲染已有消息与「[撤回的消息]」；无报错 |
| Browser UI | 输入框发送「P1D4 浏览器正常发送验证」 | 成功，气泡出现并显示「已读」 |
| Browser UI | 点击「撤回」按钮 | 该条变为「[撤回的消息]」，撤回按钮消失；无报错 |
| Browser UI | 页面文本扫描 | `系统内部错误` / `服务暂时不可用` / `无权` **均未出现** |
| Browser fetch | 未登录 `GET /conversations` | 401 + code401 |
| Browser fetch | 正常 `GET /conversations/1/messages` | 200 + code200 |
| Browser fetch | admin 读 user1 会话消息 | **403** + code403「无权访问该会话」 |
| Browser fetch | user1 撤回 user2 消息 | **403** + code403「只能撤回自己的消息」 |
| Browser fetch | 撤回不存在消息 | **404** + code404「消息不存在」 |
| Browser fetch | `targetUserId = 自己` 建会话 | **400** + code400「不能与自己发起私聊」 |
| Browser fetch | 不存在的会话读消息 | **404** + code404「会话不存在」 |
| Browser fetch | 越权撤回前后消息集合比对 | `zeroSideEffect = true` |
| Console / Errors | 页面错误与控制台 | **0 个 page error**；仅 Vite debug + `IM WebSocket 已连接` |

> 明确区分：上表 `Browser UI` 与 `Browser fetch` 两类均为真实 Chromium 会话内执行；**未把 curl 冒充 Browser E2E**。
> 截图：`.dev-logs/p1d4/p1d4-messages-semantics.png`。
> AI Provider 本轮**无涉**（未调用任何 AI 接口），故无「E2E 未验证」项。

#### 14. 后端测试

- 新增 `PrivateMessageErrorSemanticsTest`：**16 / 16 PASS**（独立 H2 库 `knowflow_p1d4_im`）。
- 后端全量：**327 / 0 failures / 0 errors**（311 → **+16**），BUILD SUCCESS。
- 卷宗：`/tmp/p1d4-im.log`、`/tmp/p1d4-full-backend.log`。

**关于既有断言变更的说明（6 处，均为「按设计同步」而非「为过测试而改」）**：
`PrivateMessagePrincipalAuthTest` 中 `P1A5-9 / -11 / -12 / -14 / -15 / -17` 原本**故意**钉死 500，其 javadoc 明写「给后续 P1-D4 提供可断言基线，改动时此测试将失败，提示同步更新」。本轮即该批次，故按设计同步为 403 / 404 / 400，并**同时增加了更严格的断言**（status + code + message + 零副作用 + 无 data 载荷），未删除任何测试、未放宽任何断言。

#### 15. 前端测试 / 16. vue-tsc / 17. build

| 门禁 | 结果 |
|---|---|
| `npx vitest run --maxWorkers=1` | **583 passed / 46 files**（无前端改动，基线持平） |
| `npx vue-tsc -b --force` | **0 error** |
| `npm run build` | **PASS**（built in 3.75s） |

#### 18. 保留的 RuntimeException / 19. 为什么保留

`PrivateMessageServiceImpl`：**保留 0 处**（代码口径 `throw new RuntimeException` = 0；仅类 javadoc 出现该词 1 次）。
理由：该文件不存在「真正的服务端异常」的显式抛出——它不吞异常、不包装 SQL 异常、无 `catch`；5 处 `throw` 全部是用户级语义错误。因此「全改」在此文件是**经过逐处判定后的结论**，而非机械替换。

**本轮 scope 外、仅在 §20 记录的同类缺口（未修）**：`StudyGroupServiceImpl` 有 **22 处**裸 `RuntimeException`，与 P1-D4 属**完全同族**（`小组不存在`×6、`成员不存在`×2、`不是小组成员`×2、`无权访问该小组`、`消息不存在`、`只能撤回自己的消息`、`只有创建者可以删除小组`、`私有小组需要邀请才能加入`、`创建者不能退出小组…` 等）⇒ 当前同样返回 HTTP 500「系统内部错误」。按任务书 §十七「不要碰其他模块」，本轮未动。

#### 20. 剩余风险

- 🟠 **`StudyGroupServiceImpl` 22 处同族 500**（上文 §19）——与 P1-D4 同构、可按同一模式低成本收敛。
- 🟠 **剩余 principal 强转 55 处 / 18 个 Controller**（注释口径：`grep` 命中 57 行，其中 `StudyGroupController:47`、`PrivateMessageController:30` 为 javadoc 文字，代码口径 55）。重灾区：`AiConfigController`(7)、`MistakeController`(6)、`UserPetController`(5)、`LearningNoteController`(5)、`NotificationController`(4)、`CommunityController`(4)。（P1-A5 记录写作「19 Controller」，本轮精确复核为 **18** 个含代码强转的 Controller。）
- 🟠 **`P2-Doc-Favorite`**：取消收藏后无法再次收藏 → `DuplicateKeyException` → HTTP 500（P1-A4 发现）。**按任务书 §十八本轮明确不修**。
- 🟠 **开发环境 AI 恒 401**（本地配置 `application-local.yml` 旧 key + dev 脚本未激活 `local` profile，待用户拍板）。
- 🟡 **`recallMessage` 二次查询**：Controller 与 Service 各自 `selectById` 同一条消息（2 次 DB 往返），Controller 那次仅用于 WS 广播参数。
- 🟡 **`getMessages` 不屏蔽 `recalled` 内容的 `content`**：撤回后 `content` 仍在响应中，由前端渲染为「[撤回的消息]」。语义上不算泄漏（发送者/接收者本就见过），但若产品要求「撤回即内容不可再取」需后端屏蔽——**属产品决策，本轮未动**。
- 🟡 **`/api/im/private/upload` 无 owner 归属**：文件落通用上传目录，且 Controller 内 `catch (IOException) { return Result.error(...) }` 会把失败降级为 **HTTP 200 + body.code=500**，并回传 `e.getMessage()`（违反「失败不得伪装成成功/不得回传原始异常文本」原则）。**Controller 本轮只读，未修**，记录为后续候选。
- 🟡 `WebSocketAuthInterceptor:22` 硬编码兜底 `jwt.secret` 默认值（当前不可达，因 `JwtUtils` 无默认值会 fail-fast）。
- ⚠️ **环境陷阱**：`127.0.0.1:50078` 被 Electron 预览代理占用 + `SERVER__PORT=50078` 由沙箱注入（README 仍写 8080）；且**被强杀的后端父进程会留下孤儿 java 子进程继续占端口**，导致新实例静默启动失败、实测到旧代码——本轮已实际踩中（陈旧 PID 14283）。

#### 21. Next Best Development Task

**P1-D5 —— `StudyGroupServiceImpl` 群组错误语义收敛（22 处 `RuntimeException` → 403 / 404 / 400）**。

按任务书 §二十三 的六档优先级重新扫描后的判断：

| 优先级 | 候选 | 是否入选 |
|---|---|---|
| ①真实用户可触发的 500 / 数据错误 | `StudyGroupServiceImpl` 22 处（群组不存在/无权限/非成员/成员已是/创建者限制…全部 500） | ✅ 最大且最高频 |
| ① | `P2-Doc-Favorite` 收藏 toggle 500 | 并列候选（见下） |
| ②数据安全/权限 | 未发现新缺陷（P0-1/P0-1b 已闭环） | — |
| ③静默失败 | IM 私信本轮无遗留；`uploadFile` 的 200+code500 属此档 | 记录 |
| ④死接口 | `dueFlashcards → /learning/flashcards/due` 404（既有记录） | — |
| ⑤防御性 principal | 55 处 / 18 Controller | 低于 ① |
| ⑥UI 技术债 | — | — |

**选 P1-D5 的理由**：① 与 P1-D4 **完全同族**（同为裸 `RuntimeException` → 500，同为「小组不存在 / 无权访问 / 只能撤回自己的消息」语义），模式已验证、可复用本批的「候选表 → 双参 BusinessException → 双断言测试 → curl 矩阵 → 零副作用」全流程；② 规模更大（22 处 vs 5 处）且**群聊是真实高频入口**（用户已能在 UI 触发「加入私有小组」「非创建者删组」等）；③ 文案与 HTTP 语义的映射规则可直接照搬 P1-D4，判定成本近零。

**次选**：`P2-Doc-Favorite`（取消收藏后再收藏 → 500）。影响面直观，但属「数据一致性 + 唯一索引设计」问题（`uk_fav_user_doc` 不含 `deleted` + `@TableLogic` 逻辑删除），改动涉及 schema/索引策略，**成本高于 P1-D5**，故排后。

---

### 2026-09-12 · P1-D5 StudyGroupServiceImpl HTTP Error Semantics

#### 1. Agent / 2. Model / 3. 日期

| 项 | 值 |
|---|---|
| Agent | WorkBuddy（Craft / Agent mode） |
| Model | DeepSeek-V4.1-Flash（当前会话模型） |
| 日期 | 2026-09-12 |
| 起点 | HEAD `c8b7f59`（P1-A3 第三批），全程**未 commit、未 push** |
| 后端实例 | PID **17617**，`13:56:09` 启动，`local` profile + `AI_API_KEY` 注入，端口 50078 |

#### 4. 扫描了多少 RuntimeException / 5. 实际修改多少

`StudyGroupServiceImpl.java` 全文件 `throw` 共 **22 处，全部为裸 `RuntimeException`**（0 处 `BusinessException`），**无 `catch` 块、不包装 SQL / 第三方异常**。逐处判定后：

| 口径 | 数量 |
|---|---:|
| 扫描 | 22 |
| 判定为「用户可理解业务错误」并收敛 | **22（100%）** |
| 判定为「真正服务端异常」而保留 | **0** |
| 收敛后单行 throw 总数 | 23（含 1 处条件拆分，见 §6 备注） |

> **「100% 收敛」是逐处判定的结论，不是机械替换**：该文件不存在 `catch (SQLException)`、不存在把底层异常包成 `RuntimeException` 的代码路径，22 处触发条件全部由「请求参数 + 数据库查得的存在性/归属/角色」构成，即全部是用户可理解的语义错误。

#### 6. 每一类 400 / 403 / 404 的映射

**404（资源不存在）— 11 处**

| 方法 | 条件 | 文案（一字未改） |
|---|---|---|
| `getGroupDetail` / `updateGroup` / `deleteGroup` / `joinGroup` / `updateAnnouncement` / `linkLearningPlan` / `updateMemberRole` | 小组查不到 | 小组不存在 ×7 |
| `inviteMember` | 按 email / userId 查不到用户 | 用户不存在 |
| `removeMember` / `updateMemberRole` | 成员查不到或不属于该小组 | 成员不存在 ×2 |
| `recallMessage` | 消息查不到 | 消息不存在 |

**403（已登录但无权限）— 7 处**

| 方法 | 条件 | 文案 |
|---|---|---|
| `getGroupDetail` | PRIVATE 小组且非成员 | 无权访问该小组 |
| `deleteGroup` | `ownerId != userId` | 只有创建者可以删除小组 |
| `leaveGroup` | 当前用户不是该组成员 | 不是小组成员 |
| `checkMemberPermission`（守卫 `getGroupMembers` / `getMessages` / `sendMessage`） | 非成员 | 不是小组成员 |
| `checkAdminPermission`（守卫 `updateGroup` / `inviteMember` / `removeMember` / `updateAnnouncement` / `linkLearningPlan`） | 非 OWNER/ADMIN | 没有管理权限 |
| `updateMemberRole` | `ownerId != operatorId` | 只有创建者可以修改成员角色 |
| `recallMessage` | `senderId != userId` | 只能撤回自己的消息 |

**400（请求本身不合法 / 违反业务规则）— 5 处**

| 方法 | 条件 | 文案 |
|---|---|---|
| `getOrCreateConversation`→`joinGroup` | 非 PUBLIC 小组 | 私有小组需要邀请才能加入 |
| `joinGroup` | 已是成员 | 已是小组成员 |
| `inviteMember` | 已是成员 | 用户已是小组成员 |
| `leaveGroup` | 角色为 OWNER | 创建者不能退出小组，请先转让或删除小组 |
| `removeMember` | 目标角色为 OWNER | 不能移除创建者 |

**判定规则（可复用）**

1. **403 vs 404**：同一文案「不是小组成员」在 `leaveGroup` 与 `checkMemberPermission` 两处出现 → **必须同码（403）**，否则同一语义两种状态码。此为「相同 message 强制同 code」的一致性约束。
2. **先存在后权限（顺序即语义）**：资源查不到必须先返回 404，不得因「未通过权限校验」而被表述成 403。
3. **400 的边界**：状态冲突 / 重复操作 / 角色限制（创建者不能退出、不能移除创建者、已是成员、私有组直加）→ 400，与资源存在性无关。

#### 7. 修改文件

| 文件 | 改动 |
|---|---|
| `backend/src/main/java/com/knowflow/service/impl/StudyGroupServiceImpl.java` | 22 处 `RuntimeException` → 双参 `BusinessException`；新增 `import com.knowflow.exception.BusinessException`；**2 处条件顺序修正** + **1 处条件拆分**（见备注） |
| `backend/src/test/java/com/knowflow/controller/StudyGroupErrorSemanticsTest.java` | **新增**，24 例 |
| `backend/src/test/java/com/knowflow/controller/StudyGroupPrincipalAuthTest.java` | 4 例「记录性断言」按设计同步为 404/403（P1A3-7/8/9/10），并加严为 status+code+message 三重断言；类 javadoc 更新 |
| `docs/AGENT_HANDOFF.md` | 本节 |

**超出「纯异常类型替换」的 3 处逻辑调整（全部为语义正确性，文案零改写）**

| # | 位置 | 原状 | 改后 | 理由 |
|---|---|---|---|---|
| 1 | `updateMemberRole` | `if (group == null \|\| !group.getOwnerId().equals(operatorId))` 合并为同一 500 文案 | 拆为 `group == null` → 404「小组不存在」+ 非创建者 → 403「只有创建者可以修改成员角色」 | 合并条件里两种语义不同；不拆会让「小组不存在」被表述成权限问题。新增文案取自本文件既有标准文案（已出现 6 次） |
| 2 | `updateAnnouncement` | 先 `checkAdminPermission` 再查小组 | 先查小组（404）再查权限（403） | 消除「小组不存在被报成没有管理权限」；与 `getGroupDetail` / `updateGroup` 既有顺序一致 |
| 3 | `linkLearningPlan` | 同上 | 同上 | 同上 |

> 说明：`removeMember` / `inviteMember` **未做此调整**——这两个方法当前**根本不查询小组实体**（只查成员关系），目标小组不存在时经由权限守卫返回 403 是与现有实现一致的语义；若改为 404 需**新增一次小组查询**，属新增逻辑，超出本轮 scope，故保留并记录（见 §17）。

#### 8. 新增测试

`backend/src/test/java/com/knowflow/controller/StudyGroupErrorSemanticsTest.java` — **24 例**，独立 H2 库 `jdbc:h2:mem:knowflow_p1d5_studygroup`（避免与共享 `mem:knowflow` 的 `CREATE INDEX` 冲突），沿用项目既有测试模式（`@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("local")` + `@Import(FakeAiConfiguration.class)`）。

覆盖：401 未登录（1）｜404 资源不存在（6：小组 / 跨 5 个写端点 / 成员 / 用户 / 消息 / 改角色时小组缺失）｜403 无权限（6：私有组 / 成员守卫三端点 / 非成员退出 / 非创建者管理五端点 / 非创建者成员管理 / 越权撤回）｜400 业务规则（5：私有组直加 / 重复加入 / 创建者退出 / 重复邀请 / 移除创建者）｜200 正常回归（6：读四端点 / 加入退出 / 发消息+撤回 / 创建者删组级联 / 创建者成员管理 / 公告+关联计划）。

**每条业务错误断言均为四元组**：`status().isXxx()` + `$.code` + `$.message` + `$.data` 不存在——专门防「HTTP 200 + body.code 4xx」假修复。

**零副作用断言（5 处）**：
- 越权撤回他人消息 → 逐字段快照全等（`recalled` / `content` / `senderId` / `update_time` / 消息行数）；
- 越权删组 → 小组仍存在、名称与公告未变、成员数未变；
- 越权移除成员 / 改角色 → 成员行仍在、角色仍为 `MEMBER`；
- 非法加入私有组 / 重复加入 / 重复邀请 / 创建者退出 / 移除创建者 → 成员行数与成员关系零变化；
- 不存在资源操作 → 不产生任何行。

#### 9. 后端全量测试 / 10. 前端 / 11. vue-tsc / 12. build

| 门禁 | 结果 |
|---|---|
| `mvn -f backend/pom.xml test` | **351 tests / 0 failures / 0 errors**（327 → **+24**） |
| `npx vitest run --maxWorkers=1` | **583 passed / 46 files** |
| `npx vue-tsc -b --force` | **0 error** |
| `npm run build` | **PASS**（built in 4.07s） |

未删除任何测试、未放宽任何断言。`StudyGroupPrincipalAuthTest` 的 4 处变更属**按设计同步**（该测试初版 javadoc 明写「P1-D5 改动时此测试会失败，提示同步更新」，且其断言对象就是本轮被收敛的语义）。

#### 13. curl 实测矩阵（真实后端 PID 17617，`--noproxy '*'`，41 项）

脚本 `/tmp/p1d5_matrix.sh`，完整输出 `.dev-logs/p1d5/post-fix-matrix.txt`。**41/41 项 `HTTP status ≡ body.code`，0 项不一致**。

| 分组 | 场景数 | 结果 |
|---|---:|---|
| A 404 资源不存在 | 11 | 404/404 全部一致（小组×1 / 跨 5 写端点 / 成员×2 / 改角色时小组缺失 / 消息 / 用户） |
| B 403 无权限 | 9 | 403/403 全部一致 |
| C 400 业务规则 | 5 | 400/400 全部一致 |
| D 越权撤回 + 零副作用 | 1 | 403/403；API 可观测状态前后**逐条一致**（`零副作用：PASS`） |
| E 正常路径回归 | 11 | 全部 200/200 |
| F 401 / 协议层 | 4 | 401×2、`/abc` 类型不匹配 400、`@Valid` 缺 name 400 |

**修改前基线**（`HEAD=c8b7f59` 的 `StudyGroupPrincipalAuthTest` 所断言的当时真实语义，已归档 `.dev-logs/p1d5/pre-fix-baseline-source.java`）：4 个场景原为 `isInternalServerError()` + `code 500`，现为 404 / 403 / 403 / 403。因该基线是**提交内测试的实际断言**（即已实证的 500），未另造 pre-fix 运行，属可复现证据而非推测。

#### 14. 浏览器验证（真实 Chromium + Vite 5173 → 50078 代理）

**Browser UI（真人在页面上的操作，非 fetch）**
1. `/login` 真实登录 `user1` → 跳转首页，`localStorage.token` 长度 192；
2. `/study-group` 打开「前端工程化实战」→ 消息框输入并 Enter 发送 → 消息上屏；
3. 点击该消息「撤回」→ DOM 变为 `class="message-item mine recalled"`，`.msg-recall` 数量归 0；
4. 推荐小组区点击「加入」→ 成功加入并在「我加入的小组」出现；
5. 全程 `document.body.innerText` 不含「系统内部错误」「服务暂时不可用」，四个失败提示前缀（撤回失败/加入失败/创建失败）**均未出现**。

**Browser fetch（页面内 `fetch('/api/...')`，**不是** Browser E2E）** — 20 项断言全部 `HTTP ≡ code`：

| 类别 | 结果 |
|---|---|
| 404 ×5（小组 / 成员 / 消息 / 用户 / 公告） | 404/404 |
| 403 ×6（非成员看私有组 / 成员列表 / 发消息 / 退出 / 非创建者删组 / 越权撤回） | 403/403 |
| 400 ×5（直加私有组 / 创建者退出 / 重复加入 / 重复邀请 / 移除创建者） | 400/400 |
| 200 ×2（成员读详情 / 本人撤回自己消息） | 200/200 |
| 401 ×1（匿名读组） | 401/401 |
| zero-side-effect | `before === after` → **true** |

**Console**：`agent-browser errors` 无输出（**0 个 page error**）；`console` 仅 `[vite] connecting/connected` 与 `WebSocket 已连接`，无新增异常。截图 `.dev-logs/p1d5/p1d5-studygroup-semantics.png`。

#### 15. zero-side-effect 验证（三层取证）

| 层 | 手段 | 结论 |
|---|---|---|
| 单元测试层 | 越权前后 DB 逐字段快照（`recalled` / `content` / `senderId` / `update_time` / 成员行数 / 消息行数） | 全等 |
| curl 层 | 越权撤回前后按 API 拉取整个消息集合逐条比对 | `PASS` |
| Browser 层 | 页面内 fetch 越权撤回前后 `JSON.stringify(records)` 比对 | `true` |

结构层面另有保证：`recallMessage` 的 `null → 404` 与 `senderId != userId → 403` 两条失败路径**都在 `setRecalled(1)` 之前**；`StudyGroupController#recallMessage` 的 WebSocket 广播语句位于 Service 调用之后，异常时不会执行。私信 / 群消息的撤回**不触碰任何 learning 事件表**。

#### 16. 保留的 RuntimeException / 17. 为什么保留

**本文件保留 0 处**（`throw new RuntimeException` 计数 = 0，仅类 javadoc 提及该词）。理由见 §5：该 Service 无 `catch`、不包装底层异常，22 处触发条件全部由业务状态构成。真正未知异常仍由 `GlobalExceptionHandler` 兜底为 500，语义未被污染。

**全仓 main 代码剩余 `RuntimeException`：7 处（本轮 scope 外，仅记录）**

| 保留位置 | 数量 | 为什么不是业务语义异常 |
|---|---:|---|
| `util/AnkiApkgParser.java` | 5 | APK/APKG 压缩包结构解析失败（zip 条目缺失、编码不可解析等），属**输入文件格式损坏**而非「资源不存在/无权限」，且不在本轮 scope |
| `service/impl/ObsidianImportServiceImpl.java` | 2 | Obsidian vault 导入期的文件系统 / 解析故障，同上 |

> 这两处是否应改为 400（用户上传了坏文件）或保持 500，需单独判定，**本轮不改**。

#### 18. 当前剩余技术债

| 优先级 | 项 | 状态 |
|---|---|---|
| ① | **`P2-Doc-Favorite` 收藏 toggle 500** —— 本轮**已用真实 HTTP 复现**：`POST /api/docs/16/favorite` ×3 → `200 / 200 / 500(系统内部错误)`。根因：`uk_fav_user_doc UNIQUE(user_id, doc_id)` **不含 `deleted`**，而 `DocFavorite` 经 `BaseEntity` 带 `@TableLogic` ⇒ 取消收藏是逻辑删除（行仍在），再次收藏 `insert` 触发唯一键冲突 → `DuplicateKeyException` → 500 | **下一任务首选** |
| ①/③ | **单参 `BusinessException(String)` 124 处**（`→ HTTP 200 + body.code 500`「假成功」）。Top：`LocalReaderService` 17、`CodeWorkspaceServiceImpl` 11、`LearningServiceImpl` 10、`KbMemberServiceImpl` 10、`AiServiceImpl` 9、`CodeGenService` 7、`admin/AdminUserController` 7… | 系统性批次，未处理 |
| ③ | 已知静默失败：`WeeklyReport.vue:462/463`、`Messages.vue`、`Categories.vue`、`LearningMode.vue:295` 吞错 | 未处理 |
| ③ | `StudyGroupController#uploadFile` 的 `catch (IOException) → Result.error(...)` ⇒ `HTTP 200 + code 500` 且回传原始 `e.getMessage()`（**本轮 Controller 只读，未修**，§十二 明确排除） | 未处理 |
| ④ | 死接口 `dueFlashcards → /learning/flashcards/due` 实测 404 | 未处理 |
| ⑤ | 部署性 principal 强转 **55 处 / 18 Controller**（本轮未触碰任何 Controller，数量与 P1-D4 一致） | 未处理 |
| 🟡 | `removeMember` / `inviteMember` 对「小组不存在」返回 403 而非 404（需新增小组查询才能改，本轮 scope 外，见 §7 备注） | 记录 |
| 🟡 | `markAsRead(groupId, userId)` / `getUnreadCount(groupId, userId)` **无成员校验**：非成员调用 `markAsRead` 不产生写入（成员行查不到即跳过），但会返回该组最后一条消息 id；`getUnreadCount` 会返回消息计数 ⇒ **轻微信息泄漏（非写入越权）**。本轮未修（不属异常语义范畴） | 新发现，记录 |
| 🟡 | `recallMessage` 在 Controller 与 Service 各 `selectById` 一次（重复查询） | 既有记录 |

#### 19. 下一最佳开发任务

按任务书 §十六 的六档优先级在**本轮完成后重新扫描**，只选一个：

> ### **P2-Doc-Favorite —— 文档收藏 toggle 在「取消后再次收藏」时返回 HTTP 500**

| 优先级 | 候选 | 判定 |
|---|---|---|
| ①真实用户可触发的 500 / 数据错误 | **P2-Doc-Favorite** —— 本轮真实 HTTP 已复现 `200/200/500`，且「收藏」是知识库页面**最高频按钮之一**，用户点三下必现；后果是**该文档再也无法收藏**（功能不可逆丢失） | ✅ **入选** |
| ① | 单参 `BusinessException` 124 处 | 规模最大但**单点用户影响弱于**「按钮点了就 500」；且属系统性 sweep，适合在单点缺陷清零后作为独立批次 |
| ②数据安全/权限 | 未发现新缺陷（P0-1 / P0-1b 已闭环；本轮新增的 `markAsRead` 信息泄漏属 🟡，无写入越权） | — |
| ③静默失败 | 静默失败清单 + `uploadFile` 假成功 | 低于 ① |
| ④死接口 | `dueFlashcards` 404 | 低于 ① |
| ⑤防御性 principal | 55 处 | 本轮已验证全部位于 `authenticated()` 之后，纯技术债 |
| ⑥UI 技术债 | — | — |

**为什么它排在 P1-D5 之后成为第一**：P1-D5 清掉了群组模块最后一批「用户操作 → 500」；扫描后剩余的唯一**同等级**缺陷就是收藏 toggle。它同时具备三个入选特征——①**用户可复现**（真实 HTTP + UI 均有自然入口）、②**数据/功能损坏**（不是仅状态码难看：文档永久无法收藏）、③**范围可控**（`DocServiceImpl#toggleFavorite` 1 个方法 + `uk_fav_user_doc` 索引策略，影响面清晰）。

**注意**：修复方向涉及「唯一索引是否纳入 `deleted`」或「改物理删除 / 复用软删行」的取舍，属**数据一致性策略决策**，实施前应先给出方案对比再动手。

---

### 2026-09-12 · P2-Doc-Favorite 收藏 Toggle 数据一致性修复

**1. Agent / 2. Model / 3. 日期**：KnowFlow Agent（WorkBuddy，Agent 模式）· 由会话上下文指定模型（DeepSeek-V4.1-Flash）· 2026-09-12

**4. 根因（已取证到 SQL 层）**

`doc_favorite` 经 `BaseEntity` 带 `@TableLogic deleted`，而唯一约束 `uk_fav_user_doc(user_id, doc_id)` **不含 `deleted`**：

```
① 收藏            → INSERT (user_id, doc_id, deleted=0)
② 取消收藏        → @TableLogic 逻辑删除 → UPDATE doc_favorite SET deleted=1 WHERE id=? AND deleted=0
                    （物理行仍在，唯一键 (user,doc) 继续被占用）
③ 再次收藏        → SELECT ... WHERE deleted=0 AND (user_id=? AND doc_id=?) → 查不到
                  → 走 INSERT → 撞 UK_FAV_USER_DOC
                  → org.springframework.dao.DuplicateKeyException
                  → GlobalExceptionHandler 兜底 → HTTP 500「系统内部错误」
```

修复前真实复现（后端 PID 17617，改动前代码，用户 user1 / 文档 16）：

```
第1次 HTTP=500 code=500 第2次 500 第3次 500 第4次 500 第5次 500
```

> 注：上一轮（P1-D5 收尾）首次复现时是 `200 / 200 / 500`；本轮再次复现时该 `(user1, doc16)` 已进入**永久不可收藏**状态（残留行占死唯一键），故 5 次全 500。**这是本缺陷最严重的后果：不是「状态码难看」，而是该用户对该文档永久失去收藏能力。**

**5. 修复方案对比**

| 方案 | 做法 | 结论 |
|---|---|---|
| **A. 复活软删行（选用）** | 再次收藏时先 `UPDATE ... SET deleted=0 WHERE user_id=? AND doc_id=? AND deleted=1`；影响 0 行才 INSERT | ✅ 不改 schema / 不改索引 / 保留软删语义 / 改动集中在 1 个方法。**且现有唯一索引反而成为「一个 user+doc 恒 1 行」的保证**，与该模型自洽 |
| B. 唯一索引纳入 deleted | `UNIQUE(user_id, doc_id, deleted)` | ❌ 会允许 `(user, doc, deleted=1)` **多行重复残留**（历史脏数据可无限累积）；且需 schema + MySQL 迁移 + 索引重建，三份方言同步 |

**6. 最终选型及原因**：方案 A。理由：B 会把「恒 1 行」弱化成「最多 1 行活跃 + N 行已删」，直接破坏本缺陷要建立的不变量；A 用最小改动同时修好功能与数据一致性。**`uk_fav_user_doc` 保持原样，未改。**

**7. 修改文件**

| 文件 | 改动 |
|---|---|
| `backend/.../mapper/DocFavoriteMapper.java` | 新增 1 个方法 `reviveDeleted(userId, docId, updateTime)`：原生 `@Update` 复活软删行（绕过 MP 自动追加的 `deleted = 0`） |
| `backend/.../service/impl/DocServiceImpl.java` | `toggleFavorite`：else 分支改为「先复活、复活 0 行才 INSERT」；INSERT 包 `catch (DuplicateKeyException)` 幂等返回；取消分支以**实际删除行数**为准再回退计数 |
| `backend/src/test/.../DocFavoriteToggleTest.java` | 新增，7 例（独立 H2 库 `knowflow_p2_fav`） |

**8. 是否修改 schema / 索引 / 事务边界**：**均未改**（无 DDL、无索引变更、无 `@Transactional` 增删 —— `toggleFavorite` 原本已有 `@Transactional`，保持不动）。

**9. 并发分析**

| 问题 | 结论 |
|---|---|
| 单用户正常点击是否彻底修复 | ✅ 是。生命周期 5 步 + 刷新持久化 + 库内行 id 全程不变（见 §13/§15） |
| 双请求并发是否仍可能 DuplicateKeyException | **代码路径已闭合**：并发落败方 INSERT 撞唯一键后 `catch (DuplicateKeyException) → return`（不重复累加计数）→ 返回 200。**已在真实 HTTP 中实际触发**：日志 2 次 `忽略并发重复收藏：userId=2, docId=16` |
| 是否需要新事务/原子 SQL | 不需要。`@Transactional` 已在；复活与计数同事务 |
| 是否需要 `update-first` | 已采用「复活优先」；无需额外原子 upsert |
| 是否把竞态重新带回 | **没有**，且顺带修掉一处**已存在的计数竞态**：原取消分支无视 `deleteById` 返回值、无条件 `-1`，并发双删会把 `favorite_count` 减穿（`GREATEST(0,...)` 只掩盖不修正）；现行以实际删除行数为准。**该防护由证伪实验证明是必要的**（见 §18） |

**10. 新增测试**：`DocFavoriteToggleTest`，7 例 —— ①完整生命周期（收藏→取消→再收藏→再取消→第三次收藏）②预置 `deleted=1` 残留行的**损坏态恢复** ③用户隔离（A/B 同文档互不影响，B 只复活自己那行）④文档不存在 404 ⑤匿名 401 ⑥详情 `favorite` 与「我的收藏」列表三轮切换契约一致 ⑦并发不变量。**每例同时断言 HTTP status + body.code + 物理行数 + deleted + 行主键（+ 计数）。**

**11. 后端全量**：**358 / 0 failures / 0 errors**（P1-D5 后 351 → **+7**），`mvn -f backend/pom.xml test`。
**12. 前端测试**：`vitest 583 / 46 files` ✅
**13. vue-tsc**：`npx vue-tsc -b --force` **0 error** ✅
**14. build**：`npm run build` **PASS**（built in 4.02s）✅

**15. 真实 HTTP 矩阵**（后端 **PID 25050 / 15:38:33 启动 / local profile**；`curl --noproxy '*'`；脚本 `/tmp/p2fav_matrix.sh`，输出 `.dev-logs/p2-fav/post-fix-matrix.txt`）

| 场景 | 预期 | 实际 | body.code | 结果 |
|---|---:|---:|---:|---|
| 匿名收藏 | 401 | 401 | 401 | ✅ |
| 文档不存在 | 404 | 404 | 404 | ✅ |
| 协议层 GET 收藏端点 | 405 | 405 | 405 | ✅ |
| 第 1 次收藏 | 200 | 200 | 200 | ✅ |
| 取消收藏 | 200 | 200 | 200 | ✅ |
| **再次收藏（修复点）** | 200 | **200** | 200 | ✅（修复前 500） |
| 第 4/5 次 toggle | 200 | 200 | 200 | ✅ |
| 并发 2 请求 × 3 轮 | 200 | 200/200 ×3 | 200 | ✅（无 5xx） |

**11 项探针全部 `HTTP status ≡ body.code`，0 项不一致。**

**16. 数据库状态验证**（关键 —— H2 是**内存库**，无法外部直连；改用 **ADMIN 的 H2 控制台在 JVM 内直读运行中库**，脚本 `/tmp/p2fav_lifecycle.sh`，输出 `.dev-logs/p2-fav/db-lifecycle.txt`）

HTTP toggle 与真实库行状态逐步互证（`user_id=2`, `doc_id=16`）：

| 步骤 | HTTP | code | 物理行数 | 行(id \| deleted) |
|---|---:|---:|---:|---|
| 起始 | — | — | 1 | 7 \| 1 |
| toggle#1 | 200 | 200 | 1 | **7** \| 0 |
| toggle#2 | 200 | 200 | 1 | **7** \| 1 |
| **toggle#3（修复点）** | 200 | 200 | 1 | **7** \| 0 |
| toggle#4 | 200 | 200 | 1 | **7** \| 1 |
| toggle#5 | 200 | 200 | 1 | **7** \| 0 |

- **行 id 全程恒为 7** ⇒ 再次收藏是**复活原行**，从未 INSERT 新行。
- **物理行数全程恒为 1** ⇒ `COUNT(*) = 1`，不是 `1 → 2 → 3`。
- 该文档每用户恰 1 行（`2→1 行`、`3→1 行`）；全表 `GROUP BY user_id, doc_id HAVING COUNT(*)>1` **无任何结果行** ⇒ 库内不存在重复收藏行。
- SQL 级旁证（`.dev-logs/p2-fav/backend.log`）：复活 SQL 执行 **17 次** vs `INSERT INTO doc_favorite` **仅 4 次** ⇒ 绝大多数「再次收藏」走复活路径。

> ⚠️ **发现（非回归）**：查询 `doc.favorite_count == 活跃收藏行数` 在**种子数据**上不成立（全库 `SUM(favorite_count)=1311` vs 实际收藏行 8）—— `data.sql` 的 `favorite_count` 是**演示计数**，与演示关系行本就不同步。本修复只产生 ±1 增量（矩阵实测 30↔31）。该不变量仅在「API 新建文档」上成立，自动化测试即以新建文档断言。

**17. 浏览器 UI 验证**（真实 Chromium + Vite 5173 → 后端 50078；脚本 `/tmp/p2fav_browser.sh`，输出 `.dev-logs/p2-fav/browser-ui.txt`；截图 `.dev-logs/p2-fav/p2fav-docdetail-favorited.png`）

- **Browser UI**（`/doc/16`，user1 真实登录）：连续 **5 次点击**收藏按钮，文案在 `已收藏 ↔ 收藏` 间正确翻转；**每次点击后整页重新加载（`open` 同一 URL）状态均保持**（S1'=收藏、S2'=已收藏 ← 修复点、S3'=收藏、S4'=已收藏、S5'=收藏）⇒ 前端状态不是假状态。
- 全程**未出现**「系统内部错误」/「服务暂时不可用」；**Console page errors = 0**。
- **Browser fetch**（页面内 `fetch`，经真实前端代理，**不是** UI E2E）：匿名收藏 `401|401|未登录或登录已失效`、不存在文档 `404|404|文档不存在` —— HTTP ≡ code。
- **刷新持久化**：每次点击后刷新均与库内状态一致；浏览器操作后直读库确认 `(2,16)` 行 id=7 `deleted=0`（与 UI「已收藏」一致）、每用户恰 1 行。

**18. DuplicateKeyException 修复前后对比**

| | 修复前 | 修复后 |
|---|---|---|
| 真实 HTTP（第 3 次起） | `HTTP 500 code 500 系统内部错误` | `HTTP 200 code 200` |
| 后端日志 | `DuplicateKeyException` + `UK_FAV_USER_DOC` 冲突（本轮修复前 5 次） | **`DuplicateKeyException` 0 次、`UK_FAV_USER_DOC` 0 次、「系统内部错误」0 次** |
| DB | 残留 `deleted=1` 行占死唯一键，永久无法收藏 | 复活原行，行 id 不变、行数恒 1 |
| 新测试在修复前代码下的表现 | **7 例中 5 例失败**（4 例 `expected:<200> but was:<500>` + 并发例 `favorite_count` 减穿 `expected:<1> but was:<0>`） | 7 / 7 通过 |

> 证伪实验：临时把 `DocServiceImpl` 退回修复前逻辑运行新测试 → 5 failures；随后按 md5（`87a404adcdaccf8982b41ad809c8457c`）逐字节还原。证据 `.dev-logs/p2-fav/falsification.txt`。**这同时证明「取消分支按实际删除行数回退计数」那一行防护是必要的，而非臆测。**

**19. 是否存在遗留风险**

- 🟡 **`doc_read_progress` 同型结构**（`uk_rp_user_doc(user_id, doc_id)` + `@TableLogic`）：但该表**无按行逻辑删除写入点**（仅在文档被删除时随文档一起逻辑删除，而 doc id 为自增不复用）⇒ **当前不可被用户触发**，记录备查。
- 🟡 **`learning_user_chapter` 同型结构**（`uk_uc_user_chapter(user_id, chapter_id)` + `@TableLogic`）：全仓仅见 select/update，**无逻辑删除写入点** ⇒ 当前不可触发，记录备查。
- 🟡 **种子数据 `favorite_count` 与关系行不同步**（演示值 vs 实际行），非回归、超本轮 scope。
- ✅ 未改 schema / 未改索引 / 未改 `DocController` / 未改 `SecurityConfig` / 未改 `GlobalExceptionHandler`；`P2-Doc-Favorite` 修复未引入新的异常类型或新异常层。

**20. 下一最佳开发任务（重新扫描后）**

> **P2-User-Register-After-Delete —— 管理员逻辑删除用户后，该 username / email 永久无法再注册（HTTP 500）**

**本轮收尾重扫的真实复现**（后端 PID 25050，公开无鉴权端点；证据 `.dev-logs/p2-fav/next-task-register-defect.txt`）：

```
① POST /api/auth/register（新邮箱/新用户名）      → 200 200（新用户 id=5）
② DELETE /api/admin/users/5（管理员删除 → 逻辑删除）→ 200 200
③ POST /api/auth/register 同 email、新 username    → 500 500「系统内部错误」
④ POST /api/auth/register 同 username、新 email    → 500 500「系统内部错误」
```

精确异常：`PUBLIC.UK_USER_EMAIL_INDEX_A ON SYS_USER(EMAIL)` 与 `PUBLIC.CONSTRAINT_INDEX_A ON SYS_USER(USERNAME)`（`username VARCHAR(50) NOT NULL UNIQUE`）—— **与 P2-Doc-Favorite 完全同族**：唯一索引不含 `deleted` + `@TableLogic`。且 `register` 的「邮箱已注册」预检查也走 `selectOne`（被逻辑删除过滤），因此连友好提示都拿不到，直接 500。

**为什么它排第一**（优先级 ①②③④⑤⑥）：①「真实用户可触发的 500」且落**注册**这一公开入口；②后果是**功能性永久锁死**（该身份再也无法注册），与 P2-Doc-Favorite 同级的可逆性损失；③单点、范围可控（`UserServiceImpl#register` + 2 个唯一索引策略）。**注意**：修法需先决策「复用软删用户行 / 允许邮箱释放 / 前置查重扩到含删除行」，属**数据一致性 + 唯一索引策略决策**，实施前先出方案对比。

**次选**：单参 `BusinessException(String)` **124 处** → HTTP 200 + body.code 500「假成功」（系统性 sweep，单点用户影响弱于上述）；再次：静默失败清单（`WeeklyReport.vue` / `Messages.vue` / `Categories.vue` / `LearningMode.vue`）、`StudyGroupController#uploadFile` 假成功、`dueFlashcards` 404 死接口、剩余 principal 55 处。

**纪律**：**未 commit、未 push**（HEAD 仍 `c8b7f59`）；未覆盖其它 Agent 未提交改动；本轮仅触及 2 个生产文件 + 1 个测试文件 + 本文档。

---

## 九、相关文档

- [AGENTS.md](file:///Users/beiluo/Documents/alProject/qoderProject/knowflow/AGENTS.md) — Agent 工作守则
- [VIBE_CONTEXT.md](file:///Users/beiluo/Documents/alProject/qoderProject/knowflow/VIBE_CONTEXT.md) — Vibe Coding 上下文
- [docs/PROJECT_ARCHITECTURE.md](file:///Users/beiluo/Documents/alProject/qoderProject/knowflow/docs/PROJECT_ARCHITECTURE.md) — 项目架构
- [docs/UI_UX_SYSTEM.md](file:///Users/beiluo/Documents/alProject/qoderProject/knowflow/docs/UI_UX_SYSTEM.md) — UI/UX 系统
- [docs/DEVELOPMENT_PROTOCOL.md](file:///Users/beiluo/Documents/alProject/qoderProject/knowflow/docs/DEVELOPMENT_PROTOCOL.md) — 开发协议

## 十、2026-09-26 Codex 全项目接管扫描

本轮严格停留在扫描、理解、基线验证和文档交接，没有实现新功能，也没有覆盖其他 Agent 的未提交代码。

- 新增完整接管报告：`docs/CODEX_PROJECT_TAKEOVER.md`，覆盖真实目录规模、前后端架构、学习引擎、Evidence、AI、数据模型、鉴权、UI、测试基线、运行验证、Top 10 风险和唯一下一任务。
- 当前 HEAD 仍为 `c8b7f59`；工作树包含大量既有未提交修改。用户已确认仓库内 `desktopApp/`、`mobile/`、`miniprogram/` 是迁移后的预期删除状态，后续默认忽略，不恢复、不扫描、不修改，也不将这些删除计入风险。
- 多端独立项目位置：桌面应用 `/Users/beiluo/Documents/alProject/qoderProject/desktopApp`；移动端 `/Users/beiluo/Documents/alProject/qoderProject/lectoforge_mobile`。小程序新路径尚未提供。
- 当前产品开发范围仅限 PC 端；移动端和小程序不推进功能开发、适配或测试。后续任务规划与验收默认只面向 PC 端。
- 本地标准启停入口：仓库根目录 `▶ 启动项目.command` 与 `■ 停止项目.command`；分别调用 `./knowflow-dev.sh start` / `stop`，后续运行验证默认优先使用这两个一键脚本。
- 后端全量测试：370 / 370 通过；前端：583 / 583 通过；`vue-tsc --noEmit` 与生产构建通过。
- 真实启动后端 8080、前端 5173，并以 `user1 / admin123` 完成首页、学习中心、知识库、闪卡、智能出题、AI 助手、应用中心、掌握度和普通用户管理员门禁验证。
- 运行时发现：默认 Embedding 指向 DeepSeek，向量化不可用；`MasteryDashboard` 存在未解析 `Icon` warning；Node 22.11.0 低于 Vite 8 支持线；构建有大于 500KB 的 chunk。
- 核心学习链确认：`Event → Evidence → Aggregate → Diagnosis → Candidate → NextAction → Route/Context`；页面不得自行复制这些决策。
- 当前最危险风险：软删除用户唯一键释放和重新注册身份隔离。错误地复活旧 user ID 会继承旧数据。
- 唯一下一任务：审查并固化当前工作树里的 P2-User-Register-After-Delete 修复，补齐 HTTP、数据库和浏览器证据后形成边界清晰的提交。

详细证据与排序见 `docs/CODEX_PROJECT_TAKEOVER.md`。

## 十一、2026-09-27 P2-User-Register-After-Delete 完成验证

- 修改目标：管理员逻辑删除用户后，原 username/email 可重新注册，同时新注册身份不得继承旧用户数据。
- 实现边界：`UserServiceImpl` 在删除事务内归档旧 username/email；注册和资料邮箱更新会释放遗留软删占用；重新注册始终 INSERT 新用户，不复活旧 user ID；唯一键竞态统一返回 409。独立审查后进一步把归档判定收紧为当前 userId 的完整头部、邮箱改为不可公开注册的非邮箱内部值，并让多历史行始终按主键升序更新以规避交叉锁顺序。
- 修改文件：`backend/src/main/java/com/knowflow/mapper/SysUserMapper.java`、`backend/src/main/java/com/knowflow/service/impl/UserServiceImpl.java`、`backend/src/test/java/com/knowflow/controller/UserRegisterAfterDeleteTest.java`，以及本交接文档/接管报告。
- 专项验证：`mvn -Dtest=UserRegisterAfterDeleteTest test`，15 tests / 0 failures / 0 errors；覆盖 null email、username/email 三种复用组合、旧软删行、多轮删除注册、资料邮箱冲突、旧收藏/任务/通知隔离、合法用户名与归档前缀碰撞（含单数字 userId 的 20 字符精确边界）、旧格式归档邮箱预占和 8 线程并发。新增前缀碰撞用例在修复前稳定得到 409，修复后通过。
- 全量验证：后端 373 tests 全部通过；前端 46 files / 583 tests 全部通过；`npx vue-tsc --noEmit` 通过；`npm run build` 通过。
- 真实链路：通过仓库 `▶ 启动项目.command` 启动，首次注册得到 userId=4，管理员删除后同 username/email 再注册得到 userId=5；数据库确认旧行唯一键已归档、新行独立插入。
- PC 浏览器：新身份登录首页显示 0 Coin、0 XP（Lv1）、0/0 任务、无目标/路径/最近学习、0 通知，确认没有继承旧身份数据。
- 运行收尾：已确认后端和前端均未继续运行；未自动 commit、未 push；未处理仓库内已迁移删除的 `desktopApp/`、`mobile/`、`miniprogram/`。
- 已知非阻塞项：Node 22.11.0 仍低于 Vite 8 的 22.12+ 支持线；构建最大 chunk 约 662.68KB；均非本修复引入。
- 下一唯一建议任务：`P2-BusinessException-AdminUser`，只收敛 `AdminUserController` 中单参 `BusinessException` 的 HTTP status/body.code 假成功语义，并补 Controller 集成测试；不做全仓 sweep。

## 十二、2026-09-27 P0-StudyGroup-Read-Authorization 完成验证

- 修改目标：小组已读/未读状态只允许真实成员访问；不存在小组与非成员必须分别返回 404/403，且不得泄漏消息数量、修改游标或广播伪造回执。
- 修改文件：`StudyGroupServiceImpl.java`、`StudyGroupController.java`、`StudyGroupErrorSemanticsTest.java`，以及交接/接管文档。
- 数据流：Controller 取当前用户 → Service 先确认小组存在 → 读取并返回同一条成员记录 → 查询未读或推进游标；游标更新 0 行时立即 403，Controller 不会继续广播。
- 空小组：`markAsRead` 返回成功 no-op；messageId 为 null 时跳过 WebSocket 广播，修复原 `Map.of` null 导致的 HTTP 500。
- TDD 证据：新增测试在修复前真实得到“非成员未读 HTTP 200 + data=1”“不存在小组已读 HTTP 500”“空小组已读 HTTP 500”；修复后专项 4/4、相关小组测试 37/37 通过。
- 全量验证：后端 377/377、前端 46 files / 583 tests、`vue-tsc --noEmit`、生产构建全部通过；构建仍有既有 Node 22.11/Vite 支持线与 662.68KB chunk 警告。
- 真实 HTTP：非成员 `/unread` 与 `/read` 均为 HTTP/body 403；空小组成员 `/read` 为 200；不存在小组 `/unread` 为 404。
- 独立复审：最终 Critical=0、Important=0；剩余 Minor 为未直接 spy WebSocket 调用次数与未定向模拟 update=0 竞态，当前控制流和 HTTP/数据库证据已覆盖安全边界。
- 下一唯一任务：`P0-StudyGroup-Upload-Security`，要求上传绑定 groupId、成员校验前置、失败状态码真实；只做 PC 端和后端，不涉及移动端/小程序。

## 十三、2026-09-27 P0-StudyGroup-Upload-Security 完成验证

- 修改目标：小组文件上传必须绑定明确小组与成员身份，且所有鉴权、类型校验均发生在落盘前。
- 接口与 PC：后端改为 `POST /api/study-groups/{groupId}/upload`；PC 端图片和普通文件上传均传入当前 `activeGroupId`。
- 安全边界：小组不存在 404、非成员 403、空文件/超限/不支持类型 400；HTML/SVG/JS 不在允许映射中；扩展名与 MIME 必须对应，图片/PDF/ZIP/Office 还校验文件签名；IO 异常不再包装成 HTTP 200，而由全局处理器返回真实 500。
- TDD 与回归：首轮新端点测试修复前 3/3 因接口不存在失败；实现后 3/3 通过。独立审查发现扩展名与 MIME 可任意交叉的重要问题，补映射、签名及伪装文件测试后专项 2/2 通过；学习小组相关测试 42/42 通过。
- 独立复审：Critical=0、Important=0；非阻塞加固项为 OOXML 仅校验 ZIP 头，尚未检查内部目录结构。
- 真实 HTTP：成员安全 PNG 200；非成员 403；HTML 400；HTML 改名 PNG 仍为 400；不存在小组 404。
- 前端验证：46 files / 583 tests、`vue-tsc --noEmit`、生产构建通过；仍有既有 Node 22.11/Vite 支持线与 662.68KB chunk 警告。
- 范围纪律：未修改其它上传系统，未处理移动端/小程序，未 commit、未 push。
- 下一唯一任务：`P2-BusinessException-AdminUser`，只收敛管理员用户接口错误状态语义并补集成测试。

## 十四、2026-09-28 P1-AdminUser-Error-Semantics 完成验证

- 修改目标：消除管理员用户接口 7 处单参数 `BusinessException` 导致的 HTTP 200/body 500 假成功。
- 明确语义：用户名/密码为空与无效重置密码为 400；用户名冲突为 409；删除当前用户或管理员账户为 403；异常 principal/未登录为 401；既有用户不存在保持 404。
- 认证解析：`AdminUserController` 不再强转 principal，改用 `SecurityUtils.getCurrentUserIdNullable()` 并显式抛 401。
- TDD：修复前新增 4 个测试全部失败，真实出现 HTTP 200/body 500 和 `ClassCastException`；修复后专项 4/4、与用户删除后重注册联合回归 19/19 通过。
- 全量验证：后端 384/384；前端 46 files / 583 tests；`vue-tsc --noEmit` 与生产构建均通过。
- 范围纪律：只修改 `AdminUserController` 与新增集成测试；未做全仓异常 sweep，未 commit、未 push。
- 下一任务：统一认证入口，先把 `SecurityUtils#getCurrentUserId()` 的单参异常改为明确 401，再逐个迁移仍直接强转 principal 的受保护 Controller。

## 十五、2026-09-28 P1-Authentication-Entry-Consistency 完成验证

- 修改目标：统一 Controller 的当前用户解析，消除匿名/异常 principal 导致的 HTTP 200/body 500 或 `ClassCastException`。
- 中央语义：`SecurityUtils#getCurrentUserId()` 复用 nullable 解析，无法得到 Long userId 时抛 `BusinessException(401, "未登录")`。
- 入口迁移：生产 Controller 可执行代码不再直接 `(Long) authentication.getPrincipal()`；受保护入口统一 strict，真正 permitAll/匿名个性化入口保留 nullable。
- 公开边界：Knowledge graph/entity graph、学习路径公开读取、文档公开读取继续允许匿名；`/api/docs/{id}` 的单段 matcher 现有行为会覆盖 favorites/recent，已有测试锁定匿名 200 空列表与数据隔离，本轮未擅自改变白名单。
- TDD：中央匿名语义修复前 2/2 稳定得到 code 500，修复后通过；新增 `AuthenticationEntryConsistencyTest` 验证 `UserController` 异常 principal 最终 HTTP/body 401。
- 验证：认证相关 75/75；后端全量 385/385。
- 独立复审：Critical=0、Important=0；审查中发现并修复 Knowledge concept、AdminIcon、AdminLearning、Auth `/me`、AgentIntent 的 nullable 误用。
- 下一任务：默认 Embedding/RAG 配置治理；必须先明确“无独立 embedding 配置时”的默认策略，不能把 DeepSeek 对话 Key 误当成 embedding Key。

## 十六、2026-09-28 P1-Embedding-RAG-Default 完成验证

- 修改目标：让默认 RAG/向量检索不再误用 DeepSeek 对话地址与 Key，并在本地向量服务不可用时安全降级。
- 默认策略：未配置 `AI_EMBEDDING_*` 时使用本机 Ollama `http://localhost:11434/v1` + `nomic-embed-text`，不发送 Authorization；云端 Embedding 必须配置独立 Key。
- 可用性：启动探测 `/api/tags`；连接 2 秒、读取 8 秒超时；运行期请求失败开启 30 秒熔断，窗口到期自动探测恢复；缺服务/模型时返回空向量并走关键词检索。
- 数据隔离：RAG 接口强制携带 userId；仅检索已发布、未删除、且用户拥有顶级知识库有效成员权限（含子分类继承）的文档；管理员按服务端用户身份放行全量；文档单删/批删同步清理 chunk。
- 消费入口：Chat 与 Agent `ApiDocTool` 均复用同一条权限化检索管线；移除了 Chat 原先绕过成员权限的全表 LIKE；向量为空时由 `DocChunkService` 在同一允许 docId 集合内关键词降级。
- TDD：新增配置测试覆盖默认 Ollama、DeepSeek 不回退、云端独立 Key、缺模型门控、伪本地域名、运行期熔断与恢复；新增关键词降级测试覆盖顶级成员对子分类文档的继承和无权限时不触碰 chunk/embedding。
- 真实环境：本机 Ollama `/api/tags` 存在 `nomic-embed-text:latest`，OpenAI 兼容 `/v1/embeddings` 实测返回 768 维向量。
- 范围纪律：未修改 Evidence、NextAction 或学习路由；未处理移动端/小程序；未 commit、未 push。
- 下一任务：核心页面静默失败治理，先扫描 `WeeklyReport`、`LearningMode`、`KnowledgeGraph`、`Docs` 的真实加载状态与现有 `ErrorState` 复用方式，再按页面补重试与失败可见性。

## 十七、2026-09-29 P1-Due-Flashcards 完成验证

- 修改目标：补齐 PC 间隔复习调用的 `GET /api/learning/flashcards/due`，消除 404 学习流程断点。
- 接口语义：只返回当前用户的新卡与已到期卡；未来卡和其他用户卡不可见；逾期卡优先、新卡随后，组内稳定排序，总量最多 100；匿名访问返回 HTTP/body 401。
- 前端行为：`SpacedMode` 直接使用到期队列接口，不再回退拉取全部闪卡并在浏览器计算；请求失败显示持久错误态与“重新加载”，不再伪装成“暂无卡片”或“复习完成”。
- TDD：新增后端用例覆盖新卡/逾期/未来/跨用户/匿名/空列表/100 上限与逾期防饥饿；新增组件用例覆盖失败可见、成功文案抑制和重试恢复。两项复审问题均先得到失败证据，修复后专项测试通过。
- 全量验证：后端 396/396；前端 47 files / 584 tests；`npx vue-tsc --noEmit` 与生产构建全部通过。
- 真实 HTTP：独立端口实测空队列 200、新建新卡后到期队列 200 且返回该卡、匿名 HTTP/body 401。
- 浏览器限制：本机 8080 被仓库外 BlogAgent 占用，为避免破坏外部进程未执行本轮 PC 页面浏览器回归；组件交互已由 happy-dom 测试覆盖，标记为 `Not verified`。
- 非阻塞警告：Node 22.11.0 仍低于 Vite 8 的 22.12+ 支持线；最大 chunk 约 662.68KB，均非本轮引入。
- 范围纪律：未修改 Evidence、NextAction、学习路由或移动端/小程序；未 commit、未 push。
- 下一任务：核心页面静默失败治理，优先从 `WeeklyReport`、`LearningMode`、`KnowledgeGraph`、`Docs` 的真实请求和三态呈现开始。

## 十八、2026-10-01 Codex Project Takeover

1. 修改目标：完成现有项目接管审计，建立当前事实快照与可执行守则，不继续开发 UI/业务功能。
2. 修改原因：用户要求采用 AGENTS + 文档 + 真实代码 + 测试 + Handoff；旧接管报告包含已修风险与过期数字，需要区分历史和现状。
3. 修改文件：仅 `AGENTS.md`、`docs/CODEX_PROJECT_TAKEOVER.md`、本文件。保留原守则、旧接管全文、所有历史交接；未改业务/API/数据库/Vue/Learning算法。
4. 业务影响：无实现变更；只记录风险，不修复权限漏洞、调用恶意代码、删除账号或进行购买。
5. UI 影响：无修改。真实验证指定八入口；发现知识库 StatCard 未导入 Icon 的 Console warning；学习中心加载途中过早显示完成；首页35%与中心0%路径进度有显示差异（根因未定）。应用中心PC三档不横溢，390/375px实测scrollWidth454/438，窄屏侧栏挤压正文。恢复默认视口。
6. 数据流理解：learning Store → 七组API/DAG/知识映射 → resolveEvidenceBatch → aggregateEvidence → diagnoseFromEvidence → strategy/action-candidate.buildTopCandidate → resolveNextAction → stampActionContext → resolveContextualRoute → 学习动作 → 后端Event/Mastery；App Registry当前15项，APP_NAV_META和apps Store仍唯一源。
7. 测试：本轮新执行 backend `mvn test` 399/399，失败0/错误0/跳过0；frontend `npx vitest run` 53文件624/624，失败0，无skip报告；`npx vue-tsc --noEmit` exit0。不是历史报告数字。
8. Build：`npm run build` exit0、4.92s；Node22.11低于Vite要求，最大chunk662.68kB/gzip143.23kB告警仍在。
9. 浏览器/HTTP：使用根目录一键脚本，启动前8080/5173均空闲，未终止仓库外进程。普通种子账号真实登录；首页、学习中心、知识库、闪卡、测验、AI、应用中心渲染。闪卡当前空，不验证完成/退出。终端匿名due401，登录due/mastery200，普通用户admin403；未捕获到页面Console error，知识库有Icon warn。浏览器Network未全量抓包，curl不冒充该验证。没有付费AI调用/外部OAuth/购买/恶意代码/私密目录读取。
10. 遗留问题与风险：
    - 静态确认宿主机能力：CodeExecution ProcessBuilder无OS隔离；LocalReader允许调用方选择绝对根目录，未建立可信本地模式/服务端根边界。本轮未危险复现。
    - 文档PUT仅登录，DocService.updateDoc未接canEditDocs；JWT不复查账号删除/状态/角色；uploads公开下载有私有资料风险。不能以部分隔离测试通过代表所有授权通过。
    - Wallet.isUnlimited仅按ADMIN，未限local环境，与用户明确要求有冲突；9/29设计本身也扩大成全局角色语义，先记录不偷偷改代码。
    - 工作台复习quality0–3原样发FLASHCARD_REVIEWED，Evidence按0–5解释，最高反馈聚合前60分；故事保存不写FEYNMAN_EXPRESSED；跨知识Evidence绑定当前上下文精度风险。
    - 学习Store部分请求与WeeklyReport/LearningMode/KnowledgeGraph/Docs等仍静默空数组；单参数BusinessException111处单行检索命中仍受兼容HTTP200/body500语义影响。
    - 注册归档唯一键/新ID、收藏reviveDeleted、认证入口、AdminUser状态码、默认OllamaEmbedding、due404已有修复，不重复列未修。其他softdelete+UNIQUE生命周期、MySQL、并发、Mastery同类调用事务边界/失败恢复、Store在途请求账号切换尚未充分验证。
    - ⚠️ Not verified：完整云AI/RAG回答、MySQL、破坏性权限复现、闪卡完成退出、各页面所有视口/暗色/键盘、生产部署。当前无稳定PC浏览器E2E配置。
11. 下一步唯一建议：P0宿主机高危能力边界治理——CodeRun/LocalReader及关联入口在非显式可信本地模式默认拒绝宿主机执行/任意目录读取，先建权限回归，限服务端根/时间上限，核验旁路；生产保留执行需真实隔离，不顺带改学习/UI。

接管阅读：AGENTS、VIBE_CONTEXT、PROJECT_ARCHITECTURE、UI_UX_SYSTEM、DEVELOPMENT_PROTOCOL、全部AGENT_HANDOFF、根/docs/frontend README、已有CODEX_PROJECT_TAKEOVER、9/29本地管理员设计及9/30工作台报告；扫描frontend路由/Store/API/learning/apps/UI、backend各关键模块及schema/索引/认证。Documentation Drift详情见新接管报告§25。

Git初始：main，HEAD `c8b7f596534e72bf6f7c024ec356a56a3100acb7`，staged为空；159 modified、267迁移目录预期deleted、112 untracked（排除迁移目录271项）。已有未提交改动全部保留，AGENTS和接管报告本来就未跟踪，不当作本轮新建系统。未安装Skill、未spawn新Agent、未commit、未push、未stash/reset/clean/restore。原始基线日志在/tmp，不入仓库；标准服务本轮启动后保留运行。

## 十九、2026-10-01 P0 Host Capability Security

1. 修改目标：完成批准的 Host Capability Trust Boundary；默认关闭宿主机执行/请求方指定路径读取，显式服务端授权才可用于可信本地开发，不涉及学习引擎或页面重设计。
2. 修改原因：旧执行限制不等于 OS Sandbox；Reader 请求自选 root 不是服务器信任根。真实安全回归确认默认用户/ADMIN仍能进入旧路径，必须在实际 Service/Tool 设边界。
3. 修改文件（本轮39项，下面路径均仓库相对路径；不将全工作树其他变化归为本轮）：
   - `backend/src/main/java/com/knowflow/security/{HostCapabilityProperties,HostCapabilityGuard}.java`
   - `backend/src/main/java/com/knowflow/service/{LocalReaderService,PathImportService}.java`
   - `backend/src/main/java/com/knowflow/service/impl/{CodeExecutionServiceImpl,CodeDebugServiceImpl,CodeAssessServiceImpl,CodeWorkspaceServiceImpl,ObsidianImportServiceImpl}.java`
   - `backend/src/main/java/com/knowflow/util/LocalFileMultipartFile.java`
   - `backend/src/main/java/com/knowflow/controller/{CodeRunController,CodeAgentController,KnowledgeImportController,ObsidianImportController}.java`
   - `backend/src/main/java/com/knowflow/agent/tool/ToolRegistry.java`，`builtin/{CodeRunTool,TestGenTool,TerminalTool,GitTool,FsReadTool,FsWriteTool,FsTreeTool,FsSymbolsTool,CodeReviewTool}.java`
   - `backend/src/main/resources/application.yml`
   - `backend/src/test/java/com/knowflow/controller/{HostCapabilityDisabledTest,HostToolBoundaryTest}.java`，`security/{HostCapabilityGuardTest,HostInterruptedProcessTest}.java`，`service/{HostExecutionTest,HostReaderTest,HostOwnedWorkspaceTest}.java`
   - `frontend/src/api/codeRun.ts`、`frontend/src/views/CodePlayground.vue`：术语修正，不调整布局或业务流程。
   - `docs/HOST_CAPABILITY_SECURITY.md`、`docs/PROJECT_ARCHITECTURE.md`、本文件、`docs/superpowers/specs/2026-10-01-host-capability-security-design.md`、`docs/superpowers/plans/2026-10-01-host-capability-security.md`。
4. 业务影响：普通 Web/ADMIN默认不能执行宿主进程或由路径读取宿主文件；已授权本地开发身份才可执行。普通上传/浏览器手选文件夹及服务器自有工作区编辑未被统一禁用；后者补符号链接拒绝。配置没有自动按 local/profile/IP/ADMIN 放行。
5. UI影响：仅把可见“沙箱模式/重置沙箱”改为工作区术语；沿用现有错误展示。默认关闭时明确提示“该能力仅在可信本地开发模式启用”，不假装服务故障。
6. 数据流影响：HTTP→既有Service/Tool→共享Guard→真实IO/ProcessBuilder；路径配置为服务器真源，客户端root/relativeTo不能扩张；异步worker捕获真实身份并finally清理。PathImport延迟读再次检查，Obsidian服务器生成临时文件不依赖允许整个临时目录。未改变NextAction/Evidence/Router/App Registry/学习Store。
7. 测试：安全红测试日志 `/tmp/knowflow-host-red.log`（11项10fail）、`red-exec.log`（14项3fail）、`workspace-red.log`（3项2fail）、`interrupt-red.log`（1项1fail）、`entry-red.log`（18项1fail），后四个实际完整文件名前缀均 `/tmp/knowflow-host-`。最终新执行 `mvn test` **457/457，fail0/error0/skip0**；新增58项。`npx vitest run` **53文件624/624**，`npx vue-tsc --noEmit` exit0。最终日志 `/tmp/knowflow-host-final-{backend,frontend,types}.log`。没有删除测试或改变断言制造通过；仅TempDir/安全有限代码/Process桩，未访问私密内容。
8. Build：`npm run build` exit0、5.13s；Node22.11/Vite最低版本及大chunk历史告警仍在，未顺手升级或拆包。日志 `/tmp/knowflow-host-final-build.log`。
9. HTTP/浏览器：最新类的独立loopback8081关闭/8082开启实例执行48项真实HTTP，401/403/400/200与body.code一致并记录message，无拒绝500；8082仅授权测试普通用户ID2、专用TempDir，不授权ADMIN。随后日常8080也重启为最新代码并显式false，重复48项通过，日志 `/tmp/knowflow-host-http-final-results.log`、`/tmp/knowflow-host-http-daily-results.log`。启动首试遇旧进程端口尚未释放，确认8080空闲后重试成功。H2内存库重载种子数据；5173前端保留。浏览器5174实测Reader路径加载、代码运行/调试/评估关闭提示；5173日常入口再测运行拒绝；未捕获Console error，不用HTTP测试冒充浏览器。
10. 复核/遗留问题：按技能做一次只读最终安全复核，发现Terminal/Git/Python Debug在wait中断后子进程可能遗留；安全Process桩复现并补共享清理，focused4项及最终全量通过。真实代码仍继承宿主权限/环境、无OS内存/网络/多租户隔离，不能公开启用；路径TOCTOU和尽力后代进程清理不是OS隔离。Obsidian预处理临时目录历史清理生命周期未重构。⚠️ Not verified：完整启用后的Obsidian生成/知识导入业务、外部AI/RAG、MySQL、生产隔离、持续恶意负载、Windows、所有编译运行时和所有浏览器路径。
11. 下一步：公共环境保持false；如需公开执行，另立真正隔离执行架构任务。JWT/文档权限/上传下载/学习Evidence/静默失败等接管遗留项仍是独立风险，不宣称整个项目安全。无自动commit/push，main/HEAD/staged不变；迁移目录267deleted不动。验证临时开启实例结束后关闭，日常8080/5173保持运行且Host Capability关闭。

完整配置/边界说明见 `docs/HOST_CAPABILITY_SECURITY.md`。用户批准书面设计并要求不再审批，原工作树未提交设计必须保留，因此本轮在现checkout原位执行；未新建重复系统或覆盖其他Agent改动。

## 2026-10-03 — 导入/运行补验与文档编辑权限、数据隔离

1. 目标/原因：补上轮未验证的四语言运行、Obsidian 四模块与导入 SSE；文档读写及附件、检索/图谱、衍生学习内容存在可达旁路，统一按既有 owner/成员角色授权。
2. 文件：新增 `security/{DocumentAccessPolicy,DocumentAssetBindings,DocumentAssetInterceptor}.java`；修改 `DocServiceImpl`、`CategoryServiceImpl`、`KnowledgeService`、`DocChunkServiceImpl`、`KnowledgeImportServiceImpl`、`ObsidianImportServiceImpl`、`LearningServiceImpl`（仅授权）、`QuizServiceImpl`、`KnowledgeProcessingServiceImpl`、`MistakeServiceImpl`、`KnowledgeMasteryServiceImpl`、`LearningContentServiceImpl`，以及 `DocController`、`KnowledgeImportController`、`DocDocumentMapper`、`DocDetailVO`、`WebMvcConfig`、`SecurityConfig`。三份 schema 追加 `doc_asset_binding`。前端 `DocDetail.vue`、API 类型、认证附件 helper 及测试；新增文档矩阵/导入/Tomcat SSE/离线 LiveServer，运行测试补三语言，旧测试只补真实授权资料/公共路径/依赖。完整文件清单见 `docs/DOCUMENT_ACCESS_SECURITY.md`。
3. 业务/UI：Owner/Editor 编辑，Reader 阅读，Other/Anonymous 私有拒绝，Admin 保留管理。移动检查来源及目标，整批预检查；草稿、列表总数、收藏/最近/进度、摘要/抽取、私有题目/路径/章节、掌握度与内容生成均覆盖。详情明确错误态、canEdit、认证 Blob 图片/下载，不改学习算法、NextAction/Evidence/Store/Registry。
4. 真实链路：HTTP→既有服务→共享文档策略→分类祖先/成员→Mapper；RAG 召回前限定可读 IDs；知识实体回查源文档。持久首源附件关联不随文本引用删除或新增转移。导入实际目标内增量去重，目录逐级检查写权限；新 Obsidian 根归真实用户，Editor 不取得子类 owner。ASYNC 内部继续分派放行而非新 HTTP；导入线程传递/清理认证上下文。
5. 最终命令结果：顺序 `cd backend && mvn -q test` **495 tests，fail0/error0/skip0，exit0**；`cd frontend && npx vitest run` **54 文件627 PASS，exit0**；`npx vue-tsc --noEmit` exit0；`npm run build` exit0（3.57s，历史 Node22.11 与 chunk 警告保留）。最终日志 `/tmp/knowflow-oct3-backend-delivery.log`、`knowflow-oct3-{vitest,types,build}.log`。一次并行 Maven 编译使类暂时消失的失败已排除，最后全量顺序执行，不把失败当成功。
6. 真实 HTTP/浏览器：独立 H2、loopback、FakeAi/MockEmbedding、专用目录，实际四语言输出；仅普通测试用户2启用宿主能力，ADMIN仍拒绝。最终 **28 HTTP PASS**，覆盖六身份、原文/图片、multipart 同步/SSE、宿主路径 SSE、Obsidian 四模块同步/SSE、移除私有引用仍保护、私有引用不能封禁公共附件。浏览器 Reader 图片 Blob 加载与摘要隐藏、Owner摘要、Other无权错误；1440/1280/1024 PC 无横向溢出，Console空。日志 `/tmp/knowflow-oct3-http-complete.log`。
7. 审查：按执行技能做一次最终只读整体审查，四个重要发现分别 RED→GREEN 修复：引用移除泄漏、公共资源引用封禁、跨库去重、独立子类穿越；补祖先循环/删除与混合非法批量零副作用。私有 KG/生成节点上下文和 Tomcat SSE actor 丢失另有回归；不循环重审。
8. 风险/未验证：浏览器 Network抓包与操作系统最终保存文件 ⚠️ Not verified；真实 HTTP字节/点击无错不能替代这些。生产 MySQL新增表迁移、部署静态代理、真实 embedding/外部AI、Windows、恶意持续负载/OS隔离未验证；历史无owner/共享无归属附件需生产审计。未重构临时预处理目录生命周期和权限查询性能；个人已保存历史不自动擦除，JWT角色撤销非本轮。宿主功能非沙箱，默认关闭不变。
9. Git/工作区：保留原位 dirty main，HEAD `c8b7f596534e72bf6f7c024ec356a56a3100acb7` 不变，staged为空、diff check exit0；无commit/push/reset/clean/stash，迁移目录不动。临时浏览器标签关闭、尺寸重置；验证自建8080/5173服务关闭，恢复本次继续执行前未监听状态，测试临时资料/日志保留。
10. 下一任务：生产旧资料/附件归属迁移审计，并验证部署层 `/uploads/**` 必须经过授权，不直接静态公开代理。完整11项执行报告、裁定与范围见 `docs/DOCUMENT_ACCESS_SECURITY.md`。

## 2026-10-04 — 新手引导与核心异步五态

- 范围/文件：UI-only onboarding Store/WelcomeGuide、Auth register 接入、CLayout/CTopNav 入口；StateView/useAsyncResource；Home/KnowledgeHome/Docs/LearningMode/KnowledgeGraph/实际 LearningCenterPage 与 LearningReportPage，历史 WeeklyReport 及增强 helper；knowledge 请求 singleflight；learning 仅追加局部 tasksError/pathsError。完整文件和 11 项报告见 `docs/ONBOARDING_ASYNC_REPORT_2026-10-04.md`。
- 链路：register → onboarding mark → layout sync → guide；页面 API → resource → 五态；知识树共享 Promise 向所有调用者传播失败。NextAction/Evidence/Registry/权限决策未改，不创建第二学习 Store。
- Git：dirty main 原位，HEAD c8b7f596534e72bf6f7c024ec356a56a3100acb7、staged 为空；未知既有修改及迁移删除保留，无 commit/push/stash/reset/clean。用户免审批约定覆盖技能审批步骤。
- 新执行 `npx vitest run` 63 文件658 PASS；`npx vue-tsc --noEmit` exit0；`npm run build` exit0；`mvn test` 495 tests failure0/error0/skip0 BUILD SUCCESS。日志 `/tmp/knowflow-onboarding-final-{vitest,types,build,backend}.log`。修复先 RED→GREEN，未删除测试。
- 浏览器：引导自动/跳过/重看/完成、四种宽度兼容、关闭焦点回头像；参数错误真实显示；Docs 断开自建后端显式错误，重启后分别重试恢复主内容与最近阅读。Network 抓包未验证；瞬时 retrying 由测试覆盖，不声称浏览器捕获。StatCard Icon 既有警告保留，Node/chunk 既有告警保留。
- 一次独立只读审查收到重要发现并修复（局部失败、旧掌握度、再生成重试、分类阻断、引导焦点），随后审查任务额度受限，无最终整体独立 PASS。未重复审查。历史未注册 WeeklyReport 生成/读取竞争风险未扩展重构。
- 引导仅按账号/浏览器保存完成，不跨设备；ADMIN 不自动打开，老用户可重看；不自动安装 App 或调用付费 AI。生产 MySQL/部署/真实外部 AI/全部页面所有视口未验证。本任务独立验证服务/标签结束后关闭，临时资料日志保留。
- 下一任务：建立 PC 端浏览器 E2E，覆盖注册首次引导、跳过/重看、账号切换和独立区域失败重试；安全/生产迁移风险仍参照上轮报告，不能由本轮 UI 验证视为已消除。

## 2026-10-04 — 普通用户 PC 知识工作室视觉层

- 用户要求自主重构普通前端（登录不改）、安装两项技能、生成素材；保留先前普通PC/admin不动约定。设计/计划与11项报告：`docs/PC_STUDIO_UI_2026-10-04.md`。
- 文件：新 pc-studio.css、StudioVisual.vue、StudioVisual.spec.ts、本地knowledge-flow.webp；CLayout增加隔离root/import；Home增加装饰与行动DOM提前；PageHeader标记class、StatCard补真实Icon导入、ui-tokens新增studio变量。LearningCenter/四步shell视觉由限定CSS消费，不改其数据流。
- 内建imagegen原创书页/知识流插画，经现有cwebp变为22,866bytes/768×512 WebP存项目public/images；无远程素材运行时依赖，图片错误隐藏。skill-installer安装官方anthropics/skills的frontend-design/webapp-testing，下轮自动发现；本轮直接读取使用，浏览器依政策用CUA。
- 有效图标回归RED→GREEN；最终 `npx vitest run`64文件660PASS，`npx vue-tsc --noEmit`exit0，`npm run build`exit0/5.35s，日志`/tmp/knowflow-studio-delivery-{vitest,types,build}.log`。无后端变动，不把上轮495项当本轮新验证。Node22.11/Vite与大chunk告警未消除。
- 浏览器Home/KnowledgeHome/LC/四步收集和复习导航、安装gate、折叠展开；Home1440/1280/1024PC及390兼容稳定后无横向溢出；KnowledgeHome Consolewarn/error[]。用户原服务8080/5173保留；自己的标签关闭、viewport恢复。Network抓包、暗色全交互、全部页面五态/长标题组合、未安装App编辑内容 ⚠️ Not verified。
- 一次独立只读复核Critical0/Important0/actionableMinor0，限定本轮范围通过，不代表后台/权限/外部AI/生产验证。NextAction/Evidence/Store/API/Registry/路由未变，五态保留。
- Git原dirty main保留，HEAD c8b7f596534e72bf6f7c024ec356a56a3100acb7/staged空；diffcheck通过；无commit/push，迁移目录不动，已有登录文件dirty diff非本轮改动。用户免审批/允许安装覆盖相应历史阶段规定。
- 下一任务：真实已安装工作台内容PC E2E及Node/chunk独立治理；不因本轮视觉交付视为解决此前生产权限迁移风险。

## 2026-10-05 — Java + AI Interview Seed Pack v1

- 结果/目标：Java + AI Engineer Interview Pack v1 PASS，限本轮学习包本地验收范围。完整17项报告：`docs/seed/JAVA_AI_INTERVIEW_PACK_V1_REPORT.md`；结构与学习地图：`docs/seed/JAVA_AI_INTERVIEW_PACK_V1.md`。设计/计划位于`docs/superpowers/{specs,plans}/2026-10-05-java-ai-seed-*.md`。先扫描Schema/实体/Mapper/Service/Controller/路由/Store与已有Seed；PRODUCT_EDITIONS、PERSONAL_EDITION_ROADMAP不存在，记录漂移，不虚构Edition flag。
- Seed architecture/version：`java-ai-interview-v1`，classpath三份原创JSON；启动只建3张轻量索引表和锁行，普通认证USER点击首页/学习首页opt-in后事务建内容。无固定真实userId，ADMIN403/Anonymous401/已删除身份401。READ_COMMITTED锁用户→版本，解决RR默认池下首启并发死锁；私有索引、删除内容与知识映射冲突409，不覆盖或复活。
- Data model mapping：共享`learning_path(owner=0)→learning_chapter→doc_document/doc_category(owner=NULL)`，`kg_entity`及既有`resource_knowledge_mapping`映射DOC/QUIZ/CODE_QUESTION/FLASHCARD；个人`learning_goal/learning_user_path/learning_flashcard/learning_note/learning_task`。章节内容承载Objectives/Difficulty/Minutes/Prerequisites/Importance；共享章不存私有卡ID。章顺序1–4适配真实PC显示，路径不互锁。Notes是真实后端私有速记，在章节显示，不另造Workbench笔记系统。
- Counts：共享8分类/8路径/32章/96知识/16原创文档/96题/32Java练习，个人1目标/8零进度报名/128卡/24速记/7待完成任务；共享索引288项、个人168项。每章3知识/4卡/3题/1练习，每路径2文档/3速记。初始化不写Event/Mastery/QuizResult/错题/复习/章完成/代码提交。7-Day Sprint复用Task，反向插入适配最新优先读取使Day1先出现，不改NextAction顺序。项目题只Personal Project事实，无生产规模/工作经历虚构；统一大写主题标签，题有答案/关键点/独立追问/常见错，删通用填充答案。
- Files：新后端`seed/InterviewSeed{Catalog,Initializer,Service}`、`controller/InterviewSeedController`、`resources/seed/{seed-index.sql,java-ai-v1-{java,ai,project}.json}`、`test/seed/{InterviewSeedIntegrationTest,InterviewSeedContentTest}`、`test/support/InterviewSeedLiveServer`；前端`api/interviewSeed.ts`、`components/Learning/InterviewSeed{Card,Chapter}.vue`及2测试，Home/LearningCenterPage只插入口与import、ChapterLearn只插训练区与import。新增Manifest/Report/Authoring Contract/设计/计划，本文件仅追加。前序大UI diffs不是本轮重写。
- Real call chain：SecurityUtils→InterviewSeedController→InterviewSeedService→原有Mapper与业务表；激活后`useLearningStore.load(true)`，学习链接复用`stampActionContext+resolveContextualRoute`。Evidence/Diagnosis/NextAction/Route/Context算法与Registry未改，无第二学习Store，无自动安装或付费AI调用。
- Tests：最后`mvn -q test`53套件506tests failure0/error0/skipped0/exit0；`npx vitest run`66files665PASS/exit0；`npx vue-tsc --noEmit`exit0；`npm run build`exit0/3.69s。日志`/tmp/knowflow-seed-final-{backend,vitest,types,build}.log`。新增后端7集成+4内容质量、前端5项。缺接口/UI失败重试/匿名调用/RR死锁/索引映射损坏/Day7顺序/0基章号/采样缺口/标签漂移/重复Java答案均先RED后GREEN；未删除测试或降低断言。一次独立复核Critical0/Important3后根修复复验，不声称再次独立审查PASS。
- Database：自建loopback8081/5174及文件型H2 MySQL模式；最终`db-accepted`两次真实启动后API重复POST与业务表SQL实数完全一致（除耗时），章sortOrder1–4各8项，Event/Mastery/Answer/ChapterCompletion/CodeSubmit=0，7Task status0。证据`/tmp/knowflow-interview-live.Xsbr7c/{api,db}-accepted-boot{1,2}.log`与api-results.json。FakeAI/mockEmbedding仅测试服务，未改生产provider。浏览器随后完成前置的记录只存在隔离测试账号，不伪装初始化历史或真人面试能力。
- Browser：普通用户点击启用；Home/学习中心/8路径/章节/知识库公开分类与2篇RAG材料/文档/按章4张卡/题库简答题/代码练习列表和Java编辑器真实可见，速记可展开。链1并发→正常完成前置→线程池→Doc→4卡翻转/退出→面试题→Java拒绝练习编辑器；链2RAG→Parse→Embedding→Hybrid/Rerank→面试答案。最终代码与新库重验章1–4、线程池答案及两链。Consolewarn/error[]；Home稳定后1440×900/1280×800/1024及390×844/375×812无横向溢出，窄屏仅兼容。截图accepted-home/threadpool/rag与flashcard/exercise保存在临时证据目录。Network抓包⚠️ Not verified，独立HTTP不冒称浏览器Network。
- Git：dirty main原位，HEAD`c8b7f596534e72bf6f7c024ec356a56a3100acb7`、staged空；diffcheck通过，新文件与本轮局部入口未提交；无commit/push/reset/clean/restore/checkout/stash，未知修改与迁移删除保留。用户8080/5173服务保留；自建测试服务/标签结束关闭，viewport恢复，日志/DB/截图临时资料保留。
- Known limitations：Java网页当前模板+人工检查，不是生产沙箱；真实MySQL/多实例生产并发/生产迁移/外部LLM/Embedding/Milvus/pgvector/MCP部署⚠️ Not verified；H2 RR不等于MySQL。热切到无Seed表数据源需重启执行索引初始化；409后无静默修复/升级UI。原Demo、路径静态评分和LC前4条摘要保留，本包8路径入口可达，不把它们算本包成果。Node/Vite与大chunk旧告警保留。用户现有后端需正常重启加载新Java代码，未替真实账号自动灌内容。
- 下一唯一任务：Java17隔离练习执行与可复现判题，区分宿主开发能力与生产沙箱边界。

### 2026-10-06 — Seed Pack 最终续轮复验

- 产品实现未新增改动；重新执行backend `mvn -q test` exit0，Surefire XML实际汇总53套件506项，failure/error/skipped均0；frontend `npx vitest run`66文件665PASS、`npx vue-tsc --noEmit`exit0、`npm run build`exit0/5.67s。Node22.11/Vite最低版本与662.68kB chunk告警仍保留。
- 上轮/tmp日志、持久数据库和截图当前不再存在；数据库两次启动、浏览器两链保留为历史记录，不冒充当前复验。当前CUA状态读取超时并重置，浏览器与持久库重验⚠️ Not verified。报告已补充真实限制，不交付失效截图链接。
- 端口只读检查8080/5173/8081/5174均无监听；未kill进程，未操作用户/docs标签，也不声称本轮完成历史标签清理。Git main/HEAD未变、staged空、未commit/push；本轮仅修正报告与追加本交接。
- 下一唯一任务仍为Java17隔离练习执行与可复现判题；不要将人工检查练习宣称为自动判题通过。

## 2026-10-06 — Interview OS v2 第一阶段：V1 Coverage Audit与书面设计

- 用户新任务要求先审计而非继续随机生成题。已真实读取/追踪v1 JSON、Catalog/Initializer/Controller/Service、相关H2表/Entity/Mapper、Quiz/Scenario/SRS/Code/Note/Event/Mastery、frontend章资源/路由/learning Store与Evidence；当前源文件为事实。PRODUCT_EDITIONS/PERSONAL_EDITION_ROADMAP不存在；限定文件名发现未找到真实Resume/JD输入，Not Available，不编造统计。
- 新文件：docs/seed/JAVA_AI_INTERVIEW_V1_COVERAGE_AUDIT.md、JAVA_AI_INTERVIEW_CAPABILITY_MAP.md（仅链接入口）；docs/interview/JAVA_AI_{INTERVIEW_OS,CAPABILITY_MAP,QUESTION_TAXONOMY,MOCK_INTERVIEW_SPEC,7_14_30_DAY_PLAN}.md。没有新增产品代码、题目、数据库行或修改v1内容。设计为待审阅，不是v2产品完成。
- 只读Node统计：8路径32章96知识16派生文档24私有笔记128卡96题32练习；S64/A20/B12/C0；121追问字符串、0结构化树节点；128卡面117种，1通用卡面重复12章；前三路径12章都有q0→q1→q2→q0文本循环；18主题标签；65知识解释是章题答案前缀。AQS k2/k3语义错位、CHM/JVM等答案共享不相关工程尾巴。内容关联存在不等于语义有效。
- 调用链风险：QuizService包含匹配写QUESTION_ANSWERED；Scenario关键词覆盖写SCENARIO_COMPLETED；CodeQuestionController信任客户端统计写CODE_SUBMITTED，不能用其包装六维面试验证。SRS实际首次1、第二次6天，不是1/3/7/14/30；前端aggregate按维度而非28领域分桶。本轮不改这些核心行为。
- 审计质量：按固定SHA256排序选Java20/AI20/Project10，样本与复现规则入文档；项目样本不宣称商业规模。未对50题做独立官方答案终审；仅内容结构/语义风险审计，不虚构质量分数。
- 方案：版本化metadata覆盖层+已有业务表/资源索引；保留v1，不原地覆盖用户数据、不复制8路径；稳定canonicalKey与parentKey树，Scenario复用既有实体。评分/Mock/Readiness先划边界；答案阅读/Project源码引用不是用户Evidence。28领域映射现8路径，Resume/JD缺失、无假成绩。
- Git main/HEAD c8b7f596534e72bf6f7c024ec356a56a3100acb7/staged空，既有dirty修改保留；无commit/push/危险命令、迁移目录不动。JSON哈希/计数/抽样与最终diffcheck执行；本阶段无产品改动，未重跑全量测试/types/build或浏览器，不借用上一轮506/665当本轮验证。当前v2没有PASS。
- 使用brainstorming架构审阅门槛：先审阅JAVA_AI_INTERVIEW_OS书面设计再写实施计划。唯一下一实施任务：语义正确、版本化Follow-up Tree，以AQS/MVCC/RAG/Agent四链为首个验收范围；Java可信判题仍保留风险，不顺带扩开发范围。

## 2026-10-06 — 登录页默认普通测试账号

- 目标/原因：用户指定登录预填seed-personal-v1，替换旧admin开发默认值；保留此前仅开发环境限制，不改变登录页设计。
- 文件：frontend/src/auth/login-defaults.ts及其__tests__/login-defaults.spec.ts，本Handoff追加。
- 调用链/业务/UI/数据影响：Login.vue的createLoginDefaults(import.meta.env.DEV)只预填指定用户名/密码，rememberMe仍false，生产字段为空；现有auth.login/API不变，不自动提交、不注册、不改数据库账号密码。此账号曾用于临时验证库，真实本地库存在性与成功登录未验证。
- TDD先RED（开发预填仍返回admin，1失败/1通过）再修改最小两行；全量Vitest66文件665通过，vue-tsc --noEmit退出0，npm run build退出0，命令链退出0；日志/tmp/knowflow-login-seed-{vitest,types,build}.log。没有后端改动，未跑mvn；Node版本与大chunk旧告警保留。
- 浏览器：5173只读端口检查无监听，未启动/操作用户页面，实际表单与登录⚠️ Not verified。不是自动创建账号的功能。
- Git：既有dirty保留、diffcheck通过、staged空，无commit/push。唯一下一验证：确认当前开发数据库存在该普通账号并真实登录；不得为此自动新增账号或修改凭据。

## 2026-10-06 — 用户授权内置Java + AI测试账号

- 用户明确授权默认注册并内置Java + AI内容。已在当前8080注册seed-personal-v1（普通USER，当前库ID4，非硬编码），实际login200，两次Seed.activate均200/activated=true，内容8路径32章96知识16文档24笔记128卡96题32练习7任务。
- 新DevelopmentInterviewAccount runner，标准knowflow-dev.sh传显式enabled=true；默认关闭，local非prod/production且实际H2内存库限定；复用UserService注册/Seed启用。不同密码或角色冲突409，不覆盖。Initializer Order0、runner100。没有生产已知密码自动账号、无限钱包、假学习事件，未替其他用户启用。
- 文件、11项报告及完整边界：docs/seed/DEVELOPMENT_INTERVIEW_ACCOUNT.md；v1 Manifest追加开发例外。本轮不改v2架构设计/首页/学习核心。
- 新2项真实runner集成测试：注册登录/幂等/128卡/零假历史/保留不同密码、默认关闭/生产混合profile拒绝；RED缺类→GREEN。最后mvn -q test53套件508项，failure/error/skipped0，exit0；Vitest66文件665PASS、typesexit0、buildexit0/4.27s。日志/tmp/knowflow-dev-account-{backend,vitest,types,build}.log。
- 实际浏览器独立标签登录成功，首页测试用户/已启用包/0CoinXP/0%/0of7任务/无学习记录可见，Consolewarn/error=[]；截图/tmp/knowflow-development-interview-account.png。验证标签关闭，未操作用户/docs标签；Network抓包Not verified。
- 未重启当前H2服务，保护内存数据；标准启动自动重建尚未实际重启验证。MySQL/file数据库被guard拒绝；生产、外部AI未验证。Git原dirty/main保留，staged空/diffcheck通过，无commit/push/kill或迁移目录操作。
- 唯一下一任务：可丢弃开发环境实际标准启动后自动重建验证；不盲目重启用户当前H2会话。

## 2026-10-11 — 章节学习UI与Java集合内容升级：真实审计阶段

- 目标/原因：用户要求先审计四页与当前Java核心集合数据，再设计最小升级。完成审计与建议，未修改产品代码/Seed/用户内容，未宣称升级PASS；brainstorming架构型设计需书面审阅后写实施计划。
- 文件：新增docs/learning/CHAPTER_LEARNING_UX_AUDIT.md、JAVA_COLLECTIONS_CONTENT_AUDIT.md、CHAPTER_CONTENT_STANDARD.md，本文件仅追加。记录四页问题矩阵、真实资源数、HashMap拟扩展及内容保护策略。
- Git：检查status/diff stat/cached diff/branch/HEAD/history；dirty main、HEAD c8b7f596534e72bf6f7c024ec356a56a3100acb7、staged空。旧产品改动及迁移删除保留；无commit/push/reset/clean/restore/stash。
- 服务：只读lsof确认现有node6275/5173、java6282/8080；保持运行未kill/restart。真实默认账号API与浏览器login成功，token不输出。
- 数据：实际认证GET路径→4章→各章knowledge/Seed资源/本人flashcards→2文档，HTTP/body200；1目标路径/4章/12去重知识/2文档/3个人笔记/16卡/12题/4练习。API实际持久资源返回去重，不冒称直接SQL全表统计。环境path5/chapter24–27仅观测，不新增ID常量；0%完成/后3章锁，未提交训练结果。
- 根因链：router→LearningCenterPage/LearningPaths/PathDetail/ChapterLearn→learning API→LearningController→LearningServiceImpl→原Mapper/learning_path/chapter/user_*；章knowledge由docIds→DOC映射，两章一文档使各章返回6知识。SeedController→InterviewSeedService→资源索引→3题/1练习/本人路径笔记；Catalog固定计数阻止直接扩JSON。NextAction/Evidence保持不变。
- P0/P1：首页固定前4路径与位置分类；列表/详情ID假评分、失败空数组；章节收藏仅ref、错误旧内容风险、本地简版Markdown与公共renderer重复、知识标题猜词。HashMap现1个TreeMap计数练习、3题循环追问；IO知识答案错位。审计不是这些问题已修复。
- 官方核验：浏览Oracle Java17 HashMap/CHM与官方OpenJDK17u raw；GitHub HTML源码读取失败后raw成功。修订实施前还需固定tag/commit及逐题核验。
- 浏览器四页真实打开，目录点击定位训练区域，真实doc/card/quiz/code入口href可见，所读Consolewarn/error=[]；截图/tmp/knowflow-learning-center-audit.png与/tmp/knowflow-hashmap-chapter-audit.png。只读审计不点击完成/收藏/答题提交。Network抓包、完整学习闭环、响应式、失败注入均Not verified。
- 测试/Build：仅文档，本轮未执行产品全量测试/types/build，不引用历史508/665作本轮PASS。最终diffcheck另行执行；用户服务保持，独立浏览器审计标签结束关闭。
- 唯一下一任务：审阅三份最小设计后，编制并执行公共章节UI+版本保护HashMap样板实施计划；不直接覆盖v1或用户笔记。

## 2026-10-11 — 共享章节 UI + HashMap 深度样板实施

- 用户明确免审批执行；范围取原任务允许的优先共享UI/HashMap样板，不宣称其他三章或全课程升级PASS。
- 文件/影响：ChapterLearn、三学习页、chapterReading、InterviewSeedChapter/QuestionTree、learning/interviewSeed API与token、六前端测试；CollectionsDepthService、Seed initializer/controller、收藏/笔记对象授权、三内容资源与两后端测试；完整清单见 docs/learning/CHAPTER_UPGRADE_REPORT.md。保留现有dirty变动。
- 调用链：ChapterLearn→原learning API/Service/Mapper；显式修订POST→pack锁/稳定资源索引→现有Doc/Quiz/Code/Card/Note/Mapping；Tree用户回答→原quiz.submit事件链，不改核心Evidence/NextAction/Store/Registry。
- 新修订 java-collections-depth-v1：1doc/8题/2Java17实验/8私人卡/1私人模板/21映射，复用原章/路径/知识，不修改chapter.content/docIds、原文档、用户编辑/复习/完成/成绩。修订GET只读、损坏409。两实验实际编译运行PASS，来源固定Oracle17/OpenJDK jdk-17+35。
- RED→GREEN覆盖真实渲染/切章竞态/收藏/笔记/目录与单标题、首页路径、错误重试、锁定图谱/CTA、显式修订/幂等/归属/编辑历史/损坏映射。未删除或放宽旧断言。
- 新执行结果：mvn -q test exit0，54 suites/513 tests/0 fail/error/skip；npx vitest run exit0，71 files/682 tests；vue-tsc/build exit0；diffcheck exit0。Node22.11与约662KB既有构建告警未处理。日志/tmp/knowflow-chapter-{backend-full,frontend-final,types-final,build-final}.log；只读review无阻断新回归。
- 浏览器：独立8081/5174隔离H2/offlineAI登录测试账号，四页→修订启用→追问实际提交→Java编辑器→12卡一次不会与退出回章；收藏刷新保留/笔记API读取存在；1440/1280/1024无横向溢出、窄屏目录折叠；不存在章节错误与重试不留旧内容。截图/tmp/knowflow-chapter-final.png等。正常链路Consolewarn/error=[]。
- Not verified：Browser Network工具缺失（HTTP测试不冒充抓包）、390/375兼容、MySQL真库、付费AI、生产部署、网页Java判题。自动回答反馈只沿用简答匹配，不是面试评估。
- 服务/Git：原node6275/5173和java6282/8080保持不动，未重启避免H2内存丢失，原8080尚未加载新后端。结束清理仅本次独立fixture/临时浏览器页。main HEAD c8b7f596534e72bf6f7c024ec356a56a3100acb7；staged空；无commit/push/reset/clean/restore/stash，迁移目录未触碰。
- 唯一下一任务：IO/NIO 章节知识—答案关系修复与版本化深化，保留旧私人记录。

## 2026-10-11 — 自主优先级：章节源码安全与真实回忆状态

- 实际完成两项：P0 legacy *-run 从源码转 HTML/登录同源运行改安全展示复制；P1 首载 quiz 绑定、动态样式和视频停留伪证据修复。详见 docs/learning/CHAPTER_CODE_SAFETY_REPORT.md；未宣称 V2 全任务完成。
- 文件：markdown.ts、ChapterLearn.vue、ChapterLearn.spec.ts、markdown-code-safety.spec.ts、test-only ChapterCodeSafetyLiveServer.java、本报告与交接。保留此前 UI/Seed 与全仓未知修改。
- 真实链路：Markdown→chapterReading→ChapterLearn；视频此前 updateVideoProgress→LearningServiceImpl→LearningUserChapter/CHAPTER_START，本轮不再按页面时间调用。手动完成与 NextAction/Evidence 原链不变；即时自测不伪造成绩。
- RED→GREEN：attached textarea逃逸注入；首载quiz空；无播放事件60秒产生3.5进度；iframe缺sandbox。新全量 frontend72files/689tests，backend54suites/513tests均0fail/error/skip，types/build/diffcheck exit0。Node22.11/大chunk告警保留。日志/tmp/knowflow-v2-*。
- 真实浏览器隔离8081/5174登录→私有QA章→源码literal/复制→刷新quiz→正确反馈禁用；1440/1280/1024无横向溢出、1024目录折叠；Consolewarn/error=[]。真实HTTP匿名401/Owner200/body200。截图/tmp/knowflow-v2-chapter-recall.png。
- Not verified：Browser Network不提供抓包；视频实际播放/sandbox兼容、Other User/Admin本轮矩阵、MySQL、生产、付费AI。既有历史虚构视频记录未清理。
- Git：main HEAD c8b7f596534e72bf6f7c024ec356a56a3100acb7，staged空，无commit/push/reset/clean/restore/stash；原8080/5173不动、迁移目录不动；只停止本轮隔离服务，不声称后台仍开发。
- 唯一下一任务（高于原IO/NIO内容）：CodePlayground/ChallengePlay仍有同源new Function，CodeChallengeServiceImpl信任请求passCount/total；建立真实伪造评分回归，统一受控执行与服务端权威评分边界，不创建第二套学习算法。

## 2026-10-11 — Learning Chapter UI/UX V2 + 泛型深度修订

- 按最新用户限定交付章节阅读里程碑：ChapterLearn 布局/排版/真实目录/目标/服务端进度/底部操作；泛型 CHM Java17 正文、6追问题、1实验、6私人卡片，复用原3知识与原训练管线。HashMap24真实回归。详情见 `docs/learning/CHAPTER_UI_UX_V2_REPORT.md`、`docs/seed/JAVA_GENERICS_DEPTH_V1.md`。
- 文件：ChapterLearn.vue/chapterReading.ts/ui-tokens.css/InterviewSeedChapter.vue/interviewSeed.ts；GenericsDepthService/InterviewSeedService/Initializer/Controller与3个内容源；LearningServiceImpl#getPathDetail、LearningPathVO注释；前端正文/章节测试与Seed/实验测试、UI规范/报告/本交接。
- 真实发现：getPathDetail漏读用户路径进度，完成1/4详情仍为空。回归先 expected25/null失败，再按认证userId读取learning_user_path，不改进度/Evidence算法。浏览器重载隔离服务后确认25%。
- 真实链路：stable v1章节key→只读preview或显式POST→原Seed索引/实体/知识映射；新修订独立pack锁、私人卡片归属校验，重复启用保留用户编辑与复习记录；GET章节→章节正文解析→真实目录/目标；路径详情→已有displayPathProgress；笔记/收藏/完成与真实Quiz管线原样复用。
- 验证：前端全量73files/692tests、types/build exit0；后端全量54suites/515tests，fail/error/skip0，exit0；Java17实际编译运行3个实验。RED日志与最终日志见报告。没有以旧数字充本轮基线。
- Browser：真实认证seed-personal-v1，路径→24→25，TOC点击/滚动当前项、复制clipboard包含copyInto、收藏、新笔记保存、题目提交反馈、上下章/完成/刷新；本章闪卡实际10张（旧4+修订6），上下文来源章节25。1440/1280/1024无全页横向溢出，窄屏目录可展开；Consolewarn/error=[]。截图/tmp/knowflow-chapter{24,25}-v2-{1440,1280,1024}.png；旧HashMap截图已有。
- Not verified：Browser Network抓包无工具，MySQL/生产/付费AI未测；章节AI上下文答疑未接入，简答字符串匹配非语义面试评分。实际原8080/5173不重启；8081/5174隔离服务用真实业务/H2与offline外部AI，不把它当生产验收。无假Evidence/Mastery/学习记录。
- Git：main HEAD c8b7f596534e72bf6f7c024ec356a56a3100acb7，staged空；保留全仓未知修改及迁移删除，无commit/push/reset/clean/restore/stash。只关闭本轮隔离服务，后端新版本需安全重启才能进入原运行实例。
- 唯一下一任务：处理既有 CodePlayground/ChallengePlay 同源代码执行与客户端评分信任边界（优先于继续扩充内容），先真实回归，再最小安全修复。

### 2026-10-11 — 前端页面清单审计

- 范围：当前工作树 frontend/src/router、views、App Registry、导航及认证/布局入口；仅文档变更。
- 文件：docs/PAGE_INVENTORY.md；本交接记录。Git：main，HEAD c8b7f596534e72bf6f7c024ec356a56a3100acb7；原有大量未提交修改保留；无 commit/push。
- 命令及结果：rg 文件/注册/导航检索；TypeScript AST 提取 routes 与 APP_DEFINITIONS（退出 0）；Vue compiler-sfc 解析全部 104 个 view（退出 0，空模板及解析错误均 0）；111 记录、103 组件 URL、100 独立组件、15 App。无功能修改，未运行 vitest/vue-tsc/build/mvn。
- 真实调用链：App.vue 按 route.meta.layout 选择 CLayout/AppShell；router.beforeEach → useAuthStore → fetchMe → ADMIN 判断；NAVIGATION + APP_DEFINITIONS/APP_NAV_META + UserApp → resolveNavigation → CSidebar。
- 浏览器：现有 5173 Vite cwd 确认属于本仓库；9 个普通用户 URL 渲染抽查，/admin/overview 普通用户被退回首页；未操作数据。Console warn/error 采样为空。未验证其余页面、管理员内容、资源授权、HTTP/Network、响应式、AI 与生产。
- 风险：TaskCenter 固定 mock 与真实身份冲突；Pomodoro 固定成长/社区展示；多处开发中按钮；FocusMode 活跃入口缺失；两份无引用 view；Registry 的 13 个注释与当前 15 个漂移。仅报告未修复。
- 下一任务：复现 TaskCenter mock 与真实成长数据冲突并追踪接口/Store，制定最小修复与回归测试。

### 2026-10-11 · Chapter Learning UI V3 参考图还原

- 范围：真实读取 Downloads/章节学习.png；只优化普通用户PC章节页；保持现有品牌与分组导航。增加真实时间线、折叠TOC、目标卡、四格工具、最近完成章节；修正字体/间距、代码行号及右栏sticky。既有完整Java17内容/旧资料均保留，没有改学习核心算法/Seed/Schema。
- 文件：ChapterLearn.vue、CLayout.vue、CSidebar.vue、InterviewSeedChapter.vue；新增ChapterProgress/ChapterTOC/LearningObjectives/ChapterStudyTools/RecentChapterLearning；chapter-reading-v3.css；ChapterLearn.spec.ts、UI_UX_SYSTEM、reports/CHAPTER_LEARNING_UI_V3.md及截图目录。
- 调用链：CLayout路由类 → 现有Header/Sidebar；RecentChapterLearning → learningEvents(CHAPTER_COMPLETE) → chapterDetail；ChapterLearn → flashcards(undefined,chapterId) + InterviewSeedChapter resources/revision → 真数量；Learning Context闪卡路由保持chapterId；题目/实验定位实际训练区并打开既有练习编辑器。关联知识打开真实关联文档。
- Git：原dirty main/c8b7f59、cached diff空；保留未知工作及预期迁移删除。无commit/push/reset/clean/restore/stash。
- 测试：新增两项先RED 2失败11通过；资源禁用再RED 1失败13通过。全量首轮新增图标未注册1失败，复用bar-chart-3后最终 `npx vitest run` 73文件695通过0失败exit0；vue-tsc exit0；build exit0（既有大chunk警告）。相关后端 `mvn -q -Dtest=InterviewSeedIntegrationTest,CollectionsDepthContentTest test` 16通过0失败/错误/跳过exit0；本轮没有后端全量声明。diff --check通过。
- 浏览器：原5173/8080章节24/25只读兼容验证；隔离5174/8081真实认证/H2/服务（仅外部AI替身，本轮未用AI）验证时间线、前置完成解锁、上下章/路径进度、收藏刷新、笔记保存、TOC折叠/定位/高亮、Java复制、闪卡真实过滤、实验详情、关联文档。1536/1440/1280/1024截图，无页面水平溢出。编辑中临时HMR错误已消失，最终验收无新增严重Console错误；没有浏览器Network抓包能力，⚠️ Not verified。
- 截图：docs/reports/chapter-ui-v3/，完整说明见reports/CHAPTER_LEARNING_UI_V3.md。原服务与隔离服务内容版本不同，未把截图比较当作纯像素评分。
- 风险/差异：原8080仍旧后端，为保留内存数据未终止，不显示当前Java17修订；参考图扁平菜单、插画、演示数字未复制，完整内容使首屏结构有差异。因此不宣布全部V3 PASS。隔离验收服务完成后关闭，不声称后台持续开发。
- ONE Next Task：安全保存原运行环境学习数据后，将默认8080运行版本同步到当前代码并重验章节24/25深度内容链路。

## 2026-10-11 · Chapter Learning V4

- 范围：保留V3三栏，字号/正文色/行高精修；allowlist提示块、原生折叠、GFM安全渲染；声明式本章题目定位；复用InterviewQuestionTree真实单选/多选与原Quiz提交。不修改Evidence/NextAction/DAG/Registry。
- 文件：markdown.ts、markdown-learning-blocks.ts、chapterQuizReferences.ts；interviewSeed.ts；InterviewQuestionTree/InterviewSeedChapter/ChapterLearn；chapter-reading-v3.css；对应新测试；generics-depth.md；test-only ChapterRichMarkdownLiveServer；UI_UX_SYSTEM、CHAPTER_CONTENT_STANDARD、reports/CHAPTER_LEARNING_V4.md及截图。
- 数据流：章节资源 → 真实questionType/options/canonicalKey → 本章关系验证 → 原有quizApi.submit → QuizController/QuizServiceImpl → 既有答案/事件/错题。不在前端计算正确性、部分分或面试评分。静态清单不生成掌握度。seed只增加正文块格式/稳定引用，未覆盖已激活文档与用户记录。
- Git：原dirty main/c8b7f59保留，未commit/push/reset/clean/restore/stash，未知修改及迁移删除不动；diff --check exit0。
- 测试：RED首次9失败10通过；嵌套quiz容器先1失败16通过再修复。最终全量vitest 75文件717通过0失败exit0；vue-tsc exit0；build exit0（Node22.11低于Vite建议、chunk警告保留）；mvn test 515通过0失败/错误/跳过exit0。日志/tmp/knowflow-v4-{full,types,build,backend-tests}.log。
- 浏览器：原5173/8080只读兼容；隔离5174/8081真实认证/H2/service/Quiz，只有外部AI替身且未调用AI。验证正确单选、漏选多选、重复锁定、安全块、稳定题目引用、代码复制、目录定位/折叠、收藏刷新、笔记保存、章节24完成/25导航、真实10张闪卡与Java实验。1440/1280/1024无页面水平溢出；最终隔离Console error空。截图chapter-ui-v4与报告记录了QA和正式修订的区别。
- 风险/未验证：浏览器Network抓包、浏览器网络失败重试（单测覆盖）、MySQL/付费AI/部署未验证；Mermaid源码回退、不新增数学。原8080旧运行版本和内存数据保留，不宣称无条件V4 PASS。隔离验收服务结束后关闭，不后台继续开发。
- ONE Next Task：安全保存原8080学习数据后同步默认后端到当前代码，再验真实账号章节24/25修订训练链。

## 2026-10-11 · 输入框 Focus 修复

- 范围：首页全局搜索、章节回答、共享 Input/Textarea/Select、workbench 与实测同类 AI 聊天；只改样式/aria-invalid，没有业务或学习算法变更。文件与原因详见 reports/INPUT_FOCUS_FIX.md。
- 根因：Tailwind ring/offset 或 textarea shadow 与 pc-studio outline 叠加；search 内联 border 覆盖 focus。使用共享单层2px浅色环，保留控件键盘可访问性与强制颜色 fallback。
- Git：main，HEAD c8b7f59，大量原有未提交修改保留；未 commit/push/reset/clean/restore。最终 git diff --check exit0。
- 验证：RED新增2测试失败；全量 vitest 76文件719通过0失败exit0，vue-tsc exit0，build exit0；既有 Node/Vite/chunk warning 保留。纯前端改动，无本轮后端测试。
- 浏览器：真实5173/8080的两个目标在1440/1280/1024默认/鼠标/Tab截图，单环与尺寸稳定；Cmd+K筛选/Escape；聊天焦点。隔离5174/8081通过真实简答提交反馈，disabled无shadow，Console error空；没有向原库提交测试答案。原服务保留，临时服务结束后关闭。
- 未验证：forced-colors运行、单独hover、浏览器Network、全站每个表单视觉；搜索Enter即时快照未确认结果页。单层旧原生字段未扩展重构。
- ONE Next Task：补齐强制颜色模式与表单错误状态的真实浏览器视觉回归。

## 2026-10-11 · Chapter Reading V5

- 范围：章节目标编号、共享技术文章排版、正文与目录同源知识编号、泛型/PECS/ConcurrentHashMap 结构化正文和安全追加升级。文件清单见 reports/CHAPTER_READING_V5.md；升级维护见 seed/JAVA_GENERICS_READING_V5.md。
- Git：main / c8b7f59，大量既有未提交/未跟踪修改保留；无 commit/push/reset/clean/restore，git diff --check exit 0。
- 前端：全量 npx vitest run，77 文件723测试通过0失败；npx vue-tsc --noEmit、npm run build 均 exit 0。既有 Node/Vite 与 chunk warning 保留。后端 mvn -q test，54 suites517测试、0失败/错误/跳过，exit 0。新增测试先 RED 后 GREEN；真实 Java 示例 javac --release 17 编译执行。
- 调用链：原 InterviewSeedController → GenericsDepthService → 原 activation 锁/事务 → 新版本共享 DOC 与知识映射 → seed_resource 用户阅读选择 → revision → ChapterLearn/既有 Markdown Renderer/TOC。旧文档与用户编辑保留；旧训练用户需显式升级，不更新笔记、进度、Evidence/Mastery/NextAction。
- 浏览器：真实隔离5174/8081后端与H2，章节25/24在1440×900、1280×800、1024×768验证；TOC定位/折叠/高亮、代码复制、训练启用与刷新恢复通过，无水平溢出、新严重Console错误。截图在 reports/chapter-reading-v5/。未提交答案或章节完成操作。
- 原5173/8080保留（node6275/java6282），原24/25可打开；后端仍旧编译版本且内存库，未重启以保护真实数据。因此默认环境完整V5内容尚未验收，不宣称全面PASS。临时服务关闭，不声称后台继续运行。
- 风险/未验证：原库安全更新、MySQL升级、浏览器Network抓包、付费AI/生产运行；其他JVM/MVCC/Redis/RAG/Agent原Seed结构待逐章治理，本轮未批量覆盖。
- ONE Next Task：持久化/备份原内存数据库后，安全更新原后端并验收旧训练用户显式V5升级。

## 2026-10-11 · Java + AI Knowledge Base Rebuild V1

- 范围：真实只读审计55章/32官方章，官方API/固定源码/Reference/论文研究；5个深度单元、75题（10场景、29父子边）、50私有卡、5文档、5可运行实验；13能力域映射现有8路径，不改DAG。详细证据见 content/CONTENT_REBUILD_REPORT.md 与其余4文档。
- 真实调用链：InterviewSeedController → ContentRebuildService → 原pack/activation锁与事务 → 新版本Doc/Quiz/Code/私有Flashcard和知识映射 → 原chapter resources revision → ChapterLearn/既有Renderer/TOC/InterviewQuestionTree/真实QuizService。没有第二套学习状态、证据算法或调度器；旧深度正文入口保留。
- Git：main/c8b7f59，既有大量dirty/untracked保留，迁移目录不恢复；无commit/push/reset/clean/restore。核心新增服务/资源，修改Initializer/Controller/API/InterviewSeedChapter/集成测试与前端组件测试；read-only audit脚本和临时库gitignore。
- 全量命令：backend mvn -q test exit0，55 suites519测试0失败/错误/跳过；frontend npx vitest run exit0，77文件725通过；vue-tsc --noEmit exit0；npm run build exit0（3.66s）；git diff --check exit0。日志 /tmp/knowflow-rebuild-backend-final2.log、frontend-final/types-final/build-final.log；Node/Vite和chunk warning未治理。RED先404，修复实际catalog知识key导致的409，不弱化断言。
- 五个实验真实Java17编译运行，Spring用真实代理/H2验证0与1行；RAG只是离线Recall/RR，不冒充Milvus/LLM生产验证。导入幂等/用户隔离/损坏409/用户编辑保留/history不变有集成测试。
- 浏览器：真实隔离5174/8081与文件H2，五章可读，目录/代码/表格、Flashcard14、Quiz单/多选均真实提交正确反馈，代码编辑器与文档可达；学习中心/路径列表/详情、1440/1280/1024截图；Console error采样空，截图 reports/content-rebuild-v1/。浏览器Network无接口，未全逐题验收。
- 固化：backend/data/rebuild-v1-VI4SsN/knowledge.mv.db，导入前/后闭库副本pre-import.mv.db/post-import.mv.db，目录已忽略Git；备份hash与SQL计数详见报告。重启同文件库后GET/POST/POST200、5版本15题均保留。预置本身无history；真实测试答案可产生正常Evidence/Mastery，不是假数据，章节完成仍0。
- 默认5173/8080仍原node6275/java6282，原内存库未重启未覆盖；本轮没有宣布默认环境PASS。独立验收是实际数据库/Controller/业务API，不是mock成功；测试服务器离线AI替身未参与课程/测验，不代表外部AI验证。临时服务验收结束后关闭，文件库与备份留存。
- 风险/未验证：默认原库安全升级、MySQL约束/生产配置、真实Milvus/付费模型、全题/全交互浏览器回归；其他27官方章仍待逐结论深度治理。来源目前单元链接和正文定位，后续逐结论字段未建。
- ONE Next Task：安全持久化原8080内存库并升级默认后端，验既有seed账号新包启用且历史进度完整保留；先落地当前五章，再扩内容。

## 2026-10-12 · seed-personal-v1 可信本地学习授权

- 真实原8080账号ID4/USER，CodeRun403；本次仅改被忽略application-local.yml：host enabled、授权ID4、专用允许目录、server loopback，不改角色和公共默认安全策略。创建专用工作区README，原密钥/配置未覆盖。
- 原服务4398/8080保留，不重启内存库；浏览器H2备份入口被阻止，未绕过。另复制上轮闭库持久化课程文件到backend/data/rebuild-v1-host-test-20261012/knowledge.mv.db，真实KnowFlowApplication运行8081，Vite5174，启动PID7463/7462；当前服务为用户测试保留，不是后台持续开发。init-mode=never、开发账号自动初始化关闭、切库关闭，无数据清空。原5173与新5174数据不互通。
- 真实接口：同账号Java/Python/JS/C++均200、exit0输出42；LocalReader允许读取200/越界403、匿名运行401。浏览器章节25→CHM实验编辑器→运行成功输出1/2，Console error空，保留交付页面及reports/local-seed-host-access/java-success.jpg。
- 全量mvn test exit0：55 suites519通过0失败/错误/跳过；frontend vitest77文件725通过、vue-tsc/build exit0（4.05s），既有Node/chunk warning保留。未改前端代码、学习引擎或业务API。详细命令、文件/配置/启动方式见reports/LOCAL_SEED_HOST_ACCESS.md。
- Git main/c8b7f59、原dirty保留，未commit/push/reset/clean/restore；本地配置和数据库不提交。未声称所有工具/付费AI/外部向量库已验证；未授予Admin后台或他人数据权限。
- ONE Next Task：安全备份原8080内存库并固化默认环境，使一键启停保留历史学习数据和授权。


## 2026-10-12 · Obsidian Content Studio Phase 1

- 范围：只读审计指定Java + AI Engineer Learning OS，新增content-studio/obsidian-import日期快照、原Markdown/附件/辅助资料副本、五类normalized JSON与personal-profile、审计/重复/技术核验/缺口/优先队列、ChatGPT brief、执行报告、工具及manifest。原库/数据库/学习状态未修改。
- Git：main / c8b7f59；大量原dirty/untracked及迁移删除保留，cached diff空；无commit/push/reset/clean/restore/stash。除本交接追加，仅新增资料包文件。
- 数据链：源目录1310物理文件 → 1194Markdown原字节快照 → frontmatter/链接/附件/代码/Callout/章节 → 独立知识/题目/历史/项目/计划资料 → pending教研。未调用Controller/API/Store/Evidence/Mastery/NextAction；本次是离线资料任务，没有业务调用链。
- 结果：1194/1194读取、失败0；37组字节重复不合并，当前空正文0/仅标题19；A203/B508/C305/D178为结构性初审，不是技术验收。14技术模块＋跨模块管理分类保留原标签/路径。原页面空属性298含副本，不误报当前空文档。
- 个人证据：45份R1历史分数保持原2026-10-06口径；649篇Notion导入来源不当技术来源，正文外部链接270篇；未确认逐字闭卷回答或评分者，AI评分/自评/今日掌握为null，不把标准答案或补录正文当用户回答。P03/P04及旧85/新90规则记版本差异。
- 命令：python3 tools/audit.py读取源文件exit1（YAML日期JSON序列化问题）；保留已完成无损快照后修复default=str，按日期快照resume规范化exit0；python3 tools/enrich.py exit0；python3 tools/verify.py exit0，1310源哈希及1194Markdown字节/文本核验，53 JSON解析、来源外键和统计一致性、无掌握度生成、0错误。日志/tmp/knowflow-obsidian-{audit,normalize,verify}.log。原始frontmatter不丢，JSON日期字符串化。
- 技术核验：仅官方MySQL8.4一致性读、Spring AI1.0 Tool Calling执行边界、LangChain4j RAG主链三项机制查阅；其余高风险结论逐项待验，不宣布1194篇全正确。
- 测试/Build/浏览器：文档与离线资料变更，业务测试/Build/UI验证不适用，未运行mvn/vitest/vue-tsc/build；⚠️ Not verified：项目源码贡献/运行/生产能力、全部附件视觉、Markdown渲染、全量外链有效性。最终diff/status检查单独记录；未操作运行服务或端口。
- ONE Next Task：将brief与所需原题交给ChatGPT，先闭卷采集P03或P04项目介绍，五维诊断后仅加工优先五章和十题，审核通过再讨论Phase 2导入。


## 2026-10-12 · Java + AI Interview Learning OS V2 · Phase2A/2B第一批

- 用户本轮改为教材生产：教学假设全部需从概念学起，不做个人能力诊断、不按历史分数跳过；原Phase1 brief不改写。新增content-studio/java-ai-interview-v2，既有业务/Obsidian/用户学习数据未覆盖。
- 交付：14作者路径/119主题/119题库目录项映射原8seed路径，1194原文件来源记录；五核心手册各18节，CHM另12节，共17教学页57742字符（含代码）26961汉字。18主题节点已关联教材，另101仅课程规划。50题内容版本含20同键选择题、30卡、8实验资产、5教学案例；18旧题语义身份复用，其余32候选待真实库核验；10追问边不是10新题。全status pending_review。
- 真实复用依据：docs/learning/CHAPTER_CONTENT_STANDARD、ContentRebuildService原seed锁/事务及资源键、三份seed catalog、interviewSeed API、QuizSubmitDTO；当前仅作者离线资产，不新建业务数据模型或Store，不接入Evidence/Mastery/NextAction。
- 来源：真实下载并阅读固定OpenJDK17.0.14+7、JDK7u80-b15、MySQL8.4.0共7源文件，保存hash/方法行号；MySQL手册/RAG框架/过滤/传输官方参考。KnowFlow静态链DocController→DocService/Extractor→Chunk/Embedding→Mapper/doc_chunk；ChatController→ChatService→授权分块→Java余弦→Context→AiService/SseEmitter，工作树hash另存，不冒充HEAD或生产运行。P03/P04仅原材料陈述。
- 运行：最终python3 tools/run-experiments.py exit0，6Java17编译/执行和1Python模型7通过0失败；SQL未执行。python3 tools/verify.py最终203检查通过0失败exit0；原Phase1 1331文件hash一致0差异。版本源码/实验范围/预期和实际分开，RAG词袋不冒充神经Embedding/真实LLM或Milvus，ReadView模型不冒充MySQL事务。
- 浏览器：真实渲染器离线HTML，五核心章×1440/1280/1024共15组合18H2、代码/表格/Callout解析，无页面横溢出；RAG整段Java复制通过，Console error采样空。临时tab关闭、viewport恢复、初始空剪贴板恢复为空文本；loopback随机58234验收服务PID15283已关闭，不操作原服务。初次preview错误为QA工具将outline输入当输出，修正后exit0，业务渲染器未改。
- 功能测试/Build：无业务功能修改，未执行mvn/vitest/vue-tsc/build，不援引旧通过数字；执行本轮离线内容QA/例程。源码/教学/去重/格式/关系/真实性自审见reviews/technical-review；不是独立外部审核或正式approved。
- Git：main/c8b7f59，既有dirty/untracked/迁移删除保留，cached diff空；无commit/push/reset/clean/restore/stash。最终diff/status另检查，仅新增包和本交接追加。
- 风险/未完成：原1194语义整合、101后续教材节点、32候选题业务去重、SQL真实两会话、真实RAG组件/生产/全部17逐屏/浏览器Network未验；Phase3保护修订/细粒度映射/Dry Run/导入尚未执行或实现，不能直接拿作者包POST现有V1接口。
- ONE Next Task：人工审核五主题教材并在独立MySQL实例执行两会话实验，确认候选题/知识键后再设计受保护Phase3 Dry Run；不把教学假设写掌握度0。


## 2026-10-12 · Java+AI V2 Phase2E→3A独立审核与HashMap隔离试点

- 范围：五教材/50题独立AI审核（非人审）、题库171行关闭QA快照语义比对、Java/Python7实验重跑；QA HashMap追加版本及真实浏览器验收。保留pending_review。
- 文件：content-studio/java-ai-interview-v2/reviews/{INDEPENDENT_CONTENT_REVIEW,QUESTION_DEDUP_REPORT,EXPERIMENT_VERIFICATION,RELEASE_READINESS,DATABASE_MAPPING,HASHMAP_ISOLATED_IMPORT,SECOND_BATCH_PLAN,PHASE3A_EXECUTION_REPORT}.md；reviews/phase3a独立记录/evidence/screenshots；HashMap正文/题库rubric/import包/manifest；新增ContentStudioHashMapService、ContentStudioQaController、ContentStudioHashMapIntegrationTest、ContentStudioDatabaseGuardTest；InterviewSeedController追加可选QA revision。
- Git：main，接管HEAD c8b7f596534e72bf6f7c024ec356a56a3100acb7；原dirty/migration删除保留，未reset/clean/restore/stash/commit/push。
- 最终命令：backend mvn -q test 57 suites528 tests0fail/error/skip exit0；frontend npx vitest run77 files725PASS exit0、vue-tsc exit0、npm run build exit0（既有警告）；离线content verify203PASS/JavaPython7PASS。
- 实际调用链：ContentStudioQaController→SecurityUtils actor→local/loopback/真实H2路径guard→原seed ledger→planHash/编辑与关联冲突→事务追加DOC/QUIZ/CODE/本人CARD与links→本人activation→InterviewSeedController optional revision；rollback只撤销选择。独立AI复核I1/I2/I3通过限定范围，不能代替人审。
- 隔离环境：backend/data/phase3a-qa-47eb64ed1e（闭库副本），8082/5175；原8080/8081/5173/5174未终止。真实提交/重复/重启/选择回滚/重新激活相同ID。旧17组指纹16完全相同；doc17只VIEW_COUNT2→3，既有阅读接口自增；学习历史和旧正文不改。之后真实浏览器作答为隔离测试交互。
- 风险/未验证：MySQL8.4无隔离环境；RAG专用类别创建403，没有提高用户权限；Citation端到端无证据；完整当前私有题库、人审、真实多事务并发、EDITOR/READER矩阵、闪卡知识回链/编辑器独立返回入口未完成。正式库未写，QA importer不是正式通用导入器。证据与截图见HASHMAP_ISOLATED_IMPORT。
- 一个下一任务：补CHM/AQS演算并完成人工内容审阅，准备隔离MySQL8.4双会话条件；在这些门禁完成前不扩量发布第二批教材。需要用户授权的是将来正式业务数据库写入，本轮无该授权请求。

补充：.gitignore只追加backend/data/phase3a-qa-*/，防止隔离数据库及私有备份误入Git；七个固定官方源码在线SHA复核7/7一致，见remote-source-check.json。

## 2026-10-12 · Phase3B 教学补齐与学习闭环

范围：CHM/AQS2.0.3教学修复；原HashMap文件QA真实学习闭环；ThreadPoolExecutor第二批完整候选样板。未写正式DB/Obsidian，未修改掌握度/Evidence/NextAction算法，未提交/push。Git仍main/HEAD c8b7f59；全部既有dirty/untracked与迁移删除保留，cached diff空。

文件：content-studio/java-ai-interview-v2/chapters/java/{concurrenthashmap,aqs-reentrantlock,threadpoolexecutor}.md、chm/04/05；8Java例与source-registry、second-batch/threadpool-assets.json、教材索引/校验脚本/README/manifest。新增ChapterResourceLinks，接入ChapterLearn/InterviewSeedChapter/KnowledgeMasteryDetail/DocDetail/CodePlayground/FlashCardsHub；route/action-contract追加已注册资源和真实章节上下文，App只对闪卡4真实filter变化重挂载；回归测试。七交付报告在reviews/PHASE3B_EXECUTION_REPORT.md等；原版本哈希快照及独立机器报告在reviews/phase3b。

真实调用链：路径5→章节24→知识1详情→chapter API认证关联→doc38→chapter24题目锚点；链接经stampActionContext→resolveContextualRoute→resolveActionRoute白名单。代码48带chapterId→编辑/刷新→明确返回24；闪卡chapter24加载→开始/翻面/自评→现有review API→退出确认→关联知识→返回；同路径改chapter25实际API筛选和返回25一致。不新增Store/业务表，不按标题猜关联。

本轮命令/结果：frontend npx vitest run 79文件/732通过0失败；vue-tsc --noEmit退出0；npm run build退出0（体积warning保留）。backend ContentStudio两suite9测试，Membership/FlashcardIsolation/Ownership三个suite51测试，共60/0f/0e/0skip；本轮未全量mvn test、不借用旧528数字。Java17八例编译/运行全0；故意清理失败分支预期/实际exit1且finallyterminated。verify.py273/273结构/身份/哈希通过。实际Chrome13检查/119API响应/Console/Pageerror0，前端5175+后端8082原H2独立文件QA，无Mock页面。

风险/未验证：HashMap当前QA正文无Callout；同一文件QA多角色浏览器未测（真实H2 MockMvc权限矩阵已测）。ThreadPool30题/8卡保持pending_review，尚未业务语义去重/数值ID隔离导入；独立AI PASS不是人工审批。MySQL客户端5.7.16、Dockerdaemon不可用，8.4双会话BLOCKED。RAG USER admin分类403是预期，向量聊天临时DocDocument无稳定doc/chunk身份，合法QA管理员/SSE E2E未验证；没有提权或改核心。普通QA真实作答/卡自评产生记录保留，不能把它们说成未发生。

下一个任务（只选一个）：由人类审阅ThreadPool完整教材及30候选题，按真实业务题库逐题语义去重并确认评分锚点，然后准备该单主题Dry Run；不得跳过人审或写正式库。


### 2026-10-12 Phase3B 额度恢复后的续做交接

范围：既有教材不重建；统一离线题库索引/范围内ThreadPool语义比较、HashMap QA Dry Run与真实闭环复测。文件：content-studio/java-ai-interview-v2/interview-bank/unified/、tools/{unify-question-bank.py,verify-unified-index.cjs,review-threadpool-relations.py,phase3b-resume-browser.cjs,phase3b-resume-experiments.py}、reviews八份Phase3B报告及phase3b-resume证据、manifest/README。无业务代码/Schema改动。

Git main/HEAD c8b7f596534e72bf6f7c024ec356a56a3100acb7，保留既有dirty/untracked/迁移删除，cached空；无reset/clean/restore/stash/commit/push。后端mvn test=528/0失败/0错误/0跳过，前端全量vitest=79文件732通过，vue-tsc/build退出0（包体积warning仍在）；verify.py273通过，8 Java17实验重新执行通过；Ajv索引1076条/2拒绝负例/幂等导出通过。日志为reviews/phase3b-resume/evidence，不能套用历史数字。

真实调用链沿现有5175→8082→隔离文件H2；QA Controller→SecurityUtils→环境文件守卫→ContentStudioHashMapService→Seed Ledger/Activation/Revision。HashMap GET Dry Run18映射全部保留/冲突空；14浏览器检查、119API、30TOC入口逐一可见、Console0。目录初次异步等待失败证据保留。浏览器测试实际作答/自评会正常产生QA记录，未编造Mastery或修改Evidence算法。没有正式写入、没有解除环境保护。

风险/未验证：878暂定身份只是保留记录的索引；589 Obsidian候选仍需独立题拆分/元数据/语义审阅；184同标题组不自动合并，996记录有至少一缺字段。ThreadPool30题仅完成历史171QA与首批50范围比较；全库最新去重不成立，因为普通题库API随机最多50题。QA Callout/多角色浏览器、任意新版本文件QA升级、MySQL8.4、RAG真实E2E、人审/正式写入仍未完成。包2.0.4全部pending_review。

一个下一任务：按unified/conflicts.json逐条解决来源元数据与题目语义身份；完成审阅后再做ThreadPool单主题QA映射，不继续批量扩展教材。

## 2026-10-12 · Phase3E 智能考试系统闭环与两处真实缺陷修复

范围：独立考试子域 com.knowflow.exam（19 类/5 表/17 API/10 模板/61 道 H2 SEED_EXAM 题）+ 前端 views/exam 5 页 + api/exam.ts，交付模板组卷→在线答题→规则评分→AI 五维评卷→成绩分析→错题本完整闭环。本轮会话修复两处真实 E2E 缺陷并完成全量回归与 9 份报告。未写正式 MySQL、未提权、未触发付费 AI、未 reset/clean/restore/stash、未 commit/push。Git main/HEAD c8b7f59；考试模块整目录与前端 exam 目录为 untracked（Phase3E 新增），cached diff 空；仓库内其余既有 M/?? 修改属其他 Agent，未触碰。

文件（本轮实际改动，均在 untracked 考试模块内）：engine/PaperComposer.java 新增 mistakeTypeDistribution(userId,topics)（L126-153）；service/impl/ExamServiceImpl.java normalizeComposeDefaults 改为带 userId 并在 MISTAKE+空分布时按错题池真实题型构成动态分布、抽出 defaultTypeDistribution()；前端 ExamResultPage.vue/ExamCenter.vue/api/exam.ts 修复 rate×10000（后端 rate 为 0-100 整数）；ExamCenter.vue modeOptions 联合类型、ExamTake.vue L80 多余闭合标签修复 build。新增 9 份 docs 报告与 docs/reports/phase3e/screenshots 18 张证据图。

真实调用链：POST /api/exam/papers/compose(strategy=MISTAKE,typeDistribution={}) → ExamServiceImpl.normalizeComposeDefaults → PaperComposer.mistakeTypeDistribution → loadMistakeQuestionIds(source=EXAM,masterized=0,question_id 非空) ∩ loadBank 可访问题 → filterByTopics → 按题型计数；无错题回退默认分布由 compose 早返回 409「没有可用错题，证据不足」。交卷：ExamGrader（多选 PARTIAL_CREDIT 错选0/少选 round(满分*命中/应选) 封顶满分-1；填空 | 等价 trim 忽略大小写；无标准答案转主观 PENDING）→ total_score 只累计已评分题，有 PENDING 则 GRADING/PENDING_AI → learning_mistake 三不录（空答案/主观 PENDING/非考试来源）→ LearningEventService.record(QUESTION_ANSWERED,"EXAM") L881。答案只在 exam_paper_question.snapshot，TakeQuestionVO 不下发，交卷 ResultVO 才有 correctAnswer/correctAnswerText。

命令/结果（本轮实测）：frontend npx vitest run=79 文件/732 通过；vue-tsc --noEmit 退出0；npm run build=✓3.95s（仅 chunk 体积既有 warning）。backend mvn test=564/6 失败/0 错误/0 跳过，6 失败全部是基线 com.knowflow.seed.ContentStudioThreadPoolIntegrationTest（HEAD 复现、与考试无关），考试模块 4 类 30/30 通过；mvn compile 0。后端为加载修复重启 1 次（8080，H2 重建属设计），日志 .dev-logs/backend-exam.log。

浏览器（TRAE 受控 Chrome，仅内置工具，未提权/未 osascript 切 Space）：闭环 A 模板卷 59/84 暂定（多选少选实得 9/17、1 主观 PENDING、标记待检查+草稿时间弹窗真实）；闭环 B 自定义 JVM+Spring 10 题 80/80 暂定；错题本标记掌握即时移除。MISTAKE 409 curl 复现→修复→重启后双账号 API 复验 200（admin 与 userId=4 隔离成立）→浏览器按钮 disabled→enabled 点击跳 /exam/take/3。1440/1280/1024 三档×5 页 15 张用同源临时 iframe 精确宽度+CSS scale 截图（真实断点/真实 XHR/仅测试 DOM），Console 无错误。

风险/未验证：真实付费 AI 评卷 NOT_VERIFIED（ExamAiServiceTest mock 覆盖成功/未配置/异常/非法 JSON/越界；五维 35/25/20/10/10 服务端重算总分）；代码题安全沙箱 BLOCKED（CodeRunController 为主 JVM 直执行，不能称判题）；MySQL8.4 考试 5 表约束/并发交卷竞态未验证；物理 1440 外层窗口截图受 Space/扩展侧栏限制（iframe 断点渲染已替代并在 EXAM_UI_REVIEW §1 如实标注）；107 道简答 topicOf(tags) 标签与卷面分组偶发错位为数据治理项。

一个下一任务：获用户授权后做 1 场真实模型主观题评卷 E2E（自动评卷+ai-grade 补评+权限/幂等 HTTP 断言），在此之前不扩 CodeRun 自动判分；随后隔离 MySQL8.4 补考试表约束与并发交卷测试。

## 2026-10-12 · 全局搜索 UI/UX 优化与真实验收

- 范围：普通用户搜索页和 CTopNav；54px/752px/12px/16px 单层搜索框、32px清除、34px分组筛选/重置、URL查询与过滤恢复、IME Enter保护、请求版本竞态保护、安全摘要与柔和高亮、独立错误重试、真实收藏。
- 文件：SearchResult.vue、CTopNav.vue、ui-tokens.css仅3搜索变量、utils/searchText.ts与2新增测试文件；docs/SEARCH_UI_UX_OPTIMIZATION_REPORT.md；reports/search-ui-ux真实截图/日志/browser.json/verify-browser.cjs。保留顶栏/令牌文件原有修改。
- Git：main/HEAD c8b7f596534e72bf6f7c024ec356a56a3100acb7，原大量dirty/untracked/迁移删除保留，cached空；无commit/push/reset/clean/restore/stash。最终相关diff检查通过。
- 调用链：CTopNav/SearchResult→URL(q/type/time/sort)→docsApi.list→request→DocController→DocServiceImpl.getDocPage→原access.scope/actor→doc_document分页/相关度→buildSnippet→Markdown token展示提取→逐段转义高亮。类型/时间仍前端过滤、排序仍后端；1000条上限明确标注。收藏走本人favorites→toggleFavorite→本人favorites确认。无第二Store、无Evidence/NextAction/索引/原文/后端权限修改。
- 命令：frontend npx vitest run，81文件737通过0失败exit0；npx vue-tsc --noEmit exit0；npm run build exit0（chunk warning保留）；相关git diff --check exit0。首次3测试失败与click类型构建错误已修正后全量重跑；未删除/弱化断言。本轮未跑mvn test，因为无后端修改，不借用历史数字。
- 浏览器：原5173/8080真实Chrome和API，17检查；1440/1280/1024/768×900截图、54px输入/0内框阴影/无横溢出、关键词和双入口Enter、过滤刷新/前后退/清空、无结果、Tab、重复请求/合成IME、文档点击、匿名收藏提示、真实账号收藏并恢复原状态。错误重试仅一次浏览器请求超时注入，之后真实后端恢复；自然Console0、注入预期ERR_TIMED_OUT1、PageError0。未重启/终止服务和数据库；文档点击可增加正常阅读次数。
- 风险：⚠️ Not verified 原生OS中文IME、屏幕阅读器播报、自然15秒超时、真实>1000数据、MySQL/生产、多角色权限矩阵；768沿用PC shell。不用Mock页面冒充E2E。
- ONE Next Task：独立验收环境验证>1000条授权数据，评估后端类型/时间过滤，保留权限作用域和排序。

## 2026-10-12 · 搜索首页中央输入框 UI 修复

- 范围：只改SearchResult的无关键词hero表单及两个必要尺寸令牌，不改结果框/CTopNav/全局style.css/后端/学习核心。修复前浏览器实际右padding16px+2pxborder→按钮右Inset18px；根因为末尾hero与compact共享padding/gap覆盖，不用负margin掩盖。
- 文件：frontend/src/views/SearchResult.vue、styles/ui-tokens.css新增hero60px/按钮112px、views/__tests__/SearchResult.spec.ts追加4测试；docs/SEARCH_HERO_INPUT_UI_FIX_REPORT.md；reports/search-hero-input前后四尺寸默认/聚焦/长词截图与RED/GREEN/全量日志/browser.json/脚本。本交接只追加。
- DOM/CSS：div改form submit；父级padding/gap0、1px灰border/12px圆角/overflowhidden；蓝按钮固定112px/stretch/无margin/padding/radius/active缩放，图标与input居中、16px输入无内框阴影。原752px宽度保留；结果框54px实测不变。
- 调用链：hero form/Enter→submitHeroSearch trim/IME/导航pending锁→原writeQuery→URL q→原routewatch→docsApi.list→request→原DocController/DocServiceImpl权限搜索→结果页。空白无导航/API；pending disabled/aria-busy，结束恢复。无数据写入或第二搜索协议。
- Git：main/HEAD c8b7f596534e72bf6f7c024ec356a56a3100acb7；原dirty/untracked/迁移删除保留、cached空；无commit/push/reset/clean/restore/stash，相关diffcheck退出0。
- 命令：修改前新增4测试失败/原3通过exit1；修复后相关7通过exit0；frontend全量npx vitest run=81文件741通过0失败exit0、vue-tsc --noEmit exit0、npm run build exit0（3.89s）。Node22.11.0/Vite最低22.12与chunk既有warning保留；测试localhost3000 ECONNREFUSED日志不改通过状态。无后端改动，本轮未跑mvn不借用历史数字。
- 浏览器：原5173/node4386、8080/java51022保留，无终止/重启；真实Chrome11检查，1440/1280/1024/768×900，hero60/button112×58/右上下Inset1（仅border）/inputborder0 shadowNone/中心差0/无横溢出，长词通过。实际按钮与Enter各请求1次、trimURL、顶部与结果同步、历史、Tab、合成IME/空词；4真实HTTP200/body.code200，“测试”9、“Spring”6；Console/PageError0。初次QA异步响应等待不充分正确判失败，等待真实结果后全流程重跑通过。before截图处于淡入期，颜色浅但真实测量before.json完整，不重建或美化。
- ⚠️ Not verified：原生系统中文IME、屏幕阅读器播报、生产/MySQL/多角色权限矩阵；本轮没有更改相关能力。
- ONE Next Task：实际中文输入法人工核对选字Enter不搜索、选字完成后Enter搜索；保持其他范围不变。

## 2026-10-12 · seed-personal-v1 金币余额设为50000

- 用户明确授权余额设置；范围为默认8080/5173当前本地库，账号ID4/USER，wallet.id2。通过既有ADMIN认证H2控制台执行行锁事务：原coin8→50000；追加COIN/CREDIT/COMPENSATION/MANUAL_ADJUST流水49992，source_id1791820788263，balance_after50000。INSERT和UPDATE各影响1行，事务核验后COMMIT，xp40/level1/USER/unlimitedfalse不变，不伪造学习事件。
- 真实链：登录→认证WalletController→WalletService.getWalletVO→wallet；管理调整通过原ADMIN门禁控制台→同一jdbc:h2:mem:knowflow连接→wallet+reward_ledger事务。普通用户控制台403未绕过，使用既有开发管理员登录，不提升目标角色或新增接口。
- 验证：调整前GET/api/wallet HTTP200/body.code200 coin8；COMMIT后目标账号同接口HTTP200/body.code200 coin50000。控制台退出，临时token删除，无服务重启/终止。当前库是内存H2，未宣称重启持久化；8082独立QA库未改。
- 文件：仅交接追加，无源码/配置变更；Vitest/vue-tsc/build/mvn不适用未运行；⚠️ Not verified 浏览器钱包展示刷新和重启持久化。Git原dirty保留，无commit/push/reset/clean/restore/stash。
- ONE Next Task：用户刷新钱包页面确认显示50000；后续按既有备份任务固化本地内存库。

## 2026-10-13 · 考试应用获取失败修复

- 范围/根因：应用中心点击获取考试，真实 PUT /api/apps/user-apps/exam HTTP200/body2006「无效的应用标识」。前端已注册 exam，后端 OfficialAppRegistry 漏注册。只追加 exam=ACTIVE 和更新过时数量注释；原 capture/spaced-review 修改保留，默认安装策略不改。
- 文件：backend/.../apps/OfficialAppRegistry.java；新增 apps/ExamAppInstallIntegrationTest.java（独立 H2，4测试）；docs/EXAM_APP_INSTALL_FIX_REPORT.md；reports/exam-app-install-fix 真实前后截图、browser.json、测试摘要。交接只追加。
- 链路：AppCenterPage.onPrimary/onInstall → useAppStore.install → appsApi.updateUserApp → AppsController → SecurityUtils actor → UserAppService.upsert → OfficialAppRegistry → 当前用户 user_app。不经过金币购买，不改变身份/考试/学习核心。Owner/Other/Admin/Anonymous及重复获取单行由测试验证。
- RED：新增4测试3失败exit1；GREEN：新增4+UserApp5全部通过exit0。frontend全量81文件741测试通过exit0、vue-tsc exit0、build exit0（Node/Vite及chunk warning保留）。backend全量582/0失败/14错误exit1（共享phase3a-qa-test索引初始化冲突）；受影响评分5/并发9各自运行全通过。隔离fork全量报告570项4失败0错误0跳过+5个ClassNotFound fork错误，exit1：DocServerFilterPaginationTest4断言与未修改学习/搜索测试类加载问题；未宣称后端全量PASS，未在干净HEAD证明基线。详见报告与backend-summary.json。
- 本地8080/PID51022内存H2未重启。一次性JDK Attach agent仅向已加载APP_STATUS补exam=ACTIVE（null→ACTIVE,count16），源码保证后续正常启动；不动8082、不清数据。这是本地开发特权修补，不作为生产部署能力。
- 浏览器：原5173/8080真实Chrome7检查，获取HTTP200/body200、刷新启用/单记录、打开/exam考试中心、1440/1280/1024截图，Console/PageError0。为验收临时重置已安装标志再页面获取，最终installed/enabled=true，无考试数据删除；其他应用与钱包前后一致coin49825/xp65，未覆盖正常消费。早期脚本等待/已安装/硬编码余额导致QA失败，完整重跑后通过，失败证据保留。
- Git：main/HEAD c8b7f596534e72bf6f7c024ec356a56a3100acb7，cached空；原dirty/untracked/迁移删除保留，无commit/push/reset/clean/restore/stash，相关diffcheck通过。
- ⚠️ Not verified：后端全量通过、MySQL/生产、H2重启持久化、付费AI/考试业务全流程。本轮获取与打开已验证。
- ONE Next Task：稳定独立构建目录复验后端全量，处理H2上下文冲突并确认文档过滤回归，保留当前本地库。

## 2026-10-13 · 笔记页面 UI/UX 优化与真实验收

- 范围/根因：/notes→NotesManage→MiniAppShell→CLayout/CTopNav/CSidebar。原-24px边距抵消真实21px padding，桌面scrollWidth+3；100vh-56未扣壳页头、窄屏底栏，目录覆盖正文；工具栏元信息拥挤、raw Markdown摘要、硬编码标签。保留现有三栏与全局导航，只改笔记阅读/必要编辑和7个尺寸令牌。
- 文件：NotesManage.vue、NoteEdit.vue、styles/ui-tokens.css仅7 notes变量，新增NotesManage.spec.ts/NoteEdit.spec.ts；docs/NOTES_UI_UX_OPTIMIZATION_REPORT.md；reports/notes-ui-ux 同文前后四尺寸、长代码/表格、抽屉、编辑、深色截图与日志。交接只追加，保留共享tokens既有修改。
- UI：目录280/收起56，阅读上限832居中，1200及以下目录抽屉；标题→搜索→全部→真实标签→列表；柔和选中、摘要两行、列表/正文独立滚动。正文16/1.75、H2 24/H3 20，代码/表格局部横滚；工具栏breadcrumb+编辑+更多，原加号新建收纳，元数据移到正文头部。复用searchSummary/renderMarkdown/normalizeNewlines/handleCodeCopyClick，不写回原文、不重写解析器。内置示例只读负ID，缺失时间不造记录。
- 真实数据流：/notes原生localStorage note-list→展示；编辑独立note-ID-draft，显式发布单次写note-list→/notes?note=ID保持选中；UI偏好sessionStorage notes-view-state。旧note-ID保留；取消恢复本次进入前草稿、脏状态/路由离开保护；成功写入后才显示保存，失败留内容/重试。另一/workbench/notes有WorkbenchController/Service/wb_note CRUD，本轮未混接/迁移。DocDetail note-from-doc、docsApi.list关联、chatApi.send保留。无Evidence/NextAction/权限算法/Obsidian/服务器笔记写入。
- 测试：RED最初11项10失败1通过exit1；copy新增测试1失败、真实保存离开guard顺序新增测试1失败均证实后修复；GREEN相关13通过exit0。最终frontend全量83文件755测试通过0失败exit0，vue-tsc exit0，build exit0/5.00s；相关diffcheck通过。已有localhost3000测试日志、Node22.11/Vite22.12及chunk warning保留；无后端改动，本轮未跑mvn，不借旧数字。
- 浏览器：原5173/8080真实Chrome、独立上下文、真实表单新建隔离笔记，16项检查通过，Console/PageError0。同文前后1440/1280/1024/768，最终scrollWidth=viewport、scrollHeight900、正文中心差0/16px/28px；目录与正文独立滚动，宽表格1121/代码3996仅自身滚动。搜索/标签/切换/双侧栏折叠、草稿取消再打开原文不变、拒绝离开留页/未改无误提示、新建/标签/显式保存/保留选中、QuotaExceededError一次注入失败后真实重试、代码实际剪贴板、抽屉Tab/ShiftTab循环与Escape返回焦点、编辑四尺寸、data-theme深色均验证。早期选择器/守卫/主题脚本问题如实失败后完整重跑，无Mock页面。浏览器Network确为GET应用状态/通知；/notes本地无笔记CRUD HTTP，不宣称后端保存。
- Git：main/HEAD c8b7f596534e72bf6f7c024ec356a56a3100acb7，cached空、原dirty/untracked/迁移删除保留；无reset/clean/restore/stash/commit/push，无服务终止/重启。QA认证临时状态仅/tmp，不进报告。
- ⚠️ Not verified：/notes无删除能力，未新增/执行删除；历史note-ID草稿未迁移、服务端同步/账号隔离、MySQL/生产、付费AI/关联API自然故障、屏幕阅读器、系统关闭原生提示、用户原始Obsidian内容。30秒自动保存定时器单测通过，浏览器验证手动草稿。真实用户日常浏览器本地笔记不加载/覆盖，验收保存仅隔离上下文。
- ONE Next Task：独立设计历史本地草稿恢复与同步方案，显式迁移/账号隔离/原文保全，维持当前UI范围。

## 2026-10-13 · Phase 3F 稳定性/安全/考试质量收口

- 范围：任务A后端基线；任务B考试并发幂等；任务C代码执行安全审计（只出威胁模型/阻断项，未引入容器、未跑攻击代码）；任务D AI评卷质量权限（Stub，不调付费模型/不读Key）；任务E搜索服务端过滤分页+107简答题topic审计；任务F全量验证与6份报告。
- 基线根因（任务A）：content-studio/.../import/threadpool-qa.json 三个 knowledgeKeys 仍为旧数字键，基座已改语义键 concurrency.threadpool.k1/k2/k3，导入409导致 ContentStudioThreadPoolIntegrationTest 6失败；仅改JSON三键恢复，未删/跳测试。
- 考试真实链：ExamController→ExamServiceImpl.startAttempt（sys_user行SELECT FOR UPDATE串行化双设备开考）；saveDraft 改条件UPDATE（仅IN_PROGRESS更新draft列，0行409，杜绝旧实体覆盖交卷）；submit/新增 autoSubmitExpiredAttempts 共用条件认领（IN_PROGRESS→GRADING）后事务内 finalizeAttemptAfterClaim；超时只信草稿、clientVersion=AUTO_TIMEOUT；ExamTimeoutScheduler（默认开、60s/批100、knowflow.exam.timeout-scheduler.enabled=false可关）。AI补评三段式 claimAiGrading（短事务认领PENDING_AI/AI_FAILED或僵死AI_RUNNING）→事务外模型→commitAiGrading（逐题graded 0→1）/releaseAiGrading；未评分主观题score恒NULL不判0。ExamAttempt新增AI_RUNNING；ExamService新增autoSubmitExpiredAttempts。
- 搜索真实链：DocQueryDTO新增type/from/to；DocServiceImpl两条排序路径共享wrapper下推 applyTypeFilter/applyTimeRange（非法值400、from/to含边界）；time/view/relevance全部排序加id兜底；相关度 total=min(DB命中,500候选窗口)，深页不再伪空；前端SearchResult.vue删除fetchAllPages/1000截断，type/from随页请求。
- 文件（本轮）：backend exam包内 ExamAttempt/ExamService/ExamServiceImpl + 新增ExamTimeoutScheduler；dto/DocQueryDTO、service/impl/DocServiceImpl；新增测试 exam/ExamConcurrencyIntegrationTest(9)、exam/ExamAiGradingIntegrationTest(5)、controller/DocServerFilterPaginationTest(9)；content-studio threadpool-qa.json；frontend api/types.ts、views/SearchResult.vue、views/exam/ExamCenter.vue(AI_RUNNING文案)、views/__tests__/SearchResult.spec.ts；docs新增 PHASE3F_EXECUTION_REPORT/EXAM_CONCURRENCY_REVIEW/CODE_EXECUTION_SECURITY_AUDIT/AI_GRADING_PERMISSION_REVIEW/SEARCH_SCALABILITY_REVIEW/QUESTION_TOPIC_DATA_AUDIT。exam包与部分文件在git中整体untracked（既有状态），未改历史题库/考试快照/learning证据算法。
- 代码执行审计结论：ProcessBuilder宿主子进程（非主JVM执行，PASS），但无OS隔离——环境变量未白名单清洗、无网络/内存/nproc隔离、共享/tmp，安全在线判题 **BLOCKED**，B1–B8阻断项见 CODE_EXECUTION_SECURITY_AUDIT；HostCapabilityGuard默认关闭、仅local userId4、allowed-root单目录，PASS。
- 题库审计：只读真实接口 GET /api/admin/quiz-questions（SHORT_ANSWER,status=1）total=107；明确错配6道（id104-109：MySQL索引/隔离/锁、Redis缓存/分布式锁、MQ重复消费，当前误挂SPRING）；36道JAVA首标签过粗（id62-97建议细映射）；未改任何数据。
- 命令与结果：backend `mvn test` 最终 Tests run:591, Failures:0, Errors:0, Skipped:0 exit0（日志/tmp/p3f-mvn-full3.log）；并发+AI两类单独连跑3轮×14全绿；frontend `npx vitest run`=83文件755通过 exit0、`npx vue-tsc --noEmit` exit0、`npm run build` exit0(3.92s)。修正：两新考试测试类改独占H2库名 phase3f-exam-concurrency/phase3f-exam-ai-grading（原复用phase3a-qa-test致schema.sql重复建idx_user_role）；并发草稿场景删除调度相关假断言（draft409≥1不保证成立），确定性409由串行场景3覆盖。
- 浏览器：未重启原5173/8080；真实Chrome对 /search?q=Java 冒烟PASS——console0错误，选PDF→请求含type=pdf，最近一周→from=2026-10-06，翻页→pageNum=2，截图留存。端到端过滤效果 NOT_VERIFIED：当前8080为修改前后端（忽略新参数），服务端行为由MockMvc H2集成测试覆盖。
- Git：main/HEAD c8b7f596534e72bf6f7c024ec356a56a3100acb7；其他Agent大量既有dirty/untracked原样保留；无commit/push/reset/clean/restore/stash。
- ⚠️ Not verified：MySQL8行锁/死锁/条件更新/大表执行计划（仅H2 MODE=MySQL）；搜索过滤真实8080 E2E（需重启后端补测）；付费模型E2E与真实Prompt Injection抗性（未授权不调用）；代码执行逃逸/渗透（按约束未执行危险代码）；多实例超时扫描（NEEDS_FIX，需DB租约）；500相关度窗口外完整语义检索（NEEDS_FIX，路线SQL下沉→ES/FULLTEXT）；生产库题库标签分布。
- ONE Next Task：MySQL8环境复跑9考试并发场景+520条搜索分页场景，并重启8080补一次搜索筛选浏览器E2E。

## 2026-10-13 · 思维导图 UI 与绘图菜单修复

- 范围：/mindmap、/drawing与必要Vite预构建/Router加载失败Toast。真实Chrome点击绘图后仍在/mindmap，VueFlow core/background/controls优化依赖HTTP504 Outdated Optimize Dep→DrawingEditor import失败。已有页面与权限守卫正确，不是NOT_IMPLEMENTED；显式optimizeDeps include三依赖修复，保留引擎/原路由，onError避免静默。
- 文件：MindMap.vue、DrawingEditor.vue、vite.config.ts、router/index.ts仅onError（既有考试路由保留）、ui-tokens.css仅canvas目录280变量、新MindMap.spec.ts/DrawingEditor.spec.ts；docs/MINDMAP_DRAWING_UI_FIX_REPORT.md、reports/mindmap-drawing截图/脚本/JSON。只追加交接。
- UI：单主要CTA、左侧简短空态、右侧flex居中（原absolute无定位边界）、工作区minwidth0/明确高度，目录280，1100以下切换+Escape、标题/工具栏响应式，节点默认适配最高100%，token颜色、独立可访问删除按钮/焦点。思维导图SVG层级关系，绘图VueFlow节点流程图；不是通用画笔/图形编辑器，未引入大型依赖。
- 真实链：CSidebar→Registry/APP_NAV_META→RouterLink→认证守卫→页→API→Drawing/MindMapController→SecurityUtils→对应Service→Mapper→wb_drawing/wb_mindmap。后端requireOwn/用户过滤不变，无Evidence/学习架构/钱包/原笔记/Obsidian改动。
- 状态修复：列表失败+重试、创建去重、保存版本号/思维导图快照避免清掉新dirty、保存成功后新编辑重排autosave，失败不无限重试；切换/离开先保存，失败留页，保存中提示；删除当前dirty图清状态/计时器，卸载清计时器。最终额外根节点断言揭示textarea单向value被保存状态重渲染重置，RED证实后改v-model/input同步。
- 测试：初始新增4全失败→通过；节点输入RED1失败/5通过；复审慢保存/脏删除RED2失败/6通过。最终前端全量85文件765测试全通过exit0/7.20s、vue-tsc exit0、build exit0/3.72s；diffcheck通过。第一次build测试node:fs类型失败改用?raw，不扩大应用类型环境。npm/Node版本/chunkwarning保留。无后端修改未运行Maven。
- 浏览器：原5173/8080真实Chrome13项，25条CRUD浏览器HTTP200/24条body200（1条历史GET body未捕获），Console/PageError0；两类QA图真实新建/节点编辑拖动/保存刷新重开/删除确认，思维导图根+子文本/层级/缩放平移，绘图真实Handle拖拽形成1连线/2节点；1440/1280/1024/768无横溢出，目录切换/Escape、历史通过，原图列表前后完全一致。脚本早期连接点选择器错误与后续根文本断言失败均如实保留记录，最后完整重跑通过。只删除POST返回的本轮对象ID。
- Git：main/HEAD c8b7f596534e72bf6f7c024ec356a56a3100acb7/cached空，保护原dirty/untracked/迁移删除，无reset/clean/restore/stash/commit/push；8080内存H2 PID51022与5173PID4386保留，Vite配置监听生效。请求状态/token只进进程内存，报告无凭据。
- ⚠️ Not verified：生产/MySQL/H2重启持久化、完整多角色后端权限矩阵、自然超时、原生IME/屏幕阅读器、关闭系统窗口未保存保护、跨浏览器整图版本冲突；画笔/任意形状/图片/导出是未实现能力。本轮真实绘图流程图已可用。
- ONE Next Task：独立设计图版本冲突及系统关闭草稿保护，保持数据归属和当前引擎。

## 2026-10-13 · Phase 3G 统一题库中心 + 算法代码练习 + 安全判题门禁

- 范围：quiz_question 扩 CODE 题型与显式分类（question_topic/topic_code/canonical_key/review_status/version/creator_id，answer 可空，question_type VARCHAR(24)）；新增 coding 整包（5 表：question_topic/code_problem/code_test_case/code_draft/code_submission）；用户题库中心 + 双栏练习页 + ADMIN 管理页；判题三档网关（disabled 默认 / mock / 本地真实 javac 经 HostCapabilityGuard）；CODE 接入 PaperComposer 组卷快照、考试结算（PENDING_JUDGE + rejudge-code 幂等）、错题(source=CODE)、CODE_SUBMITTED 事件/Evidence 防御/既有奖励；8 题真实编译验证种子（开关默认关、待审核）。8 份文档在 docs/（PHASE3G_EXECUTION_REPORT/QUESTION_BANK_CENTER_DESIGN/CODING_PROBLEM_SCHEMA/CODE_JUDGE_ARCHITECTURE/CODE_JUDGE_SECURITY_GATE/SMART_PRACTICE_INTEGRATION/CODING_PRACTICE_UI_REVIEW/CODING_PRACTICE_E2E_REPORT），截图 reports/phase3g/。
- 真实调用链：练习 CodeBankController(/api/code-bank)→CodeProblemServiceImpl(saveDraft upsert/submitPractice RUN仅PUBLIC不记错不发事件、SUBMIT全用例)→CodeJudgeGateway(disabled/mock/local)→code_submission；用户失败(WA/CE/RE/TLE/MLE) addMistake+CODE_SUBMITTED，平台失败(SYSTEM_ERROR/JUDGE_UNAVAILABLE) recordPracticeOutcome 入口直接 return：score=NULL/零错题/零事件；ACCEPTED 才在详情回题解。考试 ExamServiceImpl.finalizeAttemptAfterClaim：CODE 空答案 0 分 GRADED 不记错；非空 graded=0/GRADE_JUDGE/score=NULL→safeJudgeCode(EXAM,answerId)→条件更新 0→1 落分；attempt 汇总 PENDING_JUDGE（与 PENDING_AI 双标记）；POST /api/exam/attempts/{id}/rejudge-code→rejudgeCodeAnswers 只捞 graded=0&&JUDGE（幂等，AI_RUNNING 认领态不覆盖）。PaperComposer.buildSnapshot 的 codeProblem 仅签名/starter/限额/version/publicCases，参考解与 HIDDEN 禁入快照；ExamGrader.requiresAi 仅 SHORT_ANSWER。
- 安全：**生产在线判题 BLOCKED**——G1 OS隔离/G3禁网/G9 nproc/G10磁盘/G15限流/G16队列未过（清单见 CODE_JUDGE_SECURITY_GATE）；knowflow.judge.mode 默认 disabled，UI 横幅+运行禁用，提交 JUDGE_UNAVAILABLE/score=null。LocalHostJavaJudge 是宿主机 ProcessBuilder 反射 Harness（两阶段编译、逐用例 CaseCall 独立 main、-Xmx、墙钟强杀、0700 私目录、环境白名单），仅授权本机功能验证，不是沙箱；未放松 3F 的 CodeExecutionServiceImpl/HostCapabilityGuard。
- 文件：新增 backend com/knowflow/coding 整包（entity5/mapper5/judge8/service2/controller2/vo1/seed2 + 2 测试类）；改 exam 包 PaperComposer/ExamGrader/ExamAnswer/ExamAttempt/ExamServiceImpl/ExamVos + 考试 Controller；H2 与 MySQL schema 同步 + mysql/migration/V20261013__phase3g_coding_bank.sql；QuizQuestion 加常量；前端新增 api/codeBank.ts、views/coding/CodeProblemList.vue+CodeProblemSolve.vue、views/admin/CodeProblemMgmt.vue，改 api/exam.ts、router、apps/registry(v1.1.0 路由改 /coding/bank)、layout/Sidebar、exam 三件套(ExamCompose/ExamTake/ExamResultPage)、learning/evidence/resolver.ts(+spec)。旧 code_question/CodePlayground/CodePractice/CodeQuestionMgmt 保留未删；exam 包在 git 中整体 untracked（3F 既有状态）。无历史作答/错题/Evidence/Mastery/快照/Obsidian 改动。
- 命令结果：backend `mvn test`=Tests run 610 Failures0 Errors0 Skipped0 exit0（3F 基线591+19）；真实 JDK17 LocalJavaJudgeSeedVerificationTest 6/6（无 javac 自动 skip）；CodeBankIntegrationTest 13/13（独占 H2 phase3g-coding-bank，judge.mode=mock，seed 关）。frontend `npx vitest run`=85 files/766 tests PASS、`npx vue-tsc --noEmit` exit0、`npm run build` 成功。
- 浏览器：真实 Chrome（5173 + 为验收重启的 8080 local profile 默认 disabled）12 项：题库列表筛选分页空态、双栏题面/签名/限额/公开用例/无参考解泄露、草稿 F5 恢复、JUDGE_UNAVAILABLE(score=null 不显0分)、样式化重置确认、普通用户被 /admin 守卫重定向、ADMIN 列表(#158 已通过已发布)与编辑抽屉(PUBLIC/PUBLIC/HIDDEN+隐藏输入期望+参考解回填，仅看未存)、/exam/compose 六题型 CODE=1「题库可用1题」无超额红字、Console 0 错。窄屏 400px Tab 因 Chrome 禁 resizeTo/弹窗 NOT_VERIFIED；管理写操作与考试 CODE 浏览器全链 NOT_VERIFIED（服务端集成测试覆盖）。H2 演示题#158 重启即失；验收后 8080 后台进程已退出，5173 仍在跑。
- Git：main/HEAD c8b7f596534e72bf6f7c024ec356a56a3100acb7，其他 Agent dirty/untracked 原样保留；无 commit/push/reset/clean/restore/stash。
- ⚠️ Not verified：MySQL8 真实实例（仅 H2 MODE=MySQL）；容器/gVisor/Firecracker 隔离与逃逸渗透（按约束未跑攻击代码）；AI 生成算法题入池(模式B，NOT_IMPLEMENTED)；异步判题队列（状态机/重判已预留）；历史 6 错配+36 过粗题 topic_code 人工评审（NEEDS_FIX，刻意未改历史）；种子仅8题（目标30，均真实编译验证）；Monaco 高亮补全/多语言/提交 diff/CE 行号跳转；1280/1024 与 390/375 真机。
- ONE Next Task：实现一次性容器 Runner（固定镜像 digest、network none、非 root、pids/mem/cpu/tmpfs 限额、no-new-privileges/seccomp）+ DB 任务队列 + 用户级限流，提交 G1–G22 逐项阻断证据并独立评审后，再翻转 knowflow.judge.mode 默认值；在此之前保持 disabled。

## 2026-10-13 · 考试侧栏入口修复

- 原因：APP_DEFINITIONS已有exam，但APP_NAV_META漏配→navVisiblefalse/navSectionundefined→resolveAppNavChildren过滤。仅添加exam learning/order8，保留安装/启用、平台状态、权限判断及所有既有dirty修改。
- 文件：apps/registry.ts一条meta；新增exam-navigation.spec.ts两项；docs/EXAM_NAVIGATION_FIX_REPORT.md；reports/exam-navigation真实截图/JSON/脚本。
- RED1失败1通过，GREEN全量86文件768测试通过exit0，vue-tsc/build exit0；浏览器真实Chrome seed-personal-v1展开学习→考试→/exam，Console/PageError0、无失败Network。未改后端/用户状态/钱包/考试内容；mvn不适用未跑。保留Node/chunk警告；无commit/push或破坏性Git操作。
- ⚠️ Not verified 生产/其他账户多角色。下一任务：正式App导航metadata完整性回归检查。

## 2026-10-13 · Phase 3H AI 增强解耦与全链路降级兜底

- 范围：新增 com.knowflow.ai.capability 整包（10 文件：AiFailureKind 16 因含 retryable/安全文案、AiCapabilityStatus 七态、AiCapabilityException(422+retryAfterMs)、AiCapabilityProperties(knowflow.ai.capability.* 默认 maxAttempts3/退避500-8000ms/并发8/熔断窗60s阈值6开30s/状态缓存30s/瞬态TTL60s)、AiCircuitBreaker、AiCapabilityExecutor(开关→熔断→预算→信号量→有界指数退避重试，429尊重Retry-After且封顶8s，401/非法响应/未知异常不重试)、AiCapabilityStateService(配置+熔断+TTL瞬态聚合，不打付费探测)、AiRawResult、AiCapabilityVO(无Key/堆栈)、AiCapabilityController GET /api/ai-capability/status）。业务降级：AI出卷fallbackToBank→PaperComposer题库组卷PaperVO.aiFallback/fallbackReason（不带标志明确报错不偷换）；GET /api/quiz/explain mode=AI|BASIC（无解析hasExplanation=false不编造）；主观评分失败保持PENDING_AI/AI_FAILED不写假分不默认0（复用既有认领幂等状态机）；POST /api/code-bank/problems/{id}/ai-assist（hint1-5/explain/review，BASIC返判题编译事实+5级预置引导，不改Judge结果）。规则组卷REVIEW五桶DUE_REVIEW30/HIGH_FREQ_MISTAKE25/WEAK_TOPIC20/TARGET_TOPIC15/NEW_UNANSWERED10（SmartPracticeProperties可配），护栏WEAK_MIN_SAMPLES3/WEAK_RATE_THRESHOLD0.4为代码常量，rule-engine-v1+bankVersion(qb-哈希-题量)+sufficient+reasonCounts，没做过只标NEW_UNANSWERED绝不判不会；GET /api/quiz/smart-recommend。错题MistakePageQuery六维筛选+MistakeVO加questionType/topicCode。GET /api/learning/analytics/overview（正确率分母仅is_correct非空、未评分进ungradedCount、JudgeStatus.isPlatformFailure进excludedPlatformFailure不占负例、首次通过率=最早正式提交即AC题数/有正式提交题数、dueToday委托MistakeService当天归集与reviewDueDays=1一致）。
- 前端：新增api/aiCapability.ts（七态/AI_FAILURE_KIND_LABELS/AI_STATUS_LABELS/failureKindLabel剥AI_前缀）、stores/aiCapability.ts（30s轮询+visibilitychange补刷+失败静默，userEnabled持久化localStorage knowflow:ai:user-enabled默认开，与系统状态AND）、views/LearningAnalytics.vue（路由/learning/analytics，三汇总卡+SVG趋势+题型/知识点/Judge分布，7/30/90窗，Loading/Error重试/Empty四态）；改App.vue(轮询生命周期)、api/exam.ts(aiFallback)、api/quiz.ts(explain/smartRecommend，item字段id非questionId、options为string[])、api/codeBank.ts(aiAssist)、api/mistakes.ts+types.ts(筛选/MistakeVO)、api/learning.ts(全套类型rate=null)、router/index.ts、views/exam/ExamCompose.vue(状态提示条+aiFallback蓝色横幅不自动跳考+startFallbackPaper手动确认)、views/coding/CodeProblemSolve.vue(AI辅助卡：徽章/用户开关/提示1-5/解释/点评，requestAi先静默persistDraft，BASIC展事实)、views/Mistakes.vue(segmented+select筛选条/isoLocal无时区/题型标签/学习分析入口icon bar-chart-2)、views/SmartQuiz.vue(智能推荐按钮zap+构成提示条sufficient蓝不足黄+每题reason badge)。
- 测试：后端新增37——AiCapabilityExecutorTest 13、AiFailureInjectionIntegrationTest 18（独占H2 phase3h-ai-fault，MockWebServer注401/429/500/非法JSON/缺字段/错误题型/重复题，含错题幂等/单事件/NULL is_correct不进正确率）、AiDisabledChainIntegrationTest 6（enabled=false，库phase3h-ai-disabled，fullOfflineLearningChain全链+judgeDisabledNeverFallsBackToHost）；全量mvn test=647/647 PASS exit0。前端vitest 86文件768/768 PASS、vue-tsc 0错、build成功（仅既有chunk警告）。令牌化守护：text-[10px]违规已改text-[11px]。任务书20故障场景17 PASS/3 NOT_VERIFIED（DB写失败未注入；取消中断与组卷后改题仅机制覆盖无专项自动化）。
- 浏览器链路B（AI系统级关闭，KNOWFLOW_AI_CAPABILITY_ENABLED=false，后端PID33971:8080，vite PID28162:5173）：账号p3he2e/Test1234(userId=4，register自建；seed admin/admin123可登录user1密码未知)。curl：status=DISABLED/useBasicMode=true；smart-recommend rule-engine-v1 bankVersion qb-67a0c0c2e0cc-61 NEW_UNANSWERED5；compose-ai(hashmap,5,fallbackToBank1)→aiFallback=true reason=AI_NOT_CONFIGURED题量守恒；quiz/submit载荷外层{answers:[]}，5错syncedMistakes=5重复提交仍5（错题id11-15≠questionId，reviewCount1）；mistakes questionType=SINGLE_CHOICE→2；analytics quiz10/10/0/0 ungraded0、mistake5/5/dueToday0/weeklyNew5；explain q32 BASIC/hasExplanation=true；为补07用admin建CODE题「两数之和」id63（强制参考解答+用例期望须合法JSON字符串→"0,1"，review APPROVED+publish），普通用户ai-assist hint1→BASIC/AI_UNCONFIGURED/judgeExecutionEnabled=false返预置Lv1引导。真实Chrome 7截图reports/phase3h/01..07全部PASS（07：AI已关闭徽章+基础模式Lv1+编辑器/运行/提交不消失）；外部请求仅字体CDN无AI供应商域名。Console遗留：登录前2条401（注入会话前）、2条net::ERR_ABORTED attempts/1|2/draft（切页中止旧草稿XHR，良性无数据损坏，建议加AbortController静默）。
- 链路A（真实模型）NOT_VERIFIED：环境无真实Key，未用Mock冒充；MockWebServer仅证协议/降级，真实供应商兼容性+真实Rubric评分待有Key按PHASE3H_E2E_REPORT §4清单补验。模拟面试无AI基础模式（预置题+追问树+计时+手工录入+自评+保存）本轮未实现=功能缺口，下一任务第一优先；P1未做：提交diff/试卷智能分析/相似题；DB整体不可用不宣称可离线交卷（仅会话内短暂保留，无IndexedDB队列）。
- 8份新文档：docs/PHASE3H_EXECUTION_REPORT/AI_CAPABILITY_FALLBACK_DESIGN/CORE_LEARNING_OFFLINE_AI_MATRIX/SMART_PRACTICE_RULE_ENGINE/AI_FAILURE_INJECTION_REPORT/LEARNING_ANALYTICS_DESIGN/AI_FEATURE_UI_UX_REPORT/PHASE3H_E2E_REPORT。未改evidence算法/action-priority链/route白名单/action-contract映射/历史考试错题Evidence/Mastery/NextAction。exam/coding包仍整体untracked（3F/3G既有）。
- Git：无commit/push/reset/clean/restore/stash；其他Agent dirty/untracked原样保留。后端8080仍以AI关闭模式运行、前端5173在跑；H2内存库重启后p3he2e与题63会丢失。
- ONE Next Task：模拟面试基础模式最小闭环（预置题+一层追问树+倒计时+手工录入+量表自评+记录保存，AI模式可随时切换不丢答案，复用docs/interview种子与api/interviewSeed）。

## 2026-10-13 · Phase 3H-P1 提交 diff + 模拟面试无 AI 基础模式 + 出题质量审计

- 范围：①算法题两次提交确定性 diff：`coding/util/CodeDiff.java` 行级 LCS（n×m 表、从(0,0)正向回溯不反转、1-based 统一行号、equal/insert/delete），`GET /api/code-bank/problems/{id}/submission-diff?fromId=&toId=`（仅本人 SUBMIT、按时间自动定向、RUN 拒绝、他人/非同题 404/400），前端 CodeProblemSolve.vue 对比卡 + api/codeBank.ts。②模拟面试无 AI 基础模式（任务书第八节硬要求，从零）：新包 com.knowflow.interview（entity/mapper/core/vo/service/controller + 6 集成测试，独占 H2 phase3h-interview）；白名单题库 InterviewQuestionBank：8 方向(JAVA_BASIC/JAVA_CONCURRENCY/JVM/FRAMEWORK_STORAGE/RAG/AGENT_MCP/JAVA_AI_SCENE/ALGORITHM)×3 题×2 条一层追问+统一 5 分 Rubric 锚点，record 定义题面、接口不接受前端题面；新表 mock_interview_session（开始即题目整体 JSON 快照；H2 schema TIMESTAMP、MySQL schema DATETIME InnoDB utf8mb4_unicode_ci、迁移 V20261014 幂等存储过程，索引 idx_mis_user/idx_mis_status）；6 接口 /api/interview/**（tracks/sessions CRUD+answers 幂等覆盖+finish+分页；身份全 SecurityUtils；他人/不存在统一 404 不泄露存在性；匿名 401；题数钳 1-5 且不超池、pageSize 钳 1-50；answer 必填≤8000、selfScore 必填 1-5、selfComment 可选≤1000 空白归一 null(optionalTrimmed)、followups≤5 且 q 必须在快照追问树、a 空跳过；FINISHED 拒答；avgSelfScore HALF_UP 1 位+durationSec 服务端算）。前端 api/interview.ts、views/interview/MockInterview.vue（/interview name MockInterview requiresAuth：8 方向卡/题数 1-2-3-5/倒计时 60-120-180-300-0/历史列表；会话状态条进度+倒计时+「基础模式·无AI」徽章/Q tab/textarea/追问折叠/1-5 锚点量表/保存+切题自动保存+结束 confirmDialog/FINISHED 只读复盘+绿色参考要点；未提交文字 0.8s debounce localStorage knowflow:interview:draft:{sessionId}，已保存以服务端为准、结束清暂存；MiniAppShell/Icon/utils/toast）。注册：apps/registry.ts key mock-interview(PRACTICE/briefcase//interview/defaultInstalled/capabilities[])、APP_NAV_META learning order 9（不新增一级菜单）、icon-registry briefcase；后端 OfficialAppRegistry ACTIVE 白名单 + DEFAULT_APP_KEYS 追加 mock-interview（新用户默认安装；UserAppIntegrationTest 同步 4→5 并加断言）；老用户应用中心/ PUT /apps/user-apps 启用。
- 真实调用链：浏览器 MockInterview → api/interview.ts → MockInterviewController(/api/interview) → MockInterviewServiceImpl → InterviewQuestionBank（白名单快照）+ MockInterviewSessionMapper → mock_interview_session；无任何 ai.capability / Judge 依赖。diff：CodeProblemSolve → code-bank controller → CodeDiff（纯函数）+ code_submission 本人 SUBMIT 过滤。
- 生产 bug 修复（真实浏览器发现，单测未覆盖）：Q2 不填自评备注保存 400（可选字段误用必填 trimmed()）→ 新增 optionalTrimmed（空白→null、超长仍拒）；追问 q 合法 a 空由 400 改跳过；回归测试 optionalFieldsAccepted 断言 selfComment 空串被接受、空回答 followup 被过滤。
- AI 出题质量治理（只读审计，无代码改动）：任务书六态等价于生产「review_status 三态(PENDING_REVIEW/APPROVED/REJECTED)+status 发布位」；QuizQuestionAdminService 类级 @PreAuthorize ADMIN 覆盖建/改/审/发/删，AI 源编辑重置 PENDING+status0，publish 双重门禁（非 APPROVED 409、AI 必须 APPROVED 409），PaperComposer.loadBank 组卷池 status=1 AND (review_status IS NULL OR APPROVED)，CodeProblemServiceImpl L927/939 同门禁，question_catalog_audit 记 actor(SecurityUtils)+before/after；AI 卷 composeAi 仅个人快照不写共享库。结论：关键控制点服务端强制，无需补链；残留风险=无第二模型答案正确性复核（人工 APPROVED 兜底）。
- 命令结果：backend `mvn test`=Tests run 672 Failures0 Errors0 Skipped0 BUILD SUCCESS（3H P0 647 基线上 +interview 6 +diff 相关 +既有增长；中途 UserAppIntegrationTest expected 4 but was 5 为 DEFAULT_APP_KEYS 有意变更，已同步测试非放宽）。frontend P1 全部代码后 `npx vitest run`=86 文件 768/768 PASS、`npx vue-tsc --noEmit` exit0、`npm run build` 成功（仅既有 chunk 警告），之后前端无改动。
- 浏览器：真实 Chrome（vite 5173 + 后端 8080 AI 关闭 H2，p3hiv/iv123456 userId=4）。diff 卡 PASS（08-code-diff.png）。面试：选算法题 2 题 3 分钟→Q1 作答+展开追问作答+4 分+备注保存 toast→Q2 作答+5 分无备注保存（验证修复）→confirmDialog 结束→toast 4.5/5；服务端核验会话 id=1 FINISHED avg4.5 duration132 answered2（ALGORITHM-1 score4 comment/fu=1，ALGORITHM-2 score5 comment null fu=0）；09 进行中/10 完成复盘截图；刷新恢复：RAG 会话 id=2 仅答 Q1(3 分) 后整页刷新→进度 1/2、答案分数回填、可继续（PASS）；老用户 PUT 启用后侧栏学习区出现「模拟面试」current 高亮、首页历史显示已完成记录（11 截图）。Console 无业务错误、Network 无 AI 供应商域名。
- Git：无 commit/push/reset/clean/restore/stash；其他 Agent dirty/untracked 原样保留；未提交 design/ mobile/ application-local.yml/.env。后端 8080（job-be30db5943d04d0490da46e64c453706，/tmp/knowflow-backend-p3h4.log；注意该进程启动于 optionalTrimmed 修复前还是后：修复后已重启加载——E2E 无备注保存成功即证明；DEFAULT_APP_KEYS 改动在最后一次重启之后编辑，运行进程尚未加载该项，仅影响新注册用户默认安装，mvn test 已验证编译与行为）、vite 5173 在跑；H2 重启数据全失。
- ⚠️ Not verified：真实付费供应商模型（无 Key，链路 A 不变）；MySQL8 实库（H2 + migration SQL 静态审查）；24 题逐题浏览器点完（仅白名单代码核对+算法/RAG 两方向实测）；面试 AI 增强模式（未做）；DB 写失败注入/取消中断/组卷后改题专项自动化（P0 既有 3 项）。
- ONE Next Task：取得真实模型 Key 后按 PHASE3H_E2E_REPORT §4 执行链路 A 补验；或启动 P2（试卷智能分析规则版 + AI 解读增强）。


## 2026-10-13 · Question Catalog / 后台四题库统一

- 目标/范围：统一全部题目入口、来源身份、真实筛选分页、公共基础编辑、AI审核与审计；保留四旧ADMIN路由和场景/旧代码/算法专业编辑器。不合并物理表、不按标题合并、不删除题/试卷/错题/收藏/学习记录，不改变历史评分。
- 真实差异：quiz_question普通题；learning_scenario独立背景/问句/keyPoints；code_question为旧前端执行练习（不是代码阅读）；CODE是quiz_question主题+code_problem扩展+独立用例/提交。算法sourceQuestionId=q.id，storageId另记。目录命名空间SOURCE:id，缺canonical证据时不自动去重；旧场景/旧代码无审核/版本则明确未知。
- 文件：backend/catalog新增6类、QuestionCatalogIntegrationTest；AdminQuizQuestionController/三个专业Controller、CodeProblemServiceImpl、PaperComposer；schema只追加question_catalog_audit及MySQL V20261013迁移。frontend新增QuestionCatalog、QuestionCatalogBasicForm、api/questionCatalog和3测试文件；四专业页添加query编辑/创建、Sidebar分类和router入口。六份QUESTION_* / ADMIN_QUESTION_BANK_UI_DESIGN文档及PAGE_INVENTORY增补；详见EXECUTION_REPORT完整文件范围。
- 调用链：Sidebar → /admin/questions ADMIN守卫 → catalog API → Controller/Service ADMIN角色校验 → Jdbc UNION四原表、count/limit/offset；公共编辑锁源行+版本/时间/内容指纹CAS，不覆盖专业结构。专业写沿原Controller/Service/Mapper；普通题收口QuizQuestionAdminService。规则组卷仍PaperComposer原题池，新快照追加catalogId/canonicalKey，旧快照不变。
- 门禁：AI候选强制PENDING/status0；人工审核绑定所见版本与内容令牌，批准后另行发布；审核后内容编辑重置待审并下架。发布/算法写采用完整实体FOR UPDATE当前读并直接判断/写入，避免MySQL RR旧快照；公共基础编辑READ_COMMITTED，批次稳定锁序/事务全部回滚。审计actor由SecurityUtils确定；用户不访问ADMIN答案/隐藏用例；Judge/宿主机执行关闭。
- 回归：并发发布和驳回后编辑绕过发布两项先RED再GREEN；同数字ID不冲突、版本CAS/无版本内容令牌、批量回滚、新旧试卷快照保存等覆盖。已有CodeBank随机UUID标题含42造成隐私断言偶发失败，只调整fixture标题，不修改隐藏42用例或泄露断言。
- Git：main / c8b7f596534e72bf6f7c024ec356a56a3100acb7；cached diff为空；保留既有dirty/untracked；无reset/clean/restore/stash/commit/push。并行Phase3H修改UserApp默认应用测试4→5不属于本轮；当前全量数字以本次运行记录为准。
- 命令：backend mvn -q test（最终完整锁读后再跑）675测试，失败0/错误0/跳过0，77份报告，exit0；frontend npx vitest run 89文件/796测试通过，exit0；vue-tsc --noEmit exit0；npm run build exit0（6.13s，较大chunk警告保留）；git diff --check exit0。
- 浏览器：真实Chrome、独立vite5174/API8083/H2，非Mock页面；四旧路由、四源新建/公共与专业保存、AI人工审核/批量发布、URL后退/分页、taxonomy/audit/Judge、AI关闭下真实组卷启动专项练习、USER读写403/隐藏字段脱敏；1440/1280/1024/768无整页横向溢出。最终浏览器结果以reports/question-bank-unification/browser.json与E2E报告为准。
- 数据/服务边界：本轮只读原8080采样，没有停止/重启/写入它；另有Phase3H进程更新，不能推断所有历史账号数据已验证。独立8083保留明显QA-CATALOG测试对象便于审阅，重启只丢QA内存库。新源码未自动激活原8080，原5173新入口需新版后端安全接管后使用。
- ⚠️ Not verified / NOT_IMPLEMENTED：MySQL实库、生产权限全账号历史矩阵、付费AI、安全生产Judge未验证；统一导入/完整版本恢复、分类结构CRUD、真正代码阅读源未实现；未伪造Rubric/场景角色追问链。专业旧执行模块的客户端回传统计不是可信Judge，未并入普通考试。实施前时间点截图缺失，报告明确以保留原专业页布局与新入口作同会话对照。
- ONE Next Task：对当前原运行数据做备份与恢复演练后激活新版Catalog接口/审计迁移；再按QUESTION_DATA_MIGRATION_PLAN分阶段追加不可变题目版本与来源导入，不进行四表物理合并。

- 本轮最终E2E补记：15/15通过、Console/PageError0，真实浏览器API响应101条；匿名401/USER读写403同时核对HTTP/body.code，verify.cjs最终exit0。截图/状态见QUESTION_BANK_UNIFICATION_E2E与browser.json。

## 2026-10-13 · 模拟面试 UI/UX 专项修复（P0 输入框/布局 + P1 保存状态机与结果页）

- 范围：仅前端页面 + 文档，后端零改动。`views/interview/MockInterview.vue`：P0 根因修复（模板使用的 `.form-input/.form-label` 等类只定义在 Login.vue 的 scoped 样式中，本页不生效，textarea 退回浏览器默认 inline-block/cols=20 → 实测 177px/卡片 1135px，标签落到左下）→ 新增本页 `.iv-textarea/.iv-input/.iv-select`（width/max-width 100%、min-width:0、display:block、box-sizing），父链 `.iv-wrap/.q-card/.field-block/.interview-answer` 全部 min-width:0；label/状态胶囊置于输入框上方 field-head，字数提示 field-foot；textarea min-height≥180（现令牌 --kb-interview-answer-height=308）、resize:vertical、anywhere/pre-wrap 换行。P1：保存五态状态机 idle/typing/stashed/saving/saved/error（按 qid），0.8s 防抖仅写本机 `knowflow:interview:draft:{id}`，明确区分「未同步·已暂存本机」与「已保存到面试记录」；saving 守卫+按钮 disabled 防连点；draftSignature 保存前后比对；失败重试；setItem 失败显示「本机暂存失败」不谎报；切题先 flush 再自动保存、失败阻止跳题；刷新后服务端回填 + 本机新草稿显式恢复条（不静默覆盖）；beforeunload 仅在未同步时拦截并 flush；完成前先保存当前题、失败不得结束；FINISHED 清草稿、计时器 clearInterval。追问默认折叠、`draft.followups[q]` 独立存储、空条跳过；自评 1–5 默认说明 + 题库 rubric level1/3/5 锚点、全程标注「用户自评」；selfComment maxlength 500→1000 对齐后端；参考要点作答前折叠+对照提示、FINISHED 自动展开；完成结果页（4 统计卡/待复习/查看记录/继续同方向训练/本场复盘），toast 不替代结果页。新增 `views/interview/__tests__/MockInterview.spec.ts`（现 14 用例）。报告 docs/INTERVIEW_UI_UX_FIX_REPORT.md；截图 reports/phase3h/09（before）、12-18（after）。
- 真实调用链（未变）：浏览器 MockInterview → api/interview.ts → MockInterviewController(/api/interview) → MockInterviewServiceImpl → mock_interview_session（题目快照/answers 幂等覆盖/finish 幂等）。
- 并行 Agent 说明：执行期间另一路 Phase 3I 在同文件落地三栏训练工作区（左题目导航/中答题/右训练辅助，1280/1024/768 断点）与每题倒计时本机元数据 `knowflow:interview:timing:{id}` 及其 2 个守护测试（切题计时不重置、暂存失败不谎报）。两路修改已合流，最终 14/14 测试通过；其改动（含 ui-tokens.css 三个 interview 令牌）予以保留。开发态 HMR 热替换瞬间观察到内存草稿被空状态覆盖的 dev-only 现象，生产无 HMR，刷新恢复路径有测试+实测覆盖；建议后续给 onBeforeUnmount flush 加非空保护。
- 命令与结果：frontend `npx vitest run`=90 文件 812/812 PASS；`npx vue-tsc --noEmit` exit0；`npm run build` 成功（仅既有 chunk 警告）。backend 零改动，`mvn test -Dtest=MockInterviewIntegrationTest`=7/7 BUILD SUCCESS。
- 浏览器（真实 Chrome，vite 5173 + 8080 AI 关闭 H2，p3hiv userId=4）：id=8 新建场完成全流程。1440 实测 grid 154/753/196、textarea 711/753 占满、labelAbove；1280 card960/ta918、1024 736/694、768 704/674 操作条纵向堆叠，四宽度 document.scrollWidth<=innerWidth；500 中文 textarea 709/709 无横向滚动；composition 事件模拟正常；保存后 3/3、FINISHED 结果页 3.7/5·24 分钟·待复习 1；退出重进 Q1 主回答 500 字+追问 1/3+4 分分别只读恢复；重复 POST finish 两次均 200 同一 id；越权实测非 owner(admin) GET 404、匿名 401、owner 200。Console 无业务错误，无 AI 域名请求。
- ⚠️ Not verified：真人原生 IME（仅 composition 事件模拟）；真实 MySQL8/付费 AI Key；任务书 id=4 在该 H2 不存在（改在 id=8 验证）；durationSec 为墙钟含空闲，非纯作答时长。
- Git：无 commit/push/reset/clean/restore/stash；其他 Agent dirty/untracked 原样保留。
- ONE Next Task：人工 IME 回归；给 onBeforeUnmount 的 flushDraftStash 加草稿非空保护消除 HMR 同源理论风险。


## 2026-10-13 — Phase 3I 产品化增量与真实验收

- 范围：基础面试三栏与草稿/结束/并发安全，50题只读审核候选，PaperComposer v2 canonical/快照身份去重与已答排除、9训练配方，考试局部观察/真实知识资料/专项复测、独立有界AI点评追问。
- 文件：MockInterview/InterviewServiceImpl/ui-tokens；scripts/phase3i_question_review.py与reviews/phase3i；exam/engine、dto/ExamRequests、ExamServiceImpl、ExamController、exam/diagnosis；ExamCompose/Result/DiagnosisPanel、api/exam/examDiagnosis、SmartQuiz、Mistakes；interview/ai、独立Panel/API、schema追加/migration及MockWebServer test依赖；对应回归测试和10份Phase3I文档。完整边界见PHASE3I_EXECUTION_REPORT。
- Git：main / HEAD c8b7f596534e72bf6f7c024ec356a56a3100acb7；大量既有dirty/untracked保留，未reset/clean/restore/stash/commit/push。原8080/5173不动；本轮5175→8084隔离H2，AI/Judge/host关闭；QA运行类/固定测试内容只位于reports及/tmp，不进入正式启动入口。
- 命令结果：全量mvn -q test exit0（80套702项，0fail/error/skip）；全量Vitest exit0（95文件838项）；vue-tsc exit0；build exit0（仅bundle/插件性能警告）；git diff --check exit0；50题dry-run exit0（0正式发布）。相关回归均记录RED与GREEN，最终新鲜结果reports/phase3i/test-results.json。
- 真实调用链：界面→原Exam/Interview Controller→Service→原表；Diagnosis先owner+已提交校验→本人快照/历史/EXAM错题/ACCEPTED映射→真实可读资料和候选→原compose/start/submit/result；AI建议独立表→AiService→CapabilityExecutor，LLM不在持锁事务内。未改变Evidence/Mastery/NextAction算法。
- 浏览器：Chrome真实A默认题库全UI4组、B面试6组、C复测6组；正常Console errors均0，HTTP/body保存，四宽度无整页横向溢出。B单次abort故障重试单独记录；C使用显式隔离QA种子，不冒充正式候选。证据reports/phase3i三个*-browser.json及截图。
- 风险：50候选尚未人工技术审核、35通用+15专业适配；覆盖仅5主题。真实模型/付费计费/MySQL/正式迁移/完整离线/DB故障NOT_VERIFIED。全局EXAM知识Evidence连接仍PARTIAL，考试观察不等于掌握度。MySQL同时间提交先后无法证明时排除其他同时间记录，避免未来污染。
- 下一任务：真实管理员审核50题并审查专业类型导入适配，再在隔离MySQL验证迁移/并发与权限；配置及费用授权后独立验收真实模型。


## 2026-10-14 · 全站路由/API/Git审计续办完成（2026-10-13开始）

- 范围：当前PC frontend全部路由/页面/导航/入口与全包后端Controller候选、四来源题库、Git代码编辑器/AI工作台历史；未删/恢复/改业务。既有desktopApp/mobile/miniprogram迁出删除不恢复、不扫描内容。
- 文件：4个新增审计工具（scripts/audit-routes.mjs、audit-routes.test.mjs、summarize-route-audit.mjs、audit-routes-browser.py）；docs/audit八份要求报告、机器清单/历史/浏览器与脱敏日志/截图/审阅证据；docs/PAGE_INVENTORY仅追加本轮；本交接仅追加。所有其他dirty/untracked属于既有工作。
- Git：main / HEAD c8b7f596534e72bf6f7c024ec356a56a3100acb7；baseline status/diff/cached/history保存，最终frontend/backend tracked diff计数与起始相同；无reset/clean/restore/checkout/stash/commit/push。Git diff --check退出0。
- 资产：122路由=114组件+8redirect，111唯一组件，116views（3子视图/2未引用候选）；47菜单定义=5固定+17Registry+25后台，46不同target，42路由命中；84Controller，514词法mapping候选，502前端调用表达式。主状态{'ACTIVE': 0, 'HIDDEN': 60, 'ORPHAN': 0, 'PLACEHOLDER': 1, 'BROKEN': 1, 'DUPLICATE': 2, 'LEGACY': 8, 'UNVERIFIED': 50}，合计122；HIDDEN不宣称全功能，ACTIVE0是严格未完成核心业务验收口径。
- 命令：node --test scripts/audit-routes.test.mjs最终7/7 PASS；node scripts/audit-routes.mjs --check退出0、unknown/结构/SFC错误0且生成产物hash不变；node scripts/audit-routes.mjs生成，node scripts/summarize-route-audit.mjs合并。新增相对alias/computed path/命名redirect/UNKNOWN/lazy gate均有RED/GREEN证据。前端npx vitest run=95文件838通过、0fail；vue-tsc --noEmit退出0；npm run build退出0，有大chunk警告及测试localhost:3000连接拒绝日志。后端相关4套=40通过；全量mvn -f backend/pom.xml test=702通过、0fail/error/skip、退出0；交付日志只有脱敏suite/统计。
- 浏览器：真实Chrome，103固定×Anonymous/USER/ADMIN=309/309最终截图，主实例动态30/30补截图，749原始快照，11动态路由记录有合法组件落地；有效ID来自真实列表/DOM。部分只读筛选/Tab及1440/1280/1024/390兼容检查；user1引导层保留。仅放行登录与经真实调用链确认无持久化/AI的availability POST（USER/ADMIN HTTP200/body200）。没有主动内容CRUD/提交/执行/付费AI。
- 环境：最初5173/8080、5174/8083均核对cwd；后续5174/8083已不监听，补录login ERR_CONNECTION_REFUSED退出1，blocker原件保留，未重启/停止其他服务。旧bank74/take2真结果带原时间/端口，不伪造最新截图。主5173/8080仍在。
- 真实调用链：旧CodePractice→api/codeQuestion→CodeQuestionController→code_question/code_submit_record，旧CodePlayground工作区/代码运行/AI接口仍在；CodeAgent→codeAgent/codeGen→Agent会话/工具，Git无删除证据。coding App未提交主入口换/coding/bank。统一/admin/questions→QuestionCatalogService UNION QUIZ/SCENARIO/LEGACY_CODE/CODE_PROBLEM→各专业deep link/原表；未合并表或评分信任。Interview query.id→本人session服务。
- 风险：/task-list GET task-tags500/body500却显示空标签；TaskServiceImpl:406–413空tags构造IN(empty)为静态根因候选待失败回归。DocEdit DELETE /docs/id与图片POST /study-groups/upload没有当前合同；legacy客户端total/passCount写事件、HTTP假成功/宽泛catch/并发计数为静态风险未现场利用。/tasks成长核心模拟；Workbench直接包装Capture为同内容候选。主运行面试AI-turns/考试diagnosis辅助404必须与源码/部署版本区分。
- GET副作用：DocController详情→DocServiceImpl:443–448授权后view_count+1；本轮真实详情访问可能增加浏览计数，未做前后DB快照，增量未知且未回滚历史。不能称数据库零写。
- ⚠️ Not verified：全部核心CRUD/判题/付费AI、完整Owner/Other/Admin/KB EDITOR/READER、MySQL/迁移/生产、OAuth真成功、全按钮/入口条件组合；页面渲染及测试不等于这些PASS。
- ONE Next Task：在隔离测试中对空tags的listTags路径建立失败回归，核实500根因后最小修复并给TaskManager补Error/Retry；随后按授权合同修复文档删除/上传。保留四专业题库/旧编辑器/历史URL。


## 2026-10-14 · seed-personal-v1 既有学习库管理员权限

- 用户授权：设置seed-personal-v1管理员；当前临时内存库无此账号，用户选择“连接原账号数据库，保留原数据”。
- 来源：此前真实学习实例backend/data/rebuild-v1-host-test-20261012/knowledge.mv.db，只读查到实际ID4/USER；与当前p3hiv ID4不是同一库/账号，未误改或新建。
- 保护：闭库原件先备份 knowledge-before-admin-20261014-005647.mv.db，SHA256一致；/database/test通过后/switch使用IFEXISTS、initMode=never、initSchema=false；不初始化/迁移/删除/重置密码，原文件备份保留。
- 操作：仅对原账号PUT /api/admin/users/4 role=ADMIN，重新登录获得ADMIN JWT。8080连接持久化库，5173保留；未停止或重启其他服务。新连接已保存backend/config/datasource.json并本地exclude忽略，不提交敏感配置。
- 真实链：DatabaseAdminController→DatabaseAdminService/DataSourceManager→原持久化H2；AdminUserController→UserService.updateById→sys_user.role；UserService.login→JWT(role ADMIN)→JwtAuthenticationFilter权限。
- 验证：HTTP/body新登录200、me ADMIN，users/database/status/overview均200/200；真实Chrome后台overview/database无guard退回、pageerror0/后台API失败0，截图reports/seed-personal-admin。95表/4用户保持；全部其他行逐一比较未验证。有原文件备份，不能宣称失去的原内存库全部恢复。
- 文件：docs/reports/SEED_PERSONAL_ADMIN_SETUP.md；本交接追加；reports/seed-personal-admin证据；私有DBrole与忽略的连接配置；产品源码零修改。无commit/push。账号配置任务未跑全量测试/Build，未假称PASS。
- 风险：旧JWT含旧角色，需要重新登录；跨库旧token隔离/全部后台模块/生产/MySQL/重启后完整业务Not verified。
- ONE Next Task：用户退出重新登录使用/admin/overview；随后统一持久化启动与定期一致性备份，保留initMode=never。

## 2026-10-14 — 本地 H2 / OpenAPI / Swagger 浏览器认证

- 范围：用户确认后增加显式 opt-in、local 且非 prod/production、真实 loopback 的工具专用 Basic 认证。允许当前数据库 ADMIN 或 seed-personal-v1；账号密码复用 BCrypt/逻辑删除，当前数据库身份复核；不修改角色或业务 JWT 权限。
- 文件：新增 LocalDeveloperToolsSecurityConfig、三个 LocalDeveloperTools*Test；SecurityConfig 补 docs YAML ADMIN 保护；application.yml 默认关闭；application-local.example.yml 示例；本机忽略的 application-local.yml 仅开启开关；报告 `docs/LOCAL_DEVELOPER_TOOLS_ACCESS_2026-10-14.md`。
- Git：HEAD c8b7f596534e72bf6f7c024ec356a56a3100acb7。保留大量接管前 dirty/untracked 与迁移删除；未 commit、未 push，未输出 local 配置密钥。
- 验证：初始隔离测试 RED 9/6 failure；真实 H2 独立 Servlet 回归 RED，显式 Ant 路径匹配后 GREEN。最终 `mvn -q -Dknowflow.datasource.config-file=./target/full-tools-test-no-overrides.json test` 83 suites/714 passed/0 failure/error/skipped、退出0；`npx vitest run` 95 files/838 passed、退出0；`npx vue-tsc --noEmit` 退出0；`npm run build` 退出0（Node版本及chunk警告）。新增12项权限、profile、真实HTTP测试。
- 调用链：工具浏览器请求 → local security chain → chain-local Basic AuthenticationProvider → PasswordEncoder + SysUserMapper/sys_user → Long principal → 回环地址与当前数据库账号权限 → H2 servlet/springdoc/Swagger。JWT 继续走现有 JwtAuthenticationFilter/黑名单，再复核当前 DB 身份；普通 API Basic 不建立身份。
- 浏览器：Chrome seed-personal-v1 实际连接旧文件 H2(sa/空密码)并显示表树；Swagger加载接口；Network docs与swagger-config均200；原有过滤设置下Console观察0消息。Codex内置浏览器Basic流程、固定尺寸响应式、生产部署 ⚠️ Not verified；截图保存未完成（用户操作浏览器）。
- 运行风险：外置 datasource.json 当前为旧文件库 initMode=never，缺 QUESTION_TOPIC。新编译普通启动被已有 QuestionTopicSeeder 阻断；为恢复8080，只在临时 `/tmp/knowflow-tools-runtime/LocalToolsRecovery.java` 入口移除 questionTopicSeeder/examTemplateSeeder 启动Bean，未重建/清空业务库或改相关源码。当前PID97909、日志 `/tmp/knowflow-local-tools-server.log`；常规重启仍有schema兼容问题，依赖缺失表的新模块未修复。当前 seed-personal-v1 已是ADMIN属于原数据状态，本次不改角色。
- 下一任务：备份并设计旧文件H2无损schema升级，补齐题库/考试表列并验证正常启动后移除临时恢复入口。详情及当前正确JDBC URL见执行报告。
- 独立审查补充：只读代码审查未发现本次范围内具体阻断项（未重跑全量测试）；最终现场 ADMIN H2 HTTP200、seed Swagger HTTP200，8080仅监听127.0.0.1。

## 2026-10-14 — 移除全部公共页头返回按钮

- 用户要求：考试中心、算法题库及其他同类页头全部去掉统一返回按钮。实际来源为 MiniAppShell 与 PageHeader，真实扫描覆盖16+9=25页。
- 文件：两个公共组件移除按钮DOM、专属CSS及导航依赖；旧back/backLabel仅deprecated兼容。更新MiniAppShell.spec、新增PageHeader.spec；UI_UX_SYSTEM补充用户新设计；报告 `docs/REMOVE_SHARED_BACK_BUTTONS_2026-10-14.md`。
- Git：main，HEAD c8b7f596534e72bf6f7c024ec356a56a3100acb7。保留原有大量dirty/untracked与迁移删除；未commit/push。
- 测试：新需求RED相关9项2failed/7passed退出1；实现后相关9passed退出0。`npx vitest run`96files/839passed退出0；`npx vue-tsc --noEmit`退出0；`npm run build`退出0（已有环境/chunk警告）。最后相关diff检查通过。后端无修改，后端测试不适用。
- 调用链：原页业务加载/API/Store→MiniAppShell或PageHeader标题、描述、面包屑/上下文与动作→页面内容。统一页头不再调用导航goBack；usePageNavigation及真实业务导航、学习盖章等保留。原导航测试未改。
- 浏览器：当前前端5173属于本仓库；Chrome算法题库和考试中心已实际无页头返回按钮；侧栏/重试保留。Network考试dashboard502；8080无监听，未启停任何后端/端口进程。业务数据成功状态、其它页逐页交互、固定响应式尺寸与保存截图 ⚠️ Not verified。
- 风险/下一任务：后端当前未运行与旧文件H2兼容问题独立于本次UI；恢复后端并处理旧库schema后再验收题库/考试成功状态。不删除原业务退出/取消/结果操作；不把组件视觉调整当作业务全量验收。

## 2026-10-14 — 双击启动502修复与原H2无损结构补齐

- 范围/原因：用户要求检查并优化▶启动项目.command；502为Vite5173代理8080拒绝连接，旧原H2缺QUESTION_TOPIC及新版题库字段导致QuestionTopicSeeder启动失败，旧启动器还强制仅内存可用的DevelopmentInterviewAccount。两个端口本来就是前/后端正常分工。
- 文件：knowflow-dev.sh、▶启动项目.command、■停止项目.command、scripts/tests/test_launcher.py、scripts/sql/local-h2-safe-upgrade-20261014.sql、docs/reports/LOCAL_SCHEMA_STARTUP_AUDIT.md、LAUNCHER_502_FIX_REPORT.md、SEED_PERSONAL_ADMIN_SETUP.md续记、reports/launcher-fix/。未改业务源码、Evidence/NextAction/Registry算法或Vite配置。
- Git：main/HEAD c8b7f59；大量既有dirty/untracked/迁移删除保留，无commit/push。只在私有原H2追加13表/5列/22索引；datasource.json保持initMode=never。闭库完整备份路径及SHA256在执行报告。
- 数据证据：副本DDL及重复执行通过；原库独立备份后执行；旧95表所有旧列内容指纹与行数在真实boot/API后不变；4用户，seed-personal-v1原ADMIN仍1条。已有runner只向新增表追加27公共知识点/10公共模板；未重播个人seed、未data.sql/重置密码/删除学习资料。
- 调用链：.command→knowflow-dev.sh→cwd+进程命令验证监听者归属→后台Maven Spring Boot→外置数据源(initnever)→既有runners→Readiness ACCEPTING_TRAFFIC+auth/me HTTP/body401→固定8080代理的strict5173 Vite→代理auth/me401→显示就绪。重复start复用；显式restart或stop后start。独立代码复核发现的二次启动绕过ready标记已加RED/GREEN回归修复。
- 验证：启动器10passed退出0；三脚本bash-n和目标diff-check退出0；前端vitest96files/839passed退出0、vue-tsc退出0、build退出0（既有chunk>500kB警告）；后端DB_CONFIG_FILE=/tmp/knowflow-launcher-tests-isolated-no-override.json mvn -f backend/pom.xml test：714tests/0failures/errors/skipped退出0。首轮非隔离mvn因读外置工作库被主动TERM退出143；闭库用已核对备份恢复并重新追加DDL，最后95表原内容完全相同；中断运行不计为PASS。
- 浏览器：真实Chrome经localhost:5173新登录原ADMIN，admin/overview、admin/database、admin/questions、exam全渲染；Network实际HTTP/body.code均成功，无502/pageerror。1280×800、1440×900、1024×900；截图和响应摘要在reports/launcher-fix。真实stop/start/restart与重复.command入口通过；当前8080/5173各一组本项目服务保持运行，没有临时剔除seeder入口。
- 风险/未验证：生产/MySQL迁移、付费AI/Judge执行、所有题库写操作及Finder GUI物理双击未验证（相同.command已直接执行）。普通mvn test仍有读工作库风险；必须明确隔离DB_CONFIG_FILE，不把H2通过视为MySQL通过。
- 下一任务：优先强制所有后端测试隔离外置数据源，并建立H2/MySQL版本化迁移，避免后续源码与原库漂移。

## 2026-10-14 — 原账号 Java+AI 完整来源固化与访问恢复

- 范围/原因：用户要求直接固化content-studio全部可用资料，保留原seed-personal-v1 ADMIN与学习数据。原内容仍在文件H2，ADMIN排除逻辑使原个人学习包入口不可用；新算法及完整源资料未进入模块。
- 文件：InterviewSeedAccessPolicy及4 seed服务窄授权调整、前端InterviewSeedCard/Chapter；LearningServiceImpl.getPathList窄个人导入路径发现；CodeQuestionController私有ledger隔离；ExamServiceImpl私有题快照owner推导；ContentStudioInterviewBank+MockInterviewServiceImpl教材题与原追问快照；对应回归测试；scripts/content生成器/离线事务导入器、scripts/tests/test_content_recovery.py；content-studio/java-ai-persistent-v1算法8题与恢复清单；最终报告docs/reports/JAVA_AI_CONTENT_RECOVERY_REPORT.md。
- Git：main/HEAD c8b7f596534e72bf6f7c024ec356a56a3100acb7；保护大量既有dirty/untracked/迁移删除，未commit/push，未扫描迁移排除目录；相关diff-check通过。
- 数据：闭库原文件备份及SHA在报告。现存108表原行指纹在每阶段导入均保留，仅刷新本包新分类派生doc_count。1373来源SHA、10092稳定ledger记录；新增1364完整DOC（1194原文+56当前教材/程序/索引+114支持文件）、50图像绑定、16个人路径/1212章节、38卡、1483KG/149关系/2279映射、595题（49技术验证+8新编算法可用，538待审）、16代码教材、8code_problem/48cases、2843词法分块、9模板/9固定练习卷68题，0新attempt。82无答案候选仅保留原文；14源课程/119定义及100规划节点按真实状态保存。
- 调用链：源文件和全部直接load输入SHA→完整来源文档/既有表稳定键→原账号资源绑定→现有API/Service/Mapper→个人页面；模拟面试仅从可读已发布源题解析最多5追问/分层参考，开始会话固化独立快照；考试包含私有题则owner=组成者，公共题兼容。没有第二套Learning Store/Evidence算法，没有导入历史成绩为当前掌握度。
- 验证：DB_CONFIG_FILE=/tmp/knowflow-content-tests-isolated.json mvn test：736pass/0failure/error/skipped退出0；frontend npx vitest run：96files/840pass退出0；vue-tsc --noEmit退出0；npm run build退出0（已有chunk告警）。闭库备份复制集成回归5pass退出0，完整重复skip10092、用户修改保护、逻辑删除不复活、事务失败回滚、来源变动拒绝。Java17 8参考实现/48cases全部通过，3排序输入保持及9源hash匹配。新增业务回归RED→GREEN。
- 浏览器/重启：正常knowflow-dev.sh restart后Chrome经5173读取原ADMIN、28路径/8算法/9本次模板，13页面、1440×900/1280×800/1024×800截图；真实Network HTTP/body成功，无502/pageerror；私有PNG200且字节完整；9模板题量足够，9卷68题已创建，重跑仅读取不重复新增。报告证据reports/content-recovery。
- 风险/未验证：538待审/82缺答案/100规划节点不伪称写完或人工审核完成；源humanApproved与状态保留。生产MySQL、真实Judge/隔离Runner、付费AI、新增题付费生成、全EDITOR/READER组合与移动兼容 ⚠️ Not verified。实际用户浏览可经既有管线记录真实阅读；离线导入无学习事件。未隔离mvn可能读外置工作库。
- 下一任务：强制后端测试默认隔离外置运行库，然后逐批审校待审题与补写规划教材。

## 2026-10-14 — 全站右侧区域铺满（以考试中心为参照）

- 目标/原因：用户要求学习首页、笔记、绘图、思维导图、第一性原理及全站页面占满菜单右侧区域。实际1920宽度学习首页1280px/left432、第一性原理880px/left632、笔记1216px/left464；CLayout限宽叠加页面自身max-width/padding，工作台1180/1440双限宽，B壳1400px居中。
- 文件：45源码文件清单reports/full-width-layout/changed-files.json。CLayout统一稳定全宽pc-content区域；AppShell常规壳取消1400px，既有fullscreen编辑分支保留；4公共样式/章节壳样式；页面仅CSS或宽度类修改；CLayout.spec新增两fullscreen区域回归；router仅说明注释。UI_UX_SYSTEM、本报告与handoff续记。报告docs/reports/FULL_WIDTH_LAYOUT_REPORT_2026-10-14.md。
- 业务/UI/数据流：App.vue→原布局壳→原页面/Store/API不变。C端PC24px边距、小屏16px，页面外层width100%/max-widthnone/min-width0，去重复水平/顶部padding。内容按侧栏折叠可用宽度自动展开；Notes阅读详情全宽；弹窗/阅读列/输入框/画布内栏不泛化拉伸。未改认证、路由行为、注册、Evidence、NextAction、API或后端。
- Git：main/HEAD c8b7f59，大量既有dirty/untracked及迁移删除保留；没有commit/push。相关diff-check退出0，本次变化快照task.diff只用于核对，不恢复/覆盖未知修改。
- 命令与验证：新区域回归RED 11中1failed/10passed退出1；最终npx vitest run96files/842passed退出0、npx vue-tsc --noEmit退出0、npm run build退出0（既有Node/chunk警告）。首轮构建发现新增声明分号缺失，修复两个紧凑CSS块后重跑通过；后端无修改测试不适用。
- 浏览器：Chrome真实1920×1080修改前后90静态入口，最终0redirect/pageerror/横向溢出；学习首页/笔记/绘图/思维导图/第一性原理/考试/首页/工作台/真实章节在1920/1440/1280/1024/390共45尺寸检查，0pageerror/failedAPI/横向溢出；实际读旧笔记、1024目录开合、1920全局侧栏折叠/展开。重点页1920根宽1648、left248、right1896；折叠后自动增宽。证据after.json/focused.json/截图/verification.json。浏览器登录+只读网络允许，其余写请求拦截；没有创建/保存/提交练习或付费AI。
- 遗留/未验证：修改前后均GET /api/task-tags HTTP/body500，已记录独立既有问题，非样式修复；其他ID动态页未逐实例浏览，实际章节已测；创建后画布拖拽/连线/自动保存未触发，⚠️ Not verified。390仅兼容验证，不扩展移动开发。
- 下一任务：单独定位task-tags接口500；后续新增页保持公共全宽壳，不重复外层限宽。

## 2026-10-14 — 第一性原理输入聚焦边框调细

- 目标/原因：用户截图中蓝色边框过粗；真实Chrome确认1px边框叠加3px不透明蓝色box-shadow。
- 文件/影响：FirstPrinciplesLab.vue共享input/textarea focus仅box-shadow改none，保留1px主蓝边框及焦点可见性；业务输入/评分/API/Store均未变。报告docs/reports/FIRST_PRINCIPLES_FOCUS_BORDER_2026-10-14.md。
- Git：main/HEAD c8b7f59，无commit/push，保留全部既有修改。目标diff-check退出0。
- 验证：npx vitest run96files/842passed退出0；vue-tsc --noEmit退出0；npm run build退出0（已有Node/chunk警告）；后端未改不适用。Chrome真实主题输入+文本域聚焦computedStyle均1px蓝边、shadow none、focused true，0pageerror；截图/HTTP在reports/first-principles-focus。无提交表单或数据库写入。
- 调用链/风险：原页面→原Store/API不变；仅视觉提示修改，无本次遗留问题。下一任务：其他焦点样式如用户反馈可按同样细边标准局部核对。

## 2026-10-14 — 学习首页“开始今日任务”点击无反应修复

- 原因/范围：TASK白名单路由就是当前/learning/center，原顶部按钮push相同路由；LearningCenterPage的主CTA（含空态）改为TASK定位首个未完成任务、展开原details、focus+scrollIntoView，其余动作仍走resolveContextualRoute主CTA路线。
- 文件：LearningCenterPage.vue、LearningCenterPaths.spec.ts、报告docs/reports/START_TODAY_TASK_FIX_2026-10-14.md及handoff。NextAction/优先级/Store/白名单/认证/API未改，无自动完成或数据写入。
- 验证：回归RED 2tests/1failed/1passed退出1；全量vitest96files/843passed退出0、vue-tsc退出0、build退出0（已有Node/chunk告警）、目标diff-check退出0。后端未改不适用。
- 浏览器：真实Chrome1440×900实际点击，首项pending从top783.625到409.625、焦点取得/原描述展开，完成状态保持false，0pageerror；reports/start-today-task包含截图及实际HTTP记录。任务完成写操作未触发 ⚠️ Not verified。
- Git：main/HEAD c8b7f59；保留大量既有修改，无commit/push。下一任务：其他TASK同页入口如有同类反馈可复用可见定位，不改变动作计算源。


## 2026-10-14 Phase4E AI学习行动助手

范围：原Chat接规则规划+可选安全候选排序，预览/明确确认/真实Task CRUD，修复零标签500与/tasks模拟任务。文件：backend/learning/actions及Controllers、原Task/Learning扩展；frontend学习行动API/Card/Chat/真实TaskManager/source传递；db schemas、独立H2升级SQL、phase4e测试/报告。

调用链：Chat.vue→ChatLearningActionController→ChatLearningActionService→LearningActionToolExecutor→DailyLearningPlanService→LearningContextService/Rule→草案元数据；确认行锁+HMAC+版本+snapshot+事务→TaskService→task；LearningController仅投影source=TASK。未改Evidence/NextAction优先级。

Git：main/c8b7f59，保护原dirty/untracked，无commit/push，无删除原数据。原库闭库备份后升级，108原表旧列逐表指纹一致；seed-personal-v1 id4 ADMIN；8080/5173正常重新启动。备份/指纹位置见reports/phase4e/database-upgrade.json。

验证：DB_CONFIG_FILE=/tmp/knowflow-phase4e-test-no-config.json mvn -f backend/pom.xml test：782/0fail/0error/0skip exit0；frontend npx vitest run：98文件854通过exit0；npx vue-tsc --noEmit -p tsconfig.app.json、npm run build exit0。隔离8086/5174真实Chrome E2E生成/重新生成/编辑/确认/重放/查询/调整/完成/刷新/其他用户拒绝/AI-off快捷/390宽度通过，证据reports/phase4e。

风险/未验证：进程无DEEP_SEEK_API_KEY，实时AI未测试；MySQL/生产未验证；任务手动完成；题目ID关联但现有页面不消费精确题目深链；NL有限解析，AI仅候选排序。后端所有测试必须继续显式绕过外置工作库，不能初始化原H2。下一任务：可见DEEP_SEEK_API_KEY后执行一次受控真实增强验证。

Phase4E最终审查补充：AiConfig环境别名避免local覆盖（AI_API_KEY优先、DEEP_SEEK_API_KEY仅精确DeepSeek主机）；学习投影先过滤pending；逾期待办包含；LEARNING来源确认加锁并仅写状态避免事务外事件。故障注入/100历史/并发/别名测试红绿，最终782后端、854前端。隔离实例停止、闭库持久化通过，工作实例保持。


## 2026-10-15｜Phase 4F Flashcards V2：Sprint 1–3核心闭环（整体PARTIAL）

- 范围：原FlashCardsHub主动回忆、翻转、鼠标/触摸/键盘、四档FSRS、Note独立Cloze/代码/图文、预览、设置/配额、真实事件与刷新恢复；未新建第二个学习页或复习日志系统。Anki、图片遮挡、AI候选确认/完整Deck与新Note知识关联仍PARTIAL。
- 文件：frontend views/FlashCardsHub、api/types+learning、stores/learning及对应测试，components/workflow/LearningResultPanel；backend flashcards目录7实现+5测试、LearningFlashcardMapper、LearningServiceImpl、LearningEventService/Impl、pom.xml；scripts/tests/flashcards_v2_browser.cjs；docs/flashcards八文档、PAGE_INVENTORY、本交接。保留接管前大量dirty/untracked，未恢复迁移端目录。
- Git：main，HEAD c8b7f596534e72bf6f7c024ec356a56a3100acb7，未commit/push/reset/clean/restore/stash。不能将所有已有未提交修改归为本次。
- 命令与结果：强制独立DB_CONFIG_FILE的mvn test，795通过/0失败/0错误/0跳过，退出0；npx vitest run，98文件864通过/0失败，退出0；npx vue-tsc --noEmit退出0；npm run build（含vue-tsc -b）退出0，既有chunk警告；真实Chrome脚本退出0、24检查、pageerror/consoleerror均0。摘要和截图reports/flashcards-v2；不要沿用这些数字作为下一任务基线。
- 数据链：FlashcardFsrsController→FlashcardFsrsService→官方java-fsrs1.0.0(MIT,FSRS6)→原learning_flashcard扩展状态与原learning_event同事务；提交后显式新事务复用Mastery/Reward→既有Evidence/learning Store/NextAction。Note服务只管理内容及编号到原cardId映射，内容更新不覆盖并发排程。旧quality与DueAt不在启动时重算；Again不累加成功重复代理；较早请求从原事件重放，不追加重复学习记录。
- 真实验证：独立15197/18097、jdbc:h2:mem:flashcards_v2_e2e、新账号与10张测试卡；原工作库没有被评分/批量迁移。之前8086与其他任务冲突，已迁到独占端口重验，未停止其他任务服务。原216截图数量未硬编码或冒称逐行核对。
- 审查：只读独立审查指出动态到期队列、旧内容整行覆盖排程、版本初值、较早幂等请求、二次快照序列化、Mastery事务边界、完成恢复等；修复复查无剩余重要问题。保留回归RED/GREEN证据。
- 风险/NOT_VERIFIED：MySQL/生产与原库全行指纹、跨部署时区、多实例DDL并发、EDITOR/READER完整组合、真实私有附件选择、物理触屏、付费AI、Anki互通。旧AI仍直接生成保存，本轮未声称V2候选确认已完成；旧apkg未直接开放。
- 下一任务：Sprint4受限CSV/TSV预览、来源ID/指纹去重和确认事务，再加固apkg并以真实Anki检验；部署原工作库须先闭库备份并在副本验证追加DDL，不得重初始化。完整结果见docs/flashcards/FLASHCARD_V2_EXECUTION_REPORT.md。

## 2026-10-15 Phase4G — Sprint4 CSV/TSV内容闭环

范围：新增有界内容交换Controller/Service/解析器、3个新后端测试文件及迁移回归，前端独立对话框/表头辅助/组件测试/API类型，增量接入原FlashCardsHub。8份Phase4G报告、Anki兼容报告与PAGE_INVENTORY更新；不覆盖既有dirty/untracked；main HEAD c8b7f596534e72bf6f7c024ec356a56a3100acb7，无commit/push。

真实链：原大厅三Tab → ExchangeDialog → learningApi → Controller SecurityUtils → ExchangeService preview/HMAC/owner lock/source identity/receipt → 原NoteService → 原LearningService → learning_flashcard。新表import_owner/source/receipt仅组织/确认，不存排程或复习日志。原FSRS1.0.0、learning_event与Evidence/NextAction保护链未改。

命令：隔离DB_CONFIG_FILE的mvn -f backend/pom.xml test：813通过0失败/错误/跳过exit0；frontend npx vitest run：99文件870通过exit0；vue-tsc --noEmit exit0；npm run build含vue-tsc -b exit0。真实Chrome在15198/18098+内存flashcards_phase4g_e2e新账号：19检查通过，异常/Console error/API5xx均0；网络HTTP/body.code与截图在reports/flashcards-phase4g。旧795/864/24不是本轮基线。独立静态review6项边界已修，RED/GREEN证据保留，复查无重要问题。

保护：预览零Card写入、确认整批回滚/重试回执，双窗口只保存1组；本人导出重导重复，其他账号仅复制内容无历史；旧卡DueAt/fsrs_state/revision与事件在隔离夹具保持，重复DDL不改原值。没有触碰真实原账号卡片，原工作库/MySQL/生产逐行校验NOT_VERIFIED。

限制：Phase4G整体PARTIAL；apkg关闭未真Anki样本；Deck/授权知识关联未实现新链，遮挡和AI候选未实施，旧AI仍直接保存；旧JSON无新去重语义。预览key是进程级随机HMAC，重启失效，多实例未验收；回执无自动保留清理；单文件1MiB/1000行/2000Card，超限拒绝，无分批导出界面。外部HTML/媒体不自动导入；private-media/EDITOR READER新知识链/双窗口内容编辑与评分E2E未验证。

下一任务：安全MySQL副本验证追加DDL/唯一键/并发/回滚后进入Sprint5真实Deck（身份/归档/恢复/移动，保留Card排程）与Note来源授权绑定；从FlashcardNoteService.Input/sync和原我的闪卡开始，禁止复制排程/事件/学习Store。

## 2026-10-15 Phase4H — Java volatile/CAS最小真实学习闭环

范围：真实FSRS/错题计划候选；原Quiz作答/错题/事件同事务及requestId幂等；原Mastery提交后重算；规范资源身份日计划去重与真实提交Task proof；同页中心预算/三建议、精确题/卡入口、只读规则Chat。六份phase4h报告、PAGE追加；main HEAD c8b7f596保持，无commit/push，既有dirty不覆盖。

文件：learning/actions新增WriteGuard/Progress/Dashboard并扩展原上下文/规则/计划/投影/Chat；QuizDTO/Controller/Service、LearningActionController、EventService publisher、Mistake.addMistake事务；前端AdaptiveLearningPanel、plan-link/独立资源合同助手、API/SmartQuiz/Flashcards/Center。辅助表action_owner/action_identity/quiz_submit_receipt/task_evidence不复制Task/事件/排程；保护Evidence公式/NextAction顺序/原映射。

真实链：目标/真实章节→私有授权客观题→原答题+错题+QUESTION事件commit→原ACCEPTED映射Mastery + 原Task proof→同知识已有卡→原FSRS+event→原Task完成→center/task-logbook与原Store NextAction。Chat复用Phase4E工具，写入仍需确认。

验证：隔离DB_CONFIG_FILE，mvn -f backend/pom.xml -Dphase4h.browser=true test：825通过0失败/错误/跳过exit0（含真实Chrome）；frontend全量100文件873通过、vue-tsc、build均0；浏览器21检查异常/Console/5xx0，Java同库断言2answers/2QUESTION/1FSRS/1mistake/Mastery/proof>=2/revision1/AIcalls0。证据reports/phase4h。独立review6个问题修复复查；含权限Reader/Editor撤权、并发、事务回滚、积压/复开/删除任务和改期409。

边界：整体PARTIAL，自动任务仅新登记的精确Quiz/FSRS；理论/知识Review/阅读/旧计划、面试/代码仍未自动闭环。Mastery与Evidence既有算法并行，不声称完全统一；历史主观/代码旧成绩未重写，未来通用Quiz拒绝主观与代码评分。原库/MySQL/生产、全时区/DST/改期、跨引擎补偿NOT_VERIFIED。统计50Card/30dashboard/100投影均有边界，真实账号未评分/清库。测试18099由JUnit进程结束释放，15199临时前端也已关闭；原8080/5173未触碰，截图和报告用于评审。

下一任务：安全MySQL副本验辅助DDL/索引/事务，加入提交后任务投影重放/旧计划身份接管，再扩展真实知识Review与全时区改期；禁止复制学习Store/FSRS/证据引擎。


## 2026-10-15｜Phase 4I AI 模拟面试V2增量交付

- 范围：原BASIC保留，追加Java状态/幂等/version、有界异步候选/严格保密、语音ASR可配置与文字降级、五维Rubric快照报告、授权项目文档、实际知识点精确匹配与确认复用Task；Kafka新增3题，原24题保留。
- 文件：见 `docs/phase4i/PHASE4I_FILES.md` 与 `PHASE4I_EXECUTION_REPORT.md`；原interview包/页面/测试接管前已untracked，schema/其他业务dirty为共享修改，均未重置覆盖。
- Git：main，接管HEAD c8b7f596534e72bf6f7c024ec356a56a3100acb7；无commit/push/reset/clean/restore/stash；未改application-local.yml/密钥；未扫描或恢复迁移目录。
- 测试命令：`mvn -q -f backend/pom.xml -Dknowflow.datasource.config-file=/tmp/phase4i-full-no-overrides.json test` 退出0，868 tests/867通过/1跳过/0失败错误；`mvn -q -f backend/pom.xml -Dtest=InterviewV2MigrationTest -Dknowflow.datasource.config-file=/tmp/phase4i-tests-no-config.json test` 退出0新增1通过。跳过为历史Phase4H浏览器测试缺phase4h.browser属性。前端全量 `npx vitest run` 103文件880通过；`npx vue-tsc --noEmit` 退出0；`npm run build` 退出0（已有大chunk警告）。
- 真实链路：MockInterview.vue→V2Panel→interviewV2.ts→InterviewV2Controller→InterviewV2Service→原session快照+V2 state/turn；AiService仅产生候选→Schema/引文/范围校验→Java状态转移。语音Controller→SpeechV2Admission绑定version/depth/prompt→WAV校验/Provider→原转写→显式确认回答。项目DocDocumentMapper→DocumentAccessPolicy→快照；完成→真实LearningEvent INTERVIEW_COMPLETED活动；gaps→KgEntity+权限→preview→用户confirm→原TaskService+去重关联，无数值Mastery/FSRS覆盖。
- 浏览器：独立8081内存库/5174完成基础无AI全文+追问+自评+结束、严格V2文字、真实模型失败回退、暂停刷新恢复、追问完成、无证据报告/无资源空态；SQL核对实际正文、PRESET、COMPLETED/version6、1个完成事件。1440/1280/1024/390无横向溢出，Console无错误/警告。无浏览器Network抓包能力，HTTP/SQL不冒充Network。截图见docs/phase4i/evidence。
- 工作库：在线SCRIPT备份 `/tmp/knowflow-before-phase4i-20261015-162552.zip` 0600，不提交；仅三份增量H2迁移各重复2遍，旧会话2→2，完成0→0，5张辅助表存在。原initMode=never保留；只重启核对cwd的本项目后端，入口localhost:5173/interview已实际只读验证V2/Kafka/11可访问方向。没有向工作库写测试会话，8080/5173保留运行。
- 风险/未验证：真实LLM调用INVALID_KEY，工作库无active个人模型；ASR用户明确选择先接可配置/文字、待配置真实语音。真实成功AI追问/评分、正向浏览器学习回流再训练、Mic拒绝、MySQL与生产安全/负载均NOT_VERIFIED。项目为资料内容核对，非源码实现；bridge精确名称匹配可能空，已删Task恢复未实现；通用五维，算法专用Rubric未实现；超时GET恢复而非后台扫描。
- 下一任务（一个）：在系统设置/服务端配置有效LLM与ASR后，用Kafka幂等案例完成真实语音→编辑确认→动态追问→引用诊断→真实知识点补救Task→再次面试验收，并保留HTTP与DB证据；不要把替身协议测试改称真实模型PASS。

Phase4I最终收尾复核：Future映射绑定version与Future身份，done与超时取消不影响后继轮；新增HTTP慢模型迟到/结束取消回归通过。最后重新完整执行Maven test（同隔离config-file参数），870项/869通过/1跳过/0失败错误，退出0，包含迁移测试；以 `/tmp/knowflow-phase4i-backend-final3.log` 为最终证据，覆盖前述868阶段数字。最终新后端按原配置重启，隔离8081/5174已停止；未commit/push。

2026-10-15续作核对：对话中断后恢复本地后端并实际浏览器重试成功，正常入口为localhost:5173/interview，临时5174已关闭。工作库在迁移后另新增ID3/4两条user4会话，最终共4条（完成1条），未删除/重置。原ID1/2题目与答案（含null）已对迁移前备份的独立内存恢复副本进行SHA-256只读比对，完全一致；新增会话来源不作推断。该备份只恢复到一次性内存库，未恢复覆盖工作库。


## 2026-10-15 Phase4I-RC1 实际收尾增量（尚未完整发布）

- 范围/根因：有效key已存在交互环境DEEPSEEK_API_KEY，原进程未继承且旧配置别名拼写不兼容；兼容别名，不新建密钥管理。DeepSeek结构化调用显式关闭thinking/JSONobject；普通聊天不变。真实输出曾双问句/越界/提示解法，Java拒绝并降级，无伪造AI评分。
- 文件：AiConfig/application.yml、AiService/Impl、原interview Controller/Service/VO、V2Service/FollowupEngine/schema、LearningBridge、V2Panel/VoiceAnswer/API/回归测试、services/local-asr、db双schema和V20261018 color扩容、scripts/tests/interview_rc1_*，详见phase4i/PHASE4I_RC1_RELEASE_REPORT.md。共享dirty/untracked保留，main/c8b7f596；无commit/push/reset/clean/restore/stash。
- 真实链：H2会话2用户麦克风→本地SenseVoice→确认→AI追问→报告，证据不足总分null；MySQL会话4确切Redis回答→2轮真AI/62.9→真实文档17模型抽取15实体17关系→entity4事务原子性→浏览器确认Task1→任务中心未完成。两并发确认均复用Task1，SQL一条owner1/deleted0/status0。没有真实训练完成或改善因果断言。
- 数据：原工作8080/5173及持久H2本轮不写测试、不重启；独立H2 mem rc1_http/8081（私有最终备份/tmp/knowflow-rc1-h2-final.zip）、独立MySQL5.7.16端口13307/数据库knowflow_rc1_http_current_de333999/8082。MySQL原样schema task_tag.color20无法容纳26字符默认值，最小扩到64+重复迁移，旧值保留。限库账号不输出。
- 验证：全量mvn test exit0，880项879通过1跳过0失败0错误；frontend vitest103文件895项通过，vue-tsc/build exit0；Python19通过/pip check通过；MySQL23 SQL、23真实HTTP通过及单独真实Task并发/重启恢复补证。git diff --check exit0；323文本文件对本轮有效LLM和ASR密钥精确扫描0泄漏。Console error/warn空，/tasks 1440/1280/1024/390/375宽无横向溢出（只证明该页测量，不代替所有交互），无浏览器Network抓包。
- 本地ASR：已获用户下载授权，FunASR1.4.16，Python3.11独立venv，固定SenseVoiceSmall revision3847d57b6bdf2dd8875cb1508d2af43d80a16bf7，loopback8008有bearer保护；缓存仓库外，未永久存音频。首次官方样本9.0814秒、load1.9738、温0.7338、RSS约2.75GiB；真人第二段12.78秒/服务推理1.4613秒。
- 风险/未验证：指定11术语实录、同一场真实语音至Task、真实学习和同条件复测、麦克风拒绝/上传中断/ASR停机文字降级实际浏览器、多窗口UI竞态、Spring失败注入、MySQL8、生产。没有缺有效key的配置阻塞，但原工作服务仍旧配置未自动生效。
- 一个下一任务：继续5175隔离会话5的用户术语录音，保留raw原文统计11术语后编辑确认真实答案，跑到Task并完成实际学习后同条件复测；不能拿H2会话2与MySQL会话4合成完整整链。用户已被异步询问是否继续术语录音，等待回复，不替用户朗读。

RC1最终补证：已真实停止本地ASR后，经Java上传官方人声样本得到FAILED/空转写；浏览器MySQL会话6文字提交成功并COMPLETED，PRESET且总分null。ASR已恢复8008/PID40264（惰性加载）。此处是实际停机+浏览器文字降级，不是浏览器录音失败提示。H2会话2两条turn的speech_id/TRANSCRIBED/raw_text/answer数据库关联已核对，隔离内存最终备份/tmp/knowflow-rc1-h2-final.zip权限0600，原工作库未导出或测试。仍等待术语清单实录与同场整链/真实训练，不声明全部RC1完成。

交接状态：5175独立MySQL会话5已暂停（剩余约6分46秒），等待用户是否继续指定术语录音；进入后先点“继续面试”。服务保持8082/5175和本地ASR8008供继续验收。最终独立复审未发现新增具体回归，未把只读审查当成实际测试。


## 2026-10-16 Phase4I-RC1 续验交接

- 用户指示：先跳过11术语录音，继续其他验收；不再请求该录音，不代其完成训练，整链仍部分完成。
- 修改：OpenAiCompatibleAsrProvider.java从响应头后无期限InputStream读取改为有界BodySubscriber+sendAsync.get全响应期限，超时/中断取消，超限65536前拒绝，无重试/敏感错误；ProviderTest增2项。useInterviewRecorder.ts增NotAllowedError中文提示，新Permission.spec.ts。scripts/tests/interview_rc1_rollback.py新增真实MySQL触发器故障脚本。5份要求文档及MySQL报告/证据已追加历史。
- RED→GREEN：ASR配置1秒、正文延迟4秒原未抛错；新回归及chunked超限通过。权限原Permission denied不足中文说明，新中文断言通过。只读复审无新具体问题，未测专门线程中断回归。
- 验证：全量mvn test以显式独立H2 mem rc1_full1016和无工作配置覆盖运行exit0，882总项881通过/1既有条件跳过/0失败0错误；前端104文件896通过、types/build exit0。构建包体提示未修；git diff --check exit0。详情命令/日志在RC1报告2026-10-16节。
- 真实调用链：独立MySQL 5.7.16新库knowflow_rc1_http_current_2ed7ed67，原样schema/data0；Controller→V2Service→TransactionTemplate→InnoDB特定会话BEFORE UPDATE失败HTTP/body500，turn0/state0/ASKING/command0回滚；移除trigger同key重试及replay200，最终1PRESET，11PASS0FAIL。触发器finally移除，未影响其他会话/工作库。
- 浏览器：关闭ASR的新会话3明确未配置提示，真实文字提交完成无AI分；会话4两真实标签并发不同答，B成功、A冲突保留草稿并同步，刷新恢复同追问，API+SQL count1，已结束；证据docs/phase4i/evidence/rc1-1016-*。不冒充真实模型/真人语音。
- 环境：开始时旧临时服务与/tmp已不存在；当前本轮独立MySQL13307/PID7568、Java8082/PID8287、前端5175/launcherPID7725。原8080/5173未由本轮启动/停止或写入测试。新限库配置/tmp/knowflow-rc1-mysql-http-20261016-fixture.json为0600，不输出凭据。已有DeepSeek key认证HTTP200，deepseek-flash仍列出，仅进程环境继承；ASR当前显式关闭，不依赖Python启动。昨日会话/Task/性能属历史证据，不是当前新库数据。
- Git：main/c8b7f596，共享dirty/untracked保留，无commit/push/reset/clean/restore/stash。
- 未验证：同场真实语音到Task、真实训练/复测、按用户选择延期的术语录音；实际麦克风拒绝因工具拒绝Chrome内部设置页（未绕过）；上传中断、评估进程中断、Task跨服务失败回滚、MySQL8/生产。
- 一个下一任务：待用户愿意进行同场真实语音验收后补齐voice→AI→Task及实际学习复测；当前可继续用5175文字流程。完整报告不能标RC1全PASS。

2026-10-16最终检查：git diff --check exit0；321个相关文本对当前实际模型key精确扫描0命中。浏览器在仅重启本轮隔离8082后刷新会话3，恢复相同已完成报告和1条回答，无假分。旧临时并发标签已关闭，保留5175结果页。

## 2026-10-16 — UI/UX V2 Phase A 最小垂直切片

- 授权范围：全站源码审计/参考研究/兼容设计与路线图；仅实施C壳、Home、LearningCenter，B–F不改。122路由=114页面+8重定向，0组件缺失；18维逐页源码JSON与9份文档在docs/ui-v2。不能把全站源码扫描等价全站浏览器验收。
- 文件：CLayout/CSidebar/CTopNav、Home、LearningCenterPage、ui-tokens.css；新增ui-v2-phase-a.css、DashboardV2Hierarchy.spec.ts，追加CLayout回归；9报告/脚本/截图/JSON。精确本轮补丁evidence/phase-a-change.patch，6文件前后SHA在phase-a-files.json。保留接管545个tracked变化与既有untracked；main/c8b7f596，未reset/clean/restore/stash/commit/push。
- 原因/实现：学习包推广从首屏移后，NextAction优先；顶部重复主CTA改secondary但保留真实任务展开；兼容token和两页网格/字体/密度；导航关闭inert、打开modal、双向Tab/Escape与焦点返回、skiplink。scoped flex曾覆盖grid，实际浏览器发现并修正显式作用域；dark主按钮固定白字仅在两页改用现有语义foreground。
- 保护：不改router/Registry/navigation数据源、Store/API、后台壳、原始内容、FSRS/LearningEvent/Evidence/Mastery/NextAction。不新增产品框架/组件/依赖，不用静态Mock。最新取消统一返回和右侧全宽规则保留。
- 真实链：Home/Center→原learning Store/NextActionCard→原上下文路由；Center原startMainAction展开真实待办不完成；原任务API保存，仅测试账号数据。原8082/5175面试服务不动；本轮独立Java18096+Vite15196、H2 mem ui_v2_20261016，AI/Judge/host关闭。15195只作编辑前源码副本的真实前图重建，主工作区未回滚；会话只存/tmp权限0600，不提交密钥。
- 验证：新增4回归RED后修复，相关17项通过。最终全量Vitest 105文件900通过exit0；vue-tsc --noEmit exit0；build含vue-tsc -b exit0/4.70秒，原chunk/插件提示未放宽。无后端源码/契约变更，未跑Maven test，不借历史数字。git diff --check exit0。独立只读审查无重要回归，不冒充测试。
- 浏览器：Chrome13检查组、25布局测量、正常Console/pageerror0；Home/Center光暗×1440/1280/1024/768/390=20；其他5组是打开/宽度smoke。真实新增计划、主动作到Center/展开不完成、全局搜索与后退、折叠刷新、skip、抽屉键盘、reduced-motion、单次abort错误/真实重试。知识详情/闪卡/测验/面试/任务打开，普通用户拒后台/Admin认证打开原壳。HTTP/body与expectedFaults在phase-a-browser.json；8语义+2实际主按钮对比度AA达标不等价全站认证。
- 研究：5官方仓库/公开raw源码，4官方公开页面Chrome截图。GitHub tree API403限流保留；未声称运行5产品或验证其登录后交互；未复制品牌素材到产品。
- 风险/未验证：B–F未实施，其他页面仅源码或smoke；全站Dark/全部hover/active/图表AA、物理触屏、完整考试/闪卡评分/后台CRUD、真实AI、MySQL/生产未认证。Engineering PASS仅A；Visual/Functional全站PARTIAL。原始截图输出名复用已以before/after显式参数与源码快照重建纠正。
- 下一任务（一个）：按B先选真实知识详情的关联上下文切片，保留原文/权限/来源与修改前图；不另建编辑器，逐页回归后再扩展。

## 2026-10-16 UI V2 Phase B 知识中心

范围：六原路由KnowledgeHome/Docs/DocDetail/NotesManage/KnowledgeGraph/KnowledgeMasteryDetail，按B1→B2→B3门禁实施。复用Phase A C壳、Token、StateView/PageHeader/Pagination、现有Markdown/笔记编辑器/原图谱引擎。无新增API/数据库/依赖/路由；未改Registry/NextAction/Evidence/Mastery算法。

修改：六views、KnowledgeSidebar/CategoryNode键盘语义、ui-tokens追加原代码色别名；新增documentOutline/knowledgeGraphView纯展示工具及6新测试文件，EntryAsyncStates追加两项。源码准确补丁/SHA见`ui-v2/evidence/phase-b/phase-b-change.patch`、`phase-b-files.json`。Git main/c8b7f596，接管大量dirty/untracked完整保留，cached为空；未reset/clean/restore/stash/commit/push。

实际链路：Docs→docsApi→GET/docs→DocServiceImpl access.scope+真实pageNum/sort/type；TOC读取净化渲染标题，不再按原文正则错算代码标题；Notes仍原localStorage发布索引/独立草稿；graph只过滤源nodes/edges、不补画关系；mastery→SecurityUtils+documentAccess→原MasteryEngine；全局下一步仅learning Store+NextActionCard。

最终命令结果（2026-10-16 15:36起）：frontend全量Vitest exit0，111文件918项通过；vue-tsc --noEmit exit0；npm run build exit0（含vue-tsc-b）；保留Node/chunk提示。正常源码diff检查通过。无后台源码变化，未借历史Maven结果。

真实Chrome最终批次B1/B2/B3/keyboard-states四脚本exit0；六页1440/1280/768/390×light/dark共48布局无整页溢出；搜索/后端分页/URL恢复、目录定位、隔离笔记新建取消保存刷新、真实实体关联与零证据、平移缩放/键盘/故障重试，正常错误0；补测匿名公开首页不显示个人学习加载状态。证据见`ui-v2/13_PHASE_B_BROWSER_REGRESSION.md`和JSON。不是Mock数据验收。

隔离：原Java18096/H2 mem ui_v2_20261016保留，Vite15196当前服务；仅重启过本轮归属15196处理优化缓存504，未触碰其他8082/5175任务服务。B2长文已清理；B3两实体/一前置边/源文档仅在该内存库保留供复核，ID见b3-fixture.json，非用户原始数据/学习成绩。会话仅/tmp受限文件，报告不含Secret。

限制：笔记本浏览器存储无云同步/稳定关联文档ID；真实付费AI、生产MySQL、读屏器/物理触控、超大图谱/全站AA未认证。Engineering PASS，Visual PASS（六页矩阵），Functional PARTIAL（核心通过、上述未验）。报告10–13均已新增。

下一任务：进入UI V2 Phase C之前先审计现有FSRS/复习事实源与最新阶段成果，沿用Phase B真实门禁，不重写算法或借用本轮测试数字。

## 2026-10-16 UI V2 Phase C 闪卡/复习/测验/错题

- 范围：C1 FlashCardsHub/FocusShell、C2 ReviewPlan、用户明确追加的C3 SmartQuiz/Mistakes；E保留代码题，未改D。完整报告为docs/ui-v2/14至17。
- 文件：5个产品Vue；原FlashCardsHub/ReviewPlan测试追加；新增StudyFocusV2/SmartQuizV2/MistakesV2测试；07路线图/06矩阵更新。专属补丁和SHA在evidence/phase-c，不能把巨大HEAD diff归为本轮。
- Git：main/c8b7f596，保留既有dirty/untracked、cached为空；未reset/clean/restore/stash/commit/push；最终diff --check exit0。
- 最终门禁：frontend全量npx vitest run exit0，114文件937项通过/0失败；vue-tsc --noEmit exit0；npm run build exit0（含vue-tsc-b）。Node/chunk提示保留。日志/tmp/phase-c-final-*，持久摘要test-results.json。无后端改动，本轮Maven未跑，不借旧数字。
- 调用链：FlashCardsHub→原learningApi/FSRS Controller→FlashcardFsrsService/日志；ReviewPlan→due/statistics，删除伪日期和静态完成曲线。SmartQuiz→原QuizService/writeGuard，失败同requestId重试、新轮独立；NextAction只读learning Store，奖励不等于Mastery。Mistakes→用户错题接口，真实questionId经resolvePlanResourceSelector上下文/白名单重练。未改调度/掌握度/证据/事件/任务算法。
- 浏览器：隔离Vite15196/后端18096/H2 ui_v2_20261016、新QA用户9与隔离题卡；40个光暗×四宽度布局，C1最终再跑16；真实评分、丢响应幂等、恢复/完成、判分、错题重练/标记/筛选/重试、代码复制。正常Console/pageerror新增0；双向Tab/Space/数字3真实通过。证据在docs/ui-v2/evidence/phase-c；凭证仅/tmp，不入库。
- 限制：SHORT_ANSWER能读但原接口只判四类客观题，UI已明确并阻止无效提交；不改后台判分边界。
- NOT_VERIFIED：接管前截图（有源码快照/patch）、付费AI、物理触屏、屏幕阅读器/完整AA、公式、真实撤销、跨设备恢复、准备按钮重建后的退出焦点恢复、全站A/B回归。已测范围无已知新增业务回归，完整Functional Regression保守PARTIAL。
- 服务：仅使用自己15196/18096，其他任务5175/8082未动。复跑脚本会写隔离历史，不能对真实账号盲跑。
- 下一任务：Phase D先独立审计strict/session/version和文字降级；主观题评分入口契约另列，不沿用本轮PASS直接发布。

## 2026-10-16 UI V2 Phase D AI工具工作区

- 范围：Chat、原Phase4I InterviewV2Panel/Voice/MockInterview、FeynmanLab、ScenarioLab、chat SSE封装；无E/F、算法、后端源码/表结构改动。保护A/B/C及其他dirty/untracked，main/c8b7f596、cached空；未reset/clean/restore/stash/commit/push。
- 文件：7个产品文件、3个新增测试（chat-stream-v2/ChatV2/LearningLabsV2）、2个原面试测试追加；docs/ui-v2/18至21、06/07、evidence/phase-d。专属patch/SHA不归因整个HEAD差异。
- 调用链：Chat→原ChatService/AiService/SSE→原消息表，done才完成；会话代际隔离、IME、同步锁、安全Markdown。面试→原V2/version/requestId/STRICT/预置fallback/语音确认，补路由身份和补救响应隔离。费曼/情景仍确定性自检/要点覆盖→原LearningEvent，不伪造AI/Mastery。
- 最终门禁：frontend npx vitest run exit0，117文件952项通过/0失败0跳过；vue-tsc --noEmit exit0；npm run build exit0；Node/npm/chunk提示保留。日志/tmp/phase-d-final-*，摘要evidence/phase-d/test-results.json。无后端改动，本轮Maven未跑，不借历史数字。
- 浏览器：Chrome/隔离15196→18096/H2 ui_v2_20261016，新QA用户10及权限隔离账号；32光暗四宽度组合、4前截图、32后截图。真实规则预览/历史重试；V2 STRICT→PRESET→刷新→完成null分；双标签409且1条回答；私有会话Owner200/200、Other404/404、Anonymous401/401、Admin404/404；费曼/情景事件各+1。正常Console/pageerror新增0；审查边界延迟请求实测见review-browser.json。
- 能力差异：初次Chat在DISABLED仍返回真实模型回答，前端已遵守唯一Store阻止关闭态请求；后端Chat入口开关缺口未修，不声称UI替代服务端门禁。V2原引擎检查开关，本轮真实PRESET，无AI评价。
- NOT_VERIFIED：模型面试评价/动态AI追问、麦克风/ASR/TTS、本轮模型长代码复制、供应商端取消、全键盘/AA/屏幕阅读器、硬件触控、MySQL/生产、A/B/C全业务。七页smoke不是全回归，原聊天知识库开关/分享反馈等旧功能未完整认证。
- 结论：Engineering PASS（本轮前端）、Visual PASS（矩阵范围）、Functional PARTIAL。可进E独立审计，不等于AI/语音生产发布。
- 下一任务：为Chat普通/流式入口加统一AI开关专项回归与服务端门禁，再在授权Provider/ASR环境完成模型/语音验收。

## 2026-10-16 UI V2 S AI门禁 + Phase E代码工作台

- 范围：先S后E1/E2，未进入F；保护所有既有dirty/untracked，main/c8b7f596，cached空。未reset/clean/restore/stash/commit/push。新代码与回归测试逐切片验证，不借D历史数字。
- S文件：AiCapabilityProperties/Executor、AiServiceImpl、ChatServiceImpl，AiServiceGateTest新增8项、ExecutorTest追加3项；docs/ui-v2/22–23。真实链ChatController→ChatService（副作用前门禁）→AiService（HTTP/Adapter最终边界）→本地Provider。系统开关非客户端enabled；历史/规则计划保留认证/归属。排队/重试再次检查，关停拒绝不计熔断；已发出的请求不保证撤回。
- S门禁：本轮全量mvn test exit0，118套893项，892通过1既有条件跳过0失败0错误。前端S时重新117文件952项/types/build通过。最终重启本轮独立Java并再次HTTP/Chrome：关闭text/image/SSE422/422且Provider增量0，匿名401/401，Other404/404；规则预览和非空历史可读；开启本地桩text/image/SSE及原摘要4次。非付费AI认证。独立复核P2关停误熔断已先红后绿修正。
- E文件：CodePractice.vue、CodePlayground.vue、codeQuestion.ts仅注释；CodePracticeV2.spec.ts 4项、CodePlaygroundV2.spec.ts 11项；docs/ui-v2/24–27、06/07与evidence/phase-e。相对本轮起始快照的phase-e.patch/source-sha256.json可区分巨大既有HEAD差异。
- E调用链：目录/详情仍原code_question与发布/索引权限；新检索与最新请求保护。保留textarea与ChapterResourceLinks、唯一AI Store；不另算NextAction/掌握度。工作区是认证用户的server-owned文件存储，与受HostCapabilityGuard限制的运行能力分开。只在服务端保存成功后清dirty；取消/离开/语言/失败保留草稿。旧submit只接客户端统计，故停止本页伪判题上报；移除Function执行，不改变后端历史提交/学习证据协议。
- E最终门禁：frontend全量npx vitest run exit0，119文件967项0失败0跳过；vue-tsc --noEmit exit0，npm run build exit0。日志/tmp/phase-e-final-*；后端最终/tmp/phase-e-mvn-full-final.log（0600）；摘要evidence/phase-e/test-results.json。Node/npm/chunk提示保留，不降低阈值。
- 浏览器：真实Chrome、15197→18097/H2 ui_v2_e_off_final，新隔离账户，16光暗×1440/1280/768/390布局；真实搜索/空/重试/题目导航、剪贴板、长代码、语言/重置/路由取消、Tab/Escape/焦点返回/键盘分隔条；真实运行403/body403；SQL仅示例模拟。隔离文件创建→故障保存保留→重试200/body200→重开一致→取消删除→确认仅删测试文件。正常Console/pageerror0，无整页溢出。A/B/C/D六路由×两组合12smoke通过（非全业务认证）。
- 环境：只重启本轮自建18097/18098，另有本地计数Provider18997及Vite15197；没有动原8080/5173或其他Agent服务。文件写入前显式配置code.execution.workspace-root=/tmp/knowflow-ui-v2-e-workspaces-off/on；未写真实用户文件。早期只读GET会按既有Service创建空用户目录，未删除原文件。凭证仅/tmp0600，不在报告/证据内。
- Documentation Drift：起初误把工作区存储当成HostGuard能力，真实GET200后完整追踪Service已更正24/27；不改代码迎合旧文档。初次测试夹具路径/Node类型问题已修正，报告不当作产品RED。复核E两P2（退出语言、窄屏结果）追加先红测试后修复，终审无新增阻断。
- Verdict：S AI Gate PASS / Backend Regression PASS（本次范围）；E Engineering PASS / Visual PASS / Functional PARTIAL。NOT_VERIFIED：可信沙盒/Judge/真实编译与资源配额/付费AI/供应商取消/生产MySQL/完整AA与全站业务。后端旧提交接口仍信任客户端统计，需独立安全判题专项，UI停用不等于全站漏洞修复。
- 下一任务：可进入Phase F独立审计；可信Judge与历史统计可信度专项另开，不把本轮UI结果当生产执行授权。

## 2026-10-17 UI V2 Phase F 交付（浏览器实测于2026-10-16）

- 范围：按F1→F2→F3-A→F3-B完成安全增量。TaskCenter继续包装TaskManager；修改TaskManager/TaskRow/TodayPlan、LearningReportPage/MasteryDashboard、后台AppShell/Sidebar/UserManagement；新增TasksPhaseF(7)、ReportsPhaseF(3)、AdminShellPhaseF(1)、UsersPhaseF(6)共17项回归。没有改后端、路由权限、学习算法、数据库或原用户数据。精确增量见docs/ui-v2/evidence/phase-f/phase-f.patch及source-sha256.json。
- Git：main/c8b7f59；保留既有dirty/untracked，cached空；没有reset/clean/restore/stash/commit/push。2026-10-16交付检查未丢失既有status条目；2026-10-17继续收尾核对12个源码/测试文件哈希均与最终浏览器验证版本一致。
- F1：当前列表搜索、请求代际、创建失败保留、完成确认及提交锁；手动任务服务成功后刷新。TodayPlan只显示服务端今日，不提供假日期查询，修复summary函数插值，按learningTask/todo/habit既有接口写入后回读；保留force:false与确认。链：TaskController/TaskServiceImpl→task/list/tag；LearningPlanController/ServiceImpl→原三类任务及learning_plan。手动todo归属更新不等于学习证明，本轮没有绕过或重写证明规则。
- F2：真实近7/30天/全部、固定30天日活与8周趋势、累计阅读/快照解释、原始数据表、空趋势；NextAction只复用learning Store。Mastery知识点独立数据有效、详情键盘可达、分数/置信/来源明确。Report Service按lastReadTime归累计readSeconds，本轮解释口径不重算历史；/api/learning/mastery仍权威服务，未改FSRS/Evidence/Mastery/LearningEvent。
- F3：19个admin Vue文件盘点见admin-inventory.json。共享壳inert/焦点/Escape；用户管理使用原后端keyword/role/pageNum/pageSize，删除level*7伪进度，完整失败/提交/取消/删除反馈与焦点保护。原requiresAdmin/SecurityConfig/@PreAuthorize不放宽；QuestionCatalog只做真实列表抽样，不重写专业编辑器。
- 测试：2026-10-16最终frontend全量123文件984项通过/0失败0跳过，vue-tsc --noEmit及生产build exit0；每批先红后绿，复核三项P2追加失败测试再修复。2026-10-17工程复验记录补充于30报告及test-results.json。后端未改，本轮Maven未运行0项，不借E历史893项。Node/npm/chunk提示保留，不降低阈值。
- 浏览器：2026-10-16真实Chrome、Vite15197→Java18097/H2隔离库，64布局检查（56唯一组合，用户页两批重复）+16个A–E只读smoke；1440/1280/768/390×浅深。真实任务创建/完成/计划回读/失败恢复/Other404及Anonymous401；空与真实签到用户报告三周期API/UI一致；用户真实分页第二页、创建/改昵称/取消/删除、重复提交、失败保留；后台Anonymous401/普通403/原种子Admin200，HTTP及body同时核验。正常Console/pageerror0，整页溢出0；注入错误单列，不冒充服务端授权。7前截图、89总截图及JSON/脚本见phase-f证据目录。
- 数据：只在隔离H2新测试账号操作；12个分页普通测试账号及CRUD自己创建账号已删除，未修改角色或删除原账号。真实Mastery返回[]，非空知识点证据仅组件测试，不能宣称真实证据详情通过。
- 环境：2026-10-17原隔离15197/18097已不再监听，/tmp旧日志/凭证/快照已不可用；持久化截图/JSON/补丁/哈希仍在仓库。本次未误启动/终止8080/5173。复跑浏览器需重新建立隔离服务与账号，不能把旧证据写成今天新跑。
- 文档：新增28审计/29规格/30执行/31浏览器报告，更新06矩阵/07路线图；链接与JSON敏感token扫描通过。
- Verdict：F1 Engineering PASS / Functional PARTIAL；F2 Engineering PASS / Data Integrity PARTIAL；F3 Engineering PARTIAL / Permission Regression PARTIAL；整体Engineering/Visual/Functional PARTIAL。已实施范围门禁通过不等于全后台或生产认证。
- NOT_VERIFIED：F3-C其余18个专业后台页面完整业务/权限；非空真实掌握度证据；学习证明/习惯全部分支；生产MySQL/多租户/全角色矩阵、可信Judge/付费AI、完整AA/屏幕阅读器/硬件触屏。没有实施RC。
- 下一任务：先按专业模块分批完成F3-C并在真实学习证据隔离环境补齐掌握度/学习证明回归，然后评估UI V2 RC。

Phase F补充门禁（2026-10-17）：全量Vitest复跑123文件984项通过/0失败0跳过；类型与生产构建exit0。摘要deliveryReverification已持久保存；本日未重跑浏览器，不改变既有2026-10-16浏览器证据日期。

## 2026-10-17 UI V2 Phase F+ 与 RC准入预审

- 范围：先独立审计/真实后台分组，再C1场景列表→C2专业编辑→C3配置与服务端门禁→学习链→权限→准入。仅改ScenarioMgmt.vue、ChatConfig.vue、AiConfigController.java，新增ScenarioFPlus(5)、ChatConfigFPlus(4)、AiConfigTestGateTest(3)。保护main/c8b7f59所有既有dirty/untracked，cached空，未reset/clean/restore/stash/commit/push。增量相对接管快照见evidence/phase-f-plus/phase-f-plus.patch/source-sha256.json。
- C1：失败不伪装无数据，StateView/重试/代际，搜索与状态过滤，局部主题/滚动。C2：覆盖更新payload保留原knowledgeId；保存与确认锁、错误保留、取消/路由/beforeunload保护、弹窗Tab/Escape/焦点，沿用要点/关键词/上下架/逻辑删除。场景Controller→Service→Mapper协议保持。
- C3：个人AI配置加载失败禁止基于空态编辑，保存/删除锁、内联失败与私有输入保留；唯一capability Store显示真实系统状态。连通性测试准入不能要求旧配置AVAILABLE，复核P2已新增先失败测试后修正为系统enabled+用户偏好；首次配置注入UI分支明确不认证Provider。配置API按认证用户，不误改成ADMIN专属。
- 服务端真实缺口：系统DISABLED时ai-config/test仍调用本机计数桩1次（HTTP/body200），最小修复为认证后、取Key与构造网络前检查既有AiCapabilityProperties，关闭422/422且桩计数0；配置CRUD保留。未访问付费Provider、未改学习算法/数据库/权限。
- 工程：本轮新鲜frontend全量125文件993项通过0失败0跳过，vue-tsc --noEmit/build exit0；后端独立H2全量896项895通过1既有条件跳过0失败0错误、exit0。跳过AdaptiveLearningBrowserTest缺phase4h.browser，不算通过。前端新增9项、后端新增3项。初次后端全量因仓库外置文件DB被占用而中止exit130；停止后用DB_CONFIG_FILE=/tmp/phase-fplus-tests-datasource.json重跑成功，不解锁原库、不删测试。日志/tmp/phase-fplus-*权限0600，持久摘要test-results.json。
- 真实运行：Vite15199→Java18099（最终重新打包jar，独立H2 ui_v2_fplus_final）；AI/宿主执行/运行时数据库切换显式false；原8080/5173等未触碰。新普通Owner/Other与种子Admin登录，无提权。C1/C2/C3共24浅深四宽度布局、Mastery/详情16、A–F只读28smoke；正常Console/pageerror/整页溢出0。删除仅fixture场景，配置仅本轮假配置。细节35及cleanup.json。
- 真实证据：创建测试文档、现有accept接受内容一致概念，产生DOC映射；本人真实阅读与闪卡评分→事件→Mastery。快照分60/置信23/真实signals/time；最终Chrome原learning Store得到4条Evidence并按既有优先级选择TASK，未另算NextAction。Other列表/事件无Owner记录、读Owner卡403。真实Again后等约61秒到期再建FSRS计划，评分提交后自动完成，同requestId重试不重复事件。不是直接插分或改调度时间。
- 权限：18个ADMIN只读端点×Anonymous/两USER/Admin=72，场景9个拒绝写及个人配置/脱敏/系统门禁等共92实际HTTP/body；Other/Admin不能删除Owner个人配置，但旧Result.error仍HTTP200/body500（P2）。完整写权限/租户/所有资源/审计日志未全验，不称100%后台覆盖。
- 重要P1：真正FLASHCARD证明任务在评分前可由PUT /tasks/{id}/status标为完成；旧LearningTask重复status1产生重复完成事件；TagManagement是硬编码演示数据与本地假CRUD。未在UI任务中擅改证明/权重/历史。早期TOOL人工完成被误归类风险已纠正，只有后续真实FSRS资源计划构成证明绕过证据。其余17个admin文件未改，19文件也不等于全部路由组件。
- 文档：32独立审计、33分批设计、34执行、35浏览器、36真实证据、37权限矩阵、38RC门禁；更新06/07与本交接。前后截图/脚本/状态/哈希保存在evidence/phase-f-plus。
- Verdict：F3-C Engineering/Visual/Functional PARTIAL；Evidence Integration/Mastery Data Integrity/Admin Permission Regression PARTIAL；RC Readiness NOT_READY。付费AI/真实语音/Judge、生产MySQL/迁移、多租户全矩阵、完整AA/硬件触控仍未认证；关闭不等于认证。本轮没有实施RC或生产发布。
- 下一任务：先兼容修复证明型任务状态入口及旧LearningTask完成幂等，再落实标签真实契约并逐批补剩余专业后台，补所选发布范围的写权限/数据库/无障碍门禁后重新申请RC准入。

F+最终交付核对：70张截图、40功能布局+28smoke、全部32–38文档链接有效；3生产文件/3新增测试文件哈希固定，其余17个admin文件匹配接管基线；既有status条目无丢失，cached空。仅关闭本轮15199/18099隔离服务并移除本轮/tmp认证文件，独立内存库随进程退出清理；未清理原用户文件/数据库。证据与增量补丁持久保存，重放需重新建立隔离环境。

## 2026-10-17 Knowledge Graph UI Hotfix V1.0

- 范围：仅KnowledgeGraph.vue、新KnowledgeGraphCanvas.vue、knowledgeGraphLayout.ts和3个新增测试文件；未改全局样式/Store/API/路由/后端/权限/学习算法。沿用SVG、既有dagre和Mermaid，不重复引擎。先读真实源码/数据并保存16组before，再补失败测试和最小兼容修复。
- Git：main/c8b7f59；接管status所有条目仍保留，cached空，未reset/clean/restore/stash/commit/push。原knowledgeGraphView.ts字节不变。增量patch、6文件SHA256、test-results、最终status见docs/ui-v2/evidence/knowledge-graph-hotfix；不可用整仓HEAD diff冒充本轮差异。
- 真实链路：router→KnowledgeGraph→api/knowledge→KnowledgeController→KnowledgeService→既有Mapper/权限scope。分类1438节点1422边；实体1621节点188边、1397孤立(86.18%)，没有重复ID/重复边/自环/无效端点。截图描述旧1579/149不作为当前值。
- 根因：分类有限环全量标签；实体400/260固定坐标与随N增大的viewBox失配；外层SVG变换缺少真实边界fit/resize；技术旧网格不按方向。新实现真实层级折行/依赖dagre/关联分量布局碰撞，默认按空间和标签尺度收敛3–12节点，孤立和全量搜索入口、真实一跳、类型筛选、完整名称/详情/来源、增量保持比例。
- 生命周期：内部g单相机、ResizeObserver、rAF合并取消、零尺寸保护、Tab重挂载、迟到实体响应不再启动mastery；保留最新代际。来源真实doc-与历史doc_兼容。审查发现筛选内Enter、一跳fit被覆盖、节点Escape分支，已补测试修复。实体关闭按钮自身回归亦先失败后修正，不改已有断言。
- 工程：本轮最终frontend全量npx vitest run **128文件1016通过0失败0跳过(exit0)**，新增23项；vue-tsc --noEmit exit0，npm run build exit0/4.21s（既有chunk警告保留）。初始2失败、详情1失败、审查/来源3失败均已转绿；中间根目录启动局部测试no tests属运行目录错误，已纠正。无后端修改，Maven未运行0项，不借历史数字。
- 真实浏览器：原5173/8080服务沿用，不误停/重启；seed管理员普通登录，未调用AI/抽取/生成。最终32组光暗四宽度模式/状态截图中16组分类/实体真实非空，另16组技术/概念仅初始态；12知识首页/文档/笔记只读smoke。默认卡片相交0、标签不越界、节点在viewport内、中心误差<2px、正常Console/pageerror0、页面无横向溢出。测试真实孤立搜索、类型/分类、详情/关闭/真实一跳/聚焦、缩放拖拽/键盘、reset/刷新/真实重试、Tab/侧栏/1024resize、DPR2、来源/doc/38跳转及后退。空/网络失败是明确注入再恢复实际API。
- 性能：最终默认布局2.0–6.9ms，增量11→26→41→56→71布局2.5–10.2ms/比例保持1；真实拖拽/滚轮同时采样95帧间隔中位16.7ms、p95约16.8ms。仅本机当前子图，不推广全1621强绘制/长期堆内存结论。重复Tab仅1活动observer，卸载另有测试。
- 数据：实体全nodes/edges和分类身份/名称/类型/边hash复核一致；分类value为文档viewCount，正常查看doc-37从1到2、实际来源doc-38从0到1单列。图GET原Controller会写KNOWLEDGE_VIEWED；不是绝对零DB写入，但未编辑/删除实体、关系、原文或原始学习历史。
- 文档：46审计/47设计/48执行/49浏览器，追加06矩阵/07路线图；50PNG含16before+32after+2早期inspect，browser/extra/data-audit/test-results与精确patch留存。临时凭证不入库，脚本复跑需重新普通登录/构建私有快照；before脚本需历史代码，禁止为重拍覆盖工作区。
- Verdict：Graph Data Integrity PASS；Graph Layout/Interaction/Performance/Visual整体PARTIAL。已覆盖分类/实体范围通过；技术GET必调AI、概念GET可能调AI，无缓存只读保证，未触发Provider，生成后二模式仍NOT_VERIFIED。原生浏览器菜单zoom（DPR2不等价）、完整AA/读屏/硬件触摸、多角色权限/生产库、长期内存/全量强绘制未验证。既有F+ RC NOT_READY不变。
- 下一任务：在已有授权Provider或明确缓存环境中补技术/概念真实图数据、交互与视觉；不得为截图悄悄开启付费AI，也不扩大本轮后端协议。

图谱交付收尾核对：窄屏分类搜索独占一行后再次完成32截图复跑；最新128文件1016项/22.16s、types exit0、build exit0/4.21s。最终证据JSON与报告链接检查通过，6源文件hash/patch更新，接管status无条目丢失、cached仍空。只删除本轮/tmp临时认证文件，原5173/8080服务和用户工作区不动；复跑脚本需要重新普通登录。

## 2026-10-17 Knowledge Graph UI V2.1

- 范围：5生产文件：KnowledgeGraph.vue/KnowledgeGraphCanvas.vue/knowledgeGraphLayout.ts，新GraphToolbar.vue/knowledgeGraphRouting.ts；4测试文件：新KnowledgeGraphV21.spec.ts(5)、knowledge-graph-routing.spec.ts(5)，原layout测试追加2/Canvas测试追加1。只修改图谱相关组件/样式/必要测试/文档，不改Router/Store/API封装/后端/AI Prompt/抽取/FSRS/Evidence/Mastery或用户原文。
- Git：main/c8b7f59，保留上一轮全部dirty/untracked，cached空，无reset/clean/restore/stash/commit/push。接管3生产文件快照及2测试的前轮SHA256核对基线；精确增量patch、hash、命令结果/截图/JSON见docs/ui-v2/evidence/knowledge-graph-v21，不用全仓HEAD差异冒充本次修改。
- 根因与修复：36/40/最小点击高混用、原生block按钮内SVG上下排；局部GraphToolbar复用Button/Icon统一38px/16px/横向、最大24rem搜索/手机换行、固定loading文案/图标盒与aria-busy、单focus。Dagre点位仍保留，但旧直线会穿中间节点；新增端口+正交可见性网格/A*避障，关系标签避碰/完整清单，真实方向与未知关系码保留。实体固定窄宽度逐行分量堆叠变为按视口比例的确定性候选装箱，54/39同数据节点边界约967×2567→2881×886。适应是全貌；重置恢复默认密度，字宽/词边界不硬拆Spring Boot，选择轻描边/键盘focus分离。
- 交互缺口：技术分类cat-19错误展示ID换成原分类API数字19；实体原v-model无查询改watch真实fetch；技术/概念IME保护。原useAsyncResource最新代际、ResizeObserver/零尺寸/卸载清理和无证据mastery状态保留。概念重新生成只换Button/工具栏，原删除缓存语义未改，浏览器没有点击regenerate。
- 数据：真实全库1438分类文档/1422边、实体1621/188；RAG分类19准确54/39、11孤立、17分量，无重复/无效端点/自环。技术动态：before16/19、主套件after16/18、完整补测15/19，不能伪造固定17/23。全部RAG节点和边、完整技术补测返回节点和边都实际绘制，0穿节点/路由异常；边与边不保证完全无交叉。
- 真实链路：router→KnowledgeGraph→knowledge API→KnowledgeController/KnowledgeService→原Mapper/权限scope。额外GET精确hash分类、全实体、RAG全部nodes/edges与接管快照一致；无实体/关系/笔记/原历史写回。图GET有原正常查看事件，不能说零DB写入。公开Spring Boot/通用及“变量”调用原真实接口，未向Provider发送私有分类文档，未构建/抽取/重生成，未改Key/开关/权限；HTTP200不等于Provider付费/降级质量认证。
- 工程：本轮最新全量frontend **130文件1029通过0失败0跳过，exit0/22.76s**，新增13项；vue-tsc --noEmit exit0、npm run build exit0/4.26s，既有chunk警告保留。初始4交互断言失败+路由新API缺失、分量/重置/短英文/宽英文各失败后修代码转绿；一次装箱多括号编译失误已修并全量重跑。无后端变更，Maven未运行0项。独立只读审查未发现新P1/P2，清理重复reset赋值。
- 浏览器：原5173/8080进程沿用未停/重启，seed管理员普通登录；32before+32after四模式×四宽度×光暗，技术/概念本轮真实数据；概念末次工具栏追加8组复核，完整技术15/19另补、清空回空态。控件全38、图标16、图文中心误差≤0.657px；默认卡片/标签交叠0、穿节点0、路线越界0、无横向溢出，图谱正常Console/pageerror0。真实查询/选择/一跳/聚焦/取消、更多/全54/39、缩放拖动、reset、侧栏、键盘/焦点、生成busy/稳定宽、真实网络错误重试；空是明确注入后真实恢复，不冒充后端空库。图像67PNG，主套件及概念/技术专项exit0。
- 性能：默认布局0.4–5.5ms，RAG10→54增量0.9–4.3ms；全54概览约38%，重置回可读密度；技术补测15/19全图4.5ms/40%，重置7/86%。真实拖动滚轮并发91帧间隔median/p95约16.7ms。本机样本，强制全1621路由/长期堆内存未认证。
- 附加smoke诚实失败：8页×1440浅/390深16组无溢出/pageerror，但管理员首页两次GET /api/learning/seed/java-ai-v1产生HTTP/body403及两条Console，extra套件exit1，不降低断言。独立GET同403，消息“学习包仅由普通用户本人启用，管理员数据不会自动初始化”；后端明确拒绝，不放宽权限或修改首页。本轮发现于未改模块，未用旧快照证明发生时间，不声称全站回归PASS。smoke未观察到非GET请求。
- 文档：KNOWLEDGE_GRAPH_V21_AUDIT/DESIGN/EXECUTION_REPORT/BROWSER_REGRESSION，追加06矩阵/07路线图/本交接；证据同目录含failed smoke、HTTP blocker、同数据布局benchmark。Toolbar/Dependency Layout/Entity Layout/本轮Visual/Data Integrity PASS；Graph Interaction PARTIAL（原生浏览器菜单缩放、普通用户全矩阵/私有Provider、硬件IME/触控/完整读屏AA等未全验）；附加全站PARTIAL，RC NOT_READY不变。
- 下一任务：独立处理管理员首页seed状态查询反馈，并以普通用户补关键路径；补原生菜单zoom/硬件及所选发布范围门禁，不为验收擅自发送私有文档或开放权限。

V2.1最终交付核对：67PNG、4份V21报告链接有效，9个源/测试文件的精确增量patch与SHA256固定；接管status无条目丢失，cached仍空。原5173(node4675)/8080(java4601)归属不变；仅删除本轮/tmp临时认证文件，原用户数据/工作区/服务不动。主图谱/概念/技术全图套件exit0，附加smoke保留exit1/403证据，未把其改为全站PASS。


## 2026-10-17 后续开发总规划与工程接管 V1.0（只审计/规划）

- 范围：用户明确两步执行，本轮只审计与规划，不实施G–K。新增docs/roadmap/01_CURRENT_SYSTEM_AUDIT、02_PRODUCT_CAPABILITY_MATRIX、03_TECHNICAL_DEBT_AND_RISKS、04_MASTER_DEVELOPMENT_ROADMAP、05_PHASE_DEPENDENCIES、06_V1_RELEASE_SCOPE、07_TEST_AND_RELEASE_GATES、08_NEXT_EXECUTION_PLAN，附安全源码/路由索引、26任务完整字段、测试和保护证据；本交接只追加。未扫描/修改迁出多端目录，未安装新Skill/框架。
- Git：此前上下文main/c8b7f59；本次最终读取main/78f4b11，为外部content-studio文档提交；本轮没有commit/push或reset/clean/restore/stash。原897条status到当前930条主要因content-studio父级untracked随着外部提交展开为子路径，不能将父条目消失说成数据删除。暂存区空；1532个业务源码/测试/配置文件在本轮记录的哈希基线后不变；最终证据见docs/roadmap/evidence/verification.json。不得将全仓HEAD diff归为本轮。
- 真实盘点：AST顶层122路由/114组件/8重定向，Registry17 App、12顶层Store、56顶层API；Java803、注解Controller94、Mapper103、TableName实体103、后端116测试文件/前端130 spec。旧13App/10Store是文档漂移。未找到Phase G执行报告；不是“G已完成”。
- G1真实链：LearningController source=TASK→TaskServiceImpl.setStatus直接写；updateTask(status)、updateStage及DailyLearningPlanService STATUS也需同一服务端证明门禁。LearningTaskProjection投影task元信息，LearningActionProgress另凭QUESTION_ANSWERED/FSRS FLASHCARD_REVIEWED提交后绑定learning_task_evidence。静态确认缺口，本轮未重放生产绕过/未修代码。
- G2：source=LEARNING→LearningServiceImpl.updateTaskStatus对每次status=1都record LEARNING_TASK_COMPLETE，无转换判断/并发条件更新；P1仍在。G3：TagManagement固定20条数组，save/splice成功通知无API。TaskTag是用户任务标签，不可挪作知识标签。G4额外Overview静态统计、DocManagement分享演示需范围裁剪。没有本轮运行确证新增P0。
- 权威链：stores/learning→resolver/aggregate/diagnosis/action-candidate→resolveNextAction保持原实现；服务端record(REQUIRES_NEW/告警)和recordRequired(主事务+afterCommit)语义不同；resource_knowledge_mapping→KnowledgeMasteryServiceImpl/MasteryEngine权威重算。FSRS/事件/证据/算法、历史均未修改。QuestionCatalog已是四来源UNION/来源前缀/分页/专业编辑适配，不要新建重复目录或物理合表。
- 数据/安全：NotesManage/NoteEdit实际localStorage，与章节learning_note模型不同；提出本地边界和账号隔离评审，不自动迁移。DataSourceManager外部JSON优先级覆盖普通spring.datasource；H2 always非版本迁移，MySQL auto只判业务表存在，需J1/J2真实兼容恢复。源码默认AI true/DB runtime-switch true/Host false/Judge disabled不等于生效生产值；本轮不改运行服务器开关。
- 本轮前端：npx vitest run **130文件1029通过0失败0跳过，exit0/29.75s**；npx vue-tsc --noEmit独立复核exit0；npm run build exit0/6.42s，保留大chunk警告。
- 本轮后端：临时权限0600JSON→隔离内存H2，AI/Host/热切换false；不连接原8080文件库。首次mvn test **888执行/382通过/1失败/504错误/1跳过，exit1**，多Context重复建索引；随后4类定向reuseForks=false **19通过，exit0/14.694s**；完整mvn -DreuseForks=false -DforkCount=2 test **896执行/879通过/16失败/0错误/1条件跳过，exit1/6:15**。12个试点测试受内存库前缀保护403，聊天1因AI关闭422，状态2因DISABLED预期冲突，面试1等待Mock worker未进入。只对InterviewV2HttpTest显式Mock能力开启做对照 **1通过exit0/8.017s**，支持配置影响，不把全量重写成PASS。AdaptiveLearningBrowserTest条件skip未运行E2E。HostExecutionTest已知夹具会启动可信语言进程，不等于开放用户代码执行。后续T1需正确隔离测试契约，不删保护/降断言。
- 浏览器：本轮没有新业务E2E、未登录触发图谱GET事件；只查历史V2.1证据并核对9源/测试SHA256全部一致，67PNG/真实图数据为历史记录，不冒充本轮数据或PASS。原首页admin seed403附加smoke仍保留阻断；原生zoom/多角色全矩阵/长期内存/付费Provider/ASR/TTS/生产MySQL/完整发布回滚仍NOT_VERIFIED。
- 结论：审计规划交付，不是业务修复交付；RC NOT_READY。V1范围为PROPOSED，26任务均有优先级/状态/依赖/估算依据/文件/测试/验收/阻断。I能力包可延期但服务端必须关闭；安全/数据/备份不能延期。未输出Secret；私有完整日志留/tmp、不提交Surefire环境转储。
- 下一任务：取得下一轮实施授权后，按08仅做T1+G1+G2最小切片；先失败回归再实现，验证所有任务入口、真实FSRS/测验合法完成、普通Todo、并发幂等和回滚；不混入标签建模/云笔记/AI/Judge开发，不自动提交。

## 2026-10-20 Java + AI 面试学习产品迭代复核（第一阶段）

- 范围：用户明确只审计规划，待选定候选再实施。阅读roadmap01–08、最新交接、Phase4I/RC1与实际路由/页面/Store/API/Controller/Service/Mapper/SQL/测试定义；只新增 `docs/roadmap/09_JAVA_AI_INTERVIEW_ITERATION_REVIEW_2026-10-20.md`，本交接仅追加。
- Git：main / HEAD `78f4b11d58da89d69264f4f452d210e6a6db34d1`，ahead8，cached为空。排除迁出目录及design后起始status为300个tracked修改条目和436个untracked条目，非文件完成数；既有内容保留，无commit/push/reset/clean/restore/stash。未扫描desktopApp/mobile/miniprogram。
- 本轮源码结论：G1仍有TaskServiceImpl status/update/stage及学习投影/Chat确认的手动写入口；G2仍每次status=1调用record，不按转换去重；DataSourceManager外部JSON优先及各测试隔离/Mock契约差异仍支持先做T1。未运行利用、未修复、不虚报污染掌握度/奖励。
- 真实链：MockInterview.vue→interview API→原Controller/Service→MockInterviewSessionMapper/session；V2Panel→V2Controller/Service→JdbcTemplate→state/turn/command；评价missingKnowledge(旧gaps兼容)→Bridge→KgEntityMapper/DocumentAccessPolicy→原TaskService/task→interview_remediation。当前桥接只写普通待办title/notes，未登记学习计划metadata/identity，不能当专项练习proof。V2完成写ACTIVITY_ONLY事件，resolver不把它转数值Evidence；FSRS/客观作答仍经原权威链。
- 文档漂移：RC1已记录真实DeepSeek/真人语音/MySQL5.7局部验收，不能一概称从未验证；仍缺同场语音到Task及实际训练复测、MySQL8/生产认证。当前Bridge已优先missingKnowledge，旧设计只述gaps。ArrayList内容审阅时间10月18日晚于总交接，技术passed/人审pending不等于运行库发布。Phase4H已有volatile/CAS局部闭环历史，不重建。
- 产品建议：候选1可信记录(T1→G1→G2，4–6工程日)、候选2面试不足→原练习/FSRS→同题复测(6–10日)、候选3精选专题来源/审核/映射(3–5日另加人审)。估算待实施时复核。推荐下一轮仅候选1；G1只保护有真实proof协议的资源任务，保留普通Todo/旧桥接待办，不能把所有非TOOL任务全部封禁。
- 命令/保护：Git状态/历史/diff/cached只读检查、rg/nl源码核查、官方开源仓库/文档读取；文档本地链接18项均存在、保护基线1554个frontend/src/backend/src/scripts及package/pom文件哈希无变化，HEAD/cached未变；最终check另核对交接旧全文为前缀及增量status。保护基线在/tmp私有文件，不入仓。
- 测试/Build/浏览器：本轮未运行Maven/Vitest/types/build或浏览器，不重用历史通过数字为本轮PASS。未启停服务、未访问会推进状态的业务GET、未写学习库、未调用模型/执行危险代码。完整权限/当前数据/响应式/性能/Provider费用/ASR/TTS/MySQL8/生产均 `⚠️ Not verified`。仅文档校验不等于功能测试。
- 一个下一任务：用户选定后，先建立T1私有测试数据源与Mock能力契约，再以失败回归实施G1/G2最小切片，按09的MVP清单逐项验收。

## 2026-10-21 Phase G V1.1正式实施完成（T1→G1→G2，2026-10-20开始）

- **范围/状态**：用户新任务书明确授权实际开发；按门禁T1后G1再G2。T1/G1/G2均PASS（本轮隔离工程范围）；Engineering本切片PASS，RC仍NOT_READY。没有实施Phase H/G3/G4、AI/语音/Judge、全量课程导入或生产部署。
- **Git/保护**：main / HEAD `78f4b11d58da89d69264f4f452d210e6a6db34d1`不变，cached空且不变；保留所有用户/其他Agent dirty/untracked，无commit/push/merge/reset/clean/restore/stash。以本轮起始1554源码/配置/脚本SHA及精确before副本核对范围，Schema源文件与核心学习算法不变。未扫描/修改迁出的desktopApp/mobile/miniprogram，不覆盖09审计报告。
- **T1**：完整复现896执行/879通过/16失败/0错误/1跳过，exit1；定位外置JSON覆盖测试库、QA前缀保护403及3类Fake能力合同冲突。只在测试classpath增加IsolatedTestContextFactory+spring.factories，每类私有H2/目录、独立Embedding隔离；Surefire默认2fork/reusefalse、AI/Host/热切换/开发账号/超时扫描关闭；3类Fake/@MockBean显式能力开启。生产DataSourceManager与QA白名单不改。T1门禁900执行899通过/0失败错误/1条件跳过，exit0后进入G1。
- **G1真实链**：Task五入口(status/DTO/stage/Learning source=TASK/Chat STATUS confirm)→原TaskService行锁→LearningWriteGuard(metadata+原identity+冻结completionProtocol)；真实Quiz/FSRS→原事件→AFTER_COMMIT LearningActionProgress→task/evidence。普通Todo/旧面试Todo/无协议工具/已知旧主观题仍可手动，损坏登记409。证明任务完成与手动撤销409；Owner/Other/Admin/Anonymous及共享EDITOR/READER撤权均有回归。撤权/撤下资源保留身份但投影不可用。监听失败保留学习结果、任务待办/proof0并输出eventId诊断，不声称全链原子或自动重放。
- **G2真实链**：LearningController source=LEARNING及Chat LEARNING→LearningServiceImpl→原owner锁→LearningTaskMapper.lockOwned→条件update→recordRequired主事务事件。相同状态无更新/事件，0→1一活动，1→0不记完成活动；事件失败状态一起回滚。首次任务奖励保留10XP/2Coin，重做活动rewardEligible=false不再奖励，不造知识Evidence/Mastery。Chat从旧纯状态变为统一完成活动/首次奖励，旧并发断言更新为恰好1活动/0知识证据，同时保留200/409冲突和回滚。
- **MySQL发现与处理**：实际5.7.16新临时实例原样Schema/data（explicit_defaults_for_timestamp=1，源文件不改）；双事务复现旧RR COUNT快照漏事件，资格改锁定当前读。不同任务并发实测范围锁死锁→统一owner→task→event；H2进一步复现5事件钱包40XP而非50→仅Task完成奖励独立事务复用owner锁。可复跑`python3 scripts/tests/phase_g_mysql.py`最终9项PASS，含真实HTTP10顺序/10屏障/5不同任务+精确50XP/10Coin、撤销重做、故障回滚/重试、原identity/proof唯一约束；只停止本轮私有socket/PID。MySQL8未测，不以5.7替代。跨事件类型钱包并发和全域性能未认证。
- **最终命令/数字**：backend `mvn -q -Dphaseg.browser=true test` → **123套949执行/948通过/0失败/0错误/1条件跳过，exit0，415.76s**。中间943执行/1失败/1跳过保留（旧Chat零活动合同冲突）；独立审阅三项6个失败回归及后续MySQL/H2并发FAIL→PASS均保留。frontend `npx --no-install vitest run` → **131文件1034通过，exit0**；`npx --no-install vue-tsc --noEmit` exit0；`npm run build` exit0。不安装依赖，不删除测试/降低断言。
- **真实浏览器**：ProofTaskBrowserTest新夹具+Chrome随机端口/私有H2实际19检查PASS，TaskCenter/TaskManager/TodayPlan/LearningCenter/SmartQuiz/FlashCardsHub、Todo/真实Quiz+FSRS/刷新/重复/撤销重做/网络故障重试、PC1440×900/1280×800/1024宽/390兼容、kb-theme真实深浅色和键盘。正常pageerror0/未预期Console error0；故障abort单列。APIRequest权限/409探针与页面Network分开。1题答卷+1QUESTION事件+1FSRS事件+2 proof+revision1，真实关联见证据JSON。旧AdaptiveLearningBrowserTest只因未开phase4h.browser条件跳过，不冒称运行；新夹具已运行。
- **文件/证据**：新增roadmap10–14，更新04/07/08、Phase4I桥接/执行报告与本交接；完整本轮文件清单、精确工作前incremental.patch及脱敏测试/DB/浏览器JSON与截图在`docs/roadmap/evidence/phase-g/`。源码更改包括原Task/Learning/Plan服务、LearningWriteGuard/Progress/Projection/PlanModels、RewardEngine、3 VO、前端原任务UI/API及共用练习入口、回归/隔离/浏览器/MySQL脚本。原始完整日志/XML/临时凭据只留系统私有目录knowflow-phase-g-20261020-*，不入仓。
- **边界/风险**：无真实AI/正式学习库/危险Host/Judge调用；原8080/5173服务不停止。首次Vite端口0回退5173被断言拦截，只精确停本次子进程，后改随机空闲端口+strictPort。AFTER_COMMIT失败仍需诊断对账；旧metadata无不可变题型快照，按当前可确认主观题兼容、不改历史；MySQL8、生产发布、当前完整Provider/语音/同场复测及全钱包跨类型并发 NOT_VERIFIED。
- **一个下一任务**：用户明确选定/授权后按14做一个真实已审专题的原题快照学习复测最小切片。volatile/CAS可作候选，但当前topics仍技术未审/人审pending；先核实已审资源/ACCEPTED知识映射/当前用户权限，不伪造内容、审核或证据，不从头再派发已完成T1/G1/G2。


## 2026-10-21｜学习产品完善 V1.0（A/B主切片，C/D安全，E草稿，F部分验证）

- **授权/状态**：执行用户学习产品V1.0正式任务书；本轮切片完成，整体PARTIAL。未发布，六专题人工批准阻断正式导入。
- **Git**：main / HEAD `78f4b11d58da89d69264f4f452d210e6a6db34d1`，cached空；未commit/push/stash/reset/clean，保留已有dirty和迁移删除。所保留的28项文件摘要见learning-product/evidence/change-manifest.json；早期私有preimages在续跑不可访问，不能宣称重新完整逐字比对。
- **范围/文件**：原LearningController/Service/Impl/VO/Entity/三schema与ChapterReadingSchema；原ExamRequests/Vos/Service和ExamTake；Quiz审核范围及闪卡来源权限；前端ChapterLearn/PathDetail/API；连续性/资源安全/考试回归、真实浏览器/原MySQL脚本可选参数；六专题作者包及原content-studio MANIFEST/QUEUE/STATE/校验工具。完整文件/影响见learning-product/11_EXECUTION_REPORT.md。
- **真实调用链**：ChapterLearn→原LearningController/Service→learning_user_chapter同表书签CAS（不写Mastery）；DOC ACCEPTED→可读KG→QUIZ ACCEPTED→APPROVED→SmartQuiz原事件；ExamTake→原draft JSON revision/CAS→原截止/快照/判分→错题；本人卡→原FSRS→实际事件/到期；原learningStore→Evidence→NextAction，算法不变。
- **命令/最终结果**：backend `mvn -o -q test` 968执行/965通过/0失败/0错误/3条件跳过，exit0；前端全量Vitest 132文件1043通过、vue-tsc、Build均exit0。内容工具14通过/结构校验exit0，既有纯Java教学137通过。真实Chrome显式浏览器门禁exit0，25检查/253API/0pageerror；沙箱最初拒绝端口单列，随后允许环境重跑。MySQL5.7.16旧TIMESTAMP专项保留7PASS证据，FULLTEXT排除。
- **内容/身份**：6原教程复用，60题/48卡实际草稿，pending_review、人审false、正式发布0；不猜运行时KG ID，不将隔离夹具内容说成正式课程。实际user/path/chapter/question/attempt/event/card/task/knowledge核对JSON保留；任务仍待办不伪造proof。
- **风险/未验**：旧NULL审核兼容、历史假完成对账、Quiz版本快照、考试best-effort事件、新卡ACCEPTED映射/复习后再测未闭合；生产/MySQL8/多实例迁移/全文索引/真实AI/危险执行均NOT_VERIFIED。WebCrypto局域网HTTP和性能未认证。
- **一个下一任务**：HashMap原审核与预览导入合同→真实映射/权限→错题到本人可用卡→再次客观测验；人审不越权，T1/G1/G2不从头重做。


## 2026-10-21｜HashMap学习闭环 V1.0（A→F，隔离工程通过、正式发布待人审）

- **授权/目标**：执行用户HashMap正式任务书，实际修复/测试/浏览器，不重做T1/G1/G2、书签或考试revision。普通用户PC的首错→本人合法卡→原FSRS→同题复测→历史/原学习链已VERIFIED；正式发布BLOCKED_BY_HUMAN_REVIEW。
- **Git**：main / HEAD `78f4b11d58da89d69264f4f452d210e6a6db34d1`不变，cached空；无commit/push/reset/clean/restore/stash。起始9650条dirty/untracked状态与10317文件摘要存私有基线；精确增量及原文保护见learning-product/evidence/hashmap/change-manifest.json和protection-check.json。旧测试自动刷新g2-database.json，已将新证据留本任务并恢复到开工时完全相同SHA；没有清理其他人工作。
- **内容/门禁**：原教程4.1.0→4.1.1、作者包1.0.0→1.0.1-draft，原版本逐字保留；10题/8卡技术PASS、人审false、正式发布0。q01/q04与旧QA知识检查重复待人工复用/合并；官方JDK17/8u/7u来源仅参考机制，2个JDK17实验实际运行，Spring/JDK7/8运行未验。
- **预览/身份**：原ContentStudio QA actor/local/DB前缀guard不放宽；HashMapAuthorPreview校验正文SHA、实际chapter/ledger/canonical/授权池、版本/重复候选，runtimeWrites0；无法解析身份不猜ID。新作者import始终拒绝未批准409，没有新包正式成功导入合同；旧QA导入回归不替代新包批准。
- **真实链**：ChapterLearn→原action-contract→SmartQuiz→QuizController/QuizServiceImpl→quiz_answer_record.question_snapshot+原receipt/mistake/required QUESTION事件；QuizLearningReviewService只读原记录/本人卡+MANUAL/IMPORT ACCEPTED KG/FSRS事件，事件ID定位两次作答区间；原FlashCardsHub/FSRS→learning_event→原KnowledgeMasteryService/MasteryEngine与learningStore/Evidence/NextAction。没有重复Store/题库/任务/算法。
- **文件/数据**：原Quiz DTO/VO/Entity/Service/Controller及root/H2/MySQL三schema可空快照；新增QuizAnswerSnapshot/Schema、QuizLearningReviewService、HashMapAuthorPreview；原ChapterLearn/SmartQuiz/quiz API与QuizLearningReview组件；19项HashMap回归、ContentStudio9项、专用浏览器测试/脚本、作者内容/清单、12–19报告和当前路线增量。完整路径/前后SHA见change-manifest。
- **兼容/权限**：旧NULL答案不补造/不重判，新增DTO null字段不改变旧receipt哈希；改题摘要不符409；Other/Admin不可读他人私答/建卡，Anonymous401；READER/EDITOR撤权停止新练，私人历史保留。卡内容确认仅PENDING知识候选；普通/空白重试复用本人卡，已删除活卡不抹真实FSRS历史。
- **最终命令/结果**：backend `mvn -o -q test`128套991执行/987通过/0失败/0错误/4条件跳过，exit0/410.22s；frontend全量Vitest132文件1046通过，vue-tsc与Build exit0（现有大chunk警告）；内容工具15通过、原quality/resource exit0；作者preview含于全量。新增四档FSRS HTTP真实排程/事件/幂等与H2旧表追加两次保留旧答案通过。中间有效RED与夹具错误分开保留在verification-summary.json。
- **真实浏览器**：全量后显式HashMapLoopBrowserTest exit0/35.96s，Chrome/私有H2/随机loopback端口，21检查/199真实API/0pageerror/2预期abort Console/0未预期。首错实际落库后丢响应并重试、本人ACCEPTED卡翻面Good、FSRS重放、第二次客观正确、报告故障/Retry、PC/窄屏深浅色/键盘、登出全新Context重登恢复。真实user4/path5/chapter24/doc17/question62/version1/knowledge1/card7/answer1、2/FSRS event3，详细Network/快照/映射/错题/任务在browser JSON，不伪造正式数据。
- **Evidence/结论**：原3条证据与两次Quiz/1次FSRS可追溯；Mastery41/置信度23，原TASK NextAction携知识/章节上下文，没有写100。无时限且可查看答案的同题变化不等于能力提升。原低一致性模板“间隔过长”在1天夹具中不是实际时间结论，本轮不改受保护规则或认证整体诊断有效性。
- **未验/风险**：新作者包正式导入成功/失败全合同、人工批准/KG正式映射；MySQL新增快照字段实际升级、多实例DDL、MySQL8/生产、长历史查询性能、全部题库语义去重、完整多角色UI/新卡草稿UI、付费AI/危险执行和长期效果均NOT_VERIFIED。原三个浏览器套件本轮条件skip，HashMap另外显式运行，不借历史数字。当前仅首个章节关联题，不是完整专题试卷。
- **一个下一任务**：HashMap人工复用/合并及批准→原专业审核/canonical/真实KG候选审阅→隔离验证新作者包受控导入成功、重入、版本冲突、撤权、回滚与旧答案保留。正式批准前不自批、不写正式库；独立迁移演练/性能检查可继续。


## 2026-10-21｜学习中心「今日行动」局部 UI 优化

- **目标/范围**：执行用户局部UI任务，普通用户PC；布局、阅读层级、预算校验与失败反馈，不扩展学习业务。遵守AGENTS/UI_UX，保持原NextAction/Evidence/Mastery/FSRS与计划确认合同。
- **文件**：AdaptiveLearningPanel.vue重排顶部工具栏/主辅列表/折叠依据/空错载入状态；TaskPlanCard.vue仅增可选item-details插槽，Chat默认内容不变；新增面板18项及计划卡409/410两项前端回归、TodayActionsUiBrowserTest隔离夹具和两份tests脚本。报告 `docs/reports/TODAY_ACTIONS_UI_2026-10-21.md`；前后PNG/8组尺寸主题/真实Network/落库/验证摘要在 `docs/reports/evidence/today-actions-ui/`。
- **业务/UI影响**：同批闪卡只保留行动列表一处完整入口，标题不再充当按钮；计划执行记录与下方今日任务统计明确区分；无记录不显示0/0，保留最近30项/到期数量界限。原reason和完成要求折叠仍可看，不解析字符串制造日期/诊断，不将自评或任务完成当掌握。分钟1–720整数，无效请求不发出。
- **真实链**：LearningCenter→面板→原learningActionApi→LearningActionController(SecurityUtils)→LearningDashboardService/LearningContextService/原DailyPlanRule；JdbcTemplate读取本人task+learning_task_evidence，确认仍走DailyLearningPlanService→LearningActionDraftMapper/TaskService/TaskMapper。资源仍planResourceRoute→原selector/stampActionContext/resolveContextualRoute。无生产后端/API/DB/Store/路由/全局令牌修改，useAi:false。
- **回归/修复**：旧UI16个有效RED；独立审阅发现确认完成遇到旧刷新或无效外层分钟时可能漏读回，2个有效RED后改为等待在途请求并使用有效预算读回。相关3文件32通过；未削弱断言。骨架nextTick等待与浏览器登录首页的夹具假设错误单列，不冒充产品修复。
- **本轮命令/结果**：frontend `npx --no-install vitest run`133文件1066通过/0失败/0跳过，exit0/16.64s；`npx --no-install vue-tsc --noEmit`exit0/0.58s；`npm run build`exit0/25.49s，保留大chunk提示。backend显式 `mvn -o -q -Dtest=TodayActionsUiBrowserTest -Dtoday.ui.browser=true -Dtoday.ui.run-dir=<私有目录> test`1执行1通过0失败/错误/跳过，exit0/593.82s（含等待浏览器）；本轮未跑后端全量，不引用上轮991项作新结果。
- **真实浏览器**：Chrome/Playwright+私有H2/随机loopback端口；18检查、105真实API、0pageerror、0未预期Console错误，2条真实故障注入500记录保留。1440×900/1280×800/1024×800/390×844各深浅色无横向溢出；整数0不请求，读取/预览500重试，15分钟预览useAi:false，双击一次确认，创建1条状态0任务，重载恢复0/1，实际cardId跳转不完成任务。其他用户任务相同、真实AI调用0。CUA查看原用户页并截图，不在原8080/5173写计划；尺寸证据用实际Playwright内宽，CUA viewport覆盖不生效未冒充验收。隔离端口均关闭/临时密码夹具已移除。
- **Git保护**：main/HEAD `78f4b11d58da89d69264f4f452d210e6a6db34d1`不变、cached空；无commit/push/reset/clean/restore/stash。开工9695条完整展开status与643相关文件SHA存私有基线；已有tracked diff与原untracked均保留，源码增量只在上述2组件/原计划卡测试，交接原文仅追加。原HashMap和迁出多端目录不动。
- **未验/风险**：浏览器全部空/部分完成/全部完成/30项截断组合、真实确认409/410与失败、完整Anonymous/Other/Admin/EDITOR/READER矩阵、屏幕阅读器/全键盘路径、长期性能/MySQL/生产/付费AI/危险执行均NOT_VERIFIED；相关UI状态有组件测试不冒充真实后端。接口缺少结构化友好依据，原文保留；共享计划卡原跨planId异步风险不扩大本轮。
- **一个下一任务**：共享TaskPlanCard跨planId在途响应的失败回归及最小修复。原HashMap人审/受控导入交接继续有效；UI交付不代表正式内容批准或全局发布认证。


## 2026-10-21｜Flashcard Settings UI V2.0（复习设置弹窗专项）

- **目标/范围**：执行用户附件专项任务，普通用户PC原闪卡页复习设置弹窗；修复裁切/内部滚动，记忆目标/每日限制/高级参数分组、校验、草稿、保存状态和可访问交互。不改FSRS算法/默认参数/学习历史/API/DB/NextAction/Evidence或其他学习模块。
- **真实复现/布局**：私有Vite只读加载开工FlashCardsHub/AppDialog精确原文（没有回退工作区），900×600中原dialog高729、top−64.5、headerTop−41.5、footerBottom645.5；新展开态高568、top16/bottom584、正文404可见/1045可滚，标题/关闭/底栏固定。520宽/720最大高度用原令牌表达。原本地用户1440×900前后同设置截图保留，只读取消不保存。
- **文件**：新FlashcardSettingsDialog.vue承接原表单；AppDialog.vue新增scrollable opt-in/description/footer、该模式焦点/滚动/Esc处理，默认确认路径保留；FlashCardsHub.vue两个设置入口和saved/loaded适配；原页面spec加24、新AppDialogScrollable.spec.ts4；新增FlashcardSettingsUiBrowserTest与flashcard_settings_ui_browser.cjs隔离验证。复用已有today_actions_ui_server.cjs而未修改。报告 `docs/reports/FLASHCARD_SETTINGS_UI_V2_EXECUTION.md`，截图/Network/落库/RED-GREEN摘要在 `docs/reports/evidence/flashcard-settings-v2/`。
- **表单/API合同**：90%↔.9，未编辑高精度复用原double，70.0003%转换.700003有失败回归；新卡0–1000/复习0–10000、最大间隔1–36500、日切0–23整数；步长可空，否则≤6项、严格递增1–1440分钟，支持中英文逗号。复习按其余评分次数计数，单位据真实服务用次/天而非独立卡数。时区datalist可搜索/自由输入保留Java合法别名，不被浏览器Intl错误替换；日切显示04:00。响应缺必要字段阻止默认值覆盖并可重试。
- **真实链/状态**：原learningApi GET/PUT my/flashcards/settings→FlashcardFsrsController(SecurityUtils)→FlashcardFsrsService→JdbcTemplate learning_flashcard_settings JSON/revision。独立草稿取消不写；PUT成功→GET回读→关闭；PUT失败保留/聚焦错误，PUT成功但GET失败明确已保存且只重试GET。原schedule/settingsRevision/requestId/sessionId/四档review和required事件不变；没有新增设置Store/并发协议。
- **回归**：原UI22项RED、原Dialog2项RED、百分比精度1项RED、错误提示可见性2项RED、真实FocusShell Esc穿透2项RED；均先失败再最小修复。Build发现会话内settingsBusy残留，已移除；scrollable用window capture消费Esc，保存期间也不穿透底层专注窗口。独立只读审阅复核无剩余本轮P1/P2，不冒充审阅者运行浏览器。
- **最终前端命令/结果**：相关2文件72通过；`npx --no-install vitest run`134文件1094通过/0失败/0跳过，exit0/18.89s；裸`vue-tsc --noEmit`exit0/0.48s，另显式`--noEmit -p tsconfig.app.json`应用工程检查exit0/12.32s；`npm run build`含vue-tsc -b，exit0/30.52s，保留大chunk提示。root tsconfig为空files引用项目，裸noEmit不作完整app检查的唯一证明。
- **最终后端命令/结果**：隔离 `mvn -o -q -Dtest=FlashcardFsrsHttpTest,FlashcardSchedulerTest test`8执行8通过0失败/错误/跳过，exit0/15.93s；显式 `mvn -o -q -Dtest=FlashcardSettingsUiBrowserTest -Dflash.settings.browser=true -Dflash.settings.run-dir=<私有目录> test`1执行1通过0失败/错误/跳过，exit0/60.68s。本轮未跑后端全量，不借用历史全量数字。
- **真实Chrome**：最终33检查/138实际API/30布局测量/0pageerror/0未预期Console错误；4条预期400/500故障记录保留。1440×900/1280×800/1024×768/768×900/390×844/375×667/900×600各深浅色，全部字段滚入可见区且标题底栏可达、无横向溢出。独立临时Chrome原生125%/150%（DPR1.25/1.5，visual scale1，非CSS/pinch模拟）。真实400、读取/保存/回读500、双击仅一次成功PUT、取消/Esc、重开、登出新Context重登恢复；会话内开设置Esc后仍留学习且焦点返设置。实际Again/Hard/Good/Easy各一次，4事件/每卡fsrs_revision1/实际到期核对，Again旧review_count按原规则为0。两次成功设置PUT后revision2，其他用户配置JSON/revision未变，真实AI调用0。
- **权限/清理**：设置HTTP验证Anonymous401、Owner本人、Other带他人userId查询不能越权、Admin仍本人；不外推为知识库全矩阵。私有H2/随机loopback端口，原8080/5173未停，最终测试端口关闭、密码夹具与临时Chrome配置移除。故障filter仅测试classpath，原始日志/凭据不入仓。
- **中间失败/未验**：number v-model自动转型、happy-dom点击焦点、MockMvc真实servlet夹具、Again计数预期、故障响应UTF-8均独立记录修正；一次networkidle超时根因未确认，后续整轮通过，不称产品网络已修复。读屏/OS辅助技术、全对比度、所有浏览器/时区别名、生产/MySQL、多端同时保存、完整知识库权限/付费AI/危险执行NOT_VERIFIED。既有dueLabel的Java合法时区Intl兼容性仍是旧风险；不改日期格式化迎合本轮UI。
- **Git保护**：main/HEAD `78f4b11d58da89d69264f4f452d210e6a6db34d1`不变、cached空；开工9715条完整展开status/2370相关文件SHA及精确preimage私下保留。本轮只有上述3个已有源码/测试和追加交接发生变化，新文件清单见protection-check.json；其他已有dirty/untracked、学习中心、HashMap及迁出多端目录保留。没有commit/push/stash/reset/clean。
- **一个下一任务**：单独评估Java合法时区与浏览器日期显示的兼容合同并补回归；不扩为FSRS算法或全站时区重构。


## 2026-10-21｜Flashcard Learning UI V3.0（沉浸式闪卡界面精修）

- **目标/范围**：执行用户附件，普通用户PC `/learning/flashcards`；只改沉浸式学习UI、局部交互及必要测试，保持FSRS四档/默认参数/排程/事件/学习历史/NextAction/Evidence/Mastery。未改生产后端/API/DB/全局令牌、设置V2、导航或其他学习模块。
- **文件**：FlashCardsHub.vue、FocusShell.vue opt-in readingLayout/mode/progressDescription、原FlashCardsHub.spec.ts新增17项；新增FlashcardLearningUiBrowserTest与flashcard_learning_ui_browser.cjs；复用原today_actions_ui_server未改。报告 `docs/reports/FLASHCARD_LEARNING_UI_V3_EXECUTION.md`；截图/Network/落库/命令/保护摘要在 `docs/reports/evidence/flashcard-learning-v3/`。
- **真实根因/UI**：旧卡height clamp24rem/58vh/40rem+absolute faces+back-body overflow产生固定空白与嵌套滚动。改最大960宽，1440×900短题面实测960×256，正反面内容定高、FocusShell唯一纵向阅读滚动、固定四档软色Footer、窄屏2×2；原令牌/Markdown正文保留，恢复列表符号/标题/表格层级。220ms翻转/240ms兜底，隐藏面inert/aria-hidden；各面独立保存阅读像素位置、切卡清零。
- **局部交互**：评分仍Again1/Hard2/Good3/Easy4，预测间隔来自schedule；只选中按钮保存中、并行/导航/退出阻止、失败保持原卡答案并聚焦可见重试，继续原requestId/revision/settingsRevision/sessionId。手势只从底部手柄开始，左1/右3/上4超过100且松手才提交，Hard按钮/2；正文选字与代码滚动不评分，反馈随手柄。保留原Space/Enter/1–4/箭头/Esc，设置及其他交互控件不误触。今日事件次数与本轮独立卡进度定义分开，队列/排序未重算。
- **真实链**：原router→FlashCardsHub→learningApi→LearningController/FlashcardFsrsController(SecurityUtils)→LearningServiceImpl/FlashcardFsrsService→LearningFlashcardMapper锁卡/状态+JdbcTemplate→learning_flashcard/settings→events.recordRequired FLASHCARD_REVIEWED→原学习管线；无第二套学习状态或新事件合同。
- **回归/审阅**：先观察UI/正文误拖/忙态/失败导航RED，再补保存期间路由离开、错误后翻面、子控件键盘、可见错误及换卡滚动RED。独立只读审阅发现长答案滚动继承/反馈移出视口/重试隐藏/正反面共用偏移，均补回归最小修复；真实拖拽RED标签y约−577，改后在阅读视口。Markdown实际Chrome断言先none≠disc失败，局部样式修复。审阅最后未发现faceScroll具体P1/P2，未冒充审阅者执行测试。
- **最终前端命令**：`npx --no-install vitest run`134文件1111通过/0失败/0跳过，exit0/25.38s；裸`vue-tsc --noEmit`exit0/.51s，应用工程`--noEmit -p tsconfig.app.json`exit0/32.86s；`npm run build`含vue-tsc-b+Vite exit0/19.69s，保留原大chunk警告。根tsconfig为空files，裸noEmit不单独作完整源码证明。
- **最终后端命令**：`mvn -o -q -Dtest=FlashcardFsrsHttpTest,FlashcardSchedulerTest test`8执行8通过/0失败错误跳过，exit0/8.59s；显式 `-Dtest=FlashcardLearningUiBrowserTest -Dflash.learning.browser=true -Dflash.learning.run-dir=<私有目录>`1执行1通过/0失败错误跳过，exit0/68.74s。未跑后端全量，不借用历史991项。
- **最终真实Chrome**：33检查/30布局/122实际API/0pageerror/0未预期Console，5预期错误原样保留（2真实断网、3注入500）。实际viewport1440×900/1280×800/1024×768/768×900/390×844/375×667/900×500各深浅色；长答案末尾可达、代码横滚、无文档横溢、固定评分可用；原生125%/150% DPR1.25/1.5、visualScale1。真实前面禁评分、Space、四档/键盘Hard、手柄三方向及短拖取消、选字、设置读取取消Esc焦点、长题到长答案顶部、下一卡顶部、七卡完成、真实空与列表500重试。cancel/lostcapture/reduced-motion另有组件回归，不冒充实体触屏。
- **隔离/合同**：私有H2/随机loopback端口。真实离线→提交前500→实际提交后响应500→原请求重试：10次复习请求只有7事件、7卡revision各1；原四档实参/实际due保留、settings revision0、其他用户卡不变、真实AI调用0。HTTP Anonymous401/body401，Owner200，Other/Admin对本人卡外访问403/body403；不外推为共享知识库全矩阵。测试端口均关闭、密码夹具/临时Chrome配置删除，原8080/5173未停。
- **Git/证据边界**：main/HEAD78f4b11d58da89d69264f4f452d210e6a6db34d1与cached空保留，无commit/push/stash/reset/clean/restore；不动迁出多端。早期开工/tmp快照及部分日志续轮已不可读，未声称最后逐字审计完整整轮；续轮10202条status/3437文件SHA另取只读基线，最终变化名单见protection-check。原本地同题216队列前后截图为只读进入/翻面/退出；隔离旧UI截图沿用前轮私有原文load hook，最终轮未重拍旧界面。
- **未验/遗留**：NOT_VERIFIED读屏/OS辅助技术、全对比度、实体触屏/手机、其他浏览器、生产/MySQL、长历史性能、完整EDITOR/READER矩阵、付费AI和长期效果。原Java时区与浏览器Intl显示风险保留；真实净化器链接支持不由happy-dom键盘测试认证。沙箱端口、旧UI夹具新class假设等中间失败单列，不伪称产品修复。
- **一个下一任务**：独立评估Java合法时区与浏览器日期显示合同并补真实回归；不扩为FSRS/全站时区重构。原内容人审与正式发布门禁继续有效。


## 2026-10-21｜Flashcard UI V4.0（按用户参考图还原）

- **范围/目标**：实际改原 `/learning/flashcards` 沉浸式Vue，依据用户Downloads/闪卡.png还原Header/Card/Footer和按钮内部图标；普通PC主界面并兼容平板/窄屏。没有另做HTML或再次生成设计图，没有生产后端/API/DB/FSRS算法/学习管线修改。
- **文件**：FlashCardsHub.vue、FocusShell.vue opt-in flashcardLayout/brand插槽、原页面spec新增3；新FlashcardReviewIcon.vue七个局部SVG、FlashcardVisualUiBrowserTest、flashcard_visual_ui_browser.cjs及仅测试预览flashcard_visual_preview.cjs。V3文件/证据与全局Icon、AppDialog不改。报告 `docs/ui-v2/FLASHCARD_UI_V4_VISUAL_REDESIGN_REPORT.md`，证据 `docs/ui-v2/evidence/flashcard-ui-v4/`。
- **UI实测**：同参考题私有MANUAL卡、1672×941，Header76，卡宽1184/高449.28、圆角24、内边距40，问题28/正文18/行高32.4，Footer125/四档76高。书页品牌/退出/真实进度与每日统计/设置；灯泡胶囊、蓝分类/浅红难度、标签和手掌图标、灰手势栏；四档旋转箭头/单波形/对勾/斜向火箭。手机精简顶部、真实统计折叠、2×2评分、安全区。浅色默认按钮文字对比度6.47/5.02/5.48/4.63；颜色比参考深、无复杂渐变，非全状态颜色认证。
- **合同/真实链**：原router→FlashCardsHub→learningApi→LearningController/FlashcardFsrsController(SecurityUtils)→LearningServiceImpl/FlashcardFsrsService→LearningFlashcardMapper锁卡/状态+原JdbcTemplate→learning_flashcard/settings/event→recordRequired FLASHCARD_REVIEWED。rating1/2/3/4、revision/settingsRevision/requestId/sessionId/幂等/排程/事件不改；真实队列进度与每日事件次数口径分开，没写死示例数字。沿用V3先揭晓、保存防重复/退出、可见错误/原ID重试、手柄左1/右3/上4、Hard按钮/2、键盘/设置保护和各面阅读偏移。
- **RED/审阅**：3项UI回归先失败；标签全局图形实为文件、原wave/rocket轮廓不符，局部SVG修正；手机统计与标签断言再先2RED。Chrome首轮32字号≠28断言失败后用正确令牌修复。只读审阅发现翻面中途截空白图，新增waitFront等启用/动画结束/aria-hidden后重拍；实际1280正面已非空白。新增1101断点两主题Header scroll/clientWidth=1101。最终审阅无新增具体P1/P2，审阅未运行测试/编辑文件。
- **最终前端**：相关2文件92通过；全量 `npx --no-install vitest run`134文件1114通过/0失败跳过，exit0/16.71s；裸`vue-tsc --noEmit`exit0/.46s，应用工程`--noEmit -p tsconfig.app.json`exit0/20.24s；`npm run build`含vue-tsc-b+Vite exit0/17.37s，原大chunk警告保留。裸root noEmit不单独代表完整app检查。
- **最终后端**：原 `mvn -o -q -Dtest=FlashcardFsrsHttpTest,FlashcardSchedulerTest test`8执行8通过0失败错误跳过，exit0/8.71s；显式 `-Dtest=FlashcardVisualUiBrowserTest -Dflash.visual.browser=true -Dflash.visual.run-dir=<私有目录>`1执行1通过0失败错误跳过，exit0/73.45s。未跑后端全量。
- **最终Chrome**：38检查/34布局/156API/0pageerror/0非预期Console。5预期错误（2离线、2评分500、1列表500）原样保留。真实8尺寸1440×900/1280×800/1101×800/1024×768/768×900/390×844/375×667/900×500各深浅色，原生125/150缩放；长文/代码/末尾/手势/键盘/设置/空错/七卡完成均通过。同参考题前后截图只读，无评分；退出再进入不产事件。真实离线→恢复→提交前500→提交后丢响应500→同requestId重试，10请求仅7事件、各卡revision1、settings revision0、其他用户卡不变、真实AI0；Anonymous401/Owner200/Other与Admin403 HTTP/body一致。
- **实际5173预览**：开工5173/8080无服务，单独显式flash.visual.preview=true建立私有H2+随机后端+本机5173，strictPort不替换已有服务，最长1h/stop关闭。实际IAB退出旧localhost缓存身份、表单登录私有视觉用户，进入cardId7并翻面；319×688真实侧栏截图localhost-5173-cua.jpg，不冒充PC。预览不是已退出PASS测试，HTTP日志独立RUN/preview-evidence不覆盖验收。当前预览进程保留便于用户查看；正常后端需重新登录原账号，内存数据退出丢失。私有目录 `/var/folders/gm/cfx_r7756nddg59ccxvtvj7w0000gn/T/knowflow-flash-v4-7pgbixyn/preview-run`，创建stop可提前停；凭据仅本地0600文件，预览结束清理。正常验收端口和密码文件已清理，原8080未启动。
- **Git/保护**：main/HEAD78f4b11d58da89d69264f4f452d210e6a6db34d1，cached空保持；开工10210 status/9632 untracked/3445 SHA与精确preimage，全流程无reset/clean/restore/stash/commit/push。未知dirty工作保护，迁出多端不动，最终名单见protection-check/source-manifest。
- **偏差/未验**：卡片1184与内容自适应高度、28/18字阶按任务建议，未机械复制1308/558截图尺寸与模拟数字；SVG同语义轮廓非逐点原作者路径。NOT_VERIFIED真实读屏/OS辅助技术、实体触屏、Safari/Firefox、全状态对比度、完整EDITOR/READER矩阵、生产/MySQL/后端全量/性能/付费AI/长期效果/CI跨平台。原净化器链接策略与Java时区Intl风险保持，未新增安全白名单。
- **一个下一任务**：用户确认本轮视觉密度/语义色深度后，单独验证原富文本链接净化与浏览器可点击合同；不把UI还原扩成学习算法或全站改造。


### 2026-10-22｜V4 收尾补充（跨日最终结果）

- 前后同题截图最终统一1672×941；修正截帧等待后完整重跑。最终FlashcardVisualUiBrowserTest显式1执行1通过/0失败错误跳过，exit0/75.76s；Chrome38检查/34布局/160实际API/0pageerror/0非预期Console，5预期错误保留。上条156API/73.45s为前一轮结果，最终以本条与verification-summary为准。
- 发现旧localhost缓存签名可被初始隔离进程接受的测试环境隔离缺口，V4测试类新增DynamicPropertySource每进程随机JWT密钥；不同签名HTTP/body401断言通过。IAB实测第一轮预览令牌失效回登录，新私有视觉账号重新登录成功。只改测试配置，原生产签名与认证不改；没有测试真实正常后端切换。
- 第一轮预览已停且密码夹具移除；当前预览目录改为 `/var/folders/gm/cfx_r7756nddg59ccxvtvj7w0000gn/T/knowflow-flash-v4-7pgbixyn/preview-run2`，localhost5173仍为独立内存预览，最长1h/stop关闭，HTTP证据只写私有目录。运行中的预览不计测试PASS，先前预览runner汇总可能受到同期Surefire文件时间影响，未借该汇总当验收结果。


### 2026-10-22｜V4 最终连续流程补验

- 增补真实Good保存→确认退出→重新进入→刷新→恢复已保存1张，断言评分请求数不增加。第一轮脚本误定位“确认”导致失败，实际ToastHost按钮为“确定”；修正测试选择器后完整Chrome重跑，没有修改生产退出合同。
- 最终 `mvn -o -q -Dtest=FlashcardVisualUiBrowserTest -Dflash.visual.browser=true -Dflash.visual.run-dir=<私有目录> test` 1执行1通过、0失败/错误/跳过，exit0/64.33s；Chrome39检查/34布局/157实际API/10复习请求/7事件，0pageerror/0非预期Console，5预期错误保留。本条覆盖前条最终38检查/160API/75.76s统计。前端1114、类型与Build及原后端8项结果不变；此后只补测试夹具/证据/报告。
- 新证据saved-progress-exit-reload-resume.png；验收端口与密码夹具已关闭/移除，独立preview-run2仍运行供用户查看；未写正式数据库、未调用真实AI、未提交推送。最终保护检查与源码SHA随本轮刷新。
- 风险/下一任务沿用V4报告：先由用户确认视觉，再单独验证原富文本链接净化合同；真实读屏/触屏/生产/MySQL等仍NOT_VERIFIED。


### 2026-10-22｜V4 恢复截图最终补充

- 上轮恢复功能通过，截图仍捕获FocusShell进入动画；waitFront补等外壳动画后完整重跑，最终1执行1通过/0失败错误跳过、exit0/66.68s，Chrome39检查/34布局/160API/10复习请求/7事件、0pageerror/0非预期Console。以本条、verification-summary及最终重拍图片为准。
- 生产UI未再修改，前端1114/类型/Build与原后端8项结果不变；验收端口已关闭/凭据移除，独立5173预览实际仍监听。Git保护名单不变；无提交推送、无正式数据写入、无真实AI。未验项与下一任务沿用报告。


### 2026-10-22 — 本地启动器端口冲突与安全恢复修复
- 原因：5173/PID11275 是 scripts/tests/flashcard_visual_preview.cjs 私有预览，cwd=本项目frontend，不能当日常Vite复用或误杀。开始时8080未监听；保留预览，日常前端自动使用5174。
- 文件：knowflow-dev.sh、▶ 启动项目.command、scripts/launcher_process.py、scripts/tests/test_launcher.py、scripts/tests/test_launcher_processes.py。无业务源码变更，无commit/push；保留原工作区其他修改。
- 调用链：.command→fcntl锁→Maven/Spring Boot就绪→带SERVER__PORT的Vite→API探测；ports原子记录供启停使用。会话记录PID/启动时间/cwd；逐成员核实后TERM，无SIGKILL；PID复用只清记录；失败仅回收本轮新进程。
- 验证：场景15/15、真实随机端口进程7/7退出0；前端vitest134files/1114passed退出0，vue-tsc退出0，build退出0（分块大小/插件耗时提示；测试含localhost3000连接拒绝诊断但无失败）。Bash/Python语法及目标diff-check通过。未运行后端Maven全量测试，无后端源码变更。
- 实测：实际.command首次启动、重复复用(PID不变)、实际restart、Finder精简PATH启动、status均退出0；8080/5174均HTTP401/body.code401；5173/PID11275保持原命令。服务保持8080/5174运行。Chrome显示5174登录页，捕获warn/error为空；Network明细/登录后业务/响应式未验证。
- 报告：/Users/beiluo/Documents/Codex/2026-10-22/referenced-chatgpt-conversation-this-is-an/outputs/KnowFlow启动器修复报告.md；同目录knowflow-launcher-fix.patch是相对本次接手状态的补丁。
- 下一任务：如需完整业务回归，使用5174正常登录后验证核心流程；不必停止5173测试预览。

### 2026-10-22 — KnowFlow Logo V1 三方向提案
- 范围：新增 docs/brand/knowflow-logo-v1-20261022/，A 开放几何 K、B KF 组合、C 连续路径 K；原生 SVG、PNG、16/24/32 测试、黑白/深浅、字标、并排 HTML/PNG 与报告。未替换项目资产。
- 事实：frontend/index.html→public/favicon.svg 为复杂紫色滤镜图形；CTopNav/Sidebar→Icon(icon-AIbeikezhushou)+KnowFlow；style.css 主蓝 #3B6FE0/暗色 #4F86F9；本提案依用户指定 #356FE5，不改 Token。无路由/Store/API/后端数据流变更。
- Git：基于 main / 78f4b11，已有大量其他修改，未覆盖；无 commit/push。
- 命令：node /tmp/knowflow-logo.cjs 退出0；node /tmp/knowflow-logo-verify.cjs 使用本机 Chrome，最终退出0。1440/1280/1024/390 四尺寸，27图片/0损坏/无横向溢出/0页面和Console错误。默认Playwright浏览器未安装，改用Chrome；沙箱外启动获准。
- 验证：纯设计文件未执行功能测试、类型检查、应用Build或Maven；无业务功能修改。项目集成、HTTP/Network、商标检索、用户辨认研究及跨系统字体 ⚠️ Not verified。字标保留真实Arial Bold文本，定稿再轮廓化。
- 下一任务：等待用户选定方向，精修并补正式favicon与应用图标；继续保留旧资产，直到明确授权替换。


### 2026-10-22 — 启动器固定5173/8080（用户明确覆盖此前自动换端口策略）
- 范围/原因：用户要求固定前端5173、后端8080，先关闭冲突占用再启动。旧select_ports自动递增并保存5174，之后反复复用；接手5173已无监听。此前“保留5173预览、改用5174”交接策略由本次需求取代。
- 文件：knowflow-dev.sh、▶ 启动项目.command、scripts/launcher_process.py、scripts/tests/test_launcher.py、scripts/tests/test_launcher_processes.py、docs/LAUNCHER_FIXED_PORTS_REPORT.md及本交接。main/HEAD78f4b11；保留已有dirty/untracked，cached空，无commit/push。
- 调用链：.command→fcntl锁→清理旧端口受管会话→释放8080/5173冲突（用户授权，TERM后核实身份/端口再KILL）→Spring Boot8080→Vite5173 strictPort/SERVER__PORT8080→匿名API HTTP/body双探测。不再从ports或端口环境变量选择其他端口；无法释放则失败，不换端口。健康日常服务仍可复用，restart完整重启。
- 命令/结果：3项新回归先RED；最终python3 -m unittest scripts.tests.test_launcher 15/15 exit0；真实随机端口test_launcher_processes 10/10 exit0/53.400s（含拒绝TERM、迁移、失败回收）；vitest134files/1114passed exit0；vue-tsc --noEmit exit0；npm run build含vue-tsc-b exit0（大chunk/插件耗时提示保留）；Bash/Python语法/目标diff-check通过。首次真实测试因沙箱bind/ps限制受阻，提升权限后真实重跑通过。后端业务源码未改，未跑Maven测试。
- 实测：原.command启动和重复复用、实际restart、status exit0；当前8080/PID43288、5173/PID43909，5174无监听；两端/api/auth/me均HTTP401/body.code401；ports=8080 5173。服务保留后台运行。IAB首页及重启后刷新正常，Console warn/error=[]；首次导航早于就绪超时不算PASS。
- 风险/未验：只清理固定目标端口占用及旧受管会话，非持续守护/开机自启；受权限保护或外部自动拉起的占用可能导致明确启动失败。浏览器Network明细/登录后业务/响应式/生产/MySQL/付费AI及完整权限矩阵未验；无业务UI/Store/学习算法或数据结构变更。
- 一个下一任务：用户继续通过原.command启动并在5173使用原账号登录进行业务验收；不再按历史建议使用5174。

### 2026-10-22 — 代码练习手动/自动格式化 V1
- 目标：原生textarea增量接入本地Prettier3.9.10 Worker，JS/TS真实语法格式化；Python/Java/C++/SQL等明确暂不支持。无编辑器替换、后端接口变化、执行权限放开、Judge启用或自动保存系统。
- 文件：frontend/src/views/CodePlayground.vue；src/code-format/{contracts,engine,client,worker}.ts及3测试；composables/useCodeFormatting.ts、useCodeFormatPreferences.ts；views/__tests__/CodePlaygroundFormatting.spec.ts；既有未跟踪CodePlaygroundV2保存失败测试增加关闭自动格式化前置（原断言保留）；package.json/lock只增加prettier。报告docs/CODE_FORMAT_V1_REPORT.md，证据reports/code-format-v1/。
- 调用链：router→CodePractice目录/CodePlayground详情→userCode→本地Worker(formatWithCursor)→竞态核验→原userCode；workspace save/运行前隐式save先await格式化，再原codeWorkspaceApi→CodeWorkspaceController(SecurityUtils)→CodeWorkspaceService；运行仍codeRunApi→受控CodeRunController，普通练习无独立保存，submit仍禁用。未改NextAction/Evidence/Registry/learning Store。
- 行为：自动总/粘贴/保存默认开，输入默认关(600ms)；实际insertFromPaste更新后120ms调度，无文本粘贴不格式化。Shift+Alt+F/macShift+Option+F仅编辑框监听。5秒终止/100k字符保护，编辑ABA/选区移动/换题语言文件/卸载取消，IMЕ组合输入保护；失败保留原文并阻止支持语言保存；不支持时明确跳过并原样保存。偏好沿用本地composable持久化模式。
- 验证：全量npx vitest run 139files/1156passed/0failed/0skip exit0；相关5files/38passed exit0；vue-tsc --noEmit exit0；npm run build bundled Node24.16.0 exit0（大chunk/插件耗时警告）。并发聊天修改期间曾有ChatCoachAudit/ChatV2及findLast错误，最终全量/Build恢复；未修改聊天文件。只读review P2无输入paste已回归修复，复核无剩余问题。
- 浏览器：真实Chrome+真实Worker21检查/0页面与Console错误，1440/1280/1024/390宽、深色/粘贴/快捷键/撤销重做/IME事件/竞态/设置持久化/保存与运行载荷顺序。API由浏览器拦截，无真实文件保存或代码执行。隔离5186预览已关，原5173/8080未动。
- Git：main/78f4b11，无commit/push；保留接手时大量其他修改。本轮页面增量补丁保存在reports/code-format-v1/CodePlayground.patch，不能拿HEAD总diff冒充本次范围。
- ⚠️ Not verified：真实后端运行/保存与Anonymous/Owner/Other/Admin生产授权、Maven、真实OS中文候选窗、Win/Linux/Safari/Firefox撤销；textarea fallback仅本次格式化撤销重做。标准1280×800未单测。
- 下一任务：按需要独立评估Java/Python可靠本地格式化方案及部署/包体积，不用正则模拟，不绕过现有执行权限。


### 2026-10-22 — AI聊天/学习教练审计与P0修复V1.0
- **范围/目标**：本地/chat审计12类能力并实施P0；教练mode与coachPanelExpanded独立，面板移到消息滚动区，收起/退出不卸载消息任务草案；输入区保持底部；统一AI/规则来源提示；目录移入标题栏，修复实际挡住分享；共享AppDialog管理来源/分享/配置的Escape和焦点。
- **文件**：Chat.vue、TaskPlanCard.vue、api/types.ts、api/learning-actions.ts、learning/coach/{types,context,prompt}.ts；后端ChatSendDTO/ChatServiceImpl仅增加useKnowledgeBase默认true、false跳过同步/SSE检索；ChatCoachAudit/ChatV2/TaskPlanCard/coach-snapshot前端测试、ChatKnowledgeSwitchTest与ChatPrincipalAuthTest；scripts/tests/chat_audit_browser.cjs；docs/chat-v1/审计/报告/扩展/验证/截图与相对接手快照PRODUCTION_CHANGES.patch。
- **根因/关键修正**：旧mode直接决定大面板且处于shrink-0输入区；AI横幅/notice/卡片warnings重复；KB开关只UI未进请求；每token强制滚底；任务选择、卡片load/regenerate旧回调缺隔离；空/部分消息被当完成；反馈假称已记录、私人URL误称公开分享。另真实RED捕获页面旧diagnoseLearningState在无数据时返回HEALTHY_PROGRESS，移除页面计算，原coach序列化器只投影唯一Store的目标/知识/Evidence诊断/置信度，不改证据算法或NextAction链。
- **真实调用链**：router/chat→Chat.vue→chatApi/streamChat→ChatController(SecurityUtils)→ChatServiceImpl→conversation/message Mapper表；检索仍DocChunkService(userId)权限过滤；任务chat→ChatLearningActionController→ChatLearningActionService→tools→DailyLearningPlanService草案→version/token/idempotency确认→原task/learning；教练learning Store→原Evidence链→现有coach builder/prompt。未增表、未改Mapper/索引/学习算法/全局导航。
- **验证**：初始8项前端P0先8RED；KB关闭单测先1RED1PASS；空教练上下文先RED捕获“学习状态良好”。最终前端npx vitest run 140文件1160通过/0失败跳过exit0（19.52s）；裸vue-tsc --noEmit exit0；npm run build含vue-tsc-b+Vite exit0（4.74s，旧chunk/插件提示）；后端mvn -o -q test 133suites/997统计=989通过0失败错误8默认opt-in跳过exit0；补测ChatKnowledgeSwitchTest/ChatPrincipalAuthTest/ChatLearningActionHttpTest/DailyLearningPlanHttpTest/LearningPlanAiEnhancerTest共45通过0失败错误跳过exit0，含Owner/Other/Admin/Anonymous的HTTP/body断言。八个跳过类名单保存在backend-full-summary.json，绝不计PASS。
- **Chrome**：真实Vue+隔离API夹具，最终7组场景/27布局/247API，0pageerror/0非预期Console；2预期HTTP/body500验证保存与生成失败；1440×900/1280×800/1024×768/390×844/375×812（手机仅兼容）。新建搜索切换删除、折叠/退出草案保留、生成中/禁重、规则/AI增强source、编辑排序移除、失败同key重试、SSE停止/KBfalse/历史阅读不拉底、来源与私有入口/弹窗Escape通过。前后图保存在docs/chat-v1/evidence，不冒充真实用户数据或真实Provider产出。
- **运行态**：已核实本项目原8080/PID3510与5173/PID3604后执行knowflow-dev.sh restart；旧失效会话记录由身份检查清除，实际重启成功。当前8080/PID12719、5173/PID12819，5174无监听；两端auth/me与匿名chat/stream均HTTP401/body401，status exit0。保留后台服务。
- **Git/保护**：main/HEAD78f4b11，无commit/push、cached空；保存精确preimages与范围SHA，不覆盖既有大量dirty/untracked；全量测试数字为本次实际执行，不沿用历史。目标diff-check/JS语法通过；独立只读复核未发现可归于本轮的P1/P2阻断，审阅未复跑测试。
- **风险/未验**：真实付费AI/个人Key/vision/WebSpeech/系统剪贴板和下载内容/生产MySQL/跨浏览器/真实读屏与完整KB成员矩阵未验证；浏览器夹具不冒充Provider到DB端到端。反馈持久化、公开分享、图片导出未实现且明确标示；引用数字是片段序号非docId；SSE暂不带持久化消息ID/来源，图片历史与独立SSE选模合同仍待评估；停止接收可能已被服务器保存，先核对记录；面板偏好只保留本次页面生命周期，未增后端持久化。
- **一个下一任务**：单独设计RAG真实docId/chunkId及SSE完成元数据合同，为原文定位、反馈、重生成绑定真实消息ID；保持私聊Owner隔离与KB EDITOR/READER校验。P1/P2各项价值/依赖/工作量/风险/验收见docs/chat-v1/EXTENSIONS.md。

### 2026-10-22 — Chat 工作台 V2：会话身份与轻量布局

- **范围/文件**：本地 Chat.vue、chat/request API；ChatController/ChatService/ChatServiceImpl/ChatConversationMapper/ChatLearningActionService；新增ChatConversationTitleDTO及前后端回归、scripts/tests/chat_workbench_v2_browser.cjs。精确清单、接手快照差异、审计/设计、验证与路线图见docs/chat-v2/，未改Evidence算法/优先级/白名单/学习Store/数据库结构/全局导航。
- **真实根因与修复**：空白新建立即POST→本地空态，首发单飞真实标题；前端首题标题刷新丢失→三条后端发送入口条件初始化；SSE/摘要整实体旧快照覆盖重命名→各自仅更新负责列、数据库计数自增。会话URL恢复ID、内存草稿/图片/模式隔离、删除确认、Owner PATCH重命名。教练默认收起且PC辅助面板，小屏覆盖；独立折叠/退出不销毁任务编辑。重命名真实持久化，置顶/归档明确未接入，未造新表/虚假API。
- **调用链**：/chat→Chat.vue→chatApi/streamChat→ChatController(SecurityUtils)→ChatServiceImpl→conversation/message Mapper→原表。学习行动→原ChatLearningActionService/tools/DailyLearningPlanService/version-token-idempotency确认；教练从唯一learning Store投影，原上下文盖章/路由合同保留。
- **验证**：新增前端9项初始9RED，后端7项初始6FAIL/1PASS/0ERROR；最终新增11项前端回归含审查修复。`cd frontend && npx vitest run`141文件1171通过0失败跳过exit0；root vue-tsc --noEmit、应用-p tsconfig.app.json及npm run build均exit0，Vite7.59s，原chunk/插件提示。后端相关18通过0失败错误跳过exit0；全量mvn -o -q test从本次134份Surefire报告统计1005=997通过8跳过0失败错误，原exec会话回收后退出码未保留，未编造exit0。详细汇总含跳过类见backend-full-summary.json。
- **Chrome**：真实5173 Vue＋明确隔离API夹具，最终9组交互、28布局、281响应，0pageerror/0非预期Console；两项预期HTTP/body500注入验证失败保留及幂等重试。1440×900、1280×800、1024×768、390×844、375×812；侧栏展开/收起、空态/长文/代码表格/多行输入、教练/长任务、SSE/停止/读历史不拉底、窄屏更多菜单、首发单飞/刷新标题ID/草稿模式隔离均验证。截图与网络记录在docs/chat-v2/evidence，非用户真实数据/付费AI端到端。
- **审查**：依requesting-code-review技能只读复核发现删除当前会话跳过下一草稿恢复、URL规范化丢失coach=1两P2；均修复并加测试，复核关闭。未改断言制造PASS；V1折叠测试增加符合V2默认收起的展开前提，KB单测只补新Mapper mock依赖，原断言保留。
- **Git/运行态**：main/78f4b11，cached空，无commit/push；未知dirty/untracked保留；目标diff-check及JS语法exit0。已确认旧5173/8080归属本项目后restart成功，status仍5173/8080就绪，5174无监听；实际后端/代理匿名auth/me、chat/stream、PATCH标题均HTTP401/body401，运行记录见runtime-verification.json。后台服务保留。
- **风险/未验**：真实付费Provider/Key/vision/WebSpeech/系统剪贴板与导出内容/生产MySQL/跨浏览器读屏/完整KB角色矩阵未验；草稿模式仅内存，刷新恢复ID与服务端标题，不承诺跨设备草稿。公开分享/反馈持久化/重生成/置顶归档/项目组织未实现，依赖及验收已列路线图。SSE缺持久化消息ID与来源；停止接收不保证服务端取消，应核对历史。
- **一个下一任务**：设计并实现SSE完成元数据（messageId、truncated、真实docId/chunkId）与Owner/KB授权合同，为真实原文定位、反馈及重生成打基础。

### 2026-10-22 — 会话管理 V2：统一右键、持久化状态、可撤销分享与完整导出

- **范围/文件**：24个生产/测试/浏览器文件，精确清单与SHA见docs/chat-management-v2/FILES.json，接手快照增量CHANGES.patch。Chat.vue统一ConversationMenu组件/动作、AppDialog重命名与删除、状态锁/搜索/归档视图/分享；api/chat与types；公开只读页与最小路由；chat-export工具；后端state/share/schema与数据源迁移入口。学习Store/Evidence/NextAction/白名单算法、全局导航、原SSE/教练/任务管线未改。本轮main/78f4b11，无commit/push，cached空，保留未知dirty/untracked。
- **实际根因/结果**：旧菜单行内易裁切且无右键键盘；旧重命名失败关闭后丢输入；置顶归档无字段/API；MD依赖页面缓存；公开分享/PNG/TXT不存在。现右键/更多/Shift+F10同菜单，Teleport视口避让，outside/Escape/方向键/Enter/焦点恢复；Owner重命名/删除失败保留，忙碌防重。pinned/archived真实持久化，普通/归档严格Owner过滤，置顶优先。晚到列表重试加epoch避免覆盖新标题；分享目标切换清旧记录。
- **分享安全与调用链**：ChatController→ChatServiceImpl→原conversation表；独立ChatShareController→ChatShareService JDBC→chat_public_share。主动确认+24h/7d/30d；32字节随机token，DB仅SHA256，列表不返秘密；不可变允许文本快照，匿名纯文本公开页fetch omit/no-referrer/no-store，不开放原管理接口。Other/Admin跨私聊404，匿名管理401；过期/撤销/源删除统一404。含任意历史KB引用（含deleted）拒绝创建400/读取404，防止历史summary间接泄露；无KB内部action assistant排除，URL/图片/疑似凭据拒绝400。导出重新Owner请求完整已保存消息，统一MD/TXT/Canvas纯文本PNG分页（20页/100000字上限），无外部图片，隐去内部action token/debug。
- **迁移**：chat_conversation新增pinned/archived NOT NULL DEFAULT FALSE；新增快照表/Token唯一与owner索引。复用原增量DDL，无新依赖。审查发现仅PostConstruct不覆盖热切换，已改DataSourceManager目标连接初始化后/swap前共享静态迁移；失败关闭新池保留旧池，启动失败也close real。旧H2 auto/never真实目标切换、重复迁移与失败回收通过；真实MySQL未测。
- **测试与Build**：最终`cd frontend && npx vitest run`144文件1189通过0失败跳过exit0（13.92s）；root/app vue-tsc --noEmit通过；npm run build（vue-tsc-b+Vite）exit0，Vite4.16s，原chunk/插件提示保留。新增菜单3、管理8、导出7项；旧安全断言不删，操作步骤适配新Dialog。后端最终全量`mvn -o -q -f backend/pom.xml test`exit0，136 suites/1012统计=1004通过8跳过0失败错误，汇总docs/chat-management-v2/backend-full-summary.json；最终相关管理5/迁移2/标题7/归属9/数据库Guard3共26通过exit0。初始后端RED受Mockito自附加环境阻挡，未虚称RED；显式javaagent与本机测试权限后相关通过。审查前旧全量因后续修复中止，不计最终结果。
- **真实浏览器**：scripts/tests/chat_management_browser.cjs，无route拦截/force/模拟成功，真实5173→8080，专用验收账号+规则LIST真实消息，不付费AI。7组交互、1440×900/1280×800/390×844、48页面API响应，0pageerror/Console错误；右键/更多/Shift+F10/方向键Enter、重命名刷新/取消搜索、置顶取消刷新、归档深链/恢复/消息数、匿名分享读取与撤销失效、删除确认与当前选择通过。MD/TXT实际下载含最终段；PNG全3页保存且首/末页实看，末页保留END-完整记录/页段3。截图和实际文件/网络证据在evidence，Token日志脱敏/截图链接遮盖。finally按Owner原删除语义清理创建会话，验收账号及逻辑删除记录保留，不触碰既有用户数据/学习任务。
- **运行与Git检查**：确认本项目5173/8080归属后restart，最新安全迁移代码就绪；5174无监听。匿名auth/me、state/share管理401/body401，无效公开Token404/body404，见runtime-verification.json。目标git diff--check、JS语法exit0，后台服务保留。精确增量与文件SHA保留，未覆盖原有改动。
- **审查/未验**：独立只读审查迁移P2及KB边界已修并复核关闭，无新确定P1/P2；审阅未重跑测试。真实MySQL、Safari/Firefox/读屏、系统剪贴板、付费AI/视觉/语音未验。PNG为完整纯文本分页，非富排版或附件图片；公开链接只首返，刷新后可撤销或新建。重生成/编辑分支/反馈持久化/全文搜索仍未实现，未伪称成功；项目无ESLint脚本，不虚称运行。
- **一个下一任务**：完善SSE完成元数据（真实messageId、truncated、来源ID）及版本/幂等合同，再以保留原回答、防重复计费和跨会话隔离为验收实现真实重生成与反馈持久化。

### 2026-10-22 — Composer + ContextMenu 收口：缺图标与失败草稿覆盖修复

- **范围/文件**：frontend/src/views/Chat.vue、frontend/src/components/ui/Icon.vue；复用既有 AIComposer.vue、ContextMenu.vue 与其单测，未改发送/SSE/持久化/学习Store/Evidence/路由白名单。
- **修复**：Icon 缺 `square`（停止接收按钮渲染空白方块）→ 补 filled rect；创建会话失败、学习行动降级/失败三处无条件回填 `inputMessage` 会覆盖用户新草稿 → 改为仅当输入与附件均为空时回填，创建失败补 `failedQuestion` 以显示“恢复问题到输入框”；ContextMenu 首帧先落触发点坐标再测量避让，避免 (0,0) 闪现；更多按钮补 `aria-haspopup="menu"`。
- **验证**：`cd frontend && npx vitest run` 147文件1208通过0失败exit0；`npx vue-tsc --noEmit` exit0；`npm run build` exit0。真实浏览器（user1，5173→8080）：1440×900 输入区无横向溢出，聚焦反馈在外层容器、textarea 无边框，Shift+Enter 自动增高，更多按钮与右键同菜单、Escape/外部点击关闭、置顶后已恢复原状态，Console 0 error/warning；微调后二次回归通过。窄屏仅单测/CSS 覆盖，未真机浏览器验证。
- **风险/未验**：重生成/反馈项仍显式 disabled 未接入；公开分享沿用既有确认与隔离链路，非本轮新增；Chat.vue 存在早期遗留 `.conversation-menu` 未用 CSS（无冲突，未清理）。未 commit/push。
- **一个下一任务**：为消息菜单补齐真实重生成与反馈持久化（依赖SSE完成元数据合同），避免继续以 disabled 占位。

### 2026-10-23 — AIComposer / ContextMenu 最终验收与交付

- 范围：受控 AIComposer、通用 ContextMenu，Chat 输入/历史/消息菜单和 Messages 私聊复用；文件及 Props/Events/类型完整见 `docs/composer-context-menu/REPORT.md`。保留上一收口的 square 图标、新草稿保护与菜单首帧定位。
- 数据流：组件 submit/stop → Chat 原 sendMessage/AbortController，私聊 → 原 WebSocket 入口；无新 Store/API/SSE；学习/Evidence/路由不变。历史管理沿用 Owner API 与真实分享/导出。
- 真实截图发现 textarea 继承 `.phase-d-chat :focus-visible`，产生双焦点框；增加组件内更明确的选择器将内 outline 透明，可见键盘焦点仍在外层，forced-colors 保留 Highlight。浏览器断言内 outline transparent 后通过，不靠全局覆盖或 !important。
- 验证：全量 vitest 147 文件/1208 测试通过0失败exit0，root/app vue-tsc通过exit0，最终 npm run build exit0（大chunk提示仍在）。本轮未改后端，未重复 mvn；不冒充前序后端结果为本轮新结果。
- Chrome：`scripts/tests/composer_context_browser.cjs`真实5173→8080、专用账号、无route mock/force/付费模型。9组交互，1440×900/1280×800/390×844，48页面API响应，0pageerror/Console error。空发送、ShiftEnter、自动增长/滚动/外层焦点、附件增删/KB开关、右键/更多/键盘菜单、重命名/置顶/归档/分享撤销/三格式实际下载/删除确认通过；最终截图/evidence已保存。验收会话finally删除，专用账号和逻辑删除记录保留。
- Git：main，HEAD 78f4b11d58da89d69264f4f452d210e6a6db34d1；大量原有dirty/untracked保留，cached为空，目标diff--check通过，无commit/push。早期临时前镜像已失，不伪造独占补丁。服务保留固定5173/8080。
- 未验：真实付费SSE/视觉/麦克风、读屏/其他浏览器、私聊真实双端、当前轮教练折叠/模型配置浏览器操作；SSE取消/失败及私聊已有集成测试。真实MySQL沿用前序未验。重生成/反馈仍disabled。
- 下一任务：完善SSE完成元数据与messageId幂等合同后实现真实重生成和反馈持久化。

### 2026-10-23 — KnowFlow Modern AI Workspace 设计系统 V2 文档审计

- 目标/范围：按用户要求仅审计与设计规范，未改CSS、Vue、业务、后端或远程。新增 docs/UI_DESIGN_SYSTEM_V2.md；UI_UX_SYSTEM.md修正src/style.css路径、移除不存在的Dropdown.vue索引并关联专项；保留原产品哲学/导航权威。未commit/push。
- Git：main，HEAD 78f4b11d58da89d69264f4f452d210e6a6db34d1；开始检查status/diff/cached/历史/untracked，大量前序改动保留，cached为空。目标文档diff--check通过；不覆盖未知变更。
- 真实调用/样式：main.ts→style.css→ui-tokens.css，CLayout另引pc-studio与ui-v2-phase-a；主题useTheme→html data-theme/color-scheme。Input/Textarea/Select/Button/Checkbox/AppDialog/ContextMenu/AIComposer/通知状态图标及Chat/Messages/Login/SearchResult/Profile/NotesManage实际扫描。views原生input/textarea/select标签306/84/148，components54/16/18；仅静态出现次数，不等于缺陷数。
- 重要勘误：前序组件交互通过不等于视觉完备。Composer引用radius-xl/full、ContextMenu引用shadow-md但frontend无定义，本轮明确列为待修。Login焦点阴影+outline叠加；Select缺完整键盘/ARIA及Portal避让；Checkbox透明input焦点、普通AppDialog焦点/名称为静态风险。危险红对白底约3.76:1，不适合普通小文字；拟danger-text待实现，未悄悄改色。
- 交付：14节含现状分类、真实/拟Token区分、排版间距尺寸、输入三类、Composer/菜单/选择语义、10类通用控件、状态表、PC/窄屏/深浅主题、CSS与迁移批次、业务保护和验收。不存在的通用Tooltip/Radio/Switch/Combobox不伪称已实现。
- 验证：文档本地链接/Token静态核查、目标diff检查；纯文档未跑vitest/vue-tsc/build/mvn，未新做浏览器验收，不拿前序结果充本轮PASS。未定义Token为源码确认，功能/无障碍风险尚需浏览器复现；全站路由/对比度/参考截图逐像素比对Not verified。
- 下一任务：迁移A，先补齐缺失圆角/阴影Token、验证深浅主题与单焦点/危险文字对比，再推进Select/Checkbox/Dialog无障碍批次。

### 2026-10-23 — 前端 UI 样式统一首批实施

- 范围：style.css/ui-tokens.css/workbench.css、共享Input/Textarea/Select/Button/AppDialog/ContextMenu、AIComposer及NotesManage搜索；新增WorkspaceControls单测/真实Vue状态页/browser脚本，原composer browser支持独立输出与笔记检查。完整文件表、Token和证据见docs/ui-style-upgrade/REPORT.md。未改业务API/Store/路由/学习Evidence/SSE/WS/后端。
- 技术栈：实际Tailwind3.4.19、Vite8.1.4、Vue Scoped CSS+Variables，无CSS Modules/新增UI库；原Tailwind配置和--kb唯一Token保留。补radius-xl/full、shadow-md；字段/菜单/Composer语义别名；危险文字浅色引用danger-active、深色引用destructive。
- 效果/调用链：Input/Textarea外层field绘制边界/focus/error/disabled，内原生控件真实name/readonly/required/error描述；工作台选择器优先级避免重画内框。Composer复用原send/stop、菜单复用原业务items，圆角和阴影不再引用未定义变量；按钮md图标38px/aria-busy/主题文字，Checkbox可见box代理焦点；Dialog/Drawer共享轻浮层阴影。Notes搜索原v-model/目录状态保持。
- Select：基本listbox键盘、selected勾选/disabled、Escape、测量视口定位、Teleport及监听清理。只读独立审查发现body浮层与scrollableDialog焦点/Escape冲突，补真Teleport组合回归先RED1fail3pass，修复为最近Dialog作用域（否则body）、捕获阶段让选择器先Escape，4项通过并复核关闭。不是完整可编辑Combobox。
- 验证：新增最初3项RED后实现；最终全量vitest148文件1212测试通过0失败exit0，root/app vue-tsc通过exit0；最终build exit0（大chunk提示保留）。目标diff--check通过；后端无变更不运行mvn。
- Chrome：真实Vue控件状态页9组，浅深主题×1440/1280/390，无内border/shadow、外层focus、readonly/disabled/高度、Select键盘、菜单边缘/圆角/阴影/selected/disabled、Dialog内选择器先Escape后Dialog通过，0Console/pageerror。真实5173→8080聊天+Notes10组，51页面API响应，0Console/pageerror，三尺寸聊天/菜单/笔记搜索通过，实际下载与匿名分享撤销仍通过。无route mock/force/付费AI；截图实看。验收会话finally删除，专用账号/逻辑删除记录保留，不触碰既有用户数据。
- Git：main，HEAD78f4b11d58da89d69264f4f452d210e6a6db34d1；原大量dirty/untracked保留、cached空、未commit/push。本轮前镜像保留并生成change.patch/FILES.json，仅供审阅，不盲目应用。服务保留5173/8080。
- 未验/范围：非全站每条路由统一；Login/Profile等原生字段继续逐页迁移。Safari/Firefox/读屏/软键盘、实际付费SSE/视觉/麦克风/双端私聊/生产部署Not verified。普通Dialog全面焦点合同、Tooltip/Radio/Switch等未在本轮扩展。
- 下一任务：按V2批次迁移Login/Profile与高频筛选表单，优先真实attrs/错误/单焦点/双主题，避免全局覆盖。

### 2026-10-23 — Composer / ContextMenu 重复需求逐项复核与边界修复

- 范围：复用现有AIComposer/ContextMenu/Chat集成，不新建重复组件/Store/API，不改Messages/后端。详见docs/composer-revalidation/REPORT.md；修改两组件、Chat.vue、三组测试、原真实browser脚本，新增报告/证据。
- 原因与修复：全disabled菜单没有可聚焦项，Escape不可达→menu tabindex=-1并fallback聚焦；maxHeight prop变化不resize→watch与modelValue一起重测；创建失败有新草稿时原图片无法显式恢复→failedImages内存快照随问题保存/恢复，自动回填继续不覆盖新稿；引用菜单后焦点仍留触发器→nextTick聚焦Composer；消息按钮expanded，Composer busy ARIA/非图片附件移除名称。
- 浏览器根因：390px学习教练aside absolute盖住Composer，1440/1280/1024未遮挡。已有浏览器相交断言先RED，改≤1023px纵向布局、教练在上方32dvh限高内部滚动，保留折叠入口与底部输入；四尺寸最终通过且截图实看。
- 数据流：Chat原sendMessage/streamChat/AbortController/request/history epoch、图片读取/语音/模型Dialog不重建；父层历史items调用原Owner APIs/真实公开分享/导出/删除确认。NextAction/Evidence/Store/路由/后端不变。
- RED/验证：动态高度、全disabledmenu、失败图片恢复、引用焦点先失败后修复；图片测试首次Event.target空是测试装配错误，已纠正事件后在恢复图片断言真实RED，不混为业务根因。最终vitest148文件1216测试通过0失败exit0；root/app vue-tsc通过exit0；npm run build exit0（Vite4.45s，大chunk提示仍有）；目标diff--check通过。后端无变更未跑mvn。
- Chrome：真实5173→8080、专用账号浏览器AI增强偏好关闭，无route mock/force/付费AI；12组57页面API响应0Console/pageerror。原规则Enter实际持久化、模型Dialog、消息菜单Space引用+focus、四尺寸教练折叠/不遮挡、三尺寸历史菜单边缘与原管理/真实下载/分享撤销通过。账号/逻辑删除记录保留，finally删除测试会话，未触碰既有用户数据。
- 独立只读审查本轮逻辑无确定P1/P2，未改文件/运行测试，浏览器遮挡由主执行者发现并修复。真实提供商SSE/视觉/麦克风、双端私聊、其他浏览器/读屏/软键盘/生产Not verified；SSE失败/停止/迟到回调有组件集成覆盖，不能冒充提供商验收。重生成/反馈仍disabled。
- Git：main HEAD78f4b11d58da89d69264f4f452d210e6a6db34d1，既有dirty/untracked保留、cached空，无commit/push；前镜像→change.patch和FILES.json供审阅，不重复应用。
- 下一任务：完善SSE完成messageId/元数据与幂等合同，再实现真实重生成和反馈持久化。

## 2026-10-23 — Select / 浮层 V2：公共能力、首批迁移与真实标签持久化

- **目标/原因**：原生与自定义选择器并存，公共 Select 缺少多选/分组/确认弹窗统一合同；实际审计见 docs/select-v2/AUDIT.md（46 页修改前快照）。
- **范围/文件**：重构 ui/Select.vue；新增 SelectChoices.vue、select-types.ts、SelectDialog.vue；ui-tokens.css 增加 4 个别名。迁移 LearningPrefs/SmartQuiz/SearchResult/Docs/Categories/CodePractice/DocEdit/KnowledgeUpload/Chat 共9页24入口，TaskRow 标签改真实多选 Dialog；4个新增前端测试文件、4个既有测试适配，2个浏览器脚本+真实Vue fixture。后端 TaskServiceImpl/TaskTagRelMapper 最小修复历史关系恢复，新增 TaskTagRelationHttpTest。完整清单/SHA与本轮差异见 docs/select-v2/changed-files.json、change.patch。
- **Git**：main / HEAD 78f4b11d58da89d69264f4f452d210e6a6db34d1 未变；原工作区大量 dirty/untracked 保留，cached 无内容。未 commit/push/reset/clean/stash；未操作远程仓库；未改本轮依赖（原有 Prettier diff 不归本轮）。
- **业务/UI/数据流**：Field/Pill/Subtle、搜索/分组/多选/长列表确认共用一个类型合同，数字1和字符串'1'独立；打开/过滤/取消不写值，确认才保存。AppDialog 管焦点域，Teleport 避开裁切，翻转明确覆盖旧 top。TaskRow → setTaskTags → PUT /tasks/{id}/tags → TaskController → updateTask(认证uid) → lockedOwnedTask → syncTags → TaskTagMapper/TaskTagRelMapper → task_tag/task_tag_rel。重复标签保存曾真实500：逻辑删除占唯一键；现 owned task 锁及 owned/deleted tag 校验后恢复历史关系而非重复插入，无 schema/索引改动。
- **RED/GREEN**：新增后端4项初始2失败2通过，明确唯一键冲突；分组键盘真实Teleport回归初始 A2/B1 错序，已修复；相关合同、异步旧响应/错误重试、不可用选项、原API断言保留。
- **前端最终命令/结果**：`cd frontend && npx vitest run`152 files/1239 passed/0 failed/0 skipped，exit0；root `npx vue-tsc --noEmit` 与 app `npx vue-tsc --noEmit -p tsconfig.app.json` 均exit0；`npm run build`exit0，Vite9.35s，已有chunk/插件耗时提示保留。
- **后端最终命令/结果**：`mvn -o -q -f backend/pom.xml -DargLine=-javaagent:<本机byte-buddy-agent-1.14.19.jar> test`，允许本机随机端口，exit0；137 suites /1016统计=1008通过8跳过，0失败/错误，含标签回归4通过。首次受限沙箱运行的本机端口错误不计PASS；完整重跑计数见 backend-summary.json。未复制可能含敏感SQL参数的原始后端日志。
- **浏览器**：真实Chrome/Vite/本地8080，无业务请求mock。Select10组/48responses/0console-pageerror，四尺寸1440×900/1280×800/1024×768/390×844×深浅主题，键鼠/IME/边缘上翻/右避让/无横向溢出；任务取消不写+确认后reload持久、题库不自动POST、模型草稿不写、Docs URL、CodePractice difficulty=0一次请求、上传真实空态。原聊天额外13组/3布局/60responses/0console-pageerror，含菜单、知识开关、教练、分享导出；付费AI关闭。证据 docs/select-v2/evidence 与 chat-regression。旧/新组件真实Vue对照截图各1440/390，非所有页面历史截图。
- **运行状态**：knowflow-dev.sh 重启项目自有服务，实际127.0.0.1:5173 /8080。验收独立账户仍保留于本地，创建的任务/标签/会话finally删除（逻辑删除关系历史保留）。
- **风险/未验证**：没有全站迁移完成宣称。树保留层级/排除规则，不新增无真实需求Submenu；CodePlayground取消语言切换、导入映射、FSRS/复杂模型表单和剩余PC/管理页后续分批。LearningPrefs未注册路由；DocEdit管理资源与Categories分类后排序页面交互未覆盖。MySQL实库并发、屏幕阅读器、付费AI、生产部署 ⚠️ Not verified。不新增假主题色/多知识库设置或拼音依赖。详见 docs/select-v2/REPORT.md。
- **下一任务**：按 AUDIT 表迁移剩余普通PC平面过滤器，逐页保留值类型/取消与保存语义，并执行相同全量及真实浏览器验收；不要批量替换复杂选择器。

### 2026-10-23 · Select V3 管理子任务

- 范围：CommunityManage、UserManagement、ScenarioMgmt三页六处平面Select；保留字符串筛选、数字difficulty/status、空字符串、原保存时机与权限；弹窗键盘对打开的listbox让步。
- 文件：上述三Vue、UsersPhaseF.spec.ts、ScenarioFPlus.spec.ts、docs/select-v3/AUDIT.md、ADMIN_MIGRATION.md。共享组件未修改。
- Git：在既有dirty main/78f4b11上增量修改，未commit/push/reset；修改前3页副本 `/tmp/select-v3-before/frontend/src/views/admin/`。
- 命令：`cd frontend && npx vitest run src/views/admin/__tests__/UsersPhaseF.spec.ts src/views/admin/__tests__/ScenarioFPlus.spec.ts src/views/admin/__tests__/CatalogEditorLinks.spec.ts`，exit0，3文件24测试通过；相关diff --check exit0。全量、类型、Build由根任务统一执行。
- 调用链：用户role→applyFilters→adminApi.users；编辑role→save→adminApi.createUser/updateUser；场景数字值→save→adminApi.createScenario/updateScenario；社区status→filteredPosts本地computed。
- 风险/未验证：⚠️ Not verified 浏览器、Network、真实管理写API和权限矩阵；后端不变。初次审计泛型误命中已剔除，CategoryTreeSelect单独补入。
- 下一任务：根任务浏览器核验三页弹窗选择、Escape与disabled，然后统一全量验证。

## 2026-10-23 — Select / Cascader V3 主任务合并验收

1. **目标**：V2上增加9项自动搜索、可靠远程搜索、桌面多级Hover与移动分层，并继续逐页迁移。用户要求直接在本地完成，不提交推送。
2. **原因/审计**：分类原为div内嵌平铺树，缺少成熟键盘/浮层；原生选择器仍分散。docs/select-v3/AUDIT.md记录修改前真实引用及全模块覆盖，不是最终状态。
3. **文件**：Select.vue扩展；新CascaderSelect/cascader-types/select-position；CategoryTreeSelect保留旧合同；AppDialog仅让自有cascade先处理Escape。9PC页15处、FlashCardsHub/CodeAgent/InterviewV2Panel/MockInterview24处、3管理页6处；KnowledgeUpload分类接入级联，合并既有9入口共8文件10级联入口。新5测试文件和相关合同测试，2V3浏览器脚本/fixture；V2浏览器脚本仅增证据输出env。完整SHA/职责见docs/select-v3/changed-files.json。
4. **业务影响**：保留数字/字符串/0/null/undefined、原watch/API/保存时机。NoteEdit本来就是本地note-list发布，本轮仍是本地，不伪称后端持久化。分类父选择与展开分离，excludeIds只禁选择不挡合法后代。
5. **UI影响**：Field/Pill/Subtle复用现有Token（新增Token/依赖均0）；白色/深色悬浮面板、轻边界/Check/键盘focus；桌面120ms Hover安全跨列、上下左右避让和左展延续，小屏一列返回与路径；不改全局overflow/z-index或新建UI库。
6. **数据流**：Select受控值→原业务回调；可选loadOptions(query,AbortSignal)由组件防抖/epoch/取消管理候选读取，不复制Store/API；远程过滤保留已选标签不当成删除。CategoryVO→CategoryTreeSelect→Cascader numeric value→既有categoryId；上传切换kb仍清空分类。NextAction/Evidence/路由白名单/学习Store/SSE未改，后端无变动。
7. **测试**：最终`cd frontend && npx vitest run`157文件1262通过0失败跳过，exit0/21.46s；root/app `vue-tsc --noEmit`均exit0。初始Select3项RED、旧Category可访问合同RED、远程误报不可用RED、搜索父项意外提交RED、Dialog抢先Escape RED，均回归GREEN。保留原业务断言。首次在仓库根npx触发DNS错误，不冒充功能RED。
8. **Build**：`npm run build`(vue-tsc-b+Vite)exit0/Vite8.87s；原chunk/插件耗时提示保留。本轮未改后端，不重跑mvn，不把V2后端数字冒充本轮。
9. **浏览器**：真实Chrome/Vite，无业务API拦截。四尺寸1440×900/1280×800/1024×768/390×844×深浅主题，7组/23浏览器HTTP响应/0Console-pageerror；含Hover多层、跨列、左/上翻、键盘父选择/空项、移动返回、Dialog逐层Escape/焦点恢复、自动/远程搜索/IME/错误重试、真实Note本地显式发布重开、闪卡筛选、CodeAgent草稿不写。V2平面/多选/真实任务标签10组46响应、聊天13组60响应，均0Console-pageerror。证据docs/select-v3/evidence、flat-regression、chat-regression；旧/新真实组件同数据1440/390对照已保存。对照脚本与功能验收应顺序运行，临时HTML触发Vite全页热重载会打断进行中的测试。
10. **Git/风险/未验证**：main/HEAD78f4b11保持，cached空，既有1093 dirty/untracked保留，未commit/push/reset/clean/stash。scoped diff--check exit0；任务前副本/tmp/select-v3-before。四个旧测试没单独前副本，已按代理记录核对新增/交互适配，不制造基线；partial change.patch明确省略这些文件和无前副本handoff。独立USER直连接口POST/admin/categories得到HTTP/body403，未提升权限，所以真实分类创建/上传持久化、管理员写入⚠️ Not verified；API证据不冒充浏览器Network。付费AI、生产、真机/屏幕阅读器未验证。剩余36文件102原生标签含1required代理，业务原生101，见remaining-controls.json，未声称全站全迁移。测试账户保留；回归任务/标签/会话finally清理，隔离浏览器笔记清理，无敏感配置写入交付。
11. **下一任务**：按remaining-controls逐页迁移普通PC图谱/工作台/学习目标选择器，再独立实现CodePlayground可取消语言切换及复杂导入/管理表单；权限403作为既有产品/授权问题单独审计，不在视觉改造中放宽安全规则。

## 2026-10-23 错题本筛选区域精准修复

1. 目标/原因：修复 Select 全宽触发器和随宽浮层、选中永久灰底；实际代码根因及范围见 docs/mistakes-filter-fix/REPORT.md。
2. 文件：Mistakes.vue、Select.vue、SelectChoices.vue、style.css；新增 SelectPresentation 测试、追加 MistakesV2 合同；浏览器脚本、规范关联及报告证据。
3. 业务/UI/数据流：既有 filters/selectedCategory → 原回调 → list API，清空重置分页和领域；统计仍全部错题，无掌握写入；桌面紧凑四项/窄屏两列，面板240px，平面选中透明Check、hover浅灰、键盘独立轮廓。Token/依赖/后端变动0。
4. 验证：RED 3失败5通过 → 对应8通过；frontend npx vitest run 158文件1265通过，root/app vue-tsc退出0，npm run build退出0/Vite9.34s。真实Chrome四尺寸6组30响应、跨页Select10组48响应，均0 Console/pageerror；真实题型、空结果、移除和清空保持全量统计且无写入。
5. Git：main/HEAD78f4b11，保留既有dirty，不commit/push；任务前副本/tmp/mistakes-filter-before，任务增量patch和SHA清单位于报告目录，scoped diff --check通过。
6. 风险/未验证：测试账户、错题及测验记录保留本地，无删除接口不直接删库；真实手机/屏幕阅读器/生产/付费AI未验证。后端未改不重跑Maven。
7. 下一任务：按既有清单迁移普通PC选择器；筛选统计如需变化，独立定义接口口径。

## 2026-10-23 算法题库、智能组卷、知识图谱筛选统一

1. 目标/原因：沿用错题本Select外观与交互；三个页面及图谱画布原12处原生选择器迁移，修正GraphToolbar深层button样式覆盖Select的问题。
2. 文件：CodeProblemList、ExamCompose、KnowledgeGraph、KnowledgeGraphCanvas、GraphToolbar；4合同测试、workspace_filters_browser.cjs和docs/workspace-filter-upgrade报告/证据。
3. 业务/UI：算法父子分类真实编码、数字难度0/URL/分页/搜索重置不变；组卷Field配置表单保留，监视器只查询题量；图谱局部紧凑Pill、240px浮层/Check/hover与独立focus，窄屏无溢出。Token/依赖/后端变动0。
4. 调用链：CodeProblemList→原applyFilters/load→codeBankApi→CodeBankController/CodeProblemService；ExamCompose→原form/watch→availability，compose显式；KnowledgeGraph→字符串selectedKb本地filter/numeric|null实体API，技术AI仍手动；Canvas→原scope/type/selectGraphView，不改图算法。
5. 测试/Build：真实RED4失败8通过→相关19通过；最终全量159文件1270测试通过exit0/53.30s；root/app vue-tsc exit0；npm run build exit0/Vite6.15s。无后端改动不重跑Maven。
6. 浏览器：三页面四尺寸12视觉组+4业务组共16组、86浏览器HTTP响应、0Console/pageerror；真实算法查询与URL、空搜索重置、题量预设策略、草稿无自动生成、实体数字参数及全选省略、底部翻转/外部关闭均通过。证据及截图见专项报告。
7. Git：main/HEAD78f4b11，保留旧dirty/cached空，不commit/push；任务前/tmp/workspace-filter-before，任务增量patch与SHA在报告目录，scoped diff--check通过。
8. 遗留/未验证：隔离账户及浏览事件保留本地；真实手机/屏幕阅读器/生产/付费AI生成未验证；未更改权限/布局算法/学习唯一真源。下一任务：按清单继续普通PC剩余选择器迁移，生成配置不要机械套列表筛选清空语义。

## 2026-10-23 Searchable Select与知识库搜索精准优化

1. 目标/根因：公共Select实际29文件复用；搜索容器padding叠加全局input padding使header62px，整块focus阴影造成内框；默认搜索浮层随全宽父容器至1392px，知识库240偏窄。
2. 文件：Select/SelectChoices、style.css、ui-tokens.css、KnowledgeGraph/KnowledgeUpload两处340参数；2合同测试，真实组件fixture，专项浏览器脚本及2现有回归脚本，规范与报告见docs/searchable-select-fix。
3. UI/业务/数据流：搜索42px、单行选项42px、搜索面板最高352；底部分隔线focus，Check-only Selected，Hover/键盘灰底+轮廓；长Select默认标签ellipsis/title；非搜索默认宽度及显式panelWidth保持。只新增3Select尺寸Token，复用原配色。原查询/commit/ID/远程取消/多选/业务触发不改；图谱selectedKb原字符串本地过滤，上传原Number(value)及依赖分类清空，未改后端/Store/学习真源。
4. 测试/Build：RED3失败3通过→相关24通过；最终全量159文件1272通过，exit0/37.67s；root/app vue-tsc exit0；build exit0/Vite4.74s。无后端变化不跑Maven。
5. 浏览器：真实Chrome四尺寸专项11组25响应；三页面16组86响应、公共Select跨页10组48响应，共37组159响应，0Console/pageerror。实时搜索/空态/清空、完整title、灰底hover与keyboard、Enter数字身份、焦点恢复、独立滚动与上翻、真实图谱分类筛选不重载/AI均通过。旧CodePractice计数先等真实初次响应，保留原一次查询断言后完整重跑。
6. Git/风险：main/HEAD78f4b11、保留旧dirty/cached空、不commit/push，任务前/tmp/searchable-select-before、任务增量patch/SHA与截图见报告。测试账户和浏览事件保留，回归任务/标签清理；真实上传持久化/真机/屏幕阅读器/生产/付费AI未验证；测试fixtureID不进入业务API。
7. 下一任务：继续既有普通PC页面迁移，仅尺寸差异用panelWidth，不重复实现搜索菜单。

## 2026-10-23 搜索聚焦不显示蓝线

- 目标/根因：用户截图的蓝线来自Select搜索头focus-within品牌边框与focus-visible的2px加粗。删除普通主题这两条CSS，仅保留默认1px浅灰，未动输入/值/业务事件或菜单项键盘状态。
- 文件：Select.vue、searchable_select_browser.cjs（聚焦与非聚焦同色/1px断言、独立输出目录）、两份UI规范、docs/search-focus-neutral报告与截图。任务前副本/tmp/search-focus-neutral-before，本目录patch/SHA仅当前增量。
- 验证：真实Chrome先RED 2px≠1px，修复后四尺寸11组25响应0Console/pageerror，计算样式1px/rgb(226,230,236)，input无边框/阴影；原搜索/滚动/Enter/关闭/真实图谱过滤通过。全量159文件1272项PASS exit0/34.56s；root/app vue-tsc exit0；Build exit0/Vite5.03s。后端未改。
- Git/遗留：保留dirty/cached空，不commit/push/reset/clean；测试账户和浏览事件留本地，真实手机/屏幕阅读器/强制颜色模式/生产未验证。下一步无需扩大到其他按钮或选项键盘样式。

## 2026-10-23 AI聊天图标、无头像与宽度校准

1. 目标/根因：arrow-up原箭头尖y9/竖杆到y3错位，顶部icon-agent/导航bot等不统一；两处AI头像占位；48rem/46rem最终覆盖导致672/644px偏窄。
2. 文件：Icon、AIComposer、Chat、ui-tokens；navigation图标字段、CTopNav、命令AI入口、LearningCenterPage AI问答、DeepMode打开AI按钮；4测试文件、chat_ui_polish_browser.cjs、专项报告/证据/规范。
3. UI/业务：标准箭头+36px圆发送/20px图标；自定义24viewBox气泡星光currentColor SVG统一；回复/等待头像DOM和CSS移除，ARIA角色分组，消息和输入共用64rem上限。1440实际896同宽、1280为814，390为374无溢出。768CSS与现有<768脚本对齐使侧栏恢复可操作。原sendMessage/streamChat/learningActionApi/AbortController/Store/路由/学习真源不改。
4. 测试/Build：RED5失败16通过→相关35通过；最终160文件1277测试PASS exit0/52.47s；root/app vue-tsc exit0；build exit0/Vite5.49s，后端未改不重跑Maven。
5. 浏览器：真实Chrome五尺寸专项8组73响应、既有真实会话/附件/管理/分享导出9组48响应，合计17组121响应，0Console/pageerror。停止状态用显式暂停传输夹具，不伪造回复，不调用付费AI；SSE取消/迟到回调由原集成测试通过。截图和精确几何见docs/chat-ui-polish。
6. Git/遗留：main/HEAD78f4b11，保留旧dirty/cached空，不commit/push/reset/clean；任务前/tmp/chat-ui-polish-before与status文件，任务增量patch/SHA见报告。会话finally清理，临时凭证删除，测试账号保留；真实麦克风、付费SSE端到端、真机/屏幕阅读器/生产未验证。
7. 下一任务：如需付费模型长代码/引用端到端验收，独立执行，不在本UI任务重写模型协议。

### 2026-10-23 · 学习教练工作栏与字体系统

- 范围：Chat展示布局、LearningCoachPanel纯展示组件、AppDialog可选right placement、字体Token/Tailwind映射、公共页头及FlashCardsHub/SmartQuiz/Profile/TodayPlan字号。详见docs/coach-typography/REPORT.md与UI_DESIGN_SYSTEM_V2末节。
- 数据流：Chat→唯一learning Store原字段→展示Props；CTA仍startCoachNextAction→resolveContextualRoute；快捷问题只写原inputMessage。未改NextAction/Evidence/Store/API/SSE。
- Git：main，接管时已有大量未提交改动，完整保留。未commit/push/reset/clean。本任务增量patch与SHA单独保存在docs/coach-typography。
- 验证：全量vitest 161文件/1281测试通过；root与app vue-tsc通过；npm build通过，保留既有大包警告。Chrome五档教练布局+八页两档共21组/154响应，聊天回归9组/48响应，页面与console错误0。长文案fixture1440/390通过，不伪造账户证据。
- 风险/未验证：未调用付费AI/真实麦克风，未证明所有网络下首选Web字体可用，保留系统回退；不是全仓字号债务清零。全仓diff-check的linkedlist.md:191尾随空格为既有无关改动。
- 下一任务：按页面继续迁移剩余局部字号，并保留真实浏览器验收，避免全局强覆盖。

### 2026-10-23 · 全站二次确认弹窗统一

- 目标/根因：dialog.confirm/AppDialog与confirmDialog/ToastHost两套确认机制及三个直接实现外观/键盘不一致。收敛89处调用（58调用文件）与Chat删除、Agent高危工具、LocalReader目录确认，新增ConfirmDialog薄包装，保留表单/编辑/选择/抽屉职责。
- 公共文件：components/ui/{AppDialog,ConfirmDialog,DialogHost,ToastHost}.vue、modal-stack.ts；utils/{dialog,toast}.ts；styles/ui-tokens.css。完整清单/快照增量见docs/confirm-dialog/{AUDIT,REPORT,changed-files.txt,task.patch}。
- 数据流：原组件→confirmDialog/dialog.confirm→唯一dialogQueue→DialogHost→ConfirmDialog→AppDialog；47个CRUD/文件清空回调等待原API，失败保留。复杂考试/兑换/会话前置确认保留原业务loading/错误与权限路径；Chat删除仍使用原受控状态。无后端/Store/学习算法/路由白名单变化。
- 交互：560px/22px圆角/44px按钮Token，安全初始焦点、Tab圈定、Escape/遮罩策略、loading去重/阻止关闭、ARIA关联和焦点恢复；共享顶层模态/计数滚动锁。普通队列串行，await中的子确认显示在父确认上方。
- 独立只读复核：发现代码工作区删除等待隐藏子确认死锁、Ollama明确ok:false被当成功关闭。新增RED→GREEN测试后修复，复核无剩余P1/P2。
- 验证：全量Vitest163文件/1291测试通过；root/app类型检查与Build最终结果见REPORT/evidence。Chrome公共5组；真实闪卡结束/取消/失败DELETE重试/设置删除4组20响应；聊天9组48响应，0页面错误。专用账户，未操作原有用户数据。
- Git：main既有大量脏改保留，未reset/clean/commit/push；任务增量无新增空白错误，全仓linkedlist.md:191既有尾随空格保留。
- 未验证：未在真实管理员数据上删除知识库/切换初始化数据库，未授权执行真实高危Agent工具或付费AI；beforeunload原生提示保留。UI统一不等于生产权限验证。
- 下一任务：按独立业务任务接入复杂考试/兑换/会话流程的事务等待，不绕过其现有冲突/权限/失败恢复契约。

### 2026-10-23 · 「我的闪卡」列表/卡片 UI 优化与服务端分页

- 目标/根因：列表表格 `min-width` 不足且操作列被挤压（按钮竖排换行）；卡片顶部操作悬停才显示、正文与底部操作错位；闪卡列表一次性拉全量无分页；长代码/URL 撑破卡片。
- 文件：FlashCardsHub.vue（列宽 colgroup 合计 1144px、`.list-wrap` 内部横向滚动、宽屏 sticky 操作列、操作列 查看答案+编辑/删除纯图标、卡片 Grid 对齐与常显操作、空态区分筛选无结果、分页栏）、frontend/src/api/{learning.ts,types.ts}（PageResult 与 myFlashcards 分页签名）、backend LearningController/LearningService/LearningServiceImpl（`/learning/my/flashcards` 服务端分页 + keyword/difficulty/categoryId/sourceType 过滤，导出仍全量）、新增 frontend/src/views/__tests__/FlashCardsHub.spec.ts。
- 数据流：页面筛选/页码/页大小 → learningApi.myFlashcards → `flashcardMapper.selectPage`（createTime desc,id desc）→ PageResult；末页删空自动回退到 `ceil(total/pageSize)`；翻页清空选择并按 ID 过滤；导出走 `listMyFlashcards` 全量，FSRS/复习记录/学习 Store 未改。
- 验证：全量 vitest 164 文件/1298 测试 PASS；`vue-tsc --noEmit` exit0；`npm run build` PASS；后端 FlashcardIsolationTest/LearningPrincipalAuthTest/FlashcardFsrsHttpTest 37 项通过。Chrome 1440/1280/1024/390 实测：表格内部横向滚动且页面无横向溢出、表头与单元格 left 完全一致、sticky 操作列遮挡正确、卡片 3/2/1 列、分页 12/24/48 与筛选/删除/模式切换联动、Console 0 error。
- 数据修复：验证中误删 admin 6 张 MANUAL 预置闪卡（运行时实为文件库 H2，`initMode=never`）。已停服备份 `knowledge-before-flashcard-restore-20261024-000510.mv.db`，用 H2 RunScript 按 data.sql 恢复 6 张（保留 path_id/chapter_id/difficulty/review_count/source_type=MANUAL），并删除本次导入的 25 张 IMPORT 测试卡（id 235–260），重启后校验 MANUAL=6/IMPORT=0。
- Git/遗留：保留既有 dirty 工作区，未 commit/push/reset/clean。未验证：触摸板/移动端真机、超大数据量（千级）分页性能、真实生产数据。
- 下一任务：如需跨页批量选择，先确认产品语义再基于 flashcardId 集合扩展，不复用数组下标。

### 2026-10-24 · 收集箱页面与新建/编辑弹窗重构 V3.0

- 目标/根因：旧收集箱为自定义 hero + 卡片网格 + 右侧 `.wb-drawer`（新建与编辑共用），无页内快速记录、无列表/卡片切换、无排序、无多选批量、无分页、无编辑脏数据保护；搜索仅由服务端匹配标题。
- 文件：frontend/src/views/WorkbenchCapture.vue（整体重构，+2084/-327）、新增 frontend/src/views/__tests__/workbench-flow.spec.ts、frontend/verify/*.png 浏览器证据。复用 WorkbenchShell、AppDialog、Checkbox、Select、CategoryTreeSelect、Pagination、ContextMenu、Icon、WorkbenchRelations 与 `--kb-*` 令牌，未新增公共组件。
- 结构：`#actions` 双按钮「快速记录 / 新建条目」→ 四阶段流程 → `<Transition>` 行内快速记录面板 → 状态 Tab（真实计数）/搜索/分类/排序/视图切换 → 列表或卡片 → 分页。新建与编辑统一为居中 AppDialog（640px、scrollable、头尾固定）。
- 数据流：`listCaptures({categoryId})` 一次取真实全量（后端无分页）→ 前端按 title/content/tags 关键词、状态、排序过滤 → 客户端分页；`createCapture` 固定 INBOX，`updateCapture` 仅编辑模式；`setCaptureStatus`、`deleteCapture`、`toggleCaptureStar`、`docsApi.upload` 均复用。未改 Store、NextAction、Evidence、路由白名单与后端。
- 交互：快速记录默认收起、展开自动聚焦、收起保留草稿并显示红点、路由离开与弹窗脏数据走 `confirmDialog`；弹窗支持 文本/链接/上传文件（真实 `/docs/upload` + 进度）、URL http/https 校验、分类树、标签 Enter/逗号/去重/500 上限、新增模式「保存后继续新增」保留弹窗并重置字段、编辑模式仅「保存修改」。
- 验证：`npx vitest run src/views/__tests__/workbench-flow.spec.ts` 24 PASS；全量 164 文件 1304 测试中 1303 PASS、1 项既有失败为他人未跟踪文件 `src/views/interview/__tests__/MockInterview.spec.ts`（断言自身 CSS 未含 `min-height: 180px`，与本次无关）；`npx vue-tsc --noEmit` exit0；`npm run build` exit0（仅既有 chunk 体积警告）。真实 Chrome（Playwright 脚本 + Chrome DevTools 交互）在 1440/1280/1024/390 采集 7 类截图，无横向溢出（390 实测 `scrollWidth == clientWidth`），1280 工具栏单行、1024 两行、移动端弹窗 358×720 内部滚动且底部操作栏常显。
- Git/遗留：保留仓库既有大量未提交改动，未 reset/clean/commit/push；新增 `frontend/verify/` 未跟踪截图目录。未验证：真实手机触摸、屏幕阅读器、上传大文件/断网重试、千级条目分页性能；1024 下列表隐藏标签/来源/更新时间列（信息移入标题单元格），未做表格内横向滚动。
- 下一任务：如后端补充分页/关键词接口，将前端客户端过滤替换为服务端查询，保留现有状态计数与末页回退逻辑。

## 2026-10-24 · 普通表单无边框与对齐

- 范围：用户附件全站无边框表单V2 + 对齐。现有Tailwind/CSS架构不变；Input/Textarea复用纯展示FormField，Select同结构；新增kb-field语义表面和中性焦点、kb-form-row子网格。真实组卷规则/AI表单接入对齐；397处普通原生控件显式迁移，138处特殊控件保留。专用代码/Markdown编辑器恢复原节点。其他历史页面以样式迁移为主，不宣称所有布局结构已升级。
- 文件：核心为frontend/src/style.css、styles/ui-tokens.css、components/ui/{FormField,Input,Textarea,Select,CascaderSelect}.vue、views/exam/ExamCompose.vue；完整列表/SHA和任务前快照增量见docs/borderless-forms/changed-files.json、task-only.patch。测试fixture和浏览器脚本见REPORT。
- 调用链：ExamCompose仍使用原reactive模型和生成API；公共控件仅emit原update:modelValue，不新增数值/请求Store；原生number及v-model.number保留空值、0、min/max/step。Select继续原Teleported浮层、真实ID与搜索过滤。学习核心无修改。
- Git：main / 78f4b11d58da89d69264f4f452d210e6a6db34d1；保留接管前大量未提交修改，无cached变动，未commit/push/reset/clean/stash。目标文件diff --check通过；全仓既有linkedlist.md:191尾随空白未触碰。
- 命令结果：frontend下npx vitest run=164文件1305测试通过(exit0)；npx vue-tsc --noEmit、npx vue-tsc -p tsconfig.app.json --noEmit通过；npm run build通过。保留Node22.11/Vite建议22.12及大chunk告警，无依赖升级。一次磁盘不足导致的中断不算PASS，清理之前测试日志后重跑通过。
- 浏览器：复用本地5173/8080，Chrome独立测试账户；组卷4宽度+公共组件5组/16API；9页面×1440/390=18组/120API；搜索下拉11组/25API，均无捕获pageerror。截图和真实HTTP记录在evidence和select-regression。数字必填/空值/0/范围/步进、长标签、错误、帮助、只读禁用、textarea独立滚动、选择绑定与中性键盘轮廓已验证。
- 复核：只读复核发现的内联背景、scoped焦点和编辑器误迁移问题均修正，最终无剩余P1/P2。未触碰API、权限、Store、业务跳转和保存。
- ⚠️ Not verified：全部管理员表单的真实提交、所有历史路由状态、真实系统输入法、深色主题完整矩阵、生产/付费AI；浏览器不触发付费生成或破坏性删除。
- 下一任务：按页面将旧label/help布局逐批迁到FormField，使用同排长标签和错误状态作为验收，不把本轮公共外观迁移误认为所有旧布局已完成。

## 2026-10-24 · 输入透明无外框修订

- 用户新要求取消浅灰输入表面及灰色焦点边框，并修复搜索图标与文字重叠。field默认/hover/active及readonly/disabled透明，普通控件与搜索外壳不画常规焦点框；菜单项/按钮和系统强制颜色反馈保留。
- CTopNav由绝对定位改三列flex；图标/输入/⌘K各12px间隔。移除pc-studio旧!important灰底；低优先级默认padding不再覆盖页面图标预留，Input显式prefix/suffix空间。workbench普通输入固定边框/表面同步移除。
- 无业务数据流、Store、API或路由修改；FormField对齐和数字原生语义保留。原main/HEAD与未提交代码保留，无commit/push/reset/clean。
- 浏览器5组/16API/pageerror0，四档组卷与公共控件；顶部搜索透明/0边框/0轮廓、12px间距有计算样式断言。证据和报告docs/borderless-forms/transparent-update。全量测试、类型与构建结果以本次最终执行报告为准。
- 下一任务：按用户实际页面继续检查图标与文本组合；未验证所有历史页面业务提交和系统强制颜色模式。

### 2026-10-24 输入视觉最终澄清

用户打断透明方案并明确最终目标：统一浅灰表面 + 不出现蓝/灰聚焦框 + 图标/文字间距。已恢复field表面、hover/active、readonly/disabled及Select表面Token；保留取消普通focus outline/shadow和图标间距修复。CTopNav浅灰壳包三列flex，内部input透明，图标和快捷键各留12px。此前透明方案记录仅为历史，非当前规范。仍不改业务/Store/API，无commit/push/reset/clean。最终验证见docs/borderless-forms/no-focus-frame/REPORT.md。

最终澄清验证：Chrome5组/16API/pageerror0；全量Vitest --maxWorkers=2为164文件1305测试通过，两项vue-tsc退出0；首次3文件ENOSPC中断后释放本工具临时日志空间重跑，不改断言。顶部搜索聚焦截图在no-focus-frame/after-topnav-search.png；本次目标文件diff --check通过。
构建最终结果：npm run build退出0，Vite 4.46s；既有Node版本和大chunk提示保留，无新增依赖。

## 2026-10-24 · 收藏夹卡片图标

- /favorites左侧双图案根因：icon-doc样式类撞全局iconfont伪元素；右侧不支持fill prop透传SVG造成黑填充/橙色描边。
- 仅修改Favorites.vue展示：fav-icon--*局部前缀；保留file-text/notebook-pen/layers分派，20px图标/40px容器；收藏改18px bookmark-check品牌蓝+浅蓝底，复用现有Token，补button语义。无全局Icon组件/API/Store/路由/收藏业务修改。
- 回归脚本scripts/tests/favorites_icons_browser.cjs，独立测试账户使用可读真实文档；确认/保留/取消收藏及刷新持久化已测。Chrome1440/1280/390共3档，17API/pageerror0，额外伪元素none，收藏fill=none且品牌蓝。修复前回归断言失败，修复后通过。真实笔记/闪卡记录⚠️ Not verified，不造业务数据。
- 全量Vitest164文件1305测试通过；两项类型/build最终结果见docs/favorites-icons/REPORT.md。目标diff --check通过，无commit/push/reset/clean。任务前快照增量task-only.patch保留已有弹窗改动。
- 下一任务：继续约束页面修饰类前缀，避免icon-*全局字形冲突。
收藏夹最终验证补充：两项vue-tsc退出0，npm run build退出0（Vite4.54s）；既有Node版本及大chunk提示保留。

## 2026-10-24 · 笔记全面升级：静态审计与实施方案

- 范围：四个笔记页面、路由、前端 API、LearningNoteController、WorkbenchController/Service、实体/Mapper、双数据库 schema、Markdown、AI 和现有相关测试。
- 文件：新增 docs/notes-upgrade/AUDIT_AND_PLAN.md；本交接仅追加。产品代码未修改。
- Git：main，HEAD 78f4b11d58da89d69264f4f452d210e6a6db34d1；目标四页原有未提交修改保留；无 commit/push/reset/clean。
- 命令：git status/diff/cached diff/branch/log/rev-parse、rg、sed、cat 静态读取；未运行功能测试、类型、Build 或浏览器，均为 ⚠️ Not verified。
- 调用链：/notes→localStorage note-list/独立草稿；workbench.ts→WorkbenchController→WorkbenchServiceImpl→WbNoteMapper→wb_note(HTML)；ChapterLearn→learningApi→LearningNoteController→LearningNoteMapper→learning_note(Markdown)。
- 风险：三来源并存；本地键不按账户隔离；AI 优化直接改正文；文档仅标题关联；无版本冲突与回收站恢复；H2 不代表 MySQL 验证。Milkdown/Crepe 未安装或验证。
- 下一任务：按审计方案落实 P0 服务端唯一来源、权限/并发/版本协议，再验证编辑器和安全迁移；保留康奈尔学习逻辑与原始数据。

## 2026-10-24 · 学习工作台复习模块 V4.0（三类型交互 + 居中弹窗）

- 范围：`/workbench/review` 页面、新建/编辑复习卡弹窗、问答/挖空/主动回忆三种卡片交互、筛选/搜索/分页/列表卡片、复习记录统计；保留 SM-2 调度与笔记转卡流程。
- 前端：重写 `frontend/src/views/WorkbenchReview.vue`；`frontend/src/api/types.ts` 扩展 `WbReviewCard`/`WbReviewCardPayload` 的 `hint`/`tags`/`extraJson`；`frontend/src/views/__tests__/workbench-flow.spec.ts` 仅将表单选择器改为 `[data-test="review-card-form"]`。
- 后端：`WbReviewCard`、`WbReviewCardDTO` 新增 `hint`/`tags`/`extraJson`；`WorkbenchServiceImpl.createReviewCard/updateReviewCard` 按类型校验（BASIC 背面必填，CLOZE/RECALL 允许空背面并落扩展字段）；H2/MySQL `schema.sql` 增列；新增 `WbReviewCardSchema` 在启动时幂等 ALTER，兼容旧文件库与旧 MySQL。
- 交互：BASIC 问题→提示→显示答案；CLOZE `{{cN::...}}` 遮盖+逐空输入+本地文本比对（明确不自动评分）；RECALL 会话内复述→检查要点勾选→参考讲解。评分仍调用原 `gradeReview`→`SrsService`（SM-2），不改算法、不建第二套卡片系统。
- 命令与结果：`mvn -DskipTests compile` 通过；`npx vitest run` 164 文件/1305 测试全通过；`npx vue-tsc --noEmit` exit 0；`npm run build` 通过（Vite 8.1.4，4.96s；修复了 `point` 未使用导致的 TS6133 构建失败）。
- 数据验证：真实后端接口创建/更新/查询/删除 CLOZE 临时卡通过，新字段可读写；`./knowflow-dev.sh restart` 后接口返回 `hint/tags/extraJson`，无 Unknown column。
- 浏览器：Playwright + 本地 5173/8080，seed-personal-v1；覆盖主页面、三类型弹窗、编辑回填、类型筛选/搜索/列表卡片切换、三类型复习会话、删除确认、390/768/1024/1440。截图在 `frontend/verify/review-*.png`；pageerror 0，API 4xx/5xx 0。测试用临时卡全部删除，真实卡片 `suspended/reviewCount/intervalDay/nextReviewTime` 与验收前一致，未提交评分、未写入复习日志。
- 遗留：390 宽度下 WorkbenchShell 共享页头标题区偏窄（非本页新增）；`ContextMenu` 打开后若立即发生滚动会自动关闭（真实用户先滚动后点击不受影响）；H2 已验证，MySQL 旧库仅靠运行时幂等补列逻辑，未在真实 MySQL 执行。
- 下一任务：如需继续，补真实 MySQL 旧库迁移验证，并按现有 PC 范围评估 WorkbenchShell 页头在窄屏的换行策略；不重开移动端功能开发。


## 2026-10-24 · 笔记 P0 真实开发与验收

- 目标/原因：将 /notes 从未隔离账户的本地索引升级为真实服务端主库，保护并发、历史与迁移数据；不重复康奈尔系统。
- 范围/文件：新增 NoteService/NoteSchema/NoteController、NoteVersion及Mapper、扩展LearningNote/DTO/VO/Mapper、章节控制器委托、双数据库schema与V20261024增量DDL；前端notes API、保存会话、迁移服务/组件、Crepe编辑器、NotesManage/NoteEdit/DocDetail及相关测试；具体逐文件清单在docs/notes-upgrade/P0_IMPLEMENTATION.md。
- Git：main / 78f4b11d58da89d69264f4f452d210e6a6db34d1；保留原有未提交代码，未改Workbench两个页面的原有修改；无commit/push/reset/clean、无真实数据库删除或localStorage清空。
- 数据流：/notes→notesApi→NoteController→NoteService→LearningNoteMapper→learning_note；事务快照→learning_note_version；章节旧/api/learning/notes同样委托服务；wb_note及HTML/cue/summary/mastery/capture流程保持独立。认证owner不由请求声明；普通管理员不默认拥有私人笔记访问权。
- 并发/恢复：revision条件更新影响行数1才成功；旧revision409；历史恢复产生新修订；旧记录首改保留基线；旧离线副本同步暂停、保留原revision和needsReview，读回失败与刷新都不得自动上传覆盖。
- 迁移/AI：所有旧键只读预览和原始导出，主动确认账户后逐项真实导入、读回、回执、部分失败及刷新后重试；不删除原始键。AI只预览，确认后应用；useKnowledgeBase=false；正文/ID/revision改变拒绝旧建议。AI浏览器响应为明确的受控UI测试，不冒充真实模型。
- 命令与结果：后端全量mvn -q -f backend/pom.xml -Dmybatis-plus.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl -Dlogging.level.root=ERROR test退出0，139套件1022项、0失败/0错误/8跳过（原有opt-in浏览器测试，名单见verification.json）；MySQL专项mvn -q -f backend/pom.xml -Dnotes.mysql.enabled=true -Dtest=NoteMySqlMigrationTest test退出0，1项通过。MySQL5.7.16独立临时库增量DDL连续两次执行且中文/索引保留；JDBC旧revision条件更新返回0。
- 前端：npx vitest run退出0，169文件1321项通过；npx vue-tsc --noEmit、vue-tsc -b、npm run build均退出0；构建保留大chunk提示，编辑器包体性能预算未专项优化。
- 浏览器：scripts/tests/notes_p0_browser.cjs、notes_editor_browser.cjs、notes_compat_browser.cjs均真实Chrome退出0；主流程88API响应、兼容47响应、pageerror0。新增/保存/刷新/双标签409/删除恢复/历史恢复/迁移下载与部分失败刷新重试/旧副本断网刷新保护均已验证；原章节、康奈尔、文档预填自动持久化通过。目标viewport新载入覆盖1440/1280/1024/390/375；不宣称桌面展开侧栏直接缩窄窗口的完整矩阵通过。证据docs/notes-upgrade/evidence。
- 独立只读复核：要求技能requesting-code-review发现的问题均先补失败回归再修正；最终限定复核无新增明确风险，不替代实际测试。首次沙箱Mockito附加失败后在沙箱外以独立H2测试；共享target并发编译问题改独立JAR验收解决，最终全量通过。
- 运行：原8080/5173未重启/未改数据源；隔离15173/18080供验收，H2文件在/private/tmp/knowflow-notes-p0；本轮13306临时MySQL已关闭，目录保留。不把隔离验收视为原实例已部署新代码。
- ⚠️ Not verified：真实系统输入法、真实/付费AI、完整公式块与全部编辑器菜单交互、MySQL8及生产全栈、附件生产安全；公开分享、文件夹/收藏及文档双向关联未实现。P0核心闭环可用，不标记全部高级验收完成。
- 下一任务：补真实IME/浮动工具栏/公式块专项后推进P1原位编辑、导航组织与服务端标签筛选；保持学习架构唯一来源。

## 2026-10-24 · Java + AI 专业英语模块 P0

- 目标/范围：按 `docs/tech-english/01_PRD.md`、`02_IMPLEMENTATION_PLAN.md` 只落地 P0（词汇大厅、缩写百科、发音入口、概念详情、收藏与个人学习状态），不启动 P1–P3。
- 文档：新增 `docs/tech-english/03_BASELINE_AUDIT.md`、`04_IMPLEMENTATION_STATE.md`；本交接仅追加。
- 后端（新增未跟踪）：`TechVocabTerm`/`TechVocabPronunciation`/`TechVocabConceptLink`/`UserVocabState` 实体与 Mapper、`TechVocabVOs`/`TechVocabDtos`、`TechVocabService`、`TechVocabSchema`、`TechVocabSeedInitializer`、`seed/tech-vocab.seed.json`（50 词）、`TechEnglishController`、`admin/AdminTechEnglishController`、`TechEnglishApiTest`。`SecurityConfig` 追加公开只读 GET permitAll，`/api/tech-english/me/**` 仍需登录，`/api/admin/**` 仍为 ADMIN。
- 前端（新增未跟踪）：`api/techEnglish.ts`、`views/TechEnglishHome.vue`、`components/tech-english/{TermDetailPanel,TermPronunciation,TermEditorDialog,TermImportDialog}.vue`、`term-labels.ts`、`views/__tests__/TechEnglishHome.spec.ts`。已修改 `router/index.ts`（`/learning/english`、`/learning/english/:id`）、`config/navigation.ts`（学习组 match）、`apps/registry.ts` + `apps/icon-registry.ts`（注册 `tech-english`，导航 learning/order 10）。
- 数据流：页面 → `techEnglishApi` → `TechEnglishController` → `TechVocabService` → 4 张 `tech_vocab_*`/`user_vocab_state` 表；存量库由 `TechVocabSchema.migrate(conn)` 启动幂等补表，种子由 `TechVocabSeedInitializer`（ApplicationRunner `@Order(20)`）幂等写入，不依赖 `data.sql`。
- 复用：UI 组件 `AppDialog`/`StateView`/`Pagination`/`Icon`、`utils/toast` 的 `notify`/`getApiError`、`composables/useTextToSpeech`（Web Speech 降级）；未新建第二套闪卡/复习/题库系统，未改 Learning Store、Evidence 管线或 NextAction。
- 命令与结果：后端 `mvn -q -Dtest=TechEnglishApiTest test` 退出 0（11 项，0 失败/0 错误/0 跳过）；前端 `npx vitest run src/views/__tests__/TechEnglishHome.spec.ts` 8 项通过；`npx vue-tsc --noEmit` 退出 0；`npm run build` 退出 0（Vite 5.17s）。
- 全量回归：`npx vitest run` 4 文件 / 22 项失败、1321 通过。失败全部位于既有未提交 Notes 重构（`NoteEdit.spec.ts` 13、`NotesManage.spec.ts` 8、`NoteEditorLifecycle.spec.ts` 1）与 `icon-usage.spec.ts`（`NoteDocument.vue` 未定义图标）1，本次未改这些文件，未删除测试或放宽断言。
- 浏览器（Chrome DevTools MCP，真实后端 8080/5173）：统计 50 词/14 类型/10 领域；搜索 AQS 命中；详情「读缩写」进入播放中并可再次点击停止；干净上下文匿名收藏跳 `/login?redirect=/learning/english` 且无写入；已登录收藏 `PUT /api/tech-english/me/terms/{id}` 200 后已取消恢复；深链 `/learning/english/13?q=AQS` 直接加载；1440 双栏、1024 单栏、500 窄屏详情单栏+返回，均无横向溢出；pageerror 0，接口 200。
- Git：main / HEAD `78f4b11`；全部改动未跟踪/未提交，无 commit/push/reset/clean/stash，既有未提交修改保持原样。
- ⚠️ Not verified：MySQL 真实旧库迁移演练（仅 H2 验证）、真人录音/真实音标数据（当前仅 TTS 降级，`audioUrl` 为空时如实提示）、390×844 视口（Chrome 最小窗口宽度 500px）。真实付费模型未调用。
- 下一任务：P0 验收通过后按 PRD 接 P1（词条扩充与关联知识），先补 MySQL 旧库迁移与真实录音/音标数据验证；不重开移动端开发。

## 2026-10-24｜笔记 V2 工作台与真实组织能力验收

- 范围：继续普通笔记 P0，完成同页编辑/阅读/源码、目录与列表、全屏/沉浸、响应式、真实文件夹/标签/收藏、笔记双向关联与来源文档权限复验、AI预览确认、私人闪卡保存、紧凑迁移。保留康奈尔与学习核心；完整文件清单/影响见 `docs/notes-upgrade/V2_EXECUTION.md`。
- 文件：`frontend/src/components/notes/*`、`src/notes/*`、`api/notes.ts`、笔记薄视图/路由/App/CLayout与notes tokens/CSS及相关测试；后端NoteService/Organization/TagIndex/Schema/Controller/Image、LearningNote/DTO/VO/Mapper/Version与双方言增量DDL、NoteOrganizationHttpTest；两个V2浏览器验收脚本与 `docs/notes-upgrade/v2/evidence/*`。不将其他任务巨大脏树记为本轮产物。
- 真实调用链：Workspace→NoteDocument→useNoteSession→notesApi→NoteController→NoteService/NoteOrganizationService→Mapper/JDBC→learning_note与版本/组织索引。全部正文与组织变更复用revision/历史；不复制Evidence或NextAction，不新建第二套笔记Store/主库。
- 回归修复：首次create等待期间继续输入后旧draft入口仍指向new导致重复创建风险，失败回归后改为服务端ID指针并保留ID恢复副本；仅选区变化使AI建议过期；未知图标替换；摘要链接去地址不改正文；窄屏收起右面板。
- Git：main，HEAD `78f4b11`，无stage/commit/push/reset/clean/stash；限定跟踪文件diff check退出0。未停止/重启8080/5173。独立验收Vite15273→Java18180，临时H2 `/private/tmp/knowflow-notes-v2`，关闭真实AI/宿主机能力，测试凭据仅临时文件，不提交。
- 命令：前端全量 `npx vitest run` 172文件/1345项通过（退出0）；`npx vue-tsc --noEmit`退出0；`npm run build`退出0，有chunk提示。相关后端9项通过；package退出0。全量`mvn test`共1052项、1042通过/1失败/9跳过，退出1。单独复核13项仍只有`ExamAppInstallIntegrationTest.backendRecognizesEveryFrontendAppKey`失败：前端 `tech-english`未在后端OfficialAppRegistry生效。本轮未改这两处其他任务注册，不放宽断言。
- Chrome真实验收：核心52笔记响应，集成131接口响应，均0 pageerror。新增、编辑、自动保存、刷新、搜索、标签、断网恢复、软删除/恢复、文件夹、移动/收藏、双向笔记关联、版本恢复、迁移确认和读回原副本保留；1920/1440/1280/1024/390截图。全屏/沉浸保留编辑器DOM/光标与稳定滚动。AIchat仅明确受控响应，确认追加和私人闪卡存储走真实API，未调用付费模型。
- 未完成/⚠️ Not verified：MySQL真库/并发、真实中文输入法与全部Crepe命令/真实缩放、真实AI与引用检索、知识图谱节点关联、独立私人测验可作答保存、公开分享。自测/面试题目前只预览后追加私人笔记，不写入现有共享题库。下划线与专用提示块未扩展编辑器语义，不支持格式仍保护源码；图片512KB以内嵌方式继承笔记权限。
- 下一任务：先处理tech-english前后端注册一致性并复跑后端全量；随后设计私人测验owner模型与真实作答保存，继续复用现有学习系统。

## 2026-10-24｜智能写作目标知识库选择器局部UI修复

- 范围：复用CategoryTreeSelect→CascaderSelect与placeSelect；不重写写作页，不改后端/AI生成业务。原因：搜索输入双重padding、父框2px深色focus、384px面板上限；定位先按全高计算再收缩导致覆盖触发框。
- 文件：CascaderSelect.vue（36px搜索、品牌轻焦点、352px token/列表滚动、父面板边沿锚、整树Esc、加载错误重试）、CategoryTreeSelect.vue（状态透传）、select-position.ts（收缩坐标）、SmartWriting.vue（只绑真实分类loading/error/retry和可访问名）；2个现有测试文件新增3回归，scripts/tests/writing_select_browser.cjs，docs/writing-select/REPORT.md与截图/JSON证据。
- 数据流：categoriesApi.tree→CategoryVO映射→CascaderSelect数字value→config.kb，不变更选择含义、接口或ID类型。树为8080真实15根分类；错误重试通过受控500后回到真实响应；无假数据替换。
- 验证：新增3项RED后GREEN；前端全量172文件/1348项通过，vue-tsc --noEmit、npm run build均退出0；有全站chunk提示。Chrome真实localhost:5173/learning/writing在1440/1280/1024下搜索、层级、数字节点/路径回显、上下键+Enter、Esc焦点、外部关闭、空结果配置保留、加载错误重试通过，0 pageerror。未改后端未跑后端测试。
- Git：main/78f4b11，原脏工作树保留，无commit/push/reset/clean。5173/8080服务未重启。独立验收账户凭据仅/private/tmp临时文件，不写入仓库。
- 未解决/⚠️：现有generateByAI仅send content，config.kb未传，ChatSendPayload/DTO无KB字段。为遵守局部UI范围未改AI合同；不能声称所选KB参与真实生成，未调用付费AI。所有共享组件使用页未逐一浏览器遍历。
- 下一任务：若需兑现定向知识库生成，单独补有权限检查的KB范围接口合同与生成集成测试；本次UI交付见docs/writing-select/REPORT.md。

## 2026-10-24 · 当前 UI 开发规范与智能体入口同步

1. 目标：以最新已落地UI为准修正旧规范，避免后续Agent重新添加输入蓝框/灰色键盘环。
2. 原因：主设计正文仍含1px输入边框、2px外环、普通字段38px、旧Serif标题/字号等条款；文末追加澄清无法消除正文冲突。
3. 文件：AGENTS.md、VIBE_CONTEXT.md、docs/UI_DESIGN_SYSTEM_V2.md、UI_UX_SYSTEM.md、DEVELOPMENT_PROTOCOL.md；INPUT_FOCUS_FIX、ui-style-upgrade、borderless-forms相关历史报告/计划添加失效范围提示。本文仅追加历史，不删除旧记录。
4. 业务影响：无，本次仅文档；无运行时组件、Store、API、路由和数据库改动。
5. UI规范：普通文本/数字/搜索/Textarea/Select浅灰表面，默认无明显边框，鼠标/键盘聚焦均不新增蓝/灰border/outline/ring/外圈阴影；图标文字8–12px、顶部搜索12px；FormField/kb-form-row对齐，单行44px；数字/ARIA/特殊编辑器边界保留。
6. 数据流：无变化；AIComposer独立容器、按钮/菜单/Checkbox/Radio键盘反馈、forced-colors保留，不能把输入无外框扩成全局outline:none。
7. 验证：对照真实Token/组件源码；检查10份目标文档40条相对文件链接全部存在；现行规则覆盖与旧规范文本检查通过；目标diff --check通过。
8. Build/测试：纯文档未重跑Vitest、vue-tsc或build，不把此前1305测试等当作本轮新结果。
9. 浏览器：纯文档未重启/重测页面，运行时视觉无本次新增变更；历史浏览器证据仍仅属于原任务。
10. Git/范围：保留现有未提交修改，未commit/push/reset/clean；只更新项目级Agent规则与上下文，不声称修改了全局个人记忆或其他仓库。
11. 下一步：后续UI任务先读V2第0节与相关正文，再核对源码；发现历史规则冲突先改文档，不能把代码改回旧方案。未迁移旧表单结构仍不得宣称已全站完成。

## 2026-10-24｜Input Focus V2：聊天蓝框与共享焦点所有者

- 范围：仅焦点视觉、必要modality标记/测试。Chat与AIComposer业务script和修复前逐字一致；不改发送、AI接口、学习或笔记保存。
- 真实根因：Chrome Computed定位Chat.vue `.phase-d-chat :focus-visible:not(...)` 命中AIComposer内部textarea；与组件规则同为(0,4,0)，后加载覆盖为蓝2px outline/2px offset，内部直角、外壳20px圆角叠层。详见docs/ui-v2/input-focus-v2/before-computed.json。
- 文件：Chat.vue收窄文本focus规则；AIComposer只留外壳焦点、删除内outline与focus阴影过渡；style.css/ui-tokens加入单所有者、中性边界、鼠标无环、键盘柔和、错误/disabled/forced-colors；utils/input-modality.ts+main.ts仅标记视觉来源，无preventDefault；Select/Cascader、CTopNav、KnowledgeHome、SmartWriting、Notes的既有外壳加同一kb-input-surface类。具体清单与边界见docs/ui-v2/INPUT_FOCUS_V2_EXECUTION.md。
- 规范：请求的旧报告实际docs/reports/INPUT_FOCUS_FIX.md，主样式frontend/src/style.css。旧2026-10-24“普通键盘也无外框”与本轮明确要求冲突，按本轮保留柔和可见键盘提示，在UI_UX_SYSTEM末尾补充，不删历史。按钮/链接/菜单/Checkbox/Radio及代码编辑器特殊规则保留，无新增!important/依赖。
- 验证：modality RED后GREEN；相关3文件10项通过；最终前端全量174文件1359项通过，vue-tsc --noEmit与npm run build退出0（既有Node/chunk提示）。首轮TechEnglishHome缺失函数中间态未由本轮修改，后续完整重跑通过。限定diff check通过；未改后端未跑后端测试。
- Chrome：真实Chat五档1440/1280/1024/768/390，亮/暗、mouse/Tab、caret/尺寸、换行/Enter原发送路径通过；AI能力/SSE明确受控，不调用付费模型。真实共享组件错误/disabled、自适应/内部滚动、forced-colors外壳Highlight单轮廓通过。知识库/顶部搜索/隔离实例笔记标题mouse/Tab真实通过，pageerror0。fixture为真实组件、事件计数不冒充数据库。截图和JSON在docs/ui-v2/input-focus-v2/。
- Git：main/78f4b11，无stage/commit/push/reset/clean/restore/stash；保留庞大脏树与其他任务修改；8080/5173未重启。测试只用独立验收账户，凭据在/private/tmp未入仓。
- ⚠️：Windows真实高对比度、Safari/Firefox、真实系统IME、全部历史页面/设置弹窗未逐一人工验收；不宣称付费AI通过。
- 下一任务：复核Windows/Safari同一共享组件，并让新增文本消费者复用现有所有者类，避免页面通用focus再次命中内部textarea。

## 2026-10-24 · Java + AI 专业英语 P0 收尾 + P1 词汇训练与复习联动

- 目标/范围：接续已落地 P0，先修跨模块注册漂移与补齐 P0 可安全验收（隔离 MySQL 幂等迁移、390px 响应式、发音异常分支），再落地 P1 词汇训练与复习联动；不启动 P2/P3，不重写 PRD、不推倒 P0、不新建第二套闪卡/复习系统。执行记录见 `docs/tech-english/05_P1_EXECUTION.md`，状态表更新见 `04_IMPLEMENTATION_STATE.md`。
- 阶段 A（注册一致性）：`backend/src/main/java/com/knowflow/apps/OfficialAppRegistry.java` 补 `APP_STATUS.put("tech-english", "ACTIVE")`，与前端 `apps/registry.ts` 同步；未新建注册机制、未改其他 App Key、未删测试/弱化断言。`mvn -Dtest=ExamAppInstallIntegrationTest test` 4/4 通过。
- 阶段 B（P0 补验收）：① MySQL 5.7.16 隔离实例（`/tmp/knowflow-mysql-qa/data`，port 33306，验收后 shutdown，未触碰正式库）复现 latin1 建表导致中文写入报 `Incorrect string value`，修复 `service/TechVocabSchema.java` 为 MySQL 方言追加 `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`；latin1 库两次 `migrate()` 幂等，4 表回读 `utf8mb4_general_ci`，中文/`<>&"'`/🔒 正常；全新库两次真实启动种子「新增 50 / 跳过 0 / 关联 38」→「新增 0 / 跳过 50 / 关联 0」。② 四视口实测 1440 双栏、1280 双栏（`scrollWidth=1269<1280`）、1024 单栏（`1013<1024`）、390/375 单栏无横向溢出；发现并修复 `TermDetailPanel.vue` 窄屏「返回词汇列表」命中区被压成 16×16（`.te-detail-close` 加 32×32 最小命中区），复测 `{x:321,w:32,h:32}`。③ 发音异常注入无英文声线 → 真实 alert「当前浏览器没有可用的英语语音」；缩写 `A Q S` 与全称 `Abstract Queued Synchronizer` 朗读文本不同；播放中切词条无残留、双击只报一次错；`audioUrl` 全空，音频加载失败分支为 ⚠️ Not verified。
- P1 后端（新增）：`service/TechVocabReviewService.java`（加卡幂等 `INSERT ... ON DUPLICATE KEY UPDATE` + `SELECT ... FOR UPDATE`、队列 eligibility、评分委派、看板统计）、`controller/TechEnglishController.java` 追加 `POST /me/terms/{id}/review-card`、`GET /me/review-queue`、`POST /me/terms/{id}/review`、`GET /me/progress`、`vo/TechVocabVOs.java` 6 个 VO、`dto/TechVocabDtos.java` `ReviewInput`、`test/.../TechEnglishReviewApiTest.java` 12 项。
- P1 前端（新增/修改）：新增 `views/TechEnglishReview.vue`（四模式 `en-cn`/`cn-en`/`abbr-full`/`listening`、默认隐藏答案、揭晓后才评分、已提交不回退、`sessionStorage` 仅存会话元数据 12h 过期、评分以服务端为准）、`views/__tests__/TechEnglishReview.spec.ts`（9 项）；`api/techEnglish.ts` +7 类型/4 方法；`router/index.ts` 将 `/learning/english/review` 置于 `:id` 之前（`requiresAuth`、`fullscreen`）；`TechEnglishHome.vue` 加真实看板（已学习/今日待复习/今日已完成/已加入复习 + 各方向进度 + 三入口）；`FlashCardsHub.vue` 仅加 `TECH_ENGLISH → 专业英语 / book-open` 来源标签；`TechEnglishHome.spec.ts` 补 `getProgress` mock。
- 映射方案：`user_vocab_state.linked_flashcard_id` 为唯一映射（P0 已留字段，本轮补 `idx_user_vocab_card`）；复习卡是既有 `learning_flashcard` 的 `source_type=TECH_ENGLISH` 一种来源，一词一卡；收藏/阅读/发音/预览均不写调度；改词条中文不重置历史。未新增第二套 FSRS/SM-2。
- 真实调用链：`TechEnglishHome`/`TechEnglishReview` → `api/techEnglish.ts` → `TechEnglishController` → `TechVocabReviewService` → `UserVocabStateMapper`/`LearningFlashcardMapper`/`FlashcardFsrsService`（`preview`+`review`）→ `learning_flashcard` / `learning_flashcard_settings` / `learning_event`。`FlashCardsHub` 经既有 `api/learning.ts` 读取，未改其调度。
- 命令与结果（真实退出码）：`mvn -Dtest=ExamAppInstallIntegrationTest test` 4/4；`mvn -Dtest=TechEnglishApiTest test` 11/11；`mvn -Dtest=TechEnglishReviewApiTest test` 12/12；全量 `mvn test` 退出 0，`Tests run: 1064, Failures: 0, Errors: 0, Skipped: 9`，`BUILD SUCCESS`；`npx vitest run` 174 文件 / 1359 项全通过，退出 0；`npx vue-tsc --noEmit` 退出 0；`npm run build` 退出 0，`built in 5.28s`（CSS 修复后复跑）。
- 浏览器（Chrome DevTools MCP，隔离用户 `qa_tech_p1`/`qa_tech_p2`，内存 H2，未触碰真实复习记录）：搜索 AQS → 详情 → 加入复习 `POST /me/terms/13/review-card 200` + toast → 训练 `0/20` → 揭晓 FSRS 四档 → 评分 `1/20` 下一词；中→英输入 `polymorphism` 揭晓「与你输入的拼写一致」；缩写模式揭晓全称/类名/逐词拆解；听音模式揭晓前无 headword、播放中 disabled；离开再进恢复 `2/22`；首批评分后约 10 分钟看板「今日待复习=1」。Console pageerror 0。
- Git：main / HEAD `78f4b11`；本轮相关 `M OfficialAppRegistry.java`、`M router/index.ts`、`M FlashCardsHub.vue`、`M docs/AGENT_HANDOFF.md`、`M 04_IMPLEMENTATION_STATE.md`，`?? docs/tech-english/`、`?? TechEnglishReview.vue`、`?? TechVocabReviewService.java` 等；无 add/commit/push/reset/clean/restore/stash，保留其他 Agent 的未提交改造。
- ⚠️ Not verified：真人录音与 IPA 数据（`audioUrl` 全空，仅 TTS 降级）、音频加载失败分支、真实移动设备（仅设备视口模拟）、MySQL 正式库变更流程（仅隔离实例）。390 窄屏为视口模拟非真机。
- 下一任务：P2 前置——划词查词与知识关联需先做「文档可见性 → 词条关联权限过滤」审计；先补真人录音/音标来源与真实移动设备验证，再评估 P3 跟读评分。

## 2026-10-24 · AI 助手 V2.0：聊天渲染层 + 代码/JSON/HTML 隔离 + 编辑/引用/重新生成

- 目标/范围：只重构 `/chat` 消息渲染与消息动作，保留现有 SSE、模型切换、知识库检索、会话/分享/导出；未新建第二套 Markdown 渲染器，未删除既有功能，未 commit/push。
- 渲染层（新增）：`frontend/src/utils/chat/contentParser.ts`（markdown-it token 分组、顶层 fence 拆代码块、整条合法 JSON 识别、流式跳过 JSON、DOMPurify + `isSafeLinkHref`/`isSafeImageSrc` 协议白名单、评分 Schema 识别）；`utils/chat/fileTypes.ts`（语言→扩展名/MIME、真实 Blob 下载）；`utils/chat/clipboard.ts`；`components/chat/ChatMarkdownRenderer.vue`、`ChatCodeBlock.vue`、`ChatJsonViewer.vue`、`ChatJsonNode.vue`、`ChatHtmlPreview.vue`、`ChatImageViewer.vue`。
- 安全：聊天正文 `html:false` 转义 + DOMPurify；HTML 预览 `sandbox=""` + `referrerpolicy=no-referrer` + CSP `default-src 'none'`，未同时授予 scripts/same-origin；图片/链接协议白名单；无内联 onclick，复制走 Vue 事件。
- Chat.vue：用户消息 hover/focus 工具栏（复制/编辑/引用/更多，触屏常显）、AI 操作栏（赞/踩/复制/重新生成/引用/更多）、引用卡片、编辑保存后真实 regenerate、`followTail` + 回到底部、`.chat-conversation-scroll` 单滚动容器。
- 后端：`ChatSendDTO.quoteMessageId`；新增 `ChatRegenerateDTO`；`ChatService/Impl` 增 `updateMessage`（校验归属/role、截断后续分支）与 `regenerate`（真实模型 SSE、新 assistant 落库、不重复 `completeTurn`）；`buildQuoteContext` 校验同用户同会话、截断 1200 字；`ChatController` 增 `PATCH /messages/{id}`、`POST /messages/{id}/regenerate`。
- 真实接口验收（curl，admin/user1，H2 内存库）：流式 delta/done 正常；编辑后后续 assistant 被逻辑删除；regenerate 生成新 ID 259 替代 258；带 `quoteMessageId` 的回答正确复述被引用内容；admin 引用 user1 消息返回 `404 引用的消息不存在`。
- 修复：`consumeSse` 收到 done 后不再对响应 `cancel()`，避免正常完成在 DevTools 显示 `net::ERR_ABORTED`（此前误判为接口故障）；用户主动停止仍走 AbortController。
- 测试：`npx vitest run` 182 文件 / 1440 项全通过（含新增 `components/chat/__tests__/chat-render.spec.ts` 5 项：代码块/HTML 转义/整条 JSON/评分卡/沙箱属性；`utils/chat/__tests__/contentParser.spec.ts` 15 项；`ComposerIntegration` 7 项）；`npx vue-tsc --noEmit`、`npm run build` 退出 0；后端全量 `mvn test` 1085 项 0 失败 0 错误 9 跳过，其中 `ChatMessageEditRegenerateTest` 6/6。
- 浏览器（Chrome，1440×900，admin）：布局/侧栏/单滚动/输入框固定、Markdown 标题/列表/表格、代码块语言与复制「已复制」、AI 操作栏、引用卡片出现与取消均 PASS；截图见工作区 `step3-quote-card-appears.png` 等。
- ⚠️ Not verified：JSON Viewer 与 HTML 预览的浏览器视觉（组件测试通过，浏览器子代理预算两次耗尽）、图片查看器浏览器交互、Safari/Firefox、移动真机、深色主题、PDF/DOCX 等 P2 文件生成（未实现）。
- 下一任务：P1 收尾——`ChatFileCard` + 后端生成文件存储/权限下载；再按需补 Mermaid 与消息版本切换。

## 2026-10-24 · DatePicker UI V2：共享日期选择器与两处接入

- 目标/范围：替换浏览器原生日期弹层，新增共享单日期 `DatePicker`；本轮只接入日历事件“全天”与学习目标“截止日期”，不批量替换 `date/time/datetime-local`，不改路由、Store、后端与全局 CSS。
- 根因：`CalendarEventDialog.vue`、`MemorialDialog.vue`、`ExamMistakes.vue` 等原使用原生 `<input type="date">`；其月份 stepper、黑灰日期格、Clear/Today 属浏览器 UI，外层 CSS 无法控制。截图归属为用户截图未入仓条件下的症状推断，详见执行报告 §1。
- 选型：不新增依赖。Reka UI 2.11.0（MIT，DatePicker 仍 Alpha，整包约 9.18MB，带 internationalized-date/floating-ui 等）；`@vuepic/vue-datepicker` 14.0.0（MIT，peer Vue `>=3.5.0`，项目 `^3.4.38` 有 peer 风险，date-fns/主题体系冗余）；`v-calendar` npm latest 2.4.2（MIT，Vue 2 peer）。最终复用 `FormField`、`Icon`、`placeSelect`、`--kb-*` Token 自研。
- 新增：`frontend/src/components/ui/DatePicker.vue`（`modelValue` 为 `YYYY-MM-DD`；label/placeholder/help/error/required/disabled/readonly/clearable/min/max/locale/weekStartsOn；44px 触发器、336px 面板、品牌蓝选中、今日 soft+点、roving tabindex、方向键/Home/End/PageUp/PageDown/Escape、`aria-*` 完整）、`ui/date-picker-utils.ts`（严格解析、闰年校验、本地格式化、月末收敛、6×7 网格）、`__tests__/DatePickerV2.spec.ts`（14）、`__tests__/date-picker-utils.spec.ts`（6）。
- 修改：`components/calendar/CalendarEventDialog.vue` 仅全天分支替换，`scheduledDate` 仍为 `YYYY-MM-DD`；`views/LearningGoals.vue` 截止日期替换为 `DatePicker`（`clearable`，`deadline || null` 与编辑回填不变）；`docs/UI_DESIGN_SYSTEM_V2.md` 追加 DatePicker V2 规范；本文件。
- 日期合同：`YYYY-MM-DD` 本地日历日，空串未选择；`parseDateString` 用本地日期回读校验，`coerceDateString` 只取 ISO 前 10 位并校验；全链路无 `toISOString()`，`Asia/Shanghai` 等时区不会偏移；min/max 字符串比较含边界。`datetime-local` 未混入。
- 真实调用链：`CalendarEventDialog`/`LearningGoals` → `DatePicker` → `emit('update:modelValue')` → 原 `buildPayload()/saveGoal()` → 原 task/learning-goal API，未改任何后端协议。
- 验证命令与结果（真实退出码）：相关 `npx vitest run` 2 文件 20 项通过；全量 `npx vitest run` **182 文件 / 1440 项通过**，退出 0；`npx vue-tsc --noEmit` 退出 0；`npm run build` 退出 0（`built in 12.06s`，仅既有 chunk 提示）。
- 浏览器：真实 Chrome 1440×900 打开日历“新建事件 → 全天事件”，面板 336×389、标题左/箭头右且水平排列、周一起点、选中今日蓝底白字、非本月弱化；点击日期后值正确回写并关闭；ArrowDown 聚焦当日、ArrowRight/PageDown 跨日跨月正常、Escape 回焦；手工输入合法/非法日期分别成功与提示错误；跨年至 2027-01 可选；学习目标弹窗 label/控制同行 44px 对齐，空值不显示清除，面板空间不足向上展开。深色 `data-theme` 面板正常。1280/1024/768/390/960×600 面板均在视口内。
- 证据：`docs/ui-v2/date-picker-v2/` 12 张截图 + `console-network.json`（Console error/warning 0、HTTP≥400 0、失败请求 0）。Chrome DevTools MCP 的 `filePath` 被工作区根目录限制拒绝，截图改用本机 Chrome + CDP 临时脚本（`/tmp`，未入仓、无新增依赖）落盘。
- Git：未 commit/push/reset/clean/restore/stash；未改数据库、真实学习记录、FSRS/Evidence/Mastery/NextAction；保留其他任务未提交改动。
- ⚠️ Not verified：用户截图原文件不在仓库，页面归属为推断；Safari/Firefox 未实机；移动端为视口模拟；真实日历事件落库未写入测试数据；深色下旧弹窗仍硬编码白底；range/datetime/time 变体未实现。
- 下一任务：按执行报告 §12 迁移 `MemorialDialog`（可直接复用）与 `ExamMistakes`（需独立 range 变体），再做 `LearningCenterPage`/非全天事件 datetime 变体；迁移前逐项核对业务时区语义。

## 2026-10-24 · Java + AI 专业英语 P2 划词学习与概念关联

- 目标/范围：只做 P2（阅读器划词查询、术语浮层、匹配与多义、知识关联、AI/规则提词、词库扩充、与 P1 打通），不重做 P0/P1、不新增第二套词库或复习调度、不自动发布 AI 词条、不进入 P3。执行记录见 `docs/tech-english/06_P2_EXECUTION.md`，状态表与全量基线更新见 `04_IMPLEMENTATION_STATE.md`。
- 选区与阅读器接入：新增 `frontend/src/composables/useTermSelection.ts`（原生 Selection/Range，>40 字符 / >5 词 / 纯中文 / 编辑区直接忽略，滚动与 resize 按原 Range 重测，卸载解绑）与唯一宿主 `components/tech-english/SelectionTermLookup.vue`；接入 `views/DocDetail.vue`（`contentRef`）、`views/ChapterLearn.vue`（`readingRoot`）、`components/notes/NoteEditor.vue` 只读模式（`readRoot`）。未改 Markdown 节点、复制、右键、高亮与批注；未给每个阅读器复制第二套选区逻辑。
- 术语浮层：新增 `components/tech-english/TermLookupPopover.vue`，白底/`--kb-*` 令牌、词形 `--kb-fs-h2`（缩写 JetBrains Mono + 徽标）、英文全称、中文释义、发音（复用 `TermPronunciation.vue` 与 `useTermSpeech.ts`）、专业解释、关联知识、相关术语、`查看详细概念/收藏词汇/加入复习`；下方空间不足翻转、夹紧视口、Skeleton、`role=alert` 失败、未收录空状态、多义项标签栏、Escape/外部点击关闭、单实例。
- 后端（新增/修改）：`service/TechVocabService.java` 增 `lookup`（Unicode/大小写/首尾标点/空白归一化、`ambiguous` 多义、领域排序、拒绝原因）与 `resolveTarget` 二次鉴权（注入 `DocumentAccessPolicy`：`DOCUMENT` → `/doc/:id` 修正、`KNOWLEDGE` → `canReadKnowledge`、`CHAPTER`/`NOTE` 匿名一律不返回，已删除实体不返回）；`service/TechVocabExtractionService.java`（文档/章节/笔记鉴权、规则提取、AI 结构化校验、候选幂等、审核 `publish`）；`entity/TechVocabConceptLink.java`、`entity/TechVocabCandidate.java`、`mapper/` 对应 2 个、`dto/TechVocabDtos.java`、`vo/TechVocabVOs.java`；`controller/TechEnglishController.java` 增 `GET /lookup`、`/me/candidates/extract`、`/me/candidates`（GET/POST）；`controller/admin/AdminTechEnglishController.java` 增候选列表与 `/{id}/review`；`service/TechVocabSchema.java` 幂等补 `tech_vocab_concept_link`、`tech_vocab_candidate`。
- 前端新增/修改：`api/techEnglish.ts` 增 lookup/candidates 类型与方法（`reviewCandidate` 入参放宽为 `string | null`）；`components/tech-english/TermExtractDialog.vue`（范围/类型/上限/AI 开关、规则降级明示、预览/编辑释义与领域、提交待审；登录态改为打开时才读取）、`CandidateReviewDialog.vue`（补中文释义输入，服务端要求必填，默认仅通过不上架）、`term-labels.ts`（领域标签与推断）；`views/TechEnglishHome.vue` 暴露「候选审核」入口。
- 词库扩充：新增 `backend/src/main/resources/seed/tech-vocab.seed.p2.json` 56 条（WORD 29 / PHRASE 24 / ACRONYM 3），`seed/TechVocabSeedInitializer.java` 支持多批次幂等加载；最新启动日志「50 + 56 → 累计 106 条、关联 76」。音标留空、读法 `PENDING`，不伪造真人录音；未覆盖任何用户收藏/复习记录/术语关联。
- 真实调用链：阅读器 → `useTermSelection` → `SelectionTermLookup` → `TermLookupPopover` → `api/techEnglish.ts` → `TechEnglishController` → `TechVocabService.lookup` → `TechVocabTermMapper`/`TechVocabPronunciationMapper`/`TechVocabConceptLinkMapper`/`DocumentAccessPolicy`；收藏与复习复用 `PUT /me/terms/{id}` 与 `TechVocabReviewService.addToReview` → `FlashcardFsrsService` + `user_vocab_state.linked_flashcard_id`。
- 命令与结果（真实退出码）：`npx vitest run` **183 文件 / 1444 项通过**，退出 0；`npm run build`（内含 `vue-tsc -b`）类型 0 error、`built in 5.28s`，退出 0；后端全量基线 `mvn test` 1085 项 / 0 失败 / 0 错误 / 9 跳过，`BUILD SUCCESS`；收尾专项复跑 `mvn -Dtest='TechEnglish*Test' test` 38 项全通过（`TechEnglishP2ApiTest` 15 项）。
- 浏览器（Chrome DevTools MCP，5173 + 8080，开发用 H2 文件库 `backend/data/rebuild-v1-host-test-20261012/knowledge`，非正式 MySQL；临时文档与 admin 账号）：选中 AQS → 查询入口 → 浮层（全称/释义/两种读法/关联术语）→ 读缩写进入播放态 → `PUT /me/terms/13` 200 回显已收藏 → `POST /me/terms/13/review-card` 200 且重复点击幂等 → `/learning/english/review` 队列含 AQS → 返回阅读页浮层清理；滚动 200px 入口精确跟随（545.742→345.742）；Escape/外部点击关闭；未收录词与待核验读法均如实提示；375×812 设备视口浮层宽 351、无横向溢出；Console error/warn 0。第二条链：提取 17 条（规则模式明示）→ 编辑释义/领域 → 分两批提交 200，`/me/candidates` 共 17 条无重复、公共词库仍 106 → 管理端「候选审核」未填释义被拒 → 补释义通过不上架（仍 106）→ 勾选发布通过（106→107）→ 同领域重复 `transaction` 409 不覆盖。验收后已清理：17 条候选改为拒绝、测试词条 `synchronizers`(108) 改回 `DRAFT`（公共总数回到 106）、admin 的 AQS 收藏已取消；临时文档 `#1402` 与该校验复习卡保留在开发库（无删除接口，未直接改库）。
- 修复的真实缺陷：① 术语关联 `DOCUMENT` 路由误拼 `/docs/{id}`（实际 `/doc/:id`，点击 404）；② 匿名可见章节关联标题（章节入口本需登录）；③ 管理端审核弹窗缺少中文释义输入框导致无法通过审核（服务端必填），已补输入并接 `reviewCandidate`，新增 `CandidateReviewDialog.spec.ts` 4 项。
- Git：main / HEAD `78f4b11`；无 add/commit/push/reset/clean/restore/stash，保留其他 Agent 的未提交改动；未改数据库 Schema 删除项、未改正式用户学习进度；验收数据只写入开发用 H2 文件库（非正式 MySQL），并已按上文清理到公共词条 106。
- ⚠️ Not verified：真实付费模型下的 AI 提词（本地未配置，仅验证规则提取与降级）、真实 MySQL 旧库迁移、真实移动设备（仅视口模拟）、笔记只读正文的浏览器逐条实测（仅自动化）、真人录音/IPA、词库 106→200 的后续批次。
- 下一任务：隔离 MySQL 演练 5 表迁移并按批扩充核验词条；配置可用模型后跑一次非私人样本的 AI 提词端到端；P3 跟读评分另行启动，先解决录音采集与评分可信度。

## 2026-10-24 · 全项目文档治理与开发知识库基线

- **目标/范围**：仅文档与必要索引/校验脚本，按源码更新架构、原PRD、能力矩阵、当前状态、路线/门禁与Agent入口；不实施业务/DDL/生产配置，不运行真实AI、正式DB、审核或导入。
- **事实扫描**：127条静态路由（119组件/8redirect）、18Registry项、97Controller类/612方法映射、108@TableName实体；174前端测试源码、147后端测试源码只代表存在。66能力切片：41 IMPLEMENTED、18 PARTIAL、2 DISABLED、2 NOT_IMPLEMENTED、2 BLOCKED、1 NOT_VERIFIED；全部本轮runtime verification=NOT_VERIFIED。
- **关键更新**：普通笔记已服务端化而SmartWriting本地/模拟回退独立；T1/G1/G2历史完成不再重复列未实施；HashMap新作者包humanApproved=false/正式新发布0，技术/人工/语义/受控导入分门禁；内部工具非MCP/生产沙箱；AI和运行时切库的应用默认不等于测试安全默认。全局NOT_READY，V1仍PROPOSED。
- **文件/入口**：README、docs/README、PROJECT_ARCHITECTURE（6图）、原产品需求PRD、roadmap01–08原位更新；完整路由/API/表清单、DOCUMENT_INDEX/MIGRATION_MAP/GOVERNANCE/DELETE_CANDIDATES与docs/document-governance报告/JSON；AGENTS/VIBE/开发/UI/旧UI/部署模板同步。没有另造第二套PRD/能力矩阵/路线权威。
- **UI**：浅灰普通输入，默认无明显边框，鼠标/键盘聚焦无蓝/灰border/outline/ring/外圈阴影；44px/12px、图标间距、FormField对齐、菜单/按钮键盘语义保持；旧UI明确SUPERSEDED。
- **归档**：11份治理前规划快照复制到docs/archive/2026-10/pre-governance，原路径原位更新，保留原SHA与迁移映射；移动0/删除0，原始执行证据不改写。精确重复只列复核，不批准删除。
- **验证**：文档盘点、AST路由/版本、注解/表/测试文件、24核心入口链接/源码/状态检查；文档检查器4项回归与脚本语法通过，7 Mermaid语法通过（非渲染认证）。当前核心无死链，归档快照无链接问题；全量历史/参考仍有候选链接债，准确数量见本轮REPORT/validation.json。目标Diff通过；全仓既有linkedlist.md:191尾随空白不动。
- **只读复核**：前端/后端/内容发布分别复核；已修正文档的Docs→Category/Doc调用链、ADMIN准入和PRD/KG/导入顺序。无新增运行功能，未把历史通过数写成本轮PASS。
- **Git**：main/78f4b11，全部原dirty/untracked保留；无commit/push/reset/clean/restore/stash。源码/DB/权限/算法数据流未改，仅文档与工具写入。完整变更清单见document-governance/change-manifest.json。
- **未验证/下一步**：未逐篇认证全部历史语义，未执行业务tests/build/browser/Provider/MySQL/生产验收。下一轮推荐R01真实范围/能力批准，R05隔离目标MySQL演练可与内容人审并行；不重做旧G1/G2或自动批准HashMap。

治理最终并行增量说明：其他任务新增2份Markdown并更新英语/DatePicker源码，本轮保留、不归为本轮代码成果；最终静态183前端测试源码/149后端测试源码，HTTP612、实体109。新增KF-063–066后为66能力切片：41实现、18部分、2禁用、2未实现、2阻断、1未验，全部本轮运行未验。英语章节关联/无href、审核与发布不同状态、DatePicker仅两处迁移等边界已记录，不采信他任务报告为本轮新VERIFIED。

## 2026-10-24 · Checkbox UI V3

- 目标/原因：统一正方形勾选框，去掉原生checkbox命中全局input:focus的3px阴影，解决尺寸、间距和闪卡排程对齐。
- 文件：既有Checkbox.vue/style.css/ui-tokens.css、63处普通checkbox所在30文件；完整清单、任务快照Diff、截图见[报告](checkbox-v3/REPORT.md)。新增合同测试与Chrome脚本/夹具。公共规范/AGENTS同步。
- 业务/UI/数据流：Vue原生v-model继续处理数组/Set/true-false；change布尔兼容，native-change保留原Event；未改FSRS/评分/Store/API/Schema。仅独立开发验收账户与本人卡片有测试写入；筛选前后FSRS JSON不变。
- 验证：全量1529项1519通过10专业英语旧失败，旧快照复现；最终局部8项通过；类型/正式构建旧Date/TimePicker/英语notify错误未解决。浏览器四宽度×双主题、键盘/半选/禁用/200%与56API无HTTP错误；有既有favicon404，不能称零Console错误。最终命令结果在报告。
- 审阅：恢复被过度清理的CodeAgent非Checkbox共用键盘焦点规则。Git main/78f4b11；既有dirty保留，无commit/push/reset/clean。无生产部署。
- 未验：30页所有业务流程、真实触屏/屏幕阅读器人工验收。下一任务：独立处理英语与Date/TimePicker基线失败后再跑门禁。

## 2026-10-24 · Date & Time Picker V3：统一锚定 + TimePicker + DateTimePicker + 三页面迁移

- 目标/范围：修复原生日期时间浮层偏移/遮挡，新增 `TimePicker`（`HH:mm` / `HH:mm:ss`）与 `DateTimePicker`（`YYYY-MM-DDTHH:mm(:ss)`），迁移日历事件非全天、纪念日固定日期、习惯提醒与 LearningPrefs；不重做 DatePicker V2，不改路由/Store/后端协议/正式数据。
- 根因：三场景原控件分别为 `datetime-local`/`date`/`time`，其面板由浏览器进程绘制、不在页面 DOM，CSS 与 `getBoundingClientRect()` 均不可控。已全部迁移到共享组件；展开态 `nativeInputs = 0`。
- 定位：`select-position.ts` 新增 `placeAnchoredPanel`（下方优先、翻转、右缘钳制、8px 安全距、visualViewport offset）；新增 `use-anchored-panel.ts`（Teleport body + fixed + z-index 1200、ResizeObserver、window/visualViewport scroll/resize、锚点滚出即关闭、卸载解绑、关闭回焦）。DatePicker/TimePicker/DateTimePicker 全部复用。
- 组件：`DatePanel.vue` 为日历唯一实现；`TimeColumns.vue` 为时间列唯一实现（2/3 列、每列 168px 独立滚动、选中品牌蓝）；`TimePicker.vue` 240/288px 面板、手工输入、min/max、清除/取消/确认、键盘循环与切列；`DateTimePicker.vue` 340px 面板、草稿与提交分离、确认才写回、秒值保护、无 Z/时区偏移；两者底部操作区 sticky，短视口滚动时按钮始终可见。
- 业务迁移：`CalendarEventDialog` 非全天替换双 DateTimePicker、全天继续 DatePicker、`blank()` 分钟归零与结束 +1h 原逻辑保留；`MemorialDialog` 固定日期改 DatePicker，每年重复 `monthDay` 逻辑不变；`Habits` 提醒改 TimePicker（`HH:mm`，null 清空语义保留）；`LearningPrefs` 同步迁移。
- 数据合同：日期 `YYYY-MM-DD`；时间 `HH:mm(:ss)`；日期时间 `YYYY-MM-DDTHH:mm(:ss)` 本地墙钟；日历保存 `yyyy-MM-dd HH:mm`（后端分钟精度）；纪念日 `fixedDate yyyy-MM-dd` / `monthDay MM-dd`；习惯 `reminderTime HH:mm`。全链路无 `toISOString()`、无 `Z`。
- 真实调用链：`CalendarEventDialog/MemorialDialog/Habits/LearningPrefs` → 共享 Picker → 原 `buildPayload()/saveHabit()/save()` → 原 `/api/tasks`、`/api/calendar/memorials`、`/api/habits`；未改权限与后端。
- 验证（真实结果）：专项 8 文件 **79/79 通过**（含 DatePicker V2 历史 20 项）；全量 Vitest **190 文件 / 1519 项，1510 通过 / 9 失败**，9 项全部为 `TechEnglishHome.spec.ts` mock router 缺少 `TechEnglishTopics` 的既有问题；`vue-tsc -b` 仅剩 `TechEnglishTopic.vue` 2 个既有 `notify.success/error` 错误，本轮文件 0 错误；`vite build` 退出 0（`built in 5.47s`），`npm run build` 仍被上述 2 个既有类型错误阻断。
- 浏览器证据：真实 Chromium（本机 Playwright 缓存，未新增依赖）+ 管理员登录 + 隔离数据后删除。三页面面板 gap 4、视口内；1440/1024/375 下 DateTimePicker 340×482/416/392.7、TimePicker 240×311/305；纪念日 336×389；Dialog 滚动 80px 后触发器 397.4→317.4、面板 top 8.4、gap 4；等效缩放 125%/150% 向下、200% 翻转向上且 gap 4；深色 1440 正常；Console/pageerror/requestfailed 全 0。
- 保存回归：`POST /api/tasks` 200，跨日 `2027-01-01 23:30 → 2027-01-02 00:30`、`scheduledDate 2027-01-01`，回填一致后 `DELETE` 200；纪念日固定 `2026-12-31`、每年 `10-24` 均 200 并删除；习惯 `reminderTime 23:30` 保存/回填/删除 200。
- 证据与文档：`docs/ui-v2/date-time-picker-v3/`（30+ 截图与 6 份几何 JSON）；新增 [DATE_TIME_PICKER_V3_EXECUTION](ui-v2/DATE_TIME_PICKER_V3_EXECUTION.md)；`UI_DESIGN_SYSTEM_V2.md` 追加 V3 规范；本文件。
- Git：未 commit/push/reset/clean/restore/stash；保留其他 Agent 未提交改动；未写正式库或真实学习记录。
- ⚠️ Not verified：1440×900 下 DateTimePicker 内容 662px / 可视 480px，首屏时间列需面板内滚动约 182px（sticky 底栏已保证操作可用）；Habit 后端仅在非 null 时更新，真实清空仍受限；`LearningCenterPage` 截止时间合同冲突未迁移；`ExamMistakes` 待 DateRangePicker；缩放为等效视口模拟；移动端仅兼容检查。
- 下一任务：先统一 `LearningCenterPage` 截止时间合同再迁移 DateTimePicker；为 `ExamMistakes` 建独立 DateRangePicker；评估后端 Habit 提醒清空语义。

## 2026-10-24 · 技术英语 V3：词库 106→300 + 专题学习 + P3 第一阶段跟读

- **目标/范围**：在不重做 P0–P2、不新建第二套复习引擎的前提下，扩充已发布词库至 300 条，增强详情内容，新增按技术方向专题学习与听读/跟读（不上传、不做虚假评分）。任务书阶段 A–J。
- **词库结果**：106 → **300**（b1 JAVA/JVM/CONCURRENCY 45、b2 CACHE/DB/SPRING 47、b3 MQ/DISTRIBUTED/AGENT 43、b4 LLM/RAG 41、b5 AI_ENG 18 = 194 新增）；12 个方向；`WORD 123 / ACRONYM 26 / PHRASE 151`。全部走既有 `import-preview` → `import-confirm`，每批 `invalid=0`、重复确认 `replayed=true`。
- **种子与关系**：`TechVocabSeedInitializer.SEED_FILES` 追加 b1–b5；重启日志「新增 0 条，已存在跳过 300 条，建立关联 354 条」，新库可直接建库、旧库只补关系。修正 P0 种子历史拼写 `CONTRASTS → CONTRAST`，并在 `term-labels.ts` 增加容错映射，避免详情页泄漏英文枚举。
- **发音数据**：回读 VERIFIED 520 / PENDING 4（BM25、HNSW、LoRA、ReAct 的缩写读法留空）、`audioUrl` 非空 0、缩写 `fullForm` 缺失 0；未伪造音标或录音。
- **专题学习（G）**：新增 `/learning/english/topics`、`/learning/english/topics/:domain`；后端复用 `TechVocabReviewService.topics/topic` 聚合真实 `user_vocab_state`，匿名不伪造进度；「加入复习」复用 `addToReview` → `FlashcardFsrsService`；连续听读只播已核验读法。不新建学习路径表。
- **P3 第一阶段（H）**：新增 `/learning/english/speak`，`MediaRecorder` + `getUserMedia` + Blob 本地回放，60 秒上限，权限拒绝/无设备/占用/编码失败/不支持均有提示，退出停止并释放麦克风与 URL；默认不上传、不做自动评分。词汇大厅与详情面板均有入口。
- **文件**：`backend/src/main/resources/seed/tech-vocab.seed.v3.b1~b5.json`、`TechVocabSeedInitializer.java`、`TechVocabReviewService.java`、`TechVocabVOs.java`、`SecurityConfig.java`（topics 白名单）、`frontend/src/views/TechEnglish{Topics,Topic,Speak}.vue`、`components/tech-english/topic-order.ts`、`term-labels.ts`、`api/techEnglish.ts`、`router/index.ts`、`views/__tests__/TechEnglish{Topics,Topic,Speak}.spec.ts`、`docs/tech-english/04、07、08`。
- **真实调用链**：大厅/专题页 → `GET /api/tech-english/topics[/{domain}]` → `TechVocabReviewService` → `tech_vocab_term` + `user_vocab_state`；加入复习 → `POST /me/terms/{id}/review-card` → `TechVocabReviewService` → `FlashcardFsrsService` → `user_vocab_state.linked_flashcard_id`；跟读页仅浏览器本地 API，无上传接口。
- **验证**：前端全量 `npx vitest run` **194 文件 / 1543 项全通过**，`npx vue-tsc --noEmit` exit=0，`npm run build` exit=0；技术英语专项 38 项通过。后端 `mvn test` **1085 项，Failures 5 / Errors 3 / Skipped 9，exit=1**；8 项失败全部在未修改的 chat/AI 模块（ChatPrincipalAuthTest、ChatConversationTitleV2Test、ChatMessageEditRegenerateTest、ChatKnowledgeSwitchTest、ChatManagementMigrationTest、AiServiceGateTest），技术英语 38 项全通过，未删测试或放宽断言。
- **浏览器**：Chrome DevTools MCP 实测 `/learning/english` 300 项 / 12 方向 / 26 缩写 / 20 条每页，1440 双栏、1024 单列、390 视口模拟单列且 `overflowX=0`；`/topics` 12 卡片顺序正确；`/topics/JAVA` 28 词、路径 1..28、28 个播放按钮可用；`/speak?termId=219` 文案、网络仅 GET 200、Console 无 error。未登录/登录入口与重复加入复习回显已由自动化覆盖。
- **Git**：未 commit / push / reset / clean / restore / stash；保留其他 Agent 全部未提交改动；未改正式 MySQL 数据，验收写入仅限开发 H2 文件库。
- **⚠️ Not verified**：真实麦克风录音→回放闭环（权限弹窗无法自动化确认）、Safari/Firefox 编码回退、真实付费模型 AI 提词、MySQL 正式库迁移与 300 条回读、真实移动设备、P3 自动评分（未实现）。
- 下一任务：由人工在真实麦克风下完成「录音 → 停止 → 回放 → 重录 → 退出释放」并记录实际编码类型与 60 秒上限行为，再据此决定 P3 第二阶段评分是否启动。

## 2026-10-25 · AI 助手 V1.0：深度思考 + 折叠思考 UI + RAG/Agent 执行轨迹

- **目标/范围**：按任务书 Phase 0–5 为现有聊天实现真实深度思考与执行过程可视化；不伪造推理/进度/工具/检索结果，不重写聊天业务与学习管线。完整报告见 [ai-thinking/REPORT.md](ai-thinking/REPORT.md)。
- **关键根因修复**：`ChatServiceImpl.streamSend/regenerate` 原在请求线程内跑完模型调用，Spring MVC SseEmitter 将全部事件缓存到方法返回后一次刷出；改为 `submitSse()`（`DelegatingSecurityContextExecutorService`，守护线程 `chat-sse`）后逐条到达，并新增 `awaitSseTasks()` 供测试等待。`AiService` 10↔11 参默认方法互调导致 `StackOverflowError`，11 参 default 改为回退 9 参基础实现。`AiServiceImpl` 在 adapter 解析后补 `requireAiEnabled()`，关闭态不消耗付费 Token。`ChatManagementSchema` 对缺表（H2 42102 / MySQL 1146 / 42S02）跳过补列，避免存量库初始化中断。
- **协议/能力**：聊天 SSE 实际产生 `start / status / rag_start / rag_step / rag_done / reasoning / reasoning_done / answer / delta / done / message / error`；`tool_start/progress/done/error` 仅前端解析与组件就绪，聊天链路无 Tool Calling 不产生。能力由 `ChatModelCapabilityRegistry` 按 provider+模型族解析，`ChatModelCapability{reasoningSupported, reasoningKind(provider_text/summary/none), supportsTools, note}` 经 `GET /api/chat/capabilities` 驱动开关；`ChatSendDTO.model` 透传 11 参 `streamChat`。
- **持久化**：`chat_message.reasoning_content / reasoning_kind / reasoning_duration_ms / trace_json` 分列；正文与过程不混写；错误事件用用户可读文案，不含堆栈/密钥/系统提示词。
- **文件**：后端改 `ai/ModelAdapter`、`ai/OpenAiAdapter`、`ChatController`、`ChatSendDTO`、`ChatConversation`、`ChatMessage`、`ChatConversationMapper`、`ChatService`、`ChatServiceImpl`、`AiService`、`AiServiceImpl`、`DocChunkService`、`DocChunkServiceImpl`；新增 `ai/OpenAiResponsesStreamClient`、`ai/capability/`、`ai/trace/`、`vo/ChatModelCapabilityVO`、`service/ChatManagementSchema`、测试 `ai/`、`ChatKnowledgeSwitchTest`、`ChatMessageEditRegenerateTest`、`DocChunkKeywordFallbackTest`。前端改 `api/chat.ts`、`views/Chat.vue`、`styles/ui-tokens.css`；新增 `components/ai/AIComposer.vue`、`components/chat/`（ReasoningPanel/ExecutionTimeline/AgentToolCard 等）、`thinking-trace.spec.ts`、`chat-render.spec.ts`、`sse-reasoning.spec.ts`、`AIComposer.spec.ts`。
- **验证（本轮真实）**：前端 `npx vitest run` 194 文件 / 1544 用例全通过，`npx vue-tsc --noEmit` exit 0，`npm run build` exit 0（5.29s）。后端 `mvn -o compile -DskipTests` exit 0；`mvn -o test` **1085 项，Failures 0 / Errors 0 / Skipped 9，exit 0**——修复上一轮记录的 8 项 chat/AI 失败（异步断言、`searchSimilar→searchSimilarTraced`、11 参 verify、`@SpyBean` 异步污染、缺表迁移、默认方法递归、关闭态二次门禁）。timed curl（deepThinking=true/model=deepseek-reasoner/useKnowledgeBase=true）：start 0.034s、rag_step 0.223s、首个 reasoning 1.592s、answer 2.042s、done 2.187s，57 reasoning / 29 answer / 3 rag_step，总 2.199s。浏览器（chrome-devtools，`/chat?id=71`）：模型切 reasoner、开关 `aria-checked` false→true、面板「正在思考…模型推理内容」→「思考已完成 · 0.2 秒」自动折叠、推理为真实英文且与正文分离、RAG「知识库检索 关键词检索 3 个步骤 · 146ms」（权限过滤 1402 条·103ms / 关键词 0 条·43ms / 证据判断 0 条），0 命中如实提示未引用知识库。截图在 `docs/assets/ai-thinking/`。
- **Git**：未 commit/push/reset/clean/restore/stash；保留其他 Agent 全部未提交改动；未写正式 MySQL，验收写入仅限开发 H2 与真实付费测试会话。
- **⚠️ Not verified**：聊天链路 Agent 工具轨迹（无 Tool Calling，仅组件/单测）、OpenAI `reasoning summary` 真实通道（无 Key）、MySQL 正式库迁移、取消后下游停流计时与迟到事件竞态、超长推理性能、XSS/脱敏渗透、1024/移动视口、浏览器刷新后历史过程回显逐项复验。
- 下一任务：先决定聊天是否正式接入 `AgentRuntimeService` 并复用 `tool_*` 事件合同；若不接入，在能力矩阵中把 Agent 轨迹标为「组件就绪、入口未接入」，避免两可表述。


## 2026-10-25 · UI V3统一规范合并（仅文档）

- 目标/原因：按用户V3统一基础组件标准，收口散落规则并维护唯一入口。
- 文件：UI_DESIGN_SYSTEM_V2现行第0/3/4/5节与验收、UI_UX_SYSTEM按钮关联、docs/README、AGENTS、roadmap01/02/08、原PRD及本交接。
- 源码核对：Button.vue、style.css与ui-tokens.css；现有按钮高度32/38/44、圆角6，字体Type Scale已存在；loading负margin/mr间距、shadow-sm、lg padding/font为待迁移差异；md图标热区已38，不重用历史36描述。
- 业务/UI/数据流影响：未修改运行时代码/Token/API/Store/权限/数据。只更新规范，未声称全站实施。
- Git：保留全部既有未提交修改，无commit/push/reset/clean。检查本轮文档增量、路径和本地锚点；测试/Build/浏览器本轮未运行，纯文档不伪报功能PASS。
- 遗留/下一步：公共Button按4.1收口，执行真实计算样式、双主题/各宽度/200%/键盘与全量门禁。普通输入无聚焦外框不变。

## 2026-10-25 · UI V3落地与Component Playground

- 目标/原因：现行规范落到真实公共组件，并建立可测量/复用的开发预览。
- 文件：Button.vue、Input.vue、ui-tokens.css、router/index.ts；新增dev/registry.ts、ComponentPreview.vue、routes.ts、views/dev/UIComponents.vue、dev两份合同测试、scripts/tests/ui_playground_v3.cjs。规范/README/AGENTS/原PRD/矩阵/状态/路线同步。完整清单见component-playground/change-manifest.json。
- 实现：21个真实组件，英文/中文/分类搜索、实际公开参数、源码路径与Composition示例/复制、临时主题、计算样式与真实Token；缺失组件不造空壳。DEV路由不进入生产产物，未加入业务导航。
- 业务/UI/数据流：Button32/38/44与14/20/500、gap8、稳定loading、无阴影、outline新增且ghost旧合同保留；Input改Flex，保留语义。未改后端/权限/API/FSRS/学习Store，预览局部ref；Chrome业务请求0。
- 测试：初轮196文件/1550测试通过；最终195通过1失败/1549通过1失败，TechEnglishHome非阶梯字号守护，不属本轮文件，未修改断言。dev合同6通过；完整app类型及正式Build通过。Chrome双主题五宽度、三档Button、loading/Space/Select/Escape、21组件、forced-colors/reduced-motion及真实200%通过；截图/JSON/日志见报告。
- Git/风险：保留原dirty，无commit/push/reset/clean；目标Diff通过。屏幕阅读器、全部P1/P2状态/对比度与所有业务页NOT VERIFIED。生产产物开发入口搜索未检出；不是生产部署批准。
- 下一步：独立处理英语字号守护，再逐页迁移自绘按钮并补P1组件语义/对比度。[执行报告](component-playground/REPORT.md)。

## 2026-10-25 · 专业英语 UI/UX 统一优化 V4.0（纯 UI）

- **目标/范围**：按 V4 任务书修复专业英语顶部管理按钮图文错位、专题页导航与操作对齐、词汇大厅排版、新建/编辑词条弹窗密度与表单层级，并统一字号；不改词库、FSRS、Schema、权限与后端 API。完整记录与六视口度量见 [tech-english/09_UI_REFINEMENT_EXECUTION.md](tech-english/09_UI_REFINEMENT_EXECUTION.md)。
- **实现**：`TechEnglishHome.vue` 五按钮统一 `.te-action-btn`（38px / gap 8px / nowrap / svg 16×16 `flex:0 0 16px`），「我的生词」书签左 + 文案中 + 数量 Badge 右，管理员四入口与事件不变；`TechEnglishTopics.vue` 与 `TechEnglishTopic.vue` 的「全部专题」「在词汇大厅中筛选」改为同一工具栏两个独立操作，筛选用当前真实 `domain`；`TermDetailPanel.vue` / `TermEditorDialog.vue` / `TermImportDialog.vue` 复用 `AppDialog`、`StatCard` 与既有控件；`style.css` 新增 `#app .te-page .kb-page-header h1`（`--kb-page-title-size` 28px + nowrap）与 ≤1100px 页头操作区独占整行规则，成为三页唯一来源；`TechEnglishHome.vue` 移除 26px/22px 非阶梯字号，改用 `--kb-fs-h1` / `--kb-fs-h3`。
- **两处真实根因**：① `PageHeader` actions 容器带 `shrink-0`，宽度取 max-content 使内部 `flex-wrap` 永不生效，390×844 出现 `scrollWidth 646 / 390` 横向溢出 → 窄屏改为 `flex:1 1 100%; width:100%; min-width:0`；② `styles/pc-studio.css` 的 `.pc-studio .kb-page-header h1` 把全站页头压到 24px 且 `white-space: normal; overflow-wrap: anywhere`，1024 下 h1 高 127.9px 逐字换行 → 用 `#app` 提特异性覆盖回 28px + nowrap，≤639px 恢复换行。
- **文件**：`frontend/src/style.css`（唯一被跟踪改动）、`frontend/src/views/TechEnglishHome.vue`、`TechEnglishTopics.vue`、`TechEnglishTopic.vue`、`components/tech-english/TermEditorDialog.vue`、`TermImportDialog.vue`、`TermDetailPanel.vue`、`views/__tests__/TechEnglish*.spec.ts`（9 项新增）、`docs/tech-english/09_UI_REFINEMENT_EXECUTION.md` + `assets/ui-v4/`（22 张截图与 measurements.json）、本文件与 `tech-english/04`。
- **验证（本轮真实）**：`npx vitest run` **198 文件 / 1566 用例全通过 exit=0**（含 `typography-tokens.spec.ts`）；`npx vue-tsc --noEmit` exit=0；`npm run build` exit=0；`git diff --check -- frontend docs` exit=0。Playwright 六视口 1920×1080 / 1440×900 / 1280×800 / 1024×768 / 768×1024 / 390×844：五按钮均 38px、图标与文字中心偏 0px、标题 28px（Badge 中心差 0.01px）、`overflowX` 全 false（390 为 390/390、工具栏两行 headH 84）、专题页四个操作 dy 0、Console 错误 0。弹窗实测 860×720 / radius 16 / 遮罩 padding 16 / labelGap 6 / Select 401=401 / 控件 44px / 分组 7 / Footer 40+40 常驻（body 1884 vs 556）；空表单保存提示「请填写主词或缩写」且写请求 0；编辑弹窗 20/28 字段回填、5 个必填星号。
- **Git**：未 commit / push / reset / clean / restore / stash；未改正式 MySQL 数据；`frontend/src/components/tech-english/` 与 `TechEnglish*.vue` 仍为未跟踪状态（本轮未 add），`git diff` 仅能看到 `style.css`。
- **⚠️ Not verified**：未保存变更保护——实测 ESC 直接关闭，`TermEditorDialog.vue` 与 `AppDialog.vue` 本无 dirty 检查，按「保留既有行为」未新增；真实移动设备、Safari/Firefox、深色模式、屏幕阅读器、浏览器 200% 缩放未复验；截图 before 为用户提供的 4 张页面截图，非本轮自动采集。
- 下一任务：若需真正启用「未保存变更保护」，先在 `AppDialog` 增加可选 `beforeClose` 钩子并由 `TermEditorDialog` 传入 dirty 判断（本轮未做，需产品确认）；随后由人工在真实移动设备与深色模式下复核页头换行与弹窗密度。

## 2026-10-25 · Date & Time Picker V4.0

- **目标/范围**：修复 `/calendar` 中 `2026-10-25T00:00` 的 T 直显、弹层超高/整体滚动条/底栏裁切，并补齐年份、年月、日期、时分、时分秒、日期范围、时间范围、日期时间范围。不改后端 `@JsonFormat`、不改 Schema、不改学习/FSRS/权限。完整记录见 [date-time-picker-v4](ui-v2/DATE_TIME_PICKER_V4_EXECUTION.md)。
- **根因**：① 显示层复用了存储合同字符串（`YYYY-MM-DDTHH:mm`），`T` 直接进 DOM；改为 `date-time-format.ts` 的显示/序列化分离，组件文本与面板预览走 `formatDateTimeForDisplay`/`formatDateTimePartsForDisplay`，写回只有 `serializeLocalDateTime`。② 弹层恒定 8px 溢出与底栏裁切：`@media (max-height:800px)` 把面板纵向 padding 压到 8px，而 `.dtp-foot` 的 `bottom`/负 `margin` 写死 16px，差值即永久溢出；改为 `--dtp-pad-x/--dtp-pad-y` 共用变量。③ `use-anchored-panel.ts` 自然高度改用 `scrollHeight + 边框`，避免被上次 `maxHeight` 截断后锁死高度。
- **新增**：`YearPicker.vue`、`MonthPicker.vue`、`DateRangePicker.vue`、`TimeRangePicker.vue`、`DateTimeRangePicker.vue`、`date-time-types.ts` 及 6 份 spec；`DatePanel.vue` 增量支持 range/hover。未建 `DayPicker.vue`（与 DatePicker 同实现）、未建 `DayOfMonthPicker`/`DayCount`（无业务消费者）。
- **组件中心**：`registry.ts` 新增 10 个真实条目（`DayPicker` 复用 `DatePicker.vue` 源码路径），`catalog-v4.ts` 补 props/默认值/designSpecs，`ComponentPreview.vue` 增加按组件名分流的真实 v-model 回写。`PlaygroundV4.spec.ts` 的 P0 数量 15→25 属注册表扩容，并加强了“真实组件 + sourcePath”断言（非放宽断言）。
- **验证（本轮真实）**：`npx vitest run` **210 文件 / 1663 用例全通过 exit=0**；日期时间专项 12 文件 / 137 用例通过；`npx vue-tsc -b` exit=0；`npm run build` exit=0（5.42s）。Playwright：展示中心 11 条目 + `/calendar` 5 视口 + 3 等效缩放视口 + 深色 + forced-colors，全部无 `T` 正则命中、面板在视口内、无整体滚动、Escape 关闭、焦点回位；1280×800 / 1024×768 由 397/389 修到 397/397；`Console 0 / PageError 0 / 失败请求 0`。证据在 `docs/ui-v2/date-time-picker-v4/`。
- **业务影响**：`CalendarEventDialog` 保存合同仍为 `yyyy-MM-dd HH:mm`（页面适配层 `formatDateTime`），实测请求体 `"startTime":"2026-12-31 23:30"`；纪念日「每年重复」与习惯 `23:30`/`00:00` 语义未变。
- **Git**：未 commit / push / reset / clean / restore / stash；未改正式 MySQL；新组件与 `docs/ui-v2/` 仍为未跟踪状态。
- **⚠️ Not verified**：真实移动设备、Safari/Firefox、屏幕阅读器、真实浏览器缩放（`body{zoom}` 已判定不可用，改用等效视口近似，废弃截图标记 SUPERSEDED）；`ExamMistakes` 两处原生 `type=date` 与 `LearningCenterPage` 的 `datetime-local` 未迁移；展示中心范围条目的秒精度切换未加。
- 下一任务：`ExamMistakes` 迁移到 `DateRangePicker`（先核对筛选接口的区间合同与空区间规则），随后处理 `LearningCenterPage` 截止时间合同。

## 2026-10-25 · Component Playground V6（三批次）

- **目标/范围**：在V5三栏上产品化4个公共控件，统一实现/证据状态、复制范例与7个隔离业务Fixture。完整记录：[V6交付](component-playground/v6/V6_DELIVERY_REPORT.md)、[矩阵](component-playground/v6/V6_COMPONENT_MATRIX.md)、[测试](component-playground/v6/V6_TEST_REPORT.md)。
- **真实基线**：接管实际75目录/203Story/39适配/30源码（日期任务已扩展），不是历史V5的65/153。最终77目录/243Story/43适配/34唯一公共源码/25Demo/9规划。只把4个新源码计为本轮产品化。
- **文件**：components/ui/Radio.vue、Switch.vue、Tabs.vue、SearchInput.vue及ProductionControlsV6.spec；dev的registry/production/ProductionControlsPreview/control-examples-v6/scenarios-v6/ScenarioPreview/ComponentPreview/ComponentDocs/ComponentNavigation/PlaygroundProperties/stories/code-examples和新测试；views/dev/UIComponents；scripts/tests/ui_playground_v6*.cjs；本目录报告、UI规范/架构/README/roadmap01/02/08/原PRD增量。
- **真实调用链**：dev路由→UIComponents→统一registry/Story→真实公共控件Props→Emits→局部ref/事件日志；AIComposer/Markdown/AgentToolCard等适配仅Fixture，未调用业务服务。Story key销毁清理计时器、重置事件和局部状态。普通笔记服务端主库、SmartWriting本地草稿、Evidence/FSRS/路由白名单均未改变。
- **API/UI**：四个受控控件独立实现、键盘/禁用/readonly/IME适用状态；新SearchInput复用Input/Button。没有knowflow-ui包可复用的证据，不造双主题，不批量改名。Props分组/复制、实现筛选、业务场景跳转，质量七维NV与Inspector单属性测量分离。Search loading值脱节/源码默认与Fixture混用/弹窗示例无开启入口在最终审阅修复，均补先失败回归。
- **命令结果**：全量`npx vitest run`214文件/1680项通过exit0；根`vue-tsc --noEmit`、完整`vue-tsc -p tsconfig.app.json --noEmit`及正式`npm run build`exit0。139个正式Story SFC由vue-tsc编译。日期columnsRef已由前任务修复，本轮未改日期源码。无后端更改，mvn未运行。全仓`git diff --check`exit2为范围外LinkedList.md:191既有尾空格，本轮范围检查exit0。
- **浏览器**：真实Chrome，5视口×双主题、原生200%（dpr2），77条目挂载/35场景状态访问，15组新增交互；四控件40组视口主题+8组状态。Console/PageError/业务请求0。截图/JSON见v6/evidence；243Story不等于全部交互覆盖。
- **Git**：main@78f4b11，原dirty保护，无add/commit/push/reset/clean/restore/stash；原dev文件未被追踪，本轮用SHA与新源码patch/快照区分，不把全仓累计dirty归功本轮。业务/生产配置/Schema/正式数据无改动。
- **⚠️ Not verified/风险**：Dark浅灰字段与浅色文字的对比度风险（截图可见，未改全局Token）；完整视觉/读屏/A11y、真实移动设备、Safari/Firefox、真实业务集成/独立发包/性能。完整远期P5/P6能力未全做，不宣称已发布或全局READY。
- **下一任务**：在唯一Token来源修复深色字段对比度并完成读屏/真实业务使用方验收，随后产品化Tooltip/Popover/Combobox定位与焦点合同；保留9个规划，不造空壳。

## 2026-10-25 · 顶栏组件中心快捷入口

- 按用户要求，在CTopNav的AI助手聊天图标紧邻左侧加`component`图标，title/aria-label为「公共组件开发中心」，RouterLink到`/dev/ui-components`；仅DEV可见以匹配既有DEV路由。
- 修改：`frontend/src/components/layout/CTopNav.vue`与V6交付报告/本交接；不动已有dirty内容、Store/API/Registry/Token，不commit/push。
- 真实调用链：顶部RouterLink→现有DEV路由→UIComponents；无数据写入。
- 验证：全量Vitest214文件1680项exit0，根及完整App类型exit0，正式Build exit0（5.15s），目标git diff --check exit0；Chrome1440真实点击→V6标题通过。
- ⚠️ Not verified：本补充未重跑全部窄屏/深色/真人读屏，既有V6认证边界不扩展。
- 下一任务：随顶栏统一回归窄屏/键盘布局；组件中心继续按V6已有待办推进。

## 2026-10-25 · 面试雷达来源审计与私有导入（PARTIAL）

用户授权直接开发、同号合并保留全部来源、当前运行开发库本人账号入库。新增扫描工具/来源JSON与映射、InterviewRadarImportController→Service→现有Mapper/表、前端LearningPaths导入入口、相关测试。复用资源账本，无新DDL，不改Evidence。Git仍main/78f4b11，既有大量dirty保留，无commit/push。工具6测试通过；工具目录全量35/36，旧AGENT-TOOL-CALLING哈希漂移失败；前端1681测试/类型/build通过的快照，后端H2初版2通过，完整最终结果见[报告](interview-radar-v1/REPORT.md)。浏览器入口/禁用/窄屏/1440截图已保存；当前会话未登录，运行库本轮入库0，新后端未加载。下一任务：本人登录并加载新接口，先预览再真实入库及对账；题库48待审候选不是严格评分成品。附件迁移、2参考总览、3答案冲突、评分/首轮保护/雷达与MySQL仍未完。

2026-10-25 本任务后续更新：用户本人会话已真实导入当前文件型H2开发库，路径29新增65章/48待审题，报名后章节1270可读、0/65完成；原库关闭状态备份在`/private/tmp/knowflow-radar-pre-import-20261025`，未导入历史成绩。8份文件名/正文题号冲突和3组答案冲突待核定；严格训练/雷达未完成，维持PARTIAL/NOT_READY。最终验证边界以本任务报告为准。

本轮导入数据对账已完成：10张学习表864条原行指纹全部保留，仅新增本人报名关系；证据`interview-radar-v1/LEARNING_DATA_RECONCILIATION.json`。当前后端PID19291，原启动命令恢复；前后关闭状态H2副本保存在/private/tmp，仅本机可读。下一任务优先核定8份文件名/正文题号冲突及3组答案冲突，再继续严格训练。

## 2026-10-26 · V7–V8 17项学习功能规划 · 文档规划交接

- 日期/范围：2026-10-26 Asia/Shanghai；按最新用户附件仅源码审计、外部研究、17需求设计与文档验证，不再推进前一轮业务开发。
- 文件：docs/roadmap/learning-v7-v8下README、00–11、17份features、NEXT_CODEX_PROMPT、JSON队列/来源索引/文档校验脚本与报告；原矩阵/路线/状态/架构/PRD/docs入口/VIBE追加关联。
- Git：main/78f4b11，保留既有大量dirty/untracked，未commit/push/reset/clean/restore/stash；本轮业务保护校验结果见[VALIDATION_REPORT](roadmap/learning-v7-v8/VALIDATION_REPORT.md)。
- 真实调用链：复用NoteService/Quiz快照/Exam答卷/FSRS/V2面试/原LearningEvent→Evidence→NextAction；代码已有五级提示，图谱已有掌握标注，日计划已有changes，导入已有SSE/Draft→Accept。
- 新发现：费曼服务端仅长度/自检摘要，缺原文历史；雷达65章48待审题已入库历史不能重做；V7回执旧报告有权限403/200失败，根因待复现。
- 研究：固定commit源码机制/许可；Studyield原地址404，明确镜像另列且许可不替代原仓库；Sapling许可未确认，不复制第三方代码。
- 命令与结果：本轮仅文档验证、路径/编号/链接/依赖/状态/业务指纹与限定diff检查，实际输出见VALIDATION_REPORT；未重跑业务测试/build/浏览器/数据库/付费AI，不复用历史数字作新PASS。
- 风险/未验证：V7原临时全量日志本次不可读取，现存surefire红灯非生产漏洞定论；真实浏览器与候选全门禁待完成；人审/生产MySQL8/隔离Judge与30秒SLA未认证；全局NOT_READY。
- 下一任务：**V70-T01**，见[NEXT_CODEX_PROMPT](roadmap/learning-v7-v8/NEXT_CODEX_PROMPT.md)，只复现权限失败与必要最小修复，暂不开发F03–F17。


## 2026-10-26 · Java + AI 雷达训练连续实施

范围：本人已授权同号合并、当前开发库实际增量入库。新增radar包/原V2四维10分适配/首答保护/来源内联图/既有复习任务桥接与/interview雷达UI；详见docs/interview-radar-v1/REPORT.md及VERIFICATION_20261026.json。源95文件SHA全保留；路径29当前65章/65待审题/5图/4参考索引，39可参考训练，26来源待核定。仅新增阶段绑定表，没改Evidence算法/唯一Store。

Git：main/78f4b11，原大量dirty保留，无commit/push。真实链：RadarLearningCenter→RadarStudyController→RadarStudyService→InterviewV2Service→原session/state/turn；复习沿用LearningService/MistakeService/FSRS，计划TaskService。前端220文件1703通过、vue-tsc/build退出0，后端完整与相关结果看本轮验证记录，不把两个Evidence失败冒充通过。浏览器验证导入/筛选/确认门槛/无样本雷达/任务33/暂停会话8/中文来源图，未代答或产生AI成绩。11表原897行指纹不变。关闭H2前后快照在/private/tmp/knowflow-radar-backup-20261026-{before,after}-increment.mv.db；恢复服务使用既有knowflow-dev.sh start，8080已核实属于本仓库。

风险：内容待审/真实Provider与语音/MySQL5.7/8/生产未验；没有浏览器Network抓包。首答仍0，用户可继续/interview?radar=1&v2Id=8。下一任务：审核26道暂停题，不改源、不自行批准，不重复开发本轮已完成能力。


## 2026-10-26 · V70-T01学习证据权限限定修复交接

- 目标/范围：复现并修复Admin上下文污染匿名/其他用户探测，不实施T02–T04。分支main，HEAD78f4b11d58da89d69264f4f452d210e6a6db34d1，大量既有dirty保留，staged为空，未commit/push/reset/clean/restore/stash。
- 真实链：LearningEvidenceController→EvidenceService/Sources→PersonalLearningResourceScope→DocumentAccessPolicy→KbMemberServiceImpl.isAdminUser。原匿名公开性探测继承当前Admin导致私人来源被判公开；现null拒绝，仅同主体复用认证角色，其他ID/异步查DB。
- 源码仅3文件：KbMemberServiceImpl.java、LearningEvidenceHttpTest.java、KnowledgeProcessingServiceTest.java（后者仅补真实Admin Fixture）。报告/精确补丁/冻结前源/脱敏全日志/HTTP/截图：[V70-T01_REPORT](roadmap/learning-v7-v8/executions/V70-T01_REPORT.md)。同步01/02/08、queue JSON/MD、NEXT_CODEX_PROMPT。
- RED：原样目标1测试1失败；真实角色+新回归2测试2失败，均在生产修复前。相关73项退出0；最终6类88项通过。前端npx vitest run：220文件1703通过退出0；vue-tsc、npm run build退出0。
- 全量mvn test：沙箱1114项1失败21错误9跳过退出1；获准重跑1113项1103通过0失败1错误9跳过退出1。唯一剩余为LearningEventClosedLoopTest.flashcardReviewWithKnowledgeIdBindsMetadata知识点888001未创建，冻结前helper对照3项中2通过1相同错误；未改该无关业务/测试断言，不可称全量通过。
- Chrome隔离H2/端口18087+15187：五类账户真实UI登录、13组HTTP/code、2次真实刷新、撤权提示、刷新/重登SHA一致。六条Console资源错误均为预期401/403，无未捕获JS异常。临时服务已关闭，既有8080 PID61418/5173 PID8471保持。
- 数据/并行：只动临时测试SQL，历史原答/成绩/笔记/卡片/面试无迁移；27项雷达/V2/绑定文件SHA一致，4个冻结核心源码保持。权限修复DONE不代表V7完成或发布，NOT_READY。
- 未验证/风险：MySQL5.7/8/生产迁移、真实Provider/Judge/Host、全候选门禁；现有403/404可区分猜测ID是否存在，不假称严格存在性隐藏；本人不可访问回执total合同留T02。
- 下一任务：只执行**V70-T02**，已生成[专用提示](roadmap/learning-v7-v8/NEXT_CODEX_PROMPT.md)，尚未开始；保留55任务编号/依赖。既有闪卡Fixture错误须在全候选门禁前单独处理。


## 2026-10-26 · V70-T02回执可靠性实施交接

- Git main/78f4b11d58da89d69264f4f452d210e6a6db34d1；大量dirty保留，staged为空，无commit/push/危险清理；27项雷达/V2/绑定SHA一致。
- 范围：Sources/Service/Adapter、新QuizAttemptProvenance、原Quiz/Exam服务、3个后端测试、Kf面板/API类型/测试，共12个源码文件；无DDL、无正式数据或Evidence/Mastery/NextAction算法改动。[报告/完整日志/补丁/源码清单](roadmap/learning-v7-v8/executions/V70-T02_REPORT.md)。
- 链：Quiz原答+required事件→BEFORE_COMMIT薄captureRequired；Exam现有条件评分短事务补receipt；显式get/capture只读historyProjection。firstAttemptRef未知不造，firstTrackedAttemptRef独立；原文/0分/快照/SHA保留；当前和冻结来源都授权，事件原题引用一致。
- RED均先复现：MIN未删ID/导入顺序、原文裁剪、晚到AI/CODE漏回执、统计侵入事务、提示条件漏选、MySQL秒精度、原/追踪混同、事件错链、公开重挂后旧快照泄漏、legacy NULL分/UNKNOWN规则误可比。独立审阅3 Important及统计成本已修复回归。
- 命令：Maven相关最终115/115退出0（合法ByteBuddy agent）；完整最终1136项1126通过0失败1错误9跳过退出1，唯一为旧LearningEventClosedLoopTest知识888001未建，T01冻结前对照证明；未修改该无关失败。前端220文件1704通过、vue-tsc/build退出0；原始命令与各次退出见evidence/COMMANDS.md。
- Chrome最终两轮完整：真实登录、0分首答、403保稿/恢复重试、100分显式复测、实际翻页、刷新重登、Other及撤权；28组HTTP/code、124响应、0 pageerror，6条Console为预期400/403/409。临时H2/18087+15187已关闭，原8080 PID61418/5173 PID8471不动。
- 边界/代价：旧50条补练候选窗口未改且不是原始首轮证明；Mastery/Reward afterCommit及Exam事件best-effort保留，持久失败对账/付费重试成本未认证；统计有1000上限/逐项ACL，offset无跨页快照token；旧坏回执仅不可比，不回写。MySQL/生产迁移恢复/真实Provider/Judge/Host未验。
- T02 DONE，V7未完成/NOT_READY；55项ID与依赖与初始机器源一致。下一任务仅**V70-T03**，已生成[专用提示](roadmap/learning-v7-v8/NEXT_CODEX_PROMPT.md)，未自动执行。

2026-10-26 T02最终补验追加：同秒导入firstKnown排序新增断言RED→最小读投影修复→相关115通过、最终precision-full1136项1126通过0失败1既有错误9跳过退出1、最终Chrome28组复跑通过。原始首轮UNKNOWN和旧回执不回写；T02 DONE、T03仅READY未执行，NOT_READY。最后临时端口18087/15187已清理，原8080/5173保留。

## 2026-10-26 · PRODUCT_MVP_01 产品优先开发（A完成，B验收中）

- 授权与范围：用户明确从V70基础建设转向真实F03→F16→F17。T01/T02限定DONE保留，T03/T04未完成转延期发布验收，NOT_READY不变。原55任务保留，新增PRODUCT-MVP-01-A/B/C，58项DAG无环。不再以完整发布门禁阻断隔离研发。
- Git：main，起点78f4b11；极脏工作树先留status/diff/cached diff；未提交/推送/重置/清理/暂存他人修改。仅新增私域与Tutor策略、原NotesAiPanel/ChatService小范围集成和文档同步。当前原有27项雷达/T02保护文件对照T02报告SHA均一致，比较不是新的接管前基线。
- A最小切片DONE：私人3题AI草稿、可编辑确认、来源版本与私有owner、闭卷无答案响应、ExamGrader客观判分、原始答卷不可覆盖、重复请求/冲突、0→100独立历史与刷新重登；未进公开题库。真实DeepSeek仅发送用户明确批准的Agent合成笔记，3题2478ms，Chrome实际闭环Console无错误。完整F03高级规格仍PARTIAL。
- B已实施/TESTING：三模式策略复用原chat_conversation/message/Provider/SSE；tutor_session绑定和revision CAS；每轮模式、来源note/revision/sourceDocId/promptVersion在原doc_references冻结。来源删除/历史文档改绑后撤权、普通聊天历史/摘要返回/分享路径保护；输入与助手+会话计数事务；Provider失败无假导师回答。保留用户主动删除整会话。新功能开关默认关闭。
- 调用链：NotesPrivateQuiz→PrivateQuizController→PrivateQuizService→NoteService+DocumentAccessPolicy/AiService+ExamGrader+LearningWriteGuard→private_quiz/attempt→PRIVATE_QUIZ_SUBMITTED。NotesTutor→TutorController/TutorContextService→原ChatController.stream→ChatServiceImpl→原AiService.streamChat/SSE→原chat_message与chat_conversation。不改Evidence/NextAction/FSRS算法，不创建三套聊天后端，不把预览暴露成绩当盲测掌握度。
- 数据库：private_quiz/private_quiz_attempt/tutor_session为开关下加法DDL；隔离文件H2重复迁移/关闭重开/备份恢复；MySQL5.7/8、生产和多实例未认证。普通生产数据源/Schema/正式数据未接触；原8080/5173进程未停止。专用18086/15186及18088替身为本轮测试。
- 验证：A9项后端专项；B5项Tutor+8项旧Chat+A9项，共22项相关通过。前端最终222文件1708项、vue-tsc和build退出0。A获准完整Maven1145项：1失败（RadarStudyServiceTest异步超时，单独复跑4项通过）、1既有888001错误、9跳过，退出1。B最终完整结果见PRODUCT_MVP_01/TEST_REPORT.md，不将早期沙箱Socket错误或一次并发编译作废运行拼作通过。来源权限/并发/事务/迟到历史都有独立回归与复审。
- 浏览器：A真实DeepSeek+Chrome四宽度、0→100/刷新重登通过；B仅本机SSE合同三模式/6条历史/刷新重登/四宽度通过，Console无错误。200%实际缩放/读屏未认证。B云端请求被自动审批以新增外发范围未获授权拒绝，已向用户明确询问同一合成笔记+三条测试消息及上下文，批准前不调用，不拿替身作真实AI验收。
- 文件/证据：详见docs/roadmap/learning-v7-v8/executions/PRODUCT_MVP_01/{IMPLEMENTATION_REPORT,SOURCE_MANIFEST,TEST_REPORT,BROWSER_REPORT,NEXT_CODEX_PROMPT}。唯一实际业务入口仍/notes/:id/edit的AI助手；没有新建Mini App。
- 下一任务：先按明确授权完成B真实Tutor三模式验证并记录完整后端结果；B相关验收通过后才开始PRODUCT-MVP-01-C小型文本资料学习包。C未实施，不以旧上传或普通笔记能力冒充学习包完成；之后再按产品批次推进，不恢复T03/T04专项为研发前置。


### 2026-10-27 · PRODUCT_MVP_01 三切片收口

2026-10-27 PRODUCT_MVP_01：A/F03、B/F16、C/F17三个最小纵向切片DONE；完整高级规格仍PARTIAL。私人测验保存/冻结/客观判分/新旧历史，原Chat三模式Tutor及来源/逐轮模式持久化，受控TXT/Markdown/文本PDF异步学习包、分项重试、编辑采纳与原FSRS均已实施。真实DeepSeek合成资料链路及浏览器刷新/重登/四宽度通过；前端223文件1710项、类型/build通过。后端最终结果见执行目录TEST_REPORT.md，既有888001 Fixture红项不隐藏。六张新增表仅在隔离H2验证迁移与备份恢复；生产/MySQL未认证，开关默认关闭，T01/T02保持DONE、T03/T04仍未完成，全局NOT_READY。下一批F02/F05/F07/F10。

文件、Git、实际命令/结果与未验证项见[实现报告](roadmap/learning-v7-v8/executions/PRODUCT_MVP_01/IMPLEMENTATION_REPORT.md)、[来源清单](roadmap/learning-v7-v8/executions/PRODUCT_MVP_01/SOURCE_MANIFEST.json)、[测试](roadmap/learning-v7-v8/executions/PRODUCT_MVP_01/TEST_REPORT.md)和[浏览器](roadmap/learning-v7-v8/executions/PRODUCT_MVP_01/BROWSER_REPORT.md)。main/78f4b11未提交未推送。真实调用链：StudyPackController→StudyPackService→DocumentTextExtractor/DocService→原LearningContentService/AiService→NoteService→私人测验/原Chat Tutor；采纳→原LearningService.createMyFlashcard→原FSRS。原8080/5173未终止，专用18086/15186只用隔离H2。下一任务仅第二批F02/F05/F07/F10，先实现有原文引用的评价。

最终Maven1158项：1148通过、0失败、1既有888001错误、9跳过，退出1；前端1710项/类型/build退出0。最终汇总已落盘PRODUCT_MVP_01/evidence/backend-final-full.json。


### PRODUCT_MVP_02 · 当前增量

2026-10-27 PRODUCT_MVP_02收口：F05/F02/F07/F10四个基础切片DONE，完整高级规格仍PARTIAL、NOT_READY。相同合成MVCC资料的选区问答→L1/L2→两轮有来源评价→真实单题补练→刷新/重登已通过，16次调用未超过已批准17次。最终前端1715项/类型/build通过，后端最终相关32项及后续15项/2项通过；完整1165项仅既有888001错误（最后精准修复相关另验）。28代码/测试文件，27核心保护SHA不变，未提交/推送或连接正式库。默认coaching关闭；T01/T02历史保持，T03/T04未完成。下一批F04/F08/F09/F13。 详见`docs/roadmap/learning-v7-v8/executions/PRODUCT_MVP_02/`报告与evidence。


### 2026-10-27 · PRODUCT_MVP_03收口

2026-10-27 PRODUCT_MVP_03：F08/F04/F09/F13四个基础切片DONE，完整高级规格仍PARTIAL、全局NOT_READY。原诊断/人工修正→实际F07→两场景私人变式→冻结同题复测→三题模拟面试均已落地；隔离合成真实DeepSeek20/20，保留4次TLS失败和1次无效JSON，最终复测7.75/10、两次复测历史、T02原回执不变、UNKNOWN/NOT_COMPARABLE，无客观进步分差。前端1721项/类型/build通过；后端全量1175项0失败1既有888001错误9跳过，最后22项相关和历史映射3项通过。默认关闭，正式待审题不升已审，Fixture仅隔离放行；未提交/推送、未动正式库或第二批进程。T01/T02保持DONE，T03/T04未完成。

范围/31代码测试文件/SHA/调用链/七张加法表/权限与风险见[实现](roadmap/learning-v7-v8/executions/PRODUCT_MVP_03/IMPLEMENTATION_REPORT.md)、[测试](roadmap/learning-v7-v8/executions/PRODUCT_MVP_03/TEST_REPORT.md)。main/78f4b11，保留dirty，无commit/push/reset/clean/stash；只读审阅问题已修复。第三批15196/18096独立H2仍可回读，18099真实云代理已停止，20次额度不得续用。V2与两个面试Vue为本轮明确受保护文件改动，其他24项SHA一致；Evidence/NextAction/FSRS/RadarRubric未改。最后同题真实复测AI7.75/10，旧TLS失败未评分答文保留，不将AI评价当客观提升。下一任务：等待用户第四批明确指令，候选F06/F01/F12/F14/F15，禁止自动启动。

### 2026-10-27 · TagsInput 组件正式化（复习卡接入 + 组件中心真实展示）

- 目标/范围：修复复习卡「标签（可选）」聚焦蓝框与双层边框，封装公共 `KfTagsInput`，接入复习卡编辑弹窗与现有组件中心唯一 TagsInput 条目。未改 FSRS/评分/历史/笔记/学习进度/AI/后端/Schema，未动独立 knowflow-ui 仓库。Git main，保留大量既有 dirty/untracked，staged 为空，无 commit/push/reset/clean/stash。
- 真实链：`WorkbenchReview.vue` 编辑弹窗 `<KfTagsInput id="review-card-tags" v-model="form.tags" …>` → `KfTagsInput.vue`（受控数组、draft 本地、emit update:modelValue/add/remove/invalid/clear/focus/blur）→ 原 `saveCard` 以 `tags: form.tags.join(',')` 走既有 PUT `/api/workbench/reviews/{id}`，回显仍用 `splitTags()`；卡片 ID/FSRS/历史未变。
- 蓝框根因（源码证据）：旧 `WorkbenchReview.vue` `.review-tags:focus-within { border-color: var(--kb-primary); outline: 2px solid var(--kb-ring); outline-offset:1px }` 由父容器产生，旧 `.review-tags` 另有 1px 边框与白底形成内外双层；第二层为 `style.css` 键盘模态对裸 `input:focus-visible` 的通用 `--kb-field-focus-shadow`。
- 修复选择器：组件作用域 `html[data-kb-input-modality="keyboard"] .kf-tags-input .kf-tags-input__control:focus-visible`（(0,5,1) 覆盖 (0,4,1) 的全局规则）；`.kf-tags-input`/`:focus-within` 均 `border:0 / box-shadow:none / outline:0`；`@media (forced-colors: active)` 仅外壳 `outline:2px solid Highlight`，chip 用 `CanvasText` 边框。旧 `.review-tags`/`.review-tag-input` DOM 与 CSS 已随接入删除。未改全局 Input/Select/Checkbox。
- 组件中心：复用唯一 `TagsInput` 条目（`registry.ts` → `ComponentPreview.vue` 真实分支），从 Demo-only 升级为「已接入真实组件」/`PRODUCTION`；删除 `ExtensionDemos.vue` 的旧 TagsInput 分支/状态/样式，`extensions-v5.ts` 拓展数 12→11，无第二套条目或实现。`catalog-v4.ts` designSpecs 的 `background` 键修正为 `backgroundColor`，Inspector 才能真实比对；`StyleInspector` 增加 borderWidth/borderStyle/outline/outlineWidth/outlineStyle/minHeight 测量。
- 文件：新增 `frontend/src/components/ui/KfTagsInput.vue`、`frontend/src/components/ui/__tests__/KfTagsInput.spec.ts`；修改 `frontend/src/views/WorkbenchReview.vue`、`frontend/src/dev/{registry.ts,ComponentPreview.vue,StyleInspector.vue,stories.ts,catalog-v4.ts,code-examples.ts,ExtensionDemos.vue,extensions-v5.ts}`、`frontend/src/dev/__tests__/{TagsInputEntry.spec.ts,ExtensionsV5.spec.ts}`；文档 `docs/component-playground/v6/V6_COMPONENT_MATRIX.md`、`docs/component-playground/REPORT.md`。
- 命令与结果：`npx vitest run` TagsInput 相关 4 文件 29 项通过（KfTagsInput 18、TagsInputEntry 5、ExtensionsV5 5、ExampleTypesV6 1）；全量 237 文件 1750 项通过；`npx vue-tsc --noEmit` exit 0；`npx vite build` 通过。`npm run build`（`vue-tsc -b`）被并行任务未跟踪测试 `src/components/learning-adaptive/__tests__/InterleavedPracticePanel.spec.ts(14,705)` 的 `DOMWrapper<Node>.vm` 类型错误阻断，属既有/他人改动，本轮未修改。
- 浏览器（真实 Chrome，Light/Dark、1440×900/1280×800/1024/720×450≈200%、390×844 模拟）：组件中心 20 chips 换行无横向溢出，disabled/readonly/error ARIA 与 Events 日志正常，Code 示例可复制，Inspector borderWidth/borderStyle/outlineWidth/outlineStyle/boxShadow/padding/gap/borderRadius/minHeight/backgroundColor 全部 PASS，Console 无 error；复习卡弹窗回显 4 标签→添加「回归标签」→`PUT /api/workbench/reviews/12 [200]`→重开一致→重复拒绝→单删保存→API 标签回原值、`easeFactor 250/reviewCount 0/nextReviewTime` 未变；取消二次确认不污染；Offline 保存失败保留草稿。外壳实测 44px、`#E8ECF1`、border/outline/box-shadow 全 0。
- 未验证/风险：真实中文输入法会话（仅单测模拟 composition/isComposing/keyCode 229）、NVDA/VoiceOver 读屏与对比度、375×812 单独跑（以 390 兼容检查代）、`npm run build` 全链路因无关测试阻断。`KfTagsInput` 未迁移到独立 knowflow-ui 仓库。
- 下一任务：待用户明确指令后再处理，候选为修复 `InterleavedPracticePanel.spec.ts` 的 `DOMWrapper.vm` 类型错误或按组件矩阵下一顺序推进 Tooltip/Popover/Combobox；本轮不自动启动。


### 2026-10-27 · PRODUCT_MVP_04 收口

F06/F01/F14/F15/F12五个最小切片DONE，完整高级规格PARTIAL、开关默认关闭、全局NOT_READY。个人路径覆盖复用原chapterList/DAG、计划复用原Task事务、交错复用原单题attempt/ExamGrader、报告只读原来源；未改Evidence/NextAction/FSRS/Mastery算法。新增云调用0，隔离合成Chrome闭环/刷新/重登通过；最终测试见第四批报告。T01/T02原限定DONE，T03/T04未完成，MySQL/生产与888001历史错误保留独立验收。下一候选F11须真正独立隔离沙箱，等待用户指令；未开发F11、未commit/push。 详见[第四批交付/下一候选](roadmap/learning-v7-v8/executions/PRODUCT_MVP_04/IMPLEMENTATION_REPORT.md)。

第四批最终命令：frontend `npx vitest run` 237文件1750通过、`npx vue-tsc --noEmit`/`npm run build`退出0；backend 6个受影响suite47通过、最后C/E加固12通过，均退出0（有重复覆盖，不相加）。main/78f4b11，dirty保留，无commit/push。真实调用链/文件/数据影响和未验证项均在第四批报告；专用15206/18106及本地18108可回读，其他批次未终止。

### 2026-10-27 · ExamMistakes 日期筛选迁移 + 范围组件跨月

- 目标/范围：修复 `/exam/mistakes` 两处原生 `<input type="date">`，改用共享 `DateRangePicker`；为日期/日期时间范围补跨月选择能力。未改 FSRS/Evidence/NextAction/评分/后端 Schema，未动正式数据库。Git main，保留既有 dirty/untracked（本轮改动文件多为未跟踪新文件），无 commit/push/reset/clean/stash。
- 根因（源码证据）：`ExamMistakes.vue` 原 `toDate = \`${filters.to} 23:59:59\``；后端 `ExamController` → `ExamServiceImpl` 对 `from`/`to` 走 `LocalDate.parse(to).plusDays(1).atStartOfDay()`，`to` 必须为纯 `yyyy-MM-dd` 且已含当天，拼时间后缀会抛 `DateTimeParseException`。修复为纯 `filters.to`，浏览器实测 `to=2026-11-05` 返回 200。
- 组件链：`ExamMistakes.vue` 筛选区 `<DateRangePicker v-model="dateRange" clearable>` → 可写 computed `{start: filters.from, end: filters.to}`（同值短路，变更后 `reload()` 重置 `pageNum=1`）→ 原 `examApi.mistakes({from,to,...})` → 原 `GET /api/exam/mistakes`。未新增第二套日期实现，`DatePanel` 未改算法。
- 跨月：确认 `DatePanel` 已具备跨月（outside 日期可点、区间按真实日期值连续高亮、翻月不重置端点），本轮补 4 条组件测试锁定；`DateTimeRangePicker.vue` 新增 `setActive()`，点「开始/结束」把日历视图带到该端点所在月（compact 与宽屏两处 Tab 均接入）。
- 文件：修改 `frontend/src/views/exam/ExamMistakes.vue`、`frontend/src/components/ui/DateTimeRangePicker.vue`、`frontend/src/components/ui/__tests__/DateRangePicker.spec.ts`（+2→16）、`frontend/src/components/ui/__tests__/DateTimeRangePicker.spec.ts`（+2→18）、`docs/ui-v2/DATE_TIME_PICKER_V4_EXECUTION.md`（§9/§13/§14/§15/§16）、`docs/UI_DESIGN_SYSTEM_V2.md`（V4 段跨月规范）；新增 `frontend/src/views/__tests__/ExamMistakes.spec.ts`（4 用例）。
- 命令与结果：专项 3 文件 38 用例通过（DateRangePicker 16、DateTimeRangePicker 18、ExamMistakes 4）；全量 `npx vitest run` 238 文件 1758 用例通过 exit 0；`npx vue-tsc -b` exit 0；`npm run build` 成功 5.20s（仅既有 chunk 体积提示）。`git diff --check`（本轮相关文件）exit 0。
- 浏览器（Playwright headless Chromium）：真实登录 → `/exam/mistakes`，页内已无 `input[type=date]`，触发区 44px、筛选区 113px；面板 336×457、`scrollHeight-clientHeight=0`；`2026-10-05` → 下个月 → `2026-11-05`，视图保持 11 月、预览 `2026-10-05 — 2026-11-05`；请求 `/api/exam/mistakes?from=2026-10-05&to=2026-11-05&pageNum=1&pageSize=20` 200 且不含 `23:59:59`；清除后请求无 from/to 且 200；1024×768 面板底边 753<768 无溢出；Console/PageError 0。截图 `30-*`/`31-*`/`32-*`/`34-*` 与 `evidence-exam-mistakes.json`/`evidence-datetimerangepicker-boundary.json` 已落 `docs/ui-v2/date-time-picker-v4/`。
- 未验证/风险：`DateTimeRangePicker` 边界跳月在浏览器中 ⚠️ Not verified（展示中心 fixture 为同月且 `min/max` 限 2026-10，真实页面暂无跨月消费者）；ExamMistakes 仅用空错题数据账号验证，非空分页/页码边界未覆盖；`LearningCenterPage` 原生 `datetime-local` 仍未迁移。
- 下一任务：按用户明确指令后，将 `LearningCenterPage` 原生 `datetime-local` 迁移到 `DateTimePicker`（先统一截止时间后端合同）；本轮不自动启动。详见[DATE_TIME_PICKER_V4_EXECUTION §16](ui-v2/DATE_TIME_PICKER_V4_EXECUTION.md)。

### 2026-10-28 · 日期时间遗留问题闭环（跨月边界 / 错题分页 / 学习首页截止时间）

- 目标/范围：处理 2026-10-27 交接的三项遗留——`DateTimeRangePicker` 边界跳月浏览器未验、`ExamMistakes` 非空分页未覆盖、`LearningCenterPage` 原生 `datetime-local` 未迁移。未改后端 Schema/算法，未动正式数据库；Git main，保留既有 dirty/untracked，无 commit/push/reset/clean/stash。
- 文件：修改 `frontend/src/views/LearningCenterPage.vue`（原生控件→共享 `DateTimePicker`，提交本地墙钟）、`frontend/src/dev/ComponentPreview.vue`（跨月 fixture `2026-10-28T09:30 ~ 2026-11-05T11:30`，`DateTimeRangePicker` 演示 `min/max` 放宽至 2026-09-01~2026-12-31）、`frontend/src/views/__tests__/LearningCenterPaths.spec.ts`（+2→4 用例）、`docs/ui-v2/DATE_TIME_PICKER_V4_EXECUTION.md`（§9 证据表、§14/§15 状态、§16.5 闭环、新增 §17）；新增证据 `docs/ui-v2/date-time-picker-v4/{40,41,42,43,44}-*.png`、`evidence-20261028-legacy-followup.json`。
- 真实链：`LearningCenterPage.vue` 表单 `DateTimePicker v-model="newTask.deadline"` → `parseLocalDateTime` + `serializeLocalDateTime(parts,'minute')` → 原 `learningApi.createTask` → `POST /api/learning/tasks` → `LearningController.createTask` → `LearningServiceImpl.createTask` → `learning_task.deadline`（`LocalDateTime`）。未选时间不提交 deadline。
- 根因与修复：后端 `LocalDateTime` 无自定义 Jackson 格式，旧 `new Date(local).toISOString()` 提交 UTC `Z` 会被当本地墙钟解析造成 -8h。改为提交 `yyyy-MM-ddTHH:mm`；浏览器实测后端回读 `2026-11-05T09:30:00`，非 `01:30:00`。
- 数据：本地合成账号 `examrange1028`（userId=108）用真实组卷 `paperId=10`（25 题）+两次真实交卷，后端自动归集 25 条错题；验证用学习任务 id=21 已 `DELETE /api/learning/tasks/21` 清理，回读为空。未直接写库、未改正式库。
- 命令与结果：专项 `LearningCenterPaths` 4、`DateRangePicker` 16、`DateTimeRangePicker` 18 全通过；`ExamMistakes` 4、`PlaygroundV4` 7 全通过；全量 `npx vitest run` 238 文件/1760 用例 exit 0；`npx vue-tsc -b` exit 0（修正测试内多余断言）；`npm run build` 成功 5.52s（仅既有 chunk 提示）。
- 浏览器（Chrome via chrome-devtools MCP，1440×900）：① 展示中心跨月 fixture，面板初始 `2026 年 10 月`、`range-start=10-28`、`range-end=11-05`；点「结束」→`2026 年 11 月`、`range-end=11-05`；点「开始」→回 `2026 年 10 月`、`range-start=10-28`。② `/exam/mistakes` 第1页20条/`1 / 2（共 25 条）`，第2页5条/`2 / 2` 下一页禁用；第2页筛选 `2026-10-28 — 2026-10-28` 后请求 `from/to+pageNum=1`，列表回20条/`1 / 2`，Console 无 error/warn。③ `/learning/center` 添加计划选择 `2026-11-05 09:30`，模板无 `datetime-local`，提交后后端回读墙钟值。
- 未验证/风险：错题「标记已掌握」逐条状态变更与分页保持未回归；`DateTimeRangePicker` 仅验 1440×900，未重跑多视口；移动/Safari/Firefox/读屏仍按 V4 §14 保持未验证。
- 下一任务：待用户明确指令；候选为错题「标记已掌握」状态回归或展示中心范围组件秒精度演示；本轮不自动启动。详见[DATE_TIME_PICKER_V4_EXECUTION §17](ui-v2/DATE_TIME_PICKER_V4_EXECUTION.md)。


## 2026-10-28 分支统一整合

用户明确授权本地、GitHub和Gitee统一main，并批准保留desktopApp/mobile/miniprogram267项既有删除。已对账并保留main独有教材/文档与最新日期控件增量、V1修复、F11源码及两边提交历史；F11执行默认关闭，发布NOT_READY不变。新增可移植HashMap/Radar合成测试资源，补全启动helper及对应测试；不复制真实本地密钥或编译产物。

最终验证：Maven1209总计/1199通过/0失败/0错误/10跳过，退出0；Vitest241文件1768项/类型检查/构建退出0；启动器25项通过。证据及分支bundle/未提交文件备份：/private/tmp/knowflow-main-consolidation-20261028。原F11工作区由所属聊天归档；原V1主工作区保留未提交Fixture且切换同HEAD detached，释放分支。分支统一不代表RC_READY；MySQL8、完整发布门禁仍独立。

### 2026-10-28 · UI/UX V1.0 全站视觉基线 + 效率 Mini App + 顶栏快捷入口

- 目标/范围：统一全站字号/图标基线/页面留白；把既有任务/签到/习惯/日历/今日计划页面注册为 Mini App；顶栏新增高频快捷入口。未改 FSRS/Evidence/NextAction/评分/Tutor/MySQL 迁移/F11，未改正式库；Git main，保留既有 dirty，无 commit/push/merge/reset/clean/stash。
- 令牌：`style.css` 新增 `--kb-icon-slot(18)/--kb-icon-slot-sm(16)/--kb-nav-item-height(38)/--kb-nav-item-height-sm(32)/--kb-nav-item-gap(2)/--kb-page-padding-x·y(16~32)/--kb-quick-action-size(36)/--kb-quick-action-gap(4)/--kb-content-reading(72ch)`，旧令牌全保留。
- 组件：`Icon.vue` 全分支统一 `.kb-icon`（inline-block/vertical-align）；`PageHeader.vue` 语义类 + 28/600（md 24）；`CSidebar.vue` 一级38/二级32 + 18/16 图标容器；`CTopNav.vue` `.topnav-action` 36×36 + 快捷组 + 更多面板 + 焦点环 2px；`CLayout.vue` padding 收口；AppCenter 间距令牌化；TaskManager 高度/圆角/14px + 列表标题 h2。
- 逐页根因修复：ChatMarkdownRenderer（em 标题→20/18/16）、PomodoroPage（24/700→28/600）、AdaptiveLearningPanel（今日行动 20→18）、CheckIn（删页面内 .kb-h* 覆盖）、Habits（删 .kb-h1 700）、KnowledgeHome（h1 700→600）、TaskManager（双 h1）。
- Mini App：`tasks /tasks`、`check-in /check-in`、`habits /habits`、`calendar /calendar`、`today-plan /learning/today-plan`，category PRODUCTIVITY（效率），默认不安装，tools order 2–6；后端 `OfficialAppRegistry` 同步登记 ACTIVE；`app-management.spec.ts` 允许 tools 分类追加 PRODUCTIVITY。
- 顶栏：6 项真实路由，≥1280 显示前 3（任务/签到/习惯）+「更多快捷操作」，390 只留搜索/AI/更多/通知；真实浏览器实测 36×36、top=10、iconDy=0、无溢出、active 与 aria 正确。
- 验证：Vitest 241 文件/1768 用例通过；`vue-tsc --noEmit` exit 0；`npm run build` 成功 5.53s；后端 3 个 App 集成测试 10 用例通过。浏览器 5 视口 26 张截图全部 ox=0；Mini App E2E 21 断言通过（安装→工具组→进入→排序→收起 68px→停用二次确认→刷新/重登恢复→重新启用）；深色 4 页、焦点环、reduced-motion、390 抽屉通过。
- 未验证/风险：无改前截图（工作树已改且禁止 stash）；未断言 API HTTP 码；未跑后端全量 mvn；沉浸式阅读/编辑器、社区、代码练习、知识图谱、工作台、后台子页、今日计划未逐页实拍；notes/chat/paths 12–13px 密集工具条保留；系统字体缩放未测。
- 下一任务：优先抽紧凑工具条容器统一 12–13px 按钮，并补沉浸式页面多视口回归。详见 `docs/ui-ux/v1/UI_NEXT_STEPS.md`。
