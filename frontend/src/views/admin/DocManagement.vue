<template>
  <!-- 管理后台-文档管理：标题+操作组、视图切换、知识库筛选、文件类型筛选、表格/网格（含阅读进度）、分页 -->
  <div class="doc-mgmt-wrap">
    <!-- 页面标题 + 操作按钮 -->
    <div class="page-head">
      <div class="title-group">
        <h1 class="kb-h1">文档管理</h1>
        <span class="kb-body-sm">共 {{ totalDocs }} 个</span>
      </div>
      <div class="head-actions">
        <button class="btn-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" @click="goToUpload">
          <Icon name="upload" :size="16" aria-hidden="true" />
          <span>上传</span>
        </button>
        <button class="btn-secondary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" @click="goToCreate">
          <Icon name="file-plus" :size="16" aria-hidden="true" />
          <span>新建</span>
        </button>
        <button class="btn-secondary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" @click="toggleBatch">
          <Icon name="check-square" :size="16" aria-hidden="true" />
          <span>{{ batchMode ? '退出批量' : '批量操作' }}</span>
        </button>
      </div>
    </div>

    <!-- 视图切换 -->
    <div class="view-switcher">
      <button
        class="view-btn hover:bg-[var(--kb-card)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
        :class="{ active: viewMode === 'list' }"
        @click="viewMode = 'list'"
      >
        <Icon name="list" :size="16" aria-hidden="true" />
        <span>列表视图</span>
      </button>
      <button
        class="view-btn hover:bg-[var(--kb-card)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
        :class="{ active: viewMode === 'grid' }"
        @click="viewMode = 'grid'"
      >
        <Icon name="layout-grid" :size="16" aria-hidden="true" />
        <span>网格视图</span>
      </button>
    </div>

    <!-- 批量操作工具栏 -->
    <div v-if="batchMode" class="batch-toolbar">
      <div class="batch-info">
        <Icon name="check-square" :size="16" />
        <span>已选择 <strong>{{ checkedIds.length }}</strong> 个文档</span>
        <button v-if="checkedIds.length > 0" class="link-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" @click="checkedIds = []">取消选择</button>
      </div>
      <div class="batch-actions">
        <button class="btn-danger focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" :disabled="!checkedIds.length" @click="batchDelete">
          <Icon name="trash-2" :size="14" aria-hidden="true" />
          <span>批量删除</span>
        </button>
        <button class="btn-secondary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" :disabled="!checkedIds.length" @click="openMoveModal">
          <Icon name="move" :size="14" aria-hidden="true" />
          <span>批量移动</span>
        </button>
      </div>
    </div>

    <!-- 筛选条 -->
    <div class="filter-bar">
      <!-- 知识库（分类）筛选 -->
      <div class="filter-category">
        <CategoryTreeSelect
          v-model="selectedCategoryId"
          :categories="categoryTreeOptions"
          placeholder="全部分类"
          empty-label="全部分类"
        />
      </div>
      <!-- 状态筛选（后端查询） -->
      <div class="filter-category">
        <select v-model="selectedStatus" class="category-select focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" @change="onFilterChange">
          <option :value="undefined">全部状态</option>
          <option :value="0">草稿</option>
          <option :value="1">已发布</option>
          <option :value="2">已禁用</option>
          <option :value="3">已废弃</option>
        </select>
      </div>
      <div class="filter-types">
        <button
          v-for="t in fileTypes"
          :key="t.value"
          class="filter-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
          :class="{ active: selectedType === t.value }"
          @click="selectedType = t.value"
        >
          {{ t.label }}
        </button>
      </div>
      <div class="filter-right">
        <div class="sort-group">
          <span class="sort-label">排序:</span>
          <select v-model="sortBy" class="sort-select focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors">
            <option value="modified">修改时间</option>
            <option value="name">名称</option>
            <option value="size">大小</option>
          </select>
        </div>
        <div class="search-box">
          <Icon name="search" :size="14" class="search-icon" />
          <input
            v-model="searchQuery"
            type="text"
            placeholder="搜索标题..."
            class="search-input focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
            @keyup.enter="onFilterChange"
          />
          <button class="search-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" @click="onFilterChange">搜索</button>
        </div>
      </div>
    </div>

    <!-- 文档表格（列表视图） -->
    <div v-if="viewMode === 'list'" class="table-card">
      <!-- 加载态 -->
      <div v-if="loading" class="table-loading">
        <div class="loading-spinner"></div>
        <span>加载中...</span>
      </div>
      <!-- 表头 -->
      <div class="table-row table-head">
        <label v-if="batchMode" class="checkbox-cell">
            <input
              type="checkbox"
              :checked="allChecked"
              @change="toggleAll"
              class="checkbox focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
            />
        </label>
        <div class="col-name">文件名</div>
        <div class="col-category">分类</div>
        <div class="col-size">大小</div>
        <div class="col-modified">修改时间</div>
        <div class="col-owner">所有者</div>
        <div class="col-status">状态</div>
        <div class="col-progress">阅读进度</div>
        <div class="col-actions">操作</div>
      </div>

      <!-- 表体 -->
      <div
        v-for="doc in pagedDocs"
        :key="doc.id"
        class="table-row table-body"
      >
        <label v-if="batchMode" class="checkbox-cell">
          <input
            type="checkbox"
            v-model="checkedIds"
            :value="doc.id"
            class="checkbox focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
          />
        </label>
        <div class="col-name">
          <div class="file-name-cell">
            <div class="file-icon" :style="{ background: doc.iconBg || '' }">
              <Icon :name="doc.icon || ''" :size="16" :style="{ color: doc.iconColor || '' }" />
            </div>
            <span class="file-name-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" :title="doc.title" role="button" tabindex="0" @click="goToDetail(doc)" @keydown.enter.prevent="($event.target as HTMLElement).click()">{{ doc.title }}</span>
          </div>
        </div>
        <div class="col-category">
          <span class="category-badge" :style="categoryStyle(doc.categoryColor)">{{ doc.category }}</span>
        </div>
        <div class="col-size">{{ doc.size }}</div>
        <div class="col-modified">{{ doc.modified }}</div>
        <div class="col-owner">{{ doc.owner }}</div>
        <div class="col-status">
          <span class="status-tag" :style="{ color: statusLabel(doc.raw.status).color, background: statusLabel(doc.raw.status).color + '15' }">
            {{ statusLabel(doc.raw.status).text }}
          </span>
        </div>
        <div class="col-progress">
          <div class="progress-bar">
            <div
              class="progress-fill"
              :style="{ width: doc.progress + '%', background: progressColor(doc.progress) }"
            ></div>
          </div>
          <span class="progress-text">{{ doc.progress }}%</span>
        </div>
        <div class="col-actions">
          <button class="icon-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" title="分享" @click="shareDoc(doc)">
              <Icon name="share-2" :size="14" aria-hidden="true" />
          </button>
          <button class="icon-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" title="下载" @click="downloadDoc(doc)">
              <Icon name="download" :size="14" aria-hidden="true" />
          </button>
          <button class="icon-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" title="更多" @click="openMenu(doc)">
              <Icon name="more-vertical" :size="14" aria-hidden="true" />
          </button>
        </div>
      </div>

      <!-- 空态 -->
      <p v-if="pagedDocs.length === 0" class="table-empty">暂无文档数据</p>
    </div>

    <!-- 网格视图 -->
    <div v-if="viewMode === 'grid'" class="doc-grid">
      <div
        v-for="doc in pagedDocs"
        :key="doc.id"
        class="doc-card"
        :class="{ 'card-checked': batchMode && checkedIds.includes(doc.id) }"
      >
        <div class="card-head">
          <label v-if="batchMode" class="card-checkbox">
            <input
              type="checkbox"
              v-model="checkedIds"
              :value="doc.id"
              class="checkbox focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
            />
          </label>
          <div class="card-icon" :style="{ background: doc.iconBg || '' }">
            <Icon :name="doc.icon || ''" :size="20" :style="{ color: doc.iconColor || '' }" />
          </div>
          <div class="card-actions">
            <button class="icon-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" title="编辑" @click="openEdit(doc)">
              <Icon name="edit" :size="14" aria-hidden="true" />
            </button>
            <button class="icon-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" title="删除" @click="removeDoc(doc)">
              <Icon name="trash-2" :size="14" aria-hidden="true" />
            </button>
          </div>
        </div>
        <div class="card-title focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" :title="doc.title" role="button" tabindex="0" @click="goToDetail(doc)" @keydown.enter.prevent="($event.target as HTMLElement).click()">{{ doc.title }}</div>
        <div class="card-meta">
          <span class="category-badge" :style="categoryStyle(doc.categoryColor)">{{ doc.category }}</span>
          <span class="card-date">{{ doc.modified }}</span>
        </div>
        <div class="card-footer">
          <span class="card-stat" title="浏览量">
            <Icon name="eye" :size="12" />
            {{ doc.raw.viewCount ?? 0 }}
          </span>
          <span class="card-stat" title="阅读量">
            <Icon name="book-open" :size="12" />
            {{ doc.raw.readCount ?? 0 }}
          </span>
        </div>
      </div>
      <p v-if="pagedDocs.length === 0" class="table-empty">暂无文档数据</p>
    </div>

    <!-- 分页 -->
    <div class="pagination">
      <span class="page-info">显示 {{ rangeStart }}-{{ rangeEnd }} / 共 {{ totalDocs }} 个</span>
      <div class="page-buttons">
        <button
          class="page-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
          :disabled="currentPage === 1"
          @click="currentPage = Math.max(1, currentPage - 1)"
        >
          <Icon name="chevron-left" :size="16" />
        </button>
        <button
          v-for="page in visiblePages"
          :key="page"
          class="page-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
          :class="{
            active: page === currentPage,
            ellipsis: page === -1,
          }"
          :disabled="page === -1"
          @click="page !== -1 && (currentPage = page)"
        >
          {{ page === -1 ? '...' : page }}
        </button>
        <button
          class="page-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
          :disabled="currentPage === totalPages"
          @click="currentPage = Math.min(totalPages, currentPage + 1)"
        >
          <Icon name="chevron-right" :size="16" />
        </button>
      </div>
      <div class="page-size">
        <span class="page-size-label">每页:</span>
        <select v-model.number="pageSize" class="page-size-select focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors">
          <option :value="10">10</option>
          <option :value="20">20</option>
          <option :value="50">50</option>
        </select>
      </div>
    </div>

    <!-- 更多操作下拉菜单 -->
    <div
      v-if="menuDoc"
      class="menu-mask focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
      role="button"
      tabindex="0"
      @click="menuDoc = null"
      @keydown.enter.prevent.self="($event.target as HTMLElement).click()"
    >
      <div class="menu-popover" @click.stop>
        <button class="menu-item focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" @click="openEdit(menuDoc); menuDoc = null">
          <Icon name="edit" :size="14" aria-hidden="true" />
          <span>修订</span>
        </button>
        <button v-if="menuDoc.raw.status !== 1" class="menu-item focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" @click="publishDoc(menuDoc); menuDoc = null">
          <Icon name="check-circle" :size="14" aria-hidden="true" />
          <span>发布</span>
        </button>
        <button v-if="menuDoc.raw.status !== 0" class="menu-item focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" @click="draftDoc(menuDoc); menuDoc = null">
          <Icon name="edit-2" :size="14" aria-hidden="true" />
          <span>转为草稿</span>
        </button>
        <button v-if="menuDoc.raw.status !== 3" class="menu-item focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" @click="deprecateDoc(menuDoc); menuDoc = null">
          <Icon name="archive" :size="14" aria-hidden="true" />
          <span>废弃</span>
        </button>
        <button class="menu-item danger focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" @click="removeDoc(menuDoc); menuDoc = null">
          <Icon name="trash-2" :size="14" aria-hidden="true" />
          <span>删除</span>
        </button>
      </div>
    </div>

    <!-- 移动文档到知识库 弹窗 -->
    <div v-if="showMoveModal" class="modal-overlay focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" role="button" tabindex="0" @click="showMoveModal = false" @keydown.enter.prevent.self="($event.target as HTMLElement).click()">
      <div class="modal-content" @click.stop>
        <div class="modal-header">
          <h3>移动文档到知识库</h3>
          <button class="modal-close focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" @click="showMoveModal = false">
            <Icon name="x" :size="18" />
          </button>
        </div>
        <div class="modal-body">
          <p class="modal-tip">
            <Icon name="info" :size="14" />
            <span>将选中的 <strong>{{ checkedIds.length }}</strong> 个文档移动到以下知识库：</span>
          </p>
          <div class="kb-list">
            <label
              v-for="cat in categoryOptions"
              :key="cat.id"
              class="kb-item"
              :class="{ active: targetCategoryId === cat.id }"
            >
              <input
                type="radio"
                v-model="targetCategoryId"
                :value="cat.id"
                class="radio-input focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
              />
              <Icon name="folder" :size="18" />
              <div class="kb-info">
                <div class="kb-name">{{ cat.name }}</div>
                <div class="kb-desc">{{ cat.description || '暂无描述' }}</div>
              </div>
            </label>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn-secondary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" @click="showMoveModal = false">取消</button>
          <button class="btn-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" :disabled="!targetCategoryId || moving" @click="batchMove">
            <Icon name="check" :size="14" aria-hidden="true" />
            <span>{{ moving ? '移动中...' : '确认移动' }}</span>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// 管理后台-文档管理：文档列表/网格展示、知识库筛选、类型筛选、排序、分页、批量选择、编辑/删除/下载。
