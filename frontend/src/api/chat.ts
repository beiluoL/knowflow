import { apiGet, apiPost, apiDelete } from './request'
import type { ConversationVO, MessageVO, ChatSendPayload } from './types'

export const chatApi = {
  conversations: () => apiGet<ConversationVO[]>('/chat/conversations'),
  createConversation: (title?: string) =>
    apiPost<ConversationVO>('/chat/conversations', null, { params: { title } }),
  deleteConversation: (id: number) => apiDelete<void>(`/chat/conversations/${id}`),
  messages: (id: number) => apiGet<MessageVO[]>(`/chat/conversations/${id}/messages`),
  send: (data: ChatSendPayload) => apiPost<MessageVO>('/chat/send', data),
}
