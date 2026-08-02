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
      <h1 class="page-title">导入知识库</h1>
      <div class="header-right"></div>
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
      <!-- ===== Step 0: 选择目标知识库 + 导入引导 ===== -->
      <template v-if="currentStep === 0">
        <div class="step-panel">
          <!-- 导入引导提示 -->
          <div class="guide-card">
            <div class="guide-header">
              <Icon name="info" :size="20" class="guide-icon" />
              <h3>导入指南</h3>
            </div>
            <div class="guide-body">
              <div class="guide-section">
                <h4>支持的目录格式</h4>
                <ul>
                  <li><strong>Obsidian 仓库</strong>：直接选择 Obsidian 仓库根目录，自动解析 <code>.md</code> 文件及引用的图片</li>
                  <li><strong>本地 Markdown 目录</strong>：选择任意包含 <code>.md</code> / <code>.markdown</code> / <code>.txt</code> 文件的目录</li>
                  <li>目录层级将自动映射为知识库的分类树（最多 3 级，超出部分转为标签）</li>
                </ul>
              </div>
              <div class="guide-section">
                <h4>图片目录约定</h4>
                <ul>
                  <li>文档中引用的图片（<code>![[image.png]]</code> 或 <code>![](path)</code>）会自动迁移到服务器</li>
                  <li>支持以下常见图片目录：<code>image/</code>、<code>images/</code>、<code>attachments/</code>、<code>assets/</code></li>
                  <li>也支持与文档同级的图片文件</li>
                  <li>支持的图片格式：JPG、PNG、GIF、WebP、SVG、BMP、ICO</li>
                </ul>
              </div>
              <div class="guide-section">
                <h4>导入前注意事项</h4>
                <ul>
                  <li>确保目录中的 Markdown 文件使用 UTF-8 编码</li>
                  <li>单次导入文件总数建议不超过 200 个，超大目录请分批导入</li>
                  <li>开启「增量去重」后，重复导入同一目录会自动跳过未变更文件</li>
                  <li>AI 智能打标需要配置 AI Key，且会消耗额外时间</li>
                </ul>
              </div>
            </div>
          </div>

          <!-- 选择目标知识库 -->
          <div class="form-card">
            <h3 class="card-title">选择目标知识库</h3>
            <p class="card-desc">导入的文档将作为该知识库的子分类组织</p>
            <div class="kb-select-grid">
              <div
                v-for="cat in kbCategories"
                :key="cat.id"
                class="kb-select-item"
                :class="{ selected: selectedKbId === cat.id }"
                @click="selectedKbId = cat.id"
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
              暂无知识库，请先
              <router-link to="/knowledge/new" class="link-btn">新建知识库</router-link>
            </p>
          </div>

          <div class="step-actions">
            <button class="btn-secondary" @click="goBack">取消</button>
            <button
              class="btn-primary"
              :disabled="!selectedKbId"
              @click="goToStep(1)"
            >
              <span>下一步：选择目录</span>
              <Icon name="chevron-right" :size="14" />
            </button>
          </div>
        </div>
      </template>

      <!-- ===== Step 1: 选择目录 + 预览结构 ===== -->
      <template v-if="currentStep === 1">
        <div class="step-panel">
          <!-- 目录选择区 -->
          <div class="dir-select-zone" @click="triggerDirInput">
            <input
              ref="dirInputRef"
              type="file"
              class="hidden-file-input"
              webkitdirectory
              directory
              multiple
              @change="handleDirSelect"
            />
            <div class="dir-select-icon">
              <Icon name="folder-open" :size="32" />
            </div>
            <p class="dir-select-title">
              {{ selectedFiles.length > 0 ? `已选择 ${docFileCount} 个文档，${imageFileCount} 个图片` : '点击选择本地目录' }}
            </p>
            <p class="dir-select-hint">
              支持 Obsidian 仓库或任意 Markdown 目录，将自动解析目录层级与图片
            </p>
          </div>

          <!-- 目录结构预览 -->
          <div v-if="previewTree.length > 0" class="preview-card">
            <div class="preview-header">
              <h3 class="card-title">目录结构预览</h3>
              <div class="preview-stats">
                <span class="stat-badge doc">{{ docFileCount }} 文档</span>
                <span class="stat-badge img">{{ imageFileCount }} 图片</span>
                <span class="stat-badge dir">{{ dirCount }} 目录</span>
              </div>
            </div>
            <div class="preview-tree-container">
              <PreviewTreeNode
                v-for="node in previewTree"
                :key="node.name"
                :node="node"
                :level="0"
              />
            </div>
          </div>

          <div class="step-actions">
            <button class="btn-secondary" @click="goToStep(0)">
              <Icon name="chevron-left" :size="14" />
              <span>上一步</span>
            </button>
            <button
              class="btn-primary"
              :disabled="docFileCount === 0"
              @click="goToStep(2)"
            >
              <span>下一步：导入设置</span>
              <Icon name="chevron-right" :size="14" />
            </button>
          </div>
        </div>
      </template>

      <!-- ===== Step 2: 导入选项设置 ===== -->
      <template v-if="currentStep === 2">
        <div class="step-panel">
          <div class="form-card">
            <h3 class="card-title">导入选项</h3>
            <div class="options-list">
              <!-- 按目录创建子分类 -->
              <label class="option-item">
                <div class="option-info">
                  <p class="option-label">按目录层级创建子分类</p>
                  <p class="option-desc">将目录路径映射为知识库的分类树（最多 3 级，超出部分转为标签）</p>
                </div>
                <input type="checkbox" v-model="importOptions.createSubCategories" class="option-checkbox" />
              </label>

              <!-- 自动生成标签 -->
              <label class="option-item">
                <div class="option-info">
                  <p class="option-label">自动生成标签</p>
                  <p class="option-desc">从目录路径、文件名、正文关键词自动提取标签</p>
                </div>
                <input type="checkbox" v-model="importOptions.autoTags" class="option-checkbox" />
              </label>

              <!-- AI 智能打标 -->
              <label class="option-item">
                <div class="option-info">
                  <p class="option-label">AI 智能打标</p>
                  <p class="option-desc">调用大模型基于正文内容生成精准标签（较慢，消耗 token）</p>
                </div>
                <input type="checkbox" v-model="importOptions.aiTags" class="option-checkbox" />
              </label>

              <!-- 增量去重 -->
              <label class="option-item">
                <div class="option-info">
                  <p class="option-label">增量去重</p>
                  <p class="option-desc">按来源路径 + 内容哈希判断，跳过未变更文件</p>
                </div>
                <input type="checkbox" v-model="importOptions.incremental" class="option-checkbox" />
              </label>
            </div>
          </div>

          <!-- 导入摘要 -->
          <div class="summary-card">
            <h3 class="card-title">导入摘要</h3>
            <div class="summary-grid">
              <div class="summary-item">
                <span class="summary-label">目标知识库</span>
                <span class="summary-value">{{ selectedKbName }}</span>
              </div>
              <div class="summary-item">
                <span class="summary-label">文档数量</span>
                <span class="summary-value">{{ docFileCount }} 篇</span>
              </div>
              <div class="summary-item">
                <span class="summary-label">图片数量</span>
                <span class="summary-value">{{ imageFileCount }} 个</span>
              </div>
              <div class="summary-item">
                <span class="summary-label">创建子分类</span>
                <span class="summary-value">{{ importOptions.createSubCategories ? '是' : '否' }}</span>
              </div>
            </div>
          </div>

          <div class="step-actions">
            <button class="btn-secondary" @click="goToStep(1)">
              <Icon name="chevron-left" :size="14" />
              <span>上一步</span>
            </button>
            <button
              class="btn-primary"
              :disabled="importing"
              @click="startImport"
            >
              <Icon name="download" :size="14" />
              <span>{{ importing ? '导入中...' : '开始导入' }}</span>
            </button>
          </div>
        </div>
      </template>

      <!-- ===== Step 3: 导入进度 + 结果 ===== -->
      <template v-if="currentStep === 3">
        <div class="step-panel">
          <!-- 导入中 -->
          <div v-if="importing" class="progress-card">
            <div class="progress-spinner">
              <Icon name="loader" :size="32" class="spin-icon" />
            </div>
            <h3 class="progress-title">正在导入...</h3>
            <p class="progress-hint">正在处理 {{ docFileCount }} 个文档，请耐心等待</p>
            <div class="progress-bar-container">
              <div class="progress-bar-fill" style="width: 100%"></div>
            </div>
          </div>

          <!-- 导入结果 -->
          <div v-if="!importing && importResult" class="result-card">
            <div class="result-header">
              <div class="result-icon" :class="importResult.failedCount > 0 ? 'partial' : 'success'">
                <Icon :name="importResult.failedCount > 0 ? 'alert-circle' : 'check-circle'" :size="32" />
              </div>
              <h3 class="result-title">导入完成</h3>
            </div>

            <!-- 统计概览 -->
            <div class="result-stats">
              <div class="stat-item success">
                <span class="stat-num">{{ importResult.successCount }}</span>
                <span class="stat-text">成功</span>
              </div>
              <div class="stat-item skipped">
                <span class="stat-num">{{ importResult.skippedCount }}</span>
                <span class="stat-text">跳过</span>
              </div>
              <div class="stat-item failed">
                <span class="stat-num">{{ importResult.failedCount }}</span>
                <span class="stat-text">失败</span>
              </div>
              <div class="stat-item images">
                <span class="stat-num">{{ importResult.imageCount }}</span>
                <span class="stat-text">图片迁移</span>
              </div>
            </div>

            <!-- 新建分类 -->
            <div v-if="importResult.createdCategories.length > 0" class="created-categories">
              <h4 class="section-subtitle">新建分类</h4>
              <div class="category-tags">
                <span v-for="cat in importResult.createdCategories" :key="cat" class="category-tag">
                  <Icon name="folder" :size="12" />
                  {{ cat }}
                </span>
              </div>
            </div>

            <!-- 明细日志 Tab -->
            <div class="log-tabs">
              <button
                v-for="tab in logTabs"
                :key="tab.key"
                class="log-tab"
                :class="{ active: activeLogTab === tab.key }"
                @click="activeLogTab = tab.key"
              >
                {{ tab.label }}
                <span class="tab-count">{{ tab.count }}</span>
              </button>
            </div>

            <div class="log-list">
              <div
                v-for="(item, idx) in currentLogItems"
                :key="idx"
                class="log-item"
                :class="activeLogTab"
              >
                <Icon
                  :name="getLogItemIcon(activeLogTab)"
                  :size="14"
                  class="log-item-icon"
                />
                <div class="log-item-info">
                  <p class="log-item-path">{{ item.path }}</p>
                  <p class="log-item-msg">{{ item.message }}</p>
                </div>
                <span v-if="item.categoryName" class="log-item-cat">{{ item.categoryName }}</span>
              </div>
              <p v-if="currentLogItems.length === 0" class="log-empty">暂无记录</p>
            </div>

            <div class="step-actions">
              <button class="btn-secondary" @click="resetWizard">
                <Icon name="rotate-ccw" :size="14" />
                <span>继续导入</span>
              </button>
              <button class="btn-primary" @click="goToKb">
                <Icon name="folder" :size="14" />
                <span>查看知识库</span>
              </button>
            </div>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { categoriesApi } from '@/api/categories'
