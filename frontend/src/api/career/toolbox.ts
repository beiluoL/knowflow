import { apiGet, apiPost } from '@/api/request'
import type {
  CompareResult,
  JdOptimizeResult,
  ToolCheckResult,
  ToolRunSummary,
} from './types'

/** ATS 可读性检查：只是启发式评估，不代表任何企业 ATS 的判定。 */
export const runAtsCheck = (resumeId: number, version?: number, jobId?: number, jobVersion?: number) =>
  apiPost<ToolCheckResult>(
    '/interview/career/toolbox/ats-check',
    { resumeId, version, jobId, jobVersion },
    { timeout: 30_000 },
  )

export const runContentCheck = (resumeId: number, version?: number) =>
  apiPost<ToolCheckResult>(
    '/interview/career/toolbox/content-check',
    { resumeId, version },
    { timeout: 30_000 },
  )

export const runJdOptimize = (resumeId: number, resumeVersion: number, jobId: number, jobVersion: number) =>
  apiPost<JdOptimizeResult>(
    '/interview/career/toolbox/jd-optimize',
    { resumeId, resumeVersion, jobId, jobVersion },
    { timeout: 30_000 },
  )

export const listToolRuns = (toolType?: 'ATS' | 'CONTENT' | 'JD') =>
  apiGet<ToolRunSummary[]>('/interview/career/toolbox/history', {
    toolType: toolType || undefined,
  })

export const compareVersions = (resumeId: number, from: number, to: number) =>
  apiGet<CompareResult>(`/interview/career/records/${resumeId}/compare`, { from, to })
