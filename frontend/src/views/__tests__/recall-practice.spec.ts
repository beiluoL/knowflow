// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import * as api from '@/api/workbench'
import Practice from '../WorkbenchRecallPractice.vue'
import Dialog from '@/components/workbench/RecallSessionDialog.vue'
import type { WbRecallSession } from '@/api/types'

vi.mock('@/api/workbench', () => ({
  getRecallSession: vi.fn(),
  listRecallSessions: vi.fn(),
  createRecallSession: vi.fn(),
  updateRecallSession: vi.fn(),
  submitRecallRound: vi.fn(),
  saveRecallDraft: vi.fn(),
  saveRecallGaps: vi.fn(),
  copyRecallSession: vi.fn(),
  archiveRecallSession: vi.fn(),
  deleteRecallSession: vi.fn(),
  listNotes: vi.fn(),
}))

vi.mock('@/utils/toast', () => ({
  notify: vi.fn(),
  confirmDialog: vi.fn().mockResolvedValue(true),
  getApiError: (_: unknown, fallback: string) => fallback,
}))

const SOURCE = 'HashMap 基于数组和链表实现，扩容时重新计算索引。'

function makeSession(overrides: Partial<WbRecallSession> = {}): WbRecallSession {
  return {
    id: 5,
    title: 'Java HashMap 底层原理',
    sourceText: SOURCE,
    sourceType: 'PASTE',
    sourceLabel: '粘贴原文',
    archived: 0,
    currentRound: 1,
    status: 'IN_PROGRESS',
    scoreTrend: [],
    improvementPct: [],
    serverNow: new Date().toISOString(),
    ...overrides,
  }
}

function iso(ms: number) {
  return new Date(ms).toISOString()
}

async function renderPractice(id = 5) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/workbench/recall/:id', component: Practice },
      { path: '/workbench/recall', component: { template: '<div />' } },
    ],
  })
  await router.push(`/workbench/recall/${id}`)
  const wrapper = mount({ template: '<RouterView />' }, {
    global: { plugins: [router], stubs: { Teleport: true } },
  })
  await flushPromises()
  return { wrapper, router }
}

async function renderDialog(props: { open: boolean; session: WbRecallSession | null }) {
  const wrapper = mount(Dialog, { props, global: { stubs: { Teleport: true } } })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(api.listNotes).mockResolvedValue([])
})

