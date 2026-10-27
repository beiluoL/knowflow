// @vitest-environment node
/**
 * Interaction OS（UI/UX 5.1）§七 / §八：Learning Action Contract
 *
 * 锁死一条交互红线：NextAction 点击后**必须**把「当前在学什么」带到目标页，
 * 用户不许被要求在目标页重新挑一次知识点。
 *
 * 同时锁死白名单纪律：只允许带上该页面**真实消费**的参数（不造假深链）。
 */
import { describe, it, expect } from 'vitest'
import {
  LEARNING_ENTRY_CONTEXT,
  buildEntryContextQuery,
  buildMiniAppEntryQuery,
  describeEntryContext,
  resolveContextualRoute,
  stampActionContext,
} from '@/learning/action-contract'
import type { LearningEntryContext } from '@/learning/action-contract'
import type { NextLearningAction } from '@/learning/types'

function action(partial: Partial<NextLearningAction> & Pick<NextLearningAction, 'type'>): NextLearningAction {
  return { title: 't', reason: 'r', route: '', ...partial } as NextLearningAction
}

const CTX: LearningEntryContext = {
  knowledgeId: 42,
  knowledgeName: 'JVM G1 GC',
  goalId: 7,
  learningNodeId: 301,
}

describe('stampActionContext —— 动作自带的上下文优先，不被外部覆盖', () => {
  it('动作缺上下文时用外部补齐', () => {
    const out = stampActionContext(action({ type: 'FLASHCARD' }), CTX)
    expect(out.knowledgeId).toBe(42)
    expect(out.knowledgeName).toBe('JVM G1 GC')
    expect(out.goalId).toBe(7)
    expect(out.learningNodeId).toBe(301)
  })

  it('动作已有更具体的值时**不覆盖**（动作 > 全局上下文）', () => {
    const out = stampActionContext(
      action({ type: 'FLASHCARD', knowledgeId: 99, knowledgeName: '专属知识点' }),
      CTX,
    )
    expect(out.knowledgeId).toBe(99)
    expect(out.knowledgeName).toBe('专属知识点')
  })

  it('不修改原对象（纯函数）', () => {
    const src = action({ type: 'FLASHCARD' })
    stampActionContext(src, CTX)
    expect(src.knowledgeId).toBeUndefined()
  })

  it('外部上下文为空时不注入任何键', () => {
    const out = stampActionContext(action({ type: 'FLASHCARD' }), {})
    expect(out.knowledgeId).toBeUndefined()
    expect(out.goalId).toBeUndefined()
  })
})

describe('LEARNING_ENTRY_CONTEXT —— 白名单必须与页面真实消费一致', () => {
  it('chapterId 仅传给闪卡和实际消费关联上下文的资源页', () => {
    expect(LEARNING_ENTRY_CONTEXT['/learning/flashcards']).toContain('chapterId')
    for (const [path, params] of Object.entries(LEARNING_ENTRY_CONTEXT)) {
      if (['/learning/flashcards','/doc/:id','/learning/code-practice/:id','/learning/knowledge-mastery/:id'].includes(path)) { expect(params).toContain('chapterId'); continue }
      expect(params).not.toContain('chapterId')
    }
  })

  it('所有入口都只声明 knowledgeId / knowledgeName（外加闪卡的 chapterId）', () => {
    const allowed = new Set(['knowledgeId', 'knowledgeName', 'chapterId'])
    for (const params of Object.values(LEARNING_ENTRY_CONTEXT)) {
      for (const p of params) expect(allowed.has(p)).toBe(true)
    }
  })

  it('未登记的页面没有任何可传参数（不制造假深链）', () => {
    expect(buildEntryContextQuery('/learning/center', CTX)).toEqual({})
    expect(buildEntryContextQuery('/some/random/page', CTX)).toEqual({})
  })
})

describe('buildEntryContextQuery —— 只带真实消费的参数', () => {
  it('闪卡入口带 knowledgeId / knowledgeName / chapterId', () => {
    expect(buildEntryContextQuery('/learning/flashcards', CTX)).toEqual({
      knowledgeId: '42',
      knowledgeName: 'JVM G1 GC',
      chapterId: '301',
    })
  })

  it('测验入口不带 chapterId（该页不读它）', () => {
    const q = buildEntryContextQuery('/learning/quiz', CTX)
    expect(q.chapterId).toBeUndefined()
    expect(q.knowledgeId).toBe('42')
    expect(q.knowledgeName).toBe('JVM G1 GC')
  })

  it('goalId / learningNodeId **不写进 query**（当前无页面读取，写进去就是假深链）', () => {
    for (const path of Object.keys(LEARNING_ENTRY_CONTEXT)) {
      const q = buildEntryContextQuery(path, CTX)
      expect(q.goalId).toBeUndefined()
      if (path !== '/learning/flashcards') expect(q.learningNodeId).toBeUndefined()
    }
  })

  it('值为 null / 空串时不产生该参数', () => {
    expect(buildEntryContextQuery('/learning/quiz', { knowledgeId: null, knowledgeName: '' })).toEqual({})
  })
})

