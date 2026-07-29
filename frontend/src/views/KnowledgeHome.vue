<template>
  <div class="knowledge-layout">
    <!-- 左侧边栏 -->
    <KnowledgeSidebar
      ref="sidebarRef"
      mode="home"
      :active-key="activeKey"
      :active-category-id="activeCategoryId"
      @category-click="onCategoryClick"
      @all-click="onAllClick"
    />

    <!-- 主内容区 -->
    <main class="knowledge-main">
      <!-- 顶部搜索栏 -->
      <div class="top-bar">
        <div class="search-box">
          <Icon name="search" :size="18" class="search-icon" />
          <input
            v-model="searchKeyword"
            type="text"
            placeholder="搜索知识文档..."
            class="search-input"
            @keyup.enter="goSearch"
          />
          <span class="search-shortcut">⌘K</span>
        </div>
        <button class="upload-btn" @click="goUpload">
          <Icon name="upload" :size="16" />
          <span>上传文档</span>
        </button>
      </div>

      <!-- ============ 视图 1: 全部文档 (首页) ============ -->
      <template v-if="activeKey === 'all' && !activeCategoryId">
        <!-- 问候语 + 引言 -->
        <section class="greeting-section">
          <h1 class="greeting-title">{{ greetingText }}</h1>
          <div class="quote-card">
            <Icon name="quote" :size="20" class="quote-icon" />
            <div class="quote-content">
              <p class="quote-text">学习不是为了知道更多，而是为了理解更深。</p>
              <p class="quote-author">— 理查德·费曼</p>
            </div>
          </div>
        </section>

        <!-- 快速访问 -->
        <section class="section">
          <h2 class="section-title">快速访问</h2>
          <div class="quick-access-grid">
            <div
              v-for="(cat, idx) in topCategories"
              :key="cat.id"
              class="quick-card"
              @click="onCategoryClick(cat)"
            >
              <div class="quick-icon" :style="{ background: `${kbColors[idx % kbColors.length]}1A` }">
                <Icon :name="getCategoryIcon(cat.icon)" :size="24" :style="{ color: kbColors[idx % kbColors.length] }" />
              </div>
              <div class="quick-info">
                <h3 class="quick-name">{{ cat.name }}</h3>
                <p class="quick-count">{{ cat.docCount || 0 }} 篇文档</p>
              </div>
            </div>
          </div>
        </section>

        <!-- 学习概览 -->
        <section class="section">
          <h2 class="section-title">学习概览</h2>
          <div class="stats-grid">
            <div class="stat-card">
              <div class="stat-icon stat-blue">
                <Icon name="clock" :size="20" />
              </div>
              <div class="stat-info">
                <span class="stat-value">12.5h</span>
                <span class="stat-label">本周学习时长</span>
              </div>
            </div>
            <div class="stat-card">
              <div class="stat-icon stat-green">
                <Icon name="file-text" :size="20" />
              </div>
              <div class="stat-info">
                <span class="stat-value">48 篇</span>
                <span class="stat-label">已阅读文档</span>
              </div>
            </div>
            <div class="stat-card">
              <div class="stat-icon stat-orange">
                <Icon name="flame" :size="20" />
              </div>
              <div class="stat-info">
                <span class="stat-value">7 天</span>
                <span class="stat-label">连续学习</span>
              </div>
            </div>
            <div class="stat-card">
              <div class="stat-icon stat-purple">
                <Icon name="star" :size="20" />
              </div>
              <div class="stat-info">
                <span class="stat-value">23 个</span>
                <span class="stat-label">知识收藏</span>
              </div>
            </div>
          </div>
        </section>

        <!-- 最近浏览 -->
        <section class="section">
          <div class="section-header">
            <h2 class="section-title">最近浏览</h2>
            <router-link to="/docs" class="view-all-link">查看全部 <Icon name="arrow-right" :size="14" /></router-link>
          </div>
          <div v-if="latestDocs.length > 0" class="recent-grid">
            <div
              v-for="(doc, idx) in latestDocs"
              :key="doc.id"
              class="recent-card"
              @click="goToDoc(doc.id)"
            >
              <div class="recent-icon" :style="{ background: `${docIconColors[idx % docIconColors.length]}14` }">
                <Icon name="file-text" :size="18" :style="{ color: docIconColors[idx % docIconColors.length] }" />
              </div>
              <div class="recent-info">
                <h4 class="recent-title">{{ doc.title }}</h4>
                <p class="recent-meta">
                  <span class="recent-tag" :style="{ background: `${docIconColors[idx % docIconColors.length]}14`, color: docIconColors[idx % docIconColors.length] }">{{ doc.categoryName || '未分类' }}</span>
                  <span class="recent-time">{{ formatRelativeTime(doc.createTime) }}</span>
                </p>
              </div>
            </div>
          </div>
          <p v-else class="empty-hint">暂无最近浏览的文档</p>
        </section>
      </template>

      <!-- ============ 视图 2: 分类下有子分类 → 显示子分类卡片 ============ -->
      <template v-else-if="currentCategory && hasChildren(currentCategory)">
        <section class="category-view">
          <!-- 面包屑 -->
          <div class="category-header">
            <div class="breadcrumb">
              <span class="breadcrumb-link" @click="onAllClick">全部文档</span>
              <Icon name="chevron-right" :size="12" class="sep" />
              <template v-if="currentParentCategory">
                <span class="breadcrumb-link" @click="onCategoryClick(currentParentCategory)">{{ currentParentCategory.name }}</span>
                <Icon name="chevron-right" :size="12" class="sep" />
              </template>
              <span class="breadcrumb-current">{{ currentCategory.name }}</span>
            </div>
            <div class="header-meta">
              <span class="meta-pill">
                <Icon name="folder" :size="12" />
                {{ currentCategory.children?.length || 0 }} 个子分类
              </span>
              <span class="meta-pill" v-if="currentCategory.docCount">
                <Icon name="file-text" :size="12" />
                {{ currentCategory.docCount }} 篇文档
              </span>
            </div>
          </div>

          <!-- 分类图标标题区 -->
          <div class="category-hero" :style="{ background: `${getCategoryColor(currentCategory)}10`, borderColor: `${getCategoryColor(currentCategory)}20` }">
            <div class="hero-icon" :style="{ background: `${getCategoryColor(currentCategory)}20`, color: getCategoryColor(currentCategory) }">
              <Icon :name="getCategoryIcon(currentCategory.icon)" :size="28" />
            </div>
            <div class="hero-info">
              <h1 class="hero-title">{{ currentCategory.name }}</h1>
              <p class="hero-desc">{{ currentCategory.description || `浏览 ${currentCategory.name} 下的子分类内容` }}</p>
            </div>
          </div>

          <!-- 子分类网格 -->
          <div class="sub-category-section">
            <h2 class="section-title">子分类</h2>
            <div class="sub-category-grid">
              <div
                v-for="(sub, idx) in currentCategory.children"
                :key="sub.id"
                class="sub-category-card"
                @click="onCategoryClick(sub)"
              >
                <div class="sub-icon" :style="{ background: `${subColors[idx % subColors.length]}14` }">
                  <Icon :name="getCategoryIcon(sub.icon)" :size="22" :style="{ color: subColors[idx % subColors.length] }" />
                </div>
                <div class="sub-info">
                  <h3 class="sub-name">{{ sub.name }}</h3>
                  <p class="sub-meta">
                    <span class="sub-count">{{ sub.docCount || 0 }} 篇文档</span>
                    <template v-if="(sub.children?.length ?? 0) > 0">
                      <span class="sub-divider">·</span>
                      <span class="sub-sub">{{ sub.children?.length }} 个子分类</span>
                    </template>
                  </p>
                  <p class="sub-desc">{{ sub.description || getSubDescription(sub.name) }}</p>
                </div>
                <Icon name="arrow-right" :size="16" class="sub-arrow" />
              </div>
            </div>
          </div>

          <!-- 该分类下的热门文档 -->
          <div v-if="categoryDocs.length > 0" class="docs-section">
            <div class="section-header">
              <h2 class="section-title">{{ currentCategory.name }} 下的文档</h2>
              <router-link to="/docs" class="view-all-link">查看全部 <Icon name="arrow-right" :size="14" /></router-link>
            </div>
            <div class="docs-list">
              <div
                v-for="(doc, idx) in categoryDocs.slice(0, 6)"
                :key="doc.id"
                class="doc-row"
                @click="goToDoc(doc.id)"
              >
                <div class="doc-row-icon" :style="{ background: `${docIconColors[idx % docIconColors.length]}14`, color: docIconColors[idx % docIconColors.length] }">
                  <Icon name="file-text" :size="16" />
                </div>
                <div class="doc-row-info">
                  <h4 class="doc-row-title">{{ doc.title }}</h4>
                  <p class="doc-row-meta">
                    <span class="doc-row-tag" :style="{ background: `${docIconColors[idx % docIconColors.length]}14`, color: docIconColors[idx % docIconColors.length] }">{{ doc.categoryName || currentCategory.name }}</span>
                    <span class="doc-row-time">{{ formatRelativeTime(doc.createTime) }}</span>
                  </p>
                </div>
                <Icon name="arrow-right" :size="14" class="doc-row-arrow" />
              </div>
            </div>
          </div>
        </section>
      </template>

      <!-- ============ 视图 3: 叶子分类 → 显示文章列表 ============ -->
      <template v-else-if="currentCategory">
        <section class="category-view">
          <!-- 面包屑 -->
          <div class="category-header">
            <div class="breadcrumb">
              <span class="breadcrumb-link" @click="onAllClick">全部文档</span>
              <Icon name="chevron-right" :size="12" class="sep" />
              <template v-if="currentParentCategory">
                <span class="breadcrumb-link" @click="onCategoryClick(currentParentCategory)">{{ currentParentCategory.name }}</span>
                <Icon name="chevron-right" :size="12" class="sep" />
              </template>
              <span class="breadcrumb-current">{{ currentCategory.name }}</span>
            </div>
            <div class="header-meta">
              <span class="meta-pill">
                <Icon name="file-text" :size="12" />
                {{ currentCategory.docCount || categoryDocs.length }} 篇文档
              </span>
            </div>
          </div>

          <!-- 分类标题 + 操作 -->
          <div class="leaf-header" :style="{ background: `${getCategoryColor(currentCategory)}08`, borderColor: `${getCategoryColor(currentCategory)}18` }">
            <div class="leaf-info">
              <div class="leaf-icon" :style="{ background: `${getCategoryColor(currentCategory)}20`, color: getCategoryColor(currentCategory) }">
                <Icon :name="getCategoryIcon(currentCategory.icon)" :size="22" />
              </div>
              <div>
                <h1 class="leaf-title">{{ currentCategory.name }}</h1>
                <p class="leaf-desc">{{ currentCategory.description || `浏览 ${currentCategory.name} 分类下的所有文档` }}</p>
              </div>
            </div>
            <button class="upload-btn small" @click="goUpload">
              <Icon name="upload" :size="14" />
              <span>上传文档</span>
            </button>
          </div>

          <!-- 加载中 -->
          <div v-if="loading" class="docs-loading">
            <div v-for="i in 5" :key="i" class="loading-row">
              <div class="loading-icon"></div>
              <div class="loading-lines">
                <div class="loading-line w-3/4"></div>
                <div class="loading-line w-1/2"></div>
              </div>
            </div>
          </div>

          <!-- 空态 -->
          <div v-else-if="categoryDocs.length === 0" class="docs-empty">
            <Icon name="file-question" :size="40" class="empty-icon" />
            <h3>该分类下暂无文档</h3>
            <p>点击右上角「上传文档」添加内容</p>
          </div>

          <!-- 文档列表 -->
          <div v-else class="docs-list">
            <div
              v-for="(doc, idx) in sortedCategoryDocs"
              :key="doc.id"
              class="doc-row"
              @click="goToDoc(doc.id)"
            >
              <div class="doc-row-icon" :style="{ background: `${docIconColors[idx % docIconColors.length]}14`, color: docIconColors[idx % docIconColors.length] }">
                <Icon name="file-text" :size="16" />
              </div>
              <div class="doc-row-info">
                <h4 class="doc-row-title">{{ doc.title }}</h4>
                <p class="doc-row-summary">{{ doc.summary || '暂无摘要...' }}</p>
                <p class="doc-row-meta">
                  <span class="doc-row-tag" :style="{ background: `${docIconColors[idx % docIconColors.length]}14`, color: docIconColors[idx % docIconColors.length] }">{{ doc.categoryName || currentCategory.name }}</span>
                  <span class="doc-row-time">{{ formatRelativeTime(doc.createTime) }}</span>
                  <span class="doc-row-views"><Icon name="eye" :size="11" /> {{ doc.viewCount || 0 }}</span>
                </p>
              </div>
              <Icon name="arrow-right" :size="14" class="doc-row-arrow" />
            </div>
          </div>
        </section>
      </template>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import KnowledgeSidebar from '@/components/layout/KnowledgeSidebar.vue'
