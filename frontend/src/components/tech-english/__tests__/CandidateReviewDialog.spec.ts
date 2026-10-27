// @vitest-environment happy-dom
// 管理端候选审核：核对中文释义可补全、默认仅通过不上架、重复审核由服务端幂等处理。
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import CandidateReviewDialog from '../CandidateReviewDialog.vue'

const listCandidates = vi.fn()
const reviewCandidate = vi.fn()

vi.mock('@/api/techEnglish', () => ({
  adminTechEnglishApi: {
    listCandidates: (...args: unknown[]) => listCandidates(...args),
    reviewCandidate: (...args: unknown[]) => reviewCandidate(...args),
  },
}))

vi.mock('@/utils/toast', () => ({
  notify: vi.fn(),
  getApiError: (_e: unknown, fallback: string) => fallback,
}))

const AppDialogStub = {
  props: ['open', 'title', 'description'],
  template: '<div v-if="open" class="dialog-stub"><slot /></div>',
}

const IconStub = { template: '<i />' }

const candidate = {
  id: 21,
  headword: 'Deadlock',
  termType: 'WORD',
  domain: 'CONCURRENCY',
  extractor: 'RULE',
  meaningCn: null,
  fullForm: null,
  definitionCn: null,
  duplicateTermId: null,
  sourceType: 'DOCUMENT',
  sourceId: 7,
  sourceTitle: '并发编程入门',
  sourceContext: 'A deadlock happens when two threads wait on each other.',
}

function mountDialog() {
  return mount(CandidateReviewDialog, {
    props: { open: false },
    global: { stubs: { AppDialog: AppDialogStub, Icon: IconStub } },
  })
}

describe('候选词条审核弹窗', () => {
  beforeEach(() => {
    listCandidates.mockReset().mockResolvedValue([candidate])
    reviewCandidate.mockReset().mockResolvedValue({})
  })

  it('打开时读取 PENDING 候选并展示来源与原文上下文', async () => {
    const w = mountDialog()
    await w.setProps({ open: true })
    await flushPromises()
    expect(listCandidates).toHaveBeenCalledWith('PENDING')
    expect(w.text()).toContain('Deadlock')
    expect(w.text()).toContain('并发编程入门')
    expect(w.text()).toContain('deadlock happens')
    w.unmount()
  })

  it('审核通过时提交补全的中文释义，默认不上架公共词库', async () => {
    const w = mountDialog()
    await w.setProps({ open: true })
    await flushPromises()

    const meaning = w.get('input.cr-meaning-input')
    expect((meaning.element as HTMLInputElement).value).toBe('')
    await meaning.setValue('死锁')

    const approve = w.findAll('button').find((b) => b.text() === '通过')!
    await approve.trigger('click')
    await flushPromises()

    expect(reviewCandidate).toHaveBeenCalledWith(21, {
      approve: true,
      publish: false,
      meaningCn: '死锁',
      domain: 'CONCURRENCY',
      reviewNote: null,
    })
    expect(w.findAll('.cr-item')).toHaveLength(0)
    w.unmount()
  })

  it('勾选同时发布后审核通过会带上 publish=true', async () => {
    const w = mountDialog()
    await w.setProps({ open: true })
    await flushPromises()

    await w.get('.cr-publish input[type="checkbox"]').setValue(true)
    await w.get('input.cr-meaning-input').setValue('死锁')
    await w.findAll('button').find((b) => b.text() === '通过')!.trigger('click')
    await flushPromises()

    expect(reviewCandidate.mock.calls[0][1]).toMatchObject({ approve: true, publish: true, meaningCn: '死锁' })
    w.unmount()
  })

  it('拒绝候选时不上架公共词库', async () => {
    const w = mountDialog()
    await w.setProps({ open: true })
    await flushPromises()

    await w.findAll('button').find((b) => b.text() === '拒绝')!.trigger('click')
    await flushPromises()

    expect(reviewCandidate.mock.calls[0][1]).toMatchObject({ approve: false, publish: false })
    expect(w.findAll('.cr-item')).toHaveLength(0)
    w.unmount()
  })
})
