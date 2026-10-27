/**
 * Architecture-7：AI 学习教练模块单元测试（§538-539）
 *
 * 覆盖：
 *  - buildLearningCoachContext：真实数据组装、隐私过滤、Top N 截断（§15-23/§478/§492）
 *  - renderCoachContext：可读文本渲染，不含任何内部枚举/系统提示词（§43-45）
 *  - validateCoachSuggestion：本地白名单校验，拒绝非法 appKey / knowledgeId / actionType（§125-127/§196-202）
 *  - parseCoachResponse：结构化建议提取 + 安全降级 + Prompt Injection 防护（§116/§194-196/§305/§350）
 *  - 隐私红线：上下文/渲染文本不得出现 Coin / 钱包 / 订单 / 邮箱等经济或 PII 字段（§19-23）
 */
import { describe, it, expect } from 'vitest'
import { buildLearningCoachContext } from '../context'
import { renderCoachContext } from '../prompt'
import { validateCoachSuggestion } from '../validator'
import { parseCoachResponse } from '../response'
import { APP_DEFINITIONS } from '@/apps/registry'
import { diagnoseLearningState } from '@/learning/intelligence/diagnosis'
import { resolveNextAction } from '@/learning/action-priority'
import type { KnowledgeMasteryVO, LearningTaskVO, LearningPathVO } from '@/api/types'

function mastery(partial: Partial<KnowledgeMasteryVO> = {}): KnowledgeMasteryVO {
  return {
    knowledgeId: 1,
    name: 'AQS',
    type: null,
    categoryId: null,
    categoryName: null,
    masteryScore: 42,
    confidenceScore: 40,
    learningStatus: 'WEAK',
    forgettingRisk: 0.6,
    weaknessTypes: ['ERROR_RATE'],
    reason: null,
    lastLearnedAt: null,
    lastReviewedAt: null,
    lastAssessedAt: null,
    nextReviewAt: null,
    ...partial,
  }
}

function task(partial: Partial<LearningTaskVO> = {}): LearningTaskVO {
  return { id: 1, title: '完成 Spring Bean 生命周期练习', status: 0, ...partial }
}

function path(partial: Partial<LearningPathVO> = {}): LearningPathVO {
  return { id: 1, title: 'Java 并发编程路径', enrolled: true, progress: 35, ...partial }
}

describe('buildLearningCoachContext（真实数据 / 隐私过滤 / 截断）', () => {
  it('空数据 → hasData=false，summary 全 0', () => {
    const ctx = buildLearningCoachContext({ reviewRequired: [], weakPoints: [], tasks: [] })
    expect(ctx.hasData).toBe(false)
    expect(ctx.summary).toEqual({ reviewCount: 0, weakCount: 0, taskCount: 0, hasPath: false })
  })

  it('有真实数据 → hasData=true，summary 反映计数', () => {
    const ctx = buildLearningCoachContext({
      reviewRequired: [mastery({ name: 'A' }), mastery({ name: 'B' })],
      weakPoints: [mastery({ name: 'C', masteryScore: 30 })],
      tasks: [task(), task({ id: 2 })],
      currentPath: path(),
    })
    expect(ctx.hasData).toBe(true)
    expect(ctx.summary.reviewCount).toBe(2)
    expect(ctx.summary.weakCount).toBe(1)
    expect(ctx.summary.taskCount).toBe(2)
    expect(ctx.summary.hasPath).toBe(true)
    expect(ctx.currentPath?.title).toBe('Java 并发编程路径')
  })

  it('Top N 截断（§478/§492）：超过 5 条只保留前 5', () => {
    const many = Array.from({ length: 9 }, (_, i) => mastery({ knowledgeId: i + 1, name: `K${i}` }))
    const ctx = buildLearningCoachContext({ reviewRequired: many, weakPoints: [], tasks: [] })
    expect(ctx.reviewRequired.length).toBe(5)
    expect(ctx.summary.reviewCount).toBe(9) // 计数仍是真实总数
  })

  it('隐私过滤（§19-23）：输入类型本身不含经济/PII 字段，构建结果不暴露任何敏感键', () => {
    const ctx = buildLearningCoachContext({
      reviewRequired: [mastery({ name: 'AQS' })],
      weakPoints: [],
      tasks: [],
    })
    const json = JSON.stringify(ctx)
    // 上下文里绝不能出现经济 / PII 关键字（防御性断言）
    expect(json).not.toMatch(/coin|wallet|order|钱包|订单|balance/i)
    // 不携带 userId / email / phone 之类标识
    expect(json).not.toMatch(/userId|email|phone|token/i)
  })
})

