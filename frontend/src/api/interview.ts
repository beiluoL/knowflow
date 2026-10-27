// Phase 3H 模拟面试 · 基础模式（预置题库，零 AI 依赖）
import { apiGet, apiPost, apiPut } from './request'

export type InterviewTrackCode =
  | 'JAVA_BASIC'
  | 'JAVA_CONCURRENCY'
  | 'JVM'
  | 'FRAMEWORK_STORAGE'
  | 'RAG'
  | 'AGENT_MCP'
  | 'JAVA_AI_SCENE'
  | 'ALGORITHM'

export interface InterviewTrack {
  code: string
  name: string
  description: string
  questionPoolSize: number
}

export interface InterviewRubric {
  level1: string
  level3: string
  level5: string
}

export interface InterviewQuestion {
  id: string
  title: string
  referencePoints: string[]
  followups: string[]
  rubric: InterviewRubric
}

export interface InterviewFollowupAnswer {
  q: string
  a: string
}

export interface InterviewAnswer {
  questionId: string
  title?: string
  answer: string
  followups?: InterviewFollowupAnswer[]
  selfScore: number
  selfComment?: string | null
}

export interface InterviewSession {
  id: number
  track: string
  trackName: string
  v2?: boolean
  mode: 'BASIC' | 'STRICT' | 'TEACHING'
  status: 'IN_PROGRESS' | 'FINISHED'
  questionCount: number
  questions: InterviewQuestion[]
  answers: Array<Omit<InterviewAnswer, 'selfScore'> & { selfScore: number | null }>
  avgSelfScore?: number | null
  durationSec?: number | null
  startedAt?: string | null
  finishedAt?: string | null
  answeredCount: number
}

export interface InterviewSessionSummary {
  id: number
  track: string
  trackName: string
  status: 'IN_PROGRESS' | 'FINISHED'
  questionCount: number
  avgSelfScore?: number | null
  durationSec?: number | null
  startedAt?: string | null
  finishedAt?: string | null
}

export interface PageData<T> {
  records: T[]
  total: number
  pageNum: number
  pageSize: number
  pages: number
}

export function listInterviewTracks() {
  return apiGet<InterviewTrack[]>('/interview/tracks')
}

export function startInterviewSession(track: string, questionCount?: number) {
  return apiPost<InterviewSession>('/interview/sessions', { track, questionCount })
}

export function getInterviewSession(id: number) {
  return apiGet<InterviewSession>(`/interview/sessions/${id}`)
}

export function saveInterviewAnswer(id: number, body: InterviewAnswer) {
  return apiPut<InterviewSession>(`/interview/sessions/${id}/answers`, body)
}

export function finishInterviewSession(id: number) {
  return apiPost<InterviewSession>(`/interview/sessions/${id}/finish`)
}

export function listMyInterviewSessions(page = 1, pageSize = 10) {
  return apiGet<PageData<InterviewSessionSummary>>('/interview/sessions', { page, pageSize })
}
