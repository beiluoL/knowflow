<template>
  <div class="categories-page animate-fade-in">
    <!-- 左侧边栏 -->
    <aside class="sidebar">
      <div class="sidebar-inner">
        <!-- 返回首页 -->
        <div class="home-entry">
          <router-link to="/" class="home-entry-link">
            <div class="home-entry-icon">
              <Icon name="home" :size="20" />
            </div>
            <div class="home-entry-text">
              <div class="home-entry-title">返回首页</div>
              <div class="home-entry-sub">知识学习门户</div>
            </div>
          </router-link>
        </div>

        <div class="divider"></div>

        <!-- 顶部导航 -->
        <div class="nav-section">
          <div
            class="nav-item"
            :class="{ active: !activeCategoryId && activeNav === 'all' }"
            @click="goToAllDocs"
          >
            <Icon name="home" :size="18" />
            <span>全部文档</span>
          </div>
          <div
            class="nav-item"
            :class="{ active: activeNav === 'favorite' }"
            @click="activeNav = 'favorite'"
          >
            <Icon name="star" :size="18" />
            <span>我的收藏</span>
          </div>
          <div
            class="nav-item"
            :class="{ active: activeNav === 'recent' }"
            @click="activeNav = 'recent'"
          >
            <Icon name="clock" :size="18" />
            <span>最近阅读</span>
          </div>
        </div>

        <div class="divider"></div>

        <!-- 分类目录 -->
        <div class="category-section">
          <div class="section-title">分类目录</div>

          <div
            class="cat-item root-cat"
            :class="{ active: activeCategoryId && currentRootCategoryId === activeCategoryId && rootExpanded }"
            @click="toggleRootCategory"
          >
            <Icon name="cpu" :size="18" class="cat-icon" />
            <span class="cat-name">{{ currentRootCategory?.name || '人工智能' }}</span>
            <span class="count-badge">{{ currentRootDocCount }} 篇文档</span>
            <Icon
              :name="rootExpanded ? 'chevron-up' : 'chevron-down'"
              :size="16"
              class="chevron-icon"
            />
          </div>

          <div v-if="rootExpanded" class="sub-cats">
            <div
              v-for="child in currentRootCategory?.children || []"
              :key="child.id"
              class="cat-item sub-cat"
              :class="{ active: activeCategoryId === child.id }"
              @click="selectCategory(child.id)"
            >
              <span class="dot"></span>
              <span class="cat-name">{{ child.name }}</span>
              <span class="count-num">{{ child.docCount || 0 }}</span>
            </div>
          </div>

          <div
            v-for="cat in otherRootCategories"
            :key="cat.id"
            class="cat-item root-cat collapsed"
            @click="selectRootCategory(cat)"
          >
            <Icon :name="getCategoryIconName(cat.icon || 'folder')" :size="18" class="cat-icon" />
            <span class="cat-name">{{ cat.name }}</span>
            <Icon name="chevron-right" :size="16" class="chevron-icon" />
          </div>
        </div>
      </div>
    </aside>

    <!-- 主内容区 -->
    <main class="main-content">
      <!-- 移动端分类切换按钮 -->
      <button
        type="button"
        class="mobile-cat-toggle"
        @click="mobileSidebarOpen = true"
      >
        <Icon name="menu" :size="16" />
        <span>分类目录</span>
        <Icon name="chevron-down" :size="14" />
      </button>

      <!-- 移动端侧栏遮罩 -->
      <div
        v-if="mobileSidebarOpen"
        class="mobile-overlay"
        @click="mobileSidebarOpen = false"
      ></div>

      <!-- 移动端侧栏 -->
      <aside
        class="sidebar mobile-sidebar"
        :class="{ open: mobileSidebarOpen }"
      >
        <div class="mobile-sidebar-header">
          <span class="mobile-sidebar-title">分类目录</span>
          <button
            type="button"
            class="mobile-sidebar-close"
            @click="mobileSidebarOpen = false"
          >
            <Icon name="x" :size="18" />
          </button>
        </div>
        <div class="sidebar-inner mobile-sidebar-inner">
          <!-- 返回首页 -->
          <div class="home-entry">
            <router-link to="/" class="home-entry-link" @click="mobileSidebarOpen = false">
              <div class="home-entry-icon">
                <Icon name="home" :size="20" />
              </div>
              <div class="home-entry-text">
                <div class="home-entry-title">返回首页</div>
                <div class="home-entry-sub">知识学习门户</div>
              </div>
            </router-link>
          </div>

          <div class="divider"></div>

          <!-- 顶部导航 -->
          <div class="nav-section">
            <div
              class="nav-item"
              :class="{ active: !activeCategoryId && activeNav === 'all' }"
              @click="goToAllDocs; mobileSidebarOpen = false;"
            >
              <Icon name="home" :size="18" />
              <span>全部文档</span>
            </div>
            <div
              class="nav-item"
              :class="{ active: activeNav === 'favorite' }"
              @click="activeNav = 'favorite'; mobileSidebarOpen = false;"
            >
              <Icon name="star" :size="18" />
              <span>我的收藏</span>
            </div>
            <div
              class="nav-item"
              :class="{ active: activeNav === 'recent' }"
              @click="activeNav = 'recent'; mobileSidebarOpen = false;"
            >
              <Icon name="clock" :size="18" />
              <span>最近阅读</span>
            </div>
          </div>

          <div class="divider"></div>

          <!-- 分类目录 -->
          <div class="category-section">
            <div class="section-title">分类目录</div>

            <div
              class="cat-item root-cat"
              :class="{ active: activeCategoryId && currentRootCategoryId === activeCategoryId && rootExpanded }"
              @click="toggleRootCategory"
            >
              <Icon name="cpu" :size="18" class="cat-icon" />
              <span class="cat-name">{{ currentRootCategory?.name || '人工智能' }}</span>
              <span class="count-badge">{{ currentRootDocCount }} 篇文档</span>
              <Icon
                :name="rootExpanded ? 'chevron-up' : 'chevron-down'"
                :size="16"
                class="chevron-icon"
              />
            </div>

            <div v-if="rootExpanded" class="sub-cats">
              <div
                v-for="child in currentRootCategory?.children || []"
                :key="child.id"
                class="cat-item sub-cat"
                :class="{ active: activeCategoryId === child.id }"
                @click="selectCategory(child.id); mobileSidebarOpen = false;"
              >
                <span class="dot"></span>
                <span class="cat-name">{{ child.name }}</span>
                <span class="count-num">{{ child.docCount || 0 }}</span>
              </div>
            </div>

            <div
              v-for="cat in otherRootCategories"
              :key="cat.id"
              class="cat-item root-cat collapsed"
              @click="selectRootCategory(cat)"
            >
              <Icon :name="getCategoryIconName(cat.icon || 'folder')" :size="18" class="cat-icon" />
              <span class="cat-name">{{ cat.name }}</span>
              <Icon name="chevron-right" :size="16" class="chevron-icon" />
            </div>
          </div>
        </div>
      </aside>

      <!-- 面包屑 -->
      <nav class="breadcrumb">
        <span class="crumb clickable" @click="goToAllDocs">全部文档</span>
        <Icon name="chevron-right" :size="14" class="sep" />
        <span v-if="breadcrumbPath.length > 0">
          <template v-for="(item, index) in breadcrumbPath" :key="item.id">
            <span
              :class="['crumb', index === breadcrumbPath.length - 1 ? 'active' : 'clickable']"
              @click="index < breadcrumbPath.length - 1 && selectCategory(item.id)"
            >{{ item.name }}</span>
            <Icon
              v-if="index < breadcrumbPath.length - 1"
              name="chevron-right"
              :size="14"
              class="sep"
            />
          </template>
        </span>
        <span v-else class="crumb active">{{ currentRootCategory?.name || '全部分类' }}</span>
      </nav>

      <!-- 分类标题区 -->
      <div class="category-header">
        <div class="flex items-center gap-3">
          <h1 class="category-title">
            {{ currentCategoryName || currentRootCategory?.name || '人工智能' }}
          </h1>
          <span class="title-badge">{{ currentDocCount }} 篇文档</span>
        </div>
        <p class="category-desc">
          {{ getCategoryDescription() }}
        </p>
      </div>

      <!-- 子分类标签 -->
      <div v-if="currentSubCategories.length > 0" class="subcat-tabs">
        <button
          class="subcat-tab"
          :class="{ active: !isSubCategorySelected }"
          @click="selectCategory(currentRootCategoryId)"
        >
          全部
        </button>
        <button
          v-for="sub in currentSubCategories"
          :key="sub.id"
          class="subcat-tab"
          :class="{ active: activeCategoryId === sub.id }"
          @click="selectCategory(sub.id)"
        >
          {{ sub.name }} {{ sub.docCount || 0 }}
        </button>
      </div>

      <!-- 筛选栏 -->
      <div class="filter-bar">
        <div class="filter-left">
          <div class="filter-dropdown">
            <span>全部类型</span>
            <Icon name="chevron-down" :size="14" />
          </div>
          <div class="filter-dropdown">
            <span>{{ sortBy === 'latest' ? '最近更新' : '最多浏览' }}</span>
            <Icon name="chevron-down" :size="14" />
          </div>
        </div>
        <div class="filter-right">
          <span class="result-count">显示 {{ sortedDocs.length }} 篇文档</span>
          <div class="view-toggle">
            <button
              class="view-btn active"
              @click="viewMode = 'list'"
            >
              <Icon name="list" :size="16" />
            </button>
            <button
              class="view-btn"
              @click="viewMode = 'grid'"
            >
              <Icon name="layout-grid" :size="16" />
            </button>
          </div>
        </div>
      </div>

      <!-- 文档列表 -->
      <div v-if="loading" class="empty-state">
        <Icon name="loader" :size="32" class="spin" />
        <p>加载中...</p>
      </div>

      <div v-else-if="sortedDocs.length === 0" class="empty-state">
        <Icon name="file-question" :size="48" />
        <p>暂无文档</p>
      </div>

      <div v-else class="doc-list">
        <div
          v-for="doc in sortedDocs"
          :key="doc.id"
          class="doc-card"
          @click="goToDoc(doc.id)"
        >
          <div class="doc-icon" :style="{ backgroundColor: getDocIconBg(doc) }">
            <Icon :name="getDocIcon(doc)" :size="18" :style="{ color: getDocIconColor(doc) }" />
          </div>
          <div class="doc-info">
            <h3 class="doc-title">{{ doc.title }}</h3>
            <p class="doc-summary">{{ doc.summary || '暂无摘要' }}</p>
            <div class="doc-meta">
              <span v-for="tag in tagList(doc.tags).slice(0, 2)" :key="tag" class="doc-tag">
                {{ tag }}
              </span>
              <div class="progress-bar">
                <div
                  class="progress-fill"
                  :style="{ width: getReadProgress(doc) + '%', backgroundColor: getProgressColor(doc) }"
                ></div>
              </div>
              <span class="progress-text">{{ getReadProgress(doc) }}%</span>
              <span class="doc-time">{{ formatRelativeTime(doc.createTime) }}</span>
              <button
                class="favorite-btn"
                :class="{ favorited: isFavorited(doc) }"
                @click.stop="toggleFavorite(doc)"
              >
                <Icon :name="isFavorited(doc) ? 'star' : 'star'" :size="14" :fill="isFavorited(doc)" />
              </button>
              <button class="more-btn" @click.stop>
                <Icon name="more-horizontal" :size="16" />
              </button>
            </div>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { categoriesApi, docsApi } from '@/api'
