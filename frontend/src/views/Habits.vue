<template>
  <div class="animate-fade-in habits-page">
    <!-- ===== 面包屑 + 标题 ===== -->
    <div class="flex items-center gap-2 text-xs mb-4" style="color: var(--kb-muted-foreground);">
      <router-link to="/tasks" class="hover:underline" style="color: var(--kb-primary);">任务中心</router-link>
      <Icon name="chevron-right" :size="12" />
      <span>习惯打卡</span>
    </div>

    <div class="flex items-center justify-between mb-6 flex-wrap gap-3">
      <div>
        <h1 class="kb-h1 mb-1">习惯打卡</h1>
        <p class="kb-body-sm" style="font-size: 14px;">坚持微习惯，复利成长</p>
      </div>
      <button type="button" class="btn-primary" @click="openCreate">
        <Icon name="plus" :size="16" />
        <span>新建习惯</span>
      </button>
    </div>

    <!-- ===== 概览统计 ===== -->
    <div class="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
      <StatCard icon="repeat" icon-color="var(--kb-primary)" icon-bg="rgba(59,111,224,0.12)" :value="habits.length" label="进行中习惯" />
      <StatCard icon="check-circle" icon-color="var(--kb-accent)" icon-bg="rgba(16,185,129,0.12)" :value="`${todayDoneCount}/${habits.length}`" label="今日已达标" />
      <StatCard icon="flame" icon-color="var(--kb-warning)" icon-bg="rgba(245,158,11,0.12)" :value="maxStreak" label="最长连续" />
      <StatCard icon="award" icon-color="#8B5CF6" icon-bg="rgba(139,92,246,0.12)" :value="totalCheckins" label="累计达标天数" />
    </div>

    <!-- ===== 空状态 ===== -->
    <div v-if="!loading && habits.length === 0" class="empty-state">
      <Icon name="repeat" :size="40" style="color: var(--kb-muted-foreground); opacity: 0.5;" />
      <p class="mb-2">还没有习惯，点击右上角「新建习惯」开始坚持</p>
      <p class="empty-hint">建议从「每日阅读 30 分钟」「每日 8 杯水」等小习惯开始</p>
    </div>

    <!-- ===== 加载态 ===== -->
    <div v-if="loading" class="loading-state">
      <Icon name="loader" :size="20" class="spin" /> 加载中…
    </div>

    <!-- ===== 习惯卡片网格 ===== -->
    <div v-if="!loading && habits.length > 0" class="grid grid-cols-1 lg:grid-cols-2 gap-5">
      <div v-for="h in habits" :key="h.id" class="habit-card" :class="{ done: h.completedToday }">
        <!-- 卡片头部 -->
        <div class="habit-head">
          <div class="habit-icon" :style="{ background: hexBg(h.color), color: h.color }">
            <Icon :name="h.icon || 'repeat'" :size="20" />
          </div>
          <div class="habit-meta">
            <div class="habit-title">{{ h.name }}</div>
            <div class="habit-desc">{{ h.description || freqLabel(h) }}</div>
          </div>
          <div class="habit-actions">
            <button title="编辑" @click="openEdit(h)"><Icon name="edit-3" :size="14" /></button>
            <button title="删除" class="danger" @click="removeHabit(h)"><Icon name="trash-2" :size="14" /></button>
          </div>
        </div>

        <!-- 进度环 + 打卡按钮 -->
        <div class="habit-body">
          <div class="progress-ring">
            <svg class="ring-svg" viewBox="0 0 80 80">
              <circle cx="40" cy="40" r="34" fill="none" stroke="var(--kb-muted)" stroke-width="7" />
              <circle
                cx="40" cy="40" r="34" fill="none"
                :stroke="h.completedToday ? 'var(--kb-accent)' : 'var(--kb-primary)'"
                stroke-width="7" stroke-linecap="round"
                :stroke-dasharray="ringCircumference"
                :stroke-dashoffset="ringCircumference - ringCircumference * ringPercent(h)"
                class="ring-fill"
              />
            </svg>
            <div class="ring-center">
              <div class="ring-count tabular-nums">{{ h.todayCount }}<span class="ring-target">/{{ h.targetCount }}</span></div>
              <div class="ring-label">{{ h.completedToday ? '已达标' : '今日' }}</div>
            </div>
          </div>

          <div class="habit-stats">
            <div class="hs-row">
              <Icon name="flame" :size="14" style="color: var(--kb-warning);" />
              <span class="hs-label">连续</span>
              <span class="hs-val tabular-nums">{{ h.streak }} {{ h.frequency === 'weekly' ? '周' : '天' }}</span>
            </div>
            <div class="hs-row">
              <Icon name="trophy" :size="14" style="color: var(--kb-warning);" />
              <span class="hs-label">最佳</span>
              <span class="hs-val tabular-nums">{{ h.bestStreak }} {{ h.frequency === 'weekly' ? '周' : '天' }}</span>
            </div>
            <div class="hs-row">
              <Icon name="calendar" :size="14" style="color: var(--kb-muted-foreground);" />
              <span class="hs-label">累计</span>
              <span class="hs-val tabular-nums">{{ h.totalDays }} 天</span>
            </div>
            <div v-if="h.reminderTime" class="hs-row">
              <Icon name="bell" :size="14" style="color: var(--kb-primary);" />
              <span class="hs-label">提醒</span>
              <span class="hs-val tabular-nums">{{ h.reminderTime }}</span>
            </div>
          </div>

          <button
            type="button"
            class="btn-checkin"
            :disabled="h.completedToday || busyId === h.id"
            @click="doCheckin(h)"
          >
            <Icon :name="h.completedToday ? 'check' : 'plus'" :size="16" />
            <span>{{ h.completedToday ? '今日已达标' : (busyId === h.id ? '打卡中…' : '打卡') }}</span>
          </button>
        </div>

        <!-- 近 7 天进度条 -->
        <div class="weekly-bars">
          <div v-for="(d, idx) in h.weekly" :key="idx" class="wb-col">
            <div class="wb-track">
              <div
                class="wb-fill"
                :class="{ done: d.completed }"
                :style="{ height: barHeight(d, h.targetCount) }"
              ></div>
            </div>
            <div class="wb-label">{{ weekdayShort(idx) }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- ===== 新建 / 编辑弹窗 ===== -->
    <teleport to="body">
      <div v-if="formOpen" class="habit-modal-mask" @click.self="closeForm">
        <div class="habit-modal">
          <div class="hm-head">
            <h3 class="hm-title">{{ editing ? '编辑习惯' : '新建习惯' }}</h3>
            <button class="hm-close" @click="closeForm"><Icon name="x" :size="18" /></button>
          </div>
          <div class="hm-body">
            <div class="hm-field">
              <label class="hm-label">习惯名称 <span class="req">*</span></label>
              <input
                v-model="form.name"
                class="hm-input"
                placeholder="如：每日阅读 30 分钟"
                maxlength="50"
              />
            </div>
            <div class="hm-field">
              <label class="hm-label">描述（可选）</label>
              <input
                v-model="form.description"
                class="hm-input"
                placeholder="坚持的意义与目标"
                maxlength="120"
              />
            </div>
            <div class="hm-row">
              <div class="hm-field">
                <label class="hm-label">频率</label>
                <div class="seg-control">
                  <button type="button" :class="{ active: form.frequency === 'daily' }" @click="form.frequency = 'daily'">每日</button>
                  <button type="button" :class="{ active: form.frequency === 'weekly' }" @click="form.frequency = 'weekly'">每周</button>
                </div>
              </div>
              <div class="hm-field">
                <label class="hm-label">目标次数</label>
                <input v-model.number="form.targetCount" type="number" min="1" max="99" class="hm-input" />
              </div>
            </div>
            <div class="hm-field">
              <label class="hm-label">提醒时间（可选，浏览器通知）</label>
              <input v-model="form.reminderTime" type="time" class="hm-input" />
            </div>
            <div class="hm-field">
              <label class="hm-label">图标</label>
              <div class="icon-picker">
                <button
                  v-for="ic in iconPresets"
                  :key="ic"
                  type="button"
                  class="ip-btn"
                  :class="{ active: form.icon === ic }"
                  @click="form.icon = ic"
                >
                  <Icon :name="ic" :size="18" />
                </button>
              </div>
            </div>
            <div class="hm-field">
              <label class="hm-label">主题色</label>
              <div class="color-picker">
                <button
                  v-for="c in colorPresets"
                  :key="c"
                  type="button"
                  class="cp-btn"
                  :class="{ active: form.color === c }"
                  :style="{ background: c }"
                  @click="form.color = c"
                ></button>
              </div>
            </div>
          </div>
          <div class="hm-foot">
            <button type="button" class="hm-btn ghost" @click="closeForm">取消</button>
            <button type="button" class="hm-btn primary" :disabled="saving || !form.name.trim()" @click="saveHabit">
              {{ saving ? '保存中…' : (editing ? '保存' : '创建') }}
            </button>
          </div>
        </div>
      </div>
    </teleport>
  </div>
</template>

<script setup lang="ts">
// 习惯打卡页：多习惯管理 + 今日打卡（幂等累加）+ 连续天数 + 近 7 天进度可视化 + 提醒。
import { ref, computed, onMounted, onUnmounted } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import StatCard from '@/components/ui/StatCard.vue'
import { notify, getApiError } from '@/utils/toast'
import { dialog } from '@/utils/dialog'
import { habitApi, type HabitVO, type HabitPayload } from '@/api'

const habits = ref<HabitVO[]>([])
const loading = ref(false)
const busyId = ref<number | null>(null)

// ===== 概览统计 =====
const todayDoneCount = computed(() => habits.value.filter((h) => h.completedToday).length)
const maxStreak = computed(() => habits.value.reduce((m, h) => Math.max(m, h.streak), 0))
const totalCheckins = computed(() => habits.value.reduce((s, h) => s + h.totalDays, 0))

// ===== 表单 =====
const formOpen = ref(false)
const editing = ref<HabitVO | null>(null)
const saving = ref(false)
const form = ref<HabitPayload & { name: string }>({
  name: '',
  description: '',
  icon: 'repeat',
  color: 'var(--kb-primary)',
  frequency: 'daily',
  targetCount: 1,
  reminderTime: null,
})

const iconPresets = ['repeat', 'book-open', 'droplets', 'dumbbell', 'coffee', 'moon', 'sun', 'pen-tool', 'music', 'heart']
const colorPresets = ['var(--kb-primary)', 'var(--kb-accent)', 'var(--kb-warning)', '#8B5CF6', '#EC4899', '#0EA5E9']

function openCreate() {
  editing.value = null
  form.value = {
    name: '',
    description: '',
    icon: 'repeat',
    color: 'var(--kb-primary)',
    frequency: 'daily',
    targetCount: 1,
    reminderTime: null,
  }
  formOpen.value = true
}

function openEdit(h: HabitVO) {
  editing.value = h
  form.value = {
    name: h.name,
    description: h.description,
    icon: h.icon,
    color: h.color,
    frequency: h.frequency,
    targetCount: h.targetCount,
    reminderTime: h.reminderTime,
  }
  formOpen.value = true
}

function closeForm() {
  if (saving.value) return
  formOpen.value = false
  editing.value = null
}

async function saveHabit() {
  const name = form.value.name.trim()
  if (!name) {
    notify('请输入习惯名称', 'error')
    return
  }
  saving.value = true
  try {
    const payload: HabitPayload = {
      name,
      description: form.value.description?.trim() || undefined,
      icon: form.value.icon,
      color: form.value.color,
      frequency: form.value.frequency,
      targetCount: Math.max(1, form.value.targetCount || 1),
      reminderTime: form.value.reminderTime || null,
    }
    if (editing.value) {
      await habitApi.update(editing.value.id, payload)
      notify('习惯已更新', 'success')
    } else {
      await habitApi.create(payload)
      notify('习惯已创建，开始坚持吧', 'success')
    }
    formOpen.value = false
    editing.value = null
    await loadHabits()
  } catch (e: unknown) {
    notify(getApiError(e, '保存失败'), 'error')
  } finally {
    saving.value = false
  }
}

async function removeHabit(h: HabitVO) {
  const ok = await dialog.confirm({
    title: '删除习惯',
    message: `确定删除习惯「${h.name}」？\n历史打卡记录将一并清除，且无法恢复。`,
    variant: 'danger',
    confirmText: '删除',
  })
  if (!ok) return
  try {
    await habitApi.remove(h.id)
    notify('习惯已删除', 'success')
    await loadHabits()
  } catch (e: unknown) {
    notify(getApiError(e, '删除失败'), 'error')
  }
}

// ===== 打卡 =====
async function doCheckin(h: HabitVO) {
  if (h.completedToday || busyId.value === h.id) return
  busyId.value = h.id
  try {
    const updated = await habitApi.checkIn(h.id)
    const idx = habits.value.findIndex((x) => x.id === h.id)
    if (idx !== -1) habits.value[idx] = updated
    if (updated.completedToday) {
      notify(`「${updated.name}」今日已达标 🎉`, 'success')
    } else {
      notify(`打卡成功，还差 ${updated.targetCount - updated.todayCount} 次达标`, 'info')
    }
  } catch (e: unknown) {
    notify(getApiError(e, '打卡失败'), 'error')
  } finally {
    busyId.value = null
  }
}

// ===== 数据加载 =====
async function loadHabits(): Promise<void> {
  loading.value = true
  try {
    habits.value = await habitApi.list()
  } catch (e: unknown) {
    notify(getApiError(e, '加载习惯失败'), 'error')
  } finally {
    loading.value = false
  }
}

// ===== 提醒（浏览器 Notification API）=====
let reminderTimer: ReturnType<typeof setInterval> | null = null
const lastFired = ref<Set<string>>(new Set())

function startReminder() {
  if (reminderTimer) return
  reminderTimer = setInterval(() => {
    if (!('Notification' in window)) return
    if (Notification.permission !== 'granted') return
    const now = new Date()
    const hh = String(now.getHours()).padStart(2, '0')
    const mm = String(now.getMinutes()).padStart(2, '0')
    const cur = `${hh}:${mm}`
    const todayKey = now.toISOString().slice(0, 10)
    for (const h of habits.value) {
      if (!h.reminderTime || h.completedToday) continue
      if (h.reminderTime === cur) {
        const key = `${h.id}-${todayKey}`
        if (!lastFired.value.has(key)) {
          lastFired.value.add(key)
          new Notification('KnowFlow 习惯提醒', {
            body: `该打卡「${h.name}」啦！今日 ${h.todayCount}/${h.targetCount}`,
          })
        }
      }
    }
  }, 30 * 1000)
}

async function ensureReminderPermission() {
  if (!('Notification' in window)) return
  if (Notification.permission === 'default') {
    try {
      await Notification.requestPermission()
    } catch {
      /* 用户拒绝则静默 */
    }
  }
}

onMounted(async () => {
  await loadHabits()
  await ensureReminderPermission()
  startReminder()
})

onUnmounted(() => {
  if (reminderTimer) {
    clearInterval(reminderTimer)
    reminderTimer = null
  }
})

// ===== 渲染辅助 =====
const ringCircumference = 2 * Math.PI * 34
function ringPercent(h: HabitVO): number {
  const target = Math.max(1, h.targetCount)
  return Math.min(1, h.todayCount / target)
}
function barHeight(d: { count: number; completed: boolean }, target: number): string {
  const t = Math.max(1, target)
  const pct = Math.min(1, d.count / t)
  return d.count > 0 ? `${Math.max(12, pct * 100)}%` : '6%'
}
const weekdayLabels = ['一', '二', '三', '四', '五', '六', '日']
function weekdayShort(idx: number): string {
  // weekly 数组按时间正序（最早 → 今天），最后一个是今天
  const today = new Date().getDay()
  const adjusted = (today + 6) % 7 // 周一为 0
  const startIdx = (adjusted - (weekdayLabels.length - 1) + 7) % 7
  return weekdayLabels[(startIdx + idx) % 7]
}
function freqLabel(h: HabitVO): string {
  return h.frequency === 'weekly' ? `每周目标 ${h.targetCount} 次` : `每日目标 ${h.targetCount} 次`
}
function hexBg(color: string): string {
  // 把 var(--kb-primary) 等映射为带透明度的背景
  if (color.startsWith('var(')) {
    return 'rgba(59,111,224,0.12)'
  }
  return color + '20'
}
</script>

<style scoped>
.animate-fade-in { animation: fadeIn 0.4s ease-out; }
@keyframes fadeIn { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }

.kb-h1 { font-size: 28px; font-weight: 700; line-height: 1.3; letter-spacing: -0.02em; color: var(--kb-foreground); }
.kb-body-sm { font-size: 12px; line-height: 1.5; color: var(--kb-muted-foreground); }

.btn-primary {
  display: inline-flex; align-items: center; gap: 8px;
  padding: 10px 18px; border-radius: var(--kb-radius-lg);
  background: var(--kb-primary); color: var(--kb-primary-foreground);
  border: none; cursor: pointer; font-size: 14px; font-weight: 600;
  transition: opacity 0.15s, transform 0.15s;
}
.btn-primary:hover { opacity: 0.92; transform: translateY(-1px); }

/* ===== 空状态 / 加载态 ===== */
.empty-state, .loading-state {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  padding: 60px 20px; color: var(--kb-muted-foreground); text-align: center;
}
.empty-state p { font-size: 14px; }
.empty-hint { font-size: 12px; opacity: 0.8; }
.loading-state { flex-direction: row; gap: 8px; padding: 40px; }

.spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

/* ===== 习惯卡片 ===== */
.habit-card {
  padding: 20px; border-radius: var(--kb-radius-lg);
  background: var(--kb-card); border: 1px solid var(--kb-border);
  transition: box-shadow 0.2s, border-color 0.2s;
}
.habit-card:hover { box-shadow: 0 4px 16px rgba(0,0,0,0.05); border-color: rgba(59,111,224,0.3); }
.habit-card.done { border-color: rgba(16,185,129,0.4); }

.habit-head { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.habit-icon {
  width: 44px; height: 44px; border-radius: var(--kb-radius-md);
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.habit-meta { flex: 1; min-width: 0; }
.habit-title { font-size: 16px; font-weight: 600; color: var(--kb-foreground); margin-bottom: 2px; }
.habit-desc { font-size: 12px; color: var(--kb-muted-foreground); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.habit-actions { display: flex; gap: 2px; opacity: 0; transition: opacity 0.15s; }
.habit-card:hover .habit-actions { opacity: 1; }
.habit-actions button {
  width: 28px; height: 28px; border: none; background: transparent;
  color: var(--kb-muted-foreground); border-radius: var(--kb-radius-sm); cursor: pointer;
  display: inline-flex; align-items: center; justify-content: center; transition: all 0.12s;
}
.habit-actions button:hover { background: var(--kb-muted); color: var(--kb-foreground); }
.habit-actions button.danger:hover { color: var(--kb-destructive); background: rgba(239,68,68,0.1); }

.habit-body { display: flex; align-items: center; gap: 16px; margin-bottom: 18px; }
.progress-ring { position: relative; width: 80px; height: 80px; flex-shrink: 0; }
.ring-svg { width: 100%; height: 100%; transform: rotate(-90deg); }
.ring-fill { transition: stroke-dashoffset 0.6s ease; }
.ring-center {
  position: absolute; inset: 0; display: flex; flex-direction: column;
  align-items: center; justify-content: center;
}
.ring-count { font-size: 18px; font-weight: 700; color: var(--kb-foreground); }
.ring-target { font-size: 12px; font-weight: 500; color: var(--kb-muted-foreground); }
.ring-label { font-size: 10px; color: var(--kb-muted-foreground); }

.habit-stats { flex: 1; display: flex; flex-direction: column; gap: 6px; min-width: 0; }
.hs-row { display: flex; align-items: center; gap: 6px; font-size: 12px; }
.hs-label { color: var(--kb-muted-foreground); }
.hs-val { color: var(--kb-foreground); font-weight: 600; margin-left: auto; }

.btn-checkin {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 10px 20px; border-radius: var(--kb-radius-md);
  background: var(--kb-primary); color: var(--kb-primary-foreground);
  border: none; cursor: pointer; font-size: 13px; font-weight: 600;
  transition: opacity 0.15s; flex-shrink: 0;
}
.btn-checkin:hover:not(:disabled) { opacity: 0.9; }
.btn-checkin:disabled { opacity: 0.6; cursor: not-allowed; }
.habit-card.done .btn-checkin { background: rgba(16,185,129,0.12); color: var(--kb-accent); }

/* ===== 周进度条 ===== */
.weekly-bars {
  display: flex; gap: 6px; padding-top: 16px;
  border-top: 1px solid var(--kb-border);
}
.wb-col { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 6px; }
.wb-track {
  width: 100%; height: 48px; border-radius: 4px;
  background: var(--kb-muted); display: flex; align-items: flex-end; overflow: hidden;
}
.wb-fill {
  width: 100%; border-radius: 4px; background: rgba(59,111,224,0.4);
  transition: height 0.4s ease, background 0.2s;
}
.wb-fill.done { background: var(--kb-accent); }
.wb-label { font-size: 10px; color: var(--kb-muted-foreground); }

/* ===== 弹窗 ===== */
.habit-modal-mask {
  position: fixed; inset: 0; z-index: 50; background: rgba(0,0,0,0.4);
  display: flex; align-items: center; justify-content: center; padding: 20px;
  animation: fadeIn 0.2s ease;
}
.habit-modal {
  width: 100%; max-width: 480px; max-height: 90vh; overflow-y: auto;
  background: var(--kb-card); border-radius: var(--kb-radius-lg);
  border: 1px solid var(--kb-border); box-shadow: var(--shadow-dropdown-3);
}
.hm-head {
  display: flex; align-items: center; justify-content: space-between;
  padding: 18px 20px; border-bottom: 1px solid var(--kb-border);
}
.hm-title { font-size: 16px; font-weight: 600; color: var(--kb-foreground); }
.hm-close {
  width: 30px; height: 30px; border: none; background: transparent;
  color: var(--kb-muted-foreground); border-radius: var(--kb-radius-sm); cursor: pointer;
  display: inline-flex; align-items: center; justify-content: center;
}
.hm-close:hover { background: var(--kb-muted); color: var(--kb-foreground); }
.hm-body { padding: 20px; display: flex; flex-direction: column; gap: 16px; }
.hm-field { display: flex; flex-direction: column; gap: 6px; }
.hm-row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.hm-label { font-size: 12px; font-weight: 600; color: var(--kb-foreground); }
.req { color: var(--kb-destructive); }
.hm-input {
  padding: 9px 12px; border-radius: var(--kb-radius-sm);
  border: 1px solid var(--kb-border); background: var(--kb-background);
  color: var(--kb-foreground); font-size: 14px; outline: none; transition: border-color 0.15s;
}
.hm-input:focus { border-color: var(--kb-primary); }
.seg-control {
  display: inline-flex; background: var(--kb-muted); padding: 3px; border-radius: var(--kb-radius-sm);
}
.seg-control button {
  flex: 1; padding: 7px 12px; border: none; background: transparent;
  border-radius: calc(var(--kb-radius-sm) - 2px); cursor: pointer; font-size: 13px;
  color: var(--kb-muted-foreground); transition: all 0.12s;
}
.seg-control button.active { background: var(--kb-card); color: var(--kb-primary); font-weight: 600; box-shadow: 0 1px 3px rgba(0,0,0,0.08); }

.icon-picker, .color-picker { display: flex; flex-wrap: wrap; gap: 8px; }
.ip-btn {
  width: 36px; height: 36px; border: 1.5px solid var(--kb-border); background: var(--kb-background);
  color: var(--kb-muted-foreground); border-radius: var(--kb-radius-sm); cursor: pointer;
  display: inline-flex; align-items: center; justify-content: center; transition: all 0.12s;
}
.ip-btn:hover { border-color: var(--kb-primary); color: var(--kb-primary); }
.ip-btn.active { border-color: var(--kb-primary); color: var(--kb-primary); background: rgba(59,111,224,0.08); }
.cp-btn {
  width: 28px; height: 28px; border-radius: 50%; border: 2px solid transparent;
  cursor: pointer; transition: transform 0.12s, border-color 0.12s;
}
.cp-btn:hover { transform: scale(1.12); }
.cp-btn.active { border-color: var(--kb-foreground); }

.hm-foot {
  display: flex; justify-content: flex-end; gap: 10px;
  padding: 16px 20px; border-top: 1px solid var(--kb-border);
}
.hm-btn {
  padding: 9px 18px; border-radius: var(--kb-radius-sm); border: none; cursor: pointer;
  font-size: 14px; font-weight: 600; transition: all 0.15s;
}
.hm-btn.ghost { background: var(--kb-muted); color: var(--kb-foreground); }
.hm-btn.ghost:hover { background: var(--kb-border); }
.hm-btn.primary { background: var(--kb-primary); color: var(--kb-primary-foreground); }
.hm-btn.primary:disabled { opacity: 0.6; cursor: not-allowed; }
</style>
