<template>
  <div class="phase-c-review space-y-4 animate-fade-in">
    <PageHeader
      title="复习计划"
      description="查看服务器记录的新卡、到期卡和已保存的复习安排。"
    >
      <template v-if="contextKnowledgeName" #description>
        <p class="rp-context" data-test="review-context">
          <Icon name="book-open" :size="13" aria-hidden="true" />
          <span>正在学习：<strong>{{ contextKnowledgeName }}</strong></span>
        </p>
      </template>
      <template #actions>
        <button
          type="button"
          class="h-9 w-9 inline-flex items-center justify-center rounded-lg border transition-colors hover:bg-muted self-start md:self-auto focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-[var(--kb-ring)]"
          style="border-color: var(--kb-input);"
          :disabled="loading"
          title="刷新" aria-label="刷新复习计划"
          @click="loadFlashcards"
        >
          <Icon name="refresh-cw" :size="16" :class="loading ? 'animate-spin' : ''" style="color: var(--kb-muted-foreground);" />
        </button>
      </template>
    </PageHeader>

    <!-- Error state（UI/UX 5.1 §六：发生了什么 / 影响范围 / 怎么办 三段式，不暴露原始异常） -->
    <div
      v-if="errorDesc"
      class="rounded-xl border p-6 flex flex-col items-center justify-center gap-3"
      style="background: var(--kb-card); border-color: var(--kb-border);"
      role="alert"
      data-test="review-error"
    >
      <Icon name="alert-circle" :size="32" style="color: var(--kb-destructive);" aria-hidden="true" />
      <h3 class="rp-error-title">{{ errorDesc.title }}</h3>
      <p class="text-sm" style="color: var(--kb-muted-foreground);">{{ errorDesc.scope }}</p>
      <p class="text-sm" style="color: var(--kb-muted-foreground);">{{ errorDesc.hint }}</p>
      <button
        type="button"
        class="px-3 py-1.5 rounded-lg text-sm font-medium focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-[var(--kb-ring)]"
        style="background: var(--kb-primary); color: var(--kb-primary-foreground);"
        @click="loadFlashcards"
      >重新加载</button>
    </div>

    <!-- Loading state -->
    <template v-else-if="loading">
      <div class="rounded-xl border p-6 animate-pulse" style="background: var(--kb-card); border-color: var(--kb-border);">
        <div class="flex flex-col md:flex-row md:items-center gap-6">
          <div class="flex-1">
            <div class="flex items-center gap-2 mb-2"><div class="w-5 h-5 rounded" style="background: var(--kb-muted);"></div><div class="h-5 w-20 rounded" style="background: var(--kb-muted);"></div></div>
            <div class="h-3 w-48 rounded mb-4" style="background: var(--kb-muted);"></div>
            <div class="flex items-center gap-8 mb-4">
              <div><div class="h-8 w-12 rounded mb-1" style="background: var(--kb-muted);"></div><div class="h-3 w-10 rounded" style="background: var(--kb-muted);"></div></div>
              <div><div class="h-8 w-12 rounded mb-1" style="background: var(--kb-muted);"></div><div class="h-3 w-10 rounded" style="background: var(--kb-muted);"></div></div>
            </div>
            <div class="h-2 rounded w-full" style="background: var(--kb-muted);"></div>
          </div>
          <div class="md:border-l md:border-border md:pl-6 md:py-2">
            <div class="h-10 w-24 rounded-lg" style="background: var(--kb-muted);"></div>
          </div>
        </div>
      </div>
      <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div class="lg:col-span-2">
          <div class="rounded-xl border p-6 animate-pulse" style="background: var(--kb-card); border-color: var(--kb-border);">
            <div class="flex items-center justify-between mb-4">
              <div class="flex items-center gap-2"><div class="w-5 h-5 rounded" style="background: var(--kb-muted);"></div><div class="h-5 w-24 rounded" style="background: var(--kb-muted);"></div></div>
              <div class="flex items-center gap-2"><div class="w-8 h-8 rounded" style="background: var(--kb-muted);"></div><div class="w-20 h-5 rounded" style="background: var(--kb-muted);"></div><div class="w-8 h-8 rounded" style="background: var(--kb-muted);"></div></div>
            </div>
            <div class="grid grid-cols-7 gap-1 mb-2"><div v-for="i in 7" :key="i" class="h-6 rounded" style="background: var(--kb-muted);"></div></div>
            <div class="grid grid-cols-7 gap-1"><div v-for="i in 28" :key="i" class="h-16 rounded" style="background: var(--kb-muted);"></div></div>
          </div>
        </div>
        <div class="rounded-xl border p-6 animate-pulse" style="background: var(--kb-card); border-color: var(--kb-border);">
          <div class="h-5 w-24 rounded mb-4" style="background: var(--kb-muted);"></div>
          <div class="space-y-3"><div v-for="i in 4" :key="i" class="h-16 rounded" style="background: var(--kb-muted);"></div></div>
        </div>
      </div>
    </template>

    <!-- Content -->
    <template v-else>

    <Card hoverable>
      <div class="flex flex-col md:flex-row md:items-center gap-6">
        <div class="flex-1">
          <div class="flex items-center gap-2 mb-2">
            <Icon name="calendar" :size="22" />
            <h2 class="font-semibold text-foreground">今日复习</h2>
            <Badge variant="default" class="text-xs">服务端排程</Badge>
          </div>
          <p class="text-sm text-muted-foreground mb-4">待复习包含新卡与已到期卡，队列最多显示 100 张；提交次数来自服务端。</p>

          <div class="flex items-center gap-6 mb-4">
            <div class="text-center">
              <div class="flex items-baseline gap-1">
                <span class="text-3xl font-bold text-danger-500 tabular-nums">{{ todayReview.total }}</span>
                <span class="text-muted-foreground">张</span>
              </div>
              <p class="text-[13px] text-muted-foreground mt-1">当前可复习</p>
            </div>
            <div class="text-center">
              <div class="flex items-baseline gap-1">
                <span class="text-3xl font-bold text-success-500 tabular-nums">{{ todayReview.completed }}</span>
                <span class="text-muted-foreground">张</span>
              </div>
              <p class="text-[13px] text-muted-foreground mt-1">今日已提交</p>
            </div>
          </div>

          <p class="text-sm text-muted-foreground">已提交不等于当前队列完成率；实际保存状态可在闪卡会话中查看。</p>
        </div>

        <div class="md:border-l md:border-border md:pl-6 md:py-2">
          <Button size="lg" @click="startReview">
            <Icon name="play" :size="20" Circle />
            打开复习工作台
          </Button>
        </div>
      </div>
    </Card>

    <section class="review-queue" aria-label="服务器待复习队列">
      <h2>当前可复习的卡片</h2>
      <p v-if="dueCards.length === 0" role="status">当前没有新卡或到期卡；已安排的未来复习时间仍可在日历查看。</p>
      <ul v-else><li v-for="card in dueCards.slice(0,8)" :key="card.id"><span>{{ card.front }}</span><span>{{ card.nextReviewTime ? '已到期' : '新卡' }}</span></li></ul>
      <p class="review-queue-hint">进入工作台后，可选择“仅新卡与到期卡”。已保存评价会保留；中断会话按原本机草稿与服务端日志恢复。</p>
    </section>
    <div class="grid grid-cols-1 lg:grid-cols-3 gap-4">
      <div class="lg:col-span-2">
        <Card hoverable>
          <template #header>
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-2">
                <Icon name="calendar" :size="20" Days />
                <h2 class="font-semibold text-foreground">复习日历</h2>
              </div>
              <div class="flex items-center gap-2">
                <button
                  aria-label="上一月" @click="prevMonth"
                  class="p-1.5 rounded-lg hover:bg-gray-100 transition-colors focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-[var(--kb-ring)]"
                >
                  <Icon name="chevron-left" :size="20" />
                </button>
                <span class="text-sm font-medium text-foreground w-24 text-center tabular-nums">
                  {{ currentYear }}年{{ currentMonth }}月
                </span>
                <button
                  @click="nextMonth"
                  aria-label="下一月"
                  class="p-1.5 rounded-lg hover:bg-gray-100 transition-colors focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-[var(--kb-ring)]"
                >
                  <Icon name="chevron-right" :size="20" />
                </button>
              </div>
            </div>
          </template>

          <div class="space-y-2">
            <div class="grid grid-cols-7 gap-1 mb-2">
              <div
                v-for="day in weekDays" :key="day"
                class="text-center text-[13px] font-medium text-muted-foreground py-2"
              >
                {{ day }}
              </div>
            </div>

            <div class="grid grid-cols-7 gap-1">
              <button type="button"
                v-for="(day, index) in calendarDays" :key="index"
                :class="[
                  'aspect-square flex flex-col items-center justify-center rounded-lg text-sm transition-[background-color,box-shadow] duration-200 cursor-pointer',
                  day.isCurrentMonth ? '' : 'opacity-40',
                  day.isToday ? 'bg-primary-500 text-white font-semibold' : '',
                  day.hasReview && !day.isToday ? 'hover:bg-primary-50' : '',
                  !day.hasReview && !day.isToday ? 'hover:bg-muted' : '',
                  selectedDate === day.dateStr ? 'ring-2 ring-primary-400 ring-offset-1' : '',
                ]"
                :aria-label="day.dateStr + (day.hasReview ? ' 有已排程复习' : '')"
                :aria-pressed="selectedDate === day.dateStr"
                @click="selectDate(day)"
              >
                <span :class="day.isToday ? 'text-white' : 'text-foreground'">{{ day.day }}</span>
                <div
                  v-if="day.hasReview && !day.isToday"
                  class="w-1.5 h-1.5 rounded-full bg-primary-500 mt-0.5"
                />
                <div
                  v-if="day.hasReview && day.isToday"
                  class="w-1.5 h-1.5 rounded-full bg-card mt-0.5"
                />
              </button>
            </div>
          </div>

          <div class="flex items-center justify-center gap-4 mt-4 pt-4 border-t border-border">
            <div class="flex items-center gap-2">
              <div class="w-2 h-2 rounded-full bg-primary-500" />
              <span class="text-[13px] text-muted-foreground">有复习任务</span>
            </div>
            <div class="flex items-center gap-2">
              <div class="w-2 h-2 rounded-full bg-primary-500 ring-2 ring-primary-200" />
              <span class="text-[13px] text-muted-foreground">今天</span>
            </div>
          </div>
        </Card>
      </div>

      <div>
        <Card hoverable>
          <template #header>
            <div class="flex items-center gap-2">
              <Icon name="list" :size="20" Checks />
              <h2 class="font-semibold text-foreground">
                {{ selectedDateLabel }}复习
              </h2>
            </div>
          </template>

          <div v-if="selectedDayCards.length > 0" class="space-y-3">
            <div
              v-for="card in selectedDayCards" :key="card.id"
              class="p-3 rounded-lg bg-muted hover:bg-gray-100 transition-colors cursor-pointer"
              @click="goToFlashCards"
            >
              <div class="flex items-center gap-2 mb-2">
                <Badge variant="primary" class="text-xs">{{ card.category }}</Badge>
                <Badge :variant="getDifficultyBadgeVariant(card.difficulty)" class="text-[13px]">
                  {{ getDifficultyLabel(card.difficulty) }}
                </Badge>
              </div>
              <p class="text-sm text-foreground font-medium line-clamp-2">{{ card.question }}</p>
            </div>

            <Button block class="mt-4" @click="goToFlashCards">
              <Icon name="book-open" :size="16" />
              查看全部闪卡
            </Button>
          </div>

          <div v-else class="py-8 text-center">
            <div class="w-16 h-16 rounded-full bg-gray-100 flex items-center justify-center mx-auto mb-3">
              <Icon name="check" :size="32" Circle />
            </div>
            <p class="text-sm text-muted-foreground">暂无复习任务</p>
            <p class="text-[13px] text-muted-foreground mt-1">去学习新内容吧~</p>
          </div>
        </Card>
      </div>
    </div>

    <Card hoverable>
      <template #header>
        <div class="flex items-center gap-2">
          <Icon name="clock" :size="20" />
          <h2 class="font-semibold text-foreground">即将到来的复习</h2>
        </div>
      </template>

      <div class="space-y-4">
        <div
          v-for="day in upcomingReviews" :key="day.date"
          class="border-b border-border last:border-0 pb-4 last:pb-0"
        >
          <div class="flex items-center gap-3 mb-3">
            <div class="w-10 h-10 rounded-lg bg-primary-50 flex flex-col items-center justify-center flex-shrink-0">
              <span class="text-[13px] text-primary-400 tabular-nums">{{ getMonth(day.date) }}月</span>
              <span class="text-sm font-bold text-primary-600 tabular-nums">{{ getDay(day.date) }}</span>
            </div>
            <div>
              <p class="text-sm font-medium text-foreground">{{ formatDateLabel(day.date) }}</p>
              <p class="text-[13px] text-muted-foreground tabular-nums">{{ day.cards.length }} 张闪卡待复习</p>
            </div>
          </div>
          <div class="flex flex-wrap gap-2 ml-13">
            <Badge
              v-for="card in day.cards.slice(0, 3)" :key="card.id"
              variant="default"
              class="cursor-pointer hover:bg-gray-200 transition-colors"
              @click="goToFlashCards"
            >
              {{ card.category }}
            </Badge>
            <span
              v-if="day.cards.length > 3"
              class="text-[13px] text-muted-foreground py-0.5 tabular-nums"
            >
              +{{ day.cards.length - 3 }} 更多
            </span>
          </div>
        </div>
      </div>
    </Card>

    </template>
  </div>