import type { CategoryVO, DocVO } from '@/api/types'

const route = useRoute()
const router = useRouter()

const categoryTree = ref<CategoryVO[]>([])
const docs = ref<DocVO[]>([])
const loading = ref(false)
const activeCategoryId = ref<number | string>('')
const sortBy = ref<'latest' | 'hot'>('latest')
const viewMode = ref<'list' | 'grid'>('list')
const activeNav = ref<'all' | 'favorite' | 'recent'>('all')
const rootExpanded = ref(true)
const mobileSidebarOpen = ref(false)
const favoritedIds = ref<Set<number>>(new Set())

const iconOptions = ['code', 'server', 'database', 'brain', 'settings', 'git-branch', 'monitor', 'wifi', 'folder', 'layers', 'book-open', 'cpu', 'palette', 'briefcase', 'shield', 'message-square', 'target', 'bar-chart-2', 'bot', 'lock']

const getCategoryIconName = (iconName: string): string => {
  return iconOptions.includes(iconName) ? iconName : 'folder'
}

const currentRootCategoryId = computed(() => {
  if (!activeCategoryId.value && categoryTree.value.length > 0) {
    return categoryTree.value[0].id
  }
  const path = getCategoryPath(activeCategoryId.value || 0, categoryTree.value)
  return path.length > 0 ? path[0].id : (categoryTree.value[0]?.id || 0)
})

