// 消息通知模块请求层：封装通知列表、已读标记与未读数接口调用。
import { apiGet, apiPut } from './request'
import type { NotificationPageResult } from './types'

export const notificationsApi = {
  list: (params: {
    type?: string
    pageNum?: number
    pageSize?: number
    isRead?: number
  } = {}) => apiGet<NotificationPageResult>('/notifications', params),

  markAsRead: (id: number) => apiPut<void>(`/notifications/${id}/read`),

  markAllAsRead: () => apiPut<void>('/notifications/read-all'),

  unreadCount: () => apiGet<number>('/notifications/unread-count'),
}
