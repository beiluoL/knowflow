<template>
  <div class="recall-practice" :style="{ '--mc': 'var(--kf-state-warning)' }">
    <header class="recall-practice-top">
      <button type="button" class="recall-back" data-test="recall-back" @click="requestBack">
        <Icon name="arrow-left" :size="16" aria-hidden="true" />
        返回会话
      </button>
      <span v-if="session && !loading" class="recall-round-counter">
        第 {{ displayRound }} / 3 轮
      </span>
      <div v-if="session && !loading" class="recall-top-actions">
        <span class="recall-status" :class="`is-${statusTone}`">{{ statusLabel }}</span>
        <button type="button" class="recall-top-btn" data-test="recall-edit" @click="editOpen = true">
          <Icon name="pencil" :size="14" aria-hidden="true" />
          编辑会话
        </button>
      </div>
    </header>

    <div v-if="loading" class="recall-state recall-state-loading" role="status">
      <div class="recall-skel-line" style="width: 220px; height: 20px;"></div>
      <div class="recall-skel-line" style="width: 420px; max-width: 100%; height: 14px; margin-top: 14px;"></div>
      <div class="recall-skel-line" style="width: 100%; height: 260px; margin-top: 24px;"></div>
    </div>

    <div v-else-if="error" class="recall-state" role="alert">
      <div class="recall-state-icon"><Icon name="alert-circle" :size="36" /></div>
      <h1 class="recall-state-title">无法打开默写会话</h1>
      <p class="recall-state-desc">{{ error }}</p>
      <div class="recall-state-actions">
        <button type="button" class="recall-btn recall-btn-ghost" @click="loadSession(true)">
          <Icon name="refresh-cw" :size="14" aria-hidden="true" /> 重试
        </button>
        <button type="button" class="recall-btn recall-btn-ghost" @click="requestBack">返回列表</button>
      </div>
    </div>

    <template v-else-if="session">
      <section class="recall-practice-head">
        <div class="recall-practice-heading">
          <span class="recall-eyebrow">
            <Icon :name="session.sourceType === 'NOTE' ? 'book-open' : 'file-text'" :size="12" aria-hidden="true" />
            {{ session.sourceLabel || '粘贴原文' }}
          </span>
          <h1 class="recall-practice-title">{{ session.title || '未命名默写会话' }}</h1>
          <p class="recall-practice-subtitle">
            {{ writingRound ? currentInstruction : '三轮闭卷默写：即时复述 → 补漏强化 → 间隔复测。' }}
          </p>
        </div>
        <div class="recall-head-score" v-if="completed">
          <span class="recall-head-score-value">{{ finalCoverage }}</span>
          <span class="recall-head-score-label">第三轮文本覆盖度</span>
        </div>
      </section>

      <p v-if="archived" class="recall-archive-note">
        <Icon name="archive" :size="14" aria-hidden="true" />
        该会话已归档，仍可查看历史记录与继续完成未完成轮次。
      </p>

      <nav class="recall-round-progress" aria-label="三轮默写进度">
        <div
          v-for="step in roundSteps"
          :key="step.round"
          class="recall-round-step"
          :class="stepState(step.round)"
        >
          <span class="recall-step-num">
            <Icon v-if="isSubmitted(step.round)" name="check" :size="13" aria-hidden="true" />
            <template v-else>{{ step.round }}</template>
          </span>
          <span class="recall-step-text">
            <strong>{{ step.name }}</strong>
            <small>{{ step.desc }}</small>
          </span>
        </div>
      </nav>

      <!-- ==================== 专注默写编辑区 ==================== -->
      <section v-if="writingRound" class="recall-writing-card" data-test="recall-writing-card">
        <header class="recall-writing-head">
          <div>
            <span class="recall-writing-kicker">当前任务 · 第 {{ writingRound }} 轮</span>
            <h2 class="recall-writing-title">{{ currentTaskName }}</h2>
          </div>
          <span v-if="writingRound === 1 && session.status === 'DRAFT'" class="recall-draft-tag">由草稿开始</span>
        </header>

        <p class="recall-writing-instruction">{{ currentInstruction }}</p>

        <div v-if="writingRound === 2" class="recall-clue-block">
          <span class="recall-clue-title">
            <Icon name="target" :size="13" aria-hidden="true" />
            第一轮确认的遗漏提示（仅显示关键词，不显示原文）
          </span>
          <div v-if="firstRoundGaps.length" class="recall-clue-list">
            <span v-for="gap in firstRoundGaps" :key="gap" class="recall-clue-chip">{{ gap }}</span>
          </div>
          <p v-else class="recall-clue-empty">
            第一轮尚未确认关键遗漏；本轮完全闭卷，请凭记忆补写主要内容。
          </p>
        </div>

        <p v-if="writingRound === 3" class="recall-clue-empty">
          第三轮不显示原文与前两轮内容，请独立完成完整复述。
        </p>

        <textarea
          ref="editorEl"
          v-model="editorText"
          data-test="recall-writing"
          class="recall-writing-input"
          rows="14"
          maxlength="20000"
          :placeholder="writingPlaceholder"
          aria-label="闭卷默写输入框"
        ></textarea>

        <div class="recall-writing-meta">
          <span>字数 <strong>{{ charCount }}</strong></span>
          <span>用时 <strong>{{ elapsedText }}</strong></span>
          <span class="recall-autosave-hint">
            <Icon name="save" :size="12" aria-hidden="true" />
            中途可保存草稿，提交后本轮不可修改
          </span>
          <span v-if="dirty" class="recall-dirty-hint">有未保存内容</span>
        </div>

        <footer class="recall-writing-foot">
          <button
            type="button"
            class="recall-btn recall-btn-ghost"
            data-test="recall-save-draft"
            :disabled="saving || submitting"
            @click="saveDraft"
          >
            <Icon name="save" :size="14" aria-hidden="true" />
            {{ saving ? '保存中…' : '保存草稿' }}
          </button>
          <button
            type="button"
            class="recall-btn recall-btn-primary"
            data-test="recall-submit"
            :disabled="saving || submitting || !editorText.trim()"
            @click="submitRound"
          >
            <Icon name="send" :size="14" aria-hidden="true" />
            {{ submitting ? '提交中…' : '提交本轮默写' }}
          </button>
        </footer>
      </section>

      <!-- ==================== 第一轮结果 → 第二轮准备 ==================== -->
      <section v-if="round2Gate" class="recall-gate" data-test="recall-round2-gate">
        <div class="recall-gate-head">
          <span class="recall-gate-icon"><Icon name="check" :size="18" aria-hidden="true" /></span>
          <div>
            <h2 class="recall-gate-title">第一轮已完成，准备补漏默写</h2>
            <p class="recall-gate-desc">
              下方是你的第一轮默写与原文的文本覆盖度对比。先确认真正的关键遗漏，再开始第二轮闭卷补写。
            </p>
          </div>
        </div>
        <div class="recall-gate-gaps">
          <span class="recall-clue-title">
            <Icon name="target" :size="13" aria-hidden="true" />
            已确认的关键遗漏（{{ firstRoundGaps.length }}）
          </span>
          <div v-if="firstRoundGaps.length" class="recall-clue-list">
            <span v-for="gap in firstRoundGaps" :key="gap" class="recall-clue-chip is-confirmed">{{ gap }}</span>
          </div>
          <p v-else class="recall-clue-empty">
            还没有确认关键遗漏。可在下方对比区勾选后再开始第二轮，也可以直接开始完全闭卷补写。
          </p>
        </div>
        <button type="button" class="recall-btn recall-btn-primary" data-test="recall-start-round2" @click="startRound2">
          <Icon name="play" :size="14" aria-hidden="true" />
          开始补漏默写
        </button>
      </section>

      <!-- ==================== 第三轮等待 / 到期 ==================== -->
      <section v-if="round3Gate" class="recall-gate recall-round3-gate" data-test="recall-round3-gate">
        <div v-if="!round3Ready" class="recall-round3-waiting" data-test="recall-round3-waiting">
          <span class="recall-gate-icon is-waiting"><Icon name="clock" :size="18" aria-hidden="true" /></span>
          <div class="recall-round3-waiting-text">
            <h2 class="recall-gate-title">等待延迟复测</h2>
            <p class="recall-gate-desc">
              第三轮会在第二轮完成后 60 分钟开放。现在先离开也没关系，到期后回到本页即可继续。
            </p>
            <div class="recall-countdown">
              <span class="recall-countdown-value">{{ countdownText }}</span>
              <span class="recall-countdown-label">剩余等待时间</span>
            </div>
            <p class="recall-round3-due">开放时间：{{ formatDateTime(session.round3DueTime) }}</p>
          </div>
        </div>

        <div v-else class="recall-round3-ready">
          <div class="recall-gate-head">
            <span class="recall-gate-icon is-ready"><Icon name="clock" :size="18" aria-hidden="true" /></span>
            <div>
              <h2 class="recall-gate-title">第三轮已到期，可以开始复测</h2>
              <p class="recall-gate-desc">
                请重新闭卷默写主要内容，系统不会展示原文或前两轮答案。未到服务端开放时间时提交会被拒绝。
              </p>
            </div>
          </div>
          <button type="button" class="recall-btn recall-btn-primary" data-test="recall-start-round3" @click="startRound3">
            <Icon name="play" :size="14" aria-hidden="true" />
            开始第三轮
          </button>
        </div>
      </section>

      <!-- ==================== 三轮完成结果 ==================== -->
      <section v-if="completed" class="recall-result" data-test="recall-result">
        <header class="recall-result-head">
          <div>
            <span class="recall-result-eyebrow">
              <Icon name="check" :size="12" aria-hidden="true" />
              三轮默写已完成
            </span>
            <h2 class="recall-result-title">三轮结果与遗漏复盘</h2>
            <p class="recall-result-desc">
              分数只表示原文关键词的文本覆盖度，不代表语义理解评分。真正薄弱点以你的手动确认和复盘为准。
            </p>
          </div>
          <span v-if="session.completedTime" class="recall-result-time">
            完成于 {{ formatDateTime(session.completedTime) }}
          </span>
        </header>

        <div class="recall-score-grid">
          <div class="recall-score-card">
            <span class="recall-score-label">第一轮 · 即时默写</span>
            <span class="recall-score-value">{{ scoreText(session.round1Score) }}</span>
            <span class="recall-score-unit">文本覆盖度</span>
          </div>
          <div class="recall-score-card">
            <span class="recall-score-label">第二轮 · 补漏默写</span>
            <span class="recall-score-value">{{ scoreText(session.round2Score) }}</span>
            <span class="recall-score-delta">{{ deltaText(session.round1Score, session.round2Score) }}</span>
          </div>
          <div class="recall-score-card is-final">
            <span class="recall-score-label">第三轮 · 延迟复测</span>
            <span class="recall-score-value">{{ scoreText(session.round3Score) }}</span>
            <span class="recall-score-delta">{{ deltaText(session.round2Score, session.round3Score) }}</span>
          </div>
        </div>

        <div class="recall-review-grid">
          <div class="recall-review-panel is-warning">
            <h3 class="recall-review-title">
              <Icon name="alert-circle" :size="14" aria-hidden="true" />
              持续遗忘知识（{{ persistentGaps.length }}）
            </h3>
            <div v-if="persistentGaps.length" class="recall-clue-list">
              <span v-for="gap in persistentGaps" :key="gap" class="recall-clue-chip is-warning">{{ gap }}</span>
            </div>
            <p v-else class="recall-review-empty">
              三轮文本比对未发现持续遗漏；仍建议按语义自行复核。
            </p>
          </div>

          <div class="recall-review-panel is-success">
            <h3 class="recall-review-title">
              <Icon name="trending-up" :size="14" aria-hidden="true" />
              正确补回知识（{{ recoveredGaps.length }}）
            </h3>
            <div v-if="recoveredGaps.length" class="recall-clue-list">
              <span v-for="gap in recoveredGaps" :key="gap" class="recall-clue-chip is-success">{{ gap }}</span>
            </div>
            <p v-else class="recall-review-empty">
              第一轮遗漏的内容尚未在第三轮重新出现，建议继续强化。
            </p>
          </div>
        </div>

        <div class="recall-review-panel">
          <h3 class="recall-review-title">
            <Icon name="target" :size="14" aria-hidden="true" />
            你确认的关键薄弱点（{{ confirmedGaps.length }}）
          </h3>
          <div v-if="confirmedGaps.length" class="recall-clue-list">
            <span v-for="gap in confirmedGaps" :key="gap" class="recall-clue-chip is-confirmed">{{ gap }}</span>
          </div>
          <p v-else class="recall-review-empty">尚未手动确认关键薄弱点。</p>
        </div>

        <div class="recall-suggestion">
          <h3 class="recall-review-title">
            <Icon name="route" :size="14" aria-hidden="true" />
            下一步复习建议
          </h3>
          <ul class="recall-suggestion-list">
            <li v-for="item in recommendations" :key="item">{{ item }}</li>
          </ul>
          <p class="recall-suggestion-note">
            系统不会自动修改已有的 SM-2 / 间隔重复记录；如需安排复习，请到「间隔重复」手动创建或调整。
          </p>
        </div>
      </section>

      <!-- ==================== 对比与遗漏复盘 ==================== -->
      <section v-if="!writingRound && submittedRounds.length" class="recall-comparison-stack">
        <div class="recall-comparison-head">
          <h2 class="recall-comparison-title">
            <Icon name="git-branch" :size="16" aria-hidden="true" />
            原文与默写对比
          </h2>
          <p class="recall-comparison-desc">
            对比区仅在提交后显示原文，正式默写过程中不会展示原文或完整答案。
          </p>
        </div>
        <div v-for="round in submittedRounds" :key="round" :data-test="`recall-comparison-${round}`">
          <RecallComparison
            :source-text="session.sourceText"
            :recall-text="roundText(round)"
            :round="round"
            :score="roundScore(round)"
            :confirmed="confirmedFor(round)"
            :editable="true"
            :saving="savingGapsRound === round"
            @save-gaps="(gaps) => saveGaps(round, gaps)"
          />
        </div>
      </section>
    </template>

    <RecallSessionDialog
      :open="editOpen"
      :session="session"
      @close="editOpen = false"
      @copy="copyCurrent"
      @saved="onEdited"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import RecallSessionDialog from '@/components/workbench/RecallSessionDialog.vue'
