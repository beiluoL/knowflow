// @vitest-environment happy-dom
/**
 * UI/UX 2.2 §217：Context 透传（knowledgeId → Mini App query）
 * 验证 buildMiniAppContextQuery 从 NextAction 取出真实知识点上下文。
 */
import { describe, it, expect } from 'vitest'
import { buildMiniAppContextQuery } from '@/learning/route'
import type { NextLearningAction } from '@/learning/types'

describe('buildMiniAppContextQuery（UI/UX 2.2 §217）', () => {
  it('carries knowledgeId + knowledgeName when present', () => {
    const action: NextLearningAction = {
      type: 'MINI_APP',
      title: '用闪卡巩固「AQS」',
      reason: 'r',
      route: '/learning/flashcards',
      appKey: 'flashcard',
      knowledgeId: 123,
      knowledgeName: 'AQS',
    }
    expect(buildMiniAppContextQuery(action)).toEqual({
      knowledgeId: '123',
      knowledgeName: 'AQS',
    })
  })

  it('returns empty query when no knowledge context (§50 不伪造)', () => {
    const action: NextLearningAction = {
      type: 'MINI_APP',
      title: '打开应用',
      reason: 'r',
      route: '/learning/flashcards',
      appKey: 'flashcard',
    }
    expect(buildMiniAppContextQuery(action)).toEqual({})
  })

  it('omits knowledgeName when only id present', () => {
    const action: NextLearningAction = {
      type: 'MINI_APP',
      title: 't',
      reason: 'r',
      route: '/x',
      appKey: 'flashcard',
      knowledgeId: 9,
    }
    expect(buildMiniAppContextQuery(action)).toEqual({ knowledgeId: '9' })
  })
})
