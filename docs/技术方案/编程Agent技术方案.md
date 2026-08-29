# 编程 Agent 技术方案

> 版本：v2.0（合并稿）
> 日期：2026-08-28
> 适用项目：knowflow 编程 Agent（CodeAgent）
> 关联文档：`多语言在线运行沙箱技术方案.md`、`消息功能技术方案.md`
> 本方案合并自《编程 Agent 完整技术方案》（基础架构）与《编程助手能力增强技术方案》（能力增强里程碑），并对照当前代码实现核对状态。

---

## 1. 概述

### 1.1 设计目标

在 KnowFlow 已有的 `CodeAgentController` / `AiService` / `AgentSession` / `AgentMessage` / `AgentCallLog` 地基上，构建一套完整可扩展的编程 Agent：

1. **多 Provider 统一模型适配层**——真·多协议：OpenAI / Anthropic / 文心 qianfan。
2. **工具调用引擎**——动态注册 / 权限校验 / 生命周期 / 参数校验。
3. **ReAct 编排循环**——上下文组装 → 模型推理 → 工具调用 → 结果回灌，至终态收敛。
4. **上下文管理**——多轮存储检索 / 摘要压缩 / 会话隔离 / 分页加载。
5. **能力增强里程碑**——围绕用户高频需求，以 M1～M5 五个里程碑补齐结构理解、审查、诊断、测试、补全、执行、VCS、文档与工作流。
6. **前后端 REST + 持久化 + 前端工具面板 / 调用链可视化**。

**设计原则**：所有新能力一律实现为 `AgentTool` Spring Bean，复用 `ToolRegistry` 的权限/授权/调用链可视化，无需改动编排核心；工作流则是"编排层之上的触发规则"，在意图识别之后注入预设 prompt。

### 1.2 非目标（本期不做）

- 真实 IDE 插件（VS Code/JetBrains 扩展）——仅保证 Web 端体验对齐主流助手范式。
- 云端 Agent 编排平台、多 Agent 协作。
- 生产级硬隔离（Docker/Judge0 已在沙箱方案规划）。
- 完整 Tool 插件 SDK（本期仅「声明式启用/参数覆盖」，自定义脚本上传级能力留作增强路线）。

### 1.3 现有事实基线（已实现，必须复用）

| 模块 | 现状 | 复用方式 |
|---|---|---|
| AI 调用 | `AiService` 接口 + `AiServiceImpl`（OpenAI 兼容 `/v1/chat/completions`，含 SSE `streamChat`） | 保留底层 HTTP；新增 Provider 适配层替换"单一 OpenAI 调用"，`AiService` 升级支持多轮 messages + tools |
| Provider 注册 | `AiProviderRegistry`（15 个 provider，anthropic/claude、wenxin/文心已注册但仅按 OpenAI 协议调用） | 扩展 `ProviderInfo` 增加 `protocol` 字段（OPENAI / ANTHROPIC / QIANFAN），注册表补充原生 baseUrl |
| 会话持久化 | `agent_session` / `agent_message` 表 + `AgentSession`/`AgentMessage` 实体 + Mapper | 直接复用，扩展字段（见 §3.4） |
| 调用日志 | `agent_call_log`（含 intent/score） | 复用，扩展记录 tool 调用链 |
| 用户模型配置 | `user_ai_config`（含 apiKey/modelId/baseUrl）+ `UserAiConfigService` | 复用，作为 Provider 配置的密钥来源 |
| 前端 API | `codeAgentApi`（`chatStream` 用 fetch+ReadableStream 解析 SSE，事件 delta/done/session/error） | 扩展事件类型（tool-call/tool-result/agent-done），新增 `toolApi`/`contextApi` |
| 前端页面 | `CodeAgent.vue`（对话/会话/监测/模型 4 tab，已改造为左右气泡布局） | 新增「工具」「工作流」tab 与「调用链」侧栏，复用气泡渲染 |
| 代码沙箱 | `/api/agent/execute` + `CodeExecutionService`（Python/JS/Java/Go/C++） | 直接注册为内置 Tool `code_run` |
| 文件读写 | 既有 FS Access API + `readDirectory`/`flattenFileTree` | 注册为内置 Tool `fs_read` / `fs_write` |
| 代码调试 | `CodeDebugServiceImpl`（JS/Java/C++ 错误行号定位） | 复用，作为 `diagnose` 工具底座 |
| 代码评估 | `CodeAssessServiceImpl`（动态用例 + 静态检查） | 复用，作为 `test_gen` 验证底座 |
| 行级 diff 审阅 | 前端 `diff.ts` + `CodeAgent.vue` 审阅模式 | 作为"跨文件编辑/审查补丁"的呈现层 |