import RecallComparison from '@/components/workbench/RecallComparison.vue'
import { notify, confirmDialog, getApiError } from '@/utils/toast'
import {
  getRecallSession,
  saveRecallDraft,
  submitRecallRound,
  saveRecallGaps,
  copyRecallSession,
} from '@/api/workbench'
import type { WbRecallSession } from '@/api/types'
import './workbench-shared.css'

const route = useRoute()
const router = useRouter()

const session = ref<WbRecallSession | null>(null)
const loading = ref(true)
const error = ref('')
const editOpen = ref(false)
const editorText = ref('')
const editorEl = ref<HTMLTextAreaElement | null>(null)
const saving = ref(false)
const submitting = ref(false)
const savingGapsRound = ref<number | null>(null)
const round2Started = ref(false)
const round3Started = ref(false)
const now = ref(Date.now())
const serverOffset = ref(0)
const roundStartedAt = ref(Date.now())
const leavingByBack = ref(false)
const loadedOnce = ref(false)
let timer: number | undefined

const sessionId = computed(() => {
  const raw = route.params.id
  const value = Array.isArray(raw) ? raw[0] : raw
  const id = Number(value)
  return Number.isInteger(id) && id > 0 ? id : 0
})

const roundSteps = [
  { round: 1, name: '即时默写', desc: '不看原文，完整复述' },
  { round: 2, name: '补漏默写', desc: '针对遗漏再次闭卷' },
  { round: 3, name: '延迟复测', desc: '间隔一小时后复测' },
]

