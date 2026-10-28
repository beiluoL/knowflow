<template>
  <section class="exam-diagnosis" aria-label="考试观察与专项复测">
    <header>
      <h2 class="kf-h2">考试观察与专项复测</h2>
      <p class="diagnosis-note">以下仅是本次考试观察，不等于全局掌握度。样本不足时不下能力结论，未答与未评分别保留。</p>
    </header>
    <StateView :status="state" error-title="考试观察加载失败" :error-description="error" error-hint="请检查网络后重试；原有成绩与解析仍可查看。" empty-title="没有可展示的考试观察" empty-description="本次报告没有可归类的真实题目证据，不推测学习状态。" @retry="load">
      <template v-if="diagnosis">
        <div class="diagnosis-summary">
          <span>客观题已答且已评 {{ diagnosis.objectiveAnsweredCount }}</span><span>客观题答对 {{ diagnosis.objectiveCorrectCount }}</span>
          <span>{{ diagnosis.objectiveAccuracy == null ? '正确率暂不可计算' : `客观题正确率 ${accuracyText(diagnosis.objectiveAccuracy)}` }}</span>
          <span>未答 {{ diagnosis.unansweredCount }}</span><span>未评 {{ diagnosis.ungradedCount }}</span>
        </div>
        <p class="diagnosis-note">客观题正确率仅统计已作答且已评分的客观题；无此类样本时不显示 0%。</p>
        <section v-for="topic in diagnosis.topics" :key="topic.topicCode" class="diagnosis-topic">
          <header class="diagnosis-topic-header"><h3 class="kf-h3">{{ TOPIC_LABELS[topic.topicCode] || topic.topicCode }}</h3><span class="diagnosis-evidence-level">{{ topic.evidenceLevel === 'INSUFFICIENT' ? '样本不足' : '已有考试观察' }}</span></header>
          <p class="diagnosis-note">已答 {{ topic.answeredCount }} · 已评 {{ topic.gradedCount }} · 答错 {{ topic.wrongCount }} · 未答 {{ topic.unansweredCount }} · 未评 {{ topic.ungradedCount }}</p>
          <ul v-if="topic.observations.length" class="diagnosis-observations"><li v-for="(observation, index) in topic.observations" :key="index">{{ observation }}</li></ul>
          <h4 class="kf-h4">考试证据（含历史）</h4>
          <ul v-if="topic.evidence.length" class="diagnosis-evidence">
            <li v-for="item in topic.evidence" :key="`${item.attemptId}:${item.paperQuestionId}`"><span>{{ item.title }}</span><small>考试 #{{ item.attemptId }} · 试卷题 #{{ item.paperQuestionId }} · {{ difficultyText(item.difficulty) }} · {{ evidenceStatus(item) }}</small></li>
          </ul>
          <p v-else class="diagnosis-note">没有可展示的题目证据，不推测结论。</p>
          <div class="diagnosis-materials"><h4 class="kf-h4">关联学习资料</h4>
            <div v-if="topic.materials.length" class="diagnosis-material-links"><Button v-for="material in topic.materials" :key="material.knowledgeId" variant="ghost" :disabled="!validId(material.knowledgeId)" @click="openMaterial(material)">{{ material.name }}</Button></div>
            <p v-else class="diagnosis-note">暂无关联学习资料：该主题尚未映射真实知识点，不能制造学习深链。</p>
          </div>
          <p class="diagnosis-note">建议复测时间：{{ topic.retestAfter ? formatTime(topic.retestAfter) : '证据不足，暂未提供时间建议' }}</p>
          <details v-if="topic.practiceCandidates.length" class="diagnosis-candidates"><summary>查看 {{ topic.practiceCandidates.length }} 道真实复测候选题</summary><ul><li v-for="candidate in topic.practiceCandidates" :key="candidate.catalogId">{{ candidate.title }} · {{ QUESTION_TYPE_LABELS[candidate.questionType as QuestionType] || candidate.questionType }} · {{ difficultyText(candidate.difficulty) }}</li></ul></details>
          <p v-else class="diagnosis-note">没有可用复测候选题；先查看已有解析或关联资料。</p>
          <Button :disabled="practiceCount(topic) === 0 || !!busyTopic" :loading="busyTopic === topic.topicCode" @click="startPractice(topic)">专项复测<template v-if="practiceCount(topic)">（{{ practiceCount(topic) }} 题）</template></Button>
          <p v-if="practiceCount(topic) === 0 && topic.practiceCandidates.length" class="diagnosis-note">暂无可用于现有考试流程的复测候选题或题量建议。</p>
          <p v-if="practiceErrorTopic === topic.topicCode && practiceError" class="diagnosis-error" role="alert">{{ practiceError }} <button type="button" :disabled="!!busyTopic" @click="startPractice(topic)">重试专项复测</button></p>
        </section>
        <section class="diagnosis-comparison" aria-label="报告对比">
          <h3 class="kf-h3">报告对比</h3>
          <p>{{ diagnosis.comparison.note }}</p>
          <p v-if="diagnosis.comparison.comparable" class="diagnosis-note">本次客观题正确率 {{ accuracyText(diagnosis.comparison.currentObjectiveAccuracy) }} · 上次 {{ accuracyText(diagnosis.comparison.previousObjectiveAccuracy) }}</p>
          <p v-else class="diagnosis-note">{{ diagnosis.comparison.previousAttemptId ? '两次报告暂不可直接比较，不推断能力变化。' : '没有可用于比较的上次考试报告。' }}</p>
          <Button v-if="validId(diagnosis.comparison.previousAttemptId)" variant="secondary" @click="openPreviousReport">查看上次报告</Button>
        </section>
        <p class="diagnosis-rule">规则版本：{{ diagnosis.ruleVersion }} · 观察范围：本次考试</p>
      </template>
    </StateView>
  </section>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import StateView from '@/components/ui/StateView.vue'
