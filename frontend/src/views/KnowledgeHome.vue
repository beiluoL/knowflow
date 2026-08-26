<template>
  <div class="knowledge-layout">
    <!-- 移动端遮罩层 -->
    <div
      v-if="sidebarOpen"
      class="sidebar-overlay"
      @click="sidebarOpen = false"
    ></div>

    <!-- 左侧边栏 -->
    <KnowledgeSidebar
      ref="sidebarRef"
      mode="home"
      :active-key="activeKey"
      :active-category-id="activeCategoryId"
      :mobile-open="sidebarOpen"
      @category-click="onCategoryClick"
      @all-click="onAllClick"
      @close-mobile="sidebarOpen = false"
    />

    <!-- 主内容区 -->
    <main class="knowledge-main">
      <!-- 顶部搜索栏 -->
      <div class="top-bar">
        <!-- 移动端菜单按钮 -->
        <button class="mobile-menu-btn" @click="sidebarOpen = true">
          <Icon name="menu" :size="20" />
        </button>
        <div class="search-box">
          <Icon name="search" :size="18" class="search-icon" />
          <input
            ref="searchInputRef"
            v-model="searchKeyword"
            type="text"
            placeholder="搜索知识文档..."
            class="search-input"
            @keyup.enter="goSearch"
            @focus="showSearchHistory = true"
            @blur="hideSearchHistory"
          />
          <span class="search-shortcut">⌘K</span>
          <!-- 搜索历史下拉 -->
          <div
            v-if="showSearchHistory && searchHistory.length > 0 && !searchKeyword"
            class="search-history-dropdown"
          >
            <div class="history-header">
              <span class="history-title">搜索历史</span>
              <button class="history-clear" @click="clearSearchHistory">清除</button>
            </div>
            <div class="history-items">
              <button
                v-for="item in searchHistory"
                :key="item"
                class="history-item"
                @click="searchFromHistory(item)"
              >
                <Icon name="clock" :size="12" class="history-icon" />
                <span>{{ item }}</span>
              </button>
            </div>
          </div>
        </div>
        <button class="upload-btn" @click="goUpload">
          <Icon name="upload" :size="16" />
          <span>上传文档</span>
        </button>
        <button class="import-btn" @click="goImport">
          <Icon name="download" :size="16" />
          <span>导入知识库</span>
        </button>
        <button class="import-btn" @click="goReader">
          <Icon name="book-open" :size="16" />
          <span>本地阅读</span>
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
              role="button"
              tabindex="0"
              @click="onCategoryClick(cat)"
              @keydown.enter.prevent="($event.target as HTMLElement).click()"
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
            <StatCard
              icon="clock"
              icon-color="var(--kb-primary)"
              icon-bg="rgba(59, 111, 224, 0.1)"
              :value="userStats?.totalStudyHours != null ? userStats.totalStudyHours + 'h' : '—'"
              label="本周学习时长"
              layout="horizontal"
            />
            <StatCard
              icon="file-text"
              icon-color="var(--kb-accent)"
              icon-bg="rgba(16, 185, 129, 0.1)"
              :value="userStats?.readDocsCount != null ? userStats.readDocsCount + ' 篇' : '—'"
              label="已阅读文档"
              layout="horizontal"
            />
            <StatCard
              icon="flame"
              icon-color="var(--kb-warning)"
              icon-bg="rgba(245, 158, 11, 0.1)"
              :value="userStats?.streakDays != null ? userStats.streakDays + ' 天' : '—'"
              label="连续学习"
              layout="horizontal"
            />
            <StatCard
              icon="star"
              icon-color="#8B5CF6"
              icon-bg="rgba(139, 92, 246, 0.1)"
              :value="userStats?.favoriteCount != null ? userStats.favoriteCount + ' 个' : '—'"
              label="知识收藏"
              layout="horizontal"
            />
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
              role="button"
              tabindex="0"
              @click="goToDoc(doc.id)"
              @keydown.enter.prevent="($event.target as HTMLElement).click()"
            >
              <DocTypeBadge :file-url="doc.fileUrl" :content="doc.content" :size="36" />
              <div class="recent-info">
                <h4 class="recent-title" :title="doc.title">{{ doc.title }}</h4>
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
              <button type="button" class="breadcrumb-link" @click="onAllClick">全部文档</button>
              <Icon name="chevron-right" :size="12" class="sep" />
              <template v-if="currentParentCategory">
                <button type="button" class="breadcrumb-link" @click="onCategoryClick(currentParentCategory)">{{ currentParentCategory.name }}</button>
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
              <Icon :name="getCategoryIcon(currentCategory.icon)" :size="24" />
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
                role="button"
                tabindex="0"
                @click="onCategoryClick(sub)"
                @keydown.enter.prevent="($event.target as HTMLElement).click()"
              >
                <div class="sub-icon" :style="{ background: `${subColors[idx % subColors.length]}14` }">
                  <Icon :name="getCategoryIcon(sub.icon)" :size="24" :style="{ color: subColors[idx % subColors.length] }" />
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
                role="button"
                tabindex="0"
                @click="goToDoc(doc.id)"
                @keydown.enter.prevent="($event.target as HTMLElement).click()"
              >
                <DocTypeBadge :file-url="doc.fileUrl" :content="doc.content" :size="36" />
                <div class="doc-row-info">
                  <h4 class="doc-row-title" :title="doc.title">{{ doc.title }}</h4>
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
              <button type="button" class="breadcrumb-link" @click="onAllClick">全部文档</button>
              <Icon name="chevron-right" :size="12" class="sep" />
              <template v-if="currentParentCategory">
                <button type="button" class="breadcrumb-link" @click="onCategoryClick(currentParentCategory)">{{ currentParentCategory.name }}</button>
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
                <Icon :name="getCategoryIcon(currentCategory.icon)" :size="24" />
              </div>
              <div>
                <h1 class="leaf-title">{{ currentCategory.name }}</h1>
                <p class="leaf-desc">{{ currentCategory.description || `浏览 ${currentCategory.name} 分类下的所有文档` }}</p>
              </div>
            </div>
            <div class="leaf-actions">
              <button class="btn-ghost small" @click="openInviteDialog">
                <Icon name="user-plus" :size="14" />
                <span>邀请成员</span>
              </button>
              <button class="upload-btn small" @click="goUpload">
                <Icon name="upload" :size="14" />
                <span>上传文档</span>
              </button>
            </div>
          </div>

          <!-- 标签筛选 -->
          <div v-if="categoryTags.length > 0 && !loading" class="tag-filter-bar">
            <button
              class="tag-filter-btn"
              :class="{ active: !selectedTag }"
              @click="selectedTag = null"
            >
              全部
            </button>
            <button
              v-for="tag in categoryTags"
              :key="tag"
              class="tag-filter-btn"
              :class="{ active: selectedTag === tag }"
              @click="selectedTag = selectedTag === tag ? null : tag"
            >
              {{ tag }}
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
            <Icon name="file-question" :size="32" class="empty-icon" />
            <h3>该分类下暂无文档</h3>
            <p>点击右上角「上传文档」添加内容</p>
          </div>

          <!-- 文档列表 -->
          <div v-else class="docs-list">
            <div
              v-for="(doc, idx) in sortedCategoryDocs"
              :key="doc.id"
              class="doc-row"
              role="button"
              tabindex="0"
              @click="goToDoc(doc.id)"
              @keydown.enter.prevent="($event.target as HTMLElement).click()"
            >
              <div class="doc-row-icon" :style="{ background: `${docIconColors[idx % docIconColors.length]}14`, color: docIconColors[idx % docIconColors.length] }">
                <Icon name="file-text" :size="16" />
              </div>
              <div class="doc-row-info">
                  <h4 class="doc-row-title" :title="doc.title">{{ doc.title }}</h4>
                  <p class="doc-row-summary">{{ doc.summary || '暂无摘要...' }}</p>
                <p class="doc-row-meta">
                  <span class="doc-row-tag" :style="{ background: `${docIconColors[idx % docIconColors.length]}14`, color: docIconColors[idx % docIconColors.length] }">{{ doc.categoryName || currentCategory.name }}</span>
                  <span class="doc-row-time">{{ formatRelativeTime(doc.createTime) }}</span>
                  <span class="doc-row-views"><Icon name="eye" :size="12" /> {{ doc.viewCount || 0 }}</span>
                </p>
              </div>
              <Icon name="arrow-right" :size="14" class="doc-row-arrow" />
            </div>
          </div>

          <!-- 分页 -->
          <div v-if="!loading && categoryTotal > categoryPageSize" class="docs-pagination">
            <button
              class="page-btn"
              :disabled="categoryPage <= 1"
              @click="changePage(categoryPage - 1)"
            >
              <Icon name="chevron-left" :size="14" />
            </button>
            <span class="page-info">{{ categoryPage }} / {{ categoryTotalPages }}</span>
            <button
              class="page-btn"
              :disabled="categoryPage >= categoryTotalPages"
              @click="changePage(categoryPage + 1)"
            >
              <Icon name="chevron-right" :size="14" />
            </button>
          </div>
        </section>
      </template>
    </main>
  </div>

  <!-- 邀请成员对话框 -->
  <Teleport to="body">
    <div v-if="inviteDialogVisible" class="invite-dialog-overlay" @click.self="closeInviteDialog">
      <div class="invite-dialog">
        <div class="invite-dialog-header">
          <h3>邀请成员加入知识库</h3>
          <button class="invite-close" @click="closeInviteDialog">
            <Icon name="x" :size="18" />
          </button>
        </div>
        <div class="invite-dialog-body">
          <p class="invite-desc">
            邀请成员加入「{{ currentCategory?.name }}」知识库，共同协作编辑和管理文档
          </p>
          
          <div class="invite-form-group">
            <label>成员邮箱</label>
            <input
              v-model="inviteForm.email"
              type="email"
              placeholder="请输入成员的邮箱地址"
              class="form-input"
            />
          </div>
          
          <div class="invite-form-group">
            <label>成员角色</label>
            <div class="role-options">
              <label 
                v-for="role in roleOptions" 
                :key="role.value"
                class="role-option"
                :class="{ active: inviteForm.role === role.value }"
              >
                <input type="radio" :value="role.value" v-model="inviteForm.role" />
                <div class="role-info">
                  <span class="role-name">{{ role.label }}</span>
                  <span class="role-desc">{{ role.description }}</span>
                </div>
              </label>
            </div>
          </div>
        </div>
        <div class="invite-dialog-footer">
          <button class="btn-secondary" @click="closeInviteDialog">取消</button>
          <button class="btn-primary" @click="handleInvite">
            <Icon name="send" :size="14" />
            <span>发送邀请</span>
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import StatCard from '@/components/ui/StatCard.vue'
import KnowledgeSidebar from '@/components/layout/KnowledgeSidebar.vue'
import { categoriesApi, docsApi, adminApi, userApi } from '@/api'
import type { CategoryVO, DocVO, UserStatsVO } from '@/api/types'
import DocTypeBadge from '@/components/doc/DocTypeBadge.vue'
import { notify, getApiError } from '@/utils/toast'
import { useKnowledgeStore } from '@/stores/knowledge'

