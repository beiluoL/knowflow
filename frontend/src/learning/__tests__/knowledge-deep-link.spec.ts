/**
 * Architecture-4.2 §15~§18：知识点深链路由测试（纯函数，可单测）。
 *
 * 重点验证：
 *  - §15 appendKnowledgeQuery 只在白名单路由（scenario / feynman / first-principles）追加 ?knowledgeId=N；
 *  - §15 知识点名称透传为 knowledgeName query；
 *  - §12 兼容方式：闪卡按当前章节过滤 ?chapterId=N；
 *  - §29/§33 上下文只影响“打开哪一个具体知识点”，不新建第二套路由体系。
 */
import { describe, it, expect } from 'vitest'
import { appendKnowledgeQuery, appendChapterQuery, KNOWLEDGE_DEEP_LINK_ROUTES } from '../route'
import { targetRoute } from '../evidence/strategy'

describe('§15 知识点深链 query（route.ts）', () => {
  it('白名单路由 + knowledgeId → 追加 ?knowledgeId=N', () => {
    expect(appendKnowledgeQuery('/learning/scenario', 123)).toBe('/learning/scenario?knowledgeId=123')
  })
  it('白名单路由 + knowledgeId + knowledgeName → 同时追加', () => {
    const q = appendKnowledgeQuery('/learning/feynman', 123, 'JVM GC')
    expect(q).toContain('knowledgeId=123')
    expect(q).toContain('knowledgeName=JVM+GC')
  })
  it('非白名单路由 → 原样返回（不制造假深链）', () => {
    expect(appendKnowledgeQuery('/learning/center', 123)).toBe('/learning/center')
  })
  it('knowledgeId 为空 → 原样返回', () => {
    expect(appendKnowledgeQuery('/learning/scenario', null)).toBe('/learning/scenario')
  })
  it('§12 闪卡按当前章节过滤 → ?chapterId=N', () => {
    expect(appendChapterQuery('/learning/flashcards', 12)).toBe('/learning/flashcards?chapterId=12')
  })
  it('非闪卡路由 → 不加 chapterId', () => {
    expect(appendChapterQuery('/learning/feynman', 12)).toBe('/learning/feynman')
  })
  it('§15 白名单含 scenario / feynman / first-principles 三页', () => {
    expect(KNOWLEDGE_DEEP_LINK_ROUTES).toEqual(
      expect.arrayContaining(['/learning/scenario', '/learning/feynman', '/learning/first-principles']),
    )
  })
})

describe('targetRoute 携带知识点上下文（strategy.ts）', () => {
  it('FEYNMAN 路由 + knowledgeId → /learning/feynman?knowledgeId=123', () => {
    const t = { kind: 'ROUTE', path: '/learning/feynman', label: '费曼表达' } as const
    expect(targetRoute(t, { knowledgeId: 123 })).toBe('/learning/feynman?knowledgeId=123')
  })
  it('SCENARIO 路由 + knowledgeId + name → 两个 query 都带', () => {
    const t = { kind: 'ROUTE', path: '/learning/scenario', label: '场景练习' } as const
    const r = targetRoute(t, { knowledgeId: 123, knowledgeName: 'JVM GC' })
    expect(r).toContain('knowledgeId=123')
    expect(r).toContain('knowledgeName=JVM+GC')
  })
  it('§12 FLASHCARD 路由 + learningNodeId → ?chapterId=12（按当前章节过滤）', () => {
    const t = { kind: 'ROUTE', path: '/learning/flashcards', label: '闪卡' } as const
    expect(targetRoute(t, { learningNodeId: 12 })).toBe('/learning/flashcards?chapterId=12')
  })
  it('无上下文 → 原样返回基础路由', () => {
    const t = { kind: 'ROUTE', path: '/learning/scenario', label: '场景练习' } as const
    expect(targetRoute(t)).toBe('/learning/scenario')
  })
})