import { confirmDialog, getApiError, notify } from '@/utils/toast'
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import CategoryTreeSelect from '@/components/ui/CategoryTreeSelect.vue'
import { adminApi, docsApi } from '@/api'
import type { CategoryVO, DocVO } from '@/api/types'
import { getIconByKey, parseIconValue, resolveIconForRender } from '@/utils/presetIcons'

const router = useRouter()
const route = useRoute()

const goToCreate = () => router.push('/docs/new')
const goToUpload = () => router.push('/upload')

/** 跳转文档编辑页（B端二级页面） */
const goToDetail = (doc: DocRow) => {
  router.push('/admin/docs/' + doc.id + '/edit')
}

/** 文档行数据结构 */
interface DocRow {
  id: number
  title: string
  category: string
  categoryColor: string
  size: string
  modified: string
  owner: string
  progress: number
  icon?: string
  iconBg: string
  iconColor: string
  raw: DocVO
}

/** 文件类型筛选 */
const fileTypes = [
  { label: '全部', value: 'all' },
  { label: 'PDF', value: 'pdf' },
  { label: 'Markdown', value: 'md' },
  { label: 'Word', value: 'word' },
  { label: '笔记', value: 'note' },
]

const viewMode = ref<'list' | 'grid'>('list')
const selectedType = ref('all')
const selectedCategoryId = ref<number | undefined>(undefined)
const selectedStatus = ref<number | undefined>(undefined)
const sortBy = ref('modified')
const searchQuery = ref('')
const batchMode = ref(false)
const checkedIds = ref<number[]>([])
const showMoveModal = ref(false)
const targetCategoryId = ref<number | undefined>(undefined)
const moving = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)

