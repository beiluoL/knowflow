import { apiPost } from './request'
export interface RadarImportPreview { chapters: number; questionCandidates: number; conflicts: string[]; imageAssets?: number; referenceDocuments?: number }
export interface RadarImportResult { pathId: number; chaptersCreated: number; chaptersUpdated: number; pendingQuestionsCreated: number; scoresImported: number; imagesCreated?: number; referenceDocumentsCreated?: number }
export const interviewRadarApi = {
 preview: (source: unknown) => apiPost<RadarImportPreview>('/learning/interview-radar/import/preview', source, { timeout: 60000 }),
 apply: (source: unknown) => apiPost<RadarImportResult>('/learning/interview-radar/import', source, { timeout: 60000 }),
}
