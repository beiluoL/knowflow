<template>
  <div class="today-plan animate-fade-in">
    <!-- UI/UX 5.1 §十二：PageHeader 的副标题统一走 `description`。
         旧写法 `:subtitle` / `icon` 在本组件里**并不存在**，会被当作普通 DOM 属性渲染，
         结果就是「今日计划」下方那句说明从来没显示出来。 -->
    <PageHeader
      title="今日计划"
      :description="subtitleText"
      back
    >
      <template #actions>
        <div class="today-plan-actions">
          <span class="today-plan-date"><Icon name="calendar" :size="14" />{{ plan?.date || '服务端今日' }}</span>
          <Button
            variant="secondary"
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

    <SmartPlanPanel v-if="smartEnabled" />
    <StateView
      :status="viewStatus"
      :rows="6"
      type="card"
      :cols="3"
      error-title="计划加载失败"
      :error-description="error || '请检查网络或稍后重试。'"
      error-scope="当前页面的计划内容没有加载出来，其他页面不受影响。"
      error-hint="确认服务可用后点「重新加载」；若反复失败，可先返回学习首页。"
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
              {{ summaryMorning() }} · {{ summaryAfternoon() }} · {{ summaryEvening() }}
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
                :disabled="updating || !!item._loading || !!item.completionRequirement || !['learningTask','todo','habit'].includes(item.type)"
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
              <TaskCompletionAction :requirement="item.completionRequirement" :title="item.title" />
            </li>
          </ul>
        </div>
      </div>
    </StateView>
  </div>
</template>

<script setup lang="ts">
import SmartPlanPanel from '@/components/learning-adaptive/SmartPlanPanel.vue'
const smartEnabled=import.meta.env.VITE_PRODUCT_ADAPTIVE_LEARNING==='true'
/**
 * F3 · 今日计划页。
 * 与后端 /api/learning/plan/{today,generate,calendar.ics} 对接。
 */
import { computed, onMounted, ref } from 'vue'
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
import TaskCompletionAction from '@/components/learning-actions/TaskCompletionAction.vue'
import { habitApi } from '@/api/habit'
import { notify, getApiError, confirmDialog } from '@/utils/toast'
import type { LearningPlanVO, PlanBlockVO, PlanItemVO } from '@/api/types'

// -------- State --------
const plan = ref<LearningPlanVO | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)
const generating = ref(false)
const exporting = ref(false)

const today = new Date()
const todayStr = `${today.getFullYear()}-${String(today.getMonth()+1).padStart(2,'0')}-${String(today.getDate()).padStart(2,'0')}`
const updating = ref(false)
let planRequest = 0

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
function badgeVariant(block: PlanBlockVO): 'primary' | 'success' | 'warning' | 'danger' | 'default' {
  if (!block.items.length) return 'default'
  if (completedCount(block) === block.items.length) return 'success'
  if (completedCount(block) === 0) return 'warning'
  return 'primary'
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
  const request = ++planRequest
  void forceToday
  loading.value = true
  error.value = null
  try {
    const data = await learningApi.getTodayPlan()
    if (request === planRequest) plan.value = data
  } catch (e: unknown) {
    if (request === planRequest) error.value = getApiError(e, '加载失败')
  } finally {
    if (request === planRequest) loading.value = false
  }
}

async function handleGenerateWeek() {
  if (generating.value) return
  generating.value = true
  if (!(await confirmDialog('确定生成下周计划？沿用已有安排规则，不覆盖已有日期计划。', {"title":"确认替换现有内容？","confirmText":"确认替换","cancelText":"返回编辑","icon":"refresh-cw","variant":"danger"}))) { generating.value = false; return }
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
  if (exporting.value) return
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

/**
 * 勾选切换：按 type 调对应后端 API，成功后局部更新状态与总完成率。
 * 完成 → 勾选为 true；未完成 → 勾选为 false（todo/learningTask 支持撤销，habit 不支持）。
 */
async function toggleItem(item: PlanItemVO & { _loading?: boolean }, nextValue: boolean, _block: PlanBlockVO) {
  if (item._loading || updating.value) return
  if (item.completionRequirement) { notify('此任务需要真实学习记录，请使用去练习入口', 'info'); return }
  if (nextValue === !!item.completed) return
  try {
    item._loading = true; updating.value = true
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
    } else { throw new Error('暂不支持此计划项的状态操作') }
    // Void写接口不提供最终状态；重新读取服务端回填结果，不在页面自算完成率。
    await loadPlan()
    if (!error.value) notify('已重新读取计划状态', 'success')
  } catch (e: unknown) {
    notify(getApiError(e, '操作失败'), 'error')
  } finally {
    item._loading = false; updating.value = false
  }
}

// -------- Lifecycle --------
onMounted(loadPlan)

</script>

<style scoped>
.today-plan {
  width: 100%;
  max-width: none;
  margin-inline: 0;
  padding: var(--kb-space-6);

  min-width: 0;
  padding-inline: 0;
  padding-top: 0;
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
  font-size: var(--kb-fs-body-sm);
}
.today-plan-date input[type="date"] {
  background: transparent;
  border: none;
  outline: none;
  color: var(--kb-foreground);
  font-size: var(--kb-fs-body-sm);
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
  font-size: var(--kb-fs-body-sm);
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
  font-size: var(--kb-fs-body-md);
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
  border-top: 3px solid color-mix(in oklab, var(--kb-warning) 70%, transparent);
}
.block-afternoon {
  border-top: 3px solid color-mix(in oklab, var(--kb-primary) 70%, transparent);
}
.block-evening {
  border-top: 3px solid color-mix(in oklab, var(--kb-accent) 70%, transparent);
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
.block-emo { font-size: var(--kb-fs-h3); }
.block-title {
  margin: 0;
  font-size: var(--kb-fs-body-lg);
  font-weight: 600;
  color: var(--kb-foreground);
}
.block-time {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--kb-fs-caption);
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
  font-size: var(--kb-fs-body-sm);
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
  font-size: var(--kb-fs-xs);
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
  background: color-mix(in oklab, var(--kb-accent-blue, var(--kb-primary)) 18%, transparent);
  color: var(--kb-accent-blue, #0284c7);
}
.tag-habit {
  background: color-mix(in oklab, var(--kb-success) 18%, transparent);
  color: var(--kb-success);
}
.item-title {
  font-size: var(--kb-fs-body-md);
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
  font-size: var(--kb-fs-caption);
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
