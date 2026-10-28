// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import TermEditorDialog from '../TermEditorDialog.vue'

const api = vi.hoisted(() => ({
  createTerm: vi.fn(),
  updateTerm: vi.fn(),
  getTerm: vi.fn(),
}))

vi.mock('@/api/techEnglish', () => ({
  adminTechEnglishApi: {
    createTerm: api.createTerm,
    updateTerm: api.updateTerm,
    getTerm: api.getTerm,
  },
}))
vi.mock('@/utils/toast', () => ({
  getApiError: (_: unknown, fallback: string) => fallback,
}))

const DETAIL = {
  id: 7,
  headword: 'AQS',
  termType: 'ACRONYM' as const,
  domain: 'CONCURRENCY',
  meaningCn: '抽象队列同步器',
  contentStatus: 'PUBLISHED' as const,
  fullForm: 'AbstractQueuedSynchronizer',
  className: 'java.util.concurrent.locks.AbstractQueuedSynchronizer',
  partOfSpeech: 'noun',
  ipaUs: '/əˈbstrækt/',
  ipaUk: null,
  definitionCn: '通过状态位与队列实现同步器骨架。',
  definitionEn: 'A framework for blocking locks.',
  exampleEn: 'AQS is the backbone of most locks.',
  exampleCn: 'AQS 是多数锁的基础。',
  interviewEn: 'AQS provides the synchronization skeleton.',
  breakdownNote: '整体概念不是各单词含义的机械相加。',
  aliases: ['抽象队列同步器'],
  referenceSource: 'OpenJDK 17 源码',
  licenseNote: '项目原创整理',
  wordBreakdown: [{ word: 'Abstract', meaningCn: '抽象的' }],
  pronunciations: [
    {
      id: 11,
      variant: 'ABBR' as const,
      readingText: 'A Q S',
      locale: 'en-US',
      readingMode: 'LETTER_BY_LETTER' as const,
      audioUrl: null,
      audioProvider: null,
      licenseNote: null,
      verificationStatus: 'VERIFIED' as const,
    },
  ],
  conceptLinks: [],
  relatedTerms: [],
  favorite: false,
  learningStatus: 'NEW' as const,
}

function mountDialog(props: Record<string, unknown> = {}) {
  return mount(TermEditorDialog, {
    props: { open: true, ...props },
    global: { stubs: { Teleport: true } },
  })
}

function field(wrapper: VueWrapper, label: string) {
  const node = wrapper
    .findAll('.te-field')
    .find((item) => item.get('.te-label').text().startsWith(label))
  if (!node) throw new Error(`未找到字段：${label}`)
  return node
}

beforeEach(() => {
  vi.clearAllMocks()
  api.createTerm.mockResolvedValue({ ...DETAIL })
  api.updateTerm.mockResolvedValue({ ...DETAIL })
  api.getTerm.mockResolvedValue({ ...DETAIL })
})

describe('专业英语新建/编辑词条弹窗', () => {
  it('所有业务字段保留，且 Label 位于控件外部', () => {
    const wrapper = mountDialog()
    const labels = wrapper.findAll('.te-field .te-label').map((node) => node.text())
    for (const name of [
      '词条类型',
      '主词 / 缩写',
      '领域',
      '义项键',
      '中文短释义',
      '审核状态',
      '英文全称',
      '对应 Java 类名',
      '词性',
      '美音音标',
      '英音音标',
      '中文技术解释',
      '英文技术解释',
      '技术例句',
      '例句中文释义',
      '面试英文表达',
      '别名',
      '参考来源',
      '授权/版权说明',
      '拆解说明',
    ]) {
      expect(labels.some((text) => text.startsWith(name)), `缺少字段：${name}`).toBe(true)
    }

    // 控件必须与 Label 同级（不在灰底容器内部），且 Select 走全局 .kf-select
    for (const item of wrapper.findAll('.te-field')) {
      expect(item.find('.te-label').exists()).toBe(true)
      const control = item.find('input, select, textarea')
      expect(control.exists()).toBe(true)
      expect(control.element.parentElement?.classList.contains('te-field')).toBe(true)
    }
    expect(wrapper.findAll('select.kf-select').length).toBeGreaterThan(0)
    expect(wrapper.find('.kf-field').exists()).toBe(false)
    wrapper.unmount()
  })

  it('缩写与发布状态才出现必填星号', async () => {
    const wrapper = mountDialog()
    const requiredBefore = wrapper.findAll('.te-req').length
    await field(wrapper, '词条类型').get('select').setValue('ACRONYM')
    await field(wrapper, '审核状态').get('select').setValue('PUBLISHED')
    expect(wrapper.findAll('.te-req').length).toBe(requiredBefore + 2)
    wrapper.unmount()
  })

  it('保存调用既有创建接口，取消只触发关闭事件', async () => {
    const wrapper = mountDialog()
    await field(wrapper, '主词 / 缩写').get('input').setValue('Throughput')
    await field(wrapper, '领域').get('input').setValue('concurrency')
    await field(wrapper, '中文短释义').get('input').setValue('吞吐量')

    await wrapper.get('.dlg-btn--primary').trigger('click')
    await flushPromises()
    expect(api.createTerm).toHaveBeenCalledWith(
      expect.objectContaining({
        headword: 'Throughput',
        domain: 'CONCURRENCY',
        meaningCn: '吞吐量',
        termType: 'WORD',
        contentStatus: 'DRAFT',
      }),
    )
    expect(wrapper.emitted('saved')).toBeTruthy()

    await wrapper.get('.dlg-btn--cancel').trigger('click')
    expect(wrapper.emitted('close')).toBeTruthy()
    wrapper.unmount()
  })

  it('必填缺失时不调用接口并保留错误提示', async () => {
    const wrapper = mountDialog()
    await wrapper.get('.dlg-btn--primary').trigger('click')
    await flushPromises()
    expect(api.createTerm).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('请填写主词或缩写')
    wrapper.unmount()
  })

  it('编辑已有词条时回填全部字段', async () => {
    const wrapper = mount(TermEditorDialog, {
      props: { open: false, termId: 7 },
      global: { stubs: { Teleport: true } },
    })
    await wrapper.setProps({ open: true })
    await flushPromises()
    await flushPromises()
    expect(field(wrapper, '主词 / 缩写').get('input').element.value).toBe('AQS')
    expect(field(wrapper, '英文全称').get('input').element.value).toBe(
      'AbstractQueuedSynchronizer',
    )
    expect(field(wrapper, '中文技术解释').get('textarea').element.value).toContain('同步器骨架')
    wrapper.unmount()
  })
})
