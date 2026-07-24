<template>
  <div class="space-y-6 animate-fade-in">
    <div class="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
      <div>
        <h1 class="text-2xl font-bold text-gray-800">复习计划</h1>
        <p class="text-gray-500 mt-1">根据艾宾浩斯遗忘曲线，科学安排复习</p>
      </div>
    </div>

    <Card hoverable>
      <div class="flex flex-col md:flex-row md:items-center gap-6">
        <div class="flex-1">
          <div class="flex items-center gap-2 mb-2">
            <Icon name="calendar" :size="20" />
            <h2 class="font-semibold text-gray-800">今日复习</h2>
          </div>
          <p class="text-sm text-gray-500 mb-4">完成今日复习任务，巩固记忆效果</p>

          <div class="flex items-center gap-8 mb-4">
            <div class="text-center">
              <div class="flex items-baseline gap-1">
                <span class="text-3xl font-bold text-danger-500">{{ todayReview.total }}</span>
                <span class="text-gray-400">张</span>
              </div>
              <p class="text-xs text-gray-500 mt-1">待复习</p>
            </div>
            <div class="text-center">
              <div class="flex items-baseline gap-1">
                <span class="text-3xl font-bold text-success-500">{{ todayReview.completed }}</span>
                <span class="text-gray-400">张</span>
              </div>
              <p class="text-xs text-gray-500 mt-1">已复习</p>
            </div>
          </div>

          <Progress :percentage="todayReview.progress" label="今日进度" show-label />
        </div>

        <div class="md:border-l md:border-gray-100 md:pl-6 md:py-2">
          <Button size="lg" @click="startReview">
            <Icon name="play" :size="20" Circle />
            开始复习
          </Button>
        </div>
      </div>
    </Card>

    <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
      <div class="lg:col-span-2">
        <Card hoverable>
          <template #header>
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-2">
                <Icon name="calendar" :size="20" Days />
                <h2 class="font-semibold text-gray-800">复习日历</h2>
              </div>
              <div class="flex items-center gap-2">
                <button
                  @click="prevMonth"
                  class="p-1.5 rounded-lg hover:bg-gray-100 transition-colors"
                >
                  <Icon name="chevron-left" :size="20" />
                </button>
                <span class="text-sm font-medium text-gray-700 w-24 text-center">
                  {{ currentYear }}年{{ currentMonth }}月
                </span>
                <button
                  @click="nextMonth"
                  class="p-1.5 rounded-lg hover:bg-gray-100 transition-colors"
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
                class="text-center text-xs font-medium text-gray-400 py-2"
              >
                {{ day }}
              </div>
            </div>

            <div class="grid grid-cols-7 gap-1">
              <div
                v-for="(day, index) in calendarDays" :key="index"
                :class="[
                  'aspect-square flex flex-col items-center justify-center rounded-lg text-sm transition-all duration-200 cursor-pointer',
                  day.isCurrentMonth ? '' : 'opacity-40',
                  day.isToday ? 'bg-primary-500 text-white font-semibold' : '',
                  day.hasReview && !day.isToday ? 'hover:bg-primary-50' : '',
                  !day.hasReview && !day.isToday ? 'hover:bg-gray-50' : '',
                  selectedDate === day.dateStr ? 'ring-2 ring-primary-400 ring-offset-1' : '',
                ]"
                @click="selectDate(day)"
              >
                <span :class="day.isToday ? 'text-white' : 'text-gray-700'">{{ day.day }}</span>
                <div
                  v-if="day.hasReview && !day.isToday"
                  class="w-1.5 h-1.5 rounded-full bg-primary-500 mt-0.5"
                />
                <div
                  v-if="day.hasReview && day.isToday"
                  class="w-1.5 h-1.5 rounded-full bg-white mt-0.5"
                />
              </div>
            </div>
          </div>

          <div class="flex items-center justify-center gap-6 mt-4 pt-4 border-t border-gray-50">
            <div class="flex items-center gap-2">
              <div class="w-2 h-2 rounded-full bg-primary-500" />
              <span class="text-xs text-gray-500">有复习任务</span>
            </div>
            <div class="flex items-center gap-2">
              <div class="w-2 h-2 rounded-full bg-primary-500 ring-2 ring-primary-200" />
              <span class="text-xs text-gray-500">今天</span>
            </div>
          </div>
        </Card>
      </div>

      <div>
        <Card hoverable>
          <template #header>
            <div class="flex items-center gap-2">
              <Icon name="list" :size="20" Checks />
              <h2 class="font-semibold text-gray-800">
                {{ selectedDateLabel }}复习
              </h2>
            </div>
          </template>

          <div v-if="selectedDayCards.length > 0" class="space-y-3">
            <div
              v-for="card in selectedDayCards" :key="card.id"
              class="p-3 rounded-lg bg-gray-50 hover:bg-gray-100 transition-colors cursor-pointer"
              @click="goToFlashCards"
            >
              <div class="flex items-center gap-2 mb-2">
                <Badge variant="primary" class="text-xs">{{ card.category }}</Badge>
                <Badge :variant="getDifficultyBadgeVariant(card.difficulty)" class="text-xs">
                  {{ getDifficultyLabel(card.difficulty) }}
                </Badge>
              </div>
              <p class="text-sm text-gray-700 font-medium line-clamp-2">{{ card.question }}</p>
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
            <p class="text-sm text-gray-500">暂无复习任务</p>
            <p class="text-xs text-gray-400 mt-1">去学习新内容吧~</p>
          </div>
        </Card>
      </div>
    </div>

    <Card hoverable>
      <template #header>
        <div class="flex items-center gap-2">
          <Icon name="clock" :size="20" />
          <h2 class="font-semibold text-gray-800">即将到来的复习</h2>
        </div>
      </template>

      <div class="space-y-4">
        <div
          v-for="day in upcomingReviews" :key="day.date"
          class="border-b border-gray-100 last:border-0 pb-4 last:pb-0"
        >
          <div class="flex items-center gap-3 mb-3">
            <div class="w-10 h-10 rounded-lg bg-primary-50 flex flex-col items-center justify-center flex-shrink-0">
              <span class="text-xs text-primary-400">{{ getMonth(day.date) }}月</span>
              <span class="text-sm font-bold text-primary-600">{{ getDay(day.date) }}</span>
            </div>
            <div>
              <p class="text-sm font-medium text-gray-800">{{ formatDateLabel(day.date) }}</p>
              <p class="text-xs text-gray-500">{{ day.cards.length }} 张闪卡待复习</p>
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
              class="text-xs text-gray-400 py-0.5"
            >
              +{{ day.cards.length - 3 }} 更多
            </span>
          </div>
        </div>
      </div>
    </Card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import Card from '@/components/ui/Card.vue'
import Badge from '@/components/ui/Badge.vue'
import Progress from '@/components/ui/Progress.vue'
import Button from '@/components/ui/Button.vue'
import { reviewDays, todayReview, flashCards } from '@/data/learning'

const router = useRouter()

const weekDays = ['日', '一', '二', '三', '四', '五', '六']

const today = new Date()
const currentYear = ref(today.getFullYear())
const currentMonth = ref(today.getMonth() + 1)
const selectedDate = ref(formatDateStr(today))

const reviewDates = computed(() => {
  const dates = new Set<string>()
  reviewDays.forEach((day) => dates.add(day.date))
  flashCards.forEach((card) => {
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
  const dayData = reviewDays.find((d) => d.date === selectedDate.value)
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
  return reviewDays.filter((day) => {
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
  router.push('/learning/flashcards')
}

const goToFlashCards = () => {
  router.push('/learning/flashcards')
}
</script>

<style scoped>
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
</style>
