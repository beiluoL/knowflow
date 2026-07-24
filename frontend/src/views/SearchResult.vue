<template>
  <div class="animate-fade-in max-w-4xl mx-auto">
    <div class="mb-8">
      <div class="relative">
        <Icon name="search" :size="20" />
        <input
          v-model="searchQuery"
          type="text"
          placeholder="搜索文档、标签..."
          class="w-full pl-12 pr-4 py-3.5 text-base border border-gray-200 rounded-lg focus:outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100 transition-all duration-200 bg-white"
          @keyup.enter="handleSearch"
        />
        <button
          v-if="searchQuery"
          class="absolute right-4 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 transition-colors"
          @click="clearSearch"
        >
          <Icon name="x" :size="20" />
        </button>
      </div>
    </div>

    <div v-if="searchQuery" class="mb-6">
      <p class="text-gray-600 mb-4">
        找到 <span class="font-semibold text-primary-600">{{ searchResults.length }}</span> 条关于
        <span class="font-semibold">「{{ searchQuery }}」</span> 的结果
      </p>

      <div class="flex items-center gap-3 flex-wrap">
        <span class="text-sm text-gray-500">筛选：</span>
        <button
          :class="[
            'px-3 py-1.5 text-sm rounded-full transition-all duration-200',
            activeCategory === ''
              ? 'bg-primary-500 text-white'
              : 'bg-gray-100 text-gray-600 hover:bg-gray-200',
          ]"
          @click="activeCategory = ''"
        >
          全部
        </button>
        <button
          v-for="cat in categoryFilters" :key="cat.id"
          :class="[
            'px-3 py-1.5 text-sm rounded-full transition-all duration-200',
            activeCategory === cat.id
              ? 'bg-primary-500 text-white'
              : 'bg-gray-100 text-gray-600 hover:bg-gray-200',
          ]"
          @click="activeCategory = cat.id"
        >
          {{ cat.name }}
        </button>
      </div>

      <div class="flex items-center gap-3 mt-3 flex-wrap">
        <span class="text-sm text-gray-500">类型：</span>
        <button
          :class="[
            'px-3 py-1.5 text-sm rounded-full transition-all duration-200',
            activeType === ''
              ? 'bg-primary-100 text-primary-700'
              : 'bg-gray-100 text-gray-600 hover:bg-gray-200',
          ]"
          @click="activeType = ''"
        >
          全部类型
        </button>
        <button
          :class="[
            'px-3 py-1.5 text-sm rounded-full transition-all duration-200',
            activeType === 'doc'
              ? 'bg-primary-100 text-primary-700'
              : 'bg-gray-100 text-gray-600 hover:bg-gray-200',
          ]"
          @click="activeType = 'doc'"
        >
          文档
        </button>
        <button
          :class="[
            'px-3 py-1.5 text-sm rounded-full transition-all duration-200',
            activeType === 'tag'
              ? 'bg-primary-100 text-primary-700'
              : 'bg-gray-100 text-gray-600 hover:bg-gray-200',
          ]"
          @click="activeType = 'tag'"
        >
          标签
        </button>
      </div>
    </div>

    <div v-if="!searchQuery" class="text-center py-16">
      <Icon name="search" :size="64" />
      <p class="text-gray-500 mb-2">输入关键词开始搜索</p>
      <p class="text-sm text-gray-400">支持搜索文档标题、摘要、标签</p>
    </div>

    <div v-else-if="loading" class="text-center py-16 text-gray-400">
      搜索中...
    </div>

    <div v-else-if="filteredResults.length === 0" class="text-center py-16">
      <Icon name="file-question" :size="64" />
      <p class="text-gray-500 mb-2">未找到相关结果</p>
      <p class="text-sm text-gray-400">试试其他关键词或调整筛选条件</p>
    </div>

    <div v-else class="space-y-4">
      <Card
        v-for="doc in filteredResults" :key="doc.id"
        hoverable
        class="cursor-pointer group"
        @click="goToDoc(doc.id)"
      >
        <div class="flex items-start gap-2 mb-2">
          <Badge variant="primary">{{ doc.categoryName }}</Badge>
          <span class="text-xs text-gray-400 ml-auto">
            {{ formatDate(doc.createTime) }}
          </span>
        </div>
        <h3
          class="text-lg font-medium text-gray-800 mb-2 group-hover:text-primary-500 transition-colors"
          v-html="highlightKeyword(doc.title)"
        ></h3>
        <p
          class="text-sm text-gray-600 mb-3 line-clamp-2 leading-relaxed"
          v-html="highlightKeyword(doc.summary || '')"
        ></p>
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2 flex-wrap">
            <Badge
              v-for="tag in (doc.tags || '').split(',').filter(Boolean).slice(0, 3)" :key="tag"
              variant="default"
              class="text-xs"
              v-html="highlightKeyword(tag)"
            ></Badge>
          </div>
          <div class="flex items-center gap-4 text-xs text-gray-400">
            <div class="flex items-center gap-1">
              <Icon name="eye" :size="20" />
              <span>{{ doc.viewCount || 0 }}</span>
            </div>
          </div>
        </div>
      </Card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import Card from '@/components/ui/Card.vue'
