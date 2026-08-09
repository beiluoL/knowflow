<template>
  <div class="knowledge-import-page">
    <!-- 顶部导航 -->
    <div class="page-header">
      <div class="header-left">
        <button class="back-btn" @click="goBack">
          <Icon name="arrow-left" :size="18" />
          <span>返回知识库</span>
        </button>
      </div>
      <h1 class="page-title">目录一键四模块导入</h1>
      <div class="header-right">
        <router-link to="/knowledge/import" class="adv-import-link">
          <Icon name="folder-open" :size="16" />
          <span>普通目录导入</span>
        </router-link>
      </div>
    </div>

    <!-- 步骤指示器 -->
    <div class="step-indicator">
      <div
        v-for="(step, idx) in steps"
        :key="idx"
        class="step-item"
        :class="{ active: currentStep === idx, done: currentStep > idx }"
      >
        <div class="step-circle">
          <Icon v-if="currentStep > idx" name="check" :size="14" />
          <span v-else>{{ idx + 1 }}</span>
        </div>
        <span class="step-label">{{ step }}</span>
        <div v-if="idx < steps.length - 1" class="step-connector"></div>
      </div>
    </div>

    <div class="import-content">
      <!-- ===== Step 0: 选择导入来源 ===== -->
      <template v-if="currentStep === 0">
        <div class="step-panel">
          <!-- 智能导入向导 -->
          <div class="guide-card">
            <div class="guide-header">
              <Icon name="info" :size="20" class="guide-icon" />
              <h3>导入向导</h3>
            </div>
            <div class="guide-body">
              <div class="guide-section">
                <h4>两种导入方式</h4>
                <ul>
                  <li><strong>目录路径</strong>：填写服务器可访问的绝对路径（如 /Users/x/docs），或相对路径并指定基准目录，系统直接读取本地文件，适合大目录。</li>
                  <li><strong>文件选择 / 拖拽</strong>：选择本地目录或文件，前端预览文件列表后交由后端解析导入。</li>
                </ul>
              </div>
              <div class="guide-section">
                <h4>四模块批量生成</h4>
                <ul>
                  <li><strong>知识库</strong>：将 Markdown 解析为带层级分类的文档。</li>
                  <li><strong>学习路径</strong>：按目录层级自动生成章节并关联文档。</li>
                  <li><strong>闪卡 / 题库</strong>：基于 AI 提炼问答与测验题（需配置 AI Key）。</li>
                </ul>
              </div>
              <div class="guide-section">
                <h4>注意事项</h4>
                <ul>
                  <li>路径需由部署服务的服务器可访问，容器环境请使用挂载目录。</li>
                  <li>单次导入建议不超过 200 个文件，超大目录请分批导入。</li>
                </ul>
              </div>
            </div>
          </div>

          <!-- 导入方式 Tab -->
          <div class="import-mode-tabs">
            <button
              class="mode-tab"
              :class="{ active: sourceMode === 'path' }"
              @click="sourceMode = 'path'"
            >
              <Icon name="terminal" :size="16" />
              <span>目录路径</span>
            </button>
            <button
              class="mode-tab"
              :class="{ active: sourceMode === 'files' }"
              @click="sourceMode = 'files'"
            >
              <Icon name="upload" :size="16" />
              <span>文件选择 / 拖拽</span>
            </button>
          </div>

          <!-- 模式 A：目录路径输入（绝对 + 相对） -->
          <div v-if="sourceMode === 'path'" class="form-card">
            <h3 class="card-title">目录路径</h3>
            <p class="card-desc">填写服务器可访问的路径，支持绝对路径或相对路径（需指定基准）</p>

            <div class="form-group">
              <label class="form-label">绝对路径或相对路径</label>
              <div class="path-input-row">
                <div class="path-input-icon">
                  <Icon name="terminal" :size="20" />
                </div>
                <input
                  v-model="pathInput"
                  type="text"
                  class="path-input"
                  :class="{ 'input-error': pathError }"
                  placeholder="绝对路径如 /Users/xxx/docs/Obsidian 或相对路径如 ./uploads/notes"
                  @keyup.enter="handleScan"
                />
                <button class="btn-scan" :disabled="scanning || !pathInput.trim()" @click="handleScan">
                  <Icon :name="scanning ? 'loader' : 'search'" :size="14" :class="{ 'spin-icon': scanning }" />
                  <span>{{ scanning ? '扫描中' : '扫描' }}</span>
                </button>
              </div>
            </div>

            <div class="form-group">
              <label class="form-label">
                相对路径基准
                <span class="label-optional">（可选，仅当上方为相对路径时生效）</span>
              </label>
              <input
                v-model="relativeToInput"
                type="text"
                class="path-input standalone"
                placeholder="如 /app/uploads 或留空由服务解析"
                @keyup.enter="handleScan"
              />
            </div>

            <div v-if="pathError" class="scan-error-box">
              <div class="scan-error-head">
                <Icon name="alert-circle" :size="16" />
                <span class="scan-error-title">未找到可导入文件</span>
              </div>
              <p class="scan-error-msg">{{ pathError.message }}</p>
              <p v-if="pathError.path" class="scan-error-path">
                <span class="scan-error-k">扫描路径：</span>{{ pathError.path }}
              </p>
              <div class="scan-error-types">
                <span class="scan-error-k">支持的文件类型：</span>
                <span class="scan-error-types-list">{{ SUPPORTED_TYPES_TEXT }}</span>
              </div>
              <button class="btn-reselect" @click="reselectPath">
                <Icon name="rotate-ccw" :size="14" />
                <span>重新选择路径</span>
              </button>
            </div>
          </div>

          <!-- 模式 B：文件选择 / 拖拽 -->
          <div v-else class="form-card">
            <h3 class="card-title">选择文件或目录</h3>
            <p class="card-desc">点击选择或拖拽目录 / 多个文件到下方区域，将自动预览文件列表</p>

            <div
              class="drop-zone"
              :class="{ 'drag-over': dragging }"
              role="button"
              tabindex="0"
              aria-label="选择文件或拖拽目录到此处"
              @click="triggerFileInput"
              @keydown.enter.prevent="$event.target.click()"
              @keydown.space.prevent="$event.target.click()"
              @dragover.prevent="dragging = true"
              @dragleave.prevent="dragging = false"
              @drop.prevent="handleDrop"
            >
              <input
                ref="fileInputRef"
                type="file"
                class="hidden-file-input"
                webkitdirectory
                directory
                multiple
                @change="handleFileSelect"
              />
              <div class="dir-select-icon">
                <Icon name="upload" :size="32" />
              </div>
              <p class="dir-select-title">
                {{ selectedFiles.length > 0 ? `已选择 ${selectedFiles.length} 个文件` : '点击或拖拽目录 / 文件到此处' }}
              </p>
              <p class="dir-select-hint">
                推荐拖入整个目录（含子目录）；浏览器安全限制下将自动以文件公共父目录作为导入路径
              </p>
            </div>

            <div v-if="sourceMode === 'files' && derivedPath" class="form-group">
              <label class="form-label">推导的导入路径（可编辑）</label>
              <input
                v-model="pathInput"
                type="text"
                class="path-input standalone"
                placeholder="导入路径"
              />
              <p class="path-input-hint">
                拖拽/选择后自动推导，确认无误后即可扫描；如需精确控制请切换到「目录路径」手动填写。
              </p>
            </div>

            <div v-if="pathError" class="scan-error-box">
              <div class="scan-error-head">
                <Icon name="alert-circle" :size="16" />
                <span class="scan-error-title">未找到可导入文件</span>
              </div>
              <p class="scan-error-msg">{{ pathError.message }}</p>
              <p v-if="pathError.path" class="scan-error-path">
                <span class="scan-error-k">扫描路径：</span>{{ pathError.path }}
              </p>
              <div class="scan-error-types">
                <span class="scan-error-k">支持的文件类型：</span>
                <span class="scan-error-types-list">{{ SUPPORTED_TYPES_TEXT }}</span>
              </div>
              <button class="btn-reselect" @click="reselectPath">
                <Icon name="rotate-ccw" :size="14" />
                <span>重新选择路径</span>
              </button>
            </div>
          </div>

          <!-- 扫描结果预览 -->
          <div v-if="scanResult" class="preview-card">
            <div class="preview-header">
              <h3 class="card-title">
                已选文件预览
                <span class="scan-mode-tag">{{ scanResult.isFile ? '单文件' : '目录' }}</span>
              </h3>
              <div class="preview-stats">
                <span class="stat-badge doc">{{ scanResult.docCount }} 文档</span>
                <span v-if="scanResult.imageCount > 0" class="stat-badge img">{{ scanResult.imageCount }} 图片</span>
                <span v-if="scanResult.dirCount > 0" class="stat-badge dir">{{ scanResult.dirCount }} 目录</span>
              </div>
            </div>
            <div class="path-files-list">
              <div
                v-for="file in scanResult.files.slice(0, 200)"
                :key="file.path"
                class="path-file-item"
                :class="file.type"
              >
                <Icon :name="getFileIcon(file.ext, file.type)" :size="14" class="file-ext-icon" />
                <span class="file-path-text" :title="file.path">{{ file.path }}</span>
                <span class="file-size">{{ formatFileSize(file.size) }}</span>
              </div>
              <p v-if="scanResult.files.length > 200" class="more-files-hint">
                仅显示前 200 个文件，共 {{ scanResult.files.length }} 个
              </p>
            </div>
            <p v-if="scanResult.absolutePath" class="resolved-path-hint">
              <Icon name="check-circle" :size="14" />
              <span>解析路径：{{ scanResult.absolutePath }}</span>
            </p>
          </div>

          <div class="step-actions">
            <button class="btn-secondary" @click="goBack">取消</button>
            <button
              class="btn-primary"
              :disabled="!canProceed"
              @click="goToStep(1)"
            >
              <span>下一步：配置模块</span>
              <Icon name="chevron-right" :size="14" />
            </button>
          </div>
        </div>
      </template>

      <!-- ===== Step 1: 配置四模块 ===== -->
      <template v-if="currentStep === 1">
        <div class="step-panel">
          <div class="guide-card">
            <div class="guide-header">
              <Icon name="info" :size="20" class="guide-icon" />
              <h3>模块与知识库</h3>
            </div>
            <div class="guide-body">
              <div class="guide-section">
                <h4>勾选需要生成的模块</h4>
                <ul>
                  <li><strong>知识库</strong>始终执行（其他模块依赖其产出的文档），可搭配学习路径、闪卡、题库。</li>
                  <li>未配置 AI Key 时，闪卡 / 题库将降级跳过，可稍后在对应模块手动生成。</li>
                </ul>
              </div>
            </div>
          </div>

          <div class="form-card">
            <h3 class="card-title">目标知识库</h3>
            <p class="card-desc">四模块的知识库部分将导入到该知识库（需 Owner / Editor 权限）</p>
            <div class="kb-select-grid">
              <div
                v-for="cat in kbCategories"
                :key="cat.id"
                class="kb-select-item"
                :class="{ selected: selectedKbId === cat.id }"
                role="button"
                tabindex="0"
                :aria-pressed="selectedKbId === cat.id"
                @click="selectedKbId = cat.id"
                @keydown.enter.prevent="$event.target.click()"
                @keydown.space.prevent="$event.target.click()"
              >
                <div class="kb-select-icon" :style="{ background: getKbColor(cat.id) }">
                  <Icon :name="getCategoryIcon(cat.icon)" :size="20" />
                </div>
                <div class="kb-select-info">
                  <p class="kb-name">{{ cat.name }}</p>
                  <p class="kb-count">{{ cat.docCount || 0 }} 篇文档</p>
                </div>
                <Icon v-if="selectedKbId === cat.id" name="check-circle" :size="20" class="check-icon" />
              </div>
            </div>
            <p v-if="kbCategories.length === 0" class="empty-hint">
              暂无可导入的知识库，请先
              <router-link to="/knowledge/new" class="link-btn">新建知识库</router-link>。
            </p>
          </div>

          <div class="form-card">
            <h3 class="card-title">生成模块</h3>
            <p class="card-desc">选择一次性批量生成的模块（知识库始终包含）</p>
            <div class="module-grid">
              <button
                v-for="m in moduleOptions"
                :key="m.key"
                class="module-item"
                :class="{ selected: selectedModules.includes(m.key) }"
                @click="toggleModule(m.key)"
              >
                <Icon :name="m.icon" :size="20" class="module-icon" />
                <div class="module-info">
                  <p class="module-name">{{ m.label }}</p>
                  <p class="module-desc">{{ m.desc }}</p>
                </div>
                <Icon v-if="selectedModules.includes(m.key)" name="check-circle" :size="18" class="module-check" />
              </button>
            </div>
          </div>

          <div class="form-card">
            <h3 class="card-title">导入选项</h3>
            <div class="option-list">
              <label class="checkbox-row">
                <input type="checkbox" v-model="createSubCategories" />
                <span>按目录创建子分类（保留目录层级）</span>
              </label>
              <label class="checkbox-row">
                <input type="checkbox" v-model="autoTags" />
                <span>自动生成标签（基于文档结构）</span>
              </label>
              <label class="checkbox-row">
                <input type="checkbox" v-model="incremental" />
                <span>增量去重（跳过内容未变更的文件）</span>
              </label>
            </div>
          </div>

          <div class="step-actions">
            <button class="btn-secondary" @click="goToStep(0)">
              <Icon name="chevron-left" :size="14" />
              <span>上一步</span>
            </button>
            <button class="btn-primary" :disabled="!selectedKbId" @click="startImport">
              <Icon name="rocket" :size="14" />
              <span>开始四模块导入</span>
            </button>
          </div>
        </div>
      </template>

      <!-- ===== Step 2: 执行导入（进度） ===== -->
      <template v-if="currentStep === 2">
        <div class="step-panel">
          <div class="form-card">
            <h3 class="card-title">导入进度</h3>
            <p class="card-desc">
              {{ progress.total > 0 ? `正在处理 ${progress.current} / ${progress.total} 个文件` : '正在初始化…' }}
            </p>

            <div class="progress-bar-wrap">
              <div class="progress-bar" :style="{ width: progressPercent + '%' }"></div>
            </div>
            <p class="progress-percent">{{ progressPercent }}%</p>

            <div v-if="currentFile" class="current-file">
              <Icon name="loader" :size="14" class="spin-icon" />
              <span class="current-file-path" :title="currentFile">{{ currentFile }}</span>
            </div>

            <div v-if="progressLogs.length" class="path-files-list log-list">
              <div
                v-for="(log, i) in progressLogs.slice(-100).reverse()"
                :key="i"
                class="log-item"
                :class="log.status"
              >
                <Icon :name="logIcon(log.status)" :size="14" class="log-icon" />
                <span class="log-path" :title="log.path">{{ log.path }}</span>
                <span class="log-msg">{{ log.message }}</span>
              </div>
            </div>
          </div>

          <div class="step-actions">
            <span class="actions-spacer" />
            <button v-if="!importing" class="btn-secondary" @click="goToStep(1)">
              <Icon name="chevron-left" :size="14" />
              <span>上一步</span>
            </button>
            <button v-if="importing" class="btn-secondary" @click="cancelImport">
              <Icon name="x-circle" :size="14" />
              <span>取消导入</span>
            </button>
          </div>
        </div>
      </template>

      <!-- ===== Step 3: 完成 ===== -->
      <template v-if="currentStep === 3">
        <div class="step-panel">
          <div class="result-banner">
            <Icon name="check-circle" :size="40" class="result-icon" />
            <h2>导入完成</h2>
            <p class="result-sub">已生成以下模块内容</p>
          </div>

          <div v-if="importResult" class="result-grid">
            <div class="result-stat">
              <Icon name="file-text" :size="18" />
              <div>
                <p class="result-num">{{ importResult.docCount }}</p>
                <p class="result-label">知识库文档</p>
              </div>
            </div>
            <div class="result-stat">
              <Icon name="image" :size="18" />
              <div>
                <p class="result-num">{{ importResult.imageCount }}</p>
                <p class="result-label">迁移图片</p>
              </div>
            </div>
            <div v-if="hasModule('path')" class="result-stat">
              <Icon name="book-open" :size="18" />
              <div>
                <p class="result-num">{{ importResult.chapterCount }}</p>
                <p class="result-label">学习路径章节</p>
              </div>
            </div>
            <div v-if="hasModule('flashcard')" class="result-stat">
              <Icon name="file-text" :size="18" />
              <div>
                <p class="result-num">{{ importResult.flashcardCount }}</p>
                <p class="result-label">闪卡</p>
              </div>
            </div>
            <div v-if="hasModule('quiz')" class="result-stat">
              <Icon name="help-circle" :size="18" />
              <div>
                <p class="result-num">{{ importResult.quizCount }}</p>
                <p class="result-label">题库</p>
              </div>
            </div>
          </div>

          <p v-if="importResult?.message" class="import-message">
            <Icon name="info" :size="14" />
            <span>{{ importResult.message }}</span>
          </p>

          <div class="step-actions">
            <button class="btn-secondary" @click="goToStep(0)">
              <Icon name="rotate-ccw" :size="14" />
              <span>再次导入</span>
            </button>
            <button class="btn-primary" @click="goToResult">
              <Icon name="arrow-right" :size="14" />
              <span>前往知识库</span>
            </button>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { notify } from '@/utils/toast'
