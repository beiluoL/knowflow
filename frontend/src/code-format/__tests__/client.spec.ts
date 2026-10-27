import { afterEach, describe, expect, it, vi } from 'vitest'
import { formatCode } from '../client'
class WorkerDouble {
  static instances: WorkerDouble[] = []
  onmessage?: (event: { data: unknown }) => void
  onerror?: (event: { preventDefault: () => void }) => void
  terminate = vi.fn()
  postMessage = vi.fn()
  constructor() { WorkerDouble.instances.push(this) }
}
afterEach(() => { vi.unstubAllGlobals(); vi.useRealTimers(); WorkerDouble.instances = [] })
describe('Worker 请求生命周期', () => {
  it('超时终止计算，不遗留后台任务', async () => {
    vi.useFakeTimers(); vi.stubGlobal('Worker', WorkerDouble)
    const pending = formatCode('const x=1', 'javascript', 0, 0, new AbortController().signal)
    const assertion = expect(pending).rejects.toThrow('超时')
    await vi.advanceTimersByTimeAsync(5000); await assertion
    expect(WorkerDouble.instances[0]!.terminate).toHaveBeenCalledOnce()
  })
  it('编辑取消会终止 Worker', async () => {
    vi.stubGlobal('Worker', WorkerDouble); const controller = new AbortController()
    const pending = formatCode('const x=1', 'javascript', 0, 0, controller.signal)
    controller.abort(); await expect(pending).rejects.toMatchObject({ name: 'AbortError' })
    expect(WorkerDouble.instances[0]!.terminate).toHaveBeenCalledOnce()
  })
  it('无法创建Worker有明确错误', async () => {
    vi.stubGlobal('Worker', class { constructor() { throw new Error('blocked') } })
    await expect(formatCode('x', 'javascript', 0, 0, new AbortController().signal)).rejects.toThrow('无法加载')
  })
  it('Worker加载失败与语法错误传回失败，不返回替换内容', async () => {
    vi.stubGlobal('Worker', WorkerDouble)
    const a = formatCode('x', 'javascript', 0, 0, new AbortController().signal)
    WorkerDouble.instances[0]!.onerror!({ preventDefault() {} })
    await expect(a).rejects.toThrow('加载或执行失败')
    const b = formatCode('bad', 'javascript', 0, 0, new AbortController().signal)
    WorkerDouble.instances[1]!.onmessage!({ data: { error: '语法错误' } })
    await expect(b).rejects.toThrow('语法错误')
  })
})
