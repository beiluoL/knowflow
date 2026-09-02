// @vitest-environment happy-dom
/**
 * UI/UX 2.1：MiniAppShell 统一外壳单测（§178）
 * 覆盖：标题 / 副标题条件渲染 / 上下文条件渲染 / 动作 slot / 返回事件 / 清除上下文 / 默认内容 slot。
 */
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import MiniAppShell from '@/components/layout/MiniAppShell.vue'

describe('MiniAppShell（UI/UX 2.1 §178）', () => {
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

  it('emits back on back button click', async () => {
    const wrapper = mount(MiniAppShell, { props: { title: 'T' } })
    await wrapper.find('.mini-app-back').trigger('click')
    expect(wrapper.emitted('back')).toBeTruthy()
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
