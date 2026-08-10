<template>
  <div class="knowledge-layout">
    <!-- 左侧边栏 -->
    <KnowledgeSidebar
      mode="categories"
      :active-category-id="activeCategoryIdForSidebar"
      @navigate="onSidebarNavigate"
    />

    <!-- 主内容区 -->
    <main class="knowledge-main">
      <!-- 顶部面包屑 + 搜索 -->
      <div class="top-bar">
        <div class="breadcrumb">
          <Icon name="folder-tree" :size="14" />
          <span>分类浏览</span>
          <template v-if="selectedCategory">
            <Icon name="chevron-right" :size="12" class="sep" />
            <span class="current">{{ selectedCategory.name }}</span>
          </template>
        </div>

        <div class="search-box">
          <Icon name="search" :size="16" class="search-icon" />
          <input
            v-model="searchKeyword"
            type="text"
            placeholder="搜索分类..."
            class="search-input"
          />
        </div>
      </div>

      <!-- 左侧分类网格（未选中分类时） -->
      <section v-if="!selectedCategory" class="content-section">
        <div class="section-header">
          <h1 class="page-title">全部分类</h1>
          <span class="section-subtitle">共 {{ filteredCategories.length }} 个分类</span>
        </div>

        <div v-if="filteredCategories.length > 0" class="category-grid">
          <div
            v-for="(cat, idx) in filteredCategories"
            :key="cat.id"
            class="category-card"
            role="button"
            tabindex="0"
            @keydown.enter.prevent="($event.target as HTMLElement).click()"
            @click="selectCategory(cat)"
          >
            <div class="cat-icon" :style="{ background: `${categoryColors[idx % categoryColors.length]}14` }">
              <Icon :name="getCategoryIcon(cat.icon, idx)" :size="24" :style="{ color: categoryColors[idx % categoryColors.length] }" />
            </div>
            <div class="cat-info">
              <h3 class="cat-name">{{ cat.name }}</h3>
              <p class="cat-meta">
                <span class="cat-count">{{ cat.docCount || 0 }} 篇文档</span>
                <span class="cat-desc">{{ getCategoryDescription(cat.name) }}</span>
              </p>
            </div>
            <Icon name="arrow-right" :size="16" class="cat-arrow" />
          </div>
        </div>

        <div v-else class="empty-state">
          <Icon name="folder-question" :size="32" class="empty-icon" />
          <p>暂无分类</p>
        </div>
      </section>

      <!-- 右侧分类文档列表（选中分类后） -->
      <section v-else class="content-section">
        <div class="section-header">
          <div class="flex items-center flex-wrap gap-3 min-w-0">
            <button
              type="button"
              class="back-btn"
              @click="backToCategories"
            >
              <Icon name="arrow-left" :size="14" />
              <span>返回分类</span>
            </button>
            <h1 class="page-title">
              <Icon :name="getCategoryIcon(selectedCategory.icon, 0)" :size="20" class="cat-title-icon" />
              {{ selectedCategory.name }}
            </h1>
            <span class="count-pill">{{ filteredDocs.length }} 篇文档</span>
          </div>
          <div class="actions">
            <div class="search-box small">
              <Icon name="search" :size="14" class="search-icon" />
              <input
                v-model="docSearchKeyword"
                type="text"
                placeholder="搜索文档..."
                class="search-input"
              />
            </div>
            <select
              v-model="sortBy"
              class="sort-select"
            >
              <option value="latest">按时间排序</option>
              <option value="hot">按热度排序</option>
              <option value="name">按名称排序</option>
            </select>
          </div>
        </div>

        <!-- 加载中 -->
        <div v-if="loading" class="loading-card">
          <Icon name="loader" :size="24" class="spin" />
          <p>加载中...</p>
        </div>

        <!-- 空态 -->
        <div v-else-if="filteredDocs.length === 0" class="empty-state">
          <Icon name="file-question" :size="32" class="empty-icon" />
          <p>该分类下暂无文档</p>
        </div>

        <!-- 文档列表 -->
        <div v-else class="doc-list">
          <div
            v-for="(doc, idx) in filteredDocs"
            :key="doc.id"
            class="doc-item"
            role="button"
            tabindex="0"
            @keydown.enter.prevent="($event.target as HTMLElement).click()"
            @click="goToDoc(doc.id)"
          >
            <DocTypeBadge :file-url="doc.fileUrl" :content="doc.content" :size="40" />
            <div class="doc-main">
              <h4 class="doc-title">{{ doc.title }}</h4>
              <p class="doc-summary">{{ doc.summary || '暂无摘要' }}</p>
              <div class="doc-meta">
                <span
                  class="doc-type"
                  :style="{ background: `${docIconColors[idx % docIconColors.length]}14`, color: docIconColors[idx % docIconColors.length] }"
                >{{ getDocType(doc) }}</span>
                <span class="doc-time">{{ formatRelativeTime(doc.createTime) }}</span>
                <span class="doc-views"><Icon name="eye" :size="12" /> {{ doc.viewCount || 0 }}</span>
              </div>
            </div>
            <Icon name="arrow-right" :size="16" class="doc-arrow" />
          </div>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import KnowledgeSidebar from '@/components/layout/KnowledgeSidebar.vue'
