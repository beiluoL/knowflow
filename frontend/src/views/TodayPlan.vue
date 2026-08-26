<template>
  <div class="today-plan animate-fade-in">
    <PageHeader
      title="今日计划"
      :subtitle="subtitleText"
      icon="calendar"
    >
      <template #actions>
        <div class="today-plan-actions">
          <label class="today-plan-date">
            <Icon name="calendar" :size="14" />
            <input
              type="date"
              v-model="pickDate"
              :max="todayStr"
              title="查看日期（仅当日可查看/生成）"
              @change="onPickDateChange"
            />
          </label>
          <Button
            variant="outline"
            size="sm"
            :loading="generating"
            @click="handleGenerateWeek"
          >
            <Icon name="sparkles" :size="14" />
            生成下周计划
          </Button>
          <Button
            variant="primary"
            size="sm"
            :loading="exporting"
            @click="handleExport"
          >
            <Icon name="download" :size="14" />
            导出日历
          </Button>
        </div>
      </template>
    </PageHeader>

    <StateView
      :status="viewStatus"
      :rows="6"
      type="card"
      :cols="3"
      error-title="计划加载失败"
      error-description="请检查网络或稍后重试。"
      error-icon="alert-circle"
      :show-retry="true"
      retry-text="重新加载"
      empty-icon="book-open"
      empty-title="今日还没有安排"
      @retry="loadPlan"
    >
      <template #empty-description>
        <p>
          先去
          <router-link
            to="/learning/paths"
            class="today-plan-link"
          >生成个性化学习路径</router-link>
          并报名；也可以先去
          <router-link
            to="/tasks"
            class="today-plan-link"
          >任务中心</router-link>
          建几条任务、
          <router-link
            to="/habits"
            class="today-plan-link"
          >习惯打卡</router-link>
          养几个习惯。
        </p>
      </template>
      <template #empty-action>
        <Button variant="primary" size="md" @click="loadPlan(true)">
          <Icon name="sparkles" :size="14" />
          立即生成今日计划
        </Button>
      </template>

      <!-- Ready 态内容 -->
      <div class="today-plan-overview">
        <StatCard
          class="today-plan-progress-card"
          layout="horizontal"
          icon="target"
          label="今日完成度"
          :value="displayRatio"
          unit="%"
        >
          <div class="today-plan-subhint">
            已完成 {{ plan?.completedItems ?? 0 }} / {{ plan?.totalItems ?? 0 }} 项
          </div>
          <Progress
            :percentage="displayRatio"
            variant="primary"
            label="完成进度"
            :show-label="true"
            class="today-plan-progress"
          />
        </StatCard>

        <div class="today-plan-meta">
          <div class="today-plan-meta-row">
            <Icon name="calendar-check" :size="14" />
            <span>计划日期：<b>{{ plan?.date ?? todayStr }}</b></span>
          </div>
          <div class="today-plan-meta-row">
            <Icon name="layers" :size="14" />
            <span>
              {{ summaryMorning }} · {{ summaryAfternoon }} · {{ summaryEvening }}
            </span>
          </div>
        </div>
      </div>

      <div class="today-plan-blocks">
        <div
          v-for="block in (plan?.blocks ?? defaultBlocks)"
          :key="block.timeSlot"
          class="today-plan-block"
          :class="`block-${block.timeSlot}`"
        >
          <div class="block-header">
            <div class="block-title-wrap">
              <span class="block-emo">{{ slotEmoji(block.timeSlot) }}</span>
              <h3 class="block-title">{{ block.label ?? slotDefaultLabel(block.timeSlot) }}</h3>
            </div>
            <Badge :variant="badgeVariant(block)">
              {{ completedCount(block) }}/{{ block.items.length }}
            </Badge>
          </div>
          <div class="block-time">
            <Icon name="clock" :size="12" />
            <span>{{ block.startTime ?? slotDefaultStart(block.timeSlot) }} – {{ block.endTime ?? slotDefaultEnd(block.timeSlot) }}</span>
          </div>

          <div v-if="!block.items.length" class="block-empty">
            <Icon name="coffee" :size="16" />
            <span>本段留白，可自由安排。</span>
          </div>
          <ul v-else class="block-items">
            <li
              v-for="(item, i) in block.items"
              :key="`${block.timeSlot}-${i}-${item.type}-${itemId(item)}`"
              class="block-item"
              :class="{ 'item-done': item.completed }"
            >
              <Checkbox
                :model-value="!!item.completed"
                :disabled="!!item._loading"
                class="item-check"
                @change="(v: boolean) => toggleItem(item, v, block)"
              >
                <span class="item-left">
                  <span
                    class="item-type-tag"
                    :class="`tag-${item.type}`"
                  >
                    <Icon :name="typeIcon(item.type)" :size="12" />
                    <span>{{ typeLabel(item.type) }}</span>
                  </span>
                  <span class="item-title">{{ item.title }}</span>
                </span>
                <span class="item-meta">
                  <span v-if="item.startTime" class="item-time">
                    {{ item.startTime }}{{ item.endTime ? `–${item.endTime}` : '' }}
                  </span>
                  <span v-if="item.duration" class="item-dur">
                    <Icon name="timer" :size="12" />
                    {{ item.duration }}min
                  </span>
                </span>
              </Checkbox>
            </li>
          </ul>
        </div>
      </div>
    </StateView>
  </div>
