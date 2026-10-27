// @vitest-environment node
/**
 * 闪卡学习会话纯函数层测试（§三十三：1 卡片加载 / 3~5 自评映射 / 8 LearningEvent / 9 Review-SM-2
 * / 12 knowledgeId / 13 goalId / 14 learningNodeId / 16 学习完成 / 21~23 键盘与空态口径 / 24 隔离口径）。
 *
 * 本文件只测**纯函数**：不挂载组件、不打网络、不写任何状态，
 * 因此可以精确断言「队列优先级」「徽章」「自评→quality」这些决定学习闭环正确性的规则。
 */
import { describe, it, expect } from 'vitest'
import {
  KEYBOARD_HINT,
  RATING_LABEL,
  RATING_QUALITY,
  cardBadges,
  cardRank,
  createSessionQueue,
  dueCount,
  isDue,
  isSessionComplete,
  isSessionInProgress,
  keyToRating,
  qualityToRating,
  ratingToQuality,
  resolveEventKnowledgeId,
  sortQueue,
  splitTags,
  summarizeSession,
  toSessionCard,
  type SessionCard,
} from '@/learning/flashcard-session'
import type { FlashcardVO } from '@/api/types'

const NOW = new Date('2026-09-10T12:00:00Z')

function vo(over: Partial<FlashcardVO> & { id: number }): FlashcardVO {
  return {
    front: `q${over.id}`,
    back: `a${over.id}`,
    category: 'JavaScript',
    difficulty: 1,
    reviewCount: 3,
    ...over,
  }
}

function card(over: Partial<SessionCard> & { id: number }): SessionCard {
  const base = toSessionCard(vo({ id: over.id }))
  return { ...base, rank: 5, badges: [], ...over, id: over.id }
}

describe('三档自评 → SM-2 quality 映射（§十：复用后端，不新建第二套算法）', () => {
  it('不会=1 / 模糊=3 / 记得=5，与后端 FLASHCARD_PASS_QUALITY=3 的及格语义对齐', () => {
    expect(ratingToQuality('unknown')).toBe(1)
    expect(ratingToQuality('familiar')).toBe(3)
    expect(ratingToQuality('mastered')).toBe(5)
    expect(RATING_QUALITY).toEqual({ unknown: 1, familiar: 3, mastered: 5 })
  })

  it('quality → 自评可逆（3 为及格边界，落在「模糊」）', () => {
    expect(qualityToRating(0)).toBe('unknown')
    expect(qualityToRating(2)).toBe('unknown')
    expect(qualityToRating(3)).toBe('familiar')
    expect(qualityToRating(4)).toBe('familiar')
    expect(qualityToRating(5)).toBe('mastered')
  })

  it('标签与快捷键提示是单一来源（模板直接引用，避免三处文案漂移）', () => {
    expect(RATING_LABEL).toEqual({ unknown: '不会', familiar: '模糊', mastered: '记得' })
    expect(KEYBOARD_HINT).toContain('Space 翻卡')
    expect(KEYBOARD_HINT).toContain('1 不会')
    expect(KEYBOARD_HINT).toContain('2 模糊')
    expect(KEYBOARD_HINT).toContain('3 记得')
  })
})

describe('键盘快捷键映射（§十八）', () => {
  it('1/2/3 分别映射到三档自评', () => {
    expect(keyToRating('1')).toBe('unknown')
    expect(keyToRating('2')).toBe('familiar')
    expect(keyToRating('3')).toBe('mastered')
  })

  it('其它按键不映射（不误触评分）', () => {
    for (const k of ['0', '4', 'a', 'Enter', ' ', 'ArrowLeft']) {
      expect(keyToRating(k)).toBeNull()
    }
  })
})

describe('FlashcardVO → SessionCard 映射（§三十：字段一致，不猜 knowledgeId）', () => {
  it('保留真实 SRS / 归属字段，并把标签串拆成数组', () => {
    const c = toSessionCard(
      vo({
        id: 1,
        tags: 'JVM, GC ，并发',
        chapterId: 7,
        pathId: 2,
        docId: 9,
        docTitle: 'JVM 调优',
        sourceType: 'AI_DOC',
        nextReviewTime: '2026-09-01T00:00:00Z',
        reviewCount: 0,
      }),
    )
    expect(c.question).toBe('q1')
    expect(c.answer).toBe('a1')
    expect(c.chapterId).toBe(7)
    expect(c.pathId).toBe(2)
    expect(c.docId).toBe(9)
    expect(c.docTitle).toBe('JVM 调优')
    expect(c.sourceType).toBe('AI_DOC')
    expect(c.tags).toEqual(['JVM', 'GC', '并发'])
    expect(c.nextReviewTime).toBe('2026-09-01T00:00:00Z')
    expect(c.reviewCount).toBe(0)
  })

  it('后端缺字段时给安全兜底（分类「通用」/难度 1），但**不伪造**知识字段', () => {
    const c = toSessionCard({ id: 5 } as FlashcardVO)
    expect(c.category).toBe('通用')
    expect(c.difficulty).toBe(1)
    expect(c.chapterId).toBeNull()
    expect(c.pathId).toBeNull()
    expect(c.docId).toBeNull()
    expect(c.nextReviewTime).toBeNull()
    expect(c.reviewCount).toBe(0)
    expect(c.tags).toEqual([])
  })

  it('标签拆分容错：空串 / null / 全分隔符 → 空数组', () => {
    expect(splitTags(undefined)).toEqual([])
    expect(splitTags(null)).toEqual([])
    expect(splitTags('')).toEqual([])
    expect(splitTags(',，,')).toEqual([])
  })
})

