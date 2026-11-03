import type { CareerEdit, CareerRecord, CareerVersion, PolishResult } from './types'
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
  setDefaultRecord,
  updateRecord,
  uploadRecord,
} from './records'

export const listResumes = () => listRecords('RESUME')
export const createResume = (body: CareerEdit) => createRecord('RESUME', body)
export const getResume = (id: number, version?: number) => getRecord(id, version)
export const updateResume = (id: number, body: CareerEdit) => updateRecord(id, body)
export const confirmResume = (id: number, version: number) => confirmRecord(id, version)
export const deleteResume = (id: number) => deleteRecord(id)
export const setDefaultResume = (id: number) => setDefaultRecord(id)
export const listResumeVersions = (id: number) => listRecordVersions(id)
export const parseResume = (id: number, version: number) => parseRecord(id, version)
export const polishResume = (id: number, version: number, quotes: string[]) => polishRecord(id, version, quotes)
export const uploadResume = (file: File, onProgress?: (percent: number) => void) =>
  uploadRecord('RESUME', file, onProgress)
export const fetchResumeFile = (id: number) => fetchRecordFile(id)

export type { CareerEdit, CareerRecord, CareerVersion, PolishResult }