import { obsidianImportApi, type ObsidianModule } from '@/api/obsidianImport'
import type { PathImportScanVO, CategoryVO } from '@/api/types'

const router = useRouter()

const steps = ['选择来源', '配置模块', '执行导入', '完成']
const currentStep = ref(0)

function goToStep(idx: number) {
  if (idx >= 0 && idx < steps.length) currentStep.value = idx
}
function goBack() {
  router.back()
}
function goToResult() {
  router.push('/knowledge')
}

// ============ Step 0: 来源 ============
const sourceMode = ref<'path' | 'files'>('path')
const pathInput = ref('')
const relativeToInput = ref('')
const scanning = ref(false)
interface ScanError {
  path: string
  message: string
}
const pathError = ref<ScanError | null>(null)
const scanResult = ref<PathImportScanVO | null>(null)

// 与后端 PathImportService.DOC_EXTS / IMAGE_EXTS 对齐的支持类型
const SUPPORTED_TYPES = [
  { ext: '.md / .markdown', label: 'Markdown 笔记' },
  { ext: '.txt', label: '纯文本' },
  { ext: '.pdf', label: 'PDF 文档' },
  { ext: '.doc / .docx', label: 'Word 文档' },
  { ext: '.ppt / .pptx', label: 'PowerPoint' },
  { ext: '.html / .htm', label: '网页' },
  { ext: '.rtf', label: '富文本' },
  { ext: '.jpg / .png / .gif / .webp / .svg', label: '图片' },
]
const SUPPORTED_TYPES_TEXT = SUPPORTED_TYPES.map((t) => t.ext).join('、')

