// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import TechEnglishHome from '../TechEnglishHome.vue'

// 登录态在用例开始前设置；页面只读取 isLoggedIn / isAdmin，不需要真实 Pinia store。
const authState = vi.hoisted(() => ({ isLoggedIn: false, isAdmin: false }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => authState }))

const api = vi.hoisted(() => ({
  searchTerms: vi.fn(),
  getTerm: vi.fn(),
  getFilters: vi.fn(),
  getMyTerms: vi.fn(),
  updateUserState: vi.fn(),
  getProgress: vi.fn(),
  addToReview: vi.fn(),
  listAdminTerms: vi.fn(),
  getAdminTerm: vi.fn(),
  createTerm: vi.fn(),
  updateTerm: vi.fn(),
  previewImport: vi.fn(),
  confirmImport: vi.fn(),
}))
vi.mock('@/api/techEnglish', () => ({
  techEnglishApi: {
    searchTerms: api.searchTerms,
    getTerm: api.getTerm,
    getFilters: api.getFilters,
    getMyTerms: api.getMyTerms,
    updateUserState: api.updateUserState,
    getProgress: api.getProgress,
    addToReview: api.addToReview,
  },
  adminTechEnglishApi: {
    listTerms: api.listAdminTerms,
    getTerm: api.getAdminTerm,
    createTerm: api.createTerm,
    updateTerm: api.updateTerm,
    previewImport: api.previewImport,
    confirmImport: api.confirmImport,
  },
}))
vi.mock('@/utils/toast', () => ({
  notify: vi.fn(),
  getApiError: (_: unknown, fallback: string) => fallback,
}))

const SUMMARY_AQS = {
  id: 1,
  headword: 'AQS',
  termType: 'ACRONYM' as const,
  domain: 'CONCURRENCY',
  meaningCn: '抽象队列同步器',
  fullForm: 'AbstractQueuedSynchronizer',
  className: 'java.util.concurrent.locks.AbstractQueuedSynchronizer',
  hasVerifiedPronunciation: true,
  pronunciationVariants: ['ABBR', 'FULL_FORM'],
}
const SUMMARY_EMBEDDING = {
  id: 2,
  headword: 'Embedding',
  termType: 'WORD' as const,
  domain: 'LLM',
  meaningCn: '向量嵌入',
  fullForm: null,
  hasVerifiedPronunciation: true,
  pronunciationVariants: ['WORD_US'],
}

const DETAIL_AQS = {
  ...SUMMARY_AQS,
  contentStatus: 'PUBLISHED' as const,
  definitionCn: 'AQS 通过状态位与 CLH 变体队列提供同步器骨架。',
  exampleEn: 'AQS is the backbone of most java.util.concurrent locks.',
  wordBreakdown: [{ word: 'Abstract', meaningCn: '抽象的' }],
  aliases: ['抽象队列同步器'],
  pronunciations: [
    {
      id: 11,
      variant: 'ABBR' as const,
      readingText: 'A Q S',
      locale: 'en-US',
      readingMode: 'LETTER_BY_LETTER' as const,
      audioUrl: null,
      verificationStatus: 'VERIFIED' as const,
    },
    {
      id: 12,
      variant: 'FULL_FORM' as const,
      readingText: 'AbstractQueuedSynchronizer',
      locale: 'en-US',
      readingMode: 'SPOKEN_WORD' as const,
      audioUrl: null,
      verificationStatus: 'VERIFIED' as const,
    },
  ],
  conceptLinks: [],
  relatedTerms: [],
  favorite: false,
  learningStatus: 'NEW' as const,
}
const DETAIL_EMBEDDING = {
  ...SUMMARY_EMBEDDING,
  contentStatus: 'PUBLISHED' as const,
  definitionCn: '把文本映射为稠密向量。',
  pronunciations: [],
  conceptLinks: [],
  relatedTerms: [],
  favorite: false,
  learningStatus: 'NEW' as const,
}

function page(records: unknown[], total: number) {
  return { records, total, pageNum: 1, pageSize: 20, pages: Math.ceil(total / 20) || 1 }
}

