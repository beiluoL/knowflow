<template>
  <MiniAppShell class="phase-c-mistakes"
    title="错题本"
    subtitle="回顾做错的题目，标记掌握、反复练习薄弱点。"
    back-label="返回"
    @back="goBack"
  >
    <template #actions>
      <button
        class="shrink-0 inline-flex items-center gap-1.5 px-3 py-2 rounded-lg text-sm font-medium border border-border text-foreground bg-card hover:border-primary-300 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
        aria-label="查看学习分析"
        @click="router.push('/learning/analytics')"
      >
        <Icon name="bar-chart-2" :size="16" aria-hidden="true" />
        <span>学习分析</span>
      </button>
      <button
        @click="startPractice"
        class="shrink-0 inline-flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-medium text-white bg-primary-500 hover:bg-primary-600 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
        aria-label="配置题库错题练习" title="仅对已关联题库身份的错题组卷，其他错题继续使用原复习入口"
      >
        <Icon name="play" :size="16" aria-hidden="true" />
        <span>配置题库错题练习</span>
      </button>
    </template>

    <div class="space-y-6 animate-fade-in">

    <section class="mistake-domains flex items-center gap-2 flex-nowrap overflow-x-auto no-scrollbar" aria-label="知识领域">
      <button
        v-for="cat in categoryFilters"
        :key="cat.value"
        :aria-pressed="selectedCategory === cat.value"
        @click="handleCategoryChange(cat.value)"
        :class="[
          'shrink-0 inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm border whitespace-nowrap transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2',
          selectedCategory === cat.value
            ? 'bg-primary-500 text-white border-primary-500'
            : 'bg-card text-foreground border-border hover:border-border',
        ]"
      >
        <span
          v-if="cat.color"
          class="w-2 h-2 rounded-full shrink-0"
          :style="{ backgroundColor: cat.color }"
        ></span>
        <span>{{ cat.label }}</span>
        <span v-if="cat.count" :class="selectedCategory === cat.value ? 'opacity-80' : 'text-muted-foreground'">{{ cat.count }}</span>
      </button>
    </section>

    <!-- Phase 3H：错题多维筛选（复习状态 / 题型 / 来源 / 错误原因 / 时间范围，全部由后端确定性过滤） -->
    <section class="mistake-filters" aria-label="错题筛选区">
      <div class="mistake-status-row" role="group" aria-label="复习状态">
        <button
          v-for="t in reviewStatusFilters"
          :key="t.value"
          :aria-pressed="filters.reviewStatus === t.value"
          class="px-2.5 py-1 rounded-md text-xs whitespace-nowrap transition-colors"
          :class="filters.reviewStatus === t.value ? 'bg-primary-500 text-white' : 'text-muted-foreground hover:text-foreground'"
          @click="changeFilter('reviewStatus', t.value)"
        >{{ t.label }}</button>
      </div>

      <div class="mistake-filter-row" role="group" aria-label="错题筛选">
      <Select class="mistake-filter-control" :panel-width="240" :model-value="filters.questionType" :options="questionTypeFilters.map(t => ({ value: t.value, label: t.label }))" aria-label="错题题型" variant="pill" @update:model-value="changeFilter('questionType', $event)" />

      <Select class="mistake-filter-control" :panel-width="240" :model-value="filters.source" :options="[{ value: '', label: '全部来源' }, { value: 'QUIZ', label: '题库练习' }, { value: 'CODE', label: '算法题' }]" aria-label="错题来源" variant="pill" @update:model-value="changeFilter('source', $event)" />

      <Select class="mistake-filter-control" :panel-width="240" :model-value="filters.errorReason" :options="[{ value: '', label: '全部错误原因' }, ...errorReasonFilters.map(r => ({ value: r.value, label: r.label }))]" aria-label="错误原因" variant="pill" @update:model-value="changeFilter('errorReason', $event)" />

      <Select class="mistake-filter-control" :panel-width="240" :model-value="filters.timeRange" :options="[{ value: '', label: '全部时间' }, { value: '7', label: '近 7 天' }, { value: '30', label: '近 30 天' }, { value: '90', label: '近 90 天' }]" aria-label="错题时间范围" variant="pill" @update:model-value="changeFilter('timeRange', $event)" />

      <button
        v-if="hasActiveFilters"
        class="mistake-filter-reset" type="button"
        @click="resetFilters"
      >
        <Icon name="refresh-cw" :size="12" /> 清空筛选
      </button>
      </div>
      <div v-if="hasActiveFilters" class="mistake-filter-summary" aria-label="已选筛选条件">
        <button v-for="chip in activeFilterChips" :key="chip.key" type="button" class="mistake-filter-chip" :aria-label="'移除'+chip.label+'：'+chip.value" @click="removeFilter(chip.key)">{{chip.label}}：{{chip.value}}<Icon name="x" :size="12"/></button>
        <span class="mistake-filter-count" role="status">{{loading ? '正在筛选…' : listError ? '筛选结果加载失败' : `找到 ${total} 道错题`}} · 统计卡片为全部错题</span>
      </div>
    </section>

    <div v-if="statsError" role="alert" class="mistake-state"><p>错题统计加载失败，列表仍可使用。</p><button type="button" @click="fetchStats">重试统计</button></div>
    <section v-if="statsLoaded && !statsError" class="grid grid-cols-1 sm:grid-cols-3 gap-4">
      <div class="border rounded-[10px] p-4 bg-card border-border">
        <div class="flex items-center justify-between">
          <div>
            <div class="flex items-center gap-2 mb-1">
              <Icon name="book-open" :size="16" class="text-primary-500" aria-hidden="true" />
              <span class="text-sm text-muted-foreground">错题攻克</span>
            </div>
            <div class="flex items-baseline gap-2 mt-2">
              <!-- P2-2：正面表述「已消灭」而非「总错题」，减少挫败感 -->
              <span class="text-[28px] font-bold text-foreground">{{ stats.mastered }}</span>
              <span class="text-xs text-muted-foreground">/ {{ stats.total }} 道已攻克</span>
            </div>
          </div>
          <div class="relative w-[64px] h-[64px]" :title="`掌握率 ${masteryPercent}%`">
            <svg viewBox="0 0 64 64" class="w-full h-full -rotate-90">
              <circle
                cx="32"
                cy="32"
                r="28"
                fill="none"
                stroke="#F1f5f9"
                stroke-width="5"
              />
              <circle
                cx="32"
                cy="32"
                r="28"
                fill="none"
                stroke="url(#mistake-mastery-gradient)"
                stroke-width="5"
                stroke-linecap="round"
                :stroke-dasharray="circumference"
                :stroke-dashoffset="dashOffset"
                class="transition-[stroke-dashoffset] duration-700"
              />
              <defs>
                <linearGradient id="mistake-mastery-gradient" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%" stop-color="var(--kb-primary)" />
                  <stop offset="100%" stop-color="var(--kb-success)" />
                </linearGradient>
              </defs>
            </svg>
            <div class="absolute inset-0 flex items-center justify-center text-[13px] font-semibold text-foreground">
              {{ masteryPercent }}%
            </div>
          </div>
        </div>
        <!-- P2-2：正面鼓励文案 -->
        <p class="text-xs text-muted-foreground mt-3">
          <span v-if="stats.mastered > 0" class="text-success-500">✓ 已消灭 {{ stats.mastered }} 道错题，继续保持！</span>
          <span v-else>开始攻克第一道错题吧</span>
        </p>
      </div>
      <div class="border rounded-[10px] p-4 bg-card border-border">
        <div class="flex items-center gap-2 mb-2">
          <Icon name="check-circle" :size="16" class="text-success-500" aria-hidden="true" />
          <span class="text-sm text-muted-foreground">已掌握</span>
        </div>
        <div class="flex items-baseline gap-2">
          <span class="text-[28px] font-bold text-foreground">{{ stats.mastered }}</span>
          <span class="text-xs text-muted-foreground">道</span>
        </div>
        <div class="mt-3 space-y-1.5">
          <div class="flex items-center justify-between text-xs">
            <span class="text-muted-foreground">本周新增</span>
            <span class="text-success-500 font-medium">+{{ stats.weeklyNew }} 道</span>
          </div>
          <div class="h-1.5 rounded-full bg-gray-100 overflow-hidden">
            <div class="h-full bg-success-500 rounded-full" :style="{ width: masteryPercent + '%' }"></div>
          </div>
        </div>
      </div>
      <div class="border rounded-[10px] p-4 bg-card border-border">
        <div class="flex items-center gap-2 mb-2">
          <Icon name="clock" :size="16" class="text-warning-500" aria-hidden="true" />
          <span class="text-sm text-muted-foreground">待复习</span>
        </div>
        <div class="flex items-baseline gap-2">
          <span class="text-[28px] font-bold text-foreground">{{ stats.pending }}</span>
          <span class="text-xs text-muted-foreground">道</span>
        </div>
        <div class="mt-3 space-y-1.5">
          <div class="flex items-center justify-between text-xs">
            <span class="text-muted-foreground">今日需复习</span>
            <span class="text-warning-500 font-medium">{{ stats.dueToday }} 道</span>
          </div>
          <div class="h-1.5 rounded-full bg-gray-100 overflow-hidden">
            <div class="h-full bg-warning-500 rounded-full" :style="{ width: todayReviewPercent + '%' }"></div>
          </div>
        </div>
      </div>
    </section>

    <p v-if="actionError" role="alert" class="mistake-state">{{ actionError }}；原错题状态保留，可重新操作。</p>
    <SkeletonList v-if="loading" :rows="5" type="list" />

    <div v-else-if="listError" role="alert" class="mistake-state"><h2>{{ listError.title }}</h2><p>{{ listError.hint }}</p><button type="button" data-test="mistakes-retry" class="kb-state-retry" @click="fetchList">重试</button></div>
    <template v-else>
      <EmptyState
        v-if="mistakeList.length === 0"
        icon="check-circle"
        title="当前条件下没有错题"
        variant="success"
      >
        <p class="text-sm text-muted-foreground">可以调整筛选条件；答错的题目会按原协议记录在错题本中。</p>
      </EmptyState>

      <section v-else class="flex flex-col gap-4">
        <div
          v-for="mistake in mistakeList"
          :key="mistake.id"
          class="mistake-card border rounded-[10px] p-5 bg-card border-border"
          :style="{ '--category-color': getCategoryColor(mistake.category) }"
        >
          <div class="flex items-center justify-between gap-4 mb-3">
            <div class="mistake-question markdown-body" @click="handleCodeCopyClick" v-html="renderStudyMarkdown(mistake.question)"></div>
            <div class="flex items-center gap-2 shrink-0">
              <span v-if="mistake.questionType && questionTypeLabel(mistake.questionType)" class="inline-flex items-center px-2 py-0.5 rounded-md text-xs whitespace-nowrap bg-primary-50 text-primary-600">
                {{ questionTypeLabel(mistake.questionType) }}
              </span>
              <span class="inline-flex items-center px-2 py-0.5 rounded-md text-xs whitespace-nowrap bg-gray-100 text-muted-foreground">
                {{ mistake.category }}
              </span>
              <span
                :class="[
                  'inline-flex items-center px-2 py-0.5 rounded-md text-xs whitespace-nowrap',
                  getDifficultyStyle(mistake.difficulty),
                ]"
              >{{ getDifficultyText(mistake.difficulty) }}</span>
            </div>
          </div>
          <div class="rounded-lg p-3 mb-3 bg-red-50 border-l-[3px] border-danger-500">
            <div class="flex items-center gap-1.5 mb-1">
              <Icon name="x-circle" :size="14" class="shrink-0 text-danger-500" aria-hidden="true" />
              <span class="text-sm text-danger-500">你的答案</span>
            </div>
            <p class="text-sm text-foreground">{{ mistake.wrongAnswer }}</p>
          </div>
          <details class="mistake-answer"><summary>查看正确答案</summary>
          <div class="rounded-lg p-3 mb-3 bg-green-50 border-l-[3px] border-success-500">
            <div class="flex items-center gap-1.5 mb-1">
              <Icon name="check-circle" :size="14" class="shrink-0 text-success-500" aria-hidden="true" />
              <span class="text-sm text-success-500">正确答案</span>
            </div>
            <div class="text-sm text-foreground markdown-body" @click="handleCodeCopyClick" v-html="renderStudyMarkdown(mistake.correctAnswer || '')"></div>
          </div>
          </details>
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-3">
              <div class="flex items-center gap-1">
                <Icon name="refresh-cw" :size="14" class="text-muted-foreground" aria-hidden="true" />
                <span class="text-xs text-muted-foreground">复习 {{ mistake.reviewCount }} 次</span>
              </div>
              <span class="text-xs text-muted-foreground">{{ formatTime(mistake.createTime) }}</span>
            </div>
            <button v-if="mistake.questionId" type="button" class="mistake-repractice" @click="repractice(mistake.questionId)">重练本题</button>
            <button
              :disabled="markingIds.has(mistake.id)"
              v-if="mistake.mastered !== 1"
              @click="handleMarkMastered(mistake.id)"
              class="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-medium border text-success-500 border-success-500 bg-card hover:bg-success-50 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
            >
              <Icon name="check" :size="14" aria-hidden="true" />
              <span>{{ markingIds.has(mistake.id) ? '正在提交…' : '标记已掌握' }}</span>
            </button>
            <span v-else class="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-medium text-success-500">
              <Icon name="check-circle" :size="14" aria-hidden="true" />
              <span>已掌握</span>
            </span>
          </div>
        </div>
      </section>

      <Pagination
        v-if="total > 0"
        :page-num="pageNum"
        :page-size="pageSize"
        :total="total"
        @change="handlePageChange"
      />
    </template>
  </div>
