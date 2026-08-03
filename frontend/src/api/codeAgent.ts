// 编程 Agent 请求层：封装模型管理、SSE 流式对话、代码执行、健康检查、会话管理、模型监测。
import { apiGet, apiPost, apiPut, apiDelete } from './request'
import type {
  AgentModelsResult,
  AgentChatPayload,
  AgentChatMessage,
  AgentSessionVO,
  AgentMessageVO,
  AgentMessagePage,
  AgentMode,
  AgentStatsVO,
  AgentToolVO,
  AgentToolCallVO,
  AgentToolStatVO,
  AgentIntentRequest,
  AgentIntentResult,
  Ambiguity,
  AgentEvalRequest,
  AgentEvalResult,
  AgentSessionPageResult,
  ToolPermission,
} from './types'
import type { CodeRunPayload, CodeRunResultDTO } from './codeRun'

/** 工具执行事件负载（tool-start / tool-confirm 共用）。 */
export interface AgentToolEvent {
  callId: string
  tool: string
  /** 入参 JSON 字符串 */
  args: string
  permission: ToolPermission
  /** 仅 tool-confirm 事件携带：确认等待超时秒数 */
  timeoutSeconds?: number
}

/** 工具执行完成事件负载。 */
export interface AgentToolEndEvent {
  callId: string
  tool: string
  success: boolean
  output: string
  latencyMs: number
}

/** SSE 流式对话回调集合。 */
export interface AgentStreamCallbacks {
  /** 逐 token 增量推送 */
  onDelta?: (content: string) => void
  /** 流结束，携带完整文本 */
  onDone?: (fullContent: string) => void
  /** 后端推送的会话ID（用于前端同步当前 sessionId） */
  onSession?: (sessionId: number) => void
  /** 第 N 轮模型推理开始（ReAct 循环） */
  onThinking?: (iter: number) => void
  /** 工具开始执行 */
  onToolStart?: (event: AgentToolEvent) => void
  /** 工具执行完成 */
  onToolEnd?: (event: AgentToolEndEvent) => void
  /** 高危工具待用户二次确认，需调用 confirmTool 回传结果 */
  onToolConfirm?: (event: AgentToolEvent) => void
  /** 异常 */
  onError?: (error: string) => void
}

/** Agent 工具流式对话请求体。 */
export interface AgentToolChatPayload {
  content: string
  sessionId?: number | null
  configId?: number | null
}

