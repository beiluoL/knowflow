// @vitest-environment happy-dom
import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
const api = vi.hoisted(() => ({ send: vi.fn() }))
vi.mock('@/api/chat', () => ({ chatApi: api }))
vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }), useRouter: () => ({ back: vi.fn(), push: vi.fn() }) }))
vi.mock('@/utils/toast', () => ({ notify: vi.fn(), getApiError: (e: Error) => e.message, confirmDialog: vi.fn() }))
import SmartWriting from '../SmartWriting.vue'
beforeEach(() => { localStorage.clear(); vi.clearAllMocks() })
function setup() { return mount(SmartWriting, { global: { stubs: { MiniAppShell: { template: '<div><slot /></div>' }, CategoryTreeSelect: true } } }) }
async function score(w: ReturnType<typeof setup>) {
  await w.find('textarea').setValue('这是用于核对失败恢复的真实正文。')
  const button = w.findAll('button').find(b => b.text().includes('评分'))!
  await button.trigger('click'); await flushPromises()
}
describe('writing failure recovery', () => {
  it('keeps the draft and exposes error plus retry instead of a successful mock score', async () => {
    api.send.mockRejectedValueOnce(new Error('评分服务不可用')).mockResolvedValueOnce({ content: '{}' })
    const w = setup(); await score(w)
    expect(w.find('.score-result').exists()).toBe(false)
    expect(w.find('[role="alert"]').text()).toContain('评分服务不可用')
    expect(w.find('textarea').element.value).toContain('真实正文')
    await w.find('[data-test="writing-score-retry"]').trigger('click'); await flushPromises()
    expect(api.send).toHaveBeenCalledTimes(2)
    expect(w.find('.score-result').exists()).toBe(false)
    w.unmount()
  })
})
