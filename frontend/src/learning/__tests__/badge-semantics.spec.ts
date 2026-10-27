// @vitest-environment node
/**
 * Interaction OS（UI/UX 5.1）§十八：统一数据状态标识
 *
 * 锁死一条一致性红线：同一语义状态在所有页面必须是**同一个文案 + 同一个语义色**。
 */
import { describe, it, expect } from 'vitest'
import type { MasteryStatus } from '@/api/types'
import {
  MASTERY_STATUS_BADGE,
  badgeForAiGenerated,
  badgeForCompleted,
  badgeForCount,
  badgeForDue,
  badgeForMasteryStatus,
} from '@/learning/badge-semantics'

const ALL_STATUSES: MasteryStatus[] = [
  'NOT_STARTED',
  'LEARNING',
  'WEAK',
  'MASTERED',
  'REVIEW_REQUIRED',
]

describe('MASTERY_STATUS_BADGE —— 后端状态全覆盖，不多不少', () => {
  it('MasteryStatus 枚举每个值都有且仅有一个徽标定义', () => {
    expect(Object.keys(MASTERY_STATUS_BADGE).sort()).toEqual([...ALL_STATUSES].sort())
  })

  it('文案是全站唯一叫法（锁死，防止某页改成「需复习」）', () => {
    expect(MASTERY_STATUS_BADGE.NOT_STARTED.label).toBe('未开始')
    expect(MASTERY_STATUS_BADGE.LEARNING.label).toBe('学习中')
    expect(MASTERY_STATUS_BADGE.WEAK.label).toBe('薄弱')
    expect(MASTERY_STATUS_BADGE.MASTERED.label).toBe('已掌握')
    expect(MASTERY_STATUS_BADGE.REVIEW_REQUIRED.label).toBe('待复习')
  })

  it('语义色稳定：已掌握=success，薄弱=warning，待复习=review，学习中=learning', () => {
    expect(MASTERY_STATUS_BADGE.MASTERED.semantic).toBe('success')
    expect(MASTERY_STATUS_BADGE.WEAK.semantic).toBe('warning')
    expect(MASTERY_STATUS_BADGE.REVIEW_REQUIRED.semantic).toBe('review')
    expect(MASTERY_STATUS_BADGE.LEARNING.semantic).toBe('learning')
    expect(MASTERY_STATUS_BADGE.NOT_STARTED.semantic).toBe('neutral')
  })

  it('「待复习」与「薄弱」不是同色 —— 这是本次收敛的核心区分', () => {
    expect(MASTERY_STATUS_BADGE.REVIEW_REQUIRED.semantic).not.toBe(
      MASTERY_STATUS_BADGE.WEAK.semantic,
    )
  })
})

describe('badgeForMasteryStatus —— 未知值不抛错、不伪造', () => {
  it('null / undefined → 未知 + neutral', () => {
    expect(badgeForMasteryStatus(null)).toEqual({ label: '未知', semantic: 'neutral' })
    expect(badgeForMasteryStatus(undefined)).toEqual({ label: '未知', semantic: 'neutral' })
  })

  it('未知枚举值兜底为 未知（不返回 undefined / 不 throw）', () => {
    const spec = badgeForMasteryStatus('SOMETHING_NEW' as MasteryStatus)
    expect(spec).toEqual({ label: '未知', semantic: 'neutral' })
  })

  it('已知值走映射表', () => {
    expect(badgeForMasteryStatus('MASTERED')).toEqual(MASTERY_STATUS_BADGE.MASTERED)
  })
})

describe('专用徽标', () => {
  it('AI 生成固定为 ai 语义（暖橘高光）', () => {
    expect(badgeForAiGenerated()).toEqual({ label: 'AI 生成', semantic: 'ai' })
  })

  it('完成与否', () => {
    expect(badgeForCompleted(true)).toEqual({ label: '已完成', semantic: 'success' })
    expect(badgeForCompleted(false)).toEqual({ label: '未完成', semantic: 'neutral' })
  })

  it('到期与否 —— 待复习复用与 Mastery 相同的 review 色', () => {
    expect(badgeForDue(true).semantic).toBe(MASTERY_STATUS_BADGE.REVIEW_REQUIRED.semantic)
    expect(badgeForDue(true).label).toBe('待复习')
    expect(badgeForDue(false)).toEqual({ label: '未到期', semantic: 'neutral' })
  })

  it('计数徽标：0 不渲染成警示色（避免「待复习 0」看起来像出事）', () => {
    expect(badgeForCount(0, '待复习')).toEqual({ label: '待复习', semantic: 'neutral' })
    expect(badgeForCount(3, '待复习')).toEqual({ label: '待复习', semantic: 'review' })
  })
})
