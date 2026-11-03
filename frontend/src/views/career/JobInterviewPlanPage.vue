<template>
  <CareerWorkbenchLayout subtitle="题目审核保存为新方案快照；正式面试由 Interview V2 加载当前用户与已确认方案。">
    <template #actions>
      <Button variant="secondary" size="sm" @click="router.push('/career/interviews')">
        <Icon name="arrow-left" :size="14" /><span>返回面试列表</span>
      </Button>
      <Button variant="secondary" size="sm" :disabled="busy || !plan || !dirty" :loading="busy" @click="saveQuestions">
        <Icon v-if="!busy" name="save" :size="14" /><span>保存审核结果</span>
      </Button>
      <Button size="sm" :disabled="busy || !plan || !questions.length || dirty || !reviewed" :loading="busy" @click="startInterview">
        <Icon v-if="!busy" name="play" :size="14" /><span>开始岗位面试</span>
      </Button>
    </template>

    <StateView v-if="loading && !plan" status="loading" type="card" :cols="2" :rows="3" />
    <StateView
      v-else-if="error && !plan"
      status="error"
      :error-description="error"
      retry-text="重新加载面试方案"
      @retry="reload"
    />
    <template v-else-if="plan">
      <p v-if="error" class="interview-plan-page-error" role="alert">{{ error }}</p>
      <p v-if="message" class="interview-plan-page-message" role="status">{{ message }}</p>
      <p v-if="dirty" class="interview-plan-page-warning" role="status">
        题目有未保存修改。请先保存审核结果，再开始正式面试。
      </p>

      <section class="interview-plan-summary">
        <div class="interview-plan-summary-main">
          <p class="interview-plan-summary-eyebrow">岗位定制面试方案 #{{ plan.id }}</p>
          <h2 class="kf-text-section-title">{{ plan.job.title }}</h2>
          <p class="kf-text-caption">
            {{ plan.resume.title }} v{{ plan.resume.version }} → {{ plan.job.title }} v{{ plan.job.version }}
            · {{ plan.durationMinutes }} 分钟 · {{ plan.difficulty }}
            <template v-if="plan.interviewType"> · {{ plan.interviewType }}</template>
          </p>
          <div class="interview-plan-summary-links">
            <router-link :to="`/career/matches/${plan.matchId}`">
              <Icon name="target" :size="14" /><span>查看匹配证据</span>
            </router-link>
            <router-link :to="`/career/resumes/${plan.resume.id}?version=${plan.resume.version}`">
              <Icon name="file-text" :size="14" /><span>简历 v{{ plan.resume.version }}</span>
            </router-link>
            <router-link :to="`/career/jobs/${plan.job.id}`">
              <Icon name="briefcase" :size="14" /><span>岗位 v{{ plan.job.version }}</span>
            </router-link>
          </div>
        </div>
        <div v-if="match" class="interview-plan-summary-coverage">
          <p class="interview-plan-summary-coverage-value">{{ match.score == null ? '—' : `${match.score.toFixed(1)}%` }}</p>
          <p class="interview-plan-summary-coverage-label">必备技能覆盖率</p>
        </div>
      </section>

      <InterviewQuestionPreview
        :questions="questions"
        :busy="busy"
        @add="addQuestion"
        @regenerate="regenerate"
      />

      <section class="interview-plan-review" aria-label="题目确认">
        <Checkbox v-model="reviewed" :disabled="dirty || busy" label="我已逐题核对题目、证据与评分维度，确认可以进入正式面试" />
        <p class="kf-text-caption">
          生成方案默认需要人工确认；保存审核结果后的方案会记录为已审核。
        </p>
      </section>

      <section v-if="sessions.length" class="interview-plan-sessions" aria-label="本方案面试记录">
        <div class="interview-plan-sessions-head">
          <h3 class="kf-text-card-title">本方案面试记录</h3>
          <span class="kf-text-caption">{{ sessions.length }} 场</span>
        </div>
        <article v-for="session in sessions" :key="session.sessionId" class="interview-plan-session">
          <div>
            <p class="interview-plan-session-title">{{ session.jobTitle }}</p>
            <p class="kf-text-caption">{{ formatDateTime(session.createdAt) }}</p>
          </div>
          <span class="interview-plan-session-state" :class="{ 'is-completed': session.state === 'COMPLETED' }">
            {{ session.stateLabel }}
          </span>
          <div class="interview-plan-session-actions">
            <Button variant="secondary" size="sm" @click="router.push(`/interview?v2Id=${session.sessionId}`)">
              <Icon name="external-link" :size="14" /><span>{{ session.state === 'COMPLETED' ? 'V2 报告' : '继续面试' }}</span>
            </Button>
            <Button
              v-if="session.state === 'COMPLETED'"
              variant="text"
              size="sm"
              @click="selectedSessionId = session.sessionId"
            >岗位定制报告</Button>
          </div>
        </article>
      </section>

      <CareerInterviewReport
        v-if="selectedSession && match"
        :session="selectedSession"
        :plan="plan"
        :match="match"
      />
    </template>
  </CareerWorkbenchLayout>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Button from '@/components/ui/Button.vue'