function parseTime(value?: string) {
  if (!value) return 0
  const normalized = value.includes('T') ? value : value.replace(' ', 'T')
  const time = new Date(normalized).getTime()
  return Number.isFinite(time) ? time : 0
}

function formatDateTime(value?: string) {
  const time = parseTime(value)
  if (!time) return '--'
  const d = new Date(time)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function roundText(round: number) {
  if (!session.value) return ''
  if (round === 1) return session.value.round1Text || ''
  if (round === 2) return session.value.round2Text || ''
  return session.value.round3Text || ''
}

function roundScore(round: number) {
  if (!session.value) return undefined
  if (round === 1) return session.value.round1Score
  if (round === 2) return session.value.round2Score
  return session.value.round3Score
}

function roundDraft(round: number) {
  if (!session.value) return ''
  if (round === 1) return session.value.round1Draft || ''
  if (round === 2) return session.value.round2Draft || ''
  return session.value.round3Draft || ''
}

function isSubmitted(round: number) {
  return !!roundText(round)
}

const archived = computed(() => (session.value?.archived ?? 0) === 1)
const completed = computed(() => !!session.value && (session.value.status === 'COMPLETED' || !!session.value.round3Text))

const currentRound = computed(() => {
  if (!session.value || completed.value) return 3
  if (!session.value.round1Text) return 1
  if (!session.value.round2Text) return 2
  return 3
})

const round3Due = computed(() => parseTime(session.value?.round3DueTime))
const serverNowMs = computed(() => now.value + serverOffset.value)
const remainingMs = computed(() => Math.max(0, round3Due.value - serverNowMs.value))
const round3Ready = computed(() => {
  if (!session.value || completed.value || !session.value.round2Text || session.value.round3Text) return false
  if (!session.value.round3DueTime) return true
  return serverNowMs.value >= round3Due.value
})

const writingRound = computed(() => {
  if (!session.value || completed.value) return 0
  if (currentRound.value === 1) return 1
  if (currentRound.value === 2) return round2Started.value ? 2 : 0
  return round3Started.value && round3Ready.value ? 3 : 0
})

const displayRound = computed(() => {
  if (completed.value) return 3
  return writingRound.value || currentRound.value
})

const round2Gate = computed(() => currentRound.value === 2 && !round2Started.value && !writingRound.value)
const round3Gate = computed(() => currentRound.value === 3 && !completed.value && !writingRound.value)

const currentTaskName = computed(() => {
  if (writingRound.value === 1) return '第一轮 · 即时默写'
  if (writingRound.value === 2) return '第二轮 · 补漏默写'
  return '第三轮 · 延迟复测'
})

const currentInstruction = computed(() => {
  if (writingRound.value === 1) return '不查看原文，用自己的话完整写出知识内容。'
  if (writingRound.value === 2) return '只根据第一轮确认的遗漏提示，再次闭卷补写；不要回看原文。'
  if (writingRound.value === 3) return '间隔 60 分钟后重新闭卷默写主要内容，检验长期记忆。'
  if (round2Gate.value) return '查看第一轮遗漏，然后开始第二轮闭卷补写。'
  if (round3Gate.value) return round3Ready.value ? '第三轮已到期，开始完整复测。' : '等待第三轮复测开放。'
  return '三轮闭卷默写：即时复述 → 补漏强化 → 间隔复测。'
})

const writingPlaceholder = computed(() => {
  if (writingRound.value === 2) return '根据遗漏提示，闭卷补写你记得的内容…'
  if (writingRound.value === 3) return '闭卷复述主要内容，不要依赖前两轮答案…'
  return '在这里开始闭卷默写…'
})

const charCount = computed(() => Array.from(editorText.value.replace(/\s/g, '')).length)
const elapsedSeconds = computed(() => {
  if (!writingRound.value) return 0
  return Math.max(0, Math.floor((now.value - roundStartedAt.value) / 1000))
})
const elapsedText = computed(() => {
  const total = elapsedSeconds.value
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(Math.floor(total / 60))}:${pad(total % 60)}`
})

const dirty = computed(() => !!writingRound.value && editorText.value !== roundDraft(writingRound.value))

function statusTone() {
  if (completed.value) return 'COMPLETED'
  if (writingRound.value === 2) return 'ROUND2'
  if (writingRound.value === 1) return 'ROUND1'
  if (round3Gate.value && round3Ready.value) return 'DUE'
  if (round3Gate.value) return 'WAITING'
  if (round2Gate.value) return 'ROUND2'
  return 'DRAFT'
}

const statusLabel = computed(() => {
  if (archived.value) return '已归档'
  if (session.value?.status === 'DRAFT' && writingRound.value === 1 && !session.value.round1Text) return '草稿 · 第一轮未提交'
  if (completed.value) return '已完成'
  if (writingRound.value === 1) return '第一轮进行中'
  if (writingRound.value === 2) return '第二轮进行中'
  if (round2Gate.value) return '第一轮已提交 · 待补漏'
  if (round3Gate.value && round3Ready.value) return '第三轮已到期'
  if (round3Gate.value) return '等待延迟复测'
  return '进行中'
})

const countdownText = computed(() => {
  const total = Math.ceil(remainingMs.value / 1000)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(Math.floor(total / 3600))}:${pad(Math.floor((total % 3600) / 60))}:${pad(total % 60)}`
})