import { knowledgeImportApi } from '@/api/knowledgeImport'
import { notify, getApiError } from '@/utils/toast'
import type { CategoryVO, KnowledgeImportResultVO } from '@/api/types'
import PreviewTreeNode from '@/components/Knowledge/PreviewTreeNode.vue'

const router = useRouter()

// ===== 步骤控制 =====
const steps = ['选择知识库', '选择目录', '导入设置', '导入结果']
const currentStep = ref(0)

const goToStep = (step: number) => {
  currentStep.value = step
}

// ===== Step 0: 知识库选择 =====
const kbCategories = ref<CategoryVO[]>([])
const selectedKbId = ref<number | null>(null)

const selectedKbName = computed(() => {
  const kb = kbCategories.value.find(c => c.id === selectedKbId.value)
  return kb?.name || ''
})

const kbColors = ['#3B6FE0', '#10B981', '#F59E0B', '#8B5CF6', '#EC4899', '#06B6D4', '#84CC16']
const getKbColor = (id: number) => {
  const idx = (id - 1) % kbColors.length
  const color = kbColors[Math.max(0, idx)]
  return `${color}1A`
}

const getCategoryIcon = (icon?: string) => {
  if (icon && icon.trim()) return icon
  return 'book-open'
}

// ===== Step 1: 目录选择 =====
const dirInputRef = ref<HTMLInputElement | null>(null)
const selectedFiles = ref<File[]>([])

