<template>
  <div class="ter-page">
    <div class="ter-top">
      <button type="button" class="ter-back" @click="goBack">
        <Icon name="arrow-left" :size="16" aria-hidden="true" />
        返回专业英语
      </button>
      <span class="ter-progress-text">已完成 {{ completedCount }} / {{ totalCount }}</span>
    </div>

    <header class="ter-head">
      <h1 class="ter-title">技术英语复习</h1>
      <p class="ter-subtitle">到期优先 · 四类练习共用同一份复习进度</p>
    </header>

    <div class="ter-modes" role="tablist" aria-label="练习模式">
      <button
        v-for="option in modeOptions"
        :key="option.value"
        type="button"
        role="tab"
        class="ter-mode"
        :class="{ 'is-active': mode === option.value }"
        :aria-selected="mode === option.value"
        :disabled="!isModeAvailable(option.value)"
        :title="modeTitle(option.value)"
        @click="setMode(option.value)"
      >{{ option.label }}</button>
    </div>

    <div
      class="ter-track"
      role="progressbar"
      :aria-valuenow="completedCount"
      :aria-valuemin="0"
      :aria-valuemax="totalCount"
      aria-label="本轮复习进度"
    >
      <span :style="{ width: progressPercent }"></span>
    </div>

    <StateView
      :status="state"
      :rows="5"
      type="line"
      error-icon="alert-circle"
      error-title="复习队列加载失败"
      :error-description="error"
      error-scope="只影响本轮复习，词汇大厅与其他学习页面不受影响"
      error-hint="检查网络后重试；如仍未恢复，可先返回词汇大厅"
      empty-icon="book-open"
      :empty-title="emptyTitle"
      :empty-description="emptyDescription"
      @retry="loadQueue"
    >
      <template #skeleton>
        <div class="ter-card ter-skeleton" aria-hidden="true">
          <span class="ter-skeleton-line w-30"></span>
          <span class="ter-skeleton-word"></span>
          <span class="ter-skeleton-line w-60"></span>
          <span class="ter-skeleton-line w-40"></span>
        </div>
      </template>
      <template #empty-action>
        <button type="button" class="te-action-btn te-action-btn-primary" @click="goBack">返回词汇大厅</button>
      </template>

      <section v-if="current" class="ter-card" aria-live="polite">
        <p class="ter-meta">
          <span>{{ domainLabel(current.domain) }}</span>
          <span>{{ termTypeLabel(current.termType) }}</span>
          <span>已复习 {{ current.reviewCount }} 次</span>
        </p>

        <!-- 模式一：英文 → 中文 -->
        <template v-if="mode === 'en-cn'">
          <p class="ter-prompt-word">{{ current.headword }}</p>
          <button
            v-if="headwordPron"
            type="button"
            class="ter-play"
            :disabled="speakingKey === 'headword'"
            @click="playHeadword"
          >
            <Icon name="volume-2" :size="16" aria-hidden="true" />
            {{ speakingKey === 'headword' ? '播放中…' : '播放发音' }}
          </button>
          <p class="ter-prompt-label">请回忆它的中文含义</p>
        </template>

        <!-- 模式二：中文 → 英文 -->
        <template v-else-if="mode === 'cn-en'">
          <p class="ter-prompt-cn">{{ current.meaningCn }}</p>
          <p class="ter-prompt-label">请回忆对应的英文术语，可以先写下来再核对</p>
          <input
            v-model="typedAnswer"
            class="kf-input ter-input"
            type="text"
            placeholder="可选：输入英文术语（仅供参考）"
            aria-label="输入你的英文答案（可选）"
            autocomplete="off"
            autocapitalize="off"
            autocorrect="off"
            spellcheck="false"
          />
        </template>

        <!-- 模式三：缩写 → 英文全称 -->
        <template v-else-if="mode === 'abbr-full'">
          <p class="ter-prompt-word">{{ current.headword }}</p>
          <button
            v-if="headwordPron"
            type="button"
            class="ter-play"
            :disabled="speakingKey === 'headword'"
            @click="playHeadword"
          >
            <Icon name="volume-2" :size="16" aria-hidden="true" />
            {{ speakingKey === 'headword' ? '播放中…' : '播放英文缩写' }}
          </button>
          <p class="ter-prompt-label">请回忆它的英文全称</p>
        </template>

        <!-- 模式四：听音 → 识词 -->
        <template v-else>
          <button
            type="button"
            class="ter-listen"
            :disabled="speakingKey === 'headword'"
            aria-label="播放发音"
            @click="playHeadword"
          >
            <Icon name="volume-2" :size="30" aria-hidden="true" />
          </button>
          <p class="ter-prompt-label">先听发音，再回忆对应的英文术语</p>
        </template>

        <p v-if="speechError" class="ter-speech-error" role="alert">
          {{ speechError }}
          <button v-if="mode === 'listening'" type="button" class="ter-inline-link" @click="setMode('en-cn')">
            切换到英文→中文
          </button>
        </p>

        <button v-if="!revealed" type="button" class="te-action-btn te-action-btn-primary ter-reveal" @click="reveal">
          显示答案
        </button>

        <div v-else class="ter-answer">
          <!-- 英文 → 中文：揭示中文含义 -->
          <template v-if="mode === 'en-cn'">
            <p class="ter-answer-cn">{{ current.meaningCn }}</p>
            <p v-if="current.definitionCn" class="ter-definition">{{ current.definitionCn }}</p>
            <p v-if="ipaText" class="ter-ipa">{{ ipaText }}<span class="ter-ipa-note">音标来自词库核验记录</span></p>
          </template>

          <!-- 中文 → 英文：揭示英文术语 -->
          <template v-else-if="mode === 'cn-en'">
            <p class="ter-answer-word">{{ current.headword }}</p>
            <p v-if="current.fullForm" class="ter-full-form">{{ current.fullForm }}</p>
            <p class="ter-answer-cn">{{ current.meaningCn }}</p>
            <p v-if="current.definitionCn" class="ter-definition">{{ current.definitionCn }}</p>
            <p v-if="spellingResult" class="ter-spell-check" :class="{ 'is-match': spellingResult.matches }">
              {{ spellingResult.matches ? '与你输入的拼写一致' : '与你输入的拼写不一致' }}
              <span class="ter-spell-note">拼写核对仅供参考，是否掌握以你的自评评分为准。</span>
            </p>
          </template>

          <!-- 缩写 → 英文全称：揭示全称、拆解与关联知识 -->
          <template v-else-if="mode === 'abbr-full'">
            <p class="ter-answer-word">{{ current.fullForm }}</p>
            <button
              v-if="fullFormPron"
              type="button"
              class="ter-play"
              :disabled="speakingKey === 'full-form'"
              @click="playFullForm"
            >
              <Icon name="volume-2" :size="16" aria-hidden="true" />
              {{ speakingKey === 'full-form' ? '播放中…' : '播放英文全称' }}
            </button>
            <p class="ter-answer-cn">{{ current.meaningCn }}</p>
            <p v-if="current.className" class="ter-field">
              <span class="ter-field-label">对应类名</span>
              <code class="ter-code">{{ current.className }}</code>
            </p>
            <p v-if="current.definitionCn" class="ter-definition">{{ current.definitionCn }}</p>
            <div v-if="currentDetail?.wordBreakdown?.length" class="ter-breakdown">
              <span class="ter-field-label">全称逐词拆解</span>
              <ul>
                <li v-for="(part, index) in currentDetail.wordBreakdown" :key="`${part.word}-${index}`">
                  <code class="ter-code">{{ part.word }}</code>
                  <span>{{ part.meaningCn }}</span>
                </li>
              </ul>
            </div>
            <div v-if="currentDetail?.conceptLinks?.length" class="ter-links">
              <span class="ter-field-label">关联知识</span>
              <ul>
                <li v-for="(link, index) in currentDetail.conceptLinks" :key="`${link.targetType}-${index}`">
                  <router-link v-if="link.route" :to="link.route" class="ter-inline-link">{{ link.label }}</router-link>
                  <span v-else>{{ link.label }}（当前不可访问）</span>
                </li>
              </ul>
            </div>
            <p v-if="detailLoading" class="ter-detail-loading">正在补充术语拆解…</p>
          </template>

          <!-- 听音 → 识词：揭示单词 -->
          <template v-else>
            <p class="ter-answer-word">{{ current.headword }}</p>
            <button
              v-if="headwordPron"
              type="button"
              class="ter-play"
              :disabled="speakingKey === 'headword'"
              @click="playHeadword"
            >
              <Icon name="volume-2" :size="16" aria-hidden="true" />
              再听一次
            </button>
            <p class="ter-answer-cn">{{ current.meaningCn }}</p>
            <p v-if="ipaText" class="ter-ipa">{{ ipaText }}<span class="ter-ipa-note">音标来自词库核验记录</span></p>
            <p v-if="current.definitionCn" class="ter-definition">{{ current.definitionCn }}</p>
          </template>
        </div>

        <div v-if="revealed" class="ter-rating">
          <p class="ter-rating-hint">按现有 FSRS 复习规则自评</p>
          <div class="ter-rating-buttons">
            <button
              v-for="item in ratingOptions"
              :key="item.value"
              type="button"
              class="ter-rating-btn"
              :disabled="ratingPending"
              :aria-label="`${item.label}（${item.value}）`"
              @click="submitRating(item.value)"
            >
              <span>{{ ratingPending ? '保存中…' : item.label }}</span>
              <small>{{ intervals[item.value] || '—' }}</small>
            </button>
          </div>
          <p class="ter-save-hint">只有这里的正式自评会写入复习记录；翻看答案与播放发音不计分。</p>
        </div>
      </section>

      <section v-else-if="!loading && !error" class="ter-card ter-done">
        <span class="ter-done-icon"><Icon name="check-circle" :size="28" aria-hidden="true" /></span>
        <h2 class="ter-done-title">本轮复习已完成</h2>
        <p class="ter-done-text">
          已完成 {{ completedCount }} 个词条的正式评分，复习记录已写入服务端。
          到期时间由现有 FSRS 调度器安排。
        </p>
        <div class="ter-actions">
          <button type="button" class="te-action-btn te-action-btn-primary" @click="goBack">返回专业英语</button>
          <button type="button" class="te-action-btn" @click="loadQueue(true)">重新加载今日队列</button>
        </div>
      </section>
    </StateView>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import StateView from '@/components/ui/StateView.vue'
