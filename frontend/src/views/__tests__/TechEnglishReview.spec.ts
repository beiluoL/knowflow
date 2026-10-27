// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import TechEnglishReview from '../TechEnglishReview.vue'

const api = vi.hoisted(() => ({
  getReviewQueue: vi.fn(),
  addToReview: vi.fn(),
  submitReview: vi.fn(),
  getTerm: vi.fn(),
  flashcardSchedule: vi.fn(),
}))

vi.mock('@/api/techEnglish', () => ({
  techEnglishApi: {
    getReviewQueue: api.getReviewQueue,
    addToReview: api.addToReview,
    submitReview: api.submitReview,
    getTerm: api.getTerm,
  },
}))
vi.mock('@/api/learning', () => ({
  learningApi: { flashcardSchedule: api.flashcardSchedule },
}))
vi.mock('@/composables/useTermSpeech', async () => {
  const { ref } = await import('vue')
  return {
    useTermSpeech: () => ({
      speakingKey: ref(null),
      speechError: ref(''),
      play: vi.fn(),
      stop: vi.fn(),
    }),
  }
})
vi.mock('@/utils/toast', () => ({
  notify: vi.fn(),
  getApiError: (_: unknown, fallback: string) => fallback,
}))

const PRON_ABBR = {
  variant: 'ABBR' as const,
  readingText: 'A Q S',
  locale: 'en-US',
  audioUrl: null,
  verificationStatus: 'VERIFIED' as const,
}
const PRON_FULL = {
  variant: 'FULL_FORM' as const,
  readingText: 'AbstractQueuedSynchronizer',
  locale: 'en-US',
  audioUrl: null,
  verificationStatus: 'VERIFIED' as const,
}
const PRON_US = {
  variant: 'WORD_US' as const,
  readingText: 'Polymorphism',
  locale: 'en-US',
  audioUrl: null,
  verificationStatus: 'VERIFIED' as const,
}

const ITEM_AQS = {
  termId: 1,
  cardId: 101,
  headword: 'AQS',
  termType: 'ACRONYM' as const,
  domain: 'CONCURRENCY',
  meaningCn: '抽象队列同步器',
  fullForm: 'AbstractQueuedSynchronizer',
  className: 'java.util.concurrent.locks.AbstractQueuedSynchronizer',
  definitionCn: 'AQS 通过状态位与 CLH 变体队列提供同步器骨架。',
  ipaUs: null,
  ipaUk: null,
  pronunciations: [PRON_ABBR, PRON_FULL],
  acronymMode: true,
  listeningMode: true,
  learningStatus: 'LEARNING' as const,
  favorite: false,
  dueAt: null,
  reviewCount: 2,
}

const ITEM_WORD = {
  termId: 2,
  cardId: null,
  headword: 'Polymorphism',
  termType: 'WORD' as const,
  domain: 'JAVA',
  meaningCn: '多态',
  fullForm: null,
  className: null,
  definitionCn: '同一接口在不同对象上呈现不同行为。',
  ipaUs: null,
  ipaUk: null,
  pronunciations: [PRON_US],
  acronymMode: false,
  listeningMode: true,
  learningStatus: 'NEW' as const,
  favorite: false,
  dueAt: null,
  reviewCount: 0,
}

function queue(items: unknown[], overrides: Record<string, unknown> = {}) {
  return {
    items,
    dueCount: items.length,
    newCount: 0,
    limit: 20,
    hasReviewCards: true,
    ...overrides,
  }
}

function receipt(termId: number, cardId: number, rating: number) {
  return {
    termId,
    cardId,
    rating,
    dueAt: '2026-10-25T04:00:00Z',
    state: 'REVIEW',
    revision: 2,
    replayed: false,
  }
}

async function render() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/learning/english', name: 'TechEnglishHome', component: { template: '<div />' } },
      { path: '/learning/english/review', name: 'TechEnglishReview', component: TechEnglishReview },
    ],
  })
  await router.push('/learning/english/review')
  const wrapper = mount(TechEnglishReview, {
    global: { plugins: [createPinia(), router] },
  })
  await flushPromises()
  await flushPromises()
  return { wrapper, router }
}

