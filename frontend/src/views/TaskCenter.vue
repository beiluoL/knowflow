<template>
  <div class="animate-fade-in task-center-page">
    <!-- ===== 页面头部 ===== -->
    <div class="flex items-center justify-between mb-6 flex-wrap gap-3">
      <div>
        <h1 class="kb-h1 mb-1">任务中心</h1>
        <p class="kb-body-sm" style="font-size: 14px;">完成每日任务，持续成长升级</p>
      </div>
      <button
        type="button"
        class="inline-flex items-center gap-2 px-4 py-2.5 rounded-lg text-sm font-semibold btn-checkin-primary"
        @click="router.push('/check-in')"
      >
        <Icon name="calendar-check" :size="16" />
        <span>今日打卡</span>
      </button>
    </div>

    <!-- ===== 主体 + 侧边栏 ===== -->
    <div class="grid grid-cols-1 lg:grid-cols-[1fr_320px] gap-6">
      <!-- 左侧主内容 -->
      <div class="space-y-6 min-w-0">
        <!-- 用户等级概览卡 -->
        <div class="relative overflow-hidden rounded-2xl p-6 level-card">
          <div class="deco-circle deco-1"></div>
          <div class="deco-circle deco-2"></div>
          <div class="relative flex flex-col lg:flex-row items-start lg:items-center gap-6">
            <!-- 用户信息 -->
            <div class="flex items-center gap-4 shrink-0">
              <div class="w-14 h-14 rounded-full flex items-center justify-center text-lg font-bold avatar-circle">
                {{ userInitial }}
              </div>
              <div>
                <div class="flex items-center gap-2 mb-1">
                  <span class="text-base font-semibold">{{ user.displayName || '探索者' }}</span>
                  <span class="text-xs font-bold px-2 py-0.5 rounded-full level-badge tabular-nums">Lv.{{ user.level }}</span>
                </div>
                <div class="flex items-center gap-2 text-xs" style="color: rgba(255,255,255,0.85);">
                  <Icon name="award" :size="14" />
                  <span>当前称号：{{ user.title || '知识追寻者' }}</span>
                </div>
              </div>
            </div>

            <!-- 经验值进度条 -->
            <div class="flex-1 w-full lg:mx-4 min-w-0">
              <div class="flex items-center justify-between mb-2 text-xs" style="color: rgba(255,255,255,0.9);">
                <span class="flex items-center gap-2">
                  <Icon name="zap" :size="14" />
                  经验值
                </span>
                <span class="tabular-nums"><span class="font-semibold text-sm">{{ formatExp(user.exp) }}</span> / {{ formatExp(user.maxExp) }} EXP</span>
              </div>
              <div class="h-3 rounded-full overflow-hidden exp-bar-bg">
                <div
                  class="h-full rounded-full flex items-center justify-end pr-2 transition-[width] duration-500 exp-bar-fill"
                  :style="{ width: `${expPercent}%` }"
                >
                  <span class="text-[10px] font-bold tabular-nums" style="color: var(--kb-primary);">{{ expPercent }}%</span>
                </div>
              </div>
            </div>

            <!-- 统计 -->
            <div class="flex items-center gap-5 shrink-0">
              <div class="text-center">
                <div class="text-xs mb-0.5" style="color: rgba(255,255,255,0.85);">距升级还需</div>
                <div class="text-lg font-bold tabular-nums">{{ formatExp(user.maxExp - user.exp) }} EXP</div>
              </div>
              <div class="w-px h-10 stat-divider"></div>
              <div class="text-center">
                <div class="flex items-center gap-2 text-xs mb-0.5 justify-center" style="color: rgba(255,255,255,0.85);">
                  <Icon name="flame" :size="14" />
                  <span>连续打卡</span>
                </div>
                <div class="text-lg font-bold tabular-nums">{{ user.streakDays }} 天</div>
              </div>
            </div>
          </div>
        </div>

        <!-- 任务标签栏 -->
        <div class="bg-card border rounded-xl p-2 flex items-center gap-1 flex-wrap" style="border-color: var(--kb-border);">
          <button
            v-for="tab in taskTabs"
            :key="tab.value"
            type="button"
            class="task-tab"
            :class="{ active: currentTab === tab.value }"
            @click="currentTab = tab.value"
          >
            <Icon :name="tab.icon" :size="16" />
            <span>{{ tab.label }}</span>
            <span class="count tabular-nums">{{ getTaskCount(tab.value) }}</span>
          </button>
        </div>

        <!-- 任务列表 -->
        <div class="space-y-3">
          <div
            v-for="task in filteredTasks"
            :key="task.id"
            class="task-item"
            :class="{ done: task.status === 'done' }"
          >
            <div class="task-icon" :class="task.color">
              <Icon :name="task.icon" :size="20" />
            </div>
            <div class="flex-1 min-w-0">
              <div class="flex items-center gap-2 mb-1">
                <span class="task-name kb-h4">{{ task.name }}</span>
              </div>
              <p class="kb-body-sm">{{ task.desc }}</p>
            </div>
            <span class="exp-badge tabular-nums">+{{ task.exp }} EXP</span>
            <span v-if="task.status === 'done'" class="btn-done">
              <Icon name="check-circle" :size="14" />
              已完成
            </span>
            <button
              v-else
              type="button"
              class="btn-complete"
              @click="goToTask(task)"
            >去完成</button>
          </div>
        </div>
      </div>

      <!-- 右侧侧边栏 -->
      <aside class="space-y-6">
        <!-- 本周进度环 -->
        <div class="sidebar-card">
          <div class="flex items-center justify-between mb-4">
            <h3 class="kb-h4">本周进度</h3>
            <Icon name="trending-up" :size="16" style="color: var(--kb-accent);" />
          </div>
          <div class="flex flex-col items-center">
            <div class="relative w-32 h-32">
              <svg class="w-full h-full -rotate-90" viewBox="0 0 120 120">
                <circle cx="60" cy="60" r="50" fill="none" stroke="var(--kb-muted)" stroke-width="10" />
                <circle
                  cx="60"
                  cy="60"
                  r="50"
                  fill="none"
                  stroke="var(--kb-primary)"
                  stroke-width="10"
                  stroke-linecap="round"
                  :stroke-dasharray="314"
                  :stroke-dashoffset="314 - 314 * (weekProgress.percent / 100)"
                />
              </svg>
              <div class="absolute inset-0 flex flex-col items-center justify-center">
                <span class="text-2xl font-bold tabular-nums" style="color: var(--kb-foreground);">{{ weekProgress.percent }}%</span>
                <span class="text-xs" style="color: var(--kb-muted-foreground);">完成率</span>
              </div>
            </div>
            <div class="grid grid-cols-3 gap-2 w-full mt-4 pt-4 border-t" style="border-color: var(--kb-border);">
              <div class="text-center">
                <div class="text-sm font-semibold tabular-nums" style="color: var(--kb-foreground);">{{ weekProgress.total }}</div>
                <div class="text-xs" style="color: var(--kb-muted-foreground);">总任务</div>
              </div>
              <div class="text-center">
                <div class="text-sm font-semibold tabular-nums" style="color: var(--kb-accent);">{{ weekProgress.done }}</div>
                <div class="text-xs" style="color: var(--kb-muted-foreground);">已完成</div>
              </div>
              <div class="text-center">
                <div class="text-sm font-semibold tabular-nums" style="color: var(--kb-primary);">{{ weekProgress.doing }}</div>
                <div class="text-xs" style="color: var(--kb-muted-foreground);">进行中</div>
              </div>
            </div>
          </div>
        </div>

        <!-- 连续打卡日历 -->
        <div class="sidebar-card">
          <div class="flex items-center justify-between mb-4">
            <h3 class="kb-h4">连续打卡</h3>
            <span class="flex items-center gap-2 text-xs font-semibold tabular-nums" style="color: var(--kb-warning);">
              <Icon name="flame" :size="14" />
              {{ user.streakDays }} 天
            </span>
          </div>
          <div class="grid grid-cols-7 gap-1 mb-2">
            <div v-for="day in weekdayLabels" :key="day" class="text-xs text-center" style="color: var(--kb-muted-foreground);">{{ day }}</div>
          </div>
          <div class="grid grid-cols-7 gap-1 place-items-center">
            <div
              v-for="(day, idx) in recentDays"
              :key="idx"
              class="calendar-day"
              :class="{ checked: day.checked, today: day.isToday }"
            >{{ day.date }}</div>
          </div>
          <button
            type="button"
            class="mt-4 w-full inline-flex items-center justify-center gap-2 px-4 py-2 rounded-lg text-sm font-semibold border btn-checkin-outline"
            @click="router.push('/check-in')"
          >
            <Icon name="calendar-check" :size="16" />
            去打卡
          </button>
        </div>

        <!-- 快捷入口 -->
        <div class="sidebar-card">
          <h3 class="kb-h4 mb-3">快捷入口</h3>
          <div class="space-y-1">
            <router-link
              v-for="link in quickLinks"
              :key="link.name"
              :to="link.path"
              class="quick-link"
            >
              <div
                class="w-8 h-8 rounded-lg flex items-center justify-center shrink-0"
                :style="`background: ${link.bg}; color: ${link.color};`"
              >
                <Icon :name="link.icon" :size="16" />
              </div>
              <span class="flex-1">{{ link.name }}</span>
              <Icon name="chevron-right" :size="16" style="color: var(--kb-muted-foreground);" />
            </router-link>
          </div>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