import Checkbox from '@/components/ui/Checkbox.vue'
import Icon from '@/components/ui/Icon.vue'
import StateView from '@/components/ui/StateView.vue'
import CareerWorkbenchLayout from '@/components/career/CareerWorkbenchLayout.vue'
import InterviewQuestionPreview from '@/components/career/InterviewQuestionPreview.vue'
import CareerInterviewReport from '@/components/career/CareerInterviewReport.vue'
import { clonePlain, formatDateTime } from '@/components/career/career-labels'
import { useCareerInterview } from '@/composables/career/useCareerInterview'
import { getMatch } from '@/api/career/match'
import { listCareerInterviews } from '@/api/career/interview'
import type { CareerInterviewSummary, CareerMatch, CareerPlanQuestion } from '@/api/career/types'
import { confirmDialog, notify } from '@/utils/toast'

const route = useRoute()
const router = useRouter()
const { current, loading, busy, error, loadPlan, revise, regenerate: regenerateApi, start } = useCareerInterview()
const plan = current
const questions = ref<CareerPlanQuestion[]>([])
const match = ref<CareerMatch | null>(null)
const sessions = ref<CareerInterviewSummary[]>([])
const selectedSessionId = ref<number | null>(null)
const reviewed = ref(false)
const message = ref('')

const planId = computed(() => {
  const raw = route.params.planId
  const value = Number(Array.isArray(raw) ? raw[0] : raw)
  return Number.isSafeInteger(value) && value > 0 ? value : null
})
const dirty = computed(() => {
  if (!plan.value) return false
  return JSON.stringify(plan.value.questions ?? []) !== JSON.stringify(questions.value)
})
const selectedSession = computed(() =>
  sessions.value.find(session => session.sessionId === selectedSessionId.value) ?? null,
)

async function reload(): Promise<void> {
  if (!planId.value) return
  selectedSessionId.value = null
  const value = await loadPlan(planId.value)
  if (!value) return
  questions.value = clonePlain(value.questions ?? [])
  reviewed.value = value.generationSource === 'USER_REVIEWED'
  message.value = ''
  try {
    const [matchValue, sessionList] = await Promise.all([
      getMatch(value.matchId),
      listCareerInterviews(),
    ])
    match.value = matchValue
    sessions.value = sessionList.filter(session => session.planId === value.id)
  } catch {
    match.value = null
    sessions.value = []
  }
}

async function saveQuestions(): Promise<void> {
  if (!plan.value) return
  const value = await revise(plan.value.id, questions.value)
  if (!value) return
  message.value = '题目审核结果已保存为新的方案快照'
  notify('面试方案已更新', 'success')
  await router.push(`/career/interviews/${value.id}`)
}

function addQuestion(): void {
  if (questions.value.length >= 8 || !plan.value) return
  questions.value.push({
    questionId: `USER-${crypto.randomUUID()}`,
    title: `请围绕岗位「${plan.value.job.title}」说明一个真实项目或系统设计，并解释关键取舍。`,
    questionPurpose: 'USER_EDITED',
    evidenceReference: plan.value.job.title,
    expectedKnowledgePoints: ['技术原理', '工程边界', '可验证依据'],
    targetSkill: '系统设计',
    source: 'USER_EDITED',
    scoringDimension: 'ENGINEERING_PRACTICE',
    followupDirections: ['适用条件', '失败边界'],
    skillIds: [],
  })
}

