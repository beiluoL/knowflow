// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { ref } from 'vue'
import TechEnglishSpeak from '../TechEnglishSpeak.vue'
import type { TermDetail, TermSummary } from '@/api/techEnglish'

const api = vi.hoisted(() => ({ searchTerms: vi.fn(), getTerm: vi.fn() }))
vi.mock('@/api/techEnglish', () => ({
  techEnglishApi: { searchTerms: api.searchTerms, getTerm: api.getTerm },
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

const SUMMARY: TermSummary = {
  id: 2,
  headword: 'Lambda Expression',
  termType: 'PHRASE',
  domain: 'JAVA',
  meaningCn: 'Lambda 表达式',
  fullForm: null,
  hasVerifiedPronunciation: true,
  pronunciationVariants: ['WORD_US'],
}

const DETAIL: TermDetail = {
  id: 2,
  headword: 'Lambda Expression',
  termType: 'PHRASE',
  domain: 'JAVA',
  meaningCn: 'Lambda 表达式',
  fullForm: null,
  ipaUs: null,
  ipaUk: null,
  definitionCn: '用一段表达式实现函数式接口的唯一抽象方法。',
  exampleEn: 'The executor accepts a lambda that retries the failed job three times.',
  exampleCn: '线程池接收一个 Lambda，用于把失败任务重试三次。',
  contentStatus: 'PUBLISHED',
  pronunciations: [
    {
      id: 1,
      variant: 'WORD_US',
      readingText: 'Lambda Expression',
      locale: 'en-US',
      readingMode: 'SPOKEN_WORD',
      audioUrl: null,
      verificationStatus: 'VERIFIED',
    },
  ],
  conceptLinks: [],
  relatedTerms: [],
  favorite: false,
  learningStatus: 'NEW',
}

class FakeMediaRecorder {
  static isTypeSupported() {
    return true
  }

  state = 'inactive'
  mimeType = 'audio/webm'
  ondataavailable: ((event: { data: Blob }) => void) | null = null
  onerror: (() => void) | null = null
  onstop: (() => void) | null = null

  start() {
    this.state = 'recording'
    this.ondataavailable?.({ data: new Blob(['voice'], { type: 'audio/webm' }) })
  }

  stop() {
    this.state = 'inactive'
    this.onstop?.()
  }
}

function stubMedia(recorder: unknown, getUserMedia: (constraints: unknown) => Promise<unknown>) {
  vi.stubGlobal('MediaRecorder', recorder)
  Object.defineProperty(navigator, 'mediaDevices', {
    configurable: true,
    value: { getUserMedia: vi.fn(getUserMedia) },
  })
}

async function mountPage(query = '') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/learning/english/speak', name: 'TechEnglishSpeak', component: TechEnglishSpeak },
      { path: '/learning/english/:id', name: 'TechEnglishDetail', component: { template: '<div />' } },
      { path: '/learning/english', name: 'TechEnglishHome', component: { template: '<div />' } },
    ],
  })
  await router.push(`/learning/english/speak${query}`)
  await router.isReady()
  const wrapper = mount(TechEnglishSpeak, { global: { plugins: [createPinia(), router] } })
  await flushPromises()
  return { wrapper, router }
}

describe('TechEnglishSpeak 跟读练习', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    speech.play.mockResolvedValue(true)
    api.searchTerms.mockResolvedValue({
      records: [SUMMARY],
      total: 1,
      pageNum: 1,
      pageSize: 8,
      pages: 1,
    })
    api.getTerm.mockResolvedValue({ ...DETAIL })
    Object.assign(URL, {
      createObjectURL: vi.fn(() => 'blob:mock-recording'),
      revokeObjectURL: vi.fn(),
    })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('未选择词条时展示空状态而不是伪造练习内容', async () => {
    stubMedia(FakeMediaRecorder, async () => ({ getTracks: () => [] }))
    const { wrapper } = await mountPage()
    expect(wrapper.text()).toContain('先选择一个词条')
    expect(api.getTerm).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('带 termId 深链直接加载词条并说明录音不上传', async () => {
    stubMedia(FakeMediaRecorder, async () => ({ getTracks: () => [] }))
    const { wrapper } = await mountPage('?termId=2')
    expect(api.getTerm).toHaveBeenCalledWith(2)
    expect(wrapper.text()).toContain('Lambda Expression')
    expect(wrapper.text()).toContain('不上传')
    expect(wrapper.text()).toContain('不做自动发音评分')
    wrapper.unmount()
  })

  it('搜索后选择词条会写入 URL 并加载对应详情', async () => {
    stubMedia(FakeMediaRecorder, async () => ({ getTracks: () => [] }))
    const { wrapper, router } = await mountPage()
    await wrapper.get('input[type="search"]').setValue('Lambda')
    await wrapper.get('input[type="search"]').trigger('input')
    await new Promise((resolve) => setTimeout(resolve, 360))
    await flushPromises()
    expect(api.searchTerms).toHaveBeenCalledWith(expect.objectContaining({ q: 'Lambda', pageSize: 8 }))
    const result = wrapper.get('.te-result')
    await result.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.termId).toBe('2')
    expect(api.getTerm).toHaveBeenCalledWith(2)
    wrapper.unmount()
  })

  it('浏览器不支持录音时给出提示并禁用开始按钮', async () => {
    vi.stubGlobal('MediaRecorder', undefined)
    const { wrapper } = await mountPage('?termId=2')
    expect(wrapper.text()).toContain('当前浏览器不支持录音')
    const start = wrapper.findAll('button').find((b) => b.text().includes('开始录音'))
    expect(start!.attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })

  it('麦克风权限被拒绝时保留错误提示，不假装录音成功', async () => {
    stubMedia(FakeMediaRecorder, async () => {
      throw Object.assign(new Error('denied'), { name: 'NotAllowedError' })
    })
    const { wrapper } = await mountPage('?termId=2')
    const start = wrapper.findAll('button').find((b) => b.text().includes('开始录音'))
    await start!.trigger('click')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('麦克风权限被拒绝')
    expect(wrapper.find('audio').exists()).toBe(false)
    wrapper.unmount()
  })

  it('录音开始显示录音中，停止后生成本地回放且不调用任何上传接口', async () => {
    const stopTrack = vi.fn()
    stubMedia(FakeMediaRecorder, async () => ({ getTracks: () => [{ stop: stopTrack }] }))
    const { wrapper } = await mountPage('?termId=2')
    await wrapper.findAll('button').find((b) => b.text().includes('开始录音'))!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('录音中')

    await wrapper.findAll('button').find((b) => b.text().includes('停止录音'))!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('已录音')
    expect(wrapper.get('audio').attributes('src')).toBe('blob:mock-recording')
    expect(stopTrack).toHaveBeenCalled()
    // 只调用了检索与详情接口，没有任何上传/评分调用
    expect(api.searchTerms).not.toHaveBeenCalled()
    expect(api.getTerm).toHaveBeenCalledWith(2)
    wrapper.unmount()
  })
})