import { categoriesApi, docsApi } from '@/api'
import type { CategoryVO, DocVO } from '@/api/types'
import DocTypeBadge from '@/components/doc/DocTypeBadge.vue'
import { resolveDocType } from '@/utils/docType'

const route = useRoute()
const router = useRouter()

const categoryTree = ref<CategoryVO[]>([])
const allDocs = ref<DocVO[]>([])
const loading = ref(false)
const searchKeyword = ref('')
const docSearchKeyword = ref('')
const sortBy = ref<'latest' | 'hot' | 'name'>('latest')
const selectedCategoryId = ref<number | string>('')

const categoryColors = ['#3B6FE0', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6']
const docIconColors = ['#3B6FE0', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6']

const iconOptions = ['code', 'server', 'database', 'brain', 'settings', 'git-branch', 'monitor', 'wifi', 'folder', 'layers', 'book-open', 'cpu', 'palette', 'briefcase', 'shield', 'message-square', 'target', 'bar-chart-2', 'bot', 'lock', 'layout', 'smartphone', 'container', 'binary', 'kanban', 'file-code']

const getCategoryIcon = (iconName: string | undefined, idx: number): string => {
  if (iconName && iconOptions.includes(iconName)) return iconName
  const fallback = ['brain', 'layout', 'server', 'bar-chart-2', 'smartphone', 'database', 'container', 'shield', 'binary', 'palette', 'file-code', 'kanban']
  return fallback[idx % fallback.length]
}

const topLevelCategories = computed<CategoryVO[]>(() => categoryTree.value)

const filteredCategories = computed(() => {
  if (!searchKeyword.value.trim()) return topLevelCategories.value
  const kw = searchKeyword.value.toLowerCase()
  return topLevelCategories.value.filter(c => (c.name || '').toLowerCase().includes(kw))
})

const selectedCategory = computed(() => {
  if (!selectedCategoryId.value) return null
  return topLevelCategories.value.find(c => c.id === Number(selectedCategoryId.value)) || null
})

const activeCategoryIdForSidebar = computed(() => {
  if (selectedCategoryId.value) return Number(selectedCategoryId.value)
  return null
})

const getAllChildCategoryIds = (categoryId: number | string, categories: CategoryVO[]): number[] => {
  const ids: number[] = [Number(categoryId)]
  for (const cat of categories) {
    if (cat.id === Number(categoryId) && cat.children) {
      cat.children.forEach((child) => {
        ids.push(...getAllChildCategoryIds(child.id, cat.children || []))
      })
    }
  }
  return ids
}

const filteredDocs = computed(() => {
  let result = [...allDocs.value]
  if (docSearchKeyword.value.trim()) {
    const kw = docSearchKeyword.value.toLowerCase()
    result = result.filter(d =>
      (d.title || '').toLowerCase().includes(kw) ||
      (d.summary || '').toLowerCase().includes(kw)
    )
  }
  if (sortBy.value === 'latest') {
    return result.sort((a, b) => new Date(b.createTime || '').getTime() - new Date(a.createTime || '').getTime())
  }
  if (sortBy.value === 'hot') {
    return result.sort((a, b) => (b.viewCount || 0) - (a.viewCount || 0))
  }
  return result.sort((a, b) => (a.title || '').localeCompare(b.title || ''))
})

const getDocType = (doc: DocVO): string => resolveDocType(doc.fileUrl, doc.content).label

const getCategoryDescription = (name: string): string => {
  const descMap: Record<string, string> = {
    '人工智能': '机器学习、深度学习、NLP 等前沿技术',
    '前端开发': 'React、Vue、CSS、JavaScript 核心知识',
    '后端技术': 'Node.js、Python、微服务与系统架构',
    '数据科学': '数据分析、可视化、统计学与数据工程',
    '移动开发': 'Flutter、React Native、iOS 和 Android',
    '数据库': 'SQL 优化、NoSQL、数据建模与索引',
    'DevOps': 'Docker、K8s、CI/CD 与云原生实践',
    '网络安全': '渗透测试、加密算法与安全架构设计',
    '算法与数据结构': '排序、图论、动态规划与面试题解',
    '产品设计': '用户体验、交互设计与设计系统构建',
    '编程语言': 'Python、Java、Go、Rust 语言深度学习',
    '项目管理': '敏捷开发、Scrum 与团队协作方法论'
  }
  return descMap[name] || '系统化学习该领域的核心知识'
}

const formatRelativeTime = (dateStr?: string) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const days = Math.floor(diff / (1000 * 60 * 60 * 24))
  const hours = Math.floor(diff / (1000 * 60 * 60))
  if (days === 0) {
    if (hours === 0) return '刚刚'
    return `${hours}小时前`
  }
  if (days === 1) return '昨天'
  if (days < 7) return `${days}天前`
  if (days < 30) return `${Math.floor(days / 7)}周前`
  return `${Math.floor(days / 30)}月前`
}

let categoryLoadToken = 0
const loadCategoryDocs = async (categoryId: number | string) => {
  const token = ++categoryLoadToken
  loading.value = true
  try {
    const ids = getAllChildCategoryIds(categoryId, categoryTree.value)
    let merged: DocVO[] = []
    for (const id of ids) {
      const res = await docsApi.list({ categoryId: id, pageSize: 100 })
      if (token !== categoryLoadToken) return // 过期请求：已被更新的点击取代，丢弃结果
      merged = merged.concat(res.records || [])
    }
    if (token !== categoryLoadToken) return
    allDocs.value = [...new Map(merged.map((d) => [d.id, d])).values()]
  } catch {
    if (token !== categoryLoadToken) return
    allDocs.value = []
  } finally {
    if (token === categoryLoadToken) loading.value = false
  }
}

const selectCategory = (cat: CategoryVO) => {
  selectedCategoryId.value = cat.id
  router.push({ path: '/categories', query: { categoryId: String(cat.id) } })
  // 实际加载由 watch(route.query.categoryId) 统一驱动，避免重复请求与竞态
}

const backToCategories = () => {
  selectedCategoryId.value = ''
  router.push({ path: '/categories' })
  allDocs.value = []
}

const goToDoc = (docId: number) => {
  router.push(`/doc/${docId}`)
}

const onSidebarNavigate = () => {
  // 侧边栏导航会通过路由变化自动触发
}

onMounted(async () => {
  try {
    categoryTree.value = await categoriesApi.tree()
  } catch {
    categoryTree.value = []
  }
  const categoryId = route.query.categoryId as string
  if (categoryId) {
    selectedCategoryId.value = Number(categoryId)
    loadCategoryDocs(categoryId)
  }
})

watch(
  () => route.query.categoryId,
  (newCategoryId) => {
    if (newCategoryId && typeof newCategoryId === 'string') {
      selectedCategoryId.value = Number(newCategoryId)
      loadCategoryDocs(newCategoryId)
    } else if (!newCategoryId) {
      selectedCategoryId.value = ''
      allDocs.value = []
    }
  }
)
</script>

<style scoped>
/* 全屏布局：抵消 CLayout 的 px-4 sm:px-6 py-6 内边距 */
.knowledge-layout {
  display: flex;
  margin: calc(var(--kb-space-6) * -1) calc(var(--kb-space-6) * -1) 0;
  min-height: calc(100vh - 56px);
}

.knowledge-main {
  flex: 1;
  min-width: 0;
  padding: var(--kb-space-6) var(--kb-space-8) 40px;
  overflow-y: auto;
  height: calc(100vh - 56px);
}

/* 顶部条 */
.top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--kb-space-4);
  margin-bottom: var(--kb-space-6);
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: var(--kb-space-2);
  min-width: 0;
  font-size: var(--kb-fs-body-sm);
  line-height: var(--kb-lh-body-sm);
  color: var(--kb-muted-foreground);
}