// 任务中心页：每日/每周/每月任务 + 用户等级 + 打卡升级 + 里程碑奖励。
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { notify } from '@/utils/toast'
import { userApi } from '@/api'

const router = useRouter()

// ===== 用户状态（mock 数据；后端无该接口，注释说明） =====
// 后端暂无任务/打卡/经验值接口，前端先用 mock 数据展示完整流程。
const user = ref({
  displayName: '探索者 YU',
  level: 12,
  title: '知识追寻者',
  exp: 2450,
  maxExp: 3500,
  streakDays: 42,
})
const userInitial = computed(() => {
  const name = user.value.displayName || 'YU'
  return name.slice(-2).toUpperCase()
})
const expPercent = computed(() => {
  return Math.min(100, Math.round((user.value.exp / user.value.maxExp) * 100))
})
const formatExp = (n: number) => n.toLocaleString()

// ===== 任务标签 =====
const taskTabs = [
  { value: 'daily' as const, label: '每日任务', icon: 'sun' },
  { value: 'weekly' as const, label: '每周任务', icon: 'calendar' },
  { value: 'monthly' as const, label: '每月任务', icon: 'calendar' },
]
const currentTab = ref<'daily' | 'weekly' | 'monthly'>('daily')

interface Task {
  id: string
  type: 'daily' | 'weekly' | 'monthly'
  name: string
  desc: string
  exp: number
  icon: string
  color: 'primary' | 'accent' | 'warning' | 'destructive'
  status: 'done' | 'todo'
  link?: string
}

