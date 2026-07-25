<template>
  <div class="animate-fade-in max-w-[1000px] mx-auto">
    <!-- 页面大搜索框（主搜索区） -->
    <div class="mb-8">
      <div v-if="!hasSearched" class="text-center pt-12 pb-8">
        <h1 class="text-[32px] font-bold text-gray-800 mb-2">知识搜索</h1>
        <p class="text-[14px] text-gray-500 mb-8">输入关键词，在知识库中快速找到你需要的内容</p>
      </div>
      <div
        class="flex items-stretch rounded-2xl overflow-hidden border-2 transition-all"
        :class="hasSearched ? 'border-gray-200 shadow-sm' : 'border-primary-500 shadow-lg shadow-primary-500/10'"
      >
        <div class="flex-1 flex items-center bg-white">
          <Icon name="search" :size="20" class="ml-5 text-gray-400 shrink-0" />
          <input
            v-model="searchQuery"
            type="text"
            :placeholder="hasSearched ? '输入关键词继续搜索...' : '搜索知识库、文档、笔记...'"
            class="flex-1 px-4 py-4 text-[15px] bg-transparent text-gray-800 placeholder-gray-400 focus:outline-none"
            @keyup.enter="handleSearch"
            aria-label="搜索"
          />
          <button
            v-if="searchQuery"
            class="mr-3 text-gray-400 hover:text-gray-600 transition-colors w-8 h-8 rounded-full flex items-center justify-center hover:bg-gray-100"
            @click="clearInput"
            aria-label="清空输入"
          >
            <Icon name="x" :size="16" />
          </button>
        </div>
        <button
          class="px-8 bg-primary-500 hover:bg-primary-600 text-white text-[14px] font-medium transition-colors"
          @click="handleSearch"
        >
          搜索
        </button>
      </div>
      <div v-if="hasSearched && searchQuery" class="mt-3 flex items-center justify-between">
        <p class="text-[13px] text-gray-500">
          找到 <span class="font-semibold text-gray-700">{{ filteredResults.length }}</span> 个相关结果
          <span class="mx-2 text-gray-300">|</span>
          用时 <span class="text-gray-700">{{ searchTime.toFixed(1) }}</span> 秒
        </p>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div v-if="hasSearched && searchQuery" class="mb-5 flex items-center justify-between flex-wrap gap-3">
      <div class="flex items-center gap-2">
        <button
          v-for="tab in filterTabs"
          :key="tab.key"
          class="px-3.5 py-1.5 rounded-full text-[13px] transition-colors"
          :class="activeTab === tab.key
            ? 'bg-primary-500 text-white'
            : 'bg-white border border-[#E2E6EC] text-gray-600 hover:border-primary-300 hover:text-primary-600'"
          @click="activeTab = tab.key"
        >
          {{ tab.label }}
          <span v-if="tab.count !== undefined" class="ml-1">({{ tab.count }})</span>
        </button>
      </div>
      <div class="flex items-center gap-1 text-[13px]">
        <span class="text-gray-500 mr-1">排序：</span>
        <button
          v-for="opt in sortOptions"
          :key="opt.key"
          class="px-2.5 py-1 rounded-md transition-colors"
          :class="activeSort === opt.key
            ? 'text-primary-600 font-medium'
            : 'text-gray-500 hover:text-gray-700'"
          @click="activeSort = opt.key"
        >
          {{ opt.label }}
        </button>
      </div>
    </div>

    <!-- 空状态：未搜索 -->
    <div v-if="!hasSearched" class="text-center pb-20">
      <div class="grid grid-cols-2 md:grid-cols-3 gap-3 max-w-lg mx-auto">
        <button
          v-for="tag in hotTags"
          :key="tag"
          class="px-4 py-2.5 rounded-xl text-[13px] bg-white border border-gray-200 text-gray-600 hover:border-primary-300 hover:text-primary-600 hover:bg-primary-50/30 transition-all"
          @click="searchTag(tag)"
        >
          {{ tag }}
        </button>
      </div>
      <p class="text-[12px] text-gray-400 mt-6">热门搜索</p>
    </div>

    <!-- 加载中 -->
    <div v-else-if="loading" class="text-center py-16 text-gray-400">
      搜索中...
    </div>

    <!-- 无结果 -->
    <div v-else-if="filteredResults.length === 0" class="text-center py-16">
      <div class="w-16 h-16 mx-auto mb-4 rounded-2xl bg-gray-50 flex items-center justify-center">
        <Icon name="file-question" :size="28" class="text-gray-400" />
      </div>
      <p class="text-gray-600 mb-1">未找到相关结果</p>
      <p class="text-[13px] text-gray-400">试试其他关键词或调整筛选条件</p>
    </div>

    <!-- 搜索结果列表 -->
    <div v-else class="space-y-3">
      <div
        v-for="doc in filteredResults"
        :key="doc.id"
        class="bg-white border border-[#E2E6EC] rounded-2xl p-5 hover:border-primary-300 hover:shadow-sm transition-all cursor-pointer group"
        @click="goToDoc(doc.id)"
      >
        <div class="flex items-start justify-between gap-4">
          <div class="flex-1 min-w-0">
            <div class="flex items-center gap-2.5 mb-2">
              <span
                class="shrink-0 inline-flex items-center px-2 py-0.5 rounded text-[11px] font-medium"
                :class="getTypeStyle(doc)"
              >
                {{ getTypeLabel(doc) }}
              </span>
              <h3
                class="text-[15px] font-medium text-gray-800 truncate group-hover:text-primary-600 transition-colors"
                v-html="highlightKeyword(doc.title)"
              ></h3>
            </div>

            <p
              class="text-[13px] text-gray-500 mb-3 line-clamp-2 leading-relaxed"
              v-html="highlightKeyword(doc.summary || '')"
            ></p>

            <div class="flex items-center gap-1 text-[12px] text-gray-500 mb-2.5">
              <Icon name="folder" :size="12" class="shrink-0" />
              <span class="truncate">{{ doc.categoryName || '未分类' }}</span>
            </div>

            <div class="flex items-center gap-4 text-[12px] text-gray-400">
              <div class="flex items-center gap-1">
                <Icon name="calendar" :size="12" />
                <span>{{ formatDate(doc.createTime) }}</span>
              </div>
              <div class="flex items-center gap-1">
                <Icon name="book-open" :size="12" />
                <span>已读 {{ getReadProgress(doc) }}%</span>
              </div>
              <div class="flex items-center gap-1">
                <Icon name="clock" :size="12" />
                <span>约 {{ getReadTime(doc) }} 分钟</span>
              </div>
            </div>
          </div>

          <button
            class="shrink-0 w-8 h-8 rounded-lg flex items-center justify-center transition-colors"
            :class="isFavorited(doc)
              ? 'text-warning-500'
              : 'text-gray-300 hover:text-gray-500 hover:bg-gray-50'"
            @click.stop="toggleFavorite(doc)"
            :aria-label="isFavorited(doc) ? '取消收藏' : '收藏'"
          >
            <Icon :name="isFavorited(doc) ? 'bookmark' : 'bookmark'" :size="16" :fill="isFavorited(doc)" />
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { docsApi } from '@/api'
import type { DocVO } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { notify } from '@/utils/toast'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const searchQuery = ref('')
const activeTab = ref('all')
const activeSort = ref('relevance')
const loading = ref(false)
const hasSearched = ref(false)
const searchTime = ref(0)
const allResults = ref<DocVO[]>([])
const favoritedIds = ref<Set<number>>(new Set())