async function render(path: string) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/learning/english', name: 'TechEnglishHome', component: TechEnglishHome },
      { path: '/learning/english/review', name: 'TechEnglishReview', component: { template: '<div>review</div>' } },
      { path: '/learning/english/topics', name: 'TechEnglishTopics', component: { template: '<div>topics</div>' } },
      { path: '/learning/english/topics/:domain', name: 'TechEnglishTopic', component: { template: '<div>topic</div>' } },
      { path: '/learning/english/speak', name: 'TechEnglishSpeak', component: { template: '<div>speak</div>' } },
      { path: '/learning/english/:id', name: 'TechEnglishDetail', component: TechEnglishHome },
      { path: '/login', name: 'Login', component: { template: '<div>login</div>' } },
      { path: '/:pathMatch(.*)*', component: { template: '<div />' } },
    ],
  })
  await router.push(path)
  const wrapper = mount(
    { template: '<RouterView />' },
    { global: { plugins: [createPinia(), router], stubs: { Teleport: true } } },
  )
  await flushPromises()
  await flushPromises()
  return { wrapper, router }
}

beforeEach(() => {
  vi.clearAllMocks()
  authState.isLoggedIn = false
  authState.isAdmin = false
  api.searchTerms.mockResolvedValue(page([SUMMARY_AQS, SUMMARY_EMBEDDING], 2))
  api.getTerm.mockImplementation(async (id: number) =>
    id === 1 ? { ...DETAIL_AQS } : { ...DETAIL_EMBEDDING },
  )
  api.getFilters.mockResolvedValue({
    total: 2,
    byType: { WORD: 1, ACRONYM: 1, PHRASE: 0 },
    domains: [
      { domain: 'CONCURRENCY', count: 1 },
      { domain: 'LLM', count: 1 },
    ],
    myFavoriteCount: 0,
    myLearningCount: 0,
  })
  api.getMyTerms.mockResolvedValue(page([], 0))
  api.updateUserState.mockResolvedValue({ termId: 1, favorite: true, learningStatus: 'LEARNING' })
  api.getProgress.mockResolvedValue({
    learned: 1,
    dueToday: 2,
    completedToday: 3,
    addedToReview: 4,
    favoriteCount: 0,
    totalTerms: 2,
    domains: [{ domain: 'CONCURRENCY', total: 1, learned: 1 }],
  })
  api.addToReview.mockResolvedValue({ cardId: 77, created: true })
  api.listAdminTerms.mockResolvedValue(page([], 0))
  api.getAdminTerm.mockResolvedValue({ ...DETAIL_AQS })
  api.createTerm.mockResolvedValue({ ...DETAIL_AQS })
  api.updateTerm.mockResolvedValue({ ...DETAIL_AQS })
})

