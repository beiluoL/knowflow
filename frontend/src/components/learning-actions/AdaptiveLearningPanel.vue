<template>
  <section class="adaptive-panel" aria-label="真实学习行动" :aria-busy="loading || busy">
    <header class="action-header">
      <div class="action-heading">
        <h2>今日行动</h2>
        <p class="muted">安排今天的学习与复习</p>
      </div>
      <div class="action-toolbar">
        <label class="time-budget">
          <span>今天可用</span>
          <input class="kb-input kb-form-control" v-model.number="minutes" type="number" min="1" max="720" step="1"
                 aria-label="今日可用分钟" :aria-invalid="!validMinutes"
                 :aria-describedby="!validMinutes ? 'action-minutes-error' : undefined"
                 :disabled="loading || busy" @keydown.enter="load" />
          <span class="muted">分钟</span>
        </label>
        <Button variant="text" size="sm" :disabled="loading || busy || !validMinutes" @click="load">刷新</Button>
        <Button :variant="planId ? 'secondary' : 'primary'" size="sm" :loading="busy"
                :disabled="loading || !validMinutes" @click="preview">{{ busy ? '正在准备…' : '预览今日计划' }}</Button>
      </div>
    </header>
    <p v-if="!validMinutes" id="action-minutes-error" class="action-error" role="alert">请输入1–720之间的整数分钟。</p>
    <p v-else-if="data && minutes !== loadedMinutes" class="budget-note" role="status">时间已调整，刷新建议或预览计划后生效。</p>

    <div v-if="loadError" class="action-notice" role="alert">
      <div><p>学习安排读取失败：{{ loadError }}</p><p v-if="data" class="muted">显示上次成功读取的内容，尚未更新。</p></div>
      <Button variant="secondary" size="sm" :disabled="loading || busy || !validMinutes" @click="load">重试读取</Button>
    </div>
    <div v-if="previewError" class="action-notice" role="alert">
      <p>计划预览失败：{{ previewError }}。尚未创建任务。</p>
      <Button variant="secondary" size="sm" :disabled="loading || busy || !validMinutes" @click="preview">重试预览</Button>
    </div>

    <div v-if="loading && !data" class="action-skeleton" data-action-skeleton role="status">
      <span class="sr-only">正在读取学习安排与计划记录…</span>
      <div class="skeleton-main" aria-hidden="true"><span /><span /><span /></div>
      <div class="skeleton-side" aria-hidden="true"><span /><span /></div>
    </div>
    <p v-else-if="loading" class="muted update-notice" role="status">正在更新，暂时显示上次读取的内容…</p>

    <div v-if="data" class="adaptive-body">
      <section class="suggestions" aria-label="行动建议">
        <div class="section-heading"><h3>行动建议</h3><span v-if="data.actions.length" class="muted">{{ data.actions.length }}项 · 预计{{ estimatedMinutes }}分钟</span></div>
        <p class="section-note muted">按{{ loadedMinutes }}分钟可用时间安排，最多展示三项建议；不代表全部待复习内容。</p>
        <div v-if="!data.actions.length" class="action-empty">
          <h4>暂时没有可用的行动建议</h4>
          <p class="muted">可先预览计划，或在完成练习、采用学习路径后刷新安排。</p>
        </div>
        <ul v-else class="suggestion-list">
          <li v-for="a in data.actions" :key="a.key" class="suggestion-item">
            <div class="suggestion-meta"><span class="resource-label">{{ presentation(a).label }}</span><span class="muted">约{{ a.estimatedMinutes }}分钟</span></div>
            <h4>{{ a.title }}</h4>
            <p class="action-description muted">{{ presentation(a).summary }}</p>
            <div class="suggestion-footer">
              <details class="action-details"><summary>查看依据与完成要求</summary><p>{{ a.reason }}</p><p>完成标准：{{ a.completionCondition }}</p></details>
              <Button variant="secondary" size="sm" :aria-label="`${presentation(a).verb}：${a.title}`" @click="open(a)">{{ presentation(a).verb }}</Button>
            </div>
          </li>
        </ul>
      </section>

      <aside class="adaptive-aside">
        <section class="plan-records-section" aria-label="计划执行记录">
          <h3>计划执行记录</h3>
          <p class="section-note muted">仅统计已确认的行动计划（含历史安排）。</p>
          <div v-if="!data.tasks.length" class="record-empty">
            <p>尚未确认今日行动计划</p>
            <p class="muted">点击“预览今日计划”，检查内容后再确认保存。</p>
          </div>
          <template v-else>
            <p class="completion-count">已完成{{ doneCount }}/{{ data.tasks.length }}项<span v-if="data.bounded" class="muted">（最近30项）</span></p>
            <p v-if="doneCount === data.tasks.length" class="muted">本组行动已完成，仍可查看资源与依据。</p>
            <ul class="plan-records" tabindex="0" aria-label="已确认的行动列表">
              <li v-for="t in data.tasks" :key="t.id" :class="{ 'is-complete': t.status === 1 }">
                <span class="record-state">{{ t.status === 1 ? '已完成' : '待完成' }}</span>
                <p class="record-title">{{ t.title }}</p>
                <details v-if="t.eventId || t.status === 1" class="action-details"><summary>查看完成依据</summary><p v-if="t.eventId">依据学习事件 #{{ t.eventId }}；行动完成不等于知识掌握。</p><p v-else>手动完成，不作为掌握证据。</p></details>
                <Button v-if="t.item" variant="text" size="sm" @click="open(t.item)">查看学习资源</Button>
              </li>
            </ul>
          </template>
        </section>

        <section class="review-summary" aria-label="到期复习概况">
          <h3>待复习</h3>
          <p v-if="data.facts.dueFlashcards" class="due-count">{{ data.facts.dueFlashcardsBounded ? '至少' : '' }}{{ data.facts.dueFlashcards }}张<span class="muted">个人闪卡已到期</span></p>
          <p v-else>暂无到期闪卡</p>
          <p class="section-note muted">复习后，下次到期时间由你的评分重新安排。</p>
        </section>

        <section aria-label="巩固提示">
          <h3>巩固提示</h3>
          <p v-if="!data.weakSignals.length" class="section-note muted">{{ data.facts.masteryRecordsWithEvidence === 0 ? '学习记录还不足以判断薄弱点，可先通过真实作答建立记录。' : '当前没有达到诊断条件的巩固提示，不代表已经掌握。' }}</p>
          <ul v-else class="weak-signals"><li v-for="a in data.weakSignals" :key="a.key"><p>{{ a.title }}</p><details class="action-details"><summary>查看依据</summary><p>{{ a.reason }}</p></details></li></ul>
          <details v-if="data.missingData.length" class="action-details missing-data"><summary>哪些记录还不完整</summary><ul><li v-for="m in data.missingData" :key="m">{{ m }}</li></ul><p>缺少记录不等于薄弱，也不代表已经掌握。</p></details>
        </section>
      </aside>
    </div>

    <TaskPlanCard v-if="planId" :plan-id="planId" @confirmed="refreshConfirmed">
      <template #item-details="{ item }">
        <p class="muted">{{ presentation(item).summary }}</p>
        <details class="action-details"><summary>查看依据与完成要求</summary><p>{{ item.reason }}</p><p>完成标准：{{ item.completionCondition }}</p></details>
      </template>
    </TaskPlanCard>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { learningActionApi, type LearningDashboard, type LearningActionDraft, type PlanItem } from '@/api/learning-actions'
