// @vitest-environment happy-dom
/**
 * 简历 composable 的 Loading / Empty / Error 状态测试。
 * API 使用模块替身，不访问真实网络，也不写入真实学习或简历数据。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/api/career/resume', () => ({
  listResumes: vi.fn(),
  createResume: vi.fn(),
  getResume: vi.fn(),
  updateResume: vi.fn(),
  confirmResume: vi.fn(),
  deleteResume: vi.fn(),
  setDefaultResume: vi.fn(),
  listResumeVersions: vi.fn(),
  parseResume: vi.fn(),
  polishResume: vi.fn(),
  uploadResume: vi.fn(),
}))

import * as resumeApi from '@/api/career/resume'
import { useResume } from '../useResume'
import type { CareerRecord } from '@/api/career/types'

function record(overrides: Partial<CareerRecord> = {}): CareerRecord {
  return {
    id: 1,
    kind: 'RESUME',
    title: '测试简历',
    currentVersion: 1,
    isDefault: false,
    version: 1,
    source: 'Java 并发练习',
    confirmed: false,
    sections: {},
    ...overrides,
  }
}

describe('useResume 页面状态', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('空列表返回真实空数据而不是伪造记录', async () => {
    vi.mocked(resumeApi.listResumes).mockResolvedValue([])
    const state = useResume()
    await state.load()
    expect(state.resumes.value).toEqual([])
    expect(state.loading.value).toBe(false)
    expect(state.error.value).toBe('')
  })

  it('加载失败暴露错误并结束 Loading', async () => {
    vi.mocked(resumeApi.listResumes).mockRejectedValue(new Error('network'))
    const state = useResume()
    await state.load()
    expect(state.resumes.value).toEqual([])
    expect(state.loading.value).toBe(false)
    expect(state.error.value.length).toBeGreaterThan(0)
  })

  it('创建成功返回服务端记录', async () => {
    vi.mocked(resumeApi.createResume).mockResolvedValue(record())
    const state = useResume()
    const created = await state.create({ title: '测试简历', source: 'Java 并发练习', sections: {} })
    expect(created?.id).toBe(1)
    expect(state.busy.value).toBe(false)
  })

  it('确认失败保留错误且不伪造成功', async () => {
    vi.mocked(resumeApi.confirmResume).mockRejectedValue(new Error('conflict'))
    const state = useResume()
    const confirmed = await state.confirm(1, 1)
    expect(confirmed).toBeNull()
    expect(state.error.value.length).toBeGreaterThan(0)
  })
})