function stepState(round: number) {
  if (isSubmitted(round)) return 'is-done'
  if (displayRound.value === round && !completed.value) return 'is-current'
  return 'is-locked'
}

/* ---------------------------- 草稿与提交 ---------------------------- */

function seedEditor() {
  editorText.value = writingRound.value ? roundDraft(writingRound.value) : ''
  roundStartedAt.value = Date.now()
  nextTick(() => {
    if (writingRound.value) editorEl.value?.focus()
  })
}

watch(writingRound, () => seedEditor())

function syncServerClock(value: WbRecallSession) {
  const serverTime = parseTime(value.serverNow)
  if (serverTime) serverOffset.value = serverTime - Date.now()
}

function applySession(value: WbRecallSession, initial: boolean) {
  session.value = value
  syncServerClock(value)
  if (initial) {
    round2Started.value = !value.round2Text && !!value.round2Draft
    round3Started.value = !value.round3Text && !!value.round3Draft && (!value.round3DueTime || Date.now() + serverOffset.value >= parseTime(value.round3DueTime))
  }
}

async function loadSession(seed = false) {
  if (!sessionId.value) {
    loading.value = false
    error.value = '会话 ID 无效'
    return
  }
  loading.value = true
  error.value = ''
  try {
    const value = await getRecallSession(sessionId.value)
    applySession(value, !loadedOnce.value)
    loadedOnce.value = true
    if (seed) seedEditor()
  } catch (e) {
    error.value = getApiError(e, '加载会话失败')
  } finally {
    loading.value = false
  }
}

async function saveDraft() {
  if (!session.value || !writingRound.value || saving.value || submitting.value) return
  saving.value = true
  try {
    const updated = await saveRecallDraft(sessionId.value, {
      round: writingRound.value,
      text: editorText.value,
    })
    session.value = updated
    syncServerClock(updated)
    notify('草稿已保存', 'success')
  } catch (e) {
    notify(getApiError(e, '保存草稿失败'), 'error')
  } finally {
    saving.value = false
  }
}

async function submitRound() {
  if (!session.value || !writingRound.value || submitting.value) return
  if (!editorText.value.trim()) {
    notify('默写内容不能为空', 'warning')
    return
  }
  const round = writingRound.value
  submitting.value = true
  try {
    const updated = await submitRecallRound(sessionId.value, { round, text: editorText.value })
    session.value = updated
    syncServerClock(updated)
    if (round === 1) round2Started.value = false
    if (round === 2) round3Started.value = false
    notify(round === 3 ? '第三轮已提交，三轮默写完成' : `第 ${round} 轮已提交`, 'success')
  } catch (e) {
    notify(getApiError(e, '提交失败，内容仍保留在编辑区'), 'error')
  } finally {
    submitting.value = false
  }
}