const currentRootCategory = computed(() => {
  return findCategoryById(currentRootCategoryId.value, categoryTree.value)
})

const currentRootDocCount = computed(() => {
  const cat = currentRootCategory.value
  if (!cat) return 0
  let count = cat.docCount || 0
  if (cat.children) {
    cat.children.forEach(c => { count += c.docCount || 0 })
  }
  return count
})

const otherRootCategories = computed(() => {
  return categoryTree.value.filter(c => c.id !== currentRootCategoryId.value)
})

const currentSubCategories = computed(() => {
  return currentRootCategory.value?.children || []
})

const isSubCategorySelected = computed(() => {
  if (!activeCategoryId.value) return false
  return activeCategoryId.value !== currentRootCategoryId.value
})

const currentDocCount = computed(() => {
  return sortedDocs.value.length
})

const currentCategoryName = computed(() => {
  if (!activeCategoryId.value) return ''
  const cat = findCategoryById(activeCategoryId.value, categoryTree.value)
  return cat?.name || ''
})

const findCategoryById = (id: number | string, categories: CategoryVO[]): CategoryVO | null => {
  const target = Number(id)
  for (const cat of categories) {
    if (cat.id === target) return cat
    if (cat.children) {
      const found = findCategoryById(target, cat.children)
      if (found) return found
    }
  }
  return null
}