const knowledgeStore = useKnowledgeStore()

const router = useRouter()
const route = useRoute()

const searchKeyword = ref('')
const allCategories = ref<CategoryVO[]>([])
const topCategories = ref<CategoryVO[]>([])
const latestDocs = ref<DocVO[]>([])
const categoryDocs = ref<DocVO[]>([])
const loading = ref(false)

// 移动端抽屉
const sidebarOpen = ref(false)

// 搜索框 ref（⌘K 快捷键聚焦）
const searchInputRef = ref<HTMLInputElement | null>(null)

// 搜索历史
const searchHistory = ref<string[]>([])
const showSearchHistory = ref(false)

// 标签筛选
const selectedTag = ref<string | null>(null)

// 学习概览统计数据
const userStats = ref<UserStatsVO | null>(null)

// 分页
const categoryPage = ref(1)
const categoryPageSize = 10
const categoryTotal = ref(0)
const categoryTotalPages = computed(() => Math.ceil(categoryTotal.value / categoryPageSize) || 1)

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

// 从当前分类文档中提取所有标签
const categoryTags = computed(() => {
  const tagSet = new Set<string>()
  categoryDocs.value.forEach(doc => {
    if (doc.tags) {
      doc.tags.split(',').forEach(t => {
        const trimmed = t.trim()
        if (trimmed) tagSet.add(trimmed)
      })
    }
  })
  return Array.from(tagSet).slice(0, 12)
})

