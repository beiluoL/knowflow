<template>
  <div class="cat-mgmt animate-fade-in">
    <nav class="flex items-center gap-2 text-sm text-gray-500 mb-6">
      <span class="text-primary-500 font-medium">管理后台</span>
      <Icon name="chevron-right" :size="14" />
      <span class="text-gray-700 font-medium">分类管理</span>
    </nav>

    <!-- 页面标题 -->
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-bold text-gray-800">分类管理</h1>
        <p class="text-gray-500 text-sm mt-1">管理知识库目录与分类结构</p>
      </div>
      <div class="flex items-center gap-3">
        <Button variant="secondary" icon-name="plus" @click="openCreate()">新增分类</Button>
        <Button variant="secondary" icon-name="chevrons-down" @click="expandAll">展开全部</Button>
        <Button variant="secondary" icon-name="chevrons-up" @click="collapseAll">折叠全部</Button>
      </div>
    </div>

    <!-- 搜索框 -->
    <div class="bg-white border border-[var(--kb-border)] rounded-xl px-6 py-4 mb-4">
      <div class="max-w-md">
        <Input v-model="searchKeyword" placeholder="搜索分类名称..." icon-name="search" />
      </div>
    </div>

    <!-- 统计条 -->
    <div class="flex items-center gap-6 mb-4 text-sm text-gray-500">
      <span><strong class="text-gray-700">{{ topLevelCount }}</strong> 个顶级分类</span>
      <span>/</span>
      <span><strong class="text-gray-700">{{ subLevelCount }}</strong> 个子分类</span>
      <span>/</span>
      <span>共 <strong class="text-gray-700">{{ totalCount }}</strong> 个</span>
    </div>

    <!-- 树形列表 -->
    <div class="bg-white border border-[var(--kb-border)] rounded-xl overflow-hidden">
      <!-- 表头 -->
      <div class="px-6 py-3 border-b border-[var(--kb-border)]">
        <div class="grid grid-cols-12 gap-4 text-xs text-gray-500 font-medium items-center">
          <div class="col-span-5">分类名称</div>
          <div class="col-span-2 text-center">层级</div>
          <div class="col-span-2 text-center">文档数</div>
          <div class="col-span-3 text-center">操作</div>
        </div>
      </div>

      <!-- 树行 -->
      <div v-if="flatTreeNodes.length" class="divide-y divide-[#E2E6EC]/50">
        <div
          v-for="node in flatTreeNodes"
          :key="node.cat.id"
          class="px-6 py-3 hover:bg-gray-50/80 transition-colors group relative"
          :class="{
            'opacity-40 cursor-grabbing': draggingId === node.cat.id,
            'cursor-grab': !searchKeyword && draggingId !== node.cat.id,
          }"
          :draggable="!searchKeyword"
          @dragstart="onDragStart($event, node)"
          @dragend="onDragEnd"
          @dragover="onDragOver($event, node)"
          @dragleave="onDragLeave"
          @drop="onDrop($event, node)"
        >
          <!-- before 指示线 -->
          <div
            v-if="dropTargetId === node.cat.id && dropPosition === 'before'"
            class="absolute top-[-2px] left-6 right-6 h-[3px] bg-[#3B6FE0] rounded-full z-10"
          />
          <!-- after 指示线 -->
          <div
            v-if="dropTargetId === node.cat.id && dropPosition === 'after'"
            class="absolute bottom-[-2px] left-6 right-6 h-[3px] bg-[#3B6FE0] rounded-full z-10"
          />
          <!-- inside 高亮 -->
          <div
            v-if="dropTargetId === node.cat.id && dropPosition === 'inside'"
            class="absolute inset-y-1 inset-x-3 bg-[#3B6FE0]/[0.06] border border-[#3B6FE0]/30 rounded-lg z-0 pointer-events-none"
          />

          <div class="grid grid-cols-12 gap-4 items-center relative z-[1]">
            <!-- 名称列 -->
            <div class="col-span-5 flex items-center gap-2 min-w-0">
              <!-- 展开箭头 / 占位 -->
              <button
                v-if="node.hasChildren"
                class="w-5 h-5 flex items-center justify-center text-gray-400 hover:text-gray-700 flex-shrink-0 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
                :style="{ marginLeft: node.level * 24 + 'px' }"
                @click="toggleExpand(node.cat.id)"
                :title="expandedIds.has(node.cat.id) ? '折叠' : '展开'"
              >
                <Icon :name="expandedIds.has(node.cat.id) ? 'chevron-down' : 'chevron-right'" :size="14" aria-hidden="true" />
              </button>
              <div v-else :style="{ marginLeft: node.level * 24 + 20 + 'px' }" class="flex-shrink-0"></div>
              <!-- 图标 -->
              <div
                class="w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0"
                :style="{ backgroundColor: getCatColor(node.cat.icon) + '15' }"
              >
                <Icon :name="getCatIconName(node.cat.icon)" :size="16" :color="getCatColor(node.cat.icon)" />
              </div>
              <!-- 名称 -->
              <span class="font-medium text-gray-800 truncate">{{ node.cat.name }}</span>
              <!-- 拖拽提示 -->
              <span
                v-if="dropTargetId === node.cat.id && dropPosition === 'inside'"
                class="text-xs text-[#3B6FE0] font-medium ml-1"
              >放入</span>
            </div>
            <!-- 层级标签 -->
            <div class="col-span-2 text-center">
              <span :class="levelBadgeClass(node.level)">
                L{{ node.level + 1 }}
              </span>
            </div>
            <!-- 文档数 -->
            <div class="col-span-2 text-center text-sm text-gray-600">
              {{ node.cat.docCount ?? 0 }} 篇
            </div>
            <!-- 操作 -->
            <div class="col-span-3 flex items-center justify-center gap-1">
              <button
                class="p-1.5 text-gray-400 hover:text-primary-500 hover:bg-primary-50 rounded transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
                @click="openCreate(node.cat)"
                title="新增子分类"
              >
                <Icon name="folder-plus" :size="15" aria-hidden="true" />
              </button>
              <button
                class="p-1.5 text-gray-400 hover:text-primary-500 hover:bg-primary-50 rounded transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
                @click="openEdit(node.cat)"
                title="编辑"
              >
                <Icon name="edit-2" :size="15" aria-hidden="true" />
              </button>
              <button
                class="p-1.5 text-gray-400 hover:text-blue-500 hover:bg-blue-50 rounded transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
                @click="openMove(node.cat)"
                title="移动"
              >
                <Icon name="move" :size="15" aria-hidden="true" />
              </button>
              <button
                class="p-1.5 text-gray-400 hover:text-danger-500 hover:bg-danger-50 rounded transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
                @click="deleteCategory(node.cat)"
                title="删除"
              >
                <Icon name="trash-2" :size="15" aria-hidden="true" />
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-else class="py-16 text-center text-gray-400 text-sm">
        <Icon name="folder" :size="36" class="mx-auto mb-3 opacity-40" />
        <p>暂无分类数据</p>
      </div>

      <!-- 底部统计 -->
      <div v-if="flatTreeNodes.length" class="px-6 py-3 text-center border-t border-[var(--kb-border)]/50">
        <span class="text-sm text-gray-400">显示 {{ flatTreeNodes.length }} / {{ allFlatCats.length }} 个分类</span>
      </div>
    </div>

    <!-- ========== 新增/编辑弹窗 ========== -->
    <div
      v-if="showFormModal"
      class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
      role="button"
      tabindex="0"
      @click.self="closeFormModal"
      @keydown.enter.self.prevent="($event.target as HTMLElement).click()"
    >
      <div class="bg-white rounded-xl w-full max-w-md p-6 animate-scale-in max-h-[90vh] overflow-y-auto">
        <h3 class="text-lg font-semibold text-gray-800 mb-4">
          {{ editingCat ? '编辑分类' : '新增分类' }}
        </h3>
        <div class="space-y-4">
          <!-- 名称 -->
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1.5">分类名称</label>
            <Input v-model="form.name" placeholder="请输入分类名称" />
          </div>
          <!-- 父分类 -->
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1.5">
              父分类
              <span class="text-xs text-gray-400 font-normal ml-1">（不选则为顶级分类）</span>
            </label>
            <CategoryTreeSelect
              v-model="form.parentId"
              :categories="treeData"
              :exclude-ids="editingCat ? [editingCat.id] : []"
              placeholder="请选择父分类"
              empty-label="作为顶级分类"
            />
          </div>
          <!-- 描述 -->
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1.5">描述</label>
            <Input v-model="form.description" placeholder="请输入分类描述" />
          </div>
          <!-- 图标选择 -->
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1.5">图标</label>
            <div class="flex flex-wrap gap-1.5 mb-3">
              <button
                v-for="cat in presetIconCategories"
                :key="cat.key"
                class="focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
                @click="activeIconCategory = cat.key"
                :class="[
                  'px-2.5 py-1 text-xs rounded-md border transition-colors',
                  activeIconCategory === cat.key
                    ? 'border-primary-500 bg-primary-50 text-primary-600 font-medium'
                    : 'border-[var(--kb-border)] text-gray-500 hover:border-gray-300',
                ]"
              >
                {{ cat.label }}
              </button>
            </div>
            <div class="grid grid-cols-8 gap-2 max-h-44 overflow-y-auto p-1 border border-[var(--kb-border)] rounded-lg">
              <button
                v-for="icon in filteredPresetIcons"
                :key="icon.key"
                class="focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
                @click="selectPresetIcon(icon.key)"
                :title="`${icon.name}（${icon.key}）`"
                :class="[
                  'aspect-square rounded-md flex items-center justify-center border transition-colors',
                  selectedIconKey === icon.key
                    ? 'border-primary-500 bg-primary-50 ring-1 ring-primary-500'
                    : 'border-transparent hover:border-gray-300 hover:bg-gray-50',
                ]"
              >
                <Icon :name="icon.svg" :size="22" aria-hidden="true" />
              </button>
            </div>
            <!-- 颜色选择 -->
            <div v-if="selectedIconKey" class="mt-3 flex items-center gap-2 flex-wrap">
              <span class="text-xs text-gray-500">颜色：</span>
              <button
                v-for="c in iconColorPresets"
                :key="c"
                type="button"
                class="focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
                @click="selectIconColor(c)"
                :class="[
                  'w-6 h-6 rounded-full border-2 transition-colors',
                  selectedIconColor === c ? 'border-gray-700 scale-110' : 'border-white shadow-sm hover:scale-110',
                ]"
                :style="{ backgroundColor: c }"
                :title="c"
              />
              <label class="flex items-center gap-1 cursor-pointer text-xs text-gray-500" title="自定义颜色">
                <input
                  :value="selectedIconColor"
                  type="color"
                  class="w-6 h-6 rounded cursor-pointer border border-gray-200 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
                  @input="selectIconColor(($event.target as HTMLInputElement).value)"
                />
                <span>自定义</span>
              </label>
              <button
                v-if="selectedIconColor"
                type="button"
                class="text-xs text-gray-400 hover:text-red-500 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
                @click="selectIconColor('')"
              >清除</button>
              <div class="ml-auto flex items-center gap-2">
                <span class="text-xs text-gray-400">预览：</span>
                <div
                  class="w-9 h-9 rounded-lg flex items-center justify-center"
                  :style="{ backgroundColor: (selectedIconColor || currentPresetDefaultColor) + '15' }"
                >
                  <Icon :name="currentRenderIconName" :size="20" :color="selectedIconColor || currentPresetDefaultColor" />
                </div>
              </div>
            </div>
          </div>
          <!-- 排序 -->
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1.5">排序</label>
            <Input v-model.number="form.sortOrder" type="number" placeholder="数字越小越靠前" />
          </div>
        </div>
        <div class="flex justify-end gap-3 mt-6">
          <Button variant="secondary" @click="closeFormModal">取消</Button>
          <Button :disabled="saving" @click="saveCategory">{{ saving ? '保存中...' : '确定' }}</Button>
        </div>
      </div>
    </div>

    <!-- ========== 移动弹窗 ========== -->
    <div
      v-if="showMoveModal"
      class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
      role="button"
      tabindex="0"
      @click.self="closeMoveModal"
      @keydown.enter.self.prevent="($event.target as HTMLElement).click()"
    >
      <div class="bg-white rounded-xl w-full max-w-sm p-6 animate-scale-in">
        <h3 class="text-lg font-semibold text-gray-800 mb-2">移动分类</h3>
        <p class="text-sm text-gray-500 mb-4">
          将「<span class="text-gray-700 font-medium">{{ movingCat?.name }}</span>」移动到：
        </p>
        <CategoryTreeSelect
          v-model="moveTargetId"
          :categories="treeData"
          :exclude-ids="movingCat ? [movingCat.id] : []"
          placeholder="请选择目标父分类"
          empty-label="作为顶级分类"
        />
        <div class="flex justify-end gap-3 mt-6">
          <Button variant="secondary" @click="closeMoveModal">取消</Button>
          <Button :disabled="moving" @click="doMove">{{ moving ? '移动中...' : '确定移动' }}</Button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import Button from '@/components/ui/Button.vue'