import Button from '@/components/ui/Button.vue'
import { planResourceRoute } from './plan-link'
import TaskPlanCard from './TaskPlanCard.vue'

const router = useRouter()
const data = ref<LearningDashboard | null>(null)
const minutes = ref<number | string>(60)
const loadedMinutes = ref<number | null>(null)
const loading = ref(false), busy = ref(false), loadError = ref(''), previewError = ref(''), planId = ref('')
const validMinutes = computed(() => Number.isInteger(minutes.value) && Number(minutes.value) >= 1 && Number(minutes.value) <= 720)
const doneCount = computed(() => data.value?.tasks.filter(t => t.status === 1).length ?? 0)
const estimatedMinutes = computed(() => data.value?.actions.reduce((sum, a) => sum + a.estimatedMinutes, 0) ?? 0)
let generation = 0, previewGeneration = 0
let confirmationBudget: number | null = null
let active = true

function message(error: unknown) { return error instanceof Error ? error.message : '请检查网络后重试' }
async function load() {
  if (loading.value || busy.value || !validMinutes.value) return
  return readDashboard(Number(minutes.value))
}
async function readDashboard(budget: number) {
  const current = ++generation
  loading.value = true
  loadError.value = ''
  try {
    const result = await learningActionApi.dashboard(budget)
    if (current === generation) { data.value = result; loadedMinutes.value = budget }
  } catch (error: unknown) { if (current === generation) loadError.value = message(error) }
  finally { if (current === generation) { loading.value = false; drainConfirmationRefresh() } }
}
async function preview() {
  if (busy.value || loading.value || !validMinutes.value) return
  const current = ++previewGeneration
  busy.value = true
  previewError.value = ''
  try {
    const plan = await learningActionApi.preview({ availableMinutes: Number(minutes.value), zoneId: Intl.DateTimeFormat().resolvedOptions().timeZone, includeInterview: false, useAi: false })
    if (current === previewGeneration) planId.value = plan.planId
  } catch (error: unknown) { if (current === previewGeneration) previewError.value = message(error) }
  finally { if (current === previewGeneration) { busy.value = false; drainConfirmationRefresh() } }
}

