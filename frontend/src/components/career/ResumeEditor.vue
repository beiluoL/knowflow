<template>
  <section class="resume-editor" :class="{ 'is-preview': showPreview }" aria-label="简历编辑器">
    <header class="resume-editor-head">
      <div class="resume-editor-title">
        <Input :disabled="busy || parseBusy" v-model="title" :maxlength="160" placeholder="简历名称，例如「Java 全栈 · 2026」" aria-label="简历名称" />
        <span v-if="record" class="resume-editor-version">
          v{{ record.version }} · {{ record.confirmed ? '已确认' : '待确认' }}
          <template v-if="record.version !== record.currentVersion"> · 历史版本</template>
        </span>
        <span v-else class="resume-editor-version">新简历</span>
      </div>
      <div class="resume-editor-head-actions">
        <Button variant="secondary" size="sm" @click="showPreview = !showPreview">{{ showPreview ? '返回内容编辑' : '实时 A4 预览' }}</Button>
        <Button variant="secondary" size="sm" :disabled="busy || parseBusy || historyIndex <= 0" @click="undo">撤销</Button>
        <Button variant="secondary" size="sm" :disabled="busy || parseBusy || historyIndex >= history.length - 1" @click="redo">重做</Button>
        <Button variant="secondary" size="sm" :disabled="busy" @click="emit('export')">
          <Icon name="download" :size="14" /><span>导出文件</span>
        </Button>
        <Button
          v-if="record"
          variant="secondary"
          size="sm"
          :disabled="busy || dirty || parseBusy"
          :loading="parseBusy"
          @click="emit('parse')"
        >
          <Icon v-if="!parseBusy" name="sparkles" :size="14" /><span>{{ parseBusy ? 'AI 解析中…' : 'AI 解析建议' }}</span>
        </Button>
        <Button
          v-if="record"
          variant="secondary"
          size="sm"
          :disabled="busy || dirty || record.confirmed"
          @click="emit('confirm')"
        >
          <Icon name="check" :size="14" /><span>{{ record.confirmed ? '已确认' : '确认此版本' }}</span>
        </Button>
        <Button size="sm" :disabled="busy || !canSave" :loading="busy" @click="save">
          <Icon v-if="!busy" name="save" :size="14" />
          <span>{{ record ? '保存为新版本' : '保存简历' }}</span>
        </Button>
      </div>
    </header>

    <div class="resume-editor-draft" role="status">
      <span>{{ draft.status.value }}</span>
      <template v-if="draft.recovery.value"><Button size="sm" :disabled="busy || parseBusy" @click="draft.restore">恢复草稿</Button><Button size="sm" variant="secondary" :disabled="busy || parseBusy" @click="draft.discard">丢弃草稿</Button></template>
      <Button v-else-if="draft.failed.value" variant="secondary" size="sm" :disabled="busy || parseBusy" @click="draft.retry">重试保存草稿</Button>
    </div>
    <p v-if="source === needsTextSource" class="resume-editor-error" role="alert">原文件已安全保留，但尚无可用正文。请先粘贴并校对正文，再保存和确认。</p>
    <p v-if="validationError && dirty" class="resume-editor-error" role="status">{{ validationError }}</p>
    <p v-if="error" class="resume-editor-error" role="alert">{{ error }}</p>
    <p v-if="record && dirty" class="resume-editor-hint" role="status">
      当前有未保存修改；确认操作只针对已保存版本，请先保存为新版本。
    </p>

    <div v-if="showPreview" class="resume-editor-preview" aria-label="当前草稿实时A4预览">
      <component :is="getTemplate(previewTheme?.templateId ?? 'ats-classic').component" :doc="previewDoc" :theme="previewTheme ?? defaultTheme('ats-classic')" />
    </div>
    <fieldset class="resume-editor-fields" :disabled="busy || parseBusy">
    <div class="resume-editor-body">
      <nav class="resume-editor-nav" aria-label="简历章节">
        <button
          type="button"
          class="resume-editor-nav-item"
          :class="{ 'is-active': activeSection === 'source' }"
          @click="activeSection = 'source'"
        >
          <Icon name="file-text" :size="15" />
          <span>简历原文</span>
        </button>
        <button
          v-for="section in RESUME_SECTIONS"
          :key="section.key"
          type="button"
          class="resume-editor-nav-item"
          :class="{ 'is-active': activeSection === section.key }"
          @click="activeSection = section.key"
        >
          <span>{{ section.label }}</span>
          <span class="resume-editor-count">{{ (sections[section.key]?.length ?? 0) }}</span>
        </button>
      </nav>

      <div class="resume-editor-center">
        <section v-if="activeSection === 'source'" class="resume-editor-panel">
          <div class="resume-editor-panel-head">
            <h3 class="kf-text-card-title">简历原文</h3>
            <span class="kf-text-caption">原文仅本人可见，不会进入公共知识库</span>
          </div>
          <p class="kf-text-caption">
            结构化条目必须引用这里的连续原文片段；AI 解析或手工编辑都不会凭空创造工作经历。
          </p>
          <Textarea v-model="source" :rows="18" :maxlength="50000" placeholder="粘贴简历原文，或先上传 PDF/DOC/DOCX/TXT 自动提取。" aria-label="简历原文" />
          <p class="resume-editor-meta">{{ source.length }} / 50000 字</p>
        </section>

        <section v-else class="resume-editor-panel">
          <div class="resume-editor-panel-head">
            <div>
              <h3 class="kf-text-card-title">{{ activeDefinition.label }}</h3>
              <p class="kf-text-caption">{{ activeDefinition.hint }}</p>
            </div>
            <Button variant="secondary" size="sm" :disabled="busy" @click="addClaim">
              <Icon name="plus" :size="14" /><span>添加一项</span>
            </Button>
          </div>

          <p v-if="!claims.length" class="resume-editor-empty">
            此章节还没有条目。可以手动添加，或先在「简历原文」粘贴内容后使用 AI 解析建议。
          </p>

          <article v-for="(claim, index) in claims" :key="claimKey(index)" class="resume-claim" @dragover.prevent @drop.prevent="dropClaim(index)">
            <div class="resume-claim-head">
              <span class="resume-claim-index" :draggable="!busy && !parseBusy" title="拖动调整条目顺序，也可使用上下移按钮" @dragstart="startDrag(index, $event)" @dragend="draggedIndex = null">{{ activeDefinition.label }} {{ index + 1 }}</span>
              <div class="resume-claim-actions">
                <button type="button" class="resume-icon-button" aria-label="上移" :disabled="index === 0" @click="moveClaim(index, -1)">
                  <Icon name="chevron-up" :size="14" />
                </button>
                <button type="button" class="resume-icon-button" aria-label="下移" :disabled="index === claims.length - 1" @click="moveClaim(index, 1)">
                  <Icon name="chevron-down" :size="14" />
                </button>
                <button type="button" class="resume-icon-button is-danger" aria-label="删除此项" @click="removeClaim(index)">
                  <Icon name="trash-2" :size="14" />
                </button>
              </div>
            </div>

            <div v-if="activeFields.length" class="resume-claim-fields">
              <label v-for="field in activeFields" :key="field.key" class="resume-claim-field">
                <span>{{ field.label }}</span>
                <Input
                  :model-value="fieldValue(claim, field.key)"
                  :placeholder="field.placeholder"
                  :maxlength="200"
                  :error="fieldError(claim, field.key)"
                  :aria-label="`${activeDefinition.label}${field.label}`"
                  @update:model-value="value => setField(claim, field.key, value)"
                />
              </label>
            </div>

            <div class="resume-format-tools" role="group" aria-label="纯文本格式">
              <Button variant="secondary" size="sm" @click="formatClaim(claim, 'bullet')">项目符号</Button>
              <Button variant="secondary" size="sm" @click="formatClaim(claim, 'plain')">移除项目符号</Button>
              <Button variant="secondary" size="sm" @click="formatClaim(claim, 'newline')">末尾换行</Button>
              <span class="kf-text-caption">纯文本格式，保留原文证据</span>
            </div>
            <label class="resume-claim-field">
              <span>内容摘要 / 完整表述</span>
              <Textarea
                v-model="claim.value"
                :rows="2"
                :maxlength="500"
                show-count
                :error="!claim.value.trim() ? '请填写内容' : claim.value.length > 500 ? '内容不能超过500字' : ''"
                :placeholder="valuePlaceholder"
                :aria-label="`${activeDefinition.label}内容`"
              />
            </label>

            <div class="resume-claim-quote">
              <label class="resume-claim-field">
                <span>原文证据（必须逐字来自上方原文）</span>
                <Input v-model="claim.quote" :maxlength="2000" placeholder="复制原文中的连续片段" aria-label="原文证据" />
              </label>
              <label class="resume-claim-field resume-claim-picker">
                <span>从原文选择</span>
                <Select
                  :model-value="claim.quote"
                  :options="quoteOptions"
                  placeholder="选择原文片段"
                  aria-label="从原文选择证据"
                  :disabled="!quoteOptions.length"
                  @update:model-value="value => pickQuote(claim, value)"
                />
              </label>
            </div>

            <div class="resume-claim-foot">
              <span v-if="claim.quote && !source.includes(claim.quote)" class="resume-claim-invalid">
                该证据不在当前原文中
              </span>
              <button
                type="button"
                class="resume-polish-button"
                :disabled="polishBusy || !claim.quote || !source.includes(claim.quote)"
                @click="emit('polish', [claim.quote])"
              >
                <Icon name="sparkles" :size="13" />
                <span>AI 润色建议</span>
              </button>
            </div>
          </article>
        </section>
      </div>

      <aside class="resume-editor-ai" aria-label="AI 建议">
        <div class="resume-editor-ai-head">
          <Icon name="sparkles" :size="16" />
          <h3 class="kf-text-card-title">AI 建议</h3>
        </div>
        <p class="kf-text-caption">
          润色只改表达，不新增经历、项目、指标或公司信息；应用前需要你逐条确认。
        </p>
        <p v-if="polishBusy" class="resume-editor-ai-state">正在生成原文绑定的润色建议…</p>
        <p v-else-if="!suggestions.length" class="resume-editor-ai-state">
          在左侧条目点击「AI 润色建议」，结果会显示在这里。
        </p>
        <article v-for="(suggestion, index) in suggestions" :key="`${suggestion.original}-${index}`" class="resume-suggestion">
          <p class="resume-suggestion-label">原文</p>
          <blockquote>{{ suggestion.original }}</blockquote>
          <p class="resume-suggestion-label">建议</p>
          <p class="resume-suggestion-text">{{ suggestion.suggestion }}</p>
          <p class="resume-suggestion-reason">{{ suggestion.reason }}</p>
          <Button variant="secondary" size="sm" :disabled="busy" @click="applySuggestion(suggestion)">
            应用到对应条目
          </Button>
        </article>
      </aside>
    </div>
    </fieldset>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { toDocument } from '@/career/resume-document'
