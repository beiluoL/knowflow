import { apiGet, apiPost } from '@/api/request'
import type { BatchMatchResult, MultiResumeResult } from './types'

/** 一个岗位 → 全部已确认简历（后端逐条复用单条匹配并生成快照）。 */
export const matchJobWithResumes = (jobId: number, jobVersion?: number) =>
  apiPost<MultiResumeResult>(
    '/interview/career/matches/multi-resume',
    { jobId, jobVersion },
    { timeout: 60_000 },
  )

/** 一份简历 → 多个岗位（单次最多 10 个，失败项单独返回）。 */
export const batchMatchJobs = (resumeId: number, resumeVersion: number, jobIds: number[]) =>
  apiPost<BatchMatchResult>(
    '/interview/career/matches/batch',
    { resumeId, resumeVersion, jobIds },
    { timeout: 60_000 },
  )

export const getMatchGaps = (matchId: number) =>
  apiGet<{
    matchId: number
    missing: { skill: string; status: string; evidence: string[]; note: string }[]
    covered: { skill: string; status: string; evidence: string[]; note: string }[]
    note: string
  }>(`/interview/career/matches/${matchId}/gaps`)
