<template>
  <div class="exam-take min-h-screen bg-gray-50 flex flex-col">
    <!-- 顶栏 -->
    <header class="h-14 bg-white border-b border-border flex items-center px-4 gap-4 shrink-0">
      <button
        class="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
        @click="requestExit"
      >
        <Icon name="chevron-left" :size="18" />
        退出
      </button>
      <div class="min-w-0 flex-1">
        <h1 class="text-sm font-semibold text-foreground truncate">{{ attempt?.paperName || '在线考试' }}</h1>
      </div>
      <div class="hidden sm:flex items-center gap-2 text-xs text-muted-foreground">
        <span
          class="px-2 py-0.5 rounded"
          :class="attempt?.mode === 'EXAM' ? 'bg-red-50 text-red-500' : 'bg-primary-50 text-primary-600'"
        >
          {{ attempt?.mode === 'EXAM' ? '模拟考' : '专项练习' }}
        </span>
        <span v-if="lastSaved" class="inline-flex items-center gap-1">
          <Icon name="check" :size="12" class="text-success-500" />
          草稿已保存 {{ lastSaved }}
        </span>
      </div>
      <div
        class="font-mono text-lg font-bold tabular-nums px-3 py-1 rounded-lg"
        :class="remainingSeconds <= 300 ? 'bg-red-50 text-red-500 animate-pulse' : 'bg-primary-50 text-primary-600'"
      >
        {{ countdownText }}
      </div>
    </header>

    <section v-if="saveError" class="draft-error" role="alert" data-draft-error>
      <p>{{ saveError }}</p>
      <p>当前页面的作答仍保留，保存成功前请勿关闭页面。</p>
      <Button v-if="!draftConflict" size="sm" variant="secondary" :disabled="saving" @click="saveDraft(false)">重试保存</Button>
      <Button v-else size="sm" variant="secondary" @click="reloadServerDraft">读取服务端草稿</Button>
    </section>
    <p v-if="saving" class="draft-saving" role="status">草稿保存中…</p>
    <!-- 加载 / 错误 -->
    <div v-if="loading" class="flex-1 flex items-center justify-center">
      <div class="text-center">
        <Icon name="loader" :size="28" class="animate-spin text-primary-500 mx-auto mb-3" />
        <p class="text-sm text-muted-foreground">正在加载试卷…</p>
      </div>
    </div>
    <div v-else-if="loadError" class="flex-1 flex items-center justify-center p-6">
      <div class="text-center max-w-sm">
        <Icon name="alert-circle" :size="28" class="text-red-400 mx-auto mb-3" />
        <p class="text-sm text-red-600 font-medium mb-1">试卷加载失败</p>
        <p class="text-xs text-muted-foreground mb-4">{{ loadError }}</p>
        <button class="px-4 py-2 rounded-lg text-sm text-white bg-primary-500 hover:bg-primary-600" @click="load">
          重试
        </button>
      </div>
    </div>

    <main v-else-if="attempt && current" class="flex-1 grid grid-cols-1 lg:grid-cols-[220px_1fr_280px] gap-0 overflow-hidden">
      <!-- 左：题号导航 -->
      <aside class="hidden lg:flex flex-col border-r border-border bg-white p-4 gap-3 overflow-y-auto">
        <div class="flex items-center justify-between">
          <p class="text-xs font-semibold text-foreground">答题卡</p>
          <p class="text-xs text-muted-foreground">{{ answeredCount }}/{{ attempt.questions.length }}</p>
        </div>
        <div class="grid grid-cols-5 gap-2">
          <button
            v-for="q in attempt.questions"
            :key="q.paperQuestionId"
            class="relative h-9 rounded-lg text-xs font-medium border transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)]"
            :class="navBtnClass(q)"
            :title="`第 ${q.ordinal} 题`"
            @click="jumpTo(q)"
          >
            {{ q.ordinal }}
            <span
              v-if="markedSet.has(q.ordinal)"
              class="absolute -top-1 -right-1 w-3 h-3 rounded-full bg-warning-400 border-2 border-white"
            ></span>
          </button>
        </div>
        <div class="mt-2 space-y-1.5 text-[11px] text-muted-foreground">
          <p class="flex items-center gap-1.5"><span class="w-3 h-3 rounded bg-primary-500"></span>当前题</p>
          <p class="flex items-center gap-1.5"><span class="w-3 h-3 rounded bg-success-100 border border-success-300"></span>已作答</p>
          <p class="flex items-center gap-1.5"><span class="w-3 h-3 rounded bg-white border border-border"></span>未作答</p>
          <p class="flex items-center gap-1.5"><span class="w-3 h-3 rounded bg-warning-100 border border-warning-300"></span>标记待检查</p>
        </div>
      </aside>

      <!-- 中：题目 -->
      <section class="overflow-y-auto p-5 sm:p-8">
        <fieldset :disabled="leaving || submitting" class="exam-editable">
        <div class="max-w-3xl mx-auto">
          <div class="flex items-center gap-2 flex-wrap mb-3">
            <span class="text-xs px-2 py-0.5 rounded bg-primary-50 text-primary-600 font-medium">
              {{ typeLabel(current.questionType) }}
            </span>
            <span v-if="current.topic" class="text-xs px-2 py-0.5 rounded bg-gray-100 text-muted-foreground">
              {{ topicLabel(current.topic) }}
            </span>
            <span class="text-xs text-muted-foreground">本题 {{ current.score }} 分</span>
          </div>

          <h2 class="text-lg font-semibold text-foreground leading-relaxed mb-2">
            {{ current.ordinal }}. {{ current.title }}
          </h2>
          <p class="text-sm text-foreground/80 leading-7 whitespace-pre-wrap mb-6">{{ current.content }}</p>

          <!-- 单选 -->
          <div v-if="current.questionType === 'SINGLE_CHOICE'" class="space-y-2.5">
            <button
              v-for="(opt, i) in current.options"
              :key="i"
              class="w-full text-left flex items-start gap-3 px-4 py-3 rounded-xl border transition-colors"
              :class="answers[current.paperQuestionId] === String(i)
                ? 'border-primary-400 bg-primary-50/60'
                : 'border-border bg-white hover:border-primary-200'"
              @click="setAnswer(current.paperQuestionId, String(i))"
            >
              <span
                class="shrink-0 w-6 h-6 rounded-full border flex items-center justify-center text-xs font-medium mt-0.5"
                :class="answers[current.paperQuestionId] === String(i)
                  ? 'bg-primary-500 text-white border-primary-500'
                  : 'border-border text-muted-foreground'"
              >
                {{ letters[i] }}
              </span>
              <span class="text-sm text-foreground leading-6">{{ opt }}</span>
            </button>
          </div>

          <!-- 多选 -->
          <div v-else-if="current.questionType === 'MULTIPLE_CHOICE'" class="space-y-2.5">
            <p class="text-xs text-muted-foreground mb-2">多选题：错选不得分{{ multiPartial ? '，少选按比例得分' : '，必须全部选对才得分' }}</p>
            <button
              v-for="(opt, i) in current.options"
              :key="i"
              class="w-full text-left flex items-start gap-3 px-4 py-3 rounded-xl border transition-colors"
              :class="multiSelected(current, i)
                ? 'border-primary-400 bg-primary-50/60'
                : 'border-border bg-white hover:border-primary-200'"
              @click="toggleMulti(current, i)"
            >
              <span
                class="shrink-0 w-6 h-6 rounded-md border flex items-center justify-center text-xs font-medium mt-0.5"
                :class="multiSelected(current, i)
                  ? 'bg-primary-500 text-white border-primary-500'
                  : 'border-border text-muted-foreground'"
              >
                <Icon v-if="multiSelected(current, i)" name="check" :size="14" />
                <span v-else>{{ letters[i] }}</span>
              </span>
              <span class="text-sm text-foreground leading-6">{{ opt }}</span>
            </button>
          </div>

          <!-- 判断 -->
          <div v-else-if="current.questionType === 'TRUE_FALSE'" class="flex gap-4">
            <button
              v-for="tf in [{ v: 'true', t: '正确' }, { v: 'false', t: '错误' }]"
              :key="tf.v"
              class="flex-1 py-6 rounded-xl border text-base font-medium transition-colors"
              :class="answers[current.paperQuestionId] === tf.v
                ? 'border-primary-400 bg-primary-50 text-primary-600'
                : 'border-border bg-white text-muted-foreground hover:border-primary-200'"
              @click="setAnswer(current.paperQuestionId, tf.v)"
            >
              {{ tf.t }}
            </button>
          </div>

          <!-- 填空 -->
          <div v-else-if="current.questionType === 'FILL_BLANK'" class="space-y-2">
            <p class="text-xs text-muted-foreground">填空题：忽略首尾空格与大小写；多个等价答案以系统配置为准。</p>
            <input
              v-model="answers[current.paperQuestionId]"
              @input="scheduleSave"
              type="text"
              placeholder="请输入答案"
              class="w-full px-4 py-3 text-sm outline-none kb-input kb-form-control"
            />
          </div>

          <!-- 算法代码题（Phase 3G）：交卷后由独立判题链路统一判定 -->
          <div v-else-if="current.questionType === 'CODE'" class="space-y-3">
            <div v-if="current.codeProblem" class="space-y-3">
              <div v-if="current.codeProblem.functionSignature" class="rounded-xl border border-border bg-gray-50 p-3">
                <p class="text-xs text-muted-foreground mb-1">函数签名（固定 Harness，请勿修改方法名/参数）</p>
                <code class="text-[13px] font-mono whitespace-pre-wrap">{{ current.codeProblem.functionSignature }}</code>
              </div>
              <div v-if="current.codeProblem.constraints" class="rounded-xl border border-border p-3">
                <p class="text-xs text-muted-foreground mb-1">约束 · 时间 {{ current.codeProblem.timeLimitMs ?? 1000 }}ms / 内存 {{ current.codeProblem.memoryLimitMb ?? 256 }}MB</p>
                <p class="text-[13px] whitespace-pre-wrap leading-6">{{ current.codeProblem.constraints }}</p>
              </div>
              <details v-if="current.codeProblem.publicCases?.length" class="rounded-xl border border-border p-3">
                <summary class="text-xs text-muted-foreground cursor-pointer">公开测试用例（{{ current.codeProblem.publicCases.length }}）</summary>
                <div
                  v-for="c in current.codeProblem.publicCases"
                  :key="c.orderNo"
                  class="mt-2 text-[12px] font-mono leading-5 text-muted-foreground"
                >
                  <p>输入：{{ c.input }}</p>
                  <p>期望：{{ c.expectedOutput }}</p>
                </div>
              </details>
            </div>
            <p class="text-xs text-muted-foreground">
              算法代码题：交卷后由独立判题服务统一编译运行，包含隐藏用例；判题完成前该题不按 0 分计入，系统错误不会记为你的答错。
            </p>
            <textarea
              v-model="answers[current.paperQuestionId]"
              @input="scheduleSave"
              rows="16"
              spellcheck="false"
              placeholder="请在此编写 Java 17 代码…"
              class="w-full px-4 py-3 rounded-xl border border-border bg-white text-[13px] leading-6 font-mono outline-none focus:border-primary-400 focus:ring-2 focus:ring-primary-100 resize-y"
            ></textarea>
            <div class="flex items-center justify-between">
              <button
                v-if="current.codeProblem?.starterCode && !(answers[current.paperQuestionId] || '').trim()"
                class="text-xs text-primary-600 hover:underline"
                @click="setAnswer(current.paperQuestionId, String(current.codeProblem?.starterCode || ''))"
              >
                填入初始代码
              </button>
              <span v-else></span>
              <p class="text-[11px] text-muted-foreground text-right">草稿自动保存 · 交卷后判题</p>
            </div>
          </div>

          <!-- 主观题 -->
          <div v-else class="space-y-2">
            <p class="text-xs text-muted-foreground">
              主观题：交卷后由 AI 从技术准确性、原理理解、完整性、工程实践、表达结构五个维度辅助评分（非人工审核成绩）。
            </p>
            <textarea
              v-model="answers[current.paperQuestionId]"
              @input="scheduleSave"
              rows="16"
              placeholder="请有条理地作答，可分点陈述原理、排查过程与结论…"
              class="w-full px-4 py-3 text-sm leading-7 outline-none resize-y font-mono kb-textarea kb-form-control"
            ></textarea>
            <p class="text-[11px] text-muted-foreground text-right">
              {{ (answers[current.paperQuestionId] || '').length }} 字 · 草稿自动保存
            </p>
          </div>

          <!-- 翻页 -->
          <div class="mt-10 flex items-center justify-between">
            <button
              :disabled="index === 0"
              class="inline-flex items-center gap-1 px-4 py-2 rounded-lg text-sm border border-border bg-white text-foreground hover:border-primary-300 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
              @click="goPrev"
            >
              <Icon name="chevron-left" :size="16" /> 上一题
            </button>
            <button
              class="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg text-sm border text-sm"
              :class="markedSet.has(current.ordinal)
                ? 'border-warning-300 bg-warning-50 text-warning-600'
                : 'border-border bg-white text-muted-foreground hover:border-warning-300'"
              @click="toggleMark(current.ordinal)"
            >
              <Icon name="bookmark" :size="15" />
              {{ markedSet.has(current.ordinal) ? '取消标记' : '标记待检查' }}
            </button>
            <button
              v-if="index < attempt.questions.length - 1"
              class="inline-flex items-center gap-1 px-4 py-2 rounded-lg text-sm bg-primary-500 text-white hover:bg-primary-600 transition-colors"
              @click="goNext"
            >
              下一题 <Icon name="chevron-right" :size="16" />
            </button>
            <button
              v-else
              class="inline-flex items-center gap-1 px-4 py-2 rounded-lg text-sm font-medium text-white bg-success-500 hover:bg-success-600 transition-colors"
              @click="requestSubmit"
            >
              <Icon name="check-circle" :size="16" /> 去交卷
            </button>
          </div>
        </div>
        </fieldset>
      </section>

      <!-- 右：状态 -->
      <aside class="hidden lg:flex flex-col border-l border-border bg-white p-4 gap-4 overflow-y-auto">
        <div class="rounded-xl bg-gray-50 p-4">
          <p class="text-xs text-muted-foreground mb-2">作答进度</p>
          <div class="h-2 rounded-full bg-gray-200 overflow-hidden">
            <div
              class="h-full bg-primary-500 rounded-full transition-all duration-300"
              :style="{ width: progressPercent + '%' }"
            ></div>
          </div>
          <p class="text-xs text-foreground font-medium mt-2">{{ answeredCount }} 题已答 · {{ unansweredCount }} 题未答</p>
        </div>

        <div class="rounded-xl border border-warning-200 bg-warning-50 p-3 text-[11px] text-warning-700 leading-relaxed">
          <p class="font-semibold mb-1 inline-flex items-center gap-1"><Icon name="shield-check" :size="13" /> 诚信与计时说明</p>
          倒计时以服务器截止时间为准，本地改时间不会延长考试；草稿每 15 秒自动保存，超时自动交卷。
        </div>

        <button
          class="w-full inline-flex items-center justify-center gap-2 px-4 py-3 rounded-xl text-sm font-semibold text-white bg-primary-500 hover:bg-primary-600 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
          @click="requestSubmit"
        >
          <Icon name="check-circle" :size="17" />
          交卷
        </button>
        <p v-if="unansweredCount > 0" class="text-[11px] text-center text-warning-600">
          还有 {{ unansweredCount }} 道题未作答
        </p>
        <p v-else class="text-[11px] text-center text-success-500">所有题目均已作答</p>
      </aside>
    </main>

    <!-- 移动端底部操作条 -->
    <footer v-if="attempt" class="lg:hidden shrink-0 bg-white border-t border-border px-4 py-3 flex items-center gap-3">
      <span class="text-xs text-muted-foreground">{{ answeredCount }}/{{ attempt.questions.length }}</span>
      <button class="flex-1 py-2 rounded-lg text-sm border border-border text-foreground" @click="goPrev" :disabled="index === 0">上一题</button>
      <button v-if="index < attempt.questions.length - 1" class="flex-1 py-2 rounded-lg text-sm bg-primary-500 text-white" @click="goNext">下一题</button>
      <button v-else class="flex-1 py-2 rounded-lg text-sm bg-success-500 text-white" @click="requestSubmit">交卷</button>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import Button from '@/components/ui/Button.vue'
