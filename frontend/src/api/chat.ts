// 对话模块请求层：封装会话列表、消息收发等聊天相关接口调用。
import { apiGet, apiPost, apiDelete } from './request'
import type { ConversationVO, MessageVO, ChatSendPayload } from './types'

export const chatApi = {
  conversations: () => apiGet<ConversationVO[]>('/chat/conversations'),
  // 标题以查询参数附带，请求体留空（后端约定）
  createConversation: (title?: string) =>
    apiPost<ConversationVO>('/chat/conversations', null, { params: { title } }),
  deleteConversation: (id: number) => apiDelete<void>(`/chat/conversations/${id}`),
  messages: (id: number) => apiGet<MessageVO[]>(`/chat/conversations/${id}/messages`),
  send: (data: ChatSendPayload) => apiPost<MessageVO>('/chat/send', data),
  // 可用对话模型列表（多模型切换）
  models: () => apiGet<string[]>('/chat/models'),
}

/**
 * F1：流式发送消息回调集合。
 * - onToken：每收到一个增量 token 触发（参数为本次增量字符串）
 * - onDone：流正常结束时触发（参数为完整累积字符串）
 * - onError：流异常或后端推送 error 事件时触发
 */
export interface StreamCallbacks {
  onToken: (delta: string) => void
  onDone: (full: string) => void
  onError: (err: Error) => void
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
  // 直接从 localStorage 读 token，避免在 api 层引入 pinia store 造成循环依赖
  const token = localStorage.getItem('token') || ''
  const resp = await fetch('/api/chat/stream', {
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
  let buffer = ''      // 未完成的事件缓冲
  let full = ''        // 已累积的完整回复

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })

      // SSE 协议：事件之间用空行（\n\n）分隔
      const events = buffer.split('\n\n')
      // 最后一段可能不完整，留到下次拼接
      buffer = events.pop() ?? ''

      for (const evt of events) {
        if (!evt.trim()) continue
        const lines = evt.split('\n')
        // 找 event: 与 data: 行（SSE 规范允许前导空格）
        const eventName = lines
          .find((l) => l.startsWith('event:'))
          ?.slice('event:'.length)
          .trim()
        const dataLine = lines.find((l) => l.startsWith('data:'))
        if (!dataLine) continue
        const dataStr = dataLine.slice('data:'.length).trim()
        let payload: { content?: string; error?: string } = {}
        try {
          payload = dataStr ? JSON.parse(dataStr) : {}
        } catch {
          // 非 JSON payload（如纯字符串），降级直接当 content
          payload = { content: dataStr }
        }

        if (eventName === 'delta' && payload.content) {
          full += payload.content
          cb.onToken(payload.content)
        } else if (eventName === 'done') {
          // 后端 done 事件携带完整文本，覆盖本地累积（防止 token 拼接遗漏）
          const finalContent = payload.content ?? full
          cb.onDone(finalContent)
          return
        } else if (eventName === 'error') {
          cb.onError(new Error(payload.error || 'AI 流式调用失败'))
          return
        }
      }
    }
    // 流自然结束但未收到 done 事件：用已累积内容触发 onDone
    if (full) cb.onDone(full)
  } catch (err) {
    // 用户主动 abort 时 err 为 AbortError，不当作错误抛出
    if (err instanceof DOMException && err.name === 'AbortError') {
      if (full) cb.onDone(full)
      return
    }
    cb.onError(err instanceof Error ? err : new Error(String(err)))
  }
}
