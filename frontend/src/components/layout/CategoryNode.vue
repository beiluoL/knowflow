<template>
  <div class="category-node">
    <!-- 当前分类 -->
    <div
      class="sidebar-item"
      :class="{
        active: isCurrentActive,
        'sub-item': level === 1,
        'sub-sub-item': level === 2,
      }"
      @click="onClick"
    >
      <!-- 展开/折叠图标 -->
      <Icon
        v-if="hasChildren"
        :name="isExpanded ? 'chevron-down' : 'chevron-right'"
        :size="12"
        class="expand-icon"
        @click.stop="onToggle"
      />
      <span v-else class="expand-placeholder"></span>

      <!-- 分类图标 -->
      <Icon
        :name="getIconName(category.icon)"
        :size="16"
        class="category-icon"
        :style="{ color: getIconColor(category.icon) }"
      />

      <!-- 分类名称 -->
      <span class="category-name">{{ category.name }}</span>

      <!-- 文档数量 -->
      <span class="category-count">{{ category.docCount ?? 0 }}</span>
    </div>

    <!-- 递归渲染子分类 -->
    <template v-if="hasChildren && isExpanded">
      <CategoryNode
        v-for="child in category.children"
        :key="child.id"
        :category="child"
        :level="level + 1"
        :expanded-ids="expandedIds"
        :active-category-id="activeCategoryId"
        @toggle="handleToggle"
        @select="handleSelect"
      />
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import type { CategoryVO } from '@/api/types'

const props = defineProps<{
  category: CategoryVO
  level: number
  expandedIds: Set<number>
  activeCategoryId: number | null
}>()

const emit = defineEmits<{
  toggle: [id: number]
  select: [category: CategoryVO]
}>()

const hasChildren = computed(() => (props.category.children?.length ?? 0) > 0)
const isExpanded = computed(() => props.expandedIds.has(props.category.id))
const isCurrentActive = computed(() => props.activeCategoryId === props.category.id)

const onClick = () => {
  if (hasChildren.value) {
    emit('toggle', props.category.id)
  }
  emit('select', props.category)
}

const onToggle = () => {
  emit('toggle', props.category.id)
}

const handleToggle = (id: number) => emit('toggle', id)
const handleSelect = (cat: CategoryVO) => emit('select', cat)

const validIcons = [
  'code', 'server', 'database', 'brain', 'layout', 'palette',
  'container', 'shield', 'binary', 'book-open', 'cpu', 'bot',
  'target', 'bar-chart-2', 'briefcase', 'git-branch', 'layers',
  'file-code', 'router', 'monitor', 'wifi', 'lock', 'smartphone',
]

const iconColorMap: Record<string, string> = {
  '人工智能': '#8B5CF6',
  '前端开发': '#3B6FE0',
  '后端技术': '#10B981',
  '数据科学': '#F59E0B',
  '移动开发': '#EF4444',
  '数据库': '#06B6D4',
  'DevOps': '#F97316',
  '网络安全': '#6366F1',
  '算法与数据结构': '#64748B',
  '产品设计': '#EC4899',
  '编程语言': '#14B8A6',
  '项目管理': '#8B5CF6',
}

const nameIconMap: Record<string, string> = {
  '人工智能': 'brain',
  '前端开发': 'layout',
  '后端技术': 'server',
  '数据科学': 'bar-chart-2',
  '移动开发': 'smartphone',
  '数据库': 'database',
  'DevOps': 'container',
  '网络安全': 'shield',
  '算法与数据结构': 'binary',
  '产品设计': 'palette',
  '编程语言': 'file-code',
  '项目管理': 'briefcase',
}

function getIconName(icon?: string): string {
  if (icon && validIcons.includes(icon)) return icon
  return 'folder'
}

function getIconColor(icon?: string): string {
  if (!icon) return '#6B7280'
  for (const [name, color] of Object.entries(iconColorMap)) {
    if (icon.includes(name) || nameIconMap[name] === icon) return color
  }
  return '#3B6FE0'
}
</script>

<style scoped>
.category-node {
  width: 100%;
}

.sidebar-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  font-size: 13px;
  color: var(--kb-foreground);
  cursor: pointer;
  transition: background-color 0.15s, color 0.15s;
  user-select: none;
  white-space: nowrap;
}

.sidebar-item:hover {
  background: rgba(59, 111, 224, 0.06);
  color: var(--kb-primary);
}

.sidebar-item.active {
  background: rgba(59, 111, 224, 0.1);
  color: var(--kb-primary);
  font-weight: 500;
}

.sidebar-item.sub-item {
  padding-left: 26px;
  font-size: 12.5px;
}

.sidebar-item.sub-sub-item {
  padding-left: 42px;
  font-size: 12px;
}

.expand-icon {
  flex-shrink: 0;
  color: var(--kb-muted-foreground);
  cursor: pointer;
  width: 12px;
}

.expand-placeholder {
  flex-shrink: 0;
  width: 12px;
}

.category-icon {
  flex-shrink: 0;
}

.category-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
}

.category-count {
  flex-shrink: 0;
  font-size: 11px;
  color: var(--kb-muted-foreground);
  margin-left: auto;
}
</style>
