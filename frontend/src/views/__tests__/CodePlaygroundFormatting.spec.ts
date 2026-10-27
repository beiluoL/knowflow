// @vitest-environment happy-dom
import { beforeEach, afterEach, describe, it, expect, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
const api = vi.hoisted(() => ({ detail: vi.fn(), run: vi.fn(), save: vi.fn(), list: vi.fn() }))
vi.mock('@/api/codeQuestion', () => ({ codeQuestionApi: { detail: api.detail } }))
vi.mock('@/api/codeRun', () => ({ codeRunApi: { run: api.run } }))
vi.mock('@/api/codeWorkspace', () => ({ codeWorkspaceApi: { save: api.save, list: api.list } }))
vi.mock('@/stores/aiCapability', () => ({ useAiCapabilityStore: () => ({ aiAvailable: false, refresh: vi.fn() }) }))
vi.mock('@/utils/toast', () => ({ notify: vi.fn(), getApiError: (e: Error) => e.message, confirmDialog: async () => true }))
vi.mock('vue-router', () => ({ useRoute: () => ({ params: { id: '1' }, query: {} }), useRouter: () => ({ push: vi.fn() }), onBeforeRouteLeave: vi.fn(), onBeforeRouteUpdate: vi.fn() }))
// Only replace the Worker transport; parsing and formatting still use real Prettier.
vi.mock('@/code-format/client', async () => {
  const { formatSource } = await import('@/code-format/engine')
  const { supportsFormatting } = await import('@/code-format/contracts')
  return { supportsFormatting, formatCode: formatSource }
})
import CodePlayground from '../CodePlayground.vue'
const mounted: ReturnType<typeof mount>[] = []
const render = () => { const w = mount(CodePlayground, { global: { stubs: { Icon: true, ChapterResourceLinks: true } } }); mounted.push(w); return w }
const settle = async () => { await vi.waitFor(async () => { await flushPromises(); expect(document.querySelector('[aria-busy="true"]')).toBeNull() }) }
beforeEach(() => {
  localStorage.clear(); vi.clearAllMocks()
  api.detail.mockResolvedValue({ id: 1, title: '格式化测试', difficulty: 0, language: 'javascript', codeTemplate: 'const x=1', testCases: '[]' })
  api.list.mockResolvedValue([{ path: 'main.js', name: 'main.js', content: 'const x=1' }])
  api.save.mockResolvedValue({}); api.run.mockResolvedValue({ status: 'SUCCESS', output: 'ok', error: null })
})
afterEach(() => { mounted.forEach(w => w.unmount()); mounted.length = 0; localStorage.clear() })
describe('工作台格式化接入', () => {
  it('工具栏写回真实格式化结果，运行读取新代码且判题仍禁用', async () => {
    const w = render(); await flushPromises()
    await w.get('.format-button').trigger('click')
    await vi.waitFor(() => expect((w.get('.code-textarea').element as HTMLTextAreaElement).value).toBe('const x = 1;\n'))
    await w.get('.run-btn').trigger('click'); await flushPromises()
    expect(api.run).toHaveBeenLastCalledWith({ language: 'javascript', code: 'const x = 1;\n', stdin: '' })
    expect(w.get('.submit-btn').attributes('disabled')).toBeDefined()
  })
  it('工作区保存先格式化，运行的隐式保存同样先格式化', async () => {
    const w = render(); await flushPromises(); await w.get('.ws-toggle-btn').trigger('click'); await flushPromises()
    await w.get('.code-textarea').setValue('const x=1 ')
    await w.findAll('button').find(b => b.text() === '保存文件')!.trigger('click')
    await vi.waitFor(() => expect(api.save).toHaveBeenLastCalledWith({ path: 'main.js', content: 'const x = 1;\n' }))
    await settle(); await w.get('.code-textarea').setValue('const y=2')
    await w.get('.run-btn').trigger('click')
    await vi.waitFor(() => expect(api.run).toHaveBeenLastCalledWith({ language: 'javascript', code: 'const y = 2;\n', workspace: true, entryFile: 'main.js' }))
    expect(api.save).toHaveBeenLastCalledWith({ path: 'main.js', content: 'const y = 2;\n' })
  })
  it('语法错误取消保存，原文仍可编辑，快捷键只在代码框注册', async () => {
    const w = render(); await flushPromises(); await w.get('.ws-toggle-btn').trigger('click'); await flushPromises()
    await w.get('.code-textarea').setValue('const =')
    await w.findAll('button').find(b => b.text() === '保存文件')!.trigger('click')
    await vi.waitFor(() => expect(w.text()).not.toContain('正在格式化…'))
    expect(api.save).not.toHaveBeenCalled()
    expect((w.get('.code-textarea').element as HTMLTextAreaElement).value).toBe('const =')
    await w.get('.code-textarea').setValue('const z=3')
    await w.get('.code-textarea').trigger('keydown', { key: 'F', code: 'KeyF', altKey: true, shiftKey: true })
    await vi.waitFor(() => expect((w.get('.code-textarea').element as HTMLTextAreaElement).value).toBe('const z = 3;\n'))
  })
  it('工作区扩展名控制语言，未知文件不能沿用上个JS格式化器', async () => {
    api.list.mockResolvedValue([{ path: 'notes.txt', name: 'notes.txt', content: 'not code' }])
    const w = render(); await flushPromises(); await w.get('.ws-toggle-btn').trigger('click'); await flushPromises()
    await w.get('.format-button').trigger('click'); await flushPromises()
    expect(w.text()).toContain('暂不支持格式化')
    expect((w.get('.code-textarea').element as HTMLTextAreaElement).value).toBe('not code')
  })
})