import { examApi, type AttemptVO, type TakeQuestion, QUESTION_TYPE_LABELS, TOPIC_LABELS } from '@/api/exam'
import { notify, confirmDialog, getApiError } from '@/utils/toast'

const route = useRoute()
const router = useRouter()
const attemptId = Number(route.params.attemptId)

const loading = ref(true)
const loadError = ref('')
const attempt = ref<AttemptVO | null>(null)
const index = ref(0)
const answers = reactive<Record<string, string>>({})
const markedSet = ref<Set<number>>(new Set())
const submitting = ref(false)
const saving = ref(false)
const lastSaved = ref('')
const saveError = ref(''), draftConflict = ref(false), leaving = ref(false)

const letters = ['A', 'B', 'C', 'D', 'E', 'F', 'G']
let serverOffsetMs = 0
let tickTimer: ReturnType<typeof setInterval> | undefined
let saveTimer: ReturnType<typeof setTimeout> | undefined
let dirty = false
let editGeneration = 0
let inFlightSave: Promise<boolean> | null = null
let backupTimer: ReturnType<typeof setInterval> | undefined
let disposed = false

const current = computed<TakeQuestion | undefined>(() => attempt.value?.questions[index.value])
const answeredCount = computed(() =>
  attempt.value ? attempt.value.questions.filter((q) => (answers[q.paperQuestionId] || '').trim() !== '').length : 0
)
const unansweredCount = computed(() => (attempt.value ? attempt.value.questions.length - answeredCount.value : 0))
const progressPercent = computed(() =>
  attempt.value && attempt.value.questions.length
    ? Math.round((answeredCount.value / attempt.value.questions.length) * 100)
    : 0
)
const multiPartial = computed(() => true)

