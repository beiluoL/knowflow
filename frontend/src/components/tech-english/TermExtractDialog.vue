<template>
  <AppDialog
    :open="open"
    title="提取专业英语"
    description="从当前文章或选区提取技术词汇候选；未经审核不会进入公共词库。"
    width="760px"
    scrollable
    :hide-footer="true"
    @cancel="emit('close')"
  >
    <!-- 提取范围与规则 -->
    <section v-if="!result" class="te-scope">
      <div class="te-row">
        <span class="te-label">提取范围</span>
        <div class="te-chips">
          <button
            type="button"
            class="te-chip"
            :class="{ 'is-active': scope === 'ARTICLE' }"
            :aria-pressed="scope === 'ARTICLE'"
            @click="scope = 'ARTICLE'"
          >当前文章</button>
          <button
            type="button"
            class="te-chip"
            :class="{ 'is-active': scope === 'SELECTION' }"
            :aria-pressed="scope === 'SELECTION'"
            :disabled="!selectionText"
            @click="scope = 'SELECTION'"
          >当前选区{{ selectionText ? '' : '（未选中）' }}</button>
        </div>
      </div>

      <div class="te-row">
        <span class="te-label">词条类型</span>
        <div class="te-chips">
          <button
            v-for="type in typeOptions"
            :key="type.value"
            type="button"
            class="te-chip"
            :class="{ 'is-active': types.includes(type.value) }"
            :aria-pressed="types.includes(type.value)"
            @click="toggleType(type.value)"
          >{{ type.label }}</button>
        </div>
      </div>

      <div class="te-row">
        <span class="te-label">候选上限</span>
        <input
          v-model.number="limit"
          class="te-limit kf-input"
          type="number"
          min="5"
          max="60"
          step="5"
          aria-label="候选数量上限"
        />
        <span class="te-hint">最多 60 条，超出会截断</span>
      </div>

      <label class="te-ai kf-checkbox-row">
        <Checkbox as="span" v-model="useAi" />
        <span>
          使用 AI 建议释义
          <small>勾选后按现有模型配置调用；未配置模型时自动降级为规则提取，不会伪造 AI 结果。</small>
        </span>
      </label>

      <p v-if="extractError" class="te-error" role="alert">{{ extractError }}</p>

      <div class="te-actions">
        <button type="button" class="te-btn te-btn-primary" :disabled="extracting" @click="runExtract">
          {{ extracting ? '提取中…' : '开始提取' }}
        </button>
      </div>
    </section>

    <!-- 候选预览 -->
    <section v-else class="te-result">
      <div class="te-summary">
        <span class="te-badge" :class="result.extractor === 'AI' ? 'is-ai' : 'is-rule'">
          {{ result.extractor === 'AI' ? 'AI 建议' : '规则提取' }}
        </span>
        <span class="te-summary-text">
          扫描 {{ result.totalScanned }} 字符，候选 {{ result.items.length }} 条{{ result.truncated ? '（已截断）' : '' }}
        </span>
        <button type="button" class="te-link-btn" @click="resetPreview">重新提取</button>
      </div>
      <p v-if="result.degradationNote" class="te-note">{{ result.degradationNote }}</p>

      <p v-if="!rows.length" class="te-empty">未提取到可用候选。可以换用选区或调整词条类型后重试。</p>

      <ul v-else class="te-list">
        <li v-for="row in rows" :key="row.key" class="te-item" :class="{ 'is-unchecked': !row.checked }">
          <label class="te-check kf-checkbox-row">
            <Checkbox as="span" v-model="row.checked"  :aria-label="`选择 ${row.headword}`" />
          </label>
          <div class="te-item-main">
            <div class="te-item-head">
              <span class="te-word" :class="{ 'is-acronym': row.termType === 'ACRONYM' }">{{ row.headword }}</span>
              <span class="te-type">{{ termTypeLabel(row.termType) }}</span>
              <span v-if="row.existsInLibrary" class="te-dup">
                词库已收录{{ row.duplicateHeadword ? `：${row.duplicateHeadword}` : '' }}
              </span>
            </div>
            <div class="te-fields">
              <label class="te-field">
                <span>中文释义</span>
                <input v-model="row.meaningCn" class="kf-input" maxlength="120" placeholder="人工补全释义" />
              </label>
              <label class="te-field te-field-sm">
                <span>技术分类</span>
                <select v-model="row.domain" class="kf-input">
                  <option v-for="(label, key) in DOMAIN_LABELS" :key="key" :value="key">{{ label }}</option>
                </select>
              </label>
            </div>
            <label v-if="row.termType === 'ACRONYM'" class="te-field">
              <span>英文全称（未核验）</span>
              <input v-model="row.fullForm" class="kf-input" maxlength="160" placeholder="不确定时留空，不猜测" />
            </label>
            <p v-if="row.sourceContext" class="te-context">原文：{{ row.sourceContext }}</p>
          </div>
          <button type="button" class="te-remove" :aria-label="`移除 ${row.headword}`" @click="removeRow(row.key)">
            <Icon name="x" :size="14" />
          </button>
        </li>
      </ul>

      <p v-if="extractError" class="te-error" role="alert">{{ extractError }}</p>
      <p v-if="saveMessage" class="te-success" role="status">{{ saveMessage }}</p>

      <div class="te-actions">
        <button type="button" class="te-btn" @click="resetPreview">返回设置</button>
        <button type="button" class="te-btn te-btn-primary" :disabled="saving || !selectedRows.length" @click="submitCandidates">
          {{ saving ? '提交中…' : `提交待审核（${selectedRows.length}）` }}
        </button>
      </div>
    </section>
  </AppDialog>
