// @vitest-environment node
/**
 * UI/UX 5.0 §十一/§十九：AI 出题同域一致性（deterministic validation）
 *
 * 题目一致性铁律：题干与 4 个选项属于同一知识域 ——
 * 「JavaScript 类型题」的选项里绝不能混入 Self-Attention / 变压器等其他领域答案。
 *
 * 覆盖：
 *  1. 同域出题：干扰项只来自同 chapter/path/doc/category，跨域卡绝不混入
 *  2. 正确项永远在选项中且可被 indexOf 定位（schema validation）
 *  3. 同域干扰不足 3 → 放弃该题（宁缺毋滥，不做跨域回退）
 *  4. 无任何归属的孤立卡无法成题（自身同域不足）
 *  5. 选项去重（同域重复 back 不重复计入）
 *  6. limit 上限生效
 */
import { describe, it, expect } from 'vitest'
import {
  buildQuizFromCards,
  cardDomainKey,
  type QuizCardSource,
} from '@/learning/quiz-builder'

/** 造卡工具：domain 用 group 前缀生成不同归属，保证互相可区分 */
function card(id: number, front: string, back: string, group: string, extra: Partial<QuizCardSource> = {}): QuizCardSource {
  // 归属按 group：chapter:10 / path:20 / doc:30 / category:40 / raw:'' 五种域；
  // extra 放在最后展开，允许用例覆盖/置空归属字段
  return {
    id,
    front,
    back,
    chapterId: group === 'chapter' ? 10 : undefined,
    pathId: group === 'path' ? 20 : undefined,
    docId: group === 'doc' ? 30 : undefined,
    categoryId: group === 'category' ? 40 : undefined,
    category: group === 'raw' ? '无归属' : undefined,
    ...extra,
  }
}

/** 制造「同 group 的 n 张内容互异」的卡组 */
function makeDomain(group: string, prefix: string, n: number, startId = 1000): QuizCardSource[] {
  return Array.from({ length: n }, (_, i) => card(startId + i, `${prefix} 问题 ${i + 1}？`, `${prefix} 答案 ${i + 1}`, group))
}