const remainingSeconds = ref(0)
const countdownText = computed(() => {
  const s = Math.max(0, remainingSeconds.value)
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  const sec = s % 60
  const mm = String(m).padStart(2, '0')
  const ss = String(sec).padStart(2, '0')
  return h > 0 ? `${h}:${mm}:${ss}` : `${mm}:${ss}`
})

function typeLabel(t: TakeQuestion['questionType']): string {
  return QUESTION_TYPE_LABELS[t]
}

function topicLabel(t?: string): string {
  return (t && TOPIC_LABELS[t]) || t || '综合'
}

function navBtnClass(q: TakeQuestion): string {
  const answered = (answers[q.paperQuestionId] || '').trim() !== ''
  const marked = markedSet.value.has(q.ordinal)
  if (q.ordinal === current.value?.ordinal) return 'bg-primary-500 text-white border-primary-500'
  if (marked) return 'bg-warning-100 text-warning-700 border-warning-300'
  if (answered) return 'bg-success-100 text-success-700 border-success-300'
  return 'bg-white text-muted-foreground border-border hover:border-primary-300'
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const a = await examApi.getAttempt(attemptId)
    if (a.status !== 'IN_PROGRESS') {
      router.replace(`/exam/result/${attemptId}`)
      return
    }
    clearInterval(tickTimer)
    clearTimeout(saveTimer)
    attempt.value = a
    Object.keys(answers).forEach(k => delete answers[k])
    dirty = false; saveError.value = ''; draftConflict.value = false; lastSaved.value = ''
    Object.keys(a.draft || {}).forEach((k) => {
      answers[k] = a.draft[k]
    })
    markedSet.value = new Set(a.marked || [])
    const serverNow = new Date(a.serverNow).getTime()
    const deadline = new Date(a.serverDeadline).getTime()
    serverOffsetMs = serverNow - Date.now()
    remainingSeconds.value = Math.max(0, Math.round((deadline - Date.now() - serverOffsetMs) / 1000))
    startTimers()
    if (remainingSeconds.value <= 0) {
      notify('考试时间已到，正在自动交卷…', 'warning')
      doSubmit(true)
    }
  } catch (e) {
    loadError.value = getApiError(e, '加载失败')
  } finally {
    loading.value = false
  }
}

