// @vitest-environment happy-dom
import { beforeEach, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import ExamResultPage from '../ExamResultPage.vue'
const api = vi.hoisted(() => ({ result: vi.fn(), aiGrade: vi.fn(), rejudgeCode: vi.fn() }))
vi.mock('@/api/exam', () => ({ examApi: api, QUESTION_TYPE_LABELS: {}, TOPIC_LABELS: {} }))
vi.mock('@/utils/toast', () => ({ notify: vi.fn(), getApiError: (error: Error) => error.message }))
vi.mock('../ExamDiagnosisPanel.vue', () => ({ default: { props: ['attemptId', 'refreshKey'], template: '<section data-diagnosis>观察报告 #{{ attemptId }}</section>' } }))
const result = (id: number) => ({ attemptId: id, paperId: id, paperName: `真实报告 ${id}`, mode: 'PRACTICE', status: 'GRADED', gradingStatus: 'GRADED', aiAvailable: false, totalScore: 0, maxScore: 10, paperMaxScore: 10, passScore: 6, passed: false, correctCount: 0, gradedCount: 0, questionCount: 0, typeAnalysis: [], topicAnalysis: [], items: [], startTime: '2026-10-13T10:00:00' })
beforeEach(() => { vi.resetAllMocks(); api.result.mockImplementation((id: number) => Promise.resolve(result(id))) })
async function open() {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/exam/result/:attemptId', component: ExamResultPage }] })
  await router.push('/exam/result/5'); await router.isReady()
  const wrapper = mount(ExamResultPage, { global: { plugins: [router], stubs: { MiniAppShell: { template: '<div><slot /></div>' } } } }); await flushPromises(); return { wrapper, router }
}
it('attaches observations to the actual result and reloads when previous report navigation reuses the page', async () => {
  const { wrapper, router } = await open(); expect(wrapper.find('[data-diagnosis]').text()).toBe('观察报告 #5')
  await router.push('/exam/result/4'); await flushPromises()
  expect(api.result).toHaveBeenLastCalledWith(4); expect(wrapper.text()).toContain('真实报告 4'); expect(wrapper.find('[data-diagnosis]').text()).toBe('观察报告 #4'); wrapper.unmount()
})
it('invalid result ID never calls result or renders an invented diagnosis', async () => {
  const { wrapper, router } = await open(); api.result.mockClear(); await router.push('/exam/result/invalid'); await flushPromises()
  expect(api.result).not.toHaveBeenCalled(); expect(wrapper.find('[data-diagnosis]').exists()).toBe(false); expect(wrapper.text()).toContain('报告 ID 无效'); wrapper.unmount()
})
it('does not label mixed graded-item accuracy objective or show zero percent for no graded sample', async () => {
  const { wrapper } = await open()
  const label = wrapper.findAll('p').find(node => node.text() === '已评分题目正确率')
  expect(label).toBeDefined()
  const card = label!.element.parentElement!
  expect(card.textContent).toContain('—'); expect(card.textContent).not.toContain('0%'); wrapper.unmount()
})

it('a late previous report never replaces the newly requested result', async () => {
  let resolveOld!: (value: ReturnType<typeof result>) => void
  api.result.mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve }))
  const { wrapper, router } = await open(); await router.push('/exam/result/4'); await flushPromises()
  resolveOld(result(5)); await flushPromises(); expect(wrapper.text()).toContain('真实报告 4'); expect(wrapper.text()).not.toContain('真实报告 5'); expect(wrapper.find('[data-diagnosis]').text()).toBe('观察报告 #4'); wrapper.unmount()
})
