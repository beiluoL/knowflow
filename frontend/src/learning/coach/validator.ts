/**
 * Architecture-7：Coach 建议校验（纯函数，可单测，§125/§196-202/§536-537）。
 *
 * 任何 AI 返回的结构化建议都必须经过本地白名单校验：
 *  - appKey 必须命中本地 Registry（绝不接受 AI 提供的任意 route，§126-127）；
 *  - knowledgeId 若存在必须为正整数，且属于真实可访问的知识点（§201）；
 *  - actionType 必须为已知类型。
 * 校验失败 → valid:false，UI 不显示 CTA，但 AI 文本仍正常展示（§418-420）。
 */
import { getAppByKey, APP_DEFINITIONS } from '@/apps/registry'
import type { AppDefinition } from '@/apps/types'
import type { CoachSuggestion, CoachSuggestionValidation } from './types'

/** 已知可执行动作类型（与 NextAction 动作体系对齐）。 */
const KNOWN_ACTION_TYPES = new Set<string>([
  'REVIEW',
  'FLASHCARD',
  'QUIZ',
  'MISTAKE',
  'FEYNMAN',
  'KNOWLEDGE',
  'PATH',
  'TASK',
])

/**
 * 校验 AI 返回的结构化建议。
 * @param suggestion AI 解析出的建议（可能缺字段 / 为 null）
 * @param registry   本地 App 注册表（默认 APP_DEFINITIONS 白名单）
 */
export function validateCoachSuggestion(
  suggestion: CoachSuggestion | null | undefined,
  registry: AppDefinition[] = APP_DEFINITIONS,
): CoachSuggestionValidation {
  if (!suggestion || !suggestion.appKey) {
    return { valid: false, error: 'NO_SUGGESTION' }
  }
  const app = getAppByKey(suggestion.appKey)
  if (!app) {
    return { valid: false, error: 'UNKNOWN_APP_KEY' }
  }
  if (suggestion.knowledgeId != null) {
    if (!Number.isInteger(suggestion.knowledgeId) || suggestion.knowledgeId <= 0) {
      return { valid: false, error: 'INVALID_KNOWLEDGE_ID' }
    }
  }
  if (suggestion.actionType && !KNOWN_ACTION_TYPES.has(suggestion.actionType)) {
    return { valid: false, error: 'UNKNOWN_ACTION_TYPE' }
  }
  void registry
  return {
    valid: true,
    appKey: app.key,
    route: app.route,
    reason: suggestion.reason,
  }
}
