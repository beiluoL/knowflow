<template>
  <CareerWorkbenchLayout subtitle="JD 导入与结构化要求确认；缺证据的技能保持「简历未体现」，不推断为不会。">
    <template #actions>
      <Button variant="secondary" size="sm" @click="router.push('/career/jobs/compare')">岗位对比</Button>
      <Button variant="secondary" size="sm" @click="router.push('/career/jobs/board')">投递看板</Button>
      <Button size="sm" @click="showImport = true">
        <Icon name="plus" :size="14" /><span>导入 JD</span>
      </Button>
    </template>

    <div class="job-list-toolbar">
      <Input
        v-model="keyword"
        placeholder="搜索岗位名称"
        prefix-icon-name="search"
        :maxlength="100"
        aria-label="搜索岗位"
      />
      <Select v-model="status" :options="statusOptions" aria-label="岗位状态筛选" />
      <Select v-model="sort" :options="sortOptions" aria-label="岗位排序" />
    </div>

    <p v-if="error" class="job-list-error" role="alert">{{ error }}</p>
    <StateView v-if="loading" status="loading" type="card" :cols="2" :rows="3" />
    <StateView
      v-else-if="error && !jobs.length"
      status="error"
      :error-description="error"
      retry-text="重新加载岗位"
      @retry="reload"
    />
    <EmptyState
      v-else-if="!jobs.length"
      icon="briefcase"
      :title="status || keyword ? '当前筛选下没有岗位' : '还没有目标岗位'"
      :description="status || keyword ? '调整筛选条件后再试。' : '导入真实 JD 后，AI 提取岗位要求并人工确认，才能进行智能匹配。'"
    >
      <template #action>
        <Button v-if="!status && !keyword" @click="showImport = true">导入第一个 JD</Button>
        <Button v-else variant="secondary" @click="clearFilters">清除筛选</Button>
      </template>
    </EmptyState>

    <div v-else class="job-list-grid">
      <article v-for="job in jobs" :key="job.id" class="job-card">
        <div class="job-card-head">
          <span class="job-card-icon"><Icon name="briefcase" :size="18" /></span>
          <div class="job-card-main">
            <p class="job-card-title">{{ job.title }}</p>
            <p class="job-card-meta">
              v{{ job.currentVersion }} · {{ job.confirmed ? '已确认' : '待确认' }}
              <template v-if="job.jobStatus"> · {{ JOB_STATUS_LABELS[job.jobStatus.status] }}</template>
            </p>
          </div>
          <JobStatusBadge v-if="job.jobStatus" :status="job.jobStatus.status" />
        </div>
        <div class="job-card-sections">
          <span v-for="section in usedSections(job)" :key="section.key" class="job-card-section">
            {{ section.label }} {{ job.sections[section.key]?.length ?? 0 }}
          </span>
          <span v-if="!usedSections(job).length" class="kf-text-caption">尚未提取结构化要求</span>
        </div>
        <div class="job-card-actions">
          <Button variant="secondary" size="sm" @click="router.push(`/career/jobs/${job.id}`)">
            <Icon name="file-text" :size="14" /><span>查看 JD</span>
          </Button>
          <div class="job-card-status">
            <Select
              :model-value="job.jobStatus?.status ?? 'SAVED'"
              :options="JOB_STATUS_OPTIONS"
              :disabled="busy"
              aria-label="更新岗位状态"
              @update:model-value="value => changeStatus(job.id, value)"
            />
          </div>
        </div>
      </article>
    </div>

    <JobImportDialog :open="showImport" @close="showImport = false" @created="onImported" />
  </CareerWorkbenchLayout>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Button from '@/components/ui/Button.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import Icon from '@/components/ui/Icon.vue'
import Input from '@/components/ui/Input.vue'
import Select from '@/components/ui/Select.vue'
import StateView from '@/components/ui/StateView.vue'
import CareerWorkbenchLayout from '@/components/career/CareerWorkbenchLayout.vue'
import JobImportDialog from '@/components/career/JobImportDialog.vue'
import JobStatusBadge from '@/components/career/JobStatusBadge.vue'
import { JOB_SECTIONS, JOB_STATUS_LABELS, JOB_STATUS_OPTIONS } from '@/components/career/career-labels'
import { useJob } from '@/composables/career/useJob'
import type { CareerRecord, JobStatus } from '@/api/career/types'
import { notify } from '@/utils/toast'

