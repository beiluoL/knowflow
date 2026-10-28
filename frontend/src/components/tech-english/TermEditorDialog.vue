<template>
  <AppDialog
    :open="open"
    :title="termId ? '编辑技术词条' : '新建技术词条'"
    :description="termId ? '修改后可保存为草稿或直接发布；发布要求填写来源。' : '新建词条默认是草稿，审核后再发布到公共词库。'"
    width="860px"
    scrollable
    confirm-text="保存"
    cancel-text="取消"
    :loading="saving"
    :error="error"
    @confirm="save"
    @cancel="close"
  >
    <form class="te-form" @submit.prevent="save">
      <fieldset class="te-grid">
        <legend class="te-legend">基础信息</legend>
        <label class="te-field">
          <span class="te-label">词条类型 <span class="te-req" aria-hidden="true">*</span></span>
          <select v-model="form.termType" class="kf-select kf-form-control" @change="onTypeChange">
            <option value="WORD">单词（WORD）</option>
            <option value="ACRONYM">缩写（ACRONYM）</option>
            <option value="PHRASE">短语（PHRASE）</option>
          </select>
        </label>
        <label class="te-field">
          <span class="te-label">主词 / 缩写 <span class="te-req" aria-hidden="true">*</span></span>
          <input v-model="form.headword" class="kf-input kf-form-control" maxlength="120" placeholder="如 AQS / Throughput" />
        </label>
        <label class="te-field">
          <span class="te-label">领域 <span class="te-req" aria-hidden="true">*</span></span>
          <input v-model="form.domain" class="kf-input kf-form-control" maxlength="40" placeholder="如 CONCURRENCY / RAG" />
        </label>
        <label class="te-field">
          <span class="te-label">义项键（同形异义时必填）</span>
          <input v-model="form.senseKey" class="kf-input kf-form-control" maxlength="80" placeholder="如 java-concurrency" />
        </label>
        <label class="te-field">
          <span class="te-label">中文短释义 <span class="te-req" aria-hidden="true">*</span></span>
          <input v-model="form.meaningCn" class="kf-input kf-form-control" maxlength="200" placeholder="如 抽象队列同步器" />
        </label>
        <label class="te-field">
          <span class="te-label">审核状态</span>
          <select v-model="form.contentStatus" class="kf-select kf-form-control">
            <option value="DRAFT">草稿（DRAFT）</option>
            <option value="REVIEWED">已审核（REVIEWED）</option>
            <option value="PUBLISHED">已发布（PUBLISHED）</option>
            <option value="ARCHIVED">已归档（ARCHIVED）</option>
          </select>
        </label>
      </fieldset>

      <fieldset class="te-grid">
        <legend class="te-legend">缩写与语言信息</legend>
        <label class="te-field">
          <span class="te-label">
            英文全称
            <span v-if="form.termType === 'ACRONYM'" class="te-req" aria-hidden="true">*</span>
          </span>
          <input v-model="form.fullForm" class="kf-input kf-form-control" maxlength="200" placeholder="如 AbstractQueuedSynchronizer" />
        </label>
        <label class="te-field">
          <span class="te-label">对应 Java 类名</span>
          <input v-model="form.className" class="kf-input kf-form-control" maxlength="240" placeholder="如 java.util.concurrent.locks.AbstractQueuedSynchronizer" />
        </label>
        <label class="te-field">
          <span class="te-label">词性</span>
          <input v-model="form.partOfSpeech" class="kf-input kf-form-control" maxlength="40" placeholder="如 noun / verb" />
        </label>
        <label class="te-field">
          <span class="te-label">美音音标（仅填已核验值）</span>
          <input v-model="form.ipaUs" class="kf-input kf-form-control" maxlength="80" placeholder="如 /əˈbstrækt/" />
        </label>
        <label class="te-field">
          <span class="te-label">英音音标（仅填已核验值）</span>
          <input v-model="form.ipaUk" class="kf-input kf-form-control" maxlength="80" placeholder="留空表示未核验" />
        </label>
      </fieldset>

      <fieldset class="te-grid">
        <legend class="te-legend">技术解释</legend>
        <label class="te-field te-span-2">
          <span class="te-label">中文技术解释</span>
          <textarea v-model="form.definitionCn" class="kf-textarea kf-form-control" rows="3" placeholder="说明专业场景、工程用途与边界" />
        </label>
        <label class="te-field te-span-2">
          <span class="te-label">英文技术解释</span>
          <textarea v-model="form.definitionEn" class="kf-textarea kf-form-control" rows="2" placeholder="可选，英文专业解释" />
        </label>
      </fieldset>

      <fieldset class="te-grid">
        <legend class="te-legend">技术例句与面试表达</legend>
        <label class="te-field te-span-2">
          <span class="te-label">技术例句（英文）</span>
          <textarea v-model="form.exampleEn" class="kf-textarea kf-form-control" rows="2" placeholder="可核对的技术上下文例句" />
        </label>
        <label class="te-field te-span-2">
          <span class="te-label">例句中文释义</span>
          <textarea v-model="form.exampleCn" class="kf-textarea kf-form-control" rows="2" placeholder="例句的中文解释" />
        </label>
        <label class="te-field te-span-2">
          <span class="te-label">面试英文表达</span>
          <textarea v-model="form.interviewEn" class="kf-textarea kf-form-control" rows="2" placeholder="一句可直接用于技术面试的英文回答（可选）" />
        </label>
        <label class="te-field te-span-2">
          <span class="te-label">别名 / 中文检索词（逗号分隔）</span>
          <input v-model="form.aliases" class="kf-input kf-form-control" placeholder="如 抽象队列同步器, 同步器" />
        </label>
      </fieldset>

      <fieldset class="te-grid">
        <legend class="te-legend">参考来源与审核信息</legend>
        <label class="te-field">
          <span class="te-label">
            参考来源
            <span v-if="form.contentStatus === 'PUBLISHED'" class="te-req" aria-hidden="true">*</span>
          </span>
          <input v-model="form.referenceSource" class="kf-input kf-form-control" placeholder="如 OpenJDK 17 源码 / 官方文档" />
        </label>
        <label class="te-field">
          <span class="te-label">授权/版权说明</span>
          <input v-model="form.licenseNote" class="kf-input kf-form-control" placeholder="如 项目原创整理" />
        </label>
      </fieldset>

      <fieldset class="te-grid">
        <legend class="te-legend">全称逐词拆解</legend>
        <div class="te-span-2 te-repeat-list">
          <div v-for="(part, index) in form.wordBreakdown" :key="`wb-${index}`" class="te-repeat-row">
            <input v-model="part.word" class="kf-input kf-form-control" placeholder="单词，如 Queued" maxlength="80" />
            <input v-model="part.meaningCn" class="kf-input kf-form-control" placeholder="中文，如 排队的" maxlength="120" />
            <button type="button" class="te-action-btn te-action-btn-sm te-remove" @click="form.wordBreakdown.splice(index, 1)">删除</button>
          </div>
          <button type="button" class="te-action-btn te-action-btn-sm" @click="form.wordBreakdown.push({ word: '', meaningCn: '' })">+ 添加拆解</button>
        </div>
        <label class="te-field te-span-2">
          <span class="te-label">拆解说明</span>
          <textarea v-model="form.breakdownNote" class="kf-textarea kf-form-control" rows="2" placeholder="说明完整概念不是各单词含义的机械相加（可选）" />
        </label>
      </fieldset>

      <fieldset class="te-grid">
        <legend class="te-legend">发音变体</legend>
        <div class="te-span-2 te-repeat-list">
          <div v-for="(pron, index) in form.pronunciations" :key="`pron-${index}`" class="te-pron-edit">
            <div class="te-pron-edit-row">
              <label class="te-field">
                <span class="te-label">读法变体</span>
                <select v-model="pron.variant" class="kf-select kf-form-control">
                  <option v-for="v in variantOptions" :key="v" :value="v">{{ v }}</option>
                </select>
              </label>
              <label class="te-field">
                <span class="te-label">朗读文本</span>
                <input v-model="pron.readingText" class="kf-input kf-form-control" placeholder="缩写逐字母：A Q S；全称整词" />
              </label>
              <label class="te-field">
                <span class="te-label">口音</span>
                <select v-model="pron.locale" class="kf-select kf-form-control">
                  <option value="en-US">en-US</option>
                  <option value="en-GB">en-GB</option>
                </select>
              </label>
              <label class="te-field">
                <span class="te-label">朗读方式</span>
                <select v-model="pron.readingMode" class="kf-select kf-form-control">
                  <option value="LETTER_BY_LETTER">逐字母</option>
                  <option value="SPOKEN_WORD">整词</option>
                  <option value="MIXED">混合</option>
                </select>
              </label>
              <label class="te-field">
                <span class="te-label">核验状态</span>
                <select v-model="pron.verificationStatus" class="kf-select kf-form-control">
                  <option value="VERIFIED">已核验</option>
                  <option value="PENDING">待核验</option>
                  <option value="UNAVAILABLE">不可用</option>
                </select>
              </label>
            </div>
            <div class="te-pron-edit-row">
              <label class="te-field te-grow">
                <span class="te-label">音频地址（可选，仅 http/https）</span>
                <input v-model="pron.audioUrl" class="kf-input kf-form-control" placeholder="留空则使用系统朗读" />
              </label>
              <label class="te-field">
                <span class="te-label">音频提供者</span>
                <input v-model="pron.audioProvider" class="kf-input kf-form-control" placeholder="如 项目录音" />
              </label>
              <label class="te-field">
                <span class="te-label">授权说明</span>
                <input v-model="pron.licenseNote" class="kf-input kf-form-control" placeholder="如 CC-BY" />
              </label>
              <button type="button" class="te-action-btn te-action-btn-sm te-remove" @click="form.pronunciations.splice(index, 1)">删除</button>
            </div>
          </div>
          <button type="button" class="te-action-btn te-action-btn-sm" @click="addPronunciation">+ 添加发音变体</button>
        </div>
      </fieldset>
    </form>
  </AppDialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import AppDialog from '@/components/ui/AppDialog.vue'