</template>

<script setup lang="ts">
// 复习计划页：基于艾宾浩斯遗忘曲线展示待复习闪卡、当日进度与月历复习分布。
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import Card from '@/components/ui/Card.vue'
import Badge from '@/components/ui/Badge.vue'
import Button from '@/components/ui/Button.vue'
import { learningApi } from '@/api/learning'
import type { FlashcardVO } from '@/api/types'
import { notify } from '@/utils/toast'
// UI/UX 5.1 §六：统一错误契约（不向用户暴露 err.message / status code）
import { describePageError } from '@/utils/error-message'

const router = useRouter()
const route = useRoute()

/**
 * UI/UX 5.1 §八：本页真实消费的知识点上下文。
 * 来自 NextAction 深链（?knowledgeId=N&knowledgeName=...），并经 `LEARNING_ENTRY_CONTEXT`
 * 白名单校验后才会出现在这里 —— 无上下文时一律不渲染上下文条（不造「未知知识点」假信息）。
 */
const contextKnowledgeId = computed<number | null>(() => {
  const raw = Number(route.query.knowledgeId)
  return Number.isFinite(raw) && raw > 0 ? raw : null
})
const contextKnowledgeName = computed<string>(() =>
  typeof route.query.knowledgeName === 'string' && route.query.knowledgeName
    ? route.query.knowledgeName
    : contextKnowledgeId.value != null
      ? `知识点 #${contextKnowledgeId.value}`
      : '',
)

