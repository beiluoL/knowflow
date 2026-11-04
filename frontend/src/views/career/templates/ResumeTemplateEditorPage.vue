<template>
  <CareerWorkbenchLayout subtitle="左侧选模板与章节，中间 A4 实时预览，右侧调字体与页边距；保存只写设计配置，不改简历内容版本。">
    <template #actions>
      <Button variant="secondary" size="sm" @click="router.push('/career/templates')">
        <Icon name="arrow-left" :size="14" /><span>模板库</span>
      </Button>
      <Button variant="secondary" size="sm" :loading="exporting" :disabled="saving || !ready || !hasResumeDocumentContent(doc, theme)" @click="exportPdf">
        <Icon name="download" :size="14" /><span>导出 PDF</span>
      </Button>
      <ResumeDocxExportButton v-if="ready" :doc="doc" :theme="theme" />
      <Button size="sm" :loading="saving" :disabled="!ready" @click="save">
        <Icon name="save" :size="14" /><span>{{ designId ? '保存设计' : '保存为新设计' }}</span>
      </Button>
    </template>

    <StateView v-if="loading" status="loading" type="card" :cols="3" :rows="3" />
    <ErrorState v-else-if="error" title="编辑器加载失败" :description="error" @retry="load" />

    <div v-else-if="ready" class="ed">
      <aside class="ed-side" aria-label="模板与章节">
        <section class="ed-block">
          <h2 class="ed-block-title">模板</h2>
          <div class="ed-templates">
            <button
              v-for="template in templates"
              :key="template.id"
              type="button"
              class="ed-template"
              :class="{ 'is-active': theme.templateId === template.id }"
              @click="switchTemplate(template.id)"
            >
              <span class="ed-template-name">{{ template.name }}</span>
              <span class="ed-template-meta">{{ template.layoutLabel }} · {{ template.pages }}</span>
            </button>
          </div>
        </section>

        <section class="ed-block">
          <h2 class="ed-block-title">章节</h2>
          <ul class="ed-sections">
            <li v-for="(key, index) in theme.sectionOrder" :key="key" class="ed-section"
              :draggable="!saving && !exporting" @dragstart="draggedSection = key" @dragend="draggedSection = null"
              @dragover.prevent @drop.prevent="dropSection(index)">
              <label class="ed-section-check">
                <Checkbox
                  :model-value="theme.visibleSections.includes(key)"
                  :aria-label="`显示${SECTION_LABELS[key] ?? key}`"
                  @update:model-value="(value: boolean) => toggleSection(key, value)"
                />
                <span class="ed-section-name">{{ SECTION_LABELS[key] ?? key }}</span>
              </label>
              <span class="ed-section-order">
                <button type="button" class="ed-icon-btn" :disabled="index === 0" :aria-label="`上移${SECTION_LABELS[key]}`" @click="moveSection(index, -1)">
                  <Icon name="chevron-up" :size="14" />
                </button>
                <button type="button" class="ed-icon-btn" :disabled="index === theme.sectionOrder.length - 1" :aria-label="`下移${SECTION_LABELS[key]}`" @click="moveSection(index, 1)">
                  <Icon name="chevron-down" :size="14" />
                </button>
              </span>
            </li>
          </ul>
          <p class="kf-text-caption">可拖拽章节排序，也可用上下移按钮；只影响设计，不改简历内容版本。</p>
        </section>
      </aside>

      <main class="ed-main">
        <div class="ed-toolbar">
          <span class="kf-text-caption">{{ theme.pageSize }} · {{ pageCount }} 页（估算）</span>
          <div class="ed-zoom">
            <button type="button" class="ed-icon-btn" aria-label="缩小" :disabled="zoom <= 0.6" @click="zoom = Math.max(0.6, zoom - 0.1)">
              <Icon name="minus" :size="14" />
            </button>
            <span class="ed-zoom-value">{{ Math.round(zoom * 100) }}%</span>
            <button type="button" class="ed-icon-btn" aria-label="放大" :disabled="zoom >= 1.2" @click="zoom = Math.min(1.2, zoom + 0.1)">
              <Icon name="plus" :size="14" />
            </button>
          </div>
        </div>

        <div class="ed-stage" :style="{ height: `${pageHeight * zoom + 24}px` }">
          <div class="ed-scaler" :style="{ transform: `scale(${zoom})` }">
            <div ref="sheetRef" class="ed-sheet" :style="sheetStyle">
              <component :is="template.component" :doc="doc" :theme="theme" />
              <span
                v-for="page in pageBreaks"
                :key="page"
                class="ed-page-break"
                :style="{ top: `${page * pageHeight}px` }"
                aria-hidden="true"
              />
            </div>
          </div>
        </div>
        <p class="kf-text-caption">分页线为按内容高度估算，实际分页以打印结果为准；每个条目设置了避免跨页截断。</p>
      </main>

      <aside class="ed-side ed-side--right" aria-label="排版设置">
        <section class="ed-block">
          <h2 class="ed-block-title">纸张与字体</h2>
          <Select v-model="theme.pageSize" :options="[{ label: 'A4', value: 'A4' }, { label: 'Letter', value: 'Letter' }]" aria-label="纸张尺寸" />
          <div class="ed-segment">
            <button
              v-for="font in FONTS"
              :key="font.value"
              type="button"
              class="ed-segment-btn"
              :class="{ 'is-active': theme.fontFamily === font.value }"
              @click="theme.fontFamily = font.value"
            >{{ font.label }}</button>
          </div>
        </section>

        <section class="ed-block">
          <h2 class="ed-block-title">字号与行距</h2>
          <label class="ed-field">
            <span class="ed-field-label">字号 {{ theme.fontSize }}pt</span>
            <input v-model.number="theme.fontSize" type="range" min="9" max="18" step="0.5" aria-label="字号" />
          </label>
          <label class="ed-field">
            <span class="ed-field-label">行距 {{ theme.lineHeight }}</span>
            <input v-model.number="theme.lineHeight" type="range" min="1.2" max="2.4" step="0.05" aria-label="行距" />
          </label>
          <label class="ed-field">
            <span class="ed-field-label">章节间距 {{ theme.sectionGap }}px</span>
            <input v-model.number="theme.sectionGap" type="range" min="4" max="40" step="1" aria-label="章节间距" />
          </label>
        </section>

        <section class="ed-block">
          <h2 class="ed-block-title">页边距（mm）</h2>
          <div class="ed-margins">
            <label v-for="side in MARGIN_SIDES" :key="side.key" class="ed-margin">
              <span>{{ side.label }}</span>
              <input v-model.number="theme.margin[side.key]" type="number" min="6" max="40" :aria-label="`${side.label}页边距`" />
            </label>
          </div>
        </section>

        <section class="ed-block">
          <h2 class="ed-block-title">主题色</h2>
          <div class="ed-color">
            <input v-model="theme.accentColor" type="color" aria-label="主题色" />
            <input v-model="theme.accentColor" type="text" maxlength="7" aria-label="主题色十六进制" />
          </div>
        </section>

        <section class="ed-block">
          <h2 class="ed-block-title">我的设计</h2>
          <Select v-model="designId" :options="designOptions" placeholder="未保存的设计" aria-label="切换已保存设计" @update:model-value="applyDesign" />
          <div class="ed-actions">
            <Button variant="secondary" size="sm" @click="saveAs">另存为</Button>
            <Button variant="secondary" size="sm" @click="resetTheme">恢复默认</Button>
          </div>
        </section>
      </aside>
    </div>
  </CareerWorkbenchLayout>