async function regenerate(questionId: string): Promise<void> {
  if (!plan.value) return
  const result = await regenerateApi(plan.value.id, questionId)
  if (!result) return
  const index = questions.value.findIndex(question => question.questionId === questionId)
  if (index >= 0) questions.value.splice(index, 1, result.question)
  notify('已生成替代题目，保存后生效', 'success')
}

async function startInterview(): Promise<void> {
  if (!plan.value || dirty.value || !reviewed.value) return
  const allowed = await confirmDialog(
    '已核对题目与证据来源，开始 Interview V2 正式面试？开始后服务端会加载当前用户与冻结方案。',
    { title: '开始岗位定制面试', confirmText: '开始面试' },
  )
  if (!allowed) return
  const result = await start(plan.value.id, crypto.randomUUID())
  if (!result) return
  await router.push(`/interview?v2Id=${result.sessionId}`)
}

watch(planId, () => void reload())
onMounted(() => void reload())
</script>

<style scoped>
.interview-plan-page-error { margin: 0; color: var(--kf-state-error); font-size: var(--kf-fs-body-sm); }
.interview-plan-page-message { margin: 0; color: var(--kf-state-success); font-size: var(--kf-fs-body-sm); }
.interview-plan-page-warning { margin: 0; color: var(--kf-state-warning); font-size: var(--kf-fs-body-sm); }
.interview-plan-summary { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--kf-space-3); flex-wrap: wrap; padding: var(--kf-space-4); border: 1px solid var(--kf-border); border-radius: var(--kf-radius-md); background: var(--kf-surface-1); }
.interview-plan-summary-main { display: flex; flex-direction: column; gap: var(--kf-space-1); min-width: 0; }
.interview-plan-summary-eyebrow { margin: 0; font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
.interview-plan-summary-main h2 { margin: 0; }
.interview-plan-summary-main p { margin: 0; }
.interview-plan-summary-links { display: flex; flex-wrap: wrap; gap: var(--kf-space-3); margin-top: var(--kf-space-1); }
.interview-plan-summary-links a { display: inline-flex; align-items: center; gap: var(--kf-space-1); font-size: var(--kf-fs-body-sm); color: var(--kf-primary); text-decoration: none; }
.interview-plan-summary-links a:hover { text-decoration: underline; }
.interview-plan-summary-coverage { display: flex; flex-direction: column; align-items: flex-end; }
.interview-plan-summary-coverage-value { margin: 0; font-size: var(--kf-fs-h3); font-weight: 600; color: var(--kf-foreground); }
.interview-plan-summary-coverage-label { margin: 0; font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
.interview-plan-review { display: flex; flex-direction: column; gap: var(--kf-space-1); padding: var(--kf-space-3); border: 1px solid var(--kf-border); border-radius: var(--kf-radius-md); background: var(--kf-surface-1); }
.interview-plan-review p { margin: 0; }
.interview-plan-sessions { display: flex; flex-direction: column; gap: var(--kf-space-2); padding: var(--kf-space-4); border: 1px solid var(--kf-border); border-radius: var(--kf-radius-md); background: var(--kf-surface-1); }
.interview-plan-sessions-head { display: flex; align-items: center; justify-content: space-between; gap: var(--kf-space-2); }
.interview-plan-sessions-head h3 { margin: 0; }
.interview-plan-session { display: grid; grid-template-columns: minmax(0, 1fr) auto auto; align-items: center; gap: var(--kf-space-3); padding: var(--kf-space-2) 0; border-top: 1px solid var(--kf-border); }
.interview-plan-session-title { margin: 0; font-size: var(--kf-fs-body-sm); color: var(--kf-foreground); }
.interview-plan-session-state { padding: 1px var(--kf-space-2); border-radius: var(--kf-radius-xs); background: var(--kf-surface-2); font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
.interview-plan-session-state.is-completed { background: color-mix(in srgb, var(--kf-state-success) 10%, transparent); color: var(--kf-state-success); }
.interview-plan-session-actions { display: flex; align-items: center; gap: var(--kf-space-2); justify-content: flex-end; flex-wrap: wrap; }
@media (max-width: 640px) {
  .interview-plan-session { grid-template-columns: 1fr; }
}
</style>
