<template>
  <AppDialog open scrollable icon="settings" title="闪卡复习设置" description="先设定记忆目标与每日上限，按需调整高级参数。"
             width="calc(var(--kb-space-8) * 16.25)" :persistent="busy" @cancel="close">
    <div v-if="loading" class="settings-loading" role="status" aria-busy="true">
      <span class="sr-only">正在读取复习设置…</span><span aria-hidden="true" /><span aria-hidden="true" /><span aria-hidden="true" />
    </div>
    <div v-else-if="loadError" class="settings-notice" role="alert"><p>{{ loadError }}</p><Button size="sm" variant="secondary" @click="load">重试读取</Button></div>
    <form v-else-if="source" ref="form" class="settings-form" novalidate @submit.prevent="save">
      <section aria-labelledby="flash-memory-title">
        <h4 id="flash-memory-title">记忆目标</h4>
        <div class="settings-field retention-field">
          <label for="flash-retention-percent">目标保持率</label>
          <div class="unit-control"><input id="flash-retention-percent" :value="draft.retention" @input="draft.retention = ($event.target as HTMLInputElement).value" class="kb-input settings-number kb-form-control" type="number" min="70" max="99" step="any" inputmode="decimal" :disabled="locked" :aria-invalid="!!errors.retention" :aria-describedby="describedBy('retention')" @blur="checkField('retention')" /><span aria-hidden="true">%</span></div>
          <p id="flash-retention-percent-hint" class="field-hint">到期复习时，希望仍能记住的目标概率。提高目标通常会增加复习频率。支持70%–99%。</p>
          <p v-if="errors.retention" id="flash-retention-percent-error" class="field-error" role="alert">{{ errors.retention }}</p>
        </div>
      </section>

      <section aria-labelledby="flash-limits-title">
        <h4 id="flash-limits-title">每日学习限制</h4>
        <div class="settings-grid">
          <div class="settings-field"><label for="flash-new-limit">每日新卡上限</label><div class="unit-control"><input id="flash-new-limit" :value="draft.newLimit" @input="draft.newLimit = ($event.target as HTMLInputElement).value" class="kb-input settings-number kb-form-control" type="number" min="0" max="1000" step="1" inputmode="numeric" :disabled="locked" :aria-invalid="!!errors.newLimit" :aria-describedby="describedBy('newLimit')" @blur="checkField('newLimit')" /><span>张/天</span></div><p id="flash-new-limit-hint" class="field-hint">当天首次评分的新卡上限。0表示不引入新卡。</p><p v-if="errors.newLimit" id="flash-new-limit-error" class="field-error" role="alert">{{ errors.newLimit }}</p></div>
          <div class="settings-field"><label for="flash-review-limit">每日复习上限</label><div class="unit-control"><input id="flash-review-limit" :value="draft.reviewLimit" @input="draft.reviewLimit = ($event.target as HTMLInputElement).value" class="kb-input settings-number kb-form-control" type="number" min="0" max="10000" step="1" inputmode="numeric" :disabled="locked" :aria-invalid="!!errors.reviewLimit" :aria-describedby="describedBy('reviewLimit')" @blur="checkField('reviewLimit')" /><span>次/天</span></div><p id="flash-review-limit-hint" class="field-hint">其余评分按次数计数，同一张卡多次评分会分别计数。0表示暂停复习评分。</p><p v-if="errors.reviewLimit" id="flash-review-limit-error" class="field-error" role="alert">{{ errors.reviewLimit }}</p></div>
        </div>
      </section>

      <details :open="advancedOpen" class="advanced-settings" @toggle="advancedOpen = ($event.target as HTMLDetailsElement).open">
        <summary><span>高级参数</span><span class="field-hint">调度、时区与学习日</span><Icon name="chevron-down" :size="16" class="advanced-chevron" /></summary>
        <div class="advanced-body">
          <section aria-labelledby="flash-schedule-title"><h4 id="flash-schedule-title">调度参数</h4>
            <div class="settings-field"><label for="flash-maximum-interval">最大间隔</label><div class="unit-control"><input id="flash-maximum-interval" :value="draft.maximumInterval" @input="draft.maximumInterval = ($event.target as HTMLInputElement).value" class="kb-input settings-number kb-form-control" type="number" min="1" max="36500" step="1" inputmode="numeric" :disabled="locked" :aria-invalid="!!errors.maximumInterval" :aria-describedby="describedBy('maximumInterval')" @blur="checkField('maximumInterval')" /><span>天</span></div><p id="flash-maximum-interval-hint" class="field-hint">用于限制卡片复习间隔的最大值，支持1–36500天。</p><p v-if="errors.maximumInterval" id="flash-maximum-interval-error" class="field-error" role="alert">{{ errors.maximumInterval }}</p></div>
            <div class="settings-field"><label for="flash-learning-steps">学习步长</label><input id="flash-learning-steps" v-model="draft.learningSteps" class="kb-input kb-form-control" type="text" :disabled="locked" :aria-invalid="!!errors.learningSteps" :aria-describedby="describedBy('learningSteps')" @blur="checkField('learningSteps')" /><p id="flash-learning-steps-hint" class="field-hint">新卡初始学习的间隔，单位为分钟。用逗号分隔，如1,10；可留空，否则最多6项、严格递增，每项1–1440。</p><p v-if="errors.learningSteps" id="flash-learning-steps-error" class="field-error" role="alert">{{ errors.learningSteps }}</p></div>
            <div class="settings-field"><label for="flash-relearning-steps">重学步长</label><input id="flash-relearning-steps" v-model="draft.relearningSteps" class="kb-input kb-form-control" type="text" :disabled="locked" :aria-invalid="!!errors.relearningSteps" :aria-describedby="describedBy('relearningSteps')" @blur="checkField('relearningSteps')" /><p id="flash-relearning-steps-hint" class="field-hint">遗忘后重新学习的短期步骤，单位为分钟。格式和范围与学习步长相同，可留空。</p><p v-if="errors.relearningSteps" id="flash-relearning-steps-error" class="field-error" role="alert">{{ errors.relearningSteps }}</p></div>
          </section>
          <section aria-labelledby="flash-time-title"><h4 id="flash-time-title">时间与日期</h4>
            <div class="settings-field"><label for="flash-timezone">时区</label><input id="flash-timezone" v-model="draft.timezone" class="kb-input kb-form-control" type="text" list="flash-timezones" autocomplete="off" :disabled="locked" :aria-invalid="!!errors.timezone" :aria-describedby="describedBy('timezone')" @blur="checkField('timezone')" /><datalist id="flash-timezones"><option v-for="zone in timezoneOptions" :key="zone" :value="zone" :label="zone === 'Asia/Shanghai' ? '中国标准时间（Asia/Shanghai）' : zone" /></datalist><p id="flash-timezone-hint" class="field-hint">{{ draft.timezone === 'Asia/Shanghai' ? '中国标准时间。' : '' }}可搜索时区或直接输入已有标识；有效性将在保存时核对。</p><p v-if="errors.timezone" id="flash-timezone-error" class="field-error" role="alert">{{ errors.timezone }}</p></div>
            <div class="settings-field"><label for="flash-day-start">每日切换时间</label><select id="flash-day-start" v-model="draft.dayStart" class="kb-select settings-hour kb-form-control" :disabled="locked" :aria-invalid="!!errors.dayStart" :aria-describedby="describedBy('dayStart')" @blur="checkField('dayStart')"><option v-for="hour in 24" :key="hour" :value="String(hour - 1)">{{ String(hour - 1).padStart(2, '0') }}:00</option></select><p id="flash-day-start-hint" class="field-hint">以所选时区的本地时间作为新学习日的开始，保存后采用该时区，不随浏览器时区自动切换。</p><p v-if="errors.dayStart" id="flash-day-start-error" class="field-error" role="alert">{{ errors.dayStart }}</p></div>
          </section>
        </div>
      </details>
      <div v-if="saveError || readbackError" ref="resultNotice" tabindex="-1" class="settings-notice" role="alert"><p>{{ readbackError ? `设置已保存，但暂时无法读取最新设置：${readbackError}。可重试读取确认结果。` : `保存失败：${saveError}。输入已保留，请检查后重试。` }}</p><Button v-if="readbackError" variant="secondary" size="sm" :loading="readingBack" @click="readBack">重试读取</Button></div>
    </form>
    <template #footer>
      <p v-if="source && !written" class="footer-note">新设置用于后续排程与每日计数。</p>
      <div class="settings-actions"><Button variant="secondary" :disabled="busy" @click="close">{{ written ? '关闭' : '取消' }}</Button><Button v-if="!loadError && !written" :disabled="loading || !source || !dirty" :loading="saving" @click="save">{{ saving ? '保存中…' : '保存设置' }}</Button></div>
    </template>
  </AppDialog>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import AppDialog from '@/components/ui/AppDialog.vue'
