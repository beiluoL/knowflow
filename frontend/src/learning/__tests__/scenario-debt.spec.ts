import { describe, expect, it } from 'vitest'
import type { LearningEventVO } from '@/api/types'
import {
  latestAttemptByScenario,
  parseScenarioAttempt,
  scenarioPracticeDebt,
  type ScenarioPracticeDebtItem,
} from '@/learning/scenario-debt'

/** 构造一条 SCENARIO_COMPLETED 事件。metadata 传对象，内部序列化为 JSON 字符串。 */
function ev(
  id: number,
  resourceId: number,
  meta: { scenarioId?: number; scenarioTitle?: string; coverage?: number; matched: number; total: number },
  createTime = '2026-09-03 10:00:00',
): LearningEventVO {
  return {
    id,
    userId: 80001,
    eventType: 'SCENARIO_COMPLETED',
    resourceType: 'SCENARIO',
    resourceId,
    metadata: JSON.stringify(meta),
    createTime,
  }
}

describe('learning/scenario-debt（4.0-5 Practice Debt 纯函数）', () => {
  it('解析合法事件：从 metadata 提取 scenarioId/title/coverage/matched/total', () => {
    const m = parseScenarioAttempt(ev(1, 9, { scenarioTitle: '线上 Full GC', coverage: 0.25, matched: 1, total: 4 }))
    expect(m).not.toBeNull()
    expect(m?.scenarioId).toBe(9)
    expect(m?.scenarioTitle).toBe('线上 Full GC')
    expect(m?.coverage).toBe(0.25)
    expect(m?.matched).toBe(1)
    expect(m?.total).toBe(4)
  })

  it('容错：非 SCENARIO 事件 / 空 metadata / 坏 JSON / total<=0 一律返回 null', () => {
    expect(parseScenarioAttempt({ ...ev(1, 9, { matched: 1, total: 4 }), eventType: 'CHECK_IN' })).toBeNull()
    expect(parseScenarioAttempt({ ...ev(1, 9, { matched: 1, total: 4 }), metadata: null })).toBeNull()
    expect(parseScenarioAttempt({ ...ev(1, 9, { matched: 1, total: 4 }), metadata: '{oops' })).toBeNull()
    expect(parseScenarioAttempt(ev(1, 9, { matched: 1, total: 0 }))).toBeNull()
    expect(parseScenarioAttempt({ ...ev(1, 9, { matched: 1, total: 4 }), resourceId: null })).toBeNull()
  })

  it('coverage 缺失时按 matched/total 兜底折算', () => {
    const m = parseScenarioAttempt(ev(1, 9, { matched: 2, total: 4 }))
    expect(m?.coverage).toBe(0.5)
  })

  it('每个场景取最近一次提交（VO.id 最大者，即便覆盖度更低也算“最新状态”）', () => {
    const rows = [
      ev(1, 9, { scenarioTitle: 'Full GC', coverage: 1.0, matched: 4, total: 4 }, '2026-09-02 09:00:00'),
      ev(2, 5, { scenarioTitle: '慢 SQL', coverage: 0.75, matched: 3, total: 4 }, '2026-09-02 10:00:00'),
      ev(3, 9, { scenarioTitle: 'Full GC', coverage: 0.25, matched: 1, total: 4 }, '2026-09-03 11:00:00'),
    ]
    const latest = latestAttemptByScenario(rows)
    expect(latest).toHaveLength(2)
    const gc = latest.find((x) => x.scenarioId === 9) as ScenarioPracticeDebtItem
    expect(gc.eventId).toBe(3)
    expect(gc.coverage).toBe(0.25)
  })

  it('待补强 = 最近一次未覆盖全部要点；已满分（matched=total）场景不进列表', () => {
    const rows = [
      ev(1, 9, { scenarioTitle: 'Full GC', matched: 5, total: 5 }, '2026-09-03 10:00:00'), // 全覆盖 → 排除
      ev(2, 5, { scenarioTitle: '慢 SQL', coverage: 0.5, matched: 2, total: 4 }, '2026-09-03 11:00:00'), // 待补强
    ]
    const debt = scenarioPracticeDebt(rows)
    expect(debt).toHaveLength(1)
    expect(debt[0].scenarioId).toBe(5)
    expect(debt[0].coverage).toBe(0.5)
  })

  it('排序：覆盖度升序，同覆盖度最近提交在前', () => {
    const rows = [
      ev(10, 1, { scenarioTitle: 'A', coverage: 1.0, matched: 3, total: 3 }, '2026-09-01 09:00:00'),
      ev(11, 2, { scenarioTitle: 'B', coverage: 0.25, matched: 1, total: 4 }, '2026-09-01 09:00:00'),
      ev(12, 3, { scenarioTitle: 'C', coverage: 0.5, matched: 2, total: 4 }, '2026-09-01 09:00:00'),
      ev(13, 4, { scenarioTitle: 'D', coverage: 0.5, matched: 2, total: 4 }, '2026-09-03 09:00:00'),
    ]
    const debt = scenarioPracticeDebt(rows)
    expect(debt.map((x) => x.scenarioId)).toEqual([2, 4, 3]) // D(近) 排在 C(远) 前；A 满分排除
  })

  it('空列表 / 全坏数据 → 空结果（页面安静隐藏，不报错）', () => {
    expect(scenarioPracticeDebt([])).toEqual([])
    expect(scenarioPracticeDebt([{ ...ev(1, 9, { matched: 1, total: 4 }), metadata: 'not-json' }])).toEqual([])
  })
})