beforeEach(() => {
  vi.clearAllMocks()
  sessionStorage.clear()
  api.getReviewQueue.mockResolvedValue(queue([{ ...ITEM_AQS }, { ...ITEM_WORD }]))
  api.getTerm.mockResolvedValue({
    id: 1,
    wordBreakdown: [{ word: 'Abstract', meaningCn: '抽象的' }],
    conceptLinks: [{ targetType: 'CHAPTER', relationType: 'RELATED', label: 'AQS 底层原理', route: '/learning/x' }],
  })
  api.flashcardSchedule.mockResolvedValue({
    settingsRevision: 0,
    revision: 1,
    state: 'REVIEW',
    algorithm: 'FSRS',
    previousDueAt: null,
    conservativeInitialization: false,
    previews: [
      { rating: 1, dueAt: '2026-10-24T10:00:00Z', intervalSeconds: 600 },
      { rating: 2, dueAt: '2026-10-25T10:00:00Z', intervalSeconds: 86400 },
      { rating: 3, dueAt: '2026-10-27T10:00:00Z', intervalSeconds: 259200 },
      { rating: 4, dueAt: '2026-11-01T10:00:00Z', intervalSeconds: 604800 },
    ],
    serverTime: '2026-10-24T10:00:00Z',
  })
  api.addToReview.mockResolvedValue({ cardId: 202, created: true })
  api.submitReview.mockImplementation(async (termId: number) =>
    receipt(termId, termId === 1 ? 101 : 202, 3),
  )
})

