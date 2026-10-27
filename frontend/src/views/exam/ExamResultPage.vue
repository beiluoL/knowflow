<template>
  <MiniAppShell
    title="考试成绩"
    subtitle="规则评分可追溯，主观题为 AI 辅助评分（非人工审核成绩）"
    back-label="返回考试中心"
    @back="goCenter"
  >
    <div class="space-y-6 animate-fade-in">
      <div v-if="loading" class="border rounded-[10px] p-10 bg-card border-border text-center">
        <Icon name="loader" :size="28" class="animate-spin text-primary-500 mx-auto mb-3" />
        <p class="text-sm text-muted-foreground">正在加载成绩…</p>
      </div>

      <div v-else-if="error" class="border border-red-200 bg-red-50 rounded-xl p-8 text-center">
        <Icon name="alert-circle" :size="28" class="text-red-400 mx-auto mb-3" />
        <p class="text-sm text-red-600 font-medium mb-1">成绩加载失败</p>
        <p class="text-xs text-muted-foreground mb-4">{{ error }}</p>
        <button class="px-4 py-2 rounded-lg text-sm text-white bg-primary-500 hover:bg-primary-600" @click="load">重试</button>
      </div>

      <template v-else-if="result">
        <!-- 状态横幅 -->
        <div
          class="rounded-xl px-4 py-3 flex items-start gap-2.5 text-sm"
          :class="banner.class"
        >
          <Icon :name="banner.icon" :size="18" class="mt-0.5 shrink-0" />
          <div class="flex-1">
            <p class="font-medium">{{ banner.title }}</p>
            <p class="text-xs mt-0.5 opacity-90 leading-relaxed">{{ banner.msg }}</p>
          </div>
          <button
            v-if="canAiGrade"
            :disabled="grading"
            class="shrink-0 inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium bg-white/80 border border-current/30 hover:bg-white transition-colors disabled:opacity-60"
            @click="runAi"
          >
            <Icon :name="grading ? 'loader' : 'sparkles'" :size="14" :class="grading ? 'animate-spin' : ''" />
            {{ grading ? 'AI 评卷中…' : result.aiAvailable ? '发起 AI 评卷' : '重试 AI 评卷' }}
          </button>
          <button
            v-if="canRejudgeCode"
            :disabled="rejudging"
            class="shrink-0 inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium bg-white/80 border border-current/30 hover:bg-white transition-colors disabled:opacity-60"
            @click="runRejudgeCode"
          >
            <Icon :name="rejudging ? 'loader' : 'cpu'" :size="14" :class="rejudging ? 'animate-spin' : ''" />
            {{ rejudging ? '判题重试中…' : '重试代码判题' }}
          </button>
        </div>

        <!-- 总分卡 -->
        <section class="grid grid-cols-1 md:grid-cols-4 gap-4">
          <div class="md:col-span-2 border rounded-[10px] bg-card border-border p-6 flex items-center gap-6">
            <div class="relative w-[104px] h-[104px] shrink-0">
              <svg viewBox="0 0 104 104" class="w-full h-full -rotate-90">
                <circle cx="52" cy="52" r="44" fill="none" stroke="#F1F5F9" stroke-width="9" />
                <circle
                  cx="52" cy="52" r="44" fill="none"
                  :stroke="scoreColor" stroke-width="9" stroke-linecap="round"
                  :stroke-dasharray="circumference"
                  :stroke-dashoffset="dashOffset"
                  class="transition-[stroke-dashoffset] duration-700"
                />
              </svg>
              <div class="absolute inset-0 flex flex-col items-center justify-center">
                <span class="text-2xl font-bold text-foreground leading-none">{{ result.totalScore ?? '—' }}</span>
                <span class="text-[11px] text-muted-foreground mt-1">/ {{ result.maxScore ?? result.paperMaxScore }} 分</span>
              </div>
            </div>
            <div>
              <h2 class="text-base font-semibold text-foreground">{{ result.paperName }}</h2>
              <p class="text-xs text-muted-foreground mt-1">
                {{ result.mode === 'EXAM' ? '模拟考' : '专项练习' }} · 及格线 {{ result.passScore }} 分
              </p>
              <p class="mt-2 inline-flex items-center gap-1.5 text-sm font-medium" :class="result.passed ? 'text-success-500' : 'text-red-400'">
                <Icon :name="result.passed ? 'trophy' : 'alert-circle'" :size="16" />
                {{ result.passed ? '已达到及格线' : '未达到及格线' }}
              </p>
            </div>
          </div>

          <div class="border rounded-[10px] bg-card border-border p-5">
            <p class="text-xs text-muted-foreground mb-2">已评分题目正确率</p>
            <p class="text-2xl font-bold text-foreground">
              {{ result.gradedCount ? Math.round((result.correctCount / result.gradedCount) * 100) + '%' : '—' }}
            </p>
            <p class="text-[11px] text-muted-foreground mt-2">
              {{ result.correctCount }} 对 / {{ result.gradedCount - result.correctCount }} 错 /
              共 {{ result.questionCount }} 题
            </p>
          </div>

          <div class="border rounded-[10px] bg-card border-border p-5">
            <p class="text-xs text-muted-foreground mb-2">用时</p>
            <p class="text-2xl font-bold text-foreground">{{ durationText }}</p>
            <p class="text-[11px] text-muted-foreground mt-2 flex items-center gap-1">
              <Icon name="clock" :size="12" />
              {{ result.submitTime ? result.submitTime.replace('T', ' ').slice(0, 16) : '未交卷' }}
              <span v-if="result.deadlineExpired" class="text-red-400">· 超时自动交卷</span>
            </p>
          </div>
        </section>

        <!-- 题型 / 知识点分析 -->
        <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
          <section class="border rounded-[10px] bg-card border-border p-5">
            <h3 class="text-sm font-semibold text-foreground mb-4 inline-flex items-center gap-2">
              <Icon name="layers" :size="16" class="text-primary-500" /> 题型分析
            </h3>
            <div v-if="!result.typeAnalysis.length" class="text-xs text-muted-foreground py-6 text-center">暂无已评分题目</div>
            <ul v-else class="space-y-3.5">
              <li v-for="g in result.typeAnalysis" :key="g.key">
                <div class="flex items-center justify-between text-sm mb-1">
                  <span class="text-foreground">{{ g.name }}</span>
                  <span class="text-xs text-muted-foreground">
                    {{ g.score }}/{{ g.maxScore }} 分
                    <template v-if="g.rate != null">· {{ Math.round(g.rate) }}%</template>
                  </span>
                </div>
                <div class="h-2 rounded-full bg-gray-100 overflow-hidden">
                  <div class="h-full rounded-full transition-all duration-500"
                    :class="rateColor(g.rate)"
                    :style="{ width: (g.rate ?? 0) + '%' }"></div>
                </div>
                <p class="text-[11px] text-muted-foreground mt-1">
                  已评 {{ g.gradedCount }}/{{ g.count }} 题 · 答对 {{ g.correctCount }} 题
                </p>
              </li>
            </ul>
          </section>

          <section class="border rounded-[10px] bg-card border-border p-5">
            <h3 class="text-sm font-semibold text-foreground mb-4 inline-flex items-center gap-2">
              <Icon name="target" :size="16" class="text-primary-500" /> 知识点分析
            </h3>
            <div v-if="!result.topicAnalysis.length" class="text-xs text-muted-foreground py-6 text-center">暂无已评分题目</div>
            <ul v-else class="space-y-3.5">
              <li v-for="g in result.topicAnalysis" :key="g.key">
                <div class="flex items-center justify-between text-sm mb-1">
                  <span class="text-foreground">{{ g.name }}</span>
                  <span class="text-xs">
                    <template v-if="g.rate != null">
                      <span :class="g.rate >= 60 ? 'text-success-500' : 'text-red-400'">{{ Math.round(g.rate) }}%</span>
                    </template>
                    <span v-else class="text-warning-500">证据不足</span>
                  </span>
                </div>
                <div class="h-2 rounded-full bg-gray-100 overflow-hidden">
                  <div v-if="g.rate != null" class="h-full rounded-full transition-all duration-500"
                    :class="rateColor(g.rate)"
                    :style="{ width: g.rate + '%' }"></div>
                  <div v-else class="h-full rounded-full bg-gray-200" style="width: 12%"></div>
                </div>
                <p class="text-[11px] mt-1" :class="g.rate == null ? 'text-warning-500' : 'text-muted-foreground'">
                  {{ g.gradedCount }}/{{ g.count }} 题已评分
                  <template v-if="g.rate == null"> · 样本量不足，暂不下掌握结论</template>
                </p>
              </li>
            </ul>
          </section>
        </div>

        <ExamDiagnosisPanel v-if="result.status !== 'IN_PROGRESS'" :attempt-id="attemptId" :refresh-key="diagnosisRevision" />

        <!-- 逐题解析 -->
        <section class="border rounded-[10px] bg-card border-border">
          <header class="px-5 py-4 border-b border-border">
            <h3 class="text-sm font-semibold text-foreground inline-flex items-center gap-2">
              <Icon name="book-open" :size="16" class="text-primary-500" /> 逐题作答与解析
            </h3>
          </header>
          <ul class="divide-y divide-border">
            <li v-for="it in result.items" :key="it.paperQuestionId" class="p-5">
              <div class="flex items-start gap-3 flex-wrap mb-2">
                <span class="text-xs px-2 py-0.5 rounded bg-primary-50 text-primary-600 font-medium">
                  {{ it.ordinal }} · {{ typeLabel(it.questionType) }}
                </span>
                <span v-if="it.topic" class="text-xs px-2 py-0.5 rounded bg-gray-100 text-muted-foreground">
                  {{ topicLabel(it.topic) }}
                </span>
                <span class="text-xs text-muted-foreground">{{ it.maxScore }} 分</span>
                <span class="ml-auto text-xs font-semibold" :class="scoreBadgeClass(it)">
                  {{ scoreBadgeText(it) }}
                </span>
              </div>
              <p class="text-sm font-medium text-foreground mb-1">{{ it.title }}</p>
              <p class="text-sm text-foreground/80 whitespace-pre-wrap leading-7 mb-3">{{ it.content }}</p>

              <!-- 客观题选项 -->
              <ul v-if="it.options.length" class="space-y-1.5 mb-3">
                <li
                  v-for="(opt, i) in it.options"
                  :key="i"
                  class="flex items-start gap-2 text-sm px-3 py-2 rounded-lg"
                  :class="isCorrectOption(it, i)
                    ? 'bg-success-50 text-success-700'
                    : isWrongPicked(it, i)
                      ? 'bg-red-50 text-red-600'
                      : 'text-foreground/80'"
                >
                  <span class="shrink-0 w-5 h-5 rounded border flex items-center justify-center text-[11px] mt-0.5"
                    :class="isCorrectOption(it, i) ? 'border-success-400 bg-success-500 text-white' : 'border-border'">
                    {{ letters[i] }}
                  </span>
                  <span class="flex-1 leading-6">{{ opt }}</span>
                  <Icon v-if="isCorrectOption(it, i)" name="check" :size="15" class="text-success-500 shrink-0 mt-1" />
                  <Icon v-if="isWrongPicked(it, i)" name="x" :size="15" class="text-red-400 shrink-0 mt-1" />
                </li>
              </ul>

              <div class="grid grid-cols-1 sm:grid-cols-2 gap-2 text-sm mb-3">
                <div class="rounded-lg bg-red-50/60 px-3 py-2">
                  <p class="text-[11px] text-red-400 mb-0.5">{{ it.questionType === 'CODE' ? '你的代码' : '你的作答' }}</p>
                  <pre
                    v-if="it.questionType === 'CODE' && it.userAnswer"
                    class="text-[12px] font-mono text-red-700 whitespace-pre-wrap leading-5 max-h-64 overflow-y-auto"
                  >{{ it.userAnswer }}</pre>
                  <p v-else-if="it.questionType === 'CODE'" class="text-red-600 leading-6">（未作答，按 0 分计）</p>
                  <p v-else class="text-red-600 leading-6">{{ displayUser(it) }}</p>
                </div>
                <div v-if="it.correctAnswer" class="rounded-lg bg-success-50/60 px-3 py-2">
                  <p class="text-[11px] text-success-500 mb-0.5">标准答案</p>
                  <p class="text-success-700 leading-6">{{ it.correctAnswerText || it.correctAnswer }}</p>
                </div>
              </div>

              <div v-if="it.explanation" class="rounded-lg bg-gray-50 px-3 py-2.5 text-xs text-muted-foreground leading-6">
                <span class="font-medium text-foreground">解析：</span>{{ it.explanation }}
              </div>

              <!-- AI 五维评分 -->
              <div v-if="it.rubric && it.rubric.length" class="mt-3 rounded-xl border border-primary-100 bg-primary-50/40 p-4">
                <div class="flex items-center justify-between mb-3 flex-wrap gap-2">
                  <p class="text-xs font-semibold text-primary-600 inline-flex items-center gap-1.5">
                    <Icon name="sparkles" :size="14" /> AI 辅助评分 · {{ it.score }}/{{ it.maxScore }} 分
                    <span v-if="it.confidence != null" class="text-muted-foreground font-normal">
                      （置信度 {{ Math.round(it.confidence * 100) }}%）
                    </span>
                  </p>
                </div>
                <div class="grid grid-cols-1 sm:grid-cols-5 gap-2 mb-3">
                  <div v-for="d in it.rubric" :key="d.key" class="bg-white rounded-lg border border-primary-100 px-2.5 py-2">
                    <p class="text-[11px] text-muted-foreground">{{ d.name }}</p>
                    <p class="text-sm font-semibold text-foreground">{{ d.score }}<span class="text-[11px] text-muted-foreground">/{{ d.maxScore }}</span></p>
                    <div class="h-1 mt-1 rounded-full bg-gray-100 overflow-hidden">
                      <div class="h-full bg-primary-500 rounded-full" :style="{ width: (d.maxScore ? (d.score / d.maxScore) * 100 : 0) + '%' }"></div>
                    </div>
                  </div>
                </div>
                <ul v-if="it.hitPoints?.length" class="text-xs text-success-600 space-y-1 mb-2">
                  <li v-for="(h, i) in it.hitPoints" :key="'h' + i" class="flex items-start gap-1">
                    <Icon name="check-circle" :size="13" class="mt-0.5 shrink-0" /> {{ h }}
                  </li>
                </ul>
                <ul v-if="it.missingPoints?.length" class="text-xs text-warning-600 space-y-1 mb-2">
                  <li v-for="(m2, i) in it.missingPoints" :key="'m' + i" class="flex items-start gap-1">
                    <Icon name="alert-circle" :size="13" class="mt-0.5 shrink-0" /> 缺失：{{ m2 }}
                  </li>
                </ul>
                <ul v-if="it.errors?.length" class="text-xs text-red-500 space-y-1 mb-2">
                  <li v-for="(er, i) in it.errors" :key="'e' + i" class="flex items-start gap-1">
                    <Icon name="x-circle" :size="13" class="mt-0.5 shrink-0" /> {{ er }}
                  </li>
                </ul>
                <p v-if="it.suggestions?.length" class="text-xs text-muted-foreground leading-6">
                  <span class="font-medium text-foreground">改进建议：</span>{{ it.suggestions.join('；') }}
                </p>
                <p class="text-[11px] text-muted-foreground mt-2">{{ it.aiGradeNote || 'AI 辅助评分，非人工审核成绩。' }}</p>
              </div>

              <div v-else-if="!it.graded" class="mt-3 rounded-lg border border-dashed border-warning-300 bg-warning-50 px-3 py-2.5 text-xs text-warning-700 inline-flex items-center gap-1.5">
                <Icon name="clock" :size="13" />
                {{ it.questionType === 'SHORT_ANSWER'
                  ? '主观题待 AI 评卷，当前不计入最终成绩'
                  : it.questionType === 'CODE'
                    ? '代码题待判题服务处理，当前不计入最终成绩（系统错误不记为答错）'
                    : '本题暂未评分' }}
              </div>
            </li>
          </ul>
        </section>

        <div class="flex items-center justify-between">
          <button class="px-4 py-2 rounded-lg text-sm border border-border bg-card text-foreground hover:border-primary-300 transition-colors"
            @click="goCenter">
            返回考试中心
          </button>
          <button class="px-4 py-2 rounded-lg text-sm text-white bg-primary-500 hover:bg-primary-600 transition-colors inline-flex items-center gap-1.5"
            @click="goMistakes">
            <Icon name="bookmark-check" :size="15" /> 查看考试错题本
          </button>
        </div>
      </template>
    </div>
  </MiniAppShell>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MiniAppShell from '@/components/layout/MiniAppShell.vue'
