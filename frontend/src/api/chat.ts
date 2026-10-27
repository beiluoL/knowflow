// 对话模块请求层：封装会话列表、消息收发等聊天相关接口调用。
import { apiGet, apiPost, apiDelete, apiPatch } from './request'
import type {
  ChatModelCapabilityVO,
  ConversationVO,
  MessageVO,
  ChatSendPayload,
  ChatRegeneratePayload,
} from './types'
import { SseEventParser, parseSseData } from './sse'

export const chatApi = {
  conversations: (archived = false) => apiGet<ConversationVO[]>('/chat/conversations', {archived}),
  updateState: (id: number, state: {pinned?: boolean; archived?: boolean}) => apiPatch<ConversationVO>(`/chat/conversations/${id}/state`,state),
  shares: (id: number) => apiGet<ChatShare[]>(`/chat/conversations/${id}/shares`),
  createShare: (id: number, expiresInHours: number) => apiPost<ChatShare>(`/chat/conversations/${id}/shares`,{expiresInHours,confirmed:true}),
  revokeShare: (id: number, shareId: number) => apiDelete<void>(`/chat/conversations/${id}/shares/${shareId}`),
  // 标题以查询参数附带，请求体留空（后端约定）
  createConversation: (title?: string) =>
    apiPost<ConversationVO>('/chat/conversations', null, { params: { title } }),
  renameConversation: (id: number, title: string) => apiPatch<ConversationVO>(`/chat/conversations/${id}`, {title}),
  deleteConversation: (id: number) => apiDelete<void>(`/chat/conversations/${id}`),
  messages: (id: number) => apiGet<MessageVO[]>(`/chat/conversations/${id}/messages`),
  send: (data: ChatSendPayload) => apiPost<MessageVO>('/chat/send', data),
  // 可用对话模型列表（多模型切换）
  models: () => apiGet<string[]>('/chat/models'),
  /** 当前用户默认通道的模型深度思考能力（是否支持推理 / 推理摘要 / 工具调用） */
  capabilities: () => apiGet<ChatModelCapabilityVO>('/chat/capabilities'),
  /** 编辑用户消息（版本分支：后端截断后续回答，随后通过 regenerateChat 重新生成） */
  updateMessage: (id: number, content: string) => apiPatch<MessageVO>(`/chat/messages/${id}`, {content}),
}

/** RAG 单步执行记录（仅后端真实执行过的步骤）。 */
export interface RagStepEvent {
  stepId?: string
  type: string
  label?: string
  status: 'success' | 'empty' | 'skipped' | 'error' | string
  detail?: string
  durationMs?: number
  resultCount?: number
  sequence?: number
  timestamp?: number
  startedAt?: number
}

export interface RagDoneEvent {
  traceId?: string
  strategy?: string
  accessibleDocCount?: number
  resultCount?: number
  evidenceSufficient?: boolean
  durationMs?: number
  startedAt?: number
  endedAt?: number
  success?: boolean
}

/** Agent 工具事件（当前聊天链路尚未接入真实工具，仅预留协议；前端只在收到事件时展示）。 */
export interface ToolEvent {
  stepId?: string
  toolName?: string
  label?: string
  description?: string
  status?: string
  durationMs?: number
  args?: unknown
  result?: string
  error?: string
  sequence?: number
  timestamp?: number
}

/**
 * 流式回调集合。
 *
 * 旧协议（delta / done / error）保持必填兼容；
 * 新协议事件全部可选：只有后端真实推送时才触发，前端不得自行补造。
 */