import Button from '@/components/ui/Button.vue'
import { examDiagnosisApi, type ExamDiagnosis, type ExamDiagnosisEvidence, type ExamDiagnosisTopic, type ExamPracticeCandidate } from '@/api/examDiagnosis'
import { examApi, QUESTION_TYPE_LABELS, TOPIC_LABELS, type QuestionType } from '@/api/exam'
import { stampActionContext, resolveContextualRoute } from '@/learning/action-contract'
import { getApiError } from '@/utils/toast'

const props = defineProps<{ attemptId: number; refreshKey?: number }>()
const router = useRouter()
const diagnosis = ref<ExamDiagnosis | null>(null), loading = ref(true), error = ref('')
const busyTopic = ref(''), practiceError = ref(''), practiceErrorTopic = ref('')
const createdPaper = ref<{ attemptId: number; topicCode: string; paperId: number } | null>(null)
const state = computed(() => loading.value ? 'loading' : error.value ? 'error' : diagnosis.value?.topics.length ? 'ready' : 'empty')
let loadRequest = 0, practiceRequest = 0
const validId = (value: number | null | undefined): value is number => typeof value === 'number' && Number.isSafeInteger(value) && value > 0
const accuracyText = (value: number | null) => value == null ? '暂不可计算' : `${Math.round(value * 10) / 10}%`
const difficultyText = (value: number | null) => value == null ? '难度未提供' : ({ 1: '简单', 2: '中等', 3: '困难' }[value] || `来源难度 ${value}`)
const formatTime = (value: string) => value.replace('T', ' ').slice(0, 16)
const evidenceStatus = (item: ExamDiagnosisEvidence) => !item.answered ? '未答' : !item.graded ? '已答，未评' : item.correct === true ? '已评，答对' : item.correct === false ? '已评，答错' : '已评，未提供正确性判断'
async function load() {
  const requestId = ++loadRequest; loading.value = true; error.value = ''; diagnosis.value = null
  if (!validId(props.attemptId)) { error.value = '考试报告 ID 无效'; loading.value = false; return }
  try { const response = await examDiagnosisApi.get(props.attemptId); if (requestId === loadRequest) diagnosis.value = response }
  catch (e: unknown) { if (requestId === loadRequest) error.value = getApiError(e, '考试观察加载失败') }
  finally { if (requestId === loadRequest) loading.value = false }
}
function eligibleCandidates(topic: ExamDiagnosisTopic): ExamPracticeCandidate[] {
  const seen = new Set<number>()
  return topic.practiceCandidates.filter(candidate => {
    if (!validId(candidate.questionId) || !Object.hasOwn(QUESTION_TYPE_LABELS, candidate.questionType) || seen.has(candidate.questionId)) return false
    seen.add(candidate.questionId); return true
  })
}
function practiceCount(topic: ExamDiagnosisTopic): number {
  return Number.isSafeInteger(topic.recommendedCount) && topic.recommendedCount > 0 ? Math.min(3, topic.recommendedCount, eligibleCandidates(topic).length) : 0
}
async function startPractice(topic: ExamDiagnosisTopic) {
  if (busyTopic.value || !practiceCount(topic)) return
  const attemptId = props.attemptId, topicCode = topic.topicCode, count = practiceCount(topic), requestId = ++practiceRequest
  busyTopic.value = topicCode; practiceError.value = ''; practiceErrorTopic.value = topicCode
  try {
    let paperId = createdPaper.value?.attemptId === attemptId && createdPaper.value.topicCode === topicCode ? createdPaper.value.paperId : null
    if (paperId == null) {
      const distribution: Record<string, number> = {}
      for (const candidate of eligibleCandidates(topic).slice(0, count)) distribution[candidate.questionType] = (distribution[candidate.questionType] || 0) + 1
      const paper = await examApi.compose({ name: `${TOPIC_LABELS[topicCode] || topicCode} 专项复测`, topics: [topicCode], strategy: 'RANDOM', seed: 42, mode: 'PRACTICE', includeAnswered: true, questionCount: count, typeDistribution: distribution, totalScore: count * 10, passScore: count * 6, durationMinutes: count * 5 })
      if (requestId !== practiceRequest) return
      if (!validId(paper.id)) throw new Error('组卷未返回有效试卷，请重试')
      paperId = paper.id; createdPaper.value = { attemptId, topicCode, paperId }
    }
    const attempt = await examApi.startAttempt(paperId)
    if (requestId !== practiceRequest) return
    if (!validId(attempt.attemptId)) throw new Error('未返回有效作答记录，请重试')
    await router.push(`/exam/take/${attempt.attemptId}`)
  } catch (e: unknown) { if (requestId === practiceRequest) practiceError.value = getApiError(e, '专项复测创建失败') }
  finally { if (requestId === practiceRequest) busyTopic.value = '' }
}
function openMaterial(material: { knowledgeId: number; name: string }) {
  if (!validId(material.knowledgeId)) return
  const context = { knowledgeId: material.knowledgeId, knowledgeName: material.name }
  const action = stampActionContext({ type: 'KNOWLEDGE', title: material.name, reason: '用户选择考试观察关联的真实学习资料', route: '/learning/mastery' }, context)
  void router.push(resolveContextualRoute(action, context))
}
function openPreviousReport() { const id = diagnosis.value?.comparison.previousAttemptId; if (validId(id)) void router.push(`/exam/result/${id}`) }
watch(() => [props.attemptId, props.refreshKey] as const, () => { practiceRequest++; busyTopic.value = ''; createdPaper.value = null; practiceError.value = ''; practiceErrorTopic.value = ''; void load() }, { immediate: true })
onBeforeUnmount(() => { loadRequest++; practiceRequest++ })
</script>
<style scoped>
.exam-diagnosis { background: var(--kf-card); border: 1px solid var(--kf-border); border-radius: var(--kf-radius-md); padding: var(--kf-space-5); display: flex; flex-direction: column; gap: var(--kf-space-4); }
.diagnosis-note, .diagnosis-rule, .diagnosis-evidence small { color: var(--kf-muted-foreground); font-size: var(--kf-fs-body-sm); }
.diagnosis-summary, .diagnosis-topic-header, .diagnosis-material-links { display: flex; flex-wrap: wrap; gap: var(--kf-space-3); }
.diagnosis-summary { background: var(--kf-muted); padding: var(--kf-space-3); border-radius: var(--kf-radius-sm); }
.diagnosis-topic { padding-block: var(--kf-space-4); border-bottom: 1px solid var(--kf-border); display: flex; flex-direction: column; align-items: flex-start; gap: var(--kf-space-3); }
.diagnosis-topic-header { justify-content: space-between; width: 100%; align-items: center; }
.diagnosis-evidence-level { color: var(--kf-warning); font-size: var(--kf-fs-body-sm); }
.diagnosis-observations, .diagnosis-candidates ul { list-style: disc; padding-left: var(--kf-space-5); }
.diagnosis-evidence { width: 100%; display: flex; flex-direction: column; gap: var(--kf-space-2); }
.diagnosis-evidence small { display: block; }
.diagnosis-comparison { display: flex; flex-direction: column; align-items: flex-start; gap: var(--kf-space-3); }
.diagnosis-error, .diagnosis-error button { color: var(--kf-destructive); }
.diagnosis-error button { text-decoration: underline; }
</style>
