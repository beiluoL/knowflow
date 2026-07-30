<template>
  <div class="knowledge-upload-page">
    <!-- 顶部导航 -->
    <div class="page-header">
      <div class="header-left">
        <button class="back-btn" @click="goBack">
          <Icon name="arrow-left" :size="18" />
          <span>返回知识库</span>
        </button>
      </div>
      <h1 class="page-title">上传文档</h1>
      <div class="header-right"></div>
    </div>

    <div class="upload-layout">
      <!-- ===== 左侧：上传区 + 文件列表 ===== -->
      <div class="upload-left">
        <!-- 拖拽上传区 -->
        <div
          class="upload-zone"
          :class="{ dragging: isDragging, hasfiles: files.length > 0 }"
          @dragover.prevent="isDragging = true"
          @dragleave.prevent="isDragging = false"
          @drop.prevent="handleDrop"
          @click="triggerFileInput"
        >
          <input
            ref="fileInputRef"
            type="file"
            class="hidden-file-input"
            accept=".pdf,.doc,.docx,.md,.markdown,.txt,.ppt,.pptx"
            multiple
            @change="handleFileSelect"
          />
          <div class="upload-zone-icon">
            <Icon name="upload" :size="28" />
          </div>
          <p class="upload-zone-title">
            {{ isDragging ? '释放文件以上传' : '拖拽文件到此处，或点击选择文件' }}
          </p>
          <p class="upload-zone-hint">支持 PDF、Markdown、Word、TXT 等格式，单个文件最大 50 MB</p>
        </div>

        <!-- 已上传文件列表 -->
        <div v-if="files.length > 0" class="file-list-card">
          <div class="file-list-header">
            <h3 class="list-title">已上传文件</h3>
            <span class="file-list-count">{{ files.length }} 个文件</span>
          </div>
          <div class="file-list-body">
            <div v-for="(file, idx) in files" :key="file.id" class="file-item">
              <div class="file-icon" :class="getFileTypeColorClass(file.name)">
                <Icon :name="getFileTypeIcon(file.name)" :size="16" />
              </div>
              <div class="file-info">
                <p class="file-name">{{ file.name }}</p>
                <p class="file-size">{{ formatFileSize(file.size) }}</p>
              </div>
              <div class="file-progress">
                <div class="upload-bar">
                  <div
                    class="upload-bar-fill"
                    :class="getProgressStatusClass(file)"
                    :style="{ width: `${file.progress}%` }"
                  ></div>
                </div>
              </div>
              <span class="file-status" :class="getStatusTextClass(file)">
                {{ getStatusLabel(file) }}
              </span>
              <button class="file-remove-btn" title="移除" @click.stop="removeFile(idx)">
                <Icon name="trash-2" :size="14" />
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- ===== 右侧：上传设置表单 ===== -->
      <aside class="upload-right">
        <h3 class="form-title">上传设置</h3>
        <div class="form-body">
          <!-- 文档标题 -->
          <div class="form-row">
            <label class="form-label">
              文档标题 <span class="required-mark">*</span>
            </label>
            <input
              v-model="formData.title"
              type="text"
              placeholder="请输入文档标题"
              class="form-input"
            />
            <p class="form-hint">默认取第一个文件名，可手动修改</p>
          </div>

          <!-- 目标知识库 -->
          <div class="form-row">
            <label class="form-label">
              目标知识库 <span class="required-mark">*</span>
            </label>
            <select v-model="formData.kbId" class="form-select">
              <option value="" disabled>请选择知识库</option>
              <option v-for="cat in kbCategories" :key="cat.id" :value="cat.id">
                {{ cat.name }}
              </option>
            </select>
          </div>

          <!-- 分类 -->
          <div class="form-row">
            <label class="form-label">
              分类 <span class="required-mark">*</span>
            </label>
            <select v-model="formData.categoryId" class="form-select" :disabled="!formData.kbId">
              <option value="" disabled>{{ formData.kbId ? '请选择分类' : '请先选择知识库' }}</option>
              <option v-for="cat in filteredSubCategories" :key="cat.id" :value="cat.id">
                {{ cat.name }}
              </option>
            </select>
            <button v-if="formData.kbId && filteredSubCategories.length === 0" class="create-category-link" @click="createSubCategory">
              <Icon name="plus" :size="12" />
              <span>新建子分类</span>
            </button>
          </div>

          <!-- 标签 -->
          <div class="form-row">
            <label class="form-label">标签</label>
            <div class="tags-input">
              <span v-for="(tag, idx) in formData.tags" :key="idx" class="tag-chip">
                {{ tag }}
                <button type="button" class="tag-remove" @click="removeTag(idx)">
                  <Icon name="x" :size="12" />
                </button>
              </span>
              <input
                v-model="tagInput"
                type="text"
                placeholder="添加标签后回车"
                class="tag-text-input"
                @keydown.enter.prevent="addTag"
              />
            </div>
          </div>

          <!-- 描述 -->
          <div class="form-row">
            <label class="form-label">描述</label>
            <textarea
              v-model="formData.description"
              rows="3"
              placeholder="请输入文档描述..."
              class="form-textarea"
            ></textarea>
          </div>
        </div>

        <!-- 底部操作按钮 -->
        <div class="form-actions">
          <button type="button" class="btn-secondary" @click="handleCancel">
            <span>取消</span>
          </button>
          <button
            type="button"
            class="btn-primary"
            :disabled="!canSubmit || uploading"
            @click="handleUpload"
          >
            <Icon name="upload" :size="14" />
            <span>{{ uploading ? '上传中...' : '开始上传' }}</span>
          </button>
        </div>
      </aside>
    </div>

    <!-- 新建分类弹窗 -->
    <div v-if="showCategoryModal" class="modal-overlay" @click.self="showCategoryModal = false">
      <div class="modal-content">
        <h3 class="modal-title">新建子分类</h3>
        <div class="form-row">
          <label class="form-label">分类名称</label>
          <input v-model="newCategoryName" type="text" placeholder="请输入分类名称" class="form-input" />
        </div>
        <div class="form-row">
          <label class="form-label">分类图标</label>
          <div class="icon-grid">
            <div
              v-for="icon in availableIcons"
              :key="icon"
              class="icon-item"
              :class="{ active: newCategoryIcon === icon }"
              @click="newCategoryIcon = icon"
            >
              <Icon :name="icon" :size="18" />
            </div>
          </div>
        </div>
        <div class="modal-actions">
          <button class="btn-secondary" @click="showCategoryModal = false">取消</button>
          <button class="btn-primary" @click="confirmCreateCategory">确定</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { categoriesApi, docsApi } from '@/api'