import {
  techEnglishApi,
  type PronunciationVO,
  type ReviewItemVO,
  type ReviewQueueVO,
  type ReviewReceiptVO,
  type TermDetail,
} from '@/api/techEnglish'
import { learningApi } from '@/api/learning'
import type { FlashcardSchedulePreview } from '@/api/types'
import { useTermSpeech } from '@/composables/useTermSpeech'
import { getApiError, notify } from '@/utils/toast'
import { domainLabel, termTypeLabel } from '@/components/tech-english/term-labels'

type ReviewMode = 'en-cn' | 'cn-en' | 'abbr-full' | 'listening'

interface SubmissionRecord {
  termId: number
  rating: number
  dueAt: string | null
  state: string
  reviewedAt: string
}

interface StoredSession {
  sessionId: string
  mode: ReviewMode
  createdAt: number
  submissions: SubmissionRecord[]
}

const QUEUE_LIMIT = 20
const SESSION_STORAGE_KEY = 'knowflow:tech-english:review-session'
const SESSION_MAX_AGE = 12 * 60 * 60 * 1000
const REQUEST_ID_RE = /^[A-Za-z0-9_-]{12,80}$/

const router = useRouter()
const { speakingKey, speechError, play, stop } = useTermSpeech()

const loading = ref(true)
const error = ref('')
const queue = ref<ReviewQueueVO | null>(null)
const revealed = ref(false)
const ratingPending = ref(false)
const typedAnswer = ref('')
const schedule = ref<FlashcardSchedulePreview | null>(null)
const detailById = ref<Record<number, TermDetail>>({})
const detailLoading = ref(false)