import Input from '@/components/ui/Input.vue'
import CategoryTreeSelect from '@/components/ui/CategoryTreeSelect.vue'
import { notify, confirmDialog, getApiError } from '@/utils/toast'
import { adminApi } from '@/api'
import type { CategoryVO, CategoryInput } from '@/api/types'
import {
  presetIcons, presetIconCategories, getIconByKey, iconColorPresets,
  parseIconValue, buildIconValue, resolveIconForRender,
  type PresetIcon,
} from '@/utils/presetIcons'

// ========== 数据状态 ==========
const treeData = ref<CategoryVO[]>([])
const searchKeyword = ref('')
const expandedIds = ref<Set<number>>(new Set())

// ========== 扁平化树 & 统计 ==========
interface FlatNode {
  cat: CategoryVO
  level: number
  hasChildren: boolean
}

const allFlatCats = computed<CategoryVO[]>(() => {
  const result: CategoryVO[] = []
  const walk = (nodes: CategoryVO[]) => {
    for (const node of nodes) {
      result.push(node)
      if (node.children?.length) walk(node.children)
    }
  }
  walk(treeData.value)
  return result
})

const flatTreeNodes = computed<FlatNode[]>(() => {
  const kw = searchKeyword.value.trim().toLowerCase()
  if (kw) {
    return allFlatCats.value
      .filter(c => c.name.toLowerCase().includes(kw) || (c.description || '').toLowerCase().includes(kw))
      .map(c => ({ cat: c, level: 0, hasChildren: false }))
  }
  const result: FlatNode[] = []
  const walk = (nodes: CategoryVO[], level: number) => {
    for (const node of nodes) {
      const hasChildren = (node.children?.length ?? 0) > 0
      result.push({ cat: node, level, hasChildren })
      if (hasChildren && expandedIds.value.has(node.id)) {
        walk(node.children!, level + 1)
      }
    }
  }
  walk(treeData.value, 0)
  return result
})

