import { ref } from 'vue'
import { getApiError } from '@/utils/toast'
import {
  createPlan,
  getPlan,
  listCareerInterviews,
  listPlans,
  regeneratePlanQuestion,
  revisePlanQuestions,
  startPlan,
} from '@/api/career/interview'
import type {
  CareerInterviewSummary,
  CareerPlan,
  CareerPlanQuestion,
  CareerPlanSettings,
  PlanSummary,
} from '@/api/career/types'

/** 岗位定制面试：方案、题目审核与原 Interview V2 会话启动。 */
export function useCareerInterview() {
  const plans = ref<PlanSummary[]>([])
  const interviews = ref<CareerInterviewSummary[]>([])
  const current = ref<CareerPlan | null>(null)
  const loading = ref(false)
  const busy = ref(false)
  const error = ref('')
  let requestSeq = 0

  async function loadAll(): Promise<void> {
    const sequence = ++requestSeq
    loading.value = true
    error.value = ''
    try {
      const [planList, interviewList] = await Promise.all([listPlans(), listCareerInterviews()])
      if (sequence === requestSeq) {
        plans.value = planList
        interviews.value = interviewList
      }
    } catch (e: unknown) {
      if (sequence === requestSeq) error.value = getApiError(e, '面试方案加载失败')
    } finally {
      if (sequence === requestSeq) loading.value = false
    }
  }

  async function loadPlan(id: number): Promise<CareerPlan | null> {
    const sequence = ++requestSeq
    loading.value = true
    error.value = ''
    try {
      const value = await getPlan(id)
      if (sequence === requestSeq) current.value = value
      return value
    } catch (e: unknown) {
      if (sequence === requestSeq) error.value = getApiError(e, '面试方案加载失败')
      return null
    } finally {
      if (sequence === requestSeq) loading.value = false
    }
  }

  async function run<T>(action: () => Promise<T>, fallback: string): Promise<T | null> {
    if (busy.value) return null
    busy.value = true
    error.value = ''
    try {
      return await action()
    } catch (e: unknown) {
      error.value = getApiError(e, fallback)
      return null
    } finally {
      busy.value = false
    }
  }

  const create = (settings: CareerPlanSettings) =>
    run(() => createPlan(settings), '面试方案生成失败')

  const revise = (id: number, questions: CareerPlanQuestion[]) =>
    run(() => revisePlanQuestions(id, questions), '题目审核保存失败')

  const regenerate = (id: number, questionId: string) =>
    run(() => regeneratePlanQuestion(id, questionId), '单题重新生成失败')

  const start = (id: number, requestId: string) =>
    run(() => startPlan(id, requestId), '面试启动失败')

  return {
    plans,
    interviews,
    current,
    loading,
    busy,
    error,
    loadAll,
    loadPlan,
    create,
    revise,
    regenerate,
    start,
  }
}
