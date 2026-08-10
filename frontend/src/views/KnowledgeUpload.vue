<template>
  <div class="knowledge-upload-page">
    <!-- 顶部导航 -->
    <div class="page-header">
      <div class="header-left">
        <button type="button" class="back-btn" @click="goBack">
          <Icon name="arrow-left" :size="16" />
          <span>返回知识库</span>
        </button>
      </div>
      <h1 class="page-title">上传文档</h1>
      <div class="header-right"></div>
    </div>

    <div class="upload-layout">
      <!-- ===== 左侧：上传区 + 文件列表 ===== -->
      <div class="upload-left">
        <!-- 整体导入进度条（在路径导入模式下显示） -->
        <div
          v-if="importMode === 'path' && (pathImporting || pathScanResult) && progressTotal > 0"
          class="global-progress-card"
        >
          <div class="global-progress-header">
            <span class="global-progress-title">
              <Icon name="database-import" :size="14" />
              导入进度
            </span>
            <span class="global-progress-count tabular-nums">
              {{ progressCurrent }} / {{ progressTotal }}（{{ overallProgressPercent }}%）
            </span>
          </div>
          <div class="global-progress-bar">
            <div
              class="global-progress-fill"
              :style="{ width: overallProgressPercent + '%' }"
            ></div>
          </div>
          <p v-if="progressCurrentFile" class="global-progress-file">
            正在处理：{{ progressCurrentFile }}
          </p>
        </div>

        <!-- 导入方式 Tab：上传文件 / 路径导入 -->
        <div class="import-mode-tabs">
          <button
            type="button"
            class="mode-tab"
            :class="{ active: importMode === 'file' }"
            :disabled="uploading || pathImporting"
            @click="switchImportMode('file')"
          >
            <Icon name="upload" :size="16" />
            <span>上传文件</span>
          </button>
          <button
            type="button"
            class="mode-tab"
            :class="{ active: importMode === 'path' }"
            :disabled="uploading || pathImporting"
            @click="switchImportMode('path')"
          >
            <Icon name="terminal" :size="16" />
            <span>路径导入</span>
          </button>
        </div>

        <!-- 模式 A：上传文件（拖拽 / 选择） -->
        <template v-if="importMode === 'file'">
          <div
            class="upload-zone"
            :class="{ dragging: isDragging, hasfiles: files.length > 0 }"
            role="button"
            tabindex="0"
            aria-label="拖拽文件到此处，或点击选择文件"
            @dragover.prevent="isDragging = true"
            @dragleave.prevent="isDragging = false"
            @drop.prevent="handleDrop"
            @click="triggerFileInput"
            @keydown.enter.prevent="($event.target as HTMLElement).click()"
          >
            <input
              ref="fileInputRef"
              type="file"
              class="hidden-file-input"
              :accept="UPLOAD_ACCEPT"
              multiple
              @change="handleFileSelect"
            />
            <div class="upload-zone-icon">
              <Icon name="upload" :size="24" />
            </div>
            <p class="upload-zone-title">
              {{ isDragging ? '释放文件以上传' : '拖拽文件到此处，或点击选择文件' }}
            </p>
            <p class="upload-zone-hint">
              支持 PDF、Markdown、Word、代码文件（.java/.py/.vue/.js 等），单个文件最大 50 MB
            </p>
          </div>

          <!-- 单文件模式的文件列表 -->
          <div v-if="files.length > 0" class="file-list-card">
            <div class="file-list-header">
              <h3 class="list-title">待上传文件</h3>
              <span class="file-list-count tabular-nums">{{ files.length }} 个文件</span>
            </div>
            <div class="file-list-body">
              <div v-for="(file, idx) in files" :key="file.id" class="file-item">
                <div class="file-icon" :class="getFileTypeColorClass(file.name)">
                  <Icon :name="getFileTypeIcon(file.name)" :size="16" />
                </div>
                <div class="file-info">
                  <p class="file-name">{{ file.name }}</p>
                  <p class="file-size tabular-nums">{{ formatFileSize(file.size) }}</p>
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
                <span class="file-status tabular-nums" :class="getStatusTextClass(file)">
                  {{ getStatusLabel(file) }}
                </span>
                <button type="button" class="file-remove-btn" title="移除" @click.stop="removeFile(idx)">
                  <Icon name="trash-2" :size="14" />
                </button>
              </div>
            </div>
          </div>
        </template>

        <!-- 模式 B：路径输入（绝对路径 / 相对路径 / 单文件） -->
        <template v-if="importMode === 'path'">
          <div class="path-input-zone">
            <div class="path-input-row" :class="{ scanning: pathScanning }">
              <div class="path-input-icon">
                <Icon name="terminal" :size="20" />
              </div>
              <input
                v-model="pathInput"
                type="text"
                class="path-input"
                placeholder="输入绝对路径（如 /Users/xxx/docs）或相对路径（如 ./src），也可指定单个文件"
                :disabled="pathImporting"
                @keyup.enter="scanPath"
              />
              <button
                type="button"
                class="btn-scan"
                :disabled="pathScanning || !pathInput.trim() || pathImporting"
                @click="scanPath"
              >
                <Icon :name="pathScanning ? 'loader' : 'search'" :size="16" :class="{ 'spin-icon': pathScanning }" />
                <span>{{ pathScanning ? '扫描中...' : '扫描' }}</span>
              </button>
            </div>
            <p class="path-input-hint">
              校验路径有效性并展示文件列表（含文件名、大小、类型），确认无误后再导入。
              支持目录导入或单文件导入。
            </p>
          </div>

          <!-- 扫描结果文件列表 -->
          <div v-if="pathScanResult" class="file-list-card">
            <div class="file-list-header">
              <h3 class="list-title">
                扫描结果
                <span class="scan-mode-tag">
                  {{ pathScanResult.isFile ? '单文件' : '目录' }}
                </span>
              </h3>
              <span class="file-list-count tabular-nums">
                {{ pathScanResult.docCount }} 文档 /
                {{ pathScanResult.imageCount }} 图片 /
                {{ pathScanResult.dirCount }} 目录
              </span>
            </div>
            <div class="file-list-body path-file-list">
              <div
                v-for="(entry, idx) in displayPathFiles"
                :key="entry.path"
                class="file-item"
              >
                <div
                  class="file-icon"
                  :class="entry.type === 'doc' ? 'type-md' : 'type-default'"
                >
                  <Icon :name="getFileIconByExt(entry.ext, entry.type)" :size="16" />
                </div>
                <div class="file-info">
                  <p class="file-name" :title="entry.path">{{ entry.name }}</p>
                  <p class="file-size tabular-nums" :title="entry.path">
                    {{ entry.path }} · {{ formatFileSize(entry.size) }}
                  </p>
                </div>
                <div class="file-progress">
                  <div class="upload-bar">
                    <div
                      class="upload-bar-fill"
                      :class="getPathFileProgressClass(entry, idx)"
                      :style="{ width: getPathFileProgress(entry) + '%' }"
                    ></div>
                  </div>
                </div>
                <span class="file-status" :class="getPathFileStatusClass(entry, idx)">
                  {{ getPathFileStatusLabel(entry, idx) }}
                </span>
              </div>
              <p v-if="pathScanResult.files.length > 200" class="more-files-hint tabular-nums">
                仅显示前 200 个文件，共 {{ pathScanResult.files.length }} 个
              </p>
            </div>
          </div>
        </template>
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

          <!-- 目标知识库：卡片选择（与 KnowledgeImport 保持一致） -->
          <div class="form-row kb-select-wrap">
            <label class="form-label">
              目标知识库 <span class="required-mark">*</span>
            </label>
            <div v-if="kbCategories.length > 0" class="kb-select-grid">
              <div
                v-for="cat in kbCategories"
                :key="cat.id"
                class="kb-select-item"
                :class="{ selected: Number(formData.kbId) === cat.id }"
                role="button"
                tabindex="0"
                @click="selectKb(cat.id)"
                @keydown.enter.prevent="($event.target as HTMLElement).click()"
              >
                <div class="kb-select-icon" :style="{ background: getKbColor(cat.id) }">
                  <Icon :name="getCategoryIcon(cat.icon)" :size="20" />
                </div>
                <div class="kb-select-info">
                  <p class="kb-name">{{ cat.name }}</p>
                  <p class="kb-count tabular-nums">{{ (cat as any).docCount || 0 }} 篇文档</p>
                </div>
                <Icon v-if="Number(formData.kbId) === cat.id" name="check-circle" :size="20" class="check-icon" />
              </div>
            </div>
            <p v-else class="empty-hint">
              暂无可管理的知识库，请先
              <router-link to="/knowledge/new" class="link-btn">新建知识库</router-link>
              或联系知识库所有者添加您为 Editor。
            </p>
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
            <button v-if="formData.kbId && filteredSubCategories.length === 0" type="button" class="create-category-link" @click="createSubCategory">
              <Icon name="plus" :size="14" />
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
            :disabled="!canSubmit || (importMode === 'file' ? uploading : pathImporting)"
            @click="handleUpload"
          >
            <Icon name="upload" :size="16" />
            <span>{{ submitLabel }}</span>
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
            <button
              v-for="icon in availableIcons"
              :key="icon"
              type="button"
              class="icon-item"
              :class="{ active: newCategoryIcon === icon }"
              @click="newCategoryIcon = icon"
            >
              <Icon :name="icon" :size="16" />
            </button>
          </div>
        </div>
        <div class="modal-actions">
          <button type="button" class="btn-secondary" @click="showCategoryModal = false">取消</button>
          <button type="button" class="btn-primary" @click="confirmCreateCategory">确定</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { categoriesApi, docsApi, knowledgeImportApi } from '@/api'