const triggerDirInput = () => {
  dirInputRef.value?.click()
}

const handleDirSelect = (e: Event) => {
  const input = e.target as HTMLInputElement
  if (!input.files || input.files.length === 0) return
  selectedFiles.value = Array.from(input.files)
}

const DOC_EXTS = ['md', 'markdown', 'txt']
const IMAGE_EXTS = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg', 'bmp', 'ico']

const docFileCount = computed(() =>
  selectedFiles.value.filter(f => DOC_EXTS.includes(getExt(f.name))).length
)
const imageFileCount = computed(() =>
  selectedFiles.value.filter(f => IMAGE_EXTS.includes(getExt(f.name))).length
)

const getExt = (name: string) => {
  const dot = name.lastIndexOf('.')
  return dot > 0 ? name.substring(dot + 1).toLowerCase() : ''
}

// 目录结构预览树
interface TreeNode {
  name: string
  path: string
  type: 'dir' | 'doc' | 'image' | 'other'
  children: TreeNode[]
}

const previewTree = computed<TreeNode[]>(() => {
  if (selectedFiles.value.length === 0) return []
  const root: TreeNode = { name: '', path: '', type: 'dir', children: [] }
  for (const file of selectedFiles.value) {
    // webkitRelativePath 包含选中的根目录名作为第一段
    const relPath = (file as File & { webkitRelativePath?: string }).webkitRelativePath || file.name
    const parts = relPath.split('/')
    let current = root
    for (let i = 0; i < parts.length; i++) {
      const part = parts[i]
      const isFile = i === parts.length - 1
      const currentPath = parts.slice(0, i + 1).join('/')
      if (isFile) {
        const ext = getExt(part)
        const fileType: TreeNode['type'] = DOC_EXTS.includes(ext) ? 'doc'
          : IMAGE_EXTS.includes(ext) ? 'image'
          : 'other'
        current.children.push({ name: part, path: currentPath, type: fileType, children: [] })
      } else {
        let child = current.children.find(c => c.name === part && c.type === 'dir')
        if (!child) {
          child = { name: part, path: currentPath, type: 'dir', children: [] }
          current.children.push(child)
        }
        current = child
      }
    }
  }
  // 排序：目录在前，文件在后
  const sortTree = (nodes: TreeNode[]): TreeNode[] => {
    nodes.sort((a, b) => {
      if (a.type === 'dir' && b.type !== 'dir') return -1
      if (a.type !== 'dir' && b.type === 'dir') return 1
      return a.name.localeCompare(b.name)
    })
    for (const n of nodes) {
      if (n.children.length > 0) sortTree(n.children)
    }
    return nodes
  }
  return sortTree(root.children)
})

