<template>
  <div class="space-y-6 animate-fade-in">
    <PageHeader
      :crumbs="[{ label: '知识库' }, { label: '错题本' }]"
      title="错题本"
      :count="stats.total"
    >
      <template #actions>
        <button
          @click="startPractice"
          class="shrink-0 inline-flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-medium text-white bg-primary-500 hover:bg-primary-600 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
          aria-label="开始错题练习"
        >
          <Icon name="play" :size="16" aria-hidden="true" />
          <span>开始错题练习</span>
        </button>
      </template>
    </PageHeader>

    <section class="flex items-center gap-2 flex-nowrap overflow-x-auto no-scrollbar">
      <button
        v-for="cat in categoryFilters"
        :key="cat.value"
        @click="handleCategoryChange(cat.value)"
        :class="[
          'shrink-0 inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm border whitespace-nowrap transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2',
          selectedCategory === cat.value
            ? 'bg-primary-500 text-white border-primary-500'
            : 'bg-white text-gray-700 border-gray-200 hover:border-gray-300',
        ]"
      >
        <span
          v-if="cat.color"
          class="w-2 h-2 rounded-full shrink-0"
          :style="{ backgroundColor: cat.color }"
        ></span>
        <span>{{ cat.label }}</span>
        <span v-if="cat.count" :class="selectedCategory === cat.value ? 'opacity-80' : 'text-gray-400'">{{ cat.count }}</span>
      </button>
    </section>

    <section class="grid grid-cols-1 sm:grid-cols-3 gap-4">
      <div class="border rounded-[10px] p-4 bg-white border-gray-200">
        <div class="flex items-center justify-between">
          <div>
            <div class="flex items-center gap-2 mb-1">
              <Icon name="book-open" :size="16" class="text-primary-500" aria-hidden="true" />
              <span class="text-sm text-gray-500">错题攻克</span>
            </div>
            <div class="flex items-baseline gap-2 mt-2">
              <!-- P2-2：正面表述「已消灭」而非「总错题」，减少挫败感 -->
              <span class="text-[28px] font-bold text-gray-800">{{ stats.mastered }}</span>
              <span class="text-xs text-gray-400">/ {{ stats.total }} 道已攻克</span>
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
                  <stop offset="0%" stop-color="#3B6FE0" />
                  <stop offset="100%" stop-color="#10B981" />
                </linearGradient>
              </defs>
            </svg>
            <div class="absolute inset-0 flex items-center justify-center text-[13px] font-semibold text-gray-700">
              {{ masteryPercent }}%
            </div>
          </div>
        </div>
        <!-- P2-2：正面鼓励文案 -->
        <p class="text-xs text-gray-400 mt-3">
          <span v-if="stats.mastered > 0" class="text-success-500">✓ 已消灭 {{ stats.mastered }} 道错题，继续保持！</span>
          <span v-else>开始攻克第一道错题吧</span>
        </p>
      </div>
      <div class="border rounded-[10px] p-4 bg-white border-gray-200">
        <div class="flex items-center gap-2 mb-2">
          <Icon name="check-circle" :size="16" class="text-success-500" aria-hidden="true" />
          <span class="text-sm text-gray-500">已掌握</span>
        </div>
        <div class="flex items-baseline gap-2">
          <span class="text-[28px] font-bold text-gray-800">{{ stats.mastered }}</span>
          <span class="text-xs text-gray-400">道</span>
        </div>
        <div class="mt-3 space-y-1.5">
          <div class="flex items-center justify-between text-xs">
            <span class="text-gray-400">本周新增</span>
            <span class="text-success-500 font-medium">+{{ stats.weeklyNew }} 道</span>
          </div>
          <div class="h-1.5 rounded-full bg-gray-100 overflow-hidden">
            <div class="h-full bg-success-500 rounded-full" :style="{ width: masteryPercent + '%' }"></div>
          </div>
        </div>
      </div>
      <div class="border rounded-[10px] p-4 bg-white border-gray-200">
        <div class="flex items-center gap-2 mb-2">
          <Icon name="clock" :size="16" class="text-warning-500" aria-hidden="true" />
          <span class="text-sm text-gray-500">待复习</span>
        </div>
        <div class="flex items-baseline gap-2">
          <span class="text-[28px] font-bold text-gray-800">{{ stats.pending }}</span>
          <span class="text-xs text-gray-400">道</span>
        </div>
        <div class="mt-3 space-y-1.5">
          <div class="flex items-center justify-between text-xs">
            <span class="text-gray-400">今日需复习</span>
            <span class="text-warning-500 font-medium">{{ stats.dueToday }} 道</span>
          </div>
          <div class="h-1.5 rounded-full bg-gray-100 overflow-hidden">
            <div class="h-full bg-warning-500 rounded-full" :style="{ width: todayReviewPercent + '%' }"></div>
          </div>
        </div>
      </div>
    </section>

    <SkeletonList v-if="loading" :rows="5" type="list" />

    <template v-else>
      <EmptyState
        v-if="mistakeList.length === 0"
        icon="check-circle"
        title="太棒了，暂无错题"
        variant="success"
      >
        <p class="text-sm text-gray-500">继续保持，做题时遇到的错题会自动收录到这里</p>
      </EmptyState>

      <section v-else class="flex flex-col gap-4">
        <div
          v-for="mistake in mistakeList"
          :key="mistake.id"
          class="mistake-card border rounded-[10px] p-5 bg-white border-gray-200"
          :style="{ '--category-color': getCategoryColor(mistake.category) }"
        >
          <div class="flex items-center justify-between gap-4 mb-3">
            <p class="text-[15px] font-semibold truncate min-w-0 text-gray-800">{{ mistake.question }}</p>
            <div class="flex items-center gap-2 shrink-0">
              <span class="inline-flex items-center px-2 py-0.5 rounded-md text-xs whitespace-nowrap bg-gray-100 text-gray-500">
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
            <p class="text-sm text-gray-700">{{ mistake.wrongAnswer }}</p>
          </div>
          <div class="rounded-lg p-3 mb-3 bg-green-50 border-l-[3px] border-success-500">
            <div class="flex items-center gap-1.5 mb-1">
              <Icon name="check-circle" :size="14" class="shrink-0 text-success-500" aria-hidden="true" />
              <span class="text-sm text-success-500">正确答案</span>
            </div>
            <p class="text-sm text-gray-700">{{ mistake.correctAnswer }}</p>
          </div>
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-3">
              <div class="flex items-center gap-1">
                <Icon name="refresh-cw" :size="14" class="text-gray-400" aria-hidden="true" />
                <span class="text-xs text-gray-500">复习 {{ mistake.reviewCount }} 次</span>
              </div>
              <span class="text-xs text-gray-400">{{ formatTime(mistake.createTime) }}</span>
            </div>
            <button
              v-if="mistake.mastered !== 1"
              @click="handleMarkMastered(mistake.id)"
              class="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-medium border text-success-500 border-success-500 bg-white hover:bg-success-50 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
            >
              <Icon name="check" :size="14" aria-hidden="true" />
              <span>标记已掌握</span>
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
</template>

