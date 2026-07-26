<template>
    <div class="space-y-6 animate-fade-in">
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">知识卡片管理</h1>
          <p class="text-gray-500 text-sm mt-1">管理学习闪卡内容，支持批量导入导出</p>
        </div>
        <div class="flex items-center gap-3">
          <Button variant="secondary" icon-name="upload" @click="showImportModal = true">批量导入</Button>
          <Button icon-name="plus" @click="openCreate">新增卡片</Button>
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
            v-for="card in filteredCards" :key="card.id"
            class="group border border-gray-200 rounded-xl p-4 hover:shadow-md transition-all duration-200 cursor-pointer"
            :class="{ 'border-primary-300 bg-primary-50': selectedCard?.id === card.id }"
            @click="selectedCard = card"
          >
            <div class="flex items-start justify-between mb-3">
              <Badge :variant="getStatusVariant(card.status)">{{ getStatusText(card.status) }}</Badge>
              <div class="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                <button class="p-1 hover:bg-gray-100 rounded text-gray-400 hover:text-primary-500" @click.stop="openEdit(card)">
                  <Icon name="edit" :size="16" />
                </button>
                <button class="p-1 hover:bg-gray-100 rounded text-gray-400 hover:text-danger-500" @click.stop="deleteCard(card)">
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
              <span>正确率 {{ card.accuracy != null ? card.accuracy + '%' : '—' }}</span>
            </div>
          </div>
          <p v-if="filteredCards.length === 0" class="col-span-full text-center text-gray-400 text-sm py-12">暂无闪卡数据</p>
        </div>
      </Card>
    </div>

    <!-- 新增/编辑弹窗 -->
    <div
      v-if="showAddModal"
      class="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4"
      @click.self="closeModal"
    >
      <div class="bg-white rounded-xl w-full max-w-2xl shadow-xl animate-dropdown">
        <div class="flex items-center justify-between px-6 py-4 border-b border-gray-100">
          <h3 class="text-lg font-semibold text-gray-800">{{ editingId ? '编辑卡片' : '新增卡片' }}</h3>
          <button class="p-1 hover:bg-gray-100 rounded transition-colors" @click="closeModal">
            <Icon name="x" :size="20" />
          </button>
        </div>
        <div class="px-6 py-4 space-y-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1.5">正面（问题）</label>
            <textarea v-model="form.front" rows="3" placeholder="请输入问题" class="w-full px-3 py-2 border border-gray-200 rounded-sm text-sm focus:outline-none focus:border-primary-500 font-mono"></textarea>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1.5">背面（答案）</label>
            <textarea v-model="form.back" rows="3" placeholder="请输入答案" class="w-full px-3 py-2 border border-gray-200 rounded-sm text-sm focus:outline-none focus:border-primary-500 font-mono"></textarea>
          </div>
          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1.5">分类</label>
              <Input v-model="form.category" placeholder="如：JavaScript" />
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1.5">难度</label>
              <select v-model.number="form.difficulty" class="w-full px-3 py-2 border border-gray-200 rounded-sm text-sm focus:outline-none focus:border-primary-500">
                <option :value="1">简单</option>
                <option :value="2">中等</option>
                <option :value="3">困难</option>
              </select>
            </div>
          </div>
        </div>
        <div class="flex items-center justify-end gap-3 px-6 py-4 border-t border-gray-100">
          <Button variant="secondary" @click="closeModal">取消</Button>
          <Button :disabled="saving" @click="save">{{ saving ? '保存中...' : '保存' }}</Button>
        </div>
      </div>
    </div>
</template>

<script setup lang="ts">
// 管理后台-知识卡片：维护学习闪卡列表，支持检索筛选与新增/编辑/删除，按复习次数推导状态。
import { confirmDialog, getApiError, notify } from '@/utils/toast'
import { ref, computed, onMounted } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import Card from '@/components/ui/Card.vue'
import Input from '@/components/ui/Input.vue'
import Button from '@/components/ui/Button.vue'
import Badge from '@/components/ui/Badge.vue'
import { learningApi, adminApi } from '@/api'
import type { FlashcardVO, FlashcardInput } from '@/api/types'

