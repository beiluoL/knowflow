<template>
  <MiniAppShell
    title="学习分析"
    :subtitle="`最近 ${windowDays} 天 · 题库 / 算法题 / 错题统一口径（离线统计，不依赖 AI）`"
    back-label="返回"
    @back="goBack"
  >
    <template #actions>
      <div class="flex items-center gap-1 rounded-lg bg-gray-50 p-0.5 border border-border">
        <button
          v-for="w in WINDOW_OPTIONS"
          :key="w"
          class="px-3 py-1.5 rounded-md text-xs whitespace-nowrap transition-colors"
          :class="windowDays === w ? 'bg-primary-500 text-white' : 'text-muted-foreground hover:text-foreground'"
          :disabled="loading"
          @click="changeWindow(w)"
        >近 {{ w }} 天</button>
      </div>
    </template>

    <!-- 加载态 -->
    <div v-if="loading" class="py-24 flex flex-col items-center gap-3">
      <Icon name="loader" :size="28" class="animate-spin text-primary-500" />
      <p class="text-sm text-muted-foreground">正在汇总学习数据…</p>
    </div>

    <!-- 错误态（统计失败不影响其它学习功能） -->
    <div v-else-if="errorMsg" class="py-24 flex flex-col items-center gap-3 text-center">
      <Icon name="alert-circle" :size="30" class="text-red-400" />
      <div>
        <p class="text-sm font-medium text-foreground">学习分析加载失败</p>
        <p class="text-xs text-muted-foreground mt-1">{{ errorMsg }}</p>
      </div>
      <button class="px-4 py-2 rounded-lg text-sm text-white bg-primary-500 hover:bg-primary-600" @click="load">重试</button>
    </div>

    <template v-else-if="data">
      <!-- 顶部三大汇总卡 -->
      <section class="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div class="border rounded-[10px] bg-card border-border p-5">
          <div class="flex items-center gap-2 mb-3">
            <Icon name="edit" :size="16" class="text-primary-500" />
            <h3 class="text-sm font-semibold text-foreground">题库答题</h3>
          </div>
          <div class="flex items-baseline gap-2">
            <span class="text-3xl font-bold text-foreground tabular-nums">{{ formatRate(data.quiz.correctRate) }}%</span>
            <span class="text-xs text-muted-foreground">正确率</span>
          </div>
          <div class="mt-3 grid grid-cols-2 gap-y-1.5 text-xs text-muted-foreground">
            <span>作答 <b class="text-foreground tabular-nums">{{ data.quiz.totalAnswered }}</b> 题</span>
            <span>已评分 <b class="text-foreground tabular-nums">{{ data.quiz.gradedCount }}</b></span>
            <span>答对 <b class="text-success-600 tabular-nums">{{ data.quiz.correctCount }}</b></span>
            <span>待评分 <b class="text-warning-600 tabular-nums">{{ data.quiz.ungradedCount }}</b></span>
            <span class="col-span-2">平均耗时
              <b class="text-foreground tabular-nums">{{ data.quiz.avgTimeCostSeconds != null ? Math.round(data.quiz.avgTimeCostSeconds) : '—' }}</b> 秒
            </span>
          </div>
          <p class="text-[11px] text-muted-foreground mt-3 leading-relaxed">
            未评分主观题不计入正确率分母，不会产生假分数。
          </p>
        </div>

        <div class="border rounded-[10px] bg-card border-border p-5">
          <div class="flex items-center gap-2 mb-3">
            <Icon name="code" :size="16" class="text-primary-500" />
            <h3 class="text-sm font-semibold text-foreground">算法题</h3>
          </div>
          <div class="flex items-baseline gap-2">
            <span class="text-3xl font-bold text-foreground tabular-nums">{{ formatRate(data.code.firstPassRate) }}%</span>
            <span class="text-xs text-muted-foreground">首次通过率</span>
          </div>
          <div class="mt-3 grid grid-cols-2 gap-y-1.5 text-xs text-muted-foreground">
            <span>正式提交 <b class="text-foreground tabular-nums">{{ data.code.totalSubmissions }}</b></span>
            <span>通过(AC) <b class="text-success-600 tabular-nums">{{ data.code.acceptedSubmissionCount }}</b></span>
            <span>尝试题目 <b class="text-foreground tabular-nums">{{ data.code.attemptedQuestions }}</b></span>
            <span>一次通过 <b class="text-foreground tabular-nums">{{ data.code.firstPassQuestions }}</b></span>
          </div>
          <p class="text-[11px] text-muted-foreground mt-3 leading-relaxed">
            Judge 系统错误不计用户错误（已剔除
            <b class="text-warning-600 tabular-nums">{{ data.code.excludedPlatformFailure }}</b> 次平台失败）。
          </p>
        </div>

        <div class="border rounded-[10px] bg-card border-border p-5">
          <div class="flex items-center gap-2 mb-3">
            <Icon name="book-open" :size="16" class="text-primary-500" />
            <h3 class="text-sm font-semibold text-foreground">错题与复习</h3>
          </div>
          <div class="flex items-baseline gap-2">
            <span class="text-3xl font-bold text-foreground tabular-nums">{{ data.mistake.pending }}</span>
            <span class="text-xs text-muted-foreground">道待复习 · 今日到期 {{ data.mistake.dueToday }}</span>
          </div>
          <div class="mt-3 grid grid-cols-2 gap-y-1.5 text-xs text-muted-foreground">
            <span>累计错题 <b class="text-foreground tabular-nums">{{ data.mistake.total }}</b></span>
            <span>已攻克 <b class="text-success-600 tabular-nums">{{ data.mistake.mastered }}</b></span>
            <span>本周新增 <b class="text-foreground tabular-nums">{{ data.mistake.weeklyNew }}</b></span>
            <span>平均复习 <b class="text-foreground tabular-nums">{{ data.mistake.avgReviewCount != null ? data.mistake.avgReviewCount.toFixed(1) : '—' }}</b> 次</span>
          </div>
          <button
            class="mt-3 text-xs text-primary-600 hover:text-primary-700 inline-flex items-center gap-1"
            @click="router.push('/mistakes')"
          >
            去错题本复习 <Icon name="chevron-right" :size="12" />
          </button>
        </div>
      </section>

      <!-- 每日趋势 -->
      <section class="mt-4 border rounded-[10px] bg-card border-border p-5">
        <h3 class="text-sm font-semibold text-foreground mb-1">每日答题趋势</h3>
        <p class="text-[11px] text-muted-foreground mb-4">柱：当日作答题量；线：当日正确率（%）。无答题的日期不连线。</p>
        <div v-if="data.dailyTrend.some((p) => p.answered > 0)" class="trend-chart">
          <svg :viewBox="`0 0 ${chartWidth} ${chartHeight}`" preserveAspectRatio="none" class="w-full h-44">
            <line v-for="g in [25, 50, 75, 100]" :key="g"
              :x1="0" :x2="chartWidth"
              :y1="chartHeight - (g / 100) * plotHeight" :y2="chartHeight - (g / 100) * plotHeight"
              stroke="var(--kb-border)" stroke-width="1" stroke-dasharray="3 3" />
            <rect v-for="(p, i) in data.dailyTrend" :key="p.date"
              :x="barX(i)" :y="barY(p.answered)"
              :width="barWidth" :height="Math.max(0, chartHeight - barY(p.answered))"
              rx="2" fill="rgba(59,111,224,0.55)" />
            <polyline
              :points="rateLinePoints"
              fill="none" stroke="var(--kb-success)" stroke-width="2"
              stroke-linejoin="round" stroke-linecap="round" vector-effect="non-scaling-stroke" />
          </svg>
          <div class="flex justify-between text-[11px] text-muted-foreground mt-1">
            <span>{{ data.dailyTrend[0]?.date?.slice(5) }}</span>
            <span>{{ data.dailyTrend[data.dailyTrend.length - 1]?.date?.slice(5) }}</span>
          </div>
        </div>
        <div v-else class="py-10 text-center text-sm text-muted-foreground">
          该时间范围内暂无答题记录，去做一组练习吧。
        </div>
      </section>

      <!-- 题型 / 知识点分布 -->
      <section class="mt-4 grid grid-cols-1 lg:grid-cols-2 gap-4">
        <div class="border rounded-[10px] bg-card border-border p-5">
          <h3 class="text-sm font-semibold text-foreground mb-4">按题型表现</h3>
          <div v-if="data.byType.length" class="space-y-3">
            <div v-for="b in data.byType" :key="b.key">
              <div class="flex justify-between text-xs mb-1">
                <span class="text-foreground">{{ b.label || b.key }}</span>
                <span class="text-muted-foreground tabular-nums">
                  {{ b.correct }}/{{ b.total }} · {{ formatRate(b.rate) }}%
                </span>
              </div>
              <div class="h-2 rounded-full bg-gray-100 overflow-hidden">
                <div class="h-full rounded-full transition-all"
                  :class="rateBarClass(b.rate)"
                  :style="{ width: `${b.rate ?? 0}%` }"></div>
              </div>
            </div>
          </div>
          <p v-else class="text-xs text-muted-foreground py-4 text-center">暂无已评分数据</p>
        </div>

        <div class="border rounded-[10px] bg-card border-border p-5">
          <h3 class="text-sm font-semibold text-foreground mb-4">按知识点表现</h3>
          <div v-if="data.byTopic.length" class="space-y-3 max-h-72 overflow-y-auto pr-1">
            <div v-for="b in data.byTopic" :key="b.key">
              <div class="flex justify-between text-xs mb-1 gap-2">
                <span class="text-foreground truncate">{{ b.label || b.key }}</span>
                <span class="text-muted-foreground tabular-nums shrink-0">
                  {{ b.correct }}/{{ b.total }} · {{ formatRate(b.rate) }}%
                </span>
              </div>
              <div class="h-2 rounded-full bg-gray-100 overflow-hidden">
                <div class="h-full rounded-full transition-all"
                  :class="rateBarClass(b.rate)"
                  :style="{ width: `${b.rate ?? 0}%` }"></div>
              </div>
            </div>
          </div>
          <p v-else class="text-xs text-muted-foreground py-4 text-center">暂无已评分数据</p>
        </div>
      </section>

      <!-- Judge 判题分布 -->
      <section v-if="data.code.verdictDistribution.length" class="mt-4 border rounded-[10px] bg-card border-border p-5">
        <h3 class="text-sm font-semibold text-foreground mb-4">算法题提交结果分布</h3>
        <div class="flex flex-wrap gap-2">
          <span v-for="b in data.code.verdictDistribution" :key="b.key"
            class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs border border-border bg-background">
            <span :class="verdictClass(b.key)" class="font-medium">{{ b.label || b.key }}</span>
            <span class="text-muted-foreground tabular-nums">{{ b.total }}</span>
          </span>
        </div>
      </section>
    </template>
  </MiniAppShell>