import type { CategoryVO, PathImportFileEntry, PathImportScanVO } from '@/api/types'
import { notify, getApiError } from '@/utils/toast'

const router = useRouter()

// ==================== 常量 ====================

const UPLOAD_ACCEPT =
  '.pdf,.doc,.docx,.md,.markdown,.txt,.ppt,.pptx,.rtf,' +
  '.java,.py,.css,.vue,.js,.ts,.html,.xml,.yml,.yaml,.json,.sql,.go,.c,.cpp,' +
  '.rs,.kt,.swift,.rb,.php,.scss,.less,.jsx,.tsx,.dart'

const CODE_EXTS = new Set([
  'java', 'py', 'css', 'vue', 'js', 'ts', 'xml', 'yml', 'yaml',
  'json', 'sql', 'sh', 'bash', 'go', 'rs', 'c', 'cpp', 'h', 'hpp',
  'kt', 'swift', 'rb', 'php', 'scss', 'less', 'toml', 'ini', 'conf',
  'jsx', 'tsx', 'dart', 'html', 'htm'
])

// ==================== 类型 ====================

type ImportMode = 'file' | 'path'

interface UploadFile {
  id: string
  file: File
  name: string
  size: number
  progress: number
  status: 'pending' | 'uploading' | 'done' | 'error'
}

