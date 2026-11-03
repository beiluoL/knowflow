<template>
  <section class="resume-upload" aria-label="上传简历">
    <div
      class="resume-upload-drop"
      :class="{ 'is-dragging': dragging, 'is-disabled': uploading }"
      @dragover.prevent="dragging = true"
      @dragleave.prevent="dragging = false"
      @drop.prevent="onDrop"
    >
      <span class="resume-upload-icon"><Icon name="upload" :size="22" /></span>
      <p class="resume-upload-title">拖拽简历文件到这里，或选择本地文件</p>
      <p class="resume-upload-hint">支持可复制文本的 PDF、DOC、DOCX、UTF-8 TXT，最大 5MB</p>
      <input
        ref="inputRef"
        type="file"
        class="resume-upload-input"
        accept=".pdf,.doc,.docx,.txt"
        :disabled="uploading"
        @change="onChange"
      />
      <div class="resume-upload-actions">
        <Button variant="secondary" size="sm" :disabled="uploading" @click="chooseFile">
          <Icon name="folder-open" :size="14" />
          <span>选择文件</span>
        </Button>
        <Button
          v-if="selected"
          size="sm"
          :disabled="uploading || progress >= 100"
          :loading="uploading"
          @click="upload"
        >
          <Icon v-if="!uploading" name="upload" :size="14" />
          <span>{{ uploading ? '上传并提取中…' : '上传并提取原文' }}</span>
        </Button>
      </div>
      <p v-if="selected" class="resume-upload-file">
        {{ selected.name }} · {{ fileSize(selected.size) }}
      </p>
      <div v-if="uploading || progress > 0" class="resume-upload-progress" role="status" aria-live="polite">
        <div class="resume-upload-track"><span :style="{ width: `${progress}%` }" /></div>
        <p>{{ !uploading ? (importWarning ? '原文件已保存，请检查正文' : '正文提取完成') : progress >= 100 ? '文件已提交，正在提取文本…' : `已上传 ${progress}%` }}</p>
      </div>
      <p v-if="importWarning" class="resume-upload-error" role="alert">{{ importWarning }}</p>
      <p v-if="localError" class="resume-upload-error" role="alert">{{ localError }}</p>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import Button from '@/components/ui/Button.vue'
import Icon from '@/components/ui/Icon.vue'
import { getApiError } from '@/utils/toast'
import { uploadResume } from '@/api/career/resume'
import type { CareerRecord } from '@/api/career/types'

const emit = defineEmits<{ uploaded: [record: CareerRecord] }>()

const inputRef = ref<HTMLInputElement | null>(null)
const dragging = ref(false)
const selected = ref<File | null>(null)
const uploading = ref(false)
const progress = ref(0)
const localError = ref('')
const importWarning = ref('')

const MAX_BYTES = 5 * 1024 * 1024
const ALLOWED = ['pdf', 'doc', 'docx', 'txt']

function chooseFile() {
  inputRef.value?.click()
}

function validate(file: File): string {
  const extension = file.name.split('.').pop()?.toLowerCase() ?? ''
  if (!ALLOWED.includes(extension)) return '只支持 PDF、DOC、DOCX 或 UTF-8 TXT 文件'
  if (file.size <= 0) return '文件为空，请重新选择'
  if (file.size > MAX_BYTES) return '文件超过 5MB，请压缩或导出后再上传'
  return ''
}

function pick(file: File | undefined) {
  if (!file || uploading.value) return
  importWarning.value = ''
  const message = validate(file)
  localError.value = message
  selected.value = message ? null : file
  progress.value = 0
}

function onChange(event: Event) {
  pick((event.target as HTMLInputElement).files?.[0])
}

function onDrop(event: DragEvent) {
  dragging.value = false
  pick(event.dataTransfer?.files?.[0])
}

async function upload() {
  if (!selected.value || uploading.value) return
  uploading.value = true
  progress.value = 0
  localError.value = ''
  try {
    const record = await uploadResume(selected.value, value => {
      progress.value = value
    })
    progress.value = 100
    selected.value = null
    importWarning.value = record.importWarning ?? ''
    emit('uploaded', record)
  } catch (e: unknown) {
    progress.value = 0
    localError.value = getApiError(e, '上传或文本提取失败，请重试')
  } finally {
    uploading.value = false
  }
}

function fileSize(size: number): string {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}
</script>

<style scoped>
.resume-upload-drop {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--kf-space-2);
  padding: var(--kf-space-6);
  border: 1px dashed var(--kf-border);
  border-radius: var(--kf-radius-md);
  background: var(--kf-surface-1);
  text-align: center;
  transition: border-color var(--kf-motion-fast) var(--kf-ease-standard),
    background-color var(--kf-motion-fast) var(--kf-ease-standard);
}
.resume-upload-drop.is-dragging {
  border-color: var(--kf-primary);
  background: var(--kf-primary-subtle);
}
.resume-upload-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: var(--kf-radius-full);
  background: var(--kf-primary-subtle);
  color: var(--kf-primary);
}
.resume-upload-title {
  margin: 0;
  font-size: var(--kf-fs-body-emphasis);
  line-height: var(--kf-lh-body-emphasis);
  color: var(--kf-foreground);
}
.resume-upload-hint,
.resume-upload-file {
  margin: 0;
  font-size: var(--kf-fs-body-sm);
  color: var(--kf-muted-foreground);
}
.resume-upload-input {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
  pointer-events: none;
}
.resume-upload-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: var(--kf-space-2);
  margin-top: var(--kf-space-2);
}
.resume-upload-progress {
  width: min(360px, 100%);
  margin-top: var(--kf-space-2);
}
.resume-upload-track {
  height: 6px;
  border-radius: var(--kf-radius-full);
  background: var(--kf-surface-2);
  overflow: hidden;
}
.resume-upload-track span {
  display: block;
  height: 100%;
  background: var(--kf-primary);
  transition: width var(--kf-motion-base) var(--kf-ease-standard);
}
.resume-upload-progress p {
  margin: var(--kf-space-1) 0 0;
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.resume-upload-error {
  margin: 0;
  color: var(--kf-state-error);
  font-size: var(--kf-fs-body-sm);
}
</style>