const hotTags = ['Vue 3', 'TypeScript', 'React Hooks', '前端性能优化', '设计模式', '算法入门']

const filterTabs = computed(() => [
  { key: 'all', label: '全部', count: allResults.value.length },
  { key: 'doc', label: '文档', count: allResults.value.filter(d => !d.tags?.includes('笔记')).length },
  { key: 'note', label: '笔记', count: allResults.value.filter(d => d.tags?.includes('笔记')).length },
  { key: 'favorite', label: '收藏', count: allResults.value.filter(d => favoritedIds.value.has(d.id)).length },
])

const sortOptions = [
  { key: 'relevance', label: '按相关度' },
  { key: 'time', label: '按时间' },
  { key: 'progress', label: '按阅读进度' },
]

const filteredResults = computed(() => {
  let results = [...allResults.value]

  if (activeTab.value === 'doc') {
    results = results.filter(d => !d.tags?.includes('笔记'))
  } else if (activeTab.value === 'note') {
    results = results.filter(d => d.tags?.includes('笔记'))
  } else if (activeTab.value === 'favorite') {
    results = results.filter(d => favoritedIds.value.has(d.id))
  }

  if (activeSort.value === 'time') {
    results.sort((a, b) => new Date(b.createTime || 0).getTime() - new Date(a.createTime || 0).getTime())
  } else if (activeSort.value === 'progress') {
    results.sort((a, b) => (b.readCount || 0) - (a.readCount || 0))
  }

  return results
})

