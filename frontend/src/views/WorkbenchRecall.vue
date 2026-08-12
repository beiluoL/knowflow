<template>
  <div class="wb-page animate-fade-in" :style="{ '--mc': themeColor }">
    <!-- ============ Module Hero ============ -->
    <section class="wb-hero">
      <div class="wb-hero-bg" aria-hidden="true">
        <span class="wb-blob"></span>
        <span class="wb-grid"></span>
      </div>
      <div class="wb-hero-inner">
        <div class="wb-hero-head">
          <div class="wb-hero-text">
            <span class="wb-eyebrow">
              <span class="wb-eyebrow-dot"></span>
              Step 03 · 复习 · Active Recall
            </span>
            <h1 class="wb-title">
              <Icon name="edit-2" :size="28" class="wb-title-icon" />
              主动回忆 · 三轮闭卷默写
            </h1>
            <p class="wb-subtitle">
              <strong>即时默写 → 补漏默写 → 1小时后复测</strong>，三轮闭卷书写后自动比对原文，
              高亮遗漏与错误，强制大脑提取让记忆真正扎根。
            </p>
          </div>
          <div class="wb-hero-actions">
            <router-link to="/workbench/review" class="kb-btn wb-ghost-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors">
              <Icon name="repeat" :size="15" aria-hidden="true" /> 间隔重复
            </router-link>
            <button class="kb-btn kb-btn-primary wb-cta focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" @click="showCreate = true">
              <Icon name="plus" :size="16" aria-hidden="true" /> 新建默写
            </button>
          </div>
        </div>

        <!-- 闭环导航条 -->
        <nav class="wb-loop-nav" aria-label="学习闭环">
          <router-link v-for="s in loopSteps" :key="s.key" :to="s.path" class="wb-loop-step focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" :class="{ 'is-current': s.key === 'review' }">
            <span class="wb-loop-num">{{ s.num }}</span>
            <span class="wb-loop-name">{{ s.name }}</span>
          </router-link>
        </nav>
      </div>
    </section>

    <!-- ============ 会话列表 ============ -->
    <section v-if="!activeSession">
      <h2 class="wb-section-title">
        <Icon name="list" :size="18" style="color: var(--mc);" />
        默写会话记录
        <span class="wb-section-hint">{{ sessions.length }} 个会话</span>
      </h2>

      <div v-if="loading" class="wb-session-grid">
        <div v-for="n in 3" :key="n" class="wb-session-card wb-skeleton">
          <div class="wb-skel-line" style="width: 60%; height: 16px;"></div>
          <div class="wb-skel-line" style="width: 80%; height: 12px; margin-top: 8px;"></div>
        </div>
      </div>

      <div v-else-if="sessions.length === 0" class="wb-empty">
        <div class="wb-empty-icon"><Icon name="edit-2" :size="40" /></div>
        <h3 class="wb-empty-title">还没有默写会话</h3>
        <p class="wb-empty-desc">粘贴一段要记忆的内容，开始三轮闭卷默写。</p>
        <button class="kb-btn kb-btn-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" @click="showCreate = true">
          <Icon name="plus" :size="15" aria-hidden="true" /> 新建默写
        </button>
      </div>

      <div v-else class="wb-session-grid">
        <article
          v-for="s in sessions"
          :key="s.id"
          class="wb-session-card focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
          role="button"
          tabindex="0"
          @click="enterSession(s.id)"
          @keydown.enter.prevent="($event.target as HTMLElement).click()"
        >
          <div class="wb-session-head">
            <span class="wb-session-status" :class="s.status === 'COMPLETED' ? 'is-done' : 'is-progress'">
              <span class="wb-status-dot"></span>{{ s.status === 'COMPLETED' ? '已完成' : '进行中' }}
            </span>
            <span class="wb-session-round">{{ s.currentRound }}/3 轮</span>
          </div>
          <h3 class="wb-session-title">{{ s.title || '未命名会话' }}</h3>
          <p class="wb-session-source">{{ s.sourceText.slice(0, 80) }}{{ s.sourceText.length > 80 ? '…' : '' }}</p>
          <div class="wb-session-scores">
            <span class="wb-score-chip" :class="{ 'is-null': s.round1Score == null }">
              <span class="wb-score-label">R1</span>
              <span class="wb-score-val">{{ s.round1Score ?? '--' }}</span>
            </span>
            <span class="wb-score-chip" :class="{ 'is-null': s.round2Score == null }">
              <span class="wb-score-label">R2</span>
              <span class="wb-score-val">{{ s.round2Score ?? '--' }}</span>
            </span>
            <span class="wb-score-chip" :class="{ 'is-null': s.round3Score == null }">
              <span class="wb-score-label">R3</span>
              <span class="wb-score-val">{{ s.round3Score ?? '--' }}</span>
            </span>
          </div>
        </article>
      </div>
    </section>

    <!-- ============ 会话详情：三轮默写流程 ============ -->
    <section v-else class="recall-detail">
      <!-- 顶部：返回 + 标题 + 进度条 -->
      <div class="recall-detail-head">
        <button class="wb-icon-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" aria-label="返回" @click="exitSession">
          <Icon name="chevron-left" :size="18" aria-hidden="true" />
        </button>
        <div class="recall-detail-title">
          <h2 class="wb-section-title" style="margin: 0;">
            <Icon name="edit-2" :size="18" style="color: var(--mc);" />
            {{ activeSession.title || '未命名会话' }}
          </h2>
          <span class="recall-detail-hint">
            {{ activeSession.status === 'COMPLETED' ? '已完成三轮默写' : `进行中 · 第 ${activeSession.currentRound} 轮` }}
          </span>
        </div>
      </div>

      <!-- 轮次进度条 -->
      <div class="recall-progress">
        <div
          v-for="r in rounds"
          :key="r.num"
          class="recall-progress-node"
          :class="{
            'is-done': r.num < activeSession.currentRound || activeSession.status === 'COMPLETED',
            'is-current': r.num === activeSession.currentRound && activeSession.status !== 'COMPLETED',
            'is-waiting': r.num > activeSession.currentRound && activeSession.status !== 'COMPLETED',
          }"
        >
          <div class="recall-progress-circle">
            <Icon v-if="r.num < activeSession.currentRound || activeSession.status === 'COMPLETED'" name="check" :size="16" />
            <span v-else>{{ r.num }}</span>
          </div>
          <div class="recall-progress-label">
            <span class="recall-progress-name">{{ r.name }}</span>
            <span class="recall-progress-desc">{{ r.desc }}</span>
          </div>
          <span v-if="r.score != null" class="recall-progress-score">{{ r.score }}分</span>
        </div>
      </div>

      <!-- 当前轮次书写区 -->
      <div v-if="activeSession.status !== 'COMPLETED'" class="recall-write">
        <div class="recall-write-head">
          <h3 class="recall-write-title">
            第 {{ activeSession.currentRound }} 轮 · {{ currentRoundInfo.name }}
          </h3>
          <p class="recall-write-desc">{{ currentRoundInfo.desc }}</p>
          <span v-if="activeSession.currentRound === 3 && activeSession.round3DueTime" class="recall-countdown">
            <Icon name="clock" :size="13" />
            建议复测时间：{{ formatTime(activeSession.round3DueTime) }}
          </span>
        </div>

        <textarea
          v-model="currentText"
          class="kb-input recall-write-area focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
          :placeholder="`闭卷默写原文内容…（第 ${activeSession.currentRound} 轮）`"
          rows="14"
        ></textarea>

        <div class="recall-write-actions">
          <button
            v-if="activeSession.currentRound > 1"
            class="kb-btn wb-ghost-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
            @click="showSourcePreview = !showSourcePreview"
          >
            <Icon name="eye" :size="15" aria-hidden="true" /> {{ showSourcePreview ? '隐藏原文' : '查看原文（作弊警告）' }}
          </button>
          <button
            class="kb-btn kb-btn-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
            :disabled="submitting || !currentText.trim()"
            @click="submitRound"
          >
            <Icon name="send" :size="15" aria-hidden="true" /> 提交本轮
          </button>
        </div>

        <div v-if="showSourcePreview" class="recall-source-preview">
          <span class="recall-source-label">原文（参考）</span>
          <pre class="recall-source-text">{{ activeSession.sourceText }}</pre>
        </div>
      </div>

      <!-- 比对结果区（已提交的轮次） -->
      <div v-if="comparison" class="recall-comparison">
        <h3 class="wb-section-title">
          <Icon name="git-branch" :size="18" style="color: var(--mc);" />
          比对结果 · 第 {{ comparison.round }} 轮
        </h3>
        <div class="recall-comparison-score">
          <span class="recall-score-big" :style="{ color: scoreColor(comparison.score) }">{{ comparison.score }}</span>
          <span class="recall-score-unit">/100</span>
        </div>
        <div class="recall-comparison-diff">
          <span class="recall-diff-label">原文（高亮遗漏与错误）</span>
          <div class="recall-diff-text" v-html="comparison.markedSource"></div>
        </div>
      </div>

      <!-- 三轮分数趋势 + 进步百分比 -->
      <div v-if="activeSession.scoreTrend && activeSession.scoreTrend.some((s) => s != null)" class="recall-trend">
        <h3 class="wb-section-title">
          <Icon name="trending-up" :size="18" style="color: var(--mc);" />
          三轮分数趋势
        </h3>
        <div class="recall-trend-chart">
          <svg :viewBox="`0 0 ${svgW} ${svgH}`" class="recall-trend-svg">
            <line v-for="g in trendYTicks" :key="'g' + g.y" :x1="padL" :y1="g.y" :x2="svgW - padR" :y2="g.y"
                  stroke="var(--kb-border)" stroke-width="1" stroke-dasharray="3 4" />
            <text v-for="g in trendYTicks" :key="'gt' + g.y" :x="padL - 8" :y="g.y + 4" text-anchor="end"
                  font-size="10" font-family="var(--font-mono)" fill="var(--kb-muted-foreground)">{{ g.label }}</text>

            <polyline
              v-if="trendPoints.length >= 2"
              :points="trendPoints.map((p) => `${p.x},${p.y}`).join(' ')"
              fill="none" stroke="var(--mc)" stroke-width="2.5" stroke-linejoin="round"
            />
            <g v-for="(p, i) in trendPoints" :key="'pt' + i">
              <circle :cx="p.x" :cy="p.y" r="5" fill="var(--mc)" />
              <text :x="p.x" :y="p.y - 10" text-anchor="middle" font-size="12" font-weight="700"
                    font-family="var(--font-mono)" fill="var(--mc)">{{ p.score }}</text>
            </g>

            <text v-for="(r, i) in roundLabels" :key="'rl' + i" :x="trendX(i)" :y="svgH - 8"
                  text-anchor="middle" font-size="11" font-weight="500" fill="var(--kb-muted-foreground)">{{ r }}</text>
          </svg>
        </div>

        <div class="recall-improvement">
          <div
            v-for="(imp, i) in activeSession.improvementPct"
            :key="'imp' + i"
            class="recall-imp-card"
          >
            <span class="recall-imp-label">第 {{ i + 1 }} 轮</span>
            <span v-if="imp == null" class="recall-imp-val recall-imp-null">—</span>
            <span v-else-if="imp >= 0" class="recall-imp-val recall-imp-up">
              <Icon name="trending-up" :size="14" /> +{{ imp }}%
            </span>
            <span v-else class="recall-imp-val recall-imp-down">
              <Icon name="trending-down" :size="14" /> {{ imp }}%
            </span>
          </div>
        </div>
      </div>
    </section>

    <!-- ============ 新建会话 Drawer ============ -->
    <div v-if="showCreate" class="wb-drawer-mask focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" role="button" tabindex="0" @click.self="showCreate = false" @keydown.enter.prevent="($event.target as HTMLElement).click()">
      <div class="wb-drawer">
        <header class="wb-drawer-head">
          <div>
            <span class="wb-eyebrow wb-eyebrow-sm">New Session</span>
            <h2 class="wb-drawer-title">新建默写会话</h2>
          </div>
          <button class="wb-icon-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" aria-label="关闭" @click="showCreate = false"><Icon name="x" :size="18" aria-hidden="true" /></button>
        </header>
        <div class="wb-drawer-body">
          <div class="wb-field">
            <label class="wb-label">标题</label>
            <input v-model="createForm.title" class="kb-input focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" placeholder="如：SM-2 算法要点" />
          </div>
          <div class="wb-field">
            <label class="wb-label">原文 <span class="wb-req">*</span></label>
            <textarea v-model="createForm.sourceText" class="kb-input focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" rows="8" placeholder="粘贴要记忆的内容…"></textarea>
          </div>
          <p class="recall-create-hint">
            <Icon name="info" :size="13" />
            提交后将进入三轮闭卷默写：即时默写 → 补漏默写 → 1小时后复测
          </p>
        </div>
        <footer class="wb-drawer-foot">
          <button class="kb-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" @click="showCreate = false">取消</button>
          <button class="kb-btn kb-btn-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" @click="createSession">
            <Icon name="play" :size="15" aria-hidden="true" /> 开始默写
          </button>
        </footer>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { notify, confirmDialog, getApiError } from '@/utils/toast'
