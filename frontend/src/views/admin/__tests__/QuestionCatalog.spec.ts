// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import QuestionCatalog from '../QuestionCatalog.vue'
import type { CatalogQuestion } from '@/api/questionCatalog'
const api = vi.hoisted(() => ({ list: vi.fn(), detail: vi.fn(), review: vi.fn(), batchStatus: vi.fn(), judgeStatus: vi.fn(), facets: vi.fn(), audit: vi.fn(), confirm: vi.fn(), categories: vi.fn() }))
vi.mock('@/api/questionCatalog', () => ({ questionCatalogApi: api, CATALOG_EDITORS: { QUIZ: '/admin/quiz', SCENARIO: '/admin/scenarios', LEGACY_CODE: '/admin/code-questions', CODE_PROBLEM: '/admin/code-problems' } }))
vi.mock('@/api/admin', () => ({ adminApi: { learningCategories: api.categories } }))
vi.mock('@/utils/toast', () => ({ notify: vi.fn(), getApiError: (e: Error) => e.message, confirmDialog: api.confirm }))
const question: CatalogQuestion = { editToken: 'reviewed-content-token', sourceVersion: 3, catalogId: 'QUIZ:90', sourceType: 'QUIZ', sourceQuestionId: 90, questionType: 'SINGLE_CHOICE', title: '分页外的真实题目', status: 0, reviewStatus: 'PENDING_REVIEW' }
const result = (records = [question]) => ({ records, total: 42, pageNum: 2, pageSize: 20, pages: 3 })
async function open(query = '') {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/admin/questions', component: QuestionCatalog }, { path: '/admin/quiz', component: { template: '<div />' } }] })
  await router.push('/admin/questions' + query); await router.isReady()
  const wrapper = mount(QuestionCatalog, { global: { plugins: [router] } }); await flushPromises()
  return { wrapper, router }
}
beforeEach(() => { vi.clearAllMocks(); api.confirm.mockReset().mockResolvedValue(true); api.list.mockResolvedValue(result()); api.detail.mockResolvedValue({ item: question, payload: {} }); api.categories.mockResolvedValue([]); api.facets.mockResolvedValue({ topics: [] }); api.audit.mockResolvedValue({ records: [], total: 0, pageNum: 1, pageSize: 20, pages: 0 }) })
describe('unified question catalog', () => {
  it('filter submission preserves browser history for Back',async()=>{const {wrapper,router}=await open('?keyword=alpha');await wrapper.findAll('.catalog-tabs button').find(x=>x.text()==='场景')!.trigger('click');await flushPromises();router.back();await flushPromises();expect(router.currentRoute.value.query.questionType).toBeUndefined();expect(router.currentRoute.value.query.keyword).toBe('alpha');wrapper.unmount()})
  it('requests server pagination and all URL filters, links source ID rather than catalog ID', async () => {
    const { wrapper } = await open('?page=2&keyword=%E7%9C%9F%E5%AE%9E&sourceType=QUIZ&difficulty=2&status=0&reviewStatus=PENDING_REVIEW&categoryId=8')
    expect(api.list).toHaveBeenCalledWith({ page: 2, pageSize: 20, keyword: '真实', sourceType: 'QUIZ', difficulty: 2, status: 0, reviewStatus: 'PENDING_REVIEW', categoryId: 8 })
    expect(wrapper.text()).toContain('共 42 项'); expect(wrapper.find('.catalog-actions a').attributes('href')).toBe('/admin/quiz?questionId=90')
    expect(wrapper.text()).toContain('v3'); wrapper.unmount()
  })
  it('changing type resets page and browser back restores filters', async () => {
    const { wrapper, router } = await open('?page=2')
    await wrapper.findAll('.catalog-tabs button').find(b => b.text() === '场景')!.trigger('click'); await flushPromises()
    expect(router.currentRoute.value.query).toMatchObject({ page: '1', questionType: 'SCENARIO' })
    expect(api.list).toHaveBeenLastCalledWith({ page: 1, pageSize: 20, questionType: 'SCENARIO' })
    await router.push('/admin/questions?page=2&sourceType=CODE_PROBLEM'); await flushPromises(); router.back(); await flushPromises()
    expect(wrapper.find('.catalog-tabs button[aria-pressed="true"]').text()).toBe('场景'); wrapper.unmount()
  })
  it('a rejected request is an error, retry can reach a genuine empty result', async () => {
    api.list.mockRejectedValueOnce(new Error('网络异常')); const { wrapper } = await open()
    expect(wrapper.find('[role="alert"]').text()).toContain('网络异常'); expect(wrapper.text()).not.toContain('没有符合条件的题目')
    api.list.mockResolvedValueOnce(result([])); await wrapper.find('.kf-state-retry').trigger('click'); await flushPromises()
    expect(wrapper.text()).toContain('没有符合条件的题目'); wrapper.unmount()
  })
  it('late previous response never replaces newest filter results', async () => {
    let resolveOld!: (value: ReturnType<typeof result>) => void
    api.list.mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve }))
    const { wrapper, router } = await open(); await router.push('/admin/questions?sourceType=SCENARIO'); await flushPromises()
    expect(wrapper.text()).toContain(question.title)
    resolveOld(result([{ ...question, title: '过期结果' }])); await flushPromises()
    expect(wrapper.text()).not.toContain('过期结果'); expect(wrapper.text()).toContain(question.title); wrapper.unmount()
  })
  it('taxonomy reads actual topics and clicking a topic updates URL and server filter', async () => {
    api.facets.mockResolvedValueOnce({ topics: [{ code: 'JAVA_BASIC', name: 'Java 基础', parentCode: 'JAVA', questionCount: 12 }] })
    const { wrapper, router } = await open('?view=taxonomy&page=2')
    expect(api.facets).toHaveBeenCalledOnce(); expect(wrapper.find('.catalog-topics').text()).toContain('Java 基础'); expect(wrapper.find('.catalog-topics').text()).toContain('12')
    await wrapper.find('.catalog-topics button').trigger('click'); await flushPromises()
    expect(router.currentRoute.value.query).toMatchObject({ view: 'taxonomy', page: '1', topicCode: 'JAVA_BASIC' })
    expect(api.list).toHaveBeenLastCalledWith({ page: 1, pageSize: 20, topicCode: 'JAVA_BASIC' }); wrapper.unmount()
  })
  it('audit errors retry and real audit pages display actor, identity and before/after evidence', async () => {
    api.audit.mockRejectedValueOnce(new Error('审计加载失败')).mockResolvedValueOnce({ records: [{ id: 4, actorId: 9, sourceType: 'QUIZ', sourceQuestionId: 90, action: 'BASIC_EDIT', beforeState: '{"title":"原题"}', afterState: '{"title":"新题"}', createdAt: '2026-10-13T15:01:21.123456' }], total: 21, pageNum: 1, pageSize: 20, pages: 2 }).mockRejectedValueOnce(new Error('第二页失败')).mockResolvedValueOnce({ records: [{ id: 5, actorId: 9, sourceType: 'QUIZ', sourceQuestionId: 90, action: 'PUBLISH', beforeState: null, afterState: '{"status":1}', createdAt: '2026-10-13T15:02:21' }], total: 21, pageNum: 2, pageSize: 20, pages: 2 })
    const { wrapper } = await open('?view=versions')
    expect(wrapper.find('.catalog-audit [role="alert"]').text()).toContain('审计加载失败')
    await wrapper.find('.catalog-audit .kf-state-retry').trigger('click'); await flushPromises()
    const text = wrapper.find('.catalog-audit').text(); expect(text).toContain('#9'); expect(text).toContain('BASIC_EDIT'); expect(text).toContain('原题'); expect(text).toContain('新题'); expect(text).toContain('2026-10-13 15:01'); expect(text).not.toContain('123456'); expect(text).toContain('共 21 项')
    await wrapper.find('.catalog-audit button[aria-label="下一页"]').trigger('click'); await flushPromises(); expect(api.audit).toHaveBeenLastCalledWith(2, 20); await wrapper.find('.catalog-audit .kf-state-retry').trigger('click'); await flushPromises(); expect(api.audit).toHaveBeenLastCalledWith(2, 20); expect(wrapper.find('.catalog-audit').text()).toContain('PUBLISH'); wrapper.unmount()
  })
  it('judge view reports real disabled execution and retries actual status API', async () => {
    api.judgeStatus.mockRejectedValueOnce(new Error('状态服务不可用')).mockResolvedValueOnce({ mode: 'READ_ONLY', executionEnabled: false, productionSandboxReady: false })
    const { wrapper } = await open('?view=judge')
    expect(api.list).not.toHaveBeenCalled(); expect(api.categories).not.toHaveBeenCalled()
    expect(wrapper.find('[role="alert"]').text()).toContain('状态服务不可用')
    await wrapper.find('.kf-state-retry').trigger('click'); await flushPromises()
    expect(wrapper.text()).toContain('READ_ONLY'); expect(wrapper.text()).toContain('已禁用'); expect(wrapper.text()).toContain('未就绪'); wrapper.unmount()
  })
  it('only supported source rows can be selected and bulk errors stay visible', async () => {
    api.list.mockResolvedValueOnce(result([question, { ...question, catalogId: 'SCENARIO:90', sourceType: 'SCENARIO' }]))
    api.batchStatus.mockRejectedValueOnce(new Error('待审核题目不可发布'))
    const { wrapper } = await open()
    const checkboxes = wrapper.findAll('input[type="checkbox"]')
    expect(checkboxes[1]!.attributes('disabled')).toBeDefined()
    await checkboxes[0]!.setValue(true)
    await wrapper.findAll('.catalog-batch button').find(b => b.text() === '批量发布')!.trigger('click'); await flushPromises()
    expect(api.batchStatus).toHaveBeenCalledWith(['QUIZ:90'], true)
    expect(wrapper.find('.catalog-batch [role="alert"]').text()).toContain('待审核题目不可发布'); wrapper.unmount()
  })
  it('keeps the reviewed identity and stamp captured before confirmation when another detail opens', async () => {
    const other = { ...question, catalogId: 'QUIZ:91', sourceQuestionId: 91, editToken: 'different-content-token', sourceVersion: 4 }
    api.list.mockResolvedValueOnce(result([question, other])); api.detail.mockResolvedValueOnce({ item: question, payload: { content: '原审核题干', answer: '0' } }).mockResolvedValueOnce({ item: other, payload: { content: '其他题干', answer: '1' } })
    let confirm!: (value: boolean) => void
    api.confirm.mockImplementationOnce(() => new Promise(resolve => { confirm = resolve }))
    const { wrapper } = await open(); await wrapper.findAll('.catalog-actions button')[0]!.trigger('click'); await flushPromises()
    await wrapper.findAll('[role="dialog"] button').find(b => b.text() === '审核通过')!.trigger('click'); await flushPromises()
    await wrapper.findAll('[role="dialog"] button').find(b => b.text() === '关闭')!.trigger('click')
    await wrapper.findAll('.catalog-actions button')[1]!.trigger('click'); await flushPromises(); confirm(true); await flushPromises()
    expect(api.review).toHaveBeenCalledWith(90, 'APPROVED', '', { expectedSourceVersion: 3, expectedEditToken: 'reviewed-content-token' }); wrapper.unmount()
  })
  it('renders actual candidate content as escaped text and binds review to displayed version', async () => {
    api.detail.mockResolvedValueOnce({ item: question, payload: { content: '<img src=x onerror=alert(1)>真实题干', options: '["第一项","<script>第二项</script>"]', answer: '0', explanation: '真实答案解析' } })
    const { wrapper } = await open()
    await wrapper.find('.catalog-actions button').trigger('click'); await flushPromises()
    const preview = wrapper.find('.catalog-quiz-preview'); expect(preview.text()).toContain('真实题干'); expect(preview.text()).toContain('第一项'); expect(preview.text()).toContain('正确答案'); expect(preview.text()).toContain('真实答案解析'); expect(preview.find('img').exists()).toBe(false); expect(preview.find('script').exists()).toBe(false)
    await wrapper.findAll('[role="dialog"] button').find(b => b.text() === '审核通过')!.trigger('click'); await flushPromises()
    expect(api.review).toHaveBeenCalledWith(90, 'APPROVED', '', { expectedSourceVersion: 3, expectedEditToken: 'reviewed-content-token' }); wrapper.unmount()
  })
  it('drawer fetches source metadata and AI review uses explicit decision', async () => {
    const { wrapper } = await open()
    await wrapper.find('.catalog-actions button').trigger('click'); await flushPromises()
    expect(api.detail).toHaveBeenCalledWith('QUIZ:90'); expect(wrapper.find('[role="dialog"]').text()).toContain('审核内容预览')
    await wrapper.find('#review-comment').setValue('已核对答案')
    await wrapper.findAll('[role="dialog"] button').find(b => b.text() === '审核通过')!.trigger('click'); await flushPromises()
    expect(api.review).toHaveBeenCalledWith(90, 'APPROVED', '已核对答案', { expectedSourceVersion: 3, expectedEditToken: 'reviewed-content-token' }); wrapper.unmount()
  })
})