/** 搜索输入的防抖值（实际发给后端的 keyword） */
const searchKeyword = ref('')

const categoryOptions = ref<CategoryVO[]>([])
const categoryTreeOptions = ref<CategoryVO[]>([])
const allDocs = ref<DocRow[]>([])
const loading = ref(false)

/** 图标选项与颜色映射 */
const iconOptions = ['file-text', 'file-code', 'book-open', 'pencil', 'code', 'brain', 'layout', 'database', 'server', 'lightbulb']

const iconColors: Record<string, string> = {
  'file-text': '#3B6FE0',
  'file-code': '#10B981',
  'book-open': '#8B5CF6',
  'pencil': '#F59E0B',
  'code': '#3B6FE0',
  'brain': '#8B5CF6',
  'layout': '#EC4899',
  'database': '#F59E0B',
  'server': '#10B981',
  'lightbulb': '#F59E0B',
}

/** 更多操作菜单 */
const menuDoc = ref<DocRow | null>(null)

/** 颜色池：用于分类徽标 */
const categoryColors = ['#F59E0B', '#3B6FE0', '#10B981', '#EF4444', '#6B7280']

/** 根据文档标题推断文件类型图标与颜色；若设置过自定义图标则优先使用 */
const inferIcon = (title: string, customIcon?: string): { icon: string; bg: string; color: string } => {
  // 1. 优先匹配预置图标（存储格式 `iconKey` 或 `iconKey|colorHex`）
  if (customIcon) {
    const { name, color } = resolveIconForRender(customIcon)
    if (name && getIconByKey(parseIconValue(customIcon).key)) {
      const finalColor = color || '#6B7280'
      return {
        icon: name,
        bg: hexToRgba(finalColor, 0.1),
        color: finalColor,
      }
    }
    // 2. 旧版系统图标名
    if (iconOptions.includes(customIcon)) {
      const c = iconColors[customIcon] || '#6B7280'
      return { icon: customIcon, bg: hexToRgba(c, 0.1), color: c }
    }
  }
  // 3. 根据标题推断
  const lower = title.toLowerCase()
  if (lower.endsWith('.pdf')) {
    return { icon: 'file-text', bg: 'rgba(239,68,68,0.1)', color: '#EF4444' }
  }
  if (lower.endsWith('.md')) {
    return { icon: 'file-code', bg: 'rgba(59,111,224,0.08)', color: '#3B6FE0' }
  }
  if (lower.endsWith('.docx') || lower.endsWith('.doc')) {
    return { icon: 'file-text', bg: 'rgba(59,111,224,0.08)', color: '#3B6FE0' }
  }
  // 笔记类
  return { icon: 'pencil', bg: 'var(--kb-muted)', color: 'var(--kb-muted-foreground)' }
}