const sortedCategoryDocs = computed(() => {
  let docs = [...categoryDocs.value]
  // 标签筛选
  if (selectedTag.value) {
    docs = docs.filter(d => {
      if (!d.tags) return false
      return d.tags.split(',').map(t => t.trim()).includes(selectedTag.value!)
    })
  }
  // 按时间排序
  return docs.sort((a, b) =>
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

let categoryLoadToken = 0
async function loadCategoryDocs(categoryId: number) {
  const token = ++categoryLoadToken
  loading.value = true
  try {
    const cat = findCategoryById(allCategories.value, categoryId)
    const hasChildren = cat && (cat.children?.length ?? 0) > 0

    if (hasChildren) {
      // 父分类：加载所有子分类的前几条用于预览（不分页）
      const ids = getAllChildIds(categoryId)
      let merged: DocVO[] = []
      for (const id of ids) {
        const res = await docsApi.list({ categoryId: id, pageSize: 50 } as any)
        if (token !== categoryLoadToken) return
        merged = merged.concat(res.records || [])
      }
      if (token !== categoryLoadToken) return
      const seen = new Set<number>()
      categoryDocs.value = merged.filter(d => {
        if (seen.has(d.id)) return false
        seen.add(d.id)
        return true
      })
      categoryTotal.value = categoryDocs.value.length
    } else {
      // 叶子分类：后端分页
      const res = await docsApi.list({
        categoryId,
        pageNum: categoryPage.value,
        pageSize: categoryPageSize,
      } as any)
      if (token !== categoryLoadToken) return
      categoryDocs.value = res.records || []
      categoryTotal.value = res.total || 0
    }
  } catch (e: unknown) {
    if (token !== categoryLoadToken) return
    categoryDocs.value = []
    categoryTotal.value = 0
    notify(getApiError(e, '加载文档失败'), 'error')
  } finally {
    if (token === categoryLoadToken) loading.value = false
  }
}

function changePage(page: number) {
  if (page < 1 || page > categoryTotalPages.value) return
  categoryPage.value = page
  if (activeCategoryId.value) {
    loadCategoryDocs(activeCategoryId.value)
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
  categoryPage.value = 1
  sidebarOpen.value = false
  router.replace({ path: '/knowledge', query: { categoryId: String(cat.id) } })
}

function onAllClick() {
  activeCategoryId.value = null
  activeKey.value = 'all'
  categoryPage.value = 1
  sidebarOpen.value = false
  router.replace({ path: '/knowledge' })
}

const goSearch = () => {
  const kw = searchKeyword.value.trim()
  if (kw) {
    saveSearchHistory(kw)
  }
  showSearchHistory.value = false
  router.push({ path: '/search', query: { q: searchKeyword.value } })
}

// ===== 搜索历史 =====
function loadSearchHistory() {
  try {
    const stored = localStorage.getItem('kb_search_history')
    if (stored) {
      searchHistory.value = JSON.parse(stored)
    }
  } catch {
    searchHistory.value = []
  }
}

function saveSearchHistory(keyword: string) {
  const updated = [keyword, ...searchHistory.value.filter(k => k !== keyword)].slice(0, 10)
  searchHistory.value = updated
  try {
    localStorage.setItem('kb_search_history', JSON.stringify(updated))
  } catch {
    // localStorage 不可用时静默失败
  }
}

function clearSearchHistory() {
  searchHistory.value = []
  localStorage.removeItem('kb_search_history')
}

function searchFromHistory(keyword: string) {
  searchKeyword.value = keyword
  showSearchHistory.value = false
  goSearch()
}

function hideSearchHistory() {
  setTimeout(() => { showSearchHistory.value = false }, 200)
}

const goToDoc = (id: number) => {
  router.push(`/doc/${id}`)
}

const goUpload = () => {
  router.push('/knowledge/upload')
}

const goImport = () => {
  router.push('/knowledge/import')
}

const goReader = () => {
  router.push('/knowledge/reader')
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
    const [cats, recent, stats] = await Promise.all([
      knowledgeStore.fetchTree(),
      docsApi.list({ pageSize: 6, sortBy: 'new' } as any).then((r) => r.records || []),
      userApi.stats().catch(() => null),
    ])
    allCategories.value = cats
    topCategories.value = cats.slice(0, 6)
    latestDocs.value = recent.slice(0, 4)
    userStats.value = stats

    // 如果 URL 有 categoryId，选中对应分类（由 watch(activeCategoryId) 自动加载）
    const catId = route.query.categoryId
    if (catId && typeof catId === 'string') {
      activeCategoryId.value = Number(catId)
    }
  } catch (e: unknown) {
    topCategories.value = []
    latestDocs.value = []
    allCategories.value = []
    notify(getApiError(e, '加载知识库数据失败'), 'error')
  }
}

// 路由 categoryId 变化时，仅同步选中状态；实际加载交给 watch(activeCategoryId)
watch(
  () => route.query.categoryId,
  (newId) => {
    if (newId && typeof newId === 'string') {
      const id = Number(newId)
      if (activeCategoryId.value !== id) {
        activeCategoryId.value = id
      }
    } else if (activeCategoryId.value !== null) {
      activeCategoryId.value = null
    }
  }
)

// 选中分类变化 → 统一加载文档；配合 categoryLoadToken，只有最新一次点击的结果会被采用
watch(activeCategoryId, (id) => {
  categoryPage.value = 1
  selectedTag.value = null
  if (id) {
    loadCategoryDocs(id)
  } else {
    categoryDocs.value = []
    categoryTotal.value = 0
  }
})

// ===== 邀请成员功能 =====
const inviteDialogVisible = ref(false)
const inviteForm = ref({
  email: '',
  role: 'READER',
})

const roleOptions = [
  {
    value: 'READER',
    label: '阅读者',
    description: '只能查看和搜索文档',
  },
  {
    value: 'EDITOR',
    label: '编辑者',
    description: '可以编辑和创建文档',
  },
  {
    value: 'OWNER',
    label: '管理员',
    description: '拥有完整管理权限',
  },
]

function openInviteDialog() {
  if (!activeCategoryId.value) {
    notify('请先选择一个知识库', 'warning')
    return
  }
  inviteForm.value = { email: '', role: 'READER' }
  inviteDialogVisible.value = true
}

function closeInviteDialog() {
  inviteDialogVisible.value = false
}

async function handleInvite() {
  const email = inviteForm.value.email.trim()
  if (!email) {
    notify('请输入成员邮箱', 'warning')
    return
  }
  if (!email.includes('@')) {
    notify('请输入有效的邮箱地址', 'warning')
    return
  }

  try {
    await adminApi.addKbMember({
      categoryId: activeCategoryId.value!,
      email,
      role: inviteForm.value.role,
    })
    const roleLabel = roleOptions.find(r => r.value === inviteForm.value.role)?.label
    notify(`已邀请 ${email} 加入知识库，角色：${roleLabel}`, 'success')
    closeInviteDialog()
  } catch (e: unknown) {
    notify(getApiError(e, '邀请失败，请稍后再试'), 'error')
  }
}

// ⌘K / Ctrl+K 快捷键聚焦搜索框
function handleShortcut(e: KeyboardEvent) {
  if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
    e.preventDefault()
    searchInputRef.value?.focus()
    searchInputRef.value?.select()
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleShortcut)
  loadSearchHistory()
  loadData()
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleShortcut)
})
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
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 24px;
}