const highlightKeyword = (text: string) => {
  if (!searchQuery.value.trim()) return text || ''
  const regex = new RegExp(`(${searchQuery.value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')})`, 'gi')
  return (text || '').replace(regex, '<span class="text-primary-600 font-semibold">$1</span>')
}

const doSearch = async (keyword: string) => {
  if (!keyword.trim()) {
    allResults.value = []
    hasSearched.value = false
    return
  }
  loading.value = true
  hasSearched.value = true
  const startTime = Date.now()
  try {
    const res = await docsApi.list({ keyword, pageSize: 100 })
    allResults.value = res.records || []
    favoritedIds.value = new Set(
      allResults.value.filter(d => d.favoriteCount && d.favoriteCount > 0).map(d => d.id)
    )
    router.replace({ path: '/search', query: { q: keyword } })
  } catch {
    allResults.value = []
  } finally {
    searchTime.value = (Date.now() - startTime) / 1000
    loading.value = false
  }
}

const handleSearch = () => {
  doSearch(searchQuery.value)
}

const searchTag = (tag: string) => {
  searchQuery.value = tag
  doSearch(tag)
}

const clearInput = () => {
  searchQuery.value = ''
}

const goToDoc = (docId: number) => {
  router.push(`/doc/${docId}`)
}

const getTypeLabel = (doc: DocVO) => {
  const tags = (doc.tags || '').toLowerCase()
  if (tags.includes('pdf')) return 'PDF'
  if (tags.includes('markdown') || tags.includes('md')) return 'Markdown'
  if (tags.includes('笔记') || tags.includes('note')) return 'Note'
  return '文档'
}

const getTypeStyle = (doc: DocVO) => {
  const tags = (doc.tags || '').toLowerCase()
  if (tags.includes('pdf')) return 'bg-red-50 text-red-600'
  if (tags.includes('markdown') || tags.includes('md')) return 'bg-blue-50 text-blue-600'
  if (tags.includes('笔记') || tags.includes('note')) return 'bg-green-50 text-green-600'
  return 'bg-gray-100 text-gray-600'
}

const getReadProgress = (doc: DocVO) => {
  const wordCount = doc.wordCount || 1
  const read = doc.readCount || 0
  return Math.min(100, Math.round((read / wordCount) * 100))
}

const getReadTime = (doc: DocVO) => {
  return Math.max(1, Math.round((doc.wordCount || 0) / 300))
}

const isFavorited = (doc: DocVO) => favoritedIds.value.has(doc.id)

const toggleFavorite = (doc: DocVO) => {
  if (!auth.isLoggedIn) {
    notify('请先登录', 'warning')
    return
  }
  if (favoritedIds.value.has(doc.id)) {
    favoritedIds.value.delete(doc.id)
    notify('已取消收藏', 'info')
  } else {
    favoritedIds.value.add(doc.id)
    notify('已收藏', 'success')
  }
}

const formatDate = (dateStr?: string) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).replace(/\//g, '-')
}

onMounted(async () => {
  const query = route.query.q as string
  if (query) {
    searchQuery.value = query
    await doSearch(query)
  }
})

watch(
  () => route.query.q,
  (newQ) => {
    if (newQ && typeof newQ === 'string' && newQ !== searchQuery.value) {
      searchQuery.value = newQ
      doSearch(newQ)
    }
  }
)
</script>

<style scoped>
.animate-fade-in {
  animation: fadeIn 0.4s ease-out;
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

.line-clamp-2 {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
