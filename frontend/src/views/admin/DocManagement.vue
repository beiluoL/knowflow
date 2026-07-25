<template>
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
                placeholder="搜索文档标题..."
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
                    v-for="cat in categoryOptions" :key="cat.id"
                    @click="selectedCategory = cat.name; showCategoryFilter = false"
                    class="w-full px-4 py-2 text-left text-sm hover:bg-gray-50 transition-colors"
                    :class="{ 'bg-primary-50 text-primary-600': selectedCategory === cat.name }"
                  >
                    {{ cat.name }}
                  </button>
                </div>
              </div>
              <Button icon-name="upload" variant="secondary" @click="goToUpload">上传文档</Button>
              <Button icon-name="plus" @click="goToCreate">新增文档</Button>
            </div>
          </div>
        </div>

        <div class="overflow-x-auto">
          <table class="w-full">
            <thead class="bg-gray-50">
              <tr>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">ID</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">标题</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">分类</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">浏览量</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">创建时间</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">状态</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">操作</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-100">
              <tr
                v-for="doc in pagedDocs" :key="doc.id"
                class="hover:bg-gray-50 transition-colors table-row"
              >
                <td class="px-4 py-3 text-sm text-gray-500">#{{ doc.id }}</td>
                <td class="px-4 py-3">
                  <div class="text-sm font-medium text-gray-800 truncate max-w-xs">{{ doc.title }}</div>
                </td>
                <td class="px-4 py-3">
                  <Badge variant="primary">{{ doc.category }}</Badge>
                </td>
                <td class="px-4 py-3 text-sm text-gray-600">{{ doc.views.toLocaleString() }}</td>
                <td class="px-4 py-3 text-sm text-gray-500">{{ doc.createdAt }}</td>
                <td class="px-4 py-3">
                  <Badge :variant="statusVariant(doc.status)">{{ statusText(doc.status) }}</Badge>
                </td>
                <td class="px-4 py-3">
                  <div class="flex items-center gap-2">
                    <button class="p-1 text-gray-400 hover:text-primary-500 transition-colors" title="编辑" @click="openEdit(doc)">
                      <Icon name="edit" :size="16" />
                    </button>
                    <button class="p-1 text-gray-400 hover:text-danger-500 transition-colors" title="删除" @click="removeDoc(doc)">
                      <Icon name="trash-2" :size="16" />
                    </button>
                  </div>
                </td>
              </tr>
              <tr v-if="pagedDocs.length === 0">
                <td colspan="7" class="px-4 py-12 text-center text-gray-400 text-sm">暂无文档数据</td>
              </tr>
            </tbody>
          </table>
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
              v-for="page in visiblePages" :key="page"
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

    <!-- 新增/编辑弹窗 -->
    <div
      v-if="showModal"
      class="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4"
      @click.self="closeModal"
    >
      <div class="bg-white rounded-xl w-full max-w-2xl max-h-[90vh] overflow-y-auto shadow-xl animate-dropdown">
        <div class="flex items-center justify-between px-6 py-4 border-b border-gray-100">
          <h3 class="text-lg font-semibold text-gray-800">{{ editingId ? '编辑文档' : '新增文档' }}</h3>
          <button class="p-1 hover:bg-gray-100 rounded transition-colors" @click="closeModal">
            <Icon name="x" :size="20" />
          </button>
        </div>
        <div class="px-6 py-4 space-y-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1.5">标题</label>
            <Input v-model="form.title" placeholder="请输入文档标题" />
          </div>
          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1.5">分类</label>
              <select v-model.number="form.categoryId" class="w-full px-3 py-2 border border-gray-200 rounded-sm text-sm focus:outline-none focus:border-primary-500">
                <option :value="0">未分类</option>
                <option v-for="cat in categoryOptions" :key="cat.id" :value="cat.id">{{ cat.name }}</option>
              </select>
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1.5">状态</label>
              <select v-model.number="form.status" class="w-full px-3 py-2 border border-gray-200 rounded-sm text-sm focus:outline-none focus:border-primary-500">
                <option :value="1">已发布</option>
                <option :value="0">草稿</option>
                <option :value="2">已禁用</option>
              </select>
            </div>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1.5">摘要</label>
            <Input v-model="form.summary" placeholder="请输入文档摘要" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1.5">标签（逗号分隔）</label>
            <Input v-model="form.tags" placeholder="如：Vue,前端,框架" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1.5">正文内容</label>
            <textarea
              v-model="form.content"
              rows="6"
              placeholder="请输入文档正文（Markdown）"
              class="w-full px-3 py-2 border border-gray-200 rounded-sm text-sm focus:outline-none focus:border-primary-500 font-mono"
            ></textarea>
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
import { confirmDialog, getApiError, notify } from '@/utils/toast'
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import Card from '@/components/ui/Card.vue'
import Input from '@/components/ui/Input.vue'
import Button from '@/components/ui/Button.vue'
import Badge from '@/components/ui/Badge.vue'
import { adminApi } from '@/api'
import type { CategoryVO, DocVO, DocInput } from '@/api/types'