---

## 2. 核心架构

整体架构：前端 CodeAgent.vue（对话/会话/工具面板/调用链/监测/工作流配置）↔ SSE + REST ↔ `CodeAgentController` / `AgentToolController` ↔ `IntentService` / `AgentRuntimeService` / `ToolRegistry` ↔ 一组 `AgentTool` Spring Bean ↔ LLM 审计 / 沙箱执行 / Git CLI / 知识库检索 / FS Access。

```
                 ┌──────────────── 前端 CodeAgent.vue ────────────────┐
                 │  对话 / 会话 / 工具面板 / 调用链 / 监测 / 工作流配置 │
                 └───────────────┬───────────────────────┬───────────┘
                                 │ SSE                     │ REST
                         /api/agent/chat            /api/agent/tools (CRUD)
                                 │                         │
        ┌────────────────────────▼──────────┐   ┌──────────▼──────────────┐
        │      CodeAgentController            │   │   AgentToolController    │
        │  (意图识别→ReAct编排→流式回灌)        │   │  (工具启用/授权配置)       │
        └────────────────────────┬──────────┘   └──────────┬──────────────┘
                                 │                         │
        ┌────────────────────────▼──────────┐   ┌──────────▼──────────────┐
        │  IntentService / AgentRuntimeService│   │   ToolRegistry           │
        │  (P1/P2/P3, 工作流触发)              │   │  (权限校验+调用链落库)     │
        └────────────────────────┬──────────┘   └──────────┬──────────────┘
                                 │                         │
        ┌────────────────────────▼─────────────────────────▼─────────────┐
        │            AgentTool 实现（均为 Spring Bean 自动注册）            │
        │  code_run / fs_read / fs_write / db_query /                      │
        │  code_review / code_complete / test_gen / terminal / git /        │
        │  fs_tree / fs_symbols / diagnose / api_doc                        │
        └───────┬───────────┬───────────┬───────────┬───────────┬──────────┘
                │           │           │           │           │
        ┌───────▼───┐ ┌─────▼─────┐ ┌───▼────┐ ┌────▼─────┐ ┌──▼────────┐
        │ LLM 审计  │ │ 沙箱执行   │ │ Git CLI│ │ 知识库检索│ │ FS Access │
        │ (AiService)│ │(已有沙箱)  │ │(JGit/  │ │(已有RAG) │ │ (已有)    │
        │           │ │           │ │Process)│ │          │ │           │
        └───────────┘ └───────────┘ └────────┘ └──────────┘ └───────────┘
```

### 2.1 多 Provider 统一模型适配层

新增包 `com.knowflow.ai`，以 `ModelAdapter` 屏蔽各厂商接口差异，向上层（`AiService`）提供一致的单轮/多轮对话与流式调用能力。

- `ModelAdapter`（接口）：

  ```java
  interface ModelAdapter {
    String protocol();                      // OPENAI / ANTHROPIC / QIANFAN
    ChatResult chat(ChatRequest req);       // 单轮/多轮，非流式
    void streamChat(ChatRequest req, Consumer<TokenDelta> onToken, Consumer<StreamDone> onDone);
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

- 配置动态管理：扩展 `AiProviderRegistry.ProviderInfo` 增加 `String protocol`（枚举 `Protocol{OPENAI,ANTHROPIC,QIANFAN}`），注册时按 provider 设定（anthropic→ANTHROPIC，wenxin→QIANFAN，其余→OPENAI）。保留 `baseUrl`（文心填 `https://qianfan.baidubce.com`）。