</template>

<script setup lang="ts">
/**
 * F3 · 今日计划页。
 * 与后端 /api/learning/plan/{today,generate,calendar.ics} 对接。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/ui/PageHeader.vue'
import Button from '@/components/ui/Button.vue'
import Icon from '@/components/ui/Icon.vue'
import StateView from '@/components/ui/StateView.vue'
import Checkbox from '@/components/ui/Checkbox.vue'
import Badge from '@/components/ui/Badge.vue'
import Progress from '@/components/ui/Progress.vue'
import StatCard from '@/components/ui/StatCard.vue'
import { learningApi } from '@/api/learning'
import { setTaskStatus } from '@/api/task'
import { habitApi } from '@/api/habit'
import { notify, getApiError } from '@/utils/toast'
import type { LearningPlanVO, PlanBlockVO, PlanItemVO } from '@/api/types'

const router = useRouter()

// -------- State --------
const plan = ref<LearningPlanVO | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)
const generating = ref(false)
const exporting = ref(false)

const today = new Date()
const todayStr = today.toISOString().slice(0, 10)
const pickDate = ref(todayStr)

// 默认空 block（在 empty/loading 下展示骨架结构，便于 TDD 类型）
const defaultBlocks: PlanBlockVO[] = [
  { timeSlot: 'morning', label: '上午 🌅', startTime: '07:00', endTime: '12:00', items: [] },
  { timeSlot: 'afternoon', label: '下午 ☀️', startTime: '13:00', endTime: '18:00', items: [] },
  { timeSlot: 'evening', label: '晚间 🌙', startTime: '19:00', endTime: '23:00', items: [] },
]

const subtitleText = computed(() => {
  const d = plan.value?.date ?? todayStr
  return `日期：${d}　自动合并学习任务 · Todo · 习惯打卡`
})

// 状态派生
const totalItemCount = computed(() => (plan.value?.blocks ?? []).reduce((s, b) => s + b.items.length, 0))
const viewStatus = computed<'loading' | 'error' | 'empty' | 'ready'>(() => {
  if (loading.value) return 'loading'
  if (error.value) return 'error'
  if (!plan.value || totalItemCount.value === 0) return 'empty'
  return 'ready'
})

// 完成度环形/线性：0~100 整数显示
const displayRatio = computed(() => {
  const raw = plan.value?.completedRatio ?? 0
  return Math.max(0, Math.min(100, Math.round(raw)))
})

// -------- Helpers --------
function fmt(d: number | string, n = 2) {
  const s = String(d)
  return s.length < n ? '0'.repeat(n - s.length) + s : s
}
function slotDefaultLabel(slot: string) {
  if (slot === 'morning') return '上午 🌅'
  if (slot === 'afternoon') return '下午 ☀️'
  if (slot === 'evening') return '晚间 🌙'
  return slot
}
function slotEmoji(slot: string) {
  if (slot === 'morning') return '🌅'
  if (slot === 'afternoon') return '☀️'
  if (slot === 'evening') return '🌙'
  return '📅'
}
function slotDefaultStart(slot: string) {
  if (slot === 'morning') return '07:00'
  if (slot === 'afternoon') return '13:00'
  return '19:00'
}
function slotDefaultEnd(slot: string) {
  if (slot === 'morning') return '12:00'
  if (slot === 'afternoon') return '18:00'
  return '23:00'
}
function typeIcon(type: string) {
  if (type === 'habit') return 'activity'
  if (type === 'todo') return 'check-square'
  return 'book-open'
}
function typeLabel(type: string) {
  if (type === 'habit') return '习惯'
  if (type === 'todo') return '任务'
  return '学习'
}
function completedCount(block: PlanBlockVO) {
  return block.items.filter((it) => it.completed).length
}
function badgeVariant(block: PlanBlockVO) {
  if (!block.items.length) return 'neutral' as const
  if (completedCount(block) === block.items.length) return 'success' as const
  if (completedCount(block) === 0) return 'warning' as const
  return 'info' as const
}
function summaryMorning() {
  const b = (plan.value?.blocks ?? []).find((x) => x.timeSlot === 'morning')
  return `上午 ${completedCount(b ?? defaultBlocks[0])}/${b?.items.length ?? 0}`
}
function summaryAfternoon() {
  const b = (plan.value?.blocks ?? []).find((x) => x.timeSlot === 'afternoon')
  return `下午 ${completedCount(b ?? defaultBlocks[1])}/${b?.items.length ?? 0}`
}
function summaryEvening() {
  const b = (plan.value?.blocks ?? []).find((x) => x.timeSlot === 'evening')
  return `晚间 ${completedCount(b ?? defaultBlocks[2])}/${b?.items.length ?? 0}`
}
function itemId(item: PlanItemVO): string {
  return String(item.learningTaskId ?? item.taskId ?? item.habitId ?? item.title)
}

// -------- Actions --------
async function loadPlan(forceToday = false) {
  if (forceToday) pickDate.value = todayStr
  loading.value = true
  error.value = null
  try {
    const data = await learningApi.getTodayPlan()
    plan.value = data
  } catch (e: unknown) {
    error.value = getApiError(e, '加载失败')
  } finally {
    loading.value = false
  }
}

async function handleGenerateWeek() {
  generating.value = true
  try {
    const res = await learningApi.generatePlan({ force: false })
    notify(`已生成 ${res.generatedDays} 天计划`, 'success')
    loadPlan(true)
  } catch (e: unknown) {
    notify(getApiError(e, '生成失败'), 'error')
  } finally {
    generating.value = false
  }
}

async function handleExport() {
  exporting.value = true
  try {
    await learningApi.exportCalendar({ date: todayStr, range: 7 })
    notify('日历已导出：knowflow-plan.ics', 'success')
  } catch (e: unknown) {
    notify(getApiError(e, '导出失败'), 'error')
  } finally {
    exporting.value = false
  }
}

async function onPickDateChange() {
  if (pickDate.value === todayStr) {
    loadPlan()
  } else {
    notify('目前只支持查看今日计划（其它日期接口待扩展）', 'info')
    pickDate.value = todayStr
  }
}

/**
 * 勾选切换：按 type 调对应后端 API，成功后局部更新状态与总完成率。
 * 完成 → 勾选为 true；未完成 → 勾选为 false（todo/learningTask 支持撤销，habit 不支持）。
 */