import { defaultTheme, getTemplate } from '@/career/templates'
import { useResumeDraft } from '@/composables/career/useResumeDraft'
import Button from '@/components/ui/Button.vue'
import Icon from '@/components/ui/Icon.vue'
import Input from '@/components/ui/Input.vue'
import Select from '@/components/ui/Select.vue'
import Textarea from '@/components/ui/Textarea.vue'
import type { CareerClaim, CareerEdit, CareerRecord, PolishSuggestion, ResumeTheme } from '@/api/career/types'
import { RESUME_SECTIONS, clonePlain } from './career-labels'

interface FieldDefinition {
  key: string
  label: string
  placeholder: string
}

const SECTION_FIELDS: Record<string, FieldDefinition[]> = {
  basics: [
    { key: 'name', label: '姓名', placeholder: '本人姓名' },
    { key: 'years', label: '工作年限', placeholder: '例如 6 年' },
    { key: 'phone', label: '手机', placeholder: '仅本人可见' },
    { key: 'email', label: '邮箱', placeholder: '联系邮箱' },
  ],
  intention: [
    { key: 'targetRole', label: '目标岗位', placeholder: '例如 Java + AI 全栈工程师' },
    { key: 'city', label: '目标城市', placeholder: '例如 深圳' },
    { key: 'workMode', label: '工作方式', placeholder: '现场 / 远程 / 混合' },
  ],
  education: [
    { key: 'school', label: '学校', placeholder: '学校名称' },
    { key: 'major', label: '专业', placeholder: '专业名称' },
    { key: 'degree', label: '学历', placeholder: '本科 / 硕士' },
    { key: 'period', label: '时间', placeholder: '例如 2016.09-2020.06' },
  ],
  experience: [
    { key: 'company', label: '公司', placeholder: '公司名称' },
    { key: 'role', label: '职位', placeholder: '岗位名称' },
    { key: 'period', label: '时间', placeholder: '例如 2022.03-至今' },
    { key: 'techStack', label: '技术栈', placeholder: '例如 Java, Spring Boot, MySQL' },
  ],
  projects: [
    { key: 'project', label: '项目', placeholder: '项目名称' },
    { key: 'role', label: '角色', placeholder: '负责的模块或角色' },
    { key: 'period', label: '时间', placeholder: '例如 2024.03-2024.09' },
    { key: 'techStack', label: '技术栈', placeholder: '真实使用的技术' },
    { key: 'outcome', label: '可验证结果', placeholder: '仅填写原文中存在的指标' },
  ],
  skills: [
    { key: 'proficiency', label: '熟练度', placeholder: '例如 熟练 / 了解' },
  ],
  certificates: [
    { key: 'issuer', label: '颁发机构', placeholder: '机构名称' },
    { key: 'year', label: '年份', placeholder: '例如 2025' },
  ],
  custom: [{ key: 'heading', label: '章节标题', placeholder: '例如 开源贡献' }],
  selfIntro: [],
}

