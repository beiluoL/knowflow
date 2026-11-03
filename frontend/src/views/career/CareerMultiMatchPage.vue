<template>
  <CareerWorkbenchLayout subtitle="一个岗位比较全部已确认简历，或一份简历批量比较多个岗位；分数由代码计算，证据来自原文。">
    <template #actions>
      <Button variant="secondary" size="sm" @click="router.push('/career/matches')">
        <Icon name="arrow-left" :size="14" /><span>返回匹配列表</span>
      </Button>
    </template>

    <section class="mm-panel" aria-labelledby="mm-one-job">
      <div class="mm-head">
        <h2 id="mm-one-job" class="kf-text-section-title">一个岗位 · 多份简历</h2>
        <span class="kf-text-caption">只比较已确认版本</span>
      </div>
      <div class="mm-controls">
        <label class="mm-field">
          <span>目标岗位</span>
          <Select v-model="jobId" :options="jobOptions" placeholder="选择已确认岗位" aria-label="目标岗位" />
        </label>
        <Button :disabled="!jobId" :loading="matching" @click="runMulti">开始比较</Button>
      </div>

      <StateView v-if="matching" status="loading" type="card" :cols="2" :rows="2" />
      <ErrorState v-else-if="error" title="比较没有完成" :description="error" @retry="runMulti" />
      <EmptyState
        v-else-if="multi && !multi.candidates.length"
        icon="file-text"
        title="没有可比较的简历"
        description="请先在简历中心确认至少一份简历版本。"
      />

      <template v-else-if="multi">
        <p class="mm-summary">
          岗位版本 v{{ multi.jobVersion }} · 推荐：
          <strong>{{ recommendedTitle }}</strong>
          <span class="kf-text-caption">（{{ multi.scoreMeaning }}）</span>
        </p>
        <div class="mm-table" role="table" aria-label="多简历匹配结果">
          <div class="mm-row mm-row--head" role="row">
            <span role="columnheader">简历</span>
            <span role="columnheader">必备技能覆盖</span>
            <span role="columnheader">证据</span>
            <span role="columnheader">说明</span>
            <span role="columnheader">操作</span>
          </div>
          <div v-for="row in multi.candidates" :key="row.resumeId" class="mm-row" role="row">
            <span class="mm-cell-title" role="cell">
              {{ row.resumeTitle }} <span class="kf-text-caption">v{{ row.resumeVersion }}</span>
              <span v-if="row.recommended" class="mm-badge">推荐</span>
              <span v-else-if="row.tie" class="mm-badge is-muted">并列</span>
            </span>
            <span class="mm-cell-coverage" role="cell">
              <span class="mm-bar"><span class="mm-bar-fill" :style="{ width: `${row.coverage ?? 0}%` }" /></span>
              <span class="mm-value">{{ row.coverage === null ? '—' : `${Math.round(row.coverage)}%` }}</span>
            </span>
            <span role="cell">{{ row.matchedCount }}/{{ row.requiredCount }}</span>
            <span class="mm-reason" role="cell">{{ row.reason }}</span>
            <span class="mm-cell-actions" role="cell">
              <Button variant="text" size="sm" @click="router.push(`/career/matches/${row.matchId}`)">看证据</Button>
              <Button variant="text" size="sm" @click="tailor(row)">去定制</Button>
            </span>
          </div>
        </div>
      </template>
      <EmptyState v-else icon="target" title="选择岗位后开始比较" description="不会修改任何简历或岗位版本。" />
    </section>

    <section class="mm-panel" aria-labelledby="mm-many-jobs">
      <div class="mm-head">
        <h2 id="mm-many-jobs" class="kf-text-section-title">一份简历 · 多个岗位</h2>
        <span class="kf-text-caption">单次最多 10 个岗位</span>
      </div>
      <div class="mm-controls">
        <label class="mm-field">
          <span>简历</span>
          <Select v-model="resumeId" :options="resumeOptions" placeholder="选择已确认简历" aria-label="简历" />
        </label>
        <Button :disabled="!resumeId || !selectedJobIds.length" :loading="batching" @click="runBatch">
          批量比较（{{ selectedJobIds.length }}）
        </Button>
      </div>

      <div class="mm-job-picker" role="group" aria-label="选择岗位">
        <label v-for="job in confirmedJobs" :key="job.id" class="mm-job-option">
          <Checkbox
            :model-value="selectedJobIds.includes(job.id)"
            @update:model-value="(checked: boolean) => toggleJob(job.id, checked)"
          />
          <span class="mm-job-title">{{ job.title }}</span>
          <span class="kf-text-caption">v{{ job.currentVersion }}</span>
        </label>
        <p v-if="!confirmedJobs.length" class="kf-text-caption">还没有已确认岗位，请先导入并确认 JD。</p>
      </div>

      <ErrorState v-if="batchError" title="批量比较没有完成" :description="batchError" @retry="runBatch" />
      <ul v-else-if="batch" class="mm-batch">
        <li v-for="row in batch.jobs" :key="row.matchId" class="mm-batch-row">
          <span class="mm-cell-title">{{ row.jobTitle || ('岗位 #' + row.jobId) }}</span>
          <span class="mm-cell-coverage">
            <span class="mm-bar"><span class="mm-bar-fill" :style="{ width: `${row.coverage ?? 0}%` }" /></span>
            <span class="mm-value">{{ row.coverage === null ? '—' : `${Math.round(row.coverage)}%` }}</span>
          </span>
          <Button variant="text" size="sm" @click="router.push(`/career/matches/${row.matchId}`)">看证据</Button>
        </li>
        <li v-if="batch.failures.length" class="mm-failures">
          失败 {{ batch.failures.length }} 个：{{ batch.failures.map((f) => `#${f.jobId} ${f.message}`).join('；') }}
        </li>
      </ul>
    </section>
  </CareerWorkbenchLayout>