const selectedFiles = ref<File[]>([])
const dragging = ref(false)
const fileInputRef = ref<HTMLInputElement | null>(null)

const derivedPath = computed(() => {
  if (sourceMode.value !== 'files' || selectedFiles.value.length === 0) return ''
  const first = selectedFiles.value[0]
  const rel = (first as any).webkitRelativePath as string | undefined
  if (rel && rel.includes('/')) {
    const root = rel.split('/')[0]
    return root
  }
  // 单个文件：以文件名所在（相对）目录作为导入路径
  return first.name
})

const canProceed = computed(() => !!scanResult.value && !scanning.value)

function triggerFileInput() {
  fileInputRef.value?.click()
}

function handleFileSelect(e: Event) {
  const input = e.target as HTMLInputElement
  if (input.files) {
    selectedFiles.value = Array.from(input.files)
    pathInput.value = derivedPath.value
    autoScanForFiles()
  }
}

function handleDrop(e: DragEvent) {
  dragging.value = false
  const items = e.dataTransfer?.files
  if (items && items.length) {
    selectedFiles.value = Array.from(items)
    pathInput.value = derivedPath.value
    autoScanForFiles()
  } else {
    pathError.value = {
      path: pathInput.value.trim(),
      message: '请拖入目录或文件后再扫描。',
    }
  }
}

