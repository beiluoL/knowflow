<template>
  <AppDialog
    :open="open"
    :title="session ? (session.status === 'DRAFT' ? '编辑默写会话' : '编辑默写会话') : '新建默写会话'"
    :description="session ? '草稿可修改原文；已开始的会话会锁定原文快照。' : '将重要知识转化为三轮主动回忆任务。'"
    width="720px"
    scrollable
    :close-on-overlay="false"
    icon="edit-2"
    @cancel="request_close"
  >
    <div class="recall-form" data-test="recall-form">
      <div class="recall-field">
        <div class="recall-label-row">
          <label class="recall-label" for="recall-title">默写标题</label>
          <span class="recall-count">{{ form.title.length }}/100</span>
        </div>
        <input
          id="recall-title"
          v-model="form.title"
          data-test="recall-title"
          class="recall-input"
          type="text"
          maxlength="100"
          placeholder="例如：Java HashMap 底层原理"
        />
      </div>

      <div v-if="!locked" class="recall-field">
        <span class="recall-label">原文来源</span>
        <div class="recall-source-options" role="radiogroup" aria-label="原文来源">
          <button
            type="button"
            class="recall-source-card"
            :class="{ 'is-active': sourceMode === 'PASTE' }"
            role="radio"
            :aria-checked="sourceMode === 'PASTE'"
            data-test="recall-source-paste"
            @click="usePaste"
          >
            <Icon name="file-text" :size="16" aria-hidden="true" />
            <span class="recall-source-name">粘贴原文</span>
            <span class="recall-source-desc">直接输入或粘贴一段需要记忆的内容</span>
          </button>
          <button
            type="button"
            class="recall-source-card"
            :class="{ 'is-active': sourceMode === 'NOTE' }"
            role="radio"
            :aria-checked="sourceMode === 'NOTE'"
            data-test="recall-source-note"
            @click="openNotePicker"
          >
            <Icon name="book-open" :size="16" aria-hidden="true" />
            <span class="recall-source-name">从我的笔记选择</span>
            <span class="recall-source-desc">导入你有权限访问的康奈尔笔记正文</span>
          </button>
        </div>

        <div v-if="sourceMode === 'NOTE'" class="recall-note-panel">
          <div v-if="importedNoteTitle" class="recall-note-imported">
            <Icon name="check" :size="13" aria-hidden="true" />
            <span>已引用笔记：{{ importedNoteTitle }}</span>
            <button type="button" class="recall-link" @click="pickerOpen = true">更换</button>
          </div>
          <div v-if="pickerOpen" class="recall-note-picker">
            <div class="recall-note-search">
              <input
                v-model="noteKeyword"
                class="recall-input"
                type="search"
                placeholder="搜索笔记标题…"
                aria-label="搜索笔记"
                @keydown.enter.prevent="loadNotes"
              />
              <button type="button" class="recall-mini-btn" :disabled="loadingNotes" @click="loadNotes">
                {{ loadingNotes ? '搜索中…' : '搜索' }}
              </button>
            </div>
            <div v-if="loadingNotes" class="recall-note-empty">正在读取笔记…</div>
            <div v-else-if="notes.length === 0" class="recall-note-empty">没有可用的笔记，请先在「整理」中创建。</div>
            <ul v-else class="recall-note-list">
              <li v-for="note in notes" :key="note.id">
                <button
                  type="button"
                  class="recall-note-item"
                  :data-test="`recall-note-${note.id}`"
                  @click="pickNote(note)"
                >
                  <span class="recall-note-title">{{ note.title }}</span>
                  <span class="recall-note-preview">{{ notePreview(note) }}</span>
                </button>
              </li>
            </ul>
          </div>
        </div>
      </div>

      <div class="recall-field">
        <div class="recall-label-row">
          <label class="recall-label" for="recall-source">需要记忆的原文 <span class="recall-req">*</span></label>
          <span class="recall-count">{{ form.sourceText.length }}/10000</span>
        </div>
        <textarea
          id="recall-source"
          v-model="form.sourceText"
          data-test="recall-source"
          class="recall-textarea"
          rows="10"
          maxlength="10000"
          :readonly="locked"
          :aria-describedby="locked ? 'recall-lock-hint' : undefined"
          placeholder="输入或粘贴需要记忆的知识内容…"
        ></textarea>
        <p v-if="locked" id="recall-lock-hint" class="recall-lock" data-test="recall-lock-hint">
          <Icon name="lock" :size="13" aria-hidden="true" />
          已开始的会话原文已锁定，历史轮次需要与这份原文快照对比。若需大幅修改，请复制为新会话。
        </p>
        <div v-if="locked" class="recall-lock-actions">
          <button type="button" class="recall-link" data-test="recall-copy" @click="$emit('copy')">
            <Icon name="copy" :size="13" aria-hidden="true" /> 复制为新会话
          </button>
        </div>
      </div>

      <div class="recall-field">
        <span class="recall-label">三轮主动回忆流程</span>
        <div class="recall-flow">
          <div v-for="step in flowSteps" :key="step.num" class="recall-flow-card">
            <span class="recall-flow-num">{{ step.num }}</span>
            <div class="recall-flow-text">
              <span class="recall-flow-name">{{ step.name }}</span>
              <span class="recall-flow-desc">{{ step.desc }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <div class="recall-dialog-foot">
        <span v-if="dirty()" class="recall-dirty">有未保存的修改</span>
        <div class="recall-dialog-actions">
          <button type="button" class="recall-btn recall-btn-ghost" data-test="recall-cancel" :disabled="saving" @click="request_close">取消</button>
          <button
            v-if="!locked"
            type="button"
            class="recall-btn recall-btn-ghost"
            data-test="recall-save-draft"
            :disabled="saving"
            @click="submit(true)"
          >
            {{ saving ? '保存中…' : '保存草稿' }}
          </button>
          <button
            v-if="!session"
            type="button"
            class="recall-btn recall-btn-primary"
            data-test="recall-start"
            :disabled="saving"
            @click="submit(false)"
          >
            <Icon name="play" :size="14" aria-hidden="true" /> {{ saving ? '创建中…' : '开始第一轮' }}
          </button>
          <button
            v-else-if="isDraft"
            type="button"
            class="recall-btn recall-btn-primary"
            data-test="recall-start"
            :disabled="saving"
            @click="submit(false)"
          >
            <Icon name="play" :size="14" aria-hidden="true" /> {{ saving ? '保存中…' : '开始第一轮' }}
          </button>
          <button
            v-else
            type="button"
            class="recall-btn recall-btn-primary"
            data-test="recall-save"
            :disabled="saving"
            @click="submit(true)"
          >
            <Icon name="save" :size="14" aria-hidden="true" /> {{ saving ? '保存中…' : '保存修改' }}
          </button>
        </div>
      </div>
    </template>
  </AppDialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import AppDialog from '@/components/ui/AppDialog.vue'
import { notify, confirmDialog, getApiError } from '@/utils/toast'
import { listNotes, createRecallSession, updateRecallSession } from '@/api/workbench'
import type { WbNote, WbRecallSession } from '@/api/types'

const props = defineProps<{
  open: boolean
  session?: WbRecallSession | null
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'saved', payload: { id: number; started: boolean }): void
  (e: 'copy'): void
}>()

