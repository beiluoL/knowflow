import type { LearningEventVO } from '@/api/types'

/**
 * Architecture-4.0-5：场景练习补强推荐（Practice Debt，纯函数）。
 * <p>
 * 信号来源 = 用户真实的 SCENARIO_COMPLETED 学习事件（后端在 submit 时已把
 * coverage/score/matched/total/scenarioId/scenarioTitle 写入事件 metadata）。
 * 本模块只做「确定性筛选/排序」：每个场景取最近一次提交，若未覆盖全部要点
 * （matched < total）即视为「待补强」，按覆盖度升序推荐 —— 无 AI、无掌握度改写。
 */
export interface ScenarioAttemptMeta {
  /** 场景 ID（优先 metadata.scenarioId，缺失回退事件 resourceId） */
  scenarioId: number
  /** 场景标题 */
  scenarioTitle: string
  /** 覆盖度 0.0~1.0 */
  coverage: number
  /** 命中的要点数 */
  matched: number
  /** 要点总数 */
  total: number
}

export interface ScenarioPracticeDebtItem extends ScenarioAttemptMeta {
  /** 依据的事件 id（VO 主键单调递增，用最大值判定「最近一次」） */
  eventId: number
  /** 事件发生时间 */
  lastTime?: string | null
}

/** 解析单条 SCENARIO_COMPLETED 事件 → 覆盖元数据；不可解析/非法输入一律返回 null（容错）。 */
export function parseScenarioAttempt(ev: LearningEventVO): ScenarioAttemptMeta | null {
  if (!ev || ev.eventType !== 'SCENARIO_COMPLETED' || ev.resourceId == null || !ev.metadata) {
    return null
  }
  try {
    const raw = JSON.parse(ev.metadata) as Record<string, unknown>
    const scenarioId = Number(raw.scenarioId ?? ev.resourceId)
    const total = Number(raw.total)
    const matched = Number(raw.matched)
    if (!Number.isFinite(scenarioId) || scenarioId <= 0) return null
    if (!Number.isFinite(total) || total <= 0 || !Number.isFinite(matched) || matched < 0) {
      return null
    }
    const coverageRaw = Number(raw.coverage)
    const coverage = Number.isFinite(coverageRaw) ? coverageRaw : matched / total
    return {
      scenarioId,
      scenarioTitle: String(raw.scenarioTitle ?? `场景 #${scenarioId}`),
      coverage,
      matched,
      total,
    }
  } catch {
    return null
  }
}

/** 每个场景取「最近一次」提交（VO.id 单调递增 → 取 id 最大的事件）。 */
export function latestAttemptByScenario(events: LearningEventVO[]): ScenarioPracticeDebtItem[] {
  const latestById = new Map<number, ScenarioPracticeDebtItem>()
  for (const ev of events) {
    const meta = parseScenarioAttempt(ev)
    if (!meta) continue
    const prev = latestById.get(meta.scenarioId)
    if (!prev || ev.id > prev.eventId) {
      latestById.set(meta.scenarioId, {
        ...meta,
        eventId: ev.id,
        lastTime: ev.createTime,
      })
    }
  }
  return [...latestById.values()]
}

/**
 * 待补强场景：最近一次提交未覆盖全部要点的场景。
 * 排序：覆盖度升序（越薄弱越靠前）→ 同覆盖度按最近提交时间倒序。
 */
export function scenarioPracticeDebt(events: LearningEventVO[]): ScenarioPracticeDebtItem[] {
  return latestAttemptByScenario(events)
    .filter((a) => a.matched < a.total)
    .sort((a, b) => a.coverage - b.coverage || (b.lastTime ?? '').localeCompare(a.lastTime ?? ''))
}