describe('技术英语词汇复习专注页', () => {
  it('默认展示英文词形并隐藏答案，揭晓前没有评分按钮', async () => {
    const { wrapper } = await render()
    expect(wrapper.get('.ter-prompt-word').text()).toBe('AQS')
    expect(wrapper.text()).toContain('已完成 0 / 2')
    expect(wrapper.find('.ter-answer').exists()).toBe(false)
    expect(wrapper.find('.ter-rating').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('抽象队列同步器')
    wrapper.unmount()
  })

  it('揭晓答案后展示中文与释义，自评写入服务端并前进到下一词条', async () => {
    const { wrapper } = await render()
    await wrapper.get('.ter-reveal').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('抽象队列同步器')
    expect(wrapper.text()).toContain('AQS 通过状态位与 CLH 变体队列提供同步器骨架。')

    const buttons = wrapper.findAll('.ter-rating-btn')
    expect(buttons).toHaveLength(4)
    await buttons[2]!.trigger('click')
    await flushPromises()

    expect(api.submitReview).toHaveBeenCalledWith(
      1,
      expect.objectContaining({ rating: 3, requestId: expect.stringMatching(/^[A-Za-z0-9_-]{12,80}$/) }),
    )
    expect(wrapper.get('.ter-prompt-word').text()).toBe('Polymorphism')
    expect(wrapper.text()).toContain('已完成 1 / 2')

    const stored = JSON.parse(sessionStorage.getItem('knowflow:tech-english:review-session') || '{}')
    expect(stored.submissions).toHaveLength(1)
    expect(stored.submissions[0].termId).toBe(1)
    wrapper.unmount()
  })

  it('未加入复习的词条先建立唯一复习卡，再提交评分', async () => {
    api.getReviewQueue.mockResolvedValue(queue([{ ...ITEM_WORD }]))
    const { wrapper } = await render()
    await wrapper.get('.ter-reveal').trigger('click')
    await flushPromises()
    await wrapper.findAll('.ter-rating-btn')[2]!.trigger('click')
    await flushPromises()

    expect(api.addToReview).toHaveBeenCalledWith(2)
    expect(api.submitReview).toHaveBeenCalledWith(
      2,
      expect.objectContaining({ rating: 3 }),
    )
    expect(api.addToReview.mock.invocationCallOrder[0]).toBeLessThan(
      api.submitReview.mock.invocationCallOrder[0]!,
    )
    wrapper.unmount()
  })

  it('缩写模式只对具备可靠英文全称的词条开放', async () => {
    api.getReviewQueue.mockResolvedValue(queue([{ ...ITEM_WORD }]))
    const { wrapper } = await render()
    const abbrTab = wrapper
      .findAll('.ter-mode')
      .find((button) => button.text() === '缩写→全称')
    expect(abbrTab).toBeTruthy()
    expect(abbrTab!.attributes('disabled')).toBeDefined()
    wrapper.unmount()

    api.getReviewQueue.mockResolvedValue(queue([{ ...ITEM_AQS }]))
    const second = await render()
    const tab = second.wrapper
      .findAll('.ter-mode')
      .find((button) => button.text() === '缩写→全称')
    expect(tab!.attributes('disabled')).toBeUndefined()
    await tab!.trigger('click')
    await flushPromises()
    await second.wrapper.get('.ter-reveal').trigger('click')
    await flushPromises()
    expect(second.wrapper.text()).toContain('AbstractQueuedSynchronizer')
    expect(second.wrapper.text()).toContain('java.util.concurrent.locks.AbstractQueuedSynchronizer')
    expect(second.wrapper.text()).toContain('全称逐词拆解')
    second.wrapper.unmount()
  })

  it('听音模式揭晓前不泄露目标单词', async () => {
    api.getReviewQueue.mockResolvedValue(queue([{ ...ITEM_AQS }]))
    const { wrapper } = await render()
    const listenTab = wrapper
      .findAll('.ter-mode')
      .find((button) => button.text() === '听音识词')
    await listenTab!.trigger('click')
    await flushPromises()
    expect(wrapper.get('.ter-card').text()).not.toContain('AQS')
    await wrapper.get('.ter-reveal').trigger('click')
    await flushPromises()
    expect(wrapper.get('.ter-card').text()).toContain('AQS')
    wrapper.unmount()
  })

  it('中文→英文支持可选输入，拼写核对仅作参考', async () => {
    api.getReviewQueue.mockResolvedValue(queue([{ ...ITEM_AQS }]))
    const { wrapper } = await render()
    const cnEnTab = wrapper
      .findAll('.ter-mode')
      .find((button) => button.text() === '中文→英文')
    await cnEnTab!.trigger('click')
    await flushPromises()
    await wrapper.get('.ter-input').setValue('AQS')
    await wrapper.get('.ter-reveal').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('与你输入的拼写一致')
    expect(wrapper.text()).toContain('拼写核对仅供参考')
    wrapper.unmount()
  })

  it('刷新后按服务端记录恢复已提交进度，不重复展示已评分词条', async () => {
    sessionStorage.setItem(
      'knowflow:tech-english:review-session',
      JSON.stringify({
        sessionId: 'session_abc123456789',
        mode: 'en-cn',
        createdAt: Date.now(),
        submissions: [
          { termId: 1, rating: 3, dueAt: null, state: 'REVIEW', reviewedAt: new Date().toISOString() },
        ],
      }),
    )
    const { wrapper } = await render()
    expect(wrapper.get('.ter-prompt-word').text()).toBe('Polymorphism')
    expect(wrapper.text()).toContain('已完成 1 / 2')
    wrapper.unmount()
  })

  it('评分提交中禁用按钮，连续点击不会重复写入', async () => {
    let resolveSubmit: (value: unknown) => void = () => {}
    api.submitReview.mockReturnValueOnce(
      new Promise((resolve) => {
        resolveSubmit = resolve
      }),
    )
    api.getReviewQueue.mockResolvedValue(queue([{ ...ITEM_AQS }]))
    const { wrapper } = await render()
    await wrapper.get('.ter-reveal').trigger('click')
    await flushPromises()

    const button = wrapper.findAll('.ter-rating-btn')[2]!
    await button.trigger('click')
    await flushPromises()
    expect(button.attributes('disabled')).toBeDefined()
    await button.trigger('click')
    expect(api.submitReview).toHaveBeenCalledTimes(1)

    resolveSubmit(receipt(1, 101, 3))
    await flushPromises()
    wrapper.unmount()
  })

  it('没有复习词条时展示空态并引导返回词汇大厅', async () => {
    api.getReviewQueue.mockResolvedValue(
      queue([], { dueCount: 0, newCount: 0, hasReviewCards: false }),
    )
    const { wrapper } = await render()
    expect(wrapper.text()).toContain('还没有开始复习')
    expect(wrapper.text()).toContain('返回词汇大厅')
    wrapper.unmount()
  })
})
