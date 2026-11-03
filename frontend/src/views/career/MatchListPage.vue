<template>
  <CareerWorkbenchLayout subtitle="LLM 只负责要求提取，规则引擎负责原文证据与可解释评分；每次匹配生成独立不可变快照。">
    <template #actions>
      <Button variant="secondary" size="sm" :disabled="loading" @click="reload">
        <Icon name="refresh-cw" :size="14" /><span>刷新</span>
      </Button>
      <Button size="sm" :disabled="busy || !confirmedResumes.length || !confirmedJobs.length" @click="showCreate = !showCreate">
        <Icon name="target" :size="14" /><span>{{ showCreate ? '收起新建' : '开始匹配' }}</span>
      </Button>
    </template>

    <section v-if="showCreate" class="match-create" aria-label="新建匹配任务">
      <div class="match-create-head">
        <h3 class="kf-text-card-title">新建匹配任务</h3>
        <span class="kf-text-caption">只能选择已确认的简历版本与岗位版本</span>
      </div>
      <div class="match-create-fields">
        <label class="match-create-field">
          <span>简历</span>
          <Select v-model="resumeId" :options="resumeOptions" aria-label="选择简历" />
        </label>
        <label class="match-create-field">
          <span>简历版本</span>
          <Select v-model="resumeVersion" :options="resumeVersionOptions" aria-label="选择简历版本" />
        </label>
        <label class="match-create-field">
          <span>目标岗位</span>
          <Select v-model="jobId" :options="jobOptions" aria-label="选择目标岗位" />
        </label>
        <label class="match-create-field">
          <span>JD 版本</span>
          <Select v-model="jobVersion" :options="jobVersionOptions" aria-label="选择 JD 版本" />
        </label>
      </div>
      <p v-if="createError" class="match-create-error" role="alert">{{ createError }}</p>
      <p class="kf-text-caption">
        缺少简历证据的必备技能会标记为「简历未体现」，不会直接推断为不会，也不会补造项目经历。
      </p>
      <div class="match-create-actions">
        <Button :disabled="busy || !resumeId || !jobId" :loading="busy" @click="runMatch">
          <Icon v-if="!busy" name="target" :size="14" /><span>计算并保存匹配快照</span>
        </Button>
      </div>
    </section>

    <p v-if="error" class="match-list-error" role="alert">{{ error }}</p>
    <StateView v-if="loading" status="loading" type="card" :cols="2" :rows="3" />
    <StateView
      v-else-if="error && !matches.length"
      status="error"
      :error-description="error"
      retry-text="重新加载匹配历史"
      @retry="reload"
    />
    <EmptyState
      v-else-if="!matches.length"
      icon="target"
      title="还没有匹配记录"
      description="先确认一份简历和一个岗位 JD，再运行匹配。每个快照都会冻结当时的版本、评分与证据。"
    >
      <template #action>
        <Button v-if="confirmedResumes.length && confirmedJobs.length" @click="showCreate = true">开始第一次匹配</Button>
        <Button v-else variant="secondary" @click="router.push('/career/resumes')">先去确认简历</Button>
      </template>
    </EmptyState>

    <div v-else class="match-list">
      <article v-for="item in matches" :key="item.id" class="match-card">
        <div class="match-card-main">
          <div class="match-card-head">
            <span class="match-card-icon"><Icon name="target" :size="18" /></span>
            <div>
              <p class="match-card-title">{{ item.jobTitle }}</p>
              <p class="match-card-meta">
                简历 v{{ item.resumeVersion }} → JD v{{ item.jobVersion }}
                · {{ formatDateTime(item.createdAt) }}
              </p>
            </div>
          </div>
          <p class="match-card-version">{{ item.policyVersion }} · 评估覆盖 {{ coverage(item.evaluationCoverage) }}</p>
        </div>
        <div class="match-card-score">
          <span class="match-card-score-value">{{ item.score == null ? '—' : `${item.score.toFixed(1)}%` }}</span>
          <span class="match-card-score-label">必备技能覆盖率</span>
        </div>
        <Button variant="secondary" size="sm" @click="router.push(`/career/matches/${item.id}`)">
          <Icon name="external-link" :size="14" /><span>查看证据</span>
        </Button>
      </article>
    </div>
  </CareerWorkbenchLayout>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Button from '@/components/ui/Button.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import Icon from '@/components/ui/Icon.vue'
import Select from '@/components/ui/Select.vue'
import StateView from '@/components/ui/StateView.vue'
import CareerWorkbenchLayout from '@/components/career/CareerWorkbenchLayout.vue'
import { formatDateTime } from '@/components/career/career-labels'
import { useJobMatch } from '@/composables/career/useJobMatch'
import { listResumeVersions, listResumes } from '@/api/career/resume'
import { listJobVersions, listJobRecords } from '@/api/career/job'
import type { CareerRecord, CareerVersion } from '@/api/career/types'
import { getApiError, notify } from '@/utils/toast'

const route = useRoute()
const router = useRouter()
const { matches, loading, busy, error, load, create } = useJobMatch()

const showCreate = ref(route.query.create === '1')
const resumeRecords = ref<CareerRecord[]>([])
const jobRecords = ref<CareerRecord[]>([])
const resumeId = ref<number | null>(null)
const jobId = ref<number | null>(null)
const resumeVersion = ref<number | null>(null)
const jobVersion = ref<number | null>(null)
const resumeVersions = ref<CareerVersion[]>([])
const jobVersions = ref<CareerVersion[]>([])
const createError = ref('')