function startRound2() {
  round2Started.value = true
}

function startRound3() {
  if (!round3Ready.value) {
    notify('第三轮复测尚未到期', 'warning')
    return
  }
  round3Started.value = true
}

/* ---------------------------- 遗漏 ---------------------------- */

function parseGapJson(raw?: string): Record<string, string[]> {
  if (!raw) return {}
  try {
    const parsed: unknown = JSON.parse(raw)
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return {}
    const out: Record<string, string[]> = {}
    for (const [key, value] of Object.entries(parsed as Record<string, unknown>)) {
      if (Array.isArray(value)) out[key] = value.filter((item): item is string => typeof item === 'string')
    }
    return out
  } catch {
    return {}
  }
}

const gapMap = computed(() => parseGapJson(session.value?.gapJson))

function confirmedFor(round: number) {
  return gapMap.value[String(round)] || []
}

const firstRoundGaps = computed(() => confirmedFor(1))

const confirmedGaps = computed(() => {
  const seen = new Set<string>()
  const out: string[] = []
  Object.values(gapMap.value).forEach((items) => {
    items.forEach((item) => {
      const value = item.trim()
      if (value && !seen.has(value)) {
        seen.add(value)
        out.push(value)
      }
    })
  })
  return out
})

const TOKEN_RE = /[a-z]+|[\u4e00-\u9fa5]+/g

function tokenize(text: string): Set<string> {
  const tokens = new Set<string>()
  const re = new RegExp(TOKEN_RE.source, 'g')
  let match: RegExpExecArray | null
  while ((match = re.exec((text || '').toLowerCase())) !== null) {
    const token = match[0]
    if (/^[a-z]+$/.test(token)) {
      if (token.length > 1) tokens.add(token)
    } else if (token.length === 1) {
      tokens.add(token)
    } else {
      for (let i = 0; i + 2 <= token.length; i += 1) tokens.add(token.slice(i, i + 2))
    }
  }
  return tokens
}

function sourceTokens(text: string): string[] {
  const seen = new Set<string>()
  const out: string[] = []
  const re = new RegExp(TOKEN_RE.source, 'g')
  let match: RegExpExecArray | null
  while ((match = re.exec((text || '').toLowerCase())) !== null) {
    const token = match[0]
    const values = /^[a-z]+$/.test(token)
      ? (token.length > 1 ? [token] : [])
      : token.length === 1
        ? [token]
        : Array.from({ length: token.length - 1 }, (_, i) => token.slice(i, i + 2))
    for (const value of values) {
      if (!seen.has(value)) {
        seen.add(value)
        out.push(value)
      }
    }
  }
  return out
}

const persistentGaps = computed(() => {
  if (!session.value) return []
  const round3Tokens = tokenize(session.value.round3Text || '')
  if (confirmedGaps.value.length) {
    return confirmedGaps.value
      .filter((gap) => !round3Tokens.has(gap.toLowerCase()))
      .slice(0, 30)
  }
  const round1Tokens = tokenize(session.value.round1Text || '')
  return sourceTokens(session.value.sourceText || '')
    .filter((token) => !round1Tokens.has(token) && !round3Tokens.has(token))
    .slice(0, 30)
})

const recoveredGaps = computed(() => {
  if (!session.value) return []
  const round3Tokens = tokenize(session.value.round3Text || '')
  if (confirmedGaps.value.length) {
    return confirmedGaps.value.filter((gap) => round3Tokens.has(gap.toLowerCase())).slice(0, 30)
  }
  const round1Tokens = tokenize(session.value.round1Text || '')
  return sourceTokens(session.value.sourceText || '')
    .filter((token) => !round1Tokens.has(token) && round3Tokens.has(token))
    .slice(0, 30)
})

const submittedRounds = computed(() => [1, 2, 3].filter((round) => isSubmitted(round)))

const finalCoverage = computed(() => {
  const score = session.value?.round3Score
  return typeof score === 'number' ? score : 0
})

function scoreText(score?: number) {
  return typeof score === 'number' ? String(score) : '--'
}

function deltaText(previous?: number, current?: number) {
  if (typeof previous !== 'number' || typeof current !== 'number') return '暂无可比数据'
  const delta = current - previous
  if (delta === 0) return '与上一轮持平'
  return delta > 0 ? `较上一轮 +${delta} 个百分点` : `较上一轮 ${delta} 个百分点`
}

const recommendations = computed(() => {
  if (!session.value) return []
  const items: string[] = []
  if (persistentGaps.value.length) {
    items.push(`下一轮复习优先复述：${persistentGaps.value.slice(0, 8).join('、')}。`)
  }
  if (typeof session.value.round3Score === 'number' && typeof session.value.round2Score === 'number') {
    const delta = session.value.round3Score - session.value.round2Score
    if (delta < 0) {
      items.push(`第三轮文本覆盖度比第二轮低 ${Math.abs(delta)} 个百分点，建议 24 小时内完成一次短时默写。`)
    } else {
      items.push('第三轮文本覆盖度保持稳定，建议 1–3 天后再次闭卷复测。')
    }
  } else {
    items.push('建议 1–3 天后再次闭卷复测，检验长期记忆。')
  }
  items.push('如需加入间隔重复，请在「间隔重复」中手动创建卡片；系统不会自动改写已有 SM-2 记录。')
  return items
})

async function saveGaps(round: number, gaps: string[]) {
  if (!session.value) return
  savingGapsRound.value = round
  try {
    const updated = await saveRecallGaps(sessionId.value, { round, gaps })
    session.value = updated
    syncServerClock(updated)
    notify('关键遗漏已保存', 'success')
  } catch (e) {
    notify(getApiError(e, '保存关键遗漏失败'), 'error')
  } finally {
    savingGapsRound.value = null
  }
}

/* ---------------------------- 离开保护与编辑 ---------------------------- */