import type { CategoryVO } from '@/api/types'
import { notify, getApiError } from '@/utils/toast'

const router = useRouter()

interface UploadFile {
  id: string
  file: File
  name: string
  size: number
  progress: number
  status: 'pending' | 'uploading' | 'done' | 'error'
}

const fileInputRef = ref<HTMLInputElement | null>(null)
const isDragging = ref(false)
const files = ref<UploadFile[]>([])
const uploading = ref(false)
const tagInput = ref('')
const kbCategories = ref<CategoryVO[]>([])

const formData = ref({
  title: '',
  kbId: '' as number | string,
  categoryId: '' as number | string,
  tags: [] as string[],
  description: '',
})

// 新建分类弹窗
const showCategoryModal = ref(false)
const newCategoryName = ref('')
const newCategoryIcon = ref('folder')

const availableIcons = [
  'folder', 'code', 'server', 'database', 'brain', 'layout',
  'palette', 'container', 'shield', 'binary', 'book-open', 'cpu',
  'target', 'bar-chart-2', 'briefcase', 'layers', 'file-code',
]

const canSubmit = computed(() => {
  return (
    files.value.length > 0 &&
    formData.value.title.trim() !== '' &&
    formData.value.kbId !== '' &&
    formData.value.categoryId !== ''
  )
})

const filteredSubCategories = computed(() => {
  if (!formData.value.kbId) return []
  const kb = kbCategories.value.find(c => c.id === Number(formData.value.kbId))
  return kb?.children || []
})

function triggerFileInput() {
  fileInputRef.value?.click()
}

function handleFileSelect(e: Event) {
  const target = e.target as HTMLInputElement
  if (target.files && target.files.length > 0) {
    appendFiles(Array.from(target.files))
    target.value = ''
  }
}

function handleDrop(e: DragEvent) {
  isDragging.value = false
  if (e.dataTransfer?.files && e.dataTransfer.files.length > 0) {
    appendFiles(Array.from(e.dataTransfer.files))
  }
}

