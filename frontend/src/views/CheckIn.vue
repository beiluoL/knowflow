<template>
  <div class="animate-fade-in check-in-page">
    <!-- ===== 面包屑 + 标题 ===== -->
    <div class="flex items-center gap-2 text-xs mb-4" style="color: var(--kb-muted-foreground);">
      <router-link to="/tasks" class="hover:underline" style="color: var(--kb-primary);">任务中心</router-link>
      <Icon name="chevron-right" :size="12" />
      <span>每日打卡</span>
    </div>

    <div class="flex items-center justify-between mb-6 flex-wrap gap-3">
      <div>
        <h1 class="kb-h1 mb-1">每日打卡</h1>
        <p class="kb-body-sm" style="font-size: 14px;">坚持学习，持续成长</p>
      </div>
      <div class="text-right">
        <div class="text-sm font-semibold" style="color: var(--kb-foreground);">{{ todayText }}</div>
        <div class="text-xs" style="color: var(--kb-muted-foreground);">{{ weekdayText }}</div>
      </div>
    </div>

    <!-- ===== 打卡主卡片 ===== -->
    <div class="checkin-main-card relative overflow-hidden rounded-2xl p-8 mb-6">
      <div class="deco-circle deco-1"></div>
      <div class="deco-circle deco-2"></div>
      <div class="deco-circle deco-3"></div>
      <div class="relative">
        <!-- 连续打卡天数 -->
        <div class="flex items-center justify-center gap-2 mb-6">
          <Icon name="flame" :size="32" style="color: rgba(255,255,255,0.9);" />
          <span class="text-2xl font-bold tabular-nums">连续打卡 {{ user.streakDays }} 天</span>
        </div>
        <!-- 打卡状态 -->
        <div class="flex flex-col items-center gap-4 mb-8">
          <div
            class="w-16 h-16 rounded-full flex items-center justify-center"
            :style="checkinDone
              ? 'background: rgba(16,185,129,0.25); border: 2px solid rgba(16,185,129,0.6);'
              : 'background: rgba(255,255,255,0.15); border: 2px solid rgba(255,255,255,0.4);'"
          >
            <Icon :name="checkinDone ? 'check' : 'calendar-check'" :size="32" style="color: rgba(255,255,255,0.95);" />
          </div>
          <div>
            <div class="text-lg font-semibold mb-1">{{ checkinDone ? '今日已打卡' : '今日尚未打卡' }}</div>
            <div class="text-sm" style="color: rgba(255,255,255,0.8);">
              {{ checkinDone ? `打卡时间 ${checkinTime}` : '点击下方按钮立即打卡' }}
            </div>
          </div>
        </div>
        <!-- 打卡按钮 -->
        <div class="flex justify-center">
          <button
            type="button"
            class="btn-checkin-action"
            :disabled="checkinDone || checkinLoading"
            @click="doCheckin"
          >
            <Icon name="calendar-check" :size="20" />
            {{ checkinDone ? '今日已完成' : (checkinLoading ? '打卡中…' : '立即打卡') }}
          </button>
        </div>
      </div>
    </div>

    <!-- ===== 升级进度 ===== -->
    <div class="rounded-xl border p-6 mb-6" style="background: var(--kb-card); border-color: var(--kb-border);">
      <div class="flex items-center justify-between mb-3">
        <h3 class="kb-h4">升级进度</h3>
        <div v-if="checkinExp > 0" class="exp-toast">
          <Icon name="zap" :size="14" />
          <span class="tabular-nums">本次打卡 +{{ checkinExp }} EXP</span>
        </div>
      </div>
      <div class="flex items-center justify-between text-xs mb-2" style="color: var(--kb-muted-foreground);">
        <span class="flex items-center gap-2">
          <Icon name="award" :size="14" />
          <span class="tabular-nums">Lv.{{ user.level }} {{ user.title }}</span>
        </span>
        <span class="tabular-nums">{{ formatExp(user.exp) }} / {{ formatExp(user.maxExp) }} EXP</span>
      </div>
      <div class="h-3 rounded-full overflow-hidden" style="background: var(--kb-muted);">
        <div
          class="h-full rounded-full transition-[width] duration-1000"
          :style="{ width: `${expPercent}%`, background: 'var(--kb-primary)' }"
        ></div>
      </div>
      <div class="flex items-center justify-between text-xs mt-2" style="color: var(--kb-muted-foreground);">
        <span class="tabular-nums">{{ expPercent }}%</span>
        <span class="tabular-nums">距 Lv.{{ user.level + 1 }} 还需 {{ formatExp(user.maxExp - user.exp) }} EXP</span>
      </div>
    </div>

    <!-- ===== 连续打卡里程碑 ===== -->
    <div class="mb-6">
      <h3 class="kb-h3 mb-4">连续打卡里程碑</h3>
      <div class="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <div
          v-for="m in milestones"
          :key="m.days"
          class="milestone-card"
          :class="{ achieved: m.achieved, locked: !m.achieved }"
        >
          <div v-if="m.achieved" class="milestone-check">
            <Icon name="check" :size="14" />
          </div>
          <div class="milestone-icon">
            <Icon :name="m.achieved ? m.icon : 'lock'" :size="24" />
          </div>
          <div class="text-sm font-bold mb-1 tabular-nums" :style="m.achieved ? 'color: var(--kb-primary);' : ''">{{ m.days }} 天</div>
          <div class="text-xs font-semibold mb-2 tabular-nums" :style="m.achieved ? 'color: var(--kb-warning);' : 'color: var(--kb-muted-foreground);'">+{{ m.exp }} EXP</div>
          <div class="text-xs" style="color: var(--kb-muted-foreground);">{{ m.desc }}</div>
        </div>
      </div>
    </div>

    <!-- ===== 本月打卡日历 + 排行榜 ===== -->
    <div class="grid grid-cols-1 lg:grid-cols-[1fr_280px] gap-6">
      <!-- 月历 -->
      <div class="rounded-xl border p-6" style="background: var(--kb-card); border-color: var(--kb-border);">
        <div class="flex items-center justify-between mb-4">
          <h3 class="kb-h4">{{ currentMonthText }}打卡日历</h3>
          <span class="text-sm font-semibold tabular-nums" style="color: var(--kb-primary);">{{ monthCheckedCount }}/{{ monthTotalDays }} 天</span>
        </div>
        <div class="calendar-grid mb-2">
          <div v-for="day in weekdayLabels" :key="day" class="cal-header">{{ day }}</div>
        </div>
        <div class="calendar-grid">
          <div
            v-for="(day, idx) in monthCalendar"
            :key="idx"
            class="cal-day"
            :class="[day.empty ? 'empty' : '', day.checked ? 'checked' : '', day.isToday ? 'today' : '', day.future ? 'future' : '']"
          >{{ day.empty ? '' : day.date }}</div>
        </div>
      </div>

      <!-- 打卡排行榜 -->
      <div class="rounded-xl border p-5" style="background: var(--kb-card); border-color: var(--kb-border);">
        <div class="flex items-center gap-2 mb-4">
          <Icon name="trophy" :size="16" style="color: var(--kb-warning);" />
          <h3 class="kb-h4">本周打卡榜</h3>
        </div>
        <div class="space-y-1">
          <div
            v-for="rank in rankList"
            :key="rank.rank"
            class="rank-item"
            :class="{ self: rank.isSelf }"
          >
            <span class="rank-num" :class="`top${rank.rank}`">{{ rank.rank }}</span>
            <div
              class="w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold"
              :style="`background: ${rank.bg}; color: ${rank.color};`"
            >{{ rank.initial }}</div>
            <span class="flex-1 text-sm font-medium">{{ rank.name }}</span>
            <span class="text-xs font-semibold inline-flex items-center gap-1 tabular-nums" :style="`color: ${rank.color};`">
              <Icon name="flame" :size="12" />
              {{ rank.streakDays }} 天
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// 每日打卡独立页：从 TaskCenter 抽取的完整打卡流程
// 包含：打卡主卡 + 升级进度 + 里程碑 + 本月打卡日历 + 排行榜
import { ref, computed, onMounted } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import { notify, getApiError } from '@/utils/toast'
import { userApi, checkinApi } from '@/api'