</template>

<script setup lang="ts">
import type { LocationQueryRaw } from 'vue-router'
/**
 * 模板编辑器（三栏）。
 *
 * 约束：所有控件都真实作用到渲染；保存只写 interview_career_design，
 * 不产生简历内容版本。导出 PDF 前先确保设计已保存，打印页按设计 ID 读取。
 */
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CareerWorkbenchLayout from '@/components/career/CareerWorkbenchLayout.vue'
import Button from '@/components/ui/Button.vue'
import ResumeDocxExportButton from '@/components/career/ResumeDocxExportButton.vue'
import Icon from '@/components/ui/Icon.vue'
import Select from '@/components/ui/Select.vue'
import Checkbox from '@/components/ui/Checkbox.vue'
import StateView from '@/components/ui/StateView.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import { createDesign, updateDesign } from '@/api/career/design'
import { loadResumeExport, prepareResumePrint } from '@/career/resume-export'
import { RESUME_TEMPLATES, defaultTheme, getTemplate } from '@/career/templates'
import { hasResumeDocumentContent, SECTION_LABELS, toDocument } from '@/career/resume-document'
import type { CareerRecord, ResumeDesign, ResumeTheme } from '@/api/career/types'
import { getApiError, notify } from '@/utils/toast'

const route = useRoute()
const router = useRouter()

