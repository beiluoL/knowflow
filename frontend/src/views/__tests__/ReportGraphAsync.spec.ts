// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
const api = vi.hoisted(() => ({ learning: { getWeeklyReport: vi.fn(), listWeeklyReports: vi.fn(), dailyActivity: vi.fn(), mastery: vi.fn(), knowledgeMastery: vi.fn() }, knowledge: { graph: vi.fn(), entityGraph: vi.fn(), techGraph: vi.fn(), conceptDiagram: vi.fn(), regenerateConceptDiagram: vi.fn() }, categories: { tree: vi.fn() }, focus: { stats: vi.fn() }, mistakes: { stats: vi.fn() }, user: { stats: vi.fn() } }))
vi.mock('@/api', () => ({ learningApi: api.learning, knowledgeApi: api.knowledge, categoriesApi: api.categories, focusSessionApi: api.focus, mistakesApi: api.mistakes, userApi: api.user }))
vi.mock('vue-router', () => ({ useRoute: () => ({ query: {}, meta: {} }), useRouter: () => ({ push: vi.fn(), back: vi.fn() }) }))
vi.mock('@/utils/toast', () => ({ notify: vi.fn(), getApiError: () => '网络异常' }))
vi.mock('@/composables/usePageNavigation', () => ({ usePageNavigation: () => ({ goBack: vi.fn() }) }))
import WeeklyReport from '../WeeklyReport.vue'
import KnowledgeGraph from '../KnowledgeGraph.vue'
import { aggregateWeeklyMetrics } from '@/composables/useWeeklyReportEnhancer'
beforeEach(() => { vi.resetAllMocks(); api.learning.getWeeklyReport.mockResolvedValue(null); api.learning.listWeeklyReports.mockResolvedValue([]); api.learning.dailyActivity.mockResolvedValue([]); api.learning.mastery.mockResolvedValue(null); api.focus.stats.mockResolvedValue(null); api.mistakes.stats.mockResolvedValue(null); api.user.stats.mockResolvedValue(null); api.categories.tree.mockResolvedValue([]); api.knowledge.graph.mockResolvedValue({ nodes: [], edges: [] }); api.knowledge.entityGraph.mockResolvedValue({ nodes: [], edges: [] }); api.learning.knowledgeMastery.mockResolvedValue([]) })
describe('report and graph failures are visible and recoverable', () => {
  it('retry after regenerate failure repeats regeneration rather than cached GET', async () => {
    const diagram = { concept: '变量', mermaidCode: 'graph TD; A-->B', description: '缓存图解' };
    api.knowledge.conceptDiagram.mockResolvedValue(diagram); api.knowledge.regenerateConceptDiagram.mockRejectedValueOnce(new Error('Network Error')).mockResolvedValueOnce({ ...diagram, description: '新图解' });
    const w = mount(KnowledgeGraph, { global: { stubs: { MiniAppShell: { template: '<div><slot /></div>' }, MermaidDiagram: true } } }); await flushPromises(); await w.findAll('button').find(b => b.text() === '概念图解')!.trigger('click');
    await w.findAll('button').find(b => b.text() === 'AI 生成图解')!.trigger('click'); await flushPromises(); await w.findAll('button').find(b => b.text() === '重新生成')!.trigger('click'); await flushPromises();
    await w.find('.kb-state-retry').trigger('click'); await flushPromises(); expect(api.knowledge.regenerateConceptDiagram).toHaveBeenCalledTimes(2); expect(w.text()).toContain('新图解'); w.unmount();
  })
  it('failed mastery refresh never presents a prior score as current', async () => {
    api.knowledge.entityGraph.mockResolvedValue({ nodes: [{ id: 1, name: '实体', type: 'CONCEPT', weight: 1 }], edges: [] });
    api.learning.knowledgeMastery.mockResolvedValueOnce([{ knowledgeId: 1, masteryScore: 91, confidenceScore: 90, learningStatus: 'MASTERED' }]).mockRejectedValueOnce(new Error('Network Error'));
    const w = mount(KnowledgeGraph, { global: { stubs: { MiniAppShell: { template: '<div><slot /></div>' }, MermaidDiagram: true } } }); await flushPromises(); await w.findAll('button').find(b => b.text() === '知识实体图')!.trigger('click'); await flushPromises(); expect(w.text()).toContain('91');
    await w.findAll('button').find(b => b.text() === '刷新')!.trigger('click'); await flushPromises(); expect(w.find('[role="alert"]').exists()).toBe(true); expect(w.text()).not.toContain('91'); expect(w.text()).toContain('实体'); w.unmount();
  })
  it.each([['技术依赖图', 'AI 生成技术图谱', 'techGraph'], ['概念图解', '生成图解', 'conceptDiagram']] as const)('AI %s shows errors instead of its initial prompt', async (tab, action, method) => {
    api.knowledge[method].mockRejectedValueOnce(new Error('Network Error'));
    const w = mount(KnowledgeGraph, { global: { stubs: { MiniAppShell: { template: '<div><slot /></div>' }, MermaidDiagram: true } } });
    await flushPromises(); await w.findAll('button').find(b => b.text() === tab)!.trigger('click');
    const button = w.findAll('button').find(b => b.text().includes(action))!;
    expect(button).toBeDefined(); await button.trigger('click'); await flushPromises(); expect(w.find('[role="alert"]').exists()).toBe(true); w.unmount();
  })
  it('optional metrics failure keeps the actual report readable', async () => {
    api.learning.getWeeklyReport.mockResolvedValue({ id: 1, summary: '真实周报正文' }); api.focus.stats.mockRejectedValueOnce(new Error('Network Error'));
    const w = mount(WeeklyReport); await flushPromises(); expect(w.text()).toContain('真实周报正文'); expect(w.find('[role="alert"]').text()).toContain('增强统计'); expect(w.text()).not.toContain('暂无模式数据'); w.unmount();
  })
  it('weekly primary failure is not an empty week, retry recovers', async () => {
    api.learning.getWeeklyReport.mockRejectedValueOnce(new Error('Network Error'));
    const w = mount(WeeklyReport); await flushPromises(); expect(w.find('[role="alert"]').exists()).toBe(true);
    expect(w.text()).not.toContain('暂无本周周报'); await w.find('.kb-state-retry').trigger('click'); await flushPromises(); expect(w.find('[role="alert"]').exists()).toBe(false); expect(w.find('[role="status"]').text()).toContain('还没有学习周报'); w.unmount();
  })
  it('enhanced metrics never return fabricated empty data after failure', async () => {
    api.focus.stats.mockRejectedValueOnce(new Error('Network Error')); await expect(aggregateWeeklyMetrics()).rejects.toThrow('Network Error');
  })
  it('category graph failure and retry are distinct from genuine empty', async () => {
    api.knowledge.graph.mockRejectedValueOnce(new Error('Network Error'));
    const w = mount(KnowledgeGraph, { global: { stubs: { MiniAppShell: { template: '<div><slot /></div>' }, MermaidDiagram: true } } }); await flushPromises(); expect(w.find('[role="alert"]').exists()).toBe(true); await w.find('.kb-state-retry').trigger('click'); await flushPromises(); expect(w.find('[role="alert"]').exists()).toBe(false); expect(w.find('[role="status"]').text()).toContain('还没有知识图谱'); w.unmount();
  })
  it('entity graph load failure does not claim it was never built', async () => {
    api.knowledge.entityGraph.mockRejectedValueOnce(new Error('Network Error'));
    const w = mount(KnowledgeGraph, { global: { stubs: { MiniAppShell: { template: '<div><slot /></div>' }, MermaidDiagram: true } } }); await flushPromises(); await w.findAll('button').find(b => b.text() === '知识实体图')!.trigger('click'); await flushPromises(); expect(w.find('[role="alert"]').exists()).toBe(true); expect(w.text()).not.toContain('知识图谱尚未构建'); await w.find('.kb-state-retry').trigger('click'); await flushPromises(); expect(w.find('[role="alert"]').exists()).toBe(false); expect(w.find('[role="status"]').text()).toContain('还没有知识实体'); w.unmount();
  })
})