// ===== 用户状态（等级/经验来自 userApi.stats 实时派生，连续天数以打卡接口为准） =====
const user = ref({
  displayName: '学习者',
  level: 1,
  title: '知识追寻者',
  exp: 0,
  maxExp: 100,
  streakDays: 0,
})

const expPercent = computed(() => {
  return Math.min(100, Math.round((user.value.exp / user.value.maxExp) * 100))
})

const formatExp = (n: number) => n.toLocaleString()

// ===== 打卡状态 =====
const checkinDone = ref(false)
const checkinTime = ref('')
const checkinLoading = ref(false)
const checkinExp = ref(0)

const doCheckin = async () => {
  if (checkinDone.value || checkinLoading.value) return
  checkinLoading.value = true
  try {
    const res = await checkinApi.checkIn()
    checkinDone.value = true
    const now = new Date()
    checkinTime.value = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`
    if (res.alreadyChecked) {
      user.value.streakDays = res.continuousDays
      notify('今日已打卡', 'info')
      return
    }
    checkinExp.value = res.rewardExp
    user.value.streakDays = res.continuousDays
    user.value.exp = Math.min(user.value.maxExp, user.value.exp + res.rewardExp)
    // 将今日标记进本月打卡日历
    if (!monthCheckedDays.value.includes(now.getDate())) {
      monthCheckedDays.value = [...monthCheckedDays.value, now.getDate()]
    }
    notify(`打卡成功！获得 +${res.rewardExp} EXP`, 'success')
  } catch (e: unknown) {
    notify(getApiError(e, '打卡失败，请稍后重试'), 'error')
  } finally {
    checkinLoading.value = false
  }
}

// ===== 里程碑 =====
const milestones = computed(() => [
  { days: 7, exp: 100, desc: '坚持一周徽章', icon: 'award', achieved: user.value.streakDays >= 7 },
  { days: 14, exp: 200, desc: '专属头像框', icon: 'image', achieved: user.value.streakDays >= 14 },
  { days: 30, exp: 500, desc: '月度坚持称号', icon: 'award', achieved: user.value.streakDays >= 30 },
  { days: 100, exp: 1000, desc: '百日大师称号 + 限定徽章', icon: 'award', achieved: user.value.streakDays >= 100 },
])

// ===== 日期文本 =====
const todayText = computed(() => {
  const d = new Date()
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`
})
const weekdayText = computed(() => {
  const d = new Date()
  const labels = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  return labels[d.getDay()]
})
const currentMonthText = computed(() => {
  const d = new Date()
  return `${d.getMonth() + 1}月`
})