export const codeAgentApi = {
  /** 列出可用模型（用户已配置 + 平台预设） */
  models: () => apiGet<AgentModelsResult>('/agent/models'),

  /**
   * 流式对话（SSE）：用 fetch + ReadableStream 手动解析 SSE 帧。
   * <p>
   * 因 EventSource 仅支持 GET，而本接口为 POST + JSON body，
   * 故复用 knowledgeImport 的 SSE 解析模式。
   *
   * @param payload   对话请求（messages + configId + 可选文件上下文）
   * @param callbacks SSE 事件回调
   * @returns 取消函数，调用后中止流读取
   */
  chatStream: (
    payload: AgentChatPayload,
    callbacks: AgentStreamCallbacks,
  ): { cancel: () => void } => {
    return runAgentSse('/api/agent/chat/stream', payload, callbacks)
  },

  /**
   * Agent 工具流式对话（含 ReAct 工具编排）。
   * 相比 chatStream 额外推送 thinking / tool-start / tool-end / tool-confirm 事件。
   */
  agentStream: (
    payload: AgentToolChatPayload,
    callbacks: AgentStreamCallbacks,
  ): { cancel: () => void } => {
    return runAgentSse('/api/agent/chat/agent-stream', payload, callbacks)
  },

  /** 回传高危工具的二次确认结果 */
  confirmTool: (callId: string, approved: boolean) =>
    apiPost<void>('/agent/tool-confirm', { callId, approved }),

  /** 执行代码（复用代码沙箱） */
  execute: (payload: CodeRunPayload) => apiPost<CodeRunResultDTO>('/agent/execute', payload),

  /** 检测模型配置可用性；返回 JSON 字符串 { ok, latencyMs, error? } */
  healthCheck: (configId?: number | null) =>
    apiPost<string>('/agent/health-check', undefined, {
      params: configId != null ? { configId } : {},
    } as any),

  // ==================== 会话管理 ====================

  /** 列出当前用户的所有会话 */
  listSessions: () => apiGet<AgentSessionVO[]>('/agent/sessions'),

  /** 分页列出会话（侧边栏「加载更多」） */
  pageSessions: (params: { current?: number; size?: number; keyword?: string }) =>
    apiGet<AgentSessionPageResult>('/agent/sessions/page', params as Record<string, unknown>),

  /** 创建新会话 */
  createSession: (data: { title?: string; configId?: number; projectDir?: string }) =>
    apiPost<AgentSessionVO>('/agent/sessions', data),

  /** 重命名会话 */
  renameSession: (id: number, title: string) =>
    apiPut<AgentSessionVO>(`/agent/sessions/${id}`, { title }),

  /** 更新会话配置（模型 / 项目目录 / 上下文窗口 / 运行模式） */
  updateSession: (
    id: number,
    data: {
      title?: string
      configId?: number | null
      projectDir?: string | null
      contextWindow?: number
      agentMode?: AgentMode
    },
  ) => apiPut<AgentSessionVO>(`/agent/sessions/${id}`, data),

  /** 删除会话（连同消息） */
  deleteSession: (id: number) => apiDelete<void>(`/agent/sessions/${id}`),

  /** 获取会话的历史消息（全量） */
  getMessages: (sessionId: number) =>
    apiGet<AgentMessageVO[]>(`/agent/sessions/${sessionId}/messages`),

  /**
   * 分页获取会话历史消息（游标分页）。
   * 首次不传 beforeId 取最新一页，之后传上一次返回的 nextCursor 向上加载更早消息。
   */
  pageMessages: (sessionId: number, params: { size?: number; beforeId?: number | null }) =>
    apiGet<AgentMessagePage>(
      `/agent/sessions/${sessionId}/messages/page`,
      params as Record<string, unknown>,
    ),

  // ==================== 工具管理与调用链 ====================

  /** 工具列表（含当前用户启用状态） */
  listTools: () => apiGet<AgentToolVO[]>('/agent/tools'),

  /** 启用/禁用工具并配置写授权 */
  setTool: (name: string, data: { enabled: boolean; allowWrite?: boolean }) =>
    apiPut<void>(`/agent/tools/${name}`, data),

  /** 会话级工具调用链（可视化） */
  getCallChain: (sessionId: number) =>
    apiGet<AgentToolCallVO[]>(`/agent/tools/sessions/${sessionId}/call-chain`),

  /** 会话级工具调用统计 */
  getCallStats: (sessionId: number) =>
    apiGet<AgentToolStatVO[]>(`/agent/tools/sessions/${sessionId}/call-stats`),

  // ==================== 模型监测 ====================

  /** 获取模型监测统计数据（用于仪表盘） */
  getStats: (rangeHours = 24) =>
    apiGet<AgentStatsVO>('/agent/stats', { rangeHours } as any),

  // ==================== 意图识别与答案生成优化（P1~P3）====================

  /** P1 多轮上下文意图分类（含歧义点） */
  detectIntent: (payload: AgentIntentRequest) =>
    apiPost<AgentIntentResult>('/agent/intent', payload),

  /** P2 结构探针 + 语义歧义检测 */
  detectAmbiguities: (payload: AgentIntentRequest) =>
    apiPost<Ambiguity[]>('/agent/ambiguities', payload),

  /** P3 输出准确率评估闭环 */
  evaluate: (payload: AgentEvalRequest) =>
    apiPost<AgentEvalResult>('/agent/evaluate', payload),
}

