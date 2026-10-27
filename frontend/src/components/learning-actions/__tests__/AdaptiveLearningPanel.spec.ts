// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { nextTick } from 'vue'
import AdaptiveLearningPanel from '../AdaptiveLearningPanel.vue'
import type { LearningDashboard, LearningActionDraft, PlanItem } from '@/api/learning-actions'

const api = vi.hoisted(() => ({ dashboard: vi.fn(), preview: vi.fn(), get: vi.fn(), confirm: vi.fn(), regenerate: vi.fn(), cancel: vi.fn(), push: vi.fn(), load: vi.fn() }))
vi.mock('@/api/learning-actions', () => ({ learningActionApi: api }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: api.push }) }))
vi.mock('@/stores/learning', () => ({ useLearningStore: () => ({ load: api.load }) }))
const card: PlanItem = { key: 'flashcard:9', title: 'HashMap：桶长达到8就一定树化吗？', type: 'REVIEW', resourceType: 'FLASHCARD', resourceId: 9, pathId: null, knowledgeId: 3, reason: '本人原Card的next_review_time已到期（2026-10-15T20:54:18）', completionCondition: '揭晓后提交FSRS评分；自评不是客观正确率', estimatedMinutes: 10, priority: 'HIGH' }
const quiz: PlanItem = { ...card, key: 'quiz:8', title: 'HashMap 键契约练习', type: 'PRACTICE', resourceType: 'QUIZ', resourceId: 8, reason: '已审题库资源', completionCondition: '实际作答，打开页面不算完成' }
const planId = 'f7a08b31-b932-4875-9cbf-14b9c40f130c'
function dashboard(): LearningDashboard { return { date: '2026-10-21', zoneId: 'Asia/Shanghai', facts: { dueFlashcards: 3, dueFlashcardsBounded: false, masteryRecordsWithEvidence: 1 }, actions: [card, quiz], weakSignals: [{ ...card, key: 'knowledge:3', resourceType: 'KNOWLEDGE', title: '巩固：HashMap边界', reason: '本人掌握记录标记WEAK且置信度≥30', type: 'THEORY' }], tasks: [], bounded: false, missingData: ['近期考试记录'] } }
function draft(): LearningActionDraft { return { planId, version: 4, expiresAt: '2099-10-21T10:00:00Z', state: 'PREVIEW', source: 'RULE', action: 'CREATE', date: '2026-10-21', zoneId: 'Asia/Shanghai', availableMinutes: 60, tasks: [card, quiz], warnings: [], confirmationToken: 'isolated-confirmation', createdTasks: [] } }
let wrappers: VueWrapper[] = []
function panel() { const w = mount(AdaptiveLearningPanel); wrappers.push(w); return w }
function button(w: VueWrapper, text: string) { const b = w.findAll('button').find(b => b.text() === text); if (!b) throw new Error(`Button missing: ${text}`); return b }
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>(r => { resolve = r }); return { resolve, promise } }
beforeEach(() => { vi.resetAllMocks(); api.dashboard.mockResolvedValue(dashboard()); api.preview.mockResolvedValue(draft()); api.get.mockResolvedValue(draft()); api.load.mockResolvedValue(undefined) })
afterEach(() => { wrappers.forEach(w => w.unmount()); wrappers = [] })

