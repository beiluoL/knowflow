<template>
  <div class="space-y-4 animate-fade-in">
    <div class="flex items-center justify-between flex-wrap gap-3">
      <div>
        <h1 class="kb-h1 mb-1 flex items-center gap-2" style="color: var(--kb-foreground);">
          <Icon name="repeat" :size="24" style="color: var(--kb-primary);" /> 知识复习 · 间隔重复
        </h1>
        <p class="kb-body" style="color: var(--kb-muted-foreground);">基于 SM-2 遗忘曲线自动排程，按反馈动态拉长复习间隔。</p>
      </div>
      <div class="flex items-center gap-2">
        <button class="kb-btn kb-btn-primary" @click="startReview"><Icon name="play" :size="16" /> 开始抽查</button>
      </div>
    </div>

    <!-- 遗忘曲线可视化（基于 wb_review_log 已落数据） -->
    <div class="rounded-xl border p-4" style="background: var(--kb-card); border-color: var(--kb-border);">
      <div class="flex items-center justify-between flex-wrap gap-2 mb-3">
        <div>
          <h3 class="kb-h4 flex items-center gap-2" style="color: var(--kb-foreground);">
            <Icon name="repeat" :size="18" style="color: var(--kb-primary);" /> 遗忘曲线
          </h3>
          <p class="text-[12px] mt-0.5" style="color: var(--kb-muted-foreground);">
            近 {{ curveDays }} 天复习 {{ curve?.totalReviews || 0 }} 次 · 遗忘率
            {{ ((curve?.overallLapseRate || 0) * 100).toFixed(1) }}%
          </p>
        </div>
        <div class="flex items-center gap-1.5">
          <button
            v-for="d in [14, 30, 90]"
            :key="d"
            class="px-2.5 py-1 rounded-full text-[12px] transition-colors"
            :style="curveDays === d
              ? { background: 'var(--kb-primary)', color: '#fff' }
              : { background: 'var(--kb-background)', color: 'var(--kb-muted-foreground)', border: '1px solid var(--kb-border)' }"
            @click="curveDays = d; loadCurve()"
          >{{ d }}天</button>
        </div>
      </div>
      <div v-if="curveLoading" class="h-[200px] flex items-center justify-center" style="color: var(--kb-muted-foreground);">
        <Icon name="repeat" :size="22" class="animate-spin" />
      </div>
      <div v-else-if="!curve || curve.points.length === 0" class="h-[160px] flex items-center justify-center kb-body-sm" style="color: var(--kb-muted-foreground);">
        暂无复习记录，开始复习后这里会呈现记忆巩固趋势
      </div>
      <template v-else>
        <svg :viewBox="`0 0 720 ${svgH}`" class="w-full" style="height: auto;">
          <!-- 网格线 -->
          <line v-for="g in yTicks" :key="'g' + g" :x1="padL" :y1="g.y" :x2="720 - padR" :y2="g.y"
                :stroke="'var(--kb-border)'" stroke-width="1" stroke-dasharray="3 4" />
          <text v-for="g in yTicks" :key="'gt' + g" :x="padL - 8" :y="g.y + 4" text-anchor="end"
                font-size="11" :fill="'var(--kb-muted-foreground)'">{{ g.label }}</text>

          <!-- 复习量柱状（右轴刻度） -->
          <rect v-for="(p, i) in chartPoints" :key="'b' + i" :x="p.x - barW / 2" :y="p.barY"
                :width="barW" :height="p.barH" rx="2" :fill="'var(--kb-primary)'" fill-opacity="0.28" />

          <!-- 遗忘率折线（左轴 0~100%） -->
          <polyline :points="chartPoints.map((p) => `${p.x},${p.lineY}`).join(' ')"
                    fill="none" :stroke="'var(--kb-state-error)'" stroke-width="2.5" stroke-linejoin="round" />
          <circle v-for="(p, i) in chartPoints" :key="'c' + i" :cx="p.x" :cy="p.lineY" r="3"
                  :fill="'var(--kb-state-error)'" />

          <!-- x 轴日期标签（稀疏显示） -->
          <text v-for="(p, i) in chartPoints" :key="'x' + i" v-show="i % xLabelStep === 0" :x="p.x" :y="svgH - 8"
                text-anchor="middle" font-size="10" :fill="'var(--kb-muted-foreground)'">{{ p.dateLabel }}</text>
        </svg>
        <div class="flex items-center gap-4 mt-2 text-[12px]" style="color: var(--kb-muted-foreground);">
          <span class="flex items-center gap-1.5"><span class="inline-block w-3 h-3 rounded-sm" style="background: var(--kb-primary); opacity: .28;"></span>每日复习量</span>
          <span class="flex items-center gap-1.5"><span class="inline-block w-3 h-0.5" style="background: var(--kb-state-error);"></span>每日遗忘率</span>
        </div>
      </template>
    </div>

    <!-- 抽卡区 -->
    <div v-if="active" class="rounded-xl border p-6" style="background: var(--kb-card); border-color: var(--kb-border);">
      <div class="flex items-center justify-between mb-4">
        <span class="text-[12px] px-2 py-0.5 rounded-full" style="background: var(--kb-muted); color: var(--kb-foreground);">
          第 {{ index + 1 }} / {{ queue.length }} 张
        </span>
        <button class="icon-btn" title="暂停" @click="active = false"><Icon name="pause" :size="16" /></button>
      </div>

      <div
        class="rounded-lg p-5 min-h-[140px] flex items-center justify-center text-center cursor-pointer transition-colors"
        style="background: var(--kb-background); border: 1px dashed var(--kb-border);"
        @click="revealed = !revealed"
      >
        <div v-if="!revealed">
          <p class="kb-h3" style="color: var(--kb-foreground);">{{ current.front }}</p>
          <p class="text-[12px] mt-2" style="color: var(--kb-muted-foreground);">点击查看答案</p>
        </div>
        <div v-else>
          <p class="kb-body" style="color: var(--kb-foreground);">{{ current.back }}</p>
        </div>
      </div>

      <div v-if="revealed" class="mt-4">
        <p class="kb-body-sm mb-2 text-center" style="color: var(--kb-muted-foreground);">你记得多少？点击反馈以调整下次间隔</p>
        <div class="grid grid-cols-4 gap-2">
          <button class="review-btn" :style="qStyle(0)" @click="grade(0)"><Icon name="x-circle" :size="16" /> 忘了</button>
          <button class="review-btn" :style="qStyle(1)" @click="grade(1)"><Icon name="thumbs-down" :size="16" /> 困难</button>
          <button class="review-btn" :style="qStyle(2)" @click="grade(2)"><Icon name="thumbs-up" :size="16" /> 一般</button>
          <button class="review-btn" :style="qStyle(3)" @click="grade(3)"><Icon name="check-circle" :size="16" /> 容易</button>
        </div>
        <p v-if="lastResult" class="text-[12px] mt-3 text-center" style="color: var(--kb-state-success);">
          下次复习：{{ lastResult.intervalDay }} 天后{{ lastResult.lapsed ? '（本次遗忘，间隔已重置）' : '' }}
        </p>
      </div>
    </div>

    <!-- 空队列提示 -->
    <div v-else-if="!loading && queue.length === 0" class="rounded-xl border p-8 text-center" style="background: var(--kb-card); border-color: var(--kb-border);">
      <Icon name="calendar-check" :size="40" style="color: var(--kb-muted-foreground);" />
      <p class="kb-body-sm mt-2" style="color: var(--kb-muted-foreground);">暂无待复习卡片。新建复习卡或把笔记转为卡片吧！</p>
      <button class="kb-btn kb-btn-primary mt-3" @click="showCreate = true"><Icon name="plus" :size="15" /> 新建复习卡</button>
    </div>

    <!-- 卡片管理列表 -->
    <div class="rounded-xl border p-4" style="background: var(--kb-card); border-color: var(--kb-border);">
      <div class="flex items-center justify-between mb-3">
        <h3 class="kb-h4" style="color: var(--kb-foreground);">全部复习卡（{{ cards.length }}）</h3>
        <button class="kb-btn" @click="showCreate = true"><Icon name="plus" :size="15" /> 新建</button>
      </div>
      <div v-if="cards.length === 0" class="text-center py-6 kb-body-sm" style="color: var(--kb-muted-foreground);">还没有复习卡片</div>
      <div v-else class="space-y-2">
        <div
          v-for="c in cards"
          :key="c.id"
          class="flex items-center gap-3 p-3 rounded-lg"
          style="background: var(--kb-background);"
        >
          <div class="flex-1 min-w-0">
            <p class="kb-body-sm truncate" style="color: var(--kb-foreground);">{{ c.front }}</p>
            <p class="text-[11px]" style="color: var(--kb-muted-foreground);">
              间隔 {{ c.intervalDay }}d · 难度 {{ c.easeFactorDecimal?.toFixed(2) }} · {{ c.nextReviewHint }}
              <span v-if="c.suspended" style="color: var(--kb-state-warning);">· 已暂停</span>
            </p>
          </div>
          <button class="icon-btn" :title="c.suspended ? '恢复' : '暂停'" @click="suspend(c)">
            <Icon :name="c.suspended ? 'play' : 'pause'" :size="15" />
          </button>
          <button class="icon-btn" title="删除" @click="remove(c)"><Icon name="trash-2" :size="15" /></button>
        </div>
      </div>
    </div>

    <!-- 新建复习卡 -->
    <div v-if="showCreate" class="fixed inset-0 z-40 flex" @click.self="showCreate = false">
      <div class="fixed inset-0" style="background: rgba(0,0,0,0.35);"></div>
      <div class="relative ml-auto w-full max-w-md h-full bg-[var(--kb-background)] border-l p-5 overflow-y-auto" style="border-color: var(--kb-border);">
        <div class="flex items-center justify-between mb-4">
          <h2 class="kb-h3" style="color: var(--kb-foreground);">新建复习卡</h2>
          <button class="icon-btn" @click="showCreate = false"><Icon name="x" :size="18" /></button>
        </div>
        <div class="space-y-3">
          <div>
            <label class="kb-label">正面（问题/线索）*</label>
            <textarea v-model="cardForm.front" class="kb-input" rows="3" placeholder="问题…"></textarea>
          </div>
          <div>
            <label class="kb-label">背面（答案）*</label>
            <textarea v-model="cardForm.back" class="kb-input" rows="3" placeholder="答案…"></textarea>
          </div>
          <div>
            <label class="kb-label">类型</label>
            <select v-model="cardForm.cardType" class="kb-input">
              <option value="BASIC">问答</option>
              <option value="CLOZE">挖空</option>
              <option value="RECALL">主动回忆</option>
            </select>
          </div>
        </div>
        <div class="flex justify-end gap-2 mt-5">
          <button class="kb-btn" @click="showCreate = false">取消</button>
          <button class="kb-btn kb-btn-primary" @click="saveCard">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { notify, getApiError } from '@/utils/toast'
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
const cards = ref<WbReviewCardVO[]>([])
const queue = ref<WbReviewCardVO[]>([])
const loading = ref(true)
const active = ref(false)
const index = ref(0)
const revealed = ref(false)
const lastResult = ref<WbReviewGradeResult | null>(null)
const showCreate = ref(false)
const cardForm = reactive({ front: '', back: '', cardType: 'BASIC' })

