// @vitest-environment happy-dom
/**
 * UI/UX 3.3：usePageNavigation 单测
 * 覆盖返回优先级（真实 history → 父级兜底 → Home）、App 根 / 深层页 / 工作区首页的 back 目标、
 * 统一面包屑、以及 direct-URL / 刷新（history.state.back 为空）场景。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'

const { routerMock, routeState } = vi.hoisted(() => ({
  routerMock: { back: vi.fn(), push: vi.fn() },
  routeState: { path: '/' as string },
}))

vi.mock('vue-router', () => ({
  useRoute: () => routeState,
  useRouter: () => routerMock,
}))

import { usePageNavigation } from '@/composables/usePageNavigation'

describe('usePageNavigation (UI/UX 3.3)', () => {
  beforeEach(() => {
    routeState.path = '/'
    window.history.replaceState({}, '') // 清空 history.state.back（模拟 direct URL / 刷新）
    routerMock.back.mockClear()
    routerMock.push.mockClear()
  })

  it('workspace home (学习首页) has no back target', () => {
    routeState.path = '/learning/center'
    const { back } = usePageNavigation()
    expect(back.value).toBeNull()
  })

  it('App root page returns to workspace home with group label', () => {
    routeState.path = '/learning/flashcards' // 闪卡 App route
    const { back } = usePageNavigation()
    expect(back.value).toEqual({ label: '学习', to: '/learning/center' })
  })

  it('deep App page returns to App root', () => {
    routeState.path = '/learning/flashcards/123'
    const { back } = usePageNavigation()
    expect(back.value).toEqual({ label: '闪卡', to: '/learning/flashcards' })
  })

  it('knowledge App root returns to 知识 workspace home', () => {
    routeState.path = '/learning/knowledge-graph'
    const { back } = usePageNavigation()
    expect(back.value).toEqual({ label: '知识', to: '/knowledge' })
  })

  it('builds breadcrumbs [workspace] → [app]', () => {
    routeState.path = '/learning/flashcards'
    const { crumbs } = usePageNavigation()
    expect(crumbs.value).toEqual([
      { label: '学习', to: '/learning/center' },
      { label: '闪卡', to: '/learning/flashcards' },
    ])
  })

  it('goBack prefers real history when available', () => {
    routeState.path = '/learning/flashcards'
    window.history.replaceState({ back: '/learning/center', forward: null, position: 1 }, '')
    const { goBack } = usePageNavigation()
    goBack()
    expect(routerMock.back).toHaveBeenCalledOnce()
    expect(routerMock.push).not.toHaveBeenCalled()
  })

  it('goBack falls back to parent on direct URL / refresh (no history.back)', () => {
    routeState.path = '/learning/flashcards'
    window.history.replaceState({}, '') // 直接输入 URL / 刷新 / 书签进入
    const { goBack } = usePageNavigation()
    goBack()
    expect(routerMock.back).not.toHaveBeenCalled()
    expect(routerMock.push).toHaveBeenCalledWith('/learning/center')
  })

  it('deep App page fallback returns to App root', () => {
    routeState.path = '/learning/flashcards/123'
    window.history.replaceState({}, '')
    const { goBack } = usePageNavigation()
    goBack()
    expect(routerMock.push).toHaveBeenCalledWith('/learning/flashcards')
  })

  it('goToWorkspace navigates to workspace home', () => {
    routeState.path = '/learning/flashcards'
    const { goToWorkspace } = usePageNavigation()
    goToWorkspace()
    expect(routerMock.push).toHaveBeenCalledWith('/learning/center')
  })
})