/** 带上下文的跳转：把「正在复习哪个知识点」带到闪卡页，避免用户重新挑一次（§八）。 */
function pushWithContext(path: string): void {
  const query: Record<string, string> = {}
  if (contextKnowledgeId.value != null) query.knowledgeId = String(contextKnowledgeId.value)
  if (contextKnowledgeName.value) query.knowledgeName = contextKnowledgeName.value
  router.push(Object.keys(query).length > 0 ? { path, query } : path)
}

const weekDays = ['日', '一', '二', '三', '四', '五', '六']

const loading = ref(false)
// UI/UX 5.1 §六：保存**原始异常对象**，由 errorDesc 统一翻译成三段式文案，
// 渲染层不再直拼 `${err.message}`（旧实现会把 "status code 502" 直接甩给用户）。
const error = ref<unknown>(null)
const errorDesc = computed(() =>
  error.value ? describePageError(error.value, { subject: '复习计划加载失败' }) : null,
)

interface ReviewDay {
  date: string
  cards: FlashCardItem[]
}

interface FlashCardItem {
  id: string
  category: string
  difficulty: 'easy' | 'medium' | 'hard'
  question: string
  answer: string
  nextReviewDate?: string
}

const flashCards = ref<FlashCardItem[]>([])
const reviewDays = ref<ReviewDay[]>([])

