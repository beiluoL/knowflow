// @vitest-environment happy-dom
/**
 * UI/UX 3.0 §88-91：Navigation Resolver 单测
 * 验证：默认状态 / enabled / disabled / installed / navVisible / category 映射 / sortOrder / 用户隔离（guest）。
 */
import { describe, it, expect } from 'vitest'
import {
  resolveNavigation,
  resolveAppNavChildren,
  canShowInNavigation,
} from '@/apps/navigation'
import { getAppByKey, DEFAULT_APP_KEYS } from '@/apps/registry'
import type { UserAppState } from '@/apps/types'

function state(installed: boolean, enabled: boolean, sortOrder = 0): UserAppState {
  return { installed, enabled, sortOrder }
}

/** 由默认安装 key 构造「全部默认启用」的用户态（§14：以现有 defaultInstalled 为准） */
function defaultUserApps(): Record<string, UserAppState> {
  const rec: Record<string, UserAppState> = {}
  for (const key of DEFAULT_APP_KEYS) rec[key] = state(true, true, 0)
  return rec
}

describe('canShowInNavigation（§41 可见性）', () => {
  const flashcard = getAppByKey('flashcard')!

  it('navVisible + installed + enabled → 可见', () => {
    expect(canShowInNavigation(flashcard, state(true, true))).toBe(true)
  })

  it('enabled=false → 不可见', () => {
    expect(canShowInNavigation(flashcard, state(true, false))).toBe(false)
  })

  it('installed=false → 不可见', () => {
    expect(canShowInNavigation(flashcard, state(false, true))).toBe(false)
  })

  it('无用户态 → 不可见', () => {
    expect(canShowInNavigation(flashcard, undefined)).toBe(false)
  })

  it('focus(navVisible=false) → 永不进动态子菜单（§13）', () => {
    const focus = getAppByKey('focus')!
    expect(focus.navVisible).toBe(false)
    expect(canShowInNavigation(focus, state(true, true))).toBe(false)
  })
})

describe('resolveAppNavChildren（§9/§10/§90 分区）', () => {
  it('learning App 进入「学习」分区', () => {
    const buckets = resolveAppNavChildren({ flashcard: state(true, true) })
    expect(buckets.learning.some((c) => c.path === '/learning/flashcards')).toBe(true)
  })

  it('knowledge App 进入「知识」分区', () => {
    const buckets = resolveAppNavChildren({ 'knowledge-graph': state(true, true) })
    expect(buckets.knowledge.some((c) => c.path === '/learning/knowledge-graph')).toBe(true)
  })

  it('ai App 进入「AI」分区', () => {
    const buckets = resolveAppNavChildren({ 'ai-writing': state(true, true) })
    expect(buckets.ai.some((c) => c.path === '/learning/writing')).toBe(true)
  })

  it('disabled App 不出现在任何分区', () => {
    const buckets = resolveAppNavChildren({ flashcard: state(true, false) })
    expect(buckets.learning).toHaveLength(0)
  })
})

describe('resolveNavigation（§7 唯一解析入口）', () => {
  it('一级导航固定 5 个：首页/学习/知识/AI/应用', () => {
    const groups = resolveNavigation(true, false, {})
    expect(groups.map((g) => g.label)).toEqual(['首页', '学习', '知识', 'AI', '应用'])
  })

  it('默认状态下：学习含闪卡/练习/错题；番茄钟不占 Sidebar（3.2 §8/§16）', () => {
    const groups = resolveNavigation(true, false, defaultUserApps())
    const learning = groups.find((g) => g.label === '学习')!
    expect(learning.children!.some((c) => c.path === '/learning/flashcards')).toBe(true)
    expect(learning.children!.some((c) => c.path === '/learning/quiz')).toBe(true)
    expect(learning.children!.some((c) => c.path === '/mistakes')).toBe(true)
    // 3.2：番茄钟/专注模式不再是 Sidebar 固定子项（§8），focus App 也不进动态菜单（§13/§74），
    // 因此导航里不应出现 /learning/pomodoro —— 入口改由顶栏番茄钟 + 学习首页卡片承载（§105-107）
    expect(learning.children!.filter((c) => c.path === '/learning/pomodoro')).toHaveLength(0)
  })

  it('未登录：不注入任何 App 子菜单（§46 用户隔离）', () => {
    const groups = resolveNavigation(false, false, { flashcard: state(true, true) })
    const learning = groups.find((g) => g.label === '学习')!
    expect(learning.children!.some((c) => c.path === '/learning/flashcards')).toBe(false)
  })

  it('启用 Feynman → 学习菜单出现；停用 → 消失（§15/§76/§77）', () => {
    const withF = resolveNavigation(true, false, { feynman: state(true, true) })
    expect(withF.find((g) => g.label === '学习')!.children!.some((c) => c.path === '/workbench/story')).toBe(true)

    const withoutF = resolveNavigation(true, false, { feynman: state(true, false) })
    expect(withoutF.find((g) => g.label === '学习')!.children!.some((c) => c.path === '/workbench/story')).toBe(false)
  })

  it('启用 Knowledge Graph → 知识菜单出现（§78）', () => {
    const groups = resolveNavigation(true, false, { 'knowledge-graph': state(true, true) })
    expect(groups.find((g) => g.label === '知识')!.children!.some((c) => c.path === '/learning/knowledge-graph')).toBe(true)
  })

  it('sortOrder 覆盖 registry.order（§18/§91）', () => {
    // mistake(order=3) 设 sortOrder=0 → 应排在 quiz(order=2, sortOrder=0) 之前
    const groups = resolveNavigation(true, false, {
      quiz: state(true, true, 5),
      mistake: state(true, true, 0),
    })
    const learning = groups.find((g) => g.label === '学习')!
    const paths = learning.children!.filter((c) => ['/learning/quiz', '/mistakes'].includes(c.path)).map((c) => c.path)
    // 动态 App 子菜单固定在固定工作区入口之后；mistake(sortOrder=0) 应在 quiz(sortOrder=5) 前
    // 动态 App 子菜单固定在固定工作区入口之后；mistake 应在 quiz 前
    const mi = paths.indexOf('/mistakes')
    const qi = paths.indexOf('/learning/quiz')
    expect(mi).toBeGreaterThanOrEqual(0)
    expect(mi).toBeLessThan(qi)
  })
})