describe('三轮闭卷默写 · 专注练习页', () => {
  it('第一轮默认闭卷：不渲染原文，并回填未提交草稿', async () => {
    vi.mocked(api.getRecallSession).mockResolvedValue(makeSession({ round1Draft: '我记住的片段' }))

    const { wrapper } = await renderPractice()

    const editor = wrapper.find('[data-test="recall-writing"]')
    expect(editor.exists()).toBe(true)
    expect((editor.element as HTMLTextAreaElement).value).toBe('我记住的片段')
    expect(wrapper.html()).not.toContain('扩容时重新计算索引')
    expect(wrapper.find('[data-test="recall-comparison-1"]').exists()).toBe(false)
  })

  it('第一轮提交成功后生成对比，并进入第二轮准备（先确认遗漏）', async () => {
    vi.mocked(api.getRecallSession).mockResolvedValue(makeSession())
    vi.mocked(api.submitRecallRound).mockResolvedValue(makeSession({
      round1Text: '数组和链表',
      round1Score: 42,
      gapJson: '{"1":["扩容"]}',
    }))

    const { wrapper } = await renderPractice()
    await wrapper.find('[data-test="recall-writing"]').setValue('数组和链表')
    await wrapper.find('[data-test="recall-submit"]').trigger('click')
    await flushPromises()

    expect(api.submitRecallRound).toHaveBeenCalledWith(5, { round: 1, text: '数组和链表' })
    expect(wrapper.find('[data-test="recall-comparison-1"]').exists()).toBe(true)
    const gate = wrapper.find('[data-test="recall-round2-gate"]')
    expect(gate.exists()).toBe(true)
    expect(gate.text()).toContain('扩容')
    // 第二轮未开始前不出现默写编辑区
    expect(wrapper.find('[data-test="recall-writing"]').exists()).toBe(false)
  })

  it('第一轮提交失败时保留编辑内容，不伪造完成状态', async () => {
    vi.mocked(api.getRecallSession).mockResolvedValue(makeSession())
    vi.mocked(api.submitRecallRound).mockRejectedValue(new Error('network'))

    const { wrapper } = await renderPractice()
    await wrapper.find('[data-test="recall-writing"]').setValue('未提交成功的内容')
    await wrapper.find('[data-test="recall-submit"]').trigger('click')
    await flushPromises()

    expect((wrapper.find('[data-test="recall-writing"]').element as HTMLTextAreaElement).value)
      .toBe('未提交成功的内容')
    expect(wrapper.find('[data-test="recall-comparison-1"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="recall-round2-gate"]').exists()).toBe(false)
  })

  it('第二轮先展示遗漏清单，点击后才进入闭卷编辑并隐藏原文与对比', async () => {
    vi.mocked(api.getRecallSession).mockResolvedValue(makeSession({
      round1Text: '数组和链表',
      round1Score: 40,
      gapJson: '{"1":["扩容"]}',
    }))

    const { wrapper } = await renderPractice()
    expect(wrapper.find('[data-test="recall-round2-gate"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="recall-writing"]').exists()).toBe(false)

    await wrapper.find('[data-test="recall-start-round2"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-test="recall-writing"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="recall-round2-gate"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="recall-comparison-1"]').exists()).toBe(false)
    expect(wrapper.html()).not.toContain('扩容时重新计算索引')
    // 仅保留关键词线索，不展示完整答案
    expect(wrapper.find('[data-test="recall-writing-card"]').text()).toContain('扩容')
    expect((wrapper.find('[data-test="recall-writing"]').element as HTMLTextAreaElement).value).toBe('')
  })

  it('第二轮提交后生成独立记录，并进入第三轮等待门禁', async () => {
    const nowMs = Date.now()
    vi.mocked(api.getRecallSession).mockResolvedValue(makeSession({
      round1Text: '数组和链表',
      round1Score: 40,
      gapJson: '{"1":["扩容"]}',
    }))
    vi.mocked(api.submitRecallRound).mockResolvedValue(makeSession({
      round1Text: '数组和链表',
      round2Text: '数组和链表，扩容时重新计算索引',
      round2Score: 76,
      round3DueTime: iso(nowMs + 60 * 60 * 1000),
      serverNow: iso(nowMs),
    }))

    const { wrapper } = await renderPractice()
    await wrapper.find('[data-test="recall-start-round2"]').trigger('click')
    await flushPromises()
    await wrapper.find('[data-test="recall-writing"]').setValue('数组和链表，扩容时重新计算索引')
    await wrapper.find('[data-test="recall-submit"]').trigger('click')
    await flushPromises()

    expect(api.submitRecallRound).toHaveBeenCalledWith(5, {
      round: 2,
      text: '数组和链表，扩容时重新计算索引',
    })
    expect(wrapper.find('[data-test="recall-round3-waiting"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="recall-start-round3"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="recall-writing"]').exists()).toBe(false)
  })

  it('第三轮未到期：只显示等待倒计时与开放时间，不开放正式提交', async () => {
    const nowMs = Date.now()
    vi.mocked(api.getRecallSession).mockResolvedValue(makeSession({
      round1Text: '数组和链表',
      round2Text: '数组和链表，扩容',
      round3DueTime: iso(nowMs + 30 * 60 * 1000),
      serverNow: iso(nowMs),
    }))

    const { wrapper } = await renderPractice()

    const waiting = wrapper.find('[data-test="recall-round3-waiting"]')
    expect(waiting.exists()).toBe(true)
    expect(waiting.text()).toMatch(/\d{2}:\d{2}:\d{2}/)
    expect(waiting.text()).toContain('开放时间')
    expect(wrapper.find('[data-test="recall-start-round3"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="recall-writing"]').exists()).toBe(false)
  })

  it('第三轮到期后可开始复测，提交后展示三轮完成结果', async () => {
    const nowMs = Date.now()
    vi.mocked(api.getRecallSession).mockResolvedValue(makeSession({
      round1Text: '数组和链表',
      round2Text: '数组和链表，扩容',
      round1Score: 40,
      round2Score: 72,
      round3DueTime: iso(nowMs - 60 * 1000),
      serverNow: iso(nowMs),
    }))
    vi.mocked(api.submitRecallRound).mockResolvedValue(makeSession({
      status: 'COMPLETED',
      round1Text: '数组和链表',
      round2Text: '数组和链表，扩容',
      round3Text: '数组和链表，扩容时重新计算索引',
      round1Score: 40,
      round2Score: 72,
      round3Score: 91,
      completedTime: iso(nowMs),
    }))

    const { wrapper } = await renderPractice()
    expect(wrapper.find('[data-test="recall-start-round3"]').exists()).toBe(true)

    await wrapper.find('[data-test="recall-start-round3"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-test="recall-writing"]').exists()).toBe(true)

    await wrapper.find('[data-test="recall-writing"]').setValue('数组和链表，扩容时重新计算索引')
    await wrapper.find('[data-test="recall-submit"]').trigger('click')
    await flushPromises()

    expect(api.submitRecallRound).toHaveBeenCalledWith(5, {
      round: 3,
      text: '数组和链表，扩容时重新计算索引',
    })
    const result = wrapper.find('[data-test="recall-result"]')
    expect(result.exists()).toBe(true)
    expect(result.text()).toContain('91')
    expect(result.text()).toContain('文本覆盖度')
  })

  it('三轮完成页区分持续遗忘、正确补回与个人确认薄弱点', async () => {
    vi.mocked(api.getRecallSession).mockResolvedValue(makeSession({
      status: 'COMPLETED',
      round1Text: '数组和链表',
      round2Text: '数组和链表，扩容',
      round3Text: '数组和链表扩容机制',
      round1Score: 30,
      round2Score: 60,
      round3Score: 88,
      gapJson: '{"1":["扩容","重新计算索引"]}',
      completedTime: iso(Date.now()),
    }))

    const { wrapper } = await renderPractice()
    const result = wrapper.find('[data-test="recall-result"]')

    expect(result.exists()).toBe(true)
    expect(result.text()).toContain('持续遗忘知识')
    expect(result.text()).toContain('重新计算索引')
    expect(result.text()).toContain('正确补回知识')
    expect(result.text()).toContain('扩容')
    expect(result.text()).toContain('你确认的关键薄弱点')
    expect(result.text()).toContain('不会自动修改已有的 SM-2')
    // 已完成会话仍可查看三轮对比
    expect(wrapper.find('[data-test="recall-comparison-3"]').exists()).toBe(true)
  })
})

