// @vitest-environment happy-dom
/**
 * 复习计划页（ReviewPlan）—— UI/UX 5.1 §六 / §八 专项测试
 *
 * 覆盖本阶段对该页的两处真实修复：
 *  1. §六：加载失败不再把 `闪卡数据加载失败：Request failed with status code 502` 直接渲染出来，
 *         而是三要素文案（发生了什么 / 影响范围 / 怎么解决）。
 *  2. §八：深链带来的知识点上下文被**真实消费**（顶部上下文条），并且跳转闪卡页时会**继续传下去**，
 *         用户不必再挑一次知识点。
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { reactive } from 'vue'

const routeMock = reactive<{ query: Record<string, unknown> }>({ query: {} })
const pushMock = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: pushMock, back: vi.fn() }),
  useRoute: () => routeMock,
}))

const flashcardsMock = vi.fn()
const dueMock = vi.fn()
const statsMock = vi.fn()
vi.mock('@/api/learning', () => ({
  learningApi: {
    dueFlashcards: () => dueMock(),
    flashcardStatistics: () => statsMock(),
    get flashcards() {
      return flashcardsMock
    },
  },
}))

vi.mock('@/utils/toast', () => ({ notify: vi.fn() }))
vi.mock('@/utils/studySession', () => ({ loadReviewedIds: () => new Set<string>() }))

import ReviewPlan from '@/views/ReviewPlan.vue'

function mountPage() {
  return mount(ReviewPlan, {
    global: { stubs: { RouterLink: true } },
  })
}

beforeEach(() => {
  pushMock.mockClear()
  flashcardsMock.mockReset()
  dueMock.mockReset().mockResolvedValue([])
  statsMock.mockReset().mockResolvedValue({today:{completed:0,newCards:0,reviews:0}})
  routeMock.query = {}
})

describe('ReviewPlan §八 —— 消费并继续传递知识点上下文', () => {
  it('无上下文时不渲染上下文条（不造「未知知识点」）', async () => {
    flashcardsMock.mockResolvedValue([])
    const w = mountPage()
    await flushPromises()
    expect(w.find('[data-test="review-context"]').exists()).toBe(false)
  })

  it('带 knowledgeId/knowledgeName 深链时显示「正在学习：X」', async () => {
    routeMock.query = { knowledgeId: '42', knowledgeName: 'JVM G1 GC' }
    flashcardsMock.mockResolvedValue([])
    const w = mountPage()
    await flushPromises()
    const chip = w.find('[data-test="review-context"]')
    expect(chip.exists()).toBe(true)
    expect(chip.text()).toContain('JVM G1 GC')
  })

  it('只有 knowledgeId 时退化为编号，而不是「未知」', async () => {
    routeMock.query = { knowledgeId: '42' }
    flashcardsMock.mockResolvedValue([])
    const w = mountPage()
    await flushPromises()
    expect(w.find('[data-test="review-context"]').text()).toContain('知识点 #42')
  })

  it('跳转闪卡页时把上下文一起带过去（不再重新挑知识点）', async () => {
    routeMock.query = { knowledgeId: '42', knowledgeName: 'JVM G1 GC' }
    flashcardsMock.mockResolvedValue([])
    const w = mountPage()
    await flushPromises()

    // 该页有两个入口（顶部主 CTA / 空态 CTA），点第一个可用的即可
    const cta = w.findAll('button').find((b) => /复习|开始|闪卡/.test(b.text()))
    expect(cta, '复习计划页应至少有一个进入闪卡复习的 CTA').toBeTruthy()
    await cta!.trigger('click')

    expect(pushMock).toHaveBeenCalledTimes(1)
    const arg = pushMock.mock.calls[0][0]
    expect(arg.path).toBe('/learning/flashcards')
    expect(arg.query).toMatchObject({ knowledgeId: '42', knowledgeName: 'JVM G1 GC' })
  })

  it('无上下文时跳转保持干净裸路由', async () => {
    flashcardsMock.mockResolvedValue([])
    const w = mountPage()
    await flushPromises()

    const cta = w.findAll('button').find((b) => /复习|开始|闪卡/.test(b.text()))
    await cta!.trigger('click')
    expect(pushMock).toHaveBeenCalledWith('/learning/flashcards')
  })
})

describe('ReviewPlan §六 —— 失败不等于空，且不泄漏原始报错', () => {
  it('加载失败渲染三要素，而不是「今天没有待复习」的假空态', async () => {
    flashcardsMock.mockRejectedValue(
      Object.assign(new Error('Request failed with status code 502'), {
        response: { status: 502 },
      }),
    )
    const w = mountPage()
    await flushPromises()

    const err = w.find('[data-test="review-error"]')
    expect(err.exists()).toBe(true)
    expect(err.attributes('role')).toBe('alert')
    expect(err.text()).toContain('服务暂时不可用')
  })

  it('页面文本里不出现 status code / 英文异常名', async () => {
    flashcardsMock.mockRejectedValue(
      Object.assign(new Error('Request failed with status code 502'), {
        response: { status: 502 },
      }),
    )
    const w = mountPage()
    await flushPromises()

    const text = w.text()
    expect(text).not.toContain('status code')
    expect(text).not.toContain('502')
    expect(text).not.toContain('Request failed')
  })

  it('错误态同时给出「怎么解决」与重试入口', async () => {
    flashcardsMock.mockRejectedValue(new Error('boom'))
    const w = mountPage()
    await flushPromises()

    const err = w.find('[data-test="review-error"]')
    const retry = err.findAll('button').find((b) => b.text().includes('重新加载'))
    expect(retry).toBeTruthy()
    expect(err.text().length).toBeGreaterThan(10)
  })
})

describe('Phase C server review facts',()=>{
 it('uses server due queue and statistics without static completed curve claims',async()=>{flashcardsMock.mockResolvedValue([]);dueMock.mockResolvedValue([{id:5,front:'服务端到期卡',difficulty:1}]);statsMock.mockResolvedValue({today:{completed:7,newCards:2,reviews:5}});const w=mountPage();await flushPromises();expect(w.text()).toContain('服务端到期卡');expect(w.text()).toContain('今日已提交');expect(w.text()).not.toContain('学习后 20 分钟');expect(w.text()).not.toContain('本地计划');w.unmount()})
 it('does not invent calendar dates for cards without a backend due date',async()=>{flashcardsMock.mockResolvedValue([{id:9,front:'尚未排程的卡片',reviewCount:2,nextReviewTime:null}]);const w=mountPage();await flushPromises();expect(w.text()).not.toContain('尚未排程的卡片');w.unmount()})
})
