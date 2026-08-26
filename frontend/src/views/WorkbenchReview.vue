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
              Step 03 · 复习 · Spaced Repetition
            </span>
            <h1 class="wb-title">
              <Icon name="repeat" :size="28" class="wb-title-icon" />
              知识复习 · 间隔重复
            </h1>
            <p class="wb-subtitle">
              基于 <strong>SM-2 遗忘曲线</strong>自动排程，按反馈动态拉长复习间隔。
              记忆宫殿用空间锚定让抽象知识具象、牢固。
            </p>
          </div>
          <div class="wb-hero-actions">
            <router-link to="/workbench/recall" class="kb-btn wb-ghost-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors">
              <Icon name="edit-2" :size="15" aria-hidden="true" /> 主动回忆
            </router-link>
            <router-link to="/workbench/palace" class="kb-btn wb-ghost-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors">
              <Icon name="map-pin" :size="15" aria-hidden="true" /> 记忆宫殿
            </router-link>
            <button class="kb-btn kb-btn-primary wb-cta focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" @click="startReview">
              <Icon name="play" :size="15" aria-hidden="true" /> 开始抽查
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

    <!-- ============ 遗忘曲线可视化 ============ -->
    <section>
      <h2 class="wb-section-title">
        <Icon name="trending-down" :size="18" style="color: var(--mc);" />
        遗忘曲线
        <span class="wb-section-hint">近 {{ curveDays }} 天复习 {{ curve?.totalReviews || 0 }} 次 · 遗忘率 {{ ((curve?.overallLapseRate || 0) * 100).toFixed(1) }}%</span>
      </h2>
      <div class="wb-curve-card">
        <div class="wb-curve-toolbar">
          <div class="wb-curve-range">
            <button
              v-for="d in [14, 30, 90]"
              :key="d"
              class="wb-range-btn hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
              :class="{ 'is-active': curveDays === d }"
              @click="curveDays = d; loadCurve()"
            >{{ d }}天</button>
          </div>
          <div v-if="curve" class="wb-curve-legend">
            <span class="wb-legend-item">
              <span class="wb-legend-bar"></span>每日复习量
            </span>
            <span class="wb-legend-item">
              <span class="wb-legend-line"></span>遗忘率
            </span>
          </div>
        </div>

        <div v-if="curveLoading" class="wb-curve-loading">
          <Icon name="repeat" :size="22" class="animate-spin" style="color: var(--kb-muted-foreground);" />
        </div>
        <div v-else-if="!curve || curve.points.length === 0" class="wb-curve-empty">
          <Icon name="bar-chart-2" :size="32" style="color: var(--kb-muted-foreground); opacity: 0.4;" />
          <p>暂无复习记录，开始复习后这里会呈现记忆巩固趋势</p>
        </div>
        <template v-else>
          <svg :viewBox="`0 0 ${svgW} ${svgH}`" class="wb-curve-svg">
            <line v-for="g in yTicks" :key="'g' + g" :x1="padL" :y1="g.y" :x2="svgW - padR" :y2="g.y"
                  stroke="var(--kb-border)" stroke-width="1" stroke-dasharray="3 4" />
            <text v-for="g in yTicks" :key="'gt' + g" :x="padL - 8" :y="g.y + 4" text-anchor="end"
                  font-size="10" font-family="var(--font-mono)" fill="var(--kb-muted-foreground)">{{ g.label }}</text>

            <rect v-for="(p, i) in chartPoints" :key="'b' + i" :x="p.x - p.barW / 2" :y="p.barY"
                  :width="p.barW" :height="p.barH" rx="2" fill="var(--mc)" fill-opacity="0.28" />

            <polyline :points="chartPoints.map((p) => `${p.x},${p.lineY}`).join(' ')"
                      fill="none" stroke="#EF4444" stroke-width="2.5" stroke-linejoin="round" />
            <circle v-for="(p, i) in chartPoints" :key="'c' + i" :cx="p.x" :cy="p.lineY" r="3" fill="#EF4444" />

            <text v-for="(p, i) in chartPoints" :key="'x' + i" v-show="i % xLabelStep === 0" :x="p.x" :y="svgH - 8"
                  text-anchor="middle" font-size="10" font-family="var(--font-mono)" fill="var(--kb-muted-foreground)">{{ p.dateLabel }}</text>
          </svg>
        </template>
      </div>
    </section>

    <!-- ============ 抽卡区 ============ -->
    <section v-if="active">
      <h2 class="wb-section-title">
        <Icon name="layers" :size="18" style="color: var(--mc);" />
        抽查进行中
        <span class="wb-section-hint">第 {{ index + 1 }} / {{ queue.length }} 张</span>
      </h2>
      <div class="wb-quiz-card">
        <button class="wb-quiz-close focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" title="暂停" @click="active = false">
          <Icon name="pause" :size="15" aria-hidden="true" />
        </button>

        <div
          class="wb-quiz-face focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
          :class="{ 'is-revealed': revealed }"
          role="button"
          tabindex="0"
          @click="revealed = !revealed"
          @keydown.enter.prevent="($event.target as HTMLElement).click()"
        >
          <div v-if="!revealed" class="wb-quiz-front">
            <span class="wb-quiz-label">问题</span>
            <p class="wb-quiz-text">{{ current.front }}</p>
            <span class="wb-quiz-hint"><Icon name="eye" :size="13" aria-hidden="true" /> 点击查看答案</span>
          </div>
          <div v-else class="wb-quiz-back">
            <span class="wb-quiz-label wb-quiz-label-back">答案</span>
            <p class="wb-quiz-text">{{ current.back }}</p>
          </div>
        </div>

        <div v-if="revealed" class="wb-quiz-grade">
          <p class="wb-quiz-grade-title">你记得多少？反馈以调整下次间隔</p>
          <div class="wb-quiz-grade-grid">
            <button class="wb-grade-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" :style="qStyle(0)" @click="grade(0)">
              <Icon name="x-circle" :size="16" aria-hidden="true" />
              <span class="wb-grade-label">忘了</span>
              <span class="wb-grade-hint">重置</span>
            </button>
            <button class="wb-grade-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" :style="qStyle(1)" @click="grade(1)">
              <Icon name="thumbs-down" :size="16" aria-hidden="true" />
              <span class="wb-grade-label">困难</span>
              <span class="wb-grade-hint">+1d</span>
            </button>
            <button class="wb-grade-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" :style="qStyle(2)" @click="grade(2)">
              <Icon name="thumbs-up" :size="16" aria-hidden="true" />
              <span class="wb-grade-label">一般</span>
              <span class="wb-grade-hint">×2.5</span>
            </button>
            <button class="wb-grade-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" :style="qStyle(3)" @click="grade(3)">
              <Icon name="check-circle" :size="16" aria-hidden="true" />
              <span class="wb-grade-label">容易</span>
              <span class="wb-grade-hint">×4</span>
            </button>
          </div>
          <p v-if="lastResult" class="wb-quiz-result">
            <Icon name="calendar-check" :size="13" />
            下次复习：{{ lastResult.intervalDay }} 天后
            <span v-if="lastResult.lapsed" class="wb-quiz-lapsed">（本次遗忘，间隔已重置）</span>
          </p>
        </div>
      </div>
    </section>

    <!-- ============ 空队列提示 ============ -->
    <section v-else-if="!loading && queue.length === 0" class="wb-empty">
      <div class="wb-empty-icon"><Icon name="calendar-check" :size="40" /></div>
      <h3 class="wb-empty-title">暂无待复习卡片</h3>
      <p class="wb-empty-desc">新建复习卡，或把笔记转为卡片，让记忆开始流动。</p>
      <button class="kb-btn kb-btn-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" @click="showCreate = true">
        <Icon name="plus" :size="15" aria-hidden="true" /> 新建复习卡
      </button>
    </section>

    <!-- ============ 卡片管理列表 ============ -->
    <section>
      <div class="wb-list-head">
        <h2 class="wb-section-title" style="margin: 0;">
          <Icon name="layers" :size="18" style="color: var(--mc);" />
          全部复习卡
          <span class="wb-section-hint">{{ cards.length }} 张</span>
        </h2>
        <button class="kb-btn kb-btn-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" @click="showCreate = true">
          <Icon name="plus" :size="15" aria-hidden="true" /> 新建
        </button>
      </div>

      <div v-if="cards.length === 0" class="wb-empty wb-empty-sm">
        <Icon name="layers" :size="28" style="color: var(--kb-muted-foreground);" />
        <p class="wb-empty-desc" style="margin: 0;">还没有复习卡片</p>
      </div>
      <div v-else class="wb-card-rows">
        <div v-for="c in cards" :key="c.id" class="wb-card-row">
          <div class="wb-card-row-body">
            <p class="wb-card-row-front">{{ c.front }}</p>
            <div class="wb-card-row-meta">
              <span class="wb-chip wb-chip-mono">间隔 {{ c.intervalDay }}d</span>
              <span class="wb-chip wb-chip-mono">难度 {{ c.easeFactorDecimal?.toFixed(2) }}</span>
              <span class="wb-chip wb-chip-muted">{{ c.nextReviewHint }}</span>
              <span v-if="c.suspended" class="wb-chip wb-chip-warn">已暂停</span>
            </div>
          </div>
          <div class="wb-card-row-actions">
            <button class="wb-icon-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" :title="c.suspended ? '恢复' : '暂停'" @click="suspend(c)">
              <Icon :name="c.suspended ? 'play' : 'pause'" :size="15" aria-hidden="true" />
            </button>
            <button class="wb-icon-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" title="删除" @click="remove(c)">
              <Icon name="trash-2" :size="15" aria-hidden="true" />
            </button>
          </div>
        </div>
      </div>
    </section>

    <!-- ============ 新建复习卡 Drawer ============ -->
    <div v-if="showCreate" class="wb-drawer-mask focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" role="button" tabindex="0" @click.self="showCreate = false" @keydown.enter.prevent="($event.target as HTMLElement).click()">
      <div class="wb-drawer">
        <header class="wb-drawer-head">
          <div>
            <span class="wb-eyebrow wb-eyebrow-sm">New Card</span>
            <h2 class="wb-drawer-title">新建复习卡</h2>
          </div>
          <button class="wb-icon-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" @click="showCreate = false"><Icon name="x" :size="18" aria-hidden="true" /></button>
        </header>
        <div class="wb-drawer-body">
          <div class="wb-field">
            <label class="wb-label">正面（问题/线索）<span class="wb-req">*</span></label>
            <textarea v-model="cardForm.front" class="kb-input" rows="3" placeholder="问题…"></textarea>
          </div>
          <div class="wb-field">
            <label class="wb-label">背面（答案）<span class="wb-req">*</span></label>
            <textarea v-model="cardForm.back" class="kb-input" rows="3" placeholder="答案…"></textarea>
          </div>
          <div class="wb-field">
            <label class="wb-label">类型</label>
            <select v-model="cardForm.cardType" class="kb-input">
              <option value="BASIC">问答</option>
              <option value="CLOZE">挖空</option>
              <option value="RECALL">主动回忆</option>
            </select>
          </div>
        </div>
        <footer class="wb-drawer-foot">
          <button class="kb-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" @click="showCreate = false">取消</button>
          <button class="kb-btn kb-btn-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" @click="saveCard">
            <Icon name="check" :size="15" aria-hidden="true" /> 保存
          </button>
        </footer>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { notify, confirmDialog, getApiError } from '@/utils/toast'
