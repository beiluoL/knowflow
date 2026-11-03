import { onBeforeUnmount, ref } from 'vue'
import { deleteResumeDraft, getResumeDraft, saveResumeDraft, type ResumeDraft } from '@/api/career/draft'
import type { CareerEdit } from '@/api/career/types'
import { getApiError } from '@/utils/toast'

/** 草稿仅写本人服务端空间；正式版本保存由原编辑器显式触发。 */
export function useResumeDraft(read: () => CareerEdit, apply: (value: CareerEdit) => void) {
  const status = ref('草稿尚未加载')
  const recovery = ref<ResumeDraft | null>(null)
  const ready = ref(false)
  const failed = ref(false)
  let id = 0
  let base = 0
  let revision = 0
  let epoch = 0
  let blocked = false
  let savedPayload = ''
  let timer: ReturnType<typeof setTimeout> | undefined
  let pending: Promise<void> = Promise.resolve()
  async function initialize(recordId: number, version: number): Promise<void> {
    clearTimeout(timer)
    const sequence = ++epoch
    const initial = JSON.stringify(read())
    savedPayload = initial
    id = recordId; base = version; blocked = false; failed.value = false; ready.value = false; recovery.value = null
    status.value = '正在读取草稿…'
    try {
      const result = await getResumeDraft(recordId)
      if (sequence !== epoch) return
      revision = result.revision
      recovery.value = result.payload ? result : null
      status.value = result.payload ? `发现基于 v${result.baseVersion} 的草稿，请选择恢复或丢弃` : '草稿已就绪'
      ready.value = true
      if (!result.payload && initial !== JSON.stringify(read())) schedule()
    } catch (failure: unknown) { if (sequence === epoch) { failed.value = true; status.value = getApiError(failure, '草稿读取失败，请重试') } }
  }
  function schedule(): void {
    if (!ready.value || recovery.value || blocked) return
    clearTimeout(timer)
    status.value = '有修改，等待保存草稿…'
    timer = setTimeout(() => { void flush() }, 800)
  }
  function flush(): Promise<void> {
    clearTimeout(timer)
    if (!ready.value || recovery.value || blocked) return pending
    const sequence = epoch
    const recordId = id
    const version = base
    const serialized = JSON.stringify(read())
    if (serialized === savedPayload) return pending
    const payload = JSON.parse(serialized) as CareerEdit
    pending = pending.then(async () => {
      if (sequence !== epoch || blocked || serialized === savedPayload) return
      status.value = '正在保存草稿…'
      try {
        const result = await saveResumeDraft(recordId, revision, version, payload)
        if (sequence !== epoch) return
        revision = result.revision
        savedPayload = serialized
        failed.value = false
        status.value = `草稿已保存 · ${result.updatedAt ?? ''}`
      } catch (failure: unknown) {
        if (sequence !== epoch) return
        blocked = true
        failed.value = true
        status.value = getApiError(failure, '草稿保存失败；修改仍在编辑器中，请重试')
      }
    })
    return pending
  }
  function restore(): void {
    if (!recovery.value?.payload) return
    base = recovery.value.baseVersion
    apply(recovery.value.payload)
    recovery.value = null
    savedPayload = JSON.stringify(read())
    status.value = '草稿已恢复，请校对后正式保存；历史版本不会被覆盖'
  }
  async function clear(): Promise<void> {
    clearTimeout(timer)
    await pending
    if (revision) { const result = await deleteResumeDraft(id, revision); revision = result.revision }
    savedPayload = JSON.stringify(read()); recovery.value = null; status.value = '草稿已清除'
  }
  async function discard(): Promise<void> {
    try { await clear() } catch (failure: unknown) { status.value = getApiError(failure, '草稿清除失败') }
  }
  function retry(): void { blocked = false; if (ready.value) void flush(); else void initialize(id, base) }
  onBeforeUnmount(() => { clearTimeout(timer); if (ready.value && !recovery.value && !blocked) void flush() })
  return { status, recovery, ready, failed, initialize, schedule, flush, restore, discard, clear, retry }
}
