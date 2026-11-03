import { apiGet, apiPost } from '@/api/request'
import type {
  CareerInterviewSummary,
  CareerPlan,
  CareerPlanQuestion,
  CareerPlanSettings,
  PlanSummary,
} from './types'

interface RegenerateResult {
  planId: number
  question: CareerPlanQuestion
}

export const listPlans = () => apiGet<PlanSummary[]>('/interview/career/plans')
export const getPlan = (id: number) => apiGet<CareerPlan>(`/interview/career/plans/${id}`)
export const createPlan = (settings: CareerPlanSettings) =>
  apiPost<CareerPlan>('/interview/career/plans', {
    ...settings,
    requestId: crypto.randomUUID(),
  }, { timeout: 60_000 })
export const startPlan = (id: number, requestId: string) =>
  apiPost<{ sessionId: number; [key: string]: unknown }>(
    `/interview/career/plans/${id}/start`,
    { requestId },
    { timeout: 60_000 },
  )
export const revisePlanQuestions = (id: number, questions: CareerPlanQuestion[]) =>
  apiPost<CareerPlan>(`/interview/career/plans/${id}/questions`, {
    requestId: crypto.randomUUID(),
    questions,
  })
export const regeneratePlanQuestion = (id: number, questionId: string) =>
  apiPost<RegenerateResult>(
    `/interview/career/plans/${id}/questions/${encodeURIComponent(questionId)}/regenerate`,
    { seed: crypto.randomUUID() },
  )
export const listCareerInterviews = () =>
  apiGet<CareerInterviewSummary[]>('/interview/career/interviews')