type FileProgressStatus = 'pending' | 'processing' | 'done' | 'skipped' | 'failed'

interface PathFileProgress {
  status: FileProgressStatus
  message?: string
}

// ==================== 基础状态 ====================

const fileInputRef = ref<HTMLInputElement | null>(null)
const isDragging = ref(false)
const files = ref<UploadFile[]>([])
const uploading = ref(false)
const tagInput = ref('')
const kbCategories = ref<CategoryVO[]>([])

// 路径导入相关状态
const importMode = ref<ImportMode>('file')
const pathInput = ref('')
const pathScanning = ref(false)
const pathScanResult = ref<PathImportScanVO | null>(null)
const pathImporting = ref(false)
const pathCancelFn = ref<{ cancel: () => Promise<void> } | null>(null)
const pathFileProgressMap = ref<Map<string, PathFileProgress>>(new Map())

// 整体进度
const progressTotal = ref(0)
const progressCurrent = ref(0)
const progressCurrentFile = ref('')

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

// ==================== 计算属性 ====================

const submitLabel = computed(() => {
  const busy = importMode.value === 'file' ? uploading.value : pathImporting.value
  if (!busy) return importMode.value === 'file' ? '开始上传' : '开始导入'
  return importMode.value === 'file' ? '上传中...' : '导入中...'
})

const overallProgressPercent = computed(() => {
  if (progressTotal.value === 0) return 0
  return Math.round((progressCurrent.value / progressTotal.value) * 100)
})

const displayPathFiles = computed(() =>
  (pathScanResult.value?.files ?? []).slice(0, 200),
)