const sessionId = ref('')
const mode = ref<ReviewMode>('en-cn')
const submissions = ref<SubmissionRecord[]>([])
let sessionCreatedAt = Date.now()
let pendingRequest: { termId: number; rating: number; requestId: string } | null = null

const modeOptions: { value: ReviewMode; label: string }[] = [
  { value: 'en-cn', label: '英文→中文' },
  { value: 'cn-en', label: '中文→英文' },
  { value: 'abbr-full', label: '缩写→全称' },
  { value: 'listening', label: '听音识词' },
]

const ratingOptions = [
  { value: 1, label: '重来' },
  { value: 2, label: '困难' },
  { value: 3, label: '记得' },
  { value: 4, label: '简单' },
] as const

/* ============================ 会话恢复 ============================ */

function newToken(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID().replace(/-/g, '')
  }
  return `${Date.now().toString(36)}${Math.random().toString(36).slice(2)}`
    .replace(/[^A-Za-z0-9_-]/g, '')
    .slice(0, 32)
    .padEnd(16, '0')
}

function isReviewMode(value: unknown): value is ReviewMode {
  return value === 'en-cn' || value === 'cn-en' || value === 'abbr-full' || value === 'listening'
}

function restoreSession() {
  sessionId.value = newToken()
  mode.value = 'en-cn'
  submissions.value = []
  sessionCreatedAt = Date.now()
  try {
    const raw = sessionStorage.getItem(SESSION_STORAGE_KEY)
    if (!raw) return
    const parsed = JSON.parse(raw) as Partial<StoredSession>
    if (!parsed || typeof parsed.sessionId !== 'string' || !REQUEST_ID_RE.test(parsed.sessionId)) return
    if (!parsed.createdAt || Date.now() - parsed.createdAt > SESSION_MAX_AGE) return
    const restored = Array.isArray(parsed.submissions)
      ? parsed.submissions.filter(
          (item): item is SubmissionRecord =>
            !!item && typeof item.termId === 'number' && typeof item.rating === 'number',
        )
      : []
    sessionId.value = parsed.sessionId
    mode.value = isReviewMode(parsed.mode) ? parsed.mode : 'en-cn'
    submissions.value = restored
    sessionCreatedAt = parsed.createdAt
  } catch {
    // 会话进度仅用于当前标签页恢复；读取失败不影响服务端复习记录
  }
}

