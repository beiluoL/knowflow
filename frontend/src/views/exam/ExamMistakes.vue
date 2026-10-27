<template>
  <MiniAppShell
    title="考试错题本"
    subtitle="仅收录真实答错的考试题目；待 AI 评分的主观题不会提前计入"
    back-label="返回考试中心"
    @back="goBack"
  >
    <template #actions>
      <button
        class="shrink-0 inline-flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-medium text-white bg-primary-500 hover:bg-primary-600 disabled:bg-gray-200 disabled:text-muted-foreground transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
        :disabled="strengthening || !total"
        @click="strengthen"
      >
        <Icon :name="strengthening ? 'loader' : 'target'" :size="16" :class="strengthening ? 'animate-spin' : ''" />
        <span>{{ strengthening ? '组卷中…' : '错题强化卷' }}</span>
      </button>
    </template>

    <div class="space-y-5 animate-fade-in">
      <!-- 筛选 -->
      <section class="border rounded-[10px] bg-card border-border p-4 space-y-3">
        <div class="flex items-center gap-2 flex-wrap">
          <button
            class="px-3 py-1.5 rounded-lg text-xs border transition-colors"
            :class="filters.topic === '' ? 'bg-primary-500 text-white border-primary-500' : 'bg-background border-border text-muted-foreground hover:border-primary-300'"
            @click="setTopic('')"
          >
            全部知识点
          </button>
          <button
            v-for="tp in topicChips"
            :key="tp.key"
            class="px-2.5 py-1.5 rounded-lg text-xs border transition-colors"
            :class="filters.topic === tp.key ? 'bg-primary-50 text-primary-600 border-primary-300' : 'bg-background border-border text-muted-foreground hover:border-primary-300'"
            @click="setTopic(tp.key)"
          >
            {{ tp.label }}
          </button>
        </div>
        <div class="flex items-center gap-3 flex-wrap">
          <div class="flex items-center gap-1.5">
            <button
              v-for="s in statusFilters"
              :key="s.value"
              class="px-2.5 py-1 rounded-lg text-xs border"
              :class="filters.mastered === s.value ? 'bg-primary-50 text-primary-600 border-primary-300' : 'bg-background border-border text-muted-foreground'"
              @click="filters.mastered = s.value; reload()"
            >
              {{ s.label }}
            </button>
          </div>
          <div class="ml-auto w-full sm:w-[280px]">
            <DateRangePicker
              v-model="dateRange"
              aria-label="按错题收录时间筛选"
              placeholder="开始日期 — 结束日期"
              clearable
            />
          </div>
        </div>
      </section>

      <!-- 列表 -->
      <div v-if="loading" class="space-y-3">
        <div v-for="i in 4" :key="i" class="border rounded-[10px] p-5 bg-card border-border animate-pulse">
          <div class="h-4 w-1/3 bg-gray-100 rounded mb-3"></div>
          <div class="h-3 w-2/3 bg-gray-100 rounded"></div>
        </div>
      </div>

      <div v-else-if="error" class="border border-red-200 bg-red-50 rounded-xl p-8 text-center">
        <Icon name="alert-circle" :size="28" class="text-red-400 mx-auto mb-3" />
        <p class="text-sm text-red-600 font-medium mb-1">错题加载失败</p>
        <p class="text-xs text-muted-foreground mb-4">{{ error }}</p>
        <button class="px-4 py-2 rounded-lg text-sm text-white bg-primary-500" @click="load">重试</button>
      </div>

      <div v-else-if="!records.length" class="border rounded-[10px] bg-card border-border p-12 text-center">
        <Icon name="check-circle" :size="36" class="text-success-300 mx-auto mb-3" />
        <p class="text-sm font-medium text-foreground mb-1">
          {{ total === 0 ? '当前筛选下没有错题' : '还没有考试错题' }}
        </p>
        <p class="text-xs text-muted-foreground">完成一场模拟考，答错的题目会自动收录到这里</p>
        <button class="mt-4 px-4 py-2 rounded-lg text-sm text-white bg-primary-500 hover:bg-primary-600" @click="goCenter">
          去考试中心
        </button>
      </div>

      <template v-else>
        <ul class="space-y-3">
          <li
            v-for="m in records"
            :key="m.id"
            class="border rounded-[10px] bg-card border-border p-5"
            :class="{ 'opacity-70': m.mastered }"
          >
            <div class="flex items-center gap-2 flex-wrap mb-2">
              <span class="text-[11px] px-1.5 py-0.5 rounded bg-primary-50 text-primary-600">
                {{ topicLabel(m.topic) }}
              </span>
              <span v-if="m.difficulty" class="text-[11px] px-1.5 py-0.5 rounded bg-gray-100 text-muted-foreground">
                难度 {{ m.difficulty }}
              </span>
              <span v-if="m.mastered" class="text-[11px] px-1.5 py-0.5 rounded bg-success-50 text-success-600 inline-flex items-center gap-0.5">
                <Icon name="check" :size="11" /> 已掌握
              </span>
              <span v-if="m.reviewCount" class="text-[11px] text-muted-foreground">复习 {{ m.reviewCount }} 次</span>
              <span class="ml-auto text-[11px] text-muted-foreground">
                {{ fmtTime(m.createTime) }}
              </span>
            </div>
            <p class="text-sm font-medium text-foreground leading-6 mb-2">{{ stripHtml(m.question) }}</p>
            <div v-if="!m.mastered || m.wrongAnswer" class="grid grid-cols-1 sm:grid-cols-2 gap-2 mb-3">
              <div class="rounded-lg bg-red-50/60 px-3 py-2">
                <p class="text-[11px] text-red-400 mb-0.5">考试作答</p>
                <p class="text-xs text-red-600 leading-5">{{ m.wrongAnswer || '（未作答）' }}</p>
              </div>
              <div v-if="m.correctAnswer" class="rounded-lg bg-success-50/60 px-3 py-2">
                <p class="text-[11px] text-success-500 mb-0.5">正确答案</p>
                <p class="text-xs text-success-700 leading-5">{{ m.correctAnswer }}</p>
              </div>
            </div>
            <div class="flex items-center gap-2">
              <button
                v-if="!m.mastered"
                class="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs text-white bg-success-500 hover:bg-success-600 transition-colors"
                @click="master(m)"
              >
                <Icon name="check" :size="13" /> 标记已掌握
              </button>
              <button
                v-else
                class="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs border border-border text-muted-foreground hover:border-primary-300"
                disabled
              >
                <Icon name="check-circle" :size="13" /> 已掌握
              </button>
            </div>
          </li>
        </ul>

        <!-- 分页 -->
        <div v-if="pages > 1" class="flex items-center justify-center gap-2 pt-2">
          <button
            class="px-3 py-1.5 rounded-lg text-xs border border-border bg-card text-foreground disabled:opacity-40"
            :disabled="pageNum <= 1"
            @click="changePage(pageNum - 1)"
          >
            上一页
          </button>
          <span class="text-xs text-muted-foreground">{{ pageNum }} / {{ pages }}（共 {{ total }} 条）</span>
          <button
            class="px-3 py-1.5 rounded-lg text-xs border border-border bg-card text-foreground disabled:opacity-40"
            :disabled="pageNum >= pages"
            @click="changePage(pageNum + 1)"
          >
            下一页
          </button>
        </div>
      </template>
    </div>
  </MiniAppShell>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import MiniAppShell from '@/components/layout/MiniAppShell.vue'