// 遗忘曲线状态
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
    notify({ type: 'error', message: getApiError(e, '加载失败') })
  } finally {
    loading.value = false
  }
}

// 计算 SVG 绘图坐标：柱状用复习量（右轴），折线用遗忘率（左轴 0~100%）
const chartPoints = computed(() => {
  if (!curve.value) return []
  const pts = curve.value.points
  const n = pts.length
  const innerW = svgW - padL - padR
  const innerH = svgH - padT - padB
  const maxReviews = Math.max(1, ...pts.map((p) => p.reviews))
  const xLabelStep = Math.max(1, Math.ceil(n / 10))
  const barW = Math.max(2, Math.min(14, innerW / n - 2))
  return pts.map((p, i) => {
    const x = padL + (n === 1 ? innerW / 2 : (innerW * i) / (n - 1))
    const barH = (p.reviews / maxReviews) * innerH
    const lineY = padT + innerH - p.lapseRate * innerH
    const dateLabel = p.date.slice(5) // MM-DD
    return { x, barY: padT + innerH - barH, barH, lineY, dateLabel, rate: p.lapseRate }
  })
})
// y 轴刻度（按百分比）
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
    notify({ type: 'error', message: getApiError(e, '加载遗忘曲线失败') })
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
      notify({ type: 'info', message: '暂时没有到期的卡片' })
      return
    }
    index.value = 0
    revealed.value = false
    current.value = queue.value[0]
    active.value = true
  } catch (e) {
    notify({ type: 'error', message: getApiError(e, '开始失败') })
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
        notify({ type: 'success', message: '本轮复习完成！' })
      }
    }, 900)
  } catch (e) {
    notify({ type: 'error', message: getApiError(e, '评分失败') })
  }
}

