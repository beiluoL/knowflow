import request, { apiDelete, apiGet, apiPost, apiPut, apiUpload } from '@/api/request'
import type {
  CareerEdit,
  CareerKind,
  CareerRecord,
  CareerVersion,
  PolishResult,
} from './types'

const BASE = '/interview/career/records'

/** 简历与岗位共用同一套不可变版本接口；kind 只决定资料类型，权限仍由后端校验。 */
export const listRecords = (kind: CareerKind) =>
  apiGet<CareerRecord[]>(BASE, { kind })

export const createRecord = (kind: CareerKind, body: CareerEdit) =>
  apiPost<CareerRecord>(`${BASE}/${kind}`, body)

export const getRecord = (id: number, version?: number) =>
  apiGet<CareerRecord>(`${BASE}/${id}`, version == null ? undefined : { version })

export const updateRecord = (id: number, body: CareerEdit) =>
  apiPut<CareerRecord>(`${BASE}/${id}`, body)

export const confirmRecord = (id: number, version: number) =>
  apiPost<CareerRecord>(`${BASE}/${id}/confirm`, { version })

export const deleteRecord = (id: number) => apiDelete<void>(`${BASE}/${id}`)

export const setDefaultRecord = (id: number) =>
  apiPost<CareerRecord>(`${BASE}/${id}/default`)

export const listRecordVersions = (id: number) =>
  apiGet<CareerVersion[]>(`${BASE}/${id}/versions`)

export const parseRecord = (id: number, version: number) =>
  apiPost<CareerRecord>(
    `${BASE}/${id}/parse`,
    { version, allowModelProcessing: true },
    { timeout: 5 * 60 * 1000 },
  )

export const polishRecord = (id: number, version: number, quotes: string[]) =>
  apiPost<PolishResult>(
    `${BASE}/${id}/polish`,
    { version, allowModelProcessing: true, quotes },
    { timeout: 5 * 60 * 1000 },
  )

export const uploadRecord = (kind: CareerKind, file: File, onProgress?: (percent: number) => void) =>
  apiUpload<CareerRecord>(`${BASE}/${kind}/upload`, file, {}, onProgress)

export const fetchRecordFile = (id: number) =>
  request.get<Blob, Blob>(`${BASE}/${id}/file`, { responseType: 'blob' })