import './workbench-shared.css'
import {
  listRecallSessions,
  getRecallSession,
  createRecallSession,
  submitRecallRound,
  deleteRecallSession,
} from '@/api/workbench'
import type { WbRecallSession, WbRecallSessionPayload } from '@/api/types'

const route = useRoute()
const router = useRouter()
const themeColor = '#F59E0B'

const sessions = ref<WbRecallSession[]>([])
const activeSession = ref<WbRecallSession | null>(null)
const loading = ref(true)
const submitting = ref(false)
const showCreate = ref(false)
const showSourcePreview = ref(false)
const currentText = ref('')
const comparison = ref<{ round: number; score: number; markedSource: string } | null>(null)

const createForm = reactive<Pick<WbRecallSessionPayload, 'title' | 'sourceText'>>({
  title: '',
  sourceText: '',
})

const loopSteps = [
  { key: 'input', num: '01', name: '输入', path: '/workbench/capture' },
  { key: 'organize', num: '02', name: '整理', path: '/workbench/notes' },
  { key: 'review', num: '03', name: '复习', path: '/workbench/review' },
  { key: 'output', num: '04', name: '输出', path: '/workbench/story' },
]

const rounds = computed(() => {
  if (!activeSession.value) return []
  const s = activeSession.value
  return [
    { num: 1, name: '即时默写', desc: '阅读原文后立即闭卷默写', score: s.round1Score },
    { num: 2, name: '补漏默写', desc: '查看比对后补漏再默写一次', score: s.round2Score },
    { num: 3, name: '1小时复测', desc: '间隔1小时后再次闭卷复测', score: s.round3Score },
  ]
})

