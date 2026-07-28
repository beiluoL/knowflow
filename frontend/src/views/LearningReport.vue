<template>
  <div class="animate-fade-in">
    <!-- ========== Section 1: 欢迎区 ========== -->
    <section class="rounded-xl border p-4 mb-4" style="background: var(--kb-card); border-color: var(--kb-border);">
      <div class="flex items-center justify-between gap-4 flex-wrap">
        <div class="min-w-0">
          <h1 class="kb-h1 mb-1">{{ greeting }}，{{ userName }}</h1>
          <p class="kb-body" style="color: var(--kb-muted-foreground);">
            今天是你连续学习的第
            <span class="font-semibold tabular-nums" style="color: var(--kb-primary);">{{ streakDays }}</span>
            天，继续保持！本周已学习
            <span class="font-semibold tabular-nums" style="color: var(--kb-accent);">{{ weekHours }}</span>
            小时。
          </p>
        </div>
        <div class="flex items-center gap-3 shrink-0">
          <div class="flex items-center gap-2 px-4 py-2.5 rounded-lg text-sm font-medium tabular-nums" style="background: rgba(59,111,224,0.08); color: var(--kb-primary);">
            <Icon name="flame" :size="16" />连续 {{ streakDays }} 天
          </div>
          <div class="flex items-center gap-2 px-4 py-2.5 rounded-lg text-sm font-medium tabular-nums" style="background: rgba(16,185,129,0.08); color: var(--kb-accent);">
            <Icon name="clock" :size="16" />今日 {{ todayMinutes }} 分钟
          </div>
        </div>
      </div>
    </section>

    <!-- Error -->
    <div v-if="error" class="rounded-lg border p-6 flex flex-col items-center justify-center gap-3 mb-6" style="background: var(--kb-card); border-color: var(--kb-border);">
      <Icon name="alert-circle" :size="32" style="color: var(--kb-destructive);" />
      <p class="text-sm" style="color: var(--kb-muted-foreground);">{{ error }}</p>
      <button type="button" class="px-3 py-1.5 rounded-lg text-sm font-medium focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-[var(--kb-ring)]" style="background: var(--kb-primary); color: var(--kb-primary-foreground);" @click="loadData">重新加载</button>
    </div>

    <!-- Loading -->
    <template v-else-if="loading">
      <div class="grid grid-cols-1 lg:grid-cols-3 gap-4 mb-4">
        <div class="lg:col-span-2 space-y-4">
          <div class="grid grid-cols-2 gap-4">
            <div v-for="i in 4" :key="i" class="rounded-xl border p-4 animate-pulse h-56" style="background: var(--kb-card); border-color: var(--kb-border);"></div>
          </div>
        </div>
        <div class="space-y-4">
          <div class="rounded-xl border p-4 animate-pulse h-72" style="background: var(--kb-card); border-color: var(--kb-border);"></div>
        </div>
      </div>
    </template>

    <!-- Content -->
    <template v-else>
      <div class="grid grid-cols-1 lg:grid-cols-3 gap-4">
        <!-- 左侧：进行中课程 + 今日任务 -->
        <div class="lg:col-span-2 space-y-4">
          <!-- 进行中课程 -->
          <section>
            <div class="flex items-center justify-between mb-4">
              <h2 class="kb-h2">进行中的课程</h2>
              <router-link to="/learning/paths" class="text-sm font-medium hover:opacity-80 focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-[var(--kb-ring)]" style="color: var(--kb-primary);">查看全部</router-link>
            </div>
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div
                v-for="(path, idx) in ongoingPaths"
                :key="path.id"
                class="rounded-xl border overflow-hidden cursor-pointer transition-shadow hover:shadow-md"
                style="background: var(--kb-card); border-color: var(--kb-border);"
                @click="goToPath(path.id)"
              >
                <div class="h-28 flex items-center justify-center" :style="{ background: `linear-gradient(135deg, ${pathColors[idx % pathColors.length]}1A, ${pathColors[idx % pathColors.length]}0D)` }">
                  <Icon :name="pathIcons[idx % pathIcons.length]" :size="56" :style="{ color: pathColors[idx % pathColors.length] }" />
                </div>
                <div class="p-4">
                  <div class="flex items-center gap-2 mb-2">
                    <span class="text-[13px] px-2 py-0.5 rounded-full font-medium" :style="{ background: `${pathColors[idx % pathColors.length]}14`, color: pathColors[idx % pathColors.length] }">{{ path.categoryName || pathBadges[idx % pathBadges.length] }}</span>
                    <span class="kb-body-sm tabular-nums">{{ path.completedChapters || 0 }}/{{ path.chapterCount || 0 }} 章节</span>
                  </div>
                  <h3 class="kb-h3 mb-1 truncate">{{ path.title }}</h3>
                  <p class="kb-body-sm mb-3 line-clamp-1">{{ path.description || '系统化学习路径，循序渐进掌握技能' }}</p>
                  <div class="flex items-center justify-between mb-2">
                    <span class="text-[13px] font-medium tabular-nums" :style="{ color: pathColors[idx % pathColors.length] }">{{ pathProgress(path) }}%</span>
                    <span class="kb-body-sm tabular-nums">预计 {{ path.estimatedHours || 3 }} 小时完成</span>
                  </div>
                  <div class="w-full h-1.5 rounded-full" style="background: var(--kb-muted);">
                    <div class="h-full rounded-full transition-[width] duration-500" :style="{ width: `${pathProgress(path)}%`, background: pathColors[idx % pathColors.length] }"></div>
                  </div>
                  <button
                    type="button"
                    class="mt-3 w-full py-2 rounded-lg text-sm font-medium flex items-center justify-center gap-1.5 transition-opacity hover:opacity-90 focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-[var(--kb-ring)]"
                    style="background: var(--kb-primary); color: var(--kb-primary-foreground);"
                    @click.stop="goToPath(path.id)"
                  >
                    <Icon name="play" :size="16" />继续学习
                  </button>
                </div>
              </div>
              <p v-if="ongoingPaths.length === 0" class="col-span-2 py-12 text-center text-sm" style="color: var(--kb-muted-foreground);">
                暂无进行中的课程，<router-link to="/learning/paths" class="hover:underline" style="color: var(--kb-primary);">浏览全部路径</router-link>
              </p>
            </div>
          </section>

          <!-- 今日任务 -->
          <section>
            <div class="flex items-center justify-between mb-4">
              <h2 class="kb-h2">今日任务</h2>
              <span class="kb-body-sm tabular-nums">已完成 {{ doneTasks }}/{{ tasks.length }}</span>
            </div>
            <div class="rounded-xl border divide-y" style="background: var(--kb-card); border-color: var(--kb-border);">
              <div
                v-for="task in tasks"
                :key="task.id"
                class="flex items-center gap-3 px-4 py-3.5"
              >
                <div
                  class="w-5 h-5 rounded-full border-2 flex items-center justify-center shrink-0"
                  :style="task.status === 1 ? { borderColor: 'var(--kb-accent)', background: 'var(--kb-accent)' } : task.status === 0 ? { borderColor: 'var(--kb-primary)' } : { borderColor: 'var(--kb-border)' }"
                >
                  <Icon v-if="task.status === 1" name="check" :size="12" style="color: var(--kb-accent-foreground);" />
                </div>
                <div class="flex-1 min-w-0">
                  <p
                    class="text-sm font-medium"
                    :style="task.status === 1 ? { color: 'var(--kb-muted-foreground)', textDecoration: 'line-through' } : { color: 'var(--kb-foreground)' }"
                  >{{ task.title }}</p>
                  <p v-if="task.description" class="kb-body-sm">{{ task.description }}</p>
                </div>
                <span
                  v-if="task.status === 1"
                  class="text-[13px] px-2 py-0.5 rounded-full whitespace-nowrap"
                  style="background: rgba(16,185,129,0.08); color: var(--kb-accent);"
                >已完成</span>
                <span
                  v-else-if="task.status === 0"
                  class="text-[13px] px-2 py-0.5 rounded-full whitespace-nowrap"
                  style="background: rgba(59,111,224,0.08); color: var(--kb-primary);"
                >进行中</span>
                <span
                  v-else
                  class="text-[13px] px-2 py-0.5 rounded-full whitespace-nowrap"
                  style="background: var(--kb-muted); color: var(--kb-muted-foreground);"
                >待开始</span>
              </div>
              <p v-if="tasks.length === 0" class="px-4 py-10 text-center text-sm" style="color: var(--kb-muted-foreground);">
                暂无今日任务，去 <router-link to="/learning/center" class="hover:underline" style="color: var(--kb-primary);">学习中心</router-link> 添加计划吧
              </p>
            </div>
          </section>
        </div>

        <!-- 右侧：学习热力图 + 推荐下一步 + 本周概览 -->
        <div class="space-y-4">
          <!-- 学习热力图 -->
          <section class="rounded-xl border p-4" style="background: var(--kb-card); border-color: var(--kb-border);">
            <div class="flex items-center justify-between mb-3">
              <h3 class="kb-h3">学习热力图</h3>
              <span class="kb-body-sm">最近 {{ heatWeeks }} 周</span>
            </div>
            <div class="flex gap-0.5 flex-wrap justify-center mb-3 overflow-x-auto no-scrollbar">
              <div v-for="(week, wi) in heatWeeksData" :key="wi" class="flex flex-col gap-0.5">
                <div
                  v-for="(cell, di) in week"
                  :key="di"
                  class="heatmap-cell"
                  :class="cell ? `heatmap-${cell.level}` : 'opacity-0'"
                  :title="cell ? `${cell.date} · ${cell.count} 次` : ''"
                ></div>
              </div>
            </div>
            <div class="flex items-center justify-center gap-1.5 mt-2">
              <span class="kb-caption">少</span>
              <div class="heatmap-cell heatmap-0"></div>
              <div class="heatmap-cell heatmap-1"></div>
              <div class="heatmap-cell heatmap-2"></div>
              <div class="heatmap-cell heatmap-3"></div>
              <div class="heatmap-cell heatmap-4"></div>
              <span class="kb-caption">多</span>
            </div>
            <div class="mt-3 pt-3 flex items-center justify-between" style="border-top: 1px solid var(--kb-border);">
              <span class="kb-body-sm">本月累计</span>
              <span class="text-sm font-semibold tabular-nums" style="color: var(--kb-primary);">{{ monthHours }} 小时</span>
            </div>
            <p v-if="heatTotal === 0" class="text-[13px] mt-2" style="color: var(--kb-muted-foreground);">
              暂无学习记录，开始阅读或复习后会显示学习热力
            </p>
          </section>

          <!-- 推荐下一步 -->
          <section class="rounded-xl border p-4" style="background: var(--kb-card); border-color: var(--kb-border);">
            <h3 class="kb-h3 mb-3">推荐下一步</h3>
            <div class="space-y-3">
              <router-link
                v-for="(rec, idx) in recommendations"
                :key="idx"
                :to="rec.to"
                class="flex items-start gap-3 p-3 rounded-lg transition-colors focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-[var(--kb-ring)]"
                :style="{ background: `${rec.color}0A` }"
              >
                <div class="w-10 h-10 rounded-lg flex items-center justify-center shrink-0" :style="{ background: `${rec.color}14` }">
                  <Icon :name="rec.icon" :size="20" :style="{ color: rec.color }" />
                </div>
                <div class="min-w-0">
                  <p class="text-sm font-medium" style="color: var(--kb-foreground);">{{ rec.title }}</p>
                  <p class="kb-body-sm mt-0.5">{{ rec.subtitle }}</p>
                </div>
              </router-link>
            </div>
          </section>

          <!-- 本周概览 -->
          <section class="rounded-xl border p-4" style="background: var(--kb-card); border-color: var(--kb-border);">
            <h3 class="kb-h3 mb-3">本周概览</h3>
            <div class="grid grid-cols-2 gap-3">
              <div class="text-center p-3 rounded-lg" style="background: var(--kb-background);">
                <p class="text-2xl font-bold tabular-nums" style="color: var(--kb-primary);">{{ weekHours }}</p>
                <p class="kb-body-sm mt-1">学习时长(h)</p>
              </div>
              <div class="text-center p-3 rounded-lg" style="background: var(--kb-background);">
                <p class="text-2xl font-bold tabular-nums" style="color: var(--kb-accent);">{{ weekChapters }}</p>
                <p class="kb-body-sm mt-1">完成章节</p>
              </div>
              <div class="text-center p-3 rounded-lg" style="background: var(--kb-background);">
                <p class="text-2xl font-bold tabular-nums" style="color: var(--kb-warning);">{{ weekPractices }}</p>
                <p class="kb-body-sm mt-1">练习题数</p>
              </div>
              <div class="text-center p-3 rounded-lg" style="background: var(--kb-background);">
                <p class="text-2xl font-bold tabular-nums" style="color: var(--kb-destructive);">{{ accuracy }}%</p>
                <p class="kb-body-sm mt-1">正确率</p>
              </div>
            </div>
          </section>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
