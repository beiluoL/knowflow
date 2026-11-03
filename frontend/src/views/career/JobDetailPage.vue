<template>
  <CareerWorkbenchLayout subtitle="左侧保留 JD 原文，右侧是可核对的结构化岗位要求；两边都绑定同一不可变版本。">
    <template #actions>
      <Button variant="secondary" size="sm" @click="router.push('/career/jobs')">
        <Icon name="arrow-left" :size="14" /><span>返回岗位中心</span>
      </Button>
      <Button
        variant="secondary"
        size="sm"
        :disabled="busy || !record"
        @click="parseWithConsent"
      >
        <Icon name="sparkles" :size="14" /><span>AI 提取要求</span>
      </Button>
      <Button
        variant="secondary"
        size="sm"
        :disabled="busy || !record || record.confirmed"
        @click="confirmVersion"
      >
        <Icon name="check" :size="14" /><span>{{ record?.confirmed ? '已确认' : '确认岗位版本' }}</span>
      </Button>
      <Button size="sm" :disabled="busy || !canSave" :loading="busy" @click="saveJob">
        <Icon v-if="!busy" name="save" :size="14" /><span>保存新版本</span>
      </Button>
    </template>

    <StateView v-if="loading && !record" status="loading" type="card" :cols="2" :rows="3" />
    <StateView
      v-else-if="error && !record"
      status="error"
      :error-description="error"
      retry-text="重新加载岗位"
      @retry="reload"
    />
    <template v-else-if="record">
      <p v-if="error" class="job-detail-error" role="alert">{{ error }}</p>
      <p v-if="message" class="job-detail-message" role="status">{{ message }}</p>

      <section class="job-detail-summary">
        <div class="job-detail-summary-main">
          <Input v-model="title" :maxlength="160" aria-label="岗位名称" />
          <div class="job-detail-meta">
            <JobStatusBadge :status="pipeline?.status ?? 'SAVED'" />
            <span>v{{ record.version }} · {{ record.confirmed ? '已确认' : '待确认' }}</span>
            <span v-if="!record.confirmed" class="job-detail-warning">AI 提取或手工修改后必须确认才会参与匹配</span>
          </div>
        </div>
        <div class="job-detail-pipeline">
          <Select
            :model-value="pipeline?.status ?? 'SAVED'"
            :options="JOB_STATUS_OPTIONS"
            :disabled="busy"
            aria-label="岗位状态"
            @update:model-value="changeStatus"
          />
          <Input v-model="note" :maxlength="2000" placeholder="投递备注（可选）" aria-label="投递备注" @blur="saveNote" />
        </div>
      </section>

      <div class="job-detail-body">
        <section class="job-detail-source" aria-label="JD 原文">
          <div class="job-detail-panel-head">
            <h3 class="kf-text-card-title">JD 原文</h3>
            <span class="kf-text-caption">{{ source.length }} / 50000 字</span>
          </div>
          <Textarea v-model="source" :rows="26" :maxlength="50000" aria-label="JD 原文" />
        </section>
        <section class="job-detail-requirements" aria-label="结构化岗位要求">
          <JobRequirementPanel
            v-model:sections="sections"
            :source="source"
            editable
            :busy="busy"
          />
        </section>
      </div>

      <section class="job-detail-history">
        <ResumeVersionHistory
          :versions="versions"
          :current-version="record.currentVersion"
          :active-version="record.version"
          @select="selectVersion"
        />
      </section>

      <section class="job-detail-interviews" aria-label="本岗位面试记录">
        <div class="job-detail-interviews-head">
          <h3 class="kf-text-card-title">本岗位面试记录</h3>
          <span class="kf-text-caption">{{ jobPlans.length }} 份方案 · {{ jobSessions.length }} 场会话</span>
        </div>
        <p v-if="!jobPlans.length" class="kf-text-caption">本岗位还没有岗位定制面试方案。</p>
        <article v-for="plan in jobPlans" :key="plan.id" class="job-detail-interview">
          <div>
            <p class="job-detail-interview-title">方案 #{{ plan.id }} · {{ plan.questionCount }} 题</p>
            <p class="kf-text-caption">{{ formatDateTime(plan.createdAt) }} · {{ plan.difficulty }}</p>
          </div>
          <router-link :to="`/career/interviews/${plan.id}`">
            <Icon name="external-link" :size="14" /><span>查看方案</span>
          </router-link>
          <div class="job-detail-interview-sessions">
            <router-link
              v-for="session in sessionsFor(plan.id)"
              :key="session.sessionId"
              :to="`/interview?v2Id=${session.sessionId}`"
            >
              <Icon name="mic" :size="14" /><span>{{ session.stateLabel }}</span>
            </router-link>
            <span v-if="!sessionsFor(plan.id).length" class="kf-text-caption">尚未开始</span>
          </div>
        </article>
      </section>
    </template>
  </CareerWorkbenchLayout>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Button from '@/components/ui/Button.vue'