const currentRoundInfo = computed(() => {
  const r = activeSession.value?.currentRound || 1
  return rounds.value.find((x) => x.num === r) || rounds.value[0]
})

// ===== SVG trend chart =====
const svgW = 480
const svgH = 200
const padL = 36
const padR = 16
const padT = 24
const padB = 32

const trendPoints = computed(() => {
  if (!activeSession.value) return []
  const trend = activeSession.value.scoreTrend
  const innerW = svgW - padL - padR
  const innerH = svgH - padT - padB
  return trend
    .map((score, i) => {
      if (score == null) return null
      const x = padL + (innerW * i) / 2
      const y = padT + innerH - (score / 100) * innerH
      return { x, y, score }
    })
    .filter((p): p is { x: number; y: number; score: number } => p !== null)
})

const trendYTicks = [
  { y: padT, label: '100' },
  { y: padT + (svgH - padT - padB) * 0.5, label: '50' },
  { y: svgH - padB, label: '0' },
]
const roundLabels = ['即时默写', '补漏默写', '1h复测']
function trendX(i: number) {
  const innerW = svgW - padL - padR
  return padL + (innerW * i) / 2
}

// ===== Load =====
async function loadSessions() {
  loading.value = true
  try {
    sessions.value = await listRecallSessions()
  } catch (e) {
    notify(getApiError(e, '加载会话列表失败'), 'error')
  } finally {
    loading.value = false
  }
}

