<template>
  <CareerWorkbenchLayout subtitle="结构化条目必须绑定原文证据；每次正式保存都会生成不可变版本。">
    <template #actions>
      <Button variant="secondary" size="sm" @click="router.push('/career/resumes')">
        <Icon name="arrow-left" :size="14" /><span>返回简历中心</span>
      </Button>
      <Button variant="secondary" size="sm" :disabled="loading || busy || parsing" @click="editor?.saveDraft()">保存草稿</Button>
      <Button variant="secondary" size="sm" @click="editor?.openPreview()">预览</Button>
      <Button v-if="record" variant="secondary" size="sm" @click="showVersions">版本管理</Button>
      <div v-if="record" class="resume-page-export" aria-label="文件导出">
        <Select :model-value="designKey" :options="designOptions" :disabled="designLoading || busy" aria-label="选择导出设计" @update:model-value="selectExportDesign" />
        <Button variant="secondary" size="sm" :disabled="designLoading || !!designError || busy" @click="openTemplateEditor">模板选择</Button>
        <Button variant="secondary" size="sm" :loading="preparingPdf" :disabled="designLoading || !!designError || busy || parsing" @click="exportPdf">导出 PDF</Button>
        <ResumeDocxExportButton :doc="toDocument(record)" :theme="activeTheme" :disabled="designLoading || !!designError || busy || parsing" />
      </div>
      <Button
        v-if="record?.confirmed && !record.isDefault"
        variant="secondary"
        size="sm"
        :disabled="busy"
        @click="makeDefault"
      >
        设为默认
      </Button>
    </template>

    <StateView
      v-if="loading && !record"
      status="loading"
      type="card"
      :cols="2"
      :rows="3"
    />
    <StateView
      v-else-if="error && !record"
      status="error"
      :error-description="error"
      retry-text="重新加载简历"
      @retry="reload"
    />
    <template v-else>
      <p v-if="error" class="resume-page-error" role="alert">{{ error }}</p>
      <p v-if="designError" class="resume-page-error" role="alert">{{ designError }} <Button size="sm" variant="secondary" @click="loadExportDesigns">重试读取设计</Button></p>
      <p v-if="record" class="resume-page-export-hint">导出使用已保存的 v{{ record.version }} 与所选设计；尚未正式保存的编辑不会进入导出文件。</p>
      <p v-if="message" class="resume-page-message" role="status">{{ message }}</p>

      <ResumeParsePreview
        v-if="record && !record.confirmed && hasSections"
        :record="record"
        :busy="busy"
        @confirm="confirmVersion"
        @edit="scrollToEditor"
      />

      <ResumeEditor
        ref="editor"
        :record="record"
        :preview-theme="activeTheme"
        :busy="busy"
        :parse-busy="parsing"
        :polish-busy="polishBusy"
        :suggestions="suggestions"
        @save="saveResume"
        @confirm="confirmVersion"
        @parse="parseWithConsent"
        @polish="polishWithConsent"
        @export="exportResume"
      />

      <div v-if="record" class="resume-page-actions">
        <Button v-if="record.version !== record.currentVersion" :disabled="busy" @click="restoreVersion">将此历史版本恢复为新版本</Button>
      </div>
      <section v-if="record" class="resume-page-history">
        <ResumeVersionHistory
          :versions="versions"
          :current-version="record.currentVersion"
          :active-version="record.version"
          @select="selectVersion"
        />
        <ResumeVersionCompare v-if="versions.length > 1" :record-id="record.id" :versions="versions" />
      </section>
    </template>
  </CareerWorkbenchLayout>
</template>

