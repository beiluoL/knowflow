<template>
  <div class="animate-fade-in space-y-5">
    <!-- Page Header -->
    <div class="flex items-center justify-between flex-wrap gap-3">
      <div>
        <h1 class="kb-h1">学习中心</h1>
        <p class="kb-body-sm mt-1">追踪你的学习进度，发现学习规律</p>
      </div>
      <span
        class="text-xs px-2 py-1 rounded"
        style="background: var(--kb-muted); color: var(--kb-muted-foreground);"
      >累计数据 · 来自学习统计接口</span>
    </div>

    <!-- Error -->
    <div v-if="error" class="rounded-lg border p-8 flex flex-col items-center justify-center gap-3" style="background: var(--kb-card); border-color: var(--kb-border);">
      <Icon name="alert-circle" :size="32" style="color: var(--kb-destructive);" />
      <p class="text-sm" style="color: var(--kb-muted-foreground);">{{ error }}</p>
      <button
        type="button"
        class="px-3 py-1.5 rounded-lg text-xs font-medium"
        style="background: var(--kb-primary); color: var(--kb-primary-foreground);"
        @click="loadData"
      >重新加载</button>
    </div>

    <!-- Loading skeleton -->
    <template v-else-if="loading">
      <section class="grid grid-cols-2 lg:grid-cols-4 gap-3">
        <div
          v-for="i in 4"
          :key="i"
          class="rounded-lg border p-4 animate-pulse"
          style="background: var(--kb-card); border-color: var(--kb-border);"
        >
          <div class="flex items-center gap-2 mb-2">
            <div class="w-7 h-7 rounded-md" style="background: var(--kb-muted);"></div>
            <div class="h-3 rounded w-16" style="background: var(--kb-muted);"></div>
          </div>
          <div class="h-6 rounded w-20 mb-2" style="background: var(--kb-muted);"></div>
        </div>
      </section>
      <div class="rounded-lg border p-5 animate-pulse" style="background: var(--kb-card); border-color: var(--kb-border);">
        <div class="h-4 rounded w-24 mb-4" style="background: var(--kb-muted);"></div>
        <div class="grid grid-cols-7 gap-1.5">
          <div v-for="i in 35" :key="i" class="aspect-square rounded-sm" style="background: var(--kb-muted);"></div>
        </div>
      </div>
    </template>

    <!-- Content -->
    <template v-else>
      <!-- Summary stats 4 grid -->
      <section class="grid grid-cols-2 lg:grid-cols-4 gap-3">
        <div
          v-for="stat in overviewStats"
          :key="stat.label"
          class="rounded-lg border p-4"
          style="background: var(--kb-card); border-color: var(--kb-border);"
        >
          <div class="flex items-center gap-1.5 mb-1.5">
            <Icon :name="stat.icon" :size="14" :style="`color: ${stat.iconColor};`" />
            <span class="text-xs" style="color: var(--kb-muted-foreground);">{{ stat.label }}</span>
          </div>
          <div class="flex items-baseline gap-1.5">
            <span class="text-xl font-bold whitespace-nowrap" style="color: var(--kb-foreground);">
              {{ stat.value }}
            </span>
            <span class="text-xs whitespace-nowrap" style="color: var(--kb-muted-foreground);">{{ stat.unit }}</span>
            <Icon
              v-if="stat.trendUp"
              name="trending-up"
              :size="14"
              class="ml-auto"
              style="color: var(--kb-accent);"
            />
          </div>
        </div>
      </section>

      <!-- Weekly heatmap card -->
      <section class="rounded-lg border p-5" style="background: var(--kb-card); border-color: var(--kb-border);">
        <div class="flex items-center justify-between mb-3">
          <h3 class="text-[15px] font-semibold" style="color: var(--kb-foreground);">学习热力图</h3>
          <span class="text-xs" style="color: var(--kb-muted-foreground);">最近 5 周</span>
        </div>
        <div class="grid grid-cols-7 gap-1.5 mb-3">
          <span class="text-[10px] text-center" style="color: var(--kb-muted-foreground);">一</span>
          <span class="text-[10px] text-center" style="color: var(--kb-muted-foreground);">二</span>
          <span class="text-[10px] text-center" style="color: var(--kb-muted-foreground);">三</span>
          <span class="text-[10px] text-center" style="color: var(--kb-muted-foreground);">四</span>
          <span class="text-[10px] text-center" style="color: var(--kb-muted-foreground);">五</span>
          <span class="text-[10px] text-center" style="color: var(--kb-muted-foreground);">六</span>
          <span class="text-[10px] text-center" style="color: var(--kb-muted-foreground);">日</span>
          <template v-for="(level, idx) in heatmapData" :key="idx">
            <div
              class="aspect-square rounded-sm transition-transform hover:scale-110"
              :class="`heat-${level}`"
              :title="`学习强度 ${level}`"
            ></div>
          </template>
        </div>
        <div class="flex items-center justify-end gap-1.5">
          <span class="text-[10px]" style="color: var(--kb-muted-foreground);">少</span>
          <div class="h-2.5 w-2.5 rounded-sm heat-0"></div>
          <div class="h-2.5 w-2.5 rounded-sm heat-1"></div>
          <div class="h-2.5 w-2.5 rounded-sm heat-2"></div>
          <div class="h-2.5 w-2.5 rounded-sm heat-3"></div>
          <span class="text-[10px]" style="color: var(--kb-muted-foreground);">多</span>
        </div>
        <p v-if="heatmapEmpty" class="text-xs mt-2" style="color: var(--kb-muted-foreground);">
          暂无番茄钟记录，开始专注后会显示学习热力
        </p>
      </section>

      <!-- Subject breakdown card -->
      <section class="rounded-lg border p-5" style="background: var(--kb-card); border-color: var(--kb-border);">
        <h3 class="text-[15px] font-semibold mb-4" style="color: var(--kb-foreground);">各分类闪卡分布</h3>
        <div v-if="subjectStats.length > 0" class="space-y-3">
          <div v-for="subject in subjectStats" :key="subject.name">
            <div class="flex justify-between items-center mb-1.5">
              <div class="flex items-center gap-2">
                <span
                  class="w-2.5 h-2.5 rounded-full"
                  :style="`background: ${subject.color};`"
                ></span>
                <span class="text-sm" style="color: var(--kb-foreground);">{{ subject.name }}</span>
              </div>
              <span class="text-xs font-medium" style="color: var(--kb-muted-foreground);">{{ subject.hours }} 张</span>
            </div>
            <div class="h-2 rounded-full overflow-hidden" style="background: var(--kb-muted);">
              <div
                class="h-2 rounded-full transition-all duration-500"
                :style="`width: ${subject.percent}%; background: ${subject.color};`"
              ></div>
            </div>
          </div>
        </div>
        <p v-else class="text-sm" style="color: var(--kb-muted-foreground);">暂无闪卡数据</p>
      </section>

      <!-- Recent learning log -->
      <section class="rounded-lg border p-5" style="background: var(--kb-card); border-color: var(--kb-border);">
        <h3 class="text-[15px] font-semibold mb-2" style="color: var(--kb-foreground);">最近学习</h3>
        <div>
          <div
            v-for="(log, idx) in recentLogs"
            :key="idx"
            class="flex items-center gap-3 py-3"
            :style="idx < recentLogs.length - 1 ? `border-bottom: 1px solid var(--kb-border);` : ''"
          >
            <div
              class="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg"
              style="background: rgba(59,111,224,0.1);"
            >
              <Icon :name="log.icon" :size="15" style="color: var(--kb-primary);" />
            </div>
            <p class="min-w-0 flex-1 text-sm truncate" style="color: var(--kb-foreground);">{{ log.text }}</p>
            <span class="text-xs shrink-0 whitespace-nowrap" style="color: var(--kb-muted-foreground);">{{ log.time }}</span>
          </div>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