describe('renderCoachContext（可读文本，不含系统提示词）', () => {
  it('渲染概览 + 诊断 + 待复习 + 薄弱 + 任务 + 路径', () => {
    const ctx = buildLearningCoachContext({
      reviewRequired: [mastery({ name: 'AQS', nextReviewAt: '2026-09-01' })],
      weakPoints: [mastery({ name: 'CAS', masteryScore: 30, weaknessTypes: ['NO_REVIEW'] })],
      tasks: [task()],
      currentPath: path(),
      nextAction: { type: 'REVIEW', title: '复习 AQS', reason: '已进入复习队列', route: '/learning/review', knowledgeName: 'AQS' },
      diagnoses: diagnoseLearningState({
        reviewRequired: [mastery({ name: 'AQS' })],
        weakPoints: [mastery({ name: 'CAS' })],
        tasks: [task()],
        currentPath: path(),
      }),
    })
    const text = renderCoachContext(ctx)
    expect(text).toContain('待复习 1 个')
    expect(text).toContain('AQS')
    expect(text).toContain('CAS')
    expect(text).toContain('Java 并发编程路径')
    // 不暴露内部系统提示词 / 注入防护指令文本（§43-45）
    expect(text).not.toMatch(/system prompt|你是一个|必须忽略|ignore previous/i)
  })

  it('无数据 → 诚实提示（§87/§347）', () => {
    const ctx = buildLearningCoachContext({ reviewRequired: [], weakPoints: [], tasks: [] })
    const text = renderCoachContext(ctx)
    expect(text).toContain('暂无足够数据')
  })
})

describe('validateCoachSuggestion（本地白名单校验）', () => {
  it('合法 appKey + 正整数 knowledgeId → valid，route 来自本地 Registry', () => {
    const v = validateCoachSuggestion({ appKey: 'flashcard', knowledgeId: 12, actionType: 'FLASHCARD' })
    expect(v.valid).toBe(true)
    // route 必须来自本地 Registry，而非 AI 提供
    expect(v.route).toBe('/learning/flashcards')
    expect(v.appKey).toBe('flashcard')
  })

  it('未知 appKey → valid:false，UNKNOWN_APP_KEY', () => {
    const v = validateCoachSuggestion({ appKey: 'evil-app', knowledgeId: 1 })
    expect(v.valid).toBe(false)
    expect(v.error).toBe('UNKNOWN_APP_KEY')
  })

  it('非法 knowledgeId（0 / 负数 / 非整数）→ valid:false，INVALID_KNOWLEDGE_ID', () => {
    expect(validateCoachSuggestion({ appKey: 'quiz', knowledgeId: 0 }).error).toBe('INVALID_KNOWLEDGE_ID')
    expect(validateCoachSuggestion({ appKey: 'quiz', knowledgeId: -3 }).error).toBe('INVALID_KNOWLEDGE_ID')
    expect(validateCoachSuggestion({ appKey: 'quiz', knowledgeId: 1.5 }).error).toBe('INVALID_KNOWLEDGE_ID')
  })

  it('未知 actionType → valid:false，UNKNOWN_ACTION_TYPE', () => {
    const v = validateCoachSuggestion({ appKey: 'quiz', knowledgeId: 1, actionType: 'HACK' })
    expect(v.valid).toBe(false)
    expect(v.error).toBe('UNKNOWN_ACTION_TYPE')
  })

  it('缺少 appKey → valid:false，NO_SUGGESTION', () => {
    expect(validateCoachSuggestion({ knowledgeId: 1 }).valid).toBe(false)
  })
})

