// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import source from '../CodePlayground.vue?raw'
const api = vi.hoisted(() => ({ detail: vi.fn(), submit: vi.fn(), run: vi.fn(), confirm: vi.fn(), push: vi.fn(), leave: vi.fn(), update: vi.fn(), list: vi.fn(), save: vi.fn() }))
vi.mock('@/api/codeQuestion', () => ({ codeQuestionApi: { detail: api.detail, submit: api.submit } }))
vi.mock('@/api/codeRun', () => ({ codeRunApi: { run: api.run } }))
vi.mock('@/api/codeWorkspace', () => ({ codeWorkspaceApi: { list: api.list, save: api.save } }))
vi.mock('@/stores/aiCapability', () => ({ useAiCapabilityStore: () => ({ aiAvailable: false, refresh: vi.fn() }) }))
vi.mock('@/utils/toast', () => ({ notify: vi.fn(), getApiError: (e: Error) => e.message, confirmDialog: api.confirm, promptDialog: vi.fn() }))
vi.mock('vue-router', () => ({ useRoute: () => ({ params: { id: '1' }, query: {} }), useRouter: () => ({ push: api.push }), onBeforeRouteLeave: api.leave, onBeforeRouteUpdate: api.update }))
import CodePlayground from '../CodePlayground.vue'
const render = () => mount(CodePlayground, { global: { stubs: { ChapterResourceLinks: true, Icon: true } } })
beforeEach(() => { vi.clearAllMocks(); api.detail.mockResolvedValue({ id: 1, title: '测试题', language: 'javascript', codeTemplate: 'const original = 1', testCases: '[]' }); api.confirm.mockResolvedValue(false); api.list.mockResolvedValue([{ path: 'main.js', name: 'main.js', content: 'server original' }]); api.save.mockResolvedValue({}); api.run.mockRejectedValue(new Error('该能力仅在可信本地开发模式启用')) })
describe('代码工作台安全与编辑保护', () => {
  it('不存在主应用动态执行与客户端判题上报', () => {
    // 静态断言证明旧危险入口移除，测试绝不执行用户源码。
    expect(source).not.toMatch(/new\s+Function\s*\(|\beval\s*\(/)
    expect(source).not.toContain('codeQuestionApi.submit(')
  })
  it('取消重置保留临时代码', async () => {
    const w = render(); await flushPromises(); await w.get('.code-textarea').setValue('const draft = 2')
    await w.get('[aria-label="重置代码"]').trigger('click'); await flushPromises()
    expect(api.confirm).toHaveBeenCalled(); expect((w.get('.code-textarea').element as HTMLTextAreaElement).value).toBe('const draft = 2'); w.unmount()
  })
  it('取消语言切换保留代码与语言', async () => {
    const w = render(); await flushPromises(); await w.get('.code-textarea').setValue('const draft = 2')
    await w.get('.lang-select').setValue('python'); await flushPromises()
    expect((w.get('.lang-select').element as HTMLSelectElement).value).toBe('javascript')
    expect((w.get('.code-textarea').element as HTMLTextAreaElement).value).toBe('const draft = 2'); w.unmount()
  })
  it('Tab可自然离开编辑器，离开保护不伪称已保存', async () => {
    const w = render(); await flushPromises(); await w.get('.code-textarea').setValue('draft')
    const event = new KeyboardEvent('keydown', { key: 'Tab', cancelable: true })
    w.get('.code-textarea').element.dispatchEvent(event)
    expect(event.defaultPrevented).toBe(false)
    expect(w.text()).toContain('未保存')
    expect(await api.leave.mock.calls.at(-1)![0]()).toBe(false); w.unmount()
  })
  it('运行时锁定防重复且失败不伪称成功', async () => {
    let reject!: (e: Error) => void
    api.run.mockImplementationOnce(() => new Promise((_, r) => { reject = r }))
    const w = render(); await flushPromises(); await w.get('.run-btn').trigger('click'); await w.get('.run-btn').trigger('click')
    expect(api.run).toHaveBeenCalledTimes(1); expect(w.get('.code-textarea').attributes('disabled')).toBeDefined()
    reject(new Error('没有运行权限')); await flushPromises()
    expect(w.text()).toContain('没有运行权限'); expect(w.get('.code-textarea').attributes('disabled')).toBeUndefined(); w.unmount()
  })
  it('AI关闭时禁用模型入口，判题入口明确不可用', async () => {
    const w = render(); await flushPromises()
    expect(w.get('.ai-btn').attributes('disabled')).toBeDefined()
    expect(w.get('.submit-btn').attributes('disabled')).toBeDefined()
    expect(w.text()).toContain('安全判题'); w.unmount()
  })
  it('工作区保存失败保留草稿，只有成功响应才能解除未保存状态', async () => {
    const w = render(); await flushPromises()
    // This existing API failure test deliberately saves raw, non-JS draft text.
    await w.get('.format-settings input[type="checkbox"]').setValue(false)
    await w.get('.ws-toggle-btn').trigger('click'); await flushPromises()
    await w.get('.code-textarea').setValue('workspace draft')
    api.save.mockRejectedValueOnce(new Error('offline'))
    const save = () => w.findAll('button').find(b => b.text() === '保存文件')!
    await save().trigger('click'); await flushPromises()
    expect(w.text()).toContain('有未保存修改')
    expect((w.get('.code-textarea').element as HTMLTextAreaElement).value).toBe('workspace draft')
    await save().trigger('click'); await flushPromises()
    expect(w.text()).not.toContain('有未保存修改')
    expect(api.save).toHaveBeenLastCalledWith({ path: 'main.js', content: 'workspace draft' }); w.unmount()
  })
  it('题目加载失败提供重试，不能渲染可操作的空题', async () => {
    api.detail.mockRejectedValueOnce(new Error('offline'))
    const w = render(); await flushPromises()
    expect(w.text()).toContain('题目加载失败')
    expect(w.get('.run-btn').attributes('disabled')).toBeDefined()
    await w.findAll('button').find(b => b.text() === '重试')!.trigger('click'); await flushPromises()
    expect(w.text()).toContain('测试题'); expect(w.get('.run-btn').attributes('disabled')).toBeUndefined(); w.unmount()
  })

  it('退出工作区恢复题目模板时同时恢复对应语言', async () => {
    api.list.mockResolvedValueOnce([{ path: 'main.py', name: 'main.py', content: 'print(1)' }])
    const w = render(); await flushPromises()
    await w.get('.ws-toggle-btn').trigger('click'); await flushPromises()
    expect((w.get('.lang-select').element as HTMLSelectElement).value).toBe('python')
    await w.get('.ws-toggle-btn').trigger('click'); await flushPromises()
    expect((w.get('.lang-select').element as HTMLSelectElement).value).toBe('javascript')
    expect((w.get('.code-textarea').element as HTMLTextAreaElement).value).toBe('const original = 1'); w.unmount()
  })
  it('调试和评估会将窄屏工作分区切换到结果', async () => {
    const w = render(); await flushPromises()
    await w.get('.debug-foot-btn').trigger('click'); await flushPromises()
    expect(w.get('.playground-page').attributes('data-section')).toBe('result')
    await w.findAll('.workspace-sections button')[1]!.trigger('click')
    await w.get('.assess-foot-btn').trigger('click'); await flushPromises()
    expect(w.get('.playground-page').attributes('data-section')).toBe('result'); w.unmount()
  })

  it('键盘可以调整题目面板，宽度限制防止编辑区被挤出', async () => {
    const w = render(); await flushPromises()
    const separator = w.get('[role="separator"]')
    for (let i = 0; i < 10; i++) await separator.trigger('keydown', { key: 'ArrowRight' })
    expect(separator.attributes('aria-valuenow')).toBe('65')
    for (let i = 0; i < 10; i++) await separator.trigger('keydown', { key: 'ArrowLeft' })
    expect(separator.attributes('aria-valuenow')).toBe('25'); w.unmount()
  })

})
