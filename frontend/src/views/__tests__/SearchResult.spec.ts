// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { reactive } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
const mocks = vi.hoisted(() => ({ list: vi.fn(), push: vi.fn(), route: { query: {} as Record<string,string> } }))
vi.mock('vue-router', () => ({ useRoute: () => mocks.route, useRouter: () => ({ push: mocks.push }) }))
vi.mock('@/api', () => ({ docsApi: { list: mocks.list } }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ isLoggedIn: false }) }))
vi.mock('@/utils/toast', () => ({ notify: vi.fn(), getApiError: (_e: unknown, message: string) => message }))
import SearchResult from '../SearchResult.vue'
beforeEach(() => { vi.clearAllMocks(); mocks.route = reactive({ query: { q: '测试' } }); mocks.list.mockResolvedValue({ records: [], total: 0 }) })
describe('submitted search state', () => {
 it('discards late results after URL is cleared', async () => {
  let complete!: (value: unknown) => void
  mocks.list.mockImplementationOnce(() => new Promise(resolve => { complete = resolve }))
  const wrapper = mount(SearchResult, { global: { stubs: { Icon: true } } })
  mocks.route.query = {}; await flushPromises();
  complete({ records: [{ id: 1, title: '旧结果' }], total: 1 }); await flushPromises()
  expect(wrapper.find('.search-hero').exists()).toBe(true)
  expect(wrapper.find('.result-item').exists()).toBe(false); wrapper.unmount()
 })
 it('shows error with retry rather than empty results', async () => {
  mocks.list.mockRejectedValueOnce(new Error('timeout'))
  const wrapper = mount(SearchResult, { global: { stubs: { Icon: true } } }); await flushPromises()
  expect(wrapper.text()).toContain('搜索未完成'); expect(wrapper.text()).not.toContain('未找到相关结果')
  await wrapper.find('[role=alert] button').trigger('click'); await flushPromises()
  expect(mocks.list).toHaveBeenCalledTimes(2); expect(wrapper.text()).toContain('未找到相关结果'); wrapper.unmount()
 })
 it('restores filters and does not submit IME enter', async () => {
  mocks.route.query = { q: '测试', type: 'pdf', sort: 'view', time: 'week' }
  const wrapper = mount(SearchResult, { global: { stubs: { Icon: true } } }); await flushPromises()
  expect(wrapper.findAllComponents({name:'Select'})[0].props('modelValue')).toBe('pdf')
  expect(mocks.list).toHaveBeenCalledWith(expect.objectContaining({ keyword: '测试', sort: 'view' }))
  await wrapper.find('input').trigger('keydown', { key: 'Enter', isComposing: true })
  expect(mocks.push).not.toHaveBeenCalled(); wrapper.unmount()
 })
 it('commits a selected filter using the submitted keyword and preserves the input draft', async () => {
  const wrapper = mount(SearchResult, { global: { stubs: { Icon: true } } }); await flushPromises()
  await wrapper.find('input').setValue('尚未提交的草稿')
  wrapper.findAllComponents({name:'Select'})[0].vm.$emit('update:modelValue', 'pdf'); await flushPromises()
  expect(mocks.push).toHaveBeenCalledWith(expect.objectContaining({
   path: '/search', query: expect.objectContaining({ q: '测试', type: 'pdf' }),
  }))
  expect(mocks.list).toHaveBeenCalledTimes(1)
  expect((wrapper.find('input').element as HTMLInputElement).value).toBe('尚未提交的草稿')
  wrapper.unmount()
 })
 it('pushes type/time filters to the server query instead of fetching all pages', async () => {
  mocks.route.query = { q: '测试', type: 'note', time: 'month' }
  const wrapper = mount(SearchResult, { global: { stubs: { Icon: true } } }); await flushPromises()
  const expectedFrom = new Date(); expectedFrom.setDate(expectedFrom.getDate() - 29)
  expect(mocks.list).toHaveBeenCalledTimes(1)
  expect(mocks.list).toHaveBeenCalledWith(expect.objectContaining({
    keyword: '测试', type: 'note', from: expectedFrom.toISOString().slice(0, 10),
    pageNum: 1, pageSize: 10,
  })); wrapper.unmount()
 })
})

describe('hero search form', () => {
 beforeEach(() => { mocks.route.query = {}; mocks.push.mockResolvedValue(undefined) })
 const create = () => mount(SearchResult, { global: { stubs: { Icon: true } } })
 it('disables blank input and never navigates on an empty submit', async () => {
  const wrapper = create(); await wrapper.find('.hero-search-input').setValue('   ')
  expect(wrapper.find('.hero-search-btn').attributes('disabled')).toBeDefined()
  await wrapper.find('form').trigger('submit'); expect(mocks.push).not.toHaveBeenCalled()
  expect(mocks.list).not.toHaveBeenCalled(); wrapper.unmount()
 })
 it('submits a trimmed keyword from the form', async () => {
  const wrapper = create(); await wrapper.find('.hero-search-input').setValue('  测试  ')
  await wrapper.find('form').trigger('submit'); await flushPromises()
  expect(mocks.push).toHaveBeenCalledOnce()
  expect(mocks.push).toHaveBeenCalledWith(expect.objectContaining({ path: '/search', query: expect.objectContaining({ q: '测试' }) })); wrapper.unmount()
 })
 it('blocks IME Enter and enables Enter after composition ends', async () => {
  const wrapper = create(); const input=wrapper.find('.hero-search-input'); await input.setValue('测试')
  await input.trigger('compositionstart'); await input.trigger('keydown', { key: 'Enter', isComposing: true }); await wrapper.find('form').trigger('submit')
  expect(mocks.push).not.toHaveBeenCalled(); await input.trigger('compositionend'); await input.trigger('keydown', { key: 'Enter' }); await flushPromises()
  expect(mocks.push).toHaveBeenCalledOnce(); wrapper.unmount()
 })
 it('prevents duplicate submission while navigation is pending', async () => {
  let complete!: () => void; mocks.push.mockImplementationOnce(() => new Promise<void>(resolve=>{complete=resolve}))
  const wrapper=create(); await wrapper.find('.hero-search-input').setValue('测试'); await wrapper.find('form').trigger('submit')
  expect(wrapper.find('.hero-search-btn').attributes('disabled')).toBeDefined(); expect(wrapper.find('form').attributes('aria-busy')).toBe('true')
  await wrapper.find('form').trigger('submit'); expect(mocks.push).toHaveBeenCalledOnce(); complete(); await flushPromises()
  expect(wrapper.find('.hero-search-btn').attributes('disabled')).toBeUndefined(); wrapper.unmount()
 })
})
