// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import QuizMgmt from '../QuizMgmt.vue'
import ScenarioMgmt from '../ScenarioMgmt.vue'
import CodeQuestionMgmt from '../CodeQuestionMgmt.vue'
import CodeProblemMgmt from '../CodeProblemMgmt.vue'
const api = vi.hoisted(() => ({ quizQuestions: vi.fn(), quizQuestionDetail: vi.fn(), scenarios: vi.fn(), scenarioDetail: vi.fn(), codeQuestions: vi.fn(), codeQuestionDetail: vi.fn(), learningCategories: vi.fn(), list: vi.fn(), detail: vi.fn(), stats: vi.fn() }))
vi.mock('@/api', () => ({ adminApi: api }))
vi.mock('@/api/admin', () => ({ adminApi: api }))
vi.mock('@/api/codeBank', () => ({ adminCodeProblemApi: api, DIFFICULTY_LABELS: { 1: '简单' } }))
vi.mock('@/utils/toast', () => ({ notify: vi.fn(), getApiError: () => '请求失败', confirmDialog: vi.fn(), promptDialog: vi.fn() }))
const common = { id: 91, title: '目录直达专业题目', questionType: 'SINGLE_CHOICE', content: '专业题干', answer: '0', options: '["A","B"]', description: '专业代码题干', status: 0, difficulty: 1, keyPoints: [{ label: '要点', keywords: ['真实'] }], contextText: '场景', questionText: '问题', cases: [], problemJson: '{}' }
beforeEach(() => {
  vi.clearAllMocks(); api.quizQuestions.mockResolvedValue({ records: [], total: 0 }); api.scenarios.mockResolvedValue([]); api.codeQuestions.mockResolvedValue([]); api.learningCategories.mockResolvedValue([]); api.list.mockResolvedValue({ records: [], total: 0, page: 1, size: 20, pages: 0 }); api.stats.mockResolvedValue({})
  for (const method of [api.quizQuestionDetail, api.scenarioDetail, api.codeQuestionDetail, api.detail]) method.mockResolvedValue(common)
})
async function open(component: typeof QuizMgmt, query = '?questionId=91') {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/source', component }] })
  await router.push('/source' + query); await router.isReady()
  const wrapper = mount(component, { global: { plugins: [router] } }); await flushPromises(); return { wrapper, router }
}
describe('catalog links preserve specialized source editors', () => {
  for (const [name, component, detail] of [['Quiz', QuizMgmt, api.quizQuestionDetail], ['Scenario', ScenarioMgmt, api.scenarioDetail], ['Legacy code', CodeQuestionMgmt, api.codeQuestionDetail], ['Code bank', CodeProblemMgmt, api.detail]] as const) {
    it(`${name} create shortcut opens the original empty professional form`, async () => {
      const { wrapper } = await open(component, '?create=1')
      expect(detail).not.toHaveBeenCalled()
      expect(wrapper.findAll('input').some(input => input.element.value === common.title)).toBe(false)
      const expectedHeading = { Quiz: '手动新增题目', Scenario: '新建场景', 'Legacy code': '新增题目', 'Code bank': '新建算法题' }[name]
      expect(wrapper.findAll('h2,h3').some(heading => heading.text() === expectedHeading)).toBe(true)
      wrapper.unmount()
    })
    it(`${name} loads full detail independently of empty source list`, async () => {
      const { wrapper } = await open(component); expect(detail).toHaveBeenCalledWith(91)
      expect(wrapper.findAll('input').some(input => input.element.value === common.title)).toBe(true); wrapper.unmount()
    })
  }
  it('generic quiz editor refuses a code-bank detail', async () => {
    api.quizQuestionDetail.mockResolvedValueOnce({ ...common, questionType: 'CODE' })
    const { wrapper } = await open(QuizMgmt)
    expect(wrapper.findAll('input').some(input => input.element.value === common.title)).toBe(false); wrapper.unmount()
  })
  it('invalid ID never opens an arbitrary resource', async () => {
    const { wrapper } = await open(QuizMgmt, '?questionId=91oops'); expect(api.quizQuestionDetail).not.toHaveBeenCalled(); wrapper.unmount()
  })
  it('changing source ID cancels stale detail from a prior link', async () => {
    let resolveOld!: (value: typeof common) => void
    api.quizQuestionDetail.mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve }))
    const { wrapper, router } = await open(QuizMgmt)
    await router.push('/source?questionId=92'); await flushPromises()
    resolveOld({ ...common, title: '过期题目' }); await flushPromises()
    expect(wrapper.findAll('input').some(input => input.element.value === '过期题目')).toBe(false); wrapper.unmount()
  })
})