export interface StreamCallbacks {
  /** 旧协议增量（后端仍继续推送，保证旧客户端可用） */
  onToken: (delta: string) => void
  /** 流正常结束时触发（参数为完整累积字符串） */
  onDone: (full: string) => void
  /** 流异常或后端推送 error 事件时触发 */
  onError: (err: Error) => void
  /** 回复开始：携带真实 conversationId 与用户消息 ID，用于丢弃迟到事件 */
  onStart?: (data: {conversationId?: number; userMessageId?: number; regenerate?: boolean}) => void
  /** 阶段变化：retrieving | generating | thinking | answering */
  onStatus?: (data: {phase?: string}) => void
  /** 推理内容 / 推理摘要增量 */
  onReasoning?: (data: {content: string; kind?: string}) => void
  /** 当前思考段结束（携带真实耗时） */
  onReasoningDone?: (data: {durationMs?: number; kind?: string}) => void
  /** 最终回答增量（新协议） */
  onAnswer?: (data: {content: string}) => void
  onRagStart?: (data: {startedAt?: number}) => void
  onRagStep?: (data: RagStepEvent) => void
  onRagDone?: (data: RagDoneEvent) => void
  onToolStart?: (data: ToolEvent) => void
  onToolProgress?: (data: ToolEvent) => void
  onToolDone?: (data: ToolEvent) => void
  onToolError?: (data: ToolEvent) => void
  /** 落库后的真实助手消息 ID */
  onMessage?: (data: {messageId?: number; conversationId?: number; truncated?: number}) => void
}

/**
 * F1：通过 SSE 流式发送对话消息，逐 token 推送 AI 回复。
 * 使用原生 fetch + ReadableStream 消费 text/event-stream，避开 axios 整段缓冲。
 *
 * @param data   聊天请求体（与 chatApi.send 一致）
 * @param cb     流式回调
 * @param signal 可选的 AbortSignal，用于"停止"按钮中断生成
 */
export async function streamChat(
  data: ChatSendPayload,
  cb: StreamCallbacks,
  signal?: AbortSignal,
): Promise<void> {
  return consumeSse('/api/chat/stream', data, cb, signal)
}

/**
 * 重新生成回答：后端会真实调用模型并落库新的 assistant 消息，
 * 事件协议与 /chat/stream 完全一致。
 */
export async function regenerateChat(
  messageId: number,
  data: ChatRegeneratePayload,
  cb: StreamCallbacks,
  signal?: AbortSignal,
): Promise<void> {
  return consumeSse(`/api/chat/messages/${messageId}/regenerate`, data, cb, signal)
}