</MiniAppShell>
</template>

<script setup lang="ts">
// 错题本：按分类筛选错题、掌握率环形进度、标记已掌握与复习提醒。
import { ref, reactive, computed, onMounted } from 'vue'
import Select from '@/components/ui/Select.vue'
import Icon from '@/components/ui/Icon.vue'
import MiniAppShell from '@/components/layout/MiniAppShell.vue'
import Pagination from '@/components/ui/Pagination.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import SkeletonList from '@/components/ui/SkeletonList.vue'
import { mistakesApi, type MistakeListParams } from '@/api/mistakes'
import type { MistakeVO, MistakeStats } from '@/api/types'
import { useRouter } from 'vue-router'
import { getApiError } from '@/utils/toast'
import { withRewardFeedback } from '@/economy/rewardToast'
import { setLastCompleted } from '@/learning/action-priority'
import { resolvePlanResourceSelector } from '@/learning/action-contract'
import { describePageError } from '@/utils/error-message'
import { handleCodeCopyClick } from '@/utils/codeCopy'
import { renderMarkdown as renderStudyMarkdown } from '@/utils/markdown'

const router = useRouter()

function goBack() {
  if (window.history.length > 1) router.back()
  else router.push('/learning/center')
}

function startPractice() {
  router.push({ name: 'ExamCompose', query: { strategy: 'MISTAKE' } })
}