/** 根据字数计算文件大小显示（1 字符 ≈ 2 字节） */
const formatSize = (wordCount?: number): string => {
  if (!wordCount || wordCount <= 0) return '—'
  const bytes = wordCount * 2
  if (bytes < 1024) return bytes + ' B'
  const kb = bytes / 1024
  if (kb < 1024) return kb.toFixed(1) + ' KB'
  return (kb / 1024).toFixed(1) + ' MB'
}

/** 根据文件类型筛选（前端过滤，基于标题后缀） */
const filteredByType = computed(() => {
  if (selectedType.value === 'all') return allDocs.value
  return allDocs.value.filter((d) => {
    const t = d.title.toLowerCase()
    if (selectedType.value === 'pdf') return t.endsWith('.pdf')
    if (selectedType.value === 'md') return t.endsWith('.md')
    if (selectedType.value === 'word') return t.endsWith('.docx') || t.endsWith('.doc')
    if (selectedType.value === 'note') return !t.endsWith('.pdf') && !t.endsWith('.md') && !t.endsWith('.docx') && !t.endsWith('.doc')
    return true
  })
})

/** 排序后的列表（categoryId/keyword/status 已在后端查询，前端仅做文件类型过滤和排序） */
const filteredDocs = computed(() => {
  let result = [...filteredByType.value]
  // 排序（后端不支持排序参数，前端处理）
  if (sortBy.value === 'name') {
    result.sort((a, b) => a.title.localeCompare(b.title))
  } else if (sortBy.value === 'size') {
    result.sort((a, b) => (b.raw.wordCount ?? 0) - (a.raw.wordCount ?? 0))
  } else {
    // 修改时间倒序（后端已按 createTime DESC 排序，这里保持）
    result.sort((a, b) => b.modified.localeCompare(a.modified))
  }
  return result
})

const totalDocs = computed(() => filteredDocs.value.length)
const totalPages = computed(() => Math.max(1, Math.ceil(totalDocs.value / pageSize.value)))

const pagedDocs = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredDocs.value.slice(start, start + pageSize.value)
})

const rangeStart = computed(() =>
  totalDocs.value === 0 ? 0 : (currentPage.value - 1) * pageSize.value + 1,
)
const rangeEnd = computed(() => Math.min(currentPage.value * pageSize.value, totalDocs.value))

/** 分页按钮（含省略号 -1） */
const visiblePages = computed(() => {
  const total = totalPages.value
  const current = currentPage.value
  const pages: number[] = []
  if (total <= 7) {
    for (let i = 1; i <= total; i++) pages.push(i)
    return pages
  }
  // 显示首尾 + 当前页附近
  pages.push(1)
  if (current > 4) pages.push(-1)
  const start = Math.max(2, current - 1)
  const end = Math.min(total - 1, current + 1)
  for (let i = start; i <= end; i++) pages.push(i)
  if (current < total - 3) pages.push(-1)
  pages.push(total)
  return pages
})

/** 全选状态 */
const allChecked = computed(
  () => pagedDocs.value.length > 0 && pagedDocs.value.every((d) => checkedIds.value.includes(d.id)),
)

const toggleAll = () => {
  if (allChecked.value) {
    checkedIds.value = checkedIds.value.filter(
      (id) => !pagedDocs.value.some((d) => d.id === id),
    )
  } else {
    pagedDocs.value.forEach((d) => {
      if (!checkedIds.value.includes(d.id)) checkedIds.value.push(d.id)
    })
  }
}

const toggleBatch = () => {
  batchMode.value = !batchMode.value
  if (!batchMode.value) checkedIds.value = []
}

/** 批量删除文档 */
const batchDelete = async () => {
  if (!checkedIds.value.length) {
    notify('请选择要删除的文档', 'warning')
    return
  }
  if (!(await confirmDialog(`确定删除选中的 ${checkedIds.value.length} 个文档吗？此操作不可恢复。`))) return
  try {
    await adminApi.batchDeleteDocs(checkedIds.value)
    notify(`已删除 ${checkedIds.value.length} 个文档`, 'success')
    checkedIds.value = []
    batchMode.value = false
    await loadDocs()
  } catch (e: unknown) {
    notify('批量删除失败：' + getApiError(e), 'error')
  }
}

/** 打开批量移动弹窗 */
const openMoveModal = () => {
  if (!checkedIds.value.length) {
    notify('请选择要移动的文档', 'warning')
    return
  }
  // 默认不选，让用户主动选择目标
  targetCategoryId.value = undefined
  showMoveModal.value = true
}