const props = defineProps<{
  previewTheme?: ResumeTheme
  record: CareerRecord | null
  busy?: boolean
  parseBusy?: boolean
  polishBusy?: boolean
  suggestions: PolishSuggestion[]
}>()

const emit = defineEmits<{
  save: [payload: CareerEdit]
  confirm: []
  parse: []
  polish: [quotes: string[]]
  export: []
}>()

const needsTextSource = '[原文件未提取到可用正文，请手动粘贴简历文本后再确认]'
const showPreview = ref(false)
const previewDoc = computed(() => toDocument({ id: props.record?.id ?? 0, kind: 'RESUME', currentVersion: props.record?.currentVersion ?? 0, version: props.record?.version ?? 0, isDefault: false, confirmed: false, ...payload() }))
const draggedIndex = ref<number | null>(null)
const activeSection = ref<string>('source')
const title = ref('')
const source = ref('')
const sections = ref<Record<string, CareerClaim[]>>({})
const error = ref('')
function payload(): CareerEdit { return { expectedVersion: props.record?.currentVersion, title: title.value, source: source.value, sections: clonePlain(sections.value) } }
function applyPayload(value: CareerEdit): void { title.value = value.title; source.value = value.source; sections.value = clonePlain(value.sections) }
const draft = useResumeDraft(payload, applyPayload)
const history = ref<string[]>([])
const historyIndex = ref(0)
let historyApplying = false
let syncing = false
function undo(): void { if (historyIndex.value > 0) applyHistory(historyIndex.value - 1) }
function redo(): void { if (historyIndex.value < history.value.length - 1) applyHistory(historyIndex.value + 1) }
function applyHistory(index: number): void {
  historyApplying = true
  historyIndex.value = index
  applyPayload(JSON.parse(history.value[index]) as CareerEdit)
  historyApplying = false
}
watch(() => JSON.stringify(payload()), value => {
  if (syncing) return
  if (!historyApplying && history.value[historyIndex.value] !== value) {
    history.value = history.value.slice(0, historyIndex.value + 1)
    history.value.push(value)
    if (history.value.length > 100) history.value.shift()
    historyIndex.value = history.value.length - 1
  }
  draft.schedule()
}, { flush: 'sync' })
defineExpose({ openPreview: () => { showPreview.value = true }, saveDraft: draft.flush, clearDraft: draft.clear, flushDraft: draft.flush, draftFailed: draft.failed })