import { adminTechEnglishApi } from '@/api/techEnglish'
import type {
  ContentStatus,
  PronunciationVariant,
  ReadingMode,
  TermDetail,
  TermInput,
  TermType,
  VerificationStatus,
} from '@/api/techEnglish'
import { getApiError } from '@/utils/toast'

interface PronunciationDraft {
  variant: PronunciationVariant
  readingText: string
  locale: string
  readingMode: ReadingMode
  audioUrl: string
  audioProvider: string
  licenseNote: string
  verificationStatus: VerificationStatus
}

interface FormState {
  termType: TermType
  headword: string
  domain: string
  senseKey: string
  meaningCn: string
  contentStatus: ContentStatus
  fullForm: string
  className: string
  partOfSpeech: string
  ipaUs: string
  ipaUk: string
  definitionCn: string
  definitionEn: string
  exampleEn: string
  exampleCn: string
  interviewEn: string
  breakdownNote: string
  aliases: string
  referenceSource: string
  licenseNote: string
  wordBreakdown: { word: string; meaningCn: string }[]
  pronunciations: PronunciationDraft[]
}

const props = withDefaults(
  defineProps<{
    open: boolean
    termId?: number | null
    initial?: TermDetail | null
  }>(),
  { termId: null, initial: null },
)

const emit = defineEmits<{
  close: []
  saved: [detail: TermDetail]
}>()

