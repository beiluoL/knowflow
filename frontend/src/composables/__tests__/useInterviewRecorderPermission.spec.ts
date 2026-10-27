// @vitest-environment happy-dom
import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useInterviewRecorder } from '../useInterviewRecorder'

afterEach(() => vi.unstubAllGlobals())
describe('interview microphone permission fallback', () => {
 it('explains denied permission and keeps text input available', async () => {
  const getUserMedia = vi.fn().mockRejectedValue(new DOMException('Permission denied', 'NotAllowedError'))
  vi.stubGlobal('navigator', { mediaDevices: { getUserMedia } })
  vi.stubGlobal('MediaRecorder', class {})
  let recorder!: ReturnType<typeof useInterviewRecorder>
  const wrapper = mount({ setup() { recorder = useInterviewRecorder(); return () => null } })
  await recorder.start(120, 100000)
  expect(recorder.error.value).toContain('麦克风权限被拒绝')
  expect(recorder.error.value).toContain('文字作答')
  expect(recorder.state.value).toBe('idle')
  expect(recorder.blob.value).toBeNull()
  expect(getUserMedia).toHaveBeenCalledTimes(1)
  wrapper.unmount()
 })
})