const activeDefinition = computed(() =>
  RESUME_SECTIONS.find(section => section.key === activeSection.value)
  ?? { key: 'source', label: '简历原文', hint: '' },
)
const claims = computed<CareerClaim[]>(() => sections.value[activeSection.value] ?? [])
const activeFields = computed(() => SECTION_FIELDS[activeSection.value] ?? [])
const valuePlaceholder = computed(() => {
  if (activeSection.value === 'skills') return '技能名称，例如 Java、Spring Boot、RAG'
  if (activeSection.value === 'projects') return '项目背景、你的职责与可验证结果'
  if (activeSection.value === 'experience') return '职责范围与真实成果，不编造指标'
  return '从原文提炼的完整表述'
})
const quoteOptions = computed(() => {
  const values = source.value
    .split(/[\n。！？；]/)
    .map(item => item.trim())
    .filter(item => item.length >= 2 && item.length <= 200)
  return Array.from(new Set(values)).slice(0, 80).map(item => ({ value: item, label: item }))
})

function cloneSections(value: Record<string, CareerClaim[]> | undefined): Record<string, CareerClaim[]> {
  return clonePlain(value ?? {})
}

function syncFromRecord(): void {
  syncing = true
  title.value = props.record?.title ?? ''
  source.value = props.record?.source ?? ''
  sections.value = cloneSections(props.record?.sections)
  activeSection.value = props.record ? 'basics' : 'source'
  error.value = ''
  history.value = [JSON.stringify(payload())]; historyIndex.value = 0; syncing = false
  void draft.initialize(props.record?.id ?? 0, props.record?.version ?? 0)
}

