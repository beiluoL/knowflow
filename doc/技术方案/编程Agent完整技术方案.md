# 编程 Agent 完整技术方案（贴合 KnowFlow 现状）

> 目标：在已有 CodeAgentController / AiService / AgentSession / AgentMessage / AgentCallLog 地基上，补齐「完整编程 Agent」四大内核：
> 1. 多 Provider 统一模型适配层（真·多协议：OpenAI / Anthropic / 文心 qianfan）
> 2. 工具调用引擎（动态注册 / 权限校验 / 生命周期 / 参数校验）
> 3. 上下文管理（多轮存储检索 / 摘要压缩 / 会话隔离 / 分页加载）
> 4. 前后端 REST + 持久化 + 前端工具面板 / 调用链可视化

本文为**方案设计**，实现前需与现有代码逐条对齐（见文末「现有事实基线」）。

---

## 0. 现有事实基线（已实现，必须复用，禁止重复造轮子）

| 模块 | 现状 | 复用方式 |
|---|---|---|
| AI 调用 | `AiService` 接口 + `AiServiceImpl`（仅 OpenAI 兼容 `/v1/chat/completions`，含 SSE `streamChat`） | 保留底层 HTTP；新增 Provider 适配层替换"单一 OpenAI 调用"，`AiService` 升级支持多轮 messages + tools |
| Provider 注册 | `AiProviderRegistry`（15 个 provider，anthropic/claude、wenxin/文心已注册但仅按 OpenAI 协议调用） | 扩展 `ProviderInfo` 增加 `protocol` 字段（OPENAI / ANTHROPIC / QIANFAN），注册表补充原生 baseUrl |
| 会话持久化 | `agent_session` / `agent_message` 表 + `AgentSession`/`AgentMessage` 实体 + Mapper | 直接复用，扩展字段（见 §3.4） |
| 调用日志 | `agent_call_log`（含 intent/score） | 复用，扩展记录 tool 调用链 |
| 用户模型配置 | `user_ai_config`（含 apiKey/modelId/baseUrl）+ `UserAiConfigService` | 复用，作为 Provider 配置的密钥来源 |
| 前端 API | `codeAgentApi`（`chatStream` 用 fetch+ReadableStream 解析 SSE，事件 delta/done/session/error） | 扩展事件类型（tool-call/tool-result/agent-done），新增 `toolApi`/`contextApi` |
| 前端页面 | `CodeAgent.vue`（对话/会话/监测/模型 4 tab，已改造为左右气泡布局） | 新增「工具」tab 与「调用链」侧栏，复用气泡渲染 |
| 代码沙箱 | `/api/agent/execute` + `CodeExecutionService`（已支持 Python/JS/Java 等） | 直接注册为内置 Tool `code_run` |
| 文件读写 | 既有 FS Access API + `readDirectory`/`flattenFileTree` | 注册为内置 Tool `fs_read` / `fs_write` |

---

## 1. 后端：大模型对接模块（多 Provider 统一适配层）

### 1.1 协议抽象
新增包 `com.knowflow.ai`：

- `ModelAdapter`（接口）：
  ```java
  interface ModelAdapter {
    String protocol();                      // OPENAI / ANTHROPIC / QIANFAN
    ChatResult chat(ChatRequest req);       // 单轮/多轮，非流式
    void streamChat(ChatRequest req, Consumer<TokenDelta> onToken, StreamDone onDone);
  }
  ```
  `ChatRequest` 含：`List<ChatMessage> messages`、`List<ToolSpec> tools`、`String model`、`Double temperature`、`Integer maxTokens`、`String apiKey`、`String baseUrl`、`String apiSecret`（文心用）。
  `ChatMessage`：`role(system/user/assistant/tool)` + `content` + `toolCalls?` + `toolCallId?`。
  `ToolSpec`：`name` + `description` + `JsonNode parameters`（JSON Schema）。

- 三个实现：
  - `OpenAiAdapter`（现有逻辑上移，含 SSE 解析，兼容 deepseek/百炼/自定义等所有 OpenAI 形态）。
  - `AnthropicAdapter`：POST `{baseUrl}/v1/messages`，Header `x-api-key` + `anthropic-version`，body `model/messages/system/tools`，`tool_use` block 解析，流式用 `stream:true` 的 SSE `content_block_delta`。
  - `QianfanAdapter`（文心）：用 `apiKey+secret` 调 `/oauth/2.0/token` 换 `access_token`，再 POST `/v2/chat/completion`（或 `/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/...`），tool_calling 走文心 `functions` 字段，需把 OpenAI tools 映射为文心 `functions`。

