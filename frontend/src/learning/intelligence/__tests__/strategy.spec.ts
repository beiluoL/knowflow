/**
 * Architecture-6：Strategy 单元测试（§227）
 *
 * 覆盖：
 *  - Diagnosis → Strategy 映射（§49）
 *  - Strategy → 用户可读建议（§261-263：具体、可理解，禁止「AI 判定你…」）
 */
import { describe, it, expect } from 'vitest'
import { resolveLearningStrategy, strategyAdvice } from '../strategy'
import type { DiagnosisType, LearningDiagnosis } from '../types'

function diag(type: DiagnosisType): LearningDiagnosis {
  return {
    type,
    title: type,
    reason: '',
    evidence: [],
    severity: 'MEDIUM',
    priority: 0,
  }
}

describe('resolveLearningStrategy 映射（§49）', () => {
  it('REVIEW_OVERDUE → REVIEW', () => {
    expect(resolveLearningStrategy(diag('REVIEW_OVERDUE'))).toBe('REVIEW')
  })
  it('REPEATED_MISTAKE → MISTAKE', () => {
    expect(resolveLearningStrategy(diag('REPEATED_MISTAKE'))).toBe('MISTAKE')
  })
  it('WEAK_KNOWLEDGE → RELEARN', () => {
    expect(resolveLearningStrategy(diag('WEAK_KNOWLEDGE'))).toBe('RELEARN')
  })
  it('PATH_STALLED → PATH', () => {
    expect(resolveLearningStrategy(diag('PATH_STALLED'))).toBe('PATH')
  })
  it('LOW_ACCURACY → FLASHCARD', () => {
    expect(resolveLearningStrategy(diag('LOW_ACCURACY'))).toBe('FLASHCARD')
  })
  it('INACTIVE_LEARNING → RESTART', () => {
    expect(resolveLearningStrategy(diag('INACTIVE_LEARNING'))).toBe('RESTART')
  })
  it('HEALTHY_PROGRESS → CONTINUE', () => {
    expect(resolveLearningStrategy(diag('HEALTHY_PROGRESS'))).toBe('CONTINUE')
  })
})

describe('strategyAdvice 用户可读（§261-263）', () => {
  it('每个诊断都返回非空建议', () => {
    const types: DiagnosisType[] = [
      'REVIEW_OVERDUE',
      'REPEATED_MISTAKE',
      'WEAK_KNOWLEDGE',
      'PATH_STALLED',
      'LOW_ACCURACY',
      'INACTIVE_LEARNING',
      'HEALTHY_PROGRESS',
    ]
    for (const t of types) {
      const advice = strategyAdvice(diag(t))
      expect(advice.length).toBeGreaterThan(0)
    }
  })

  it('建议不出现内部枚举或「AI 判定」措辞（§267-269）', () => {
    for (const t of ['WEAK_KNOWLEDGE', 'REVIEW_OVERDUE', 'REPEATED_MISTAKE'] as DiagnosisType[]) {
      const advice = strategyAdvice(diag(t))
      expect(advice).not.toMatch(/AI|智能算法|模型判断|WEAK_KNOWLEDGE|REVIEW_OVERDUE/)
    }
  })
})