watch(() => props.record, syncFromRecord, { immediate: true })

const dirty = computed(() => {
  if (!props.record) {
    return Boolean(title.value.trim() || source.value.trim() || Object.values(sections.value).some(list => list.length))
  }
  return props.record.title !== title.value
    || props.record.source !== source.value
    || JSON.stringify(props.record.sections ?? {}) !== JSON.stringify(sections.value)
})

const validationError = computed(() => {
  if (!title.value.trim()) return '请填写简历名称'
  if (title.value.length > 160) return '简历名称不能超过160字'
  if (source.value.length > 50000) return '简历原文不能超过50000字'
  if (!source.value.trim() || source.value === needsTextSource) return '请填写并校对简历原文'
  for (const section of RESUME_SECTIONS) {
    for (const claim of sections.value[section.key] ?? []) {
      if (claim.value.length > 500) return `${section.label}的内容不能超过500字`
      if (claim.quote.length > 2000) return `${section.label}的证据不能超过2000字`
      for (const field of SECTION_FIELDS[section.key] ?? []) {
        const issue = fieldError(claim, field.key)
        if (issue) return `${section.label}：${issue}`
      }
      if (!claim.value.trim()) return `${section.label}存在内容为空的条目`
      if (!claim.quote.trim()) return `${section.label}存在缺少原文证据的条目`
      if (!source.value.includes(claim.quote)) return `${section.label}的原文证据不在当前原文中`
    }
  }
  return ''
})
const canSave = computed(() => !props.busy && !props.parseBusy && !validationError.value)

function fieldValue(claim: CareerClaim, key: string): string {
  return claim.fields?.[key] ?? ''
}

// 字段只在用户输入时写入；读取不创建空字段，避免预览触发虚假脏状态。
function fieldError(claim: CareerClaim, key: string): string {
  const value = fieldValue(claim, key).trim();
  if (value.length > 200) return '字段不能超过200字';
  if (key === 'email' && value && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) return '请输入有效邮箱';
  if (key === 'phone' && value && !/^\+?[\d\s().-]{5,30}$/.test(value)) return '请输入有效联系电话';
  return '';
}

// 受控格式只修改字符串，不引入 HTML，也不创建正式简历版本。
function formatClaim(claim: CareerClaim, mode: 'bullet' | 'plain' | 'newline'): void {
  if (props.busy || props.parseBusy) return;
  const value = mode === 'newline' ? `${claim.value}\n` : claim.value.split('\n').map(line => {
    const plain = line.replace(/^\s*[-•]\s+/, '');
    return mode === 'bullet' && plain.trim() ? `• ${plain}` : plain;
  }).join('\n');
  if (value.length > 500) { error.value = '格式调整后超过500字，请先精简内容'; return; }
  claim.value = value;
  error.value = '';
}

function setField(claim: CareerClaim, key: string, value: string | number | null): void {
  claim.fields ??= {}
  claim.fields[key] = String(value ?? '')
}

function pickQuote(claim: CareerClaim, value: string | number | null): void {
  claim.quote = String(value ?? '')
}

function addClaim(): void {
  const key = activeSection.value
  if (key === 'source') return
  ;(sections.value[key] ??= []).push({ value: '', quote: '', fields: {} })
}

function removeClaim(index: number): void {
  claims.value.splice(index, 1)
}

