// AI 配置请求层：封装用户级 AI Key 配置与平台模型列表接口。
import { apiGet, apiPost, apiDelete } from './request'
import type { UserAiConfigVO, UserAiConfigPayload, PlatformModelVO } from './types'

export const aiConfigApi = {
  getConfig: () => apiGet<UserAiConfigVO>('/ai-config'),
  saveConfig: (data: UserAiConfigPayload) => apiPost<UserAiConfigVO>('/ai-config', data),
  deleteConfig: () => apiDelete<void>('/ai-config'),
  platformModels: () => apiGet<PlatformModelVO[]>('/ai-config/platform-models'),
}