/** 批量移动文档到目标知识库 */
const batchMove = async () => {
  if (!targetCategoryId.value) {
    notify('请选择目标知识库', 'warning')
    return
  }
  if (!checkedIds.value.length) {
    notify('请选择要移动的文档', 'warning')
    return
  }
  moving.value = true
  try {
    await adminApi.batchMoveDocs({
      docIds: checkedIds.value,
      categoryId: targetCategoryId.value,
    })
    notify(`已移动 ${checkedIds.value.length} 个文档`, 'success')
    showMoveModal.value = false
    checkedIds.value = []
    batchMode.value = false
    await loadDocs()
  } catch (e: unknown) {
    notify('批量移动失败：' + getApiError(e), 'error')
  } finally {
    moving.value = false
  }
}

/** 分类徽标样式 */
const categoryStyle = (color: string) => ({
  backgroundColor: hexToRgba(color, 0.08),
  color: color,
})

/** 进度条颜色：根据进度变化 */
const progressColor = (p: number): string => {
  if (p >= 80) return 'var(--kb-state-success)'
  if (p >= 40) return 'var(--kb-primary)'
  if (p >= 20) return 'var(--kb-state-warning)'
  return 'var(--kb-state-error)'
}

const hexToRgba = (hex: string, alpha: number): string => {
  const h = hex.replace('#', '')
  const r = parseInt(h.substring(0, 2), 16)
  const g = parseInt(h.substring(2, 4), 16)
  const b = parseInt(h.substring(4, 6), 16)
  return `rgba(${r}, ${g}, ${b}, ${alpha})`
}

const formatDate = (v?: string): string => {
  if (!v) return '—'
  return v.includes('T') ? v.slice(0, 10) : v.slice(0, 10)
}

/** 打开更多操作菜单 */
const openMenu = (doc: DocRow) => {
  menuDoc.value = doc
}

/** 跳转到文档编辑页面 */
const openEdit = (doc: DocRow) => {
  router.push(`/admin/docs/${doc.id}/edit`)
}

/** 文档状态操作：发布/废弃/修订 */
const publishDoc = async (doc: DocRow) => {
  try {
    await adminApi.publishDoc(doc.id)
    notify('文档已发布', 'success')
    await loadDocs()
  } catch (e: unknown) {
    notify('发布失败：' + getApiError(e), 'error')
  }
}

const deprecateDoc = async (doc: DocRow) => {
  if (!(await confirmDialog(`确定废弃文档《${doc.title}》吗？`))) return
  try {
    await adminApi.deprecateDoc(doc.id)
    notify('文档已废弃', 'success')
    await loadDocs()
  } catch (e: unknown) {
    notify('操作失败：' + getApiError(e), 'error')
  }
}

const draftDoc = async (doc: DocRow) => {
  try {
    await adminApi.draftDoc(doc.id)
    notify('文档已转为草稿', 'success')
    await loadDocs()
  } catch (e: unknown) {
    notify('操作失败：' + getApiError(e), 'error')
  }
}

/** 获取文档状态标签 */
const statusLabel = (status?: number): { text: string; color: string } => {
  switch (status) {
    case 1: return { text: '已发布', color: '#10B981' }
    case 2: return { text: '已禁用', color: '#EF4444' }
    case 3: return { text: '已废弃', color: '#6B7280' }
    default: return { text: '草稿', color: '#F59E0B' }
  }
}

const removeDoc = async (doc: DocRow) => {
  if (!(await confirmDialog(`确定删除文档《${doc.title}》吗？此操作不可恢复。`))) return
  try {
    await adminApi.removeDoc(doc.id)
    notify('删除成功', 'success')
    if (pagedDocs.value.length === 1 && currentPage.value > 1) {
      currentPage.value -= 1
    }
    await loadDocs()
  } catch (e: unknown) {
    notify('删除失败：' + getApiError(e), 'error')
  }
}

const shareDoc = (_doc: DocRow) => {
  notify('分享链接已复制（演示）', 'success')
}

