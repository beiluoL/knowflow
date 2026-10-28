// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { ref } from 'vue'
import TechEnglishTopic from '../TechEnglishTopic.vue'
import type { TopicDetailVO } from '@/api/techEnglish'

const authState = vi.hoisted(() => ({ isLoggedIn: false, isAdmin: false }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => authState }))

const api = vi.hoisted(() => ({ getTopic: vi.fn(), addToReview: vi.fn() }))
vi.mock('@/api/techEnglish', () => ({
  techEnglishApi: { getTopic: api.getTopic, addToReview: api.addToReview },
}))

vi.mock('@/utils/toast', () => ({
  notify: vi.fn(),
  getApiError: (_: unknown, fallback = '请求失败') => fallback,
}))

const speech = vi.hoisted(() => ({ play: vi.fn(), stop: vi.fn() }))
vi.mock('@/composables/useTermSpeech', () => ({
  useTermSpeech: () => ({
    speakingKey: ref(null),
    speechError: ref(''),
    play: speech.play,
    stop: speech.stop,
  }),
}))

function pron(variant: 'WORD_US' | 'ABBR', readingText: string) {
  return {
    id: Math.random(),
    variant,
    readingText,
    locale: 'en-US',
    readingMode: variant === 'ABBR' ? ('LETTER_BY_LETTER' as const) : ('SPOKEN_WORD' as const),
    audioUrl: null,
    verificationStatus: 'VERIFIED' as const,
  }
}

const TOPIC: TopicDetailVO = {
  domain: 'JAVA',
  total: 3,
  learned: 1,
  due: 1,
  terms: [
    {
      termId: 1,
      headword: 'Object',
      termType: 'WORD',
      meaningCn: '对象',
      fullForm: null,
      ipaUs: null,
      ipaUk: null,
      pronunciations: [pron('WORD_US', 'Object')],
      hasVerifiedPronunciation: true,
      learningStatus: 'NEW',
      favorite: false,
      hasReviewCard: false,
      dueAt: null,
      reviewCount: 0,
    },
    {
      termId: 2,
      headword: 'Lambda Expression',
      termType: 'PHRASE',
      meaningCn: 'Lambda 表达式',
      fullForm: null,
      ipaUs: null,
      ipaUk: null,
      pronunciations: [pron('WORD_US', 'Lambda Expression')],
      hasVerifiedPronunciation: true,
      learningStatus: 'LEARNING',
      favorite: true,
      hasReviewCard: true,
      dueAt: '2026-10-25T09:00:00',
      reviewCount: 2,
    },
    {
      termId: 3,
      headword: 'OOM',
      termType: 'ACRONYM',
      meaningCn: '内存溢出错误',
      fullForm: 'OutOfMemoryError',
      ipaUs: null,
      ipaUk: null,
      pronunciations: [],
      hasVerifiedPronunciation: false,
      learningStatus: 'NEW',
      favorite: false,
      hasReviewCard: false,
      dueAt: null,
      reviewCount: 0,
    },
  ],
}

async function mountPage(domain = 'JAVA') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/learning/english/topics/:domain', name: 'TechEnglishTopic', component: TechEnglishTopic },
      { path: '/learning/english/topics', name: 'TechEnglishTopics', component: { template: '<div />' } },
      { path: '/learning/english/:id', name: 'TechEnglishDetail', component: { template: '<div />' } },
      { path: '/learning/english/review', name: 'TechEnglishReview', component: { template: '<div />' } },
      { path: '/learning/english', name: 'TechEnglishHome', component: { template: '<div />' } },
      { path: '/login', name: 'Login', component: { template: '<div />' } },
    ],
  })
  await router.push(`/learning/english/topics/${domain}`)
  await router.isReady()
  const wrapper = mount(TechEnglishTopic, { global: { plugins: [createPinia(), router] } })
  await flushPromises()
  return { wrapper, router }
}