const form = reactive({
  title: '',
  sourceText: '',
  sourceType: 'PASTE' as 'PASTE' | 'NOTE',
  noteId: null as number | null,
})
const sourceMode = ref<'PASTE' | 'NOTE'>('PASTE')
const importedNoteTitle = ref('')
const pickerOpen = ref(false)
const notes = ref<WbNote[]>([])
const noteKeyword = ref('')
const loadingNotes = ref(false)
const saving = ref(false)
const snapshot = ref('')
const locked = ref(false)
const isDraft = computed(() => props.session?.status === 'DRAFT')

const flowSteps = [
  { num: '1', name: '第一轮 · 即时默写', desc: '不看原文，从记忆中完整复述。' },
  { num: '2', name: '第二轮 · 补漏默写', desc: '查看遗漏并补充，再次闭卷默写。' },
  { num: '3', name: '第三轮 · 延迟复测', desc: '间隔一小时后重新默写，检验长期记忆。' },
]

function currentSnapshot() {
  return JSON.stringify({ title: form.title, sourceText: form.sourceText, sourceType: form.sourceType, noteId: form.noteId })
}
function reset() {
  const s = props.session
  form.title = s?.title || ''
  form.sourceText = s?.sourceText || ''
  form.sourceType = s?.sourceType === 'NOTE' ? 'NOTE' : 'PASTE'
  form.noteId = s?.noteId ?? null
  sourceMode.value = s?.sourceType === 'NOTE' ? 'NOTE' : 'PASTE'
  importedNoteTitle.value = s?.sourceType === 'NOTE' ? s?.sourceLabel || '' : ''
  pickerOpen.value = false
  noteKeyword.value = ''
  notes.value = []
  locked.value = !!s && s.status !== 'DRAFT'
  snapshot.value = currentSnapshot()
}
watch(() => props.open, (open) => { if (open) reset() }, { immediate: true })

function dirty() {
  return currentSnapshot() !== snapshot.value
}

function usePaste() {
  sourceMode.value = 'PASTE'
  form.sourceType = 'PASTE'
  form.noteId = null
  importedNoteTitle.value = ''
  pickerOpen.value = false
}

