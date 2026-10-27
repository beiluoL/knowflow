// @vitest-environment happy-dom
/**
 * Interaction OS（UI/UX 5.1）§七 / §八：NextAction 点击契约测试
 *
 * 这是本阶段最核心的一条回归：**点击「下一步」后，用户不需要重新挑一次知识点**。
 *
 * 旧实现的缺陷（真实存在、已修）：
 *   doAction() → router.push(resolveActionRoute(action)) → 裸路由
 *   首页写着「用闪卡巩固『JVM G1 GC』」，点进去却是通用闪卡页。
 *
 * 断言口径：只看 **router.push 收到的最终参数**（用户的真实体验），
 * 不断言内部函数调用，避免测试与实现同构。
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { reactive } from 'vue'

const pushMock = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: pushMock, back: vi.fn() }),
  useRoute: () => reactive({ path: '/', query: {} }),
}))

// —— 学习 store：提供全站唯一的 nextActionContext（本组用例会改写它）——
const learningMock = vi.hoisted(() => ({
  state: {
    nextActionContext: {} as Record<string, unknown>,
  },
}))
vi.mock('@/stores/learning', () => ({
  useLearningStore: () => learningMock.state,
}))

// —— 应用 store：默认「已安装 + 已启用」——
const appStoreMock = vi.hoisted(() => ({
  stateOf: vi.fn(() => ({ installed: true, enabled: true })),
  install: vi.fn(),
  enable: vi.fn(),
}))
vi.mock('@/stores/apps', () => ({
  useAppStore: () => appStoreMock,
}))

vi.mock('@/stores/wallet', () => ({
  useWalletStore: () => ({ wallet: { coin: 100 }, fetchWallet: vi.fn() }),
}))

vi.mock('@/api/store', () => ({
  storeApi: { products: vi.fn().mockResolvedValue([]) },
}))

import NextActionCard from '@/components/workflow/NextActionCard.vue'
import type { NextLearningAction } from '@/learning/types'
import type { LearningEntryContext } from '@/learning/action-contract'

function makeAction(partial: Partial<NextLearningAction> & Pick<NextLearningAction, 'type'>) {
  return { title: '用闪卡巩固「JVM G1 GC」', reason: '你最近在这个知识点上错得较多', route: '', ...partial } as NextLearningAction
}

const CTX: LearningEntryContext = {
  knowledgeId: 42,
  knowledgeName: 'JVM G1 GC',
  goalId: 7,
  learningNodeId: 301,
}

/** 取出唯一一次 push 的参数，归一化成字符串便于断言 */
function pushedUrl(): string {
  expect(pushMock).toHaveBeenCalledTimes(1)
  const arg = pushMock.mock.calls[0][0]
  if (typeof arg === 'string') return arg
  const q = new URLSearchParams(arg.query ?? {}).toString()
  return q ? `${arg.path}?${q}` : arg.path
}

/** 解析出 push 的目标路径与 query（避免手写 URL 编码断言） */
function pushedTarget(): { path: string; query: Record<string, string> } {
  expect(pushMock).toHaveBeenCalledTimes(1)
  const arg = pushMock.mock.calls[0][0]
  if (typeof arg === 'string') {
    const [path, qs] = arg.split('?')
    return { path, query: Object.fromEntries(new URLSearchParams(qs ?? '')) }
  }
  return { path: arg.path, query: { ...(arg.query ?? {}) } }
}

beforeEach(() => {
  pushMock.mockClear()
  learningMock.state.nextActionContext = {}
  appStoreMock.stateOf.mockReturnValue({ installed: true, enabled: true })
})

describe('NextActionCard · §八 点击必须携带学习上下文', () => {
  it('FLASHCARD：把 knowledgeId / knowledgeName / chapterId 一起带过去', async () => {
    const w = mount(NextActionCard, {
      props: { action: makeAction({ type: 'FLASHCARD' }), context: CTX },
    })
    await w.find('button').trigger('click')
    await flushPromises()

    const url = pushedUrl()
    expect(url.startsWith('/learning/flashcards?')).toBe(true)
    expect(pushedTarget().query).toMatchObject({
      knowledgeId: '42',
      knowledgeName: 'JVM G1 GC',
      chapterId: '301',
    })
  })

  it('QUIZ：带 knowledgeId，但不塞该页不消费的 chapterId', async () => {
    const w = mount(NextActionCard, {
      props: { action: makeAction({ type: 'QUIZ' }), context: CTX },
    })
    await w.find('button').trigger('click')
    await flushPromises()

    expect(pushedUrl()).toContain('knowledgeId=42')
    expect(pushedUrl()).not.toContain('chapterId')
  })

  it('未传 context 时回落到学习 store 的 nextActionContext（页面无需自己拼）', async () => {
    learningMock.state.nextActionContext = { ...CTX }
    const w = mount(NextActionCard, { props: { action: makeAction({ type: 'REVIEW' }) } })
    await w.find('button').trigger('click')
    await flushPromises()

    expect(pushedUrl()).toContain('knowledgeId=42')
  })

  it('上下文为空时退回干净裸路由（不产生孤立的 `?`）', async () => {
    const w = mount(NextActionCard, { props: { action: makeAction({ type: 'FLASHCARD' }) } })
    await w.find('button').trigger('click')
    await flushPromises()

    expect(pushedUrl()).toBe('/learning/flashcards')
  })

  it('动作自带的 knowledgeId 优先于外部上下文（更具体者胜）', async () => {
    const w = mount(NextActionCard, {
      props: { action: makeAction({ type: 'FLASHCARD', knowledgeId: 999 }), context: CTX },
    })
    await w.find('button').trigger('click')
    await flushPromises()

    expect(pushedUrl()).toContain('knowledgeId=999')
    expect(pushedUrl()).not.toContain('knowledgeId=42')
  })

  it('KNOWLEDGE 动作走 /learning/knowledge-mastery/:id —— 不画蛇添足加 query', async () => {
    const w = mount(NextActionCard, {
      props: { action: makeAction({ type: 'KNOWLEDGE', knowledgeId: 42 }), context: CTX },
    })
    await w.find('button').trigger('click')
    await flushPromises()

    expect(pushedUrl()).toBe('/learning/knowledge-mastery/42')
  })
})