interface TodayReview {
  total: number
  completed: number
  progress: number
}

const todayReview = ref<TodayReview>({
  total: 0,
  completed: 0,
  progress: 0,
})

// 将后端难度数值映射为前端展示档位：2=难 / 1=中 / 其它=易
function mapDifficulty(raw?: number): 'easy' | 'medium' | 'hard' {
  if (raw === 3) return 'hard';
  if (raw === 2) return 'medium';
  return 'easy';
}

// Date-only display of the actual backend dueAt; no local scheduling fallback.
function computeNextReviewDate(c: FlashcardVO): string | undefined {
 if(!c.nextReviewTime)return undefined
 const date=new Date(c.nextReviewTime)
 return Number.isNaN(date.getTime())?undefined:formatDateStr(date)
}
const dueCards=ref<FlashcardVO[]>([])
let loadGeneration=0
async function loadFlashcards(): Promise<void> {
  const generation=++loadGeneration;
  loading.value = true;
  error.value = null;
  try {
    const [list,due,statistics]=await Promise.all([learningApi.flashcards(),learningApi.dueFlashcards(),learningApi.flashcardStatistics()]);
    if(generation!==loadGeneration)return;
    dueCards.value=due;
    flashCards.value = list.map((c) => ({
      id:String(c.id),category:c.category||'未分类',difficulty:mapDifficulty(c.difficulty),
      question:c.front||'',answer:c.back||'',nextReviewDate:computeNextReviewDate(c),
    }));
    reviewDays.value=buildReviewDays(flashCards.value);
    todayReview.value={total:due.length,completed:statistics.today.completed,progress:0};
  } catch (err) {
    if(generation!==loadGeneration)return;
    // UI/UX 5.1 §六：保存原始异常，由 errorDesc 统一翻译成三段式文案。
    // 旧写法把 `${err.message}` 直接拼进界面 —— 用户会看到 "status code 502"。
    error.value = err;
    notify('闪卡数据加载失败', 'error');
  } finally {
    if(generation===loadGeneration)loading.value = false;
  }
}

