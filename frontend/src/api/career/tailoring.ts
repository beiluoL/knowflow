import { apiGet, apiPost } from '@/api/request'
import type { CareerInsights, TailoringMode, TailoringTask } from './types'

export interface GenerateTailoringInput {
  resumeId: number
  resumeVersion: number
  jobId: number
  jobVersion: number
  mode: TailoringMode
  allowModelProcessing: boolean
}

export const generateTailoring = (input: GenerateTailoringInput) =>
  apiPost<TailoringTask>('/interview/career/tailoring/tasks', input, { timeout: 60_000 })

export const listTailoringTasks = () =>
  apiGet<{ id: number; resumeId: number; jobId: number; mode: string; status: string; modelSource: string; resultVersion: number | null; createdAt: string }[]>(
    '/interview/career/tailoring/tasks',
  )

export const getTailoringTask = (id: number) =>
  apiGet<TailoringTask>(`/interview/career/tailoring/tasks/${id}`)

export const reviewTailoringTask = (id: number, acceptedIds: string[]) =>
  apiPost<TailoringTask>(`/interview/career/tailoring/tasks/${id}/review`, { acceptedIds })

export const createTailoredVersion = (id: number) =>
  apiPost<TailoringTask>(`/interview/career/tailoring/tasks/${id}/create-version`, {}, { timeout: 30_000 })

export const getCareerInsights = () =>
  apiGet<CareerInsights>('/interview/career/insights', undefined, { timeout: 30_000 })
