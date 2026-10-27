// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import ExamDiagnosisPanel from '../ExamDiagnosisPanel.vue'
const api = vi.hoisted(() => ({ diagnosis: vi.fn(), compose: vi.fn(), startAttempt: vi.fn() }))
vi.mock('@/api/examDiagnosis', () => ({ examDiagnosisApi: { get: api.diagnosis } }))
vi.mock('@/api/exam', () => ({ examApi: api, QUESTION_TYPE_LABELS: { SINGLE_CHOICE: '单选', MULTIPLE_CHOICE: '多选', TRUE_FALSE: '判断', FILL_BLANK: '填空', SHORT_ANSWER: '简答', CODE: '编程' }, TOPIC_LABELS: { JVM: 'JVM' } }))
vi.mock('@/utils/toast', () => ({ getApiError: (error: Error) => error.message }))
const topic = { topicCode: 'JVM', answeredCount: 1, gradedCount: 1, wrongCount: 1, unansweredCount: 1, ungradedCount: 1, evidenceLevel: 'INSUFFICIENT', observations: ['仅有一次已评观察'], evidence: [{ attemptId: 5, paperQuestionId: 40, title: 'GC 的观察题', difficulty: 2, correct: false, answered: true, graded: true }, { attemptId: 5, paperQuestionId: 41, title: '未答题', difficulty: null, correct: null, answered: false, graded: false }], practiceCandidates: [{ catalogId: 'QUIZ:8', questionId: 8, title: 'GC 候选题', difficulty: 1, questionType: 'SINGLE_CHOICE' }, { catalogId: 'QUIZ:9', questionId: 9, title: '判断候选题', difficulty: 2, questionType: 'TRUE_FALSE' }], materials: [{ knowledgeId: 12, name: '垃圾回收基础' }], recommendedCount: 2, retestAfter: '2026-10-14T10:00:00' }
const diagnosis = { ruleVersion: 'exam-diagnosis-v1', attemptId: 5, scope: 'EXAM_OBSERVATIONS_NOT_GLOBAL_MASTERY', objectiveAnsweredCount: 1, objectiveCorrectCount: 0, objectiveAccuracy: 0, unansweredCount: 1, ungradedCount: 1, topics: [topic], comparison: { previousAttemptId: 4, comparable: false, currentObjectiveAccuracy: 0, previousObjectiveAccuracy: null, note: '样本不足，不进行能力提升比较' } }
beforeEach(() => { vi.resetAllMocks(); api.diagnosis.mockResolvedValue(diagnosis); api.compose.mockResolvedValue({ id: 77 }); api.startAttempt.mockResolvedValue({ attemptId: 88 }) })
async function open() {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/exam/result/:attemptId', component: { template: '<div />' } }, { path: '/exam/take/:attemptId', component: { template: '<div />' } }, { path: '/learning/knowledge-mastery/:id', component: { template: '<div />' } }] })
  await router.push('/exam/result/5'); await router.isReady()
  const wrapper = mount(ExamDiagnosisPanel, { props: { attemptId: 5 }, global: { plugins: [router] } }); await flushPromises(); return { wrapper, router }
}
describe('real exam observation panel', () => {
  it('loads evidence without claiming mastery, separates unanswered and ungraded, links real materials safely', async () => {
    const { wrapper, router } = await open(); expect(api.diagnosis).toHaveBeenCalledWith(5)
    const text = wrapper.text(); expect(text).toContain('考试观察'); expect(text).toContain('全局掌握度'); expect(text).toContain('样本不足'); expect(text).toContain('未答 1'); expect(text).toContain('未评 1'); expect(text).toContain('GC 的观察题'); expect(text).toContain('2026-10-14 10:00')
    await wrapper.findAll('button').find(button => button.text() === '垃圾回收基础')!.trigger('click'); await flushPromises(); expect(router.currentRoute.value.path).toBe('/learning/knowledge-mastery/12'); wrapper.unmount()
  })
  it('shows API failure and retries rather than inventing an empty diagnosis', async () => {
    api.diagnosis.mockRejectedValueOnce(new Error('诊断网络失败')); const { wrapper } = await open()
    expect(wrapper.find('[role="alert"]').text()).toContain('诊断网络失败'); expect(wrapper.text()).not.toContain('没有可展示的考试观察')
    await wrapper.find('.kb-state-retry').trigger('click'); await flushPromises(); expect(wrapper.text()).toContain('GC 的观察题'); wrapper.unmount()
  })
  it('renders truthful unknown accuracy, missing material mappings and no candidate disabled action', async () => {
    api.diagnosis.mockResolvedValueOnce({ ...diagnosis, objectiveAccuracy: null, topics: [{ ...topic, materials: [], practiceCandidates: [], recommendedCount: 0, retestAfter: null }] })
    const { wrapper } = await open(); expect(wrapper.text()).toContain('正确率暂不可计算'); expect(wrapper.text()).toContain('暂无关联学习资料'); expect(wrapper.text()).toContain('没有可用复测候选题')
    expect(wrapper.findAll('button').find(button => button.text().includes('专项复测'))!.attributes('disabled')).toBeDefined(); expect(api.compose).not.toHaveBeenCalled(); wrapper.unmount()
  })
  it('explicit retest uses only real candidate type counts and starts existing exam take flow', async () => {
    const { wrapper, router } = await open(); expect(api.compose).not.toHaveBeenCalled()
    await wrapper.findAll('button').find(button => button.text().includes('专项复测'))!.trigger('click'); await flushPromises()
    expect(api.compose).toHaveBeenCalledWith(expect.objectContaining({ strategy: 'RANDOM', seed: 42, mode: 'PRACTICE', topics: ['JVM'], questionCount: 2, typeDistribution: { SINGLE_CHOICE: 1, TRUE_FALSE: 1 } }))
    expect(api.startAttempt).toHaveBeenCalledWith(77); expect(router.currentRoute.value.path).toBe('/exam/take/88'); wrapper.unmount()
  })
  it('caps practice counts by actual candidate availability, never fabricating requested questions', async () => {
    api.diagnosis.mockResolvedValueOnce({ ...diagnosis, topics: [{ ...topic, recommendedCount: 10 }] }); const { wrapper } = await open()
    await wrapper.findAll('button').find(button => button.text().includes('专项复测'))!.trigger('click'); await flushPromises()
    expect(api.compose.mock.calls[0]![0].questionCount).toBe(2); wrapper.unmount()
  })
  it('limits a retest to three even when more real candidates are available', async () => {
    api.diagnosis.mockResolvedValueOnce({ ...diagnosis, topics: [{ ...topic, recommendedCount: 5, practiceCandidates: [...topic.practiceCandidates, { catalogId: 'QUIZ:10', questionId: 10, title: '第三候选题', difficulty: 1, questionType: 'SHORT_ANSWER' }, { catalogId: 'QUIZ:11', questionId: 11, title: '第四候选题', difficulty: 1, questionType: 'SINGLE_CHOICE' }] }] })
    const { wrapper } = await open(); await wrapper.findAll('button').find(button => button.text().includes('专项复测'))!.trigger('click'); await flushPromises()
    expect(api.compose.mock.calls[0]![0]).toMatchObject({ questionCount: 3, typeDistribution: { SINGLE_CHOICE: 1, TRUE_FALSE: 1, SHORT_ANSWER: 1 } }); wrapper.unmount()
  })
  it('failed composition stays visible and retry never starts a fake attempt', async () => {
    api.compose.mockRejectedValueOnce(new Error('候选题已不可用'))
    const { wrapper, router } = await open(); await wrapper.findAll('button').find(button => button.text().includes('专项复测'))!.trigger('click'); await flushPromises()
    expect(wrapper.find('[role="alert"]').text()).toContain('候选题已不可用'); expect(api.startAttempt).not.toHaveBeenCalled(); expect(router.currentRoute.value.path).toBe('/exam/result/5')
    await wrapper.findAll('button').find(button => button.text() === '重试专项复测')!.trigger('click'); await flushPromises(); expect(router.currentRoute.value.path).toBe('/exam/take/88'); wrapper.unmount()
  })
  it('retry after attempt creation failure reuses the already composed real paper', async () => {
    api.startAttempt.mockRejectedValueOnce(new Error('开始作答失败'))
    const { wrapper } = await open(); await wrapper.findAll('button').find(button => button.text().includes('专项复测'))!.trigger('click'); await flushPromises()
    expect(wrapper.find('[role="alert"]').text()).toContain('开始作答失败')
    await wrapper.findAll('button').find(button => button.text() === '重试专项复测')!.trigger('click'); await flushPromises()
    expect(api.compose).toHaveBeenCalledTimes(1); expect(api.startAttempt).toHaveBeenNthCalledWith(2, 77); wrapper.unmount()
  })
  it('comparison uses the actual previous attempt route and states non-comparability', async () => {
    const { wrapper, router } = await open(); expect(wrapper.text()).toContain(diagnosis.comparison.note)
    await wrapper.findAll('button').find(button => button.text() === '查看上次报告')!.trigger('click'); await flushPromises(); expect(router.currentRoute.value.path).toBe('/exam/result/4'); wrapper.unmount()
  })
  it('ignores stale diagnosis when the attempt prop changes', async () => {
    let resolveOld!: (value: unknown) => void
    api.diagnosis.mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve }))
    const { wrapper } = await open(); api.diagnosis.mockResolvedValueOnce({ ...diagnosis, attemptId: 6, topics: [{ ...topic, observations: ['新的考试观察'] }] })
    await wrapper.setProps({ attemptId: 6 }); await flushPromises(); resolveOld({ ...diagnosis, topics: [{ ...topic, observations: ['过期考试观察'] }] }); await flushPromises()
    expect(wrapper.text()).toContain('新的考试观察'); expect(wrapper.text()).not.toContain('过期考试观察'); wrapper.unmount()
  })
  it('uses a real empty state when diagnosis topics are absent', async () => {
    api.diagnosis.mockResolvedValueOnce({ ...diagnosis, topics: [] }); const { wrapper } = await open(); expect(wrapper.text()).toContain('没有可展示的考试观察'); wrapper.unmount()
  })
})