const saving = ref(false)
const error = ref('')
const form = ref<FormState>(emptyForm())

const variantOptions = computed<PronunciationVariant[]>(() =>
  form.value.termType === 'ACRONYM'
    ? ['ABBR', 'FULL_FORM']
    : ['WORD_US', 'WORD_UK'],
)

function emptyForm(): FormState {
  return {
    termType: 'WORD',
    headword: '',
    domain: '',
    senseKey: '',
    meaningCn: '',
    contentStatus: 'DRAFT',
    fullForm: '',
    className: '',
    partOfSpeech: '',
    ipaUs: '',
    ipaUk: '',
    definitionCn: '',
    definitionEn: '',
    exampleEn: '',
    exampleCn: '',
    interviewEn: '',
    breakdownNote: '',
    aliases: '',
    referenceSource: '',
    licenseNote: '',
    wordBreakdown: [],
    pronunciations: [],
  }
}

function fromDetail(detail: TermDetail): FormState {
  return {
    termType: detail.termType,
    headword: detail.headword,
    domain: detail.domain,
    senseKey: detail.senseKey || '',
    meaningCn: detail.meaningCn,
    contentStatus: detail.contentStatus,
    fullForm: detail.fullForm || '',
    className: detail.className || '',
    partOfSpeech: detail.partOfSpeech || '',
    ipaUs: detail.ipaUs || '',
    ipaUk: detail.ipaUk || '',
    definitionCn: detail.definitionCn || '',
    definitionEn: detail.definitionEn || '',
    exampleEn: detail.exampleEn || '',
    exampleCn: detail.exampleCn || '',
    interviewEn: detail.interviewEn || '',
    breakdownNote: detail.breakdownNote || '',
    aliases: (detail.aliases || []).join(', '),
    referenceSource: detail.referenceSource || '',
    licenseNote: detail.licenseNote || '',
    wordBreakdown: (detail.wordBreakdown || []).map((p) => ({ ...p })),
    pronunciations: (detail.pronunciations || []).map((p) => ({
      variant: p.variant,
      readingText: p.readingText || '',
      locale: p.locale || 'en-US',
      readingMode: p.readingMode || 'SPOKEN_WORD',
      audioUrl: p.audioUrl || '',
      audioProvider: p.audioProvider || '',
      licenseNote: p.licenseNote || '',
      verificationStatus: p.verificationStatus,
    })),
  }
}

