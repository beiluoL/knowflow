<template>
  <div class="animate-fade-in">
    <!-- 页面头部 -->
    <section class="flex items-center justify-between mb-6 gap-4 flex-wrap">
      <h1 class="kb-h2">分类浏览</h1>
      <div class="flex items-center gap-2 px-3 py-2 rounded-lg border w-full sm:w-72" style="background: var(--kb-card); border-color: var(--kb-border);">
        <Icon name="search" :size="16" class="shrink-0" style="color: var(--kb-muted-foreground);" />
        <input
          v-model="searchKeyword"
          type="text"
          placeholder="搜索分类..."
          class="flex-1 text-sm outline-none bg-transparent"
          style="color: var(--kb-foreground);"
        />
      </div>
    </section>

    <!-- 主体：左侧分类网格 + 右侧标签云 -->
    <div class="flex gap-6">
      <!-- 左侧 -->
      <div class="flex-1 min-w-0">
        <!-- 分类网格（4 列） -->
        <div v-if="!selectedCategory" class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4 mb-8">
          <a
            v-for="(cat, idx) in filteredCategories"
            :key="cat.id"
            href="#"
            class="category-card"
            @click.prevent="selectCategory(cat)"
          >
            <div class="cat-icon" :style="{ background: `${categoryColors[idx % categoryColors.length]}14` }">
              <Icon :name="getCategoryIcon(cat.icon, idx)" :size="24" :style="{ color: categoryColors[idx % categoryColors.length] }" />
            </div>
            <h3 class="text-sm font-semibold mb-1" style="color: var(--kb-foreground);">{{ cat.name }}</h3>
            <p class="text-xs mb-2" style="color: var(--kb-muted-foreground);">{{ cat.docCount || 0 }} 篇文档</p>
            <p class="text-[11px] line-clamp-2" style="color: var(--kb-muted-foreground);">{{ cat.subtitle || getCategoryDescription(cat.name) }}</p>
          </a>
        </div>

        <!-- 分类文档列表（点击分类后显示） -->
        <div v-else>
          <div class="flex items-center justify-between mb-4 gap-3 flex-wrap">
            <div class="flex items-center gap-3 min-w-0">
              <button
                type="button"
                class="text-sm font-medium hover:opacity-80 inline-flex items-center gap-1 shrink-0"
                style="color: var(--kb-primary);"
                @click="backToCategories"
              >
                <Icon name="arrow-left" :size="14" />
                <span>返回分类</span>
              </button>
              <h2 class="kb-h4 truncate">{{ selectedCategory.name }} - 文档列表</h2>
            </div>
            <div class="flex items-center gap-3">
              <div class="flex items-center gap-2 px-3 py-1.5 rounded-lg border" style="background: var(--kb-card); border-color: var(--kb-border);">
                <Icon name="search" :size="14" class="shrink-0" style="color: var(--kb-muted-foreground);" />
                <input
                  v-model="docSearchKeyword"
                  type="text"
                  placeholder="搜索文档..."
                  class="text-xs outline-none bg-transparent w-32 sm:w-40"
                  style="color: var(--kb-foreground);"
                />
              </div>
              <select
                v-model="sortBy"
                class="text-xs px-2 py-1.5 rounded-lg border outline-none cursor-pointer"
                style="background: var(--kb-card); border-color: var(--kb-border); color: var(--kb-muted-foreground);"
              >
                <option value="latest">按时间排序</option>
                <option value="hot">按热度排序</option>
                <option value="name">按名称排序</option>
              </select>
            </div>
          </div>

          <!-- 加载中 -->
          <div v-if="loading" class="rounded-xl border p-10 text-center" style="background: var(--kb-card); border-color: var(--kb-border);">
            <Icon name="loader" :size="28" class="spin inline-block" style="color: var(--kb-muted-foreground);" />
            <p class="text-sm mt-2" style="color: var(--kb-muted-foreground);">加载中...</p>
          </div>

          <!-- 空态 -->
          <div v-else-if="filteredDocs.length === 0" class="rounded-xl border p-10 text-center" style="background: var(--kb-card); border-color: var(--kb-border);">
            <Icon name="file-question" :size="40" style="color: var(--kb-muted-foreground);" />
            <p class="text-sm mt-2" style="color: var(--kb-muted-foreground);">暂无文档</p>
          </div>

          <!-- 文档列表 -->
          <div v-else class="rounded-xl border overflow-hidden" style="background: var(--kb-card); border-color: var(--kb-border);">
            <a
              v-for="(doc, idx) in filteredDocs"
              :key="doc.id"
              href="#"
              class="cat-doc-item"
              @click.prevent="goToDoc(doc.id)"
            >
              <span
                class="inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded-md font-medium shrink-0 mt-0.5"
                :style="{ background: `${docIconColors[idx % docIconColors.length]}14`, color: docIconColors[idx % docIconColors.length] }"
              >{{ getDocType(doc) }}</span>
              <div class="flex-1 min-w-0">
                <h4 class="text-sm font-semibold mb-1" style="color: var(--kb-foreground);">{{ doc.title }}</h4>
                <p class="text-xs line-clamp-1" style="color: var(--kb-muted-foreground);">{{ doc.summary || '暂无摘要' }}</p>
              </div>
              <span class="text-xs shrink-0" style="color: var(--kb-muted-foreground);">{{ formatRelativeTime(doc.createTime) }}</span>
            </a>
          </div>
        </div>
      </div>

      <!-- 右侧：标签云 -->
      <aside class="w-56 shrink-0 hidden lg:block">
        <div class="rounded-xl border p-5 sticky top-20" style="background: var(--kb-card); border-color: var(--kb-border);">
          <h3 class="kb-h4 mb-4" style="font-size: 13px;">热门标签</h3>
          <div class="flex flex-wrap gap-2">
            <span
              v-for="tag in hotTags"
              :key="tag"
              class="tag-item"
              @click="goToSearch(tag)"
            >{{ tag }}</span>
          </div>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