const FONTS = [
  { value: 'sans' as const, label: '黑体' },
  { value: 'serif' as const, label: '宋体' },
  { value: 'mono' as const, label: '等宽' },
]
const MARGIN_SIDES = [
  { key: 'top' as const, label: '上' },
  { key: 'right' as const, label: '右' },
  { key: 'bottom' as const, label: '下' },
  { key: 'left' as const, label: '左' },
]

const loading = ref(true)
const saving = ref(false)
const exporting = ref(false)
const error = ref('')
const resume = ref<CareerRecord | null>(null)
const designs = ref<ResumeDesign[]>([])
const designId = ref<number | null>(null)
const sheetRef = ref<HTMLElement | null>(null)
const zoom = ref(0.85)
const pageCount = ref(1)
const draggedSection = ref<string | null>(null)

const theme = reactive<ResumeTheme>(defaultTheme('ats-classic'))

const templates = RESUME_TEMPLATES
const ready = computed(() => !!resume.value)
const doc = computed(() => toDocument(resume.value!))
const template = computed(() => getTemplate(theme.templateId))
const designOptions = computed(() =>
  designs.value.map((item) => ({ label: `${item.name}${item.isDefault ? ' · 默认' : ''}`, value: item.id })),
)
const pageBreaks = computed(() => {
  const breaks: number[] = []
  for (let i = 1; i < pageCount.value; i++) breaks.push(i)
  return breaks
})
/** 页边距直接使用 mm，浏览器按物理单位换算，预览与打印保持一致。 */
const pageHeight = computed(() => theme.pageSize === 'Letter' ? 1056 : 1123)
const sheetStyle = computed(() => ({
  width: theme.pageSize === 'Letter' ? '215.9mm' : '210mm',
  minHeight: theme.pageSize === 'Letter' ? '279.4mm' : '297mm',
  paddingTop: `${theme.margin.top}mm`,
  paddingRight: `${theme.margin.right}mm`,
  paddingBottom: `${theme.margin.bottom}mm`,
  paddingLeft: `${theme.margin.left}mm`,
}))

function assignTheme(next: ResumeTheme): void {
  theme.templateId = next.templateId
  theme.pageSize = next.pageSize
  theme.fontFamily = next.fontFamily
  theme.fontSize = next.fontSize
  theme.lineHeight = next.lineHeight
  theme.accentColor = next.accentColor
  theme.sectionGap = next.sectionGap
  theme.margin = { ...next.margin }
  theme.sectionOrder = [...next.sectionOrder]
  theme.visibleSections = [...next.visibleSections]
}

function switchTemplate(id: string): void {
  const defaults = defaultTheme(id)
  // 保留用户已调的字体与边距，只切换版式与强调色
  theme.templateId = id
  theme.accentColor = defaults.accentColor
  theme.lineHeight = defaults.lineHeight
  theme.fontSize = defaults.fontSize
}

function toggleSection(key: string, visible: boolean): void {
  const set = new Set(theme.visibleSections)
  if (visible) set.add(key)
  else set.delete(key)
  theme.visibleSections = theme.sectionOrder.filter((item) => set.has(item))
}

function moveSection(index: number, delta: number): void {
  const next = [...theme.sectionOrder]
  const target = index + delta
  if (target < 0 || target >= next.length) return
  ;[next[index], next[target]] = [next[target], next[index]]
  theme.sectionOrder = next
}