import Button from '@/components/ui/Button.vue'
import Icon from '@/components/ui/Icon.vue'
import { learningApi } from '@/api/learning'
import type { FlashcardReviewSettings } from '@/api/types'
import { getApiError, notify } from '@/utils/toast'

const emit = defineEmits<{ close: []; loaded: [settings: FlashcardReviewSettings]; saved: [settings: FlashcardReviewSettings] }>()
type Field = 'retention' | 'newLimit' | 'reviewLimit' | 'maximumInterval' | 'learningSteps' | 'relearningSteps' | 'timezone' | 'dayStart'
const ids: Record<Field, string> = { retention: 'flash-retention-percent', newLimit: 'flash-new-limit', reviewLimit: 'flash-review-limit', maximumInterval: 'flash-maximum-interval', learningSteps: 'flash-learning-steps', relearningSteps: 'flash-relearning-steps', timezone: 'flash-timezone', dayStart: 'flash-day-start' }
const draft = reactive<Record<Field, string>>({ retention: '', newLimit: '', reviewLimit: '', maximumInterval: '', learningSteps: '', relearningSteps: '', timezone: '', dayStart: '' })
const source = ref<FlashcardReviewSettings | null>(null), initialDraft = ref('')
const loading = ref(true), saving = ref(false), readingBack = ref(false), written = ref(false), advancedOpen = ref(false)
const loadError = ref(''), saveError = ref(''), readbackError = ref(''), errors = ref<Partial<Record<Field, string>>>({})
const form = ref<HTMLFormElement | null>(null)
const resultNotice = ref<HTMLElement | null>(null)
const busy = computed(() => saving.value || readingBack.value), locked = computed(() => busy.value || written.value)
const dirty = computed(() => JSON.stringify(draft) !== initialDraft.value)
const touched = new Set<Field>()
let attempted = false, active = true, generation = 0
const intl = Intl as typeof Intl & { supportedValuesOf?: (key: string) => string[] }
const knownTimezones = intl.supportedValuesOf?.('timeZone') ?? []
const timezoneOptions = computed(() => [...new Set([source.value?.timezone, 'Asia/Shanghai', 'UTC', ...knownTimezones].filter((zone): zone is string => !!zone))])