const canSubmit = computed(() => {
  const hasFile = importMode.value === 'file'
    ? files.value.length > 0
    : (pathScanResult.value?.docCount ?? 0) > 0
  return (
    hasFile &&
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

// ==================== 模式切换 ====================

function switchImportMode(mode: ImportMode) {
  if (importMode.value === mode) return
  importMode.value = mode
  if (mode === 'path') {
    // 清空文件上传模式状态
    files.value = []
  } else {
    // 清空路径模式状态
    pathInput.value = ''
    pathScanResult.value = null
    pathFileProgressMap.value.clear()
  }
  // 重置整体进度
  progressTotal.value = 0
  progressCurrent.value = 0
  progressCurrentFile.value = ''
}

// ==================== 文件选择 / 拖拽 ====================

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

// ==================== 路径扫描 ====================

async function scanPath() {
  const p = pathInput.value.trim()
  if (!p) {
    notify('请输入路径', 'warning')
    return
  }
  pathScanning.value = true
  try {
    const result = await knowledgeImportApi.scanPath(p)
    pathScanResult.value = result
    pathFileProgressMap.value.clear()
    if (result.files.length === 0) {
      notify('路径下未找到可导入的文件', 'warning')
    } else {
      notify(
        `扫描完成：${result.docCount} 个文档${result.imageCount ? `，${result.imageCount} 个图片` : ''}`,
        'success',
      )
    }
    if (!formData.value.title && result.docCount > 0) {
      formData.value.title = result.rootName
    }
  } catch (e: unknown) {
    notify(getApiError(e, '路径扫描失败'), 'error')
    pathScanResult.value = null
  } finally {
    pathScanning.value = false
  }
}

// ==================== 标签 ====================

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

// ==================== 图标 / 文件类型辅助 ====================

function getFileTypeIcon(filename: string): string {
  const ext = filename.split('.').pop()?.toLowerCase() ?? ''
  if (ext === 'pdf') return 'file-text'
  if (ext === 'doc' || ext === 'docx') return 'file-text'
  if (ext === 'ppt' || ext === 'pptx') return 'file-text'
  if (CODE_EXTS.has(ext)) return 'code'
  if (ext === 'md' || ext === 'markdown' || ext === 'txt') return 'file-text'
  return 'file'
}

function getFileTypeColorClass(filename: string): string {
  const ext = filename.split('.').pop()?.toLowerCase()
  if (ext === 'pdf') return 'type-pdf'
  if (ext === 'md' || ext === 'markdown') return 'type-md'
  if (ext === 'doc' || ext === 'docx') return 'type-doc'
  if (ext === 'ppt' || ext === 'pptx') return 'type-ppt'
  if (CODE_EXTS.has(ext ?? '')) return 'type-md'
  return 'type-default'
}

function getFileIconByExt(ext: string, type: string): string {
  if (type === 'image') return 'image'
  if (CODE_EXTS.has(ext)) return 'code'
  if (ext === 'pdf' || ext === 'doc' || ext === 'docx' || ext === 'ppt' || ext === 'pptx') return 'file-text'
  if (ext === 'md' || ext === 'markdown' || ext === 'txt') return 'file-text'
  return 'file'
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

// 路径文件进度：每个条目根据 index 推断进度
function getPathFileProgress(entry: PathImportFileEntry): number {
  const mapEntry = pathFileProgressMap.value.get(entry.path)
  if (!mapEntry) {
    // 尚未处理：按比例返回 0~10 的占位显示
    return progressCurrent.value > 0 ? Math.min(10, overallProgressPercent.value) : 0
  }
  switch (mapEntry.status) {
    case 'done': return 100
    case 'skipped': return 100
    case 'failed': return 100
    case 'processing': return 50
    default: return 0
  }
}

function getPathFileProgressClass(entry: PathImportFileEntry, _idx: number): string {
  const s = pathFileProgressMap.value.get(entry.path)?.status
  if (s === 'done' || s === 'skipped') return 'fill-success'
  if (s === 'failed') return 'fill-error'
  if (s === 'processing') return 'fill-primary'
  return 'fill-primary'
}

function getPathFileStatusLabel(entry: PathImportFileEntry, _idx: number): string {
  const item = pathFileProgressMap.value.get(entry.path)
  if (!item) return '等待中'
  switch (item.status) {
    case 'done': return '成功'
    case 'skipped': return '跳过'
    case 'failed': return '失败'
    case 'processing': return '处理中'
    default: return '等待中'
  }
}

function getPathFileStatusClass(entry: PathImportFileEntry, _idx: number): string {
  const s = pathFileProgressMap.value.get(entry.path)?.status
  if (s === 'done' || s === 'skipped') return 'status-success'
  if (s === 'failed') return 'status-error'
  if (s === 'processing') return 'status-muted'
  return 'status-muted'
}

function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i]
}

// ==================== 上传 / 导入核心逻辑 ====================

async function handleUpload(): Promise<void> {
  if (!canSubmit.value) return
  if (importMode.value === 'file') {
    await handleFileUpload()
  } else {
    await handlePathImport()
  }
}

/** 模式 A：逐个上传文件，带真实上传进度回调（axios onUploadProgress） */
async function handleFileUpload(): Promise<void> {
  if (uploading.value) return
  uploading.value = true
  progressTotal.value = files.value.length
  progressCurrent.value = 0

  try {
    for (const uploadFile of files.value) {
      uploadFile.status = 'uploading'
      uploadFile.progress = 10
      progressCurrentFile.value = uploadFile.name

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
            // 使用真实的上传进度（percent：0~100）
            uploadFile.progress = percent
          },
        )

        uploadFile.progress = 100
        uploadFile.status = 'done'
      } catch (e: unknown) {
        uploadFile.status = 'error'
        uploadFile.progress = 100
        notify(`${uploadFile.name} 上传失败：${getApiError(e, '请稍后再试')}`, 'error')
      }
      progressCurrent.value++
    }

    const hasError = files.value.some((f) => f.status === 'error')
    if (!hasError) {
      notify('文档上传成功！', 'success')
      setTimeout(() => { router.push('/knowledge') }, 1500)
    } else {
      notify('部分文件上传失败，请重试', 'warning')
    }
  } finally {
    uploading.value = false
    progressCurrentFile.value = ''
  }
}