// 仅接收本编辑器内的拖动索引，不读取外部拖放载荷。
function startDrag(index: number, event: DragEvent): void {
  if (props.busy || props.parseBusy) { event.preventDefault(); return; }
  draggedIndex.value = index;
  if (event.dataTransfer) { event.dataTransfer.effectAllowed = 'move'; event.dataTransfer.setData('text/plain', 'resume-claim'); }
}
function dropClaim(target: number): void {
  const index = draggedIndex.value;
  draggedIndex.value = null;
  if (props.busy || props.parseBusy || index === null || index === target) return;
  moveClaim(index, target - index);
}
watch(activeSection, () => { draggedIndex.value = null; });

function moveClaim(index: number, direction: number): void {
  const target = index + direction
  if (target < 0 || target >= claims.value.length) return
  const list = claims.value
  const [item] = list.splice(index, 1)
  list.splice(target, 0, item)
}

function claimKey(index: number): string {
  return `${activeSection.value}-${index}`
}

function save(): void {
  if (props.busy || props.parseBusy) return
  error.value = validationError.value
  if (error.value) return
  emit('save', {
    expectedVersion: props.record?.currentVersion,
    title: title.value.trim(),
    source: source.value,
    sections: clonePlain(sections.value),
  })
}

function applySuggestion(suggestion: PolishSuggestion): void {
  let applied = false
  for (const section of RESUME_SECTIONS) {
    for (const claim of sections.value[section.key] ?? []) {
      if (claim.quote === suggestion.original) {
        claim.value = suggestion.suggestion
        applied = true
        break
      }
    }
    if (applied) break
  }
  if (!applied) error.value = '没有找到与该建议绑定的原文条目，建议未应用'
}
</script>

