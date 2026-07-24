<template>
    <div class="space-y-6 animate-fade-in">
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">知识库管理</h1>
          <p class="text-gray-500 text-sm mt-1">管理分类和标签，组织知识库内容</p>
        </div>
      </div>

      <div class="flex gap-1 p-1 bg-gray-100 rounded-lg w-fit">
        <button
          @click="activeTab = 'categories'"
          :class="[
            'px-4 py-2 text-sm font-medium rounded-md transition-all',
            activeTab === 'categories'
              ? 'bg-white text-gray-800 shadow-sm'
              : 'text-gray-500 hover:text-gray-700'
          ]"
        >
          <div class="flex items-center gap-2">
            <Icon name="folder-tree" :size="16" />
            <span>分类管理</span>
          </div>
        </button>
        <button
          @click="activeTab = 'tags'"
          :class="[
            'px-4 py-2 text-sm font-medium rounded-md transition-all',
            activeTab === 'tags'
              ? 'bg-white text-gray-800 shadow-sm'
              : 'text-gray-500 hover:text-gray-700'
          ]"
        >
          <div class="flex items-center gap-2">
            <Icon name="tags" :size="16" />
            <span>标签管理</span>
          </div>
        </button>
      </div>

      <div v-show="activeTab === 'categories'" class="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <Card class="lg:col-span-1">
          <template #header>
            <div class="flex items-center justify-between">
              <h3 class="font-semibold text-gray-800">分类树</h3>
              <Button size="sm" icon-name="plus" @click="openCreate">新增</Button>
            </div>
          </template>
          <div class="space-y-1">
            <div
              v-for="category in categoryTree" :key="category.id"
              class="tree-item"
            >
              <div
                class="flex items-center gap-2 px-3 py-2 rounded-md cursor-pointer transition-colors group"
                :class="{ 'bg-primary-50 text-primary-700': selectedCategory?.id === category.id }"
                @click="selectedCategory = category"
              >
                <button
                  v-if="category.children?.length"
                  @click.stop="toggleCategory(category.id)"
                  class="p-0.5 hover:bg-gray-200 rounded transition-colors"
                >
                  <Icon name="chevron-right" :size="16" />
                </button>
                <div v-else class="w-4 h-4"></div>
                <Icon :name="getCategoryIconName(category.icon ?? 'code')" :size="16" />
                <span class="flex-1 text-sm">{{ category.name }}</span>
                <span class="text-xs text-gray-400">{{ category.docCount ?? 0 }}</span>
                <div class="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                  <button class="p-1 hover:bg-gray-200 rounded text-gray-400 hover:text-primary-500 transition-colors" @click.stop="selectedCategory = category">
                    <Icon name="edit" :size="20" />
                  </button>
                  <button class="p-1 hover:bg-gray-200 rounded text-gray-400 hover:text-danger-500 transition-colors" @click.stop="deleteCategory(category)">
                    <Icon name="trash-2" :size="20" />
                  </button>
                </div>
              </div>
              <div
                v-if="category.children?.length && expandedCategories.includes(category.id)"
                class="ml-4 space-y-1 mt-1 children-container"
              >
                <div
                  v-for="child in category.children" :key="child.id"
                  class="flex items-center gap-2 px-3 py-2 rounded-md cursor-pointer transition-colors group"
                  :class="{ 'bg-primary-50 text-primary-700': selectedCategory?.id === child.id }"
                  @click="selectedCategory = child"
                >
                  <div class="w-4 h-4"></div>
                  <Icon :name="getCategoryIconName(child.icon ?? 'code')" :size="16" />
                  <span class="flex-1 text-sm">{{ child.name }}</span>
                  <span class="text-xs text-gray-400">{{ child.docCount ?? 0 }}</span>
                  <div class="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                    <button class="p-1 hover:bg-gray-200 rounded text-gray-400 hover:text-primary-500 transition-colors" @click.stop="selectedCategory = child">
                      <Icon name="edit" :size="20" />
                    </button>
                    <button class="p-1 hover:bg-gray-200 rounded text-gray-400 hover:text-danger-500 transition-colors" @click.stop="deleteCategory(child)">
                      <Icon name="trash-2" :size="20" />
                    </button>
                  </div>
                </div>
              </div>
            </div>
            <p v-if="categoryTree.length === 0" class="text-sm text-gray-400 px-3 py-6 text-center">暂无分类</p>
          </div>
        </Card>

        <Card class="lg:col-span-2">
          <template #header>
            <h3 class="font-semibold text-gray-800">分类详情</h3>
          </template>
          <div v-if="selectedCategory" class="space-y-6 animate-fade-in">
            <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1.5">分类名称</label>
                <Input v-model="categoryForm.name" placeholder="请输入分类名称" />
              </div>
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1.5">图标</label>
                <div class="relative">
                  <button
                    @click="showIconPicker = !showIconPicker"
                    class="w-full flex items-center gap-3 px-3 py-2 border border-gray-200 rounded-sm hover:bg-gray-50 transition-colors"
                  >
                    <Icon :name="getCategoryIconName(categoryForm.icon)" :size="20" class="text-gray-600" />
                    <span class="text-sm text-gray-600">{{ categoryForm.icon }}</span>
                    <Icon name="chevron-down" :size="16" />
                  </button>
                  <div
                    v-if="showIconPicker"
                    class="absolute top-full left-0 mt-1 w-full bg-white border border-gray-200 rounded-sm shadow-lg z-10 p-3 animate-dropdown"
                  >
                    <div class="grid grid-cols-5 gap-2">
                      <button
                        v-for="icon in iconOptions" :key="icon"
                        @click="categoryForm.icon = icon; showIconPicker = false"
                        class="p-2 rounded hover:bg-gray-100 transition-colors flex items-center justify-center"
                        :class="{ 'bg-primary-50 text-primary-500': categoryForm.icon === icon }"
                      >
                        <Icon :name="getCategoryIconName(icon)" :size="20" />
                      </button>
                    </div>
                  </div>
                </div>
              </div>
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1.5">排序</label>
                <Input v-model="categoryForm.sort" type="number" placeholder="请输入排序值" />
              </div>
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1.5">父级分类</label>
                <div class="relative">
                  <button
                    @click="showParentPicker = !showParentPicker"
                    class="w-full flex items-center gap-3 px-3 py-2 border border-gray-200 rounded-sm hover:bg-gray-50 transition-colors text-left"
                  >
                    <span class="text-sm text-gray-600">{{ categoryForm.parent || '无（顶级分类）' }}</span>
                    <Icon name="chevron-down" :size="16" />
                  </button>
                  <div
                    v-if="showParentPicker"
                    class="absolute top-full left-0 mt-1 w-full bg-white border border-gray-200 rounded-sm shadow-lg z-10 py-1 animate-dropdown"
                  >
                    <button
                      @click="categoryForm.parent = ''; showParentPicker = false"
                      class="w-full px-3 py-2 text-left text-sm hover:bg-gray-50 transition-colors"
                      :class="{ 'bg-primary-50 text-primary-600': !categoryForm.parent }"
                    >
                      无（顶级分类）
                    </button>
                    <button
                      v-for="cat in categoryTree" :key="cat.id"
                      @click="categoryForm.parent = cat.name; showParentPicker = false"
                      class="w-full px-3 py-2 text-left text-sm hover:bg-gray-50 transition-colors"
                      :class="{ 'bg-primary-50 text-primary-600': categoryForm.parent === cat.name }"
                    >
                      {{ cat.name }}
                    </button>
                  </div>
                </div>
              </div>
            </div>
            <div class="flex items-center justify-end gap-3 pt-4 border-t border-gray-100">
              <Button variant="secondary" @click="resetCategoryForm">重置</Button>
              <Button :disabled="saving" @click="saveCategory">{{ saving ? '保存中...' : '保存' }}</Button>
            </div>
          </div>
          <div v-else class="flex flex-col items-center justify-center py-16 text-gray-400">
            <Icon name="folder-tree" :size="64" />
            <p class="text-sm">请在左侧选择一个分类，或点击「新增」创建分类</p>
          </div>
        </Card>
      </div>

      <div v-show="activeTab === 'tags'" class="space-y-6">
        <Card>
          <template #header>
            <div class="flex items-center justify-between">
              <h3 class="font-semibold text-gray-800">标签列表</h3>
              <Button size="sm" icon-name="plus" @click="showAddTagModal = true">新增标签</Button>
            </div>
          </template>
          <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-4">
            <div
              v-for="(tag, index) in tags" :key="tag.id"
              class="group relative p-4 border border-gray-200 rounded-lg hover:border-gray-300 hover:shadow-md transition-all cursor-pointer tag-card"
              :style="{ animationDelay: `${index * 50}ms` }"
            >
              <div class="flex items-start justify-between mb-3">
                <div
                  class="w-8 h-8 rounded-lg flex items-center justify-center"
                  :style="{ backgroundColor: tag.color + '20' }"
                >
                  <Icon name="tag" :size="16" :style="{ color: tag.color }" />
                </div>
                <div class="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                  <button class="p-1 hover:bg-gray-100 rounded text-gray-400 hover:text-primary-500 transition-colors">
                    <Icon name="edit" :size="20" />
                  </button>
                  <button class="p-1 hover:bg-gray-100 rounded text-gray-400 hover:text-danger-500 transition-colors">
                    <Icon name="trash-2" :size="20" />
                  </button>
                </div>
              </div>
              <h4 class="font-medium text-gray-800 mb-1">{{ tag.name }}</h4>
              <div class="flex items-center justify-between">
                <span class="text-xs text-gray-500">{{ tag.count }} 篇文档</span>
                <div class="w-4 h-4 rounded-full" :style="{ backgroundColor: tag.color }"></div>
              </div>
            </div>
          </div>
          <p class="text-xs text-gray-400 mt-4">标签由文档的 tags 字段自动聚合生成，此处为展示视图（后端无独立标签实体）。</p>
        </Card>
      </div>
    </div>
