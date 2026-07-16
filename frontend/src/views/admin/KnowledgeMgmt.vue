<template>
  <AppShell>
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
              <Button size="sm" icon-name="plus" @click="showAddCategoryModal = true">新增</Button>
            </div>
          </template>
          <div class="space-y-1">
            <div
              v-for="category in categoryTree"
              :key="category.id"
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
                <Icon :name="getCategoryIconName(category.icon)" :size="16" />
                <span class="flex-1 text-sm">{{ category.name }}</span>
                <span class="text-xs text-gray-400">{{ category.count }}</span>
                <div class="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                  <button class="p-1 hover:bg-gray-200 rounded text-gray-400 hover:text-primary-500 transition-colors">
                    <Icon name="edit" :size="20" />
                  </button>
                  <button class="p-1 hover:bg-gray-200 rounded text-gray-400 hover:text-danger-500 transition-colors">
                    <Icon name="trash-2" :size="20" />
                  </button>
                </div>
              </div>
              <div
                v-if="category.children?.length && expandedCategories.includes(category.id)"
                class="ml-4 space-y-1 mt-1 children-container"
              >
                <div
                  v-for="child in category.children"
                  :key="child.id"
                  class="flex items-center gap-2 px-3 py-2 rounded-md cursor-pointer transition-colors group"
                  :class="{ 'bg-primary-50 text-primary-700': selectedCategory?.id === child.id }"
                  @click="selectedCategory = child"
                >
                  <div class="w-4 h-4"></div>
                  <Icon :name="getCategoryIconName(child.icon)" :size="16" />
                  <span class="flex-1 text-sm">{{ child.name }}</span>
                  <span class="text-xs text-gray-400">{{ child.count }}</span>
                  <div class="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                    <button class="p-1 hover:bg-gray-200 rounded text-gray-400 hover:text-primary-500 transition-colors">
                      <Icon name="edit" :size="20" />
                    </button>
                    <button class="p-1 hover:bg-gray-200 rounded text-gray-400 hover:text-danger-500 transition-colors">
                      <Icon name="trash-2" :size="20" />
                    </button>
                  </div>
                </div>
              </div>
            </div>
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
                        v-for="icon in iconOptions"
                        :key="icon"
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
                      v-for="cat in categoryTree"
                      :key="cat.id"
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
              <Button @click="saveCategory">保存</Button>
            </div>
          </div>
          <div v-else class="flex flex-col items-center justify-center py-16 text-gray-400">
            <Icon name="folder-tree" :size="64" />
            <p class="text-sm">请在左侧选择一个分类</p>
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
              v-for="(tag, index) in tags"
              :key="tag.id"
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
        </Card>
      </div>
    </div>
  </AppShell>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import AppShell from '@/components/layout/AppShell.vue'
import Card from '@/components/ui/Card.vue'
import Button from '@/components/ui/Button.vue'
import Input from '@/components/ui/Input.vue'

const activeTab = ref<'categories' | 'tags'>('categories')
const selectedCategory = ref<Category | null>(null)
const expandedCategories = ref<string[]>(['1'])
const showAddCategoryModal = ref(false)
const showAddTagModal = ref(false)
const showIconPicker = ref(false)
const showParentPicker = ref(false)

interface Category {
  id: string
  name: string
  icon: string
  count: number
  children?: Category[]
}

const categoryTree: Category[] = [
  {
    id: '1',
    name: '前端开发',
    icon: 'code',
    count: 1256,
    children: [
      { id: '1-1', name: 'Vue', icon: 'code', count: 356 },
      { id: '1-2', name: 'React', icon: 'code', count: 289 },
      { id: '1-3', name: 'TypeScript', icon: 'code', count: 234 },
      { id: '1-4', name: 'CSS', icon: 'code', count: 187 },
      { id: '1-5', name: '工程化', icon: 'settings', count: 190 },
    ],
  },
  {
    id: '2',
    name: '后端开发',
    icon: 'server',
    count: 986,
    children: [
      { id: '2-1', name: 'Node.js', icon: 'server', count: 267 },
      { id: '2-2', name: 'Java', icon: 'server', count: 345 },
      { id: '2-3', name: 'Python', icon: 'server', count: 213 },
      { id: '2-4', name: 'Go', icon: 'server', count: 161 },
    ],
  },
  {
    id: '3',
    name: '人工智能',
    icon: 'brain',
    count: 654,
    children: [
      { id: '3-1', name: '大语言模型', icon: 'brain', count: 198 },
      { id: '3-2', name: '机器学习', icon: 'brain', count: 234 },
      { id: '3-3', name: '深度学习', icon: 'brain', count: 222 },
    ],
  },
  {
    id: '4',
    name: '数据库',
    icon: 'database',
    count: 486,
    children: [
      { id: '4-1', name: 'MySQL', icon: 'database', count: 178 },
      { id: '4-2', name: 'Redis', icon: 'database', count: 156 },
      { id: '4-3', name: 'MongoDB', icon: 'database', count: 152 },
    ],
  },
  {
    id: '5',
    name: '运维',
    icon: 'monitor',
    count: 312,
    children: [
      { id: '5-1', name: 'Docker', icon: 'layers', count: 123 },
      { id: '5-2', name: 'Kubernetes', icon: 'layers', count: 109 },
      { id: '5-3', name: 'CI/CD', icon: 'settings', count: 80 },
    ],
  },
  {
    id: '6',
    name: '产品设计',
    icon: 'book-open',
    count: 248,
  },
]

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

watch(selectedCategory, (val) => {
  if (val) {
    categoryForm.name = val.name
    categoryForm.icon = val.icon
    categoryForm.sort = '0'
    categoryForm.parent = ''
  }
})

const toggleCategory = (id: string) => {
  const index = expandedCategories.value.indexOf(id)
  if (index > -1) {
    expandedCategories.value.splice(index, 1)
  } else {
    expandedCategories.value.push(id)
  }
}

const resetCategoryForm = () => {
  if (selectedCategory.value) {
    categoryForm.name = selectedCategory.value.name
    categoryForm.icon = selectedCategory.value.icon
    categoryForm.sort = '0'
    categoryForm.parent = ''
  }
}

const saveCategory = () => {
  alert('保存成功')
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