describe('TechEnglishTopic 专题学习路径', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    authState.isLoggedIn = false
    speech.play.mockResolvedValue(true)
    api.getTopic.mockResolvedValue(JSON.parse(JSON.stringify(TOPIC)))
    api.addToReview.mockResolvedValue({ cardId: 11, created: true })
  })

  it('展示本专题统计，待学按总数减已学计算', async () => {
    const { wrapper } = await mountPage()
    const values = wrapper.findAll('.te-summary-value').map((node) => node.text())
    expect(values).toEqual(['3', '1', '2', '1'])
    const labels = wrapper.findAll('.te-summary-label').map((node) => node.text())
    expect(labels).toEqual(['本专题词条', '已学', '待学', '待复习'])
    expect(wrapper.get('.te-summary-value.is-due').text()).toBe('1')
  })

  it('按接口返回的编写顺序渲染路径序号，而不是自行重排', async () => {
    const { wrapper } = await mountPage()
    const words = wrapper.findAll('.te-path-word').map((node) => node.text())
    expect(words).toEqual(['Object', 'Lambda Expression', 'OOM'])
    const indexes = wrapper.findAll('.te-path-index').map((node) => node.text())
    expect(indexes).toEqual(['1', '2', '3'])
  })

  it('待学 / 已学 / 待复习筛选只影响列表展示', async () => {
    const { wrapper } = await mountPage()
    const buttons = wrapper.findAll('.kf-filter-btn')

    await buttons.find((b) => b.text() === '待复习')!.trigger('click')
    await flushPromises()
    expect(wrapper.findAll('.te-path-word').map((n) => n.text())).toEqual(['Lambda Expression'])

    await buttons.find((b) => b.text() === '已学')!.trigger('click')
    await flushPromises()
    expect(wrapper.findAll('.te-path-word').map((n) => n.text())).toEqual(['Lambda Expression'])

    await buttons.find((b) => b.text() === '待学')!.trigger('click')
    await flushPromises()
    expect(wrapper.findAll('.te-path-word').map((n) => n.text())).toEqual(['Object', 'OOM'])
  })

  it('无已核验读法的词条禁用播放，连续听读只统计可播放词条', async () => {
    const { wrapper } = await mountPage()
    const oomButton = wrapper.get('button[aria-label="播放 OOM 的发音"]')
    expect(oomButton.attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('共 2 个词条')
    const playAll = wrapper.findAll('button').find((b) => b.text().includes('连续听读'))
    expect(playAll!.attributes('disabled')).toBeUndefined()
  })

  it('未登录时加入复习指向登录页，不调用写接口', async () => {
    const { wrapper, router } = await mountPage()
    const links = wrapper.findAll('a.te-add-review')
    expect(links.length).toBe(3)
    expect(links[0].text()).toBe('登录后加入复习')
    expect(links[0].attributes('href')).toContain('/login')
    expect(router.currentRoute.value.path).toBe('/learning/english/topics/JAVA')
    expect(api.addToReview).not.toHaveBeenCalled()
  })

  it('登录后加入复习复用既有接口，并回显已在复习', async () => {
    authState.isLoggedIn = true
    const { wrapper } = await mountPage()
    const buttons = wrapper.findAll('button.te-add-review')
    const objectButton = buttons.find((b) => b.text() === '加入复习')
    expect(objectButton).toBeTruthy()
    await objectButton!.trigger('click')
    await flushPromises()
    expect(api.addToReview).toHaveBeenCalledWith(1)
    expect(buttons.some((b) => b.text() === '已在复习')).toBe(true)
  })

  it('词条详情链接带上真实 termId 与专题领域', async () => {
    const { wrapper } = await mountPage()
    const detailLinks = wrapper.findAll('a[title="查看技术概念"]')
    expect(detailLinks[0].attributes('href')).toBe('/learning/english/1?domain=JAVA')
  })

  it('接口失败展示错误态，重试后恢复路径', async () => {
    api.getTopic.mockRejectedValueOnce(new Error('boom'))
    const { wrapper } = await mountPage()
    expect(wrapper.text()).toContain('专题加载失败')
    api.getTopic.mockResolvedValue(JSON.parse(JSON.stringify(TOPIC)))
    await wrapper.get('.kf-state-retry').trigger('click')
    await flushPromises()
    expect(wrapper.findAll('.te-path-word').length).toBe(3)
  })

  it('专题操作按钮保持「图标 + 文字」结构，筛选入口带真实 domain', async () => {
    const { wrapper } = await mountPage()
    for (const label of ['全部专题', '在词汇大厅中筛选', '连续听读', '进入今日复习']) {
      const node = wrapper
        .findAll('.te-action-label')
        .find((item) => item.text() === label)
      expect(node, `缺少按钮文案：${label}`).toBeTruthy()
      expect(node!.element.parentElement?.querySelector('svg')).toBeTruthy()
    }
    const filterLink = wrapper
      .findAll('a.te-action-btn')
      .find((link) => link.text().includes('在词汇大厅中筛选'))
    expect(filterLink!.attributes('href')).toBe('/learning/english?domain=JAVA')
  })
})