.search-box {
  position: relative;
  flex: 1;
  min-width: 0;
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
  font-size: var(--kb-fs-body-md);
  color: var(--kb-foreground);
  outline: none;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.search-input:hover {
  border-color: var(--kb-primary);
}

.search-input:focus,
.search-input:focus-visible {
  border-color: var(--kb-primary);
  box-shadow: 0 0 0 3px rgba(59, 111, 224, 0.12);
}

.search-shortcut {
  position: absolute;
  right: 14px;
  top: 50%;
  transform: translateY(-50%);
  font-size: var(--kb-fs-xs);
  font-family: var(--font-mono);
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
  font-size: var(--kb-fs-body-md);
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.15s, transform 0.15s, box-shadow 0.15s;
  white-space: nowrap;
}

.upload-btn:hover {
  opacity: 0.9;
}

.upload-btn:active {
  opacity: 0.9;
  transform: scale(0.98);
}

.upload-btn:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}

.upload-btn.small {
  height: 36px;
  padding: 0 14px;
  font-size: var(--kb-fs-body-sm);
}

.import-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 44px;
  padding: 0 16px;
  border-radius: 10px;
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-foreground);
  font-size: var(--kb-fs-body-md);
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
}

.import-btn:hover {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
}

.import-btn:active {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.08);
  transform: scale(0.98);
}