import { categoriesApi, docsApi } from '@/api'
import type { CategoryVO, DocVO } from '@/api/types'

const router = useRouter()
const route = useRoute()
const sidebarRef = ref<InstanceType<typeof KnowledgeSidebar> | null>(null)

const searchKeyword = ref('')
const allCategories = ref<CategoryVO[]>([])
const topCategories = ref<CategoryVO[]>([])
const latestDocs = ref<DocVO[]>([])
const categoryDocs = ref<DocVO[]>([])
const loading = ref(false)

const activeKey = ref<'all'>('all')
const activeCategoryId = ref<number | null>(null)

const kbColors = ['#3B6FE0', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6', '#6B7280']
const docIconColors = ['#EF4444', '#3B6FE0', '#8B5CF6', '#10B981', '#F59E0B', '#6B7280']
const subColors = ['#3B6FE0', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6', '#06B6D4', '#F97316', '#EC4899']

const greetingText = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '凌晨好，注意休息'
  if (hour < 12) return '早上好，开始今天的学习吧'
  if (hour < 18) return '下午好，开始今天的探索吧'
  return '晚上好，开始今天的学习吧'
})

// 当前选中的分类
const currentCategory = computed<CategoryVO | null>(() => {
  if (!activeCategoryId.value) return null
  return findCategoryById(allCategories.value, activeCategoryId.value)
})