.breadcrumb .sep {
  color: var(--kb-muted-foreground);
  opacity: 0.6;
  flex-shrink: 0;
}

.breadcrumb .current {
  font-weight: 500;
  color: var(--kb-primary);
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.search-box {
  position: relative;
  width: 240px;
}

.search-box.small {
  width: 180px;
}

.search-icon {
  position: absolute;
  left: 12px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--kb-muted-foreground);
}

.search-input {
  width: 100%;
  height: 36px;
  padding-left: 36px;
  padding-right: var(--kb-space-3);
  border-radius: 8px;
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  font-size: var(--kb-fs-body-sm);
  line-height: var(--kb-lh-body-sm);
  color: var(--kb-foreground);
  outline: none;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.search-input:hover {
  border-color: var(--kb-primary);
}

.search-input:focus,
.search-input:focus-visible {
  border-color: var(--kb-primary);
  box-shadow: 0 0 0 3px rgba(59, 111, 224, 0.15);
  outline: none;
}

/* 内容区 */
.content-section {
  display: flex;
  flex-direction: column;
  gap: var(--kb-space-5);
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--kb-space-3);
}

.page-title {
  font-size: var(--kb-fs-h4);
  line-height: var(--kb-lh-h4);
  font-weight: 700;
  color: var(--kb-foreground);
  display: flex;
  align-items: center;
  gap: var(--kb-space-2);
  min-width: 0;
}