function appendFiles(fileList: File[]) {
  const MAX_SIZE = 50 * 1024 * 1024
  for (const f of fileList) {
    if (f.size > MAX_SIZE) {
      notify(`${f.name} 超过 50 MB 限制`, 'warning')
      continue
    }
    if (files.value.some((x) => x.name === f.name && x.size === f.size)) {
      continue
    }
    files.value.push({
      id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      file: f,
      name: f.name,
      size: f.size,
      progress: 0,
      status: 'pending',
    })
  }
  if (!formData.value.title && files.value.length > 0) {
    const firstName = files.value[0].name
    formData.value.title = firstName.substring(0, firstName.lastIndexOf('.')) || firstName
  }
}

function removeFile(idx: number) {
  files.value.splice(idx, 1)
  if (files.value.length === 0) {
    formData.value.title = ''
  }
}

function addTag() {
  const tag = tagInput.value.trim()
  if (tag && !formData.value.tags.includes(tag)) {
    formData.value.tags.push(tag)
  }
  tagInput.value = ''
}

function removeTag(idx: number) {
  formData.value.tags.splice(idx, 1)
}

function getFileTypeIcon(filename: string): string {
  const ext = filename.split('.').pop()?.toLowerCase()
  if (ext === 'pdf') return 'file-text'
  if (ext === 'doc' || ext === 'docx') return 'file-text'
  if (ext === 'md' || ext === 'markdown') return 'file-text'
  if (ext === 'txt') return 'file-text'
  if (ext === 'ppt' || ext === 'pptx') return 'file-text'
  return 'file'
}

function getFileTypeColorClass(filename: string): string {
  const ext = filename.split('.').pop()?.toLowerCase()
  if (ext === 'pdf') return 'type-pdf'
  if (ext === 'md' || ext === 'markdown') return 'type-md'
  if (ext === 'doc' || ext === 'docx') return 'type-doc'
  if (ext === 'ppt' || ext === 'pptx') return 'type-ppt'
  return 'type-default'
}

function getProgressStatusClass(file: UploadFile): string {
  if (file.status === 'done') return 'fill-success'
  if (file.status === 'error') return 'fill-error'
  return 'fill-primary'
}

function getStatusLabel(file: UploadFile): string {
  if (file.status === 'done') return '完成'
  if (file.status === 'error') return '失败'
  if (file.status === 'uploading') return `${file.progress}%`
  return '等待中'
}

function getStatusTextClass(file: UploadFile): string {
  if (file.status === 'done') return 'status-success'
  if (file.status === 'error') return 'status-error'
  return 'status-muted'
}

function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i]
}

async function handleUpload(): Promise<void> {
  if (!canSubmit.value || uploading.value) return
  uploading.value = true

  try {
    for (const uploadFile of files.value) {
      uploadFile.status = 'uploading'
      uploadFile.progress = 10

      const titleForThis = files.value.length === 1
        ? formData.value.title.trim()
        : (uploadFile.name.substring(0, uploadFile.name.lastIndexOf('.')) || uploadFile.name)

      try {
        await docsApi.upload(
          uploadFile.file,
          {
            title: titleForThis,
            summary: formData.value.description,
            categoryId: Number(formData.value.categoryId),
            tags: formData.value.tags.join(','),
            status: 1,
          },
          (percent) => {
            uploadFile.progress = 10 + Math.round((percent / 100) * 90)
          }
        )

        uploadFile.progress = 100
        uploadFile.status = 'done'
      } catch (e) {
        uploadFile.status = 'error'
        uploadFile.progress = 100
        notify(`${uploadFile.name} 上传失败：${getApiError(e, '请稍后再试')}`, 'error')
      }
    }

    const hasError = files.value.some((f) => f.status === 'error')
    if (!hasError) {
      notify('文档上传成功！', 'success')
      setTimeout(() => {
        router.push('/knowledge')
      }, 1500)
    } else {
      notify('部分文件上传失败，请重试', 'warning')
    }
  } finally {
    uploading.value = false
  }
}

function handleCancel(): void {
  files.value = []
  formData.value = { title: '', kbId: '', categoryId: '', tags: [], description: '' }
  if (fileInputRef.value) {
    fileInputRef.value.value = ''
  }
  router.push('/knowledge')
}

function goBack(): void {
  router.push('/knowledge')
}