// 当前分类的父级（用于面包屑）
const currentParentCategory = computed<CategoryVO | null>(() => {
  if (!activeCategoryId.value) return null
  return findParentCategory(allCategories.value, activeCategoryId.value)
})

const sortedCategoryDocs = computed(() => {
  return [...categoryDocs.value].sort((a, b) =>
    new Date(b.createTime || '').getTime() - new Date(a.createTime || '').getTime()
  )
})

function findCategoryById(cats: CategoryVO[], id: number): CategoryVO | null {
  for (const cat of cats) {
    if (cat.id === id) return cat
    if (cat.children && cat.children.length > 0) {
      const found = findCategoryById(cat.children, id)
      if (found) return found
    }
  }
  return null
}

function findParentCategory(cats: CategoryVO[], id: number, parent: CategoryVO | null = null): CategoryVO | null {
  for (const cat of cats) {
    if (cat.id === id) return parent
    if (cat.children && cat.children.length > 0) {
      const found = findParentCategory(cat.children, id, cat)
      if (found !== null || cat.children.some(c => c.id === id)) {
        if (cat.children.some(c => c.id === id)) return cat
        if (found) return found
      }
    }
  }
  return null
}

function hasChildren(cat: CategoryVO): boolean {
  return (cat.children?.length ?? 0) > 0
}

