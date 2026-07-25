<template>
  <div class="space-y-6 animate-fade-in">
    <PageHeader
      :crumbs="[{ label: '知识库' }, { label: '收藏夹' }]"
      title="收藏夹"
      :count="total"
    >
      <template #actions>
        <button
          @click="toggleBatchMode"
          class="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-medium border text-gray-700 border-gray-200 bg-white hover:bg-gray-50 transition-colors"
        >
          <Icon name="check-square" :size="16" />
          <span>{{ batchMode ? '取消' : '批量管理' }}</span>
        </button>
      </template>
    </PageHeader>

    <section class="flex items-center gap-2 flex-nowrap overflow-x-auto no-scrollbar">
      <button
        v-for="tab in typeTabs"
        :key="tab.value"
        @click="handleTypeChange(tab.value)"
        :class="[
          'shrink-0 inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm border whitespace-nowrap transition-colors',
          selectedType === tab.value
            ? 'bg-primary-500 text-white border-primary-500'
            : 'bg-white text-gray-700 border-gray-200 hover:border-gray-300',
        ]"
      >
        <span>{{ tab.label }}</span>
        <span :class="selectedType === tab.value ? 'opacity-80' : 'text-gray-400'">
          {{ tab.count ?? 0 }}
        </span>
      </button>
    </section>

    <SkeletonList v-if="loading" :rows="6" type="card" :cols="3" />

    <template v-else>
      <EmptyState v-if="favoriteItems.length === 0" icon="heart" title="暂无收藏">
        <p class="text-sm text-gray-500">去逛逛知识库，收藏感兴趣的内容吧</p>
      </EmptyState>

      <section v-else class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
        <div
          v-for="item in favoriteItems"
          :key="item.id"
          class="group relative border rounded-[10px] p-4 flex flex-col bg-white border-gray-200 hover:shadow-md transition-shadow cursor-pointer"
          @click="handleItemClick(item)"
        >
          <div class="absolute top-3 right-3 flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
            <button
              @click.stop="handleUnfavorite(item.id)"
              class="p-1 rounded text-danger-500 hover:bg-red-50"
              :aria-label="`取消收藏 ${item.title}`"
            >
              <Icon name="heart" :size="14" />
            </button>
            <button
              class="p-1 rounded text-gray-400 hover:bg-gray-100"
              :aria-label="`分享 ${item.title}`"
            >
              <Icon name="share-2" :size="14" />
            </button>
          </div>
          <div class="flex items-center gap-2.5 mb-3">
            <span
              class="shrink-0 w-8 h-8 rounded-full flex items-center justify-center bg-blue-50 text-primary-500"
            >
              <Icon name="file-text" :size="16" />
            </span>
          </div>
          <p class="text-[15px] font-semibold line-clamp-1 mb-1.5 pr-16 text-gray-800">
            {{ item.title }}
          </p>
          <p class="text-xs truncate mb-1 text-gray-500">
            来自: {{ item.categoryName || '未分类' }}
          </p>
          <p class="text-xs text-gray-500">
            收藏于 {{ formatTime(item.favoriteTime) }}
          </p>
        </div>
      </section>
    </template>

    <Pagination
      v-if="total > 0"
      :page-num="pageNum"
      :page-size="pageSize"
      :total="total"
      @change="handlePageChange"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import Pagination from '@/components/ui/Pagination.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import SkeletonList from '@/components/ui/SkeletonList.vue'
import { docsApi } from '@/api/docs'
import { confirmDialog } from '@/utils/toast'
import type { DocVO } from '@/api/types'

const router = useRouter()
const loading = ref(false)
const favoriteItems = ref<DocVO[]>([])
const selectedType = ref('doc')
const pageNum = ref(1)
const pageSize = ref(12)
const total = ref(0)
const batchMode = ref(false)

const typeTabs = computed(() => [
  { value: 'doc', label: '文档', count: total.value },
])

async function fetchFavorites() {
  loading.value = true
  try {
    const data = await docsApi.favorites()
    favoriteItems.value = data
    total.value = data.length
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

function handleTypeChange(type: string) {
  selectedType.value = type
  pageNum.value = 1
  fetchFavorites()
}

function handlePageChange(page: number) {
  pageNum.value = page
  fetchFavorites()
}

function handleItemClick(item: DocVO) {
  router.push(`/docs/${item.id}`)
}

async function handleUnfavorite(id: number) {
  if (!(await confirmDialog('确定取消收藏吗？'))) return
  docsApi.toggleFavorite(id).then(() => {
    favoriteItems.value = favoriteItems.value.filter((f) => f.id !== id)
    total.value--
  })
}

function toggleBatchMode() {
  batchMode.value = !batchMode.value
}

function formatTime(time?: string): string {
  if (!time) return '-'
  const date = new Date(time)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const days = Math.floor(diff / (1000 * 60 * 60 * 24))
  if (days < 1) return '今天'
  if (days < 30) return `${days}天前`
  return date.toLocaleDateString()
}

onMounted(() => {
  fetchFavorites()
})
</script>

<style scoped>
.no-scrollbar::-webkit-scrollbar {
  display: none;
}
.no-scrollbar {
  -ms-overflow-style: none;
  scrollbar-width: none;
}
.animate-fade-in {
  animation: fadeIn 0.3s ease-out;
}
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}
.line-clamp-1 {
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