async function confirmDiscard() {
  return confirmDialog('当前默写尚未保存，确认离开？', {
    title: '放弃未保存的默写内容？',
    confirmText: '放弃并离开',
    cancelText: '继续默写',
    icon: 'log-out',
    variant: 'default',
  })
}

async function requestBack() {
  if (saving.value || submitting.value) return
  if (dirty.value) {
    const ok = await confirmDiscard()
    if (!ok) return
    leavingByBack.value = true
  }
  router.push('/workbench/recall')
}

onBeforeRouteLeave(async () => {
  if (leavingByBack.value || !dirty.value) return true
  return confirmDiscard()
})

function beforeUnload(event: BeforeUnloadEvent) {
  if (!dirty.value) return
  event.preventDefault()
  event.returnValue = ''
}

async function onEdited(payload: { id: number; started: boolean }) {
  editOpen.value = false
  if (payload.started) {
    notify('已进入第一轮默写', 'success')
  }
  await loadSession(false)
}

async function copyCurrent() {
  if (!session.value) return
  try {
    const id = await copyRecallSession(session.value.id)
    editOpen.value = false
    notify('已复制为新的草稿会话', 'success')
    router.push(`/workbench/recall/${id}`)
  } catch (e) {
    notify(getApiError(e, '复制失败'), 'error')
  }
}

onMounted(() => {
  loadSession(true)
  timer = window.setInterval(() => {
    now.value = Date.now()
  }, 1000)
  window.addEventListener('beforeunload', beforeUnload)
})

onBeforeUnmount(() => {
  if (timer) window.clearInterval(timer)
  window.removeEventListener('beforeunload', beforeUnload)
})
</script>

