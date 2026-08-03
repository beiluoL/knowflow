// AI 配置请求层：封装用户级 AI Key 配置（支持多模型）与平台模型列表接口。
import { apiGet, apiPost, apiDelete } from './request'
import type { UserAiConfigVO, UserAiConfigPayload, PlatformModelVO } from './types'

export const aiConfigApi = {
  /** 获取当前用户的激活配置（兼容通用 Chat） */
  getConfig: () => apiGet<UserAiConfigVO>('/ai-config'),
  /** 列出当前用户的全部模型配置（编程 Agent 用） */
  listConfigs: () => apiGet<UserAiConfigVO[]>('/ai-config/list'),
  /** 保存（新增或更新）一条模型配置；payload.id 存在则更新，否则新增 */
  saveConfig: (data: UserAiConfigPayload) => apiPost<UserAiConfigVO>('/ai-config', data),
  /** 删除一条模型配置 */
  deleteConfig: (id: number) => apiDelete<void>(`/ai-config/${id}`),
  /** 设置某条配置为激活（通用 Chat 使用） */
  activate: (id: number) => apiPost<void>(`/ai-config/${id}/activate`),
  /** 删除全部配置（切换回平台模型） */
  deleteAll: () => apiDelete<void>('/ai-config'),
  /** 获取平台提供的全部模型列表（云端 + 本地） */
  platformModels: () => apiGet<PlatformModelVO[]>('/ai-config/platform-models'),
}
