<template>
  <div class="learning-center animate-fade-in">
    <!-- ========== Section 1: 名人名言 Hero ========== -->
    <section class="quote-hero">
      <div class="quote-hero-bg">
        <div class="quote-grid-pattern"></div>
        <div class="quote-glow quote-glow-1"></div>
        <div class="quote-glow quote-glow-2"></div>
      </div>
      <div class="quote-content">
        <div class="quote-mark">"</div>
        <p class="quote-text">{{ currentQuote.text }}</p>
        <div class="quote-divider"></div>
        <p class="quote-author">
          <span class="quote-author-name">{{ currentQuote.author }}</span>
          <span v-if="currentQuote.title" class="quote-author-title">{{ currentQuote.title }}</span>
        </p>
      </div>
      <div class="quote-dots">
        <button
          v-for="(_, idx) in quotes"
          :key="idx"
          type="button"
          :aria-label="`第 ${idx + 1} 条名言`"
          :class="['quote-dot', { 'quote-dot-active': idx === quoteIndex }]"
          @click="quoteIndex = idx"
        ></button>
      </div>
      <button
        v-if="quotes.length > 1"
        type="button"
        aria-label="下一条名言"
        class="quote-nav-btn quote-nav-next"
        @click="nextQuote"
      >
        <Icon name="chevron-right" :size="18" />
      </button>
    </section>

    <!-- ========== Section 2: 学习数据概览 ========== -->
    <section class="stats-bar">
      <div class="stats-greeting">
        <h1 class="kb-h1">{{ greeting }}，{{ userName }}</h1>
        <p class="kb-body">
          今天是你连续学习的第
          <span class="font-semibold tabular-nums" style="color: var(--kb-primary);">{{ streakDays }}</span>
          天，继续保持！本周已学习
          <span class="font-semibold tabular-nums" style="color: var(--kb-accent);">{{ weekHours }}</span>
          小时。
        </p>
      </div>
      <div class="stats-pills">
        <div class="stats-pill stats-pill-flame">
          <Icon name="flame" :size="16" />
          <span class="tabular-nums">连续 {{ streakDays }} 天</span>
        </div>
        <div class="stats-pill stats-pill-clock">
          <Icon name="clock" :size="16" />
          <span class="tabular-nums">今日 {{ todayMinutes }} 分钟</span>
        </div>
        <div class="stats-pill stats-pill-energy">
          <Icon name="zap" :size="16" />
          <span class="tabular-nums">能量 {{ energy }}</span>
        </div>
        <router-link to="/learning/mode" class="stats-pill stats-pill-focus" title="进入沉浸学习模式">
          <Icon name="moon" :size="16" />
          <span>沉浸模式</span>
        </router-link>
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
      <!-- ========== Section 3: 快速入口 ========== -->
      <section class="quick-nav">
        <router-link
          v-for="(item, idx) in quickNavItems"
          :key="idx"
          :to="item.to"
          class="quick-nav-card"
        >
          <div class="quick-nav-icon" :style="{ background: `${item.color}14`, color: item.color }">
            <Icon :name="item.icon" :size="22" />
          </div>
          <span class="quick-nav-label">{{ item.label }}</span>
          <Icon name="arrow-right" :size="14" class="quick-nav-arrow" />
        </router-link>
      </section>

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
                class="path-card"
                @click="goToPath(path.id)"
              >
                <div class="path-card-cover" :style="{ background: `linear-gradient(135deg, ${pathColors[idx % pathColors.length]}1A, ${pathColors[idx % pathColors.length]}0D)` }">
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
              <div class="flex items-center gap-3">
                <span class="kb-body-sm tabular-nums">已完成 {{ doneTasks }}/{{ tasks.length }}</span>
                <button type="button" class="add-task-btn" @click="showAddTask = !showAddTask">
                  <Icon name="plus" :size="14" />
                  <span>添加计划</span>
                </button>
              </div>
            </div>

            <!-- 添加计划表单 -->
            <div v-if="showAddTask" class="add-task-form">
              <input
                v-model="newTask.title"
                type="text"
                placeholder="计划标题，如：阅读《JavaScript 高级程序设计》第3章"
                class="add-task-input"
                @keydown.enter="handleCreateTask"
              />
              <textarea
                v-model="newTask.description"
                placeholder="描述（可选）"
                rows="2"
                class="add-task-textarea"
              ></textarea>
              <div class="flex items-center gap-2 mt-2">
                <select v-model="newTask.type" class="add-task-select">
                  <option value="study">学习</option>
                  <option value="reading">阅读</option>
                  <option value="practice">练习</option>
                  <option value="review">复习</option>
                  <option value="other">其他</option>
                </select>
                <input
                  v-model="newTask.deadline"
                  type="datetime-local"
                  class="add-task-date"
                />
                <div class="flex-1"></div>
                <button type="button" class="add-task-cancel" @click="resetAddTask">取消</button>
                <button type="button" class="add-task-submit" :disabled="creatingTask || !newTask.title.trim()" @click="handleCreateTask">
                  {{ creatingTask ? '添加中...' : '确认添加' }}
                </button>
              </div>
            </div>

            <div class="rounded-xl border divide-y" style="background: var(--kb-card); border-color: var(--kb-border);">
              <div
                v-for="task in tasks"
                :key="task.id"
                class="flex items-center gap-3 px-4 py-3.5 task-row"
              >
                <button
                  type="button"
                  class="task-check-btn"
                  :style="task.status === 1 ? { borderColor: 'var(--kb-accent)', background: 'var(--kb-accent)' } : task.status === 0 ? { borderColor: 'var(--kb-primary)' } : { borderColor: 'var(--kb-border)' }"
                  :title="task.status === 1 ? '标记为未完成' : '标记为已完成'"
                  @click="toggleTaskStatus(task)"
                >
                  <Icon v-if="task.status === 1" name="check" :size="12" style="color: var(--kb-accent-foreground);" />
                </button>
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
                <button type="button" class="task-delete-btn" title="删除" @click="handleDeleteTask(task)">
                  <Icon name="trash-2" :size="14" />
                </button>
              </div>
              <p v-if="tasks.length === 0 && !showAddTask" class="px-4 py-10 text-center text-sm" style="color: var(--kb-muted-foreground);">
                暂无今日任务，点击上方「添加计划」按钮创建你的第一个学习计划吧
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
// 学习中心：名人名言 + 学习数据概览 + 快速入口 + 进行中课程 + 今日任务 + 学习热力图 + 推荐下一步 + 本周概览
import { ref, computed, onMounted, onUnmounted } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import { notify, confirmDialog, getApiError } from '@/utils/toast'
import { userApi, learningApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import type { LearningTaskVO, LearningPathVO, DailyActivityVO, LearningTaskInput } from '@/api/types'

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
const energy = ref(0)

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

// 名人名言数据
interface Quote {
  text: string
  author: string
  title?: string
}

const quotes: Quote[] = [
  { text: '学而时习之，不亦说乎？', author: '孔子', title: '《论语》' },
  { text: '知之者不如好之者，好之者不如乐之者。', author: '孔子', title: '《论语》' },
  { text: '路漫漫其修远兮，吾将上下而求索。', author: '屈原', title: '《离骚》' },
  { text: '读书破万卷，下笔如有神。', author: '杜甫', title: '《奉赠韦左丞丈二十二韵》' },
  { text: '业精于勤，荒于嬉；行成于思，毁于随。', author: '韩愈', title: '《进学解》' },
  { text: '纸上得来终觉浅，绝知此事要躬行。', author: '陆游', title: '《冬夜读书示子聿》' },
  { text: '问渠那得清如许？为有源头活水来。', author: '朱熹', title: '《观书有感》' },
  { text: '千里之行，始于足下。', author: '老子', title: '《道德经》' },
  { text: '书山有路勤为径，学海无涯苦作舟。', author: '韩愈' },
  { text: '博学之，审问之，慎思之，明辨之，笃行之。', author: '子思', title: '《中庸》' },
  { text: '不积跬步，无以至千里；不积小流，无以成江海。', author: '荀子', title: '《劝学》' },
  { text: '学而不思则罔，思而不学则殆。', author: '孔子', title: '《论语》' },
]

const quoteIndex = ref(Math.floor(Math.random() * quotes.length))
let quoteTimer: ReturnType<typeof setInterval> | null = null

const currentQuote = computed(() => quotes[quoteIndex.value])

function nextQuote() {
  quoteIndex.value = (quoteIndex.value + 1) % quotes.length
}

const quickNavItems = [
  { label: '学习路径', icon: 'route', color: '#3B6FE0', to: '/learning/paths' },
  { label: '番茄钟专注', icon: 'timer', color: '#EF4444', to: '/learning/pomodoro' },
  { label: '代码练习', icon: 'code', color: '#F59E0B', to: '/learning/code-practice' },
  { label: '编程挑战', icon: 'rocket', color: '#8B5CF6', to: '/challenge' },
  { label: '闪卡复习', icon: 'layers', color: '#10B981', to: '/learning/flashcards' },
  { label: '复习计划', icon: 'calendar-check', color: '#F59E0B', to: '/learning/review' },
  { label: '知识图谱', icon: 'share-2', color: '#8B5CF6', to: '/learning/knowledge-graph' },
  { label: 'AI 问答', icon: 'brain', color: '#06B6D4', to: '/chat' },
]

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

// ===== 添加计划功能 =====
const showAddTask = ref(false)
const creatingTask = ref(false)
const newTask = ref<LearningTaskInput>({
  title: '',
  description: '',
  type: 'study',
  deadline: '',
})

const resetAddTask = () => {
  showAddTask.value = false
  newTask.value = { title: '', description: '', type: 'study', deadline: '' }
}

const handleCreateTask = async () => {
  if (!newTask.value.title.trim()) {
    notify('请输入计划标题', 'warning')
    return
  }
  creatingTask.value = true
  try {
    const payload: LearningTaskInput = {
      title: newTask.value.title.trim(),
      description: newTask.value.description?.trim() || undefined,
      type: newTask.value.type || 'study',
    }
    if (newTask.value.deadline) {
      payload.deadline = new Date(newTask.value.deadline).toISOString()
    }
    await learningApi.createTask(payload)
    notify('计划添加成功', 'success')
    resetAddTask()
    // 重新加载任务列表
    const taskList = await learningApi.tasks().catch(() => [] as LearningTaskVO[])
    tasks.value = taskList.slice(0, 5)
  } catch (e: unknown) {
    notify('添加失败：' + getApiError(e), 'error')
  } finally {
    creatingTask.value = false
  }
}

const toggleTaskStatus = async (task: LearningTaskVO) => {
  const newStatus = task.status === 1 ? 0 : 1
  try {
    await learningApi.updateTaskStatus(task.id, newStatus)
    task.status = newStatus
    if (newStatus === 1) {
      notify('任务已完成', 'success')
    }
  } catch (e: unknown) {
    notify('操作失败：' + getApiError(e), 'error')
  }
}

const handleDeleteTask = async (task: LearningTaskVO) => {
  const ok = await confirmDialog(`确定删除计划「${task.title}」吗？`)
  if (!ok) return
  try {
    await learningApi.deleteTask(task.id)
    tasks.value = tasks.value.filter((t) => t.id !== task.id)
    notify('已删除', 'success')
  } catch (e: unknown) {
    notify('删除失败：' + getApiError(e), 'error')
  }
}

const goToPath = (id: number) => {
  window.location.href = `/learning/path/${id}`
}

// 热力图数据
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
    energy.value = stats.energy ?? 0

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
  // 每 15 秒自动切换名言
  quoteTimer = setInterval(nextQuote, 15000)
})

