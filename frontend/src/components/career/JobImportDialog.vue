<template>
  <AppDialog
    :open="open"
    title="导入岗位 JD"
    description="导入后会先保存为待确认资料；AI 提取的岗位要求必须人工核对后才能参与匹配。"
    width="680px"
    scrollable
    hide-footer
    @cancel="close"
  >
    <div class="job-import">
      <nav class="job-import-tabs" role="tablist" aria-label="JD 导入方式">
        <button
          v-for="item in tabs"
          :key="item.key"
          type="button"
          role="tab"
          class="job-import-tab"
          :class="{ 'is-active': mode === item.key }"
          :aria-selected="mode === item.key"
          @click="mode = item.key; error = ''"
        >
          {{ item.label }}
        </button>
      </nav>

      <section v-if="mode === 'paste'" class="job-import-panel">
        <label class="job-import-field">
          <span>岗位名称</span>
          <Input v-model="title" :maxlength="160" placeholder="例如 高级 Java 工程师 · 深圳" aria-label="岗位名称" />
        </label>
        <label class="job-import-field">
          <span>JD 原文</span>
          <Textarea v-model="source" :rows="10" :maxlength="50000" placeholder="粘贴岗位职责、必备技能、优先技能与任职要求。" aria-label="JD 原文" />
        </label>
        <p class="kf-text-caption">{{ source.length }} / 50000 字</p>
      </section>

      <section v-else-if="mode === 'upload'" class="job-import-panel">
        <div class="job-import-drop">
          <Icon name="upload" :size="22" class="job-import-drop-icon" />
          <p>选择 JD 文件（PDF / DOCX / UTF-8 TXT，最大 5MB）</p>
          <input ref="fileInput" type="file" accept=".pdf,.docx,.txt" class="job-import-file" @change="onFileChange" />
          <Button variant="secondary" size="sm" :disabled="busy" @click="fileInput?.click()">
            <Icon name="folder-open" :size="14" /><span>选择文件</span>
          </Button>
          <p v-if="selectedFile" class="kf-text-caption">{{ selectedFile.name }} · {{ selectedFile.size }} 字节</p>
          <div v-if="uploadProgress > 0" class="job-import-progress" role="status">
            <div class="job-import-track"><span :style="{ width: `${uploadProgress}%` }" /></div>
            <p>{{ uploadProgress >= 100 ? '正在提取 JD 文本…' : `已上传 ${uploadProgress}%` }}</p>
          </div>
        </div>
      </section>

      <section v-else class="job-import-panel">
        <label class="job-import-field">
          <span>公开岗位链接</span>
          <Input v-model="url" :maxlength="2048" placeholder="https://..." aria-label="公开岗位链接" />
        </label>
        <p class="kf-text-caption">
          只抓取公开可访问页面，不绕过登录、反爬或访问限制；失败时请切换为手动粘贴。
        </p>
      </section>

      <p v-if="error" class="job-import-error" role="alert">{{ error }}</p>

      <div class="job-import-actions">
        <Button variant="secondary" size="sm" :disabled="busy" @click="close">取消</Button>
        <Button
          v-if="mode === 'paste'"
          size="sm"
          :disabled="busy || !title.trim() || !source.trim()"
          :loading="busy"
          @click="createFromPaste"
        >
          <Icon v-if="!busy" name="plus" :size="14" /><span>保存并继续核对</span>
        </Button>
        <Button
          v-else-if="mode === 'upload'"
          size="sm"
          :disabled="busy || !selectedFile"
          :loading="busy"
          @click="uploadFile"
        >
          <Icon v-if="!busy" name="upload" :size="14" /><span>上传并提取</span>
        </Button>
        <Button
          v-else
          size="sm"
          :disabled="busy || !url.trim()"
          :loading="busy"
          @click="importUrl"
        >
          <Icon v-if="!busy" name="link" :size="14" /><span>抓取链接文本</span>
        </Button>
      </div>
    </div>
  </AppDialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import AppDialog from '@/components/ui/AppDialog.vue'