- `ModelAdapterFactory`：依据 `AiProviderRegistry.find(provider).getProtocol()` 选择 Adapter；provider 未知或 custom 默认 OPENAI。

### 1.2 配置动态管理
- 扩展 `AiProviderRegistry.ProviderInfo`：新增 `String protocol`（枚举 `Protocol{OPENAI,ANTHROPIC,QIANFAN}`），注册时按 provider 设定（anthropic→ANTHROPIC，wenxin→QIANFAN，其余→OPENAI）。保留 `baseUrl`（文心填 `https://qianfan.baidubce.com`）。
- `AiService` 升级：`chat/streamChat` 内部通过 `UserAiConfigService` 取密钥 → `ModelAdapterFactory` 取 Adapter → 调用。新增方法：
  ```java
  ChatResult chatMulti(List<ChatMessage> messages, List<ToolSpec> tools, Long userId, Long configId);
  void streamMulti(List<ChatMessage> messages, List<ToolSpec> tools, Long userId, Long configId, SseEmitter, Callback);
  ```
- 前端「模型」tab 已有的 `AiConfigController.platformModels` 复用 `AiProviderRegistry.all()`，无需大改。

### 1.3 统一异常
- `AiException`（继承 `RuntimeException`），`GlobalExceptionHandler` 已存在，统一包装为 `Result.failed(code,msg)`，SSE 场景中转为 `error` 事件。

---

## 2. 后端：工具调用引擎

### 2.1 注册机制（动态 + 内置）
新增包 `com.knowflow.agent.tool`：

- `AgentTool`（接口）：
  ```java
  interface AgentTool {
    String name();                       // 唯一，如 code_run
    String description();                // 给 LLM 看
    JsonNode parameters();               // JSON Schema
    boolean enabledByDefault();
    ToolPermission permission();         // 见 §2.3
    ToolResult execute(JsonNode args, ToolContext ctx) throws ToolException;
  }
  ```
- `ToolContext`：含 `userId`、`sessionId`、`workspaceDir`（来自 session.projectDir）、`tenantScope`。
- **内置工具**（Spring Bean 实现 `AgentTool`，`@Component` 自动注册）：
  - `CodeRunTool`：复用 `CodeExecutionService.execute`（已在 `/agent/execute` 使用），支持 Python/JS/Java/Go/C++，参数 `{language, code}`。
  - `FsReadTool`：读文件/列目录，参数 `{path, recursive?}`，基于既有 FS Access。
  - `FsWriteTool`：写文件，参数 `{path, content}`，**高危，需权限 + 确认**。
  - `DbQueryTool`：数据库查询（只读 SELECT），参数 `{sql}`，连接按用户数据源配置（MVP 先接 H2/系统库只读视图，避免越权）。
  - `WebSearchTool`（可选）：预留。
- `ToolRegistry`：`Map<String, AgentTool>`，`@Autowired List<AgentTool>` 自动收集；支持运行时 enable/disable（状态存 `agent_tool_config` 表，§2.4）；支持管理端动态注册（DTO 描述 + 参数 schema，MVP 先支持「声明式启用/参数覆盖」，插件式自定义 Tool 放后续）。

### 2.2 调用链生命周期（ReAct 编排）
新增 `com.knowflow.agent`：

- `AgentRuntimeService.run(sessionId, userMessage, userId, configId)`：
  1. 载入会话上下文（§3）→ 拼 `ChatMessage` 列表 + 注入启用 Tools。
  2. 调 `AiService.streamMulti`：
     - 收到 `delta` → 转发 SSE `delta`（文本）。
     - 收到 `tool_calls` → 对每个 call：校验权限（§2.3）→ 参数校验（JSON Schema）→ 执行 → 记录 `AgentCallLog`（tool 类型）→ 将 `role=tool` 消息回灌 → 继续下一轮 LLM。
     - 无 tool_calls → 终态，发 `agent-done`。
  3. 循环上限 `MAX_ITER=8`，防失控。
  4. 终态落库 `agent_message`（assistant）+ 更新 `agent_session` 统计。

- SSE 新增事件（前端 `parseAgentSseFrame` 需扩展）：
  - `tool-call`：`{ id, name, args }`
  - `tool-result`：`{ id, name, ok, output, truncated? }`
  - `agent-done`：`{ content, callChain: [...] }`