/** 模式 B：路径导入（复用 knowledgeImportApi.importPathStream SSE 能力） */
async function handlePathImport(): Promise<void> {
  if (pathImporting.value || !pathScanResult.value) return
  pathImporting.value = true
  pathFileProgressMap.value.clear()
  progressTotal.value = pathScanResult.value.docCount
  progressCurrent.value = 0
  progressCurrentFile.value = ''

  let finishedWithError = false

  pathCancelFn.value = knowledgeImportApi.importPathStream(
    {
      path: pathScanResult.value.absolutePath,
      targetCategoryId: Number(formData.value.categoryId),
      createSubCategories: false, // 上传页面默认不创建子分类
      autoTags: false,
      aiTags: false,
      incremental: false,
    },
    {
      onStart: () => {},
      onFileStart: (d) => {
        progressCurrentFile.value = d.path
        pathFileProgressMap.value.set(d.path, { status: 'processing' })
      },
      onFileDone: (d) => {
        let status: FileProgressStatus = 'done'
        if (d.status === 'skipped') status = 'skipped'
        else if (d.status === 'failed') status = 'failed'
        else if (d.status === 'success') status = 'done'
        pathFileProgressMap.value.set(d.path, { status, message: d.message })
        progressCurrent.value = d.index
      },
      onComplete: (res) => {
        progressCurrent.value = res.totalDocs
        pathImporting.value = false
        pathCancelFn.value = null
        if (res.failedCount > 0) {
          notify(`导入完成：成功 ${res.successCount}，跳过 ${res.skippedCount}，失败 ${res.failedCount}`, 'warning')
          finishedWithError = true
        } else {
          notify(`导入成功：${res.successCount} 篇文档`, 'success')
        }
      },
      onCancel: () => {
        pathImporting.value = false
        pathCancelFn.value = null
        notify('导入已取消', 'warning')
        finishedWithError = true
      },
      onError: (err) => {
        pathImporting.value = false
        pathCancelFn.value = null
        notify(getApiError(new Error(err), '导入失败'), 'error')
        finishedWithError = true
      },
    },
  )

  // 监听导入结束，未报错则跳转
  const poll = setInterval(() => {
    if (!pathImporting.value) {
      clearInterval(poll)
      if (!finishedWithError) {
        setTimeout(() => { router.push('/knowledge') }, 1500)
      }
    }
  }, 500)
}

// ==================== 取消 / 返回 ====================

function handleCancel(): void {
  files.value = []
  pathInput.value = ''
  pathScanResult.value = null
  pathFileProgressMap.value.clear()
  progressTotal.value = 0
  progressCurrent.value = 0
  progressCurrentFile.value = ''
  formData.value = { title: '', kbId: '', categoryId: '', tags: [], description: '' }
  if (fileInputRef.value) {
    fileInputRef.value.value = ''
  }
  router.push('/knowledge')
}

function goBack(): void {
  router.push('/knowledge')
}

// ==================== 分类管理 ====================

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
  } catch (e: unknown) {
    notify(`创建失败：${getApiError(e, '请稍后再试')}`, 'error')
  }
}

async function loadCategories(): Promise<void> {
  try {
    // 仅加载当前用户可编辑的知识库（与导入知识库功能保持一致）
    const list = await knowledgeImportApi.listEditableKbs()
    kbCategories.value = list || []
  } catch (e: unknown) {
    notify(`加载分类失败：${getApiError(e)}`, 'error')
    kbCategories.value = []
  }
}

// ===== 知识库卡片选择 =====
function selectKb(id: number) {
  formData.value.kbId = id
  // 切换知识库时清空分类选择
  formData.value.categoryId = undefined
}

const kbColors = ['#3B6FE0', '#10B981', '#F59E0B', '#8B5CF6', '#EC4899', '#06B6D4', '#84CC16']
function getKbColor(id: number) {
  const idx = (id - 1) % kbColors.length
  return `${kbColors[Math.max(0, idx)]}1A`
}
function getCategoryIcon(icon?: string) {
  if (icon && icon.trim()) return icon
  return 'book-open'
}

onMounted(loadCategories)
</script>

<style scoped>
.knowledge-upload-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 24px 40px;
}

@media (max-width: 768px) {
  .knowledge-upload-page {
    padding: 0 16px 32px;
  }
}

/* ===== 整体导入进度条 ===== */
.global-progress-card {
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: 14px;
  padding: 16px;
  margin-bottom: 16px;
}

.global-progress-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}

.global-progress-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  font-size: var(--kb-fs-body-sm);
  font-weight: 600;
  color: var(--kb-foreground);
}

.global-progress-count {
  font-size: var(--kb-fs-caption);
  color: var(--kb-primary);
  font-weight: 600;
}