function startTimers() {
  tickTimer = setInterval(() => {
    if (submitting.value) return
    const deadline = new Date(attempt.value!.serverDeadline).getTime()
    remainingSeconds.value = Math.max(0, Math.round((deadline - Date.now() - serverOffsetMs) / 1000))
    if (remainingSeconds.value <= 0) {
      doSubmit(true)
    }
  }, 1000)
}

function setAnswer(id: number | string, val: string) {
  answers[id] = val
  scheduleSave()
}

function multiSelected(q: TakeQuestion, i: number): boolean {
  const raw = answers[q.paperQuestionId] || ''
  return raw.split(',').map((x) => x.trim()).filter(Boolean).includes(String(i))
}

function toggleMulti(q: TakeQuestion, i: number) {
  const set = new Set(
    (answers[q.paperQuestionId] || '').split(',').map((x) => x.trim()).filter(Boolean)
  )
  if (set.has(String(i))) set.delete(String(i))
  else set.add(String(i))
  const ordered = [...set].map(Number).sort((a, b) => a - b).map(String)
  answers[q.paperQuestionId] = ordered.join(',')
  scheduleSave()
}

function toggleMark(ordinal: number) {
  const s = new Set(markedSet.value)
  if (s.has(ordinal)) s.delete(ordinal)
  else s.add(ordinal)
  markedSet.value = s
  scheduleSave()
}