async function autoScanForFiles() {
  // 文件模式无法直接拿到绝对路径，先进入扫描用相对根目录预览；
  // 若后端无法解析则提示切换目录路径模式。
  await handleScan()
}

// 重新选择路径：清空扫描结果并聚焦路径输入框，方便快速修正
function reselectPath() {
  pathError.value = null
  scanResult.value = null
  sourceMode.value = 'path'
  if (sourceMode.value === 'path') {
    const el = document.querySelector<HTMLInputElement>('.path-input:not(.standalone)')
    el?.focus()
  }
}

async function handleScan() {
  const path = pathInput.value.trim()
  if (!path) {
    pathError.value = {
      path: '',
      message: '请输入路径或先选择文件后再扫描。',
    }
    return
  }
  scanning.value = true
  pathError.value = null
  scanResult.value = null
  try {
    const res = await obsidianImportApi.scan({
      path,
      relativeTo: relativeToInput.value.trim() || undefined,
    })
    scanResult.value = res.data
    const vo = res.data
    const hasFiles = vo && (vo.docCount > 0 || vo.imageCount > 0 || (vo.files && vo.files.length > 0))
    if (!hasFiles) {
      // 未发现可导入文件，给出含具体路径、支持类型与检查建议的友好提示
      pathError.value = {
        path,
        message:
          '该路径下未发现可导入的文件。请检查：① 路径是否正确（服务器可访问的绝对路径）；' +
          '② 文件夹是否为空；③ 文件扩展名是否在支持范围内。',
      }
    } else {
      notify(`扫描完成，共 ${vo!.docCount} 个文档`, 'success')
    }
  } catch (e: any) {
    pathError.value = {
      path,
      message: e?.message || '路径扫描失败，请确认路径是否可被服务访问，或切换为「目录路径」手动填写绝对路径。',
    }
  } finally {
    scanning.value = false
  }
}

// ============ Step 1: 模块配置 ============
const kbCategories = ref<CategoryVO[]>([])
const selectedKbId = ref<number | null>(null)

