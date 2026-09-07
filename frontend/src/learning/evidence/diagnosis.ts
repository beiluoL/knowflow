/**
 * Architecture-4.1-§20/§21：由真实 Evidence 推导的能力诊断 + 确定性根因。
 *
 * 与既有 `intelligence/diagnosis.ts` 的分工：
 *   既有 Diagnosis（REVIEW_OVERDUE / WEAK_KNOWLEDGE / LOW_ACCURACY …）回答「**学习状态**哪里不对」
 *     —— 输入是复习队列、薄弱知识点、任务、路径进度。
 *   本文件 BehaviorDiagnosis（LOW_RECALL / LOW_EXPLANATION / LOW_APPLICATION …）回答
 *     「**哪项能力**弱」—— 输入只有 Evidence 聚合结果。
 *   因此这是新枚举，不是既有 DiagnosisType 的重复（既有那个 switch 是穷举的，
 *   往里塞能力维度会破坏 LearningStrategy 映射，§36 禁止）。
 *
 * 铁律（§20）：**没有 Evidence 就没有诊断**。
 *   某维度 score 为 null（无样本）→ 绝不输出该维度诊断，不用 0 分假装「很差」。
 *
 * 根因（§21）：全部为确定性规则（基于真实分数的比较），不涉及任何 AI 判断、不写死「AI 认为…」。
 */
import type { EvidenceAggregate, EvidenceDimension } from './types'
import { dimensionOf } from './aggregate'

/** 能力诊断类型（§20） */
export type BehaviorDiagnosisType =
  | 'LOW_RECALL'
  | 'LOW_UNDERSTANDING'
  | 'LOW_EXPLANATION'
  | 'LOW_APPLICATION'
  | 'LOW_EXPRESSION'
  | 'LOW_CONSISTENCY'

/** 低于该分视为「弱」（0~100 口径） */
export const LOW_SCORE_THRESHOLD = 60
/** 低于该分视为「严重弱」 */
export const CRITICAL_SCORE_THRESHOLD = 40

/** 诊断排序权重（越大越优先，§20：越影响学习闭环的能力越靠前） */
const PRIORITY: Record<BehaviorDiagnosisType, number> = {
  LOW_RECALL: 90,
  LOW_APPLICATION: 85,
  LOW_UNDERSTANDING: 80,
  LOW_EXPLANATION: 75,
  LOW_EXPRESSION: 60,
  LOW_CONSISTENCY: 40,
}

/** 诊断类型 → 对应能力维度 */
export const DIAGNOSIS_DIMENSION: Record<BehaviorDiagnosisType, EvidenceDimension> = {
  LOW_RECALL: 'RECALL',
  LOW_UNDERSTANDING: 'UNDERSTANDING',
  LOW_EXPLANATION: 'EXPLANATION',
  LOW_APPLICATION: 'APPLICATION',
  LOW_EXPRESSION: 'EXPRESSION',
  LOW_CONSISTENCY: 'CONSISTENCY',
}

/** 维度中文名（用户可见文案用） */
export const DIMENSION_LABEL: Record<EvidenceDimension, string> = {
  RECALL: '记忆',
  UNDERSTANDING: '理解',
  EXPLANATION: '讲解',
  APPLICATION: '应用',
  EXPRESSION: '表达',
  CONSISTENCY: '一致性',
}

export interface BehaviorDiagnosis {
  type: BehaviorDiagnosisType
  /** 用户可见标题，如「应用能力偏弱」 */
  title: string
  /** 用户可见根因（确定性规则产出，真实分数驱动） */
  reason: string
  dimension: EvidenceDimension
  /** 该维度聚合分（真实，0~100） */
  score: number
  /** 该维度证据条数 */
  sampleCount: number
  /** 聚合置信度 0~1 */
  confidence: number
  severity: 'HIGH' | 'MEDIUM' | 'LOW'
  /** 排序权重 */
  priority: number
}

/**
 * §21 根因：确定性规则，只用真实分数做比较。
 * 每条文案都能追溯到「哪个分数低 + 另一个分数如何」，不含模糊断言。
 */
