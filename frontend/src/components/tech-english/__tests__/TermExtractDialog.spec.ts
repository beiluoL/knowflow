// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import TermExtractDialog from '../TermExtractDialog.vue'

const authState = vi.hoisted(() => ({ isLoggedIn: true }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => authState }))

const api = vi.hoisted(() => ({
  extractCandidates: vi.fn(),
  saveCandidates: vi.fn(),
}))
vi.mock('@/api/techEnglish', () => ({ techEnglishApi: api }))

const notifyMock = vi.hoisted(() => vi.fn())
vi.mock('@/utils/toast', () => ({
  notify: notifyMock,
  getApiError: (e: unknown, fallback: string) => (e instanceof Error ? e.message : fallback),
}))

vi.mock('@/components/ui/AppDialog.vue', () => ({
  default: {
    props: ['open', 'title', 'description', 'width', 'scrollable', 'hideFooter'],
    emits: ['cancel'],
    template:
      '<div v-if="open" class="dialog-stub"><header>{{ title }}</header><p>{{ description }}</p><slot /></div>',
  },
}))

const ITEM_LIVENESS = {
  headword: 'Liveness',
  termType: 'WORD',
  domain: 'CONCURRENCY',
  fullForm: null,
  meaningCn: null,
  definitionCn: null,
  sourceContext: 'Liveness ensures the system keeps making progress.',
  confidence: 40,
  extractor: 'RULE',
  existsInLibrary: false,
  duplicateTermId: null,
  duplicateHeadword: null,
}
const ITEM_AQS = {
  ...ITEM_LIVENESS,
  headword: 'AQS',
  termType: 'ACRONYM',
  fullForm: 'AbstractQueuedSynchronizer',
  sourceContext: 'The AQS framework helps implement synchronizers.',
  confidence: 95,
  existsInLibrary: true,
  duplicateTermId: 1,
  duplicateHeadword: 'AQS',
}

function extraction(overrides: Record<string, unknown> = {}) {
  return {
    extractor: 'RULE',
    aiAvailable: false,
    degradationNote: '未配置可用 AI 模型，本次为规则提取，不代表 AI 结果',
    totalScanned: 120,
    truncated: false,
    items: [ITEM_LIVENESS, ITEM_AQS],
    ...overrides,
  }
}

async function mountDialog(props: Record<string, unknown> = {}) {
  const wrapper = mount(TermExtractDialog, {
    props: {
      open: true,
      sourceType: 'DOCUMENT',
      sourceId: 9,
      sourceTitle: 'Java 并发原理',
      articleText: 'The AQS framework helps implement synchronizers in Java.',
      ...props,
    },
    global: { stubs: { Icon: true } },
  })
  await flushPromises()
  return wrapper
}

function buttonByText(wrapper: ReturnType<typeof mount>, text: string) {
  return wrapper.findAll('button').find((b) => b.text().includes(text))!
}

