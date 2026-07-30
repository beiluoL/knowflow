// 单聊私信请求层：封装会话、消息、已读相关接口调用。
import { apiGet, apiPost, apiDelete } from './request'
import type {
  PrivateConversationVO,
  PrivateMessageVO,
  PrivateMessageSendPayload,
  PrivateMessagePageResult,
} from './types'

const API_PREFIX = '/im/private'

export const imApi = {
  /** 获取或创建与某用户的私聊会话 */
  getOrCreateConversation: (targetUserId: number) =>
    apiPost<PrivateConversationVO>(`${API_PREFIX}/conversations`, null, {
      params: { targetUserId },
    }),

  /** 我的私聊会话列表 */
  getMyConversations: () => apiGet<PrivateConversationVO[]>(`${API_PREFIX}/conversations`),

  /** 获取会话消息历史 */
  getMessages: (conversationId: number, page = 1, size = 20) =>
    apiGet<PrivateMessagePageResult>(`${API_PREFIX}/conversations/${conversationId}/messages`, {
      page,
      size,
    }),

  /** 发送私聊消息 */
  sendMessage: (data: PrivateMessageSendPayload) =>
    apiPost<PrivateMessageVO>(`${API_PREFIX}/messages`, data),

  /** 标记会话已读 */
  markAsRead: (conversationId: number) =>
    apiPost<void>(`${API_PREFIX}/conversations/${conversationId}/read`),

  /** 获取会话未读消息数 */
  getUnreadCount: (conversationId: number) =>
    apiGet<number>(`${API_PREFIX}/conversations/${conversationId}/unread`),

  /** 撤回消息 */
  recallMessage: (messageId: number) =>
    apiDelete<void>(`${API_PREFIX}/messages/${messageId}`),

  /** 上传文件（图片/文件） */
  uploadFile: (file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    // 注意：不要手动设置 Content-Type: multipart/form-data —— 必须交给 axios/浏览器
    // 自动追加 boundary，否则服务端无法解析 multipart 报文。
    return apiPost<{
      fileName: string
      fileUrl: string
      fileSize: number
      fileType: string
    }>(`${API_PREFIX}/upload`, formData)
  },

  /** 搜索用户（发起单聊时选择对象） */
  searchUsers: (keyword: string) =>
    apiGet<import('./types').UserVO[]>('/users/search', { keyword }),
}

export default imApi