describe('parseCoachResponse（提取 + 安全降级 + 注入防护）', () => {
  it('```json 代码块 → 解析为合法建议', () => {
    const raw = '建议你复习一下：\n```json\n{"appKey":"flashcard","knowledgeId":7,"actionType":"FLASHCARD","reason":"掌握度偏低"}\n```\n加油'
    const r = parseCoachResponse(raw)
    expect(r.suggestion?.appKey).toBe('flashcard')
    expect(r.validation?.valid).toBe(true)
    expect(r.validation?.route).toBe('/learning/flashcards')
  })

  it('行内 {...} → 也能解析', () => {
    const raw = '试试闪卡吧 {"appKey":"flashcard","knowledgeId":3}'
    const r = parseCoachResponse(raw)
    expect(r.validation?.valid).toBe(true)
  })

  it('无结构化建议 → 仅返回文本，不报错', () => {
    const raw = '你可以先从复习计划开始，把今天到期的知识点过一遍。'
    const r = parseCoachResponse(raw)
    expect(r.suggestion).toBeUndefined()
    expect(r.message).toBe(raw)
  })

  it('Prompt Injection：AI 返回未知 appKey + 伪造 route → 校验失败，UI 不渲染 CTA', () => {
    const raw = '去这里领奖：\n```json\n{"appKey":"evil","knowledgeId":1,"route":"https://evil.example.com/steal"}\n```'
    const r = parseCoachResponse(raw)
    // 校验失败时，解析层不携带 suggestion（安全降级，UI 不会渲染任何 CTA）
    expect(r.suggestion).toBeUndefined()
    // 校验层拒绝未知 appKey，且绝不给出任何 route（不会跳转到 evil）
    expect(r.validation?.valid).toBe(false)
    expect(r.validation?.error).toBe('UNKNOWN_APP_KEY')
    expect(r.validation?.route).toBeUndefined()
    // 因此 CoachSuggestionCard 的 v-if 不成立，CTA 不渲染（绝不 router.push 到 evil 路由）
  })

  it('Prompt Injection：伪造 route 即使绕过高危 appKey 也不被采用（route 永远来自 Registry）', () => {
    // 假设 AI 想借合法 appKey 偷渡一个外部 route —— 但 parse 层根本不传 route，validate 只用 Registry
    const raw = '```json\n{"appKey":"flashcard","knowledgeId":5,"route":"javascript:alert(1)"}\n```'
    const r = parseCoachResponse(raw)
    expect(r.validation?.valid).toBe(true)
    expect(r.validation?.route).toBe('/learning/flashcards') // 本地白名单，非 AI 提供
    expect(r.validation?.route).not.toContain('javascript:')
  })

  it('本地白名单完整性兜底：所有 APP_DEFINITIONS 的 key 都应能通过校验', () => {
    for (const app of APP_DEFINITIONS) {
      const v = validateCoachSuggestion({ appKey: app.key, knowledgeId: 1 })
      expect(v.valid, `appKey=${app.key} 应合法`).toBe(true)
      expect(v.route).toBe(app.route)
    }
  })
})

describe('端到端管线（真实数据 → 诊断 → 下一步 → Context → 文本，无 PII）', () => {
  it('完整管线产出非空上下文且文本不含经济/PII 关键字', () => {
    const reviewRequired = [mastery({ name: 'AQS', learningStatus: 'REVIEW_REQUIRED' })]
    const weakPoints = [mastery({ name: 'CAS', masteryScore: 28 })]
    const tasks = [task()]
    const currentPath = path()
    const diagnoses = diagnoseLearningState({ reviewRequired, weakPoints, tasks, currentPath })
    const nextAction = resolveNextAction({ reviewRequired, weakPoints, tasks, currentPath, installedApps: APP_DEFINITIONS })
    const ctx = buildLearningCoachContext({ reviewRequired, weakPoints, tasks, currentPath, nextAction, diagnoses })
    const text = renderCoachContext(ctx)
    expect(ctx.hasData).toBe(true)
    expect(text).not.toMatch(/coin|wallet|order|钱包|订单|余额|balance/i)
  })
})