.cat-title-icon {
  color: var(--kb-primary);
  flex-shrink: 0;
}

.section-subtitle {
  font-size: var(--kb-fs-body-sm);
  line-height: var(--kb-lh-body-sm);
  color: var(--kb-muted-foreground);
  margin-left: var(--kb-space-2);
  font-variant-numeric: tabular-nums;
}

.count-pill {
  display: inline-flex;
  align-items: center;
  padding: 2px var(--kb-space-2);
  border-radius: 999px;
  font-size: var(--kb-fs-caption);
  line-height: var(--kb-lh-caption);
  font-weight: 600;
  background: rgba(59, 111, 224, 0.1);
  color: var(--kb-primary);
  margin-left: var(--kb-space-2);
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--kb-space-3);
}

/* 排序下拉：对齐设计系统 .kb-select 的交互规格（hover 主色边框 + focus 主色高亮环） */
.sort-select {
  height: 36px;
  padding: 0 28px 0 var(--kb-space-3);
  border-radius: 8px;
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  font-size: var(--kb-fs-body-sm);
  line-height: var(--kb-lh-body-sm);
  color: var(--kb-foreground);
  outline: none;
  cursor: pointer;
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 24 24' fill='none' stroke='%236B7280' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpolyline points='6 9 12 15 18 9'%3E%3C/polyline%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right var(--kb-space-2) center;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.sort-select:hover {
  border-color: var(--kb-primary);
}

.sort-select:active {
  border-color: var(--kb-primary);
}

.sort-select:focus {
  border-color: var(--kb-primary);
  box-shadow: 0 0 0 3px rgba(59, 111, 224, 0.1);
}

.sort-select:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}

.back-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--kb-space-1);
  padding: 6px var(--kb-space-3);
  border-radius: var(--kb-radius-sm);
  font-size: var(--kb-fs-body-sm);
  line-height: var(--kb-lh-body-sm);
  font-weight: 500;
  color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.08);
  border: none;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.15s ease, transform 0.15s ease;
}

.back-btn:hover {
  background: rgba(59, 111, 224, 0.15);
}

.back-btn:active {
  background: rgba(59, 111, 224, 0.22);
  transform: scale(0.98);
}

.back-btn:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}

/* 分类网格 */
.category-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: var(--kb-space-4);
}

.category-card {
  display: flex;
  align-items: center;
  gap: var(--kb-space-4);
  min-width: 0;
  padding: var(--kb-space-4) var(--kb-space-5);
  border-radius: 12px;
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  cursor: pointer;
  transition: border-color 0.15s ease, box-shadow 0.15s ease, transform 0.15s ease, background 0.15s ease;
}

.category-card:hover {
  border-color: var(--kb-primary);
  box-shadow: 0 4px 16px rgba(59, 111, 224, 0.08);
  transform: translateY(-1px);
}

.category-card:active {
  transform: translateY(0) scale(0.98);
  background: rgba(59, 111, 224, 0.04);
}

.category-card:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
  border-color: var(--kb-primary);
  /* 覆盖全局 [role='button']:focus-visible 的 6px 圆角，保持卡片自身圆角 */
  border-radius: 12px;
}

.category-card:focus-visible .cat-arrow {
  opacity: 1;
  color: var(--kb-primary);
}

