/**
 * Architecture-4.1：Evidence Resolver 测试（§32）
 *
 * 重点验证：
 *  - Event vs Evidence 的区分：每种真实事件 → 对应维度与真实换算分；
 *  - 不可解析 / 脏数据 / 不支持的事件一律 null（不伪造，§33）；
 *  - 置信度口径与维度归属固定，供后续聚合与诊断稳定依赖。
 */
import { describe, it, expect } from 'vitest'
import type { LearningEventVO } from '@/api/types'
import {
  resolveEvidence,
  resolveEvidenceBatch,
  resolveFeynmanEvidence,
  resolveScenarioEvidence,
  resolveCodingEvidence,
  resolveQuizEvidence,
  resolveFlashcardEvidence,
  resolveFirstPrinciplesEvidence,
} from '../resolver'

const NOW = '2026-09-06T10:00:00.000Z'

function ev(over: Partial<LearningEventVO> & { id: number; eventType: string }): LearningEventVO {
  return {
    userId: 1,
    resourceType: null,
    resourceId: null,
    metadata: null,
    createTime: NOW,
    ...over,
  } as LearningEventVO
}

const json = (o: Record<string, unknown>) => JSON.stringify(o)

describe('Evidence Resolver · 费曼（§8 不重新评分）', () => {
  it('FEYNMAN_EXPRESSED → EXPLANATION，复用 metadata.overall', () => {
    const e = resolveFeynmanEvidence(
      ev({ id: 1, eventType: 'FEYNMAN_EXPRESSED', resourceId: 0, metadata: json({ topic: 'JVM GC', overall: 56 }) }),
    )
    expect(e).not.toBeNull()
    expect(e!.dimension).toBe('EXPLANATION')
    expect(e!.score).toBe(56)
    expect(e!.confidence).toBeLessThan(1) // 作者自检类，置信度必须低于机器可验证类
    expect(e!.label).toContain('JVM GC')
  })

  it('overall 缺失时用四维均值兜底，四维也缺失则不产出（不猜）', () => {
    const e = resolveFeynmanEvidence(
      ev({
        id: 2,
        eventType: 'FEYNMAN_EXPRESSED',
        metadata: json({ accuracy: 75, clarity: 25, completeness: 75, structure: 50 }),
      }),
    )
    expect(e!.score).toBe(56) // (75+25+75+50)/4

    expect(resolveFeynmanEvidence(ev({ id: 3, eventType: 'FEYNMAN_EXPRESSED', metadata: json({}) }))).toBeNull()
  })

  it('evidenceId 由事件 id + 维度复合而成（可重算、可去重）', () => {
    const e = resolveFeynmanEvidence(
      ev({ id: 42, eventType: 'FEYNMAN_EXPRESSED', metadata: json({ overall: 80 }) }),
    )
    expect(e!.evidenceId).toBe('evt:42:EXPLANATION')
  })
})

describe('Evidence Resolver · 场景 / 编程（§9/§12）', () => {
  it('SCENARIO_COMPLETED → APPLICATION，复用 score', () => {
    const e = resolveScenarioEvidence(
      ev({
        id: 4,
        eventType: 'SCENARIO_COMPLETED',
        resourceId: 9,
        metadata: json({ scenarioId: 9, scenarioTitle: 'GC 调优', score: 75, matched: 3, total: 4 }),
      }),
    )
    expect(e!.dimension).toBe('APPLICATION')
    expect(e!.score).toBe(75)
    expect(e!.sourceId).toBe(9)
  })

  it('score 缺失时用 matched/total 现算；total<=0 视为脏数据不产出', () => {
    const e = resolveScenarioEvidence(
      ev({ id: 5, eventType: 'SCENARIO_COMPLETED', resourceId: 1, metadata: json({ matched: 1, total: 4 }) }),
    )
    expect(e!.score).toBe(25)

    expect(
      resolveScenarioEvidence(
        ev({ id: 6, eventType: 'SCENARIO_COMPLETED', resourceId: 1, metadata: json({ matched: 1, total: 0 }) }),
      ),
    ).toBeNull()
  })

  it('CODE_SUBMITTED → APPLICATION（真实用例通过率，机器可验证）', () => {
    const e = resolveCodingEvidence(
      ev({
        id: 7,
        eventType: 'CODE_SUBMITTED',
        resourceId: 3,
        metadata: json({ language: 'java', total: 4, passCount: 3, passed: false }),
      }),
    )
    expect(e!.dimension).toBe('APPLICATION')
    expect(e!.score).toBe(75)
    expect(e!.confidence).toBeGreaterThan(0.8)
  })

  it('无测试用例的提交不产出 Evidence（没有可靠评分就不伪造，§12）', () => {
    expect(
      resolveCodingEvidence(
        ev({ id: 8, eventType: 'CODE_SUBMITTED', resourceId: 3, metadata: json({ total: 0, passCount: 0 }) }),
      ),
    ).toBeNull()
  })
})