async function createSubCategory(): Promise<void> {
  newCategoryName.value = ''
  newCategoryIcon.value = 'folder'
  showCategoryModal.value = true
}

async function confirmCreateCategory(): Promise<void> {
  if (!newCategoryName.value.trim()) {
    notify('请输入分类名称', 'warning')
    return
  }
  try {
    const kbId = Number(formData.value.kbId)
    const newCat = await categoriesApi.create({
      name: newCategoryName.value.trim(),
      parentId: kbId,
      icon: newCategoryIcon.value,
    })
    notify('分类创建成功', 'success')
    showCategoryModal.value = false
    
    // 重新加载分类列表
    await loadCategories()
    formData.value.categoryId = newCat.id
  } catch (e) {
    notify(`创建失败：${getApiError(e, '请稍后再试')}`, 'error')
  }
}

async function loadCategories(): Promise<void> {
  try {
    const tree = await categoriesApi.tree()
    kbCategories.value = tree || []
  } catch (e) {
    notify(`加载分类失败：${getApiError(e)}`, 'error')
    kbCategories.value = []
  }
}

onMounted(loadCategories)
</script>

<style scoped>
.knowledge-upload-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 24px 40px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 0;
  margin-bottom: 8px;
}

.header-left,
.header-right {
  width: 120px;
}

.header-right {
  display: flex;
  justify-content: flex-end;
}

.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: 8px;
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-foreground);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}

.back-btn:hover {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
}

.page-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--kb-foreground);
  margin: 0;
}

.upload-layout {
  display: grid;
  grid-template-columns: 1fr 360px;
  gap: 24px;
  align-items: flex-start;
}

@media (max-width: 1024px) {
  .upload-layout {
    grid-template-columns: 1fr;
  }
}

.upload-left {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.upload-zone {
  border: 2px dashed var(--kb-border);
  border-radius: 16px;
  padding: 48px 24px;
  text-align: center;
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s;
}

.upload-zone:hover {
  border-color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.03);
}

.upload-zone.dragging {
  border-color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.06);
}

.upload-zone-icon {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 16px;
  background: rgba(59, 111, 224, 0.08);
  color: var(--kb-primary);
}

.upload-zone-title {
  font-size: 15px;
  font-weight: 500;
  color: var(--kb-foreground);
  margin-bottom: 4px;
}

.upload-zone-hint {
  font-size: 13px;
  color: var(--kb-muted-foreground);
}

.hidden-file-input {
  display: none;
}

.file-list-card {
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: 14px;
  overflow: hidden;
}

.file-list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid var(--kb-border);
}

.list-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--kb-foreground);
  margin: 0;
}

.file-list-count {
  font-size: 12px;
  color: var(--kb-muted-foreground);
}

.file-list-body {
  display: flex;
  flex-direction: column;
}

.file-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--kb-border);
}

.file-item:last-child {
  border-bottom: none;
}