describe('专业英语词汇大厅', () => {
  it('首次加载展示词条并自动选中第一条详情', async () => {
    const { wrapper } = await render('/learning/english')
    expect(wrapper.text()).toContain('AQS')
    expect(wrapper.text()).toContain('抽象队列同步器')
    expect(wrapper.text()).toContain('AQS 通过状态位与 CLH 变体队列提供同步器骨架。')
    expect(api.searchTerms).toHaveBeenCalledWith(
      expect.objectContaining({ pageNum: 1, pageSize: 20 }),
    )
    expect(api.getTerm).toHaveBeenCalledWith(1)
    wrapper.unmount()
  })

  it('搜索回车写入 URL 并按关键词重新检索', async () => {
    const { wrapper, router } = await render('/learning/english')
    await wrapper.get('input[type="search"]').setValue('CAS')
    await wrapper.get('input[type="search"]').trigger('keydown.enter')
    await flushPromises()
    expect(router.currentRoute.value.query.q).toBe('CAS')
    expect(api.searchTerms).toHaveBeenLastCalledWith(
      expect.objectContaining({ q: 'CAS', pageNum: 1 }),
    )
    wrapper.unmount()
  })

  it('类型筛选与分页都由 URL 查询驱动', async () => {
    api.searchTerms.mockResolvedValue(page([SUMMARY_AQS], 25))
    const { wrapper, router } = await render('/learning/english')
    const acronym = wrapper
      .findAll('.kb-filter-group button')
      .find((button) => button.text().startsWith('缩写'))
    expect(acronym).toBeTruthy()
    await acronym!.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.type).toBe('ACRONYM')
    expect(api.searchTerms).toHaveBeenLastCalledWith(
      expect.objectContaining({ type: 'ACRONYM', pageNum: 1 }),
    )

    await wrapper.get('button[aria-label="下一页"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.page).toBe('2')
    expect(api.searchTerms).toHaveBeenLastCalledWith(
      expect.objectContaining({ type: 'ACRONYM', pageNum: 2 }),
    )
    wrapper.unmount()
  })

  it('列表失败显示错误态，重试后恢复真实数据', async () => {
    api.searchTerms.mockRejectedValueOnce(new Error('offline'))
    const { wrapper } = await render('/learning/english')
    const alert = wrapper.get('[role="alert"]')
    expect(alert.text()).toContain('词库加载失败')
    expect(alert.text()).not.toContain('暂无词条')
    await alert.get('.kb-state-retry').trigger('click')
    await flushPromises()
    await flushPromises()
    expect(wrapper.text()).toContain('AQS')
    wrapper.unmount()
  })

  it('匿名点击收藏跳转登录，且不写入个人状态', async () => {
    const { wrapper, router } = await render('/learning/english')
    await wrapper.get('button[title="收藏"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/login')
    expect(router.currentRoute.value.query.redirect).toContain('/learning/english')
    expect(api.updateUserState).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('登录后收藏写入真实状态并回显「已收藏」', async () => {
    authState.isLoggedIn = true
    const { wrapper } = await render('/learning/english')
    await wrapper.get('button[title="收藏"]').trigger('click')
    await flushPromises()
    expect(api.updateUserState).toHaveBeenCalledWith(1, { favorite: true })
    expect(wrapper.text()).toContain('已收藏')
    expect(wrapper.text()).toContain('学习中')
    wrapper.unmount()
  })

  it('点击词条进入详情路由并保留当前筛选条件', async () => {
    const { wrapper, router } = await render('/learning/english?q=CAS&type=ACRONYM')
    await wrapper.get('.te-term').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/learning/english/1')
    expect(router.currentRoute.value.query).toEqual({ q: 'CAS', type: 'ACRONYM' })
    wrapper.unmount()
  })

  it('详情深链直接按路由 id 加载详情并进入单栏详情态', async () => {
    const { wrapper } = await render('/learning/english/2?q=Embedding')
    expect(wrapper.get('.te-layout').classes()).toContain('is-detail')
    expect(api.getTerm).toHaveBeenCalledWith(2)
    expect(wrapper.text()).toContain('向量嵌入')
    wrapper.unmount()
  })

  it('登录后展示真实学习看板，可加入复习并进入复习页', async () => {
    authState.isLoggedIn = true
    const { wrapper, router } = await render('/learning/english')
    expect(wrapper.text()).toContain('我的学习看板')
    expect(wrapper.text()).toContain('已学习词汇')
    expect(wrapper.text()).toContain('今日待复习')
    expect(api.getProgress).toHaveBeenCalled()

    const addButton = wrapper
      .findAll('button')
      .find((button) => button.text().includes('加入复习'))
    expect(addButton).toBeTruthy()
    await addButton!.trigger('click')
    await flushPromises()
    expect(api.addToReview).toHaveBeenCalledWith(1)

    const startButton = wrapper
      .findAll('button')
      .find((button) => button.text().includes('开始今日复习'))
    await startButton!.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/learning/english/review')
    wrapper.unmount()
  })

  it('管理员工具栏五个操作按钮保持「图标 + 文字」结构且可点击', async () => {
    authState.isLoggedIn = true
    authState.isAdmin = true
    const { wrapper } = await render('/learning/english')
    const buttons = wrapper.get('.te-head-actions').findAll('.te-action-btn')
    expect(buttons).toHaveLength(5)
    for (const button of buttons) {
      expect(button.find('svg').exists()).toBe(true)
      expect(button.get('.te-action-label').text().length).toBeGreaterThan(0)
    }
    expect(buttons.map((button) => button.get('.te-action-label').text())).toEqual([
      '我的生词',
      '新建词条',
      '批量导入',
      '候选审核',
      '管理词条',
    ])

    await buttons[1].trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('新建技术词条')
    wrapper.unmount()
  })

  it('非管理员不渲染管理入口，仅保留「我的生词」', async () => {
    const { wrapper } = await render('/learning/english')
    const labels = wrapper
      .findAll('.te-head-actions .te-action-label')
      .map((node) => node.text())
    expect(labels).toEqual(['我的生词'])
    wrapper.unmount()
  })

  it('「我的生词」用独立徽标显示收藏数，点击写入 mine 查询', async () => {
    authState.isLoggedIn = true
    api.getFilters.mockResolvedValue({
      total: 2,
      byType: { WORD: 1, ACRONYM: 1, PHRASE: 0 },
      domains: [{ domain: 'CONCURRENCY', count: 1 }],
      myFavoriteCount: 1,
      myLearningCount: 0,
    })
    const { wrapper, router } = await render('/learning/english')
    expect(wrapper.get('.te-action-badge').text()).toBe('1')
    await wrapper.get('.te-head-actions .te-action-btn').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.mine).toBe('1')
    wrapper.unmount()
  })
})