.global-progress-bar {
  height: 8px;
  border-radius: 4px;
  background: var(--kb-muted);
  overflow: hidden;
}

.global-progress-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--kb-primary), #6366F1);
  border-radius: 4px;
  transition: width 0.3s ease;
}

.global-progress-file {
  margin: 8px 0 0;
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
  font-family: var(--font-mono);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ===== 导入方式 Tab 切换 ===== */
.import-mode-tabs {
  display: flex;
  gap: 4px;
  padding: 4px;
  background: var(--kb-muted);
  border-radius: 12px;
  margin-bottom: 16px;
}

.mode-tab {
  flex: 1;
  min-width: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 8px 16px;
  border: none;
  background: transparent;
  color: var(--kb-muted-foreground);
  font-size: var(--kb-fs-body-sm);
  font-weight: 500;
  line-height: var(--kb-lh-body-sm);
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease, transform 0.15s ease;
  font-family: inherit;
}

.mode-tab:hover:not(:disabled) {
  background: rgba(59, 111, 224, 0.06);
  color: var(--kb-foreground);
}

.mode-tab:active:not(:disabled) {
  transform: scale(0.98);
}

.mode-tab:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}

.mode-tab:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.mode-tab.active {
  background: var(--kb-card);
  color: var(--kb-primary);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}

.mode-tab.active:hover:not(:disabled) {
  background: var(--kb-card);
  color: var(--kb-primary);
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
  gap: 8px;
  padding: 8px 16px;
  border-radius: 8px;
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-foreground);
  font-size: var(--kb-fs-body-sm);
  font-weight: 500;
  line-height: var(--kb-lh-body-sm);
  cursor: pointer;
  white-space: nowrap;
  transition: border-color 0.15s ease, color 0.15s ease, background 0.15s ease, transform 0.15s ease;
}

.back-btn:hover {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.06);
}

.back-btn:active {
  transform: scale(0.98);
}

.back-btn:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}

.page-title {
  font-size: var(--kb-fs-h4);
  font-weight: var(--kb-fw-h4);
  line-height: var(--kb-lh-h4);
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
  transition: border-color 0.2s ease, background 0.2s ease;
}

.upload-zone:hover {
  border-color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.03);
}

.upload-zone:active {
  background: rgba(59, 111, 224, 0.08);
}

.upload-zone:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
  border-color: var(--kb-primary);
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
  font-size: var(--kb-fs-body-lg);
  font-weight: 500;
  line-height: var(--kb-lh-body-lg);
  color: var(--kb-foreground);
  margin-bottom: 4px;
}

.upload-zone-hint {
  font-size: var(--kb-fs-body-sm);
  line-height: var(--kb-lh-body-sm);
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
  flex-wrap: wrap;
  gap: 8px;
  padding: 16px;
  border-bottom: 1px solid var(--kb-border);
}

.list-title {
  font-size: var(--kb-fs-body-md);
  font-weight: 600;
  line-height: var(--kb-lh-body-md);
  color: var(--kb-foreground);
  min-width: 0;
  margin: 0;
}

