import { describe, expect, it } from 'vitest'
import { parseWritingScore } from '../score'

const valid = () => ({ score: 0, grade: 'D', gradeDescription: '需改进', overallComment: '未覆盖主题', dimensions: ['内容质量', '结构逻辑', '语言表达', '原创性'].map(name => ({ name, score: 0, comment: '未覆盖', color: 'url(unsafe)' })), suggestions: ['补充论点'] })
describe('honest writing assessment', () => {
  it('preserves a real zero and derives safe token colours locally', () => {
    const result = parseWritingScore(JSON.stringify(valid()))
    expect(result.score).toBe(0)
    expect(result.dimensions.every(d => d.color === 'var(--kb-danger)')).toBe(true)
  })
  it('accepts a single JSON fence but never arbitrary surrounding prose', () => {
    expect(parseWritingScore('```json\n' + JSON.stringify(valid()) + '\n```').score).toBe(0)
    expect(() => parseWritingScore('评分为：' + JSON.stringify(valid()))).toThrow()
  })
  it.each(['not json', '{}', 'null', '[]'])('rejects incomplete result %s', raw => {
    expect(() => parseWritingScore(raw)).toThrow()
  })
  it.each([-1, 101, 1.5, '75', null])('rejects invalid overall score %s', score => {
    expect(() => parseWritingScore(JSON.stringify({ ...valid(), score }))).toThrow()
  })
  it('rejects missing, duplicate or invalid dimensions and invented default comments', () => {
    const data = valid()
    data.dimensions[0]!.score = 101
    expect(() => parseWritingScore(JSON.stringify(data))).toThrow()
    expect(() => parseWritingScore(JSON.stringify({ ...valid(), dimensions: [] }))).toThrow()
    expect(() => parseWritingScore(JSON.stringify({ ...valid(), overallComment: '' }))).toThrow()
    expect(() => parseWritingScore(JSON.stringify({ ...valid(), suggestions: [] }))).toThrow()
  })
})
