<template>
  <AppDialog
    :open="open"
    title="批量导入技术词条"
    description="先预览校验与冲突，再确认写入；同一预览 token 重复确认不会重复写库。"
    width="760px"
    scrollable
    :confirm-text="preview ? '确认导入' : '预览校验'"
    :loading="busy"
    :disabled="!preview && !raw.trim()"
    :error="error"
    @confirm="onConfirm"
    @cancel="close"
  >
    <div class="te-import">
      <label class="te-field">
        <span class="te-label">JSON 词条数组（UTF-8）</span>
        <textarea
          v-model="raw"
          class="kb-textarea kb-form-control te-json"
          rows="10"
          spellcheck="false"
          placeholder='[{"headword":"AQS","termType":"ACRONYM","domain":"CONCURRENCY","fullForm":"AbstractQueuedSynchronizer","meaningCn":"抽象队列同步器","contentStatus":"DRAFT"}]'
        />
      </label>

      <div class="te-import-actions">
        <button type="button" class="te-action-btn" @click="fillSample">插入示例</button>
        <button type="button" class="te-action-btn" :disabled="busy" @click="previewImport">预览校验</button>
        <button
          v-if="preview"
          type="button"
          class="te-action-btn te-action-btn-primary"
          :disabled="busy"
          @click="confirmImport"
        >确认导入</button>
        <button v-if="preview || result" type="button" class="te-action-btn" :disabled="busy" @click="reset">重新开始</button>
      </div>

      <section v-if="preview" class="te-preview">
        <h3 class="te-section-title">预览结果</h3>
        <p class="te-muted">
          新增 {{ preview.newCount }} · 更新 {{ preview.updateCount }} · 无效 {{ preview.invalidCount }}
          <span v-if="preview.expiresAt">（预览 token 有效期至 {{ formatTime(preview.expiresAt) }}）</span>
        </p>
        <ul v-if="preview.items.length" class="te-items">
          <li v-for="item in preview.items" :key="`${item.index}-${item.headword}`">
            <span class="te-item-index">#{{ item.index + 1 }}</span>
            <span class="te-item-word">{{ item.headword || '（无主词）' }}</span>
            <span class="te-item-domain">{{ item.domain || '（无领域）' }}</span>
            <span class="te-item-action" :class="`is-${(item.action || '').toLowerCase()}`">{{ actionLabel(item.action) }}</span>
            <span v-if="item.message" class="te-muted">{{ item.message }}</span>
          </li>
        </ul>
        <p v-else class="te-muted">没有可导入的词条。</p>
      </section>

      <section v-if="result" class="te-preview">
        <h3 class="te-section-title">导入结果</h3>
        <p class="te-muted">
          新增 {{ result.created }} · 更新 {{ result.updated }} · 跳过 {{ result.skipped }} · 无效 {{ result.invalid }}
          <template v-if="result.replayed">（这是同一 token 的重复确认，已回放首次结果，未重复写库）</template>
        </p>
      </section>
    </div>
  </AppDialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import AppDialog from '@/components/ui/AppDialog.vue'
import { adminTechEnglishApi } from '@/api/techEnglish'
import type { ImportPreviewVO, ImportResultVO, TermInput } from '@/api/techEnglish'
import { getApiError } from '@/utils/toast'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: []; imported: [] }>()

const raw = ref('')
const preview = ref<ImportPreviewVO | null>(null)
const result = ref<ImportResultVO | null>(null)
const error = ref('')
const busy = ref(false)

watch(
  () => props.open,
  (open) => {
    if (!open) return
    error.value = ''
  },
)

function reset() {
  preview.value = null
  result.value = null
  error.value = ''
}

function fillSample() {
  raw.value = JSON.stringify(
    [
      {
        headword: 'AQS',
        termType: 'ACRONYM',
        domain: 'CONCURRENCY',
        fullForm: 'AbstractQueuedSynchronizer',
        className: 'java.util.concurrent.locks.AbstractQueuedSynchronizer',
        meaningCn: '抽象队列同步器',
        definitionCn: 'AQS 通过一个 int 状态与 CLH 变体队列实现同步器骨架。',
        contentStatus: 'DRAFT',
        referenceSource: 'OpenJDK 17 源码',
      },
    ],
    null,
    2,
  )
}