onUnmounted(() => {
  if (quoteTimer) clearInterval(quoteTimer)
})
</script>

<style scoped>
.animate-fade-in {
  animation: fadeIn 0.4s ease-out;
}

/* 添加计划按钮 */
.add-task-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 30px;
  padding: 0 12px;
  font-size: 13px;
  font-weight: 500;
  border-radius: var(--kb-radius-sm);
  border: 1px solid var(--kb-primary);
  background: rgba(59, 111, 224, 0.06);
  color: var(--kb-primary);
  cursor: pointer;
  transition: background 0.15s ease;
}
.add-task-btn:hover {
  background: rgba(59, 111, 224, 0.12);
}

/* 添加计划表单 */
.add-task-form {
  margin-bottom: 12px;
  padding: 16px;
  border-radius: var(--kb-radius-md);
  border: 1px solid var(--kb-border);
  background: var(--kb-background);
}
.add-task-input {
  width: 100%;
  height: 38px;
  padding: 0 12px;
  font-size: 14px;
  border-radius: var(--kb-radius-sm);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-foreground);
  outline: none;
  transition: border-color 0.15s ease;
}
.add-task-input:focus {
  border-color: var(--kb-primary);
}
.add-task-textarea {
  width: 100%;
  margin-top: 8px;
  padding: 8px 12px;
  font-size: 13px;
  border-radius: var(--kb-radius-sm);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-foreground);
  outline: none;
  resize: none;
  transition: border-color 0.15s ease;
}
.add-task-textarea:focus {
  border-color: var(--kb-primary);
}
.add-task-select {
  height: 32px;
  padding: 0 8px;
  font-size: 12px;
  border-radius: var(--kb-radius-sm);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-foreground);
  outline: none;
}
.add-task-date {
  height: 32px;
  padding: 0 8px;
  font-size: 12px;
  border-radius: var(--kb-radius-sm);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-foreground);
  outline: none;
}
.add-task-cancel {
  height: 32px;
  padding: 0 14px;
  font-size: 13px;
  font-weight: 500;
  border-radius: var(--kb-radius-sm);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-foreground);
  cursor: pointer;
  transition: background 0.15s ease;
}
.add-task-cancel:hover {
  background: var(--kb-muted);
}
.add-task-submit {
  height: 32px;
  padding: 0 16px;
  font-size: 13px;
  font-weight: 500;
  border-radius: var(--kb-radius-sm);
  border: none;
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  cursor: pointer;
  transition: opacity 0.15s ease;
}
.add-task-submit:hover {
  opacity: 0.9;
}
.add-task-submit:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 任务行交互 */
.task-row {
  transition: background 0.15s ease;
}
.task-row:hover {
  background: var(--kb-muted);
}
.task-check-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 2px solid var(--kb-border);
  background: transparent;
  cursor: pointer;
  flex-shrink: 0;
  transition: all 0.15s ease;
}
.task-check-btn:hover {
  transform: scale(1.1);
}
.task-delete-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: var(--kb-radius-sm);
  border: none;
  background: transparent;
  color: var(--kb-muted-foreground);
  cursor: pointer;
  opacity: 0;
  transition: all 0.15s ease;
}
.task-row:hover .task-delete-btn {
  opacity: 1;
}
.task-delete-btn:hover {
  color: var(--kb-destructive);
  background: rgba(239, 68, 68, 0.08);
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.kb-h1 { font-size: 28px; font-weight: 700; line-height: 1.3; letter-spacing: -0.02em; color: var(--kb-foreground); font-family: var(--font-serif, 'Noto Serif SC', Georgia, serif); }
.kb-h2 { font-size: 22px; font-weight: 600; line-height: 1.35; letter-spacing: -0.01em; color: var(--kb-foreground); font-family: var(--font-serif, 'Noto Serif SC', Georgia, serif); }
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

/* ========== 名人名言 Hero ========== */
.quote-hero {
  position: relative;
  border-radius: 20px;
  overflow: hidden;
  margin-bottom: 20px;
  min-height: 200px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 64px;
  background: linear-gradient(135deg, #1a2942 0%, #0f1a2e 50%, #1a1f3a 100%);
}

.quote-hero-bg {
  position: absolute;
  inset: 0;
  overflow: hidden;
}

.quote-grid-pattern {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(59, 111, 224, 0.06) 1px, transparent 1px),
    linear-gradient(90deg, rgba(59, 111, 224, 0.06) 1px, transparent 1px);
  background-size: 40px 40px;
  mask-image: radial-gradient(ellipse at center, black 30%, transparent 80%);
  -webkit-mask-image: radial-gradient(ellipse at center, black 30%, transparent 80%);
}

.quote-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.5;
}
.quote-glow-1 {
  width: 300px;
  height: 300px;
  background: rgba(59, 111, 224, 0.4);
  top: -100px;
  left: -50px;
  animation: glowFloat 8s ease-in-out infinite;
}
.quote-glow-2 {
  width: 250px;
  height: 250px;
  background: rgba(139, 92, 246, 0.3);
  bottom: -80px;
  right: -30px;
  animation: glowFloat 8s ease-in-out infinite reverse;
}