describe('「待复习」判定使用后端真实 nextReviewTime（§十六）', () => {
  it('到期时间已过 → true；未到 / 无排期 → false', () => {
    expect(isDue({ nextReviewTime: '2026-09-09T00:00:00Z' }, NOW)).toBe(true)
    expect(isDue({ nextReviewTime: '2026-09-11T00:00:00Z' }, NOW)).toBe(false)
    expect(isDue({ nextReviewTime: null }, NOW)).toBe(false)
    expect(isDue({ nextReviewTime: '不是时间' }, NOW)).toBe(false)
  })

  it('dueCount 只统计真实到期卡片', () => {
    const cards = [
      card({ id: 1, nextReviewTime: '2026-09-01T00:00:00Z' }),
      card({ id: 2, nextReviewTime: null }),
      card({ id: 3, nextReviewTime: '2026-12-01T00:00:00Z' }),
    ]
    expect(dueCount(cards, NOW)).toBe(1)
  })
})

describe('队列优先级（§十六：NextAction 指定知识点 > 待复习 > 薄弱 > 当前路径 > 其他）', () => {
  const ctx = {
    knowledgeId: 42,
    chapterId: 7,
    pathId: 2,
    weakKnowledgeIds: [99],
    knowledgeByChapter: { 8: [99] },
    now: NOW,
  }

  it('rank 1：NextAction 指定知识点且卡片位于同一学习节点章节', () => {
    expect(cardRank(card({ id: 1, chapterId: 7 }), ctx)).toBe(1)
  })

  it('rank 2：无知识点上下文但已到期', () => {
    expect(cardRank(card({ id: 2, chapterId: 1, nextReviewTime: '2026-09-01T00:00:00Z' }), { ...ctx, knowledgeId: null })).toBe(2)
  })

  it('rank 3：章节映射命中薄弱知识点（仅当真实映射存在）', () => {
    expect(cardRank(card({ id: 3, chapterId: 8 }), { ...ctx, knowledgeId: null })).toBe(3)
    // 映射缺失 → 不得凭空判定为薄弱
    expect(cardRank(card({ id: 3, chapterId: 8 }), { ...ctx, knowledgeId: null, knowledgeByChapter: null })).toBe(5)
  })

  it('rank 4：属于当前学习路径', () => {
    expect(cardRank(card({ id: 4, chapterId: 1, pathId: 2 }), { ...ctx, knowledgeId: null })).toBe(4)
  })

  it('rank 5：其余（新卡 / 无归属）', () => {
    expect(cardRank(card({ id: 5, chapterId: 1, pathId: 1 }), { ...ctx, knowledgeId: null })).toBe(5)
  })

  it('排序稳定且确定性：同 rank 按到期时间、再按 id 升序', () => {
    const list: SessionCard[] = [
      card({ id: 30, chapterId: 9, nextReviewTime: '2026-09-05T00:00:00Z' }),
      card({ id: 10, chapterId: 9, nextReviewTime: '2026-09-01T00:00:00Z' }),
      card({ id: 20, chapterId: 9, nextReviewTime: '2026-09-01T00:00:00Z' }),
      card({ id: 1, chapterId: 7 }),
    ]
    const ordered = sortQueue(list, ctx).map((c) => c.id)
    // 先 rank1（id=1，NextAction 指定章节），再 rank2 按到期时间 → 10/20（同日按 id）→ 30
    expect(ordered).toEqual([1, 10, 20, 30])
    // 幂等：重复排序结果一致
    expect(sortQueue(sortQueue(list, ctx), ctx).map((c) => c.id)).toEqual(ordered)
  })

  it('createSessionQueue 空输入返回空数组（不抛错）', () => {
    expect(createSessionQueue(null, ctx)).toEqual([])
    expect(createSessionQueue(undefined, ctx)).toEqual([])
    expect(createSessionQueue([], ctx)).toEqual([])
  })

  it('createSessionQueue 已把 rank/badges 烘焙进卡片，供模板直接渲染', () => {
    const q = createSessionQueue(
      [vo({ id: 1, chapterId: 7 }), vo({ id: 2, chapterId: 8 })],
      ctx,
    )
    expect(q[0].id).toBe(1)
    expect(q[0].rank).toBe(1)
    expect(q[0].badges.map((b) => b.kind)).toContain('next')
    expect(q[1].rank).toBe(3)
    expect(q[1].badges.map((b) => b.kind)).toContain('weak')
  })
})