async function enterSession(id: number) {
  try {
    activeSession.value = await getRecallSession(id)
    comparison.value = null
    currentText.value = ''
    showSourcePreview.value = false
  } catch (e) {
    notify(getApiError(e, '加载会话失败'), 'error')
  }
}

function exitSession() {
  if (activeSession.value && activeSession.value.status !== 'COMPLETED' && currentText.value.trim()) {
    confirmDialog('当前默写内容未提交，确认离开？').then((ok) => {
      if (ok) {
        activeSession.value = null
        comparison.value = null
        loadSessions()
      }
    })
  } else {
    activeSession.value = null
    comparison.value = null
    loadSessions()
  }
}

async function createSession() {
  if (!createForm.sourceText.trim()) {
    notify('原文不能为空', 'warning')
    return
  }
  try {
    const id = await createRecallSession({
      title: createForm.title,
      sourceText: createForm.sourceText,
    })
    notify('已创建，开始第一轮默写', 'success')
    showCreate.value = false
    Object.assign(createForm, { title: '', sourceText: '' })
    await enterSession(id)
    loadSessions()
  } catch (e) {
    notify(getApiError(e, '创建失败'), 'error')
  }
}

async function submitRound() {
  if (!activeSession.value || !currentText.value.trim()) return
  submitting.value = true
  try {
    const round = activeSession.value.currentRound
    const updated = await submitRecallRound(activeSession.value.id, {
      round,
      text: currentText.value,
    })
    activeSession.value = updated
    const score = round === 1 ? updated.round1Score : round === 2 ? updated.round2Score : updated.round3Score
    comparison.value = {
      round,
      score: score || 0,
      markedSource: buildMarkedSource(updated.sourceText, currentText.value),
    }
    currentText.value = ''
    notify(`第 ${round} 轮已提交，得分 ${score} 分`, 'success')
    loadSessions()
  } catch (e) {
    notify(getApiError(e, '提交失败'), 'error')
  } finally {
    submitting.value = false
  }
}