import Icon from '@/components/ui/Icon.vue'
import ExamDiagnosisPanel from './ExamDiagnosisPanel.vue'
import {
  examApi,
  type ExamResult,
  type ResultItem,
  QUESTION_TYPE_LABELS,
  TOPIC_LABELS,
} from '@/api/exam'
import { notify, getApiError } from '@/utils/toast'

const route = useRoute()
const router = useRouter()
const attemptId = computed(() => {
  const raw = route.params.attemptId
  return typeof raw === 'string' && /^[1-9]\d*$/.test(raw) && Number.isSafeInteger(Number(raw)) ? Number(raw) : 0
})
const diagnosisRevision = ref(0)
let resultRequest = 0

const loading = ref(true)
const error = ref('')
const result = ref<ExamResult | null>(null)
const grading = ref(false)
const rejudging = ref(false)

const letters = ['A', 'B', 'C', 'D', 'E', 'F', 'G']
const circumference = 2 * Math.PI * 44

const dashOffset = computed(() => {
  if (!result.value || !result.value.maxScore) return circumference
  const rate = Math.min(1, (result.value.totalScore || 0) / result.value.maxScore)
  return circumference * (1 - rate)
})

const scoreColor = computed(() => {
  if (result.value?.passed) return 'var(--kb-success, #22c55e)'
  return 'var(--kb-danger, #f87171)'
})

