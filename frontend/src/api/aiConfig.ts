// AI 配置请求层：封装用户级 AI Key 配置（支持多模型）与平台模型列表接口。
import { apiGet, apiPost, apiDelete } from './request'
import type { UserAiConfigVO, UserAiConfigPayload, PlatformModelVO, AiTestResult } from './types'

export const aiConfigApi = {
  getConfig: () => apiGet<UserAiConfigVO>('/ai-config'),
  listConfigs: () => apiGet<UserAiConfigVO[]>('/ai-config/list'),
  saveConfig: (data: UserAiConfigPayload) => apiPost<UserAiConfigVO>('/ai-config', data),
  deleteConfig: (id: number) => apiDelete<void>(`/ai-config/${id}`),
  activate: (id: number) => apiPost<void>(`/ai-config/${id}/activate`),
  deleteAll: () => apiDelete<void>('/ai-config'),
  platformModels: () => apiGet<PlatformModelVO[]>('/ai-config/platform-models'),
  /** 测试 AI 配置连通性 */
  testConnection: (data: { provider: string; apiKey: string; baseUrl: string; model: string }) =>
    apiPost<AiTestResult>('/ai-config/test', data),
}