// ===== 日期工具 =====
const weekdayLabels = ['一', '二', '三', '四', '五', '六', '日']

// ===== 本月打卡日历（基于后端返回的 monthCheckedDays） =====
const monthCheckedDays = ref<number[]>([])

const monthCalendar = computed(() => {
  const today = new Date()
  const year = today.getFullYear()
  const month = today.getMonth()
  const firstDay = new Date(year, month, 1)
  const lastDay = new Date(year, month + 1, 0)
  // 周一为起始：getDay() 周日是 0
  const startOffset = (firstDay.getDay() + 6) % 7
  const totalDays = lastDay.getDate()
  const todayDate = today.getDate()
  const cells: { empty?: boolean; date?: number; checked?: boolean; isToday?: boolean; future?: boolean }[] = []
  // 起始空白
  for (let i = 0; i < startOffset; i++) cells.push({ empty: true })
  // 本月每天
  for (let d = 1; d <= totalDays; d++) {
    const isToday = d === todayDate
    const isFuture = d > todayDate
    const checked = monthCheckedDays.value.includes(d)
    cells.push({ date: d, isToday, future: isFuture, checked })
  }
  // 末尾补齐 7 的倍数
  while (cells.length % 7 !== 0) cells.push({ empty: true })
  return cells
})

const monthCheckedCount = computed(() => monthCalendar.value.filter((c) => c.checked).length)
const monthTotalDays = computed(() => new Date().getDate())

// ===== 排行榜 =====
const rankList = [
  { rank: 1, initial: 'LW', name: '李伟', streakDays: 365, bg: 'rgba(245,158,11,0.15)', color: 'var(--kb-warning)', isSelf: false },
  { rank: 2, initial: 'ZC', name: '张晨', streakDays: 128, bg: 'rgba(148,163,184,0.15)', color: 'var(--kb-muted-foreground)', isSelf: false },
  { rank: 3, initial: 'WF', name: '王芳', streakDays: 96, bg: 'rgba(180,120,80,0.15)', color: '#A0724A', isSelf: false },
  { rank: 4, initial: 'YU', name: '探索者 YU', streakDays: 42, bg: 'rgba(59,111,224,0.12)', color: 'var(--kb-primary)', isSelf: true },
  { rank: 5, initial: 'CX', name: '陈雪', streakDays: 38, bg: 'rgba(16,185,129,0.12)', color: 'var(--kb-accent)', isSelf: false },
]