const moduleOptions: { key: ObsidianModule; label: string; desc: string; icon: string }[] = [
  { key: 'knowledge', label: '知识库', desc: '解析为带层级分类的文档（必选）', icon: 'file-text' },
  { key: 'path', label: '学习路径', desc: '按目录层级生成章节并关联文档', icon: 'book-open' },
  { key: 'flashcard', label: '闪卡', desc: 'AI 提炼问答卡片', icon: 'layers' },
  { key: 'quiz', label: '题库', desc: 'AI 生成选择/判断/简答题', icon: 'help-circle' },
]
const selectedModules = ref<ObsidianModule[]>(['knowledge', 'path', 'flashcard', 'quiz'])

const createSubCategories = ref(true)
const autoTags = ref(true)
const incremental = ref(true)

function toggleModule(key: ObsidianModule) {
  // knowledge 始终包含
  if (key === 'knowledge') return
  const idx = selectedModules.value.indexOf(key)
  if (idx >= 0) selectedModules.value.splice(idx, 1)
  else selectedModules.value.push(key)
}
function hasModule(key: ObsidianModule) {
  return importResult.value?.generatedModules?.includes(key) ?? selectedModules.value.includes(key)
}

async function loadKbs() {
  try {
    const res = await obsidianImportApi.listEditableKbs()
    kbCategories.value = res.data ?? []
    if (kbCategories.value.length) selectedKbId.value = kbCategories.value[0].id
  } catch {
    kbCategories.value = []
  }
}

// ============ Step 2: 执行导入（SSE 进度） ============
const importing = ref(false)
const progress = ref({ current: 0, total: 0 })
const currentFile = ref('')
interface ProgressLog { path: string; status: string; message: string }
const progressLogs = ref<ProgressLog[]>([])
const importResult = ref<{
  docCount: number
  imageCount: number
  chapterCount: number
  flashcardCount: number
  quizCount: number
  generatedModules: ObsidianModule[]
  message?: string
} | null>(null)

let cancelFn: (() => Promise<void>) | null = null

const progressPercent = computed(() => {
  if (progress.value.total <= 0) return importing.value ? 5 : 0
  return Math.min(99, Math.round((progress.value.current / progress.value.total) * 100))
})

function logIcon(status: string) {
  if (status === 'success' || status === 'ok') return 'check-circle'
  if (status === 'skip') return 'skip-forward'
  if (status === 'error' || status === 'fail') return 'x-circle'
  return 'file-text'
}

function startImport() {
  if (!selectedKbId.value) return
  importing.value = true
  progress.value = { current: 0, total: 0 }
  currentFile.value = ''
  progressLogs.value = []
  importResult.value = null
  currentStep.value = 2

  const params = {
    path: pathInput.value.trim(),
    relativeTo: relativeToInput.value.trim() || undefined,
    targetCategoryId: selectedKbId.value,
    modules: selectedModules.value,
    createSubCategories: createSubCategories.value,
    autoTags: autoTags.value,
    incremental: incremental.value,
  }

  cancelFn = obsidianImportApi
    .importStream(params, {
      onStart: (data) => {
        progress.value.total = data.total
      },
      onFileStart: (data) => {
        currentFile.value = data.path
        progress.value.current = data.index
      },
      onFileDone: (data) => {
        progress.value.current = data.index
        progressLogs.value.push({
          path: data.path,
          status: data.status || 'ok',
          message: data.message || '',
        })
      },
      onComplete: (result: any) => {
        importing.value = false
        progress.value.current = progress.value.total
        importResult.value = {
          docCount: result.docCount ?? 0,
          imageCount: result.imageCount ?? 0,
          chapterCount: result.chapterCount ?? 0,
          flashcardCount: result.flashcardCount ?? 0,
          quizCount: result.quizCount ?? 0,
          generatedModules: (result.generatedModules ?? selectedModules.value) as ObsidianModule[],
          message: result.message,
        }
        notify('导入完成', 'success')
        currentStep.value = 3
      },
      onError: (error) => {
        importing.value = false
        pathError.value = error
        notify(error, 'error')
        currentStep.value = 1
      },
      onCancel: () => {
        importing.value = false
        notify('已取消导入', 'info')
      },
    })
    .cancel
}

async function cancelImport() {
  if (cancelFn) await cancelFn()
  cancelFn = null
}

// ============ 工具 ============
function getFileIcon(ext: string, type: string) {
  if (type === 'image') return 'image'
  const code = ['java', 'py', 'js', 'ts', 'vue', 'go', 'css', 'html', 'xml', 'json', 'sql', 'yml', 'md']
  if (code.includes(ext.replace('.', ''))) return 'code'
  if (ext === 'pdf' || ext === 'doc' || ext === 'docx') return 'file-text'
  return 'file'
}
function formatFileSize(bytes: number) {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  let val = bytes
  while (val >= 1024 && i < units.length - 1) {
    val /= 1024
    i++
  }
  return `${val.toFixed(val >= 10 || i === 0 ? 0 : 1)} ${units[i]}`
}
const KB_COLORS = ['#3b82f6', '#8b5cf6', '#10b981', '#f59e0b', '#ef4444', '#06b6d4', '#ec4899']
function getKbColor(id?: number) {
  return KB_COLORS[(id ?? 0) % KB_COLORS.length]
}
function getCategoryIcon(icon?: string) {
  const map: Record<string, string> = {
    book: 'book-open',
    code: 'code',
    brain: 'file-text',
   灯泡: 'file-text',
    folder: 'folder',
  }
  return map[icon || ''] || 'folder'
}

onMounted(loadKbs)
</script>