.file-list-count {
  font-size: var(--kb-fs-caption);
  line-height: var(--kb-lh-caption);
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

.type-pdf { background: rgba(239, 68, 68, 0.1); color: var(--kb-destructive); }
.type-md { background: rgba(16, 185, 129, 0.1); color: var(--kb-accent); }
.type-doc { background: rgba(59, 111, 224, 0.08); color: var(--kb-primary); }
.type-ppt { background: rgba(245, 158, 11, 0.1); color: var(--kb-warning); }
.type-default { background: rgba(107, 114, 128, 0.1); color: var(--kb-muted-foreground); }

.file-info {
  flex: 1;
  min-width: 0;
}

.file-name {
  font-size: var(--kb-fs-body-sm);
  font-weight: 500;
  line-height: var(--kb-lh-body-sm);
  color: var(--kb-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin: 0;
}

.file-size {
  font-size: var(--kb-fs-caption);
  line-height: var(--kb-lh-caption);
  color: var(--kb-muted-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
.fill-success { background: var(--kb-accent); }
.fill-error { background: var(--kb-destructive); }

.file-status {
  font-size: var(--kb-fs-caption);
  line-height: var(--kb-lh-caption);
  flex-shrink: 0;
  min-width: 48px;
  text-align: right;
}

.status-success { color: var(--kb-accent); }
.status-error { color: var(--kb-destructive); }
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
  transition: background 0.15s ease, color 0.15s ease, transform 0.15s ease;
}

.file-remove-btn:hover {
  background: rgba(239, 68, 68, 0.08);
  color: var(--kb-destructive);
}

.file-remove-btn:active {
  transform: scale(0.94);
  background: rgba(239, 68, 68, 0.14);
}

.file-remove-btn:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
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
  font-size: var(--kb-fs-body-lg);
  font-weight: 600;
  line-height: var(--kb-lh-h4);
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
  gap: 8px;
}

.form-label {
  font-size: var(--kb-fs-body-sm);
  font-weight: 500;
  line-height: var(--kb-lh-body-sm);
  color: var(--kb-foreground);
}

.required-mark {
  color: var(--kb-destructive);
}

.form-hint {
  font-size: var(--kb-fs-xs);
  line-height: var(--kb-lh-xs);
  color: var(--kb-muted-foreground);
  margin: 0;
}

.form-input,
.form-select {
  width: 100%;
  height: 38px;
  padding: 0 12px;
  border-radius: 8px;
  font-size: var(--kb-fs-body-sm);
  background: var(--kb-background);
  color: var(--kb-foreground);
  border: 1px solid var(--kb-border);
  outline: none;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
  font-family: inherit;
}

.form-select {
  cursor: pointer;
}

.form-select:hover:not(:disabled) {
  border-color: var(--kb-primary);
}

.form-input:focus,
.form-select:focus {
  border-color: var(--kb-primary);
}

.form-input:focus-visible,
.form-select:focus-visible,
.form-textarea:focus-visible {
  border-color: var(--kb-primary);
  box-shadow: 0 0 0 3px rgba(59, 111, 224, 0.15);
}

.form-select:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.form-textarea {
  width: 100%;
  padding: 8px 12px;
  border-radius: 8px;
  font-size: var(--kb-fs-body-sm);
  line-height: var(--kb-lh-body-sm);
  background: var(--kb-background);
  color: var(--kb-foreground);
  border: 1px solid var(--kb-border);
  outline: none;
  resize: none;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
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
  font-size: var(--kb-fs-caption);
  line-height: var(--kb-lh-caption);
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease, transform 0.15s ease;
  align-self: flex-start;
}

.create-category-link:hover {
  background: rgba(59, 111, 224, 0.06);
  border-color: var(--kb-primary);
}

.create-category-link:active {
  transform: scale(0.98);
  background: rgba(59, 111, 224, 0.12);
}

.create-category-link:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}

.tags-input {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 8px 12px;
  border-radius: 8px;
  background: var(--kb-background);
  border: 1px solid var(--kb-border);
  min-height: 38px;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.tags-input:hover {
  border-color: var(--kb-primary);
}

.tags-input:focus-within {
  border-color: var(--kb-primary);
  box-shadow: 0 0 0 3px rgba(59, 111, 224, 0.15);
}

.tag-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 24px;
  padding: 0 8px;
  border-radius: 6px;
  font-size: var(--kb-fs-caption);
  line-height: var(--kb-lh-caption);
  font-weight: 500;
  background: rgba(59, 111, 224, 0.1);
  color: var(--kb-primary);
  max-width: 100%;
}

.tag-remove {
  background: transparent;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  color: var(--kb-primary);
  padding: 0;
  display: inline-flex;
  align-items: center;
  opacity: 0.7;
  transition: opacity 0.15s ease, background 0.15s ease, transform 0.15s ease;
}

.tag-remove:hover {
  opacity: 1;
  background: rgba(59, 111, 224, 0.14);
}

.tag-remove:active {
  transform: scale(0.9);
}

.tag-remove:focus-visible {
  opacity: 1;
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}

.tag-text-input {
  flex: 1;
  min-width: 80px;
  height: 24px;
  font-size: var(--kb-fs-body-sm);
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
  min-width: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 38px;
  padding: 0 16px;
  border-radius: 8px;
  font-size: var(--kb-fs-body-sm);
  font-weight: 500;
  line-height: var(--kb-lh-body-sm);
  cursor: pointer;
  transition: background 0.15s ease, opacity 0.15s ease, transform 0.15s ease;
  border: none;
  font-family: inherit;
}

.btn-primary:focus-visible,
.btn-secondary:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}

.btn-primary {
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
}

.btn-primary:hover:not(:disabled) {
  opacity: 0.9;
}

.btn-primary:active:not(:disabled) {
  transform: scale(0.98);
  opacity: 0.85;
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

.btn-secondary:hover:not(:disabled) {
  background: var(--kb-muted);
}

.btn-secondary:active:not(:disabled) {
  transform: scale(0.98);
  background: var(--kb-muted);
  border-color: var(--kb-primary);
}

.btn-secondary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
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
  font-size: var(--kb-fs-body-lg);
  font-weight: 600;
  line-height: var(--kb-lh-h4);
  color: var(--kb-foreground);
  margin: 0 0 16px;
}

.icon-grid {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 8px;
}

@media (max-width: 768px) {
  .icon-grid {
    grid-template-columns: repeat(6, 1fr);
  }
}