describe('「为什么是这张卡」徽章（§十五：轻量，最多 2 条）', () => {
  it('NextAction 推荐 + 待复习可同时出现', () => {
    const badges = cardBadges(card({ id: 1, chapterId: 7, nextReviewTime: '2026-09-01T00:00:00Z' }), {
      knowledgeId: 42,
      chapterId: 7,
      now: NOW,
    })
    expect(badges.map((b) => b.label)).toEqual(['NextAction 推荐', '待复习'])
  })

  it('没有任何真实依据时不产出徽章（不伪造）', () => {
    expect(cardBadges(card({ id: 2, chapterId: 1, pathId: 1, reviewCount: 5 }), { now: NOW })).toEqual([])
  })

  it('新卡徽章来自真实 reviewCount === 0', () => {
    const badges = cardBadges(card({ id: 3, reviewCount: 0 }), { now: NOW })
    expect(badges.map((b) => b.kind)).toEqual(['new'])
  })

  it('超过两条时截断（不做徽章墙）', () => {
    const badges = cardBadges(
      card({ id: 4, chapterId: 7, pathId: 2, nextReviewTime: '2026-09-01T00:00:00Z' }),
      { knowledgeId: 42, chapterId: 7, pathId: 2, now: NOW },
    )
    expect(badges.length).toBeLessThanOrEqual(2)
  })
})

describe('学习完成与会话状态（§十六 / §十九 / §二十）', () => {
  it('isSessionComplete：评分数覆盖队列长度才算完成', () => {
    expect(isSessionComplete(0, 6)).toBe(false)
    expect(isSessionComplete(5, 6)).toBe(false)
    expect(isSessionComplete(6, 6)).toBe(true)
    expect(isSessionComplete(0, 0)).toBe(false) // 空队列不算「完成」
  })

  it('isSessionInProgress：仅「已评价 ≥1 且未完成」才需要退出确认', () => {
    expect(isSessionInProgress(0, 6)).toBe(false)
    expect(isSessionInProgress(3, 6)).toBe(true)
    expect(isSessionInProgress(6, 6)).toBe(false) // 已完成 → 不再拦截离开
  })

  it('汇总：真实分布 + 自评换算分（空会话不给 0）', () => {
    expect(summarizeSession([])).toEqual({
      total: 0,
      mastered: 0,
      familiar: 0,
      unknown: 0,
      selfRatedScore: null,
    })
    const s = summarizeSession(['mastered', 'mastered', 'familiar', 'unknown'])
    expect(s.total).toBe(4)
    expect(s.mastered).toBe(2)
    expect(s.familiar).toBe(1)
    expect(s.unknown).toBe(1)
    // (2*5 + 1*3 + 1*1) / (4*5) * 100 = 14/20 = 70
    expect(s.selfRatedScore).toBe(70)
  })

  it('自评分边界：全记得=100，全不会=20（不是 0，保留真实语义）', () => {
    expect(summarizeSession(['mastered', 'mastered']).selfRatedScore).toBe(100)
    expect(summarizeSession(['unknown', 'unknown']).selfRatedScore).toBe(20)
  })
})

describe('事件 knowledgeId 透传（§十二 / §十三：让 Evidence 能归属知识点）', () => {
  it('优先调用方显式上下文，其次队列上下文', () => {
    expect(resolveEventKnowledgeId(42, { knowledgeId: 7 })).toBe(42)
    expect(resolveEventKnowledgeId(null, { knowledgeId: 7 })).toBe(7)
  })

  it('都没有 / 非法值 → null（后端不写该字段，绝不用 0 或猜测值冒充）', () => {
    expect(resolveEventKnowledgeId(null, {})).toBeNull()
    expect(resolveEventKnowledgeId(undefined, null)).toBeNull()
    expect(resolveEventKnowledgeId(0, { knowledgeId: 0 })).toBeNull()
    expect(resolveEventKnowledgeId(-1, { knowledgeId: -1 })).toBeNull()
  })
})