- `AiService` 升级：`chat/streamChat` 内部通过 `UserAiConfigService` 取密钥 → `ModelAdapterFactory` 取 Adapter → 调用。新增方法：

  ```java
  ChatResult chatMulti(List<ChatMessage> messages, List<ToolSpec> tools, Long userId, Long configId);
  void streamMulti(List<ChatMessage> messages, List<ToolSpec> tools, Long userId, Long configId, SseEmitter, Callback);
  ```

- 前端「模型」tab 已有的 `AiConfigController.platformModels` 复用 `AiProviderRegistry.all()`，无需大改。

- 统一异常：`AiException`（继承 `RuntimeException`），`GlobalExceptionHandler` 已存在，统一包装为 `Result.failed(code,msg)`，SSE 场景中转为 `error` 事件。

### 2.2 工具调用引擎

新增包 `com.knowflow.agent.tool`，所有可被大模型调用的能力均实现 `AgentTool` 接口，由 `ToolRegistry` 自动收集并统一校验执行。

- `AgentTool`（接口）：

  ```java
  interface AgentTool {
    String name();                       // 唯一，如 code_run
    String description();                // 给 LLM 看
    JsonNode parameters();               // JSON Schema
    boolean enabledByDefault();
    ToolPermission permission();         // 见 §2.2.2
    ToolResult execute(JsonNode args, ToolContext ctx) throws ToolException;
  }
  ```

- `ToolContext`：含 `userId`、`sessionId`、`workspaceDir`（来自 session.projectDir）、`tenantScope`、`messageId`。

- `ToolRegistry`：`Map<String, AgentTool>`，`@Autowired List<AgentTool>` 自动收集；提供 `listToolsForUser`（含启用状态）/ `permissionOf` / `isEnabled` / `invoke` / `setConfig`。运行时 enable/disable 状态存 `agent_tool_config` 表；管理端动态注册采用「声明式启用/参数覆盖」。

#### 2.2.1 调用链生命周期（ReAct 编排）

`AgentRuntimeService.run(sessionId, userMessage, userId, configId)`：

1. 由 `AgentContextManager` 组装受控长度的历史（含摘要压缩）。
2. 自定义工作流：命中关键词/意图的工作流模板以 system 消息注入（仅注入首个最匹配项）。
3. 拼 `ChatMessage` 列表 + 注入该用户已启用的 `ToolSpec`。
4. 能力检测：若 `aiService.supportsTools(...)` 为 false（如 Ollama 上的 deepseek-coder:6.7b），退化为普通对话，并向前端推送 `info` 提示，避免下发 tools 触发 400。
5. 调 `AiService.chatMulti`：
   - 收到 `delta` → 转发 SSE `delta`（文本）。
   - 收到 `tool_calls` → 对每个 call：权限校验 → 参数解析 → 执行 → 记录 `AgentToolCall` → 将 `role=tool` 消息回灌 → 继续下一轮 LLM。
   - 无 tool_calls → 终态，发 `agent-done`。
6. 循环上限 `MAX_ITER=8`，防失控。
7. 终态落库 `agent_message`（assistant）+ 更新 `agent_session` 统计。

- SSE 新增事件（前端 `parseAgentSseFrame` 需扩展）：
  - `tool-call`：`{ id, name, args }`
  - `tool-result`：`{ id, name, ok, output, truncated? }`
  - `agent-done`：`{ content, callChain: [...] }`

#### 2.2.2 权限校验

`ToolPermission` 枚举：`SAFE`（code_run 只读执行、fs_read 可读）、`WRITE`（fs_write、code_review、test_gen、git）、`DANGEROUS`（db 写/删、命令执行、terminal）。

校验链（在 `ToolRegistry.invoke` 内）：