const validIcons = [
  'code', 'server', 'database', 'brain', 'layout', 'palette',
  'container', 'shield', 'binary', 'book-open', 'cpu', 'bot',
  'target', 'bar-chart-2', 'briefcase', 'git-branch', 'layers',
  'file-code', 'router', 'monitor', 'wifi', 'lock', 'smartphone',
]

const iconColorMap: Record<string, string> = {
  '人工智能': '#8B5CF6', '前端开发': '#3B6FE0', '后端技术': '#10B981',
  '数据科学': '#F59E0B', '移动开发': '#EF4444', '数据库': '#06B6D4',
  'DevOps': '#F97316', '网络安全': '#6366F1', '算法与数据结构': '#64748B',
  '产品设计': '#EC4899', '编程语言': '#14B8A6', '项目管理': '#8B5CF6',
}

const nameIconMap: Record<string, string> = {
  '人工智能': 'brain', '前端开发': 'layout', '后端技术': 'server',
  '数据科学': 'bar-chart-2', '移动开发': 'smartphone', '数据库': 'database',
  'DevOps': 'container', '网络安全': 'shield', '算法与数据结构': 'binary',
  '产品设计': 'palette', '编程语言': 'file-code', '项目管理': 'briefcase',
}

function getCategoryIcon(icon?: string): string {
  if (icon && validIcons.includes(icon)) return icon
  return 'folder'
}

