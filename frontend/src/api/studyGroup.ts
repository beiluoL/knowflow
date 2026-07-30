// 学习小组请求层：封装小组、成员、消息相关接口调用。
import { apiGet, apiPost, apiPut, apiDelete } from './request'
import type {
  StudyGroupVO,
  StudyGroupMemberVO,
  GroupMessageVO,
  StudyGroupCreatePayload,
  GroupMessageSendPayload,
  GroupInvitePayload,
  GroupMessagePageResult,
} from './types'

const API_PREFIX = '/study-groups'

export const studyGroupApi = {
  /** 获取我加入的小组列表 */
  getMyGroups: () => apiGet<StudyGroupVO[]>(`${API_PREFIX}/my`),

  /** 获取推荐小组列表 */
  getRecommendGroups: (limit = 10) =>
    apiGet<StudyGroupVO[]>(`${API_PREFIX}/recommend`, { limit }),

  /** 获取小组详情 */
  getGroupDetail: (id: number) => apiGet<StudyGroupVO>(`${API_PREFIX}/${id}`),

  /** 创建小组 */
  createGroup: (data: StudyGroupCreatePayload) =>
    apiPost<StudyGroupVO>(`${API_PREFIX}`, data),

  /** 更新小组信息 */
  updateGroup: (id: number, data: StudyGroupCreatePayload) =>
    apiPut<StudyGroupVO>(`${API_PREFIX}/${id}`, data),

  /** 删除小组 */
  deleteGroup: (id: number) => apiDelete<void>(`${API_PREFIX}/${id}`),

  /** 获取小组成员列表 */
  getGroupMembers: (groupId: number) =>
    apiGet<StudyGroupMemberVO[]>(`${API_PREFIX}/${groupId}/members`),

  /** 邀请成员 */
  inviteMember: (data: GroupInvitePayload) =>
    apiPost<void>(`${API_PREFIX}/invite`, data),

  /** 加入公开小组 */
  joinGroup: (groupId: number) =>
    apiPost<void>(`${API_PREFIX}/${groupId}/join`),

  /** 退出小组 */
  leaveGroup: (groupId: number) =>
    apiPost<void>(`${API_PREFIX}/${groupId}/leave`),

  /** 移除成员 */
  removeMember: (groupId: number, memberId: number) =>
    apiDelete<void>(`${API_PREFIX}/${groupId}/members/${memberId}`),

  /** 更新成员角色 */
  updateMemberRole: (groupId: number, memberId: number, role: string) =>
    apiPut<void>(`${API_PREFIX}/${groupId}/members/${memberId}/role`, null, {
      params: { role },
    }),

  /** 获取消息列表 */
  getMessages: (groupId: number, page = 1, size = 20) =>
    apiGet<GroupMessagePageResult>(`${API_PREFIX}/${groupId}/messages`, {
      page,
      size,
    }),

  /** 发送消息 */
  sendMessage: (data: GroupMessageSendPayload) =>
    apiPost<GroupMessageVO>(`${API_PREFIX}/messages`, data),

  /** 撤回消息 */
  recallMessage: (messageId: number) =>
    apiDelete<void>(`${API_PREFIX}/messages/${messageId}`),

  /** 标记已读 */
  markAsRead: (groupId: number) =>
    apiPost<void>(`${API_PREFIX}/${groupId}/read`),

  /** 获取未读消息数 */
  getUnreadCount: (groupId: number) =>
    apiGet<number>(`${API_PREFIX}/${groupId}/unread`),

  /** 更新小组公告 */
  updateAnnouncement: (groupId: number, announcement: string) =>
    apiPut<void>(`${API_PREFIX}/${groupId}/announcement`, null, {
      params: { announcement },
    }),

  /** 关联学习计划 */
  linkLearningPlan: (groupId: number, planId: number) =>
    apiPut<void>(`${API_PREFIX}/${groupId}/learning-plan`, null, {
      params: { planId },
    }),

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
}

export default studyGroupApi