1. 工具是否启用（查 `agent_tool_config.enabled`；无配置时回退到 `enabledByDefault`）。
2. `permission` 等级 vs 用户的授权等级：WRITE 需 `allow_write=1`，DANGEROUS 需显式 `allow_write=1`（视作高危开启）。
3. `DANGEROUS` 需前端二次确认（SSE 发 `tool-confirm` 事件，前端回 `POST /agent/tool/confirm` 带 ok/deny）。
4. 参数校验：用 `everit`/Jackson 校验 JSON Schema（引入轻量依赖，或手写必需字段校验）。

越权统一抛 `ToolPermissionException` → `Result.failed(403xx, ...)`。工具不存在时按 `DANGEROUS` 保守处理。

#### 2.2.3 持久化

- `agent_tool_config`：`id, user_id, tool_name, enabled(INT), allow_write(INT), create_time, update_time, deleted`；索引 `uk_agent_tool_config_user_tool(user_id, tool_name)`。
- `agent_tool_call`：`id, session_id, message_id, tool_name, permission, args_json, result_json, status, latency_ms, create_time`；索引 `idx_agent_tool_call_session(session_id)`。
  （`agent_call_log` 已记录调用，二者互补：tool_call 细粒度、call_log 汇总。）

### 2.3 上下文管理

#### 2.3.1 存储与检索

- 复用 `agent_message`；新增方法 `AgentMessageMapper.selectBySessionPage(sessionId, offset, limit)` 做分页加载（增加 `message_type` 区分普通/工具消息）。
- 会话级隔离：所有查询带 `user_id` + `session_id`，Mapper 默认 LambdaQueryWrapper 加 `eq(userId)`。

#### 2.3.2 摘要压缩（`ContextCompressor` / `AgentContextManager`）

- 策略 A（计数截断）：上下文超 `MAX_TOKENS`（默认 6000，可配置）→ 保留 system + 最近 N 轮，前面轮次调用 `AiService.complete` 生成摘要并入 system。
- 策略 B（工具噪声剔除）：`role=tool` 长输出超阈值 → 截断 `truncated=true`。
- 动态调整：会话级 `context_window` 字段，前端可改。

#### 2.3.3 分页加载

- 接口 `GET /agent/sessions/{id}/messages?cursor=&limit=20` 返回按时间正序分页（现有 `getMessages` 全量，保留并新增分页版）。

#### 2.3.4 `agent_session` / `agent_message` 字段扩展（兼容已有）

- `agent_session` 增加：`context_window INT DEFAULT 6000`、`agent_mode VARCHAR(20) DEFAULT 'chat'`。
- `agent_message` 增加：`message_type VARCHAR(20) DEFAULT 'normal'`（normal / tool_call / tool_result / summary）、`parent_id BIGINT`、`tool_name VARCHAR(40)`、`tool_call_id VARCHAR(40)`。

---

## 3. 工具清单

所有工具实现 `com.knowflow.agent.tool.AgentTool`，由 `ToolRegistry` 自动发现。下表给出名称、权限、默认启用与职责。

### 3.1 内置基础工具

| 工具名 | 权限 | 默认启用 | 职责 |
|---|---|---|---|
| `code_run` | SAFE | 是 | 复用 `CodeExecutionService.execute`，支持 Python/JS/Java/Go/C++，参数 `{language, code}` |
| `fs_read` | SAFE | 是 | 读文件/列目录，参数 `{path, recursive?}`，基于既有 FS Access |
| `fs_write` | WRITE | 是 | 写文件，参数 `{path, content}`，**高危，需权限 + 确认** |
| `db_query` | SAFE | 是 | 数据库查询（只读 SELECT），参数 `{sql}`，连接按用户数据源配置（MVP 先接 H2/系统库只读视图，避免越权） |

### 3.2 能力增强工具

