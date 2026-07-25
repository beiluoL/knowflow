<template>
  <div class="space-y-6 animate-fade-in">
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-800">标签管理</h1>
        <p class="text-gray-500 text-sm mt-1">管理知识库标签，组织文档分类体系</p>
      </div>
      <Button icon-name="plus" @click="showAddModal = true">新增标签</Button>
    </div>

    <div class="flex items-center gap-4">
      <div class="relative flex-1 max-w-sm">
        <Icon name="search" :size="16" class="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
        <input
          v-model="searchQuery"
          type="text"
          placeholder="搜索标签..."
          class="w-full h-9 pl-9 pr-4 rounded-lg text-[13px] border border-[#E2E6EC] bg-white text-gray-800 placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-primary-500/30 focus:border-primary-500 transition-all"
        />
      </div>
      <div class="text-sm text-gray-500">
        共 <span class="font-medium text-gray-800">{{ filteredTags.length }}</span> 个标签
      </div>
    </div>

    <Card>
      <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
        <div
          v-for="(tag, index) in filteredTags" :key="tag.id"
          class="group relative p-4 border border-[#E2E6EC] rounded-lg hover:border-primary-500/30 hover:shadow-md transition-all cursor-pointer tag-card"
          :style="{ animationDelay: `${index * 30}ms` }"
          @click="selectTag(tag)"
        >
          <div class="flex items-start justify-between mb-3">
            <div
              class="w-10 h-10 rounded-lg flex items-center justify-center"
              :style="{ backgroundColor: tag.color + '20' }"
            >
              <Icon name="tag" :size="18" :style="{ color: tag.color }" />
            </div>
            <div class="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
              <button
                class="p-1.5 hover:bg-gray-100 rounded text-gray-400 hover:text-primary-500 transition-colors"
                @click.stop="editTag(tag)"
                title="编辑"
              >
                <Icon name="edit" :size="16" />
              </button>
              <button
                class="p-1.5 hover:bg-gray-100 rounded text-gray-400 hover:text-danger-500 transition-colors"
                @click.stop="deleteTag(tag)"
                title="删除"
              >
                <Icon name="trash-2" :size="16" />
              </button>
            </div>
          </div>
          <h4 class="font-medium text-gray-800 mb-1 truncate">{{ tag.name }}</h4>
          <div class="flex items-center justify-between">
            <span class="text-xs text-gray-500">{{ tag.count }} 篇文档</span>
            <div class="w-4 h-4 rounded-full shrink-0" :style="{ backgroundColor: tag.color }"></div>
          </div>
        </div>
      </div>
      <p v-if="filteredTags.length === 0" class="text-center py-16 text-gray-400">
        暂无标签
      </p>
      <p class="text-xs text-gray-400 mt-4 pt-4 border-t border-gray-50">
        提示：标签颜色用于在文档列表中快速区分不同主题，建议为同一领域的标签选择相近色系。
      </p>
    </Card>

    <div v-if="selectedTag" class="grid grid-cols-1 lg:grid-cols-3 gap-6">
      <Card class="lg:col-span-2">
        <template #header>
          <div class="flex items-center justify-between">
            <h3 class="font-semibold text-gray-800">标签详情：{{ selectedTag.name }}</h3>
            <Button size="sm" variant="secondary" @click="selectedTag = null">关闭</Button>
          </div>
        </template>
        <div class="space-y-4">
          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1.5">标签名称</label>
              <Input v-model="tagForm.name" placeholder="请输入标签名称" />
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1.5">标签颜色</label>
              <div class="flex items-center gap-2">
                <input
                  type="color"
                  v-model="tagForm.color"
                  class="w-9 h-9 rounded border border-gray-200 cursor-pointer"
                />
                <Input v-model="tagForm.color" placeholder="#3B6FE0" />
              </div>
            </div>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1.5">快速选色</label>
            <div class="flex flex-wrap gap-2">
              <button
                v-for="color in colorPresets" :key="color"
                type="button"
                class="w-8 h-8 rounded-lg border-2 transition-all hover:scale-110"
                :style="{ backgroundColor: color, borderColor: tagForm.color === color ? '#3B6FE0' : 'transparent' }"
                @click="tagForm.color = color"
              ></button>
            </div>
          </div>
          <div class="flex items-center justify-between pt-2">
            <span class="text-sm text-gray-500">关联文档数：{{ selectedTag.count }} 篇</span>
            <div class="flex gap-2">
              <Button variant="secondary" @click="resetTagForm">重置</Button>
              <Button :disabled="saving" @click="saveTag">{{ saving ? '保存中...' : '保存' }}</Button>
            </div>
          </div>
        </div>
      </Card>

      <Card>
        <template #header>
          <h3 class="font-semibold text-gray-800">关联文档</h3>
        </template>
        <div class="space-y-2">
          <div
            v-for="doc in tagDocs" :key="doc.id"
            class="p-3 rounded-lg hover:bg-gray-50 cursor-pointer transition-colors"
            @click="goToDoc(doc.id)"
          >
            <h5 class="text-sm font-medium text-gray-800 mb-1 line-clamp-1">{{ doc.title }}</h5>
            <div class="flex items-center gap-2 text-xs text-gray-400">
              <span>{{ doc.categoryName }}</span>
              <span>·</span>
              <span>{{ formatDate(doc.createTime) }}</span>
            </div>
          </div>
          <p v-if="tagDocs.length === 0" class="text-sm text-gray-400 text-center py-8">
            暂无关联文档
          </p>
        </div>
      </Card>
    </div>

    <div v-if="showAddModal" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40" @click.self="showAddModal = false">
      <div class="bg-white rounded-xl p-6 w-full max-w-md shadow-xl animate-modal-in">
        <h3 class="text-lg font-semibold text-gray-800 mb-4">新增标签</h3>
        <div class="space-y-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1.5">标签名称</label>
            <Input v-model="newTagForm.name" placeholder="请输入标签名称" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1.5">标签颜色</label>
            <div class="flex items-center gap-2 mb-2">
              <input
                type="color"
                v-model="newTagForm.color"
                class="w-9 h-9 rounded border border-gray-200 cursor-pointer"
              />
              <Input v-model="newTagForm.color" placeholder="#3B6FE0" />
            </div>
            <div class="flex flex-wrap gap-2">
              <button
                v-for="color in colorPresets" :key="color"
                type="button"
                class="w-7 h-7 rounded-lg border-2 transition-all hover:scale-110"
                :style="{ backgroundColor: color, borderColor: newTagForm.color === color ? '#3B6FE0' : 'transparent' }"
                @click="newTagForm.color = color"
              ></button>
            </div>
          </div>
        </div>
        <div class="flex justify-end gap-2 mt-6">
          <Button variant="secondary" @click="showAddModal = false">取消</Button>
          <Button @click="createTag">创建</Button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { confirmDialog, getApiError, notify } from '@/utils/toast'
