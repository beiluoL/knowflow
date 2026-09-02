/**
 * Architecture-4：Next Action Resolver 单元测试（§246-250/§260-264）
 *
 * 覆盖：
 *  - 有 Review → REVIEW（§247）
 *  - 无 Review 有 Task → TASK（§248）
 *  - 无 Task 有 Weak → WEAK（§249，优先费曼/闪卡 App，缺失降级知识详情）
 *  - 都没有 → EXPLORE（§250）
 *  - 有进行中路径 → LEARNING_PATH
 *  - 循环保护：最近完成类型跳过（§260-264）
 */
import { describe, it, expect, afterEach, vi } from 'vitest'
import { resolveNextAction, setLastCompleted, getLastCompleted } from '../action-priority'
import type { LearningActionContext, LearningActionType } from '../types'
import type { KnowledgeMasteryVO, LearningTaskVO, LearningPathVO } from '@/api/types'
import type { AppDefinition } from '@/apps/types'

// node 环境无 sessionStorage：注入内存 stub，验证跨页面记录与过期逻辑
const storageStub = new Map<string, string>()
vi.stubGlobal('sessionStorage', {
  getItem: (k: string) => storageStub.get(k) ?? null,
  setItem: (k: string, v: string) => { storageStub.set(k, String(v)) },
  removeItem: (k: string) => { storageStub.delete(k) },
})

function mastery(partial: Partial<KnowledgeMasteryVO>): KnowledgeMasteryVO {
  return {
    knowledgeId: 1,
    name: 'AQS',
    type: null,
    categoryId: null,
    categoryName: null,
    masteryScore: 42,
    confidenceScore: 40,
    learningStatus: 'REVIEW_REQUIRED',
    forgettingRisk: 0.6,
    weaknessTypes: [],
    reason: null,
    lastLearnedAt: null,
    lastReviewedAt: null,
    lastAssessedAt: null,
    nextReviewAt: null,
    ...partial,
  }
}

function task(partial: Partial<LearningTaskVO> = {}): LearningTaskVO {
  return {
    id: 1,
    title: '阅读第 3 章',
    type: 'study',
    status: 0,
    ...partial,
  } as LearningTaskVO
}

function path(partial: Partial<LearningPathVO> = {}): LearningPathVO {
  return {
    id: 10,
    title: 'Java 并发',
    enrolled: true,
    progress: 40,
    ...partial,
  } as LearningPathVO
}

function app(key: string): AppDefinition {
  return { key, name: key, description: '', icon: 'layers', category: 'PRACTICE', categoryLabel: '练习', route: `/app/${key}`, version: '1.0.0', status: 'ACTIVE', defaultInstalled: false, capabilities: [] } as AppDefinition
}

function baseCtx(): LearningActionContext {
  return {
    reviewRequired: [],
    weakPoints: [],
    tasks: [],
    currentPath: null,
    installedApps: [app('feynman'), app('flashcard')],
  }
}

// 每个用例之间清空会话级循环保护记录
afterEach(() => {
  try {
    sessionStorage.removeItem('knowflow:last-completed')
  } catch {
    // 忽略
  }
})