import Badge from '@/components/ui/Badge.vue'
import { docsApi, categoriesApi } from '@/api'
import type { CategoryVO, DocVO } from '@/api/types'

const route = useRoute()
const router = useRouter()

const searchQuery = ref('')
const activeCategory = ref<number | string>('')
const activeType = ref('')
const loading = ref(false)
const allResults = ref<DocVO[]>([])
const categoryFilters = ref<CategoryVO[]>([])

const searchResults = computed<DocVO[]>(() => allResults.value)

const filteredResults = computed(() => {
  let results = searchResults.value
  if (activeCategory.value !== '') {
    results = results.filter((doc) => doc.categoryId === Number(activeCategory.value))
  }
  if (activeType.value === 'tag' && searchQuery.value.trim()) {
    const q = searchQuery.value.trim().toLowerCase()
    results = results.filter((doc) => (doc.tags || '').toLowerCase().split(',').some((t) => t.trim().includes(q)))
  }
  return results
})

const highlightKeyword = (text: string) => {
  if (!searchQuery.value.trim()) return text || ''
  const regex = new RegExp(`(${searchQuery.value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')})`, 'gi')
  return (text || '').replace(regex, '<mark class="bg-yellow-200 text-yellow-900 px-0.5 rounded">$1</mark>')
}

const doSearch = async (keyword: string) => {
  if (!keyword.trim()) {
    allResults.value = []
    return
  }
  loading.value = true
  try {
    const res = await docsApi.list({ keyword, pageSize: 100 })
    allResults.value = res.records || []
  } catch {
    allResults.value = []
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  if (searchQuery.value.trim()) {
    router.push({ path: '/search', query: { q: searchQuery.value } })
  }
}

const clearSearch = () => {
  searchQuery.value = ''
  activeCategory.value = ''
  activeType.value = ''
  allResults.value = []
  router.push({ path: '/search' })
}

const goToDoc = (docId: number) => {
  router.push(`/doc/${docId}`)
}

const formatDate = (dateStr?: string) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })
}

onMounted(async () => {
  try {
    const tree = await categoriesApi.tree()
    categoryFilters.value = tree.filter((c) => !c.parentId || c.parentId === 0)
  } catch {
    categoryFilters.value = []
  }
  const query = route.query.q as string
  if (query) {
    searchQuery.value = query
    await doSearch(query)
  }
})

watch(
  () => route.query.q,
  (newQ) => {
    if (newQ && typeof newQ === 'string') {
      searchQuery.value = newQ
      doSearch(newQ)
    }
  }
)

watch([activeCategory, activeType], () => {
  /* 纯前端二次筛选，无需重新请求 */
})
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
