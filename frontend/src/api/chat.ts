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
}