/** 下载文档：获取文档详情后导出为 Markdown 文件 */
const downloadDoc = async (doc: DocRow) => {
  try {
    const detail = await docsApi.detail(doc.id)
    const content = detail.content ?? ''
    const title = doc.title || `document-${doc.id}`
    const filename = title.endsWith('.md') ? title : title + '.md'
    const blob = new Blob([`# ${title}\n\n${content}`], { type: 'text/markdown;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    notify(`已下载：${filename}`, 'success')
  } catch (e: unknown) {
    notify('下载失败：' + getApiError(e), 'error')
  }
}

/** 加载文档列表（后端按 categoryId/keyword/status 筛选） */
const loadDocs = async () => {
  loading.value = true
  try {
    const params: Record<string, unknown> = { pageSize: 200 }
    if (selectedCategoryId.value !== undefined) {
      params.categoryId = selectedCategoryId.value
    }
    if (searchKeyword.value.trim()) {
      params.keyword = searchKeyword.value.trim()
    }
    if (selectedStatus.value !== undefined) {
      params.status = selectedStatus.value
    }

    const [docPage, cats, catTree] = await Promise.all([
      adminApi.docs(params),
      adminApi.categories(),
      adminApi.categoryTree(),
    ])
    categoryOptions.value = cats
    categoryTreeOptions.value = catTree
    const catMap = new Map<number, { name: string; color: string }>()
    cats.forEach((c, idx) => {
      catMap.set(c.id, {
        name: c.name,
        color: categoryColors[idx % categoryColors.length],
      })
    })

    const records = (docPage.records ?? []) as DocVO[]
    allDocs.value = records.map((d) => {
      const cat = catMap.get(d.categoryId ?? -1) ?? { name: '未分类', color: '#6B7280' }
      const iconInfo = inferIcon(d.title ?? '', d.icon)
      return {
        id: d.id,
        title: d.title ?? '',
        category: cat.name,
        categoryColor: cat.color,
        size: formatSize(d.wordCount),
        modified: formatDate(d.createTime),
        owner: (d as DocVO & { author?: string }).author || '—',
        progress: 0,
        icon: iconInfo.icon,
        iconBg: iconInfo.bg ?? '',
        iconColor: iconInfo.color ?? '',
        raw: d,
      }
    })
  } catch (e: unknown) {
    notify('加载文档失败：' + getApiError(e), 'error')
  } finally {
    loading.value = false
  }
}

/** 筛选条件变化时触发后端查询（分类/状态/搜索） */
const onFilterChange = () => {
  searchKeyword.value = searchQuery.value
  currentPage.value = 1
  loadDocs()
}

/** 监听路由 query 变化（从知识库管理跳转过来时 categoryId 会变） */
watch(
  () => route.query.categoryId,
  (val) => {
    const num = val !== undefined && val !== null && val !== '' ? Number(val) : NaN
    if (!Number.isNaN(num)) {
      if (selectedCategoryId.value !== num) {
        selectedCategoryId.value = num
        currentPage.value = 1
        loadDocs()
      }
    } else {
      // 没有 categoryId 参数时，清除筛选
      if (selectedCategoryId.value !== undefined) {
        selectedCategoryId.value = undefined
        currentPage.value = 1
        loadDocs()
      }
    }
  },
)

/** 监听分类筛选变化（CategoryTreeSelect 触发） */
watch(selectedCategoryId, () => {
  currentPage.value = 1
  loadDocs()
})

onMounted(() => {
  // 从路由 query 读取知识库筛选
  const catIdFromRoute = route.query.categoryId
  if (catIdFromRoute !== undefined && catIdFromRoute !== null && catIdFromRoute !== '') {
    const num = Number(catIdFromRoute)
    if (!Number.isNaN(num)) {
      selectedCategoryId.value = num
    }
  }
  loadDocs()
})
</script>

<style scoped>
/* 页面容器 */
.doc-mgmt-wrap {
  padding: 24px 28px 40px;
  position: relative;
  animation: fadeIn 0.4s ease-out;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 页面标题 */
.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
  flex-wrap: wrap;
  gap: 12px;
}

.title-group {
  display: flex;
  align-items: center;
  gap: 12px;
}

.kb-h1 {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.3;
  letter-spacing: -0.02em;
  color: var(--kb-foreground);
  margin: 0;
}

.kb-h3 {
  font-size: 18px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--kb-foreground);
  margin: 0;
}

.kb-body-sm {
  font-size: 12px;
  font-weight: 400;
  line-height: 1.5;
  color: var(--kb-muted-foreground);
}

.head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

/* 视图切换 */
.view-switcher {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px;
  background: var(--kb-muted);
  border-radius: 8px;
  margin-bottom: 16px;
}

.view-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 12px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  background: transparent;
  color: var(--kb-muted-foreground);
  border: none;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.view-btn.active {
  background: var(--kb-card);
  color: var(--kb-foreground);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
}

/* 筛选条 */
.filter-bar {
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: 10px;
  padding: 16px;
  margin-bottom: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

/* 知识库筛选下拉 */
.filter-category {
  display: flex;
  align-items: center;
}

.category-select {
  height: 32px;
  padding: 0 28px 0 10px;
  border-radius: 6px;
  font-size: 13px;
  background: var(--kb-card);
  color: var(--kb-foreground);
  border: 1px solid var(--kb-border);
  outline: none;
  cursor: pointer;
  transition: border-color 0.15s;
  min-width: 140px;
}

.category-select:focus {
  border-color: var(--kb-ring);
}

.filter-types {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.filter-btn {
  height: 32px;
  padding: 0 12px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  background: var(--kb-card);
  color: var(--kb-foreground);
  border: 1px solid var(--kb-border);
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.filter-btn.active {
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  border-color: var(--kb-primary);
}

.filter-right {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-left: auto;
  flex-wrap: wrap;
}

.sort-group {
  display: flex;
  align-items: center;
  gap: 6px;
}

.sort-label {
  font-size: 13px;
  color: var(--kb-muted-foreground);
}

.sort-select {
  height: 32px;
  padding: 0 10px;
  border-radius: 6px;
  font-size: 13px;
  background: var(--kb-card);
  color: var(--kb-foreground);
  border: 1px solid var(--kb-border);
  outline: none;
  cursor: pointer;
}

.search-box {
  position: relative;
  display: flex;
  align-items: center;
}

.search-icon {
  position: absolute;
  left: 10px;
  color: var(--kb-muted-foreground);
  pointer-events: none;
}

.search-input {
  height: 32px;
  width: 160px;
  padding: 0 12px 0 32px;
  border-radius: 6px;
  font-size: 13px;
  background: var(--kb-background);
  color: var(--kb-foreground);
  border: 1px solid var(--kb-border);
  outline: none;
  transition: border-color 0.15s;
}

.search-input:focus {
  border-color: var(--kb-ring);
}

.search-btn {
  height: 32px;
  padding: 0 12px;
  margin-left: 6px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  border: none;
  cursor: pointer;
  transition: opacity 0.15s;
}
.search-btn:hover {
  opacity: 0.9;
}

/* 表格加载态 */
.table-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 40px 0;
  color: var(--kb-muted-foreground);
  font-size: 13px;
}
.loading-spinner {
  width: 28px;
  height: 28px;
  border: 3px solid var(--kb-muted);
  border-top-color: var(--kb-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}

/* 表格卡片 */
.table-card {
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: 10px;
  overflow: hidden;
}

/* 表格行 */
.table-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--kb-border);
}

.table-row:last-child {
  border-bottom: none;
}

.table-head {
  background: var(--kb-background);
  padding: 10px 16px;
  font-size: 12px;
  font-weight: 600;
  color: var(--kb-muted-foreground);
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.table-body:hover {
  background: var(--kb-background);
}

/* 复选框 */
.checkbox-cell {
  width: 32px;
  flex-shrink: 0;
  display: flex;
  justify-content: center;
}

.checkbox {
  width: 16px;
  height: 16px;
  accent-color: var(--kb-primary);
  cursor: pointer;
}

/* 表格列宽 */
.col-name {
  flex: 1;
  min-width: 0;
}

.col-category {
  width: 96px;
  flex-shrink: 0;
}

.col-size {
  width: 64px;
  flex-shrink: 0;
  text-align: right;
  font-size: 13px;
  color: var(--kb-muted-foreground);
}

.col-modified {
  width: 112px;
  flex-shrink: 0;
  font-size: 13px;
  color: var(--kb-muted-foreground);
}

.col-owner {
  width: 80px;
  flex-shrink: 0;
  font-size: 13px;
  color: var(--kb-muted-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.col-status {
  width: 72px;
  flex-shrink: 0;
}

.status-tag {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}

.col-progress {
  width: 96px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.col-actions {
  width: 96px;
  flex-shrink: 0;
  display: flex;
  justify-content: flex-end;
  gap: 4px;
}

/* 文件名单元格 */
.file-name-cell {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.file-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.file-name-text {
  font-size: 13px;
  font-weight: 500;
  color: var(--kb-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
  transition: color 0.15s;
}

.file-name-text:hover {
  color: var(--kb-primary);
}

/* 分类徽标 */
.category-badge {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}

/* 进度条 */
.progress-bar {
  flex: 1;
  height: 6px;
  border-radius: 3px;
  background: var(--kb-muted);
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 0.3s ease-out;
}

.progress-text {
  font-size: 12px;
  color: var(--kb-muted-foreground);
  flex-shrink: 0;
}

/* 操作按钮 */
.icon-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  border: none;
  background: transparent;
  color: var(--kb-muted-foreground);
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.icon-btn:hover {
  background: var(--kb-muted);
  color: var(--kb-primary);
}

/* 表格空态 */
.table-empty {
  text-align: center;
  color: var(--kb-muted-foreground);
  padding: 48px 0;
  margin: 0;
}

/* ===== 网格视图 ===== */
.doc-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 16px;
}

.doc-card {
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: 10px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  transition: border-color 0.15s, box-shadow 0.15s, transform 0.15s;
}

.doc-card:hover {
  border-color: var(--kb-ring);
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.08);
  transform: translateY(-2px);
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.card-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.card-actions {
  display: flex;
  gap: 2px;
  opacity: 0;
  transition: opacity 0.15s;
}

.doc-card:hover .card-actions {
  opacity: 1;
}

.card-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--kb-foreground);
  line-height: 1.4;
  cursor: pointer;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 39px;
  transition: color 0.15s;
}

.card-title:hover {
  color: var(--kb-primary);
}

.card-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
}

.card-date {
  font-size: 12px;
  color: var(--kb-muted-foreground);
}

.card-footer {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-top: 8px;
  border-top: 1px solid var(--kb-border);
}

.card-stat {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--kb-muted-foreground);
}

/* 分页 */
.pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
  flex-wrap: wrap;
  gap: 12px;
}

.page-info {
  font-size: 13px;
  color: var(--kb-muted-foreground);
}

.page-buttons {
  display: flex;
  align-items: center;
  gap: 6px;
}

.page-btn {
  min-width: 32px;
  height: 32px;
  padding: 0 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  background: var(--kb-card);
  color: var(--kb-foreground);
  border: 1px solid var(--kb-border);
  cursor: pointer;
  transition: background 0.15s;
}

.page-btn:hover:not(:disabled):not(.ellipsis) {
  background: var(--kb-muted);
}

.page-btn.active {
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  border-color: var(--kb-primary);
}

.page-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.page-btn.ellipsis {
  border: none;
  background: transparent;
  cursor: default;
}

.page-size {
  display: flex;
  align-items: center;
  gap: 6px;
}

.page-size-label {
  font-size: 13px;
  color: var(--kb-muted-foreground);
}

.page-size-select {
  height: 32px;
  padding: 0 10px;
  border-radius: 6px;
  font-size: 13px;
  background: var(--kb-card);
  color: var(--kb-foreground);
  border: 1px solid var(--kb-border);
  outline: none;
  cursor: pointer;
}

/* 更多操作菜单 */
.menu-mask {
  position: fixed;
  inset: 0;
  z-index: 40;
}

.menu-popover {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  padding: 4px;
  min-width: 120px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 6px;
  font-size: 13px;
  color: var(--kb-foreground);
  background: transparent;
  border: none;
  cursor: pointer;
  transition: background 0.15s;
  text-align: left;
}

.menu-item:hover {
  background: var(--kb-muted);
}

.menu-item.danger {
  color: var(--kb-state-error);
}

.menu-item.danger:hover {
  background: rgba(239, 68, 68, 0.08);
}

/* 弹窗 */
.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 50;
  padding: 16px;
}

.modal-card {
  background: var(--kb-card);
  border-radius: 12px;
  width: 100%;
  max-width: 720px;
  max-height: 90vh;
  overflow-y: auto;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.15);
  animation: modalIn 0.2s ease-out;
}

