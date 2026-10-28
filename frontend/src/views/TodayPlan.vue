<template>
  <div class="today-plan animate-fade-in">
    <PageHeader
      title="今日计划"
      :description="subtitleText"
    >
      <template #actions>
        <div class="today-plan-actions">
          <span class="today-plan-date"><Icon name="calendar" :size="16" />{{ plan?.date || '服务端今日' }}</span>
          <RouterLink to="/tasks" class="today-plan-all">
            <Icon name="list" :size="14" />
            查看全部任务
          </RouterLink>
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
            variant="secondary"
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

      <!-- Ready 态：左侧时间线 + 右侧今日概览 -->
      <div class="plan-layout">
        <div class="plan-main">
          <section
            v-for="block in (plan?.blocks ?? defaultBlocks)"
            :key="block.timeSlot"
            class="plan-block"
            :class="`block-${block.timeSlot}`"
          >
            <header class="plan-block-head">
              <div class="plan-block-title">
                <span class="plan-block-icon">
                  <Icon :name="slotIcon(block.timeSlot)" :size="16" />
                </span>
                <div class="plan-block-name-wrap">
                  <h2 class="plan-block-name">{{ slotName(block) }}</h2>
                  <span class="plan-block-time">
                    {{ block.startTime ?? slotDefaultStart(block.timeSlot) }} – {{ block.endTime ?? slotDefaultEnd(block.timeSlot) }}
                  </span>
                </div>
              </div>
              <span class="plan-block-count" :class="{ complete: block.items.length > 0 && completedCount(block) === block.items.length }">
                {{ completedCount(block) }}/{{ block.items.length }}
              </span>
            </header>

            <div v-if="!block.items.length" class="plan-block-empty">
              <Icon name="coffee" :size="16" />
              <span>本段留白，可自由安排。</span>
            </div>
            <ul v-else class="plan-items">
              <li
                v-for="(item, i) in block.items"
                :key="`${block.timeSlot}-${i}-${item.type}-${itemId(item)}`"
                class="plan-item"
                :class="{ 'item-done': item.completed, current: nextItem === item }"
              >
                <Checkbox
                  :model-value="!!item.completed"
                  :disabled="updating || !!item._loading || !!item.completionRequirement || !['learningTask','todo','habit'].includes(item.type)"
                  class="item-check"
                  @change="(v: boolean) => toggleItem(item, v, block)"
                >
                  <span class="item-main">
                    <span class="item-title">{{ item.title }}</span>
                    <span class="item-sub">
                      <span
                        class="item-type-tag"
                        :class="`tag-${item.type}`"
                      >
                        <Icon :name="typeIcon(item.type)" :size="12" />
                        <span>{{ typeLabel(item.type) }}</span>
                      </span>
                      <span v-if="item.startTime" class="item-time">
                        {{ item.startTime }}{{ item.endTime ? `–${item.endTime}` : '' }}
                      </span>
                      <span v-if="item.duration" class="item-dur">
                        <Icon name="timer" :size="12" />
                        {{ item.duration }} 分钟
                      </span>
                      <span v-if="nextItem === item" class="item-current-flag">下一项</span>
                    </span>
                  </span>
                </Checkbox>
                <TaskCompletionAction :requirement="item.completionRequirement" :title="item.title" />
              </li>
            </ul>
          </section>
        </div>

        <aside class="plan-side" aria-label="今日概览">
          <section class="plan-side-card">
            <h2 class="plan-side-title">今日进度</h2>
            <div class="plan-progress-head">
              <span class="plan-progress-value">{{ displayRatio }}<small>%</small></span>
              <span class="plan-progress-count">已完成 {{ plan?.completedItems ?? 0 }} / {{ plan?.totalItems ?? 0 }} 项</span>
            </div>
            <Progress
              :percentage="displayRatio"
              variant="primary"
              label="完成进度"
            />
            <dl class="plan-stats">
              <div>
                <dt>待完成</dt>
                <dd>{{ remaining }} 项</dd>
              </div>
              <div>
                <dt>计划时长</dt>
                <dd>{{ totalMinutes ? `${totalMinutes} 分钟` : '未估算' }}</dd>
              </div>
            </dl>
            <p class="plan-slot-summary">{{ summaryMorning() }} · {{ summaryAfternoon() }} · {{ summaryEvening() }}</p>
          </section>

          <section class="plan-side-card">
            <h2 class="plan-side-title">下一项</h2>
            <div v-if="nextItem" class="plan-next">
              <span class="plan-next-title">{{ nextItem.title }}</span>
              <span class="plan-next-meta">
                <span class="item-type-tag" :class="`tag-${nextItem.type}`">
                  <Icon :name="typeIcon(nextItem.type)" :size="12" />
                  <span>{{ typeLabel(nextItem.type) }}</span>
                </span>
                <span v-if="nextItem.startTime" class="item-time">{{ nextItem.startTime }}</span>
                <span v-if="nextItem.duration" class="item-dur">{{ nextItem.duration }} 分钟</span>
              </span>
            </div>
            <p v-else class="plan-next-empty">今天的任务都已完成。</p>
          </section>
        </aside>
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
import Progress from '@/components/ui/Progress.vue'
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
  { timeSlot: 'morning', label: '上午', startTime: '07:00', endTime: '12:00', items: [] },
  { timeSlot: 'afternoon', label: '下午', startTime: '13:00', endTime: '18:00', items: [] },
  { timeSlot: 'evening', label: '晚间', startTime: '19:00', endTime: '23:00', items: [] },
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