const dirCount = computed(() => {
  let count = 0
  const countDirs = (nodes: TreeNode[]) => {
    for (const n of nodes) {
      if (n.type === 'dir') {
        count++
        countDirs(n.children)
      }
    }
  }
  countDirs(previewTree.value)
  return count
})

// ===== Step 2: 导入选项 =====
const importOptions = ref({
  createSubCategories: true,
  autoTags: true,
  aiTags: false,
  incremental: true,
})

// ===== Step 3: 导入执行 =====
const importing = ref(false)
const importResult = ref<KnowledgeImportResultVO | null>(null)
const activeLogTab = ref<'success' | 'skipped' | 'failed'>('success')

const logTabs = computed(() => [
  { key: 'success' as const, label: '成功', count: importResult.value?.successItems.length ?? 0 },
  { key: 'skipped' as const, label: '跳过', count: importResult.value?.skippedItems.length ?? 0 },
  { key: 'failed' as const, label: '失败', count: importResult.value?.failedItems.length ?? 0 },
])

const currentLogItems = computed(() => {
  if (!importResult.value) return []
  if (activeLogTab.value === 'success') return importResult.value.successItems
  if (activeLogTab.value === 'skipped') return importResult.value.skippedItems
  return importResult.value.failedItems
})

const getLogItemIcon = (tab: string) => {
  if (tab === 'success') return 'check-circle'
  if (tab === 'skipped') return 'skip-forward'
  return 'x-circle'
}