describe('Evidence Resolver · 记忆类（§10/§11）', () => {
  it('QUESTION_ANSWERED → RECALL（correct=true → 100）', () => {
    const e = resolveQuizEvidence(
      ev({ id: 9, eventType: 'QUESTION_ANSWERED', resourceId: 11, metadata: json({ correct: true, score: 100 }) }),
    )
    expect(e!.dimension).toBe('RECALL')
    expect(e!.score).toBe(100)
  })

  it('答错 → 0 分；字符串 "false" 同样识别（后端 JSON 序列化容错）', () => {
    expect(
      resolveQuizEvidence(ev({ id: 10, eventType: 'QUESTION_ANSWERED', metadata: json({ correct: false }) }))!.score,
    ).toBe(0)
    expect(
      resolveQuizEvidence(ev({ id: 11, eventType: 'QUESTION_ANSWERED', metadata: json({ correct: 'false' }) }))!.score,
    ).toBe(0)
  })

  it('FLASHCARD_REVIEWED → RECALL（复用 SM-2 quality，不重新实现 SM-2）', () => {
    const e = resolveFlashcardEvidence(
      ev({
        id: 12,
        eventType: 'FLASHCARD_REVIEWED',
        resourceId: 5,
        metadata: json({ quality: 4, lapsed: false, intervalDay: 6 }),
      }),
    )
    expect(e!.dimension).toBe('RECALL')
    expect(e!.score).toBe(80) // 4/5
  })

  it('lapsed=true（本次遗忘）时分数封顶 40', () => {
    const e = resolveFlashcardEvidence(
      ev({ id: 13, eventType: 'FLASHCARD_REVIEWED', resourceId: 5, metadata: json({ quality: 5, lapsed: true }) }),
    )
    expect(e!.score).toBe(40)
  })
})

describe('Evidence Resolver · 第一性原理（§16/§17）', () => {
  it('FIRST_PRINCIPLES_DERIVED → UNDERSTANDING（结构完整度）', () => {
    const e = resolveFirstPrinciplesEvidence(
      ev({
        id: 14,
        eventType: 'FIRST_PRINCIPLES_DERIVED',
        resourceId: 0,
        metadata: json({ topic: 'JVM 分代', completeness: 67, filled: 4, total: 6, missing: ['facts', 'causality'] }),
      }),
    )
    expect(e!.dimension).toBe('UNDERSTANDING')
    expect(e!.score).toBe(67)
    expect(e!.confidence).toBeLessThanOrEqual(0.5) // 结构完整度类，置信度必须保守
  })

  it('completeness 缺失时用 filled/total 现算', () => {
    const e = resolveFirstPrinciplesEvidence(
      ev({ id: 15, eventType: 'FIRST_PRINCIPLES_DERIVED', metadata: json({ filled: 6, total: 6 }) }),
    )
    expect(e!.score).toBe(100)
  })
})

describe('Evidence Resolver · 统一入口与容错（§7/§33）', () => {
  it('不支持的事件类型不产出 Evidence', () => {
    expect(resolveEvidence(ev({ id: 16, eventType: 'CHECK_IN' }))).toBeNull()
    expect(resolveEvidence(ev({ id: 17, eventType: 'DOCUMENT_READ' }))).toBeNull()
    expect(resolveEvidence(ev({ id: 18, eventType: 'AI_CHAT' }))).toBeNull()
  })

  it('metadata 非法 JSON / 缺失 → null，不抛错、不造数', () => {
    expect(resolveEvidence(ev({ id: 19, eventType: 'FEYNMAN_EXPRESSED', metadata: '{not-json' }))).toBeNull()
    expect(resolveEvidence(ev({ id: 20, eventType: 'SCENARIO_COMPLETED', metadata: null }))).toBeNull()
  })

  it('批量解析：丢弃不可解析项并按时间升序（聚合稳定可复现）', () => {
    const list = resolveEvidenceBatch([
      ev({ id: 3, eventType: 'FEYNMAN_EXPRESSED', createTime: '2026-09-06T10:00:00.000Z', metadata: json({ overall: 50 }) }),
      ev({ id: 1, eventType: 'CHECK_IN', createTime: '2026-09-01T10:00:00.000Z' }),
      ev({ id: 2, eventType: 'SCENARIO_COMPLETED', createTime: '2026-09-02T10:00:00.000Z', resourceId: 1, metadata: json({ score: 40 }) }),
    ])
    expect(list.map((e) => e.eventId)).toEqual([2, 3])
    expect(list[0].dimension).toBe('APPLICATION')
    expect(list[1].dimension).toBe('EXPLANATION')
  })

  it('knowledgeId 只在 metadata 真实携带时才有值（不猜测资源→知识点映射）', () => {
    const withK = resolveEvidence(
      ev({ id: 21, eventType: 'SCENARIO_COMPLETED', resourceId: 7, metadata: json({ score: 60, knowledgeId: 99 }) }),
    )
    expect(withK!.knowledgeId).toBe(99)

    const withoutK = resolveEvidence(
      ev({ id: 22, eventType: 'SCENARIO_COMPLETED', resourceId: 7, metadata: json({ score: 60 }) }),
    )
    expect(withoutK!.knowledgeId).toBeNull()
  })
})