const router = useRouter()

const goToCreate = () => {
  router.push('/docs/new')
}
const goToUpload = () => {
  router.push('/upload')
}

interface DocRow {
  id: number
  title: string
  category: string
  views: number
  createdAt: string
  status?: number
  raw: DocVO
}

const searchQuery = ref('')
const selectedCategory = ref('')
const showCategoryFilter = ref(false)
const currentPage = ref(1)
const pageSize = 10

const categoryOptions = ref<CategoryVO[]>([])
const allDocs = ref<DocRow[]>([])
const loading = ref(false)

const statusText = (s?: number) => (s === 1 ? '已发布' : s === 2 ? '已禁用' : '草稿')
const statusVariant = (s?: number): 'success' | 'warning' | 'danger' =>
  s === 1 ? 'success' : s === 2 ? 'danger' : 'warning'

const formatDate = (v?: string): string => {
  if (!v) return '—'
  return v.includes('T') ? v.slice(0, 10) : v.slice(0, 10)
}

const filteredDocs = computed(() => {
  let result = [...allDocs.value]
  if (searchQuery.value) {
    const q = searchQuery.value.toLowerCase()
    result = result.filter((d) => d.title.toLowerCase().includes(q))
  }
  if (selectedCategory.value) {
    result = result.filter((d) => d.category === selectedCategory.value)
  }
  return result
})

const totalDocs = computed(() => filteredDocs.value.length)
const totalPages = computed(() => Math.max(1, Math.ceil(totalDocs.value / pageSize)))
const pagedDocs = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return filteredDocs.value.slice(start, start + pageSize)
})

const visiblePages = computed(() => {
  const pages: number[] = []
  const total = totalPages.value
  const current = currentPage.value
  let start = Math.max(1, current - 2)
  let end = Math.min(total, current + 2)
  if (end - start + 1 < 5) {
    if (start === 1) end = Math.min(5, total)
    else start = Math.max(1, total - 4)
  }
  for (let i = start; i <= end; i++) pages.push(i)
  return pages
})

// ===== 弹窗 / 表单 =====
const showModal = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const form = ref<DocInput & { content?: string }>({
  title: '',
  summary: '',
  categoryId: 0,
  tags: '',
  content: '',
  status: 1,
})

const openEdit = (doc: DocRow) => {
  editingId.value = doc.id
  form.value = {
    title: doc.raw.title ?? '',
    summary: doc.raw.summary ?? '',
    categoryId: doc.raw.categoryId ?? 0,
    tags: doc.raw.tags ?? '',
    content: (doc.raw as Record<string, any>).content ?? '',
    status: doc.raw.status ?? 1,
  }
  showModal.value = true
}

const closeModal = () => {
  showModal.value = false
  editingId.value = null
}

const save = async () => {
  if (!form.value.title.trim()) {
    notify('请填写文档标题', 'warning')
    return
  }
  saving.value = true
  try {
    const payload: DocInput = {
      title: form.value.title,
      summary: form.value.summary,
      categoryId: form.value.categoryId || undefined,
      tags: form.value.tags,
      content: form.value.content,
      status: form.value.status,
    }
    if (editingId.value) {
      await adminApi.updateDoc(editingId.value, payload)
    } else {
      await adminApi.createDoc(payload)
    }
    notify(editingId.value ? '更新成功' : '创建成功', 'success')
    closeModal()
    await loadDocs()
  } catch (e: unknown) {
    notify('保存失败：' + getApiError(e), 'error')
  } finally {
    saving.value = false
  }
}

const removeDoc = async (doc: DocRow) => {
  if (!(await confirmDialog(`确定删除文档《${doc.title}》吗？此操作不可恢复。`))) return
  try {
    await adminApi.removeDoc(doc.id)
    notify('删除成功', 'success')
    if (pagedDocs.value.length === 1 && currentPage.value > 1) {
      currentPage.value -= 1
    }
    await loadDocs()
  } catch (e: unknown) {
    notify('删除失败：' + getApiError(e), 'error')
  }
}

const loadDocs = async () => {
  loading.value = true
  try {
    const [docPage, cats] = await Promise.all([
      adminApi.docs({ pageSize: 200 }),
      adminApi.categories(),
    ])
    categoryOptions.value = cats
    const records = (docPage.records ?? []) as DocVO[]
    allDocs.value = records.map((d) => ({
      id: d.id,
      title: d.title,
      category: categoryMapFrom(cats).get(d.categoryId ?? -1) ?? '未分类',
      views: d.viewCount ?? 0,
      createdAt: formatDate(d.createTime),
      status: d.status,
      raw: d,
    }))
  } catch (e: unknown) {
    notify('加载文档失败：' + getApiError(e), 'error')
  } finally {
    loading.value = false
  }
}

const categoryMapFrom = (cats: CategoryVO[]): Map<number, string> => {
  const map = new Map<number, string>()
  cats.forEach((c) => map.set(c.id, c.name))
  return map
}

onMounted(loadDocs)
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
</style>