async function toggleItem(item: PlanItemVO & { _loading?: boolean }, nextValue: boolean, block: PlanBlockVO) {
  if (item._loading) return
  if (nextValue === !!item.completed) return
  try {
    item._loading = true
    if (item.type === 'learningTask') {
      if (!item.learningTaskId) throw new Error('缺少 learningTaskId')
      await learningApi.updateTaskStatus(item.learningTaskId, nextValue ? 1 : 0)
    } else if (item.type === 'todo') {
      if (!item.taskId) throw new Error('缺少 taskId')
      await setTaskStatus(item.taskId, nextValue ? 1 : 0)
    } else if (item.type === 'habit') {
      if (!item.habitId) throw new Error('缺少 habitId')
      if (!nextValue) {
        // 习惯打卡首版不支持撤销（后端 undo API 虽有，但保持体验一致）
        notify('习惯打卡已记录，如需撤销请到习惯管理页面操作', 'info')
        return
      }
      await habitApi.checkIn(item.habitId)
    }
    item.completed = nextValue
    recomputeCompleted()
    notify(nextValue ? '已标记完成' : '已恢复待办', 'success')
  } catch (e: unknown) {
    notify(getApiError(e, '操作失败'), 'error')
  } finally {
    item._loading = false
  }
}

function recomputeCompleted() {
  if (!plan.value) return
  let total = 0
  let done = 0
  for (const b of plan.value.blocks) {
    total += b.items.length
    for (const it of b.items) if (it.completed) done++
  }
  plan.value.totalItems = total
  plan.value.completedItems = done
  plan.value.completedRatio = total === 0 ? 0 : Math.round((done / total) * 10000) / 100
}

// -------- Lifecycle --------
onMounted(loadPlan)