</template>

<script setup lang="ts">
import { confirmDialog, notify } from '@/utils/toast'
import { ref, reactive, watch, onMounted } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import Card from '@/components/ui/Card.vue'
import Button from '@/components/ui/Button.vue'
import Input from '@/components/ui/Input.vue'
import { categoriesApi, adminApi } from '@/api'
import type { CategoryVO } from '@/api/types'

const activeTab = ref<'categories' | 'tags'>('categories')
const selectedCategory = ref<CategoryVO | null>(null)
const expandedCategories = ref<number[]>([])
const showAddTagModal = ref(false)
const showIconPicker = ref(false)
const showParentPicker = ref(false)
const saving = ref(false)

const categoryTree = ref<CategoryVO[]>([])

const iconOptions = ['code', 'server', 'database', 'brain', 'settings', 'monitor', 'wifi', 'layers', 'book-open']

const getCategoryIconName = (iconName: string): string => {
  return iconOptions.includes(iconName) ? iconName : 'code'
}

const categoryForm = reactive({
  name: '',
  icon: 'code',
  sort: '0',
  parent: '',
})

const flatList = (list: CategoryVO[]): CategoryVO[] => {
  const out: CategoryVO[] = []
  list.forEach((c) => {
    out.push(c)
    if (c.children) out.push(...flatList(c.children))
  })
  return out
}