describe('quiz-builder：同域一致性（§十一）', () => {
  it('JS 域卡片只与同域卡组题，跨域（Self-Attention）内容绝不混入选项', () => {
    // JS 域 5 张 + 单独一张 Transformer 域（该域仅 1 张 → 无法成题，也证明跨域被隔离）
    const js = makeDomain('path', 'JavaScript 类型转换', 5, 1)
    const transformer = [card(900, 'Self-Attention 的 Q/K/V 是什么？', 'Q/K/V 是对输入做线性变换得到的三个向量', 'path', { pathId: 99 })]
    const quiz = buildQuizFromCards([...js, ...transformer], 12)

    expect(quiz.length).toBeGreaterThanOrEqual(1)
    for (const q of quiz) {
      // 选项绝不出现 Transformer 答案
      for (const opt of q.options) {
        expect(opt).not.toContain('Q/K/V 是对输入做线性变换得到的三个向量')
        expect(opt).not.toMatch(/Self-Attention|Transformer|Q\/K\/V/)
      }
      // 正确项一定在选项中（schema validation）
      expect(q.options[q.correctAnswer]).toBe(q.answer)
      expect(q.options).toContain(q.answer)
      // 选项恰好 4 个且互异
      expect(q.options).toHaveLength(4)
      expect(new Set(q.options).size).toBe(4)
      // 题干与答案同域（域键一致）
      expect(q.domain).toBe('path:20')
    }
  })

  it('干扰项只取同 chapter 卡，doc 域内容不进入 chapter 题的选项', () => {
    const chapter = makeDomain('chapter', 'Java 并发', 5, 200)
    const otherDoc = makeDomain('doc', '网络协议', 6, 300)
    const quiz = buildQuizFromCards([...chapter, ...otherDoc], 12)
    // doc 域 6 张自成域 → 也会成题；chapter 域 5 张各带 4 同域干扰 → 各成 1 题
    expect(quiz.length).toBe(11)
    const chapterQuiz = quiz.filter((q) => q.domain === 'chapter:10')
    expect(chapterQuiz.length).toBe(5)
    for (const q of chapterQuiz) {
      expect(q.options).toHaveLength(4)
      for (const opt of q.options) expect(opt).not.toMatch(/网络协议/)
      expect(q.options[q.correctAnswer]).toBe(q.answer)
      expect(q.answer).toMatch(/Java 并发/)
    }
    // doc 域题同样不含 chapter 答案
    const docQuiz = quiz.filter((q) => q.domain === 'doc:30')
    expect(docQuiz.length).toBe(6)
    for (const q of docQuiz) {
      for (const opt of q.options) expect(opt).not.toMatch(/Java 并发/)
    }
  })

  it('同域干扰不足 3 → 放弃该题（宁缺毋滥），不跨域回退', () => {
    // 两个不同 path 域各 2 张：每个域内干扰仅 1 个 → 全部放弃
    const a = [card(500, 'A1？', 'A1 答案', 'path'), card(501, 'A2？', 'A2 答案', 'path')]
    const b = [card(600, 'B1？', 'B1 答案', 'path', { pathId: 21 }), card(601, 'B2？', 'B2 答案', 'path', { pathId: 21 })]
    const quiz = buildQuizFromCards([...a, ...b], 12)
    expect(quiz).toHaveLength(0)
  })

  it('raw 兜底域：完全无归属但内容相近（同 raw）的卡可互相成题', () => {
    // 每张卡无任何归属字段 → 域键全部为 raw:（空分类名）→ 同域有 5 干扰 → 可成题
    const lonely = Array.from({ length: 6 }, (_, i) =>
      card(700 + i, `孤立问题 ${i}`, `孤立答案 ${i}`, 'raw', {
        chapterId: undefined,
        pathId: undefined,
        docId: undefined,
        categoryId: undefined,
        category: undefined,
      }),
    )
    const quiz = buildQuizFromCards(lonely, 12)
    expect(quiz.length).toBeGreaterThanOrEqual(1)
    for (const q of quiz) expect(q.domain).toBe('raw:')
  })

  it('选项去重：同域重复 back 不重复计数，limit 上限生效', () => {
    // 同域内 2 个「答案 1」重复 + 3 个不同答案 → 去重后干扰仍够 3
    const dup = [
      card(1, 'P1？', '答案 1', 'path'),
      card(2, 'P2？', '答案 1', 'path'), // 与 card1 同 back
      card(3, 'P3？', '答案 2', 'path'),
      card(4, 'P4？', '答案 3', 'path'),
      card(5, 'P5？', '答案 4', 'path'),
    ]
    const quiz = buildQuizFromCards(dup, 2)
    expect(quiz.length).toBeLessThanOrEqual(2) // limit=2 生效
    for (const q of quiz) expect(new Set(q.options).size).toBe(4)
  })

  it('cardDomainKey 优先级：chapter > path > doc > category > raw', () => {
    const c = card(1, 'f', 'b', 'raw', { chapterId: 5, pathId: 6, docId: 7, categoryId: 8, category: 'x' })
    expect(cardDomainKey(c)).toBe('chapter:5')
    expect(cardDomainKey({ ...c, chapterId: undefined })).toBe('path:6')
    expect(cardDomainKey({ ...c, chapterId: undefined, pathId: undefined })).toBe('doc:7')
    expect(cardDomainKey({ ...c, chapterId: undefined, pathId: undefined, docId: undefined })).toBe('category:8')
    expect(
      cardDomainKey({ ...c, chapterId: undefined, pathId: undefined, docId: undefined, categoryId: undefined }),
    ).toBe('raw:x')
  })
})
