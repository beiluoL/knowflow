<template>
  <!-- 管理后台-文件管理：上传文件列表、删除、存储统计、上传目录配置 -->
  <div class="file-mgmt-wrap">
    <!-- 页面标题 -->
    <div class="page-head">
      <h1 class="kb-h1">文件管理</h1>
      <div class="flex items-center gap-2">
        <button class="btn-secondary" @click="loadData">
          <Icon name="refresh-cw" :size="14" />
          <span>刷新</span>
        </button>
      </div>
    </div>

    <!-- 存储统计卡片 -->
    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-icon" style="background: rgba(59,111,224,0.1); color: #3B6FE0;">
          <Icon name="file" :size="20" />
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ stats?.totalFiles ?? '-' }}</div>
          <div class="stat-label">文件总数</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background: rgba(16,185,129,0.1); color: #10B981;">
          <Icon name="hard-drive" :size="20" />
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ stats?.totalSizeReadable ?? '-' }}</div>
          <div class="stat-label">占用空间</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background: rgba(245,158,11,0.1); color: #F59E0B;">
          <Icon name="image" :size="20" />
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ stats?.imageCount ?? '-' }}</div>
          <div class="stat-label">图片文件</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background: rgba(139,92,246,0.1); color: #8B5CF6;">
          <Icon name="file-text" :size="20" />
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ stats?.otherCount ?? '-' }}</div>
          <div class="stat-label">其他文件</div>
        </div>
      </div>
    </div>

    <!-- 上传目录配置卡片 -->
    <div class="config-card">
      <div class="config-head">
        <h3 class="kb-h3">
          <Icon name="folder" :size="18" class="mr-2" style="color: var(--kb-primary);" />
          上传目录配置
        </h3>
        <button
          v-if="!editingDir"
          class="btn-secondary btn-sm"
          @click="startEditDir"
        >
          <Icon name="edit-3" :size="14" />
          <span>修改目录</span>
        </button>
      </div>
      <div v-if="!editingDir" class="config-body">
        <div class="config-row">
          <span class="config-label">当前目录：</span>
          <code class="config-value">{{ config?.absoluteDir || '加载中...' }}</code>
        </div>
        <div v-if="config" class="config-row">
          <span class="config-label">目录状态：</span>
          <span :class="config.exists ? 'status-ok' : 'status-warn'">
            <Icon :name="config.exists ? 'check-circle' : 'alert-circle'" :size="14" />
            {{ config.exists ? '目录存在' : '目录不存在' }}
          </span>
        </div>
      </div>
      <div v-else class="config-edit">
        <div class="flex items-center gap-2 mb-2">
          <input
            v-model="newDir"
            type="text"
            class="dir-input"
            placeholder="输入上传目录的绝对路径，如 /Users/xxx/knowflow/uploads"
          />
          <button class="btn-secondary btn-sm" @click="pickDirectory" title="选择本地文件夹">
            <Icon name="folder" :size="14" />
            <span>选择文件夹</span>
          </button>
        </div>
        <div class="config-tip">
          <Icon name="info" :size="14" />
          <span>修改目录后需重启后端才能让 /uploads 静态资源映射生效。已有文件不会自动迁移。</span>
        </div>
        <div class="flex items-center gap-2 mt-3">
          <button class="btn-primary btn-sm" :disabled="savingDir" @click="saveDir">
            <Icon name="check" :size="14" />
            <span>{{ savingDir ? '保存中...' : '保存配置' }}</span>
          </button>
          <button class="btn-secondary btn-sm" @click="editingDir = false">取消</button>
        </div>
      </div>
    </div>

    <!-- 文件列表 -->
    <div class="section-card">
      <div class="section-head">
        <h3 class="kb-h3">文件列表（共 {{ total }} 个）</h3>
      </div>
      <!-- 筛选栏 -->
      <div class="filter-bar">
        <div class="filter-group">
          <button
            v-for="t in typeFilters"
            :key="t.value"
            class="kb-filter-btn"
            :class="{ active: filterType === t.value }"
            @click="filterType = t.value"
          >{{ t.label }}</button>
        </div>
        <input
          v-model="keyword"
          type="text"
          class="search-input"
          placeholder="搜索文件名..."
          @keydown.enter="applyFilter"
        />
        <button class="btn-secondary btn-sm" @click="applyFilter">
          <Icon name="search" :size="14" />
          <span>搜索</span>
        </button>
      </div>

      <!-- 加载中 -->
      <div v-if="loading" class="empty-tip">
        <Icon name="loader" :size="24" class="animate-spin" />
        <span>加载中...</span>
      </div>

      <!-- 空状态 -->
      <div v-else-if="files.length === 0" class="empty-tip">
        <Icon name="inbox" :size="32" style="color: var(--kb-muted-foreground);" />
        <span>暂无上传文件</span>
      </div>

      <!-- 文件网格 -->
      <div v-else class="file-grid">
        <div v-for="file in files" :key="file.fileUrl" class="file-card">
          <!-- 文件预览 -->
          <div class="file-preview" @click="previewFile(file)">
            <img v-if="file.isImage" :src="file.fileUrl" :alt="file.fileName" loading="lazy" />
            <div v-else class="file-icon-large">
              <Icon :name="getFileIcon(file.extension)" :size="36" />
            </div>
          </div>
          <!-- 文件信息 -->
          <div class="file-info">
            <div class="file-name" :title="file.fileName">{{ file.fileName }}</div>
            <div class="file-meta">
              <span>{{ formatSize(file.fileSize) }}</span>
              <span class="meta-dot">·</span>
              <span>{{ file.extension.toUpperCase() || '未知' }}</span>
              <span class="meta-dot">·</span>
              <span>{{ formatDate(file.lastModified) }}</span>
            </div>
          </div>
          <!-- 操作按钮 -->
          <div class="file-actions">
            <button
              class="icon-btn"
              title="复制链接"
              @click="copyUrl(file.fileUrl)"
            >
              <Icon name="link" :size="14" />
            </button>
            <a
              v-if="file.isImage"
              :href="file.fileUrl"
              target="_blank"
              rel="noopener"
              class="icon-btn"
              title="新窗口打开"
            >
              <Icon name="external-link" :size="14" />
            </a>
            <button
              class="icon-btn danger"
              title="删除"
              @click="deleteFile(file)"
            >
              <Icon name="trash-2" :size="14" />
            </button>
          </div>
        </div>
      </div>

      <!-- 分页 -->
      <div v-if="total > pageSize" class="pagination-bar">
        <button
          class="page-btn"
          :disabled="page <= 1"
          @click="page--; loadData()"
        >上一页</button>
        <span class="page-info">第 {{ page }} / {{ totalPages }} 页</span>
        <button
          class="page-btn"
          :disabled="page >= totalPages"
          @click="page++; loadData()"
        >下一页</button>
      </div>
    </div>

    <!-- 隐藏的文件夹选择 input -->
    <input
      ref="dirInputRef"
      type="file"
      webkitdirectory
      directory
      multiple
      style="display: none"
      @change="onDirectoryPicked"
    />
  </div>