.import-btn:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}

/* 问候语 */
.greeting-section {
  margin-bottom: 36px;
}

.greeting-title {
  font-size: var(--kb-fs-h3);
  line-height: var(--kb-lh-h3);
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
  font-size: var(--kb-fs-body-lg);
  line-height: var(--kb-lh-body-lg);
  font-weight: 500;
  color: var(--kb-foreground);
  margin-bottom: 6px;
}

.quote-author {
  font-size: var(--kb-fs-body-sm);
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
  font-size: var(--kb-fs-body-lg);
  line-height: var(--kb-lh-body-lg);
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
  padding: 4px 8px;
  margin-right: -8px;
  border-radius: var(--kb-radius-sm);
  font-size: var(--kb-fs-body-sm);
  font-weight: 500;
  color: var(--kb-primary);
  text-decoration: none;
  white-space: nowrap;
  transition: background 0.15s, opacity 0.15s;
}

.view-all-link:hover {
  opacity: 0.8;
  background: rgba(59, 111, 224, 0.08);
}

.view-all-link:active {
  opacity: 1;
  background: rgba(59, 111, 224, 0.14);
}

.view-all-link:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
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
  gap: 12px;
  padding: 16px;
  border-radius: 12px;
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s, transform 0.15s, background 0.15s;
}

.quick-card:hover {
  border-color: var(--kb-primary);
  box-shadow: 0 4px 16px rgba(59, 111, 224, 0.08);
  transform: translateY(-1px);
}

.quick-card:active {
  border-color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.04);
  transform: translateY(0) scale(0.98);
}

.quick-card:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
  border-radius: 12px;
  border-color: var(--kb-primary);
}