function getCategoryColor(cat: CategoryVO | null): string {
  if (!cat) return '#3B6FE0'
  if (cat.icon && iconColorMap[cat.icon]) return iconColorMap[cat.icon]
  if (nameIconMap[cat.name]) return iconColorMap[cat.name] || '#3B6FE0'
  return '#3B6FE0'
}

function getSubDescription(name: string): string {
  const map: Record<string, string> = {
    '机器学习': 'ML 基础、模型训练与评估',
    '深度学习': '神经网络、CNN、RNN、Transformer',
    'NLP': '自然语言处理、文本分析、对话系统',
    '计算机视觉': '图像识别、目标检测、图像分割',
    '强化学习': 'RL 算法、策略优化、奖励机制',
  }
  return map[name] || `${name}相关内容`
}

async function loadCategoryDocs(categoryId: number) {
  loading.value = true
  try {
    const ids = getAllChildIds(categoryId)
    let merged: DocVO[] = []
    for (const id of ids) {
      const res = await docsApi.list({ categoryId: id, pageSize: 50 } as any)
      merged = merged.concat(res.records || [])
    }
    // 去重
    const seen = new Set<number>()
    categoryDocs.value = merged.filter(d => {
      if (seen.has(d.id)) return false
      seen.add(d.id)
      return true
    })
  } catch {
    categoryDocs.value = []
  } finally {
    loading.value = false
  }
}

function getAllChildIds(id: number): number[] {
  const result: number[] = [id]
  const cat = findCategoryById(allCategories.value, id)
  if (cat?.children) {
    for (const child of cat.children) {
      result.push(...getAllChildIds(child.id))
    }
  }
  return result
}

function onCategoryClick(cat: CategoryVO) {
  activeCategoryId.value = cat.id
  activeKey.value = 'all'
  loadCategoryDocs(cat.id)
  // 同步路由
  router.replace({ path: '/knowledge', query: { categoryId: String(cat.id) } })
}

function onAllClick() {
  activeCategoryId.value = null
  activeKey.value = 'all'
  categoryDocs.value = []
  router.replace({ path: '/knowledge' })
}

