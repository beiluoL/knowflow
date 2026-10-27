// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { appsApi } from '@/api/apps'
import { getWorkbenchOverview } from '@/api/workbench'
import WorkbenchShell from '../WorkbenchShell.vue'
import { storeApi } from '@/api/store'

vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ user: { id: 37 } }) }))
vi.mock('@/api/apps', () => ({ appsApi: { getUserApps: vi.fn(), updateUserApp: vi.fn() } }))
vi.mock('@/api/workbench', () => ({ getWorkbenchOverview: vi.fn() }))
vi.mock('@/api/store', () => ({ storeApi: { products: vi.fn() } }))

const overview = { captureTotal: 5, captureInbox: 2, captureStarred: 1, noteTotal: 3, reviewDue: 4, reviewCount: 7, palaceTotal: 0, lociTotal: 0, storyTotal: 2, storyDraft: 1, reviewLast7d: 3 }
async function render(props: { stage: 'capture' | 'review'; appKey?: string } = { stage: 'capture' }) {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/:pathMatch(.*)*', component: { template: '<div />' } }] })
  await router.push('/workbench/capture')
  const wrapper = mount(WorkbenchShell, { props, slots: { default: '<button>真实工作区操作</button>' }, global: { plugins: [createPinia(), router] } })
  await flushPromises()
  return wrapper
}
beforeEach(() => {
  vi.clearAllMocks()
  localStorage.clear()
  vi.mocked(appsApi.getUserApps).mockResolvedValue([])
  vi.mocked(getWorkbenchOverview).mockResolvedValue(overview)
  vi.mocked(storeApi.products).mockResolvedValue([])
})
describe('工作台统一壳', () => {
  it('记忆宫殿属于复习阶段，但不能借用间隔重复的安装状态', async () => {
    vi.mocked(appsApi.getUserApps).mockResolvedValue([{ appKey: 'spaced-review', installed: true, enabled: true, sortOrder: 0 }])
    const wrapper = await render({ stage: 'review', appKey: 'memory-palace' })
    expect(wrapper.text()).not.toContain('真实工作区操作')
    expect(wrapper.text()).toContain('安装记忆宫殿')
    expect(wrapper.get('[data-stage="review"]').attributes('aria-current')).toBe('page')
    expect(wrapper.findAll('[data-stage]')).toHaveLength(4)
    wrapper.unmount()
  })
  it('安装请求未确认时不提前挂载业务操作', async () => {
    let finish!: (value: any) => void
    vi.mocked(appsApi.updateUserApp).mockReturnValue(new Promise(resolve => { finish = resolve }))
    const wrapper = await render()
    await wrapper.get('[data-test="workbench-install"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).not.toContain('真实工作区操作')
    expect(wrapper.emitted('ready')).toBeUndefined()
    finish({ appKey: 'capture', installed: true, enabled: true, sortOrder: 0 })
    await flushPromises()
    expect(wrapper.emitted('ready')).toHaveLength(1)
    wrapper.unmount()
  })
  it('未安装时只显示安装入口，不挂载业务操作', async () => {
    const wrapper = await render()
    expect(wrapper.text()).toContain('安装收集箱')
    expect(wrapper.text()).not.toContain('真实工作区操作')
    expect(wrapper.emitted('ready')).toBeUndefined()
  })
  it('安装确认后启用内容，四阶段导航带真实统计', async () => {
    vi.mocked(appsApi.updateUserApp).mockResolvedValue({ appKey: 'capture', installed: true, enabled: true, sortOrder: 0 })
    const wrapper = await render()
    await wrapper.get('[data-test="workbench-install"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('真实工作区操作')
    expect(wrapper.emitted('ready')).toHaveLength(1)
    expect(wrapper.get('[data-stage="review"]').text()).toContain('4')
    expect(wrapper.get('[data-stage="capture"]').attributes('aria-current')).toBe('page')
  })
  it('安装失败不伪造成功，保留可重试入口', async () => {
    vi.mocked(appsApi.updateUserApp).mockRejectedValue(new Error('offline'))
    const wrapper = await render()
    await wrapper.get('[data-test="workbench-install"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).not.toContain('真实工作区操作')
    expect(wrapper.get('[role="alert"]').text()).toContain('失败')
  })
  it('统计失败显示重试，不显示零作为成功数据', async () => {
    vi.mocked(getWorkbenchOverview).mockRejectedValue(new Error('offline'))
    const wrapper = await render()
    expect(wrapper.text()).toContain('统计加载失败')
    expect(wrapper.get('[data-stage="review"] small').text()).toBe('— 今日到期')
  })
  it('商品信息失败时不把未知价格应用当作免费安装', async () => {
    vi.mocked(storeApi.products).mockRejectedValue(new Error('offline'))
    const wrapper = await render()
    await wrapper.get('[data-test="workbench-install"]').trigger('click')
    await flushPromises()
    expect(appsApi.updateUserApp).not.toHaveBeenCalled()
    expect(wrapper.text()).not.toContain('真实工作区操作')
  })
  it('未拥有付费商品时不得直接安装', async () => {
    vi.mocked(storeApi.products).mockResolvedValue([{ id: 3, productKey: 'capture-paid', name: '收集箱', type: 'APP', appKey: 'capture', priceCoin: 20, status: 'ACTIVE', version: '1', owned: false }])
    const wrapper = await render()
    await wrapper.get('[data-test="workbench-install"]').trigger('click')
    await flushPromises()
    expect(appsApi.updateUserApp).not.toHaveBeenCalled()
    expect(wrapper.text()).not.toContain('真实工作区操作')
  })
})