function jumpTo(q: TakeQuestion) {
  index.value = attempt.value!.questions.findIndex((x) => x.paperQuestionId === q.paperQuestionId)
}

function goPrev() {
  if (index.value > 0) index.value -= 1
}

function goNext() {
  if (attempt.value && index.value < attempt.value.questions.length - 1) index.value += 1
}

function scheduleSave() {
  dirty = true
  editGeneration++
  lastSaved.value = ''
  clearTimeout(saveTimer)
  saveTimer = setTimeout(() => saveDraft(false), 2000)
}

async function saveDraft(silent: boolean): Promise<boolean> {
  if (inFlightSave) return inFlightSave
  if (!dirty) return true
  if (!attempt.value || submitting.value || draftConflict.value || disposed) return false
  const generation = editGeneration, snapshot = {...answers}, marked = [...markedSet.value], revision = attempt.value.draftRevision || 0
  saving.value = true
  inFlightSave = (async () => {
    try {
      const saved = await examApi.saveDraft(attemptId, snapshot, marked, revision)
      if (disposed) return false
      attempt.value!.draftRevision = saved.draftRevision
      serverOffsetMs = new Date(saved.serverNow).getTime() - Date.now()
      dirty = generation !== editGeneration
      saveError.value = ''
      if (!dirty) {
        const d = new Date()
        lastSaved.value = `${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}:${String(d.getSeconds()).padStart(2,'0')}`
      }
      return true
    } catch (e) {
      if (!disposed) {
        saveError.value = getApiError(e,'草稿保存失败，请重试')
        draftConflict.value = (e as {status?:number;response?:{status?:number}})?.status === 409 || (e as {response?:{status?:number}})?.response?.status === 409
        if (!silent) notify(saveError.value,'error')
      }
      return false
    }
  })()
  try { return await inFlightSave }
  finally {
    inFlightSave = null; saving.value = false
    if (!disposed && dirty && !saveError.value && !draftConflict.value) {
      clearTimeout(saveTimer); saveTimer = setTimeout(() => void saveDraft(false),2000)
    }
  }
}

