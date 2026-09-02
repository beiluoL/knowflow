/**
 * Architecture-6：Next Action 集成（§6.4/§98-106/§242-247）
 *
 * 原则：
 *  - 不重写 resolveNextAction（§6.4/§311），原优先级 REVIEW>TASK>WEAK>PATH>EXPLORE 必须保持不变（§243）；
 *  - Intelligence 只给 NextAction 追加 diagnosisType / reason / evidence 说明（§244），绝不偷偷改 priority；
 *  - 纯函数，无副作用（§32-35）。
 *
 * 调用链（§121）：Diagnosis → Strategy → NextAction → ActionState → CTA。
 */
import { resolveNextAction } from '../action-priority'
import type { LearningActionContext, NextLearningAction } from '../types'
import { diagnoseLearningState } from './diagnosis'
import type { DiagnosisType, LearningDiagnosis, LearningIntelligenceContext } from './types'

/** 把 Resolver 上下文映射为 Intelligence 上下文（§131-132：复用已加载数据，不重新拉取） */
export function toIntelligenceContext(ctx: LearningActionContext): LearningIntelligenceContext {
  return {
    reviewRequired: ctx.reviewRequired,
    weakPoints: ctx.weakPoints,
    tasks: ctx.tasks,
    currentPath: ctx.currentPath ?? null,
  }
}

/** 根据动作匹配最相关的诊断类型（不覆盖原优先级，只补充原因标签） */
function matchDiagnosisType(action: NextLearningAction, diagnoses: LearningDiagnosis[]): DiagnosisType | undefined {
  switch (action.type) {
    case 'REVIEW':
      return diagnoses.find((d) => d.type === 'REVIEW_OVERDUE')?.type
    case 'LEARNING_PATH':
      return diagnoses.find((d) => d.type === 'PATH_STALLED')?.type
    case 'KNOWLEDGE':
    case 'MINI_APP':
    case 'FLASHCARD': {
      if (action.knowledgeId == null) return undefined
      const related = diagnoses
        .filter(
          (d) =>
            (d.type === 'WEAK_KNOWLEDGE' || d.type === 'REPEATED_MISTAKE' || d.type === 'LOW_ACCURACY') &&
            d.knowledgeId === action.knowledgeId,
        )
        .sort((a, b) => b.priority - a.priority)
      return related[0]?.type
    }
    default:
      // TASK / QUIZ / EXPLORE 不挂诊断（§101-103）
      return undefined
  }
}

/**
 * 给已解析的 NextAction 追加 diagnosisType（不改动 type/route/title/reason/priority）。
 * 返回新的对象（不可变，便于测试与组合）。
 */
export function attachDiagnosis(
  action: NextLearningAction,
  diagnoses: LearningDiagnosis[],
): NextLearningAction {
  const diagnosisType = matchDiagnosisType(action, diagnoses)
  if (diagnosisType == null) return action
  return { ...action, diagnosisType }
}

/**
 * 带智能诊断的 Next Action 解析（§311）。
 * 行为等价于 resolveNextAction，仅额外在结果上挂 diagnosisType。
 */
export function resolveNextActionWithIntelligence(
  ctx: LearningActionContext,
  skip: Parameters<typeof resolveNextAction>[1] = [],
): NextLearningAction {
  const action = resolveNextAction(ctx, skip)
  const diagnoses = diagnoseLearningState(toIntelligenceContext(ctx))
  return attachDiagnosis(action, diagnoses)
}