/** 仅接受本页已存在的章节标识，外部拖入的数据不参与排序。 */
function dropSection(target: number): void {
  if (saving.value || exporting.value || draggedSection.value === null) return
  const from = theme.sectionOrder.indexOf(draggedSection.value)
  draggedSection.value = null
  if (from < 0 || from === target) return
  const order = [...theme.sectionOrder]
  const [item] = order.splice(from, 1)
  order.splice(target, 0, item)
  theme.sectionOrder = order
}

function resetTheme(): void {
  assignTheme(defaultTheme(theme.templateId))
}

function applyDesign(id: number | null): void {
  const design = designs.value.find((item) => item.id === id)
  if (design) assignTheme(design.theme)
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  const resumeId = Number(route.params.resumeId)
  if (!resumeId) {
    error.value = '缺少简历标识'
    loading.value = false
    return
  }
  try {
    const selected = await loadResumeExport(resumeId, route.query)
    resume.value = selected.record
    designs.value = selected.designs
    designId.value = selected.design?.id ?? null
    assignTheme(selected.theme)
  } catch (failure) {
    error.value = getApiError(failure, '编辑器加载失败')
  } finally {
    loading.value = false
    await nextTick()
    measure()
  }
}

function measure(): void {
  const element = sheetRef.value
  if (!element) return
  const height = element.scrollHeight
  pageCount.value = Math.max(1, Math.ceil(height / pageHeight.value))
}

function keepDesignLink(id: number): void {
  if (!resume.value) return
  const query: LocationQueryRaw = { ...route.query, version: String(resume.value.version), designId: String(id) }
  delete query.templateId
  void router.replace({ query })
}

async function save(): Promise<boolean> {
  if (!resume.value) return false
  saving.value = true
  try {
    if (designId.value) {
      const updated = await updateDesign(resume.value.id, designId.value, { ...theme })
      designs.value = designs.value.map((item) => (item.id === updated.id ? updated : item))
      notify('设计已保存', 'success')
    } else {
      const created = await createDesign(resume.value.id, { ...theme })
      designId.value = created.id
      designs.value = [created, ...designs.value]
      notify('已保存为新设计', 'success')
    }
    keepDesignLink(designId.value!)
    return true
  } catch (failure) {
    notify(getApiError(failure, '保存失败'), 'error')
    return false
  } finally {
    saving.value = false
  }
}

async function saveAs(): Promise<void> {
  if (!resume.value) return
  saving.value = true
  try {
    const created = await createDesign(resume.value.id, { ...theme }, `${template.value.name} 副本`)
    designs.value = [created, ...designs.value]
    designId.value = created.id
    keepDesignLink(created.id)
    notify('已另存为新设计', 'success')
  } catch (failure) {
    notify(getApiError(failure, '另存为失败'), 'error')
  } finally {
    saving.value = false
  }
}

async function exportPdf(): Promise<void> {
  if (!resume.value) return
  exporting.value = true
  try {
    const result = await prepareResumePrint(resume.value, theme, designId.value)
    designs.value = [result.design, ...designs.value.filter(item => item.id !== result.design.id)]
    const url = router.resolve(result.location).href
    window.open(url, '_blank', 'noopener')
  } catch (failure: unknown) {
    notify(getApiError(failure, '设计保存或 PDF 导出准备失败，请重试'), 'error')
  } finally {
    exporting.value = false
  }
}

watch(
  () => [theme.fontSize, theme.fontFamily, theme.lineHeight, theme.sectionGap, theme.sectionOrder.length, theme.margin.top, theme.margin.bottom],
  async () => {
    await nextTick()
    measure()
  },
)

onMounted(load)
</script>

