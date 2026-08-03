// 本地代码生成 API 封装：调用后端 Ollama + deepseek-coder 生成可落盘的代码文件。
import { apiGet, apiPost } from './request'
import type { CodeGenRequest, CodeGenResult, CodeGenHealth } from './types'

/**
 * 生成请求超时时间：本地 6.7B 模型首次加载 + 推理可能耗时数分钟，
 * 需要覆盖 axios 默认的 15s 全局超时，否则会在模型还在生成时被前端提前掐断。
 */
const GENERATE_TIMEOUT_MS = 5 * 60 * 1000

export const codeGenApi = {
  /** 根据自然语言指令生成代码文件 */
  generate: (data: CodeGenRequest) =>
    apiPost<CodeGenResult>('/code-gen/generate', data, { timeout: GENERATE_TIMEOUT_MS }),

  /** 生成前自检：Ollama 服务是否可达、目标模型是否已安装 */
  health: (model?: string) =>
    apiGet<CodeGenHealth>('/code-gen/health', model ? { model } : undefined),
}
