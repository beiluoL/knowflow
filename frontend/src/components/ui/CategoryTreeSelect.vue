<template>
  <!-- 树形分类选择器：将树形分类数据渲染为带缩进的下拉选项 -->
  <div class="cat-tree-select" ref="wrapperRef">
    <div
      class="cat-select-trigger"
      :class="{ active: isOpen, disabled }"
      @click="!disabled && (isOpen = !isOpen)"
    >
      <span v-if="selectedLabel" class="cat-select-value">{{ selectedLabel }}</span>
      <span v-else class="cat-select-placeholder">{{ placeholder }}</span>
      <Icon name="chevron-down" :size="14" class="cat-select-arrow" :class="{ rotated: isOpen }" />
    </div>
    <div v-if="isOpen" class="cat-select-dropdown">
      <div class="cat-select-option" @click="selectOption(undefined)">
        {{ emptyLabel }}
      </div>
      <template v-for="node in flatNodes" :key="node.id">
        <div
          class="cat-select-option"
          :class="{ selected: modelValue === node.id, disabled: nodeDisabled(node) }"
          :style="{ paddingLeft: 12 + node.level * 20 + 'px' }"
          @click="!nodeDisabled(node) && selectOption(node.id)"
        >
          <Icon v-if="node.hasChildren" :name="node.expanded ? 'chevron-down' : 'chevron-right'" :size="12" class="mr-1 shrink-0 cursor-pointer" @click.stop="toggleExpand(node.id)" />
          <span v-else class="inline-block w-3 mr-1"></span>
          <Icon :name="getIconName(node.icon)" :size="14" class="mr-1.5 shrink-0" :style="{ color: getIconColor(node.icon) }" />
          <span class="truncate">{{ node.name }}</span>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 树形分类选择器：将 CategoryVO[] 树形数据渲染为带缩进的下拉选项。
 * 支持 v-model 绑定选中的 categoryId，支持禁用指定节点。
 */
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import type { CategoryVO } from '@/api/types'
import { resolveIconForRender } from '@/utils/presetIcons'

const props = withDefaults(defineProps<{
  /** 树形分类数据 */
  categories: CategoryVO[]
  /** v-model 绑定值 */
  modelValue?: number | null
  /** 占位文本 */
  placeholder?: string
  /** 空选项标签 */
  emptyLabel?: string
  /** 是否禁用 */
  disabled?: boolean
  /** 禁止选中的 ID 列表（如编辑时排除自身） */
  excludeIds?: number[]
}>(), {
  placeholder: '请选择分类',
  emptyLabel: '作为顶级分类',
  disabled: false,
  excludeIds: () => [],
})

const emit = defineEmits<{
  'update:modelValue': [value: number | undefined]
}>()

const isOpen = ref(false)
const wrapperRef = ref<HTMLElement | null>(null)
const expandedIds = ref<Set<number>>(new Set())

interface FlatNode {
  id: number
  name: string
  icon?: string
  level: number
  hasChildren: boolean
  expanded: boolean
}

const flatNodes = computed<FlatNode[]>(() => {
  const result: FlatNode[] = []
  const walk = (nodes: CategoryVO[], level: number) => {
    for (const node of nodes) {
      const hasChildren = (node.children?.length ?? 0) > 0
      const expanded = expandedIds.value.has(node.id)
      result.push({ id: node.id, name: node.name, icon: node.icon, level, hasChildren, expanded })
      if (hasChildren && expanded) {
        walk(node.children!, level + 1)
      }
    }
  }
  walk(props.categories, 0)
  return result
})

const selectedLabel = computed(() => {
  if (props.modelValue == null) return ''
  const find = (nodes: CategoryVO[]): string => {
    for (const node of nodes) {
      if (node.id === props.modelValue) return node.name
      if (node.children?.length) {
        const r = find(node.children)
        if (r) return r
      }
    }
    return ''
  }
  return find(props.categories)
})

const toggleExpand = (id: number) => {
  const next = new Set(expandedIds.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  expandedIds.value = next
}

const selectOption = (id: number | undefined) => {
  emit('update:modelValue', id ?? undefined)
  isOpen.value = false
}

const nodeDisabled = (node: FlatNode) => props.excludeIds.includes(node.id)

const getIconName = (raw?: string): string => {
  if (!raw) return 'folder'
  const { name } = resolveIconForRender(raw)
  return name || 'folder'
}

const getIconColor = (raw?: string): string => {
  if (!raw) return '#6B7280'
  const { color } = resolveIconForRender(raw)
  return color || '#6B7280'
}

// 点击外部关闭
const onClickOutside = (e: MouseEvent) => {
  if (wrapperRef.value && !wrapperRef.value.contains(e.target as Node)) {
    isOpen.value = false
  }
}
onMounted(() => document.addEventListener('click', onClickOutside))
onBeforeUnmount(() => document.removeEventListener('click', onClickOutside))

// 默认展开所有有子分类的节点
watch(() => props.categories, (cats) => {
  const ids = new Set<number>()
  const walk = (nodes: CategoryVO[]) => {
    for (const node of nodes) {
      if (node.children?.length) {
        ids.add(node.id)
        walk(node.children)
      }
    }
  }
  walk(cats)
  expandedIds.value = ids
}, { immediate: true })
</script>

<style scoped>
.cat-tree-select {
  position: relative;
  width: 100%;
}
.cat-select-trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 40px;
  padding: 0 12px;
  border: 1px solid var(--kb-border);
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 0.15s;
  background: var(--kb-card);
}
.cat-select-trigger:hover {
  border-color: var(--kb-primary);
}
.cat-select-trigger.active {
  border-color: var(--kb-primary);
  box-shadow: 0 0 0 3px rgba(59, 111, 224, 0.1);
}
.cat-select-trigger.disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.cat-select-value {
  font-size: 14px;
  color: var(--kb-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cat-select-placeholder {
  font-size: 14px;
  color: var(--kb-muted-foreground);
}
.cat-select-arrow {
  color: var(--kb-muted-foreground);
  transition: transform 0.2s;
  flex-shrink: 0;
}
.cat-select-arrow.rotated {
  transform: rotate(180deg);
}
.cat-select-dropdown {
  position: absolute;
  top: calc(100% + 4px);
  left: 0;
  right: 0;
  z-index: 50;
  max-height: 280px;
  overflow-y: auto;
  border: 1px solid var(--kb-border);
  border-radius: 8px;
  background: var(--kb-card);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  padding: 4px 0;
}
.cat-select-option {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  font-size: 13px;
  color: var(--kb-foreground);
  cursor: pointer;
  transition: background 0.1s;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.cat-select-option:hover {
  background: var(--kb-muted);
}
.cat-select-option.selected {
  color: var(--kb-primary);
  font-weight: 500;
  background: rgba(59, 111, 224, 0.05);
}
.cat-select-option.disabled {
  color: var(--kb-muted-foreground);
  cursor: not-allowed;
  opacity: 0.5;
}
</style>