describe('resolveNextAction 优先级（§54/§131）', () => {
  it('有待复习 → REVIEW（§55/§247）', () => {
    const action = resolveNextAction({
      ...baseCtx(),
      reviewRequired: [mastery({}), mastery({ knowledgeId: 2, name: 'CAS' })],
    })
    expect(action.type).toBe('REVIEW')
    expect(action.route).toBe('/learning/review')
    expect(action.reason).toContain('复习队列')
    expect(action.title).toContain('AQS')
  })

  it('无 Review 有未完成任务 → TASK（§248）', () => {
    const action = resolveNextAction({
      ...baseCtx(),
      tasks: [task({ id: 1, status: 0 })],
    })
    expect(action.type).toBe('TASK')
    expect(action.route).toBe('/learning/center')
    expect(action.title).toContain('阅读第 3 章')
  })

  it('无 Task 有薄弱知识点 → 优先费曼 App（§56/§130/§249）', () => {
    const action = resolveNextAction({
      ...baseCtx(),
      weakPoints: [mastery({ masteryScore: 35 })],
    })
    expect(action.type).toBe('MINI_APP')
    expect(action.appKey).toBe('feynman')
    expect(action.knowledgeId).toBe(1)
    expect(action.route).toBe('/app/feynman')
    expect(action.reason).toContain('35%')
  })

  it('薄弱知识点但未安装费曼 → 闪卡 App', () => {
    const action = resolveNextAction({
      ...baseCtx(),
      installedApps: [app('flashcard')],
      weakPoints: [mastery({})],
    })
    expect(action.type).toBe('FLASHCARD')
    expect(action.appKey).toBe('flashcard')
  })

  it('薄弱知识点但无任何技能 App → 降级知识详情（§56）', () => {
    const action = resolveNextAction({
      ...baseCtx(),
      installedApps: [],
      weakPoints: [mastery({ knowledgeId: 7 })],
    })
    expect(action.type).toBe('KNOWLEDGE')
    expect(action.route).toBe('/learning/knowledge-mastery/7')
    expect(action.knowledgeId).toBe(7)
  })

  it('有进行中的学习路径 → LEARNING_PATH（§57）', () => {
    const action = resolveNextAction({
      ...baseCtx(),
      currentPath: path({ progress: 40 }),
    })
    expect(action.type).toBe('LEARNING_PATH')
    expect(action.route).toBe('/learning/path/10')
  })

  it('路径未报名不算进行中 → 不推荐路径', () => {
    const action = resolveNextAction({
      ...baseCtx(),
      currentPath: path({ enrolled: false }),
    })
    expect(action.type).not.toBe('LEARNING_PATH')
  })

  it('都没有 → EXPLORE 兜底（§250）', () => {
    const action = resolveNextAction(baseCtx())
    expect(action.type).toBe('EXPLORE')
    expect(action.route).toBe('/learning/paths')
  })
})

describe('循环保护（§260-264）', () => {
  it('刚完成 REVIEW → 跳过 REVIEW，落到 TASK', () => {
    const action = resolveNextAction({
      ...baseCtx(),
      reviewRequired: [mastery({})],
      tasks: [task({ id: 2, status: 0 })],
      lastCompleted: { type: 'REVIEW', at: Date.now() },
    })
    expect(action.type).toBe('TASK')
  })

  it('刚完成 FLASHCARD → 薄弱点不再推荐闪卡，降级知识详情', () => {
    const action = resolveNextAction({
      ...baseCtx(),
      installedApps: [app('flashcard')],
      weakPoints: [mastery({})],
      lastCompleted: { type: 'FLASHCARD', at: Date.now() },
    })
    expect(action.type).toBe('KNOWLEDGE')
  })

  it('刚完成 QUIZ → 薄弱点仍可推荐费曼（§262）', () => {
    const action = resolveNextAction({
      ...baseCtx(),
      weakPoints: [mastery({})],
      lastCompleted: { type: 'QUIZ', at: Date.now() },
    })
    expect(action.type).toBe('MINI_APP')
    expect(action.appKey).toBe('feynman')
  })

  it('sessionStorage 记录同样生效（跨页面 §264）', () => {
    setLastCompleted('REVIEW' as LearningActionType)
    expect(getLastCompleted()?.type).toBe('REVIEW')
    const action = resolveNextAction({
      ...baseCtx(),
      reviewRequired: [mastery({})],
    })
    expect(action.type).not.toBe('REVIEW')
  })

  it('过期记录（>30 分钟）不生效', () => {
    try {
      sessionStorage.setItem('knowflow:last-completed', JSON.stringify({ type: 'REVIEW', at: Date.now() - 31 * 60 * 1000 }))
    } catch {
      // 忽略
    }
    expect(getLastCompleted()).toBeNull()
    const action = resolveNextAction({
      ...baseCtx(),
      reviewRequired: [mastery({})],
    })
    expect(action.type).toBe('REVIEW')
  })

  it('显式 skip 参数生效', () => {
    const action = resolveNextAction(
      { ...baseCtx(), reviewRequired: [mastery({})] },
      ['REVIEW'],
    )
    expect(action.type).not.toBe('REVIEW')
  })
})