| 工具名 | 权限 | 默认启用 | 职责 |
|---|---|---|---|
| `fs_tree` | SAFE | 是 | 返回挂载目录的树状结构（带文件大小/语言），支撑"结构理解" |
| `fs_symbols` | SAFE | 是 | 解析目录内符号（导出函数/类/接口），支撑跨文件引用分析 |
| `code_review` | WRITE（需授权，因可给修复补丁） | 是 | 读取指定文件/目录，输出分级审查报告（bug/安全/性能/规范）+ 可选修复补丁 |
| `diagnose` | SAFE | 是 | 接收报错+代码上下文，定位根因并生成修复补丁（复用调试服务） |
| `test_gen` | WRITE | 是 | 为指定函数/文件生成单元测试，写入 `tests/` 并在沙箱跑通 |
| `code_complete` | SAFE | 是 | 行内补全：给定前缀/后缀与语言，返回补全片段（FIM 风格） |
| `terminal` | DANGEROUS | 否（需显式开启） | 在受控沙箱执行 shell 命令（构建/安装/运行），超时+命令白名单 |
| `git` | WRITE | 否（需显式开启） | 项目目录 git 操作：status/diff/log/commit/branch |
| `api_doc` | SAFE | 是 | 基于知识库（优先）+ 可选 Web 抓取，回答 API 用法与集成示例 |

### 3.3 工具参数契约（示例）

**code_review**
```json
{ "file": "src/calc.java", "scope": "file|dir", "severity": "all|warning|error", "fix": true }
```
返回：
```json
{ "summary": "3 errors, 2 warnings",
  "issues": [
    { "severity": "error", "line": 24, "rule": "null-deref",
      "desc": "可能空指针", "suggestion": "增加 null 判断",
      "patch": "@@ -24,3 +24,5 @@\n+ if (x == null) return 0;" }
  ] }
```

**terminal**
```json
{ "command": "npm test", "cwd": "subdir", "timeoutSec": 60 }
```
返回：`{ "exitCode": 0, "stdout": "...", "stderr": "...", "timedOut": false }`

**git**
```json
{ "action": "commit", "message": "fix: ...", "files": ["a.ts","b.ts"] }
```

**test_gen**
```json
{ "target": "src/calc.java", "framework": "junit|pytest|jest" }
```

### 3.4 安全约束（与既有框架一致）

- `terminal` / `git` 默认 DANGEROUS，**默认禁用**，需用户在工具面板显式开启 `allowWrite`。
- `terminal` 命令白名单：仅允许 `npm/npx/pip/python/node/git/mvn/gradle/ls/cat/grep/make/go/cargo/test` 等；拒绝 `rm -rf /`、`sudo`、`curl|sh`、`:(){` 等危险模式；超时默认 60s。
- 所有写操作（review 修复落盘、test_gen 写文件、git commit）走既有的 `tool-confirm` 二次确认。
- 路径穿越防护复用 `CodeWorkspaceServiceImpl.safeResolve` 思路：拒绝绝对路径与 `..`。
- `DbQueryTool` 仅开放只读 system 视图，禁止任意写。

---

## 4. 自定义工作流

### 4.1 数据模型（新增表 `agent_workflow`）

```sql
CREATE TABLE IF NOT EXISTS agent_workflow (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '所属用户（逻辑外键 sys_user.id）',
    name VARCHAR(100) NOT NULL COMMENT '工作流名称',
    trigger_type VARCHAR(20) NOT NULL COMMENT 'intent(按意图触发)/keyword(关键词)/manual(手动)',
    trigger_value VARCHAR(200) COMMENT '意图类型(generate/review...) 或触发关键词，逗号分隔',
    prompt_template MEDIUMTEXT NOT NULL COMMENT '注入的系统/用户 prompt 模板，支持 {input}/{file} 占位',
    enabled INT DEFAULT 1 COMMENT '是否启用: 0 禁用 / 1 启用',
    sort_order INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
);
CREATE INDEX idx_agent_workflow_user ON agent_workflow (user_id, deleted);
```

### 4.2 触发机制