</template>

<script setup lang="ts">
// Phase 3H：统一学习分析页（离线口径）。
// 正确率 = 答对 / 已评分（未评分主观题剔除）；Judge 平台失败不计用户错误。
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import MiniAppShell from '@/components/layout/MiniAppShell.vue'
import Icon from '@/components/ui/Icon.vue'
import { learningApi, type LearningAnalyticsOverview } from '@/api/learning'
import { getApiError } from '@/utils/toast'

const WINDOW_OPTIONS = [7, 30, 90] as const
const router = useRouter()
const windowDays = ref<number>(30)
const loading = ref(true)
const errorMsg = ref('')
const data = ref<LearningAnalyticsOverview | null>(null)

function goBack() {
  if (window.history.length > 1) router.back()
  else router.push('/learning/center')
}

function formatRate(r: number | null | undefined): string {
  return r == null ? '—' : String(Math.round(r * 10) / 10)
}

function rateBarClass(rate: number | null | undefined): string {
  if (rate == null) return 'bg-gray-300'
  if (rate >= 80) return 'bg-success-500'
  if (rate >= 60) return 'bg-primary-500'
  if (rate >= 40) return 'bg-warning-500'
  return 'bg-red-400'
}

function verdictClass(key: string): string {
  if (key === 'ACCEPTED') return 'text-success-600'
  if (key === 'SYSTEM_ERROR' || key === 'JUDGE_UNAVAILABLE') return 'text-warning-600'
  return 'text-red-500'
}

