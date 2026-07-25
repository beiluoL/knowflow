<template>
  <div class="animate-fade-in space-y-5">
    <!-- Page Header -->
    <div class="flex items-center justify-between flex-wrap gap-3">
      <div>
        <h1 class="kb-h1">学习中心</h1>
        <p class="kb-body-sm mt-1">追踪你的学习进度，发现学习规律</p>
      </div>
      <div class="flex items-center gap-1 p-1 rounded-lg" style="background: var(--kb-muted);">
        <button
          v-for="tab in timeTabs"
          :key="tab"
          type="button"
          class="time-tab"
          :class="activeTab === tab ? 'active' : ''"
          :disabled="loading"
          @click="switchTab(tab)"
        >{{ tab }}</button>
      </div>
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
      </section>

      <!-- Subject breakdown card -->
      <section class="rounded-lg border p-5" style="background: var(--kb-card); border-color: var(--kb-border);">
        <h3 class="text-[15px] font-semibold mb-4" style="color: var(--kb-foreground);">各语言学习时长</h3>
        <div class="space-y-3">
          <div v-for="subject in subjectStats" :key="subject.name">
            <div class="flex justify-between items-center mb-1.5">
              <div class="flex items-center gap-2">
                <span
                  class="w-2.5 h-2.5 rounded-full"
                  :style="`background: ${subject.color};`"
                ></span>
                <span class="text-sm" style="color: var(--kb-foreground);">{{ subject.name }}</span>
              </div>
              <span class="text-xs font-medium" style="color: var(--kb-muted-foreground);">{{ subject.hours }}h</span>
            </div>
            <div class="h-2 rounded-full overflow-hidden" style="background: var(--kb-muted);">
              <div
                class="h-2 rounded-full transition-all duration-500"
                :style="`width: ${subject.percent}%; background: ${subject.color};`"
              ></div>
            </div>
          </div>
        </div>
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
import { ref, onMounted } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import { notify } from '@/utils/toast'

const timeTabs = ['本周', '本月', '全部']
const activeTab = ref('本周')

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

const weekStats: Stat[] = [
  { label: '学习时长', value: '12.5', unit: '小时', icon: 'clock', iconColor: 'var(--kb-primary)', trendUp: true },
  { label: '完成课程', value: '8', unit: '个', icon: 'book-open', iconColor: 'var(--kb-primary)' },
  { label: '闪卡复习', value: '45', unit: '张', icon: 'layers', iconColor: 'var(--kb-primary)' },
  { label: '正确率', value: '78', unit: '%', icon: 'target', iconColor: 'var(--kb-primary)', trendUp: true },
]

const monthStats: Stat[] = [
  { label: '学习时长', value: '52.3', unit: '小时', icon: 'clock', iconColor: 'var(--kb-primary)', trendUp: true },
  { label: '完成课程', value: '24', unit: '个', icon: 'book-open', iconColor: 'var(--kb-primary)' },
  { label: '闪卡复习', value: '186', unit: '张', icon: 'layers', iconColor: 'var(--kb-primary)' },
  { label: '正确率', value: '82', unit: '%', icon: 'target', iconColor: 'var(--kb-primary)', trendUp: true },
]

const allStats: Stat[] = [
  { label: '学习时长', value: '482', unit: '小时', icon: 'clock', iconColor: 'var(--kb-primary)', trendUp: true },
  { label: '完成课程', value: '128', unit: '个', icon: 'book-open', iconColor: 'var(--kb-primary)' },
  { label: '闪卡复习', value: '1240', unit: '张', icon: 'layers', iconColor: 'var(--kb-primary)' },
  { label: '正确率', value: '85', unit: '%', icon: 'target', iconColor: 'var(--kb-primary)', trendUp: true },
]

// 热力图：5周 x 7天 = 35 个格子，level 0-3
const heatmapData = ref<number[]>([])

// 默认热力图（模拟数据，按周分布，越近期越密集）
const defaultHeatmap: number[] = [
  0, 1, 2, 1, 3, 0, 0, // week 1
  1, 2, 3, 2, 1, 1, 0, // week 2
  0, 2, 1, 0, 2, 3, 1, // week 3
  1, 3, 2, 3, 1, 0, 0, // week 4
  0, 1, 1, 2, 0, 0, 0, // week 5 (本周)
]

interface Subject {
  name: string
  hours: number
  percent: number
  color: string
}

const subjectStats = ref<Subject[]>([])

const weekSubjects: Subject[] = [
  { name: 'Java', hours: 5.2, percent: 100, color: '#3B6FE0' },
  { name: 'Python', hours: 3.8, percent: 73, color: '#10B981' },
  { name: '前端', hours: 2.1, percent: 40, color: '#F59E0B' },
  { name: '算法', hours: 1.4, percent: 27, color: '#8B5CF6' },
]

const monthSubjects: Subject[] = [
  { name: 'Java', hours: 18.5, percent: 100, color: '#3B6FE0' },
  { name: 'Python', hours: 14.2, percent: 77, color: '#10B981' },
  { name: '前端', hours: 10.8, percent: 58, color: '#F59E0B' },
  { name: '算法', hours: 8.8, percent: 48, color: '#8B5CF6' },
]

const allSubjects: Subject[] = [
  { name: 'Java', hours: 152, percent: 100, color: '#3B6FE0' },
  { name: 'Python', hours: 138, percent: 91, color: '#10B981' },
  { name: '前端', hours: 105, percent: 69, color: '#F59E0B' },
  { name: '算法', hours: 87, percent: 57, color: '#8B5CF6' },
]

interface RecentLog {
  icon: string
  text: string
  time: string
}

const recentLogs = ref<RecentLog[]>([])

const defaultLogs: RecentLog[] = [
  { icon: 'check-circle', text: '完成了 Java HashMap 章节', time: '2小时前' },
  { icon: 'rotate-ccw', text: '复习了 Python 基础语法闪卡', time: '5小时前' },
  { icon: 'code', text: '学习了 CSS Flexbox 布局', time: '昨天' },
  { icon: 'brain', text: '完成了排序算法练习', time: '昨天' },
  { icon: 'layers', text: '复习了 Java 集合框架闪卡', time: '2天前' },
]

async function loadData(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    // 后端暂无学习统计接口，使用 mock 数据，模拟异步加载
    await new Promise((resolve) => setTimeout(resolve, 300))

    const statsMap: Record<string, Stat[]> = {
      本周: weekStats,
      本月: monthStats,
      全部: allStats,
    }
    const subjectsMap: Record<string, Subject[]> = {
      本周: weekSubjects,
      本月: monthSubjects,
      全部: allSubjects,
    }

    overviewStats.value = statsMap[activeTab.value] ?? weekStats
    subjectStats.value = subjectsMap[activeTab.value] ?? weekSubjects
    heatmapData.value = defaultHeatmap
    recentLogs.value = defaultLogs
  } catch (err) {
    const message = err instanceof Error ? err.message : '加载失败'
    error.value = `学习统计加载失败：${message}`
    notify('学习统计加载失败', 'error')
  } finally {
    loading.value = false
  }
}

function switchTab(tab: string): void {
  if (activeTab.value === tab || loading.value) return
  activeTab.value = tab
  void loadData()
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