import Icon from '@/components/ui/Icon.vue'
import Card from '@/components/ui/Card.vue'
import Button from '@/components/ui/Button.vue'
import Input from '@/components/ui/Input.vue'
import { docsApi } from '@/api'
import type { DocVO } from '@/api/types'

const router = useRouter()

interface Tag {
  id: string
  name: string
  color: string
  count: number
}

const tags = ref<Tag[]>([
  { id: '1', name: 'Vue 3', color: '#3B6FE0', count: 234 },
  { id: '2', name: 'React', color: '#10B981', count: 198 },
  { id: '3', name: 'TypeScript', color: '#3178C6', count: 187 },
  { id: '4', name: 'Node.js', color: '#68A063', count: 156 },
  { id: '5', name: 'Python', color: '#FFD43B', count: 145 },
  { id: '6', name: 'MySQL', color: '#4479A1', count: 134 },
  { id: '7', name: 'Redis', color: '#DC382D', count: 123 },
  { id: '8', name: 'Docker', color: '#2496ED', count: 112 },
  { id: '9', name: 'GPT', color: '#10A37F', count: 101 },
  { id: '10', name: '算法', color: '#F59E0B', count: 98 },
  { id: '11', name: '设计模式', color: '#8B5CF6', count: 87 },
  { id: '12', name: '性能优化', color: '#EF4444', count: 76 },
])