const durationText = computed(() => {
  const s = result.value?.durationSeconds
  if (s == null) return '—'
  const m = Math.floor(s / 60)
  const sec = s % 60
  if (m < 60) return `${m}分${sec}秒`
  return `${Math.floor(m / 60)}小时${m % 60}分`
})

const canAiGrade = computed(() => {
  if (!result.value) return false
  const hasPendingSubjective = result.value.items.some(
    (i) => !i.graded && i.questionType === 'SHORT_ANSWER'
  )
  return (
    hasPendingSubjective ||
    result.value.gradingStatus === 'PENDING_AI' ||
    result.value.gradingStatus === 'AI_FAILED'
  )
})

const canRejudgeCode = computed(() => {
  if (!result.value) return false
  return (
    result.value.gradingStatus === 'PENDING_JUDGE' ||
    result.value.items.some((i) => !i.graded && i.questionType === 'CODE')
  )
})

const banner = computed(() => {
  const r = result.value!
  const pending = r.items.filter((i) => !i.graded).length
  // Phase 3G：代码题等待判题优先提示（未判完不按 0 分计，系统错误不记错）
  if (r.gradingStatus === 'PENDING_JUDGE') {
    const codePending = r.items.filter((i) => !i.graded && i.questionType === 'CODE').length
    return {
      class: 'bg-warning-50 text-warning-700 border border-warning-200',
      icon: 'cpu',
      title: '暂定成绩：代码题判题中',
      msg: `还有 ${codePending || pending} 道算法代码题等待判题服务处理，当前总分不包含未判完题目（不按 0 分计）。判题服务恢复后可点击「重试代码判题」；系统错误不会记为答错。`,
    }
  }
  if (r.gradingStatus === 'PENDING_AI' || (r.status === 'GRADING' && pending > 0)) {
    return {
      class: 'bg-warning-50 text-warning-700 border border-warning-200',
      icon: 'clock',
      title: '暂定成绩：主观题评分中',
      msg: `还有 ${pending} 道主观题待 AI 评卷，当前总分仅包含已评分题目，不把未评分题按 0 分计入最终成绩。评分完成后本页自动更新。`,
    }
  }
  if (r.gradingStatus === 'AI_FAILED' || r.status === 'GRADING_FAILED') {
    return {
      class: 'bg-red-50 text-red-600 border border-red-200',
      icon: 'alert-circle',
      title: '部分题目 AI 评分失败',
      msg: '失败题目保持待评状态（未按 0 分处理）。模型恢复后可重试评卷，历史评分证据会保留版本。',
    }
  }
  if (r.gradingStatus === 'REVIEW_REQUIRED') {
    return {
      class: 'bg-primary-50 text-primary-600 border border-primary-200',
      icon: 'shield-check',
      title: '部分题目需要复核',
      msg: 'AI 对该题评分置信度较低，建议人工复核；当前分数标注为 AI 辅助评分。',
    }
  }
  return {
    class: 'bg-success-50 text-success-600 border border-success-200',
    icon: 'check-circle',
    title: '评分完成',
    msg: '客观题由后端规则判分，主观题为 AI 五维辅助评分；错题已自动收入考试错题本（未作答与待评题不收录）。',
  }
})