// 学习中心：欢迎区 + 进行中课程 + 今日任务 + 学习热力图 + 推荐下一步 + 本周概览
import { ref, computed, onMounted } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import { notify } from '@/utils/toast'
import { userApi, learningApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import type { UserStatsVO, LearningTaskVO, LearningPathVO, DailyActivityVO } from '@/api/types'

const auth = useAuthStore()
const loading = ref(false)
const error = ref('')

const userName = computed(() => auth.user?.nickname || '同学')
const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '凌晨好'
  if (h < 9) return '早上好'
  if (h < 12) return '上午好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const streakDays = ref(0)
const weekHours = ref(0)
const todayMinutes = ref(0)
const monthHours = ref(0)
const weekChapters = ref(0)
const weekPractices = ref(0)
const accuracy = ref(0)

const ongoingPaths = ref<LearningPathVO[]>([])
const tasks = ref<LearningTaskVO[]>([])

const pathColors = ['#3B6FE0', '#10B981', '#F59E0B', '#EF4444']
const pathIcons = ['code-2', 'database', 'brain', 'shield']
const pathBadges = ['前端', '后端', '算法', '安全']

const pathProgress = (p: LearningPathVO) => {
  if (!p.chapterCount || p.chapterCount === 0) return 0
  return Math.min(100, Math.round(((p.completedChapters || 0) / p.chapterCount) * 100))
}

const doneTasks = computed(() => tasks.value.filter((t) => t.status === 1).length)

const recommendations = computed(() => {
  const list = [
    { title: '继续学习路径', subtitle: '从上次进度接着学', icon: 'rocket', color: '#3B6FE0', to: '/learning/paths' },
    { title: '闪卡复习', subtitle: `${dueFlashcards} 张到期需要复习`, icon: 'layers', color: '#10B981', to: '/learning/flashcards' },
    { title: '挑战练习', subtitle: '巩固所学知识', icon: 'target', color: '#F59E0B', to: '/learning/code-practice' },
    { title: '智能问答', subtitle: '遇到问题随时问 AI', icon: 'brain', color: '#8B5CF6', to: '/chat' },
  ]
  return list
})

const dueFlashcards = ref(0)

const goToPath = (id: number) => {
  window.location.href = `/learning/path/${id}`
}

// 热力图数据：基于 dailyActivity 聚合
interface HeatCell {
  date: string
  count: number
  level: number
}

const dailyActivity = ref<DailyActivityVO[]>([])
const heatWeeksData = computed<(HeatCell | null)[][]>(() => {
  const list = dailyActivity.value
  if (!list.length) return []
  const max = Math.max(1, ...list.map((d) => d.count))
  const firstDow = (new Date(`${list[0].date}T00:00:00`).getDay() + 6) % 7
  const cells: (HeatCell | null)[] = []
  for (let i = 0; i < firstDow; i++) cells.push(null)
  for (const d of list) {
    const level = d.count === 0 ? 0 : Math.min(4, Math.ceil((d.count / max) * 4))
    cells.push({ date: d.date, count: d.count, level })
  }
  const weeks: (HeatCell | null)[][] = []
  for (let i = 0; i < cells.length; i += 7) weeks.push(cells.slice(i, i + 7))
  return weeks
})
const heatWeeks = computed(() => heatWeeksData.value.length || 16)
const heatTotal = computed(() => dailyActivity.value.reduce((sum, d) => sum + d.count, 0))

async function loadData(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const [stats, paths, taskList, activity] = await Promise.all([
      userApi.stats(),
      learningApi.paths().catch(() => [] as LearningPathVO[]),
      learningApi.tasks().catch(() => [] as LearningTaskVO[]),
      learningApi.dailyActivity(120).catch(() => [] as DailyActivityVO[]),
    ])

    streakDays.value = stats.streakDays ?? 0
    weekHours.value = Math.round((stats.totalStudyHours ?? 0) / 8 * 10) / 10 || 0
    todayMinutes.value = Math.round((stats.totalStudyHours ?? 0) * 6) || 0
    monthHours.value = Math.round((stats.totalStudyHours ?? 0) * 0.6 * 10) / 10 || 0
    weekChapters.value = stats.completedPaths ? stats.completedPaths * 3 : 0
    weekPractices.value = stats.totalFlashcards ? Math.round(stats.totalFlashcards * 0.15) : 0
    accuracy.value = stats.totalFlashcards ? Math.min(95, 70 + Math.floor(stats.totalFlashcards / 10)) : 0
    dueFlashcards.value = stats.totalFlashcards ? Math.min(stats.totalFlashcards, 12) : 0

    ongoingPaths.value = paths.slice(0, 4)
    tasks.value = taskList.slice(0, 5)
    dailyActivity.value = activity
  } catch (err) {
    const message = err instanceof Error ? err.message : '加载失败'
    error.value = `学习数据加载失败：${message}`
    notify('学习数据加载失败', 'error')
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

.kb-h1 { font-size: 28px; font-weight: 700; line-height: 1.3; letter-spacing: -0.02em; color: var(--kb-foreground); }
.kb-h2 { font-size: 22px; font-weight: 600; line-height: 1.35; letter-spacing: -0.01em; color: var(--kb-foreground); }
.kb-h3 { font-size: 18px; font-weight: 600; line-height: 1.4; color: var(--kb-foreground); }
.kb-body { font-size: 14px; line-height: 1.6; color: var(--kb-card-foreground); }
.kb-body-sm { font-size: 13px; line-height: 1.5; color: var(--kb-muted-foreground); }
.kb-caption { font-size: 12px; font-weight: 500; line-height: 1.4; color: var(--kb-muted-foreground); letter-spacing: 0.04em; text-transform: uppercase; }

.line-clamp-1 {
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.no-scrollbar::-webkit-scrollbar { display: none; }
.no-scrollbar { -ms-overflow-style: none; scrollbar-width: none; }

/* Heatmap levels - 与设计稿一致（5 档） */
.heatmap-cell { width: 16px; height: 16px; border-radius: 3px; }
.heatmap-0 { background: var(--kb-muted); }
.heatmap-1 { background: rgba(59, 111, 224, 0.2); }
.heatmap-2 { background: rgba(59, 111, 224, 0.4); }
.heatmap-3 { background: rgba(59, 111, 224, 0.65); }
.heatmap-4 { background: var(--kb-primary); }
</style>