// 按复习日期将闪卡分组，取最近 7 天用于日程列表展示
function buildReviewDays(cards: FlashCardItem[]): ReviewDay[] {
  const map = new Map<string, FlashCardItem[]>();
  cards.forEach((card) => {
    const d = card.nextReviewDate;
    if (!d) return;
    if (!map.has(d)) map.set(d, []);
    map.get(d)!.push(card);
  });
  const dates = Array.from(map.keys()).sort();
  return dates.slice(0, 7).map((date) => ({ date, cards: map.get(date)! }));
}

onMounted(() => {
  void loadFlashcards();
});

const today = new Date()
const currentYear = ref(today.getFullYear())
const currentMonth = ref(today.getMonth() + 1)
const selectedDate = ref(formatDateStr(today))

const reviewDates = computed(() => {
  const dates = new Set<string>()
  reviewDays.value.forEach((day) => dates.add(day.date))
  flashCards.value.forEach((card) => {
    if (card.nextReviewDate) {
      dates.add(card.nextReviewDate)
    }
  })
  return dates
})

interface CalendarDay {
  day: number
  dateStr: string
  isCurrentMonth: boolean
  isToday: boolean
  hasReview: boolean
}

// 构建月历网格：用 42 格（6 行 ×7 列）铺满当月，并补齐上/下月相邻日期，标记有复习任务的日期
const calendarDays = computed<CalendarDay[]>(() => {
  const year = currentYear.value
  const month = currentMonth.value - 1
  const firstDay = new Date(year, month, 1)
  const lastDay = new Date(year, month + 1, 0)
  const daysInMonth = lastDay.getDate()
  const startDayOfWeek = firstDay.getDay()

  const days: CalendarDay[] = []

  const prevMonthLastDay = new Date(year, month, 0).getDate()
  for (let i = startDayOfWeek - 1; i >= 0; i--) {
    const dayNum = prevMonthLastDay - i
    const prevMonth = month === 0 ? 12 : month
    const prevYear = month === 0 ? year - 1 : year
    const dateStr = formatDateStr(new Date(prevYear, prevMonth - 1, dayNum))
    days.push({
      day: dayNum,
      dateStr,
      isCurrentMonth: false,
      isToday: false,
      hasReview: reviewDates.value.has(dateStr),
    })
  }

  for (let i = 1; i <= daysInMonth; i++) {
    const dateStr = formatDateStr(new Date(year, month, i))
    const isToday =
      i === today.getDate() &&
      month === today.getMonth() &&
      year === today.getFullYear()
    days.push({
      day: i,
      dateStr,
      isCurrentMonth: true,
      isToday,
      hasReview: reviewDates.value.has(dateStr),
    })
  }

  const remainingDays = 42 - days.length
  for (let i = 1; i <= remainingDays; i++) {
    const nextMonth = month + 1 === 12 ? 1 : month + 2
    const nextYear = month + 1 === 12 ? year + 1 : year
    const dateStr = formatDateStr(new Date(nextYear, nextMonth - 1, i))
    days.push({
      day: i,
      dateStr,
      isCurrentMonth: false,
      isToday: false,
      hasReview: reviewDates.value.has(dateStr),
    })
  }

  return days
})

const selectedDayCards = computed(() => {
  const dayData = reviewDays.value.find((d: ReviewDay) => d.date === selectedDate.value)
  return dayData?.cards || []
})

const selectedDateLabel = computed(() => {
  const todayStr = formatDateStr(today)
  if (selectedDate.value === todayStr) {
    return '今日'
  }
  const tomorrow = new Date(today)
  tomorrow.setDate(tomorrow.getDate() + 1)
  if (selectedDate.value === formatDateStr(tomorrow)) {
    return '明日'
  }
  const date = new Date(selectedDate.value)
  return `${date.getMonth() + 1}月${date.getDate()}日`
})