const loading = ref(false)
const listError = ref<ReturnType<typeof describePageError> | null>(null)
const statsError=ref(false),statsLoaded=ref(false),actionError=ref('')
const markingIds=ref(new Set<number>())
let listGeneration=0
function repractice(id:number){router.push(resolvePlanResourceSelector('QUIZ',id,{type:'QUIZ',title:'重练本题',reason:'用户选择真实错题来源',route:'/learning/quiz'}))}
const mistakeList = ref<MistakeVO[]>([])
const stats = ref<MistakeStats>({ total: 0, mastered: 0, pending: 0, weeklyNew: 0, dueToday: 0 })
const selectedCategory = ref('all')
const pageNum = ref(1)
const pageSize = ref(5)
const total = ref(0)

// Phase 3H：多维筛选（全部走后端确定性过滤，前端不做二次裁剪）
interface MistakeFilters {
  reviewStatus: '' | 'pending' | 'mastered' | 'due'
  questionType: string
  source: string
  errorReason: string
  timeRange: string
}
const filters = reactive<MistakeFilters>({
  reviewStatus: '',
  questionType: '',
  source: '',
  errorReason: '',
  timeRange: '',
})
const reviewStatusFilters = [
  { value: '' as const, label: '全部状态' },
  { value: 'pending' as const, label: '待复习' },
  { value: 'due' as const, label: '今日到期' },
  { value: 'mastered' as const, label: '已掌握' },
]
const questionTypeFilters = [
  { value: '', label: '全部题型' },
  { value: 'SINGLE_CHOICE', label: '单选题' },
  { value: 'MULTIPLE_CHOICE', label: '多选题' },
  { value: 'TRUE_FALSE', label: '判断题' },
  { value: 'FILL_BLANK', label: '填空题' },
  { value: 'SHORT_ANSWER', label: '简答题' },
  { value: 'CODE', label: '算法题' },
]
const errorReasonFilters = [
  { value: 'WRONG_ANSWER', label: '答案错误' },
  { value: 'COMPILE_ERROR', label: '编译错误' },
  { value: 'RUNTIME_ERROR', label: '运行错误' },
  { value: 'TIME_LIMIT_EXCEEDED', label: '运行超时' },
  { value: 'MEMORY_LIMIT_EXCEEDED', label: '内存超限' },
]
const QUESTION_TYPE_LABELS: Record<string, string> = Object.fromEntries(
  questionTypeFilters.filter((t) => t.value).map((t) => [t.value, t.label]),
)
const activeFilterChips = computed(() => {
  const chips: {key: keyof MistakeFilters | 'category';label:string;value:string}[]=[];
  if(selectedCategory.value!=='all')chips.push({key:'category',label:'领域',value:selectedCategory.value});
  if(filters.reviewStatus)chips.push({key:'reviewStatus',label:'状态',value:reviewStatusFilters.find(t=>t.value===filters.reviewStatus)?.label||filters.reviewStatus});
  if(filters.questionType)chips.push({key:'questionType',label:'题型',value:QUESTION_TYPE_LABELS[filters.questionType]||filters.questionType});
  if(filters.source)chips.push({key:'source',label:'来源',value:filters.source==='QUIZ'?'题库练习':'算法题'});
  if(filters.errorReason)chips.push({key:'errorReason',label:'错误原因',value:errorReasonFilters.find(r=>r.value===filters.errorReason)?.label||filters.errorReason});
  if(filters.timeRange)chips.push({key:'timeRange',label:'时间',value:`近 ${filters.timeRange} 天`});
  return chips;
});
const hasActiveFilters = computed(() => activeFilterChips.value.length>0);
function removeFilter(key:keyof MistakeFilters|'category'){if(key==='category')handleCategoryChange('all');else changeFilter(key,'')}

