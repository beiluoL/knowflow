// Phase 1 知识库证据驱动的面试题生成与草稿审核
import { apiDelete, apiGet, apiPost, apiPut } from './request'

export type GenerationStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED'
export type GeneratedQuestionStatus = 'DRAFT' | 'ENABLED' | 'ARCHIVED'

export interface GenerationCapability {
  categoryId: number
  name: string
  documentCount: number
}

export interface GenerationTask {
  id: number
  categoryId: number
  topic: string
  difficulty: number
  questionCount: number
  status: GenerationStatus
  progress: number
  message: string
  errorMessage?: string | null
  createdAt?: string
  updatedAt?: string
}

export interface SourceChunk {
  documentId: number
  documentTitle: string
  chunkIndex: number
  excerpt: string
}

export interface GeneratedQuestion {
  id: number
  taskId: number
  questionId: string
  quizQuestionId?: number | null
  title: string
  category: string
  difficulty: number
  questionType: string
  referenceAnswer: string
  referencePoints: string[]
  rubric: Record<string, string>
  followUps: string[]
  sourceDocumentIds: number[]
  sourceChunks: SourceChunk[]
  generationModel?: string | null
  promptVersion: string
  status: GeneratedQuestionStatus
  createdAt?: string
}

export interface GenerationSubmitBody {
  categoryId: number
  topic?: string
  difficulty?: number
  questionCount?: number
  requestId: string
}

export interface GeneratedQuestionUpdateBody {
  title: string
  category: string
  difficulty: number
  referenceAnswer: string
  referencePoints: string[]
  rubric: Record<string, string>
  followUps: string[]
}

export function listGenerationCapabilities() {
  return apiGet<GenerationCapability[]>('/interview/generation/capabilities')
}

export function submitGenerationTask(body: GenerationSubmitBody) {
  return apiPost<GenerationTask>('/interview/generation/tasks', body)
}

export function getGenerationTask(id: number) {
  return apiGet<GenerationTask>(`/interview/generation/tasks/${id}`)
}

export function retryGenerationTask(id: number) {
  return apiPost<GenerationTask>(`/interview/generation/tasks/${id}/retry`)
}

export function listGeneratedQuestions(status?: GeneratedQuestionStatus) {
  return apiGet<GeneratedQuestion[]>('/interview/generation/questions', status ? { status } : undefined)
}

export function updateGeneratedQuestion(id: number, body: GeneratedQuestionUpdateBody) {
  return apiPut<GeneratedQuestion>(`/interview/generation/questions/${id}`, body)
}

export function publishGeneratedQuestion(id: number) {
  return apiPost<GeneratedQuestion>(`/interview/generation/questions/${id}/publish`)
}

export function archiveGeneratedQuestion(id: number) {
  return apiPost<GeneratedQuestion>(`/interview/generation/questions/${id}/archive`)
}

export function deleteGeneratedQuestion(id: number) {
  return apiDelete<{ removed: number }>(`/interview/generation/questions/${id}`)
}

export function bulkGeneratedQuestions(ids: number[], action: 'publish' | 'archive' | 'delete') {
  return apiPost<{ affected: number; action: string }>('/interview/generation/questions/bulk', { ids, action })
}

/** 从服务端恢复最近任务，避免刷新后丢失任务入口。 */
export function listGenerationTasks() {
  return apiGet<GenerationTask[]>('/interview/generation/tasks')
}

/** 所选题目必须由后端再次验证已发布和来源权限。 */
export function startGeneratedInterview(questionIds:number[],requestId:string) {
  return apiPost<{sessionId:number}>('/interview/generation/interviews',{questionIds,requestId})
}