async function suspend(c: WbReviewCardVO) {
  try {
    await toggleReviewSuspend(c.id)
    load()
  } catch (e) {
    notify({ type: 'error', message: getApiError(e, '操作失败') })
  }
}
async function remove(c: WbReviewCardVO) {
  if (!confirm('确认删除该复习卡？')) return
  try {
    await deleteReview(c.id)
    notify({ type: 'success', message: '已删除' })
    load()
  } catch (e) {
    notify({ type: 'error', message: getApiError(e, '删除失败') })
  }
}
async function saveCard() {
  if (!cardForm.front.trim() || !cardForm.back.trim()) {
    notify({ type: 'warning', message: '正反面均不能为空' })
    return
  }
  const noteId = route.query.noteId ? Number(route.query.noteId) : undefined
  try {
    await createReview({ ...cardForm, noteId: noteId as number | undefined })
    notify({ type: 'success', message: '已添加，进入今日队列' })
    showCreate.value = false
    Object.assign(cardForm, { front: '', back: '', cardType: 'BASIC' })
    load()
  } catch (e) {
    notify({ type: 'error', message: getApiError(e, '保存失败') })
  }
}

function qStyle(q: number) {
  const colors = ['var(--kb-state-error)', 'var(--kb-state-warning)', 'var(--kb-state-info)', 'var(--kb-state-success)']
  return { background: colors[q] + '14', color: colors[q], border: '1px solid ' + colors[q] + '44' }
}

onMounted(() => {
  load()
  loadCurve()
  if (route.query.front) cardForm.front = String(route.query.front)
  if (route.query.back) cardForm.back = String(route.query.back)
})
</script>

<style scoped>
.review-btn {
  display: flex; align-items: center; justify-content: center; gap: 6px;
  padding: 10px 6px; border-radius: 10px; font-size: 13px; font-weight: 600;
  cursor: pointer; transition: all 0.15s;
}
.review-btn:hover { filter: brightness(0.97); transform: translateY(-1px); }
</style>