function persistSession() {
  try {
    const payload: StoredSession = {
      sessionId: sessionId.value,
      mode: mode.value,
      createdAt: sessionCreatedAt,
      submissions: submissions.value,
    }
    sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(payload))
  } catch {
    // 无法写入 sessionStorage 时仍可完成复习，仅失去刷新恢复能力
  }
}

function resetSession() {
  sessionId.value = newToken()
  mode.value = 'en-cn'
  submissions.value = []
  sessionCreatedAt = Date.now()
  pendingRequest = null
  persistSession()
}

/* ============================ 队列与进度 ============================ */

const submittedIds = computed(() => new Set(submissions.value.map((item) => item.termId)))
const remaining = computed(() => (queue.value?.items ?? []).filter((item) => !submittedIds.value.has(item.termId)))
const current = computed<ReviewItemVO | null>(() => remaining.value[0] ?? null)
const completedCount = computed(() => submissions.value.length)
const totalCount = computed(() => completedCount.value + remaining.value.length)
const progressPercent = computed(() =>
  totalCount.value ? `${Math.round((completedCount.value / totalCount.value) * 100)}%` : '0%',
)

const state = computed(() => {
  if (error.value) return 'error' as const
  if (loading.value) return 'loading' as const
  return totalCount.value > 0 ? ('ready' as const) : ('empty' as const)
})

const emptyTitle = computed(() =>
  queue.value?.hasReviewCards ? '今天没有待复习的词条' : '还没有开始复习',
)

const emptyDescription = computed(() =>
  queue.value?.hasReviewCards
    ? '到期任务完成后，FSRS 会在合适的时间再次安排；你也可以继续在大厅添加新词条。'
    : '先在词汇大厅挑选术语并点击「加入复习」，这里就会按到期时间生成练习队列。',
)

async function loadQueue(startNew = false) {
  if (startNew) {
    resetSession()
    revealed.value = false
  }
  loading.value = true
  error.value = ''
  try {
    queue.value = await techEnglishApi.getReviewQueue(QUEUE_LIMIT)
    if (!current.value) {
      revealed.value = false
      typedAnswer.value = ''
      stop()
    }
  } catch (e: unknown) {
    queue.value = null
    error.value = getApiError(e, '复习队列加载失败')
  } finally {
    loading.value = false
  }
}

/* ============================ 模式切换 ============================ */

function isModeAvailableFor(item: ReviewItemVO | null, value: ReviewMode): boolean {
  if (!item) return value === 'en-cn' || value === 'cn-en'
  if (value === 'abbr-full') return item.acronymMode && !!item.fullForm
  if (value === 'listening') return item.listeningMode
  return true
}