/**
 * 比对原文与默写，生成高亮 HTML：
 * - 命中的词：正常显示
 * - 遗漏的词：红色高亮（遗漏）
 * - 默写中多出的词：不在此处标注（原文只标遗漏）
 */
function buildMarkedSource(source: string, recall: string): string {
  const sourceTokens = tokenize(source)
  const recallTokens = tokenize(recall)
  let result = ''
  let lastIdx = 0
  const re = /[a-zA-Z]+|[\u4e00-\u9fa5]+/g
  let m: RegExpExecArray | null
  while ((m = re.exec(source)) !== null) {
    const token = m[0].toLowerCase()
    const original = m[0]
    result += escapeHtml(source.slice(lastIdx, m.index))
    const isHit = sourceTokens.has(token) && recallTokens.has(token)
    if (isHit) {
      result += `<span class="rc-hit">${escapeHtml(original)}</span>`
    } else {
      result += `<span class="rc-miss">${escapeHtml(original)}</span>`
    }
    lastIdx = m.index + original.length
  }
  result += escapeHtml(source.slice(lastIdx))
  return result
}

function tokenize(text: string): Set<string> {
  const tokens = new Set<string>()
  const lower = text.toLowerCase()
  const re = /[a-z]+|[\u4e00-\u9fa5]/g
  let m: RegExpExecArray | null
  while ((m = re.exec(lower)) !== null) {
    if (m[0].length > 1) tokens.add(m[0])
  }
  return tokens
}