function parseTerms(): TermInput[] {
  const parsed: unknown = JSON.parse(raw.value)
  const list = Array.isArray(parsed)
    ? parsed
    : (parsed as { terms?: unknown })?.terms
  if (!Array.isArray(list)) throw new Error('JSON 必须是词条数组，或包含 terms 数组的对象')
  return list as TermInput[]
}

async function previewImport() {
  if (busy.value) return
  error.value = ''
  result.value = null
  busy.value = true
  try {
    preview.value = await adminTechEnglishApi.previewImport(parseTerms())
  } catch (e: unknown) {
    preview.value = null
    error.value = e instanceof SyntaxError ? 'JSON 解析失败，请检查格式' : getApiError(e, '预览失败')
  } finally {
    busy.value = false
  }
}

async function confirmImport() {
  if (!preview.value || busy.value) return
  busy.value = true
  error.value = ''
  try {
    result.value = await adminTechEnglishApi.confirmImport(preview.value.token)
    emit('imported')
  } catch (e: unknown) {
    error.value = getApiError(e, '导入失败')
  } finally {
    busy.value = false
  }
}

function onConfirm() {
  if (preview.value && !result.value) void confirmImport()
  else void previewImport()
}

function close() {
  if (busy.value) return
  emit('close')
}

function actionLabel(action?: string | null): string {
  if (action === 'NEW') return '新增'
  if (action === 'UPDATE') return '更新'
  if (action === 'INVALID') return '无效'
  if (action === 'SKIP') return '跳过'
  return action || '未知'
}

function formatTime(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN')
}
</script>

<style scoped>
.te-import {
  display: grid;
  gap: var(--kb-space-4);
}
.te-field {
  display: grid;
  gap: calc(var(--kb-space-3) / 2);
  min-width: 0;
}
.te-label {
  display: block;
  margin: 0;
  font-size: var(--kb-fs-body-sm);
  line-height: var(--kb-lh-body-sm);
  font-weight: var(--kb-fw-label);
  color: var(--kb-foreground);
}
.te-json {
  min-width: 0;
  font-family: var(--font-mono);
  font-size: var(--kb-fs-body-sm);
  resize: vertical;
}
.te-import-actions {
  display: flex;
  align-items: center;
  gap: var(--kb-space-2);
  flex-wrap: wrap;
}
.te-preview {
  display: grid;
  gap: var(--kb-space-2);
  padding-top: var(--kb-space-3);
  border-top: 1px solid var(--kb-border);
}
.te-section-title {
  margin: 0;
  font-size: var(--kb-fs-body-lg);
  font-weight: 600;
  color: var(--kb-foreground);
}
.te-muted {
  margin: 0;
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
  overflow-wrap: anywhere;
}
.te-items {
  display: grid;
  gap: var(--kb-space-2);
  margin: 0;
  padding: 0;
  list-style: none;
  max-height: 280px;
  overflow-y: auto;
}
.te-items li {
  display: flex;
  align-items: baseline;
  gap: var(--kb-space-2);
  flex-wrap: wrap;
  padding: var(--kb-space-2);
  border-radius: var(--kb-radius-xs);
  background: var(--kb-background);
  font-size: var(--kb-fs-caption);
}
.te-item-index {
  color: var(--kb-muted-foreground);
}
.te-item-word {
  font-family: var(--font-mono);
  color: var(--kb-foreground);
}
.te-item-domain {
  color: var(--kb-muted-foreground);
}
.te-item-action.is-new {
  color: var(--kb-state-success);
}
.te-item-action.is-update {
  color: var(--kb-primary);
}
.te-item-action.is-invalid {
  color: var(--kb-state-error);
}
</style>