import Icon from '@/components/ui/Icon.vue'
import DateRangePicker from '@/components/ui/DateRangePicker.vue'
import type { DateRangeValue } from '@/components/ui/date-time-types'
import { examApi, type ExamMistake, TOPIC_LABELS } from '@/api/exam'
import { notify, getApiError } from '@/utils/toast'

const router = useRouter()
const loading = ref(true)
const error = ref('')
const records = ref<ExamMistake[]>([])
const total = ref(0)
const pages = ref(1)
const pageNum = ref(1)
const pageSize = 20
const strengthening = ref(false)

const filters = reactive({
  topic: '',
  mastered: '' as '' | '0' | '1',
  from: '',
  to: '',
})

const statusFilters = [
  { value: '' as const, label: '全部' },
  { value: '0' as const, label: '待复习' },
  { value: '1' as const, label: '已掌握' },
]

const topicChips = Object.entries(TOPIC_LABELS).map(([key, label]) => ({ key, label }))

const fromDate = computed(() => filters.from || undefined)
/**
 * 后端 `/exam/mistakes` 用 `LocalDate.parse(to)` 并按 `to + 1 天 00:00` 取上界，
 * 因此 `to` 必须是纯 `yyyy-MM-dd`；拼 `23:59:59` 会解析失败。
 */
const toDate = computed(() => filters.to || undefined)

const dateRange = computed<DateRangeValue>({
  get: () => ({ start: filters.from, end: filters.to }),
  set: (value) => {
    if (value.start === filters.from && value.end === filters.to) return
    filters.from = value.start
    filters.to = value.end
    reload()
  },
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const page = await examApi.mistakes({
      topic: filters.topic || undefined,
      mastered: filters.mastered === '' ? undefined : Number(filters.mastered),
      from: fromDate.value,
      to: toDate.value,
      pageNum: pageNum.value,
      pageSize,
    })
    records.value = page.records || []
    total.value = page.total || 0
    pages.value = page.pages || 1
  } catch (e) {
    error.value = getApiError(e, '加载失败')
  } finally {
    loading.value = false
  }
}

function reload() {
  pageNum.value = 1
  load()
}

function setTopic(t: string) {
  filters.topic = t
  reload()
}

function changePage(p: number) {
  pageNum.value = p
  load()
}

async function master(m: ExamMistake) {
  try {
    await examApi.masterMistake(m.id)
    m.mastered = true
    notify('已标记掌握，继续保持！', 'success')
  } catch (e) {
    notify(getApiError(e, '操作失败'), 'error')
  }
}

async function strengthen() {
  strengthening.value = true
  try {
    const paper = await examApi.compose({
      name: '错题强化卷',
      topics: filters.topic ? [filters.topic] : [],
      strategy: 'MISTAKE',
      typeDistribution: {},
      totalScore: 100,
      durationMinutes: 20,
      passScore: 60,
      mode: 'PRACTICE',
      multiPolicy: 'PARTIAL_CREDIT',
    })
    const attempt = await examApi.startAttempt(paper.id)
    router.replace(`/exam/take/${attempt.attemptId}`)
  } catch (e) {
    notify(getApiError(e, '错题不足，无法生成强化卷'), 'error', 4000)
  } finally {
    strengthening.value = false
  }
}

function topicLabel(t?: string): string {
  return (t && TOPIC_LABELS[t]) || t || '综合'
}

function stripHtml(html?: string): string {
  return (html || '').replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim()
}

function fmtTime(t?: string): string {
  return t ? t.replace('T', ' ').slice(0, 16) : ''
}

function goBack() {
  if (window.history.length > 1) router.back()
  else router.push('/exam')
}

function goCenter() {
  router.push('/exam')
}

onMounted(load)
</script>