describe('TermExtractDialog 专业词汇提取与候选确认', () => {
  beforeEach(() => {
    api.extractCandidates.mockReset()
    api.saveCandidates.mockReset()
    notifyMock.mockReset()
    authState.isLoggedIn = true
  })

  it('打开时默认提取整篇文章；有选区时默认使用选区，并说明审核边界', async () => {
    const article = await mountDialog()
    expect(article.text()).toContain('未经审核不会进入公共词库')
    expect(buttonByText(article, '当前文章').classes()).toContain('is-active')
    expect(buttonByText(article, '当前选区').attributes('disabled')).toBeDefined()
    article.unmount()

    const selection = await mountDialog({ selectionText: 'The AQS framework' })
    expect(buttonByText(selection, '当前选区').classes()).toContain('is-active')
    selection.unmount()
  })

  it('整篇提取只传文档 ID，由服务端按知识库权限读取正文', async () => {
    api.extractCandidates.mockResolvedValue(extraction())
    const wrapper = await mountDialog()

    await buttonByText(wrapper, '开始提取').trigger('click')
    await flushPromises()

    expect(api.extractCandidates).toHaveBeenCalledWith({
      documentId: 9,
      scope: 'ARTICLE',
      types: ['WORD', 'ACRONYM', 'PHRASE'],
      limit: 20,
      useAi: false,
    })
    wrapper.unmount()
  })

  it('章节教材（TEXT 来源）整篇提取传正文，选区提取只传选区文本', async () => {
    api.extractCandidates.mockResolvedValue(extraction())
    const article = await mountDialog({ sourceType: 'TEXT', sourceId: null, sourceTitle: '并发章节' })
    await buttonByText(article, '开始提取').trigger('click')
    await flushPromises()
    expect(api.extractCandidates).toHaveBeenLastCalledWith({
      text: 'The AQS framework helps implement synchronizers in Java.',
      scope: 'ARTICLE',
      types: ['WORD', 'ACRONYM', 'PHRASE'],
      limit: 20,
      useAi: false,
    })
    article.unmount()

    const selection = await mountDialog({ sourceType: 'TEXT', sourceId: null, selectionText: 'AbstractQueuedSynchronizer' })
    await buttonByText(selection, '开始提取').trigger('click')
    await flushPromises()
    expect(api.extractCandidates).toHaveBeenLastCalledWith({
      text: 'AbstractQueuedSynchronizer',
      scope: 'SELECTION',
      types: ['WORD', 'ACRONYM', 'PHRASE'],
      limit: 20,
      useAi: false,
    })
    selection.unmount()
  })

  it('候选预览如实标注规则提取/AI 建议与降级说明，词库已收录项默认不勾选', async () => {
    api.extractCandidates.mockResolvedValue(extraction())
    const wrapper = await mountDialog()
    await buttonByText(wrapper, '开始提取').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('规则提取')
    expect(wrapper.text()).toContain('未配置可用 AI 模型')
    expect(wrapper.text()).toContain('词库已收录：AQS')
    const checks = wrapper.findAll('.te-check input')
    expect(checks).toHaveLength(2)
    // 顺序：Liveness（未收录，勾选）、AQS（已收录，不勾选）
    expect((checks[0].element as HTMLInputElement).checked).toBe(true)
    expect((checks[1].element as HTMLInputElement).checked).toBe(false)
    expect(wrapper.text()).toContain('提交待审核（1）')
    wrapper.unmount()
  })

  it('用户可修改释义与领域后提交，提交的是个人待审候选而非公共词条', async () => {
    api.extractCandidates.mockResolvedValue(extraction())
    api.saveCandidates.mockResolvedValue({ saved: 1, skipped: 0, items: [] })
    const wrapper = await mountDialog()
    await buttonByText(wrapper, '开始提取').trigger('click')
    await flushPromises()

    const meaningInput = wrapper.findAll('input').find((i) => i.attributes('placeholder') === '人工补全释义')!
    await meaningInput.setValue('活性（系统持续取得进展）')
    await wrapper.find('select').setValue('DISTRIBUTED')

    // 预览态下不存在设置页的 AI 复选框，只有每行候选的勾选框；AQS 已收录默认不勾选。
    expect(wrapper.findAll('input[type="checkbox"]')).toHaveLength(2)
    await buttonByText(wrapper, '提交待审核').trigger('click')
    await flushPromises()

    expect(api.saveCandidates).toHaveBeenCalledTimes(1)
    const payload = api.saveCandidates.mock.calls[0][0]
    expect(payload).toHaveLength(1)
    expect(payload[0]).toMatchObject({
      headword: 'Liveness',
      termType: 'WORD',
      domain: 'DISTRIBUTED',
      meaningCn: '活性（系统持续取得进展）',
      sourceType: 'DOCUMENT',
      sourceId: 9,
      sourceTitle: 'Java 并发原理',
      extractor: 'RULE',
    })
    expect(wrapper.text()).toContain('已保存 1 条个人待审候选')
    expect(wrapper.text()).toContain('审核通过前不会进入公共词库')
    wrapper.unmount()
  })

  it('重复提交由服务端幂等去重，页面明确提示没有新增', async () => {
    api.extractCandidates.mockResolvedValue(extraction())
    api.saveCandidates.mockResolvedValue({ saved: 0, skipped: 1, items: [] })
    const wrapper = await mountDialog()
    await buttonByText(wrapper, '开始提取').trigger('click')
    await flushPromises()
    await buttonByText(wrapper, '提交待审核').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('已保存 0 条个人待审候选')
    expect(wrapper.text()).toContain('1 条因重复被跳过')
    expect(notifyMock).toHaveBeenCalledWith('没有新增候选（可能已在个人候选列表中）', 'info')
    wrapper.unmount()
  })

  it('移除候选项后不再计入提交数量，空结果展示可操作的空状态', async () => {
    api.extractCandidates.mockResolvedValue(extraction({ items: [ITEM_LIVENESS] }))
    const wrapper = await mountDialog()
    await buttonByText(wrapper, '开始提取').trigger('click')
    await flushPromises()

    await wrapper.find('.te-remove').trigger('click')
    expect(wrapper.text()).toContain('提交待审核（0）')
    expect(buttonByText(wrapper, '提交待审核').attributes('disabled')).toBeDefined()
    wrapper.unmount()

    api.extractCandidates.mockResolvedValue(extraction({ items: [] }))
    const empty = await mountDialog()
    await buttonByText(empty, '开始提取').trigger('click')
    await flushPromises()
    expect(empty.text()).toContain('未提取到可用候选')
    empty.unmount()
  })

  it('提取失败与保存失败都显示真实错误，不静默吞掉', async () => {
    api.extractCandidates.mockRejectedValueOnce(new Error('文档不可访问'))
    const wrapper = await mountDialog()
    await buttonByText(wrapper, '开始提取').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('文档不可访问')

    api.extractCandidates.mockResolvedValue(extraction())
    await buttonByText(wrapper, '开始提取').trigger('click')
    await flushPromises()

    api.saveCandidates.mockRejectedValueOnce(new Error('候选保存失败：网络超时'))
    await buttonByText(wrapper, '提交待审核').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('候选保存失败：网络超时')
    wrapper.unmount()
  })

  it('「返回设置」清空预览，可重新选择范围与类型', async () => {
    api.extractCandidates.mockResolvedValue(extraction())
    const wrapper = await mountDialog()
    await buttonByText(wrapper, '开始提取').trigger('click')
    await flushPromises()
    expect(wrapper.find('.te-result').exists()).toBe(true)

    await buttonByText(wrapper, '返回设置').trigger('click')
    expect(wrapper.find('.te-result').exists()).toBe(false)
    expect(wrapper.find('.te-scope').exists()).toBe(true)
    wrapper.unmount()
  })

  it('至少保留一种词条类型，避免提交必然为空的请求', async () => {
    const wrapper = await mountDialog()
    const chips = () => wrapper.findAll('.te-chip').slice(2) // 前两个是范围 chips
    await chips()[0].trigger('click')
    await chips()[1].trigger('click')
    expect(chips().filter((c) => c.classes().includes('is-active'))).toHaveLength(1)
    await chips()[2].trigger('click') // 只剩一种时点击不应取消
    expect(chips().filter((c) => c.classes().includes('is-active'))).toHaveLength(1)
    wrapper.unmount()
  })
})