function isModeAvailable(value: ReviewMode): boolean {
  return isModeAvailableFor(current.value, value)
}

function modeTitle(value: ReviewMode): string {
  if (isModeAvailable(value)) return ''
  if (value === 'abbr-full') return '该词条没有可靠的英文全称，已跳过缩写练习'
  if (value === 'listening') return '该词条暂无已核验且可播放的发音，已跳过听音练习'
  return ''
}

function setMode(value: ReviewMode) {
  if (!isModeAvailable(value) || mode.value === value) return
  mode.value = value
  revealed.value = false
  typedAnswer.value = ''
  speechError.value = ''
  stop()
  persistSession()
  void loadCurrentDetailIfNeeded()
}

/* ============================ 答案揭晓 ============================ */

const ipaText = computed(() => {
  const item = current.value
  if (!item) return ''
  const parts: string[] = []
  if (item.ipaUs) parts.push(`美 ${item.ipaUs}`)
  if (item.ipaUk) parts.push(`英 ${item.ipaUk}`)
  return parts.join(' · ')
})

const spellingResult = computed(() => {
  if (mode.value !== 'cn-en' || !revealed.value || !current.value) return null
  const typed = typedAnswer.value.trim()
  if (!typed) return null
  const normalize = (value: string) =>
    value.trim().toLowerCase().replace(/[-_]+/g, ' ').replace(/\s+/g, ' ')
  const targets = [current.value.headword, current.value.fullForm]
    .filter((value): value is string => !!value && value.trim().length > 0)
    .map(normalize)
  return { matches: targets.includes(normalize(typed)) }
})

async function reveal() {
  if (!current.value) return
  revealed.value = true
  await loadCurrentDetailIfNeeded()
}

/* ============================ 发音 ============================ */

function pickPronunciation(item: ReviewItemVO, variants: string[]): PronunciationVO | null {
  for (const variant of variants) {
    const hit = (item.pronunciations || []).find(
      (pron) => pron.variant === variant && pron.verificationStatus === 'VERIFIED',
    )
    if (hit) return hit
  }
  return null
}

const headwordPron = computed(() => {
  const item = current.value
  if (!item) return null
  return item.termType === 'ACRONYM'
    ? pickPronunciation(item, ['ABBR', 'WORD_US', 'WORD_UK'])
    : pickPronunciation(item, ['WORD_US', 'WORD_UK'])
})

const fullFormPron = computed(() => {
  const item = current.value
  return item ? pickPronunciation(item, ['FULL_FORM']) : null
})

function playHeadword() {
  if (headwordPron.value) void play(headwordPron.value, 'headword')
}

function playFullForm() {
  if (fullFormPron.value) void play(fullFormPron.value, 'full-form')
}

/* ============================ 排程预览 ============================ */

let scheduleSeq = 0

async function loadSchedule() {
  schedule.value = null
  const cardId = current.value?.cardId
  if (!cardId) return
  const seq = ++scheduleSeq
  try {
    const data = await learningApi.flashcardSchedule(cardId)
    if (seq === scheduleSeq) schedule.value = data
  } catch {
    // 排程预览仅用于展示间隔提示，失败不影响正式评分
  }
}

const intervals = computed<Record<number, string>>(() => {
  const map: Record<number, string> = {}
  for (const preview of schedule.value?.previews ?? []) {
    map[preview.rating] = intervalLabel(preview.intervalSeconds)
  }
  return map
})

function intervalLabel(seconds?: number): string {
  if (!seconds || seconds <= 0) return ''
  if (seconds < 60) return `${Math.round(seconds)} 秒`
  if (seconds < 3600) return `${Math.round(seconds / 60)} 分钟`
  if (seconds < 86400) return `${Math.round(seconds / 3600)} 小时`
  return `${Math.round(seconds / 86400)} 天`
}

/* ============================ 术语补充信息 ============================ */

const currentDetail = computed(() => {
  const item = current.value
  return item ? detailById.value[item.termId] ?? null : null
})