async function reloadServerDraft() {
  if (await confirmDialog('读取服务端草稿会替换本页尚未保存的答案，请先复制需要保留的作答。继续吗？', {"title":"确认替换现有内容？","confirmText":"确认替换","cancelText":"返回编辑","icon":"refresh-cw","variant":"danger"})) await load()
}

async function requestSubmit() {
  if (submitting.value || leaving.value || !attempt.value) return
  const unanswered = unansweredCount.value
  const markedCount = markedSet.value.size
  const tip = [
    unanswered > 0 ? `还有 ${unanswered} 道题未作答（未作答按答错处理）` : '所有题目均已作答',
    markedCount > 0 ? `${markedCount} 道题被标记待检查` : '',
  ].filter(Boolean).join('；')
  const ok = await confirmDialog(`${tip}。确定要交卷吗？交卷后${attempt.value.mode === 'EXAM' ? '不可修改答案' : '可在结果页查看解析'}。`, {"title":"提交试卷？","confirmText":"确认交卷","cancelText":"继续作答","icon":"check-circle","variant":"default"})
  if (ok) doSubmit(false)
}

async function requestExit() {
  if (!submitting.value) await router.push('/exam')
}

async function doSubmit(timeout: boolean) {
  if (submitting.value || !attempt.value) return
  clearTimeout(saveTimer)
  if (inFlightSave) await inFlightSave
  if (draftConflict.value && !timeout) { notify('草稿存在冲突，请先读取服务端草稿。','warning'); return }
  submitting.value = true
  try {
    const result = await examApi.submit(attemptId, {
      answers: { ...answers },
      marked: [...markedSet.value],
      expectedRevision: attempt.value.draftRevision || 0,
      clientVersion: 'exam-web-1.1',
    })
    notify(timeout ? '超时已自动交卷' : '交卷成功', 'success')
    router.replace(`/exam/result/${result.attemptId}`)
  } catch (e) {
    submitting.value = false
    saveError.value=getApiError(e,'交卷失败，请重试'); notify(saveError.value, 'error', 5000)
  }
}

