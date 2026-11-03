import { ref } from 'vue'
import { getApiError } from '@/utils/toast'
import { correctMatch, createMatch, getMatch, listMatches } from '@/api/career/match'
import type { CareerMatch, MatchCorrection, MatchSummary } from '@/api/career/types'

/** 匹配任务读取、创建与人工修正；每次创建/修正都由后端返回独立不可变快照。 */
export function useJobMatch() {
  const matches = ref<MatchSummary[]>([])
  const current = ref<CareerMatch | null>(null)
  const loading = ref(false)
  const busy = ref(false)
  const error = ref('')
  let requestSeq = 0

  async function load(): Promise<void> {
    const sequence = ++requestSeq
    loading.value = true
    error.value = ''
    try {
      const list = await listMatches()
      if (sequence === requestSeq) matches.value = list
    } catch (e: unknown) {
      if (sequence === requestSeq) error.value = getApiError(e, '匹配历史加载失败')
    } finally {
      if (sequence === requestSeq) loading.value = false
    }
  }

  async function loadOne(id: number): Promise<CareerMatch | null> {
    const sequence = ++requestSeq
    loading.value = true
    error.value = ''
    try {
      const value = await getMatch(id)
      if (sequence === requestSeq) current.value = value
      return value
    } catch (e: unknown) {
      if (sequence === requestSeq) error.value = getApiError(e, '匹配快照加载失败')
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

  const create = (resumeId: number, resumeVersion: number, jobId: number, jobVersion: number) =>
    run(() => createMatch(resumeId, resumeVersion, jobId, jobVersion), '智能匹配失败')

  const correct = (id: number, corrections: MatchCorrection[]) =>
    run(() => correctMatch(id, corrections), '匹配修正失败')

  return { matches, current, loading, busy, error, load, loadOne, create, correct }
}
