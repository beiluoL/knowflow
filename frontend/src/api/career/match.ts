import { apiGet, apiPost } from '@/api/request'
import type { CareerMatch, MatchCorrection, MatchSummary } from './types'

export const listMatches = () => apiGet<MatchSummary[]>('/interview/career/matches')
export const getMatch = (id: number) => apiGet<CareerMatch>(`/interview/career/matches/${id}`)
export const createMatch = (resumeId: number, resumeVersion: number, jobId: number, jobVersion: number) =>
  apiPost<CareerMatch>('/interview/career/matches', {
    resumeId,
    resumeVersion,
    jobId,
    jobVersion,
    requestId: crypto.randomUUID(),
  }, { timeout: 60_000 })
export const correctMatch = (id: number, corrections: MatchCorrection[]) =>
  apiPost<CareerMatch>(`/interview/career/matches/${id}/corrections`, {
    requestId: crypto.randomUUID(),
    corrections,
  })