// ===== 拉取用户信息与打卡状态 =====
async function loadUser(): Promise<void> {
  try {
    const stats = await userApi.stats()
    if (stats) {
      if (stats.displayName) user.value.displayName = stats.displayName
      if (stats.level) user.value.level = stats.level
      // 等级内进度：level = exp/100 + 1，当前段进度 = exp - (level-1)*100，满格 100
      if (typeof stats.exp === 'number' && stats.level) {
        user.value.exp = Math.max(0, Math.min(100, stats.exp - (stats.level - 1) * 100))
        user.value.maxExp = 100
      }
    }
  } catch {
    // 后端异常时静默使用默认值
  }
}

async function loadCheckinStatus(): Promise<void> {
  try {
    const status = await checkinApi.status()
    if (status) {
      checkinDone.value = status.checkedToday
      user.value.streakDays = status.continuousDays
      monthCheckedDays.value = status.monthCheckedDays || []
      if (status.checkedToday) {
        const now = new Date()
        checkinTime.value = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`
      }
    }
  } catch {
    // 静默处理，不阻断页面
  }
}

onMounted(() => {
  void loadUser()
  void loadCheckinStatus()
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
.kb-h3 { font-size: 18px; font-weight: 600; line-height: 1.4; color: var(--kb-foreground); }
.kb-h4 { font-size: 15px; font-weight: 600; line-height: 1.45; color: var(--kb-foreground); }
.kb-body-sm { font-size: 12px; line-height: 1.5; color: var(--kb-muted-foreground); }

/* ===== 打卡主卡片 ===== */
.checkin-main-card {
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

/* ===== 打卡按钮 ===== */
.btn-checkin-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 14px 40px;
  border-radius: var(--kb-radius-lg);
  font-size: 16px;
  font-weight: 700;
  background: var(--kb-card);
  color: var(--kb-primary);
  border: none;
  cursor: pointer;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  transition: transform 0.15s, box-shadow 0.15s;
}
.btn-checkin-action:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.15);
}
.btn-checkin-action:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

/* ===== 升级提示 toast ===== */
.exp-toast {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 4px 10px;
  border-radius: 9999px;
  font-size: 12px;
  font-weight: 600;
  background: rgba(16, 185, 129, 0.12);
  color: var(--kb-accent);
  animation: pop 0.3s ease-out;
}
@keyframes pop {
  from { transform: scale(0.8); opacity: 0; }
  to { transform: scale(1); opacity: 1; }
}

/* ===== 里程碑卡片 ===== */
.milestone-card {
  border-radius: var(--kb-radius-md);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  padding: 20px 16px;
  text-align: center;
  transition: border-color 0.15s, background-color 0.15s;
  position: relative;
}
.milestone-card.achieved {
  border-color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.04);
}
.milestone-card.locked {
  opacity: 0.6;
}
.milestone-card .milestone-icon {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 12px;
}
.milestone-card.achieved .milestone-icon {
  background: rgba(59, 111, 224, 0.12);
  color: var(--kb-primary);
}
.milestone-card.locked .milestone-icon {
  background: var(--kb-muted);
  color: var(--kb-muted-foreground);
}
.milestone-card .milestone-check {
  position: absolute;
  top: 10px;
  right: 10px;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
}

/* ===== 月历 ===== */
.calendar-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 4px;
}
.cal-header {
  text-align: center;
  font-size: 12px;
  font-weight: 600;
  padding: 6px 0;
  color: var(--kb-muted-foreground);
}
.cal-day {
  aspect-ratio: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  border-radius: var(--kb-radius-sm);
  background: var(--kb-background);
  color: var(--kb-foreground);
  transition: background 0.15s;
}
.cal-day.empty {
  background: transparent;
}
.cal-day.checked {
  background: rgba(59, 111, 224, 0.12);
  color: var(--kb-primary);
  font-weight: 600;
}
.cal-day.today {
  border: 2px solid var(--kb-primary);
}
.cal-day.future {
  color: var(--kb-muted-foreground);
  opacity: 0.5;
}

/* ===== 排行榜 ===== */
.rank-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: var(--kb-radius-sm);
  transition: background 0.15s;
}
.rank-item:hover {
  background: var(--kb-muted);
}
.rank-item.self {
  background: rgba(59, 111, 224, 0.06);
  border: 1px solid rgba(59, 111, 224, 0.2);
}
.rank-num {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  border-radius: 50%;
  background: var(--kb-muted);
  color: var(--kb-muted-foreground);
}
.rank-num.top1 {
  background: rgba(245, 158, 11, 0.15);
  color: var(--kb-warning);
}
.rank-num.top2 {
  background: rgba(148, 163, 184, 0.2);
  color: var(--kb-muted-foreground);
}
.rank-num.top3 {
  background: rgba(180, 120, 80, 0.15);
  color: #A0724A;
}
</style>