<style scoped>
.knowledge-import-page {
  min-height: 100vh;
  background: var(--kb-background);
  padding: 24px 16px 48px;
}

.page-header {
  max-width: 960px;
  margin: 0 auto 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.header-left,
.header-right {
  display: flex;
  flex: 1;
}
.header-left {
  justify-content: flex-start;
}
.header-right {
  justify-content: flex-end;
}

.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-md);
  color: var(--kb-foreground);
  font-size: var(--kb-fs-body-md);
  cursor: pointer;
  transition: background 0.15s, transform 0.15s;
}
.back-btn:hover {
  background: var(--kb-muted);
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
  font-weight: 600;
  color: var(--kb-foreground);
  margin: 0;
  text-align: center;
}

.adv-import-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--kb-fs-body-md);
  color: var(--kb-primary);
  text-decoration: none;
  border-radius: var(--kb-radius-sm);
  transition: opacity 0.15s;
}
.adv-import-link:hover {
  text-decoration: underline;
}
.adv-import-link:active {
  opacity: 0.7;
}
.adv-import-link:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}

/* 步骤指示器 */
.step-indicator {
  max-width: 960px;
  margin: 0 auto 24px;
  display: flex;
  align-items: center;
}
.step-item {
  display: flex;
  align-items: center;
  flex: 1;
  position: relative;
}
.step-item:last-child {
  flex: 0 0 auto;
}
.step-circle {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--kb-muted);
  color: var(--kb-muted-foreground);
  font-size: var(--kb-fs-body-md);
  font-weight: 600;
  flex-shrink: 0;
  transition: all 0.2s;
  z-index: 1;
}
.step-label {
  margin-left: 8px;
  font-size: var(--kb-fs-body-md);
  color: var(--kb-muted-foreground);
  white-space: nowrap;
}
.step-item.active .step-circle {
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
}
.step-item.active .step-label {
  color: var(--kb-foreground);
  font-weight: 500;
}
.step-item.done .step-circle {
  background: var(--kb-accent);
  color: var(--kb-accent-foreground);
}
.step-item.done .step-label {
  color: var(--kb-foreground);
}
.step-connector {
  flex: 1;
  height: 2px;
  background: var(--kb-border);
  margin: 0 12px;
}

.import-content {
  max-width: 960px;
  margin: 0 auto;
}

.step-panel {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* 向导卡片 */
.guide-card {
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-lg);
  padding: 18px 20px;
}
.guide-header {
  display: flex;
  align-items: center;
  gap: 10px;
}
.guide-icon {
  color: var(--kb-primary);
}
.guide-header h3 {
  margin: 0;
  font-size: var(--kb-fs-body-lg);
  font-weight: 600;
  color: var(--kb-foreground);
}
.guide-body {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.guide-section h4 {
  margin: 0 0 6px;
  font-size: var(--kb-fs-body-md);
  font-weight: 600;
  color: var(--kb-foreground);
}
.guide-section ul {
  margin: 0;
  padding-left: 18px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.guide-section li {
  font-size: var(--kb-fs-body-md);
  color: var(--kb-muted-foreground);
  line-height: 1.6;
}
.guide-section code {
  background: var(--kb-muted);
  padding: 1px 5px;
  border-radius: 4px;
  font-size: var(--kb-fs-caption);
  font-family: var(--font-mono);
  color: var(--kb-foreground);
}

/* 表单卡片 */
.form-card {
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-lg);
  padding: 20px;
}
.card-title {
  margin: 0;
  font-size: var(--kb-fs-body-lg);
  font-weight: 600;
  color: var(--kb-foreground);
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.card-desc {
  margin: 6px 0 16px;
  font-size: var(--kb-fs-body-md);
  color: var(--kb-muted-foreground);
  line-height: 1.6;
}
.form-group {
  margin-bottom: 16px;
}
.form-group:last-child {
  margin-bottom: 0;
}
.form-label {
  display: block;
  font-size: var(--kb-fs-body-md);
  font-weight: 500;
  color: var(--kb-foreground);
  margin-bottom: 8px;
}
.label-optional {
  font-weight: 400;
  color: var(--kb-muted-foreground);
}
.input-error {
  border-color: var(--kb-destructive) !important;
}

/* 导入方式 Tab */
.import-mode-tabs {
  display: flex;
  gap: 8px;
  background: var(--kb-muted);
  padding: 4px;
  border-radius: var(--kb-radius-md);
  width: fit-content;
}
.mode-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border: none;
  background: transparent;
  border-radius: var(--kb-radius-sm);
  color: var(--kb-muted-foreground);
  font-size: var(--kb-fs-body-md);
  cursor: pointer;
  transition: all 0.15s;
}
.mode-tab:hover:not(.active) {
  color: var(--kb-foreground);
  background: rgba(59, 111, 224, 0.06);
}
.mode-tab:active {
  transform: scale(0.98);
}
.mode-tab:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}
.mode-tab.active {
  background: var(--kb-card);
  color: var(--kb-foreground);
  box-shadow: var(--shadow-sm);
  font-weight: 500;
}

