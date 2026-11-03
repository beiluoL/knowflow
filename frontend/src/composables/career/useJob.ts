import { ref } from 'vue'
import { getApiError } from '@/utils/toast'
import {
  confirmJob,
  createJob,
  deleteJob,
  getJob,
  getJobPipeline,
  importJobByUrl,
  listJobVersions,
  listJobs,
  parseJob,
  updateJob,
  updateJobPipeline,
  uploadJob,
  type JobListQuery,
} from '@/api/career/job'
import type {
  CareerEdit,
  CareerRecord,
  CareerVersion,
  JobPipeline,
  JobStatus,
  PolishResult,
} from '@/api/career/types'
import { polishJob } from '@/api/career/job'

/** 岗位读取、JD 解析与投递状态；所有写操作等待服务端确认后再更新本地状态。 */
export function useJob() {
  const jobs = ref<CareerRecord[]>([])
  const current = ref<CareerRecord | null>(null)
  const pipeline = ref<JobPipeline | null>(null)
  const versions = ref<CareerVersion[]>([])
  const loading = ref(false)
  const busy = ref(false)
  const error = ref('')
  let requestSeq = 0

  async function load(query: JobListQuery = {}): Promise<void> {
    const sequence = ++requestSeq
    loading.value = true
    error.value = ''
    try {
      const list = await listJobs(query)
      if (sequence === requestSeq) jobs.value = list
    } catch (e: unknown) {
      if (sequence === requestSeq) error.value = getApiError(e, '岗位列表加载失败')
    } finally {
      if (sequence === requestSeq) loading.value = false
    }
  }

  async function loadOne(id: number, version?: number): Promise<CareerRecord | null> {
    const sequence = ++requestSeq
    loading.value = true
    error.value = ''
    try {
      const record = await getJob(id, version)
      if (sequence === requestSeq) current.value = record
      return record
    } catch (e: unknown) {
      if (sequence === requestSeq) error.value = getApiError(e, '岗位版本加载失败')
      return null
    } finally {
      if (sequence === requestSeq) loading.value = false
    }
  }

  async function loadPipeline(id: number): Promise<void> {
    try {
      pipeline.value = await getJobPipeline(id)
    } catch (e: unknown) {
      error.value = getApiError(e, '岗位投递状态加载失败')
    }
  }

  async function loadVersions(id: number): Promise<void> {
    try {
      versions.value = await listJobVersions(id)
    } catch (e: unknown) {
      error.value = getApiError(e, '岗位版本历史加载失败')
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

  const create = (body: CareerEdit) => run(() => createJob(body), '岗位保存失败')
  const save = (id: number, body: CareerEdit) => run(() => updateJob(id, body), '岗位版本保存失败')
  const confirm = (id: number, version: number) => run(() => confirmJob(id, version), '岗位确认失败')
  const remove = (id: number) => run(() => deleteJob(id), '岗位删除失败')
  const parse = (id: number, version: number) =>
    run(() => parseJob(id, version), 'AI 解析失败，原版本未更改')
  const upload = (file: File, onProgress?: (percent: number) => void) =>
    run(() => uploadJob(file, onProgress), 'JD 文件上传失败')
  const importUrl = (url: string) => run(() => importJobByUrl(url), '岗位链接导入失败')
  const updateStatus = (id: number, status: JobStatus, note: string) =>
    run(() => updateJobPipeline(id, status, note), '岗位状态更新失败')
  const polish = (id: number, version: number, quotes: string[]): Promise<PolishResult | null> =>
    run(() => polishJob(id, version, quotes), 'AI 润色建议生成失败')

  return {
    jobs,
    current,
    pipeline,
    versions,
    loading,
    busy,
    error,
    load,
    loadOne,
    loadPipeline,
    loadVersions,
    create,
    save,
    confirm,
    remove,
    parse,
    upload,
    importUrl,
    updateStatus,
    polish,
  }
}
