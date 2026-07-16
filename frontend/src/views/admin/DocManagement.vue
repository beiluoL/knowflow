<template>
  <AppShell>
    <div class="space-y-6 animate-fade-in">
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">文档管理</h1>
          <p class="text-gray-500 text-sm mt-1">管理知识库中的所有文档</p>
        </div>
      </div>

      <Card padding="none">
        <div class="p-4 border-b border-gray-100">
          <div class="flex flex-col sm:flex-row sm:items-center gap-4">
            <div class="flex-1">
              <Input
                v-model="searchQuery"
                placeholder="搜索文档标题、作者..."
                prefix-icon-name="search"
              />
            </div>
            <div class="flex items-center gap-3 flex-wrap">
              <div class="relative">
                <button
                  @click="showCategoryFilter = !showCategoryFilter"
                  class="inline-flex items-center gap-2 px-4 py-2 text-sm border border-gray-200 rounded-sm bg-white hover:bg-gray-50 transition-colors"
                >
                  <Icon name="filter" :size="16" />
                  <span>{{ selectedCategory || '全部分类' }}</span>
                  <Icon name="chevron-down" :size="16" />
                </button>
                <div
                  v-if="showCategoryFilter"
                  class="absolute top-full left-0 mt-1 w-48 bg-white border border-gray-200 rounded-sm shadow-lg z-10 py-1 animate-dropdown"
                >
                  <button
                    @click="selectedCategory = ''; showCategoryFilter = false"
                    class="w-full px-4 py-2 text-left text-sm hover:bg-gray-50 transition-colors"
                    :class="{ 'bg-primary-50 text-primary-600': !selectedCategory }"
                  >
                    全部分类
                  </button>
                  <button
                    v-for="cat in categories"
                    :key="cat"
                    @click="selectedCategory = cat; showCategoryFilter = false"
                    class="w-full px-4 py-2 text-left text-sm hover:bg-gray-50 transition-colors"
                    :class="{ 'bg-primary-50 text-primary-600': selectedCategory === cat }"
                  >
                    {{ cat }}
                  </button>
                </div>
              </div>
              <Button variant="secondary" icon-name="more-horizontal">批量操作</Button>
              <Button icon-name="plus" @click="showAddModal = true">新增文档</Button>
            </div>
          </div>
        </div>

        <div class="hidden md:block overflow-x-auto">
          <table class="w-full">
            <thead class="bg-gray-50">
              <tr>
                <th class="px-4 py-3 text-left">
                  <input
                    type="checkbox"
                    :checked="allSelected"
                    @change="toggleSelectAll"
                    class="w-4 h-4 rounded border-gray-300 text-primary-500 focus:ring-primary-500"
                  />
                </th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">ID</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">标题</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">分类</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">作者</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">类型</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">浏览量</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">创建时间</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">状态</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">操作</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-100">
              <tr
                v-for="doc in filteredDocs"
                :key="doc.id"
                class="hover:bg-gray-50 transition-colors table-row"
              >
                <td class="px-4 py-3">
                  <input
                    type="checkbox"
                    :checked="selectedIds.includes(doc.id)"
                    @change="toggleSelect(doc.id)"
                    class="w-4 h-4 rounded border-gray-300 text-primary-500 focus:ring-primary-500"
                  />
                </td>
                <td class="px-4 py-3 text-sm text-gray-500">#{{ doc.id }}</td>
                <td class="px-4 py-3">
                  <div class="text-sm font-medium text-gray-800 truncate max-w-xs">{{ doc.title }}</div>
                </td>
                <td class="px-4 py-3">
                  <Badge variant="primary">{{ doc.category }}</Badge>
                </td>
                <td class="px-4 py-3">
                  <div class="flex items-center gap-2">
                    <Avatar :name="doc.author" size="sm" />
                    <span class="text-sm text-gray-700">{{ doc.author }}</span>
                  </div>
                </td>
                <td class="px-4 py-3">
                  <Badge variant="default">{{ doc.type }}</Badge>
                </td>
                <td class="px-4 py-3 text-sm text-gray-600">{{ doc.views.toLocaleString() }}</td>
                <td class="px-4 py-3 text-sm text-gray-500">{{ doc.createdAt }}</td>
                <td class="px-4 py-3">
                  <Badge :variant="doc.status === '已发布' ? 'success' : doc.status === '草稿' ? 'warning' : 'danger'">
                    {{ doc.status }}
                  </Badge>
                </td>
                <td class="px-4 py-3">
                  <div class="flex items-center gap-2">
                    <button class="p-1 text-gray-400 hover:text-primary-500 transition-colors" title="编辑">
                      <Icon name="edit" :size="16" />
                    </button>
                    <button class="p-1 text-gray-400 hover:text-danger-500 transition-colors" title="删除">
                      <Icon name="trash-2" :size="16" />
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="md:hidden divide-y divide-gray-100">
          <div
            v-for="doc in filteredDocs"
            :key="doc.id"
            class="p-4 hover:bg-gray-50 transition-colors mobile-card"
          >
            <div class="flex items-start gap-3">
              <input
                type="checkbox"
                :checked="selectedIds.includes(doc.id)"
                @change="toggleSelect(doc.id)"
                class="w-4 h-4 rounded border-gray-300 text-primary-500 focus:ring-primary-500 mt-1"
              />
              <div class="flex-1 min-w-0">
                <div class="flex items-start justify-between gap-2">
                  <h3 class="text-sm font-medium text-gray-800 line-clamp-2">{{ doc.title }}</h3>
                  <Badge :variant="doc.status === '已发布' ? 'success' : doc.status === '草稿' ? 'warning' : 'danger'" class="flex-shrink-0">
                    {{ doc.status }}
                  </Badge>
                </div>
                <div class="flex items-center gap-3 mt-2 flex-wrap">
                  <Badge variant="primary">{{ doc.category }}</Badge>
                  <Badge variant="default">{{ doc.type }}</Badge>
                </div>
                <div class="flex items-center justify-between mt-3">
                  <div class="flex items-center gap-2">
                    <Avatar :name="doc.author" size="sm" />
                    <span class="text-xs text-gray-500">{{ doc.author }}</span>
                  </div>
                  <div class="flex items-center gap-1 text-xs text-gray-400">
                    <Icon name="eye" :size="12" />
                    <span>{{ doc.views.toLocaleString() }}</span>
                  </div>
                </div>
                <div class="flex items-center justify-between mt-3 pt-3 border-t border-gray-100">
                  <span class="text-xs text-gray-400">{{ doc.createdAt }}</span>
                  <div class="flex items-center gap-2">
                    <button class="p-1.5 text-gray-400 hover:text-primary-500 hover:bg-primary-50 rounded transition-colors">
                      <Icon name="edit" :size="16" />
                    </button>
                    <button class="p-1.5 text-gray-400 hover:text-danger-500 hover:bg-danger-50 rounded transition-colors">
                      <Icon name="trash-2" :size="16" />
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="px-4 py-3 border-t border-gray-100 flex flex-col sm:flex-row items-center justify-between gap-3">
          <p class="text-sm text-gray-500">
            共 <span class="font-medium text-gray-700">{{ totalDocs }}</span> 条记录
          </p>
          <div class="flex items-center gap-1">
            <button
              @click="currentPage = Math.max(1, currentPage - 1)"
              :disabled="currentPage === 1"
              class="p-2 rounded-sm border border-gray-200 hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              <Icon name="chevron-left" :size="16" />
            </button>
            <button
              v-for="page in visiblePages"
              :key="page"
              @click="currentPage = page"
              :class="[
                'w-8 h-8 text-sm rounded-sm transition-colors',
                currentPage === page
                  ? 'bg-primary-500 text-white'
                  : 'border border-gray-200 text-gray-600 hover:bg-gray-50'
              ]"
            >
              {{ page }}
            </button>
            <button
              @click="currentPage = Math.min(totalPages, currentPage + 1)"
              :disabled="currentPage === totalPages"
              class="p-2 rounded-sm border border-gray-200 hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              <Icon name="chevron-right" :size="16" />
            </button>
          </div>
        </div>
      </Card>
    </div>
  </AppShell>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import AppShell from '@/components/layout/AppShell.vue'