<script setup lang="ts">
import type { LocationQueryRaw } from 'vue-router'
import { computed, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import Button from '@/components/ui/Button.vue'
import Icon from '@/components/ui/Icon.vue'
import Select from '@/components/ui/Select.vue'
import { listDesigns } from '@/api/career/design'
import { prepareResumePrint, readPositiveQuery } from '@/career/resume-export'
import StateView from '@/components/ui/StateView.vue'
import CareerWorkbenchLayout from '@/components/career/CareerWorkbenchLayout.vue'
import ResumeDocxExportButton from '@/components/career/ResumeDocxExportButton.vue'
import { toDocument } from '@/career/resume-document'
import { defaultTheme } from '@/career/templates'
import ResumeEditor from '@/components/career/ResumeEditor.vue'
import ResumeParsePreview from '@/components/career/ResumeParsePreview.vue'
import ResumeVersionCompare from '@/components/career/ResumeVersionCompare.vue'
import ResumeVersionHistory from '@/components/career/ResumeVersionHistory.vue'
import { useResume } from '@/composables/career/useResume'
import type { CareerEdit, CareerRecord, PolishSuggestion, ResumeDesign } from '@/api/career/types'
import { confirmDialog, getApiError, notify } from '@/utils/toast'

const route = useRoute()
const router = useRouter()
const {
  versions,
  loading,
  busy,
  error,
  loadOne,
  loadVersions,
  create,
  save,
  confirm,
  parse,
  polish,
  makeDefault: setDefault,
} = useResume()

const editor = ref<InstanceType<typeof ResumeEditor> | null>(null)
const record = ref<CareerRecord | null>(null)
const suggestions = ref<PolishSuggestion[]>([])
const polishBusy = ref(false)
const parsing = ref(false)
const message = ref('')
const designKey = ref('default')
const exportDesigns = ref<ResumeDesign[]>([])
const designLoading = ref(false)
const designError = ref('')
const preparingPdf = ref(false)
let designSequence = 0
const designOptions = computed(() => [{ value: 'default', label: 'ATS 默认设计（未保存）' }, ...exportDesigns.value.map(item => ({ value: String(item.id), label: item.name }))])
const activeDesign = computed(() => exportDesigns.value.find(item => String(item.id) === designKey.value))
const activeTheme = computed(() => activeDesign.value?.theme ?? defaultTheme('ats-classic'))
async function loadExportDesigns(): Promise<void> {
  const id = record.value?.id
  const sequence = ++designSequence
  if (!id) { exportDesigns.value = []; designKey.value = 'default'; return }
  designLoading.value = true; designError.value = ''
  try {
    const values = await listDesigns(id)
    if (sequence !== designSequence) return
    exportDesigns.value = values
    const requested = readPositiveQuery(route.query.designId, '设计')
    if (requested !== undefined && !values.some(item => item.id === requested)) throw new Error('所选设计不存在或无权访问，请重新选择')
    designKey.value = String(requested ?? (route.query.templateId === 'ats-classic' ? 'default' : (values.find(item => item.isDefault) ?? values[0])?.id ?? 'default'))
  } catch (failure: unknown) {
    if (sequence === designSequence) { exportDesigns.value = []; designError.value = getApiError(failure, '导出设计读取失败，请重试') }
  } finally { if (sequence === designSequence) designLoading.value = false }
}
function selectExportDesign(value: string | number | null): void {
  designKey.value = String(value ?? 'default')
  if (!record.value) return
  const query: LocationQueryRaw = { ...route.query, version: String(record.value.version) }
  if (activeDesign.value) { query.designId = String(activeDesign.value.id); delete query.templateId }
  else { delete query.designId; query.templateId = 'ats-classic' }
  void router.replace({ query })
}
function openTemplateEditor(): void {
  if (!record.value) return
  void router.push({ path: `/career/templates/editor/${record.value.id}`, query: { version: String(record.value.version), ...(activeDesign.value ? { designId: String(activeDesign.value.id) } : { templateId: 'ats-classic' }) } })
}
async function exportPdf(): Promise<void> {
  if (!record.value || preparingPdf.value || designLoading.value || designError.value) return
  preparingPdf.value = true
  try {
    const result = await prepareResumePrint(record.value, activeTheme.value, activeDesign.value?.id ?? null)
    exportDesigns.value = [result.design, ...exportDesigns.value.filter(item => item.id !== result.design.id)]
    window.open(router.resolve(result.location).href, '_blank', 'noopener')
  } catch (failure: unknown) { notify(getApiError(failure, 'PDF 导出准备失败，请重试'), 'error') }
  finally { preparingPdf.value = false }
}
function showVersions(): void { document.querySelector('.resume-page-history')?.scrollIntoView({ behavior: 'smooth', block: 'start' }) }
watch(() => record.value?.id, () => { void loadExportDesigns() })

const resumeId = computed(() => {
  const raw = route.params.resumeId
  const value = Number(Array.isArray(raw) ? raw[0] : raw)
  return Number.isSafeInteger(value) && value > 0 ? value : null
})
const hasSections = computed(() =>
  Boolean(record.value && Object.values(record.value.sections).some(list => list?.length)),
)

function syncRecord(value: CareerRecord | null): void {
  record.value = value
  if (value) {
    void loadVersions(value.id)
  } else {
    versions.value = []
  }
}

async function reload(): Promise<void> {
  suggestions.value = []
  if (!resumeId.value) {
    syncRecord(null)
    return
  }
  const queryVersion = Number(route.query.version)
  const value = await loadOne(
    resumeId.value,
    Number.isSafeInteger(queryVersion) && queryVersion > 0 ? queryVersion : undefined,
  )
  syncRecord(value)
}

async function loadVersion(version: number): Promise<CareerRecord | null> {
  if (!resumeId.value) return null
  const value = await loadOne(resumeId.value, version)
  if (value) record.value = value
  return value
}

async function executeSave(payload: CareerEdit, id?: number): Promise<void> {
  message.value = ''
  const result = id ? await save(id, payload) : await create(payload)
  if (!result) return
  try { await editor.value?.clearDraft() } catch { notify('正式版本已保存；草稿清理失败，请保留并核对草稿', 'warning') }
  syncRecord(result)
  suggestions.value = []
  message.value = result.confirmed ? '已保存并确认简历版本' : '已保存新的待确认版本'
  notify('简历版本已保存', 'success')
  if (!id) await router.replace(`/career/resumes/${result.id}`)
  else if (route.query.version !== undefined) await router.replace({ query: { ...route.query, version: String(result.version) } })
}

async function saveResume(payload: CareerEdit): Promise<void> {
  await executeSave(payload, record.value?.id)
}

async function confirmVersion(): Promise<void> {
  if (!record.value) return
  const value = record.value
  const result = await confirm(value.id, value.version)
  if (!result) return
  syncRecord(result)
  notify('简历版本已确认，可参与智能匹配', 'success')
}

async function parseWithConsent(): Promise<void> {
  if (!record.value) return
  const allowed = await confirmDialog(
    '允许当前已配置的模型处理这份私人简历吗？结果只会保存为新的待确认版本，需要你逐项核对。',
    { title: 'AI 解析简历', confirmText: '允许处理' },
  )
  if (!allowed) return
  parsing.value = true
  message.value = 'AI 正在解析当前版本，可能需要一些时间…'
  const result = await parse(record.value.id, record.value.version)
  parsing.value = false
  if (!result) return
  syncRecord(result)
  message.value = 'AI 已生成待确认版本，请核对原文证据后再确认'
}

async function polishWithConsent(quotes: string[]): Promise<void> {
  if (!record.value || !quotes.length) return
  const allowed = await confirmDialog(
    '允许当前模型对所选原文片段生成润色建议吗？建议不会自动写入，也不会新增经历或指标。',
    { title: 'AI 润色建议', confirmText: '生成建议' },
  )
  if (!allowed) return
  polishBusy.value = true
  suggestions.value = []
  const result = await polish(record.value.id, record.value.version, quotes)
  polishBusy.value = false
  if (!result) return
  suggestions.value = result.suggestions
  message.value = result.suggestions.length ? '润色建议已生成，请在右侧逐条确认' : '模型没有返回可应用的润色建议'
}

async function makeDefault(): Promise<void> {
  if (!record.value) return
  const result = await setDefault(record.value.id)
  if (!result) return
  record.value = result
  notify('已设为默认简历', 'success')
}

async function restoreVersion(): Promise<void> {
  if (!record.value) return
  const value = record.value
  await saveResume({ expectedVersion: value.currentVersion, title: value.title, source: value.source, sections: value.sections })
}

async function selectVersion(version: number): Promise<void> {
  await editor.value?.flushDraft()
  if (editor.value?.draftFailed) return
  const selected = await loadVersion(version)
  if (selected) await router.replace({ query: { ...route.query, version: String(selected.version) } })
}

function scrollToEditor(): void {
  const target = document.querySelector('.resume-editor')
  target?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function exportResume(): void { document.querySelector('[aria-label="文件导出"]')?.scrollIntoView({ behavior: 'smooth', block: 'start' }) }

watch(resumeId, () => void reload())


onBeforeRouteLeave(async () => {
  await editor.value?.flushDraft()
  if (editor.value?.draftFailed) return await confirmDialog('草稿未能保存，离开可能丢失当前修改。确定离开吗？', { title: '草稿保存失败' })
  return true
})

onMounted(() => void reload())
</script>

<style scoped>
.resume-page-export { display: flex; flex-wrap: wrap; align-items: center; gap: var(--kf-space-2); }
.resume-page-export-hint { color: var(--kf-muted-foreground); font-size: var(--kf-fs-caption); }
.resume-page-actions { display: flex; gap: var(--kf-space-2); flex-wrap: wrap; }
.resume-page-error { margin: 0; color: var(--kf-state-error); font-size: var(--kf-fs-body-sm); }
.resume-page-message { margin: 0; color: var(--kf-state-success); font-size: var(--kf-fs-body-sm); }
.resume-page-history { display: grid; grid-template-columns: 280px minmax(0, 1fr); gap: var(--kf-space-4); align-items: start; padding-top: var(--kf-space-4); border-top: 1px solid var(--kf-border); }
@media (max-width: 1000px) {
  .resume-page-history { grid-template-columns: 1fr; }
}
</style>