@keyframes modalIn {
  from {
    opacity: 0;
    transform: scale(0.95) translateY(8px);
  }
  to {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
}

.modal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--kb-border);
}

.modal-body {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.modal-foot {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 16px 20px;
  border-top: 1px solid var(--kb-border);
}

/* 表单 */
.form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex: 1;
}

.form-row {
  display: flex;
  gap: 12px;
}

.form-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--kb-foreground);
}

.form-input {
  height: 36px;
  padding: 0 12px;
  border-radius: 6px;
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-foreground);
  font-size: 13px;
  outline: none;
  transition: border-color 0.15s, box-shadow 0.15s;
  width: 100%;
}

.form-input:focus {
  border-color: var(--kb-ring);
  box-shadow: 0 0 0 3px rgba(59, 111, 224, 0.1);
}

/* 图标选择器 */
.icon-picker {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.icon-option {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}

.icon-option:hover {
  border-color: var(--kb-muted-foreground);
}

.icon-option.active {
  border-color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.08);
}

/* 内容文本域 */
.form-textarea {
  width: 100%;
  padding: 12px;
  border-radius: 6px;
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-foreground);
  font-size: 13px;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  line-height: 1.6;
  outline: none;
  resize: vertical;
  transition: border-color 0.15s, box-shadow 0.15s;
  box-sizing: border-box;
}