import Card from '@/components/ui/Card.vue'
import Input from '@/components/ui/Input.vue'
import Button from '@/components/ui/Button.vue'
import Badge from '@/components/ui/Badge.vue'
import Avatar from '@/components/ui/Avatar.vue'

const searchQuery = ref('')
const selectedCategory = ref('')
const showCategoryFilter = ref(false)
const showAddModal = ref(false)
const currentPage = ref(1)
const selectedIds = ref<string[]>([])

const categories = ['前端开发', '后端开发', '人工智能', '数据库', '运维', '产品设计']

interface Doc {
  id: string
  title: string
  category: string
  author: string
  type: string
  views: number
  createdAt: string
  status: string
}

const allDocs: Doc[] = [
  { id: '001', title: 'Vue 3 组合式 API 完全指南', category: '前端开发', author: '张三', type: 'Markdown', views: 2580, createdAt: '2024-01-15', status: '已发布' },
  { id: '002', title: 'TypeScript 高级类型详解', category: '前端开发', author: '李四', type: 'PDF', views: 2156, createdAt: '2024-01-14', status: '已发布' },
  { id: '003', title: 'Node.js 性能优化实战', category: '后端开发', author: '王五', type: 'Markdown', views: 1890, createdAt: '2024-01-13', status: '已发布' },
  { id: '004', title: '大语言模型原理与应用', category: '人工智能', author: '赵六', type: 'Word', views: 1654, createdAt: '2024-01-12', status: '草稿' },
  { id: '005', title: 'MySQL 索引优化深入理解', category: '数据库', author: '孙七', type: 'Markdown', views: 1432, createdAt: '2024-01-11', status: '已发布' },
  { id: '006', title: 'Docker 容器化部署最佳实践', category: '运维', author: '周八', type: 'PDF', views: 1256, createdAt: '2024-01-10', status: '已发布' },
  { id: '007', title: 'React Hooks 深度解析', category: '前端开发', author: '吴九', type: 'Markdown', views: 1123, createdAt: '2024-01-09', status: '已发布' },
  { id: '008', title: '微服务架构设计模式', category: '后端开发', author: '郑十', type: 'Word', views: 987, createdAt: '2024-01-08', status: '已禁用' },
  { id: '009', title: '产品需求文档撰写指南', category: '产品设计', author: '冯十一', type: 'Markdown', views: 876, createdAt: '2024-01-07', status: '已发布' },
  { id: '010', title: 'Redis 缓存设计与实现', category: '数据库', author: '陈十二', type: 'PDF', views: 765, createdAt: '2024-01-06', status: '草稿' },
]