/** 生成本地时区无时区后缀的 ISO（yyyy-MM-ddTHH:mm:ss），与后端 LocalDateTime 解析对齐。 */
function isoLocal(daysAgo: number): string {
  const d = new Date(Date.now() - daysAgo * 24 * 60 * 60 * 1000)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function changeFilter<K extends keyof MistakeFilters>(key: K, value: MistakeFilters[K]) {
  filters[key] = value
  pageNum.value = 1
  fetchList()
}

function resetFilters() {
  selectedCategory.value = 'all'
  filters.reviewStatus = ''
  filters.questionType = ''
  filters.source = ''
  filters.errorReason = ''
  filters.timeRange = ''
  pageNum.value = 1
  fetchList()
}

const categoryFilters = ref([
  { value: 'all', label: '全部', count: 0, color: '' },
  { value: 'AI', label: 'AI', count: 0, color: 'rgba(139, 92, 246, 0.8)' },
  { value: '前端', label: '前端', count: 0, color: 'rgba(59, 111, 224, 0.8)' },
  { value: '算法', label: '算法', count: 0, color: 'var(--kb-warning)' },
  { value: '后端', label: '后端', count: 0, color: 'rgba(16, 185, 129, 0.8)' },
])

const masteryPercent = computed(() => {
  if (stats.value.total === 0) return 0
  return Math.round((stats.value.mastered / stats.value.total) * 100)
})

// 圆环周长（半径 28），用于 SVG 进度环
const circumference = 2 * Math.PI * 28

// 掌握率对应的虚线偏移：未掌握部分即为空白
const dashOffset = computed(() => {
  return circumference * (1 - masteryPercent.value / 100)
})

const todayReviewPercent = computed(() => {
  if (stats.value.pending === 0) return 0
  // 今日需复习占待复习比例（dueToday ≤ pending，真实数据）
  return Math.min(100, Math.round((stats.value.dueToday / stats.value.pending) * 100))
})

async function fetchStats() {
  statsError.value=false
  try {
    stats.value = await mistakesApi.stats()
    statsLoaded.value=true
  } catch {
    statsError.value=true
  }
}

async function fetchList() {
  const generation=++listGeneration
  listError.value=null
  loading.value = true
  try {
    const params: MistakeListParams = {
      category: selectedCategory.value === 'all' ? undefined : selectedCategory.value,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      reviewStatus: filters.reviewStatus || undefined,
      questionType: filters.questionType || undefined,
      sources: filters.source ? [filters.source] : undefined,
      errorReason: filters.errorReason || undefined,
      from: filters.timeRange ? isoLocal(Number(filters.timeRange)) : undefined,
    }
    const res = await mistakesApi.list(params)
    if(generation!==listGeneration)return
    mistakeList.value = res.records
    total.value = res.total
  } catch (e) {
    if(generation!==listGeneration)return
    listError.value=describePageError(e,{subject:'错题列表加载失败'})
  } finally {
    if(generation===listGeneration)loading.value = false
  }
}

function handleCategoryChange(cat: string) {
  selectedCategory.value = cat
  pageNum.value = 1
  fetchList()
}

function handlePageChange(page: number) {
  pageNum.value = page
  fetchList()
}

async function handleMarkMastered(id: number) {
 if(markingIds.value.has(id))return
 markingIds.value.add(id);actionError.value=''
 try {
  await withRewardFeedback(async()=>{await mistakesApi.markMastered(id)},'错题已标记掌握')
  setLastCompleted('REVIEW');await Promise.all([fetchList(),fetchStats()])
 }catch(error){actionError.value=getApiError(error,'掌握标记失败')}
 finally{markingIds.value.delete(id)}
}

function questionTypeLabel(type?: string): string {
  if (!type) return ''
  return QUESTION_TYPE_LABELS[type] || ''
}

function getDifficultyText(diff?: number): string {
  if (diff === 1) return '简单'
  if (diff === 2) return '中等'
  if (diff === 3) return '困难'
  return '未知'
}

function getDifficultyStyle(diff?: number): string {
  if (diff === 1) return 'bg-green-50 text-success-500'
  if (diff === 2) return 'bg-yellow-50 text-warning-500'
  if (diff === 3) return 'bg-red-50 text-danger-500'
  return 'bg-gray-100 text-muted-foreground'
}

// P2-2：按分类获取色条颜色，与 categoryFilters 的颜色保持一致
// 视觉上让用户一眼区分错题类型：AI=紫、前端=蓝、算法=橙、后端=绿
function getCategoryColor(category?: string): string {
  if (!category) return 'var(--kb-muted-foreground)'
  const found = categoryFilters.value.find((c) => c.value === category || category.includes(c.value))
  return found?.color || 'var(--kb-muted-foreground)'
}

function formatTime(time?: string): string {
  if (!time) return ''
  const date = new Date(time)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const days = Math.floor(diff / (1000 * 60 * 60 * 24))
  if (days === 0) return '今天'
  if (days === 1) return '昨天'
  if (days < 7) return `${days}天前`
  if (days < 30) return `${Math.floor(days / 7)}周前`
  return `${Math.floor(days / 30)}个月前`
}

onMounted(() => {
  fetchStats()
  fetchList()
})
</script>

<style scoped>
.mistake-filters{display:flex;flex-direction:column;gap:var(--kb-space-3);min-width:0;}
.mistake-status-row{display:flex;flex-wrap:wrap;align-items:center;gap:var(--kb-space-1);}
.mistake-filter-row{display:flex;flex-wrap:wrap;align-items:center;gap:var(--kb-space-2);min-width:0;}
.mistake-filter-control{flex:0 1 auto;width:fit-content;min-width:8.75rem;max-width:100%;}
.mistake-filter-reset{display:inline-flex;align-items:center;gap:var(--kb-space-1);min-height:var(--kb-menu-item-height);padding:var(--kb-space-2);border-radius:var(--kb-radius-md);color:var(--kb-muted-foreground);font-size:var(--kb-fs-body-sm);}
.mistake-filter-reset:hover{color:var(--kb-foreground);background:var(--kb-menu-hover);}
.mistake-filter-summary{display:flex;flex-wrap:wrap;align-items:center;gap:var(--kb-space-2);font-size:var(--kb-fs-caption);}
.mistake-filter-chip{display:inline-flex;align-items:center;gap:var(--kb-space-1);max-width:100%;overflow-wrap:anywhere;padding:var(--kb-space-1) var(--kb-space-2);border:1px solid var(--kb-border);border-radius:var(--kb-radius-full);background:var(--kb-card);}
.mistake-filter-chip:hover{background:var(--kb-menu-hover);}
.mistake-filter-count{color:var(--kb-muted-foreground);}
@media(max-width:640px){.mistake-filter-control{flex:1 1 calc(50% - var(--kb-space-2));min-width:0;}.mistake-filter-summary{align-items:flex-start;}.mistake-filter-count{flex-basis:100%;}}

.no-scrollbar::-webkit-scrollbar {
  display: none;
}
.no-scrollbar {
  -ms-overflow-style: none;
  scrollbar-width: none;
}
.animate-fade-in {
  animation: fadeIn 0.3s ease-out;
}
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

/* P2-2：错题卡片左侧色条，按分类着色，便于视觉分类扫读 */
.mistake-card {
  position: relative;
  overflow: hidden;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}
.mistake-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  bottom: 0;
  width: 4px;
  background: var(--category-color, var(--kb-muted-foreground));
  border-radius: 4px 0 0 4px;
}
.mistake-card:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.06);
}
.phase-c-mistakes :deep(.mini-app-title) { font-family:inherit;font-size:var(--kb-page-title-size); }
.mistake-state { padding:var(--kb-space-5);border:1px solid var(--kb-border);border-radius:var(--kb-radius-lg);background:var(--kb-card);display:grid;gap:var(--kb-space-3); }
.mistake-question { min-width:0;flex:1;font-size:var(--kb-fs-body-lg);line-height:1.7;overflow-wrap:anywhere; }
.mistake-card > div:first-child { flex-wrap:wrap;align-items:start; }
.mistake-card > div:last-child { flex-wrap:wrap;gap:var(--kb-space-3); }
.mistake-answer summary { cursor:pointer;padding:var(--kb-space-3) 0;font-size:var(--kb-fs-body-md);font-weight:600; }
.mistake-repractice { color:var(--kb-primary);min-height:var(--kb-control-hit-size);padding:var(--kb-space-2); }
.phase-c-mistakes :is(button,select,summary):focus-visible { outline:2px solid var(--kb-ring);outline-offset:2px; }
.phase-c-mistakes :deep(button.bg-primary-500.text-white) { color:var(--kb-primary-foreground); }
.mistake-card .bg-red-50 { background:color-mix(in srgb,var(--kb-destructive) 8%,var(--kb-card)); }
.mistake-card .bg-green-50 { background:color-mix(in srgb,var(--kb-success) 8%,var(--kb-card)); }
.markdown-body :deep(pre),.markdown-body :deep(table) { max-width:100%;overflow-x:auto; }
.markdown-body :deep(img) { max-width:100%;height:auto; }
</style>