</template>

<script setup lang="ts">
/**
 * 文件管理页（管理员）
 * 功能：存储统计、上传目录配置、文件列表（图片预览/删除/复制链接）
 */
import { ref, computed, onMounted } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import { fileApi } from '@/api'
import type { UploadFileVO, UploadStatsVO, UploadConfigVO } from '@/api'
import { notify, confirmDialog, getApiError } from '@/utils/toast'
import { handleImageLightboxClick } from '@/utils/imageLightbox'

// ===== 状态 =====
const loading = ref(false)
const files = ref<UploadFileVO[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(24)
const filterType = ref<'image' | 'other' | ''>('')
const keyword = ref('')
const stats = ref<UploadStatsVO | null>(null)
const config = ref<UploadConfigVO | null>(null)

// 目录编辑
const editingDir = ref(false)
const newDir = ref('')
const savingDir = ref(false)
const dirInputRef = ref<HTMLInputElement | null>(null)

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))

const typeFilters = [
  { label: '全部', value: '' as const },
  { label: '图片', value: 'image' as const },
  { label: '其他', value: 'other' as const },
]

// ===== 数据加载 =====
async function loadData() {
  loading.value = true
  try {
    const [listRes, statsRes, configRes] = await Promise.all([
      fileApi.list({
        page: page.value,
        pageSize: pageSize.value,
        type: filterType.value,
        keyword: keyword.value,
      }),
      fileApi.stats(),
      fileApi.getConfig(),
    ])
    files.value = listRes.list
    total.value = listRes.total
    stats.value = statsRes
    config.value = configRes
  } catch (e: unknown) {
    notify(getApiError(e), 'error')
  } finally {
    loading.value = false
  }
}

function applyFilter() {
  page.value = 1
  loadData()
}

// ===== 目录配置 =====
function startEditDir() {
  if (!config.value) return
  newDir.value = config.value.uploadDir
  editingDir.value = true
}

async function saveDir() {
  if (!newDir.value.trim()) {
    notify('请输入目录路径', 'warning')
    return
  }
  savingDir.value = true
  try {
    await fileApi.updateConfig(newDir.value.trim())
    notify('目录配置已保存，请重启后端以让 /uploads 映射生效', 'success')
    editingDir.value = false
    await loadData()
  } catch (e: unknown) {
    notify(getApiError(e), 'error')
  } finally {
    savingDir.value = false
  }
}