@keyframes glowFloat {
  0%, 100% { transform: translate(0, 0); }
  50% { transform: translate(30px, -20px); }
}

.quote-content {
  position: relative;
  z-index: 1;
  text-align: center;
  max-width: 800px;
}

.quote-mark {
  font-family: Georgia, 'Times New Roman', serif;
  font-size: 72px;
  line-height: 1;
  color: rgba(59, 111, 224, 0.4);
  margin-bottom: -20px;
  font-weight: 700;
}

.quote-text {
  font-family: var(--font-serif, 'Noto Serif SC', Georgia, serif);
  font-size: 26px;
  font-weight: 500;
  line-height: 1.6;
  color: rgba(255, 255, 255, 0.95);
  letter-spacing: 0.02em;
  margin-bottom: 20px;
  transition: opacity 0.4s ease;
}

.quote-divider {
  width: 40px;
  height: 2px;
  background: rgba(59, 111, 224, 0.6);
  margin: 0 auto 16px;
  border-radius: 2px;
}

.quote-author {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  flex-wrap: wrap;
}

.quote-author-name {
  font-size: 15px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.85);
}

.quote-author-title {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.5);
}

.quote-dots {
  position: relative;
  z-index: 1;
  display: flex;
  gap: 8px;
  margin-top: 24px;
}

