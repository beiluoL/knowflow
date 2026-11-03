import { ref } from 'vue'
import { getApiError } from '@/utils/toast'
import {
  confirmResume,
  createResume,
  deleteResume,
  getResume,
  listResumeVersions,
  listResumes,
  parseResume,
  polishResume,
  setDefaultResume,
  updateResume,
  uploadResume,
} from '@/api/career/resume'
import type { CareerEdit, CareerRecord, CareerVersion, PolishResult } from '@/api/career/types'

/**
 * 简历读取与动作封装。
 *
 * 只缓存列表与当前版本，不把简历原文写入 localStorage；失败保留当前数据并暴露可读错误，
 * 页面负责 Loading / Empty / Error / Retry 状态。
 */
export function useResume() {
  const resumes = ref<CareerRecord[]>([])
  const current = ref<CareerRecord | null>(null)
  const versions = ref<CareerVersion[]>([])
  const loading = ref(false)
  const busy = ref(false)
  const error = ref('')
  let requestSeq = 0

  async function load(): Promise<void> {
    const sequence = ++requestSeq
    loading.value = true
    error.value = ''
    try {
      const list = await listResumes()
      if (sequence === requestSeq) resumes.value = list
    } catch (e: unknown) {
      if (sequence === requestSeq) error.value = getApiError(e, '简历列表加载失败')
    } finally {
      if (sequence === requestSeq) loading.value = false
    }
  }

  async function loadOne(id: number, version?: number): Promise<CareerRecord | null> {
    const sequence = ++requestSeq
    loading.value = true
    error.value = ''
    try {
      const record = await getResume(id, version)
      if (sequence === requestSeq) current.value = record
      return record
    } catch (e: unknown) {
      if (sequence === requestSeq) error.value = getApiError(e, '简历版本加载失败')
      return null
    } finally {
      if (sequence === requestSeq) loading.value = false
    }
  }

  async function loadVersions(id: number): Promise<void> {
    error.value = ''
    try {
      versions.value = await listResumeVersions(id)
    } catch (e: unknown) {
      error.value = getApiError(e, '版本历史加载失败')
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

  const create = (body: CareerEdit) =>
    run(() => createResume(body), '简历保存失败')

  const save = (id: number, body: CareerEdit) =>
    run(() => updateResume(id, body), '简历版本保存失败')

  const confirm = (id: number, version: number) =>
    run(() => confirmResume(id, version), '简历确认失败')

  const remove = (id: number) => run(() => deleteResume(id), '简历删除失败')

  const makeDefault = (id: number) => run(() => setDefaultResume(id), '默认简历设置失败')

  const parse = (id: number, version: number) =>
    run(() => parseResume(id, version), 'AI 解析失败，原版本未更改')

  const polish = (id: number, version: number, quotes: string[]): Promise<PolishResult | null> =>
    run(() => polishResume(id, version, quotes), 'AI 润色建议生成失败')

  const upload = (file: File, onProgress?: (percent: number) => void) =>
    run(() => uploadResume(file, onProgress), '简历上传失败')

  return {
    resumes,
    current,
    versions,
    loading,
    busy,
    error,
    load,
    loadOne,
    loadVersions,
    create,
    save,
    confirm,
    remove,
    makeDefault,
    parse,
    polish,
    upload,
  }
}