const getCategoryPath = (id: number | string, categories: CategoryVO[], path: CategoryVO[] = []): CategoryVO[] => {
  const target = Number(id)
  for (const cat of categories) {
    const currentPath = [...path, cat]
    if (cat.id === target) return currentPath
    if (cat.children) {
      const found = getCategoryPath(target, cat.children, currentPath)
      if (found.length > 0) return found
    }
  }
  return []
}

const breadcrumbPath = computed(() => {
  if (!activeCategoryId.value) return []
  return getCategoryPath(activeCategoryId.value, categoryTree.value)
})

const getCategoryDescription = (): string => {
  const name = currentRootCategory.value?.name || ''
  const descMap: Record<string, string> = {
    '编程开发': '涵盖前端、后端、数据库、架构等全栈技术领域的知识文档',
    '人工智能': '涵盖机器学习、深度学习、自然语言处理等核心技术领域的知识文档',
    '产品设计': '产品经理方法论、UI/UX设计、用户研究等产品设计领域知识',
    '数据科学': '数据分析、数据挖掘、可视化、大数据技术等数据领域知识',
    '后端技术': '后端开发、微服务、数据库、缓存、消息队列等技术知识',
    '前端开发': '前端框架、工程化、性能优化、CSS 布局等前端技术知识',
  }
  return descMap[name] || '该分类下的优质技术文档与学习资源'
}