### 2.3 权限校验
- `ToolPermission` 枚举：`SAFE`（code_run 只读执行、fs_read 可读）、`WRITE`（fs_write）、`DANGEROUS`（db 写/删、命令执行）。
- 校验链（在 `ToolRegistry.invoke` 内）：
  1. 工具是否启用（查 `agent_tool_config.enabled`）。
  2. `permission` 等级 vs `session`/用户的授权等级（用户模型配置或角色决定）。
  3. `WRITE/DANGEROUS` 需前端二次确认（SSE 发 `tool-confirm` 事件，前端回 `POST /agent/tool/confirm` 带 ok/deny）。
  4. 参数校验：用 `everit`/Jackson 校验 JSON Schema（引入轻量依赖，或手写必需字段校验）。
- 越权统一抛 `ToolPermissionException` → `Result.failed(403xx, ...)`。

### 2.4 持久化（新增 2 张表，DATABASE.md 同步）
- `agent_tool_config`：`id, user_id, tool_name, enabled(INT), allow_write(INT), create_time, update_time, deleted`；索引 `uk_agent_tool_config_user_tool(user_id, tool_name)`。
- `agent_tool_call`：`id, session_id, message_id, tool_name, permission, args_json, result_json, status, latency_ms, create_time`；索引 `idx_agent_tool_call_session(session_id)`。
  （`agent_call_log` 已记录调用，二者互补：tool_call 细粒度、call_log 汇总。）

---

## 3. 后端：上下文管理模块

### 3.1 存储与检索
- 复用 `agent_message`；新增方法 `AgentMessageMapper.selectBySessionPage(sessionId, offset, limit)` 做分页加载（§3.4 增加 `message_type` 区分普通/工具消息）。
- 会话级隔离：所有查询带 `user_id` + `session_id`，Mapper 默认 LambdaQueryWrapper 加 `eq(userId)`。

### 3.2 摘要压缩
- `ContextCompressor`：
  - 策略 A（计数截断）：上下文超 `MAX_TOKENS`（默认 6000，可配置）→ 保留 system + 最近 N 轮，前面轮次调用 `AiService.complete` 生成摘要并入 system。
  - 策略 B（工具噪声剔除）：`role=tool` 长输出超阈值 → 截断 `truncated=true`。
  - 动态调整：会话级 `context_window` 字段（§3.4），前端可改。

### 3.3 分页加载
- 接口 `GET /agent/sessions/{id}/messages?cursor=&limit=20` 返回按时间正序分页（现有 `getMessages` 全量，保留并新增分页版）。

### 3.4 `agent_session` / `agent_message` 字段扩展（兼容已有）
- `agent_session` 增加：`context_window INT DEFAULT 6000`、`agent_mode VARCHAR(20) DEFAULT 'chat'`。
- `agent_message` 增加：`message_type VARCHAR(20) DEFAULT 'normal'`（normal / tool_call / tool_result / summary）、`parent_id BIGINT`、`tool_name VARCHAR(40)`、`tool_call_id VARCHAR(40)`。

---

## 4. 后端：RESTful API（统一异常 + 响应）

