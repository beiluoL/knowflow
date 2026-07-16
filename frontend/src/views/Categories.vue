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
              v-for="category in categoryTree"
              :key="category.id"
              class="category-item"
            >
              <div
                :class="[
                  'flex items-center gap-2 px-3 py-2 cursor-pointer rounded-md transition-all duration-200',
                  activeCategoryId === category.id
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
                <span class="text-xs text-gray-400">{{ category.docCount }}</span>
              </div>
              <div
                v-if="category.children && category.children.length > 0 && expandedCategories.includes(category.id)"
                class="ml-2 border-l border-gray-100 pl-2"
              >
                <div
                  v-for="child in category.children"
                  :key="child.id"
                  class="category-item"
                >
                  <div
                    :class="[
                      'flex items-center gap-2 px-3 py-1.5 cursor-pointer rounded-md transition-all duration-200',
                      activeCategoryId === child.id
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
                    <span class="text-xs text-gray-400">{{ child.docCount }}</span>
                  </div>
                  <div
                    v-if="child.children && child.children.length > 0 && expandedCategories.includes(child.id)"
                    class="ml-2 border-l border-gray-100 pl-2"
                  >
                    <div
                      v-for="grandchild in child.children"
                      :key="grandchild.id"
                      :class="[
                        'flex items-center gap-2 px-3 py-1.5 cursor-pointer rounded-md transition-all duration-200',
                        activeCategoryId === grandchild.id
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
            <p class="text-sm text-gray-500">共 {{ filteredDocs.length }} 篇文档</p>
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

        <div v-if="filteredDocs.length === 0" class="text-center py-16">
          <Icon name="file-question" :size="64" />
          <p class="text-gray-500">暂无文档</p>
        </div>

        <div v-else class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
          <Card
            v-for="doc in sortedDocs"
            :key="doc.id"
            hoverable
            class="cursor-pointer group"
            @click="goToDoc(doc.id)"
          >
            <div class="flex flex-col h-full">
              <div class="flex items-center gap-2 mb-3">
                <Badge variant="primary">{{ doc.categoryName }}</Badge>
                <span class="text-xs text-gray-400 ml-auto">
                  {{ formatDate(doc.updatedAt) }}
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
                <Badge v-for="tag in doc.tags.slice(0, 3)" :key="tag" variant="default">
                  {{ tag }}
                </Badge>
              </div>
              <div class="flex items-center justify-between pt-3 border-t border-gray-50">
                <div class="flex items-center gap-2">
                  <Avatar :name="doc.author" size="sm" />
                  <span class="text-xs text-gray-500">{{ doc.author }}</span>
                </div>
                <div class="flex items-center gap-3 text-xs text-gray-400">
                  <span class="flex items-center gap-1">
                    <Icon name="eye" :size="20" />
                    {{ doc.viewCount }}
                  </span>
                  <span class="flex items-center gap-1">
                    <Icon name="heart" :size="20" />
                    {{ doc.likeCount }}
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
import Avatar from '@/components/ui/Avatar.vue'
import { categoryTree } from '@/data/categoryTree'
import { docs } from '@/data/docs'
import type { Category } from '@/types'

const route = useRoute()
const router = useRouter()

const activeCategoryId = ref<string>('')
const expandedCategories = ref<string[]>(['1', '2'])
const sortBy = ref<'latest' | 'hot'>('latest')

const iconOptions = ['code', 'server', 'database', 'brain', 'settings', 'git-branch', 'monitor', 'wifi', 'folder']

const getCategoryIconName = (iconName: string): string => {
  return iconOptions.includes(iconName) ? iconName : 'folder'
}

const findCategoryById = (id: string, categories: Category[]): Category | null => {
  for (const cat of categories) {
    if (cat.id === id) return cat
    if (cat.children) {
      const found = findCategoryById(id, cat.children)
      if (found) return found
    }
  }
  return null
}

const currentCategoryName = computed(() => {
  if (!activeCategoryId.value) return ''
  const cat = findCategoryById(activeCategoryId.value, categoryTree)
  return cat?.name || ''
})

const getAllChildCategoryIds = (categoryId: string): string[] => {
  const ids: string[] = [categoryId]
  const category = findCategoryById(categoryId, categoryTree)
  if (category?.children) {
    category.children.forEach((child) => {
      ids.push(...getAllChildCategoryIds(child.id))
    })
  }
  return ids
}

const filteredDocs = computed(() => {
  if (!activeCategoryId.value) return docs
  const categoryIds = getAllChildCategoryIds(activeCategoryId.value)
  return docs.filter((doc) => categoryIds.includes(doc.categoryId) || doc.categoryId === activeCategoryId.value)
})

const sortedDocs = computed(() => {
  const result = [...filteredDocs.value]
  if (sortBy.value === 'latest') {
    return result.sort(
      (a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime()
    )
  }
  return result.sort((a, b) => b.viewCount - a.viewCount)
})

const toggleCategory = (categoryId: string) => {
  const index = expandedCategories.value.indexOf(categoryId)
  if (index > -1) {
    expandedCategories.value.splice(index, 1)
  } else {
    expandedCategories.value.push(categoryId)
  }
  selectCategory(categoryId)
}

const selectCategory = (categoryId: string) => {
  activeCategoryId.value = categoryId
  router.push({ path: '/categories', query: { categoryId } })
}

const goToDoc = (docId: string) => {
  router.push(`/doc/${docId}`)
}

const formatDate = (dateStr: string) => {
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', {
    month: 'short',
    day: 'numeric',
  })
}

onMounted(() => {
  const categoryId = route.query.categoryId as string
  if (categoryId) {
    activeCategoryId.value = categoryId
    const cat = findCategoryById(categoryId, categoryTree)
    if (cat?.parentId) {
      if (!expandedCategories.value.includes(cat.parentId)) {
        expandedCategories.value.push(cat.parentId)
      }
    }
  }
})

watch(
  () => route.query.categoryId,
  (newCategoryId) => {
    if (newCategoryId && typeof newCategoryId === 'string') {
      activeCategoryId.value = newCategoryId
    } else {
      activeCategoryId.value = ''
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
