import { apiDelete, apiGet, apiPut } from '@/api/request'
import type { CareerEdit } from './types'
export interface ResumeDraft { revision: number; baseVersion: number; payload: CareerEdit | null; updatedAt?: string }
const path = (id: number) => `/interview/career/studio/resumes/${id}/draft`
export const getResumeDraft = (id: number) => apiGet<ResumeDraft>(path(id))
export const saveResumeDraft = (id: number, revision: number, baseVersion: number, payload: CareerEdit) =>
  apiPut<ResumeDraft>(path(id), { revision, baseVersion, payload })
export const deleteResumeDraft = (id: number, revision: number) => apiDelete<ResumeDraft>(`${path(id)}?revision=${revision}`)
