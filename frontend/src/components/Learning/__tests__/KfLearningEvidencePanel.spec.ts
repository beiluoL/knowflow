// @vitest-environment happy-dom
import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
const api = vi.hoisted(() => ({ page: vi.fn(), compare: vi.fn() }))
vi.mock('@/api/learningEvidence', () => ({ learningEvidenceApi: api }))
import Panel from '../KfLearningEvidencePanel.vue'
beforeEach(() => vi.clearAllMocks())
describe('evidence report states', () => {
  it('shows real failure and retry without pretending the history is empty', async () => {
    api.page.mockRejectedValueOnce(new Error('暂时无法读取证据')).mockResolvedValueOnce({ items: [], page: 1, size: 10, total: 0, unavailableCount: 0, hasMore: false })
    const w = mount(Panel); await flushPromises()
    expect(w.find('[role="alert"]').text()).toContain('暂时无法读取证据')
    await w.findAll('button').find(b => b.text().includes('重试'))!.trigger('click'); await flushPromises()
    expect(w.text()).toContain('暂无已捕获的学习证据')
    w.unmount()
  })
  it('preserves zero and distinguishes self recall from exam marks', async () => {
    api.page.mockResolvedValue({ items: [{ sourceType: 'QUIZ_ANSWER', sourceId: 1, resourceKey: 'QUIZ:1', score: 0, maxScore: 100, assessmentKind: 'OBJECTIVE', firstSourceId: 1, isFirstKnownAttempt: true, snapshotState: 'FROZEN', answer: '原回答', occurredAt: '2026-10-25', knowledgeKeys: [] }, { sourceType: 'FLASHCARD_REVIEW', sourceId: 2, score: null, maxScore: null, selfRating: 3, assessmentKind: 'SELF_REPORTED', firstSourceId: 2, snapshotState: 'FROZEN', occurredAt: '2026-10-25', knowledgeKeys: [] }], hasMore: false, unavailableCount: 0 })
    const w = mount(Panel); await flushPromises()
    expect(w.text()).toContain('0 / 100')
    expect(w.text()).toContain('自评 3')
    expect(w.text()).toContain('不代表客观掌握度')
    expect(w.text()).toContain('原回答')
    w.unmount()
  })

  it('does not label an incomplete legacy history as an original first attempt', async () => {
    api.page.mockResolvedValue({ items: [{ sourceType: 'QUIZ_ANSWER', sourceId: 8, score: 0, maxScore: 100, assessmentKind: 'OBJECTIVE', firstSourceId: 3, isFirstKnownAttempt: false, firstAttemptRef: null, historyCompleteness: 'INCOMPLETE', snapshotState: 'FROZEN', answer: '保留原文', occurredAt: '2026-10-26', knowledgeKeys: [] }], hasMore: false, unavailableCount: 0 })
    const w = mount(Panel); await flushPromises()
    expect(w.text()).toContain('已知首条不等于原始首轮')
    expect(w.text()).toContain('0 / 100')
    w.unmount()
  })
})
