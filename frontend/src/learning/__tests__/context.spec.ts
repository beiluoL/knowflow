/**
 * Architecture-4.2 §3~§8：Knowledge Context 解析器测试。
 *
 * 重点验证：
 *  - §3~§6 resolveLearningContext 仅用真实映射推导 Goal → Knowledge（章节 → 文档 → 知识点）；
 *  - §17 当前节点与章节映射不一致时绝不串线（mappingMatchesNode 守卫）；
 *  - §8 resolveEvidenceKnowledgeId 的优先级链（metadata > ctx > explicit > null，禁止猜测）；
 *  - knowledgeNameOf / hasKnowledge 取不到真实名称时返回 null（禁止「知识点 N」占位）。
 */
import { describe, it, expect } from 'vitest'
import {
  resolveLearningContext,
  resolveEvidenceKnowledgeId,
  knowledgeNameOf,
  hasKnowledge,
  EMPTY_LEARNING_CONTEXT,
} from '../context'
import type {
  LearningGoalVO,
  ChapterNodeVO,
  ChapterKnowledgeVO,
  KnowledgeRefVO,
  KnowledgeMasteryVO,
} from '@/api/types'

const goal = { id: 7, title: '7 天掌握 JVM GC', pathId: 2, status: 'ACTIVE' } as LearningGoalVO
const node = { id: 12, title: 'GC 算法' } as ChapterNodeVO
const knowledge: KnowledgeRefVO[] = [{ id: 42, name: 'JVM GC', type: 'CONCEPT', source: 'DOC' }]
const ck = { chapterId: 12, chapterTitle: 'GC 算法', docIds: [1], knowledge } as ChapterKnowledgeVO
const masteries: KnowledgeMasteryVO[] = [
  { knowledgeId: 42, name: 'JVM GC' },
] as KnowledgeMasteryVO[]

describe('§3-§8 resolveLearningContext', () => {
  it('全空来源 → 返回全 null 的空上下文（不伪造）', () => {
    expect(resolveLearningContext({})).toEqual(EMPTY_LEARNING_CONTEXT)
  })

  it('仅 goal → 只绑 goalId/goalTitle/pathId，其余为 null', () => {
    const ctx = resolveLearningContext({ goal })
    expect(ctx.goalId).toBe(7)
    expect(ctx.goalTitle).toBe('7 天掌握 JVM GC')
    expect(ctx.pathId).toBe(2)
    expect(ctx.learningNodeId).toBeNull()
    expect(ctx.knowledgeId).toBeNull()
    expect(ctx.knowledgeTitle).toBeNull()
  })

  it('goal + 当前节点 → 绑 learningNodeId/Title', () => {
    const ctx = resolveLearningContext({ goal, currentNode: node })
    expect(ctx.learningNodeId).toBe(12)
    expect(ctx.learningNodeTitle).toBe('GC 算法')
  })

  it('真实章节映射 → 知识点来自 mapping（优先已被掌握度跟踪者）', () => {
    const ctx = resolveLearningContext({ goal, currentNode: node, chapterKnowledge: ck, masteries })
    expect(ctx.knowledgeId).toBe(42)
    expect(ctx.knowledgeTitle).toBe('JVM GC')
  })

  it('调用方显式 knowledgeId 覆盖 mapping（最具体优先，§8）', () => {
    const ctx = resolveLearningContext({ goal, currentNode: node, chapterKnowledge: ck, explicitKnowledgeId: 99 })
    expect(ctx.knowledgeId).toBe(99)
    expect(ctx.knowledgeTitle).toBeNull() // 显式 id 无显式 name，且 mapping 不匹配 99 → 不编造名称
  })

  it('显式 knowledgeName 透传（即便无显式 id）', () => {
    const ctx = resolveLearningContext({ goal, explicitKnowledgeName: '显式知识' })
    expect(ctx.knowledgeTitle).toBe('显式知识')
    expect(ctx.knowledgeId).toBeNull()
  })

  it('§17 章节映射不对应当前节点 → 不串线，知识点为 null', () => {
    const otherCk = { ...ck, chapterId: 999 } as ChapterKnowledgeVO
    const ctx = resolveLearningContext({ goal, currentNode: node, chapterKnowledge: otherCk })
    expect(ctx.knowledgeId).toBeNull()
  })
})

describe('§8 resolveEvidenceKnowledgeId', () => {
  it('事件 metadata 真实 knowledgeId 优先', () => {
    expect(resolveEvidenceKnowledgeId(42, { knowledgeId: 7 } as never, 99)).toBe(42)
  })
  it('metadata 缺失 → 取 context 的 knowledgeId', () => {
    expect(resolveEvidenceKnowledgeId(null, { knowledgeId: 7 } as never, 99)).toBe(7)
  })
  it('metadata 与 context 均缺失 → 取显式兜底', () => {
    expect(resolveEvidenceKnowledgeId(null, { knowledgeId: null } as never, 99)).toBe(99)
  })
  it('全部缺失 → null（禁止猜测，§33）', () => {
    expect(resolveEvidenceKnowledgeId(null, { knowledgeId: null } as never, null)).toBeNull()
  })
  it('metadata <= 0 视为无效 → 回退', () => {
    expect(resolveEvidenceKnowledgeId(0, { knowledgeId: 7 } as never)).toBe(7)
  })
})

describe('knowledgeNameOf', () => {
  it('命中 → 返回真实名称', () => {
    expect(knowledgeNameOf(masteries, 42)).toBe('JVM GC')
  })
  it('id 为 null → null', () => {
    expect(knowledgeNameOf(masteries, null)).toBeNull()
  })
  it('无 masteries → null', () => {
    expect(knowledgeNameOf(null, 42)).toBeNull()
  })
  it('未命中 → null（禁止「知识点 N」占位，§19）', () => {
    expect(knowledgeNameOf(masteries, 999)).toBeNull()
  })
})

describe('hasKnowledge', () => {
  it('有 knowledgeId → true', () => {
    expect(hasKnowledge({ ...EMPTY_LEARNING_CONTEXT, knowledgeId: 1 })).toBe(true)
  })
  it('null / 无 knowledgeId → false', () => {
    expect(hasKnowledge(null)).toBe(false)
    expect(hasKnowledge(EMPTY_LEARNING_CONTEXT)).toBe(false)
  })
})