function addPronunciation() {
  form.value.pronunciations.push({
    variant: form.value.termType === 'ACRONYM' ? 'ABBR' : 'WORD_US',
    readingText: '',
    locale: 'en-US',
    readingMode: form.value.termType === 'ACRONYM' ? 'LETTER_BY_LETTER' : 'SPOKEN_WORD',
    audioUrl: '',
    audioProvider: '',
    licenseNote: '',
    verificationStatus: 'PENDING',
  })
}

function onTypeChange() {
  const allowed = variantOptions.value
  form.value.pronunciations = form.value.pronunciations.filter((p) => allowed.includes(p.variant))
}

watch(
  () => props.open,
  async (open) => {
    if (!open) return
    error.value = ''
    form.value = emptyForm()
    if (!props.termId) {
      if (props.initial) form.value = fromDetail(props.initial)
      return
    }
    if (props.initial && props.initial.id === props.termId) {
      form.value = fromDetail(props.initial)
      return
    }
    saving.value = true
    try {
      const detail = await adminTechEnglishApi.getTerm(props.termId)
      form.value = fromDetail(detail)
    } catch (e: unknown) {
      error.value = getApiError(e, '词条加载失败')
    } finally {
      saving.value = false
    }
  },
)

function validate(): string {
  const f = form.value
  if (!f.headword.trim()) return '请填写主词或缩写'
  if (!f.domain.trim()) return '请填写领域'
  if (!f.meaningCn.trim()) return '请填写中文短释义'
  if (f.termType === 'ACRONYM' && !f.fullForm.trim()) return '缩写类型必须填写英文全称'
  if (f.contentStatus === 'PUBLISHED' && !f.referenceSource.trim()) return '发布词条必须填写参考来源'
  for (const p of f.pronunciations) {
    if (p.audioUrl.trim() && !/^https?:\/\//i.test(p.audioUrl.trim())) return '音频地址只允许 http/https'
    if (p.verificationStatus === 'VERIFIED' && !p.readingText.trim()) return '已核验读法必须填写朗读文本'
  }
  return ''
}

function nullable(value: string): string | null {
  const trimmed = value.trim()
  return trimmed ? trimmed : null
}

function buildPayload(): TermInput {
  const f = form.value
  return {
    headword: f.headword.trim(),
    termType: f.termType,
    domain: f.domain.trim().toUpperCase(),
    senseKey: nullable(f.senseKey),
    fullForm: nullable(f.fullForm),
    className: nullable(f.className),
    partOfSpeech: nullable(f.partOfSpeech),
    ipaUs: nullable(f.ipaUs),
    ipaUk: nullable(f.ipaUk),
    meaningCn: f.meaningCn.trim(),
    definitionCn: nullable(f.definitionCn),
    definitionEn: nullable(f.definitionEn),
    wordBreakdown: f.wordBreakdown
      .map((p) => ({ word: p.word.trim(), meaningCn: p.meaningCn.trim() }))
      .filter((p) => p.word),
    exampleEn: nullable(f.exampleEn),
    exampleCn: nullable(f.exampleCn),
    interviewEn: nullable(f.interviewEn),
    breakdownNote: nullable(f.breakdownNote),
    aliases: f.aliases
      .split(/[,，]/)
      .map((a) => a.trim())
      .filter(Boolean),
    contentStatus: f.contentStatus,
    referenceSource: nullable(f.referenceSource),
    licenseNote: nullable(f.licenseNote),
    pronunciations: f.pronunciations.map((p) => ({
      variant: p.variant,
      readingText: nullable(p.readingText),
      locale: p.locale || null,
      readingMode: p.readingMode,
      audioUrl: nullable(p.audioUrl),
      audioProvider: nullable(p.audioProvider),
      licenseNote: nullable(p.licenseNote),
      verificationStatus: p.verificationStatus,
    })),
  }
}

async function save() {
  if (saving.value) return
  const message = validate()
  if (message) {
    error.value = message
    return
  }
  saving.value = true
  error.value = ''
  try {
    const payload = buildPayload()
    const detail = props.termId
      ? await adminTechEnglishApi.updateTerm(props.termId, payload)
      : await adminTechEnglishApi.createTerm(payload)
    emit('saved', detail)
  } catch (e: unknown) {
    error.value = getApiError(e, '保存失败')
  } finally {
    saving.value = false
  }
}

function close() {
  if (saving.value) return
  emit('close')
}
</script>

<style scoped>
/* 表单布局：label 在控件外部，控件沿用全局浅灰表面（--kf-field-surface），不再外包灰底 .kf-field */
.te-form {
  display: grid;
  gap: var(--kf-space-5);
  /* 作为内联尺寸容器，使下方 @container 断点真正生效（原先无容器祖先，窄屏规则不会触发） */
  container-type: inline-size;
}
.te-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: var(--kf-space-4);
  row-gap: var(--kf-space-4);
  min-width: 0;
  margin: 0;
  padding: 0;
  border: 0;
}
.te-legend {
  grid-column: 1 / -1;
  padding: 0;
  margin-bottom: var(--kf-space-1);
  font-size: var(--kf-fs-body-lg);
  line-height: var(--kf-lh-body-lg);
  font-weight: 600;
  color: var(--kf-foreground);
}
.te-span-2 {
  grid-column: 1 / -1;
}
.te-field {
  display: grid;
  align-content: start;
  gap: calc(var(--kf-space-3) / 2);
  min-width: 0;
}
/* 全局 .kf-select 是 inline-block + min-width:140px，会让下拉只占灰底一小块；
   表单内强制填满字段宽度，箭头仍由全局样式固定在右侧 */
