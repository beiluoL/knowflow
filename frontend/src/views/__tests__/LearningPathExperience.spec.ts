// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { reactive } from 'vue'
const api = vi.hoisted(() => ({ paths: vi.fn(), pathDetail: vi.fn(), chapters: vi.fn(), dag: vi.fn(), certificates: vi.fn() }))
vi.mock('@/api', () => ({ learningApi: api }))
const route = reactive({ params: { id: '1' } })
const push = vi.fn()
vi.mock('vue-router', () => ({ useRoute: () => route, useRouter: () => ({ push }) }))
vi.mock('@/utils/toast', () => ({ notify: vi.fn(), getApiError: () => '请求失败' }))
import LearningPaths from '../LearningPaths.vue'
import PathDetail from '../PathDetail.vue'
const path = { id: 1, title: 'Java 核心', chapterCount: 1, enrolled: true, progress: 50 }
beforeEach(() => { vi.resetAllMocks(); route.params.id = '1'; api.paths.mockResolvedValue([path]); api.pathDetail.mockResolvedValue(path); api.chapters.mockResolvedValue([{ id: 11, pathId: 1, title: 'HashMap', completed: false, locked: false }]); api.dag.mockResolvedValue({ nodes: [], edges: [] }); api.certificates.mockResolvedValue([]) })
describe('learning path experience', () => {
  it('list failure is an actionable error, not an empty catalog', async () => {
    api.paths.mockRejectedValueOnce(new Error('offline'))
    const w = mount(LearningPaths); await flushPromises()
    expect(w.find('[role="alert"]').exists()).toBe(true)
    expect(w.text()).not.toContain('暂无学习路径')
    await w.find('.kf-state-retry').trigger('click'); await flushPromises()
    expect(w.text()).toContain('Java 核心'); expect(w.text()).not.toContain('4.8')
    expect(w.find('.cover-tag').text()).toBe('难度未提供')
    w.unmount()
  })
  it('chapter failure never turns into a completed-count or zero-progress claim', async () => {
    api.chapters.mockRejectedValueOnce(new Error('offline'))
    const w = mount(PathDetail, { global: { stubs: { RouterLink: true, DagGraph: true } } }); await flushPromises()
    expect(w.find('[role="alert"]').exists()).toBe(true)
    expect(w.text()).not.toContain('0 / 0')
    expect(w.text()).toContain('50')
    await w.find('.kf-state-retry').trigger('click'); await flushPromises()
    expect(w.text()).toContain('HashMap'); w.unmount()
  })
  it('route changes ignore stale path responses', async () => {
    let resolve!: (value: typeof path) => void
    api.pathDetail.mockImplementationOnce(() => new Promise(r => { resolve = r }))
    const w = mount(PathDetail, { global: { stubs: { RouterLink: true, DagGraph: true } } })
    route.params.id = '2'; api.pathDetail.mockResolvedValue({ ...path, id: 2, title: '新的路径' }); await flushPromises()
    resolve(path); await flushPromises()
    expect(w.text()).toContain('新的路径'); expect(w.text()).not.toContain('Java 核心'); w.unmount()
  })
  it('cannot open locked chapters, even via the dependency graph', async () => {
    api.chapters.mockResolvedValue([{ id: 11, pathId: 1, title: 'Locked', completed: false, locked: true }])
    const w = mount(PathDetail, { global: { stubs: { RouterLink: true, DagGraph: true } } }); await flushPromises()
    await w.find('.timeline-item').trigger('click')
    w.findComponent({ name: 'DagGraph' }).vm.$emit('node-click', { id: 11, status: 'available' })
    await w.find('.primary-btn').trigger('click')
    expect(push).not.toHaveBeenCalled(); w.unmount()
  })
})