// 学习报告页：汇总学习概览、学科分布、近 5 周学习热力图与近期动态。
import { ref, computed, onMounted } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import { notify } from '@/utils/toast'
import { userApi, learningApi } from '@/api'
import type { UserStatsVO, LearningTaskVO, FlashcardVO } from '@/api/types'
import { loadSessions, heatmap } from '@/utils/studySession'

const loading = ref(false)
const error = ref('')

interface Stat {
  label: string
  value: string
  unit: string
  icon: string
  iconColor: string
  trendUp?: boolean
}

const overviewStats = ref<Stat[]>([])

// 热力图：5周 x 7天 = 35 个格子，level 0-3（来自本地番茄钟记录）
const heatmapData = ref<number[]>([])
const heatmapEmpty = computed(() => heatmapData.value.every((l) => l === 0))

interface Subject {
  name: string
  hours: number
  percent: number
  color: string
}

const subjectStats = ref<Subject[]>([])

const colorPalette = ['#3B6FE0', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6', '#06B6D4', '#EC4899', '#84CC16', '#F97316']

interface RecentLog {
  icon: string
  text: string
  time: string
}

const recentLogs = ref<RecentLog[]>([])

// 按分类聚合闪卡数量，按最大值归一化为百分比（用于学科分布条形展示）
function buildSubjects(cards: FlashcardVO[]): Subject[] {
  const counts = new Map<string, number>()
  cards.forEach((c) => {
    const key = c.category || '未分类'
    counts.set(key, (counts.get(key) ?? 0) + 1)
  })
  const entries = Array.from(counts.entries())
  const max = Math.max(1, ...entries.map(([, v]) => v))
  return entries.map(([name, hours], i) => ({
    name,
    hours,
    percent: Math.round((hours / max) * 100),
    color: colorPalette[i % colorPalette.length],
  }))
}

// 综合用户统计、学习任务与闪卡数量，拼装「近期动态」时间线条目
function buildLogs(
  stats: UserStatsVO,
  tasks: LearningTaskVO[],
  flashcardCount: number,
): RecentLog[] {
  const logs: RecentLog[] = []
  const totalFlash = stats.totalFlashcards ?? flashcardCount
  if (tasks.length > 0) {
    logs.push({ icon: 'list', text: `你有 ${tasks.length} 个学习任务`, time: '进行中' })
  }
  if (totalFlash > 0) {
    logs.push({ icon: 'layers', text: `共 ${totalFlash} 张闪卡可复习`, time: '随时' })
  }
  if ((stats.completedPaths ?? 0) > 0) {
    logs.push({ icon: 'book-open', text: `已完成 ${stats.completedPaths} 个学习路径`, time: '累计' })
  }
  if ((stats.streakDays ?? 0) > 0) {
    logs.push({ icon: 'flame', text: `已连续学习 ${stats.streakDays} 天`, time: '持续中' })
  }
  if (logs.length === 0) {
    logs.push({ icon: 'info', text: '暂无学习记录，去学习中心开始吧', time: '现在' })
  }
  return logs
}

async function loadData(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const [stats, tasks, cards] = await Promise.all([
      userApi.stats(),
      learningApi.tasks().catch(() => [] as LearningTaskVO[]),
      learningApi.flashcards().catch(() => [] as FlashcardVO[]),
    ])
    const flashcardCount = cards.length
    overviewStats.value = [
      {
        label: '学习时长',
        value: String(stats.totalStudyHours ?? 0),
        unit: '小时',
        icon: 'clock',
        iconColor: 'var(--kb-primary)',
        trendUp: (stats.totalStudyHours ?? 0) > 0,
      },
      {
        label: '完成课程',
        value: String(stats.completedPaths ?? 0),
        unit: '个',
        icon: 'book-open',
        iconColor: 'var(--kb-primary)',
      },
      {
        label: '闪卡总数',
        value: String(stats.totalFlashcards ?? flashcardCount),
        unit: '张',
        icon: 'layers',
        iconColor: 'var(--kb-primary)',
      },
      {
        label: '连续天数',
        value: String(stats.streakDays ?? 0),
        unit: '天',
        icon: 'flame',
        iconColor: 'var(--kb-primary)',
        trendUp: (stats.streakDays ?? 0) > 0,
      },
    ]
    heatmapData.value = heatmap(loadSessions())
    subjectStats.value = buildSubjects(cards)
    recentLogs.value = buildLogs(stats, tasks, flashcardCount)
  } catch (err) {
    const message = err instanceof Error ? err.message : '加载失败'
    error.value = `学习统计加载失败：${message}`
    notify('学习统计加载失败', 'error')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadData()
})
</script>

<style scoped>
.animate-fade-in {
  animation: fadeIn 0.4s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.time-tab {
  padding: 0.375rem 1rem;
  border-radius: var(--kb-radius-sm);
  font-size: 0.8125rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
  border: none;
  background: transparent;
}

.time-tab.active {
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
}

.time-tab:not(.active) {
  color: var(--kb-muted-foreground);
}

.time-tab:not(.active):hover {
  color: var(--kb-foreground);
}

/* Heatmap levels */
.heat-0 {
  background: var(--kb-muted);
}

.heat-1 {
  background: color-mix(in srgb, var(--kb-primary) 15%, var(--kb-card));
}

.heat-2 {
  background: color-mix(in srgb, var(--kb-primary) 40%, var(--kb-card));
}

.heat-3 {
  background: color-mix(in srgb, var(--kb-primary) 70%, var(--kb-card));
}
</style>