<script setup lang="ts">
// 错题本：按分类筛选错题、掌握率环形进度、标记已掌握与复习提醒。
import { ref, computed, onMounted } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import Pagination from '@/components/ui/Pagination.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import SkeletonList from '@/components/ui/SkeletonList.vue'
import { mistakesApi } from '@/api/mistakes'
import type { MistakeVO, MistakeStats } from '@/api/types'
import { useRouter } from 'vue-router'
import { notify, getApiError } from '@/utils/toast'

const router = useRouter()

function startPractice() {
  router.push('/learning/flashcards')
}

const loading = ref(false)
const mistakeList = ref<MistakeVO[]>([])
const stats = ref<MistakeStats>({ total: 0, mastered: 0, pending: 0, weeklyNew: 0, dueToday: 0 })
const selectedCategory = ref('all')
const pageNum = ref(1)
const pageSize = ref(5)
const total = ref(0)

const categoryFilters = ref([
  { value: 'all', label: '全部', count: 0, color: '' },
  { value: 'AI', label: 'AI', count: 0, color: 'rgba(139, 92, 246, 0.8)' },
  { value: '前端', label: '前端', count: 0, color: 'rgba(59, 111, 224, 0.8)' },
  { value: '算法', label: '算法', count: 0, color: '#F59E0B' },
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
  try {
    stats.value = await mistakesApi.stats()
  } catch {
    // 统计加载失败静默处理，不影响列表展示
  }
}

async function fetchList() {
  loading.value = true
  try {
    const res = await mistakesApi.list({
      category: selectedCategory.value === 'all' ? undefined : selectedCategory.value,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    mistakeList.value = res.records
    total.value = res.total
  } catch (e) {
    notify(getApiError(e, '错题列表加载失败'), 'error')
  } finally {
    loading.value = false
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

function handleMarkMastered(id: number) {
  mistakesApi.markMastered(id).then(() => {
    fetchList()
    fetchStats()
  })
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
  return 'bg-gray-100 text-gray-500'
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
</style>