const startImport = async () => {
  if (!selectedKbId.value) {
    notify('请先选择目标知识库', 'error')
    return
  }
  if (docFileCount.value === 0) {
    notify('未找到可导入的文档文件', 'error')
    return
  }

  importing.value = true
  currentStep.value = 3

  try {
    const formData = new FormData()
    for (const file of selectedFiles.value) {
      const relPath = (file as File & { webkitRelativePath?: string }).webkitRelativePath || file.name
      // 使用相对路径作为文件名，后端据此还原目录结构
      formData.append('files', file, relPath)
    }
    formData.append('targetCategoryId', String(selectedKbId.value))
    formData.append('createSubCategories', String(importOptions.value.createSubCategories))
    formData.append('autoTags', String(importOptions.value.autoTags))
    formData.append('aiTags', String(importOptions.value.aiTags))
    formData.append('incremental', String(importOptions.value.incremental))

    const result = await knowledgeImportApi.importDirectory(formData)
    importResult.value = result
    activeLogTab.value = result.failedCount > 0 ? 'failed' : 'success'
    notify(
      `导入完成：成功 ${result.successCount} 篇，跳过 ${result.skippedCount} 篇，失败 ${result.failedCount} 篇`,
      result.failedCount > 0 ? 'warning' : 'success',
    )
  } catch (e: unknown) {
    notify(getApiError(e, '导入失败'), 'error')
    importResult.value = {
      targetCategoryId: selectedKbId.value,
      totalDocs: docFileCount.value,
      successCount: 0,
      skippedCount: 0,
      failedCount: docFileCount.value,
      imageCount: 0,
      createdCategories: [],
      successItems: [],
      skippedItems: [],
      failedItems: [{
        path: '',
        title: '',
        message: getApiError(e, '导入失败'),
      }],
    }
    activeLogTab.value = 'failed'
  } finally {
    importing.value = false
  }
}

const resetWizard = () => {
  currentStep.value = 0
  selectedFiles.value = []
  importResult.value = null
  importing.value = false
  if (dirInputRef.value) dirInputRef.value.value = ''
}

const goToKb = () => {
  router.push('/knowledge')
}

const goBack = () => {
  router.push('/knowledge')
}

// ===== 初始化 =====
const loadCategories = async () => {
  try {
    const tree = await categoriesApi.tree()
    // 只显示顶级分类（知识库）
    kbCategories.value = tree
  } catch (e: unknown) {
    notify(getApiError(e, '加载知识库列表失败'), 'error')
  }
}

onMounted(loadCategories)
</script>

<style scoped>
.knowledge-import-page {
  max-width: 900px;
  margin: 0 auto;
  padding: 24px 20px 60px;
}

/* ===== 顶部导航 ===== */
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

.header-left,
.header-right {
  flex: 1;
}

.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: transparent;
  border: none;
  color: var(--kb-muted-foreground);
  cursor: pointer;
  font-size: 13px;
  transition: color 0.15s;
}

.back-btn:hover {
  color: var(--kb-primary);
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--kb-foreground);
}

/* ===== 步骤指示器 ===== */
.step-indicator {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0;
  margin-bottom: 32px;
}

.step-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.step-circle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--kb-muted);
  color: var(--kb-muted-foreground);
  font-size: 13px;
  font-weight: 600;
  transition: all 0.2s;
  flex-shrink: 0;
}

.step-item.active .step-circle {
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
}

.step-item.done .step-circle {
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
}

.step-label {
  font-size: 13px;
  color: var(--kb-muted-foreground);
  white-space: nowrap;
}

.step-item.active .step-label {
  color: var(--kb-foreground);
  font-weight: 500;
}

.step-connector {
  width: 40px;
  height: 2px;
  background: var(--kb-border);
  margin: 0 8px;
}

/* ===== 步骤面板 ===== */
.step-panel {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* ===== 导入指南卡片 ===== */
.guide-card {
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: 12px;
  overflow: hidden;
}

.guide-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 20px;
  background: rgba(59, 111, 224, 0.06);
  border-bottom: 1px solid var(--kb-border);
}

.guide-icon {
  color: var(--kb-primary);
}

.guide-header h3 {
  font-size: 15px;
  font-weight: 600;
  color: var(--kb-foreground);
}

.guide-body {
  padding: 16px 20px;
}

.guide-section {
  margin-bottom: 16px;
}

.guide-section:last-child {
  margin-bottom: 0;
}

.guide-section h4 {
  font-size: 13px;
  font-weight: 600;
  color: var(--kb-foreground);
  margin-bottom: 8px;
}

.guide-section ul {
  list-style: none;
  padding: 0;
  margin: 0;
}

.guide-section li {
  font-size: 13px;
  color: var(--kb-muted-foreground);
  line-height: 1.8;
  padding-left: 16px;
  position: relative;
}

.guide-section li::before {
  content: '·';
  position: absolute;
  left: 4px;
  color: var(--kb-primary);
  font-weight: bold;
}