export function resolveRootCause(type: BehaviorDiagnosisType, agg: EvidenceAggregate): string {
  const map = {
    RECALL: dimensionOf(agg, 'RECALL')?.score ?? null,
    UNDERSTANDING: dimensionOf(agg, 'UNDERSTANDING')?.score ?? null,
    EXPLANATION: dimensionOf(agg, 'EXPLANATION')?.score ?? null,
    APPLICATION: dimensionOf(agg, 'APPLICATION')?.score ?? null,
    EXPRESSION: dimensionOf(agg, 'EXPRESSION')?.score ?? null,
    CONSISTENCY: dimensionOf(agg, 'CONSISTENCY')?.score ?? null,
  }
  const good = (v: number | null) => v != null && v >= LOW_SCORE_THRESHOLD

  switch (type) {
    case 'LOW_RECALL':
      if (map.CONSISTENCY != null && map.CONSISTENCY < LOW_SCORE_THRESHOLD) {
        return '练习间隔过长、证据太零散，记忆还没形成稳定复现'
      }
      if (good(map.UNDERSTANDING) || good(map.EXPLANATION)) {
        return '理解和讲解都还行，但缺少主动回忆练习，记住的东西容易忘'
      }
      return '最近缺乏主动回忆练习，记忆缺少复现机会'
    case 'LOW_UNDERSTANDING':
      if (good(map.RECALL)) {
        return '记得住结论，但缺少对底层机制的拆解练习'
      }
      return '对底层机制的拆解不足，理解停留在结论层面'
    case 'LOW_EXPLANATION':
      if (good(map.UNDERSTANDING)) {
        return '表达结构与核心知识之间存在断层：想得清楚，但讲不清楚'
      }
      return '理解还不够扎实，讲不出来往往是因为还没真正吃透'
    case 'LOW_APPLICATION':
      if (good(map.UNDERSTANDING) || good(map.EXPLANATION)) {
        return '理论表现稳定，但场景练习不足，知识还没转化成能用的东西'
      }
      return '基础理解还不牢，先补齐理解，再谈应用'
    case 'LOW_EXPRESSION':
      return '书面表达缺少训练，想法落到文字上时结构容易散'
    case 'LOW_CONSISTENCY':
      return '练习节奏不稳定，断断续续会让前面的证据很快失效'
  }
}

/** 严重度：分数越低越严重；CONSISTENCY 只作温和提示（LOW） */
function severityOf(type: BehaviorDiagnosisType, score: number): 'HIGH' | 'MEDIUM' | 'LOW' {
  if (type === 'LOW_CONSISTENCY') return 'LOW'
  if (score < CRITICAL_SCORE_THRESHOLD) return 'HIGH'
  return 'MEDIUM'
}

/**
 * §20 由 Evidence 聚合结果推导诊断。
 * @returns 按 priority 降序的诊断列表；**完全无 Evidence 时返回空数组**（不编造、不返回「状态良好」）。
 */
export function diagnoseFromEvidence(agg: EvidenceAggregate): BehaviorDiagnosis[] {
  if (!agg || agg.totalEvidence === 0) return []
  const out: BehaviorDiagnosis[] = []
  for (const type of Object.keys(PRIORITY) as BehaviorDiagnosisType[]) {
    const dimension = DIAGNOSIS_DIMENSION[type]
    const dim = dimensionOf(agg, dimension)
    if (!dim || dim.score == null) continue // 无样本 → 不诊断（§20）
    if (dim.score >= LOW_SCORE_THRESHOLD) continue
    out.push({
      type,
      title: `${DIMENSION_LABEL[dimension]}能力偏弱`,
      reason: resolveRootCause(type, agg),
      dimension,
      score: dim.score,
      sampleCount: dim.sampleCount,
      confidence: dim.confidence,
      severity: severityOf(type, dim.score),
      priority: PRIORITY[type],
    })
  }
  return out.sort((a, b) => b.priority - a.priority || a.score - b.score)
}

/**
 * 轻量状态卡用：最强的维度（优势）与最弱的维度。
 * 只在「有真实 Evidence」的维度里比较，无 Evidence 一律 null（§33 不伪造优势）。
 */
export function strongestAndWeakest(
  agg: EvidenceAggregate,
): { strongest: { dimension: EvidenceDimension; score: number } | null; weakest: { dimension: EvidenceDimension; score: number } | null } {
  const scored = agg.dimensions
    .filter((d): d is typeof d & { score: number } => d.score != null && d.dimension !== 'CONSISTENCY')
  if (scored.length === 0) return { strongest: null, weakest: null }
  const sorted = [...scored].sort((a, b) => b.score - a.score)
  const hi = sorted[0]
  const lo = sorted[sorted.length - 1]
  return {
    strongest: { dimension: hi.dimension, score: hi.score },
    weakest: hi.dimension === lo.dimension ? null : { dimension: lo.dimension, score: lo.score },
  }
}