describe('今日行动的用户可见状态与原业务合同', () => {
  it('没有计划时引导预览，不把空记录显示成0/0或掌握结论', async () => {
    api.dashboard.mockResolvedValue({ ...dashboard(), actions: [], weakSignals: [], facts: { dueFlashcards: 0, dueFlashcardsBounded: false, masteryRecordsWithEvidence: 0 } })
    const w = panel(); await flushPromises()
    expect(w.text()).toContain('尚未确认今日行动计划')
    expect(w.text()).not.toMatch(/0\s*\/\s*0/)
    expect(w.text()).toContain('不足以判断')
    expect(w.text()).toContain('暂无到期闪卡')
    expect(w.text()).toContain('暂时没有可用的行动建议')
    expect(w.find('[role="progressbar"]').exists()).toBe(false)
    expect(api.confirm).not.toHaveBeenCalled()
  })

  it('同一候选只保留一个资源入口，标题不是按钮，原顺序与上下文不变', async () => {
    const w = panel(); await flushPromises()
    const suggestions = w.get('[aria-label="行动建议"]')
    expect(suggestions.findAll('h4').map(n => n.text())).toEqual([card.title, quiz.title])
    expect(w.findAll('button').some(b => b.text() === card.title)).toBe(false)
    expect(w.findAll('button').filter(b => b.text() === '复习闪卡')).toHaveLength(1)
    await button(w, '复习闪卡').trigger('click')
    expect(api.push).toHaveBeenLastCalledWith('/learning/flashcards?cardId=9&knowledgeId=3')
    await button(w, '开始练习').trigger('click')
    expect(api.push).toHaveBeenLastCalledWith('/learning/quiz?questionId=8&knowledgeId=3')
    expect(api.confirm).not.toHaveBeenCalled()
  })

  it('原始依据和完成限制仍可展开查看，内部字段只放在折叠详情', async () => {
    const w = panel(); await flushPromises()
    const details = w.get('[aria-label="行动建议"]').findAll('details')
    expect(details).toHaveLength(2)
    expect(details[0]!.attributes('open')).toBeUndefined()
    expect(details[0]!.text()).toContain(card.reason)
    expect(details[0]!.text()).toContain('自评不是客观正确率')
    expect(w.get('[aria-label="巩固提示"]').find('details').text()).toContain('WEAK')
  })

  it.each(['0', '-1', '721', '1.5', ''])('无效可用分钟%s不发出刷新或预览请求', async value => {
    const w = panel(); await flushPromises(); await w.get('[aria-label="今日可用分钟"]').setValue(value)
    expect(w.get('[aria-label="今日可用分钟"]').attributes('aria-invalid')).toBe('true')
    expect(w.get('[role="alert"]').text()).toContain('1–720')
    await button(w, '刷新').trigger('click'); await button(w, '预览今日计划').trigger('click')
    expect(api.dashboard).toHaveBeenCalledTimes(1); expect(api.preview).not.toHaveBeenCalled()
  })

  it('调整时间不悄悄改动已加载建议的预算，刷新后才更新依据', async () => {
    const w = panel(); await flushPromises(); await w.get('[aria-label="今日可用分钟"]').setValue(30)
    expect(w.text()).toContain('按60分钟可用时间安排')
    expect(w.text()).toContain('时间已调整')
    await button(w, '刷新').trigger('click'); await flushPromises()
    expect(api.dashboard).toHaveBeenLastCalledWith(30)
    expect(w.text()).toContain('按30分钟可用时间安排')
  })

  it('首次加载有区域骨架，失败提供重试且不伪装为空数据', async () => {
    const pending = deferred<LearningDashboard>(); api.dashboard.mockReturnValueOnce(pending.promise)
    const w = panel(); await nextTick(); expect(w.find('[data-action-skeleton]').exists()).toBe(true)
    pending.resolve(dashboard()); await flushPromises(); expect(w.find('[data-action-skeleton]').exists()).toBe(false)
    w.unmount(); api.dashboard.mockRejectedValueOnce(new Error('网络断开'))
    const failed = panel(); await flushPromises(); expect(failed.get('[role="alert"]').text()).toContain('网络断开')
    expect(failed.text()).not.toContain('尚未确认今日行动计划')
    await button(failed, '重试读取').trigger('click'); await flushPromises(); expect(failed.text()).toContain(card.title)
  })

  it('刷新失败保留旧内容并明确未更新，重试成功移除旧数据提示', async () => {
    const w = panel(); await flushPromises(); api.dashboard.mockRejectedValueOnce(new Error('刷新失败'))
    await button(w, '刷新').trigger('click'); await flushPromises()
    expect(w.text()).toContain('显示上次成功读取的内容，尚未更新')
    expect(w.get('[aria-label="行动建议"]').text()).toContain(card.title)
    await button(w, '重试读取').trigger('click'); await flushPromises()
    expect(w.text()).not.toContain('显示上次成功读取的内容，尚未更新')
  })

  it('已有计划使用真实完成数，保留截断范围与学习证明语义', async () => {
    api.dashboard.mockResolvedValue({ ...dashboard(), bounded: true, facts: { ...dashboard().facts, dueFlashcards: 50, dueFlashcardsBounded: true }, tasks: [{ id: 1, title: '已完成回忆', status: 1, eventId: 8, item: card }, { id: 2, title: '待做练习', status: 0, eventId: null, item: quiz }] })
    const w = panel(); await flushPromises(); const records = w.get('[aria-label="计划执行记录"]')
    expect(records.text()).toContain('已完成1/2项'); expect(records.text()).toContain('最近30项')
    expect(records.text()).toContain('仅统计已确认的行动计划')
    expect(w.text()).toContain('至少50张')
    expect(w.find('input[type="checkbox"]').exists()).toBe(false)
    expect(records.get('.is-complete').text()).toContain('已完成回忆')
    expect(records.find('details').text()).toContain('学习事件 #8')
  })

  it('全部完成也保留原资源入口，不把任务完成称为知识掌握', async () => {
    api.dashboard.mockResolvedValue({ ...dashboard(), tasks: [{ id: 1, title: '已完成回忆', status: 1, eventId: null, item: card }] })
    const w = panel(); await flushPromises(); const records = w.get('[aria-label="计划执行记录"]')
    expect(records.text()).toContain('已完成1/1项'); expect(records.text()).toContain('本组行动已完成')
    expect(records.text()).toContain('不作为掌握证据')
    await button(w, '查看学习资源').trigger('click'); expect(api.push).toHaveBeenLastCalledWith('/learning/flashcards?cardId=9&knowledgeId=3')
  })

  it('预览处理中只提交一次，沿用原禁用AI及用户确认合同', async () => {
    const pending = deferred<LearningActionDraft>(); api.preview.mockReturnValueOnce(pending.promise)
    const w = panel(); await flushPromises(); await button(w, '预览今日计划').trigger('click')
    await button(w, '正在准备…').trigger('click'); expect(api.preview).toHaveBeenCalledTimes(1)
    expect(api.preview).toHaveBeenCalledWith(expect.objectContaining({ availableMinutes: 60, useAi: false, includeInterview: false }))
    expect(api.confirm).not.toHaveBeenCalled(); pending.resolve(draft()); await flushPromises()
    expect(w.text()).toContain('草案未保存')
    expect(w.get('.task-plan-card details').text()).toContain(card.reason)
    api.confirm.mockResolvedValue({ ...draft(), state: 'CONFIRMED', createdTasks: [{ id: 40, source: 'TASK', title: card.title, status: 0 }] })
    await button(w, '确认创建任务').trigger('click'); await flushPromises()
    expect(api.confirm.mock.calls[0]![1]).toEqual(expect.objectContaining({ version: 4, confirmationToken: 'isolated-confirmation', idempotencyKey: expect.any(String) }))
    expect(w.text()).toContain('已创建 1 项学习任务')
  })

  it('预览失败不会把仍有效的建议标成刷新失败，可单独重试', async () => {
    api.preview.mockRejectedValueOnce(new Error('计划暂不可用'))
    const w = panel(); await flushPromises(); await button(w, '预览今日计划').trigger('click'); await flushPromises()
    expect(w.get('[role="alert"]').text()).toContain('计划暂不可用')
    expect(w.text()).not.toContain('显示上次成功读取的内容，尚未更新')
    await button(w, '重试预览').trigger('click'); await flushPromises(); expect(w.text()).toContain('草案未保存')
  })

  it('无薄弱诊断不等于已经掌握，原缺失记录可查看', async () => {
    api.dashboard.mockResolvedValue({ ...dashboard(), weakSignals: [] })
    const w = panel(); await flushPromises(); expect(w.get('[aria-label="巩固提示"]').text()).toContain('不代表已经掌握')
    expect(w.get('[aria-label="巩固提示"]').find('details').text()).toContain('近期考试记录')
  })
  it('确认先完成、旧刷新后返回时，排队重新读取确认后的真实记录', async () => {
    const w = panel(); await flushPromises(); await button(w, '预览今日计划').trigger('click'); await flushPromises()
    const oldRead = deferred<LearningDashboard>()
    const saved = { ...dashboard(), tasks: [{ id: 40, title: card.title, status: 0, eventId: null, item: card }] }
    api.dashboard.mockReturnValueOnce(oldRead.promise).mockResolvedValueOnce(saved)
    await button(w, '刷新').trigger('click')
    api.confirm.mockResolvedValue({ ...draft(), state: 'CONFIRMED', createdTasks: [{ id: 40, source: 'TASK', title: card.title, status: 0 }] })
    await button(w, '确认创建任务').trigger('click'); await flushPromises()
    oldRead.resolve(dashboard()); await flushPromises()
    expect(w.get('[aria-label="计划执行记录"]').text()).toContain('已完成0/1项')
    expect(api.dashboard).toHaveBeenCalledTimes(3)
  })

  it('外层分钟无效时，确认后使用最后有效预算读取记录，不发送无效分钟', async () => {
    const w = panel(); await flushPromises(); await button(w, '预览今日计划').trigger('click'); await flushPromises()
    await w.get('[aria-label="今日可用分钟"]').setValue('')
    api.dashboard.mockResolvedValue({ ...dashboard(), tasks: [{ id: 40, title: card.title, status: 0, eventId: null, item: card }] })
    api.confirm.mockResolvedValue({ ...draft(), state: 'CONFIRMED', createdTasks: [{ id: 40, source: 'TASK', title: card.title, status: 0 }] })
    await button(w, '确认创建任务').trigger('click'); await flushPromises()
    expect(w.get('[aria-label="计划执行记录"]').text()).toContain('已完成0/1项')
    expect(api.dashboard.mock.calls.map(call => call[0])).toEqual([60, 60])
    expect(w.get('[aria-label="今日可用分钟"]').attributes('aria-invalid')).toBe('true')
  })

})