const resolveParentId = (parentName: string): number | undefined => {
  if (!parentName) return undefined
  const found = flatList(categoryTree.value).find((c) => c.name === parentName)
  return found?.id
}

watch(selectedCategory, (val) => {
  if (val) {
    categoryForm.name = val.name
    categoryForm.icon = val.icon ?? 'code'
    categoryForm.sort = String(val.sortOrder ?? 0)
    // 反查父级名称
    const parent = flatList(categoryTree.value).find((c) => c.children?.some((ch) => ch.id === val.id))
    categoryForm.parent = parent?.name ?? ''
  }
})

const toggleCategory = (id: number) => {
  const index = expandedCategories.value.indexOf(id)
  if (index > -1) {
    expandedCategories.value.splice(index, 1)
  } else {
    expandedCategories.value.push(id)
  }
}

const openCreate = () => {
  selectedCategory.value = null
  categoryForm.name = ''
  categoryForm.icon = 'code'
  categoryForm.sort = '0'
  categoryForm.parent = ''
}

const resetCategoryForm = () => {
  if (selectedCategory.value) {
    categoryForm.name = selectedCategory.value.name
    categoryForm.icon = selectedCategory.value.icon ?? 'code'
    categoryForm.sort = String(selectedCategory.value.sortOrder ?? 0)
  }
}

const saveCategory = async () => {
  if (!categoryForm.name.trim()) {
    notify('请填写分类名称', 'warning')
    return
  }
  saving.value = true
  const payload = {
    name: categoryForm.name,
    icon: categoryForm.icon,
    sortOrder: Number(categoryForm.sort) || 0,
    parentId: resolveParentId(categoryForm.parent),
  }
  try {
    if (selectedCategory.value?.id) {
      await adminApi.updateCategory(selectedCategory.value.id, payload)
      notify('分类已更新', 'success')
    } else {
      const created = await adminApi.createCategory(payload)
      notify('分类已创建', 'success')
      selectedCategory.value = created ?? null
    }
    await loadTree()
  } catch (e: any) {
    notify('保存失败：' + (e?.response?.data?.message || e?.message || '未知错误'), 'error')
  } finally {
    saving.value = false
  }
}

const deleteCategory = async (category: CategoryVO) => {
  if (!(await confirmDialog(`确定删除分类「${category.name}」吗？其子分类将一并受影响。`))) return
  try {
    await adminApi.removeCategory(category.id)
    notify('删除成功', 'success')
    if (selectedCategory.value?.id === category.id) openCreate()
    await loadTree()
  } catch (e: any) {
    notify('删除失败：' + (e?.response?.data?.message || e?.message || '未知错误'), 'error')
  }
}

interface Tag {
  id: string
  name: string
  color: string
  count: number
}

const tags: Tag[] = [
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
]

const loadTree = async () => {
  try {
    categoryTree.value = await categoriesApi.tree()
  } catch (e: any) {
    notify('加载分类失败：' + (e?.response?.data?.message || e?.message || '未知错误'), 'error')
  }
}

onMounted(loadTree)
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

.children-container {
  animation: slideDown 0.3s ease-out;
}

@keyframes slideDown {
  from {
    opacity: 0;
    transform: translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
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
</style>
