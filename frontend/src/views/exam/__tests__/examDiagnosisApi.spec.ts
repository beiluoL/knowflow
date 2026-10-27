import { beforeEach, expect, it, vi } from 'vitest'
const get = vi.hoisted(() => vi.fn())
vi.mock('@/api/request', () => ({ apiGet: get }))
import { examDiagnosisApi } from '@/api/examDiagnosis'
beforeEach(() => get.mockReset())
it('uses the existing authenticated request layer for the exact attempt diagnosis endpoint', async () => {
  const response = { attemptId: 7, topics: [] }; get.mockResolvedValueOnce(response)
  expect(await examDiagnosisApi.get(7)).toBe(response); expect(get).toHaveBeenCalledWith('/exam/attempts/7/diagnosis')
})
it('does not disguise API failure as empty observations', async () => {
  get.mockRejectedValueOnce(new Error('403: 无权读取考试报告'))
  await expect(examDiagnosisApi.get(7)).rejects.toThrow('403: 无权读取考试报告')
})
