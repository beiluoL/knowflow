<template>
  <div class="animate-fade-in">
    <div class="flex gap-6">
      <aside class="hidden md:block w-64 flex-shrink-0">
        <Card class="sticky top-6">
          <template #header>
            <h3 class="font-medium text-gray-800 flex items-center gap-2">
              <Icon name="folder-tree" :size="16" />
              分类导航
            </h3>
          </template>
          <div class="py-2">
            <div
              v-for="category in categoryTree" :key="category.id"
              class="category-item"
            >
              <div
                :class="[
                  'flex items-center gap-2 px-3 py-2 cursor-pointer rounded-md transition-all duration-200',
                  isActive(category.id)
                    ? 'bg-primary-50 text-primary-600'
                    : 'text-gray-700 hover:bg-gray-50',
                ]"
                @click="toggleCategory(category.id)"
              >
                <Icon name="chevron-down" :size="20" v-if="category.children && category.children.length> 0"
                  :class="[
                    'w-4 h-4 transition-transform duration-200 flex-shrink-0',
                    expandedCategories.includes(category.id) ? '' : '-rotate-90',
                  ]"
                />
                <span v-else class="w-4 flex-shrink-0"></span>
                <Icon
                  :name="getCategoryIconName(category.icon || 'folder')"
                  :size="16"
                  class="flex-shrink-0"
                />
                <span class="text-sm flex-1 truncate">{{ category.name }}</span>
                <span class="text-xs text-gray-400">{{ category.docCount || 0 }}</span>
              </div>
              <div
                v-if="category.children && category.children.length > 0 && expandedCategories.includes(category.id)"
                class="ml-2 border-l border-gray-100 pl-2"
              >
                <div
                  v-for="child in category.children" :key="child.id"
                  class="category-item"
                >
                  <div
                    :class="[
                      'flex items-center gap-2 px-3 py-1.5 cursor-pointer rounded-md transition-all duration-200',
                      isActive(child.id)
                        ? 'bg-primary-50 text-primary-600'
                        : 'text-gray-600 hover:bg-gray-50',
                    ]"
                    @click="selectCategory(child.id)"
                  >
                    <Icon name="chevron-down" :size="20" v-if="child.children && child.children.length> 0"
                      :class="[
                        'w-3.5 h-3.5 transition-transform duration-200 flex-shrink-0',
                        expandedCategories.includes(child.id) ? '' : '-rotate-90',
                      ]"
                      @click.stop="toggleCategory(child.id)"
                    />
                    <span v-else class="w-3.5 flex-shrink-0"></span>
                    <span class="text-sm flex-1 truncate">{{ child.name }}</span>
                    <span class="text-xs text-gray-400">{{ child.docCount || 0 }}</span>
                  </div>
                  <div
                    v-if="child.children && child.children.length > 0 && expandedCategories.includes(child.id)"
                    class="ml-2 border-l border-gray-100 pl-2"
                  >
                    <div
                      v-for="grandchild in child.children" :key="grandchild.id"
                      :class="[
                        'flex items-center gap-2 px-3 py-1.5 cursor-pointer rounded-md transition-all duration-200',
                        isActive(grandchild.id)
                          ? 'bg-primary-50 text-primary-600'
                          : 'text-gray-500 hover:bg-gray-50',
                      ]"
                      @click="selectCategory(grandchild.id)"
                    >
                      <span class="w-3.5 flex-shrink-0"></span>
                      <span class="text-sm flex-1 truncate">{{ grandchild.name }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </Card>
      </aside>

      <div class="flex-1 min-w-0">
        <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
          <div>
            <h1 class="text-2xl font-bold text-gray-800 mb-1">
              {{ currentCategoryName || '全部分类' }}
            </h1>
            <p class="text-sm text-gray-500">
              <span v-if="loading">加载中...</span>
              <span v-else>共 {{ sortedDocs.length }} 篇文档</span>
            </p>
          </div>
          <div class="flex items-center gap-3">
            <div class="flex items-center bg-gray-100 rounded-md p-1">
              <button
                :class="[
                  'px-3 py-1.5 text-sm rounded transition-all duration-200',
                  sortBy === 'latest'
                    ? 'bg-white text-gray-800 shadow-sm'
                    : 'text-gray-500 hover:text-gray-700',
                ]"
                @click="sortBy = 'latest'"
              >
                最新
              </button>
              <button
                :class="[
                  'px-3 py-1.5 text-sm rounded transition-all duration-200',
                  sortBy === 'hot'
                    ? 'bg-white text-gray-800 shadow-sm'
                    : 'text-gray-500 hover:text-gray-700',
                ]"
                @click="sortBy = 'hot'"
              >
                最热
              </button>
            </div>
          </div>
        </div>

        <div v-if="!loading && sortedDocs.length === 0" class="text-center py-16">
          <Icon name="file-question" :size="64" />
          <p class="text-gray-500">暂无文档</p>
        </div>

        <div v-else class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
          <Card
            v-for="doc in sortedDocs" :key="doc.id"
            hoverable
            class="cursor-pointer group"
            @click="goToDoc(doc.id)"
          >
            <div class="flex flex-col h-full">
              <div class="flex items-center gap-2 mb-3">
                <Badge variant="primary">{{ doc.categoryName }}</Badge>
                <span class="text-xs text-gray-400 ml-auto">
                  {{ formatDate(doc.createTime) }}
                </span>
              </div>
              <h3
                class="font-medium text-gray-800 mb-2 line-clamp-2 group-hover:text-primary-500 transition-colors"
              >
                {{ doc.title }}
              </h3>
              <p class="text-sm text-gray-500 mb-4 line-clamp-2 flex-1">
                {{ doc.summary }}
              </p>
              <div class="flex items-center gap-2 flex-wrap mb-3">
                <Badge v-for="tag in (doc.tags || '').split(',').filter(Boolean).slice(0, 3)" :key="tag" variant="default">
                  {{ tag }}
                </Badge>
              </div>
              <div class="flex items-center justify-between pt-3 border-t border-gray-50">
                <div class="flex items-center gap-1 text-xs text-gray-400">
                  <Icon name="eye" :size="20" />
                  <span>{{ doc.viewCount || 0 }}</span>
                </div>
                <div class="flex items-center gap-3 text-xs text-gray-400">
                  <span class="flex items-center gap-1">
                    <Icon name="heart" :size="20" />
                    {{ doc.favoriteCount || 0 }}
                  </span>
                </div>
              </div>
            </div>
          </Card>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import Card from '@/components/ui/Card.vue'