.te-field .kf-select {
  display: block;
  width: 100%;
  min-width: 0;
}
.te-field .kf-input,
.te-field .kf-textarea {
  min-width: 0;
}
.te-label {
  display: block;
  margin: 0;
  font-size: var(--kf-fs-body-sm);
  line-height: var(--kf-lh-body-sm);
  font-weight: var(--kf-fw-label);
  color: var(--kf-foreground);
}
.te-req {
  margin-inline-start: 2px;
  color: var(--kf-destructive);
}
.te-repeat-list {
  display: grid;
  gap: var(--kf-space-3);
}
.te-repeat-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) auto;
  gap: var(--kf-space-2);
  align-items: end;
}
.te-pron-edit {
  display: grid;
  gap: var(--kf-space-2);
  padding: var(--kf-space-3);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  background: var(--kf-card);
}
.te-pron-edit-row {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: var(--kf-space-2);
  align-items: end;
}
.te-pron-edit-row:last-child {
  grid-template-columns: minmax(0, 2fr) minmax(0, 1fr) minmax(0, 1fr) auto;
}
.te-grow {
  min-width: 0;
}
.te-remove {
  color: var(--kf-destructive);
  border-color: color-mix(in srgb, var(--kf-destructive) 35%, transparent);
}
.te-action-btn.te-remove:hover:not(:disabled) {
  background: var(--kf-danger-subtle);
  border-color: var(--kf-destructive);
}
@container (max-width: 720px) {
  .te-grid,
  .te-pron-edit-row,
  .te-pron-edit-row:last-child,
  .te-repeat-row {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