.icon-item {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  cursor: pointer;
  color: var(--kb-foreground);
  background: var(--kb-background);
  border: 1px solid transparent;
  transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease, transform 0.15s ease;
}

.icon-item:hover {
  background: var(--kb-muted);
  border-color: var(--kb-border);
}

.icon-item:active {
  transform: scale(0.94);
}

.icon-item:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
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

/* ===== 路径输入区 ===== */
.path-input-zone {
  margin-bottom: 16px;
}

.path-input-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 8px;
  background: var(--kb-card);
  border: 2px solid var(--kb-border);
  border-radius: 14px;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.path-input-row:hover {
  border-color: var(--kb-primary);
}

.path-input-row:focus-within,
.path-input-row.scanning {
  border-color: var(--kb-primary);
  box-shadow: 0 0 0 3px rgba(59, 111, 224, 0.15);
}

.path-input-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  color: var(--kb-muted-foreground);
  flex-shrink: 0;
}

.path-input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  font-size: var(--kb-fs-body-md);
  color: var(--kb-foreground);
  font-family: var(--font-mono);
  padding: 8px 4px;
}

.path-input::placeholder {
  color: var(--kb-muted-foreground);
}

.path-input:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-scan {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  border: none;
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  font-size: var(--kb-fs-body-sm);
  font-weight: 500;
  line-height: var(--kb-lh-body-sm);
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.15s ease, transform 0.15s ease, opacity 0.15s ease;
  flex-shrink: 0;
  white-space: nowrap;
  font-family: inherit;
}

.btn-scan:hover:not(:disabled) {
  background: var(--color-primary-dark);
}

.btn-scan:active:not(:disabled) {
  transform: scale(0.98);
  opacity: 0.9;
}

.btn-scan:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}

.btn-scan:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.path-input-hint {
  margin: 12px 2px 0;
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
  line-height: 1.6;
}

/* ===== 扫描结果：文件列表辅助 ===== */
.scan-mode-tag {
  display: inline-block;
  margin-left: 8px;
  padding: 2px 8px;
  font-size: var(--kb-fs-xs);
  font-weight: 500;
  background: rgba(59, 111, 224, 0.1);
  color: var(--kb-primary);
  border-radius: 4px;
}

.path-file-list .file-name {
  font-size: var(--kb-fs-body-sm);
  font-weight: 500;
}

.path-file-list .file-size {
  color: var(--kb-muted-foreground);
  font-family: var(--font-mono);
  font-size: var(--kb-fs-xs);
}

.more-files-hint {
  padding: 12px;
  text-align: center;
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
  border-top: 1px solid var(--kb-border);
}

.spin-icon {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ===== 知识库卡片选择（与 KnowledgeImport 保持一致） ===== */
.kb-select-wrap {
  display: block;
}

.kb-select-wrap .form-label {
  margin-bottom: 12px;
  display: block;
}

.kb-select-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 12px;
}

.kb-select-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 2px solid var(--kb-border);
  border-radius: 10px;
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease, transform 0.15s ease;
  position: relative;
}

.kb-select-item:hover {
  border-color: rgba(59, 111, 224, 0.4);
  background: rgba(59, 111, 224, 0.03);
}

.kb-select-item:active {
  transform: scale(0.99);
  background: rgba(59, 111, 224, 0.06);
}

.kb-select-item:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
  border-color: var(--kb-primary);
}

.kb-select-item.selected {
  border-color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.04);
}

.kb-select-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  flex-shrink: 0;
  color: var(--kb-primary);
}

.kb-select-info {
  flex: 1;
  min-width: 0;
}

.kb-name {
  font-size: var(--kb-fs-body-md);
  font-weight: 500;
  line-height: var(--kb-lh-body-md);
  color: var(--kb-foreground);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin: 0;
}

.kb-count {
  font-size: var(--kb-fs-caption);
  line-height: var(--kb-lh-caption);
  color: var(--kb-muted-foreground);
  margin: 2px 0 0;
}

.check-icon {
  color: var(--kb-primary);
  flex-shrink: 0;
}

.empty-hint {
  font-size: var(--kb-fs-body-md);
  line-height: var(--kb-lh-body-md);
  color: var(--kb-muted-foreground);
  padding: 16px;
  border: 1px dashed var(--kb-border);
  border-radius: 10px;
  text-align: center;
  margin: 0;
}

.link-btn {
  display: inline-block;
  border-radius: 4px;
  color: var(--kb-primary);
  text-decoration: underline;
  text-underline-offset: 2px;
  transition: color 0.15s ease, opacity 0.15s ease;
}

.link-btn:hover {
  color: var(--color-primary-dark);
}

.link-btn:active {
  opacity: 0.8;
}

.link-btn:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}
</style>