// Move the decimal point as text so a legal saved double is not rounded on opening.
function percent(value: number) {
  if (String(value).includes('e')) return String(value * 100)
  const [whole, fraction = ''] = String(value).split('.'), digits = fraction.padEnd(2, '0')
  const tail = digits.slice(2).replace(/0+$/, '')
  return `${Number(whole) * 100 + Number(digits.slice(0, 2))}${tail ? `.${tail}` : ''}`
}
function retentionValue(value: string) {
  const decimal = /^\+?(\d+)(?:\.(\d*))?$/.exec(value.trim())
  // The validated percentage is 70–99, so moving its decimal needs two digits.
  return decimal ? Number(`0.${String(Number(decimal[1])).padStart(2, '0')}${decimal[2] ?? ''}`) : Number(value) / 100
}
function complete(value: FlashcardReviewSettings): boolean {
  const s = value?.scheduling
  return !!s && [s.desiredRetention, s.maximumInterval, value.newCardLimit, value.reviewLimit, value.dayStartHour].every(n => typeof n === 'number' && Number.isFinite(n)) && Array.isArray(s.learningMinutes) && Array.isArray(s.relearningMinutes) && [...s.learningMinutes, ...s.relearningMinutes].every(n => typeof n === 'number' && Number.isFinite(n)) && typeof value.timezone === 'string'
}
async function load() {
  if (busy.value) return
  const current = ++generation; loading.value = true; loadError.value = ''
  try {
    const value = await learningApi.flashcardSettings()
    if (!active || current !== generation) return
    if (!complete(value)) throw new Error('当前配置不完整，未使用默认值覆盖。请重试读取；若仍失败，请联系管理员检查原配置。')
    source.value = value
    Object.assign(draft, { retention: percent(value.scheduling.desiredRetention), newLimit: String(value.newCardLimit), reviewLimit: String(value.reviewLimit), maximumInterval: String(value.scheduling.maximumInterval), learningSteps: value.scheduling.learningMinutes.join(','), relearningSteps: value.scheduling.relearningMinutes.join(','), timezone: value.timezone, dayStart: String(value.dayStartHour) })
    initialDraft.value = JSON.stringify(draft); errors.value = {}; emit('loaded', value)
  } catch (e: unknown) { if (active && current === generation) loadError.value = getApiError(e, '无法读取复习设置，请重试') }
  finally { if (active && current === generation) loading.value = false }
}
function steps(value: string): number[] | string {
  if (!value.trim()) return []
  const parts = value.split(/[,，]/).map(s => s.trim())
  if (parts.length > 6) return '最多填写6个步长。'
  if (parts.some(s => !/^\d+$/.test(s) || Number(s) < 1 || Number(s) > 1440)) return '每个步长需为1–1440的整数分钟，用逗号分隔；也可留空。'
  const result = parts.map(Number)
  if (result.some((n, i) => i > 0 && n <= result[i - 1]!)) return '步长必须严格递增，不能重复或倒序。'
  return result
}
function validate(): Partial<Record<Field, string>> {
  const result: Partial<Record<Field, string>> = {}
  if (!draft.retention.trim() || !Number.isFinite(Number(draft.retention)) || Number(draft.retention) < 70 || Number(draft.retention) > 99) result.retention = '请输入70%–99%之间的目标保持率，可保留小数。'
  for (const [key, min, max] of [['newLimit', 0, 1000], ['reviewLimit', 0, 10000], ['maximumInterval', 1, 36500], ['dayStart', 0, 23]] as const) {
    if (!draft[key].trim() || !Number.isInteger(Number(draft[key])) || Number(draft[key]) < min || Number(draft[key]) > max) result[key] = `请输入${min}–${max}之间的整数。`
  }
  for (const key of ['learningSteps', 'relearningSteps'] as const) { const parsed = steps(draft[key]); if (typeof parsed === 'string') result[key] = parsed }
  if (!draft.timezone.trim()) result.timezone = '请填写时区，例如Asia/Shanghai。'
  // Java ZoneId accepts valid aliases/offset zones that browser Intl may not know.
  // Preserve them and leave final timezone validity to the existing server contract.
  return result
}
function checkField(field: Field) { touched.add(field); const all = validate(); errors.value = Object.fromEntries(Object.entries(all).filter(([key]) => attempted || touched.has(key as Field))) }
watch(draft, () => { if (attempted || touched.size) { const all = validate(); errors.value = Object.fromEntries(Object.entries(all).filter(([key]) => attempted || touched.has(key as Field))) } })
function describedBy(field: Field) { return `${ids[field]}-hint${errors.value[field] ? ` ${ids[field]}-error` : ''}` }
async function save() {
  if (busy.value || loading.value || !source.value || written.value || !dirty.value) return
  attempted = true; errors.value = validate()
  const first = (Object.keys(ids) as Field[]).find(key => errors.value[key])
  if (first) {
    if (Object.keys(errors.value).some(key => !['retention', 'newLimit', 'reviewLimit'].includes(key))) advancedOpen.value = true
    await nextTick(); form.value?.querySelector<HTMLElement>(`#${ids[first]}`)?.focus(); return
  }
  const retention = draft.retention === percent(source.value.scheduling.desiredRetention) ? source.value.scheduling.desiredRetention : retentionValue(draft.retention)
  const payload: FlashcardReviewSettings = { ...source.value, newCardLimit: Number(draft.newLimit), reviewLimit: Number(draft.reviewLimit), timezone: draft.timezone, dayStartHour: Number(draft.dayStart), scheduling: { ...source.value.scheduling, desiredRetention: retention, maximumInterval: Number(draft.maximumInterval), learningMinutes: steps(draft.learningSteps) as number[], relearningMinutes: steps(draft.relearningSteps) as number[] } }
  saving.value = true; saveError.value = ''
  try {
    const saved = await learningApi.saveFlashcardSettings(payload)
    if (!active) return
    written.value = true; emit('saved', saved)
    await readBack()
  } catch (e: unknown) { if (active) { saveError.value = getApiError(e, '请检查设置后重试'); await focusResultNotice() } }
  finally { if (active) saving.value = false }
}
async function readBack() {
  if (readingBack.value) return
  readingBack.value = true; readbackError.value = ''
  try {
    const latest = await learningApi.flashcardSettings()
    if (!active) return
    if (!complete(latest)) throw new Error('回读配置不完整，请重试读取')
    emit('loaded', latest); notify('复习设置已保存', 'success'); emit('close')
  } catch (e: unknown) { if (active) { readbackError.value = getApiError(e, '请稍后重试读取'); await focusResultNotice() } }
  finally { if (active) readingBack.value = false }
}
async function focusResultNotice() { await nextTick(); if (active) resultNotice.value?.focus() }
function close() { if (!busy.value) emit('close') }
onMounted(load)
onUnmounted(() => { active = false; generation++ })
</script>