### 4.1 接口清单（新增/改造）
| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/agent/chat/stream` | 改造：支持 tools + 多轮，SSE 事件扩展 tool-call/tool-result/agent-done/tool-confirm |
| POST | `/api/agent/tool/confirm` | 用户对 WRITE/DANGEROUS 工具二次确认 |
| GET | `/api/agent/tools` | 工具列表（含启用状态、权限、描述、参数 schema） |
| GET | `/api/agent/tools/{name}` | 工具详情 |
| PUT | `/api/agent/tools/{name}` | 启用/禁用 + 参数覆盖（ToolConfig 落库） |
| GET | `/api/agent/sessions/{id}/messages` | 改造：支持 `?cursor=&limit=` 分页 |
| PUT | `/api/agent/sessions/{id}/context` | 设置 context_window / agent_mode |
| GET | `/api/agent/sessions/{id}/context` | 返回当前上下文窗口摘要（可视化用） |
| GET | `/api/agent/sessions/{id}/call-chain` | 本次/历史工具调用链（可视化用） |
| （复用） | `/api/agent/sessions` CRUD、`/agent/models`、`/agent/stats`、`/agent/intent`、`/agent/ambiguities`、`/agent/evaluate` | 已有 |

### 4.2 统一响应
- 复用 `Result<T>`（success/failed/code/message/data）。所有 Controller 用 `Result.success(...)`。
- `GlobalExceptionHandler` 增加 `AiException`/`ToolException`/`ToolPermissionException` 映射。

### 4.3 持久化层约定（遵守阿里规范记忆）
- 禁用物理外键；逻辑关联用 `session_id/user_id` + 索引（`idx_agent_tool_call_session` 等已规划）。
- 表名小写非复数；`BaseEntity` 已含 `id/create_time/update_time`；逻辑删除 `deleted INT DEFAULT 0`。

---

## 5. 前端：对话交互界面

### 5.1 多会话管理
- 复用现有「会话」tab（`listSessions/createSession/renameSession/deleteSession/getMessages`），新增：
  - 会话列表侧栏（桌面端左侧固定，气泡区在右），切换时按需分页加载历史（`cursor/limit`）。
  - 切换会话保留各自 context 配置。

### 5.2 流式实时展示 + Markdown + 高亮
- 扩展 `codeAgentApi.chatStream` 回调：新增 `onToolCall / onToolResult / onAgentDone / onToolConfirm`。
- `parseAgentSseFrame` 增加对应事件分支。
- Markdown 渲染沿用现有 `renderMarkdown`（确认已集成代码高亮；若未接 `highlight.js`/`markdown-it`，在方案实现阶段补充）。

### 5.3 工具管理面板（新增「工具」tab）
- 调 `/api/agent/tools` 展示列表：名称、描述、权限徽标（SAFE/WRITE/DANGEROUS 用 kb-* 色）、启用开关、参数 schema 折叠。
- 详情抽屉：参数 JSON Schema 展示 + 启停开关（`PUT /agent/tools/{name}`）。
- 样式用项目 `kb-*` 变量，图标用 `Icon.vue`（tool/wrench/shield 等已有）。

### 5.4 上下文与调用链可视化
- 调用链侧栏/内联卡片：监听 `tool-call`/`tool-result` 事件，渲染为时间线（工具名→参数→结果→耗时），用 `Icon` + `kb-*` 配色，异常标红。
- 上下文信息：调 `/agent/sessions/{id}/context` 显示 token 占用、窗口大小、压缩摘要。
- 用户消息气泡内可显示「调用了 N 个工具」徽标，点击展开调用链。

### 5.5 响应式
- 桌面端：左「会话列表」+ 中「对话气泡」+ 右「工具/调用链」三栏（或工具/调用链用 tab 切换），`max-width` 合理，参考 Copilot Chat 布局。

---

## 6. 分批实现计划（建议）

| 批次 | 范围 | 验收 |
|---|---|---|
| P0 | Provider 适配层（OpenAI/Anthropic/Qianfan）+ `AiService` 多轮升级 + `AiProviderRegistry.protocol` | 三协议单轮对话联通；已有 OpenAI 对话不回归 |
| P1 | 工具引擎（Tool 接口 + 注册 + 内置 4 工具 + 权限 + 生命周期）+ 持久化 2 表 | `/agent/tools` 增删改查 + ReAct 编排跑通 code_run |
| P2 | 上下文模块（分页 + 摘要压缩 + 字段扩展） | 长对话分页/压缩生效 |
| P3 | REST 接口补全 + 统一异常 + 文档同步 | 全部接口联调通过 |
| P4 | 前端（工具面板 + 调用链可视化 + 多会话分页 + SSE 事件扩展） | 浏览器端完整 Agent 体验 |
| P5 | 文档同步（DATABASE.md/README.md/项目日志.md）+ 本地 commit（不 push） | 文档与代码一致 |

---

## 7. 风险与决策点
- **文心 tool_calling**：文心 `functions` 能力各模型不一，MVP 对文心先支持单轮/多轮文本，tool 编排标注「受限」。
- **数据库工具越权**：`DbQueryTool` 仅开放只读 system 视图，禁止任意写。
- **自定义 Tool 插件化**：本期仅「声明式启用/参数覆盖」，完整插件 SDK 后续。
- **前端高亮库**：若 `renderMarkdown` 未集成语法高亮，P4 引入 `highlight.js`（经 `@` 别名与 Vite 兼容）。
- **依赖**：JSON Schema 校验用轻量方案（`com.networknt:json-schema-validator` 或手写必需字段校验），避免重依赖。

---

## 8. 待你确认的关键点
1. P0~P5 分批是否认可？是否要先做 P0+P1（后端内核）再做前端？
2. 文心 tool_calling 受限是否接受？还是本期先不做文心工具编排？
3. `DbQueryTool` 只读范围是否仅 system/H2 元信息？
4. 自定义 Tool 是否需要「用户上传脚本」级别的插件能力，还是仅内置工具启停即可？