</template>

<script setup lang="ts">
import Checkbox from '@/components/ui/Checkbox.vue'

// 专业词汇提取：先预览再由用户逐条确认，落库的是「个人待审候选」，不等于公共已发布词条。
import { computed, ref, watch } from 'vue'
import AppDialog from '@/components/ui/AppDialog.vue'
import Icon from '@/components/ui/Icon.vue'
import {
  techEnglishApi,
  type CandidateInput,
  type ExtractedCandidateVO,
  type ExtractionResultVO,
  type TermType,
} from '@/api/techEnglish'
import { DOMAIN_LABELS, TERM_TYPE_LABELS, termTypeLabel } from './term-labels'
import { useAuthStore } from '@/stores/auth'
import { getApiError, notify } from '@/utils/toast'

const props = withDefaults(
  defineProps<{
    open: boolean
    /** 来源类型：知识库文档 / 笔记 / 无实体正文（章节教材） */
    sourceType: 'DOCUMENT' | 'NOTE' | 'TEXT'
    sourceId?: number | null
    sourceTitle?: string
    /** 当前文章完整正文（TEXT 来源必填；DOCUMENT/NOTE 由服务端读取） */
    articleText?: string
    /** 打开弹窗时捕获的选区文本，为空表示不支持选区提取 */
    selectionText?: string
  }>(),
  { sourceId: null, sourceTitle: '', articleText: '', selectionText: '' },
)

const emit = defineEmits<{ close: []; saved: [number] }>()

const typeOptions: Array<{ value: TermType; label: string }> = [
  { value: 'WORD', label: TERM_TYPE_LABELS.WORD },
  { value: 'ACRONYM', label: TERM_TYPE_LABELS.ACRONYM },
  { value: 'PHRASE', label: TERM_TYPE_LABELS.PHRASE },
]

interface Row {
  key: string
  checked: boolean
  headword: string
  termType: TermType
  domain: string
  fullForm: string
  meaningCn: string
  definitionCn: string
  sourceContext: string
  confidence: number | null
  extractor: 'RULE' | 'AI'
  existsInLibrary: boolean
  duplicateHeadword: string | null
}

const scope = ref<'ARTICLE' | 'SELECTION'>('ARTICLE')
const types = ref<TermType[]>(['WORD', 'ACRONYM', 'PHRASE'])
const limit = ref(20)
const useAi = ref(false)

const extracting = ref(false)
const extractError = ref('')
const result = ref<ExtractionResultVO | null>(null)
const rows = ref<Row[]>([])
const saving = ref(false)
const saveMessage = ref('')

const selectedRows = computed(() => rows.value.filter((row) => row.checked))

function resetState() {
  scope.value = props.selectionText ? 'SELECTION' : 'ARTICLE'
  types.value = ['WORD', 'ACRONYM', 'PHRASE']
  limit.value = 20
  useAi.value = false
  extracting.value = false
  extractError.value = ''
  result.value = null
  rows.value = []
  saving.value = false
  saveMessage.value = ''
}

watch(
  () => props.open,
  (open) => {
    if (open) resetState()
  },
  { immediate: true },
)

function toggleType(value: TermType) {
  if (types.value.includes(value)) {
    // 至少保留一种类型，避免提交必然为空的请求
    if (types.value.length === 1) return
    types.value = types.value.filter((item) => item !== value)
    return
  }
  types.value = [...types.value, value]
}

function resetPreview() {
  result.value = null
  rows.value = []
  saveMessage.value = ''
  extractError.value = ''
}

