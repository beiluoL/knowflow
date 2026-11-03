<template>
  <section class="career-report" aria-label="岗位定制面试报告">
    <div class="career-report-head">
      <div>
        <h3 class="kf-text-card-title">岗位定制报告</h3>
        <p class="kf-text-caption">沿用 Interview V2 评分契约，叠加 JD 要求覆盖与岗位技能缺口；AI 评分不是已验证掌握度。</p>
      </div>
      <span class="career-report-state">{{ session.stateLabel }}</span>
    </div>

    <p v-if="loading" class="kf-text-caption">正在加载 Interview V2 报告…</p>
    <p v-else-if="error" class="career-report-error" role="alert">{{ error }}</p>
    <p v-else-if="!v2?.report" class="career-report-empty">
      本场面试尚未完成评价，暂时没有最终报告。完成后可在此查看 JD 覆盖、岗位技能得分与下一步训练建议。
    </p>
    <template v-else>
      <div class="career-report-scores">
        <div class="career-report-score">
          <p class="career-report-score-value">{{ v2.report.overallScore == null ? '证据不足' : v2.report.overallScore.toFixed(1) }}</p>
          <p class="career-report-score-label">V2 综合评分</p>
        </div>
        <div class="career-report-score">
          <p class="career-report-score-value">{{ coverageText }}</p>
          <p class="career-report-score-label">JD 必备技能覆盖</p>
        </div>
        <div class="career-report-score">
          <p class="career-report-score-value">{{ followupScore }}</p>
          <p class="career-report-score-label">动态追问表现</p>
        </div>
        <div class="career-report-score">
          <p class="career-report-score-value">{{ v2.report.evaluatedTurns }} / {{ v2.report.answeredTurns }}</p>
          <p class="career-report-score-label">已评价回答</p>
        </div>
      </div>

      <div class="career-report-grid">
        <div class="career-report-block">
          <p class="career-report-block-title">岗位维度得分</p>
          <ul class="career-report-list">
            <li v-for="(value, key) in v2.report.dimensionScores" :key="key">
              <span>{{ DIMENSION_NAME[key] || key }}</span>
              <strong>{{ value == null ? '证据不足' : `${value} / 100` }}</strong>
            </li>
          </ul>
        </div>
        <div class="career-report-block">
          <p class="career-report-block-title">JD 要求覆盖情况</p>
          <ul class="career-report-list">
            <li v-for="dimension in match.required" :key="dimension.requirement">
              <span>{{ dimension.requirement }}</span>
              <strong :class="dimension.matchStatus === 'MATCHED' ? 'is-ok' : 'is-gap'">
                {{ MATCH_STATUS_LABELS[dimension.matchStatus] }}
              </strong>
            </li>
          </ul>
        </div>
        <div class="career-report-block">
          <p class="career-report-block-title">简历项目表现</p>
          <ul v-if="projectRows.length" class="career-report-list">
            <li v-for="row in projectRows" :key="row.questionId">
              <span class="career-report-project">{{ row.title }}</span>
              <strong>{{ row.score == null ? '未评价' : `${row.score} / 100` }}</strong>
            </li>
          </ul>
          <p v-else class="kf-text-caption">本方案没有简历项目题。</p>
        </div>
      </div>

      <div class="career-report-grid">
        <div class="career-report-block">
          <p class="career-report-block-title">关键失分点</p>
          <ul v-if="lostPoints.length" class="career-report-advice">
            <li v-for="item in lostPoints" :key="item">{{ item }}</li>
          </ul>
          <p v-else class="kf-text-caption">报告未给出明确失分点，或证据不足。</p>
        </div>
        <div class="career-report-block">
          <p class="career-report-block-title">主要能力差距</p>
          <ul v-if="gapItems.length" class="career-report-advice">
            <li v-for="item in gapItems" :key="item">{{ item }}</li>
          </ul>
          <p v-else class="kf-text-caption">暂无额外缺口说明。</p>
        </div>
        <div class="career-report-block">
          <p class="career-report-block-title">下一步训练建议</p>
          <ul v-if="nextSteps.length" class="career-report-advice">
            <li v-for="item in nextSteps" :key="item">{{ item }}</li>
          </ul>
          <p v-else class="kf-text-caption">报告未给出训练建议。</p>
        </div>
      </div>

      <p class="career-report-foot">
        {{ v2.report.assessmentType === 'AI_SUGGESTION_NOT_VERIFIED_MASTERY' ? 'AI 建议，不修改真实掌握度。' : '评分来自 Interview V2 报告。' }}
        评分标准 {{ v2.report.rubricVersion }}，证据范围 {{ v2.report.evaluatedTurns }} / {{ v2.report.answeredTurns }} 个回答。
      </p>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import type { CareerInterviewSummary, CareerMatch, CareerPlan } from '@/api/career/types'
import { MATCH_STATUS_LABELS } from './career-labels'
import { getV2, type V2Session } from '@/api/interviewV2'
import { getApiError } from '@/utils/toast'

const DIMENSION_NAME: Record<string, string> = {
  TECHNICAL_CORRECTNESS: '技术准确性',
  KNOWLEDGE_COVERAGE: '知识覆盖',
  ENGINEERING_PRACTICE: '工程实践',
  EXPRESSION_STRUCTURE: '表达结构',
  FOLLOWUP_RESPONSE: '追问响应',
}