async function load() {
  const requestId = ++resultRequest
  loading.value = true
  error.value = ''
  if (!attemptId.value) { result.value = null; error.value = '报告 ID 无效'; loading.value = false; return }
  try {
    const r = await examApi.result(attemptId.value)
    if (requestId !== resultRequest) return
    result.value = r
    diagnosisRevision.value++
    if (r.status === 'IN_PROGRESS') {
      router.replace(`/exam/take/${attemptId.value}`)
    }
  } catch (e: unknown) {
    if (requestId === resultRequest) error.value = getApiError(e, '加载失败')
  } finally {
    if (requestId === resultRequest) loading.value = false
  }
}

async function runAi() {
  grading.value = true
  try {
    const res = await examApi.aiGrade(attemptId.value)
    if (res.pending > 0) {
      notify(`${res.gradedAi} 题已完成 AI 评分，${res.pending} 题仍待评`, 'info', 4000)
    } else {
      notify('AI 评卷完成', 'success')
    }
    await load()
  } catch (e) {
    notify(getApiError(e, 'AI 评卷失败，题目保持待评状态'), 'error', 5000)
    await load()
  } finally {
    grading.value = false
  }
}

async function runRejudgeCode() {
  rejudging.value = true
  try {
    const r = await examApi.rejudgeCode(attemptId.value)
    if (r.gradingStatus === 'PENDING_JUDGE') {
      notify('判题服务仍未就绪，题目保持待判（未按 0 分计）', 'warning', 5000)
    } else {
      notify('代码题判题完成', 'success')
    }
    await load()
  } catch (e) {
    notify(getApiError(e, '重试判题失败，题目保持待判状态'), 'error', 5000)
    await load()
  } finally {
    rejudging.value = false
  }
}

