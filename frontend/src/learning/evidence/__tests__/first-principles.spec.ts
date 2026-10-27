/**
 * Architecture-4.1：First Principles 测试（§14~§17/§33）
 *
 * 重点验证：
 *  - 六环节结构完整度是确定性的（同输入同输出，前后端口径一致）；
 *  - 只判「写没写够」，**不判对错**（因此语义不同但结构相同的输入得分相同）；
 *  - 短内容 / 空白 / 纯空格一律不算填写合格；
 *  - 缺失环节如实列出，供 UI 提示「还差什么」。
 */
import { describe, it, expect } from 'vitest'
import {
  FIRST_PRINCIPLES_STEPS,
  MIN_STEP_LENGTH,
  completenessComment,
  evaluateFirstPrinciples,
  isStepFilled,
  toFirstPrinciplesMetadata,
  validateFirstPrinciples,
  type FirstPrinciplesDraft,
} from '../first-principles'

const full: FirstPrinciplesDraft = {
  facts: '对象创建后可能被回收，堆内存大小有限',
  assumptions: '大多数对象朝生夕死，这个假设需要验证',
  constraints: '回收时必须处理并发，内存不能无限增长',
  mechanism: '可达性分析 → 标记存活对象 → 回收不可达对象',
  causality: '因为对象朝生夕死，所以分代，所以年轻代用复制算法',
  derivation: '从可达性加分代假设加停顿约束，重新推出需要分代收集器',
}

describe('结构完整度（§16 只评结构，不评对错）', () => {
  it('六环节全填 → 100', () => {
    const r = evaluateFirstPrinciples('为什么 JVM 要分代回收', full)
    expect(r.completeness).toBe(100)
    expect(r.filled).toBe(6)
    expect(r.missing).toEqual([])
  })

  it('全空 → 0，并如实列出全部缺失环节', () => {
    const r = evaluateFirstPrinciples('JVM GC', {})
    expect(r.completeness).toBe(0)
    expect(r.missing).toEqual([...FIRST_PRINCIPLES_STEPS])
  })

  it('填 4 个 → 67（4/6 四舍五入）', () => {
    const r = evaluateFirstPrinciples('JVM GC', {
      facts: full.facts,
      assumptions: full.assumptions,
      constraints: full.constraints,
      mechanism: full.mechanism,
    })
    expect(r.completeness).toBe(67)
    expect(r.missing).toEqual(['causality', 'derivation'])
  })

  it('不判断语义正确性：把内容换成完全不同主题但同样完整，得分仍为 100', () => {
    const other: FirstPrinciplesDraft = {
      facts: 'aaaaaaaa',
      assumptions: 'bbbbbbbb',
      constraints: 'cccccccc',
      mechanism: 'dddddddd',
      causality: 'eeeeeeee',
      derivation: 'ffffffff',
    }
    expect(evaluateFirstPrinciples('任意主题', other).completeness).toBe(100)
  })
})

describe('填写合格判定', () => {
  it('纯空白 / 过短内容不算填写', () => {
    expect(isStepFilled('')).toBe(false)
    expect(isStepFilled('   \n\t ')).toBe(false)
    expect(isStepFilled('太短')).toBe(false)
    expect(isStepFilled('a'.repeat(MIN_STEP_LENGTH))).toBe(true)
  })

  it('去空白后计数：中间夹空格的内容按实际字数判定', () => {
    expect(isStepFilled('abc   def')).toBe(false) // 去空白后 6 字，不足 8
    expect(isStepFilled('abcd   efgh')).toBe(true) // 去空白后 8 字，刚好合格
  })
})

describe('提交前校验', () => {
  it('主题过短 / 环节超长 → 返回第一条错误；合法 → null', () => {
    expect(validateFirstPrinciples('', full)).toBe('请填写推导主题')
    expect(validateFirstPrinciples('x', full)).toBe('请填写推导主题')
    expect(validateFirstPrinciples('JVM GC', { ...full, facts: 'a'.repeat(4001) })).toContain('内容过长')
    expect(validateFirstPrinciples('JVM GC', full)).toBeNull()
  })
})

describe('点评与 metadata 口径', () => {
  it('点评按阈值分档（确定性文案，非 AI 生成）', () => {
    expect(completenessComment(100)).toContain('六个环节都写全了')
    expect(completenessComment(67)).toContain('主体结构已成型')
    expect(completenessComment(34)).toContain('关键环节还空着')
    expect(completenessComment(0)).toContain('推导还很不完整')
  })

  it('metadata 与后端字段一一对应（topic/completeness/filled/total/missing）', () => {
    const r = evaluateFirstPrinciples('JVM 分代', {
      facts: full.facts,
      mechanism: full.mechanism,
    })
    const meta = toFirstPrinciplesMetadata(r)
    expect(meta).toEqual({
      topic: 'JVM 分代',
      completeness: 33,
      filled: 2,
      total: 6,
      missing: ['assumptions', 'constraints', 'causality', 'derivation'],
    })
  })
})