// ---- 极简 SVG 趋势图：柱=作答题量，折线=正确率 ----
const chartWidth = 600
const chartHeight = 180
const plotHeight = 150
const maxBars = computed(() => Math.max(data.value?.dailyTrend.length || 1, 1))
const barWidth = computed(() => Math.max(4, (chartWidth / maxBars.value) * 0.55))
function barX(i: number): number {
  const slot = chartWidth / maxBars.value
  return i * slot + (slot - barWidth.value) / 2
}
function maxAnswered(): number {
  return Math.max(1, ...(data.value?.dailyTrend.map((p) => p.answered) || [1]))
}
function barY(answered: number): number {
  return chartHeight - (answered / maxAnswered()) * plotHeight
}
const rateLinePoints = computed(() => {
  const points = data.value?.dailyTrend || []
  const slot = chartWidth / Math.max(points.length, 1)
  return points
    .filter((p) => p.rate != null)
    .map((p) => {
      const i = points.indexOf(p)
      const x = i * slot + slot / 2
      const y = chartHeight - ((p.rate ?? 0) / 100) * plotHeight
      return `${x.toFixed(1)},${y.toFixed(1)}`
    })
    .join(' ')
})

async function load() {
  loading.value = true
  errorMsg.value = ''
  try {
    data.value = await learningApi.analyticsOverview(windowDays.value)
  } catch (e) {
    errorMsg.value = getApiError(e, '统计数据加载失败')
  } finally {
    loading.value = false
  }
}

function changeWindow(days: number) {
  if (days === windowDays.value) return
  windowDays.value = days
  load()
}

onMounted(load)
</script>