describe('默写会话弹窗 · 新建与编辑', () => {
  it('新建：保存草稿以 draft=true 创建，且不进入第一轮', async () => {
    vi.mocked(api.createRecallSession).mockResolvedValue(9)

    const wrapper = await renderDialog({ open: true, session: null })
    await wrapper.find('[data-test="recall-title"]').setValue('HashMap 原理')
    await wrapper.find('[data-test="recall-source"]').setValue('数组 + 链表 + 红黑树')
    await wrapper.find('[data-test="recall-save-draft"]').trigger('click')
    await flushPromises()

    expect(api.createRecallSession).toHaveBeenCalledWith(expect.objectContaining({
      title: 'HashMap 原理',
      sourceText: '数组 + 链表 + 红黑树',
      draft: true,
    }))
    expect(wrapper.emitted('saved')?.[0]?.[0]).toEqual({ id: 9, started: false })
  })

  it('新建：开始第一轮以 draft=false 创建，成功后通知进入练习', async () => {
    vi.mocked(api.createRecallSession).mockResolvedValue(11)

    const wrapper = await renderDialog({ open: true, session: null })
    await wrapper.find('[data-test="recall-title"]').setValue('HashMap 原理')
    await wrapper.find('[data-test="recall-source"]').setValue('数组 + 链表')
    await wrapper.find('[data-test="recall-start"]').trigger('click')
    await flushPromises()

    expect(api.createRecallSession).toHaveBeenCalledWith(expect.objectContaining({ draft: false }))
    expect(wrapper.emitted('saved')?.[0]?.[0]).toEqual({ id: 11, started: true })
  })

  it('新建：原文为空时阻止提交', async () => {
    const wrapper = await renderDialog({ open: true, session: null })
    await wrapper.find('[data-test="recall-title"]').setValue('只有标题')
    await wrapper.find('[data-test="recall-start"]').trigger('click')
    await flushPromises()

    expect(api.createRecallSession).not.toHaveBeenCalled()
    expect(wrapper.emitted('saved')).toBeUndefined()
  })

  it('新建：创建失败时保留用户输入，不发出完成事件', async () => {
    vi.mocked(api.createRecallSession).mockRejectedValue(new Error('boom'))

    const wrapper = await renderDialog({ open: true, session: null })
    await wrapper.find('[data-test="recall-title"]').setValue('HashMap 原理')
    await wrapper.find('[data-test="recall-source"]').setValue('数组 + 链表')
    await wrapper.find('[data-test="recall-start"]').trigger('click')
    await flushPromises()

    expect((wrapper.find('[data-test="recall-title"]').element as HTMLInputElement).value).toBe('HashMap 原理')
    expect((wrapper.find('[data-test="recall-source"]').element as HTMLTextAreaElement).value).toBe('数组 + 链表')
    expect(wrapper.emitted('saved')).toBeUndefined()
  })

  it('草稿会话：可编辑标题与原文，并可直接开始第一轮', async () => {
    vi.mocked(api.updateRecallSession).mockResolvedValue(makeSession({ status: 'DRAFT' }))

    const wrapper = await renderDialog({
      open: true,
      session: makeSession({ status: 'DRAFT', title: '旧标题', sourceText: '旧原文' }),
    })
    expect((wrapper.find('[data-test="recall-source"]').element as HTMLTextAreaElement).readOnly).toBe(false)

    await wrapper.find('[data-test="recall-title"]').setValue('新标题')
    await wrapper.find('[data-test="recall-source"]').setValue('新原文')
    await wrapper.find('[data-test="recall-start"]').trigger('click')
    await flushPromises()

    expect(api.updateRecallSession).toHaveBeenCalledWith(5, expect.objectContaining({
      title: '新标题',
      sourceText: '新原文',
    }))
    expect(wrapper.emitted('saved')?.[0]?.[0]).toEqual({ id: 5, started: true })
  })

  it('已开始会话：锁定原文快照，仅保留复制为新会话入口', async () => {
    const wrapper = await renderDialog({
      open: true,
      session: makeSession({ status: 'IN_PROGRESS', round1Text: '第一轮内容' }),
    })

    expect((wrapper.find('[data-test="recall-source"]').element as HTMLTextAreaElement).readOnly).toBe(true)
    expect(wrapper.find('[data-test="recall-lock-hint"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="recall-start"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="recall-save"]').exists()).toBe(true)

    await wrapper.find('[data-test="recall-copy"]').trigger('click')
    expect(wrapper.emitted('copy')).toBeTruthy()
  })
})