import Badge from '@/components/ui/Badge.vue'
import { categoriesApi, docsApi } from '@/api'
import type { CategoryVO, DocVO } from '@/api/types'

const route = useRoute()
const router = useRouter()

const categoryTree = ref<CategoryVO[]>([])
const docs = ref<DocVO[]>([])
const loading = ref(false)
const activeCategoryId = ref<number | string>('')
const expandedCategories = ref<(number | string)[]>([])
const sortBy = ref<'latest' | 'hot'>('latest')

const iconOptions = ['code', 'server', 'database', 'brain', 'settings', 'git-branch', 'monitor', 'wifi', 'folder']

const getCategoryIconName = (iconName: string): string => {
  return iconOptions.includes(iconName) ? iconName : 'folder'
}

const isActive = (id: number) => activeCategoryId.value === id

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

const currentCategoryName = computed(() => {
  if (!activeCategoryId.value) return ''
  const cat = findCategoryById(activeCategoryId.value, categoryTree.value)
  return cat?.name || ''
})

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
  const result = [...docs.value]
  if (sortBy.value === 'latest') {
    return result.sort(
      (a, b) => new Date(b.createTime || '').getTime() - new Date(a.createTime || '').getTime()
    )
  }
  return result.sort((a, b) => (b.viewCount || 0) - (a.viewCount || 0))
})

const loadDocs = async () => {
  loading.value = true
  try {
    if (!activeCategoryId.value) {
      const res = await docsApi.list({ pageSize: 100 })
      docs.value = res.records || []
    } else {
      const ids = getAllChildCategoryIds(activeCategoryId.value)
      let merged: DocVO[] = []
      for (const id of ids) {
        const res = await docsApi.list({ categoryId: id, pageSize: 100 })
        merged = merged.concat(res.records || [])
      }
      docs.value = [...new Map(merged.map((d) => [d.id, d])).values()]
    }
  } catch {
    docs.value = []
  } finally {
    loading.value = false
  }
}

const toggleCategory = (categoryId: number) => {
  const index = expandedCategories.value.indexOf(categoryId)
  if (index > -1) {
    expandedCategories.value.splice(index, 1)
  } else {
    expandedCategories.value.push(categoryId)
  }
  selectCategory(categoryId)
}

const selectCategory = (categoryId: number) => {
  activeCategoryId.value = categoryId
  router.push({ path: '/categories', query: { categoryId: String(categoryId) } })
}

const goToDoc = (docId: number) => {
  router.push(`/doc/${docId}`)
}

const formatDate = (dateStr?: string) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', {
    month: 'short',
    day: 'numeric',
  })
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
    const cat = findCategoryById(categoryId, categoryTree.value)
    if (cat?.parentId) {
      if (!expandedCategories.value.includes(cat.parentId)) {
        expandedCategories.value.push(cat.parentId)
      }
    }
  }
  await loadDocs()
})

watch(
  () => route.query.categoryId,
  (newCategoryId) => {
    if (newCategoryId && typeof newCategoryId === 'string') {
      activeCategoryId.value = Number(newCategoryId)
    } else {
      activeCategoryId.value = ''
    }
    loadDocs()
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
