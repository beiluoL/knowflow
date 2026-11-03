<template>
  <CareerWorkbenchLayout subtitle="按真实投递状态推进岗位；状态变更保留历史。">
    <template #actions><Button variant="secondary" @click="router.push('/career/jobs')">岗位列表</Button></template>
    <Input v-model="keyword" aria-label="搜索看板岗位" placeholder="搜索岗位" />
    <StateView v-if="loading" status="loading" />
    <StateView v-else-if="error" status="error" :error-description="error" @retry="load()" />
    <EmptyState v-else-if="!jobs.length" title="还没有岗位" description="先到岗位中心导入真实 JD。" />
    <div v-else class="career-board">
      <section v-for="column in JOB_STATUS_OPTIONS" :key="column.value" class="career-board__column"
        @dragover.prevent @drop.prevent="drop(String(column.value))">
        <h2>{{ column.label }} · {{ inColumn(String(column.value)).length }}</h2>
        <article v-for="job in inColumn(String(column.value))" :key="job.id" class="career-board__card"
          :draggable="!busy" @dragstart="dragged = job.id" @dragend="dragged = null">
          <h3>{{ job.title }}</h3>
          <p>JD v{{ job.currentVersion }} · {{ job.confirmed ? '已确认' : '待确认' }}</p>
          <Select :model-value="job.jobStatus?.status ?? 'SAVED'" :options="JOB_STATUS_OPTIONS" :disabled="busy"
            :aria-label="`${job.title}投递状态`" @update:model-value="value => move(job.id, String(value))" />
          <Button variant="secondary" size="sm" @click="open(job)">查看详情与历程</Button>
        </article>
      </section>
    </div>
    <section v-if="selected" class="career-board__history" aria-label="岗位真实历程">
      <h2>{{ selected.title }} · 求职历程</h2>
      <p v-if="historyError" role="alert">{{ historyError }}</p>
      <p v-else-if="historyLoading">正在读取历程…</p>
      <p v-else-if="!events.length">暂无已记录的状态变化</p>
      <p v-for="event in events" :key="event.id">
        {{ event.occurred_at }} · {{ label(event.from_status) }} → {{ label(event.to_status) }} · {{ event.note }}
      </p>
      <Button @click="router.push(`/career/jobs/${selected.id}`)">打开岗位详情</Button>
    </section>
  </CareerWorkbenchLayout>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import CareerWorkbenchLayout from '@/components/career/CareerWorkbenchLayout.vue'
import Button from '@/components/ui/Button.vue'
import Input from '@/components/ui/Input.vue'
import Select from '@/components/ui/Select.vue'
import StateView from '@/components/ui/StateView.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import { JOB_STATUS_OPTIONS, JOB_STATUS_LABELS } from '@/components/career/career-labels'
import { useJob } from '@/composables/career/useJob'
import { getJobEvents, type JobEvent } from '@/api/career/job'
import type { CareerRecord, JobStatus } from '@/api/career/types'
import { getApiError } from '@/utils/toast'
const router = useRouter()
const { jobs, loading, busy, error, load, updateStatus } = useJob()
const keyword = ref('')
const dragged = ref<number | null>(null)
const selected = ref<CareerRecord | null>(null)
const events = ref<JobEvent[]>([])
const historyError = ref('')
const historyLoading = ref(false)
const filtered = computed(() => jobs.value.filter(job => job.title.toLowerCase().includes(keyword.value.trim().toLowerCase())))
const inColumn = (status: string) => filtered.value.filter(job => (job.jobStatus?.status ?? 'SAVED') === status)
const label = (status: JobStatus) => JOB_STATUS_LABELS[status]
async function move(id: number, status: string): Promise<void> {
  if (busy.value || !Object.prototype.hasOwnProperty.call(JOB_STATUS_LABELS, status)) return
  const job = jobs.value.find(item => item.id === id)
  if (!job || job.jobStatus?.status === status) return
  // 等待服务端确认，失败时原卡片留在原列。
  const result = await updateStatus(id, status as JobStatus, job.jobStatus?.note ?? '')
  if (result) await load()
}
async function drop(status: string): Promise<void> {
  const id = dragged.value
  dragged.value = null
  if (id !== null) await move(id, status)
}
async function open(job: CareerRecord): Promise<void> {
  selected.value = job
  events.value = []
  historyError.value = ''
  historyLoading.value = true
  try { const result = await getJobEvents(job.id); if (selected.value?.id === job.id) events.value = result }
  catch (failure: unknown) { historyError.value = getApiError(failure, '历程读取失败') }
  finally { historyLoading.value = false }
}
onMounted(() => load())
</script>
<style scoped>
.career-board { display: grid; grid-template-columns: repeat(8, minmax(220px, 1fr)); gap: var(--kf-space-3); overflow-x: auto; padding-block: var(--kf-space-3); }
.career-board__column { padding: var(--kf-space-3); background: var(--kf-surface-2); border-radius: var(--kf-radius-md); }
.career-board__column h2 { font-size: var(--kf-fs-body); margin-bottom: var(--kf-space-3); }
.career-board__card { display: grid; gap: var(--kf-space-2); background: var(--kf-surface-1); padding: var(--kf-space-3); margin-bottom: var(--kf-space-3); border-radius: var(--kf-radius-md); }
.career-board__card p { color: var(--kf-muted-foreground); font-size: var(--kf-fs-caption); }
.career-board__history { padding: var(--kf-space-4); background: var(--kf-surface-1); }
@media (max-width: 600px) { .career-board { grid-template-columns: 1fr; overflow-x: visible; } }
</style>