const topLevelCount = computed(() => treeData.value.length)
const subLevelCount = computed(() => allFlatCats.value.length - treeData.value.length)
const totalCount = computed(() => allFlatCats.value.length)

// ========== 展开折叠 ==========
const toggleExpand = (id: number) => {
  const next = new Set(expandedIds.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  expandedIds.value = next
}
const expandAll = () => {
  const ids = new Set<number>()
  const walk = (nodes: CategoryVO[]) => {
    for (const node of nodes) {
      if (node.children?.length) {
        ids.add(node.id)
        walk(node.children)
      }
    }
  }
  walk(treeData.value)
  expandedIds.value = ids
}
const collapseAll = () => {
  expandedIds.value = new Set()
}

// ========== 图标解析 ==========
const getCatIconName = (raw?: string): string => {
  if (!raw) return 'folder'
  const { name } = resolveIconForRender(raw)
  return name || 'folder'
}
const getCatColor = (raw?: string): string => {
  if (!raw) return '#6B7280'
  const { color } = resolveIconForRender(raw)
  return color || '#6B7280'
}

// ========== 层级标签样式 ==========
const levelBadgeClass = (level: number): string => {
  const base = 'inline-flex items-center px-2 py-0.5 rounded text-[10px] font-medium'
  if (level === 0) return `${base} bg-green-50 text-green-600`
  if (level === 1) return `${base} bg-blue-50 text-blue-600`
  return `${base} bg-purple-50 text-purple-600`
}

// ========== 新增/编辑弹窗 ==========
const showFormModal = ref(false)
const editingCat = ref<CategoryVO | null>(null)
const saving = ref(false)

const form = reactive({
  name: '',
  description: '',
  icon: '',
  parentId: undefined as number | undefined,
  sortOrder: 0,
})

// 图标选择
const activeIconCategory = ref<PresetIcon['category']>('language')
const filteredPresetIcons = computed(() =>
  presetIcons.filter(icon => icon.category === activeIconCategory.value)
)
const selectedIconKey = computed(() => parseIconValue(form.icon).key)
const selectedIconColor = computed(() => parseIconValue(form.icon).color)
const currentPresetDefaultColor = computed(() => getIconByKey(selectedIconKey.value)?.color || '#6B7280')
const currentRenderIconName = computed(() => {
  const preset = getIconByKey(selectedIconKey.value)
  return preset?.svg || selectedIconKey.value || 'folder'
})
const selectPresetIcon = (key: string) => {
  const keepColor = selectedIconColor.value
  form.icon = buildIconValue(key, keepColor)
}
const selectIconColor = (color: string) => {
  form.icon = buildIconValue(selectedIconKey.value, color)
}

const openCreate = (parent?: CategoryVO) => {
  editingCat.value = null
  form.name = ''
  form.description = ''
  form.icon = 'lang-java'
  form.parentId = parent?.id ?? undefined
  form.sortOrder = 0
  activeIconCategory.value = 'language'
  showFormModal.value = true
}

const openEdit = (cat: CategoryVO) => {
  editingCat.value = cat
  form.name = cat.name
  form.description = cat.description || ''
  form.icon = cat.icon || 'lang-java'
  form.parentId = cat.parentId && cat.parentId !== 0 ? cat.parentId : undefined
  form.sortOrder = cat.sortOrder ?? 0
  const key = parseIconValue(cat.icon).key
  const preset = getIconByKey(key)
  if (preset) activeIconCategory.value = preset.category
  showFormModal.value = true
}

const closeFormModal = () => {
  showFormModal.value = false
  editingCat.value = null
}

const saveCategory = async () => {
  if (!form.name.trim()) {
    notify('请填写分类名称', 'warning')
    return
  }
  if (editingCat.value?.id && editingCat.value.id === form.parentId) {
    notify('父分类不能是自身', 'warning')
    return
  }
  saving.value = true
  try {
    const payload: CategoryInput = {
      name: form.name,
      description: form.description,
      icon: form.icon,
      sortOrder: form.sortOrder,
      parentId: form.parentId ?? 0,
    }
    if (editingCat.value?.id) {
      await adminApi.updateCategory(editingCat.value.id, payload)
      notify('分类已更新', 'success')
    } else {
      await adminApi.createCategory(payload)
      notify('分类已创建', 'success')
    }
    await loadTree()
    closeFormModal()
  } catch (e: unknown) {
    notify('保存失败：' + getApiError(e), 'error')
  } finally {
    saving.value = false
  }
}

// ========== 移动弹窗 ==========
const showMoveModal = ref(false)
const movingCat = ref<CategoryVO | null>(null)
const moveTargetId = ref<number | undefined>(undefined)
const moving = ref(false)

const openMove = (cat: CategoryVO) => {
  movingCat.value = cat
  moveTargetId.value = undefined
  showMoveModal.value = true
}
const closeMoveModal = () => {
  showMoveModal.value = false
  movingCat.value = null
  moveTargetId.value = undefined
}
const doMove = async () => {
  if (!movingCat.value) return
  moving.value = true
  try {
    const payload: CategoryInput = {
      name: movingCat.value.name,
      description: movingCat.value.description,
      icon: movingCat.value.icon,
      sortOrder: movingCat.value.sortOrder ?? 0,
      parentId: moveTargetId.value ?? 0,
    }
    await adminApi.updateCategory(movingCat.value.id, payload)
    notify('分类已移动', 'success')
    await loadTree()
    closeMoveModal()
  } catch (e: unknown) {
    notify('移动失败：' + getApiError(e), 'error')
  } finally {
    moving.value = false
  }
}

// ========== 删除 ==========
const deleteCategory = async (cat: CategoryVO) => {
  if (!(await confirmDialog(`确定删除分类「${cat.name}」吗？`))) return
  try {
    await adminApi.removeCategory(cat.id)
    notify('删除成功', 'success')
    await loadTree()
  } catch (e: unknown) {
    notify('删除失败：' + getApiError(e), 'error')
  }
}

// ========== 拖拽移动 ==========
const draggingId = ref<number | null>(null)
const dropTargetId = ref<number | null>(null)
const dropPosition = ref<'before' | 'inside' | 'after' | null>(null)

const onDragStart = (e: DragEvent, node: FlatNode) => {
  draggingId.value = node.cat.id
  e.dataTransfer?.setData('text/plain', String(node.cat.id))
  e.dataTransfer!.effectAllowed = 'move'
}

const onDragEnd = () => {
  draggingId.value = null
  dropTargetId.value = null
  dropPosition.value = null
}

const onDragOver = (e: DragEvent, node: FlatNode) => {
  e.preventDefault()
  const sourceId = draggingId.value
  if (!sourceId || sourceId === node.cat.id) return
  if (isDescendant(node.cat.id, sourceId)) return

  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
  const ratio = (e.clientY - rect.top) / rect.height

  dropTargetId.value = node.cat.id
  if (ratio < 0.3) {
    dropPosition.value = 'before'
  } else if (ratio > 0.7) {
    dropPosition.value = 'after'
  } else {
    dropPosition.value = 'inside'
  }

  e.dataTransfer!.dropEffect = 'move'
}

const onDragLeave = () => {
  // 延迟清除避免闪烁；由 dragover 重新设置
  setTimeout(() => {
    if (draggingId.value === null) {
      dropTargetId.value = null
      dropPosition.value = null
    }
  }, 50)
}

const onDrop = async (e: DragEvent, node: FlatNode) => {
  e.preventDefault()
  const sourceId = draggingId.value
  const targetId = dropTargetId.value
  const position = dropPosition.value
  if (!sourceId || !targetId || !position) return
  if (sourceId === node.cat.id) return
  if (isDescendant(node.cat.id, sourceId)) return

  await handleDropMove(sourceId, node.cat, position)

  draggingId.value = null
  dropTargetId.value = null
  dropPosition.value = null
}

/** 判断 targetId 是否是 ancestorId 的子孙节点 */
const isDescendant = (targetId: number, ancestorId: number): boolean => {
  const findNode = (nodes: CategoryVO[], id: number): CategoryVO | null => {
    for (const n of nodes) {
      if (n.id === id) return n
      if (n.children) {
        const found = findNode(n.children, id)
        if (found) return found
      }
    }
    return null
  }
  const ancestor = findNode(treeData.value, ancestorId)
  if (!ancestor) return false

  const check = (nodes: CategoryVO[]): boolean => {
    for (const n of nodes) {
      if (n.id === targetId) return true
      if (n.children && check(n.children)) return true
    }
    return false
  }
  return ancestor.children ? check(ancestor.children) : false
}

/** 执行拖拽移动 */
const handleDropMove = async (
  sourceId: number,
  target: CategoryVO,
  position: 'before' | 'inside' | 'after',
) => {
  const sourceNode = allFlatCats.value.find((c) => c.id === sourceId)
  if (!sourceNode) return

  let newParentId: number
  let newSortOrder: number

  if (position === 'inside') {
    newParentId = target.id
    // 放到子分类末尾，取现有子分类最大 sortOrder + 1
    const children = allFlatCats.value.filter((c) => c.parentId === target.id)
    newSortOrder = children.length > 0 ? Math.max(...children.map((c) => c.sortOrder ?? 0)) + 1 : 0
  } else {
    newParentId = target.parentId ?? 0
    const siblings = allFlatCats.value.filter((c) => (c.parentId ?? 0) === newParentId)
    const sortedSiblings = siblings.sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
    const targetIndex = sortedSiblings.findIndex((c) => c.id === target.id)

    if (position === 'before') {
      const prev = sortedSiblings[targetIndex - 1]
      if (prev) {
        newSortOrder = ((prev.sortOrder ?? 0) + (target.sortOrder ?? 0)) / 2
      } else {
        newSortOrder = (target.sortOrder ?? 0) - 1
      }
    } else {
      const next = sortedSiblings[targetIndex + 1]
      if (next) {
        newSortOrder = ((target.sortOrder ?? 0) + (next.sortOrder ?? 0)) / 2
      } else {
        newSortOrder = (target.sortOrder ?? 0) + 1
      }
    }
  }

  try {
    await adminApi.updateCategory(sourceId, {
      name: sourceNode.name,
      description: sourceNode.description,
      icon: sourceNode.icon,
      sortOrder: Math.round(newSortOrder),
      parentId: newParentId,
    })
    notify('分类已移动', 'success')
    await loadTree()
  } catch (e: unknown) {
    notify('移动失败：' + getApiError(e), 'error')
  }
}

// ========== 加载树 ==========
const loadTree = async () => {
  try {
    treeData.value = await adminApi.categoryTree()
    // 默认展开所有
    const initExpanded = new Set<number>()
    const walk = (nodes: CategoryVO[]) => {
      for (const node of nodes) {
        if (node.children?.length) {
          initExpanded.add(node.id)
          walk(node.children)
        }
      }
    }
    walk(treeData.value)
    expandedIds.value = initExpanded
  } catch (e: unknown) {
    notify('加载分类树失败：' + getApiError(e), 'error')
  }
}

onMounted(() => {
  loadTree()
})
</script>

<style scoped>
.animate-fade-in {
  animation: fadeIn 0.5s ease-out;
}
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}
.animate-scale-in {
  animation: scaleIn 0.2s ease-out;
}
@keyframes scaleIn {
  from { opacity: 0; transform: scale(0.95); }
  to { opacity: 1; transform: scale(1); }
}
</style>