import './workbench-shared.css'
import {
  listReviews,
  drawReviews,
  createReview,
  deleteReview,
  gradeReview,
  toggleReviewSuspend,
  getForgettingCurve,
} from '@/api/workbench'
import type { WbReviewCardVO, WbReviewGradeResult, WbForgettingCurve } from '@/api/types'

const route = useRoute()
const themeColor = '#F59E0B'

const cards = ref<WbReviewCardVO[]>([])
const queue = ref<WbReviewCardVO[]>([])
const loading = ref(true)
const active = ref(false)
const index = ref(0)
const revealed = ref(false)
const lastResult = ref<WbReviewGradeResult | null>(null)
const showCreate = ref(false)
const cardForm = reactive({ front: '', back: '', cardType: 'BASIC' })

const loopSteps = [
  { key: 'input', num: '01', name: '输入', path: '/workbench/capture' },
  { key: 'organize', num: '02', name: '整理', path: '/workbench/notes' },
  { key: 'review', num: '03', name: '复习', path: '/workbench/review' },
  { key: 'output', num: '04', name: '输出', path: '/workbench/story' },
]

const curve = ref<WbForgettingCurve | null>(null)
const curveLoading = ref(false)
const curveDays = ref(30)
const svgW = 720
const svgH = 220
const padL = 36
const padR = 16
const padT = 16
const padB = 28