function toRow(item: ExtractedCandidateVO, index: number): Row {
  return {
    key: `${item.headword}-${item.termType}-${index}`,
    // 词库中已存在的同形词条默认不勾选，避免制造重复候选
    checked: !item.existsInLibrary,
    headword: item.headword,
    termType: item.termType,
    domain: item.domain || 'JAVA',
    fullForm: item.fullForm || '',
    meaningCn: item.meaningCn || '',
    definitionCn: item.definitionCn || '',
    sourceContext: item.sourceContext || '',
    confidence: item.confidence ?? null,
    extractor: item.extractor,
    existsInLibrary: item.existsInLibrary,
    duplicateHeadword: item.duplicateHeadword ?? null,
  }
}

function sourceHint() {
  return {
    sourceType: props.sourceType,
    sourceId: props.sourceId ?? null,
    sourceTitle: props.sourceTitle || '',
  }
}

async function runExtract() {
  if (extracting.value) return
  if (scope.value === 'SELECTION' && !props.selectionText) {
    extractError.value = '当前没有可用于提取的选区'
    return
  }
  extracting.value = true
  extractError.value = ''
  saveMessage.value = ''
  try {
    const payload = scope.value === 'SELECTION'
      ? {
          text: props.selectionText,
          documentId: props.sourceType === 'DOCUMENT' ? props.sourceId ?? undefined : undefined,
          noteId: props.sourceType === 'NOTE' ? props.sourceId ?? undefined : undefined,
          scope: 'SELECTION' as const,
          types: types.value,
          limit: limit.value,
          useAi: useAi.value,
        }
      : props.sourceType === 'DOCUMENT' || props.sourceType === 'NOTE'
        ? {
            documentId: props.sourceType === 'DOCUMENT' ? props.sourceId ?? undefined : undefined,
            noteId: props.sourceType === 'NOTE' ? props.sourceId ?? undefined : undefined,
            scope: 'ARTICLE' as const,
            types: types.value,
            limit: limit.value,
            useAi: useAi.value,
          }
        : {
            text: props.articleText,
            scope: 'ARTICLE' as const,
            types: types.value,
            limit: limit.value,
            useAi: useAi.value,
          }
    const data = await techEnglishApi.extractCandidates(payload)
    result.value = data
    rows.value = data.items.map(toRow)
  } catch (e) {
    extractError.value = getApiError(e, '提取失败，请稍后重试')
  } finally {
    extracting.value = false
  }
}

function removeRow(key: string) {
  rows.value = rows.value.filter((row) => row.key !== key)
}