</template>

<script setup lang="ts">
/**
 * 多简历 / 多岗位匹配页。
 *
 * 不在这里算分：每次比较都调用后端，逐条复用单条匹配并生成快照，
 * 因此「多简历结果」与「匹配详情」的口径永远一致。
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CareerWorkbenchLayout from '@/components/career/CareerWorkbenchLayout.vue'
import Button from '@/components/ui/Button.vue'
import Icon from '@/components/ui/Icon.vue'
import Select from '@/components/ui/Select.vue'
import Checkbox from '@/components/ui/Checkbox.vue'
import StateView from '@/components/ui/StateView.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import { listJobs } from '@/api/career/job'
import { listResumes } from '@/api/career/resume'
import { batchMatchJobs, matchJobWithResumes } from '@/api/career/matching'
import type { BatchMatchResult, CareerRecord, MultiResumeCandidate, MultiResumeResult } from '@/api/career/types'
import { getApiError, notify } from '@/utils/toast'

const router = useRouter()
const route = useRoute()

const jobs = ref<CareerRecord[]>([])
const resumes = ref<CareerRecord[]>([])
const jobId = ref<number | null>(null)
const resumeId = ref<number | null>(null)
const selectedJobIds = ref<number[]>([])
const matching = ref(false)
const batching = ref(false)
const error = ref('')
const batchError = ref('')
const multi = ref<MultiResumeResult | null>(null)
const batch = ref<BatchMatchResult | null>(null)

const confirmedJobs = computed(() => jobs.value.filter((job) => job.confirmed))
const jobOptions = computed(() =>
  confirmedJobs.value.map((job) => ({ label: `${job.title} · v${job.currentVersion}`, value: job.id })),
)
const resumeOptions = computed(() =>
  resumes.value.filter((item) => item.confirmed)
    .map((item) => ({ label: `${item.title} · v${item.currentVersion}`, value: item.id })),
)
const recommendedTitle = computed(() => {
  if (!multi.value?.recommendedResumeId) return '覆盖接近，建议人工比较'
  const hit = multi.value.candidates.find((row) => row.resumeId === multi.value?.recommendedResumeId)
  return hit ? `${hit.resumeTitle} v${hit.resumeVersion}` : '—'
})

function toggleJob(id: number, checked: boolean): void {
  const set = new Set(selectedJobIds.value)
  if (checked) set.add(id)
  else set.delete(id)
  selectedJobIds.value = [...set].slice(0, 10)
}

async function runMulti(): Promise<void> {
  if (!jobId.value) return
  matching.value = true
  error.value = ''
  try {
    multi.value = await matchJobWithResumes(jobId.value)
  } catch (failure) {
    error.value = getApiError(failure, '多简历比较没有完成')
    multi.value = null
  } finally {
    matching.value = false
  }
}

async function runBatch(): Promise<void> {
  if (!resumeId.value || !selectedJobIds.value.length) return
  const resume = resumes.value.find((item) => item.id === resumeId.value)
  if (!resume) return
  batching.value = true
  batchError.value = ''
  try {
    batch.value = await batchMatchJobs(resume.id, resume.currentVersion, selectedJobIds.value)
    if (batch.value.failures.length) {
      notify(`部分岗位未能匹配（${batch.value.failures.length} 个）`, 'info')
    }
  } catch (failure) {
    batchError.value = getApiError(failure, '批量比较没有完成')
    batch.value = null
  } finally {
    batching.value = false
  }
}

function tailor(row: MultiResumeCandidate): void {
  router.push({
    path: '/career/tools/tailor',
    query: { resumeId: String(row.resumeId), jobId: String(jobId.value || '') },
  })
}

onMounted(async () => {
  try {
    const [jobList, resumeList] = await Promise.all([listJobs(), listResumes()])
    jobs.value = jobList
    resumes.value = resumeList
    const queryJob = Number(route.query.jobId)
    if (jobList.some((job) => job.id === queryJob && job.confirmed)) {
      jobId.value = queryJob
      await runMulti()
    } else if (confirmedJobs.value.length) {
      jobId.value = confirmedJobs.value[0].id
    }
    const confirmedResumes = resumeList.filter((item) => item.confirmed)
    if (confirmedResumes.length) resumeId.value = confirmedResumes[0].id
  } catch (failure) {
    error.value = getApiError(failure, '岗位与简历列表加载失败')
  }
})
</script>

<style scoped>
.mm-panel {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-3);
  padding: var(--kf-space-4);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  background: var(--kf-surface-1);
  min-width: 0;
}
.mm-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kf-space-3);
  flex-wrap: wrap;
}
.mm-controls {
  display: flex;
  align-items: flex-end;
  gap: var(--kf-space-3);
  flex-wrap: wrap;
}
.mm-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 260px;
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.mm-summary {
  margin: 0;
  font-size: var(--kf-fs-body-sm);
  color: var(--kf-foreground);
}
.mm-table {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-sm);
  overflow: hidden;
}
.mm-row {
  display: grid;
  grid-template-columns: 1.6fr 1.4fr 0.6fr 1.8fr 1fr;
  gap: var(--kf-space-2);
  align-items: center;
  padding: var(--kf-space-2) var(--kf-space-3);
  border-bottom: 1px solid var(--kf-border);
  font-size: var(--kf-fs-body-sm);
}
.mm-row:last-child {
  border-bottom: 0;
}
.mm-row--head {
  background: var(--kf-surface-2);
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.mm-cell-title {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  flex-wrap: wrap;
}
.mm-badge {
  font-size: var(--kf-fs-caption);
  padding: 0 6px;
  border-radius: 999px;
  background: var(--kf-primary-subtle);
  color: var(--kf-primary);
}
.mm-badge.is-muted {
  background: var(--kf-surface-2);
  color: var(--kf-muted-foreground);
}
.mm-cell-coverage {
  display: flex;
  align-items: center;
  gap: 8px;
}
.mm-bar {
  flex: 1;
  height: 6px;
  border-radius: 999px;
  background: var(--kf-surface-2);
  overflow: hidden;
  min-width: 60px;
}
.mm-bar-fill {
  display: block;
  height: 100%;
  background: var(--kf-primary);
}
.mm-value {
  width: 42px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}
.mm-reason {
  color: var(--kf-muted-foreground);
  font-size: var(--kf-fs-caption);
  line-height: var(--kf-lh-caption);
}
.mm-cell-actions {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}
.mm-job-picker {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 6px;
  max-height: 220px;
  overflow: auto;
  padding: var(--kf-space-2);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-sm);
  background: var(--kf-surface-2);
}
.mm-job-option {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: var(--kf-fs-body-sm);
  min-width: 0;
}
.mm-job-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.mm-batch {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.mm-batch-row {
  display: grid;
  grid-template-columns: 1.6fr 1.4fr auto;
  gap: var(--kf-space-2);
  align-items: center;
  padding: var(--kf-space-2) 0;
  border-bottom: 1px solid var(--kf-border);
  font-size: var(--kf-fs-body-sm);
}
.mm-failures {
  color: var(--kf-warning-foreground, var(--kf-foreground));
  font-size: var(--kf-fs-caption);
}
@media (max-width: 1024px) {
  .mm-row {
    grid-template-columns: 1.4fr 1.2fr 0.6fr 1.4fr;
  }
  .mm-row > :nth-child(2) {
    display: none;
  }
}
@media (max-width: 640px) {
  .mm-row {
    grid-template-columns: minmax(0, 1fr) auto;
    row-gap: 4px;
  }
  .mm-row > :nth-child(2),
  .mm-row > :nth-child(3),
  .mm-row > :nth-child(4) {
    display: none;
  }
  .mm-panel {
    padding: var(--kf-space-3);
  }
}
</style>
