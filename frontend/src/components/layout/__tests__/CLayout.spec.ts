// @vitest-environment happy-dom
/**
 * UI/UX 3.3.1 §8：Sidebar 折叠状态持久化
 *
 * 验证：
 *  - 收起 → F5 → 仍然收起
 *  - 展开 → F5 → 仍然展开
 *  - 状态只写 localStorage['csidebar-collapsed']，不污染 UserApp / 业务状态
 *  - 主内容区随折叠同步（is-collapsed 类 → padding-left 64px）
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia } from 'pinia'

const { routeState } = vi.hoisted(() => ({
  routeState: { path: '/', fullPath: '/', meta: {} as Record<string, unknown> },
}))

vi.mock('vue-router', () => ({
  useRoute: () => routeState,
  useRouter: () => ({ push: vi.fn() }),
}))
vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ isLoggedIn: true, isAdmin: false, user: { id: 1 } }),
}))
vi.mock('@/stores/background', () => ({ useBackgroundStore: () => ({ isActive: false }) }))
vi.mock('@/stores/apps', () => ({
  useAppStore: () => ({ userApps: {}, hydrate: vi.fn() }),
}))

import CLayout from '@/components/layout/CLayout.vue'
import CSidebar from '@/components/layout/CSidebar.vue'

const STORAGE_KEY = 'csidebar-collapsed'

function mountLayout() {
  return mount(CLayout, {
    global: {
      plugins: [createPinia()],
      stubs: {
        RouterLink: { template: '<a><slot /></a>' },
        // CTopNav 与折叠持久化无关，且依赖大量额外 store —— 直接 stub 掉
        CTopNav: true,
      },
    },
  })
}

describe('CLayout — sidebar collapse persistence (3.3.1 §8)', () => {
  beforeEach(() => {
    localStorage.clear()
    routeState.meta = {}
    routeState.path = '/'
    routeState.fullPath = '/'
  })

  it('restores collapsed=true after reload (F5)', async () => {
    localStorage.setItem(STORAGE_KEY, 'true')
    const wrapper = mountLayout()
    // onMounted 读取持久化值发生在首次渲染之后，父组件重渲染是异步的，需等一拍
    await flushPromises()
    expect(wrapper.find('.clayout-main').classes()).toContain('is-collapsed')
    expect(wrapper.findComponent(CSidebar).props('collapsed')).toBe(true)
  })

  it('restores collapsed=false after reload (F5)', async () => {
    localStorage.setItem(STORAGE_KEY, 'false')
    const wrapper = mountLayout()
    await flushPromises()
    expect(wrapper.find('.clayout-main').classes()).not.toContain('is-collapsed')
    expect(wrapper.findComponent(CSidebar).props('collapsed')).toBe(false)
  })

  it('defaults to expanded when no persisted state', async () => {
    const wrapper = mountLayout()
    await flushPromises()
    expect(wrapper.findComponent(CSidebar).props('collapsed')).toBe(false)
  })

  it('exposes stable PC shell regions for layout and browser verification', async () => {
    const wrapper = mountLayout()
    await flushPromises()
    expect(wrapper.find('[data-test="pc-shell"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="pc-sidebar"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="pc-main"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="pc-content"]').exists()).toBe(true)
  })

  it.each([false, true])('keeps a single content region for fullscreen=%s', async (fullscreen) => {
    routeState.meta = { fullscreen }
    const wrapper = mount(CLayout, {
      slots: { default: '<section data-test="page">内容</section>' },
      global: { plugins: [createPinia()], stubs: { CTopNav: true, RouterLink: { template: '<a><slot /></a>' } } },
    })
    await flushPromises()
    expect(wrapper.findAll('[data-test="pc-content"]')).toHaveLength(1)
    expect(wrapper.find('[data-test="pc-content"] [data-test="page"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('persists collapse on toggle, and main content follows', async () => {
    const wrapper = mountLayout()
    expect(wrapper.find('.clayout-main').classes()).not.toContain('is-collapsed')

    await wrapper.findComponent(CSidebar).vm.$emit('toggle-collapse')
    await flushPromises() // watch 异步刷新，等其落盘

    expect(wrapper.findComponent(CSidebar).props('collapsed')).toBe(true)
    expect(wrapper.find('.clayout-main').classes()).toContain('is-collapsed')
    expect(localStorage.getItem(STORAGE_KEY)).toBe('true')
  })

  it('persists expand on second toggle', async () => {
    localStorage.setItem(STORAGE_KEY, 'true')
    const wrapper = mountLayout()
    await flushPromises()
    // 确认 mount 后确实是收起态
    expect(wrapper.findComponent(CSidebar).props('collapsed')).toBe(true)

    await wrapper.findComponent(CSidebar).vm.$emit('toggle-collapse')
    await flushPromises()
    expect(localStorage.getItem(STORAGE_KEY)).toBe('false')
    expect(wrapper.find('.clayout-main').classes()).not.toContain('is-collapsed')
  })

  it('does not write sidebar state into UserApp / business stores', async () => {
    const wrapper = mountLayout()
    wrapper.findComponent(CSidebar).vm.$emit('toggle-collapse')
    await flushPromises()
    const keys = Object.keys(localStorage)
    expect(keys).toEqual([STORAGE_KEY])
  })

  // ===== 3.3.2 §24/§26：真实按钮点击的端到端折叠（非直接 emit）=====
  it('user click on the real collapse <button> collapses sidebar end-to-end (§2/§3/§16)', async () => {
    const wrapper = mountLayout()
    await flushPromises()
    expect(wrapper.find('.clayout-main').classes()).not.toContain('is-collapsed')

    const btn = wrapper.find('.csidebar-collapse')
    expect(btn.exists()).toBe(true)
    expect(btn.element.tagName).toBe('BUTTON')
    expect(btn.attributes('type')).toBe('button')

    await btn.trigger('click')
    await flushPromises()

    expect(wrapper.findComponent(CSidebar).props('collapsed')).toBe(true)
    expect(wrapper.find('.clayout-main').classes()).toContain('is-collapsed')
    expect(localStorage.getItem(STORAGE_KEY)).toBe('true')
  })

  it('second real click on the button re-expands the sidebar (§7/§10/§11)', async () => {
    const wrapper = mountLayout()
    await flushPromises()
    await wrapper.find('.csidebar-collapse').trigger('click')
    await flushPromises()
    expect(wrapper.find('.clayout-main').classes()).toContain('is-collapsed')

    await wrapper.find('.csidebar-collapse').trigger('click')
    await flushPromises()
    expect(wrapper.findComponent(CSidebar).props('collapsed')).toBe(false)
    expect(wrapper.find('.clayout-main').classes()).not.toContain('is-collapsed')
    expect(localStorage.getItem(STORAGE_KEY)).toBe('false')
  })
})

describe('UI V2 navigation keyboard contract', () => {
  it('offers a keyboard skip link to the focusable main region', async () => {
    const wrapper = mountLayout(); await flushPromises()
    expect(wrapper.find('a[href="#knowflow-main"]').text()).toBe('跳到主内容')
    expect(wrapper.find('main#knowflow-main').attributes('tabindex')).toBe('-1'); wrapper.unmount()
  })
  it('keeps offscreen mobile navigation inert until opened and closes with Escape', async () => {
    const media = vi.spyOn(window, 'matchMedia').mockReturnValue({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() } as unknown as MediaQueryList)
    const wrapper = mountLayout(); await flushPromises()
    expect(wrapper.findComponent(CSidebar).attributes('inert')).toBeDefined()
    wrapper.findComponent({ name: 'CTopNav' }).vm.$emit('toggle-sidebar'); await flushPromises()
    expect(wrapper.findComponent(CSidebar).attributes('inert')).toBeUndefined()
    expect(wrapper.findComponent(CSidebar).attributes('aria-modal')).toBe('true')
    await wrapper.trigger('keydown', { key: 'Escape' }); await flushPromises()
    expect(wrapper.findComponent(CSidebar).attributes('inert')).toBeDefined()
    wrapper.unmount(); media.mockRestore()
  })
})