const confirmedResumes = computed(() => resumeRecords.value.filter(record => record.confirmed))
const confirmedJobs = computed(() => jobRecords.value.filter(record => record.confirmed))
const resumeOptions = computed(() => confirmedResumes.value.map(record => ({ value: record.id, label: record.title })))
const jobOptions = computed(() => confirmedJobs.value.map(record => ({ value: record.id, label: record.title })))
const resumeVersionOptions = computed(() =>
  resumeVersions.value.filter(version => version.confirmed).map(version => ({ value: version.version, label: `v${version.version}` })),
)
const jobVersionOptions = computed(() =>
  jobVersions.value.filter(version => version.confirmed).map(version => ({ value: version.version, label: `v${version.version}` })),
)

function coverage(value: number | null): string {
  if (value == null) return '—'
  return `${Math.round(value * 100)}%`
}

async function loadOptions(): Promise<void> {
  try {
    const [resumes, jobs] = await Promise.all([listResumes(), listJobRecords()])
    resumeRecords.value = resumes
    jobRecords.value = jobs
    resumeId.value = confirmedResumes.value[0]?.id ?? null
    jobId.value = confirmedJobs.value[0]?.id ?? null
  } catch (e: unknown) {
    createError.value = getApiError(e, '简历或岗位列表加载失败')
  }
}

async function loadVersions(): Promise<void> {
  if (resumeId.value) {
    try {
      resumeVersions.value = await listResumeVersions(resumeId.value)
      resumeVersion.value = resumeVersions.value.find(version => version.confirmed)?.version ?? null
    } catch (e: unknown) {
      createError.value = getApiError(e, '简历版本加载失败')
    }
  } else {
    resumeVersions.value = []
    resumeVersion.value = null
  }
  if (jobId.value) {
    try {
      jobVersions.value = await listJobVersions(jobId.value)
      jobVersion.value = jobVersions.value.find(version => version.confirmed)?.version ?? null
    } catch (e: unknown) {
      createError.value = getApiError(e, 'JD 版本加载失败')
    }
  } else {
    jobVersions.value = []
    jobVersion.value = null
  }
}

async function runMatch(): Promise<void> {
  if (!resumeId.value || !resumeVersion.value || !jobId.value || !jobVersion.value) return
  createError.value = ''
  const result = await create(resumeId.value, resumeVersion.value, jobId.value, jobVersion.value)
  if (!result) return
  notify('匹配快照已生成', 'success')
  showCreate.value = false
  await reload()
  void router.push(`/career/matches/${result.id}`)
}

async function reload(): Promise<void> {
  await load()
}

watch(resumeId, () => void loadVersions())
watch(jobId, () => void loadVersions())
watch(() => route.query.create, value => { if (value === '1') showCreate.value = true })

onMounted(() => {
  void load()
  void loadOptions()
})
</script>

<style scoped>
.match-create { display: flex; flex-direction: column; gap: var(--kf-space-2); padding: var(--kf-space-4); border: 1px solid var(--kf-border); border-radius: var(--kf-radius-md); background: var(--kf-surface-1); }
.match-create-head { display: flex; align-items: baseline; justify-content: space-between; gap: var(--kf-space-2); flex-wrap: wrap; }
.match-create-head h3 { margin: 0; }
.match-create-fields { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--kf-space-2); }
.match-create-field { display: flex; flex-direction: column; gap: var(--kf-space-1); font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
.match-create-error, .match-list-error { margin: 0; color: var(--kf-state-error); font-size: var(--kf-fs-body-sm); }
.match-create-actions { display: flex; justify-content: flex-end; }
.match-list { display: flex; flex-direction: column; gap: var(--kf-space-2); }
.match-card { display: grid; grid-template-columns: minmax(0, 1fr) auto auto; align-items: center; gap: var(--kf-space-4); padding: var(--kf-space-3); border: 1px solid var(--kf-border); border-radius: var(--kf-radius-md); background: var(--kf-surface-1); }
.match-card-main { display: flex; flex-direction: column; gap: var(--kf-space-1); min-width: 0; }
.match-card-head { display: flex; align-items: center; gap: var(--kf-space-2); min-width: 0; }
.match-card-icon { display: inline-flex; align-items: center; justify-content: center; width: 30px; height: 30px; border-radius: var(--kf-radius-sm); background: var(--kf-primary-subtle); color: var(--kf-primary); flex: none; }
.match-card-title { margin: 0; font-size: var(--kf-fs-body-sm); font-weight: 500; color: var(--kf-foreground); }
.match-card-meta, .match-card-version { margin: 0; font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
.match-card-score { display: flex; flex-direction: column; align-items: flex-end; }
.match-card-score-value { font-size: var(--kf-fs-h4); font-weight: 600; color: var(--kf-foreground); font-variant-numeric: tabular-nums; }
.match-card-score-label { font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
@media (max-width: 760px) {
  .match-create-fields { grid-template-columns: 1fr; }
  .match-card { grid-template-columns: minmax(0, 1fr) auto; }
  .match-card > :last-child { grid-column: 1 / -1; justify-self: start; }
}
</style>