const tasks = ref<Task[]>([
  // 每日任务
  { id: 'd1', type: 'daily', name: '阅读 1 篇文档', desc: '阅读任意一篇知识库文档，拓宽知识视野', exp: 50, icon: 'file-text', color: 'accent', status: 'done', link: '/knowledge' },
  { id: 'd2', type: 'daily', name: '学习 3 张闪卡', desc: '通过闪卡复习巩固已学知识点', exp: 30, icon: 'layers', color: 'primary', status: 'done', link: '/learning/flashcards' },
  { id: 'd3', type: 'daily', name: '完成 1 次代码练习', desc: '动手实践，在代码练习中提升编程能力', exp: 80, icon: 'code', color: 'warning', status: 'done', link: '/learning/code-practice' },
  { id: 'd4', type: 'daily', name: '回答 1 个社区问题', desc: '在社区分享你的见解，帮助他人解决问题', exp: 50, icon: 'message-square', color: 'primary', status: 'todo', link: '/community' },
  { id: 'd5', type: 'daily', name: '复习 10 个知识点', desc: '根据复习计划回顾已学内容，加深记忆', exp: 40, icon: 'refresh-cw', color: 'accent', status: 'todo', link: '/learning/review' },
  { id: 'd6', type: 'daily', name: '学习 30 分钟', desc: '沉浸学习，专注提升自我', exp: 60, icon: 'clock', color: 'destructive', status: 'todo', link: '/learning/mode' },
  // 每周任务
  { id: 'w1', type: 'weekly', name: '完成 5 篇文档阅读', desc: '本周累计阅读 5 篇知识库文档（3/5）', exp: 200, icon: 'book-open', color: 'accent', status: 'todo', link: '/knowledge' },
  { id: 'w2', type: 'weekly', name: '学习 21 张闪卡', desc: '本周累计学习 21 张闪卡（8/21）', exp: 150, icon: 'layers', color: 'primary', status: 'todo', link: '/learning/flashcards' },
  { id: 'w3', type: 'weekly', name: '完成 3 次代码练习', desc: '本周累计完成 3 次代码练习（1/3）', exp: 300, icon: 'code', color: 'warning', status: 'todo', link: '/learning/code-practice' },
  { id: 'w4', type: 'weekly', name: '发布 2 篇笔记', desc: '整理学习心得，发布 2 篇笔记（0/2）', exp: 180, icon: 'edit', color: 'primary', status: 'todo', link: '/notes' },
  // 每月任务
  { id: 'm1', type: 'monthly', name: '完成 1 条学习路径', desc: '本月完成一条完整的学习路径', exp: 800, icon: 'route', color: 'primary', status: 'todo', link: '/learning/paths' },
  { id: 'm2', type: 'monthly', name: '获得 1 个新称号', desc: '通过持续学习解锁新的专属称号', exp: 500, icon: 'award', color: 'warning', status: 'todo', link: '/kb-titles' },
  { id: 'm3', type: 'monthly', name: '累计学习 20 小时', desc: '本月累计有效学习时长达到 20 小时（8.5/20）', exp: 600, icon: 'clock', color: 'accent', status: 'todo', link: '/learning/mode' },
])