const filteredDocs = computed(() => {
  let result = [...allDocs]
  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase()
    result = result.filter(
      doc =>
        doc.title.toLowerCase().includes(query) ||
        doc.author.toLowerCase().includes(query)
    )
  }
  if (selectedCategory.value) {
    result = result.filter(doc => doc.category === selectedCategory.value)
  }
  return result
})

const totalDocs = computed(() => filteredDocs.value.length)
const totalPages = computed(() => Math.ceil(totalDocs.value / 10))

const visiblePages = computed(() => {
  const pages: number[] = []
  const total = totalPages.value
  const current = currentPage.value
  let start = Math.max(1, current - 2)
  let end = Math.min(total, current + 2)
  if (end - start + 1 < 5) {
    if (start === 1) {
      end = Math.min(5, total)
    } else {
      start = Math.max(1, total - 4)
    }
  }
  for (let i = start; i <= end; i++) {
    pages.push(i)
  }
  return pages
})

const allSelected = computed(() => {
  return filteredDocs.value.length > 0 && filteredDocs.value.every(doc => selectedIds.value.includes(doc.id))
})

const toggleSelectAll = () => {
  if (allSelected.value) {
    selectedIds.value = []
  } else {
    selectedIds.value = filteredDocs.value.map(doc => doc.id)
  }
}

const toggleSelect = (id: string) => {
  const index = selectedIds.value.indexOf(id)
  if (index > -1) {
    selectedIds.value.splice(index, 1)
  } else {
    selectedIds.value.push(id)
  }
}
</script>

<style scoped>
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

.animate-dropdown {
  animation: dropdown 0.2s ease-out;
}

@keyframes dropdown {
  from {
    opacity: 0;
    transform: translateY(-5px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.table-row {
  animation: fadeInRow 0.3s ease-out;
}

@keyframes fadeInRow {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

.mobile-card {
  animation: slideIn 0.3s ease-out;
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateX(-10px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

.line-clamp-2 {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