import Icon from '@/components/ui/Icon.vue'
import Input from '@/components/ui/Input.vue'
import Select from '@/components/ui/Select.vue'
import StateView from '@/components/ui/StateView.vue'
import Textarea from '@/components/ui/Textarea.vue'
import CareerWorkbenchLayout from '@/components/career/CareerWorkbenchLayout.vue'
import JobRequirementPanel from '@/components/career/JobRequirementPanel.vue'
import JobStatusBadge from '@/components/career/JobStatusBadge.vue'
import ResumeVersionHistory from '@/components/career/ResumeVersionHistory.vue'
import { JOB_SECTIONS, JOB_STATUS_OPTIONS, clonePlain, formatDateTime } from '@/components/career/career-labels'
import { useJob } from '@/composables/career/useJob'
import { listPlans, listCareerInterviews } from '@/api/career/interview'
import type { CareerClaim, CareerInterviewSummary, CareerRecord, JobStatus, PlanSummary } from '@/api/career/types'
import { confirmDialog, notify } from '@/utils/toast'

const route = useRoute()
const router = useRouter()
const {
  current,
  pipeline,
  versions,
  loading,
  busy,
  error,
  loadOne,
  loadPipeline,
  loadVersions,
  save,
  confirm,
  parse,
  updateStatus,
} = useJob()

const record = ref<CareerRecord | null>(null)
const title = ref('')
const source = ref('')
const sections = ref<Record<string, CareerClaim[]>>({})
const note = ref('')
const message = ref('')
const jobPlans = ref<PlanSummary[]>([])
const jobSessions = ref<CareerInterviewSummary[]>([])

const jobId = computed(() => {
  const raw = route.params.jobId
  const value = Number(Array.isArray(raw) ? raw[0] : raw)
  return Number.isSafeInteger(value) && value > 0 ? value : null
})

const validationError = computed(() => {
  if (!title.value.trim()) return '请填写岗位名称'
  if (!source.value.trim()) return '请填写 JD 原文'
  for (const section of JOB_SECTIONS) {
    for (const claim of sections.value[section.key] ?? []) {
      if (!claim.value.trim()) return `${section.label}存在内容为空的条目`
      if (!claim.quote.trim()) return `${section.label}存在缺少 JD 原文证据的条目`
      if (!source.value.includes(claim.quote)) return `${section.label}的证据不在当前 JD 原文中`
    }
  }
  return ''
})
const canSave = computed(() => Boolean(record.value) && !validationError.value)

function adopt(value: CareerRecord | null): void {
  record.value = value
  if (!value) return
  title.value = value.title
  source.value = value.source
  sections.value = clonePlain(value.sections ?? {})
  message.value = ''
}

async function reload(): Promise<void> {
  if (!jobId.value) return
  const value = await loadOne(jobId.value)
  adopt(value)
  if (value) {
    await Promise.all([loadPipeline(value.id), loadVersions(value.id)])
    note.value = pipeline.value?.note ?? ''
    await loadInterviews(value.id)
  }
}

async function loadInterviews(id: number): Promise<void> {
  try {
    const [plans, sessions] = await Promise.all([listPlans(), listCareerInterviews()])
    jobPlans.value = plans.filter(plan => plan.jobId === id)
    jobSessions.value = sessions.filter(session =>
      jobPlans.value.some(plan => plan.id === session.planId),
    )
  } catch {
    jobPlans.value = []
    jobSessions.value = []
  }
}

function sessionsFor(planId: number): CareerInterviewSummary[] {
  return jobSessions.value.filter(session => session.planId === planId)
}

async function saveJob(): Promise<void> {
  if (!record.value || validationError.value) return
  const result = await save(record.value.id, {
    expectedVersion: record.value.currentVersion,
    title: title.value.trim(),
    source: source.value,
    sections: clonePlain(sections.value),
  })
  if (!result) return
  adopt(result)
  await loadVersions(result.id)
  message.value = result.confirmed ? '已保存并确认岗位版本' : '已保存新的待确认岗位版本'
  notify('岗位版本已保存', 'success')
}

async function confirmVersion(): Promise<void> {
  if (!record.value) return
  const result = await confirm(record.value.id, record.value.version)
  if (!result) return
  adopt(result)
  await loadVersions(result.id)
  notify('岗位版本已确认，可参与智能匹配', 'success')
}