- 在 `AgentRuntimeService` 编排入口（上下文组装之后、调用 LLM 之前）加载该用户 `enabled=1` 的工作流。
- `trigger_type=intent`：当识别意图命中 `trigger_value` 时，将 `prompt_template` 渲染后拼接到系统提示词。
- `trigger_type=keyword`：用户消息命中关键词时触发。
- `trigger_type=manual`：前端工作流面板提供"运行"按钮，直接以该模板发起一轮对话。
- 占位符约定：`{input}`=用户输入，`{file}`=当前预览文件，`{tree}`=文件树快照。
- 单次只注入匹配度最高的工作流，按 `sort_order` 取首个，避免 prompt 冲突。

### 4.3 前端

- 新增「工作流」标签页（复用 `CodeAgent.vue` 的 tab 体系）`AgentWorkflowPanel.vue`：列表/启停/编辑模板/触发类型。
- 接口：`GET/POST/PUT/DELETE /api/agent/workflows`。

---

## 5. 能力增强里程碑（M1–M5）

下表对照用户需求拆解，按里程碑组织实现顺序。状态标记：✅ 已实现 / ⏳ 进行中 / ⬜ 未启动。

| 里程碑 | 范围 | 工具 | 依赖底座 | 状态 |
|---|---|---|---|---|
| **M1 结构理解 + 跨文件** | 文件树/符号解析 + 上下文快照增强 | `fs_tree` / `fs_symbols` | FS Access（已有） | ✅ |
| **M2 审查 + 诊断** | 分级审查报告+补丁；报错定位+修复 | `code_review` / `diagnose` | LLM 审计 + 调试服务（已有） | ✅ |
| **M3 测试 + 补全** | 单测生成并沙箱验证；行内 FIM 补全 | `test_gen` / `code_complete` | 沙箱执行 + 评估服务（已有） | ✅ |
| **M4 执行 + VCS** | 受控 shell；项目目录 git 操作 | `terminal` / `git` | 沙箱 + JGit/Process（DANGEROUS，需授权） | ✅ |
| **M5 文档 + 工作流** | API 用法检索；工作流配置体系 | `api_doc` + `agent_workflow` | 知识库 RAG（已有） | ✅ |

### 5.1 需求与实现方式映射

| 需求项 | 实现方式 | 类型 |
|---|---|---|
| 多语言代码生成与补全 | 生成链路已多语言；新增 `code_complete` 工具（行内补全，FIM 风格） | 新增工具 |
| 智能代码审查与优化建议 | 新增 `code_review` 工具（读取目标文件 + LLM 审计，输出分级问题清单 + 修复补丁） | 新增工具 |
| 上下文感知对话式辅助 | 强化 `IntentService` + 文件快照上下文回灌（已有） | 增强 |
| 项目结构理解与跨文件编辑 | 新增 `fs_tree` / `fs_symbols` 工具 + diff 审阅采纳（已有） | 新增工具 |
| 终端命令执行与调试 | 新增 `terminal` 工具（受控 shell，超时/白名单） | 新增工具 |
| 版本控制集成 | 新增 `git` 工具（status/diff/log/commit/branch） | 新增工具 |
| 错误诊断与自动修复 | 复用 `CodeDebugService` + 新增 `diagnose` 工具（基于报错自动定位+修复） | 新增工具 |
| 测试用例生成 | 新增 `test_gen` 工具（生成单测 + 沙箱验证） | 新增工具 |
| API 文档查询与集成 | 新增 `api_doc` 工具（知识库检索 + 可选 Web 抓取） | 新增工具 |
| 自定义工作流配置 | 新增 `agent_workflow` 表 + 工作流触发机制（prompt 模板 + 触发意图/关键词） | 新增表+机制 |
| 响应快 / 准确性高 / 适配 IDE | 流式优先、并发工具、调用链缓存、P3 评估闭环；前端 HMR | 既有 |

---

## 6. 接口设计