const goSearch = () => {
  router.push({ path: '/search', query: { q: searchKeyword.value } })
}

const goToDoc = (id: number) => {
  router.push(`/doc/${id}`)
}

const goUpload = () => {
  router.push('/admin/upload')
}

const formatRelativeTime = (dateStr?: string) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  const now = new Date()
  const diff = (now.getTime() - date.getTime()) / 1000
  if (diff < 3600) return `${Math.max(1, Math.floor(diff / 60))} 分钟前`
  if (diff < 86400) return `${Math.floor(diff / 3600)} 小时前`
  if (diff < 172800) return '昨天'
  if (diff < 604800) return `${Math.floor(diff / 86400)} 天前`
  return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

const loadData = async () => {
  try {
    const [cats, recent] = await Promise.all([
      categoriesApi.tree(),
      docsApi.list({ pageSize: 6, sortBy: 'new' } as any).then((r) => r.records || []),
    ])
    allCategories.value = cats
    topCategories.value = cats.slice(0, 6)
    latestDocs.value = recent.slice(0, 4)

    // 如果 URL 有 categoryId，选中对应分类
    const catId = route.query.categoryId
    if (catId && typeof catId === 'string') {
      const id = Number(catId)
      activeCategoryId.value = id
      loadCategoryDocs(id)
    }
  } catch {
    topCategories.value = []
    latestDocs.value = []
    allCategories.value = []
  }
}

watch(
  () => route.query.categoryId,
  (newId) => {
    if (newId && typeof newId === 'string') {
      const id = Number(newId)
      if (activeCategoryId.value !== id) {
        activeCategoryId.value = id
        loadCategoryDocs(id)
      }
    }
  }
)

onMounted(loadData)
</script>

<style scoped>
/* 全屏布局 */
.knowledge-layout {
  display: flex;
  margin: -24px -24px 0;
  min-height: calc(100vh - 56px);
}

.knowledge-main {
  flex: 1;
  min-width: 0;
  padding: 24px 32px 40px;
  overflow-y: auto;
  height: calc(100vh - 56px);
}

/* 顶部搜索栏 */
.top-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 28px;
}

.search-box {
  position: relative;
  flex: 1;
  max-width: 560px;
}

.search-icon {
  position: absolute;
  left: 16px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--kb-muted-foreground);
}

.search-input {
  width: 100%;
  height: 44px;
  padding-left: 44px;
  padding-right: 64px;
  border-radius: 10px;
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  font-size: 14px;
  color: var(--kb-foreground);
  outline: none;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.search-input:focus {
  border-color: var(--kb-primary);
  box-shadow: 0 0 0 3px rgba(59, 111, 224, 0.12);
}

.search-shortcut {
  position: absolute;
  right: 14px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 4px;
  background: var(--kb-muted);
  color: var(--kb-muted-foreground);
  font-weight: 500;
}

.upload-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 44px;
  padding: 0 18px;
  border-radius: 10px;
  border: none;
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.15s;
  white-space: nowrap;
}

.upload-btn:hover {
  opacity: 0.9;
}

.upload-btn.small {
  height: 36px;
  padding: 0 14px;
  font-size: 13px;
}

/* 问候语 */
.greeting-section {
  margin-bottom: 36px;
}

.greeting-title {
  font-size: 28px;
  font-weight: 700;
  color: var(--kb-foreground);
  margin-bottom: 16px;
}

.quote-card {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  padding: 20px 24px;
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(59, 111, 224, 0.04), rgba(139, 92, 246, 0.04));
  border: 1px solid rgba(59, 111, 224, 0.1);
}

.quote-icon {
  color: var(--kb-primary);
  flex-shrink: 0;
  margin-top: 2px;
}

.quote-text {
  font-size: 15px;
  font-weight: 500;
  color: var(--kb-foreground);
  margin-bottom: 6px;
}

.quote-author {
  font-size: 13px;
  color: var(--kb-muted-foreground);
}