/* 路径输入 */
.path-input-row {
  display: flex;
  align-items: center;
  background: var(--kb-background);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-md);
  overflow: hidden;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.path-input-row:focus-within {
  border-color: var(--kb-primary);
  box-shadow: 0 0 0 3px rgba(59, 111, 224, 0.15);
}
.path-input-icon {
  padding: 0 10px;
  color: var(--kb-muted-foreground);
  display: flex;
}
.path-input {
  flex: 1;
  border: none;
  background: transparent;
  padding: 11px 12px;
  font-size: var(--kb-fs-body-md);
  color: var(--kb-foreground);
  outline: none;
}
.path-input.standalone {
  width: 100%;
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-md);
  background: var(--kb-background);
  padding: 11px 12px;
}
.btn-scan {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin: 4px;
  padding: 8px 16px;
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  border: none;
  border-radius: var(--kb-radius-sm);
  font-size: var(--kb-fs-body-md);
  cursor: pointer;
  white-space: nowrap;
  transition: opacity 0.15s, transform 0.15s;
}
.btn-scan:hover:not(:disabled) {
  opacity: 0.9;
}
.btn-scan:active:not(:disabled) {
  transform: scale(0.98);
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
  margin: 8px 0 0;
  font-size: var(--kb-fs-body-md);
  color: var(--kb-muted-foreground);
  line-height: 1.5;
}
.field-error {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 10px 0 0;
  font-size: var(--kb-fs-body-md);
  color: var(--kb-destructive);
}

/* 扫描错误提示卡片 */
.scan-error-box {
  margin: 14px 0 0;
  padding: 14px 16px;
  background: rgba(239, 68, 68, 0.08);
  border: 1px solid var(--kb-destructive);
  border-radius: var(--kb-radius-md);
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.scan-error-head {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--kb-destructive);
  font-weight: 600;
  font-size: var(--kb-fs-body-md);
}
.scan-error-title {
  color: var(--kb-destructive);
}
.scan-error-msg {
  margin: 0;
  font-size: var(--kb-fs-body-md);
  font-weight: 400;
  color: var(--kb-foreground);
  line-height: 1.6;
}
.scan-error-path {
  margin: 0;
  font-size: var(--kb-fs-body-md);
  color: var(--kb-foreground);
  word-break: break-all;
}
.scan-error-k {
  font-weight: 600;
  color: var(--kb-foreground);
}
.scan-error-types {
  font-size: var(--kb-fs-body-md);
  color: var(--kb-foreground);
  line-height: 1.6;
}
.scan-error-types-list {
  font-weight: 400;
  color: var(--kb-muted-foreground);
}
.btn-reselect {
  align-self: flex-start;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
  padding: 8px 16px;
  background: var(--kb-card);
  color: var(--kb-foreground);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-md);
  font-size: var(--kb-fs-body-md);
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}
.btn-reselect:hover {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
}
.btn-reselect:active {
  transform: scale(0.98);
}
.btn-reselect:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}

/* 拖拽区 */
.drop-zone {
  border: 2px dashed var(--kb-border);
  border-radius: var(--kb-radius-lg);
  padding: 32px 20px;
  text-align: center;
  cursor: pointer;
  transition: all 0.15s;
  background: var(--kb-muted);
}
.drop-zone:hover {
  border-color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.04);
}
.drop-zone:active {
  transform: scale(0.99);
}
.drop-zone:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}
.drop-zone.drag-over {
  border-color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.06);
}
.dir-select-icon {
  color: var(--kb-muted-foreground);
  margin-bottom: 10px;
}
.dir-select-title {
  margin: 0;
  font-size: var(--kb-fs-body-lg);
  font-weight: 500;
  color: var(--kb-foreground);
}
.dir-select-hint {
  margin: 6px 0 0;
  font-size: var(--kb-fs-body-md);
  color: var(--kb-muted-foreground);
}
.hidden-file-input {
  display: none;
}

/* 预览卡片 */
.preview-card {
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-lg);
  padding: 20px;
}
.preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}
.scan-mode-tag {
  font-size: var(--kb-fs-caption);
  padding: 2px 8px;
  background: var(--kb-muted);
  border-radius: 9999px;
  color: var(--kb-muted-foreground);
  font-weight: 400;
}
.preview-stats {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.stat-badge {
  font-size: var(--kb-fs-caption);
  padding: 3px 10px;
  border-radius: 9999px;
  font-weight: 500;
}
.stat-badge.doc {
  background: rgba(59, 111, 224, 0.12);
  color: var(--kb-primary);
}
.stat-badge.img {
  background: rgba(16, 185, 129, 0.12);
  color: var(--kb-accent);
}
.stat-badge.dir {
  background: rgba(245, 158, 11, 0.12);
  color: var(--kb-warning);
}
.path-files-list {
  max-height: 320px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.path-file-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: var(--kb-radius-sm);
  font-size: var(--kb-fs-caption);
}
.path-file-item:hover {
  background: var(--kb-muted);
}
.file-ext-icon {
  color: var(--kb-muted-foreground);
  flex-shrink: 0;
}
.file-path-text {
  flex: 1;
  color: var(--kb-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.file-size {
  color: var(--kb-muted-foreground);
  flex-shrink: 0;
}
.more-files-hint {
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
  font-style: italic;
  padding: 6px 8px;
}
.resolved-path-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 12px 0 0;
  font-size: var(--kb-fs-caption);
  color: var(--kb-accent);
}

/* 知识库选择 */
.kb-select-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
}
.kb-select-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-md);
  cursor: pointer;
  transition: all 0.15s;
  position: relative;
}
.kb-select-item:hover {
  border-color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.04);
}
.kb-select-item:active {
  transform: scale(0.99);
}
.kb-select-item:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}
.kb-select-item.selected {
  border-color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.06);
}
.kb-select-icon {
  width: 40px;
  height: 40px;
  border-radius: var(--kb-radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}
.kb-select-info {
  flex: 1;
  min-width: 0;
}
.kb-name {
  margin: 0;
  font-size: var(--kb-fs-body-md);
  font-weight: 500;
  color: var(--kb-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.kb-count {
  margin: 2px 0 0;
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
}
.check-icon {
  color: var(--kb-primary);
  flex-shrink: 0;
}
.empty-hint {
  font-size: var(--kb-fs-body-md);
  color: var(--kb-muted-foreground);
  line-height: 1.6;
}
.link-btn {
  color: var(--kb-primary);
  text-decoration: none;
  border-radius: var(--kb-radius-sm);
  transition: opacity 0.15s;
}
.link-btn:hover {
  text-decoration: underline;
}
.link-btn:active {
  opacity: 0.7;
}
.link-btn:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}

/* 模块选择 */
.module-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
}
.module-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-md);
  cursor: pointer;
  background: var(--kb-card);
  text-align: left;
  transition: all 0.15s;
  position: relative;
}
.module-item:hover {
  border-color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.04);
}
.module-item:active {
  transform: scale(0.99);
}
.module-item:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}
.module-item.selected {
  border-color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.06);
}
.module-icon {
  color: var(--kb-primary);
  flex-shrink: 0;
  margin-top: 2px;
}
.module-info {
  flex: 1;
  min-width: 0;
}
.module-name {
  margin: 0;
  font-size: var(--kb-fs-body-md);
  font-weight: 500;
  color: var(--kb-foreground);
}
.module-desc {
  margin: 4px 0 0;
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
  line-height: 1.5;
}
.module-check {
  color: var(--kb-primary);
  flex-shrink: 0;
  margin-top: 2px;
}