### 6.1 接口清单（新增/改造）

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| POST | `/api/agent/chat/stream` | 登录 | 改造：支持 tools + 多轮，SSE 事件扩展 tool-call/tool-result/agent-done/tool-confirm |
| POST | `/api/agent/chat` | 登录 | 对话（既有，内部编排新工具） |
| POST | `/api/agent/tool/confirm` | 登录 | 用户对 WRITE/DANGEROUS 工具二次确认 |
| GET | `/api/agent/tools` | 登录 | 工具列表（含启用状态、权限、描述、参数 schema） |
| GET | `/api/agent/tools/{name}` | 登录 | 工具详情 |
| PUT | `/api/agent/tools/{name}` | 登录 | 启用/禁用 + 参数覆盖（ToolConfig 落库） |
| GET | `/api/agent/sessions/{id}/messages` | 登录 | 改造：支持 `?cursor=&limit=` 分页 |
| PUT | `/api/agent/sessions/{id}/context` | 登录 | 设置 context_window / agent_mode |
| GET | `/api/agent/sessions/{id}/context` | 登录 | 返回当前上下文窗口摘要（可视化用） |
| GET | `/api/agent/sessions/{id}/call-chain` | 登录 | 本次/历史工具调用链（可视化用） |
| GET | `/api/agent/workflows` | 登录 | 工作流列表（新增） |
| POST | `/api/agent/workflows` | 登录 | 创建工作流（新增） |
| PUT | `/api/agent/workflows/{id}` | 登录 | 更新（新增） |
| DELETE | `/api/agent/workflows/{id}` | 登录 | 删除（逻辑删除，新增） |
| （复用） | `/api/agent/sessions` CRUD、`/agent/models`、`/agent/stats`、`/agent/intent`、`/agent/ambiguities`、`/agent/evaluate` | — | 已有 |

> 工具执行无需新增专属 HTTP 接口——统一经 ReAct 编排由 `/api/agent/chat` 驱动，工具结果通过 SSE `tool_result` 事件回灌前端调用链（`AgentCallChain` 已支持可视化）。

### 6.2 统一响应

- 复用 `Result<T>`（success/failed/code/message/data）。所有 Controller 用 `Result.success(...)`。
- `GlobalExceptionHandler` 增加 `AiException`/`ToolException`/`ToolPermissionException` 映射。

### 6.3 持久化层约定（遵守阿里规范）

- 禁用物理外键；逻辑关联用 `session_id/user_id` + 索引（`idx_agent_tool_call_session` 等已规划）。
- 表名小写非复数；`BaseEntity` 已含 `id/create_time/update_time`；逻辑删除 `deleted INT DEFAULT 0`。

---

## 7. 前端改动点

| 文件 | 改动 |
|---|---|
| `views/CodeAgent.vue` | 新增「工具」「工作流」tab；补全建议的轻量弹层（code_complete）；确保新工具的 callchain 节点正常渲染；桌面端三栏布局（左会话列表+中气泡+右工具/调用链） |
| `components/agent/AgentToolPanel.vue` | 新增 `terminal`/`git` 的"高危需开启"提示；展示新增工具；参数 JSON Schema 折叠 + 启停开关 |
| `components/agent/AgentWorkflowPanel.vue` | 新增：工作流配置 CRUD |
| `components/agent/AgentCallChain.vue` | 已支持，确认新工具名在节点类型映射中；异常标红 |
| `api/codeAgent.ts` | 新增 `workflows` 接口封装；扩展 `chatStream` 回调 `onToolCall/onToolResult/onAgentDone/onToolConfirm`；`parseAgentSseFrame` 增加对应事件分支 |
| `utils/diff.ts` | 已具备，review 修复补丁可复用其审阅呈现 |
| Markdown 渲染 | 沿用现有 `renderMarkdown`；若未集成 `highlight.js`/`markdown-it`，实现阶段补充 |

---

## 8. 实现状态

对照当前代码库（`backend/src/main/java/com/knowflow/`），各模块均已落地：

