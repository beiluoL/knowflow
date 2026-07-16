<template>
  <AppShell>
    <div class="space-y-6 animate-fade-in">
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">知识卡片管理</h1>
          <p class="text-gray-500 text-sm mt-1">管理学习闪卡内容，支持批量导入导出</p>
        </div>
        <div class="flex items-center gap-3">
          <Button variant="secondary" icon-name="upload" @click="showImportModal = true">批量导入</Button>
          <Button icon-name="plus" @click="showAddModal = true">新增卡片</Button>
        </div>
      </div>

      <div class="grid grid-cols-1 md:grid-cols-4 gap-4">
        <Card class="bg-gradient-to-br from-primary-50 to-primary-100 border-primary-200">
          <div class="flex items-center gap-3">
            <div class="w-10 h-10 rounded-lg bg-primary-500 flex items-center justify-center">
              <Icon name="layers" :size="20" class="text-white" />
            </div>
            <div>
              <p class="text-sm text-gray-500">总卡片数</p>
              <p class="text-xl font-bold text-gray-800">{{ stats.total }}</p>
            </div>
          </div>
        </Card>
        <Card class="bg-gradient-to-br from-green-50 to-green-100 border-green-200">
          <div class="flex items-center gap-3">
            <div class="w-10 h-10 rounded-lg bg-green-500 flex items-center justify-center">
              <Icon name="check-circle" :size="20" class="text-white" />
            </div>
            <div>
              <p class="text-sm text-gray-500">已掌握</p>
              <p class="text-xl font-bold text-gray-800">{{ stats.mastered }}</p>
            </div>
          </div>
        </Card>
        <Card class="bg-gradient-to-br from-yellow-50 to-yellow-100 border-yellow-200">
          <div class="flex items-center gap-3">
            <div class="w-10 h-10 rounded-lg bg-yellow-500 flex items-center justify-center">
              <Icon name="refresh-cw" :size="20" class="text-white" />
            </div>
            <div>
              <p class="text-sm text-gray-500">学习中</p>
              <p class="text-xl font-bold text-gray-800">{{ stats.learning }}</p>
            </div>
          </div>
        </Card>
        <Card class="bg-gradient-to-br from-red-50 to-red-100 border-red-200">
          <div class="flex items-center gap-3">
            <div class="w-10 h-10 rounded-lg bg-red-500 flex items-center justify-center">
              <Icon name="alert-circle" :size="20" class="text-white" />
            </div>
            <div>
              <p class="text-sm text-gray-500">待复习</p>
              <p class="text-xl font-bold text-gray-800">{{ stats.due }}</p>
            </div>
          </div>
        </Card>
      </div>

      <Card padding="none">
        <div class="p-4 border-b border-gray-100">
          <div class="flex flex-col sm:flex-row sm:items-center gap-4">
            <div class="flex-1">
              <Input
                v-model="searchQuery"
                placeholder="搜索卡片内容、标签..."
                prefix-icon-name="search"
              />
            </div>
            <div class="flex items-center gap-3 flex-wrap">
              <select
                v-model="filterCategory"
                class="px-3 py-2 border border-gray-200 rounded-lg text-sm focus:outline-none focus:border-primary-500"
              >
                <option value="">全部分类</option>
                <option v-for="cat in categories" :key="cat" :value="cat">{{ cat }}</option>
              </select>
              <select
                v-model="filterStatus"
                class="px-3 py-2 border border-gray-200 rounded-lg text-sm focus:outline-none focus:border-primary-500"
              >
                <option value="">全部状态</option>
                <option value="mastered">已掌握</option>
                <option value="learning">学习中</option>
                <option value="new">新卡片</option>
              </select>
            </div>
          </div>
        </div>

        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 p-4">
          <div
            v-for="card in filteredCards"
            :key="card.id"
            class="group border border-gray-200 rounded-xl p-4 hover:shadow-md transition-all duration-200 cursor-pointer"
            :class="{ 'border-primary-300 bg-primary-50': selectedCard?.id === card.id }"
            @click="selectedCard = card"
          >
            <div class="flex items-start justify-between mb-3">
              <Badge :variant="getStatusVariant(card.status)">{{ getStatusText(card.status) }}</Badge>
              <div class="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                <button class="p-1 hover:bg-gray-100 rounded text-gray-400 hover:text-primary-500" @click.stop="editCard(card)">
                  <Icon name="edit" :size="16" />
                </button>
                <button class="p-1 hover:bg-gray-100 rounded text-gray-400 hover:text-danger-500" @click.stop="deleteCard(card.id)">
                  <Icon name="trash-2" :size="16" />
                </button>
              </div>
            </div>
            <h3 class="font-medium text-gray-800 mb-2 line-clamp-2">{{ card.front }}</h3>
            <p class="text-sm text-gray-500 line-clamp-2 mb-3">{{ card.back }}</p>
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-2 flex-wrap">
                <Badge v-for="tag in card.tags" :key="tag" variant="default" class="text-xs">{{ tag }}</Badge>
              </div>
              <span class="text-xs text-gray-400">{{ card.category }}</span>
            </div>
            <div class="mt-3 pt-3 border-t border-gray-100 flex items-center justify-between text-xs text-gray-500">
              <span>复习 {{ card.reviewCount }} 次</span>
              <span>正确率 {{ card.accuracy }}%</span>
            </div>
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