function escapeHtml(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function scoreColor(score: number) {
  if (score >= 80) return '#10B981'
  if (score >= 50) return '#F59E0B'
  return '#EF4444'
}

function formatTime(iso?: string) {
  if (!iso) return ''
  const d = new Date(iso.includes('T') ? iso : iso.replace(' ', 'T'))
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

onMounted(() => {
  loadSessions()
  if (route.query.id) {
    enterSession(Number(route.query.id))
  }
})
</script>

<style scoped>
.wb-hero-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.wb-ghost-btn {
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  color: var(--kb-foreground);
}
.wb-ghost-btn:hover { border-color: var(--mc); color: var(--mc); }

/* ===== Session Grid ===== */
.wb-session-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
}
.wb-session-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px;
  border-radius: var(--kb-radius-md);
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  cursor: pointer;
  transition: all 0.18s ease;
}
.wb-session-card:hover {
  border-color: color-mix(in srgb, var(--mc) 35%, var(--kb-border));
  box-shadow: var(--shadow-card-hover);
  transform: translateY(-2px);
}
.wb-session-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.wb-session-status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
}
.wb-session-status.is-done {
  background: color-mix(in srgb, #10B981 12%, transparent);
  color: #10B981;
}
.wb-session-status.is-progress {
  background: color-mix(in srgb, var(--mc) 12%, transparent);
  color: var(--mc);
}
.wb-status-dot {
  width: 6px; height: 6px;
  border-radius: 50%;
  background: currentColor;
}
.wb-session-round {
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 600;
  color: var(--kb-muted-foreground);
}
.wb-session-title {
  font-family: var(--font-serif);
  font-size: 16px;
  font-weight: 700;
  color: var(--kb-foreground);
  margin: 0;
}
.wb-session-source {
  font-size: 12px;
  line-height: 1.6;
  color: var(--kb-muted-foreground);
  margin: 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  flex: 1;
}
.wb-session-scores {
  display: flex;
  gap: 6px;
}
.wb-score-chip {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  padding: 4px 8px;
  border-radius: var(--kb-radius-sm);
  background: color-mix(in srgb, var(--mc) 8%, transparent);
}
.wb-score-chip.is-null {
  background: var(--kb-muted);
  opacity: 0.6;
}
.wb-score-label {
  font-family: var(--font-mono);
  font-size: 9px;
  color: var(--kb-muted-foreground);
  letter-spacing: 0.04em;
}
.wb-score-val {
  font-family: var(--font-mono);
  font-size: 15px;
  font-weight: 700;
  color: var(--mc);
}
.wb-score-chip.is-null .wb-score-val {
  color: var(--kb-muted-foreground);
}

/* ===== Detail ===== */
.recall-detail {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.recall-detail-head {
  display: flex;
  align-items: center;
  gap: 10px;
}
.recall-detail-title {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.recall-detail-hint {
  font-size: 12px;
  color: var(--kb-muted-foreground);
}

/* ===== Progress Bar ===== */
.recall-progress {
  display: flex;
  align-items: stretch;
  gap: 0;
  padding: 18px;
  border-radius: var(--kb-radius-md);
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
}
.recall-progress-node {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  position: relative;
  padding: 0 8px;
  text-align: center;
}
.recall-progress-node:not(:last-child)::after {
  content: '';
  position: absolute;
  top: 16px;
  right: -50%;
  width: 100%;
  height: 2px;
  background: var(--kb-border);
  z-index: 0;
}
.recall-progress-node.is-done:not(:last-child)::after {
  background: var(--mc);
}
.recall-progress-circle {
  position: relative;
  z-index: 1;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 700;
  background: var(--kb-muted);
  color: var(--kb-muted-foreground);
  border: 2px solid var(--kb-card);
  transition: all 0.2s ease;
}
.recall-progress-node.is-done .recall-progress-circle {
  background: var(--mc);
  color: #fff;
}
.recall-progress-node.is-current .recall-progress-circle {
  background: var(--kb-card);
  border-color: var(--mc);
  color: var(--mc);
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--mc) 15%, transparent);
}
.recall-progress-node.is-waiting .recall-progress-circle {
  opacity: 0.5;
}
.recall-progress-label {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.recall-progress-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--kb-foreground);
}
.recall-progress-desc {
  font-size: 11px;
  color: var(--kb-muted-foreground);
  line-height: 1.4;
}
.recall-progress-score {
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 700;
  color: var(--mc);
}