const current = ref<WbReviewCardVO>({} as WbReviewCardVO)

async function load() {
  loading.value = true
  try {
    cards.value = await listReviews({})
  } catch (e) {
    notify(getApiError(e, '加载失败'), 'error')
  } finally {
    loading.value = false
  }
}

const chartPoints = computed(() => {
  if (!curve.value) return []
  const pts = curve.value.points
  const n = pts.length
  const innerW = svgW - padL - padR
  const innerH = svgH - padT - padB
  const maxReviews = Math.max(1, ...pts.map((p) => p.reviews))
  const barW = Math.max(2, Math.min(14, innerW / n - 2))
  return pts.map((p, i) => {
    const x = padL + (n === 1 ? innerW / 2 : (innerW * i) / (n - 1))
    const barH = (p.reviews / maxReviews) * innerH
    const lineY = padT + innerH - p.lapseRate * innerH
    const dateLabel = p.date.slice(5)
    return { x, barY: padT + innerH - barH, barH, barW, lineY, dateLabel, rate: p.lapseRate }
  })
})
const yTicks = [
  { y: padT, label: '0%' },
  { y: padT + (svgH - padT - padB) * 0.25, label: '25%' },
  { y: padT + (svgH - padT - padB) * 0.5, label: '50%' },
  { y: padT + (svgH - padT - padB) * 0.75, label: '75%' },
  { y: svgH - padB, label: '100%' },
]
const xLabelStep = computed(() => Math.max(1, Math.ceil((curve.value?.points.length || 1) / 10)))