import Button from '@/components/ui/Button.vue'
import Icon from '@/components/ui/Icon.vue'
import Input from '@/components/ui/Input.vue'
import Textarea from '@/components/ui/Textarea.vue'
import { getApiError } from '@/utils/toast'
import { createJob, importJobByUrl, uploadJob } from '@/api/career/job'
import type { CareerRecord } from '@/api/career/types'

defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: []; created: [record: CareerRecord] }>()

const tabs = [
  { key: 'paste', label: '粘贴 JD' },
  { key: 'upload', label: '上传文件' },
  { key: 'link', label: '链接导入' },
] as const
const mode = ref<'paste' | 'upload' | 'link'>('paste')
const title = ref('')
const source = ref('')
const url = ref('')
const selectedFile = ref<File | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const uploadProgress = ref(0)
const busy = ref(false)
const error = ref('')

function close(): void {
  if (busy.value) return
  emit('close')
}

function onFileChange(event: Event): void {
  selectedFile.value = (event.target as HTMLInputElement).files?.[0] ?? null
  uploadProgress.value = 0
  error.value = ''
}

async function createFromPaste(): Promise<void> {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    const record = await createJob({ title: title.value.trim(), source: source.value, sections: {} })
    emit('created', record)
  } catch (e: unknown) {
    error.value = getApiError(e, '岗位保存失败，请重试')
  } finally {
    busy.value = false
  }
}

async function uploadFile(): Promise<void> {
  if (busy.value || !selectedFile.value) return
  busy.value = true
  error.value = ''
  uploadProgress.value = 0
  try {
    const record = await uploadJob(selectedFile.value, value => {
      uploadProgress.value = value
    })
    uploadProgress.value = 100
    emit('created', record)
  } catch (e: unknown) {
    error.value = getApiError(e, 'JD 文件上传失败，请重试或改用粘贴')
  } finally {
    busy.value = false
  }
}

async function importUrl(): Promise<void> {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    const record = await importJobByUrl(url.value.trim())
    emit('created', record)
  } catch (e: unknown) {
    error.value = getApiError(e, '岗位链接无法解析，请手动粘贴 JD')
  } finally {
    busy.value = false
  }
}
</script>

<style scoped>
.job-import { display: flex; flex-direction: column; gap: var(--kf-space-3); }
.job-import-tabs { display: flex; gap: var(--kf-space-1); padding: 3px; border-radius: var(--kf-radius-sm); background: var(--kf-surface-2); }
.job-import-tab { flex: 1; min-height: 32px; border: 0; border-radius: var(--kf-radius-xs); background: transparent; color: var(--kf-muted-foreground); font-size: var(--kf-fs-body-sm); cursor: pointer; }
.job-import-tab.is-active { background: var(--kf-surface-1); color: var(--kf-foreground); box-shadow: var(--kf-shadow-sm); }
.job-import-tab:focus-visible { outline: 2px solid var(--kf-primary); outline-offset: 1px; }
.job-import-panel { display: flex; flex-direction: column; gap: var(--kf-space-2); }
.job-import-field { display: flex; flex-direction: column; gap: var(--kf-space-1); font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
.job-import-drop { display: flex; flex-direction: column; align-items: center; gap: var(--kf-space-2); padding: var(--kf-space-5); border: 1px dashed var(--kf-border); border-radius: var(--kf-radius-sm); text-align: center; font-size: var(--kf-fs-body-sm); color: var(--kf-muted-foreground); }
.job-import-drop-icon { color: var(--kf-primary); }
.job-import-file { position: absolute; width: 1px; height: 1px; opacity: 0; pointer-events: none; }
.job-import-progress { width: min(320px, 100%); }
.job-import-track { height: 6px; border-radius: var(--kf-radius-full); background: var(--kf-surface-2); overflow: hidden; }
.job-import-track span { display: block; height: 100%; background: var(--kf-primary); }
.job-import-error { margin: 0; color: var(--kf-state-error); font-size: var(--kf-fs-body-sm); }
.job-import-actions { display: flex; justify-content: flex-end; gap: var(--kf-space-2); flex-wrap: wrap; }
</style>
