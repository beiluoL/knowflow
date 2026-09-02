// @vitest-environment happy-dom
/**
 * UI/UX 2.2 §216：共享状态组件单测
 * 覆盖 EmptyState / ErrorState / StateView（loading/error/empty/ready 四态）。
 */
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import EmptyState from '@/components/ui/EmptyState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import StateView from '@/components/ui/StateView.vue'

describe('EmptyState（§25-27 空态三要素）', () => {
  it('renders title', () => {
    const w = mount(EmptyState, { props: { title: '今天没有待复习内容' } })
    expect(w.text()).toContain('今天没有待复习内容')
  })

  it('renders description', () => {
    const w = mount(EmptyState, { props: { title: 'T', description: '可以继续学习新的知识' } })
    expect(w.text()).toContain('可以继续学习新的知识')
  })

  it('renders #action slot (CTA ≤1, §26)', () => {
    const w = mount(EmptyState, {
      props: { title: 'T' },
      slots: { action: '<button class="cta">去学习</button>' },
    })
    expect(w.find('.cta').exists()).toBe(true)
  })
})

describe('ErrorState（§29-30 错误 + 重试）', () => {
  it('renders default title 加载失败 when not provided', () => {
    const w = mount(ErrorState)
    expect(w.find('.kb-state-title').text()).toBe('加载失败')
  })

  it('renders provided title', () => {
    const w = mount(ErrorState, { props: { title: '出了一点问题' } })
    expect(w.find('.kb-state-title').text()).toBe('出了一点问题')
  })

  it('shows retry button by default and emits retry', async () => {
    const w = mount(ErrorState)
    const btn = w.find('.kb-state-retry')
    expect(btn.exists()).toBe(true)
    await btn.trigger('click')
    expect(w.emitted('retry')).toBeTruthy()
  })

  it('hides retry when showRetry=false (§30 不暴露内部错误)', () => {
    const w = mount(ErrorState, { props: { showRetry: false } })
    expect(w.find('.kb-state-retry').exists()).toBe(false)
  })
})

describe('StateView 四态容器（§18 统一 Loading/Empty/Error）', () => {
  it('loading → 渲染骨架屏 (animate-pulse)', () => {
    const w = mount(StateView, { props: { status: 'loading' } })
    expect(w.find('.animate-pulse').exists()).toBe(true)
  })

  it('error → 渲染 ErrorState + 重试', () => {
    const w = mount(StateView, { props: { status: 'error' } })
    expect(w.find('.kb-state-error').exists()).toBe(true)
  })

  it('empty → 渲染 EmptyState（默认标题 暂无数据）', () => {
    const w = mount(StateView, { props: { status: 'empty' } })
    expect(w.text()).toContain('暂无数据')
  })

  it('ready → 渲染默认插槽内容', () => {
    const w = mount(StateView, {
      props: { status: 'ready' },
      slots: { default: '<p class="real-content">真实内容</p>' },
    })
    expect(w.find('.real-content').exists()).toBe(true)
  })
})