function beforeUnloadHandler(e: BeforeUnloadEvent) {
  if (attempt.value && !submitting.value) {
    saveDraft(true)
    e.preventDefault()
    e.returnValue = '考试进行中，确定离开吗？'
  }
}

function visibilityHandler() {
  if (document.visibilityState === 'hidden') saveDraft(true)
}

onBeforeRouteLeave(async () => {
  if (submitting.value || !attempt.value) return true
  const ok = await confirmDialog('考试进行中，离开后仍按服务器时间计时。确定离开吗？', {"title":"离开本次考试？","confirmText":"确认离开","cancelText":"继续作答","icon":"log-out","variant":"default"})
  if (!ok) return false
  leaving.value = true
  try {
    while (dirty || inFlightSave) {
      if (!await saveDraft(false)) return false
    }
    return true
  } finally {leaving.value=false}
})

onMounted(() => {
  void load()
  backupTimer = setInterval(() => { if (dirty && !saving.value && !submitting.value && !draftConflict.value) void saveDraft(true) },15000)
})

onBeforeUnmount(() => {
  disposed = true
  clearInterval(backupTimer)
  clearInterval(tickTimer)
  clearTimeout(saveTimer)
  window.removeEventListener('beforeunload', beforeUnloadHandler)
  document.removeEventListener('visibilitychange', visibilityHandler)
})

// 挂载离开提醒
window.addEventListener('beforeunload', beforeUnloadHandler)
document.addEventListener('visibilitychange', visibilityHandler)
</script>

<style scoped>
.exam-take :deep(textarea) {
  font-family: var(--kb-font-mono, ui-monospace, SFMono-Regular, Menlo, monospace);
}
</style>

<style scoped>
.exam-editable {min-width:0;padding:0;margin:0;border:0}
.draft-error {padding:var(--kb-space-4);background:var(--kb-muted);border-block-end:1px solid var(--kb-border);color:var(--kb-foreground)}
.draft-error p {margin-block:var(--kb-space-2)}
.draft-saving {padding:var(--kb-space-2) var(--kb-space-4);color:var(--kb-muted-foreground)}
</style>