const searchQuery = ref('')
const selectedTag = ref<Tag | null>(null)
const showAddModal = ref(false)
const saving = ref(false)

const colorPresets = [
  '#3B6FE0', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6',
  '#06B6D4', '#EC4899', '#84CC16', '#F97316', '#6366F1',
  '#14B8A6', '#F43F5E',
]

const newTagForm = reactive({
  name: '',
  color: '#3B6FE0',
})

const tagForm = reactive({
  name: '',
  color: '#3B6FE0',
})

const filteredTags = computed(() => {
  if (!searchQuery.value) return tags.value
  const q = searchQuery.value.toLowerCase()
  return tags.value.filter((t) => t.name.toLowerCase().includes(q))
})

const tagDocs = ref<DocVO[]>([])

const selectTag = (tag: Tag) => {
  selectedTag.value = tag
  tagForm.name = tag.name
  tagForm.color = tag.color
  loadTagDocs(tag)
}

const loadTagDocs = async (_tag: Tag) => {
  try {
    const res = await docsApi.list({ pageSize: 5 })
    tagDocs.value = res.records || []
  } catch {
    tagDocs.value = []
  }
}

const resetTagForm = () => {
  if (selectedTag.value) {
    tagForm.name = selectedTag.value.name
    tagForm.color = selectedTag.value.color
  }
}

const saveTag = async () => {
  if (!tagForm.name.trim()) {
    notify('请填写标签名称', 'warning')
    return
  }
  if (!selectedTag.value) return
  saving.value = true
  try {
    const target = tags.value.find((t) => t.id === selectedTag.value!.id)
    if (target) {
      target.name = tagForm.name
      target.color = tagForm.color
    }
    notify('标签已更新', 'success')
  } catch (e: unknown) {
    notify('保存失败：' + getApiError(e), 'error')
  } finally {
    saving.value = false
  }
}

const editTag = (tag: Tag) => {
  selectTag(tag)
  if (selectedTag.value) {
    const el = document.querySelector('.lg\\:col-span-2')
    el?.scrollIntoView({ behavior: 'smooth' })
  }
}

const deleteTag = async (tag: Tag) => {
  if (!(await confirmDialog(`确定删除标签「${tag.name}」吗？`))) return
  try {
    const index = tags.value.findIndex((t) => t.id === tag.id)
    if (index > -1) tags.value.splice(index, 1)
    if (selectedTag.value?.id === tag.id) selectedTag.value = null
    notify('删除成功', 'success')
  } catch (e: unknown) {
    notify('删除失败：' + getApiError(e), 'error')
  }
}

const createTag = () => {
  if (!newTagForm.name.trim()) {
    notify('请填写标签名称', 'warning')
    return
  }
  const newTag: Tag = {
    id: String(Date.now()),
    name: newTagForm.name,
    color: newTagForm.color,
    count: 0,
  }
  tags.value.unshift(newTag)
  showAddModal.value = false
  newTagForm.name = ''
  newTagForm.color = '#3B6FE0'
  notify('标签已创建', 'success')
}

const goToDoc = (id: number) => {
  router.push(`/doc/${id}`)
}

const formatDate = (dateStr?: string) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', {
    month: 'short',
    day: 'numeric',
  })
}

onMounted(() => {
})
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

.animate-modal-in {
  animation: modalIn 0.25s ease-out;
}

@keyframes modalIn {
  from {
    opacity: 0;
    transform: scale(0.95) translateY(10px);
  }
  to {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
}

.tag-card {
  animation: scaleIn 0.3s ease-out both;
}

@keyframes scaleIn {
  from {
    opacity: 0;
    transform: scale(0.95);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

.line-clamp-1 {
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