async function loadCurrentDetailIfNeeded() {
  const item = current.value
  if (!item || mode.value !== 'abbr-full' || detailById.value[item.termId]) return
  detailLoading.value = true
  try {
    const data = await techEnglishApi.getTerm(item.termId)
    detailById.value = { ...detailById.value, [item.termId]: data }
  } catch {
    // 术语拆解与关联知识是补充信息，加载失败不阻断复习
  } finally {
    detailLoading.value = false
  }
}

/* ============================ 评分提交 ============================ */

function recordSubmission(item: ReviewItemVO, rating: number, receipt: ReviewReceiptVO) {
  submissions.value = [
    ...submissions.value.filter((entry) => entry.termId !== item.termId),
    {
      termId: item.termId,
      rating,
      dueAt: receipt.dueAt ?? null,
      state: receipt.state,
      reviewedAt: new Date().toISOString(),
    },
  ]
  persistSession()
}

async function submitRating(rating: number) {
  const item = current.value
  if (!item || !revealed.value || ratingPending.value) return
  if (pendingRequest && (pendingRequest.termId !== item.termId || pendingRequest.rating !== rating)) {
    pendingRequest = null
  }
  if (!pendingRequest) {
    pendingRequest = { termId: item.termId, rating, requestId: newToken() }
  }
  ratingPending.value = true
  try {
    if (!item.cardId) {
      const added = await techEnglishApi.addToReview(item.termId)
      item.cardId = added.cardId
      if (!added.created) notify('该词条已在复习计划中，继续记录本次评分', 'info')
    }
    const receipt = await techEnglishApi.submitReview(item.termId, {
      rating,
      requestId: pendingRequest.requestId,
      sessionId: sessionId.value,
    })
    recordSubmission(item, rating, receipt)
    pendingRequest = null
    revealed.value = false
    typedAnswer.value = ''
    stop()

    const next = remaining.value[0]
    if (next && !isModeAvailableFor(next, mode.value)) {
      mode.value = 'en-cn'
      notify('下一词条不支持当前练习模式，已切换为英文→中文', 'info')
    }
    notify(
      receipt.replayed
        ? '该次评分已记录过，未重复记分'
        : `已记录评分：${ratingOptions.find((option) => option.value === rating)?.label ?? rating}`,
      'success',
    )
  } catch (e: unknown) {
    notify(getApiError(e, '评分提交失败'), 'error')
  } finally {
    ratingPending.value = false
  }
}

/* ============================ 路由与监听 ============================ */

function goBack() {
  void router.push({ name: 'TechEnglishHome' })
}

watch(
  current,
  (item) => {
    revealed.value = false
    typedAnswer.value = ''
    speechError.value = ''
    stop()
    if (item && !isModeAvailableFor(item, mode.value)) {
      mode.value = 'en-cn'
    }
    void loadSchedule()
  },
  { immediate: true },
)

onMounted(() => {
  restoreSession()
  void loadQueue()
})
</script>