.file-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.type-pdf { background: rgba(239, 68, 68, 0.1); color: #EF4444; }
.type-md { background: rgba(16, 185, 129, 0.1); color: #10B981; }
.type-doc { background: rgba(59, 111, 224, 0.08); color: #3B6FE0; }
.type-ppt { background: rgba(245, 158, 11, 0.1); color: #F59E0B; }
.type-default { background: rgba(107, 114, 128, 0.1); color: #6B7280; }

.file-info {
  flex: 1;
  min-width: 0;
}

.file-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--kb-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin: 0;
}

.file-size {
  font-size: 12px;
  color: var(--kb-muted-foreground);
  margin-top: 2px;
  margin: 0;
}

.file-progress {
  width: 128px;
  flex-shrink: 0;
}

.upload-bar {
  height: 6px;
  border-radius: 3px;
  background: var(--kb-muted);
  overflow: hidden;
}

.upload-bar-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 0.3s;
}

.fill-primary { background: var(--kb-primary); }
.fill-success { background: #10B981; }
.fill-error { background: #EF4444; }

.file-status {
  font-size: 12px;
  flex-shrink: 0;
  min-width: 48px;
  text-align: right;
}

.status-success { color: #10B981; }
.status-error { color: #EF4444; }
.status-muted { color: var(--kb-muted-foreground); }

.file-remove-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: none;
  cursor: pointer;
  color: var(--kb-muted-foreground);
  border-radius: 6px;
  flex-shrink: 0;
  transition: all 0.15s;
}

.file-remove-btn:hover {
  background: rgba(239, 68, 68, 0.08);
  color: #EF4444;
}

.upload-right {
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: 16px;
  padding: 20px;
  position: sticky;
  top: 80px;
}

.form-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--kb-foreground);
  margin: 0 0 20px;
}

.form-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-row {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--kb-foreground);
}

.required-mark {
  color: #EF4444;
}

.form-hint {
  font-size: 11px;
  color: var(--kb-muted-foreground);
  margin: 0;
}

.form-input,
.form-select {
  width: 100%;
  height: 38px;
  padding: 0 12px;
  border-radius: 8px;
  font-size: 13px;
  background: var(--kb-background);
  color: var(--kb-foreground);
  border: 1px solid var(--kb-border);
  outline: none;
  transition: border-color 0.15s;
  font-family: inherit;
}

.form-select {
  cursor: pointer;
}

.form-input:focus,
.form-select:focus {
  border-color: var(--kb-primary);
}

.form-select:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.form-textarea {
  width: 100%;
  padding: 8px 12px;
  border-radius: 8px;
  font-size: 13px;
  background: var(--kb-background);
  color: var(--kb-foreground);
  border: 1px solid var(--kb-border);
  outline: none;
  resize: none;
  transition: border-color 0.15s;
  font-family: inherit;
}

.form-textarea:focus {
  border-color: var(--kb-primary);
}

.create-category-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  border-radius: 6px;
  border: 1px dashed var(--kb-border);
  background: transparent;
  color: var(--kb-primary);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
  align-self: flex-start;
}

.create-category-link:hover {
  background: rgba(59, 111, 224, 0.06);
  border-color: var(--kb-primary);
}

.tags-input {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 8px 10px;
  border-radius: 8px;
  background: var(--kb-background);
  border: 1px solid var(--kb-border);
  min-height: 38px;
}

.tag-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 24px;
  padding: 0 10px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
  background: rgba(59, 111, 224, 0.1);
  color: var(--kb-primary);
}

.tag-remove {
  background: transparent;
  border: none;
  cursor: pointer;
  color: var(--kb-primary);
  padding: 0;
  display: inline-flex;
  align-items: center;
  opacity: 0.7;
  transition: opacity 0.15s;
}

.tag-remove:hover {
  opacity: 1;
}

.tag-text-input {
  flex: 1;
  min-width: 80px;
  height: 24px;
  font-size: 13px;
  background: transparent;
  border: none;
  outline: none;
  color: var(--kb-foreground);
  font-family: inherit;
}

.tag-text-input::placeholder {
  color: var(--kb-muted-foreground);
}

.form-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 20px;
}

.btn-primary,
.btn-secondary {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 38px;
  padding: 0 16px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
  border: none;
  font-family: inherit;
}

.btn-primary {
  background: var(--kb-primary);
  color: white;
}

.btn-primary:hover:not(:disabled) {
  opacity: 0.9;
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-secondary {
  background: var(--kb-background);
  color: var(--kb-foreground);
  border: 1px solid var(--kb-border);
}

.btn-secondary:hover {
  background: var(--kb-muted);
}

/* 新建分类弹窗 */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}

.modal-content {
  background: var(--kb-card);
  border-radius: 16px;
  padding: 24px;
  width: 400px;
  max-width: 90%;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.15);
}

.modal-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--kb-foreground);
  margin: 0 0 16px;
}

.icon-grid {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 8px;
}

.icon-item {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  cursor: pointer;
  background: var(--kb-background);
  border: 1px solid transparent;
  transition: all 0.15s;
}

.icon-item:hover {
  background: var(--kb-muted);
}

.icon-item.active {
  background: rgba(59, 111, 224, 0.1);
  border-color: var(--kb-primary);
  color: var(--kb-primary);
}

.modal-actions {
  display: flex;
  gap: 12px;
  margin-top: 20px;
}

.modal-actions .btn-secondary,
.modal-actions .btn-primary {
  flex: 1;
}

@media (max-width: 768px) {
  .file-item {
    flex-wrap: wrap;
  }
  .file-progress {
    width: 100%;
    order: 3;
  }
  .file-status {
    order: 4;
    min-width: auto;
  }
  .file-remove-btn {
    order: 5;
  }
  .page-header {
    flex-direction: column;
    gap: 12px;
  }
  .header-left,
  .header-right {
    width: auto;
  }
}
</style>
