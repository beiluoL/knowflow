<template>
  <!-- 管理后台-文档管理：标题+操作组、视图切换、文件类型筛选、表格（含阅读进度）、分页 -->
  <div class="doc-mgmt-wrap">
    <!-- 页面标题 + 操作按钮 -->
    <div class="page-head">
      <div class="title-group">
        <h1 class="kb-h1">文档管理</h1>
        <span class="kb-body-sm">共 {{ totalDocs }} 个</span>
      </div>
      <div class="head-actions">
        <button class="btn-primary" @click="goToUpload">
          <Icon name="upload" :size="16" />
          <span>上传</span>
        </button>
        <button class="btn-secondary" @click="goToCreate">
          <Icon name="file-plus" :size="16" />
          <span>新建</span>
        </button>
        <button class="btn-secondary" @click="toggleBatch">
          <Icon name="check-square" :size="16" />
          <span>{{ batchMode ? '退出批量' : '批量操作' }}</span>
        </button>
      </div>
    </div>

    <!-- 视图切换 -->
    <div class="view-switcher">
      <button
        class="view-btn"
        :class="{ active: viewMode === 'list' }"
        @click="viewMode = 'list'"
      >
        <Icon name="list" :size="16" />
        <span>列表视图</span>
      </button>
      <button
        class="view-btn"
        :class="{ active: viewMode === 'grid' }"
        @click="viewMode = 'grid'"
      >
        <Icon name="layout-grid" :size="16" />
        <span>网格视图</span>
      </button>
    </div>

    <!-- 筛选条 -->
    <div class="filter-bar">
      <div class="filter-types">
        <button
          v-for="t in fileTypes"
          :key="t.value"
          class="filter-btn"
          :class="{ active: selectedType === t.value }"
          @click="selectedType = t.value"
        >
          {{ t.label }}
        </button>
      </div>
      <div class="filter-right">
        <div class="sort-group">
          <span class="sort-label">排序:</span>
          <select v-model="sortBy" class="sort-select">
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
            placeholder="搜索..."
            class="search-input"
          />
        </div>
      </div>
    </div>

    <!-- 文档表格 -->
    <div class="table-card">
      <!-- 表头 -->
      <div class="table-row table-head">
        <label v-if="batchMode" class="checkbox-cell">
          <input
            type="checkbox"
            :checked="allChecked"
            @change="toggleAll"
            class="checkbox"
          />
        </label>
        <div class="col-name">文件名</div>
        <div class="col-category">分类</div>
        <div class="col-size">大小</div>
        <div class="col-modified">修改时间</div>
        <div class="col-owner">所有者</div>
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
            class="checkbox"
          />
        </label>
        <div class="col-name">
          <div class="file-name-cell">
            <div class="file-icon" :style="{ background: doc.iconBg }">
              <Icon :name="doc.icon" :size="16" :style="{ color: doc.iconColor }" />
            </div>
            <span class="file-name-text" :title="doc.title">{{ doc.title }}</span>
          </div>
        </div>
        <div class="col-category">
          <span class="category-badge" :style="categoryStyle(doc.categoryColor)">{{ doc.category }}</span>
        </div>
        <div class="col-size">{{ doc.size }}</div>
        <div class="col-modified">{{ doc.modified }}</div>
        <div class="col-owner">{{ doc.owner }}</div>
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
          <button class="icon-btn" title="分享" @click="shareDoc(doc)">
            <Icon name="share-2" :size="14" />
          </button>
          <button class="icon-btn" title="下载" @click="downloadDoc(doc)">
            <Icon name="download" :size="14" />
          </button>
          <button class="icon-btn" title="更多" @click="openMenu(doc)">
            <Icon name="more-vertical" :size="14" />
          </button>
        </div>
      </div>

      <!-- 空态 -->
      <p v-if="pagedDocs.length === 0" class="table-empty">暂无文档数据</p>
    </div>

    <!-- 分页 -->
    <div class="pagination">
      <span class="page-info">显示 {{ rangeStart }}-{{ rangeEnd }} / 共 {{ totalDocs }} 个</span>
      <div class="page-buttons">
        <button
          class="page-btn"
          :disabled="currentPage === 1"
          @click="currentPage = Math.max(1, currentPage - 1)"
        >
          <Icon name="chevron-left" :size="16" />
        </button>
        <button
          v-for="page in visiblePages"
          :key="page"
          class="page-btn"
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
          class="page-btn"
          :disabled="currentPage === totalPages"
          @click="currentPage = Math.min(totalPages, currentPage + 1)"
        >
          <Icon name="chevron-right" :size="16" />
        </button>
      </div>
      <div class="page-size">
        <span class="page-size-label">每页:</span>
        <select v-model.number="pageSize" class="page-size-select">
          <option :value="10">10</option>
          <option :value="20">20</option>
          <option :value="50">50</option>
        </select>
      </div>
    </div>

    <!-- 更多操作下拉菜单 -->
    <div
      v-if="menuDoc"
      class="menu-mask"
      @click="menuDoc = null"
    >
      <div class="menu-popover" @click.stop>
        <button class="menu-item" @click="openEdit(menuDoc); menuDoc = null">
          <Icon name="edit" :size="14" />
          <span>编辑</span>
        </button>
        <button class="menu-item danger" @click="removeDoc(menuDoc); menuDoc = null">
          <Icon name="trash-2" :size="14" />
          <span>删除</span>
        </button>
      </div>
    </div>

    <!-- 编辑弹窗 -->
    <div
      v-if="showModal"
      class="modal-mask"
      @click.self="closeModal"
    >
      <div class="modal-card">
        <div class="modal-head">
          <h3 class="kb-h3">编辑文档</h3>
          <button class="icon-btn" title="关闭" @click="closeModal">
            <Icon name="x" :size="18" />
          </button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label class="form-label">标题</label>
            <input v-model="form.title" type="text" class="form-input" placeholder="请输入文档标题" />
          </div>
          <div class="form-row">
            <div class="form-group">
              <label class="form-label">分类</label>
              <select v-model.number="form.categoryId" class="form-input">
                <option :value="0">未分类</option>
                <option v-for="cat in categoryOptions" :key="cat.id" :value="cat.id">{{ cat.name }}</option>
              </select>
            </div>
            <div class="form-group">
              <label class="form-label">状态</label>
              <select v-model.number="form.status" class="form-input">
                <option :value="1">已发布</option>
                <option :value="0">草稿</option>
                <option :value="2">已禁用</option>
              </select>
            </div>
          </div>
          <div class="form-group">
            <label class="form-label">摘要</label>
            <input v-model="form.summary" type="text" class="form-input" placeholder="请输入文档摘要" />
          </div>
          <div class="form-group">
            <label class="form-label">标签（逗号分隔）</label>
            <input v-model="form.tags" type="text" class="form-input" placeholder="如：Vue,前端,框架" />
          </div>
        </div>
        <div class="modal-foot">
          <button class="btn-secondary" @click="closeModal">取消</button>
          <button class="btn-primary" :disabled="saving" @click="save">
            {{ saving ? '保存中...' : '保存' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// 管理后台-文档管理：文档列表展示、类型筛选、排序、分页、批量选择、编辑/删除。
// 注：文件大小/所有者/阅读进度字段后端暂未提供，使用 mock 数据展示。
import { confirmDialog, getApiError, notify } from '@/utils/toast'
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { adminApi } from '@/api'
import type { CategoryVO, DocVO, DocInput } from '@/api/types'

const router = useRouter()

const goToCreate = () => router.push('/docs/new')
const goToUpload = () => router.push('/upload')

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
  icon: string
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
const sortBy = ref('modified')
const searchQuery = ref('')
const batchMode = ref(false)
const checkedIds = ref<number[]>([])
const currentPage = ref(1)
const pageSize = ref(10)

const categoryOptions = ref<CategoryVO[]>([])
const allDocs = ref<DocRow[]>([])
const loading = ref(false)

/** 更多操作菜单 */
const menuDoc = ref<DocRow | null>(null)

/** 编辑弹窗 */
const showModal = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const form = ref<DocInput & { content?: string }>({
  title: '',
  summary: '',
  categoryId: 0,
  tags: '',
  content: '',
  status: 1,
})

/** 颜色池：用于分类徽标 */
const categoryColors = ['#F59E0B', '#3B6FE0', '#10B981', '#EF4444', '#6B7280']

/** 根据文档标题推断文件类型图标与颜色 */
const inferIcon = (title: string): { icon: string; bg: string; color: string } => {
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

/** 根据文件类型筛选 */
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

/** 搜索 + 排序后的列表 */
const filteredDocs = computed(() => {
  let result = [...filteredByType.value]
  if (searchQuery.value) {
    const q = searchQuery.value.toLowerCase()
    result = result.filter((d) => d.title.toLowerCase().includes(q))
  }
  // 排序
  if (sortBy.value === 'name') {
    result.sort((a, b) => a.title.localeCompare(b.title))
  } else if (sortBy.value === 'size') {
    result.sort((a, b) => parseFloat(b.size) - parseFloat(a.size))
  } else {
    // 修改时间倒序
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
    // 取消当前页选中
    checkedIds.value = checkedIds.value.filter(
      (id) => !pagedDocs.value.some((d) => d.id === id),
    )
  } else {
    // 选中当前页
    pagedDocs.value.forEach((d) => {
      if (!checkedIds.value.includes(d.id)) checkedIds.value.push(d.id)
    })
  }
}

const toggleBatch = () => {
  batchMode.value = !batchMode.value
  if (!batchMode.value) checkedIds.value = []
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

/** 打开编辑弹窗 */
const openEdit = (doc: DocRow) => {
  editingId.value = doc.id
  form.value = {
    title: doc.raw.title ?? '',
    summary: doc.raw.summary ?? '',
    categoryId: doc.raw.categoryId ?? 0,
    tags: doc.raw.tags ?? '',
    content: (doc.raw as Record<string, unknown>).content ?? '',
    status: doc.raw.status ?? 1,
  }
  showModal.value = true
}

const closeModal = () => {
  showModal.value = false
  editingId.value = null
}

const save = async () => {
  if (!form.value.title.trim()) {
    notify('请填写文档标题', 'warning')
    return
  }
  saving.value = true
  try {
    const payload: DocInput = {
      title: form.value.title,
      summary: form.value.summary,
      categoryId: form.value.categoryId || undefined,
      tags: form.value.tags,
      content: form.value.content,
      status: form.value.status,
    }
    if (editingId.value) {
      await adminApi.updateDoc(editingId.value, payload)
    }
    notify('更新成功', 'success')
    closeModal()
    await loadDocs()
  } catch (e: unknown) {
    notify('保存失败：' + getApiError(e), 'error')
  } finally {
    saving.value = false
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

const downloadDoc = (_doc: DocRow) => {
  notify('开始下载（演示）', 'success')
}

/** 加载文档列表 */
const loadDocs = async () => {
  loading.value = true
  try {
    const [docPage, cats] = await Promise.all([
      adminApi.docs({ pageSize: 200 }),
      adminApi.categories(),
    ])
    categoryOptions.value = cats
    const catMap = new Map<number, { name: string; color: string }>()
    cats.forEach((c, idx) => {
      catMap.set(c.id, {
        name: c.name,
        color: categoryColors[idx % categoryColors.length],
      })
    })

    const records = (docPage.records ?? []) as DocVO[]
    // 注：size/owner/progress 为 mock 字段，后端暂未提供
    const mockSizes = ['2.4 MB', '56 KB', '1.8 MB', '12 KB', '5.1 MB', '128 KB', '890 KB', '8 KB', '3.7 MB', '72 KB']
    const mockOwners = ['张三', '李四', '王五', '赵六']
    allDocs.value = records.map((d, idx) => {
      const cat = catMap.get(d.categoryId ?? -1) ?? { name: '未分类', color: '#6B7280' }
      const iconInfo = inferIcon(d.title ?? '')
      return {
        id: d.id,
        title: d.title ?? '',
        category: cat.name,
        categoryColor: cat.color,
        size: mockSizes[idx % mockSizes.length],
        modified: formatDate(d.createTime),
        owner: mockOwners[idx % mockOwners.length],
        progress: [85, 62, 100, 30, 45, 78, 50, 15, 90, 55][idx % 10],
        icon: iconInfo.icon,
        iconBg: iconInfo.bg,
        iconColor: iconInfo.color,
        raw: d,
      }
    })
  } catch (e: unknown) {
    notify('加载文档失败：' + getApiError(e), 'error')
  } finally {
    loading.value = false
  }
}

onMounted(loadDocs)
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
  max-width: 560px;
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
}
</style>