const searchQuery = ref('')
const filterCategory = ref('')
const filterStatus = ref('')
const selectedCard = ref<Flashcard | null>(null)
const showAddModal = ref(false)
const showImportModal = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)

interface Flashcard {
  id: string
  front: string
  back: string
  category: string
  tags: string[]
  status: 'new' | 'learning' | 'mastered'
  reviewCount: number
  accuracy: number | null
}

const cards = ref<Flashcard[]>([])
const loading = ref(false)

// 按复习次数推导卡片状态：≥10 已掌握，>0 学习中，否则新卡片
const deriveStatus = (rc?: number): 'new' | 'learning' | 'mastered' => {
  const n = rc ?? 0
  if (n >= 10) return 'mastered'
  if (n > 0) return 'learning'
  return 'new'
}

const stats = computed(() => {
  const list = cards.value
  return {
    total: list.length,
    mastered: list.filter((c) => c.status === 'mastered').length,
    learning: list.filter((c) => c.status === 'learning').length,
    due: list.filter((c) => c.status === 'new').length,
  }
})

const categories = computed(() => Array.from(new Set(cards.value.map((c) => c.category).filter(Boolean))))

const filteredCards = computed(() => {
  let result = [...cards.value]
  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase()
    result = result.filter((card) => card.front.toLowerCase().includes(query) || card.back.toLowerCase().includes(query))
  }
  if (filterCategory.value) {
    result = result.filter((card) => card.category === filterCategory.value)
  }
  if (filterStatus.value) {
    result = result.filter((card) => card.status === filterStatus.value)
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

const form = ref<FlashcardInput>({ front: '', back: '', category: '', difficulty: 1 })

const openCreate = () => {
  editingId.value = null
  form.value = { front: '', back: '', category: '', difficulty: 1 }
  showAddModal.value = true
}

const openEdit = (card: Flashcard) => {
  editingId.value = Number(card.id)
  const src = cards.value.find((c) => c.id === card.id)
  form.value = {
    front: src?.front ?? '',
    back: src?.back ?? '',
    category: src?.category ?? '',
    difficulty: 1,
  }
  showAddModal.value = true
}

const closeModal = () => {
  showAddModal.value = false
  editingId.value = null
}

const save = async () => {
  if (!form.value.front.trim() || !form.value.back.trim()) {
    notify('请填写正面和背面内容', 'warning')
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await adminApi.updateFlashcard(editingId.value, form.value)
      notify('卡片已更新', 'success')
    } else {
      await adminApi.createFlashcard(form.value)
      notify('卡片已创建', 'success')
    }
    closeModal()
    await loadCards()
  } catch (e: unknown) {
    notify('保存失败：' + getApiError(e), 'error')
  } finally {
    saving.value = false
  }
}

const deleteCard = async (card: Flashcard) => {
  if (!(await confirmDialog('确定要删除这张卡片吗？'))) return
  try {
    await adminApi.removeFlashcard(Number(card.id))
    notify('删除成功', 'success')
    selectedCard.value = null
    await loadCards()
  } catch (e: unknown) {
    notify('删除失败：' + getApiError(e), 'error')
  }
}

const loadCards = async () => {
  loading.value = true
  try {
    const list = (await learningApi.flashcards()) as FlashcardVO[]
    cards.value = list.map((f) => ({
      id: String(f.id),
      front: f.front ?? '',
      back: f.back ?? '',
      category: f.category ?? '',
      tags: [],
      status: deriveStatus(f.reviewCount),
      reviewCount: f.reviewCount ?? 0,
      accuracy: null,
    }))
  } catch (e: unknown) {
    notify('加载闪卡失败：' + getApiError(e), 'error')
  } finally {
    loading.value = false
  }
}

onMounted(loadCards)
</script>

<style scoped>
.animate-fade-in {
  animation: fadeIn 0.5s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.animate-dropdown {
  animation: dropdown 0.2s ease-out;
}

@keyframes dropdown {
  from { opacity: 0; transform: translateY(-5px); }
  to { opacity: 1; transform: translateY(0); }
}

.line-clamp-2 {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