.guide-section code {
  background: var(--kb-muted);
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 12px;
  font-family: 'JetBrains Mono', monospace;
  color: var(--kb-primary);
}

/* ===== 表单卡片 ===== */
.form-card,
.summary-card,
.preview-card {
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: 12px;
  padding: 20px;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--kb-foreground);
  margin-bottom: 4px;
}

.card-desc {
  font-size: 13px;
  color: var(--kb-muted-foreground);
  margin-bottom: 16px;
}

/* ===== 知识库选择网格 ===== */
.kb-select-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
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
  transition: all 0.15s;
  position: relative;
}

.kb-select-item:hover {
  border-color: rgba(59, 111, 224, 0.4);
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
  font-size: 14px;
  font-weight: 500;
  color: var(--kb-foreground);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.kb-count {
  font-size: 12px;
  color: var(--kb-muted-foreground);
  margin-top: 2px;
}

.check-icon {
  color: var(--kb-primary);
  flex-shrink: 0;
}

.empty-hint {
  font-size: 14px;
  color: var(--kb-muted-foreground);
  text-align: center;
  padding: 24px;
}

.link-btn {
  color: var(--kb-primary);
  text-decoration: underline;
}

/* ===== 目录选择区 ===== */
.dir-select-zone {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  border: 2px dashed var(--kb-border);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.15s;
  background: var(--kb-card);
}

.dir-select-zone:hover {
  border-color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.02);
}

.hidden-file-input {
  display: none;
}

.dir-select-icon {
  color: var(--kb-primary);
  margin-bottom: 12px;
}

.dir-select-title {
  font-size: 15px;
  font-weight: 500;
  color: var(--kb-foreground);
  margin-bottom: 6px;
}

.dir-select-hint {
  font-size: 13px;
  color: var(--kb-muted-foreground);
}

/* ===== 预览卡片 ===== */
.preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.preview-stats {
  display: flex;
  gap: 8px;
}

.stat-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
}

.stat-badge.doc {
  background: rgba(59, 111, 224, 0.1);
  color: var(--kb-primary);
}

.stat-badge.img {
  background: rgba(16, 185, 129, 0.1);
  color: #10B981;
}

.stat-badge.dir {
  background: rgba(245, 158, 11, 0.1);
  color: #F59E0B;
}

.preview-tree-container {
  max-height: 400px;
  overflow-y: auto;
  font-size: 13px;
}

/* ===== 导入选项 ===== */
.options-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.option-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border: 1px solid var(--kb-border);
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.15s;
}

.option-item:hover {
  border-color: rgba(59, 111, 224, 0.3);
}

.option-info {
  flex: 1;
}

.option-label {
  font-size: 14px;
  font-weight: 500;
  color: var(--kb-foreground);
  margin-bottom: 2px;
}

.option-desc {
  font-size: 12px;
  color: var(--kb-muted-foreground);
}

.option-checkbox {
  width: 18px;
  height: 18px;
  accent-color: var(--kb-primary);
  cursor: pointer;
}

/* ===== 导入摘要 ===== */
.summary-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.summary-item {
  display: flex;
  justify-content: space-between;
  padding: 10px 14px;
  background: var(--kb-muted);
  border-radius: 8px;
}

.summary-label {
  font-size: 13px;
  color: var(--kb-muted-foreground);
}

.summary-value {
  font-size: 13px;
  font-weight: 500;
  color: var(--kb-foreground);
}

/* ===== 步骤操作按钮 ===== */
.step-actions {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-top: 8px;
}

.btn-primary,
.btn-secondary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
  border: none;
}

.btn-primary {
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
}

.btn-primary:hover:not(:disabled) {
  opacity: 0.9;
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-secondary {
  background: var(--kb-muted);
  color: var(--kb-foreground);
}

.btn-secondary:hover {
  background: var(--kb-border);
}

/* ===== 导入进度 ===== */
.progress-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48px 20px;
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: 12px;
}

.progress-spinner {
  color: var(--kb-primary);
  margin-bottom: 16px;
}

.spin-icon {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.progress-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--kb-foreground);
  margin-bottom: 6px;
}

.progress-hint {
  font-size: 13px;
  color: var(--kb-muted-foreground);
  margin-bottom: 20px;
}

.progress-bar-container {
  width: 100%;
  max-width: 300px;
  height: 6px;
  background: var(--kb-muted);
  border-radius: 999px;
  overflow: hidden;
}

