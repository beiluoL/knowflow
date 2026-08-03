// Ollama 本地模型管理 API 封装：配置 CRUD、连接测试、模型列表、加载/卸载/删除。
import { apiGet, apiPost, apiPut, apiDelete } from './request'
import type {
  OllamaConfigVO,
  OllamaModelVO,
  OllamaTestResult,
  OllamaOpResult,
} from './types'

export const ollamaApi = {
  // ==================== 配置管理 ====================

  /** 获取当前用户的 Ollama 配置 */
  getConfig: () => apiGet<OllamaConfigVO>('/ollama/config'),

  /** 更新 Ollama 配置 */
  updateConfig: (data: OllamaConfigVO) => apiPut<OllamaConfigVO>('/ollama/config', data),

  /** 导入 Ollama 配置 */
  importConfig: (data: OllamaConfigVO) => apiPost<OllamaConfigVO>('/ollama/config/import', data),

  // ==================== 连接测试 ====================

  /** 测试 Ollama 服务连接 */
  testConnection: (baseUrl?: string) =>
    apiPost<OllamaTestResult>('/ollama/test', baseUrl ? { baseUrl } : {}),

  // ==================== 模型列表 ====================

  /** 获取已安装的模型列表 */
  listModels: (baseUrl?: string) =>
    apiGet<OllamaModelVO[]>('/ollama/models', baseUrl ? { baseUrl } as any : undefined),

  // ==================== 模型加载/卸载 ====================

  /** 加载模型到内存 */
  loadModel: (modelName: string, baseUrl?: string) =>
    apiPost<OllamaOpResult>(`/ollama/models/${encodeURIComponent(modelName)}/load`,
      undefined, baseUrl ? { params: { baseUrl } } as any : undefined),

  /** 从内存卸载模型 */
  unloadModel: (modelName: string, baseUrl?: string) =>
    apiPost<OllamaOpResult>(`/ollama/models/${encodeURIComponent(modelName)}/unload`,
      undefined, baseUrl ? { params: { baseUrl } } as any : undefined),

  /** 删除已安装的模型 */
  deleteModel: (modelName: string, baseUrl?: string) =>
    apiDelete<OllamaOpResult>(`/ollama/models/${encodeURIComponent(modelName)}`,
      baseUrl ? { baseUrl } : undefined),

  /** 获取将 Ollama 模型添加为 Agent 配置所需的参数 */
  getAgentConfig: (modelName: string) =>
    apiPost<Record<string, string>>(`/ollama/models/${encodeURIComponent(modelName)}/add-to-agent`),
}
