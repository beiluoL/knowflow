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

/* ────────────────────────────────────────────────────────────────────────
 * Interaction OS（UI/UX 5.1）§三/§四/§五/§六/§二十一 增补
 * ──────────────────────────────────────────────────────────────────────── */

describe('StateView §四 —— 骨架必须“像内容”，不是中间一个转圈', () => {
  it('提供 #skeleton 插槽时优先渲染页面自己的内容形状骨架', () => {
    const w = mount(StateView, {
      props: { status: 'loading' },
      slots: { skeleton: '<div class="card-shaped-skeleton"></div>' },
    })
    expect(w.find('.card-shaped-skeleton').exists()).toBe(true)
  })

  it('未提供 #skeleton 时回落 SkeletonList（向后兼容）', () => {
    const w = mount(StateView, { props: { status: 'loading' } })
    expect(w.find('.animate-pulse').exists()).toBe(true)
  })

  it('ready 态不渲染骨架（禁止加载态泄漏到真实内容）', () => {
    const w = mount(StateView, {
      props: { status: 'ready' },
      slots: { default: '<p>x</p>', skeleton: '<div class="never"></div>' },
    })
    expect(w.find('.never').exists()).toBe(false)
  })
})

describe('ErrorState §六 —— 三段式：发生了什么 / 影响范围 / 怎么解决', () => {
  it('scope 与 hint 都会渲染出来', () => {
    const w = mount(ErrorState, {
      props: { title: '服务暂时不可用', scope: '这个页面的内容暂时无法加载。', hint: '稍后重试。' },
    })
    expect(w.find('.kb-state-scope').text()).toContain('这个页面的内容暂时无法加载。')
    expect(w.find('.kb-state-hint').text()).toContain('稍后重试。')
  })

  it('缺省 scope / hint 时不渲染空行（向后兼容既有调用方）', () => {
    const w = mount(ErrorState, { props: { title: 'X' } })
    expect(w.find('.kb-state-scope').exists()).toBe(false)
    expect(w.find('.kb-state-hint').exists()).toBe(false)
  })

  it('§二十一：错误是断言型状态 → role=alert + aria-live=assertive', () => {
    const w = mount(ErrorState)
    const root = w.find('.kb-state-error')
    expect(root.attributes('role')).toBe('alert')
    expect(root.attributes('aria-live')).toBe('assertive')
  })

  it('StateView.errorScope / errorHint 透传到 ErrorState', () => {
    const w = mount(StateView, {
      props: { status: 'error', errorScope: '只坏了这一块', errorHint: '请重试' },
    })
    expect(w.find('.kb-state-scope').text()).toContain('只坏了这一块')
    expect(w.find('.kb-state-hint').text()).toContain('请重试')
  })
})

describe('EmptyState §五/§二十一 —— 空态是“陈述”，语义比错误更弱', () => {
  it('role=status + aria-live=polite（与 ErrorState 的 alert 明确区分）', () => {
    const w = mount(EmptyState, { props: { title: 'T' } })
    const root = w.find('[role="status"]')
    expect(root.exists()).toBe(true)
    expect(root.attributes('aria-live')).toBe('polite')
  })

  it('§十四：空态行动按钮 ≤ 1 个 Primary（由调用方传入，组件不自行加第二个）', () => {
    const w = mount(EmptyState, {
      props: { title: 'T' },
      slots: { action: '<button class="only-cta">去添加</button>' },
    })
    expect(w.findAll('.only-cta').length).toBe(1)
  })
})

describe('StateView §六 —— 状态互斥（不允许同时出现两个状态）', () => {
  it('error 态不会同时渲染 empty 或 ready 内容', () => {
    const w = mount(StateView, {
      props: { status: 'error' },
      slots: { default: '<p class="real-content">真实内容</p>' },
    })
    expect(w.find('.real-content').exists()).toBe(false)
    expect(w.find('.kb-state-error').exists()).toBe(true)
  })

  it('empty 态不会同时渲染 error 或 ready 内容', () => {
    const w = mount(StateView, {
      props: { status: 'empty' },
      slots: { default: '<p class="real-content">真实内容</p>' },
    })
    expect(w.find('.real-content').exists()).toBe(false)
    expect(w.find('.kb-state-error').exists()).toBe(false)
  })
})
