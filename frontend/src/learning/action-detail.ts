/**
 * UI/UX 5.0 §七：NextAction 的「学习方式 / 预计时间」推导（纯函数）。
 *
 * 为什么单独一个文件：
 *   NextActionCard 需要回答「我现在最应该做什么 / 点击后会发生什么」（§十八），
 *   除了标题与原因，还必须给出**学习方式**和**预计时间**——否则用户看到「复习 G1 GC」
 *   却不知道要点进去做什么、要花多久。
 *
 * 铁律（与全项目一致）：
 *   - 全部确定性推导，不调 AI、不随机、不猜测；
 *   - 取不到就回落到通用值，绝不伪造「AI 推荐」字样；
 *   - 纯函数、可单测。
 */
import type { NextLearningAction } from './types'
import type { EvidenceStrategyType } from './evidence/strategy'
import { STRATEGY_LABEL, resolveEvidenceStrategy } from './evidence/strategy'

/** 动作展示元信息（供 NextActionCard / AI Coach / 学习中心复用，保证全站文案一致） */
export interface ActionDetail {
  /** 学习方式（用户可见，如「主动回忆」「间隔复习」） */
  method: string
  /** 预计耗时（分钟，整数） */
  estimatedMinutes: number
}

/** 各动作类型的默认学习方式（§七：方式要具体，不能笼统写「学习」） */
const METHOD_BY_TYPE: Record<Exclude<NextLearningAction['type'], 'EVIDENCE_PRACTICE'>, string> = {
  REVIEW: '间隔复习',
  QUIZ: '主动回忆',
  FLASHCARD: '主动回忆',
  KNOWLEDGE: '重新学习',
  LEARNING_PATH: '系统学习',
  TASK: '任务推进',
  MINI_APP: '专项练习',
  EXPLORE: '自由探索',
}

/** 各动作类型的默认预计耗时（分钟）—— 一个专注单元的诚实估计，不夸大 */
const MINUTES_BY_TYPE: Record<Exclude<NextLearningAction['type'], 'EVIDENCE_PRACTICE'>, number> = {
  REVIEW: 8,
  QUIZ: 10,
  FLASHCARD: 8,
  KNOWLEDGE: 15,
  LEARNING_PATH: 20,
  TASK: 15,
  MINI_APP: 10,
  EXPLORE: 5,
}

/**
 * 策略 → 预计耗时（分钟）。
 * 与 STRATEGY_LABEL 一一对应；新增策略类型时必须同步补这里（Record 会在编译期报错提醒）。
 */
const MINUTES_BY_STRATEGY: Record<EvidenceStrategyType, number> = {
  ACTIVE_RECALL: 8,
  FIRST_PRINCIPLES: 15,
  FEYNMAN: 12,
  APPLICATION: 15,
  WRITING_REVIEW: 20,
  SPACED_REVIEW: 8,
  CONTINUE: 15,
}

/** 策略 → 学习方式：Evidence 动作显示真实策略名，而不是笼统的「练习」 */
const METHOD_BY_STRATEGY: Record<EvidenceStrategyType, string> = {
  ACTIVE_RECALL: '主动回忆',
  FIRST_PRINCIPLES: '第一性原理',
  FEYNMAN: '费曼表达',
  APPLICATION: '场景应用',
  WRITING_REVIEW: '写作复盘',
  SPACED_REVIEW: '间隔复习',
  CONTINUE: '继续学习',
}

/**
 * 推导动作的展示元信息。
 * @param action 由 resolveNextAction 产出的唯一 NextAction
 */
export function describeAction(action: NextLearningAction): ActionDetail {
  // Mini App / Evidence 动作：方式优先取真实策略名（费曼/场景等 App 本质是某种学习法）
  const strategy =
    action.behaviorDiagnosis && (action.type === 'EVIDENCE_PRACTICE' || action.type === 'MINI_APP')
      ? resolveEvidenceStrategy(action.behaviorDiagnosis)
      : null

  if (strategy) {
    return {
      method: METHOD_BY_STRATEGY[strategy] ?? STRATEGY_LABEL[strategy],
      estimatedMinutes: MINUTES_BY_STRATEGY[strategy] ?? 10,
    }
  }

  if (action.type === 'EVIDENCE_PRACTICE') {
    return { method: '专项练习', estimatedMinutes: 10 }
  }

  return {
    method: METHOD_BY_TYPE[action.type],
    estimatedMinutes: MINUTES_BY_TYPE[action.type],
  }
}

/** 预计时间展示文案（如「8 分钟」） */
export function formatEstimatedMinutes(minutes: number): string {
  return `${minutes} 分钟`
}