// 选择本地文件夹（仅获取路径名，浏览器无法获取完整权限路径）
function pickDirectory() {
  // 浏览器安全限制：input[webkitdirectory] 只能获取相对路径，
  // 但用户可通过它浏览文件夹后，手动在文本框中修改完整路径
  dirInputRef.value?.click()
}

function onDirectoryPicked(e: Event) {
  const input = e.target as HTMLInputElement
  if (input.files && input.files.length > 0) {
    // 取第一个文件的相对路径（含文件夹名）
    const relPath = input.files[0].webkitRelativePath || input.files[0].name
    const folderName = relPath.split('/')[0]
    // 提示用户补全绝对路径前缀
    notify(`已选择文件夹「${folderName}」，请补全完整绝对路径后保存`, 'info')
  }
  // 清空 input 以便重复选择同一文件夹
  input.value = ''
}

// ===== 文件操作 =====
async function deleteFile(file: UploadFileVO) {
  try {
    const ok = await confirmDialog(`确定删除文件「${file.fileName}」吗？此操作不可恢复。`, '删除确认')
    if (!ok) return
    await fileApi.remove(file.fileUrl)
    notify('删除成功', 'success')
    await loadData()
  } catch (e: unknown) {
    notify(getApiError(e), 'error')
  }
}

async function copyUrl(url: string) {
  try {
    await navigator.clipboard.writeText(url)
    notify('链接已复制到剪贴板', 'success')
  } catch {
    notify('复制失败，请手动复制', 'error')
  }
}

function previewFile(file: UploadFileVO) {
  if (!file.isImage) return
  // 模拟一个 click 事件委托给 lightbox 处理
  const img = document.createElement('img')
  img.src = file.fileUrl
  img.alt = file.fileName
  img.classList.add('md-image-zoom')
  const evt = new MouseEvent('click', { bubbles: true })
  Object.defineProperty(evt, 'target', { value: img })
  handleImageLightboxClick(evt)
}

// ===== 工具函数 =====
function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
  return (bytes / (1024 * 1024 * 1024)).toFixed(2) + ' GB'
}

function formatDate(ts: number): string {
  if (!ts) return '-'
  const d = new Date(ts)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function getFileIcon(ext: string): string {
  const e = ext.toLowerCase()
  if (['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg', 'bmp', 'ico'].includes(e)) return 'image'
  if (['pdf'].includes(e)) return 'file-text'
  if (['doc', 'docx'].includes(e)) return 'file-text'
  if (['xls', 'xlsx'].includes(e)) return 'file-text'
  if (['ppt', 'pptx'].includes(e)) return 'file-text'
  if (['zip', 'rar', '7z', 'tar', 'gz'].includes(e)) return 'archive'
  if (['mp4', 'avi', 'mov', 'wmv', 'flv'].includes(e)) return 'video'
  if (['mp3', 'wav', 'flac', 'aac'].includes(e)) return 'music'
  if (['md', 'txt', 'json', 'yml', 'yaml'].includes(e)) return 'file'
  if (['js', 'ts', 'java', 'py', 'go', 'rs', 'c', 'cpp', 'html', 'css'].includes(e)) return 'code'
  return 'file'
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.file-mgmt-wrap {
  padding: 24px;
  max-width: 1400px;
  margin: 0 auto;
}

/* 页头 */
.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}
.page-head h1 {
  font-size: 28px;
  font-weight: 700;
  color: var(--kb-foreground);
  margin: 0;
}

/* 统计卡片网格 */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}
.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: 12px;
  box-shadow: var(--shadow-card);
}
.stat-icon {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.stat-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.stat-value {
  font-size: 22px;
  font-weight: 700;
  color: var(--kb-foreground);
  line-height: 1.2;
}
.stat-label {
  font-size: 12px;
  color: var(--kb-muted-foreground);
}

/* 目录配置卡片 */
.config-card {
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: 12px;
  padding: 20px 24px;
  margin-bottom: 24px;
  box-shadow: var(--shadow-card);
}
.config-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.config-head h3 {
  display: flex;
  align-items: center;
  font-size: 16px;
  font-weight: 600;
  color: var(--kb-foreground);
  margin: 0;
}
.config-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.config-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}
.config-label {
  color: var(--kb-muted-foreground);
  flex-shrink: 0;
}
.config-value {
  font-family: var(--font-mono);
  font-size: 13px;
  color: var(--kb-foreground);
  background: var(--kb-muted);
  padding: 3px 8px;
  border-radius: 4px;
  word-break: break-all;
}
.status-ok {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #10B981;
  font-size: 13px;
}
.status-warn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #F59E0B;
  font-size: 13px;
}
.config-edit {
  display: flex;
  flex-direction: column;
}
.dir-input {
  flex: 1;
  height: 38px;
  padding: 0 12px;
  border: 1px solid var(--kb-border);
  border-radius: 8px;
  font-size: 13px;
  font-family: var(--font-mono);
  color: var(--kb-foreground);
  background: var(--kb-card);
  outline: none;
  transition: border-color 0.15s;
}
.dir-input:focus {
  border-color: var(--kb-primary);
  box-shadow: 0 0 0 3px rgba(59, 111, 224, 0.1);
}
.config-tip {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  font-size: 12px;
  color: var(--kb-muted-foreground);
  margin-top: 8px;
}
.config-tip svg {
  flex-shrink: 0;
  margin-top: 1px;
}