<style scoped>
.ed {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr) 240px;
  gap: var(--kf-space-4);
  align-items: start;
}
.ed-side {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-4);
  padding: var(--kf-space-3);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  background: var(--kf-surface-1);
  position: sticky;
  top: 8px;
}
.ed-block {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-2);
}
.ed-block-title {
  margin: 0;
  font-size: var(--kf-fs-caption);
  font-weight: 500;
  color: var(--kf-muted-foreground);
  letter-spacing: 0.04em;
}
.ed-templates {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.ed-template {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 6px 8px;
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-sm);
  background: var(--kf-surface-1);
  text-align: left;
  cursor: pointer;
}
.ed-template.is-active {
  border-color: var(--kf-primary);
  background: var(--kf-primary-subtle);
}
.ed-template-name {
  font-size: var(--kf-fs-body-sm);
  color: var(--kf-foreground);
}
.ed-template-meta {
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.ed-sections {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.ed-section {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 4px;
  padding: 4px 2px;
}
.ed-section-check {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.ed-section-name {
  font-size: var(--kf-fs-body-sm);
  color: var(--kf-foreground);
}
.ed-section-order {
  display: inline-flex;
  gap: 2px;
}
.ed-icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-xs, 4px);
  background: var(--kf-surface-1);
  color: var(--kf-muted-foreground);
  cursor: pointer;
}
.ed-icon-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.ed-main {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-2);
  min-width: 0;
}
.ed-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kf-space-3);
  padding: var(--kf-space-2) var(--kf-space-3);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-sm);
  background: var(--kf-surface-1);
}
.ed-zoom {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.ed-zoom-value {
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
  min-width: 36px;
  text-align: center;
}
.ed-stage {
  position: relative;
  overflow: auto;
  padding: 12px 0;
  background: var(--kf-surface-2);
  border-radius: var(--kf-radius-md);
}
.ed-scaler {
  width: 794px;
  margin: 0 auto;
  transform-origin: top center;
  transition: transform var(--kf-motion-fast, 120ms) ease;
}
.ed-sheet {
  position: relative;
  width: 794px;
  min-height: 1123px;
  padding: 60px 62px;
  box-sizing: border-box;
  background: var(--kf-surface-1);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}
.ed-page-break {
  position: absolute;
  left: 0;
  right: 0;
  border-top: 1px dashed var(--kf-border);
  pointer-events: none;
}
.ed-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.ed-field-label {
  font-size: var(--kf-fs-caption);
  color: var(--kf-foreground);
}
.ed-field input[type='range'] {
  width: 100%;
}
.ed-margins {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
}
.ed-margin {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: var(--kf-fs-caption);
  color: var(--kf-foreground);
}
.ed-margin input {
  width: 100%;
  min-height: 32px;
  padding: 0 8px;
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-sm);
  background: var(--kf-surface-2);
  color: var(--kf-foreground);
  font-size: var(--kf-fs-body-sm);
}
.ed-color {
  display: flex;
  align-items: center;
  gap: 8px;
}
.ed-color input[type='color'] {
  width: 36px;
  height: 32px;
  padding: 0;
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-sm);
  background: transparent;
}
.ed-color input[type='text'] {
  flex: 1;
  min-height: 32px;
  padding: 0 8px;
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-sm);
  background: var(--kf-surface-2);
  color: var(--kf-foreground);
  font-family: var(--kf-font-mono, monospace);
  font-size: var(--kf-fs-caption);
}
.ed-segment {
  display: flex;
  gap: 4px;
}
.ed-segment-btn {
  flex: 1;
  min-height: 30px;
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-sm);
  background: var(--kf-surface-1);
  color: var(--kf-foreground);
  font-size: var(--kf-fs-caption);
  cursor: pointer;
}
.ed-segment-btn.is-active {
  border-color: var(--kf-primary);
  background: var(--kf-primary-subtle);
  color: var(--kf-primary);
}
.ed-actions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
@media (max-width: 1280px) {
  .ed {
    grid-template-columns: 200px minmax(0, 1fr);
  }
  .ed-side--right {
    grid-column: 1 / -1;
    position: static;
  }
}
@media (max-width: 1024px) {
  .ed {
    grid-template-columns: minmax(0, 1fr);
  }
  .ed-side {
    position: static;
  }
  .ed-scaler {
    transform: scale(0.62) !important;
  }
}
</style>