.quick-icon {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.quick-info {
  min-width: 0;
  max-width: 100%;
}

.quick-name {
  font-size: var(--kb-fs-body-md);
  font-weight: 600;
  color: var(--kb-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.quick-count {
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
  font-variant-numeric: tabular-nums;
}

/* 学习概览统计 */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
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
  padding: 12px 16px;
  border-radius: 10px;
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s, background 0.15s, transform 0.15s;
}

.recent-card:hover {
  border-color: var(--kb-primary);
  box-shadow: 0 2px 8px rgba(59, 111, 224, 0.06);
}

.recent-card:active {
  border-color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.04);
  transform: scale(0.98);
}

.recent-card:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
  border-radius: 10px;
  border-color: var(--kb-primary);
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
  font-size: var(--kb-fs-body-md);
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
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
  font-size: var(--kb-fs-caption);
}

.recent-tag {
  padding: 2px 6px;
  border-radius: 4px;
  font-weight: 500;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-time {
  color: var(--kb-muted-foreground);
  font-variant-numeric: tabular-nums;
}

.empty-hint {
  padding: 40px 0;
  text-align: center;
  font-size: var(--kb-fs-body-md);
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
  flex-wrap: wrap;
  min-width: 0;
  gap: 6px;
  font-size: var(--kb-fs-body-sm);
}

.breadcrumb-link {
  padding: 0;
  border: none;
  background: none;
  font-family: inherit;
  font-size: inherit;
  line-height: inherit;
  color: var(--kb-muted-foreground);
  cursor: pointer;
  border-radius: var(--kb-radius-sm);
  transition: color 0.15s;
}

.breadcrumb-link:hover {
  color: var(--kb-primary);
}

.breadcrumb-link:active {
  color: var(--kb-primary);
  opacity: 0.75;
}

.breadcrumb-link:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
  color: var(--kb-primary);
}

.breadcrumb-current {
  color: var(--kb-foreground);
  font-weight: 600;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.breadcrumb .sep {
  color: var(--kb-muted-foreground);
  opacity: 0.5;
}

.header-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.meta-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 999px;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
  font-size: var(--kb-fs-caption);
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
  font-size: var(--kb-fs-h4);
  line-height: var(--kb-lh-h4);
  font-weight: 700;
  color: var(--kb-foreground);
  margin: 0 0 6px;
}

.hero-desc {
  font-size: var(--kb-fs-body-md);
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
  gap: 16px;
}

.sub-category-card {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px 20px;
  border-radius: 12px;
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s, transform 0.15s, background 0.15s;
}

.sub-category-card:hover {
  border-color: var(--kb-primary);
  box-shadow: 0 4px 14px rgba(59, 111, 224, 0.08);
  transform: translateY(-1px);
}

.sub-category-card:active {
  border-color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.04);
  transform: translateY(0) scale(0.98);
}

.sub-category-card:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
  border-color: var(--kb-primary);
}

.sub-category-card:focus-visible .sub-arrow {
  opacity: 1;
  color: var(--kb-primary);
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
  font-size: var(--kb-fs-body-md);
  font-weight: 600;
  color: var(--kb-foreground);
  margin: 0 0 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sub-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
  margin: 0 0 4px;
  font-variant-numeric: tabular-nums;
}

.sub-divider {
  opacity: 0.5;
}

.sub-sub {
  color: var(--kb-muted-foreground);
}

.sub-desc {
  font-size: var(--kb-fs-caption);
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
  gap: 16px;
  padding: 16px 20px;
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

.doc-row:active {
  background: rgba(59, 111, 224, 0.08);
}

.doc-row:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: -2px;
  background: rgba(59, 111, 224, 0.03);
}

.doc-row:focus-visible .doc-row-arrow {
  opacity: 1;
  color: var(--kb-primary);
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
  font-size: var(--kb-fs-body-md);
  font-weight: 600;
  color: var(--kb-foreground);
  margin: 0 0 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.doc-row-summary {
  font-size: var(--kb-fs-body-sm);
  color: var(--kb-muted-foreground);
  margin: 0 0 6px;
  line-height: var(--kb-lh-body-sm);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.doc-row-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  min-width: 0;
  gap: 8px;
  font-size: var(--kb-fs-caption);
  margin: 0;
}

.doc-row-tag {
  padding: 1px 6px;
  border-radius: 4px;
  font-weight: 500;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.doc-row-time {
  color: var(--kb-muted-foreground);
  font-variant-numeric: tabular-nums;
}

.doc-row-views {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--kb-muted-foreground);
  font-variant-numeric: tabular-nums;
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
  padding: 24px 28px;
  border-radius: 14px;
  border: 1px solid;
  flex-wrap: wrap;
}

.leaf-info {
  display: flex;
  align-items: center;
  gap: 16px;
  flex: 1;
  min-width: 0;
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
  font-size: var(--kb-fs-h4);
  line-height: var(--kb-lh-h4);
  font-weight: 700;
  color: var(--kb-foreground);
  margin: 0 0 4px;
}

.leaf-desc {
  font-size: var(--kb-fs-body-sm);
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
  gap: 12px;
  padding: 60px 24px;
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: 12px;
  text-align: center;
}

.docs-empty h3 {
  font-size: var(--kb-fs-body-lg);
  line-height: var(--kb-lh-body-lg);
  font-weight: 600;
  color: var(--kb-foreground);
  margin: 0;
}

.docs-empty p {
  font-size: var(--kb-fs-body-sm);
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
  gap: 16px;
  padding: 16px 20px;
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
    font-size: var(--kb-fs-h4);
    line-height: var(--kb-lh-h4);
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

/* ===== 邀请成员对话框 ===== */
.invite-dialog-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
  padding: 20px;
}

.invite-dialog {
  width: 100%;
  max-width: 480px;
  background: var(--kb-card);
  border-radius: 16px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.2);
  overflow: hidden;
}