const upcomingReviews = computed(() => {
  return reviewDays.value.filter((day: ReviewDay) => {
    const dayDate = new Date(day.date)
    const todayDate = new Date(today.getFullYear(), today.getMonth(), today.getDate())
    return dayDate >= todayDate
  }).slice(0, 5)
})

function formatDateStr(date: Date) {
  const y = date.getFullYear()
  const m = (date.getMonth() + 1).toString().padStart(2, '0')
  const d = date.getDate().toString().padStart(2, '0')
  return `${y}-${m}-${d}`
}

function getMonth(dateStr: string) {
  return new Date(dateStr).getMonth() + 1
}

function getDay(dateStr: string) {
  return new Date(dateStr).getDate()
}

function formatDateLabel(dateStr: string) {
  const date = new Date(dateStr)
  const todayStr = formatDateStr(today)
  if (dateStr === todayStr) {
    return '今天'
  }
  const tomorrow = new Date(today)
  tomorrow.setDate(tomorrow.getDate() + 1)
  if (dateStr === formatDateStr(tomorrow)) {
    return '明天'
  }
  const weekDayNames = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  return `${date.getMonth() + 1}月${date.getDate()}日 ${weekDayNames[date.getDay()]}`
}

const getDifficultyLabel = (difficulty: string) => {
  const labels: Record<string, string> = {
    easy: '简单',
    medium: '中等',
    hard: '困难',
  }
  return labels[difficulty] || difficulty
}

const getDifficultyBadgeVariant = (difficulty: string) => {
  const variants: Record<string, 'primary' | 'success' | 'warning' | 'danger' | 'default'> = {
    easy: 'success',
    medium: 'warning',
    hard: 'danger',
  }
  return variants[difficulty] || 'default'
}

const prevMonth = () => {
  if (currentMonth.value === 1) {
    currentMonth.value = 12
    currentYear.value--
  } else {
    currentMonth.value--
  }
}

const nextMonth = () => {
  if (currentMonth.value === 12) {
    currentMonth.value = 1
    currentYear.value++
  } else {
    currentMonth.value++
  }
}

const selectDate = (day: CalendarDay) => {
  selectedDate.value = day.dateStr
}

const startReview = () => {
  pushWithContext('/learning/flashcards')
}

const goToFlashCards = () => {
  pushWithContext('/learning/flashcards')
}

</script>

<style scoped>
/* UI/UX 5.1 §八：深链知识点上下文条（与 MiniAppShell 上下文条同语义） */
.rp-context {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin: 10px 0 0;
  padding: 4px 10px;
  border-radius: 999px;
  background: var(--kb-primary-soft);
  color: var(--kb-primary);
  font-size: var(--kb-fs-caption);
  line-height: 1.4;
}

.rp-error-title {
  margin: 0;
  font-size: var(--kb-fs-body-lg);
  line-height: var(--kb-lh-body-lg);
  font-weight: 600;
  color: var(--kb-foreground);
}

.animate-fade-in {
  animation: fadeIn 0.5s ease-out;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.line-clamp-2 {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.ml-13 {
  margin-left: 52px;
}
.phase-c-review { min-width:0; }
.phase-c-review :deep(h1) { font-family:inherit;font-size:var(--kb-page-title-size); }
.review-queue { display:grid;gap:var(--kb-space-3);padding:var(--kb-space-5);border:1px solid var(--kb-border);background:var(--kb-card);border-radius:var(--kb-radius-lg); }
.review-queue h2 { font-size:var(--kb-section-title-size);font-weight:600; }
.review-queue ul { list-style:none;padding:0;margin:0;display:grid;gap:var(--kb-space-2); }
.review-queue li { display:flex;justify-content:space-between;gap:var(--kb-space-3);border-bottom:1px solid var(--kb-border);padding:var(--kb-space-2) 0; }
.review-queue li span:first-child { min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap; }
.review-queue li span+span,.review-queue-hint { color:var(--kb-muted-foreground);font-size:var(--kb-fs-body-sm); }
.phase-c-review :is(button,a):focus-visible { outline:2px solid var(--kb-ring);outline-offset:2px; }
.phase-c-review :deep(button.bg-primary-500.text-white) { color:var(--kb-primary-foreground); }
@media(max-width:640px){.phase-c-review .ml-13{margin-left:0}.phase-c-review :deep(.kb-card){min-width:0}.review-queue{padding:var(--kb-space-4)}}
</style>