.form-textarea:focus {
  border-color: var(--kb-ring);
  box-shadow: 0 0 0 3px rgba(59, 111, 224, 0.1);
}

/* 按钮 */
.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 14px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  border: none;
  cursor: pointer;
  transition: opacity 0.15s;
}

.btn-primary:hover {
  opacity: 0.9;
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-secondary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 14px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  background: var(--kb-card);
  color: var(--kb-sidebar-foreground);
  border: 1px solid var(--kb-border);
  cursor: pointer;
  transition: background 0.15s;
}

.btn-secondary:hover {
  background: var(--kb-muted);
}

.btn-secondary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-danger {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 14px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  background: #EF4444;
  color: #fff;
  border: none;
  cursor: pointer;
  transition: opacity 0.15s;
}

.btn-danger:hover {
  opacity: 0.9;
}

.btn-danger:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 批量操作工具栏 */
.batch-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  margin-bottom: 16px;
  background: var(--kb-primary);
  color: #fff;
  border-radius: 8px;
  animation: fadeIn 0.2s ease-out;
}

.batch-info {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.batch-info strong {
  font-weight: 600;
  color: #fff;
  margin: 0 2px;
}

.link-btn {
  background: transparent;
  border: none;
  color: rgba(255, 255, 255, 0.85);
  font-size: 12px;
  cursor: pointer;
  text-decoration: underline;
  padding: 0;
  margin-left: 8px;
}

.link-btn:hover {
  color: #fff;
}

.batch-actions {
  display: flex;
  gap: 8px;
}

.batch-actions .btn-secondary {
  background: rgba(255, 255, 255, 0.15);
  color: #fff;
  border-color: rgba(255, 255, 255, 0.3);
}

.batch-actions .btn-secondary:hover {
  background: rgba(255, 255, 255, 0.25);
}

.batch-actions .btn-secondary:disabled {
  background: rgba(255, 255, 255, 0.1);
  color: rgba(255, 255, 255, 0.5);
}

/* 复选框样式 */
.checkbox-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  flex-shrink: 0;
}

.checkbox {
  width: 16px;
  height: 16px;
  cursor: pointer;
  accent-color: var(--kb-primary);
}

/* 网格视图复选框 */
.card-checkbox {
  display: flex;
  align-items: center;
  cursor: pointer;
}

.card-checkbox .checkbox {
  width: 18px;
  height: 18px;
}

.doc-card.card-checked {
  border-color: var(--kb-primary);
  box-shadow: 0 0 0 2px rgba(59, 111, 224, 0.15);
}

/* 移动文档弹窗 */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  animation: fadeIn 0.2s ease-out;
}

.modal-content {
  background: var(--kb-card);
  border-radius: 10px;
  width: 90%;
  max-width: 560px;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.2);
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--kb-border);
}

.modal-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--kb-foreground);
}

.modal-close {
  background: transparent;
  border: none;
  color: var(--kb-muted-foreground);
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.modal-close:hover {
  background: var(--kb-muted);
  color: var(--kb-foreground);
}

.modal-body {
  padding: 16px 20px;
  overflow-y: auto;
}

.modal-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--kb-muted-foreground);
  margin: 0 0 12px;
}

.modal-tip strong {
  color: var(--kb-primary);
  margin: 0 2px;
}

.kb-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 360px;
  overflow-y: auto;
}

.kb-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--kb-border);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s;
}

.kb-item:hover {
  background: var(--kb-muted);
}

.kb-item.active {
  border-color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.05);
}

.radio-input {
  width: 16px;
  height: 16px;
  cursor: pointer;
  accent-color: var(--kb-primary);
  flex-shrink: 0;
}

.kb-info {
  flex: 1;
  min-width: 0;
}

.kb-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--kb-foreground);
  margin-bottom: 2px;
}

.kb-desc {
  font-size: 12px;
  color: var(--kb-muted-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 20px;
  border-top: 1px solid var(--kb-border);
}

/* 响应式：移动端 */
@media (max-width: 768px) {
  .doc-mgmt-wrap {
    padding: 16px;
  }

  .table-row {
    flex-wrap: wrap;
    gap: 8px;
  }

  .col-name {
    flex: 1 1 100%;
  }

  .col-category,
  .col-size,
  .col-modified,
  .col-owner,
  .col-progress {
    width: auto;
    flex: 1;
  }

  .col-actions {
    width: auto;
  }

  .filter-right {
    margin-left: 0;
    width: 100%;
  }

  .search-input {
    width: 100%;
    flex: 1;
  }

  .pagination {
    justify-content: center;
  }

  .doc-grid {
    grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
    gap: 12px;
  }
}
</style>
