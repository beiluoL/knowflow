/**
 * Architecture-7：Coach 响应解析（纯函数，不依赖任何 LLM，可单测，§116/§194-196/§538-539）。
 *
 * AI 回复以自然语言为主；若模型返回结构化建议（```json 代码块或行内 {...}），
 * 解析后必须经 validateCoachSuggestion 校验：通过才带上 suggestion + validation，
 * 否则仅返回纯文本消息（安全降级，§305/§350）。绝不信任模型返回内容直接执行。
 */
import type { CoachResponse, CoachSuggestion } from './types'
import { validateCoachSuggestion } from './validator'

/**
 * 解析 AI 回复文本。
 * @param raw 模型原始回复
 */
export function parseCoachResponse(raw: string): CoachResponse {
  const text = raw?.trim() ?? ''
  const extracted = extractSuggestion(text)
  if (extracted) {
    const validation = validateCoachSuggestion(extracted)
    if (validation.valid) {
      return { message: text, suggestion: extracted, validation }
    }
    // 校验失败：返回消息 + 无效校验结果（UI 据此不渲染 CTA）
    return { message: text, validation }
  }
  return { message: text }
}

/** 从文本中提取结构化建议对象（优先 ```json 代码块，其次行内 {...}）。 */
function extractSuggestion(text: string): CoachSuggestion | null {
  if (!text) return null
  const block = text.match(/```(?:json)?\s*([\s\S]*?)```/i)
  const candidate = block ? block[1] : extractInlineJson(text)
  if (!candidate) return null
  try {
    const parsed = JSON.parse(candidate)
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
      const obj = parsed as Record<string, unknown>
      return {
        appKey: typeof obj.appKey === 'string' ? obj.appKey : undefined,
        knowledgeId: typeof obj.knowledgeId === 'number' ? obj.knowledgeId : undefined,
        actionType: typeof obj.actionType === 'string' ? obj.actionType : undefined,
        reason: typeof obj.reason === 'string' ? obj.reason : undefined,
      }
    }
  } catch {
    return null
  }
  return null
}

/** 提取文本中第一个 {...} 片段（容错：找不到则返回 null）。 */
function extractInlineJson(text: string): string | null {
  const start = text.indexOf('{')
  const end = text.lastIndexOf('}')
  if (start === -1 || end === -1 || end <= start) return null
  return text.slice(start, end + 1)
}