/* 选项 */
.option-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.checkbox-row {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: var(--kb-fs-body-md);
  color: var(--kb-foreground);
  cursor: pointer;
  padding: 4px;
  margin: -4px;
  border-radius: var(--kb-radius-sm);
  transition: background 0.15s;
}
.checkbox-row:hover {
  background: rgba(59, 111, 224, 0.04);
}
.checkbox-row:focus-within {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}
.checkbox-row input {
  cursor: pointer;
  accent-color: var(--kb-primary);
}

/* 操作按钮 */
.step-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.actions-spacer {
  flex: 1;
}
.btn-primary,
.btn-secondary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 11px 22px;
  border-radius: var(--kb-radius-md);
  font-size: var(--kb-fs-body-md);
  font-weight: 500;
  cursor: pointer;
  border: 1px solid transparent;
  transition: all 0.15s;
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
}
.btn-primary:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}
.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.btn-secondary {
  background: var(--kb-card);
  color: var(--kb-foreground);
  border-color: var(--kb-border);
}
.btn-secondary:hover {
  background: var(--kb-muted);
}
.btn-secondary:active {
  transform: scale(0.98);
}
.btn-secondary:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}

/* 进度 */
.progress-bar-wrap {
  margin-top: 16px;
  height: 10px;
  background: var(--kb-muted);
  border-radius: 9999px;
  overflow: hidden;
}
.progress-bar {
  height: 100%;
  background: var(--kb-primary);
  border-radius: 9999px;
  transition: width 0.3s ease;
}
.progress-percent {
  margin: 8px 0 0;
  font-size: var(--kb-fs-body-md);
  font-weight: 600;
  color: var(--kb-foreground);
  text-align: right;
  font-variant-numeric: tabular-nums;
}
.current-file {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 14px;
  font-size: var(--kb-fs-body-md);
  color: var(--kb-muted-foreground);
}
.current-file-path {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.log-list {
  margin-top: 14px;
  max-height: 280px;
}
.log-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  font-size: var(--kb-fs-caption);
}
.log-icon {
  flex-shrink: 0;
}
.log-item.success .log-icon,
.log-item.ok .log-icon {
  color: var(--kb-accent);
}
.log-item.error .log-icon,
.log-item.fail .log-icon {
  color: var(--kb-destructive);
}
.log-item.skip .log-icon {
  color: var(--kb-muted-foreground);
}
.log-path {
  flex: 1;
  color: var(--kb-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.log-msg {
  color: var(--kb-muted-foreground);
  flex-shrink: 0;
}

/* 结果 */
.result-banner {
  text-align: center;
  padding: 16px 0;
}
.result-icon {
  color: var(--kb-accent);
}
.result-banner h2 {
  margin: 10px 0 4px;
  font-size: var(--kb-fs-h4);
  color: var(--kb-foreground);
}
.result-sub {
  margin: 0;
  font-size: var(--kb-fs-body-md);
  color: var(--kb-muted-foreground);
}
.result-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 12px;
}
.result-stat {
  display: flex;
  align-items: center;
  gap: 12px;
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-md);
  padding: 16px;
  color: var(--kb-primary);
}
.result-stat > div {
  flex: 1;
}
.result-num {
  margin: 0;
  font-size: var(--kb-fs-h4);
  font-weight: 700;
  color: var(--kb-foreground);
  font-variant-numeric: tabular-nums;
}
.result-label {
  margin: 2px 0 0;
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
}
.import-message {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--kb-fs-body-md);
  color: var(--kb-muted-foreground);
  background: var(--kb-muted);
  padding: 10px 14px;
  border-radius: var(--kb-radius-md);
}

.spin-icon {
  animation: spin 1s linear infinite;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

/* 响应式 */
@media (max-width: 768px) {
  .page-header {
    flex-wrap: wrap;
    gap: 8px;
  }
  .page-title {
    order: -1;
    width: 100%;
    text-align: left;
  }
  .import-mode-tabs {
    width: 100%;
  }
  .mode-tab {
    flex: 1;
    justify-content: center;
  }
  .kb-select-grid,
  .module-grid {
    grid-template-columns: 1fr;
  }
  .step-actions {
    flex-direction: column-reverse;
  }
  .step-actions .btn-primary,
  .step-actions .btn-secondary {
    width: 100%;
    justify-content: center;
  }
  .step-label {
    display: none;
  }
}
</style>
