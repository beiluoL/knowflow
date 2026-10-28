// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
const api = vi.hoisted(() => ({ categories: { tree: vi.fn() }, docs: { list: vi.fn(), recent: vi.fn() }, user: { stats: vi.fn() }, learning: { getReport: vi.fn(), dailyActivity: vi.fn(), learningEvents: vi.fn() } }))
vi.mock('@/api', () => ({ categoriesApi: api.categories, docsApi: api.docs, userApi: api.user, learningApi: api.learning, adminApi: {} }))
vi.mock('vue-router', () => ({ useRoute: () => ({ query: {}, path: '/knowledge' }), useRouter: () => ({ push: vi.fn(), back: vi.fn(), replace: vi.fn() }) }))
vi.mock('@/utils/toast', () => ({ notify: vi.fn(), confirmDialog: vi.fn(), getApiError: () => '网络异常' }))
vi.mock('@/stores/learning', () => ({ useLearningStore: () => ({ loading: false, loaded: true, error: '', weakError: '', nextAction: { type: 'EXPLORE' }, goals: [], paths: [], tasks: [], reviewRequired: [], weakPoints: [], evidence: [], evidenceAggregate: { dimensions: [] }, diagnosis: [], load: vi.fn() }) }))
import KnowledgeHome from '../KnowledgeHome.vue'
import LearningReportPage from '../LearningReportPage.vue'
import LearningCenterPage from '../LearningCenterPage.vue'
beforeEach(() => { vi.resetAllMocks(); localStorage.clear(); api.categories.tree.mockResolvedValue([]); api.docs.list.mockResolvedValue({ records: [] }); api.docs.recent.mockResolvedValue([]); api.user.stats.mockResolvedValue({}); api.learning.dailyActivity.mockResolvedValue([]); api.learning.learningEvents.mockResolvedValue({ records: [] }) })
describe('actual routed entry views', () => {
  it('knowledge workspace reuses the central next action instead of inventing a local recommendation', async () => {
    localStorage.setItem('token', 'unit-authenticated-user');
    api.categories.tree.mockResolvedValue([{ id: 1, name: '真实分类', children: [] }]);
    const w = mount(KnowledgeHome, { global: { plugins: [createPinia()], stubs: { KnowledgeSidebar: true, RouterLink: true, NextActionCard: true } } }); await flushPromises();
    expect(w.findComponent({ name: 'NextActionCard' }).props('action')).toEqual({type:'EXPLORE'}); w.unmount();
  })
  it('public knowledge browsing never displays an anonymous personal-plan loading state', async () => {
    api.categories.tree.mockResolvedValue([{ id: 1, name: '公开分类', children: [] }]);
    const w = mount(KnowledgeHome, { global: { plugins: [createPinia()], stubs: { KnowledgeSidebar: true, RouterLink: true, NextActionCard: true } } }); await flushPromises();
    expect(w.find('.knowledge-continuity').exists()).toBe(false); expect(w.text()).not.toContain('正在读取学习计划'); w.unmount();
  })
  it('recent-doc failure does not block an independently loaded category', async () => {
    const cat = { id: 1, name: '隔离分类', children: [] }; api.categories.tree.mockResolvedValue([cat]);
    api.docs.list.mockRejectedValueOnce(new Error('Network Error')).mockResolvedValue({ records: [{ id: 1, title: '分类文档', categoryId: 1 }], total: 1 });
    const w = mount(KnowledgeHome, { global: { plugins: [createPinia()], stubs: { KnowledgeSidebar: true, RouterLink: true } } }); await flushPromises();
    w.findComponent({ name: 'KnowledgeSidebar' }).vm.$emit('category-click', cat); await flushPromises(); expect(w.text()).toContain('分类文档'); w.unmount();
  })
  it('knowledge homepage main load failure has inline retry rather than erased arrays', async () => {
    api.categories.tree.mockRejectedValueOnce(new Error('Network Error'));
    const w = mount(KnowledgeHome, { global: { plugins: [createPinia()], stubs: { KnowledgeSidebar: true, RouterLink: true } } }); await flushPromises(); expect(w.find('[role="alert"]').exists()).toBe(true); await w.find('.kf-state-retry').trigger('click'); await flushPromises(); expect(w.find('[role="alert"]').exists()).toBe(false); expect(w.find('[role="status"]').text()).toContain('还没有知识资料'); w.unmount();
  })
  it('routed report has an actionable error and genuine empty state', async () => {
    api.learning.getReport.mockRejectedValueOnce(new Error('Network Error')).mockResolvedValueOnce(null);
    const w = mount(LearningReportPage, { global: { stubs: { RouterLink: true } } }); await flushPromises(); expect(w.find('[role="alert"]').exists()).toBe(true); await w.find('.kf-state-retry').trigger('click'); await flushPromises(); expect(w.find('[role="status"]').text()).toContain('还没有学习报告'); w.unmount();
  })
  it('learning heatmap rejection never claims no learning records', async () => {
    api.learning.dailyActivity.mockRejectedValueOnce(new Error('Network Error'));
    const w = mount(LearningCenterPage, { global: { plugins: [createPinia()], stubs: { RouterLink: true, NextActionCard: true } } }); await flushPromises(); expect(w.find('[role="alert"]').exists()).toBe(true); expect(w.text()).not.toContain('暂无学习记录，开始阅读或复习后会显示学习热力'); w.unmount();
  })
})