const getAllChildCategoryIds = (categoryId: number | string): number[] => {
  const ids: number[] = [Number(categoryId)]
  const category = findCategoryById(categoryId, categoryTree.value)
  if (category?.children) {
    category.children.forEach((child) => {
      ids.push(...getAllChildCategoryIds(child.id))
    })
  }
  return ids
}

const sortedDocs = computed(() => {
  let result = [...docs.value]
  if (activeNav.value === 'favorite') {
    result = result.filter(d => favoritedIds.value.has(d.id))
  } else if (activeNav.value === 'recent') {
    result = result.slice(0, 8)
  }
  if (sortBy.value === 'latest') {
    return result.sort(
      (a, b) => new Date(b.createTime || '').getTime() - new Date(a.createTime || '').getTime()
    )
  }
  return result.sort((a, b) => (b.viewCount || 0) - (a.viewCount || 0))
})

const tagList = (tags?: string): string[] => {
  if (!tags) return []
  return tags.split(',').filter(Boolean)
}

const getReadProgress = (doc: DocVO): number => {
  const seed = (doc.id * 17) % 100
  return seed < 10 ? seed + 15 : seed
}

const getProgressColor = (doc: DocVO): string => {
  const progress = getReadProgress(doc)
  if (progress >= 100) return '#10B981'
  if (progress >= 60) return '#3B6FE0'
  return '#8B5CF6'
}

const getDocIcon = (doc: DocVO): string => {
  const tags = (doc.tags || '').toLowerCase()
  if (tags.includes('pdf')) return 'file-text'
  if (tags.includes('markdown') || tags.includes('md')) return 'file-text'
  if (tags.includes('笔记') || tags.includes('note')) return 'sticky-note'
  return 'file-text'
}

const getDocIconBg = (doc: DocVO): string => {
  const tags = (doc.tags || '').toLowerCase()
  if (tags.includes('pdf')) return 'rgba(239, 68, 68, 0.1)'
  if (tags.includes('markdown') || tags.includes('md')) return 'rgba(59, 111, 224, 0.1)'
  if (tags.includes('笔记') || tags.includes('note')) return 'rgba(16, 185, 129, 0.1)'
  return 'rgba(245, 158, 11, 0.1)'
}

const getDocIconColor = (doc: DocVO): string => {
  const tags = (doc.tags || '').toLowerCase()
  if (tags.includes('pdf')) return '#EF4444'
  if (tags.includes('markdown') || tags.includes('md')) return '#3B6FE0'
  if (tags.includes('笔记') || tags.includes('note')) return '#10B981'
  return '#F59E0B'
}

const isFavorited = (doc: DocVO) => favoritedIds.value.has(doc.id)

const toggleFavorite = (doc: DocVO) => {
  if (favoritedIds.value.has(doc.id)) {
    favoritedIds.value.delete(doc.id)
  } else {
    favoritedIds.value.add(doc.id)
  }
}

const loadDocs = async () => {
  loading.value = true
  try {
    const catId = activeCategoryId.value || currentRootCategoryId.value
    if (!catId) {
      const res = await docsApi.list({ pageSize: 100 })
      docs.value = res.records || []
    } else {
      const ids = getAllChildCategoryIds(catId)
      let merged: DocVO[] = []
      for (const id of ids) {
        const res = await docsApi.list({ categoryId: id, pageSize: 100 })
        merged = merged.concat(res.records || [])
      }
      docs.value = [...new Map(merged.map((d) => [d.id, d])).values()]
    }
    favoritedIds.value = new Set(docs.value.slice(0, 3).map(d => d.id))
  } catch {
    docs.value = []
  } finally {
    loading.value = false
  }
}

