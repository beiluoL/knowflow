// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import TermLookupPopover from '../TermLookupPopover.vue'

const authState = vi.hoisted(() => ({ isLoggedIn: true }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => authState }))

const routerPush = vi.hoisted(() => vi.fn())
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: routerPush, currentRoute: { value: { fullPath: '/docs/1' } } }),
}))

const api = vi.hoisted(() => ({
  lookupTerm: vi.fn(),
  updateUserState: vi.fn(),
  addToReview: vi.fn(),
}))
vi.mock('@/api/techEnglish', () => ({ techEnglishApi: api }))

const notifyMock = vi.hoisted(() => vi.fn())
vi.mock('@/utils/toast', () => ({
  notify: notifyMock,
  getApiError: (e: unknown, fallback: string) => (e instanceof Error ? e.message : fallback),
}))

const ANCHOR = { top: 300, left: 120, right: 220, bottom: 320, width: 100, height: 20 }

function detail(overrides: Record<string, unknown> = {}) {
  return {
    id: 1,
    headword: 'AQS',
    termType: 'ACRONYM',
    domain: 'CONCURRENCY',
    fullForm: 'AbstractQueuedSynchronizer',
    meaningCn: '抽象队列同步器',
    definitionCn: 'Java 并发包中的同步器框架，常用于构建锁等同步组件。',
    pronunciations: [
      { variant: 'ABBR', readingText: null, locale: 'en-US', readingMode: 'LETTER_BY_LETTER', verificationStatus: 'VERIFIED' },
    ],
    conceptLinks: [
      { targetType: 'DOCUMENT', targetId: 9, relationType: 'RELATED', label: 'Java 并发原理', route: '/docs/9' },
    ],
    relatedTerms: [{ id: 2, headword: 'ReentrantLock', meaningCn: '可重入锁', domain: 'CONCURRENCY', relationType: 'RELATED' }],
    favorite: false,
    learningStatus: 'NEW',
    contentStatus: 'PUBLISHED',
    ...overrides,
  }
}

async function mountPopover(props: Record<string, unknown> = {}) {
  const wrapper = mount(TermLookupPopover, {
    props: { query: 'AQS', anchor: { ...ANCHOR }, ...props },
    global: {
      stubs: {
        teleport: true,
        Icon: true,
        TermPronunciation: true,
        RouterLink: { props: ['to'], template: '<a class="router-link-stub"><slot /></a>' },
      },
    },
  })
  await flushPromises()
  return wrapper
}