async function loadCurve() {
  curveLoading.value = true
  try {
    curve.value = await getForgettingCurve(curveDays.value)
  } catch (e) {
    notify(getApiError(e, '加载遗忘曲线失败'), 'error')
  } finally {
    curveLoading.value = false
  }
}

async function startReview() {
  lastResult.value = null
  try {
    const noteId = route.query.noteId ? Number(route.query.noteId) : undefined
    const data = await drawReviews(20)
    queue.value = noteId ? data.filter((d) => d.noteId === noteId) : data
    if (queue.value.length === 0) {
      notify('暂时没有到期的卡片', 'info')
      return
    }
    index.value = 0
    revealed.value = false
    current.value = queue.value[0]
    active.value = true
  } catch (e) {
    notify(getApiError(e, '开始失败'), 'error')
  }
}

async function grade(quality: number) {
  try {
    const res = await gradeReview(current.value.id, { quality })
    lastResult.value = res
    Object.assign(current.value, {
      intervalDay: res.intervalDay,
      easeFactorDecimal: res.easeFactor,
      suspended: 0,
    })
    await load()
    setTimeout(() => {
      if (index.value + 1 < queue.value.length) {
        index.value += 1
        current.value = queue.value[index.value]
        revealed.value = false
      } else {
        active.value = false
        notify('本轮复习完成！', 'success')
      }
    }, 900)
  } catch (e) {
    notify(getApiError(e, '评分失败'), 'error')
  }
}

async function suspend(c: WbReviewCardVO) {
  try {
    await toggleReviewSuspend(c.id)
    load()
  } catch (e) {
    notify(getApiError(e, '操作失败'), 'error')
  }
}
async function remove(c: WbReviewCardVO) {
  const ok = await confirmDialog('确认删除该复习卡？')
  if (!ok) return
  try {
    await deleteReview(c.id)
    notify('已删除', 'success')
    load()
  } catch (e) {
    notify(getApiError(e, '删除失败'), 'error')
  }
}
async function saveCard() {
  if (!cardForm.front.trim() || !cardForm.back.trim()) {
    notify('正反面均不能为空', 'warning')
    return
  }
  const noteId = route.query.noteId ? Number(route.query.noteId) : undefined
  try {
    await createReview({ ...cardForm, noteId: noteId as number | undefined })
    notify('已添加，进入今日队列', 'success')
    showCreate.value = false
    Object.assign(cardForm, { front: '', back: '', cardType: 'BASIC' })
    load()
  } catch (e) {
    notify(getApiError(e, '保存失败'), 'error')
  }
}

function qStyle(q: number) {
  const colors = ['#EF4444', '#F59E0B', '#3B6FE0', '#10B981']
  return { background: `color-mix(in srgb, ${colors[q]} 12%, transparent)`, color: colors[q], border: `1px solid color-mix(in srgb, ${colors[q]} 35%, transparent)` }
}

onMounted(() => {
  load()
  loadCurve()
  if (route.query.front) cardForm.front = String(route.query.front)
  if (route.query.back) cardForm.back = String(route.query.back)
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

/* ===== Curve Card ===== */
.wb-curve-card {
  padding: 18px;
  border-radius: var(--kb-radius-md);
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
}
.wb-curve-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}
.wb-curve-range {
  display: inline-flex;
  gap: 4px;
  padding: 3px;
  border-radius: var(--kb-radius-sm);
  background: var(--kb-background);
  border: 1px solid var(--kb-border);
}
.wb-range-btn {
  padding: 5px 12px;
  border-radius: 4px;
  background: transparent;
  color: var(--kb-muted-foreground);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;
}
.wb-range-btn.is-active {
  background: var(--mc);
  color: #fff;
  font-weight: 600;
}
.wb-curve-legend {
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: 11px;
  color: var(--kb-muted-foreground);
}
.wb-legend-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}
.wb-legend-bar {
  width: 12px; height: 10px;
  border-radius: 2px;
  background: var(--mc);
  opacity: 0.28;
}
.wb-legend-line {
  width: 14px; height: 2px;
  background: #EF4444;
}
.wb-curve-svg {
  width: 100%;
  height: auto;
}
.wb-curve-loading, .wb-curve-empty {
  height: 180px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--kb-muted-foreground);
  font-size: 13px;
}