async function parseWithConsent(): Promise<void> {
  if (!record.value) return
  const allowed = await confirmDialog(
    '允许当前已配置的模型处理这份私人 JD 吗？提取结果只会保存为新的待确认版本。',
    { title: 'AI 提取岗位要求', confirmText: '允许处理' },
  )
  if (!allowed) return
  const result = await parse(record.value.id, record.value.version)
  if (!result) return
  adopt(result)
  await loadVersions(result.id)
  message.value = 'AI 已提取岗位要求，请逐项核对原文证据后确认'
}

async function changeStatus(value: string | number | null): Promise<void> {
  if (!record.value) return
  const next = String(value ?? '') as JobStatus
  if (!JOB_STATUS_OPTIONS.some(option => option.value === next)) return
  const result = await updateStatus(record.value.id, next, note.value)
  if (!result) return
  pipeline.value = result
  notify('岗位状态已更新', 'success')
}

async function saveNote(): Promise<void> {
  if (!record.value || !pipeline.value) return
  if (pipeline.value.note === note.value) return
  const result = await updateStatus(record.value.id, pipeline.value.status, note.value)
  if (result) pipeline.value = result
}

async function selectVersion(version: number): Promise<void> {
  if (!record.value) return
  const value = await loadOne(record.value.id, version)
  if (value) adopt(value)
}

watch(jobId, () => void reload())
watch(current, value => {
  if (!value) return
  if (record.value && value.id === record.value.id && value.version === record.value.version) return
  if (!record.value || value.id === record.value.id) adopt(value)
})

onMounted(() => void reload())
</script>

<style scoped>
.job-detail-error { margin: 0; color: var(--kf-state-error); font-size: var(--kf-fs-body-sm); }
.job-detail-message { margin: 0; color: var(--kf-state-success); font-size: var(--kf-fs-body-sm); }
.job-detail-summary { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--kf-space-3); flex-wrap: wrap; padding: var(--kf-space-3); border: 1px solid var(--kf-border); border-radius: var(--kf-radius-md); background: var(--kf-surface-1); }
.job-detail-summary-main { display: flex; flex-direction: column; gap: var(--kf-space-2); flex: 1; min-width: 240px; max-width: 480px; }
.job-detail-meta { display: flex; align-items: center; gap: var(--kf-space-2); flex-wrap: wrap; font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
.job-detail-warning { color: var(--kf-state-warning); }
.job-detail-pipeline { display: flex; flex-direction: column; gap: var(--kf-space-2); width: 220px; }
.job-detail-body { display: grid; grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr); gap: var(--kf-space-4); align-items: start; }
.job-detail-source, .job-detail-requirements { display: flex; flex-direction: column; gap: var(--kf-space-2); padding: var(--kf-space-3); border: 1px solid var(--kf-border); border-radius: var(--kf-radius-md); background: var(--kf-surface-1); min-width: 0; }
.job-detail-panel-head { display: flex; align-items: center; justify-content: space-between; gap: var(--kf-space-2); }
.job-detail-panel-head h3 { margin: 0; }
.job-detail-history { display: grid; grid-template-columns: 280px minmax(0, 1fr); gap: var(--kf-space-4); padding-top: var(--kf-space-4); border-top: 1px solid var(--kf-border); }
.job-detail-interviews { display: flex; flex-direction: column; gap: var(--kf-space-2); padding-top: var(--kf-space-4); border-top: 1px solid var(--kf-border); }
.job-detail-interviews-head { display: flex; align-items: center; justify-content: space-between; gap: var(--kf-space-2); }
.job-detail-interviews-head h3 { margin: 0; }
.job-detail-interview { display: grid; grid-template-columns: minmax(0, 1fr) auto minmax(160px, auto); align-items: center; gap: var(--kf-space-3); padding: var(--kf-space-2) var(--kf-space-3); border-radius: var(--kf-radius-sm); background: var(--kf-surface-2); }
.job-detail-interview-title { margin: 0; font-size: var(--kf-fs-body-sm); color: var(--kf-foreground); }
.job-detail-interview a { display: inline-flex; align-items: center; gap: var(--kf-space-1); font-size: var(--kf-fs-caption); color: var(--kf-primary); text-decoration: none; }
.job-detail-interview a:hover { text-decoration: underline; }
.job-detail-interview-sessions { display: flex; flex-wrap: wrap; gap: var(--kf-space-2); justify-content: flex-end; }
@media (max-width: 1100px) {
  .job-detail-body { grid-template-columns: 1fr; }
  .job-detail-pipeline { width: 100%; }
  .job-detail-history { grid-template-columns: 1fr; }
}
@media (max-width: 760px) {
  .job-detail-interview { grid-template-columns: 1fr; }
  .job-detail-interview-sessions { justify-content: flex-start; }
}
</style>