/* 通用区块 */
.section {
  margin-bottom: 32px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--kb-foreground);
  margin-bottom: 16px;
}

.section-header .section-title {
  margin-bottom: 0;
}

.view-all-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  font-weight: 500;
  color: var(--kb-primary);
  text-decoration: none;
}

.view-all-link:hover {
  opacity: 0.8;
}

/* 快速访问 */
.quick-access-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 16px;
}

.quick-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
  padding: 18px 16px;
  border-radius: 12px;
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s, transform 0.15s;
}

.quick-card:hover {
  border-color: var(--kb-primary);
  box-shadow: 0 4px 16px rgba(59, 111, 224, 0.08);
  transform: translateY(-1px);
}

.quick-icon {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.quick-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--kb-foreground);
}

.quick-count {
  font-size: 12px;
  color: var(--kb-muted-foreground);
}

/* 学习概览统计 */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  border-radius: 12px;
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
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

.stat-blue { background: rgba(59, 111, 224, 0.1); color: #3B6FE0; }
.stat-green { background: rgba(16, 185, 129, 0.1); color: #10B981; }
.stat-orange { background: rgba(245, 158, 11, 0.1); color: #F59E0B; }
.stat-purple { background: rgba(139, 92, 246, 0.1); color: #8B5CF6; }

.stat-info {
  display: flex;
  flex-direction: column;
}

.stat-value {
  font-size: 22px;
  font-weight: 700;
  color: var(--kb-foreground);
  line-height: 1.2;
}

.stat-label {
  font-size: 13px;
  color: var(--kb-muted-foreground);
  margin-top: 2px;
}

/* 最近浏览 */
.recent-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
}

.recent-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border-radius: 10px;
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.recent-card:hover {
  border-color: var(--kb-primary);
  box-shadow: 0 2px 8px rgba(59, 111, 224, 0.06);
}

.recent-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.recent-info {
  flex: 1;
  min-width: 0;
}

.recent-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--kb-foreground);
  margin-bottom: 6px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.recent-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

.recent-tag {
  padding: 2px 6px;
  border-radius: 4px;
  font-weight: 500;
}

.recent-time {
  color: var(--kb-muted-foreground);
}

.empty-hint {
  padding: 40px 0;
  text-align: center;
  font-size: 14px;
  color: var(--kb-muted-foreground);
}

/* ============ 分类视图 ============ */
.category-view {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* 面包屑 */
.category-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.breadcrumb-link {
  color: var(--kb-muted-foreground);
  cursor: pointer;
  transition: color 0.15s;
}

.breadcrumb-link:hover {
  color: var(--kb-primary);
}

.breadcrumb-current {
  color: var(--kb-foreground);
  font-weight: 600;
}

.breadcrumb .sep {
  color: var(--kb-muted-foreground);
  opacity: 0.5;
}

.header-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.meta-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
  background: var(--kb-muted);
  color: var(--kb-muted-foreground);
}

/* 分类 hero 区 */
.category-hero {
  display: flex;
  gap: 20px;
  align-items: center;
  padding: 24px 28px;
  border-radius: 14px;
  border: 1px solid;
}

.hero-icon {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.hero-info {
  flex: 1;
  min-width: 0;
}

.hero-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--kb-foreground);
  margin: 0 0 6px;
}

.hero-desc {
  font-size: 14px;
  color: var(--kb-muted-foreground);
  margin: 0;
}

/* 子分类网格 */
.sub-category-section {
  margin-bottom: 8px;
}

.sub-category-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 14px;
}

.sub-category-card {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px 18px;
  border-radius: 12px;
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s, transform 0.15s;
}

.sub-category-card:hover {
  border-color: var(--kb-primary);
  box-shadow: 0 4px 14px rgba(59, 111, 224, 0.08);
  transform: translateY(-1px);
}