/* ===== Quiz Card ===== */
.wb-quiz-card {
  position: relative;
  padding: 24px;
  border-radius: var(--kb-radius-md);
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  box-shadow: var(--shadow-card);
}
.wb-quiz-close {
  position: absolute;
  top: 16px; right: 16px;
  width: 30px; height: 30px;
  border-radius: var(--kb-radius-sm);
  background: transparent;
  color: var(--kb-muted-foreground);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s ease;
}
.wb-quiz-close:hover { background: var(--kb-muted); color: var(--kb-foreground); }
.wb-quiz-face {
  min-height: 160px;
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 28px 20px;
  border-radius: var(--kb-radius-md);
  background: linear-gradient(135deg, color-mix(in srgb, var(--mc) 5%, var(--kb-background)), var(--kb-background));
  border: 1px dashed var(--kb-border);
  cursor: pointer;
  transition: all 0.2s ease;
}
.wb-quiz-face:hover { border-color: var(--mc); }
.wb-quiz-face.is-revealed {
  background: linear-gradient(135deg, color-mix(in srgb, #10B981 6%, var(--kb-background)), var(--kb-background));
  border-color: #10B981;
  border-style: solid;
}
.wb-quiz-front, .wb-quiz-back {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}
.wb-quiz-label {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--mc) 14%, transparent);
  color: var(--mc);
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.05em;
}
.wb-quiz-label-back {
  background: color-mix(in srgb, #10B981 14%, transparent);
  color: #10B981;
}
.wb-quiz-text {
  font-family: var(--font-serif);
  font-size: 20px;
  font-weight: 600;
  color: var(--kb-foreground);
  line-height: 1.5;
  margin: 0;
  max-width: 560px;
}
.wb-quiz-hint {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--kb-muted-foreground);
}
.wb-quiz-grade {
  margin-top: 18px;
  text-align: center;
}
.wb-quiz-grade-title {
  font-size: 13px;
  color: var(--kb-muted-foreground);
  margin: 0 0 12px;
}
.wb-quiz-grade-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}
.wb-grade-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  padding: 12px 8px;
  border-radius: var(--kb-radius-md);
  cursor: pointer;
  transition: all 0.15s ease;
}
.wb-grade-btn:hover { transform: translateY(-2px); filter: brightness(0.96); }
.wb-grade-label {
  font-size: 13px;
  font-weight: 600;
}
.wb-grade-hint {
  font-family: var(--font-mono);
  font-size: 10px;
  opacity: 0.7;
}
.wb-quiz-result {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-top: 14px;
  padding: 6px 12px;
  border-radius: 999px;
  background: color-mix(in srgb, #10B981 10%, transparent);
  color: #10B981;
  font-size: 12px;
  font-weight: 600;
}
.wb-quiz-lapsed { color: var(--kb-warning); }

/* ===== Card rows ===== */
.wb-list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.wb-card-rows {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.wb-card-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border-radius: var(--kb-radius-md);
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  transition: all 0.15s ease;
}
.wb-card-row:hover { border-color: color-mix(in srgb, var(--mc) 30%, var(--kb-border)); }
.wb-card-row-body { flex: 1; min-width: 0; }
.wb-card-row-front {
  font-size: 14px;
  font-weight: 500;
  color: var(--kb-foreground);
  margin: 0 0 6px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.wb-card-row-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}
.wb-chip {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 2px 7px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
}
.wb-chip-mono {
  font-family: var(--font-mono);
  background: var(--kb-muted);
  color: var(--kb-muted-foreground);
}
.wb-chip-muted { background: var(--kb-muted); color: var(--kb-muted-foreground); }
.wb-chip-warn { background: color-mix(in srgb, var(--kb-warning) 14%, transparent); color: var(--kb-warning); }
.wb-card-row-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}
.wb-empty-sm { padding: 28px; }

@media (max-width: 768px) {
  .wb-quiz-grade-grid { grid-template-columns: repeat(2, 1fr); }
  .wb-quiz-text { font-size: 17px; }
}
</style>