<style scoped>
.ter-page {
  display: grid;
  gap: var(--kf-space-4);
  width: 100%;
  max-width: 820px;
  margin: 0 auto;
  min-width: 0;
}
.ter-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kf-space-3);
  flex-wrap: wrap;
}
.ter-back {
  display: inline-flex;
  align-items: center;
  gap: var(--kf-space-1);
  padding: 0;
  border: 0;
  background: none;
  color: var(--kf-muted-foreground);
  font-size: var(--kf-fs-body-sm);
  cursor: pointer;
}
.ter-back:hover {
  color: var(--kf-primary);
}
.ter-back:focus-visible {
  outline: var(--kf-field-focus-width) solid var(--kf-ring);
  outline-offset: var(--kf-space-1);
}
.ter-progress-text {
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.ter-head {
  display: grid;
  gap: var(--kf-space-1);
}
.ter-title {
  margin: 0;
  font-size: clamp(26px, 4vw, 32px);
  line-height: 1.25;
  color: var(--kf-foreground);
}
.ter-subtitle {
  margin: 0;
  font-size: var(--kf-fs-body-sm);
  color: var(--kf-muted-foreground);
}
.ter-modes {
  display: flex;
  gap: var(--kf-space-2);
  flex-wrap: wrap;
}
.ter-mode {
  padding: var(--kf-space-2) var(--kf-space-3);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  background: var(--kf-card);
  color: var(--kf-muted-foreground);
  font-size: var(--kf-fs-body-sm);
  cursor: pointer;
}
.ter-mode:hover:not(:disabled) {
  border-color: var(--kf-primary);
  color: var(--kf-foreground);
}
.ter-mode.is-active {
  border-color: var(--kf-primary);
  background: var(--kf-primary-soft);
  color: var(--kf-primary);
}
.ter-mode:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.ter-mode:focus-visible {
  outline: var(--kf-field-focus-width) solid var(--kf-ring);
  outline-offset: var(--kf-space-1);
}
.ter-track {
  height: 6px;
  border-radius: 999px;
  background: var(--kf-muted);
  overflow: hidden;
}
.ter-track > span {
  display: block;
  height: 100%;
  background: var(--kf-primary);
  transition: width 0.25s ease;
}
.ter-card {
  display: grid;
  gap: var(--kf-space-4);
  padding: clamp(20px, 4vw, 36px);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-lg);
  background: var(--kf-card);
  text-align: center;
}
.ter-meta {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--kf-space-2);
  flex-wrap: wrap;
  margin: 0;
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.ter-prompt-word {
  margin: 0;
  font-family: var(--font-mono);
  font-size: clamp(28px, 6vw, 40px);
  line-height: 1.2;
  font-weight: 600;
  color: var(--kf-foreground);
  overflow-wrap: anywhere;
}
.ter-prompt-cn {
  margin: 0;
  font-size: clamp(24px, 5vw, 34px);
  line-height: 1.35;
  font-weight: 600;
  color: var(--kf-foreground);
  overflow-wrap: anywhere;
}
.ter-prompt-label {
  margin: 0;
  font-size: var(--kf-fs-body-sm);
  color: var(--kf-muted-foreground);
}
.ter-input {
  width: 100%;
  max-width: 420px;
  justify-self: center;
  text-align: left;
}
.ter-play {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--kf-space-1);
  justify-self: center;
  padding: var(--kf-space-2) var(--kf-space-3);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  background: var(--kf-background);
  color: var(--kf-primary);
  font-size: var(--kf-fs-body-sm);
  cursor: pointer;
}
.ter-play:hover:not(:disabled) {
  border-color: var(--kf-primary);
}
.ter-play:disabled {
  opacity: 0.6;
  cursor: progress;
}
.ter-listen {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  justify-self: center;
  width: 88px;
  height: 88px;
  border: 1px solid var(--kf-primary);
  border-radius: 50%;
  background: var(--kf-primary-soft);
  color: var(--kf-primary);
  cursor: pointer;
}
.ter-listen:hover:not(:disabled) {
  background: var(--kf-primary);
  color: var(--kf-card);
}
.ter-listen:disabled {
  opacity: 0.6;
  cursor: progress;
}
.ter-speech-error {
  margin: 0;
  font-size: var(--kf-fs-caption);
  color: var(--kf-state-error);
}
.ter-inline-link {
  padding: 0;
  border: 0;
  background: none;
  color: var(--kf-primary);
  font-size: inherit;
  cursor: pointer;
  text-decoration: underline;
}
.ter-reveal {
  justify-self: center;
  min-width: 160px;
}
.ter-answer {
  display: grid;
  gap: var(--kf-space-3);
  padding-top: var(--kf-space-4);
  border-top: 1px solid var(--kf-border);
  text-align: left;
}
.ter-answer-word {
  margin: 0;
  font-family: var(--font-mono);
  font-size: clamp(24px, 5vw, 32px);
  line-height: 1.25;
  font-weight: 600;
  color: var(--kf-foreground);
  overflow-wrap: anywhere;
}
.ter-full-form {
  margin: 0;
  font-family: var(--font-mono);
  font-size: var(--kf-fs-body-md);
  color: var(--kf-foreground);
  overflow-wrap: anywhere;
}
.ter-answer-cn {
  margin: 0;
  font-size: var(--kf-fs-body-lg);
  color: var(--kf-foreground);
}
.ter-definition {
  margin: 0;
  font-size: var(--kf-fs-body-md);
  line-height: var(--kf-lh-body-lg);
  color: var(--kf-muted-foreground);
  overflow-wrap: anywhere;
}
.ter-ipa {
  display: flex;
  align-items: baseline;
  gap: var(--kf-space-2);
  flex-wrap: wrap;
  margin: 0;
  font-family: var(--font-mono);
  font-size: var(--kf-fs-body-sm);
  color: var(--kf-foreground);
}
.ter-ipa-note {
  font-family: inherit;
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.ter-spell-check {
  margin: 0;
  padding: var(--kf-space-2) var(--kf-space-3);
  border-radius: var(--kf-radius-xs);
  background: var(--kf-state-warning-soft, var(--kf-muted));
  color: var(--kf-state-warning);
  font-size: var(--kf-fs-caption);
}
.ter-spell-check.is-match {
  background: var(--kf-state-success-soft, var(--kf-muted));
  color: var(--kf-state-success);
}
.ter-spell-note {
  display: block;
  margin-top: 2px;
  color: var(--kf-muted-foreground);
}
.ter-field {
  display: flex;
  align-items: baseline;
  gap: var(--kf-space-3);
  flex-wrap: wrap;
  margin: 0;
}
.ter-field-label {
  flex: none;
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.ter-code {
  font-family: var(--font-mono);
  font-size: var(--kf-fs-body-sm);
  overflow-wrap: anywhere;
}
.ter-breakdown,
.ter-links {
  display: grid;
  gap: var(--kf-space-2);
}
.ter-breakdown ul,
.ter-links ul {
  display: grid;
  gap: var(--kf-space-1);
  margin: 0;
  padding: 0;
  list-style: none;
}
.ter-breakdown li,
.ter-links li {
  display: flex;
  align-items: baseline;
  gap: var(--kf-space-2);
  flex-wrap: wrap;
  font-size: var(--kf-fs-body-sm);
}
.ter-detail-loading {
  margin: 0;
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.ter-rating {
  display: grid;
  gap: var(--kf-space-3);
  padding-top: var(--kf-space-4);
  border-top: 1px solid var(--kf-border);
}
.ter-rating-hint {
  margin: 0;
  font-size: var(--kf-fs-body-sm);
  color: var(--kf-muted-foreground);
}
.ter-rating-buttons {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--kf-space-2);
}
.ter-rating-btn {
  display: grid;
  gap: 2px;
  padding: var(--kf-space-2) var(--kf-space-1);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  background: var(--kf-card);
  color: var(--kf-foreground);
  font-size: var(--kf-fs-body-sm);
  cursor: pointer;
}
.ter-rating-btn:hover:not(:disabled) {
  border-color: var(--kf-primary);
}
.ter-rating-btn:disabled {
  opacity: 0.6;
  cursor: progress;
}
.ter-rating-btn small {
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.ter-rating-btn:nth-child(1) { color: var(--kf-state-error); }
.ter-rating-btn:nth-child(2) { color: var(--kf-state-warning); }
.ter-rating-btn:nth-child(3) { color: var(--kf-state-success); }
.ter-rating-btn:nth-child(4) { color: var(--kf-primary); }
.ter-save-hint {
  margin: 0;
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.ter-done {
  justify-items: center;
  gap: var(--kf-space-2);
  text-align: center;
}
.ter-done-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: var(--kf-state-success-soft, var(--kf-muted));
  color: var(--kf-state-success);
}
.ter-done-title {
  margin: 0;
  font-size: var(--kf-fs-h3, var(--kf-fs-body-lg));
  color: var(--kf-foreground);
}
.ter-done-text {
  max-width: 480px;
  margin: 0;
  font-size: var(--kf-fs-body-sm);
  line-height: var(--kf-lh-body-md);
  color: var(--kf-muted-foreground);
}
.ter-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--kf-space-2);
  flex-wrap: wrap;
  margin-top: var(--kf-space-2);
}
.ter-skeleton {
  gap: var(--kf-space-3);
}
.ter-skeleton-line,
.ter-skeleton-word {
  display: block;
  height: 16px;
  border-radius: var(--kf-radius-xs);
  background: var(--kf-muted);
}
.ter-skeleton-word {
  height: 44px;
  width: 50%;
  margin: 0 auto;
}
.ter-skeleton-line.w-30 { width: 30%; margin: 0 auto; }
.ter-skeleton-line.w-60 { width: 60%; margin: 0 auto; }
.ter-skeleton-line.w-40 { width: 40%; margin: 0 auto; }
@media (max-width: 560px) {
  .ter-rating-buttons {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .ter-card {
    padding: var(--kf-space-4);
  }
}
</style>