.sub-icon {
  width: 42px;
  height: 42px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.sub-info {
  flex: 1;
  min-width: 0;
}

.sub-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--kb-foreground);
  margin: 0 0 4px;
}

.sub-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--kb-muted-foreground);
  margin: 0 0 4px;
}

.sub-divider {
  opacity: 0.5;
}

.sub-sub {
  color: var(--kb-muted-foreground);
}

.sub-desc {
  font-size: 12px;
  color: var(--kb-muted-foreground);
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sub-arrow {
  color: var(--kb-muted-foreground);
  opacity: 0;
  transition: opacity 0.15s, color 0.15s;
  flex-shrink: 0;
  margin-top: 4px;
}

.sub-category-card:hover .sub-arrow {
  opacity: 1;
  color: var(--kb-primary);
}

/* 文档列表 */
.docs-section {
  margin-top: 4px;
}

.docs-list {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--kb-border);
  border-radius: 12px;
  overflow: hidden;
  background: var(--kb-card);
}

.doc-row {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 14px 18px;
  border-bottom: 1px solid var(--kb-border);
  cursor: pointer;
  transition: background 0.12s;
}

.doc-row:last-child {
  border-bottom: none;
}

.doc-row:hover {
  background: rgba(59, 111, 224, 0.03);
}

.doc-row-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.doc-row-info {
  flex: 1;
  min-width: 0;
}

.doc-row-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--kb-foreground);
  margin: 0 0 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.doc-row-summary {
  font-size: 13px;
  color: var(--kb-muted-foreground);
  margin: 0 0 6px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.doc-row-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  margin: 0;
}

.doc-row-tag {
  padding: 1px 6px;
  border-radius: 4px;
  font-weight: 500;
}

.doc-row-time {
  color: var(--kb-muted-foreground);
}

.doc-row-views {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  color: var(--kb-muted-foreground);
}

.doc-row-arrow {
  color: var(--kb-muted-foreground);
  opacity: 0;
  transition: opacity 0.15s, color 0.15s;
  flex-shrink: 0;
  margin-top: 6px;
}

.doc-row:hover .doc-row-arrow {
  opacity: 1;
  color: var(--kb-primary);
}

/* 叶子分类头 */
.leaf-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 22px 28px;
  border-radius: 14px;
  border: 1px solid;
  flex-wrap: wrap;
}

.leaf-info {
  display: flex;
  align-items: center;
  gap: 16px;
}

.leaf-icon {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.leaf-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--kb-foreground);
  margin: 0 0 4px;
}

.leaf-desc {
  font-size: 13px;
  color: var(--kb-muted-foreground);
  margin: 0;
}

/* 加载/空态 */
.docs-loading,
.docs-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 60px 20px;
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: 12px;
  text-align: center;
}

.docs-empty h3 {
  font-size: 15px;
  font-weight: 600;
  color: var(--kb-foreground);
  margin: 0;
}

.docs-empty p {
  font-size: 13px;
  color: var(--kb-muted-foreground);
  margin: 0;
}

.empty-icon {
  color: var(--kb-muted-foreground);
  opacity: 0.6;
}

.loading-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 18px;
  border-bottom: 1px solid var(--kb-border);
  background: var(--kb-card);
}

.loading-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: var(--kb-muted);
  animation: pulse 1.5s ease-in-out infinite;
  flex-shrink: 0;
}

.loading-lines {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.loading-line {
  height: 12px;
  border-radius: 4px;
  background: var(--kb-muted);
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

@media (max-width: 768px) {
  .knowledge-layout {
    margin: -16px -16px 0;
  }
  .knowledge-main {
    padding: 16px;
  }
  .top-bar {
    flex-direction: column;
    align-items: stretch;
  }
  .upload-btn {
    height: 40px;
    justify-content: center;
  }
  .greeting-title {
    font-size: 22px;
  }
  .quick-access-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .sub-category-grid {
    grid-template-columns: 1fr;
  }
  .leaf-header,
  .category-hero {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
