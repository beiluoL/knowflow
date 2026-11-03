// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { mount } from '@vue/test-utils'
import { useResumeDraft } from '../useResumeDraft'
import * as api from '@/api/career/draft'
vi.mock('@/api/career/draft', () => ({ getResumeDraft: vi.fn(), saveResumeDraft: vi.fn(), deleteResumeDraft: vi.fn() }))
vi.mock('@/utils/toast', () => ({ getApiError: (error: Error) => error.message }))
describe('服务端简历草稿', () => {
 afterEach(() => { vi.clearAllMocks(); vi.useRealTimers() })
 it('每轮输入防抖合并成草稿，恢复必须显式选择，不触碰正式版本', async () => {
  vi.useFakeTimers()
  vi.mocked(api.getResumeDraft).mockResolvedValue({ revision: 0, baseVersion: 0, payload: null })
  vi.mocked(api.saveResumeDraft).mockResolvedValue({ revision: 1, baseVersion: 3, payload: null })
  const payload = { title: '简历', source: '原文', sections: {} }
  let draft!: ReturnType<typeof useResumeDraft>
  const wrapper = mount(defineComponent({ setup() { draft = useResumeDraft(() => payload, vi.fn()); return () => null } }))
  await draft.initialize(12, 3)
  draft.schedule(); payload.source = '修改项目经历'; draft.schedule()
  await vi.advanceTimersByTimeAsync(810)
  expect(api.saveResumeDraft).toHaveBeenCalledTimes(1)
  expect(api.saveResumeDraft).toHaveBeenCalledWith(12, 0, 3, payload)
  vi.mocked(api.deleteResumeDraft).mockResolvedValue({ revision: 2, baseVersion: 3, payload: null })
  await draft.clear()
  expect(api.deleteResumeDraft).toHaveBeenCalledWith(12, 1)
  wrapper.unmount()
 })
 it('保留远端待恢复草稿，并发失败不继续覆盖服务端', async () => {
  const payload = { title: '旧草稿', source: '真实项目', sections: {} }
  vi.mocked(api.getResumeDraft).mockResolvedValue({ revision: 4, baseVersion: 1, payload })
  const apply = vi.fn()
  let draft!: ReturnType<typeof useResumeDraft>
  const wrapper = mount(defineComponent({ setup() { draft = useResumeDraft(() => payload, apply); return () => null } }))
  await draft.initialize(12, 2)
  await draft.flush()
  expect(apply).not.toHaveBeenCalled()
  expect(api.saveResumeDraft).not.toHaveBeenCalled()
  draft.restore()
  expect(apply).toHaveBeenCalledWith(payload)
  payload.title = '恢复后的进一步修改'
  vi.mocked(api.saveResumeDraft).mockRejectedValue(new Error('草稿已变化'))
  await draft.flush(); await draft.flush()
  expect(api.saveResumeDraft).toHaveBeenCalledWith(12, 4, 1, payload)
  expect(api.saveResumeDraft).toHaveBeenCalledTimes(1)
  expect(draft.failed.value).toBe(true)
  wrapper.unmount()
 })
})