/** 通用 SSE 消费器：按事件名分发，未知事件忽略（向前兼容）。 */
async function consumeSse(
  url: string,
  data: unknown,
  cb: StreamCallbacks,
  signal?: AbortSignal,
): Promise<void> {
  // 直接从 localStorage 读 token，避免在 api 层引入 pinia store 造成循环依赖
  const token = localStorage.getItem('token') || ''
  const resp = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(data),
    signal,
  })

  if (!resp.ok || !resp.body) {
    throw new Error(`流式请求失败：HTTP ${resp.status}`)
  }

  const reader = resp.body.getReader()
  const decoder = new TextDecoder('utf-8')
  const parser = new SseEventParser()
  let full = ''        // 已累积的完整回复
  let finished = false // 已收到 done 事件（服务端已确认完成）
  let sawAnswer = false // 已收到新协议 answer 事件：之后的兼容 delta 不再重复累积
  let sawMessage = false
  let sawError = false

  const dispatch = (eventName: string, payload: Record<string, unknown>) => {
    const text = typeof payload.content === 'string' ? payload.content : undefined
    switch (eventName) {
      case 'start':
        cb.onStart?.({
          conversationId: typeof payload.conversationId === 'number' ? payload.conversationId : undefined,
          userMessageId: typeof payload.userMessageId === 'number' ? payload.userMessageId : undefined,
          regenerate: payload.regenerate === true,
        })
        break
      case 'status':
        cb.onStatus?.({phase: typeof payload.phase === 'string' ? payload.phase : undefined})
        break
      case 'reasoning':
        if (text) cb.onReasoning?.({content: text, kind: typeof payload.kind === 'string' ? payload.kind : undefined})
        break
      case 'reasoning_done':
        cb.onReasoningDone?.({
          durationMs: typeof payload.durationMs === 'number' ? payload.durationMs : undefined,
          kind: typeof payload.kind === 'string' ? payload.kind : undefined,
        })
        break
      case 'answer':
        if (text) {
          sawAnswer = true
          full += text
          cb.onAnswer?.({content: text})
          cb.onToken(text)
        }
        break
      case 'delta':
        // 后端同时推送 answer 与 delta：已见 answer 时忽略 delta，避免重复累积
        if (text && !sawAnswer) {
          full += text
          cb.onToken(text)
        }
        break
      case 'rag_start':
        cb.onRagStart?.({startedAt: typeof payload.startedAt === 'number' ? payload.startedAt : undefined})
        break
      case 'rag_step':
        cb.onRagStep?.(payload as unknown as RagStepEvent)
        break
      case 'rag_done':
        cb.onRagDone?.(payload as unknown as RagDoneEvent)
        break
      case 'tool_start':
        cb.onToolStart?.(payload as unknown as ToolEvent)
        break
      case 'tool_progress':
        cb.onToolProgress?.(payload as unknown as ToolEvent)
        break
      case 'tool_done':
        cb.onToolDone?.(payload as unknown as ToolEvent)
        break
      case 'tool_error':
        cb.onToolError?.(payload as unknown as ToolEvent)
        break
      case 'message':
        sawMessage = true
        cb.onMessage?.({
          messageId: typeof payload.messageId === 'number' ? payload.messageId : undefined,
          conversationId: typeof payload.conversationId === 'number' ? payload.conversationId : undefined,
          truncated: typeof payload.truncated === 'number' ? payload.truncated : undefined,
        })
        break
      case 'done': {
        // 后端 done 事件携带完整文本，覆盖本地累积（防止 token 拼接遗漏）
        const finalContent = text ?? full
        cb.onDone(finalContent)
        finished = true
        break
      }
      case 'error':
        sawError = true
        cb.onError(new Error(typeof payload.error === 'string' ? payload.error : 'AI 流式调用失败'))
        break
      default:
        break
    }
  }

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (signal?.aborted) return
      if (done) {
        for (const evt of parser.flush()) dispatch(evt.event, parseSseData(evt.data))
        break
      }
      for (const evt of parser.push(decoder.decode(value, {stream: true}))) {
        dispatch(evt.event, parseSseData(evt.data))
      }
      // 后端 error 事件表示本次生成失败，不再继续等待
      if (sawError) break
      // done 之后仍需读取 message 事件（真实消息 ID），随后服务端会关闭连接
      if (finished && sawMessage) break
    }
    // EOF 不代表服务端确认完成，保留部分内容并交由页面显示可恢复错误。
    if (!finished && !sawError) cb.onError(new Error('连接已结束，但未收到完成确认'))
  } catch (err) {
    // 用户主动 abort 时 err 为 AbortError，不当作错误抛出
    if (signal?.aborted || (err instanceof DOMException && err.name === 'AbortError')) {
      return
    }
    cb.onError(err instanceof Error ? err : new Error(String(err)))
  } finally {
    if (!finished && !sawError) void reader.cancel().catch(() => {})
    reader.releaseLock()
  }
}

export interface ChatShare {id:number;urlPath?:string;createdAt?:string;expiresAt:string;revoked?:boolean;redactions?:string[]}
export interface PublicChatSnapshot {title:string;createdAt:string;expiresAt:string;messages:{role:string;content:string;createTime?:string}[];redactions?:string[]}
/** Public capability endpoint deliberately sends neither bearer credentials nor referrer. */
export async function readPublicChat(token:string):Promise<PublicChatSnapshot>{
  if(!/^[A-Za-z0-9_-]{40,100}$/.test(token))throw new Error('分享不存在、已过期或已撤销')
  const response=await fetch(`/api/public/chat-shares/${encodeURIComponent(token)}`,{credentials:'omit',cache:'no-store',referrerPolicy:'no-referrer'})
  const result=await response.json()
  if(!response.ok||result.code!==200)throw new Error('分享不存在、已过期或已撤销')
  return result.data
}