/**
 * 通用 Agent SSE 执行器：fetch + ReadableStream 手动解析 SSE 数据帧。
 * <p>
 * 与 knowledgeImport 的 runSseImport 类似，但事件类型为 delta/done/error。
 */
function runAgentSse(
  url: string,
  payload: AgentChatPayload | AgentToolChatPayload,
  callbacks: AgentStreamCallbacks,
): { cancel: () => void } {
  const controller = new AbortController()

  const run = async () => {
    try {
      const token = localStorage.getItem('token')
      const resp = await fetch(url, {
        method: 'POST',
        body: JSON.stringify(payload),
        headers: {
          'Content-Type': 'application/json',
          Accept: 'text/event-stream',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        signal: controller.signal,
      })

      if (!resp.ok) {
        const text = await resp.text().catch(() => '')
        callbacks.onError?.(`对话请求失败（${resp.status}）：${text || resp.statusText}`)
        return
      }

      const reader = resp.body?.getReader()
      if (!reader) {
        callbacks.onError?.('无法读取响应流')
        return
      }

      const decoder = new TextDecoder('utf-8')
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })

        let frameEnd: number
        while ((frameEnd = buffer.indexOf('\n\n')) >= 0) {
          const frame = buffer.slice(0, frameEnd)
          buffer = buffer.slice(frameEnd + 2)
          parseAgentSseFrame(frame, callbacks)
        }
      }
      if (buffer.trim()) {
        parseAgentSseFrame(buffer, callbacks)
      }
    } catch (e: unknown) {
      if ((e as Error).name === 'AbortError') return
      callbacks.onError?.((e as Error).message || '对话流读取异常')
    }
  }

  void run()

  return {
    cancel: () => {
      controller.abort()
    },
  }
}

/** 解析单个 SSE 帧（event: xxx / data: json）。 */
function parseAgentSseFrame(frame: string, callbacks: AgentStreamCallbacks) {
  const lines = frame.split('\n')
  let eventName = 'message'
  let dataStr = ''
  for (const line of lines) {
    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataStr += line.slice(5).trim()
    }
  }
  if (!dataStr) return

  interface SseData {
    content?: string
    error?: string
    sessionId?: number
    iter?: number
    callId?: string
    tool?: string
    args?: string
    permission?: ToolPermission
    timeoutSeconds?: number
    success?: boolean
    output?: string
    latencyMs?: number
  }

  let data: SseData
  try {
    data = JSON.parse(dataStr)
  } catch {
    return
  }

  switch (eventName) {
    case 'delta':
      if (data.content) callbacks.onDelta?.(data.content)
      break
    case 'done':
      callbacks.onDone?.(data.content || '')
      break
    case 'session':
      if (data.sessionId) callbacks.onSession?.(data.sessionId)
      break
    case 'thinking':
      callbacks.onThinking?.(data.iter ?? 1)
      break
    case 'tool-start':
      if (data.callId && data.tool) {
        callbacks.onToolStart?.({
          callId: data.callId,
          tool: data.tool,
          args: data.args || '{}',
          permission: data.permission || 'SAFE',
        })
      }
      break
    case 'tool-end':
      if (data.callId && data.tool) {
        callbacks.onToolEnd?.({
          callId: data.callId,
          tool: data.tool,
          success: data.success === true,
          output: data.output || '',
          latencyMs: data.latencyMs ?? 0,
        })
      }
      break
    case 'tool-confirm':
      if (data.callId && data.tool) {
        callbacks.onToolConfirm?.({
          callId: data.callId,
          tool: data.tool,
          args: data.args || '{}',
          permission: data.permission || 'DANGEROUS',
          timeoutSeconds: data.timeoutSeconds,
        })
      }
      break
    case 'error':
      callbacks.onError?.(data.error || '对话失败')
      break
  }
}

/** 构建对话消息的便捷工厂。 */
export function msg(role: AgentChatMessage['role'], content: string): AgentChatMessage {
  return { role, content }
}