function typeLabel(t: ResultItem['questionType']): string {
  return QUESTION_TYPE_LABELS[t]
}

function topicLabel(t?: string): string {
  return (t && TOPIC_LABELS[t]) || t || '综合'
}

function pickedIndices(it: ResultItem): Set<string> {
  return new Set((it.userAnswer || '').split(',').map((x) => x.trim()).filter(Boolean))
}

function correctIndices(it: ResultItem): Set<string> {
  if (it.questionType === 'TRUE_FALSE') return new Set()
  return new Set((it.correctAnswer || '').split(',').map((x) => x.trim()).filter(Boolean))
}

function isCorrectOption(it: ResultItem, i: number): boolean {
  return correctIndices(it).has(String(i))
}

function isWrongPicked(it: ResultItem, i: number): boolean {
  return pickedIndices(it).has(String(i)) && !isCorrectOption(it, i)
}

function displayUser(it: ResultItem): string {
  if (!it.userAnswer) return '（未作答）'
  if (it.questionType === 'TRUE_FALSE') return it.userAnswer === 'true' ? '正确' : '错误'
  if (it.options.length) {
    const picked = pickedIndices(it)
    return [...picked].map((p) => letters[Number(p)]).sort().join('、') || '（未作答）'
  }
  return it.userAnswer
}

function scoreBadgeClass(it: ResultItem): string {
  if (!it.graded) return 'text-warning-500'
  if (it.questionType === 'SHORT_ANSWER') return 'text-primary-600'
  return it.correct ? 'text-success-500' : 'text-red-400'
}

function scoreBadgeText(it: ResultItem): string {
  if (!it.graded) return '待评 · 不计 0 分'
  return `${it.score ?? 0} / ${it.maxScore} 分`
}

function rateColor(rate: number | null): string {
  if (rate == null) return 'bg-gray-300'
  if (rate >= 80) return 'bg-success-500'
  if (rate >= 60) return 'bg-warning-400'
  return 'bg-red-400'
}

function goCenter() {
  router.push('/exam')
}

function goMistakes() {
  router.push('/exam/mistakes')
}

watch(attemptId, () => { result.value = null; void load() }, { immediate: true })
onBeforeUnmount(() => { resultRequest++ })
</script>
