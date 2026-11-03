import { apiGet, apiPost, apiPut } from '@/api/request'
import type { CareerEdit, CareerRecord, CareerVersion, JobPipeline, JobStatus, PolishResult } from './types'
import {
  confirmRecord,
  createRecord,
  deleteRecord,
  fetchRecordFile,
  getRecord,
  listRecordVersions,
  listRecords,
  parseRecord,
  polishRecord,
  updateRecord,
  uploadRecord,
} from './records'

export interface JobListQuery {
  status?: JobStatus | ''
  keyword?: string
  sort?: 'updated' | 'title' | 'status'
}

export const listJobRecords = () => listRecords('JOB')
export const createJob = (body: CareerEdit) => createRecord('JOB', body)
export const getJob = (id: number, version?: number) => getRecord(id, version)
export const updateJob = (id: number, body: CareerEdit) => updateRecord(id, body)
export const confirmJob = (id: number, version: number) => confirmRecord(id, version)
export const deleteJob = (id: number) => deleteRecord(id)
export const listJobVersions = (id: number) => listRecordVersions(id)
export const parseJob = (id: number, version: number) => parseRecord(id, version)
export const polishJob = (id: number, version: number, quotes: string[]) => polishRecord(id, version, quotes)
export const uploadJob = (file: File, onProgress?: (percent: number) => void) =>
  uploadRecord('JOB', file, onProgress)
export const fetchJobFile = (id: number) => fetchRecordFile(id)

export const listJobs = (query: JobListQuery = {}) =>
  apiGet<CareerRecord[]>('/interview/career/jobs', {
    status: query.status || undefined,
    keyword: query.keyword?.trim() || undefined,
    sort: query.sort || undefined,
  })

export const getJobPipeline = (id: number) => apiGet<JobPipeline>(`/interview/career/jobs/${id}/pipeline`)
export const updateJobPipeline = (id: number, status: JobStatus, note: string) =>
  apiPut<JobPipeline>(`/interview/career/jobs/${id}/pipeline`, { status, note })
export const importJobByUrl = (url: string) =>
  apiPost<CareerRecord>('/interview/career/records/JOB/import-url', { url }, { timeout: 60_000 })

export type { CareerEdit, CareerRecord, CareerVersion, PolishResult }

export interface JobEvent {
  id: number
  event_type: string
  from_status: JobStatus
  to_status: JobStatus
  note: string
  occurred_at: string
}
export const getJobEvents = (id: number) => apiGet<JobEvent[]>(`/interview/career/jobs/${id}/events`)