const filteredTasks = computed(() => tasks.value.filter((t) => t.type === currentTab.value))

const getTaskCount = (type: 'daily' | 'weekly' | 'monthly'): string => {
  const list = tasks.value.filter((t) => t.type === type)
  const done = list.filter((t) => t.status === 'done').length
  return `${done}/${list.length}`
}

const goToTask = (task: Task) => {
  if (task.link) {
    router.push(task.link)
  } else {
    notify(`任务「${task.name}」即将上线`, 'info')
  }
}

// ===== 本周进度 =====
const weekProgress = computed(() => {
  const list = tasks.value.filter((t) => t.type === 'daily' || t.type === 'weekly')
  const done = list.filter((t) => t.status === 'done').length
  const total = list.length
  const doing = list.filter((t) => t.status === 'todo').length
  return {
    total,
    done,
    doing,
    percent: total > 0 ? Math.round((done / total) * 100) : 0,
  }
})

// ===== 连续打卡日历（最近一周） =====
const weekdayLabels = ['一', '二', '三', '四', '五', '六', '日']

const recentDays = computed(() => {
  const today = new Date()
  const dayOfWeek = (today.getDay() + 6) % 7 // 周一为 0
  // 显示本周一到今天
  const result: { date: number; checked: boolean; isToday: boolean }[] = []
  for (let i = 0; i <= dayOfWeek; i++) {
    const d = new Date(today)
    d.setDate(today.getDate() - dayOfWeek + i)
    result.push({
      date: d.getDate(),
      checked: i < dayOfWeek, // 前几天已打卡
      isToday: i === dayOfWeek,
    })
  }
  // 如果不够 7 天，前面用空值补（已在前面用 weekdayLabels 显示）
  return result
})

// ===== 快捷入口 =====
const quickLinks = [
  { name: '成就系统', path: '/achievements', icon: 'trophy', bg: 'rgba(245,158,11,0.12)', color: 'var(--kb-warning)' },
  { name: '知识库称号', path: '/kb-titles', icon: 'award', bg: 'rgba(59,111,224,0.12)', color: 'var(--kb-primary)' },
  { name: '学习报告', path: '/learning/center', icon: 'bar-chart-3', bg: 'rgba(16,185,129,0.12)', color: 'var(--kb-accent)' },
]

// ===== 拉取用户信息 =====
async function loadUser(): Promise<void> {
  try {
    const stats = await userApi.stats()
    if (stats) {
      // 后端字段可能不同，做容错处理
      if (stats.displayName) user.value.displayName = stats.displayName
    }
  } catch {
    // 后端无接口时静默使用 mock 数据
  }
}