const toggleRootCategory = () => {
  rootExpanded.value = !rootExpanded.value
  if (rootExpanded.value) {
    selectCategory(currentRootCategoryId.value)
  }
}

const selectRootCategory = (cat: CategoryVO) => {
  activeCategoryId.value = cat.id
  rootExpanded.value = true
  router.push({ path: '/categories', query: { categoryId: String(cat.id) } })
}

const selectCategory = (categoryId: number | string) => {
  activeCategoryId.value = categoryId
  activeNav.value = 'all'
  router.push({ path: '/categories', query: { categoryId: String(categoryId) } })
}

const goToAllDocs = () => {
  activeCategoryId.value = ''
  activeNav.value = 'all'
  router.push('/docs')
}

const goToDoc = (docId: number) => {
  router.push(`/doc/${docId}`)
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

onMounted(async () => {
  try {
    categoryTree.value = await categoriesApi.tree()
  } catch {
    categoryTree.value = []
  }
  const categoryId = route.query.categoryId as string
  if (categoryId) {
    activeCategoryId.value = Number(categoryId)
  } else if (categoryTree.value.length > 0) {
    activeCategoryId.value = categoryTree.value[0].id
  }
  await loadDocs()
})

watch(
  () => route.query.categoryId,
  (newCategoryId) => {
    if (newCategoryId && typeof newCategoryId === 'string') {
      activeCategoryId.value = Number(newCategoryId)
    } else {
      activeCategoryId.value = categoryTree.value[0]?.id || ''
    }
    loadDocs()
  }
)
</script>

<style scoped>
.categories-page {
  display: flex;
  min-height: calc(100vh - 64px);
  background-color: #F8F9FC;
  margin: 0 -24px;
}

.sidebar {
  width: 240px;
  flex-shrink: 0;
  background-color: #F3F4F8;
  border-right: 1px solid #E2E6EC;
}

.sidebar-inner {
  position: sticky;
  top: 0;
  padding: 16px 0;
}

.nav-section {
  padding: 0 8px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  border-radius: 8px;
  font-size: 14px;
  color: #6B7280;
  cursor: pointer;
  transition: all 0.2s;
  margin-bottom: 2px;
}

.nav-item:hover {
  background-color: rgba(59, 111, 224, 0.06);
  color: #374151;
}

.nav-item.active {
  background-color: rgba(59, 111, 224, 0.1);
  color: #3B6FE0;
  font-weight: 500;
}

.divider {
  height: 1px;
  background-color: #E2E6EC;
  margin: 12px 16px;
}

.home-entry {
  padding: 0 12px;
}

.home-entry-link {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  text-decoration: none;
  background: linear-gradient(135deg, rgba(59, 111, 224, 0.08), rgba(139, 92, 246, 0.06));
  transition: background 0.2s ease, transform 0.2s ease;
}

.home-entry-link:hover {
  background: linear-gradient(135deg, rgba(59, 111, 224, 0.15), rgba(139, 92, 246, 0.1));
  transform: translateX(2px);
}

.home-entry-icon {
  width: 36px;
  height: 36px;
  border-radius: 9px;
  background: linear-gradient(135deg, var(--kb-primary), #8B5CF6);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.home-entry-text {
  min-width: 0;
  flex: 1;
}

.home-entry-title {
  font-size: 13px;
  font-weight: 600;
  color: #1F2937;
  line-height: 1.3;
}

.home-entry-sub {
  font-size: 11px;
  color: #6B7280;
  margin-top: 2px;
}

.category-section {
  padding: 0 8px;
}

.section-title {
  font-size: 12px;
  font-weight: 500;
  color: #9CA3AF;
  padding: 8px 16px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.cat-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 14px;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
  position: relative;
}

.cat-item:hover {
  background-color: rgba(59, 111, 224, 0.06);
}

.root-cat {
  color: #374151;
  font-weight: 500;
  margin-bottom: 2px;
}

.root-cat.active {
  background-color: #3B6FE0;
  color: white;
}

.root-cat.active .cat-icon,
.root-cat.active .chevron-icon {
  color: white !important;
}

.root-cat.active .count-badge {
  background-color: rgba(255, 255, 255, 0.2);
  color: white;
}

.cat-icon {
  width: 18px;
  height: 18px;
  flex-shrink: 0;
  color: #6B7280;
}

.cat-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.count-badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
  background-color: #E2E6EC;
  color: #6B7280;
  font-weight: 500;
}

.chevron-icon {
  color: #9CA3AF;
  flex-shrink: 0;
}

.sub-cats {
  padding-left: 8px;
  margin-top: 2px;
}

.sub-cat {
  color: #6B7280;
  font-size: 13px;
  padding: 7px 14px 7px 12px;
}

.sub-cat.active {
  color: #3B6FE0;
  font-weight: 500;
  background-color: rgba(59, 111, 224, 0.08);
}

.sub-cat.active .dot {
  background-color: #3B6FE0;
}

.dot {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background-color: #D1D5DB;
  flex-shrink: 0;
}

.count-num {
  font-size: 12px;
  color: #9CA3AF;
  flex-shrink: 0;
}

.sub-cat.active .count-num {
  color: #3B6FE0;
}

.root-cat.collapsed {
  color: #6B7280;
  font-weight: normal;
}

.main-content {
  flex: 1;
  min-width: 0;
  padding: 24px 32px;
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  margin-bottom: 20px;
}

.crumb {
  color: #6B7280;
}

.crumb.clickable {
  color: #3B6FE0;
  cursor: pointer;
  transition: opacity 0.2s;
}

.crumb.clickable:hover {
  opacity: 0.8;
}

.crumb.active {
  color: #111827;
  font-weight: 500;
}

.sep {
  color: #D1D5DB;
}

.category-header {
  margin-bottom: 24px;
}

.category-title {
  font-size: 28px;
  font-weight: 700;
  color: #111827;
  margin: 0;
}

.title-badge {
  display: inline-flex;
  align-items: center;
  padding: 4px 12px;
  border-radius: 12px;
  background: linear-gradient(135deg, #3B6FE0 0%, #5B8DEF 100%);
  color: white;
  font-size: 13px;
  font-weight: 500;
}

.category-desc {
  font-size: 14px;
  color: #6B7280;
  margin: 10px 0 0 0;
  line-height: 1.6;
}

.subcat-tabs {
  display: flex;
  gap: 4px;
  margin-bottom: 20px;
  border-bottom: 2px solid #E2E6EC;
  padding-bottom: 0;
}

.subcat-tab {
  padding: 10px 20px;
  font-size: 14px;
  color: #6B7280;
  background: none;
  border: none;
  cursor: pointer;
  position: relative;
  bottom: -2px;
  transition: color 0.2s;
  font-weight: 500;
}

.subcat-tab:hover {
  color: #3B6FE0;
}

.subcat-tab.active {
  color: #3B6FE0;
  border-bottom: 2px solid #3B6FE0;
  font-weight: 600;
}

.filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.filter-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.filter-dropdown {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border: 1px solid #E2E6EC;
  border-radius: 8px;
  font-size: 13px;
  color: #374151;
  cursor: pointer;
  background-color: white;
  transition: border-color 0.2s;
}

.filter-dropdown:hover {
  border-color: #3B6FE0;
}

.filter-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.result-count {
  font-size: 13px;
  color: #6B7280;
}

.view-toggle {
  display: flex;
  border: 1px solid #E2E6EC;
  border-radius: 8px;
  overflow: hidden;
  background-color: white;
}

.view-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: none;
  cursor: pointer;
  color: #9CA3AF;
  transition: all 0.2s;
}

.view-btn:hover {
  background-color: #F3F4F8;
  color: #6B7280;
}

.view-btn.active {
  background-color: #3B6FE0;
  color: white;
}

.empty-state {
  text-align: center;
  padding: 80px 0;
  color: #9CA3AF;
}

.empty-state p {
  margin-top: 12px;
  font-size: 14px;
}

.spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.doc-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.doc-card {
  display: flex;
  gap: 16px;
  padding: 16px 20px;
  background-color: white;
  border: 1px solid #E2E6EC;
  border-radius: 16px;
  cursor: pointer;
  transition: all 0.2s;
}

.doc-card:hover {
  border-color: #B4C7F0;
  box-shadow: 0 2px 12px rgba(59, 111, 224, 0.08);
  transform: translateY(-1px);
}

.doc-icon {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.doc-info {
  flex: 1;
  min-width: 0;
}

.doc-title {
  font-size: 15px;
  font-weight: 600;
  color: #111827;
  margin: 0 0 6px 0;
  line-height: 1.4;
}

.doc-card:hover .doc-title {
  color: #3B6FE0;
}

.doc-summary {
  font-size: 13px;
  color: #6B7280;
  margin: 0 0 12px 0;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.doc-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  color: #9CA3AF;
}

.doc-tag {
  padding: 2px 8px;
  border-radius: 4px;
  background-color: #F3F4F8;
  color: #6B7280;
  font-size: 11px;
  flex-shrink: 0;
}

.progress-bar {
  flex: 1;
  height: 6px;
  background-color: #E2E6EC;
  border-radius: 3px;
  overflow: hidden;
  min-width: 60px;
}

.progress-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 0.3s;
}

.progress-text {
  font-size: 12px;
  color: #6B7280;
  font-weight: 500;
  flex-shrink: 0;
}

.doc-time {
  color: #9CA3AF;
  flex-shrink: 0;
}

.favorite-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: none;
  cursor: pointer;
  color: #D1D5DB;
  border-radius: 6px;
  transition: all 0.2s;
  flex-shrink: 0;
}