.cat-icon {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.cat-info {
  flex: 1;
  min-width: 0;
}

.cat-name {
  font-size: var(--kb-fs-body-md);
  line-height: var(--kb-lh-body-md);
  font-weight: 600;
  color: var(--kb-foreground);
  margin-bottom: var(--kb-space-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cat-meta {
  display: flex;
  align-items: center;
  gap: var(--kb-space-2);
  min-width: 0;
  font-size: var(--kb-fs-caption);
  line-height: var(--kb-lh-caption);
  color: var(--kb-muted-foreground);
}

.cat-count {
  font-weight: 600;
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}

.cat-desc {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cat-arrow {
  color: var(--kb-muted-foreground);
  flex-shrink: 0;
  opacity: 0;
  transition: opacity 0.15s ease, color 0.15s ease;
}

.category-card:hover .cat-arrow {
  opacity: 1;
  color: var(--kb-primary);
}

/* 文档列表 */
.doc-list {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--kb-border);
  border-radius: 12px;
  overflow: hidden;
  background: var(--kb-card);
}

.doc-item {
  display: flex;
  align-items: center;
  gap: var(--kb-space-4);
  min-width: 0;
  padding: var(--kb-space-4) var(--kb-space-5);
  border-bottom: 1px solid var(--kb-border);
  cursor: pointer;
  transition: background 0.15s ease, transform 0.15s ease;
}

.doc-item:last-child {
  border-bottom: none;
}

.doc-item:hover {
  background: rgba(59, 111, 224, 0.03);
}

.doc-item:active {
  background: rgba(59, 111, 224, 0.08);
  transform: scale(0.995);
}

.doc-item:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: -2px;
  background: rgba(59, 111, 224, 0.03);
  /* 覆盖全局 [role='button']:focus-visible 的 6px 圆角，列表行保持直角 */
  border-radius: 0;
}

.doc-item:focus-visible .doc-arrow {
  opacity: 1;
  color: var(--kb-primary);
}

.doc-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.doc-main {
  flex: 1;
  min-width: 0;
}

.doc-title {
  font-size: var(--kb-fs-body-md);
  line-height: var(--kb-lh-body-md);
  font-weight: 600;
  color: var(--kb-foreground);
  margin-bottom: var(--kb-space-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.doc-summary {
  font-size: var(--kb-fs-body-sm);
  line-height: var(--kb-lh-body-sm);
  color: var(--kb-muted-foreground);
  margin-bottom: var(--kb-space-2);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.doc-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--kb-space-3);
  font-size: var(--kb-fs-caption);
  line-height: var(--kb-lh-caption);
}

.doc-type {
  padding: 2px var(--kb-space-2);
  border-radius: 4px;
  font-weight: 600;
  white-space: nowrap;
}

.doc-time {
  color: var(--kb-muted-foreground);
  white-space: nowrap;
}

.doc-views {
  display: inline-flex;
  align-items: center;
  gap: var(--kb-space-1);
  color: var(--kb-muted-foreground);
  font-variant-numeric: tabular-nums;
}

.doc-arrow {
  color: var(--kb-muted-foreground);
  flex-shrink: 0;
  opacity: 0;
  transition: opacity 0.15s ease, color 0.15s ease;
}

.doc-item:hover .doc-arrow {
  opacity: 1;
  color: var(--kb-primary);
}

/* 空态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--kb-space-3);
  padding: 60px var(--kb-space-5);
  color: var(--kb-muted-foreground);
  font-size: var(--kb-fs-body-md);
  line-height: var(--kb-lh-body-md);
  background: var(--kb-card);
  border: 1px dashed var(--kb-border);
  border-radius: 12px;
}

.empty-icon {
  color: var(--kb-muted-foreground);
  opacity: 0.6;
}

.loading-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--kb-space-3);
  padding: 60px var(--kb-space-5);
  color: var(--kb-muted-foreground);
  font-size: var(--kb-fs-body-md);
  line-height: var(--kb-lh-body-md);
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: 12px;
}

.spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

@media (max-width: 768px) {
  .knowledge-layout {
    margin: calc(var(--kb-space-4) * -1) calc(var(--kb-space-4) * -1) 0;
  }
  .knowledge-main {
    padding: var(--kb-space-4);
  }
  .top-bar {
    flex-direction: column;
    align-items: stretch;
  }
  .search-box,
  .search-box.small {
    width: 100%;
  }
  .category-grid {
    grid-template-columns: 1fr;
  }
  /* 小屏：文档列表内边距收敛到 16px，避免横向溢出 */
  .doc-item,
  .category-card {
    padding: var(--kb-space-4);
  }
  .actions {
    width: 100%;
  }
  .actions .search-box.small {
    flex: 1;
    min-width: 0;
  }
  .sort-select {
    flex-shrink: 0;
  }
}
</style>