const props = defineProps<{
  session: CareerInterviewSummary
  plan: CareerPlan
  match: CareerMatch
}>()

const v2 = ref<V2Session | null>(null)
const loading = ref(false)
const error = ref('')
let seq = 0

const coverageText = computed(() => {
  const total = props.match.required.length
  if (!total) return '—'
  const matched = props.match.required.filter(dimension => dimension.matchStatus === 'MATCHED').length
  return `${matched} / ${total}`
})
const followupScore = computed(() => {
  const value = v2.value?.report?.dimensionScores?.FOLLOWUP_RESPONSE
  return value == null ? '证据不足' : `${value} / 100`
})
const projectRows = computed(() => {
  const turns = v2.value?.turns ?? []
  return props.plan.questions
    .filter(question => question.source === 'RESUME_PROJECT')
    .map(question => {
      const turn = turns.find(item => item.questionId === question.questionId)
      const dimensions = turn?.evaluation?.dimensions
        ? Object.values(turn.evaluation.dimensions).filter((value): value is number => typeof value === 'number' && Number.isFinite(value))
        : []
      const score = turn?.evaluation?.totalScore
        ?? (dimensions.length ? Math.round(dimensions.reduce((sum, value) => sum + value, 0) / dimensions.length * 10) / 10 : null)
      return { questionId: question.questionId, title: question.title, score }
    })
})
const lostPoints = computed(() => {
  const report = v2.value?.report
  if (!report) return []
  return Array.from(new Set([...(report.weaknesses ?? []), ...(report.missedKnowledgePoints ?? [])]))
})
const gapItems = computed(() => {
  const report = v2.value?.report
  const gaps = props.match.required
    .filter(dimension => ['MISSING', 'PARTIAL', 'UNCERTAIN'].includes(dimension.matchStatus))
    .map(dimension => `${dimension.requirement}：${MATCH_STATUS_LABELS[dimension.matchStatus]}`)
  return Array.from(new Set([...gaps, ...(report?.weaknesses ?? [])]))
})
const nextSteps = computed(() => {
  const report = v2.value?.report
  if (!report) return []
  const values = [...(report.improvements ?? [])]
  for (const id of report.recommendedPracticeQuestionIds ?? []) values.push(`推荐补练题目 ID：${id}`)
  return Array.from(new Set(values.filter(Boolean)))
})

async function load(): Promise<void> {
  const sequence = ++seq
  loading.value = true
  error.value = ''
  try {
    const value = await getV2(props.session.sessionId)
    if (sequence === seq) v2.value = value
  } catch (e: unknown) {
    if (sequence === seq) error.value = getApiError(e, '面试报告加载失败')
  } finally {
    if (sequence === seq) loading.value = false
  }
}

watch(() => props.session.sessionId, () => void load())
onMounted(() => void load())
</script>

<style scoped>
.career-report { display: flex; flex-direction: column; gap: var(--kf-space-3); padding: var(--kf-space-4); border: 1px solid var(--kf-border); border-radius: var(--kf-radius-md); background: var(--kf-surface-1); }
.career-report-head { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--kf-space-2); flex-wrap: wrap; }
.career-report-head h3 { margin: 0 0 var(--kf-space-1); }
.career-report-head p { margin: 0; }
.career-report-state { padding: 1px var(--kf-space-2); border-radius: var(--kf-radius-xs); background: var(--kf-surface-2); font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
.career-report-error { margin: 0; color: var(--kf-state-error); font-size: var(--kf-fs-body-sm); }
.career-report-empty { margin: 0; padding: var(--kf-space-4); border-radius: var(--kf-radius-sm); background: var(--kf-surface-2); font-size: var(--kf-fs-body-sm); color: var(--kf-muted-foreground); }
.career-report-scores { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: var(--kf-space-2); }
.career-report-score { display: flex; flex-direction: column; gap: 2px; padding: var(--kf-space-2) var(--kf-space-3); border-radius: var(--kf-radius-sm); background: var(--kf-surface-2); }
.career-report-score-value { margin: 0; font-size: var(--kf-fs-h4); font-weight: 600; color: var(--kf-foreground); }
.career-report-score-label { margin: 0; font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
.career-report-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: var(--kf-space-2); }
.career-report-block { display: flex; flex-direction: column; gap: var(--kf-space-1); padding: var(--kf-space-3); border-radius: var(--kf-radius-sm); background: var(--kf-surface-2); min-width: 0; }
.career-report-block-title { margin: 0; font-size: var(--kf-fs-body-sm); color: var(--kf-foreground); }
.career-report-list { display: flex; flex-direction: column; gap: var(--kf-space-1); margin: 0; padding: 0; list-style: none; font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
.career-report-list li { display: flex; align-items: baseline; justify-content: space-between; gap: var(--kf-space-2); }
.career-report-list strong { color: var(--kf-foreground); font-weight: 500; white-space: nowrap; }
.career-report-list strong.is-ok { color: var(--kf-state-success); }
.career-report-list strong.is-gap { color: var(--kf-state-warning); }
.career-report-project { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.career-report-advice { margin: 0; padding-left: var(--kf-space-4); font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
.career-report-foot { margin: 0; font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
@media (max-width: 900px) {
  .career-report-scores { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .career-report-grid { grid-template-columns: 1fr; }
}
</style>