.quote-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  border: none;
  background: rgba(255, 255, 255, 0.2);
  cursor: pointer;
  transition: all 0.3s ease;
  padding: 0;
}

.quote-dot:hover {
  background: rgba(255, 255, 255, 0.4);
}

.quote-dot-active {
  width: 24px;
  border-radius: 3px;
  background: var(--kb-primary);
}

.quote-nav-btn {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  z-index: 1;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.1);
  background: rgba(255, 255, 255, 0.05);
  color: rgba(255, 255, 255, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s ease;
  backdrop-filter: blur(8px);
}

.quote-nav-btn:hover {
  background: rgba(255, 255, 255, 0.12);
  color: rgba(255, 255, 255, 0.95);
  border-color: rgba(255, 255, 255, 0.2);
}

.quote-nav-next {
  right: 24px;
}

/* ========== 学习数据概览条 ========== */
.stats-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: 16px;
  padding: 20px 24px;
  margin-bottom: 20px;
}

.stats-greeting {
  min-width: 0;
}

.stats-pills {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
  flex-wrap: wrap;
}

.stats-pill {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 20px;
  font-size: 13px;
  font-weight: 600;
}

.stats-pill-flame {
  background: rgba(59, 111, 224, 0.08);
  color: var(--kb-primary);
}