.favorite-btn:hover {
  background-color: #FEF3C7;
  color: #F59E0B;
}

.favorite-btn.favorited {
  color: #F59E0B;
}

.more-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: none;
  cursor: pointer;
  color: #D1D5DB;
  border-radius: 6px;
  transition: all 0.2s;
  flex-shrink: 0;
}

.more-btn:hover {
  background-color: #F3F4F8;
  color: #6B7280;
}

.animate-fade-in {
  animation: fadeIn 0.5s ease-out;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.mobile-cat-toggle {
  display: none;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: 8px;
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  font-size: 13px;
  color: var(--kb-foreground);
  cursor: pointer;
  margin-bottom: 16px;
}

.mobile-overlay {
  display: none;
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  z-index: 40;
}

.mobile-sidebar {
  display: none;
  position: fixed;
  top: 0;
  left: 0;
  bottom: 0;
  width: 260px;
  z-index: 50;
  transform: translateX(-100%);
  transition: transform 0.3s ease;
  overflow-y: auto;
}

.mobile-sidebar.open {
  transform: translateX(0);
}

.mobile-sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border-bottom: 1px solid var(--kb-border);
}

.mobile-sidebar-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--kb-foreground);
}

.mobile-sidebar-close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 6px;
  color: var(--kb-muted-foreground);
  cursor: pointer;
  background: transparent;
  border: none;
}

.mobile-sidebar-close:hover {
  background: var(--kb-muted);
}

.mobile-sidebar-inner {
  position: static;
}

@media (max-width: 768px) {
  .sidebar:not(.mobile-sidebar) {
    display: none;
  }

  .mobile-cat-toggle {
    display: inline-flex;
  }

  .mobile-overlay {
    display: block;
  }

  .mobile-sidebar {
    display: block;
  }

  .main-content {
    padding: 16px !important;
  }
}
</style>
