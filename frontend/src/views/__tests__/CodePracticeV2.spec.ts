// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
const api = vi.hoisted(() => ({ list: vi.fn() }))
const push = vi.hoisted(() => vi.fn())
vi.mock('@/api/codeQuestion', () => ({ codeQuestionApi: api }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push, back: vi.fn() }), useRoute: () => ({ query: {} }) }))
import CodePractice from '../CodePractice.vue'
const row = (id: number, title: string) => ({ id, title, language: 'javascript', difficulty: 0, description: '# 说明\\n[上下文](/docs/1)' })
const render = () => mount(CodePractice, { global: { stubs: { Icon: true } } })
beforeEach(() => { vi.clearAllMocks(); api.list.mockResolvedValue([row(1, '真实目录题')]) })
describe('代码目录 UI V2', () => {
  it('失败不能伪装成空题库，重试保留入口', async () => {
    api.list.mockRejectedValueOnce(new Error('offline'))
    const w = render(); await flushPromises()
    expect(w.text()).toContain('题目加载失败')
    await w.findAll('button').find(b => b.text() === '重试')!.trigger('click'); await flushPromises()
    expect(w.text()).toContain('真实目录题'); w.unmount()
  })
  it('关键词与难度沿用后端筛选协议', async () => {
    const w = render(); await flushPromises()
    await w.get('input[type="search"]').setValue('闭包')
    await w.get('form').trigger('submit'); await flushPromises()
    expect(api.list).toHaveBeenLastCalledWith({ keyword: '闭包' })
    w.findComponent({ name: 'Select' }).vm.$emit('update:modelValue', '1'); await flushPromises()
    expect(api.list).toHaveBeenLastCalledWith({ keyword: '闭包', difficulty: 1 })
    expect(api.list).toHaveBeenCalledTimes(3); w.unmount()
  })
  it('旧响应不会覆盖最新语言筛选', async () => {
    let finish!: (v: unknown) => void
    api.list.mockImplementationOnce(() => new Promise(r => { finish = r }))
    const w = render()
    api.list.mockResolvedValueOnce([row(2, '最新筛选')])
    await w.findAll('button').find(b => b.text() === 'Python')!.trigger('click'); await flushPromises()
    finish([row(3, '过期结果')]); await flushPromises()
    expect(w.text()).toContain('最新筛选'); expect(w.text()).not.toContain('过期结果'); w.unmount()
  })
  it('不伪造缺失时长与通过率，保留原题身份', async () => {
    const w = render(); await flushPromises()
    expect(w.text()).not.toContain('30min'); expect(w.text()).not.toContain('0%')
    expect(w.text()).not.toContain('](/docs/1)')
    await w.findAll('button').find(b => b.text().includes('开始练习'))!.trigger('click')
    expect(push.mock.calls.at(-1)?.[0]).toEqual('/learning/code-practice/1'); w.unmount()
  })
})