describe('NextActionCard · MINI_APP 入口同样不丢上下文', () => {
  it('闪卡 Mini App（核心学习页）：带上 knowledgeId 与 chapterId', async () => {
    const w = mount(NextActionCard, {
      props: { action: makeAction({ type: 'MINI_APP', appKey: 'flashcard' }), context: CTX },
    })
    await w.find('button').trigger('click')
    await flushPromises()

    const url = pushedUrl()
    expect(url.startsWith('/learning/flashcards?')).toBe(true)
    expect(url).toContain('knowledgeId=42')
    expect(url).toContain('chapterId=301')
  })

  it('故事 Mini App（非核心学习页）：只带知识点，不强塞 learningNodeId', async () => {
    const w = mount(NextActionCard, {
      props: { action: makeAction({ type: 'MINI_APP', appKey: 'feynman' }), context: CTX },
    })
    await w.find('button').trigger('click')
    await flushPromises()

    const url = pushedUrl()
    expect(url.startsWith('/workbench/story?')).toBe(true)
    expect(url).toContain('knowledgeId=42')
    expect(url).not.toContain('chapterId')
  })

  it('已安装但停用 → 重新启用后打开（不丢失上下文）', async () => {
    appStoreMock.stateOf.mockReturnValue({ installed: true, enabled: false })
    const w = mount(NextActionCard, {
      props: { action: makeAction({ type: 'MINI_APP', appKey: 'flashcard' }), context: CTX },
    })
    await w.find('button').trigger('click')
    await flushPromises()

    expect(appStoreMock.enable).toHaveBeenCalledWith('flashcard')
    expect(pushedUrl()).toContain('knowledgeId=42')
  })
})

describe('NextActionCard · 展示契约（§二十六：3 秒内知道要做什么）', () => {
  it('渲染 标题 / 原因 / 方式 / 预计', () => {
    const w = mount(NextActionCard, {
      props: { action: makeAction({ type: 'FLASHCARD' }), context: CTX },
    })
    expect(w.text()).toContain('下一步')
    expect(w.text()).toContain('用闪卡巩固「JVM G1 GC」')
    expect(w.text()).toContain('方式')
    expect(w.text()).toContain('预计')
  })

  it('有 knowledgeName 时展示「知识点」行', () => {
    const w = mount(NextActionCard, {
      props: { action: makeAction({ type: 'FLASHCARD', knowledgeName: 'JVM G1 GC' }) },
    })
    expect(w.text()).toContain('知识点')
    expect(w.text()).toContain('JVM G1 GC')
  })

  it('只有 id 没有名称时不编造「知识点 42」（元信息行整条不渲染）', () => {
    const w = mount(NextActionCard, {
      props: { action: makeAction({ type: 'FLASHCARD', knowledgeId: 42, title: '复习', reason: '到期' }) },
    })
    // 「知识点」这一 dt/dd 不应出现；title/reason 里出现该词不算（那是文案本身）
    expect(w.find('.na-meta-knowledge').exists()).toBe(false)
    expect(w.html()).not.toContain('知识点 42')
  })

  it('dismissible → 关闭按钮具备可访问名称，并派发 dismiss', async () => {
    const w = mount(NextActionCard, {
      props: { action: makeAction({ type: 'FLASHCARD' }), dismissible: true },
    })
    const btn = w.find('.na-dismiss')
    expect(btn.attributes('aria-label')).toBe('关闭建议')
    await btn.trigger('click')
    expect(w.emitted('dismiss')).toBeTruthy()
  })
})

describe('NextActionCard · 禁止自动跳转（§133-135）', () => {
  it('挂载本身不给任何跳转', async () => {
    mount(NextActionCard, { props: { action: makeAction({ type: 'FLASHCARD' }), context: CTX } })
    await flushPromises()
    expect(pushMock).not.toHaveBeenCalled()
  })
})

// 让 pinia 在本文件可用（组件内部未直接使用 store 时也不应报错）
beforeEach(() => {
  setActivePinia(createPinia())
})
