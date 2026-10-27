/**
 * Architecture-4.2 §27/§28：Diagnosis 绑定知识点上下文测试（contextualizeDiagnosis）。
 *
 * 重点验证：
 *  - 有知识点名称时，「应用能力偏弱」→「在「GC」上应用能力偏弱」，原因加「GC：」前缀；
 *  - 无名称 / 空名称 → 原样返回（不伪造上下文，§27）。
 */
import { describe, it, expect } from 'vitest'
import type { BehaviorDiagnosis } from '../diagnosis'
import { contextualizeDiagnosis } from '../diagnosis'

const base: BehaviorDiagnosis = {
  type: 'LOW_APPLICATION',
  title: '应用能力偏弱',
  reason: '理论表现稳定，但场景练习不足',
  dimension: 'APPLICATION',
  score: 30,
  sampleCount: 4,
  confidence: 0.9,
  severity: 'HIGH',
  priority: 85,
}

describe('§27/§28 contextualizeDiagnosis', () => {
  it('有知识点名称 → 标题与原因都绑定到具体知识点', () => {
    const d = contextualizeDiagnosis(base, 'GC')
    expect(d.title).toBe('在「GC」上应用能力偏弱')
    expect(d.reason).toBe('GC：理论表现稳定，但场景练习不足')
  })

  it('空字符串名称 → 原样返回（不伪造）', () => {
    const d = contextualizeDiagnosis(base, '')
    expect(d.title).toBe(base.title)
    expect(d.reason).toBe(base.reason)
  })

  it('null 名称 → 原样返回', () => {
    const d = contextualizeDiagnosis(base, null)
    expect(d.title).toBe(base.title)
    expect(d.reason).toBe(base.reason)
  })

  it('纯空白名称 → 原样返回', () => {
    const d = contextualizeDiagnosis(base, '   ')
    expect(d.title).toBe(base.title)
  })
})