<style scoped>
.settings-form { display:grid;gap:var(--kb-space-5);color:var(--kb-foreground);font-size:var(--kb-fs-body-sm);line-height:var(--kb-lh-body-sm);min-width:0; }
.settings-form section { min-width:0; }
h4 { font-size:var(--kb-fs-body-md);font-weight:var(--kb-fw-h4);line-height:var(--kb-lh-body-md);margin-bottom:var(--kb-space-3); }
.settings-grid { display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:var(--kb-space-4); }
.settings-field { display:grid;gap:var(--kb-space-2);min-width:0; }
.settings-field+.settings-field { margin-top:var(--kb-space-4); }
.settings-grid>.settings-field { margin:0; }
label { font-weight:var(--kb-fw-h4); }
.unit-control { display:grid;grid-template-columns:minmax(0,1fr) auto;align-items:center;gap:var(--kb-space-2); }
.unit-control>span { color:var(--kb-muted-foreground);white-space:nowrap;font-size:var(--kb-fs-caption); }
.retention-field>.unit-control { max-width:calc(var(--kb-space-8) * 6); }
.settings-field input,.settings-hour { width:100%;min-width:0;max-width:100%;font-size:var(--kb-fs-body-sm);color:var(--kb-foreground); }
.settings-field input:not(.kb-form-control),.settings-hour:not(.kb-form-control) { height:var(--kb-btn-height-md);background:var(--kb-card); }
.settings-hour:not(.kb-form-control) { border:var(--kb-studio-rule) solid var(--kb-border);border-radius:var(--kb-field-radius); }
.settings-hour {padding-inline:var(--kb-space-3); }
.settings-number { appearance:textfield;-moz-appearance:textfield; }
.settings-number::-webkit-inner-spin-button,.settings-number::-webkit-outer-spin-button { appearance:none;margin:0; }
.settings-field [aria-invalid="true"] { border-color:var(--kb-destructive); }
.settings-field :disabled { opacity:.6;cursor:not-allowed; }
.field-hint,.footer-note { color:var(--kb-muted-foreground);font-size:var(--kb-fs-caption);line-height:var(--kb-lh-body-sm);overflow-wrap:anywhere; }
.field-error { color:var(--kb-destructive);font-size:var(--kb-fs-caption);overflow-wrap:anywhere; }
.advanced-settings { border-top:var(--kb-studio-rule) solid var(--kb-border);padding-top:var(--kb-space-4);min-width:0; }
.advanced-settings>summary { display:flex;align-items:center;gap:var(--kb-space-2);cursor:pointer;list-style:none;font-weight:var(--kb-fw-h4); }
.advanced-settings>summary::-webkit-details-marker { display:none; }
.advanced-settings>summary>.field-hint { font-weight:normal; }
.advanced-chevron { margin-inline-start:auto;flex-shrink:0;transition:transform .15s; }
.advanced-settings[open]>summary { margin-bottom:var(--kb-space-4); }
.advanced-settings[open] .advanced-chevron { transform:rotate(180deg); }
.advanced-body { display:grid;gap:var(--kb-space-5);padding-top:var(--kb-space-4); }
.advanced-body h4 { color:var(--kb-muted-foreground);font-size:var(--kb-fs-body-sm); }
.settings-notice { display:grid;gap:var(--kb-space-3);margin-top:var(--kb-space-4);padding:var(--kb-space-3);border:var(--kb-studio-rule) solid var(--kb-border);border-inline-start-color:var(--kb-destructive);border-radius:var(--kb-radius-sm);font-size:var(--kb-fs-body-sm);line-height:var(--kb-lh-body-sm);color:var(--kb-foreground);overflow-wrap:anywhere; }
.settings-notice button { justify-self:start; }
.footer-note { flex:1 1 40%; }
.settings-actions { display:flex;justify-content:flex-end;gap:var(--kb-space-2);margin-inline-start:auto; }
.settings-loading { min-height:calc(var(--kb-space-8) * 8); }
.settings-loading>span:not(.sr-only) { display:block;background:var(--kb-muted);height:var(--kb-space-8);border-radius:var(--kb-radius-sm);margin-block:var(--kb-space-5); }
.settings-loading>span:last-child { width:70%; }
.advanced-settings>summary:focus-visible,.settings-hour:focus-visible:not(.kb-form-control) { outline:var(--kb-field-focus-width) solid var(--kb-ring);outline-offset:var(--kb-space-1); }
@media (max-width:30rem) { .settings-grid { grid-template-columns:minmax(0,1fr); }.advanced-settings>summary>.field-hint { display:none; } }
@media (prefers-reduced-motion:reduce) { .advanced-chevron { transition:none; } }
</style>