// 右侧概览使用的派生数据：全部来自服务端计划项，不伪造推荐。
const allItems = computed(() => (plan.value?.blocks ?? []).flatMap((b) => b.items))
const totalMinutes = computed(() => allItems.value.reduce((sum, it) => sum + (it.duration ?? 0), 0))
const remaining = computed(() => Math.max((plan.value?.totalItems ?? 0) - (plan.value?.completedItems ?? 0), 0))
const nextItem = computed(() => allItems.value.find((it) => !it.completed) ?? null)

// -------- Helpers --------
const EMOJI_PATTERN = /[\u{1F300}-\u{1FAFF}\u{2600}-\u{27BF}\u{FE0F}]/gu

/** 时间段名称：优先用服务端 label，去掉历史遗留的重复 Emoji。 */
function slotName(block: PlanBlockVO) {
  const label = (block.label || '').replace(EMOJI_PATTERN, '').trim()
  if (label) return label
  if (block.timeSlot === 'morning') return '上午'
  if (block.timeSlot === 'afternoon') return '下午'
  if (block.timeSlot === 'evening') return '晚间'
  return block.timeSlot
}
/** 每个时间段只保留一个语义图标，不再重复装饰 Emoji。 */
function slotIcon(slot: string) {
  if (slot === 'morning') return 'coffee'
  if (slot === 'evening') return 'moon'
  return 'sun'
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
  if (type === 'habit') return 'repeat'
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
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-4);
  width: 100%;
  min-width: 0;
  padding-bottom: var(--kf-space-4);
}

.today-plan-actions {
  display: flex;
  align-items: center;
  gap: var(--kf-space-2);
  flex-wrap: wrap;
  justify-content: flex-end;
}

.today-plan-date {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 10px;
  border-radius: var(--kf-radius-md);
  background: var(--kf-muted);
  color: var(--kf-muted-foreground);
  font-size: var(--kf-fs-body-sm);
  font-variant-numeric: tabular-nums;
}
.today-plan-all {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 10px;
  border-radius: var(--kf-radius-md);
  color: var(--kf-primary);
  font-size: var(--kf-fs-body-sm);
  text-decoration: none;
  white-space: nowrap;
}
.today-plan-all:hover { background: var(--kf-primary-soft); }
.today-plan-link {
  color: var(--kf-primary);
  text-decoration: none;
}
.today-plan-link:hover { text-decoration: underline; }

