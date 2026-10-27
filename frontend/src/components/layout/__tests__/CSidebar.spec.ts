// @vitest-environment happy-dom
/**
 * UI/UX 3.3 / 3.3.1：CSidebar 单测
 *
 * 覆盖（3.3.1 §42）：
 *  - collapse / expand：折叠态渲染图标 rail（tooltip = 全称），展开态显示文字
 *  - 折叠态点击动态 App / 分组标题真实可导航（§9，修「死按钮」）
 *  - 折叠开关可点击（emit toggle-collapse）
 *  - 当前工作区 / 当前项高亮（active + 主色指示条，§12）
 *  - enabled App 动态出现，未启用 App 不出现
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'

const { routeState, routerMock } = vi.hoisted(() => ({
  routeState: { path: '/learning/center' },
  routerMock: { push: vi.fn() },
}))

vi.mock('vue-router', () => ({
  useRoute: () => routeState,
  useRouter: () => routerMock,
}))
vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ isLoggedIn: true, isAdmin: false, user: { id: 1 } }),
}))
vi.mock('@/stores/background', () => ({ useBackgroundStore: () => ({ isActive: false }) }))
vi.mock('@/stores/apps', () => ({
  useAppStore: () => ({ userApps: { flashcard: { installed: true, enabled: true } } }),
}))

import CSidebar from '@/components/layout/CSidebar.vue'

const RouterLinkStub = { template: '<a class="router-link"><slot /></a>' }

function mountSidebar(collapsed: boolean) {
  return mount(CSidebar, {
    props: { collapsed },
    global: { stubs: { RouterLink: RouterLinkStub } },
  })
}

describe('CSidebar (UI/UX 3.3 / 3.3.1)', () => {
  beforeEach(() => {
    routeState.path = '/learning/center'
    routerMock.push.mockClear()
    localStorage.clear()
  })

  it('collapsed: renders dynamic App as icon rail with tooltip = label', () => {
    const wrapper = mountSidebar(true)
    const rail = wrapper.findAll('.csidebar-subrail-item')
    const flashcard = rail.find((el) => el.attributes('title') === '闪卡')
    expect(flashcard?.exists()).toBe(true)
    expect(flashcard?.attributes('aria-label')).toBe('闪卡')
  })

  it('expanded: shows App label text (not icon-only rail)', () => {
    const wrapper = mountSidebar(false)
    expect(wrapper.find('.csidebar-subrail-item').exists()).toBe(false)
    const labels = wrapper.findAll('.csidebar-child').map((el) => el.text())
    expect(labels).toContain('学习首页')
    expect(labels).toContain('闪卡')
  })

  it('marks active workspace group', () => {
    const wrapper = mountSidebar(false)
    expect(wrapper.find('.csidebar-top.is-parent-active').exists()).toBe(true)
  })

  it('identifies the resolver-backed primary navigation region', () => {
    const wrapper = mountSidebar(false)
    expect(wrapper.find('[data-test="pc-primary-navigation"]').attributes('aria-label')).toBe('主导航')
  })

  it('disabled App does not appear in navigation', () => {
    const wrapper = mountSidebar(false)
    const labels = wrapper.findAll('.csidebar-child').map((el) => el.text())
    expect(labels).not.toContain('费曼')
  })

  // ===== 3.3.1 新增 =====

  it('collapsed: clicking a group header navigates to its workspace home (no dead button, §9)', async () => {
    const wrapper = mountSidebar(true)
    // 「学习」分组标题（折叠态下是 button）
    const groupBtn = wrapper
      .findAll('.csidebar-top')
      .find((el) => el.element.tagName === 'BUTTON' && el.attributes('aria-label') === '学习')
    expect(groupBtn?.exists()).toBe(true)
    await groupBtn!.trigger('click')
    expect(routerMock.push).toHaveBeenCalledWith('/learning/center')
  })

  it('expanded: clicking a group header toggles children instead of navigating', async () => {
    const wrapper = mountSidebar(false)
    const groupBtn = wrapper
      .findAll('.csidebar-top')
      .find((el) => el.attributes('aria-label') === '学习')
    expect(groupBtn?.exists()).toBe(true)
    await groupBtn!.trigger('click')
    expect(routerMock.push).not.toHaveBeenCalled()
  })

  it('collapsed: clicking a dynamic App in the rail emits navigate (closes mobile drawer)', async () => {
    const wrapper = mountSidebar(true)
    const rail = wrapper.findAll('.csidebar-subrail-item')
    const flashcard = rail.find((el) => el.attributes('title') === '闪卡')
    await flashcard!.trigger('click')
    expect(wrapper.emitted('navigate')).toBeTruthy()
  })

  it('collapse toggle button is present and emits toggle-collapse', async () => {
    const wrapper = mountSidebar(false)
    const btn = wrapper.find('.csidebar-collapse')
    expect(btn.exists()).toBe(true)
    expect(btn.attributes('aria-label')).toBe('收起侧边栏')
    await btn.trigger('click')
    expect(wrapper.emitted('toggle-collapse')).toHaveLength(1)
  })

  it('collapsed toggle button label switches to "展开侧边栏"', () => {
    const wrapper = mountSidebar(true)
    const btn = wrapper.find('.csidebar-collapse')
    expect(btn.attributes('aria-label')).toBe('展开侧边栏')
    expect(btn.attributes('title')).toBe('展开侧边栏')
  })

  it('active child gets is-active class (drives the primary accent bar, §12)', () => {
    routeState.path = '/learning/flashcards'
    const wrapper = mountSidebar(false)
    const active = wrapper.findAll('.csidebar-child.is-active')
    expect(active.length).toBeGreaterThan(0)
    expect(active[0].text()).toContain('闪卡')
  })

  it('top-level direct item (首页) is active only on exact /', () => {
    routeState.path = '/learning/center'
    const wrapper = mountSidebar(false)
    const home = wrapper.findAll('.csidebar-top').find((el) => el.attributes('aria-label') === '首页')
    expect(home?.classes()).not.toContain('is-active')

    routeState.path = '/'
    const wrapper2 = mountSidebar(false)
    const home2 = wrapper2.findAll('.csidebar-top').find((el) => el.attributes('aria-label') === '首页')
    expect(home2?.classes()).toContain('is-active')
  })

  // ===== 3.3.2 §24 验收清单补充断言 =====

  it('collapsed: 所有文字标签隐藏，仅保留图标（§8 文字消失 / icon 保留）', () => {
    const wrapper = mountSidebar(true)
    expect(wrapper.findAll('.csidebar-label').length).toBe(0)
    expect(wrapper.findAll('.csidebar-icon').length).toBeGreaterThan(0)
  })

  it('collapsed: 当前 active 子项在图标 rail 中保留 is-active + tooltip（§9/§15）', () => {
    routeState.path = '/learning/flashcards'
    const wrapper = mountSidebar(true)
    const rail = wrapper.findAll('.csidebar-subrail-item')
    const active = rail.find((el) => el.classes().includes('is-active'))
    expect(active?.exists()).toBe(true)
    expect(active?.attributes('title')).toBe('闪卡')
    expect(active?.attributes('aria-label')).toBe('闪卡')
  })

  it('collapsed: 一级导航项暴露 title tooltip（图标-only 模式下可识别，§14）', () => {
    const wrapper = mountSidebar(true)
    const home = wrapper
      .findAll('.csidebar-top')
      .find((el) => el.attributes('aria-label') === '首页')
    expect(home?.attributes('title')).toBe('首页')
  })

  it('expanded: 一级分组标题带 chevron 且可点击切换（非死按钮，§25）', async () => {
    const wrapper = mountSidebar(false)
    const groupBtn = wrapper
      .findAll('.csidebar-top')
      .find((el) => el.element.tagName === 'BUTTON' && el.attributes('aria-label') === '学习')
    expect(groupBtn?.exists()).toBe(true)
    expect(groupBtn?.find('.csidebar-chevron').exists()).toBe(true)
    await groupBtn!.trigger('click')
    // 展开态点击分组标题 = 切换子项展开/收起（路由 /learning/center 落在「学习」分组内，
    // 默认自动展开，故点击后应收起，子项列表消失，证明点击真实生效而非死按钮）
    expect(wrapper.findAll('.csidebar-sub').length).toBe(0)
    expect(routerMock.push).not.toHaveBeenCalled()
  })
})