const searchQuery = ref('')
const filterCategory = ref('')
const filterStatus = ref('')
const selectedCard = ref<Flashcard | null>(null)
const showAddModal = ref(false)
const showImportModal = ref(false)

interface Flashcard {
  id: string
  front: string
  back: string
  category: string
  tags: string[]
  status: 'new' | 'learning' | 'mastered'
  reviewCount: number
  accuracy: number
}

const stats = ref({
  total: 256,
  mastered: 128,
  learning: 89,
  due: 39,
})

const categories = ['前端开发', '后端开发', '人工智能', '数据库', '运维', '算法']

const flashcards: Flashcard[] = [
  { id: '1', front: 'Vue 3 的 Composition API 是什么？', back: 'Composition API 是 Vue 3 引入的一组 API，允许我们使用导入的函数而不是声明选项来编写 Vue 组件。', category: '前端开发', tags: ['Vue', 'JavaScript'], status: 'learning', reviewCount: 5, accuracy: 80 },
  { id: '2', front: '什么是闭包（Closure）？', back: '闭包是指有权访问另一个函数作用域中的变量的函数。创建闭包的常见方式，就是在一个函数内部创建另一个函数。', category: '前端开发', tags: ['JavaScript', '基础'], status: 'mastered', reviewCount: 12, accuracy: 95 },
  { id: '3', front: 'React 的 useEffect 钩子有什么作用？', back: 'useEffect 用于在函数组件中执行副作用操作，如数据获取、订阅或手动修改 DOM。', category: '前端开发', tags: ['React', 'Hooks'], status: 'new', reviewCount: 0, accuracy: 0 },
  { id: '4', front: '什么是数据库索引？', back: '数据库索引是一种数据结构，用于快速查询数据库表中的数据。它类似于书籍的目录，可以加快数据检索速度。', category: '数据库', tags: ['MySQL', '基础'], status: 'learning', reviewCount: 3, accuracy: 70 },
  { id: '5', front: 'Docker 容器和虚拟机的区别？', back: 'Docker 容器共享主机操作系统内核，启动快、资源占用少；虚拟机需要完整的操作系统，启动慢、资源占用多。', category: '运维', tags: ['Docker', 'DevOps'], status: 'mastered', reviewCount: 8, accuracy: 90 },
  { id: '6', front: '什么是 RESTful API？', back: 'RESTful API 是一种基于 HTTP 协议的网络应用程序接口设计风格，使用 URL 定位资源，HTTP 方法定义操作。', category: '后端开发', tags: ['API', '架构'], status: 'learning', reviewCount: 4, accuracy: 75 },
]

const filteredCards = computed(() => {
  let result = [...flashcards]
  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase()
    result = result.filter(card =>
      card.front.toLowerCase().includes(query) ||
      card.back.toLowerCase().includes(query) ||
      card.tags.some(tag => tag.toLowerCase().includes(query))
    )
  }
  if (filterCategory.value) {
    result = result.filter(card => card.category === filterCategory.value)
  }
  if (filterStatus.value) {
    result = result.filter(card => card.status === filterStatus.value)
  }
  return result
})

const getStatusVariant = (status: string) => {
  switch (status) {
    case 'mastered': return 'success'
    case 'learning': return 'warning'
    default: return 'default'
  }
}

const getStatusText = (status: string) => {
  switch (status) {
    case 'mastered': return '已掌握'
    case 'learning': return '学习中'
    default: return '新卡片'
  }
}

const editCard = (card: Flashcard) => {
  selectedCard.value = card
  showAddModal.value = true
}

const deleteCard = (_id: string) => {
  if (confirm('确定要删除这张卡片吗？')) {
    alert('删除成功')
  }
}
</script>

<style scoped>
.animate-fade-in {
  animation: fadeIn 0.5s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.line-clamp-2 {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