/* 时间线 + 概览侧栏：桌面约 2:1，时间段内容自适应高度，不再三列等高。 */
.plan-layout {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(280px, 1fr);
  gap: var(--kf-space-4);
  align-items: start;
}
.plan-main {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-3);
  min-width: 0;
}
.plan-block {
  background: var(--kf-card);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-lg);
  overflow: hidden;
}
.plan-block-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kf-space-3);
  padding: 10px var(--kf-space-4);
}
.plan-block-title {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.plan-block-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
  width: 26px;
  height: 26px;
  border-radius: var(--kf-radius-sm);
  background: var(--kf-muted);
  color: var(--kf-muted-foreground);
}
.block-morning .plan-block-icon { background: var(--kf-warning-soft); color: var(--kf-warning); }
.block-afternoon .plan-block-icon { background: var(--kf-primary-soft); color: var(--kf-primary); }
.block-evening .plan-block-icon { background: var(--kf-muted); color: var(--kf-muted-foreground); }
.plan-block-name-wrap {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}
.plan-block-name {
  margin: 0;
  font-size: var(--kf-fs-h4);
  line-height: 1.3;
  font-weight: 600;
  color: var(--kf-foreground);
}
.plan-block-time {
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
  font-variant-numeric: tabular-nums;
}
.plan-block-count {
  flex: none;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--kf-muted);
  color: var(--kf-muted-foreground);
  font-size: var(--kf-fs-caption);
  font-variant-numeric: tabular-nums;
}
.plan-block-count.complete { background: var(--kf-success-soft); color: var(--kf-success); }
.plan-block-empty {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px var(--kf-space-4);
  border-top: 1px solid var(--kf-border);
  color: var(--kf-muted-foreground);
  font-size: var(--kf-fs-body-sm);
}
.plan-items {
  list-style: none;
  margin: 0;
  padding: 0;
}
.plan-item {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--kf-space-2);
  padding: 10px var(--kf-space-4);
  border-top: 1px solid var(--kf-border);
  transition: background-color 0.15s ease;
}
.plan-item:hover { background: color-mix(in oklab, var(--kf-muted) 45%, var(--kf-card)); }
/* 当前任务只用浅蓝底 + 左侧强调条，不使用整块高饱和背景。 */
.plan-item.current {
  background: var(--kf-primary-subtle);
  box-shadow: inset 3px 0 0 var(--kf-primary);
}
/* 任务行：Checkbox + 标题主体 + 类型/时间/耗时，轻分割线，无整块高饱和底色。 */
.item-check {
  flex: 1 1 auto;
  min-width: 0;
  align-items: flex-start;
  gap: var(--kf-space-3);
  padding: 0;
}
.item-check .kf-checkbox-label {
  display: block;
  flex: 1 1 auto;
  min-width: 0;
}
.item-main {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}
.item-title {
  font-size: var(--kf-fs-body-md);
  line-height: 1.45;
  color: var(--kf-foreground);
  overflow-wrap: anywhere;
}
.item-sub {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--kf-space-2);
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
  font-variant-numeric: tabular-nums;
}
.item-type-tag {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 1px 6px;
  border-radius: 999px;
  font-size: var(--kf-fs-xs);
  font-weight: 500;
  line-height: 1.5;
  white-space: nowrap;
}
.tag-learningTask { background: var(--kf-primary-soft); color: var(--kf-primary); }
.tag-todo { background: var(--kf-muted); color: var(--kf-muted-foreground); }
.tag-habit { background: var(--kf-success-soft); color: var(--kf-success); }
.item-time,
.item-dur { display: inline-flex; align-items: center; gap: 3px; white-space: nowrap; }
.item-current-flag {
  display: inline-flex;
  align-items: center;
  padding: 1px 6px;
  border-radius: 999px;
  background: var(--kf-primary-soft);
  color: var(--kf-primary);
  font-size: var(--kf-fs-xs);
  font-weight: 600;
}
.item-done .item-title {
  color: var(--kf-muted-foreground);
  text-decoration: line-through;
}
:deep(.task-completion-action) { flex-basis: 100%; }

/* 右侧今日概览 */
.plan-side {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-3);
  min-width: 0;
}
.plan-side-card {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-3);
  padding: var(--kf-space-4);
  background: var(--kf-card);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-lg);
}
.plan-side-title {
  margin: 0;
  font-size: var(--kf-fs-body-md);
  font-weight: 600;
  color: var(--kf-foreground);
}
.plan-progress-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--kf-space-3);
}
.plan-progress-value {
  font-size: var(--kf-fs-h1);
  line-height: 1.1;
  font-weight: 600;
  color: var(--kf-foreground);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.plan-progress-value small {
  margin-left: 1px;
  font-size: var(--kf-fs-body-md);
  font-weight: 500;
  color: var(--kf-muted-foreground);
}
.plan-progress-count {
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.plan-stats {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--kf-space-2);
  margin: 0;
  padding-top: var(--kf-space-3);
  border-top: 1px solid var(--kf-border);
}
.plan-stats > div { display: flex; flex-direction: column; gap: 1px; min-width: 0; }
.plan-stats dt { font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
.plan-stats dd {
  margin: 0;
  font-size: var(--kf-fs-body-sm);
  font-weight: 500;
  color: var(--kf-foreground);
  font-variant-numeric: tabular-nums;
}
.plan-slot-summary {
  margin: 0;
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
  font-variant-numeric: tabular-nums;
}
.plan-next { display: flex; flex-direction: column; gap: 4px; min-width: 0; }
.plan-next-title {
  font-size: var(--kf-fs-body-md);
  font-weight: 500;
  color: var(--kf-foreground);
  overflow-wrap: anywhere;
}
.plan-next-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--kf-space-2);
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.plan-next-empty {
  margin: 0;
  font-size: var(--kf-fs-body-sm);
  color: var(--kf-muted-foreground);
}

/* 中等屏：概览改为双栏卡片，时间线单列。 */
@media (max-width: 1100px) {
  .plan-layout { grid-template-columns: minmax(0, 1fr); }
  .plan-side { display: grid; grid-template-columns: 1fr 1fr; align-items: start; }
}
/* 手机：全部单列，任务卡铺满可用宽度。 */
@media (max-width: 768px) {
  /* 页头操作区独占整行，避免四个按钮在窄屏撑出横向溢出。 */
  .today-plan :deep(.kf-page-header > div > div:last-child) { flex: 1 1 100%; width: 100%; min-width: 0; }
  .plan-side { grid-template-columns: 1fr; }
  .plan-block-head { padding: 10px var(--kf-space-3); }
  .plan-item { padding: 10px var(--kf-space-3); }
  .today-plan-actions { width: 100%; justify-content: flex-start; }
}
</style>
