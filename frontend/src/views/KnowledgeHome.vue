<template>
  <div class="animate-fade-in">
    <!-- ========== Section 1: Hero 搜索区（居中） ========== -->
    <section class="mb-10">
      <div class="flex flex-col items-center text-center pt-6 pb-3">
        <div class="inline-flex items-center gap-2 px-3 py-1 rounded-full mb-5" style="background: rgba(59,111,224,0.08);">
          <Icon name="compass" :size="14" style="color: var(--kb-primary);" />
          <span class="text-xs font-medium" style="color: var(--kb-primary);">知识库浏览中心</span>
        </div>
        <h1 class="kb-h1 mb-3" style="text-wrap: balance;">探索知识库</h1>
        <p class="text-sm mb-7" style="color: var(--kb-muted-foreground);">
          在 {{ topCategories.length }} 个知识库、{{ totalDocs }} 篇文档中找到你需要的内容
        </p>

        <!-- 大搜索框 -->
        <div class="w-full max-w-2xl relative">
          <Icon name="search" :size="20" class="absolute left-4 top-1/2 -translate-y-1/2" style="color: var(--kb-muted-foreground);" />
          <input
            v-model="searchKeyword"
            type="text"
            placeholder="搜索知识库、文档、标签..."
            class="kb-search-input"
            @keyup.enter="goSearch"
          />
          <span class="absolute right-4 top-1/2 -translate-y-1/2 text-xs px-2 py-1 rounded-md font-medium" style="background: var(--kb-muted); color: var(--kb-muted-foreground);">⌘K</span>
        </div>

        <!-- 热门标签 -->
        <div class="flex items-center flex-wrap justify-center gap-2 mt-6">
          <span class="text-xs mr-1" style="color: var(--kb-muted-foreground);">热门标签：</span>
          <button
            v-for="tag in hotTags"
            :key="tag.name"
            type="button"
            class="kb-tag-chip"
            @click="goSearch(tag.name)"
          >
            {{ tag.name }}
          </button>
        </div>
      </div>
    </section>

    <!-- ========== Section 2: 知识库卡片网格（2x3） ========== -->
    <section class="mb-10">
      <div class="flex items-center justify-between mb-5">
        <div class="flex items-center gap-2">
          <h2 class="kb-h3">全部知识库</h2>
          <span class="text-xs px-2 py-0.5 rounded-md" style="background: var(--kb-muted); color: var(--kb-muted-foreground);">{{ topCategories.length }}</span>
        </div>
        <router-link to="/categories" class="text-sm font-medium hover:opacity-80 inline-flex items-center gap-1" style="color: var(--kb-primary);">
          查看全部
          <Icon name="arrow-right" :size="14" />
        </router-link>
      </div>

      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
        <a
          v-for="(cat, idx) in topCategories"
          :key="cat.id"
          class="kb-card-item"
          @click.prevent="goToCategory(cat.id)"
        >
          <div :style="{ height: '4px', background: kbColors[idx % kbColors.length] }"></div>
          <div class="p-5">
            <div class="flex items-start gap-3 mb-3">
              <div class="kb-card-icon" :style="{ background: `${kbColors[idx % kbColors.length]}1A` }">
                <Icon :name="getCategoryIcon(cat.icon)" :size="20" :style="{ color: kbColors[idx % kbColors.length] }" />
              </div>
              <div class="min-w-0 flex-1">
                <h3 class="text-sm font-semibold truncate" style="color: var(--kb-foreground);">{{ cat.name }}</h3>
                <p class="text-xs mt-0.5 truncate" style="color: var(--kb-muted-foreground);">{{ cat.subtitle || kbSubtitles[idx % kbSubtitles.length] }}</p>
              </div>
            </div>
            <p class="text-xs line-clamp-2 mb-4" style="color: var(--kb-muted-foreground);">{{ cat.description || `系统化学习${cat.name}相关知识，循序渐进掌握技能。` }}</p>
            <div class="flex items-center justify-between pt-3" style="border-top: 1px solid var(--kb-border);">
              <div class="flex items-center gap-3 text-xs" style="color: var(--kb-muted-foreground);">
                <span class="inline-flex items-center gap-1"><Icon name="file-text" :size="12" />{{ cat.docCount || 0 }} 篇</span>
                <span class="inline-flex items-center gap-1"><Icon name="bookmark" :size="12" />{{ cat.children?.length || 0 }} 章</span>
              </div>
              <span class="text-xs font-medium inline-flex items-center gap-0.5" style="color: var(--kb-primary);">进入<Icon name="chevron-right" :size="12" /></span>
            </div>
          </div>
        </a>
      </div>
      <p v-if="!loading && topCategories.length === 0" class="text-center py-10 text-sm" style="color: var(--kb-muted-foreground);">
        暂无知识库分类，请稍后再来
      </p>
    </section>

    <!-- ========== Section 3: 按分类浏览 ========== -->
    <section v-if="topCategories.length > 0" class="mb-10">
      <h2 class="kb-h3 mb-5">按分类浏览</h2>
      <div class="flex items-center flex-wrap gap-3">
        <button class="cat-chip" @click="goToCategory()">
          <Icon name="layout-grid" :size="16" style="color: var(--kb-primary);" />
          <span class="text-sm font-medium" style="color: var(--kb-foreground);">全部</span>
          <span class="cat-chip-count">{{ totalDocs }}</span>
        </button>
        <button
          v-for="(cat, idx) in topCategories"
          :key="cat.id"
          class="cat-chip"
          @click="goToCategory(cat.id)"
        >
          <Icon :name="getCategoryIcon(cat.icon)" :size="16" :style="{ color: kbColors[idx % kbColors.length] }" />
          <span class="text-sm font-medium" style="color: var(--kb-foreground);">{{ cat.name }}</span>
          <span class="cat-chip-count">{{ cat.docCount || 0 }}</span>
        </button>
      </div>
    </section>

    <!-- ========== Section 4: 精选文档（2列） ========== -->
    <section class="mb-10">
      <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <!-- 精选推荐 -->
        <div class="rounded-xl border overflow-hidden" style="background: var(--kb-card); border-color: var(--kb-border);">
          <div class="flex items-center justify-between px-5 py-4" style="border-bottom: 1px solid var(--kb-border);">
            <div class="flex items-center gap-2">
              <Icon name="star" :size="16" style="color: var(--kb-warning);" />
              <h3 class="text-sm font-semibold" style="color: var(--kb-foreground);">精选推荐</h3>
            </div>
            <router-link to="/docs?sortBy=hot" class="text-xs font-medium hover:opacity-80" style="color: var(--kb-primary);">更多</router-link>
          </div>
          <div class="px-5">
            <a
              v-for="(doc, idx) in featuredDocs"
              :key="doc.id"
              class="doc-list-item"
              @click.prevent="goToDoc(doc.id)"
            >
              <div class="w-9 h-9 rounded-lg flex items-center justify-center shrink-0" :style="{ background: `${docIconColors[idx % docIconColors.length]}14` }">
                <Icon name="file-text" :size="16" :style="{ color: docIconColors[idx % docIconColors.length] }" />
              </div>
              <div class="flex-1 min-w-0">
                <h4 class="doc-list-title text-sm font-semibold truncate" style="color: var(--kb-foreground);">{{ doc.title }}</h4>
                <p class="text-xs line-clamp-1 mt-1" style="color: var(--kb-muted-foreground);">{{ doc.summary || '暂无摘要' }}</p>
                <div class="flex items-center gap-2 mt-1.5">
                  <span class="text-xs px-1.5 py-0.5 rounded" :style="{ background: `${docIconColors[idx % docIconColors.length]}14`, color: docIconColors[idx % docIconColors.length] }">{{ doc.categoryName || '未分类' }}</span>
                  <span style="color: var(--kb-border);">·</span>
                  <span class="inline-flex items-center gap-0.5 text-xs" style="color: var(--kb-muted-foreground);"><Icon name="eye" :size="12" />{{ doc.viewCount || 0 }} 阅读</span>
                </div>
              </div>
            </a>
            <p v-if="!loading && featuredDocs.length === 0" class="py-8 text-center text-sm" style="color: var(--kb-muted-foreground);">暂无推荐文档</p>
          </div>
        </div>

        <!-- 最新更新 -->
        <div class="rounded-xl border overflow-hidden" style="background: var(--kb-card); border-color: var(--kb-border);">
          <div class="flex items-center justify-between px-5 py-4" style="border-bottom: 1px solid var(--kb-border);">
            <div class="flex items-center gap-2">
              <Icon name="clock" :size="16" style="color: var(--kb-primary);" />
              <h3 class="text-sm font-semibold" style="color: var(--kb-foreground);">最新更新</h3>
            </div>
            <router-link to="/docs?sortBy=new" class="text-xs font-medium hover:opacity-80" style="color: var(--kb-primary);">更多</router-link>
          </div>
          <div class="px-5">
            <a
              v-for="(doc, idx) in latestDocs"
              :key="doc.id"
              class="doc-list-item"
              @click.prevent="goToDoc(doc.id)"
            >
              <div class="w-9 h-9 rounded-lg flex items-center justify-center shrink-0" :style="{ background: `${docIconColors[idx % docIconColors.length]}14` }">
                <Icon name="file-text" :size="16" :style="{ color: docIconColors[idx % docIconColors.length] }" />
              </div>
              <div class="flex-1 min-w-0">
                <h4 class="doc-list-title text-sm font-semibold truncate" style="color: var(--kb-foreground);">{{ doc.title }}</h4>
                <p class="text-xs line-clamp-1 mt-1" style="color: var(--kb-muted-foreground);">{{ doc.summary || '暂无摘要' }}</p>
                <div class="flex items-center gap-2 mt-1.5">
                  <span class="text-xs px-1.5 py-0.5 rounded" :style="{ background: `${docIconColors[idx % docIconColors.length]}14`, color: docIconColors[idx % docIconColors.length] }">{{ doc.categoryName || '未分类' }}</span>
                  <span style="color: var(--kb-border);">·</span>
                  <span class="inline-flex items-center gap-0.5 text-xs" style="color: var(--kb-muted-foreground);"><Icon name="clock" :size="12" />{{ formatRelativeTime(doc.createTime) }}</span>
                </div>
              </div>
            </a>
            <p v-if="!loading && latestDocs.length === 0" class="py-8 text-center text-sm" style="color: var(--kb-muted-foreground);">暂无最新文档</p>
          </div>
        </div>
      </div>
    </section>

    <!-- ========== Section 5: 底部 CTA ========== -->
    <section class="mb-6">
      <div class="rounded-2xl border px-6 py-8 lg:px-8 lg:py-10 flex flex-col items-center text-center" style="background: var(--kb-card); border-color: var(--kb-border);">
        <div class="w-12 h-12 rounded-xl flex items-center justify-center mb-4" style="background: rgba(59,111,224,0.08);">
          <Icon name="help-circle" :size="24" style="color: var(--kb-primary);" />
        </div>
        <h3 class="kb-h3 mb-2">找不到需要的知识？</h3>
        <p class="text-sm mb-6" style="color: var(--kb-muted-foreground);">
          上传你的文档，或管理现有知识库，让团队的知识沉淀更有序
        </p>
        <div class="flex items-center gap-3 flex-wrap justify-center">
          <router-link to="/admin/upload" class="btn-primary">
            <Icon name="upload" :size="16" />
            上传你的文档
          </router-link>
          <router-link to="/admin/knowledge" class="btn-secondary">
            <Icon name="settings-2" :size="16" />
            管理知识库
          </router-link>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