/* ===== Write Area ===== */
.recall-write {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 20px;
  border-radius: var(--kb-radius-md);
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
}
.recall-write-head {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.recall-write-title {
  font-family: var(--font-serif);
  font-size: 18px;
  font-weight: 700;
  color: var(--kb-foreground);
  margin: 0;
}
.recall-write-desc {
  font-size: 13px;
  color: var(--kb-muted-foreground);
  margin: 0;
}
.recall-countdown {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--kb-warning);
  font-weight: 500;
  margin-top: 2px;
}
.recall-write-area {
  font-family: var(--font-sans);
  font-size: 15px;
  line-height: 1.8;
  min-height: 200px;
  resize: vertical;
}
.recall-write-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
.recall-source-preview {
  padding: 14px;
  border-radius: var(--kb-radius-sm);
  background: color-mix(in srgb, var(--kb-warning) 5%, transparent);
  border: 1px dashed var(--kb-warning);
}
.recall-source-label {
  display: block;
  font-size: 11px;
  font-weight: 600;
  color: var(--kb-warning);
  margin-bottom: 6px;
}
.recall-source-text {
  font-family: var(--font-sans);
  font-size: 13px;
  line-height: 1.7;
  color: var(--kb-foreground);
  white-space: pre-wrap;
  margin: 0;
}

/* ===== Comparison ===== */
.recall-comparison {
  padding: 20px;
  border-radius: var(--kb-radius-md);
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
}
.recall-comparison-score {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin-bottom: 14px;
}
.recall-score-big {
  font-family: var(--font-mono);
  font-size: 40px;
  font-weight: 900;
  line-height: 1;
}
.recall-score-unit {
  font-family: var(--font-mono);
  font-size: 16px;
  color: var(--kb-muted-foreground);
}
.recall-comparison-diff {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.recall-diff-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--kb-muted-foreground);
}
.recall-diff-text {
  font-size: 14px;
  line-height: 1.8;
  color: var(--kb-foreground);
  padding: 12px;
  border-radius: var(--kb-radius-sm);
  background: var(--kb-background);
  border: 1px solid var(--kb-border);
}
.recall-diff-text :deep(.rc-hit) {
  color: var(--kb-foreground);
}
.recall-diff-text :deep(.rc-miss) {
  background: color-mix(in srgb, #EF4444 18%, transparent);
  color: #EF4444;
  border-radius: 2px;
  padding: 0 2px;
  font-weight: 600;
}

/* ===== Trend Chart ===== */
.recall-trend {
  padding: 20px;
  border-radius: var(--kb-radius-md);
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
}
.recall-trend-chart {
  margin: 10px 0;
}
.recall-trend-svg {
  width: 100%;
  height: auto;
}
.recall-improvement {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-top: 12px;
}
.recall-imp-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 10px;
  border-radius: var(--kb-radius-sm);
  background: var(--kb-background);
  border: 1px solid var(--kb-border);
}
.recall-imp-label {
  font-size: 11px;
  color: var(--kb-muted-foreground);
  font-weight: 500;
}
.recall-imp-val {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-family: var(--font-mono);
  font-size: 16px;
  font-weight: 700;
}
.recall-imp-null { color: var(--kb-muted-foreground); }
.recall-imp-up { color: #10B981; }
.recall-imp-down { color: #EF4444; }

/* ===== Create Hint ===== */
.recall-create-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--kb-muted-foreground);
  margin: 0;
  padding: 10px;
  border-radius: var(--kb-radius-sm);
  background: color-mix(in srgb, var(--mc) 5%, transparent);
}

@media (max-width: 1100px) {
  .wb-session-grid { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 768px) {
  .wb-session-grid { grid-template-columns: 1fr; }
  .recall-progress { flex-direction: column; gap: 16px; }
  .recall-progress-node { flex-direction: row; text-align: left; }
  .recall-progress-node:not(:last-child)::after { display: none; }
  .recall-improvement { grid-template-columns: 1fr; }
}
</style>