/* 通用按钮 */
.btn-primary, .btn-secondary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 14px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  border: none;
  transition: all 0.15s ease;
}
.btn-primary {
  background: var(--kb-primary);
  color: #fff;
}
.btn-primary:hover {
  opacity: 0.9;
}
.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.btn-secondary {
  background: var(--kb-card);
  color: var(--kb-foreground);
  border: 1px solid var(--kb-border);
}
.btn-secondary:hover {
  background: var(--kb-muted);
}
.btn-sm {
  height: 30px;
  padding: 0 10px;
  font-size: 12px;
}

/* 文件列表区 */
.section-card {
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: 12px;
  padding: 20px 24px;
  box-shadow: var(--shadow-card);
}
.section-head {
  margin-bottom: 16px;
}
.section-head h3 {
  font-size: 16px;
  font-weight: 600;
  color: var(--kb-foreground);
  margin: 0;
}

/* 筛选栏 */
.filter-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 18px;
  flex-wrap: wrap;
}
.filter-group {
  display: flex;
  gap: 4px;
  background: var(--kb-muted);
  border-radius: 8px;
  padding: 3px;
}
.kb-filter-btn {
  height: 28px;
  padding: 0 12px;
  border: none;
  background: transparent;
  color: var(--kb-muted-foreground);
  font-size: 12px;
  font-weight: 500;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s;
}
.kb-filter-btn.active {
  background: var(--kb-card);
  color: var(--kb-primary);
  box-shadow: var(--shadow-sm);
}
.search-input {
  flex: 1;
  min-width: 200px;
  height: 32px;
  padding: 0 12px;
  border: 1px solid var(--kb-border);
  border-radius: 8px;
  font-size: 13px;
  color: var(--kb-foreground);
  background: var(--kb-card);
  outline: none;
}
.search-input:focus {
  border-color: var(--kb-primary);
}

/* 空状态 */
.empty-tip {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 60px 0;
  color: var(--kb-muted-foreground);
  font-size: 14px;
}

/* 文件网格 */
.file-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 16px;
}
.file-card {
  border: 1px solid var(--kb-border);
  border-radius: 10px;
  overflow: hidden;
  background: var(--kb-card);
  transition: box-shadow 0.15s ease, transform 0.15s ease;
}
.file-card:hover {
  box-shadow: var(--shadow-card-hover);
  transform: translateY(-2px);
}
.file-preview {
  width: 100%;
  height: 120px;
  background: var(--kb-muted);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  overflow: hidden;
}
.file-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.2s;
}
.file-preview:hover img {
  transform: scale(1.05);
}
.file-icon-large {
  color: var(--kb-muted-foreground);
}
.file-info {
  padding: 10px 12px;
}
.file-name {
  font-size: 12px;
  font-weight: 500;
  color: var(--kb-foreground);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-bottom: 4px;
}
.file-meta {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--kb-muted-foreground);
}
.meta-dot {
  opacity: 0.5;
}
.file-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 0 12px 10px;
}
.icon-btn {
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--kb-border);
  border-radius: 6px;
  background: var(--kb-card);
  color: var(--kb-muted-foreground);
  cursor: pointer;
  transition: all 0.15s;
}
.icon-btn:hover {
  background: var(--kb-muted);
  color: var(--kb-primary);
  border-color: var(--kb-primary);
}
.icon-btn.danger:hover {
  background: rgba(239, 68, 68, 0.08);
  color: var(--kb-destructive);
  border-color: var(--kb-destructive);
}

/* 分页 */
.pagination-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-top: 24px;
}
.page-btn {
  height: 32px;
  padding: 0 14px;
  border: 1px solid var(--kb-border);
  border-radius: 8px;
  background: var(--kb-card);
  color: var(--kb-foreground);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
}
.page-btn:hover:not(:disabled) {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
}
.page-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.page-info {
  font-size: 13px;
  color: var(--kb-muted-foreground);
}
</style>