function openNotePicker() {
  sourceMode.value = 'NOTE'
  form.sourceType = 'NOTE'
  pickerOpen.value = true
  if (notes.value.length === 0) loadNotes()
}

async function loadNotes() {
  loadingNotes.value = true
  try {
    notes.value = await listNotes({ keyword: noteKeyword.value.trim() || undefined })
  } catch (e) {
    notify(getApiError(e, '加载笔记失败'), 'error')
    notes.value = []
  } finally {
    loadingNotes.value = false
  }
}

function notePreview(note: WbNote) {
  const text = (note.noteColumn || note.summaryColumn || note.cueColumn || '').replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim()
  return text ? text.slice(0, 80) + (text.length > 80 ? '…' : '') : '（笔记正文为空）'
}

function pickNote(note: WbNote) {
  const text = [note.cueColumn, note.noteColumn, note.summaryColumn]
    .filter((part) => part && part.trim())
    .join('\n\n')
    .replace(/<[^>]+>/g, '')
    .trim()
  if (!text) {
    notify('该笔记正文为空，请换一篇笔记', 'warning')
    return
  }
  form.sourceText = text
  form.noteId = note.id
  form.sourceType = 'NOTE'
  importedNoteTitle.value = note.title
  pickerOpen.value = false
}

async function request_close() {
  if (saving.value) return
  if (dirty()) {
    const ok = await confirmDialog('当前内容尚未保存，确认关闭？', {
      title: '放弃未保存的修改？',
      confirmText: '放弃并关闭',
      cancelText: '继续编辑',
      icon: 'log-out',
      variant: 'default',
    })
    if (!ok) return
  }
  emit('close')
}