// A confirmed save must be read back even if an earlier refresh is still in flight.
function refreshConfirmed(saved: LearningActionDraft) {
  const budget = validMinutes.value ? Number(minutes.value) : loadedMinutes.value ?? saved.availableMinutes
  if (!Number.isInteger(budget) || budget < 1 || budget > 720) return
  confirmationBudget = budget
  drainConfirmationRefresh()
}
function drainConfirmationRefresh() {
  if (!active || confirmationBudget === null || loading.value || busy.value) return
  const budget = confirmationBudget
  confirmationBudget = null
  void readDashboard(budget)
}

// Presentation only: use explicit resource/type fields, never parse reasons or rank actions.
function presentation(item: Pick<PlanItem, 'resourceType' | 'type'>) {
  if (item.type === 'CODE') return { label: '编程练习', verb: '打开编程练习', summary: '结合真实题目练习代码，并核对结果与边界。' }
  if (item.resourceType === 'FLASHCARD') return { label: '闪卡复习', verb: '复习闪卡', summary: '先主动回忆，再核对答案并记录本次评分。' }
  if (item.resourceType === 'QUIZ') return { label: '题目练习', verb: '开始练习', summary: '通过实际作答与解析核对，检查自己的理解。' }
  if (item.resourceType === 'CHAPTER') return { label: '章节学习', verb: '学习章节', summary: '继续已采用的学习路径，理解概念、边界与例子。' }
  if (item.resourceType === 'KNOWLEDGE') return { label: item.type === 'REVIEW' ? '知识复习' : '知识巩固', verb: '查看知识点', summary: '根据已有学习记录，回忆概念并核对来源。' }
  if (item.type === 'INTERVIEW') return { label: '模拟面试', verb: '打开模拟面试', summary: '结合面试问题练习作答与复盘。' }
  return { label: '学习资源', verb: '打开学习资源', summary: '查看学习内容，并按完成要求开展练习。' }
}
function open(item: PlanItem) { router.push(planResourceRoute(item)) }
onMounted(load)
onUnmounted(() => { active = false; confirmationBudget = null; generation++; previewGeneration++ })
</script>