.progress-bar-fill {
  height: 100%;
  background: var(--kb-primary);
  border-radius: 999px;
  animation: progress-pulse 1.5s ease-in-out infinite;
}

@keyframes progress-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

/* ===== 导入结果 ===== */
.result-card {
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: 12px;
  padding: 24px;
}

.result-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}

.result-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 50%;
}

.result-icon.success {
  background: rgba(16, 185, 129, 0.1);
  color: #10B981;
}

.result-icon.partial {
  background: rgba(245, 158, 11, 0.1);
  color: #F59E0B;
}

.result-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--kb-foreground);
}

.result-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 20px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 16px 8px;
  border-radius: 10px;
}

.stat-item.success {
  background: rgba(16, 185, 129, 0.08);
}

.stat-item.skipped {
  background: rgba(59, 111, 224, 0.08);
}

.stat-item.failed {
  background: rgba(239, 68, 68, 0.08);
}

.stat-item.images {
  background: rgba(245, 158, 11, 0.08);
}

.stat-num {
  font-size: 24px;
  font-weight: 700;
  color: var(--kb-foreground);
}

.stat-item.success .stat-num { color: #10B981; }
.stat-item.skipped .stat-num { color: var(--kb-primary); }
.stat-item.failed .stat-num { color: #EF4444; }
.stat-item.images .stat-num { color: #F59E0B; }

.stat-text {
  font-size: 12px;
  color: var(--kb-muted-foreground);
  margin-top: 4px;
}

/* ===== 新建分类 ===== */
.created-categories {
  margin-bottom: 20px;
}

.section-subtitle {
  font-size: 14px;
  font-weight: 600;
  color: var(--kb-foreground);
  margin-bottom: 8px;
}

.category-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.category-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  background: rgba(59, 111, 224, 0.08);
  color: var(--kb-primary);
  border-radius: 6px;
  font-size: 12px;
}

/* ===== 日志 Tab ===== */
.log-tabs {
  display: flex;
  gap: 4px;
  margin-bottom: 12px;
  border-bottom: 1px solid var(--kb-border);
}

.log-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  background: transparent;
  border: none;
  border-bottom: 2px solid transparent;
  color: var(--kb-muted-foreground);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
}

.log-tab:hover {
  color: var(--kb-foreground);
}

.log-tab.active {
  color: var(--kb-primary);
  border-bottom-color: var(--kb-primary);
  font-weight: 500;
}

.tab-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: var(--kb-muted);
  color: var(--kb-muted-foreground);
  font-size: 11px;
  font-weight: 600;
}

.log-tab.active .tab-count {
  background: rgba(59, 111, 224, 0.15);
  color: var(--kb-primary);
}

/* ===== 日志列表 ===== */
.log-list {
  max-height: 300px;
  overflow-y: auto;
}

.log-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 0;
  border-bottom: 1px solid var(--kb-border);
}

.log-item:last-child {
  border-bottom: none;
}

.log-item-icon {
  flex-shrink: 0;
  margin-top: 2px;
}

.log-item.success .log-item-icon {
  color: #10B981;
}

.log-item.skipped .log-item-icon {
  color: var(--kb-primary);
}

.log-item.failed .log-item-icon {
  color: #EF4444;
}

.log-item-info {
  flex: 1;
  min-width: 0;
}

.log-item-path {
  font-size: 13px;
  color: var(--kb-foreground);
  font-family: 'JetBrains Mono', monospace;
  word-break: break-all;
}

.log-item-msg {
  font-size: 12px;
  color: var(--kb-muted-foreground);
  margin-top: 2px;
}

.log-item-cat {
  flex-shrink: 0;
  padding: 2px 8px;
  background: var(--kb-muted);
  border-radius: 4px;
  font-size: 11px;
  color: var(--kb-muted-foreground);
  white-space: nowrap;
}

.log-empty {
  text-align: center;
  font-size: 13px;
  color: var(--kb-muted-foreground);
  padding: 20px;
}

/* ===== 响应式 ===== */
@media (max-width: 768px) {
  .knowledge-import-page {
    padding: 16px 12px 40px;
  }

  .step-label {
    display: none;
  }

  .step-connector {
    width: 24px;
  }

  .kb-select-grid {
    grid-template-columns: 1fr;
  }

  .summary-grid {
    grid-template-columns: 1fr;
  }

  .result-stats {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