| 模块 | 关键类 | 状态 |
|---|---|---|
| 多 Provider 适配层 | `ai/ModelAdapter`、`OpenAiAdapter`、`AnthropicAdapter`、`QianfanAdapter`、`ModelAdapterFactory` | ✅ |
| 工具调用引擎 | `agent/tool/AgentTool`、`ToolRegistry`、`ToolPermission`、`ToolException`、`ToolResult`、`ToolContext` | ✅ |
| 内置基础工具 | `tool/builtin/CodeRunTool`、`FsReadTool`、`FsWriteTool`、`DbQueryTool` | ✅ |
| 能力增强工具 | `tool/builtin/FsTreeTool`、`FsSymbolsTool`、`CodeReviewTool`、`DiagnoseTool`、`TestGenTool`、`CodeCompleteTool`、`TerminalTool`、`GitTool`、`ApiDocTool` | ✅ |
| ReAct 编排循环 | `agent/AgentRuntimeService`（含 `AgentEventListener`、`MAX_ITER=8`、能力检测退化、工作流注入） | ✅ |
| 上下文管理 | `agent/AgentContextManager`（含 token 估算、摘要压缩） | ✅ |
| 自定义工作流 | `entity/AgentWorkflow`、`service/AgentWorkflowService`、`agent_workflow` 表 | ✅ |
| 调用链落库 | `entity/AgentToolCall`、`entity/AgentToolConfig` | ✅ |

里程碑 M1–M5 对应工具均已实现（见 §5 状态列）。

### 8.1 后续分批收尾建议

| 批次 | 范围 | 验收 |
|---|---|---|
| P3 | REST 接口补全 + 统一异常 + 文档同步 | 全部接口联调通过 |
| P4 | 前端（工具面板 + 调用链可视化 + 多会话分页 + SSE 事件扩展 + 工作流面板） | 浏览器端完整 Agent 体验 |
| P5 | 文档同步（DATABASE.md/README.md/项目日志.md）+ 本地 commit（不 push） | 文档与代码一致 |

---

## 9. 风险与缓解

| 风险 | 缓解 |
|---|---|
| 文心 tool_calling 受限 | 文心 `functions` 能力各模型不一，MVP 对文心先支持单轮/多轮文本，tool 编排标注「受限」；`supportsTools` 检测后自动退化 |
| 数据库工具越权 | `DbQueryTool` 仅开放只读 system 视图，禁止任意写 |
| terminal/git 命令执行安全 | 默认禁用 + 白名单 + 超时 + 二次确认 + 路径穿越防护 |
| LLM 审查误报 | 分级（error/warning/info）+ 用户可逐条采纳/忽略（审阅模式已实现） |
| 新增工具拖慢对话 | DANGEROUS 默认关；工具调用并发受 `ToolRegistry` 既有编排控制；流式优先 |
| 工作流 prompt 冲突 | 单次只注入匹配度最高的工作流，按 `sort_order` 取首个 |
| 自定义 Tool 插件化 | 本期仅「声明式启用/参数覆盖」，完整插件 SDK 后续 |
| 前端高亮库 | 若 `renderMarkdown` 未集成语法高亮，引入 `highlight.js`（经 `@` 别名与 Vite 兼容） |
| 依赖 | JSON Schema 校验用轻量方案（`com.networknt:json-schema-validator` 或手写必需字段校验），避免重依赖 |
| 文档与代码漂移 | 本方案落地后同步更新 `DATABASE.md` / `README.md` / `docs/项目日志.md`（遵循"大功能变更须同步文档"约定） |

---

## 10. 待确认事项

1. 文心 tool_calling 受限是否接受？还是本期先不做文心工具编排？
2. `DbQueryTool` 只读范围是否仅 system/H2 元信息？
3. 自定义 Tool 是否需要「用户上传脚本」级别的插件能力，还是仅内置工具启停即可？
4. `terminal`/`git` 在本地 H2 + 浏览器 FS Access 场景下，git 操作的目标目录是否就是 `cachedDirHandle` 对应的本地目录（需 `git` 在该路径可用）。
5. 工作流是否需要支持"多步组合"（A→B→C）还是仅"单模板注入"即可（本方案按单模板注入，多步留作增强路线）。
6. `code_complete` 是否走独立轻接口（低延迟）还是复用 chat 流式（成本更低但稍慢）。
