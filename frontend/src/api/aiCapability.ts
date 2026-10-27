// Phase 3H：AI 能力状态请求层。
// 后端状态由「配置 + 熔断器 + 近期失败」聚合，不触发付费健康检查；前端轮询是免费的。
import { apiGet } from './request'

/** 与后端 AiCapabilityStatus 对齐的 7 态。 */
export type AiCapabilityStatusCode =
  | 'AVAILABLE'
  | 'UNCONFIGURED'
  | 'DEGRADED'
  | 'RATE_LIMITED'
  | 'TIMEOUT'
  | 'UNAVAILABLE'
  | 'DISABLED'

export interface AiCapabilityStatusVO {
  /** 系统级开关（knowflow.ai.capability.enabled）。 */
  enabled: boolean
  /** 是否存在有效模型配置（系统/用户级 Key）。 */
  configured: boolean
  status: AiCapabilityStatusCode
  /** 当前作用域 provider:model，仅展示用，不含任何 Key。 */
  provider?: string
  /** 最近一次失败分类枚举名（监控/调试语义，无堆栈无细节）。 */
  recentFailureKind?: string | null
  /** 前端是否应进入基础（无 AI）模式。 */
  useBasicMode: boolean
}

/** 失败分类 → 用户可理解的中文短提示（不泄露内部细节）。 */
export const AI_FAILURE_KIND_LABELS: Record<string, string> = {
  UNCONFIGURED: 'AI 未配置',
  INVALID_KEY: 'API Key 无效',
  INVALID_REQUEST: 'AI 请求被拒绝',
  INVALID_RESPONSE: 'AI 返回格式异常',
  UNSUPPORTED_CAPABILITY: '当前模型不支持该能力',
  RATE_LIMITED: 'AI 服务繁忙',
  PROVIDER_ERROR: 'AI 供应商暂时异常',
  TIMEOUT: 'AI 响应超时',
  NETWORK_ERROR: '无法连接 AI 服务',
  QUOTA_EXHAUSTED: 'AI 额度不足',
  CIRCUIT_OPEN: 'AI 熔断恢复中',
  CONCURRENCY_LIMITED: 'AI 并发已满',
  BUDGET_EXCEEDED: '今日 AI 额度已用完',
  USER_DISABLED: '已关闭 AI 增强',
  SYSTEM_DISABLED: 'AI 增强已关闭',
  CANCELLED: '已取消',
  UNKNOWN: 'AI 暂不可用',
}

export const AI_STATUS_LABELS: Record<AiCapabilityStatusCode, string> = {
  AVAILABLE: 'AI 可用',
  UNCONFIGURED: 'AI 未配置',
  DEGRADED: 'AI 降级中',
  RATE_LIMITED: 'AI 限流中',
  TIMEOUT: 'AI 超时',
  UNAVAILABLE: 'AI 不可用',
  DISABLED: 'AI 已关闭',
}

export function failureKindLabel(kind?: string | null): string {
  if (!kind) return ''
  return AI_FAILURE_KIND_LABELS[kind] || kind
}

export const aiCapabilityApi = {
  status: () => apiGet<AiCapabilityStatusVO>('/ai-capability/status'),
}
