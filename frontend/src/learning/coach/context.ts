/**
 * Architecture-7：Coach Context 构建（纯函数，不调用任何 API，§49-54/§473）。
 *
 * 从页面已加载的真实数据组装最小化、隐私过滤的 LearningCoachContext：
 *  - 复用 Architecture-6 的 NextAction / Diagnosis（§46-48）；
 *  - 只保留与用户问题相关的数据，Top N 截断（§478）；
 *  - 不传经济数据（Coin/钱包/订单）、不传 PII（§19-23）。
 */
import type { CoachContextInput, LearningCoachContext } from './types'
import type { DiagnosisType, Severity } from '../intelligence/types'
import type { KnowledgeMasteryVO } from '@/api/types'

/** 单次上下文最多携带的知识点数量，控制 token 预算（§478/§492）。 */
const TOP_N = 5

function safeName(v: KnowledgeMasteryVO | null | undefined): string {
  return v?.name ?? '未命名知识点'
}

/**
 * 构建学习教练上下文。
 * @param input 页面已加载的真实学习数据（reviewRequired / weakPoints / tasks / currentPath / nextAction / diagnoses）
 */
export function buildLearningCoachContext(input: CoachContextInput): LearningCoachContext {
  const review = (input.reviewRequired ?? []).slice(0, TOP_N).map((r) => ({
    knowledgeName: safeName(r),
    masteryScore: r.masteryScore,
    nextReviewAt: r.nextReviewAt ?? null,
  }))

  const weak = (input.weakPoints ?? []).slice(0, TOP_N).map((w) => ({
    knowledgeName: safeName(w),
    masteryScore: w.masteryScore,
    weaknessTypes: Array.isArray(w.weaknessTypes) ? w.weaknessTypes : [],
  }))

  const tasks = (input.tasks ?? []).slice(0, TOP_N).map((t) => ({
    title: t.title,
    status: t.status,
    deadline: t.deadline ?? null,
  }))

  const diagnoses = (input.diagnoses ?? []).map((d) => ({
    type: d.type as DiagnosisType,
    title: d.title,
    reason: d.reason,
    severity: d.severity as Severity,
    knowledgeName: d.knowledgeName,
  }))

  const nextAction = input.nextAction
    ? {
        type: input.nextAction.type,
        title: input.nextAction.title,
        reason: input.nextAction.reason,
        knowledgeName: input.nextAction.knowledgeName,
      }
    : undefined

  const currentPath = input.currentPath
    ? { title: input.currentPath.title, progress: input.currentPath.progress }
    : null

  const reviewCount = (input.reviewRequired ?? []).length
  const weakCount = (input.weakPoints ?? []).length
  const taskCount = (input.tasks ?? []).length

  const snapshot = input.learningState
  const totalEvidence = snapshot?.evidenceAggregate?.totalEvidence ?? 0
  const learningState = snapshot ? {
    goalTitle: snapshot.activeGoal?.title ?? null,
    knowledgeTitle: snapshot.context?.knowledgeTitle ?? null,
    totalEvidence,
    activeDays: snapshot.evidenceAggregate?.activeDays ?? 0,
    diagnoses: totalEvidence > 0 ? (snapshot.behaviorDiagnoses ?? []).slice(0, TOP_N).map(({title,reason,score,sampleCount,confidence}) => ({title,reason,score,sampleCount,confidence})) : [],
  } : undefined

  const recentActivity = input.recentActivity
  const hasData =
    !!learningState?.goalTitle ||
    !!learningState?.knowledgeTitle ||
    totalEvidence > 0 ||
    reviewCount > 0 ||
    weakCount > 0 ||
    taskCount > 0 ||
    !!currentPath ||
    diagnoses.length > 0 ||
    (recentActivity ?? []).length > 0

  return {
    hasData,
    learningState,
    nextAction,
    diagnoses,
    reviewRequired: review,
    weakPoints: weak,
    tasks,
    currentPath,
    recentActivity,
    summary: { reviewCount, weakCount, taskCount, hasPath: !!currentPath },
  }
}
