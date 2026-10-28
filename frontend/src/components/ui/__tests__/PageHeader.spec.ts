// @vitest-environment happy-dom
import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import PageHeader from '@/components/ui/PageHeader.vue'

vi.mock('@/composables/usePageNavigation', () => ({
  usePageNavigation: () => ({ back: { value: { label: '学习', to: '/learning' } }, goBack: vi.fn() }),
}))

describe('PageHeader shared header without back button', () => {
  it('keeps title, description, counts, breadcrumbs and actions without rendering legacy back controls', () => {
    const wrapper = mount(PageHeader, {
      props: {
        title: '学习分析', description: '查看学习进展', count: 3,
        back: true, backLabel: '返回',
        crumbs: [{ label: '学习', to: '/learning' }, { label: '学习分析' }],
      },
      slots: { actions: '<button class="refresh-action">刷新</button>' },
      global: { stubs: { RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' } } },
    })
    expect(wrapper.find('.kf-page-back').exists()).toBe(false)
    expect(wrapper.find('button[aria-label^="返回"]').exists()).toBe(false)
    expect(wrapper.get('h1').text()).toBe('学习分析')
    expect(wrapper.text()).toContain('查看学习进展')
    expect(wrapper.text()).toContain('3 项')
    expect(wrapper.get('nav a').attributes('href')).toBe('/learning')
    expect(wrapper.get('[aria-current="page"]').text()).toBe('学习分析')
    expect(wrapper.get('.refresh-action').text()).toBe('刷新')
  })
})