.invite-dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: 1px solid var(--kb-border);
}

.invite-dialog-header h3 {
  margin: 0;
  font-size: var(--kb-fs-h4);
  line-height: var(--kb-lh-h4);
  font-weight: 600;
  color: var(--kb-foreground);
}

.invite-close {
  background: transparent;
  border: none;
  cursor: pointer;
  color: var(--kb-muted-foreground);
  padding: 4px;
  border-radius: 6px;
  transition: all 0.15s;
}

.invite-close:hover {
  background: var(--kb-muted);
  color: var(--kb-foreground);
}

.invite-close:active {
  background: var(--kb-muted);
  color: var(--kb-foreground);
  transform: scale(0.94);
}

.invite-close:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
  color: var(--kb-foreground);
}

.invite-dialog-body {
  padding: 24px;
}

.invite-desc {
  color: var(--kb-muted-foreground);
  font-size: var(--kb-fs-body-md);
  margin: 0 0 20px;
  line-height: var(--kb-lh-body-md);
}

.invite-form-group {
  margin-bottom: 20px;
}

.invite-form-group label {
  display: block;
  font-size: var(--kb-fs-body-sm);
  font-weight: 500;
  color: var(--kb-foreground);
  margin-bottom: 8px;
}

.invite-form-group .form-input {
  width: 100%;
  padding: 10px 14px;
  border-radius: 10px;
  font-size: var(--kb-fs-body-md);
  background: var(--kb-background);
  color: var(--kb-foreground);
  border: 1px solid var(--kb-border);
  outline: none;
  transition: border-color 0.15s, box-shadow 0.15s;
  box-sizing: border-box;
}

.invite-form-group .form-input:hover {
  border-color: var(--kb-primary);
}

.invite-form-group .form-input:focus,
.invite-form-group .form-input:focus-visible {
  border-color: var(--kb-primary);
  box-shadow: 0 0 0 3px rgba(59, 111, 224, 0.12);
}

.role-options {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.role-option {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 10px;
  border: 1px solid var(--kb-border);
  cursor: pointer;
  transition: all 0.15s;
}

.role-option:hover {
  border-color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.04);
}

.role-option:active {
  border-color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.1);
}

.role-option:focus-within {
  border-color: var(--kb-primary);
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}

.role-option.active {
  border-color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.08);
}

.role-option input[type="radio"] {
  margin: 0;
  accent-color: var(--kb-primary);
  margin-top: 3px;
}

.role-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.role-name {
  font-size: var(--kb-fs-body-md);
  font-weight: 500;
  color: var(--kb-foreground);
}

.role-desc {
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
}

.invite-dialog-footer {
  display: flex;
  gap: 12px;
  padding: 20px 24px;
  border-top: 1px solid var(--kb-border);
}

.invite-dialog-footer .btn-secondary,
.invite-dialog-footer .btn-primary {
  flex: 1;
  height: 40px;
  border-radius: 10px;
  font-size: var(--kb-fs-body-md);
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.invite-dialog-footer .btn-secondary:focus-visible,
.invite-dialog-footer .btn-primary:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}

.invite-dialog-footer .btn-secondary {
  background: var(--kb-background);
  color: var(--kb-foreground);
  border: 1px solid var(--kb-border);
}

.invite-dialog-footer .btn-secondary:hover {
  background: var(--kb-muted);
}

.invite-dialog-footer .btn-secondary:active {
  background: var(--kb-muted);
  border-color: var(--kb-primary);
  transform: scale(0.98);
}

.invite-dialog-footer .btn-primary {
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
}

.invite-dialog-footer .btn-primary:hover {
  opacity: 0.9;
}

.invite-dialog-footer .btn-primary:active {
  opacity: 0.9;
  transform: scale(0.98);
}