// 分类浏览页：顶部分类网格 + 文档列表 + 标签云
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { categoriesApi, docsApi } from '@/api'
import type { CategoryVO, DocVO } from '@/api/types'

const route = useRoute()
const router = useRouter()

const categoryTree = ref<CategoryVO[]>([])
const allDocs = ref<DocVO[]>([])
const loading = ref(false)
const searchKeyword = ref('')
const docSearchKeyword = ref('')
const sortBy = ref<'latest' | 'hot' | 'name'>('latest')
const selectedCategoryId = ref<number | string>('')

// 分类主色板：与设计稿对齐（蓝/绿/黄/红/紫）
const categoryColors = ['#3B6FE0', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6']
const docIconColors = ['#3B6FE0', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6']

// 热门标签（从文档标签聚合或使用默认列表）
const hotTags = ref<string[]>([
  'Python', 'React', '深度学习', 'CSS', 'Docker', 'NLP',
  '微服务', 'TypeScript', '算法', 'GPT', 'Kubernetes', 'Vue',
  '数据可视化', 'Git', '设计系统', 'Rust', 'GraphQL', 'SQL'
])

// icon 映射：将后端 icon 字段或分类名映射到 lucide 图标
const iconOptions = ['code', 'server', 'database', 'brain', 'settings', 'git-branch', 'monitor', 'wifi', 'folder', 'layers', 'book-open', 'cpu', 'palette', 'briefcase', 'shield', 'message-square', 'target', 'bar-chart-2', 'bot', 'lock', 'layout', 'smartphone', 'container', 'binary', 'kanban', 'file-code']

const categoryNameIconMap: Record<string, string> = {
  '人工智能': 'brain',
  '前端开发': 'layout',
  '后端技术': 'server',
  '数据科学': 'bar-chart-2',
  '移动开发': 'smartphone',
  '数据库': 'database',
  'DevOps': 'container',
  '网络安全': 'shield',
  '算法与数据结构': 'binary',
  '产品设计': 'palette',
  '编程语言': 'file-code',
  '项目管理': 'kanban',
  '编程开发': 'code',
  '默认': 'folder'
}

const getCategoryIcon = (iconName: string | undefined, idx: number): string => {
  if (iconName && iconOptions.includes(iconName)) return iconName
  // 默认按索引轮换使用一组常用图标
  const fallback = ['brain', 'layout', 'server', 'bar-chart-2', 'smartphone', 'database', 'container', 'shield', 'binary', 'palette', 'file-code', 'kanban']
  return fallback[idx % fallback.length]
}

// 顶层分类列表（按设计稿展开为扁平网格）
const topLevelCategories = computed<CategoryVO[]>(() => {
  return categoryTree.value
})

// 按搜索关键字过滤分类
const filteredCategories = computed(() => {
  if (!searchKeyword.value.trim()) return topLevelCategories.value
  const kw = searchKeyword.value.toLowerCase()
  return topLevelCategories.value.filter(c => (c.name || '').toLowerCase().includes(kw))
})

// 当前选中的分类对象
const selectedCategory = computed(() => {
  if (!selectedCategoryId.value) return null
  return topLevelCategories.value.find(c => c.id === Number(selectedCategoryId.value)) || null
})

// 递归收集某分类自身及其所有后代子分类的 id 列表
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

// 过滤并排序文档列表
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

const getDocType = (doc: DocVO): string => {
  const tags = (doc.tags || '').toLowerCase()
  if (tags.includes('pdf')) return 'PDF'
  if (tags.includes('markdown') || tags.includes('md')) return 'MD'
  if (tags.includes('笔记') || tags.includes('note')) return 'Note'
  return 'DOC'
}

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

const loadCategoryDocs = async (categoryId: number | string) => {
  loading.value = true
  try {
    const ids = getAllChildCategoryIds(categoryId, categoryTree.value)
    let merged: DocVO[] = []
    for (const id of ids) {
      const res = await docsApi.list({ categoryId: id, pageSize: 100 })
      merged = merged.concat(res.records || [])
    }
    // 去重
    allDocs.value = [...new Map(merged.map((d) => [d.id, d])).values()]
  } catch {
    allDocs.value = []
  } finally {
    loading.value = false
  }
}

const selectCategory = (cat: CategoryVO) => {
  selectedCategoryId.value = cat.id
  router.push({ path: '/categories', query: { categoryId: String(cat.id) } })
  loadCategoryDocs(cat.id)
}

const backToCategories = () => {
  selectedCategoryId.value = ''
  router.push({ path: '/categories' })
  allDocs.value = []
}

const goToDoc = (docId: number) => {
  router.push(`/doc/${docId}`)
}

const goToSearch = (tag: string) => {
  router.push({ path: '/search', query: { q: tag } })
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
/* 分类卡片：与设计稿 .category-card 对齐 */
.category-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 24px 16px;
  border-radius: var(--kb-radius-lg);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  transition: all 0.2s;
  cursor: pointer;
  text-decoration: none;
}
.category-card:hover {
  box-shadow: 0 4px 16px rgba(59, 111, 224, 0.1);
  border-color: var(--kb-primary);
  transform: translateY(-2px);
}
.category-card .cat-icon {
  width: 48px;
  height: 48px;
  border-radius: var(--kb-radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 12px;
}

/* 标签云 */
.tag-item {
  display: inline-flex;
  padding: 5px 12px;
  border-radius: 20px;
  font-size: 12px;
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-muted-foreground);
  text-decoration: none;
  transition: all 0.15s;
  cursor: pointer;
  user-select: none;
}
.tag-item:hover {
  background: rgba(59, 111, 224, 0.08);
  color: var(--kb-primary);
  border-color: var(--kb-primary);
}

/* 分类下的文档列表项 */
.cat-doc-item {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--kb-border);
  transition: background-color 0.15s;
  text-decoration: none;
}
.cat-doc-item:hover {
  background-color: rgba(59, 111, 224, 0.02);
}
.cat-doc-item:last-child {
  border-bottom: none;
}

.spin {
  animation: spin 1s linear infinite;
}
@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.animate-fade-in {
  animation: fadeIn 0.5s ease-out;
}
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>
