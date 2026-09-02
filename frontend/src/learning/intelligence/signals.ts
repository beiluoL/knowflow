/**
 * Architecture-6：Learning Signals（§11/§341 信号层）
 *
 * 作用：把真实上下文压缩为「信号」——廉价的派生计数 / 标记（§134-137，O(n) 纯内存计算）。
 *   LearningEvent / KnowledgeMastery / Review / Quiz / Mistake
 *     → LearningSignals（本文件）
 *     → Diagnosis（diagnosis.ts）
 *     → Strategy（strategy.ts）
 *     → NextAction（action-priority.ts）
 *
 * 不调用任何 API、不查询 DB（§218-223），所有输入来自页面已加载的真实数据。
 */
import type { LearningIntelligenceContext } from './types'

/** 由上下文推导出的原始信号（真实数据的轻量聚合） */
export interface LearningSignalsResolved {
  reviewCount: number
  weakCount: number
  pendingTaskCount: number
  pathInProgress: boolean
  pathProgress: number
  /** 距最近一次学习事件的天数；无事件数据时为 null（不推导 INACTIVE，§148） */
  daysSinceLastActivity: number | null
  /** 重复出错的 knowledgeId（仅当上下文提供真实 mapping，§67-69） */
  repeatedMistakeKnowledgeIds: number[]
  /** 正确率偏低的 knowledgeId（仅当上下文提供真实聚合，§70-72） */
  lowAccuracyKnowledgeIds: number[]
  /** 停滞路径 id（仅当上下文提供，§23） */
  stalledPathIds: number[]
}

/** 从 Intelligence 上下文派生信号（纯函数，无副作用） */
export function deriveSignals(ctx: LearningIntelligenceContext): LearningSignalsResolved {
  const reviewCount = ctx.reviewRequired?.length ?? 0
  const weakCount = ctx.weakPoints?.length ?? 0
  const pendingTaskCount = (ctx.tasks ?? []).filter((t) => t.status !== 1).length

  const path = ctx.currentPath
  const pathInProgress = !!(path && (path.progress ?? 0) > 0 && (path.progress ?? 0) < 100)
  const pathProgress = Math.min(100, Math.round(path?.progress ?? 0))

  // 最近学习事件（仅当真实提供）
  let daysSinceLastActivity: number | null = null
  const events = ctx.recentEvents
  if (events && events.length > 0) {
    const times = events
      .map((e) => (e.createTime ? new Date(e.createTime).getTime() : NaN))
      .filter((t) => !Number.isNaN(t))
    if (times.length > 0) {
      const last = Math.max(...times)
      daysSinceLastActivity = Math.floor((Date.now() - last) / 86_400_000)
    }
  }

  return {
    reviewCount,
    weakCount,
    pendingTaskCount,
    pathInProgress,
    pathProgress,
    daysSinceLastActivity,
    repeatedMistakeKnowledgeIds: ctx.repeatedMistakeKnowledgeIds ?? [],
    lowAccuracyKnowledgeIds: ctx.lowAccuracyKnowledgeIds ?? [],
    stalledPathIds: ctx.stalledPathIds ?? [],
  }
}