describe('TermLookupPopover 统一术语解释浮层', () => {
  beforeEach(() => {
    api.lookupTerm.mockReset()
    api.updateUserState.mockReset()
    api.addToReview.mockReset()
    notifyMock.mockReset()
    routerPush.mockReset()
    authState.isLoggedIn = true
  })

  it('查询中展示轻量 Skeleton，成功后展示词形、全称、释义、发音与知识关联', async () => {
    let resolve: (value: unknown) => void = () => {}
    api.lookupTerm.mockReturnValue(new Promise((r) => { resolve = r }))
    const wrapper = await mountPopover()
    expect(wrapper.find('.tlp-skeleton').exists()).toBe(true)

    resolve({ query: 'AQS', normalized: 'aqs', rejectedReason: null, ambiguous: false, matches: [detail()] })
    await flushPromises()

    expect(api.lookupTerm).toHaveBeenCalledWith('AQS', undefined)
    expect(wrapper.find('.tlp-word').text()).toBe('AQS')
    expect(wrapper.find('.tlp-fullform').text()).toBe('AbstractQueuedSynchronizer')
    expect(wrapper.find('.tlp-meaning').text()).toBe('抽象队列同步器')
    expect(wrapper.find('.tlp-definition').text()).toContain('同步器框架')
    expect(wrapper.text()).toContain('关联知识')
    expect(wrapper.text()).toContain('Java 并发原理')
    expect(wrapper.text()).toContain('ReentrantLock')
    expect(wrapper.find('.tlp-word').classes()).toContain('is-acronym')
    wrapper.unmount()
  })

  it('未收录术语展示清晰空状态，并可跳转搜索或提交待审核术语', async () => {
    api.lookupTerm.mockResolvedValue({ query: 'Zram', normalized: 'zram', rejectedReason: null, ambiguous: false, matches: [] })
    const wrapper = await mountPopover({ query: 'Zram' })

    expect(wrapper.text()).toContain('暂未收录该专业术语')
    expect(wrapper.text()).toContain('搜索相关词汇')
    await wrapper.findAll('button').find((b) => b.text().includes('提交待审核术语'))!.trigger('click')
    expect(wrapper.emitted('open-extract')).toHaveLength(1)
    wrapper.unmount()
  })

  it('超长或跨段选区展示拒绝原因，不展示词条内容', async () => {
    api.lookupTerm.mockResolvedValue({
      query: 'a'.repeat(100),
      normalized: null,
      rejectedReason: '选区过长或跨段落，暂不支持划词查询',
      ambiguous: false,
      matches: [],
    })
    const wrapper = await mountPopover({ query: 'a'.repeat(100) })
    expect(wrapper.text()).toContain('暂不支持查询该选区')
    expect(wrapper.text()).toContain('选区过长或跨段落')
    expect(wrapper.find('.tlp-word').exists()).toBe(false)
    wrapper.unmount()
  })

  it('同形多义展示义项切换，点击后更新当前术语卡', async () => {
    api.lookupTerm.mockResolvedValue({
      query: 'Deadlock',
      normalized: 'deadlock',
      rejectedReason: null,
      ambiguous: true,
      matches: [
        detail({ id: 3, headword: 'Deadlock', domain: 'CONCURRENCY', fullForm: null, termType: 'WORD', meaningCn: '线程死锁' }),
        detail({ id: 4, headword: 'Deadlock', domain: 'DB', fullForm: null, termType: 'WORD', meaningCn: '数据库死锁' }),
      ],
    })
    const wrapper = await mountPopover({ query: 'Deadlock' })

    expect(wrapper.findAll('.tlp-sense')).toHaveLength(2)
    expect(wrapper.text()).toContain('该词形存在多个义项')
    expect(wrapper.find('.tlp-meaning').text()).toBe('线程死锁')

    await wrapper.findAll('.tlp-sense')[1].trigger('click')
    await nextTick()
    expect(wrapper.find('.tlp-meaning').text()).toBe('数据库死锁')
    wrapper.unmount()
  })

  it('收藏采用乐观更新，接口失败时回滚并提示错误', async () => {
    api.lookupTerm.mockResolvedValue({
      query: 'AQS', normalized: 'aqs', rejectedReason: null, ambiguous: false, matches: [detail()],
    })
    api.updateUserState.mockRejectedValue(new Error('网络异常'))
    const wrapper = await mountPopover()

    const favoriteBtn = wrapper.findAll('button').find((b) => b.text().includes('收藏词汇'))!
    await favoriteBtn.trigger('click')
    await flushPromises()

    expect(api.updateUserState).toHaveBeenCalledWith(1, { favorite: true })
    expect(wrapper.findAll('button').find((b) => b.text().includes('收藏词汇'))).toBeTruthy()
    expect(notifyMock).toHaveBeenCalledWith('网络异常', 'error')
    wrapper.unmount()
  })

  it('收藏成功后切换为已收藏状态', async () => {
    api.lookupTerm.mockResolvedValue({
      query: 'AQS', normalized: 'aqs', rejectedReason: null, ambiguous: false, matches: [detail()],
    })
    api.updateUserState.mockResolvedValue({ termId: 1, favorite: true, learningStatus: 'NEW' })
    const wrapper = await mountPopover()

    await wrapper.findAll('button').find((b) => b.text().includes('收藏词汇'))!.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('已收藏')
    expect(notifyMock).toHaveBeenCalledWith('已收藏词汇', 'success')
    wrapper.unmount()
  })

  it('加入复习区分新建与已存在，两种情况都不静默', async () => {
    api.lookupTerm.mockResolvedValue({
      query: 'AQS', normalized: 'aqs', rejectedReason: null, ambiguous: false, matches: [detail()],
    })
    api.addToReview.mockResolvedValueOnce({ cardId: 11, created: true }).mockResolvedValueOnce({ cardId: 11, created: false })
    const wrapper = await mountPopover()

    const reviewBtn = () => wrapper.findAll('button').find((b) => b.text().includes('加入复习'))!
    await reviewBtn().trigger('click')
    await flushPromises()
    expect(notifyMock).toHaveBeenLastCalledWith('已加入复习计划', 'success')

    await reviewBtn().trigger('click')
    await flushPromises()
    expect(notifyMock).toHaveBeenLastCalledWith('该词条已在复习计划中', 'info')
    wrapper.unmount()
  })

  it('未登录时收藏/加入复习先跳登录并携带 redirect 参数', async () => {
    authState.isLoggedIn = false
    api.lookupTerm.mockResolvedValue({
      query: 'AQS', normalized: 'aqs', rejectedReason: null, ambiguous: false, matches: [detail()],
    })
    const wrapper = await mountPopover()

    await wrapper.findAll('button').find((b) => b.text().includes('收藏词汇'))!.trigger('click')
    await flushPromises()
    expect(api.updateUserState).not.toHaveBeenCalled()
    expect(routerPush).toHaveBeenCalledWith({ path: '/login', query: { redirect: '/docs/1' } })

    await wrapper.findAll('button').find((b) => b.text().includes('加入复习'))!.trigger('click')
    await flushPromises()
    expect(api.addToReview).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('Escape 与点击浮层外部关闭；点击浮层内部按钮不关闭', async () => {
    api.lookupTerm.mockResolvedValue({
      query: 'AQS', normalized: 'aqs', rejectedReason: null, ambiguous: false, matches: [detail()],
    })
    const wrapper = await mountPopover()

    wrapper.find('.tlp-actions').element.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    await nextTick()
    expect(wrapper.emitted('close')).toBeUndefined()

    document.body.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    await nextTick()
    expect(wrapper.emitted('close')).toHaveLength(1)

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await nextTick()
    expect(wrapper.emitted('close')).toHaveLength(2)
    wrapper.unmount()
  })

  it('查询词变化时按新词重新查询（同一浮层只展示一个术语）', async () => {
    api.lookupTerm
      .mockResolvedValueOnce({ query: 'AQS', normalized: 'aqs', rejectedReason: null, ambiguous: false, matches: [detail()] })
      .mockResolvedValueOnce({
        query: 'Embedding', normalized: 'embedding', rejectedReason: null, ambiguous: false,
        matches: [detail({ id: 2, headword: 'Embedding', termType: 'WORD', fullForm: null, meaningCn: '向量嵌入', relatedTerms: [], conceptLinks: [] })],
      })
    const wrapper = await mountPopover()
    expect(wrapper.find('.tlp-word').text()).toBe('AQS')

    await wrapper.setProps({ query: 'Embedding' })
    await flushPromises()
    expect(api.lookupTerm).toHaveBeenLastCalledWith('Embedding', undefined)
    expect(wrapper.find('.tlp-word').text()).toBe('Embedding')
    wrapper.unmount()
  })

  it('窄屏下浮层宽度夹在视口内，不横向溢出', async () => {
    const widthSpy = vi.spyOn(window, 'innerWidth', 'get').mockReturnValue(320)
    api.lookupTerm.mockResolvedValue({
      query: 'AQS', normalized: 'aqs', rejectedReason: null, ambiguous: false, matches: [detail()],
    })
    const wrapper = await mountPopover({ anchor: { top: 300, left: 300, right: 310, bottom: 320, width: 10, height: 20 } })

    const card = wrapper.find('.term-lookup-popover').element as HTMLElement
    expect(card.style.width).toBe('296px') // 320 - 12 * 2
    expect(card.style.left).toBe('12px')
    wrapper.unmount()
    widthSpy.mockRestore()
  })

  it('查询失败展示错误与重试入口，不把失败伪装为空数据', async () => {
    api.lookupTerm
      .mockRejectedValueOnce(new Error('服务暂不可用'))
      .mockResolvedValueOnce({ query: 'AQS', normalized: 'aqs', rejectedReason: null, ambiguous: false, matches: [detail()] })
    const wrapper = await mountPopover()

    expect(wrapper.text()).toContain('术语查询失败')
    expect(wrapper.text()).toContain('服务暂不可用')
    await wrapper.findAll('button').find((b) => b.text().includes('重试'))!.trigger('click')
    await flushPromises()
    expect(wrapper.find('.tlp-word').text()).toBe('AQS')
    wrapper.unmount()
  })
})