const route = useRoute()
const router = useRouter()
const showImport = ref(route.query.import === '1')
const keyword = ref('')
const status = ref<JobStatus | ''>((route.query.status as JobStatus | undefined) ?? '')
const sort = ref<'updated' | 'title' | 'status'>('updated')
let keywordTimer: ReturnType<typeof setTimeout> | undefined

const { jobs, loading, busy, error, load, updateStatus } = useJob()

const statusOptions = computed(() => [
  { value: '', label: '全部状态' },
  ...JOB_STATUS_OPTIONS,
])
const sortOptions = [
  { value: 'updated', label: '按更新时间' },
  { value: 'title', label: '按岗位名称' },
  { value: 'status', label: '按投递状态' },
]

async function reload(): Promise<void> {
  await load({ status: status.value, keyword: keyword.value, sort: sort.value })
}

function clearFilters(): void {
  keyword.value = ''
  status.value = ''
  sort.value = 'updated'
  void reload()
}

function usedSections(job: CareerRecord) {
  return JOB_SECTIONS.filter(section => (job.sections[section.key]?.length ?? 0) > 0)
}

async function changeStatus(id: number, value: string | number | null): Promise<void> {
  const next = String(value ?? '') as JobStatus
  if (!Object.prototype.hasOwnProperty.call(JOB_STATUS_LABELS, next)) return
  const job = jobs.value.find(item => item.id === id)
  const record = await updateStatus(id, next, job?.jobStatus?.note ?? '')
  if (!record) return
  notify('岗位状态已更新', 'success')
  await reload()
}

function onImported(record: CareerRecord): void {
  showImport.value = false
  notify('JD 原文已导入，请继续核对结构化要求', 'success')
  void router.push(`/career/jobs/${record.id}`)
}

watch([status, sort], () => void reload())
watch(keyword, () => {
  clearTimeout(keywordTimer)
  keywordTimer = setTimeout(() => void reload(), 300)
})
watch(() => route.query.import, value => {
  if (value === '1') showImport.value = true
})
watch(() => route.query.status, value => {
  status.value = typeof value === 'string' ? value as JobStatus : ''
})

onMounted(() => void reload())
</script>

<style scoped>
.job-list-toolbar { display: flex; align-items: center; gap: var(--kf-space-2); flex-wrap: wrap; }
.job-list-toolbar > :first-child { flex: 1 1 240px; max-width: 380px; }
.job-list-toolbar > :nth-child(2), .job-list-toolbar > :nth-child(3) { width: 150px; }
.job-list-error { margin: 0; color: var(--kf-state-error); font-size: var(--kf-fs-body-sm); }
.job-list-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(340px, 1fr)); gap: var(--kf-space-3); }
.job-card { display: flex; flex-direction: column; gap: var(--kf-space-3); padding: var(--kf-space-4); border: 1px solid var(--kf-border); border-radius: var(--kf-radius-md); background: var(--kf-surface-1); }
.job-card-head { display: flex; align-items: flex-start; gap: var(--kf-space-2); }
.job-card-icon { display: inline-flex; align-items: center; justify-content: center; width: 32px; height: 32px; border-radius: var(--kf-radius-sm); background: var(--kf-primary-subtle); color: var(--kf-primary); flex: none; }
.job-card-main { flex: 1; min-width: 0; }
.job-card-title { margin: 0; font-size: var(--kf-fs-body-emphasis); font-weight: 500; color: var(--kf-foreground); }
.job-card-meta { margin: 2px 0 0; font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
.job-card-sections { display: flex; flex-wrap: wrap; gap: var(--kf-space-1); }
.job-card-section { padding: 2px var(--kf-space-2); border-radius: var(--kf-radius-xs); background: var(--kf-surface-2); font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
.job-card-actions { display: flex; align-items: center; justify-content: space-between; gap: var(--kf-space-2); flex-wrap: wrap; }
.job-card-status { width: 140px; }
@media (max-width: 640px) {
  .job-list-toolbar > :first-child { flex-basis: 100%; max-width: none; }
  .job-list-toolbar > :nth-child(2), .job-list-toolbar > :nth-child(3) { width: 100%; }
  .job-list-grid { grid-template-columns: 1fr; }
}
</style>