/* leaf-header 操作区 */
.leaf-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.btn-ghost.small {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: 8px;
  font-size: var(--kb-fs-body-sm);
  font-weight: 500;
  cursor: pointer;
  background: var(--kb-card);
  color: var(--kb-foreground);
  border: 1px solid var(--kb-border);
  transition: all 0.15s;
}

.btn-ghost.small:hover {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
}

.btn-ghost.small:active {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.08);
  transform: scale(0.98);
}

.btn-ghost.small:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}

@media (max-width: 640px) {
  .leaf-actions {
    flex-direction: column;
    width: 100%;
  }

  .btn-ghost.small,
  .upload-btn.small {
    width: 100%;
    justify-content: center;
  }
}

/* ===== 移动端侧边栏抽屉 ===== */
.sidebar-overlay {
  display: none;
}

.mobile-menu-btn {
  display: none;
}

@media (max-width: 1024px) {
  .sidebar-overlay {
    display: block;
    position: fixed;
    inset: 0;
    background: rgba(0, 0, 0, 0.4);
    z-index: 1000;
  }

  .mobile-menu-btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 40px;
    height: 40px;
    border-radius: 8px;
    border: 1px solid var(--kb-border);
    background: var(--kb-card);
    color: var(--kb-foreground);
    cursor: pointer;
    flex-shrink: 0;
    transition: border-color 0.15s, color 0.15s, background 0.15s, transform 0.15s;
  }

  .mobile-menu-btn:hover {
    border-color: var(--kb-primary);
    color: var(--kb-primary);
  }

  .mobile-menu-btn:active {
    border-color: var(--kb-primary);
    color: var(--kb-primary);
    background: rgba(59, 111, 224, 0.08);
    transform: scale(0.96);
  }

  .mobile-menu-btn:focus-visible {
    outline: 2px solid var(--kb-ring);
    outline-offset: 2px;
    border-color: var(--kb-primary);
  }
}

/* ===== 分页 ===== */
.docs-pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: 16px;
  padding: 12px 0;
}

.page-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-foreground);
  cursor: pointer;
  transition: all 0.15s;
}

.page-btn:hover:not(:disabled) {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
}

.page-btn:active:not(:disabled) {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.08);
  transform: scale(0.96);
}

.page-btn:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
  border-color: var(--kb-primary);
}

.page-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.page-info {
  font-size: var(--kb-fs-body-sm);
  font-weight: 500;
  color: var(--kb-muted-foreground);
  min-width: 60px;
  text-align: center;
  font-variant-numeric: tabular-nums;
}

/* ===== 搜索历史下拉 ===== */
.search-history-dropdown {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  margin-top: 4px;
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: 10px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  z-index: 100;
  overflow: hidden;
}

.history-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px 6px;
}

.history-title {
  font-size: var(--kb-fs-xs);
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: var(--kb-muted-foreground);
}

.history-clear {
  background: transparent;
  border: none;
  font-size: var(--kb-fs-xs);
  color: var(--kb-primary);
  cursor: pointer;
  padding: 0;
  border-radius: var(--kb-radius-sm);
  transition: color 0.15s, opacity 0.15s;
}

.history-clear:hover {
  text-decoration: underline;
}

.history-clear:active {
  text-decoration: underline;
  opacity: 0.75;
}

.history-clear:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
  text-decoration: underline;
}

.history-items {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 4px 14px 12px;
}

.history-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: var(--kb-fs-caption);
  max-width: 100%;
  min-width: 0;
  background: var(--kb-muted);
  color: var(--kb-foreground);
  border: 1px solid transparent;
  cursor: pointer;
  transition: all 0.15s;
}

.history-item span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-item:hover {
  background: rgba(59, 111, 224, 0.08);
  color: var(--kb-primary);
  border-color: var(--kb-primary);
}

.history-item:active {
  background: rgba(59, 111, 224, 0.16);
  color: var(--kb-primary);
  border-color: var(--kb-primary);
  transform: scale(0.97);
}

.history-item:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
  color: var(--kb-primary);
  border-color: var(--kb-primary);
}

.history-icon {
  color: var(--kb-muted-foreground);
}

/* ===== 标签筛选栏 ===== */
.tag-filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px 0;
}

.tag-filter-btn {
  padding: 4px 12px;
  border-radius: 999px;
  font-size: var(--kb-fs-caption);
  font-weight: 500;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  background: var(--kb-card);
  color: var(--kb-muted-foreground);
  border: 1px solid var(--kb-border);
  cursor: pointer;
  transition: all 0.15s;
}

.tag-filter-btn:hover {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
}

.tag-filter-btn:active {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.08);
  transform: scale(0.97);
}

.tag-filter-btn:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
  border-color: var(--kb-primary);
}

.tag-filter-btn.active {
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  border-color: var(--kb-primary);
}
</style>