async function submit(draft: boolean) {
  if (saving.value) return
  if (!locked.value && !form.sourceText.trim()) {
    notify('原文不能为空', 'warning')
    return
  }
  saving.value = true
  try {
    if (props.session) {
      const payload: Record<string, unknown> = { title: form.title }
      if (!locked.value) {
        payload.sourceText = form.sourceText
        payload.sourceType = form.sourceType
        if (form.noteId != null) payload.noteId = form.noteId
      }
      const updated = await updateRecallSession(props.session.id, payload)
      notify(draft ? '会话已保存' : '已保存，开始第一轮默写', 'success')
      emit('saved', { id: updated.id, started: !draft })
    } else {
      const id = await createRecallSession({
        title: form.title,
        sourceText: form.sourceText,
        sourceType: form.sourceType,
        noteId: form.noteId ?? undefined,
        draft,
      })
      notify(draft ? '已保存为草稿' : '已创建，开始第一轮默写', 'success')
      emit('saved', { id, started: !draft })
    }
  } catch (e) {
    notify(getApiError(e, draft ? '保存失败' : '创建失败'), 'error')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.recall-form {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-5);
}
.recall-field {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-2);
}
.recall-label-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--kf-space-3);
}
.recall-label {
  font-size: var(--kf-fs-body-sm);
  font-weight: 600;
  color: var(--kf-foreground);
}
.recall-count {
  font-family: var(--font-mono);
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.recall-req { color: var(--kf-destructive); }
.recall-input,
.recall-textarea {
  width: 100%;
  box-sizing: border-box;
  border: none;
  border-radius: var(--kf-radius-md);
  background: var(--kf-background);
  color: var(--kf-foreground);
  font-family: var(--font-sans);
  font-size: var(--kf-fs-body-md);
  line-height: var(--kf-lh-body-md);
  padding: var(--kf-space-3) var(--kf-space-4);
  outline: none;
  transition: box-shadow 0.15s ease, background 0.15s ease;
}
.recall-input:focus-visible,
.recall-textarea:focus-visible {
  box-shadow: 0 0 0 2px var(--kf-ring);
  background: var(--kf-card);
}
.recall-textarea {
  min-height: 180px;
  max-height: 42vh;
  resize: vertical;
  line-height: 1.8;
}
.recall-textarea[readonly] {
  color: var(--kf-muted-foreground);
  cursor: not-allowed;
}
.recall-source-options {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--kf-space-3);
}
.recall-source-card {
  display: grid;
  grid-template-columns: auto 1fr;
  grid-template-rows: auto auto;
  gap: 2px var(--kf-space-2);
  align-items: center;
  padding: var(--kf-space-3) var(--kf-space-4);
  border-radius: var(--kf-radius-md);
  border: 1px solid var(--kf-border);
  background: var(--kf-background);
  color: var(--kf-foreground);
  text-align: left;
  cursor: pointer;
}
.recall-source-card > svg { grid-row: 1 / span 2; color: var(--kf-muted-foreground); }
.recall-source-card.is-active {
  border-color: var(--kf-primary);
  background: var(--kf-primary-soft);
}
.recall-source-card.is-active > svg { color: var(--kf-primary); }
.recall-source-card:focus-visible {
  outline: 2px solid var(--kf-ring);
  outline-offset: 2px;
}
.recall-source-name { font-size: var(--kf-fs-body-sm); font-weight: 600; }
.recall-source-desc { font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
.recall-note-panel {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-2);
  padding: var(--kf-space-3);
  border-radius: var(--kf-radius-md);
  background: var(--kf-background);
}
.recall-note-imported {
  display: flex;
  align-items: center;
  gap: var(--kf-space-2);
  font-size: var(--kf-fs-caption);
  color: var(--kf-state-success);
}
.recall-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: none;
  background: none;
  color: var(--kf-primary);
  font-size: var(--kf-fs-caption);
  cursor: pointer;
  padding: 0;
}
.recall-link:focus-visible { outline: 2px solid var(--kf-ring); outline-offset: 2px; }
.recall-note-search {
  display: flex;
  gap: var(--kf-space-2);
}
.recall-note-search .recall-input { flex: 1; }
.recall-mini-btn {
  padding: 0 var(--kf-space-3);
  border-radius: var(--kf-radius-md);
  border: 1px solid var(--kf-border);
  background: var(--kf-card);
  color: var(--kf-foreground);
  font-size: var(--kf-fs-caption);
  cursor: pointer;
}
.recall-note-list {
  list-style: none;
  margin: 0;
  padding: 0;
  max-height: 200px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.recall-note-item {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: var(--kf-space-2) var(--kf-space-3);
  border: none;
  border-radius: var(--kf-radius-sm);
  background: transparent;
  text-align: left;
  cursor: pointer;
}
.recall-note-item:hover { background: var(--kf-card); }
.recall-note-title { font-size: var(--kf-fs-body-sm); font-weight: 600; color: var(--kf-foreground); }
.recall-note-preview {
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.recall-note-empty {
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
  padding: var(--kf-space-2) 0;
}
.recall-lock {
  display: flex;
  align-items: flex-start;
  gap: var(--kf-space-2);
  margin: 0;
  padding: var(--kf-space-3);
  border-radius: var(--kf-radius-md);
  background: color-mix(in srgb, var(--kf-state-warning) 8%, transparent);
  color: var(--kf-state-warning);
  font-size: var(--kf-fs-caption);
  line-height: 1.6;
}
.recall-lock-actions { display: flex; justify-content: flex-end; }
.recall-flow {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--kf-space-3);
}
.recall-flow-card {
  display: flex;
  align-items: flex-start;
  gap: var(--kf-space-2);
  padding: var(--kf-space-3);
  border-radius: var(--kf-radius-md);
  border: 1px solid var(--kf-border);
  background: var(--kf-card);
}
.recall-flow-num {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
  width: 22px;
  height: 22px;
  border-radius: 999px;
  background: var(--kf-primary-soft);
  color: var(--kf-primary);
  font-family: var(--font-mono);
  font-size: var(--kf-fs-caption);
  font-weight: 700;
}
.recall-flow-text { display: flex; flex-direction: column; gap: 2px; }
.recall-flow-name { font-size: var(--kf-fs-caption); font-weight: 600; color: var(--kf-foreground); }
.recall-flow-desc { font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); line-height: 1.5; }
.recall-dialog-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kf-space-3);
  width: 100%;
}
.recall-dirty { font-size: var(--kf-fs-caption); color: var(--kf-state-warning); }
.recall-dialog-actions { display: flex; align-items: center; gap: var(--kf-space-2); margin-left: auto; flex-wrap: wrap; }
.recall-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: var(--kf-space-2) var(--kf-space-4);
  border-radius: var(--kf-radius-md);
  font-size: var(--kf-fs-body-sm);
  font-weight: 600;
  cursor: pointer;
  border: 1px solid transparent;
}
.recall-btn-ghost {
  background: var(--kf-card);
  border-color: var(--kf-border);
  color: var(--kf-foreground);
}
.recall-btn-ghost:hover { background: var(--kf-background); }
.recall-btn-primary {
  background: var(--kf-primary);
  color: var(--kf-primary-foreground);
}
.recall-btn-primary:hover { filter: brightness(0.95); }
.recall-btn:disabled { opacity: 0.55; cursor: not-allowed; }
.recall-btn:focus-visible { outline: 2px solid var(--kf-ring); outline-offset: 2px; }
@media (max-width: 720px) {
  .recall-source-options,
  .recall-flow { grid-template-columns: 1fr; }
}
</style>
