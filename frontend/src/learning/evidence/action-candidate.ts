/**
 * Architecture-4.1-§25/§26：Strategy → Action Candidate。
 *
 * §25 不重写 NextAction：本模块只产出「候选动作」，
 *   最终的优先级排序仍然交给既有 `resolveNextAction`（Review > Task > …）。
 *   候选动作以 `ctx.evidence` 形式注入：为空时既有行为**完全不变**（§36）。
 *
 * §26 Goal Context：候选动作必须尽可能绑定 goalId / learningNodeId / knowledgeId，
 *   让「JVM GC 目标 → GC 节点 → 应用能力弱 → 场景练习 → GC 场景」这条链成立，
 *   而不是随机练另一个知识点。
 *
 * 绑定口径（诚实）：knowledgeId 只在三种真实来源中出现，绝不猜测：
 *   1. Evidence 自身携带（事件 metadata 真实写入 knowledgeId）；
 *   2. 当前目标 knowledgeId（LearningGoalVO.knowledgeId，真实字段）；
 *   3. 当前学习节点 knowledgeId（LearningPathNode / Chapter 真实字段）。
 *   三者都取不到时 knowledgeId 为 null，动作仍然可用，只是不带知识点上下文。
 */
import type { AppDefinition } from '@/apps/types'
import type { KnowledgeMasteryVO, LearningGoalVO } from '@/api/types'
import type { NextLearningAction } from '../types'
import type { BehaviorDiagnosis } from './diagnosis'
import { DIMENSION_LABEL } from './diagnosis'
import type { EvidenceAggregate } from './types'
import type { EvidenceStrategyType, StrategyTarget } from './strategy'
import { STRATEGY_LABEL, firstAvailableTarget, resolveEvidenceStrategy, targetRoute } from './strategy'

/** §26 目标上下文（全部可选，取不到就不绑定，不伪造） */
export interface EvidenceGoalContext {
  /** 当前进行中的学习目标 */
  goal?: LearningGoalVO | null
  /** 目标当前所处的学习节点 ID */
  learningNodeId?: number | null
  /** 目标当前所处的学习节点标题（如「GC 算法」） */
  learningNodeTitle?: string | null
  /** 目标关联的真实知识点 ID */
  knowledgeId?: number | null
  /** 知识点名称（用于路由 query 透传 / 上下文条） */
  knowledgeName?: string | null
}

/** 一个策略候选动作（§25） */
export interface StrategyActionCandidate {
  /** 既有 NextLearningAction 结构，可直接被 resolveNextAction 返回 */
  action: NextLearningAction
  /** 触发该动作的诊断（真实 Evidence 推导） */
  diagnosis: BehaviorDiagnosis
  /** 诊断 → 策略 */
  strategy: EvidenceStrategyType
  /** 实际选中的目标（已过 App 可用性） */
  target: StrategyTarget
  /** 是否因「无可用 App」走到兜底 */
  fallback: boolean
}

/** 从知识点列表里按 id 取名称（真实数据兜底，取不到返回 null） */
function nameOf(masteries: KnowledgeMasteryVO[] | undefined, id: number | null | undefined): string | null {
  if (id == null || !masteries) return null
  return masteries.find((m) => m.knowledgeId === id)?.name ?? null
}

/**
 * §25/§26 构造候选动作。
 * @param diagnosis 由 Evidence 推导出的诊断（无诊断时不调用本函数）
 * @param installedApps 已安装且启用的 App（UserApp + Registry 白名单）
 * @param ctx 目标上下文
 * @param masteries 已加载的掌握度列表（仅用于补全知识点名称，可缺省）
 */
export function buildStrategyCandidate(
  diagnosis: BehaviorDiagnosis,
  installedApps: AppDefinition[] = [],
  ctx: EvidenceGoalContext = {},
  masteries?: KnowledgeMasteryVO[],
): StrategyActionCandidate {
  const strategy = resolveEvidenceStrategy(diagnosis)
  const target = firstAvailableTarget(strategy, installedApps)
  const fallback = target.kind === 'ROUTE' && target.path === '/learning/center'

  // §26 知识点绑定：Evidence(真实) > Goal/Node(真实) > null
  const knowledgeId = ctx.knowledgeId ?? null
  const knowledgeName =
    ctx.knowledgeName ?? nameOf(masteries, knowledgeId) ?? null

  const dimLabel = DIMENSION_LABEL[diagnosis.dimension]
  const strategyLabel = STRATEGY_LABEL[strategy]
  const scoreText = `${dimLabel} ${diagnosis.score} 分（${diagnosis.sampleCount} 条证据）`
  const goalText = ctx.goal?.title ? `围绕目标「${ctx.goal.title}」，` : ''
  const nodeText = ctx.learningNodeTitle ? `当前节点「${ctx.learningNodeTitle}」· ` : ''
  const knowledgeText = knowledgeName ? `「${knowledgeName}」` : '这个知识点'

  const action: NextLearningAction = {
    type: target.kind === 'APP' ? 'MINI_APP' : 'FLASHCARD',
    title: fallback ? `继续学习${knowledgeText}` : `用${strategyLabel}补强${knowledgeText}`,
    reason: `${nodeText}${goalText}${diagnosis.reason}（${scoreText}）。`,
    route: targetRoute(target),
    appKey: target.kind === 'APP' ? target.appKey : undefined,
    knowledgeId: knowledgeId ?? undefined,
    knowledgeName: knowledgeName ?? undefined,
  }

  return { action, diagnosis, strategy, target, fallback }
}

/**
 * §25 从「Evidence 聚合 + 诊断」直接产出主候选（取优先级最高且有可用目标的诊断）。
 * 无诊断 / 无证据 → 返回 null（调用方维持既有行为，不注入）。
 */
export function buildTopCandidate(
  agg: EvidenceAggregate,
  diagnoses: BehaviorDiagnosis[],
  installedApps: AppDefinition[] = [],
  ctx: EvidenceGoalContext = {},
  masteries?: KnowledgeMasteryVO[],
): StrategyActionCandidate | null {
  if (agg.totalEvidence === 0 || diagnoses.length === 0) return null
  const top = diagnoses[0]
  return buildStrategyCandidate(top, installedApps, ctx, masteries)
}
