// @vitest-environment happy-dom
/**
 * UI/UX 2.1 + 3.3：MiniAppShell 统一外壳单测
 * 覆盖：标题 / 副标题 / 上下文 / 动作 slot / 返回（统一走 usePageNavigation）/ 清除上下文 / 默认内容 slot。
 */
import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import MiniAppShell from '@/components/layout/MiniAppShell.vue'

// UI/UX 3.3：返回逻辑统一收口到 usePageNavigation，MiniAppShell 不再 emit 'back'
const navMock = vi.hoisted(() => ({ back: { value: null }, crumbs: { value: [] }, goBack: vi.fn() }))
vi.mock('@/composables/usePageNavigation', () => ({ usePageNavigation: () => navMock }))

describe('MiniAppShell（UI/UX 2.1 §178 / 3.3）', () => {
  it('renders title', () => {
    const wrapper = mount(MiniAppShell, { props: { title: '闪卡大厅' } })
    expect(wrapper.find('.mini-app-title').text()).toBe('闪卡大厅')
  })

  it('renders subtitle when provided', () => {
    const wrapper = mount(MiniAppShell, { props: { title: 'T', subtitle: '副标题' } })
    expect(wrapper.find('.mini-app-subtitle').text()).toBe('副标题')
  })

  it('hides subtitle when not provided', () => {
    const wrapper = mount(MiniAppShell, { props: { title: 'T' } })
    expect(wrapper.find('.mini-app-subtitle').exists()).toBe(false)
  })

  it('renders context only when knowledgeName provided', () => {
    const empty = mount(MiniAppShell, { props: { title: 'T' } })
    expect(empty.find('.mini-app-context').exists()).toBe(false)

    const withCtx = mount(MiniAppShell, { props: { title: 'T', knowledgeName: 'AQS' } })
    expect(withCtx.find('.mini-app-context').exists()).toBe(true)
    expect(withCtx.find('.mini-app-context').text()).toContain('AQS')
  })

  it('renders actions slot', () => {
    const wrapper = mount(MiniAppShell, {
      props: { title: 'T' },
      slots: { actions: '<button class="my-action">Go</button>' },
    })
    expect(wrapper.find('.my-action').exists()).toBe(true)
  })

  it('calls usePageNavigation.goBack on back button click (no longer emits back)', async () => {
    const wrapper = mount(MiniAppShell, { props: { title: 'T' } })
    await wrapper.find('.mini-app-back').trigger('click')
    expect(wrapper.emitted('back')).toBeFalsy()
    expect(navMock.goBack).toHaveBeenCalledOnce()
  })

  it('emits clear-context when clearable and context present', async () => {
    const wrapper = mount(MiniAppShell, {
      props: { title: 'T', knowledgeName: 'AQS', clearableContext: true },
    })
    await wrapper.find('.mini-app-context-clear').trigger('click')
    expect(wrapper.emitted('clear-context')).toBeTruthy()
  })

  it('renders default slot content', () => {
    const wrapper = mount(MiniAppShell, {
      props: { title: 'T' },
      slots: { default: '<p class="body-content">hello</p>' },
    })
    expect(wrapper.find('.body-content').exists()).toBe(true)
  })
})
