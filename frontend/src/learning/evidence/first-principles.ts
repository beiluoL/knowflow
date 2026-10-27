/**
 * Architecture-4.1-§14~17：First Principles Lab（第一性原理）纯逻辑层。
 *
 * ── 诚实边界（§16/§33）──
 * 这不是「语义正确性评分」。系统无法验证你写的底层机制对不对，
 * 因此第一版只做 **结构完整度**：检查 6 个环节是否都认真填写了。
 * UI 与文档中必须如实表述为「推导结构完整度」，禁止包装成「AI 已理解你的水平」。
 * 未来若接入 LLM，也只能作为评价辅助，不得直接修改 Mastery / Diagnosis（§16/§34）。
 *
 * 六个环节（§15）：
 *   1 已知事实 facts         —— 不用类比、只写可验证的事实
 *   2 假设 assumptions       —— 你默认成立但没验证过的东西
 *   3 约束 constraints       —— 物理/资源/规则上的硬边界
 *   4 底层机制 mechanism     —— 拆到不能再拆的基本原理
 *   5 因果关系 causality     —— 机制之间如何推导出结果
 *   6 重新推导 derivation    —— 从基本原理重新推出结论
 */

/** 环节 key（顺序即 UI 展示顺序，也是后端字段顺序） */
export const FIRST_PRINCIPLES_STEPS = [
  'facts',
  'assumptions',
  'constraints',
  'mechanism',
  'causality',
  'derivation',
] as const

export type FirstPrinciplesStep = (typeof FIRST_PRINCIPLES_STEPS)[number]

/** 六环节文案（UI / 后端共用语义，避免前后端口径漂移） */
export const FIRST_PRINCIPLES_STEP_META: {
  key: FirstPrinciplesStep
  index: number
  title: string
  hint: string
  placeholder: string
}[] = [
  {
    key: 'facts',
    index: 1,
    title: '已知事实',
    hint: '只写可以验证的事实，不写类比、不写结论',
    placeholder: '例：对象创建后可能被回收；堆内存大小有限',
  },
  {
    key: 'assumptions',
    index: 2,
    title: '假设',
    hint: '你默认成立、但没有亲自验证过的东西',
    placeholder: '例：大多数对象朝生夕死（需要验证，不是事实）',
  },
  {
    key: 'constraints',
    index: 3,
    title: '约束',
    hint: '绕不过去的硬边界：物理限制、资源上限、规则要求',
    placeholder: '例：回收时必须停线程或处理并发；内存不能无限增长',
  },
  {
    key: 'mechanism',
    index: 4,
    title: '底层机制',
    hint: '拆到不能再拆的基本原理，而不是「因为框架这么做的」',
    placeholder: '例：可达性分析 → 标记存活对象 → 回收不可达对象 → 整理/复制',
  },
  {
    key: 'causality',
    index: 5,
    title: '因果关系',
    hint: '机制之间如何一步步推出结果，写出因果链',
    placeholder: '例：因为对象朝生夕死 → 所以分代 → 所以年轻代用复制算法成本最低',
  },
  {
    key: 'derivation',
    index: 6,
    title: '重新推导',
    hint: '忘掉结论，只从上面 1-5 重新推出原来的结论',
    placeholder: '例：从「可达性 + 分代假设 + 停顿约束」重新推出「需要分代收集器」',
  },
]

/** 单环节最短有效字数：低于该长度视为「没认真写」，不计入完整度 */
export const MIN_STEP_LENGTH = 8
/** 主题最短长度 */
export const MIN_TOPIC_LENGTH = 2
/** 主题最长长度（与后端 MAX_TOPIC_LENGTH 对齐） */
export const MAX_TOPIC_LENGTH = 200
/** 单环节最长长度（防御性，与后端一致） */
export const MAX_STEP_LENGTH = 4000

/** 用户填写内容（六环节 + 主题） */
export type FirstPrinciplesDraft = Record<FirstPrinciplesStep, string>

/** 评估结果（确定性，可前后端各自复算） */
export interface FirstPrinciplesEvaluation {
  topic: string
  /** 结构完整度 0~100 = filled / 6 × 100（§16） */
  completeness: number
  /** 填写合格的环节数 */
  filled: number
  /** 环节总数（固定 6） */
  total: number
  /** 未填写 / 填写过短的环节 key */
  missing: FirstPrinciplesStep[]
  /** 逐环节是否合格（UI 打勾用） */
  stepFilled: Record<FirstPrinciplesStep, boolean>
}

/** 单环节是否「填写合格」：去掉空白后达到最短字数 */
export function isStepFilled(text: string | null | undefined): boolean {
  return (text ?? '').trim().replace(/\s+/g, '').length >= MIN_STEP_LENGTH
}

/**
 * §16 结构完整度评估（纯函数、确定性、无 AI）。
 * 只判断「写没写够」，完全不判断「写得对不对」。
 */
export function evaluateFirstPrinciples(
  topic: string,
  draft: Partial<FirstPrinciplesDraft>,
): FirstPrinciplesEvaluation {
  const stepFilled = {} as Record<FirstPrinciplesStep, boolean>
  const missing: FirstPrinciplesStep[] = []
  let filled = 0
  for (const step of FIRST_PRINCIPLES_STEPS) {
    const ok = isStepFilled(draft?.[step])
    stepFilled[step] = ok
    if (ok) filled++
    else missing.push(step)
  }
  const total = FIRST_PRINCIPLES_STEPS.length
  return {
    topic: (topic ?? '').trim(),
    completeness: Math.round((filled / total) * 100),
    filled,
    total,
    missing,
    stepFilled,
  }
}

/** 提交前校验：返回第一条错误信息，合法返回 null */
export function validateFirstPrinciples(topic: string, draft: Partial<FirstPrinciplesDraft>): string | null {
  const t = (topic ?? '').trim()
  if (t.length < MIN_TOPIC_LENGTH) return '请填写推导主题'
  if (t.length > MAX_TOPIC_LENGTH) return `推导主题过长（上限 ${MAX_TOPIC_LENGTH} 字符）`
  for (const step of FIRST_PRINCIPLES_STEPS) {
    const raw = draft?.[step] ?? ''
    if (raw.length > MAX_STEP_LENGTH) {
      const meta = FIRST_PRINCIPLES_STEP_META.find((m) => m.key === step)
      return `「${meta?.title ?? step}」内容过长（上限 ${MAX_STEP_LENGTH} 字符）`
    }
  }
  return null
}

/** 完整度 → 用户可读评价（如实：只谈结构，不谈对错） */
export function completenessComment(completeness: number): string {
  if (completeness >= 100) return '六个环节都写全了。下一步是对照资料检查「底层机制」和「因果链」是否站得住。'
  if (completeness >= 67) return '主体结构已成型，补全缺失环节后这套推导才算完整。'
  if (completeness >= 34) return '已经有基础框架，但关键环节还空着，推导链是断的。'
  return '推导还很不完整。第一性原理的价值在于过程，建议从「已知事实」重新拆。'
}

/**
 * 把评估结果转成可持久化的事件 metadata（与后端 FirstPrinciplesSubmitDTO 一一对应）。
 * 仅用于前端本地预演 / 单测断言；真实落库由后端重算，避免信任前端数值。
 */
export function toFirstPrinciplesMetadata(ev: FirstPrinciplesEvaluation): {
  topic: string
  completeness: number
  filled: number
  total: number
  missing: FirstPrinciplesStep[]
} {
  return {
    topic: ev.topic,
    completeness: ev.completeness,
    filled: ev.filled,
    total: ev.total,
    missing: [...ev.missing],
  }
}