onMounted(() => {
  void loadUser()
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

/* ===== 用户等级卡 ===== */
.level-card {
  background: linear-gradient(135deg, var(--kb-primary), rgba(59, 111, 224, 0.75));
  color: var(--kb-primary-foreground);
}
.deco-circle {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.08);
  pointer-events: none;
}
.deco-1 { right: -2rem; top: -2rem; width: 12rem; height: 12rem; background: rgba(255,255,255,0.08); }
.deco-2 { right: -4rem; top: 2.5rem; width: 8rem; height: 8rem; background: rgba(255,255,255,0.06); }
.deco-3 { left: 30%; bottom: -3rem; width: 6rem; height: 6rem; background: rgba(255,255,255,0.05); }

.avatar-circle {
  background: rgba(255, 255, 255, 0.25);
  color: var(--kb-primary-foreground);
  border: 2px solid rgba(255, 255, 255, 0.4);
}
.level-badge {
  background: rgba(255, 255, 255, 0.25);
  color: var(--kb-primary-foreground);
}
.exp-bar-bg {
  background: rgba(255, 255, 255, 0.25);
}
.exp-bar-fill {
  background: var(--kb-primary-foreground);
}
.stat-divider {
  background: rgba(255, 255, 255, 0.25);
}

/* ===== 打卡按钮 ===== */
.btn-checkin-primary {
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  box-shadow: 0 2px 8px rgba(59, 111, 224, 0.2);
  transition: opacity 0.15s, transform 0.15s;
  border: none;
  cursor: pointer;
}
.btn-checkin-primary:hover {
  opacity: 0.9;
  transform: translateY(-1px);
}

.btn-checkin-outline {
  background: transparent;
  color: var(--kb-primary);
  border-color: var(--kb-primary);
  cursor: pointer;
  transition: background-color 0.15s;
}
.btn-checkin-outline:hover {
  background: rgba(59, 111, 224, 0.08);
}

/* ===== 任务标签 ===== */
.task-tab {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: var(--kb-radius-md);
  font-size: 13px;
  font-weight: 500;
  color: var(--kb-muted-foreground);
  background: transparent;
  border: none;
  cursor: pointer;
  transition: background-color 0.15s, color 0.15s;
}
.task-tab:hover {
  background: var(--kb-muted);
  color: var(--kb-foreground);
}
.task-tab.active {
  background: rgba(59, 111, 224, 0.08);
  color: var(--kb-primary);
}
.task-tab .count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 28px;
  height: 18px;
  padding: 0 6px;
  border-radius: 9px;
  font-size: 11px;
  font-weight: 600;
  background: var(--kb-muted);
  color: var(--kb-muted-foreground);
}
.task-tab.active .count {
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
}

/* ===== 任务项 ===== */
.task-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-lg);
  background: var(--kb-card);
  transition: box-shadow 0.2s, border-color 0.2s;
}
.task-item:hover {
  border-color: rgba(59, 111, 224, 0.4);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}
.task-item.done {
  opacity: 0.7;
}

.task-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: var(--kb-radius-md);
  flex-shrink: 0;
}
.task-icon.primary { background: rgba(59, 111, 224, 0.1); color: var(--kb-primary); }
.task-icon.accent { background: rgba(16, 185, 129, 0.1); color: var(--kb-accent); }
.task-icon.warning { background: rgba(245, 158, 11, 0.1); color: var(--kb-warning); }
.task-icon.destructive { background: rgba(239, 68, 68, 0.1); color: var(--kb-destructive); }

.exp-badge {
  font-size: 12px;
  font-weight: 600;
  padding: 4px 8px;
  border-radius: var(--kb-radius-sm);
  background: rgba(245, 158, 11, 0.1);
  color: var(--kb-warning);
  white-space: nowrap;
  flex-shrink: 0;
}

.btn-done {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  font-weight: 600;
  color: var(--kb-accent);
  padding: 6px 12px;
  border-radius: var(--kb-radius-sm);
  background: rgba(16, 185, 129, 0.1);
  flex-shrink: 0;
}

.btn-complete {
  font-size: 12px;
  font-weight: 600;
  padding: 6px 14px;
  border-radius: var(--kb-radius-sm);
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  border: none;
  cursor: pointer;
  transition: opacity 0.15s;
  flex-shrink: 0;
}
.btn-complete:hover {
  opacity: 0.9;
}

/* ===== 侧边栏卡片 ===== */
.sidebar-card {
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-lg);
  padding: 20px;
}

.calendar-day {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: var(--kb-radius-sm);
  font-size: 12px;
  color: var(--kb-muted-foreground);
  background: var(--kb-muted);
}
.calendar-day.checked {
  background: rgba(16, 185, 129, 0.15);
  color: var(--kb-accent);
  font-weight: 600;
}
.calendar-day.today {
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  font-weight: 600;
}
.calendar-day.today.checked {
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
}

.quick-link {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: var(--kb-radius-sm);
  color: var(--kb-foreground);
  text-decoration: none;
  font-size: 13px;
  transition: background-color 0.15s;
}
.quick-link:hover {
  background: var(--kb-muted);
}

/* ===== 响应式 ===== */
@media (max-width: 1024px) {
  .level-card .relative > .flex > div.text-center + .w-px + div.text-center,
  .level-card .stat-divider {
    display: none;
  }
}

@media (max-width: 768px) {
  .task-item {
    flex-wrap: wrap;
  }
  .task-item .exp-badge {
    order: 3;
  }
  .task-item .btn-complete,
  .task-item .btn-done {
    order: 4;
    margin-left: auto;
  }
}
</style>
