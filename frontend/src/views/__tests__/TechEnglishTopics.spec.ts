// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import TechEnglishTopics from '../TechEnglishTopics.vue'

const authState = vi.hoisted(() => ({ isLoggedIn: false, isAdmin: false }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => authState }))

const api = vi.hoisted(() => ({ getTopics: vi.fn() }))
vi.mock('@/api/techEnglish', () => ({ techEnglishApi: { getTopics: api.getTopics } }))
vi.mock('@/utils/toast', () => ({
  notify: vi.fn(),
  getApiError: (_: unknown, fallback = '请求失败') => fallback,
}))

const TOPICS = [
  { domain: 'RAG', total: 20, learned: 5, due: 2 },
  { domain: 'JAVA', total: 12, learned: 0, due: 0 },
  { domain: 'SPRING', total: 20, learned: 10, due: 0 },
  { domain: 'AI_ENG', total: 18, learned: 0, due: 0 },
]

function makeRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/learning/english/topics', name: 'TechEnglishTopics', component: TechEnglishTopics },
      { path: '/learning/english/topics/:domain', name: 'TechEnglishTopic', component: { template: '<div />' } },
      { path: '/learning/english', name: 'TechEnglishHome', component: { template: '<div />' } },
    ],
  })
}

async function mountPage() {
  const router = makeRouter()
  await router.push('/learning/english/topics')
  await router.isReady()
  const wrapper = mount(TechEnglishTopics, { global: { plugins: [createPinia(), router] } })
  await flushPromises()
  await flushPromises()
  return wrapper
}

describe('TechEnglishTopics', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    authState.isLoggedIn = false
    api.getTopics.mockResolvedValue(TOPICS)
  })

  it('按基础方向优先的顺序展示专题，而不是接口返回顺序', async () => {
    const wrapper = await mountPage()
    const titles = wrapper.findAll('.te-topic-title').map((node) => node.text())
    expect(titles[0]).toContain('Java')
    expect(titles[titles.length - 1]).toContain('AI')
    expect(titles.length).toBe(TOPICS.length)
  })

  it('展示词条总数、已学进度与到期复习数量', async () => {
    const wrapper = await mountPage()
    const metas = wrapper.findAll('.te-topic-meta').map((node) => node.text())
    const ragMeta = metas.find((text) => text.includes('5 / 20'))
    expect(ragMeta).toBeTruthy()
    expect(ragMeta).toContain('待复习 2')
    expect(wrapper.text()).toContain('暂无到期复习')
  })

  it('未登录时说明只展示词条数量，不伪造个人进度', async () => {
    const wrapper = await mountPage()
    expect(wrapper.text()).toContain('登录后可看到已学与到期复习进度')
  })

  it('每个专题卡片指向真实专题路由', async () => {
    const wrapper = await mountPage()
    const links = wrapper.findAll('a.te-topic-enter')
    expect(links.length).toBe(TOPICS.length)
    expect(links[0].attributes('href')).toBe('/learning/english/topics/JAVA')
  })

  it('接口失败时展示错误态并可重试', async () => {
    api.getTopics.mockRejectedValueOnce(new Error('boom'))
    const wrapper = await mountPage()
    expect(wrapper.text()).toContain('专题加载失败')
    api.getTopics.mockResolvedValue(TOPICS)
    await wrapper.get('.kb-state-retry').trigger('click')
    await flushPromises()
    expect(wrapper.findAll('.te-topic-card').length).toBe(TOPICS.length)
  })

  it('专题为空时展示空状态而不是空白页面', async () => {
    api.getTopics.mockResolvedValue([])
    const wrapper = await mountPage()
    expect(wrapper.text()).toContain('暂无专题')
  })
})