<style scoped>
.adaptive-panel { container-type:inline-size;min-width:0;margin-block:var(--kb-space-6);padding:var(--kb-space-5);border:var(--kb-studio-rule) solid var(--kb-border);border-radius:var(--kb-radius-md);background:var(--kb-card);color:var(--kb-foreground);font-size:var(--kb-fs-body-sm);line-height:var(--kb-lh-body-sm); }
.action-header,.action-toolbar,.time-budget,.section-heading,.suggestion-meta,.suggestion-footer,.action-notice { display:flex;align-items:center;gap:var(--kb-space-3);flex-wrap:wrap;min-width:0; }
.action-header,.section-heading { justify-content:space-between; }
.action-header { align-items:flex-start;gap:var(--kb-space-4); }
.action-heading h2 { font-size:var(--kb-fs-h4);font-weight:var(--kb-fw-h3);line-height:var(--kb-lh-h4); }
.action-heading p { margin-top:var(--kb-space-1); }
.action-toolbar { gap:var(--kb-space-2); }
.time-budget { gap:var(--kb-space-2); }
.time-budget input { width:calc(var(--kb-space-8) * 2);min-width:0;height:var(--kb-btn-height-sm);padding-inline:var(--kb-space-2);border:var(--kb-studio-rule) solid var(--kb-border);border-radius:var(--kb-field-radius);background:var(--kb-background);color:var(--kb-foreground); }
.time-budget input[aria-invalid="true"] { border-color:var(--kb-destructive); }
.time-budget input:disabled { opacity:.6; }
.muted,.action-details { color:var(--kb-muted-foreground); }
.budget-note,.action-error,.update-notice { margin-top:var(--kb-space-3); }
.action-error { color:var(--kb-destructive); }
.action-notice { justify-content:space-between;margin-top:var(--kb-space-4);padding:var(--kb-space-3);border:var(--kb-studio-rule) solid var(--kb-border);border-inline-start-color:var(--kb-destructive);border-radius:var(--kb-radius-sm); }
.adaptive-body { display:grid;grid-template-columns:minmax(0,1fr);gap:var(--kb-space-6);margin-top:var(--kb-space-5); }
.adaptive-body section,.adaptive-aside { min-width:0; }
h3 { font-size:var(--kb-fs-body-lg);font-weight:var(--kb-fw-h4);line-height:var(--kb-lh-h4); }
h4 { font-size:var(--kb-fs-body-md);font-weight:var(--kb-fw-h4);line-height:var(--kb-lh-body-md);overflow-wrap:anywhere; }
p,li { overflow-wrap:anywhere; }
.section-note { margin-top:var(--kb-space-2);font-size:var(--kb-fs-caption); }
ul { list-style:none;margin:0;padding:0; }
.suggestion-list { margin-top:var(--kb-space-2); }
.suggestion-item { padding-block:var(--kb-space-4);border-bottom:var(--kb-studio-rule) solid var(--kb-border); }
.suggestion-item:last-child { border-bottom:0;padding-bottom:0; }
.suggestion-meta { gap:var(--kb-space-3);font-size:var(--kb-fs-caption);margin-bottom:var(--kb-space-2); }
.resource-label { color:var(--kb-primary); }
.action-description { margin-top:var(--kb-space-2); }
.suggestion-footer { justify-content:space-between;align-items:flex-start;margin-top:var(--kb-space-3); }
.suggestion-footer>details { flex:1 1 50%; }
.action-details { font-size:var(--kb-fs-caption);min-width:0; }
.action-details summary { cursor:pointer;width:fit-content;max-width:100%; }
.action-details[open] summary { margin-bottom:var(--kb-space-2); }
.action-details p+ p { margin-top:var(--kb-space-2); }
.action-details ul { list-style:disc;padding-inline-start:var(--kb-space-4); }
.action-details li { margin-block:var(--kb-space-1); }
.action-details summary:focus-visible,.plan-records:focus-visible { outline:var(--kb-field-focus-width) solid var(--kb-ring);outline-offset:var(--kb-space-1); }
.adaptive-aside { display:flex;flex-direction:column;gap:var(--kb-space-5); }
.adaptive-aside>section+section { border-top:var(--kb-studio-rule) solid var(--kb-border);padding-top:var(--kb-space-4); }
.record-empty,.completion-count,.due-count { margin-top:var(--kb-space-3); }
.record-empty p+p { font-size:var(--kb-fs-caption);margin-top:var(--kb-space-2); }
.completion-count { font-weight:var(--kb-fw-h4); }
.completion-count span { font-weight:normal; }
.plan-records { max-block-size:calc(var(--kb-space-8) * 12);overflow-y:auto;scrollbar-gutter:stable;margin-top:var(--kb-space-2); }
.plan-records li { padding-block:var(--kb-space-3);border-bottom:var(--kb-studio-rule) solid var(--kb-border); }
.plan-records li:last-child { border-bottom:0; }
.record-state { font-size:var(--kb-fs-caption);color:var(--kb-primary); }
.record-title { margin-block:var(--kb-space-1); }
.is-complete .record-state,.is-complete .record-title { color:var(--kb-muted-foreground); }
.plan-records button { padding-inline:0; }
.due-count { font-size:var(--kb-fs-body-md);font-weight:var(--kb-fw-h4); }
.due-count span { display:block;font-size:var(--kb-fs-caption);font-weight:normal;margin-top:var(--kb-space-1); }
.weak-signals li { margin-top:var(--kb-space-3); }
.weak-signals details { margin-top:var(--kb-space-1); }
.missing-data { margin-top:var(--kb-space-3); }
.action-empty { padding-block:var(--kb-space-5); }
.action-empty p { margin-top:var(--kb-space-2); }
.action-skeleton { display:grid;grid-template-columns:minmax(0,2fr) minmax(0,1fr);gap:var(--kb-space-6);margin-top:var(--kb-space-5);min-height:calc(var(--kb-space-8) * 8); }
.action-skeleton span { display:block;height:var(--kb-space-6);background:var(--kb-muted);border-radius:var(--kb-radius-sm);margin-block:var(--kb-space-5); }
.action-skeleton span:nth-child(2) { width:75%; }
@container (min-width:58rem) {
  .adaptive-body { grid-template-columns:minmax(0,1.8fr) minmax(0,1fr); }
  .adaptive-aside { border-inline-start:var(--kb-studio-rule) solid var(--kb-border);padding-inline-start:var(--kb-space-6); }
}
@container (max-width:34rem) {
  .action-toolbar { width:100%; }
  .action-header { gap:var(--kb-space-3); }
  .suggestion-footer { gap:var(--kb-space-2); }
  .action-skeleton { grid-template-columns:minmax(0,1fr); }
  .skeleton-side { display:none; }
}
</style>