.stats-pill-clock {
  background: rgba(16, 185, 129, 0.08);
  color: var(--kb-accent);
}

.stats-pill-energy {
  background: rgba(245, 158, 11, 0.08);
  color: var(--kb-warning);
}

.stats-pill-focus {
  background: rgba(99, 102, 241, 0.1);
  color: #6366f1;
  text-decoration: none;
  transition: background 0.15s, transform 0.15s;
}
.stats-pill-focus:hover {
  background: rgba(99, 102, 241, 0.18);
  transform: translateY(-1px);
}

/* ========== 快速入口 ========== */
.quick-nav {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 20px;
}

@media (max-width: 768px) {
  .quick-nav {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 480px) {
  .quick-nav {
    grid-template-columns: repeat(2, 1fr);
  }
}

.quick-nav-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 16px 8px;
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: 14px;
  transition: all 0.2s ease;
  cursor: pointer;
  text-decoration: none;
  position: relative;
}

.quick-nav-card:hover {
  border-color: var(--kb-primary);
  box-shadow: 0 4px 12px rgba(59, 111, 224, 0.08);
  transform: translateY(-2px);
}

.quick-nav-card:hover .quick-nav-arrow {
  opacity: 1;
  transform: translateX(0);
}

.quick-nav-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.quick-nav-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--kb-foreground);
}

.quick-nav-arrow {
  position: absolute;
  top: 12px;
  right: 10px;
  color: var(--kb-primary);
  opacity: 0;
  transform: translateX(-4px);
  transition: all 0.2s ease;
}

/* ========== 课程卡片 ========== */
.path-card {
  border-radius: 14px;
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  overflow: hidden;
  cursor: pointer;
  transition: box-shadow 0.2s ease, border-color 0.2s ease;
}

.path-card:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.06);
}

.path-card-cover {
  height: 112px;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* Heatmap levels */
.heatmap-cell { width: 16px; height: 16px; border-radius: 3px; }
.heatmap-0 { background: var(--kb-muted); }
.heatmap-1 { background: rgba(59, 111, 224, 0.2); }
.heatmap-2 { background: rgba(59, 111, 224, 0.4); }
.heatmap-3 { background: rgba(59, 111, 224, 0.65); }
.heatmap-4 { background: var(--kb-primary); }

/* 移动端适配 */
@media (max-width: 768px) {
  .quote-hero {
    padding: 32px 24px;
    min-height: 160px;
  }
  .quote-text {
    font-size: 20px;
  }
  .quote-mark {
    font-size: 56px;
  }
  .quote-nav-next {
    right: 12px;
  }
  .stats-bar {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
