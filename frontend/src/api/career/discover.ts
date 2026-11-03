import { apiDelete, apiGet, apiPost } from '@/api/request'
import type {
  JobSearchResponse,
  JobSourceMeta,
  NormalizedJob,
  SearchHistoryItem,
} from './types'

export interface JobSearchQuery {
  keyword?: string
  location?: string
  company?: string
  remoteOnly?: boolean
  salaryMin?: number
  experience?: string
  education?: string
  jobType?: string
  sources?: string[]
  page?: number
  size?: number
}

export const listJobSources = () =>
  apiGet<JobSourceMeta[]>('/interview/career/discover/sources')

export const searchJobs = (query: JobSearchQuery) =>
  apiGet<JobSearchResponse>('/interview/career/discover/jobs', {
    keyword: query.keyword?.trim() || undefined,
    location: query.location || undefined,
    company: query.company?.trim() || undefined,
    remoteOnly: query.remoteOnly || undefined,
    salaryMin: query.salaryMin || undefined,
    experience: query.experience || undefined,
    education: query.education || undefined,
    jobType: query.jobType || undefined,
    sources: query.sources?.length ? query.sources.join(',') : undefined,
    page: query.page || 1,
    size: query.size || 20,
  }, { timeout: 60_000 })

export const listSearchHistory = () =>
  apiGet<SearchHistoryItem[]>('/interview/career/discover/history')

export const saveSearchHistory = (
  keyword: string,
  filters: Record<string, unknown>,
  saved: boolean,
) => apiPost<{ id: number; saved: boolean }>('/interview/career/discover/history', {
  keyword,
  filters,
  saved,
})

export const deleteSearchHistory = (id: number) =>
  apiDelete<void>(`/interview/career/discover/history/${id}`)

/** 一键加入岗位中心：后端复用 CareerRecordService，不新建岗位表。 */
export const importDiscoveredJob = (job: NormalizedJob) =>
  apiPost<{ recordId: number; duplicate: boolean }>('/interview/career/discover/import', {
    sourceId: job.sourceId,
    externalId: job.externalId,
    title: job.company ? `${job.company} · ${job.title}` : job.title,
    company: job.company,
    location: job.location,
    description: job.description,
    sourceUrl: job.sourceUrl || job.applicationUrl,
    applicationUrl: job.applicationUrl,
    postedAt: job.postedAt,
    attribution: job.attribution,
  })
