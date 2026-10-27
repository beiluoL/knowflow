// @vitest-environment happy-dom
import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import StudioVisual from '../StudioVisual.vue'
import StatCard from '../StatCard.vue'

describe('studio presentation resilience', () => {
  it('hides failed decorative assets instead of showing a broken image', async () => {
    const wrapper = mount(StudioVisual)
    expect(wrapper.get('img').attributes('alt')).toBe('')
    expect(wrapper.get('img').attributes('aria-hidden')).toBe('true')
    await wrapper.get('img').trigger('error')
    expect(wrapper.find('img').exists()).toBe(false)
  })

  it('renders a statistic icon without unresolved component warnings', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const wrapper = mount(StatCard, { props: { icon: 'clock', value: 5, label: '学习时长' } })
    expect(wrapper.text()).toContain('学习时长')
    expect(warn.mock.calls.some(args => args.join(' ').includes('Failed to resolve component: Icon'))).toBe(false)
    wrapper.unmount()
    warn.mockRestore()
  })
})