// 知识库首页：Hero 搜索 + 知识库卡片网格 + 分类导航 + 双列精选文档 + 底部 CTA
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { categoriesApi, docsApi } from '@/api'
import type { CategoryVO, DocVO } from '@/api/types'

const router = useRouter()

const loading = ref(false)
const searchKeyword = ref('')
const topCategories = ref<CategoryVO[]>([])
const featuredDocs = ref<DocVO[]>([])
const latestDocs = ref<DocVO[]>([])

// 知识库主色板（与设计稿一致：蓝/绿/橙/红/紫/灰）
const kbColors = ['#3B6FE0', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6', '#6B7280']
const kbSubtitles = [
  '前端 / 框架 / 工程化',
  '后端 / 服务 / 数据库',
  '数据分析 / 可视化',
  '机器学习 / 深度学习',
  '设计系统 / UX / 交互',
  '算法 / 操作系统 / 网络',
]

const docIconColors = ['#EF4444', '#3B6FE0', '#8B5CF6', '#10B981', '#F59E0B', '#6B7280']

const hotTags = [
  { name: '前端开发' },
  { name: '后端技术' },
  { name: '数据科学' },
  { name: '人工智能' },
  { name: '产品设计' },
]

const totalDocs = computed(() => topCategories.value.reduce((sum, c) => sum + (c.docCount || 0), 0))

const validIcons = [
  'code', 'server', 'database', 'brain', 'settings', 'monitor', 'wifi', 'layers',
  'book-open', 'folder', 'shield', 'git-branch', 'message-square', 'target',
  'bar-chart-2', 'palette', 'briefcase', 'cpu', 'bot', 'lock',
  'layout', 'binary',
]

const getCategoryIcon = (iconName?: string): string => {
  return validIcons.includes(iconName || '') ? iconName! : 'folder'
}

const goSearch = (kw?: string) => {
  router.push({ path: '/search', query: { q: kw || searchKeyword.value } })
}

const goToCategory = (id?: number) => {
  if (id) {
    router.push({ path: '/categories', query: { categoryId: String(id) } })
  } else {
    router.push('/categories')
  }
}

const goToDoc = (id: number) => {
  router.push(`/doc/${id}`)
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
  loading.value = true
  try {
    const [cats, hot, recent] = await Promise.all([
      categoriesApi.tree(),
      docsApi.list({ pageSize: 6, sortBy: 'hot' } as any).then((r) => r.records || []),
      docsApi.list({ pageSize: 6, sortBy: 'new' } as any).then((r) => r.records || []),
    ])
    topCategories.value = cats.slice(0, 6)
    featuredDocs.value = hot.slice(0, 5)
    latestDocs.value = recent.slice(0, 5)
  } catch {
    topCategories.value = []
    featuredDocs.value = []
    latestDocs.value = []
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.animate-fade-in {
  animation: fadeIn 0.4s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.line-clamp-1 {
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.line-clamp-2 {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* Hero search input */
.kb-search-input {
  width: 100%;
  height: 52px;
  padding-left: 48px;
  padding-right: 80px;
  border-radius: var(--kb-radius-lg);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  font-size: 15px;
  color: var(--kb-foreground);
  outline: none;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  transition: border-color 0.15s, box-shadow 0.15s;
}
.kb-search-input::placeholder { color: var(--kb-muted-foreground); }
.kb-search-input:focus {
  border-color: var(--kb-primary);
  box-shadow: 0 0 0 3px rgba(59, 111, 224, 0.12);
}

/* Hot tag chip */
.kb-tag-chip {
  display: inline-flex;
  align-items: center;
  padding: 4px 12px;
  border-radius: 9999px;
  font-size: 12px;
  font-weight: 500;
  background: var(--kb-muted);
  color: var(--kb-card-foreground);
  border: none;
  cursor: pointer;
  transition: background-color 0.15s, color 0.15s;
}
.kb-tag-chip:hover {
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
}

/* KB card item */
.kb-card-item {
  display: block;
  border-radius: var(--kb-radius-lg);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  overflow: hidden;
  cursor: pointer;
  transition: box-shadow 0.2s, transform 0.2s, border-color 0.2s;
}
.kb-card-item:hover {
  box-shadow: 0 8px 24px rgba(59, 111, 224, 0.12);
  transform: translateY(-2px);
  border-color: var(--kb-primary);
}
.kb-card-icon {
  width: 44px;
  height: 44px;
  border-radius: var(--kb-radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

/* Category chip */
.cat-chip {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 18px;
  border-radius: var(--kb-radius-md);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.cat-chip:hover {
  border-color: var(--kb-primary);
  box-shadow: 0 2px 8px rgba(59, 111, 224, 0.08);
}
.cat-chip-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 24px;
  height: 18px;
  padding: 0 6px;
  border-radius: 9999px;
  font-size: 11px;
  font-weight: 600;
  background: var(--kb-muted);
  color: var(--kb-muted-foreground);
}

/* Doc list item */
.doc-list-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid var(--kb-border);
  cursor: pointer;
  transition: background-color 0.15s;
}
.doc-list-item:last-child { border-bottom: none; }
.doc-list-item:hover .doc-list-title { color: var(--kb-primary); }
.doc-list-title { transition: color 0.15s; }

/* Buttons */
.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 18px;
  border-radius: var(--kb-radius-sm);
  font-size: 13px;
  font-weight: 600;
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  text-decoration: none;
  border: none;
  cursor: pointer;
  transition: opacity 0.15s;
}
.btn-primary:hover { opacity: 0.9; }

.btn-secondary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 18px;
  border-radius: var(--kb-radius-sm);
  font-size: 13px;
  font-weight: 600;
  background: var(--kb-card);
  color: var(--kb-primary);
  border: 1px solid var(--kb-border);
  text-decoration: none;
  cursor: pointer;
  transition: background-color 0.15s;
}
.btn-secondary:hover { background: var(--kb-muted); }
</style>