<style scoped>
.recall-practice {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-5);
  width: 100%;
  max-width: 1120px;
  margin: 0 auto;
  padding-bottom: var(--kf-space-6);
}
.recall-practice-top {
  display: flex;
  align-items: center;
  gap: var(--kf-space-3);
  flex-wrap: wrap;
  min-height: 34px;
}
.recall-back,
.recall-top-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  background: var(--kf-card);
  color: var(--kf-foreground);
  font-size: var(--kf-fs-caption);
  font-weight: 600;
  cursor: pointer;
}
.recall-back:hover,
.recall-top-btn:hover { background: var(--kf-background); }
.recall-back:focus-visible,
.recall-top-btn:focus-visible { outline: 2px solid var(--kf-ring); outline-offset: 2px; }
.recall-round-counter {
  font-family: var(--font-mono);
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.recall-top-actions {
  display: inline-flex;
  align-items: center;
  gap: var(--kf-space-2);
  margin-left: auto;
}
.recall-status {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  background: var(--kf-background);
  color: var(--kf-muted-foreground);
  white-space: nowrap;
}
.recall-status.is-ROUND1,
.recall-status.is-ROUND2 { background: var(--kf-primary-soft); color: var(--kf-primary); }
.recall-status.is-WAITING { background: color-mix(in srgb, var(--kf-state-warning) 14%, transparent); color: var(--kf-state-warning); }
.recall-status.is-DUE { background: color-mix(in srgb, var(--kf-state-error) 12%, transparent); color: var(--kf-state-error); }
.recall-status.is-COMPLETED { background: color-mix(in srgb, var(--kf-state-success) 12%, transparent); color: var(--kf-state-success); }
.recall-practice-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--kf-space-5);
  padding: var(--kf-space-5);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-lg);
  background: linear-gradient(135deg, color-mix(in srgb, var(--kf-state-warning) 7%, var(--kf-card)) 0%, var(--kf-card) 70%);
}
.recall-practice-heading { min-width: 0; }
.recall-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 9px;
  border-radius: 999px;
  background: var(--kf-card);
  border: 1px solid var(--kf-border);
  font-size: 11px;
  color: var(--kf-muted-foreground);
}
.recall-practice-title {
  margin: var(--kf-space-2) 0 0;
  font-family: var(--font-serif);
  font-size: var(--kf-fs-h1);
  font-weight: 800;
  line-height: 1.3;
  color: var(--kf-foreground);
  overflow-wrap: anywhere;
}
.recall-practice-subtitle {
  margin: var(--kf-space-2) 0 0;
  font-size: var(--kf-fs-body-sm);
  line-height: 1.7;
  color: var(--kf-muted-foreground);
  max-width: 680px;
}
.recall-head-score {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  flex: none;
}
.recall-head-score-value {
  font-family: var(--font-mono);
  font-size: var(--kf-fs-display);
  font-weight: 800;
  line-height: 1;
  color: var(--kf-primary);
}
.recall-head-score-label { font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
.recall-archive-note {
  display: flex;
  align-items: center;
  gap: var(--kf-space-2);
  margin: 0;
  padding: var(--kf-space-3) var(--kf-space-4);
  border-radius: var(--kf-radius-md);
  background: color-mix(in srgb, var(--kf-state-warning) 8%, transparent);
  color: var(--kf-state-warning);
  font-size: var(--kf-fs-caption);
}
.recall-round-progress {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--kf-space-2);
  padding: 6px;
  border-radius: var(--kf-radius-lg);
  border: 1px solid var(--kf-border);
  background: var(--kf-card);
}
.recall-round-step {
  display: flex;
  align-items: center;
  gap: var(--kf-space-2);
  padding: var(--kf-space-3);
  border-radius: var(--kf-radius-md);
  min-width: 0;
}
.recall-round-step.is-current { background: var(--kf-primary-soft); }
.recall-round-step.is-done { background: color-mix(in srgb, var(--kf-state-success) 8%, transparent); }
.recall-step-num {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
  width: 24px;
  height: 24px;
  border-radius: 999px;
  background: var(--kf-background);
  color: var(--kf-muted-foreground);
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 700;
}
.recall-round-step.is-current .recall-step-num { background: var(--kf-primary); color: var(--kf-primary-foreground); }
.recall-round-step.is-done .recall-step-num { background: var(--kf-state-success); color: #fff; }
.recall-step-text { display: flex; flex-direction: column; gap: 1px; min-width: 0; }
.recall-step-text strong { font-size: var(--kf-fs-caption); color: var(--kf-foreground); }
.recall-step-text small { font-size: 11px; color: var(--kf-muted-foreground); }
.recall-round-step.is-locked { opacity: 0.55; }
.recall-writing-card {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-3);
  padding: var(--kf-space-5);
  border-radius: var(--kf-radius-lg);
  border: 1px solid color-mix(in srgb, var(--kf-state-warning) 30%, var(--kf-border));
  background: var(--kf-card);
  box-shadow: var(--shadow-card);
}
.recall-writing-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--kf-space-3);
  flex-wrap: wrap;
}
.recall-writing-kicker {
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--kf-state-warning);
}
.recall-writing-title {
  margin: 4px 0 0;
  font-family: var(--font-serif);
  font-size: 20px;
  font-weight: 800;
  color: var(--kf-foreground);
}
.recall-writing-instruction {
  margin: 0;
  font-size: var(--kf-fs-body-sm);
  line-height: 1.7;
  color: var(--kf-muted-foreground);
}
.recall-draft-tag {
  padding: 3px 9px;
  border-radius: 999px;
  background: var(--kf-background);
  font-size: 11px;
  color: var(--kf-muted-foreground);
}
.recall-writing-input {
  width: 100%;
  box-sizing: border-box;
  min-height: 340px;
  max-height: 58vh;
  padding: var(--kf-space-4);
  border: none;
  border-radius: var(--kf-radius-md);
  background: var(--kf-background);
  color: var(--kf-foreground);
  font-family: var(--font-sans);
  font-size: var(--kf-fs-body-md);
  line-height: 1.9;
  resize: vertical;
  outline: none;
  transition: box-shadow 0.15s ease, background 0.15s ease;
}
.recall-writing-input:focus-visible {
  box-shadow: 0 0 0 2px var(--kf-ring);
  background: var(--kf-card);
}
.recall-writing-meta {
  display: flex;
  align-items: center;
  gap: var(--kf-space-4);
  flex-wrap: wrap;
  font-family: var(--font-mono);
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.recall-writing-meta strong { color: var(--kf-foreground); }
.recall-autosave-hint { display: inline-flex; align-items: center; gap: 4px; }
.recall-dirty-hint { color: var(--kf-state-warning); font-weight: 600; }
.recall-writing-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kf-space-3);
  padding-top: var(--kf-space-3);
  border-top: 1px solid var(--kf-border);
  flex-wrap: wrap;
}
.recall-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: var(--kf-space-2) var(--kf-space-4);
  border-radius: var(--kf-radius-md);
  border: 1px solid transparent;
  font-size: var(--kf-fs-body-sm);
  font-weight: 600;
  cursor: pointer;
}
.recall-btn-ghost {
  background: var(--kf-card);
  border-color: var(--kf-border);
  color: var(--kf-foreground);
}
.recall-btn-ghost:hover { background: var(--kf-background); }
.recall-btn-primary {
  background: var(--kf-primary);
  color: var(--kf-primary-foreground);
}
.recall-btn-primary:hover { filter: brightness(0.95); }
.recall-btn:disabled { opacity: 0.55; cursor: not-allowed; }
.recall-btn:focus-visible { outline: 2px solid var(--kf-ring); outline-offset: 2px; }
.recall-gate {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-4);
  padding: var(--kf-space-5);
  border-radius: var(--kf-radius-lg);
  border: 1px solid var(--kf-border);
  background: var(--kf-card);
}
.recall-gate-head { display: flex; align-items: flex-start; gap: var(--kf-space-3); }
.recall-gate-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
  width: 38px;
  height: 38px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--kf-state-success) 12%, transparent);
  color: var(--kf-state-success);
}
.recall-gate-icon.is-waiting { background: color-mix(in srgb, var(--kf-state-warning) 14%, transparent); color: var(--kf-state-warning); }
.recall-gate-icon.is-ready { background: var(--kf-primary-soft); color: var(--kf-primary); }
.recall-gate-title { margin: 0; font-family: var(--font-serif); font-size: var(--kf-fs-body-lg); font-weight: 800; color: var(--kf-foreground); }
.recall-gate-desc { margin: 6px 0 0; font-size: var(--kf-fs-body-sm); line-height: 1.7; color: var(--kf-muted-foreground); max-width: 700px; }
.recall-gate-gaps { display: flex; flex-direction: column; gap: var(--kf-space-2); }
.recall-clue-block,
.recall-clue-empty {
  margin: 0;
}
.recall-clue-block {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-2);
  padding: var(--kf-space-3);
  border-radius: var(--kf-radius-md);
  background: color-mix(in srgb, var(--kf-state-warning) 6%, transparent);
}
.recall-clue-title {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: var(--kf-fs-caption);
  font-weight: 700;
  color: var(--kf-foreground);
}
.recall-clue-list { display: flex; flex-wrap: wrap; gap: 6px; }
.recall-clue-chip {
  padding: 3px 9px;
  border-radius: 999px;
  border: 1px solid var(--kf-border);
  background: var(--kf-card);
  font-size: var(--kf-fs-caption);
  color: var(--kf-foreground);
}
.recall-clue-chip.is-confirmed {
  border-color: color-mix(in srgb, var(--kf-state-warning) 50%, var(--kf-border));
  background: color-mix(in srgb, var(--kf-state-warning) 12%, var(--kf-card));
}
.recall-clue-chip.is-warning {
  border-color: color-mix(in srgb, var(--kf-state-error) 35%, var(--kf-border));
  color: var(--kf-state-error);
}
.recall-clue-chip.is-success {
  border-color: color-mix(in srgb, var(--kf-state-success) 35%, var(--kf-border));
  color: var(--kf-state-success);
}
.recall-clue-empty { font-size: var(--kf-fs-caption); line-height: 1.7; color: var(--kf-muted-foreground); }
.recall-round3-waiting { display: flex; align-items: flex-start; gap: var(--kf-space-4); }
.recall-round3-waiting-text { min-width: 0; }
.recall-countdown {
  display: inline-flex;
  align-items: baseline;
  gap: var(--kf-space-2);
  margin-top: var(--kf-space-3);
}
.recall-countdown-value {
  font-family: var(--font-mono);
  font-size: var(--kf-fs-display);
  font-weight: 800;
  line-height: 1;
  color: var(--kf-state-warning);
}
.recall-countdown-label { font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
.recall-round3-due { margin: var(--kf-space-2) 0 0; font-family: var(--font-mono); font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
.recall-round3-ready { display: flex; flex-direction: column; gap: var(--kf-space-4); align-items: flex-start; }
.recall-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--kf-space-3);
  padding: 64px 20px;
  border-radius: var(--kf-radius-lg);
  border: 1px dashed var(--kf-border);
  background: var(--kf-card);
  text-align: center;
}
.recall-state-loading { align-items: stretch; text-align: left; }
.recall-state-icon { color: var(--kf-state-error); }
.recall-state-title { margin: 0; font-family: var(--font-serif); font-size: var(--kf-fs-h3); color: var(--kf-foreground); }
.recall-state-desc { margin: 0; font-size: var(--kf-fs-body-sm); color: var(--kf-muted-foreground); max-width: 480px; }
.recall-state-actions { display: flex; gap: var(--kf-space-2); flex-wrap: wrap; justify-content: center; }
.recall-skel-line {
  border-radius: 6px;
  background: linear-gradient(90deg, var(--kf-background), var(--kf-muted));
  animation: recall-practice-pulse 1.4s ease-in-out infinite;
}
@keyframes recall-practice-pulse { 0%, 100% { opacity: 0.55; } 50% { opacity: 1; } }
.recall-result {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-4);
  padding: var(--kf-space-5);
  border-radius: var(--kf-radius-lg);
  border: 1px solid color-mix(in srgb, var(--kf-state-success) 30%, var(--kf-border));
  background: var(--kf-card);
}
.recall-result-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--kf-space-4);
  flex-wrap: wrap;
}
.recall-result-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 9px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--kf-state-success) 12%, transparent);
  color: var(--kf-state-success);
  font-size: 11px;
  font-weight: 700;
}
.recall-result-title { margin: var(--kf-space-2) 0 0; font-family: var(--font-serif); font-size: var(--kf-fs-h3); font-weight: 800; color: var(--kf-foreground); }
.recall-result-desc { margin: 6px 0 0; font-size: var(--kf-fs-caption); line-height: 1.7; color: var(--kf-muted-foreground); max-width: 680px; }
.recall-result-time { font-family: var(--font-mono); font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
.recall-score-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--kf-space-3);
}
.recall-score-card {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: var(--kf-space-4);
  border-radius: var(--kf-radius-md);
  border: 1px solid var(--kf-border);
  background: var(--kf-background);
}
.recall-score-card.is-final { border-color: color-mix(in srgb, var(--kf-primary) 35%, var(--kf-border)); background: var(--kf-primary-soft); }
.recall-score-label { font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
.recall-score-value { font-family: var(--font-mono); font-size: var(--kf-fs-h1); font-weight: 800; line-height: 1.1; color: var(--kf-foreground); }
.recall-score-card.is-final .recall-score-value { color: var(--kf-primary); }
.recall-score-unit,
.recall-score-delta { font-size: 11px; color: var(--kf-muted-foreground); }
.recall-review-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--kf-space-3);
}
.recall-review-panel {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-2);
  padding: var(--kf-space-4);
  border-radius: var(--kf-radius-md);
  border: 1px solid var(--kf-border);
  background: var(--kf-card);
}
.recall-review-panel.is-warning { background: color-mix(in srgb, var(--kf-state-error) 4%, var(--kf-card)); }
.recall-review-panel.is-success { background: color-mix(in srgb, var(--kf-state-success) 4%, var(--kf-card)); }
.recall-review-title { display: flex; align-items: center; gap: 5px; margin: 0; font-size: var(--kf-fs-body-sm); font-weight: 700; color: var(--kf-foreground); }
.recall-review-empty { margin: 0; font-size: var(--kf-fs-caption); line-height: 1.7; color: var(--kf-muted-foreground); }
.recall-suggestion {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-2);
  padding: var(--kf-space-4);
  border-radius: var(--kf-radius-md);
  background: var(--kf-background);
}
.recall-suggestion-list { margin: 0; padding-left: 20px; display: flex; flex-direction: column; gap: 5px; font-size: var(--kf-fs-body-sm); line-height: 1.65; color: var(--kf-foreground); }
.recall-suggestion-note { margin: 0; font-size: var(--kf-fs-caption); line-height: 1.6; color: var(--kf-muted-foreground); }
.recall-comparison-stack { display: flex; flex-direction: column; gap: var(--kf-space-4); }
.recall-comparison-head { display: flex; flex-direction: column; gap: 4px; }
.recall-comparison-title { display: flex; align-items: center; gap: 6px; margin: 0; font-family: var(--font-serif); font-size: var(--kf-fs-body-lg); font-weight: 800; color: var(--kf-foreground); }
.recall-comparison-desc { margin: 0; font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
@media (max-width: 900px) {
  .recall-review-grid,
  .recall-score-grid { grid-template-columns: 1fr; }
  .recall-round-progress { grid-template-columns: 1fr; }
  .recall-practice-head { align-items: flex-start; }
  .recall-head-score { display: none; }
}
@media (max-width: 600px) {
  .recall-practice { gap: var(--kf-space-4); }
  .recall-practice-title { font-size: 24px; }
  .recall-writing-card,
  .recall-gate,
  .recall-result { padding: var(--kf-space-4); }
  .recall-writing-foot { flex-direction: column-reverse; align-items: stretch; }
  .recall-writing-foot .recall-btn { justify-content: center; }
  .recall-round3-waiting { flex-direction: column; }
  .recall-countdown-value { font-size: var(--kf-fs-h1); }
}
</style>