async function submitCandidates() {
  if (saving.value || !selectedRows.value.length) return
  saving.value = true
  extractError.value = ''
  const hint = sourceHint()
  const payload: CandidateInput[] = selectedRows.value.map((row) => ({
    headword: row.headword,
    termType: row.termType,
    domain: row.domain,
    fullForm: row.fullForm.trim() || null,
    meaningCn: row.meaningCn.trim() || null,
    definitionCn: row.definitionCn.trim() || null,
    sourceType: hint.sourceType,
    sourceId: hint.sourceId,
    sourceTitle: hint.sourceTitle || null,
    sourceContext: row.sourceContext || null,
    confidence: row.confidence,
    extractor: row.extractor,
  }))
  try {
    const res = await techEnglishApi.saveCandidates(payload)
    saveMessage.value = `已保存 ${res.saved} 条个人待审候选${res.skipped ? `，${res.skipped} 条因重复被跳过` : ''}。审核通过前不会进入公共词库。`
    emit('saved', res.saved)
    // 保存成功后保留列表供用户核对，再次提交由服务端幂等去重
    rows.value = rows.value
      .filter((row) => !row.checked)
      .map((row) => ({ ...row, checked: true }))
    if (res.saved === 0) notify('没有新增候选（可能已在个人候选列表中）', 'info')
  } catch (e) {
    extractError.value = getApiError(e, '候选保存失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

// 未登录时由父级拦截；此处兜底避免调用必然 401 的接口。
// 只有真正打开时才读取登录态，未打开的弹窗不产生 Pinia 依赖（阅读页可独立渲染）。
watch(
  () => props.open,
  (open) => {
    if (open && !useAuthStore().isLoggedIn) notify('请先登录后再提取专业词汇', 'info')
  },
)
</script>

<style scoped>
.te-scope,
.te-result {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.te-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  font-size: 13px;
}

.te-label {
  width: 68px;
  flex: none;
  color: var(--kf-muted-foreground, #6b7280);
}

.te-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.te-chip {
  height: 30px;
  padding: 0 12px;
  border-radius: var(--kf-radius-sm, 8px);
  border: 1px solid var(--kf-border, #e2e6ec);
  background: var(--kf-card, #ffffff);
  color: var(--kf-foreground, #1a1d23);
  font-size: 13px;
  cursor: pointer;
}

.te-chip.is-active {
  border-color: var(--kf-primary, #3b6fe0);
  color: var(--kf-primary, #3b6fe0);
  background: var(--kf-primary-soft, #eef3fd);
}

.te-chip:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.te-limit {
  width: 92px;
}

.te-hint {
  color: var(--kf-muted-foreground, #6b7280);
  font-size: 12px;
}

.te-ai {
  display: flex;
  align-items: center;
  gap: var(--kf-checkbox-gap);
  padding: 10px 12px;
  border-radius: var(--kf-radius-sm, 8px);
  background: var(--kf-muted, #f7f8fa);
  font-size: var(--kf-fs-body-md);
}

.te-ai small {
  display: block;
  margin-top: 2px;
  color: var(--kf-muted-foreground, #6b7280);
  font-size: 12px;
  line-height: 1.5;
}

.te-summary {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  font-size: 13px;
}

.te-badge {
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 12px;
  border: 1px solid var(--kf-border, #e2e6ec);
}

.te-badge.is-ai {
  color: var(--kf-primary, #3b6fe0);
  background: var(--kf-primary-soft, #eef3fd);
  border-color: transparent;
}

.te-badge.is-rule {
  color: var(--kf-muted-foreground, #6b7280);
  background: var(--kf-muted, #f7f8fa);
}

.te-summary-text {
  color: var(--kf-muted-foreground, #6b7280);
  flex: 1;
  min-width: 160px;
}

.te-link-btn {
  border: none;
  background: none;
  padding: 0;
  color: var(--kf-primary, #3b6fe0);
  font-size: 13px;
  cursor: pointer;
}

.te-note {
  margin: 0;
  padding: 8px 12px;
  border-radius: var(--kf-radius-sm, 8px);
  background: var(--kf-muted, #f7f8fa);
  color: var(--kf-muted-foreground, #6b7280);
  font-size: 12px;
  line-height: 1.6;
}

.te-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.te-item {
  display: flex;
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--kf-border, #e2e6ec);
  border-radius: var(--kf-radius-md, 12px);
  background: var(--kf-card, #ffffff);
}

.te-item.is-unchecked {
  opacity: 0.6;
}

.te-check {
  padding-top: 2px;
}

.te-item-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.te-item-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.te-word {
  font-size: 16px;
  font-weight: 600;
  color: var(--kf-foreground, #1a1d23);
}

.te-word.is-acronym {
  font-family: var(--font-mono, 'JetBrains Mono', monospace);
}

.te-type {
  padding: 1px 6px;
  border-radius: 6px;
  background: var(--kf-muted, #f7f8fa);
  color: var(--kf-muted-foreground, #6b7280);
  font-size: 11px;
}

.te-dup {
  color: var(--kf-warning, #b45309);
  font-size: 12px;
}

.te-fields {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.te-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
  min-width: 180px;
  font-size: 12px;
  color: var(--kf-muted-foreground, #6b7280);
}

.te-field-sm {
  flex: 0 0 150px;
}

.te-field .kf-input {
  height: 32px;
  font-size: 13px;
}

.te-context {
  margin: 0;
  color: var(--kf-muted-foreground, #6b7280);
  font-size: 12px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.te-remove {
  width: 26px;
  height: 26px;
  flex: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--kf-muted-foreground, #6b7280);
  cursor: pointer;
}

.te-remove:hover {
  background: var(--kf-muted, #f7f8fa);
  color: var(--kf-danger, #d93025);
}

.te-empty {
  margin: 0;
  padding: 28px 0;
  text-align: center;
  color: var(--kf-muted-foreground, #6b7280);
  font-size: 13px;
}

.te-error {
  margin: 0;
  color: var(--kf-danger, #d93025);
  font-size: 13px;
}

.te-success {
  margin: 0;
  color: var(--kf-success, #1a7f5a);
  font-size: 13px;
}

.te-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.te-btn {
  height: 34px;
  padding: 0 14px;
  border-radius: var(--kf-radius-sm, 8px);
  border: 1px solid var(--kf-border, #e2e6ec);
  background: var(--kf-card, #ffffff);
  color: var(--kf-foreground, #1a1d23);
  font-size: 13px;
  cursor: pointer;
}

.te-btn-primary {
  border-color: transparent;
  background: var(--kf-primary, #3b6fe0);
  color: #ffffff;
}

.te-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