// 日期选择器变化：如果用户未来在后端开放 /plan/date 接口，这里可以直接联动；
// 目前仅显示今日，所以只占位。
watch(pickDate, (v) => {
  if (v === todayStr) return
})
</script>

<style scoped>
.today-plan {
  width: 100%;
  max-width: 1160px;
  margin: 0 auto;
  padding: var(--kb-space-6);
}

.today-plan-actions {
  display: flex;
  align-items: center;
  gap: var(--kb-space-3);
  flex-wrap: wrap;
  justify-content: flex-end;
}

.today-plan-date {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  border-radius: var(--kb-radius-md);
  color: var(--kb-muted-foreground);
  font-size: 13px;
}
.today-plan-date input[type="date"] {
  background: transparent;
  border: none;
  outline: none;
  color: var(--kb-foreground);
  font-size: 13px;
  color-scheme: light dark;
}
.today-plan-link {
  color: var(--kb-primary);
  text-decoration: none;
}
.today-plan-link:hover { text-decoration: underline; }

.today-plan-overview {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr);
  gap: var(--kb-space-4);
  margin: var(--kb-space-5) 0 var(--kb-space-4);
}
.today-plan-subhint {
  margin-top: var(--kb-space-2);
  font-size: 13px;
  color: var(--kb-muted-foreground);
}
.today-plan-progress-card { padding: var(--kb-space-4); }
.today-plan-progress { margin-top: var(--kb-space-3); }
.today-plan-meta {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: var(--kb-space-3);
  padding: var(--kb-space-4);
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-lg);
}
.today-plan-meta-row {
  display: inline-flex;
  align-items: center;
  gap: var(--kb-space-2);
  color: var(--kb-foreground);
  font-size: 14px;
}

.today-plan-blocks {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--kb-space-4);
}
.today-plan-block {
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-lg);
  padding: var(--kb-space-4);
  display: flex;
  flex-direction: column;
  gap: var(--kb-space-3);
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
}
.today-plan-block:hover {
  transform: translateY(-2px);
  border-color: color-mix(in oklab, var(--kb-primary) 30%, var(--kb-border));
  box-shadow: var(--kb-shadow-sm);
}
.block-morning {
  border-top: 3px solid color-mix(in oklab, #f59e0b 70%, transparent);
}
.block-afternoon {
  border-top: 3px solid color-mix(in oklab, #0ea5e9 70%, transparent);
}
.block-evening {
  border-top: 3px solid color-mix(in oklab, #7c3aed 70%, transparent);
}

.block-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.block-title-wrap {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.block-emo { font-size: 20px; }
.block-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--kb-foreground);
}
.block-time {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--kb-muted-foreground);
}
.block-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: var(--kb-space-4) var(--kb-space-3);
  border-radius: var(--kb-radius-md);
  background: var(--kb-muted);
  color: var(--kb-muted-foreground);
  font-size: 13px;
}
.block-items {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: var(--kb-space-2);
}
.block-item {
  border-radius: var(--kb-radius-md);
  border: 1px solid var(--kb-border);
  padding: var(--kb-space-2) var(--kb-space-3);
  background: var(--kb-card);
  transition: border-color 0.15s ease, background 0.15s ease;
}
.block-item:hover {
  border-color: color-mix(in oklab, var(--kb-primary) 35%, var(--kb-border));
  background: var(--kb-accent);
}
.item-check .kb-checkbox-label {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kb-space-3);
}
.item-left {
  display: inline-flex;
  align-items: center;
  gap: var(--kb-space-2);
  min-width: 0;
  flex: 1 1 auto;
}
.item-type-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  font-weight: 500;
  white-space: nowrap;
}
.tag-learningTask {
  background: color-mix(in oklab, var(--kb-primary) 18%, transparent);
  color: var(--kb-primary);
}
.tag-todo {
  background: color-mix(in oklab, var(--kb-accent-blue, #0ea5e9) 18%, transparent);
  color: var(--kb-accent-blue, #0284c7);
}
.tag-habit {
  background: color-mix(in oklab, var(--kb-success) 18%, transparent);
  color: var(--kb-success);
}
.item-title {
  font-size: 14px;
  color: var(--kb-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.item-meta {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--kb-muted-foreground);
  font-size: 12px;
  white-space: nowrap;
}
.item-dur { display: inline-flex; align-items: center; gap: 3px; }

.item-done .item-title {
  text-decoration: line-through;
  color: var(--kb-muted-foreground);
}

@media (max-width: 960px) {
  .today-plan-overview { grid-template-columns: 1fr; }
  .today-plan-blocks { grid-template-columns: 1fr; }
}
</style>