describe('resolveContextualRoute —— 修掉「点进去重新挑知识点」的核心契约', () => {
  it('FLASHCARD：原来只有裸路由，现在带上下文', () => {
    const route = resolveContextualRoute(action({ type: 'FLASHCARD' }), CTX)
    expect(route).toContain('/learning/flashcards?')
    expect(route).toContain('knowledgeId=42')
    expect(route).toContain('knowledgeName=JVM+G1+GC')
    expect(route).toContain('chapterId=301')
  })

  it('QUIZ：带 knowledgeId / knowledgeName，不带 chapterId', () => {
    const route = resolveContextualRoute(action({ type: 'QUIZ' }), CTX)
    expect(route).toContain('knowledgeId=42')
    expect(route).not.toContain('chapterId')
  })

  it('REVIEW：带上下文（本阶段给 ReviewPlan 补上了消费）', () => {
    expect(resolveContextualRoute(action({ type: 'REVIEW' }), CTX)).toContain('knowledgeId=42')
  })

  it('无上下文时退回裸路由 —— 不产生孤零零的 `?`', () => {
    const route = resolveContextualRoute(action({ type: 'FLASHCARD' }), {})
    expect(route).toBe('/learning/flashcards')
    expect(route).not.toContain('?')
  })

  it('既有知识 NextAction 保持原路由，章节关联由显式资源导航传递', () => {
    const route = resolveContextualRoute(action({ type: 'KNOWLEDGE', knowledgeId: 42 }), CTX)
    expect(route).toBe('/learning/knowledge-mastery/42')
    expect(route).not.toContain('?knowledgeId')
  })

  it('EVIDENCE_PRACTICE 自带的 query 优先，不被覆盖', () => {
    const a = action({
      type: 'EVIDENCE_PRACTICE',
      route: '/learning/flashcards?knowledgeId=888&knowledgeName=%E8%87%AA%E5%B8%A6',
    })
    const route = resolveContextualRoute(a, CTX)
    expect(route).toContain('knowledgeId=888')
    expect(route).not.toContain('knowledgeId=42')
    // 页面自带 query 里没有 chapterId 时，仍应补上真实的学习节点
    expect(route).toContain('chapterId=301')
  })

  it('非法 EVIDENCE_PRACTICE 路由回退学习中心（白名单纪律不被本模块绕过）', () => {
    const route = resolveContextualRoute(
      action({ type: 'EVIDENCE_PRACTICE', route: 'https://evil.example.com/hack' }),
      CTX,
    )
    expect(route).toBe('/learning/center')
  })

  it('MINI_APP 的核心学习页（flashcard）也能带上 chapterId', () => {
    // flashcard Mini App 的 route 就是 /learning/flashcards
    const q = buildMiniAppEntryQuery('/learning/flashcards', action({ type: 'MINI_APP' }), CTX)
    expect(q.knowledgeId).toBe('42')
    expect(q.knowledgeName).toBe('JVM G1 GC')
    expect(q.chapterId).toBe('301')
  })

  it('MINI_APP 的非学习页（故事/写作）只带 knowledgeId / knowledgeName', () => {
    const q = buildMiniAppEntryQuery('/workbench/story', action({ type: 'MINI_APP' }), CTX)
    expect(q).toEqual({ knowledgeId: '42', knowledgeName: 'JVM G1 GC' })
  })
})

describe('describeEntryContext —— 不返回虚构文案', () => {
  it('有名称时用人话描述', () => {
    expect(describeEntryContext(CTX)).toBe('正在学习：JVM G1 GC')
  })

  it('只有 id 时退化为编号（不是「未知知识点」）', () => {
    expect(describeEntryContext({ knowledgeId: 42 })).toBe('正在学习：知识点 #42')
  })

  it('完全没有上下文时返回 null，由调用方决定不渲染', () => {
    expect(describeEntryContext({})).toBeNull()
  })
})