<style scoped>
.resume-editor { display: flex; flex-direction: column; gap: var(--kf-space-4); }
.resume-editor-head { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--kf-space-3); flex-wrap: wrap; }
.resume-editor-title { display: flex; align-items: center; gap: var(--kf-space-2); flex-wrap: wrap; min-width: 0; flex: 1; }
.resume-editor-title > :first-child { max-width: 420px; }
.resume-editor-version { font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); white-space: nowrap; }
.resume-editor-head-actions { display: flex; align-items: center; gap: var(--kf-space-2); flex-wrap: wrap; }
.resume-editor-preview { overflow-x: auto; padding: var(--kf-space-3); background: var(--kf-surface-2); }
.resume-format-tools { display: flex; flex-wrap: wrap; align-items: center; gap: var(--kf-space-2); }
.resume-editor-draft { display: flex; align-items: center; gap: var(--kf-space-2); flex-wrap: wrap; font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
.resume-editor-error { margin: 0; color: var(--kf-state-error); font-size: var(--kf-fs-body-sm); }
.resume-editor-hint { margin: 0; color: var(--kf-state-warning); font-size: var(--kf-fs-body-sm); }
.resume-editor-fields { min-width: 0; border: 0; padding: 0; margin: 0; }
.resume-editor-body { display: grid; grid-template-columns: 180px minmax(0, 1fr) 300px; gap: var(--kf-space-4); align-items: start; }
.resume-editor-nav { display: flex; flex-direction: column; gap: 2px; position: sticky; top: var(--kf-space-4); }
.resume-editor-nav-item { display: flex; align-items: center; justify-content: space-between; gap: var(--kf-space-2); min-height: 36px; padding: 0 var(--kf-space-2); border: 0; border-radius: var(--kf-radius-sm); background: transparent; color: var(--kf-muted-foreground); font-size: var(--kf-fs-body-sm); text-align: left; cursor: pointer; }
.resume-editor-nav-item:first-child { justify-content: flex-start; }
.resume-editor-nav-item:hover { background: var(--kf-surface-2); color: var(--kf-foreground); }
.resume-editor-nav-item.is-active { background: var(--kf-primary-subtle); color: var(--kf-primary); font-weight: 500; }
.resume-editor-nav-item:focus-visible { outline: 2px solid var(--kf-primary); outline-offset: 1px; }
.resume-editor-count { font-size: var(--kf-fs-caption); }
.resume-editor-center { min-width: 0; }
.resume-editor-panel { display: flex; flex-direction: column; gap: var(--kf-space-3); padding: var(--kf-space-4); border: 1px solid var(--kf-border); border-radius: var(--kf-radius-md); background: var(--kf-surface-1); }
.resume-editor-panel-head { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--kf-space-3); flex-wrap: wrap; }
.resume-editor-panel-head h3 { margin: 0 0 var(--kf-space-1); }
.resume-editor-panel-head p { margin: 0; }
.resume-editor-meta { margin: 0; font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); text-align: right; }
.resume-editor-empty { margin: 0; padding: var(--kf-space-6); border: 1px dashed var(--kf-border); border-radius: var(--kf-radius-sm); text-align: center; color: var(--kf-muted-foreground); font-size: var(--kf-fs-body-sm); }
.resume-claim { display: flex; flex-direction: column; gap: var(--kf-space-2); padding: var(--kf-space-3); border: 1px solid var(--kf-border); border-radius: var(--kf-radius-sm); background: var(--kf-surface-1); }
.resume-claim-head { display: flex; align-items: center; justify-content: space-between; gap: var(--kf-space-2); }
.resume-claim-index { font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
.resume-claim-actions { display: flex; gap: 2px; }
.resume-icon-button { display: inline-flex; align-items: center; justify-content: center; width: 28px; height: 28px; border: 0; border-radius: var(--kf-radius-xs); background: transparent; color: var(--kf-muted-foreground); cursor: pointer; }
.resume-icon-button:hover:not(:disabled) { background: var(--kf-surface-2); color: var(--kf-foreground); }
.resume-icon-button.is-danger:hover:not(:disabled) { color: var(--kf-state-error); }
.resume-icon-button:focus-visible { outline: 2px solid var(--kf-primary); outline-offset: 1px; }
.resume-icon-button:disabled { opacity: .4; cursor: not-allowed; }
.resume-claim-fields { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: var(--kf-space-2); }
.resume-claim-field { display: flex; flex-direction: column; gap: var(--kf-space-1); min-width: 0; font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
.resume-claim-quote { display: grid; grid-template-columns: minmax(0, 1fr) minmax(200px, 1fr); gap: var(--kf-space-2); }
.resume-claim-picker { min-width: 0; }
.resume-claim-foot { display: flex; align-items: center; justify-content: space-between; gap: var(--kf-space-2); flex-wrap: wrap; }
.resume-claim-invalid { font-size: var(--kf-fs-caption); color: var(--kf-state-error); }
.resume-polish-button { display: inline-flex; align-items: center; gap: var(--kf-space-1); min-height: 28px; padding: 0 var(--kf-space-2); border: 1px solid var(--kf-border); border-radius: var(--kf-radius-sm); background: transparent; color: var(--kf-primary); font-size: var(--kf-fs-caption); cursor: pointer; }
.resume-polish-button:hover:not(:disabled) { background: var(--kf-primary-subtle); }
.resume-polish-button:disabled { opacity: .5; cursor: not-allowed; }
.resume-editor-ai { display: flex; flex-direction: column; gap: var(--kf-space-2); padding: var(--kf-space-3); border: 1px solid var(--kf-border); border-radius: var(--kf-radius-md); background: var(--kf-surface-1); position: sticky; top: var(--kf-space-4); max-height: calc(100vh - 180px); overflow: auto; }
.resume-editor-ai-head { display: flex; align-items: center; gap: var(--kf-space-2); color: var(--kf-primary); }
.resume-editor-ai-head h3 { margin: 0; }
.resume-editor-ai-state { margin: 0; font-size: var(--kf-fs-body-sm); color: var(--kf-muted-foreground); }
.resume-suggestion { display: flex; flex-direction: column; gap: var(--kf-space-1); padding: var(--kf-space-2); border-radius: var(--kf-radius-sm); background: var(--kf-surface-2); }
.resume-suggestion-label { margin: 0; font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
.resume-suggestion blockquote { margin: 0; padding-left: var(--kf-space-2); border-left: 2px solid var(--kf-border); font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
.resume-suggestion-text { margin: 0; font-size: var(--kf-fs-body-sm); color: var(--kf-foreground); }
.resume-suggestion-reason { margin: 0; font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
@media (max-width: 1200px) {
  .resume-editor-body { grid-template-columns: 160px minmax(0, 1fr); }
  .resume-editor.is-preview .resume-editor-fields { display: none; }
  .resume-editor-ai { grid-column: 1 / -1; position: static; max-height: none; }
}
@media (max-width: 760px) {
  .resume-editor-body { grid-template-columns: 1fr; }
  .resume-editor-nav { flex-direction: row; flex-wrap: wrap; position: static; }
  .resume-editor-nav-item { flex: 1 1 auto; justify-content: center; }
  .resume-claim-quote { grid-template-columns: 1fr; }
}
</style>
